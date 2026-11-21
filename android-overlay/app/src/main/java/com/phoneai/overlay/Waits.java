package com.phoneai.overlay;

import java.util.function.BooleanSupplier;

/** 等一個條件成立的小工具（純 Java）：每隔 stepMs 檢查一次，最多等 timeoutMs。 */
final class Waits {
    private Waits() { }

    static boolean until(BooleanSupplier cond, long timeoutMs, long stepMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (true) {
            if (cond.getAsBoolean()) return true;
            if (System.currentTimeMillis() >= end) return false;
            try { Thread.sleep(stepMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return cond.getAsBoolean(); }
        }
    }
}
