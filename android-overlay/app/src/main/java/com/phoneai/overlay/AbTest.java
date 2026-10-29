package com.phoneai.overlay;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AI 比較測試：同一批固定的小任務，分別用不同的 AI 服務組合各跑一遍（直接走自動操作，不經過固定指令與巨集），
 * 比成功與否、步數、時間與預估花費。任務都是無害、可重來的（開設定頁、算數、開時鐘）。
 * 每個任務跑完會回主畫面並強制關閉開過的 App，讓每一組都從同樣的起點開始。需要 root 或 Shizuku（才能強制關閉 App）。
 * 成功判斷是粗略的：看最後畫面上有沒有該有的文字／套件，不是 AI 自己說成功就算。
 */
final class AbTest {
    private AbTest() { }

    static volatile boolean running;
    static volatile boolean stopRequested;
    static volatile String progress = "";
    /** 跑完還沒給使用者看的結果（MainActivity 回到前景時顯示）。 */
    static volatile String unseen;

    /** mustSee＝最後畫面上要看得到其中一個文字；mustReply＝AI 的回答要含其中一個；mustPkg＝最後在前景的 App 套件要含這個字；extra＝測完還要一起強制關閉的 App。 */
    static final class Task {
        final String name, task, app, mustPkg;
        final String[] mustSee, mustReply, extra;
        Task(String name, String task, String app, String[] mustSee, String mustPkg, String[] mustReply, String... extra) {
            this.name = name; this.task = task; this.app = app; this.mustSee = mustSee; this.mustPkg = mustPkg; this.mustReply = mustReply; this.extra = extra;
        }
    }

    private static String[] a(String... s) { return s; }

    /** 簡單題：兩邊通常都做得到，用來確認基本功能。 */
    static final Task[] EASY = {
            new Task("Wi-Fi 頁面", "進入設定裡的 Wi-Fi 頁面", "設定", a("Wi-Fi"), "settings", null),
            new Task("計算 123＋45", "打開計算機，計算 123 加 45，告訴我結果", "計算機", null, null, a("168")),
            new Task("時鐘碼錶", "打開時鐘，進入碼錶頁面", "時鐘", null, "clock", null),
            new Task("設定搜尋藍牙", "打開設定，在搜尋欄輸入「藍牙」", "設定", a("藍牙"), "settings", null)
    };

    /** 困難題：要捲動找選項、連續算數、讀取資訊、跨 App；不保證每個模型都做得到，用來分出高下。 */
    static final Task[] HARD = {
            new Task("找軟體資訊", "打開設定，往下捲動找到「關於手機」，進入後再進入「軟體資訊」頁面", "設定", a("軟體資訊", "Software information", "One UI"), "settings", null),
            new Task("連續算數", "打開計算機，計算 (123 + 45) × 6 − 78，告訴我結果", "計算機", null, null, a("930")),
            new Task("讀取螢幕逾時", "打開設定，進入「顯示」，找到螢幕逾時（螢幕關閉時間）目前設定是多久，告訴我", "設定", null, null, a("秒", "分鐘", "分")),
            new Task("搜尋後進入結果", "打開設定，搜尋「螢幕解析度」，點進第一個搜尋結果", "設定", a("解析度", "Screen resolution", "resolution"), "settings", null),
            new Task("跨 App", "先打開計算機算 12 乘 12，記下結果，再打開時鐘進入碼錶頁面，最後告訴我算出的結果", "計算機", null, "clock", a("144"), "時鐘")
    };

    static Task[] tasks = EASY;

    static final class Config {
        final String name;
        final LlmConfig.Slot cheap, vision;
        Config(String name, LlmConfig.Slot cheap, LlmConfig.Slot vision) { this.name = name; this.cheap = cheap; this.vision = vision; }
    }

