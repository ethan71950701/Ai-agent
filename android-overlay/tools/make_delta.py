#!/usr/bin/env python3
"""用法：make_delta.py 舊.apk 新.apk 輸出.patch
用 bsdiff4 做差異，再把裡面的 bzip2 區塊換成 zlib（App 端只需要 java.util.zip，不用額外函式庫）。
格式 VXPATCH1：魔術字 8 + 新檔大小 8，接著 ctrl／diff／extra 三塊各「原始長度 8 + 壓縮長度 8」，最後是三塊 zlib 資料。數字都是小端 int64。"""
import bz2, struct, sys, zlib

import bsdiff4

def main(old, new, out):
    with open(old, 'rb') as f: a = f.read()
    with open(new, 'rb') as f: b = f.read()
    raw = bsdiff4.diff(a, b)
    assert raw[:8] == b'BSDIFF40'
    clen, dlen, nlen = struct.unpack('<qqq', raw[8:32])
    assert nlen == len(b)
    blocks = [raw[32:32 + clen], raw[32 + clen:32 + clen + dlen], raw[32 + clen + dlen:]]
    plain = [bz2.decompress(x) for x in blocks]
    comp = [zlib.compress(x, 9) for x in plain]
    with open(out, 'wb') as f:
        f.write(b'VXPATCH1' + struct.pack('<q', nlen))
        for p, c in zip(plain, comp): f.write(struct.pack('<qq', len(p), len(c)))
        for c in comp: f.write(c)

if __name__ == '__main__':
    main(*sys.argv[1:4])
