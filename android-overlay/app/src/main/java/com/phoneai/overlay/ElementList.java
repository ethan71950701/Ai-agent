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

    /** 畫面最上層、讀得到元件的視窗裡的元件；拿不到（無障礙沒開、沒有可讀的視窗）回空清單。 */
    static List<JSONObject> collect(int w, int h) {
        List<JSONObject> out = new ArrayList<>();
        RippleAccessibilityService svc = RippleAccessibilityService.instance;
        if (svc == null) return out;
        for (AccessibilityNodeInfo root : candidateRoots(svc, h)) {
            walk(root, w, h, out);
            if (!out.isEmpty()) return out;   // 最上層的視窗讀不到東西（例如收起來的通知欄），就換下一層
        }
        return out;
    }

    /**
     * 由上往下（依圖層）的候選視窗：別的 App 的應用程式視窗，以及佔螢幕四成以上高度的系統視窗（拉下來的通知欄、快速設定）。
     * 略過 PhoneAI 自己的視窗、鍵盤、無障礙懸浮層；狀態列、導覽列這種細長的系統視窗也略過。
     */
    private static List<AccessibilityNodeInfo> candidateRoots(RippleAccessibilityService svc, int h) {
        List<AccessibilityNodeInfo> roots = new ArrayList<>();
        List<AccessibilityWindowInfo> wins = svc.getWindows();
        if (wins == null) return roots;
        List<AccessibilityWindowInfo> sorted = new ArrayList<>(wins);
        sorted.sort((a, b) -> b.getLayer() - a.getLayer());
        Rect bounds = new Rect();
        for (AccessibilityWindowInfo win : sorted) {
            int type = win.getType();
            if (type == AccessibilityWindowInfo.TYPE_SYSTEM) {
                win.getBoundsInScreen(bounds);
                if (bounds.height() < h * 0.4) continue;
            } else if (type != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
            AccessibilityNodeInfo r = win.getRoot();
            if (r == null || svc.getPackageName().equals(String.valueOf(r.getPackageName()))) continue;
            roots.add(r);
        }
        return roots;
    }

    /** 目前前景（目標）App 的套件名稱；讀不到回 null。經驗要記在哪個 App、要帶哪個 App 的經驗，都用它。 */
    static String foregroundPackage() {
        RippleAccessibilityService svc = RippleAccessibilityService.instance;
        if (svc == null) return null;
        AccessibilityNodeInfo r = targetRoot(svc);
        return r == null || r.getPackageName() == null ? null : r.getPackageName().toString();
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
                String kind = n.isEditable() ? "輸入框" : actionable ? "按鈕" : "文字";
                JSONObject el = element(out.size() + 1, (r.centerX() * 1000) / w, (r.centerY() * 1000) / h, label, kind, n.isFocused());
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

    private static JSONObject element(int n, int x, int y, String t, String k, boolean focused) {
        try {
            return new JSONObject().put("n", n).put("x", x).put("y", y).put("t", t).put("k", k).put("f", focused);
        } catch (Exception e) {
            return null;
        }
    }

    /** 給 AI 看的清單：編號、種類（輸入框／按鈕／文字）、文字、大約位置（0～1000，只用來分辨上下左右；點擊仍用編號）。 */
    static String describe(List<JSONObject> els) {
        StringBuilder sb = new StringBuilder();
        for (JSONObject o : els) {
            sb.append(o.optInt("n")).append(". ").append(o.optString("k")).append(o.optBoolean("f") ? "（已聚焦）" : "").append('「')
                    .append(o.optString("t").isEmpty() ? "無文字" : o.optString("t")).append("」(")
                    .append(o.optInt("x")).append(',').append(o.optInt("y")).append(")\n");
        }
        return sb.toString();
    }

    /** 動作本身或 batch 裡每個子動作的 pick 都換成 x、y；有任何一個找不到編號就回 false。 */
    static boolean pickAll(JSONObject action, List<JSONObject> els) {
        if (action.has("pick") && !pick(action, els)) return false;
        org.json.JSONArray subs = action.optJSONArray("actions");
        for (int i = 0; subs != null && i < subs.length(); i++) {
            JSONObject sub = subs.optJSONObject(i);
            if (sub != null && sub.has("pick") && !pick(sub, els)) return false;
        }
        return true;
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
