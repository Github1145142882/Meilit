package com.meilit.halo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.Arrays;

public final class HaloRingView extends View {
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int[] colors = new int[Aw20072Controller.LED_COUNT];
    private final int[] brightness = new int[Aw20072Controller.LED_COUNT];
    private int selectedLed = 1;

    public HaloRingView(Context context) {
        super(context);
        init();
    }

    public HaloRingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    void clear() {
        Arrays.fill(colors, Color.TRANSPARENT);
        Arrays.fill(brightness, 0);
        invalidate();
    }

    void setSelectedLed(int selectedLed) {
        this.selectedLed = selectedLed;
        invalidate();
    }

    void setLed(int led, int level, int color) {
        if (led < 1 || led > Aw20072Controller.LED_COUNT) return;
        int index = led - 1;
        brightness[index] = Math.max(0, Math.min(level, 63));
        colors[index] = color & 0x00FFFFFF;
        invalidate();
    }

    void setAll(int level, int color) {
        for (int i = 0; i < Aw20072Controller.LED_COUNT; i++) {
            colors[i] = color & 0x00FFFFFF;
            brightness[i] = Math.max(0, Math.min(level, 63));
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int requested = dp(220);
        int width = resolveSize(requested, widthMeasureSpec);
        int height = resolveSize(requested, heightMeasureSpec);
        setMeasuredDimension(width, Math.min(width, height));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight());
        float center = size / 2f;
        float ringRadius = size * 0.36f;
        float ledRadius = Math.max(dp(8), size * 0.045f);

        strokePaint.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < Aw20072Controller.LED_COUNT; i++) {
            double radians = Math.toRadians(-90 + i * (360f / Aw20072Controller.LED_COUNT));
            float x = center + (float) Math.cos(radians) * ringRadius;
            float y = center + (float) Math.sin(radians) * ringRadius;

            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(resolveLedColor(i));
            canvas.drawCircle(x, y, ledRadius, fillPaint);

            strokePaint.setColor((i + 1) == selectedLed ? Color.WHITE : 0x663A4652);
            strokePaint.setStrokeWidth((i + 1) == selectedLed ? dp(2) : dp(1));
            canvas.drawCircle(x, y, ledRadius + dp(2), strokePaint);
        }
    }

    private int resolveLedColor(int index) {
        int level = brightness[index];
        if (level <= 0) return 0xFF202A33;
        float scale = Math.max(0.12f, level / 63f);
        int color = colors[index];
        return Color.rgb(
                Math.round(Color.red(color) * scale),
                Math.round(Color.green(color) * scale),
                Math.round(Color.blue(color) * scale));
    }

    private void init() {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        clear();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
