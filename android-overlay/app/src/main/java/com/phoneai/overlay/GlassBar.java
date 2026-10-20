package com.phoneai.overlay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

/**
 * 液態玻璃（仿 iOS 26／KernelSU）。兩種用法：
 * 1) 底欄（lens=false）：把後面的畫面縮小抓成圖，模糊，邊緣折射、色散，疊淡色與亮邊。
 * 2) 底欄上選中的那顆（lens=true）：拖動時放大成透鏡——不模糊，中間放大、邊緣折射強烈，能看到底下的圖示與文字被放大；沒拖的時候只是半透明的淡色圓底。
 * 抓背景時：自己整個不畫；別的玻璃元件在抓背景時只畫子內容（圖示、文字），不畫玻璃層，所以透鏡看得到底欄的圖示。
 * 沒有變動的時候不重畫（kick() 之後約一秒多內每個畫格更新）。需要 API 33（RuntimeShader／RenderEffect），本 App minSdk 34。
 */
final class GlassBar extends FrameLayout {
    private static final String COMMON =
            "uniform shader content;\n"
            + "uniform float2 size;\n"
            + "uniform float radius;\n"
            + "uniform float depth;\n"
            + "uniform float amount;\n"
            + "uniform float skew;\n"
            + "uniform float refr;\n"
            + "uniform float chroma;\n"
            + "half4 main(float2 p) {\n"
            + "  float2 c = size * 0.5;\n"
            + "  float2 q = abs(p - c) - (c - float2(radius));\n"
            + "  float sd = length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - radius;\n"   // 到圓角矩形邊的距離，內側為負
            + "  float t = clamp(-sd / depth, 0.0, 1.0);\n"
            + "  float bend = (1.0 - t) * (1.0 - t);\n"                                          // 越靠邊折射越強
            + "  float2 dir = normalize(p - c + float2(0.001));\n"
            + "  float2 z = p;\n"
            + "  z.x += skew * (p.y - c.y);\n"                                                     // 移動時畫面被拖得歪斜（液體的拖曳感）
            + "  float2 sp = z - dir * bend * depth * 0.75 * refr * amount;\n"
            + "  half4 col = content.eval(sp);\n"
            + "  col.b = content.eval(z - dir * bend * depth * 1.0 * refr * chroma * amount).b;\n"              // 紅藍取樣點不同＝邊緣色散
            + "  col.r = content.eval(z - dir * bend * depth * 0.5 * refr * chroma * amount).r;\n"
            + "  half l = dot(col.rgb, half3(0.299, 0.587, 0.114));\n"
            + "  col.rgb = (half3(l) + (col.rgb - half3(l)) * half(1.0 + 0.6 * amount)) + half3(bend * 0.03 * amount) * col.a;\n"   // 只有有內容的地方才加亮（透明處不加，免得出現方塊）   // 比背景亮一點，邊緣更亮
            + "  return col;\n"
            + "}\n";
    private static final String AGSL_BAR = COMMON;
    private static final String AGSL_LENS = COMMON;

    /** 有任何玻璃元件正在抓背景：其他玻璃元件只畫子內容，免得互相遞迴。 */
    private static boolean anyCapture;
    /** 玻璃按鈕沒被按住時的效果強度（輕微折射，讓它平常也看得出是一塊玻璃）。 */
    static final float REST = 0.4f;
    /** 透鏡裡的圖示是否經過折射著色器。實驗中：false 時不經過著色器，用來判斷半透明方塊的來源。 */
    // 圖示是否經過折射著色器：改由設定頁的開關控制（GlassPrefs.iconRefr）
    static volatile String status = "還沒畫過";
    static volatile String lensInfo = "";   // 診斷用：透鏡強度、模糊、歪斜

