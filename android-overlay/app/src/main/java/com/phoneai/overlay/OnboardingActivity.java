package com.phoneai.overlay;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

/** 第一次開啟時的教學：左右滑動的分頁，每頁一個循環小動畫；底下的圓點用彈簧動畫拉長。可隨時略過；之後在「功能 → 進階 → 使用教學」重看。 */
public class OnboardingActivity extends Activity {
    private static final String[] TITLES = {"歡迎使用 PhoneAI", "隨時叫出它", "用說的，或用打的", "它會自己操作", "敏感操作先問你", "最後一步：設定"};
    private static final String[] BODIES = {
            "住在你手機裡的 AI 助手：你說想做什麼，它看著螢幕幫你操作。",
            "長按電源鍵（先在系統設定把它設成預設數位助理），或打開螢幕邊緣的浮動按鈕。",
            "語音在手機上離線辨識，不會上傳；也可以直接打字。",
            "看到畫面後會點擊、輸入、捲動。螢幕邊緣的光暈和點擊漣漪，讓你知道它正在做什麼。",
            "付款、轉帳、刪除、傳訊息、登入這類操作，一律先跳出確認框；你沒有回應就是不同意。",
            "給 App 需要的權限、設定 API Key。隨時可以在「功能 → 維護」重做。"};
    private ViewPager2 pager;
    private final View[] dots = new View[TITLES.length];
    private View next;
    private TextView skip;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        registerBack();
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.attr(this, com.google.android.material.R.attr.colorSurface));
        root.setFitsSystemWindows(true);
        pager = new ViewPager2(this);
        pager.setAdapter(new Adapter());
        pager.setOffscreenPageLimit(1);
        pager.setPageTransformer((page, pos) -> {   // 滑動時：離開的頁縮小淡出、進來的頁由小放大
            float a = 1f - Math.min(1f, Math.abs(pos));
            page.setAlpha(0.3f + 0.7f * a);
            float s = 0.9f + 0.1f * a;
            page.setScaleX(s);
            page.setScaleY(s);
            page.setTranslationX(-pos * page.getWidth() * 0.12f);
        });
        root.addView(pager, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 24);
        bottom.setPadding(pad, 0, pad, Ui.dp(this, 24));
        LinearLayout dotRow = new LinearLayout(this);
        dotRow.setGravity(android.view.Gravity.CENTER);
        for (int i = 0; i < TITLES.length; i++) {
            View d = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 8), Ui.dp(this, 8));
            lp.setMargins(Ui.dp(this, 3), 0, Ui.dp(this, 3), 0);
            dotRow.addView(d, lp);
            dots[i] = d;
        }
        bottom.addView(dotRow, new LinearLayout.LayoutParams(-1, -2));
        // 第二排：左邊「略過」，中間是箭頭按鈕（最後一頁同樣是箭頭，按下完成）
        FrameLayout actions = new FrameLayout(this);
        skip = Ui.body(this, L.t("略過"));
        skip.setTextIsSelectable(false);
        skip.setPadding(Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        skip.setOnClickListener(v -> finishTour());
        actions.addView(skip, new FrameLayout.LayoutParams(-2, -2, android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL));
        next = Ui.arrowFab(this, v -> {
            if (pager.getCurrentItem() >= TITLES.length - 1) finishTour();
            else pager.setCurrentItem(pager.getCurrentItem() + 1, true);
        });
        actions.addView(next, new FrameLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 64), android.view.Gravity.CENTER));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, Ui.dp(this, 64));
        alp.topMargin = Ui.dp(this, 12);
        bottom.addView(actions, alp);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-1, -2, android.view.Gravity.BOTTOM);
        root.addView(bottom, bp);
        setContentView(root);
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int i) { select(i); }
        });
        select(0);
    }

    /** 圓點：目前的那顆用彈簧拉長成膠囊、換成主色。 */
    private void select(int sel) {
        for (int i = 0; i < dots.length; i++) {
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setCornerRadius(Ui.dp(this, 4));
            g.setColor(i == sel ? Ui.attr(this, com.google.android.material.R.attr.colorPrimary) : Ui.attr(this, com.google.android.material.R.attr.colorOutlineVariant));
            dots[i].setBackground(g);
            final View d = dots[i];
            final int target = Ui.dp(this, i == sel ? 24 : 8);
            android.animation.ValueAnimator va = android.animation.ValueAnimator.ofInt(d.getLayoutParams().width, target);
            va.setDuration(260);
            va.setInterpolator(new android.view.animation.OvershootInterpolator(1.4f));
            va.addUpdateListener(a -> {
                ViewGroup.LayoutParams lp = d.getLayoutParams();
                lp.width = (Integer) a.getAnimatedValue();
                d.setLayoutParams(lp);
            });
            va.start();
        }
        skip.setVisibility(sel == TITLES.length - 1 ? View.INVISIBLE : View.VISIBLE);
    }

    private void finishTour() {
        android.content.SharedPreferences cfg = getSharedPreferences("cfg", MODE_PRIVATE);
        boolean first = !cfg.getBoolean("onboarded", false);
        cfg.edit().putBoolean("onboarded", true).apply();
        // 第一次開 App：歡迎動畫看完，接著進設定導覽；之後從「功能介紹」進來，看完直接回去
        if (first) startActivity(new Intent(this, GuideActivity.class));
        finish();
    }

    // 預測返回（Android 13+）：小米等手機要 App 自己註冊，不會呼叫 onBackPressed
    private void registerBack() {
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::finishTour);
    }

    /** 第一頁就讓使用者選主題色（和設定頁是同一個元件）。 */
    private void addThemePicker(Activity a, LinearLayout page) {
        TextView hint = Ui.body(a, L.t("先選一個主題色（之後可以在「設定 → 外觀」再改）"));
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2);
        hl.topMargin = Ui.dp(a, 16);
        page.addView(hint, hl);
        Ui.themePicker(a, page);
    }

    private final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override
        public int getItemCount() { return TITLES.length; }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
            LinearLayout page = new LinearLayout(OnboardingActivity.this);
            page.setOrientation(LinearLayout.VERTICAL);
            page.setLayoutParams(new RecyclerView.LayoutParams(-1, -1));
            return new RecyclerView.ViewHolder(page) { };
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder h, int i) {
            LinearLayout page = (LinearLayout) h.itemView;
            page.removeAllViews();
            page.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            page.setPadding(Ui.dp(OnboardingActivity.this, 32), Ui.dp(OnboardingActivity.this, 48), Ui.dp(OnboardingActivity.this, 32), Ui.dp(OnboardingActivity.this, 120));
            DemoView demo = new DemoView(OnboardingActivity.this, i);
            page.addView(demo, new LinearLayout.LayoutParams(-1, 0, 1f));
            // 文字區佔一半高度並置中：標題與說明（welcome 的設定圓點也在這裡）不再貼在下方或偏上
            LinearLayout textBox = new LinearLayout(OnboardingActivity.this);
            textBox.setOrientation(LinearLayout.VERTICAL);
            textBox.setGravity(android.view.Gravity.CENTER);
            textBox.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
            page.addView(textBox);
            Ui.bounceIn(demo);   // 示意圖進場彈一下
            TextView t = new TextView(OnboardingActivity.this);
            t.setText(L.t(TITLES[i]));
            t.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_HeadlineMedium);
            t.setTextColor(Ui.attr(OnboardingActivity.this, com.google.android.material.R.attr.colorOnSurface));
            t.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, -2);
            tl.topMargin = Ui.dp(OnboardingActivity.this, 24);
            textBox.addView(t, tl);
            TextView b = new TextView(OnboardingActivity.this);
            b.setText(L.t(BODIES[i]));
            b.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
            b.setTextColor(Ui.attr(OnboardingActivity.this, com.google.android.material.R.attr.colorOnSurfaceVariant));
            b.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, -2);
            bl.topMargin = Ui.dp(OnboardingActivity.this, 12);
            textBox.addView(b, bl);
            if (i == 0) addThemePicker(OnboardingActivity.this, textBox);
        }
    }
}
