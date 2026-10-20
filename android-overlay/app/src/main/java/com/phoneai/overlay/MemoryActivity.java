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

/**
 * 助理的記憶：看全部待辦與筆記，可以新增、編輯、完成、刪除。資料是 Termux 的 ~/memory.json（只有 Termux 和 root 讀得到），
 * 所以用 root 讀寫（寫入是覆蓋原檔內容，檔案擁有者與 600 權限不變）。格式與 scripts/memory.py 一致；刪除 = 標記 deleted（跟 Python 一樣不真的移除）。
 */
public class MemoryActivity extends Activity {
    private static final String FILE = "/data/data/com.termux/files/home/memory.json";
    /** App 自己的 su 預設在「只看得到自己資料夾」的掛載空間裡（Android 的 App 資料隔離），看不到 Termux 的檔案；
     *  所以依序試幾種「跳到系統全域掛載空間」的 su 寫法，第一個看得到檔案的就記起來。 */
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
    private volatile int suMode = 0;
    private LinearLayout list;
    private TextView status;
    private JSONObject data;
    private float d;
    private String diag = "";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        d = getResources().getDisplayMetrics().density;
        LinearLayout[] c = new LinearLayout[1];
        setContentView(Ui.screen(this, "助理的記憶", true, c));
        LinearLayout box = c[0];
        status = Ui.infoCard(this, box, 13, false, true);
        Ui.buttons(this, box,
                Ui.pill(this, "＋ 待辦", true, v -> edit(null, "todo")),
                Ui.pill(this, "＋ 筆記", false, v -> edit(null, "note")),
                Ui.pill(this, "重新整理", false, v -> reload()));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        box.addView(list);
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    // ---------- 讀寫（root） ----------

