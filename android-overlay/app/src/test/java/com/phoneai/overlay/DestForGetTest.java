package com.phoneai.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.io.File;
import java.nio.file.Files;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DestForGetTest {
    private File inbox;
    private final java.util.List<File> held = new java.util.ArrayList<>();

    @Before public void setUp() throws Exception { inbox = Files.createTempDirectory("dest").toFile(); }

    @After public void tearDown() { for (File f : held) FileTools.releaseDest(f); }

    private File reserve(String name, String reuse) {
        File f = FileTools.reserveDest(inbox, name, reuse);
        held.add(f);
        return f;
    }

    @Test
    public void twoDownloadsAtTheSameTimeNeverGetTheSameFile() {
        File a = reserve("photo.jpg", null), b = reserve("photo.jpg", null);   // 都還沒開始寫（檔案都還不存在）
        assertEquals("photo.jpg", a.getName());
        assertNotEquals(a, b);
        assertEquals("photo (2).jpg", b.getName());
        FileTools.releaseDest(a);
        assertEquals("放掉之後那個名字又可以用", "photo.jpg", reserve("photo.jpg", null).getName());
    }

    @Test
    public void someoneElsesPartialIsNotPickedUp() throws Exception {
        Files.write(TransferClient.partOf(new File(inbox, "photo.jpg")).toPath(), new byte[10]);   // 另一個資料夾的同名檔沒收完的半成品
        assertEquals("不是這個遠端路徑的半成品，不能接上", "photo (2).jpg", reserve("photo.jpg", null).getName());
    }

    @Test
    public void thePartialOfTheSameRemotePathIsResumed() throws Exception {
        Files.write(TransferClient.partOf(new File(inbox, "photo.jpg")).toPath(), new byte[10]);
        assertEquals("photo.jpg", reserve("photo.jpg", "photo.jpg").getName());   // 未完成清單說上次就是存成這個檔名
    }

    @Test
    public void aPartialBeingResumedIsNotHandedOutTwice() throws Exception {
        Files.write(TransferClient.partOf(new File(inbox, "photo.jpg")).toPath(), new byte[10]);
        File first = reserve("photo.jpg", "photo.jpg");
        File second = reserve("photo.jpg", "photo.jpg");
        assertEquals("photo.jpg", first.getName());
        assertNotEquals("同一份半成品不能同時給兩個取檔寫", first, second);
    }

    @Test
    public void resumeNameWithoutAPartialOrWithAPathFallsBackToAFreshName() throws Exception {
        assertEquals("半成品不在了", "a.txt", reserve("a.txt", "a.txt").getName());
        assertEquals("亂來的名字（含路徑）不採用", "b.txt", reserve("b.txt", "../x/b.txt").getName());
    }
}
