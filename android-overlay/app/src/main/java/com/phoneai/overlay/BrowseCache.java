package com.phoneai.overlay;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 瀏覽對方資料夾時，讀過的內容先暫存（純 Java）：返回上一層、再進同一個資料夾不用重新讀；放太久的會在背景更新。 */
final class BrowseCache {
    static final long FRESH_MS = 30_000;
    static final int MAX = 60;

    static final class Page {
        final List<String[]> rows;
        final long at;
        Page(List<String[]> rows, long at) { this.rows = rows; this.at = at; }
    }

    private final Map<String, Page> map = new LinkedHashMap<String, Page>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Page> e) { return size() > MAX; }
    };

    synchronized void put(String path, List<String[]> rows, long now) { map.put(path, new Page(rows, now)); }

    synchronized Page get(String path) { return map.get(path); }

    synchronized void clear() { map.clear(); }

    /** 暫存的內容夠新嗎（不夠新：先顯示，再在背景更新）。 */
    static boolean fresh(Page e, long now) { return e != null && now - e.at < FRESH_MS; }

    /** 兩份清單內容一樣嗎（背景更新後沒變就不用重畫）。 */
    static boolean same(List<String[]> a, List<String[]> b) {
        if (a == null || b == null || a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (!java.util.Arrays.equals(a.get(i), b.get(i))) return false;
        return true;
    }
}
