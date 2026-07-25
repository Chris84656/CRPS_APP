package cn.ntit.crps_app.ui;

import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import cn.ntit.crps_app.R;
import cn.ntit.crps_app.util.LTTB;
import cn.ntit.crps_app.util.NumberFormatter;
import cn.ntit.crps_app.viewmodel.SharedViewModel;

public class DetailsFragment extends Fragment {

    private SharedViewModel viewModel;

    private TextView tvRuntime;
    private View viewStatusDot;
    private LineChart chartVI, chartPE, chartTemp;
    private TextView tvChart1Empty, tvChart2Empty, tvChart3Empty;
    private FrameLayout overlayChart1;
    private View overlayDisconnected;
    private MaterialButton btnPauseResume;
    private ChartMarkerView markerVI, markerPE, markerTemp;

    private boolean isLiveMode = true;
    private boolean viewInitialized = false;
    private boolean[] chartInitialized = {false, false, false};
    private float currentViewX = 0f;
    private static final float VISIBLE_RANGE = 60f;
    private static final int DOWNSAMPLE_THRESHOLD = 500;
    private static final int DOWNSAMPLE_TARGET = 400;

    private Handler scrollHandler;
    private Runnable scrollRunnable;
    private long scrollStartTime = 0;
    private float scrollStartX = 0f;

