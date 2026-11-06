package com.phoneai.overlay;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import org.json.JSONObject;
import org.json.JSONArray;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.speech.RecognitionListener;
import android.speech.RecognitionService;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 仿 Gemini 的底部助理面板：變暗背景 + 極光/粒子 + 底部深色圓角面板（聲波、輸入框、麥克風／送出）。預設一開就聽（語音優先），也能點輸入框打字。
 * 語音用 App 內的 SpeechRecognizer（才能拿到音量畫動畫、邊說邊顯示文字）；沒有麥克風權限或找不到辨識服務時，退回系統的語音視窗。
 * 辨識完不立刻執行：倒數幾秒，期間改字或按取消就停止（聽錯直接執行很危險）。
 */
public class AssistantSheet {
    static final int REQ_MIC = 4712, REQ_FALLBACK = 4713;
    static final int COUNTDOWN_SECONDS = 1;   // 辨識完停 1 秒就自動送出（送出鈕外圈倒數）；這 1 秒內動輸入框就取消
    static final String GOOGLE_PKG = "com.google.android.googlequicksearchbox";

    private final Activity act;
    private final float d;
    private final FrameLayout root;
    private final View scrim;
    private final AuroraView aurora;
    private final LinearLayout content;
    private final WaveView wave;
    private final EditText input;
    private final ActionButtonView send;
    private final GlassBar row;
    /** 目前開著的面板（OverlayService 收到 reply／sheet_close 指令時用）。只在主執行緒讀寫。 */
    static AssistantSheet current;
    /** AI 要問使用者問題時（agent2 的 ask_user）：問題顯示在面板上方；左邊的背景模式鈕藏起來；送出後答案交還給 agent2，不是當成新任務。 */
    static volatile java.util.concurrent.CompletableFuture<String> questionAnswer;
    static volatile java.util.concurrent.CountDownLatch questionShown;
    private boolean questionMode;
    private TextView questionView;
    private View bgBtnView;
    private TextView partialView;
    private SpeechCapture capture;     // 目前正在錄的這一句（SenseVoice 用）
    private int voiceGen;              // 每次開始聽加一；辨識結果回來時對不上就丟掉（使用者已取消或重說）
    private boolean replyMode;   // 顯示回答卡片中：卡片在上方、下面是可以接續對話的輸入列（語音／打字）
    private boolean waiting, replyShown;
    private View replyCard;
    private String curSvc = "";
    private final Runnable waitTimeout = this::dismiss;

    /** 錯誤碼 12/13（語言不支援/不可用）時，依序換這幾種語言寫法再試；null = 不指定語言（用系統預設）。 */
    private static String[] langs() {   // 辨識語言的寫法（隨介面語言，不快取，所以切換語言不用重開）
        return L.en() ? new String[]{"en-US", null} : new String[]{"zh-TW", "cmn-Hant-TW", "zh-Hant-TW", "zh-CN", null};
    }
    private int langIdx;
    /** 要輪流試的辨識服務（不含我們自己的存根）。排序：先試系統預設（使用者在系統「語音輸入」裡選的，例如三星，有離線包），其餘照系統給的順序。
     *  最後一個位置（svcIdx == size）= 系統預設，會放在最前面試。 */
    private List<ComponentName> recSvcs = new ArrayList<>();
    private int svcIdx;
    private SpeechRecognizer recognizer;
    private CountDownTimer timer;
    private boolean listening, typing, dismissing, programmatic;
    private int bottomInset;
    // 圈選：面板一打開就截一張圖，直接在上面圈（不用按 ◎）；圈完的範圍會跟著下一次送出一起問 AI
    private boolean shotRequested;