    /**
     * 可以比較的 API：Claude（有 Key 的話）和每個已經存了 Key 的服務。模型用你現在在「AI 服務與 API Key」設定的（如果那個服務正在用），
     * 沒有就用預設模型。想換模型可以用「自訂組合」。
     */
    static List<Config> candidates(Context c) {
        List<Config> out = new ArrayList<>();
        if (new File(c.getFilesDir(), "claude_key").length() > 0)
            out.add(new Config("Claude（Haiku 5.5＋Sonnet 5.5）", new LlmConfig.Slot(LlmConfig.ANTHROPIC, "", ""), new LlmConfig.Slot(LlmConfig.ANTHROPIC, "", "")));
        List<String> hosts = new ArrayList<>();
        for (LlmPresets.Preset ps : LlmPresets.ALL) {
            if (ps.base.isEmpty()) continue;
            LlmConfig.Slot a = modelsFor(c, ps.base, ps.cheap, true), b = modelsFor(c, ps.base, ps.vision, false);
            hosts.add(LlmConfig.host(ps.base));
            if (LlmConfig.readKey(c, a) != null) out.add(new Config(L.t(ps.shortName()) + "：" + label(a.model, b.model), a, b));
        }
        for (LlmConfig.Slot s : new LlmConfig.Slot[]{LlmConfig.cheap(c), LlmConfig.vision(c)}) {   // 自己填網址的服務（Ollama 等）
            if (!s.openai() || hosts.contains(LlmConfig.host(s.base)) || LlmConfig.readKey(c, s) == null) continue;
            hosts.add(LlmConfig.host(s.base));
            LlmConfig.Slot a = modelsFor(c, s.base, s.model, true), b = modelsFor(c, s.base, s.model, false);
            out.add(new Config(LlmConfig.host(s.base) + "：" + label(a.model, b.model), a, b));
        }
        out.addAll(custom);
        return out;
    }

    private static String label(String cheap, String vision) { return cheap.equals(vision) ? cheap : cheap + " / " + vision; }

    /** 這個服務現在如果正在用（便宜／看圖欄位的網址相同），就用你設定的模型；否則用預設。 */
    private static LlmConfig.Slot modelsFor(Context c, String base, String def, boolean cheap) {
        LlmConfig.Slot cur = cheap ? LlmConfig.cheap(c) : LlmConfig.vision(c);
        boolean same = cur.openai() && LlmConfig.host(cur.base) != null && LlmConfig.host(cur.base).equals(LlmConfig.host(base)) && !cur.model.isEmpty();
        return new LlmConfig.Slot(LlmConfig.OPENAI, base, same ? cur.model : def);
    }

    /** 使用者在比較測試裡臨時自訂的組合（只在這次 App 執行期間有效）。 */
    static final List<Config> custom = new ArrayList<>();

    static final class Result {
        final String config, task;
        final boolean ok;
        final int steps;
        final long ms;
        final double cost;
        final String note;
        Result(String config, String task, boolean ok, int steps, long ms, double cost, String note) {
            this.config = config; this.task = task; this.ok = ok; this.steps = steps; this.ms = ms; this.cost = cost; this.note = note;
        }
    }

    /** 在背景執行緒呼叫。跑完把結果文字存起來並放進 unseen。 */
    static void run(Context c, List<Config> configs, Task[] taskSet) {
        if (running) return;
        tasks = taskSet;
        running = true;
        stopRequested = false;
        List<Result> results = new ArrayList<>();
        AgentLoop.Ui ui = new AgentLoop.Ui() {
            @Override public void hide() { }
            @Override public void status(String text) { }
            @Override public boolean confirm(String msg) { return false; }   // 測試任務不該有敏感操作：問到就當否
        };
        try {
            Map<String, String> apps = Router.apps(c);
            for (Config cfg : configs) {
                LlmConfig.override = new LlmConfig.Slot[]{cfg.cheap, cfg.vision};
                for (Task t : taskSet) {
                    if (stopRequested) break;
                    progress = cfg.name + "：" + t.name;
                    resetPhone(c, apps, t);
                    results.add(runOne(c, cfg, t, apps));
                    sleep(3000);   // 免費方案每分鐘次數有限：任務之間留點空檔
                }
                if (stopRequested) break;
            }
        } finally {
            LlmConfig.override = null;
            resetPhone(c, null, null);
            running = false;
            String text = summarize(results);
            save(c, text);
            unseen = text;
            progress = "";
        }
    }

    private static Result runOne(Context c, Config cfg, Task t, Map<String, String> apps) {
        String pkg = Router.findApp(apps, t.app);
        Usage.beginTally();
        long t0 = System.currentTimeMillis();
        String reply = null, err = null;
        try {
            reply = AgentLoop.run(c, t.task, new AgentLoop.Ui() {
                @Override public void hide() { }
                @Override public void status(String text) { }
                @Override public boolean confirm(String msg) { return false; }
            }, pkg);
        } catch (Exception e) {
            err = String.valueOf(e.getMessage());
        }
        long ms = System.currentTimeMillis() - t0;
        double cost = Usage.tallyCost();
        Usage.endTally();
        JSONArray det = AgentLoop.takeDetails();
        int steps = det == null ? 0 : det.length();
        String why = err != null ? err : check(c, t, reply);
        return new Result(cfg.name, t.name, why == null, steps, ms, cost, why == null ? "" : why);
    }

