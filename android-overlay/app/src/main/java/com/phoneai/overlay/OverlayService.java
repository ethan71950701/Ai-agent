package com.phoneai.overlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.Collections;

/**
 * 前景服務：
 *  1. 全螢幕透明懸浮層（不攔觸控）畫點擊漣漪 / 滑動軌跡。
 *  2. 「AI 正在操作手機」常駐通知，要求升級成 Android 16 Live Update（Now Bar / 狀態列膠囊，是否顯示要實機測）。
 * agent2.py 透過 127.0.0.1:PORT 送一行一個 JSON 指令；第一行必須是 files/token 的內容（root 才讀得到）。
 * 連線斷掉（agent2 結束或被殺）就自動收掉通知與懸浮層。
 */
public class OverlayService extends Service {
    static final String TAG = "PhoneAIOverlay";
    static final int NOTIF_ID = 7195;
    static final String CHANNEL = "status_low";  // 舊的 "status" 是預設重要性，頻道建立後改不了，所以換新 id
    static final String ACTION_TEST = "com.phoneai.overlay.TEST";
    static final String ACTION_STOP = "com.phoneai.overlay.STOP";
    static final String ACTION_BEGIN = "com.phoneai.overlay.BEGIN";
    /** 目前執行中的服務（給 App 內的自動操作更新 Now Bar 用）；沒在跑就是 null。 */
    static volatile OverlayService instance;

    /** App 內的自動操作每一步呼叫：更新 Now Bar 的文字與步數；服務沒在跑就先啟動。 */
    /** 自動操作結束：收掉 Now Bar（前景服務與它的通知）。 */
    static void endSteps(android.content.Context c) {
        OverlayService s = instance;
        if (s != null) s.main.post(s::finish);
    }

    static void showStep(android.content.Context c, String text, int step, int max) {
        OverlayService s = instance;
        if (s == null) {
            c.startForegroundService(new Intent(c, OverlayService.class).setAction(ACTION_BEGIN));
            return;
        }
        s.main.post(() -> s.postStatus(L.t("AI 正在操作手機"), text, step, max));
    }
    /** 點 Now Bar（背景虛擬螢幕存在時）：顯示／關閉預覽小視窗。 */
    static final String ACTION_PREVIEW = "com.phoneai.overlay.PREVIEW";
    /** Now Bar 上的「接手／完成」按鈕：切換使用者接手背景螢幕。 */
    static final String ACTION_TAKEOVER = "com.phoneai.overlay.TAKEOVER";
    /** 使用者按下送出的瞬間由 App 發出：先顯示 Now Bar「準備中」與光暈，不用等後面的流程（路由、啟動、連線要好幾秒）。 */
    static final int CHIP_COLOR = 0xFF0B6BCB;  // 膠囊底色（藍）

    /** 給 MainActivity 顯示用的最後診斷結果 */
    static volatile String lastDiag = L.t("（還沒發過通知）");
    /** 有 agent2 連著 = 正在執行任務（助理面板用它決定要不要顯示「停止」）。 */
    static volatile boolean agentRunning;
    /** 背景虛擬螢幕的 SurfaceFlinger 編號（agent2 用 vd 指令告知）；空字串 = 沒有背景螢幕。 */
    static volatile String vdSf = "";
    /** 背景虛擬螢幕的系統螢幕編號（使用者接手時對它送 input -d）。 */
    static volatile int vdId = 0;
    /** 使用者正在預覽上接手背景螢幕（AI 要暫停）。 */
    static volatile boolean takeover;
    /** 接手剛結束過、agent2 還沒被告知（它下一次問 paused 會收到 "done"）。 */
    static volatile boolean takeoverEnded;
    static volatile String takeoverWhy = "";

    /** 主執行緒呼叫：進入／結束接手模式（agent2 的 takeover 指令、或使用者按預覽上的「接手／完成」）。 */
    static void applyTakeover(boolean on, String why) {
        if (on) {
            takeover = true;
            takeoverWhy = why == null ? "" : why;
        } else {
            if (takeover) takeoverEnded = true;
            takeover = false;
            takeoverWhy = "";
        }
        refreshNotification();  // Now Bar 上的按鈕文字要跟著變（接手 ↔ 完成）
    }

