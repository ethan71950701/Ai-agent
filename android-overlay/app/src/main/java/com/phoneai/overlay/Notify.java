package com.phoneai.overlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.os.Build;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

/**
 * 助理的通知與朗讀（取代 Termux:API 的 termux-notification、termux-tts-speak）。
 * 通知會顯示完整文字（聲音關掉也看得到）；朗讀用媒體音量，跟原本 `termux-tts-speak -s MUSIC` 一樣。
 * 朗讀開關是「設定 → 語音 → 朗讀結果」（cfg 的 tts_on）。
 */
final class Notify {
    private static final String CHANNEL = "assistant";
    private static final int ID = 7199;
    private static TextToSpeech tts;
    private static boolean ttsReady;

    private Notify() { }

    /** 唸出來，同時用通知顯示。 */
    static void say(Context c, String text) {
        if (text == null || text.isEmpty()) return;
        notifyText(c, "AI 助手", text, ID);
        if (c.getSharedPreferences("cfg", Context.MODE_PRIVATE).getBoolean("tts_on", true)) speak(c, text);
    }

    static void notifyText(Context c, String title, String text, int id) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(new NotificationChannel(CHANNEL, "助理", NotificationManager.IMPORTANCE_LOW));
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
        b.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text)).setAutoCancel(true);
        nm.notify(id, b.build());
    }

    static synchronized void speak(Context c, String text) {
        Context app = c.getApplicationContext();
        if (tts == null) {
            tts = new TextToSpeech(app, status -> {
                synchronized (Notify.class) {
                    ttsReady = status == TextToSpeech.SUCCESS;
                    if (ttsReady) {
                        tts.setLanguage(Locale.TRADITIONAL_CHINESE);
                        tts.setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
                    }
                }
            });
        }
        if (ttsReady) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "assistant");
    }
}
