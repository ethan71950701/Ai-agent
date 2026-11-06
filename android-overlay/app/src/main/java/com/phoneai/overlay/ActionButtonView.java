package com.phoneai.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * 面板右邊的圓形按鈕：沒字時是麥克風（點一下開始聽）、正在聽時變成藍色實心圓（點一下停止）、有字時是白色線條的紙飛機（送出）。
 * 倒數送出時外面有一圈細線慢慢縮短（{@link #setProgress} 1→0）。
 */
public class ActionButtonView extends View {
    public static final int MIC = 0, LISTENING = 1, SEND = 2;

    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path body = new Path();
    private final RectF oval = new RectF();
    private final float d;
    private float progress;
    private int mode = MIC;
    private int accent = 0xFF4F6BED;

    /** 正在聽時的實心圓顏色（主題主色）。 */
    public void setAccent(int c) {
        accent = c;
        invalidate();
    }

    public ActionButtonView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        line.setColor(0xFFFFFFFF);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        fill.setStyle(Paint.Style.FILL);
        ring.setColor(0x99FFFFFF);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeCap(Paint.Cap.ROUND);
        ring.setStrokeWidth(2 * d);
    }

    public void setMode(int m) {
        if (mode == m) return;
        mode = m;
        setContentDescription(m == SEND ? L.t("送出") : m == LISTENING ? L.t("停止聆聽") : L.t("開始說話"));
        invalidate();
    }

    /** 0 = 不顯示；1 = 整圈。 */
    public void setProgress(float p) {
        progress = Math.max(0f, Math.min(1f, p));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), box = Math.min(w, h), cx = w / 2f, cy = h / 2f;
        if (mode == LISTENING || mode == SEND) {   // 正在聽、可以送出：主色實心圓（Material 3 的 filled 圖示按鈕）
            fill.setColor(accent);
            c.drawCircle(cx, cy, box / 2f - 2 * d, fill);
        }   // 待機（MIC）：不畫圓底。Material 3 搜尋列的尾端圖示是單純的圖示按鈕，膠囊裡再放一個灰圓像圓中有圓；只有正在聽與送出才是實心圓
        if (mode == SEND) drawPlane(c, box); else drawMic(c, cx, cy, box);
        if (progress > 0f) {
            float pad = 2 * d;
            oval.set(pad, pad, w - pad, h - pad);
            c.drawArc(oval, -90f, 360f * progress, false, ring);
        }
    }

    private final Path[] micPaths = IconView.pathsOf("mic");   // 跟 App 其他地方同一組線條圖示

    private void drawMic(Canvas c, float cx, float cy, float box) {
        float s = box * 0.56f / 24f;
        c.save();
        c.translate(cx - 12f * s, cy - 12f * s);
        c.scale(s, s);
        line.setStrokeWidth(1.9f);   // 座標已經縮放，這是圖示座標裡的線寬
        for (Path p : micPaths) c.drawPath(p, line);
        c.restore();
    }

    private void drawPlane(Canvas c, float box) {
        float s = box * 0.46f;
        float ox = (getWidth() - s) / 2f, oy = (getHeight() - s) / 2f;
        line.setStrokeWidth(Math.max(1.6f * d, s * 0.075f));
        body.reset();
        // 外框：左上、尖端（正右方）、左下、內凹點，封閉
        body.moveTo(ox + 0.04f * s, oy + 0.10f * s);
        body.lineTo(ox + 0.98f * s, oy + 0.50f * s);
        body.lineTo(ox + 0.04f * s, oy + 0.90f * s);
        body.lineTo(ox + 0.26f * s, oy + 0.50f * s);
        body.close();
        c.drawPath(body, line);
        c.drawLine(ox + 0.26f * s, oy + 0.50f * s, ox + 0.98f * s, oy + 0.50f * s, line);   // 摺痕
    }
}
