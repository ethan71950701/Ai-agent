package com.phoneai.overlay;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.util.SparseArray;
import android.view.Display;
import android.view.View;
import android.graphics.PixelFormat;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

/**
 * 持有 TYPE_ACCESSIBILITY_OVERLAY 懸浮層。一般 TYPE_APPLICATION_OVERLAY 會在「設定」等 App 前景時被系統強制隱藏
 * （dumpsys: mIsForceHiddenNonSystemOverlayWindow=true）；無障礙懸浮層是否不受影響要實機測。
 * 不讀取畫面內容（onAccessibilityEvent 什麼都不做）；只在 setFocusedText 時讀取目前焦點的輸入框。
 */
public class RippleAccessibilityService extends AccessibilityService {
    static volatile RippleAccessibilityService instance;
    private RippleView view;
    private WindowManager wm;
    private FloatButton floatBtn;
    private WindowManager.LayoutParams floatLp;
    private boolean suppressed;  // agent 執行中先藏起來，免得被截進畫面
    private EdgeGlowView glow;
    private boolean glowOn;      // agent 正在執行（連線中）

    private final SparseArray<RippleView> extra = new SparseArray<>();  // 第二螢幕（背景模式）的漣漪畫布，編號 = 系統螢幕 id
    private final SparseArray<WindowManager> extraWm = new SparseArray<>();

    /** 指定螢幕的漣漪畫布；0 或沒給 = 主螢幕。第二螢幕不存在或系統不允許就回 null。 */
    static RippleView viewFor(int displayId) {
        RippleAccessibilityService s = instance;
        if (s == null) return null;
        return displayId <= 0 ? s.view : s.extraView(displayId);
    }

        static String gesture(org.json.JSONObject j) {
        RippleAccessibilityService s = instance;
        if (s == null) return "nosvc";
        try {
            return s.performGesture(j);
        } catch (Exception e) {
            android.util.Log.e(OverlayService.TAG, "gesture", e);
            return "fail";
        }
    }

    private String performGesture(org.json.JSONObject j) throws Exception {
        final int display = Math.max(0, j.optInt("display"));
        final int hold = Math.max(0, Math.min(5000, j.optInt("hold")));
        org.json.JSONArray fs = j.getJSONArray("fingers");
        int n = Math.min(fs.length(), 4);
        boolean anyMove = false;
        int[][] f = new int[n][5];
        for (int i = 0; i < n; i++) {
            org.json.JSONObject o = fs.getJSONObject(i);
            f[i] = new int[]{o.getInt("x1"), o.getInt("y1"), o.getInt("x2"), o.getInt("y2"), Math.max(1, Math.min(5000, o.optInt("ms", 300)))};
            if (f[i][0] != f[i][2] || f[i][1] != f[i][3]) anyMove = true;
        }
        if (n == 0) return "fail";
        final boolean twoPhase = hold > 0 && anyMove;
        android.accessibilityservice.GestureDescription.StrokeDescription[] first =
                new android.accessibilityservice.GestureDescription.StrokeDescription[n];
        android.accessibilityservice.GestureDescription.Builder b1 = new android.accessibilityservice.GestureDescription.Builder();
        b1.setDisplayId(display);
        long total = 0;
        for (int i = 0; i < n; i++) {
            android.graphics.Path p = new android.graphics.Path();
            p.moveTo(f[i][0], f[i][1]);
            boolean moves = f[i][0] != f[i][2] || f[i][1] != f[i][3];
            long dur;
            if (twoPhase) {
                dur = hold;
            } else {
                if (moves) p.lineTo(f[i][2], f[i][3]);
                dur = moves ? f[i][4] : Math.max(1, hold + f[i][4]);
            }
            first[i] = new android.accessibilityservice.GestureDescription.StrokeDescription(p, 0, Math.max(1, dur), twoPhase);
            b1.addStroke(first[i]);
            total = Math.max(total, dur);
        }
        final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        final boolean[] okFlag = {false};
        final boolean[] rejected = {false};
        final android.accessibilityservice.GestureDescription second;
        if (twoPhase) {
            android.accessibilityservice.GestureDescription.Builder b2 = new android.accessibilityservice.GestureDescription.Builder();
            b2.setDisplayId(display);
            for (int i = 0; i < n; i++) {
                android.graphics.Path p = new android.graphics.Path();
                p.moveTo(f[i][0], f[i][1]);
                p.lineTo(f[i][2], f[i][3]);
                b2.addStroke(first[i].continueStroke(p, 0, f[i][4], false));
            }
            second = b2.build();
            total += 5000;
        } else {
            second = null;
        }
        final android.accessibilityservice.GestureDescription g1 = b1.build();
        final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        h.post(() -> {
            boolean sent = dispatchGesture(g1, new GestureResultCallback() {
                @Override public void onCompleted(android.accessibilityservice.GestureDescription d) {
                    if (second == null) { okFlag[0] = true; done.countDown(); return; }
                    boolean s2 = dispatchGesture(second, new GestureResultCallback() {
                        @Override public void onCompleted(android.accessibilityservice.GestureDescription d2) { okFlag[0] = true; done.countDown(); }
                        @Override public void onCancelled(android.accessibilityservice.GestureDescription d2) { done.countDown(); }
                    }, h);
                    if (!s2) done.countDown();
                }
                @Override public void onCancelled(android.accessibilityservice.GestureDescription d) { done.countDown(); }
            }, h);
            if (!sent) { rejected[0] = true; done.countDown(); }
        });
        done.await(total + 4000, java.util.concurrent.TimeUnit.MILLISECONDS);
        if (rejected[0]) return "nocap";
        return okFlag[0] ? "ok" : "fail";
    }