    public AssistantSheet(Activity a) {
        act = a;
        d = a.getResources().getDisplayMetrics().density;
        root = new FrameLayout(a);

        // 背景：由上到下漸深的暗幕（不是一個黑色方塊），讓字在任何桌布上都看得清楚
        scrim = new View(a);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x40000000, 0x99000000, 0xDD000000}));
        scrim.setAlpha(0f);
        scrim.setOnClickListener(v -> dismiss());
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        aurora = new AuroraView(a);
        root.addView(aurora, new FrameLayout.LayoutParams(-1, -1));

        // 內容：透明，只有底部的面板
        content = new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding((int) (NARROW_DP * d), 0, (int) (NARROW_DP * d), (int) (14 * d));

        if (OverlayService.agentRunning) {  // 任務正在執行：面板上直接給「停止」，不用去拉通知欄
            TextView stop = new TextView(a);
            stop.setText(L.t("■  停止目前的任務"));
            stop.setTextColor(0xFFFFFFFF);
            stop.setTextSize(14);
            stop.setGravity(Gravity.CENTER);
            stop.setPadding((int) (18 * d), (int) (10 * d), (int) (18 * d), (int) (10 * d));
            GradientDrawable sb = new GradientDrawable();
            sb.setColor(0x99121218);
            sb.setStroke((int) Math.max(1, d), 0x66FFFFFF);
            sb.setCornerRadius(40 * d);
            stop.setBackground(sb);
            stop.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                act.startForegroundService(new Intent(act, OverlayService.class).setAction(OverlayService.ACTION_STOP));
                dismiss();
            });
            LinearLayout.LayoutParams stp = new LinearLayout.LayoutParams(-2, -2);
            stp.bottomMargin = (int) (6 * d);
            content.addView(stop, stp);
        }

        questionView = new TextView(a);   // 平常不顯示；AI 問問題時才出現在面板上方
        questionView.setTextColor(Color.WHITE);
        questionView.setTextSize(18);
        questionView.setGravity(Gravity.CENTER);
        questionView.setLineSpacing(0, 1.15f);
        questionView.setShadowLayer(8 * d, 0, 0, 0xCC000000);
        questionView.setMaxLines(6);
        questionView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        questionView.setPadding((int) (10 * d), (int) (8 * d), (int) (10 * d), (int) (4 * d));
        questionView.setVisibility(View.GONE);
        content.addView(questionView, new LinearLayout.LayoutParams(-1, -2));

        partialView = new TextView(a);   // 邊說邊顯示：SenseVoice 每約 0.8 秒辨識一次，顯示目前聽到的字
        partialView.setTextColor(Color.WHITE);
        partialView.setTextSize(20);
        partialView.setGravity(Gravity.CENTER);
        partialView.setLineSpacing(0, 1.15f);
        partialView.setShadowLayer(8 * d, 0, 0, 0xCC000000);
        partialView.setMaxLines(4);
        partialView.setPadding((int) (14 * d), (int) (8 * d), (int) (14 * d), (int) (10 * d));
        partialView.setVisibility(View.GONE);
        content.addView(partialView, new LinearLayout.LayoutParams(-1, -2));

        // 擴散的光暈與提示卡可能超出內容範圍：不要裁切
        content.setClipChildren(false);
        root.setClipChildren(false);
        wave = new WaveView(a);
        wave.setVisibility(View.GONE);
        wave.setOnClickListener(v -> typeNow());   // 點聲波 = 改成打字

        // 單一個玻璃膠囊面板（跟下面的底欄同一種液態玻璃，背景是極光；兩端半圓）：一列＝左邊背景模式鈕、中間輸入框（聽或思考時換成聲波）、右邊麥克風／送出
        final int accent = bright(App.accentColor(a, 0xFF6EA8FF));
        GlassBar glassRow = new GlassBar(a, 0xFF23262A, 0x00000000, 0x44FFFFFF, 32 * d, false);   // 圓角 32 = 一半高度（64dp）
        glassRow.setSource(root);   // 玻璃折射的是面板後面的畫面：暗幕與極光（別的 App 的畫面抓不到）
        glassRow.setGlowColor(0xFFFFFFFF);
        glassRow.setClipChildren(false);
        glassRow.setLive(true);     // 極光一直在動，玻璃要每幀更新
        row = glassRow;
        final LinearLayout bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding((int) (8 * d), (int) (8 * d), (int) (8 * d), (int) (8 * d));   // 高度 = 8 + 48 + 8 = 64dp；圓角 32 − 內距 8 = 按鈕半徑 24（官方的 optical roundness）
        row.addView(bar, new FrameLayout.LayoutParams(-1, -2));

        input = new EditText(a);
        input.setHintTextColor(0xA0FFFFFF);
        input.setTextColor(Color.WHITE);
        input.setTextSize(17);
        input.setBackground(null);                 // 底色由整個面板提供
        input.setPadding((int) (8 * d), (int) (10 * d), (int) (8 * d), (int) (10 * d));
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setSingleLine(true);
        input.setEllipsize(android.text.TextUtils.TruncateAt.END);   // 面板窄的時候提示文字太長就用 … 收尾
        input.setOnTouchListener((v, ev) -> {
            if (ev.getAction() == MotionEvent.ACTION_DOWN) enterTyping();  // 碰輸入框 = 要打字，語音停止
            return false;
        });
        input.setOnEditorActionListener((v, id, ev) -> {
            if (id == EditorInfo.IME_ACTION_SEND) { submit(); return true; }
            return false;
        });
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int af) { }
            public void onTextChanged(CharSequence s, int st, int b, int c) { }
            public void afterTextChanged(Editable e) {
                if (!programmatic) {
                    cancelCountdown();
                    if (listening) enterTyping();  // 偵測到打字：語音辨識立刻停止
                }
                refreshAction();
            }
        });
        // 背景模式開關：圖示鈕放在輸入列最左邊（跟右邊的送出鈕對稱，不再佔一整排）。開著 = 任務在第二螢幕執行，不動目前的畫面
        final android.content.SharedPreferences cfg = a.getSharedPreferences("cfg", Activity.MODE_PRIVATE);
        final BgIconView bgBtn = new BgIconView(a);
        bgBtn.setOn(cfg.getBoolean("bg_on", false));
        bgBtn.setVisibility(Root.knownRoot() && cfg.getBoolean("bg_feature", true) ? android.view.View.VISIBLE : android.view.View.GONE);   // 背景模式要 root：沒 root 不顯示；設定裡可以關掉這個按鈕
        bgBtn.setOnClickListener(v -> {
            boolean on = !cfg.getBoolean("bg_on", false);
            cfg.edit().putBoolean("bg_on", on).apply();
            bgBtn.setOn(on);
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);  // 不跳提示文字，只靠圖示變化
        });
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams((int) (44 * d), (int) (44 * d));
        blp.rightMargin = (int) (2 * d);
        bar.addView(bgBtn, blp);
        bgBtnView = bgBtn;
        FrameLayout center = new FrameLayout(a);
        center.addView(input, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_VERTICAL));
        center.addView(wave, new FrameLayout.LayoutParams(-1, (int) (40 * d), Gravity.CENTER));
        bar.addView(center, new LinearLayout.LayoutParams(0, (int) (48 * d), 1f));

        send = new ActionButtonView(a);
        send.setAccent(accent);
        send.setOnClickListener(v -> {   // 有字＝送出；沒字＝開始／停止聽
            if (input.getText().length() > 0 || circleBox != null) submit();
            else if (listening) stopListening();
            else startVoice();
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams((int) (48 * d), (int) (48 * d));
        slp.leftMargin = (int) (2 * d);
        bar.addView(send, slp);
        content.addView(row, new LinearLayout.LayoutParams(-1, -2));

        root.addView(content, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        // 邊緣到邊緣：自己處理鍵盤與導覽列的高度，讓內容永遠貼在鍵盤/導覽列上方
        act.getWindow().setDecorFitsSystemWindows(false);
        // 視窗要蓋到鏡頭挖孔／狀態列那一條，否則開場光波與最上緣的亮光會在最上面被切掉（使用者回報「最上面有點被裁掉」；推測的原因）
        android.view.WindowManager.LayoutParams wlp = act.getWindow().getAttributes();
        wlp.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        act.getWindow().setAttributes(wlp);
        act.getWindow().setStatusBarContrastEnforced(false);       // 不要讓系統在狀態列/導覽列加一層對比遮罩
        act.getWindow().setNavigationBarContrastEnforced(false);
        root.setOnApplyWindowInsetsListener((v, ins) -> {
            int ime = ins.getInsets(WindowInsets.Type.ime()).bottom;
            int nav = ins.getInsets(WindowInsets.Type.systemBars()).bottom;
            bottomInset = Math.max(ime, nav);
            content.setTranslationY(-bottomInset);
            updateGlowY();
            return ins;
        });
        content.addOnLayoutChangeListener((v, l, t, rr, b, ol, ot, orr, ob) -> updateGlowY());

        refreshAction();
        setStatus(L.t("直接說話，或在這裡打字"));
    }

    // ---------- 圈選提問：面板一打開就在背景截一張，直接在畫面上圈（圈完自動變長方形，可以調整） ----------
    private CircleCanvas canvas;
    private Bitmap shot;
    private RectF circleBox;

    /** 面板打開時由 AskActivity 呼叫：面板先藏著、先截圖（才不會拍到開場動畫），好了把圈選畫布放在面板後面，再呼叫 ready 讓面板出現。 */
    public void prefetchShot(final Runnable ready) {
        final boolean[] fired = {false};
        final Runnable fire = () -> { if (!fired[0]) { fired[0] = true; ready.run(); } };
        root.postDelayed(fire, 1500);   // 太久截不到就不等了，照常打開面板（這時才來的截圖會拍到面板，丟掉）
        CircleShot.prefetch(act.getApplicationContext(), b -> {
            if (fired[0]) return;
            if (b != null) attachShot(b);
            fire.run();
        });
    }

    private void attachShot(Bitmap b) {
        if (dismissing || b == null || questionMode) return;
        shot = b;
        if (canvas != null) { canvas.setShot(b); return; }
        canvas = new CircleCanvas(act, b, new CircleCanvas.Listener() {
            @Override public void onSelected(RectF box) {
                circleBox = box;
                circleConvo = new JSONArray();   // 選了新的區域：對話重來
                setStatus(L.t("已選取，說出或打字問題（空白也可以）"));
                refreshAction();
            }
            @Override public void onCleared() {
                circleBox = null;
                refreshAction();
            }
            @Override public void onTap() {
                if (circleBox != null) { circleBox = null; canvas.clearSel(); refreshAction(); }   // 點選取框外面：取消選取
                else dismiss();                                                                    // 沒有選取：跟點旁邊一樣收起面板
            }
        });
        root.addView(canvas, root.indexOfChild(aurora), new FrameLayout.LayoutParams(-1, -1));
    }

    private String circleCrop() {
        int pad = (int) (12 * d);
        int l = Math.max(0, (int) circleBox.left - pad), t = Math.max(0, (int) circleBox.top - pad);
        int rr = Math.min(shot.getWidth(), (int) circleBox.right + pad), bb = Math.min(shot.getHeight(), (int) circleBox.bottom + pad);
        Bitmap crop = (rr - l < 8 || bb - t < 8) ? shot : Bitmap.createBitmap(shot, l, t, rr - l, bb - t);
        return CircleShot.jpeg64(crop, 1280, 85);
    }

    private JSONArray circleConvo = new JSONArray();   // 這一個選取區域的整段對話（第一輪有圖，之後接著問都帶著，才有上下文）

    /** 有選取：把選取的區域（加一張縮小的整個畫面當上下文）和問題一起交給聊天模型；之後不用再選，直接接著問（同一段對話）。 */
    private void askCircle(final String q, final boolean followUp) {
        try {
            JSONArray content = new JSONArray();
            if (!followUp) {
                circleConvo = new JSONArray();
                content.put(CircleShot.img(circleCrop()));
                content.put(CircleShot.img(CircleShot.jpeg64(shot, 800, 70)));
                content.put(new JSONObject().put("type", "text").put("text", (L.en()
                        ? "The first image is the region the user selected; the second is the whole screen for context. The user's question: "
                        : "第一張圖是使用者選取的區域，第二張是整個畫面（給你看上下文）。使用者的問題：")
                        + (q.isEmpty() ? (L.en() ? "What is this? Explain briefly and add useful related information." : "這是什麼？請簡短說明，並補充有用的相關資訊。") : q)));
            } else {
                content.put(new JSONObject().put("type", "text").put("text", q));
            }
            circleConvo.put(new JSONObject().put("role", "user").put("content", content));
        } catch (Exception e) {
            return;
        }
        circleBox = null;
        if (canvas != null) canvas.clearSel();
        stopRecognizer();
        hideKeyboard();
        setText("");
        setStatus(L.t("思考中…"));
        setWaveState(WaveView.THINKING);
        final Context app = act.getApplicationContext();
        final JSONArray convo = circleConvo;
        new Thread(() -> {
            String r = ChatEngine.visionChat(app, convo, true);
            act.runOnUiThread(() -> {
                if (dismissing) return;
                setWaveState(WaveView.IDLE);
                if (r == null) {
                    if (convo.length() > 0) convo.remove(convo.length() - 1);   // 失敗的這一輪不留，重送才不會疊兩次
                    showReply("", L.t("連不上 AI：") + ChatEngine.lastError);
                } else {
                    try { convo.put(new JSONObject().put("role", "assistant").put("content", r)); } catch (Exception ignored) { }
                    showReply("", r);
                }
            });
        }, "circle-ask-panel").start();
    }

    public View view() { return root; }

    /** 進入「回答問題」模式：顯示 AI 的問題，藏起左邊的背景模式鈕（回答問題用不到），語音／打字都可以。面板已經開著時也能呼叫（重新開始）。 */
    public void setQuestion(String q) {
        questionMode = true;
        questionView.setText(q);
        questionView.setVisibility(View.VISIBLE);
        bgBtnView.setVisibility(View.GONE);
        waiting = false;
        root.removeCallbacks(waitTimeout);
        row.animate().cancel();
        row.setAlpha(1f);
        row.setTranslationY(0f);
        row.setEnabled(true);
        setWaveState(WaveView.IDLE);
        setStatus(L.t("說出或打字你的回答"));
        java.util.concurrent.CountDownLatch l = questionShown;
        if (l != null) l.countDown();
    }

    private void answerQuestion(String t) {
        java.util.concurrent.CompletableFuture<String> f = questionAnswer;
        if (f != null) f.complete(t);
        stopRecognizer();
        hideKeyboard();
        root.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
        dismiss();
    }

    /** 沒回答就關掉面板（返回、點旁邊、被滑掉）＝取消：把空字串交還給 agent2。 */
    private void cancelQuestion() {
        java.util.concurrent.CompletableFuture<String> f = questionAnswer;
        if (questionMode && f != null) f.complete("");
    }

    // ---------- 進場 / 離場 ----------

    /** 開場：先從螢幕最上方放出光波、灑下光點（AuroraView.playIntro），波過去後面板再浮上來。 */
    /** 首頁輸入框送來的文字：直接填進面板並送出。 */
    /** AI 要問使用者問題：叫出面板顯示問題（語音或打字都可以），等回答。回 null = 叫不出面板；空字串 = 使用者取消。在背景執行緒呼叫。 */
    static String askUser(android.content.Context app, String question) {
        java.util.concurrent.CompletableFuture<String> f = new java.util.concurrent.CompletableFuture<>();
        java.util.concurrent.CountDownLatch shown = new java.util.concurrent.CountDownLatch(1);
        questionAnswer = f;
        questionShown = shown;
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            try {
                app.startActivity(new Intent(app, AskActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("question", question));
            } catch (Exception e) {
                shown.countDown();
                f.complete(null);
            }
        });
        try {
            if (!shown.await(5, java.util.concurrent.TimeUnit.SECONDS)) return null;
            return f.get(180, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            return null;
        } finally {
            questionAnswer = null;
            questionShown = null;
        }
    }

    public void presetAndSend(String t) {
        setText(t);
        submit();
    }

    /** 分享過來的文字：只填進輸入框，等使用者按送出。 */
    public void fillText(String t) {
        setText(t);
    }

    public void enter() {
        current = this;

        root.performHapticFeedback(HapticFeedbackConstants.GESTURE_START);  // 開啟時輕輕震一下
        aurora.playIntro();
        scrim.animate().alpha(1f).setDuration(450).start();
        content.setAlpha(0f);
        ValueAnimator in = ValueAnimator.ofFloat(0f, 1f);
        in.setStartDelay(380);
        in.setDuration(560);
        in.setInterpolator(new DecelerateInterpolator(2f));
        in.addUpdateListener(an -> {  // 每一幀都用「當下」的鍵盤/導覽列高度，不怕 insets 比動畫晚到
            float p = (float) an.getAnimatedValue();
            content.setAlpha(p);
            content.setTranslationY((1f - p) * 110 * d - bottomInset);
        });
        in.start();
    }

    public void dismiss() {
        Notify.stopSpeaking();
        if (dismissing) return;
        dismissing = true;
        cancelQuestion();
        cancelCountdown();
        stopRecognizer();
        scrim.animate().alpha(0f).setDuration(240).start();
        if (replyCard != null) replyCard.animate().alpha(0f).translationY(200 * d).setDuration(260).start();
        ObjectAnimator a = ObjectAnimator.ofFloat(content, View.TRANSLATION_Y, content.getTranslationY(), 500 * d);
        a.setDuration(280);
        a.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator an) { act.finish(); act.overridePendingTransition(0, 0); }
        });
        a.start();
    }

    public void destroy() {
        Notify.stopSpeaking();   // 退出面板就停止朗讀
        cancelQuestion();
        if (current == this) current = null;
        root.removeCallbacks(waitTimeout);
        cancelCountdown();
        stopRecognizer();
    }

    private void updateGlowY() {
        if (content.getHeight() > 0 && root.getHeight() > 0) {
            aurora.setGlowY(root.getHeight() - bottomInset - 40 * d);  // 光暈貼著畫面底部，像 Gemini 的底邊光
        }
    }

    // ---------- 語音 ----------

    public void startVoice() {
        if (dismissing) return;
        Notify.stopSpeaking();   // 要說話就先打斷朗讀
        typing = false;
        hideKeyboard();
        cancelCountdown();
        if (act.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            act.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            setStatus(L.t("需要麥克風權限"));
            return;
        }
        if (SherpaEngine.ready(act)) {   // 已下載 SenseVoice：App 自己錄音、自己辨識（三星與小米都用這條），不需要網路
            startSherpa();
            return;
        }
        if (act.getSharedPreferences("cfg", Activity.MODE_PRIVATE).getBoolean("voice_sys", false)) {
            trace(L.t("直接用系統語音視窗（主畫面勾選，或先前 App 內辨識都失敗而自動記住）"));
            fallbackDialog(false);
            return;
        }
        recSvcs = pickRecognizers();
        trace(L.t("可用辨識服務 ") + recSvcs.size() + L.t(" 個：") + recSvcs);
        svcIdx = recSvcs.size();  // 從「系統預設」開始；失敗才往前面的其他服務走
        langIdx = 0;
        beginRecognition();
    }

    /** SenseVoice：錄完一句（停頓約 1 秒結束）再整段辨識，說完才出字。錄音與辨識都在 App 內，回呼都在主執行緒。 */
    private void startSherpa() {
        stopRecognizer();
        final int gen = ++voiceGen;
        trace(L.t("使用 SenseVoice（App 內離線辨識）"));
        listening = true;
        if (continueMode) carryText = input.getText().toString(); else { carryText = ""; setText(""); }   // 上一段撞到錄音上限：保留已經辨識出來的字，接著說的字接在後面
        setStatus(L.t("我在聽…"));
        setWaveState(WaveView.LISTENING);
        refreshAction();
        capture = SpeechCapture.start(act, new SpeechCapture.Cb() {
            @Override public void onListening() { }
            @Override public void onLevel(float v) {
                if (!listening || gen != voiceGen) return;
                wave.setLevel(v);
                aurora.setEnergy(0.35f + 0.65f * v);
            }
            @Override public void onDone(short[] pcm) {
                if (gen != voiceGen) return;
                capture = null;
                listening = false;
                if (pcm == null) { idleMessage(L.t("沒聽到聲音，點右邊的麥克風再說一次")); return; }
                setWaveState(WaveView.THINKING);
                setStatus(L.t("辨識中…"));
                new Thread(() -> {
                    String text = null, err = null;
                    try {
                        text = SpeechCapture.recognizeFinal(act, pcm);   // 前面分段辨識好的 ＋ 最後一段
                    } catch (Throwable e) {
                        err = String.valueOf(e.getMessage() != null ? e.getMessage() : e);
                    }
                    final String t = text, e2 = err;
                    act.runOnUiThread(() -> {
                        if (gen != voiceGen) return;
                        if (e2 != null) { trace(L.t("SenseVoice 錯誤：") + e2); idleMessage(L.t("語音辨識失敗（") + e2 + L.t("），可以改用打字")); }
                        else if (t == null || t.isEmpty()) idleMessage(L.t("沒聽清楚，點右邊的麥克風再說一次"));
                        else gotText(t);
                    });
                }, "sensevoice").start();
            }
            @Override public void onError(String msg) {
                if (gen != voiceGen) return;
                capture = null;
                listening = false;
                trace(L.t("錄音錯誤：") + msg);
                idleMessage(L.t("錄音失敗（") + msg + L.t("）"));
            }
        }, t -> { if (listening && gen == voiceGen) showPartial(t); });
    }

    private void showPartial(String t) {
        // 太長只留最後 60 字（前面加「…」）：以前靠 TextView 的 ellipsize=START，多行時前面的字會莫名消失
        partialView.setText(t.length() > 60 ? "…" + t.substring(t.length() - 60) : t);
        partialView.setVisibility(View.VISIBLE);
    }

    /** 目前這個辨識服務不行（連不上、語言都不支援…）：換下一個；全部用完退回系統語音視窗。 */
    private void nextService(String why) {
        svcIdx = svcIdx == recSvcs.size() ? 0 : svcIdx + 1;  // 系統預設失敗 → 其他服務；其他都試過 → 退回系統視窗
        langIdx = 0;
        if (svcIdx >= recSvcs.size()) { fallbackDialog(); return; }
        setStatus(why + L.t("，換另一個辨識服務…"));
        beginRecognition();
    }

    private void beginRecognition() {
        stopRecognizer();
        try {
            recognizer = svcIdx < recSvcs.size()
                    ? SpeechRecognizer.createSpeechRecognizer(act, recSvcs.get(svcIdx))
                    : SpeechRecognizer.createSpeechRecognizer(act);   // 系統預設
            recognizer.setRecognitionListener(new Listener());
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            if (langs()[langIdx] != null) i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, langs()[langIdx]);
            curSvc = svcIdx < recSvcs.size() ? recSvcs.get(svcIdx).flattenToShortString() : L.t("系統預設");
            trace(L.t("開始辨識：服務=") + curSvc + L.t("，語言=") + langs()[langIdx]);
            recognizer.startListening(i);
            listening = true;
            setText("");
            setStatus(L.t("我在聽…"));
            setWaveState(WaveView.LISTENING);
            refreshAction();
        } catch (Exception e) {
            trace(L.t("啟動失敗：") + e);
            nextService(L.t("辨識服務啟動失敗"));
        }
    }

    /** 不用我們自己的存根辨識服務（我們是「預設助理」時系統預設辨識可能被指到我們）；Google 的排前面，其餘照系統給的順序。 */
    private List<ComponentName> pickRecognizers() {
        List<ComponentName> out = new ArrayList<>();
        try {
            List<ResolveInfo> l = act.getPackageManager().queryIntentServices(
                    new Intent(RecognitionService.SERVICE_INTERFACE), 0);
            for (ResolveInfo ri : l) {
                ServiceInfo si = ri.serviceInfo;
                if (si.packageName.equals(act.getPackageName())) continue;
                ComponentName cn = new ComponentName(si.packageName, si.name);
                if (GOOGLE_PKG.equals(si.packageName)) out.add(0, cn); else out.add(cn);
            }
        } catch (Exception ignored) { }
        // 實測（小米 13 / Android 17 官改）：Google App 有 GoogleRecognitionService，但系統清單沒列它（query-services 看不到）。
        // 所以不管清單有沒有，都把它直接指定放在最前面試；真的連不上會回「用戶端錯誤」，照舊換下一個服務。
        ComponentName g = new ComponentName(GOOGLE_PKG, "com.google.android.voicesearch.serviceapi.GoogleRecognitionService");
        if (!out.contains(g)) out.add(0, g);
        return out;
    }

    /** 語音辨識過程記錄（診斷用）：存最近 10 筆在設定檔裡，主畫面會顯示；同時寫進 logcat（標籤 PhoneAIVoice）。 */
    private void trace(String msg) {
        android.util.Log.w("PhoneAIVoice", msg);
        try {
            android.content.SharedPreferences sp = act.getSharedPreferences("cfg", Activity.MODE_PRIVATE);
            org.json.JSONArray old;
            try { old = new org.json.JSONArray(sp.getString("voice_trace", "[]")); } catch (Exception e) { old = new org.json.JSONArray(); }
            org.json.JSONArray now = new org.json.JSONArray();
            for (int k = Math.max(0, old.length() - 9); k < old.length(); k++) now.put(old.opt(k));
            now.put(new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(new java.util.Date()) + " " + msg);
            sp.edit().putString("voice_trace", now.toString()).apply();
        } catch (Exception ignored) { }
    }

    private void fallbackDialog() { fallbackDialog(true); }

    /** remember = true：這是 App 內辨識全部失敗才退回的，記住「這支手機用系統視窗」，下次直接跳（主畫面可取消勾選）。 */
    private void fallbackDialog(boolean remember) {
        if (remember) {   // 連續失敗 3 次才記住「這支手機用系統視窗」；偶爾一次失敗（麥克風被佔用、服務暫時忙）不該永久切換（使用者回報一次失敗就卡在系統視窗）
            android.content.SharedPreferences sp = act.getSharedPreferences("cfg", Activity.MODE_PRIVATE);
            int n = sp.getInt("voice_fail", 0) + 1;
            android.content.SharedPreferences.Editor ed = sp.edit().putInt("voice_fail", n);
            if (n >= 3) ed.putBoolean("voice_sys", true);
            ed.apply();
            trace(L.t("App 內辨識失敗（連續第 ") + n + L.t(" 次）") + (n >= 3 ? L.t("，之後改用系統視窗") : L.t("，還會再試 App 內辨識")));
        }
        trace(L.t("退回系統語音視窗"));
        try {
            act.startActivityForResult(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, L.en() ? "en-US" : "zh-TW")
                    .putExtra(RecognizerIntent.EXTRA_PROMPT, L.t("請說要做什麼")), REQ_FALLBACK);
        } catch (ActivityNotFoundException e) {
            setStatus(L.t("這支手機沒有語音辨識，請用下面的輸入框打字"));
            setWaveState(WaveView.IDLE);
        }
    }

    public void onPermissionResult(int req, int[] grants) {
        if (req != REQ_MIC) return;
        if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) startVoice();
        else fallbackDialog(false);
    }

    public void onActivityResult(int req, int res, Intent data) {
        if (req != REQ_FALLBACK) return;
        if (res == Activity.RESULT_OK && data != null) {
            ArrayList<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (r != null && !r.isEmpty()) gotText(r.get(0));
        }
    }

    private void stopListening() {
        if (capture != null) capture.stop();   // SenseVoice：立刻結束錄音，交出目前聽到的再辨識
        if (recognizer != null) recognizer.stopListening();
        setWaveState(WaveView.THINKING);
        setStatus(L.t("辨識中…"));
    }

    private void stopRecognizer() {
        listening = false;
        aurora.setEnergy(0.3f);
        voiceGen++;   // 讓還在辨識中的結果失效
        if (capture != null) { capture.cancel(); capture = null; }
        if (recognizer != null) {
            try { recognizer.cancel(); recognizer.destroy(); } catch (Exception ignored) { }
            recognizer = null;
        }
    }

    private class Listener implements RecognitionListener {
        @Override public void onReadyForSpeech(Bundle b) { }
        @Override public void onBeginningOfSpeech() { }
        @Override public void onBufferReceived(byte[] buf) { }
        @Override public void onEvent(int t, Bundle b) { }

        @Override public void onRmsChanged(float rms) {
            float v = Math.max(0f, Math.min(1f, (rms + 2f) / 12f));  // 約 -2 ~ 10 dB
            wave.setLevel(v);
            aurora.setEnergy(0.35f + 0.65f * v);
        }

        @Override public void onEndOfSpeech() {
            listening = false;
            setWaveState(WaveView.THINKING);
            setStatus(L.t("辨識中…"));
        }

        @Override public void onPartialResults(Bundle b) {
            ArrayList<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (r != null && !r.isEmpty()) setText(r.get(0));  // 邊說邊顯示在輸入框裡
        }

        @Override public void onResults(Bundle b) {
            listening = false;
            ArrayList<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (r != null && !r.isEmpty()) gotText(r.get(0));
            else idleMessage(L.t("沒聽清楚，點右邊的麥克風再說一次"));
        }

        @Override public void onError(int e) {
            listening = false;
            trace(L.t("錯誤 ") + errName(e) + L.t("，服務=") + curSvc);
            if (e == SpeechRecognizer.ERROR_NO_MATCH || e == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                idleMessage(L.t("沒聽到聲音，點右邊的麥克風再說一次"));
            } else if (e == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                fallbackDialog();
            } else if ((e == 12 || e == 13) && langIdx + 1 < langs().length) {  // 語言不支援/不可用：換下一種寫法再試
                langIdx++;
                setStatus(L.t("換一種語言設定再試…"));
                beginRecognition();
            } else if (e == 8) {
                idleMessage(L.t("辨識服務忙碌（8），稍後點麥克風再試"));
            } else {
                nextService(errName(e));  // 連不上/語言都不行/其他錯誤：換下一個辨識服務，全部失敗會退回系統語音視窗
            }
        }
    }

    private static String errName(int e) {
        switch (e) {
            case 1: return L.t("網路逾時（1）");
            case 2: return L.t("網路錯誤（2）");
            case 3: return L.t("麥克風錯誤（3）");
            case 4: return L.t("辨識服務錯誤（4）");
            case 5: return L.t("用戶端錯誤（5）");
            case 8: return L.t("辨識服務忙碌（8）");
            case 10: return L.t("請求太多（10）");
            case 11: return L.t("辨識服務中斷（11）");
            case 12: return L.t("語言不支援（12）");
            case 13: return L.t("語言不可用（13）");
            default: return L.t("代碼 ") + e;
        }
    }

    private void idleMessage(String s) {
        setWaveState(WaveView.IDLE);
        aurora.setEnergy(0.3f);
        setStatus(s);
        refreshAction();
    }

    // ---------- 文字 / 送出 ----------

    private boolean continueMode;
    private String carryText = "";

    private void gotText(String t) {
        if (continueMode) t = carryText + t;
        continueMode = SpeechCapture.lastHitCap;
        if (continueMode) {   // 撞到 30 秒上限：話多半還沒說完，不要自動送出；字留在輸入框，按麥克風接著說、或按送出
            LifeLog.add(act, "面板辨識完成 " + t.length() + " 字，但到了錄音上限，不自動送出");
            setText(t);
            setWaveState(WaveView.IDLE);
            aurora.setEnergy(0.3f);
            idleMessage(L.t("到了錄音上限，按麥克風接著說，或按送出"));
            return;
        }
        act.getSharedPreferences("cfg", Activity.MODE_PRIVATE).edit().putInt("voice_fail", 0).apply();   // 辨識成功：連續失敗次數歸零
        LifeLog.add(act, "面板辨識完成 " + t.length() + " 字，開始倒數 " + COUNTDOWN_SECONDS + " 秒");
        setText(t);
        setWaveState(WaveView.IDLE);
        aurora.setEnergy(0.3f);
        startCountdown();
    }

    private void startCountdown() {
        cancelCountdown();
        long total = COUNTDOWN_SECONDS * 1000L;
        timer = new CountDownTimer(total, 50) {
            public void onTick(long ms) { send.setProgress(ms / (float) total); }  // 送出鈕外圈細線慢慢縮短
            public void onFinish() { send.setProgress(0f); LifeLog.add(act, "面板倒數結束，自動送出"); submit(); }
        };
        timer.start();
    }

    private void cancelCountdown() {
        if (timer != null) { LifeLog.add(act, "面板倒數被取消"); timer.cancel(); timer = null; }
        send.setProgress(0f);
    }

    private void enterTyping() {
        typing = true;
        cancelCountdown();
        stopRecognizer();
        setWaveState(WaveView.IDLE);
        wave.setLevel(0f);
        setStatus(L.t("打好按右邊的送出"));
        refreshAction();
    }

    private void submit() {
        LifeLog.add(act, "面板送出（" + input.getText().length() + " 字）");
        Notify.stopSpeaking();
        cancelCountdown();
        String t = input.getText().toString().trim();
        if (t.isEmpty() && circleBox == null) return;
        if (questionMode) { answerQuestion(t); return; }
        if (circleBox != null && shot != null) { askCircle(t, false); return; }   // 有選取：這次問的是選起來的地方
        if (circleConvo.length() > 0 && !t.isEmpty()) { askCircle(t, true); return; }   // 剛問完圈選：接著問就是同一段對話
        // 先用關鍵字找固定指令；沒命中交給 Haiku 路由（在背景做，等送出的畫面狀態排完再開始）
        Assist.Route route = Assist.route(t);
        root.post(() -> runLocal(route, t));
        stopRecognizer();
        hideKeyboard();
        root.performHapticFeedback(HapticFeedbackConstants.CONFIRM);        // 送出時再震一下
        setText("");
        aurora.setEnergy(1f);
        if (replyMode) {   // 接續對話：舊的回答卡淡掉等新的；輸入列留著但先停用；新的回答到了（showReply）會換掉卡片、恢復輸入列
            replyShown = false;
            waiting = true;
            if (replyCard != null) replyCard.animate().alpha(0.3f).setDuration(240).start();
            row.setEnabled(false);
            input.setEnabled(false);
            row.animate().alpha(0.5f).setDuration(200).start();
            setStatus(L.t("思考中…"));
            setWaveState(WaveView.THINKING);
            refreshAction();
            root.postDelayed(waitTimeout, 30000);
            return;
        }
        // 送出後面板不關：輸入換成思考中的聲波、按鈕停用。助理如果是用文字回答，會出現回答卡片；
        // 如果是要操作手機或開 App，操作開始時會把面板收起來。30 秒都沒消息就自己收起來。
        waiting = true;
        input.setEnabled(false);
        setWaveState(WaveView.THINKING);
        row.setEnabled(false);
        root.postDelayed(waitTimeout, 30000);
    }

    /** 助理文字回答到了：卡片（沒有標題、沒有關閉鈕）出現在畫面中上方，面板留著，可以接續對話。 */
    /** 固定指令、Haiku 路由判斷後的動作、或聊天：在背景做（交給 Brain.answer，跟首頁共用同一套流程），回答到了用回答卡片顯示並唸出來。 */
    private void runLocal(Assist.Route r, String t) {
        new Thread(() -> {
            TransferNotify.onStart = () -> act.runOnUiThread(AssistantSheet.this::dismiss);   // 傳檔案開始：面板收起來，進度看通知
            String reply;
            try {
                reply = Brain.answer(act, t, new AgentLoop.Ui() {
                @Override public void hide() { act.runOnUiThread(AssistantSheet.this::dismiss); }
                @Override public void status(String s) { }   // 進度由 Now Bar 顯示（OverlayService.showStep）；不要再發同編號的一般通知把它蓋掉
                @Override public boolean confirm(String msg) { return confirmOnUi(msg); }
                @Override public String ask(String q) { return askUser(act.getApplicationContext(), q); }
            });
            } finally {
                TransferNotify.onStart = null;
            }
            if (reply == null) return;   // 沒有回覆（不該發生）
            final String fr = reply;
            Notify.say(act, fr);
            act.runOnUiThread(() -> {
                if (!dismissing) { showReply("", fr); return; }
                // 面板已經收起來（自動操作時會收）：搜尋結果這類較長的回答，重新叫出面板顯示回答卡片
                if (AgentLoop.showCard) AnswerCard.show(act, fr);
            });
        }, "local-reply").start();
    }

    /** 在主執行緒跳確認框，背景執行緒等使用者按完。 */
    private boolean confirmOnUi(String msg) {
        return ConfirmActivity.ask(act, msg);
    }

    public void showReply(String title, String text) {
        if (dismissing) return;
        if (replyCard != null) {  // 已經有回答卡（接續對話的下一則、彩蛋的第二張）：換掉舊的
            root.removeView(replyCard);
            replyCard = null;
        }
        replyShown = true;
        replyMode = true;
        waiting = false;
        root.removeCallbacks(waitTimeout);
        hideKeyboard();
        root.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
        stopRecognizer();
        // 面板留著（點麥克風可語音接續、打字也可以）；背景模式鈕也留著（接續的話可能是要操作手機的任務）
        setWaveState(WaveView.IDLE);
        wave.setLevel(0f);
        row.animate().cancel();
        row.setEnabled(true);
        row.setAlpha(1f);
        row.setTranslationY(0f);
        input.setEnabled(true);
        setText("");
        setStatus(L.t("繼續問…"));
        aurora.setEnergy(0.45f);
        final int topPad = (int) (56 * d), botPad = (int) (150 * d) + bottomInset;
        final int maxH = Math.max((int) (160 * d), root.getHeight() - topPad - botPad);   // 狀態列下面到輸入列上面之間能用的高度
        final FrameLayout card = ReplyCard.build(act, title, text, this::dismiss, maxH);
        replyCard = card;
        // 卡片放在「狀態列下面」到「輸入列上面」這一段的中間偏上，不要貼著底部（使用者回報太偏下）
        final FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        cp.setMargins((int) (14 * d), topPad, (int) (14 * d), botPad);
        card.setLayoutParams(cp);
        card.setAlpha(0f);
        card.setTranslationY(36 * d);
        card.setScaleX(0.97f);
        card.setScaleY(0.97f);
        root.addView(card, cp);
        card.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(400)
                .setInterpolator(new DecelerateInterpolator(2.2f)).start();
    }

        private void setText(String t) {
        programmatic = true;
        input.setText(t);
        input.setSelection(input.getText().length());
        programmatic = false;
    }

    private void refreshAction() {
        boolean hasText = (input != null && input.getText().length() > 0) || circleBox != null;   // 有選取時空白也能送出
        send.setMode(hasText ? ActionButtonView.SEND : listening ? ActionButtonView.LISTENING : ActionButtonView.MIC);
        send.setEnabled(!waiting);
        send.setAlpha(waiting ? 0.35f : 1f);   // 思考中不能按
        updateWidth();
    }

    /** 聲波條的狀態：聽與思考時顯示聲波（蓋住輸入框），其他時候顯示輸入框。 */
    private void setWaveState(int st) {
        wave.setState(st);
        if (st != WaveView.LISTENING) partialView.setVisibility(View.GONE);   // 聽完就收起邊說邊顯示的字，最後的字放進輸入框
        waveState = st;
        boolean show = st == WaveView.LISTENING || st == WaveView.THINKING;
        wave.setVisibility(show ? View.VISIBLE : View.GONE);
        input.setVisibility(show ? View.INVISIBLE : View.VISIBLE);
        refreshAction();
    }

    private static final int NARROW_DP = 46, WIDE_DP = 14;   // 面板左右留白：聽與待機時窄，打字或有字時展開
    private boolean wideNow;
    private int waveState = WaveView.IDLE;
    private android.animation.ValueAnimator widthAnim;

    /** 聽或思考時面板窄一點（只是聲波和兩顆按鈕）；打字或有字時展開，輸入框才有地方寫。 */
    private void updateWidth() {
        boolean listeningOrThinking = waveState == WaveView.LISTENING || waveState == WaveView.THINKING;
        boolean wide = !listeningOrThinking && (typing || (input != null && input.getText().length() > 0));
        if (wide == wideNow) return;
        wideNow = wide;
        if (widthAnim != null) widthAnim.cancel();
        final int from = content.getPaddingLeft(), to = (int) ((wide ? WIDE_DP : NARROW_DP) * d);
        widthAnim = android.animation.ValueAnimator.ofInt(from, to);
        widthAnim.setDuration(240);
        widthAnim.setInterpolator(new DecelerateInterpolator(2f));
        widthAnim.addUpdateListener(an -> {
            int p = (int) an.getAnimatedValue();
            content.setPadding(p, 0, p, content.getPaddingBottom());
        });
        widthAnim.start();
    }

    /** 兩個顏色按比例混合：t = 0 全是 a，t = 1 全是 b。 */
    private static int blend(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    /** 主色太暗的話混一點白，在深色面板上才看得到（聽的時候的實心圓用它）。 */
    private static int bright(int c) {
        float lum = (0.299f * ((c >> 16) & 255) + 0.587f * ((c >> 8) & 255) + 0.114f * (c & 255)) / 255f;
        return lum < 0.45f ? blend(c, 0xFFFFFFFF, 0.35f) : c;
    }

    private void typeNow() {
        enterTyping();
        input.requestFocus();
        showKeyboard();
    }

    private void showKeyboard() {
        android.view.inputmethod.InputMethodManager imm = act.getSystemService(android.view.inputmethod.InputMethodManager.class);
        if (imm != null) imm.showSoftInput(input, 0);
    }

    /** 狀態文字顯示在輸入框的提示（沒字的時候才看得到）。 */
    private void setStatus(String s) { input.setHint(s); }

    private void hideKeyboard() {
        android.view.inputmethod.InputMethodManager imm = act.getSystemService(android.view.inputmethod.InputMethodManager.class);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        input.clearFocus();
    }

    // ---------- 小工具 ----------

    private TextView text(int sp, int color, boolean bold) {
        TextView tv = new TextView(act);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setGravity(Gravity.CENTER);
        if (bold) tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return tv;
    }

    private GradientDrawable pill(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(40 * d);
        return g;
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
