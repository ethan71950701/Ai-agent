"""agent2.py — Claude 版自動迴圈：一次呼叫同時決定「做什麼」和「點哪裡」

用法: python ~/agent2.py "進入設定裡的Wi-Fi頁面" [最大步數，預設 8]
環境變數 AGENT_WAIT: 開始前等待秒數，預設 0（直接截圖；畫面若是 Termux，提示詞會要求它用 open/home 離開）。

狀態（2026-10-17，使用者手機實測）:
  已驗證: tap、swipe(左滑)、type(剪貼簿貼上，網址列輸入 hello world 成功)、任務含敏感字時的確認框、
          done 結束、升級到 Opus 5.5 並完成任務（AGENT_TEST_ESCALATE=1 測過，沒送 thinking 參數也沒 400）、
          AGENT_DEBUG 存每步畫面。
  未驗證: 點擊層級的確認框（要點「送出/登入…」時）、enter 動作、scroll 動作、ask 動作；
          Opus 的 thinking/effort 參數（第三方資料稱 Opus 5.5 思考一律開啟，輸出 token 含思考會偏多）。
動作: tap / scroll / swipe / type / enter / open(用指令開App) / cmd(白名單指令) / back / home / ask / done / fail
  （open、cmd 為 2026-10-17 新增，未在手機實測；open 會檢查套件已安裝、禁止開應用商店）
環境變數: AGENT_NOTIFY=0(關掉常駐通知)、AGENT_TOUCHES=1(開系統「顯示點觸」，未驗證注入點擊會不會顯示)、AGENT_WAIT(開始前等待秒數)、AGENT_DEBUG=1(每步畫面存到 /sdcard/Download/agent_step_N.png，
          含畫面內容，用完要刪)、AGENT_TEST_ESCALATE=1(測試用：強制第一個模型回答格式錯誤以觸發升級)。
踩過的坑: 執行途中不要切換 App，agent 會照眼前的畫面做（曾因使用者切到 Claude App 而在第 4 步回 fail）。

設計重點：
- 預設用 MODELS[0]（Sonnet 5.5）。卡住（畫面沒變 / 格式錯 / 模型說 fail / 連點同位置）
  就升級到 MODELS[1]（Opus 5.5）重試，且整個任務最多升級 MAX_ESC 次，並把卡住原因寫進歷史。
- 因為推論在雲端，不需要像 path.py 那樣把 Termux 切到前景。
- 保護：任務含敏感字眼（付款/轉帳/刪除/密碼…）時不直接拒絕，改跳確認框讓使用者決定，之後 type/enter 也會再問；
  要點的東西含「送出/登入/安裝…」或模型回 ask 時也跳確認框（termux-dialog，沒回應=不同意）；
  最多步數；畫面連續沒變；連點同位置；結束刪截圖。確認是否成立取決於模型回報的 what，不是萬無一失。
- type 用剪貼簿貼上（不受目前鍵盤語言影響；input text 在中文鍵盤會打出注音），貼完盡量還原原本剪貼簿。
- 升級的那一步做完就降回 MODELS[0]（不一直用貴的）；整個任務最多升級 MAX_ESC=2 次。
- 動作後等待秒數由模型用 "wait" 欄位建議（夾在 0.5~6 秒，沒給用 1.5）。畫面看起來沒變時，先多等 2 秒再拍一次才算「沒變」，
  避免等太短被誤判卡住。（以上兩點 2026-10-17 新增，尚未在手機上實測）
- 升級不吃掉步數（STATS['esc'] 記錄升級次數，迴圈多留空間）。
- Sonnet 5.5 用 thinking={"type":"between_tools"} 關掉前置思考（已驗證可用，省錢）。
  注意：Sonnet 5.5 送 {"type":"disabled"} 會 400。Opus 的 thinking/effort 參數行為「未驗證」，
  所以對 Opus 不送 thinking 參數。
- 圖片寬度 540（Claude 找小圖示時 540 比 360 準：540 為 9/9，360 為 9/11）。
"""
import json, os, re, sys, time, base64, hashlib, subprocess, urllib.request, urllib.error, shlex

