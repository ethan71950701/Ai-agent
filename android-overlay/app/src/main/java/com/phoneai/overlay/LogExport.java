package com.phoneai.overlay;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** 把診斷資訊與最近的任務過程匯出成一個文字檔分享出去（貼給別人看用）。不含 API Key；常見的 Key 格式與 Bearer 權杖會被遮蔽。 */
final class LogExport {
    private LogExport() { }

    static final int MAX_TASKS = 20;

    /** 遮蔽看起來像 Key 的字串（保險用：正常不會有 Key 進到紀錄裡）。 */
    static String redact(String s) {
        if (s == null) return "";
        return s.replaceAll("AIza[0-9A-Za-z_\\-]{20,}", "[已遮蔽]")
                .replaceAll("AQ\\.[0-9A-Za-z_\\-]{20,}", "[已遮蔽]")
                .replaceAll("sk-[0-9A-Za-z_\\-]{16,}", "[已遮蔽]")
                .replaceAll("(?i)(bearer|x-api-key|api[_-]?key)(\\s*[:=]\\s*|\\s+)[^\\s\"',]{12,}", "$1 [已遮蔽]");
    }

    private static String slot(Context c, LlmConfig.Slot s) {
        return s.openai() ? "OpenAI 相容 " + LlmConfig.host(s.base) + " / " + s.model : "Claude（Anthropic）";
    }

