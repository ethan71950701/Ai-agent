package com.phoneai.overlay;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;

/**
 * 蓋在 Activity 上的整頁（PageBuilder）用的出場動畫：跟其他整頁（Ui.enterSpring／closeToTap）同一套——
 * 從被點的卡片的位置與大小展開到全螢幕（圓角跟著變），卡片的小圖淡出、內容晚一點淡入；返回時縮回那張卡片。
 * 展開途中按返回，從目前的進度接著縮回去（可打斷）；Android 14 以上的返回手勢會讓頁面跟著手指縮小。
 */
final class PageAnim {
    private static final PathInterpolator SMOOTH_IO = new PathInterpolator(0.4f, 0f, 0.2f, 1f);

    private final Activity a;
    private final FrameLayout host;   // 裝頁面內容＋蓋層；動畫變形的是這一層
    private final Veil veil;
    private final float[] rect;
    private final float corner;
    private ValueAnimator opening;
    private boolean closing;
    private float p = 0f;             // 0＝縮在卡片上、1＝全螢幕
    private float backP0 = 1f;
    private boolean backPrepared;

    PageAnim(Activity a, View page) {
        this.a = a;
        float[] r = null;
        Bitmap snap = null;
        Object[] tap = Ui.takeTap();
        if (tap != null) { r = (float[]) tap[0]; snap = (Bitmap) tap[1]; }
        if (r == null) {   // 不是點卡片開的：用畫面中間一張虛擬的卡片當起點（跟其他整頁一樣）
            android.util.DisplayMetrics dm = a.getResources().getDisplayMetrics();
            r = new float[]{dm.widthPixels * 0.2f, dm.heightPixels * 0.42f, dm.widthPixels * 0.6f, Ui.dp(a, 120)};
        }
        this.rect = r;
        this.corner = Ui.dp(a, 22);
        host = new FrameLayout(a);
        host.addView(page, new FrameLayout.LayoutParams(-1, -1));
        veil = new Veil(a, snap, host);
        host.addView(veil, new FrameLayout.LayoutParams(-1, -1));
    }

