package com.phoneai.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RecordingCanvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/**
 * 頂欄的毛玻璃（實驗）：把「它底下那一塊內容」模糊後當背景，上面再蓋一層淡淡的底色與一條細線。
 * 不折射，只有模糊（比底欄的液態玻璃單純）。內容來源是捲動區；捲動時、版面變動時、換分頁後的一小段時間會重畫。
 */
final class FrostView extends View {
    private View source;
    private RenderNode node = new RenderNode("frost");
    private final Paint tint = new Paint();
    private final Paint mask = new Paint();
    private final float d;
    private long until;

    FrostView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
        int surface = Ui.attr(c, com.google.android.material.R.attr.colorSurface);
        int onSurface = Ui.attr(c, com.google.android.material.R.attr.colorOnSurface);
        // 玻璃的底色：就是頁面的背景色（約 45% 不透明）。沒有內容透出來的地方，玻璃跟頁面一模一樣，漸層過渡就不會有色差、斷層
        tint.setColor((surface & 0x00FFFFFF) | 0x9E000000);   // 62% 不透明：後面的亮卡片（例如青色那張）和暗卡片的色差被壓小，一整條比較均勻
        mask.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN));   // 漸層遮罩：留下玻璃的程度 = 遮罩的不透明度
    }

    void setSource(View s) {
        source = s;
        if (s != null) s.getViewTreeObserver().addOnScrollChangedListener(this::invalidate);
    }

    /** 接下來 ms 毫秒內每一幀都重畫（換分頁、卡片進場動畫時內容在動，但不是捲動）。 */
    void kick(long ms) {
        until = Math.max(until, SystemClock.uptimeMillis() + ms);
        invalidate();
    }

    /** 回到前景：換新的繪圖節點（跟底欄玻璃一樣，避免舊的繪圖資源壞掉）。 */
    void reset() {
        try {
            node = new RenderNode("frost");
        } catch (Throwable ignored) { }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (source == null || w <= 0 || h <= 0) return;
        if (!canvas.isHardwareAccelerated()) {   // 沒有硬體加速：不模糊，只蓋底色
            canvas.drawRect(0, 0, w, h, tint);
            return;
        }
        try {
            int[] a = new int[2], b = new int[2];
            getLocationOnScreen(a);
            source.getLocationOnScreen(b);
            node.setPosition(0, 0, w, h);
            float r = 22f * d;   // 模糊更大一點：亮的卡片色塊往旁邊散開、被周圍的暗色拉平
            node.setRenderEffect(RenderEffect.createBlurEffect(r, r, Shader.TileMode.MIRROR));
            RecordingCanvas rc = node.beginRecording(w, h);
            rc.drawColor(Ui.attr(getContext(), com.google.android.material.R.attr.colorSurface));
            rc.translate(b[0] - a[0], b[1] - a[1]);   // 捲動區在這塊玻璃座標裡的位置
            // 不能直接 source.draw(rc)：系統自己錄捲動容器時會先減掉捲動量，手動呼叫 draw 不會，結果畫出來的永遠是「沒捲動」的頁面頂端
            // （底欄玻璃也踩過同一個坑：背景凍結）。所以改成自己逐個子 View 畫，並減掉捲動量。
            if (source.getBackground() != null) source.getBackground().draw(rc);
            if (source instanceof android.view.ViewGroup) {
                android.view.ViewGroup g = (android.view.ViewGroup) source;
                for (int k = 0; k < g.getChildCount(); k++) {
                    View ch = g.getChildAt(k);
                    if (ch.getVisibility() != View.VISIBLE || ch.getAlpha() <= 0f) continue;
                    int save = rc.save();
                    rc.translate(ch.getLeft() + ch.getTranslationX() - g.getScrollX(), ch.getTop() + ch.getTranslationY() - g.getScrollY());
                    if (ch.getAlpha() < 1f) rc.saveLayerAlpha(0, 0, ch.getWidth(), ch.getHeight(), Math.round(ch.getAlpha() * 255));
                    rc.clipRect(0, 0, ch.getWidth(), ch.getHeight());
                    rc.translate(-ch.getScrollX(), -ch.getScrollY());
                    ch.draw(rc);
                    rc.restoreToCount(save);
                }
            } else {
                source.draw(rc);
            }
            node.endRecording();
        } catch (Throwable t) {
            CrashLog.w("頂欄毛玻璃", t);
            canvas.drawRect(0, 0, w, h, tint);
        }
        // 玻璃（模糊＋底色）整塊畫在一個圖層上，再用漸層遮罩把最下面一段淡成透明
        int sc = canvas.saveLayer(0, 0, w, h, null);
        try {
            canvas.drawRenderNode(node);
            canvas.drawRect(0, 0, w, h, tint);
            float fade = Math.min(h, 44f * d);
            mask.setShader(new android.graphics.LinearGradient(0, h - fade, 0, h, 0xFF000000, 0x00000000, Shader.TileMode.CLAMP));
            canvas.drawRect(0, h - fade, w, h, mask);
        } finally {
            canvas.restoreToCount(sc);
        }
        if (SystemClock.uptimeMillis() < until) postInvalidateOnAnimation();
    }
}
