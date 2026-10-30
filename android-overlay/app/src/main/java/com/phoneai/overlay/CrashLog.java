package com.phoneai.overlay;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

/**
 * App 自己的錯誤紀錄：當機（沒攔到的例外）與程式主動記下的錯誤都寫進 App 資料夾的 app_errors.log，
 * 在「功能 → 維護 → 錯誤紀錄」看、分享、存檔。不含 API Key；最多留約 120 KB，超過就丟掉最舊的一半。
 */
final class CrashLog {
    private CrashLog() { }

    private static final int MAX = 120_000;

    static File file(Context c) { return new File(c.getFilesDir(), "app_errors.log"); }

    /** 在 Application.onCreate 呼叫：沒攔到的例外先寫進紀錄，再交還給系統（照原本的方式結束程序）。 */
    static void install(final Context c) {
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try { write(c, "當機（執行緒 " + t.getName() + "）", stack(e)); } catch (Throwable ignored) { }
            if (prev != null) prev.uncaughtException(t, e);
        });
    }

    static String stack(Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    /** 程式主動記一筆（已經攔下來、沒有當機，但值得留下的錯誤）。 */
    static void w(String tag, String msg) {
        if (App.ctx != null) write(App.ctx, tag, msg);
    }

    static void w(String tag, Throwable e) {
        if (App.ctx != null) write(App.ctx, tag, stack(e));
    }

    static synchronized void write(Context c, String title, String body) {
        try {
            File f = file(c);
            String entry = "=== " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(new java.util.Date())
                    + " · v1.0." + BuildConfig.VERSION_CODE + " · " + title + "\n" + LogExport.redact(body) + "\n\n";
            if (f.length() > MAX) {   // 太大：只留後面一半
                String all = new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                int cut = all.indexOf("\n=== ", all.length() / 2);
                java.nio.file.Files.write(f.toPath(), (cut < 0 ? "" : all.substring(cut + 1)).getBytes(StandardCharsets.UTF_8));
            }
            try (FileOutputStream o = new FileOutputStream(f, true)) { o.write(entry.getBytes(StandardCharsets.UTF_8)); }
        } catch (Throwable ignored) { }
    }

    static String read(Context c) {
        try {
            File f = file(c);
            return f.isFile() ? new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8) : "";
        } catch (Throwable e) {
            return "";
        }
    }

    static void clear(Context c) { file(c).delete(); }
}
