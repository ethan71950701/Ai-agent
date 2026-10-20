"""powerkey.py — 用 root 偵測「長按電源鍵」，偵測到就叫出助理面板（給長按電源鍵被系統綁死的手機用，例如小米的小愛同學）。

用法:
    python ~/powerkey.py --test        只偵測、不開面板：按住電源鍵超過門檻會印出「偵測到長按」（先用這個確認）
    python ~/powerkey.py               偵測到長按就開助理面板（會一直跑，Ctrl+C 結束）
    nohup python ~/powerkey.py >/dev/null 2>&1 &     丟到背景一直跑
    python ~/powerkey.py --hold 0.8    調整門檻秒數（預設 0.4；系統的電源選單比我們早出現就調小一點，短按被誤判就調大）
    python ~/powerkey.py --close-menu  幫忙用 root 關掉系統的電源選單（預設不關，因為已用 settings 把系統長按設成不做事，見下）
原理：`getevent -l /dev/input/eventN` 會印出電源鍵的 DOWN / UP；按下超過門檻秒數還沒放開就算長按，用 root 啟動 PhoneAI 的 AskActivity。
限制：①系統自己的長按功能（小愛同學、電源選單）可能同時跳出來；電源選單預設由我們用 root 送 CLOSE_SYSTEM_DIALOGS 關掉（實測小米 13 上電源選單會出現）；②Termux 被系統殺掉就不會動，也不會開機自動跑；
      ③畫面關著時按電源鍵是「亮屏」，不一定算長按。
狀態：尚未在手機實測。
"""
import os, re, subprocess, sys, time

HOLD = 0.4  # 系統自己的長按約 0.5 秒就會跳出電源選單，我們要趕在它前面；一般短按約 0.1～0.2 秒，不會誤判
COOLDOWN = 2.0


def find_power_devices(pl_output):
    """從 `getevent -pl` 的輸出找出有 KEY_POWER 的輸入裝置路徑（可能不只一個）。
    名稱含 bark 的（例如小米 13 的 pmic_pwrkey_bark）不是真正的按鍵事件來源，先排除；全被排除時才退回全部。"""
    devs, path, name = [], None, ""
    for line in (pl_output or "").splitlines():
        m = re.match(r"add device \d+: (\S+)", line)
        if m:
            path, name = m.group(1), ""
            continue
        n = re.match(r'\s*name:\s+"(.*)"', line)
        if n:
            name = n.group(1)
        elif "KEY_POWER" in line and path and not any(d[0] == path for d in devs):
            devs.append((path, name))  # 名稱行在 events 行之前，這時 name 已經是這個裝置的
    real = [p for p, nm in devs if "pwrkey" in nm.lower() and "bark" not in nm.lower()]   # 名稱像電源鍵的優先（小米 13：pmic_pwrkey）
    if real:
        return real
    keep = [p for p, nm in devs if "bark" not in nm.lower() and "goodix" not in nm.lower()]  # 指紋感應器（goodix）也會登記電源鍵，不是我們要的
    return keep or [p for p, _ in devs]


def parse_power_event(line):
    """getevent -l 的一行 → "DOWN" / "UP"（只認 KEY_POWER），其他回 None。"""
    m = re.search(r"EV_KEY\s+KEY_POWER\s+(DOWN|UP)", line or "")
    return m.group(1) if m else None


LOG = os.path.expanduser("~/powerkey.log")


def log(msg):
    """每次偵測、觸發、開面板的結果都寫進 ~/powerkey.log（離開 Termux 後失效時，用來查是沒偵測到還是開面板失敗）。檔案超過 200KB 就清掉重來。"""
    try:
        if os.path.exists(LOG) and os.path.getsize(LOG) > 200_000:
            os.remove(LOG)
        fd = os.open(LOG, os.O_WRONLY | os.O_CREAT | os.O_APPEND, 0o600)  # 檔案由 Termux 自己建立，root 也能接著寫
        with os.fdopen(fd, "a", encoding="utf-8") as f:
            f.write("%s %s\n" % (time.strftime("%H:%M:%S"), msg))
    except OSError:
        pass


def parse_event_time(line):
    """getevent -lt 每行開頭的 [ 1234.567890]（開機後秒數，是核心記下的事件時間，不受程式有沒有被凍結影響）。沒有回 None。"""
    m = re.match(r"\s*\[\s*(\d+\.\d+)\]", line or "")
    return float(m.group(1)) if m else None


def process_uptime():
    try:
        return float(open("/proc/uptime").read().split()[0])
    except (OSError, ValueError, IndexError):
        return None


def su(cmd):
    return subprocess.run(["su", "-c", cmd], capture_output=True, text=True).stdout