    /** 重發通知（沿用最近一次的標題與進度文字）；服務不在就什麼都不做。 */
    static void refreshNotification() {
        OverlayService s = self;
        if (s != null) s.postStatus(s.title, s.lastText, s.lastStep, s.lastMax);
    }

    /** 對話框（確認框、輸入框）一律明確指定開在「主螢幕」：背景螢幕剛被點過時，系統會以為使用者在用那個螢幕，沒指定就會開到背景螢幕裡。 */
    static android.os.Bundle mainDisplayOptions() {
        android.app.ActivityOptions o = android.app.ActivityOptions.makeBasic();
        o.setLaunchDisplayId(android.view.Display.DEFAULT_DISPLAY);
        return o.toBundle();
    }
    /** 背景虛擬螢幕的像素尺寸（預覽視窗用它算比例）。 */
    static volatile int vdW = 1080, vdH = 2340;

    private static volatile OverlayService self;
    private final Handler main = new Handler(Looper.getMainLooper());
    private NotificationManager nm;
    private WindowManager wm;
    private RippleView view;
    private String title = L.t("AI 正在操作手機");
    private String lastText = "";
    private int lastStep, lastMax;
;

;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        self = this;
        nm = getSystemService(NotificationManager.class);
        wm = getSystemService(WindowManager.class);
        nm.deleteNotificationChannel("status");
        // 比照 LiveBridge：低重要性、鎖定畫面可見（它的通知頻道 importance=2 能進 Now Bar；我們的預設重要性 3 沒進，原因是推測）
        NotificationChannel ch = new NotificationChannel(CHANNEL, L.t("執行狀態"), NotificationManager.IMPORTANCE_LOW);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        ch.setShowBadge(false);
        ch.setSound(null, null);
        ch.enableVibration(false);
        nm.createNotificationChannel(ch);
        // 前景服務的類型是 Android 14（API 34）才有的要求；Android 13 不需要也不能傳
        android.app.Notification n0 = build(L.t("PhoneAI 待命中"), L.t("等待 agent2.py 連線"), 0, 0);
        if (android.os.Build.VERSION.SDK_INT >= 34) startForeground(NOTIF_ID, n0, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(NOTIF_ID, n0);
        addOverlay();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String a = intent == null ? null : intent.getAction();
        A11yGuard.ensure(this);
        if (ACTION_TEST.equals(a)) runTest();
        else if (ACTION_STOP.equals(a)) stopAgent();
        else if (ACTION_BEGIN.equals(a)) postStatus(L.t("AI 正在操作手機"), L.t("準備中…"), 0, 0);
        else if (ACTION_PREVIEW.equals(a)) RippleAccessibilityService.togglePreview(vdSf);
        else if (ACTION_TAKEOVER.equals(a)) {  // Now Bar 的「接手／完成」：切換，並打開預覽讓使用者操作
            applyTakeover(!takeover, "");
            RippleAccessibilityService.takeoverChanged();
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        if (self == this) self = null;
        main.removeCallbacksAndMessages(null);
        try { if (tts != null) { tts.stop(); tts.shutdown(); } } catch (Exception ignored) { }
        removeOverlay();
        super.onDestroy();
    }

    // ---------- 朗讀 ----------

    private android.speech.tts.TextToSpeech tts;
    private boolean ttsReady;
    private String ttsPending;

    private void speak(String text) {
        if (text.length() > 500) text = text.substring(0, 500);   // 太長的只念前面
        if (ttsReady && tts != null) {
            tts.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "phoneai");
            return;
        }
        ttsPending = text;   // 引擎還在啟動：好了再念
        if (tts == null) {
            tts = new android.speech.tts.TextToSpeech(this, status -> {
                if (status != android.speech.tts.TextToSpeech.SUCCESS) { Log.w(TAG, "tts init failed " + status); tts = null; return; }
                tts.setAudioAttributes(new android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA).build());
                if (tts.isLanguageAvailable(java.util.Locale.TRADITIONAL_CHINESE) >= android.speech.tts.TextToSpeech.LANG_AVAILABLE) {
                    tts.setLanguage(java.util.Locale.TRADITIONAL_CHINESE);
                }
                ttsReady = true;
                if (ttsPending != null) { tts.speak(ttsPending, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "phoneai"); ttsPending = null; }
            });
        }
    }

    // ---------- token：agent2 用 su 讀 files/token，其他 App 讀不到，所以只有 root 端能下指令 ----------

    // ---------- 懸浮層 ----------

    private void addOverlay() {
        if (view != null || !Settings.canDrawOverlays(this)) return;
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.START;
        p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        p.setFitInsetsTypes(0);
        // Android 12+：別的 App 的懸浮層透明度 > 0.8 會擋住底下的觸控（包含 input tap），所以設 0.8
        p.alpha = 0.8f;
        view = new RippleView(this);
        try {
            wm.addView(view, p);
        } catch (Exception e) {
            Log.e(TAG, "addView", e);
            view = null;
        }
    }

    private void removeOverlay() {
        if (view == null) return;
        try { wm.removeView(view); } catch (Exception ignored) { }
        view = null;
    }

    /** 優先用無障礙懸浮層（不會被「設定」等 App 強制隱藏），沒開才用一般懸浮層 */
    private RippleView fx() {
        RippleView a = RippleAccessibilityService.view();
        return a != null ? a : view;
    }

    /** 指定螢幕（背景模式的第二螢幕）；display 是 0 或沒給就是主螢幕。 */
    private RippleView fx(int display) {
        return display > 0 ? RippleAccessibilityService.viewFor(display) : fx();
    }

    // ---------- 通知 ----------

    private Notification build(String t, String text, int step, int max) {
        Intent stop = new Intent(this, OverlayService.class).setAction(ACTION_STOP);
        PendingIntent pi = PendingIntent.getService(this, 1, stop, PendingIntent.FLAG_IMMUTABLE);
        Icon icon = Icon.createWithResource(this, R.drawable.ic_stat);
        // 點通知開 App。沒有 contentIntent 時 logcat 顯示 OngoingActivityDataHelper.shouldHide()=true（intent.component:null），膠囊被藏
        boolean vd = !vdSf.isEmpty();  // 有背景虛擬螢幕：點通知 = 顯示／關閉預覽；沒有就開 App
        PendingIntent open = vd
                ? PendingIntent.getService(this, 2, new Intent(this, OverlayService.class).setAction(ACTION_PREVIEW),
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT)
                : PendingIntent.getActivity(this, 0,
                        new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String chip = max > 0 ? "AI " + step + "/" + max : "AI";  // 膠囊空間小（建議 7 字內）
        Notification.Builder b = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(t)
                .setContentText(text)
                .setContentIntent(open)
                .setColor(CHIP_COLOR)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                .addAction(new Notification.Action.Builder(icon, L.t("停止"), pi).build());
        if (vd) {  // 有背景虛擬螢幕：Now Bar 上只有「停止」和「接手／完成」；要預覽就點通知本身
            PendingIntent take = PendingIntent.getService(this, 3, new Intent(this, OverlayService.class).setAction(ACTION_TAKEOVER),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            b.addAction(new Notification.Action.Builder(icon, takeover ? L.t("完成") : L.t("接手"), take).build());
        }
        // 要求升級成 Live Update（等同 AndroidX 的 setRequestPromotedOngoing）
        Bundle ex = new Bundle();
        ex.putBoolean("android.requestPromotedOngoing", true);
        // 不要加 android.ongoingActivityNoti.*：SystemUI 的 NotificationEntry.isOngoingActivity() 只要看到 style>0 就把 mIsRon 設成 false，
        // 改走「舊式三星路徑」（只認設定清單裡的 App，我們不在）。標準 Live Update 路徑則只需 secure 設定 enable_notification_nowbar_test=1。
        b.addExtras(ex);
        if (Build.VERSION.SDK_INT >= 36) {
            b.setShortCriticalText(chip);
        }
        return b.build();
    }

    private void postStatus(String t, String text, int step, int max) {
        lastText = text; lastStep = step; lastMax = max;
        nm.notify(NOTIF_ID, build(t, text, step, max));
        main.postDelayed(this::diagnose, 300);
    }

    /** 檢查系統有沒有真的把通知升級成 Live Update（FLAG_PROMOTED_ONGOING），結果給 MainActivity 和 agent2 看。 */
    private String diagnose() {
        StringBuilder sb = new StringBuilder("SDK=" + Build.VERSION.SDK_INT + " build=" + getString(R.string.build_id).split(" ")[0]);
        if (Build.VERSION.SDK_INT >= 36) {
            sb.append(" canPostPromoted=").append(nm.canPostPromotedNotifications());
            for (StatusBarNotification s : nm.getActiveNotifications()) {
                if (s.getId() != NOTIF_ID) continue;
                Notification n = s.getNotification();
                sb.append(" promotable=").append(n.hasPromotableCharacteristics());
                sb.append(" promoted=").append((n.flags & Notification.FLAG_PROMOTED_ONGOING) != 0);
            }
        }
        sb.append(" bgripple=1");  // 這版 App 的 ripple/swipe 看得懂 display 參數（agent2 背景模式用它判斷能不能畫第二螢幕漣漪）
        sb.append(" overlay=").append(view != null);
        sb.append(" a11y=").append(RippleAccessibilityService.view() != null);
        lastDiag = sb.toString();
        Log.i(TAG, lastDiag);
        return lastDiag;
    }

    // ---------- 指令 ----------

        /** 在連線執行緒呼叫，會等使用者按是/否（最多 120 秒，逾時當否）。對話框沒跳出來回 "unavailable"。 */
    private String confirm(String msg) {
        java.util.concurrent.CompletableFuture<Boolean> ans = new java.util.concurrent.CompletableFuture<>();
        java.util.concurrent.CountDownLatch shown = new java.util.concurrent.CountDownLatch(1);
        ConfirmActivity.answer = ans;
        ConfirmActivity.shown = shown;
        main.post(() -> {
            try {
                startActivity(new Intent(this, ConfirmActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                        .putExtra("msg", msg), mainDisplayOptions());
            } catch (Exception e) {
                Log.e(TAG, "confirm start", e);
            }
        });
        try {
            if (!shown.await(4, java.util.concurrent.TimeUnit.SECONDS)) return "unavailable";
            return ans.get(120, java.util.concurrent.TimeUnit.SECONDS) ? "yes" : "no";
        } catch (java.util.concurrent.TimeoutException e) {
            return "no";
        } catch (Exception e) {
            return "unavailable";
        }
    }

        private void finish() {
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    /** 通知上的「停止」：用 root 結束 agent2.py（第一次會跳 Magisk 授權）。agent2 收到 SIGTERM 會自己清理。 */
    private void stopAgent() {
        AgentLoop.cancel();        // App 內的自動操作迴圈在下一個檢查點結束
        Notify.stopSpeaking();
        postStatus(title, L.t("正在停止…"), 0, 0);
        main.postDelayed(this::finish, 800);
    }

    /** App 裡的「測試」按鈕：示範通知更新、漣漪與滑動。 */
    private void runTest() {
        int w = getResources().getDisplayMetrics().widthPixels;
        int h = getResources().getDisplayMetrics().heightPixels;
        postStatus(L.t("AI 正在操作手機（測試）"), L.t("第 1/3 步 · 點擊「畫面中央」"), 1, 3);
        main.postDelayed(() -> { RippleView v = fx(); if (v != null) v.ripple(w / 2f, h / 2f); }, 300);
        main.postDelayed(() -> {
            postStatus(L.t("AI 正在操作手機（測試）"), L.t("第 2/3 步 · 滑動"), 2, 3);
            RippleView v = fx(); if (v != null) v.swipe(w * 0.8f, h * 0.6f, w * 0.2f, h * 0.6f);
        }, 2000);
        main.postDelayed(() -> {
            postStatus(L.t("AI 正在操作手機（測試）"), L.t("第 3/3 步 · 點擊「左上」"), 3, 3);
            RippleView v = fx(); if (v != null) v.ripple(w * 0.25f, h * 0.3f);
        }, 4000);
        main.postDelayed(this::finish, 8000);
    }
}
