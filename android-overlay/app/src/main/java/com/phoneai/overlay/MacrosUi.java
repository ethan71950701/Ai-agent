package com.phoneai.overlay;

import android.app.Activity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

/** 「記住的步驟」清單：看有哪些任務被記住、用過幾次，可以單獨刪除或全部清除。 */
final class MacrosUi {
    private MacrosUi() { }

    static void open(Activity a, Runnable changed) {
        List<String[]> items = Macros.list(a);
        if (items.isEmpty()) {
            new MaterialAlertDialogBuilder(a).setTitle(L.t("記住的步驟"))
                    .setMessage(L.t("還沒有記住任何任務。同一個任務成功做完一次後，AI 會把步驟記下來，下次同樣的話就直接重播，幾乎不花錢。"))
                    .setPositiveButton(L.t("知道了"), null).show();
            return;
        }
        String[] lines = new String[items.size()];
        for (int i = 0; i < lines.length; i++)
            lines[i] = items.get(i)[1] + "\n" + String.format(L.t("　%s 步，已重播 %s 次"), items.get(i)[2], items.get(i)[3]);
        new MaterialAlertDialogBuilder(a).setTitle(String.format(L.t("記住的步驟（%d 個）"), items.size()))
                .setItems(lines, (d, which) -> new MaterialAlertDialogBuilder(a).setTitle(items.get(which)[1])
                        .setMessage(L.t("刪除這個記住的步驟？下次同樣的任務會重新請 AI 一步步做。"))
                        .setPositiveButton(L.t("刪除"), (d2, w2) -> { Macros.remove(a, items.get(which)[0]); changed.run(); open(a, changed); })
                        .setNegativeButton(L.t("取消"), (d2, w2) -> open(a, changed)).show())
                .setNeutralButton(L.t("全部清除"), (d, w) -> { Macros.clear(a); changed.run(); })
                .setNegativeButton(L.t("關閉"), null).show();
    }
}
