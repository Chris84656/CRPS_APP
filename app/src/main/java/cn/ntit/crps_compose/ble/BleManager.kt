package cn.ntit.crps_compose.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import cn.ntit.crps_compose.data.DynamicData
import cn.ntit.crps_compose.data.StaticData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.UUID

/**
 * BLE 管理器（协程化重写）。
 *
 * 移植自旧版 crps_re0 的 Java 实现，保留全部健壮性设计：
 * - GATT 操作队列 + 超时三层兜底（超时清队列 / g==gatt 过滤 / 空队列直接 return）
 * - JSON 拼包缓冲区（StringBuffer 保证 binder 线程 append 与主线程 reset 线程安全）
 * - 意外断连自动重连（次数限制 + 间隔）
 * - 蓝牙关闭广播监听
 *
 * 状态通过 StateFlow/SharedFlow 暴露，UI 层 collectAsState 驱动 Compose。
 */
@SuppressLint("MissingPermission")
class BleManager private constructor(context: Context) {

    companion object {
        private const val TAG = "BleManager"

        // 连接状态
        const val STATE_DISCONNECTED = 0
        const val STATE_SCANNING = 1
        const val STATE_CONNECTING = 2
        const val STATE_CONNECTED = 3

        // UUID（与 ESP32 固件约定一致）
        private val SERVICE_UUID = UUID.fromString("00001815-0000-1000-8000-00805f9b34fb")
        private val CHAR_DATA_UUID = UUID.fromString("00002a56-0000-1000-8000-00805f9b34fb")
        private val CHAR_CTRL_UUID = UUID.fromString("00002a57-0000-1000-8000-00805f9b34fb")
        private val CHAR_STATIC_UUID = UUID.fromString("00002a58-0000-1000-8000-00805f9b34fb")
        private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private const val DEVICE_NAME_PREFIX = "CRPS Monitor_"
        private const val MTU_SIZE = 247
        private const val SCAN_DURATION_MS = 7_000L
        private const val MAX_RECONNECT_ATTEMPTS = 3
        private const val GATT_OP_TIMEOUT_MS = 5_000L
        private const val RECONNECT_DELAY_MS = 2_000L
        private const val RECONNECT_TIMEOUT_MS = 5_000L

        @Volatile
        private var instance: BleManager? = null

        fun getInstance(context: Context): BleManager =
            instance ?: synchronized(this) {
                instance ?: BleManager(context.applicationContext).also { instance = it }
            }
    }

    data class ScannedDevice(val name: String, val address: String, val rssi: Int) {
        override fun toString(): String = name
    }

    // ==================== 对外状态流 ====================

    private val _connectionState = MutableStateFlow(STATE_DISCONNECTED)
    val connectionState: StateFlow<Int> = _connectionState.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val scannedDevices: StateFlow<List<ScannedDevice>> = _scannedDevices.asStateFlow()

    /** 当前已连接设备（含名称），供 UI 在自动连接/重连时也能显示选中设备 */
    private val _connectedDevice = MutableStateFlow<ScannedDevice?>(null)
    val connectedDevice: StateFlow<ScannedDevice?> = _connectedDevice.asStateFlow()

    private val _dynamicData = MutableStateFlow<DynamicData?>(null)
    val dynamicData: StateFlow<DynamicData?> = _dynamicData.asStateFlow()

    private val _staticData = MutableStateFlow<StaticData?>(null)
    val staticData: StateFlow<StaticData?> = _staticData.asStateFlow()

    private val _errorEvents = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val errorEvents: SharedFlow<String> = _errorEvents.asSharedFlow()

    // ==================== 内部状态 ====================

    private val appContext: Context = context.applicationContext
    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private var scanner: BluetoothLeScanner? = null
    private var gatt: BluetoothGatt? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var reconnectAttempts = 0
    private var lastConnectedAddress: String? = null
    private var isScanning = false
    private var userInitiatedDisconnect = false
    private var currentMtu = 23

    // JSON 拼包缓冲区
    private val jsonBufferData = StringBuffer()
    private val jsonBufferStatic = StringBuffer()

    // GATT 操作队列
    private val gattQueue = Collections.synchronizedList(ArrayList<Runnable>())
    private var gattBusy = false

