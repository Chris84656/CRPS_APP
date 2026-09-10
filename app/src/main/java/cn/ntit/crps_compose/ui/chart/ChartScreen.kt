package cn.ntit.crps_compose.ui.chart

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect as drawScopeClipRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.ntit.crps_compose.R
import cn.ntit.crps_compose.ble.BleManager
import cn.ntit.crps_compose.ui.components.AnimatedNumber
import cn.ntit.crps_compose.ui.theme.chartColorsOf
import cn.ntit.crps_compose.util.NumberFormatter
import cn.ntit.crps_compose.viewmodel.MainViewModel

/** 可视窗口范围（秒） */
private const val VISIBLE_RANGE = 15f

/** 预热期最小窗口（秒）：数据尚不足一个窗口时的最小显示宽度，避免前几点过度横向拉伸 */
private const val MIN_WINDOW = 2f

/** 单线图表高度（无轴化紧凑卡片） */
private val CHART_HEIGHT = 64.dp

/** 渐变面积顶部不透明度（线色，向下渐透明） */
private const val AREA_ALPHA_TOP = 0.26f

/** 渐变面积底部不透明度（接近透明但非 0） */
private const val AREA_ALPHA_BOTTOM = 0.015f

/** 曲线线宽 */
private const val LINE_STROKE = 2f

/** Y 轴范围趋近系数（60fps 下 0.08 ≈ 200ms 时间常数，缩放丝滑带加减速） */
private const val Y_LERP = 0.08f

/** 右缘虚拟锚点 Y 趋近系数（60fps 下 0.09 ≈ 180ms 时间常数，消除 250ms 阶跃突进） */
private const val HEAD_LERP = 0.09f

/**
 * 右缘虚拟锚点数据活跃容差（秒）：最新采样点落后"现在"在容差内时，曲线右端始终锁定视口右缘；
 * 只有真正断流（落后超容差，对齐 dataInterrupted 3s 语义）才回缩到最新数据处防空延伸。
 * 0.5s 太短：BLE 偶发丢帧/切后台恢复会让右端脱离右侧。
 */
private const val VIRT_EXT_SEC = 2.5f

/** 抽稀最小点距（px）：点太密时曲线转角显示不出弧度，抽稀让转角更圆润 */
private const val MIN_SEG_PX = 12f

/** 三次贝塞尔张力系数（FlClash 式）：越大曲线越松弛弯曲，0.4 为标准值 */
private const val TENSION = 0.4f

/**
 * 图表页：六张单线实时图表卡片（无坐标轴、无网格、无交互），
 * 平滑曲线（左右撑满）+ 向下渐变面积；卡片头部显示名称、实时数值与状态圆点。
 *
 * 性能设计（沿用旧版流式架构）：
 * - withFrameNanos 驱动帧循环，仅平移视口起点，不重建任何数据
 * - 二分定位可见窗口起点，仅遍历可见点（~250 点/线）
 * - 帧号与视口起点仅在绘制期读取，不触发重组
 */
