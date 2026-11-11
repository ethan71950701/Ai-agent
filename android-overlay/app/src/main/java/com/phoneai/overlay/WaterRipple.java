package com.phoneai.overlay;

import android.animation.ValueAnimator;
import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * 水波：手指按下去，像水面被點了一下，水從手指的位置擴散開來（波前最強，內容被推擠、邊緣有一點色散、波峰有水面反光）；
 * 手指不放，水就蓋著這一片，水面像池水一樣緩慢、不規則地流動（不是一圈一圈往外的波）；放開，波前的環先淡掉，水再平順地收回手指的位置。
 * 用 AGSL 著色器（RuntimeShader）＋ View.setRenderEffect，直接扭曲卡片自己畫出來的內容；不用 RippleDrawable。
 * 著色器若在這支手機上編譯失敗，{@link #broken} 變成 true，呼叫端改用系統水波紋。
 */
final class WaterRipple {
    private WaterRipple() { }

    static volatile boolean broken;
    private static final int TAG = 0x7f0a0e5a;   // View.setTag 用的鍵：這個 View 目前跑著的水波（St）

    private static final long EXPAND_MS = 750;     // 擴散到蓋滿整張卡片
    private static final long MIN_HOLD_MS = 220;   // 快速點一下也至少讓水擴散這麼久才開始收
    private static final long RETRACT_MS = 560;    // 放開後收回去

    private static final String SRC = ""
            + "uniform shader content;\n"
            + "uniform float2 center;\n"
            + "uniform float2 size;\n"
            + "uniform float radius;\n"
            + "uniform float amp;\n"                                    // 水蓋住的範圍（波前以內）的強度
            + "uniform float ring;\n"                                   // 波前那一圈的強度（放開收回時先淡掉，免得看起來像一圈波往回縮）
            + "uniform float time;\n"
            + "half4 main(float2 fc) {\n"
            + "  float2 d = fc - center;\n"
            + "  float dist = length(d);\n"
            + "  float2 dir = d / max(dist, 1.0);\n"
            + "  float x = dist - radius;\n"                              // 離波前的距離（波前＝radius；負的＝被水蓋住）
            + "  float env = exp(-(x * x) / 4800.0);\n"                   // 波前的包絡（寬約 55px）
            + "  float slope = cos(x / 14.0) * env;\n"                    // 波前的水面斜率：決定折射往哪邊推
            + "  float inside = 1.0 - smoothstep(-20.0, 50.0, x);\n"      // 被水蓋住的範圍（波前以內）＝1
            + "  float2 q = fc / 110.0;\n"                                // 水面的自然流動：幾個慢慢飄的正弦疊起來，沒有方向、不是同心圓
            + "  q += 0.35 * sin(q.yx * 1.3 + time * 0.5);\n"
            + "  float2 w = float2(sin(q.y * 1.7 + time * 1.1) + sin(q.x * 1.3 - time * 0.8 + 1.7),\n"
            + "                    cos(q.x * 1.5 - time * 0.9) + sin(q.y * 1.1 + time * 0.7 + 0.6));\n"
            + "  float2 flow = w * 2.6 * amp * inside;\n"                 // 流動造成的位移（約 ±5px）
            + "  float off = 24.0 * ring * slope;\n"                      // 波前的折射位移（像素）
            + "  float2 base = center + d * (1.0 - 0.05 * amp * inside);\n"   // 水透鏡：被水蓋住的地方內容微微放大
            + "  float2 hi = size - float2(1.0);\n"
            + "  float2 uv = clamp(base - dir * off + flow, float2(0.0), hi);\n"
            + "  float2 spread = dir * (4.5 * ring * env) + flow * 0.25;\n"   // 色散：紅藍通道多偏一點
            + "  half4 cr = content.eval(clamp(uv + spread, float2(0.0), hi));\n"
            + "  half4 cg = content.eval(uv);\n"
            + "  half4 cb = content.eval(clamp(uv - spread, float2(0.0), hi));\n"
            + "  half4 c = half4(cr.r, cg.g, cb.b, cg.a);\n"
            + "  float shine = max(slope, 0.0) * ring * 0.38 + max(w.x + w.y, 0.0) * 0.012 * amp * inside;\n"   // 水面反光
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
                    float ringK = 1f;   // 波前那一圈的強度倍率
                    if (st.rel >= 0) {
                        long start = Math.max(st.rel, st.t0 + MIN_HOLD_MS);
                        if (now >= start) {
                            float q = Math.min(1f, (now - start) / (float) RETRACT_MS);
                            float s = q < 0.5f ? 4f * q * q * q : 1f - (float) Math.pow(-2f * q + 2f, 3) / 2f;   // 先慢、中間快、最後慢地收
                            p = expandAt(start - st.t0) * (1f - s);
                            ringK = Math.max(0f, 1f - q / 0.3f);   // 開頭 30% 先把波前的環淡掉，之後只剩水面平順地縮回去
                            if (q >= 1f) { an.cancel(); return; }
                        }
                    }
                    float a = Math.min(1f, p / 0.10f) * 0.9f;   // 剛開始與收到尾巴時淡入淡出
                    st.sh.setFloatUniform("radius", p * st.maxR);
                    st.sh.setFloatUniform("amp", a);
                    st.sh.setFloatUniform("ring", a * ringK);
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
