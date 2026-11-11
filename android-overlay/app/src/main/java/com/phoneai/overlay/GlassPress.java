package com.phoneai.overlay;

import android.animation.ValueAnimator;
import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * 液態玻璃按壓：手指按下去，整張卡片變成一塊液態玻璃（跟底欄同一種質感），不是一顆圓球：
 *  - 卡片的整圈邊緣（跟著卡片自己的圓角形狀）變成玻璃邊：內容被折射、擠壓，邊緣有一點色散，邊上有一圈亮線（光從左上來，靠近手指的地方更亮），
 *  - 手指底下玻璃微微鼓起，把內容放大一點（柔和的一塊，沒有硬邊），鼓起處左上有一小塊柔和的高光，
 *  - 手指在卡片上滑動，鼓起處與亮線的重心會帶一點延遲跟著走（像液體），
 *  - 放開，玻璃感淡出，鼓起處消下去。
 * 用 AGSL 著色器（RuntimeShader）＋ View.setRenderEffect，直接處理卡片自己畫出來的內容；不用 RippleDrawable。
 * 著色器若在這支手機上編譯失敗，{@link #broken} 變成 true，呼叫端改用系統水波紋。
 */
final class GlassPress {
    private GlassPress() { }

    static volatile boolean broken;
    private static final int TAG = 0x7f0a0e5a;   // View.setTag 用的鍵：這個 View 目前的玻璃狀態（St）

    private static final long GROW_MS = 260;       // 玻璃感長出來
    private static final long MIN_HOLD_MS = 160;   // 快速點一下也至少顯示這麼久才開始淡出
    private static final long SHRINK_MS = 380;     // 放開後淡出

    private static final String SRC = ""
            + "uniform shader content;\n"
            + "uniform float2 center;\n"                                // 手指（鼓起處）的位置
            + "uniform float2 size;\n"
            + "uniform float4 radii;\n"                                 // 卡片四個角的圓角：左上、右上、右下、左下（px）
            + "uniform float edgeW;\n"                                  // 玻璃邊的寬度（px）
            + "uniform float sig;\n"                                    // 鼓起處的大小（px）
            + "uniform float amp;\n"                                    // 強度 0～1（淡入淡出）
            + "half4 main(float2 fc) {\n"
            + "  half4 base = content.eval(fc);\n"
            + "  if (amp <= 0.001) return base;\n"
            // 卡片的圓角矩形：sd＜0 在裡面，depth＝離邊緣多深
            + "  float2 p = fc - size * 0.5;\n"
            + "  float rad = (p.x > 0.0) ? ((p.y > 0.0) ? radii.z : radii.y) : ((p.y > 0.0) ? radii.w : radii.x);\n"
            + "  float2 q = abs(p) - (size * 0.5 - float2(rad));\n"
            + "  float sd = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - rad;\n"
            + "  if (sd > 0.0) return base;\n"
            + "  float depth = -sd;\n"
            + "  float2 sg = float2(p.x < 0.0 ? -1.0 : 1.0, p.y < 0.0 ? -1.0 : 1.0);\n"
            + "  float2 qm = max(q, 0.0);\n"
            + "  float2 nrm = (length(qm) > 0.0) ? normalize(qm) * sg : ((q.x > q.y) ? float2(sg.x, 0.0) : float2(0.0, sg.y));\n"   // 朝外的法線
            // 玻璃邊的折射：靠邊的內容被擠壓（取樣點往卡片裡面拉）
            + "  float inband = 1.0 - smoothstep(0.0, edgeW, depth);\n"
            + "  float bend = amp * edgeW * 0.7 * inband * inband;\n"
            + "  float2 s = fc - nrm * bend;\n"
            // 手指底下鼓起：柔和地把內容放大
            + "  float2 d = fc - center;\n"
            + "  float g = exp(-dot(d, d) / (2.0 * sig * sig));\n"
            + "  s = center + (s - center) * (1.0 - 0.12 * amp * g);\n"
            + "  float2 hi = size - float2(1.0);\n"
            + "  float2 spread = nrm * (inband * 4.0 * amp);\n"        // 色散：紅藍通道多偏一點
            + "  half4 cr = content.eval(clamp(s + spread, float2(0.0), hi));\n"
            + "  half4 cg = content.eval(clamp(s, float2(0.0), hi));\n"
            + "  half4 cb = content.eval(clamp(s - spread, float2(0.0), hi));\n"
            + "  half4 c = half4(cr.r, cg.g, cb.b, cg.a);\n"
            // 光澤：邊上一圈亮線（光從左上，靠近手指更亮）＋鼓起處左上的柔和高光
            + "  float rimW = edgeW * 0.22;\n"
            + "  float rimLine = exp(-(depth * depth) / (rimW * rimW));\n"
            + "  float ld = dot(nrm, float2(-0.6, -0.8));\n"
            + "  float lit = 0.35 + 0.65 * max(ld, 0.0) + 0.25 * max(-ld, 0.0);\n"
            + "  float nearK = 0.55 + 0.45 * exp(-dot(d, d) / (2.0 * sig * sig * 4.0));\n"
            + "  float2 sp = fc - (center + sig * float2(-0.30, -0.36));\n"
            + "  float spot = exp(-dot(sp, sp) / (0.9 * sig * sig));\n"
            + "  float add = (rimLine * lit * nearK * 0.55 + spot * 0.13 + inband * 0.03) * amp;\n"
            + "  c.rgb += half3(add) * c.a;\n"
            + "  return c;\n"
            + "}\n";

    private static final class St {
        RuntimeShader sh;
        float w, h, baseR, edgeW;
        float tx, ty, cx, cy;   // 手指（目標）與透鏡目前的位置（帶一點延遲跟著手指）
        long t0 = SystemClock.uptimeMillis();
        long rel = -1;          // 手指放開的時間（-1＝還按著）
        ValueAnimator va;
    }

    /** 卡片四個角的圓角（左上、右上、右下、左下，px）；不是 MaterialCardView 就當作沒有圓角。 */
    private static float[] cornerRadii(View v) {
        float[] r = new float[4];
        try {
            if (v instanceof com.google.android.material.card.MaterialCardView) {
                com.google.android.material.shape.ShapeAppearanceModel m = ((com.google.android.material.card.MaterialCardView) v).getShapeAppearanceModel();
                android.graphics.RectF b = new android.graphics.RectF(0, 0, v.getWidth(), v.getHeight());
                r[0] = m.getTopLeftCornerSize().getCornerSize(b);
                r[1] = m.getTopRightCornerSize().getCornerSize(b);
                r[2] = m.getBottomRightCornerSize().getCornerSize(b);
                r[3] = m.getBottomLeftCornerSize().getCornerSize(b);
            }
        } catch (Throwable ignored) { }
        return r;
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
            st.edgeW = Math.min(16f * dens, 0.45f * Math.min(st.w, st.h));
            float[] rr = cornerRadii(v);
            st.tx = st.cx = x;
            st.ty = st.cy = y;
            st.sh.setFloatUniform("size", st.w, st.h);
            st.sh.setFloatUniform("radii", rr[0], rr[1], rr[2], rr[3]);
            st.sh.setFloatUniform("edgeW", st.edgeW);
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
                            scale *= 1f - 0.5f * s;    // 鼓起處消下去
                            a *= 1f - s;               // 淡出
                            if (q >= 1f) { an.cancel(); return; }
                        }
                    }
                    st.cx += (st.tx - st.cx) * 0.3f;   // 帶一點延遲跟著手指（像液體）
                    st.cy += (st.ty - st.cy) * 0.3f;
                    st.sh.setFloatUniform("center", st.cx, st.cy);
                    st.sh.setFloatUniform("sig", st.baseR * scale * 0.6f);
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
