package com.phoneai.overlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;

import java.io.File;

/** 實驗：被門鈴叫醒後，短暫的前景服務，向發送端取一個檔案（用現有的簽章請求），完成就結束。 */
public class FetchService extends Service {
    private static final String CHANNEL = "fetch", ACTIVE = "fetch_active";

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent in, int flags, int startId) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "接收檔案", NotificationManager.IMPORTANCE_LOW));
        String name = in == null ? "" : in.getStringExtra("name");
        Notification n = new Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("正在接收檔案").setContentText(name).setOngoing(true).build();
        startForeground(7712, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        if (in == null) { stopSelf(); return START_NOT_STICKY; }
        final String from = in.getStringExtra("from"), ip = in.getStringExtra("ip"), path = in.getStringExtra("path");
        final int port = in.getIntExtra("port", 0);
        final long size = in.getLongExtra("size", -1);
        new Thread(() -> {
            String result;
            try {
                byte[] key = TransferStore.keyOf(this, from);
                if (key == null || !TransferStore.hasAllFiles()) throw new java.io.IOException(key == null ? "沒配對" : "沒有「所有檔案存取」權限");
                File inbox = TransferStore.inbox();
                inbox.mkdirs();
                File dest = unique(inbox, new File(name == null ? "file" : name.replace('\\', '/')).getName());
                TransferClient cl = new TransferClient(ip, port, TransferStore.myId(this), TransferStore.myName(this), key);
                long got = cl.download(path, dest, null);
                if (size >= 0 && got != size) { dest.delete(); throw new java.io.IOException("大小不符：預期 " + size + "，收到 " + got); }
                result = "已存到 下載/PhoneAI/" + dest.getName() + "（" + TransferStore.size(got) + "）";
                TransferStore.log(this, "門鈴取回 " + dest.getName() + "（" + TransferStore.size(got) + "）");
            } catch (Throwable e) {
                result = "接收失敗：" + e.getMessage();
            }
            LifeLog.add(this, "FCM 門鈴取檔：" + result);
            nm.notify(7713, new Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle("互傳檔案").setContentText(result).setAutoCancel(true).build());
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }, "fetch-service").start();
        return START_NOT_STICKY;
    }

    private static File unique(File dir, String name) {
        File f = new File(dir, name);
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name, ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 2; f.exists(); i++) f = new File(dir, base + " (" + i + ")" + ext);
        return f;
    }
}
