package com.example.fitlog.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.example.fitlog.R;

import java.util.Locale;

/** Kilo değişimi için çizgi grafik; isteğe bağlı hedef çizgisi. */
public class LineChartView extends View {

    private float[] values = new float[0];
    private String[] labels = new String[0];
    private float target = 0;

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint targetPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path line = new Path();
    private final Path fill = new Path();
    private final int colorLine;
    private final float density;

    public LineChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        colorLine = ContextCompat.getColor(context, R.color.brand_green);

        linePaint.setColor(colorLine);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(2.5f * density);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setStrokeCap(Paint.Cap.ROUND);

        pointPaint.setColor(colorLine);

        targetPaint.setColor(ContextCompat.getColor(context, R.color.accent_orange));
        targetPaint.setStyle(Paint.Style.STROKE);
        targetPaint.setStrokeWidth(1.5f * density);
        targetPaint.setPathEffect(new DashPathEffect(new float[]{6 * density, 4 * density}, 0));

        textPaint.setColor(ContextCompat.getColor(context, R.color.text_secondary));
        textPaint.setTextSize(11 * density);
    }

    /** Değerler eski → yeni sırayla. target 0 ise hedef çizgisi çizilmez. */
    public void setData(float[] values, String[] labels, float target) {
        this.values = values != null ? values : new float[0];
        this.labels = labels != null ? labels : new String[0];
        this.target = target;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec),
                resolveSize((int) (200 * density), heightMeasureSpec));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        fillPaint.setShader(new LinearGradient(0, 0, 0, h,
                ColorUtils.setAlphaComponent(colorLine, 70), ColorUtils.setAlphaComponent(colorLine, 0),
                Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int n = values.length;
        if (n == 0) return;

        float axisW = 36 * density;
        float labelH = 18 * density;
        float left = getPaddingLeft() + axisW;
        float right = getWidth() - getPaddingRight() - 8 * density;
        float top = getPaddingTop() + 10 * density;
        float bottom = getHeight() - getPaddingBottom() - labelH;

        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (float v : values) { min = Math.min(min, v); max = Math.max(max, v); }
        if (target > 0) { min = Math.min(min, target); max = Math.max(max, target); }
        float pad = Math.max(1f, (max - min) * 0.15f);
        min -= pad; max += pad;

        float stepX = n > 1 ? (right - left) / (n - 1) : 0;
        line.reset(); fill.reset();
        for (int i = 0; i < n; i++) {
            float x = n > 1 ? left + stepX * i : (left + right) / 2;
            float y = bottom - (values[i] - min) / (max - min) * (bottom - top);
            if (i == 0) { line.moveTo(x, y); fill.moveTo(x, bottom); fill.lineTo(x, y); }
            else { line.lineTo(x, y); fill.lineTo(x, y); }
            if (i == n - 1) { fill.lineTo(x, bottom); fill.close(); }
        }
        if (n > 1) canvas.drawPath(fill, fillPaint);
        canvas.drawPath(line, linePaint);

        float r = n > 30 ? 2 * density : 3.5f * density;
        for (int i = 0; i < n; i++) {
            float x = n > 1 ? left + stepX * i : (left + right) / 2;
            float y = bottom - (values[i] - min) / (max - min) * (bottom - top);
            canvas.drawCircle(x, y, i == n - 1 ? r * 1.6f : r, pointPaint);
        }

        // Y ekseni: min / max
        textPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(String.format(Locale.getDefault(), "%.0f", max - pad), getPaddingLeft(), top + 8 * density, textPaint);
        canvas.drawText(String.format(Locale.getDefault(), "%.0f", min + pad), getPaddingLeft(), bottom, textPaint);

        if (target > 0) {
            float y = bottom - (target - min) / (max - min) * (bottom - top);
            Path p = new Path();
            p.moveTo(left, y);
            p.lineTo(right, y);
            canvas.drawPath(p, targetPaint);
        }

        // X etiketleri: ilk ve son
        if (labels.length == n && n > 0) {
            textPaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(labels[0], left, getHeight() - getPaddingBottom() - 2 * density, textPaint);
            if (n > 1) {
                textPaint.setTextAlign(Paint.Align.RIGHT);
                canvas.drawText(labels[n - 1], right, getHeight() - getPaddingBottom() - 2 * density, textPaint);
            }
        }
    }
}
