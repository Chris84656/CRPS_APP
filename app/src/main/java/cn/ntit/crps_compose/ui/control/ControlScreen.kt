package cn.ntit.crps_compose.ui.control

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.ntit.crps_compose.R
import cn.ntit.crps_compose.ble.BleManager
import cn.ntit.crps_compose.ui.components.AnimatedNumber
import cn.ntit.crps_compose.util.NumberFormatter
import cn.ntit.crps_compose.viewmodel.MainViewModel

/** 控制页：设备连接 + 电源开关 + AC/DC 读数 + 温度 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlScreen(viewModel: MainViewModel) {
    val connectionState by viewModel.connectionState.collectAsState()
    val devices by viewModel.scannedDevices.collectAsState()
    val connectedDevice by viewModel.connectedDevice.collectAsState()
    val dynamic by viewModel.dynamicData.collectAsState()
    val interrupted by viewModel.dataInterrupted.collectAsState()
    val powerPending by viewModel.powerPending.collectAsState()
    val powerTimeout by viewModel.powerTimeout.collectAsState()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val connected = connectionState == BleManager.STATE_CONNECTED

    // 电源开关本地状态
    var pendingTarget by remember { mutableStateOf<Boolean?>(null) }
    var lastTarget by rememberSaveable { mutableStateOf(false) }

    // 设备选择
    var selectedAddress by rememberSaveable { mutableStateOf<String?>(null) }
    var deviceMenuExpanded by remember { mutableStateOf(false) }

    // 电源命令超时提示（含重试）
    LaunchedEffect(powerTimeout) {
        if (powerTimeout) {
            val r = snackbarHostState.showSnackbar(
                message = context.getString(R.string.operation_timeout),
                actionLabel = context.getString(R.string.retry),
                withDismissAction = true,
            )
            if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                viewModel.confirmPowerRetry()
            }
        }
    }

    // 命令被确认/超时后清除 pending 视觉状态
    LaunchedEffect(powerPending) {
        if (!powerPending) pendingTarget = null
    }

    // 去掉内层 Scaffold：外层 MainScreen 已处理上下栏 insets，
    // 这里用 Box + SnackbarHost 承载全局 Snackbar 与断连遮罩，结构更干净
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // 数据中断横幅（展开/收起动画）
            AnimatedVisibility(
                visible = interrupted && connected,
                enter = fadeIn(tween(220)) + expandVertically(tween(260)),
                exit = fadeOut(tween(160)) + shrinkVertically(tween(220)),
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.data_interrupted),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            // ============ 区块 A：设备连接 ============
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BluetoothIcon(connected = connected, active = connectionState != BleManager.STATE_DISCONNECTED)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.device_connection),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    StatusPill(state = connectionState)
                }

                // 设备选择 + 扫描按钮
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    ExposedDropdownMenuBox(
                        expanded = deviceMenuExpanded,
                        onExpandedChange = { deviceMenuExpanded = it },
                        modifier = Modifier.weight(1f),
                    ) {
                        OutlinedTextField(
                            value = connectedDevice?.name
                                ?: devices.firstOrNull { it.address == selectedAddress }?.name
                                ?: stringResource(R.string.select_device),
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            label = { Text(stringResource(R.string.select_device)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = deviceMenuExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                        )
                        ExposedDropdownMenu(
                            expanded = deviceMenuExpanded,
                            onDismissRequest = { deviceMenuExpanded = false },
                        ) {
                            if (devices.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.scanning),
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                devices.forEach { device ->
                                    DropdownMenuItem(
                                        text = { Text("${device.name}  (${device.rssi} dBm)") },
                                        onClick = {
                                            selectedAddress = device.address
                                            deviceMenuExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }

                    ScanButton(
                        active = connectionState == BleManager.STATE_SCANNING,
                        enabled = connectionState != BleManager.STATE_CONNECTING && !connected,
                        onClick = { viewModel.retry() },
                    )
                }

                // 连接按钮
                Button(
                    onClick = {
                        if (connected) {
                            viewModel.disconnect()
                        } else {
                            val addr = selectedAddress
                                ?: devices.firstOrNull()?.address
                            if (addr != null) {
                                viewModel.connect(addr)
                                selectedAddress = addr
                            } else {
                                viewModel.retry()
                            }
                        }
                    },
                    enabled = when (connectionState) {
                        BleManager.STATE_CONNECTING -> false
                        BleManager.STATE_CONNECTED -> true
                        else -> true
                    },
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    AnimatedContent(
                        targetState = connectionState,
                        transitionSpec = { fadeIn(tween(180)).togetherWith(fadeOut(tween(120))) },
                        label = "btnText",
                    ) { state ->
                        Text(
                            when (state) {
                                BleManager.STATE_CONNECTED -> stringResource(R.string.disconnect)
                                BleManager.STATE_CONNECTING -> stringResource(R.string.connecting)
                                else -> stringResource(R.string.connect)
                            },
                        )
                    }
                }
            }

            // ============ 数据区（断连时模糊 + 遮罩） ============
            Box(modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)) {
                Column(
                    modifier = Modifier.blur(if (connected) 0.dp else 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // 区块 B：电源开关
                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PowerIcon(on = dynamic?.isPowerOn == true)
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.power_state),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                val powerOn = dynamic?.isPowerOn == true
                                val powerTextColor by animateColorAsState(
                                    targetValue = if (powerOn) MaterialTheme.colorScheme.tertiary
                                    else MaterialTheme.colorScheme.error,
                                    label = "powerText",
                                )
                                AnimatedContent(
                                    targetState = powerOn,
                                    transitionSpec = { fadeIn(tween(180)).togetherWith(fadeOut(tween(120))) },
                                    label = "powerStatus",
                                ) { on ->
                                    Text(
                                        text = stringResource(if (on) R.string.power_on else R.string.power_off),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = powerTextColor,
                                    )
                                }
                            }
                            Switch(
                                checked = pendingTarget ?: (dynamic?.isPowerOn == true),
                                onCheckedChange = { on ->
                                    if (!powerPending) {
                                        pendingTarget = on
                                        lastTarget = on
                                        viewModel.sendPower(on)
                                    }
                                },
                                enabled = connected && !powerPending,
                            )
                        }
                    }

                    // 区块 C：AC 输入 + DC 输出
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ReadingCard(
                            title = stringResource(R.string.ac_input),
                            accentColor = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f),
                        ) {
                            ReadingRow("Vin", dynamic?.vin, "V", NumberFormatter::voltage)
                            ReadingRow("Iin", dynamic?.iin, "A", NumberFormatter::current)
                            ReadingRow("Pin", dynamic?.let { it.vin * it.iin }, "W", NumberFormatter::power)
                        }
                        ReadingCard(
                            title = stringResource(R.string.dc_output),
                            accentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        ) {
                            ReadingRow("Vout", dynamic?.vout, "V", NumberFormatter::voltage)
                            ReadingRow("Iout", dynamic?.iout, "A", NumberFormatter::current)
                            ReadingRow("Pout", dynamic?.pout, "W", NumberFormatter::power)
                        }
                    }

                    // 区块 C2：转换效率（跨两卡宽度的独立卡片，避免与输入/输出卡片重复）
                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Speed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.conversion_efficiency),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.width(8.dp))
                            // 低功率下效率测量不可靠，仅作参考（弱化：更小更灰，不抢主信息注意力）
                            Text(
                                text = stringResource(R.string.low_power_hint),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(top = 1.dp),
                            )
                            if (dynamic?.eff == null) {
                                Text(
                                    text = "--",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            } else {
                                val effTarget = requireNotNull(dynamic?.eff).toFloat()
                                AnimatedNumber(
                                    target = effTarget,
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    format = { NumberFormatter.efficiency(it.toInt()) },
                                )
                                Text(
                                    text = " %",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 3.dp),
                                )
                            }
                        }
                    }

                    // 区块 D：温度 + 风扇
                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Thermostat,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.temperature_monitor),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                        ) {
                            TempCell(
                                label = stringResource(R.string.ambient_t1),
                                icon = Icons.Filled.Thermostat,
                                iconTint = MaterialTheme.colorScheme.tertiary,
                                value = dynamic?.t1,
                                unit = "°C",
                            )
                            VerticalDivider()
                            TempCell(
                                label = stringResource(R.string.hotspot_t2),
                                icon = Icons.Filled.Thermostat,
                                iconTint = MaterialTheme.colorScheme.error,
                                value = dynamic?.t2,
                                unit = "°C",
                            )
                            VerticalDivider()
                            TempCell(
                                label = stringResource(R.string.fan_speed),
                                icon = Icons.Filled.Air,
                                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                                value = dynamic?.fan?.toFloat(),
                                unit = "RPM",
                                format = { NumberFormatter.fanSpeed(it.toInt()) },
                            )
                        }
                    }
                }
            }
        }

        // 断连遮罩：相对视口居中（滚动时固定），不再受嵌套 Scaffold insets 影响
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(16.dp),
        ) {
            DisconnectedOverlay(visible = !connected)
        }

        // Snackbar：置于内容最上层，不被滚动内容或遮罩遮挡
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** 断连遮罩：圆角卡片 + 图标 + 提示语，淡入缩放，视觉更克制 */
@Composable
private fun DisconnectedOverlay(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)) + scaleIn(
            animationSpec = tween(260, easing = FastOutSlowInEasing),
            initialScale = 0.9f,
        ),
        exit = fadeOut(tween(200)),
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

