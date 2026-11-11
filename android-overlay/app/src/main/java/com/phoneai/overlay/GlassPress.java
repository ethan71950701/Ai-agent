package com.phoneai.overlay;

import android.animation.ValueAnimator;
import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * 液態玻璃按壓：手指按下去，手指底下浮出一顆玻璃透鏡（跟底欄同一種質感）：
 *  - 透鏡中間把底下的內容微微放大，靠邊緣的內容被折射、擠壓，邊緣有一點色散，
 *  - 邊緣有一圈亮邊（光從左上來，左上較亮、右下較暗），左上內側有一小塊柔和的高光，透鏡底下有一點影子，
 *  - 手指在卡片上滑動，透鏡會帶著一點延遲跟著走（像液體）；
 *  - 放開，透鏡縮小、淡出，融回卡片裡。
 * 用 AGSL 著色器（RuntimeShader）＋ View.setRenderEffect，直接處理卡片自己畫出來的內容；不用 RippleDrawable。
 * 著色器若在這支手機上編譯失敗，{@link #broken} 變成 true，呼叫端改用系統水波紋。
 */
final class GlassPress {
    private GlassPress() { }

    static volatile boolean broken;
    private static final int TAG = 0x7f0a0e5a;   // View.setTag 用的鍵：這個 View 目前的透鏡（St）

    private static final long GROW_MS = 260;       // 透鏡長出來
    private static final long MIN_HOLD_MS = 160;   // 快速點一下也至少讓透鏡出現這麼久才開始縮
    private static final long SHRINK_MS = 380;     // 放開後縮小、淡出

    private static final String SRC = ""
            + "uniform shader content;\n"
            + "uniform float2 center;\n"
            + "uniform float2 size;\n"
            + "uniform float lensR;\n"                                   // 透鏡半徑（px）
            + "uniform float amp;\n"                                     // 強度 0～1（淡入淡出）
            + "half4 main(float2 fc) {\n"
            + "  half4 base = content.eval(fc);\n"
            + "  float2 d = fc - center;\n"
            + "  float dist = length(d);\n"
            + "  float R = max(lensR, 1.0);\n"
            + "  float t = dist / R;\n"                                   // 0＝透鏡中心，1＝邊緣
            + "  if (t > 1.35 || amp <= 0.001) return base;\n"
            + "  float2 dir = d / max(dist, 0.001);\n"
            // 透鏡底下的影子（只在透鏡外一圈）
            + "  float outside = smoothstep(0.97, 1.0, t) * (1.0 - smoothstep(1.0, 1.35, t));\n"
            + "  half4 outc = base;\n"
            + "  outc.rgb *= half(1.0 - 0.22 * amp * outside);\n"
            + "  if (t >= 1.0) return outc;\n"
            // 折射：中間放大、靠邊緣的內容被往外擠（取樣點往外推）
            + "  float edge = smoothstep(0.45, 1.0, t);\n"
            + "  float zoom = 1.0 - 0.20 * amp * (1.0 - t * t);\n"
            + "  float2 hi = size - float2(1.0);\n"
            + "  float2 s = center + d * zoom;\n"
            + "  s += dir * (edge * edge * 0.22 * R * amp);\n"
            + "  float2 spread = dir * (edge * 3.5 * amp);\n"             // 色散：紅藍通道多偏一點
            + "  half4 cr = content.eval(clamp(s + spread, float2(0.0), hi));\n"
            + "  half4 cg = content.eval(clamp(s, float2(0.0), hi));\n"
            + "  half4 cb = content.eval(clamp(s - spread, float2(0.0), hi));\n"
            + "  half4 c = half4(cr.r, cg.g, cb.b, cg.a);\n"
            // 光澤：邊緣亮邊（光從左上）＋左上內側一小塊柔和的高光
            + "  float band = smoothstep(0.82, 0.985, t);\n"
            + "  float ld = dot(dir, float2(-0.6, -0.8));\n"
            + "  float rim = band * (0.30 + 0.70 * max(ld, 0.0)) + band * 0.22 * max(-ld, 0.0);\n"
            + "  float2 sp = fc - (center + R * float2(-0.32, -0.38));\n"
            + "  float spot = exp(-dot(sp, sp) / (0.07 * R * R));\n"
            + "  float add = (rim * 0.5 + spot * 0.20 + 0.04) * amp;\n"
            + "  c.rgb += half3(add) * c.a;\n"
            + "  float aa = 1.0 - smoothstep(0.985, 1.0, t);\n"          // 邊緣抗鋸齒
            + "  return outc + (c - outc) * half(aa);\n"
            + "}\n";

    private static final class St {
        RuntimeShader sh;
        float w, h, baseR;
        float tx, ty, cx, cy;   // 手指（目標）與透鏡目前的位置（帶一點延遲跟著手指）
        long t0 = SystemClock.uptimeMillis();
        long rel = -1;          // 手指放開的時間（-1＝還按著）
        ValueAnimator va;
    }

    /** 長出來：會稍微衝過頭再回穩。 */
    private static float easeOutBack(float t) {
        float c1 = 1.4f, c3 = c1 + 1f, u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }

    /** 在 (x, y)（這個 View 自己的座標）放一顆玻璃透鏡；上一顆還在就先收掉。 */
    static void start(final View v, float x, float y) {
        if (broken || v.getWidth() <= 0 || v.getHeight() <= 0) return;
        stop(v);
        try {
            final St st = new St();
            final float dens = v.getResources().getDisplayMetrics().density;
            st.sh = new RuntimeShader(SRC);
            st.w = v.getWidth();
            st.h = v.getHeight();
            st.baseR = Math.max(48f * dens, Math.min(120f * dens, 0.55f * Math.min(st.w, st.h)));
            st.tx = st.cx = x;
            st.ty = st.cy = y;
            st.sh.setFloatUniform("size", st.w, st.h);
            st.va = ValueAnimator.ofFloat(0f, 1f);
            st.va.setDuration(1000);
            st.va.setRepeatCount(ValueAnimator.INFINITE);   // 按著時一直重畫（透鏡跟著手指）；縮完才停
            st.va.setInterpolator(new LinearInterpolator());
            st.va.addUpdateListener(an -> {
                try {
                    if (!v.isAttachedToWindow()) { an.cancel(); return; }   // 點下去已經換頁了
                    long now = SystemClock.uptimeMillis();
                    long el = now - st.t0;
                    float g = Math.min(1f, el / (float) GROW_MS);
                    float scale = 0.55f + 0.45f * easeOutBack(g);
                    float a = Math.min(1f, el / 160f);
                    if (st.rel >= 0) {
                        long start = Math.max(st.rel, st.t0 + MIN_HOLD_MS);
                        if (now >= start) {
                            float q = Math.min(1f, (now - start) / (float) SHRINK_MS);
                            float s = q * q * (3f - 2f * q);
                            scale *= 1f - 0.85f * s;   // 縮小
                            a *= 1f - s;               // 淡出
                            if (q >= 1f) { an.cancel(); return; }
                        }
                    }
                    st.cx += (st.tx - st.cx) * 0.3f;   // 帶一點延遲跟著手指（像液體）
                    st.cy += (st.ty - st.cy) * 0.3f;
                    st.sh.setFloatUniform("center", st.cx, st.cy);
                    st.sh.setFloatUniform("lensR", st.baseR * scale);
                    st.sh.setFloatUniform("amp", a);
                    v.setRenderEffect(RenderEffect.createRuntimeShaderEffect(st.sh, "content"));
                } catch (Throwable t) {   // 著色器在這支手機上跑不起來：收掉、之後改用系統水波紋
                    broken = true;
                    CrashLog.w("液態玻璃著色器（動畫中）", t);
                    an.cancel();
                }
            });
            st.va.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationCancel(android.animation.Animator an) { v.setRenderEffect(null); }
            });
            v.setTag(TAG, st);
            st.va.start();
        } catch (Throwable t) {
            broken = true;
            CrashLog.w("液態玻璃著色器", t);
            v.setRenderEffect(null);
        }
    }

    /** 手指在卡片上移動：透鏡跟著走。 */
    static void move(View v, float x, float y) {
        Object o = v.getTag(TAG);
        if (o instanceof St && ((St) o).rel < 0) { ((St) o).tx = x; ((St) o).ty = y; }
    }

    /** 手指放開：透鏡縮小、淡出。 */
    static void release(View v) {
        Object o = v.getTag(TAG);
        if (o instanceof St && ((St) o).rel < 0) ((St) o).rel = SystemClock.uptimeMillis();
    }

    /** 立刻收掉（手指開始捲動、被取消）。 */
    static void stop(View v) {
        Object o = v.getTag(TAG);
        if (o instanceof St && ((St) o).va != null) ((St) o).va.cancel();
        v.setTag(TAG, null);
        if (o != null) v.setRenderEffect(null);
    }
}
