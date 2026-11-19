package com.example.fitlog.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.fitlog.R;

import java.util.Locale;

/**
 * Basit çubuk grafik: her çubuk bir gün, isteğe bağlı kesikli hedef çizgisi.
 * Hedefi aşan çubuklar turuncu çizilir. Renkler tema token'larından gelir.
 */
public class BarChartView extends View {

    private float[] values = new float[0];
    private String[] labels = new String[0];
    private float goal = 0;
    private int highlightIndex = -1;
    private boolean overGoalWarning = true;

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint goalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path goalPath = new Path();
    private final RectF rect = new RectF();

    private final int colorBar, colorBarDim, colorOver, colorTrack;
    private final float density;

    public BarChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        colorBar = ContextCompat.getColor(context, R.color.brand_green);
        colorBarDim = ContextCompat.getColor(context, R.color.brand_green_dark);
        colorOver = ContextCompat.getColor(context, R.color.accent_orange);
        colorTrack = ContextCompat.getColor(context, R.color.track);

        labelPaint.setColor(ContextCompat.getColor(context, R.color.text_secondary));
        labelPaint.setTextSize(11 * density);
        labelPaint.setTextAlign(Paint.Align.CENTER);

        valuePaint.setColor(ContextCompat.getColor(context, R.color.text_primary));
        valuePaint.setTextSize(10 * density);
        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setFakeBoldText(true);

        goalPaint.setColor(colorOver);
        goalPaint.setStyle(Paint.Style.STROKE);
        goalPaint.setStrokeWidth(1.5f * density);
        goalPaint.setPathEffect(new DashPathEffect(new float[]{6 * density, 4 * density}, 0));
    }

    /** Değerleri eski → yeni (soldan sağa) sırayla verin. */
    public void setData(float[] values, String[] labels, float goal) {
        this.values = values != null ? values : new float[0];
        this.labels = labels != null ? labels : new String[0];
        this.goal = goal;
        setContentDescription(buildDescription());
        invalidate();
    }

    public void setHighlightIndex(int index) {
        highlightIndex = index;
        invalidate();
    }

    public void setOverGoalWarning(boolean enabled) {
        overGoalWarning = enabled;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredHeight = (int) (180 * density);
        setMeasuredDimension(resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec),
                resolveSize(desiredHeight, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int n = values.length;
        if (n == 0) return;

        float labelArea = labels.length > 0 ? 18 * density : 0;
        float valueArea = n <= 10 ? 14 * density : 0;
        float top = getPaddingTop() + valueArea;
        float bottom = getHeight() - getPaddingBottom() - labelArea;
        float left = getPaddingLeft();
        float width = getWidth() - getPaddingLeft() - getPaddingRight();
        float chartH = bottom - top;
        if (chartH <= 0 || width <= 0) return;

        float max = goal;
        for (float v : values) max = Math.max(max, v);
        if (max <= 0) max = 1;
        max *= 1.1f;

        float slot = width / n;
        float barW = Math.min(slot * 0.6f, 28 * density);
        float radius = Math.min(barW / 2, 6 * density);
        int labelEvery = n <= 10 ? 1 : (int) Math.ceil(n / 7.0);

        for (int i = 0; i < n; i++) {
            float cx = left + slot * i + slot / 2;
            // arka iz
            barPaint.setColor(colorTrack);
            rect.set(cx - barW / 2, top, cx + barW / 2, bottom);
            canvas.drawRoundRect(rect, radius, radius, barPaint);

            float v = values[i];
            if (v > 0) {
                float h = chartH * (v / max);
                boolean over = overGoalWarning && goal > 0 && v > goal * 1.1f;
                barPaint.setColor(over ? colorOver : (i == highlightIndex ? colorBarDim : colorBar));
                rect.set(cx - barW / 2, bottom - Math.max(h, radius * 2), cx + barW / 2, bottom);
                canvas.drawRoundRect(rect, radius, radius, barPaint);
                if (valueArea > 0) {
                    canvas.drawText(shortNumber(v), cx, rect.top - 4 * density, valuePaint);
                }
            }
            if (i < labels.length && (i % labelEvery == 0 || i == n - 1)) {
                canvas.drawText(labels[i], cx, getHeight() - getPaddingBottom() - 4 * density, labelPaint);
            }
        }

        if (goal > 0) {
            float y = bottom - chartH * (goal / max);
            goalPath.reset();
            goalPath.moveTo(left, y);
            goalPath.lineTo(left + width, y);
            canvas.drawPath(goalPath, goalPaint);
        }
    }

    static String shortNumber(float v) {
        if (v >= 10000) return String.format(Locale.ROOT, "%.0fk", v / 1000f);
        if (v >= 1000) return String.format(Locale.ROOT, "%.1fk", v / 1000f);
        return String.valueOf(Math.round(v));
    }

    private String buildDescription() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i < labels.length) sb.append(labels[i]).append(": ");
            sb.append(Math.round(values[i])).append("; ");
        }
        return sb.toString();
    }
}
