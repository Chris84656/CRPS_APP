package cn.ntit.crps_re0.ui;

import android.content.Context;
import android.widget.TextView;

import com.github.mikephil.charting.charts.Chart;
import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.utils.MPPointF;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import cn.ntit.crps_re0.R;

public class ChartMarkerView extends MarkerView {

    private TextView tvTime;
    private TextView tvValues;
    private String[] labels;

    // 时间锚点：anchorRealTimeMs 时刻对应 ESP32 运行时间 anchorRt 秒
    private long anchorRealTimeMs = 0;
    private float anchorRt = 0;

    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    // #18: 动态偏移调整量，避免 marker 靠近顶部或右侧时被裁切
    private int offsetForDrawingX = 0;
    private int offsetForDrawingY = 0;

    public ChartMarkerView(Context context, String[] labels) {
        super(context, R.layout.view_marker);
        this.labels = labels;
        tvTime = findViewById(R.id.tv_marker_time);
        tvValues = findViewById(R.id.tv_marker_values);
    }

    public void setTimeAnchor(long realTimeMs, float rt) {
        this.anchorRealTimeMs = realTimeMs;
        this.anchorRt = rt;
    }

    @Override
    public void refreshContent(Entry e, Highlight highlight) {
        // 将 ESP32 运行时间转换为真实时钟时间
        if (anchorRealTimeMs > 0) {
            long entryRealTime = anchorRealTimeMs - (long) ((anchorRt - e.getX()) * 1000);
            tvTime.setText(timeFormat.format(new Date(entryRealTime)));
        } else {
            tvTime.setText("");
        }

        int dsIndex = highlight.getDataSetIndex();
        String label = (dsIndex < labels.length) ? labels[dsIndex] : "";
        tvValues.setText(String.format(Locale.US, "%s: %.2f", label, e.getY()));

        // #18: 根据 highlight 像素坐标动态计算偏移调整量，避免靠近顶部或右侧时被裁切
        offsetForDrawingX = 0;
        offsetForDrawingY = 0;
        Chart chart = getChartView();
        if (chart != null) {
            int markerWidth = getWidth();
            int markerHeight = getHeight();
            int chartWidth = chart.getWidth();
            float drawX = highlight.getDrawX();
            float drawY = highlight.getDrawY();

            if (drawY < markerHeight + 10) {
                // 靠近顶部：改为显示在下方，目标 offsetY = 10
                offsetForDrawingY = markerHeight + 20;
            }
            if (drawX > chartWidth - markerWidth) {
                // 靠近右侧：左偏，目标 offsetX = -markerWidth - 10
                offsetForDrawingX = -markerWidth / 2 - 10;
            }
        }

        super.refreshContent(e, highlight);
    }

    @Override
    public MPPointF getOffset() {
        int w = getWidth();
        int h = getHeight();
        // 默认上方居中
        float offsetX = -w / 2f;
        float offsetY = -h - 10f;
        // #18: 根据刷新时计算的偏移调整
        offsetX += offsetForDrawingX;
        offsetY += offsetForDrawingY;
        return new MPPointF(offsetX, offsetY);
    }
}
