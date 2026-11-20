package com.phoneai.overlay;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 點通知進來：完整顯示通知的文字（通知列最多只看得到一部分），可以選取複製。 */
public class NoticeActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        String title = getIntent().getStringExtra("title"), text = getIntent().getStringExtra("text");
        LinearLayout[] c = new LinearLayout[1];
        setContentView(Ui.screen(this, title == null || title.isEmpty() ? L.t("通知") : title, true, c));
        Ui.enterSpring(this);
        TextView t = Ui.body(this, text == null ? "" : text);
        t.setTextSize(16);
        t.setTextIsSelectable(true);
        LinearLayout card = Ui.card(this, c[0]);
        card.addView(t);
    }
}
