package com.phoneai.overlay;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 診斷實驗：背景模式的預覽小窗裡，AI 點擊的漣漪看不看得到？同時測兩種畫法，一前一後各畫一次，讓你分得出是哪一種有出現：
 *  A（新）：漣漪直接畫在預覽卡片上（座標從背景螢幕換算）。
 *  B（舊）：漣漪畫在背景螢幕上的覆蓋層，預覽看的是那個螢幕（間接看到）。
 * 流程：建立背景螢幕 → 在上面開「設定」→ 打開預覽小窗 → 畫 A（5 個位置）→ 等 → 畫 B（同樣 5 個位置）→ 收掉。約 15 秒。需要 root 與無障礙服務。
 * 結果只是診斷文字，要你親眼看預覽小窗，回報「第幾次有看到」。在背景執行緒呼叫。
 */
final class RippleTest {
    private RippleTest() { }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** 測試位置（背景螢幕座標）：中央與四個象限的中心。 */
    static int[][] points(int w, int h) {
        return new int[][]{{w / 2, h / 2}, {w / 4, h / 4}, {3 * w / 4, h / 4}, {w / 4, 3 * h / 4}, {3 * w / 4, 3 * h / 4}};
    }

    static String run(Context c) {
        StringBuilder sb = new StringBuilder();
        if (RippleAccessibilityService.instance == null) return L.t("無障礙服務沒有開，沒辦法測。");
        if (!Root.isRoot()) return L.t("沒有 root，背景螢幕建立不了。");
        boolean created = false;
        try {
            created = BgDisplay.start(c);
            if (!created) return L.t("背景螢幕建立失敗。");
            sb.append(L.t("背景螢幕建立成功，編號 ")).append(BgDisplay.id).append("，").append(OverlayService.vdW).append("x").append(OverlayService.vdH).append('\n');
            Maintenance.suRun(BgDisplay.launch("com.android.settings", BgDisplay.id));
            sleep(1500);
            final String sf = OverlayService.vdSf;
            onMain(() -> RippleAccessibilityService.showPreview(sf));   // 打開預覽小窗
            sleep(2000);
            int[][] pts = points(OverlayService.vdW, OverlayService.vdH);

            // A：畫在預覽卡片上
            final boolean[] drewA = {true};
            for (int[] p : pts) {
                onMain(() -> drewA[0] &= RippleAccessibilityService.previewRipple(p[0], p[1], p[0], p[1]));
                sleep(500);
            }
            sb.append(L.t("第一次（新畫法，畫在預覽卡片上）：")).append(drewA[0] ? L.t("已畫 5 個位置\n") : L.t("預覽小窗沒有開，沒畫\n"));
            sleep(2500);

            // B：畫在背景螢幕的覆蓋層上
            final String[] stateB = {""};
            final int[] drewB = {0};
            for (int[] p : pts) {
                onMain(() -> {
                    RippleView v = RippleAccessibilityService.viewFor(BgDisplay.id);
                    if (v == null) { stateB[0] = L.t("覆蓋層建立失敗"); return; }
                    stateB[0] = v.isAttachedToWindow() ? L.t("覆蓋層已建立") : L.t("覆蓋層沒有掛上視窗");
                    v.ripple(p[0], p[1]);
                    drewB[0]++;
                });
                sleep(500);
            }
            sb.append(L.t("第二次（舊畫法，畫在背景螢幕的覆蓋層上）：")).append(stateB[0].isEmpty() ? L.t("沒有動作") : stateB[0]).append("，").append(drewB[0]).append(L.t(" 個位置已送出畫\n"));
            sleep(2500);
            sb.append('\n').append(L.t("請回報：預覽小窗裡，第一次、第二次，哪一次有看到漣漪？（位置應該在中央，以及四個象限的中心）"));
        } catch (Throwable t) {
            sb.append(L.t("出錯：")).append(t);
        } finally {
            if (created) BgDisplay.stop();
        }
        return sb.toString();
    }

    private static void onMain(Runnable r) {
        final CountDownLatch done = new CountDownLatch(1);
        MAIN.post(() -> { try { r.run(); } finally { done.countDown(); } });
        try { done.await(3, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
