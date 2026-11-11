package com.phoneai.overlay;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

/** 彈簧動畫工具（Material 3 Expressive 的做法：位移與大小有一點回彈，淡入淡出不回彈）。 */
final class Anim {
    private Anim() { }

    private static final java.util.WeakHashMap<View, java.util.HashMap<DynamicAnimation.ViewProperty, SpringAnimation>> RUNNING = new java.util.WeakHashMap<>();
    static final float BOUNCY = 0.72f, SMOOTH = 1f;
    /** 按壓回饋的彈簧：按下是「臨界阻尼」（不過衝、稍微放慢，不會啪一下縮到底）；放開是較軟的彈簧、只輕輕過衝一點（以前剛性 900／500、阻尼 0.9／0.45，按下太硬、放開太彈）。 */
    static final float PRESS_IN_K = 600f, PRESS_IN_D = 1f, PRESS_OUT_K = 320f, PRESS_OUT_D = 0.72f;

    private static final int R_FPS = 0x7f0a0e59;   // View.setTag 用的鍵

    /**
     * 動畫期間要求高更新率（Android 15 以上）。這支手機的螢幕會依內容自動降更新率省電；手指離開後只剩一個小東西在彈，
     * 系統就把更新率降得很低，彈簧看起來一格一格的。這裡明確告訴系統「現在有動畫，請用高更新率」，ms 毫秒後恢復預設。
     */
    static void boostFrames(View v, long ms) { boostFrames(v, ms, null); }

    /** tag：這段動畫是什麼（診斷頁會寫出來，才知道哪個動作掉幀）。 */
    static void boostFrames(View v, long ms, String tag) {
        if (v == null) return;
        String who = "";
        android.content.Context c = v.getContext();
        while (c instanceof android.content.ContextWrapper && !(c instanceof android.app.Activity)) c = ((android.content.ContextWrapper) c).getBaseContext();
        if (c instanceof android.app.Activity) who = c.getClass().getSimpleName().replace("Activity", "");
        monTags.add((tag == null ? "動畫" : tag) + (who.isEmpty() ? "" : "@" + who));
        monitorFrames(ms);
        if (android.os.Build.VERSION.SDK_INT < 35) return;
        try {
            final View root = v.getRootView();
            root.setRequestedFrameRate(View.REQUESTED_FRAME_RATE_CATEGORY_HIGH);
            Object old = root.getTag(R_FPS);
            if (old instanceof Runnable) root.removeCallbacks((Runnable) old);
            Runnable r = () -> root.setRequestedFrameRate(View.REQUESTED_FRAME_RATE_CATEGORY_DEFAULT);
            root.setTag(R_FPS, r);
            root.postDelayed(r, ms);
        } catch (Throwable ignored) { }
    }

    // ---------- 診斷：動畫期間實際的畫面更新率 ----------
    private static boolean monitoring;
    private static long monUntil, monLast, monMax, monSum;
    private static int monN, monSlow;
    private static final java.util.LinkedHashSet<String> monTags = new java.util.LinkedHashSet<>();
    private static final java.util.ArrayDeque<String> FPS_LOG = new java.util.ArrayDeque<>();

    /** 動畫期間記下每一幀的間隔：平均間隔就是實際更新率（8.3 毫秒≈120Hz、16.7≈60Hz、33≈30Hz），診斷頁看。 */
    private static void monitorFrames(long ms) {
        monUntil = Math.max(monUntil, android.os.SystemClock.uptimeMillis() + ms);
        if (monitoring) return;
        monitoring = true;
        monLast = 0; monMax = 0; monSum = 0; monN = 0; monSlow = 0;
        final android.view.Choreographer ch = android.view.Choreographer.getInstance();
        ch.postFrameCallback(new android.view.Choreographer.FrameCallback() {
            @Override
            public void doFrame(long ns) {
                long t = ns / 1_000_000L;
                if (monLast != 0) {
                    long g = t - monLast;
                    monN++; monSum += g;
                    if (g > monMax) monMax = g;
                    if (g > 20) monSlow++;
                }
                monLast = t;
                if (android.os.SystemClock.uptimeMillis() < monUntil) { ch.postFrameCallback(this); return; }
                monitoring = false;
                java.util.LinkedHashSet<String> keep = new java.util.LinkedHashSet<>(monTags);
                monTags.clear();
                if (monN >= 6) {
                    float avg = monSum / (float) monN;
                    FPS_LOG.addFirst(String.format(java.util.Locale.US, "[%s] %d 幀，平均間隔 %.1f 毫秒（約 %d Hz），最長 %d 毫秒，超過 20 毫秒 %d 次",
                            android.text.TextUtils.join("、", keep), monN, avg, Math.round(1000f / avg), monMax, monSlow));
                    while (FPS_LOG.size() > 5) FPS_LOG.removeLast();
                }
            }
        });
    }

    /** 診斷頁用：最近幾次動畫期間的更新率。 */
    static String frameInfo() { return android.text.TextUtils.join("\n　　", FPS_LOG); }

    static void spring(View v, DynamicAnimation.ViewProperty prop, float to, float stiffness, float damping) {
        boostFrames(v, 900, "彈簧");
        // 同一個 View 的同一個屬性只留一個彈簧；舊的沒取消會在新的結束後繼續跑，把值拉回舊目標（按鈕卡在縮小狀態就是這樣來的）
        java.util.HashMap<DynamicAnimation.ViewProperty, SpringAnimation> m = RUNNING.get(v);
        if (m == null) { m = new java.util.HashMap<>(); RUNNING.put(v, m); }
        SpringAnimation old = m.get(prop);
        if (old != null && old.isRunning()) old.cancel();
        SpringAnimation a = new SpringAnimation(v, prop);
        m.put(prop, a);
        a.setSpring(new SpringForce(to).setStiffness(stiffness).setDampingRatio(damping));
        a.start();
    }

