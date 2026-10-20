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
import android.graphics.Color;
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
 * 仿 Gemini 的底部助理面板：變暗背景 + 極光/粒子 + 漸層光球 + 底下輸入框。預設一開就聽（語音優先），也能點輸入框打字。
 * 語音用 App 內的 SpeechRecognizer（才能拿到音量畫動畫、邊說邊顯示文字）；沒有麥克風權限或找不到辨識服務時，退回系統的語音視窗。
 * 辨識完不立刻執行：倒數幾秒，期間改字或按取消就停止（聽錯直接執行很危險）。
 */
public class AssistantSheet {
    static final int REQ_MIC = 4712, REQ_FALLBACK = 4713;
    static final int COUNTDOWN_SECONDS = 5;
    static final String GOOGLE_PKG = "com.google.android.googlequicksearchbox";

    private final Activity act;
    private final float d;
    private final FrameLayout root;
    private final View scrim;
    private final AuroraView aurora;
    private final LinearLayout content;
    private final OrbView orb;
    private final EditText input;
    private final SendIconView send;
    private final LinearLayout row;
    /** 目前開著的面板（OverlayService 收到 Termux 的 reply／sheet_close 指令時用）。只在主執行緒讀寫。 */
    static AssistantSheet current;
    /** AI 要問使用者問題時（agent2 的 ask_user）：問題顯示在光球上方；左邊的背景模式鈕藏起來；送出後答案交還給 agent2，不是當成新任務。 */
    static volatile java.util.concurrent.CompletableFuture<String> questionAnswer;
    static volatile java.util.concurrent.CountDownLatch questionShown;
    private boolean questionMode;
    private TextView questionView;
    private View bgBtnView;
    private SpeechCapture capture;     // 目前正在錄的這一句（SenseVoice 用）
    private int voiceGen;              // 每次開始聽加一；辨識結果回來時對不上就丟掉（使用者已取消或重說）
    private boolean replyMode;   // 顯示回答卡片中：卡片在上方、下面是可以接續對話的輸入列（語音／打字）
    private boolean waiting, replyShown;
    private View replyCard;
    private String curSvc = "";
    private final Runnable waitTimeout = this::dismiss;

    /** 錯誤碼 12/13（語言不支援/不可用）時，依序換這幾種語言寫法再試；null = 不指定語言（用系統預設）。 */
    private static final String[] LANGS = {"zh-TW", "cmn-Hant-TW", "zh-Hant-TW", "zh-CN", null};
    private int langIdx;
    /** 要輪流試的辨識服務（不含我們自己的存根）。排序：先試系統預設（使用者在系統「語音輸入」裡選的，例如三星，有離線包），其餘照系統給的順序。
     *  最後一個位置（svcIdx == size）= 系統預設，會放在最前面試。 */
    private List<ComponentName> recSvcs = new ArrayList<>();
    private int svcIdx;
    private SpeechRecognizer recognizer;
    private CountDownTimer timer;
    private boolean listening, typing, dismissing, programmatic;
    private int bottomInset;

