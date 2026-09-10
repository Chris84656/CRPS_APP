package cn.ntit.crps_compose.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.ntit.crps_compose.R
import cn.ntit.crps_compose.theme.ThemePalette
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/**
 * 主题配色选择 BottomSheet：4 列网格展示 12 套预设，
 * 点击色卡立即更换主题（颜色全局渐变过渡）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSheet(
    selectedIndex: Int,
    darkMode: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val systemDark = isSystemInDarkTheme()
    val dark = when (darkMode) {
        cn.ntit.crps_compose.theme.ThemeStore.DARK_DARK -> true
        cn.ntit.crps_compose.theme.ThemeStore.DARK_LIGHT -> false
        else -> systemDark
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentWindowInsets = { androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.theme_palette),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.presets_title),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
            )

            val storedSelected = when (selectedIndex) {
                ThemePalette.PRESET_CUSTOM -> -1 // 无自定义入口，仅防御
                else -> selectedIndex
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                items(count = ThemePalette.PRESETS_LIGHT.size) { displayIndex ->
                    val stored = ThemePalette.displayIndexToStored(displayIndex)
                    val palette = ThemePalette.getPreset(stored, dark)
                    val isSelected = stored == storedSelected

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(stored)
                                scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                            },
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .background(
                                    brush = Brush.horizontalGradient(
                                        listOf(
                                            Color(palette.primary),
                                            Color(palette.secondary),
                                        ),
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                )
                                .then(
                                    if (isSelected) {
                                        Modifier.border(
                                            width = 3.dp,
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = RoundedCornerShape(10.dp),
                                        )
                                    } else {
                                        Modifier
                                    },
                                ),
                        )
                        Text(
                            text = palette.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}