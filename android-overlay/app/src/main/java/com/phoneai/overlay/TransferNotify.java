package com.phoneai.overlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

/**
 * 傳檔案時的通知：傳輸中是一則會更新的進度通知（檔名、百分比、大小、存到或傳到哪裡），
 * 結束後換成一則一般通知。進度通知一律用同一個編號，同時只會顯示一筆。
 */
final class TransferNotify {
    private TransferNotify() { }

    private static final String CHANNEL = "transfer";
    private static final int PROGRESS_ID = 7310, DONE_BASE = 7330;
    private static long lastAt;
    private static int doneCount;

    /** 進度更新：最多每 0.4 秒換一次，免得通知列一直閃。 */
    static synchronized void progress(Context c, String title, String where, String name, long done, long total) {
        long now = System.currentTimeMillis();
        if (done < total && now - lastAt < 400) return;
        lastAt = now;
        int pct = total > 0 ? (int) (done * 100 / Math.max(1, total)) : 0;
        String text = name + "　" + pct + "%　" + TransferStore.size(done) + " / " + TransferStore.size(total);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, L.t("互傳檔案"), NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(c, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle(title).setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text + "\n" + where))
                .setProgress(100, Math.min(100, pct), false)
                .setOngoing(true).setOnlyAlertOnce(true).build();
        nm.notify(PROGRESS_ID, n);
    }

    /** 傳輸結束（成功或失敗）：收起進度通知。 */
    static synchronized void cancel(Context c) {
        lastAt = 0;
        ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(PROGRESS_ID);
    }

    /** 傳輸完成：收起進度，再發一則一般通知，讓使用者看到結果。 */
    static void finish(Context c, String text) {
        cancel(c);
        Notify.notifyText(c, L.t("互傳檔案"), text, DONE_BASE + (doneCount++ % 20));
    }
}
