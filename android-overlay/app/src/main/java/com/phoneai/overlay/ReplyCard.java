package com.phoneai.overlay;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 助理文字回答的卡片：跟長按電源鍵的助理面板同一種風格（深色玻璃、白色細邊、大圓角），只有可捲動、可選取的文字，
 * 沒有標題、沒有關閉鈕、沒有複製鈕——點卡片外面的空白處或按返回就關閉（呼叫端的暗幕負責）；文字可長按選取，用系統的複製。
 * 卡片底部直接放一個 AuroraView（跟電源鍵面板同一套極光光暈與飄動的光點），在文字的下層，所以不會蓋到字。
 * 只由助理面板（AssistantSheet.showReply）使用。title、close 參數保留是為了相容呼叫端，這張卡片不再用它們。
 */
final class ReplyCard {
    private static final int GLOW_DP = 170;   // 底部光區的高度：只佔卡片下方一段，上面留給文字

    private ReplyCard() { }

    static FrameLayout build(Activity act, String title, String text, Runnable close) {
        final float d = act.getResources().getDisplayMetrics().density;
        FrameLayout card = new FrameLayout(act);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xD8202030, 0xD810101C});
        bg.setStroke((int) Math.max(2, 1.5f * d), 0x99FFFFFF);   // 白色細邊
        bg.setCornerRadius(30 * d);
        card.setBackground(bg);
        card.setElevation(24 * d);                                // 浮起來；陰影用白色，像一圈柔和白光
        card.setOutlineSpotShadowColor(0xFFFFFFFF);
        card.setOutlineAmbientShadowColor(0xFFFFFFFF);
        card.setClipToOutline(true);

        // 底部光區：AuroraView 放在一個固定高度的容器裡，貼著卡片底邊；光暈中心在容器底部附近
        final int glowH = (int) (GLOW_DP * d);
        FrameLayout glowBox = new FrameLayout(act);
        AuroraView aurora = new AuroraView(act);
        aurora.setEnergy(0.7f);
        glowBox.addView(aurora, new FrameLayout.LayoutParams(-1, -1));
        card.addView(glowBox, new FrameLayout.LayoutParams(-1, glowH, Gravity.BOTTOM));
        card.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, orr, ob) -> aurora.setGlowY(glowH - 16 * d));

        final int maxH = (int) (act.getResources().getDisplayMetrics().heightPixels * 0.55f);
        ScrollView sv = new ScrollView(act) {
            @Override
            protected void onMeasure(int w, int h) {
                super.onMeasure(w, MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST));
            }
        };
        sv.setVerticalScrollBarEnabled(false);
        sv.setPadding((int) (26 * d), (int) (22 * d), (int) (24 * d), (int) (22 * d));
        TextView body = new TextView(act);
        body.setText(text);
        body.setTextColor(0xFFF4F4F8);
        body.setTextSize(20);
        body.setLineSpacing(0, 1.38f);
        body.setTextIsSelectable(true);
        body.setPadding(0, 0, (int) (4 * d), 0);
        sv.addView(body, new FrameLayout.LayoutParams(-1, -2));
        card.addView(sv, new FrameLayout.LayoutParams(-1, -2));

        return card;
    }
}
