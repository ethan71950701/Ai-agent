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

    /** 目前選的主題（莫奈或哪一色）；比對它就知道畫面是不是已經是最新的主題。 */
    static String themeKey(Context c) {
        android.content.SharedPreferences sp = c.getSharedPreferences("cfg", MODE_PRIVATE);
        return sp.getBoolean("monet_on", true) ? "monet" : "seed" + sp.getInt("seed_idx", 0);
    }

    /** 桌面壁紙的主色（系統自己的取色沒跟著壁紙變時，由 App 自己算用）；讀不到回 0。 */
    static int wallpaperSeed(Context c) {
        try {
            android.app.WallpaperManager wm = android.app.WallpaperManager.getInstance(c.getApplicationContext());
            android.app.WallpaperColors wc = wm.getWallpaperColors(android.app.WallpaperManager.FLAG_SYSTEM);
            return wc == null ? 0 : wc.getPrimaryColor().toArgb();
        } catch (Throwable t) { return 0; }
    }

    /** 莫奈用的選項：讀得到桌面壁紙主色就以它為種子自己配色（有些手機的系統取色不會跟著壁紙變）；讀不到才照系統給的。 */
    static DynamicColorsOptions monetOptions(Context c) {
        DynamicColorsOptions.Builder ob = new DynamicColorsOptions.Builder();
        int seed = wallpaperSeed(c);
        if (seed != 0) ob.setContentBasedSource(seed);
        return ob.build();
    }

    /** 目前主題的主色（莫奈取色或固定色）。助理面板的 Activity 沒套 Material 主題，所以自己組一個再讀；讀不到回 defColor。 */
    static int accentColor(Context c, int defColor) {
        try {
            Context base = c.getApplicationContext();
            android.view.ContextThemeWrapper tc = new android.view.ContextThemeWrapper(base, R.style.AppTheme);
            SharedPreferences sp = base.getSharedPreferences("cfg", MODE_PRIVATE);
            Context use = tc;
            if (!sp.getBoolean("monet_on", true)) {
                int i = Math.max(0, Math.min(SEEDS.length - 1, sp.getInt("seed_idx", 0)));
                tc.getTheme().applyStyle(ACCENTS[i], true);
            } else {
                use = DynamicColors.wrapContextIfAvailable(tc, monetOptions(c));
            }
            android.util.TypedValue tv = new android.util.TypedValue();
            if (!use.getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimary, tv, true)) return defColor;
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
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityPreCreated(Activity a, Bundle b) {
                if (!themed(a)) return;
                SharedPreferences sp = getSharedPreferences("cfg", MODE_PRIVATE);
                if (!sp.getBoolean("monet_on", true)) {
                    // 選了固定主題色：深色背景 + 預先算好的亮色強調色（不用系統的深淺模式）
                    int i = Math.max(0, Math.min(SEEDS.length - 1, sp.getInt("seed_idx", 0)));
                    a.getTheme().applyStyle(ACCENTS[i], true);
                    return;
                }
                DynamicColors.applyToActivityIfAvailable(a, monetOptions(a));
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
