package com.phoneai.overlay;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

/** 「＋」鈕點開的小選單（深色圓角卡片，長在鈕的上方）：選要在目前畫面還是背景螢幕做事，或上傳圖片。 */
final class PlusMenu {
    private PlusMenu() { }

    static PopupWindow show(Activity act, View anchor, boolean showBg, boolean bgOn,
                            Runnable onMain, Runnable onBg, Runnable onPick, Runnable onCamera, Runnable onDismiss) {
        final float d = act.getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding((int) (6 * d), (int) (6 * d), (int) (6 * d), (int) (6 * d));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF2202327);
        bg.setCornerRadius(24 * d);
        bg.setStroke((int) Math.max(1, d), 0x33FFFFFF);
        box.setBackground(bg);

        final PopupWindow pw = new PopupWindow(box, (int) (270 * d), -2, true);
        if (showBg) {
            box.addView(item(act, L.t("在這個畫面操作"), L.t("目前正在用的螢幕"), !bgOn, () -> { pw.dismiss(); onMain.run(); }));
            box.addView(item(act, L.t("在背景螢幕操作"), L.t("隱形的第二個螢幕，不打擾你"), bgOn, () -> { pw.dismiss(); onBg.run(); }));
            View div = new View(act);
            div.setBackgroundColor(0x22FFFFFF);
            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(-1, (int) Math.max(1, d));
            dp.setMargins((int) (12 * d), (int) (4 * d), (int) (12 * d), (int) (4 * d));
            box.addView(div, dp);
        }
        box.addView(item(act, L.t("拍照"), L.t("用相機拍一張，連同問題一起問"), false, () -> { pw.dismiss(); onCamera.run(); }));
        box.addView(item(act, L.t("上傳圖片"), L.t("從相簿選，最多 4 張"), false, () -> { pw.dismiss(); onPick.run(); }));

        pw.setOutsideTouchable(true);
        pw.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        pw.setElevation(14 * d);
        pw.setOnDismissListener(() -> { if (onDismiss != null) onDismiss.run(); });
        box.measure(View.MeasureSpec.makeMeasureSpec((int) (270 * d), View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
        // 長在鈕的正上方（往左對齊）
        pw.showAsDropDown(anchor, 0, -(anchor.getHeight() + box.getMeasuredHeight() + (int) (10 * d)), Gravity.START);
        return pw;
    }

    private static View item(Activity act, String title, String sub, boolean checked, Runnable go) {
        final float d = act.getResources().getDisplayMetrics().density;
        LinearLayout r = new LinearLayout(act);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight((int) (56 * d));
        r.setPadding((int) (14 * d), (int) (8 * d), (int) (14 * d), (int) (8 * d));
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(18 * d);
        r.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), null, mask));
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(act);
        t.setText(title);
        t.setTextColor(Color.WHITE);
        t.setTextSize(16);
        col.addView(t);
        TextView s = new TextView(act);
        s.setText(sub);
        s.setTextColor(0x99FFFFFF);
        s.setTextSize(12);
        col.addView(s);
        r.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        if (checked) {
            TextView ck = new TextView(act);
            ck.setText("✓");
            ck.setTextColor(Color.WHITE);
            ck.setTextSize(18);
            r.addView(ck);
        }
        r.setOnClickListener(v -> go.run());
        return r;
    }
}
