package com.phoneai.overlay;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * 水波紋：手指按下的位置像水面被點了一下，一圈圈波紋向外擴散，波經過的地方會折射（卡片自己的內容被推擠、拉扯，邊緣有一點色散），
 * 波峰有一點水面反光，越擴散越淡。用 AGSL 著色器（RuntimeShader）＋ View.setRenderEffect，直接扭曲卡片自己畫出來的內容；不用水波紋的 RippleDrawable。
 * 著色器若在這支手機上編譯失敗（不該發生），{@link #broken} 變成 true，呼叫端改用系統水波紋。
 */
final class WaterRipple {
    private WaterRipple() { }

    static volatile boolean broken;
    private static final int TAG = 0x7f0a0e5a;   // View.setTag 用的鍵：這個 View 目前跑著的動畫

    private static final String SRC = ""
            + "uniform shader content;\n"
            + "uniform float2 center;\n"
            + "uniform float2 size;\n"
            + "uniform float radius;\n"
            + "uniform float amp;\n"
            + "half4 main(float2 fc) {\n"
            + "  float2 d = fc - center;\n"
            + "  float dist = length(d);\n"
            + "  float2 dir = d / max(dist, 1.0);\n"
            + "  float x = dist - radius;\n"                          // 離波前的距離（波前＝radius）
            + "  float env = exp(-(x * x) / 2400.0);\n"               // 波的包絡：只有波前附近有波（寬約 40px）
            + "  float phase = x / 11.0;\n"                           // 波長約 70px，包絡內大約兩圈
            + "  float slope = cos(phase) * env;\n"                   // 水面的斜率：決定折射往哪邊推
            + "  float off = 10.0 * amp * slope;\n"                   // 折射的位移（像素）
            + "  float2 hi = size - float2(1.0);\n"
            + "  float2 uv = clamp(fc - dir * off, float2(0.0), hi);\n"
            + "  float2 spread = dir * (1.8 * amp * env);\n"          // 色散：紅藍通道多偏一點
            + "  half4 cr = content.eval(clamp(uv + spread, float2(0.0), hi));\n"
            + "  half4 cg = content.eval(uv);\n"
            + "  half4 cb = content.eval(clamp(uv - spread, float2(0.0), hi));\n"
            + "  half4 c = half4(cr.r, cg.g, cb.b, cg.a);\n"
            + "  float shine = max(slope, 0.0) * amp * 0.18;\n"       // 波峰的水面反光
            + "  c.rgb += half3(shine) * c.a;\n"
            + "  return c;\n"
            + "}\n";

    /** 在 (x, y)（這個 View 自己的座標）開始一圈水波；上一圈還沒跑完就先收掉。 */
    static void start(final View v, float x, float y) {
        if (broken || v.getWidth() <= 0 || v.getHeight() <= 0) return;
        stop(v);
        try {
            final RuntimeShader sh = new RuntimeShader(SRC);
            final float w = v.getWidth(), h = v.getHeight();
            sh.setFloatUniform("center", x, y);
            sh.setFloatUniform("size", w, h);
            final float maxR = (float) Math.hypot(Math.max(x, w - x), Math.max(y, h - y)) + 70f * v.getResources().getDisplayMetrics().density;
            final ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(900);
            va.setInterpolator(new LinearInterpolator());
            va.addUpdateListener(an -> {
              try {
                float t = (float) an.getAnimatedValue();
                float radius = maxR * (1f - (float) Math.pow(1f - t, 2.4));   // 先快後慢地擴散出去
                float ramp = Math.min(1f, t / 0.07f);                        // 剛按下去先淡入一下（捲動時被取消的瞬間才不會閃）
                float amp = ramp * (float) Math.pow(1f - t, 1.3);            // 越擴散越淡
                sh.setFloatUniform("radius", radius);
                sh.setFloatUniform("amp", amp);
                v.setRenderEffect(RenderEffect.createRuntimeShaderEffect(sh, "content"));
              } catch (Throwable t) {   // 著色器在這支手機上跑不起來：收掉、之後改用系統水波紋
                broken = true;
                CrashLog.w("水波紋著色器（動畫中）", t);
                an.cancel();
              }
            });
            va.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator an) { v.setRenderEffect(null); }
                @Override public void onAnimationCancel(Animator an) { v.setRenderEffect(null); }
            });
            v.setTag(TAG, va);
            va.start();
        } catch (Throwable t) {
            broken = true;   // 著色器編譯失敗：之後改用系統水波紋
            CrashLog.w("水波紋著色器", t);
            v.setRenderEffect(null);
        }
    }

    /** 立刻收掉（手指開始捲動、被取消）。 */
    static void stop(View v) {
        Object old = v.getTag(TAG);
        if (old instanceof ValueAnimator) ((ValueAnimator) old).cancel();
        v.setTag(TAG, null);
    }
}
