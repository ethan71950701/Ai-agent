"""網路層：客戶端（取檔、傳檔、配對）、伺服器（給手機取檔、收手機傳來的檔）、區網探索。
協定與手機版 TransferClient／TransferServer／TransferDiscovery 相同，見 docs/FILE_TRANSFER.md。"""
import ipaddress
import os
import socket
import threading
import time
from urllib.parse import parse_qsl, quote_plus, unquote_plus

from . import crypto

PORT = 47321          # 內容伺服器（TCP）
DISCOVERY_PORT = 47322  # 找裝置（UDP）
SIG_WINDOW = 60       # 簽章的時間窗（秒）
MAX_FAILS = 10


def _enc(s):
    return quote_plus(s, safe="")


def _local_ips():
    ips = []
    try:
        for info in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET):
            ip = info[4][0]
            if ip not in ips and not ip.startswith("127."):
                ips.append(ip)
    except OSError:
        pass
    return ips


def _allowed(ip):
    """只服務區網（跟手機版一樣）。"""
    a = ipaddress.ip_address(ip)
    return a.is_private or a.is_loopback or a.is_link_local


class HttpError(IOError):
    pass


def _read_line(f):
    line = f.readline(8193)
    if not line:
        return None
    return line.rstrip(b"\r\n").decode("latin-1")


def _read_headers(f):
    h = {}
    while True:
        line = _read_line(f)
        if line is None or line == "":
            break
        i = line.find(":")
        if i > 0:
            h[line[:i].strip().lower()] = line[i + 1:].strip()
    return h


class Client:
    """對一台已配對裝置發請求。同步呼叫，要在背景執行緒用。"""

    def __init__(self, ip, port, my_id, my_name, key):
        self.ip, self.port, self.my_id, self.my_name, self.key = ip, port, my_id, my_name, key

    def _open(self):
        s = socket.create_connection((self.ip, self.port), timeout=4)
        s.settimeout(20)
        return s

    def _head(self, method, target):
        ts = str(int(time.time()))
        nonce = crypto.hexs(crypto.random_bytes(12))
        sig = crypto.sign(self.key, method, target, ts, nonce)
        return nonce, (f"{method} {target} HTTP/1.1\r\nHost: phoneai\r\nX-Id: {self.my_id}\r\nX-Ts: {ts}\r\nX-Nonce: {nonce}"
                       f"\r\nX-Name: {_enc(self.my_name)}\r\nX-Sig: {sig}\r\nConnection: close\r\n\r\n").encode("latin-1")

    @staticmethod
    def _status(f):
        line = _read_line(f)
        if line is None:
            raise HttpError("對方沒有回應")
        parts = line.split(" ")
        code = int(parts[1]) if len(parts) > 1 else 0
        h = _read_headers(f)
        if code != 200:
            n = min(int(h.get("content-length", "0") or 0), 1024)
            body = f.read(n) or b""
            raise HttpError(f"{code} {body.decode('utf-8', 'replace')}")
        return h

    def _get_text(self, target):
        s = self._open()
        try:
            nonce, head = self._head("GET", target)
            s.sendall(head)
            f = s.makefile("rb")
            self._status(f)
            return crypto.SealedReader(f, crypto.enc_key(self.key, nonce)).read().decode("utf-8")
        finally:
            s.close()

    def ping(self):
        t = self._get_text("/v1/ping")
        return t[3:] if t.startswith("ok\t") else t

    def list(self, path):
        return self._get_text("/v1/list?path=" + _enc(path))

    def find(self, name):
        return self._get_text("/v1/find?name=" + _enc(name))

    def download(self, remote_path, dest, progress=None):
        """從對方取檔案到 dest（先寫 .part，完整收到才改名）。失敗或停止都會刪掉未完成的檔案。"""
        s = self._open()
        tmp = dest + ".part"
        try:
            nonce, head = self._head("GET", "/v1/file?path=" + _enc(remote_path))
            s.sendall(head)
            f = s.makefile("rb")
            h = self._status(f)
            total = int(h.get("x-size", "0") or 0)
            done = 0
            reader = crypto.SealedReader(f, crypto.enc_key(self.key, nonce))
            with open(tmp, "wb") as out:
                while True:
                    chunk = reader.read(32 * 1024)
                    if not chunk:
                        break
                    out.write(chunk)
                    done += len(chunk)
                    if progress:
                        progress(done, total)
            os.replace(tmp, dest)
            return done
        except BaseException:
            try:
                os.remove(tmp)
            except OSError:
                pass
            raise
        finally:
            s.close()

    def put(self, src, progress=None):
        """把本機檔案送到對方的收件匣；回傳對方存成的檔名。"""
        size = os.path.getsize(src)
        target = f"/v1/put?name={_enc(os.path.basename(src))}&size={size}"
        s = self._open()
        try:
            nonce, head = self._head("PUT", target)
            s.sendall(head)
            wf = s.makefile("wb")
            sealed = crypto.SealedWriter(wf, crypto.enc_key(self.key, nonce))
            done = 0
            with open(src, "rb") as fin:
                while True:
                    chunk = fin.read(32 * 1024)
                    if not chunk:
                        break
                    sealed.write(chunk)
                    done += len(chunk)
                    if progress:
                        progress(done, size)
            sealed.close()
            wf.flush()
            rf = s.makefile("rb")
            self._status(rf)
            body = crypto.SealedReader(rf, crypto.enc_key(self.key, nonce)).read().decode("utf-8")
            parts = body.split("\t")
            return parts[1] if len(parts) > 1 else os.path.basename(src)
        finally:
            s.close()


