package cn.ntit.crps_compose.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cn.ntit.crps_compose.R
import cn.ntit.crps_compose.theme.ThemeStore
import cn.ntit.crps_compose.ui.chart.ChartScreen
import cn.ntit.crps_compose.ui.control.ControlScreen
import cn.ntit.crps_compose.ui.info.InfoScreen
import cn.ntit.crps_compose.viewmodel.MainViewModel

/** 底部导航三页 */
enum class MainTab(@StringRes val labelRes: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    CONTROL(R.string.tab_control, Icons.Outlined.PowerSettingsNew, Icons.Filled.PowerSettingsNew),
    DETAILS(R.string.tab_details, Icons.Outlined.ShowChart, Icons.Filled.ShowChart),
    INFO(R.string.tab_info, Icons.Outlined.Info, Icons.Filled.Info),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showThemeSheet by remember { mutableStateOf(false) }

    val connectionState by viewModel.connectionState.collectAsState()
    val preset by viewModel.presetIndex.collectAsState()
    val darkMode by viewModel.darkMode.collectAsState()

    // ============ 权限流程 ============
    val requiredPermissions: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun allGranted(): Boolean = requiredPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    var granted by remember { mutableStateOf(allGranted()) }
    var showPreDialog by remember { mutableStateOf(!granted) }
    var showDeniedDialog by remember { mutableStateOf(false) }
    var everRequested by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        everRequested = true
        val ok = result.values.all { it }
        granted = ok
        if (!ok) showDeniedDialog = true
    }

    // 从系统设置返回后复查权限
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && everRequested && !granted) {
                granted = allGranted()
                if (!granted) showDeniedDialog = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 权限就绪后启动自动连接（仅一次）
    LaunchedEffect(granted) {
        if (granted) viewModel.start()
    }

    // 应用切后台：暂停曲线缓存（减少内存与无效计算）
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.setChartPaused(true)
                Lifecycle.Event.ON_START -> viewModel.setChartPaused(false)
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // BLE 错误事件 → Snackbar（带重试动作）
    LaunchedEffect(Unit) {
        viewModel.errors.collect { message ->
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = context.getString(R.string.retry),
                withDismissAction = true,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.retry()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = {
                            (fadeIn(tween(160)) + slideInHorizontally(tween(200)) { it / 5 })
                                .togetherWith(fadeOut(tween(120)))
                        },
                        label = "title",
                    ) { t ->
                        Text(stringResource(MainTab.entries[t].labelRes))
                    }
                },
                actions = {
                    IconButton(onClick = { showThemeSheet = true }) {
                        Icon(Icons.Filled.Palette, contentDescription = stringResource(R.string.theme_palette))
                    }
                    IconButton(onClick = {
                        // 三态循环：跟随系统 → 亮色 → 暗色
                        val next = when (darkMode) {
                            ThemeStore.DARK_FOLLOW_SYSTEM -> ThemeStore.DARK_LIGHT
                            ThemeStore.DARK_LIGHT -> ThemeStore.DARK_DARK
                            else -> ThemeStore.DARK_FOLLOW_SYSTEM
                        }
                        viewModel.setDarkMode(next)
                    }) {
                        AnimatedContent(
                            targetState = darkMode,
                            transitionSpec = {
                                (fadeIn(tween(200)) + scaleIn(tween(200))).togetherWith(fadeOut(tween(120)))
                            },
                            label = "darkIcon",
                        ) { mode ->
                            val icon = when (mode) {
                                ThemeStore.DARK_DARK -> Icons.Filled.DarkMode
                                ThemeStore.DARK_LIGHT -> Icons.Filled.LightMode
                                else -> Icons.Filled.BrightnessAuto
                            }
                            Icon(icon, contentDescription = stringResource(R.string.dark_mode))
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEachIndexed { index, t ->
                    val selected = tab == index
                    // 选中图标弹簧放大
                    val scale by animateFloatAsState(
                        targetValue = if (selected) 1.18f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                        label = "tabScale",
                    )
                    NavigationBarItem(
                        selected = selected,
                        onClick = { tab = index },
                        icon = {
                            Icon(
                                imageVector = if (selected) t.selectedIcon else t.icon,
                                contentDescription = stringResource(t.labelRes),
                                modifier = Modifier
                                    .size(26.dp)
                                    .scale(scale),
                            )
                        },
                        label = { Text(stringResource(t.labelRes)) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            AnimatedContent(
                targetState = MainTab.entries[tab],
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val dir = if (forward) 1 else -1
                    (slideInHorizontally(tween(280)) { it / 6 * dir } + fadeIn(tween(220)))
                        .togetherWith(slideOutHorizontally(tween(220)) { -it / 6 * dir } + fadeOut(tween(180)))
                        .using(SizeTransform(clip = false))
                },
                label = "page",
            ) { target ->
                when (target) {
                    MainTab.CONTROL -> ControlScreen(viewModel)
                    MainTab.DETAILS -> ChartScreen(viewModel)
                    MainTab.INFO -> InfoScreen(viewModel)
                }
            }
        }
    }

    // ============ 首次权限说明 ============
    if (showPreDialog) {
        val isBle = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        AlertDialog(
            onDismissRequest = { showPreDialog = false },
            title = { Text(stringResource(if (isBle) R.string.permission_ble_title else R.string.permission_location_title)) },
            text = { Text(stringResource(if (isBle) R.string.permission_ble_message else R.string.permission_location_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showPreDialog = false
                    permissionLauncher.launch(requiredPermissions)
                }) { Text("确定") }
            },
        )
    }

    // ============ 权限被拒引导设置 ============
    if (showDeniedDialog) {
        AlertDialog(
            onDismissRequest = { showDeniedDialog = false },
            title = { Text(stringResource(R.string.permission_ble_title)) },
            text = { Text(stringResource(R.string.permission_denied_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeniedDialog = false
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(Uri.fromParts("package", context.packageName, null)),
                        )
                    }
                }) { Text(stringResource(R.string.open_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeniedDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    // ============ 主题配色 BottomSheet ============
    if (showThemeSheet) {
        ThemeSheet(
            selectedIndex = preset,
            darkMode = darkMode,
            onSelect = { viewModel.setPresetIndex(it) },
            onDismiss = { showThemeSheet = false },
        )
    }
}