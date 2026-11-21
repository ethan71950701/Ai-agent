package com.phoneai.overlay;

import java.util.concurrent.atomic.AtomicBoolean;

/** 「同一時間只讓一個」的門（純 Java）：自動操作一次只能有一個在動，手機的畫面與觸控本來就只有一份。進去要 tryEnter、結束一定要 exit（放在 finally）。 */
final class SingleRun {
    private final AtomicBoolean busy = new AtomicBoolean();

    boolean tryEnter() { return busy.compareAndSet(false, true); }

    void exit() { busy.set(false); }

    boolean busy() { return busy.get(); }
}