def _plain(ip, port, method, target, body=None):
    s = socket.create_connection((ip, port), timeout=4)
    try:
        s.settimeout(15)
        data = body or b""
        s.sendall(f"{method} {target} HTTP/1.1\r\nHost: phoneai\r\nContent-Length: {len(data)}\r\nConnection: close\r\n\r\n".encode("latin-1") + data)
        f = s.makefile("rb")
        line = _read_line(f)
        if line is None:
            raise HttpError("對方沒有回應")
        code = int(line.split(" ")[1])
        _read_headers(f)
        return code, f.read().decode("utf-8")
    finally:
        s.close()


class Offer:
    def __init__(self, peer_id, peer_name, key, sas_code):
        self.peer_id, self.peer_name, self.key, self.sas = peer_id, peer_name, key, sas_code


def pair_start(ip, port, my_id, my_name):
    """發起配對：送出自己的暫時公開金鑰，算出確認碼與長期金鑰（金鑰要等使用者核對確認碼相符才存）。"""
    pair = crypto.Pair()
    body = f"id={my_id}\nname={my_name.replace(chr(10), ' ')}\npub={crypto.hexs(pair.public)}".encode("utf-8")
    code, text = _plain(ip, port, "POST", "/v1/pair/start", body)
    if code != 200:
        raise HttpError(f"{code} {text}")
    m = dict(l.split("=", 1) for l in text.split("\n") if "=" in l)
    their_pub = crypto.unhex(m["pub"])
    shared = pair.shared(their_pub)
    tr = crypto.transcript(my_id, pair.public, m["id"], their_pub)
    return Offer(m["id"], m.get("name", m["id"]), crypto.pair_key(shared, tr), crypto.sas(shared, tr))


def pair_status(ip, port, my_id):
    code, text = _plain(ip, port, "GET", "/v1/pair/status?id=" + _enc(my_id))
    return text.strip() if code == 200 else "none"


# ---------- 伺服器 ----------

