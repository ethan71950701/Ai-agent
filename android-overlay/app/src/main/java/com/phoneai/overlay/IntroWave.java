package com.phoneai.overlay;

import android.animation.ValueAnimator;
import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * 助理面板開場（長按電源鍵叫出時）：一道透明的玻璃波從螢幕最上方中間往下擴散，經過的地方把「目前的畫面」（面板打開時先截的那張圖）折射、推擠一下，
 * 邊緣帶一點彩色光邊、波峰有白色反光；沒有任何彩色光暈。時間與位置跟 AuroraView 的開場光環一致（1 秒、easeOutCubic、半徑到螢幕高度 1.2 倍）。
 * 用 AGSL 著色器（RuntimeShader）＋ View.setRenderEffect 套在畫面截圖的那一層上；跑不起來就什麼都不做（照舊只有光環）。
 */
final class IntroWave {
    private IntroWave() { }

    private static volatile boolean broken;

    private static final String SRC = ""
            + "uniform shader content;\n"
            + "uniform float2 size;\n"
            + "uniform float2 center;\n"
            + "uniform float radius;\n"
            + "uniform float amp;\n"
            + "half4 main(float2 fc) {\n"
            + "  float2 d = fc - center;\n"
            + "  float dist = length(d);\n"
            + "  float2 dir = d / max(dist, 1.0);\n"
            + "  float x = dist - radius;\n"
            // 漣漪：波前後面拖著幾圈越來越弱的波（像石頭丟進水裡），波前前面很快就平了
            + "  float w = x > 0.0 ? 55.0 : 110.0;\n"
            + "  float env = exp(-(x * x) / (2.0 * w * w));\n"
            + "  float slope = cos(x / 24.0) * env;\n"                   // 一圈約 150px
            + "  float off = 26.0 * amp * slope;\n"                      // 折射：內容被推擠、形變
            + "  float2 hi = size - float2(1.0);\n"
            + "  float2 uv = clamp(fc - dir * off, float2(0.0), hi);\n"
            + "  float2 spread = dir * (3.0 * amp * abs(slope));\n"             // 一點點色散（水的彩邊很淡）
            + "  half4 cr = content.eval(clamp(uv + spread, float2(0.0), hi));\n"
            + "  half4 cg = content.eval(uv);\n"
            + "  half4 cb = content.eval(clamp(uv - spread, float2(0.0), hi));\n"
            + "  half4 c = half4(cr.r, cg.g, cb.b, cg.a);\n"
            // 水的立體感：波峰亮、波谷暗（光照在起伏的水面上），再加一點波峰的白色反光
            + "  float shade = slope * amp * 0.10 + max(slope, 0.0) * amp * 0.16;\n"
            + "  c.rgb = max(c.rgb + half3(shade) * c.a, half3(0.0));\n"
            + "  return c;\n"
            + "}\n";

    static void play(final View v) { play(v, 0); }

    /** 剛加進畫面的那一層可能還沒量好大小：每一幀再試一次，最多 20 次。 */
    private static void play(final View v, final int tries) {
        if (broken || v == null) return;
        if (v.getWidth() <= 0 || v.getHeight() <= 0) {
            if (tries < 20) v.postOnAnimation(() -> play(v, tries + 1));
            else LifeLog.add(v.getContext(), "開場玻璃波：畫面一直沒有大小，略過");
            return;
        }
        try {
            final RuntimeShader sh = new RuntimeShader(SRC);
            final float w = v.getWidth(), h = v.getHeight();
            sh.setFloatUniform("size", w, h);
            sh.setFloatUniform("center", w / 2f, 0.80f * h);   // 像一滴水滴在面板出現的地方，一圈圈往上散開（整個圓看得到才有水的感覺）
            ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(2000);
            va.setInterpolator(new LinearInterpolator());
            va.addUpdateListener(an -> {
                try {
                    float t = (float) an.getAnimatedValue();
                    float e = 1f - (1f - t) * (1f - t);   // 先快後慢，像水波越走越慢
                    float amp = Math.min(1f, t / 0.05f) * (float) Math.pow(1f - t, 1.4);   // 越散越淡
                    sh.setFloatUniform("radius", e * h * 0.95f);
                    sh.setFloatUniform("amp", amp);
                    v.setRenderEffect(RenderEffect.createRuntimeShaderEffect(sh, "content"));
                } catch (Throwable t) {
                    broken = true;
                    CrashLog.w("開場玻璃波著色器（動畫中）", t);
                    an.cancel();
                }
            });
            va.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator an) { v.setRenderEffect(null); }
                @Override public void onAnimationCancel(android.animation.Animator an) { v.setRenderEffect(null); }
            });
            va.start();
        } catch (Throwable t) {
            broken = true;
            CrashLog.w("開場玻璃波著色器", t);
            v.setRenderEffect(null);
        }
    }
}
