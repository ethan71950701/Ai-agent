package com.phoneai.overlay;

import android.animation.ValueAnimator;
import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * 水波：手指按下去，像水面被點了一下，水從手指的位置擴散開來；手指不放，水就一直蓋著這一片，水面上有行進中的小波；
 * 放開，水收回手指的位置。水蓋住的範圍（波前以內）整片都有折射（內容被推擠、像隔著水看，邊緣有一點色散，波峰有水面反光），波前最強。
 * 用 AGSL 著色器（RuntimeShader）＋ View.setRenderEffect，直接扭曲卡片自己畫出來的內容；不用 RippleDrawable。
 * 著色器若在這支手機上編譯失敗，{@link #broken} 變成 true，呼叫端改用系統水波紋。
 */
final class WaterRipple {
    private WaterRipple() { }

    static volatile boolean broken;
    private static final int TAG = 0x7f0a0e5a;   // View.setTag 用的鍵：這個 View 目前跑著的水波（St）

    private static final long EXPAND_MS = 750;     // 擴散到蓋滿整張卡片
    private static final long MIN_HOLD_MS = 220;   // 快速點一下也至少讓水擴散這麼久才開始收
    private static final long RETRACT_MS = 480;    // 放開後收回去

    private static final String SRC = ""
            + "uniform shader content;\n"
            + "uniform float2 center;\n"
            + "uniform float2 size;\n"
            + "uniform float radius;\n"
            + "uniform float amp;\n"
            + "uniform float time;\n"
            + "half4 main(float2 fc) {\n"
            + "  float2 d = fc - center;\n"
            + "  float dist = length(d);\n"
            + "  float2 dir = d / max(dist, 1.0);\n"
            + "  float x = dist - radius;\n"                              // 離波前的距離（波前＝radius；負的＝被水蓋住）
            + "  float env = exp(-(x * x) / 4800.0);\n"                   // 波前的包絡（寬約 55px）
            + "  float slope = cos(x / 14.0) * env;\n"                    // 波前的水面斜率：決定折射往哪邊推
            + "  float inside = 1.0 - smoothstep(-20.0, 50.0, x);\n"      // 被水蓋住的範圍（波前以內）＝1
            + "  float fall = exp(-dist / 900.0);\n"                      // 離手指越遠，水面的小波越小
            + "  float slope2 = cos(dist / 30.0 - time * 8.0) * inside * fall;\n"   // 水面上往外行進的小波
            + "  float off = 24.0 * amp * slope + 9.0 * amp * slope2;\n"  // 折射的位移（像素）
            + "  float2 base = center + d * (1.0 - 0.05 * amp * inside);\n"   // 水透鏡：被水蓋住的地方內容微微放大
            + "  float2 hi = size - float2(1.0);\n"
            + "  float2 uv = clamp(base - dir * off, float2(0.0), hi);\n"
            + "  float2 spread = dir * (4.5 * amp * env + 2.0 * amp * abs(slope2));\n"   // 色散：紅藍通道多偏一點
            + "  half4 cr = content.eval(clamp(uv + spread, float2(0.0), hi));\n"
            + "  half4 cg = content.eval(uv);\n"
            + "  half4 cb = content.eval(clamp(uv - spread, float2(0.0), hi));\n"
            + "  half4 c = half4(cr.r, cg.g, cb.b, cg.a);\n"
            + "  float shine = max(slope, 0.0) * amp * 0.38 + max(slope2, 0.0) * amp * 0.10;\n"   // 水面反光
            + "  c.rgb += half3(shine) * c.a;\n"
            + "  return c;\n"
            + "}\n";

    private static final class St {
        RuntimeShader sh;
        float w, h, maxR;
        long t0 = SystemClock.uptimeMillis();
        long rel = -1;          // 手指放開的時間（-1＝還按著）
        ValueAnimator va;
    }

    /** 擴散曲線：先快後慢。 */
    private static float expandAt(long ms) {
        float t = Math.max(0f, Math.min(1f, ms / (float) EXPAND_MS));
        return 1f - (float) Math.pow(1f - t, 2.4);
    }

    /** 在 (x, y)（這個 View 自己的座標）放一滴水；上一滴還在就先收掉。 */
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
            st.va.setRepeatCount(ValueAnimator.INFINITE);   // 按著不放時一直重畫（水面的小波在動）；放開收完才停
            st.va.setInterpolator(new LinearInterpolator());
            st.va.addUpdateListener(an -> {
                try {
                    if (!v.isAttachedToWindow()) { an.cancel(); return; }   // 點下去已經換頁了
                    long now = SystemClock.uptimeMillis();
                    float p = expandAt(now - st.t0);
                    if (st.rel >= 0) {
                        long start = Math.max(st.rel, st.t0 + MIN_HOLD_MS);
                        if (now >= start) {
                            float q = Math.min(1f, (now - start) / (float) RETRACT_MS);
                            float s = q * q * (3f - 2f * q);                    // 先慢後快再慢的收回
                            p = expandAt(start - st.t0) * (1f - s);
                            if (q >= 1f) { an.cancel(); return; }
                        }
                    }
                    st.sh.setFloatUniform("radius", p * st.maxR);
                    st.sh.setFloatUniform("amp", Math.min(1f, p / 0.10f) * 0.9f);   // 剛開始與收到尾巴時淡入淡出
                    st.sh.setFloatUniform("time", (now - st.t0) / 1000f);
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

    /** 手指放開：水收回手指的位置。 */
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
