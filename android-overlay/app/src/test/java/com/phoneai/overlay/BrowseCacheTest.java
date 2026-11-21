package com.phoneai.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class BrowseCacheTest {
    private static List<String[]> rows(String... names) {
        List<String[]> l = new ArrayList<>();
        for (String n : names) l.add(new String[]{"F", "10", n});
        return l;
    }

    @Test
    public void readFoldersComeBackFromTheCache() {
        BrowseCache c = new BrowseCache();
        assertNull(c.get("DCIM"));
        c.put("DCIM", rows("a.jpg"), 1000);
        assertEquals("a.jpg", c.get("DCIM").rows.get(0)[2]);
        assertNotNull(c.get("DCIM"));
    }

    @Test
    public void oldEntriesAreShownButMarkedStale() {
        BrowseCache c = new BrowseCache();
        c.put("x", rows("a"), 1000);
        assertTrue(BrowseCache.fresh(c.get("x"), 1000 + BrowseCache.FRESH_MS - 1));
        assertFalse(BrowseCache.fresh(c.get("x"), 1000 + BrowseCache.FRESH_MS));
        assertFalse(BrowseCache.fresh(null, 1000));
    }

    @Test
    public void sameDetectsChanges() {
        assertTrue(BrowseCache.same(rows("a", "b"), rows("a", "b")));
        assertFalse(BrowseCache.same(rows("a", "b"), rows("a", "c")));
        assertFalse(BrowseCache.same(rows("a"), rows("a", "b")));
        assertFalse(BrowseCache.same(null, rows("a")));
    }

    @Test
    public void onlyTheMostRecentlyUsedFoldersAreKept() {
        BrowseCache c = new BrowseCache();
        for (int i = 0; i < BrowseCache.MAX + 5; i++) c.put("f" + i, rows("a"), 1000);
        assertNull("最舊的被擠掉", c.get("f0"));
        assertNotNull(c.get("f" + (BrowseCache.MAX + 4)));
    }
}
