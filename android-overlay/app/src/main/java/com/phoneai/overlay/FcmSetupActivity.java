package com.phoneai.overlay;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.List;

/**
 * 設定 → 互傳檔案 → 推播喚醒設定：三步驟（匯入 → 配對 → 檢查），每步有狀態。
 * 讓裝置沒開互傳時也能被叫醒（原理與驗證見 docs/FCM_TRANSFER_DESIGN.md）。
 */
public class FcmSetupActivity extends Activity {
    private static final int REQ_FILES = 11;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private LinearLayout box;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout[] c = new LinearLayout[1];
        setContentView(Ui.screen(this, L.t("推播喚醒設定"), true, c));
        Ui.enterSpring(this);
        box = c[0];
    }

    @Override protected void onResume() {
        super.onResume();
        FcmLab.refreshMyToken(this);
        render();
        ui.postDelayed(this::render, 2500);   // token 是非同步取得的：稍後再畫一次
    }

    private void render() {
        box.removeAllViews();
        FcmHealth.Input in = inputs();
        List<FcmHealth.Item> items = FcmHealth.evaluate(in);

        Ui.section(this, box, L.t("1. 匯入檔案"));
        LinearLayout a = Ui.card(this, box);
        a.addView(Ui.body(this, L.t("到 Firebase 下載 google-services.json 和服務帳戶金鑰，一次選兩個檔案（App 會自己判斷哪個是哪個，匯入後金鑰加密存放）。已經有一台設好了的話，改請那台傳給你就好。")));
        a.addView(Ui.button(this, L.t("選檔案（可一次選兩個）"), v -> {
            Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(it, REQ_FILES);
        }));
        a.addView(Ui.body(this, L.t("設定檔：") + (in.hasConfig ? L.t("已匯入") : L.t("還沒有")) + "\n" + L.t("發送金鑰：") + (in.hasKey ? L.t("已加密存放") : L.t("還沒有"))));
        if (FcmLab.canShareSetup(this)) {
            a.addView(Ui.tonal(this, L.t("把這個設定傳給另一台已配對裝置"), v -> pickPeer(p -> share(p))));
        }

        Ui.section(this, box, L.t("2. 配對與 token"));
        LinearLayout b2 = Ui.card(this, box);
        b2.addView(Ui.body(this, L.t("配對請到「互傳檔案」頁面。配對成功時會自動互換推播 token；如果之前配對過、或有一邊當時還沒設定，可以在這裡補互換（兩邊要連得上）。")));
        b2.addView(Ui.tonal(this, L.t("和某台已配對裝置互換 token"), v -> pickPeer(p -> exchange(p))));

        Ui.section(this, box, L.t("3. 檢查"));
        LinearLayout c3 = Ui.card(this, box);
        for (FcmHealth.Item it : items) {
            String mark = it.level == FcmHealth.Level.OK ? "✅ " : it.level == FcmHealth.Level.BAD ? "❌ " : it.level == FcmHealth.Level.WARN ? "⚠️ " : "ℹ️ ";
            String line = mark + L.t(it.label) + (it.hint.isEmpty() ? "" : "\n    " + L.t(it.hint));
            c3.addView(Ui.body(this, line));
        }
        c3.addView(Ui.button(this, L.t("測試叫醒對方"), v -> pickPeer(p -> testWake(p))));
        Ui.buttons(this, c3,
                Ui.tonal(this, L.t("電池設定"), v -> openBattery()),
                Ui.tonal(this, L.t("這個 App 的設定"), v -> openAppSettings()));

        Ui.section(this, box, L.t("進階"));
        box.addView(Ui.tonal(this, L.t("實驗畫面（舊的測試工具）"), v -> startActivity(new Intent(this, FcmLabActivity.class))));
    }

    private FcmHealth.Input inputs() {
        FcmHealth.Input in = new FcmHealth.Input();
        in.hasConfig = FcmLab.hasConfig(this);
        in.hasKey = FcmVault.has(this);
        in.initError = in.hasConfig ? FcmLab.init(this) : null;
        in.myToken = FcmLab.myToken(this);
        in.lanIp = FcmLab.lanIp();
        in.notificationsOn = getSystemService(android.app.NotificationManager.class).areNotificationsEnabled();
        in.batteryUnrestricted = getSystemService(android.os.PowerManager.class).isIgnoringBatteryOptimizations(getPackageName());
        List<TransferStore.Peer> ps = TransferStore.peers(this);
        in.paired = ps.size();
        StringBuilder miss = new StringBuilder();
        for (TransferStore.Peer p : ps) {
            if (p.fcmToken != null) in.pairedWithToken++;
            else miss.append(miss.length() == 0 ? "" : "、").append(p.name);
        }
        in.missingNames = miss.toString();
        in.manufacturer = Build.MANUFACTURER;
        return in;
    }

    @Override protected void onActivityResult(int req, int res, Intent d) {
        super.onActivityResult(req, res, d);
        if (req != REQ_FILES || res != RESULT_OK || d == null) return;
        java.util.List<Uri> uris = new java.util.ArrayList<>();
        if (d.getData() != null) uris.add(d.getData());
        ClipData cd = d.getClipData();
        if (cd != null) for (int i = 0; i < cd.getItemCount(); i++) uris.add(cd.getItemAt(i).getUri());
        new Thread(() -> {
            StringBuilder sb = new StringBuilder();
            // 設定檔先存，金鑰後存：金鑰存的時候才能比對專案是不是同一個
            java.util.List<String> jsons = new java.util.ArrayList<>();
            for (Uri u : uris) {
                try (java.io.InputStream in = getContentResolver().openInputStream(u)) {
                    java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int n, total = 0;
                    while (in != null && (n = in.read(buf)) > 0) { total += n; if (total > 64 * 1024) break; bo.write(buf, 0, n); }
                    jsons.add(bo.toString("UTF-8"));
                } catch (Exception e) {
                    sb.append("✗ ").append(e.getMessage()).append('\n');
                }
            }
            jsons.sort((x, y) -> Integer.compare(FcmFiles.classify(x) == FcmFiles.Kind.CONFIG ? 0 : 1, FcmFiles.classify(y) == FcmFiles.Kind.CONFIG ? 0 : 1));
            for (String j : jsons) {
                FcmFiles.Kind k = FcmFiles.classify(j);
                String err = FcmLab.storeAuto(this, j);
                String what = k == FcmFiles.Kind.CONFIG ? L.t("設定檔") : k == FcmFiles.Kind.SERVICE_ACCOUNT ? L.t("發送金鑰") : L.t("檔案");
                sb.append(err == null ? "✓ " : "✗ ").append(what).append(err == null ? "" : "：" + err).append('\n');
            }
            FcmLab.init(this);
            FcmLab.refreshMyToken(this);
            final String msg = sb.toString().trim();
            ui.post(() -> { Toast.makeText(this, msg.isEmpty() ? L.t("沒有選到檔案") : msg + "\n" + L.t("匯入後請把下載資料夾裡的原檔刪掉"), Toast.LENGTH_LONG).show(); render(); });
        }).start();
    }

    private void pickPeer(java.util.function.Consumer<TransferStore.Peer> then) {
        final List<TransferStore.Peer> ps = TransferStore.peers(this);
        if (ps.isEmpty()) { Toast.makeText(this, L.t("還沒有配對過的裝置（先到互傳檔案頁面配對）"), Toast.LENGTH_LONG).show(); return; }
        String[] names = new String[ps.size()];
        for (int i = 0; i < names.length; i++) {
            TransferStore.Peer q = ps.get(i);
            String where = q.ip != null ? q.ip : (q.tsIp != null ? q.tsIp : L.t("位址未知"));
            names[i] = q.name + "\n" + where + "｜" + (q.fcmToken == null ? L.t("沒存 token") : L.t("已存 token"));
        }
        new android.app.AlertDialog.Builder(this).setTitle(L.t("選裝置")).setItems(names, (d, w) -> then.accept(ps.get(w))).show();
    }

    private void share(TransferStore.Peer p) {
        Toast.makeText(this, L.t("傳送中…請對方在手機上按「接受」"), Toast.LENGTH_LONG).show();
        new Thread(() -> {
            String err = FcmLab.shareSetup(this, p);
            ui.post(() -> { Toast.makeText(this, err == null ? L.t("對方已接受，設定完成") : L.t("沒有完成：") + err, Toast.LENGTH_LONG).show(); render(); });
        }).start();
    }

    private void exchange(TransferStore.Peer p) {
        new Thread(() -> {
            String msg;
            try {
                String theirs = FileTools.clientFor(this, p).exchangeFcmToken(FcmLab.myToken(this));
                if (theirs != null) TransferStore.setFcmToken(this, p.id, theirs);
                msg = theirs != null ? L.t("已互換") : L.t("對方還沒有 token（它要先匯入設定檔）");
            } catch (Exception e) {
                msg = L.t("沒成功：") + e.getMessage();
            }
            final String m = msg;
            ui.post(() -> { Toast.makeText(this, m, Toast.LENGTH_LONG).show(); render(); });
        }).start();
    }

    /** 用叫醒的方式去連對方，量花了多久；對方本來就在線上會直接找到。 */
    private void testWake(TransferStore.Peer p) {
        Toast.makeText(this, L.t("測試中…（最久約 20 秒）"), Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            long t0 = System.currentTimeMillis();
            String msg;
            try {
                FileTools.clientFor(this, p).ping();
                msg = L.t("連上了，花了 ") + (System.currentTimeMillis() - t0) / 1000.0 + L.t(" 秒（如果對方本來就開著，就不是叫醒的成績，請讓它休眠後再測）");
            } catch (Exception e) {
                msg = L.t("連不上：") + e.getMessage();
            }
            final String m = msg;
            ui.post(() -> Toast.makeText(this, m, Toast.LENGTH_LONG).show());
        }).start();
    }

    private void openBattery() {
        try { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); }
        catch (Exception e) { openAppSettings(); }
    }

    private void openAppSettings() {
        // 小米的「自啟動」頁面有固定的入口，能開就直接開；不行就開這個 App 的詳細資料頁，使用者再往下找
        String m = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase(java.util.Locale.ROOT);
        if (m.contains("xiaomi") || m.contains("redmi") || m.contains("poco")) {
            try {
                startActivity(new Intent().setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"));
                return;
            } catch (Exception ignored) { }
        }
        try { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))); }
        catch (Exception e) { Toast.makeText(this, L.t("打不開設定頁，請到系統設定手動找"), Toast.LENGTH_LONG).show(); }
    }
}
