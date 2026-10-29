package com.phoneai.overlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

/**
 * 沒 root 更新時 PackageInstaller 的回呼。寫在 Manifest 裡：就算 App 在等待期間被系統收掉，
 * 回呼來了系統會重新叫起它，使用者還是會看到「安裝」確認畫面或失敗原因（通知不會像 Toast 一樣消失）。
 */
public class InstallResultReceiver extends BroadcastReceiver {
    static final String CH = "app_update_result";

    @Override public void onReceive(Context c, Intent i) {
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(i.getAction())) {
            Updater.finishInstall(c);   // 更新完成：App 被系統關掉又換成新版，用通知告訴使用者
            Intent launch = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
            notify(c, L.t("PhoneAI 已更新"), L.t("已更新到 1.0.") + BuildConfig.VERSION_CODE + L.t("，點這裡開啟"),
                    launch == null ? null : PendingIntent.getActivity(c, 2, launch, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            return;
        }
        int st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, -1);
        String msg = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent ask = i.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
            if (ask == null) return;
            ask.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { c.startActivity(ask); } catch (Exception ignored) { }
            // 背景啟動可能被系統擋下：同時放一則通知，點了一樣能打開安裝確認
            notify(c, L.t("PhoneAI 更新"), L.t("點這裡完成安裝"),
                    PendingIntent.getActivity(c, 1, ask, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            return;
        }
        Updater.finishInstall(c);
        if (st != PackageInstaller.STATUS_SUCCESS) {
            notify(c, L.t("PhoneAI 更新失敗"), (msg == null ? "" : msg) + " (" + st + ")", null);
        } else {
            c.getSystemService(NotificationManager.class).cancel(Updater.NOTE + 1);
        }
    }

    private static void notify(Context c, String title, String text, PendingIntent open) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CH, L.t("App 更新結果"), NotificationManager.IMPORTANCE_HIGH));
        Notification.Builder b = new Notification.Builder(c, CH).setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title).setContentText(text).setAutoCancel(true);
        if (open != null) b.setContentIntent(open);
        nm.notify(Updater.NOTE + 1, b.build());
    }
}
