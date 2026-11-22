package com.phoneai.overlay;

import android.app.Activity;
import android.content.DialogInterface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 整頁：用法跟 MaterialAlertDialogBuilder 一樣（setTitle、setMessage、setView、setItems、三顆按鈕），
 * 但畫出來的是跟其他整頁一樣的頁面（大標題頂欄、左上返回鍵），從右邊滑進來。
 * 頁面是蓋在「呼叫它的那個 Activity」上面的一層（不是另開 Activity、也不是對話框視窗），
 * 所以頁面裡再跳出的確認／編輯小窗、Toast 等，還是掛在原本的 Activity 上，照常運作。
 * 按任何按鈕或點了清單項目，頁面會先關掉再執行對應的動作（跟對話框一樣）；返回鍵與左上箭頭＝取消。
 */
final class PageBuilder {
    private final Activity a;
    private CharSequence title = "", message, posL, neuL, negL;
    private View view;
    private CharSequence[] items;
    private DialogInterface.OnClickListener itemsL, posC, neuC, negC;
    private DialogInterface.OnCancelListener cancelL;

    PageBuilder(Activity a) { this.a = a; }

    PageBuilder setTitle(CharSequence t) { title = t; return this; }
    PageBuilder setMessage(CharSequence m) { message = m; return this; }
    PageBuilder setView(View v) { view = v; return this; }
    PageBuilder setItems(CharSequence[] it, DialogInterface.OnClickListener l) { items = it; itemsL = l; return this; }
    PageBuilder setPositiveButton(CharSequence t, DialogInterface.OnClickListener l) { posL = t; posC = l; return this; }
    PageBuilder setNeutralButton(CharSequence t, DialogInterface.OnClickListener l) { neuL = t; neuC = l; return this; }
    PageBuilder setNegativeButton(CharSequence t, DialogInterface.OnClickListener l) { negL = t; negC = l; return this; }
    PageBuilder setOnCancelListener(DialogInterface.OnCancelListener l) { cancelL = l; return this; }

    /** 回傳的把手可以 dismiss()（關掉這一頁）。 */
    Handle show() {
        final Handle h = new Handle();
        h.a = a;
        h.cancelL = cancelL;
        Ui.pageBack = h::cancel;
        LinearLayout[] c = new LinearLayout[1];
        final View root = Ui.screen(a, String.valueOf(title), true, c);
        Ui.pageBack = null;
        final LinearLayout box = c[0];
        h.root = root;
        if (message != null && message.length() > 0) {
            TextView t = Ui.body(a, message.toString());
            t.setPadding(Ui.dp(a, 8), 0, Ui.dp(a, 8), Ui.dp(a, 8));
            box.addView(t);
        }
        if (view != null) {
            if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
            box.addView(view, new LinearLayout.LayoutParams(-1, -2));
        }
        if (items != null) {
            Ui.standaloneRows = true;
            try {
                for (int i = 0; i < items.length; i++) {
                    final int idx = i;
                    String s = String.valueOf(items[i]);
                    int nl = s.indexOf('\n');
                    String head = nl < 0 ? s : s.substring(0, nl).trim(), sub = nl < 0 ? null : s.substring(nl + 1).trim();
                    Ui.row(a, box, 0, 1, null, head, sub, Ui.chevron(a), v -> { h.dismiss(); if (itemsL != null) itemsL.onClick(h, idx); });
                }
            } finally { Ui.standaloneRows = false; }
        }
        if (posL != null || neuL != null || negL != null) {   // 跟對話框一樣：按鈕不拉滿整行；中立的靠左，取消與確定靠右
            LinearLayout bar = new LinearLayout(a);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setGravity(android.view.Gravity.CENTER_VERTICAL);
            if (neuL != null) addButton(bar, h, neuL, neuC, DialogInterface.BUTTON_NEUTRAL, false);
            bar.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1f));   // 把後面的按鈕推到右邊
            if (negL != null) addButton(bar, h, negL, negC, DialogInterface.BUTTON_NEGATIVE, false);
            if (posL != null) addButton(bar, h, posL, posC, DialogInterface.BUTTON_POSITIVE, true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = Ui.dp(a, 20);
            box.addView(bar, lp);
        }
        h.attach();
        return h;
    }

    private void addButton(LinearLayout bar, Handle h, CharSequence text, DialogInterface.OnClickListener l, int which, boolean primary) {
        com.google.android.material.button.MaterialButton b = Ui.pill(a, text.toString(), primary, v -> { h.dismiss(); if (l != null) l.onClick(h, which); });
        b.setMinWidth(Ui.dp(a, 88));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, Ui.dp(a, 48));
        lp.leftMargin = Ui.dp(a, 8);
        bar.addView(b, lp);
    }

    /** 這一頁的把手：dismiss＝關掉；cancel＝返回（會呼叫 setOnCancelListener）。也當成給按鈕回呼用的 DialogInterface。 */
    static final class Handle implements DialogInterface {
        Activity a;
        View root;
        OnCancelListener cancelL;
        private boolean gone;
        private android.window.OnBackInvokedCallback back;

        private PageAnim anim;

        void attach() {
            anim = new PageAnim(a, root);
            anim.open();
            try {
                if (android.os.Build.VERSION.SDK_INT >= 34) {
                    back = new android.window.OnBackAnimationCallback() {
                        @Override public void onBackStarted(android.window.BackEvent e) { if (anim != null) anim.onBackStarted(); }
                        @Override public void onBackProgressed(android.window.BackEvent e) { if (anim != null) anim.onBackProgressed(e.getProgress()); }
                        @Override public void onBackCancelled() { if (anim != null) anim.onBackCancelled(); }
                        @Override public void onBackInvoked() { cancel(); }
                    };
                } else {
                    back = this::cancel;
                }
                a.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, back);
            } catch (Throwable ignored) { back = null; }
        }

        @Override
        public void dismiss() {
            if (gone) return;
            gone = true;
            try { if (back != null) a.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(back); } catch (Throwable ignored) { }
            if (anim != null) anim.close(null);
        }

        @Override
        public void cancel() {
            if (gone) return;
            dismiss();
            if (cancelL != null) cancelL.onCancel(this);
        }
    }
}
