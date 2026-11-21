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
                        return pretty(sb.toString(), "這個資料夾沒有內容，或不存在。電腦的路徑要用完整寫法，例如 C:/Users/使用者名稱（Windows 的使用者資料夾叫 Users，不是 user）");
                    }
                    return pretty(client(c, dev).list(p), "這個資料夾沒有內容，或不存在。電腦的路徑要用完整寫法，例如 C:/Users/使用者名稱（Windows 的使用者資料夾叫 Users，不是 user）");
                }
                case "file_get": {
                    if (local) return "錯誤：file_get 是從另一台裝置取檔案，請指定 device";
                    String p = a.optString("path").trim();
                    if (p.isEmpty()) return "錯誤：要給檔案路徑（先用 file_find 找到）";
                    TransferClient cl = client(c, dev);
                    TransferStore.inbox().mkdirs();
                    purgeOldParts(TransferStore.inbox());
                    File dest = destForGet(TransferStore.inbox(), new File(p.replace('\\', '/')).getName());
                    PendingTransfers.track(c, cl, PendingTransfers.entry("down", peerNamed(c, dev), dest.getName(), "", p, 0));   // 登記進「沒傳完」清單
                    long n;
                    try {
                        n = cl.download(p, dest, (d, t) -> TransferNotify.progress(c, L.t("正在從 ") + dev + L.t(" 取回"),
                                L.t("存到：下載/PhoneAI"), dest.getName(), d, t));
                    } catch (TransferNotify.Stopped s) {
                        TransferNotify.finish(c, L.t("已停止，未完成的檔案已刪除"));
                        return "已停止，未完成的檔案已刪除";
                    } catch (TransferNotify.Paused pz) {
                        TransferNotify.haltedById(c, cl.trackedId(), L.t("已暫停，已收到的部分有保留，按「繼續」接著收"), true);
                        return "已暫停，已收到的部分有保留，到「互傳檔案」頁或通知按「繼續」";
                    } catch (java.io.IOException io) {
                        TransferNotify.haltedById(c, cl.trackedId(), io.getMessage(), false);   // 意外中斷：通知留著，可以按「繼續」
                        throw io;
                    }
                    TransferStore.log(c, L.t("取回 ") + dest.getName() + "（" + TransferStore.size(n) + "）" + shaNote(cl));
                    TransferNotify.finishFile(c, L.t("已存到 下載/PhoneAI/") + dest.getName() + "（" + TransferStore.size(n) + "）" + shaNote(cl), dest);   // 點通知開啟檔案
                    return "已存到 下載/PhoneAI/" + dest.getName() + "（" + TransferStore.size(n) + "）";
                }
                case "file_send": {
                    if (local) return "錯誤：file_send 要指定接收的 device";
                    String p = a.optString("path").trim();
                    File src = new File(TransferStore.root(), p.startsWith("/sdcard/") ? p.substring(8) : p);
                    if (!src.isFile() || !src.getCanonicalPath().startsWith(TransferStore.root().getCanonicalPath() + File.separator))
                        return "錯誤：找不到這個檔案：" + p;
                    String saved;
                    TransferClient cl2;
                    try {
                        cl2 = client(c, dev);
                    } catch (Exception notFound) {
                        TransferStore.Peer peer = peerNamed(c, dev);
                        if (!FcmLab.canRing(c, peer)) throw notFound;   // 連不上對方：有推播就改叫醒它來取
                        String err = FcmLab.ring(c, peer, p.startsWith("/sdcard/") ? p.substring(8) : p);
                        if (err != null) return "錯誤：對方連不上，改用推播叫醒也失敗：" + err;
                        TransferStore.log(c, L.t("推播叫醒 ") + peer.name + L.t(" 來取 ") + src.getName());
                        return "對方現在連不上，已用推播叫醒「" + peer.name + "」，請它自己來取：" + src.getName() + "（要同一個 Wi-Fi；它會存到對方的 下載/PhoneAI）";
                    }
                    PendingTransfers.track(c, cl2, PendingTransfers.entry("up", peerNamed(c, dev), src.getName(), src.getAbsolutePath(), "", src.length()));
                    try {
                        saved = cl2.putResumable(() -> new java.io.FileInputStream(src), src.length(), src.getName(), (d, t) -> TransferNotify.progress(c, L.t("正在傳給 ") + dev,
                                L.t("傳到：") + dev + L.t(" 的收件匣"), src.getName(), d, t));
                    } catch (TransferNotify.Stopped s) {
                        TransferNotify.finish(c, L.t("已停止傳給 ") + dev);
                        return "已停止，檔案沒有傳出";
                    } catch (TransferNotify.Paused pz) {
                        TransferNotify.haltedById(c, cl2.trackedId(), L.t("已暫停，對方已收到的部分有保留，按「繼續」接著傳"), true);
                        return "已暫停，到「互傳檔案」頁或通知按「繼續」";
                    } catch (java.io.IOException io) {
                        TransferStore.Peer peer = peerNamed(c, dev);
                        if (!isConnectFailure(io) || !FcmLab.canRing(c, peer)) {
                            TransferNotify.haltedById(c, cl2.trackedId(), io.getMessage(), false);   // 意外中斷：通知留著，可以按「繼續」
                            throw io;
                        }   // 一開始就連不上：改叫醒對方來取
                        String err = FcmLab.ring(c, peer, p.startsWith("/sdcard/") ? p.substring(8) : p);
                        if (err != null) return "錯誤：對方連不上，改用推播叫醒也失敗：" + err;
                        TransferStore.log(c, L.t("推播叫醒 ") + peer.name + L.t(" 來取 ") + src.getName());
                        return "對方現在連不上，已用推播叫醒「" + peer.name + "」，請它自己來取：" + src.getName() + "（要同一個 Wi-Fi；它會存到對方的 下載/PhoneAI）";
                    }
                    TransferStore.log(c, L.t("傳出 ") + src.getName() + " → " + dev + shaNote(cl2));
                    TransferNotify.finish(c, L.t("已傳到 ") + dev + "：" + src.getName() + shaNote(cl2));
                    return "已傳到對方的收件匣，檔名：" + saved + "（" + TransferStore.size(src.length()) + "）" + shaNote(cl2);
                }
                default:
                    return "錯誤：未知工具 " + name;
            }
        } catch (Exception e) {
            TransferNotify.cancel(c);
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
            if (f != null) {
                TransferStore.updateAddress(c, p.id, f.ip, f.port);
                TransferStore.updateName(c, p.id, f.name);
            }
            sb.append("- ").append(p.name).append(f != null ? "（在線）" : "（找不到，可能沒開機、沒開傳檔案、或不在同一個 Wi-Fi）").append("\n");
        }
        return sb.toString();
    }

    /** 在區網找這台已配對裝置（不叫醒、不連線）；找到回位址，沒找到回 null。診斷用。 */
    static TransferDiscovery.Found scanPeer(TransferStore.Peer p, int timeoutMs) {
        List<TransferStore.Peer> one = new ArrayList<>();
        one.add(p);
        return TransferDiscovery.scan(p.id, timeoutMs, TransferDiscovery.PORT, extra(one)).get(p.id);
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
    /** 手機上選到的檔案（content://）直接串流送給已配對的裝置，不用先複製到別處。 */
    static void sendUri(Context c, TransferStore.Peer p, android.net.Uri uri) throws Exception {
        android.database.Cursor cur = c.getContentResolver().query(uri, null, null, null, null);
        String name = "file";
        long size = -1;
        try {
            if (cur != null && cur.moveToFirst()) {
                int ni = cur.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                int si = cur.getColumnIndex(android.provider.OpenableColumns.SIZE);
                if (ni >= 0 && !cur.isNull(ni)) name = cur.getString(ni);
                if (si >= 0 && !cur.isNull(si)) size = cur.getLong(si);
            }
        } finally {
            if (cur != null) cur.close();
        }
        if (size < 0) size = 0;
        final long total = size;
        final String shown = name;
        TransferNotify.begin();
        try {
            TransferClient cl;
            try {
                cl = clientFor(c, p);
            } catch (Exception notFound) {
                if (!FcmLab.canRing(c, p)) throw notFound;   // 找不到對方：有推播就改按門鈴，沒有就維持原本的錯誤
                java.io.InputStream src = c.getContentResolver().openInputStream(uri);
                if (src == null) throw new Exception("打不開這個檔案");
                String err = FcmLab.sendViaDoorbell(c, p, src, name);
                if (err != null) throw new Exception(L.t("對方連不上，改用推播叫醒也失敗：") + err);
                TransferStore.log(c, L.t("推播叫醒 ") + p.name + L.t(" 來取 ") + name);
                TransferNotify.finish(c, L.t("已通知 ") + p.name + L.t(" 來取：") + name);
                return;
            }
            if (c.getContentResolver().openInputStream(uri) == null) throw new Exception("打不開這個檔案");   // 先確認打得開（會馬上關掉）
            String saved;
            PendingTransfers.track(c, cl, PendingTransfers.entry("up", p, name, uri.toString(), "", total));
            try {
                saved = cl.putResumable(() -> {
                    java.io.InputStream st = c.getContentResolver().openInputStream(uri);
                    if (st == null) throw new java.io.IOException("打不開這個檔案");
                    return st;
                }, total, name, (d, t) -> TransferNotify.progress(c, L.t("正在傳給 ") + p.name,
                        L.t("傳到：") + p.name + L.t(" 的收件匣"), shown, d, t));
            } catch (TransferNotify.Paused pz) {
                TransferNotify.haltedById(c, cl.trackedId(), L.t("已暫停，對方已收到的部分有保留，按「繼續」接著傳"), true);
                return;
            } catch (java.io.IOException io) {
                if (!isConnectFailure(io) || !FcmLab.canRing(c, p)) {
                    TransferNotify.haltedById(c, cl.trackedId(), io.getMessage(), false);   // 意外中斷：通知留著，可以按「繼續」
                    throw io;
                }   // 一開始就連不上、而且能用推播：改叫醒對方
                java.io.InputStream again = c.getContentResolver().openInputStream(uri);
                if (again == null) throw io;
                String err = FcmLab.sendViaDoorbell(c, p, again, name);
                if (err != null) throw new Exception(L.t("對方連不上，改用推播叫醒也失敗：") + err);
                TransferStore.log(c, L.t("推播叫醒 ") + p.name + L.t(" 來取 ") + name);
                TransferNotify.finish(c, L.t("已通知 ") + p.name + L.t(" 來取：") + name);
                return;
            }
            TransferStore.log(c, L.t("傳出 ") + name + " → " + p.name + shaNote(cl));
            TransferNotify.finish(c, L.t("已傳到 ") + p.name + "：" + saved + shaNote(cl));
        } catch (TransferNotify.Stopped s) {
            TransferNotify.finish(c, L.t("已停止傳給 ") + p.name);
        } catch (Exception e) {
            TransferNotify.cancel(c);
            throw e;
        }
    }

    /** 手動搜尋（不用 AI）的結果：每行 類型\t大小\t時間\t路徑，這裡換成 {類型, 大小, 路徑}。 */
    static List<String[]> rows(String raw) {
        List<String[]> out = new ArrayList<>();
        for (String line : raw.split("\n")) {
            String[] f = line.split("\t", 4);
            if (f.length == 4) out.add(new String[]{f[0], f[1], f[3]});
        }
        return out;
    }

    /** 手動搜本機（整支手機的儲存空間）。 */
    static List<String[]> searchLocal(String q) throws Exception {
        return rows(TransferServer.findIn(TransferStore.root(), q));
    }

    /** 手動搜某一台已配對的裝置。 */
    static List<String[]> searchPeer(Context c, TransferStore.Peer p, String q) throws Exception {
        return rows(clientFor(c, p).find(q));
    }

    /** 手動取回：從已配對的裝置取一個檔案到 下載/PhoneAI。 */
    static String fetch(Context c, TransferStore.Peer p, String remotePath) throws Exception {
        String name = new File(remotePath.replace('\\', '/')).getName();
        TransferStore.inbox().mkdirs();
        purgeOldParts(TransferStore.inbox());
        File dest = destForGet(TransferStore.inbox(), name);
        final String[] trackedId = new String[1];
        TransferNotify.begin();
        try {
            TransferClient cl = clientFor(c, p);
            PendingTransfers.track(c, cl, PendingTransfers.entry("down", p, dest.getName(), "", remotePath, 0));
            trackedId[0] = cl.trackedId();
            long n = cl.download(remotePath, dest, (d, t) -> TransferNotify.progress(c, L.t("正在從 ") + p.name + L.t(" 取回"),
                    L.t("存到：下載/PhoneAI"), dest.getName(), d, t));
            TransferStore.log(c, L.t("取回 ") + dest.getName() + "（" + TransferStore.size(n) + "）");
            TransferNotify.finish(c, L.t("已存到 下載/PhoneAI/") + dest.getName() + "（" + TransferStore.size(n) + "）");
            return dest.getName();
        } catch (TransferNotify.Stopped s) {
            TransferNotify.finish(c, L.t("已停止，未完成的檔案已刪除"));
            return null;
        } catch (TransferNotify.Paused pz) {
            TransferNotify.haltedById(c, trackedId[0], L.t("已暫停，已收到的部分有保留，按「繼續」接著收"), true);
            return null;
        } catch (java.io.IOException io) {
            TransferNotify.haltedById(c, trackedId[0], io.getMessage(), false);
            throw io;
        }
    }

    /** 手動傳：把手機上的一個檔案送到已配對的裝置。 */
    static String sendLocal(Context c, TransferStore.Peer p, File f) throws Exception {
        final String[] trackedId = new String[1];
        TransferNotify.begin();
        try {
            TransferClient cl = clientFor(c, p);
            PendingTransfers.track(c, cl, PendingTransfers.entry("up", p, f.getName(), f.getAbsolutePath(), "", f.length()));
            trackedId[0] = cl.trackedId();
            String saved = cl.putResumable(() -> new java.io.FileInputStream(f), f.length(), f.getName(), (d, t) -> TransferNotify.progress(c, L.t("正在傳給 ") + p.name,
                    L.t("傳到：") + p.name + L.t(" 的收件匣"), f.getName(), d, t));
            TransferStore.log(c, L.t("傳出 ") + f.getName() + " → " + p.name);
            TransferNotify.finish(c, L.t("已傳到 ") + p.name + "：" + saved);
            return saved;
        } catch (TransferNotify.Stopped s) {
            TransferNotify.finish(c, L.t("已停止傳給 ") + p.name);
            return null;
        } catch (TransferNotify.Paused pz) {
            TransferNotify.haltedById(c, trackedId[0], L.t("已暫停，對方已收到的部分有保留，按「繼續」接著傳"), true);
            return null;
        } catch (java.io.IOException io) {
            TransferNotify.haltedById(c, trackedId[0], io.getMessage(), false);
            throw io;
        }
    }

    /** 「沒傳完」清單按「繼續」要重新選檔案時回傳的記號。 */
    static final String NEED_PICK = "NEED_PICK";

    /** 繼續一筆沒傳完的傳輸（要在背景執行緒呼叫）。傳到這台裝置的來源檔案如果打不開，回 NEED_PICK，請使用者重新選同一個檔案（對方的半成品靠檔案指紋自動接上）。 */
    static String resume(Context c, PendingStore.Entry e) throws Exception {
        TransferStore.Peer peer = null;
        for (TransferStore.Peer q : TransferStore.peers(c)) if (q.id.equals(e.peerId)) peer = q;
        if (peer == null) {
            PendingTransfers.store(c).remove(e.id);
            return L.t("找不到這台裝置（可能已經移除配對）");
        }
        if (e.kind.equals("down")) {
            String saved = fetch(c, peer, e.remotePath);
            return saved == null ? L.t("已停止") : L.t("已存到 下載/PhoneAI/") + saved;
        }
        if (e.source.startsWith("content://")) {
            android.net.Uri u = android.net.Uri.parse(e.source);
            try (java.io.InputStream t = c.getContentResolver().openInputStream(u)) {
                if (t == null) return NEED_PICK;
            } catch (Exception ex) {
                return NEED_PICK;   // 授權過期或檔案不在了
            }
            sendUri(c, peer, u);
            return L.t("已傳完");
        }
        File f = new File(e.source);
        if (!f.isFile()) return NEED_PICK;
        sendLocal(c, peer, f);
        return L.t("已傳完");
    }

    /** 丟掉一筆沒傳完的：刪掉本機或對方那邊已經收到的半成品，並從清單移除。 */
    static void discardPending(Context c, PendingStore.Entry e) {
        try {
            if (e.kind.equals("down")) {
                TransferClient.partOf(new File(TransferStore.inbox(), e.name)).delete();
            } else if (Resume.validFp(e.fp)) {
                for (TransferStore.Peer q : TransferStore.peers(c)) {
                    if (!q.id.equals(e.peerId)) continue;
                    try { clientFor(c, q).discardRemote(e.fp); } catch (Exception ignored) { }   // 連不上就算了，對方的半成品三天後會被清掉
                }
            }
        } finally {
            PendingTransfers.store(c).remove(e.id);
        }
    }

    /** 找到這台已配對裝置現在的位址，回傳可用的客戶端。 */
    static TransferClient clientFor(Context c, TransferStore.Peer p) throws Exception {
        // 順序：1) 區網搜尋（在同一個 Wi‑Fi 最快）  2) Tailscale 位址（外網）  3) 上次記住的區網位址
        List<TransferStore.Peer> one = new ArrayList<>();
        one.add(p);
        TransferDiscovery.Found f = TransferDiscovery.scan(p.id, 1200, TransferDiscovery.PORT, extra(one)).get(p.id);
        if (f == null && FcmLab.canRing(c, p) && !(p.tsIp != null && p.tsPort > 0 && canPing(c, p, p.tsIp, p.tsPort))) {
            if (FcmLab.lanIp() == null && p.tsIp != null && !TailscaleControl.isOn()) TailscaleControl.turnOnForSession(c, 12000);   // 不在 Wi-Fi 上、又知道對方的 Tailscale 位址：先把自己的 Tailscale 開起來
            // 找不到對方：用推播叫它把互傳伺服器開起來，再等它出現（最多約 15 秒）。傳檔、瀏覽、找檔都走這裡，所以全部都不用對方先開著
            String err = FcmLab.wake(c, p);
            if (err == null) {
                long t0 = System.currentTimeMillis(), until = t0 + (FcmLab.lanIp() == null ? 45000 : 25000), nextRing = t0 + 8000;   // 外網要多等：對方的推播、開 Tailscale、建立路徑都要時間（螢幕關著更慢）
                while (f == null && System.currentTimeMillis() < until) {
                    f = TransferDiscovery.scan(p.id, 1500, TransferDiscovery.PORT, extra(one)).get(p.id);
                    if (f == null && p.tsIp != null && p.tsPort > 0 && canPing(c, p, p.tsIp, p.tsPort)) break;   // 區網找不到，但 Tailscale 位址連得上了：往下走原本的 Tailscale 路徑
                    if (f == null && System.currentTimeMillis() >= nextRing && System.currentTimeMillis() < until - 4000) {
                        FcmLab.wake(c, p);   // 對方的推播連線可能剛好斷著：補送一則新的（舊的過期會被拒絕，新的才有效）
                        nextRing += 8000;
                    }
                }
            } else {
                TransferStore.log(c, L.t("推播叫醒失敗：") + err + (FcmLab.tokenGone(err) ? L.t("（對方的 token 已失效；兩支手機都在線的時候會自動更新，也可以在「推播喚醒設定」重新互換）") : ""));
            }
        }
        String ip;
        int port;
        if (f != null) {
            ip = f.ip;
            port = f.port;
        } else if (p.tsIp != null && p.tsPort > 0 && canPing(c, p, p.tsIp, p.tsPort)) {
            ip = p.tsIp;
            port = p.tsPort;
        } else {
            ip = p.ip;
            port = p.port;
        }
        if (ip == null || port == 0) throw new Exception("找不到「" + p.name + "」：可能沒開機、沒開互傳，或不在同一個 Wi-Fi（外網請確認 Tailscale 都在線）");
        if (f != null) {
            TransferStore.updateAddress(c, p.id, ip, port);
            TransferStore.updateName(c, p.id, f.name);
        }
        if (ip != null && Doorbell.isTailnet(ip)) TailscaleControl.noteUse();   // 走 Tailscale：重新計「閒置多久才關回去」
        TransferClient cl = new TransferClient(ip, port, TransferStore.myId(c), TransferStore.myName(c), p.key);
        if (f != null || Doorbell.isTailnet(ip)) FcmLab.healTokenAsync(c, p, cl);   // 確實連得上對方時（區網找得到、或走 Tailscale），順便互換推播 token：補沒有的、更新對方換過的
        return cl;
    }

    private static boolean canPing(Context c, TransferStore.Peer p, String ip, int port) {
        try {
            new TransferClient(ip, port, TransferStore.myId(c), TransferStore.myName(c), p.key).ping();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 校驗碼的一小段說明，放在記錄與通知後面：前 8 碼，以及有沒有跟對方核對過。 */
    static String shaNote(TransferClient cl) {
        if (cl == null || cl.lastSha.isEmpty()) return "";
        return " SHA-256 " + Checksum.shortOf(cl.lastSha) + (cl.lastVerified ? L.t(" ✓已核對") : L.t("（對方沒提供，未核對）"));
    }

    /** 是不是「一開始就連不上對方」（還沒傳任何資料）：這種情況才可以安全地改用推播叫醒。傳到一半斷線不算。 */
    static boolean isConnectFailure(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof java.net.ConnectException || t instanceof java.net.NoRouteToHostException
                    || t instanceof java.net.UnknownHostException) return true;
            if (t instanceof java.net.SocketTimeoutException) {
                String m = String.valueOf(t.getMessage()).toLowerCase(Locale.ROOT);
                if (m.contains("connect")) return true;   // 連線逾時；讀取逾時（已經在傳了）不算
            }
            String m = t.getMessage();
            if (m != null && m.startsWith("failed to connect to")) return true;
        }
        return false;
    }

    /** 依名稱（包含比對）找已配對的裝置；找不到或有多台符合回 null。 */
    private static TransferStore.Peer peerNamed(Context c, String dev) {
        List<TransferStore.Peer> peers = TransferStore.peers(c), hit = new ArrayList<>();
        String q = dev.toLowerCase(Locale.ROOT);
        for (TransferStore.Peer p : peers) if (p.name.toLowerCase(Locale.ROOT).contains(q) || q.contains(p.name.toLowerCase(Locale.ROOT))) hit.add(p);
        if (hit.isEmpty() && peers.size() == 1) hit.add(peers.get(0));
        return hit.size() == 1 ? hit.get(0) : null;
    }

    private static TransferClient client(Context c, String dev) throws Exception {
        List<TransferStore.Peer> peers = TransferStore.peers(c), hit = new ArrayList<>();
        String q = dev.toLowerCase(Locale.ROOT);
        for (TransferStore.Peer p : peers) if (p.name.toLowerCase(Locale.ROOT).contains(q) || q.contains(p.name.toLowerCase(Locale.ROOT))) hit.add(p);
        if (hit.isEmpty() && peers.size() == 1) hit.add(peers.get(0));
        if (hit.isEmpty()) throw new Exception("沒有叫「" + dev + "」的已配對裝置；用 device_list 看有哪些");
        if (hit.size() > 1) throw new Exception("有多台符合「" + dev + "」，請說清楚是哪一台");
        return clientFor(c, hit.get(0));
    }

    /** 取檔的存檔位置：如果上次沒收完留下了這個檔名的半成品，就沿用同一個檔名好接著收；否則找不重複的檔名。 */
    static File destForGet(File inbox, String name) {
        File plain = new File(inbox, name);
        if (TransferClient.partOf(plain).isFile() && !plain.exists()) return plain;
        return unique(inbox, name);
    }

    /** 清掉三天前留下的半成品（沒人要接著收的）。 */
    static void purgeOldParts(File inbox) { Resume.purgeOldParts(inbox, System.currentTimeMillis()); }

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
