package com.phoneai.overlay;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.CountDownTimer;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;

/** 輸入框 + 送出 + 語音。MainActivity 和 AskActivity 共用；宿主要把 onActivityResult 轉給 handleResult。 */
public class AskPanel {
    static final int REQ_VOICE = 4711;
    static final int AUTO_SEND_SECONDS = 5;

    private final Activity act;
    private final EditText input;
    private final Button send;
    private final TextView status;
    private final LinearLayout root;
    private CountDownTimer timer;
    private boolean programmatic;
    private final Runnable onSent;

    public AskPanel(Activity a, Runnable onSent) {
        act = a;
        this.onSent = onSent;
        root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);

        com.google.android.material.textfield.TextInputLayout til = new com.google.android.material.textfield.TextInputLayout(a);
        til.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
        til.setBoxCornerRadii(Ui.dp(a, 20), Ui.dp(a, 20), Ui.dp(a, 20), Ui.dp(a, 20));
        til.setHint("要做什麼？");
        til.setHelperText("例：開藍牙、打開 Chrome 搜尋天氣");
        input = new com.google.android.material.textfield.TextInputEditText(til.getContext());
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setSingleLine(false);
        input.setMinLines(2);
        input.setMaxLines(4);
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int af) { }
            public void onTextChanged(CharSequence s, int st, int b, int c) { }
            public void afterTextChanged(Editable e) { if (!programmatic) cancelCountdown(); }
        });
        input.setOnEditorActionListener((v, id, ev) -> {
            if (id == EditorInfo.IME_ACTION_SEND) { submit(); return true; }
            return false;
        });
        til.addView(input);
        root.addView(til);

        com.google.android.material.button.MaterialButton mic = Ui.pill(a, "語音", false, v -> startVoice());
        com.google.android.material.button.MaterialButton sendBtn = Ui.pill(a, "送出", true, v -> submit());
        send = sendBtn;
        LinearLayout bar = Ui.buttons(a, root, mic, sendBtn);

        status = new TextView(a);
        status.setTextSize(14);
        root.addView(status);
    }

    public View view() { return root; }

    public void focus() { input.requestFocus(); }

    public void startVoice() {
        cancelCountdown();
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-TW")
                .putExtra(RecognizerIntent.EXTRA_PROMPT, "請說要做什麼");
        try {
            act.startActivityForResult(i, REQ_VOICE);
        } catch (ActivityNotFoundException e) {
            status.setText("這支手機沒有可用的語音辨識（需要 Google 語音服務）。可以改用打字。");
        }
    }

    /** 宿主的 onActivityResult 呼叫它；回傳 true 表示這是語音結果。 */
    public boolean handleResult(int req, int res, Intent data) {
        if (req != REQ_VOICE) return false;
        if (res == Activity.RESULT_OK && data != null) {
            ArrayList<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (r != null && !r.isEmpty()) {
                programmatic = true;
                input.setText(r.get(0));
                input.setSelection(input.getText().length());
                programmatic = false;
                startCountdown();
            }
        }
        return true;
    }

    /** 語音辨識可能聽錯，所以不立刻執行：倒數幾秒，期間改字或按取消就停止。 */
    private void startCountdown() {
        cancelCountdown();
        timer = new CountDownTimer(AUTO_SEND_SECONDS * 1000L, 1000) {
            public void onTick(long ms) {
                send.setText("送出（" + (ms / 1000 + 1) + "）");
                status.setText("聽到的內容在上面。要修改就直接改字，倒數會停止。");
            }
            public void onFinish() { submit(); }
        };
        timer.start();
    }

    private void cancelCountdown() {
        if (timer != null) { timer.cancel(); timer = null; }
        send.setText("送出");
    }

    private void submit() {
        cancelCountdown();
        String err = CommandSender.send(act, input.getText().toString());
        if (err != null) {
            status.setText("❌ " + err);
            return;
        }
        status.setText("✅ 已送出，看通知欄或左上角膠囊");
        input.setText("");
        if (onSent != null) onSent.run();
    }
}
