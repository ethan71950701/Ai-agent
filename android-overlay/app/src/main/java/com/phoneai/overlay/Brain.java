package com.phoneai.overlay;

import android.app.Activity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.concurrent.CountDownLatch;

/**
 * 一句話的處理流程（在背景執行緒呼叫）：關鍵字命中固定指令直接做；否則交給 Haiku 路由判斷
 * （指令／開 App／操作畫面／聊天）。回傳要顯示給使用者的文字。首頁與問問看面板共用。
 */
final class Brain {
    private Brain() { }

    /** 在主執行緒跳確認框，背景執行緒等使用者按完。 */
    static boolean confirm(Activity act, String msg) {
        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] ok = {false};
        act.runOnUiThread(() -> new MaterialAlertDialogBuilder(act)
                .setMessage(msg)
                .setPositiveButton(L.t("確定"), (d, w) -> { ok[0] = true; latch.countDown(); })
                .setNegativeButton(L.t("取消"), (d, w) -> latch.countDown())
                .setOnCancelListener(d -> latch.countDown())
                .show());
        try { latch.await(); } catch (InterruptedException e) { return false; }
        return ok[0];
    }

    static String answer(Activity act, String t, AgentLoop.Ui ui) {
        String reply = run(act, t, ui);
        TaskLog.add(act, t, reply);   // 首頁與問問看面板共用這裡，所以兩邊的任務都會進歷史
        return reply;
    }

    private static String run(Activity act, String t, AgentLoop.Ui ui) {
        Assist.Route route = Assist.route(t);
        if (route.kind == Assist.FIXED) return runFixed(act, route);
        Router.Decision d = Router.decide(act, t);
        String kind = d == null ? Router.CHAT : d.kind;
        switch (kind) {
            case Router.CMD:
                return runFixed(act, Assist.fixed(d.cmdIndex));
            case Router.APP:
                Maintenance.suRun("monkey -p " + d.pkg + " -c android.intent.category.LAUNCHER 1");
                return L.t("已開啟 App");
            case Router.AGENT:
                return AgentLoop.run(act, t, ui);
            default: {
                String reply = localAnswer(act, t);
                if (reply == null) reply = ChatEngine.ask(act, t, msg -> confirm(act, msg));
                return reply != null ? reply : L.t("連不上 AI：請檢查網路，或到「功能 → 維護」確認 Claude API Key。");
            }
        }
    }

    static String runFixed(Activity act, Assist.Route r) {
        if (r.confirm && !confirm(act, L.t("這會開關飛航模式，可能會斷網，要繼續嗎？"))) return L.t("已取消");
        Maintenance.suRun(r.cmd);
        return r.reply;
    }

    /** 定時任務（建立、列出、取消）或能直接回答的短問題（電量、幾點…）；都不是回 null。 */
    static String localAnswer(Activity act, String t) {
        try {
            String s = Schedule.handle(act, t, msg -> confirm(act, msg));
            if (s != null) return s;
        } catch (Exception e) {
            return L.t("定時任務出錯：") + e.getMessage();
        }
        return Schedule.quickAnswer(act, t);
    }
}
