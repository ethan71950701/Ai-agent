package com.phoneai.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.LinearGradient;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/** 背景：流動的極光漸層 + 往上飄的粒子。energy 越高（聽到聲音時）越亮、粒子越快越密。 */
public class AuroraView extends View {
    static final int[] PALETTE = {0xFF4285F4, 0xFF9B72CB, 0xFFD96570, 0xFF34A8EB};
    private static final int BLOBS = 4, PARTS = 260;

    private final Paint blob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RadialGradient[] shaders = new RadialGradient[BLOBS];
    private final float[] bx = new float[PARTS], by = new float[PARTS], speed = new float[PARTS],
            size = new float[PARTS], life = new float[PARTS], phase = new float[PARTS];
    private final int[] color = new int[PARTS];
    private final Random rnd = new Random(7);
    private final float d;
    private float radius, energy, target = 0.3f, glowY = -1;
    private long last;

    // 開場動畫：從螢幕最上方放出一圈圈光波往下擴散 + 細碎光點往下灑（像小愛同學喚醒）
    private static final float INTRO_S = 1.25f;
    private static final int INTRO_PARTS = 140;
    private final float[] ix = new float[INTRO_PARTS], idelay = new float[INTRO_PARTS], ivx = new float[INTRO_PARTS],
            ivy = new float[INTRO_PARTS], isize = new float[INTRO_PARTS], ilife = new float[INTRO_PARTS];
    private final int[] icolor = new int[INTRO_PARTS];
    private final Paint wave = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean introPending;
    private long introStart;

    /** 重播開場動畫（尺寸還沒量好時會等到第一次繪製才開始）。 */
    public void playIntro() { introPending = true; introStart = 0; }

