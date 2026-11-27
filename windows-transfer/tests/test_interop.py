"""互通測試：Windows 版（Python）對手機版的 Java 程式碼，雙向都要通。
需要 javac（會把 android-overlay 裡的 Transfer*.java 與它用到的純 Java 類別編譯到暫存資料夾；
要 Android 的類別用 tests/java/stubs 的替身）；沒有 javac 就跳過。
另外要 org.json（手機上內建、電腦上沒有）：依序找環境變數 PHONEAI_JSON_JAR、Gradle 快取、Maven Central 下載（核對 SHA-256）。
執行：python -m unittest tests.test_interop -v   （在 windows-transfer 資料夾下）"""
import glob
import hashlib
import os
import shutil
import socket
import subprocess
import tempfile
import threading
import time
import unittest
import urllib.request
from unittest import mock

import sys
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from phoneai_transfer import crypto, net  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
JAVA_SRC = os.path.join(HERE, "..", "..", "android-overlay", "app", "src", "main", "java", "com", "phoneai", "overlay")
HARNESS = os.path.join(HERE, "java")
# 手機版 Transfer*.java 直接用到的純 Java 類別（沒有 Android 相依），一起編譯
JAVA_FILES = ("TransferCrypto.java", "TransferServer.java", "TransferClient.java", "TransferDiscovery.java",
              "Checksum.java", "Resume.java", "SpaceCheck.java", "PendingStore.java", "FcmFiles.java", "Doorbell.java")
JSON_VER = "20231013"   # 跟 android-overlay/app/build.gradle 的 testImplementation 同一版
JSON_SHA256 = "0f18192df289114e17aa1a0d0a7f8372cc9f5c7e4f7e39adcf8906fe714fa7d3"
JSON_URL = f"https://repo1.maven.org/maven2/org/json/json/{JSON_VER}/json-{JSON_VER}.jar"


def free_port():
    s = socket.socket()
    s.bind(("127.0.0.1", 0))
    p = s.getsockname()[1]
    s.close()
    return p


def json_jar(tmp):
    """找 org.json 的 jar；都找不到回 None（測試跳過）。"""
    env = os.environ.get("PHONEAI_JSON_JAR")
    if env and os.path.isfile(env):
        return env
    cache = os.path.join(os.path.expanduser("~"), ".gradle", "caches", "modules-2", "files-2.1", "org.json", "json")
    found = sorted(glob.glob(os.path.join(cache, JSON_VER, "*", f"json-{JSON_VER}.jar")))
    if found:
        return found[0]
    dest = os.path.join(tmp, f"json-{JSON_VER}.jar")
    try:
        with urllib.request.urlopen(JSON_URL, timeout=30) as r:
            data = r.read()
    except OSError:
        return None
    if hashlib.sha256(data).hexdigest() != JSON_SHA256:
        raise RuntimeError("下載的 org.json jar 跟預期的 SHA-256 不符")
    with open(dest, "wb") as f:
        f.write(data)
    return dest


def build_java(out, jar):
    files = [os.path.join(JAVA_SRC, f) for f in JAVA_FILES]
    files += sorted(glob.glob(os.path.join(HARNESS, "*.java"))) + sorted(glob.glob(os.path.join(HARNESS, "stubs", "*.java")))
    r = subprocess.run(["javac", "-encoding", "UTF-8", "-cp", jar, "-d", out] + files, capture_output=True, text=True)
    if r.returncode != 0:
        raise RuntimeError("編譯手機版 Java 失敗：\n" + r.stderr[-4000:])



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


# CI 設 PHONEAI_INTEROP_REQUIRED=1：缺 javac 或 org.json 時直接失敗，不要默默跳過
REQUIRED = os.environ.get("PHONEAI_INTEROP_REQUIRED") == "1"


@unittest.skipUnless(REQUIRED or (shutil.which("javac") and shutil.which("java")), "需要 javac／java")
class Interop(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.mkdtemp()
        cls.classes = os.path.join(cls.tmp, "classes")
        os.makedirs(cls.classes)
        jar = json_jar(cls.tmp)
        if jar is None:
            if REQUIRED:
                raise RuntimeError("找不到 org.json，互通測試沒辦法跑")
            shutil.rmtree(cls.tmp, ignore_errors=True)
            raise unittest.SkipTest("找不到 org.json（設 PHONEAI_JSON_JAR，或讓測試能連到 Maven Central）")
        build_java(cls.classes, jar)
        cls.cp = os.pathsep.join([cls.classes, jar])

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
        proc = subprocess.Popen(["java", "-cp", self.cp, "com.phoneai.overlay.InteropServer", str(port),
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
            r = subprocess.run(["java", "-cp", self.cp, "com.phoneai.overlay.JavaClient", str(port),
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

    def test_response_key_derivation_matches_java(self):
        """回應金鑰（resp|nonce）、請求金鑰（enc|nonce）與加 dk=1 的網址，兩邊算出來要一樣。"""
        nonce = crypto.hexs(os.urandom(12))
        for target in ("/v1/ping", "/v1/list?path=%2Fsdcard"):
            r = subprocess.run(["java", "-cp", self.cp, "com.phoneai.overlay.KeyCheck", crypto.hexs(self.key), nonce, target],
                               capture_output=True, text=True, timeout=60)
            self.assertEqual(0, r.returncode, r.stderr)
            enc, resp, dk = r.stdout.splitlines()
            self.assertEqual(crypto.hexs(crypto.enc_key(self.key, nonce)), enc)
            self.assertEqual(crypto.hexs(crypto.resp_key(self.key, nonce)), resp)
            self.assertNotEqual(enc, resp)
            self.assertEqual(crypto.with_dk(target), dk)

    def _record_dk(self):
        """記下 Python 客戶端每次收到的回應標頭裡的 X-Dk。"""
        seen = []
        orig = net.Client._resp_key

        def spy(client, h, nonce):
            seen.append(h.get("x-dk"))
            return orig(client, h, nonce)
        p = mock.patch.object(net.Client, "_resp_key", spy)
        p.start()
        self.addCleanup(p.stop)
        return seen

    def test_java_server_answers_new_client_with_response_key(self):
        """新版客戶端（網址帶 dk=1）：Java 伺服器要回 X-Dk: 1，並用回應金鑰加密。"""
        port, proc = self.start_java_server()
        seen = self._record_dk()
        c = net.Client("127.0.0.1", port, "pyphone", "PyPC", self.key)
        self.assertEqual("JavaPC", c.ping())
        self.assertIn("hello.txt", c.list(self.share))
        self.assertTrue(seen)
        self.assertEqual({"1"}, set(seen))

    def test_old_client_without_dk_still_works_with_java_server(self):
        """舊版客戶端（不帶 dk=1）：Java 伺服器不回 X-Dk，照舊用同一把金鑰，還是要通。"""
        port, proc = self.start_java_server()
        seen = self._record_dk()
        with mock.patch.object(crypto, "with_dk", lambda t: t):
            c = net.Client("127.0.0.1", port, "pyphone", "PyPC", self.key)
            self.assertEqual("JavaPC", c.ping())
            self.assertIn("hello.txt", c.list(self.share))
            dest = os.path.join(self.tmp, "old.txt")
            self.assertEqual(os.path.getsize(self.payload), c.download(self.payload, dest))
        with open(dest, "rb") as a, open(self.payload, "rb") as b:
            self.assertEqual(b.read(), a.read())
        self.assertTrue(seen)
        self.assertEqual({None}, set(seen))


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