@Composable
fun ChartScreen(viewModel: MainViewModel) {
    val connectionState by viewModel.connectionState.collectAsState()
    val interrupted by viewModel.dataInterrupted.collectAsState()
    val dynamic by viewModel.dynamicData.collectAsState()
    val preset by viewModel.presetIndex.collectAsState()
    val darkMode by viewModel.darkMode.collectAsState()
    val connected = connectionState == BleManager.STATE_CONNECTED

    // 运行时间显示：实时跟随 dynamic 更新；断连时保持最后值
    var runtimeText by remember { mutableStateOf("0秒") }
    LaunchedEffect(dynamic) {
        dynamic?.let { runtimeText = NumberFormatter.runtime(it.rt) }
    }

    // 帧序列号：每帧 +1 触发 Canvas 重绘；仅传给 Canvas 在绘制期读取 → 只失效绘制阶段
    val frameTick = remember { mutableLongStateOf(0L) }
    val viewportStart = remember { mutableFloatStateOf(0f) }
    // 视口窗口宽度（秒）：预热期自适应（< VISIBLE_RANGE），数据攒够后固定为 VISIBLE_RANGE
    val viewportWindow = remember { mutableFloatStateOf(VISIBLE_RANGE) }
    // 视口是否已锚定（锚定前不绘制曲线，避免切换时的圆点错位）
    var viewReady by remember { mutableStateOf(false) }

    // 断连时退出绘制（数据缓存会随设备切换清空）
    LaunchedEffect(connected) {
        if (!connected) viewReady = false
    }

    // 帧循环：右边缘锁定最新点；预热期窗口自适应（数据不足一个窗口时铺满整条），
    // 数据攒够后进入固定窗口滚动。用 wall-clock 连续推进，保证 60fps 丝滑。
    LaunchedEffect(Unit) {
        var anchorTimeNs = 0L
        var anchorX = 0f
        while (true) {
            withFrameNanos { now ->
                val xs = viewModel.xTimes
                if (anchorTimeNs == 0L && xs.size > 1) {
                    anchorTimeNs = now
                    anchorX = xs[xs.size - 1]
                }
                if (anchorTimeNs != 0L) {
                    val elapsed = (now - anchorTimeNs) / 1e9f
                    val latestRt = anchorX + elapsed
                    val dataSpan = (latestRt - xs[0]).coerceAtLeast(MIN_WINDOW)
                    val window = minOf(dataSpan, VISIBLE_RANGE)
                    viewportWindow.value = window
                    viewportStart.value = latestRt - window
                    viewReady = true
                }
                frameTick.value = now
            }
        }
    }

    val chartColors = chartColorsOf(preset, 0xFF2C52B8.toInt(), darkMode)
    // 状态圆点：连接且数据未中断为绿色（呼吸扩散），否则红色
    val statusActive = connected && !interrupted
    // 数据每 250ms 到达触发 dynamic 状态变化 → 重组刷新 hasData
    val hasData = viewModel.xTimes.size > 1
    @Suppress("UNUSED_VARIABLE")
    val dataTick = dynamic

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .blur(if (connected) 0.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ============ 运行时间独立卡片 ============
            RuntimeCard(
                runtimeText = runtimeText,
                active = connected && !interrupted,
            )

            // ============ 六张单线图表卡片 ============
            val xs = viewModel.xTimes
            SingleChartCard(
                title = stringResource(R.string.chart_voltage),
                value = dynamic?.vout,
                unit = "V",
                format = NumberFormatter::voltage,
                xTimes = xs,
                values = viewModel.yVout,
                bounds = viewModel.chartBounds[0],
                color = Color(chartColors[0]),
                statusActive = statusActive,
                viewportStart = viewportStart,
                viewportWindow = viewportWindow,
                frameTick = frameTick,
                ready = viewReady,
            )
            SingleChartCard(
                title = stringResource(R.string.chart_current),
                value = dynamic?.iout,
                unit = "A",
                format = NumberFormatter::current,
                xTimes = xs,
                values = viewModel.yIout,
                bounds = viewModel.chartBounds[1],
                color = Color(chartColors[1]),
                statusActive = statusActive,
                viewportStart = viewportStart,
                viewportWindow = viewportWindow,
                frameTick = frameTick,
                ready = viewReady,
            )
            SingleChartCard(
                title = stringResource(R.string.chart_power),
                value = dynamic?.pout,
                unit = "W",
                format = NumberFormatter::power,
                xTimes = xs,
                values = viewModel.yPout,
                bounds = viewModel.chartBounds[2],
                color = Color(chartColors[2]),
                statusActive = statusActive,
                viewportStart = viewportStart,
                viewportWindow = viewportWindow,
                frameTick = frameTick,
                ready = viewReady,
            )
            SingleChartCard(
                title = stringResource(R.string.chart_efficiency),
                value = dynamic?.eff?.toFloat(),
                unit = "%",
                format = { NumberFormatter.efficiency(it.toInt()) },
                xTimes = xs,
                values = viewModel.yEff,
                bounds = viewModel.chartBounds[3],
                color = Color(chartColors[3]),
                statusActive = statusActive,
                viewportStart = viewportStart,
                viewportWindow = viewportWindow,
                frameTick = frameTick,
                ready = viewReady,
            )
            SingleChartCard(
                title = stringResource(R.string.chart_ambient),
                value = dynamic?.t1,
                unit = "°C",
                format = NumberFormatter::temperature,
                xTimes = xs,
                values = viewModel.yT1,
                bounds = viewModel.chartBounds[4],
                color = Color(chartColors[4]),
                statusActive = statusActive,
                viewportStart = viewportStart,
                viewportWindow = viewportWindow,
                frameTick = frameTick,
                ready = viewReady,
            )
            SingleChartCard(
                title = stringResource(R.string.chart_hotspot),
                value = dynamic?.t2,
                unit = "°C",
                format = NumberFormatter::temperature,
                xTimes = xs,
                values = viewModel.yT2,
                bounds = viewModel.chartBounds[5],
                color = Color(chartColors[5]),
                statusActive = statusActive,
                viewportStart = viewportStart,
                viewportWindow = viewportWindow,
                frameTick = frameTick,
                ready = viewReady,
            )

            // 空状态
            AnimatedVisibility(
                visible = !hasData,
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(150)),
            ) {
                Text(
                    text = stringResource(R.string.no_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                )
            }

            // 数据中断横幅
            AnimatedVisibility(
                visible = interrupted && connected,
                enter = fadeIn(tween(220)),
                exit = fadeOut(tween(150)),
            ) {
                Text(
                    text = stringResource(R.string.data_interrupted),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
            }
        }

        // 断连遮罩：圆角卡片 + 图标 + 提示语（与控制页一致）
        AnimatedVisibility(
            visible = !connected,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bluetooth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp),
                    )
                    Text(
                        text = stringResource(R.string.disconnected_overlay),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        text = stringResource(R.string.disconnected_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/** 运行时间独立卡片：图标 + 标签 + 呼吸点 + 大号时长 */
@Composable
private fun RuntimeCard(runtimeText: String, active: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.runtime),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f),
                )
                BreathingDot(active = active)
            }
            Text(
                text = runtimeText,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(start = 28.dp, top = 6.dp),
            )
        }
    }
}

