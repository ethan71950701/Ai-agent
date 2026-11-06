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
    private float dragX, dragV, lastVel, accExt, stretchD, stretchV, pressP;
    private long lastTick;
    private boolean stretching;
    private float lensCurX, lensTargetX;   // 透鏡目前位置與手指目標位置（見 startStretch）
    /** 形變的門檻：加速度低於 ACC_DEAD（dp/ms²）、速度低於 VEL_DEAD（dp/ms）的抖動不形變，免得輕微碰一下畫面就抖。數字可依實測調整。 */
    private static final float ACC_DEAD = 0.004f, VEL_DEAD = 0.02f;

    /** 按住時每一幀更新透鏡與底欄的縮放：按住放大逐漸到位（pressP），再加上由手指加速度驅動的彈簧阻尼形變（d）。 */
    /** 果凍形變：d 越大（加速度越大）透鏡越往移動的方向拉長、上下壓扁；強度跟著「拖動形變強度」滑桿（預設 2 ＝ 係數 1）。 */
    private void applyJelly(float d) {
        if (!(indicator instanceof GlassBar)) return;
        float k = GlassPrefs.stretch / 2f;
        float jx = Math.max(0.8f, Math.min(1.7f, 1f + 0.16f * k * d));
        float jy = Math.max(0.65f, Math.min(1.15f, 1f - 0.10f * k * d));
        ((GlassBar) indicator).setJelly(jx, jy);
    }

    private void startStretch() {
        if (stretching) return;
        stretching = true;
        stretchD = 0;
        stretchV = 0;
        pressP = 0;
        lensCurX = indicator.getTranslationX();   // 從目前位置開始，之後每一幀平滑追向手指
        lensTargetX = lensCurX;
        lastTick = android.os.SystemClock.uptimeMillis();
        indicator.postOnAnimation(new Runnable() {
            @Override
            public void run() {
                if (!barDragging) {   // 手指放開：形變量平順衰減到 0 再結束（以前直接結束，形變可能卡在非 0，靜止的透鏡就一直是歪的）
                    stretchD *= 0.85f;
                    stretchV = 0f;
                    if (indicator instanceof GlassBar) {
                        ((GlassBar) indicator).setStretch(0.25f * stretchD * GlassPrefs.stretch, -0.2f * stretchD * GlassPrefs.stretch);
                        applyJelly(stretchD);
                        ((GlassBar) indicator).kick(200);
                    }
                    if (Math.abs(stretchD) < 0.003f) {
                        if (indicator instanceof GlassBar) { ((GlassBar) indicator).setStretch(0f, 0f); ((GlassBar) indicator).setJelly(1f, 1f); }
                        stretching = false;
                        return;
                    }
                    indicator.postOnAnimation(this);
                    return;
                }
                long now = android.os.SystemClock.uptimeMillis();
                float dt = Math.min(0.033f, Math.max(0.001f, (now - lastTick) / 1000f));
                lastTick = now;
                lensCurX += (lensTargetX - lensCurX) * Math.min(1f, dt * 25f);   // 每一幀平滑靠近手指（觸控事件不同步，直接設定會一跳一跳）
                indicator.setTranslationX(lensCurX);
                pressP += (1f - pressP) * Math.min(1f, dt * 14f);
                float force = 9000f * accExt;                                  // 加速度越大，推動形變的力越大
                accExt *= (float) Math.pow(0.8, dt * 60);                       // 沒有新的移動事件，加速度很快歸零
                float k = 170f, c = 26f;                                        // 臨界阻尼（c ≈ 2√k）：不來回彈，不會一抽一抽
                stretchV += (force - k * stretchD - c * stretchV) * dt;
                stretchD += stretchV * dt;
                float d = Math.max(-0.5f, Math.min(1.4f, stretchD));
                // 按住放大照舊；形變不再縮放整個 View（那樣中間的圖示也會跟著變形），改由著色器只在透鏡邊緣形變
                indicator.setScaleX(1f + (GlassPrefs.pressX - 1f) * pressP);
                indicator.setScaleY(1f + (GlassPrefs.pressY - 1f) * pressP);
                if (indicator instanceof GlassBar) {
                    ((GlassBar) indicator).setStretch(0.25f * d * GlassPrefs.stretch, -0.2f * d * GlassPrefs.stretch);
                    applyJelly(d);
                    ((GlassBar) indicator).kick(200);
                }
                bar.setScaleX(1f + (GlassPrefs.barPress - 1f) * pressP);
                bar.setScaleY(1f + (GlassPrefs.barPress - 1f) * pressP);
                indicator.postOnAnimation(this);
            }
        });
    }
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
            if (glass != null) glass.kick(600);   // 拖動時底欄要即時重畫（其他頁平常是省電、不會自己重畫）
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
        GlassBar.touchInfo = L.t("壓力 ") + String.format(java.util.Locale.US, "%.2f", p) + L.t("、接觸面積 ") + String.format(java.util.Locale.US, "%.2f", e.getSize());
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
        startStretch();   // 按住放大＋加速度形變由這個彈簧阻尼模型每一幀更新
        lensTo(1f);
        Anim.spring(indicator, androidx.dynamicanimation.animation.DynamicAnimation.TRANSLATION_X,
                Math.max(min, Math.min(max, ex - (barItemW - Ui.dp(this, 24)) / 2f)), 380f, 0.7f);
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

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent e) {
        int act = e.getActionMasked();
        if (act == android.view.MotionEvent.ACTION_DOWN || act == android.view.MotionEvent.ACTION_UP) kickGlass();   // 點一下可能會展開、收合、換內容：底欄後面的畫面要跟著更新（現在用 GPU 錄，很便宜）
        return super.dispatchTouchEvent(e);
    }

    private void kickGlass() { if (glass != null) glass.kick(1500); if (indicator instanceof GlassBar) ((GlassBar) indicator).kick(1500); }
    private View bar;
    private int barItemW, barPad;
    private int shown = -1;
    private static final String[] TAB_TITLE = {"", "功能", "設定"};
    private static final String[] TAB_ICON = {"home", "apps", "tune"};
    private static final String[] TAB_LABEL = {"首頁", "功能", "設定"};

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        MemoryActivity.warm();   // 先在背景讀好記憶，點進去就不用等
        final boolean hadRoot = Root.knownRoot();
        new Thread(() -> {   // 背景判斷有沒有 root；跟畫面上畫的不一樣就重建（背景模式等只有 root 才顯示的項目）
            boolean r = Root.isRoot();
            if (r != hadRoot) runOnUiThread(this::recreate);
        }, "root-probe").start();
        myTheme = App.themeKey(this);
        tab = b == null ? 0 : b.getInt("tab", 0);
        LinearLayout[] c = new LinearLayout[1];
        root = Ui.screen(this, L.t(TAB_TITLE[tab]), false, c);
        box = c[0];
        box.setPadding(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 120));   // 底部留空，才不會被懸浮底欄蓋住
        ((android.view.ViewGroup) root).setClipChildren(false);   // 玻璃按鈕按住放大後會超出底欄上下緣，上層不能裁掉
        navBar = floatingBar();
        ((android.view.ViewGroup) root).addView(navBar, barParams());
        setContentView(root);
        root.getViewTreeObserver().addOnScrollChangedListener(this::kickGlass);   // 頁面捲動時，底欄的折射要跟著更新
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
                new androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(width, Ui.dp(this, 58));
        lp.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL;
        lp.setMargins(0, 0, 0, Ui.dp(this, 16));
        return lp;
    }

    /** 懸浮底欄：浮在內容上方的圓角膠囊；選中的分頁有一個會滑動、拉伸的淡色圓底（彈簧動畫），往下捲時整條收起、往上捲再出現。 */
    private View navBar;

    /** 底欄上下移動。 */
    private void moveNav(float to, float stiff, float damp) {
        Anim.spring(bar, androidx.dynamicanimation.animation.DynamicAnimation.TRANSLATION_Y, to, stiff, damp);
    }

    private View floatingBar() {
        final int onS = Ui.attr(this, com.google.android.material.R.attr.colorOnSurface);
        // 玻璃：半透明深色底（只透出一點點底下的文字）+ 淡淡的光面 + 細亮邊；折射與形變照舊
        final GlassBar card = new GlassBar(this, 0xFF23262A,   // 底欄是透明玻璃（像酷安），底色比之前亮一點
                0x00000000,
                0x44FFFFFF, Ui.dp(this, 29), false);
        card.setSource(root);   // 保留玻璃的折射與形變（底下的畫面經過玻璃折射）
        glass = card;
        card.setLive(tab == 0);
        card.setGlowColor(0xFFFFFFFF);   // 玻璃的光用中性白，不跟主題色
        bar = card;
        final LensClip fl = new LensClip(this);
        // 選中的那顆：平常是半透明淡色圓底，按住拖動時變成放大、折射的液態玻璃透鏡
        final GlassBar lens = new GlassBar(this, Ui.attr(this, com.google.android.material.R.attr.colorSurface),
                (onS & 0xFFFFFF) | 0x40000000, (onS & 0xFFFFFF) | 0x55000000, Ui.dp(this, 26), true);
        lens.setSource(root);   // 透鏡保留折射與形變
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
        fl.addView(indicator, new android.widget.FrameLayout.LayoutParams(10, Ui.dp(this, 52), android.view.Gravity.CENTER_VERTICAL));   // 透鏡放在最上層，才會蓋住並放大底下的圖示
        fl.set(h2, indicator);
        lens.setIconSource(h2);   // 按住時放大的圖示：就是底欄那排主色圖示
        card.addView(fl, new android.widget.FrameLayout.LayoutParams(-1, -1));
        card.addOnLayoutChangeListener((v, l, t, r, bt, ol, ot, or, ob) -> {
            int w = (r - l - 2 * barPad) / 3;
            if (w > 0 && w != barItemW) {
                barItemW = w;
                android.view.ViewGroup.LayoutParams lp = indicator.getLayoutParams();
                lp.width = w - Ui.dp(this, 24);   // 靜止時的圓底比按鈕窄，置中
                indicator.setLayoutParams(lp);
                indicator.setTranslationX(barPad + tab * w + Ui.dp(this, 12));
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
            float min = barPad + Ui.dp(this, 12), max = barPad + 2 * barItemW + Ui.dp(this, 12);
            int idx = Math.max(0, Math.min(2, (int) ((ex - barPad) / barItemW)));
            switch (e.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    pressureGlow(e, v, true);
                    dragT = 0;
                    dragV = 0;
                    lastVel = 0;
                    accExt = 0;
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
                    // 形變跟「加速度」有關：起步、急停、轉向時形變最大，等速移動時回復（彈簧阻尼模型見 startStretch）
                    long nowMs = android.os.SystemClock.uptimeMillis();
                    if (dragT != 0 && nowMs > dragT) {
                        float dtMs = nowMs - dragT;
                        float vel = (ex - dragX) / getResources().getDisplayMetrics().density / dtMs;   // dp/ms
                        dragV = dragV * 0.7f + vel * 0.3f;                                               // 平滑後的速度
                        float acc = (dragV - lastVel) / dtMs;                                            // 加速度 dp/ms²
                        lastVel = dragV;
                        float jerk = Math.max(0f, Math.abs(acc) - ACC_DEAD);                             // 扣掉手指微抖的加速度，輕微晃動不形變
                        if (Math.abs(dragV) < VEL_DEAD) jerk = 0f;                                       // 幾乎沒在移動時不形變
                        accExt = Math.min(0.3f, accExt * 0.5f + jerk * 0.5f);
                    }
                    dragX = ex;
                    dragT = nowMs;
                    Anim.cancel(indicator, androidx.dynamicanimation.animation.DynamicAnimation.TRANSLATION_X);   // 只停位移，放大的彈簧不能被取消
                    lensTargetX = Math.max(min, Math.min(max, ex - (barItemW - Ui.dp(this, 24)) / 2f));   // 目標位置；實際位置由每一幀的 startStretch 平滑追過去
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
                    Anim.spring(indicator, androidx.dynamicanimation.animation.DynamicAnimation.SCALE_X, 1f, 400f, 0.6f);
                    Anim.spring(indicator, androidx.dynamicanimation.animation.DynamicAnimation.SCALE_Y, 1f, 400f, 0.6f);
                    Anim.spring(bar, androidx.dynamicanimation.animation.DynamicAnimation.SCALE_X, 1f, 400f, 0.7f);
                    Anim.spring(bar, androidx.dynamicanimation.animation.DynamicAnimation.SCALE_Y, 1f, 400f, 0.7f);
                    lensTo(GlassBar.REST);
                    if (e.getActionMasked() == android.view.MotionEvent.ACTION_CANCEL || idx == tab) {
                        Anim.spring(indicator, androidx.dynamicanimation.animation.DynamicAnimation.TRANSLATION_X, barPad + tab * barItemW + Ui.dp(this, 12), 400f, 0.65f);
                    } else {
                        showTab(idx);
                    }
                    return true;
                default:
                    return true;
            }
        });
        androidx.core.widget.NestedScrollView sv = (androidx.core.widget.NestedScrollView) ((Object[]) root.getTag())[1];
        sv.setOnScrollChangeListener((androidx.core.widget.NestedScrollView.OnScrollChangeListener) (v, x, y, ox, oy) -> {
            kickGlass();   // 底欄不收起，只讓折射跟著底下的內容更新
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
            lb.setText(L.t(TAB_LABEL[i]));
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
        Anim.boostFrames(root, 900, "換分頁");   // 換分頁的動畫期間要高更新率
        final int prev = shown;
        shown = i;
        tab = i;
        if (glass != null) glass.setLive(i == 0);
        if (indicator instanceof GlassBar) ((GlassBar) indicator).setLive(i == 0);
        if (barItemW > 0) {   // 圓底滑到新位置：位移帶一點回彈，滑動中橫向拉長再縮回
            float x = barPad + i * barItemW + Ui.dp(this, 12);
            if (prev < 0) indicator.setTranslationX(x);
            else {
                Anim.spring(indicator, androidx.dynamicanimation.animation.DynamicAnimation.TRANSLATION_X, x, 380f, 0.62f);
                indicator.setScaleX(1.35f);
                Anim.spring(indicator, androidx.dynamicanimation.animation.DynamicAnimation.SCALE_X, 1f, 260f, 0.55f);
            }
        }
        animateTitle(L.t(TAB_TITLE[i]), prev < 0 ? 0 : (i > prev ? 1 : -1));
        final float dir = prev < 0 ? 0f : (i > prev ? 1f : -1f);   // 往右邊的分頁（編號變大）：新頁從右邊滑進來、舊頁往左滑出去
        if (prev < 0) showTabNow(i);
        else cascadeTo(i, dir);
        moveNav(0f, 400f, 0.7f);
    }

    private int tabGen;

    /** 第一次進來：直接建好，沒有動畫。 */
    private void showTabNow(final int tabNow) {
        prepareBox(tabNow);
        final int gen = ++tabGen;
        cascadeDir = 0f;
        if (tabNow == 0) { buildHome(); finishTab(tabNow, gen); }
        else runTabSteps(tabNow == 1 ? functionSteps() : settingsSteps(), tabNow, gen);
    }

    private void prepareBox(int tabNow) {
        removeHomeBar();
        box.setGravity(android.view.Gravity.TOP);
        box.setPadding(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, tabNow == 0 ? 0 : 120));   // 首頁不留底部空間
        setHomeScroll(tabNow != 0);
        box.removeAllViews();
    }

    private float cascadeDir;   // 目前這次換分頁的方向（1＝新頁從右邊來、-1＝從左邊來、0＝不動畫）
    private int cascadeIdx;     // 已經排進場動畫的元件數（決定每個元件晚幾毫秒開始）

    /**
     * 換分頁：舊頁面很快往反方向淡出；新頁面的每個元件「本來就在切換方向那一邊」（畫面外），
     * 從上到下一個接一個橫向滑進來。內容是一段一段在同一幀或後面每一幀建的，建好的元件馬上排進場，不會先出現再消失。
     */
    private void cascadeTo(final int tabNow, final float dir) {
        final int gen = ++tabGen;
        box.animate().cancel();
        box.animate().alpha(0f).translationX(-dir * Ui.dp(this, 32)).setDuration(110)
                .setInterpolator(new android.view.animation.PathInterpolator(0.3f, 0f, 0.8f, 0.15f))
                .withEndAction(() -> {
                    if (gen != tabGen) return;
                    prepareBox(tabNow);
                    box.setAlpha(1f);
                    box.setTranslationX(0f);
                    box.setTranslationY(0f);
                    cascadeDir = dir;
                    cascadeIdx = 0;
                    if (tabNow == 0) { buildHome(); cascadeFrom(0); finishTab(tabNow, gen); }
                    else runTabSteps(tabNow == 1 ? functionSteps() : settingsSteps(), tabNow, gen);
                }).start();
    }

    /** 從第 from 個元件起，每個元件從切換方向那一邊（畫面外）橫向滑進來，一個比一個晚一點開始（從上到下）。 */
    private void cascadeFrom(int from) {
        if (cascadeDir == 0f) return;
        final android.view.animation.PathInterpolator emph = new android.view.animation.PathInterpolator(0.05f, 0.7f, 0.1f, 1f);
        final float off = cascadeDir * getResources().getDisplayMetrics().widthPixels;
        for (int k = from; k < box.getChildCount(); k++) {
            View c = box.getChildAt(k);
            long delay = 45L * Math.min(cascadeIdx++, 10);
            c.setTranslationX(off);
            c.animate().translationX(0f).setStartDelay(delay).setDuration(420).setInterpolator(emph).start();
        }
        Anim.boostFrames(box, 900, "換分頁滑入");
    }

    /** 第一段在同一幀就建好，其餘每一幀跑一段；換分頁很快連按時（gen 變了）就放棄舊的。 */
    private void runTabSteps(final java.util.List<Runnable> steps, final int tabNow, final int gen) {
        final android.view.Choreographer ch = android.view.Choreographer.getInstance();
        final int[] next = {0};
        final Runnable once = () -> {
            int before = box.getChildCount();
            steps.get(next[0]++).run();
            cascadeFrom(before);
        };
        once.run();
        if (next[0] >= steps.size()) { finishTab(tabNow, gen); return; }
        ch.postFrameCallback(new android.view.Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                if (gen != tabGen || tabNow != tab || isFinishing() || isDestroyed()) return;
                once.run();
                if (next[0] < steps.size()) ch.postFrameCallback(this);
                else finishTab(tabNow, gen);
            }
        });
    }

    private void finishTab(final int tabNow, final int gen) {
        refresh();
        // 保險：進場動畫結束後，把每一項強制恢復為原位、完全顯示（避免動畫停在畫面外，內容看不見、點不到）
        box.postDelayed(() -> {
            if (gen != tabGen) return;
            box.setAlpha(1f);
            box.setTranslationX(0f);
            for (int k = 0; k < box.getChildCount(); k++) {
                View ch = box.getChildAt(k);
                ch.setAlpha(1f);
                ch.setTranslationX(0f);
                ch.setTranslationY(0f);
            }
        }, 1500);
        kickGlass();
    }

    /**
     * 左上方大標題換字：跟下面的元件同一套動作。舊的字很快往反方向淡出（約 0.11 秒，跟舊頁面一起），
     * 換字後新的字從切換方向那一邊（畫面外）橫向滑進來，排在所有元件前面（第一個）。
     * 動畫用整個 CollapsingToolbarLayout 的位移與透明度（它自己畫標題，改標題顏色或邊距在長距離位移時文字會被擠壞）。
     */
    private void animateTitle(final String text, final int dir) {
        final com.google.android.material.appbar.CollapsingToolbarLayout ctl = (com.google.android.material.appbar.CollapsingToolbarLayout) ((Object[]) root.getTag())[0];
        ctl.animate().cancel();
        if (dir == 0) { ctl.setTitle(text); ctl.setAlpha(1f); ctl.setTranslationX(0f); return; }
        final float screenW = getResources().getDisplayMetrics().widthPixels;
        ctl.animate().alpha(0f).translationX(-dir * Ui.dp(this, 32)).setDuration(110)
                .setInterpolator(new android.view.animation.PathInterpolator(0.3f, 0f, 0.8f, 0.15f))
                .withEndAction(() -> {
                    ctl.setTitle(text);
                    ctl.setAlpha(1f);
                    ctl.setTranslationX(dir * screenW);
                    ctl.animate().translationX(0f).setDuration(420)
                            .setInterpolator(new android.view.animation.PathInterpolator(0.05f, 0.7f, 0.1f, 1f)).start();
                }).start();
    }

    private LinearLayout hero;
    private String myTheme = "";

    private View homeBar;
    private WaveView barWave;
    private android.widget.EditText homeEt;
    private ActionButtonView homeAction;
    private SpeechCapture homeCapture;

    /** 首頁不可捲動：關掉捲動區與標題列的收合（其他分頁照常）。 */
    private void setHomeScroll(boolean on) {
        if (root == null) return;
        View bar = ((android.view.ViewGroup) root).getChildAt(0);
        if (bar instanceof com.google.android.material.appbar.AppBarLayout)
            for (int i = 0; i < ((com.google.android.material.appbar.AppBarLayout) bar).getChildCount(); i++) {
                View c = ((com.google.android.material.appbar.AppBarLayout) bar).getChildAt(i);
                if (c.getLayoutParams() instanceof com.google.android.material.appbar.AppBarLayout.LayoutParams) {
                    c.getLayoutParams().height = Ui.dp(this, on ? 116 : 56);   // 功能／設定頁的標題區縮小，首頁維持原樣
                    ((com.google.android.material.appbar.AppBarLayout.LayoutParams) c.getLayoutParams()).setScrollFlags(
                            on ? com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL
                               | com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED : 0);
                    c.requestLayout();
                }
            }
        if (box != null && box.getParent() instanceof androidx.core.widget.NestedScrollView) {
            androidx.core.widget.NestedScrollView sv = (androidx.core.widget.NestedScrollView) box.getParent();
            sv.setNestedScrollingEnabled(on);
            sv.setOverScrollMode(on ? View.OVER_SCROLL_IF_CONTENT_SCROLLS : View.OVER_SCROLL_NEVER);
        }
    }

    /** 首頁底部的膠囊：提示文字／輸入框＋右邊圓形按鈕（沒字＝麥克風，點了整條換成波開始聽；有字＝送出）。 */
    private void showHomeBar() {
        removeHomeBar();
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setCornerRadius(Ui.dp(this, 32));
        gd.setColor(Ui.attr(this, com.google.android.material.R.attr.colorSurfaceContainerHigh));
        bar.setBackground(gd);
        bar.setPadding(Ui.dp(this, 22), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        final android.widget.EditText et = new android.widget.EditText(this);
        homeEt = et;
        et.setHint(L.t("輸入或按右邊說話"));
        et.setSingleLine(true);
        et.setBackground(null);
        et.setTextColor(Ui.attr(this, com.google.android.material.R.attr.colorOnSurface));
        et.setHintTextColor(Ui.attr(this, com.google.android.material.R.attr.colorOnSurfaceVariant));
        et.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEND);
        final ActionButtonView action = new ActionButtonView(this);
        action.setAccent(Ui.attr(this, com.google.android.material.R.attr.colorPrimary));
        Runnable send = () -> {
            String t = et.getText().toString().trim();
            if (t.isEmpty()) return;
            et.setText("");
            et.clearFocus();
            homeAsk(t);
        };
        et.setOnEditorActionListener((v, id, e) -> { send.run(); return true; });
        et.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable e) {
                action.setMode(e.toString().trim().isEmpty() ? ActionButtonView.MIC : ActionButtonView.SEND);
            }
        });
        final android.widget.FrameLayout holder = new android.widget.FrameLayout(this);
        barWave = new WaveView(this);   // 語音模式：整條換成波（跟助理面板一樣）
        barWave.setIdle(0.5f);
        barWave.setVisibility(View.GONE);
        holder.addView(et, new android.widget.FrameLayout.LayoutParams(-1, -2, android.view.Gravity.CENTER_VERTICAL));
        holder.addView(barWave, new android.widget.FrameLayout.LayoutParams(-1, Ui.dp(this, 34), android.view.Gravity.CENTER_VERTICAL));
        bar.addView(holder, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1f));
        action.setOnClickListener(v -> {
            if (homeCapture != null) { homeCapture.stop(); return; }   // 正在聽：再按一下結束
            if (et.getText().toString().trim().isEmpty()) homeVoice(); else send.run();
        });
        homeAction = action;
        bar.addView(action, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        // 底部光暈 + 粒子：跟助理面板同一個 AuroraView（貼著底邊的光），放在最底層
        final AuroraView aurora = new AuroraView(this);
        aurora.setEnergy(0.2f);
        ((android.view.ViewGroup) root).addView(aurora, 0, new androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(-1, -1));
        root.post(() -> aurora.setGlowY(root.getHeight() - Ui.dp(this, 200)));   // 光暈提高，不被底欄擋住
        homeFx = new android.view.View[]{aurora};

        androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams lp =
                new androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(-1, -2);
        lp.gravity = android.view.Gravity.BOTTOM;
        lp.setMargins(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 96));   // 留在底部導覽列上方
        ((android.view.ViewGroup) root).addView(bar, lp);
        homeBar = bar;
    }

    /** 首頁直接回答：在問候語下面顯示回答（不開面板）。 */
    private void homeAsk(String t) {
        if (answerView == null) return;
        answerView.setVisibility(View.VISIBLE);
        answerView.setText(L.t("思考中…"));
        new Thread(() -> {
            String r = Brain.answer(this, t, new AgentLoop.Ui() {
                @Override public void hide() { }
                @Override public void status(String s) { }   // 進度由 Now Bar 顯示
                @Override public boolean confirm(String msg) { return Brain.confirm(MainActivity.this, msg); }
                @Override public String ask(String q) { return AssistantSheet.askUser(getApplicationContext(), q); }
            });
            final String fr = r == null ? "" : r;
            runOnUiThread(() -> {
                setWaves(WaveView.IDLE);
                if (answerView != null) answerView.setText(Md.render(fr));
                Notify.say(this, fr);
                // 操作手機時 App 已經退到背景，首頁的文字看不到：需要的話把回答卡片叫出來（跟助理面板一樣）
                if (AgentLoop.showCard) AnswerCard.show(this, fr);
            });
        }, "home-ask").start();
    }

    private TextView answerView;

    /** 首頁麥克風：在頁面上錄音（離線辨識），辨識出的文字直接回答。 */
    private void homeVoice() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 31);
            return;
        }
        if (!SherpaEngine.ready(this)) {
            android.widget.Toast.makeText(this, L.t("請先到引導頁下載語音辨識模型"), android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        if (answerView != null) { answerView.setVisibility(View.VISIBLE); answerView.setText(L.t("我在聽…")); }
        homeCapture = SpeechCapture.start(new SpeechCapture.Cb() {
            @Override public void onListening() { setWaves(WaveView.LISTENING); }
            @Override public void onLevel(float v) { if (barWave != null) barWave.setLevel(v); }
            @Override public void onDone(short[] pcm) {
                homeCapture = null;
                setWaves(WaveView.THINKING);
                if (pcm == null) { setWaves(WaveView.IDLE); showAnswer(L.t("沒聽到聲音，再按一次麥克風")); return; }
                new Thread(() -> {
                    String text = "";
                    try { text = SherpaEngine.recognize(MainActivity.this, SpeechCapture.toFloat(pcm), SpeechCapture.RATE); }
                    catch (Throwable e) { text = ""; }
                    final String t = text == null ? "" : text.trim();
                    runOnUiThread(() -> { if (t.isEmpty()) { setWaves(WaveView.IDLE); showAnswer(L.t("沒聽清楚，再按一次麥克風")); } else homeAsk(t); });
                }, "home-voice").start();
            }
            @Override public void onError(String msg) { homeCapture = null; setWaves(WaveView.IDLE); showAnswer(L.t("錄音失敗：") + msg); }
        });
    }

    private void setWaves(int st) {
        boolean voice = st != WaveView.IDLE;   // 聽或想的時候整條是波，平常是輸入框
        if (barWave != null) { barWave.setState(st); barWave.setVisibility(voice ? View.VISIBLE : View.GONE); }
        if (homeEt != null) homeEt.setVisibility(voice ? View.INVISIBLE : View.VISIBLE);
        if (homeAction != null && st == WaveView.LISTENING) homeAction.setMode(ActionButtonView.LISTENING);
        else if (homeAction != null && st == WaveView.IDLE) homeAction.setMode(homeEt != null && homeEt.getText().toString().trim().isEmpty() ? ActionButtonView.MIC : ActionButtonView.SEND);
    }

    private void showAnswer(String text) {
        if (answerView == null) return;
        answerView.setVisibility(View.VISIBLE);
        answerView.setText(text);
    }

    private android.view.View[] homeFx;

    private void removeHomeBar() {
        if (homeBar != null && homeBar.getParent() != null) ((android.view.ViewGroup) homeBar.getParent()).removeView(homeBar);
        homeBar = null;
        if (homeFx != null) for (android.view.View v : homeFx) if (v.getParent() != null) ((android.view.ViewGroup) v.getParent()).removeView(v);
        homeFx = null;
    }

    // 首頁問候語（中文原文，顯示時才 L.t）
    private static final String[] GREET_MORNING = {"早安，今天想做什麼？", "新的一天開始了", "早，先喝杯水吧", "太陽都起床了，你呢？", "先喝咖啡，再叫我"};
    private static final String[] GREET_DAY = {"午安，來杯咖啡？", "肚子餓了嗎？", "下午好，撐住", "去午休吧，我顧著"};
    private static final String[] GREET_EVENING = {"晚上好，辛苦了", "今天過得如何？", "要不要休息一下？", "下班了？我也想下班"};
    private static final String[] GREET_NIGHT = {"還沒睡嗎？", "夜深了，小聲點", "熬夜對身體不好喔", "月亮都睡了，你呢？"};
    private static final String[] GREET_ANY = {"嗨，有什麼我能幫的？", "說吧，我在聽", "今天想玩點什麼？", "叮！你的 AI 上線了", "我已經充飽電了", "今天不當機，保證", "要不要我幫你偷懶？", "手指累了就用說的", "許願吧，限手機內", "先說好，我不會煮飯"};

    private static final String[][] JOKES = {
            {"阿姨去醫院打針，醫生說「先深呼吸」，阿姨回什麼？", "我是小姐。"},
            {"狗會喵喵，貓會汪汪，雞會什麼？", "機會是留給準備好的人。"},
            {"為什麼雞要過馬路？", "因為他想去對面。"},
            {"煎蛋愛上了荷包蛋，拿吉他到樓下唱什麼歌？", "這是一首煎蛋的小情歌。"},
            {"牛牽到北京還是牛，那牛牽到北極是什麼？", "New Balance，牛被冷死。"},
            {"余光中覺得老婆煮晚餐很辛苦，說今天我來煮。這是什麼故事？", "這就是光中要煮的故事。"},
            {"麵包超人不小心扭到腳，結果變成什麼？", "牛角麵包超人。"},
            {"得了便宜還 Minecraft。", ""},   // 沒有答案：直接顯示一句
            {"為什麼手機老是愛睡覺？", "因為它每天都在待機。"},
            {"什麼東西越洗越髒？", "水。"},
            {"小明為什麼帶梯子去上學？", "因為他要上高中。"},
            {"什麼蛋不能吃？", "笨蛋。"},
            {"白蘿蔔和紅蘿蔔誰比較強？", "紅蘿蔔，因為它「紅」了。"},
            {"電腦為什麼不怕冷？", "因為它有 Windows。"},
    };

    private void buildHome() {
        box.setGravity(android.view.Gravity.CENTER);   // 標誌與問候語放在畫面中間偏上（下面留空間給輸入膠囊）
        box.setPadding(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 400));
        MarkView mark = new MarkView(this);
        LinearLayout.LayoutParams mkp = new LinearLayout.LayoutParams(Ui.dp(this, 210), Ui.dp(this, 118));
        mkp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        box.addView(mark, mkp);
        TextView greet = new TextView(this);
        int hr = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        String[] slot = hr >= 5 && hr < 11 ? GREET_MORNING : hr >= 11 && hr < 17 ? GREET_DAY : hr >= 17 && hr < 23 ? GREET_EVENING : GREET_NIGHT;
        java.util.Random rnd = new java.util.Random();
        // 大約一半是冷笑話、一半是一般問候；跟上一次不一樣（上一次存在設定裡，重開 App 也記得）
        String last = cfg().getString("last_greet", "");
        boolean joke = false;
        boolean smallText = false;
        int idx = 0;
        String key = "";
        for (int tries = 0; tries < 12; tries++) {
            joke = rnd.nextInt(2) == 0;
            if (joke) { idx = rnd.nextInt(JOKES.length); key = "J" + idx; if (L.t(JOKES[idx][0]).equals(JOKES[idx][0]) && L.en()) key = last; }   // 英文介面：沒有英文版的笑話不顯示（key 設成跟上次一樣，下一輪重抽）
            else { idx = rnd.nextInt(slot.length + GREET_ANY.length); key = "G" + (idx < slot.length ? slot[idx] : GREET_ANY[idx - slot.length]); }
            if (!key.equals(last)) break;
        }
        cfg().edit().putString("last_greet", key).apply();
        if (joke) {   // 冷笑話：點一下看答案，再點一下回到問題；答案留空 = 單句
            final String[] jk = JOKES[idx];
            if (jk[1].isEmpty()) {
                greet.setText(L.t(jk[0]));
            } else {
                final String q = L.t(jk[0]), an = L.t(jk[1]), hint = L.t("點一下看答案");
                final android.text.SpannableString qs = new android.text.SpannableString(q + "\n" + hint);
                qs.setSpan(new android.text.style.RelativeSizeSpan(0.4f), q.length() + 1, qs.length(), 0);
                greet.setText(qs);
                smallText = true;
                final boolean[] shown = {false};
                greet.setOnClickListener(v -> { shown[0] = !shown[0]; greet.setText(shown[0] ? an : qs); });
            }
        } else {
            // 逗號後換行，分成上下兩行（有逗號的才會分）
            greet.setText(L.t(idx < slot.length ? slot[idx] : GREET_ANY[idx - slot.length]).replaceFirst("[，,]\\s*", "\n"));
        }
        greet.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, smallText ? 30 : 40);
        greet.setTextColor(Ui.attr(this, com.google.android.material.R.attr.colorOnSurface));
        greet.setGravity(android.view.Gravity.CENTER);
        greet.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-1, -2);
        gp.topMargin = Ui.dp(this, 16);
        gp.bottomMargin = Ui.dp(this, 16);
        box.addView(greet, gp);
        answerView = new TextView(this);   // 回答顯示在問候語下面
        answerView.setVisibility(View.GONE);
        answerView.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
        answerView.setTextColor(Ui.attr(this, com.google.android.material.R.attr.colorOnSurface));
        answerView.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, -2);
        ap.bottomMargin = Ui.dp(this, 16);
        box.addView(answerView, ap);
        // 英雄區：用普通的 LinearLayout 加圓角背景（不用 MaterialCardView，避免它被撐高）
        hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 20);
        hero.setPadding(p, p, p, Ui.dp(this, 14));
        heroBox = hero;
        box.addView(hero, new LinearLayout.LayoutParams(-1, -2));

        showHomeBar();
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

    /** 功能頁分成幾段（每段一個區塊），換分頁時每一幀只建一段，不要一次建整頁（會掉幀）。 */
    private java.util.List<Runnable> functionSteps() {
        heroBox = null;
        final java.util.List<Runnable> steps = new java.util.ArrayList<>();
        steps.add(() -> {
            Ui.section(this, box, L.t("資料"));
            Ui.tileGrid(this, box,
                    new Ui.Tile("pin", L.t("AI 服務與 API Key"), L.t("選 Claude、Gemini、OpenAI 等；Key 只存在手機裡"), true, v -> AiServiceUi.open(this, this::recreate)),
                    new Ui.Tile("search", L.t("網頁搜尋"), TavilySearch.hasKey(this) ? L.t("已設定 Tavily Key（說「幫我查…」才搜尋）") : L.t("說「幫我查…」就能搜尋，任何 AI 都行；可設 Key 讓額度更穩"), false, v -> TavilySearch.openSettings(this, this::recreate)),
                    new Ui.Tile("pin", L.t("可信任的網站"), String.format(L.t("目前 %d 個；搜尋時 AI 會挑相關的先查"), TavilySearch.trusted(this).size()), false, v -> TavilySearch.editTrusted(this, this::recreate)),
                    new Ui.Tile("list", L.t("記住的步驟"), String.format(L.t("目前記住 %d 個任務（點進去看、刪除）"), Macros.count(this)), false, v -> MacrosUi.open(this, this::recreate)),
                    new Ui.Tile("flask", L.t("AI 比較測試"), L.t("同一批任務用不同 AI 各跑一遍，比成功率與花費"), false, v -> AbTestUi.open(this)),
                    new Ui.Tile("note", L.t("助理的記憶"), L.t("待辦與筆記"), false, v -> startActivity(new Intent(this, MemoryActivity.class))),
                    new Ui.Tile("note", L.t("通知整理"), NotifDigest.on(this) ? String.format(java.util.Locale.US, L.t("每天 %02d:%02d 整理通知"), NotifDigest.hour(this), NotifDigest.minute(this)) : L.t("定時整理通知，有日期的事一鍵加入"), false, v -> NotifDigest.openSettings(this, this::recreate)),
                    new Ui.Tile("clock", L.t("任務過程"), L.t("AI 每一步做了什麼"), false, v -> startActivity(new Intent(this, LogActivity.class))),
                    new Ui.Tile("transfer", L.t("互傳檔案"), L.t("在自己的裝置之間互傳檔案"), false, v -> startActivity(new Intent(this, TransferActivity.class))));
        });
        steps.add(() -> {
            Ui.section(this, box, L.t("維護"));
            Ui.tileGrid(this, box,
                    new Ui.Tile("download", L.t("檢查並更新"), L.t("按一下：有新版本就自動更新"), false, v -> startUpdate()),
                    new Ui.Tile("power", L.t("解除安裝"), L.t("還原設定、刪除 Key，最後移除本 App"), true, v -> Uninstaller.start(this)),
                    new Ui.Tile("check_circle", L.t("設定導覽"), L.t("逐步檢查權限、Key（root 選用）"), false, v -> startActivity(new Intent(this, GuideActivity.class))),
                    new Ui.Tile("sparkle", L.t("用量與上限"), L.t("限制 Claude 可用的 token 數：看用了多少，可設每日／每月上限"), false, v -> Usage.dialog(this)));
        });
        steps.add(() -> {
            Ui.section(this, box, L.t("進階"));
            Ui.tileGrid(this, box,
                    new Ui.Tile("terminal", L.t("進階與診斷"), L.t("版本、狀態、測試"), false, v -> startActivity(new Intent(this, DiagActivity.class))),
                    new Ui.Tile("note", L.t("指令一覽"), L.t("AI 和固定指令能做的事"), false, v -> startActivity(new Intent(this, CommandsActivity.class))),
                    new Ui.Tile("sparkle", L.t("功能介紹"), L.t("再看一次介紹動畫"), false, v -> startActivity(new Intent(this, OnboardingActivity.class))));
        });
        return steps;
    }

    private void buildFunctions() { for (Runnable r : functionSteps()) r.run(); }

    private void startUpdate() {
        Updater.start(this);
    }

    private void repairPerms() {
        android.widget.Toast.makeText(this, L.t("修復中…"), android.widget.Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            final String r = Maintenance.repairPermissions();
            runOnUiThread(() -> { android.widget.Toast.makeText(this, r, android.widget.Toast.LENGTH_LONG).show(); refresh(); });
        }).start();
    }

    /** 語言（可拉開的群組）：選了立刻切換，不用退出 App——重建目前畫面，所有文字用新語言重新產生。 */
    private void buildLanguageGroup() {
        final String[] values = {"auto", "zh", "en"};
        final String[] labels = {L.t("跟隨系統") + " (Auto)", "繁體中文", "English"};
        String cur = L.pref(this);
        int ci = Math.max(0, java.util.Arrays.asList(values).indexOf(cur));
        LinearLayout lg = Ui.expandGroup(this, box, "list", "Language / 語言", labels[ci], false);
        for (int k = 0; k < values.length; k++) {
            final String v = values[k];
            View check = k == ci ? new IconView(this, "check", Ui.attr(this, com.google.android.material.R.attr.colorPrimary)) : null;
            View trailing = check;
            if (check != null) trailing = wrapIcon(check);
            Ui.row(this, lg, k, values.length, null, labels[k], null, trailing, x -> {
                if (v.equals(L.pref(this))) return;
                L.setPref(this, v);
                recreate();
            });
        }
    }

    /** 操作手機完成後，要不要把回答卡片叫出來（面板在操作時已經收起）；聊天問答的回答永遠顯示在面板裡，不受影響。 */
    private void buildReplyCardGroup() {
        final String[] values = {"auto", "always", "never"};
        final String[] labels = {L.t("自動（回答較長或有搜尋才顯示）"), L.t("一律顯示"), L.t("一律不顯示（只用通知）")};
        String cur = cfg().getString("reply_card", "auto");
        int ci = Math.max(0, java.util.Arrays.asList(values).indexOf(cur));
        LinearLayout rg = Ui.expandGroup(this, box, "window", L.t("操作完成後的回答卡片"), labels[ci], false);
        for (int k = 0; k < values.length; k++) {
            final String v = values[k];
            View trailing = k == ci ? wrapIcon(new IconView(this, "check", Ui.attr(this, com.google.android.material.R.attr.colorPrimary))) : null;
            Ui.row(this, rg, k, values.length, null, labels[k], null, trailing, x -> {
                if (v.equals(cfg().getString("reply_card", "auto"))) return;
                cfg().edit().putString("reply_card", v).apply();
                recreate();
            });
        }
    }

    /** 選要保護的 App：AI 操作之前先問你；銀行、支付類不用勾，會自動保護。 */
    private void chooseProtectedApps() {
        // 讀 App 清單與名稱很慢（幾百個 App）：放在背景執行緒，讀好再顯示，不卡住畫面
        android.widget.Toast.makeText(this, L.t("讀取 App 清單…"), android.widget.Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            final java.util.List<String[]> apps = AppLabels.list(this);   // 名稱讀過的有快取，只讀新裝的
            final java.util.List<String> pkgs = new java.util.ArrayList<>();
            for (String[] a : apps) pkgs.add(a[0]);
            final String[] names = new String[pkgs.size()];
            final boolean[] checked = new boolean[pkgs.size()];
            final java.util.Set<String> chosen = ProtectedApps.userSet(this);
            for (int i = 0; i < pkgs.size(); i++) {
                names[i] = apps.get(i)[1];
                checked[i] = chosen.contains(pkgs.get(i));
            }
            runOnUiThread(() -> CheckDialog.show(this, L.t("選擇要保護的 App"), names, checked, () -> {
                java.util.Set<String> out = new java.util.HashSet<>();
                for (int i = 0; i < pkgs.size(); i++) if (checked[i]) out.add(pkgs.get(i));
                ProtectedApps.setUserSet(this, out);
            }));
        }, "protected-apps").start();
    }

    private View wrapIcon(View icon) {
        android.widget.FrameLayout f = new android.widget.FrameLayout(this);
        f.addView(icon, new android.widget.FrameLayout.LayoutParams(Ui.dp(this, 24), Ui.dp(this, 24), android.view.Gravity.CENTER));
        return f;
    }

    /** 設定頁分成幾段（每個可展開的群組一段），換分頁時每一幀只建一段。 */
    private java.util.List<Runnable> settingsSteps() {
        heroBox = null;
        final java.util.List<Runnable> steps = new java.util.ArrayList<>();
        steps.add(() -> {
            LinearLayout g = Ui.expandGroup(this, box, "droplet", L.t("外觀"), L.t("主題色與莫奈取色"), true);
            Ui.themePicker(this, g);
            buildLanguageGroup();
            buildReplyCardGroup();

        });
        steps.add(() -> {
            LinearLayout p = Ui.expandGroup(this, box, "shield", L.t("權限"), L.t("懸浮層、通知、預設數位助理"), false);
            Ui.row(this, p, 0, 3, "layers", L.t("懸浮層"), L.t("顯示在其他應用程式上層"), null, v -> startActivity(
                    new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))));
            Ui.row(this, p, 1, 3, "bell", L.t("通知"), L.t("允許通知"), null, v -> requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1));
            Ui.row(this, p, 2, 3, "sparkle", L.t("預設數位助理"), L.t("長按電源鍵叫出"), null, v -> openAssistSettings());

        });
        steps.add(() -> {
            LinearLayout g = Ui.expandGroup(this, box, "sparkle", L.t("底欄玻璃（進階）"), L.t("折射、模糊、放大與開關，拖動即時生效"), false);
            glassSlider(g, L.t("折射強度"), L.t("邊緣彎曲的程度"), 0f, 2f, GlassPrefs.refr, GlassPrefs.K_REFR);
            glassSlider(g, L.t("色散（只有上層透鏡）"), L.t("透鏡邊緣的彩色光邊，0 = 沒有"), 0f, 2f, GlassPrefs.chroma, GlassPrefs.K_CHROMA);
            glassSlider(g, L.t("模糊程度（dp）"), L.t("底欄背景的模糊半徑"), 0f, 24f, GlassPrefs.blur, GlassPrefs.K_BLUR);
            glassSlider(g, L.t("圖示放大倍率"), L.t("按住時圖示放大的量（預設 0.08 ≈ 1.08 倍）"), 0f, 0.5f, GlassPrefs.zoom, GlassPrefs.K_ZOOM);
            glassSlider(g, L.t("按住放大（左右）"), L.t("按住時上層按鈕橫向放大的倍數"), 1f, 1.6f, GlassPrefs.pressX, GlassPrefs.K_PRESS_X);
            glassSlider(g, L.t("按住放大（上下）"), L.t("按住時上層按鈕直向放大的倍數"), 1f, 1.8f, GlassPrefs.pressY, GlassPrefs.K_PRESS_Y);
            glassSlider(g, L.t("按住放大（整條底欄）"), L.t("按住時整條底欄放大的倍數，1 = 不放大"), 1f, 1.2f, GlassPrefs.barPress, GlassPrefs.K_BAR_PRESS);
            glassSlider(g, L.t("拖動歪斜量"), L.t("拖動時畫面被拖歪的程度"), 0f, 2f, GlassPrefs.skew, GlassPrefs.K_SKEW);
            glassSlider(g, L.t("拖動形變強度"), L.t("拖動時透鏡邊緣被拉扯的程度，0 = 沒有；預設 2"), 0f, 6f, GlassPrefs.stretch, GlassPrefs.K_STRETCH);
            glassSlider(g, L.t("底欄暗度"), L.t("0 = 幾乎透明，1 = 很暗"), 0f, 1f, GlassPrefs.barDark, GlassPrefs.K_BAR_DARK);
            glassSwitch(g, 0, 4, "image", L.t("按住時放大圖示"), GlassPrefs.magnify, GlassPrefs.K_MAGNIFY);
            glassSwitch(g, 1, 4, "sun", L.t("拖動歪斜"), GlassPrefs.skewOn, GlassPrefs.K_SKEW_ON);
            glassSwitch(g, 2, 4, "bolt", L.t("GPU 錄製背景"), GlassPrefs.gpu, GlassPrefs.K_GPU);
            glassSwitch(g, 3, 4, "image", L.t("圖示折射與色散"), GlassPrefs.iconRefr, GlassPrefs.K_ICON_REFR);
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, Ui.dp(this, 52));
            rl.topMargin = Ui.dp(this, 12);
            g.addView(Ui.pill(this, L.t("恢復預設"), false, v -> {
                GlassPrefs.reset(this);
                recreate();
            }), rl);

        });
        steps.add(() -> {
            LinearLayout g = Ui.expandGroup(this, box, "float", L.t("呼叫方式"), L.t("浮動按鈕、電源鍵、預設助理"), false);
            Ui.switchRow(this, g, 0, 3, "float", L.t("浮動按鈕"), L.t("螢幕邊緣的 AI 圓鈕，可拖曳"), cfg().getBoolean("float_on", false), (v, on) -> {
                cfg().edit().putBoolean("float_on", on).apply();
                RippleAccessibilityService.refreshFloat();
            });
            final MaterialSwitch pkb = Ui.switchRow(this, g, 1, 3, "power", L.t("長按電源鍵叫出助理（小米用）"),
                    L.t("需要 root。會把系統自己的長按設成「不做事」，關掉會還原。三星原生長按就能叫助理，請不要開"), cfg().getBoolean("powerkey_on", false), null);
            final android.widget.CompoundButton.OnCheckedChangeListener[] pkl = new android.widget.CompoundButton.OnCheckedChangeListener[1];
            pkl[0] = (v, on) -> {
                final String cur2 = Settings.Global.getString(getContentResolver(), "power_button_long_press");
                if (on && cur2 != null && !cur2.equals("0") && !cur2.equals("1")) {   // 目前是別的值（例如三星的 103 = 原生語音助理）：先問，免得蓋掉原生長按
                    new android.app.AlertDialog.Builder(this)
                            .setTitle(L.t("確定要開嗎？"))
                            .setMessage(L.t("目前系統的長按電源鍵動作是 ") + cur2 + L.t("（三星的 103 是原生語音助理）。這項會把它蓋成「不做事」，關掉這項時我會還原成 ") + cur2 + L.t("。三星手機建議不要開。"))
                            .setPositiveButton(L.t("還是要開"), (d, w) -> applyPowerKey(true))
                            .setNegativeButton(L.t("取消"), (d, w) -> {
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
            Ui.row(this, g, 2, 3, "sparkle", L.t("預設數位助理"), L.t("到系統設定選擇（長按電源鍵叫出）"), Ui.chevron(this), v -> openAssistSettings());

        });
        steps.add(() -> {
            LinearLayout g = Ui.expandGroup(this, box, "wave", L.t("語音"), L.t("朗讀、系統語音視窗"), false);
            Ui.switchRow(this, g, 0, 3, "window", L.t("語音一律用系統視窗"), L.t("有些手機內建辨識不讓 App 直接用，會自動打開；想重測 App 內辨識就關掉"),
                    cfg().getBoolean("voice_sys", false), (v, on) -> cfg().edit().putBoolean("voice_sys", on).apply());
            Ui.switchRow(this, g, 1, 3, "volume", L.t("朗讀結果"), L.t("AI 完成任務後念出來"), cfg().getBoolean("tts_on", true),
                    (v, on) -> cfg().edit().putBoolean("tts_on", on).apply());
            Ui.switchRow(this, g, 2, 3, "wave", L.t("開面板時自動聽"), L.t("叫出助理面板就開始聽；關掉就要自己點麥克風"), cfg().getBoolean("voice_auto", true),
                    (v, on) -> cfg().edit().putBoolean("voice_auto", on).apply());

        });
        steps.add(() -> {
            LinearLayout g = Ui.expandGroup(this, box, "sparkle", L.t("執行任務時"), L.t("光暈、背景模式、自動修復、實驗"), false);
            final boolean hasRoot = Root.knownRoot();   // 背景模式與無障礙自動修復都要 root：沒 root 就不顯示
            final int n = hasRoot ? 8 : 5;
            int k = 0;
            Ui.switchRow(this, g, k++, n, "sun", L.t("呼吸光暈"), L.t("任務執行中，螢幕四周顯示光暈"), cfg().getBoolean("glow_on", true),
                    (v, on) -> cfg().edit().putBoolean("glow_on", on).apply());
            if (hasRoot) Ui.switchRow(this, g, k++, n, "monitor", L.t("背景模式按鈕（實驗）"), L.t("在助理面板顯示背景模式按鈕。按鈕要自己按開，平常任務還是在目前的畫面做；開著時任務在隱形的第二螢幕執行，只用截圖模式（Sonnet 看圖，費用較高），銀行、支付類 App 不會在背景操作"),
                    cfg().getBoolean("bg_feature", true), (v, on) -> cfg().edit().putBoolean("bg_feature", on).apply());
            if (hasRoot) Ui.row(this, g, k++, n, "monitor", L.t("測試背景螢幕"), L.t("建立一次背景螢幕、開「設定」截圖，看畫面是不是正常"), Ui.chevron(this), v -> {
                android.widget.Toast.makeText(this, L.t("測試中…（約 10 秒）"), android.widget.Toast.LENGTH_SHORT).show();
                new Thread(() -> {
                    String r = BgDisplay.selfTest(this);
                    final android.graphics.Bitmap shot = BgDisplay.lastTestShot;
                    runOnUiThread(() -> {
                        com.google.android.material.dialog.MaterialAlertDialogBuilder b = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle(L.t("測試背景螢幕")).setPositiveButton(L.t("知道了"), null);
                        if (shot == null) b.setMessage(r);
                        else {   // 把背景螢幕截到的畫面一起顯示，才看得出它真的拍到了東西
                            android.widget.LinearLayout col = new android.widget.LinearLayout(this);
                            col.setOrientation(android.widget.LinearLayout.VERTICAL);
                            int pad = Ui.dp(this, 20);
                            col.setPadding(pad, pad / 2, pad, 0);
                            col.addView(Ui.body(this, r));
                            android.widget.ImageView iv = new android.widget.ImageView(this);
                            iv.setImageBitmap(shot);
                            iv.setAdjustViewBounds(true);
                            col.addView(iv, new android.widget.LinearLayout.LayoutParams(Ui.dp(this, 160), -2));
                            b.setView(col);
                        }
                        b.show();
                    });
                }).start();
            });
            if (hasRoot) Ui.switchRow(this, g, k++, n, "shield", L.t("無障礙自動修復"), L.t("被系統關掉時用 root 重新打開（只動 Vertex 自己的）"), A11yGuard.enabled(this),
                    (v, on) -> cfg().edit().putBoolean("a11y_guard", on).apply());
            Ui.switchRow(this, g, k++, n, "flask", L.t("元件清單模式（省錢）"), L.t("不截圖，改給 AI 看元件清單，用 Haiku 選編號點擊（卡住升級 Sonnet）。會把畫面文字送給 AI；背景模式不適用"),
                    cfg().getBoolean("tree_on", true), (v, on) -> cfg().edit().putBoolean("tree_on", on).apply());
            Ui.row(this, g, k++, n, "shield", L.t("保護的 App"), L.t("AI 操作這些 App 之前先問你（銀行、支付類會自動保護）"), Ui.chevron(this), v -> chooseProtectedApps());
            Ui.switchRow(this, g, k++, n, "flask", L.t("記住成功的步驟（巨集）"), L.t("同樣的任務下次直接重播，AI 只確認結果；畫面對不上就交回 AI。更快更省"),
                    cfg().getBoolean("macro_on", true), (v, on) -> cfg().edit().putBoolean("macro_on", on).apply());
            Ui.row(this, g, k++, n, "shield", L.t("執行模式"), modeName(ExecMode.get(this)) + L.t("（點一下切換）"), Ui.chevron(this), v -> chooseMode());
        });
        return steps;
    }

    private void buildSettings() { for (Runnable r : settingsSteps()) r.run(); }

    private static String modeName(String m) {
        return ExecMode.SHIZUKU.equals(m) ? "Shizuku" : ExecMode.NORMAL.equals(m) ? L.t("普通") : "root";
    }

    /** 執行模式：root（用 su）、Shizuku（adb 權限，不用 root）、普通（只用無障礙與系統 API）。 */
    private void chooseMode() {
        String[] modes = {ExecMode.ROOT, ExecMode.SHIZUKU, ExecMode.NORMAL};
        String[] names = {"root", "Shizuku", L.t("普通")};
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(L.t("執行模式"))
                .setSingleChoiceItems(names, java.util.Arrays.asList(modes).indexOf(ExecMode.get(this)), (d, which) -> {
                    ExecMode.set(this, modes[which]);
                    d.dismiss();
                    recreate();
                })
                .setNegativeButton(L.t("取消"), null).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        AbTestUi.showUnseen(this);
        if (!myTheme.equals(App.themeKey(this))) {   // 在教學或設定換了主題色：這一頁要跟著換（功能頁以前不會更新）
            recreate();
            return;
        }
        // 從子頁面返回（主畫面一直沒被 stop 過）：系統剛好在返回動畫的最後一幀通知我們，這時重建首頁卡片、查權限會讓最後一幀掉幀；
        // 延到動畫結束後再做。真的離開過 App（被 stop）再回來才立刻做，也才需要把玻璃的繪圖暫存重錄。
        final boolean fromSub = resumedBefore && !stoppedSince;
        resumedBefore = true;
        if (fromSub) {
            root.postDelayed(() -> { A11yGuard.ensure(this); refresh(); }, 450);
        } else {
            stoppedSince = false;
            A11yGuard.ensure(this);
            refresh();
            if (glass != null) glass.resetGpu();   // 回到 App：玻璃的繪圖暫存全部重錄（見 GlassBar.resetGpu）
            if (indicator instanceof GlassBar) ((GlassBar) indicator).resetGpu();
        }
        kickGlass();
    }

    private boolean resumedBefore, stoppedSince;

    @Override
    protected void onStop() {
        stoppedSince = true;
        super.onStop();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_UI_HIDDEN) {   // 畫面被藏起來、系統可能回收繪圖資源
            if (glass != null) glass.resetGpu();
            if (indicator instanceof GlassBar) ((GlassBar) indicator).resetGpu();
        }
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
            android.widget.Toast.makeText(this, L.t("沒有記錄原本的值，所以不動它。請到「設定」裡改回你要的長按電源鍵動作"), android.widget.Toast.LENGTH_LONG).show();
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
        boolean ok = ov && a11y;
        // 中性底色：英雄區與權限格子都用表面色，主色只留給按鈕；狀態用文字與圖示表示
        int bg = Ui.attr(this, com.google.android.material.R.attr.colorSurfaceContainerHigh);
        int on = Ui.attr(this, com.google.android.material.R.attr.colorOnSurface);
        android.graphics.drawable.GradientDrawable heroBg = new android.graphics.drawable.GradientDrawable();
        heroBg.setCornerRadius(Ui.dp(this, 24));
        heroBg.setColor(bg);
        hero.setBackground(heroBg);
        hero.setVisibility(ok ? View.GONE : View.VISIBLE);   // 一切就緒就不顯示，有問題才出現
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
        t1.setText(ok ? L.t("一切就緒") : L.t("還有設定沒完成"));
        t1.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleLarge);
        t1.setTextColor(on);
        tx.addView(t1);
        TextView t2 = new TextView(this);
        t2.setText(ok ? L.t("助理可以使用") : L.t("點下面的 ＋ 項目就能設定"));
        t2.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        t2.setTextColor(on);
        tx.addView(t2);
        top.addView(tx, new LinearLayout.LayoutParams(0, -2, 1f));
        heroBox.addView(top);

        View gap = new View(this);   // 狀態文字和下面的列之間留一點空間
        heroBox.addView(gap, new LinearLayout.LayoutParams(-1, Ui.dp(this, 16)));
        // 三個權限狀態：兩欄的圓角格子（沒完成的點下去可以設定）
        View[] chips = {
            chip(L.t("懸浮層"), ov, on, v -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())))),
            chip(L.t("通知"), nt, on, v -> requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1)),
            chip(L.t("無障礙"), a11y, on, v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))};
        for (int i = 0; i < chips.length; i += 2) {
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            for (int k = 0; k < 2; k++) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
                lp.setMargins(k == 0 ? 0 : Ui.dp(this, 4), 0, k == 0 ? Ui.dp(this, 4) : 0, 0);
                if (i + k < chips.length) r.addView(chips[i + k], lp);
                else r.addView(new View(this), new LinearLayout.LayoutParams(0, 0, 1f));
            }
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
            rp.topMargin = Ui.dp(this, 10);
            heroBox.addView(r, rp);
        }
    }
}
