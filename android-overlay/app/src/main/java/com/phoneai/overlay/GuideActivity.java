package com.phoneai.overlay;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.button.MaterialButton;

/**
 * 設定導覽：和歡迎動畫同一種介面（左右滑動、每頁一個循環小動畫、底下圓點），共 9 步。
 * 每頁右下角的狀態會自動檢查（root、權限、API Key、語音模型），做完回來會更新。
 */
public class GuideActivity extends Activity {
    private static final int N = 9;
    private static final int REQ_NOTIF = 21, REQ_MIC = 22;
    private static final String[] TITLES = {
            "1. 選擇執行模式", "2. 懸浮層權限", "3. 通知權限", "4. 無障礙服務", "5. 麥克風權限",
            "6. Claude API Key", "7. 離線語音辨識模型", "8. 長按電源鍵叫出（選用）", "9. 試試看"};
    private static final String[] WHY = {
            "選一種執行模式，之後隨時可以在設定改。不確定就選「普通」，什麼都不用裝。",
            "讓助理面板、光暈和浮動按鈕能疊在別的 App 上面。",
            "任務進行中，狀態列會出現進度膠囊，讓你知道 AI 在做什麼。",
            "讓 App 能幫你點擊、滑動、輸入文字、讀取畫面上的按鈕與文字，並畫出點擊的漣漪。沒有 root 時，截圖和返回、首頁這些按鍵也靠它，所以沒有 root 一定要開。",
            "用語音跟助理說話。語音辨識在手機上離線完成，聲音不會上傳。",
            "AI 需要 Anthropic 的 API Key 才能思考。Key 只存在這支手機 App 的資料夾裡，不會上傳到別處。",
            "語音辨識（SenseVoice）會下載到手機裡，之後離線運作，聲音不會上傳。約 228 MB，建議連 Wi-Fi。不下載的話就不能用離線語音辨識，之後隨時可以回來下載。",
            "設定後長按電源鍵就能叫出 PhoneAI。三星手機：長按電源鍵本來就能叫原生語音助理，把預設數位助理改成 PhoneAI 即可。小米等其他手機：要到「功能 → 設定 → 呼叫方式」打開「長按電源鍵叫出助理」（需要 root）。",
            "回到首頁，在「問問看」輸入一句話，例如「現在幾點」或「開藍牙」，按送出。第一次會看到助理面板，之後就可以直接用了。"};
    private static final String[][] STEPS = {
            {"沒有 root、或還不想給：這一步可以直接跳過，按下面的箭頭往下走。",
                    "想用 root：第一次需要時，root 管理器（Magisk、KernelSU 等）會跳出授權視窗，按「允許」。",
                    "如果沒有跳出視窗：打開你的 root 管理器，找到「超級使用者」（Superuser）清單，把 PhoneAI Overlay 設成允許。",
                    "回到這裡，按下面的「檢查 root」。"},
            {"按「開啟權限頁」。", "在清單裡找到 PhoneAI Overlay，把開關打開。", "按返回鍵回到這裡，狀態會自動更新。"},
            {"按「允許通知」，在系統跳出的視窗按「允許」。"},
            {"按「開啟無障礙設定」。",
                    "找到「已下載的應用程式」或 PhoneAI Overlay，點進去打開開關，在提示視窗按「允許」。",
                    "有些手機重裝後會自動把它關掉。有 root 的話，之後不能操作時到「功能 → 進階與診斷 → 修復權限」可以補回來；沒有 root 就要自己再開一次。"},
            {"按「允許麥克風」，在系統視窗按「允許」。"},
            {"用電腦或手機瀏覽器打開 console.anthropic.com，註冊或登入。",
                    "到 Plans & Billing 儲值（預付制，金額你自己決定）。建議到 Limits 設定每月上限。",
                    "到 Settings → API keys → Create key，名稱隨意（例如 phone-agent）。",
                    "重要：Linked account 選「未連結」，工作區選「預設工作區」。選「你的帳號」會拿到個人 Key（sk-ant-usr- 開頭），這個專案用它會 401。",
                    "複製 Key（只會顯示一次），回到這裡按「輸入 API Key」，貼上後按儲存。",
                    "不要把 Key 貼給任何人，包括 AI 助手。"},
            {"按「下載語音模型」，直接在這一頁下載，進度會顯示在這裡。", "下載完成後會自動顯示「已下載」。", "不想現在下載可以先跳過，之後回到這一頁再按一次即可。"},
            {"按「開啟語音輸入設定」，在「數位助理」或「預設數位助理」裡選 PhoneAI Overlay。", "找不到的話，就用上面小米的方法。"},
            {"有問題就到「功能 → 進階與診斷」看狀態。", "把畫面文字貼給開發者時，不要貼 API Key。"}};
    /** 三種模式的比較（引導頁第一頁的「三種模式比較」按鈕）。 */
    static final String COMPARE =
            "【root】最完整最快\n"
            + "• 開關 Wi-Fi、藍牙、行動數據、NFC、定位、深色模式、勿擾、旋轉、亮度：一句話直接做好，不跳任何提示\n"
            + "• 截圖、點擊、輸入都用 root，穩定\n"
            + "• 獨有：背景螢幕、即時觸控、無障礙被關掉時自動修復、App 內更新直接安裝\n"
            + "• 需要：已 root 的手機\n\n"
            + "【Shizuku】不用 root，接近 root\n"
            + "• 上面那些開關、輸入、截圖都能直接做，不用跳設定頁\n"
            + "• 不能：背景螢幕、即時觸控、無障礙自動修復（這些要真的 root）；更新要按系統的「安裝」\n"
            + "• 需要：安裝並啟動 Shizuku App；手機每次重開機後要再啟動一次\n\n"
            + "【普通】什麼都不用裝\n"
            + "• 點擊、滑動、輸入、截圖靠無障礙服務（一定要開）\n"
            + "• Wi-Fi、藍牙、行動數據、NFC、定位、深色模式：只能打開設定頁，要你自己按\n"
            + "• 勿擾、亮度、自動旋轉：要先在系統允許對應權限一次\n"
            + "• 音量、返回、首頁、通知欄、截圖都可以直接做\n\n"
            + "不確定就先選「普通」，想要更完整再改。";
    private static final String[][] MODE_STEPS = {
            {"想用 root：第一次需要時，root 管理器（Magisk、KernelSU 等）會跳出授權視窗，按「允許」。",
                    "如果沒有跳出視窗：打開你的 root 管理器，找到「超級使用者」（Superuser）清單，把 PhoneAI Overlay 設成允許。",
                    "回到這裡，按下面的「檢查 root」。"},
            {"先安裝 Shizuku App（Google Play 或它的 GitHub），打開它，照它的說明啟動服務（無線偵錯或 root；手機重開機後要再啟動一次）。",
                    "回到這裡按「授權 Shizuku」，在 Shizuku 跳出的視窗按「允許」。",
                    "狀態變成「已可用」就完成了。"},
            {"這個模式不用安裝或授權任何東西，直接往下走。", "之後想要更完整，隨時可以回到這一頁改選 root 或 Shizuku。"}};
    private static final String[] MODE_NAMES = {ExecMode.ROOT, ExecMode.SHIZUKU, ExecMode.NORMAL};
    private static final String[] MODE_LABELS = {"root", "Shizuku", "普通"};
    private rikka.shizuku.Shizuku.OnRequestPermissionResultListener shizukuListener;
    private static final String[] BTN = {"檢查", "開啟權限頁", "允許通知", "開啟無障礙設定", "允許麥克風",
            "輸入 API Key", "下載語音模型", "開啟語音輸入設定", "完成，回到 App"};
    private static final int[] DEMO = {10, 11, 12, 13, 2, 15, 2, 1, 5};

