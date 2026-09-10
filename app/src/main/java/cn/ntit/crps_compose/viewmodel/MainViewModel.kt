package cn.ntit.crps_compose.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.ntit.crps_compose.ble.BleManager
import cn.ntit.crps_compose.data.DynamicData
import cn.ntit.crps_compose.data.StaticData
import cn.ntit.crps_compose.theme.ThemeStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * 应用状态中心：BLE 状态流透传、曲线缓存（EMA + 环形裁剪 + Y 轴 bounds）、
 * 数据超时检测、电源命令确认、主题偏好。
 *
 * 所有曲线缓存访问仅发生在主线程（BLE 数据流在 Main 收集、图表帧循环在 Main 运行）。
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val PREFS_NAME = "crps_prefs"
        private const val KEY_LAST_MAC = "last_mac"

        // 曲线缓存上限（7200 点 ≈ 30 分钟 @ 4Hz），超限一次性裁剪至 6000（25 分钟）
        private const val MAX_CACHE_SIZE = 7200
        private const val TRIM_TO_SIZE = 6000

        // EMA 平滑系数分档（Vout/Iout/Pout/Eff/T1/T2）：
        // 电压/效率中等、电流/功率快变量响应快、温度本身变化慢以去噪为主。
        private val EMA_ALPHA = floatArrayOf(0.5f, 0.65f, 0.65f, 0.5f, 0.4f, 0.4f)

        // 跳变直通阈值：新值与平滑值偏差超过 max(|旧值|×6%, 0.5) 判定为真实电平跳变，
        // 平滑值直接重置为新值，曲线瞬间到位不爬坡。
        private const val JUMP_RATIO = 0.06f
        private const val JUMP_FLOOR = 0.5f

        // 超时阈值
        private const val DATA_TIMEOUT_MS = 3_000L
        private const val STATIC_TIMEOUT_MS = 10_000L
        private const val POWER_TIMEOUT_MS = 5_000L

        // Y 轴 bounds 计算参数
        private const val BOUNDS_LOOKBACK = 60 // 15s @ 4Hz，取可见窗口内实际范围

        // 只扩不缩：数据越出当前范围立即扩大；每 10s 才允许收缩一次到实际范围，消除呼吸抖动
        private const val BOUNDS_SHRINK_MS = 10_000L

        // 上下留白：上 18%、下 8%
        private const val PAD_TOP = 0.18f
        private const val PAD_BOTTOM = 0.08f

        // 最小跨度比例（spanRatio × |中心值|）：实际波动小于该值时不放大成满屏起伏
        private const val SPAN_RATIO_V = 0.03f
        private const val SPAN_RATIO_A = 0.05f
        private const val SPAN_RATIO_W = 0.05f
        private const val SPAN_RATIO_EFF = 0.04f
        private const val SPAN_RATIO_T = 0.05f
    }

    private val ble = BleManager.getInstance(app)
    private val prefs: SharedPreferences =
        app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ==================== 主题状态 ====================

    private val _presetIndex = MutableStateFlow(ThemeStore.loadPresetIndex(app))
    val presetIndex: StateFlow<Int> = _presetIndex.asStateFlow()

    private val _darkMode = MutableStateFlow(ThemeStore.loadDarkMode(app))
    val darkMode: StateFlow<Int> = _darkMode.asStateFlow()

    fun setPresetIndex(index: Int) {
        _presetIndex.value = index
        ThemeStore.savePresetIndex(getApplication(), index)
    }

    fun setDarkMode(mode: Int) {
        _darkMode.value = mode
        ThemeStore.saveDarkMode(getApplication(), mode)
    }

    // ==================== BLE 状态透传 ====================

    val connectionState: StateFlow<Int> = ble.connectionState
    val scannedDevices: StateFlow<List<BleManager.ScannedDevice>> = ble.scannedDevices
    val connectedDevice: StateFlow<BleManager.ScannedDevice?> = ble.connectedDevice
    val dynamicData: StateFlow<DynamicData?> = ble.dynamicData
    val staticData: StateFlow<StaticData?> = ble.staticData

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private val _dataInterrupted = MutableStateFlow(false)
    val dataInterrupted: StateFlow<Boolean> = _dataInterrupted.asStateFlow()

    private val _staticInterrupted = MutableStateFlow(false)
    val staticInterrupted: StateFlow<Boolean> = _staticInterrupted.asStateFlow()

    // ==================== 曲线缓存 ====================

    /** X 轴：ESP32 运行时间 rt（秒），与 6 路 Y 一一对应 */
    val xTimes: ArrayList<Float> = ArrayList()
    val yVout: ArrayList<Float> = ArrayList()
    val yIout: ArrayList<Float> = ArrayList()
    val yPout: ArrayList<Float> = ArrayList()
    val yEff: ArrayList<Float> = ArrayList()
    val yT1: ArrayList<Float> = ArrayList()
    val yT2: ArrayList<Float> = ArrayList()

    /** 六个单线图的 Y 轴 bounds：[min, max]，顺序 Vout/Iout/Pout/Eff/T1/T2，只扩不缩、增量更新 */
    val chartBounds: Array<FloatArray> = Array(6) { floatArrayOf(0f, 1f) }

    // 应用切后台时暂停数据追加，避免堆积
    private val chartPaused = kotlinx.coroutines.flow.MutableStateFlow(false)

    // 每通道上次收缩时间戳（只扩不缩：越界立即扩，收缩需间隔 10s）
    private val lastShrinkAt = LongArray(6) { 0L }

    private var emaInit = false
    private val ema = FloatArray(6)

    private var dataTimeoutJob: Job? = null
    private var staticTimeoutJob: Job? = null

    // ==================== 电源命令 ====================

    private val _powerPending = MutableStateFlow(false)
    val powerPending: StateFlow<Boolean> = _powerPending.asStateFlow()

    private val _powerTimeout = MutableStateFlow(false)
    val powerTimeout: StateFlow<Boolean> = _powerTimeout.asStateFlow()

    private var pendingPowerState = -1
    private var powerTimeoutJob: Job? = null

    init {
        // 转发错误事件（含重试语义由 UI 处理）
        viewModelScope.launch {
            ble.errorEvents.collect { _errors.tryEmit(it) }
        }

        // 连接成功：保存 MAC 供下次自动重连
        viewModelScope.launch {
            connectionState.collect { state ->
                if (state == BleManager.STATE_CONNECTED) {
                    ble.getLastConnectedAddress()?.let { prefs.edit().putString(KEY_LAST_MAC, it).apply() }
                }
            }
        }

        // 动态数据：追加曲线 + 重置超时 + 电源命令确认
        viewModelScope.launch {
            dynamicData.filterNotNull().collect { d -> onDynamicData(d) }
        }

        // 静态数据：重置超时
        viewModelScope.launch {
            staticData.filterNotNull().collect { onStaticData() }
        }

        // 扫描结果：命中记忆设备则自动连接
        viewModelScope.launch {
            scannedDevices.collect { autoConnectIfMatched() }
        }
    }

    // ==================== 启动 / 自动连接 ====================

    /** 权限就绪后调用：恢复上次设备并启动扫描自动重连 */
    fun start() {
        if (!ble.isBluetoothEnabled()) {
            _errors.tryEmit("蓝牙未开启，请先开启蓝牙")
            return
        }
        if (connectionState.value == BleManager.STATE_CONNECTED) return

        val lastMac = prefs.getString(KEY_LAST_MAC, null)
        if (lastMac != null) {
            ble.setLastConnectedAddress(lastMac)
            ble.startScan()
            // 扫描兜底：10s 仍无设备则停止
            viewModelScope.launch {
                delay(10_000)
                if (connectionState.value == BleManager.STATE_SCANNING) {
                    ble.stopScan()
                }
            }
        }
    }

    /** 用户重试：未连接则重新扫描 */
    fun retry() {
        if (connectionState.value != BleManager.STATE_CONNECTED) {
            ble.startScan()
        }
    }

    fun connect(address: String) = ble.connect(address)

    fun disconnect() = ble.disconnect()

    private fun autoConnectIfMatched() {
        if (connectionState.value != BleManager.STATE_SCANNING) return
        val lastMac = prefs.getString(KEY_LAST_MAC, null) ?: return
        val match = scannedDevices.value.firstOrNull { it.address == lastMac }
        match?.let { ble.connect(it.address) }
    }

    /** 当前选择设备（供控制页 UI 记住选择） */
    val selectedDeviceAddress: kotlinx.coroutines.flow.MutableStateFlow<String?> =
        MutableStateFlow(null)

    // ==================== 曲线追加 ====================

    private fun onDynamicData(d: DynamicData) {
        _dataInterrupted.value = false
        if (!chartPaused.value) {
            appendChartEntry(d)
        }
        resetDataTimeout()

        // 电源命令确认：设备回报 pwr 与目标一致即确认成功
        if (_powerPending.value && d.pwr == pendingPowerState) {
            _powerPending.value = false
            powerTimeoutJob?.cancel()
        }
    }

    private fun onStaticData() {
        _staticInterrupted.value = false
        resetStaticTimeout()
    }

    private fun appendChartEntry(d: DynamicData) {
        val raw = floatArrayOf(d.vout, d.iout, d.pout, d.eff.toFloat(), d.t1, d.t2)
        if (!emaInit) {
            for (i in 0 until 6) ema[i] = raw[i]
            emaInit = true
        } else {
            for (i in 0 until 6) {
                val a = EMA_ALPHA[i]
                // 跳变直通：新值与平滑值偏差超过阈值判定为真实电平跳变，直接重置（瞬间到位不爬坡）
                val threshold = maxOf(kotlin.math.abs(ema[i]) * JUMP_RATIO, JUMP_FLOOR)
                ema[i] = if (kotlin.math.abs(raw[i] - ema[i]) > threshold) raw[i]
                else a * raw[i] + (1 - a) * ema[i]
            }
        }

        // 曲线 X 轴用本地单调时钟（秒）：固件 rt 是整数秒（每秒 4 条数据同值），
        // 直接用 rt 会让 4 个点堆在同一横坐标、曲线变垂直尖峰。
        // 本地 elapsedRealtime 每 250ms 真实前进 0.25，掉帧时也能正确反映时间间隔。
        xTimes.add(SystemClock.elapsedRealtime() / 1000f)
        appendTrimmed(yVout, ema[0])
        appendTrimmed(yIout, ema[1])
        appendTrimmed(yPout, ema[2])
        appendTrimmed(yEff, ema[3])
        appendTrimmed(yT1, ema[4])
        appendTrimmed(yT2, ema[5])

        updateChartBounds()
    }

    private fun appendTrimmed(list: ArrayList<Float>, v: Float) {
        list.add(v)
        // 超限时一次性批量裁剪（subList.clear 单次数组搬移，摊薄成本远低于逐点 remove(0)）
        if (list.size > MAX_CACHE_SIZE) {
            list.subList(0, list.size - TRIM_TO_SIZE).clear()
        }
    }

    /** 计算六个单线图的 Y 轴 bounds（只扩不缩 + 最小跨度 + 上下留白），供图表页每帧读取 */
    private fun updateChartBounds() {
        if (yVout.size >= 2) chartBounds[0] = updateBounds(0, yVout, allowNegative = false, SPAN_RATIO_V)
        if (yIout.size >= 2) chartBounds[1] = updateBounds(1, yIout, allowNegative = false, SPAN_RATIO_A)
        if (yPout.size >= 2) chartBounds[2] = updateBounds(2, yPout, allowNegative = false, SPAN_RATIO_W)
        if (yEff.size >= 2) chartBounds[3] = updateBounds(3, yEff, allowNegative = false, SPAN_RATIO_EFF)
        if (yT1.size >= 2) chartBounds[4] = updateBounds(4, yT1, allowNegative = true, SPAN_RATIO_T)
        if (yT2.size >= 2) chartBounds[5] = updateBounds(5, yT2, allowNegative = true, SPAN_RATIO_T)
    }

    private fun updateBounds(
        index: Int,
        list: ArrayList<Float>,
        allowNegative: Boolean,
        spanRatio: Float,
    ): FloatArray {
        if (list.size < 2) return floatArrayOf(0f, 1f)

        // 1. 可见窗口内实际数据范围
        val start = (list.size - BOUNDS_LOOKBACK).coerceAtLeast(0)
        var min = Float.MAX_VALUE
        var max = -Float.MAX_VALUE
        for (i in start until list.size) {
            val v = list[i]
            if (v < min) min = v
            if (v > max) max = v
        }
        if (min == Float.MAX_VALUE) return floatArrayOf(0f, 1f)

        // 2. 最小跨度：实际波动 < |中心值|×比例 时强制保持最小宽度，小幅噪声不放大成满屏起伏
        var lo = min
        var hi = max
        var range = hi - lo
        val center = (lo + hi) / 2f
        val minSpan = kotlin.math.abs(center) * spanRatio
        if (range < minSpan) {
            val extra = (minSpan - range) / 2f
            lo -= extra
            hi += extra
            range = minSpan
        }
        if (range < 1e-6f) range = 1f // 兜底防除零

        // 3. 上下留白：上 18%、下 8%；零下保护（数据下限 ≥ 0 时显示下限不低于 0）
        var targetLo = lo - range * PAD_BOTTOM
        var targetHi = hi + range * PAD_TOP
        if (!allowNegative && targetLo < 0f) targetLo = 0f

        // 4. 只扩不缩：越界立即扩大；每 10s 才允许收缩一次到实际范围
        val cur = chartBounds[index]
        val now = SystemClock.uptimeMillis()
        return if (now - lastShrinkAt[index] >= BOUNDS_SHRINK_MS) {
            lastShrinkAt[index] = now
            floatArrayOf(targetLo, targetHi)
        } else {
            floatArrayOf(minOf(cur[0], targetLo), maxOf(cur[1], targetHi))
        }
    }

    // ==================== 超时检测 ====================

    private fun resetDataTimeout() {
        dataTimeoutJob?.cancel()
        dataTimeoutJob = viewModelScope.launch {
            delay(DATA_TIMEOUT_MS)
            if (connectionState.value == BleManager.STATE_CONNECTED) {
                _dataInterrupted.value = true
            }
        }
    }

    private fun resetStaticTimeout() {
        staticTimeoutJob?.cancel()
        staticTimeoutJob = viewModelScope.launch {
            delay(STATIC_TIMEOUT_MS)
            if (connectionState.value == BleManager.STATE_CONNECTED) {
                _staticInterrupted.value = true
            }
        }
    }

    // ==================== 电源控制 ====================

    fun sendPower(on: Boolean) {
        if (connectionState.value != BleManager.STATE_CONNECTED) return
        pendingPowerState = if (on) 1 else 0
        _powerPending.value = true
        _powerTimeout.value = false
        ble.sendPowerCommand(on)

        powerTimeoutJob?.cancel()
        powerTimeoutJob = viewModelScope.launch {
            delay(POWER_TIMEOUT_MS)
            if (_powerPending.value) {
                _powerPending.value = false
                _powerTimeout.value = true
            }
        }
    }

    fun confirmPowerRetry() {
        val lastOn = pendingPowerState == 1
        sendPower(lastOn)
    }

    // ==================== 图表暂停（应用生命周期） ====================

    fun setChartPaused(paused: Boolean) {
        chartPaused.value = paused
        if (!paused) {
            // 恢复时重置 EMA：避免长时间暂停后首点用旧 EMA 产生异常值
            emaInit = false
        }
    }

    /** 切换设备后由 UI 调用，清空全部曲线缓存 */
    fun clearChartData() {
        xTimes.clear()
        yVout.clear(); yIout.clear(); yPout.clear()
        yEff.clear(); yT1.clear(); yT2.clear()
        emaInit = false
        lastShrinkAt.fill(0L)
        for (i in chartBounds.indices) {
            chartBounds[i] = floatArrayOf(0f, 1f)
        }
    }
}