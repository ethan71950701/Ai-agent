package com.phoneai.overlay;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 設定 → 互傳檔案：開關、這台的名稱與狀態、配對、已配對的裝置、最近幾筆記錄。
 * 實際傳檔案是對助理說（見 FileTools）。設計見 docs/FILE_TRANSFER.md。
 */
public class TransferActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private LinearLayout box;
    private String shownPending = "";
    private boolean visible, lastRunning;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!visible) return;
            if (TransferStore.enabled(TransferActivity.this) && TransferService.running) TransferService.keepPairing();
            if (TransferService.running != lastRunning) { lastRunning = TransferService.running; render(); }
            TransferService.Pending p = TransferService.pending;
            String key = p == null ? "" : p.peerId + p.state;
            if (!key.equals(shownPending)) {
                shownPending = key;
                if (p != null && "waiting".equals(p.state)) askMatch(p);
                else render();
            }
            ui.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout[] c = new LinearLayout[1];
        setContentView(Ui.screen(this, L.t("互傳檔案"), true, c));
        Ui.enterSpring(this);
        box = c[0];
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
        if (TransferStore.enabled(this) && !TransferService.running) TransferService.start(this);
        render();
        ui.postDelayed(tick, 1000);
    }

    @Override
    protected void onPause() {
        visible = false;
        TransferService.closePairing();
        ui.removeCallbacks(tick);
        super.onPause();
    }

    private void render() {
        box.removeAllViews();
        LinearLayout sw = Ui.card(this, box);
        sw.addView(Ui.toggle(this, L.t("開啟互傳檔案"), TransferStore.enabled(this), (v, on) -> {
            if (on == TransferStore.enabled(this)) return;
            TransferStore.setEnabled(this, on);
            render();
        }));
        if (TransferStore.enabled(this) && !TransferStore.hasAllFiles()) {
            sw.addView(Ui.body(this, L.t("需要「所有檔案存取」權限，才能找到並讀寫檔案。")));
            sw.addView(Ui.button(this, L.t("前往授權"), v -> {
                try {
                    startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName())));
                } catch (Exception e) {
                    startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                }
            }));
        }
        if (!TransferStore.enabled(this)) return;

        LinearLayout me = Ui.card(this, box);
        String state = TransferService.running ? L.t("運作中") : L.t("沒有在運作");
        me.addView(Ui.body(this, TransferStore.myName(this) + "　·　" + state));
        me.addView(Ui.body(this, L.t("位址：") + localIps()
                + (TransferService.lastError.isEmpty() ? "" : "\n" + L.t("原因：") + TransferService.lastError)));
        me.addView(Ui.tonal(this, L.t("改名稱"), v -> rename()));

        LinearLayout pair = Ui.card(this, box);
        pair.addView(Ui.body(this, L.t("兩台都打開這一頁，在任一台按下面的按鈕，選對方即可配對。")));
        pair.addView(Ui.button(this, L.t("配對另一台"), v -> scanAndPair()));

        List<TransferStore.Peer> peers = TransferStore.peers(this);
        if (!peers.isEmpty()) {
            Ui.section(this, box, L.t("已配對的裝置"));
            LinearLayout list = Ui.card(this, box);
            for (TransferStore.Peer p : peers) {
                list.addView(Ui.tonal(this, p.name, v -> removeDialog(p)));
            }
        }

        List<String> recent = TransferStore.recentLog(this, 5);
        if (!recent.isEmpty()) {
            Ui.section(this, box, L.t("最近"));
            TextView log = Ui.infoCard(this, box, 12, false, false);
            log.setText(String.join("\n", recent));
        }
    }

    private void removeDialog(TransferStore.Peer p) {
        tint(new AlertDialog.Builder(this).setMessage(L.t("移除「") + p.name + L.t("」？移除後不能再取檔案，要重新配對。"))
                .setPositiveButton(L.t("移除"), (d, w) -> { TransferStore.removePeer(this, p.id); render(); })
                .setNegativeButton(L.t("取消"), null)).show();
    }

    private void rename() {
        final EditText e = new EditText(this);
        e.setText(TransferStore.myName(this));
        tint(new AlertDialog.Builder(this).setTitle(L.t("這台裝置的名稱")).setView(e)
                .setPositiveButton(L.t("儲存"), (d, w) -> { TransferStore.setMyName(this, e.getText().toString()); render(); })
                .setNegativeButton(L.t("取消"), null)).show();
    }

    /** 對話框的按鈕用主題色（預設是系統的紫色）。 */
    private AlertDialog tint(AlertDialog.Builder b) {
        AlertDialog d = b.create();
        d.setOnShowListener(x -> {
            int col = Ui.attr(this, com.google.android.material.R.attr.colorPrimary);
            d.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(col);
            d.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(col);
        });
        return d;
    }

    // ---------- 我是被配對的一方：核對確認碼 ----------

    private void askMatch(TransferService.Pending p) {
        tint(new AlertDialog.Builder(this).setCancelable(false)
                .setTitle(L.t("確認碼：") + p.sas)
                .setMessage(L.t("「") + p.name + L.t("」想配對。對方的畫面上是同一組數字嗎？"))
                .setPositiveButton(L.t("相符"), (d, w) -> { TransferService.acceptPending(this); shownPending = ""; render(); })
                .setNegativeButton(L.t("不相符"), (d, w) -> { TransferService.rejectPending(); shownPending = ""; render(); })
        ).show();
    }

    // ---------- 我是發起配對的一方 ----------

    private void scanAndPair() {
        Toast.makeText(this, L.t("搜尋裝置中…"), Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            Map<String, TransferDiscovery.Found> found = TransferDiscovery.scan("*", 2500, TransferDiscovery.PORT, null);
            found.remove(TransferStore.myId(this));
            if (found.isEmpty()) found.putAll(TransferDiscovery.sweep(TransferServer.PORT));   // 廣播被擋時，改逐一試同網段位址
            List<TransferDiscovery.Found> l = new ArrayList<>(found.values());
            ui.post(() -> chooseDevice(l));
        }).start();
    }

    private void chooseDevice(List<TransferDiscovery.Found> l) {
        final String[] items = new String[l.size() + 1];
        for (int i = 0; i < l.size(); i++) items[i] = l.get(i).name + "（" + l.get(i).ip + "）";
        items[l.size()] = L.t("找不到？手動輸入 IP");
        tint(new AlertDialog.Builder(this).setTitle(l.isEmpty() ? L.t("沒有找到裝置（對方也要開啟互傳檔案並停在這一頁）") : L.t("選擇要配對的裝置"))
                .setItems(items, (d, i) -> {
                    if (i < l.size()) startPair(l.get(i).ip, l.get(i).port);
                    else askIp();
                }).setNegativeButton(L.t("取消"), null)).show();
    }

    private void askIp() {
        final EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_PHONE);
        e.setHint("192.168.1.23");
        tint(new AlertDialog.Builder(this).setTitle(L.t("對方的 IP")).setView(e)
                .setPositiveButton(L.t("配對"), (d, w) -> startPair(e.getText().toString().trim(), TransferServer.PORT))
                .setNegativeButton(L.t("取消"), null)).show();
    }

    private void startPair(final String ip, final int port) {
        new Thread(() -> {
            try {
                final TransferClient.Offer o = TransferClient.pairStart(ip, port, TransferStore.myId(this), TransferStore.myName(this));
                ui.post(() -> tint(new AlertDialog.Builder(this).setCancelable(false)
                        .setTitle(L.t("確認碼：") + o.sas)
                        .setMessage(L.t("對方（") + o.peerName + L.t("）的畫面上是同一組數字嗎？相符再按「相符」，然後請對方也按「相符」。"))
                        .setPositiveButton(L.t("相符"), (d, w) -> waitAccepted(ip, port, o))
                        .setNegativeButton(L.t("不相符"), null)).show());
            } catch (Exception e) {
                ui.post(() -> Toast.makeText(this, L.t("配對失敗：") + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void waitAccepted(final String ip, final int port, final TransferClient.Offer o) {
        Toast.makeText(this, L.t("等對方按「相符」…"), Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            long end = System.currentTimeMillis() + 120000;
            String st = "waiting";
            try {
                while (System.currentTimeMillis() < end && st.equals("waiting")) {
                    Thread.sleep(1000);
                    st = TransferClient.pairStatus(ip, port, TransferStore.myId(this));
                }
            } catch (Exception ignored) { }
            final String result = st;
            ui.post(() -> {
                if (result.equals("accepted")) {
                    TransferStore.Peer p = new TransferStore.Peer();
                    p.id = o.peerId;
                    p.name = o.peerName;
                    p.ip = ip;
                    p.port = port;
                    p.key = o.key;
                    TransferStore.addPeer(this, p);
                    TransferStore.log(this, L.t("已配對 ") + p.name);
                    Toast.makeText(this, L.t("配對完成：") + p.name, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, L.t("對方沒有接受或逾時，沒有配對"), Toast.LENGTH_LONG).show();
                }
                render();
            });
        }).start();
    }

    private static String localIps() {
        StringBuilder sb = new StringBuilder();
        try {
            for (java.net.NetworkInterface ni : java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (java.net.InetAddress a : java.util.Collections.list(ni.getInetAddresses()))
                    if (a instanceof java.net.Inet4Address) sb.append(sb.length() > 0 ? ", " : "").append(a.getHostAddress());
            }
        } catch (Exception ignored) { }
        return sb.length() == 0 ? "?" : sb.toString();
    }
}
