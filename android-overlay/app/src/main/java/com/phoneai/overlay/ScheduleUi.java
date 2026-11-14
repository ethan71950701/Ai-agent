package com.phoneai.overlay;

import android.app.Activity;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

/** 定時任務清單：點一項可以「立刻試跑一次」或刪除。（新增請對助理說，例如「每天早上 8 點幫我查…」。） */
final class ScheduleUi {
    private ScheduleUi() { }

    static String summary(Activity a) {
        int n = Schedule.load(a).length();
        return n == 0 ? L.t("目前沒有；對助理說「每天早上 8 點幫我查…」就能建立") : String.format(L.t("目前 %d 個（點進去看、試跑、刪除）"), n);
    }

    static void open(Activity a, Runnable done) {
        JSONArray items = Schedule.load(a);
        if (items.length() == 0) {
            new MaterialAlertDialogBuilder(a).setTitle(L.t("定時任務"))
                    .setMessage(L.t("目前沒有定時任務。\n\n對助理說，例如：「每天早上 8 點幫我查某某商品的價格」「每 3 天晚上 9 點查一次…」，確認後就會建立。只查公開網頁，看不到你的帳號或訂單。"))
                    .setPositiveButton(L.t("好"), null).show();
            return;
        }
        final String[] names = new String[items.length()];
        final int[] ids = new int[items.length()];
        for (int i = 0; i < items.length(); i++) {
            JSONObject it = items.optJSONObject(i);
            ids[i] = it.optInt("id");
            names[i] = Schedule.describe(it);
        }
        new MaterialAlertDialogBuilder(a).setTitle(L.t("定時任務")).setItems(names, (d, which) -> actions(a, ids[which], names[which], done))
                .setNegativeButton(L.t("關閉"), null).show();
    }

    private static void actions(Activity a, int id, String name, Runnable done) {
        new MaterialAlertDialogBuilder(a).setTitle(name)
                .setPositiveButton(L.t("立刻試跑一次"), (d, w) -> {
                    Toast.makeText(a, L.t("查詢中…"), Toast.LENGTH_SHORT).show();
                    new Thread(() -> {
                        final String r = Schedule.runNow(a, id);
                        a.runOnUiThread(() -> new MaterialAlertDialogBuilder(a).setTitle(L.t("試跑結果")).setMessage(r).setPositiveButton(L.t("好"), null).show());
                    }).start();
                })
                .setNegativeButton(L.t("刪除"), (d, w) -> {
                    Schedule.remove(a, id);
                    Toast.makeText(a, L.t("已刪除"), Toast.LENGTH_SHORT).show();
                    if (done != null) done.run();
                })
                .setNeutralButton(L.t("取消"), null).show();
    }
}
