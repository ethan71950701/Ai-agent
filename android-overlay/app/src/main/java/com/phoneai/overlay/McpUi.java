package com.phoneai.overlay;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.widget.LinearLayout;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/** 設定 → AI 與搜尋 → GitHub（MCP）：貼權杖、連線測試、移除。 */
final class McpUi {
    private McpUi() { }

    private static final String TOKEN_URL = "https://github.com/settings/personal-access-tokens/new";

    static String summary(Activity a) {
        return McpTools.hasToken(a) ? String.format(L.t("已設定；可用的唯讀工具 %d 個"), McpTools.toolCount(a)) : L.t("讓 AI 能查你的 GitHub（Release、編譯結果、PR、Issue），只讀不寫");
    }

    static void open(final Activity a, final Runnable after) {
        final TextInputLayout til = new TextInputLayout(a);
        til.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        til.setHint(McpTools.hasToken(a) ? L.t("已有權杖；貼上新的會取代") : L.t("貼上 GitHub 權杖（github_pat_ 或 ghp_ 開頭）"));
        til.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
        final TextInputEditText et = new TextInputEditText(til.getContext());
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        til.addView(et);
        LinearLayout form = new LinearLayout(a);
        form.setOrientation(LinearLayout.VERTICAL);
        int fp = Ui.dp(a, 20);
        form.setPadding(fp, fp / 2, fp, 0);
        form.addView(Ui.body(a, L.t("接上 GitHub 官方的 MCP 服務之後，你可以直接問「最新版編譯成功了嗎？」「這個 PR 有沒有人評論？」，AI 會去查。")));
        form.addView(Ui.body(a, L.t("只開放唯讀的工具（查、讀），不會建立、修改、刪除任何東西。權杖請只給「讀取」權限，而且只勾你要查的 repo；權杖只存在手機裡。")));
        form.addView(Ui.body(a, L.t("提醒：GitHub 上別人寫的內容（Issue、PR 留言）會被當成不可信的資料，AI 不會照裡面的話做事。查到的內容會傳給你選的 AI 服務。")));
        form.addView(til);
        PageBuilder b = new PageBuilder(a).setTitle(L.t("GitHub（MCP）")).setView(form)
                .setPositiveButton(L.t("儲存並測試連線"), (d, w) -> {
                    final String k = et.getText().toString();
                    if (k.trim().isEmpty() && !McpTools.hasToken(a)) return;
                    android.widget.Toast.makeText(a, L.t("連線中…"), android.widget.Toast.LENGTH_SHORT).show();
                    new Thread(() -> {
                        String saveErr = k.trim().isEmpty() ? null : Maintenance.saveKey(k, McpTools.TOKEN_FILE);
                        final String r = saveErr != null ? saveErr : McpTools.refresh(a.getApplicationContext());
                        a.runOnUiThread(() -> {
                            android.widget.Toast.makeText(a, r, android.widget.Toast.LENGTH_LONG).show();
                            if (after != null) after.run();
                        });
                    }).start();
                })
                .setNeutralButton(L.t("開啟申請頁面"), (d, w) -> {
                    try { a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(TOKEN_URL))); } catch (Exception ignored) { }
                });
        if (McpTools.hasToken(a)) b.setNegativeButton(L.t("移除權杖"), (d, w) -> {
            McpTools.clear(a);
            android.widget.Toast.makeText(a, L.t("已移除"), android.widget.Toast.LENGTH_SHORT).show();
            if (after != null) after.run();
        });
        b.show();
    }
}
