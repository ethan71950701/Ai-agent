"""vdisplay.py — 實驗：用 scrcpy 的伺服器建立「沒有視窗」的虛擬螢幕，測它能不能取代系統的模擬螢幕小視窗。

用法:
    python ~/vdisplay.py [秒數，預設 25] [--shell]     實驗：建立、開設定、截圖、關閉
    python ~/vdisplay.py --serve [--shell]            給 agent2.py 背景模式用：建立隱形螢幕後把編號寫到 /data/local/tmp/vdisplay.json，
                                                      一直保持到出現 /data/local/tmp/vdisplay.stop、逾時（15 分鐘）或伺服器結束
前提：~/scrcpy-server.jar 已下載（scrcpy 官方 release 的 scrcpy-server-v5.0.1，SHA-256 764eb6f7…53bc536）。
做法：用 root 啟動 scrcpy 伺服器（單獨執行、不接電腦，由本腳本接上它的串流並丟掉），伺服器會建立虛擬螢幕；
本腳本找出新螢幕的編號，試著在上面開「設定」並截圖，最後關掉伺服器（虛擬螢幕跟著消失）。
--shell：改用 shell 身分（uid 2000，scrcpy 本來的執行身分）啟動伺服器；root 身分被系統拒絕時再試。
狀態（2026-10-18）：實驗模式已在手機實測成功（建立虛擬螢幕、開設定全螢幕、截圖、關閉後螢幕消失、螢幕上沒有任何視窗）；
--serve 模式與 agent2.py 的整合**尚未實測**。
"""
import json, os, re, socket, subprocess, sys, threading, time

TERMUX = "/data/data/com.termux/files"
JAR_SRC = TERMUX + "/home/scrcpy-server.jar"
JAR = "/data/local/tmp/scrcpy-server.jar"
VERSION = "5.0.1"  # 必須跟 jar 的版本一模一樣，否則伺服器拒絕執行
SCID = "0badc0de"
SIZE = "1080x2340/420"
SHOT = "/data/local/tmp/vd.png"
OUT = "/data/local/tmp/vdisplay.json"
STOP = "/data/local/tmp/vdisplay.stop"
TTL = 900  # --serve 最久保持幾秒（agent2 被強制中斷、沒機會關掉時的保險）


# 呼叫系統指令（app_process、dumpsys、am…）時不能帶 Termux 的函式庫路徑，否則會載到 Termux 版的 OpenSSL 等，
# 跟系統的 libsqlite 對不上而崩潰（第一次實測：CANNOT LINK EXECUTABLE "app_process"）。
CLEAN_ENV = {k: v for k, v in os.environ.items() if k not in ("LD_LIBRARY_PATH", "LD_PRELOAD")}


def sh(cmd):
    r = subprocess.run(["/system/bin/sh", "-c", cmd], capture_output=True, text=True, env=CLEAN_ENV)
    return r.stdout + r.stderr


def snapshot():
    """目前有哪些螢幕：(系統編號集合, SurfaceFlinger 虛擬螢幕編號集合)。"""
    d = sh("dumpsys display")
    ids = set(re.findall(r"^\s+Display (\d+):", d, re.M))
    sf = set(re.findall(r"Display (\d+) \(Virtual display\)", sh("dumpsys SurfaceFlinger --display-id")))
    return ids, sf


def stop_server(srv, sock=None):
    """關掉伺服器：先斷開串流（伺服器偵測到就會自己結束），再 terminate，最後用 pkill 補刀。
    第一次實測只 terminate 外層 sh，真正的 app_process 還活著，虛擬螢幕沒有消失。"""
    try:
        if sock:
            sock.close()
    except OSError:
        pass
    srv.terminate()
    time.sleep(0.5)
    sh("pkill -f com.genymobile.scrcpy.Server")


START = b"\x00\x00\x00\x01"
# 存取單元分隔符（AUD，NAL 類型 9）：每個畫面後面補一個。切 NAL 的一方（這裡和 App）要看到「下一個起始碼」
# 才知道上一個 NAL 收完了；不補的話，每張畫面都要等下一張畫面來才顯示（畫面一停下，最後一張要等很久）。
AUD = START + b"\x09\xf0"