    static String build(Context c) {
        StringBuilder sb = new StringBuilder();
        sb.append("PhoneAI 匯出紀錄\n");
        sb.append("匯出時間：").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(new java.util.Date())).append('\n');
        sb.append("App 版本：1.0.").append(BuildConfig.VERSION_CODE).append('\n');
        sb.append("裝置：").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append("，Android ").append(Build.VERSION.RELEASE).append("（API ").append(Build.VERSION.SDK_INT).append("）\n");
        try { sb.append("執行模式：").append(ExecMode.get(c)).append("；shell 可用：").append(Root.available()).append("；真 root：").append(Root.knownRoot()).append('\n'); } catch (Exception ignored) { }
        try { sb.append("便宜模型服務：").append(slot(c, LlmConfig.cheap(c))).append('\n'); sb.append("看圖模型服務：").append(slot(c, LlmConfig.vision(c))).append('\n'); } catch (Exception ignored) { }
        sb.append("通知診斷：").append(OverlayService.lastDiag).append('\n');
        sb.append("無障礙服務：").append(A11yGuard.listed(c) ? "開" : "關").append('\n');
        sb.append("\n====== 最近的任務（最新在前） ======\n");
        JSONArray list = TaskLog.list(c);
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("MM/dd HH:mm:ss", java.util.Locale.US);
        for (int i = 0; i < Math.min(MAX_TASKS, list.length()); i++) {
            JSONObject o = list.optJSONObject(i);
            if (o == null) continue;
            sb.append("\n【").append(f.format(new java.util.Date(o.optLong("id")))).append("】").append(o.optString("text")).append('\n');
            sb.append("回答：").append(o.optString("reply")).append('\n');
            sb.append("token：").append(o.optLong("tokens")).append("，估價：$").append(String.format(java.util.Locale.US, "%.4f", o.optDouble("cost"))).append('\n');
            for (String k : new String[]{"card", "macro", "search"}) if (!o.optString(k).isEmpty()) sb.append(k).append("：").append(o.optString(k)).append('\n');
            JSONArray d = o.optJSONArray("detail");
            for (int j = 0; d != null && j < d.length(); j++) {
                JSONObject s = d.optJSONObject(j);
                if (s == null) continue;
                sb.append("  第 ").append(s.optInt("step", j + 1)).append(" 步 [").append(s.optString("action")).append("] ")
                        .append(s.optString("model")).append(' ').append(s.optLong("tokens")).append(" token\n");
                if (!s.optString("think").isEmpty()) sb.append("    思考：").append(s.optString("think")).append('\n');
                if (!s.optString("result").isEmpty()) sb.append("    結果：").append(s.optString("result")).append('\n');
            }
        }
        String errs = CrashLog.read(c);
        if (!errs.trim().isEmpty()) sb.append("\n====== 錯誤紀錄（當機與出錯） ======\n").append(errs.length() > 20000 ? "…（前面較舊的省略）\n" + errs.substring(errs.length() - 20000) : errs);
        sb.append("\n====== 系統記錄（logcat，只含本 App，最近 200 行） ======\n").append(ownLogcat());
        return redact(sb.toString());
    }

    /** 本 App 自己的 logcat（不需要權限，只看得到自己的輸出）；5 秒內沒讀完就放棄。 */
    private static String ownLogcat() {
        try {
            Process p = new ProcessBuilder("logcat", "-d", "-t", "200").redirectErrorStream(true).start();
            final java.io.InputStream in = p.getInputStream();
            final java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            Thread t = new Thread(() -> { try { byte[] b = new byte[4096]; int n; while ((n = in.read(b)) > 0) buf.write(b, 0, n); } catch (Exception ignored) { } });
            t.setDaemon(true);
            t.start();
            if (!p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) p.destroyForcibly();
            t.join(1000);
            return new String(buf.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "（讀不到：" + e.getMessage() + "）";
        }
    }

    /** 把一段文字存成檔案分享出去。 */
    static void shareText(Activity a, String text, String prefix) {
        new Thread(() -> {
            try {
                File dir = new File(a.getCacheDir(), "export");
                File[] old = dir.listFiles();
                if (old != null) for (File o : old) o.delete();
                dir.mkdirs();
                File out = new File(dir, prefix + new java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.US).format(new java.util.Date()) + ".txt");
                try (FileOutputStream fo = new FileOutputStream(out)) { fo.write(text.getBytes(StandardCharsets.UTF_8)); }
                Uri uri = androidx.core.content.FileProvider.getUriForFile(a, "com.phoneai.overlay.update", out);
                Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri)
                        .putExtra(Intent.EXTRA_SUBJECT, "PhoneAI").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                a.runOnUiThread(() -> a.startActivity(Intent.createChooser(send, L.t("匯出紀錄"))));
            } catch (Exception e) {
                a.runOnUiThread(() -> android.widget.Toast.makeText(a, L.t("匯出失敗：") + e.getMessage(), android.widget.Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    /** 把一段文字存到「下載/PhoneAI」。 */
    static void saveText(Activity a, String text, String prefix) {
        new Thread(() -> {
            try {
                String name = prefix + new java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.US).format(new java.util.Date()) + ".txt";
                android.content.ContentValues v = new android.content.ContentValues();
                v.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name);
                v.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                v.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Download/PhoneAI");
                Uri uri = a.getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri == null) throw new java.io.IOException("無法建立檔案");
                try (java.io.OutputStream o = a.getContentResolver().openOutputStream(uri)) { o.write(text.getBytes(StandardCharsets.UTF_8)); }
                a.runOnUiThread(() -> android.widget.Toast.makeText(a, L.t("已存到 下載/PhoneAI/") + name, android.widget.Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                a.runOnUiThread(() -> android.widget.Toast.makeText(a, L.t("匯出失敗：") + e.getMessage(), android.widget.Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    /** 存到「下載/PhoneAI」資料夾（用 MediaStore，不需要儲存權限）。 */
    static void saveToDownloads(Activity a) {
        new Thread(() -> {
            try {
                String name = "phoneai_log_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.US).format(new java.util.Date()) + ".txt";
                android.content.ContentValues v = new android.content.ContentValues();
                v.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name);
                v.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                v.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Download/PhoneAI");
                Uri uri = a.getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri == null) throw new java.io.IOException("無法建立檔案");
                try (java.io.OutputStream o = a.getContentResolver().openOutputStream(uri)) { o.write(build(a).getBytes(StandardCharsets.UTF_8)); }
                a.runOnUiThread(() -> android.widget.Toast.makeText(a, L.t("已存到 下載/PhoneAI/") + name, android.widget.Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                a.runOnUiThread(() -> android.widget.Toast.makeText(a, L.t("匯出失敗：") + e.getMessage(), android.widget.Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    /** 存成快取資料夾裡的文字檔，丟給系統的分享選單。 */
    static void share(Activity a) {
        new Thread(() -> {
            try {
                File dir = new File(a.getCacheDir(), "export");
                File[] old = dir.listFiles();
                if (old != null) for (File o : old) o.delete();
                dir.mkdirs();
                File out = new File(dir, "phoneai_log_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.US).format(new java.util.Date()) + ".txt");
                try (FileOutputStream fo = new FileOutputStream(out)) { fo.write(build(a).getBytes(StandardCharsets.UTF_8)); }
                Uri uri = androidx.core.content.FileProvider.getUriForFile(a, "com.phoneai.overlay.update", out);
                Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri)
                        .putExtra(Intent.EXTRA_SUBJECT, "PhoneAI 紀錄").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                a.runOnUiThread(() -> a.startActivity(Intent.createChooser(send, L.t("匯出紀錄"))));
            } catch (Exception e) {
                a.runOnUiThread(() -> android.widget.Toast.makeText(a, L.t("匯出失敗：") + e.getMessage(), android.widget.Toast.LENGTH_LONG).show());
            }
        }).start();
    }
}
