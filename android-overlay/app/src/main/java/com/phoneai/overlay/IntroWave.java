package com.phoneai.overlay;

import android.animation.ValueAnimator;
import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * 助理面板開場的「水波」，照使用者給的小米澎湃 2 影片逐格看出來的做法：
 *  - 只有一道柔和的「放大鏡帶」，從面板（輸入列）出現的地方往外掃過整個畫面（面板打開時先截的那張圖）；
 *    帶子經過的地方內容被微微放大、推擠一下，過了就恢復；沒有一圈圈的波、沒有光環、沒有明暗、沒有彩色。
 *  - 掃過整個畫面大約 1.1 秒，一開始快、後面慢（0.65 秒使用者覺得太快）。
 *  - 面板本身同時從模糊中浮現（AssistantSheet.enter 裡做），像玻璃凝結出來。
 * 用 AGSL 著色器（RuntimeShader）＋ View.setRenderEffect 套在畫面截圖那一層上；跑不起來就什麼都不做。
 */
final class IntroWave {
    private IntroWave() { }

    private static volatile boolean broken;

    private static final String SRC = ""
            + "uniform shader content;\n"
            + "uniform float2 size;\n"
            + "uniform float2 center;\n"
            + "uniform float radius;\n"                                 // 帶子中心線離起點多遠（px）
            + "uniform float bw;\n"                                     // 帶子的寬度（px）
            + "uniform float amp;\n"
            + "half4 main(float2 fc) {\n"
            + "  float2 d = fc - center;\n"
            + "  float dist = length(d);\n"
            + "  float2 dir = d / max(dist, 1.0);\n"
            + "  float x = dist - radius;\n"
            + "  float g = exp(-(x * x) / (2.0 * bw * bw));\n"
            + "  float shift = 0.30 * x * g * amp;\n"                  // 帶子裡的取樣點往中心線收＝放大
            + "  return content.eval(clamp(fc - dir * shift, float2(0.0), size - float2(1.0)));\n"
            + "}\n";

    /** 從 (cx, cy) 往外掃一次；delay 毫秒後開始。 */
    static void play(final View v, final float cx, final float cy, final long delay) { play(v, cx, cy, delay, 0); }

    /** 剛加進畫面的那一層可能還沒量好大小：每一幀再試一次，最多 20 次。 */
    private static void play(final View v, final float cx, final float cy, final long delay, final int tries) {
        if (broken || v == null) return;
        if (v.getWidth() <= 0 || v.getHeight() <= 0) {
            if (tries < 20) v.postOnAnimation(() -> play(v, cx, cy, delay, tries + 1));
            else LifeLog.add(v.getContext(), "開場玻璃波：畫面一直沒有大小，略過");
            return;
        }
        try {
            final RuntimeShader sh = new RuntimeShader(SRC);
            final float w = v.getWidth(), h = v.getHeight();
            final float band = Math.max(60f * v.getResources().getDisplayMetrics().density, 0.05f * h);
            final float maxR = (float) Math.hypot(Math.max(cx, w - cx), Math.max(cy, h - cy)) + band * 2.5f;
            sh.setFloatUniform("size", w, h);
            sh.setFloatUniform("center", cx, cy);
            sh.setFloatUniform("bw", band);
            // 不用 setStartDelay：等待的那段時間也先把效果（強度 0）套上去，讓 GPU 先把著色器準備好，真正開始掃的時候才不會卡一下
            final long dur = 1100;
            ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(delay + dur);
            va.setInterpolator(new LinearInterpolator());
            va.addUpdateListener(an -> {
                try {
                    float t = Math.max(0f, (an.getAnimatedFraction() * (delay + dur) - delay) / dur);
                    float e = 1f - (1f - t) * (1f - t);                  // 先快後慢
                    float amp = Math.min(1f, t / 0.08f) * (1f - t * t);  // 一路都看得到，最後才淡掉
                    sh.setFloatUniform("radius", e * maxR);
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
