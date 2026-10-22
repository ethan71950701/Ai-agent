package com.phoneai.overlay;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import com.google.android.material.materialswitch.MaterialSwitch;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 接手背景螢幕時「輸入文字」用的小對話框：在你的主螢幕鍵盤上打字（預設隱藏內容，適合密碼），
 * 按「貼上」就透過剪貼簿貼到背景螢幕（跟 AI 輸入文字是同一個做法，所以中文也行），貼完約 1.5 秒後清掉剪貼簿。
 * 背景螢幕本身的鍵盤是隱藏的（display_ime_policy=hide），所以才需要這個。
 */
public class TakeoverInputActivity extends Activity {
    static volatile boolean active;
    private EditText et;
    private Button paste, pasteEnter;
    private final Handler h = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        active = true;
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        float d = getResources().getDisplayMetrics().density;
        int pad = (int) (18 * d);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText(L.t("輸入到背景畫面"));
        title.setTextSize(18);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        box.addView(title);
        TextView hint = new TextView(this);
        hint.setText(L.t("先在背景畫面點好要輸入的欄位，再來這裡打字。"));
        hint.setTextSize(13);
        hint.setPadding(0, (int) (4 * d), 0, (int) (8 * d));
        box.addView(hint);

        et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);  // 預設隱藏內容
        et.setSingleLine(true);
        box.addView(et, new LinearLayout.LayoutParams(-1, -2));

        com.google.android.material.materialswitch.MaterialSwitch show = new com.google.android.material.materialswitch.MaterialSwitch(this);
        show.setText(L.t("顯示文字"));
        show.setOnCheckedChangeListener((v, on) -> {
            et.setInputType(InputType.TYPE_CLASS_TEXT | (on ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : InputType.TYPE_TEXT_VARIATION_PASSWORD));
            et.setSelection(et.getText().length());
        });
        box.addView(show);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.END);
        Button cancel = new com.google.android.material.button.MaterialButton(this);
        cancel.setText(L.t("取消"));
        cancel.setOnClickListener(v -> finish());
        paste = new com.google.android.material.button.MaterialButton(this);
        paste.setText(L.t("貼上"));
        paste.setOnClickListener(v -> send(false));
        pasteEnter = new com.google.android.material.button.MaterialButton(this);
        pasteEnter.setText(L.t("貼上並 Enter"));
        pasteEnter.setOnClickListener(v -> send(true));
        row.addView(cancel);
        row.addView(paste);
        row.addView(pasteEnter);
        box.addView(row);
        setContentView(box);
        setFinishOnTouchOutside(false);
    }

    private void send(boolean enter) {
        String t = et.getText().toString();
        int display = OverlayService.vdId;
        if (t.isEmpty()) return;
        if (display <= 0) {
            Toast.makeText(this, L.t("找不到背景螢幕"), Toast.LENGTH_SHORT).show();
            return;
        }
        paste.setEnabled(false);
        pasteEnter.setEnabled(false);
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("", t);
        PersistableBundle ex = new PersistableBundle();
        ex.putBoolean("android.content.extra.IS_SENSITIVE", true);  // 告訴系統這是敏感內容（不要在剪貼簿預覽裡顯示）
        clip.getDescription().setExtras(ex);
        cm.setPrimaryClip(clip);
        RootInput.key(display, 279);        // KEYCODE_PASTE，貼到背景螢幕目前選取的輸入框
        if (enter) RootInput.key(display, 66);
        et.setText("");
        // 等貼完再清剪貼簿（這時 App 還在前景才清得掉），然後關掉
        h.postDelayed(() -> {
            try { cm.clearPrimaryClip(); } catch (Exception ignored) { }
            finish();
        }, 1500);
    }

    @Override
    protected void onDestroy() {
        active = false;
        RippleAccessibilityService.armTakeoverIdle();
        super.onDestroy();
    }
}
