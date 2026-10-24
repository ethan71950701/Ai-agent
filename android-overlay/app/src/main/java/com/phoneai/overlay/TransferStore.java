package com.phoneai.overlay;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 裝置之間傳檔案的本機資料：這台裝置的 id 與名稱、已配對的裝置與長期金鑰（App 私有資料夾）、傳輸記錄。設計見 docs/FILE_TRANSFER.md。
 * 金鑰只存在 files/transfer_peers.json，不進 repo、不印出來。
 */
final class TransferStore {
    private TransferStore() { }

    static final class Peer {
        String id, name, ip;      // 區網位址（最後一次在區網找到的）
        int port;
        String tsIp;             // Tailscale 位址（100.x，外網用）
        int tsPort;
        byte[] key;
    }

    /** Tailscale 的位址範圍 100.64.0.0/10。 */
    static boolean isTailnet(String ip) {
        if (ip == null || !ip.startsWith("100.")) return false;
        String[] f = ip.split("\\.");
        if (f.length != 4) return false;
        try {
            int second = Integer.parseInt(f[1]);
            return second >= 64 && second <= 127;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static SharedPreferences cfg(Context c) { return c.getSharedPreferences("cfg", Context.MODE_PRIVATE); }

    static boolean enabled(Context c) { return cfg(c).getBoolean("transfer_on", false); }

    /** 總開關：開了才會啟動伺服器（並在通知列顯示）。 */
    static void setEnabled(Context c, boolean on) {
        cfg(c).edit().putBoolean("transfer_on", on).apply();
        if (on) { TransferService.lastError = ""; TransferService.start(c); }
        else TransferService.stop(c);
    }

    static synchronized String myId(Context c) {
        String id = cfg(c).getString("tr_id", null);
        if (id == null) {
            id = TransferCrypto.hex(TransferCrypto.random(8));
            cfg(c).edit().putString("tr_id", id).apply();
        }
        return id;
    }

    static String myName(Context c) { return cfg(c).getString("tr_name", Build.MODEL); }

    static void setMyName(Context c, String name) {
        String n = name == null ? "" : name.trim().replace('|', ' ').replace('\n', ' ');
        cfg(c).edit().putString("tr_name", n.isEmpty() ? Build.MODEL : n).apply();
    }

    /** 開放與收件的位置：儲存空間根目錄（/sdcard）；收到的檔案放在 下載/PhoneAI。不包含 App 私有資料夾。 */
    static File root() { return Environment.getExternalStorageDirectory(); }

    static File inbox() { return new File(root(), "Download/PhoneAI"); }

    /** 需要「所有檔案存取」權限才能讀寫儲存空間裡別的 App 與使用者的檔案。 */
    static boolean hasAllFiles() { return Environment.isExternalStorageManager(); }

    // ---------- 已配對的裝置 ----------

    private static File peersFile(Context c) { return new File(c.getFilesDir(), "transfer_peers.json"); }

    static synchronized List<Peer> peers(Context c) {
        List<Peer> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(new String(Files.readAllBytes(peersFile(c).toPath()), StandardCharsets.UTF_8));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Peer p = new Peer();
                p.id = o.getString("id");
                p.name = o.optString("name", p.id);
                p.ip = o.optString("ip", "");
                if (p.ip.isEmpty()) p.ip = null;
                p.port = o.optInt("port", 0);
                p.tsIp = o.optString("ts_ip", "");
                if (p.tsIp.isEmpty()) p.tsIp = null;
                p.tsPort = o.optInt("ts_port", 0);
                p.key = TransferCrypto.unhex(o.getString("key"));
                out.add(p);
            }
        } catch (Exception ignored) { }
        return out;
    }

    private static synchronized void save(Context c, List<Peer> list) {
        try {
            JSONArray a = new JSONArray();
            for (Peer p : list) {
                a.put(new JSONObject().put("id", p.id).put("name", p.name).put("ip", p.ip == null ? "" : p.ip)
                        .put("port", p.port).put("ts_ip", p.tsIp == null ? "" : p.tsIp).put("ts_port", p.tsPort)
                        .put("key", TransferCrypto.hex(p.key)));
            }
            File f = peersFile(c), tmp = new File(f.getPath() + ".tmp");
            Files.write(tmp.toPath(), a.toString().getBytes(StandardCharsets.UTF_8));
            tmp.setReadable(false, false);
            tmp.setReadable(true, true);
            tmp.setWritable(false, false);
            tmp.setWritable(true, true);
            if (!tmp.renameTo(f)) throw new IllegalStateException("rename failed");
        } catch (Exception ignored) { }
    }

    static synchronized void addPeer(Context c, Peer p) {
        List<Peer> l = peers(c);
        for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).id.equals(p.id)) l.remove(i);
        l.add(p);
        save(c, l);
    }

    static synchronized void removePeer(Context c, String id) {
        List<Peer> l = peers(c);
        for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).id.equals(id)) l.remove(i);
        save(c, l);
    }

    /** 記住位址：Tailscale 位址和區網位址分開存，兩種網路都能用。 */
    static synchronized void updateAddress(Context c, String id, String ip, int port) {
        List<Peer> l = peers(c);
        for (Peer p : l) {
            if (!p.id.equals(id)) continue;
            if (isTailnet(ip)) { p.tsIp = ip; p.tsPort = port; }
            else { p.ip = ip; p.port = port; }
        }
        save(c, l);
    }

    static synchronized void updateName(Context c, String id, String name) {
        List<Peer> l = peers(c);
        boolean changed = false;
        for (Peer p : l) if (p.id.equals(id) && !p.name.equals(name)) { p.name = name; changed = true; }
        if (changed) save(c, l);
    }

    static byte[] keyOf(Context c, String id) {
        for (Peer p : peers(c)) if (p.id.equals(id)) return p.key;
        return null;
    }

    static String nameOf(Context c, String id) {
        for (Peer p : peers(c)) if (p.id.equals(id)) return p.name;
        return id;
    }

    // ---------- 記錄 ----------

    private static File logFile(Context c) { return new File(c.getFilesDir(), "transfer.log"); }

    static synchronized void log(Context c, String line) {
        try {
            List<String> l = new ArrayList<>();
            File f = logFile(c);
            if (f.exists()) l.addAll(Files.readAllLines(f.toPath(), StandardCharsets.UTF_8));
            l.add(new SimpleDateFormat("MM/dd HH:mm", Locale.TAIWAN).format(new Date()) + "  " + line);
            while (l.size() > 300) l.remove(0);
            Files.write(f.toPath(), String.join("\n", l).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) { }
    }

    static synchronized List<String> recentLog(Context c, int n) {
        List<String> out = new ArrayList<>();
        try {
            List<String> l = Files.readAllLines(logFile(c).toPath(), StandardCharsets.UTF_8);
            for (int i = l.size() - 1; i >= 0 && out.size() < n; i--) out.add(l.get(i));
        } catch (Exception ignored) { }
        return out;
    }

    static String size(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024 * 1024) return String.format(Locale.US, "%.1f KB", b / 1024.0);
        if (b < 1024L * 1024 * 1024) return String.format(Locale.US, "%.1f MB", b / 1048576.0);
        return String.format(Locale.US, "%.2f GB", b / 1073741824.0);
    }
}
