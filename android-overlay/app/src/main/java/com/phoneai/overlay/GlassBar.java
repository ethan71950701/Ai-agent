package com.phoneai.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

import com.qmdeve.liquidglass.widget.LiquidGlassView;

/**
 * 以開源 AndroidLiquidGlassView 包裝的底欄玻璃元件。
 * 保留原本 MainActivity 需要的方法與參數，底層玻璃效果改為外部套件實作。
 */
final class GlassBar extends FrameLayout {
    static final float REST = 0.4f;
    static volatile String status = "還沒畫過";
    static volatile String lensInfo = "";
    static volatile String touchInfo = "";

    private final boolean lens;
    private final int bgColor;
    private final int tintColor;
    private final float radius;
    private final LiquidGlassView liquid;
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF rect = new RectF();

    private View source;
    private GlassBar parentBar;
    private View iconSource;
    private boolean plain;
    private float amount;
    private float blurFactor = 1f;
    private float glowAmount;
    private float pressGlow;
    private int glowColor = 0xFFFFFFFF;

    private long until;
    private boolean ticking;
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (!ticking) return;
            if (SystemClock.uptimeMillis() < until) {
                invalidate();
                postOnAnimation(this);
            } else {
                ticking = false;
            }
        }
    };

    GlassBar(Context c, int bgColor, int tintColor, int rimColor, float radiusPx, boolean lens) {
        super(c);
        this.lens = lens;
        this.bgColor = bgColor;
        this.tintColor = tintColor;
        this.radius = radiusPx;
        this.amount = lens ? REST : 1f;

        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);

        liquid = new LiquidGlassView(c);
        liquid.setDraggableEnabled(false);
        liquid.setElasticEnabled(false);
        liquid.setTouchEffectEnabled(false);
        addView(liquid, 0, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(Ui.dp(c, 1));
        edge.setColor((rimColor & 0x00FFFFFF) | 0x66FFFFFF);

        fill.setStyle(Paint.Style.FILL);

        if (!lens) {
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View v, Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
                }
            });
            setClipToOutline(true);
        }

        applyParams();
    }

    void setSource(View v) {
        source = v;
        bindSourceIfPossible();
    }

    void setParentBar(GlassBar b) {
        parentBar = b;
        applyParams();
    }

    void setGlowColor(int color) {
        glowColor = color;
        applyParams();
    }

    void setGlowAmount(float a) {
        glowAmount = clamp01(a);
        applyParams();
    }

    float getPressGlow() {
        return pressGlow;
    }

    void setPressGlow(float p) {
        pressGlow = clamp01(p);
        applyParams();
    }

    void setIconSource(View v) {
        iconSource = v;
    }

    void setPlain(boolean p) {
        plain = p;
        applyParams();
    }

    void setAmount(float a) {
        amount = clamp01(a);
        applyParams();
        kick(1200);
    }

    float getAmount() {
        return amount;
    }

    void setBlurFactor(float f) {
        blurFactor = clamp01(f);
        applyParams();
        kick(1200);
    }

    void kick(long ms) {
        until = Math.max(until, SystemClock.uptimeMillis() + Math.max(0, ms));
        if (!ticking) {
            ticking = true;
            postOnAnimation(ticker);
        }
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        bindSourceIfPossible();
        applyParams();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rect.set(0, 0, w, h);
        clip.reset();
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW);
        liquid.setCornerRadius(radius);
        applyParams();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (!plain && getWidth() > 0 && getHeight() > 0) {
            canvas.drawPath(clip, fill);
        }
        super.dispatchDraw(canvas);
        if (!plain && !lens) {
            canvas.drawPath(clip, edge);
        }
        status = "開源 LiquidGlassView";
    }

    private void bindSourceIfPossible() {
        ViewGroup vg = null;
        if (source instanceof ViewGroup) vg = (ViewGroup) source;
        else if (getParent() instanceof ViewGroup) vg = (ViewGroup) getParent();
        if (vg != null) liquid.bind(vg);
    }

    private void applyParams() {
        float active = lens ? clamp01((amount - REST) / (1f - REST)) : 1f;
        float glow = Math.max(glowAmount, pressGlow);
        float density = getResources().getDisplayMetrics().density;

        // 盡量映射現有 GlassPrefs 參數，不改設定頁對外參數。
        float refr = Math.max(0f, GlassPrefs.refr);
        float refractionHeightPx = Ui.dp(getContext(), 12 + 14f * refr + 8f * active);
        float refractionOffsetPx = Ui.dp(getContext(), 20 + 30f * refr + 10f * active);
        float blurPx = Math.max(0.01f, GlassPrefs.blur * density * (lens ? 0.35f : 1f) * blurFactor);
        float dispersion = lens ? clamp01(GlassPrefs.chroma * 0.5f * active) : 0f;

        liquid.setCornerRadius(radius);
        liquid.setRefractionHeight(refractionHeightPx);
        liquid.setRefractionOffset(refractionOffsetPx);
        liquid.setBlurRadius(blurPx);
        liquid.setDispersion(dispersion);

        int base = tintColor != 0 ? tintColor : bgColor;
        int mixed = mix(base, glowColor, 0.18f * glow);
        liquid.setTintColorRed(Color.red(mixed) / 255f);
        liquid.setTintColorGreen(Color.green(mixed) / 255f);
        liquid.setTintColorBlue(Color.blue(mixed) / 255f);

        float baseAlpha = plain ? 0f : (lens ? 0.13f + 0.20f * amount : 0.16f);
        float alpha = clamp01(baseAlpha + glow * 0.08f);
        liquid.setTintAlpha(alpha);

        int fillAlpha = Math.round((lens ? 14 : 18) * (plain ? 0f : 1f));
        fill.setColor((bgColor & 0x00FFFFFF) | (fillAlpha << 24));

        lensInfo = "強度 " + String.format(java.util.Locale.US, "%.2f", amount)
                + "、模糊倍率 " + String.format(java.util.Locale.US, "%.2f", blurFactor)
                + "、折射 " + String.format(java.util.Locale.US, "%.2f", refr)
                + (parentBar != null ? "、跟隨底欄" : "")
                + (iconSource != null ? "、有圖示來源" : "");

        if (source == null && getParent() == null) {
            status = "等待來源";
        }
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static int mix(int a, int b, float t) {
        t = clamp01(t);
        int r = Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t);
        int g = Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t);
        int bl = Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t);
        return Color.rgb(r, g, bl);
    }
}
