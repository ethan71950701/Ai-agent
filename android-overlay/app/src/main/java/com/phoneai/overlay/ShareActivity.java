package com.phoneai.overlay;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** 別的 App 的「分享」選單選 PhoneAI：把文字填進助理面板的輸入框，不自動送出、不自動聽，讓使用者確認後再送。 */
public class ShareActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String text = getIntent().getStringExtra(Intent.EXTRA_TEXT);
        if (text != null && !text.trim().isEmpty()) {
            startActivity(new Intent(this, AskActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra("fill", text)
                    .putExtra("voice", false));
        }
        finish();
    }
}