        private RippleView extraView(int id) {
        RippleView v = extra.get(id);
        if (v != null && v.isAttachedToWindow()) return v;
        if (v != null) {  // 螢幕被移除又重建過：舊的畫布已經沒用，丟掉重做
            try { extraWm.get(id).removeView(v); } catch (Exception ignored) { }
            extra.remove(id);
            extraWm.remove(id);
        }
        try {
            Display d = getSystemService(DisplayManager.class).getDisplay(id);
            if (d == null) return null;
            Context dc = createDisplayContext(d);
            WindowManager w = dc.getSystemService(WindowManager.class);
            WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            p.gravity = Gravity.TOP | Gravity.START;
            p.setFitInsetsTypes(0);
            v = new RippleView(dc);
            w.addView(v, p);
            extra.put(id, v);
            extraWm.put(id, w);
            Log.i(OverlayService.TAG, "second-display ripple view attached, display=" + id);
            return v;
        } catch (Exception e) {
            Log.e(OverlayService.TAG, "second-display addView display=" + id, e);
            return null;
        }
    }

    private android.widget.FrameLayout previewHost;  // 預覽的視窗根（透明；手勢時視窗會往上撐高，卡片在裡面動）
    private PreviewTouch previewTouch;  // 預覽的透明觸控層（跟卡片同大，只收觸控）
    private final ImeWatcher imeWatcher = new ImeWatcher();  // 主螢幕鍵盤跳出時把預覽移到上面
    private PreviewView preview;  // 背景虛擬螢幕的預覽小視窗（使用者點 Now Bar 才出現）

    /** 點 Now Bar 時呼叫：沒有預覽就開、已經開著就關。sf = 虛擬螢幕的 SurfaceFlinger 編號（只含數字）。 */
    static void togglePreview(String sf) {
        RippleAccessibilityService s = instance;
        if (s == null) return;
        if (s.preview != null) s.closePreview();
        else s.openPreview(sf);
    }

    /** 接手狀態變了（agent2 的 takeover 指令，或使用者按預覽上的「接手／完成」）：必要時打開預覽，並更新預覽的外觀。 */
    static void takeoverChanged() {
        RippleAccessibilityService s = instance;
        if (s != null) s.applyTakeover();
    }