    /** 成功回 null；失敗回原因。 */
    private static String check(Context c, Task t, String reply) {
        if (reply == null) return L.t("沒有回覆");
        for (String bad : new String[]{"已到最大步數", "已停止", "截圖失敗", "出錯", "失敗", "HTTP "}) if (reply.contains(bad) && !reply.contains("已完成")) return reply.length() > 80 ? reply.substring(0, 80) + "…" : reply;
        if (t.mustReply != null && !containsAny(reply, t.mustReply)) return L.t("回答裡沒有「") + t.mustReply[0] + "」：" + (reply.length() > 60 ? reply.substring(0, 60) + "…" : reply);
        if (t.mustPkg != null) {
            String fg = ElementList.foregroundPackage();
            if (fg == null || !fg.toLowerCase(Locale.ROOT).contains(t.mustPkg)) return L.t("最後畫面不是預期的 App（") + fg + "）";
        }
        if (t.mustSee != null) {
            int[] wh = AgentLoop.screenSize();
            List<JSONObject> els = ElementList.collect(wh[0], wh[1]);
            boolean seen = false;
            for (JSONObject o : els == null ? new ArrayList<JSONObject>() : els) if (containsAny(o.optString("t"), t.mustSee)) { seen = true; break; }
            if (!seen) return L.t("最後畫面上看不到「") + t.mustSee[0] + "」";
        }
        return null;
    }

    private static boolean containsAny(String text, String[] needles) {
        for (String n : needles) if (text.contains(n)) return true;
        return false;
    }

    /** 回主畫面並強制關閉要測的 App，讓每一組都從一樣的起點開始。 */
    private static void resetPhone(Context c, Map<String, String> apps, Task t) {
        try {
            if (Root.available()) {
                Maintenance.suRun("input keyevent 3");
                if (t != null && apps != null) {
                    String pkg = Router.findApp(apps, t.app);
                    if (pkg != null && pkg.matches("[A-Za-z0-9._]+")) Maintenance.suRun("am force-stop " + pkg);
                    for (String extra : t.extra) {
                        String p2 = Router.findApp(apps, extra);
                        if (p2 != null && p2.matches("[A-Za-z0-9._]+")) Maintenance.suRun("am force-stop " + p2);
                    }
                }
            } else {
                Phone.key("home");
            }
        } catch (Exception ignored) { }
        sleep(1500);
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }

    static String summarize(List<Result> rs) {
        StringBuilder sb = new StringBuilder();
        List<String> order = new ArrayList<>();
        for (Result r : rs) if (!order.contains(r.config)) order.add(r.config);
        for (String cfg : order) {
            int n = 0, ok = 0, steps = 0;
            long ms = 0;
            double cost = 0;
            for (Result r : rs) {
                if (!r.config.equals(cfg)) continue;
                n++;
                if (r.ok) ok++;
                steps += r.steps;
                ms += r.ms;
                cost += r.cost;
            }
            sb.append("■ ").append(cfg).append('\n')
                    .append(String.format(Locale.US, L.t("　成功 %d／%d，平均 %.1f 步、%d 秒，預估花費 $%.4f"), ok, n, steps / (double) Math.max(1, n), ms / Math.max(1, n) / 1000, cost)).append('\n');
            for (Result r : rs) {
                if (!r.config.equals(cfg)) continue;
                sb.append("　").append(r.ok ? "✓ " : "✗ ").append(r.task).append("　").append(r.steps).append(L.t(" 步 ")).append(r.ms / 1000).append(L.t(" 秒 $"))
                        .append(String.format(Locale.US, "%.4f", r.cost)).append(r.ok ? "" : "　" + r.note).append('\n');
            }
            sb.append('\n');
        }
        return sb.length() == 0 ? L.t("沒有跑任何任務。") : sb.toString().trim();
    }

    private static File file(Context c) { return new File(c.getFilesDir(), "abtest.txt"); }

    private static void save(Context c, String text) {
        try { Files.write(file(c).toPath(), text.getBytes(StandardCharsets.UTF_8)); } catch (Exception ignored) { }
    }

    static String last(Context c) {
        try { return new String(Files.readAllBytes(file(c).toPath()), StandardCharsets.UTF_8); } catch (Exception e) { return null; }
    }
}
