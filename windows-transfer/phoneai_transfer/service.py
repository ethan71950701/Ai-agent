"""電腦版的核心：伺服器（給手機取檔、收檔）、配對、傳檔案。GUI 只呼叫這裡，事件用 queue 交給 GUI 執行緒。"""
import os
import queue
import threading
import time

from . import net
from .store import Store


class Service:
    def __init__(self, store=None):
        self.store = store or Store()
        self.events = queue.Queue()     # (種類, 資料) → GUI 每 200 毫秒讀一次
        self.server = net.Server(self)
        self.responder = None
        self.pair_until = 0.0           # 配對視窗開到什麼時候（只有這段時間才接受配對）
        self.pending = None             # 手機來配對時等使用者核對確認碼：dict
        self.running = False
        self.last_error = ""
        self._stop = threading.Event()   # 使用者按「停止」：進行中的傳輸在下一次進度更新時中斷

    # ---- 停止 ----
    def request_stop(self):
        self._stop.set()

    def _check(self):
        if self._stop.is_set():
            raise Stopped()

    def _tick(self, title, name, done, total):
        self._check()
        self.events.put(("progress", (title, name, done, total)))

    # ---- 開關 ----
    def start(self):
        if self.running:
            return
        try:
            self.server.start(net.PORT)
            self.responder = net.Responder(self.store.my_id, self.store.my_name)
            self.responder.start(net.DISCOVERY_PORT)
            self.running = True
            self.last_error = ""
        except OSError as e:
            self.last_error = str(e)
            self.events.put(("error", "互傳伺服器啟動失敗：" + str(e)))

    def stop(self):
        self.server.stop()
        if self.responder:
            self.responder.stop()
        self.running = False

    def open_pairing(self, seconds=120):
        self.pair_until = time.time() + seconds

    def pairing_open(self):
        """Host 介面：配對視窗開著才接受配對。"""
        return time.time() < self.pair_until

    # ---- Host 介面（伺服器會呼叫） ----
    def my_id(self):
        return self.store.my_id()

    def my_name(self):
        return self.store.my_name()

    def key_of(self, peer_id):
        return self.store.key_of(peer_id)

    def inbox(self):
        return self.store.inbox()

    def pair_request(self, peer_id, peer_name, key, sas):
        self.pending = {"id": peer_id, "name": peer_name, "key": key, "sas": sas, "state": "waiting"}
        self.events.put(("pair_request", dict(self.pending)))

    def pair_state(self, peer_id):
        p = self.pending
        return p["state"] if p and p["id"] == peer_id else "none"

    def peer_name(self, peer_id, name):
        self.store.update_peer_name(peer_id, name)

    def event(self, peer_id, kind, what, size):
        who = self._name(peer_id)
        if kind == "get":
            text = f"{who} 取走了：{what}（{_size(size)}）"
        elif kind == "put":
            text = f"{who} 傳來了：{what}（{_size(size)}）"
            self.events.put(("notice", text))
        elif kind == "list":
            text = f"{who} 查看了資料夾：{what}"
        else:
            text = f"{who} 搜尋了檔案：{what}"
        self.store.log(text)
        self.events.put(("log", text))

    def progress(self, peer_id, to_peer, name, done, total):
        who = self._name(peer_id)
        title = f"正在傳給 {who}" if to_peer else f"正在收 {who} 傳來的檔案"
        self._tick(title, name, done, total)

    def transfer_ended(self):
        self.events.put(("progress_end", None))

    def _name(self, peer_id):
        p = self.store.peer(peer_id)
        return p["name"] if p else peer_id

    # ---- 配對 ----
    def finish_pair(self, accept):
        """使用者核對確認碼後按相符／不相符。狀態會留著，讓手機的輪詢知道結果。"""
        p = self.pending
        if not p:
            return
        if accept:
            self.store.add_peer(p["id"], p["name"], p["key"])
            self.store.log(f"已配對 {p['name']}")
        p["state"] = "accepted" if accept else "rejected"

    def pair_outgoing(self, ip, port):
        """發起配對：回傳 Offer；GUI 讓使用者核對確認碼，相符才呼叫 confirm_outgoing。"""
        return net.pair_start(ip, port, self.my_id(), self.my_name())

    def confirm_outgoing(self, offer, ip, port):
        """使用者說相符：等對方也按相符（最多 2 分鐘），對方接受才存金鑰。回傳是否配對成功。"""
        end = time.time() + 120
        state = "waiting"
        while time.time() < end and state == "waiting":
            time.sleep(1)
            try:
                state = net.pair_status(ip, port, self.my_id())
            except OSError:
                state = "none"
        if state == "accepted":
            self.store.add_peer(offer.peer_id, offer.peer_name, offer.key)
            self.store.log(f"已配對 {offer.peer_name}")
            return True
        return False

    # ---- 找裝置與傳檔（客戶端） ----
    def scan(self):
        found = net.scan("*", timeout=2.5)
        if not found:
            found = net.sweep()
        found.pop(self.my_id(), None)
        return found

    def search_local(self, query):
        """手動搜尋：本機（整台電腦），不用 AI。回傳 (類型, 大小, 路徑) 列表。"""
        return net.parse_listing(net._find_everywhere(query))

    def search_peer(self, peer_id, query):
        """手動搜尋：已配對的裝置（手機）。"""
        return net.parse_listing(self.client_for(peer_id).find(query))

    def client_for(self, peer_id):
        p = self.store.peer(peer_id)
        if not p:
            raise IOError("這台裝置還沒配對")
        f = net.scan(peer_id, timeout=2.5).get(peer_id)
        if not f:
            raise IOError(f"找不到「{p['name']}」：可能沒開機、沒開互傳，或不在同一個網路")
        self.store.update_peer_name(peer_id, f.name)
        return net.Client(f.ip, f.port, self.my_id(), self.my_name(), crypto_key(p))

    def send_file(self, peer_id, path):
        self._stop.clear()
        c = self.client_for(peer_id)
        name = os.path.basename(path)
        title = f"正在傳給 {self._name(peer_id)}"
        try:
            saved = c.put(path, lambda d, t: self._tick(title, name, d, t))
        finally:
            self.events.put(("progress_end", None))
        self.store.log(f"傳給 {self._name(peer_id)}：{name}")
        self.events.put(("notice", f"已傳到 {self._name(peer_id)}：{saved}"))
        return saved

    def fetch_file(self, peer_id, remote_path, dest_dir):
        self._stop.clear()
        c = self.client_for(peer_id)
        name = os.path.basename(remote_path.replace("\\", "/"))
        os.makedirs(dest_dir, exist_ok=True)
        dest = _unique(dest_dir, name)
        title = f"正在從 {self._name(peer_id)} 取回"
        try:
            n = c.download(remote_path, dest, lambda d, t: self._tick(title, name, d, t))
        finally:
            self.events.put(("progress_end", None))
        self.store.log(f"取回 {os.path.basename(dest)}（{_size(n)}）")
        self.events.put(("notice", f"已存到 {dest}"))
        return dest


class Stopped(IOError):
    """使用者按了停止（未完成的檔案已由下載或收檔的程式刪掉）。"""


def crypto_key(peer):
    from .crypto import unhex
    return unhex(peer["key"])


def _unique(folder, name):
    dest = os.path.join(folder, name)
    stem, ext = os.path.splitext(name)
    i = 2
    while os.path.exists(dest):
        dest = os.path.join(folder, f"{stem} ({i}){ext}")
        i += 1
    return dest


def _size(n):
    for unit, div in (("GB", 1 << 30), ("MB", 1 << 20), ("KB", 1 << 10)):
        if n >= div:
            return f"{n / div:.1f} {unit}"
    return f"{n} B"
