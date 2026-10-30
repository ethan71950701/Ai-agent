package com.phoneai.overlay;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** 看得見方框的多選清單（系統的 setMultiChoiceItems 在這個主題下畫不出勾選框），上面顯示「已選 N 個」。 */
final class CheckDialog {
    private CheckDialog() { }

    static void show(Activity a, String title, String[] names, boolean[] on, Runnable onSave) {
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(a, 20);
        col.setPadding(pad, pad / 2, pad, 0);
        final TextView count = Ui.body(a, "");
        col.addView(count);
        final ListView lv = new ListView(a);
        lv.setDivider(null);
        lv.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return names.length; }
            @Override public Object getItem(int i) { return names[i]; }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View v, ViewGroup parent) {
                MaterialCheckBox cb = v instanceof MaterialCheckBox ? (MaterialCheckBox) v : new MaterialCheckBox(a);
                cb.setOnCheckedChangeListener(null);   // 重用的列先拿掉舊的監聽，免得設定狀態時誤觸發
                cb.setText(names[i]);
                cb.setTextSize(15);
                cb.setChecked(on[i]);
                cb.setOnCheckedChangeListener((b, checked) -> { on[i] = checked; count.setText(summary(on)); });
                return cb;
            }
        });
        count.setText(summary(on));
        col.addView(lv, new LinearLayout.LayoutParams(-1, (int) (a.getResources().getDisplayMetrics().heightPixels * 0.55f)));
        new MaterialAlertDialogBuilder(a).setTitle(title).setView(col)
                .setPositiveButton(L.t("儲存"), (d, w) -> onSave.run())
                .setNegativeButton(L.t("取消"), null).show();
    }

    private static String summary(boolean[] on) {
        int n = 0;
        for (boolean b : on) if (b) n++;
        return String.format(L.t("已選 %d 個"), n);
    }
}