class FrameSplitter:
    """scrcpy 開 send_frame_meta 時，每個封包前面有 12 位元組：8 位元組 pts＋旗標、4 位元組長度（大端）。
    （scrcpy v5 的選項叫 send_stream_meta；沒關掉的話還會送「工作階段」標頭：最高位元是 1、只有 12 位元組沒有內容，這裡也會跳過。）
    feed() 回傳這次收完整的封包（一個封包 = 一整張畫面或 SPS/PPS 設定），純函式，方便離線測試。"""

    def __init__(self):
        self.buf = b""

    def feed(self, data):
        self.buf += data
        out = []
        while len(self.buf) >= 12:
            if self.buf[0] & 0x80:  # 工作階段標頭（寬、高），沒有內容
                self.buf = self.buf[12:]
                continue
            size = int.from_bytes(self.buf[8:12], "big")
            if len(self.buf) < 12 + size:
                break
            out.append(self.buf[12:12 + size])
            self.buf = self.buf[12 + size:]
        return out
STREAM_PORT = 47196
TOKEN_FILE = "/data/data/com.phoneai.overlay/files/token"  # 跟 agent2 一樣：只有 root（和 App 自己）讀得到


class StreamProxy:
    """把 scrcpy 的原始 H.264（Annex-B）影片轉送給 App 的預覽視窗。
    重點：新連上的預覽要馬上看得到畫面，所以記住 SPS/PPS 與「最近一個關鍵幀起」的所有 NAL，新客戶先收到這些再接上即時串流。
    feed()/backlog() 是純函式（不碰 socket），方便離線測試。"""

    def __init__(self, max_nals=900):
        self.buf = b""
        self.sps = self.pps = None
        self.gop = []  # 最近一個關鍵幀起的 NAL（含 SPS/PPS 在最前面）
        self.max_nals = max_nals
        self.frames = 0  # 累計收到的「畫面」數（H.264 的 NAL 類型 1、5），用來量串流實際幀率
        self.nbytes = 0

    def feed(self, data):
        """餵入一段原始資料，回傳這次新完整的 NAL 列表（每個都以 00 00 00 01 開頭）。"""
        self.buf += data
        pos = []
        i = 0
        while True:
            j = self.buf.find(b"\x00\x00\x01", i)
            if j < 0:
                break
            pos.append(j - 1 if j > 0 and self.buf[j - 1] == 0 else j)  # 四位元組起始碼的第一個 0
            i = j + 3
        nals = []
        for a, b in zip(pos, pos[1:]):
            raw = self.buf[a:b]
            k = len(raw) - len(raw.lstrip(b"\x00"))  # 起始碼 = 一串 0 加一個 0x01，兩個都要去掉
            nals.append(START + raw[k + 1:])
        if pos:
            self.buf = self.buf[pos[-1]:]  # 最後一個還可能不完整，留到下次
        for n in nals:
            t = n[4] & 0x1F
            self.nbytes += len(n)
            if t in (1, 5):
                self.frames += 1
            if t == 7:
                self.sps = n
            elif t == 8:
                self.pps = n
            elif t == 5:
                self.gop = [x for x in (self.sps, self.pps) if x] + [n]
            elif self.gop and t in (1, 6) and len(self.gop) < self.max_nals:
                self.gop.append(n)
        return nals

    def backlog(self):
        """新客戶要先收到的資料；還沒有關鍵幀就只給 SPS/PPS。"""
        return b"".join(self.gop) if self.gop else b"".join(x for x in (self.sps, self.pps) if x)


