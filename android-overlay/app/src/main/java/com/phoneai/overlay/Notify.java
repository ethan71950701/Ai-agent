package com.phoneai.overlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

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
        String plain = Md.plain(text);   // 朗讀與通知不要出現「星號、井字」這類符號
        notifyText(c, L.t("AI 助手"), plain, ID);
        if (c.getSharedPreferences("cfg", Context.MODE_PRIVATE).getBoolean("tts_on", true)) speak(c, Md.spoken(text));
    }

    static void notifyText(Context c, String title, String text, int id) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(new NotificationChannel(CHANNEL, L.t("助理"), NotificationManager.IMPORTANCE_LOW));
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
        b.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text)).setAutoCancel(true);
        nm.notify(id, b.build());
    }

    private static String pending;                 // 引擎還在啟動時先記著，好了再念
    static volatile String ttsInfo = "";           // 診斷頁顯示：最近一次朗讀的結果
    private static final int CHUNK = 3500;         // 系統朗讀一次有字數上限，太長要切段

    /** 打斷正在唸的朗讀（使用者要說話、按停止、關面板時）。 */
    static synchronized void stopSpeaking() {
        pending = null;
        if (tts != null && ttsReady) tts.stop();
    }

    static synchronized void speak(Context c, String text) {
        if (text == null || text.trim().isEmpty()) {
            ttsInfo = L.t("沒有可朗讀的文字");
            return;
        }
        Context app = c.getApplicationContext();
        if (tts == null) {
            tts = new TextToSpeech(app, status -> {
                synchronized (Notify.class) {
                    ttsReady = status == TextToSpeech.SUCCESS;
                    if (!ttsReady) {
                        ttsInfo = L.t("朗讀引擎啟動失敗（代碼 ") + status + L.t("）");
                        return;
                    }
                    tts.setLanguage(L.en() ? Locale.US : Locale.TRADITIONAL_CHINESE);
                    tts.setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
                    tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override public void onStart(String id) { }
                        @Override public void onDone(String id) { }
                        @Override public void onError(String id) { ttsInfo = L.t("朗讀失敗：") + id; }
                    });
                    if (pending != null) {
                        String p = pending;
                        pending = null;
                        speakNow(p);
                    }
                }
            });
        }
        if (ttsReady) speakNow(text);
        else pending = text;   // 以前第一次朗讀時引擎還沒好，會被直接丟掉
    }

    private static void speakNow(String text) {
        tts.setLanguage(L.en() ? Locale.US : Locale.TRADITIONAL_CHINESE);   // 每次朗讀都跟著目前的介面語言
        int max = Math.min(CHUNK, Math.max(200, TextToSpeech.getMaxSpeechInputLength() - 100));
        int pos = 0, n = 0, res = TextToSpeech.SUCCESS;
        while (pos < text.length()) {
            int end = Math.min(text.length(), pos + max);
            if (end < text.length()) end = lastBreak(text, pos, end);
            res = tts.speak(text.substring(pos, end), n == 0 ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD, null, "assistant-" + n);
            if (res != TextToSpeech.SUCCESS) break;
            pos = end;
            n++;
        }
        ttsInfo = res == TextToSpeech.SUCCESS ? L.t("最近朗讀 ") + text.length() + L.t(" 字") : L.t("朗讀失敗（代碼 ") + res + L.t("）");
    }

    /** 太長要切段時，盡量切在句尾。 */
    private static int lastBreak(String t, int from, int to) {
        for (int i = to; i > from + 100; i--) {
            char ch = t.charAt(i - 1);
            if (ch == '。' || ch == '！' || ch == '？' || ch == '\n' || ch == '.' || ch == '!' || ch == '?') return i;
        }
        return to;
    }
}
