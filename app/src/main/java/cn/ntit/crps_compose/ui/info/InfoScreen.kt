package cn.ntit.crps_compose.ui.info

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.ntit.crps_compose.R
import cn.ntit.crps_compose.ble.BleManager
import cn.ntit.crps_compose.data.StaticData
import cn.ntit.crps_compose.util.NumberFormatter
import cn.ntit.crps_compose.viewmodel.MainViewModel

/** 信息页：电源静态信息 + 状态寄存器 */
@Composable
fun InfoScreen(viewModel: MainViewModel) {
    val static by viewModel.staticData.collectAsState()
    val dynamic by viewModel.dynamicData.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val staticInterrupted by viewModel.staticInterrupted.collectAsState()

    val connected = connectionState == BleManager.STATE_CONNECTED
    val hasData = connected && static != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        // 空状态
        AnimatedVisibility(
            visible = !hasData,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(140)),
        ) {
            Text(
                text = stringResource(R.string.no_info),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
            )
        }

        // 电源信息卡
        AnimatedVisibility(
            visible = hasData,
            enter = fadeIn(tween(260)),
            exit = fadeOut(tween(160)),
        ) {
            Column {
                InfoCard {
                    InfoRow(stringResource(R.string.manufacturer), static?.midDisplay)
                    InfoRow(stringResource(R.string.model), static?.mmDisplay)
                    InfoRow(stringResource(R.string.rated_power), static?.rpmax?.let { "${(it).toInt()} W" })
                    InfoRow(stringResource(R.string.rated_current), static?.rimax?.let { "${it.toInt()} A" })
                    InfoRow(stringResource(R.string.pmbus_version), static?.prevDisplay)
                    InfoRow(stringResource(R.string.serial_number), static?.msnDisplay)
                    InfoRow(stringResource(R.string.manufacture_location), static?.mlocDisplay)
                    InfoRow(stringResource(R.string.manufacture_date), static?.mdateDisplay)
                    InfoRow(stringResource(R.string.firmware_version), static?.mrevDisplay)
                    InfoRow(stringResource(R.string.ambient_otp), static?.rt1?.let { "${it.toInt()} °C" })
                    InfoRow(stringResource(R.string.hotspot_otp), static?.rt2?.let { "${it.toInt()} °C" })
                }

                // 状态寄存器卡
                Text(
                    text = stringResource(R.string.status_registers),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
                )
                InfoCard {
                    dynamic?.let { d ->
                        StatusRow(stringResource(R.string.status_iout), d.si)
                        StatusRow(stringResource(R.string.status_input), d.sn)
                        StatusRow(stringResource(R.string.status_temperature), d.st)
                        StatusRow(stringResource(R.string.status_fans), d.sf)
                    }
                }

                // 静态信息中断提示
                AnimatedVisibility(
                    visible = staticInterrupted && connected,
                    enter = fadeIn(tween(220)) + expandVertically(tween(260)),
                    exit = fadeOut(tween(160)) + shrinkVertically(tween(220)),
                ) {
                    Text(
                        text = stringResource(R.string.static_interrupted),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                }
            }
        }

        // 版本信息
        Text(
            text = "CRPS Monitor\nAuthor: Chris84656\nVersion: V1.0 (Compose)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = MaterialTheme.typography.labelSmall.fontSize * 1.6f,
        )
    }
}

@Composable
private fun InfoCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), content = content)
    }
}

@Composable
private fun InfoRow(label: String, value: String?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value ?: stringResource(R.string.unknown),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun StatusRow(label: String, value: Int) {
    var expanded by rememberSaveable(label) { mutableStateOf(false) }

    val (stateText, stateColor) = when {
        value == 0x0000 -> stringResource(R.string.status_normal) to MaterialTheme.colorScheme.tertiary
        value == 0x0001 -> stringResource(R.string.status_abnormal) to MaterialTheme.colorScheme.error
        else -> stringResource(R.string.status_unknown) to MaterialTheme.colorScheme.onSurfaceVariant
    }

    val arrowAngle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(240, easing = FastOutSlowInEasing),
        label = "arrowAngle",
    )

    // 整行展开：AnimatedVisibility 让高度与透明度同步过渡，与箭头旋转共用缓动，视觉更协调
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stateText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = stateColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(stateColor.copy(alpha = 0.14f))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            )
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .rotate(arrowAngle),
            )
        }
        // 展开的寄存器值：等宽字体十六进制，浅色底衬；高度 + 透明度同步展开收起
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(240, easing = FastOutSlowInEasing)) +
                fadeIn(tween(240, easing = FastOutSlowInEasing)),
            exit = shrinkVertically(tween(200, easing = FastOutSlowInEasing)) +
                fadeOut(tween(160)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                Text(
                    text = NumberFormatter.hex(value),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}