CLOSE_DIALOGS = "am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS"
OPEN_PANEL = "am start -n com.phoneai.overlay/.AskActivity"  # 要不要自動開始聽由 App 決定（沒裝離線辨識、又只能用系統視窗的手機不會自動聽；裝了離線辨識就自動聽）


def fire_commands(close_menu=False):
    """偵測到長按時要用 root 依序執行的 shell 指令（拆成函式方便測試）。
    先關掉系統對話框（電源選單）→ 開面板 → 稍等再關一次（有的系統電源選單比我們晚幾百毫秒才出現）。"""
    cmds = [OPEN_PANEL]
    if close_menu:
        cmds = [CLOSE_DIALOGS, OPEN_PANEL, "sleep 0.4; " + CLOSE_DIALOGS, "sleep 0.5; " + CLOSE_DIALOGS]
    return cmds


def fire(test, close_menu=False):
    if test:
        print("偵測到長按（測試模式，不開面板）", flush=True)
        return
    print("偵測到長按 → 開助理面板", flush=True)
    log("觸發：開助理面板")
    cmds = fire_commands(close_menu)
    first = (cmds[0] + "; " + cmds[1]) if len(cmds) > 1 else cmds[0]
    subprocess.Popen(["su", "-c", "(%s) >> %s 2>&1" % (first, LOG)], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)  # am 的輸出也進紀錄檔
    for c in cmds[2:]:  # 補送的幾次在背景各自等一下再送，不卡住偵測
        subprocess.Popen(["su", "-c", c], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def main():
    test = "--test" in sys.argv
    close_menu = "--close-menu" in sys.argv
    hold = float(sys.argv[sys.argv.index("--hold") + 1]) if "--hold" in sys.argv else HOLD
    devs = find_power_devices(su("getevent -pl"))
    if not devs:
        print("找不到電源鍵的輸入裝置（getevent -pl 裡沒有 KEY_POWER）。需要 root。")
        return 1
    subprocess.run(["termux-wake-lock"], stderr=subprocess.DEVNULL)  # 要求喚醒鎖，降低背景被系統凍結的機率
    log("啟動：裝置 %s，門檻 %.1f 秒%s" % (" ".join(devs), hold, "，測試模式" if test else ""))
    print("電源鍵裝置：%s，長按門檻 %.1f 秒%s。按 Ctrl+C 結束。" % (" ".join(devs), hold, "（測試模式）" if test else ""), flush=True)
    import selectors
    sel = selectors.DefaultSelector()
    for d in devs:  # getevent 一次只能收一個裝置，所以每個裝置各開一個
        pr = subprocess.Popen(["su", "-c", "getevent -lt " + d], stdout=subprocess.PIPE, text=True, bufsize=1)
        sel.register(pr.stdout, selectors.EVENT_READ)
    down_t, fired, last_fire = None, False, 0.0
    ev_down, presses, fires, last_beat = None, 0, 0, time.time()
    try:
        while True:
            for key, _ in sel.select(timeout=0.05):
                line = key.fileobj.readline()
                if not line:
                    print("getevent 結束了。")
                    log("getevent 結束了（偵測中斷）")
                    return 1
                ev = parse_power_event(line)
                et, now_up = parse_event_time(line), process_uptime()
                late = (now_up - et) if (et is not None and now_up is not None) else None
                if ev == "DOWN":
                    down_t, fired, ev_down = time.time(), False, et
                    presses += 1
                    log("電源鍵按下" + ("（這個事件晚了 %.1f 秒才被處理）" % late if late is not None and late > 1.0 else ""))
                elif ev == "UP":
                    real = (et - ev_down) if (et is not None and ev_down is not None) else None
                    log("電源鍵放開：實際按了 %s 秒，程式量到 %s 秒" % ("%.2f" % real if real is not None else "?", "%.2f" % (time.time() - down_t) if down_t else "?"))
                    down_t = None
            if down_t and not fired and time.time() - down_t >= hold and time.time() - last_fire > COOLDOWN:
                fired, last_fire = True, time.time()
                fires += 1
                fire(test, close_menu)
            if time.time() - last_beat >= 60:  # 心跳：紀錄裡的時間如果有很大的缺口，代表那段時間程式被系統凍結了
                last_beat = time.time()
                log("心跳：按下 %d 次、觸發 %d 次" % (presses, fires))
    except KeyboardInterrupt:
        pass
    finally:
        subprocess.run(["termux-wake-unlock"], stderr=subprocess.DEVNULL)
        su("pkill -f '[g]etevent -l'")  # Termux 沒有權限直接殺 root 開的子行程（會丟 PermissionError），改用 root 結束它
    return 0


if __name__ == "__main__":
    sys.exit(main())