def start_stream_server(proxy):
    """在 127.0.0.1:47196 提供串流；連線後第一行必須是 App 的 token。回傳 (廣播函式, 關閉函式)。"""
    clients, lock = [], threading.Lock()
    try:
        token = open(TOKEN_FILE).read().strip()
    except OSError:
        token = None
    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("127.0.0.1", STREAM_PORT))
    srv.listen(2)

    def accept_loop():
        while True:
            try:
                c, _ = srv.accept()
            except OSError:
                return
            try:
                c.settimeout(3)
                line = b""
                while not line.endswith(b"\n") and len(line) < 100:
                    ch = c.recv(1)
                    if not ch:
                        break
                    line += ch
                if not token or line.strip().decode("ascii", "ignore") != token:
                    c.close()
                    continue
                c.settimeout(2)
                with lock:  # 先送完備份、再加入名單，中間不會漏掉也不會重複
                    c.sendall(proxy.backlog() + AUD)  # 補 AUD：備份的最後一張畫面才會馬上顯示
                    clients.append(c)
            except OSError:
                c.close()
    threading.Thread(target=accept_loop, daemon=True).start()

    def broadcast(nals):
        if not nals or not clients:
            return
        data = b"".join(nals)
        with lock:
            for c in list(clients):
                try:
                    c.sendall(data)
                except OSError:
                    clients.remove(c)
                    c.close()

    def close():
        try:
            srv.close()
        except OSError:
            pass
        with lock:
            for c in clients:
                c.close()
    return broadcast, close


SF_BEFORE = []  # 直接顯示：建立背景螢幕前就存在的虛擬螢幕截圖編號（agent2 用來在編號改變時重新找）


def parse_dpi(size, default=420):
    """'1080x1800/420' → 420；沒寫 dpi 用預設。"""
    m = re.match(r"\d+x\d+/(\d+)$", size or "")
    return int(m.group(1)) if m else default


def start_direct(vw, vh, dpi):
    """直接顯示（優先）：用 App 裡的 DirectDisplay 以 root 建立背景螢幕，畫面直接畫進 App 的預覽，不經 scrcpy、影片壓縮與轉送。
    成功回 (程序, 系統編號, 截圖編號)；App 太舊（沒有 DirectDisplay）或失敗回 None，呼叫端改用 scrcpy。"""
    out = sh("pm path com.phoneai.overlay").strip().splitlines()
    apk = out[0][len("package:"):].strip() if out and out[0].startswith("package:") else ""
    if not re.fullmatch(r"/[\w./=~+-]+\.apk", apk):
        print("直接顯示：找不到 App，改用 scrcpy")
        return None
    before = snapshot()
    cmd = "CLASSPATH=%s exec app_process / com.phoneai.overlay.DirectDisplay --host %d %d %d" % (apk, vw, vh, dpi)
    srv = subprocess.Popen(["/system/bin/sh", "-c", cmd], stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                           stderr=subprocess.STDOUT, text=True, env=CLEAN_ENV)
    got = {}

    def pump():
        for line in srv.stdout:
            print("[直接顯示]", line.rstrip())
            m = re.match(r"display (\d+)", line)
            if m:
                got["id"] = m.group(1)
    threading.Thread(target=pump, daemon=True).start()
    new_sf = None
    for _ in range(60):  # 最多約 15 秒
        if srv.poll() is not None:
            break
        if got.get("id"):
            sf = snapshot()[1] - before[1]
            if sf:
                new_sf = sorted(sf)[0]
                break
        time.sleep(0.25)
    if got.get("id") and new_sf:
        global SF_BEFORE
        SF_BEFORE = sorted(before[1])
        return srv, got["id"], new_sf
    print("直接顯示建立失敗（App 太舊或系統不允許），改用 scrcpy")
    stop_direct(srv)
    return None


def png_colors(path):
    """PNG 解壓後出現幾種不同的位元組值（粗略判斷是不是一片純色：全黑只有個位數種，有文字圖示的畫面會有上百種）。讀不到回 -1。"""
    import struct
    import zlib
    try:
        data = open(path, "rb").read()
        pos, idat = 8, b""
        while pos + 8 <= len(data):
            n, kind = struct.unpack(">I4s", data[pos:pos + 8])
            if kind == b"IDAT":
                idat += data[pos + 8:pos + 8 + n]
            pos += 12 + n
        raw = zlib.decompress(idat)
        return len(set(raw))
    except (OSError, zlib.error, struct.error):
        return -1


