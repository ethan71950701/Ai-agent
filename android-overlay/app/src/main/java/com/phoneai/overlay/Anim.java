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

    private static final java.util.WeakHashMap<View, java.util.HashMap<DynamicAnimation.ViewProperty, SpringAnimation>> RUNNING = new java.util.WeakHashMap<>();
    static final float BOUNCY = 0.72f, SMOOTH = 1f;

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

    /**
     * 展開／收合：高度用「時間＋曲線」連續長出來（不是整塊淡入），下面的卡片跟著一格一格被推開；裡面的內容隨高度長出來而淡入。
     * 展開是先快後慢、收合是先慢後快。同時間只跑一個，連點會從目前的高度接著動，不會跳。
     */
    static void expandSmooth(final View body, final boolean show) {
        final ViewGroup.LayoutParams lp = body.getLayoutParams();
        Object old = body.getTag(R_EXPAND);
        if (old instanceof android.animation.ValueAnimator) ((android.animation.ValueAnimator) old).cancel();
        final float from, to;
        if (show) {
            View parent = (View) body.getParent();
            int w = parent.getWidth() > 0 ? parent.getWidth() : body.getResources().getDisplayMetrics().widthPixels;
            body.setVisibility(View.VISIBLE);
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            body.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            to = body.getMeasuredHeight();
            from = body.getVisibility() == View.VISIBLE && body.getHeight() > 0 && lp.height != ViewGroup.LayoutParams.WRAP_CONTENT ? body.getHeight() : 0f;
        } else {
            from = body.getHeight();
            to = 0f;
        }
        final float start = show ? (body.getHeight() > 0 && body.getHeight() < to ? body.getHeight() : 0f) : from;
        final float end = show ? to : 0f;
        android.animation.ValueAnimator va = android.animation.ValueAnimator.ofFloat(start, end);
        va.setDuration(show ? 380 : 260);
        va.setInterpolator(show ? new android.view.animation.PathInterpolator(0.05f, 0.7f, 0.1f, 1f) : new android.view.animation.PathInterpolator(0.3f, 0f, 0.8f, 0.15f));
        va.addUpdateListener(an -> {
            float h = (float) an.getAnimatedValue();
            lp.height = Math.max(0, Math.round(h));
            body.setLayoutParams(lp);
            body.setAlpha(end > 0f ? Math.min(1f, h / (end * 0.7f)) : Math.min(1f, h / Math.max(1f, start * 0.7f)));
        });
        va.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(android.animation.Animator an) { cancelled = true; }
            @Override public void onAnimationEnd(android.animation.Animator an) {
                if (cancelled) return;
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                body.setAlpha(1f);
                if (!show) body.setVisibility(View.GONE);
                body.setLayoutParams(lp);
            }
        });
        body.setTag(R_EXPAND, va);
        va.start();
    }

    private static final int R_EXPAND = 0x7f0a0e57;   // View.setTag 用的鍵（只要不跟系統的衝突）
}
