package com.phoneai.overlay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

/**
 * 圈選的畫布：畫截圖（可以雙指縮放、移動），單指隨便圈一圈（流動發光的線），放手後自動變成一個長方形
 * （像 Google 的「圈選搜尋」）：四個角有白色的角標，可以拖角或邊縮放、拖裡面移動；在長方形外面再畫一圈就重選。
 * 每次圈完或調整完回報圖片座標的範圍；點一下（沒有拖動）回報 onTap。
 */
final class CircleCanvas extends View {
    interface Listener {
        void onSelected(RectF box);   // 選好了或調整完了（圖片座標）
        void onCleared();             // 開始畫新的一圈，舊的選取收掉
        void onTap();                 // 只是點一下（沒有拖動）
    }

    private Bitmap bmp;
    private final float d;
    private final Listener listener;

    private static final float MAX_ZOOM = 6f, MIN_ZOOM = 0.3f;
    private final Path path = new Path();                  // 正在畫的線（圖片座標）
    private final Path box = new Path();
    private final Path brackets = new Path();
    private final RectF sel = new RectF();                 // 選取的長方形（圖片座標）
    private final RectF tmp = new RectF();
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG), glow = new Paint(Paint.ANTI_ALIAS_FLAG), core = new Paint(Paint.ANTI_ALIAS_FLAG),
            dim = new Paint(), pic = new Paint(Paint.FILTER_BITMAP_FLAG), dot = new Paint(Paint.ANTI_ALIAS_FLAG), brk = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix m = new Matrix(), gm = new Matrix();
    private LinearGradient gradient;
    private float gradLen = 1f;
    private float s0 = 1f, zoom = 1f, tx, ty;
    private float minX, minY, maxX, maxY, downX, downY, headX, headY, lastX, lastY;
    private boolean has, done, drawing, zooming, adjusting;
    private boolean eL, eT, eR, eB, moving;               // 正在調整的邊／角，或整個移動
    private long doneAt;
    private float lastDist, lastMx, lastMy;

    CircleCanvas(Context c, Bitmap bitmap, Listener l) {
        super(c);
        bmp = bitmap;
        d = c.getResources().getDisplayMetrics().density;
        listener = l;
        for (Paint p : new Paint[]{line, glow, core, brk}) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }
        core.setColor(0xFFFFFFFF);
        brk.setColor(0xFFFFFFFF);
        dim.setColor(0xFF000000);
        dot.setColor(0xFFFFFFFF);
    }

    /** 換成另一張截圖：重新算填滿的比例，清掉舊的選取。 */
    void setShot(Bitmap b) {
        bmp = b;
        clearSel();
        if (getWidth() > 0) onSizeChanged(getWidth(), getHeight(), getWidth(), getHeight());
        else requestLayout();
        invalidate();
    }

    private float scale() { return s0 * zoom; }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        s0 = Math.min(w / (float) bmp.getWidth(), h / (float) bmp.getHeight());
        zoom = 1f;
        tx = (w - bmp.getWidth() * s0) / 2f;
        ty = (h - bmp.getHeight() * s0) / 2f;
        gradLen = Math.max(1f, bmp.getWidth() * 0.7f);
        gradient = new LinearGradient(0, 0, gradLen, gradLen * 0.4f,
                new int[]{0xFF4285F4, 0xFFA142F4, 0xFFFF6BB5, 0xFF2DE2E6, 0xFF4285F4}, null, Shader.TileMode.REPEAT);
    }

    private void clamp() {
        float S = scale(), iw = bmp.getWidth() * S, ih = bmp.getHeight() * S;
        tx = iw <= getWidth() ? (getWidth() - iw) / 2f : Math.max(getWidth() - iw, Math.min(0, tx));
        ty = ih <= getHeight() ? (getHeight() - ih) / 2f : Math.max(getHeight() - ih, Math.min(0, ty));
    }

    private float bx(float x) { return (x - tx) / scale(); }
    private float by(float y) { return (y - ty) / scale(); }

    void clearSel() {
        path.reset();
        has = done = adjusting = false;
        invalidate();
    }

    /** 手指按下的位置落在選取框的哪裡：角、邊、裡面，或外面（回 false）。 */
    private boolean hitSel(float x, float y) {
        float S = scale(), slop = 30 * d;
        float l = sel.left * S + tx, t = sel.top * S + ty, r = sel.right * S + tx, b = sel.bottom * S + ty;
        boolean inX = x > l - slop && x < r + slop, inY = y > t - slop && y < b + slop;
        if (!inX || !inY) return false;
        eL = Math.abs(x - l) < slop;
        eR = !eL && Math.abs(x - r) < slop;
        eT = Math.abs(y - t) < slop;
        eB = !eT && Math.abs(y - b) < slop;
        // 框很小時，邊的感應區會互相蓋住：寬或高小於兩倍感應區，就不分左右（或上下）邊，直接當作移動
        moving = !(eL || eR || eT || eB);
        return true;
    }

    private void adjust(float x, float y) {
        float S = scale();
        float dx = (x - lastX) / S, dy = (y - lastY) / S;
        lastX = x;
        lastY = y;
        float minSide = 56 * d / S, W = bmp.getWidth(), H = bmp.getHeight();
        if (moving) {
            float w = sel.width(), h = sel.height();
            float nl = Math.max(0, Math.min(W - w, sel.left + dx)), nt = Math.max(0, Math.min(H - h, sel.top + dy));
            sel.set(nl, nt, nl + w, nt + h);
        } else {
            if (eL) sel.left = Math.max(0, Math.min(sel.right - minSide, sel.left + dx));
            if (eR) sel.right = Math.min(W, Math.max(sel.left + minSide, sel.right + dx));
            if (eT) sel.top = Math.max(0, Math.min(sel.bottom - minSide, sel.top + dy));
            if (eB) sel.bottom = Math.min(H, Math.max(sel.top + minSide, sel.bottom + dy));
        }
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = e.getX();
                downY = lastY = e.getY();
                drawing = false;
                zooming = false;
                adjusting = done && hitSel(downX, downY);   // 按在長方形上：調整；按在外面：之後拖動就是重新圈
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:   // 第二根手指：改成縮放／移動畫面，正在畫或調整的不算
                if (drawing && !done) { path.reset(); has = false; drawing = false; }
                adjusting = false;
                zooming = true;
                lastDist = dist(e);
                lastMx = midX(e);
                lastMy = midY(e);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (zooming) {
                    if (e.getPointerCount() >= 2) {
                        float nd = dist(e), mx = midX(e), my = midY(e);
                        if (lastDist > 0) {
                            float nz = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * nd / lastDist));
                            float r = nz / zoom;
                            tx = mx - (lastMx - tx) * r;
                            ty = my - (lastMy - ty) * r;
                            zoom = nz;
                        }
                        clamp();
                        lastDist = nd;
                        lastMx = mx;
                        lastMy = my;
                        invalidate();
                    }
                    return true;
                }
                if (adjusting) {
                    adjust(e.getX(), e.getY());
                    return true;
                }
                if (!drawing) {
                    if (Math.hypot(e.getX() - downX, e.getY() - downY) < 8 * d) return true;   // 還沒真的在畫（只是點到）
                    drawing = true;                       // 開始畫新的一圈：舊的選取與提問列收掉
                    clearSel();
                    listener.onCleared();
                    path.reset();
                    path.moveTo(bx(downX), by(downY));
                    minX = maxX = bx(downX);
                    minY = maxY = by(downY);
                    has = true;
                }
                float px = bx(e.getX()), py = by(e.getY());
                headX = px;
                headY = py;
                path.lineTo(px, py);
                minX = Math.min(minX, px); maxX = Math.max(maxX, px);
                minY = Math.min(minY, py); maxY = Math.max(maxY, py);
                invalidate();
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                lastDist = 0;
                lastMx = e.getX(e.getPointerCount() > 1 && e.getActionIndex() == 0 ? 1 : 0);
                lastMy = e.getY(e.getPointerCount() > 1 && e.getActionIndex() == 0 ? 1 : 0);
                return true;
            case MotionEvent.ACTION_UP:
                if (adjusting && !zooming) {
                    adjusting = false;
                    listener.onSelected(new RectF(sel));
                } else if (drawing && has && !zooming) {
                    float S = scale();
                    if ((maxX - minX) * S < 40 * d && (maxY - minY) * S < 40 * d) {   // 太小：不算
                        clearSel();
                        listener.onTap();
                    } else {
                        // 線條變成長方形：外框加一點邊距，至少 56dp，不超出圖片
                        float pad = 10 * d / S, minSide = 56 * d / S;
                        sel.set(minX - pad, minY - pad, maxX + pad, maxY + pad);
                        if (sel.width() < minSide) sel.inset(-(minSide - sel.width()) / 2f, 0);
                        if (sel.height() < minSide) sel.inset(0, -(minSide - sel.height()) / 2f);
                        sel.intersect(0, 0, bmp.getWidth(), bmp.getHeight());
                        done = true;
                        doneAt = android.os.SystemClock.uptimeMillis();
                        listener.onSelected(new RectF(sel));
                    }
                } else if (!zooming && !drawing && done && !hitSel(e.getX(), e.getY()) && Math.hypot(e.getX() - downX, e.getY() - downY) < 8 * d) {
                    listener.onTap();   // 點在長方形外面
                }
                drawing = false;
                zooming = false;
                adjusting = false;
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
                drawing = false;
                zooming = false;
                adjusting = false;
                return true;
            default:
                return true;
        }
    }

    private float dist(MotionEvent e) { return (float) Math.hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1)); }
    private float midX(MotionEvent e) { return (e.getX(0) + e.getX(1)) / 2f; }
    private float midY(MotionEvent e) { return (e.getY(0) + e.getY(1)) / 2f; }

    /** 四個角的白色角標（圓角的 L 形），畫在長方形的角上。 */
    private void buildBrackets(float S) {
        float r = 18 * d / S, len = 34 * d / S;
        r = Math.min(r, Math.min(sel.width(), sel.height()) / 3f);
        len = Math.min(len, Math.min(sel.width(), sel.height()) / 2f);
        float l = sel.left, t = sel.top, rr = sel.right, b = sel.bottom;
        brackets.reset();
        brackets.moveTo(l, t + len); brackets.lineTo(l, t + r);
        tmp.set(l, t, l + 2 * r, t + 2 * r); brackets.arcTo(tmp, 180, 90); brackets.lineTo(l + len, t);
        brackets.moveTo(rr - len, t); brackets.lineTo(rr - r, t);
        tmp.set(rr - 2 * r, t, rr, t + 2 * r); brackets.arcTo(tmp, 270, 90); brackets.lineTo(rr, t + len);
        brackets.moveTo(rr, b - len); brackets.lineTo(rr, b - r);
        tmp.set(rr - 2 * r, b - 2 * r, rr, b); brackets.arcTo(tmp, 0, 90); brackets.lineTo(rr - len, b);
        brackets.moveTo(l + len, b); brackets.lineTo(l + r, b);
        tmp.set(l, b - 2 * r, l + 2 * r, b); brackets.arcTo(tmp, 90, 90); brackets.lineTo(l, b - len);
    }

    @Override
    protected void onDraw(Canvas c) {
        float S = scale();
        m.setScale(S, S);
        m.postTranslate(tx, ty);
        long now = android.os.SystemClock.uptimeMillis();
        c.save();
        c.concat(m);
        c.drawBitmap(bmp, 0, 0, pic);
        if (has) {
            float phase = (now % 2600L) / 2600f;
            float since = done ? now - doneAt : 0f;
            gm.setTranslate(phase * gradLen, 0);   // 光沿著線流動
            gradient.setLocalMatrix(gm);
            line.setShader(gradient);
            glow.setShader(gradient);
            Path shape = path;
            if (done) {   // 長方形：圈外變暗、發光的外框、白色角標
                float rad = 16 * d / S;
                box.reset();
                box.addRoundRect(sel, rad, rad, Path.Direction.CW);
                shape = box;
                dim.setAlpha((int) (0x99 * Math.min(1f, since / 300f)));
                c.save();
                c.clipOutPath(box);
                c.drawRect(0, 0, bmp.getWidth(), bmp.getHeight(), dim);
                c.restore();
            }
            float burst = done ? 1f + 0.9f * Math.max(0f, 1f - since / 600f) : 1f;
            float breathe = done ? 0.7f + 0.3f * (float) Math.sin(phase * 2 * Math.PI) : 0.85f;
            float[] w = {26f, 17f, 10f}, al = {0.16f, 0.26f, 0.42f};
            for (int i = 0; i < w.length; i++) {
                glow.setStrokeWidth(w[i] * d * burst / S);
                glow.setAlpha((int) (255 * al[i] * breathe * (done ? 1.2f : 1f)));
                c.drawPath(shape, glow);
            }
            line.setStrokeWidth((done ? 3f : 4.5f) * d * burst / S);
            c.drawPath(shape, line);
            if (!done) {
                core.setStrokeWidth(1.6f * d / S);
                core.setAlpha(0xCC);
                c.drawPath(shape, core);
            } else {
                buildBrackets(S);
                brk.setStrokeWidth(5 * d / S);
                c.drawPath(brackets, brk);
            }
            if (drawing && !done) {
                dot.setShader(null);
                dot.setAlpha(0x55);
                c.drawCircle(headX, headY, 16 * d / S, dot);
                dot.setAlpha(0xFF);
                c.drawCircle(headX, headY, 6 * d / S, dot);
            }
            postInvalidateOnAnimation();
        }
        c.restore();
    }
}
