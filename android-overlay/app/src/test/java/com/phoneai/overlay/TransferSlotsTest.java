package com.phoneai.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;

import org.junit.Test;

public class TransferSlotsTest {
    /** 在另一條執行緒上跑一段，等它做完（模擬另一筆傳輸）。 */
    private static void onOtherThread(Runnable r) throws Exception {
        Thread t = new Thread(r);
        t.start();
        t.join();
    }

    @Test
    public void eachTransferHasItsOwnFlags() throws Exception {
        TransferSlots s = new TransferSlots();
        TransferSlots.Slot a = s.enter(1000);
        final TransferSlots.Slot[] b = new TransferSlots.Slot[1];
        final CountDownLatch bRegistered = new CountDownLatch(1), release = new CountDownLatch(1);
        Thread other = new Thread(() -> {
            b[0] = s.enter(1100);
            bRegistered.countDown();
            try { release.await(); } catch (InterruptedException ignored) { }
        });
        other.start();
        bRegistered.await();
        assertTrue("最早開始的是通知上顯示的那一筆", s.isPrimary(a, 1200));
        s.stopPrimary(1200);
        assertTrue(a.stop);
        assertFalse("另一筆不受影響", b[0].stop);
        release.countDown();
        other.join();
    }

    @Test
    public void endingOneTransferKeepsTheOthersActiveAndTheirFlags() throws Exception {
        TransferSlots s = new TransferSlots();
        TransferSlots.Slot mine = s.enter(1000);
        final TransferSlots.Slot[] other = new TransferSlots.Slot[1];
        final CountDownLatch up = new CountDownLatch(1), finish = new CountDownLatch(1);
        Thread t = new Thread(() -> {
            other[0] = s.enter(1100);
            up.countDown();
            try { finish.await(); } catch (InterruptedException ignored) { }
            s.leave(1500);   // 對方這一筆結束
        });
        t.start();
        up.await();
        s.pausePrimary(1200);   // 使用者對通知上顯示的（mine）按暫停
        finish.countDown();
        t.join();
        assertTrue("還有一筆在傳，不能說沒有進行中", s.active(1600));
        assertTrue("別筆結束不能把我的暫停清掉", mine.pause);
        assertFalse("最後一筆收掉才算全部結束", s.leave(1700) == false);
        assertFalse(s.active(1700));
    }

    @Test
    public void beginDropsOnlyThisThreadsOldSlot() throws Exception {
        TransferSlots s = new TransferSlots();
        TransferSlots.Slot first = s.enter(1000);
        s.stopPrimary(1100);
        s.begin();   // 同一條執行緒開始新的傳輸：舊的停止旗標不能留著
        assertNull(s.current(1200));
        TransferSlots.Slot again = s.enter(1300);
        assertFalse(again.stop);
        assertFalse(again == first);
    }

    @Test
    public void staleSlotsAreTreatedAsFinished() throws Exception {
        TransferSlots s = new TransferSlots();
        final TransferSlots.Slot[] a = new TransferSlots.Slot[1];
        onOtherThread(() -> a[0] = s.enter(1000));   // 那一筆開了就沒再更新（卡死、忘了收尾）
        assertTrue(s.active(1000 + TransferSlots.STALE_MS));
        assertFalse("太久沒更新就當作結束，不然永遠顯示傳輸中", s.active(1001 + TransferSlots.STALE_MS));
        assertFalse(s.stopPrimary(1001 + TransferSlots.STALE_MS));
    }

    @Test
    public void stopWithNothingRunningDoesNothing() {
        TransferSlots s = new TransferSlots();
        assertFalse(s.stopPrimary(1000));
        assertFalse(s.pausePrimary(1000));
        assertEquals(true, s.leave(1000));
    }
}
