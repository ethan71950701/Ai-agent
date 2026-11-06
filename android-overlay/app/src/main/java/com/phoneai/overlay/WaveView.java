package com.phoneai.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.view.View;

/**
 * 聲波（取代以前的光球）：用函數畫的連續波浪線，三條不同相位與頻率的正弦波疊在一起，
 * 振幅 = 音量 × 包絡線（中間大、兩端漸漸收成 0）。聽的時候隨音量起伏，思考時以固定的振幅、較快的速度流動。
 * 狀態與 {@link #setState}／{@link #setLevel} 沿用光球的用法，所以面板其他程式不用改。
 */
public class WaveView extends View {
    public static final int IDLE = 0, LISTENING = 1, THINKING = 2, DONE = 3;

    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float d;
    private int state = IDLE;
    private float amp, ampTarget;
    private long last;
    private float phase;

    // 三條波：{頻率（週期數）、速度、相對振幅、透明度、線寬(dp)}
    private static final float[][] WAVES = {
            {1.6f, 3.4f, 1.00f, 255, 2.4f},
            {2.4f, -2.7f, 0.62f, 150, 1.8f},
            {3.3f, 4.6f, 0.38f, 90, 1.4f},
    };

    public WaveView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setColor(0xFFFFFFFF);
    }

    public void setState(int s) {
        state = s;
        invalidate();
    }

    /** 0~1，音量。 */
    public void setLevel(float v) { ampTarget = Math.max(0f, Math.min(1f, v)); }

    @Override
    protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        float dt = last == 0 ? 0.016f : Math.min(0.05f, (now - last) / 1000f);
        last = now;
        float goal = state == LISTENING ? 0.12f + 0.88f * ampTarget : (state == THINKING ? 0.5f : 0.06f);
        amp += (goal - amp) * Math.min(1f, dt * 10f);
        phase += dt * (state == THINKING ? 1.9f : 1f);

        float w = getWidth(), h = getHeight(), cy = h / 2f, maxA = h * 0.46f;
        int steps = Math.max(24, (int) (w / (3f * d)));
        for (float[] wv : WAVES) {
            path.reset();
            for (int i = 0; i <= steps; i++) {
                float u = i / (float) steps;                                   // 0~1，由左到右
                float env = (float) Math.pow(Math.sin(Math.PI * u), 2);         // 兩端 0、中間 1
                float y = cy + maxA * amp * wv[2] * env * (float) Math.sin(2 * Math.PI * wv[0] * u + phase * wv[1]);
                float x = u * w;
                if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            line.setStrokeWidth(wv[4] * d);
            line.setAlpha((int) wv[3]);
            c.drawPath(path, line);
        }
        postInvalidateOnAnimation();
    }
}
