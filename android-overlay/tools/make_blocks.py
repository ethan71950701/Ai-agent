#!/usr/bin/env python3
"""用法：make_blocks.py 新.apk 輸出.blocks [區塊大小，預設 4096]
產生「區塊指紋檔」：App 拿它去比對手機上已安裝的（任何版本的）APK，相同的區塊直接複用，只用 HTTP Range 下載缺的部分。
格式 VXBLK1：魔術字 8、區塊大小 int32、檔案大小 int64、完整區塊數 int32，接著每個完整區塊：弱校驗 uint32（rsync 風格：a＝位元組和，b＝Σ(B−j)·x[j]，各取低 16 位，b 在高位）＋ MD5 前 8 位元組。小端。
最後不足一整塊的尾巴不放指紋（App 一律下載）。"""
import hashlib, struct, sys
import numpy as np

def main(path, out, B=4096):
    data = open(path, 'rb').read()
    n = len(data) // B
    arr = np.frombuffer(data, dtype=np.uint8, count=n * B).reshape(n, B)
    coef = (B - np.arange(B)).astype(np.int64)
    with open(out, 'wb') as f:
        f.write(b'VXBLK1\0\0' + struct.pack('<iqi', B, len(data), n))
        for s in range(0, n, 512):
            blk = arr[s:s + 512].astype(np.int64)
            a = blk.sum(axis=1)
            b = (blk * coef).sum(axis=1)
            for i in range(blk.shape[0]):
                weak = (int(a[i]) & 0xffff) | ((int(b[i]) & 0xffff) << 16)
                f.write(struct.pack('<I', weak))
                f.write(hashlib.md5(data[(s + i) * B:(s + i + 1) * B]).digest()[:8])

if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2], int(sys.argv[3]) if len(sys.argv) > 3 else 4096)
