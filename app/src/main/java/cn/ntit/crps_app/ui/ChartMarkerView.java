package cn.ntit.crps_app.ui;

import android.content.Context;
import android.widget.TextView;

import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.utils.MPPointF;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import cn.ntit.crps_app.R;

public class ChartMarkerView extends MarkerView {

    private TextView tvTime;
    private TextView tvValues;
    private String[] labels;

    // 时间锚点：anchorRealTimeMs 时刻对应 ESP32 运行时间 anchorRt 秒
    private long anchorRealTimeMs = 0;
    private float anchorRt = 0;

    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

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

        super.refreshContent(e, highlight);
    }

    @Override
    public MPPointF getOffset() {
        return new MPPointF(-(getWidth() / 2f), -getHeight() - 10f);
    }
}
