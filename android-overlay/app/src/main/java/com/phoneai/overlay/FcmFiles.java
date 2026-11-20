package com.phoneai.overlay;

import org.json.JSONObject;

/**
 * 推播喚醒用的兩個 JSON 檔（純 Java）：Firebase 的 google-services.json（設定檔），與服務帳戶金鑰（私密金鑰）。
 * 使用者一次選兩個檔案時，靠內容判斷哪個是哪個；也檢查兩個是不是同一個 Firebase 專案。
 */
final class FcmFiles {
    private FcmFiles() { }

    enum Kind { CONFIG, SERVICE_ACCOUNT, UNKNOWN }

    static Kind classify(String json) {
        try {
            JSONObject j = new JSONObject(json);
            if ("service_account".equals(j.optString("type")) && j.has("private_key") && j.has("client_email")) return Kind.SERVICE_ACCOUNT;
            if (j.has("project_info") && j.has("client")) return Kind.CONFIG;
        } catch (Exception ignored) { }
        return Kind.UNKNOWN;
    }

    /** 設定檔缺什麼就回原因，沒問題回 null（初始化 Firebase 需要這幾個欄位）。 */
    static String configProblem(String json) {
        try {
            JSONObject j = new JSONObject(json);
            JSONObject info = j.getJSONObject("project_info");
            if (info.optString("project_id").isEmpty() || info.optString("project_number").isEmpty()) return "設定檔缺少專案編號";
            JSONObject cl = j.getJSONArray("client").getJSONObject(0);
            if (cl.getJSONObject("client_info").optString("mobilesdk_app_id").isEmpty()) return "設定檔缺少 App 編號";
            if (cl.getJSONArray("api_key").getJSONObject(0).optString("current_key").isEmpty()) return "設定檔缺少 API 金鑰欄位";
            String pkg = cl.getJSONObject("client_info").getJSONObject("android_client_info").optString("package_name");
            if (!pkg.isEmpty() && !pkg.equals("com.phoneai.overlay")) return "套件名稱不對（是 " + pkg + "，要 com.phoneai.overlay）";
            return null;
        } catch (Exception e) {
            return "設定檔格式不對";
        }
    }

    static String projectOfConfig(String json) {
        try { return new JSONObject(json).getJSONObject("project_info").getString("project_id"); } catch (Exception e) { return null; }
    }

    static String projectOfKey(String json) {
        try { return new JSONObject(json).getString("project_id"); } catch (Exception e) { return null; }
    }

    /** 兩個檔案要是同一個專案；不是就回原因。任何一個缺就回 null（沒東西可比）。 */
    static String mismatch(String configJson, String keyJson) {
        String a = configJson == null ? null : projectOfConfig(configJson), b = keyJson == null ? null : projectOfKey(keyJson);
        if (a == null || b == null) return null;
        return a.equals(b) ? null : "設定檔是專案「" + a + "」，金鑰卻是專案「" + b + "」，兩個要同一個 Firebase 專案";
    }
}
