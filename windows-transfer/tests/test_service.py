"""電腦版核心流程（不需要 tkinter）：兩台 Python 服務之間配對、傳檔、停止。"""
import os
import shutil
import socket
import sys
import tempfile
import threading
import time
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from phoneai_transfer import net  # noqa: E402
from phoneai_transfer.service import Service, Stopped  # noqa: E402
from phoneai_transfer.store import Store  # noqa: E402


def free_port():
    s = socket.socket()
    s.bind(("127.0.0.1", 0))
    p = s.getsockname()[1]
    s.close()
    return p


class Pc:
    """一台模擬電腦：自己的設定資料夾、自己的伺服器（埠隨機，不用 47321）。"""

    def __init__(self, folder, name):
        self.store = Store(folder, inbox=os.path.join(folder, "inbox"))
        self.store.set_name(name)
        self.svc = Service(self.store)
        self.port = free_port()
        self.svc.server.start(self.port)
        self.svc.open_pairing(60)

    def close(self):
        self.svc.server.stop()


class ServiceFlow(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.a = Pc(os.path.join(self.tmp, "a"), "電腦A")
        self.b = Pc(os.path.join(self.tmp, "b"), "電腦B")
        self.share = os.path.join(self.tmp, "share")
        os.makedirs(self.share)
        self.payload = os.path.join(self.share, "data.bin")
        with open(self.payload, "wb") as f:
            f.write(os.urandom(300 * 1024))

    def tearDown(self):
        self.a.close()
        self.b.close()
        shutil.rmtree(self.tmp, ignore_errors=True)

    def _pair(self):
        """B 發起配對，A 的使用者按相符：兩邊都要存下對方。"""
        offer = net.pair_start("127.0.0.1", self.a.port, self.b.store.my_id(), self.b.store.my_name())
        self.assertEqual(6, len(offer.sas))
        # A 收到配對請求（模擬使用者按相符）
        for _ in range(50):
            if self.a.svc.pending:
                break
            time.sleep(0.05)
        self.assertIsNotNone(self.a.svc.pending)
        self.assertEqual(offer.sas, self.a.svc.pending["sas"])   # 兩邊確認碼一樣
        self.a.svc.finish_pair(True)
        ok = self.b.svc.confirm_outgoing(offer, "127.0.0.1", self.a.port)
        self.assertTrue(ok)
        self.assertEqual("電腦A", self.b.store.peer(self.a.store.my_id())["name"])
        self.assertEqual("電腦B", self.a.store.peer(self.b.store.my_id())["name"])

    def test_pair_then_transfer_both_ways(self):
        self._pair()
        c = net.Client("127.0.0.1", self.a.port, self.b.store.my_id(), self.b.store.my_name(),
                       self.b.store.key_of(self.a.store.my_id()))
        self.assertEqual("電腦A", c.ping())
        dest = os.path.join(self.tmp, "got.bin")
        self.assertEqual(os.path.getsize(self.payload), c.download(self.payload, dest))
        with open(dest, "rb") as x, open(self.payload, "rb") as y:
            self.assertEqual(y.read(), x.read())
        # 反向：B 傳給 A，A 的收件匣要有檔案
        saved = c.put(self.payload)
        self.assertEqual("data.bin", saved)
        self.assertTrue(os.path.exists(os.path.join(self.a.store.inbox(), "data.bin")))

    def test_unpaired_device_is_refused(self):
        c = net.Client("127.0.0.1", self.a.port, "stranger", "X", os.urandom(32))
        with self.assertRaises(IOError):
            c.ping()

    def test_stop_deletes_partial_download(self):
        self._pair()
        c = net.Client("127.0.0.1", self.a.port, self.b.store.my_id(), self.b.store.my_name(),
                       self.b.store.key_of(self.a.store.my_id()))
        dest = os.path.join(self.tmp, "stopped.bin")
        calls = []

        def progress(done, total):
            calls.append(done)
            if len(calls) == 2:
                raise Stopped()          # 模擬使用者按了停止
        with self.assertRaises(Stopped):
            c.download(self.payload, dest, progress)
        self.assertFalse(os.path.exists(dest))
        self.assertFalse(os.path.exists(dest + ".part"))

    def test_store_peers_and_log(self):
        s = Store(os.path.join(self.tmp, "s"), inbox=os.path.join(self.tmp, "s", "inbox"))
        s.add_peer("p1", "手機", os.urandom(32))
        self.assertEqual("手機", s.peer("p1")["name"])
        s.update_peer_name("p1", "新名字")
        self.assertEqual("新名字", s.peer("p1")["name"])
        s.remove_peer("p1")
        self.assertIsNone(s.peer("p1"))
        s.log("測試記錄")
        self.assertIn("測試記錄", s.recent()[0])


if __name__ == "__main__":
    unittest.main()