class Server:
    """給手機（或其他裝置）取檔案、收檔案。host 介面見 app.py 的 Host 實作。"""

    def __init__(self, host):
        self.host = host
        self._sock = None
        self._seen = {}
        self._fails = {}
        self._lock = threading.Lock()
        self.running = False

    def start(self, port=PORT):
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        s.bind(("", port))
        s.listen(16)
        self._sock = s
        self.running = True
        threading.Thread(target=self._loop, daemon=True).start()

    def stop(self):
        self.running = False
        if self._sock:
            try:
                self._sock.close()
            except OSError:
                pass
            self._sock = None

    def _loop(self):
        while self.running:
            try:
                conn, addr = self._sock.accept()
            except OSError:
                return
            threading.Thread(target=self._serve, args=(conn, addr[0]), daemon=True).start()

    def _serve(self, conn, ip):
        try:
            with conn:
                if not _allowed(ip):
                    return
                conn.settimeout(20)
                f = conn.makefile("rb")
                if self._blocked(ip):
                    self._reply(conn, 429, "too many failures")
                    return
                line = _read_line(f)
                if not line:
                    return
                parts = line.split(" ")
                if len(parts) < 2:
                    return
                method, target = parts[0], parts[1]
                h = _read_headers(f)
                path, _, query = target.partition("?")
                params = dict(parse_qsl(query, keep_blank_values=True))
                if path.startswith("/v1/pair/"):
                    self._pair(conn, f, path, params, h)
                    return
                peer = self._authenticate(method, target, h, ip)
                if peer is None:
                    self._fail(ip)
                    self._reply(conn, 401, "unauthorized")
                    return
                name = h.get("x-name")
                if name:
                    name = unquote_plus(name).strip()
                    if 0 < len(name) <= 40:
                        self.host.peer_name(peer[0], name)
                self._route(conn, f, method, path, params, peer)
        except Exception:
            pass  # 單一連線出錯不影響伺服器

    def _route(self, conn, f, method, path, params, peer):
        peer_id, key, nonce = peer
        if path == "/v1/ping":
            self._send_text(conn, key, nonce, "ok\t" + self.host.my_name())
        elif path == "/v1/list":
            d = params.get("path", "")
            self.host.event(peer_id, "list", d, 0)
            self._send_text(conn, key, nonce, _listing(d))
        elif path == "/v1/find":
            q = params.get("name", "")
            self.host.event(peer_id, "find", q, 0)
            self._send_text(conn, key, nonce, _find_everywhere(q))
        elif path == "/v1/file":
            p = _resolve(params.get("path", ""))
            if not os.path.isfile(p):
                self._reply(conn, 404, "not a file")
                return
            try:
                self._send_file(conn, key, nonce, peer_id, p)
            finally:
                self.host.transfer_ended()
        elif path == "/v1/put":
            try:
                self._receive(conn, f, key, nonce, peer_id, params.get("name", ""), params.get("size", "0"))
            finally:
                self.host.transfer_ended()
        else:
            self._reply(conn, 404, "unknown")

    def _send_text(self, conn, key, nonce, text):
        conn.sendall(b"HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nConnection: close\r\n\r\n")
        w = conn.makefile("wb")
        sealed = crypto.SealedWriter(w, crypto.enc_key(key, nonce))
        sealed.write(text.encode("utf-8"))
        sealed.close()
        w.flush()

    def _send_file(self, conn, key, nonce, peer_id, path):
        size = os.path.getsize(path)
        conn.sendall(f"HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nX-Size: {size}\r\nConnection: close\r\n\r\n".encode("latin-1"))
        w = conn.makefile("wb")
        sealed = crypto.SealedWriter(w, crypto.enc_key(key, nonce))
        done = 0
        with open(path, "rb") as fin:
            while True:
                chunk = fin.read(32 * 1024)
                if not chunk:
                    break
                sealed.write(chunk)
                done += len(chunk)
                self.host.progress(peer_id, True, os.path.basename(path), done, size)
        sealed.close()
        w.flush()
        self.host.event(peer_id, "get", path, done)

    def _receive(self, conn, f, key, nonce, peer_id, name, size):
        base = os.path.basename(name.replace("\\", "/"))
        if base in ("", ".", ".."):
            self._reply(conn, 400, "bad name")
            return
        inbox = self.host.inbox()
        os.makedirs(inbox, exist_ok=True)
        dest = _unique(inbox, base)
        tmp = os.path.join(inbox, "." + os.path.basename(dest) + ".part")
        try:
            expect = int(size or 0)
        except ValueError:
            expect = 0
        total = 0
        try:
            reader = crypto.SealedReader(f, crypto.enc_key(key, nonce))
            with open(tmp, "wb") as out:
                while True:
                    chunk = reader.read(32 * 1024)
                    if not chunk:
                        break
                    out.write(chunk)
                    total += len(chunk)
                    self.host.progress(peer_id, False, base, total, expect)
            os.replace(tmp, dest)
        except BaseException:
            try:
                os.remove(tmp)
            except OSError:
                pass
            raise
        self.host.event(peer_id, "put", dest, total)
        self._send_text(conn, key, nonce, f"saved\t{os.path.basename(dest)}\t{total}")

    def _reply(self, conn, code, msg):
        b = msg.encode("utf-8")
        conn.sendall(f"HTTP/1.1 {code} X\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: {len(b)}\r\nConnection: close\r\n\r\n".encode("latin-1") + b)

    def _pair(self, conn, f, path, params, h):
        if not self.host.pairing_open():
            self._reply(conn, 403, "pairing closed")
            return
        if path == "/v1/pair/hello":
            self._reply(conn, 200, f"PHONEAI\t{self.host.my_id()}\t{self.host.my_name().replace(chr(9), ' ')}\t{PORT}")
            return
        if path == "/v1/pair/status":
            self._reply(conn, 200, self.host.pair_state(params.get("id", "")))
            return
        if path != "/v1/pair/start":
            self._reply(conn, 404, "unknown")
            return
        try:
            n = int(h.get("content-length", "0"))
        except ValueError:
            n = 0
        if n <= 0 or n > 2048:
            self._reply(conn, 400, "bad body")
            return
        body = f.read(n)
        m = dict(l.split("=", 1) for l in body.decode("utf-8", "replace").split("\n") if "=" in l)
        pid, pname, pub = m.get("id", ""), m.get("name", ""), m.get("pub", "")
        if not pid or len(pid) > 64 or not pub:
            self._reply(conn, 400, "bad body")
            return
        their_pub = crypto.unhex(pub)
        pair = crypto.Pair()
        shared = pair.shared(their_pub)
        tr = crypto.transcript(pid, their_pub, self.host.my_id(), pair.public)
        self.host.pair_request(pid, pname or pid, crypto.pair_key(shared, tr), crypto.sas(shared, tr))
        self._reply(conn, 200, f"id={self.host.my_id()}\nname={self.host.my_name()}\npub={crypto.hexs(pair.public)}")

    def _authenticate(self, method, target, h, ip):
        peer_id, ts, nonce, sig = h.get("x-id"), h.get("x-ts"), h.get("x-nonce"), h.get("x-sig")
        if not (peer_id and ts and nonce and sig) or len(nonce) > 64 or len(peer_id) > 64:
            return None
        key = self.host.key_of(peer_id)
        if key is None:
            return None
        try:
            t = int(ts)
        except ValueError:
            return None
        if abs(time.time() - t) > SIG_WINDOW:
            return None
        if not crypto.same_hex(crypto.sign(key, method, target, ts, nonce), sig):
            return None
        with self._lock:
            now = time.time()
            self._seen = {k: v for k, v in self._seen.items() if now - v < 600}
            if peer_id + nonce in self._seen:
                return None   # 同一個請求被重送
            self._seen[peer_id + nonce] = now
        return peer_id, key, nonce

    def _blocked(self, ip):
        with self._lock:
            f = self._fails.get(ip)
            if not f:
                return False
            if time.time() - f[1] > 300:
                self._fails.pop(ip, None)
                return False
            return f[0] >= MAX_FAILS

    def _fail(self, ip):
        with self._lock:
            now = time.time()
            f = self._fails.get(ip)
            if not f or now - f[1] > 300:
                self._fails[ip] = [1, now]
            else:
                f[0] += 1


