package com.phoneai.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.os.SystemClock;
import android.view.View;

/** 中間的漸層光球：待命時呼吸，聽的時候隨音量脈動並擴散光圈，思考/送出時加速旋轉。 */
public class OrbView extends View {
    public static final int IDLE = 0, LISTENING = 1, THINKING = 2, DONE = 3;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hl = new Paint(Paint.ANTI_ALIAS_FLAG);
    private SweepGradient sweep;
    private RadialGradient glowShader, hlShader;
    private int state = IDLE;
    private float amp, ampTarget, rot, burst;
    private long last;
    private final float d;

    public OrbView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(2 * d);
        // 單位大小的漸層，用 canvas.scale 放大
        sweep = new SweepGradient(0, 0, new int[]{0xFF4285F4, 0xFF34A8EB, 0xFF9B72CB, 0xFFD96570, 0xFFF9AB00, 0xFF9B72CB, 0xFF4285F4}, null);
        glowShader = new RadialGradient(0, 0, 1, new int[]{0xFF9B72CB, 0x009B72CB}, null, Shader.TileMode.CLAMP);
        hlShader = new RadialGradient(0, 0, 1, new int[]{0x99FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP);
        fill.setShader(sweep);
        glow.setShader(glowShader);
        hl.setShader(hlShader);
    }

    public void setState(int s) {
        state = s;
        if (s == DONE) burst = 1f;
    }

    /** 0~1，音量。 */
    public void setLevel(float v) { ampTarget = Math.max(0f, Math.min(1f, v)); }

    @Override
    protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        float dt = last == 0 ? 0.016f : Math.min(0.05f, (now - last) / 1000f);
        last = now;
        float t = now / 1000f;
        float goal = state == LISTENING ? ampTarget : (state == THINKING ? 0.35f : 0f);
        amp += (goal - amp) * Math.min(1f, dt * 10f);
        burst = Math.max(0f, burst - dt * 1.6f);
        float spin = state == THINKING ? 320f : (state == LISTENING ? 50f + 140f * amp : 28f);
        rot = (rot + spin * dt) % 360f;

        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        // 所有東西（球、光暈、圓環）都必須畫在這個 View 的邊框裡面：View 重畫時系統只更新它自己的邊框範圍（髒區域），
        // 畫到邊框外的部分不會被更新，看起來就是被切掉（使用者回報圓環上端被切；先前只關掉父層裁切沒用）
        float maxR = Math.min(getWidth(), getHeight()) / 2f - 1f;
        float base = Math.min(getWidth(), getHeight()) * 0.26f;
        float r = base * (1f + 0.04f * (float) Math.sin(t * 1.6f) + 0.30f * amp + 0.25f * burst);

        // 外圈光暈
        c.save();
        c.translate(cx, cy);
        float gr = Math.min(r * 2.0f, maxR);  // 光暈半徑不超過邊框；它是放射狀漸層，到邊緣本來就是透明
        c.scale(gr, gr);
        glow.setAlpha((int) (255 * Math.min(1f, 0.35f + 0.6f * amp + 0.5f * burst)));
        c.drawCircle(0, 0, 1f, glow);
        c.restore();

        // 擴散光圈（聽的時候明顯）
        float ringStrength = state == LISTENING ? (0.25f + 0.75f * amp) : (state == THINKING ? 0.3f : 0.12f);
        for (int k = 0; k < 3; k++) {
            float ph = (t * 0.8f + k / 3f) % 1f;
            // 圓環從球邊擴散到「最大半徑」（不超過邊框），擴散到最外面時透明度剛好歸零，所以不會有東西畫到邊框外
            float outer = Math.min(r * (1.05f + 0.7f + 0.9f * amp), maxR);
            float rr = r * 1.05f + ph * Math.max(0f, outer - r * 1.05f);
            ring.setColor(AuroraView.PALETTE[k % AuroraView.PALETTE.length] & 0x00FFFFFF
                    | ((int) (255 * (1f - ph) * 0.55f * ringStrength) << 24));
            c.drawCircle(cx, cy, rr, ring);
        }

        // 球體：旋轉的漸層
        c.save();
        c.translate(cx, cy);
        c.rotate(rot);
        c.scale(r, r);
        c.drawCircle(0, 0, 1f, fill);
        c.restore();

        // 高光，讓它像一顆球
        c.save();
        c.translate(cx - 0.32f * r, cy - 0.38f * r);
        c.scale(r * 0.85f, r * 0.85f);
        c.drawCircle(0, 0, 1f, hl);
        c.restore();

        postInvalidateOnAnimation();
    }
}
