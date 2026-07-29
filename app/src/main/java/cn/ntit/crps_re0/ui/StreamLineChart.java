package cn.ntit.crps_re0.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.data.Entry;
import com.google.android.material.color.MaterialColors;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 流式实时图表 View：60fps 滚动 + 4Hz 数据追加，专为 BLE 实时监控场景设计。
 *
 * 关键设计：
 * - offsetViewport(dt) 仅平移 X 起点 + invalidate，不重建任何数据
 * - setLine(idx, entries, ...) 用 ArrayList 快照避免 CME，但只更新本 view 状态
 * - onDraw 直接遍历数据点画 Path，无视口外的点
 * - Y 轴 bounds 在 setLine 时计算并缓存，避免每帧重算
 */
public class StreamLineChart extends View {

    public enum YAxisDependency { LEFT, RIGHT }

    // 数据
    private final List<Entry>[] lines = new List[2];
    private final int[] lineColors = new int[2];
    private final YAxisDependency[] lineDeps = new YAxisDependency[2];
    private int lineCount = 0;

    // 视口
    private float viewportStart = 0;
    private float viewportRange = 60f;

    // Y 轴配置
    private boolean leftAllowNegative = false;
    private boolean rightAxisEnabled = true;
    private boolean rightAxisFixed = false;
    private boolean sharedAxis = false;
    private float leftAxisMin = 0, leftAxisMax = 1;
    private float rightAxisMin = 0, rightAxisMax = 100;
    private static final int AXIS_LABEL_COUNT = 5;

    // 圆点缓动
    private final float[] headYCurrent = new float[2];
    private final float[] headYTarget = new float[2];
    private final boolean[] headInited = new boolean[2];
    private static final float HEAD_LERP = 0.08f;

    // 画笔
    private int axisColor, gridColor;
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint headPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerTextBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Marker（暂停态点击触发）
    private float markerX = -1;
    private String[] markerLabels = new String[]{"", ""};
    private long downTime = 0;
    private float downX, downY;
    private boolean longPressed = false;
    private boolean isPaused = false;
    private boolean markerDragging = false;
    // 时间锚点：anchorRealTimeMs 时刻对应 ESP32 运行时间 anchorRt 秒
    private long anchorRealTimeMs = 0;
    private float anchorRt = 0;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    // 缓存 onDraw 计算的网格范围，updateMarker 用
    private float cachedGridLeft = 0;
    private float cachedPlotW = 0;

    public StreamLineChart(Context context) {
        super(context);
        init();
    }

    public StreamLineChart(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public StreamLineChart(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        axisColor = MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY);
        gridColor = MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorOutlineVariant, Color.LTGRAY);

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(4f);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setStrokeCap(Paint.Cap.ROUND);

