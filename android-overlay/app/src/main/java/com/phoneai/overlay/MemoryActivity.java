package com.phoneai.overlay;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 助理的記憶：看全部待辦與筆記，可以新增、編輯、完成、刪除。資料在 App 自己的資料夾，App 內用 root 讀寫，
 * 所以用 root 讀寫（寫入是覆蓋原檔內容，檔案擁有者與 600 權限不變）。刪除 = 標記 deleted（不真的移除）。
 */
public class MemoryActivity extends Activity {
    private static final String FILE = "/data/data/com.phoneai.overlay/files/memory.json"; 
    /** App 自己的 su 預設在「只看得到自己資料夾」的掛載空間裡；
     *  依序試幾種「跳到系統全域掛載空間」的 su 寫法，第一個看得到檔案的就記起來。 */
    private static String[] attempt(int n, String cmd) {
        switch (n) {
            case 0: return new String[]{Root.su(), "-mm", "-c", cmd};
            case 1: return new String[]{Root.su(), "-M", "-c", cmd};
            case 2: return new String[]{Root.su(), "--mount-master", "-c", cmd};
            case 3: return new String[]{Root.su(), "-c", "nsenter --mount=/proc/1/ns/mnt -- sh -c '" + cmd.replace("'", "'\\''") + "'"};
            default: return new String[]{Root.su(), "-c", cmd};
        }
    }
    private static final int ATTEMPTS = 5;
    private static volatile int suMode = 0;
    private LinearLayout list;
    private TextView status;
    private JSONObject data;
    private static volatile JSONObject cache;   // 同一次 App 執行中記住上次讀到的資料
    private float d;
    private String diag = "";
    private String query = "";   // 搜尋框的文字（小寫）；空字串 = 全部顯示
    private static final String BACKUP_DIR = "/sdcard/Download/", BACKUP_PREFIX = "phoneai_memory_backup";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        d = getResources().getDisplayMetrics().density;
        LinearLayout[] c = new LinearLayout[1];
        setContentView(Ui.screen(this, L.t("助理的記憶"), true, c));
        Ui.enterSpring(this);
        LinearLayout box = c[0];
        status = Ui.infoCard(this, box, 13, false, true);
        com.google.android.material.textfield.TextInputLayout til = new com.google.android.material.textfield.TextInputLayout(this);
        til.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
        til.setHint(L.t("搜尋待辦、筆記"));
        com.google.android.material.textfield.TextInputEditText search = new com.google.android.material.textfield.TextInputEditText(til.getContext());
        search.setSingleLine(true);
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) { }
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable e) {
                query = e.toString().trim().toLowerCase(Locale.ROOT);
                if (data != null) render();
            }
        });
        til.addView(search);
        box.addView(til);
        Ui.buttons(this, box,
                Ui.pill(this, L.t("＋ 待辦"), true, v -> edit(null, "todo")),
                Ui.pill(this, L.t("＋ 筆記"), false, v -> edit(null, "note")),
                Ui.pill(this, L.t("重新整理"), false, v -> reload()));
        Ui.buttons(this, box,
                Ui.pill(this, L.t("匯出"), false, v -> exportMemory()),
                Ui.pill(this, L.t("匯入"), false, v -> importMemory()));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        box.addView(list);
        if (cache != null) { data = cache; render(); }   // 同一次 App 執行中，再進來時立刻顯示
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    // ---------- 讀寫（root） ----------

    /** 用 root 讀 memory.json（主畫面也會呼叫，先在背景讀好）。結果說明寫進 info。 */
    static JSONObject readMemory(StringBuilder info) {
        // 檔案就在 App 自己的資料夾：直接讀，不需要 root
        if (App.ctx != null) return Memory.load(App.ctx);
        JSONObject got = null;
        String script = "id -u; ls -l " + FILE + " 2>&1; echo =====; cat " + FILE + " 2>&1";
        for (int n = 0; n < ATTEMPTS && got == null; n++) {
            try {
                Process p = new ProcessBuilder(attempt(n, script)).redirectErrorStream(true).start();
                p.getOutputStream().close();
                String out = readAll(p.getInputStream());
                int code = p.waitFor();
                int k = out.indexOf("=====\n");
                String head = k < 0 ? out.trim() : out.substring(0, k).trim();
                String body = k < 0 ? "" : out.substring(k + 6).trim();
                info.append(L.t("方式")).append(n).append(L.t("：結束碼 ")).append(code).append(L.t("｜")).append(head.replace('\n', '｜')).append('\n');
                if (body.startsWith("{")) {
                    got = new JSONObject(body);
                    if (got.optJSONArray("items") == null) got.put("items", new JSONArray());
                    suMode = n;
                    info.append(L.t("→ 用方式")).append(n).append(L.t("讀到了\n"));
                }
            } catch (Exception e) {
                info.append(L.t("方式")).append(n).append(L.t("：")).append(e).append('\n');
            }
        }
        return got;
    }

    /** 主畫面開啟時呼叫（背景執行緒）：先把資料讀進快取，點進記憶頁就不用等。 */
    static void warm() {
        if (cache != null) return;
        JSONObject g = readMemory(new StringBuilder());
        if (g != null) cache = g;
    }

    private void reload() {
        if (data == null) status.setText(L.t("讀取中…"));   // 已有上次的資料就先顯示，背景再更新
        new Thread(() -> {
            StringBuilder sb = new StringBuilder();
            JSONObject got = readMemory(sb);
            String err = got == null ? L.t("每種 su 寫法都讀不到 memory.json（可能真的還沒有資料，或 root 管理器不支援切換掛載空間）") : "";
            String info = sb.toString();
            final JSONObject g = got;
            final String e = err, inf = info;
            runOnUiThread(() -> {
                diag = inf;
                if (g != null) { data = g; cache = g; render(); } else status.setText(e + "\n" + inf);
            });
        }).start();
    }

    private void saveThen(Runnable after) {
        new Thread(() -> {
            String err = null;
            try {
                Memory.save(this, data);   // 檔案在 App 自己的資料夾，直接寫，不需要 root
            } catch (Exception e) {
                err = L.t("寫入失敗：") + e.getMessage();
            }
            final String e = err;
            runOnUiThread(() -> { if (e != null) status.setText(e); after.run(); });
        }).start();
    }

    /** 匯出／匯入備份要讀寫下載資料夾：有 root 用 root，沒有就用 App 自己的檔案權限（需要「所有檔案存取」）。 */
    private static String run(String script) {
        return Root.available() ? Maintenance.suRun(script) : Phone.shellRead(script);
    }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
        return o.toString("UTF-8");
    }

    // ---------- 畫面 ----------

    private void render() {
        list.removeAllViews();
        JSONArray items = data.optJSONArray("items");
        int todo = section(L.t("待辦"), items, "todo", false, "circle"), done = section(L.t("已完成"), items, "todo", true, "check_circle"),
                note = section(L.t("筆記"), items, "note", false, "note");
        int les = section(L.t("AI 學到的經驗（下次做任務會參考）"), items, "lesson", false, "sparkle");
        status.setText(L.t("待辦 ") + todo + L.t(" · 已完成 ") + done + L.t(" · 筆記 ") + note + L.t(" · 經驗 ") + les + L.t("\n點一項可編輯；有圖釘的是永久記住，AI 不能刪") + (diag.isEmpty() ? "" : "\n" + diag));
    }

    private int section(String name, JSONArray items, String kind, boolean doneOnes, String icon) {
        java.util.List<JSONObject> hit = new java.util.ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject it = items.optJSONObject(i);
            if (it == null || it.optBoolean("deleted") || !kind.equals(it.optString("kind"))) continue;
            if (it.optBoolean("done") != doneOnes) continue;
            if (!query.isEmpty() && !it.optString("text").toLowerCase(Locale.ROOT).contains(query)) continue;
            hit.add(it);
        }
        Ui.section(this, list, name + L.t("（") + hit.size() + L.t("）"));
        if (hit.isEmpty()) {
            Ui.row(this, list, 0, 1, icon, L.t("沒有項目"), null, null, null);
            return 0;
        }
        for (int i = 0; i < hit.size(); i++) {
            final JSONObject it = hit.get(i);
            String created = it.optString("created", "");
            View pin = null;
            if (!"lesson".equals(kind)) {   // 每一列右邊的星星：空心＝普通，實心（主題色）＝永久記憶（AI 不能改或刪）；點一下就切換，不用點開
                final boolean on = it.optBoolean("pinned");
                final StarView star = new StarView(this, Ui.attr(this, com.google.android.material.R.attr.colorPrimary),
                        Ui.attr(this, com.google.android.material.R.attr.colorOnSurfaceVariant));
                star.setOn(on, false);
                star.setOnClickListener(v -> {
                    star.setOn(!on, true);   // 先彈一下，再存檔重畫
                    star.postDelayed(() -> {
                        try { it.put("pinned", !on); } catch (Exception ignored) { }
                        saveThen(this::render);
                    }, 320);
                });
                pin = star;
            }
            Ui.row(this, list, i, hit.size(), icon, it.optString("text"), created.length() >= 10 ? created.substring(5, 10).replace('-', '/') : null, pin, v -> edit(it, kind));
        }
        return hit.size();
    }

    // ---------- 匯出／匯入（下載資料夾的 phoneai_memory_backup.json，格式就是 items 陣列） ----------

    /** 匯出：待辦、筆記、經驗（不含已刪除的）寫到下載資料夾，檔名帶時間，多次匯出不會互相覆蓋。用 root 寫。 */
    private void exportMemory() {
        if (data == null) return;
        JSONObject out = new JSONObject();
        JSONArray keep = new JSONArray();
        JSONArray items = data.optJSONArray("items");
        for (int i = 0; items != null && i < items.length(); i++) {
            JSONObject it = items.optJSONObject(i);
            if (it != null && !it.optBoolean("deleted")) keep.put(it);
        }
        try {
            out.put("items", keep);
        } catch (Exception ignored) { }
        final String json = out.toString();
        final String name = BACKUP_PREFIX + "_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(new java.util.Date()) + ".json";
        final String path = BACKUP_DIR + name;
        new Thread(() -> {
            String res = run("cat > " + path + " <<'PHONEAI_EOF'\n" + json + "\nPHONEAI_EOF\n[ -s " + path + " ] && echo PHONEAI_OK");
            final boolean ok = res.contains("PHONEAI_OK");
            runOnUiThread(() -> status.setText(ok ? L.t("已匯出到下載資料夾：") + name : L.t("匯出失敗：") + res.trim()));
        }).start();
    }

    /** 匯入：列出下載資料夾裡的備份（新的在前）讓使用者選一個，再確認；只加入還沒有的項目。 */
    private void importMemory() {
        new Thread(() -> {
            String out = run("ls -1t " + BACKUP_DIR + BACKUP_PREFIX + "*.json 2>/dev/null");
            final java.util.List<String> names = new java.util.ArrayList<>();
            for (String line : out.split("\n")) {
                String l = line.trim();
                if (!l.startsWith(BACKUP_DIR)) continue;
                String n = l.substring(BACKUP_DIR.length());
                if (n.matches(BACKUP_PREFIX + "[A-Za-z0-9_\\-]*\\.json")) names.add(n);
            }
            runOnUiThread(() -> {
                if (names.isEmpty()) {
                    status.setText(L.t("找不到可以匯入的備份：先按「匯出」，會存到下載資料夾"));
                    return;
                }
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle(L.t("選擇要匯入的備份"))
                        .setItems(names.toArray(new String[0]), (dlg, which) -> confirmImport(names.get(which)))
                        .setNegativeButton(L.t("取消"), null).show();
            });
        }).start();
    }

    private void confirmImport(String name) {
        new Thread(() -> {
            JSONArray in = parseItems(run("cat " + BACKUP_DIR + name + " 2>/dev/null"));
            runOnUiThread(() -> {
                if (in == null) {
                    status.setText(L.t("這個備份讀不了"));
                    return;
                }
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setMessage(String.format(L.t("匯入 %d 則？只會加入還沒有的項目，不會刪除或覆蓋現有的。"), in.length()))
                        .setPositiveButton(L.t("匯入"), (d2, w2) -> mergeAndSave(in))
                        .setNegativeButton(L.t("取消"), null).show();
            });
        }).start();
    }

    private static JSONArray parseItems(String raw) {
        try {
            String t = raw.trim();
            if (!t.startsWith("{")) return null;
            return new JSONObject(t).optJSONArray("items");
        } catch (Exception e) {
            return null;
        }
    }

    private void mergeAndSave(JSONArray in) {
        if (data == null) return;
        int added = 0;
        try {
            JSONArray items = data.getJSONArray("items");
            for (int i = 0; i < in.length(); i++) {
                JSONObject it = in.optJSONObject(i);
                if (it == null) continue;
                String kind = it.optString("kind"), text = it.optString("text");
                if (kind.isEmpty() || text.isEmpty() || hasSame(items, kind, text)) continue;
                int id = data.optInt("next_id", 1);
                data.put("next_id", id + 1);
                items.put(new JSONObject(it.toString()).put("id", id).put("deleted", false));
                added++;
            }
        } catch (Exception e) {
            status.setText(L.t("匯入失敗：") + e.getMessage());
            return;
        }
        android.widget.Toast.makeText(this, L.t("已加入 ") + added + L.t(" 則"), android.widget.Toast.LENGTH_SHORT).show();
        saveThen(this::render);
    }

    private static boolean hasSame(JSONArray items, String kind, String text) {
        for (int i = 0; i < items.length(); i++) {
            JSONObject it = items.optJSONObject(i);
            if (it != null && !it.optBoolean("deleted") && kind.equals(it.optString("kind")) && text.equals(it.optString("text"))) return true;
        }
        return false;
    }

    /** it == null 是新增。 */
    private void edit(JSONObject it, String kind) {
        final com.google.android.material.textfield.TextInputLayout til = new com.google.android.material.textfield.TextInputLayout(this);
        til.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
        til.setHint(it == null ? L.t("內容") : L.t("編輯內容"));
        final com.google.android.material.textfield.TextInputEditText et = new com.google.android.material.textfield.TextInputEditText(til.getContext());
        et.setText(it == null ? "" : it.optString("text"));
        et.setSelection(et.getText().length());
        et.setMinLines(2);
        til.addView(et);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int fp = (int) (20 * d);
        form.setPadding(fp, fp / 2, fp, 0);
        form.addView(til);
        com.google.android.material.dialog.MaterialAlertDialogBuilder b = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle(it == null ? L.t("新增") : L.t("編輯")).setView(form)
                .setPositiveButton(L.t("儲存"), (dlg, w) -> {
                    String t = et.getText().toString().trim();
                    if (t.isEmpty()) return;
                    try {
                        if (it == null) {
                            int id = data.optInt("next_id", 1);
                            data.put("next_id", id + 1);
                            data.getJSONArray("items").put(new JSONObject().put("id", id).put("kind", kind).put("text", t)
                                    .put("created", new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm", java.util.Locale.US).format(new java.util.Date()))
                                    .put("done", false).put("done_at", JSONObject.NULL).put("deleted", false).put("pinned", false));
                        } else {
                            it.put("text", t);
                        }
                    } catch (Exception ignored) { }
                    saveThen(this::render);
                })
                .setNegativeButton(L.t("取消"), null);
        if (it != null) {
            b.setNeutralButton(L.t("刪除"), (dlg, w) -> new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setMessage(L.t("刪除「") + it.optString("text") + L.t("」？"))
                    .setPositiveButton(L.t("刪除"), (d2, w2) -> {
                        try { it.put("deleted", true); } catch (Exception ignored) { }
                        saveThen(this::render);
                    }).setNegativeButton(L.t("取消"), null).show());
            if ("todo".equals(kind)) {
                b.setNegativeButton(it.optBoolean("done") ? L.t("改回未完成") : L.t("標為完成"), (dlg, w) -> {
                    try {
                        boolean nd = !it.optBoolean("done");
                        it.put("done", nd);
                        it.put("done_at", nd ? new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm", java.util.Locale.US).format(new java.util.Date()) : JSONObject.NULL);
                    } catch (Exception ignored) { }
                    saveThen(this::render);
                });
            }
        }
        b.show();
    }
}
