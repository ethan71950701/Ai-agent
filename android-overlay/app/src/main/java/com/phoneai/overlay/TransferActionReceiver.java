package com.phoneai.overlay;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 傳輸通知上的按鈕：暫停、繼續、丟掉。繼續要花時間（可能傳很久），交給前景服務 ResumeService。 */
public class TransferActionReceiver extends BroadcastReceiver {
    @Override public void onReceive(final Context c, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        final String id = intent.getStringExtra("id") == null ? "" : intent.getStringExtra("id");
        switch (intent.getAction()) {
            case TransferNotify.ACTION_PAUSE:
                TransferNotify.requestPause();   // 傳輸中的程式在下一次更新進度時會停下來，通知換成「已暫停」
                break;
            case TransferNotify.ACTION_RESUME:
                try {
                    c.startForegroundService(new Intent(c, ResumeService.class).putExtra("id", id));
                } catch (Exception e) {
                    LifeLog.add(c, "繼續傳輸：無法啟動服務：" + e);
                }
                break;
            case TransferNotify.ACTION_DISCARD:
                final PendingResult pr = goAsync();
                new Thread(() -> {
                    try {
                        PendingStore.Entry e = PendingTransfers.store(c).find(id);
                        if (e != null) FileTools.discardPending(c, e);
                        ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(7310);
                        TransferNotify.cancel(c);
                    } finally {
                        pr.finish();
                    }
                }, "transfer-discard").start();
                break;
            default:
        }
    }
}
