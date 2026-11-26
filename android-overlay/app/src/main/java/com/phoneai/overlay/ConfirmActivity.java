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
    /**
     * 一次確認請求。每個請求有自己的編號，對話框只回答「自己那一個」：舊的對話框（逾時沒按、還開著）關掉或被按，
     * 不會把新請求的答案填掉（以前用一個共用的 static 欄位，舊框關掉會讓新的請求被當成「否」，按舊框的「是」會同意新的請求）。
     */
    static final class Req {
        final long id;
        final CompletableFuture<Integer> answer = new CompletableFuture<>();   // 0＝否、1＝是、2＝是，而且這個任務內不再問
        final CountDownLatch shown = new CountDownLatch(1);
        final CountDownLatch gone = new CountDownLatch(1);   // 確認框真的關掉（onDestroy）才放行：不然 AI 下一個點擊會打在還沒關完的對話框上，下面的 App 也還沒取回視窗焦點（鍵盤就跳不出來）
        volatile java.lang.ref.WeakReference<Activity> activity;
        Req(long id) { this.id = id; }
    }

    private static final java.util.concurrent.atomic.AtomicLong SEQ = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.ConcurrentHashMap<Long, Req> REQS = new java.util.concurrent.ConcurrentHashMap<>();

    static Req newRequest() {
        Req r = new Req(SEQ.incrementAndGet());
        REQS.put(r.id, r);
        return r;
    }

    static Intent intentFor(Context c, Req r, String msg, boolean offerSkip) {
        return new Intent(c, ConfirmActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                .putExtra("id", r.id).putExtra("msg", msg).putExtra("skip", offerSkip);
    }

    /** 請求結束（答了、逾時、出錯）：從清單拿掉；對話框如果還開著就關掉（逾時沒按的舊框不能留著）。 */
    static void close(Req r) {
        REQS.remove(r.id);
        r.answer.complete(0);
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            java.lang.ref.WeakReference<Activity> w = r.activity;
            Activity a = w == null ? null : w.get();
            if (a != null && !a.isFinishing()) a.finish();
        });
    }

    /** 在背景執行緒呼叫，會等使用者按是／否（最多 120 秒）；逾時、對話框沒跳出來、使用者沒按都當「否」。任何主題的 Activity 都能用（不靠 Material 主題）。 */
    static boolean ask(Context c, String msg) { return askTri(c, msg, false) >= 1; }

    /** 三檔：0＝否、1＝是（只這一次）、2＝是，而且這個任務內同一類的確認不再問（offerSkip 才會出現第三顆按鈕）。沒按、逾時一律 0。 */
    static int askTri(Context c, String msg, boolean offerSkip) {
        final Context app = c.getApplicationContext();
        final Req r = newRequest();
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            try {
                app.startActivity(intentFor(app, r, msg, offerSkip));
            } catch (Exception e) {
                r.shown.countDown();
                r.answer.complete(0);
            }
        });
        try {
            if (!r.shown.await(4, java.util.concurrent.TimeUnit.SECONDS)) return 0;
            int ans = r.answer.get(120, java.util.concurrent.TimeUnit.SECONDS);
            r.gone.await(2, java.util.concurrent.TimeUnit.SECONDS);   // 等對話框關掉
            Thread.sleep(600);                                        // 再等下面的 App 取回視窗焦點（輸入法要有焦點才會彈出）
            return ans;
        } catch (Exception e) {
            return 0;
        } finally {
            close(r);
        }
    }

    /** AI 剛送出觸控或按鍵的一小段時間內不接受任何觸控：避免 AI 自己的點擊落在確認框的「是」上（借鏡別的專案的 InjectGuard）。 */
    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (AgentLoop.injectedRecently()) return true;
        return super.dispatchTouchEvent(ev);
    }

    private Req req;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        req = REQS.get(getIntent().getLongExtra("id", -1));
        if (req == null || req.answer.isDone()) { finish(); return; }   // 已經結束的請求（逾時、重建）：不要再顯示
        req.activity = new java.lang.ref.WeakReference<>(this);
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
        req.shown.countDown();
    }

    private void done(int r) {
        if (req != null) req.answer.complete(r);
        finish();
    }

    @Override
    protected void onDestroy() {
        if (req != null) {
            req.answer.complete(0);
            req.gone.countDown();
        }
        super.onDestroy();
    }
}
