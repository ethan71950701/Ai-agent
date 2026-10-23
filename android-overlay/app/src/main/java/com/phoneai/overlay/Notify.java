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
 * 助理的通知與朗讀（系統通知與系統朗讀）。
 * 通知會顯示完整文字（聲音關掉也看得到）；朗讀用媒體音量。
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
        notifyText(c, L.t("AI 助手"), text, ID);
        if (c.getSharedPreferences("cfg", Context.MODE_PRIVATE).getBoolean("tts_on", true)) speak(c, text);
    }

    static void notifyText(Context c, String title, String text, int id) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(new NotificationChannel(CHANNEL, L.t("助理"), NotificationManager.IMPORTANCE_LOW));
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
        b.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text)).setAutoCancel(true);
        nm.notify(id, b.build());
    }

    /** 打斷正在唸的朗讀（使用者要說話、按停止、關面板時）。 */
    static synchronized void stopSpeaking() {
        if (tts != null && ttsReady) tts.stop();
    }

    static synchronized void speak(Context c, String text) {
        Context app = c.getApplicationContext();
        if (tts == null) {
            tts = new TextToSpeech(app, status -> {
                synchronized (Notify.class) {
                    ttsReady = status == TextToSpeech.SUCCESS;
                    if (ttsReady) {
                        tts.setLanguage(L.en() ? Locale.US : Locale.TRADITIONAL_CHINESE);
                        tts.setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
                    }
                }
            });
        }
        if (ttsReady) {
            tts.setLanguage(L.en() ? Locale.US : Locale.TRADITIONAL_CHINESE);   // 每次朗讀都跟著目前的介面語言
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "assistant");
        }
    }
}
