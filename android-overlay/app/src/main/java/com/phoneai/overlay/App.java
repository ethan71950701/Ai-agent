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

    private static boolean themed(Activity a) {
        return a instanceof MainActivity || a instanceof OnboardingActivity || a instanceof DiagActivity || a instanceof MemoryActivity
                || a instanceof LogActivity || a instanceof ErrorLogActivity || a instanceof MicTestActivity || a instanceof GuideActivity || a instanceof ConfirmActivity || a instanceof TransferActivity
                || a instanceof TaskDetailActivity || a instanceof BrowseActivity || a instanceof FileSearchActivity;
    }

    static android.content.Context ctx;   // 給 Root 找 App 資料夾用

    @Override
    public void onCreate() {
        super.onCreate();
        ctx = this;
        L.init(this);   // 介面語言要最先決定（靜態字串陣列也用得到）
        CrashLog.install(this);   // 當機時把錯誤堆疊寫進「錯誤紀錄」
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
                DynamicColorsOptions.Builder ob = new DynamicColorsOptions.Builder();
                DynamicColors.applyToActivityIfAvailable(a, ob.build());
            }
            @Override public void onActivityCreated(Activity a, Bundle b) { }
            @Override public void onActivityStarted(Activity a) { }
            @Override public void onActivityResumed(Activity a) { }
            @Override public void onActivityPaused(Activity a) { }
            @Override public void onActivityStopped(Activity a) { }
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
            @Override public void onActivityDestroyed(Activity a) { }
        });
    }
}
