"""互通測試：Windows 版（Python）對手機版的 Java 程式碼，雙向都要通。
需要 javac（會把 android-overlay 裡的 Transfer*.java 編譯到暫存資料夾）；沒有 javac 就跳過。
執行：python -m unittest discover -s tests -v   （在 windows-transfer 資料夾下）"""
import os
import shutil
import socket
import subprocess
import tempfile
import threading
import time
import unittest

import sys
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from phoneai_transfer import crypto, net  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
JAVA_SRC = os.path.join(HERE, "..", "..", "android-overlay", "app", "src", "main", "java", "com", "phoneai", "overlay")
HARNESS = os.path.join(HERE, "java")


def free_port():
    s = socket.socket()
    s.bind(("127.0.0.1", 0))
    p = s.getsockname()[1]
    s.close()
    return p


def build_java(out):
    files = [os.path.join(JAVA_SRC, f) for f in ("TransferCrypto.java", "TransferServer.java", "TransferClient.java",
                                                   "TransferDiscovery.java")]
    files += [os.path.join(HARNESS, "InteropServer.java"), os.path.join(HARNESS, "JavaClient.java")]
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", out] + files, check=True, capture_output=True)



class TestHost:
    """Windows 版伺服器的 host（GUI 版會換成真正會顯示通知與存設定的實作）。"""

    def __init__(self, my_id, my_name, keys, inbox):
        self._id, self._name, self._keys, self._inbox = my_id, my_name, keys, inbox
        self.events = []
        self.pair_seen = None

    def my_id(self):
        return self._id

    def my_name(self):
        return self._name

    def key_of(self, peer_id):
        return self._keys.get(peer_id)

    def inbox(self):
        return self._inbox

    def pairing_open(self):
        return True

    def pair_request(self, peer_id, peer_name, key, sas):
        self.pair_seen = (peer_id, peer_name, key, sas)

    def pair_state(self, peer_id):
        return "none"

    def event(self, peer_id, kind, what, size):
        self.events.append((kind, what, size))

    def peer_name(self, peer_id, name):
        pass

    def peer_address(self, peer_id, ip):
        pass

    def progress(self, peer_id, to_peer, name, done, total):
        pass

    def transfer_ended(self):
        pass


@unittest.skipUnless(shutil.which("javac") and shutil.which("java"), "需要 javac／java")
class Interop(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.mkdtemp()
        cls.classes = os.path.join(cls.tmp, "classes")
        os.makedirs(cls.classes)
        build_java(cls.classes)

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.tmp, ignore_errors=True)

    def setUp(self):
        self.key = os.urandom(32)
        self.share = tempfile.mkdtemp(dir=self.tmp)
        self.inbox = os.path.join(self.share, "inbox")
        os.makedirs(self.inbox)
        self.payload = os.path.join(self.share, "hello.txt")
        with open(self.payload, "wb") as f:
            f.write(b"hello from python\n" * 5000)   # 跨過好幾段（每段 64 KB）

    @staticmethod
    def _stop(proc):
        proc.kill()
        proc.wait()
        proc.stdout.close()
        proc.stderr.close()

    def start_java_server(self):
        port = free_port()
        proc = subprocess.Popen(["java", "-cp", self.classes, "com.phoneai.overlay.InteropServer", str(port),
                                 crypto.hexs(self.key), self.share, os.path.join(self.share, "javainbox")],
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        os.makedirs(os.path.join(self.share, "javainbox"), exist_ok=True)
        self.addCleanup(self._stop, proc)
        line = proc.stdout.readline()
        if line.strip() != "READY":
            proc.kill()
            self.fail("Java 伺服器沒有啟動：" + proc.stderr.read())
        return port, proc

    def test_python_client_talks_to_java_server(self):
        port, proc = self.start_java_server()
        c = net.Client("127.0.0.1", port, "pyphone", "PyPC", self.key)
        self.assertEqual("JavaPC", c.ping())
        listing = c.list(self.share)
        self.assertIn("hello.txt", listing)
        self.assertIn("F\t", listing)
        found = c.find("hello")
        self.assertIn("hello.txt", found)
        dest = os.path.join(self.tmp, "got.txt")
        n = c.download(self.payload, dest)
        self.assertEqual(os.path.getsize(self.payload), n)
        with open(dest, "rb") as a, open(self.payload, "rb") as b:
            self.assertEqual(b.read(), a.read())
        saved = c.put(self.payload)
        self.assertEqual("hello.txt", saved)
        self.assertTrue(os.path.exists(os.path.join(self.share, "javainbox", "hello.txt")))

    def test_python_client_wrong_key_is_rejected(self):
        port, proc = self.start_java_server()
        c = net.Client("127.0.0.1", port, "pyphone", "PyPC", os.urandom(32))
        with self.assertRaises(IOError):
            c.ping()

    def test_pairing_sas_and_key_match_java(self):
        port, proc = self.start_java_server()
        offer = net.pair_start("127.0.0.1", port, "pyphone", "PyPC")
        line = proc.stdout.readline().strip().split(" ")
        self.assertEqual("PAIR", line[0])
        self.assertEqual("pyphone", line[1])
        self.assertEqual(offer.sas, line[3])                 # 兩邊的確認碼一樣
        self.assertEqual(crypto.hexs(offer.key), line[4])    # 兩邊算出的長期金鑰一樣

    def test_java_client_talks_to_python_server(self):
        host = TestHost("pyphone", "PyPC", {"pyphone": self.key}, self.inbox)
        server = net.Server(host)
        port = free_port()
        server.start(port)
        self.addCleanup(server.stop)
        time.sleep(0.1)

        def run(*args):
            r = subprocess.run(["java", "-cp", self.classes, "com.phoneai.overlay.JavaClient", str(port),
                                crypto.hexs(self.key)] + list(args), capture_output=True, text=True, timeout=60)
            self.assertEqual(0, r.returncode, r.stderr)
            return r.stdout

        self.assertIn("OUT PyPC", run("ping"))
        self.assertIn("hello.txt", run("list", self.share))
        dest = os.path.join(self.tmp, "fromjava.txt")
        self.assertIn("OUT", run("get", self.payload, dest))
        with open(dest, "rb") as a, open(self.payload, "rb") as b:
            self.assertEqual(b.read(), a.read())
        self.assertIn("OUT hello.txt", run("put", self.payload))
        with open(os.path.join(self.inbox, "hello.txt"), "rb") as a, open(self.payload, "rb") as b:
            self.assertEqual(b.read(), a.read())


class Crypto(unittest.TestCase):
    def test_sealed_roundtrip_multi_chunk(self):
        import io
        key = os.urandom(32)
        data = os.urandom(3 * crypto.CHUNK + 123)
        buf = io.BytesIO()
        w = crypto.SealedWriter(buf, key)
        w.write(data)
        w.close()
        buf.seek(0)
        self.assertEqual(data, crypto.SealedReader(buf, key).read())

    def test_truncated_stream_is_detected(self):
        import io
        key = os.urandom(32)
        buf = io.BytesIO()
        w = crypto.SealedWriter(buf, key)
        w.write(os.urandom(crypto.CHUNK * 2))
        # 不呼叫 close()：最後一段沒送出，應該被當成被截斷
        raw = buf.getvalue()
        with self.assertRaises(IOError):
            crypto.SealedReader(io.BytesIO(raw), key).read()


if __name__ == "__main__":
    unittest.main()
