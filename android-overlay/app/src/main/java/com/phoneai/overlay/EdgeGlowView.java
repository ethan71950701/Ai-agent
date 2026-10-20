package com.phoneai.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.SweepGradient;
import android.os.SystemClock;
import android.view.Display;
import android.view.RoundedCorner;
import android.view.View;

/**
 * 螢幕邊緣的流動光暈（AI 正在操作手機的提示）：
 *  - 顏色：一圈彩色漸層沿邊緣「順時針」慢慢繞行；色票只用相近的藍→青→紫→粉，首尾相接，沒有突兀的色差。
 *  - 形狀：不是等寬的一圈，而是「厚度像波浪一樣起伏」，波沿著邊緣順時針流動；多層疊在一起，從邊緣往內平滑淡出。
 * 不攔觸控；整段任務期間都在，不為截圖藏起來（給 AI 的說明裡有交代「邊緣的光暈是系統提示，忽略」）。
 */
public class EdgeGlowView extends View {
    private static final int[] COLORS = {0xFF4285F4, 0xFF34A8EB, 0xFF7C8CF0, 0xFF9B72CB, 0xFFC56CB0, 0xFF9B72CB, 0xFF7C8CF0, 0xFF4285F4};
    private static final int N = 200;                                    // 沿邊緣取樣的點數
    private static final float LAP_SECONDS = 9f;                         // 顏色繞一圈幾秒
    private static final float WAVE_DP_PER_S = 60f;                      // 波沿邊緣前進的速度
    private static final int WAVES = 9;                                  // 繞一圈有幾個波
    private static final int WAVES2 = 21;                                // 第二層細波；兩個都要是整數，繞一圈才接得起來（不然左上角起點會有斷口）
    // 每一層：最大深度(dp)、基本透明度；由淺到深、由亮到淡，疊出平滑的淡出
    private static final float[] DEPTH = {10f, 18f, 27f, 37f, 48f};
    private static final float[] ALPHA = {0.34f, 0.24f, 0.16f, 0.10f, 0.06f};

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix m = new Matrix();
    private final Path path = new Path();
    private final float d;               // 厚度與波速的單位（全螢幕版 = 螢幕密度；縮在預覽小視窗裡的版本再乘上縮小比例）
    private final float fixedCorner;     // > 0：用這個圓角半徑（預覽卡片）；0：問系統螢幕的圓角
    private final float[] px = new float[N], py = new float[N], nx = new float[N], ny = new float[N];
    // 波形的 sin／cos 預先算好（每個點、兩個頻率各一組）：每一幀只要用加法公式 sin(a+b)=sin a·cos b+cos a·sin b 組合，
    // 不用再對 5 層 × 200 點每幀呼叫 2000 次 sin（以前光暈把主執行緒占滿，同一程序裡的預覽影片只剩 20～30 fps）
    private final float[] sk1 = new float[N + 1], ck1 = new float[N + 1], sk2 = new float[N + 1], ck2 = new float[N + 1];
    private float k1, k2;
    private final Runnable redraw = this::invalidate;
    private final long frameMs;                                          // 重畫間隔（全螢幕約 30fps；預覽小視窗裡更慢；慢波浪不需要 60/120fps）
    private float perimeter;
    private float cornerR;   // 目前使用的圓角半徑（像素）；盡量用手機螢幕實際的圓角
    private SweepGradient sweep;

    public EdgeGlowView(Context c) {
        this(c, 1f, 0f, 28);
    }

    /** 縮小版（畫在預覽小視窗的卡片裡）：unitScale 縮小厚度與波速；cornerPx 是卡片的圓角；frameMs 重畫間隔。 */
    public EdgeGlowView(Context c, float unitScale, float cornerPx, long frameMs) {
        super(c);
        d = c.getResources().getDisplayMetrics().density * unitScale;
        fixedCorner = cornerPx;
        this.frameMs = frameMs;
        p.setStyle(Paint.Style.FILL);
    }