    public AuroraView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
    }

    /** 0（安靜）~ 1（很大聲） */
    public void setEnergy(float e) { target = Math.max(0f, Math.min(1f, e)); }

    /** 光暈中心的 y（通常是面板頂端）。 */
    public void setGlowY(float y) { glowY = y; }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        radius = Math.max(w, 1) * 0.62f;
        for (int i = 0; i < BLOBS; i++) {
            shaders[i] = new RadialGradient(0, 0, radius,
                    new int[]{PALETTE[i], PALETTE[i] & 0x00FFFFFF}, null, Shader.TileMode.CLAMP);
        }
        for (int i = 0; i < PARTS; i++) respawn(i, true);
    }

    private void respawn(int i, boolean first) {
        int w = Math.max(getWidth(), 1);
        bx[i] = rnd.nextFloat() * w;
        by[i] = (glowY > 0 ? glowY : getHeight() * 0.75f) + rnd.nextFloat() * 80 * d;
        speed[i] = 0.05f + rnd.nextFloat() * 0.15f;
        size[i] = 0.30f + rnd.nextFloat() * rnd.nextFloat() * 1.0f;  // 絕大多數是極細的塵點，偶爾一顆稍亮
        life[i] = first ? rnd.nextFloat() : 0f;
        phase[i] = rnd.nextFloat() * 6.28f;
        // 多數偏白、帶一點色，像圈選搜尋那種細碎的光點；少數是主色
        color[i] = (rnd.nextInt(10) < 6 ? 0xFFFFFF : (PALETTE[rnd.nextInt(PALETTE.length)] & 0x00FFFFFF));
    }

    @Override
    protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        float dt = last == 0 ? 0.016f : Math.min(0.05f, (now - last) / 1000f);
        last = now;
        energy += (target - energy) * Math.min(1f, dt * 5f);
        float t = now / 1000f, w = getWidth(), h = getHeight();
        float gy = glowY > 0 ? glowY : h * 0.75f;

        for (int i = 0; i < BLOBS; i++) {
            float cx = w * (0.5f + 0.42f * (float) Math.sin(t * (0.35f + 0.11f * i) + i * 1.7f));
            float cy = gy + 30 * d * (float) Math.sin(t * (0.5f + 0.09f * i) + i * 2.3f);
            float s = 0.7f + 0.4f * energy + 0.08f * (float) Math.sin(t * 1.1f + i);
            blob.setShader(shaders[i]);
            blob.setAlpha((int) (255 * (0.22f + 0.42f * energy)));
            c.save();
            c.translate(cx, cy);
            c.scale(s, s);
            c.drawCircle(0, 0, radius, blob);
            c.restore();
        }

        float rise = Math.max(h * 0.35f, 200 * d);
        for (int i = 0; i < PARTS; i++) {
            life[i] += dt * speed[i] * (0.6f + energy * 2.4f);
            if (life[i] >= 1f) respawn(i, false);
            float x = bx[i] + (float) Math.sin(t * 0.9f + phase[i]) * 8 * d;
            float y = by[i] - life[i] * rise;
            float twinkle = 0.55f + 0.45f * (float) Math.sin(t * (2.2f + speed[i] * 6f) + phase[i] * 3f);  // 閃爍
            float a = (float) Math.sin(Math.PI * life[i]) * (0.30f + 0.60f * energy) * twinkle;
            float r = size[i] * d * (0.85f + 0.5f * energy);
            dot.setColor(color[i] | ((int) (255 * a * 0.12f) << 24));
            c.drawCircle(x, y, r * 2.0f, dot);  // 很淡的小光暈
            dot.setColor(color[i] | ((int) (255 * Math.min(1f, a * 1.2f)) << 24));
            c.drawCircle(x, y, r, dot);
        }
        drawIntro(c, now, w, h);
        postInvalidateOnAnimation();
    }

    private void drawIntro(Canvas c, long now, float w, float h) {
        if (introPending && w > 0) {
            introPending = false;
            introStart = now;
            for (int i = 0; i < INTRO_PARTS; i++) {
                ix[i] = rnd.nextFloat() * w;
                idelay[i] = rnd.nextFloat() * 0.5f;
                ivx[i] = (rnd.nextFloat() - 0.5f) * 70 * d;
                ivy[i] = (180 + rnd.nextFloat() * 520) * d;
                isize[i] = 0.35f + rnd.nextFloat() * rnd.nextFloat() * 1.1f;
                ilife[i] = 0.9f + rnd.nextFloat() * 0.55f;
                icolor[i] = rnd.nextInt(10) < 6 ? 0xFFFFFF : (PALETTE[rnd.nextInt(PALETTE.length)] & 0x00FFFFFF);
            }
        }
        if (introStart == 0) return;
        float t = (now - introStart) / 1000f;
        if (t > INTRO_S + 0.3f) { introStart = 0; return; }

        // 光波：圓心在螢幕最上方中間，三圈依序往外、往下擴散，邊緣柔和、逐漸變細變淡
        float cx = w / 2f, cy = -0.04f * h;
        for (int k = 0; k < 3; k++) {
            float p = Math.max(0f, Math.min(1f, (t - k * 0.13f) / 1.0f));
            if (p <= 0f || p >= 1f) continue;
            float e = 1f - (1f - p) * (1f - p) * (1f - p);             // easeOutCubic
            float rad = e * h * 1.2f + 8 * d;
            float band = (96 - 52 * p) * d;                             // 光環寬度由粗變細
            float outer = rad + band * 0.5f;
            float inner = Math.max(0f, rad - band * 0.5f);
            int col = PALETTE[k % PALETTE.length] & 0x00FFFFFF;
            float a = (float) Math.pow(1f - p, 1.3f);
            // 兩端是 alpha 0 的同色（col 沒有 alpha），中間才是亮的光環
            wave.setShader(new RadialGradient(cx, cy, outer,
                    new int[]{col, col | ((int) (255 * 0.85f * a) << 24), col},
                    new float[]{Math.min(0.98f, inner / outer), Math.min(0.99f, rad / outer), 1f},
                    Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, outer, wave);
        }
        // 螢幕頂邊的一道光，隨波漸隱
        float topA = Math.max(0f, 1f - t / 0.8f);
        if (topA > 0f) {
            wave.setShader(new LinearGradient(0, 0, 0, 90 * d,
                    new int[]{0xFFFFFF | ((int) (255 * 0.55f * topA) << 24), 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, 90 * d, wave);
        }
        wave.setShader(null);

        // 灑下的光點
        for (int i = 0; i < INTRO_PARTS; i++) {
            float tt = t - idelay[i];
            if (tt < 0f || tt > ilife[i]) continue;
            float x = ix[i] + ivx[i] * tt;
            float y = -8 * d + ivy[i] * tt + 0.5f * 260 * d * tt * tt;
            float a = (float) Math.pow(1f - tt / ilife[i], 1.4f) * 0.95f;
            float r = isize[i] * d;
            dot.setColor(icolor[i] | ((int) (255 * a * 0.18f) << 24));
            c.drawCircle(x, y, r * 2.4f, dot);
            dot.setColor(icolor[i] | ((int) (255 * a) << 24));
            c.drawCircle(x, y, r, dot);
        }
    }
}
