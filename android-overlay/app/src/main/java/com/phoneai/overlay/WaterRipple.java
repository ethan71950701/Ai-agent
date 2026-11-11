package com.phoneai.overlay;

import android.animation.ValueAnimator;
import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * 水波按壓：手指按下去，一圈乾淨的波從手指擴散出去，經過的地方內容被折射一下、帶一點彩色邊，波過了就恢復清楚；
 * 按住時只剩手指底下一團很淡的光（內容不模糊、不染色、不抖）；放開，那團光與水漬邊的亮線一塊塊「滲進」卡片裡消失，手指的地方最後消失。
 * 用 AGSL 著色器（RuntimeShader）＋ View.setRenderEffect，直接處理卡片自己畫出來的內容；不用 RippleDrawable。
 * 著色器若在這支手機上編譯失敗，{@link #broken} 變成 true，呼叫端改用系統水波紋。
 */
final class WaterRipple {
    private WaterRipple() { }

    static volatile boolean broken;
    private static final int TAG = 0x7f0a0e5a;   // View.setTag 用的鍵：這個 View 目前跑著的水波（St）

    private static final long EXPAND_MS = 750;     // 擴散到蓋滿整張卡片
    private static final long MIN_HOLD_MS = 220;   // 快速點一下也至少讓水擴散這麼久才開始滲進去
    private static final long SOAK_MS = 950;       // 放開後水滲進去、乾掉

    // 原則（使用者覺得「整個霧霧的不好看」之後重想）：
    //  1. 按住時內容保持清楚：不模糊、不染色、不抖。整張卡片持續變樣子就會像髒了、起霧。
    //  2. 「水」的感覺只靠一圈乾淨的波前：從手指擴散一次，經過的地方折射＋彩色邊，過了就恢復清楚。
    //  3. 用「光」不用「霧」：手指底下一團很淡的光（只加亮、不蓋顏色），按住時就只剩這個。
    //  4. 放開時保留使用者喜歡的「滲進去」：那團光與水漬邊的亮線一塊塊被吸收，手指的地方最後消失。
    private static final String SRC = ""
            + "uniform shader content;\n"
            + "uniform float2 center;\n"
            + "uniform float2 size;\n"
            + "uniform float radius;\n"                                 // 波前的位置（px）
            + "uniform float amp;\n"                                    // 整體強度（淡入）
            + "uniform float ring;\n"                                   // 波前那一圈的強度（放開時先淡掉）
            + "uniform float soak;\n"                                   // 滲進去的程度 0（全濕）～1.25（全乾）
            + "half4 main(float2 fc) {\n"
            + "  float2 d = fc - center;\n"
            + "  float dist = length(d);\n"
            + "  float2 dir = d / max(dist, 1.0);\n"
            + "  float x = dist - radius;\n"                              // 離波前的距離（負的＝波已經經過）
            + "  float env = exp(-(x * x) / 2600.0);\n"                   // 只有波前附近（寬約 40px）
            + "  float slope = cos(x / 13.0) * env;\n"
            + "  float inFront = 1.0 - smoothstep(-20.0, 50.0, x);\n"
            // 滲進去的遮罩：固定的不規則圖＋離手指越遠越先乾
            + "  float n = 0.5 + 0.125 * (sin(fc.x / 55.0 + 1.3) + sin(fc.y / 47.0 - 0.7) + sin((fc.x + fc.y) / 38.0 + 2.1) + sin((fc.x - fc.y) / 63.0));\n"
            + "  float edgeK = smoothstep(0.0, max(radius, 1.0), dist);\n"
            + "  float th = 1.0 - mix(n, edgeK, 0.55);\n"
            + "  float mask = 1.0 - smoothstep(th - 0.12, th + 0.12, soak);\n"
            + "  float wet = inFront * mask;\n"
            // 只有波前會折射：內容被推一下＋彩色邊；波過了就是原本清楚的內容
            + "  float off = 16.0 * ring * slope;\n"
            + "  float2 hi = size - float2(1.0);\n"
            + "  float2 uv = clamp(fc - dir * off, float2(0.0), hi);\n"
            + "  float2 spread = dir * (6.0 * ring * env);\n"
            + "  half4 cr = content.eval(clamp(uv + spread, float2(0.0), hi));\n"
            + "  half4 cg = content.eval(uv);\n"
            + "  half4 cb = content.eval(clamp(uv - spread, float2(0.0), hi));\n"
            + "  half4 c = half4(cr.r, cg.g, cb.b, cg.a);\n"
            // 光：手指底下一團很淡的光（只加亮）、波峰反光、放開時水漬邊的亮線
            + "  float sigma = 0.35 * max(radius, 1.0) + 40.0;\n"
            + "  float glow = 0.07 * exp(-dot(d, d) / (2.0 * sigma * sigma)) * amp * wet;\n"
            + "  float shine = max(slope, 0.0) * ring * 0.28;\n"
            + "  float rim = 4.0 * mask * (1.0 - mask) * inFront * 0.20 * amp;\n"
            + "  c.rgb += half3(glow + shine + rim) * c.a;\n"
            + "  return c;\n"
            + "}\n";

    private static final class St {
        RuntimeShader sh;
        float w, h, maxR;
        long t0 = SystemClock.uptimeMillis();
        long last = t0;
        float tAcc;             // 水面流動的累積時間（放開後走得比較慢）
        long rel = -1;          // 手指放開的時間（-1＝還按著）
        ValueAnimator va;
    }

    /** 擴散曲線：先快後慢。 */
    private static float expandAt(long ms) {
        float t = Math.max(0f, Math.min(1f, ms / (float) EXPAND_MS));
        return 1f - (float) Math.pow(1f - t, 2.4);
    }

    /** 在 (x, y)（這個 View 自己的座標）放一灘水；上一灘還在就先收掉。 */
    static void start(final View v, float x, float y) {
        if (broken || v.getWidth() <= 0 || v.getHeight() <= 0) return;
        stop(v);
        try {
            final St st = new St();
            st.sh = new RuntimeShader(SRC);
            st.w = v.getWidth();
            st.h = v.getHeight();
            st.sh.setFloatUniform("center", x, y);
            st.sh.setFloatUniform("size", st.w, st.h);
            st.maxR = (float) Math.hypot(Math.max(x, st.w - x), Math.max(y, st.h - y)) + 70f * v.getResources().getDisplayMetrics().density;
            st.va = ValueAnimator.ofFloat(0f, 1f);
            st.va.setDuration(1000);
            st.va.setRepeatCount(ValueAnimator.INFINITE);   // 按著不放時一直重畫（水面在流動）；滲完才停
            st.va.setInterpolator(new LinearInterpolator());
            st.va.addUpdateListener(an -> {
                try {
                    if (!v.isAttachedToWindow()) { an.cancel(); return; }   // 點下去已經換頁了
                    long now = SystemClock.uptimeMillis();
                    float p = expandAt(now - st.t0);
                    float ringK = 1f;       // 波前那一圈的強度倍率
                    float soak = 0f;        // 滲進去的程度
                    float rate = 1f;        // 水面流動的速度倍率
                    if (st.rel >= 0) {
                        long start = Math.max(st.rel, st.t0 + MIN_HOLD_MS);
                        if (now >= start) {
                            float q = Math.min(1f, (now - start) / (float) SOAK_MS);
                            float s = q * q * (3f - 2f * q);
                            p = expandAt(start - st.t0) * (1f - 0.12f * s);   // 水的範圍幾乎不縮，只是慢慢被吸收
                            ringK = Math.max(0f, 1f - q / 0.25f);             // 開頭先把波前的環淡掉
                            soak = 1.25f * s;
                            rate = 1f - 0.85f * s;                            // 水面漸漸靜下來
                            if (q >= 1f) { an.cancel(); return; }
                        }
                    }
                    st.tAcc += (now - st.last) / 1000f * rate;
                    st.last = now;
                    float a = Math.min(1f, p / 0.10f) * 0.9f;   // 剛開始淡入
                    st.sh.setFloatUniform("radius", p * st.maxR);
                    st.sh.setFloatUniform("amp", a);
                    st.sh.setFloatUniform("ring", a * ringK);
                    st.sh.setFloatUniform("soak", soak);
                    v.setRenderEffect(RenderEffect.createRuntimeShaderEffect(st.sh, "content"));
                } catch (Throwable t) {   // 著色器在這支手機上跑不起來：收掉、之後改用系統水波紋
                    broken = true;
                    CrashLog.w("水波紋著色器（動畫中）", t);
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
            CrashLog.w("水波紋著色器", t);
            v.setRenderEffect(null);
        }
    }

    /** 手指放開：水滲進卡片裡。 */
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
