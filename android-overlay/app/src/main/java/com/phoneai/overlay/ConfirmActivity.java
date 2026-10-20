package com.phoneai.overlay;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

/** agent2 要使用者確認時（付款、登入、安裝…）的原生對話框。沒按（返回、點外面、逾時）一律當「否」。 */
public class ConfirmActivity extends Activity {
    static volatile CompletableFuture<Boolean> answer;
    static volatile CountDownLatch shown;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setShowWhenLocked(true);
        String msg = getIntent().getStringExtra("msg");
        new AlertDialog.Builder(this)
                .setTitle("需要你確認")
                .setMessage(msg)
                .setCancelable(false)
                .setPositiveButton("是", (d, w) -> done(true))
                .setNegativeButton("否", (d, w) -> done(false))
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
        super.onDestroy();
    }
}
