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
    private static volatile boolean stopRequested, pauseRequested, active;

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

    static void requestStop() { stopRequested = true; }

    static void requestPause() { pauseRequested = true; }

    static boolean pauseRequested() { return pauseRequested; }

    static boolean stopRequested() { return stopRequested; }

    /** 現在有沒有傳輸進行中（互傳檔案頁用來顯示停止按鈕，不必靠通知）。 */
    static boolean active() { return active; }

    /** 進度更新：最多每 0.4 秒換一次，免得通知列一直閃。 */
    static synchronized void progress(Context c, String title, String where, String name, long done, long total) {
        if (stopRequested) throw new Stopped();
        if (pauseRequested) throw new Paused();
        if (!active) {
            active = true;
            SPEED = new TransferProgress();
            Runnable r = onStart;
            if (r != null) r.run();
        }
        long now = System.currentTimeMillis();
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
        stopRequested = false;
        pauseRequested = false;
        SPEED = new TransferProgress();
    }

    static synchronized void cancel(Context c) {
        if (active || pauseRequested) {   // 診斷：傳輸通知被收掉時記下是誰收的（查「Now Bar 不見了」用）
            StackTraceElement[] st = new Throwable().getStackTrace();
            LifeLog.add(c, "傳輸通知：被收掉，呼叫者 " + (st.length > 1 ? st[1].getClassName().replace("com.phoneai.overlay.", "") + "." + st[1].getMethodName() : "?"));
        }
        lastAt = 0;
        stopRequested = false;
        pauseRequested = false;
        active = false;
        ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(PROGRESS_ID);
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
        stopRequested = false;
        pauseRequested = false;
        active = false;
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