def direct_check(vw, vh, dpi):
    """檢查直接顯示的背景螢幕能不能截圖：建立 → 開設定 → 用幾種方式截圖，印出是不是全黑。"""
    r = start_direct(vw, vh, dpi)
    if not r:
        return 2
    srv, new_id, new_sf = r
    print("背景螢幕 系統編號=%s 截圖編號=%s，開設定…" % (new_id, new_sf))
    print(sh("am start --display %s -n com.android.settings/.Settings" % new_id).strip()[-100:])
    time.sleep(3)
    print("--- 背景螢幕的元件（overlay.py tree，只印在這裡）")
    subprocess.run([sys.executable, TERMUX + "/home/overlay.py", "tree", new_id])  # root 的 ~ 是 /，所以用 Termux 的家目錄路徑
    print("--- uitree.py：uiautomator 讀背景螢幕的元件（花幾秒、有幾個、App 的無障礙還在不在）")
    subprocess.run([sys.executable, TERMUX + "/home/uitree.py", new_id])
    for label, arg in (("截圖編號", new_sf), ("系統編號", new_id)):
        path = "/data/local/tmp/vd_check.png"
        err = sh("rm -f %s; screencap -d %s -p %s 2>&1; ls -l %s 2>&1" % (path, arg, path, path)).strip()
        ratio = png_colors(path)
        print("screencap -d %s（%s）: %s → 顏色種類 %d（%s）" % (arg, label, err[-120:], ratio,
              "一片純色（黑）" if 0 <= ratio < 20 else ("有畫面" if ratio >= 20 else "讀不到")))
    sh("rm -f /data/local/tmp/vd_check.png")
    stop_direct(srv)
    print("檢查完畢，背景螢幕已收掉")
    return 0


def stop_direct(srv):
    """關掉直接顯示主機：關標準輸入它就釋放背景螢幕並結束；沒結束就強制。"""
    try:
        srv.stdin.close()
    except (OSError, ValueError):
        pass
    try:
        srv.wait(2)
    except subprocess.TimeoutExpired:
        srv.terminate()
    sh("pkill -f com.phoneai.overlay.DirectDisplay")


