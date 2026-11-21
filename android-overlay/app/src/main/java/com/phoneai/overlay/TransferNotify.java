package com.phoneai.overlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;

/**
 * 傳檔案時的通知：傳輸中是一則會更新的進度通知（檔名、百分比、大小、存到或傳到哪裡），
 * 結束後換成一則一般通知。進度通知一律用同一個編號，同時只會顯示一筆。
 */
final class TransferNotify {
    private TransferNotify() { }

    private static final String CHANNEL = "transfer";
    private static final int PROGRESS_ID = 7310, DONE_BASE = 7330;
    static final String ACTION_STOP = "com.phoneai.overlay.TRANSFER_STOP";
    static final String ACTION_PAUSE = "com.phoneai.overlay.TRANSFER_PAUSE";
    static final String ACTION_RESUME = "com.phoneai.overlay.TRANSFER_RESUME";
    static final String ACTION_DISCARD = "com.phoneai.overlay.TRANSFER_DISCARD";
    private static long lastAt;
    private static int doneCount;
    private static final TransferSlots SLOTS = new TransferSlots();   // 同時進行的傳輸各自一格，停止／暫停旗標不再全域共用
    private static String lastPrimaryId = "";

    /** 使用者按了進度通知的「停止」：進行中的傳輸在下一次更新時中斷，未完成的檔案會被刪掉。 */
    static final class Stopped extends RuntimeException {
        Stopped() { super("stopped"); }
    }

    /** 使用者按了「暫停」：進行中的傳輸在下一次更新時中斷，已傳的部分保留，之後可以按「繼續」。 */
    static final class Paused extends RuntimeException {
        Paused() { super("paused"); }
    }

    /** 傳輸剛開始時要做的事（助理面板用：自動收起來，進度只看通知）；用完要設回 null。 */
    static volatile Runnable onStart;
    private static TransferProgress SPEED = new TransferProgress();

    /** 按了停止／暫停：只作用在通知上顯示的那一筆傳輸。 */
    static void requestStop() { SLOTS.stopPrimary(System.currentTimeMillis()); }

    static void requestPause() { SLOTS.pausePrimary(System.currentTimeMillis()); }

    /** 這條執行緒上的這筆傳輸有沒有被要求暫停／停止（重試等待中也要看）。 */
    static boolean pauseRequested() {
        TransferSlots.Slot s = SLOTS.current(System.currentTimeMillis());
        return s != null && s.pause;
    }

    static boolean stopRequested() {
        TransferSlots.Slot s = SLOTS.current(System.currentTimeMillis());
        return s != null && s.stop;
    }

    /** 現在有沒有傳輸進行中（互傳檔案頁用來顯示停止按鈕，不必靠通知；Tailscale 的閒置判斷也看它）。 */
    static boolean active() { return SLOTS.active(System.currentTimeMillis()); }

