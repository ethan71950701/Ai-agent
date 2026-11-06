package com.phoneai.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/**
 * 聲波（取代以前的光球）：五條用函數畫的波浪線疊在一起，每條由兩個不同頻率的正弦合成、振幅自己緩緩起伏，
 * 再乘上「兩端 0、中間 1」的包絡線，並用漸層上色。聽的時候振幅隨音量；思考時以固定振幅、更快的速度流動。
 * 狀態與 {@link #setState}／{@link #setLevel} 沿用光球的用法，所以面板其他程式不用改。
 */
public class WaveView extends View {
    public static final int IDLE = 0, LISTENING = 1, THINKING = 2, DONE = 3;

    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float d;
    private int state = IDLE;
    private float amp, ampTarget;
    private float idleGoal = 0.1f;
    private long last;
    private float phase;

    // 每條波：{頻率 1（週期數）、速度 1、頻率 2、速度 2、相對振幅、透明度、線寬(dp)}
    private static final float[][] WAVES = {
            {1.5f, 3.0f, 3.1f, -4.2f, 1.00f, 255, 2.6f},
            {2.1f, -2.4f, 4.3f, 3.1f, 0.78f, 190, 2.0f},
            {2.9f, 3.8f, 5.0f, -2.2f, 0.58f, 140, 1.7f},
            {1.2f, -1.8f, 2.6f, 2.9f, 0.90f, 100, 3.6f},
            {3.7f, 5.0f, 6.1f, -3.5f, 0.42f, 80, 1.3f},
    };
    private static final int[] COLORS = {0xFF6EA8FF, 0xFFB388FF, 0xFFFF7BAC, 0xFFFFC46B};   // 藍 → 紫 → 粉 → 暖黃（跟極光同一組顏色）

    public WaveView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        line.setShader(new LinearGradient(0, 0, w, 0, COLORS, null, Shader.TileMode.CLAMP));
    }

    public void setState(int s) {
        state = s;
        invalidate();
    }

    /** 閒置時的振幅（0~1）；首頁裝飾用較大的值，平常 0.1。 */
    public void setIdle(float g) { idleGoal = Math.max(0f, Math.min(1f, g)); }

    /** 0~1，音量。 */
    public void setLevel(float v) { ampTarget = Math.max(0f, Math.min(1f, v)); }

    @Override
    protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        float dt = last == 0 ? 0.016f : Math.min(0.05f, (now - last) / 1000f);
        last = now;
        float goal = state == LISTENING ? 0.28f + 0.72f * ampTarget : (state == THINKING ? 0.65f : idleGoal);
        amp += (goal - amp) * Math.min(1f, dt * 10f);
        phase += dt * (state == THINKING ? 1.9f : 1f);
        float t = now / 1000f;

        float w = getWidth(), h = getHeight(), cy = h / 2f, maxA = h * 0.5f;
        int steps = Math.max(32, (int) (w / (2.5f * d)));
        for (int k = 0; k < WAVES.length; k++) {
            float[] wv = WAVES[k];
            float breathe = 0.72f + 0.28f * (float) Math.sin(t * (1.1f + 0.35f * k) + k * 1.9f);   // 每條自己的呼吸
            path.reset();
            for (int i = 0; i <= steps; i++) {
                float u = i / (float) steps;                                    // 0~1，由左到右
                float env = (float) Math.pow(Math.sin(Math.PI * u), 2);          // 兩端 0、中間 1
                double a = Math.sin(2 * Math.PI * wv[0] * u + phase * wv[1]);
                double b = Math.sin(2 * Math.PI * wv[2] * u + phase * wv[3]);
                float y = cy + maxA * amp * wv[4] * breathe * env * (float) (0.65 * a + 0.35 * b);
                float x = u * w;
                if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            line.setStrokeWidth(wv[6] * d);
            line.setAlpha((int) wv[5]);
            c.drawPath(path, line);
        }
        postInvalidateOnAnimation();
    }
}