def relaunch_as_root():
    """從 Termux 直接跑時，用 su 以 root 重新執行自己（用 Termux 的 python 與函式庫路徑）。"""
    cmd = "LD_LIBRARY_PATH=%s/usr/lib %s/usr/bin/python %s %s" % (
        TERMUX, TERMUX, os.path.abspath(__file__), " ".join(sys.argv[1:]))
    os.execvp("su", ["su", "-c", cmd])


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    secs = int(args[0]) if args else 25
    use_shell = "--shell" in sys.argv
    serve = "--serve" in sys.argv
    measure = None
    if "--measure" in sys.argv:  # 量幀率：開 Chrome 播一個 60fps 的動畫測試頁，統計 scrcpy 實際送出的幀率
        i = sys.argv.index("--measure")
        measure = int(sys.argv[i + 1]) if i + 1 < len(sys.argv) and sys.argv[i + 1].isdigit() else 20
    app = next((a[len("--app="):] for a in sys.argv[1:] if a.startswith("--app=")), None)  # 實驗模式：指定要開哪個 App（預設開「設定」）
    global SIZE
    for a in sys.argv[1:]:
        if a.startswith("--size="):  # 例如 --size=1080x1800/420（寬x高/dpi）；agent2 用它決定背景螢幕的比例
            SIZE = a[len("--size="):]
    m = re.match(r"(\d+)x(\d+)", SIZE)
    vw, vh = (int(m.group(1)), int(m.group(2))) if m else (1080, 2340)
    if os.getuid() != 0:
        relaunch_as_root()
    if "--direct-check" in sys.argv:
        return direct_check(vw, vh, parse_dpi(SIZE))
    if serve and "--scrcpy" not in sys.argv:
        r = start_direct(vw, vh, parse_dpi(SIZE))
        if r:
            srv, new_id, new_sf = r
            print("新螢幕（直接顯示）: 系統編號=%s 截圖編號=%s" % (new_id, new_sf))
            tmp = OUT + ".tmp"
            with open(tmp, "w") as f:
                f.write(json.dumps({"id": int(new_id), "sf": new_sf, "w": vw, "h": vh, "direct": True, "sf_before": SF_BEFORE}))
            os.chmod(tmp, 0o644)
            os.replace(tmp, OUT)
            print("已就緒，等待停止信號（%s）、逾時 %d 秒或主機結束" % (STOP, TTL))
            t0 = time.time()
            while time.time() - t0 < TTL and srv.poll() is None and not os.path.exists(STOP):
                time.sleep(0.5)
            stop_direct(srv)
            for f in (OUT, STOP):
                try:
                    os.remove(f)
                except OSError:
                    pass
            print("已關閉")
            return 0
    if not os.path.exists(JAR_SRC):
        print("找不到 %s，請先下載 scrcpy-server" % JAR_SRC)
        return 1
    sh("cp %s %s && chmod 644 %s" % (JAR_SRC, JAR, JAR))
    before = snapshot()
    print("建立前的螢幕:", sorted(before[0]), "虛擬:", sorted(before[1]))

    # max_size=1024：只縮小「影片」（背景螢幕本身還是 SIZE，AI 截圖不受影響）。預覽窗只有螢幕寬的三成多，
    # 編 1080x1800 太浪費；縮小後編碼、傳送、解碼都快，延遲較低、幀數較穩。
    cmd = ("CLASSPATH=%s exec app_process / com.genymobile.scrcpy.Server %s scid=%s log_level=info tunnel_forward=true "
           "audio=false control=false cleanup=false send_device_meta=false send_stream_meta=false send_dummy_byte=false "
           "send_frame_meta=true new_display=%s vd_system_decorations=false "
           "video_codec=h264 video_bit_rate=8000000 max_fps=60 max_size=1024 video_codec_options=i-frame-interval=2 display_ime_policy=hide"
           % (JAR, VERSION, SCID, SIZE))
    argv = ["su", "2000", "-c", cmd] if use_shell else ["/system/bin/sh", "-c", cmd]
    print("啟動伺服器（%s 身分）…" % ("shell" if use_shell else "root"))
    srv = subprocess.Popen(argv, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, env=CLEAN_ENV)

    def pump():
        for line in srv.stdout:
            print("[伺服器]", line.rstrip())
    threading.Thread(target=pump, daemon=True).start()

    sock = None
    for _ in range(60):  # 伺服器起來要一點時間
        if srv.poll() is not None:
            break
        try:
            s = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
            s.connect("\0scrcpy_" + SCID)
            sock = s
            break
        except OSError:
            s.close()
            time.sleep(0.25)
    if sock is None:
        print("沒能連上伺服器（它可能已經結束，看上面 [伺服器] 的訊息）。結束碼:", srv.poll())
        stop_server(srv)
        return 2
    print("已連上伺服器的串流，開始丟棄影像資料")

    proxy = StreamProxy()
    broadcast = close_stream = None
    if serve:
        try:
            broadcast, close_stream = start_stream_server(proxy)
        except OSError as e:
            print("預覽串流伺服器沒開成（預覽會退回截圖模式）:", e)

    fps_samples = []  # 每 3 秒一筆（只收有畫面的）：背景螢幕畫面在動時，scrcpy 實際送出的幀率

    frames = FrameSplitter()

    def drain():
        t0, f0, b0 = time.time(), 0, 0
        try:
            while True:
                data = sock.recv(65536)
                if not data:
                    break
                for pkt in frames.feed(data):
                    nals = proxy.feed(pkt + AUD)  # 每張畫面收完就整張送出（AUD 讓最後一個 NAL 也算完整）
                    if broadcast:
                        broadcast(nals)
                dt = time.time() - t0
                if dt >= 3.0:
                    df = proxy.frames - f0
                    if df > 0:
                        fps = df / dt
                        fps_samples.append(fps)
                        print("[串流] 近 %.1f 秒 %d 個畫面 ≈ %.1f fps，%.2f Mbps" % (dt, df, fps, (proxy.nbytes - b0) * 8 / dt / 1e6))
                    t0, f0, b0 = time.time(), proxy.frames, proxy.nbytes
        except OSError:
            pass
    threading.Thread(target=drain, daemon=True).start()

    new_id = new_sf = None
    for _ in range(40):
        ids, sf = snapshot()
        if ids - before[0] and sf - before[1]:
            new_id, new_sf = sorted(ids - before[0])[0], sorted(sf - before[1])[0]
            break
        time.sleep(0.25)
    print("新螢幕: 系統編號=%s 截圖編號=%s" % (new_id, new_sf))
    if new_id and new_sf and measure:
        print("在新螢幕開 Chrome 播放動畫測試頁（testufo.com，會連到外部網站）…")
        print(sh("am start --display %s -a android.intent.action.VIEW -d https://www.testufo.com/ -p com.android.chrome" % new_id).strip()[-120:])
        time.sleep(measure)
        stop_server(srv, sock)
        if close_stream:
            close_stream()
        if fps_samples:
            ordered = sorted(fps_samples)
            print("量測結果：%d 筆樣本，中位數 ≈ %.1f fps，最高 ≈ %.1f fps（scrcpy 設定 max_fps=60）" % (
                len(ordered), ordered[len(ordered) // 2], ordered[-1]))
        else:
            print("量測結果：沒有收到任何畫面（頁面沒開起來，或串流沒資料）")
        return 0
    if new_id and new_sf and not serve:
        if app and re.fullmatch(r"[A-Za-z0-9_.]+", app):
            comp = None
            for line in reversed(sh("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER " + app).strip().splitlines()):
                if re.fullmatch(re.escape(app) + r"/[\w.$]+", line.strip()):
                    comp = line.strip()
                    break
            print("啟動入口:", comp)
            target = ("-n " + comp) if comp else ("-a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p " + app)
            cmd_start = "am start --display %s %s" % (new_id, target)
            print("在新螢幕開 %s（完整輸出）:" % app)
            print(sh(cmd_start).strip())
            print("--- 相關系統紀錄（最後幾行）:")
            print(sh("logcat -d | grep -iE 'ActivityTaskManager|ActivityStarter|%s' | tail -n 12" % app).strip())
        else:
            print("在新螢幕開「設定」:", sh("am start --display %s -n com.android.settings/.Settings" % new_id).strip()[-120:])
        time.sleep(2.5)
        print("截圖:", sh("screencap -d %s -p %s; ls -l %s" % (new_sf, SHOT, SHOT)).strip())
        stacks = [l.strip() for l in sh("am stack list").splitlines() if ("displayId=%s" % new_id) in l or "settings" in l]
        print("新螢幕上的任務:", stacks[:4])
    if serve:
        if not (new_id and new_sf):
            print("建立失敗，沒有新螢幕")
            stop_server(srv, sock)
            return 3
        tmp = OUT + ".tmp"
        with open(tmp, "w") as f:
            f.write('{"id": %s, "sf": "%s", "w": %d, "h": %d}' % (new_id, new_sf, vw, vh))
        os.chmod(tmp, 0o644)
        os.replace(tmp, OUT)
        print("已就緒，等待停止信號（%s）、逾時 %d 秒或伺服器結束" % (STOP, TTL))
        t0 = time.time()
        while time.time() - t0 < TTL and srv.poll() is None and not os.path.exists(STOP):
            time.sleep(0.5)
        stop_server(srv, sock)
        if close_stream:
            close_stream()
        for f in (OUT, STOP):
            try:
                os.remove(f)
            except OSError:
                pass
        print("已關閉")
        return 0
    left = max(0, secs - 4)
    print("保持 %d 秒（這段時間新螢幕還在）…" % left)
    time.sleep(left)
    stop_server(srv, sock)
    time.sleep(1.5)
    after = snapshot()
    print("關閉伺服器後的螢幕:", sorted(after[0]), "虛擬:", sorted(after[1]), "（新螢幕應該消失）")
    print("完成。截圖在 %s（內含畫面內容，用完請刪：su -c 'rm %s'）" % (SHOT, SHOT))
    return 0


if __name__ == "__main__":
    sys.exit(main())
