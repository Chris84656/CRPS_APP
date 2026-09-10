package cn.ntit.crps_compose.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle

/**
 * 小数平滑滚动组件：目标值变化时以弹簧/缓动动画过渡，
 * 取代旧版的硬 setText 跳变。
 */
@Composable
fun AnimatedNumber(
    target: Float,
    modifier: Modifier = Modifier,
    style: TextStyle,
    format: (Float) -> String,
    springy: Boolean = false,
) {
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = if (springy) spring(stiffness = 200f) else tween(durationMillis = 280),
        label = "animatedNumber",
    )
    androidx.compose.material3.Text(
        text = format(animated),
        modifier = modifier,
        style = style,
    )
}