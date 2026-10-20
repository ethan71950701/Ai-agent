package com.phoneai.overlay;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;

/** 設定畫面：大標題頂欄、狀態摘要卡、分組的圖示列表（仿系統設定）；原始診斷資訊在「進階與診斷」子頁。 */
public class MainActivity extends Activity {
    private LinearLayout heroBox;
    private AskPanel ask;

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        ask.handleResult(req, res, data);
    }

    private void openAssistSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_VOICE_INPUT_SETTINGS));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private SharedPreferences cfg() { return getSharedPreferences("cfg", MODE_PRIVATE); }

    private LinearLayout box;
    private View root;
    private int tab;
    private View indicator;
    private GlassBar glass;
    private long dragT;
    private float dragX, dragV;
    private android.animation.ValueAnimator lensAnim;
    /** 透鏡強度淡入／淡出（按住拖動時 1，放開 0）。 */
    /** 底欄玻璃的一條滑桿：拖動即時寫入 cfg 並套用。 */
    private void glassSlider(LinearLayout g, String title, String hint, float min, float max, float value, String key) {
        LinearLayout inner = Ui.card(this, g);
        TextView t = Ui.body(this, title + "　" + String.format(java.util.Locale.US, "%.2f", value));
        t.setTextColor(Ui.attr(this, com.google.android.material.R.attr.colorOnSurface));
        inner.addView(t);
        inner.addView(Ui.body(this, hint));
        com.google.android.material.slider.Slider sl = new com.google.android.material.slider.Slider(this);
        sl.setValueFrom(min);
        sl.setValueTo(max);
        sl.setValue(Math.max(min, Math.min(max, value)));
        sl.addOnChangeListener((s, v, fromUser) -> {
            GlassPrefs.putFloat(this, key, v);
            t.setText(title + "　" + String.format(java.util.Locale.US, "%.2f", v));
        });
        inner.addView(sl, new LinearLayout.LayoutParams(-1, -2));
    }

    /** 底欄玻璃的一個開關。 */
    private void glassSwitch(LinearLayout g, int i, int n, String icon, String title, boolean on, String key) {
        Ui.switchRow(this, g, i, n, icon, title, null, on, (v, checked) -> GlassPrefs.putBool(this, key, checked));
    }

    /** 用手指的壓力決定底欄發光的強度（壓越重越亮）。Android 回報的壓力範圍因手機而異，這裡把 0.15～1.0 對應到 0～1。 */
    private void pressureGlow(android.view.MotionEvent e, View v, boolean smooth) {
        float p = e.getPressure();
        GlassBar.touchInfo = "壓力 " + String.format(java.util.Locale.US, "%.2f", p) + "、接觸面積 " + String.format(java.util.Locale.US, "%.2f", e.getSize());
        float target = (p - 0.15f) / 0.85f;
        if (smooth) animatePress(target, 160);
        else if (glass != null) glass.setPressGlow(target);
    }

    private android.animation.ValueAnimator pressAnim;

    /** 發光漸漸亮起／漸漸熄滅（不瞬間跳），避免短按時光一閃就沒了。 */
    private void animatePress(float to, long ms) {
        if (glass == null) return;
        if (pressAnim != null) pressAnim.cancel();
        pressAnim = android.animation.ValueAnimator.ofFloat(glass.getPressGlow(), to);
        pressAnim.setDuration(ms);
        pressAnim.addUpdateListener(a -> glass.setPressGlow((Float) a.getAnimatedValue()));
        pressAnim.start();
    }

    private boolean barDragging;
    private float barDownX;
    private int barDownIdx;

    /** 進入透鏡狀態：選中的按鈕放大、顯示折射。 */
    private void enterDrag(float ex, float min, float max) {
        if (barDragging) return;
        barDragging = true;
        Anim.cancel(indicator);
        Anim.spring(indicator, androidx.dynamicanimation.animation.DynamicAnimation.SCALE_X, 1.2f, 500f, 0.6f);
        Anim.spring(indicator, androidx.dynamicanimation.animation.DynamicAnimation.SCALE_Y, 1.4f, 500f, 0.6f);
        lensTo(1f);
        Anim.spring(indicator, androidx.dynamicanimation.animation.DynamicAnimation.TRANSLATION_X,
                Math.max(min, Math.min(max, ex - (barItemW - Ui.dp(this, 8)) / 2f)), 380f, 0.7f);
    }

    private void lensTo(float to) {
        if (!(indicator instanceof GlassBar)) return;
        final GlassBar lens = (GlassBar) indicator;
        if (lensAnim != null) lensAnim.cancel();
        lensAnim = android.animation.ValueAnimator.ofFloat(lens.getAmount(), to);
        lensAnim.setDuration(to > 0 ? 160 : 260);
        lensAnim.addUpdateListener(a -> {
            float v = (Float) a.getAnimatedValue();
            lens.setAmount(v);
            float bf = 1f - Math.max(0f, Math.min(1f, (v - GlassBar.REST) / (1f - GlassBar.REST)));   // 按住越用力，模糊越少
            lens.setBlurFactor(bf);
            if (glass != null) glass.setGlowAmount(Math.max(0f, (v - GlassBar.REST) / (1f - GlassBar.REST)));   // 放開回到 0，按住到 1
        });
        lensAnim.start();
    }

    private void kickGlass() { if (glass != null) glass.kick(1500); if (indicator instanceof GlassBar) ((GlassBar) indicator).kick(1500); }
    private View bar;
    private int barItemW, barPad;
    private int shown = -1;
    private static final String[] TAB_TITLE = {"PhoneAI", "功能", "設定"};
    private static final String[] TAB_ICON = {"home", "apps", "tune"};
    private static final String[] TAB_LABEL = {"首頁", "功能", "設定"};

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        myTheme = App.themeKey(this);
        tab = b == null ? 0 : b.getInt("tab", 0);
        LinearLayout[] c = new LinearLayout[1];
        root = Ui.screen(this, TAB_TITLE[tab], false, c);
        box = c[0];
        box.setPadding(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 120));   // 底部留空，才不會被懸浮底欄蓋住
        ask = new AskPanel(this, null);
        ((android.view.ViewGroup) root).setClipChildren(false);   // 玻璃按鈕按住放大後會超出底欄上下緣，上層不能裁掉
        ((android.view.ViewGroup) root).addView(floatingBar(), barParams());
        setContentView(root);
        showTab(tab);
        if (b == null && !cfg().getBoolean("onboarded", false)) startActivity(new Intent(this, OnboardingActivity.class));   // 第一次開啟：先看教學
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", tab);
    }

    private androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams barParams() {
        // 底欄寬度固定：最寬 360dp 並置中，橫向時不會拉成整條很長；螢幕較窄時左右各留 24dp
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int width = Math.min(screenW - Ui.dp(this, 48), Ui.dp(this, 360));
        androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams lp =
                new androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(width, Ui.dp(this, 68));
        lp.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL;
        lp.setMargins(0, 0, 0, Ui.dp(this, 16));
        return lp;
    }

    /** 懸浮底欄：浮在內容上方的圓角膠囊；選中的分頁有一個會滑動、拉伸的淡色圓底（彈簧動畫），往下捲時整條收起、往上捲再出現。 */
    private View floatingBar() {
        final int onS = Ui.attr(this, com.google.android.material.R.attr.colorOnSurface);
        final GlassBar card = new GlassBar(this, Ui.attr(this, com.google.android.material.R.attr.colorSurface),
                (Ui.attr(this, com.google.android.material.R.attr.colorSurfaceContainerHighest) & 0xFFFFFF) | 0x59000000,   // 玻璃上的淡淡底色
                (onS & 0xFFFFFF) | 0x40000000, Ui.dp(this, 34), false);
        card.setSource(root);
        glass = card;
        card.setGlowColor(Ui.attr(this, com.google.android.material.R.attr.colorPrimary));
        bar = card;
        final LensClip fl = new LensClip(this);
        // 選中的那顆：平常是半透明淡色圓底，按住拖動時變成放大、折射的液態玻璃透鏡
        final GlassBar lens = new GlassBar(this, Ui.attr(this, com.google.android.material.R.attr.colorSurface),
                (onS & 0xFFFFFF) | 0x40000000, (onS & 0xFFFFFF) | 0x55000000, Ui.dp(this, 30), true);
        lens.setSource(root);
        lens.setGlowColor(Ui.attr(this, com.google.android.material.R.attr.colorPrimary));
        lens.setParentBar(card);   // 透鏡下面用底欄的模糊背景
        lens.setClipChildren(false);
        indicator = lens;
        card.setClipChildren(false);
        fl.setClipChildren(false);
        barPad = Ui.dp(this, 8);
        // 同樣的圖示與文字畫兩層：下層是暗色（一般狀態），上層是主色，只畫在玻璃按鈕覆蓋的範圍內 → 玻璃底下的部分換色
        LinearLayout h = tabRow(Ui.attr(this, com.google.android.material.R.attr.colorOnSurfaceVariant));
        final LinearLayout h2 = tabRow(Ui.attr(this, com.google.android.material.R.attr.colorPrimary));
        fl.addView(h, new android.widget.FrameLayout.LayoutParams(-1, -1));
        fl.addView(h2, new android.widget.FrameLayout.LayoutParams(-1, -1));
        fl.addView(indicator, new android.widget.FrameLayout.LayoutParams(10, Ui.dp(this, 58), android.view.Gravity.CENTER_VERTICAL));   // 透鏡放在最上層，才會蓋住並放大底下的圖示
        fl.set(h2, indicator);
        lens.setIconSource(h2);   // 按住時放大的圖示：就是底欄那排主色圖示
        card.addView(fl, new android.widget.FrameLayout.LayoutParams(-1, -1));
        card.addOnLayoutChangeListener((v, l, t, r, bt, ol, ot, or, ob) -> {
            int w = (r - l - 2 * barPad) / 3;
            if (w > 0 && w != barItemW) {
                barItemW = w;
                android.view.ViewGroup.LayoutParams lp = indicator.getLayoutParams();
                lp.width = w - Ui.dp(this, 8);
                indicator.setLayoutParams(lp);
                indicator.setTranslationX(barPad + tab * w + Ui.dp(this, 4));
            }
        });
        // 點或在底欄上左右滑：圓底跟著手指走，放開後切到最近的分頁
        card.setClickable(true);
        // 短按：照原本的彈跳動畫切到該分頁；按住超過 140ms 或手指移動：進入透鏡（放大、折射），跟著手指走，放開再彈回定位
        final android.os.Handler holdLoop = new android.os.Handler(getMainLooper());
        final float slop = Ui.dp(this, 8);
        final Runnable[] hold = new Runnable[1];
        card.setOnTouchListener((v, e) -> {
            kickGlass();
            if (barItemW <= 0) return false;
            float ex = e.getX();
            float min = barPad + Ui.dp(this, 4), max = barPad + 2 * barItemW + Ui.dp(this, 4);
            int idx = Math.max(0, Math.min(2, (int) ((ex - barPad) / barItemW)));
            switch (e.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    pressureGlow(e, v, true);
                    dragT = 0;
                    dragV = 0;
                    barDragging = false;
                    barDownX = ex;
                    barDownIdx = idx;
                    if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
                    hold[0] = () -> enterDrag(ex, min, max);
                    holdLoop.postDelayed(hold[0], 140);
                    return true;
                case android.view.MotionEvent.ACTION_MOVE: {
                    pressureGlow(e, v, false);
                    if (!barDragging && Math.abs(ex - barDownX) > slop) {
                        holdLoop.removeCallbacks(hold[0]);
                        enterDrag(ex, min, max);
                    }
                    if (!barDragging) return true;
                    // 液體的形變：移動越快，按鈕沿移動方向被拉長、上下被壓扁
                    long nowMs = android.os.SystemClock.uptimeMillis();
                    if (dragT != 0 && nowMs > dragT) {
                        float vDp = Math.abs(ex - dragX) / getResources().getDisplayMetrics().density / (nowMs - dragT);
                        dragV = dragV * 0.7f + vDp * 0.3f;
                    }
                    dragX = ex;
                    dragT = nowMs;
                    float st = Math.min(0.3f, dragV * 0.3f);
                    Anim.cancel(indicator);
                    indicator.setScaleX(1.2f + st);
                    indicator.setScaleY(1.4f - st * 0.6f);
                    indicator.setTranslationX(Math.max(min, Math.min(max, ex - (barItemW - Ui.dp(this, 8)) / 2f)));
                    return true;
                }
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    holdLoop.removeCallbacks(hold[0]);
                    animatePress(0f, 320);
                    if (!barDragging) {   // 短按：切換分頁（showTab 會做彈跳的圓底與圖示動畫）
                        if (e.getActionMasked() == android.view.MotionEvent.ACTION_UP && barDownIdx != tab) showTab(barDownIdx);
                        return true;
                    }
                    barDragging = false;
                    Anim.spring(indicator, androidx.dynamicanimation.animation.DynamicAnimation.SCALE_Y, 1f, 400f, 0.6f);
                    lensTo(GlassBar.REST);
                    if (e.getActionMasked() == android.view.MotionEvent.ACTION_CANCEL || idx == tab) {
                        Anim.spring(indicator, androidx.dynamicanimation.animation.DynamicAnimation.SCALE_X, 1f, 400f, 0.6f);
                        Anim.spring(indicator, androidx.dynamicanimation.animation.DynamicAnimation.TRANSLATION_X, barPad + tab * barItemW + Ui.dp(this, 4), 400f, 0.65f);
                    } else {
                        showTab(idx);
                    }
                    return true;
                default:
                    return true;
            }
        });
        // 往下捲收起、往上捲出現
        androidx.core.widget.NestedScrollView sv = (androidx.core.widget.NestedScrollView) ((Object[]) root.getTag())[1];
        sv.setOnScrollChangeListener((androidx.core.widget.NestedScrollView.OnScrollChangeListener) (v, x, y, ox, oy) -> {
            kickGlass();
            int dy = y - oy;
            if (dy > 8) Anim.spring(bar, androidx.dynamicanimation.animation.DynamicAnimation.TRANSLATION_Y, Ui.dp(this, 110), 400f, 0.85f);
            else if (dy < -8 || y == 0) Anim.spring(bar, androidx.dynamicanimation.animation.DynamicAnimation.TRANSLATION_Y, 0f, 400f, 0.7f);
        });
        return card;
    }

    private LinearLayout tabRow(int color) {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setPadding(barPad, 0, barPad, 0);
        for (int i = 0; i < 3; i++) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(android.view.Gravity.CENTER);
            item.addView(new IconView(this, TAB_ICON[i], color), new LinearLayout.LayoutParams(Ui.dp(this, 22), Ui.dp(this, 22)));
            TextView lb = new TextView(this);
            lb.setText(TAB_LABEL[i]);
            lb.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelMedium);
            lb.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            lb.setTextColor(color);
            item.addView(lb);
            h.addView(item, new LinearLayout.LayoutParams(0, -1, 1f));
        }
        return h;
    }

    private void showTab(int i) {
        kickGlass();
        final int prev = shown;
        shown = i;
        tab = i;
        if (barItemW > 0) {   // 圓底滑到新位置：位移帶一點回彈，滑動中橫向拉長再縮回
            float x = barPad + i * barItemW + Ui.dp(this, 4);
            if (prev < 0) indicator.setTranslationX(x);
            else {
                Anim.spring(indicator, androidx.dynamicanimation.animation.DynamicAnimation.TRANSLATION_X, x, 380f, 0.62f);
                indicator.setScaleX(1.35f);
                Anim.spring(indicator, androidx.dynamicanimation.animation.DynamicAnimation.SCALE_X, 1f, 260f, 0.55f);
            }
        }
        ((com.google.android.material.appbar.CollapsingToolbarLayout) ((Object[]) root.getTag())[0]).setTitle(TAB_TITLE[i]);
        final float dir = prev < 0 ? 0f : (i > prev ? 1f : -1f);   // 新內容從切換的方向彈進來
        final int tabNow = i;
        final android.view.View av = ask.view();
        final Runnable swap = () -> {
            if (tabNow != tab) return;
            if (av.getParent() != null) ((android.view.ViewGroup) av.getParent()).removeView(av);
            box.removeAllViews();
            if (tabNow == 0) buildHome(); else if (tabNow == 1) buildFunctions(); else buildSettings();
            refresh();
            box.setAlpha(1f);
            Anim.cascade(box, 14, dir * Ui.dp(this, 36), Ui.dp(this, prev < 0 ? 24 : 12), 35);
            kickGlass();
        };
        if (prev < 0) swap.run();
        else box.animate().alpha(0f).setDuration(90).withEndAction(swap).start();   // 舊內容先淡出再換，不會「啪」一下消失
        Anim.spring(bar, androidx.dynamicanimation.animation.DynamicAnimation.TRANSLATION_Y, 0f, 400f, 0.7f);
    }

    private MaterialCardView hero;
    private String myTheme = "";

    private void buildHome() {
        hero = new MaterialCardView(this);
        hero.setCardElevation(0);
        hero.setStrokeWidth(0);
        hero.setRadius(Ui.dp(this, 32));
        heroBox = new LinearLayout(this);
        heroBox.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 20);
        heroBox.setPadding(p, p, p, Ui.dp(this, 14));
        hero.addView(heroBox);
        box.addView(hero, new LinearLayout.LayoutParams(-1, -2));

        Ui.section(this, box, "問問看");
        LinearLayout askBox = Ui.card(this, box);
        askBox.addView(ask.view());
    }

    /** 狀態小標籤（圖示＋文字）；沒完成的可以點進去設定。 */
    private View chip(String label, boolean ok, int on, View.OnClickListener fix) {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(android.view.Gravity.CENTER_VERTICAL);
        h.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setCornerRadius(Ui.dp(this, 20));
        g.setColor((on & 0xFFFFFF) | 0x26000000);
        h.setBackground(g);
        IconView ic = new IconView(this, ok ? "check_circle" : "plus", on);
        h.addView(ic, new LinearLayout.LayoutParams(Ui.dp(this, 20), Ui.dp(this, 20)));
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge);
        t.setTextColor(on);
        t.setSingleLine(true);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.leftMargin = Ui.dp(this, 8);
        h.addView(t, tl);
        if (!ok && fix != null) {
            h.setOnClickListener(fix);
            Anim.pressable(h, 0.95f);
        }
        return h;
    }

    private void buildFunctions() {
        heroBox = null;
        Ui.section(this, box, "資料");
        Ui.tileGrid(this, box,
                new Ui.Tile("note", "助理的記憶", "待辦與筆記", false, v -> startActivity(new Intent(this, MemoryActivity.class))),
                new Ui.Tile("clock", "任務過程", "AI 每一步做了什麼", false, v -> startActivity(new Intent(this, LogActivity.class))));
        Ui.section(this, box, "語音");
        Ui.tileGrid(this, box,
                new Ui.Tile("wave", "語音辨識", "SenseVoice 離線；下載與測試", true, v -> startActivity(new Intent(this, SherpaTestActivity.class))));
        Ui.section(this, box, "權限");
        Ui.tileGrid(this, box,
                new Ui.Tile("layers", "懸浮層", "顯示在其他應用程式上層", false, v -> startActivity(
                        new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())))),
                new Ui.Tile("bell", "通知", "允許通知", false, v -> requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1)),
                new Ui.Tile("sparkle", "預設數位助理", "長按電源鍵叫出", false, v -> openAssistSettings()));
        Ui.section(this, box, "維護（不用開 Termux）");
        Ui.tileGrid(this, box,
                new Ui.Tile("pin", "Claude API Key", "存到 Termux 的 ~/.claude_key", true, v -> askKey()),
                new Ui.Tile("download", "檢查並更新", "按一下：有新版本就自動更新", false, v -> startUpdate()),
                new Ui.Tile("power", "解除安裝 PhoneAI", "還原設定、刪除腳本與 Key，最後移除本 App", true, v -> Uninstaller.start(this)),
                new Ui.Tile("check_circle", "設定導覽", "逐步檢查 root、權限、Key", false, v -> startActivity(new Intent(this, GuideActivity.class))));
        Ui.section(this, box, "進階");
        Ui.tileGrid(this, box,
                new Ui.Tile("terminal", "進階與診斷", "版本、狀態、測試", false, v -> startActivity(new Intent(this, DiagActivity.class))),
                new Ui.Tile("sparkle", "使用教學", "再看一次介紹動畫", false, v -> startActivity(new Intent(this, OnboardingActivity.class))));
    }

    private void startUpdate() {
        Updater.start(this);
    }

    private void repairPerms() {
        android.widget.Toast.makeText(this, "修復中…", android.widget.Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            final String r = Maintenance.repairPermissions();
            runOnUiThread(() -> { android.widget.Toast.makeText(this, r, android.widget.Toast.LENGTH_LONG).show(); refresh(); });
        }).start();
    }

    private void askKey() {
        Maintenance.askKey(this, null);
    }

    private void buildSettings() {
        heroBox = null;
        LinearLayout g = Ui.expandGroup(this, box, "droplet", "外觀", "主題色與莫奈取色", true);
        Ui.themePicker(this, g);

        g = Ui.expandGroup(this, box, "sparkle", "底欄玻璃（進階）", "折射、模糊、放大與開關，拖動即時生效", false);
        glassSlider(g, "折射強度", "邊緣彎曲的程度", 0f, 2f, GlassPrefs.refr, GlassPrefs.K_REFR);
        glassSlider(g, "色散（紅藍錯位）", "邊緣的彩色光暈，0 = 沒有", 0f, 2f, GlassPrefs.chroma, GlassPrefs.K_CHROMA);
        glassSlider(g, "模糊程度（dp）", "底欄背景的模糊半徑", 0f, 24f, GlassPrefs.blur, GlassPrefs.K_BLUR);
        glassSlider(g, "圖示放大倍率", "按住時圖示放大的量（0.22 ≈ 1.22 倍）", 0f, 0.5f, GlassPrefs.zoom, GlassPrefs.K_ZOOM);
        glassSlider(g, "拖動歪斜量", "拖動時畫面被拖歪的程度", 0f, 2f, GlassPrefs.skew, GlassPrefs.K_SKEW);
        glassSwitch(g, 0, 5, "image", "按住時放大圖示", GlassPrefs.magnify, GlassPrefs.K_MAGNIFY);
        glassSwitch(g, 1, 5, "wave", "圖示經過折射（實驗）", GlassPrefs.iconRefr, GlassPrefs.K_ICON_REFR);
        glassSwitch(g, 2, 5, "target", "透鏡底下用底欄背景（會出現半透明方塊，先關）", GlassPrefs.lensBg, GlassPrefs.K_LENS_BG);
        glassSwitch(g, 3, 5, "sun", "拖動歪斜", GlassPrefs.skewOn, GlassPrefs.K_SKEW_ON);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, Ui.dp(this, 52));
        rl.topMargin = Ui.dp(this, 12);
        g.addView(Ui.pill(this, "恢復預設", false, v -> {
            GlassPrefs.reset(this);
            recreate();
        }), rl);

        g = Ui.expandGroup(this, box, "float", "呼叫方式", "浮動按鈕、電源鍵、預設助理", false);
        Ui.switchRow(this, g, 0, 3, "float", "浮動按鈕", "螢幕邊緣的 AI 圓鈕，可拖曳", cfg().getBoolean("float_on", false), (v, on) -> {
            cfg().edit().putBoolean("float_on", on).apply();
            RippleAccessibilityService.refreshFloat();
        });
        final MaterialSwitch pkb = Ui.switchRow(this, g, 1, 3, "power", "長按電源鍵叫出助理（小米用）",
                "需要 root。會把系統自己的長按設成「不做事」，關掉會還原。三星原生長按就能叫助理，請不要開", cfg().getBoolean("powerkey_on", false), null);
        final android.widget.CompoundButton.OnCheckedChangeListener[] pkl = new android.widget.CompoundButton.OnCheckedChangeListener[1];
        pkl[0] = (v, on) -> {
            final String cur2 = Settings.Global.getString(getContentResolver(), "power_button_long_press");
            if (on && cur2 != null && !cur2.equals("0") && !cur2.equals("1")) {   // 目前是別的值（例如三星的 103 = 原生語音助理）：先問，免得蓋掉原生長按
                new android.app.AlertDialog.Builder(this)
                        .setTitle("確定要開嗎？")
                        .setMessage("目前系統的長按電源鍵動作是 " + cur2 + "（三星的 103 是原生語音助理）。這項會把它蓋成「不做事」，關掉這項時我會還原成 " + cur2 + "。三星手機建議不要開。")
                        .setPositiveButton("還是要開", (d, w) -> applyPowerKey(true))
                        .setNegativeButton("取消", (d, w) -> {
                            pkb.setOnCheckedChangeListener(null);
                            pkb.setChecked(false);
                            pkb.setOnCheckedChangeListener(pkl[0]);
                        })
                        .setOnCancelListener(d -> {
                            pkb.setOnCheckedChangeListener(null);
                            pkb.setChecked(false);
                            pkb.setOnCheckedChangeListener(pkl[0]);
                        }).show();
                return;
            }
            applyPowerKey(on);
        };
        pkb.setOnCheckedChangeListener(pkl[0]);
        Ui.row(this, g, 2, 3, "sparkle", "預設數位助理", "到系統設定選擇（長按電源鍵叫出）", Ui.chevron(this), v -> openAssistSettings());

        g = Ui.expandGroup(this, box, "wave", "語音", "朗讀、系統語音視窗", false);
        Ui.switchRow(this, g, 0, 2, "window", "語音一律用系統視窗", "有些手機內建辨識不讓 App 直接用，會自動打開；想重測 App 內辨識就關掉",
                cfg().getBoolean("voice_sys", false), (v, on) -> cfg().edit().putBoolean("voice_sys", on).apply());
        Ui.switchRow(this, g, 1, 2, "volume", "朗讀結果", "AI 完成任務後念出來", cfg().getBoolean("tts_on", true),
                (v, on) -> cfg().edit().putBoolean("tts_on", on).apply());

        g = Ui.expandGroup(this, box, "sparkle", "執行任務時", "光暈、背景模式、自動修復、實驗", false);
        Ui.switchRow(this, g, 0, 4, "sun", "呼吸光暈", "任務執行中，螢幕四周顯示光暈", cfg().getBoolean("glow_on", true),
                (v, on) -> cfg().edit().putBoolean("glow_on", on).apply());
        Ui.switchRow(this, g, 1, 4, "monitor", "背景模式（實驗）", "任務在第二螢幕執行，不動目前的畫面", cfg().getBoolean("bg_on", false),
                (v, on) -> cfg().edit().putBoolean("bg_on", on).apply());
        Ui.switchRow(this, g, 2, 4, "shield", "無障礙自動修復", "被系統關掉時用 root 重新打開（只動 PhoneAI 自己的）", A11yGuard.enabled(this),
                (v, on) -> cfg().edit().putBoolean("a11y_guard", on).apply());
        Ui.switchRow(this, g, 3, 4, "flask", "省錢實驗：元件清單", "不截圖，改給 AI 看元件清單，用 Haiku 選編號點擊（卡住升級 Sonnet）。會把畫面文字送給 AI；背景模式不適用",
                cfg().getBoolean("tree_on", false), (v, on) -> cfg().edit().putBoolean("tree_on", on).apply());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!myTheme.equals(App.themeKey(this))) {   // 在教學或設定換了主題色：這一頁要跟著換（功能頁以前不會更新）
            recreate();
            return;
        }
        A11yGuard.ensure(this);
        refresh();
        kickGlass();
    }

    private void applyPowerKey(boolean on) {
        getSharedPreferences("cfg", MODE_PRIVATE).edit().putBoolean("powerkey_on", on).apply();
        setPowerLongPress(!on);   // 開：記下原本的值再設成 0（系統自己的長按要先關掉，否則電源選單／小愛會一起跳）；關：還原成原本的值
        RippleAccessibilityService.refreshPowerKey();
    }

    /** restore=false：記下原本的值，再把設定寫成 0；restore=true：還原成記下的原本值，沒有記錄（或原本就沒有這個設定）就刪除設定回到系統預設。用 root 執行。 */
    private void setPowerLongPress(boolean restore) {
        final android.content.SharedPreferences sp = getSharedPreferences("cfg", MODE_PRIVATE);
        final String cur = Settings.Global.getString(getContentResolver(), "power_button_long_press");
        if (!restore && !sp.contains("plp_orig")) sp.edit().putString("plp_orig", cur == null ? "" : cur).apply();   // "" = 原本沒有這個設定
        if (restore && !sp.contains("plp_orig")) {   // 沒有記錄原值：不要猜（刪除設定不一定等於原本的狀態，三星實測不是），請使用者到系統設定改回去
            android.widget.Toast.makeText(this, "沒有記錄原本的值，所以不動它。請到「設定」裡改回你要的長按電源鍵動作", android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        final String orig = sp.getString("plp_orig", "");
        final String cmd = restore
                ? (orig.isEmpty() ? "settings delete global power_button_long_press" : "settings put global power_button_long_press " + orig)
                : "settings put global power_button_long_press 0";
        new Thread(() -> {
            try {
                Process p = new ProcessBuilder(Root.su(), "-c", cmd).redirectErrorStream(true).start();
                p.getInputStream().readAllBytes();
                p.waitFor();
            } catch (Exception ignored) { }
            if (restore) sp.edit().remove("plp_orig").apply();
            runOnUiThread(this::refresh);
        }).start();
    }

    private void refresh() {
        if (heroBox == null) return;
        boolean ov = Settings.canDrawOverlays(this);
        boolean nt = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        boolean a11y = A11yGuard.listed(this);
        boolean term = checkSelfPermission(CommandSender.PERM) == PackageManager.PERMISSION_GRANTED;
        boolean ok = ov && a11y && term;
        int bg = Ui.attr(this, ok ? com.google.android.material.R.attr.colorPrimaryContainer : com.google.android.material.R.attr.colorErrorContainer);
        int on = Ui.attr(this, ok ? com.google.android.material.R.attr.colorOnPrimaryContainer : com.google.android.material.R.attr.colorOnErrorContainer);
        hero.setCardBackgroundColor(bg);
        heroBox.removeAllViews();
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(android.view.Gravity.CENTER_VERTICAL);
        Badge badge = new Badge(this, ok ? "check_circle" : "refresh", 56, (on & 0xFFFFFF) | 0x26000000, on);
        ((LinearLayout.LayoutParams) badge.getLayoutParams()).rightMargin = Ui.dp(this, 16);
        top.addView(badge);
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        TextView t1 = new TextView(this);
        t1.setText(ok ? "一切就緒" : "還有設定沒完成");
        t1.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleLarge);
        t1.setTextColor(on);
        tx.addView(t1);
        TextView t2 = new TextView(this);
        t2.setText(ok ? "助理可以使用" : "點下面的 ＋ 項目就能設定");
        t2.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        t2.setTextColor(on);
        tx.addView(t2);
        top.addView(tx, new LinearLayout.LayoutParams(0, -2, 1f));
        heroBox.addView(top);

        View[] chips = {
            chip("懸浮層", ov, on, v -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())))),
            chip("通知", nt, on, v -> requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1)),
            chip("無障礙", a11y, on, v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))),
            chip("Termux 權限", term, on, v -> repairPerms())};
        for (int i = 0; i < 4; i += 2) {
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            for (int k = 0; k < 2; k++) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
                lp.setMargins(k == 0 ? 0 : Ui.dp(this, 4), 0, k == 0 ? Ui.dp(this, 4) : 0, 0);
                r.addView(chips[i + k], lp);
            }
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
            rp.topMargin = Ui.dp(this, 10);
            heroBox.addView(r, rp);
        }
    }
}
