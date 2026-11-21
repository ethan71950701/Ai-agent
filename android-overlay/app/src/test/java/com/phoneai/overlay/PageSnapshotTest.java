package com.phoneai.overlay;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PageSnapshotTest {
    @Test
    public void onlyBrowsersAreRecognised() {
        assertTrue(PageSnapshot.isBrowser("com.android.chrome"));
        assertTrue(PageSnapshot.isBrowser("com.sec.android.app.sbrowser"));
        assertFalse("銀行、通訊軟體不是瀏覽器", PageSnapshot.isBrowser("com.example.bank"));
        assertFalse(PageSnapshot.isBrowser("jp.naver.line.android"));
        assertFalse(PageSnapshot.isBrowser(null));
    }

    @Test
    public void urlBarIdsOfCommonBrowsers() {
        assertTrue(PageSnapshot.isUrlViewId("com.android.chrome:id/url_bar"));
        assertTrue(PageSnapshot.isUrlViewId("org.mozilla.firefox:id/mozac_browser_toolbar_url_view"));
        assertTrue(PageSnapshot.isUrlViewId("com.sec.android.app.sbrowser:id/location_bar_edit_text"));
        assertFalse(PageSnapshot.isUrlViewId("com.android.chrome:id/search_box_text"));
        assertFalse(PageSnapshot.isUrlViewId(null));
    }

    @Test
    public void fullUrlVersusShortenedSiteName() {
        assertTrue(PageSnapshot.looksLikeFullUrl("https://example.com/a/b?x=1"));
        assertFalse(PageSnapshot.looksLikeFullUrl("example.com"));
        assertFalse(PageSnapshot.looksLikeFullUrl("https://"));
        assertFalse(PageSnapshot.looksLikeFullUrl(null));
    }

    @Test
    public void formatMarksTheTextAsUntrustedAndSaysWhatIsMissing() {
        PageSnapshot.Raw r = new PageSnapshot.Raw();
        r.pkg = "com.android.chrome";
        r.title = "一篇文章";
        r.urlText = "example.com";
        r.texts.add("大標題");
        r.texts.add("  第一段   內容 ");
        r.texts.add("第一段 內容");   // 連續重複的不重複給
        r.texts.add("");
        String out = PageSnapshot.format(r);
        assertTrue(out, out.startsWith("【畫面上的網頁文字"));
        assertTrue(out.contains("標題：一篇文章") && out.contains("大標題") && out.contains("第一段 內容"));
        assertTrue("縮短的網址要說明不是完整網址：" + out, out.contains("不一定是完整網址"));
        assertTrue(out.contains("只有螢幕上這一段"));
        assertEquals1(out, "第一段 內容");
    }

    private static void assertEquals1(String out, String needle) {
        int first = out.indexOf(needle);
        assertTrue(first >= 0 && out.indexOf(needle, first + 1) < 0);
    }

    @Test
    public void fullUrlSuggestsReadUrlAndHugeTextIsCut() {
        PageSnapshot.Raw r = new PageSnapshot.Raw();
        r.pkg = "com.android.chrome";
        r.urlText = "https://example.com/post/1";
        for (int i = 0; i < 2000; i++) r.texts.add("這是第 " + i + " 行很長的文字內容用來測試上限");
        String out = PageSnapshot.format(r);
        assertTrue(out.contains("read_url"));
        assertTrue(out.contains("只給前面這些"));
        assertTrue(out.length() < PageSnapshot.MAX_CHARS + 1200);
    }

    @Test
    public void emptyPageSaysWhy() {
        PageSnapshot.Raw r = new PageSnapshot.Raw();
        r.pkg = "com.android.chrome";
        assertTrue(PageSnapshot.format(r).contains("讀不到文字"));
    }

    @Test
    public void currentPageIsAllowedAfterReadingAPageButNotAfterNotifications() {
        assertTrue(ChatEngine.allowedAfterUntrusted("current_page", false));
        assertFalse(ChatEngine.allowedAfterUntrusted("current_page", true));
    }
}
