package com.phoneai.overlay;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 「AI 服務」設定流程：選範圍（全部／只便宜模型／只看圖模型）→ 選服務 → 申請 Key 教學 → 填網址與模型 → 輸入 Key → 可查詢服務現有的模型。
 * 便宜模型與看圖模型可以選不同的服務（例如便宜的用 DeepSeek、看圖的用 Gemini）。
 */
final class AiServiceUi {
    private AiServiceUi() { }

    private static final String ALL = "all";

    static void open(Activity a, Runnable done) {
        String[] items = {L.t("兩個都用同一家（簡單）"), L.t("只設定便宜模型（判斷、讀元件清單）"), L.t("只設定看圖模型（截圖）")};
        String[] scopes = {ALL, LlmConfig.CHEAP, LlmConfig.VISION};
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(a, 20);
        box.setPadding(p, p / 2, p, 0);
        box.addView(Ui.body(a, L.t("目前：便宜模型 ") + LlmConfig.cheap(a).label() + L.t("｜看圖模型 ") + LlmConfig.vision(a).label()
                + L.t("\n\n兩個可以選不同的服務，例如便宜的用 DeepSeek、看圖的用 Gemini。")));
        final androidx.appcompat.app.AlertDialog[] dlg = new androidx.appcompat.app.AlertDialog[1];
        for (int i = 0; i < items.length; i++) {
            final String scope = scopes[i];
            com.google.android.material.button.MaterialButton b = Ui.pill(a, items[i], i == 0, v -> { dlg[0].dismiss(); chooseService(a, scope, done); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Ui.dp(a, 48));
            lp.topMargin = Ui.dp(a, 10);
            box.addView(b, lp);
        }
        dlg[0] = new MaterialAlertDialogBuilder(a).setTitle(L.t("AI 服務與 API Key")).setView(box).setNegativeButton(L.t("取消"), null).show();
    }

    private static void chooseService(Activity a, String scope, Runnable done) {
        String[] names = new String[LlmPresets.ALL.length + 1];
        names[0] = "Claude";
        for (int i = 0; i < LlmPresets.ALL.length; i++) names[i + 1] = L.t(LlmPresets.ALL[i].name);
        new MaterialAlertDialogBuilder(a).setTitle(L.t("選擇 AI 服務"))
                .setItems(names, (d, which) -> {
                    if (which == 0) {
                        for (String s : slots(scope)) LlmConfig.set(a, s, LlmConfig.ANTHROPIC, "", "");
                        done.run();
                    } else guide(a, scope, LlmPresets.ALL[which - 1], done);
                })
                .setNegativeButton(L.t("取消"), null).show();
    }

    private static String[] slots(String scope) {
        return ALL.equals(scope) ? new String[]{LlmConfig.CHEAP, LlmConfig.VISION} : new String[]{scope};
    }

    private static void guide(Activity a, String scope, LlmPresets.Preset ps, Runnable done) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ps.steps.length; i++) sb.append(i + 1).append(". ").append(L.t(ps.steps[i])).append("\n\n");
        sb.append(L.t("Key 只會存在這支手機的 App 私有資料夾，不要貼給任何人（包括 AI 助手）。"));
        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(a).setTitle(L.t(ps.name)).setMessage(sb.toString())
                .setPositiveButton(L.t("下一步"), (d, w) -> {
                    if (!ps.base.isEmpty()) saveAndAskKey(a, scope, ps.base, ps.cheap, ps.vision, done);   // 網址與預設模型都是固定的，不用再填表單
                    else form(a, scope, ps, done);   // 「其他」才要自己填網址
                })
                .setNegativeButton(L.t("取消"), null);
        if (!ps.keyUrl.isEmpty()) b.setNeutralButton(L.t("開啟申請頁面"), (d, w) -> {
            try { a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(ps.keyUrl))); } catch (Exception ignored) { }
        });
        b.show();
    }

    private static EditText field(Activity a, LinearLayout form, String hint, String value) {
        TextInputLayout til = new TextInputLayout(a);
        til.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        til.setHint(hint);
        TextInputEditText et = new TextInputEditText(til.getContext());
        et.setSingleLine(true);
        et.setText(value);
        til.addView(et);
        form.addView(til);
        return et;
    }

    private static void form(Activity a, String scope, LlmPresets.Preset ps, Runnable done) {
        LinearLayout form = new LinearLayout(a);
        form.setOrientation(LinearLayout.VERTICAL);
        int fp = Ui.dp(a, 20);
        form.setPadding(fp, fp / 2, fp, 0);
        form.addView(Ui.body(a, L.t("填這個服務的網址（到 /v1 為止）和模型。下一步輸入 Key 之後，可以查詢這個服務現在有哪些模型再選。注意：操作手機的提示是照 Claude 調的，換別家要自己實測；網頁搜尋是 Claude 專屬；任務過程的費用會顯示 $0。")));
        boolean both = ALL.equals(scope);
        EditText base = field(a, form, L.t("網址"), ps.base.isEmpty() ? LlmConfig.DEF_BASE : ps.base);
        EditText cheap = LlmConfig.VISION.equals(scope) ? null : field(a, form, L.t("便宜模型"), ps.cheap);
        EditText vision = LlmConfig.CHEAP.equals(scope) ? null : field(a, form, L.t("看圖模型"), ps.vision);
        new MaterialAlertDialogBuilder(a).setTitle(L.t(ps.name)).setView(form)
                .setPositiveButton(L.t("儲存並輸入 Key"), (d, w) -> saveAndAskKey(a, scope, base.getText().toString(),
                        cheap == null ? "" : cheap.getText().toString(), vision == null ? "" : vision.getText().toString(), done))
                .setNegativeButton(L.t("取消"), null).show();
    }

    /** 存下網址與模型，接著輸入 Key，再問要不要查詢模型清單。 */
    private static void saveAndAskKey(Activity a, String scope, String b, String cheapModel, String visionModel, Runnable done) {
        if (!LlmConfig.VISION.equals(scope)) LlmConfig.set(a, LlmConfig.CHEAP, LlmConfig.OPENAI, b, cheapModel);
        if (!LlmConfig.CHEAP.equals(scope)) LlmConfig.set(a, LlmConfig.VISION, LlmConfig.OPENAI, b, visionModel);
        LlmConfig.Slot s = LlmConfig.VISION.equals(scope) ? LlmConfig.vision(a) : LlmConfig.cheap(a);
        Maintenance.askKeyFor(a, LlmConfig.keyFile(s), L.t("API Key：") + LlmConfig.host(b), () -> offerModelList(a, scope, done));
    }

    /** Key 存好後：問要不要查這個服務現有的模型（模型常改名，查了比猜準）。 */
    private static void offerModelList(Activity a, String scope, Runnable done) {
        new MaterialAlertDialogBuilder(a).setTitle(L.t("要查詢可用的模型嗎？"))
                .setMessage(L.t("向這個服務查詢現在有哪些模型，再選你要的。不查也可以，直接用預設值；之後模型被改名導致出錯時，再回來設定一次。"))
                .setPositiveButton(L.t("查詢"), (d, w) -> {
                    List<String> order = new ArrayList<>();
                    for (String s : slots(scope)) order.add(s);
                    pickModels(a, order, 0, done);
                })
                .setNegativeButton(L.t("用預設值"), (d, w) -> done.run()).show();
    }

    private static void pickModels(Activity a, List<String> order, int idx, Runnable done) {
        if (idx >= order.size()) { done.run(); return; }
        final String name = order.get(idx);
        final LlmConfig.Slot slot = LlmConfig.CHEAP.equals(name) ? LlmConfig.cheap(a) : LlmConfig.vision(a);
        android.widget.Toast.makeText(a, L.t("查詢中…"), android.widget.Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            List<String> ids = null;
            String err = null;
            try { ids = listModels(slot.base, LlmConfig.readKey(a, slot)); } catch (Exception e) { err = String.valueOf(e.getMessage()); }
            final List<String> models = ids;
            final String e = err;
            a.runOnUiThread(() -> {
                if (models == null || models.isEmpty()) {
                    android.widget.Toast.makeText(a, L.t("查詢失敗，保留預設值：") + (e == null ? "" : e), android.widget.Toast.LENGTH_LONG).show();
                    pickModels(a, order, idx + 1, done);
                    return;
                }
                String title = LlmConfig.CHEAP.equals(name) ? L.t("選便宜模型（判斷、讀元件清單）") : L.t("選看圖模型（要能看圖）");
                new MaterialAlertDialogBuilder(a).setTitle(title)
                        .setItems(models.toArray(new String[0]), (d, which) -> {
                            LlmConfig.set(a, name, LlmConfig.OPENAI, slot.base, models.get(which));
                            pickModels(a, order, idx + 1, done);
                        })
                        .setNegativeButton(L.t("保留目前的"), (d, w) -> pickModels(a, order, idx + 1, done)).show();
            });
        }).start();
    }

    /** GET {網址}/models（OpenAI 相容服務都有）；Gemini 回的名稱前面有 models/，去掉。 */
    static List<String> listModels(String base, String key) throws Exception {
        String b = base.trim();
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        HttpURLConnection con = (HttpURLConnection) new URL(b + "/models").openConnection();
        con.setConnectTimeout(15000);
        con.setReadTimeout(20000);
        if (key != null) con.setRequestProperty("Authorization", "Bearer " + key);
        int code = con.getResponseCode();
        java.io.InputStream in = code < 400 ? con.getInputStream() : con.getErrorStream();
        String text = in == null ? "" : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        if (code >= 400) throw new Exception("HTTP " + code);
        return parseModelIds(text);
    }

    static List<String> parseModelIds(String text) throws Exception {
        JSONObject r = new JSONObject(text);
        JSONArray data = r.optJSONArray("data");
        List<String> out = new ArrayList<>();
        for (int i = 0; data != null && i < data.length(); i++) {
            String id = data.getJSONObject(i).optString("id");
            if (id.startsWith("models/")) id = id.substring(7);
            if (!id.isEmpty() && !out.contains(id)) out.add(id);
        }
        Collections.sort(out);
        return out;
    }
}
