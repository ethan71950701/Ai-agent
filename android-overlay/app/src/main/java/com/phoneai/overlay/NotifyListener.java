package com.phoneai.overlay;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * 通知監聽：系統在有新通知時才叫醒這個服務，App 不用一直在背景跑。
 * 只看 Telegram 的通知，交給 TelegramRemote 判斷要不要執行。需要在系統設定裡允許「通知存取」。
 */
public class NotifyListener extends NotificationListenerService {
    private static final String TELEGRAM = "org.telegram.messenger";

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || !TELEGRAM.equals(sbn.getPackageName())) return;
        Bundle ex = sbn.getNotification().extras;
        CharSequence t = ex.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence x = ex.getCharSequence(Notification.EXTRA_TEXT);
        TelegramRemote.onNotification(this, t == null ? "" : t.toString(), x == null ? "" : x.toString());
    }
}
