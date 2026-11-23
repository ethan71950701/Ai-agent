package com.phoneai.overlay;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.DynamicColorsOptions;

/** 套用主題色：開了「莫奈取色」就跟桌布走；沒開就用使用者選的種子色產生整套配色。只套在 App 自己的畫面，不動助理面板。 */
public class App extends Application {
    // 18 色：淺一點（使用者覺得太深）。排列：第一排 5 色（前面再加一個「莫奈」），第二排 7 色，第三排 6 色
    // 主題色：KernelSU 的 15 個主色（manager/…/ui/theme/Colors.kt 的 keyColorOptions）
    static final int[] SEEDS = {
            0xFFF44336, 0xFFE91E63, 0xFF9C27B0, 0xFF673AB7, 0xFF3F51B5,
            0xFF2196F3, 0xFF00BCD4, 0xFF009688, 0xFF4FAF50, 0xFFFFEB3B,
            0xFFFFC107, 0xFFFF9800, 0xFF795548, 0xFF607D8F, 0xFFFF9CA8};
    static final int[] ACCENTS = {
            R.style.Accent0, R.style.Accent1, R.style.Accent2, R.style.Accent3, R.style.Accent4,
            R.style.Accent5, R.style.Accent6, R.style.Accent7, R.style.Accent8, R.style.Accent9,
            R.style.Accent10, R.style.Accent11, R.style.Accent12, R.style.Accent13, R.style.Accent14};
    static final String[] SEED_NAMES = {"紅", "粉紅", "紫", "深紫", "靛藍", "藍", "青", "藍綠", "綠", "黃",
            "琥珀", "橘", "棕", "灰藍", "粉"};

    static final int CUSTOM = -1;        // seed_idx＝-1：自訂顏色
    static final int DEFAULT_SEED = 5;   // 沒選過：藍色

    static int seedIdx(SharedPreferences sp) { return sp.contains("seed_idx") ? sp.getInt("seed_idx", DEFAULT_SEED) : DEFAULT_SEED; }

    static int customColor(SharedPreferences sp) { return sp.getInt("custom_color", 0xFFFF6F91); }

    /** 目前選的主題（哪一色或自訂顏色）；比對它就知道畫面是不是已經是最新的主題。 */
    static String themeKey(Context c) {
        SharedPreferences sp = c.getSharedPreferences("cfg", MODE_PRIVATE);
        int i = seedIdx(sp);
        return i == CUSTOM ? "custom" + Integer.toHexString(customColor(sp)) : "seed" + i;
    }

    /** 自訂顏色：用 Material 的配色演算法，以這個顏色當種子產生整套深色配色。 */
    static DynamicColorsOptions customOptions(SharedPreferences sp) {
        return new DynamicColorsOptions.Builder()
                .setContentBasedSource(customColor(sp))
                .setThemeOverlay(com.google.android.material.R.style.ThemeOverlay_Material3_DynamicColors_Dark)
                .build();
    }