    /** 取消這個 View 上還在跑的彈簧（手指要直接拖它時用）。 */
    /** 只取消某一個屬性的彈簧（拖動時只停位移，不影響放大）。 */
    static void cancel(View v, DynamicAnimation.ViewProperty prop) {
        java.util.HashMap<DynamicAnimation.ViewProperty, SpringAnimation> m = RUNNING.get(v);
        if (m == null) return;
        SpringAnimation a = m.get(prop);
        if (a != null && a.isRunning()) a.cancel();
    }

    static void cancel(View v) {
        java.util.HashMap<DynamicAnimation.ViewProperty, SpringAnimation> m = RUNNING.get(v);
        if (m == null) return;
        for (SpringAnimation a : m.values()) if (a.isRunning()) a.cancel();
    }

    /** 按下縮小、放開彈回；不攔截點擊。 */
    static void pressable(View v, float pressed) {
        v.setOnTouchListener((x, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    spring(x, DynamicAnimation.SCALE_X, pressed, PRESS_IN_K, PRESS_IN_D);
                    spring(x, DynamicAnimation.SCALE_Y, pressed, PRESS_IN_K, PRESS_IN_D);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    spring(x, DynamicAnimation.SCALE_X, 1f, PRESS_OUT_K, PRESS_OUT_D);
                    spring(x, DynamicAnimation.SCALE_Y, 1f, PRESS_OUT_K, PRESS_OUT_D);
                    break;
                default:
            }
            return false;
        });
    }

    /**
     * 展開／收合：高度用「時間＋曲線」連續長出來（不是整塊淡入），下面的卡片跟著一格一格被推開；裡面的內容隨高度長出來而淡入。
     * 展開是先快後慢、收合是先慢後快。同時間只跑一個，連點會從目前的高度接著動，不會跳。
     */
    static void expandSmooth(final View body, final boolean show) {
        boostFrames(body, 700, "群組展開");
        final ViewGroup.LayoutParams lp = body.getLayoutParams();
        Object old = body.getTag(R_EXPAND);
        float prevVel = 0f;
        if (old instanceof androidx.dynamicanimation.animation.SpringAnimation) {   // 連點：從目前的高度與速度接著動，不會跳
            ((androidx.dynamicanimation.animation.SpringAnimation) old).cancel();
            Object v = body.getTag(R_EXPAND_V);
            if (v instanceof Float) prevVel = (Float) v;
        }
        final float to;
        if (show) {
            View parent = (View) body.getParent();
            int w = parent.getWidth() > 0 ? parent.getWidth() : body.getResources().getDisplayMetrics().widthPixels;
            body.setVisibility(View.VISIBLE);
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            body.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            to = body.getMeasuredHeight();
        } else {
            to = 0f;
        }
        final float start = show ? (body.getHeight() > 0 && body.getHeight() < to ? body.getHeight() : 0f) : body.getHeight();
        final float end = show ? to : 0f;
        final float fadeBase = Math.max(1f, Math.abs(end - start));
        // 彈簧（非線性、有慣性），數值取 Material 3 Expressive 的「空間（spatial）」彈簧（改變大小、位置用的）：
        //   預設 stiffness 380、阻尼 0.8（約 0.26 秒穩定、過衝約 1.5%）；慢 stiffness 200、阻尼 0.8（約 0.35 秒）。
        //   很高的內容（> 800dp，例如底欄玻璃那組）用慢的，才看得到展開的過程；收合用臨界阻尼（不回彈）、剛性 600。
        // 這些數值是從第三方整理的 Material 文件查到的（各家對阻尼寫 0.73 或 0.8 不一致），不是官方原文。
        float dist = fadeBase / body.getResources().getDisplayMetrics().density;   // dp
        float zeta = show ? 0.8f : 1f;
        float stiffness = show ? (dist > 800f ? 200f : 380f) : 600f;
        final androidx.dynamicanimation.animation.SpringAnimation sa = new androidx.dynamicanimation.animation.SpringAnimation(new androidx.dynamicanimation.animation.FloatValueHolder(start));
        sa.setSpring(new androidx.dynamicanimation.animation.SpringForce(end).setDampingRatio(zeta).setStiffness(stiffness));
        sa.setStartVelocity(prevVel);
        sa.setMinimumVisibleChange(0.5f);
        sa.addUpdateListener((anim, h, vel) -> {
            body.setTag(R_EXPAND_V, vel);
            lp.height = Math.max(0, Math.round(h));
            body.setLayoutParams(lp);
            // 內容淡入淡出跟著「走了多少」，不是跟著絕對高度：很高的內容才不會一開始就全亮
            float prog = Math.abs(h - start) / fadeBase;
            body.setAlpha(show ? Math.min(1f, 0.25f + prog * 1.1f) : Math.max(0f, 1f - prog * 1.15f));
        });
        sa.addEndListener((anim, canceled, value, velocity) -> {
            if (canceled) return;
            body.setTag(R_EXPAND_V, 0f);
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            body.setAlpha(1f);
            if (!show) body.setVisibility(View.GONE);
            body.setLayoutParams(lp);
        });
        body.setTag(R_EXPAND, sa);
        sa.start();
    }

    private static final int R_EXPAND_V = 0x7f0a0e58;   // 彈簧目前的速度（連點時接續用）
    private static final int R_EXPAND = 0x7f0a0e57;   // View.setTag 用的鍵（只要不跟系統的衝突）
}
