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
 * 每頁右下角的狀態會自動檢查（root、權限、Termux、API Key），做完回來會更新。
 */
public class GuideActivity extends Activity {
    private static final int N = 10;
    private static final int REQ_NOTIF = 21, REQ_MIC = 22;
    private static final String[] TITLES = {
            "1. 授權 root", "2. 懸浮層權限", "3. 通知權限", "4. 無障礙服務", "5. 麥克風權限",
            "6. 安裝 Termux 與基本環境", "7. Claude API Key", "8. 離線語音辨識模型", "9. 長按電源鍵叫出（選用）", "10. 試試看"};
    private static final String[] WHY = {
            "PhoneAI 需要 root 權限：用來讀寫 Termux 的檔案、補回權限，以及開啟需要 root 的功能。沒有 root 的手機目前無法使用大部分功能。",
            "讓助理面板、光暈和浮動按鈕能疊在別的 App 上面。",
            "任務進行中，狀態列會出現進度膠囊，讓你知道 AI 在做什麼。",
            "讓 AI 看得到畫面上的按鈕與文字，並幫它點擊、滑動。螢幕邊緣的點擊漣漪也是靠它。",
            "用語音跟助理說話。語音辨識在手機上離線完成，聲音不會上傳。",
            "AI 的大腦跑在 Termux（手機上的 Linux 終端機 App）。這一步會自動下載 Termux 和 Termux:API，並安裝需要的套件與專案程式。",
            "AI 需要 Anthropic 的 API Key 才能思考。Key 只存在這支手機的 Termux 裡，不會上傳到別處。",
            "語音辨識（SenseVoice）會下載到手機裡，之後離線運作，聲音不會上傳。約 228 MB，建議連 Wi-Fi。不下載的話就不能用離線語音辨識，之後隨時可以回來下載。",
            "設定後長按電源鍵就能叫出 PhoneAI。三星手機：長按電源鍵本來就能叫原生語音助理，把預設數位助理改成 PhoneAI 即可。小米等其他手機：要到「功能 → 設定 → 呼叫方式」打開「長按電源鍵叫出助理」（需要 root）。",
            "回到首頁，在「問問看」輸入一句話，例如「現在幾點」或「開藍牙」，按送出。第一次會看到助理面板，之後就可以直接用了。"};
    private static final String[][] STEPS = {
            {"第一次需要 root 時，root 管理器（Magisk、KernelSU 等）會跳出授權視窗，按「允許」。",
                    "如果沒有跳出視窗：打開你的 root 管理器，找到「超級使用者」（Superuser）清單，把 PhoneAI Overlay 設成允許。",
                    "回到這裡，按下面的「檢查 root」。"},
            {"按「開啟權限頁」。", "在清單裡找到 PhoneAI Overlay，把開關打開。", "按返回鍵回到這裡，狀態會自動更新。"},
            {"按「允許通知」，在系統跳出的視窗按「允許」。"},
            {"按「開啟無障礙設定」。",
                    "找到「已下載的應用程式」或 PhoneAI Overlay，點進去打開開關，在提示視窗按「允許」。",
                    "有些手機重裝後會自動把它關掉。之後如果不能操作，到「功能 → 進階與診斷 → 修復權限」，用 root 補回來。"},
            {"按「允許麥克風」，在系統視窗按「允許」。"},
            {"按「開始安裝」，App 會自動完成全部：安裝 Termux、套件，並下載專案腳本。",
                    "建議連 Wi-Fi、插電，約 5～15 分鐘。Termux 會跳到前面一次，正常。",
                    "出錯時照畫面上的原因處理，再按一次就會從失敗的地方繼續。"},
            {"用電腦或手機瀏覽器打開 console.anthropic.com，註冊或登入。",
                    "到 Plans & Billing 儲值（預付制，金額你自己決定）。建議到 Limits 設定每月上限。",
                    "到 Settings → API keys → Create key，名稱隨意（例如 phone-agent）。",
                    "重要：Linked account 選「未連結」，工作區選「預設工作區」。選「你的帳號」會拿到個人 Key（sk-ant-usr- 開頭），這個專案用它會 401。",
                    "複製 Key（只會顯示一次），回到這裡按「輸入 API Key」，貼上後按儲存。",
                    "不要把 Key 貼給任何人，包括 AI 助手。"},
            {"按「下載語音模型」，直接在這一頁下載，進度會顯示在這裡。", "下載完成後會自動顯示「已下載」。", "不想現在下載可以先跳過，之後回到這一頁再按一次即可。"},
            {"按「開啟語音輸入設定」，在「數位助理」或「預設數位助理」裡選 PhoneAI Overlay。", "找不到的話，就用上面小米的方法。"},
            {"有問題就到「功能 → 進階與診斷」看狀態，或到「功能 → 進階與診斷 → 自我檢查」。", "把畫面文字貼給開發者時，不要貼 API Key。"}};
    private static final String[] BTN = {"檢查 root", "開啟權限頁", "允許通知", "開啟無障礙設定", "允許麥克風",
            "開始安裝", "輸入 API Key", "下載語音模型", "開啟語音輸入設定", "完成，回到 App"};
    private static final int[] DEMO = {10, 11, 12, 13, 2, 14, 15, 2, 1, 5};