    /** 目前主題的主色（莫奈取色或固定色）。助理面板的 Activity 沒套 Material 主題，所以自己組一個再讀；讀不到回 defColor。 */
    static int accentColor(Context c, int defColor) {
        try {
            Context base = c.getApplicationContext();
            SharedPreferences sp = base.getSharedPreferences("cfg", MODE_PRIVATE);
            int idx = seedIdx(sp);
            if (idx == CUSTOM) return com.google.android.material.color.utilities.Scheme.dark(customColor(sp)).getPrimary();   // 自訂顏色：用同一個演算法算主色（跟畫面實際配色一致）
            android.view.ContextThemeWrapper tc = new android.view.ContextThemeWrapper(base, R.style.AppTheme);
            tc.getTheme().applyStyle(ACCENTS[Math.max(0, Math.min(SEEDS.length - 1, idx))], true);
            android.util.TypedValue tv = new android.util.TypedValue();
            if (!tc.getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimary, tv, true)) return defColor;
            return tv.data;
        } catch (Throwable e) {
            return defColor;
        }
    }

    private static boolean themed(Activity a) {
        return a instanceof MainActivity || a instanceof OnboardingActivity || a instanceof DiagActivity || a instanceof MemoryActivity
                || a instanceof LogActivity || a instanceof DigestActivity || a instanceof GuideActivity || a instanceof ConfirmActivity || a instanceof TransferActivity
                || a instanceof TaskDetailActivity || a instanceof BrowseActivity || a instanceof FileSearchActivity
                || a instanceof FcmSetupActivity || a instanceof FcmLabActivity || a instanceof NoticeActivity;
    }

    static android.content.Context ctx;   // 給 Root 找 App 資料夾用

    @Override
    public void onCreate() {
        super.onCreate();
        ctx = this;
        L.init(this);   // 介面語言要最先決定（靜態字串陣列也用得到）
        CrashLog.install(this);   // 當機時把錯誤堆疊寫進「錯誤紀錄」
        FcmLab.init(this);   // 實驗：有設定檔就初始化 Firebase（被推播叫醒時也要先有）
        new Thread(() -> TailscaleControl.reconcile(this), "ts-reconcile").start();   // 上次我們開的 Tailscale 若因程序被殺沒關到，接手關回去
        FcmLab.refreshMyToken(this);   // 記下最新的 token（配對時要交給對方）
        GlassPrefs.load(this);
        try {   // 使用者解鎖手機時：把等著解鎖的定時自動操作補跑（也會順便跑到期的定時查詢）。沒有這個也沒關係，每 15 分鐘的排程會補上
            registerReceiver(new android.content.BroadcastReceiver() {
                @Override public void onReceive(Context c, android.content.Intent i) {
                    new Thread(() -> { try { Schedule.runDue(c.getApplicationContext()); } catch (Exception ignored) { } }, "sched-unlock").start();
                }
            }, new android.content.IntentFilter(android.content.Intent.ACTION_USER_PRESENT), Context.RECEIVER_EXPORTED);
        } catch (Throwable ignored) { }
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityPreCreated(Activity a, Bundle b) {
                if (!themed(a)) return;
                SharedPreferences sp = getSharedPreferences("cfg", MODE_PRIVATE);
                int idx = seedIdx(sp);
                if (idx == CUSTOM && DynamicColors.isDynamicColorAvailable()) {   // 自訂顏色：從種子色產生整套深色配色
                    DynamicColors.applyToActivityIfAvailable(a, customOptions(sp));
                    return;
                }
                // 固定主題色：深色背景 + 預先算好的亮色強調色（不用系統的深淺模式）
                int i = Math.max(0, Math.min(SEEDS.length - 1, idx == CUSTOM ? DEFAULT_SEED : idx));
                a.getTheme().applyStyle(ACCENTS[i], true);
            }
            @Override public void onActivityCreated(Activity a, Bundle b) { }
            @Override
            public void onActivityPostCreated(Activity a, Bundle b) {
                if (!themed(a) || a instanceof ConfirmActivity || a instanceof NoticeActivity) return;
                // 手勢導覽的小白條底下：導覽列區域塗成跟頁面一樣的底色，不留透明或系統加的白色遮罩（較舊的 Android 不會自動把內容畫到導覽列底下）
                try {
                    a.getWindow().setNavigationBarContrastEnforced(false);
                    a.getWindow().setNavigationBarColor(Ui.attr(a, com.google.android.material.R.attr.colorSurface));
                } catch (Throwable ignored) { }
            }
            @Override public void onActivityStarted(Activity a) { }
            @Override public void onActivityResumed(Activity a) { TransferBar.onResumed(a); }   // 傳輸中的懸浮列跟著目前的畫面
            @Override public void onActivityPaused(Activity a) { TransferBar.onPaused(a); }
            @Override public void onActivityStopped(Activity a) { }
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
            @Override public void onActivityDestroyed(Activity a) { }
        });
    }
}
