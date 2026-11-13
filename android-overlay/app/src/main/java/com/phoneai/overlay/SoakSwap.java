package com.phoneai.overlay;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * 回答卡換新的：舊卡像水一塊一塊「滲進去」（跟按鈕放開時的水漬同一套遮罩，從外圍先乾），
 * 同一個遮罩的另一半把新卡浮現出來；水漬邊緣有一圈亮線與一點折射。兩張卡大小不同也沒關係，各自照自己的範圍被遮。
 * 著色器在這支手機上跑不起來時 {@link #broken} 變 true，呼叫端改用直接換。
 */
final class SoakSwap {
    private SoakSwap() { }

    static volatile boolean broken;
    private static final long MS = 900;

    private static final String SRC = ""
            + "uniform shader content;\n"
            + "uniform float2 center;\n"
            + "uniform float R;\n"
            + "uniform float sc;\n"
            + "uniform float soak;\n"
            + "uniform float inv;\n"
            + "half4 main(float2 fc) {\n"
            + "  float2 d = fc - center;\n"
            + "  float dist = length(d);\n"
            + "  float n = 0.5 + 0.125 * (sin(fc.x / (55.0 * sc) + 1.3) + sin(fc.y / (47.0 * sc) - 0.7) + sin((fc.x + fc.y) / (38.0 * sc) + 2.1) + sin((fc.x - fc.y) / (63.0 * sc)));\n"
            + "  float edgeK = smoothstep(0.0, R, dist);\n"
            + "  float th = 1.0 - mix(n, edgeK, 0.55);\n"
            + "  float m = 1.0 - smoothstep(th - 0.12, th + 0.12, soak);\n"   // 1＝還是舊的、0＝已經換成新的
            + "  float line = 4.0 * m * (1.0 - m);\n"
            + "  float2 dir = d / max(dist, 1.0);\n"
            + "  half4 c = content.eval(fc + dir * (6.0 * sc * line));\n"
            + "  float k = mix(m, 1.0 - m, inv);\n"
            + "  c *= half(k);\n"
            + "  c.rgb += half3(0.22 * line) * c.a;\n"
            + "  return c;\n"
            + "}\n";

    private static RuntimeShader make(View v, float cx, float cy, float R, float sc, float inv) {
        RuntimeShader sh = new RuntimeShader(SRC);
        sh.setFloatUniform("center", cx, cy);
        sh.setFloatUniform("R", R);
        sh.setFloatUniform("sc", sc);
        sh.setFloatUniform("inv", inv);
        sh.setFloatUniform("soak", -0.15f);
        v.setRenderEffect(RenderEffect.createRuntimeShaderEffect(sh, "content"));
        return sh;
    }

    /** neu 已經加進畫面、在 old 上面（同位置）；放完 old 由 done 移除。 */
    static void run(final View old, final View neu, final Runnable done) {
        if (broken || old.getWidth() <= 0) { finish(old, neu, done); return; }
        try {
            final float d = old.getResources().getDisplayMetrics().density;
            final float cx = old.getWidth() / 2f;
            final float cy = Math.min(old.getHeight(), Math.max(neu.getHeight(), (int) (60 * d))) / 2f;
            final float R = Math.max(old.getWidth(), 1) * 0.6f + 40 * d;
            final int h0 = old.getHeight(), h1 = neu.getHeight();   // 兩張卡各自的高度：外框一起從舊的高度長（縮）到新的高度，不要一下子跳
            setH(old, h0);
            setH(neu, h0);
            final RuntimeShader a = make(old, cx, cy, R, d / 2f, 0f);
            final RuntimeShader b = make(neu, cx, cy, R, d / 2f, 1f);
            ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(MS);
            va.setInterpolator(new LinearInterpolator());
            va.addUpdateListener(an -> {
                try {
                    float q = (float) an.getAnimatedValue();
                    float s = q * q * (3f - 2f * q);
                    float soak = -0.15f + 1.4f * s;
                    float g = Math.min(1f, q / 0.55f);
                    g = 1f - (1f - g) * (1f - g) * (1f - g);   // 先快後慢
                    int hh = Math.round(h0 + (h1 - h0) * g);
                    setH(old, hh);
                    setH(neu, hh);
                    a.setFloatUniform("soak", soak);
                    b.setFloatUniform("soak", soak);
                    old.setRenderEffect(RenderEffect.createRuntimeShaderEffect(a, "content"));
                    neu.setRenderEffect(RenderEffect.createRuntimeShaderEffect(b, "content"));
                } catch (Throwable t) {
                    broken = true;
                    CrashLog.w("滲入轉場著色器（動畫中）", t);
                    an.cancel();
                }
            });
            va.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator an) { finish(old, neu, done); }
                @Override public void onAnimationCancel(Animator an) { }
            });
            va.start();
        } catch (Throwable t) {
            broken = true;
            CrashLog.w("滲入轉場著色器", t);
            finish(old, neu, done);
        }
    }

    private static void setH(View v, int h) {
        android.view.ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp != null && lp.height != h) { lp.height = h; v.setLayoutParams(lp); }
    }

    private static void finish(View old, View neu, Runnable done) {
        setH(neu, -2);   // 恢復依內容自動算高度
        try { old.setRenderEffect(null); neu.setRenderEffect(null); } catch (Throwable ignored) { }
        done.run();
    }
}
