package com.phoneai.overlay;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 助理的「傳檔案」工具（device_list、file_find、file_list、file_get、file_send）：把自然語言的請求換成
 * 對另一台已配對裝置的 TransferClient 呼叫。要在背景執行緒呼叫；回傳給模型的文字都是結果或錯誤說明。
 */
final class FileTools {
    private FileTools() { }

    static boolean handles(String name) {
        return name.equals("device_list") || name.equals("file_find") || name.equals("file_list")
                || name.equals("file_get") || name.equals("file_send") || name.equals("file_delete");
    }

    static String run(Context c, String name, JSONObject a, Memory.Confirm confirm) {
        try {
            if (!TransferStore.enabled(c)) return "錯誤：傳檔案功能還沒開。請使用者到「設定 → 傳檔案」打開，並和另一台裝置配對。";
            if (!TransferStore.hasAllFiles()) return "錯誤：還沒有「所有檔案存取」權限。請使用者到「設定 → 傳檔案」授權。";
            if (name.equals("device_list")) return deviceList(c);
            if (name.equals("file_delete")) return delete(c, a.optString("path"), confirm);
            String dev = a.optString("device").trim();
            boolean local = dev.isEmpty() || dev.equals("this") || dev.equals("本機") || dev.equals("這支手機");
            switch (name) {
                case "file_find": {
                    String q = a.optString("name").trim();
                    if (q.isEmpty()) return "錯誤：要給檔名關鍵字";
                    String raw = local ? TransferServer.findIn(TransferStore.root(), q) : client(c, dev).find(q);
                    return pretty(raw, "沒有找到檔名包含「" + q + "」的檔案");
                }
                case "file_list": {
                    String p = a.optString("path");
                    if (local) {
                        File dir = new File(TransferStore.root(), p);
                        File[] kids = dir.listFiles();
                        StringBuilder sb = new StringBuilder();
                        if (kids != null) for (int i = 0; i < kids.length && i < 100; i++) sb.append(TransferServer.lineOf(TransferStore.root(), kids[i]));
                        return pretty(sb.toString(), "空的或不存在");
                    }
                    return pretty(client(c, dev).list(p), "空的或不存在");
                }
                case "file_get": {
                    if (local) return "錯誤：file_get 是從另一台裝置取檔案，請指定 device";
                    String p = a.optString("path").trim();
                    if (p.isEmpty()) return "錯誤：要給檔案路徑（先用 file_find 找到）";
                    TransferClient cl = client(c, dev);
                    File dest = unique(TransferStore.inbox(), new File(p.replace('\\', '/')).getName());
                    dest.getParentFile().mkdirs();
                    long n = cl.download(p, dest, null);
                    TransferStore.log(c, L.t("取回 ") + dest.getName() + "（" + TransferStore.size(n) + "）");
                    return "已存到 下載/PhoneAI/" + dest.getName() + "（" + TransferStore.size(n) + "）";
                }
                case "file_send": {
                    if (local) return "錯誤：file_send 要指定接收的 device";
                    String p = a.optString("path").trim();
                    File src = new File(TransferStore.root(), p.startsWith("/sdcard/") ? p.substring(8) : p);
                    if (!src.isFile() || !src.getCanonicalPath().startsWith(TransferStore.root().getCanonicalPath() + File.separator))
                        return "錯誤：找不到這個檔案：" + p;
                    String saved = client(c, dev).put(src, null);
                    TransferStore.log(c, L.t("傳出 ") + src.getName() + " → " + dev);
                    return "已傳到對方的收件匣，檔名：" + saved + "（" + TransferStore.size(src.length()) + "）";
                }
                default:
                    return "錯誤：未知工具 " + name;
            }
        } catch (Exception e) {
            return "錯誤：" + e.getMessage();
        }
    }

    /** 刪除手機儲存空間裡的檔案或資料夾（不含 Android/）；每一次都先跳確認框，使用者按否就不刪。 */
    private static String delete(Context c, String path, Memory.Confirm confirm) throws Exception {
        String p = path == null ? "" : path.trim().replace('\\', '/');
        if (p.startsWith("/sdcard/")) p = p.substring(8);
        while (p.startsWith("/")) p = p.substring(1);
        if (p.isEmpty()) return "錯誤：要給檔案路徑";
        File root = TransferStore.root().getCanonicalFile();
        File f = new File(root, p).getCanonicalFile();
        if (f.equals(root) || !f.getPath().startsWith(root.getPath() + File.separator)) return "錯誤：路徑不在手機儲存空間裡";
        if (f.getPath().startsWith(new File(root, "Android").getPath())) return "錯誤：不能刪除 Android/ 裡的東西（其他 App 的資料）";
        if (!f.exists()) return "錯誤：找不到 " + p;
        String shown = TransferServer.relTo(root, f);
        String what = f.isDirectory() ? L.t("資料夾（連裡面的東西一起）") : L.t("檔案") + "（" + TransferStore.size(f.length()) + "）";
        if (confirm == null || !confirm.ask(L.t("要刪除") + what + "：\n" + shown + "\n" + L.t("刪除後無法復原。"))) return "使用者沒有同意，沒有刪除";
        if (!deleteTree(f)) return "錯誤：刪除失敗 " + shown;
        TransferStore.log(c, L.t("刪除 ") + shown);
        return "已刪除 " + shown;
    }

