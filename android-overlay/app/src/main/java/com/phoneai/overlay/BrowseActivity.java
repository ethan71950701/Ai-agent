package com.phoneai.overlay;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

/** 直接瀏覽已配對裝置（電腦或另一台手機）的資料夾：點資料夾進去，點檔案就接收到這支手機。 */
public class BrowseActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private TransferStore.Peer peer;
    private TransferClient client;
    private String path = "";
    private long tapAt;        // 點下去的時間（量從點到畫面出現的總時間）
    private boolean loading;   // 讀取中不接受重複點擊，避免同時發好幾個請求而慢、亂序
    private TextView where, status;
    private LinearLayout list;
    private final BrowseCache cache = new BrowseCache();
    private PullRefresh pull;
    private boolean pullPending;   // 這一次讀取是下拉觸發的：讀完要把圓圈收回去

    @Override public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (pull != null) {
            int r = pull.handle(ev);
            if (r == PullRefresh.JUST_STARTED) {   // 剛開始下拉：先取消底下（清單、卡片）的觸控，再自己接手
                android.view.MotionEvent cancel = android.view.MotionEvent.obtain(ev);
                cancel.setAction(android.view.MotionEvent.ACTION_CANCEL);
                super.dispatchTouchEvent(cancel);
                cancel.recycle();
                return true;
            }
            if (r == PullRefresh.CONSUMED) return true;
        }
        return super.dispatchTouchEvent(ev);
    }
    private int navSeq;   // 每次換資料夾加一：舊的讀取回來時如果已經不是目前這次，就只存暫存、不畫面
    private android.window.OnBackInvokedCallback backCb;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String pid = getIntent().getStringExtra("peer_id");
        for (TransferStore.Peer p : TransferStore.peers(this)) if (p.id.equals(pid)) peer = p;
        if (peer == null) {
            finish();
            return;
        }
        LinearLayout[] c = new LinearLayout[1];
        android.view.View root = Ui.screen(this, peer.name, true, c);
        setContentView(root);
        if (root instanceof android.view.ViewGroup && ((android.view.ViewGroup) root).getChildAt(0) instanceof com.google.android.material.appbar.AppBarLayout) {
            pull = new PullRefresh(this, (android.view.ViewGroup) root, Ui.scroller(root),
                    (com.google.android.material.appbar.AppBarLayout) ((android.view.ViewGroup) root).getChildAt(0),
                    () -> { if (loading) { pull.done(); return; } pullPending = true; load(path, true); });   // 從最上面往下拉：重新讀這個資料夾
        }
        Ui.enterSpring(this);
        LinearLayout box = c[0];

        LinearLayout top = Ui.card(this, box);
        where = Ui.body(this, "");
        top.addView(where);
        Ui.buttons(this, top, Ui.tonal(this, L.t("上一層"), v -> load(parentOf(path))),
                Ui.tonal(this, L.t("搜尋這台"), v -> { Ui.rememberTap(v); startActivity(new Intent(this, FileSearchActivity.class).putExtra("peer_id", peer.id)); }));
        status = Ui.body(this, "");
        box.addView(status);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        box.addView(list);
        load("");
    }

    /** 上一層：「C:/Users」→「C:/」→「」（磁碟列表）；手機的「Download/PhoneAI」→「Download」→「」。 */
    static String parentOf(String p) {
        if (p == null || p.isEmpty()) return "";
        String s = p.endsWith("/") ? p.substring(0, p.length() - 1) : p;
        int i = s.lastIndexOf('/');
        if (i < 0) return "";
        String par = s.substring(0, i);
        if (par.isEmpty()) return "/";
        if (par.length() == 2 && par.charAt(1) == ':') return par + "/";
        return par;
    }

    private void load(String target) { load(target, false); }

    /** force＝重新整理：不看暫存，一定重新讀。 */
    private void load(String target, boolean force) {
        BrowseCache.Page hit = force ? null : cache.get(target);
        if (hit != null) {   // 讀過的資料夾（含返回上一層）直接顯示，不用再問對方
            navSeq++;
            loading = false;
            show(target, hit.rows);
            boolean fresh = BrowseCache.fresh(hit, System.currentTimeMillis());
            status.setText(rows0(hit).isEmpty() ? L.t("這個資料夾是空的") : "");
            status.setText(status.getText() + (status.getText().length() > 0 ? "　" : "") + L.t("（暫存的內容") + (fresh ? "" : L.t("，背景更新中")) + L.t("；從最上面往下拉可重新讀取）"));
            if (!fresh) refreshQuietly(target, hit.rows);
            return;
        }
        if (loading) return;
        loading = true;
        final int seq = ++navSeq;
        tapAt = System.currentTimeMillis();
        if (!pullPending) status.setText(L.t("讀取中…"));   // 下拉重新整理時圓圈就是「讀取中」，不要改文字（換行數一變，清單會跳一下）
        final long t0 = System.currentTimeMillis();
        new Thread(() -> {
            try {
                if (client == null) client = FileTools.clientFor(this, peer);
                long t1 = System.currentTimeMillis();
                List<String[]> rows = FileTools.rows(client.list(target));
                cache.put(target, rows, System.currentTimeMillis());
                String timing = L.t("（連線 ") + (t1 - t0) + L.t(" 毫秒，讀取 ") + (System.currentTimeMillis() - t1) + L.t(" 毫秒）");
                ui.post(() -> {
                    if (seq != navSeq) return;   // 讀的時候使用者已經按返回、去別的資料夾了
                    loading = false;
                    if (pullPending) { pullPending = false; if (pull != null) pull.done(); }
                    show(target, rows);
                    long total = System.currentTimeMillis() - tapAt;   // 含主執行緒排隊與畫面繪製
                    status.setText(status.getText() + " " + timing + L.t("，從點到出現共 ") + total + L.t(" 毫秒"));
                });
            } catch (Exception e) {
                ui.post(() -> { if (seq == navSeq) { loading = false; status.setText(L.t("讀取失敗：") + e.getMessage()); } if (pullPending) { pullPending = false; if (pull != null) pull.done(); } });
            }
        }).start();
    }

    private static List<String[]> rows0(BrowseCache.Page e) { return e.rows; }

    /** 暫存已經放了一陣子：先顯示舊的，背景再讀一次；內容有變而且人還在這個資料夾才重畫。 */
    private void refreshQuietly(final String target, final List<String[]> old) {
        final TransferClient cl = client;
        if (cl == null) return;
        new Thread(() -> {
            try {
                List<String[]> rows = FileTools.rows(cl.list(target));
                cache.put(target, rows, System.currentTimeMillis());
                if (!BrowseCache.same(old, rows)) ui.post(() -> { if (target.equals(path) && !loading) { show(target, rows); status.setText(L.t("（內容有更新）")); } });
            } catch (Exception ignored) { }   // 背景更新失敗就維持顯示暫存的
        }).start();
    }

    /** 返回鍵：不在最上層就回上一個資料夾（不是離開這一頁）；在最上層才離開。預測返回要自己註冊，而且要壓過頁面預設的那個。 */
    private void updateBackHandler() {
        android.window.OnBackInvokedDispatcher d = getOnBackInvokedDispatcher();
        boolean needed = !path.isEmpty();
        if (needed && backCb == null) {
            backCb = () -> { loading = false; load(parentOf(path)); };   // 讀取中按返回也要能退（舊的讀取回來會因為 navSeq 不一樣被丟掉）
            d.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_OVERLAY, backCb);
        } else if (!needed && backCb != null) {
            d.unregisterOnBackInvokedCallback(backCb);
            backCb = null;
        }
    }

    private void show(String target, List<String[]> rows) {
        path = target;
        updateBackHandler();
        where.setText(L.t("位置：") + (target.isEmpty() ? L.t("（最上層）") : target));
        status.setText(rows.isEmpty() ? L.t("這個資料夾是空的") : "");
        list.removeAllViews();
        for (String[] r : rows) {
            final boolean folder = "D".equals(r[0]);
            String trimmed = r[2].endsWith("/") ? r[2].substring(0, r[2].length() - 1) : r[2];   // 磁碟根目錄「C:/」要去掉最後的 /
            String name = trimmed.contains("/") ? trimmed.substring(trimmed.lastIndexOf('/') + 1) : trimmed;
            LinearLayout card = Ui.card(this, list);
            card.setClickable(true);
            card.setBackground(rippleBackground());          // 按下去會有水波紋，知道有按到

            TextView t = Ui.body(this, (folder ? L.t("📁 ") : L.t("📄 ")) + name);
            t.setTextSize(16);
            t.setPadding(0, 0, 0, 0);
            card.addView(t);
            if (!folder) {
                long size = 0;
                try { size = Long.parseLong(r[1]); } catch (NumberFormatException ignored) { }
                card.addView(Ui.body(this, TransferStore.size(size)));
            }
            card.setOnClickListener(v -> {
                if (folder) load(r[2]);
                else confirmReceive(r[2], name);
            });
        }
    }

    private android.graphics.drawable.Drawable rippleBackground() {
        android.util.TypedValue tv = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        return getResources().getDrawable(tv.resourceId, getTheme());
    }

    private void confirmReceive(String remote, String name) {
        new MaterialAlertDialogBuilder(this).setMessage(L.t("接收「") + name + L.t("」到這支手機的「下載/PhoneAI」？"))
                .setPositiveButton(L.t("接收"), (d, w) -> new Thread(() -> {
                    try {
                        String saved = FileTools.fetch(this, peer, remote);
                        if (saved != null) ui.post(() -> Toast.makeText(this, L.t("已接收：") + saved, Toast.LENGTH_LONG).show());
                    } catch (Exception e) {
                        ui.post(() -> Toast.makeText(this, L.t("接收失敗：") + e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                }).start())
                .setNegativeButton(L.t("取消"), null).show();
    }
}
