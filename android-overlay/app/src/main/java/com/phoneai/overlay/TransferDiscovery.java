package com.phoneai.overlay;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 區域網路裡找裝置（純 Java）：UDP 廣播「PHONEAI?|目標 id」，對上的裝置回「PHONEAI!|id|名稱|TCP 埠」。
 * 只透露裝置 id、名稱與埠，真正的傳輸還是要簽章與加密；找不到時使用者可以手動輸入 IP。
 */
final class TransferDiscovery {
    static final int PORT = 47322;

    interface Self {
        String id();
        String name();
        int tcpPort();
    }

    static final class Found {
        String id, name, ip;
        int port;
    }

    static final class Responder {
        private final Self self;
        private DatagramSocket sock;
        private volatile boolean running;

        Responder(Self self) { this.self = self; }

        synchronized void start(int udpPort) throws java.io.IOException {
            if (running) return;
            sock = new DatagramSocket(null);
            sock.setReuseAddress(true);
            sock.bind(new InetSocketAddress(udpPort));
            running = true;
            Thread t = new Thread(this::loop, "transfer-discovery");
            t.setDaemon(true);
            t.start();
        }

        synchronized void stop() {
            running = false;
            if (sock != null) sock.close();
            sock = null;
        }

        private void loop() {
            DatagramSocket s = sock;
            byte[] buf = new byte[512];
            while (running && s != null) {
                try {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    s.receive(p);
                    String msg = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8);
                    if (!msg.startsWith("PHONEAI?|")) continue;
                    String target = msg.substring(9).trim();
                    if (!target.equals("*") && !target.equals(self.id())) continue;
                    byte[] r = ("PHONEAI!|" + self.id() + "|" + self.name().replace('|', ' ') + "|" + self.tcpPort()).getBytes(StandardCharsets.UTF_8);
                    s.send(new DatagramPacket(r, r.length, p.getSocketAddress()));
                } catch (Exception e) {
                    if (!running) return;
                }
            }
        }
    }

    /** 廣播找裝置；targetId 是 "*" 就收集所有回應，否則找到那一台就提早結束。extra 是額外要送的位址（測試或手動輸入的 IP）。 */
    static Map<String, Found> scan(String targetId, int timeoutMs, int udpPort, List<InetAddress> extra) {
        Map<String, Found> out = new LinkedHashMap<>();
        try (DatagramSocket s = new DatagramSocket()) {
            s.setBroadcast(true);
            byte[] q = ("PHONEAI?|" + targetId).getBytes(StandardCharsets.UTF_8);
            send(s, q, "255.255.255.255", udpPort);
            try {
                Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
                while (nis != null && nis.hasMoreElements()) {
                    NetworkInterface ni = nis.nextElement();
                    if (!ni.isUp() || ni.isLoopback()) continue;
                    for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                        if (ia.getBroadcast() != null) send(s, q, ia.getBroadcast().getHostAddress(), udpPort);
                    }
                }
            } catch (Exception ignored) { }
            for (InetAddress a : extra == null ? Collections.<InetAddress>emptyList() : extra) send(s, q, a.getHostAddress(), udpPort);
            long end = System.currentTimeMillis() + timeoutMs;
            byte[] buf = new byte[512];
            while (System.currentTimeMillis() < end) {
                s.setSoTimeout((int) Math.max(50, Math.min(400, end - System.currentTimeMillis())));
                try {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    s.receive(p);
                    String[] f = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8).split("\\|", 4);
                    if (f.length < 4 || !f[0].equals("PHONEAI!")) continue;
                    Found fd = new Found();
                    fd.id = f[1];
                    fd.name = f[2];
                    fd.port = Integer.parseInt(f[3].trim());
                    fd.ip = p.getAddress().getHostAddress();
                    out.put(fd.id, fd);
                    if (!targetId.equals("*")) break;
                } catch (SocketTimeoutException ignored) {
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (Exception ignored) {
            // 沒有網路或被擋住：回傳目前找到的（可能是空的）
        }
        return out;
    }

    private static void send(DatagramSocket s, byte[] data, String host, int port) {
        try {
            s.send(new DatagramPacket(data, data.length, InetAddress.getByName(host), port));
        } catch (Exception ignored) { }
    }
}
