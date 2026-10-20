package com.phoneai.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import androidx.core.graphics.PathParser;

import java.util.HashMap;
import java.util.Map;

/** 自己畫的線條向量圖示（24x24 座標、圓端點），取代 emoji；顏色可隨時換。 */
final class IconView extends View {
    private static final Map<String, String[]> P = new HashMap<>();
    private static void d(String name, String... paths) { P.put(name, paths); }
    static {
        d("home", "M4,11 L12,4 L20,11", "M6,10 V20 H18 V10");
        d("apps", "M4,4 H10 V10 H4 Z", "M14,4 H20 V10 H14 Z", "M4,14 H10 V20 H4 Z", "M14,14 H20 V20 H14 Z");
        d("tune", "M4,7 H20", "M4,17 H20", "M9,4.5 V9.5", "M15,14.5 V19.5");
        d("note", "M6,3 H18 V21 H6 Z", "M9,8 H15", "M9,12 H15", "M9,16 H13");
        d("clock", "M12,3 A9,9 0 1 1 11.99,3 Z", "M12,7 V12 L15.5,14");
        d("wave", "M4,10 V14", "M8,6 V18", "M12,3 V21", "M16,7 V17", "M20,10 V14");
        d("mic", "M12,3 A3,3 0 0 1 15,6 V12 A3,3 0 0 1 9,12 V6 A3,3 0 0 1 12,3 Z", "M5,11 A7,7 0 0 0 19,11", "M12,18 V21");
        d("layers", "M12,3 L21,8 L12,13 L3,8 Z", "M3,12 L12,17 L21,12", "M3,16 L12,21 L21,16");
        d("bell", "M6,16 V11 A6,6 0 0 1 18,11 V16 L20,18 H4 Z", "M10,21 H14");
        d("timer", "M12,6 A8,8 0 1 1 11.99,6 Z", "M12,10 V14", "M9,2 H15");
        d("sparkle", "M12,3 C12,8 16,12 21,12 C16,12 12,16 12,21 C12,16 8,12 3,12 C8,12 12,8 12,3 Z");
        d("terminal", "M4,5 H20 V19 H4 Z", "M7,10 L10,12.5 L7,15", "M12,16 H17");
        d("undo", "M9,7 L4,12 L9,17", "M4,12 H14 A5,5 0 0 1 14,22 H12");
        d("float", "M12,3 A9,9 0 1 1 11.99,3 Z", "M12,9 A3,3 0 1 1 11.99,9 Z");
        d("power", "M12,3 V12", "M6.5,6.5 A8,8 0 1 0 17.5,6.5");
        d("window", "M3,5 H21 V19 H3 Z", "M3,9 H21");
        d("volume", "M4,9 V15 H8 L13,19 V5 L8,9 Z", "M16.5,9 A4,4 0 0 1 16.5,15");
        d("sun", "M12,8 A4,4 0 1 1 11.99,8 Z", "M12,2 V4", "M12,20 V22", "M2,12 H4", "M20,12 H22", "M5,5 L6.5,6.5", "M17.5,17.5 L19,19", "M19,5 L17.5,6.5", "M6.5,17.5 L5,19");
        d("monitor", "M3,5 H21 V16 H3 Z", "M8,20 H16", "M12,16 V20");
        d("shield", "M12,3 L19,6 V12 C19,16 16,19 12,21 C8,19 5,16 5,12 V6 Z", "M9,12 L11.5,14.5 L15.5,9.5");
        d("flask", "M9,3 H15", "M10,3 V9 L4,19 A1,1 0 0 0 5,21 H19 A1,1 0 0 0 20,19 L14,9 V3");
        d("droplet", "M12,3 C12,3 5,11 5,15 A7,7 0 0 0 19,15 C19,11 12,3 12,3 Z");
        d("image", "M3,5 H21 V19 H3 Z", "M3,16 L8,11 L12,15 L15,12 L21,18", "M16,8.5 H16.01");
        d("target", "M12,3 A9,9 0 1 1 11.99,3 Z", "M12,8 A4,4 0 1 1 11.99,8 Z");
        d("list", "M8,6 H20", "M8,12 H20", "M8,18 H20", "M4,6 H4.01", "M4,12 H4.01", "M4,18 H4.01");
        d("refresh", "M20,12 A8,8 0 1 1 17.5,6.2", "M20,4 V9 H15");
        d("check", "M5,12.5 L10,17.5 L19,7");
        d("plus", "M12,5 V19", "M5,12 H19");
        d("pin", "M12,16 V22", "M8,3 H16 L15,10 L18,13 V16 H6 V13 L9,10 Z");
        d("circle", "M12,3 A9,9 0 1 1 11.99,3 Z");
        d("check_circle", "M12,3 A9,9 0 1 1 11.99,3 Z", "M8,12.5 L11,15.5 L16,9");
        d("download", "M12,4 V15", "M7,11 L12,16 L17,11", "M5,20 H19");
        d("chevron", "M9,6 L15,12 L9,18");
        d("chevron_down", "M6,9 L12,15 L18,9");
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path[] paths;

    IconView(Context c, String name, int color) {
        super(c);
        String[] src = P.get(name);
        if (src == null) src = P.get("sparkle");
        paths = new Path[src.length];
        for (int i = 0; i < src.length; i++) paths[i] = PathParser.createPathFromPathData(src[i]);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(1.9f);
        paint.setColor(color);
    }

    void setColor(int color) {
        paint.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float s = Math.min(getWidth(), getHeight()) / 24f;
        canvas.save();
        canvas.translate((getWidth() - 24 * s) / 2f, (getHeight() - 24 * s) / 2f);
        canvas.scale(s, s);
        for (Path p : paths) canvas.drawPath(p, paint);
        canvas.restore();
    }
}
