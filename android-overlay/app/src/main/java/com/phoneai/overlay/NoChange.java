package com.phoneai.overlay;

import org.json.JSONObject;

/**
 * 「點了、但畫面完全沒變」的處理（純邏輯）：AI 做了一個應該會讓畫面改變的動作，下一步讀到的元件清單卻和上一步一模一樣。
 * 第 1 次：在歷史裡提醒它換個做法；第 2 次：下一步改看截圖（較強的模型）；第 3 次：停下來告訴使用者卡在哪，不再一直花錢。
 * 「沒變」只是線索不是判決（有些畫面本來就不變，例如相機取景），所以只有連續三次才停。
 */
final class NoChange {
    private NoChange() { }

    static final int NONE = 0, HINT = 1, ESCALATE = 2, STOP = 3;

    /** 這種動作做完，畫面「應該」會變（等待、開 App、回桌面、回覆問題這類不算）。 */
    static boolean expectsChange(String action) {
        if (action == null) return false;
        switch (action) {
            case "tap": case "double_tap": case "longpress": case "swipe": case "drag": case "scroll":
            case "type": case "enter": case "back": case "batch":
                return true;
            default:
                return false;
        }
    }

    /** 連續幾次「動作之後清單沒變」對應的處理。 */
    static int level(int consecutive) {
        return consecutive >= 3 ? STOP : consecutive == 2 ? ESCALATE : consecutive == 1 ? HINT : NONE;
    }

    /** 動作的簡短描述（給提醒與停下來的訊息用）。 */
    static String describe(JSONObject action) {
        String name = action.optString("action");
        if (action.has("pick")) return name + "（元件編號 " + action.optInt("pick") + "）";
        if (action.has("x") && action.has("y")) return name + "（座標 " + Math.round(action.optDouble("x")) + "," + Math.round(action.optDouble("y")) + "）";
        return name;
    }

    /** 給 AI 看的提醒（寫進歷史）。 */
    static String hint(String desc, int level) {
        String base = "提示：你上一步做了「" + desc + "」，但畫面的元件清單和做之前完全一樣，沒有任何變化。可能原因：沒點到、這個按鈕沒反應、要等一下才會有反應、或點到不能點的東西。";
        if (level >= ESCALATE) return base + "而且這已經是連續第二次沒變：不要再做一樣的動作，這一步會給你截圖，請看實際畫面再決定（換別的元件、用座標、先滾動、按返回），做不到就 fail 說明卡在哪。";
        return base + "請換個做法：點別的元件、用座標點、先滾動、按返回，或等一下再看；不要重複同一個動作。";
    }

    /** 連續三次沒變時，告訴使用者的話。 */
    static String stopMessage(String desc) {
        return L.t("我試了好幾次，畫面都沒有變化，卡在：") + desc + L.t("。可能是這個按鈕沒反應、需要你自己操作，或需要登入、授權。你可以接手做，或換個說法再叫我一次。");
    }
}
