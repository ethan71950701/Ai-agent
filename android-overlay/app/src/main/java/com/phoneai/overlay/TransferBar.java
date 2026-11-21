package com.phoneai.overlay;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * App 在前景時的傳輸懸浮列（貼在畫面下方，不需要任何系統權限）：顯示現在是哪一個步驟（找對方、叫醒、傳了幾 %…）和「停止」。
 * 通知列／Now Bar 看不到的時候（人正在 App 裡）靠它。由 TransferNotify 更新、收起；App 的各個畫面切換時由 App 通知它換宿主。
 */
final class TransferBar {
    private TransferBar() { }

    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static Activity cur;
    private static View bar;
    private static TextView label;
    private static ProgressBar prog;
    private static String text = "";
    private static int pct = -1;   // -1＝還沒有百分比（轉圈圈）
    private static boolean on;

    /** pct 是 -1 表示不知道進度（找對方、叫醒這些階段）。 */
    static void update(String t, int p) {
        text = t == null ? "" : t;
        pct = p;
        on = true;
        UI.post(TransferBar::render);
    }

    static void hide() {
        on = false;
        UI.post(TransferBar::render);
    }

    static void onResumed(Activity a) {
        cur = a;
        UI.post(TransferBar::render);
    }

    static void onPaused(Activity a) {
        if (cur != a) return;
        detach();
        cur = null;
    }

    private static void detach() {
        if (bar != null && bar.getParent() instanceof ViewGroup) ((ViewGroup) bar.getParent()).removeView(bar);
        bar = null;
        label = null;
        prog = null;
    }

    private static void render() {
        Activity a = cur;
        if (a == null || a.isFinishing()) return;
        if (!on || !shouldShowOn(a)) { detach(); return; }
        if (bar == null || bar.getContext() != a) {
            detach();
            ViewGroup content = a.findViewById(android.R.id.content);
            if (!(content instanceof FrameLayout)) return;
            LinearLayout card = new LinearLayout(a);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            int pad = Ui.dp(a, 12);
            card.setPadding(pad, pad, pad, pad);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(Ui.dp(a, 16));
            bg.setColor(Ui.attr(a, com.google.android.material.R.attr.colorSurfaceContainerHigh));
            card.setBackground(bg);
            card.setElevation(Ui.dp(a, 8));
            LinearLayout col = new LinearLayout(a);
            col.setOrientation(LinearLayout.VERTICAL);
            label = new TextView(a);
            label.setTextColor(Ui.attr(a, com.google.android.material.R.attr.colorOnSurface));
            label.setTextSize(14);
            label.setMaxLines(2);
            prog = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
            prog.setMax(100);
            col.addView(label);
            col.addView(prog, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            card.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            android.widget.Button stop = new android.widget.Button(a, null, android.R.attr.borderlessButtonStyle);
            stop.setText(L.t("停止"));
            stop.setOnClickListener(v -> TransferNotify.requestStop());
            card.addView(stop);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
            int m = Ui.dp(a, 12);
            lp.setMargins(m, m, m, Ui.dp(a, 72));   // 避開下方的導覽列與輸入列
            content.addView(card, lp);
            bar = card;
        }
        label.setText(text);
        prog.setIndeterminate(pct < 0);
        if (pct >= 0) prog.setProgress(Math.min(100, pct));
    }

    /** 哪些畫面要顯示：App 自己的畫面；系統確認框、通知全文這類小視窗不顯示。 */
    static boolean shouldShowOn(Activity a) {
        return !(a instanceof ConfirmActivity) && !(a instanceof NoticeActivity);
    }
}