    private void reload() {
        status.setText("讀取中…");
        new Thread(() -> {
            String err = "", info = "";
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
                    info += "方式" + n + "：結束碼 " + code + "｜" + head.replace('\n', '｜') + "\n";
                    if (body.startsWith("{")) {
                        got = new JSONObject(body);
                        if (got.optJSONArray("items") == null) got.put("items", new JSONArray());
                        suMode = n;
                        info += "→ 用方式" + n + "讀到了\n";
                    }
                } catch (Exception e) {
                    info += "方式" + n + "：" + e + "\n";
                }
            }
            if (got == null) err = "每種 su 寫法都讀不到 memory.json（可能真的還沒有資料，或 root 管理器不支援切換掛載空間）";
            final JSONObject g = got;
            final String e = err, inf = info;
            runOnUiThread(() -> {
                diag = inf;
                if (g != null) { data = g; render(); } else status.setText(e + "\n" + inf);
            });
        }).start();
    }

    private void saveThen(Runnable after) {
        new Thread(() -> {
            String err = null;
            try {
                String json = data.toString();
                // 命令用標準輸入餵給 sh：cat > 檔案 是覆蓋內容，不換檔案，所以擁有者與權限不變
                Process p = new ProcessBuilder(attempt(suMode, "sh")).redirectErrorStream(true).start();
                p.getOutputStream().write(("cat > " + FILE + " <<'PHONEAI_EOF'\n" + json + "\nPHONEAI_EOF\nexit\n").getBytes(StandardCharsets.UTF_8));
                p.getOutputStream().close();
                String out = readAll(p.getInputStream());
                if (p.waitFor() != 0) err = "寫入失敗：" + out;
            } catch (Exception e) {
                err = "寫入失敗：" + e.getMessage();
            }
            final String e = err;
            runOnUiThread(() -> { if (e != null) status.setText(e); after.run(); });
        }).start();
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
        int todo = section("待辦", items, "todo", false, "circle"), done = section("已完成", items, "todo", true, "check_circle"),
                note = section("筆記", items, "note", false, "note");
        int les = section("AI 學到的經驗（下次做任務會參考）", items, "lesson", false, "sparkle");
        status.setText("待辦 " + todo + " · 已完成 " + done + " · 筆記 " + note + " · 經驗 " + les + "\n點一項可編輯；有圖釘的是永久記住，AI 不能刪" + (diag.isEmpty() ? "" : "\n" + diag));
    }

    private int section(String name, JSONArray items, String kind, boolean doneOnes, String icon) {
        java.util.List<JSONObject> hit = new java.util.ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject it = items.optJSONObject(i);
            if (it == null || it.optBoolean("deleted") || !kind.equals(it.optString("kind"))) continue;
            if (it.optBoolean("done") != doneOnes) continue;
            hit.add(it);
        }
        Ui.section(this, list, name + "（" + hit.size() + "）");
        if (hit.isEmpty()) {
            Ui.row(this, list, 0, 1, icon, "沒有項目", null, null, null);
            return 0;
        }
        for (int i = 0; i < hit.size(); i++) {
            final JSONObject it = hit.get(i);
            String created = it.optString("created", "");
            View pin = null;
            if (it.optBoolean("pinned")) {
                pin = new IconView(this, "pin", Ui.attr(this, com.google.android.material.R.attr.colorPrimary));
                pin.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, 20), Ui.dp(this, 20)));
            }
            Ui.row(this, list, i, hit.size(), icon, it.optString("text"), created.length() >= 10 ? created.substring(5, 10).replace('-', '/') : null, pin, v -> edit(it, kind));
        }
        return hit.size();
    }

    /** it == null 是新增。 */
    private void edit(JSONObject it, String kind) {
        final com.google.android.material.textfield.TextInputLayout til = new com.google.android.material.textfield.TextInputLayout(this);
        til.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
        til.setHint(it == null ? "內容" : "編輯內容");
        final com.google.android.material.textfield.TextInputEditText et = new com.google.android.material.textfield.TextInputEditText(til.getContext());
        et.setText(it == null ? "" : it.optString("text"));
        et.setSelection(et.getText().length());
        et.setMinLines(2);
        til.addView(et);
        final com.google.android.material.materialswitch.MaterialSwitch pin = new com.google.android.material.materialswitch.MaterialSwitch(this);
        pin.setText("永久記住（沒有期限，AI 不能改或刪，只能在這裡手動刪）");
        pin.setChecked(it != null && it.optBoolean("pinned"));
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int fp = (int) (20 * d);
        form.setPadding(fp, fp / 2, fp, 0);
        form.addView(til);
        if (!"lesson".equals(kind)) form.addView(pin);
        com.google.android.material.dialog.MaterialAlertDialogBuilder b = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle(it == null ? "新增" : "編輯").setView(form)
                .setPositiveButton("儲存", (dlg, w) -> {
                    String t = et.getText().toString().trim();
                    if (t.isEmpty()) return;
                    try {
                        if (it == null) {
                            int id = data.optInt("next_id", 1);
                            data.put("next_id", id + 1);
                            data.getJSONArray("items").put(new JSONObject().put("id", id).put("kind", kind).put("text", t)
                                    .put("created", new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm", java.util.Locale.US).format(new java.util.Date()))
                                    .put("done", false).put("done_at", JSONObject.NULL).put("deleted", false).put("pinned", pin.isChecked()));
                        } else {
                            it.put("text", t);
                            it.put("pinned", pin.isChecked());
                        }
                    } catch (Exception ignored) { }
                    saveThen(this::render);
                })
                .setNegativeButton("取消", null);
        if (it != null) {
            b.setNeutralButton("刪除", (dlg, w) -> new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setMessage("刪除「" + it.optString("text") + "」？")
                    .setPositiveButton("刪除", (d2, w2) -> {
                        try { it.put("deleted", true); } catch (Exception ignored) { }
                        saveThen(this::render);
                    }).setNegativeButton("取消", null).show());
            if ("todo".equals(kind)) {
                b.setNegativeButton(it.optBoolean("done") ? "改回未完成" : "標為完成", (dlg, w) -> {
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
