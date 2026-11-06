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
    // 膠囊玻璃著色器：移植自使用者的 Samsung Glass 模組（MIT；折射剖面與邊緣光的設計參考 KernelSU 的 FloatingBottomBar / Lens、Kyant AndroidLiquidGlass）
    private static final String COMMON =
            "uniform shader backdrop;\n"
            + "uniform float2 center;\n"
            + "uniform float2 halfSize;\n"
            + "uniform float radius;\n"
            + "uniform float density;\n"
            + "uniform float band;\n"
            + "uniform float amount;\n"
            + "uniform float progress;\n"
            + "uniform float highlightAlpha;\n"
            + "uniform float lightAngle;\n"
            + "uniform float indicator;\n"
            + "uniform float dark;\n"
            + "uniform float shade;\n"          // 底欄的暗度（只影響底欄，不影響透鏡）
            + "uniform float dispersion;\n"
            + "uniform float surface;\n"
            + "uniform float skew;\n"
            + "uniform float2 viewScale;\n"
            + "uniform float keepAlpha;\n"
            + "uniform float sheen;\n"       // 圖示層：只折射，透明的地方維持透明（不然會變成不透明的黑底）
            + "uniform float bodyLift;\n"       // 透鏡靜止時整塊微微提亮（磨砂感），按住後消失
            + "uniform float2 stretch;\n"       // 拖動的形變量（x、y）：只在透鏡邊緣的折射帶生效，中間的內容不動
            + "uniform float edgeLine;\n"       // 透鏡邊緣彩虹環的強度（0 = 不畫）
            + "uniform float edgeWidth;\n"      // 邊緣彩虹環的寬度（像素）
            + "float sd(float2 p, float r) {\n"
            + "    float2 q = abs(p) - halfSize + float2(r);\n"
            + "    return length(max(q, float2(0))) + min(max(q.x, q.y), 0.0) - r;\n"
            + "}\n"
            + "float2 gradient(float2 p, float r) {\n"
            + "    float2 q = abs(p) - halfSize + float2(r);\n"
            + "    float2 outside = max(q, float2(0));\n"
            + "    if (length(outside) > 0.001) return sign(p) * normalize(outside);\n"
            + "    return q.x > q.y ? float2(sign(p.x), 0) : float2(0, sign(p.y));\n"
            + "}\n"
            + "half4 main(float2 xy) {\n"
            + "    float2 p = xy - center;\n"
            + "    float distance = sd(p, radius);\n"
            + "    if (distance > 0.0) return half4(0.0);\n"
            + "    float depth = -distance;\n"
            + "    float edge = clamp(1.0 - depth / max(band, 0.001), 0.0, 1.0);\n"
            + "    float circleMap = 1.0 - sqrt(max(0.0, 1.0 - edge * edge));\n"
            + "    float2 normal = gradient(p, min(radius * 1.5, min(halfSize.x, halfSize.y)));\n"
            + "    if (indicator > 0.5 && length(p) > 0.001) {\n"
            + "        float2 v = normal + normalize(p);\n"
            + "        if (length(v) > 0.001) normal = normalize(v);\n"
            + "    }\n"
            + "    float2 offset = -normal * circleMap * amount;\n"
            + "    float2 refracted = xy + offset + float2(skew * p.y, 0.0);\n"
            + "    refracted -= float2(stretch.x * p.x, stretch.y * p.y) * edge * edge;\n"   // 形變只在邊緣：離邊界越近越強，中心不動
            + "    float2 lo = float2(0.5);\n"
            + "    float2 hi = center * 2.0 - float2(0.5);\n"   // 取樣限制在節點範圍內：推到範圍外會讀到透明，結果被畫成黑色，邊角像被咬掉
            + "    refracted = clamp(refracted, lo, hi);\n"
            + "    half4 base = backdrop.eval(refracted);\n"
            + "    float factor = dispersion * (p.x * p.y) / max(halfSize.x * halfSize.y, 1.0);\n"
            + "    if (dispersion > 0.0 && edge > 0.0) {\n"
            + "        float2 separated = offset * factor;\n"   // 仿酷安：沿紅到紫 7 個取樣點，各通道按光譜加權合成
            + "        float3 acc = float3(0.0);\n"
            + "        float3 wsum = float3(0.0);\n"
            + "        float alphaSum = 0.0;\n"
            + "        for (int k = 0; k < 7; k++) {\n"
            + "            float t = float(k) / 6.0;\n"
            + "            float3 w = float3(clamp(1.0 - 2.0 * t, 0.0, 1.0), clamp(1.0 - abs(2.0 * t - 1.0), 0.0, 1.0), clamp(2.0 * t - 1.0, 0.0, 1.0));\n"
            + "            half4 s = backdrop.eval(clamp(refracted + separated * (1.0 - 2.0 * t), lo, hi));\n"
            + "            acc += float3(s.rgb) * w;\n"
            + "            wsum += w;\n"
            + "            alphaSum += float(s.a);\n"
            + "        }\n"
            + "        base = half4(half3(acc / max(wsum, float3(0.001))), half(alphaSum / 7.0));\n"
            + "    }\n"
            + "    if (keepAlpha > 0.5) return base;\n"
            + "    half3 color = base.rgb + half3(surface) * (1.0 - base.a);\n"
            + "    color = mix(color, half3(0.35), indicator < 0.5 ? shade : shade * (1.0 - progress));\n"   // 底欄：往灰色混一點；透鏡沒按住時跟底欄一樣（不然比底欄更黑），按住時逐漸取消   // 透鏡內部不再變暗、不加底色，跟酷安一樣直接看到背景

            + "    float2 gn = gradient(p, radius) / viewScale;\n"
            + "    float2 n = length(gn) > 0.0001 ? normalize(gn) : float2(0.0, -1.0);\n"   // 膠囊正中央那一條線上梯度是零向量，直接正規化會變成 NaN，高光就畫出一條白線   // 放大（拉伸）後表面法線要用逆轉置修正，反光才會跟著形變
            + "    float2 light = float2(cos(lightAngle), sin(lightAngle));\n"
            + "    float mainLight = pow(max(dot(n, light), 0.0), 3.0);\n"
            + "    float secondaryLight = 0.4 * pow(max(dot(n, -light), 0.0), 3.0);\n"
            + "    float outer = pow(1.0 - smoothstep(0.0, 0.5 * density, depth), 2.0);\n"
            + "    float inner = 1.0 - smoothstep(0.15 * density, 0.7 * density, abs(depth - 1.7 * density));\n"
            + "    float lit = 0.15 + 0.85 * pow(abs(dot(n, light)), 2.0);\n"   // 整圈邊都有亮線，朝向光源（與它對面）的地方再亮一點；不再只有一個光點
            + "    float rim = lit * outer * highlightAlpha;\n"
            + "    float shadowRadius = max(0.001, 8.0 * density * progress);\n"
            + "    float shadow = indicator * 0.15 * progress\n"
            + "        * exp(-depth * depth / (2.0 * shadowRadius * shadowRadius));\n"
            + "    color *= 1.0 - 0.12 * indicator * edge * edge;\n"   // 透鏡邊緣稍微變暗（玻璃的厚度感），背景平的時候也看得出邊界；不加亮線
            + "    color = mix(color, half3(1.0), bodyLift);\n"
            + "    float sy = clamp(0.5 - p.y / (2.0 * max(halfSize.y, 1.0)), 0.0, 1.0);\n"   // 0 = 下緣、1 = 上緣
            + "    color = mix(color, half3(sy > 0.5 ? 1.0 : 0.0), sheen * abs(sy - 0.5) * 2.0 * indicator);\n"
            + "    float ring = edgeLine * (1.0 - smoothstep(0.0, edgeWidth, depth));\n"   // 邊緣彩虹環：貼著邊界一圈，不依賴背景（仿酷安的多色邊緣），黑底上也看得見
            + "    float hue = atan(n.y, n.x) / 6.2831853 + 0.5;\n"
            + "    float3 spectrum = clamp(abs(fract(hue + float3(0.0, 0.6666667, 0.3333333)) * 6.0 - 3.0) - 1.0, 0.0, 1.0);\n"
            + "    color = mix(color, half3(spectrum), clamp(ring, 0.0, 1.0));\n"
            + "    color = mix(color * (1.0 - shadow), half3(1.0), clamp(rim, 0.0, 0.9));\n"
            + "    return half4(color, 1.0);\n"
            + "}\n";
    private static final String AGSL_BAR = COMMON;
    private static final String AGSL_LENS = COMMON;

    /** 有任何玻璃元件正在抓背景：其他玻璃元件只畫子內容，免得互相遞迴。 */
    private static boolean anyCapture;
    /** 玻璃按鈕沒被按住時的效果強度（輕微折射，讓它平常也看得出是一塊玻璃）。 */
    static final float REST = 0.4f;
    /** 透鏡邊緣彩虹環的強度（0 = 不畫）；顏色依邊界方向變化（紅到紫）。想要更明顯就調大，想淡一點就調小。 */
    private static final float EDGE_LINE = 0f;
    /** 透鏡邊緣高光的強度（0 = 不畫）。想更亮就調大，想更淡就調小。 */
    private static final float HIGHLIGHT = 0.32f;
    /** 底欄中間的橫向光帶：已停用（有時會從透鏡裡透出來，看起來像一條怪線）。 */
    private static final boolean BAND_ON = false;
    /** 透鏡的立體漸層強度（上緣微亮、下緣微暗；背景再平也有凸透鏡的厚度感）。 */
    private static final float SHEEN = 0f;   // 關閉：自己的底色加亮面會讓玻璃像塑膠
    /** 透鏡靜止時整塊提亮多少（0..1，白色混合比例）；只是磨砂的微亮，不是邊緣線。按住時會慢慢消失。 */
    private static final float BODY_LIFT = 0.08f;   // 靜止時透鏡比底欄微亮，看得出圓底的形狀；按住時逐漸消失，換成清澈的玻璃
    /** 透鏡裡的圖示是否經過折射著色器。實驗中：false 時不經過著色器，用來判斷半透明方塊的來源。 */
    // 圖示是否經過折射著色器：改由設定頁的開關控制（GlassPrefs.iconRefr）
    static volatile String status = L.t("還沒畫過");
    static volatile String lensInfo = "";   // 診斷用：透鏡強度、模糊、歪斜

    private final boolean lens;
    private View source, iconSource;
    // 透鏡裡的圖示：也要經過折射著色器（之前只是直接畫，所以沒有形變、彩色光暈）
    private final RenderNode iconNode = new RenderNode("lensIcons");
    private RuntimeShader iconShader;
    private GlassBar parentBar;   // 透鏡所在的底欄：透鏡下面直接用底欄已經模糊好的背景，不會變透明
    private final RenderNode capNode = new RenderNode("glassBackdrop");
    private boolean capReady;
    private Bitmap bmp;
    private Canvas bc;
    private final RenderNode node = new RenderNode("glass");
    private final Paint bandPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    static volatile String touchInfo = "";   // 診斷用：最近一次觸控的壓力與接觸面積
    private final RenderNode shadowNode = new RenderNode("lensShadow");   // 透鏡外面的柔和陰影（按住時才出現）
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RenderNode underNode = new RenderNode("lensUnder");   // 透鏡底下：直接用底欄抓好的背景圖再模糊，不經過底欄的折射著色器
    private final RuntimeShader shader;
    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF rect = new RectF();
    private boolean capturing, failed, plain;
    private float stretchX, stretchY;   // 拖動形變量，見 setStretch
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

    // 底欄只在「被通知要更新」的短時間內重畫，所以底下的畫面在這段時間之外變了（背景更新資料、轉場結束後才換內容…），
    // 折射就會停在舊的畫面上殘留。這裡監聽整個視窗的繪製：不是我們自己造成的繪製（上一次自己畫完已經超過約 20 毫秒），
    // 就代表底下有東西變了，叫底欄再更新一小段時間。
    private long lastOwnDraw;
    private final android.view.ViewTreeObserver.OnPreDrawListener contentWatcher = () -> {
        long now = SystemClock.uptimeMillis();
        if (GlassPrefs.gpu) { if (!capturing && now - lastOwnDraw > 8) kick(120); }   // GPU 版：頁面每更新一幀，底欄也跟著重錄，才不會停在舊的一幀
        else if (now - lastOwnDraw > 20 && now >= until) kick(220);
        return true;
    };
    private boolean watching;

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!watching) { getViewTreeObserver().addOnPreDrawListener(contentWatcher); watching = true; }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (watching) { getViewTreeObserver().removeOnPreDrawListener(contentWatcher); watching = false; }
        super.onDetachedFromWindow();
    }

    void setParentBar(GlassBar b) { parentBar = b; }

    /** 發光的顏色（主色）。 */
    private int glowColor = 0xFF8AB4F8;

    void setGlowColor(int color) {
        glowColor = color;
    }

    /** 把暗色層畫進背景圖（底欄座標 → 背景圖座標）。 */
    /** 底欄的暗色層：每次畫之前都用目前的「底欄暗度」重設，滑桿拖動才會立即生效。 */
    private void refreshTint() {
        if (lens) return;   // 透鏡不畫底色
        int a = Math.round(GlassPrefs.barDark * 255);
        int h = Math.max(1, getHeight());
        tint.setShader(new LinearGradient(0, 0, 0, h, new int[]{(tintColor & 0xFFFFFF) | (a << 24), (tintColor & 0xFFFFFF) | (a << 24)},
                null, Shader.TileMode.CLAMP));
    }

    private void paintTintInto(Canvas bc, int sw, int sh) {
        refreshTint();
        bc.save();
        bc.scale(sw / (float) getWidth(), sh / (float) getHeight());
        bc.drawRect(0, 0, getWidth(), getHeight(), tint);
        bc.restore();
    }

        /** 底欄整條發光的強度（按住時由 MainActivity 設定，0 = 不發光）。 */
    private float glowAmount;

    void setGlowAmount(float a) {
        glowAmount = 0f;   // 按下發光已取消（底欄保持安靜）
        invalidate();
    }

    /** 按壓力度造成的發光（0..1）；由 MainActivity 的觸控事件設定。 */
    private float pressGlow;

    float getPressGlow() {
        return pressGlow;
    }

    void setPressGlow(float p) {
        pressGlow = 0f;   // 按下發光已取消
        invalidate();
    }

    /** 底欄橫向中間的光帶：白色核心、外圍是主題色。畫進背景圖（bc），所以會跟著底欄一起模糊。 */
    private void paintBandInto(Canvas bc, int sw, int sh) {
        if (!BAND_ON) return;
        float a = Math.max(glowAmount, pressGlow);
        if (a < 0.02f) return;
        bandPaint.setAlpha(Math.round(255 * Math.min(1f, a)));
        bc.save();
        bc.scale(sw / (float) getWidth(), sh / (float) getHeight());   // 底欄座標 → 背景圖座標
        bc.drawRect(0, 0, getWidth(), getHeight(), bandPaint);
        bc.restore();
    }

    /** 透鏡專用：底欄裡「只有圖示與文字」的那一層（另外抓、不模糊，只放大折射）。 */
    void setIconSource(View v) { iconSource = v; }

    /** 要放大的那排圖示（和底欄同一個座標系，與 fl 的左上角對齊）。 */

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
    private boolean live;

    /** 底欄後面有一直在動的畫面（首頁的極光）時開啟：每一幀都更新。 */
    void setLive(boolean on) {
        live = on;
        if (on) invalidate();
    }

    /** 拖動時的形變量（只畫在透鏡邊緣，中間的內容不動）。要配合 kick() 讓畫面更新。 */
    private float jellyX = 1f, jellyY = 1f;   // 果凍形變：拖動時透鏡的外形沿著移動方向拉長、另一邊壓扁（圖示反向補償，不會被拉歪）

    void setJelly(float jx, float jy) {
        jellyX = jx;
        jellyY = jy;
    }

    void setStretch(float sx, float sy) {
        stretchX = sx;
        stretchY = sy;
    }

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
        tint.setShader(new LinearGradient(0, 0, 0, h, lens ? new int[]{0x00000000, 0x00000000}   // 透鏡不畫底色
                        : new int[]{(tintColor & 0xFFFFFF) | 0xEB000000, (tintColor & 0xFFFFFF) | 0xEB000000},
                null, Shader.TileMode.CLAMP));
        // 柔和的光帶：中間略亮、主題色為主，邊緣完全淡出（不用純白，避免太刺眼）
        bandPaint.setShader(new LinearGradient(0, 0, 0, h, new int[]{(glowColor & 0xFFFFFF) | 0x00000000, (glowColor & 0xFFFFFF) | 0x26000000,
                (glowColor & 0xFFFFFF) | 0x4D000000, (glowColor & 0xFFFFFF) | 0x26000000, (glowColor & 0xFFFFFF) | 0x00000000},
                new float[]{0f, 0.3f, 0.5f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        rim.setShader(new LinearGradient(0, 0, w, h, new int[]{0x90FFFFFF, 0x18FFFFFF, 0x50FFFFFF},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        try {
            configure(shader, 0f);
            node.setRenderEffect(currentEffect());
        } catch (Throwable e) {
            failed = true;
            status = L.t("失敗，已退回半透明：") + e;
        }
    }

    /** 虛擬光源：固定在視窗左上方的一個點。回傳 {角度, 累計 X 縮放, 累計 Y 縮放, 光向量 x, y}；玻璃移動、拉伸時，高光點在透鏡裡的位置跟著改變。 */
    private float[] virtualLight() {
        int[] loc = new int[2];
        getLocationInWindow(loc);
        float sx = getScaleX(), sy = getScaleY();
        android.view.ViewParent p = getParent();
        while (p instanceof View) {
            sx *= ((View) p).getScaleX();
            sy *= ((View) p).getScaleY();
            p = p.getParent();
        }
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        float cx = loc[0] + getWidth() * getScaleX() / 2f, cy = loc[1] + getHeight() * getScaleY() / 2f;
        float lx = dm.widthPixels * 0.3f, ly = -dm.heightPixels * 0.35f;
        float ang = (float) Math.atan2(ly - cy, lx - cx) - skewNow * 0.6f;
        // 光源在鏡頭左上方：水平方向隨玻璃位置改變（玻璃越往右，光越從左邊斜射進來），垂直方向只略偏上，所以高光落在透鏡中間附近
        float tx = Math.max(-0.7f, Math.min(0.7f, (lx - cx) / (0.9f * dm.widthPixels))) - skewNow * 0.8f;
        float ty = -0.22f;
        return new float[]{ang, Math.max(0.3f, sx), Math.max(0.3f, sy), tx, ty};
    }

    private void configure(RuntimeShader sh, float skewValue) {
        float w = getWidth(), h = getHeight();
        float dp = getResources().getDisplayMetrics().density;
        float progress = lens ? Math.max(0f, Math.min(1f, (amount - REST) / (1f - REST))) : 0f;   // 按住的程度（底欄恆為 0）
        float push = lens ? amount : 1f;
        sh.setFloatUniform("center", w / 2f, h / 2f);
        sh.setFloatUniform("halfSize", w / 2f, h / 2f);
        sh.setFloatUniform("radius", radius);
        sh.setFloatUniform("density", 4f * dp);                              // 邊緣光的寬度尺度（約 3dp；太小就看不到）
        sh.setFloatUniform("band", (lens ? 12f : 18f) * dp);                  // 邊緣折射的寬度
        sh.setFloatUniform("amount", 24f * dp * GlassPrefs.refr * push);      // 折射推開的距離（像素）
        sh.setFloatUniform("progress", progress);
        sh.setFloatUniform("highlightAlpha", lens ? HIGHLIGHT * progress : 0f);   // 邊緣高光：被左上光源照到的亮邊，背景再平也有玻璃感；按住才慢慢出現
        float[] lp = virtualLight();
        sh.setFloatUniform("lightAngle", lp[0]);                              // 虛擬光源：固定在畫面左上方，方向隨位置與歪斜變化
        sh.setFloatUniform("viewScale", lp[1], lp[2]);                        // 這個玻璃目前被放大的倍數（含上層容器）
        sh.setFloatUniform("indicator", lens ? 1f : 0f);                      // 透鏡（選中的按鈕）用指示器模式
        sh.setFloatUniform("dark", 1f);
        sh.setFloatUniform("shade", GlassPrefs.barDark * 0.27f);   // 底欄暗度（預設 0.66 ≈ 0.18）
        sh.setFloatUniform("dispersion", lens ? GlassPrefs.chroma * progress : 0f);   // 沒按住不要彩虹邊：色散跟按住的程度成正比
        sh.setFloatUniform("surface", lens ? 0f : 0.06f);
        sh.setFloatUniform("edgeLine", lens ? EDGE_LINE * progress : 0f);   // 沒按住不畫彩虹環，按住才慢慢出現
        sh.setFloatUniform("bodyLift", lens ? BODY_LIFT * (1f - progress) : 0f);   // 靜止時透鏡整塊微亮，按住時消失
        sh.setFloatUniform("stretch", lens ? stretchX : 0f, lens ? stretchY : 0f);
        sh.setFloatUniform("edgeWidth", 1.0f * dp);   // 彩虹環細一點（1dp），跟酷安一樣是細線
        sh.setFloatUniform("skew", skewValue);
        sh.setFloatUniform("keepAlpha", 0f);
        sh.setFloatUniform("sheen", lens ? SHEEN * (0.4f + 0.6f * progress) : 0f);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (capturing) return;                        // 我正在抓背景：不要把自己畫進去
        lastOwnDraw = SystemClock.uptimeMillis();
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
                boolean jelly = lens && (jellyX != 1f || jellyY != 1f);
                if (jelly) { canvas.save(); canvas.scale(jellyX, jellyY, w / 2f, h / 2f); }
                try { drawGlass(canvas, w, h); } finally { if (jelly) canvas.restore(); }
                status = L.t("正常（玻璃效果執行中）");
            } catch (Throwable e) {   // 這支手機的玻璃效果不能用：退回半透明底色，不要讓 App 閃退
                failed = true;
                capturing = false;
                anyCapture = false;
                status = L.t("失敗，已退回半透明：") + e;
                drawFlat(canvas);
            }
        } else {
            drawFlat(canvas);
        }
        super.dispatchDraw(canvas);
        if (live || SystemClock.uptimeMillis() < until) postInvalidateOnAnimation();   // 首頁有動畫背景要一直更新；其他頁只在碰觸、捲動、切頁後的一小段時間更新（省電）
    }

    private float lastTx, skewNow;
    private long lastT;
    /** 歪斜的速度門檻（dp/ms）：低於這個的移動不產生歪斜。 */
    private static final float SKEW_DEAD = 0.03f;

    /** 玻璃按鈕的橫向速度 → 歪斜量（平滑過、停下來會回到 0）。 */
    private void updateSkew() {
        long now = SystemClock.uptimeMillis();
        float tx = getTranslationX();
        if (lastT != 0 && now > lastT) {
            float vDpMs = (tx - lastTx) / getResources().getDisplayMetrics().density / (now - lastT);   // dp/ms
            if (Math.abs(vDpMs) < SKEW_DEAD) vDpMs = 0f;   // 手指微抖的速度不歪斜，免得圖示跟著抖
            float target = Math.max(-1.0f, Math.min(1.0f, -vDpMs * 1.0f)) * GlassPrefs.skew;
            skewNow = skewNow * 0.8f + target * 0.2f;
            if (!GlassPrefs.skewOn) skewNow = 0f;
        }
        lastTx = tx;
        lastT = now;
        if (Math.abs(skewNow) > 0.003f) kick(200);
    }

    private void drawFlat(Canvas canvas) {
        refreshTint();
        if (!lens) {
            Paint solid = new Paint(Paint.ANTI_ALIAS_FLAG);
            solid.setColor((bgColor & 0xFFFFFF) | (Math.round(GlassPrefs.barDark * 255) << 24));   // 底欄暗度
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


    /** 實驗：用 GPU 把底欄後面的畫面錄進 capNode（原始解析度），底欄所在的子 View 跳過。 */
    private void recordBackdrop(View src, int w, int h, int erase) {
        capNode.setPosition(0, 0, w, h);
        capNode.setUseCompositingLayer(true, null);   // 先畫成真正的圖層，著色器才讀得到內容
        android.graphics.RecordingCanvas rc = capNode.beginRecording(w, h);
        try {
            paintBackdrop(rc, src, w, h, erase, -1);
        } finally {
            capNode.endRecording();
        }
        capReady = true;
    }

    /** 把底欄後面的畫面直接畫進 rc（底欄座標、原始解析度）。透鏡底下也用這個直接重畫：嵌套另一個 RenderNode 在帶特效的節點裡不會被畫出來。 */
    private void paintBackdrop(android.graphics.RecordingCanvas rc, View src, int w, int h, int erase, int lensDark) {
        int[] a = new int[2], b = new int[2];
        getLocationInWindow(a);
        src.getLocationInWindow(b);
        float x0 = a[0] - b[0] + getPivotX() * (getScaleX() - 1f);
        float y0 = a[1] - b[1] + getPivotY() * (getScaleY() - 1f);
        View skip = this;
        while (skip.getParent() instanceof View && skip.getParent() != src) skip = (View) skip.getParent();
        capturing = true;
        anyCapture = true;
        try {
            rc.drawColor(erase);
            rc.translate(-x0, -y0);
            if (src.getBackground() != null) src.getBackground().draw(rc);
            if (src instanceof android.view.ViewGroup) {
                android.view.ViewGroup g = (android.view.ViewGroup) src;
                for (int k = 0; k < g.getChildCount(); k++) {
                    View ch = g.getChildAt(k);
                    if (ch == skip || ch.getVisibility() != View.VISIBLE || ch.getAlpha() <= 0f) continue;
                    int save = rc.save();
                    rc.translate(ch.getLeft() + ch.getTranslationX(), ch.getTop() + ch.getTranslationY());
                    if (ch.getScaleX() != 1f || ch.getScaleY() != 1f) rc.scale(ch.getScaleX(), ch.getScaleY(), ch.getPivotX(), ch.getPivotY());
                    if (ch.getAlpha() < 1f) rc.saveLayerAlpha(0, 0, ch.getWidth(), ch.getHeight(), Math.round(ch.getAlpha() * 255));
                    rc.clipRect(0, 0, ch.getWidth(), ch.getHeight());
                    rc.translate(-ch.getScrollX(), -ch.getScrollY());   // 系統錄子 View 時會先減掉捲動量；直接呼叫 draw 要自己減（漏掉這行，捲動容器就停在最上面那一幀）
                    ch.draw(rc);
                    rc.restoreToCount(save);
                }
            } else {
                src.draw(rc);
            }
            rc.translate(x0, y0);
            if (lensDark < 0) {   // 底欄本身：光帶與暗色層
                paintBandInto(rc, w, h);
                paintTintInto(rc, w, h);
            } else if (lensDark > 0) {   // 透鏡底下：沒按住時跟底欄一樣暗（不穿透底欄），按住才變清澈
                rc.drawColor(lensDark << 24);
            }
        } finally {
            capturing = false;
            anyCapture = false;
        }
    }

    private void drawGlass(Canvas canvas, int w, int h) {
        refreshTint();
        if (lens && parentBar != null) {
            // 透鏡：底下用底欄的模糊背景（不再另外抓頁面，所以不會透明），圖示照常放大與折射
            drawOuterShadow(canvas);
            drawUnderLens(canvas);
            if (iconSource != null) drawMagnifiedIcons(canvas);   // 圖示另外一層：只折射、不模糊，所以模糊再大圖示也清楚
            return;
        }
        boolean useGpu = GlassPrefs.gpu && !lens;
        if (useGpu) {
            recordBackdrop(source, w, h, bgColor);
            configure(shader, 0f);
            node.setRenderEffect(currentEffect());
            Canvas rc = node.beginRecording();
            rc.drawRenderNode(capNode);
        } else {
            // 抓背景的解析度跟著模糊程度走：模糊很少（預設 0）時底下的字看得清楚，縮小抓會一眼看出鋸齒，要用原始解析度；
            // 模糊夠大時縮小也看不出來，才縮小抓省繪製（大量縮小抓是之前為了避免掉幀）。透鏡維持 1/2。
            float blurPx = GlassPrefs.blur * getResources().getDisplayMetrics().density * blurFactor;
            int scale = lens ? 2 : blurPx < 3f ? 1 : blurPx < 10f ? 2 : 4;
            int sw = Math.max(1, w / scale), sh = Math.max(1, h / scale);
            if (bmp == null || bmp.getWidth() != sw || bmp.getHeight() != sh) {
                bmp = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
                bc = new Canvas(bmp);
            }
            // 第一層：頁面（不含底欄）。底欄與按鈕都用同一套 GPU 模糊，所以按鈕折射的是「模糊過的」畫面，跟底欄一致
            captureInto(bmp, bc, source, scale, bgColor);   // 不透明的底色：透明像素會在著色器裡發出綠紫色斑
            if (!lens) paintBandInto(bc, sw, sh);      // 光帶畫在背景圖裡：之後跟背景一起被模糊，透鏡底下也看得到
            if (!lens) paintTintInto(bc, sw, sh);      // 暗色層也畫進背景圖：透鏡折射到的底欄就是暗的，跟底欄一致
            configure(shader, lens ? skewNow : 0f);
            node.setRenderEffect(currentEffect());
            Canvas rc = node.beginRecording();
            rc.drawBitmap(bmp, null, rect, bmpPaint);
        }
        node.endRecording();
        node.setAlpha(1f);
        canvas.save();
        canvas.clipPath(clip);
        canvas.drawRenderNode(node);
        canvas.restore();
        if (lens) lensInfo = L.t("強度 ") + String.format("%.2f", amount) + L.t("、模糊倍率 ") + String.format("%.2f", blurFactor) + L.t("、歪斜 ") + String.format("%.3f", skewNow);
        if (lens && iconSource != null && amount > 0.02f) drawMagnifiedIcons(canvas);
    }

    /** 把底欄那排圖示畫在透鏡裡：先放大錄進一個 RenderNode，再用同一支折射著色器處理，所以圖示也會形變、有彩色光暈。 */
    /** 透鏡底下的底欄背景：從底欄的截圖裁出透鏡那一塊，用同樣的模糊半徑處理，再畫在透鏡裡。 */
    /** 透鏡外面一圈柔和的陰影，把玻璃從背景分開（按住時才出現，越用力越明顯）。畫在透鏡自己的範圍外，所以上層容器不能裁切。 */
    private void drawOuterShadow(Canvas canvas) {
        float progress = Math.max(0f, Math.min(1f, (amount - REST) / (1f - REST)));
        if (progress < 0.03f) return;
        float dp = getResources().getDisplayMetrics().density;
        int m = Math.round(18f * dp);   // 陰影往外延伸的空間
        int w = getWidth(), h = getHeight();
        shadowNode.setPosition(0, 0, w + 2 * m, h + 2 * m);
        shadowNode.setRenderEffect(RenderEffect.createBlurEffect(7f * dp, 7f * dp, Shader.TileMode.CLAMP));
        Canvas rc = shadowNode.beginRecording(w + 2 * m, h + 2 * m);
        shadowPaint.setColor(((int) (0x66 * progress) << 24));
        rc.translate(m, m + 3f * dp);   // 陰影稍微往下偏，像光從上面來
        rc.drawPath(clip, shadowPaint);
        shadowNode.endRecording();
        canvas.save();
        canvas.translate(-m, -m);
        canvas.drawRenderNode(shadowNode);
        canvas.restore();
    }

    private void drawUnderLens(Canvas canvas) {
        if (GlassPrefs.gpu && parentBar.capReady && parentBar.getWidth() > 0) {
            int gw = getWidth(), gh = getHeight();
            underNode.setPosition(0, 0, gw, gh);
            configure(shader, skewNow);
            underNode.setRenderEffect(currentEffect());
            android.graphics.RecordingCanvas grc = underNode.beginRecording(gw, gh);
            grc.translate(-getX(), -getY());
            parentBar.paintBackdrop(grc, parentBar.source, parentBar.getWidth(), parentBar.getHeight(), parentBar.bgColor, Math.round(GlassPrefs.barDark * 255 * (1f - Math.max(0f, Math.min(1f, (amount - REST) / (1f - REST))))));
            underNode.endRecording();
            canvas.save();
            canvas.clipPath(clip);
            canvas.drawRenderNode(underNode);
            canvas.restore();
            return;
        }
        Bitmap src = parentBar.bmp;
        if (src == null || parentBar.getWidth() <= 0 || parentBar.getHeight() <= 0) return;
        float sx = src.getWidth() / (float) parentBar.getWidth();
        float sy = src.getHeight() / (float) parentBar.getHeight();
        int w = getWidth(), h = getHeight();
        float l = getX(), t = getY();
        underNode.setPosition(0, 0, w, h);
        configure(shader, skewNow);
        underNode.setRenderEffect(currentEffect());   // 透鏡底下的背景也經過折射（之前只有圖示折射）
        Canvas rc = underNode.beginRecording(w, h);
        rc.drawBitmap(src, new android.graphics.Rect(Math.round(l * sx), Math.round(t * sy), Math.round((l + w) * sx), Math.round((t + h) * sy)),
                rect, bmpPaint);
        // 圖示不畫在這層：這層會被模糊，圖示畫進來就跟著糊掉（模糊大於 0 時，選中那格的圖示看不清楚）。圖示改由 drawMagnifiedIcons 另外畫，只折射不模糊
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
            canvas.scale(1f / jellyX, 1f / jellyY, getWidth() / 2f, getHeight() / 2f);   // 外形被拉歪時，圖示反向補償
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
        configure(iconShader, 0f);   // 圖示不歪斜：歪斜只給透鏡本身，圖示才不會跟著微小移動抖
        iconShader.setFloatUniform("dispersion", 0f);   // 圖示不要彩虹色散，才看得清楚（透鏡邊緣的色散只給透鏡本身）
        iconShader.setFloatUniform("keepAlpha", 1f);   // 透明的地方保持透明，才不會蓋住透鏡底下的背景
        iconShader.setFloatUniform("shade", 0f);       // 圖示層只折射：暗度已經在透鏡底下那層加過了，再加一次就變暗
        iconShader.setFloatUniform("surface", 0f);
        iconShader.setFloatUniform("bodyLift", 0f);     // 圖示不跟著透鏡整塊提亮，顏色才準
        iconNode.setRenderEffect(RenderEffect.createRuntimeShaderEffect(iconShader, "backdrop"));
        canvas.save();
        canvas.clipPath(clip);
        canvas.scale(1f / jellyX, 1f / jellyY, getWidth() / 2f, getHeight() / 2f);
        canvas.drawRenderNode(iconNode);
        canvas.restore();
    }

    private RenderEffect currentEffect() {
        RenderEffect fx = RenderEffect.createRuntimeShaderEffect(shader, "backdrop");
        // 按住時模糊降到很小（但不為 0）：永遠走同一條 chain 效果，避免只剩著色器時折射消失
        float blur = Math.max(0.5f, GlassPrefs.blur * getResources().getDisplayMetrics().density * blurFactor);
        return RenderEffect.createChainEffect(fx, RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP));
    }
}
