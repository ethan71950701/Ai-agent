package com.phoneai.overlay;

import android.animation.ValueAnimator;
import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * 水波：手指按下去，一灘水從手指的位置擴散開來，蓋住整張卡片。水不只是把字扭曲而已，它看得到：
 *  - 水面微微染一層水色，上面有幾塊寬而柔的水面反光慢慢飄（由水面起伏的斜率算出來，不是細線），
 *  - 水的邊緣有一圈亮邊，波前那一圈折射最強（內容被推擠、邊緣有一點色散），
 *  - 蓋住的地方內容像隔著水微微放大，水面像池水一樣緩慢、不規則地流動。
 * 放開手指，水不是縮回去，而是「滲進卡片裡」：水面先靜下來，波前的環淡掉，然後水一塊一塊地被吸收——靠邊緣的先乾，一塊塊水漬縮小（邊緣有亮邊），手指按的地方最後才乾。
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

    private static final String SRC = ""
            + "uniform shader content;\n"
            + "uniform float2 center;\n"
            + "uniform float2 size;\n"
            + "uniform float radius;\n"
            + "uniform float amp;\n"                                    // 水的強度（淡入用）
            + "uniform float ring;\n"                                   // 波前那一圈的強度（放開時先淡掉）
            + "uniform float time;\n"                                   // 水面流動的時間（放開後會變慢）
            + "uniform float soak;\n"                                   // 滲進去的程度 0（全濕）～1.25（全乾）
            + "float surf(float2 p, float t) {\n"                       // 水面的高度（光滑的起伏）：用它的斜率算水面反光
            + "  float2 q = p / 110.0;\n"
            + "  q += 0.35 * sin(q.yx * 1.3 + t * 0.5);\n"
            + "  return sin(q.y * 1.7 + t * 1.1) + sin(q.x * 1.3 - t * 0.8 + 1.7) + 0.5 * sin((q.x + q.y) * 2.1 + t * 0.6);\n"
            + "}\n"
            + "half4 main(float2 fc) {\n"
            + "  float2 d = fc - center;\n"
            + "  float dist = length(d);\n"
            + "  float2 dir = d / max(dist, 1.0);\n"
            + "  float x = dist - radius;\n"                              // 離波前的距離（負的＝被水蓋住）
            + "  float env = exp(-(x * x) / 4800.0);\n"                   // 波前的包絡（寬約 55px）
            + "  float slope = cos(x / 14.0) * env;\n"                    // 波前的水面斜率：決定折射往哪邊推
            + "  float inFront = 1.0 - smoothstep(-20.0, 50.0, x);\n"     // 波前以內＝1
            + "  float2 q = fc / 110.0;\n"                                // 水面的自然流動：幾個慢慢飄的正弦疊起來
            + "  q += 0.35 * sin(q.yx * 1.3 + time * 0.5);\n"
            + "  float2 w = float2(sin(q.y * 1.7 + time * 1.1) + sin(q.x * 1.3 - time * 0.8 + 1.7),\n"
            + "                    cos(q.x * 1.5 - time * 0.9) + sin(q.y * 1.1 + time * 0.7 + 0.6));\n"
            // 滲進去：用一張固定的不規則圖（n）加上「離手指越遠越先乾」決定每個點什麼時候乾，所以水是一塊一塊縮小的，手指按的地方最後才乾
            + "  float n = 0.5 + 0.125 * (sin(fc.x / 55.0 + 1.3) + sin(fc.y / 47.0 - 0.7) + sin((fc.x + fc.y) / 38.0 + 2.1) + sin((fc.x - fc.y) / 63.0));\n"
            + "  float edgeK = smoothstep(0.0, max(radius, 1.0), dist);\n"
            + "  float th = 1.0 - mix(n, edgeK, 0.55);\n"
            + "  float mask = 1.0 - smoothstep(th - 0.12, th + 0.12, soak);\n"   // 1＝這裡還濕著
            + "  float wet = inFront * mask;\n"
            + "  float2 flow = w * 2.2 * amp * wet;\n"                    // 流動造成的位移（約 ±4px）
            + "  float off = 24.0 * ring * slope;\n"                      // 波前的折射位移（像素）
            + "  float2 base = center + d * (1.0 - 0.05 * amp * wet);\n"  // 水透鏡：濕的地方內容微微放大
            + "  float2 hi = size - float2(1.0);\n"
            + "  float2 uv = clamp(base - dir * off + flow, float2(0.0), hi);\n"
            + "  float2 spread = dir * (4.5 * ring * env) + flow * 0.25;\n"   // 色散：紅藍通道多偏一點
            + "  half4 cr = content.eval(clamp(uv + spread, float2(0.0), hi));\n"
            + "  half4 cg = content.eval(uv);\n"
            + "  half4 cb = content.eval(clamp(uv - spread, float2(0.0), hi));\n"
            + "  half4 c = half4(cr.r, cg.g, cb.b, cg.a);\n"
            // 水的樣子：水色、飄動的亮紋、水漬邊緣的亮邊、波前的反光
            // 水面反光：從水面起伏的斜率算出「這一小塊水面剛好把光反射到眼睛」的程度（Blinn 高光），所以是幾塊寬而柔的亮斑慢慢飄，不是細線
            + "  float hx = surf(fc + float2(10.0, 0.0), time) - surf(fc - float2(10.0, 0.0), time);\n"
            + "  float hy = surf(fc + float2(0.0, 10.0), time) - surf(fc - float2(0.0, 10.0), time);\n"
            + "  float3 nrm = normalize(float3(-hx * 0.4, -hy * 0.4, 1.0));\n"
            + "  float spec = pow(max(dot(nrm, normalize(float3(-0.215, -0.323, 0.917))), 0.0), 36.0);\n"
            + "  float rim = 4.0 * mask * (1.0 - mask) * inFront;\n"      // 水漬的邊緣（mask 在 0 與 1 之間）
            + "  float shine = max(slope, 0.0) * ring * 0.38;\n"
            + "  float3 add = (float3(0.50, 0.72, 0.95) * 0.11 + float3(spec * 0.14)) * (amp * wet) + float3(rim * 0.32 * amp + shine);\n"
            + "  c.rgb += half3(add) * c.a;\n"
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
                    st.sh.setFloatUniform("time", st.tAcc);
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
