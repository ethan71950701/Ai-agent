package com.phoneai.overlay;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/** 實驗：收到 FCM 資料訊息就跳一則通知並寫進記錄，看沒開著 App 時有沒有被叫醒、延遲多久。 */
public class FcmLabService extends FirebaseMessagingService {
    private static final Doorbell.Verifier BELL = new Doorbell.Verifier(id -> TransferStore.keyOf(App.ctx, id));

    @Override public void onMessageReceived(RemoteMessage m) {
        FcmLab.init(this);
        if ("doorbell".equals(m.getData().get("kind"))) { onDoorbell(m); return; }
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

    private void notifyLab(String text) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("fcm_lab", "FCM 實驗", NotificationManager.IMPORTANCE_HIGH));
        nm.notify(7714, new NotificationCompat.Builder(this, "fcm_lab").setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle("FCM 門鈴").setContentText(text).setStyle(new NotificationCompat.BigTextStyle().bigText(text)).setAutoCancel(true).build());
    }

    private void onDoorbell(RemoteMessage m) {
        LifeLog.add(this, "FCM 門鈴：收到（尚未驗證），欄位 " + m.getData().keySet());
        Doorbell.Result r = BELL.verify(m.getData(), System.currentTimeMillis(), FcmLab.lanIp());
        if (!r.ok()) {   // 驗證不過：不連線。實驗版才跳通知講原因（正式版只留記錄）
            LifeLog.add(this, "FCM 門鈴被拒絕：" + r.error);
            notifyLab("門鈴被拒絕：" + r.error + "｜本機位址 " + FcmLab.lanIp() + "｜已配對 " + TransferStore.peers(this).size() + " 台");
            return;
        }
        notifyLab("門鈴通過，準備取檔：" + r.ring.name);
        LifeLog.add(this, "FCM 門鈴通過：" + r.ring.name + "（" + r.ring.size + " 位元組），來自 " + TransferStore.nameOf(this, r.ring.from));
        Intent i = new Intent(this, FetchService.class)
                .putExtra("from", r.ring.from).putExtra("ip", r.ring.ip).putExtra("port", r.ring.port)
                .putExtra("path", r.ring.path).putExtra("name", r.ring.name).putExtra("size", r.ring.size);
        try { startForegroundService(i); } catch (Exception e) { LifeLog.add(this, "FCM 門鈴：無法啟動接收服務：" + e); }
    }

    @Override public void onNewToken(String token) { LifeLog.add(this, "FCM 實驗：裝置 token 更新"); }
}