    // 各类取消句柄
    private var scanTimeoutJob: Job? = null
    private var gattOpTimeoutJob: Job? = null
    private var reconnectJob: Job? = null
    private var reconnectTimeoutJob: Job? = null

    // ==================== 蓝牙关闭广播 ====================

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val state = intent?.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            if (state == BluetoothAdapter.STATE_OFF) {
                if (_connectionState.value == STATE_CONNECTED ||
                    _connectionState.value == STATE_CONNECTING
                ) {
                    userInitiatedDisconnect = true
                    disconnectGatt()
                    setConnectionState(STATE_DISCONNECTED)
                    postError("蓝牙已关闭")
                }
            }
        }
    }

    init {
        // minSdk 26，RECEIVER_NOT_EXPORTED 可用；仅监听系统广播
        ContextCompat.registerReceiver(
            appContext, bluetoothStateReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    // ==================== 基础查询 ====================

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun getLastConnectedAddress(): String? = lastConnectedAddress

    fun setLastConnectedAddress(address: String?) {
        lastConnectedAddress = address
    }

    // ==================== 扫描 ====================

    fun startScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            postError("蓝牙未开启")
            return
        }
        if (isScanning) return

        scanner = bluetoothAdapter!!.bluetoothLeScanner ?: return

        _scannedDevices.value = emptyList()
        isScanning = true
        setConnectionState(STATE_SCANNING)

        val filter = ScanFilter.Builder()
            .setDeviceName(null)
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner!!.startScan(listOf(filter), settings, scanCallback)
        } catch (e: SecurityException) {
            postError("扫描失败：缺少蓝牙权限")
            isScanning = false
            setConnectionState(STATE_DISCONNECTED)
            return
        }

        scanTimeoutJob?.cancel()
        scanTimeoutJob = scope.launch {
            delay(SCAN_DURATION_MS)
            stopScan()
        }
    }

    /** 内部停止扫描（连接前调用，不改变连接状态） */
    private fun stopScanInternal() {
        if (!isScanning) return
        isScanning = false
        scanner?.let { s ->
            try {
                s.stopScan(scanCallback)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to stop scan", e)
            }
        }
        scanTimeoutJob?.cancel()
    }

    fun stopScan() {
        stopScanInternal()
        if (_connectionState.value == STATE_SCANNING) {
            setConnectionState(STATE_DISCONNECTED)
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device: BluetoothDevice = result.device
            val name = device.name ?: return
            if (!name.startsWith(DEVICE_NAME_PREFIX)) return

            val address = device.address
            val current = _scannedDevices.value
            if (current.none { it.address == address }) {
                _scannedDevices.value = current + ScannedDevice(name, address, result.rssi)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            postError("扫描失败: $errorCode")
        }
    }

    // ==================== 连接 ====================

    fun connect(address: String) {
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            postError("蓝牙未开启")
            return
        }
        // 主动连接：屏蔽 disconnectGatt 触发的延迟回调
        userInitiatedDisconnect = true
        stopScanInternal()
        disconnectGatt()
        lastConnectedAddress = address
        reconnectAttempts = 0
        doConnect(address)
    }

    private fun doConnect(address: String) {
        reconnectJob?.cancel()
        reconnectTimeoutJob?.cancel()

        gatt?.let {
            it.disconnect()
            it.close()
            gatt = null
        }

        val device: BluetoothDevice = try {
            bluetoothAdapter!!.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            postError("无效的设备地址: $address")
            setConnectionState(STATE_DISCONNECTED)
            return
        }

        setConnectionState(STATE_CONNECTING)
        gatt = device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        // GATT 创建后复位标志：旧 GATT 的延迟回调由 g != this.gatt 过滤
        userInitiatedDisconnect = false
    }

    fun disconnect() {
        userInitiatedDisconnect = true
        reconnectJob?.cancel()
        reconnectTimeoutJob?.cancel()
        disconnectGatt()
        _connectedDevice.value = null
        setConnectionState(STATE_DISCONNECTED)
    }

    private fun disconnectGatt() {
        gatt?.let {
            it.disconnect()
            it.close()
            gatt = null
        }
        synchronized(gattQueue) {
            gattQueue.clear()
            gattBusy = false
        }
        gattOpTimeoutJob?.cancel()
        jsonBufferData.setLength(0)
        jsonBufferStatic.setLength(0)
    }

    // ==================== 写入控制 ====================

    fun sendPowerCommand(powerOn: Boolean) {
        val g = gatt ?: return
        val service: BluetoothGattService = g.getService(SERVICE_UUID) ?: return
        val ctrlChar: BluetoothGattCharacteristic = service.getCharacteristic(CHAR_CTRL_UUID) ?: return

        val cmd = byteArrayOf(if (powerOn) 0x01 else 0x00)
        ctrlChar.value = cmd
        ctrlChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

        enqueueGattOperation {
            val currentGatt = gatt
            if (currentGatt != null) {
                if (!currentGatt.writeCharacteristic(ctrlChar)) {
                    Log.w(TAG, "writeCharacteristic returned false")
                    postError("写入命令失败")
                    processNextGattOperation()
                }
            } else {
                // gatt 已断开，丢弃本条 op
                processNextGattOperation()
            }
        }
    }

    // ==================== GATT 回调 ====================

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (g !== gatt) return // 旧 GATT 的延迟回调直接丢弃
            // 只检查 status=133 且 newState=CONNECTED 的特定组合
            if (status == 133 && newState == BluetoothProfile.STATE_CONNECTED) {
                scope.launch { handleUnexpectedDisconnect() }
                return
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                reconnectAttempts = 0
                userInitiatedDisconnect = false
                reconnectJob?.cancel()
                reconnectTimeoutJob?.cancel()
                updateConnectedDevice()
                scope.launch {
                    setConnectionState(STATE_CONNECTED)
                    g.requestMtu(MTU_SIZE)
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _connectedDevice.value = null
                scope.launch {
                    if (!userInitiatedDisconnect) {
                        handleUnexpectedDisconnect()
                    } else {
                        setConnectionState(STATE_DISCONNECTED)
                    }
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            if (g !== gatt) return
            currentMtu = if (status == BluetoothGatt.GATT_SUCCESS) mtu else 23
            Log.d(TAG, "MTU changed: $currentMtu (status=$status)")
            g.discoverServices()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (g !== gatt) return
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = g.getService(SERVICE_UUID)
                if (service != null) {
                    enableNotification(g, service, CHAR_DATA_UUID)
                    enableNotification(g, service, CHAR_STATIC_UUID)
                }
            } else {
                postError("服务发现失败")
            }
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            if (g !== gatt) return
            val value = characteristic.value ?: return
            val chunk = String(value, StandardCharsets.UTF_8)
            // 拼包缓冲区，按 UUID 维护
            val buffer = when (characteristic.uuid) {
                CHAR_DATA_UUID -> jsonBufferData
                CHAR_STATIC_UUID -> jsonBufferStatic
                else -> return
            }
            buffer.append(chunk)
            tryParseAndDispatch(characteristic.uuid, buffer)
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (g !== gatt) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                postError("写入失败 status=$status")
            }
            processNextGattOperation()
        }

        override fun onDescriptorWrite(
            g: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (g !== gatt) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                postError("通知启用失败")
                disconnectGatt()
                setConnectionState(STATE_DISCONNECTED)
                return
            }
            processNextGattOperation()
        }
    }

    // ==================== 内部方法 ====================

    private fun enableNotification(g: BluetoothGatt, service: BluetoothGattService, charUuid: UUID) {
        val characteristic = service.getCharacteristic(charUuid) ?: return

        g.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(CCCD_UUID) ?: return
        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE

        enqueueGattOperation {
            val currentGatt = gatt
            if (currentGatt != null) {
                if (!currentGatt.writeDescriptor(descriptor)) {
                    Log.w(TAG, "writeDescriptor returned false")
                    postError("通知启用失败")
                    disconnectGatt()
                    setConnectionState(STATE_DISCONNECTED)
                    // 队列已在 disconnectGatt 中清空
                }
            } else {
                processNextGattOperation()
            }
        }
    }

    /** 拼包尝试：JSON 括号配对完整即派发（本方法运行于 binder 线程） */
    private fun tryParseAndDispatch(uuid: UUID, buffer: StringBuffer) {
        val json = buffer.toString().trim()
        if (json.isEmpty()) return

        if (isJsonComplete(json)) {
            buffer.setLength(0)
            // 移到主线程解析，避免占用 binder 线程
            val jsonStr = json
            scope.launch(Dispatchers.Main) { parseAndDispatch(uuid, jsonStr) }
        }
    }

    private fun isJsonComplete(json: String): Boolean {
        var braceCount = 0
        var started = false
        for (c in json) {
            if (c == '{') {
                braceCount++
                started = true
            } else if (c == '}') {
                braceCount--
            }
            if (started && braceCount == 0) return true
        }
        return false
    }

    private fun parseAndDispatch(uuid: UUID, json: String) {
        try {
            val obj = JSONObject(json)
            when (uuid) {
                CHAR_DATA_UUID -> _dynamicData.value = DynamicData.parse(obj)
                CHAR_STATIC_UUID -> _staticData.value = StaticData.parse(obj)
            }
        } catch (e: Exception) {
            Log.w(TAG, "JSON parse failed: $json", e)
        }
    }

    private fun handleUnexpectedDisconnect() {
        if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS && lastConnectedAddress != null) {
            reconnectAttempts++
            setConnectionState(STATE_CONNECTING)
            reconnectJob?.cancel()
            reconnectTimeoutJob?.cancel()
            reconnectJob = scope.launch {
                delay(RECONNECT_DELAY_MS)
                if (!userInitiatedDisconnect && lastConnectedAddress != null) {
                    doConnect(lastConnectedAddress!!)
                    reconnectTimeoutJob = scope.launch {
                        delay(RECONNECT_TIMEOUT_MS)
                        if (_connectionState.value == STATE_CONNECTING) {
                            Log.w(TAG, "Reconnect timeout, giving up")
                            disconnectGatt()
                            setConnectionState(STATE_DISCONNECTED)
                            postError("连接已断开")
                        }
                    }
                }
            }
        } else {
            // 重连次数耗尽：关闭 gatt 避免资源泄漏
            disconnectGatt()
            setConnectionState(STATE_DISCONNECTED)
            postError("连接已断开")
        }
    }

    private fun setConnectionState(state: Int) {
        _connectionState.value = state
    }

    /** 连接成功后更新当前已连接设备（含名称），供 UI 在自动连接/重连时也能显示选中设备 */
    private fun updateConnectedDevice() {
        val addr = lastConnectedAddress
        if (addr == null) {
            _connectedDevice.value = null
            return
        }
        val scanned = _scannedDevices.value.firstOrNull { it.address == addr }
        val name = scanned?.name
            ?: runCatching { bluetoothAdapter?.getRemoteDevice(addr)?.name }.getOrNull()
            ?: addr
        _connectedDevice.value = ScannedDevice(name, addr, scanned?.rssi ?: 0)
    }

    private fun postError(message: String) {
        _errorEvents.tryEmit(message)
    }

    // ==================== GATT 操作队列 ====================

    private fun enqueueGattOperation(operation: Runnable) {
        synchronized(gattQueue) {
            gattQueue.add(operation)
            if (!gattBusy) {
                processNextGattOperation()
            }
        }
    }

    private fun processNextGattOperation() {
        synchronized(gattQueue) {
            // 取消上一条 op 的超时（无论成功失败都会走到这里）
            gattOpTimeoutJob?.cancel()
            if (gattQueue.isEmpty()) {
                gattBusy = false
                return
            }
            gattBusy = true
            val op = gattQueue.removeAt(0)
            // op 必须在主线程执行（等价原版 mainHandler.post）：Main.immediate 保证主线程调用时同步、binder 线程调用时入队
            scope.launch { op.run() }
            // 超时 job 在体外赋值，避免 op 内嵌套 processNextGattOperation 时外层覆盖内层 job 引用
            gattOpTimeoutJob = scope.launch {
                delay(GATT_OP_TIMEOUT_MS)
                synchronized(gattQueue) {
                    if (gattBusy) {
                        Log.w(TAG, "GATT operation timeout, forcing next")
                        postError("GATT 操作超时")
                        gattBusy = false
                        gattQueue.clear()
                    }
                }
            }
        }
    }
}