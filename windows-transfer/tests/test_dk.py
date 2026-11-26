"""回應金鑰分開（dk）：新版對新版用分開的金鑰；新版用戶端遇到舊版伺服器（沒回 X-Dk）照舊也能通。"""
import os
import sys
import tempfile
import time
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from phoneai_transfer import crypto, net  # noqa: E402
from tests.test_interop import TestHost, free_port  # noqa: E402


class Dk(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.key = bytes(range(32))
        self.host = TestHost("srv", "Server", {"cli": self.key}, self.tmp)
        self.port = free_port()
        self.server = net.Server(self.host)
        self.server.start(self.port)
        time.sleep(0.2)

    def tearDown(self):
        self.server.stop()

    def client(self):
        return net.Client("127.0.0.1", self.port, "cli", "Client", self.key)

    def test_vector_matches_java(self):
        # 跟 Java 的 TransferCrypto.respKey 一樣的值（Java 單元測試也檢查同一組）
        self.assertEqual(crypto.resp_key(bytes(range(32)), "0a1b2c").hex(), "aa4ccec66d2984f9cff8f9255c3987778896725b44d6f1455dedc03faa172598")
        self.assertEqual(crypto.with_dk("/v1/ping"), "/v1/ping?dk=1")
        self.assertEqual(crypto.with_dk("/v1/list?path=a"), "/v1/list?path=a&dk=1")

    def test_new_to_new_ping_and_put(self):
        c = self.client()
        self.assertEqual(c.ping(), "Server")
        src = os.path.join(self.tmp, "small.txt")
        with open(src, "w") as f:
            f.write("hello")   # 只有一個區塊：以前請求與回應的金鑰＋IV 會完全一樣
        self.assertTrue(c.put(src))

    def test_new_client_old_server(self):
        # 模擬舊版伺服器：不看 dk，也不回 X-Dk
        orig = self.server._resp
        self.server._resp = lambda key, nonce: (crypto.enc_key(key, nonce), "")
        try:
            self.assertEqual(self.client().ping(), "Server")
        finally:
            self.server._resp = orig


    def test_old_client_new_server(self):
        # 模擬舊版用戶端：不加 dk、也不看 X-Dk（回應用跟請求同一把金鑰）
        orig_dk, orig_rk = crypto.with_dk, net.Client._resp_key
        crypto.with_dk = lambda t: t
        net.Client._resp_key = lambda self, h, nonce: crypto.enc_key(self.key, nonce)
        try:
            self.assertEqual(self.client().ping(), "Server")
        finally:
            crypto.with_dk, net.Client._resp_key = orig_dk, orig_rk


if __name__ == "__main__":
    unittest.main()
