package com.phoneai.overlay;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.os.StatFs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Calendar;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 定時任務（只支援純查詢）＋「能直接回答的短問題」。照搬 scripts/schedule.py 與 intents.py 的 quick_answer。
 * 排程用系統的 JobScheduler（每 15 分鐘，最小間隔），不再需要 Termux:API。清單存在 App 的 files/schedules.json。
 */
final class Schedule {
    private static final int JOB_ID = 7198;
    private static final long MAX_LATE_MS = 3L * 3600 * 1000;
    private static final String CHANNEL = "scheduled";

    private static final Pattern PAT = Pattern.compile(
            "^每天\\s*(早上|早晨|上午|中午|下午|傍晚|晚上|半夜|凌晨)?\\s*([0-9]{1,2}|[零〇一二兩两三四五六七八九十]{1,3})\\s*(?:點|点|時|时|:|：)\\s*(半|[0-9]{1,2}|[零〇一二兩两三四五六七八九十]{1,3})?\\s*分?\\s*[，,、。\\s]*(?:幫我|帮我|請|请|要|提醒我)?\\s*(.+)$");

    // 短問題的句型（照搬 intents.py）
    private static final Pattern BATT_RE = Pattern.compile("(電量|电量|電池|电池)|(?i:battery)");
    private static final Pattern BATT_Q = Pattern.compile("(多少|幾|几|剩|還有|还有|看|查|現在|现在|怎麼樣|怎么样|如何|%|百分)");
    private static final Pattern TIME_RE = Pattern.compile("(現在|现在|目前)?(幾點|几点|時間|时间)(了|是)?|(現在|现在)(是)?(星期|禮拜|礼拜)?幾|今天(星期|禮拜|礼拜)幾|今天几号|今天幾號|(?i:whattime|whatstime|timeisit|currenttime|thetime|whatday|whatdate|todaysdate|whatisthedate|dateistoday)");
    private static final Pattern YEAR_RE = Pattern.compile("(今年|現在|现在|目前)(是)?(西元|公元)?(幾年|几年|哪一年|哪年)|(西元|公元)(幾年|几年)|(今年|現在|现在)(幾|几)年|(?i:whatyear|currentyear|whatyearisit)");
    private static final Pattern ANDROID_RE = Pattern.compile("(安卓|android|Android)(系統)?(版本|幾|几|多少)|(系統|系统)版本|(?i:androidversion|whatandroid|whichandroid)");
    private static final Pattern STORE_RE = Pattern.compile("(儲存|存儲|存储|容量|硬碟|硬盘|空間|空间)|(?i:storage|diskspace|freespace|howmuchspace)");
    private static final Pattern MEM_RE = Pattern.compile("(記憶體|内存|內存|ram)|(?i:phonememory|howmuchmemory|freememory|availablememory|memoryusage)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MODEL_RE = Pattern.compile("(型號|型号|機型|机型|什麼手機|什么手机|哪一支手機|哪支手机)|(?i:phonemodel|devicemodel|whatphone|whichphone|whatdevice)");
    private static final Pattern BRIGHT_RE = Pattern.compile("亮度|(?i:brightness)");
    private static final Pattern WIFI_RE = Pattern.compile("(wifi|wi-fi|無線網路|无线网络)", Pattern.CASE_INSENSITIVE);
    private static final Pattern VOL_RE = Pattern.compile("(音量|聲音大小|声音大小)|(?i:volume)");
    private static final Pattern Q_RE = Pattern.compile("(多少|幾|几|剩|還有|还有|夠|够|用了|用到|現在|现在|目前|是什麼|是什么|哪|連|连|狀態|状态|名稱|名称)");

    private Schedule() { }

    // ---------- 排程清單 ----------

    static File store(Context c) { return new File(c.getFilesDir(), "schedules.json"); }

    static synchronized JSONArray load(Context c) {
        try {
            return new JSONArray(new String(Files.readAllBytes(store(c).toPath()), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    static synchronized void save(Context c, JSONArray items) throws Exception {
        Files.write(store(c).toPath(), items.toString(1).getBytes(StandardCharsets.UTF_8));
    }

    private static final Pattern PAT_EN = Pattern.compile(
            "^(?:every\\s*day|daily|each\\s*day)\\s*(?:at)?\\s*([0-9]{1,2})(?::([0-9]{2}))?\\s*(a\\.?m\\.?|p\\.?m\\.?)?\\s*[,.]?\\s*(.+)$", Pattern.CASE_INSENSITIVE);

    /** 「每天晚上 10 點半查儲存空間」→ {h, m, task}；不是這個句型回 null。 */
    static int[] parseTime(String text, String[] taskOut) {
        String raw = (text == null ? "" : text).trim();
        Matcher en = PAT_EN.matcher(raw);
        if (en.matches()) {   // "every day at 8:30 pm check battery"
            int eh = Integer.parseInt(en.group(1)), emi = en.group(2) == null ? 0 : Integer.parseInt(en.group(2));
            String ap = en.group(3) == null ? "" : en.group(3).toLowerCase(java.util.Locale.ROOT).replace(".", "");
            if (ap.equals("pm") && eh < 12) eh += 12;
            if (ap.equals("am") && eh == 12) eh = 0;
            String etask = en.group(4).replaceAll("^[\\s,.]+|[\\s,.]+$", "");
            if (eh > 23 || emi > 59 || etask.isEmpty()) return null;
            taskOut[0] = etask;
            return new int[]{eh, emi};
        }
        Matcher m = PAT.matcher(raw);
        if (!m.matches()) return null;
        String period = m.group(1), hs = m.group(2), ms = m.group(3);
        Integer h = cnInt(hs);
        if (h == null) return null;
        int mi;
        if ("半".equals(ms)) mi = 30;
        else if (ms != null) {
            Integer v = cnInt(ms);
            if (v == null) return null;
            mi = v;
        } else mi = 0;
        if (period != null) {
            if ((period.equals("下午") || period.equals("傍晚") || period.equals("晚上")) && h < 12) h += 12;
            if (period.equals("中午") && h < 11) h += 12;
            if ((period.equals("半夜") || period.equals("凌晨")) && h == 12) h = 0;
        }
        if (h < 0 || h > 23 || mi < 0 || mi > 59) return null;
        String task = m.group(4).replaceAll("^[\\s，,。]+|[\\s，,。]+$", "");
        if (task.isEmpty()) return null;
        taskOut[0] = task;
        return new int[]{h, mi};
    }

    /** 阿拉伯數字或中文數字（最多到 99）。 */
    static Integer cnInt(String s) {
        if (s == null || s.isEmpty()) return null;
        if (s.matches("[0-9]+")) return Integer.parseInt(s);
        String digits = "零〇一二兩两三四五六七八九";
        if (s.equals("十")) return 10;
        if (s.contains("十")) {
            String[] p = s.split("十", -1);
            int tens = p[0].isEmpty() ? 1 : digits.indexOf(p[0].charAt(0)) % 10;
            if (tens < 0) return null;
            int ones = p.length < 2 || p[1].isEmpty() ? 0 : digits.indexOf(p[1].charAt(0)) % 10;
            if (ones < 0) return null;
            return tens * 10 + ones;
        }
        if (s.length() == 1 && digits.indexOf(s.charAt(0)) >= 0) return digits.indexOf(s.charAt(0)) % 10;
        return null;
    }

    private static String fmt(JSONObject it) {
        return it.optInt("id") + L.t(". 每天 ") + String.format(java.util.Locale.US, "%02d:%02d", it.optInt("h"), it.optInt("m")) + " " + it.optString("task");
    }

    /**
     * 面板送來的句子：建立、列出、取消定時。不是定時相關回 null；否則回要顯示的文字。
     * confirm(msg) 在背景執行緒呼叫，會跳確認框等使用者按。
     */
    static String handle(Context c, String text, Memory.Confirm confirm) throws Exception {
        String t = (text == null ? "" : text).replaceAll("\\s+", "");
        String tl = t.toLowerCase(java.util.Locale.ROOT);
        if (t.matches("^(我的定時|有哪些定時|定時清單|定時列表|看定時).*") || tl.matches("^(my|show|list|whatare|whataremy)?(scheduledtasks|schedules|schedulelist).*")) {
            JSONArray items = load(c);
            if (items.length() == 0) return L.t("目前沒有定時任務。");
            StringBuilder sb = new StringBuilder(L.t("目前定時："));
            for (int i = 0; i < items.length(); i++) sb.append('\n').append(fmt(items.getJSONObject(i)));
            return sb.toString();
        }
        if (t.matches("^(取消|刪除|删除)(全部|所有)定時.*") || tl.matches("^(cancel|delete|remove)(all|every)(my)?(scheduledtasks|schedules).*")) {
            save(c, new JSONArray());
            return L.t("已取消全部定時任務。");
        }
        Matcher m = Pattern.compile("^(取消|刪除|删除)定時(?:第)?([0-9一二三四五六七八九十]+)").matcher(t);
        boolean found = m.find();
        String numTxt = found ? m.group(2) : null;
        if (!found) {
            Matcher me = Pattern.compile("^(cancel|delete|remove)(scheduledtask|schedule)(?:number|no\\.?|#)?([0-9]+)").matcher(tl);
            if (me.find()) { found = true; numTxt = me.group(3); }
        }
        if (found) {
            Integer n = cnInt(numTxt);
            JSONArray items = load(c), keep = new JSONArray();
            for (int i = 0; i < items.length(); i++) if (items.getJSONObject(i).optInt("id") != (n == null ? -1 : n)) keep.put(items.getJSONObject(i));
            if (keep.length() == items.length()) return L.t("找不到定時 ") + numTxt + L.t("。");
            save(c, keep);
            return L.t("已取消定時 ") + numTxt + L.t("。");
        }
        String[] task = new String[1];
        int[] hm = parseTime(text, task);
        if (hm == null) return null;
        if (!isQuickQuestion(task[0])) return L.t("定時任務目前只支援純查詢（電量、儲存空間、記憶體、現在幾點、Wi-Fi、亮度…）。沒人看著的時候我不會自己操作螢幕。");
        if (!confirm.ask(String.format(java.util.Locale.US, L.t("每天 %02d:%02d 自動「%s」並用通知告訴你（時間誤差最多約 15 分鐘）。要建立嗎？"), hm[0], hm[1], task[0])))
            return L.t("已取消，沒有建立定時。");
        JSONArray items = load(c);
        int nid = 1;
        for (int i = 0; i < items.length(); i++) nid = Math.max(nid, items.getJSONObject(i).optInt("id") + 1);
        items.put(new JSONObject().put("id", nid).put("h", hm[0]).put("m", hm[1]).put("task", task[0]).put("last", ""));
        save(c, items);
        boolean ok = ensureJob(c);
        return L.t("已建立定時 ") + nid + L.t("：每天 ") + String.format(java.util.Locale.US, "%02d:%02d", hm[0], hm[1]) + " " + task[0] + L.t("。")
                + (ok ? "" : L.t("（排程註冊失敗，請再試一次）"));
    }

    /** 註冊每 15 分鐘一次的排程（重複註冊會覆蓋，不會變兩個）。 */
    static boolean ensureJob(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return false;
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(c, ScheduleJob.class))
                .setPeriodic(15 * 60 * 1000L).setPersisted(true).build();
        return js.schedule(job) == JobScheduler.RESULT_SUCCESS;
    }

    /** 排程每 15 分鐘叫一次：跑該跑的查詢，用通知回報。 */
    static void runDue(Context c) throws Exception {
        JSONArray items = load(c);
        Calendar now = Calendar.getInstance();
        String today = String.format(java.util.Locale.US, "%04d-%02d-%02d", now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH));
        for (int i = 0; i < items.length(); i++) {
            JSONObject it = items.getJSONObject(i);
            Calendar at = (Calendar) now.clone();
            at.set(Calendar.HOUR_OF_DAY, it.optInt("h"));
            at.set(Calendar.MINUTE, it.optInt("m"));
            at.set(Calendar.SECOND, 0);
            at.set(Calendar.MILLISECOND, 0);
            long late = now.getTimeInMillis() - at.getTimeInMillis();
            if (today.equals(it.optString("last")) || late < 0 || late > MAX_LATE_MS) continue;
            String ans = quickAnswer(c, it.optString("task"));
            it.put("last", today);
            if (ans != null) notify(c, L.t("定時：") + it.optString("task"), ans, 7197 + it.optInt("id"));
        }
        save(c, items);
    }

    private static void notify(Context c, String title, String text, int id) {
        Notify.notifyText(c, title, text, id);
    }

    // ---------- 短問題（照搬 intents.py） ----------

    static boolean isQuickQuestion(String text) {
        String t = (text == null ? "" : text).replaceAll("[\\s，。,.？?！!]", "");
        if (t.isEmpty() || t.length() > (t.matches("\\p{ASCII}+") ? 40 : 18) || t.toLowerCase().startsWith("ai")) return false;
        if (t.matches("^((開|關|打開|關閉|开|关|打开|关闭|調|调|設|设)|(?i:turn|enable|disable|switch|open|close|set)).*")) return false;
        // 只要句子裡有主題詞就算查詢（「告訴我電量」「看一下儲存空間」都可以）；動作類的已在上面排除
        if (BATT_RE.matcher(t).find() || ANDROID_RE.matcher(t).find() || YEAR_RE.matcher(t).find() || TIME_RE.matcher(t).find()) return true;
        return STORE_RE.matcher(t).find() || MEM_RE.matcher(t).find() || MODEL_RE.matcher(t).find()
                || BRIGHT_RE.matcher(t).find() || WIFI_RE.matcher(t).find() || VOL_RE.matcher(t).find();
    }

    /** 能直接回答的短問題，回傳答案；不是這類問題回 null（不呼叫 AI）。 */
    static String quickAnswer(Context c, String text) {
        String t = (text == null ? "" : text).replaceAll("[\\s，。,.？?！!]", "");
        if (t.isEmpty() || t.length() > (t.matches("\\p{ASCII}+") ? 40 : 18) || t.toLowerCase().startsWith("ai")) return null;
        try {
            if (BATT_RE.matcher(t).find()) return battery(c);
            if (!t.matches("^((開|關|打開|關閉|开|关|打开|关闭|調|调|設|设)|(?i:turn|enable|disable|switch|open|close|set)).*")) {
                String r = system(c, t);
                if (r != null) return r;
            }
            if (ANDROID_RE.matcher(t).find()) return L.t("這支手機的 Android 版本是 ") + Build.VERSION.RELEASE + L.t("。");
            if (YEAR_RE.matcher(t).find()) {
                int y = Calendar.getInstance().get(Calendar.YEAR);
                return L.t("今年是西元 ") + y + L.t(" 年（民國 ") + (y - 1911) + L.t(" 年）。");
            }
            if (TIME_RE.matcher(t).find()) {
                Calendar n = Calendar.getInstance();
                if (L.en()) return String.format(java.util.Locale.US, "It is %1$tA, %1$tB %1$td, %1$tH:%1$tM.", n);
                String[] wd = {"日", "一", "二", "三", "四", "五", "六"};
                return "現在是 " + (n.get(Calendar.MONTH) + 1) + " 月 " + n.get(Calendar.DAY_OF_MONTH) + " 日（星期"
                        + wd[n.get(Calendar.DAY_OF_WEEK) - 1] + L.t("）") + String.format(java.util.Locale.US, "%02d:%02d", n.get(Calendar.HOUR_OF_DAY), n.get(Calendar.MINUTE)) + L.t("。");
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private static String battery(Context c) {
        android.content.Intent bi = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (bi == null) return null;
        int level = bi.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1), scale = bi.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100);
        int status = bi.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1);
        boolean charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING || status == android.os.BatteryManager.BATTERY_STATUS_FULL;
        int pct = Math.round(100f * level / scale);
        int temp = bi.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, -1);
        return L.t("目前電量 ") + pct + "%" + (charging ? L.t("，充電中") : L.t("，沒有在充電")) + (temp > 0 ? L.t("，電池溫度約 ") + String.format(java.util.Locale.US, "%.1f", temp / 10f) + "°C" : "") + L.t("。");
    }

    private static String system(Context c, String t) {
        if (STORE_RE.matcher(t).find()) {
            StatFs s = new StatFs(Environment.getDataDirectory().getPath());
            double total = s.getTotalBytes() / 1e9, free = s.getAvailableBytes() / 1e9;
            return String.format(java.util.Locale.US, L.t("儲存空間：已用 %.1f GB／共 %.1f GB，剩 %.1f GB。"), total - free, total, free);
        }
        if (MEM_RE.matcher(t).find()) {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            double total = mi.totalMem / 1e9, avail = mi.availMem / 1e9;
            return String.format(java.util.Locale.US, L.t("記憶體：共 %.1f GB，目前可用 %.1f GB（已用約 %d%%）。"), total, avail, Math.round(100 * (total - avail) / total));
        }
        if (MODEL_RE.matcher(t).find()) {
            return L.t("這支手機是 ") + (Build.MANUFACTURER.isEmpty() ? "" : Build.MANUFACTURER + " ") + Build.MODEL + L.t("。");
        }
        if (BRIGHT_RE.matcher(t).find()) {
            try {
                int v = Settings.System.getInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
                return L.t("目前螢幕亮度約 ") + Math.round(100 * v / 255f) + L.t("%（系統值 ") + v + L.t("／255）。");
            } catch (Settings.SettingNotFoundException e) {
                return null;
            }
        }
        if (WIFI_RE.matcher(t).find()) {
            String out = Maintenance.suRun("cmd wifi status");
            if (out.matches("(?s).*wifi is disabled.*")) return L.t("Wi-Fi 目前是關閉的。");
            Matcher m = Pattern.compile("SSID:\\s*\"?([^\",\\n]+)\"?").matcher(out);
            if (m.find() && !m.group(1).trim().equals("<unknown ssid>") && !m.group(1).trim().equals("unknown"))
                return L.t("Wi-Fi 已開啟，連線中的網路是「") + m.group(1).trim() + L.t("」。");
            if (out.matches("(?s).*wifi is enabled.*")) return L.t("Wi-Fi 已開啟（讀不到目前連的網路名稱）。");
        }
        if (VOL_RE.matcher(t).find()) {
            Matcher m = Pattern.compile("volume is (\\d+) in range \\[(\\d+)\\.\\.(\\d+)\\]").matcher(Maintenance.suRun("cmd media_session volume --get --stream 3"));
            if (m.find()) {
                int cur = Integer.parseInt(m.group(1)), lo = Integer.parseInt(m.group(2)), hi = Integer.parseInt(m.group(3));
                return L.t("目前媒體音量 ") + cur + L.t("／") + hi + L.t("（約 ") + Math.round(100f * (cur - lo) / Math.max(1, hi - lo)) + L.t("%）。");
            }
        }
        return null;
    }
}
