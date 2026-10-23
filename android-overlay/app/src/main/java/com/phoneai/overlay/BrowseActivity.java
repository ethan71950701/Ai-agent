package com.phoneai.overlay;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

/** 直接瀏覽已配對裝置（電腦或另一台手機）的資料夾：點資料夾進去，點檔案就接收到這支手機。 */
public class BrowseActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private TransferStore.Peer peer;
    private TransferClient client;
    private String path = "";
    private boolean loading;   // 讀取中不接受重複點擊，避免同時發好幾個請求而慢、亂序
    private TextView where, status;
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String pid = getIntent().getStringExtra("peer_id");
        for (TransferStore.Peer p : TransferStore.peers(this)) if (p.id.equals(pid)) peer = p;
        if (peer == null) {
            finish();
            return;
        }
        LinearLayout[] c = new LinearLayout[1];
        setContentView(Ui.screen(this, peer.name, true, c));
        Ui.enterSpring(this);
        LinearLayout box = c[0];

        LinearLayout top = Ui.card(this, box);
        where = Ui.body(this, "");
        top.addView(where);
        Ui.buttons(this, top, Ui.tonal(this, L.t("上一層"), v -> load(parentOf(path))),
                Ui.tonal(this, L.t("搜尋這台"), v -> startActivity(new Intent(this, FileSearchActivity.class).putExtra("peer_id", peer.id))));
        status = Ui.body(this, "");
        box.addView(status);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        box.addView(list);
        load("");
    }

    /** 上一層：「C:/Users」→「C:/」→「」（磁碟列表）；手機的「Download/PhoneAI」→「Download」→「」。 */
    static String parentOf(String p) {
        if (p == null || p.isEmpty()) return "";
        String s = p.endsWith("/") ? p.substring(0, p.length() - 1) : p;
        int i = s.lastIndexOf('/');
        if (i < 0) return "";
        String par = s.substring(0, i);
        if (par.isEmpty()) return "/";
        if (par.length() == 2 && par.charAt(1) == ':') return par + "/";
        return par;
    }

    private void load(String target) {
        if (loading) return;
        loading = true;
        status.setText(L.t("讀取中…"));
        final long t0 = System.currentTimeMillis();
        new Thread(() -> {
            try {
                if (client == null) client = FileTools.clientFor(this, peer);
                long t1 = System.currentTimeMillis();
                List<String[]> rows = FileTools.rows(client.list(target));
                long ms = System.currentTimeMillis() - t0;
                String timing = L.t("（連線 ") + (t1 - t0) + L.t(" 毫秒，讀取 ") + (System.currentTimeMillis() - t1) + L.t(" 毫秒）");
                ui.post(() -> { loading = false; show(target, rows); status.setText(status.getText() + " " + timing); });
            } catch (Exception e) {
                ui.post(() -> { loading = false; status.setText(L.t("讀取失敗：") + e.getMessage()); });
            }
        }).start();
    }

    private void show(String target, List<String[]> rows) {
        path = target;
        where.setText(L.t("位置：") + (target.isEmpty() ? L.t("（最上層）") : target));
        status.setText(rows.isEmpty() ? L.t("這個資料夾是空的") : "");
        list.removeAllViews();
        for (String[] r : rows) {
            final boolean folder = "D".equals(r[0]);
            String trimmed = r[2].endsWith("/") ? r[2].substring(0, r[2].length() - 1) : r[2];   // 磁碟根目錄「C:/」要去掉最後的 /
            String name = trimmed.contains("/") ? trimmed.substring(trimmed.lastIndexOf('/') + 1) : trimmed;
            LinearLayout card = Ui.card(this, list);
            TextView t = Ui.body(this, (folder ? L.t("📁 ") : L.t("📄 ")) + name);
            t.setTextSize(16);
            t.setPadding(0, 0, 0, 0);
            card.addView(t);
            if (!folder) {
                long size = 0;
                try { size = Long.parseLong(r[1]); } catch (NumberFormatException ignored) { }
                card.addView(Ui.body(this, TransferStore.size(size)));
            }
            card.setOnClickListener(v -> {
                if (folder) load(r[2]);
                else confirmReceive(r[2], name);
            });
        }
    }

    private void confirmReceive(String remote, String name) {
        new MaterialAlertDialogBuilder(this).setMessage(L.t("接收「") + name + L.t("」到這支手機的「下載/PhoneAI」？"))
                .setPositiveButton(L.t("接收"), (d, w) -> new Thread(() -> {
                    try {
                        String saved = FileTools.fetch(this, peer, remote);
                        if (saved != null) ui.post(() -> Toast.makeText(this, L.t("已接收：") + saved, Toast.LENGTH_LONG).show());
                    } catch (Exception e) {
                        ui.post(() -> Toast.makeText(this, L.t("接收失敗：") + e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                }).start())
                .setNegativeButton(L.t("取消"), null).show();
    }
}
