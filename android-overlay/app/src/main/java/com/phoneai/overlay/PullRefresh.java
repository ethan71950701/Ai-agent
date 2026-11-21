package com.phoneai.overlay;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.core.widget.NestedScrollView;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.progressindicator.CircularProgressIndicator;

/**
 * 從清單最上面往下拉就重新整理（標準做法）：圓圈跟著手指滑出來、弧線依拉的距離畫出；拉過門檻放手就轉圈並重新讀取，沒拉夠就彈回去。
 * 放手用彈簧（有一點彈性，不是等速）。用法：Activity 的 dispatchTouchEvent 先交給 handle()。
 */
final class PullRefresh {
    interface OnRefresh { void run(); }

    static final int NOT_HANDLED = 0, CONSUMED = 1, JUST_STARTED = 2;

    private final Activity act;
    private final NestedScrollView sv;
    private final AppBarLayout bar;
    private final OnRefresh onRefresh;
    private final FrameLayout holder;
    private final CircularProgressIndicator spinner;
    private final float threshold, limit, holdAt, slop;
    private float startY, pulled;
    private boolean armed, active, loading;
    private SpringAnimation spring;

    PullRefresh(Activity a, ViewGroup coordinator, NestedScrollView sv, AppBarLayout bar, OnRefresh onRefresh) {
        this.act = a;
        this.sv = sv;
        this.bar = bar;
        this.onRefresh = onRefresh;
        threshold = Ui.dp(a, 80);
        limit = Ui.dp(a, 150);
        holdAt = Ui.dp(a, 66);   // 轉圈時清單停在這個位置：要讓圓圈整個在清單上面，不壓到第一張卡片
        slop = ViewConfiguration.get(a).getScaledTouchSlop();
        holder = new FrameLayout(a);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Ui.attr(a, com.google.android.material.R.attr.colorSurfaceContainerHighest));   // 和卡片（ContainerHigh）不同色，圓底才看得出來
        holder.setBackground(bg);
        holder.setElevation(Ui.dp(a, 8));
        holder.setAlpha(0f);
        spinner = new CircularProgressIndicator(a);
        spinner.setIndicatorSize(Ui.dp(a, 24));
        spinner.setTrackThickness(Ui.dp(a, 3));
        spinner.setIndeterminate(false);
        spinner.setMax(100);
        spinner.setTrackColor(android.graphics.Color.TRANSPARENT);   // 不畫底下那圈整圓的軌道，只留會長的弧線
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
        holder.addView(spinner, sp);
        int size = Ui.dp(a, 40);
        androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams lp = new androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(size, size);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        coordinator.addView(holder, lp);
        paint(0);
    }

    private boolean canPull() {
        return !loading && sv.getScrollY() == 0 && bar.getTop() >= -2;   // 清單在最上面、標題列也是展開的
    }

    /** 回傳 NOT_HANDLED（照常處理）、CONSUMED（這個事件被下拉吃掉了）、JUST_STARTED（剛開始下拉：呼叫端要先取消底下的觸控再吃掉）。 */
    int handle(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (spring != null && !loading) spring.cancel();
                armed = canPull();
                active = false;
                startY = ev.getRawY();
                return NOT_HANDLED;
            case MotionEvent.ACTION_MOVE: {
                if (!armed) return NOT_HANDLED;
                float dy = ev.getRawY() - startY;
                if (!active) {
                    if (dy < -slop) { armed = false; return NOT_HANDLED; }
                    if (dy > slop && canPull()) {
                        active = true;
                        startY = ev.getRawY() - slop;   // 從過了門檻的位置才開始算，不會一開始就跳一下
                        paint(0);
                        return JUST_STARTED;
                    }
                    return NOT_HANDLED;
                }
                paint(PullMath.resist(ev.getRawY() - startY, limit));
                return CONSUMED;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                armed = false;
                if (!active) return NOT_HANDLED;
                active = false;
                release();
                return CONSUMED;
            default:
                return active ? CONSUMED : NOT_HANDLED;
        }
    }

    private void release() {
        if (PullMath.triggers(pulled, threshold)) {
            loading = true;
            startLoadingAnim();   // 從目前的弧線與角度接著轉：弧線一長一短、轉速忽快忽慢；不切換成系統的不確定進度（它會從頭開始，弧線縮成一個點再長出來，看起來像閃一下）
            springTo(holdAt, null);
            onRefresh.run();
        } else {
            springTo(0, null);
        }
    }

    /** 重新讀取完了：圓圈縮回去。 */
    void done() {
        if (!loading) return;
        springTo(0, () -> { loading = false; stopLoadingAnim(); });
        loading = true;   // 彈回去的過程中不接受新的下拉
    }

    private android.animation.ValueAnimator arcAnim, spinAnim;

    private void startLoadingAnim() {
        stopLoadingAnim();
        final float rot0 = holder.getRotation();
        arcAnim = android.animation.ValueAnimator.ofFloat(0.75f, 0.22f);   // 從現在的四分之三圈縮短、再拉長，來回
        arcAnim.setDuration(750);
        arcAnim.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        arcAnim.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        arcAnim.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        arcAnim.addUpdateListener(an -> spinner.setProgressCompat(Math.round((float) an.getAnimatedValue() * 100), false));
        arcAnim.start();
        spinAnim = android.animation.ValueAnimator.ofFloat(rot0, rot0 + 720f);   // 轉兩圈剛好接回起點，重複時看不出接縫；加減速讓速度忽快忽慢
        spinAnim.setDuration(1600);
        spinAnim.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        spinAnim.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        spinAnim.addUpdateListener(an -> holder.setRotation((float) an.getAnimatedValue()));
        spinAnim.start();
    }

    private void stopLoadingAnim() {
        if (arcAnim != null) { arcAnim.cancel(); arcAnim = null; }
        if (spinAnim != null) { spinAnim.cancel(); spinAnim = null; }
        holder.setRotation(0);
        spinner.setProgressCompat(0, false);
    }

    private void springTo(float target, Runnable end) {
        if (spring != null) spring.cancel();
        FloatValueHolder h = new FloatValueHolder(pulled);
        spring = new SpringAnimation(h);
        spring.setSpring(new SpringForce(target).setStiffness(SpringForce.STIFFNESS_LOW).setDampingRatio(0.72f));   // 略欠阻尼：有一點彈性
        spring.setStartValue(pulled);
        spring.addUpdateListener((anim, value, velocity) -> paint(value));
        spring.addEndListener((anim, canceled, value, velocity) -> { if (!canceled && end != null) end.run(); });
        spring.start();
    }

    private void paint(float p) {
        pulled = p;
        sv.setTranslationY(p);
        
        int top = bar.getBottom();
        holder.setTranslationY(top + Math.max(-holder.getHeight() - 8, p - holder.getHeight() - Ui.dp(act, 6)));
        holder.setAlpha(Math.min(1f, p / Ui.dp(act, 36)));
        float scale = 0.6f + 0.4f * Math.min(1f, p / Ui.dp(act, 50));
        holder.setScaleX(scale);
        holder.setScaleY(scale);
        holder.setRotation(loading ? 0 : p * 0.9f);   // 拉到底也還會慢慢轉，看得出還在回應手指
        if (!loading) spinner.setProgressCompat(Math.round(PullMath.arcFraction(p, threshold) * 100), false);
    }
}