    // 圆点 Y 值缓动
    private float[] headYCurrent = new float[6];
    private float[] headYTarget = new float[6];
    private static final float HEAD_LERP = 0.08f;

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
            updateCharts();
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
        overlayDisconnected = view.findViewById(R.id.overlay_disconnected);
        btnPauseResume = view.findViewById(R.id.btn_pause_resume);
    }

    private void setupButton() {
        btnPauseResume.setOnClickListener(v -> {
            isLiveMode = !isLiveMode;
            if (isLiveMode) {
                btnPauseResume.setText("暂停刷新");
                scrollStartTime = 0;
                setChartsDragEnabled(false);
                chartVI.highlightValues(null);
                chartPE.highlightValues(null);
                chartTemp.highlightValues(null);
                // 立刻跳到实时位置，不等下一帧
                currentViewX = getLatestViewX();
                for (LineChart c : new LineChart[]{chartVI, chartPE, chartTemp}) {
                    c.centerViewTo(currentViewX + VISIBLE_RANGE / 2f, 0f, YAxis.AxisDependency.LEFT);
                    c.invalidate();
                }
            } else {
                btnPauseResume.setText("实时刷新");
                setChartsDragEnabled(true);
            }
        });
    }

    private void setChartsDragEnabled(boolean enabled) {
        for (LineChart chart : new LineChart[]{chartVI, chartPE, chartTemp}) {
            chart.setDragEnabled(enabled);
            chart.setHighlightPerTapEnabled(enabled);
        }
    }

    // ==================== 图表初始化 ====================

    private void setupCharts() {
        setupSingleChart(chartVI, new String[]{"输出电压", "输出电流"});
        setupSingleChart(chartPE, new String[]{"输出功率", "转换效率"});
        setupSingleChart(chartTemp, new String[]{"环境温度", "热点温度"});
        setChartsDragEnabled(false);
    }

    private void setupSingleChart(LineChart chart, String[] markerLabels) {
        chart.getDescription().setEnabled(false);
        chart.setDrawGridBackground(false);
        chart.setDragEnabled(false);
        chart.setScaleXEnabled(false);
        chart.setScaleYEnabled(false);
        chart.setPinchZoom(false);
        chart.setVisibleXRangeMaximum(VISIBLE_RANGE);
        chart.setVisibleXRangeMinimum(VISIBLE_RANGE);
        chart.setNoDataText("");
        chart.getLegend().setEnabled(false);
        chart.setExtraOffsets(2f, 4f, 2f, 4f);
        chart.setHighlightPerDragEnabled(false);
        chart.setHighlightPerTapEnabled(false);

        int axisColor = MaterialColors.getColor(chart, com.google.android.material.R.attr.colorOnSurfaceVariant);
        int gridColor = MaterialColors.getColor(chart, com.google.android.material.R.attr.colorOutlineVariant);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setLabelCount(5, false);
        xAxis.setTextColor(axisColor);
        xAxis.setGranularity(10f);
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return "•";
            }
        });

        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(gridColor);
        leftAxis.setTextColor(axisColor);

        if (chart != chartTemp) {
            YAxis rightAxis = chart.getAxisRight();
            rightAxis.setEnabled(true);
            rightAxis.setDrawGridLines(false);
            rightAxis.setTextColor(axisColor);
        } else {
            chart.getAxisRight().setEnabled(false);
        }

        ChartMarkerView marker = new ChartMarkerView(requireContext(), markerLabels);
        chart.setMarker(marker);
        if (chart == chartVI) markerVI = marker;
        else if (chart == chartPE) markerPE = marker;
        else markerTemp = marker;

        chart.setOnChartGestureListener(new com.github.mikephil.charting.listener.OnChartGestureListener() {
            @Override public void onChartGestureStart(MotionEvent me, com.github.mikephil.charting.listener.ChartTouchListener.ChartGesture g) {}
            @Override public void onChartGestureEnd(MotionEvent me, com.github.mikephil.charting.listener.ChartTouchListener.ChartGesture g) {}
            @Override public void onChartLongPressed(MotionEvent me) {}
            @Override public void onChartDoubleTapped(MotionEvent me) {}
            @Override public void onChartSingleTapped(MotionEvent me) {}
            @Override public void onChartFling(MotionEvent me1, MotionEvent me2, float vx, float vy) {}
            @Override public void onChartScale(MotionEvent me, float sx, float sy) {}
            @Override public void onChartTranslate(MotionEvent me, float dX, float dY) {}
        });
    }

    // ==================== 匀速滚动 + 圆点缓动 ====================

    private void setupSmoothScroll() {
        scrollHandler = new Handler(Looper.getMainLooper());
        scrollRunnable = new Runnable() {
            @Override
            public void run() {
                if (isLiveMode && viewModel.getVoutEntries().size() > 1) {
                    if (scrollStartTime == 0) {
                        scrollStartTime = SystemClock.elapsedRealtime();
                        List<Entry> entries = viewModel.getVoutEntries();
                        scrollStartX = entries.get(entries.size() - 1).getX();
                    }
                    float elapsed = (SystemClock.elapsedRealtime() - scrollStartTime) / 1000f;
                    currentViewX = scrollStartX + elapsed - VISIBLE_RANGE * (2f / 3f);

                    // 圆点缓动
                    float headX = scrollStartX + elapsed; // 圆点 X 基于时间插值，每帧更新
                    for (int i = 0; i < 6; i++) {
                        headYCurrent[i] += (headYTarget[i] - headYCurrent[i]) * HEAD_LERP;
                    }
                    updateHeadDots(headX);

                    // X 轴 min/max 每帧更新 + centerViewTo 移动视口
                    float axisMax = currentViewX + VISIBLE_RANGE + 10f;
                    for (LineChart chart : new LineChart[]{chartVI, chartPE, chartTemp}) {
                        chart.getXAxis().setAxisMinimum(currentViewX);
                        chart.getXAxis().setAxisMaximum(axisMax);
                        chart.centerViewTo(currentViewX + VISIBLE_RANGE / 2f, 0f, YAxis.AxisDependency.LEFT);
                        chart.invalidate();
                    }
                }
                scrollHandler.postDelayed(this, 16);
            }
        };
        scrollHandler.post(scrollRunnable);
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
            long now = System.currentTimeMillis();
            if (markerVI != null) markerVI.setTimeAnchor(now, data.rt);
            if (markerPE != null) markerPE.setTimeAnchor(now, data.rt);
            if (markerTemp != null) markerTemp.setTimeAnchor(now, data.rt);
            if (isLiveMode) updateCharts();
        });

        viewModel.getDataInterrupted().observe(getViewLifecycleOwner(), interrupted -> {
            if (interrupted == null) return;
            overlayChart1.setVisibility(interrupted ? View.VISIBLE : View.GONE);
            viewStatusDot.setBackgroundResource(interrupted ? R.drawable.bg_dot_warning : R.drawable.bg_dot);
        });

        viewModel.getConnectionState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) return;
            if (state == cn.ntit.crps_app.ble.BleManager.STATE_CONNECTED) {
                overlayDisconnected.setVisibility(View.GONE);
                btnPauseResume.setEnabled(true);
                setBlur(false);
                if (!isLiveMode) {
                    isLiveMode = true;
                    btnPauseResume.setText("暂停刷新");
                    scrollStartTime = 0;
                    setChartsDragEnabled(false);
                }
            } else {
                overlayDisconnected.setVisibility(View.VISIBLE);
                btnPauseResume.setEnabled(false);
                setBlur(true);
                isLiveMode = false;
            }
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

    // ==================== 图表更新 ====================

    private void updateCharts() {
        boolean hasData = viewModel.getVoutEntries().size() > 1;
        tvChart1Empty.setVisibility(hasData ? View.GONE : View.VISIBLE);
        tvChart2Empty.setVisibility(hasData ? View.GONE : View.VISIBLE);
        tvChart3Empty.setVisibility(hasData ? View.GONE : View.VISIBLE);
        if (!hasData) return;

        if (!viewInitialized) {
            viewInitialized = true;
            currentViewX = getLatestViewX();
        }

        // 图表1：左轴=电压(独立)，右轴=电流(独立)
        updateSingleChart(chartVI, 0,
                viewModel.getVoutEntries(), viewModel.getIoutEntries(),
                R.color.chart_voltage, R.color.chart_current,
                YAxis.AxisDependency.LEFT, YAxis.AxisDependency.RIGHT,
                false); // 左右轴独立

        // 图表2：左轴=功率(独立)，右轴=效率(固定0-100)
        updateSingleChart(chartPE, 1,
                viewModel.getPoutEntries(), viewModel.getEffEntries(),
                R.color.chart_power, R.color.chart_efficiency,
                YAxis.AxisDependency.LEFT, YAxis.AxisDependency.RIGHT,
                false);

        // 图表3：左轴=温度(T1+T2合并)
        updateSingleChart(chartTemp, 2,
                viewModel.getT1Entries(), viewModel.getT2Entries(),
                R.color.chart_temp1, R.color.chart_temp2,
                YAxis.AxisDependency.LEFT, YAxis.AxisDependency.LEFT,
                true); // 双 dataset 共用同一轴
    }

    private void updateSingleChart(LineChart chart, int chartIdx,
                                   List<Entry> entries1, List<Entry> entries2,
                                   int color1, int color2,
                                   YAxis.AxisDependency axis1, YAxis.AxisDependency axis2,
                                   boolean sharedAxis) {
        List<Entry> data1 = getRenderData(entries1);
        List<Entry> data2 = getRenderData(entries2);

        LineDataSet ds1 = createDataSet(data1, color1, axis1);
        LineDataSet ds2 = createDataSet(data2, color2, axis2);
        LineDataSet head1 = createHeadDot(data1, color1, axis1);
        LineDataSet head2 = createHeadDot(data2, color2, axis2);

        chart.setData(new LineData(ds1, ds2, head1, head2));

        // 曲线末端 X 同步为时间插值，与 updateHeadDots 保持一致，消除 250ms 震动
        if (scrollStartTime > 0) {
            float elapsed = (SystemClock.elapsedRealtime() - scrollStartTime) / 1000f;
            float headX = scrollStartX + elapsed;
            if (!ds1.getValues().isEmpty()) ds1.getValues().get(ds1.getValues().size() - 1).setX(headX);
            if (!ds2.getValues().isEmpty()) ds2.getValues().get(ds2.getValues().size() - 1).setX(headX);
        }

        headYTarget[chartIdx * 2] = data1.isEmpty() ? 0 : data1.get(data1.size() - 1).getY();
        headYTarget[chartIdx * 2 + 1] = data2.isEmpty() ? 0 : data2.get(data2.size() - 1).getY();
        if (headYCurrent[chartIdx * 2] == 0 && headYCurrent[chartIdx * 2 + 1] == 0) {
            headYCurrent[chartIdx * 2] = headYTarget[chartIdx * 2];
            headYCurrent[chartIdx * 2 + 1] = headYTarget[chartIdx * 2 + 1];
        }

        // X 轴范围：覆盖视口 + 余量，在 notifyDataSetChanged 之前设
        chart.getXAxis().setAxisMinimum(currentViewX);
        chart.getXAxis().setAxisMaximum(currentViewX + VISIBLE_RANGE + 10f);

        // notifyDataSetChanged 更新 Transformer 矩阵（calcMinMax + calculateOffsets）
        chart.notifyDataSetChanged();

        // Y 轴自动缩放
        if (sharedAxis) {
            applyAutoScaleCombined(chart.getAxisLeft(), entries1, entries2);
        } else {
            applyAutoScale(chart.getAxisLeft(), entries1);
            if (chart.getAxisRight().isEnabled()) {
                if (chartIdx == 1) {
                    chart.getAxisRight().setAxisMinimum(0f);
                    chart.getAxisRight().setAxisMaximum(100f);
                } else {
                    applyAutoScale(chart.getAxisRight(), entries2);
                }
            }
        }
        chart.invalidate();
    }

    private void updateHeadDots(float headX) {
        updateHeadDot(chartVI, 0, 1, headX);
        updateHeadDot(chartPE, 2, 3, headX);
        updateHeadDot(chartTemp, 4, 5, headX);
    }

    private void updateHeadDot(LineChart chart, int idx1, int idx2, float headX) {
        LineData data = chart.getData();
        if (data == null || data.getDataSetCount() < 4) return;
        LineDataSet line1 = (LineDataSet) data.getDataSetByIndex(0);
        LineDataSet line2 = (LineDataSet) data.getDataSetByIndex(1);
        if (line1 != null && !line1.getValues().isEmpty()) {
            line1.getValues().set(line1.getValues().size() - 1, new Entry(headX, headYCurrent[idx1]));
        }
        if (line2 != null && !line2.getValues().isEmpty()) {
            line2.getValues().set(line2.getValues().size() - 1, new Entry(headX, headYCurrent[idx2]));
        }
        LineDataSet head1 = (LineDataSet) data.getDataSetByIndex(2);
        LineDataSet head2 = (LineDataSet) data.getDataSetByIndex(3);
        if (head1 != null && !head1.getValues().isEmpty()) {
            head1.getValues().set(0, new Entry(headX, headYCurrent[idx1]));
        }
        if (head2 != null && !head2.getValues().isEmpty()) {
            head2.getValues().set(0, new Entry(headX, headYCurrent[idx2]));
        }
    }

    private List<Entry> getRenderData(List<Entry> source) {
        if (source.size() > DOWNSAMPLE_THRESHOLD) {
            return LTTB.downsample(source, DOWNSAMPLE_TARGET);
        }
        return new ArrayList<>(source);
    }

    private LineDataSet createDataSet(List<Entry> entries, int colorRes, YAxis.AxisDependency axis) {
        LineDataSet ds = new LineDataSet(entries, "line");
        ds.setColor(ContextCompat.getColor(requireContext(), colorRes));
        ds.setLineWidth(2f);
        ds.setDrawCircles(false);
        ds.setDrawValues(false);
        ds.setAxisDependency(axis);
        ds.setMode(LineDataSet.Mode.HORIZONTAL_BEZIER);
        ds.setCubicIntensity(0.15f);
        ds.setHighlightEnabled(true);
        return ds;
    }

    private LineDataSet createHeadDot(List<Entry> entries, int colorRes, YAxis.AxisDependency axis) {
        List<Entry> lastPoint = new ArrayList<>();
        if (!entries.isEmpty()) {
            lastPoint.add(entries.get(entries.size() - 1));
        }
        LineDataSet ds = new LineDataSet(lastPoint, "head");
        int color = ContextCompat.getColor(requireContext(), colorRes);
        ds.setColor(color);
        ds.setCircleColor(color);
        ds.setCircleRadius(4f);
        ds.setCircleHoleRadius(2f);
        ds.setCircleHoleColor(color);
        ds.setDrawCircles(true);
        ds.setDrawValues(false);
        ds.setLineWidth(0f);
        ds.setAxisDependency(axis);
        ds.setHighlightEnabled(false);
        return ds;
    }

    // ==================== Y 轴自动缩放 ====================

    /**
     * 单轴自动缩放：取最近 N 个点的 min/max，加 padding，直接设值
     */
    private void applyAutoScale(YAxis axis, List<Entry> entries) {
        if (entries.size() < 2) return;
        float[] range = getRecentMinMax(entries);
        setAxisBounds(axis, range[0], range[1]);
    }

    /**
     * 双 dataset 合并缩放（温度图 T1+T2 共用左轴）
     */
    private void applyAutoScaleCombined(YAxis axis, List<Entry> entries1, List<Entry> entries2) {
        if (entries1.size() < 2 && entries2.size() < 2) return;
        float[] r1 = getRecentMinMax(entries1);
        float[] r2 = getRecentMinMax(entries2);
        float min = Math.min(r1[0], r2[0]);
        float max = Math.max(r1[1], r2[1]);
        setAxisBounds(axis, min, max);
    }

    private float[] getRecentMinMax(List<Entry> entries) {
        int lookback = Math.min(entries.size(), 600);
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (int i = entries.size() - lookback; i < entries.size(); i++) {
            float y = entries.get(i).getY();
            if (y < min) min = y;
            if (y > max) max = y;
        }
        if (min == Float.MAX_VALUE) return new float[]{0, 1};
        return new float[]{min, max};
    }

    private void setAxisBounds(YAxis axis, float dataMin, float dataMax) {
        float range = dataMax - dataMin;
        if (range < 1f) range = 1f; // 最小量程 1
        float padding = range * 0.2f; // 20% padding
        float axisMin = dataMin - padding;
        float axisMax = dataMax + padding;
        if (axisMin < 0) axisMin = 0;
        axis.setAxisMinimum(axisMin);
        axis.setAxisMaximum(axisMax);
    }

    // ==================== 生命周期 ====================

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (scrollHandler != null) {
            scrollHandler.removeCallbacksAndMessages(null);
        }
    }
}
