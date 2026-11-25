package com.phoneai.overlay;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;

/** 設定 → AI 與資料 → 其他 MCP 服務：新增（網址＋權杖，或用 OAuth 登入）、重新連線、登出、移除。 */
final class McpServersUi {
    private McpServersUi() { }

    private static volatile int loginSeq;

    static String summary(Activity a) {
        int n = McpServers.list(a).size();
        return n == 0 ? L.t("接上 Notion、Linear 等提供 MCP 的服務（可用 OAuth 登入）") : String.format(L.t("已接上 %d 個服務"), n);
    }

    static void open(final Activity a, final Runnable after) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int fp = Ui.dp(a, 20);
        box.setPadding(fp, fp / 2, fp, 0);
        box.addView(Ui.body(a, L.t("加入服務提供的 MCP 網址之後，聊天時 AI 就能用它的工具（例如查筆記、看待辦）。服務標示唯讀的工具直接用；其他可能修改資料的工具，每次都會先問你。")));
        box.addView(Ui.body(a, L.t("提醒：查到的內容會傳給你選的 AI 服務；服務裡別人寫的內容只當資料，AI 不會照裡面的話做事。")));
        List<McpServers.Server> l = McpServers.list(a);
        if (!l.isEmpty()) Ui.section(a, box, L.t("已接上的服務"));
        for (int i = 0; i < l.size(); i++) {
            final McpServers.Server s = l.get(i);
            int n = McpServers.tools(a, s.id).length();
            String type = McpServers.authType(a, s.id);
            String sub = s.url + "\n" + (type.equals("oauth") ? L.t("已用 OAuth 登入") : type.equals("token") ? L.t("用權杖") : L.t("沒有登入")) + "｜" + (n > 0 ? String.format(L.t("%d 個工具"), n) : L.t("還沒連上"));
            Ui.row(a, box, i, l.size(), "terminal", s.name, sub, Ui.chevron(a), v -> server(a, s, after));
        }
        new PageBuilder(a).setTitle(L.t("其他 MCP 服務")).setView(box)
                .setPositiveButton(L.t("新增服務"), (d, w) -> add(a, after))
                .show();
    }

    private static TextInputEditText field(Activity a, LinearLayout form, String hint, boolean secret) {
        TextInputLayout til = new TextInputLayout(a);
        til.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        til.setHint(hint);
        if (secret) til.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
        TextInputEditText et = new TextInputEditText(til.getContext());
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT | (secret ? android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD : android.text.InputType.TYPE_TEXT_VARIATION_URI));
        til.addView(et);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(a, 8);
        form.addView(til, lp);
        return et;
    }

    private static void add(final Activity a, final Runnable after) {
        LinearLayout form = new LinearLayout(a);
        form.setOrientation(LinearLayout.VERTICAL);
        int fp = Ui.dp(a, 20);
        form.setPadding(fp, fp / 2, fp, 0);
        form.addView(Ui.body(a, L.t("填服務官方文件寫的 MCP 網址（https 開頭，例如 https://mcp.notion.com/mcp）。權杖可以不填：服務支援 OAuth 的話，會打開瀏覽器讓你登入並按同意。")));
        final TextInputEditText name = field(a, form, L.t("名稱（例如 Notion）"), false);
        final TextInputEditText url = field(a, form, L.t("MCP 網址"), false);
        final TextInputEditText token = field(a, form, L.t("權杖（選填）"), true);
        new PageBuilder(a).setTitle(L.t("新增 MCP 服務")).setView(form)
                .setPositiveButton(L.t("連線"), (d, w) -> {
                    String u = url.getText().toString().trim(), n = name.getText().toString().trim(), t = token.getText().toString().trim();
                    String bad = McpServers.checkUrl(u);
                    if (bad != null) { Toast.makeText(a, bad, Toast.LENGTH_LONG).show(); return; }
                    if (n.isEmpty()) { try { n = Uri.parse(u).getHost(); } catch (Exception e) { n = u; } }
                    try {
                        String id = McpServers.put(a, n, u);
                        if (!t.isEmpty()) McpServers.saveToken(a, id, t);
                        connect(a, id, after);
                    } catch (Exception e) {
                        Toast.makeText(a, L.t("存檔失敗：") + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    private static void server(final Activity a, final McpServers.Server s, final Runnable after) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int fp = Ui.dp(a, 20);
        box.setPadding(fp, fp / 2, fp, 0);
        String type = McpServers.authType(a, s.id);
        box.addView(Ui.body(a, s.url));
        box.addView(Ui.body(a, (type.equals("oauth") ? L.t("已用 OAuth 登入") : type.equals("token") ? L.t("用權杖") : L.t("沒有登入"))
                + "｜" + String.format(L.t("%d 個工具"), McpServers.tools(a, s.id).length())));
        box.addView(Ui.tonal(a, L.t("移除這個服務"), v -> new PageBuilder(a).setTitle(L.t("移除服務？")).setMessage(String.format(L.t("會刪掉「%s」的網址、登入資料與工具清單。"), s.name))
                .setPositiveButton(L.t("移除"), (d, w) -> {
                    McpServers.remove(a, s.id);
                    Toast.makeText(a, L.t("已移除"), Toast.LENGTH_SHORT).show();
                    if (after != null) after.run();
                }).show()));
        PageBuilder b = new PageBuilder(a).setTitle(s.name).setView(box)
                .setPositiveButton(L.t("重新連線"), (d, w) -> connect(a, s.id, after));
        if (!type.isEmpty()) b.setNeutralButton(L.t("登出"), (d, w) -> {
            McpServers.clearAuth(a, s.id);
            Toast.makeText(a, L.t("已登出"), Toast.LENGTH_SHORT).show();
            if (after != null) after.run();
        });
        b.show();
    }

    private static void toast(final Activity a, final String msg) {
        a.runOnUiThread(() -> Toast.makeText(a, msg, Toast.LENGTH_LONG).show());
    }

    /** 連線抓工具；服務要登入（401）而且沒設權杖，就走 OAuth 登入再連一次。 */
    static void connect(final Activity a, final String id, final Runnable after) {
        toast(a, L.t("連線中…"));
        final int seq = ++loginSeq;   // 又按了一次：前一次還在等瀏覽器的就放棄
        new Thread(() -> {
            String r;
            try {
                r = McpServers.connect(a.getApplicationContext(), id);
            } catch (McpClient.AuthRequired e) {
                if (McpServers.authType(a, id).equals("token")) r = L.t("權杖被拒絕：請確認權杖沒有過期、有需要的權限");
                else r = login(a, id, e.wwwAuth, seq);
            } catch (Exception e) {
                r = L.t("連線失敗：") + e.getMessage();
            }
            final String msg = r;
            a.runOnUiThread(() -> {
                Toast.makeText(a, msg, Toast.LENGTH_LONG).show();
                if (after != null) after.run();
            });
        }).start();
    }

    private static String login(final Activity a, final String id, String wwwAuth, final int seq) {
        McpServers.Server s = McpServers.byId(a, id);
        if (s == null) return L.t("找不到這個服務");
        try {
            McpOAuth.Session session = McpOAuth.login(s.url, wwwAuth, new McpOAuth.Ui() {
                @Override public void progress(String msg) { toast(a, msg); }
                @Override public void openBrowser(String url) {
                    a.runOnUiThread(() -> {
                        try { a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
                        catch (Exception e) { Toast.makeText(a, L.t("打不開瀏覽器"), Toast.LENGTH_LONG).show(); }
                    });
                }
                @Override public boolean cancelled() { return seq != loginSeq; }
            });
            McpServers.saveOAuth(a, id, session);
            try {   // 從瀏覽器回到 App（有「顯示在其他應用程式上層」權限才允許從背景帶回前景；不行就讓使用者自己切回來）
                a.startActivity(new Intent(a, a.getClass()).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            } catch (Exception ignored) { }
            return McpServers.connect(a.getApplicationContext(), id);
        } catch (Exception e) {
            return L.t("登入失敗：") + e.getMessage();
        }
    }
}
