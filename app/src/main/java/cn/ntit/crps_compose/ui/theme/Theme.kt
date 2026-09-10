package cn.ntit.crps_compose.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import cn.ntit.crps_compose.theme.ThemePalette

/**
 * 把色板转为 Material3 ColorScheme，每色经 animateColorAsState 过渡，
 * 主题切换时全局颜色渐变（无 Activity 重建、无闪烁）。
 */
@Composable
private fun animatedColorScheme(p: ThemePalette, dark: Boolean) =
    if (dark) {
        darkColorScheme(
            primary = animateColor(p.primary), onPrimary = animateColor(p.onPrimary),
            primaryContainer = animateColor(p.primaryContainer), onPrimaryContainer = animateColor(p.onPrimaryContainer),
            secondary = animateColor(p.secondary), onSecondary = animateColor(p.onSecondary),
            secondaryContainer = animateColor(p.secondaryContainer), onSecondaryContainer = animateColor(p.onSecondaryContainer),
            tertiary = animateColor(p.tertiary), onTertiary = animateColor(p.onTertiary),
            tertiaryContainer = animateColor(p.tertiaryContainer), onTertiaryContainer = animateColor(p.onTertiaryContainer),
            background = animateColor(p.background), onBackground = animateColor(p.onBackground),
            surface = animateColor(p.surface), onSurface = animateColor(p.onSurface),
            surfaceVariant = animateColor(p.surfaceVariant), onSurfaceVariant = animateColor(p.onSurfaceVariant),
            outline = animateColor(p.outline), outlineVariant = animateColor(p.outlineVariant),
            surfaceContainerLowest = animateColor(p.surfaceContainerLowest),
            surfaceContainerLow = animateColor(p.surfaceContainerLow),
            surfaceContainer = animateColor(p.surfaceContainer),
            surfaceContainerHigh = animateColor(p.surfaceContainerHigh),
            surfaceContainerHighest = animateColor(p.surfaceContainerHighest),
        )
    } else {
        lightColorScheme(
            primary = animateColor(p.primary), onPrimary = animateColor(p.onPrimary),
            primaryContainer = animateColor(p.primaryContainer), onPrimaryContainer = animateColor(p.onPrimaryContainer),
            secondary = animateColor(p.secondary), onSecondary = animateColor(p.onSecondary),
            secondaryContainer = animateColor(p.secondaryContainer), onSecondaryContainer = animateColor(p.onSecondaryContainer),
            tertiary = animateColor(p.tertiary), onTertiary = animateColor(p.onTertiary),
            tertiaryContainer = animateColor(p.tertiaryContainer), onTertiaryContainer = animateColor(p.onTertiaryContainer),
            background = animateColor(p.background), onBackground = animateColor(p.onBackground),
            surface = animateColor(p.surface), onSurface = animateColor(p.onSurface),
            surfaceVariant = animateColor(p.surfaceVariant), onSurfaceVariant = animateColor(p.onSurfaceVariant),
            outline = animateColor(p.outline), outlineVariant = animateColor(p.outlineVariant),
            surfaceContainerLowest = animateColor(p.surfaceContainerLowest),
            surfaceContainerLow = animateColor(p.surfaceContainerLow),
            surfaceContainer = animateColor(p.surfaceContainer),
            surfaceContainerHigh = animateColor(p.surfaceContainerHigh),
            surfaceContainerHighest = animateColor(p.surfaceContainerHighest),
        )
    }

@Composable
private fun animateColor(target: Int): Color {
    val animated by animateColorAsState(
        targetValue = Color(target),
        animationSpec = tween(durationMillis = 420),
        label = "themeColor",
    )
    return animated
}

/**
 * 应用主题：根据预设索引 + 夜间模式生成动画 ColorScheme。
 *
 * @param presetIndex 存储索引，可为 ThemePalette.PRESET_CUSTOM
 * @param customPrimary 自定义主色（presetIndex == PRESET_CUSTOM 时生效）
 * @param darkModeOverride 夜间模式覆盖（ThemeStore.DARK_*）
 */
@Composable
fun CrpsTheme(
    presetIndex: Int,
    customPrimary: Int,
    darkModeOverride: Int,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (darkModeOverride) {
        1 -> true
        0 -> false
        else -> systemDark
    }

    val palette: ThemePalette = if (presetIndex == ThemePalette.PRESET_CUSTOM) {
        ThemePalette.generateFromPrimary(customPrimary, dark)
    } else {
        ThemePalette.getPreset(presetIndex, dark)
    }

    MaterialTheme(
        colorScheme = animatedColorScheme(palette, dark),
        content = content,
    )
}

/** 图表 6 色（按曲线顺序）：直接从色板读取，供 Canvas 使用 */
@Composable
fun chartColorsOf(presetIndex: Int, customPrimary: Int, darkModeOverride: Int): IntArray {
    val systemDark = isSystemInDarkTheme()
    val dark = when (darkModeOverride) {
        1 -> true
        0 -> false
        else -> systemDark
    }
    val palette: ThemePalette = if (presetIndex == ThemePalette.PRESET_CUSTOM) {
        ThemePalette.generateFromPrimary(customPrimary, dark)
    } else {
        ThemePalette.getPreset(presetIndex, dark)
    }
    return IntArray(6) { palette.chartColor(it) }
}