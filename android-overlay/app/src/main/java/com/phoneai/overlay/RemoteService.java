package com.phoneai.overlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;

/** 遠端指令的前景服務：開著時在背景聽 Telegram 群組的指令（讓系統不會把它關掉）。 */
public class RemoteService extends Service {
    private static final String CHANNEL = "remote";
    private static final int NOTIF_ID = 7401;

    static void start(Context c) {
        try {
            c.startForegroundService(new Intent(c, RemoteService.class));
        } catch (Exception ignored) { }
    }

    static void stop(Context c) {
        c.stopService(new Intent(c, RemoteService.class));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, L.t("遠端指令"), NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setContentTitle(L.t("遠端指令已開啟"))
                .setContentText(L.t("正在聽 Telegram 群組的指令；到「互傳檔案」頁可以關閉"))
                .setOngoing(true).build();
        startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        if (!TelegramRemote.enabled(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        TelegramRemote.start(this);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        TelegramRemote.stop();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
