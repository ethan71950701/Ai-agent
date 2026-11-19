package com.phoneai.overlay;

import android.content.Context;
import android.util.SparseArray;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.List;

/**
 * 診斷實驗：背景螢幕（虛擬螢幕）上，無障礙服務拿不拿得到元件清單？
 * 建立背景螢幕、在上面開「設定」，然後用 getWindowsOnAllDisplays() 看每個螢幕有幾個視窗、每個視窗讀得到幾個元件，最後收掉背景螢幕。
 * 結果只是診斷文字（不含畫面內容文字）。需要 root 與無障礙服務。在背景執行緒呼叫，約 10 秒。
 */
final class BgProbe {
    private BgProbe() { }

    static String run(Context c) {
        StringBuilder sb = new StringBuilder();
        RippleAccessibilityService svc = RippleAccessibilityService.instance;
        if (svc == null) return L.t("無障礙服務沒有開，沒辦法測。");
        if (!Root.isRoot()) return L.t("沒有 root，背景螢幕建立不了。");
        sb.append(L.t("主螢幕（對照）：")).append(describe(svc.getWindowsOnAllDisplays(), 0)).append('\n');
        boolean created = false;
        try {
            created = BgDisplay.start(c);
            if (!created) return sb.append(L.t("背景螢幕建立失敗。")).toString();
            sb.append(L.t("背景螢幕建立成功，編號 ")).append(BgDisplay.id).append('\n');
            Maintenance.suRun(BgDisplay.launch("com.android.settings", BgDisplay.id));
            try { Thread.sleep(2500); } catch (InterruptedException ignored) { }
            SparseArray<List<AccessibilityWindowInfo>> all = svc.getWindowsOnAllDisplays();
            StringBuilder ids = new StringBuilder();
            for (int i = 0; all != null && i < all.size(); i++) ids.append(all.keyAt(i)).append(' ');
            sb.append(L.t("無障礙看得到的螢幕編號：")).append(ids.length() == 0 ? L.t("（沒有）") : ids.toString().trim()).append('\n');
            boolean seen = all != null && all.indexOfKey(BgDisplay.id) >= 0;
            sb.append(seen ? L.t("→ 看得到背景螢幕\n") : L.t("→ 看不到背景螢幕（無障礙服務沒有這個螢幕的視窗）\n"));
            if (seen) sb.append(L.t("背景螢幕：")).append(describe(all, BgDisplay.id));
        } catch (Throwable t) {
            sb.append(L.t("出錯：")).append(t);
        } finally {
            if (created) BgDisplay.stop();
        }
        return sb.toString();
    }

    private static String describe(SparseArray<List<AccessibilityWindowInfo>> all, int displayId) {
        List<AccessibilityWindowInfo> wins = all == null ? null : all.get(displayId);
        if (wins == null || wins.isEmpty()) return L.t("沒有視窗");
        StringBuilder sb = new StringBuilder(wins.size() + L.t(" 個視窗"));
        for (AccessibilityWindowInfo w : wins) {
            AccessibilityNodeInfo r = w.getRoot();
            int[] n = new int[2];   // 全部元件數、可點元件數
            if (r != null) count(r, n, 0);
            sb.append("\n  • type ").append(w.getType()).append(" ").append(r == null ? L.t("讀不到內容") : String.valueOf(r.getPackageName()) + "，" + n[0] + L.t(" 個元件，") + n[1] + L.t(" 個可點"));
        }
        return sb.toString();
    }

    private static void count(AccessibilityNodeInfo n, int[] out, int depth) {
        if (n == null || out[0] > 800 || depth > 40) return;
        out[0]++;
        if (n.isClickable()) out[1]++;
        for (int i = 0; i < n.getChildCount(); i++) count(n.getChild(i), out, depth + 1);
    }
}
