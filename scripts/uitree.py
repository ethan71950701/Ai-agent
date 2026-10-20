#!/usr/bin/env python3
"""用 root 的 `uiautomator dump` 讀某個螢幕上的元件（文字、描述、能不能點、位置），主螢幕與背景虛擬螢幕都行
（背景螢幕：uiautomator dump --display <系統編號>，三星實測讀得到 97 個元件）。
這個檔只負責「讀取與整理」，不呼叫 Claude；要不要把結果交給 AI 由 agent2 決定（會涉及畫面文字，要使用者同意）。

用法（實驗）：python ~/uitree.py [螢幕編號]     不加編號 = 主螢幕；會印出花了幾秒、元件數、前幾十個元件，最後檢查 App 的無障礙服務還在不在
"""
import os, re, subprocess, sys, time
import xml.etree.ElementTree as ET

DUMP = "/data/local/tmp/ui_dump.xml"
BOUNDS = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")


def parse(xml_text, max_nodes=300):
    """uiautomator 的 XML → 元件清單。每個：{n, text, desc, id, cls, click, scroll, x, y, w, h}。
    略過：密碼欄位、看不見（寬或高為 0）、沒有文字／描述／也不能點的容器。"""
    try:
        root = ET.fromstring(xml_text)
    except ET.ParseError:
        return []
    out = []
    for e in root.iter("node"):
        if e.get("password") == "true":
            continue
        m = BOUNDS.fullmatch(e.get("bounds", ""))
        if not m:
            continue
        x1, y1, x2, y2 = (int(v) for v in m.groups())
        if x2 <= x1 or y2 <= y1:
            continue
        text, desc = e.get("text", "").strip(), e.get("content-desc", "").strip()
        click = e.get("clickable") == "true"
        if not (text or desc or click):
            continue
        out.append({"n": len(out), "text": text, "desc": desc, "id": e.get("resource-id", ""),
                    "cls": e.get("class", "").rsplit(".", 1)[-1], "click": click, "scroll": e.get("scrollable") == "true",
                    "x": (x1 + x2) // 2, "y": (y1 + y2) // 2, "w": x2 - x1, "h": y2 - y1})
        if len(out) >= max_nodes:
            break
    return out


def dump(display=None):
    """讀元件；display=None 讀主螢幕。回傳 (元件清單, 秒數)；失敗回 ([], 秒數)。"""
    opt = "--display %d " % int(display) if display else ""
    cmd = "rm -f %s; uiautomator dump %s%s >/dev/null 2>&1; cat %s 2>/dev/null; rm -f %s" % (DUMP, opt, DUMP, DUMP, DUMP)
    t0 = time.time()
    if os.getuid() == 0:
        # 已經是 root（例如 vdisplay.py --direct-check 呼叫）：直接用系統的 sh，並拿掉 Termux 的函式庫路徑——
        # 不拿掉的話 uiautomator 會去載入 Termux 的函式庫而失敗（0.2 秒就回來、0 個元件）
        env = {k: v for k, v in os.environ.items() if k not in ("LD_LIBRARY_PATH", "LD_PRELOAD")}
        r = subprocess.run(["/system/bin/sh", "-c", cmd], capture_output=True, text=True, env=env)
    else:
        r = subprocess.run(["su", "-c", cmd], capture_output=True, text=True)
    return parse(r.stdout), time.time() - t0


def line(it):
    """一個元件一行，給人看或給模型看：編號、可點、文字／描述、中心座標。"""
    label = it["text"] or it["desc"] or it["id"].rsplit("/", 1)[-1] or it["cls"]
    return "%d%s %s (%d,%d)" % (it["n"], "*" if it["click"] else "", label[:40], it["x"], it["y"])


def _main():
    display = int(sys.argv[1]) if len(sys.argv) > 1 and sys.argv[1].isdigit() else None
    items, secs = dump(display)
    print("螢幕 %s：%d 個元件，讀取花了 %.2f 秒（* = 可點）" % (display if display else "0（主螢幕）", len(items), secs))
    for it in items[:40]:
        print("  " + line(it))
    try:  # uiautomator 可能暫時擠掉其他無障礙服務：讀完立刻問 App 無障礙還在不在
        sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
        from overlay import Overlay
        ov = Overlay()
        if ov.connect():
            print("讀取後 App 診斷:", ov.check())
            ov.end()
        else:
            print("連不上 App（無法檢查無障礙服務）")
    except Exception as e:
        print("檢查 App 診斷出錯:", repr(e))
    return 0


if __name__ == "__main__":
    sys.exit(_main())
