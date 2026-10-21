package com.phoneai.overlay;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

/** 彈簧動畫工具（Material 3 Expressive 的做法：位移與大小有一點回彈，淡入淡出不回彈）。 */
final class Anim {
    private Anim() { }

    static final float STIFF = 420f, BOUNCY = 0.72f, SMOOTH = 1f;

    private static final java.util.WeakHashMap<View, java.util.HashMap<DynamicAnimation.ViewProperty, SpringAnimation>> RUNNING = new java.util.WeakHashMap<>();

    static void spring(View v, DynamicAnimation.ViewProperty prop, float to, float stiffness, float damping) {
        // 同一個 View 的同一個屬性只留一個彈簧；舊的沒取消會在新的結束後繼續跑，把值拉回舊目標（按鈕卡在縮小狀態就是這樣來的）
        java.util.HashMap<DynamicAnimation.ViewProperty, SpringAnimation> m = RUNNING.get(v);
        if (m == null) { m = new java.util.HashMap<>(); RUNNING.put(v, m); }
        SpringAnimation old = m.get(prop);
        if (old != null && old.isRunning()) old.cancel();
        SpringAnimation a = new SpringAnimation(v, prop);
        m.put(prop, a);
        a.setSpring(new SpringForce(to).setStiffness(stiffness).setDampingRatio(damping));
        a.start();
    }

    /** 取消這個 View 上還在跑的彈簧（手指要直接拖它時用）。 */
    /** 只取消某一個屬性的彈簧（拖動時只停位移，不影響放大）。 */
    static void cancel(View v, DynamicAnimation.ViewProperty prop) {
        java.util.HashMap<DynamicAnimation.ViewProperty, SpringAnimation> m = RUNNING.get(v);
        if (m == null) return;
        SpringAnimation a = m.get(prop);
        if (a != null && a.isRunning()) a.cancel();
    }

    static void cancel(View v) {
        java.util.HashMap<DynamicAnimation.ViewProperty, SpringAnimation> m = RUNNING.get(v);
        if (m == null) return;
        for (SpringAnimation a : m.values()) if (a.isRunning()) a.cancel();
    }

    /** 按下縮小、放開彈回；不攔截點擊。 */
    static void pressable(View v, float pressed) {
        v.setOnTouchListener((x, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    spring(x, DynamicAnimation.SCALE_X, pressed, 900f, 0.9f);
                    spring(x, DynamicAnimation.SCALE_Y, pressed, 900f, 0.9f);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    spring(x, DynamicAnimation.SCALE_X, 1f, 500f, 0.45f);
                    spring(x, DynamicAnimation.SCALE_Y, 1f, 500f, 0.45f);
                    break;
                default:
            }
            return false;
        });
    }

    /** 一群內容依序從下方彈入（換分頁、展開時用）。 */
    static void cascade(ViewGroup g, int maxItems, float fromX, float fromY, long step) {
        int n = Math.min(g.getChildCount(), maxItems);
        for (int i = 0; i < n; i++) {
            final View c = g.getChildAt(i);
            c.setAlpha(0f);
            c.setTranslationX(fromX);
            c.setTranslationY(fromY);
            c.postDelayed(() -> {
                spring(c, DynamicAnimation.ALPHA, 1f, 300f, SMOOTH);
                spring(c, DynamicAnimation.TRANSLATION_X, 0f, 380f, 0.78f);
                spring(c, DynamicAnimation.TRANSLATION_Y, 0f, 380f, 0.78f);
            }, i * step);
        }
    }

    /** 展開／收合：高度用彈簧長出來（展開有一點回彈、收合不回彈），裡面的列依序彈入。 */
    static void expand(final ViewGroup body, final boolean show, final float dp) {
        final ViewGroup.LayoutParams lp = body.getLayoutParams();
        if (show) {
            View parent = (View) body.getParent();
            int w = parent.getWidth() > 0 ? parent.getWidth() : body.getResources().getDisplayMetrics().widthPixels;
            body.setVisibility(View.VISIBLE);
            body.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            final int target = body.getMeasuredHeight();
            lp.height = 0;
            body.setLayoutParams(lp);
            cascade(body, 12, 0f, 14 * dp, 30);
            run(body, lp, 0f, target, 380f, 0.8f, false);
        } else {
            run(body, lp, body.getHeight(), 0, 700f, SMOOTH, true);
        }
    }

    private static void run(final View body, final ViewGroup.LayoutParams lp, float from, float to,
                            float stiffness, float damping, final boolean hideAtEnd) {
        SpringAnimation a = new SpringAnimation(new FloatValueHolder(from));
        a.setSpring(new SpringForce(to).setStiffness(stiffness).setDampingRatio(damping));
        a.setMinimumVisibleChange(1f);
        a.addUpdateListener((an, v, vel) -> {
            lp.height = Math.max(0, Math.round(v));
            body.setLayoutParams(lp);
        });
        a.addEndListener((an, canceled, v, vel) -> {
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            if (hideAtEnd) body.setVisibility(View.GONE);
            body.setLayoutParams(lp);
        });
        a.start();
    }
}
