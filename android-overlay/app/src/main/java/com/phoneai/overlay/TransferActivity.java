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
 * 設定 → 傳檔案：總開關、這台裝置的名稱、配對（兩台裝置各顯示同一組確認碼，使用者核對相符才存）、
 * 已配對的裝置、最近的傳輸記錄。實際傳檔案是對助理說（見 FileTools）。設計見 docs/FILE_TRANSFER.md。
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
            if (TransferService.running != lastRunning) { lastRunning = TransferService.running; render(); }
            if (TransferStore.enabled(TransferActivity.this) && TransferService.running) TransferService.keepPairing();
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
        setContentView(Ui.screen(this, L.t("傳檔案"), true, c));
        Ui.enterSpring(this);
        box = c[0];
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
        if (TransferStore.enabled(this) && !TransferService.running) TransferService.start(this);   // 開關是開的但服務沒在跑（例如 App 被系統結束過）
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
        TextView intro = Ui.infoCard(this, box, 14, false, false);
        intro.setText(L.t("在自己的裝置之間傳檔案：配對一次之後，對助理說「把 X 傳到我的手機」或「從〇〇手機拿 X」就會自動傳。只在同一個 Wi-Fi 內運作，內容加密。"));

        LinearLayout card = Ui.card(this, box);
        card.addView(Ui.toggle(this, L.t("開啟傳檔案"), TransferStore.enabled(this), (v, on) -> {
            if (on == TransferStore.enabled(this)) return;
            TransferStore.setEnabled(this, on);
            render();
        }));
        if (TransferStore.enabled(this) && !TransferStore.hasAllFiles()) {
            TextView t = Ui.body(this, L.t("需要「所有檔案存取」權限，才能找到並讀寫你的檔案。"));
            card.addView(t);
            card.addView(Ui.button(this, L.t("前往授權"), v -> {
                try {
                    startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName())));
                } catch (Exception e) {
                    startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                }
            }));
        }
        if (!TransferStore.enabled(this)) return;

        Ui.section(this, box, L.t("這台裝置"));
        LinearLayout me = Ui.card(this, box);
        me.addView(Ui.body(this, L.t("名稱：") + TransferStore.myName(this) + "\n" + L.t("收到的檔案會放在：下載/PhoneAI")));
        me.addView(Ui.body(this, (TransferService.running ? L.t("狀態：運作中") : L.t("狀態：沒有在運作") + (TransferService.lastError.isEmpty() ? "" : "\n" + L.t("原因：") + TransferService.lastError)) + "\n" + L.t("本機位址：") + localIps()));
        me.addView(Ui.button(this, L.t("改名稱"), v -> rename()));

        Ui.section(this, box, L.t("配對"));
        LinearLayout pair = Ui.card(this, box);
        pair.addView(Ui.body(this, L.t("兩台裝置都開啟傳檔案並停在這一頁，任何一台按「配對另一台」選對方就好。兩邊會顯示同一組 6 位數確認碼，相符才按「相符」。")));
        pair.addView(Ui.pill(this, L.t("配對另一台"), true, v -> scanAndPair()));

        Ui.section(this, box, L.t("已配對的裝置"));
        List<TransferStore.Peer> peers = TransferStore.peers(this);
        LinearLayout list = Ui.card(this, box);
        if (peers.isEmpty()) list.addView(Ui.body(this, L.t("還沒有。")));
        for (TransferStore.Peer p : peers) {
            list.addView(Ui.body(this, p.name));
            list.addView(Ui.button(this, L.t("移除 ") + p.name, v -> tint(new AlertDialog.Builder(this)
                    .setMessage(L.t("移除後這台裝置就不能再取檔案；要再用得重新配對。"))
                    .setPositiveButton(L.t("移除"), (d, w) -> { TransferStore.removePeer(this, p.id); render(); })
                    .setNegativeButton(L.t("取消"), null)).show()));
        }

        Ui.section(this, box, L.t("最近的記錄"));
        TextView log = Ui.infoCard(this, box, 12, true, true);
        List<String> recent = TransferStore.recentLog(this, 15);
        log.setText(recent.isEmpty() ? L.t("還沒有傳輸。") : String.join("\n", recent));
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
            if (found.isEmpty()) found.putAll(TransferDiscovery.sweep(TransferServer.PORT));   // 廣播被路由器或系統擋掉時，改逐一試同網段位址
            found.remove(TransferStore.myId(this));
            List<TransferDiscovery.Found> l = new ArrayList<>(found.values());
            ui.post(() -> chooseDevice(l));
        }).start();
    }

    private void chooseDevice(List<TransferDiscovery.Found> l) {
        final String[] items = new String[l.size() + 1];
        for (int i = 0; i < l.size(); i++) items[i] = l.get(i).name + "（" + l.get(i).ip + "）";
        items[l.size()] = L.t("找不到？手動輸入 IP");
        tint(new AlertDialog.Builder(this).setTitle(l.isEmpty() ? L.t("沒有找到裝置（對方也要開啟傳檔案並停在這一頁）") : L.t("選擇要配對的裝置"))
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
