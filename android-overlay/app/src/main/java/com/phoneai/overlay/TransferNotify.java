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
    private static long lastAt;
    private static int doneCount;
    private static volatile boolean stopRequested, active;

    /** 使用者按了進度通知的「停止」：進行中的傳輸在下一次更新時中斷，未完成的檔案會被刪掉。 */
    static final class Stopped extends RuntimeException {
        Stopped() { super("stopped"); }
    }

    /** 傳輸剛開始時要做的事（助理面板用：自動收起來，進度只看通知）；用完要設回 null。 */
    static volatile Runnable onStart;

    static void requestStop() { stopRequested = true; }

    static boolean stopRequested() { return stopRequested; }

    /** 現在有沒有傳輸進行中（互傳檔案頁用來顯示停止按鈕，不必靠通知）。 */
    static boolean active() { return active; }

    /** 進度更新：最多每 0.4 秒換一次，免得通知列一直閃。 */
    static synchronized void progress(Context c, String title, String where, String name, long done, long total) {
        if (stopRequested) throw new Stopped();
        if (!active) {
            active = true;
            Runnable r = onStart;
            if (r != null) r.run();
        }
        long now = System.currentTimeMillis();
        if (done < total && now - lastAt < 400) return;
        lastAt = now;
        int pct = total > 0 ? (int) (done * 100 / Math.max(1, total)) : 0;
        String text = name + "　" + pct + "%　" + TransferStore.size(done) + " / " + TransferStore.size(total);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, L.t("互傳檔案"), NotificationManager.IMPORTANCE_LOW));
        Notification.Builder nb = new Notification.Builder(c, CHANNEL).setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title).setContentText(text + "\n" + where)
                .setCategory(Notification.CATEGORY_PROGRESS).setShowWhen(false)
                .setProgress(100, Math.min(100, pct), false)
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
    }

    static synchronized void cancel(Context c) {
        lastAt = 0;
        stopRequested = false;
        active = false;
        ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(PROGRESS_ID);
    }

    /** 傳輸完成：收起進度，再發一則一般通知，讓使用者看到結果。 */
    static void finish(Context c, String text) {
        cancel(c);
        Notify.notifyText(c, L.t("互傳檔案"), text, DONE_BASE + (doneCount++ % 20));
    }
}
