package com.phoneai.overlay;

import android.app.Activity;
import android.content.Intent;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/** 「功能 → AI 比較測試」的對話框：選要比的組合 → 確認 → 在背景跑 → 回到 App 時顯示結果。 */
final class AbTestUi {
    private AbTestUi() { }

    static void open(Activity a) {
        if (AbTest.running) {
            new MaterialAlertDialogBuilder(a).setTitle(L.t("AI 比較測試"))
                    .setMessage(L.t("正在跑：") + AbTest.progress)
                    .setPositiveButton(L.t("知道了"), null)
                    .setNegativeButton(L.t("停止測試"), (d, w) -> { AbTest.stopRequested = true; AgentLoop.cancel(); }).show();
            return;
        }
        String last = AbTest.last(a);
        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(a).setTitle(L.t("AI 比較測試"))
                .setMessage(L.t("同一批小任務（Wi-Fi 頁面、計算 123＋45、時鐘碼錶、設定搜尋藍牙），分別用不同的 AI 服務各跑一遍，比成功與否、步數、時間和預估花費。\n\n測試期間 AI 會操作你的手機（開設定、計算機、時鐘），請不要碰螢幕；每個任務跑完會回主畫面並強制關閉那個 App。需要 root 或 Shizuku。會用到各服務的額度或費用。"))
                .setPositiveButton(L.t("開始"), (d, w) -> pick(a))
                .setNegativeButton(L.t("取消"), null);
        if (last != null) b.setNeutralButton(L.t("上次結果"), (d, w) -> showResult(a, last));
        b.show();
    }

    private static void pick(Activity a) {
        if (!Root.available()) {
            new MaterialAlertDialogBuilder(a).setTitle(L.t("AI 比較測試")).setMessage(L.t("需要 root 或 Shizuku 才能在每個任務後強制關閉 App，讓每一組從同樣的起點開始。請先到「設定 → 執行模式」選 root 或 Shizuku。")).setPositiveButton(L.t("知道了"), null).show();
            return;
        }
        List<AbTest.Config> all = AbTest.candidates(a);
        if (all.size() < 2) {
            new MaterialAlertDialogBuilder(a).setTitle(L.t("AI 比較測試")).setMessage(L.t("至少要有兩種組合才能比較：到「功能 → AI 服務與 API Key」再設定一個服務（存好 Key）。")).setPositiveButton(L.t("知道了"), null).show();
            return;
        }
        String[] names = new String[all.size()];
        boolean[] on = new boolean[all.size()];
        for (int i = 0; i < names.length; i++) { names[i] = all.get(i).name; on[i] = i < 2; }
        new MaterialAlertDialogBuilder(a).setTitle(L.t("選要比較的組合"))
                .setMultiChoiceItems(names, on, (d, which, checked) -> on[which] = checked)
                .setPositiveButton(L.t("下一步"), (d, w) -> {
                    List<AbTest.Config> chosen = new ArrayList<>();
                    for (int i = 0; i < on.length; i++) if (on[i]) chosen.add(all.get(i));
                    if (chosen.isEmpty()) return;
                    confirmRun(a, chosen);
                })
                .setNegativeButton(L.t("取消"), null).show();
    }

    private static void confirmRun(Activity a, List<AbTest.Config> chosen) {
        int tasks = AbTest.TASKS.length;
        new MaterialAlertDialogBuilder(a).setTitle(L.t("開始比較？"))
                .setMessage(String.format(L.t("會跑 %d 組 × %d 個任務，AI 操作你的手機約 %d 到 %d 分鐘，期間請不要碰螢幕。要停可以按 Now Bar 的「停止」。免費方案可能遇到次數上限，會算失敗。"), chosen.size(), tasks, chosen.size() * 2, chosen.size() * 4))
                .setPositiveButton(L.t("開始"), (d, w) -> {
                    Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    new Thread(() -> {
                        AbTest.run(a.getApplicationContext(), chosen);
                        // 跑完把 App 帶回前景，顯示結果
                        a.getApplicationContext().startActivity(new Intent(a.getApplicationContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                    }, "ab-test").start();
                    a.getApplicationContext().startActivity(home);   // 先回桌面，之後任務自己開 App
                })
                .setNegativeButton(L.t("取消"), null).show();
    }

    static void showResult(Activity a, String text) {
        new MaterialAlertDialogBuilder(a).setTitle(L.t("AI 比較測試結果")).setMessage(text + L.t("\n\n成功判斷是粗略的（看最後畫面有沒有該有的文字），預估花費用我們內建的價格表，只是參考。")).setPositiveButton(L.t("知道了"), null).show();
    }

    /** MainActivity 回到前景時呼叫：有剛跑完還沒看的結果就顯示。 */
    static void showUnseen(Activity a) {
        String t = AbTest.unseen;
        if (t == null) return;
        AbTest.unseen = null;
        showResult(a, t);
    }
}