/** 状态呼吸点：数据正常时缓慢呼吸的绿色圆点，中断/断连时静止红色 */
@Composable
private fun BreathingDot(active: Boolean) {
    val transition = rememberInfiniteTransition(label = "breath")
    val factor by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathFactor",
    )
    val color by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
        label = "dotColor",
    )
    val alpha by animateFloatAsState(
        targetValue = if (active) factor else 1f,
        animationSpec = tween(300),
        label = "dotAlpha",
    )
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha)),
    )
}

@Composable
private fun LegendDot(color: Color) {
    Box(
        modifier = Modifier
            .size(11.dp)
            .clip(CircleShape)
            .background(color),
    )
}

/**
 * 数据状态圆点：绿 = 正常更新（扩散波纹），红 = 数据中断/无数据。
 * 扩散限定在自身区域内，不影响周围布局。
 */
@Composable
private fun StatusDot(active: Boolean) {
    val color by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
        label = "statusDot",
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(24.dp)) {
        if (active) {
            val transition = rememberInfiniteTransition(label = "statusPulse")
            val progress by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
                label = "pulse",
            )
            val alpha = (1f - progress) * 0.45f
            Box(
                modifier = Modifier
                    .size(20.dp * (0.45f + progress * 0.55f))
                    .clip(CircleShape)
                    .background(color.copy(alpha = alpha)),
            )
        }
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
    }
}

/** 单线图表卡片：头部（名称+图例色点 | 实时数值+单位 | 状态圆点）+ 渐变面积曲线 */
@Composable
private fun SingleChartCard(
    title: String,
    value: Float?,
    unit: String,
    format: (Float) -> String,
    xTimes: List<Float>,
    values: List<Float>,
    bounds: FloatArray,
    color: Color,
    statusActive: Boolean,
    viewportStart: State<Float>,
    viewportWindow: State<Float>,
    frameTick: State<Long>,
    ready: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            // 头部：名称（左）+ 实时数值与状态圆点（右），保留左右内边距
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp),
            ) {
                LegendDot(color)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (value == null) {
                    Text(
                        text = "--",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                } else {
                    AnimatedNumber(
                        target = value,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        format = format,
                    )
                    Text(
                        text = " $unit",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                Spacer(Modifier.width(6.dp))
                StatusDot(active = statusActive)
            }

            Spacer(Modifier.height(6.dp))

            // 图表区：无轴无网格，曲线 + 向下渐变面积
            SingleLineChart(
                xTimes = xTimes,
                values = values,
                color = color,
                yMin = bounds[0],
                yMax = bounds[1],
                viewportStart = viewportStart,
                viewportWindow = viewportWindow,
                frameTick = frameTick,
                ready = ready,
            )
        }
    }
}

