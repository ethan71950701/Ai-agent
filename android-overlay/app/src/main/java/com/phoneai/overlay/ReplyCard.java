package com.phoneai.overlay;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
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

    private static final int BODY_TAG = 0x7f0a0e62;

    /** 串流回答：文字邊收邊換進同一張卡片（沒有重建卡片）。 */
    static void setText(View card, String text) {
        Object o = card.getTag(BODY_TAG);
        if (!(o instanceof TextView)) return;
        TextView body = (TextView) o;
        body.setText(Md.render(text));
        if (text != null && text.contains(TavilySearch.SOURCES_MARK.trim())) {
            android.text.util.Linkify.addLinks(body, android.text.util.Linkify.WEB_URLS);
            body.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
            body.setLinkTextColor(0xFF8AB4F8);
        }
    }

    static FrameLayout build(Activity act, String title, String text, Runnable close, int maxHeightPx) { return build(act, title, text, close, maxHeightPx, null); }

    /** source 不是 null：卡片用跟輸入列同一種液態玻璃（折射 source 後面的畫面、顏色增強）；null：舊的深色卡片。 */
    static FrameLayout build(Activity act, String title, String text, Runnable close, int maxHeightPx, View source) {
        final float d = act.getResources().getDisplayMetrics().density;
        final boolean glass = source != null;
        FrameLayout card;
        if (glass) {
            GlassBar g = new GlassBar(act, 0xFF23262A, 0x00000000, 0x44FFFFFF, 30 * d, false);
            g.setSource(source);
            g.setGlowColor(0xFFFFFFFF);
            g.setClipChildren(false);
            g.setLive(true);
            g.setBlurDp(14f);       // 後面的畫面（桌面截圖）模糊：卡片裡模糊、卡片外清楚，邊界就出來了
            g.setDarkScale(0.9f);   // 比輸入列稍暗：後面是別的 App 的畫面（抓不到、不能模糊）時，卡片邊界才看得出來
            g.setLift(1f);   // 跟後面的畫面拉開亮度落差：很暗的背景提亮、其他壓暗，任何背景都看得出卡片
            // 白色邊框已拿掉（使用者覺得很突兀）；邊界靠後面畫面的模糊與亮度落差
            card = g;
        } else card = new FrameLayout(act);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xD8202030, 0xD810101C});
        bg.setStroke((int) Math.max(2, 1.5f * d), 0x99FFFFFF);   // 白色細邊
        bg.setCornerRadius(30 * d);
        if (!glass) {
        card.setBackground(bg);
        card.setElevation(24 * d);                                // 浮起來；陰影用白色，像一圈柔和白光
        card.setOutlineSpotShadowColor(0xFFFFFFFF);
        card.setOutlineAmbientShadowColor(0xFFFFFFFF);
        card.setClipToOutline(true);
        }

        // 底部光區：AuroraView 放在一個固定高度的容器裡，貼著卡片底邊；光暈中心在容器底部附近
        final int glowH = (int) (GLOW_DP * d);
        FrameLayout glowBox = new FrameLayout(act);
        AuroraView aurora = new AuroraView(act);
        aurora.setEnergy(0.7f);
        glowBox.addView(aurora, new FrameLayout.LayoutParams(-1, -1));
        View glowFade = new View(act);   // 光區上緣慢慢淡進卡片底色，不要有一條硬邊
        glowFade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xD8191927, 0x00191927}));
        glowBox.addView(glowFade, new FrameLayout.LayoutParams(-1, (int) (90 * d), Gravity.TOP));
        if (!glass) {   // 玻璃版不放底部光區（玻璃後面已經是極光）
        card.addView(glowBox, new FrameLayout.LayoutParams(-1, glowH, Gravity.BOTTOM));
        card.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, orr, ob) -> aurora.setGlowY(glowH - 16 * d));
        }

        final int maxH = maxHeightPx;   // 呼叫端依畫面實際可用的高度算好（卡片從上方排起，長文在裡面捲動，不會超出螢幕）
        ScrollView sv = new ScrollView(act) {
            @Override
            protected void onMeasure(int w, int h) {
                super.onMeasure(w, MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST));
            }
        };
        sv.setVerticalScrollBarEnabled(false);
        sv.setVerticalFadingEdgeEnabled(true);        // 文字太長時，上下緣慢慢變淡，表示還可以捲
        sv.setFadingEdgeLength((int) (40 * d));
        sv.setOnTouchListener((v, e) -> { v.getParent().requestDisallowInterceptTouchEvent(true); return false; });   // 在卡片上上下滑是捲動，不是關面板
        sv.setPadding((int) (26 * d), (int) (22 * d), (int) (24 * d), (int) (22 * d));
        TextView body = new TextView(act);
        body.setText(Md.render(text));
        body.setTextColor(0xFFF4F4F8);
        body.setTextSize(20);
        body.setLineSpacing(0, 1.38f);
        body.setTextIsSelectable(true);
        if (text != null && text.contains(TavilySearch.SOURCES_MARK.trim())) {   // 有來源清單：網址要能點（這種回答改用可點連結的方式，不能同時用選取模式）
            android.text.util.Linkify.addLinks(body, android.text.util.Linkify.WEB_URLS);
            body.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
            body.setLinkTextColor(0xFF8AB4F8);
        }
        body.setPadding(0, 0, (int) (4 * d), 0);
        sv.addView(body, new FrameLayout.LayoutParams(-1, -2));
        card.addView(sv, new FrameLayout.LayoutParams(-1, -2));
        card.setTag(BODY_TAG, body);

        return card;
    }
}