// ==================== 小组件 ====================

/** 状态胶囊：颜色随连接状态平滑过渡 */
@Composable
private fun StatusPill(state: Int) {
    val bg: androidx.compose.ui.graphics.Color
    val content: androidx.compose.ui.graphics.Color
    val res: Int
    when (state) {
        BleManager.STATE_CONNECTED -> {
            bg = MaterialTheme.colorScheme.primaryContainer
            content = MaterialTheme.colorScheme.onPrimaryContainer
            res = R.string.connected
        }
        BleManager.STATE_CONNECTING -> {
            bg = MaterialTheme.colorScheme.secondaryContainer
            content = MaterialTheme.colorScheme.onSecondaryContainer
            res = R.string.connecting
        }
        BleManager.STATE_SCANNING -> {
            bg = MaterialTheme.colorScheme.tertiaryContainer
            content = MaterialTheme.colorScheme.onTertiaryContainer
            res = R.string.scanning
        }
        else -> {
            bg = MaterialTheme.colorScheme.surfaceContainerHighest
            content = MaterialTheme.colorScheme.onSurfaceVariant
            res = R.string.not_connected
        }
    }
    val bgAnim by animateColorAsState(bg, label = "pillBg")
    val contentAnim by animateColorAsState(content, label = "pillContent")
    Text(
        text = stringResource(res),
        style = MaterialTheme.typography.labelMedium,
        color = contentAnim,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bgAnim)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

/** 蓝牙图标：连接时显示扩散波纹 */
@Composable
private fun BluetoothIcon(connected: Boolean, active: Boolean) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(28.dp)) {
        if (connected) {
            val transition = rememberInfiniteTransition(label = "bluetoothPulse")
            val progress by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
                label = "pulse",
            )
            val alpha = (1f - progress) * 0.45f
            Box(
                modifier = Modifier
                    .size(26.dp * (0.7f + progress * 0.55f))
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha)),
            )
        }
        Icon(
            imageVector = Icons.Filled.Bluetooth,
            contentDescription = null,
            tint = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 扫描按钮：扫描时持续旋转 */
@Composable
private fun ScanButton(active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val angle by animateFloatAsState(
        targetValue = if (active) 360f else 0f,
        animationSpec = if (active) {
            infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Restart)
        } else {
            tween(200)
        },
        label = "scanAngle",
    )
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .padding(start = 8.dp)
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Icon(
            imageVector = Icons.Filled.Refresh,
            contentDescription = "扫描",
            tint = if (active) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            modifier = Modifier.rotate(angle),
        )
    }
}

