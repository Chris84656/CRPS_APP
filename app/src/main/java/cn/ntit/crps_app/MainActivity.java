package cn.ntit.crps_app;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;

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

import java.util.ArrayList;
import java.util.List;

import cn.ntit.crps_app.ble.BleManager;
import cn.ntit.crps_app.model.DynamicData;
import cn.ntit.crps_app.model.StaticData;
import cn.ntit.crps_app.ui.ControlFragment;
import cn.ntit.crps_app.ui.DetailsFragment;
import cn.ntit.crps_app.ui.InfoFragment;
import cn.ntit.crps_app.viewmodel.SharedViewModel;

public class MainActivity extends AppCompatActivity implements BleManager.BleCallback {

    private static final int REQ_PERMISSIONS = 1001;
    private static final String PREFS_NAME = "crps_prefs";
    private static final String KEY_LAST_MAC = "last_mac";
    private static final String KEY_LAST_NAME = "last_name";
    private static final String KEY_THEME_MODE = "theme_mode";

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

        checkPermissionsAndStart();
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
        toolbar.setOnMenuItemClickListener(item -> {
            cycleThemeMode();
            return true;
        });
        // 添加夜间模式按钮
        toolbar.getMenu().add(0, 1, 0, "夜间模式").setIcon(getThemeIcon()).setShowAsAction(1);
        toolbar.setOnMenuItemClickListener(item -> {
            cycleThemeMode();
            return true;
        });
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
            if (allGranted) onPermissionsGranted();
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
    protected void onStop() {
        super.onStop();
        viewModel.setChartPaused(true);
    }

    @Override
    protected void onStart() {
        super.onStart();
        viewModel.setChartPaused(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        bleManager.setCallback(null);
        dataTimeoutHandler.removeCallbacksAndMessages(null);
        staticTimeoutHandler.removeCallbacksAndMessages(null);
    }
}
