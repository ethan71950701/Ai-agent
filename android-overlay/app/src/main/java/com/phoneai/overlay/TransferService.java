package com.phoneai.overlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;

import java.io.File;

/**
 * 裝置之間傳檔案的前景服務：「設定 → 傳檔案」的總開關開著時，才在區網上提供伺服器與裝置探索，
 * 並在通知列顯示。每次有檔案被取走或收到，記錄並發通知。設計見 docs/FILE_TRANSFER.md。
 */
public class TransferService extends Service {
    private static final String CHANNEL = "transfer";
    private static final int NOTIF_ID = 7301;
    private static int eventId;

    /** 配對視窗到什麼時候（毫秒時間戳）；沒開就是 0。 */
    static volatile long pairUntil;

    /** 有裝置想配對時的待處理資料；使用者核對確認碼後按允許或拒絕。 */
    static final class Pending {
        String peerId, name, sas;
        byte[] key;
        volatile String state = "waiting";
    }

    static volatile Pending pending;
    static volatile boolean running;

    private android.net.wifi.WifiManager.MulticastLock mlock;
    private TransferServer server;
    private TransferDiscovery.Responder responder;

    static void start(Context c) {
        try {
            c.startForegroundService(new Intent(c, TransferService.class));
        } catch (Exception ignored) { }
    }

    static void stop(Context c) {
        c.stopService(new Intent(c, TransferService.class));
    }

    /** 開一段時間的配對視窗（其他裝置才能對我發起配對）。 */
    static void openPairing(int seconds) {
        pending = null;
        pairUntil = System.currentTimeMillis() + seconds * 1000L;
    }

    /** 配對頁開著時持續保持配對視窗（不清掉等待核對的請求）。 */
    static void keepPairing() {
        pairUntil = System.currentTimeMillis() + 15000;
    }

    static void closePairing() {
        pairUntil = 0;
    }

    /** 使用者核對確認碼相符：存下這個裝置與長期金鑰。 */
    static void acceptPending(Context c) {
        Pending p = pending;
        if (p == null || !"waiting".equals(p.state)) return;
        TransferStore.Peer peer = new TransferStore.Peer();
        peer.id = p.peerId;
        peer.name = p.name;
        peer.key = p.key;
        TransferStore.addPeer(c, peer);
        TransferStore.log(c, L.t("已配對") + " " + p.name);
        p.state = "accepted";
        closePairing();
    }

    static void rejectPending() {
        Pending p = pending;
        if (p != null) p.state = "rejected";
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, L.t("傳檔案"), NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setContentTitle(L.t("傳檔案已開啟"))
                .setContentText(L.t("已配對的裝置可以在同一個 Wi-Fi 取檔案；到「設定 → 傳檔案」可以關閉"))
                .setOngoing(true).build();
        startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        if (!TransferStore.enabled(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startServers();
        return START_STICKY;
    }

    private void startServers() {
        if (server != null) return;
        final Context c = getApplicationContext();
        server = new TransferServer(new TransferServer.Host() {
            @Override public String myId() { return TransferStore.myId(c); }
            @Override public String myName() { return TransferStore.myName(c); }
            @Override public byte[] keyOf(String peerId) { return TransferStore.keyOf(c, peerId); }
            @Override public File root() { return TransferStore.root(); }
            @Override public File inbox() { return TransferStore.inbox(); }
            @Override public boolean pairingOpen() { return System.currentTimeMillis() < pairUntil; }

            @Override
            public void pairRequest(String peerId, String peerName, byte[] key, String sas) {
                Pending p = new Pending();
                p.peerId = peerId;
                p.name = peerName;
                p.key = key;
                p.sas = sas;
                pending = p;
            }

            @Override
            public String pairState(String peerId) {
                Pending p = pending;
                return p != null && p.peerId.equals(peerId) ? p.state : "none";
            }

            @Override
            public void event(String peerId, String kind, String what, long size) {
                String who = TransferStore.nameOf(c, peerId);
                String sz = size > 0 ? "（" + TransferStore.size(size) + "）" : "";
                switch (kind) {
                    case "get":
                        note(c, who + L.t(" 取走了：") + what + sz);
                        break;
                    case "put":
                        note(c, who + L.t(" 傳來了：") + what + sz);
                        break;
                    case "list":
                        TransferStore.log(c, who + L.t(" 查看了資料夾：") + (what.isEmpty() ? "/" : what));
                        break;
                    default:
                        TransferStore.log(c, who + L.t(" 搜尋了檔案：") + what);
                }
            }
        });
        try {
            server.start(TransferServer.PORT);
            responder = new TransferDiscovery.Responder(new TransferDiscovery.Self() {
                @Override public String id() { return TransferStore.myId(c); }
                @Override public String name() { return TransferStore.myName(c); }
                @Override public int tcpPort() { return TransferServer.PORT; }
            });
            responder.start(TransferDiscovery.PORT);
            try {   // 沒有這個鎖，很多手機會把收到的廣播封包丟掉
                android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager) c.getSystemService(Context.WIFI_SERVICE);
                mlock = wm.createMulticastLock("phoneai-transfer");
                mlock.setReferenceCounted(false);
                mlock.acquire();
            } catch (Exception ignored) { }
            running = true;
        } catch (Exception e) {
            TransferStore.log(c, L.t("傳檔案伺服器啟動失敗：") + e.getMessage());
            stopSelf();
        }
    }

    /** 記錄並發通知：每次有檔案被取走或收到，使用者都看得到。 */
    private static void note(Context c, String text) {
        TransferStore.log(c, text);
        Notify.notifyText(c, L.t("傳檔案"), text, 7302 + (eventId++ % 20));
    }

    @Override
    public void onDestroy() {
        running = false;
        closePairing();
        if (server != null) server.stop();
        if (responder != null) responder.stop();
        try { if (mlock != null && mlock.isHeld()) mlock.release(); } catch (Exception ignored) { }
        mlock = null;
        server = null;
        responder = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
