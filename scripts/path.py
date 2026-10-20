"""path.py — 本地模型（Qwen3-VL 2B + llama.cpp）照「路徑」逐步找座標並點擊

用法: python ~/path.py "設定,連線,Wi-Fi"
  逗號分隔每一步要找的東西（要跟畫面上的字一致）。
環境變數 PATH_WAIT: 開始前等待秒數，預設 5（讓使用者切到起始畫面）；桌面小工具會設成 1。

流程（每一步）:
  記下前景 App -> 截圖 -> 把 Termux 拉到前景（背景跑模型會慢 4 倍）-> 本地模型找座標
  -> 回到原本的 App（並檢查真的回去了）-> 點擊
  回不去就不點擊（安全）。找不到 / 解析失敗 / 畫面沒變 也不點擊。

座標是 0~1000 的相對座標（已驗證）。模型輸出格式不穩定（整數、0~1 小數、少引號、bbox），
所以解析寫得很寬鬆。
"""
import subprocess, sys, re, time, hashlib, os

HOME = os.path.expanduser("~")
LLAMA = f"{HOME}/llama.cpp/build/bin/llama-mtmd-cli"
MODEL = f"{HOME}/model/Qwen3VL-2B-Instruct-Q4_K_M.gguf"
MMPROJ = f"{HOME}/model/mmproj-Qwen3VL-2B-Instruct-Q8_0.gguf"
SHOT = f"{HOME}/s.png"
SMALL = f"{HOME}/s_small.png"
WIDTH = 360  # 本地模型用 360 寬（測過 5 個畫面都對，比 540 快）
BLOCK = ["付款", "轉帳", "匯款", "購買", "支付", "刪除", "密碼", "下單"]


def su(cmd):
    return subprocess.run(["su", "-c", cmd], capture_output=True, text=True).stdout


def get_focus():
    out = su("dumpsys window | grep mCurrentFocus")
    m = re.search(r"([A-Za-z0-9_.]+/[A-Za-z0-9_.$]+)", out)
    return m.group(1) if m else None


def shot():
    su(f"screencap -p {SHOT}; chown {os.getuid()}:{os.getgid()} {SHOT}")
    subprocess.run(["magick", SHOT, "-resize", f"{WIDTH}x", SMALL])
    r = subprocess.run(["identify", "-format", "%w %h", SHOT], capture_output=True, text=True)
    w, h = r.stdout.split()
    return int(w), int(h), hashlib.md5(open(SMALL, "rb").read()).hexdigest()


def restore(focus):
    """回到原本的 App 畫面。桌面用 HOME 鍵；其他用 am start 加旗標把『既有畫面』移到最前面。
    （用 monkey 或不加旗標的 am start 會重開新畫面，頁面會跑掉。）"""
    pkg = focus.split("/")[0]
    if "launcher" in pkg:
        su("input keyevent 3")
    else:
        su(f"am start -n '{focus}' -f 0x10020000")
    time.sleep(1.5)
    now = get_focus() or ""
    return now.startswith(pkg)


def find(target):
    prompt = (f"找出{target}。只回傳標準JSON。找到就回傳"
              '{"found":true,"x":中心x,"y":中心y}，x和y都是0到1000的整數，不要小數。'
              '畫面上沒有就回傳{"found":false}')
    # -c 2048：不加的話預設上下文太大，會被系統 Killed
    r = subprocess.run([LLAMA, "-m", MODEL, "--mmproj", MMPROJ, "--image", SMALL,
                        "-c", "2048", "-p", prompt], capture_output=True, text=True)
    out = r.stdout
    if re.search(r'found"?\s*:\s*false', out):
        return None, out
    x = re.search(r'(?<![A-Za-z])x"?\s*:\s*([0-9.]+)', out)
    y = re.search(r'(?<![A-Za-z])y"?\s*:\s*([0-9.]+)', out)
    if not (x and y):
        return None, out
    xv, yv = float(x.group(1)), float(y.group(1))
    if xv <= 1.5 and yv <= 1.5:
        xv, yv = xv * 1000, yv * 1000
    return (xv, yv), out


def main():
    if len(sys.argv) < 2:
        print('用法: python ~/path.py "設定,連線,Wi-Fi"')
        return
    targets = [t.strip() for t in sys.argv[1].split(",") if t.strip()]
    if any(b in t for t in targets for b in BLOCK):
        print("路徑含敏感字眼，拒絕執行")
        return
    wait = float(os.environ.get("PATH_WAIT", "5"))
    if wait >= 3:
        print("5 秒內切到起始畫面...")
    time.sleep(wait)
    last_hash = None
    for i, t in enumerate(targets, 1):
        print(f"\n=== 第 {i}/{len(targets)} 步：找「{t}」 ===")
        focus = get_focus()
        if not focus:
            print("抓不到前景畫面，停止")
            return
        try:
            w, h, hh = shot()
        except Exception as e:
            print("截圖失敗:", e)
            return
        if hh == last_hash:
            print("上一步點擊後畫面沒變，停止")
            return
        last_hash = hh
        su("am start -n com.termux/com.termux.app.TermuxActivity")
        t0 = time.time()
        pos, out = find(t)
        print(f"模型回答: {out.strip()} ({time.time()-t0:.0f} 秒)")
        if not pos:
            print("找不到或看不懂，停止")
            return
        if not restore(focus):
            print("沒能回到原本畫面，為了安全不點擊，停止")
            return
        px, py = int(pos[0] * w / 1000), int(pos[1] * h / 1000)
        print(f"點擊 ({px},{py})")
        su(f"input tap {px} {py}")
        time.sleep(2)
    print("\n全部步驟執行完，請確認最後的畫面對不對")


try:
    main()
finally:
    for f in (SHOT, SMALL):  # 截圖可能含私人內容，結束就刪
        try:
            os.remove(f)
        except OSError:
            pass
