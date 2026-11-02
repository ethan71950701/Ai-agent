package com.phoneai.overlay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

/**
 * 圈選的畫布：畫截圖（可以雙指縮放、移動），單指畫圈（流動發光的線）。圈完回報圖片座標範圍；
 * 點一下（沒有拖動）回報 onTap。圈選畫面（CircleActivity）與助理面板共用。
 */
final class CircleCanvas extends View {
    interface Listener {
        void onSelected(RectF box);   // 圈完了（圖片座標）
        void onCleared();             // 開始畫新的一圈，舊的圈選收掉
        void onTap();                 // 只是點一下（沒有拖動）
    }

    private Bitmap bmp;
    private final float d;
    private final Listener listener;

    private static final float MAX_ZOOM = 6f, MIN_ZOOM = 0.3f;   // 可以縮到很小，讓整張圖縮在面板上面不被擋住
    private final Path path = new Path();                  // 圖片座標
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG), glow = new Paint(Paint.ANTI_ALIAS_FLAG), core = new Paint(Paint.ANTI_ALIAS_FLAG),
            dim = new Paint(), pic = new Paint(Paint.FILTER_BITMAP_FLAG), dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix m = new Matrix(), gm = new Matrix();
    private LinearGradient gradient;                       // 流動的光：藍、紫、粉、青（跟助理面板的極光同一組色）
    private float gradLen = 1f;
    private float s0 = 1f, zoom = 1f, tx, ty;               // s0 = 剛好填滿的比例；zoom = 使用者放大倍數；tx、ty = 平移（畫面像素）
    private float minX, minY, maxX, maxY, downX, downY, headX, headY;
    private boolean has, done, drawing, zooming;
    private long doneAt;
    private float lastDist, lastMx, lastMy;

    CircleCanvas(Context c, Bitmap bitmap, Listener l) {
        super(c);
        bmp = bitmap;
        d = c.getResources().getDisplayMetrics().density;
        listener = l;
        for (Paint p : new Paint[]{line, glow, core}) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }
        core.setColor(0xFFFFFFFF);
        dim.setColor(0xFF000000);
        dot.setColor(0xFFFFFFFF);
    }

    /** 換成另一張截圖（面板打開後才截好時用）：重新算填滿的比例，清掉舊的圈。 */
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

    private void clamp() {   // 放大後畫面邊緣不要拉出空白
        float S = scale(), iw = bmp.getWidth() * S, ih = bmp.getHeight() * S;
        tx = iw <= getWidth() ? (getWidth() - iw) / 2f : Math.max(getWidth() - iw, Math.min(0, tx));
        ty = ih <= getHeight() ? (getHeight() - ih) / 2f : Math.max(getHeight() - ih, Math.min(0, ty));
    }

    private float bx(float x) { return (x - tx) / scale(); }
    private float by(float y) { return (y - ty) / scale(); }

    void clearSel() {
        path.reset();
        has = done = false;
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                drawing = false;
                zooming = false;
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:   // 第二根手指下來：改成縮放／移動，正在畫的這一圈不算
                if (drawing && !done) { path.reset(); has = false; drawing = false; }
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
                if (!drawing) {
                    if (Math.hypot(e.getX() - downX, e.getY() - downY) < 8 * d) return true;   // 還沒真的在畫（只是點到）
                    drawing = true;                       // 開始畫新的一圈：舊的圈選與提問列收掉
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
                lastDist = 0;   // 放掉一根手指：剩下的手指不要接著畫，等全部放開
                lastMx = e.getX(e.getPointerCount() > 1 && e.getActionIndex() == 0 ? 1 : 0);
                lastMy = e.getY(e.getPointerCount() > 1 && e.getActionIndex() == 0 ? 1 : 0);
                return true;
            case MotionEvent.ACTION_UP:
                if (drawing && has && !zooming) {
                    float S = scale();
                    if ((maxX - minX) * S < 40 * d && (maxY - minY) * S < 40 * d) {   // 太小：不算圈選
                        clearSel();
                        listener.onTap();
                    } else {
                        path.close();
                        done = true;
                        doneAt = android.os.SystemClock.uptimeMillis();
                        listener.onSelected(new RectF(minX, minY, maxX, maxY));
                    }
                }
                drawing = false;
                zooming = false;
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
                drawing = false;
                zooming = false;
                return true;
            default:
                return true;
        }
    }

    private float dist(MotionEvent e) { return (float) Math.hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1)); }
    private float midX(MotionEvent e) { return (e.getX(0) + e.getX(1)) / 2f; }
    private float midY(MotionEvent e) { return (e.getY(0) + e.getY(1)) / 2f; }

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
            if (done) {   // 圈外慢慢變暗：只留圈選範圍亮著
                dim.setAlpha((int) (0x99 * Math.min(1f, since / 350f)));
                c.save();
                c.clipOutPath(path);
                c.drawRect(0, 0, bmp.getWidth(), bmp.getHeight(), dim);
                c.restore();
            }
            gm.setTranslate(phase * gradLen, 0);   // 光沿著線流動
            gradient.setLocalMatrix(gm);
            line.setShader(gradient);
            glow.setShader(gradient);
            // 剛圈完的那一瞬間線條變粗、光暈變亮，再回到平常；之後光暈跟著呼吸
            float burst = done ? 1f + 0.9f * Math.max(0f, 1f - since / 600f) : 1f;
            float breathe = done ? 0.7f + 0.3f * (float) Math.sin(phase * 2 * Math.PI) : 0.85f;
            float[] w = {26f, 17f, 10f}, al = {0.16f, 0.26f, 0.42f};
            for (int i = 0; i < w.length; i++) {   // 一層一層疊出柔和的光暈（不用模糊濾鏡）
                glow.setStrokeWidth(w[i] * d * burst / S);
                glow.setAlpha((int) (255 * al[i] * breathe * (done ? 1.2f : 1f)));
                c.drawPath(path, glow);
            }
            line.setStrokeWidth(4.5f * d * burst / S);
            c.drawPath(path, line);
            core.setStrokeWidth(1.6f * d / S);
            core.setAlpha(0xCC);
            c.drawPath(path, core);
            if (drawing && !done) {   // 手指前面一顆發亮的光點
                dot.setShader(null);
                dot.setAlpha(0x55);
                c.drawCircle(headX, headY, 16 * d / S, dot);
                dot.setAlpha(0xFF);
                c.drawCircle(headX, headY, 6 * d / S, dot);
            }
            postInvalidateOnAnimation();   // 有圈選時持續重畫，光才會流動
        }
        c.restore();
    }
}
