package com.phoneai.overlay;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 專案腳本：從 GitHub 下載整個 repo（公開的不需要權杖），複製到 Termux 的 ~/phone-ai-agent，再執行 install.sh 把腳本放到 ~。
 * 首次設定與「檢查並更新」都用這裡，所以 App 更新時腳本也會一起更新。
 */
final class Project {
    private Project() { }

    static final String REPO_DIR = Maintenance.HOME + "/phone-ai-agent";

    /** 成功回傳 null，失敗回傳給人看的原因。say 會收到進度訊息。 */
    static String install(Context c, java.util.function.Consumer<String> say) {
        try {
            if (!Maintenance.suRun("[ -d " + Maintenance.HOME + " ] && echo yes").contains("yes"))
                return "還沒有安裝 Termux：請先到「設定導覽」第 6 步完成安裝";
            File zip = new File(c.getCacheDir(), "repo.zip");
            try {
                download("https://codeload.github.com/" + BuildConfig.REPO + "/zip/refs/heads/main", zip, null, say);
            } catch (java.io.IOException e) {
                // 私人 repo：改用已存的權杖（只在記憶體用來下載）
                String t = Maintenance.suRun("cat " + Maintenance.TOKEN).trim();
                if (t.isEmpty() || t.startsWith("ERR:")) return "下載專案失敗（" + e.getMessage() + "）";
                download("https://api.github.com/repos/" + BuildConfig.REPO + "/zipball/main", zip, t, say);
            }
            File dir = new File(c.getCacheDir(), "repo");
            deleteRec(dir);
            unzipStrip(zip, dir);
            zip.delete();
            String r = Maintenance.suRun("U=$(stat -c %u /data/data/com.termux)\nrm -rf " + REPO_DIR + "\ncp -r '" + dir.getAbsolutePath() + "' " + REPO_DIR + "\n"
                    + "chown -R $U:$U " + REPO_DIR + "\n[ -f " + REPO_DIR + "/install.sh ] && echo copied");
            deleteRec(dir);
            if (!r.contains("copied")) return "複製專案失敗：" + r.trim();
            say.accept("安裝腳本中…");
            Maintenance.suStream(Maintenance.termuxScript("bash ~/phone-ai-agent/install.sh 2>&1"), l -> say.accept(l.length() > 90 ? l.substring(0, 90) : l));
            boolean ok = Maintenance.suRun("[ -f " + Maintenance.HOME + "/assistant.py ] && echo yes").contains("yes");
            return ok ? null : "腳本沒有複製到 Termux 家目錄";
        } catch (Exception e) {
            return "安裝腳本失敗：" + e.getMessage();
        }
    }

    static void download(String url, File dest, String token, java.util.function.Consumer<String> say) throws Exception {
        String cur = url;
        boolean first = true;
        for (int hop = 0; hop < 6; hop++) {
            HttpURLConnection c = (HttpURLConnection) new URL(cur).openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(20000);
            c.setReadTimeout(60000);
            c.setRequestProperty("User-Agent", "PhoneAI-Setup");
            c.setRequestProperty("Accept", "application/vnd.github+json");
            if (first && token != null) c.setRequestProperty("Authorization", "Bearer " + token);
            int code = c.getResponseCode();
            if (code / 100 == 3) {
                cur = c.getHeaderField("Location");
                first = false;
                c.disconnect();
                continue;
            }
            if (code != 200) throw new java.io.IOException("HTTP " + code + "（" + (cur.length() > 60 ? cur.substring(0, 60) : cur) + "）");
            long total = c.getContentLengthLong(), got = 0, lastSay = 0;
            try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    got += n;
                    if (total > 5_000_000 && got - lastSay > total / 10) {
                        lastSay = got;
                        say.accept("  下載 " + (got * 100 / total) + "%");
                    }
                }
            }
            return;
        }
        throw new java.io.IOException("轉址太多次");
    }

    private static void unzipStrip(File zip, File dest) throws Exception {
        String canon = dest.getCanonicalPath() + File.separator;
        dest.mkdirs();
        try (ZipInputStream z = new ZipInputStream(new java.io.BufferedInputStream(new java.io.FileInputStream(zip)))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                String name = e.getName();
                int k = name.indexOf('/');
                if (k < 0) continue;
                name = name.substring(k + 1);   // 去掉最上層的「owner-repo-sha/」
                if (name.isEmpty()) continue;
                File out = new File(dest, name);
                if (!out.getCanonicalPath().startsWith(canon)) continue;   // 擋 zip slip
                if (e.isDirectory()) { out.mkdirs(); continue; }
                out.getParentFile().mkdirs();
                try (OutputStream o = new FileOutputStream(out)) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = z.read(buf)) > 0) o.write(buf, 0, n);
                }
            }
        }
    }

    private static void deleteRec(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteRec(k);
        f.delete();
    }
}
