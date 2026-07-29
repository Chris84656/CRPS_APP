package cn.ntit.crps_re0;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

import cn.ntit.crps_re0.ble.BleManager;
import cn.ntit.crps_re0.model.DynamicData;
import cn.ntit.crps_re0.model.StaticData;
import cn.ntit.crps_re0.theme.ThemeManager;
import cn.ntit.crps_re0.ui.ControlFragment;
import cn.ntit.crps_re0.ui.DetailsFragment;
import cn.ntit.crps_re0.ui.InfoFragment;
import cn.ntit.crps_re0.ui.ThemeBottomSheet;
import cn.ntit.crps_re0.viewmodel.SharedViewModel;

public class MainActivity extends AppCompatActivity implements BleManager.BleCallback {

    private static final int REQ_PERMISSIONS = 1001;
    private static final int REQ_APP_SETTINGS = 1002;
    private static final String PREFS_NAME = "crps_prefs";
    private static final String KEY_LAST_MAC = "last_mac";
    private static final String KEY_LAST_NAME = "last_name";
    private static final String KEY_THEME_MODE = "theme_mode";

    // #38: 标记权限是否曾被拒绝，用于 onResume 时复查
    private boolean permissionsDenied = false;

    private BleManager bleManager;
    private SharedViewModel viewModel;
    private MaterialToolbar toolbar;
    private Handler mainHandler;

    private ControlFragment controlFragment;
    private DetailsFragment detailsFragment;
    private InfoFragment infoFragment;
    private Fragment activeFragment;
    private Handler dataTimeoutHandler;
    private Runnable dataTimeoutRunnable;
    private Handler staticTimeoutHandler;
    private Runnable staticTimeoutRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        restoreThemeMode();
        applyThemeColors();
        setContentView(R.layout.activity_main);

        mainHandler = new Handler(Looper.getMainLooper());
        bleManager = BleManager.getInstance(this);
        bleManager.setCallback(this);
        viewModel = new ViewModelProvider(this).get(SharedViewModel.class);

