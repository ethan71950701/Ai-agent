package com.phoneai.overlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;

/** 從通知按「繼續」：在前景服務裡把沒傳完的那一筆接著傳完，傳完（或又失敗）就結束。進度與結果用傳輸通知顯示。 */
public class ResumeService extends Service {
    private static final String CHANNEL = "resume_job";

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent in, int flags, int startId) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, L.t("互傳檔案"), NotificationManager.IMPORTANCE_MIN));
        Notification n = new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_stat).setContentTitle(L.t("繼續傳輸…")).setOngoing(true).build();
        startForeground(7714, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        final String id = in == null ? "" : in.getStringExtra("id");
        new Thread(() -> {
            try {
                PendingStore.Entry e = PendingTransfers.store(this).find(id);
                if (e == null) {
                    TransferNotify.cancel(this);
                } else {
                    String r = FileTools.resume(this, e);
                    if (FileTools.NEED_PICK.equals(r)) {
                        TransferNotify.haltedById(this, id, L.t("原來的檔案打不開：到「互傳檔案」頁按「繼續」，重新選同一個檔案，會從斷點繼續"), false);
                    }
                }
            } catch (Exception ex) {
                TransferNotify.haltedById(this, id, String.valueOf(ex.getMessage()), false);
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
            }
        }, "resume-job").start();
        return START_NOT_STICKY;
    }
}