    /** 進度更新：最多每 0.4 秒換一次，免得通知列一直閃。 */
    static synchronized void progress(Context c, String title, String where, String name, long done, long total) {
        long now = System.currentTimeMillis();
        TransferSlots.Slot slot = SLOTS.enter(now);
        if (slot.stop) throw new Stopped();
        if (slot.pause) throw new Paused();
        if (!slot.shown) {
            slot.shown = true;
            Runnable r = onStart;
            if (r != null) r.run();
        }
        if (!SLOTS.isPrimary(slot, now)) return;   // 同時有好幾筆：通知只顯示最早開始的那一筆，其他的只管自己的旗標
        if (!slot.id.equals(lastPrimaryId)) { lastPrimaryId = slot.id; SPEED = new TransferProgress(); lastAt = 0; }
        if (done < total && now - lastAt < 400) return;
        lastAt = now;
        int pct = total > 0 ? (int) (done * 100 / Math.max(1, total)) : 0;
        double bps = SPEED.update(now, done);
        long eta = TransferProgress.etaSeconds(total, done, bps);
        String shortLine = TransferProgress.compact(pct, bps, eta);   // 收合時只有一行：太長會被截掉，所以只放百分比、速度、剩餘時間
        String full = name + "\n" + TransferStore.size(done) + " / " + TransferStore.size(total) + "\n" + shortLine + "\n" + where;   // 展開才看得到完整檔名
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, L.t("互傳檔案"), NotificationManager.IMPORTANCE_LOW));
        Notification.Builder nb = new Notification.Builder(c, CHANNEL).setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title).setContentText(shortLine)
                .setSubText(TransferProgress.ellipsizeMiddle(name, 24))
                .setStyle(new Notification.BigTextStyle().bigText(full))
                .setCategory(Notification.CATEGORY_PROGRESS).setShowWhen(false)
                .setProgress(100, Math.min(100, pct), false)
                .addAction(new Notification.Action.Builder(Icon.createWithResource(c, android.R.drawable.ic_media_pause),
                        L.t("暫停"), action(c, ACTION_PAUSE, "")).build())
                .addAction(new Notification.Action.Builder(Icon.createWithResource(c, android.R.drawable.ic_menu_close_clear_cancel),
                        L.t("停止"), PendingIntent.getService(c, 0, new Intent(c, TransferService.class).setAction(ACTION_STOP),
                        PendingIntent.FLAG_IMMUTABLE)).build())
                .setOngoing(true).setOnlyAlertOnce(true);
        android.os.Bundle ex = new android.os.Bundle();
        ex.putBoolean("android.requestPromotedOngoing", true);   // 同 Updater：要求超級島／Now Bar／Live Update
        nb.addExtras(ex);
        if (android.os.Build.VERSION.SDK_INT >= 36) nb.setShortCriticalText(pct + "%");
        nm.notify(PROGRESS_ID, nb.build());
    }

    /** 傳輸結束（成功或失敗）：收起進度通知。 */
    /** 新的傳輸開始：清掉上一次留下的「停止」。 */
    static synchronized void begin() {
        SLOTS.begin();   // 只清掉這條執行緒自己上一次的旗標，不影響別的傳輸
        SLOTS.enter(System.currentTimeMillis());   // 立刻算「進行中」：連線、叫醒對方的等待期間也能按暫停／停止，Tailscale 的閒置判斷也知道在忙（2 分鐘沒動靜會自動當作結束）
    }

    /** 這條執行緒的傳輸收尾（不管怎麼結束都要呼叫，只收掉自己這一筆、不動通知）：有些結束方式（改用推播叫醒、直接 return）不會經過 finish／cancel。 */
    static void release() { SLOTS.leave(System.currentTimeMillis()); }

    static synchronized void cancel(Context c) {
        if (SLOTS.current(System.currentTimeMillis()) != null) {   // 診斷：傳輸通知被收掉時記下是誰收的（查「Now Bar 不見了」用）
            StackTraceElement[] st = new Throwable().getStackTrace();
            LifeLog.add(c, "傳輸通知：被收掉，呼叫者 " + (st.length > 1 ? st[1].getClassName().replace("com.phoneai.overlay.", "") + "." + st[1].getMethodName() : "?"));
        }
        lastAt = 0;
        boolean none = SLOTS.leave(System.currentTimeMillis());   // 只收掉自己這一筆
        if (none) ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(PROGRESS_ID);   // 還有別筆在傳就留著通知，它的下一次更新會接手顯示
    }

    /** 傳輸完成：收起進度，再發一則一般通知，讓使用者看到結果。 */
    static void finish(Context c, String text) {
        cancel(c);
        Notify.notifyText(c, L.t("互傳檔案"), text, DONE_BASE + (doneCount++ % 20));
    }

    /** 收到檔案：收起進度，再發完成通知；點通知直接開啟那個檔案。 */
    static void finishFile(Context c, String text, java.io.File file) {
        cancel(c);
        Notify.notifyFile(c, L.t("互傳檔案"), text, DONE_BASE + (doneCount++ % 20), file);
    }

    /** 通知上的按鈕：交給 TransferActionReceiver 處理（暫停、繼續、丟掉）。id 是「沒傳完」清單裡那一筆。 */
    private static PendingIntent action(Context c, String act, String id) {
        Intent it = new Intent(c, TransferActionReceiver.class).setAction(act).putExtra("id", id);
        return PendingIntent.getBroadcast(c, (act + id).hashCode(), it, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * 傳輸中斷或暫停後，通知「留著」（不消失、不被滑掉，Now Bar 也還在），顯示已傳多少，並有「繼續」「丟掉」。
     * 同一個進度通知編號，會取代傳輸中的那則。e 是「沒傳完」清單裡的那一筆。
     */
    static synchronized void halted(Context c, PendingStore.Entry e, String reason, boolean paused) {
        lastAt = 0;
        boolean none = SLOTS.leave(System.currentTimeMillis());   // 這一筆結束了（旗標跟著收掉）；別筆還在傳的話，它們不受影響
        if (e != null && !none) {   // 還有別筆在傳：通知列留給它，這一筆看「沒傳完」清單就好（不然會蓋掉它的進度）
            LifeLog.add(c, "傳輸通知：" + e.name + (paused ? " 已暫停" : " 已中斷") + "，但還有別筆在傳，通知先不換，到「互傳檔案」頁看");
            return;
        }
        if (e == null) {   // 找不到對應的「沒傳完」紀錄（沒登記到、或已被清掉）：沒有東西可以繼續，只好收掉通知，並記下來方便查
            LifeLog.add(c, "傳輸通知：" + (paused ? "已暫停" : "已中斷") + "但找不到對應的未完成紀錄，所以收掉通知");
            cancel(c);
            return;
        }
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, L.t("互傳檔案"), NotificationManager.IMPORTANCE_LOW));
        int pct = e.percent();
        String head = (paused ? L.t("已暫停") : L.t("已中斷")) + "　" + pct + "%";
        String full = e.name + "\n" + TransferStore.size(e.done) + " / " + TransferStore.size(e.size) + "\n" + (reason == null ? "" : reason);
        Notification.Builder nb = new Notification.Builder(c, CHANNEL).setSmallIcon(R.drawable.ic_stat)
                .setContentTitle((e.kind.equals("up") ? L.t("傳給 ") : L.t("取自 ")) + e.peerName).setContentText(head)
                .setSubText(TransferProgress.ellipsizeMiddle(e.name, 24))
                .setStyle(new Notification.BigTextStyle().bigText(full))
                .setCategory(Notification.CATEGORY_PROGRESS).setShowWhen(false)   // 跟傳輸中同一種類別：Now Bar／超級島才會繼續顯示
                .setProgress(100, Math.min(100, pct), false)
                .addAction(new Notification.Action.Builder(Icon.createWithResource(c, android.R.drawable.ic_media_play),
                        L.t("繼續"), action(c, ACTION_RESUME, e.id)).build())
                .addAction(new Notification.Action.Builder(Icon.createWithResource(c, android.R.drawable.ic_menu_close_clear_cancel),
                        L.t("丟掉"), action(c, ACTION_DISCARD, e.id)).build())
                .setContentIntent(PendingIntent.getActivity(c, 7311, new Intent(c, TransferActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT))
                .setOngoing(true).setOnlyAlertOnce(true);
        android.os.Bundle ex = new android.os.Bundle();
        ex.putBoolean("android.requestPromotedOngoing", true);
        nb.addExtras(ex);
        if (android.os.Build.VERSION.SDK_INT >= 36) nb.setShortCriticalText(pct + "%");   // 膠囊上跟傳輸中一樣只放百分比
        nm.notify(PROGRESS_ID, nb.build());
        LifeLog.add(c, "傳輸通知：顯示「" + (paused ? "已暫停" : "已中斷") + "」" + e.name + " " + pct + "%");
    }

    /** 把「沒傳完」的那一筆（用 id 找）顯示成中斷或暫停的通知。 */
    static void haltedById(Context c, String id, String reason, boolean paused) {
        PendingStore.Entry e = id == null ? null : PendingTransfers.store(c).find(id);
        halted(c, e, reason, paused);
    }
}