/** 电源圆形图标：颜色随开关状态过渡 */
@Composable
private fun PowerIcon(on: Boolean) {
    val bg by animateColorAsState(
        targetValue = if (on) MaterialTheme.colorScheme.tertiaryContainer
        else MaterialTheme.colorScheme.errorContainer,
        label = "powerIconBg",
    )
    val tint by animateColorAsState(
        targetValue = if (on) MaterialTheme.colorScheme.onTertiaryContainer
        else MaterialTheme.colorScheme.onErrorContainer,
        label = "powerIconTint",
    )
    // 开启时弹簧弹入
    val scale by animateFloatAsState(
        targetValue = if (on) 1f else 0.92f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
        ),
        label = "powerScale",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(bg),
    ) {
        Icon(
            imageVector = Icons.Filled.PowerSettingsNew,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(26.dp),
        )
    }
}

@Composable
private fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun ReadingCard(
    title: String,
    accentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(accentColor, RoundedCornerShape(2.dp)),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            content()
        }
    }
}

@Composable
private fun ReadingRow(
    label: String,
    value: Float?,
    unit: String,
    format: (Float) -> String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (value == null) {
            Text("--", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        } else {
            AnimatedNumber(
                target = value,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                format = format,
            )
        }
        Text(
            text = " $unit",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TempCell(
    label: String,
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    value: Float?,
    unit: String,
    format: (Float) -> String = { NumberFormatter.temperature(it) },
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
            if (value == null) {
                Text("--", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            } else {
                AnimatedNumber(
                    target = value,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    format = format,
                )
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun VerticalDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(64.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}