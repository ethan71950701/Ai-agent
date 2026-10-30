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

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setShowWhenLocked(true);
        String msg = getIntent().getStringExtra("msg");
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
