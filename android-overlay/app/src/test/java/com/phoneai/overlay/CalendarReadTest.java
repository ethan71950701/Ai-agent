package com.phoneai.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import org.junit.Test;

public class CalendarReadTest {
    private static final TimeZone TPE = TimeZone.getTimeZone("Asia/Taipei");

    @Test public void dayStartParsesAndDefaultsToToday() {
        long d = CalendarRead.dayStart("2026-11-26", 0, TPE);
        assertEquals(1795622400000L, d);   // 2026-11-26 00:00 台北 = 2026-11-25 16:00 UTC
        assertEquals(d, CalendarRead.dayStart("", d + 5 * 3600_000L, TPE));
        assertEquals(-1, CalendarRead.dayStart("11/26", 0, TPE));
        assertEquals(-1, CalendarRead.dayStart("2026-02-30", 0, TPE));
    }

    @Test public void formatsTimedAndAllDayEvents() {
        List<CalendarRead.Item> l = new ArrayList<>();
        CalendarRead.Item a = new CalendarRead.Item();
        a.title = "看牙醫"; a.location = "台北"; a.begin = 1795622400000L + 9 * 3600_000L; a.end = a.begin + 3600_000L; l.add(a);
        CalendarRead.Item b = new CalendarRead.Item();
        b.title = "生日"; b.allDay = true; b.begin = 1795651200000L; b.end = b.begin + 86_400_000L; l.add(b);   // 2026-11-26 00:00 UTC
        String s = CalendarRead.format(l, "", TPE, false);
        assertTrue(s, s.contains("2026-11-26") && s.contains("09:00–10:00 看牙醫｜地點：台北"));
        assertTrue(s, s.contains("全天 生日"));
        assertTrue(CalendarRead.format(new ArrayList<>(), "牙醫", TPE, false).contains("沒有符合「牙醫」"));
        assertTrue(CalendarRead.format(l, "", TPE, true).contains("只列前"));
    }
}
