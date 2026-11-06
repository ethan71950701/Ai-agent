package com.phoneai.overlay;

import android.content.Context;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
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
        t.setTextIsSelectable(false);   // 不可選取：選取會把點擊吃掉，按列表時會按不到
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
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(attr(c, com.google.android.material.R.attr.colorPrimaryContainer)));
        b.setTextColor(attr(c, com.google.android.material.R.attr.colorOnPrimaryContainer));
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
        ctl.setExpandedTitleColor(attr(a, com.google.android.material.R.attr.colorPrimary));   // 大標題用主題的強調色
        ctl.setCollapsedTitleTextColor(attr(a, com.google.android.material.R.attr.colorOnSurface));
        ctl.setContentScrimColor(attr(a, com.google.android.material.R.attr.colorSurface));
        ctl.setExpandedTitleMarginStart(dp(a, 24));
        ctl.setExpandedTitleMarginBottom(dp(a, 20));
        com.google.android.material.appbar.MaterialToolbar tb = new com.google.android.material.appbar.MaterialToolbar(a);
        if (back) {
            tb.setNavigationIcon(R.drawable.ic_back);
            tb.setNavigationOnClickListener(v -> closeToTap(a));
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
            // 設定列的圖示：中性底、主題強調色（跟功能頁一致）
            Badge ic = new Badge(c, icon, 40, attr(c, com.google.android.material.R.attr.colorSurfaceContainerHighest),
                    attr(c, com.google.android.material.R.attr.colorPrimary));
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
            card.setOnClickListener(v -> { rememberTap(v); click.onClick(v); });
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
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);   // 高度填滿整列，同列的卡片才會等高
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
        // 一般卡片：圖示底是中性色，圖示用主題的強調色（跟參考的樣式一樣）；主要卡片照舊
        Badge ic = new Badge(c, t.icon, 56,
                attr(c, t.emph ? com.google.android.material.R.attr.colorPrimary : com.google.android.material.R.attr.colorSurfaceContainerHighest),
                attr(c, t.emph ? com.google.android.material.R.attr.colorOnPrimary : com.google.android.material.R.attr.colorPrimary));
        v.addView(ic);
        int on = attr(c, t.emph ? com.google.android.material.R.attr.colorOnPrimaryContainer : com.google.android.material.R.attr.colorOnSurface);
        TextView tt = new TextView(c);
        tt.setText(t.title);
        // 標題或說明太長的卡片字體縮小，讓同一列的卡片高度接近（短的維持原本大小）
        boolean longTitle = t.title.length() > 6;
        tt.setTextAppearance(longTitle ? com.google.android.material.R.style.TextAppearance_Material3_TitleMedium
                : com.google.android.material.R.style.TextAppearance_Material3_TitleLarge);
        tt.setTextColor(on);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.topMargin = dp(c, 28);
        v.addView(tt, tl);
        TextView st = new TextView(c);
        st.setText(t.sub);
        st.setTextAppearance(t.sub.length() > 12 ? com.google.android.material.R.style.TextAppearance_Material3_BodySmall
                : com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        st.setTextColor(attr(c, t.emph ? com.google.android.material.R.attr.colorOnPrimaryContainer : com.google.android.material.R.attr.colorOnSurfaceVariant));
        v.addView(st);
        card.addView(v);
        card.setClickable(true);
        card.setOnClickListener(c2 -> { rememberTap(c2); t.click.onClick(c2); });
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
            Anim.expandSmooth(body, show);
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
        int[] counts = {4, 5, 6};   // 15 個主色，莫奈格子放在第一排
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
                mo.setText(L.t("莫奈"));
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
                dot.setContentDescription(L.t(App.SEED_NAMES[k]));
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

    // ---- 子頁開啟動畫：從點下去的那一格「彈簧」長出來 ----
    private static float tapX = -1, tapY = -1;

    /** 記住使用者點的那一格（螢幕座標的中心），子頁從這裡彈出來。 */
    /** 圓角正方形的箭頭按鈕（原生 Material 風格，放在底部中間）：按下會縮一下，點了箭頭轉一圈。 */
    static View arrowFab(Context c, View.OnClickListener click) {
        MaterialCardView card = new MaterialCardView(c);
        card.setCardBackgroundColor(attr(c, com.google.android.material.R.attr.colorPrimary));
        card.setCardElevation(0);
        card.setStrokeWidth(0);
        card.setRadius(dp(c, 20));
        IconView arrow = new IconView(c, "chevron", attr(c, com.google.android.material.R.attr.colorOnPrimary));
        card.addView(arrow, new android.widget.FrameLayout.LayoutParams(dp(c, 28), dp(c, 28), android.view.Gravity.CENTER));
        card.setClickable(true);
        card.setOnClickListener(v -> {
            arrow.animate().rotationBy(360f).setDuration(420).start();
            click.onClick(v);
        });
        Anim.pressable(card, 0.9f);
        return card;
    }

    /** 一組內容一張一張「升起」：從下面 20dp 淡入滑上來（Material 的強調減速曲線），不用彈簧、不縮放，比彈出來穩。delay 是起始延遲（毫秒）。 */
    static void riseIn(View v, long delay) {
        Anim.boostFrames(v, 900 + delay, "升起");
        v.setAlpha(0f);
        v.setTranslationY(dp(v.getContext(), 20));
        v.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(320).setInterpolator(EMPHASIZED).start();
    }

    /**
     * 把「建 count 個項目」切成很多小段：每一幀只花約 2.5 毫秒建幾個，不會卡住展開動畫，內容也會邊展開邊陸續出現
     * （以前是一口氣建完，或等動畫結束才建，前者會頓一下、後者展開完要空一下）。build 在主執行緒執行；done 可以是 null。
     */
    static void chunked(final android.app.Activity a, final int count, final java.util.function.IntConsumer build, final Runnable done) {
        final android.view.Choreographer ch = android.view.Choreographer.getInstance();
        final int[] next = {0};
        final long startNs = System.nanoTime();
        ch.postFrameCallback(new android.view.Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                if (a.isFinishing() || a.isDestroyed()) return;
                if (System.nanoTime() - startNs < 90_000_000L) { ch.postFrameCallback(this); return; }   // 動畫剛起步的前約 0.09 秒不建（那時在建立繪圖層，最容易掉幀）
                long deadline = System.nanoTime() + 2_500_000L;
                do {   // 至少建一個；用完這一幀的時間預算就等下一幀
                    build.accept(next[0]++);
                } while (next[0] < count && System.nanoTime() < deadline);
                if (next[0] < count) ch.postFrameCallback(this);
                else if (done != null) done.run();
            }
        });
    }

    /** 示意圖第一次出現時彈一下（彈簧，有點過衝）。 */
    static void bounceIn(View v) {
        v.setScaleX(0.6f);
        v.setScaleY(0.6f);
        Anim.spring(v, androidx.dynamicanimation.animation.DynamicAnimation.SCALE_X, 1f, 260f, 0.62f);
        Anim.spring(v, androidx.dynamicanimation.animation.DynamicAnimation.SCALE_Y, 1f, 260f, 0.62f);
    }

    /** 點的那張卡片在螢幕上的位置與大小（打開頁面時從這裡長出來、關閉時縮回這裡）；開完會清掉，免得下次從別的地方開時用到舊的。 */
    /** 診斷用：最近一次展開動畫的幀數與卡頓（超過 24 毫秒的幀間隔）。 */
    static volatile String enterInfo = "";
    private static final java.util.ArrayDeque<String> ENTER_LOG = new java.util.ArrayDeque<>();
    private static float[] tapRect;
    private static android.graphics.Bitmap tapSnap;
    private static final java.util.WeakHashMap<android.app.Activity, android.graphics.Bitmap> EXIT_SNAP = new java.util.WeakHashMap<>();
    private static final java.util.WeakHashMap<android.app.Activity, float[]> EXIT_RECT = new java.util.WeakHashMap<>();

    static void rememberTap(View v) {
        tapSnap = null;
        try {   // 把被點的卡片拍成一張小圖：展開一開始看起來要跟這張卡片一模一樣（文字還在），再淡出
            if (v.getWidth() > 0 && v.getHeight() > 0) {
                android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(v.getWidth(), v.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
                v.draw(new android.graphics.Canvas(b));
                tapSnap = b;
            }
        } catch (Throwable ignored) { }
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        tapX = loc[0] + v.getWidth() / 2f;
        tapY = loc[1] + v.getHeight() / 2f;
        tapRect = new float[]{loc[0], loc[1], v.getWidth(), v.getHeight()};
    }

    private static final android.view.animation.PathInterpolator EMPHASIZED = new android.view.animation.PathInterpolator(0.05f, 0.7f, 0.1f, 1f);
    /** 平順的「慢進慢出」（跟 Android 的 FastOutSlowIn 一樣）：起步比官方強調曲線溫和，容器展開不會一下子衝出去。 */
    private static final android.view.animation.PathInterpolator SMOOTH_IO = new android.view.animation.PathInterpolator(0.4f, 0f, 0.2f, 1f);

    /**
     * 像桌面打開 App：整頁從點的那張卡片的位置和大小展開到全螢幕（圓角跟著變小），內容稍微慢一點淡入；
     * progress 0 = 縮在卡片上、1 = 全螢幕。關閉時反過來縮回去。需要視窗是透明的（AppTheme.Sub），底下的頁面才看得到。
     */
    private static void applyExpand(final View root, final float[] r, float p, final float corner) {
        float W = Math.max(1, root.getWidth()), H = Math.max(1, root.getHeight());
        float sx = r[2] / W, sy = r[3] / H;
        float k = 1f - p;   // 0 = 全螢幕
        root.setScaleX(sx + (1f - sx) * p);
        root.setScaleY(sy + (1f - sy) * p);
        root.setTranslationX(r[0] * k);
        root.setTranslationY(r[1] * k);
        root.setAlpha(1f);
        Object v = root.getTag(R_VEIL);   // 蓋在內容上的一層「卡片顏色」：剛開始只看到一個變大的卡片，內容晚一點才淡入（不要一開始就看到縮小的整個頁面）
        if (v instanceof View) {
            float x = Math.max(0f, Math.min(1f, (p - 0.15f) / 0.5f));
            ((View) v).setAlpha(1f - x * x * (3f - 2f * x));
            if (v instanceof Veil) {
                float y = Math.max(0f, Math.min(1f, p / 0.3f));
                ((Veil) v).setSnapAlpha(1f - y * y * (3f - 2f * y));   // 卡片上的文字在前 30% 淡出
            }
        }
        cornerRadius = corner * k / Math.max(0.05f, root.getScaleX());   // 圓角在縮放前的座標，除以縮放才會是畫面上的大小
        root.invalidateOutline();
    }

    private static float cornerRadius;
    private static final int R_VEIL = 0x7f0a0e58;   // View.setTag 用的鍵

    /** 蓋在頁面內容最上面的一層：卡片顏色＋被點卡片的小圖（小圖不跟著頁面變形，固定在容器左上角，隨展開淡出）。 */
    private static final class Veil extends View {
        private final android.graphics.Bitmap snap;
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);
        private float snapAlpha = 1f;

        Veil(android.content.Context c, android.graphics.Bitmap snap) {
            super(c);
            this.snap = snap;
        }

        void setSnapAlpha(float a) {
            snapAlpha = a;
            invalidate();
        }

        @Override
        protected void onDraw(android.graphics.Canvas canvas) {
            canvas.drawColor(attr(getContext(), com.google.android.material.R.attr.colorSurfaceContainerHigh));
            if (snap == null || snapAlpha <= 0.01f) return;
            View root = getRootView();
            float rsx = Math.max(0.01f, root.getScaleX()), rsy = Math.max(0.01f, root.getScaleY());
            canvas.save();
            canvas.scale(1f / rsx, 1f / rsy);   // 抵銷頁面被壓扁／放大的比例，小圖才不會變形
            paint.setAlpha(Math.round(255 * snapAlpha));
            canvas.drawBitmap(snap, 0f, 0f, paint);
            canvas.restore();
        }
    }

    private static void installVeil(android.app.Activity a, View root, float alpha, android.graphics.Bitmap snap) {
        ViewGroup content = a.findViewById(android.R.id.content);
        if (content == null) return;
        Veil veil = new Veil(a, snap);
        veil.setAlpha(alpha);
        content.addView(veil, new ViewGroup.LayoutParams(-1, -1));
        root.setTag(R_VEIL, veil);
    }

    /** 動畫結束：蓋層只隱藏、留在原處（INVISIBLE 不會重新排版；removeView／addView 都會讓整頁重排，卡在動畫的第一幀或最後一幀）。返回時直接拿來用。 */
    private static void removeVeil(final View root) {
        Object v = root.getTag(R_VEIL);
        if (v instanceof View) ((View) v).setVisibility(View.INVISIBLE);
    }

    private static void setupClip(View root) {
        root.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), cornerRadius); }
        });
        root.setClipToOutline(true);
    }

    /** 關閉：縮回打開時那張卡片再結束；沒有記錄（不是從卡片打開的）就淡出結束。 */
    static void closeToTap(final android.app.Activity a) {
        final View root = a.getWindow().getDecorView();
        final float[] r = EXIT_RECT.get(a);
        if (r == null || root.getWidth() == 0) {
            root.animate().alpha(0f).setDuration(160).withEndAction(() -> { a.finish(); a.overridePendingTransition(0, 0); }).start();
            return;
        }
        final float corner = dp(a, 22);
        root.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        setupClip(root);
        Anim.boostFrames(root, 700, "子頁返回");
        Object old = root.getTag(R_VEIL);
        if (old instanceof View) {   // 打開時放好的蓋層直接拿來用，不用臨時 addView（會讓整頁重新排版）
            ((View) old).setAlpha(0f);
            ((View) old).setVisibility(View.VISIBLE);
        } else {
            installVeil(a, root, 0f, EXIT_SNAP.get(a));
        }   // 縮回去的時候，內容淡成卡片顏色，卡片上的文字再淡入
        android.animation.ValueAnimator va = android.animation.ValueAnimator.ofFloat(root.getScaleX() >= 0.999f ? 1f : 0f, 0f);
        va.setDuration(340);
        va.setInterpolator(SMOOTH_IO);
        va.addUpdateListener(an -> applyExpand(root, r, (float) an.getAnimatedValue(), corner));
        va.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator an) { a.finish(); a.overridePendingTransition(0, 0); }
        });
        va.start();
    }

    /** 子頁 onCreate 最後呼叫：整個畫面從點擊的位置用彈簧放大出來，不做系統的左右滑動。 */
    static void enterSpring(final android.app.Activity a) {
        a.overridePendingTransition(0, 0);
        final View root = a.getWindow().getDecorView();
        final float[] rect = tapRect;
        tapRect = null;
        if (rect != null) {   // 從卡片展開（像桌面打開 App）
            EXIT_RECT.put(a, rect);
            final android.graphics.Bitmap snap = tapSnap;
            tapSnap = null;
            if (snap != null) EXIT_SNAP.put(a, snap);
            installVeil(a, root, 1f, snap);
            root.setAlpha(0f);
            root.setPivotX(0f);
            root.setPivotY(0f);
            root.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    root.getViewTreeObserver().removeOnPreDrawListener(this);
                    final float corner = dp(a, 22);
                    setupClip(root);
                    root.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                    applyExpand(root, rect, 0f, corner);
                    android.animation.ValueAnimator va = android.animation.ValueAnimator.ofFloat(0f, 1f);
                    va.setDuration(480);
                    va.setInterpolator(SMOOTH_IO);
                    final long[] st = {0, 0, 0, 0, 0};   // 上一幀時間、最長間隔、超過 24 毫秒的次數、總幀數、最長間隔發生在進度的幾 %
                    va.addUpdateListener(an -> {
                        applyExpand(root, rect, (float) an.getAnimatedValue(), corner);
                        long now = android.os.SystemClock.uptimeMillis();
                        if (st[0] != 0) { long gap = now - st[0]; if (gap > st[1]) { st[1] = gap; st[4] = Math.round(an.getAnimatedFraction() * 100); } if (gap > 24) st[2]++; }
                        st[0] = now;
                        st[3]++;
                    });
                    va.addListener(new android.animation.AnimatorListenerAdapter() {
                        @Override public void onAnimationEnd(android.animation.Animator an) {
                            ENTER_LOG.addFirst(a.getClass().getSimpleName() + "：" + st[3] + " 幀，最長間隔 " + st[1] + " 毫秒（在進度 " + st[4] + "%），超過 24 毫秒 " + st[2] + " 次");
                            while (ENTER_LOG.size() > 5) ENTER_LOG.removeLast();   // 留最近 5 次，診斷頁一起看
                            enterInfo = android.text.TextUtils.join("\n　　", ENTER_LOG);
                            applyExpand(root, rect, 1f, corner);
                            removeVeil(root);
                            root.postDelayed(() -> {   // 結束那一幀不要同時做這麼多事：圓角裁切與繪圖層晚一點才拆
                                root.setClipToOutline(false);
                                root.setLayerType(View.LAYER_TYPE_NONE, null);
                            }, 250);
                        }
                    });
                    // 先讓第一次繪製（建立繪圖層、畫出第一幀）完成再開始跑時間：頁面很重時前一兩幀會卡，動畫時間照跑的話，卡完會直接跳到後面
                    Anim.boostFrames(root, 900, "子頁展開");
                    root.post(() -> root.postOnAnimation(va::start));
                    return true;
                }
            });
            registerBack(a, root);
            return;
        }
        // 先在第一次繪製前就設成縮小、透明，避免先看到整頁閃一下再縮小
        root.setAlpha(0f);
        root.setScaleX(0.92f);
        root.setScaleY(0.92f);
        root.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                root.getViewTreeObserver().removeOnPreDrawListener(this);
                int[] loc = new int[2];
                root.getLocationOnScreen(loc);
                float px = tapX < 0 ? root.getWidth() / 2f : tapX - loc[0];
                float py = tapY < 0 ? root.getHeight() / 2f : tapY - loc[1];
                root.setPivotX(px);
                root.setPivotY(py);
                // 動畫期間整頁畫在獨立的 GPU 層：縮放、淡入只動這一層，不用每幀重畫裡面的內容；結束後拿掉
                root.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                // 沒有點的卡片位置時的備用動畫：整頁從 0.92 倍淡入放大到原大小，用 Material 的強調減速曲線（不用彈簧，才不會像突然冒出來再彈一下）
                root.animate().alpha(1f).setDuration(200).setInterpolator(EMPHASIZED).start();
                root.animate().scaleX(1f).scaleY(1f).setDuration(400).setInterpolator(EMPHASIZED)
                        .withEndAction(() -> root.setLayerType(View.LAYER_TYPE_NONE, null)).start();
                return true;
            }
        });
        registerBack(a, root);
    }

    /**
     * 系統的返回手勢：子頁面用預測返回時，頁面跟著手指縮小、變淡（預覽），放手就回到原來的大小或真的關閉。
     * Android 14 以上才有逐步的進度；Android 13 只註冊一般返回，行為同以前。
     */
    private static void registerBack(android.app.Activity a, View root) {
        android.window.OnBackInvokedDispatcher d = a.getOnBackInvokedDispatcher();
        int prio = android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT;
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            d.registerOnBackInvokedCallback(prio, new android.window.OnBackAnimationCallback() {
                @Override
                public void onBackStarted(android.window.BackEvent e) {
                    root.setLayerType(View.LAYER_TYPE_HARDWARE, null);   // 返回時整頁畫在獨立的層上，避免多層疊加出現重影
                }

                @Override
                public void onBackProgressed(android.window.BackEvent e) {
                    // 只淡出，不縮放：縮放加淡出時會跟系統的預覽重疊，看起來像重影
                    root.setAlpha(Math.max(0f, 1f - 1.3f * e.getProgress()));
                }

                @Override
                public void onBackCancelled() {
                    root.animate().alpha(1f).setDuration(120).withEndAction(() -> root.setLayerType(View.LAYER_TYPE_NONE, null)).start();
                }

                @Override
                public void onBackInvoked() {
                    root.setAlpha(1f);
                    closeToTap(a);
                }
            });
        } else {
            d.registerOnBackInvokedCallback(prio, () -> {
                a.finish();
                a.overridePendingTransition(0, 0);
            });
        }
    }

    static int attr(Context c, int attrRes) {
        TypedValue tv = new TypedValue();
        c.getTheme().resolveAttribute(attrRes, tv, true);
        return tv.data;
    }
}