def _unique(folder, name):
    dest = os.path.join(folder, name)
    if not os.path.exists(dest):
        return dest
    stem, ext = os.path.splitext(name)
    for i in range(1, 1000):
        dest = os.path.join(folder, f"{stem} ({i}){ext}")
        if not os.path.exists(dest):
            return dest
    return os.path.join(folder, f"{stem}-{int(time.time())}{ext}")


def _resolve(path):
    """電腦版：路徑可以是任何資料夾（整台電腦都開放）。空字串列出磁碟；相對路徑從使用者資料夾算起。"""
    p = path.replace("\\", "/")
    if p == "":
        return ""
    if not os.path.isabs(p):
        p = os.path.join(os.path.expanduser("~"), p)
    return os.path.normpath(p)


def _entry(path):
    try:
        st = os.stat(path)
    except OSError:
        return None
    is_dir = os.path.isdir(path)
    size = 0 if is_dir else st.st_size
    return f"{'D' if is_dir else 'F'}\t{size}\t{int(st.st_mtime * 1000)}\t{path.replace(os.sep, '/')}\n"


def drives():
    if os.name == "nt":
        import string
        return [f"{d}:/" for d in string.ascii_uppercase if os.path.exists(f"{d}:\\")]
    return ["/"]


def _listing(path):
    if path == "":
        return "".join(e for e in (_entry(d) for d in drives()) if e)
    try:
        kids = [os.path.join(path, k) for k in os.listdir(path)]
    except OSError:
        return ""
    kids.sort(key=lambda p: (not os.path.isdir(p), os.path.basename(p).lower()))
    return "".join(e for e in (_entry(k) for k in kids[:500]) if e)


SKIP_DIRS = {"windows", "$recycle.bin", "system volume information", "programdata", "program files",
             "program files (x86)", "recovery", "perflogs"}


def _find_everywhere(query, limit_s=12.0, max_visit=400000):
    """找整台電腦的檔案：所有磁碟一層一層（廣度優先）輪流找，這樣每個磁碟都有機會被找到。
    跳過 Windows 系統資料夾；整體最多 12 秒（手機那邊最多等 20 秒），回傳最新的 30 個。"""
    q = query.lower().strip()
    if not q:
        return ""
    home = os.path.expanduser("~")
    deadline = time.time() + limit_s
    downloads = os.path.join(home, "Downloads")
    # 第一步：先找「下載」（大部分檔案都在這裡）；找到就直接回傳
    hits = _search([(downloads, 0)], q, deadline, max_visit)
    if not hits:
        # 第二步：其他地方，所有磁碟輪流找
        others = [home] + [d for d in drives() if os.path.normcase(d[:2]) != os.path.normcase(home[:2])]
        hits = _search([(d, 0) for d in others], q, deadline, max_visit)
    hits.sort(key=lambda p: os.path.getmtime(p) if os.path.exists(p) else 0, reverse=True)
    return "".join(e for e in (_entry(p) for p in hits[:30]) if e)


