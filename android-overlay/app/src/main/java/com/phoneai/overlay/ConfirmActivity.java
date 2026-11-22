package com.phoneai.overlay;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

/** agent2 要使用者確認時（付款、登入、安裝…）的原生對話框。沒按（返回、點外面、逾時）一律當「否」。 */
public class ConfirmActivity extends Activity {
    static volatile CompletableFuture<Boolean> answer;
    static volatile CountDownLatch shown;
    static volatile CountDownLatch gone;   // 確認框真的關掉（onDestroy）才放行：不然 AI 下一個點擊會打在還沒關完的對話框上，下面的 App 也還沒取回視窗焦點（鍵盤就跳不出來）

    /** 在背景執行緒呼叫，會等使用者按是／否（最多 120 秒）；逾時、對話框沒跳出來、使用者沒按都當「否」。任何主題的 Activity 都能用（不靠 Material 主題）。 */
    static boolean ask(Context c, String msg) {
        final Context app = c.getApplicationContext();
        CompletableFuture<Boolean> ans = new CompletableFuture<>();
        CountDownLatch shownLatch = new CountDownLatch(1);
        CountDownLatch goneLatch = new CountDownLatch(1);
        answer = ans;
        shown = shownLatch;
        gone = goneLatch;
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            try {
                app.startActivity(new Intent(app, ConfirmActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                        .putExtra("msg", msg));
            } catch (Exception e) {
                shownLatch.countDown();
                ans.complete(false);
            }
        });
        try {
            if (!shownLatch.await(4, java.util.concurrent.TimeUnit.SECONDS)) return false;
            boolean r = ans.get(120, java.util.concurrent.TimeUnit.SECONDS);
            goneLatch.await(2, java.util.concurrent.TimeUnit.SECONDS);   // 等對話框關掉
            Thread.sleep(600);                                           // 再等下面的 App 取回視窗焦點（輸入法要有焦點才會彈出）
            return r;
        } catch (Exception e) {
            return false;
        }
    }

    /** AI 剛送出觸控或按鍵的一小段時間內不接受任何觸控：避免 AI 自己的點擊落在確認框的「是」上（借鏡別的專案的 InjectGuard）。 */
    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (AgentLoop.injectedRecently()) return true;
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setShowWhenLocked(true);
        String msg = getIntent().getStringExtra("msg");
        if (getSharedPreferences("cfg", MODE_PRIVATE).getBoolean("confirm_card", false)) showCard(msg);
        else
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(L.t("需要你確認"))
                .setMessage(msg)
                .setCancelable(false)
                .setPositiveButton(L.t("是"), (d, w) -> done(true))
                .setNegativeButton(L.t("否"), (d, w) -> done(false))
                .show();
        CountDownLatch l = shown;
        if (l != null) l.countDown();
    }

    /** 回答卡片樣式（設定裡的開關）：跟助理的回答框同一種卡片，下面兩顆按鈕。點卡片外面、返回都當「否」。 */
    private void showCard(String msg) {
        final float d = getResources().getDisplayMetrics().density;
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        root.setBackgroundColor(0x88000000);
        root.setOnClickListener(v -> done(false));
        android.widget.LinearLayout col = new android.widget.LinearLayout(this);
        col.setOrientation(android.widget.LinearLayout.VERTICAL);
        int maxH = (int) (getResources().getDisplayMetrics().heightPixels * 0.5f);
        android.widget.FrameLayout card = ReplyCard.build(this, L.t("需要你確認"), msg, () -> done(false), maxH, null);
        col.addView(card, new android.widget.LinearLayout.LayoutParams(-1, -2));
        android.widget.LinearLayout btns = new android.widget.LinearLayout(this);
        btns.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        btns.setPadding(0, (int) (14 * d), 0, 0);
        btns.addView(cardButton(L.t("否"), 0x66FFFFFF, false), new android.widget.LinearLayout.LayoutParams(0, (int) (52 * d), 1f));
        android.widget.LinearLayout.LayoutParams yl = new android.widget.LinearLayout.LayoutParams(0, (int) (52 * d), 1f);
        yl.leftMargin = (int) (12 * d);
        btns.addView(cardButton(L.t("是"), App.accentColor(this, 0xFF6EA8FF), true), yl);
        col.addView(btns, new android.widget.LinearLayout.LayoutParams(-1, -2));
        android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(-1, -2, android.view.Gravity.CENTER);
        lp.leftMargin = lp.rightMargin = (int) (20 * d);
        col.setClickable(true);
        root.addView(col, lp);
        setContentView(root);
    }

    private android.widget.TextView cardButton(String t, int color, boolean yes) {
        float d = getResources().getDisplayMetrics().density;
        android.widget.TextView b = new android.widget.TextView(this);
        b.setText(t);
        b.setTextColor(0xFFFFFFFF);
        b.setTextSize(18);
        b.setGravity(android.view.Gravity.CENTER);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(yes ? color : 0x33FFFFFF);
        g.setCornerRadius(26 * d);
        b.setBackground(g);
        b.setOnClickListener(v -> done(yes));
        return b;
    }

    @Override
    public void onBackPressed() { done(false); }

    private void done(boolean yes) {
        CompletableFuture<Boolean> a = answer;
        if (a != null) a.complete(yes);
        finish();
    }

    @Override
    protected void onDestroy() {
        CompletableFuture<Boolean> a = answer;
        if (a != null) a.complete(false);
        CountDownLatch g = gone;
        if (g != null) g.countDown();
        super.onDestroy();
    }
}
