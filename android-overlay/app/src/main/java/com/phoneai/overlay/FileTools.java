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
        final File[] held = new File[1];   // file_get 預訂的存檔位置，結束時要放掉
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
                    TransferNotify.begin();
                    TransferClient cl = client(c, dev);
                    TransferStore.inbox().mkdirs();
                    purgeOldParts(TransferStore.inbox());
                    File dest = reserveDest(TransferStore.inbox(), new File(p.replace('\\', '/')).getName(), reuseNameFor(c, peerNamed(c, dev), p));
                    held[0] = dest;
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
                    TransferNotify.begin();
                    cl2 = client(c, dev);
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
                        TransferNotify.haltedById(c, cl2.trackedId(), io.getMessage(), false);   // 意外中斷：通知留著，可以按「繼續」
                        throw io;
                    }
                    TransferStore.log(c, L.t("傳出 ") + src.getName() + " → " + dev + shaNote(cl2));
                    TransferNotify.finish(c, L.t("已傳到 ") + dev + "：" + src.getName() + shaNote(cl2));
                    return "已傳到對方的收件匣，檔名：" + saved + "（" + TransferStore.size(src.length()) + "）" + shaNote(cl2);
                }
                default:
                    return "錯誤：未知工具 " + name;
            }
        } catch (TransferNotify.Stopped s) {   // 還沒開始傳（找對方、叫醒的時候）就按了停止
            TransferNotify.finish(c, L.t("已停止"));
            return "已停止";
        } catch (Exception e) {
            TransferNotify.cancel(c);
            return "錯誤：" + e.getMessage();
        } finally {
            releaseDest(held[0]);
            if (name.equals("file_get") || name.equals("file_send")) TransferNotify.release();
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

    /** 刪資料夾或檔案：遇到符號連結只刪連結本身，不進去它指向的地方（不然會刪到共享資料夾以外的東西）。 */
    static boolean deleteTree(File f) {
        if (java.nio.file.Files.isSymbolicLink(f.toPath())) return f.delete();
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
            TransferClient cl = clientFor(c, p);
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
                TransferNotify.haltedById(c, cl.trackedId(), io.getMessage(), false);   // 意外中斷：通知留著，可以按「繼續」
                throw io;
            }
            TransferStore.log(c, L.t("傳出 ") + name + " → " + p.name + shaNote(cl));
            TransferNotify.finish(c, L.t("已傳到 ") + p.name + "：" + saved + shaNote(cl));
        } catch (TransferNotify.Stopped s) {
            TransferNotify.finish(c, L.t("已停止傳給 ") + p.name);
        } catch (Exception e) {
            TransferNotify.cancel(c);
            throw e;
        } finally {
            TransferNotify.release();
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
        File dest = reserveDest(TransferStore.inbox(), name, reuseNameFor(c, p, remotePath));
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
        } finally {
            releaseDest(dest);
            TransferNotify.release();
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
        } finally {
            TransferNotify.release();
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
        TransferNotify.phase(c, L.t("正在找：") + p.name, L.t("找對方"));
        { TransferStore.Peer q = freshPeer(c, p); TransferStore.log(c, "找對方：" + q.name + "｜記下的區網位址 " + q.ip + ":" + q.port + "｜Tailscale " + q.tsIp + ":" + q.tsPort + "｜我的區網 " + FcmLab.lanNet()); }
        TransferDiscovery.Found f = TransferDiscovery.scan(p.id, 1200, TransferDiscovery.PORT, extra(one)).get(p.id);
        if (f == null) TransferStore.log(c, "廣播搜尋沒找到：" + p.name);
        if (f == null) f = directLan(c, p);   // 區網「廣播搜尋」被擋（路由器、省電、螢幕關著的手機常會丟掉廣播封包）時，直接連上次記下的區網位址
        if (f == null && FcmLab.canRing(c, p) && !(p.tsIp != null && p.tsPort > 0 && canPing(c, p, p.tsIp, p.tsPort))) {
            final boolean remoteNow = FcmLab.isRemote(FcmLab.lanNet(), p.ip);   // 沒連 Wi-Fi、或連的是另一個網路：都要走 Tailscale
            if (remoteNow && !TailscaleControl.isOn()) {   // 不需要先知道對方的 Tailscale 位址：叫醒後它會自己回報
                TransferNotify.phase(c, L.t("正在開 Tailscale（外網）…"), L.t("開外網"));
                TailscaleControl.turnOnForSession(c, 12000);
            }
            TransferNotify.phase(c, L.t("對方沒回應，正在用推播叫醒：") + p.name, L.t("叫醒中"));   // 不在 Wi-Fi 上、又知道對方的 Tailscale 位址：先把自己的 Tailscale 開起來
            // 找不到對方：用推播叫它把互傳伺服器開起來，再等它出現（最多約 15 秒）。傳檔、瀏覽、找檔都走這裡，所以全部都不用對方先開著
            String err = FcmLab.wake(c, p);
            if (err == null) {
                long t0 = System.currentTimeMillis(), until = t0 + (remoteNow ? 45000 : 25000), nextRing = t0 + 8000;   // 外網要多等：對方的推播、開 Tailscale、建立路徑都要時間（螢幕關著更慢）
                while (f == null && System.currentTimeMillis() < until) {
                    if (TransferNotify.stopRequested()) throw new TransferNotify.Stopped();   // 等對方醒來的時候也要能按停止
                    f = TransferDiscovery.scan(p.id, 1500, TransferDiscovery.PORT, extra(one)).get(p.id);
                    if (f == null) f = directLan(c, p);   // 對方伺服器開好了，但廣播沒回應：每一輪也直接連連看
                    TransferStore.Peer now = freshPeer(c, p);   // 對方回報的 Tailscale 位址會在等待中途才寫進來，每一輪都重讀
                    if (f == null && now.tsIp != null && now.tsPort > 0 && canPing(c, now, now.tsIp, now.tsPort)) break;   // 區網找不到，但 Tailscale 位址連得上了：往下走原本的 Tailscale 路徑
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
        boolean verified = true;   // 這個位址是掃描找到或剛連得上的
        final TransferStore.Peer cur = freshPeer(c, p);
        if (f != null) {
            ip = f.ip;
            port = f.port;
        } else if (cur.tsIp != null && cur.tsPort > 0 && canPing(c, cur, cur.tsIp, cur.tsPort)) {
            ip = cur.tsIp;
            port = cur.tsPort;
        } else {
            ip = p.ip;
            port = p.port;
            verified = false;   // 區網找不到、Tailscale 也不通：只剩上次記下的位址，還沒確認對方在不在
        }
        if (ip == null || port == 0) throw new Exception("找不到「" + p.name + "」：可能沒開機、沒開互傳，或不在同一個 Wi-Fi（外網請確認 Tailscale 都在線）");
        if (!verified && !canPing(c, p, ip, port))   // 對方沒回應就直接說，不要先顯示「已連上」再傳到一半才失敗
            throw new Exception("找不到「" + p.name + "」：它沒有回應。可能已經關機或沒電、手機沒網路、或不在同一個 Wi-Fi（外網請確認兩邊 Tailscale 都登入）");
        if (f != null) {
            TransferStore.updateAddress(c, p.id, ip, port);
            TransferStore.updateName(c, p.id, f.name);
        }
        if (ip != null && Doorbell.isTailnet(ip)) TailscaleControl.noteUse();   // 走 Tailscale：重新計「閒置多久才關回去」
        TransferNotify.phase(c, L.t("已連上，開始傳送…"), L.t("已連上"));
        TransferClient cl = new TransferClient(ip, port, TransferStore.myId(c), TransferStore.myName(c), p.key);
        final String shownName = p.name;
        cl.setReconnector(() -> {   // 傳到一半（或剛開始）連不上：重新找一次對方（區網掃描、叫醒、外網），不要一直對著可能過期的位址重試
            TransferNotify.phase(c, L.t("連不上，重新找對方：") + shownName, L.t("重找"));
            TransferClient n = clientFor(c, p);
            return new String[]{n.host(), String.valueOf(n.portNumber())};
        });
        if (f != null || Doorbell.isTailnet(ip)) FcmLab.healTokenAsync(c, p, cl);   // 確實連得上對方時（區網找得到、或走 Tailscale），順便互換推播 token：補沒有的、更新對方換過的
        return cl;
    }

    /**
     * 不靠廣播：直接連對方上次記下的區網位址（TCP）。連得上就當作找到（位址沒變、伺服器有開）；連不上回 null。
     * 為什麼需要：UDP 廣播搜尋常被擋（路由器隔離、對方螢幕關著時 Wi-Fi 會丟掉廣播），結果對方明明已被叫醒、伺服器也開了，這邊卻一直找不到、一直「讀取中」。
     */
    private static TransferDiscovery.Found directLan(Context c, TransferStore.Peer p) {
        TransferStore.Peer cur = freshPeer(c, p);
        if (cur.ip == null || cur.port <= 0 || Doorbell.isTailnet(cur.ip)) return null;
        if (!canPing(c, cur, cur.ip, cur.port)) return scanSubnet(c, cur);
        TransferStore.log(c, "直接連上次的區網位址成功：" + cur.ip + ":" + cur.port + "（廣播搜尋沒找到）");
        TransferDiscovery.Found g = new TransferDiscovery.Found();
        g.id = cur.id;
        g.name = cur.name;
        g.ip = cur.ip;
        g.port = cur.port;
        return g;
    }

    private static volatile long lastScan;

    /**
     * 記下的位址連不上（常是對方換了 DHCP 位址：No route to host），廣播又被擋：逐一試本機所在 /24 網段的每個位址的互傳埠，
     * 開著的再用配對金鑰驗證；找到就更新記下的位址。最多每 20 秒掃一次。
     */
    private static TransferDiscovery.Found scanSubnet(Context c, TransferStore.Peer cur) {
        String net = FcmLab.lanNet();
        if (net == null || cur.port <= 0) return null;
        long now = System.currentTimeMillis();
        if (now - lastScan < 20000) return null;
        lastScan = now;
        final String me = net.substring(0, net.indexOf('/'));
        final String base = me.substring(0, me.lastIndexOf('.') + 1);
        final int port = cur.port;
        final java.util.concurrent.ConcurrentLinkedQueue<String> open = new java.util.concurrent.ConcurrentLinkedQueue<>();
        java.util.concurrent.ExecutorService ex = java.util.concurrent.Executors.newFixedThreadPool(48);
        for (int i = 1; i < 255; i++) {
            final String ip = base + i;
            if (ip.equals(me) || ip.equals(cur.ip)) continue;
            ex.execute(() -> {
                try (java.net.Socket s = new java.net.Socket()) {
                    s.connect(new java.net.InetSocketAddress(ip, port), 700);
                    open.add(ip);
                } catch (Exception ignored) { }
            });
        }
        ex.shutdown();
        try { ex.awaitTermination(15, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
        TransferStore.log(c, "掃網段 " + base + "x 的埠 " + port + "：開著的 " + open.size() + " 個");
        for (String ip : open) {
            if (!canPing(c, cur, ip, port)) continue;
            TransferStore.log(c, "掃網段找到對方：" + cur.name + " 現在在 " + ip + ":" + port + "（記下的是 " + cur.ip + "）");
            TransferStore.updateAddress(c, cur.id, ip, port);
            TransferDiscovery.Found g = new TransferDiscovery.Found();
            g.id = cur.id;
            g.name = cur.name;
            g.ip = ip;
            g.port = port;
            return g;
        }
        return null;
    }

    /** 已配對裝置目前存的樣子（位址可能在等待中被更新）；找不到就用原本那份。 */
    private static TransferStore.Peer freshPeer(Context c, TransferStore.Peer p) {
        for (TransferStore.Peer q : TransferStore.peers(c)) if (q.id.equals(p.id)) return q;
        return p;
    }

    private static boolean canPing(Context c, TransferStore.Peer p, String ip, int port) {
        long t0 = System.currentTimeMillis();
        try {
            new TransferClient(ip, port, TransferStore.myId(c), TransferStore.myName(c), p.key).ping();
            return true;
        } catch (Exception e) {
            TransferStore.log(c, "連線測試失敗：" + p.name + " " + ip + ":" + port + "（" + (System.currentTimeMillis() - t0) + " 毫秒）" + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : "：" + e.getMessage()));   // 診斷：是逾時（位址不通）、被拒絕（沒人在聽）、還是驗證失敗
            return false;
        }
    }

    /** 校驗碼的一小段說明，放在記錄與通知後面：前 8 碼，以及有沒有跟對方核對過。 */
    static String shaNote(TransferClient cl) {
        if (cl == null || cl.lastSha.isEmpty()) return "";
        return " SHA-256 " + Checksum.shortOf(cl.lastSha) + (cl.lastVerified ? L.t(" ✓已核對") : L.t("（對方沒提供，未核對）"));
    }

    /** 依名稱（包含比對、不分大小寫）找已配對的裝置；一台都沒對上、但只配對了一台時，就是那一台。可能是 0、1 或多台。 */
    private static List<TransferStore.Peer> matchPeers(Context c, String dev) {
        List<TransferStore.Peer> peers = TransferStore.peers(c), hit = new ArrayList<>();
        String q = dev.toLowerCase(Locale.ROOT);
        for (TransferStore.Peer p : peers) if (p.name.toLowerCase(Locale.ROOT).contains(q) || q.contains(p.name.toLowerCase(Locale.ROOT))) hit.add(p);
        if (hit.isEmpty() && peers.size() == 1) hit.add(peers.get(0));
        return hit;
    }

    /** 找不到或有多台符合回 null（呼叫端用來登記進度，不需要報錯）。 */
    private static TransferStore.Peer peerNamed(Context c, String dev) {
        List<TransferStore.Peer> hit = matchPeers(c, dev);
        return hit.size() == 1 ? hit.get(0) : null;
    }

    private static TransferClient client(Context c, String dev) throws Exception {
        List<TransferStore.Peer> hit = matchPeers(c, dev);
        if (hit.isEmpty()) throw new Exception("沒有叫「" + dev + "」的已配對裝置；用 device_list 看有哪些");
        if (hit.size() > 1) throw new Exception("有多台符合「" + dev + "」，請說清楚是哪一台");
        return clientFor(c, hit.get(0));
    }

    /** 取檔存檔位置的預訂：同時進行的兩個取檔不會選到同一個檔名（以前各自檢查「檔案存在嗎」，兩個都說不存在、就寫進同一個暫存檔）。 */
    private static final java.util.Set<String> RESERVED = new java.util.HashSet<>();

    /**
     * 取檔的存檔位置，並預訂起來（用完要 releaseDest）：
     * 只有「這個遠端路徑上次沒收完」的那個檔名（reuseName，從未完成清單來的）才沿用它的半成品接著收；
     * 其他情況一律選一個「檔案不存在、沒有別人的半成品、沒被別的取檔預訂」的新檔名。
     * （以前只看檔名：不同資料夾的同名檔會接上對方的半成品，拼出壞檔。）
     */
    static File reserveDest(File inbox, String name, String reuseName) {
        synchronized (RESERVED) {
            if (reuseName != null && !reuseName.isEmpty() && new File(reuseName).getName().equals(reuseName)) {
                File prev = new File(inbox, reuseName);
                if (TransferClient.partOf(prev).isFile() && !prev.exists() && !RESERVED.contains(prev.getPath())) {
                    RESERVED.add(prev.getPath());
                    return prev;
                }
            }
            File f = unique(inbox, name);
            RESERVED.add(f.getPath());
            return f;
        }
    }

    static void releaseDest(File f) {
        if (f == null) return;
        synchronized (RESERVED) { RESERVED.remove(f.getPath()); }
    }

    /** 這個遠端路徑上次沒收完的存檔檔名（從未完成清單找）；沒有回 null。 */
    private static String reuseNameFor(Context c, TransferStore.Peer peer, String remotePath) {
        try {
            if (peer == null) return null;
            PendingStore.Entry e = PendingTransfers.store(c).find(PendingStore.idFor("down", peer.id, remotePath, 0));
            return e == null ? null : e.name;
        } catch (Exception ex) {
            return null;
        }
    }

    /** 清掉三天前留下的半成品（沒人要接著收的）。 */
    static void purgeOldParts(File inbox) { Resume.purgeOldParts(inbox, System.currentTimeMillis()); }

    private static File unique(File dir, String name) {
        File f = new File(dir, name);
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name, ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 2; f.exists() || TransferClient.partOf(f).exists() || RESERVED.contains(f.getPath()); i++) f = new File(dir, base + " (" + i + ")" + ext);
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
