package com.phoneai.overlay;

/**
 * 找已配對的對方「現在在哪個位址」的判斷順序（從 FileTools.clientFor 抽出來，才能用假的網路做單元測試）。
 * 順序：
 * 1) 區網廣播搜尋（同一個 Wi-Fi 最快）
 * 2) 廣播被擋：直接連上次記下的區網位址；連不上再掃本機網段（Env.directLan 裡做）
 * 3) 還是找不到、能用推播叫醒、而且記下的 Tailscale 位址也不通：Tailscale 沒開就先開，再叫醒對方，
 *    然後每一輪重新找（廣播、區網、對方中途回報的 Tailscale 位址），每 8 秒補送一次推播；同一個 Wi-Fi 等 25 秒，外網等 45 秒
 * 4) 決定位址：找到的 > 連得上的 Tailscale 位址 > 上次記下的區網位址（這個要再測一次連得上才用）
 * 網路、推播、Tailscale、時鐘全部經過 Env，這個類別本身不碰 Android。
 */
final class PeerFinder {
    private PeerFinder() { }

    static final long WAIT_LAN_MS = 25000, WAIT_REMOTE_MS = 45000, RERING_MS = 8000;

    interface Env {
        /** 區網廣播搜尋，找不到回 null。 */
        TransferDiscovery.Found scan(int timeoutMs);
        /** 不靠廣播：連上次記下的區網位址、不行再掃網段；找不到回 null。 */
        TransferDiscovery.Found directLan();
        /** 能不能用推播叫醒對方（有設定、有對方的 token）。 */
        boolean canRing();
        /** 實際連一次（含驗證）；peer 只用來寫紀錄。 */
        boolean ping(TransferStore.Peer peer, String ip, int port);
        /** 對方目前存著的資料（等待中對方回報的 Tailscale 位址會寫進來）。 */
        TransferStore.Peer fresh();
        String lanNet();
        boolean tailscaleOn();
        void turnOnTailscale();
        /** 送推播叫醒對方；成功回 null，失敗回原因。 */
        String wake();
        long now();
        boolean stopRequested();
        void log(String line);
        void phase(String text, String chip);
    }

    static final class Result {
        String ip;
        int port;
        TransferDiscovery.Found found;   // 區網找到的（有它時要更新記下的位址與名稱）
    }

    static Result find(Env e, TransferStore.Peer p) throws Exception {
        { TransferStore.Peer q = e.fresh(); e.log("找對方：" + q.name + "｜記下的區網位址 " + q.ip + ":" + q.port + "｜Tailscale " + q.tsIp + ":" + q.tsPort + "｜我的區網 " + e.lanNet()); }
        TransferDiscovery.Found f = e.scan(1200);
        if (f == null) e.log("廣播搜尋沒找到：" + p.name);
        if (f == null) f = e.directLan();   // 區網「廣播搜尋」被擋（路由器、省電、螢幕關著的手機常會丟掉廣播封包）時，直接連上次記下的區網位址
        if (f == null && e.canRing() && !(p.tsIp != null && p.tsPort > 0 && e.ping(p, p.tsIp, p.tsPort))) {
            // 區網（廣播、記下的位址、掃網段）都找不到、Tailscale 又沒開：兩邊的網段可能剛好一樣（例如都是 192.168.1.x 但不是同一台路由器），也當作在外面，主動開 Tailscale
            final boolean remoteNow = FcmLab.isRemote(e.lanNet(), p.ip) || !e.tailscaleOn();
            if (remoteNow && !e.tailscaleOn()) {   // 不需要先知道對方的 Tailscale 位址：叫醒後它會自己回報
                e.phase(L.t("正在開 Tailscale（外網）…"), L.t("開外網"));
                e.turnOnTailscale();
            }
            e.phase(L.t("對方沒回應，正在用推播叫醒：") + p.name, L.t("叫醒中"));
            // 找不到對方：用推播叫它把互傳伺服器開起來，再等它出現。傳檔、瀏覽、找檔都走這裡，所以全部都不用對方先開著
            String err = e.wake();
            if (err == null) {
                long t0 = e.now(), until = t0 + (remoteNow ? WAIT_REMOTE_MS : WAIT_LAN_MS), nextRing = t0 + RERING_MS;   // 外網要多等：對方的推播、開 Tailscale、建立路徑都要時間（螢幕關著更慢）
                while (f == null && e.now() < until) {
                    if (e.stopRequested()) throw new TransferNotify.Stopped();   // 等對方醒來的時候也要能按停止
                    f = e.scan(1500);
                    if (f == null) f = e.directLan();   // 對方伺服器開好了，但廣播沒回應：每一輪也直接連連看
                    TransferStore.Peer now = e.fresh();   // 對方回報的 Tailscale 位址會在等待中途才寫進來，每一輪都重讀
                    if (f == null && now.tsIp != null && now.tsPort > 0 && e.ping(now, now.tsIp, now.tsPort)) break;   // 區網找不到，但 Tailscale 位址連得上了：往下走 Tailscale
                    if (f == null && e.now() >= nextRing && e.now() < until - 4000) {
                        e.wake();   // 對方的推播連線可能剛好斷著：補送一則新的（舊的過期會被拒絕，新的才有效）
                        nextRing += RERING_MS;
                    }
                }
            } else {
                e.log(L.t("推播叫醒失敗：") + err + (FcmLab.tokenGone(err) ? L.t("（對方的 token 已失效；兩支手機都在線的時候會自動更新，也可以在「推播喚醒設定」重新互換）") : ""));
            }
        }
        Result r = new Result();
        r.found = f;
        boolean verified = true;   // 這個位址是掃描找到或剛連得上的
        final TransferStore.Peer cur = e.fresh();
        if (f != null) {
            r.ip = f.ip;
            r.port = f.port;
        } else if (cur.tsIp != null && cur.tsPort > 0 && e.ping(cur, cur.tsIp, cur.tsPort)) {
            r.ip = cur.tsIp;
            r.port = cur.tsPort;
        } else {
            r.ip = p.ip;
            r.port = p.port;
            verified = false;   // 區網找不到、Tailscale 也不通：只剩上次記下的位址，還沒確認對方在不在
        }
        if (r.ip == null || r.port == 0) throw new Exception("找不到「" + p.name + "」：可能沒開機、沒開互傳，或不在同一個 Wi-Fi（外網請確認 Tailscale 都在線）");
        if (!verified && !e.ping(p, r.ip, r.port))   // 對方沒回應就直接說，不要先顯示「已連上」再傳到一半才失敗
            throw new Exception("找不到「" + p.name + "」：它沒有回應。可能已經關機或沒電、手機沒網路、或不在同一個 Wi-Fi（外網請確認兩邊 Tailscale 都登入）");
        return r;
    }
}