    /** 手機螢幕實際的圓角半徑（像素）：問系統（四個角取最大的），問不到才用 54dp 估計。 */
    private float screenCornerRadius() {
        if (fixedCorner > 0f) return fixedCorner;
        Display dsp = getDisplay();
        float best = 0f;
        if (dsp != null) {
            for (int pos : new int[]{RoundedCorner.POSITION_TOP_LEFT, RoundedCorner.POSITION_TOP_RIGHT,
                    RoundedCorner.POSITION_BOTTOM_LEFT, RoundedCorner.POSITION_BOTTOM_RIGHT}) {
                RoundedCorner rc = dsp.getRoundedCorner(pos);
                if (rc != null) best = Math.max(best, rc.getRadius());
            }
        }
        return best > 0f ? best : 54 * d;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getWidth() > 0 && getHeight() > 0) rebuild(getWidth(), getHeight());   // 這時才問得到所在的螢幕
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        rebuild(w, h);
    }

    private void rebuild(int w, int h) {
        sweep = new SweepGradient(w / 2f, h / 2f, COLORS, null);
        float r = Math.min(screenCornerRadius(), Math.min(w, h) / 2f);   // 跟手機螢幕的圓角一致，光暈的角才會貼著螢幕的角
        cornerR = r;
        float top = w - 2 * r, side = h - 2 * r, arc = (float) (Math.PI * r / 2);
        perimeter = 2 * top + 2 * side + 4 * arc;
        k1 = (float) (2 * Math.PI * WAVES / perimeter);
        k2 = (float) (2 * Math.PI * WAVES2 / perimeter);
        for (int i = 0; i <= N; i++) {
            double sPos = (double) perimeter * i / N;
            sk1[i] = (float) Math.sin(k1 * sPos);
            ck1[i] = (float) Math.cos(k1 * sPos);
            sk2[i] = (float) Math.sin(k2 * sPos);
            ck2[i] = (float) Math.cos(k2 * sPos);
        }
        for (int i = 0; i < N; i++) {
            float s = perimeter * i / N;
            float x, y, nxx, nyy;
            if (s < top) { x = r + s; y = 0; nxx = 0; nyy = 1; }
            else if ((s -= top) < arc) { double a = -Math.PI / 2 + s / r; float cx = w - r, cy = r; x = cx + r * (float) Math.cos(a); y = cy + r * (float) Math.sin(a); nxx = (cx - x) / r; nyy = (cy - y) / r; }
            else if ((s -= arc) < side) { x = w; y = r + s; nxx = -1; nyy = 0; }
            else if ((s -= side) < arc) { double a = s / r; float cx = w - r, cy = h - r; x = cx + r * (float) Math.cos(a); y = cy + r * (float) Math.sin(a); nxx = (cx - x) / r; nyy = (cy - y) / r; }
            else if ((s -= arc) < top) { x = w - r - s; y = h; nxx = 0; nyy = -1; }
            else if ((s -= top) < arc) { double a = Math.PI / 2 + s / r; float cx = r, cy = h - r; x = cx + r * (float) Math.cos(a); y = cy + r * (float) Math.sin(a); nxx = (cx - x) / r; nyy = (cy - y) / r; }
            else if ((s -= arc) < side) { x = 0; y = h - r - s; nxx = 1; nyy = 0; }
            else { s -= side; double a = Math.PI + s / r; float cx = r, cy = r; x = cx + r * (float) Math.cos(a); y = cy + r * (float) Math.sin(a); nxx = (cx - x) / r; nyy = (cy - y) / r; }
            px[i] = x; py[i] = y; nx[i] = nxx; ny[i] = nyy;
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        if (sweep == null || perimeter <= 0) return;
        float t = SystemClock.uptimeMillis() / 1000f;
        m.setRotate(t / LAP_SECONDS * 360f, getWidth() / 2f, getHeight() / 2f);  // 角度增加 = 順時針
        sweep.setLocalMatrix(m);
        p.setShader(sweep);
        float breath = 0.5f + 0.5f * (float) Math.sin(t * 1.3f);
        double travel = (double) WAVE_DP_PER_S * d * t;                  // 波往前走了多遠（沿順時針方向）

        for (int j = 0; j < DEPTH.length; j++) {
            float ph = j * 0.7f;                                         // 各層相位錯開，波浪才有層次
            double a1 = ph - k1 * travel, a2 = ph * 1.7f - k2 * travel * 0.8;
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1), c2 = (float) Math.cos(a2), s2 = (float) Math.sin(a2);
            path.reset();
            // 外緣與內緣都多走一點回到起點（i = N 就是 i = 0），整圈閉合；以前少畫最後一段，左上角會露出一根尖尖的斷口
            for (int i = 0; i <= N; i++) {
                if (i == 0) path.moveTo(px[0], py[0]); else path.lineTo(px[i % N], py[i % N]);
            }
            for (int i = N; i >= 0; i--) {
                int q = i % N;
                // 振幅大：厚的地方約 1.0 倍、薄的地方接近 0，看得出波峰波谷；兩個頻率疊起來比較自然
                float wave = 0.50f + 0.40f * (sk1[i] * c1 + ck1[i] * s1)
                        + 0.18f * (sk2[i] * c2 + ck2[i] * s2);
                float dep = Math.min(DEPTH[j] * d * Math.max(0.04f, wave), cornerR * 0.9f);  // 不讓厚度變負（內緣翻出去會畫出怪形狀）；也不超過圓角半徑（在轉角處會穿過圓心翻面）
                path.lineTo(px[q] + nx[q] * dep, py[q] + ny[q] * dep);
            }
            path.close();
            p.setAlpha((int) (255 * ALPHA[j] * (0.85f + 0.15f * breath)));
            c.drawPath(path, p);
        }
        removeCallbacks(redraw);
        postDelayed(redraw, frameMs);
    }
}