    private final boolean[] ok = new boolean[N];
    private volatile boolean voiceBusy;
    private final String[] label = new String[N];
    private ViewPager2 pager;
    private final View[] dots = new View[N];
    private TextView prev;
    private MaterialButton next;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        for (int i = 0; i < N; i++) label[i] = "檢查中…";
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
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Ui.dp(this, 16);
        bottom.setPadding(pad, 0, pad, Ui.dp(this, 20));
        prev = Ui.body(this, "上一步");
        prev.setPadding(Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        prev.setOnClickListener(v -> pager.setCurrentItem(Math.max(0, pager.getCurrentItem() - 1), true));
        bottom.addView(prev, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout dotRow = new LinearLayout(this);
        dotRow.setGravity(Gravity.CENTER);
        for (int i = 0; i < N; i++) {
            View d = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 8), Ui.dp(this, 8));
            lp.setMargins(Ui.dp(this, 2), 0, Ui.dp(this, 2), 0);
            dotRow.addView(d, lp);
            dots[i] = d;
        }
        bottom.addView(dotRow, new LinearLayout.LayoutParams(0, -2, 1f));
        next = Ui.pill(this, "下一步", true, v -> {
            if (pager.getCurrentItem() >= N - 1) finish();
            else pager.setCurrentItem(pager.getCurrentItem() + 1, true);
        });
        bottom.addView(next, new LinearLayout.LayoutParams(-2, Ui.dp(this, 52)));
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
        next.setText(sel == N - 1 ? "完成" : "下一步");
    }

    /** 每一頁的按鈕動作。 */
    private void act(int i) {
        switch (i) {
            case 0: refresh(); break;
            case 1: startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))); break;
            case 2: requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF); break;
            case 3: startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); break;
            case 4: requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC); break;
            case 5: startActivity(new Intent(this, SetupActivity.class)); break;
            case 6: Maintenance.askKey(this, this::refresh); break;
            case 7: downloadVoice(); break;
            case 8:
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
                    label[7] = m;
                    pager.getAdapter().notifyDataSetChanged();
                }));
            } catch (Throwable e) {
                err = String.valueOf(e.getMessage() != null ? e.getMessage() : e);
                SherpaEngine.deleteRec(SherpaEngine.dir(this));
            }
            final String e2 = err;
            runOnUiThread(() -> {
                voiceBusy = false;
                refresh();
                if (e2 != null) android.widget.Toast.makeText(this, "下載失敗：" + e2 + "（可以再按一次）", android.widget.Toast.LENGTH_LONG).show();
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
            page.addView(new DemoView(a, DEMO[i]), new LinearLayout.LayoutParams(-1, Ui.dp(a, 190)));

            NestedScrollView sv = new NestedScrollView(a);
            LinearLayout col = new LinearLayout(a);
            col.setOrientation(LinearLayout.VERTICAL);
            int pad = Ui.dp(a, 28);
            col.setPadding(pad, Ui.dp(a, 8), pad, Ui.dp(a, 140));   // 底部留空，才不會被底下的按鈕列蓋住
            sv.addView(col, new FrameLayout.LayoutParams(-1, -2));
            page.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

            TextView t = new TextView(a);
            t.setText(TITLES[i]);
            t.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_HeadlineMedium);
            t.setTextColor(Ui.attr(a, com.google.android.material.R.attr.colorOnSurface));
            t.setGravity(Gravity.CENTER);
            col.addView(t, new LinearLayout.LayoutParams(-1, -2));

            TextView w = new TextView(a);
            w.setText(WHY[i]);
            w.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
            w.setTextColor(Ui.attr(a, com.google.android.material.R.attr.colorOnSurfaceVariant));
            w.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-1, -2);
            wl.topMargin = Ui.dp(a, 12);
            col.addView(w, wl);

            LinearLayout card = Ui.card(a, col);
            for (int k = 0; k < STEPS[i].length; k++) {
                TextView s = Ui.body(a, (k + 1) + ". " + STEPS[i][k]);
                LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2);
                sl.topMargin = Ui.dp(a, 6);
                card.addView(s, sl);
            }

            TextView st = new TextView(a);
            st.setText(label[i]);
            st.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall);
            st.setTextColor(Ui.attr(a, ok[i] ? com.google.android.material.R.attr.colorPrimary : com.google.android.material.R.attr.colorOnSurfaceVariant));
            st.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams stl = new LinearLayout.LayoutParams(-1, -2);
            stl.topMargin = Ui.dp(a, 16);
            col.addView(st, stl);

            MaterialButton b = Ui.pill(a, BTN[i], !ok[i] || i == N - 1, v -> act(i));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, Ui.dp(a, 52));
            bl.topMargin = Ui.dp(a, 12);
            col.addView(b, bl);
        }
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

    /** 逐項檢查；root、Termux、Key 要用 su，放在背景執行，結果回到主執行緒更新各頁。 */
    private void refresh() {
        new Thread(() -> {
            boolean root = Maintenance.suRun("id -u; echo done").trim().startsWith("0");
            String rootDiag = root ? "" : Maintenance.rootDiag();
            String t = Maintenance.suRun("[ -d /data/data/com.termux/files/home ] && echo T; [ -f /data/data/com.termux/files/home/assistant.py ] && echo S; echo done");
            boolean termux = t.contains("T");
            boolean setup = t.contains("S");
            boolean key = Maintenance.suRun("[ -s /data/data/com.termux/files/home/.claude_key ] && echo K; echo done").contains("K");
            runOnUiThread(() -> {
                boolean ov = Settings.canDrawOverlays(this);
                boolean nt = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
                boolean a11y = A11yGuard.listed(this);
                boolean mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
                set(0, root, root ? "✓ 已授權" : "尚未授權：請到 root 管理器允許，再按「檢查 root」\n" + rootDiag);
                set(1, ov, ov ? "✓ 已開啟" : "尚未開啟");
                set(2, nt, nt ? "✓ 已允許" : "尚未允許");
                set(3, a11y, a11y ? "✓ 已開啟" : "尚未開啟");
                set(4, mic, mic ? "✓ 已允許" : "尚未允許");
                set(5, setup, setup ? "✓ 已安裝完成" : termux ? "Termux 已在，但基本環境還沒裝好" : "尚未安裝");
                set(6, key, key ? "✓ 已設定" : "尚未設定");
                boolean voice = SherpaEngine.ready(this);
                set(7, voice, voice ? "✓ 已下載" : "尚未下載（可以先跳過）");
                set(8, false, "請自己確認：在「數位助理」裡選到 PhoneAI（程式無法判斷）");
                set(9, false, "做完這一步就可以開始用了");
                pager.getAdapter().notifyDataSetChanged();
            });
        }).start();
    }

    private void set(int i, boolean isOk, String text) {
        ok[i] = isOk;
        label[i] = text;
    }
}
