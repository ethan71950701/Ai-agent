package com.phoneai.overlay;

import android.content.Context;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;

/**
 * 共用的 Material 3 元件：App 的每個畫面都用這裡的函式建立，顏色與字體跟著系統主題（深色／淺色）。
 * 只負責外觀；點擊、存檔等邏輯仍然留在各個畫面。
 */
final class Ui {
    private Ui() { }

    static int dp(Context c, int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    /** 可捲動的頁面：背景是 Material 的 surface 色，內距 16dp。 */
    static LinearLayout page(Context c, ScrollView[] outScroll) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(c, 16);
        box.setPadding(pad, dp(c, 24), pad, pad);
        ScrollView sv = new ScrollView(c);
        sv.setFillViewport(true);
        sv.addView(box);
        sv.setBackgroundColor(attr(c, com.google.android.material.R.attr.colorSurface));
        if (outScroll != null && outScroll.length > 0) outScroll[0] = sv;
        return box;
    }

    /** 頁面標題（大字）。 */
    static TextView title(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_HeadlineSmall);
        t.setTextColor(attr(c, com.google.android.material.R.attr.colorOnSurface));
        t.setPadding(0, 0, 0, dp(c, 8));
        return t;
    }

    /** 區塊標題（例如「狀態」「設定」）：主色、小字。 */
    static TextView header(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall);
        t.setTextColor(attr(c, com.google.android.material.R.attr.colorPrimary));
        t.setPadding(dp(c, 4), dp(c, 20), 0, dp(c, 6));
        return t;
    }

    /** 一般說明文字（次要顏色）。 */
    static TextView body(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        t.setTextColor(attr(c, com.google.android.material.R.attr.colorOnSurfaceVariant));
        t.setTextIsSelectable(true);
        return t;
    }

    /** 卡片：把一組相關內容框在一起（Material 的 surfaceContainer 色、圓角）。內容要自己 addView 到回傳的 MaterialCardView 裡。 */
    static LinearLayout card(Context c, LinearLayout parent) {
        MaterialCardView card = new MaterialCardView(c);
        card.setCardBackgroundColor(attr(c, com.google.android.material.R.attr.colorSurfaceContainerHigh));
        card.setRadius(dp(c, 16));
        card.setCardElevation(0);
        card.setStrokeWidth(0);
        LinearLayout inner = new LinearLayout(c);
        inner.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(c, 16);
        inner.setPadding(pad, dp(c, 8), pad, dp(c, 8));
        card.addView(inner);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(c, 4);
        parent.addView(card, lp);
        return inner;
    }

    /** 主要按鈕（實心）。 */
    static MaterialButton button(Context c, String text, View.OnClickListener l) {
        MaterialButton b = new MaterialButton(c);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(c, 8);
        b.setLayoutParams(lp);
        return b;
    }

    /** 次要按鈕（淡色底）。 */
    static MaterialButton tonal(Context c, String text, View.OnClickListener l) {
        MaterialButton b = button(c, text, l);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(attr(c, com.google.android.material.R.attr.colorSecondaryContainer)));
        b.setTextColor(attr(c, com.google.android.material.R.attr.colorOnSecondaryContainer));
        return b;
    }

    /** 開關（系統的 Switch 樣式）。checked 是初始狀態，listener 收到新狀態。 */
    static MaterialSwitch toggle(Context c, String text, boolean checked, android.widget.CompoundButton.OnCheckedChangeListener l) {
        MaterialSwitch s = new MaterialSwitch(c);
        s.setText(text);
        s.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
        s.setTextColor(attr(c, com.google.android.material.R.attr.colorOnSurface));
        s.setChecked(checked);
        s.setOnCheckedChangeListener(l);
        s.setPadding(0, dp(c, 10), 0, dp(c, 10));
        return s;
    }

    // ---- 設定頁風格（大標題可收合的頂欄、分組圓角列表）----

    /**
     * 整個畫面：大標題頂欄（往下捲會縮成小標題）＋可捲動內容。內容要加進 content[0]。
     * back=true 時左上角有返回鍵。
     */
    static View screen(android.app.Activity a, String title, boolean back, LinearLayout[] content) {
        androidx.coordinatorlayout.widget.CoordinatorLayout root = new androidx.coordinatorlayout.widget.CoordinatorLayout(a);
        root.setBackgroundColor(attr(a, com.google.android.material.R.attr.colorSurface));
        root.setFitsSystemWindows(true);

        com.google.android.material.appbar.AppBarLayout bar = new com.google.android.material.appbar.AppBarLayout(a);
        bar.setBackgroundColor(attr(a, com.google.android.material.R.attr.colorSurface));
        bar.setFitsSystemWindows(false);
        com.google.android.material.appbar.CollapsingToolbarLayout ctl = new com.google.android.material.appbar.CollapsingToolbarLayout(a);
        ctl.setTitle(title);
        ctl.setCollapsedTitleTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleLarge);
        ctl.setExpandedTitleTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_DisplaySmall);
        ctl.setExpandedTitleColor(attr(a, com.google.android.material.R.attr.colorOnSurface));
        ctl.setCollapsedTitleTextColor(attr(a, com.google.android.material.R.attr.colorOnSurface));
        ctl.setContentScrimColor(attr(a, com.google.android.material.R.attr.colorSurface));
        ctl.setExpandedTitleMarginStart(dp(a, 24));
        ctl.setExpandedTitleMarginBottom(dp(a, 20));
        com.google.android.material.appbar.MaterialToolbar tb = new com.google.android.material.appbar.MaterialToolbar(a);
        if (back) {
            tb.setNavigationIcon(R.drawable.ic_back);
            tb.setNavigationOnClickListener(v -> a.finish());
        }
        com.google.android.material.appbar.CollapsingToolbarLayout.LayoutParams tlp =
                new com.google.android.material.appbar.CollapsingToolbarLayout.LayoutParams(-1, dp(a, 56));
        tlp.setCollapseMode(com.google.android.material.appbar.CollapsingToolbarLayout.LayoutParams.COLLAPSE_MODE_PIN);
        ctl.addView(tb, tlp);
        com.google.android.material.appbar.AppBarLayout.LayoutParams clp =
                new com.google.android.material.appbar.AppBarLayout.LayoutParams(-1, dp(a, 160));
        clp.setScrollFlags(com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL
                | com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED);
        bar.addView(ctl, clp);
        root.addView(bar, new androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(-1, -2));

        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(a, 16);
        box.setPadding(pad, dp(a, 4), pad, dp(a, 32));
        androidx.core.widget.NestedScrollView sv = new androidx.core.widget.NestedScrollView(a);
        sv.setFillViewport(true);
        sv.addView(box, new android.view.ViewGroup.LayoutParams(-1, -2));
        androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams slp =
                new androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(-1, -1);
        slp.setBehavior(new com.google.android.material.appbar.AppBarLayout.ScrollingViewBehavior());
        root.addView(sv, slp);
        content[0] = box;
        root.setTag(new Object[]{ctl, sv});   // 換分頁時改大標題、底欄隨捲動收起
        return root;
    }

    /** 分組小標題（例如「呼叫方式」）。 */
    static void section(Context c, LinearLayout parent, String text) {
        TextView t = header(c, text);
        t.setPadding(dp(c, 12), dp(c, 20), 0, dp(c, 8));
        parent.addView(t);
    }

    /**
     * 列表裡的一列：左邊圖示（線條圖示放在餅乾形底板）、標題、一行說明、右邊 trailing（開關或箭頭，可為 null）。
     * index／count 決定圓角：群組最上、最下的外側是大圓角，中間的列是小圓角，列與列之間留一條細縫（Material 3 Expressive 的分段列表）。
     */
    static MaterialCardView row(Context c, LinearLayout parent, int index, int count,
                                String icon, String title, String summary, View trailing, View.OnClickListener click) {
        MaterialCardView card = new MaterialCardView(c);
        card.setCardBackgroundColor(attr(c, com.google.android.material.R.attr.colorSurfaceContainerHigh));
        card.setCardElevation(0);
        card.setStrokeWidth(0);
        float big = dp(c, 24), small = dp(c, 6);
        float top = index == 0 ? big : small, bottom = index == count - 1 ? big : small;
        card.setShapeAppearanceModel(card.getShapeAppearanceModel().toBuilder()
                .setTopLeftCornerSize(top).setTopRightCornerSize(top)
                .setBottomLeftCornerSize(bottom).setBottomRightCornerSize(bottom).build());

        LinearLayout h = new LinearLayout(c);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(android.view.Gravity.CENTER_VERTICAL);
        h.setMinimumHeight(dp(c, 64));
        h.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));

        if (icon != null) {
            Badge ic = new Badge(c, icon, 40, attr(c, com.google.android.material.R.attr.colorSecondaryContainer),
                    attr(c, com.google.android.material.R.attr.colorOnSecondaryContainer));
            ((LinearLayout.LayoutParams) ic.getLayoutParams()).rightMargin = dp(c, 16);
            h.addView(ic);
        }

        LinearLayout texts = new LinearLayout(c);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(c);
        t.setText(title);
        t.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
        t.setTextColor(attr(c, com.google.android.material.R.attr.colorOnSurface));
        texts.addView(t);
        if (summary != null && !summary.isEmpty()) {
            TextView sm = new TextView(c);
            sm.setText(summary);
            sm.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
            sm.setTextColor(attr(c, com.google.android.material.R.attr.colorOnSurfaceVariant));
            texts.addView(sm);
        }
        h.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

        if (trailing != null) {
            android.view.ViewGroup.LayoutParams old = trailing.getLayoutParams();
            LinearLayout.LayoutParams tl = old != null
                    ? new LinearLayout.LayoutParams(old.width, old.height)   // 保留圖示自己指定的大小（IconView 沒有固有大小，wrap_content 會變 0）
                    : new LinearLayout.LayoutParams(-2, -2);
            tl.leftMargin = dp(c, 12);
            h.addView(trailing, tl);
        }
        LinearLayout outer = new LinearLayout(c);   // 垂直容器：上面是這一列，下面可用 addBelow 加額外內容
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.addView(h, new LinearLayout.LayoutParams(-1, -2));
        card.addView(outer);
        if (click != null) {
            card.setClickable(true);
            card.setOnClickListener(click);
            Anim.pressable(card, 0.98f);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = index == 0 ? 0 : dp(c, 2);
        parent.addView(card, lp);
        return card;
    }

    /** 在 row() 回傳的卡片下方加額外內容（例如色塊），縮排到跟文字對齊。 */
    static void addBelow(MaterialCardView card, View v) {
        LinearLayout outer = (LinearLayout) card.getChildAt(0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = dp(card.getContext(), 72);
        lp.bottomMargin = dp(card.getContext(), 16);
        outer.addView(v, lp);
    }

    /** 右側的小箭頭（點進去會開另一頁）。 */
    static View chevron(Context c) {
        IconView v = new IconView(c, "chevron", attr(c, com.google.android.material.R.attr.colorOnSurfaceVariant));
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 20), dp(c, 20)));
        return v;
    }

    /** 帶開關的一列：點整列也能切換。listener 只在使用者操作時收到新狀態。 */
    static MaterialSwitch switchRow(Context c, LinearLayout parent, int index, int count,
                                    String icon, String title, String summary, boolean checked,
                                    android.widget.CompoundButton.OnCheckedChangeListener l) {
        MaterialSwitch sw = new MaterialSwitch(c);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener(l);
        row(c, parent, index, count, icon, title, summary, sw, v -> sw.toggle());
        return sw;
    }

    /** 雙欄的功能卡片（圖示在淡色圓角底、標題、一行說明）。emph=true 用主色容器，當作重點。 */
    static final class Tile {
        final String icon, title, sub; final View.OnClickListener click; final boolean emph;
        Tile(String icon, String title, String sub, boolean emph, View.OnClickListener click) {
            this.icon = icon; this.title = title; this.sub = sub; this.emph = emph; this.click = click;
        }
    }

    static void tileGrid(Context c, LinearLayout parent, Tile... tiles) {
        for (int i = 0; i < tiles.length; i += 2) {
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int k = 0; k < 2; k++) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
                lp.setMargins(k == 0 ? 0 : dp(c, 6), 0, k == 0 ? dp(c, 6) : 0, 0);
                if (i + k < tiles.length) row.addView(tile(c, tiles[i + k]), lp);
                else row.addView(new View(c), lp);
            }
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
            rp.topMargin = dp(c, 12);
            parent.addView(row, rp);
        }
    }

    private static MaterialCardView tile(Context c, Tile t) {
        MaterialCardView card = new MaterialCardView(c);
        card.setCardBackgroundColor(attr(c, t.emph ? com.google.android.material.R.attr.colorPrimaryContainer
                : com.google.android.material.R.attr.colorSurfaceContainerHigh));
        card.setCardElevation(0);
        card.setStrokeWidth(0);
        card.setRadius(dp(c, 28));
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(dp(c, 20), dp(c, 20), dp(c, 20), dp(c, 20));
        Badge ic = new Badge(c, t.icon, 56,
                attr(c, t.emph ? com.google.android.material.R.attr.colorPrimary : com.google.android.material.R.attr.colorSecondaryContainer),
                attr(c, t.emph ? com.google.android.material.R.attr.colorOnPrimary : com.google.android.material.R.attr.colorOnSecondaryContainer));
        v.addView(ic);
        int on = attr(c, t.emph ? com.google.android.material.R.attr.colorOnPrimaryContainer : com.google.android.material.R.attr.colorOnSurface);
        TextView tt = new TextView(c);
        tt.setText(t.title);
        tt.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleLarge);
        tt.setTextColor(on);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.topMargin = dp(c, 28);
        v.addView(tt, tl);
        TextView st = new TextView(c);
        st.setText(t.sub);
        st.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        st.setTextColor(attr(c, t.emph ? com.google.android.material.R.attr.colorOnPrimaryContainer : com.google.android.material.R.attr.colorOnSurfaceVariant));
        v.addView(st);
        card.addView(v);
        card.setClickable(true);
        card.setOnClickListener(t.click);
        Anim.pressable(card, 0.95f);
        return card;
    }

    /** 可收合的一組：標題列（圖示、標題、摘要、箭頭）點一下展開／收起；回傳內容區，rows 要加進去。 */
    static LinearLayout expandGroup(Context c, LinearLayout parent, String icon, String title, String summary, boolean open) {
        LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, dp(c, 2), 0, 0);
        body.setVisibility(open ? View.VISIBLE : View.GONE);
        LinearLayout wrap = new LinearLayout(c);
        wrap.setOrientation(LinearLayout.VERTICAL);
        IconView arrow = new IconView(c, "chevron_down", attr(c, com.google.android.material.R.attr.colorOnSurfaceVariant));
        arrow.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 22), dp(c, 22)));
        arrow.setRotation(open ? 180f : 0f);
        final float dens = c.getResources().getDisplayMetrics().density;
        row(c, wrap, 0, 1, icon, title, summary, arrow, v -> {
            boolean show = body.getVisibility() != View.VISIBLE;
            Anim.spring(arrow, androidx.dynamicanimation.animation.DynamicAnimation.ROTATION, show ? 180f : 0f, 400f, 0.7f);
            Anim.expand(body, show, dens);
        });
        wrap.addView(body);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(c, 12);
        parent.addView(wrap, lp);
        return body;
    }

    /** 換大標題頂欄的標題。root 是 screen() 回傳的 View。 */
    static void setTitle(View root, String title) {
        ((com.google.android.material.appbar.CollapsingToolbarLayout) ((Object[]) root.getTag())[0]).setTitle(title);
    }

    static androidx.core.widget.NestedScrollView scroller(View root) {
        return (androidx.core.widget.NestedScrollView) ((Object[]) root.getTag())[1];
    }

    /** 圓角文字卡（說明、狀態、紀錄輸出）。回傳裡面的 TextView，之後 setText 即可。 */
    static TextView infoCard(Context c, LinearLayout parent, int sp, boolean mono, boolean selectable) {
        MaterialCardView card = new MaterialCardView(c);
        card.setCardBackgroundColor(attr(c, com.google.android.material.R.attr.colorSurfaceContainerHigh));
        card.setCardElevation(0);
        card.setStrokeWidth(0);
        card.setRadius(dp(c, 24));
        final MaterialCardView self = card;
        TextView t = new TextView(c) {   // 沒有文字時整張卡片隱藏，免得留一塊空白
            @Override
            public void setText(CharSequence text, BufferType type) {
                super.setText(text, type);
                self.setVisibility(text == null || text.length() == 0 ? View.GONE : View.VISIBLE);
            }
        };
        card.setVisibility(View.GONE);
        t.setTextSize(sp);
        t.setTextColor(attr(c, com.google.android.material.R.attr.colorOnSurface));
        if (mono) t.setTypeface(android.graphics.Typeface.MONOSPACE);
        t.setTextIsSelectable(selectable);
        t.setLineSpacing(0, 1.15f);
        t.setPadding(dp(c, 20), dp(c, 16), dp(c, 20), dp(c, 16));
        card.addView(t);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(c, 12);
        parent.addView(card, lp);
        return t;
    }

    /** 膠囊按鈕：filled=實心主色，否則淡色底。 */
    static MaterialButton pill(Context c, String text, boolean filled, View.OnClickListener l) {
        MaterialButton b = filled ? button(c, text, l) : tonal(c, text, l);
        b.setCornerRadius(dp(c, 28));
        b.setMinHeight(dp(c, 52));
        Anim.pressable(b, 0.96f);
        return b;
    }

    /** 一排等寬按鈕。 */
    static LinearLayout buttons(Context c, LinearLayout parent, MaterialButton... bs) {
        LinearLayout h = new LinearLayout(c);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setBaselineAligned(false);
        for (int i = 0; i < bs.length; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);   // 同一排高度一致
            lp.setMargins(i == 0 ? 0 : dp(c, 4), dp(c, 12), i == bs.length - 1 ? 0 : dp(c, 4), 0);
            bs[i].setInsetTop(0);
            bs[i].setInsetBottom(0);
            h.addView(bs[i], lp);
        }
        parent.addView(h, new LinearLayout.LayoutParams(-1, -2));
        return h;
    }

    /** 主題色選擇（設定頁與教學第一頁共用）：莫奈開關在第一排，其餘 18 色分三排（第一排 5、第二排 7、第三排 6），每格等寬，不會被裁掉。 */
    static void themePicker(final android.app.Activity a, LinearLayout parent) {
        final android.content.SharedPreferences cfg = a.getSharedPreferences("cfg", android.content.Context.MODE_PRIVATE);
        final boolean monet = cfg.getBoolean("monet_on", true);
        final int cur = cfg.getInt("seed_idx", 0);
        LinearLayout inner = card(a, parent);
        LinearLayout grid = new LinearLayout(a);
        grid.setOrientation(LinearLayout.VERTICAL);
        int[] counts = {5, 7, 6};
        int k = 0;
        for (int r = 0; r < 3; r++) {
            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
            rl.topMargin = r == 0 ? 0 : dp(a, 8);
            grid.addView(row, rl);
            if (r == 0) {
                TextView mo = new TextView(a);
                mo.setText("莫奈");
                mo.setGravity(android.view.Gravity.CENTER);
                mo.setTextSize(12);
                mo.setTextColor(attr(a, monet ? com.google.android.material.R.attr.colorOnSecondaryContainer : com.google.android.material.R.attr.colorOnSurfaceVariant));
                android.graphics.drawable.GradientDrawable mg = new android.graphics.drawable.GradientDrawable();
                mg.setCornerRadius(dp(a, 18));
                mg.setColor(monet ? attr(a, com.google.android.material.R.attr.colorSecondaryContainer) : attr(a, com.google.android.material.R.attr.colorSurfaceContainerHighest));
                mo.setBackground(mg);
                mo.setOnClickListener(v -> {
                    cfg.edit().putBoolean("monet_on", true).apply();
                    a.recreate();
                });
                row.addView(mo, new LinearLayout.LayoutParams(0, dp(a, 36), 1f));
            }
            for (int n = 0; n < counts[r]; n++, k++) {
                final int idx = k;
                android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                gd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                gd.setColor(App.SEEDS[k]);
                if (!monet && k == cur) gd.setStroke(dp(a, 3), attr(a, com.google.android.material.R.attr.colorOnSurface));
                View dot = new View(a);
                dot.setBackground(gd);
                dot.setAlpha(monet ? 0.45f : 1f);
                dot.setContentDescription(App.SEED_NAMES[k]);
                dot.setOnClickListener(v -> {
                    cfg.edit().putInt("seed_idx", idx).putBoolean("monet_on", false).apply();   // 選顏色就代表不用莫奈
                    a.recreate();
                });
                // 每格等寬，裡面放一個正圓（不要讓圓被拉成橢圓）
                android.widget.FrameLayout cell = new android.widget.FrameLayout(a);
                cell.addView(dot, new android.widget.FrameLayout.LayoutParams(dp(a, 36), dp(a, 36), android.view.Gravity.CENTER));
                row.addView(cell, new LinearLayout.LayoutParams(0, dp(a, 36), 1f));
            }
        }
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(-1, -2);
        gl.topMargin = dp(a, 12);
        gl.bottomMargin = dp(a, 8);
        inner.addView(grid, gl);
    }

    static int attr(Context c, int attrRes) {
        TypedValue tv = new TypedValue();
        c.getTheme().resolveAttribute(attrRes, tv, true);
        return tv.data;
    }
}
