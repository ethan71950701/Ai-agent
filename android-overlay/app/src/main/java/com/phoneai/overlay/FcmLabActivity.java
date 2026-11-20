package com.phoneai.overlay;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.messaging.FirebaseMessaging;

/** 實驗畫面：載入設定檔、看這支手機的 token、對另一支手機的 token 送測試推播。只存在實驗版。 */
public class FcmLabActivity extends Activity {
    private static final int REQ_CONFIG = 1, REQ_SENDER = 2;
    private TextView status, tokenView;
    private String myToken = "";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setTitle("FCM 喚醒實驗");
        int p = (int) (16 * getResources().getDisplayMetrics().density);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(p, p, p, p);
        ScrollView sv = new ScrollView(this);
        sv.addView(col);
        setContentView(sv);
        TextView intro = new TextView(this);
        intro.setText("實驗：用 FCM 推播叫醒沒開著的 App。\n1）兩支手機都選 google-services.json\n2）發送的那支再選服務帳戶金鑰（用完請刪）\n3）把接收那支的 token 複製到發送那支，按送出。送之前先把接收那支的 App 從最近工作清單滑掉。");
        col.addView(intro);
        status = new TextView(this);
        col.addView(status);
        col.addView(button("選 google-services.json", v -> pick(REQ_CONFIG)));
        col.addView(button("選服務帳戶金鑰（只有發送端需要）", v -> pick(REQ_SENDER)));
        col.addView(button("刪除服務帳戶金鑰", v -> { FcmVault.delete(this); FcmLab.file(this, FcmLab.SENDER).delete(); refresh(); }));
        tokenView = new TextView(this);
        tokenView.setTextIsSelectable(true);
        col.addView(tokenView);
        col.addView(button("複製這支手機的 token", v -> {
            ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("token", myToken));
            Toast.makeText(this, "已複製", Toast.LENGTH_SHORT).show();
        }));
        EditText target = new EditText(this);
        target.setHint("貼上接收端手機的 token");
        col.addView(target);
        col.addView(button("送出測試推播", v -> {
            String t = target.getText().toString().trim();
            if (t.isEmpty()) { Toast.makeText(this, "先貼 token", Toast.LENGTH_SHORT).show(); return; }
            new Thread(() -> {
                String r = FcmLab.send(this, t, "hello " + new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date()));
                runOnUiThread(() -> Toast.makeText(this, r == null ? "已送出" : r, Toast.LENGTH_LONG).show());
            }).start();
        }));
        TextView t2 = new TextView(this);
        t2.setText("\n—— 接上互傳（階段 2）——\n兩支手機要先在「設定 → 互傳檔案」配對，並給「所有檔案存取」。發送端的互傳總開關要開著（它在使用中）。接收端把 App 滑掉也行。");
        col.addView(t2);
        col.addView(button("儲存已配對裝置的 token", v -> pickPeer(peer -> {
            EditText e = new EditText(this);
            e.setHint("貼上「" + peer.name + "」的 token");
            new android.app.AlertDialog.Builder(this).setTitle(peer.name).setView(e).setPositiveButton("儲存", (d, w) -> {
                TransferStore.setFcmToken(this, peer.id, e.getText().toString());
                Toast.makeText(this, "已儲存", Toast.LENGTH_SHORT).show();
            }).setNegativeButton("取消", null).show();
        })));
        col.addView(button("把我的 token 交給已配對裝置（兩邊都開著互傳）", v -> new Thread(() -> {
            String my = myToken;
            if (!Doorbell.validToken(my)) { runOnUiThread(() -> Toast.makeText(this, "這支手機還沒有 token（先載入設定檔）", Toast.LENGTH_LONG).show()); return; }
            StringBuilder sb = new StringBuilder();
            for (TransferStore.Peer peerX : TransferStore.peers(this)) {
                try { FileTools.clientFor(this, peerX).sendFcmToken(my); sb.append("✓ ").append(peerX.name).append('\n'); }
                catch (Exception e) { sb.append("✗ ").append(peerX.name).append("：").append(e.getMessage()).append('\n'); }
            }
            final String res = sb.length() == 0 ? "沒有已配對的裝置" : sb.toString();
            runOnUiThread(() -> Toast.makeText(this, res, Toast.LENGTH_LONG).show());
        }).start()));
        EditText nameQ = new EditText(this);
        nameQ.setHint("要傳的檔名關鍵字（本機共享資料夾裡找）");
        col.addView(nameQ);
        col.addView(button("按門鈴：請對方來取這個檔案", v -> {
            final String q = nameQ.getText().toString().trim();
            if (q.isEmpty()) { Toast.makeText(this, "先填檔名關鍵字", Toast.LENGTH_SHORT).show(); return; }
            pickPeer(peer -> new Thread(() -> {
                String raw;
                try { raw = TransferServer.findIn(TransferStore.root(), q); } catch (java.io.IOException e) { raw = ""; }
                String rel = null;
                for (String line : raw.split("\n")) {
                    String[] f = line.split("\t");
                    if (f.length >= 4 && f[0].equals("F")) { rel = f[3]; break; }
                }
                final String r = rel == null ? "找不到檔名包含「" + q + "」的檔案" : FcmLab.ring(this, peer, rel);
                final String shown = rel;
                runOnUiThread(() -> Toast.makeText(this, r == null ? "已按門鈴：" + shown : r, Toast.LENGTH_LONG).show());
            }).start());
        }));
        refresh();
    }

    private Button button(String t, android.view.View.OnClickListener l) {
        Button x = new Button(this);
        x.setText(t);
        x.setAllCaps(false);
        x.setOnClickListener(l);
        return x;
    }

    private void pickPeer(java.util.function.Consumer<TransferStore.Peer> then) {
        final java.util.List<TransferStore.Peer> ps = TransferStore.peers(this);
        if (ps.isEmpty()) { Toast.makeText(this, "還沒有配對過的裝置（設定 → 互傳檔案）", Toast.LENGTH_LONG).show(); return; }
        String[] names = new String[ps.size()];
        for (int i = 0; i < names.length; i++) names[i] = ps.get(i).name + (ps.get(i).fcmToken == null ? "（沒存 token）" : "");
        new android.app.AlertDialog.Builder(this).setTitle("選裝置").setItems(names, (d, w) -> then.accept(ps.get(w))).show();
    }

    private void pick(int req) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
        startActivityForResult(i, req);
    }

    @Override protected void onActivityResult(int req, int res, Intent d) {
        super.onActivityResult(req, res, d);
        if (res != RESULT_OK || d == null || d.getData() == null) return;
        try (java.io.InputStream in = getContentResolver().openInputStream(d.getData())) {
            String r = FcmLab.store(this, in, req == REQ_CONFIG ? FcmLab.CONFIG : FcmLab.SENDER);
            Toast.makeText(this, r == null ? "已載入" : r, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, String.valueOf(e), Toast.LENGTH_LONG).show();
        }
        refresh();
    }

    private void refresh() {
        String init = FcmLab.init(this);
        status.setText("設定檔：" + (FcmLab.hasConfig(this) ? "已載入（" + FcmLab.projectId(this) + "）" : "未載入")
                + "\n服務帳戶金鑰：" + (FcmLab.hasSenderKey(this) ? "有（發送端，已加密存放）" : "無")
                + "\n初始化：" + (init == null ? "成功" : init));
        tokenView.setText("");
        if (init != null) return;
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(t -> {
            if (t.isSuccessful()) { myToken = t.getResult(); tokenView.setText("這支手機的 token：\n" + myToken); }
            else tokenView.setText("取得 token 失敗：" + t.getException());
        });
    }
}