        toolbar = findViewById(R.id.toolbar);
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);

        if (savedInstanceState != null) {
            activeTab = savedInstanceState.getInt(KEY_ACTIVE_TAB, 0);
        }

        setupFragments();
        setupBottomNav(bottomNav);
        setupToolbar();
        setupDataTimeout();

        // 恢复 toolbar 标题和图标
        if (activeTab == 1) {
            toolbar.setTitle(R.string.tab_details);
            toolbar.setNavigationIcon(R.drawable.ic_analytics);
        } else if (activeTab == 2) {
            toolbar.setTitle(R.string.tab_info);
            toolbar.setNavigationIcon(R.drawable.ic_info);
        }

        // 推送当前图表颜色到 ViewModel
        viewModel.setChartColors(ThemeManager.getCurrentColors(this).chartColors);

        checkPermissionsAndStart();
    }

    /**
     * 应用当前主题配色到 Activity 级别元素（状态栏、导航栏、toolbar、window 背景等）。
     * 在 setContentView 之前调用，确保视图创建时就用对颜色。
     */
    private void applyThemeColors() {
        int presetIndex = ThemeManager.loadPresetIndex(this);
        if (presetIndex != ThemeManager.PRESET_CUSTOM) {
            int styleResId = getPresetStyleResId(presetIndex);
            if (styleResId != 0) {
                getTheme().applyStyle(styleResId, true);
            }
        }

        ThemeManager.ThemeColorSet colors = ThemeManager.getCurrentColors(this);
        Window window = getWindow();

        window.setStatusBarColor(colors.surface);
        window.setNavigationBarColor(colors.surfaceContainer);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            View decor = window.getDecorView();
            int flags = decor.getSystemUiVisibility();
            if (isLightColor(colors.surface)) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            decor.setSystemUiVisibility(flags);
        }
    }

    private boolean isLightColor(int color) {
        int r = android.graphics.Color.red(color);
        int g = android.graphics.Color.green(color);
        int b = android.graphics.Color.blue(color);
        double lum = 0.299 * r + 0.587 * g + 0.114 * b;
        return lum > 180;
    }

    private int getPresetStyleResId(int index) {
        switch (index) {
            case 0: return R.style.ThemeOverlay_CRPS_APP_Preset0;
            case 1: return R.style.ThemeOverlay_CRPS_APP_Preset1;
            case 2: return R.style.ThemeOverlay_CRPS_APP_Preset2;
            case 3: return R.style.ThemeOverlay_CRPS_APP_Preset3;
            case 4: return R.style.ThemeOverlay_CRPS_APP_Preset4;
            case 5: return R.style.ThemeOverlay_CRPS_APP_Preset5;
            case 6: return R.style.ThemeOverlay_CRPS_APP_Preset6;
            case 7: return R.style.ThemeOverlay_CRPS_APP_Preset7;
            case 8: return R.style.ThemeOverlay_CRPS_APP_Preset8;
            case 9: return R.style.ThemeOverlay_CRPS_APP_Preset9;
            case 10: return R.style.ThemeOverlay_CRPS_APP_Preset10;
            case 11: return R.style.ThemeOverlay_CRPS_APP_Preset11;
            default: return 0;
        }
    }

    private static final String KEY_ACTIVE_TAB = "active_tab";
    private int activeTab = 0;

    private void setupFragments() {
        controlFragment = (ControlFragment) getSupportFragmentManager().findFragmentByTag("control");
        detailsFragment = (DetailsFragment) getSupportFragmentManager().findFragmentByTag("details");
        infoFragment = (InfoFragment) getSupportFragmentManager().findFragmentByTag("info");

        if (controlFragment == null) {
            controlFragment = new ControlFragment();
            detailsFragment = new DetailsFragment();
            infoFragment = new InfoFragment();

            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, infoFragment, "info").hide(infoFragment)
                    .add(R.id.fragment_container, detailsFragment, "details").hide(detailsFragment)
                    .add(R.id.fragment_container, controlFragment, "control")
                    .commit();
        }
        // 恢复当前 Tab
        Fragment target;
        if (activeTab == 1) target = detailsFragment;
        else if (activeTab == 2) target = infoFragment;
        else target = controlFragment;

        getSupportFragmentManager().beginTransaction()
                .hide(controlFragment).hide(detailsFragment).hide(infoFragment)
                .show(target)
                .commit();
        activeFragment = target;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_ACTIVE_TAB, activeTab);
    }

    private void setupBottomNav(BottomNavigationView bottomNav) {
        // 恢复底部导航选中状态
        if (activeTab == 1) bottomNav.setSelectedItemId(R.id.nav_details);
        else if (activeTab == 2) bottomNav.setSelectedItemId(R.id.nav_info);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_control) {
                activeTab = 0;
                switchFragment(controlFragment);
                toolbar.setTitle(R.string.tab_control);
                toolbar.setNavigationIcon(R.drawable.ic_power);
                return true;
            } else if (id == R.id.nav_details) {
                activeTab = 1;
                switchFragment(detailsFragment);
                toolbar.setTitle(R.string.tab_details);
                toolbar.setNavigationIcon(R.drawable.ic_analytics);
                return true;
            } else if (id == R.id.nav_info) {
                activeTab = 2;
                switchFragment(infoFragment);
                toolbar.setTitle(R.string.tab_info);
                toolbar.setNavigationIcon(R.drawable.ic_info);
                return true;
            }
            return false;
        });
    }

    private void setupToolbar() {
        toolbar.inflateMenu(R.menu.bottom_nav_menu);
        toolbar.getMenu().clear();

        // 调色板按钮（order=0，在左边）
        toolbar.getMenu().add(0, 2, 0, "主题配色")
                .setIcon(R.drawable.ic_palette)
                .setShowAsAction(1);
        // 夜间模式按钮（order=1，在右边）
        toolbar.getMenu().add(0, 1, 1, "夜间模式")
                .setIcon(getThemeIcon())
                .setShowAsAction(1);

        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                cycleThemeMode();
                return true;
            } else if (item.getItemId() == 2) {
                showThemeBottomSheet();
                return true;
            }
            return false;
        });
    }

    private void showThemeBottomSheet() {
        ThemeBottomSheet sheet = new ThemeBottomSheet();
        sheet.setOnApplyListener(presetIndex -> {
            ThemeManager.savePresetIndex(this, presetIndex);
            recreate();
        });
        sheet.show(getSupportFragmentManager(), "ThemeBottomSheet");
    }

    private int getThemeIcon() {
        int nightFlags = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        boolean isDark = (nightFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES);
        return isDark ? R.drawable.ic_dark_mode : R.drawable.ic_light_mode;
    }

    private void cycleThemeMode() {
        // 判断当前实际是暗色还是亮色
        int nightFlags = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        boolean isDark = (nightFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES);

        // 两态切换：暗→亮，亮→暗
        int next = isDark ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES;
        int targetBg = isDark ? 0xFFFFF8F3 : 0xFF141210;

        // 按钮位置
        int[] loc = new int[2];
        toolbar.getLocationOnScreen(loc);
        int cx = loc[0] + toolbar.getWidth() - 48;
        int cy = loc[1] + toolbar.getHeight() / 2;

        // 用屏幕尺寸计算半径
        android.graphics.Point screenSize = new android.graphics.Point();
        getWindowManager().getDefaultDisplay().getRealSize(screenSize);
        float finalRadius = (float) Math.hypot(
                Math.max(cx, screenSize.x - cx),
                Math.max(cy, screenSize.y - cy));

        // 覆盖层
        View overlay = new View(this);
        overlay.setBackgroundColor(targetBg);
        ViewGroup rootView = findViewById(android.R.id.content);
        rootView.addView(overlay, new ViewGroup.LayoutParams(screenSize.x, screenSize.y));

        // 等布局完成后启动动画
        overlay.post(() -> {
            android.animation.Animator anim = android.view.ViewAnimationUtils.createCircularReveal(
                    overlay, cx, cy, 0f, finalRadius);
            anim.setDuration(200);
            anim.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    saveThemeMode(next);
                    AppCompatDelegate.setDefaultNightMode(next);
                }
            });
            anim.start();
        });
    }

    private int getThemeMode() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    private void saveThemeMode(int mode) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putInt(KEY_THEME_MODE, mode).apply();
    }

    private void restoreThemeMode() {
        int mode = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    private void switchFragment(Fragment target) {
        if (target == activeFragment) return;
        getSupportFragmentManager().beginTransaction()
                .hide(activeFragment)
                .show(target)
                .commit();
        activeFragment = target;
    }

    // ==================== 权限 ====================

    private void checkPermissionsAndStart() {
        List<String> needed = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.BLUETOOTH_SCAN);
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (needed.isEmpty()) {
            onPermissionsGranted();
        } else {
            String title = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ?
                    getString(R.string.permission_ble_title) : getString(R.string.permission_location_title);
            String message = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ?
                    getString(R.string.permission_ble_message) : getString(R.string.permission_location_message);

            new AlertDialog.Builder(this)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, (d, w) ->
                            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), REQ_PERMISSIONS))
                    .setCancelable(false)
                    .show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMISSIONS) {
            boolean allGranted = true;
            for (int r : grantResults) {
                if (r != PackageManager.PERMISSION_GRANTED) { allGranted = false; break; }
            }
            if (allGranted) {
                permissionsDenied = false;
                onPermissionsGranted();
            } else {
                // #38: 权限被拒绝时引导用户去设置开启权限
                permissionsDenied = true;
                showPermissionDeniedDialog();
            }
        }
    }

    private void showPermissionDeniedDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.permission_ble_title)
                .setMessage(R.string.permission_denied_message)
                .setPositiveButton(R.string.open_settings, (d, w) -> {
                    Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(android.net.Uri.fromParts("package", getPackageName(), null));
                    try {
                        startActivityForResult(intent, REQ_APP_SETTINGS);
                    } catch (Exception e) {
                        // 设置页跳转失败，忽略
                    }
                })
                .setCancelable(false)
                .show();
    }

    private boolean hasRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void onPermissionsGranted() {
        if (!bleManager.isBluetoothEnabled()) {
            viewModel.getErrorMessageMutable().setValue(getString(R.string.bluetooth_off_message));
            return;
        }
        // 已连接则不扫描
        if (bleManager.getConnectionState() == BleManager.STATE_CONNECTED) {
            return;
        }
        // 自动重连
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String lastMac = prefs.getString(KEY_LAST_MAC, null);
        if (lastMac != null) {
            bleManager.setLastConnectedAddress(lastMac);
            bleManager.startScan();
            // 扫描回调中如果发现记忆设备会自动连接
            mainHandler.postDelayed(() -> {
                if (bleManager.getConnectionState() == BleManager.STATE_SCANNING) {
                    bleManager.stopScan();
                }
            }, 10000);
        }
    }

    // ==================== BLE 回调 ====================

    @Override
    public void onScanResult(List<BleManager.ScannedDevice> devices) {
        viewModel.getScannedDevicesMutable().setValue(devices);
        // 自动连接记忆设备
        String lastMac = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_LAST_MAC, null);
        if (lastMac != null && bleManager.getConnectionState() == BleManager.STATE_SCANNING) {
            for (BleManager.ScannedDevice d : devices) {
                if (d.address.equals(lastMac)) {
                    bleManager.connect(d.address);
                    break;
                }
            }
        }
    }

    @Override
    public void onScanFinished() {
        // 静默回退
    }

    @Override
    public void onConnectionStateChanged(int state) {
        viewModel.getConnectionStateMutable().setValue(state);
        if (state == BleManager.STATE_CONNECTED) {
            // 保存 MAC
            String addr = bleManager.getLastConnectedAddress();
            if (addr != null) {
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                        .putString(KEY_LAST_MAC, addr).apply();
            }
        }
    }

    @Override
    public void onDynamicData(DynamicData data) {
        viewModel.getDynamicDataMutable().setValue(data);
        viewModel.getDataInterruptedMutable().setValue(false);
        // 追加图表数据
        float x = data.rt;
        viewModel.addChartEntry(x, data);
        // 重置超时
        resetDataTimeout();
    }

    @Override
    public void onStaticData(StaticData data) {
        viewModel.getStaticDataMutable().setValue(data);
        viewModel.getStaticInterruptedMutable().setValue(false);
        resetStaticTimeout();
    }

    @Override
    public void onError(String message) {
        viewModel.getErrorMessageMutable().setValue(message);
    }

    // ==================== 数据超时检测 ====================

    private void setupDataTimeout() {
        dataTimeoutHandler = new Handler(Looper.getMainLooper());
        dataTimeoutRunnable = () -> {
            if (bleManager.getConnectionState() == BleManager.STATE_CONNECTED) {
                viewModel.getDataInterruptedMutable().setValue(true);
            }
        };
        staticTimeoutHandler = new Handler(Looper.getMainLooper());
        staticTimeoutRunnable = () -> {
            if (bleManager.getConnectionState() == BleManager.STATE_CONNECTED) {
                viewModel.getStaticInterruptedMutable().setValue(true);
            }
        };
    }

    private void resetDataTimeout() {
        dataTimeoutHandler.removeCallbacks(dataTimeoutRunnable);
        dataTimeoutHandler.postDelayed(dataTimeoutRunnable, 3000);
    }

    private void resetStaticTimeout() {
        staticTimeoutHandler.removeCallbacks(staticTimeoutRunnable);
        staticTimeoutHandler.postDelayed(staticTimeoutRunnable, 10000);
    }

    // ==================== 生命周期 ====================

    @Override
    protected void onStart() {
        super.onStart();
        viewModel.setChartPaused(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // #37: 权限被拒绝后从设置回切时复查权限
        if (permissionsDenied && hasRequiredPermissions()) {
            permissionsDenied = false;
            onPermissionsGranted();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        viewModel.setChartPaused(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        bleManager.setCallback(null);
        // #73: 清理 mainHandler 上挂载的所有回调，避免 Activity 泄漏
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }
        dataTimeoutHandler.removeCallbacksAndMessages(null);
        staticTimeoutHandler.removeCallbacksAndMessages(null);
    }
}