    private final boolean[] ok = new boolean[N];
    private volatile boolean voiceBusy;
    private android.widget.ProgressBar voiceBar;
    private TextView voiceStatus;
    private final String[] label = new String[N];
    private ViewPager2 pager;
    private final View[] dots = new View[N];
    private TextView prev;
    private View next;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        for (int i = 0; i < N; i++) label[i] = L.t("檢查中…");
        shizukuListener = (code, result) -> refresh();
        try { rikka.shizuku.Shizuku.addRequestPermissionResultListener(shizukuListener); } catch (Throwable ignored) { }
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.attr(this, com.google.android.material.R.attr.colorSurface));
        root.setFitsSystemWindows(true);
        pager = new ViewPager2(this);
        pager.setAdapter(new Adapter());
        pager.setOffscreenPageLimit(1);
        pager.setPageTransformer((page, pos) -> {
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
        int pad = Ui.dp(this, 16);
        bottom.setPadding(pad, 0, pad, Ui.dp(this, 20));
        LinearLayout dotRow = new LinearLayout(this);
        dotRow.setGravity(Gravity.CENTER);
        for (int i = 0; i < N; i++) {
            View d = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 8), Ui.dp(this, 8));
            lp.setMargins(Ui.dp(this, 2), 0, Ui.dp(this, 2), 0);
            dotRow.addView(d, lp);
            dots[i] = d;
        }
        bottom.addView(dotRow, new LinearLayout.LayoutParams(-1, -2));
        // 第二排：左邊「上一步」，中間是箭頭按鈕（最後一頁同樣是箭頭，按下完成）
        FrameLayout actions = new FrameLayout(this);
        prev = Ui.body(this, L.t("上一步"));
        prev.setPadding(Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        prev.setOnClickListener(v -> pager.setCurrentItem(Math.max(0, pager.getCurrentItem() - 1), true));
        actions.addView(prev, new FrameLayout.LayoutParams(-2, -2, Gravity.START | Gravity.CENTER_VERTICAL));
        next = Ui.arrowFab(this, v -> {
            if (pager.getCurrentItem() >= N - 1) finish();
            else pager.setCurrentItem(pager.getCurrentItem() + 1, true);
        });
        actions.addView(next, new FrameLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 64), Gravity.CENTER));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, Ui.dp(this, 64));
        alp.topMargin = Ui.dp(this, 12);
        bottom.addView(actions, alp);
        root.addView(bottom, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        setContentView(root);
        Ui.enterSpring(this);
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int i) { select(i); }
        });
        select(0);
    }

    /** 圓點：目前那頁的圓點拉長成膠囊；底下的文字與按鈕跟著換。 */
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
        prev.setVisibility(sel == 0 ? View.INVISIBLE : View.VISIBLE);
    }

    /** 每一頁的按鈕動作。 */
    private void act(int i) {
        switch (i) {
            case 0:
                if (ExecMode.SHIZUKU.equals(ExecMode.get(this))) {
                    if (!ShizukuBackend.installed(this)) android.widget.Toast.makeText(this, L.t("還沒安裝 Shizuku：先安裝並啟動它"), android.widget.Toast.LENGTH_LONG).show();
                    else ShizukuBackend.requestPermission();
                }
                refresh();
                break;
            case 1: startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))); break;
            case 2: requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF); break;
            case 3: startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); break;
            case 4: requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC); break;
            case 5: Maintenance.askKey(this, this::refresh); break;
            case 6: downloadVoice(); break;
            case 7:
                try {
                    startActivity(new Intent(Settings.ACTION_VOICE_INPUT_SETTINGS));
                } catch (Exception e) {
                    startActivity(new Intent(Settings.ACTION_SETTINGS));
                }
                break;
            default: finish(); break;
        }
    }

    /** 第 8 頁：直接在這一頁下載語音模型（進度顯示在這一頁），不另外開子頁。 */
    private void downloadVoice() {
        if (voiceBusy || SherpaEngine.ready(this)) return;
        voiceBusy = true;
        new Thread(() -> {
            String err = null;
            try {
                SherpaEngine.download(this, m -> runOnUiThread(() -> {
                    label[6] = m;
                    java.util.regex.Matcher pm = java.util.regex.Pattern.compile("(\\d+)%").matcher(m);
                    if (pm.find() && voiceBar != null) voiceBar.setProgress(Integer.parseInt(pm.group(1)));
                    if (voiceStatus != null) voiceStatus.setText(m);
                }));
            } catch (Throwable e) {
                err = String.valueOf(e.getMessage() != null ? e.getMessage() : e);
                SherpaEngine.deleteRec(SherpaEngine.dir(this));
            }
            final String e2 = err;
            runOnUiThread(() -> {
                voiceBusy = false;
                refresh();
                if (e2 != null) android.widget.Toast.makeText(this, L.t("下載失敗：") + e2 + L.t("（可以再按一次）"), android.widget.Toast.LENGTH_LONG).show();
            });
        }, "sensevoice-dl").start();
    }

    private final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override
        public int getItemCount() { return N; }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
            LinearLayout page = new LinearLayout(GuideActivity.this);
            page.setOrientation(LinearLayout.VERTICAL);
            page.setLayoutParams(new RecyclerView.LayoutParams(-1, -1));
            return new RecyclerView.ViewHolder(page) { };
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder h, int i) {
            Activity a = GuideActivity.this;
            LinearLayout page = (LinearLayout) h.itemView;
            page.removeAllViews();
            page.setGravity(Gravity.CENTER_HORIZONTAL);
            DemoView demo = new DemoView(a, DEMO[i]);
            page.addView(demo, new LinearLayout.LayoutParams(-1, Ui.dp(a, 190)));
            Ui.bounceIn(demo);   // 示意圖進場彈一下

            NestedScrollView sv = new NestedScrollView(a);
            LinearLayout col = new LinearLayout(a);
            col.setOrientation(LinearLayout.VERTICAL);
            int pad = Ui.dp(a, 28);
            col.setPadding(pad, Ui.dp(a, 8), pad, Ui.dp(a, 140));   // 底部留空，才不會被底下的按鈕列蓋住
            sv.addView(col, new FrameLayout.LayoutParams(-1, -2));
            page.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

            TextView t = new TextView(a);
            t.setText(L.t(TITLES[i]));
            t.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_HeadlineMedium);
            t.setTextColor(Ui.attr(a, com.google.android.material.R.attr.colorOnSurface));
            t.setGravity(Gravity.CENTER);
            col.addView(t, new LinearLayout.LayoutParams(-1, -2));

            TextView w = new TextView(a);
            w.setText(L.t(WHY[i]));
            w.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
            w.setTextColor(Ui.attr(a, com.google.android.material.R.attr.colorOnSurfaceVariant));
            w.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-1, -2);
            wl.topMargin = Ui.dp(a, 12);
            col.addView(w, wl);

            String mode = ExecMode.get(a);
            if (i == 0) {   // 第一頁：選執行模式
                LinearLayout row = new LinearLayout(a);
                row.setOrientation(LinearLayout.HORIZONTAL);
                for (int k = 0; k < MODE_NAMES.length; k++) {
                    final String m = MODE_NAMES[k];
                    MaterialButton mb = Ui.pill(a, L.t(MODE_LABELS[k]), m.equals(mode), v -> { ExecMode.set(a, m); refresh(); });
                    LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(0, Ui.dp(a, 48), 1f);
                    ml.setMarginEnd(k < MODE_NAMES.length - 1 ? Ui.dp(a, 8) : 0);
                    row.addView(mb, ml);
                }
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
                rl.topMargin = Ui.dp(a, 12);
                col.addView(row, rl);
                TextView cmp = Ui.body(a, L.t("三種模式比較 ›"));
                cmp.setGravity(Gravity.CENTER);
                cmp.setTextColor(Ui.attr(a, com.google.android.material.R.attr.colorPrimary));
                cmp.setPadding(0, Ui.dp(a, 10), 0, Ui.dp(a, 4));
                cmp.setOnClickListener(v -> new com.google.android.material.dialog.MaterialAlertDialogBuilder(a)
                        .setTitle(L.t("三種模式比較")).setMessage(L.t(COMPARE)).setPositiveButton(L.t("知道了"), null).show());
                col.addView(cmp, new LinearLayout.LayoutParams(-1, -2));
            }
            String[] steps = i == 0 ? MODE_STEPS[java.util.Arrays.asList(MODE_NAMES).indexOf(mode)] : STEPS[i];
            LinearLayout card = Ui.card(a, col);
            for (int k = 0; k < steps.length; k++) {
                TextView s = Ui.body(a, (k + 1) + ". " + L.t(steps[k]));
                LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2);
                sl.topMargin = Ui.dp(a, 6);
                card.addView(s, sl);
            }

            if (i == 6) {   // 語音模型：下載進度條
                voiceBar = new android.widget.ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
                voiceBar.setMax(100);
                voiceBar.setProgress(0);
                LinearLayout.LayoutParams vbl = new LinearLayout.LayoutParams(-1, Ui.dp(a, 8));
                vbl.topMargin = Ui.dp(a, 16);
                col.addView(voiceBar, vbl);
            }
            TextView st = new TextView(a);
            st.setText(label[i]);
            if (i == 6) voiceStatus = st;
            st.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall);
            st.setTextColor(Ui.attr(a, ok[i] ? com.google.android.material.R.attr.colorPrimary : com.google.android.material.R.attr.colorOnSurfaceVariant));
            st.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams stl = new LinearLayout.LayoutParams(-1, -2);
            stl.topMargin = Ui.dp(a, 16);
            col.addView(st, stl);

            String btnText = i != 0 ? BTN[i] : ExecMode.SHIZUKU.equals(mode) ? "授權 Shizuku" : "檢查 root";
            MaterialButton b = Ui.pill(a, L.t(btnText), !ok[i] || i == N - 1, v -> act(i));
            b.setVisibility(!ok[i] || i == N - 1 ? View.VISIBLE : View.GONE);   // 已經完成的項目不再顯示按鈕
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, Ui.dp(a, 52));
            bl.topMargin = Ui.dp(a, 12);
            col.addView(b, bl);
        }
    }

    @Override
    protected void onDestroy() {
        try { rikka.shizuku.Shizuku.removeRequestPermissionResultListener(shizukuListener); } catch (Throwable ignored) { }
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] p, int[] g) {
        super.onRequestPermissionsResult(req, p, g);
        refresh();
    }

    private boolean installedShizuku() { return ShizukuBackend.installed(this); }

    /** 逐項檢查；root、Key 要用 su，放在背景執行，結果回到主執行緒更新各頁。 */
    private void refresh() {
        new Thread(() -> {
            Root.recheck();   // 使用者可能剛在 root 管理器裡允許：重新判斷，不要沿用舊結果
            final String mode = ExecMode.get(this);
            final boolean root = ExecMode.ROOT.equals(mode) && Root.isRoot();
            final int sz = ShizukuBackend.state();
            final boolean shz = installedShizuku();
            boolean key = ChatEngine.key(this) != null;
            runOnUiThread(() -> {
                boolean ov = Settings.canDrawOverlays(this);
                boolean nt = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
                boolean a11y = A11yGuard.listed(this);
                boolean mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
                if (ExecMode.NORMAL.equals(mode)) set(0, true, L.t("✓ 普通模式（不需要額外授權）"));
                else if (ExecMode.SHIZUKU.equals(mode)) set(0, sz == 0, sz == 0 ? L.t("✓ Shizuku 已可用") : !shz ? L.t("尚未安裝 Shizuku") : sz == 1 ? L.t("Shizuku 沒有在執行：打開 Shizuku 啟動它") : sz == 2 ? L.t("還沒授權：按「授權 Shizuku」") : L.t("Shizuku 版本太舊，請更新"));
                else set(0, root, root ? L.t("✓ 已授權（會優先用 root，更快更完整）") : L.t("未授權：到 root 管理器允許 PhoneAI，回來按「檢查 root」；沒有 root 可以改選 Shizuku 或普通模式"));
                set(1, ov, ov ? L.t("✓ 已開啟") : L.t("尚未開啟"));
                set(2, nt, nt ? L.t("✓ 已允許") : L.t("尚未允許"));
                set(3, a11y, a11y ? L.t("✓ 已開啟") : L.t("尚未開啟"));
                set(4, mic, mic ? L.t("✓ 已允許") : L.t("尚未允許"));
                set(5, key, key ? L.t("✓ 已設定") : L.t("尚未設定"));
                boolean voice = SherpaEngine.ready(this);
                set(6, voice, voice ? L.t("✓ 已下載") : L.t("尚未下載（可以先跳過）"));
                set(7, false, L.t("請自己確認：在「數位助理」裡選到 PhoneAI（程式無法判斷）"));
                set(8, false, L.t("做完這一步就可以開始用了"));
                pager.getAdapter().notifyDataSetChanged();
            });
        }).start();
    }

    private void set(int i, boolean isOk, String text) {
        ok[i] = isOk;
        label[i] = text;
    }
}
