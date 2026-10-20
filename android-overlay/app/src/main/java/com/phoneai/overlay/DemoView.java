package com.phoneai.overlay;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** 教學頁上的循環小動畫（全部用畫的，沒有圖片檔）。kind：0 歡迎、1 叫出、2 語音／打字、3 操作、4 安全確認、5 設定清單。 */
final class DemoView extends View {
    private final int kind;
    private float t;   // 0..1 循環
    private final ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int cPrimary, cContainer, cOnContainer, cSurf, cDim, cOn, cTertiary;
    private final float d;

    DemoView(Context c, int kind) {
        super(c);
        this.kind = kind;
        d = c.getResources().getDisplayMetrics().density;
        cPrimary = Ui.attr(c, com.google.android.material.R.attr.colorPrimary);
        cContainer = Ui.attr(c, com.google.android.material.R.attr.colorPrimaryContainer);
        cOnContainer = Ui.attr(c, com.google.android.material.R.attr.colorOnPrimaryContainer);
        cSurf = Ui.attr(c, com.google.android.material.R.attr.colorSurfaceContainerHigh);
        cDim = Ui.attr(c, com.google.android.material.R.attr.colorOnSurfaceVariant);
        cOn = Ui.attr(c, com.google.android.material.R.attr.colorOnSurface);
        cTertiary = Ui.attr(c, com.google.android.material.R.attr.colorTertiary);
        anim.setDuration(4400);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> { t = (Float) a.getAnimatedValue(); invalidate(); });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        anim.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        anim.cancel();
        super.onDetachedFromWindow();
    }

    private static float ease(float x) { x = Math.max(0f, Math.min(1f, x)); return x * x * (3 - 2 * x); }
    private static float seg(float v, float a, float b) { return ease((v - a) / (b - a)); }   // v 在 [a,b] 之間 0→1

    private void phone(Canvas c, float cx, float cy, float w, float h) {
        RectF r = new RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
        p.setStyle(Paint.Style.FILL);
        p.setColor(cSurf);
        c.drawRoundRect(r, 20 * d, 20 * d, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3 * d);
        p.setColor(cDim);
        c.drawRoundRect(r, 20 * d, 20 * d, p);
        p.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), cx = w / 2, cy = h / 2;
        // 每圈開頭淡入、結尾淡出，循環接回去不會「啪」一下跳
        float alpha = seg(t, 0f, 0.08f) * (1f - seg(t, 0.9f, 1f));
        if (alpha < 0.99f) {   // 只有淡入淡出時才開離屏圖層（每格都開會掉幀）
            c.saveLayerAlpha(0, 0, w, h, Math.round(255 * alpha));
            drawScene(c, w, h, cx, cy);
            c.restore();
        } else {
            drawScene(c, w, h, cx, cy);
        }
    }

    private void drawScene(Canvas c, float w, float h, float cx, float cy) {
        switch (kind) {
            case 0: welcome(c, cx, cy); break;
            case 1: invoke(c, cx, cy, h); break;
            case 2: voice(c, cx, cy); break;
            case 3: operate(c, cx, cy, h); break;
            case 4: safety(c, cx, cy); break;
            case 5: checklist(c, cx, cy); break;
            case 10: rootShield(c, cx, cy); break;
            case 11: overlayPanel(c, cx, cy); break;
            case 12: bell(c, cx, cy); break;
            case 13: tapToggle(c, cx, cy); break;
            case 14: terminal(c, cx, cy); break;
            case 15: keyLock(c, cx, cy); break;
            default: checklist(c, cx, cy); break;
        }
    }

    private void star(Canvas c, float cx, float cy, float r, int color, float rot) {
        Path s = new Path();
        s.moveTo(cx, cy - r);
        s.cubicTo(cx, cy - r * 0.35f, cx + r * 0.35f, cy, cx + r, cy);
        s.cubicTo(cx + r * 0.35f, cy, cx, cy + r * 0.35f, cx, cy + r);
        s.cubicTo(cx, cy + r * 0.35f, cx - r * 0.35f, cy, cx - r, cy);
        s.cubicTo(cx - r * 0.35f, cy, cx, cy - r * 0.35f, cx, cy - r);
        c.save();
        c.rotate(rot, cx, cy);
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        c.drawPath(s, p);
        c.restore();
    }

    private void welcome(Canvas c, float cx, float cy) {
        for (int i = 0; i < 3; i++) {   // 三圈向外擴散的光環
            float k = (t + i / 3f) % 1f;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3 * d);
            p.setColor((cPrimary & 0xFFFFFF) | ((int) (150 * (1 - k)) << 24));
            c.drawCircle(cx, cy, (46 + 70 * k) * d, p);
        }
        float pulse = 1f + 0.08f * (float) Math.sin(t * 2 * Math.PI);
        star(c, cx, cy, 44 * d * pulse, cPrimary, t * 40f - 20f);
        star(c, cx + 52 * d, cy - 40 * d, 12 * d * (1f + 0.3f * (float) Math.sin(t * 4 * Math.PI)), cTertiary, 0);
        star(c, cx - 56 * d, cy + 36 * d, 9 * d * (1f + 0.3f * (float) Math.cos(t * 4 * Math.PI)), cTertiary, 0);
    }

    private void invoke(Canvas c, float cx, float cy, float h) {
        float pw = 120 * d, ph = Math.min(h - 20 * d, 190 * d);
        phone(c, cx, cy, pw, ph);
        // 右側電源鍵：0.05~0.25 被按住（凹進去），顏色變主色
        float press = seg(t, 0.05f, 0.15f) * (1f - seg(t, 0.3f, 0.4f));
        p.setStyle(Paint.Style.FILL);
        p.setColor(press > 0.3f ? cPrimary : cDim);
        c.drawRoundRect(new RectF(cx + pw / 2 - (2 - 3 * press) * d, cy - 30 * d, cx + pw / 2 + (5 - 3 * press) * d, cy + 4 * d), 3 * d, 3 * d, p);
        // 下方面板升起：0.3~0.55 彈出，0.85 以後收回
        float up = seg(t, 0.3f, 0.55f) * (1f - seg(t, 0.88f, 1f));
        float sheetH = ph * 0.42f;
        float top = cy + ph / 2 - sheetH * up;
        c.save();
        c.clipRect(cx - pw / 2 + 3 * d, cy - ph / 2 + 3 * d, cx + pw / 2 - 3 * d, cy + ph / 2 - 3 * d);
        p.setColor(cContainer);
        c.drawRoundRect(new RectF(cx - pw / 2 + 8 * d, top, cx + pw / 2 - 8 * d, top + sheetH + 30 * d), 16 * d, 16 * d, p);
        // 光球
        float orb = (10 + 3 * (float) Math.sin(t * 8 * Math.PI)) * d;
        p.setColor(cPrimary);
        c.drawCircle(cx, top + 26 * d, orb, p);
        p.setColor(cOnContainer);
        p.setAlpha(80);
        c.drawRoundRect(new RectF(cx - 40 * d, top + 50 * d, cx + 40 * d, top + 62 * d), 6 * d, 6 * d, p);
        p.setAlpha(255);
        c.restore();
        // 浮動按鈕（另一種叫法）：左側小圓，輕輕漂浮
        p.setColor(cPrimary);
        c.drawCircle(cx - pw / 2 - 34 * d, cy + (float) Math.sin(t * 2 * Math.PI) * 6 * d, 12 * d, p);
    }

    private void voice(Canvas c, float cx, float cy) {
        // 麥克風（上）
        float my = cy - 38 * d;
        p.setStyle(Paint.Style.FILL);
        p.setColor(cPrimary);
        c.drawRoundRect(new RectF(cx - 12 * d, my - 26 * d, cx + 12 * d, my + 10 * d), 12 * d, 12 * d, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3 * d);
        p.setStrokeCap(Paint.Cap.ROUND);
        c.drawArc(new RectF(cx - 20 * d, my - 18 * d, cx + 20 * d, my + 22 * d), 0, 180, false, p);
        c.drawLine(cx, my + 22 * d, cx, my + 32 * d, p);
        // 聲波（左右各 3 條）
        for (int i = 0; i < 6; i++) {
            float side = i < 3 ? -1 : 1;
            int k = i % 3;
            float amp = (6 + 10 * Math.abs((float) Math.sin(t * 6 * Math.PI + k * 1.3f + i))) * d;
            float x = cx + side * (34 + k * 12) * d;
            p.setColor((cPrimary & 0xFFFFFF) | ((int) (255 - k * 60) << 24));
            c.drawLine(x, my - amp, x, my + amp, p);
        }
        // 文字逐行出現（下）
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 3; i++) {
            float k = seg(t, 0.15f + i * 0.2f, 0.35f + i * 0.2f);
            float wlen = (i == 2 ? 70 : 130) * d * k;
            p.setColor((cDim & 0xFFFFFF) | (170 << 24));
            c.drawRoundRect(new RectF(cx - 65 * d, cy + 34 * d + i * 16 * d, cx - 65 * d + wlen, cy + 42 * d + i * 16 * d), 4 * d, 4 * d, p);
        }
    }

    private void operate(Canvas c, float cx, float cy, float h) {
        float pw = 130 * d, ph = Math.min(h - 20 * d, 190 * d);
        phone(c, cx, cy, pw, ph);
        // 邊緣光暈：色相在主色與第三色之間流動
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(4 * d);
        p.setColor(blend(cPrimary, cTertiary, 0.5f + 0.5f * (float) Math.sin(t * 2 * Math.PI)));
        p.setAlpha(150);
        c.drawRoundRect(new RectF(cx - pw / 2 + 2 * d, cy - ph / 2 + 2 * d, cx + pw / 2 - 2 * d, cy + ph / 2 - 2 * d), 18 * d, 18 * d, p);
        p.setAlpha(255);
        // 3x3 App 方塊
        float gx = cx - 36 * d, gy = cy - 50 * d, step = 36 * d;
        p.setStyle(Paint.Style.FILL);
        for (int r = 0; r < 3; r++) for (int k = 0; k < 3; k++) {
            p.setColor((cDim & 0xFFFFFF) | (90 << 24));
            c.drawRoundRect(new RectF(gx + k * step - 11 * d, gy + r * step - 11 * d, gx + k * step + 11 * d, gy + r * step + 11 * d), 7 * d, 7 * d, p);
        }
        // 漣漪＋游標：從左上走到中間再到右下，每到一處擴一圈
        float[][] pts = {{0, 0}, {1, 1}, {2, 2}};
        float pos = t * 3f;
        int i = Math.min(2, (int) pos);
        int j = Math.min(2, i + 1);
        float mv = seg(pos - i, 0.45f, 1f);
        float px = gx + (pts[i][0] + (pts[j][0] - pts[i][0]) * mv) * step, py = gy + (pts[i][1] + (pts[j][1] - pts[i][1]) * mv) * step;
        float rip = seg(pos - i, 0.1f, 0.45f);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2.5f * d);
        p.setColor((cPrimary & 0xFFFFFF) | ((int) (200 * (1 - rip)) << 24));
        c.drawCircle(gx + pts[i][0] * step, gy + pts[i][1] * step, (6 + 22 * rip) * d, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(cPrimary);
        c.drawCircle(px, py, 6 * d, p);
    }

    private void safety(Canvas c, float cx, float cy) {
        // 小對話框彈出，下面兩顆按鈕；「是」被按下時打勾描線
        float pop = seg(t, 0.05f, 0.25f) * (1f - seg(t, 0.9f, 1f));
        float bw = 150 * d, bh = 100 * d;
        c.save();
        c.scale(pop, pop, cx, cy);
        p.setStyle(Paint.Style.FILL);
        p.setColor(cSurf);
        c.drawRoundRect(new RectF(cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2), 24 * d, 24 * d, p);
        p.setColor((cDim & 0xFFFFFF) | (170 << 24));
        c.drawRoundRect(new RectF(cx - 50 * d, cy - 34 * d, cx + 50 * d, cy - 26 * d), 4 * d, 4 * d, p);
        c.drawRoundRect(new RectF(cx - 36 * d, cy - 18 * d, cx + 36 * d, cy - 10 * d), 4 * d, 4 * d, p);
        boolean yes = t > 0.55f && t < 0.9f;
        p.setColor(yes ? cPrimary : cContainer);
        c.drawRoundRect(new RectF(cx + 2 * d, cy + 8 * d, cx + 62 * d, cy + 38 * d), 15 * d, 15 * d, p);
        p.setColor(cContainer);
        c.drawRoundRect(new RectF(cx - 62 * d, cy + 8 * d, cx - 2 * d, cy + 38 * d), 15 * d, 15 * d, p);
        c.restore();
        // 打勾：0.55~0.75 描線
        float k = seg(t, 0.55f, 0.75f);
        if (k > 0 && t < 0.9f) {
            Path chk = new Path();
            chk.moveTo(cx + 22 * d, cy + 24 * d);
            chk.lineTo(cx + 30 * d, cy + 31 * d);
            chk.lineTo(cx + 44 * d, cy + 16 * d);
            Path part = new Path();
            PathMeasure pm = new PathMeasure(chk, false);
            pm.getSegment(0, pm.getLength() * k, part, true);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3 * d);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(Ui.attr(getContext(), com.google.android.material.R.attr.colorOnPrimary));
            c.drawPath(part, p);
        }
    }

    private void checklist(Canvas c, float cx, float cy) {
        String[] names = {"懸浮層", "通知", "無障礙", "API Key"};
        for (int i = 0; i < 4; i++) {
            float y = cy - 66 * d + i * 44 * d;
            float k = seg(t, 0.1f + i * 0.18f, 0.25f + i * 0.18f) * (1f - seg(t, 0.92f, 1f));
            p.setStyle(Paint.Style.FILL);
            p.setColor(cSurf);
            c.drawRoundRect(new RectF(cx - 90 * d, y - 18 * d, cx + 90 * d, y + 18 * d), 18 * d, 18 * d, p);
            p.setColor(k > 0.6f ? cPrimary : cContainer);
            c.drawCircle(cx - 66 * d, y, 11 * d, p);
            if (k > 0.05f) {
                Path chk = new Path();
                chk.moveTo(cx - 72 * d, y);
                chk.lineTo(cx - 67 * d, y + 5 * d);
                chk.lineTo(cx - 59 * d, y - 5 * d);
                Path part = new Path();
                PathMeasure pm = new PathMeasure(chk, false);
                pm.getSegment(0, pm.getLength() * k, part, true);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2.5f * d);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setColor(Ui.attr(getContext(), com.google.android.material.R.attr.colorOnPrimary));
                c.drawPath(part, p);
            }
            p.setStyle(Paint.Style.FILL);
            p.setColor(cOn);
            p.setTextSize(15 * d);
            c.drawText(names[i], cx - 46 * d, y + 5 * d, p);
        }
    }

    // ---- 設定導覽用的小動畫（kind 10～15） ----

    private void rootShield(Canvas c, float cx, float cy) {
        float s = 1f + 0.04f * (float) Math.sin(t * 2 * Math.PI);
        float r = 66 * d * s;
        Path sh = new Path();
        sh.moveTo(cx, cy - r);
        sh.lineTo(cx + r * 0.85f, cy - r * 0.6f);
        sh.lineTo(cx + r * 0.85f, cy + r * 0.1f);
        sh.quadTo(cx + r * 0.7f, cy + r * 0.7f, cx, cy + r);
        sh.quadTo(cx - r * 0.7f, cy + r * 0.7f, cx - r * 0.85f, cy + r * 0.1f);
        sh.lineTo(cx - r * 0.85f, cy - r * 0.6f);
        sh.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(cContainer);
        c.drawPath(sh, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(4 * d);
        p.setColor(cPrimary);
        c.drawPath(sh, p);
        float k = seg(t, 0.3f, 0.65f) * (1f - seg(t, 0.92f, 1f));
        if (k > 0) {
            Path ck = new Path();
            ck.moveTo(cx - 24 * d, cy);
            ck.lineTo(cx - 7 * d, cy + 17 * d);
            ck.lineTo(cx + 26 * d, cy - 15 * d);
            PathMeasure pm = new PathMeasure(ck, false);
            Path part = new Path();
            pm.getSegment(0, pm.getLength() * k, part, true);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(6 * d);
            p.setColor(cPrimary);
            c.drawPath(part, p);
        }
    }

    private void overlayPanel(Canvas c, float cx, float cy) {
        // 背後是一個 App 的畫面，懸浮層權限打開後，面板就會從下面升上來
        float w = 180 * d, h = 200 * d;
        RectF app = new RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
        p.setStyle(Paint.Style.FILL);
        p.setColor(cSurf);
        c.drawRoundRect(app, 18 * d, 18 * d, p);
        p.setColor((cDim & 0xFFFFFF) | (110 << 24));
        for (int i = 0; i < 4; i++) c.drawRoundRect(new RectF(cx - 60 * d, cy - 60 * d + i * 26 * d, cx + 50 * d, cy - 48 * d + i * 26 * d), 5 * d, 5 * d, p);
        float up = seg(t, 0.2f, 0.5f) * (1f - seg(t, 0.85f, 1f));
        float ph = 90 * d;
        float top = cy + h / 2 - ph * up;
        c.save();
        c.clipRect(app);
        p.setColor(cContainer);
        c.drawRoundRect(new RectF(cx - w / 2 + 8 * d, top, cx + w / 2 - 8 * d, top + ph + 40 * d), 16 * d, 16 * d, p);
        boolean on = t > 0.55f && t < 0.9f;
        RectF sw = new RectF(cx + 22 * d, top + 22 * d, cx + 58 * d, top + 44 * d);
        p.setColor(on ? cPrimary : cDim);
        c.drawRoundRect(sw, 11 * d, 11 * d, p);
        p.setColor(Ui.attr(getContext(), com.google.android.material.R.attr.colorSurface));
        float kx = on ? sw.right - 11 * d : sw.left + 11 * d;
        c.drawCircle(kx, sw.centerY(), 8 * d, p);
        c.restore();
    }

    private void bell(Canvas c, float cx, float cy) {
        float swing = (float) Math.sin(t * 6 * Math.PI) * 14f * (1f - seg(t, 0.7f, 1f));
        c.save();
        c.rotate(swing, cx, cy - 40 * d);
        p.setStyle(Paint.Style.FILL);
        p.setColor(cPrimary);
        RectF body = new RectF(cx - 36 * d, cy - 50 * d, cx + 36 * d, cy + 26 * d);
        c.drawRoundRect(body, 36 * d, 36 * d, p);
        c.drawRect(new RectF(cx - 40 * d, cy + 18 * d, cx + 40 * d, cy + 30 * d), p);
        c.drawCircle(cx, cy - 56 * d, 6 * d, p);
        c.drawCircle(cx, cy + 40 * d, 9 * d, p);
        c.restore();
        float pop = seg(t, 0.45f, 0.6f) * (1f - seg(t, 0.9f, 1f));
        p.setColor(cTertiary);
        c.drawCircle(cx + 36 * d, cy - 40 * d, 11 * d * pop, p);
    }

    private void tapToggle(Canvas c, float cx, float cy) {
        // 三列設定，一隻手指點中間那一列的開關，開關滑到「開」
        float rowW = 180 * d, rowH = 40 * d;
        for (int i = 0; i < 3; i++) {
            float y = cy - 56 * d + i * 56 * d;
            p.setStyle(Paint.Style.FILL);
            p.setColor(cSurf);
            c.drawRoundRect(new RectF(cx - rowW / 2, y - rowH / 2, cx + rowW / 2, y + rowH / 2), 16 * d, 16 * d, p);
            boolean on = i == 1 && t > 0.5f;
            float tx = cx + rowW / 2 - 36 * d;
            RectF sw = new RectF(tx, y - 10 * d, tx + 32 * d, y + 10 * d);
            p.setColor(on ? cPrimary : cDim);
            c.drawRoundRect(sw, 10 * d, 10 * d, p);
            p.setColor(Ui.attr(getContext(), com.google.android.material.R.attr.colorSurface));
            c.drawCircle(on ? sw.right - 10 * d : sw.left + 10 * d, y, 7 * d, p);
        }
        float fx = seg(t, 0.1f, 0.4f);
        float fy = cy - 56 * d + 56 * d;
        float px = cx + 60 * d + (1 - fx) * 40 * d;
        float py = fy + 70 * d * (1 - fx);
        float tap = seg(t, 0.4f, 0.5f);
        p.setColor(cOnContainer);
        p.setAlpha(120);
        c.drawCircle(px, py, 9 * d, p);
        p.setAlpha(255);
        float rip = seg(t, 0.45f, 0.75f);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2.5f * d);
        p.setColor((cPrimary & 0xFFFFFF) | ((int) (200 * (1 - rip)) << 24));
        c.drawCircle(px, py, (6 + 20 * rip) * d * tap, p);
    }

    private void terminal(Canvas c, float cx, float cy) {
        float w = 200 * d, h = 130 * d;
        RectF r = new RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
        p.setStyle(Paint.Style.FILL);
        p.setColor(cSurf);
        c.drawRoundRect(r, 14 * d, 14 * d, p);
        String cmd = "$ python ~/update_app.py";
        int n = Math.round(cmd.length() * seg(t, 0.05f, 0.55f));
        p.setTypeface(android.graphics.Typeface.MONOSPACE);
        p.setTextSize(13 * d);
        p.setColor(cOn);
        c.drawText(cmd.substring(0, n), r.left + 12 * d, cy - 8 * d, p);
        if (t % 0.5f < 0.25f && t < 0.6f) c.drawRect(r.left + 12 * d + p.measureText(cmd.substring(0, n)), cy - 22 * d, r.left + 19 * d + p.measureText(cmd.substring(0, n)), cy - 6 * d, p);
        float ok = seg(t, 0.62f, 0.75f);
        if (ok > 0) {
            p.setColor(cPrimary);
            c.drawText("✓ 完成", r.left + 12 * d, cy + 18 * d, p);
        }
        p.setTypeface(null);
    }

    private void keyLock(Canvas c, float cx, float cy) {
        // 鑰匙插進鎖孔，轉一下，鎖變成主色
        float k = seg(t, 0.1f, 0.5f);
        float kx = cx - 90 * d + k * 60 * d;
        float turn = seg(t, 0.55f, 0.75f) * 90f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(t > 0.75f ? cPrimary : cDim);
        c.drawRoundRect(new RectF(cx + 10 * d, cy - 26 * d, cx + 62 * d, cy + 26 * d), 10 * d, 10 * d, p);
        p.setColor(Ui.attr(getContext(), com.google.android.material.R.attr.colorSurface));
        c.save();
        c.rotate(turn, cx + 36 * d, cy);
        c.drawCircle(cx + 36 * d, cy, 5 * d, p);
        c.restore();
        c.save();
        c.rotate(turn, kx, cy);
        p.setColor(cTertiary);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(5 * d);
        p.setStrokeCap(Paint.Cap.ROUND);
        c.drawCircle(kx - 14 * d, cy, 9 * d, p);
        c.drawLine(kx - 5 * d, cy, cx + 8 * d, cy, p);
        c.drawLine(cx - 2 * d, cy, cx - 2 * d, cy + 8 * d, p);
        c.restore();
    }

    private static int blend(int a, int b, float k) {
        int ra = (a >> 16) & 255, ga = (a >> 8) & 255, ba = a & 255, rb = (b >> 16) & 255, gb = (b >> 8) & 255, bb = b & 255;
        return 0xFF000000 | ((int) (ra + (rb - ra) * k) << 16) | ((int) (ga + (gb - ga) * k) << 8) | (int) (ba + (bb - ba) * k);
    }
}