    private final boolean lens;
    private View source, iconSource;
    // 透鏡裡的圖示：也要經過折射著色器（之前只是直接畫，所以沒有形變、彩色光暈）
    private final RenderNode iconNode = new RenderNode("lensIcons");
    private RuntimeShader iconShader;
    private GlassBar parentBar;   // 透鏡所在的底欄：透鏡下面直接用底欄已經模糊好的背景，不會變透明
    private Bitmap bmp;
    private Canvas bc;
    private final RenderNode node = new RenderNode("glass");
    private final RenderNode glowNode = new RenderNode("lensGlow");   // 按住時的發光外圈（像 KernelSU 的底欄）
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bandPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    static volatile String touchInfo = "";   // 診斷用：最近一次觸控的壓力與接觸面積
    private final RenderNode underNode = new RenderNode("lensUnder");   // 透鏡底下：直接用底欄抓好的背景圖再模糊，不經過底欄的折射著色器
    private final RuntimeShader shader;
    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF rect = new RectF();
    private boolean capturing, failed, plain;
    private long until;
    private final int bgColor, tintColor;
    private final float radius;
    private float blurFactor = 1f;   // 模糊程度 0..1：按住底欄時降到 0（清楚看到底下），放開再回來
    private float amount;   // 0 = 平的淡色圓底、1 = 完整透鏡效果（只有 lens 用；底欄固定 1）