    private void applyTakeover() {
        if (OverlayService.takeover && preview == null && !OverlayService.vdSf.isEmpty()) openPreview(OverlayService.vdSf);
        if (preview != null) preview.setTakeover(OverlayService.takeover, OverlayService.takeoverWhy);
    }

    /** 打字框關了：重新計時，閒置一陣子就把操作交回 AI。 */
    static void armTakeoverIdle() {
        RippleAccessibilityService s = instance;
        if (s != null && s.previewTouch != null) s.previewTouch.armIdle();
    }
    private void openPreview(String sf) {
        if (sf == null || !sf.matches("\\d{1,20}")) return;
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        int saved = getSharedPreferences("cfg", MODE_PRIVATE).getInt("preview_w", 0);  // 上次拖曳調整後的寬度
        int w = saved >= (int) (dm.widthPixels * 0.22f) && saved <= (int) (dm.widthPixels * 0.85f)
                ? saved : (int) (dm.widthPixels * 0.36f);
        int flagsBase = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        try {
            // 第一層：全螢幕、不攔觸控的透明視窗，卡片放在裡面，位置靠 translation（GPU），動畫不需要重排視窗
            preview = new PreviewView(this, sf, w, OverlayService.vdH / (float) OverlayService.vdW);
            previewHost = new android.widget.FrameLayout(this);
            android.widget.FrameLayout.LayoutParams clp = new android.widget.FrameLayout.LayoutParams(
                    preview.cardWidth(), preview.cardHeight(), Gravity.TOP | Gravity.START);
            previewHost.addView(preview, clp);
            preview.setTranslationX(dm.widthPixels - w - 12 * dm.density);
            preview.setTranslationY(96 * dm.density);
            WindowManager.LayoutParams hp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    flagsBase | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, PixelFormat.TRANSLUCENT);
            hp.gravity = Gravity.TOP | Gravity.START;
            hp.setFitInsetsTypes(0);
            hp.preferredRefreshRate = 120f;  // 要求高更新率（系統不一定照辦）
            // 兩層都要能畫進挖孔/狀態列區域，否則系統會把其中一層往下推，觸控層就對不上看得到的卡片
            hp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            wm.addView(previewHost, hp);

            // 第二層：跟卡片同樣大小、完全透明的小視窗，只收觸控
            WindowManager.LayoutParams tp = new WindowManager.LayoutParams(
                    preview.cardWidth(), preview.cardHeight() + (int) (PreviewTouch.HANDLE_H_DP * dm.density),   // 比卡片多一條把手區（小白條）
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flagsBase, PixelFormat.TRANSLUCENT);
            tp.gravity = Gravity.TOP | Gravity.START;
            tp.x = (int) preview.getTranslationX();
            tp.y = (int) preview.getTranslationY();
            tp.setFitInsetsTypes(0);
            tp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            previewTouch = new PreviewTouch(this, preview, wm, tp, this::closePreview);
            wm.addView(previewTouch, tp);
            imeWatcher.start(shown -> { if (previewTouch != null) previewTouch.imeChanged(shown); });
            if (!DirectHost.ready(OverlayService.vdId)) LiveInput.start(this, OverlayService.vdId);  // 直接顯示時觸控走主機的 Binder，不用另開  // 即時觸控注入（背景準備好前先走舊的放手後送出）
            applyTakeover();  // 預覽打開時，如果正在接手，外觀要對
            refreshGlows();
        } catch (Exception e) {
            Log.e(OverlayService.TAG, "preview addView", e);
            closePreview();
        }
    }

    private void closePreview() {
        if (preview == null && previewHost == null && previewTouch == null) return;
        LiveInput.stop();
        imeWatcher.stop();
        if (previewTouch != null) {
            previewTouch.cancelAnimation();
            try { wm.removeView(previewTouch); } catch (Exception ignored) { }
            previewTouch = null;
        }
        if (preview != null) preview.stop();
        if (previewHost != null) {
            try { wm.removeView(previewHost); } catch (Exception ignored) { }
            previewHost = null;
        }
        preview = null;
        refreshGlows();   // 預覽關了：光暈回到主螢幕
    }

    /** 目前可用的漣漪畫布（沒開無障礙服務就是 null） */
    /**
     * 把文字填進目前焦點的輸入框（ACTION_SET_TEXT）。只碰焦點欄位，不讀其他元件；append = true 時接在原本的文字後面。
     * 成功回 true；沒有焦點輸入框、服務沒開、或系統不接受時回 false（呼叫端改用其他方法）。
     */
    static boolean setFocusedText(String text, boolean append) {
        RippleAccessibilityService s = instance;
        if (s == null || text == null) return false;
        android.view.accessibility.AccessibilityNodeInfo root = s.getRootInActiveWindow();
        if (root == null) return false;
        android.view.accessibility.AccessibilityNodeInfo node = root.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT);
        if (node == null || !node.isEditable()) return false;
        String value = text;
        if (append && !node.isShowingHintText() && node.getText() != null) value = node.getText().toString() + text;
        android.os.Bundle args = new android.os.Bundle();
        args.putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
        return node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, args);
    }

    /** 對目前焦點的輸入框送出輸入法的動作鍵（搜尋／前往／送出）；有些 App（例如 Google 地圖的搜尋框）不吃假的 Enter 鍵。成功回 true。 */
    static boolean imeEnter() {
        RippleAccessibilityService s = instance;
        if (s == null) return false;
        android.view.accessibility.AccessibilityNodeInfo root = s.getRootInActiveWindow();
        if (root == null) return false;
        android.view.accessibility.AccessibilityNodeInfo node = root.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT);
        if (node == null || !node.isEditable()) return false;
        return node.performAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId());
    }

    /** 雙擊：同一點連點兩下，中間隔約 0.14 秒（系統要求 0.3 秒內才算雙擊，用 root 連下兩次 input 太慢）。成功回 true。 */
    static boolean doubleTap(int x, int y) {
        final RippleAccessibilityService s = instance;
        if (s == null) return false;
        try {
            android.graphics.Path p = new android.graphics.Path();
            p.moveTo(x, y);
            android.accessibilityservice.GestureDescription.Builder b = new android.accessibilityservice.GestureDescription.Builder();
            b.addStroke(new android.accessibilityservice.GestureDescription.StrokeDescription(p, 0, 40));
            b.addStroke(new android.accessibilityservice.GestureDescription.StrokeDescription(p, 140, 40));
            final android.accessibilityservice.GestureDescription g = b.build();
            final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
            final boolean[] ok = {false};
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                boolean sent = s.dispatchGesture(g, new GestureResultCallback() {
                    @Override public void onCompleted(android.accessibilityservice.GestureDescription d) { ok[0] = true; done.countDown(); }
                    @Override public void onCancelled(android.accessibilityservice.GestureDescription d) { done.countDown(); }
                }, null);
                if (!sent) done.countDown();
            });
            done.await(3, java.util.concurrent.TimeUnit.SECONDS);
            return ok[0];
        } catch (Exception e) {
            return false;
        }
    }

    static RippleView view() {
        RippleAccessibilityService s = instance;
        return s == null ? null : s.view;
    }

    /** 依設定「長按電源鍵叫出助理」開／關偵測（主畫面切換開關時、無障礙服務連上時呼叫）。 */
    static void refreshPowerKey() {
        RippleAccessibilityService s = instance;
        if (s == null) return;
        if (s.getSharedPreferences("cfg", MODE_PRIVATE).getBoolean("powerkey_on", false)) PowerKeyWatcher.start(s);
        else PowerKeyWatcher.stop();
    }

    /** MainActivity 切換浮動按鈕、或 agent 開始/結束時呼叫（同一個程序，直接用靜態方法）。 */
    static void refreshFloat() {
        RippleAccessibilityService s = instance;
        if (s != null) s.applyFloat();
    }
                private void refreshGlows() {
        boolean want = glowOn && getSharedPreferences("cfg", Context.MODE_PRIVATE).getBoolean("glow_on", true);
        if (preview != null) preview.setGlow(want);
        applyGlow(true);
    }

    private void applyGlow(boolean visible) {
        // 背景模式（有背景虛擬螢幕）時 AI 沒在操作主螢幕：主螢幕不畫光暈，狀態看 Now Bar／通知，要看畫面就開小視窗（光暈在小視窗裡）
        boolean want = glowOn && preview == null && OverlayService.vdSf.isEmpty()
                && getSharedPreferences("cfg", Context.MODE_PRIVATE).getBoolean("glow_on", true);
        if (!want) {
            if (glow != null) {
                try { wm.removeView(glow); } catch (Exception ignored) { }
                glow = null;
            }
            return;
        }
        if (glow != null) { glow.setVisibility(visible ? View.VISIBLE : View.INVISIBLE); return; }
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.START;
        p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        p.setFitInsetsTypes(0);
        try {
            glow = new EdgeGlowView(this);
            wm.addView(glow, p);
        } catch (Exception e) {
            Log.e(OverlayService.TAG, "glow addView", e);
            glow = null;
        }
    }

    private void applyFloat() {
        boolean want = !suppressed
                && getSharedPreferences("cfg", Context.MODE_PRIVATE).getBoolean("float_on", false);
        if (!want) {
            if (floatBtn != null) {
                try { wm.removeView(floatBtn); } catch (Exception ignored) { }
                floatBtn = null;
            }
            return;
        }
        if (floatBtn != null) return;
        int size = (int) (48 * getResources().getDisplayMetrics().density);
        floatLp = new WindowManager.LayoutParams(size, size,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        floatLp.gravity = Gravity.TOP | Gravity.START;
        android.content.SharedPreferences sp = getSharedPreferences("cfg", Context.MODE_PRIVATE);
        floatLp.x = sp.getInt("float_x", 0);
        floatLp.y = sp.getInt("float_y", getResources().getDisplayMetrics().heightPixels / 3);
        floatBtn = new FloatButton(this, wm, floatLp, () ->
                startActivity(new Intent(this, AskActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        OverlayService.mainDisplayOptions()));
        try {
            wm.addView(floatBtn, floatLp);
        } catch (Exception e) {
            Log.e(OverlayService.TAG, "float addView", e);
            floatBtn = null;
        }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        refreshPowerKey();
        wm = getSystemService(WindowManager.class);
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.START;
        p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        p.setFitInsetsTypes(0);
        try {
            view = new RippleView(this);
            wm.addView(view, p);
            instance = this;
            applyFloat();
        } catch (Exception e) {
            Log.e(OverlayService.TAG, "a11y addView", e);
            view = null;
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override
    public void onInterrupt() { }

    @Override
    public void onDestroy() {
        closePreview();
        PowerKeyWatcher.stop();
        instance = null;
        if (floatBtn != null) {
            try { getSystemService(WindowManager.class).removeView(floatBtn); } catch (Exception ignored) { }
            floatBtn = null;
        }
        if (glow != null) {
            try { getSystemService(WindowManager.class).removeView(glow); } catch (Exception ignored) { }
            glow = null;
        }
        if (view != null) {
            try { getSystemService(WindowManager.class).removeView(view); } catch (Exception ignored) { }
            view = null;
        }
        for (int i = 0; i < extra.size(); i++) {
            try { extraWm.valueAt(i).removeView(extra.valueAt(i)); } catch (Exception ignored) { }
        }
        extra.clear();
        extraWm.clear();
        super.onDestroy();
    }
}
