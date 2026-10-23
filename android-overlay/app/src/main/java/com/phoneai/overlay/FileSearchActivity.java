package com.phoneai.overlay;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.util.List;

/**
 * 手動找檔案（不用 AI）：輸入檔名，搜手機本機或某台已配對的裝置，點結果就傳過去或取回來。
 * 從「互傳檔案」頁的「找檔案」進入。
 */
public class FileSearchActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextInputEditText query;
    private LinearLayout results;
    private TextView status;
    private boolean searching;
    private TransferStore.Peer preset;   // 從某台裝置的「找檔案」進來：預設就搜那台

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout[] c = new LinearLayout[1];
        setContentView(Ui.screen(this, L.t("找檔案"), true, c));
        Ui.enterSpring(this);
        LinearLayout box = c[0];

        LinearLayout card = Ui.card(this, box);
        TextInputLayout til = new TextInputLayout(this);
        til.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        til.setHint(L.t("檔名關鍵字"));
        query = new TextInputEditText(til.getContext());
        query.setSingleLine(true);
        query.setOnEditorActionListener((v, id, e) -> { searchLocal(); return true; });
        til.addView(query);
        card.addView(til);
        String pid = getIntent().getStringExtra("peer_id");
        if (pid != null) {
            for (TransferStore.Peer p : TransferStore.peers(this)) if (p.id.equals(pid)) preset = p;
        }
        if (preset != null) {
            Ui.buttons(this, card, Ui.button(this, L.t("在 ") + preset.name + L.t(" 裡找"), v -> searchPreset()),
                    Ui.tonal(this, L.t("搜手機"), v -> searchLocal()));
        } else {
            Ui.buttons(this, card, Ui.button(this, L.t("搜手機"), v -> searchLocal()),
                    Ui.tonal(this, L.t("搜某台裝置"), v -> pickDeviceToSearch()));
        }

        status = Ui.body(this, "");
        box.addView(status);
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        box.addView(results);
    }

    private String q() {
        return query.getText() == null ? "" : query.getText().toString().trim();
    }

    private void searchPreset() {
        final String q = q();
        if (q.isEmpty()) {
            Toast.makeText(this, L.t("先輸入檔名關鍵字"), Toast.LENGTH_SHORT).show();
            return;
        }
        final TransferStore.Peer p = preset;
        run(L.t("搜尋 ") + p.name + L.t(" 中…"), () -> FileTools.searchPeer(this, p, q), p);
    }

    private void searchLocal() {
        final String q = q();
        if (q.isEmpty() || searching) return;
        run(L.t("搜尋手機中…"), () -> FileTools.searchLocal(q), null);
    }

    private void pickDeviceToSearch() {
        final String q = q();
        if (q.isEmpty()) {
            Toast.makeText(this, L.t("先輸入檔名關鍵字"), Toast.LENGTH_SHORT).show();
            return;
        }
        List<TransferStore.Peer> peers = TransferStore.peers(this);
        if (peers.isEmpty()) {
            Toast.makeText(this, L.t("還沒有配對過的裝置"), Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[peers.size()];
        for (int i = 0; i < names.length; i++) names[i] = peers.get(i).name;
        new MaterialAlertDialogBuilder(this).setTitle(L.t("要搜哪一台")).setItems(names, (d, i) -> {
            final TransferStore.Peer p = peers.get(i);
            run(L.t("搜尋 ") + p.name + L.t(" 中…"), () -> FileTools.searchPeer(this, p, q), p);
        }).setNegativeButton(L.t("取消"), null).show();
    }

    private interface Search { List<String[]> run() throws Exception; }

    /** 搜尋放在背景執行緒；peer 為 null 表示搜本機。 */
    private void run(String msg, Search s, TransferStore.Peer peer) {
        searching = true;
        status.setText(msg);
        results.removeAllViews();
        new Thread(() -> {
            try {
                List<String[]> rows = s.run();
                ui.post(() -> show(rows, peer));
            } catch (Exception e) {
                ui.post(() -> {
                    searching = false;
                    status.setText(L.t("搜尋失敗：") + e.getMessage());
                });
            }
        }).start();
    }

    private android.graphics.drawable.Drawable rippleBackground() {
        android.util.TypedValue tv = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        return getResources().getDrawable(tv.resourceId, getTheme());
    }

    private void show(List<String[]> rows, TransferStore.Peer peer) {
        searching = false;
        status.setText(rows.isEmpty() ? L.t("沒有找到") : L.t("找到 ") + rows.size() + L.t(" 筆（點一下操作）"));
        for (String[] r : rows) {
            final boolean isFile = "F".equals(r[0]);
            LinearLayout card = Ui.card(this, results);
            if (isFile) {
                card.setClickable(true);
                card.setBackground(rippleBackground());
            }
            TextView t = Ui.body(this, r[2]);
            t.setTextSize(15);
            card.addView(t);
            if (isFile) {
                long size = 0;
                try { size = Long.parseLong(r[1]); } catch (NumberFormatException ignored) { }
                card.addView(Ui.body(this, TransferStore.size(size)));
                card.setOnClickListener(v -> actionFor(r[2], peer));
            }
        }
    }

    /** 點結果：本機的檔案要選傳給哪一台；裝置上的檔案就取回來。 */
    private void actionFor(final String path, final TransferStore.Peer peer) {
        if (peer != null) {
            new MaterialAlertDialogBuilder(this).setMessage(L.t("取回到這支手機的「下載/PhoneAI」？\n") + path)
                    .setPositiveButton(L.t("取回"), (d, w) -> new Thread(() -> {
                        try {
                            String name = FileTools.fetch(this, peer, path);
                            if (name != null) ui.post(() -> Toast.makeText(this, L.t("已取回：") + name, Toast.LENGTH_LONG).show());
                        } catch (Exception e) {
                            ui.post(() -> Toast.makeText(this, L.t("取回失敗：") + e.getMessage(), Toast.LENGTH_LONG).show());
                        }
                    }).start())
                    .setNegativeButton(L.t("取消"), null).show();
            return;
        }
        List<TransferStore.Peer> peers = TransferStore.peers(this);
        if (peers.isEmpty()) {
            Toast.makeText(this, L.t("還沒有配對過的裝置"), Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[peers.size()];
        for (int i = 0; i < names.length; i++) names[i] = peers.get(i).name;
        new MaterialAlertDialogBuilder(this).setTitle(L.t("傳給哪一台")).setItems(names, (d, i) -> {
            final TransferStore.Peer p = peers.get(i);
            new Thread(() -> {
                try {
                    FileTools.sendLocal(this, p, new File(TransferStore.root(), path));
                } catch (Exception e) {
                    ui.post(() -> Toast.makeText(this, L.t("傳檔案失敗：") + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            }).start();
        }).setNegativeButton(L.t("取消"), null).show();
    }
}