    public AssistantSheet(Activity a) {
        act = a;
        d = a.getResources().getDisplayMetrics().density;
        root = new FrameLayout(a);

        // 背景：由上到下漸深的暗幕（不是一個黑色方塊），讓字和光球在任何桌布上都看得清楚
        scrim = new View(a);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x40000000, 0x99000000, 0xDD000000}));
        scrim.setAlpha(0f);
        scrim.setOnClickListener(v -> dismiss());
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        aurora = new AuroraView(a);
        root.addView(aurora, new FrameLayout.LayoutParams(-1, -1));

        // 內容：透明，只有光球和輸入列
        content = new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding((int) (18 * d), 0, (int) (14 * d), (int) (18 * d));

        if (OverlayService.agentRunning) {  // 任務正在執行：面板上直接給「停止」，不用去拉通知欄
            TextView stop = new TextView(a);
            stop.setText("■  停止目前的任務");
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

        questionView = new TextView(a);   // 平常不顯示；AI 問問題時才出現在光球上方
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

        orb = new OrbView(a);
        // 擴散圓環最大約球半徑的 2.65 倍、外圈光暈約 2 倍，都比 200dp 高的 View 還大；不關掉裁切，圓環與光暈的上下兩端會被 View 邊框切掉
        content.setClipChildren(false);
        root.setClipChildren(false);
        orb.setOnClickListener(v -> { if (listening) stopListening(); else startVoice(); });
        content.addView(orb, new LinearLayout.LayoutParams(-1, (int) (240 * d)));  // 多留一點高度給擴散圓環


        row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        input = new EditText(a);
        input.setHintTextColor(0xA0FFFFFF);
        input.setTextColor(Color.WHITE);
        input.setTextSize(17);
        GradientDrawable ib = new GradientDrawable();
        ib.setColor(0x99121218);                   // 半透明深色玻璃，白字在任何背景都讀得到
        ib.setStroke((int) Math.max(1, d), 0x50FFFFFF);
        ib.setCornerRadius(40 * d);
        input.setBackground(ib);
        input.setPadding((int) (20 * d), (int) (14 * d), (int) (20 * d), (int) (14 * d));
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setSingleLine(true);
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
        bgBtn.setOnClickListener(v -> {
            boolean on = !cfg.getBoolean("bg_on", false);
            cfg.edit().putBoolean("bg_on", on).apply();
            bgBtn.setOn(on);
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);  // 不跳提示文字，只靠圖示變化
        });
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams((int) (52 * d), (int) (52 * d));
        blp.rightMargin = (int) (4 * d);
        row.addView(bgBtn, blp);
        bgBtnView = bgBtn;
        row.addView(input, new LinearLayout.LayoutParams(0, -2, 1f));

        send = new SendIconView(a);
        send.setOnClickListener(v -> submit());  // 右邊只有「送出」；要重新聽就點中間的光球
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams((int) (52 * d), (int) (52 * d));
        slp.leftMargin = (int) (4 * d);
        row.addView(send, slp);
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
        setStatus("直接說話，或在這裡打字");
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
        orb.setState(OrbView.IDLE);
        setStatus("說出或打字你的回答");
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

    /** 開場：先從螢幕最上方放出光波、灑下光點（AuroraView.playIntro），波過去後光球和輸入列再浮上來。 */
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
        typing = false;
        hideKeyboard();
        cancelCountdown();
        if (act.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            act.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            setStatus("需要麥克風權限");
            return;
        }
        if (SherpaEngine.ready(act)) {   // 已下載 SenseVoice：App 自己錄音、自己辨識（三星與小米都用這條），不需要網路
            startSherpa();
            return;
        }
        if (act.getSharedPreferences("cfg", Activity.MODE_PRIVATE).getBoolean("voice_sys", false)) {
            trace("直接用系統語音視窗（主畫面勾選，或先前 App 內辨識都失敗而自動記住）");
            fallbackDialog(false);
            return;
        }
        recSvcs = pickRecognizers();
        trace("可用辨識服務 " + recSvcs.size() + " 個：" + recSvcs);
        svcIdx = recSvcs.size();  // 從「系統預設」開始；失敗才往前面的其他服務走
        langIdx = 0;
        beginRecognition();
    }

    /** SenseVoice：錄完一句（停頓約 1 秒結束）再整段辨識，說完才出字。錄音與辨識都在 App 內，回呼都在主執行緒。 */
    private void startSherpa() {
        stopRecognizer();
        final int gen = ++voiceGen;
        trace("使用 SenseVoice（App 內離線辨識）");
        listening = true;
        setText("");
        setStatus("我在聽…");
        orb.setState(OrbView.LISTENING);
        refreshAction();
        capture = SpeechCapture.start(new SpeechCapture.Cb() {
            @Override public void onListening() { }
            @Override public void onLevel(float v) {
                if (!listening || gen != voiceGen) return;
                orb.setLevel(v);
                aurora.setEnergy(0.35f + 0.65f * v);
            }
            @Override public void onDone(short[] pcm) {
                if (gen != voiceGen) return;
                capture = null;
                listening = false;
                if (pcm == null) { idleMessage("沒聽到聲音，點一下光球再說一次"); return; }
                orb.setState(OrbView.THINKING);
                setStatus("辨識中…");
                final float[] f = SpeechCapture.toFloat(pcm);
                new Thread(() -> {
                    String text = null, err = null;
                    try {
                        text = SherpaEngine.recognize(act, f, SpeechCapture.RATE);
                    } catch (Throwable e) {
                        err = String.valueOf(e.getMessage() != null ? e.getMessage() : e);
                    }
                    final String t = text, e2 = err;
                    act.runOnUiThread(() -> {
                        if (gen != voiceGen) return;
                        if (e2 != null) { trace("SenseVoice 錯誤：" + e2); idleMessage("語音辨識失敗（" + e2 + "），可以改用打字"); }
                        else if (t == null || t.isEmpty()) idleMessage("沒聽清楚，點一下光球再說一次");
                        else gotText(t);
                    });
                }, "sensevoice").start();
            }
            @Override public void onError(String msg) {
                if (gen != voiceGen) return;
                capture = null;
                listening = false;
                trace("錄音錯誤：" + msg);
                idleMessage("錄音失敗（" + msg + "）");
            }
        });
    }

    /** 目前這個辨識服務不行（連不上、語言都不支援…）：換下一個；全部用完退回系統語音視窗。 */
    private void nextService(String why) {
        svcIdx = svcIdx == recSvcs.size() ? 0 : svcIdx + 1;  // 系統預設失敗 → 其他服務；其他都試過 → 退回系統視窗
        langIdx = 0;
        if (svcIdx >= recSvcs.size()) { fallbackDialog(); return; }
        setStatus(why + "，換另一個辨識服務…");
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
            if (LANGS[langIdx] != null) i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGS[langIdx]);
            curSvc = svcIdx < recSvcs.size() ? recSvcs.get(svcIdx).flattenToShortString() : "系統預設";
            trace("開始辨識：服務=" + curSvc + "，語言=" + LANGS[langIdx]);
            recognizer.startListening(i);
            listening = true;
            setText("");
            setStatus("我在聽…");
            orb.setState(OrbView.LISTENING);
            refreshAction();
        } catch (Exception e) {
            trace("啟動失敗：" + e);
            nextService("辨識服務啟動失敗");
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
            trace("App 內辨識失敗（連續第 " + n + " 次）" + (n >= 3 ? "，之後改用系統視窗" : "，還會再試 App 內辨識"));
        }
        trace("退回系統語音視窗");
        try {
            act.startActivityForResult(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-TW")
                    .putExtra(RecognizerIntent.EXTRA_PROMPT, "請說要做什麼"), REQ_FALLBACK);
        } catch (ActivityNotFoundException e) {
            setStatus("這支手機沒有語音辨識，請用下面的輸入框打字");
            orb.setState(OrbView.IDLE);
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
        orb.setState(OrbView.THINKING);
        setStatus("辨識中…");
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
            orb.setLevel(v);
            aurora.setEnergy(0.35f + 0.65f * v);
        }

        @Override public void onEndOfSpeech() {
            listening = false;
            orb.setState(OrbView.THINKING);
            setStatus("辨識中…");
        }

        @Override public void onPartialResults(Bundle b) {
            ArrayList<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (r != null && !r.isEmpty()) setText(r.get(0));  // 邊說邊顯示在輸入框裡
        }

        @Override public void onResults(Bundle b) {
            listening = false;
            ArrayList<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (r != null && !r.isEmpty()) gotText(r.get(0));
            else idleMessage("沒聽清楚，點一下光球再說一次");
        }

        @Override public void onError(int e) {
            listening = false;
            trace("錯誤 " + errName(e) + "，服務=" + curSvc);
            if (e == SpeechRecognizer.ERROR_NO_MATCH || e == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                idleMessage("沒聽到聲音，點一下光球再說一次");
            } else if (e == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                fallbackDialog();
            } else if ((e == 12 || e == 13) && langIdx + 1 < LANGS.length) {  // 語言不支援/不可用：換下一種寫法再試
                langIdx++;
                setStatus("換一種語言設定再試…");
                beginRecognition();
            } else if (e == 8) {
                idleMessage("辨識服務忙碌（8），稍後點光球再試");
            } else {
                nextService(errName(e));  // 連不上/語言都不行/其他錯誤：換下一個辨識服務，全部失敗會退回系統語音視窗
            }
        }
    }

    private static String errName(int e) {
        switch (e) {
            case 1: return "網路逾時（1）";
            case 2: return "網路錯誤（2）";
            case 3: return "麥克風錯誤（3）";
            case 4: return "辨識服務錯誤（4）";
            case 5: return "用戶端錯誤（5）";
            case 8: return "辨識服務忙碌（8）";
            case 10: return "請求太多（10）";
            case 11: return "辨識服務中斷（11）";
            case 12: return "語言不支援（12）";
            case 13: return "語言不可用（13）";
            default: return "代碼 " + e;
        }
    }

    private void idleMessage(String s) {
        orb.setState(OrbView.IDLE);
        aurora.setEnergy(0.3f);
        setStatus(s);
        refreshAction();
    }

    // ---------- 文字 / 送出 ----------

    private void gotText(String t) {
        act.getSharedPreferences("cfg", Activity.MODE_PRIVATE).edit().putInt("voice_fail", 0).apply();   // 辨識成功：連續失敗次數歸零
        setText(t);
        orb.setState(OrbView.IDLE);
        aurora.setEnergy(0.3f);
        startCountdown();
    }

    private void startCountdown() {
        cancelCountdown();
        long total = COUNTDOWN_SECONDS * 1000L;
        timer = new CountDownTimer(total, 50) {
            public void onTick(long ms) { send.setProgress(ms / (float) total); }  // 送出鈕外圈細線慢慢縮短
            public void onFinish() { send.setProgress(0f); submit(); }
        };
        timer.start();
    }

    private void cancelCountdown() {
        if (timer != null) { timer.cancel(); timer = null; }
        send.setProgress(0f);
    }

    private void enterTyping() {
        typing = true;
        cancelCountdown();
        stopRecognizer();
        orb.setState(OrbView.IDLE);
        orb.setLevel(0f);
        setStatus("打好按右邊的送出");
        refreshAction();
    }

    private void submit() {
        cancelCountdown();
        String t = input.getText().toString().trim();
        if (t.isEmpty()) return;
        if (questionMode) { answerQuestion(t); return; }
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
            setStatus("思考中…");
            orb.setState(OrbView.THINKING);
            root.postDelayed(waitTimeout, 30000);
            return;
        }
        // 送出後面板不關：輸入列淡出，光球留著轉（思考中）。助理如果是用文字回答，光球會直接變成回答卡片；
        // 如果是要操作手機或開 App，Termux 會通知 App 把面板收起來。30 秒都沒消息就自己收起來。
        orb.setState(OrbView.THINKING);
        row.animate().alpha(0f).translationY(30 * d).setDuration(240).start();
        row.setEnabled(false);
        waiting = true;
        root.postDelayed(waitTimeout, 30000);
    }

    /** 助理文字回答到了：卡片（沒有標題、沒有關閉鈕）出現在畫面中上方，光球收掉；下面留著縮小的光球和輸入列，可以接續對話。 */
    /** 固定指令（root 直接執行）或聊天（Claude API）：在背景做，回答到了用回答卡片顯示。 */
    private boolean nativeAgent() {
        return act.getSharedPreferences("cfg", android.content.Context.MODE_PRIVATE).getBoolean("agent_native", true);
    }

    /** 固定指令、Haiku 路由判斷後的動作、或聊天：在背景做，回答到了用回答卡片顯示並唸出來。 */
    private void runLocal(Assist.Route r, String t) {
        new Thread(() -> {
            String reply = r.kind == Assist.FIXED ? runFixed(r) : routeAndRun(t);
            if (reply == null) return;   // Termux 自己會回報（操作手機時）
            final String fr = reply;
            Notify.say(act, fr);
            root.post(() -> showReply("", fr));
        }, "local-reply").start();
    }

    private String runFixed(Assist.Route r) {
        if (r.confirm && !confirmOnUi("這會開關飛航模式，可能會斷網，要繼續嗎？")) return "已取消";
        Maintenance.suRun(r.cmd);
        return r.reply;
    }

    /** Haiku 判斷這句話是指令、開 App、聊天還是操作畫面，再分派。判斷失敗就當聊天（聊天會回報連不上的原因）。 */
    private String routeAndRun(String t) {
        Router.Decision d = Router.decide(act, t);
        String kind = d == null ? Router.CHAT : d.kind;
        switch (kind) {
            case Router.CMD:
                return runFixed(Assist.fixed(d.cmdIndex));
            case Router.APP:
                Maintenance.suRun("monkey -p " + d.pkg + " -c android.intent.category.LAUNCHER 1");
                return "已開啟 App";
            case Router.AGENT:
                if (nativeAgent()) {
                    return AgentLoop.run(act, t, new AgentLoop.Ui() {
                        @Override public void hide() { act.runOnUiThread(AssistantSheet.this::dismiss); }
                        @Override public void status(String s) { Notify.notifyText(act, "AI 正在操作手機", s, 7195); }
                        @Override public boolean confirm(String msg) { return confirmOnUi(msg); }
                    });
                }
                String err = CommandSender.send(act, t);   // 關掉新迴圈時，改用 Termux 的 assistant.py
                if (err != null) root.post(() -> setStatus("❌ " + err));
                return null;
            default: {
                String reply = localAnswer(t);
                if (reply == null) reply = ChatEngine.ask(act, t, this::confirmOnUi);
                return reply != null ? reply : "連不上 AI：請檢查網路，或到「功能 → 維護」確認 Claude API Key。";
            }
        }
    }

    /** 定時任務（建立、列出、取消）或能直接回答的短問題（電量、幾點…）；都不是回 null。 */
    private String localAnswer(String t) {
        try {
            String s = Schedule.handle(act, t, this::confirmOnUi);
            if (s != null) return s;
        } catch (Exception e) {
            return "定時任務出錯：" + e.getMessage();
        }
        return Schedule.quickAnswer(act, t);
    }

    /** 在主執行緒跳確認框，背景執行緒等使用者按完。 */
    private boolean confirmOnUi(String msg) {
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        final boolean[] ok = {false};
        act.runOnUiThread(() -> new com.google.android.material.dialog.MaterialAlertDialogBuilder(act)
                .setMessage(msg)
                .setPositiveButton("確定", (d, w) -> { ok[0] = true; latch.countDown(); })
                .setNegativeButton("取消", (d, w) -> latch.countDown())
                .setOnCancelListener(d -> latch.countDown())
                .show());
        try { latch.await(); } catch (InterruptedException e) { return false; }
        return ok[0];
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
        // 光球留著（縮小，點一下可語音接續），輸入列也留著（打字接續）；背景模式鈕用不到
        bgBtnView.setVisibility(View.GONE);
        orb.setVisibility(View.VISIBLE);
        orb.setState(OrbView.IDLE);
        orb.setLevel(0f);
        ViewGroup.LayoutParams olp = orb.getLayoutParams();
        olp.height = (int) (150 * d);
        orb.setLayoutParams(olp);
        row.animate().cancel();
        row.setEnabled(true);
        row.setAlpha(1f);
        row.setTranslationY(0f);
        input.setEnabled(true);
        setText("");
        setStatus("繼續問…");
        aurora.setEnergy(0.45f);
        final FrameLayout card = ReplyCard.build(act, title, text, this::dismiss);
        replyCard = card;
        // 卡片放在「狀態列下面」到「輸入列上面」這一段的中間偏上，不要貼著底部（使用者回報太偏下）
        final FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_VERTICAL);
        cp.setMargins((int) (14 * d), (int) (40 * d), (int) (14 * d), (int) (240 * d) + bottomInset);
        card.setLayoutParams(cp);
        card.setAlpha(0f);
        card.setTranslationY(36 * d - 0.04f * root.getHeight());
        card.setScaleX(0.97f);
        card.setScaleY(0.97f);
        root.addView(card, cp);
        card.animate().alpha(1f).translationY(-0.04f * root.getHeight()).scaleX(1f).scaleY(1f).setDuration(400)
                .setInterpolator(new DecelerateInterpolator(2.2f)).start();
    }

    /** Termux 說這句話要操作手機（或已經做完）：還在等回答的話就把面板收起來；已經顯示回答卡片就不動。 */
    public void closeIfWaiting() {
        if (waiting && !replyShown) dismiss();
    }

    private void setText(String t) {
        programmatic = true;
        input.setText(t);
        input.setSelection(input.getText().length());
        programmatic = false;
    }

    private void refreshAction() {
        boolean hasText = input != null && input.getText().length() > 0;
        send.setEnabled(hasText);
        send.setAlpha(hasText ? 1f : 0.35f);  // 沒內容時變暗，不能按
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
