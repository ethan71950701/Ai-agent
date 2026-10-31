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
        return ConfirmActivity.ask(act, msg);   // 不再用 MaterialAlertDialogBuilder(act)：助理面板的主題不是 Material，會直接閃退
    }

    static String answer(Activity act, String t, AgentLoop.Ui ui) {
        Root.recheckIfStale();   // 使用者可能剛撤銷或允許 root：每個任務開始前重新確認
        AgentLoop.showCard = false;
        AgentLoop.cardNote = "";
        AgentLoop.macroNote = "";   // 上一個任務留下的值不能影響這一次（聊天回答不該跳卡片）
        ChatEngine.searchNote = "";
        Usage.beginTally();
        String reply = null;
        try {
            reply = run(act, t, ui);
        } finally {
            double cost = Usage.tallyCost();
            long tokens = Usage.endTally();
            TaskLog.add(act, t, reply, AgentLoop.takeDetails(), tokens, cost);
            if (!AgentLoop.cardNote.isEmpty()) TaskLog.noteLatest(act, AgentLoop.cardNote);
            if (!AgentLoop.macroNote.isEmpty()) TaskLog.noteLatest(act, "macro", AgentLoop.macroNote);
            if (!ChatEngine.searchNote.isEmpty()) TaskLog.noteLatest(act, "search", ChatEngine.searchNote);   // 首頁與問問看面板共用這裡，所以兩邊的任務都會進歷史
        }
        return reply;
    }

    private static String run(Activity act, String t, AgentLoop.Ui ui) {
        Assist.Route route = Assist.route(t);
        if (route.kind == Assist.FIXED) {
            // 缺權限時系統的「修改系統設定」頁會被助理面板蓋住：先收起面板
            if (!Root.available() && Phone.willAskWriteSettings(act, route.cmd)) { ui.hide(); try { Thread.sleep(600); } catch (InterruptedException ignored) { } }
            return runFixed(act, route);
        }
        String over = Usage.overLimit(act);   // 超過用量上限：不送出 Claude 的請求
        if (over != null) return over;
        Router.Decision d = Router.decide(act, t);
        String kind = d == null ? Router.CHAT : d.kind;
        // Haiku 偶爾把「打開設定的 Wi-Fi 頁面」當成聊天（或路由失敗）：話裡有明顯的操作詞就改走自動操作
        if (Router.CHAT.equals(kind) && Assist.looksLikeAction(t)) kind = Router.AGENT;
        switch (kind) {
            case Router.CMD:
                return runFixed(act, Assist.fixed(d.cmdIndex));
            case Router.APP:
                Maintenance.suRun("monkey -p " + d.pkg + " -c android.intent.category.LAUNCHER 1");
                return L.t("已開啟 App");
            case Router.AGENT:
                return AgentLoop.run(act, t, ui, d == null || Router.isSystemInfoQuestion(t) ? null : d.openPkg, d == null ? null : d.macroTask);   // 查系統資訊不先開 App，讓模型用指令查
            default: {
                String reply = scheduleAnswer(act, t);   // 定時任務（建立、列出、取消）程式自己處理
                if (reply == null) reply = ChatEngine.ask(act, t, msg -> confirm(act, msg));
                if (reply == null) reply = Schedule.quickAnswer(act, t);   // 連不上 AI（沒網路）時，才用關鍵字直接回答電量、儲存空間、幾點這類短問題
                // 聊天助理回答「我不能操作手機」，但使用者明明是要操作：補救，改走自動操作
                if (Assist.refusesToOperate(reply) && Assist.looksLikeAction(t)) return AgentLoop.run(act, t, ui);
                return reply != null ? reply : L.t("連不上 AI：") + ChatEngine.lastError + L.t("（請檢查網路，或到「功能 → 維護」確認 Claude API Key。）");
            }
        }
    }

    static String runFixed(Activity act, Assist.Route r) {
        if (r.cmd.startsWith(Assist.VOLUME_PREFIX))   // 直接設定音量百分比：用 AudioManager，不用 root 也不用 AI
            return Phone.setVolume(act, Integer.parseInt(r.cmd.substring(Assist.VOLUME_PREFIX.length())));
        if (r.confirm && !confirm(act, L.t("這個動作可能會斷網（連 AI 也會連不上），要繼續嗎？"))) return L.t("已取消");
        if (!Root.available()) return Phone.runFixedNoRoot(act, r.cmd);   // 沒有 root：用無障礙與系統 API，做不到的打開設定頁
        Maintenance.suRun(r.cmd);
        return r.reply;
    }

    /** 定時任務（建立、列出、取消）；不是回 null。 */
    static String scheduleAnswer(Activity act, String t) {
        try {
            return Schedule.handle(act, t, msg -> confirm(act, msg));
        } catch (Exception e) {
            return L.t("定時任務出錯：") + e.getMessage();
        }
    }
}