HOME = os.path.expanduser("~")
KEY = open(f"{HOME}/.claude_key").read().strip()
MODELS = ["claude-sonnet-5-5", "claude-opus-5-5"]  # 先用第一個，卡住才升級到第二個
WIDTH = 540
SHOT = f"{HOME}/s.png"
SMALL = f"{HOME}/s_small.png"
TASK_BLOCK = ["付款", "轉帳", "匯款", "購買", "支付", "刪除", "密碼", "下單"]
CONFIRM = TASK_BLOCK + ["送出", "傳送", "發送", "登入", "提交", "同意", "安裝", "下載", "授權"]
STATS = {"in": 0, "out": 0, "esc": 0}
MAX_ESC = 2  # 整個任務最多升級幾次（升級的那步做完會降回第一個模型，所以可能再升級）
DEFAULT_WAIT, MIN_WAIT, MAX_WAIT = 1.5, 0.5, 6.0  # 動作後等多久再截圖（秒）；模型可用 wait 欄位建議


# 模型可以直接下的白名單指令（免點畫面、免費、快）。只能選這張表，不能自創指令。
CMDS = {
    "wifi_on": "svc wifi enable", "wifi_off": "svc wifi disable",
    "bluetooth_on": "svc bluetooth enable", "bluetooth_off": "svc bluetooth disable",
    "volume_up": "input keyevent 24", "volume_down": "input keyevent 25",
}
NO_OPEN = ("com.android.vending",)  # 應用商店：用 open 開之前一定先跳確認框
INSTALL_WORDS = ["下載", "安裝", "Google Play", "google play", "應用商店", "Play商店", "play商店"]


def su(cmd):
    return subprocess.run(["su", "-c", cmd], capture_output=True, text=True).stdout


NOTIF_ID = "7195"
STATE = {"title": "AI 正在操作手機", "show": os.environ.get("AGENT_NOTIFY", "1") != "0"}