def _search(queue, q, deadline, max_visit):
    """廣度優先搜尋，回傳符合的完整路徑。"""
    visited = 0
    hits = []
    i = 0
    while i < len(queue) and visited < max_visit and time.time() < deadline and len(hits) < 200:
        d, depth = queue[i]
        i += 1
        try:
            names = os.listdir(d)
        except OSError:
            continue
        for n in names:
            visited += 1
            if n.startswith("."):
                continue
            p = os.path.join(d, n)
            if q in n.lower():
                hits.append(p)
            if depth < 12 and os.path.isdir(p) and not os.path.islink(p):
                if depth == 0 and n.lower() in SKIP_DIRS:
                    continue
                queue.append((p, depth + 1))
    return hits


# ---------- 區網探索 ----------

class Found:
    def __init__(self, peer_id, name, ip, port):
        self.id, self.name, self.ip, self.port = peer_id, name, ip, port


def _broadcast_targets():
    targets = {"255.255.255.255"}
    for ip in _local_ips():
        parts = ip.split(".")
        targets.add(".".join(parts[:3] + ["255"]))
    return targets


def scan(target_id, timeout=2.0, extra=()):
    """廣播找裝置；target_id 是 '*' 就收集全部回應，否則找到那台就提早結束。"""
    found = {}
    q = f"PHONEAI?|{target_id}".encode("utf-8")
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
        s.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
        s.settimeout(0.3)
        for t in list(_broadcast_targets()) + list(extra):
            try:
                s.sendto(q, (t, DISCOVERY_PORT))
            except OSError:
                pass
        end = time.time() + timeout
        while time.time() < end:
            try:
                data, addr = s.recvfrom(512)
            except socket.timeout:
                continue
            except OSError:
                break
            parts = data.decode("utf-8", "replace").split("|", 3)
            if len(parts) < 4 or parts[0] != "PHONEAI!":
                continue
            try:
                port = int(parts[3].strip())
            except ValueError:
                continue
            found[parts[1]] = Found(parts[1], parts[2], addr[0], port)
            if target_id != "*":
                break
    return found


def sweep(port=PORT):
    """廣播收不到時的備案：逐一試同網段位址的 hello（只有配對視窗開著的裝置會回應）。"""
    import concurrent.futures
    targets = []
    for ip in _local_ips():
        pre = ip.rsplit(".", 1)[0]
        targets += [f"{pre}.{i}" for i in range(1, 255) if f"{pre}.{i}" != ip]

    def hello(ip):
        try:
            code, text = _plain(ip, port, "GET", "/v1/pair/hello")
        except OSError:
            return None
        parts = text.strip().split("\t")
        if code != 200 or len(parts) < 4 or parts[0] != "PHONEAI":
            return None
        return Found(parts[1], parts[2], ip, int(parts[3]))

    out = {}
    with concurrent.futures.ThreadPoolExecutor(max_workers=48) as ex:
        for f in ex.map(hello, targets):
            if f:
                out[f.id] = f
    return out


class Responder:
    """UDP 回應：別人廣播找我時，回我的 id、名稱與 TCP 埠。"""

    def __init__(self, get_id, get_name):
        self.get_id, self.get_name = get_id, get_name
        self._sock = None

    def start(self, port=DISCOVERY_PORT):
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        s.bind(("", port))
        self._sock = s
        threading.Thread(target=self._loop, daemon=True).start()

    def stop(self):
        if self._sock:
            try:
                self._sock.close()
            except OSError:
                pass
            self._sock = None

    def _loop(self):
        while self._sock:
            try:
                data, addr = self._sock.recvfrom(512)
            except OSError:
                return
            msg = data.decode("utf-8", "replace")
            if not msg.startswith("PHONEAI?|"):
                continue
            target = msg[9:].strip()
            if target not in ("*", self.get_id()):
                continue
            reply = f"PHONEAI!|{self.get_id()}|{self.get_name().replace('|', ' ')}|{PORT}".encode("utf-8")
            try:
                self._sock.sendto(reply, addr)
            except OSError:
                pass
