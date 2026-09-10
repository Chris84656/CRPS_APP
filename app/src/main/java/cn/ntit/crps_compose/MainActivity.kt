package cn.ntit.crps_compose

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cn.ntit.crps_compose.ui.MainScreen
import cn.ntit.crps_compose.ui.theme.CrpsTheme
import cn.ntit.crps_compose.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // targetSdk 36 强制性 edge-to-edge：内容延伸到系统栏，由 Compose WindowInsets 处理内边距
        enableEdgeToEdge()

        // 监控应用需保持数据实时可见，避免长时间无操作触发系统自动熄屏
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            val preset by viewModel.presetIndex.collectAsState()
            val darkMode by viewModel.darkMode.collectAsState()

            CrpsTheme(
                presetIndex = preset,
                customPrimary = 0xFF2C52B8.toInt(),
                darkModeOverride = darkMode,
            ) {
                MainScreen(viewModel)
            }
        }
    }
}