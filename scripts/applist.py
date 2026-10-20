"""applist.py — 「App 顯示名稱 -> 套件名稱」對照表（~/apps.tsv）。

~/apps.tsv 是「顯示名稱<TAB>套件名稱」（只含桌面上有圖示的 App）。Termux 讀不到別的 App 的顯示名稱，
所以向 PhoneAI Overlay App 要（經 overlay.py 的連線，cmd "apps"），再由這裡存成檔案。
檔案不存在時 load() 會自動向 App 要一次；之後想重抓：python ~/applist.py（同時會印出整份清單，也就是「列出所有 App 包名」）。
App 沒裝/連不上、檔案壞掉時一律當成空表，不影響其他功能。
"""
import os, re

PATH = os.path.expanduser("~/apps.tsv")


def norm(t):
    return re.sub(r"[\s\-_，。,.·!！?？:：'’]", "", t).lower()  # 驚嘆號等也去掉：「Block Blast!」要能比對到「Block Blast」


def refresh():
    """向 PhoneAI Overlay App 要清單並存到 ~/apps.tsv。成功回傳筆數，失敗回傳 None。"""
    try:
        from overlay import Overlay
    except ImportError:
        return None
    ov = Overlay()
    if not ov.connect():
        return None
    rows = ov.apps()
    ov.close()
    if not rows:
        return None
    tmp = PATH + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        for label, pkg in rows:
            f.write(label + "\t" + pkg + "\n")
    os.replace(tmp, PATH)
    return len(rows)


def load():
    """回傳 {正規化後的顯示名稱: 套件名稱}。同名取先出現的。檔案不存在就先向 App 要一次。"""
    if not os.path.exists(PATH):
        refresh()
    d = {}
    try:
        for line in open(PATH, encoding="utf-8"):
            label, _, pkg = line.rstrip("\n").partition("\t")
            pkg = pkg.strip()
            if label and re.fullmatch(r"[A-Za-z0-9_.]+", pkg):
                d.setdefault(norm(label), pkg)
    except OSError:
        pass
    return d


def find(name, table=None):
    """用名稱找套件：先完全相同；再找「唯一」互相包含的（至少 2 個字）。找不到或有多個可能就回 None（寧可不開）。"""
    table = load() if table is None else table
    n = norm(name or "")
    if len(n) < 2:
        return None
    if n in table:
        return table[n]
    hits = {pkg for k, pkg in table.items() if n in k or (len(k) >= 2 and k in n)}
    return hits.pop() if len(hits) == 1 else None


if __name__ == "__main__":
    n = refresh()
    if n is None:
        print("向 PhoneAI Overlay App 要清單失敗（App 沒裝、服務沒起來，或 root 沒授權給 Termux）。")
        raise SystemExit(1)
    print("共 %d 個 App（已存到 %s）\n" % (n, PATH))
    for line in open(PATH, encoding="utf-8"):
        label, _, pkg = line.rstrip("\n").partition("\t")
        print("%s  %s" % (pkg, label))
