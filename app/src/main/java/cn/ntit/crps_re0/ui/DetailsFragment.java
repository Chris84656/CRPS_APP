package cn.ntit.crps_re0.ui;

import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.github.mikephil.charting.data.Entry;
import com.google.android.material.button.MaterialButton;

import java.util.List;

import cn.ntit.crps_re0.R;
import cn.ntit.crps_re0.util.NumberFormatter;
import cn.ntit.crps_re0.viewmodel.SharedViewModel;

public class DetailsFragment extends Fragment {

    private SharedViewModel viewModel;

    private TextView tvRuntime;
    private View viewStatusDot;
    private StreamLineChart chartVI, chartPE, chartTemp;
    private TextView tvChart1Empty, tvChart2Empty, tvChart3Empty;
    // #20: 三图各加 overlay
    private FrameLayout overlayChart1, overlayChart2, overlayChart3;
    private View overlayDisconnected;
    private MaterialButton btnPauseResume;

    private boolean isLiveMode = true;
    private boolean viewInitialized = false;
    private float currentViewX = 0f;
    private static final float VISIBLE_RANGE = 60f;

    private Handler scrollHandler;
    private Runnable scrollRunnable;
    private long scrollStartTime = 0;
    private float scrollStartX = 0f;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_details, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);

        bindViews(view);
        setupCharts();

        // 主题切换重建后：有数据则立刻填充，避免"暂无数据"闪现
        if (viewModel.getVoutEntries().size() > 1) {
            tvChart1Empty.setVisibility(View.GONE);
            tvChart2Empty.setVisibility(View.GONE);
            tvChart3Empty.setVisibility(View.GONE);
            viewInitialized = true;
            currentViewX = getLatestViewX();
            pushDataToCharts();
            for (StreamLineChart c : new StreamLineChart[]{chartVI, chartPE, chartTemp}) {
                c.setViewportStart(currentViewX);
            }
        }

        setupButton();
        setupSmoothScroll();
        observeData();
    }

    private void bindViews(View view) {
        tvRuntime = view.findViewById(R.id.tv_runtime);
        viewStatusDot = view.findViewById(R.id.view_status_dot);
        chartVI = view.findViewById(R.id.chart_vi);
        chartPE = view.findViewById(R.id.chart_pe);
        chartTemp = view.findViewById(R.id.chart_temp);
        tvChart1Empty = view.findViewById(R.id.tv_chart1_empty);
        tvChart2Empty = view.findViewById(R.id.tv_chart2_empty);
        tvChart3Empty = view.findViewById(R.id.tv_chart3_empty);
        overlayChart1 = view.findViewById(R.id.overlay_chart1);
        // #20: 绑定 chart2/chart3 的 overlay
        overlayChart2 = view.findViewById(R.id.overlay_chart2);
        overlayChart3 = view.findViewById(R.id.overlay_chart3);
        overlayDisconnected = view.findViewById(R.id.overlay_disconnected);
        btnPauseResume = view.findViewById(R.id.btn_pause_resume);
    }

    private void setupButton() {
        btnPauseResume.setOnClickListener(v -> {
            isLiveMode = !isLiveMode;
            if (isLiveMode) {
                btnPauseResume.setText("暂停刷新");
                scrollStartTime = 0;
                currentViewX = getLatestViewX();
                for (StreamLineChart c : new StreamLineChart[]{chartVI, chartPE, chartTemp}) {
                    c.setPaused(false);
                    c.setViewportStart(currentViewX);
                }
            } else {
                btnPauseResume.setText("实时刷新");
                for (StreamLineChart c : new StreamLineChart[]{chartVI, chartPE, chartTemp}) {
                    c.setPaused(true);
                }
            }
        });
    }

    // ==================== 图表初始化 ====================

    private void setupCharts() {
        // 图表1：电压(左轴) + 电流(右轴)，独立缩放，不允许负值
        chartVI.setViewportRange(VISIBLE_RANGE);
        chartVI.setLeftAllowNegative(false);
        chartVI.setRightAxisEnabled(true);
        chartVI.setSharedAxis(false);
        chartVI.setMarkerLabels(new String[]{"输出电压", "输出电流"});

        // 图表2：功率(左轴) + 效率(右轴固定 0-100)
        chartPE.setViewportRange(VISIBLE_RANGE);
        chartPE.setLeftAllowNegative(false);
        chartPE.setRightAxisEnabled(true);
        chartPE.setRightAxisFixed(0f, 100f);
        chartPE.setSharedAxis(false);
        chartPE.setMarkerLabels(new String[]{"输出功率", "转换效率"});

        // 图表3：T1+T2 共用左轴，允许负值
        chartTemp.setViewportRange(VISIBLE_RANGE);
        chartTemp.setLeftAllowNegative(true);
        chartTemp.setRightAxisEnabled(false);
        chartTemp.setSharedAxis(true);
        chartTemp.setMarkerLabels(new String[]{"环境温度", "热点温度"});
    }

    // ==================== 匀速滚动 + 圆点缓动 ====================

    private void setupSmoothScroll() {
        scrollHandler = new Handler(Looper.getMainLooper());
        scrollRunnable = new Runnable() {
            @Override
            public void run() {
                if (isLiveMode && viewModel.getVoutEntries().size() > 1) {
                    long now = SystemClock.elapsedRealtime();
                    if (scrollStartTime == 0) {
                        scrollStartTime = now;
                        List<Entry> entries = viewModel.getVoutEntries();
                        scrollStartX = entries.get(entries.size() - 1).getX();
                        currentViewX = scrollStartX - VISIBLE_RANGE * (2f / 3f);
                        for (StreamLineChart c : new StreamLineChart[]{chartVI, chartPE, chartTemp}) {
                            c.setViewportStart(currentViewX);
                        }
                    }

                    float elapsed = (now - scrollStartTime) / 1000f;
                    float newViewX = scrollStartX + elapsed - VISIBLE_RANGE * (2f / 3f);
                    float dx = newViewX - currentViewX;
                    currentViewX = newViewX;

                    // offsetViewport 仅平移 X 起点，不重建数据 → 真正丝滑
                    // advanceHead 在 view 内部做圆点 Y 缓动
                    for (StreamLineChart c : new StreamLineChart[]{chartVI, chartPE, chartTemp}) {
                        c.offsetViewport(dx);
                        c.advanceHead();
                    }
                }
                scrollHandler.postDelayed(this, 16);
            }
        };
        // 不在此处 post，由 onResume / onHiddenChanged 统一调度
    }

    private float getLatestViewX() {
        List<Entry> entries = viewModel.getVoutEntries();
        if (entries.isEmpty()) return 0f;
        return entries.get(entries.size() - 1).getX() - VISIBLE_RANGE * (2f / 3f);
    }

    // ==================== 数据观察 ====================

    private void observeData() {
        viewModel.getDynamicData().observe(getViewLifecycleOwner(), data -> {
            if (data == null) return;
            tvRuntime.setText(NumberFormatter.runtime(data.rt));
            // 设置时间锚点，marker 用此把 ESP32 rt 转为真实时钟时间
            long now = System.currentTimeMillis();
            for (StreamLineChart c : new StreamLineChart[]{chartVI, chartPE, chartTemp}) {
                c.setTimeAnchor(now, data.rt);
            }
            if (isLiveMode) pushDataToCharts();
            viewStatusDot.setBackgroundResource(data.isPowerOn() ? R.drawable.bg_dot : R.drawable.bg_dot_off);
        });

        viewModel.getDataInterrupted().observe(getViewLifecycleOwner(), interrupted -> {
            if (interrupted == null) return;
            // #20: 三图 overlay 同步控制
            int visibility = interrupted ? View.VISIBLE : View.GONE;
            overlayChart1.setVisibility(visibility);
            if (overlayChart2 != null) overlayChart2.setVisibility(visibility);
            if (overlayChart3 != null) overlayChart3.setVisibility(visibility);
        });

        viewModel.getConnectionState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) return;
            if (state == cn.ntit.crps_re0.ble.BleManager.STATE_CONNECTED) {
                overlayDisconnected.setVisibility(View.GONE);
                btnPauseResume.setEnabled(true);
                setBlur(false);
                if (!isLiveMode) {
                    isLiveMode = true;
                    btnPauseResume.setText("暂停刷新");
                    scrollStartTime = 0;
                }
            } else {
                overlayDisconnected.setVisibility(View.VISIBLE);
                btnPauseResume.setEnabled(false);
                setBlur(true);
                isLiveMode = false;
                // 断连时清除 marker 并复位 paused 状态，避免 marker 残留
                for (StreamLineChart c : new StreamLineChart[]{chartVI, chartPE, chartTemp}) {
                    c.setPaused(false);
                }
            }
        });

        viewModel.getChartColors().observe(getViewLifecycleOwner(), colors -> {
            if (colors == null || colors.length < 6) return;
            chartVI.setLineColor(0, colors[0]);
            chartVI.setLineColor(1, colors[1]);
            chartPE.setLineColor(0, colors[2]);
            chartPE.setLineColor(1, colors[3]);
            chartTemp.setLineColor(0, colors[4]);
            chartTemp.setLineColor(1, colors[5]);
        });
    }

    private void setBlur(boolean blur) {
        View scrollView = (View) overlayDisconnected.getParent();
        scrollView = ((ViewGroup) scrollView).getChildAt(0); // ScrollView
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            scrollView.setRenderEffect(blur
                    ? RenderEffect.createBlurEffect(20f, 20f, Shader.TileMode.CLAMP)
                    : null);
        } else {
            scrollView.setAlpha(blur ? 0.3f : 1f);
        }
    }

    // ==================== 数据推送到 chart ====================

    private void pushDataToCharts() {
        // 防御：暂停状态下不更新数据，保证曲线完全冻结
        if (!isLiveMode) return;
        boolean hasData = viewModel.getVoutEntries().size() > 1;
        tvChart1Empty.setVisibility(hasData ? View.GONE : View.VISIBLE);
        tvChart2Empty.setVisibility(hasData ? View.GONE : View.VISIBLE);
        tvChart3Empty.setVisibility(hasData ? View.GONE : View.VISIBLE);
        if (!hasData) return;

        if (!viewInitialized) {
            viewInitialized = true;
            currentViewX = getLatestViewX();
        }

        // 图表1：电压(左轴) + 电流(右轴)
        chartVI.setLine(0, viewModel.getVoutEntries(), R.color.chart_voltage,
                StreamLineChart.YAxisDependency.LEFT);
        chartVI.setLine(1, viewModel.getIoutEntries(), R.color.chart_current,
                StreamLineChart.YAxisDependency.RIGHT);

        // 图表2：功率(左轴) + 效率(右轴)
        chartPE.setLine(0, viewModel.getPoutEntries(), R.color.chart_power,
                StreamLineChart.YAxisDependency.LEFT);
        chartPE.setLine(1, viewModel.getEffEntries(), R.color.chart_efficiency,
                StreamLineChart.YAxisDependency.RIGHT);

        // 图表3：T1+T2 共用左轴
        chartTemp.setLine(0, viewModel.getT1Entries(), R.color.chart_temp1,
                StreamLineChart.YAxisDependency.LEFT);
        chartTemp.setLine(1, viewModel.getT2Entries(), R.color.chart_temp2,
                StreamLineChart.YAxisDependency.LEFT);
    }

    // ==================== 生命周期 ====================

    // #19: Fragment 隐藏时停止 60fps 空转，显示时恢复
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) {
            if (scrollHandler != null) scrollHandler.removeCallbacks(scrollRunnable);
        } else if (isLiveMode && scrollHandler != null) {
            scrollStartTime = 0; // 重置滚动锚点
            scrollHandler.post(scrollRunnable);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // #19: 应用进入后台时停止 60fps 空转
        if (scrollHandler != null) scrollHandler.removeCallbacks(scrollRunnable);
    }

    @Override
    public void onResume() {
        super.onResume();
        // #19: 应用回到前台且可见时恢复滚动
        if (isLiveMode && !isHidden() && scrollHandler != null) {
            scrollStartTime = 0;
            scrollHandler.post(scrollRunnable);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (scrollHandler != null) {
            scrollHandler.removeCallbacksAndMessages(null);
        }
    }
}
