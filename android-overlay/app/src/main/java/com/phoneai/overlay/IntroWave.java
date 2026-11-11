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
            + "  float env = exp(-(x * x) / 9000.0);\n"                  // 波的寬度約 95px
            + "  float slope = cos(x / 20.0) * env;\n"
            + "  float off = 34.0 * amp * slope;\n"                      // 折射：內容被推擠
            + "  float2 hi = size - float2(1.0);\n"
            + "  float2 uv = clamp(fc - dir * off, float2(0.0), hi);\n"
            + "  float2 spread = dir * (8.0 * amp * env);\n"             // 波邊緣的彩色光邊（色散）
            + "  half4 cr = content.eval(clamp(uv + spread, float2(0.0), hi));\n"
            + "  half4 cg = content.eval(uv);\n"
            + "  half4 cb = content.eval(clamp(uv - spread, float2(0.0), hi));\n"
            + "  half4 c = half4(cr.r, cg.g, cb.b, cg.a);\n"
            + "  float shine = max(slope, 0.0) * amp * 0.30;\n"          // 波峰的白色反光
            + "  c.rgb += half3(shine) * c.a;\n"
            + "  return c;\n"
            + "}\n";

    static void play(final View v) {
        if (broken || v == null || v.getWidth() <= 0 || v.getHeight() <= 0) return;
        try {
            final RuntimeShader sh = new RuntimeShader(SRC);
            final float w = v.getWidth(), h = v.getHeight();
            sh.setFloatUniform("size", w, h);
            sh.setFloatUniform("center", w / 2f, -0.04f * h);
            ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(1000);
            va.setInterpolator(new LinearInterpolator());
            va.addUpdateListener(an -> {
                try {
                    float t = (float) an.getAnimatedValue();
                    float e = 1f - (1f - t) * (1f - t) * (1f - t);   // easeOutCubic，跟光環一樣
                    float amp = Math.min(1f, t / 0.06f) * (float) Math.pow(1f - t, 1.2);
                    sh.setFloatUniform("radius", e * h * 1.2f);
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
