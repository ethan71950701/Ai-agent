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
    static final int[] SEEDS = {
            0xFF4FC3F7, 0xFF7986CB, 0xFFBA68C8, 0xFFF06292, 0xFFEF5350,
            0xFFFF7043, 0xFFFFA726, 0xFFFFCA28, 0xFFD4E157, 0xFF9CCC65, 0xFF66BB6A, 0xFF26A69A, 0xFF26C6DA,
            0xFF42A5F5, 0xFF78909C, 0xFFA1887F, 0xFFF48FB1, 0xFFB39DDB};
    static final String[] SEED_NAMES = {"天藍", "靛藍", "紫", "粉", "紅", "橙紅", "橙", "琥珀", "黃綠", "青檸", "綠", "翡翠", "青",
            "水藍", "灰藍", "棕", "玫瑰", "薰衣草"};

    /** 目前選的主題（莫奈或哪一色）；比對它就知道畫面是不是已經是最新的主題。 */
    static String themeKey(Context c) {
        android.content.SharedPreferences sp = c.getSharedPreferences("cfg", MODE_PRIVATE);
        return sp.getBoolean("monet_on", true) ? "monet" : "seed" + sp.getInt("seed_idx", 0);
    }

    private static boolean themed(Activity a) {
        return a instanceof MainActivity || a instanceof OnboardingActivity || a instanceof SetupActivity || a instanceof DiagActivity || a instanceof MemoryActivity
                || a instanceof LogActivity || a instanceof SherpaTestActivity || a instanceof MicTestActivity || a instanceof GuideActivity;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        GlassPrefs.load(this);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityPreCreated(Activity a, Bundle b) {
                if (!themed(a)) return;
                SharedPreferences sp = getSharedPreferences("cfg", MODE_PRIVATE);
                DynamicColorsOptions.Builder ob = new DynamicColorsOptions.Builder();
                if (!sp.getBoolean("monet_on", true)) {
                    int i = Math.max(0, Math.min(SEEDS.length - 1, sp.getInt("seed_idx", 0)));
                    ob.setContentBasedSource(SEEDS[i]);
                }
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
