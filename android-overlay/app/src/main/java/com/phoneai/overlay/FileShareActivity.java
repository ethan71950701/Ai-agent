package com.phoneai.overlay;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * 別的 App 的「分享」選單選 PhoneAI（檔案）：挑要傳給哪一台已配對的裝置，然後在背景傳出去。
 * 進度與停止都在通知列（同互傳檔案的其他傳輸）。
 */
public class FileShareActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        final List<Uri> uris = collect(getIntent());
        if (uris.isEmpty()) {
            finish();
            return;
        }
        if (!TransferStore.enabled(this)) {
            Toast.makeText(this, L.t("請先到「功能 → 互傳檔案」打開開關"), Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        final List<TransferStore.Peer> peers = TransferStore.peers(this);
        if (peers.isEmpty()) {
            Toast.makeText(this, L.t("還沒有配對過的裝置"), Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        String[] names = new String[peers.size()];
        for (int i = 0; i < names.length; i++) names[i] = peers.get(i).name;
        new MaterialAlertDialogBuilder(this)
                .setTitle(L.t("傳給哪一台（") + uris.size() + L.t(" 個檔案）"))
                .setItems(names, (d, i) -> send(peers.get(i), uris))
                .setNegativeButton(L.t("取消"), null)
                .setOnDismissListener(d -> finish())
                .show();
    }

    private void send(TransferStore.Peer p, List<Uri> uris) {
        Toast.makeText(this, L.t("開始傳送…可以在通知列看進度"), Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            for (Uri u : uris) {
                try {
                    FileTools.sendUri(this, p, u);
                } catch (Exception e) {
                    TransferStore.log(this, L.t("傳送失敗：") + e.getMessage());
                }
            }
        }).start();
    }

    @SuppressWarnings("deprecation")
    private static List<Uri> collect(Intent it) {
        List<Uri> out = new ArrayList<>();
        if (it == null) return out;
        if (Intent.ACTION_SEND.equals(it.getAction())) {
            Uri u = it.getParcelableExtra(Intent.EXTRA_STREAM);
            if (u != null) out.add(u);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(it.getAction())) {
            List<Uri> l = it.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (l != null) out.addAll(l);
        }
        return out;
    }
}