/** 单条曲线的无轴图表：渐变面积 + 平滑曲线（左右撑满、无数据点标记） */
@Composable
private fun SingleLineChart(
    xTimes: List<Float>,
    values: List<Float>,
    color: Color,
    yMin: Float,
    yMax: Float,
    viewportStart: State<Float>,
    viewportWindow: State<Float>,
    frameTick: State<Long>,
    ready: Boolean,
) {
    val density = LocalDensity.current
    // Y 轴范围平滑：首帧直接对齐，之后每帧向目标指数趋近。
    // 仅在绘制期读写，不触发重组（与 viewportStart 同一模式）。
    val smooth = remember { mutableStateOf(Pair(Float.NaN, Float.NaN)) }
    // 右缘虚拟锚点 Y（值域）：每帧向最新数据平滑趋近，消除 250ms 阶跃突进
    val headY = remember { mutableStateOf(Float.NaN) }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT),
    ) {
        // 读帧号：每帧重绘；仅在此绘制期读取，不触发重组
        frameTick.value
        val vs = viewportStart.value
        val window = viewportWindow.value

        if (!ready || xTimes.size < 2 || values.size < 2) return@Canvas

        val padTop = with(density) { 8.dp.toPx() }
        val padBottom = with(density) { 6.dp.toPx() }
        // 左右撑满卡片内容区，无留白
        val gridLeft = 0f
        val gridRight = size.width
        val plotW = gridRight - gridLeft
        val plotH = size.height - padTop - padBottom
        if (plotW <= 0f || plotH <= 0f) return@Canvas

        // Y 轴范围逐帧趋近目标（指数缓动：快起步、慢收尾，缩放不生硬）
        var (bLo, bHi) = smooth.value
        if (bLo.isNaN()) {
            bLo = yMin
            bHi = yMax
        } else {
            bLo += (yMin - bLo) * Y_LERP
            bHi += (yMax - bHi) * Y_LERP
        }
        smooth.value = bLo to bHi

        // 右缘虚拟锚点：X 锁视口右缘（最远延伸 0.5s 到最新数据），Y 值域平滑趋近最新值
        val yBase = bHi - bLo
        val headTarget = values[values.size - 1]
        val hv = headY.value
        val head = if (hv.isNaN()) headTarget else hv + (headTarget - hv) * HEAD_LERP
        headY.value = head
        val latestTime = xTimes[xTimes.size - 1]
        val virtTime = minOf(vs + window, latestTime + VIRT_EXT_SEC)
        val virtX = gridLeft + (virtTime - vs) / window * plotW
        val virtY = padTop + plotH * (bHi - head) / yBase

        drawScopeClipRect(
            left = gridLeft, top = padTop, right = gridRight, bottom = padTop + plotH,
            clipOp = ClipOp.Intersect,
        ) {
            drawGradientArea(
                xTimes = xTimes, values = values, color = color,
                yMin = bLo, yMax = bHi,
                viewportStart = vs,
                window = window,
                gridLeft = gridLeft, gridRight = gridRight,
                padTop = padTop, plotW = plotW, plotH = plotH,
                virtX = virtX, virtY = virtY,
            )
        }
    }
}

/** 二分查找：第一个 >= target 的下标（x 单调递增） */
private fun lowerBound(list: List<Float>, target: Float): Int {
    var lo = 0
    var hi = list.size
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (list[mid] < target) lo = mid + 1 else hi = mid
    }
    return lo
}

/**
 * 绘制单条曲线：Catmull-Rom 样条转三次贝塞尔（圆滑、经过真实点、自带温和过冲）
 * + 向下渐变面积（顶部 0.26 → 底部 0.015）。
 * 尾部追加一个「右缘虚拟锚点」，使曲线右端连续延伸至视口右缘。
 */
