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
    static volatile CompletableFuture<Integer> answer;   // 0＝否、1＝是、2＝是，而且這個任務內不再問
    static volatile CountDownLatch shown;
    static volatile CountDownLatch gone;   // 確認框真的關掉（onDestroy）才放行：不然 AI 下一個點擊會打在還沒關完的對話框上，下面的 App 也還沒取回視窗焦點（鍵盤就跳不出來）

    /** 在背景執行緒呼叫，會等使用者按是／否（最多 120 秒）；逾時、對話框沒跳出來、使用者沒按都當「否」。任何主題的 Activity 都能用（不靠 Material 主題）。 */
    static boolean ask(Context c, String msg) { return askTri(c, msg, false) >= 1; }

    /** 三檔：0＝否、1＝是（只這一次）、2＝是，而且這個任務內同一類的確認不再問（offerSkip 才會出現第三顆按鈕）。沒按、逾時一律 0。 */
    static int askTri(Context c, String msg, boolean offerSkip) {
        final Context app = c.getApplicationContext();
        CompletableFuture<Integer> ans = new CompletableFuture<>();
        CountDownLatch shownLatch = new CountDownLatch(1);
        CountDownLatch goneLatch = new CountDownLatch(1);
        answer = ans;
        shown = shownLatch;
        gone = goneLatch;
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            try {
                app.startActivity(new Intent(app, ConfirmActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                        .putExtra("msg", msg).putExtra("skip", offerSkip));
            } catch (Exception e) {
                shownLatch.countDown();
                ans.complete(0);
            }
        });
        try {
            if (!shownLatch.await(4, java.util.concurrent.TimeUnit.SECONDS)) return 0;
            int r = ans.get(120, java.util.concurrent.TimeUnit.SECONDS);
            goneLatch.await(2, java.util.concurrent.TimeUnit.SECONDS);   // 等對話框關掉
            Thread.sleep(600);                                           // 再等下面的 App 取回視窗焦點（輸入法要有焦點才會彈出）
            return r;
        } catch (Exception e) {
            return 0;
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
        com.google.android.material.dialog.MaterialAlertDialogBuilder b2 = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(L.t("需要你確認"))
                .setMessage(msg)
                .setCancelable(false)
                .setPositiveButton(L.t("是"), (d, w) -> done(1))
                .setNegativeButton(L.t("否"), (d, w) -> done(0));
        if (getIntent().getBooleanExtra("skip", false)) b2.setNeutralButton(L.t("是，這個任務內不再問"), (d, w) -> done(2));   // 只有低風險的確認才有這一顆（付款、密碼、驗證碼、刪除、受保護的 App 一律每次都問）
        b2.show();
        CountDownLatch l = shown;
        if (l != null) l.countDown();
    }

    private void done(int r) {
        CompletableFuture<Integer> a = answer;
        if (a != null) a.complete(r);
        finish();
    }

    @Override
    protected void onDestroy() {
        CompletableFuture<Integer> a = answer;
        if (a != null) a.complete(0);
        CountDownLatch g = gone;
        if (g != null) g.countDown();
        super.onDestroy();
    }
}