    /** 加進畫面並開始展開。 */
    void open() {
        a.addContentView(host, new ViewGroup.LayoutParams(-1, -1));
        host.setPivotX(0f);
        host.setPivotY(0f);
        host.setAlpha(0f);
        host.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                host.getViewTreeObserver().removeOnPreDrawListener(this);
                host.setOutlineProvider(new ViewOutlineProvider() {
                    @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), cornerNow(v)); }
                });
                host.setClipToOutline(true);
                host.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                apply(0f);
                ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
                va.setDuration(480);
                va.setInterpolator(SMOOTH_IO);
                va.addUpdateListener(an -> apply((float) an.getAnimatedValue()));
                va.addListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator an) {
                        if (opening != va) return;   // 被關閉動作接手了
                        opening = null;
                        apply(1f);
                        veil.setVisibility(View.INVISIBLE);
                        host.postDelayed(() -> { if (!closing) host.setLayerType(View.LAYER_TYPE_NONE, null); }, 250);
                    }
                });
                opening = va;
                Anim.boostFrames(host, 900, "整頁展開");
                host.post(() -> host.postOnAnimation(() -> { if (!closing && opening == va) va.start(); }));   // 先讓第一次繪製完成再開始跑時間
                return true;
            }
        });
    }

    private float cornerNow(View v) {
        float sr = screenCorner();
        float onScreen = sr + (corner - sr) * (1f - p);
        return onScreen / Math.max(0.05f, v.getScaleX());   // 圓角在縮放前的座標，除以縮放才是畫面上的大小
    }

    private float screenCorner() {
        try {
            android.view.WindowInsets wi = host.getRootWindowInsets();
            if (wi != null) {
                android.view.RoundedCorner c = wi.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_LEFT);
                if (c != null) return c.getRadius();
            }
        } catch (Throwable ignored) { }
        return 36f * a.getResources().getDisplayMetrics().density;
    }

    private void apply(float progress) {
        p = progress;
        float W = Math.max(1, host.getWidth()), H = Math.max(1, host.getHeight());
        float sx = rect[2] / W, sy = rect[3] / H, k = 1f - progress;
        int[] loc = new int[2];
        ((View) host.getParent()).getLocationOnScreen(loc);
        host.setScaleX(sx + (1f - sx) * progress);
        host.setScaleY(sy + (1f - sy) * progress);
        host.setTranslationX((rect[0] - loc[0]) * k);
        host.setTranslationY((rect[1] - loc[1]) * k);
        host.setAlpha(1f);
        float x = Math.max(0f, Math.min(1f, (progress - 0.15f) / 0.5f));
        veil.setAlpha(1f - x * x * (3f - 2f * x));   // 蓋層（卡片顏色）：剛開始只看到一個變大的卡片，內容晚一點才淡入
        float y = Math.max(0f, Math.min(1f, progress / 0.3f));
        veil.setSnapAlpha(1f - y * y * (3f - 2f * y));   // 卡片上的字在前 30% 淡出
        host.invalidateOutline();
    }

    boolean isClosing() { return closing; }

    /** 縮回卡片再移除；展開還沒跑完就從目前的進度接著縮。end 在移除之後執行。 */
    void close(final Runnable end) {
        if (closing) return;
        closing = true;
        float start = p;
        if (opening != null) { opening.removeAllListeners(); opening.cancel(); opening = null; }
        prepClose();
        ValueAnimator va = ValueAnimator.ofFloat(start, 0f);
        va.setDuration(Math.round(340 * (0.35f + 0.65f * start)));
        va.setInterpolator(SMOOTH_IO);
        va.addUpdateListener(an -> apply((float) an.getAnimatedValue()));
        va.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator an) {
                if (host.getParent() instanceof ViewGroup) ((ViewGroup) host.getParent()).removeView(host);
                if (end != null) end.run();
            }
        });
        va.start();
    }

    private void prepClose() {
        host.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        veil.setVisibility(View.VISIBLE);
        veil.setAlpha(0f);
        Anim.boostFrames(host, 700, "整頁返回");
    }

    // ---- 返回手勢（Android 14 以上）：頁面跟著手指往卡片縮，放手再接著縮回去或彈回原樣 ----
    void onBackStarted() {
        if (closing) return;
        backP0 = 1f;
        if (opening != null) { backP0 = p; opening.removeAllListeners(); opening.cancel(); opening = null; }
        prepClose();
        backPrepared = true;
        apply(backP0);
    }

    void onBackProgressed(float progress) {
        if (closing || !backPrepared) return;
        apply(backP0 * (1f - 0.6f * progress));   // 手勢拉滿大約縮到四成
    }

    void onBackCancelled() {
        if (closing || !backPrepared) return;
        backPrepared = false;
        ValueAnimator va = ValueAnimator.ofFloat(p, 1f);
        va.setDuration(220);
        va.setInterpolator(SMOOTH_IO);
        va.addUpdateListener(an -> apply((float) an.getAnimatedValue()));
        va.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator an) {
                apply(1f);
                veil.setVisibility(View.INVISIBLE);
                host.postDelayed(() -> { if (!closing) host.setLayerType(View.LAYER_TYPE_NONE, null); }, 250);
            }
        });
        va.start();
    }

    /** 蓋在頁面最上面的一層：卡片顏色＋被點卡片的小圖（小圖不跟著頁面變形）。 */
    private static final class Veil extends View {
        private final Bitmap snap;
        private final View scaleRef;
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private float snapAlpha = 1f;

        Veil(android.content.Context c, Bitmap snap, View scaleRef) {
            super(c);
            this.snap = snap;
            this.scaleRef = scaleRef;
        }

        void setSnapAlpha(float v) { snapAlpha = v; invalidate(); }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawColor(Ui.attr(getContext(), com.google.android.material.R.attr.colorSurfaceContainerHigh));
            if (snap == null || snapAlpha <= 0.01f) return;
            float sx = Math.max(0.01f, scaleRef.getScaleX()), sy = Math.max(0.01f, scaleRef.getScaleY());
            canvas.save();
            canvas.scale(1f / sx, 1f / sy);   // 抵銷頁面被壓扁／放大的比例，小圖才不會變形
            paint.setAlpha(Math.round(255 * snapAlpha));
            canvas.drawBitmap(snap, 0f, 0f, paint);
            canvas.restore();
        }
    }
}