    GlassBar(Context c, int bgColor, int tintColor, int rimColor, float radiusPx, boolean lens) {
        super(c);
        this.lens = lens;
        this.bgColor = bgColor;
        this.tintColor = tintColor;
        this.radius = radiusPx;
        this.amount = lens ? REST : 1f;
        shader = new RuntimeShader(lens ? AGSL_LENS : AGSL_BAR);
        if (lens) iconShader = new RuntimeShader(AGSL_LENS);
        setWillNotDraw(false);
        setClipChildren(false);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(Ui.dp(c, 2));
        inner.setStyle(Paint.Style.STROKE);
        inner.setStrokeWidth(Ui.dp(c, 3));
        inner.setColor(0x14FFFFFF);
        if (!lens) {
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View v, Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
                }
            });
        }
    }

    void setSource(View v) { source = v; }

    void setParentBar(GlassBar b) { parentBar = b; }

    /** 發光的顏色（主色）。 */
    private int glowColor = 0xFF8AB4F8;

    void setGlowColor(int color) {
        glowColor = color;
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(Ui.dp(getContext(), 4));
        glowPaint.setColor(color);
    }

    /** 底欄整條發光的強度（按住時由 MainActivity 設定，0 = 不發光）。 */
    private float glowAmount;

    void setGlowAmount(float a) {
        glowAmount = a;
        invalidate();
    }

    /** 按住時的發光：主色的外圈模糊後往外散開，強度跟著按住的力度。 */
    private void drawGlow(Canvas canvas) {
        drawHalo(canvas, Math.min(1f, amount * 0.9f));
    }

    /** 按壓力度造成的發光（0..1）；由 MainActivity 的觸控事件設定。 */
    private float pressGlow;

    void setPressGlow(float p) {
        pressGlow = Math.max(0f, Math.min(1f, p));
        invalidate();
    }

    /** 底欄橫向中間的光帶：白色核心、外圍是主題色。畫進背景圖（bc），所以會跟著底欄一起模糊。 */
    private void paintBandInto(Canvas bc, int sw, int sh) {
        float a = Math.max(glowAmount, pressGlow);
        if (a < 0.02f) return;
        bandPaint.setAlpha(Math.round(255 * Math.min(1f, a)));
        bc.save();
        bc.scale(sw / (float) getWidth(), sh / (float) getHeight());   // 底欄座標 → 背景圖座標
        bc.drawRect(0, 0, getWidth(), getHeight(), bandPaint);
        bc.restore();
    }

    private void drawHalo(Canvas canvas, float alpha) {
        int w = getWidth(), h = getHeight();
        float pad = Ui.dp(getContext(), 14);
        float blur = Ui.dp(getContext(), 7);
        glowNode.setPosition(0, 0, Math.round(w + 2 * pad), Math.round(h + 2 * pad));
        glowNode.setRenderEffect(RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.DECAL));
        Canvas gc = glowNode.beginRecording(Math.round(w + 2 * pad), Math.round(h + 2 * pad));
        gc.translate(pad, pad);
        gc.drawRoundRect(new RectF(0, 0, w, h), radius, radius, glowPaint);
        glowNode.endRecording();
        glowNode.setAlpha(alpha);
        canvas.save();
        canvas.translate(-pad, -pad);
        canvas.drawRenderNode(glowNode);
        canvas.restore();
    }

    /** 透鏡專用：底欄裡「只有圖示與文字」的那一層（另外抓、不模糊，只放大折射）。 */
    void setIconSource(View v) { iconSource = v; }

    /** 要放大的那排圖示（和底欄同一個座標系，與 fl 的左上角對齊）。 */

    /** true = 不畫任何底（沒有模糊、底色），只畫裡面的內容。 */
    void setPlain(boolean p) { plain = p; }

    /** 透鏡的強度 0..1。 */
    void setAmount(float a) {
        amount = a;
        kick(1200);
    }

    float getAmount() { return amount; }

    void setBlurFactor(float f) {
        blurFactor = Math.max(0f, Math.min(1f, f));
        kick(1200);
    }

    /** 接下來 ms 毫秒內每個畫格都重新抓背景（捲動、動畫、點擊時呼叫）。 */
    void kick(long ms) {
        until = Math.max(until, SystemClock.uptimeMillis() + ms);
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        rect.set(0, 0, w, h);
        clip.reset();
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW);
        node.setPosition(0, 0, w, h);
        iconNode.setPosition(0, 0, w, h);
        tint.setShader(new LinearGradient(0, 0, 0, h, lens ? new int[]{(tintColor & 0xFFFFFF) | 0x38000000, (tintColor & 0xFFFFFF) | 0x1C000000}
                        : new int[]{(tintColor & 0xFFFFFF) | 0x06000000, (tintColor & 0xFFFFFF) | 0x01000000},
                null, Shader.TileMode.CLAMP));
        bandPaint.setShader(new LinearGradient(0, 0, 0, h, new int[]{0x00FFFFFF, (glowColor & 0xFFFFFF) | 0x66000000, 0xD0FFFFFF,
                (glowColor & 0xFFFFFF) | 0x66000000, 0x00FFFFFF}, new float[]{0f, 0.3f, 0.5f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        rim.setShader(new LinearGradient(0, 0, w, h, new int[]{0x90FFFFFF, 0x18FFFFFF, 0x50FFFFFF},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        try {
            configure(shader, 0f);
            node.setRenderEffect(currentEffect());
        } catch (Throwable e) {
            failed = true;
            status = "失敗，已退回半透明：" + e;
        }
    }

    private void configure(RuntimeShader sh, float skewValue) {
        sh.setFloatUniform("size", getWidth(), getHeight());
        sh.setFloatUniform("radius", radius);
        sh.setFloatUniform("depth", Ui.dp(getContext(), lens ? 12 : 18));
        sh.setFloatUniform("amount", amount);
        sh.setFloatUniform("skew", skewValue);
        sh.setFloatUniform("refr", GlassPrefs.refr);
        sh.setFloatUniform("chroma", GlassPrefs.chroma);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (capturing) return;                        // 我正在抓背景：不要把自己畫進去
        if (anyCapture) return;                       // 玻璃按鈕在抓背景（只要底欄後面的頁面）：底欄與按鈕都不畫
        if (plain) {                                  // 完全透明的底欄：什麼都不畫，只畫子內容
            super.dispatchDraw(canvas);
            return;
        }
        int w = getWidth(), h = getHeight();
        boolean glass = !failed && source != null && w > 0 && h > 0 && canvas.isHardwareAccelerated() && true;
        if (lens) updateSkew();
        if (glass) {
            try {
                drawGlass(canvas, w, h);
                status = "正常（玻璃效果執行中）";
            } catch (Throwable e) {   // 這支手機的玻璃效果不能用：退回半透明底色，不要讓 App 閃退
                failed = true;
                capturing = false;
                anyCapture = false;
                status = "失敗，已退回半透明：" + e;
                drawFlat(canvas);
            }
        } else {
            drawFlat(canvas);
        }
        super.dispatchDraw(canvas);
        if (SystemClock.uptimeMillis() < until) postInvalidateOnAnimation();
    }

    private float lastTx, skewNow;
    private long lastT;

    /** 玻璃按鈕的橫向速度 → 歪斜量（平滑過、停下來會回到 0）。 */
    private void updateSkew() {
        long now = SystemClock.uptimeMillis();
        float tx = getTranslationX();
        if (lastT != 0 && now > lastT) {
            float vDpMs = (tx - lastTx) / getResources().getDisplayMetrics().density / (now - lastT);   // dp/ms
            float target = Math.max(-0.35f, Math.min(0.35f, -vDpMs * 0.35f)) * GlassPrefs.skew;
            skewNow = skewNow * 0.6f + target * 0.4f;
            if (!GlassPrefs.skewOn) skewNow = 0f;
        }
        lastTx = tx;
        lastT = now;
        if (Math.abs(skewNow) > 0.003f) kick(200);
    }

    private void drawFlat(Canvas canvas) {
        if (!lens) {
            Paint solid = new Paint(Paint.ANTI_ALIAS_FLAG);
            solid.setColor((bgColor & 0xFFFFFF) | 0xE6000000);
            canvas.drawPath(clip, solid);
        }
        canvas.drawPath(clip, tint);
    }

    /** 把 src 畫進 target（縮小 scale 倍），座標對齊到本 View 的左上角（補回 pivot 縮放造成的位移）。 */
    private void captureInto(Bitmap target, Canvas c, View src, int scale, int erase) {
        target.eraseColor(erase);
        int[] a = new int[2], b = new int[2];
        getLocationInWindow(a);
        src.getLocationInWindow(b);
        float x0 = a[0] - b[0] + getPivotX() * (getScaleX() - 1f);
        float y0 = a[1] - b[1] + getPivotY() * (getScaleY() - 1f);
        capturing = true;
        anyCapture = true;
        try {
            c.save();
            c.scale(1f / scale, 1f / scale);
            c.translate(-x0, -y0);
            src.draw(c);
            c.restore();
        } finally {
            capturing = false;
            anyCapture = false;
        }
    }

    private void drawGlass(Canvas canvas, int w, int h) {
        if (lens && amount > 0.05f) drawGlow(canvas);
        if (lens && parentBar != null && GlassPrefs.lensBg) {
            // 透鏡：底下用底欄的模糊背景（不再另外抓頁面，所以不會透明），圖示照常放大與折射
            canvas.save();
            canvas.clipPath(clip);
            Paint base = new Paint(Paint.ANTI_ALIAS_FLAG);
            base.setColor(bgColor);
            canvas.drawPath(clip, base);        // 先鋪一層不透明底色，把底下原大小的圖示蓋住
            canvas.restore();
            drawUnderLens(canvas);
            canvas.save();
            canvas.clipPath(clip);
            canvas.drawPath(clip, tint);
            canvas.restore();
            if (iconSource != null && amount > 0.02f) drawMagnifiedIcons(canvas);
            return;
        }
        int scale = lens ? 2 : 4;   // 透鏡要清楚；底欄模糊反正很糊，抓 1/4 大小省很多繪製（掉幀的主因）
        int sw = Math.max(1, w / scale), sh = Math.max(1, h / scale);
        if (bmp == null || bmp.getWidth() != sw || bmp.getHeight() != sh) {
            bmp = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
            bc = new Canvas(bmp);
        }
        // 第一層：頁面（不含底欄）。底欄與按鈕都用同一套 GPU 模糊，所以按鈕折射的是「模糊過的」畫面，跟底欄一致
        captureInto(bmp, bc, source, scale, bgColor);
        if (!lens) paintBandInto(bc, sw, sh);      // 光帶畫在背景圖裡：之後跟背景一起被模糊，透鏡底下也看得到
        configure(shader, lens ? skewNow : 0f);
        node.setRenderEffect(currentEffect());
        Canvas rc = node.beginRecording();
        rc.drawBitmap(bmp, null, rect, bmpPaint);
        node.endRecording();
        node.setAlpha(1f);
        canvas.save();
        canvas.clipPath(clip);
        canvas.drawRenderNode(node);
        canvas.drawPath(clip, tint);
        canvas.restore();
        if (lens) lensInfo = "強度 " + String.format("%.2f", amount) + "、模糊倍率 " + String.format("%.2f", blurFactor) + "、歪斜 " + String.format("%.3f", skewNow);
        if (lens && iconSource != null && amount > 0.02f) drawMagnifiedIcons(canvas);
    }

    /** 把底欄那排圖示畫在透鏡裡：先放大錄進一個 RenderNode，再用同一支折射著色器處理，所以圖示也會形變、有彩色光暈。 */
    /** 透鏡底下的底欄背景：從底欄的截圖裁出透鏡那一塊，用同樣的模糊半徑處理，再畫在透鏡裡。 */
    private void drawUnderLens(Canvas canvas) {
        Bitmap src = parentBar.bmp;
        if (src == null || parentBar.getWidth() <= 0 || parentBar.getHeight() <= 0) return;
        float sx = src.getWidth() / (float) parentBar.getWidth();
        float sy = src.getHeight() / (float) parentBar.getHeight();
        int w = getWidth(), h = getHeight();
        float l = getX(), t = getY();
        underNode.setPosition(0, 0, w, h);
        float blur = Math.max(0.5f, GlassPrefs.blur * getResources().getDisplayMetrics().density);   // 底下的背景永遠模糊（不隨按住變化）
        underNode.setRenderEffect(RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP));
        Canvas rc = underNode.beginRecording(w, h);
        rc.drawBitmap(src, new android.graphics.Rect(Math.round(l * sx), Math.round(t * sy), Math.round((l + w) * sx), Math.round((t + h) * sy)),
                rect, bmpPaint);
        underNode.endRecording();
        canvas.save();
        canvas.clipPath(clip);
        canvas.drawRenderNode(underNode);
        canvas.restore();
    }

    private void drawMagnifiedIcons(Canvas canvas) {
        float s = GlassPrefs.magnify ? 1f + GlassPrefs.zoom * amount : 1f;
        float ox = getX(), oy = getY();   // 按鈕在底欄座標中的位置（含 translationX）
        float cx = ox + getWidth() / 2f, cy = oy + getHeight() / 2f;
        if (!GlassPrefs.iconRefr) {   // 實驗：不經過折射著色器，直接畫放大的圖示（用來確認方塊是不是來自圖示層）
            canvas.save();
            canvas.clipPath(clip);
            canvas.translate(-ox, -oy);
            canvas.scale(s, s, cx, cy);
            iconSource.draw(canvas);
            canvas.restore();
            return;
        }
        Canvas rc = iconNode.beginRecording(getWidth(), getHeight());
        rc.translate(-ox, -oy);           // 底欄座標 → 按鈕座標
        rc.scale(s, s, cx, cy);           // 以按鈕中心放大
        iconSource.draw(rc);
        iconNode.endRecording();
        configure(iconShader, skewNow);
        iconNode.setRenderEffect(RenderEffect.createRuntimeShaderEffect(iconShader, "content"));
        canvas.save();
        canvas.clipPath(clip);
        canvas.drawRenderNode(iconNode);
        canvas.restore();
    }

    private RenderEffect currentEffect() {
        RenderEffect fx = RenderEffect.createRuntimeShaderEffect(shader, "content");
        // 按住時模糊降到很小（但不為 0）：永遠走同一條 chain 效果，避免只剩著色器時折射消失
        float blur = Math.max(0.5f, GlassPrefs.blur * getResources().getDisplayMetrics().density * blurFactor);
        return RenderEffect.createChainEffect(fx, RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP));
    }
}
