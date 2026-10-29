package com.phoneai.overlay;

/** 把 AI 服務回的 HTTP 錯誤翻成人看得懂的說明（429＝額度用完…），後面附原始回應的開頭給除錯用。 */
final class LlmErrors {
    private LlmErrors() { }

    static String explain(int code, String body, String service) {
        String what;
        switch (code) {
            case 429:
                what = L.t("額度用完了：這一分鐘或今天的使用次數（或 token）超過上限。免費方案的上限很小，每日額度在太平洋時間午夜重設（台灣約下午 3～4 點）。等一下再試，或到「功能 → AI 服務與 API Key」換一個服務／改用 Claude。");
                break;
            case 401:
                what = L.t("Key 不對或已失效：到「功能 → AI 服務與 API Key」重新輸入這個服務的 Key。");
                break;
            case 403:
                what = L.t("沒有權限：Key 可能沒開這個功能或這個模型，或這個地區不能用（免費方案有地區限制時會這樣）。");
                break;
            case 404:
                what = L.t("找不到這個模型：模型名稱可能已改名或下架。到「功能 → AI 服務與 API Key」重選服務，Key 存好後按「查詢」從清單選。");
                break;
            case 400:
                what = L.t("請求被拒絕：這個模型可能不支援看圖、工具，或內容太長。換一個支援看圖的模型再試。");
                break;
            case 402:
                what = L.t("餘額不足：到這個服務的網站儲值。");
                break;
            default:
                what = code >= 500 ? L.t("這個服務暫時有問題（伺服器錯誤），稍後再試。") : L.t("服務回傳了錯誤。");
        }
        String raw = body == null ? "" : body.replaceAll("\\s+", " ").trim();
        if (raw.length() > 160) raw = raw.substring(0, 160) + "…";
        return (service.isEmpty() ? "" : service + "：") + "HTTP " + code + "　" + what + (raw.isEmpty() ? "" : "\n（" + raw + "）");
    }
}
