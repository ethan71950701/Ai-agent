package com.phoneai.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

/** 螢幕邊緣的小圓鈕：點一下開 AskActivity，拖曳可移動（位置會記住）。 */
public class FloatButton extends View {
    interface OnTap { void run(); }

    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final WindowManager wm;
    private final WindowManager.LayoutParams lp;
    private final OnTap onTap;
    private final float slop;
    private float downX, downY, startX, startY;
    private boolean dragged;

    FloatButton(Context c, WindowManager wm, WindowManager.LayoutParams lp, OnTap onTap) {
        super(c);
        this.wm = wm;
        this.lp = lp;
        this.onTap = onTap;
        float d = c.getResources().getDisplayMetrics().density;
        slop = 8 * d;
        bg.setColor(0xCC0B6BCB);
        fg.setColor(0xFFFFFFFF);
        fg.setTextSize(16 * d);
        fg.setTextAlign(Paint.Align.CENTER);
        fg.setFakeBoldText(true);
    }

    @Override
    protected void onDraw(Canvas c) {
        float r = Math.min(getWidth(), getHeight()) / 2f;
        c.drawCircle(getWidth() / 2f, getHeight() / 2f, r, bg);
        c.drawText("AI", getWidth() / 2f, getHeight() / 2f + fg.getTextSize() / 3f, fg);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getRawX(); downY = e.getRawY();
                startX = lp.x; startY = lp.y;
                dragged = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                if (!dragged && Math.hypot(dx, dy) > slop) dragged = true;
                if (dragged) {
                    lp.x = (int) (startX + dx);
                    lp.y = (int) (startY + dy);
                    try { wm.updateViewLayout(this, lp); } catch (Exception ignored) { }
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (dragged) {
                    getContext().getSharedPreferences("cfg", Context.MODE_PRIVATE).edit()
                            .putInt("float_x", lp.x).putInt("float_y", lp.y).apply();
                } else {
                    performClick();
                    onTap.run();
                }
                return true;
            default:
                return super.onTouchEvent(e);
        }
    }

    @Override
    public boolean performClick() { return super.performClick(); }
}