def status(text):
    """更新「正在執行」的常駐通知（termux-notification，同一個 id 會覆蓋舊的）。
    有「停止」按鈕：按了會結束 agent2.py。背景送出，不拖慢主流程。
    注意：這是一般常駐通知；三星 Now Bar 只顯示支援 Live Updates 的 App，termux-notification 不一定會顯示在那裡（未驗證）。"""
    print("[狀態]", text)
    if not STATE["show"]:
        return
    try:
        subprocess.Popen(["termux-notification", "--id", NOTIF_ID, "--title", STATE["title"], "--content", text,
                          "--ongoing", "--alert-once", "--priority", "low",
                          "--button1", "停止", "--button1-action",
                          "pkill -f agent2.py; termux-notification-remove " + NOTIF_ID],
                         stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    except OSError:
        STATE["show"] = False


def describe(act):
    """把動作轉成給人看的一句話（輸入的文字內容不放進通知，避免鎖定畫面洩漏，只顯示字數）。"""
    a = act.get("action")
    if a == "tap":
        return "點擊「%s」" % act.get("what", "")
    if a == "scroll":
        return "往下捲動" if act.get("dir") == "down" else "往上捲動"
    if a == "swipe":
        return "滑動"
    if a == "type":
        return "輸入文字（%d 字）" % len(str(act.get("text", "")))
    if a == "enter":
        return "按 Enter"
    if a == "open":
        return "開啟 " + str(act.get("pkg", ""))
    if a == "cmd":
        return "執行 " + str(act.get("name", ""))
    if a == "ask":
        return "等你確認"
    return {"back": "按返回", "home": "回主畫面", "done": "完成", "fail": "無法完成"}.get(a, str(a))


def say(t):
    print("->", t)
    subprocess.run(["termux-tts-speak", "-s", "MUSIC", t], stderr=subprocess.DEVNULL)
    subprocess.run(["termux-notification", "--id", "7196", "--title", "AI 助手", "--content", t],
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def confirm(msg):
    r = subprocess.run(["termux-dialog", "confirm", "-t", "需要你確認", "-i", msg],
                       capture_output=True, text=True)
    # 除錯：印出確認框原始回應，方便判斷「按了是卻被當成否」是框沒回應、解析失敗還是模型自己拒絕
    print("[確認框] 回傳碼=%s 輸出=%r 錯誤=%r" % (r.returncode, r.stdout.strip()[:100], r.stderr.strip()[:100]))
    try:
        return str(json.loads(r.stdout).get("text", "")).strip().lower() in ("yes", "是")
    except Exception:
        return False


def shot():
    su(f"screencap -p {SHOT}; chown {os.getuid()}:{os.getgid()} {SHOT}")
    subprocess.run(["magick", SHOT, "-resize", f"{WIDTH}x", SMALL])
    r = subprocess.run(["identify", "-format", "%w %h", SHOT], capture_output=True, text=True)
    w, h = r.stdout.split()
    return int(w), int(h), hashlib.md5(open(SMALL, "rb").read()).hexdigest()


def type_text(text):
    """輸入文字到已選取的輸入框。優先用剪貼簿貼上（不受目前鍵盤語言影響，中英文都行，已驗證）；
    剪貼簿失敗且是純英數，才退回 input text（會受目前鍵盤影響，中文鍵盤會打出注音）。
    貼完會盡量還原原本的剪貼簿內容。"""
    if not text:
        return False
    old = subprocess.run(["termux-clipboard-get"], capture_output=True, text=True).stdout
    r = subprocess.run(["termux-clipboard-set", text], capture_output=True, text=True)
    if r.returncode == 0:
        su("input keyevent 279")  # KEYCODE_PASTE
        time.sleep(1)
        subprocess.run(["termux-clipboard-set"], input=old, text=True)
        return True
    if text.isascii() and "%" not in text and "\n" not in text:
        su("input text " + shlex.quote(text.replace(" ", "%s")))
        return True
    return False


def open_app(pkg):
    """用 root 直接開 App（比點桌面圖示快）。套件必須已安裝；回傳 (成功?, 訊息)。"""
    if not re.fullmatch(r"[A-Za-z0-9_.]+", pkg or ""):
        return False, "不允許的套件名稱"
    if ("package:" + pkg) not in su("pm list packages " + pkg).split():
        return False, "沒有安裝 " + pkg
    out = su("monkey -p %s -c android.intent.category.LAUNCHER 1 2>&1" % pkg)
    if "No activities found" in out:
        return False, pkg + " 沒有可開啟的畫面"
    return True, "已開啟 " + pkg


def make_prompt(task, hist):
    h = "；".join(hist[-8:]) if hist else "無"
    return ("你在操作一支Android手機，圖片是目前畫面。任務：" + task + "。已執行的動作：" + h + "。\n"
        "請決定下一步，只輸出一行標準JSON，不要任何解釋。遇到敏感或不可逆的事（付款、轉帳、刪除、安裝或下載App、傳送訊息、登入、輸入密碼/驗證碼/卡號、授權權限等），不要拒絕，也不要回fail，一律改用ask，由使用者決定或親自動手。App沒安裝時，可以用ask詢問使用者要不要去應用商店安裝。若畫面是Termux終端機，那是執行本程式的地方，請直接用open開需要的App或按home，不要操作Termux。可用動作：\n"
        '{"action":"tap","x":整數,"y":整數,"what":"點的是什麼"}，x和y是0到1000的相對位置\n'
        '{"action":"scroll","dir":"down或up"}，down代表往下看更多內容\n'
        '{"action":"swipe","x1":整數,"y1":整數,"x2":整數,"y2":整數}，手指從(x1,y1)滑到(x2,y2)，座標0到1000，用於左右滑動或自訂滑動\n'
        '{"action":"type","text":"要輸入的文字"}，在已選取的輸入框輸入文字，輸入前要先點一下輸入框；密碼、驗證碼、卡號不要自己輸入，改用ask（manual設true）請使用者自己輸入；不要點系統跳出的「貼上」浮動按鈕或鍵盤上的剪貼簿建議，輸入文字只用type動作\n'
        '{"action":"enter"}，按下Enter送出輸入框內容\n'
        '{"action":"open","pkg":"App套件名稱"}，用指令直接開啟已安裝的App，比在桌面找圖示快；常見：Chrome=com.android.chrome、設定=com.android.settings、Instagram=com.instagram.android、LINE=jp.naver.line.android、Spotify=com.spotify.music、Samsung相機=com.sec.android.app.camera；不確定套件名稱就改用畫面點擊\n'
        '{"action":"cmd","name":"wifi_on|wifi_off|bluetooth_on|bluetooth_off|volume_up|volume_down"}，直接開關Wi-Fi、藍牙或調音量，不用點畫面\n'
        '能用open或cmd做的事優先用它們，其餘才用畫面操作。\n'
        '{"action":"back"}、{"action":"home"}\n'
        '{"action":"ask","why":"原因","manual":false}，下一步涉及付款、轉帳、刪除、安裝App、傳送訊息、登入或其他不可逆/敏感操作時用，why要寫清楚要做什麼；需要使用者自己動手（輸入密碼、驗證碼、指紋、授權）時manual設true，使用者做完會按是，之後你再看畫面繼續\n'
        '{"action":"done","say":"要告訴使用者的話"}，只有確定任務已完成才用\n'
        '{"action":"fail","say":"原因"}，真的做不到時用（不是因為內容敏感）\n'
        '除了done和fail，每個動作都可以多加一個"wait":秒數（0.5到6），代表做完這個動作後，畫面大概要多久才會穩定，我才會截下一張圖。'
        '例如點選單項目或捲動約1，開啟App約3，網頁載入約4，輸入文字約1；不確定就省略。')


def ask_claude(task, hist, model):
    img = base64.b64encode(open(SMALL, "rb").read()).decode()
    body = {"model": model, "max_tokens": 300,
            "messages": [{"role": "user", "content": [
                {"type": "image", "source": {"type": "base64", "media_type": "image/png", "data": img}},
                {"type": "text", "text": make_prompt(task, hist)}]}]}
    if "sonnet" in model:
        body["thinking"] = {"type": "between_tools"}
    req = urllib.request.Request("https://api.anthropic.com/v1/messages",
        data=json.dumps(body).encode(),
        headers={"x-api-key": KEY, "anthropic-version": "2023-06-01",
                 "content-type": "application/json"})
    try:
        r = json.loads(urllib.request.urlopen(req, timeout=60).read())
        out = "".join(b.get("text", "") for b in r["content"] if b.get("type") == "text")
        return out, r["usage"]
    except urllib.error.HTTPError as e:
        return None, "HTTP %s %s" % (e.code, e.read().decode()[:200])
    except Exception as e:
        return None, str(e)


def parse(out):
    objs = re.findall(r"\{[^{}]*\}", out)
    if not objs:
        return None
    try:
        return json.loads(objs[-1])
    except Exception:
        return None


def can_escalate(level, why, hist):
    if level >= len(MODELS) - 1 or STATS["esc"] >= MAX_ESC:
        return False
    STATS["esc"] += 1
    hist.append("上一步卡住（" + why + "），請換個方法，不要重複剛才的動作")
    print("卡住（%s），升級為 %s 再試一次" % (why, MODELS[level + 1]))
    return True


def main():
    if len(sys.argv) < 2:
        print('用法: python ~/agent2.py "任務" [最大步數]')
        return
    task = sys.argv[1]
    maxsteps = int(sys.argv[2]) if len(sys.argv) > 2 else 8
    sensitive_task = False
    if any(w in task for w in INSTALL_WORDS):
        if not confirm("任務要下載或安裝 App（會進入應用商店）。要繼續嗎？按安裝前還會再問你一次。"):
            say("已取消")
            return
    if any(b in task for b in TASK_BLOCK):
        if not confirm("任務含敏感字眼（付款、轉帳、刪除、密碼等）。要繼續嗎？之後輸入文字和敏感步驟還會再問你。"):
            say("已取消")
            return
        sensitive_task = True
    wait = float(os.environ.get("AGENT_WAIT", "0"))
    if wait >= 3:
        print("%d 秒內切到起始畫面..." % wait)
    time.sleep(wait)
    level = 0
    hist, last_hash, same, last_tap = [], None, 0, None
    for i in range(1, maxsteps + 3):
        if i - STATS["esc"] > maxsteps:
            break
        print("\n=== 第 %d/%d 步（模型: %s）===" % (i - STATS["esc"], maxsteps, MODELS[level]))
        status("第 %d/%d 步 · 看畫面中（%s）" % (i - STATS["esc"], maxsteps, "Opus" if "opus" in MODELS[level] else "Sonnet"))
        try:
            w, h, hh = shot()
        except Exception as e:
            print("截圖失敗:", e)
            return
        if os.environ.get("AGENT_DEBUG"):
            su("cp %s /sdcard/Download/agent_step_%d.png" % (SMALL, i))
        if hh == last_hash:  # 可能只是畫面還沒更新完：多等一下再拍一次，才算沒變
            time.sleep(2)
            w, h, hh = shot()
        same = same + 1 if hh == last_hash else 0
        last_hash = hh
        if same >= 2:
            if can_escalate(level, "畫面沒有變化", hist):
                level += 1
                same = 0
                continue
            say("畫面一直沒變，停止")
            return
        t0 = time.time()
        out, info = ask_claude(task, hist, MODELS[level])
        if out is None:
            print("呼叫失敗:", info)
            say("連線失敗")
            return
        STATS["in"] += info["input_tokens"]
        STATS["out"] += info["output_tokens"]
        pi, po = (4, 20) if "opus" in MODELS[level] else (2, 10)  # 每百萬 token 美元（第三方價格）
        STATS["cost"] = STATS.get("cost", 0) + info["input_tokens"] * pi / 1e6 + info["output_tokens"] * po / 1e6
        print("Claude: %s (%.1f 秒, %d入/%d出)" % (out.strip(), time.time() - t0,
              info["input_tokens"], info["output_tokens"]))
        act = parse(out)
        if act and "action" in act:
            status("第 %d/%d 步 · %s" % (i - STATS["esc"], maxsteps, describe(act)))
        if os.environ.get("AGENT_TEST_ESCALATE") and level == 0:
            act = None  # 測試用
        if not act or "action" not in act:
            if can_escalate(level, "回答格式錯誤", hist):
                level += 1
                continue
            print("看不懂回答，停止")
            return
        a = act["action"]
        if a == "done":
            say(act.get("say", "完成了"))
            return
        if a == "fail":
            if can_escalate(level, "模型說做不到", hist):
                level += 1
                continue
            say(act.get("say", "我做不到"))
            return
        if a == "ask":
            why = str(act.get("why", ""))
            if act.get("manual") is True:
                if not confirm("需要你自己動手：" + why + "。做完按「是」繼續，不做按「否」取消。"):
                    say("已取消")
                    return
                hist.append("使用者已親自完成：" + why)
            else:
                if not confirm("下一步敏感或不可逆：" + why + "。要繼續嗎？"):
                    say("已取消")
                    return
                hist.append("使用者已同意：" + why)
            time.sleep(1)
            continue
        try:
            if a == "tap":
                what = str(act.get("what", ""))
                if any(b in what for b in CONFIRM):
                    if not confirm("要點擊「" + what + "」，確定嗎？"):
                        say("已取消")
                        return
                px = min(max(int(float(act["x"]) * w / 1000), 0), w - 1)
                py = min(max(int(float(act["y"]) * h / 1000), 0), h - 1)
                if last_tap == (px, py):
                    if can_escalate(level, "連續點同一位置", hist):
                        level += 1
                        last_tap = None
                        continue
                    say("連續點同一位置，停止")
                    return
                last_tap = (px, py)
                print("點擊 (%d,%d) %s" % (px, py, what))
                su("input tap %d %d" % (px, py))
                hist.append("點了" + what)
            elif a == "scroll":
                down = act.get("dir") == "down"
                y1, y2 = (int(h * 0.7), int(h * 0.3)) if down else (int(h * 0.3), int(h * 0.7))
                su("input swipe %d %d %d %d 300" % (w // 2, y1, w // 2, y2))
                hist.append("往下捲動" if down else "往上捲動")
            elif a == "swipe":
                pts = [float(act[k]) for k in ("x1", "y1", "x2", "y2")]
                x1, x2 = [min(max(int(pts[i] * w / 1000), 0), w - 1) for i in (0, 2)]
                y1, y2 = [min(max(int(pts[i] * h / 1000), 0), h - 1) for i in (1, 3)]
                su("input swipe %d %d %d %d 300" % (x1, y1, x2, y2))
                hist.append("滑動")
            elif a == "type":
                text = str(act.get("text", ""))
                if sensitive_task or any(b in text for b in CONFIRM):
                    if not confirm("要輸入文字：「" + text + "」，確定嗎？"):
                        say("已取消")
                        return
                ok = type_text(text)
                print("輸入文字:", text, "(成功)" if ok else "(失敗)")
                hist.append("輸入了「" + text + "」" if ok else "輸入文字失敗")
            elif a == "enter":
                if sensitive_task and not confirm("要按 Enter 送出，確定嗎？"):
                    say("已取消")
                    return
                su("input keyevent 66")
                hist.append("按了Enter")
            elif a == "open":
                if str(act.get("pkg", "")) in NO_OPEN and not confirm("要開啟應用商店（可能會安裝 App），確定嗎？"):
                    say("已取消")
                    return
                ok, msg = open_app(str(act.get("pkg", "")))
                print("開啟App:", msg)
                hist.append(msg if ok else "開App失敗（" + msg + "），改用畫面操作")
                last_tap = None
            elif a == "cmd":
                c = CMDS.get(str(act.get("name", "")))
                if not c:
                    print("不在白名單的指令:", act.get("name"))
                    hist.append("指令不在白名單，改用畫面操作")
                else:
                    su(c)
                    print("執行指令:", act.get("name"))
                    hist.append("用指令執行了" + str(act["name"]))
            elif a == "back":
                su("input keyevent 4")
                hist.append("按了返回")
            elif a == "home":
                su("input keyevent 3")
                hist.append("回主畫面")
            else:
                print("未知動作:", a)
                return
        except (KeyError, ValueError) as e:
            print("動作資料不完整:", e)
            return
        try:
            ws = min(max(float(act.get("wait", 3.0 if a == "open" else DEFAULT_WAIT)), MIN_WAIT), MAX_WAIT)
        except (TypeError, ValueError):
            ws = DEFAULT_WAIT
        print("等 %.1f 秒再截圖" % ws)
        time.sleep(ws)
        if level > 0:  # 貴的模型只負責解開卡住的這一步，做完就換回便宜的
            print("這步做完了，切回 %s" % MODELS[0])
            level = 0
            same = 0
    say("達到最大步數，停止")


import signal
signal.signal(signal.SIGTERM, lambda *_: sys.exit(130))  # 通知上的「停止」用 pkill：轉成正常結束，才會刪截圖、解除喚醒鎖、移除通知
subprocess.run(["termux-wake-lock"])
# 選用：AGENT_TOUCHES=1 時打開系統「顯示點觸位置」，結束後還原。注入的 input tap 會不會顯示圓點，未驗證。
_touch_old = None
if os.environ.get("AGENT_TOUCHES") == "1":
    _touch_old = (su("settings get system show_touches").strip() or "0")
    su("settings put system show_touches 1")
try:
    main()
finally:
    subprocess.run(["termux-wake-unlock"])
    subprocess.run(["termux-notification-remove", NOTIF_ID], stderr=subprocess.DEVNULL)
    if _touch_old is not None:
        su("settings put system show_touches " + _touch_old)
    for f in (SHOT, SMALL):
        try:
            os.remove(f)
        except OSError:
            pass
    cost = STATS.get("cost", 0)
    print("本次用量: %d 入 / %d 出，估計約 $%.4f（Sonnet 每百萬 $2/$10、Opus $4/$20，第三方價格估算）" % (STATS["in"], STATS["out"], cost))
