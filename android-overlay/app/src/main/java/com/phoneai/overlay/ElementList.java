package com.phoneai.overlay;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 元件清單（省錢實驗）：不截圖，改用無障礙服務讀目標 App 視窗裡的可點元件，編上號碼給 AI 選。
 * 每個元件是 {n: 編號, x, y: 中心的 0～1000 相對位置, t: 文字}；點擊時 AI 只要回編號。
 */
final class ElementList {
    private ElementList() { }

    private static final int MAX = 80;
    private static final int LABEL_MAX = 40;

    /** 目前的目標 App 畫面上的元件；拿不到（無障礙沒開、沒有目標視窗）回空清單。 */
    static List<JSONObject> collect(int w, int h) {
        List<JSONObject> out = new ArrayList<>();
        RippleAccessibilityService svc = RippleAccessibilityService.instance;
        if (svc == null) return out;
        AccessibilityNodeInfo root = targetRoot(svc);
        if (root == null) return out;
        walk(root, w, h, out);
        return out;
    }

    /** 不是 PhoneAI 自己的應用程式視窗；優先取活動中的那個（助理面板也是 PhoneAI 的視窗，要略過）。 */
    private static AccessibilityNodeInfo targetRoot(RippleAccessibilityService svc) {
        AccessibilityNodeInfo fallback = null;
        List<AccessibilityWindowInfo> wins = svc.getWindows();
        if (wins == null) return null;
        for (AccessibilityWindowInfo win : wins) {
            if (win.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
            AccessibilityNodeInfo r = win.getRoot();
            if (r == null) continue;
            if (svc.getPackageName().equals(String.valueOf(r.getPackageName()))) continue;
            if (win.isActive()) return r;
            if (fallback == null) fallback = r;
        }
        return fallback;
    }

    private static void walk(AccessibilityNodeInfo n, int w, int h, List<JSONObject> out) {
        if (n == null || out.size() >= MAX) return;
        if (n.isVisibleToUser()) {
            Rect r = new Rect();
            n.getBoundsInScreen(r);
            String label = label(n);
            boolean actionable = n.isClickable() || n.isLongClickable() || n.isCheckable() || n.isEditable();
            boolean onScreen = r.width() > 0 && r.height() > 0 && r.bottom > 0 && r.top < h && r.right > 0 && r.left < w;
            if (onScreen && (actionable || !label.isEmpty())) {
                JSONObject el = element(out.size() + 1, (r.centerX() * 1000) / w, (r.centerY() * 1000) / h, label);
                if (el != null) out.add(el);
            }
        }
        for (int i = 0; i < n.getChildCount() && out.size() < MAX; i++) walk(n.getChild(i), w, h, out);
    }

    private static String label(AccessibilityNodeInfo n) {
        CharSequence t = n.getText();
        if (t == null || t.toString().trim().isEmpty()) t = n.getContentDescription();
        if (t == null) return "";
        String s = t.toString().trim().replace('\n', ' ');
        return s.length() > LABEL_MAX ? s.substring(0, LABEL_MAX) : s;
    }

    private static JSONObject element(int n, int x, int y, String t) {
        try {
            return new JSONObject().put("n", n).put("x", x).put("y", y).put("t", t);
        } catch (Exception e) {
            return null;
        }
    }

    /** 給 AI 看的清單：只有編號與文字，不給座標（點擊時程式去查）。 */
    static String describe(List<JSONObject> els) {
        StringBuilder sb = new StringBuilder();
        for (JSONObject o : els) sb.append(o.optInt("n")).append(". ").append(o.optString("t").isEmpty() ? "（無文字）" : o.optString("t")).append('\n');
        return sb.toString();
    }

    /** 把 action 裡的 pick（編號）換成 x、y；找不到編號回 false。 */
    static boolean pick(JSONObject action, List<JSONObject> els) {
        int n = action.optInt("pick", -1);
        for (JSONObject o : els) {
            if (o.optInt("n") != n) continue;
            try {
                action.put("x", o.optInt("x")).put("y", o.optInt("y"));
                return true;
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }
}
