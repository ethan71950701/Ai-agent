package com.phoneai.overlay;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 首次設定精靈：只裝這一個 APK，其餘（Termux、Termux:API、套件、專案腳本）都在這裡下載、安裝。
 * 每一步先檢查「已經完成」就跳過，所以可以重複按（失敗後再按一次會從失敗的那步接著做）。需要 root。
 * 狀態：整條流程沒有在手機上實測過（2026-10-20 寫成）。
 */
public class SetupActivity extends Activity {
    private static final String REPO = BuildConfig.REPO;   // 編譯時決定（CI 帶 GITHUB_REPOSITORY，fork 會自動指向自己的 repo）
    private static final String P = "/data/data/com.termux/files/usr/bin/";
    private static final String[] NAMES = {"Termux", "Termux:API", "Termux 基本環境", "基本套件（Python、Git…）", "專案腳本", "Claude API Key"};
    private final boolean[] done = new boolean[NAMES.length];
    private LinearLayout list;
    private TextView log;
    private volatile boolean busy;
    private String apiKey;      // 使用者在精靈裡輸入、尚未存檔的 Key（只在記憶體，存檔後丟掉）
    private final StringBuilder logText = new StringBuilder();
    // 進度顯示：目前第幾步、這一步花了多久、上次有回報是幾秒前（讓你知道它還在動）
    private static final String[] STEP_NAMES = {"安裝 Termux", "安裝 Termux:API", "Termux 基本環境", "補回權限", "安裝套件", "下載專案腳本", "儲存 API Key"};
    private ProgressBar bar;
    private TextView status;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private volatile int curStep = -1;
    private volatile long stepStart;
    private volatile long lastSay;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (busy && curStep >= 0) {
                long now = System.currentTimeMillis();
                long run = (now - stepStart) / 1000, quiet = (now - lastSay) / 1000;
                String tail = quiet < 3 ? "剛剛還有回報" : "已經 " + quiet + " 秒沒有新輸出（大多是在等下載或套件，請耐心等）";
                status.setText("第 " + (curStep + 1) + "／" + STEP_NAMES.length + " 步：" + STEP_NAMES[curStep]
                        + "\n這一步已花 " + run / 60 + " 分 " + run % 60 + " 秒　·　" + tail);
            }
            ui.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout[] c = new LinearLayout[1];
        setContentView(Ui.screen(this, "首次設定", true, c));
        Ui.enterSpring(this);
        LinearLayout box = c[0];
        TextView intro = Ui.infoCard(this, box, 14, false, false);
        intro.setText("只裝了這個 App 的話，按下面的按鈕，我會下載並安裝 Termux、Termux:API、需要的套件和專案腳本（共約 200～400 MB，請連 Wi-Fi、插電，過程約 5～15 分鐘；Termux 會短暫跳到前景一次，這是正常的）。需要 root。");
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        box.addView(list);
        Ui.buttons(this, box, Ui.pill(this, "開始設定／繼續", true, v -> start()), Ui.pill(this, "重新檢查", false, v -> check()));
        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setIndeterminate(true);
        bar.setMax(100);
        box.addView(bar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 8)));
        status = Ui.infoCard(this, box, 13, false, false);
        status.setText("尚未開始");
        log = Ui.infoCard(this, box, 12, true, true);
        ui.post(tick);
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacks(tick);
        super.onDestroy();
    }

    /** 進入新的一步：更新步驟名稱、重設計時。 */
    private void step(int n) {
        curStep = n;
        stepStart = System.currentTimeMillis();
        lastSay = stepStart;
        runOnUiThread(() -> { bar.setIndeterminate(true); });
    }

    @Override
    protected void onResume() {
        super.onResume();
        check();
    }

    private void say(String s) {
        lastSay = System.currentTimeMillis();
        // 下載的「  下載 NN%」行會直接變成進度條的百分比
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("下載 (\\d+)%").matcher(s);
        final int pct = m.find() ? Integer.parseInt(m.group(1)) : -1;
        runOnUiThread(() -> {
            if (pct >= 0) { bar.setIndeterminate(false); bar.setProgress(pct); }
            logText.append(s).append('\n');
            if (logText.length() > 6000) logText.delete(0, logText.length() - 5000);
            log.setText(logText);
        });
    }

    private void render() {
        list.removeAllViews();
        Ui.section(this, list, "步驟");
        for (int i = 0; i < NAMES.length; i++) {
            String label = done[i] ? "完成" : "尚未完成";
            if (i == 0 && done[0] && termuxFromPlay) label = "已安裝，但是 Google Play 版：請先解除安裝";
            Ui.row(this, list, i, NAMES.length, done[i] ? "check_circle" : "circle", NAMES[i], label, null, null);
        }
    }

    /** 檢查每一步是不是已經完成。 */
    private void check() {
        if (busy) return;
        new Thread(() -> {
            // 以「資料夾存在」或 pm 查得到，任一成立就算已安裝（避免 pm 輸出格式差異造成重複安裝）
            // 只以套件管理員為準：資料夾可能是之前殘留的，不能當作「已安裝」
            String r = Maintenance.suRun("pm path com.termux >/dev/null 2>&1 && echo T1\n"
                    + "pm path com.termux.api >/dev/null 2>&1 && echo T2\n"
                    + "[ -x " + P + "bash ] && echo T3\n"
                    + "[ -x " + P + "python ] && [ -x " + P + "git ] && [ -x " + P + "termux-notification ] && echo T4\n"
                    + "[ -f " + Maintenance.HOME + "/assistant.py ] && echo T5\n"
                    + "[ -s " + Maintenance.KEY + " ] && echo T6\n");
            for (int i = 0; i < NAMES.length; i++) done[i] = r.contains("T" + (i + 1));
            // Google Play 版的 Termux 簽章跟我們要的 GitHub 版不同，不能覆蓋安裝，也比較舊；要先請使用者解除安裝
            String inst = Maintenance.suRun("dumpsys package com.termux 2>/dev/null | grep -m1 installerPackageName; echo done");
            termuxFromPlay = inst.contains("com.android.vending");
            runOnUiThread(() -> { if (termuxFromPlay) say("⚠ 偵測到的 Termux 是 Google Play 版，請先解除安裝它（見下方說明），再按「開始安裝」"); });
            runOnUiThread(this::render);
        }).start();
    }

    private boolean termuxFromPlay;

    private void start() {
        if (busy) return;

        // 需要的輸入先問齊（Key 與權杖只留在記憶體，用完就丟）
        // 公開的 repo 不需要權杖；私人 repo 下載失敗時才會請你到「功能 → 維護 → GitHub 權杖」設定
        if (!done[5] && apiKey == null && !pendingKeyFile().exists()) {
            promptSecret("Claude API Key", "貼上 API Key（只存在這支手機 Termux 的 ~/.claude_key，不會顯示、不會上傳到別處；不要貼給任何人）。先不設定的話按取消，之後在「功能 → 維護」再設。", k -> {
                savePendingKey(k);
                start();
            }, () -> {
                savePendingKey("");
                start();
            });
            return;
        }
        busy = true;
        new Thread(this::runAll, "setup").start();
    }

    private interface Str { void ok(String s); }

    // Key 一輸入就先放在 App 自己的私有資料夾（不在 /sdcard），Termux 還沒裝好時也不會丟；存進 Termux 後才刪除
    private java.io.File pendingKeyFile() { return new java.io.File(getFilesDir(), "pending_key"); }

    private void savePendingKey(String k) {
        try {
            java.io.File f = pendingKeyFile();
            java.io.FileOutputStream out = new java.io.FileOutputStream(f);
            out.write(k.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.close();
            f.setReadable(false, false);
            f.setReadable(true, true);
            f.setWritable(false, false);
            f.setWritable(true, true);
        } catch (java.io.IOException e) {
            say("✗ 暫存 API Key 失敗：" + e.getMessage());
        }
    }

    private String readPendingKey() {
        try {
            java.io.File f = pendingKeyFile();
            if (!f.exists()) return null;
            byte[] b = java.nio.file.Files.readAllBytes(f.toPath());
            return new String(b, java.nio.charset.StandardCharsets.UTF_8).trim();
        } catch (java.io.IOException e) {
            return null;
        }
    }

    private void promptSecret(String title, String help, Str ok) { promptSecret(title, help, ok, null); }

    private void promptSecret(String title, String help, Str ok, Runnable skip) {
        final com.google.android.material.textfield.TextInputLayout til = new com.google.android.material.textfield.TextInputLayout(this);
        til.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
        til.setEndIconMode(com.google.android.material.textfield.TextInputLayout.END_ICON_PASSWORD_TOGGLE);
        final com.google.android.material.textfield.TextInputEditText et = new com.google.android.material.textfield.TextInputEditText(til.getContext());
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        til.addView(et);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int fp = Ui.dp(this, 20);
        form.setPadding(fp, fp / 2, fp, 0);
        form.addView(Ui.body(this, help));
        form.addView(til);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle(title).setView(form)
                .setPositiveButton("確定", (d, w) -> ok.ok(et.getText().toString().trim()))
                .setNegativeButton("取消", (d, w) -> { if (skip != null) skip.run(); }).show();
    }

    private void fail(String why) {
        say("✗ " + why);
        busy = false;
        runOnUiThread(this::check);
    }

    private void runAll() {
        try {
            if (!done[0] || termuxFromPlay) {
                step(0);
                say("→ 下載並安裝 Termux…");
                if (!installRelease("termux/termux-app", "arm64-v8a", "termux")) return;   // 已裝但是 Play 版時也會進來，問一次要不要替換
            }
            if (!done[1]) {
                step(1);
                say("→ 下載並安裝 Termux:API…");
                if (!installRelease("termux/termux-api", ".apk", "termux-api")) return;
            }
            if (!done[2]) {
                step(2);
                say("→ 啟動 Termux 一次，讓它解開基本環境（Termux 會跳到前景，完成後我會切回來）…");
                Maintenance.suRun("am start -n com.termux/com.termux.app.TermuxActivity");
                boolean ok = false;
                for (int i = 0; i < 90 && !ok; i++) {   // 最多等 3 分鐘；第一次開 Termux 會解壓基本環境，每 10 秒告訴你還在等
                    try { Thread.sleep(2000); } catch (InterruptedException ignored) { }
                    ok = Maintenance.suRun("[ -x " + P + "bash ] && echo yes").contains("yes");
                    if (!ok && i % 5 == 4) say("  仍在等 Termux 初始化（第一次可能要幾分鐘）…");
                }
                Maintenance.suRun("am start -n com.phoneai.overlay/.SetupActivity");
                if (!ok) { fail("Termux 的基本環境沒有在 3 分鐘內出現：請手動開一次 Termux，等它跑完再按「繼續」"); return; }
                say("✓ Termux 基本環境好了");
            }
            // 補回權限（允許外部 App 呼叫 Termux 等）
            step(3);
            say("→ 補回 App 權限與 Termux 設定…");
            say(Maintenance.repairPermissions());
            if (!done[3]) {
                step(4);
                say("→ 安裝套件（pkg update／upgrade／install，約 3～10 分鐘，輸出只顯示最後幾行）…");
                final int[] n = {0};
                int code = Maintenance.suStream(Maintenance.termuxScript(
                        // </dev/null：不讓套件管理員等待鍵盤輸入（平板上最容易卡在這裡）；timeout：單一步最多 15 分鐘
                        "timeout 900 pkg update -y </dev/null 2>&1; timeout 900 pkg upgrade -y -o Dpkg::Options::=--force-confdef -o Dpkg::Options::=--force-confold </dev/null 2>&1; "
                                + "timeout 900 pkg install -y git python imagemagick wget unzip termux-api </dev/null 2>&1"),
                        l -> { if (++n[0] % 8 == 0 || l.startsWith("E:")) say(l.length() > 90 ? l.substring(0, 90) : l); });
                String chk = Maintenance.suRun("[ -x " + P + "python ] && [ -x " + P + "git ] && [ -x " + P + "termux-notification ] && echo ok");
                if (!chk.contains("ok")) { fail("套件沒有裝好（結束碼 " + code + "）：看上面最後幾行；網路不穩的話再按一次「繼續」"); return; }
                say("✓ 套件完成");
            }
            if (!done[4]) {
                step(5);
                say("→ 下載專案腳本…");
                if (!installScripts()) return;
            }
            if (!done[5]) {
                step(6);
                String key = apiKey != null ? apiKey : readPendingKey();
                apiKey = null;
                if (key != null && !key.isEmpty()) {
                    String err = Maintenance.saveKey(key);
                    if (err != null) { say("✗ " + err + "（可以之後在「功能 → 維護 → Claude API Key」重設）"); }
                    else {
                        say("✓ API Key 已存好");
                        pendingKeyFile().delete();
                    }
                } else {
                    pendingKeyFile().delete();
                    say("（API Key 先沒設定；之後在「功能 → 維護 → Claude API Key」設）");
                }
            }
            say("✓ 全部完成。回上一頁，到「功能 → 維護 → 自我檢查」確認；然後就可以長按電源鍵或從首頁問助理了。");
        } catch (Throwable e) {
            say("✗ 出錯：" + e);
        }
        busy = false;
        runOnUiThread(this::check);
    }

    // ---------- 下載與安裝 ----------

    /** 從 GitHub 最新 release 挑符合的 .apk、下載、用 root 安裝。 */
    private boolean installRelease(String repo, String must, String label) throws Exception {
        String json = httpString("https://api.github.com/repos/" + repo + "/releases/latest", null);
        JSONArray assets = new JSONObject(json).getJSONArray("assets");
        String url = null, name = null;
        for (int i = 0; i < assets.length(); i++) {
            String nm = assets.getJSONObject(i).getString("name");
            if (nm.endsWith(".apk") && nm.contains(must)) { url = assets.getJSONObject(i).getString("browser_download_url"); name = nm; break; }
        }
        if (url == null) { fail("找不到 " + repo + " 的 " + must + " 安裝檔（它的發布格式可能變了）"); return false; }
        say("  檔案：" + name);
        String pkg = label.equals("termux") ? "com.termux" : "com.termux.api";
        File apk = new File(getCacheDir(), label + ".apk");
        download(url, apk, null);
        say("  安裝中…");
        String r = installApk(apk, label);
        if (!r.contains("Success") && (r.contains("UPDATE_INCOMPATIBLE") || r.contains("SIGNATURE"))) {
            // 舊的（Google Play 版等）簽章不同：問一次，同意後用 root 解除安裝舊版再裝新版
            final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            final boolean[] yes = {false};
            runOnUiThread(() -> new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("要替換成 GitHub 版嗎？")
                    .setMessage("手機上的 " + pkg + " 是別的來源的版本（簽章不同），不能直接覆蓋。\n\n"
                            + "同意的話，會先解除安裝舊版，再安裝 GitHub 版。舊版裡的 Termux 資料（例如 ~/memory.json 的記憶）會一起刪除；"
                            + "記憶會先備份到 /sdcard/Download/phoneai_memory_backup.json。")
                    .setPositiveButton("解除安裝並重裝", (d, w) -> { yes[0] = true; latch.countDown(); })
                    .setNegativeButton("取消", (d, w) -> latch.countDown())
                    .setOnCancelListener(d -> latch.countDown())
                    .show());
            latch.await();
            if (!yes[0]) { apk.delete(); fail("已取消：沒有替換。之後可以再按「繼續」。"); return false; }
            say("  備份記憶並解除安裝舊版…");
            Maintenance.suRun("cp " + Maintenance.HOME + "/memory.json /sdcard/Download/phoneai_memory_backup.json 2>/dev/null; true");
            Maintenance.suRun("pm uninstall --user 0 " + pkg + " 2>&1; true");
            say("  重新安裝 GitHub 版…");
            r = installApk(apk, label);
        }
        apk.delete();
        if (!r.contains("Success")) {
            fail("安裝失敗：" + r.trim());
            return false;
        }
        say("✓ " + pkg + " 裝好了");
        return true;
    }

    /** 用 root 安裝 APK，回傳 pm 的輸出。 */
    private String installApk(File apk, String label) {
        return Maintenance.suRun("cp '" + apk.getAbsolutePath() + "' /data/local/tmp/" + label + ".apk && chmod 644 /data/local/tmp/" + label + ".apk\n"
                + "pm install -r --user 0 /data/local/tmp/" + label + ".apk 2>&1\nrm -f /data/local/tmp/" + label + ".apk");
    }

    private boolean installScripts() {
        String err = Project.install(this, this::say);
        if (err != null) { fail(err); return false; }
        say("✓ 專案腳本好了");
        return true;
    }

    // ---------- 網路 ----------

    private String httpString(String url, String token) throws Exception {
        File f = new File(getCacheDir(), "http.tmp");
        download(url, f, token);
        byte[] b = java.nio.file.Files.readAllBytes(f.toPath());
        f.delete();
        return new String(b, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** 下載（手動跟著轉址；權杖只送給第一個網址 api.github.com，轉址後不帶）。 */
    private void download(String url, File dest, String token) throws Exception {
        Project.download(url, dest, token, this::say);
    }


}
