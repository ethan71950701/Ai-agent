package com.phoneai.overlay;

import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 操作手機之後（面板已經收起來、別的 App 在前面）把回答顯示給使用者看。
 * 先叫出回答卡片畫面（AskActivity）；系統在背景擋掉的話（過 1.5 秒畫面還沒出現），改用懸浮視窗畫一張卡片。
 * 結果會記進任務過程（「回答卡片」那一行），方便看到底是哪一步沒顯示。
 */
final class AnswerCard {
    private AnswerCard() { }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static void show(Context c, String reply) {
        final Context app = c.getApplicationContext();
        final long t0 = System.currentTimeMillis();
        try {
            app.startActivity(new Intent(app, AskActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra("reply_text", reply).putExtra("voice", false));
        } catch (Exception e) {
            TaskLog.noteLatest(app, "卡片畫面啟動失敗：" + e.getClass().getSimpleName());
        }
        MAIN.postDelayed(() -> {
            if (AskActivity.lastCreated >= t0) {
                TaskLog.noteLatest(app, "卡片已顯示");
            } else if (overlay(app, reply)) {
                TaskLog.noteLatest(app, "卡片畫面被系統擋下，改用懸浮卡片");
            } else {
                TaskLog.noteLatest(app, "卡片畫面被系統擋下，也沒有懸浮視窗權限，只有通知");
            }
        }, 1500);
    }

    /** 用懸浮視窗畫一張回答卡片（需要「顯示在其他 App 上層」權限，進度條也是用它）；點一下關閉，20 秒後自己消失。 */
    private static boolean overlay(Context c, String text) {
        try {
            if (!Settings.canDrawOverlays(c)) return false;
            final WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
            float d = c.getResources().getDisplayMetrics().density;
            LinearLayout card = new LinearLayout(c);
            card.setOrientation(LinearLayout.VERTICAL);
            int pad = (int) (20 * d);
            card.setPadding(pad, pad, pad, pad);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xF2202327);
            bg.setCornerRadius(24 * d);
            card.setBackground(bg);
            TextView body = new TextView(c);
            body.setText(Md.render(text));
            body.setTextColor(0xFFFFFFFF);
            body.setTextSize(17);
            card.addView(body);
            TextView hint = new TextView(c);
            hint.setText(L.t("點一下關閉"));
            hint.setTextColor(0xFFAAAAAA);
            hint.setTextSize(12);
            hint.setPadding(0, (int) (10 * d), 0, 0);
            card.addView(hint);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    c.getResources().getDisplayMetrics().widthPixels - (int) (32 * d), WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            lp.y = (int) (140 * d);
            final Runnable remove = () -> {
                try { wm.removeView(card); } catch (Exception ignored) { }
            };
            card.setOnClickListener((View v) -> remove.run());
            wm.addView(card, lp);
            MAIN.postDelayed(remove, 20000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
