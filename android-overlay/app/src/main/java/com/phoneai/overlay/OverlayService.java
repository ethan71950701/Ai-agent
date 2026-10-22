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
    static final int PORT = 47195;
    static final int NOTIF_ID = 7195;
    static final String CHANNEL = "status_low";  // 舊的 "status" 是預設重要性，頻道建立後改不了，所以換新 id
    static final String ACTION_TEST = "com.phoneai.overlay.TEST";
    static final String ACTION_STOP = "com.phoneai.overlay.STOP";
    /** 點 Now Bar（背景虛擬螢幕存在時）：顯示／關閉預覽小視窗。 */
    static final String ACTION_PREVIEW = "com.phoneai.overlay.PREVIEW";
    /** Now Bar 上的「接手／完成」按鈕：切換使用者接手背景螢幕。 */
    static final String ACTION_TAKEOVER = "com.phoneai.overlay.TAKEOVER";
    /** 使用者按下送出的瞬間由 App 發出：先顯示 Now Bar「準備中」與光暈，不用等後面的流程（路由、啟動、連線要好幾秒）。 */
    static final String ACTION_BEGIN = "com.phoneai.overlay.BEGIN";
    static final long PENDING_MS = 15_000;  // 這麼久都沒有 agent2 連上來就收掉（例如指令立刻就做完了）
    static final int CHIP_COLOR = 0xFF0B6BCB;  // 膠囊底色（藍）
    static final long IDLE_MS = 60_000;  // 啟動後這麼久都沒有 agent 連上就自己結束

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
    private ServerSocket server;
    private String token;
    private int clients = 0;
    private String title = L.t("AI 正在操作手機");
    private String lastText = "";
    private int lastStep, lastMax;

    private final Runnable pendingEnd = () -> {
        if (clients == 0) {
            agentRunning = false;
            RippleAccessibilityService.setFloatSuppressed(false);
            RippleAccessibilityService.setAgentGlow(false);
            finish();
        }
    };

    private void begin() {
        agentRunning = true;
        main.removeCallbacks(idleStop);
        main.removeCallbacks(pendingEnd);
        RippleAccessibilityService.setFloatSuppressed(true);
        RippleAccessibilityService.setAgentGlow(true);
        postStatus(L.t("AI 正在操作手機"), L.t("準備中…"), 0, 0);
        main.postDelayed(pendingEnd, PENDING_MS);
    }

    private final Runnable idleStop = () -> {
        if (clients == 0) finish();
    };

    @Override
    public void onCreate() {
        super.onCreate();
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
        token = loadToken();
        // 前景服務的類型是 Android 14（API 34）才有的要求；Android 13 不需要也不能傳
        android.app.Notification n0 = build(L.t("PhoneAI 待命中"), L.t("等待 agent2.py 連線"), 0, 0);
        if (android.os.Build.VERSION.SDK_INT >= 34) startForeground(NOTIF_ID, n0, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(NOTIF_ID, n0);
        addOverlay();
        startServer();
        main.postDelayed(idleStop, IDLE_MS);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String a = intent == null ? null : intent.getAction();
        A11yGuard.ensure(this);
        if (ACTION_TEST.equals(a)) runTest();
        else if (ACTION_STOP.equals(a)) stopAgent();
        else if (ACTION_BEGIN.equals(a)) begin();
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
        if (self == this) self = null;
        main.removeCallbacksAndMessages(null);
        try { if (server != null) server.close(); } catch (Exception ignored) { }
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

    private String loadToken() {
        File f = new File(getFilesDir(), "token");
        try {
            if (f.exists()) return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim();
            byte[] b = new byte[16];
            new SecureRandom().nextBytes(b);
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02x", x));
            try (OutputStream o = new FileOutputStream(f)) {
                o.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            }
            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "token", e);
            return null;
        }
    }

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

    private void startServer() {
        new Thread(() -> {
            try {
                server = new ServerSocket(PORT, 4, InetAddress.getByName("127.0.0.1"));
                while (true) {
                    Socket s = server.accept();
                    new Thread(() -> serve(s)).start();
                }
            } catch (Exception e) {
                Log.i(TAG, "server closed: " + e);
            }
        }).start();
    }

    private void serve(Socket s) {
        boolean authed = false;
        try (Socket sock = s;
             BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(sock.getOutputStream(), true)) {
            String first = in.readLine();
            if (token == null || first == null || !token.equals(first.trim())) {
                out.println("denied");
                return;
            }
            authed = true;
            main.post(() -> {
                clients++;
                agentRunning = true;
                main.removeCallbacks(idleStop);
                main.removeCallbacks(pendingEnd);
                RippleAccessibilityService.setFloatSuppressed(true);
                RippleAccessibilityService.setAgentGlow(true);
            });
            out.println("ok");
            String line;
            while ((line = in.readLine()) != null) {
                out.println(handle(new JSONObject(line)));
            }
        } catch (Exception e) {
            Log.i(TAG, "client: " + e);
        } finally {
            if (authed) main.post(() -> {
                if (--clients == 0) {
                    vdSf = "";
                    vdId = 0;
                    takeover = false;
                    takeoverEnded = false;
                    RippleAccessibilityService.hidePreview();
                    agentRunning = false;
                    RippleAccessibilityService.setFloatSuppressed(false);
                    RippleAccessibilityService.setAgentGlow(false);
                    finish();
                }
            });
        }
    }

    /** 在背景執行緒被呼叫；畫面相關的事丟回主執行緒。 */
    private String handle(JSONObject j) throws Exception {
        String cmd = j.optString("cmd");
        switch (cmd) {
            case "ripple":
                main.post(() -> { RippleView v = fx(j.optInt("display")); if (v != null) v.ripple(j.optInt("x"), j.optInt("y")); });
                return "ok";
            case "swipe":  // delay = 先等幾毫秒再畫（按住拖曳：按住結束後才畫軌跡）
                main.post(() -> { RippleView v = fx(j.optInt("display")); if (v != null) v.swipe(j.optInt("x1"), j.optInt("y1"), j.optInt("x2"), j.optInt("y2"), Math.max(0, j.optLong("delay"))); });
                return "ok";
            case "hold":  // 長按動畫：一圈慢慢往外，放手時剛好到最外圈
                main.post(() -> { RippleView v = fx(j.optInt("display")); if (v != null) v.hold(j.optInt("x"), j.optInt("y"), j.optLong("ms", 800)); });
                return "ok";
            case "tree": {  // 列出某個螢幕上的元件（文字、位置、能不能點）；回 JSON 陣列，服務沒開回 nosvc
                String t = RippleAccessibilityService.tree(j.optInt("display", 0), Math.min(300, j.optInt("max", 200)));
                return t == null ? "nosvc" : t;
            }
            case "gesture":  // 多指／長按／拖曳（無障礙服務的 dispatchGesture）；做完才回覆，回 ok / nocap / nosvc / fail
                return RippleAccessibilityService.gesture(j);
            case "hide":  // 截圖前：清掉漣漪（光暈不藏：整段任務都要在，AI 的說明裡有交代要忽略它）
                main.post(() -> {
                    RippleView v = fx();
                    if (v != null) v.clear();
                    if (view != null) view.clear();
                    RippleAccessibilityService.clearAll();  // 第二螢幕的漣漪也清掉，免得被截進畫面
                });
                return "ok";
            case "show":  // 舊版相容，什麼都不做
                return "ok";
            case "vd": {  // 背景虛擬螢幕出現／消失：sf 是截圖編號（只收數字），空字串 = 消失
                String sf = j.optString("sf");
                vdSf = sf.matches("\\d{1,20}") ? sf : "";
                vdId = j.optInt("id", 0);
                int w = j.optInt("w", 1080), h = j.optInt("h", 2340);
                if (w >= 200 && w <= 4000 && h >= 200 && h <= 6000) { vdW = w; vdH = h; }
                if (vdSf.isEmpty()) { vdId = 0; takeover = false; takeoverEnded = false; }
                main.post(() -> {
                    if (vdSf.isEmpty()) RippleAccessibilityService.hidePreview();
                    postStatus(title, lastText, lastStep, lastMax);  // 重發通知，更新「點一下顯示預覽」
                    RippleAccessibilityService.refreshGlowsNow();  // 背景模式開始／結束：主螢幕光暈跟著開關
                });
                return "ok";
            }
            case "takeover": {  // agent2 請使用者接手（例如要輸入密碼）：自動打開預覽並進入接手模式；on=false 結束
                boolean on = j.optBoolean("on");
                String why = j.optString("why");
                main.post(() -> {
                    applyTakeover(on, why);
                    RippleAccessibilityService.takeoverChanged();
                });
                return "ok";
            }
            case "paused":  // agent2 每一步開始前問：使用者是不是正在接手？yes = 接手中（AI 等）；done = 剛結束；no = 沒有
                if (takeover) return "yes";
                if (takeoverEnded) { takeoverEnded = false; return "done"; }
                return "no";
            case "status":
                if (j.has("title")) title = j.optString("title");
                main.post(() -> postStatus(title, j.optString("text"), j.optInt("step"), j.optInt("max")));
                return "ok";
            case "diag":
                return diagnose();
            case "apps": {
                org.json.JSONArray arr = new org.json.JSONArray();
                for (String line : AppListExporter.build(this).split("\n")) if (!line.isEmpty()) arr.put(line);
                return arr.toString();  // 單行 JSON，每個元素是「名稱\t套件」
            }
            case "report": {  // agent2 結束時回報一行摘要（任務、步數、用量、估價），主畫面顯示最近幾筆
                android.content.SharedPreferences sp = getSharedPreferences("cfg", MODE_PRIVATE);
                org.json.JSONArray old;
                try { old = new org.json.JSONArray(sp.getString("history", "[]")); } catch (Exception e) { old = new org.json.JSONArray(); }
                org.json.JSONArray now = new org.json.JSONArray();
                for (int k = Math.max(0, old.length() - 19); k < old.length(); k++) now.put(old.opt(k));
                now.put(new JSONObject().put("t", System.currentTimeMillis()).put("text", j.optString("text")));
                sp.edit().putString("history", now.toString()).apply();
                return "ok";
            }
            case "speak": {  // 朗讀（App 自己用系統語音合成）；使用者在首頁關掉朗讀就回 off
                if (!getSharedPreferences("cfg", MODE_PRIVATE).getBoolean("tts_on", true)) return "off";
                final String text = j.optString("text");
                if (text.isEmpty()) return "ok";
                main.post(() -> speak(text));
                return "ok";
            }
            case "reply": {  // 助理的文字回答：在主螢幕底部浮出一張卡片（不會自己消失）
                final String title = j.optString("title"), text = j.optString("text");
                main.post(() -> {
                    try {
                        AssistantSheet sheet = AssistantSheet.current;
                        if (sheet != null) {  // 面板還開著：光球變成卡片
                            sheet.showReply(title, text);
                            return;
                        }
                        startActivity(new Intent(this, AskActivity.class)   // 跟電源鍵面板同一個畫面（極光粒子、底邊光、輸入列），顯示回答卡片
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                                .putExtra("voice", false).putExtra("reply_text", text), mainDisplayOptions());
                    } catch (Exception e) {
                        Log.e(TAG, "reply start", e);
                    }
                });
                return "ok";
            }
            case "sheet_close":  // 助理判斷這句話要操作手機／開 App：把還在等回答的面板收起來
                main.post(() -> { AssistantSheet sh = AssistantSheet.current; if (sh != null) sh.closeIfWaiting(); });
                return "ok";
            case "confirm":
                return confirm(j.optString("msg"));
            case "ask_text":  // AI 要問使用者問題：跳出助理面板（語音／打字都行），回 {"text": 回答}；沒回答回空字串；面板沒跳出來回 unavailable
                return askText(j.optString("question"));
            case "end":
                main.post(this::finish);
                return "ok";
            default:
                return "unknown";
        }
    }

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

    /** 在連線執行緒呼叫，會等使用者回答（最多 5 分鐘）。 */
    private String askText(String question) {
        java.util.concurrent.CompletableFuture<String> ans = new java.util.concurrent.CompletableFuture<>();
        java.util.concurrent.CountDownLatch shown = new java.util.concurrent.CountDownLatch(1);
        AssistantSheet.questionAnswer = ans;
        AssistantSheet.questionShown = shown;
        main.post(() -> {
            try {
                startActivity(new Intent(this, AskActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                        .putExtra("question", question), mainDisplayOptions());
            } catch (Exception e) {
                Log.e(TAG, "ask_text start", e);
            }
        });
        try {
            if (!shown.await(6, java.util.concurrent.TimeUnit.SECONDS)) return "unavailable";
            String t = ans.get(300, java.util.concurrent.TimeUnit.SECONDS);
            return new org.json.JSONObject().put("text", t).toString();
        } catch (java.util.concurrent.TimeoutException e) {
            return "{\"text\":\"\"}";
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
        postStatus(title, L.t("正在停止…"), 0, 0);
        new Thread(() -> {
            boolean ok;
            try {
                // pkill 找不到行程時回傳 1（任務其實已經結束），這不是 root 的問題，所以先用 id -u 確認 root，再看有沒有東西被停掉。
                // 比對字串寫成 [a]gent2 是為了不要比對到 su 自己那一行指令。
                Process p = new ProcessBuilder(Root.su(), "-c", "id -u; pkill -f '[a]gent2\\.py'; pkill -f '[a]ssistant\\.py'; exit 0")
                        .redirectErrorStream(true).start();
                String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                p.waitFor();
                ok = out.startsWith("0");
            } catch (Exception e) {
                ok = false;
            }
            final boolean done = ok;
            main.post(() -> {
                if (done) finish();
                else postStatus(title, L.t("停止失敗：請允許 PhoneAI Overlay 使用 root"), 0, 0);
            });
        }).start();
    }

    /** App 裡的「測試」按鈕：示範通知更新、漣漪與滑動。 */
    private void runTest() {
        main.removeCallbacks(idleStop);
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
        main.postDelayed(() -> { if (clients == 0) finish(); }, 8000);
    }
}