        headPaint.setStyle(Paint.Style.FILL);

        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1f);
        gridPaint.setColor(gridColor);

        axisTextPaint.setColor(axisColor);
        axisTextPaint.setTextSize(30f);

        markerPaint.setStyle(Paint.Style.STROKE);
        markerPaint.setStrokeWidth(1.5f);
        markerPaint.setColor(axisColor);
        markerTextPaint.setTextSize(28f);
        markerTextBgPaint.setColor(0xCC000000);
    }

    // ==================== 公共 API ====================

    public void setViewportRange(float range) {
        this.viewportRange = range;
        invalidate();
    }

    public void setViewportStart(float start) {
        this.viewportStart = start;
        invalidate();
    }

    public void offsetViewport(float dxSeconds) {
        this.viewportStart += dxSeconds;
        invalidate();
    }

    public float getViewportStart() { return viewportStart; }
    public float getViewportEnd() { return viewportStart + viewportRange; }

    public void setLeftAllowNegative(boolean allow) { this.leftAllowNegative = allow; }
    public void setRightAxisEnabled(boolean enabled) { this.rightAxisEnabled = enabled; }
    public void setRightAxisFixed(float min, float max) {
        this.rightAxisFixed = true;
        this.rightAxisMin = min;
        this.rightAxisMax = max;
    }
    public void setSharedAxis(boolean shared) { this.sharedAxis = shared; }
    public void setMarkerLabels(String[] labels) { this.markerLabels = labels; }

    /**
     * 设置时间锚点，用于 marker 显示真实时钟时间。
     * anchorRealTimeMs 时刻对应 ESP32 运行时间 anchorRt 秒。
     */
    public void setTimeAnchor(long anchorRealTimeMs, float anchorRt) {
        this.anchorRealTimeMs = anchorRealTimeMs;
        this.anchorRt = anchorRt;
    }

    /**
     * 设置暂停状态。暂停态下单击/拖动显示 marker；实时态不响应 marker。
     */
    public void setPaused(boolean paused) {
        this.isPaused = paused;
        if (!paused) {
            markerX = -1;
            invalidate();
        }
    }

    /**
     * 设置一条线的数据。每次 BLE 数据到达时调用。
     * 内部 ArrayList 快照避免 CME，原始 List 不被修改。
     * 裁剪超出视口范围的数据，防止曲线越界绘制。
     */
    public void setLine(int idx, List<Entry> entries, int colorRes, YAxisDependency dep) {
        if (idx < 0 || idx >= 2) return;
        List<Entry> snapshot = new ArrayList<>(entries);
        // 裁剪超出视口范围的数据
        // 保留 [viewportStart - viewportRange, viewportStart + viewportRange * 1.2] 范围
        // 左侧多保留一个视口宽度用于绘制进入视口的曲线段
        // 右侧 20% 余量防止末端缓动期间数据被裁掉
        float minX = viewportStart - viewportRange;
        float maxX = viewportStart + viewportRange * 1.2f;
        List<Entry> filtered = new ArrayList<>();
        for (Entry e : snapshot) {
            if (e.getX() >= minX && e.getX() <= maxX) filtered.add(e);
        }
        lines[idx] = filtered;
        lineColors[idx] = ContextCompat.getColor(getContext(), colorRes);
        lineDeps[idx] = dep;
        if (idx + 1 > lineCount) lineCount = idx + 1;
        updateAxisBounds();
        invalidate();
    }

    /**
     * 仅更新某条线的颜色，不改变数据。换主题时调用。
     */
    public void setLineColor(int idx, int color) {
        if (idx < 0 || idx >= 2) return;
        lineColors[idx] = color;
        invalidate();
    }

    public void clearLines() {
        for (int i = 0; i < lineCount; i++) {
            lines[i] = null;
            headInited[i] = false;
        }
        lineCount = 0;
        invalidate();
    }

    /**
     * 圆点 Y 缓动，外部 scrollRunnable 每帧调用。
     * 与原 MPAndroidChart HEAD_LERP=0.08 行为一致。
     */
    public void advanceHead() {
        for (int i = 0; i < lineCount; i++) {
            if (lines[i] == null || lines[i].isEmpty()) continue;
            float target = lines[i].get(lines[i].size() - 1).getY();
            headYTarget[i] = target;
            if (!headInited[i]) {
                headYCurrent[i] = target;
                headInited[i] = true;
            } else {
                headYCurrent[i] += (headYTarget[i] - headYCurrent[i]) * HEAD_LERP;
            }
        }
        invalidate();
    }

    public void resetHead() {
        for (int i = 0; i < lineCount; i++) headInited[i] = false;
    }

    public void clearMarker() {
        markerX = -1;
        invalidate();
    }

    // ==================== Y 轴 bounds ====================

    private void updateAxisBounds() {
        if (sharedAxis && lineCount == 2 && lines[0] != null && lines[1] != null) {
            float[] r1 = recentMinMax(lines[0]);
            float[] r2 = recentMinMax(lines[1]);
            float min = Math.min(r1[0], r2[0]);
            float max = Math.max(r1[1], r2[1]);
            setLeftBounds(min, max);
        } else {
            if (lines[0] != null) {
                float[] r0 = recentMinMax(lines[0]);
                setLeftBounds(r0[0], r0[1]);
            }
            if (!rightAxisFixed && lineCount >= 2 && lines[1] != null) {
                float[] r1 = recentMinMax(lines[1]);
                setRightBounds(r1[0], r1[1]);
            }
        }
    }

    private float[] recentMinMax(List<Entry> entries) {
        if (entries == null || entries.size() < 2) return new float[]{0, 1};
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

    private void setLeftBounds(float dataMin, float dataMax) {
        float range = dataMax - dataMin;
        if (range < 1f) range = 1f;
        float padding = range * 0.2f;
        leftAxisMin = dataMin - padding;
        leftAxisMax = dataMax + padding;
        if (!leftAllowNegative && leftAxisMin < 0) leftAxisMin = 0;
    }

    private void setRightBounds(float dataMin, float dataMax) {
        float range = dataMax - dataMin;
        if (range < 1f) range = 1f;
        float padding = range * 0.2f;
        rightAxisMin = dataMin - padding;
        rightAxisMax = dataMax + padding;
    }

    // ==================== 绘制 ====================

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float padTop = 12f, padBottom = 20f;
        float plotH = h - padTop - padBottom;

        // 测量左 Y 轴文字最大宽度，网格线从文字右侧开始，避免与数字重叠
        axisTextPaint.setTextAlign(Paint.Align.LEFT);
        float leftTextMaxW = 0;
        for (int i = 0; i < AXIS_LABEL_COUNT; i++) {
            float value = leftAxisMax - (leftAxisMax - leftAxisMin) * i / (AXIS_LABEL_COUNT - 1);
            String text = String.format("%.1f", value);
            Rect b = new Rect();
            axisTextPaint.getTextBounds(text, 0, text.length(), b);
            if (b.width() > leftTextMaxW) leftTextMaxW = b.width();
        }
        // 测量右 Y 轴文字最大宽度（仅当启用且非 shared）
        float rightTextMaxW = 0;
        if (rightAxisEnabled && !sharedAxis) {
            for (int i = 0; i < AXIS_LABEL_COUNT; i++) {
                float value = rightAxisMax - (rightAxisMax - rightAxisMin) * i / (AXIS_LABEL_COUNT - 1);
                String text = String.format(rightAxisFixed ? "%.0f" : "%.1f", value);
                Rect b = new Rect();
                axisTextPaint.getTextBounds(text, 0, text.length(), b);
                if (b.width() > rightTextMaxW) rightTextMaxW = b.width();
            }
        }
        float leftPad = 4f; // 左侧留白
        float rightPad = 4f; // 右侧留白
        float gridLeft = leftPad + leftTextMaxW + 6f; // 文字右侧 + 6px 间隔
        float gridRight = w - rightPad - (rightTextMaxW > 0 ? rightTextMaxW + 6f : 0f);
        float plotW = gridRight - gridLeft;
        // 缓存给 updateMarker 用
        cachedGridLeft = gridLeft;
        cachedPlotW = plotW;

        // 1. 网格（5 条水平线）
        gridPaint.setColor(gridColor);
        for (int i = 0; i <= 4; i++) {
            float y = padTop + plotH * i / 4f;
            canvas.drawLine(gridLeft, y, gridRight, y, gridPaint);
        }

        // 2. 左 Y 轴刻度
        axisTextPaint.setColor(axisColor);
        axisTextPaint.setTextAlign(Paint.Align.LEFT);
        for (int i = 0; i < AXIS_LABEL_COUNT; i++) {
            float y = padTop + plotH * i / (AXIS_LABEL_COUNT - 1);
            float value = leftAxisMax - (leftAxisMax - leftAxisMin) * i / (AXIS_LABEL_COUNT - 1);
            canvas.drawText(String.format("%.1f", value), leftPad, y + 8f, axisTextPaint);
        }

        // 3. 右 Y 轴刻度（仅当启用且非 shared）
        if (rightAxisEnabled && !sharedAxis) {
            axisTextPaint.setTextAlign(Paint.Align.RIGHT);
            for (int i = 0; i < AXIS_LABEL_COUNT; i++) {
                float y = padTop + plotH * i / (AXIS_LABEL_COUNT - 1);
                float value = rightAxisMax - (rightAxisMax - rightAxisMin) * i / (AXIS_LABEL_COUNT - 1);
                canvas.drawText(String.format(rightAxisFixed ? "%.0f" : "%.1f", value),
                        w - 4f, y + 8f, axisTextPaint);
            }
        }

        // 4. X 轴底部 • 刻度
        axisTextPaint.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < 5; i++) {
            float x = gridLeft + plotW * i / 4f;
            canvas.drawText("•", x, h - 4f, axisTextPaint);
        }

        // 5. 曲线 + 末端圆点（裁剪到网格区域内，与网格边界对齐）
        canvas.save();
        canvas.clipRect(gridLeft, padTop, gridRight, padTop + plotH);
        for (int i = 0; i < lineCount; i++) {
            if (lines[i] == null || lines[i].isEmpty()) continue;
            linePaint.setColor(lineColors[i]);

            // 选择 Y 轴
            YAxisDependency dep = lineDeps[i];
            float yMin, yMax;
            if (sharedAxis) {
                yMin = leftAxisMin; yMax = leftAxisMax;
            } else if (dep == YAxisDependency.LEFT) {
                yMin = leftAxisMin; yMax = leftAxisMax;
            } else {
                yMin = rightAxisMin; yMax = rightAxisMax;
            }

            Path path = new Path();
            boolean first = true;
            float lastPx = 0;
            int entryCount = lines[i].size();
            int idx = 0;
            for (Entry e : lines[i]) {
                float x = e.getX();
                float px = gridLeft + (x - viewportStart) / viewportRange * plotW;
                if (px < -100 || px > w + 100) {
                    first = true;
                    idx++;
                    continue;
                }
                // 末端点的 Y 用缓动值，与圆点同步；其它点用真实数据值
                float entryY;
                boolean isLast = (idx == entryCount - 1);
                if (isLast && headInited[i]) {
                    entryY = headYCurrent[i];
                } else {
                    entryY = e.getY();
                }
                float py = padTop + plotH * (yMax - entryY) / (yMax - yMin);
                if (first) {
                    path.moveTo(px, py);
                    first = false;
                } else {
                    path.lineTo(px, py);
                }
                lastPx = px;
                idx++;
            }
            canvas.drawPath(path, linePaint);

            // 末端圆点（lerp 后的 Y）
            if (headInited[i]) {
                float py = padTop + plotH * (yMax - headYCurrent[i]) / (yMax - yMin);
                headPaint.setColor(lineColors[i]);
                canvas.drawCircle(lastPx, py, 6f, headPaint);
                headPaint.setColor(Color.WHITE);
                canvas.drawCircle(lastPx, py, 2.5f, headPaint);
            }
        }
        canvas.restore();

        // 6. Marker（暂停态点击触发）
        if (markerX >= 0) {
            drawMarker(canvas, gridLeft, gridRight, padTop, plotW, plotH);
        }
    }

    private void drawMarker(Canvas canvas, float gridLeft, float gridRight, float padTop, float plotW, float plotH) {
        float markerPx = gridLeft + (markerX - viewportStart) / viewportRange * plotW;
        if (markerPx < gridLeft || markerPx > gridRight) return;

        // 垂直辅助线
        markerPaint.setColor(axisColor);
        canvas.drawLine(markerPx, padTop, markerPx, padTop + plotH, markerPaint);

        // 找到 markerX 处各线的最近点
        Entry[] nearests = new Entry[lineCount];
        float[] pys = new float[lineCount];
        for (int i = 0; i < lineCount; i++) {
            if (lines[i] == null) continue;
            Entry nearest = null;
            float minDist = Float.MAX_VALUE;
            for (Entry e : lines[i]) {
                float d = Math.abs(e.getX() - markerX);
                if (d < minDist) {
                    minDist = d;
                    nearest = e;
                }
            }
            if (nearest != null) {
                nearests[i] = nearest;
                YAxisDependency dep = lineDeps[i];
                float yMin = sharedAxis ? leftAxisMin :
                        (dep == YAxisDependency.LEFT ? leftAxisMin : rightAxisMin);
                float yMax = sharedAxis ? leftAxisMax :
                        (dep == YAxisDependency.LEFT ? leftAxisMax : rightAxisMax);
                pys[i] = padTop + plotH * (yMax - nearest.getY()) / (yMax - yMin);
            }
        }

        // 在每个数据点画圆点
        for (int i = 0; i < lineCount; i++) {
            if (nearests[i] == null) continue;
            headPaint.setColor(lineColors[i]);
            canvas.drawCircle(markerPx, pys[i], 5f, headPaint);
        }

        // 准备气泡文本：时间 + 各线 "标签: 数值"
        String timeStr = "";
        if (anchorRealTimeMs > 0 && nearests[0] != null) {
            long entryRealTime = anchorRealTimeMs - (long) ((anchorRt - nearests[0].getX()) * 1000);
            timeStr = timeFormat.format(new Date(entryRealTime));
        }
        String[] valueStrs = new String[lineCount];
        for (int i = 0; i < lineCount; i++) {
            if (nearests[i] == null) { valueStrs[i] = null; continue; }
            String label = (markerLabels != null && i < markerLabels.length ? markerLabels[i] : "");
            valueStrs[i] = String.format("%s: %.2f", label, nearests[i].getY());
        }

        // 测量文本尺寸
        float timeTextSize = 36f;
        float valueTextSize = 32f;
        Rect timeBounds = new Rect();
        Rect valueBounds = new Rect();
        markerTextPaint.setTextSize(timeTextSize);
        markerTextPaint.getTextBounds(timeStr, 0, timeStr.length(), timeBounds);
        markerTextPaint.setTextSize(valueTextSize);
        for (int i = 0; i < lineCount; i++) {
            if (valueStrs[i] == null) continue;
            Rect b = new Rect();
            markerTextPaint.getTextBounds(valueStrs[i], 0, valueStrs[i].length(), b);
            if (b.width() > valueBounds.width()) valueBounds = b;
        }

        float paddingH = 16f, paddingV = 10f, lineSpacing = 4f;
        float bubbleW = Math.max(timeBounds.width(), valueBounds.width()) + paddingH * 2;
        float bubbleH = timeBounds.height() + valueBounds.height() * lineCount
                + paddingV * 2 + lineSpacing * (lineCount - 1) + (lineCount > 0 ? lineSpacing : 0);
        if (timeStr.isEmpty()) bubbleH -= timeBounds.height() + lineSpacing;

        // 气泡位置：默认在 marker 上方居中，靠近顶部/边缘时调整
        float bubbleX = markerPx - bubbleW / 2f;
        if (bubbleX < gridLeft) bubbleX = gridLeft;
        if (bubbleX + bubbleW > gridRight) bubbleX = gridRight - bubbleW;
        float bubbleY = padTop + 4f; // 默认顶部
        // 取最高数据点 py 作为参考
        float topPy = padTop + plotH;
        for (int i = 0; i < lineCount; i++) {
            if (nearests[i] != null && pys[i] < topPy) topPy = pys[i];
        }
        // 默认显示在最高数据点上方
        bubbleY = topPy - bubbleH - 12f;
        if (bubbleY < padTop) bubbleY = topPy + 12f; // 靠近顶部改到下方

        // 画圆角矩形背景
        markerTextBgPaint.setColor(0xF0222222);
        markerTextBgPaint.setStyle(Paint.Style.FILL);
        float radius = 8f;
        canvas.drawRoundRect(new RectF(bubbleX, bubbleY, bubbleX + bubbleW, bubbleY + bubbleH),
                radius, radius, markerTextBgPaint);

        // 画时间（第一行，加粗）
        float textX = bubbleX + paddingH;
        float textY = bubbleY + paddingV + timeBounds.height();
        if (!timeStr.isEmpty()) {
            markerTextPaint.setTextSize(timeTextSize);
            markerTextPaint.setColor(0xFFFFFFFF);
            markerTextPaint.setFakeBoldText(true);
            canvas.drawText(timeStr, textX, textY, markerTextPaint);
            markerTextPaint.setFakeBoldText(false);
            textY += lineSpacing;
        }

        // 画数值（每条线一行，颜色与曲线一致）
        markerTextPaint.setTextSize(valueTextSize);
        for (int i = 0; i < lineCount; i++) {
            if (valueStrs[i] == null) continue;
            textY += valueBounds.height();
            markerTextPaint.setColor(lineColors[i]);
            canvas.drawText(valueStrs[i], textX, textY, markerTextPaint);
            if (i < lineCount - 1) textY += lineSpacing;
        }
    }

    // ==================== 触摸：暂停态单击/拖动 marker ====================

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isPaused) return false; // 实时态不响应 marker
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downTime = SystemClock.elapsedRealtime();
                downX = event.getX();
                downY = event.getY();
                markerDragging = false;
                updateMarker(event.getX());
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = Math.abs(event.getX() - downX);
                float dy = Math.abs(event.getY() - downY);
                if (!markerDragging && (dx > 8 || dy > 8)) {
                    markerDragging = true;
                }
                if (markerDragging) updateMarker(event.getX());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                markerDragging = false;
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void updateMarker(float touchX) {
        // 用 onDraw 缓存的网格范围反推 markerX
        if (cachedPlotW <= 0) return;
        float ratio = (touchX - cachedGridLeft) / cachedPlotW;
        if (ratio < 0) ratio = 0;
        if (ratio > 1) ratio = 1;
        markerX = viewportStart + ratio * viewportRange;
        invalidate();
    }
}
