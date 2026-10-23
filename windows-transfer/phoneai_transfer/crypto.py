"""加密與簽章：跟手機版 TransferCrypto.java 逐項相同（格式要一模一樣，兩邊才能互通）。"""
import hashlib
import hmac as _hmac
import os
import struct

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

CHUNK = 64 * 1024


def random_bytes(n):
    return os.urandom(n)


def hexs(b):
    return b.hex()


def unhex(s):
    return bytes.fromhex(s)


def hmac256(key, data):
    return _hmac.new(key, data.encode("utf-8"), hashlib.sha256).digest()


def sign(key, method, target, ts, nonce):
    """請求簽章：方法、目標網址（含查詢字串，要跟實際送出的一樣）、時間戳、隨機數。"""
    return hexs(hmac256(key, f"{method}\n{target}\n{ts}\n{nonce}"))


def same_hex(a, b):
    return _hmac.compare_digest(a.encode("utf-8"), b.encode("utf-8"))


def enc_key(key, nonce):
    """這一個請求的內容加密金鑰（每個請求不同）。"""
    return hmac256(key, "enc|" + nonce)


# ---------- 配對（ECDH P-256） ----------

class Pair:
    def __init__(self):
        self._priv = ec.generate_private_key(ec.SECP256R1())
        # Java 的 getEncoded() 是 X.509 SubjectPublicKeyInfo（DER），跟這裡一樣
        self.public = self._priv.public_key().public_bytes(
            serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)

    def shared(self, their_public):
        their = serialization.load_der_public_key(their_public)
        return self._priv.exchange(ec.ECDH(), their)


def transcript(init_id, init_pub, resp_id, resp_pub):
    """發起方的資料在前；確認碼與長期金鑰都綁著它，中間人換金鑰就會對不上。"""
    return f"{init_id}|{hexs(init_pub)}|{resp_id}|{hexs(resp_pub)}"


def pair_key(shared, tr):
    return hmac256(shared, "key|" + tr)


def sas(shared, tr):
    """6 位數確認碼：兩台都顯示，使用者核對相符才接受。"""
    v = int.from_bytes(hmac256(shared, "sas|" + tr), "big")
    return "%06d" % (v % 1000000)


# ---------- 分段加密 ----------

def _iv12(index, last):
    # 8 位元組的索引（大端）＋ 3 個 0 ＋ 最後一位元組是旗標，跟 Java iv() 相同
    return struct.pack(">Q", index) + b"\x00\x00\x00" + bytes([1 if last else 0])


class SealedWriter:
    """每段：[旗標 1][長度 4，大端][密文＋驗證碼]；旗標 1 = 最後一段。close() 一定送出最後一段。"""

    def __init__(self, sock_file, key):
        self._out = sock_file
        self._aes = AESGCM(key)
        self._buf = bytearray()
        self._index = 0
        self._closed = False

    def write(self, data):
        self._buf += data
        while len(self._buf) > CHUNK:   # 超過一段才送；最後一段留給 close()
            self._flush(False, bytes(self._buf[:CHUNK]))
            del self._buf[:CHUNK]

    def _flush(self, last, data):
        ct = self._aes.encrypt(_iv12(self._index, last), data, None)
        self._index += 1
        self._out.write(bytes([1 if last else 0]) + struct.pack(">I", len(ct)) + ct)

    def close(self):
        if self._closed:
            return
        self._closed = True
        self._flush(True, bytes(self._buf))
        self._buf = bytearray()
        self._out.flush()


class SealedReader:
    """解密輸入：每段驗證；沒收到最後一段就丟 IOError（內容被截斷）。"""

    def __init__(self, sock_file, key):
        self._in = sock_file
        self._aes = AESGCM(key)
        self._cur = b""
        self._pos = 0
        self._index = 0
        self._ended = False

    def _readn(self, n):
        data = b""
        while len(data) < n:
            part = self._in.read(n - len(data))
            if not part:
                raise IOError("資料不完整（連線被切斷）")
            data += part
        return data

    def _fill(self):
        while self._pos >= len(self._cur):
            if self._ended:
                return False
            head = self._readn(5)
            last = head[0] == 1
            (ln,) = struct.unpack(">I", head[1:5])
            if ln < 16 or ln > CHUNK + 64:
                raise IOError("資料格式不對")
            ct = self._readn(ln)
            try:
                self._cur = self._aes.decrypt(_iv12(self._index, last), ct, None)
            except Exception:
                raise IOError("驗證失敗（內容被改過或金鑰不對）")
            self._index += 1
            self._pos = 0
            if last:
                self._ended = True
        return True

    def read(self, n=-1):
        out = bytearray()
        while n < 0 or len(out) < n:
            if not self._fill():
                break
            take = len(self._cur) - self._pos
            if n >= 0:
                take = min(take, n - len(out))
            out += self._cur[self._pos:self._pos + take]
            self._pos += take
        return bytes(out)
