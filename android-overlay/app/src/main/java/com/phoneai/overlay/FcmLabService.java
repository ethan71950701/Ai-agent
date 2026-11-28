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
        FcmLab.noteReceived(this, m.getData());   // 記下收到的時間與延遲（設定頁顯示，看推播連線穩不穩）
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
        Doorbell.Result r = BELL.verify(m.getData(), System.currentTimeMillis(), FcmLab.lanNet());
        if (!r.ok()) {   // 驗證不過：不連線。實驗版才跳通知講原因（正式版只留記錄）
            LifeLog.add(this, "FCM 門鈴被拒絕：" + r.error);
            notifyLab("門鈴被拒絕：" + r.error + "｜本機 " + FcmLab.lanNet() + "｜已配對 " + TransferStore.peers(this).size() + " 台");
            return;
        }
        if ("addr".equals(r.ring.mode)) {   // 對方（被我叫醒、開了 Tailscale 的那一端）回報它的 Tailscale 位址：記下來，馬上就能連
            TransferStore.updateAddress(this, r.ring.from, r.ring.ip, r.ring.port);
            boolean lan = !Doorbell.isTailnet(r.ring.ip);
            if (lan) TransferStore.noteLanReport(r.ring.from, System.currentTimeMillis());   // 找對方的人靠這個知道「對方醒了、區網位址是這個」，馬上試連
            LifeLog.add(this, "FCM：收到「" + TransferStore.nameOf(this, r.ring.from) + "」回報的" + (lan ? "區網" : " Tailscale ") + "位址 " + r.ring.ip);
            return;
        }
        if ("serve".equals(r.ring.mode)) {   // 只是叫醒：把伺服器開起來，對方再用原本的方式連進來
            LifeLog.add(this, "FCM 叫醒通過：" + TransferStore.nameOf(this, r.ring.from) + " 要連進來");
            String st = FcmLab.serveForAWhile(this, r.ring.remote);
            notifyLab("收到「" + TransferStore.nameOf(this, r.ring.from) + "」的叫醒：" + st);   // 每次被叫醒都讓人看得到結果（也符合高優先級推播要有可見通知的建議）
            FcmLab.reportAddressAsync(this, r.ring.from, !r.ring.remote);   // 從外網來：回報我的 Tailscale 位址（它第一次可能還不知道）；區網叫醒：回報我的區網位址（它馬上就能判斷是不是真的同一個區網）
            return;
        }
        LifeLog.add(this, "FCM 門鈴：收到舊版的「來取檔案」門鈴，已不支援，忽略（請兩支都更新到最新版）");   // 檔案一律由發送端直接連線傳，推播只負責叫醒
    }

    @Override public void onNewToken(String token) {
        FcmLab.saveMyToken(this, token);
        LifeLog.add(this, "FCM：裝置 token 更新，正在通知已配對的裝置");
        final android.content.Context app = getApplicationContext();
        Thread t = new Thread(() -> FcmLab.pushTokenToPeers(app), "fcm-push-token");
        t.setDaemon(true);
        t.start();
    }
}
