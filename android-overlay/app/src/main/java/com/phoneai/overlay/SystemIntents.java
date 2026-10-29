package com.phoneai.overlay;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 用 Android 的「系統意圖」請別的 App 做事，而且把內容預先填好：新增行事曆事件、撥號、地圖導航。
 * 都只是打開對應的 App 並填好資料，要按儲存、撥出、開始導航仍由使用者自己按，所以不會直接做出不可逆的事。
 * 跟設鬧鐘一樣先用 root 的 am start（三星有些 App 擋一般 App 的意圖），失敗再用一般的 startActivity。
 */
final class SystemIntents {
    private SystemIntents() { }

    /** 單引號包起來給 shell 用（內容裡的單引號要跳脫）。 */
    static String q(String s) { return "'" + (s == null ? "" : s).replace("'", "'\\''") + "'"; }

    /** 解析「yyyy-MM-dd HH:mm」（也接受中間是 T）；解析不出來回 -1。 */
    static long parseTime(String s) {
        if (s == null) return -1;
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.TAIWAN).parse(s.trim().replace('T', ' '));
            return d == null ? -1 : d.getTime();
        } catch (Exception e) {
            return -1;
        }
    }

    /** 只留電話號碼用得到的字元。 */
    static String cleanNumber(String n) { return n == null ? "" : n.replaceAll("[^0-9+*#]", ""); }

    private static boolean start(Context c, String amArgs, Intent fallback) {
        String out = Maintenance.suRun("am start " + amArgs);
        if (!out.startsWith("ERR:") && !out.contains("Error")) return true;
        try {
            c.startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }

    /** 新增行事曆事件：打開行事曆的「新增」畫面，標題與時間填好，使用者按儲存才會建立。 */
    static String addEvent(Context c, String title, String start, String end, String location, String description) {
        long b = parseTime(start);
        if (b < 0) return "錯誤：start 要寫成 yyyy-MM-dd HH:mm（24 小時制），例如 2026-10-30 15:00";
        long e = end == null || end.isEmpty() ? b + 3600_000L : parseTime(end);
        if (e < b) e = b + 3600_000L;
        String t = title == null || title.trim().isEmpty() ? "新活動" : title.trim();
        StringBuilder am = new StringBuilder("-a android.intent.action.INSERT -d content://com.android.calendar/events")
                .append(" --es title ").append(q(t)).append(" --el beginTime ").append(b).append(" --el endTime ").append(e);
        Intent it = new Intent(Intent.ACTION_INSERT).setData(android.provider.CalendarContract.Events.CONTENT_URI)
                .putExtra("title", t).putExtra("beginTime", b).putExtra("endTime", e);
        if (location != null && !location.isEmpty()) { am.append(" --es eventLocation ").append(q(location)); it.putExtra("eventLocation", location); }
        if (description != null && !description.isEmpty()) { am.append(" --es description ").append(q(description)); it.putExtra("description", description); }
        if (!start(c, am.toString(), it)) return "錯誤：找不到可以新增行事曆事件的 App";
        return String.format(Locale.US, "已打開行事曆的新增畫面（%s），要在行事曆裡按儲存才會建立", new SimpleDateFormat("MM/dd HH:mm", Locale.TAIWAN).format(new Date(b)));
    }

    /** 撥號：打開撥號畫面並填好號碼，要自己按撥出。 */
    static String dial(Context c, String number) {
        String n = cleanNumber(number);
        if (n.isEmpty()) return "錯誤：沒有可用的電話號碼";
        if (!start(c, "-a android.intent.action.DIAL -d " + q("tel:" + Uri.encode(n)), new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(n)))))
            return "錯誤：找不到撥號的 App";
        return "已打開撥號畫面（" + n + "），要自己按撥出";
    }

    /** 在地圖上搜尋或導航到某個地方（geo: 意圖，顯示地點，不會自動開始導航）。 */
    static String showOnMap(Context c, String place) {
        String p = place == null ? "" : place.trim();
        if (p.isEmpty()) return "錯誤：沒有地點";
        String uri = "geo:0,0?q=" + Uri.encode(p);
        if (!start(c, "-a android.intent.action.VIEW -d " + q(uri), new Intent(Intent.ACTION_VIEW, Uri.parse(uri)))) return "錯誤：找不到地圖 App";
        return "已在地圖上打開「" + p + "」";
    }
}
