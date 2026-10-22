package com.phoneai.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.widget.FrameLayout;

/** 把 overlay 這個子 View 只畫在 region（那顆玻璃按鈕）覆蓋的範圍內：用來讓玻璃下面的圖示與文字換顏色，其他部分不變。 */
final class LensClip extends FrameLayout {
    private View overlay, region;
    private final Path path = new Path();
    private final RectF r = new RectF();

    LensClip(Context c) {
        super(c);
        setClipChildren(false);
    }

    private float lx, ly, lsx, lsy;
    private int lw, lh;
    private final android.view.ViewTreeObserver.OnPreDrawListener watch = () -> {
        // 硬體繪製時，單純改 translation／scale 不會重錄父層的繪圖指令，clip 範圍就會停在舊位置；所以每一格檢查玻璃按鈕有沒有動，動了就要求重畫
        if (region != null && (region.getTranslationX() != lx || region.getTranslationY() != ly || region.getScaleX() != lsx
                || region.getScaleY() != lsy || region.getWidth() != lw || region.getHeight() != lh)) {
            lx = region.getTranslationX();
            ly = region.getTranslationY();
            lsx = region.getScaleX();
            lsy = region.getScaleY();
            lw = region.getWidth();
            lh = region.getHeight();
            invalidate();
        }
        return true;
    };

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnPreDrawListener(watch);
    }

    @Override
    protected void onDetachedFromWindow() {
        getViewTreeObserver().removeOnPreDrawListener(watch);
        super.onDetachedFromWindow();
    }

    void set(View overlay, View region) {
        this.overlay = overlay;
        this.region = region;
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        // 主色圖示只在透鏡範圍內出現（GlassBar 把它畫進透鏡裡）；底欄其他地方維持暗色圖示，所以透鏡經過時圖示才變色
        if (child == overlay) return false;
        return super.drawChild(canvas, child, drawingTime);
    }
}