private fun DrawScope.drawGradientArea(
    xTimes: List<Float>,
    values: List<Float>,
    color: Color,
    yMin: Float,
    yMax: Float,
    viewportStart: Float,
    window: Float,
    gridLeft: Float,
    gridRight: Float,
    padTop: Float,
    plotW: Float,
    plotH: Float,
    virtX: Float,
    virtY: Float,
) {
    if (xTimes.isEmpty() || values.isEmpty()) return
    val n = minOf(xTimes.size, values.size)
    val yBase = yMax - yMin
    if (yBase <= 0f) return
    if (window <= 0f) return

    val startX = viewportStart - window
    val endX = viewportStart + window * 1.4f
    val start = lowerBound(xTimes, startX).coerceIn(1, n - 1)

    // 1. 收集可见窗口内的真实点（含右边缘余量），转像素坐标。
    //    抽稀：相邻点像素间距 < MIN_SEG_PX 时丢弃（点太密转角显不出弧度）；
    //    方向反转（峰谷）点强制保留，避免削掉真实波动。
    val px = ArrayList<Float>()
    val py = ArrayList<Float>()
    var lastX = -Float.MAX_VALUE
    var lastY = 0f
    var lastSlopeSign = 0
    var i = start
    while (i < n) {
        val x = xTimes[i]
        if (x > endX) break
        val pxv = gridLeft + (x - viewportStart) / window * plotW
        val pyv = padTop + plotH * (yMax - values[i]) / yBase
        val slopeSign = if (pyv > lastY) 1 else if (pyv < lastY) -1 else 0
        val keep = pxv - lastX >= MIN_SEG_PX || (lastSlopeSign != 0 && slopeSign != lastSlopeSign)
        if (keep) {
            px.add(pxv)
            py.add(pyv)
            lastX = pxv
        }
        lastY = pyv
        if (slopeSign != 0) lastSlopeSign = slopeSign
        i++
    }
    if (px.size < 2 || px[0] > gridRight) return

    // 2. 尾部追加右缘虚拟锚点（X≈右缘、Y 平滑过渡），消除 250ms 阶跃突进
    px.add(virtX)
    py.add(virtY)
    val m = px.size

    // 3. 三次贝塞尔平滑（FlClash 式）：控制点由前后采样点推算，
    //    整条曲线一阶导数连续、拐角圆润无尖角；采样点位置本身不被修改
    val linePath = Path()
    linePath.moveTo(px[0], py[0])
    for (k in 0 until m - 1) {
        val x0 = px[k]
        val y0 = py[k]
        val x1 = px[k + 1]
        val y1 = py[k + 1]
        val h = x1 - x0
        if (h <= 0f) {
            linePath.lineTo(x1, y1)
            continue
        }
        // 边界复用自身点位兜底（首点 prev=自身、尾点 next=自身），防止越界
        val prevK = (k - 1).coerceAtLeast(0)
        val nextK = (k + 2).coerceAtMost(m - 1)
        val c1x = x0 + (x1 - px[prevK]) * TENSION
        val c1y = y0 + (y1 - py[prevK]) * TENSION
        val c2x = x1 - (px[nextK] - x0) * TENSION
        val c2y = y1 - (py[nextK] - y0) * TENSION
        linePath.cubicTo(c1x, c1y, c2x, c2y, x1, y1)
    }

    val bottomY = padTop + plotH
    // 面积闭合右边界：不超过绘图区右缘
    val closePx = px[m - 1].coerceAtMost(gridRight)

    // 1. 渐变面积：曲线闭合到绘图区底部，颜色向下渐透明
    val areaPath = Path().apply { addPath(linePath) }
    areaPath.lineTo(closePx, bottomY)
    areaPath.lineTo(px[0], bottomY)
    areaPath.close()
    drawPath(
        path = areaPath,
        brush = Brush.verticalGradient(
            colors = listOf(color.copy(alpha = AREA_ALPHA_TOP), color.copy(alpha = AREA_ALPHA_BOTTOM)),
            startY = padTop - 8f,
            endY = bottomY,
        ),
    )

    // 2. 曲线
    drawPath(
        path = linePath,
        color = color,
        style = Stroke(
            width = LINE_STROKE,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
}