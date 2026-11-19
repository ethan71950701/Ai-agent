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
        col.addView(button("刪除服務帳戶金鑰", v -> { FcmLab.file(this, FcmLab.SENDER).delete(); refresh(); }));
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
        refresh();
    }

    private Button button(String t, android.view.View.OnClickListener l) {
        Button x = new Button(this);
        x.setText(t);
        x.setAllCaps(false);
        x.setOnClickListener(l);
        return x;
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
                + "\n服務帳戶金鑰：" + (FcmLab.file(this, FcmLab.SENDER).isFile() ? "有（發送端）" : "無")
                + "\n初始化：" + (init == null ? "成功" : init));
        tokenView.setText("");
        if (init != null) return;
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(t -> {
            if (t.isSuccessful()) { myToken = t.getResult(); tokenView.setText("這支手機的 token：\n" + myToken); }
            else tokenView.setText("取得 token 失敗：" + t.getException());
        });
    }
}