    private static boolean deleteTree(File f) {
        File[] kids = f.isDirectory() ? f.listFiles() : null;
        if (kids != null) for (File k : kids) deleteTree(k);
        return f.delete();
    }

    private static String deviceList(Context c) {
        List<TransferStore.Peer> peers = TransferStore.peers(c);
        if (peers.isEmpty()) return "還沒有配對過的裝置。請使用者到「設定 → 傳檔案」配對。";
        Map<String, TransferDiscovery.Found> seen = TransferDiscovery.scan("*", 1500, TransferDiscovery.PORT, extra(peers));
        StringBuilder sb = new StringBuilder();
        for (TransferStore.Peer p : peers) {
            TransferDiscovery.Found f = seen.get(p.id);
            if (f != null) TransferStore.updateAddress(c, p.id, f.ip, f.port);
            sb.append("- ").append(p.name).append(f != null ? "（在線）" : "（找不到，可能沒開機、沒開傳檔案、或不在同一個 Wi-Fi）").append("\n");
        }
        return sb.toString();
    }

    private static List<InetAddress> extra(List<TransferStore.Peer> peers) {
        List<InetAddress> l = new ArrayList<>();
        for (TransferStore.Peer p : peers) {
            if (p.ip == null) continue;
            try { l.add(InetAddress.getByName(p.ip)); } catch (Exception ignored) { }
        }
        return l;
    }

    /** 用名稱（包含比對）找已配對的裝置，探索它現在的位址，回傳可用的客戶端。 */
    private static TransferClient client(Context c, String dev) throws Exception {
        List<TransferStore.Peer> peers = TransferStore.peers(c), hit = new ArrayList<>();
        String q = dev.toLowerCase(Locale.ROOT);
        for (TransferStore.Peer p : peers) if (p.name.toLowerCase(Locale.ROOT).contains(q) || q.contains(p.name.toLowerCase(Locale.ROOT))) hit.add(p);
        if (hit.isEmpty() && peers.size() == 1) hit.add(peers.get(0));
        if (hit.isEmpty()) throw new Exception("沒有叫「" + dev + "」的已配對裝置；用 device_list 看有哪些");
        if (hit.size() > 1) throw new Exception("有多台符合「" + dev + "」，請說清楚是哪一台");
        TransferStore.Peer p = hit.get(0);
        List<TransferStore.Peer> one = new ArrayList<>();
        one.add(p);
        TransferDiscovery.Found f = TransferDiscovery.scan(p.id, 2500, TransferDiscovery.PORT, extra(one)).get(p.id);
        String ip = f != null ? f.ip : p.ip;
        int port = f != null ? f.port : p.port;
        if (ip == null || port == 0) throw new Exception("找不到「" + p.name + "」：可能沒開機、沒開傳檔案、或不在同一個 Wi-Fi");
        if (f != null) TransferStore.updateAddress(c, p.id, ip, port);
        return new TransferClient(ip, port, TransferStore.myId(c), p.key);
    }

    private static File unique(File dir, String name) {
        File f = new File(dir, name);
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name, ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 2; f.exists(); i++) f = new File(dir, base + " (" + i + ")" + ext);
        return f;
    }

    /** 伺服器回的「類型\t大小\t修改時間\t路徑」每行，換成給模型看的文字。 */
    private static String pretty(String raw, String empty) {
        if (raw == null || raw.trim().isEmpty()) return empty;
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        StringBuilder sb = new StringBuilder();
        for (String line : raw.split("\n")) {
            String[] f = line.split("\t", 4);
            if (f.length < 4) continue;
            long size = 0, t = 0;
            try { size = Long.parseLong(f[1]); t = Long.parseLong(f[2]); } catch (Exception ignored) { }
            sb.append(f[0].equals("D") ? "[資料夾] " : "[檔案] ").append(f[3]);
            if (f[0].equals("F")) sb.append("  ").append(TransferStore.size(size));
            sb.append("  ").append(df.format(new Date(t))).append("\n");
        }
        return sb.length() == 0 ? empty : sb.toString();
    }
}
