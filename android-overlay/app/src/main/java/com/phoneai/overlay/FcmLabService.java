package com.phoneai.overlay;

import android.app.NotificationChannel;
import android.app.NotificationManager;

import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/** 實驗：收到 FCM 資料訊息就跳一則通知並寫進記錄，看沒開著 App 時有沒有被叫醒、延遲多久。 */
public class FcmLabService extends FirebaseMessagingService {
    @Override public void onMessageReceived(RemoteMessage m) {
        FcmLab.init(this);
        long now = System.currentTimeMillis();
        long sent = 0;
        try { sent = Long.parseLong(String.valueOf(m.getData().get("sent"))); } catch (Exception ignored) { }
        String delay = sent > 0 ? (now - sent) + " ms" : "?";
        String note = "收到推播：" + m.getData().get("text") + "｜延遲 " + delay + "｜原優先級 " + m.getOriginalPriority() + "→實際 " + m.getPriority();
        LifeLog.add(this, "FCM 實驗：" + note);
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("fcm_lab", "FCM 實驗", NotificationManager.IMPORTANCE_HIGH));
        nm.notify(7711, new NotificationCompat.Builder(this, "fcm_lab").setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle("FCM 實驗：被叫醒了").setContentText(note).setStyle(new NotificationCompat.BigTextStyle().bigText(note)).setAutoCancel(true).build());
    }

    @Override public void onNewToken(String token) { LifeLog.add(this, "FCM 實驗：裝置 token 更新"); }
}
