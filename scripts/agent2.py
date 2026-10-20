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
環境變數: AGENT_OVERLAY=0(不用 PhoneAI Overlay App；預設有裝就用：點擊漣漪 + Live Update 通知，未實測)、AGENT_NOTIFY=0(關掉常駐通知)、AGENT_TOUCHES=1(開系統「顯示點觸」，未驗證注入點擊會不會顯示)、AGENT_BG=1(**實驗**：改在背景的「第二螢幕」操作，不影響前台；缺少時會自動建立模擬螢幕，見 docs/ROADMAP_BACKGROUND.md)、AGENT_WAIT(開始前等待秒數)、AGENT_DEBUG=1(每步畫面存到 /sdcard/Download/agent_step_N.png，
          含畫面內容，用完要刪)、AGENT_TEST_ESCALATE=1(測試用：強制第一個模型回答格式錯誤以觸發升級)、AGENT_TEST_BG_FAIL=1(測試用：背景模式下假裝 App 不能在背景執行，測「退回前景並提醒」)。
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
import json, os, re, sys, time, base64, hashlib, struct, subprocess, threading, urllib.request, urllib.error, shlex
try:
    import applist  # ~/apps.tsv：App 顯示名稱 -> 套件名稱（PhoneAI Overlay App 匯出），open 用名稱查套件
except ImportError:
    applist = None
try:
    import memory  # ~/memory.json：這裡只用「經驗」（lesson）；使用者的待辦與筆記不會帶進操作手機的提示
except ImportError:
    memory = None
try:
    import intents  # ~/intents.py：命令層（白名單）。能用系統命令／網址／intent 做的事就不要點畫面
except ImportError:
    intents = None
try:
    from overlay import Overlay  # ~/overlay.py：PhoneAI Overlay App（漣漪 + Live Update 通知），沒有就照舊
except ImportError:
    Overlay = None

HOME = os.path.expanduser("~")
KEY = open(f"{HOME}/.claude_key").read().strip()
MODELS = ["claude-sonnet-5-5", "claude-opus-5-5"]  # 先用第一個，卡住才升級到第二個
if os.environ.get("AGENT_FIRST", "").lower() == "haiku":
    # 實驗開關（預設不開）：Haiku 5.5 當第一層（官方價 $0.10/$0.50 每百萬，約 Sonnet 的 1/20），卡住升 Sonnet，再卡升 Opus。
    # 能不能穩定看圖操作手機要實測，結果記在 docs/EXPERIMENTS.md。
    MODELS = ["claude-haiku-5-5", "claude-sonnet-5-5", "claude-opus-5-5"]
if os.environ.get("AGENT_TREE", "0") == "1" and os.environ.get("AGENT_TREE_MODEL", "").lower() == "haiku":
    # 元件清單模式的實驗：AI 只要選元件編號（文字選擇題），不用精準座標，先用 Haiku；卡住再升 Sonnet、Opus
    MODELS = ["claude-haiku-5-5", "claude-sonnet-5-5", "claude-opus-5-5"]
PRICES = {"haiku": (0.10, 0.50), "sonnet": (2, 10), "opus": (4, 20)}  # 每百萬 token 美元（官方價表；Haiku 是 100K token 以內的價）


def price(model):
    for k, v in PRICES.items():
        if k in model:
            return v
    return PRICES["sonnet"]


def short(model):
    return next((k.capitalize() for k in PRICES if k in model), model)


WIDTH = int(os.environ.get("AGENT_WIDTH", "432"))  # 截圖寬度；432 寬省約 25% 輸入 token，手電筒等小圖示實測仍認得出（2026-10-18）；要更準可設 540
SHOT = f"{HOME}/s.png"
SMALL = f"{HOME}/s_small.png"
TASK_BLOCK = ["付款", "轉帳", "匯款", "購買", "支付", "刪除", "密碼", "下單"]
CONFIRM = TASK_BLOCK + ["送出", "傳送", "發送", "登入", "提交", "同意", "安裝", "下載", "授權"]
STATS = {"in": 0, "out": 0, "esc": 0}
TASK_TEXT = [""]  # 給結尾回報用的任務文字（main 會填）
MAX_ESC = 3 if len(MODELS) > 2 else 2  # 整個任務最多升級幾次（升級的那步做完會降回第一個模型，所以可能再升級）
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
    """用 root 跑指令。收到停止訊號（SIGTERM）時，Termux 沒有權限殺掉 root 的 su 子行程，subprocess 會丟 PermissionError 蓋掉
    正常結束的流程（清理、移除通知都被跳過）；所以自己管子行程：中斷時盡力結束它，再把原本的例外丟出去。"""
    p = subprocess.Popen(["su", "-c", cmd], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    try:
        return p.communicate()[0]
    except BaseException:
        try:
            p.kill()
        except OSError:
            pass
        raise


BG = {"on": os.environ.get("AGENT_BG", "0") == "1", "id": None, "sf": None, "opened": set(), "vd": False}  # 背景模式：id=系統螢幕編號，sf=截圖用的 SurfaceFlinger 編號


def parse_overlay(display_dump, sf_dump):
    """從 dumpsys 輸出找模擬第二螢幕的編號。回傳 (系統編號, 截圖編號)，找不到的那個是 None。"""
    m = re.search(r"Display (\d+) com\.android\.server\.display\.OverlayDisplayAdapter", display_dump or "")
    t = re.search(r'Display (\d+) \(Virtual display\): displayName="[^"]*(?:重疊|[Oo]verlay)[^"]*"', sf_dump or "")
    return (m.group(1) if m else None, t.group(1) if t else None)


def clampi(v, lo, hi):
    return max(lo, min(hi, int(v)))


def build_gesture(act, w, h):
    """把 AI 的手勢動作（swipe / longpress / drag / multitouch）整理成 (手指清單, 按住毫秒)。座標 0~1000 換成像素，時間夾在合理範圍。
    手指 = {x1,y1,x2,y2,ms}；hold = 先按住再移動。看不懂回 None。"""
    def px(v, size):
        return clampi(float(v) * size / 1000, 0, size - 1)

    def finger(o, default_ms):
        return {"x1": px(o["x1"], w), "y1": px(o["y1"], h), "x2": px(o["x2"], w), "y2": px(o["y2"], h),
                "ms": clampi(o.get("ms", default_ms), 50, 5000)}
    a = act.get("action")
    try:
        if a == "swipe":
            return [finger(act, 300)], 0
        if a == "longpress":
            x, y = px(act["x"], w), px(act["y"], h)
            return [{"x1": x, "y1": y, "x2": x, "y2": y, "ms": clampi(act.get("ms", 800), 300, 5000)}], 0
        if a == "drag":
            return [finger(act, 500)], clampi(act.get("hold_ms", 600), 200, 5000)
        if a == "multitouch":
            fs = [finger(o, 400) for o in act["fingers"][:4]]
            return (fs, clampi(act.get("hold_ms", 0), 0, 5000)) if fs else None
    except (KeyError, TypeError, ValueError):
        return None
    return None


def gesture_fallback_cmd(fingers, hold):
    """App 的手勢功能用不了時，單指手勢退回 `input` 指令（做不到多指）。回傳 shell 指令或 None。"""
    if len(fingers) != 1:
        return None
    f = fingers[0]
    if hold and (f["x1"], f["y1"]) != (f["x2"], f["y2"]):
        return inp("draganddrop %d %d %d %d %d" % (f["x1"], f["y1"], f["x2"], f["y2"], f["ms"] + hold))
    return inp("swipe %d %d %d %d %d" % (f["x1"], f["y1"], f["x2"], f["y2"], f["ms"] + hold))


PRE_APP = []  # 這次先開的 App 套件（供結束時存經驗用）
LESSONS = []  # 這次任務帶進提示的經驗（main 開始時依要開的 App 載入）
MAX_REMEMBER = 3  # 一個任務最多即時記幾則經驗（實測：AI 每步都在記，同樣的意思換句話說存了十幾則）
MAX_MEASURES = 4  # 一個任務最多幾次「分段拖曳量測」（drag_start）
DRAG = {"down": False, "x": 0, "y": 0}  # 「拖到一半」的手指狀態：down = 手指現在按在螢幕上沒放開


def drag_script(op, cur, target=None, step_px=90):
    """拖曳到一半可以截圖的手勢（用 `input motionevent`：每個事件各自送出，手指在事件之間一直按著）。
    op: start（在 cur 按下、停一下再移到 target，不放開）/ move（從 cur 移到 target，不放開）/ end（可先移到 target，再放開）/ cancel（取消）。
    座標是像素。回傳 (shell 指令字串, 手指最後的位置)。移動拆成每約 step_px 一格的 MOVE 事件，遊戲才看得到「拖著走」。"""
    def moves(p, q):
        n = clampi(max(abs(q[0] - p[0]), abs(q[1] - p[1])) / float(step_px), 1, 10)
        return ["MOVE %d %d" % (p[0] + (q[0] - p[0]) * k // n, p[1] + (q[1] - p[1]) * k // n) for k in range(1, n + 1)]
    evs, end = [], cur
    if op == "start":
        evs = ["DOWN %d %d" % cur, "SLEEP 0.35"] + (moves(cur, target) if target else [])
        end = target or cur
    elif op == "move":
        evs, end = moves(cur, target), target
    elif op == "end":
        evs = (moves(cur, target) if target and target != cur else []) + ["UP %d %d" % (target or cur)]
        end = target or cur
    elif op == "cancel":
        evs = ["CANCEL %d %d" % cur]
    parts = []
    for e in evs:
        parts.append(e.replace("SLEEP ", "sleep ") if e.startswith("SLEEP") else inp("motionevent " + e))
    return "; sleep 0.04; ".join(parts), end


def release_drag(hist=None, why="換了別的動作"):
    """拖到一半卻要做別的事（或任務結束）：先把手指放開，免得手指一直按在螢幕上。"""
    if DRAG["down"]:
        su(drag_script("end", (DRAG["x"], DRAG["y"]))[0])
        DRAG["down"] = False
        if hist is not None:
            hist.append("拖曳到一半，因為" + why + "已放開手指")


def inp(args):
    """組 input 指令；背景模式要指定螢幕（input -d 編號）。"""
    return ("input -d %s %s" % (BG["id"], args)) if BG["on"] and BG["id"] else "input " + args


BG_TASKBAR_PX = 126  # 第二螢幕底下 DeX 工具列的高度（48dp @420dpi 換算，估算值）；視窗拉大時留給它


def parse_tasks(dump, display_id):
    """解析 `am stack list`：回傳 ([(taskId, 套件, (左,上,右,下)), ...], (螢幕寬, 螢幕高) 或 None)，只含指定螢幕的任務。"""
    tasks, size = [], None
    for blk in re.split(r"RootTask id=", dump or "")[1:]:
        m = re.match(r"\d+ bounds=\[(\d+),(\d+)\]\[(\d+),(\d+)\] displayId=(\d+)", blk)
        if not m or m.group(5) != str(display_id):
            continue
        size = (int(m.group(3)), int(m.group(4)))
        for t in re.finditer(r"taskId=(\d+): ([\w.]+)/\S+ bounds=\[(\d+),(\d+)\]\[(\d+),(\d+)\]", blk):
            tasks.append((int(t.group(1)), t.group(2), tuple(int(t.group(i)) for i in range(3, 7))))
    return tasks, size


def parse_roots(dump, display_id):
    """解析 `am stack list`：回傳指定螢幕上的 [(RootTask 編號, [套件, ...]), ...]。"""
    roots = []
    for blk in re.split(r"RootTask id=", dump or "")[1:]:
        m = re.match(r"(\d+) bounds=\[\d+,\d+\]\[\d+,\d+\] displayId=(\d+)", blk)
        if m and m.group(2) == str(display_id):
            roots.append((int(m.group(1)), re.findall(r"taskId=\d+: ([\w.]+)/", blk)))
    return roots


def bg_cleanup():
    """任務結束後把「這次在第二螢幕開的 App」關掉，免得一直堆在背景（使用者碰不到第二螢幕，沒辦法手動關）。
    先用 am stack remove 移除那個 RootTask；沒移掉、且這個 App 沒有在主螢幕使用時，才 force-stop（不影響主螢幕上的同一個 App）。
    AGENT_BG_KEEP=1 可保留（想看結果時）。"""
    if BG["vd"]:
        vd_stop()  # 虛擬螢幕整個收掉，上面的 App 一起消失
        return
    if not (BG["on"] and BG["id"] and BG["opened"]) or os.environ.get("AGENT_BG_KEEP", "0") == "1":
        return
    closed = []
    for rid, pkgs in parse_roots(su("am stack list"), BG["id"]):
        if any(p in BG["opened"] for p in pkgs):
            su("am stack remove %d" % rid)
            closed.append(rid)
    dump = su("am stack list")
    left = {t[1] for t in parse_tasks(dump, BG["id"])[0]} & BG["opened"]
    on_main = {t[1] for t in parse_tasks(dump, 0)[0]}
    for pkg in sorted(left - on_main):
        su("am force-stop " + pkg)
        print("背景模式：stack remove 沒移掉，改用 force-stop " + pkg)
    if left - on_main:
        print("背景模式：已關閉第二螢幕上的 App 視窗")
    for pkg in sorted(left & on_main):
        print("背景模式：%s 還留在第二螢幕（主螢幕也在用它，不強制關閉）" % pkg)
    if closed and not left:
        print("背景模式：已關閉第二螢幕上的 App 視窗")  # 實測 stack remove 常沒效，之後 force-stop 補上；這行只在確定清乾淨時才印


def wait_user_control(ov, hist, sleep=time.sleep, max_wait=600):
    """使用者正在接手背景螢幕（在預覽上親自操作）時，AI 暫停；接手結束後在歷史裡註明，讓 AI 以新畫面為準。
    回傳「等了幾次」（測試用）。ov 是 Overlay（連不上就不用等）。"""
    if not (ov and getattr(ov, "ok", False)):
        return 0
    waited = 0
    while waited < max_wait:
        r = ov.paused()
        if r == "yes":
            if waited == 0:
                print("使用者正在接手背景螢幕，AI 暫停中…")
            waited += 1
            sleep(1)
            continue
        if r == "done" or waited:
            hist.append("使用者剛剛親自操作過這個畫面，現況以新畫面為準，不要照之前的計畫繼續")
        break
    return waited


def alert(text):
    """提醒使用者（通知 + 震動 + 語音）。不動 STATS["say"]（那是最後的結果訊息）。"""
    subprocess.run(["termux-notification", "--id", "7197", "--title", "AI 助手", "--content", text, "--vibrate", "200,100,200"],
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    subprocess.run(["termux-tts-speak", "-s", "MUSIC", text], stderr=subprocess.DEVNULL)


def is_black_image(path):
    """整張幾乎純黑（平均亮度與變異都很小）。ImageMagick 出錯就當作不是黑的，不誤判。"""
    try:
        out = subprocess.run(["magick", path, "-format", "%[fx:mean] %[fx:standard_deviation]", "info:"],
                             capture_output=True, text=True).stdout.split()
        return float(out[0]) < 0.02 and float(out[1]) < 0.01
    except (OSError, ValueError, IndexError):
        return False


def bg_app_ok(pkg):
    """背景螢幕上這個 App 有沒有真的跑起來：任務還在、畫面不是純黑（連續兩次都黑才算）。回傳 (好不好, 原因)。"""
    time.sleep(2.0)  # 有些 App 開了就自己關掉，等一下才看得出來
    tasks, _ = parse_tasks(su("am stack list"), BG["id"])
    if not any(t[1] == pkg for t in tasks):
        return False, "App 一開就消失了"
    for _ in range(2):
        shot()
        if not is_black_image(SMALL):
            return True, ""
        time.sleep(2.0)
    return False, "背景螢幕一直是黑的"


def bg_fallback(pkg, reason):
    """這個 App 在背景螢幕上不能用：提醒使用者、收掉背景螢幕、之後改在主螢幕（前景）執行。"""
    msg = "這個 App 在背景不能用（%s），改在前景執行" % reason
    print("背景模式：" + msg)
    alert(msg)
    bg_cleanup()  # 先收掉背景螢幕（隱形螢幕整個收；系統模擬螢幕則關掉開過的 App）
    BG.update(on=False, vd=False, id=None, sf=None, ripple=None)
    BG["opened"].clear()


def maximize_bg(pkg):
    """把第二螢幕上這個 App 的小浮動視窗拉到幾乎全螢幕（am task resize，已在手機驗證：視窗變大且內容會重新排版）。成功回 True。"""
    if os.environ.get("AGENT_BG_MAX", "1") == "0" or BG["vd"]:  # 隱形虛擬螢幕上 App 本來就是全螢幕
        return False
    tasks, size = parse_tasks(su("am stack list"), BG["id"])
    mine = [t for t in tasks if t[1] == pkg]
    if not mine or not size:
        return False
    tid = max(t[0] for t in mine)  # 同一個 App 有多個任務時取最新的（編號最大）
    su("am task resize %d 0 0 %d %d" % (tid, size[0], size[1] - BG_TASKBAR_PX))
    print("背景模式：把視窗拉大（task %d → %dx%d）" % (tid, size[0], size[1] - BG_TASKBAR_PX))
    return True


def fx_display():
    """漣漪要畫在哪個螢幕：一般模式 0（主螢幕）；背景模式是第二螢幕編號，但舊版 App 看不懂 display 參數
    （會把漣漪畫在主螢幕錯的位置），所以要確認 App 診斷有 bgripple=1 才畫，否則回 None（不畫）。"""
    if not BG["on"]:
        return 0
    if BG.get("ripple") is None:
        BG["ripple"] = bool(OV and OV.ok and "bgripple=1" in (OV.check() or ""))
        if not BG["ripple"]:
            print("背景模式：App 版本太舊或連不上，不畫第二螢幕的漣漪（更新 APK 後才有）")
    return int(BG["id"]) if BG["ripple"] and BG["id"] else None


VD_JSON = "/data/local/tmp/vdisplay.json"
VD_STOP = "/data/local/tmp/vdisplay.stop"


# 背景螢幕的 寬x高/dpi；比手機（1080x2340）矮一點，預覽視窗看起來比較舒服。
# 720x1200/280 與原本 1080x1800/420 版面相同（dp 一樣）但像素少 55%：背景 App 的繪製與合成都輕很多（直接顯示實測 1080x1800 只有 21～30 fps）；
# AI 看的截圖本來就縮到 432 寬，預覽窗也只有 400～650 像素寬，解析度夠用。要回舊的設 AGENT_BG_SIZE=1080x1800/420
BG_SIZE = os.environ.get("AGENT_BG_SIZE", "720x1200/280")


def parse_vd_size(text):
    """vdisplay.py --serve 寫的 JSON 裡的 (寬, 高)；沒有就回預設。"""
    try:
        d = json.loads(text)
        return int(d["w"]), int(d["h"])
    except (ValueError, KeyError, TypeError):
        return 1080, 2340


def parse_vd_json(text):
    """vdisplay.py --serve 寫的 JSON → (系統螢幕編號字串, 截圖編號字串)，格式不對回 (None, None)。"""
    try:
        d = json.loads(text)
        return str(int(d["id"])), str(int(d["sf"]))
    except (ValueError, KeyError, TypeError):
        return None, None


def vd_start():
    """建立「沒有視窗」的隱形虛擬螢幕（vdisplay.py --serve，root）。成功回 True。
    優先用 App 的「直接顯示」（DirectDisplay，不需要 scrcpy）；失敗才用 ~/scrcpy-server.jar。AGENT_BG_VD=0 可強制改用系統模擬螢幕。"""
    if os.environ.get("AGENT_BG_VD", "1") == "0":
        return False
    su("pkill -f '[v]display.py --serve'; pkill -f '[c]om.genymobile.scrcpy.Server'; pkill -f '[c]om.phoneai.overlay.DirectDisplay'; rm -f %s %s" % (VD_JSON, VD_STOP))
    subprocess.Popen([sys.executable, os.path.expanduser("~/vdisplay.py"), "--serve", "--size=" + BG_SIZE], start_new_session=True,
                     stdout=open(os.path.expanduser("~/vdisplay.log"), "w"), stderr=subprocess.STDOUT)
    for _ in range(40):  # 最多等約 15 秒
        time.sleep(0.4)
        raw = su("cat " + VD_JSON)
        BG["id"], BG["sf"] = parse_vd_json(raw)
        if BG["id"] and BG["sf"]:
            BG["vd"] = True
            BG["w"], BG["h"] = parse_vd_size(raw)
            try:
                BG["sf_before"] = json.loads(raw).get("sf_before")  # 直接顯示才有：建立前就存在的虛擬螢幕（截圖編號變了時用來重新找）
            except ValueError:
                BG["sf_before"] = None
            print("背景模式：隱形虛擬螢幕 id=%s，截圖編號=%s（沒有視窗）" % (BG["id"], BG["sf"]))
            if OV and OV.ok:
                OV.vd(BG["sf"], BG.get("w", 1080), BG.get("h", 2340), BG["id"])  # App 的 Now Bar 就會出現「點一下顯示預覽」；id 讓使用者能接手
            return True
    print("背景模式：隱形虛擬螢幕建立失敗（看 ~/vdisplay.log），改用系統模擬螢幕")
    return False


def vd_stop():
    """通知 vdisplay.py 收掉虛擬螢幕（它關掉伺服器，上面的 App 跟著消失）；2 秒後還在就強制結束。"""
    if OV and OV.ok:
        OV.vd(None)  # 通知 App：背景螢幕要消失了（預覽視窗跟著關）
    su("touch " + VD_STOP)
    time.sleep(2)
    su("pkill -f '[v]display.py --serve'; pkill -f '[c]om.genymobile.scrcpy.Server'; pkill -f '[c]om.phoneai.overlay.DirectDisplay'; rm -f %s %s" % (VD_JSON, VD_STOP))


def bg_setup():
    """背景模式：找（或建立）模擬第二螢幕。成功回 True。這是實驗功能，已在手機驗證：截圖 screencap -d、操作 input -d 可行。"""
    if not BG["on"]:
        return True
    if vd_start():
        return True
    if os.environ.get("AGENT_BG_OVERLAY") != "1":
        # 實測（小米 13 / Android 17）：退回「系統模擬螢幕」（overlay_display_devices）會被系統鏡像主畫面，視窗裡又有視窗，無限重疊、重開機也不會消失。
        # 所以預設不再自動建模擬螢幕；真的想用要自己加 AGENT_BG_OVERLAY=1。
        say("背景模式需要 ~/scrcpy-server.jar 和能建立隱形螢幕的系統；目前建立失敗，這次沒有執行。請先關掉背景模式，或下載 scrcpy 檔案（python ~/selftest.py 會告訴你指令）")
        return False
    for attempt in (0, 1):
        BG["id"], BG["sf"] = parse_overlay(su("dumpsys display"), su("dumpsys SurfaceFlinger --display-id"))
        if BG["id"] and BG["sf"]:
            print("背景模式：第二螢幕 id=%s，截圖編號=%s" % (BG["id"], BG["sf"]))
            return True
        if attempt == 0:
            print("背景模式：還沒有第二螢幕，建立模擬螢幕（settings put global overlay_display_devices）")
            su('settings put global overlay_display_devices "1080x2340/420"')
            time.sleep(3)
    print("背景模式：找不到第二螢幕，停止。可手動執行：su -c 'settings delete global overlay_display_devices' 還原後再試")
    return False


SHEET = {"keep": False}   # 助理面板（光球）：送出後停著等回答；查資料這類「不用操作畫面」的任務保留到答案出來，要操作畫面前才收


def close_sheet():
    """收起還在等回答的助理面板（已顯示回答卡片的 App 端不會動）；收起後等一下動畫，免得截到面板。"""
    if OV and OV.ok and not SHEET["keep"]:
        try:
            if OV.sheet_close():
                time.sleep(0.5)
        except OSError:
            pass


OV = None  # Overlay 物件；App 沒裝或連不上時 OV.ok=False，所有特效安靜跳過
NOTIF_ID = "7195"
STATE = {"title": "AI 正在操作手機", "show": os.environ.get("AGENT_NOTIFY", "1") != "0"}


def status(text, step=0, max_steps=0):
    """更新「正在執行」的常駐通知（termux-notification，同一個 id 會覆蓋舊的）。
    有「停止」按鈕：按了會結束 agent2.py。背景送出，不拖慢主流程。
    注意：這是一般常駐通知；三星 Now Bar 只顯示支援 Live Updates 的 App，termux-notification 不一定會顯示在那裡（未驗證）。"""
    print("[狀態]", text)
    if not STATE["show"]:
        return
    if OV and OV.ok and OV.status(text, step, max_steps, STATE["title"]):
        return  # PhoneAI Overlay App 接手（Live Update 通知 + 自己的停止鈕）；失敗才退回 termux-notification
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
    if a == "batch":
        return "連續動作"
    if a.startswith("drag_"):
        return {"drag_start": "按住並拖動（還沒放開）", "drag_move": "微調位置（還沒放開）", "drag_end": "放開", "drag_cancel": "取消拖曳"}.get(a, "拖曳")
    if a in ("swipe", "longpress", "drag", "multitouch"):
        return {"swipe": "滑動", "longpress": "長按", "drag": "拖曳", "multitouch": "多指手勢"}[a]
    if a == "type":
        return "輸入文字（%d 字）" % len(str(act.get("text", "")))
    if a == "enter":
        return "按 Enter"
    if a == "open":
        return "開啟 " + str(act.get("name") or act.get("pkg", ""))
    if a == "cmd":
        return "執行 " + str(act.get("name", ""))
    if a == "intent":
        return "命令：" + str(act.get("name", ""))
    if a == "ask":
        return "等你確認"
    if a == "ask_user":
        return "問你問題"
    if a == "search":
        return "搜尋網路"
    return {"back": "按返回", "home": "回主畫面", "done": "完成", "fail": "無法完成"}.get(a, str(a))


def say(t, card=False):
    """card=True：另外請 App 浮出文字卡片（任務的最終回答用，字會留在畫面上）。"""
    STATS["say"] = t
    if card and STATS.get("t0") and "t_answer" not in STATS:
        STATS["t_answer"] = time.time() - STATS["t0"]   # 最終答案給出的時間（使用者感受到的完成時間）
    print("->", t)
    if card and OV and OV.ok:
        try:
            OV.reply(t)
        except OSError:
            pass
    # 朗讀：App 在線就請 App 念（用系統語音合成，不開 Termux 子程序；使用者可在 App 首頁關掉）；App 不在才退回 termux-tts-speak（背景執行）
    spoken = OV.speak(t) if OV and OV.ok else None
    if spoken is None and os.environ.get("AGENT_TTS", "1") != "0":
        # 輸入輸出都要斷開：朗讀程式如果還連著我們的輸出（例如用 | tee 存紀錄時是同一條管線），整個指令要等它念完才會結束
        subprocess.Popen(["termux-tts-speak", "-s", "MUSIC", t], stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL,
                         stderr=subprocess.DEVNULL, start_new_session=True)
    subprocess.run(["termux-notification", "--id", "7196", "--title", "AI 助手", "--content", t],
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def ask_user_text(q):
    """問使用者一個問題，回傳回答文字；使用者沒回答（取消、逾時）回 ""；問不到回 None。
    優先用 App 的助理面板（語音／打字，跟長按電源鍵同一個介面）；App 不可用才用 termux-dialog。"""
    if OV and OV.ok:
        r = OV.ask_text(q)
        if r is not None:
            return r
    r = subprocess.run(["termux-dialog", "text", "-t", "AI 想問你", "-i", q[:200]], capture_output=True, text=True)
    try:
        d = json.loads(r.stdout)
        return str(d.get("text", "")).strip() if d.get("code") == -1 else ""
    except ValueError:
        return None


def confirm(msg):
    if OV and OV.ok:
        a = OV.confirm(msg)  # PhoneAI Overlay App 的原生對話框；不可用才退回 termux-dialog
        print("[確認框(App)] 回答=%r" % (a,))
        if a is not None:
            return a
    r = subprocess.run(["termux-dialog", "confirm", "-t", "需要你確認", "-i", msg],
                       capture_output=True, text=True)
    # 除錯：印出確認框原始回應，方便判斷「按了是卻被當成否」是框沒回應、解析失敗還是模型自己拒絕
    print("[確認框] 回傳碼=%s 輸出=%r 錯誤=%r" % (r.returncode, r.stdout.strip()[:100], r.stderr.strip()[:100]))
    try:
        return str(json.loads(r.stdout).get("text", "")).strip().lower() in ("yes", "是")
    except Exception:
        return False


def vd_virtual_ids(sf_dump):
    """dumpsys SurfaceFlinger --display-id 裡的虛擬螢幕編號集合。"""
    return set(re.findall(r"Display (\d+) \(Virtual display\)", sf_dump))


def pick_new_sf(sf_dump, before):
    """背景螢幕的截圖編號：現在的虛擬螢幕扣掉建立前就有的；剛好一個才回傳，否則 None。"""
    new = vd_virtual_ids(sf_dump) - set(before or ())
    return next(iter(new)) if len(new) == 1 else None


def parse_raw_header(data):
    """screencap 不加 -p 輸出的原始畫面：開頭是 12 或 16 位元組標頭（寬、高、格式…，小端 32 位元），後面是 RGBA 像素。
    回傳 (寬, 高, 標頭長度)；格式不是 RGBA_8888（1）或長度對不上（例如輸出被換行符號轉換弄壞）回 None，呼叫端退回舊的 PNG 做法。"""
    if len(data) < 16:
        return None
    w, h, fmt = struct.unpack("<III", data[:12])
    if fmt != 1 or not (0 < w < 20000 and 0 < h < 20000):
        return None
    hdr = len(data) - w * h * 4
    return (w, h, hdr) if hdr in (12, 16) else None


LAST_SIG = [None, 0]   # [最近一次截圖的「簽名」（畫面指紋；等畫面穩定時判斷有沒有變過）, 畫面豐富度（印出來校準用）]


def raw_sig(data, hdr, w, h):
    """原始畫面的指紋，對「畫面有沒有變」夠用，而且很快：只看畫面中間（四邊各留 14% 寬不看——主螢幕邊緣的光暈波浪一直在動，
    狀態列的時鐘也會變，這些都不是 App 的內容），每 8 列取一列、每列每隔 37 個位元組取一個再雜湊。"""
    m = int(w * 0.14)
    md = hashlib.md5()
    for y in range(m, max(m + 1, h - m), 8):
        a = hdr + (y * w + m) * 4
        md.update(data[a:hdr + (y * w + max(m + 1, w - m)) * 4:37])
    return md.hexdigest()


def screen_richness(data, hdr, w, h):
    """畫面「豐富度」：取樣（約 48x96 個點），每個顏色通道量化成 16 級，數有幾種不同的顏色。
    載入中的灰色骨架畫面、純色畫面很低；有商品圖、文字的畫面很高。只用來印出來校準（還沒拿來做判斷）。"""
    seen = set()
    sx, sy = max(1, w // 48), max(1, h // 96)
    for y in range(0, h, sy):
        row = hdr + y * w * 4
        for x in range(0, w, sx):
            o = row + x * 4
            seen.add((data[o] >> 4, data[o + 1] >> 4, data[o + 2] >> 4))
    return len(seen)


RAW_CACHE = {"cap": None, "t": 0.0}   # 等載入時最後拍的那張原始畫面：下一次截圖若很新（沒有動作介入）就直接用，不再拍一次


def take_cached(max_age=1.5, now=time.time):
    cap, t = RAW_CACHE["cap"], RAW_CACHE["t"]
    RAW_CACHE["cap"] = None
    return cap if cap and now() - t <= max_age else None


def wait_loaded(min_wait, max_wait, thr, poll=None, sleep=time.sleep, now=time.time):
    """命令把 App 開到某個頁面後，等畫面「載入完」才繼續（取代固定等待）：先等 min_wait 秒，拍一張畫面算豐富度，
    ≥ thr 就繼續；還沒到每 0.4 秒再拍，最多等 max_wait 秒（到了就讓 AI 自己看著辦）。最後那張畫面留在 RAW_CACHE，下一步截圖直接用。
    只用在校準過門檻的命令（PLAN_RICH）。回傳實際等了幾秒。"""
    poll = poll or capture_raw
    t0 = now()
    sleep(min_wait)
    while True:
        cap = poll()
        if not cap:   # 拍不到就退回固定等待的剩餘時間
            sleep(max(0.0, max_wait - (now() - t0)))
            return now() - t0
        rich = screen_richness(cap[0], cap[3], cap[1], cap[2])
        elapsed = now() - t0
        print("[等載入] %.1f 秒，畫面豐富度 %d（要 ≥ %d）" % (elapsed, rich, thr))
        if rich >= thr or elapsed >= max_wait:
            RAW_CACHE["cap"], RAW_CACHE["t"] = cap, now()
            return elapsed
        sleep(0.4)


def wait_changed(max_wait, thr, base_sig, poll=None, sleep=time.sleep, now=time.time, before_poll=None):
    """點擊之後等畫面「已經變了、穩定了、而且內容夠豐富」才繼續，最多等 max_wait 秒（AI 指定的等待秒數，所以最壞情況跟以前的固定等待一樣）。
    三個條件缺一不可：①指紋跟點擊前不同（畫面真的換了）②連兩張一樣（不是轉場中）③豐富度 ≥ thr（不是灰色骨架畫面）。
    之前單用①②的「等畫面穩定」會在載入中的半成品畫面放行；加上③才安全。最後那張畫面留給下一步截圖用。回傳實際等了幾秒。"""
    poll = poll or capture_raw
    t0 = now()
    sleep(min(0.8, max_wait))
    if before_poll:
        before_poll()   # 清掉點擊的漣漪，免得被拍進畫面
    prev = None
    while True:
        cap = poll()
        if not cap:
            sleep(max(0.0, max_wait - (now() - t0)))
            return now() - t0
        sig = raw_sig(cap[0], cap[3], cap[1], cap[2])
        rich = screen_richness(cap[0], cap[3], cap[1], cap[2])
        elapsed = now() - t0
        ok = sig != base_sig and prev == sig and rich >= thr
        print("[等畫面] %.1f 秒：%s、%s、豐富度 %d" % (elapsed, "已變" if sig != base_sig else "還沒變", "穩定" if prev == sig else "還在動", rich))
        if ok or elapsed >= max_wait:
            RAW_CACHE["cap"], RAW_CACHE["t"] = cap, now()
            return elapsed
        prev = sig
        sleep(0.2)


def capture_raw():
    """用 root 截一張原始畫面；回傳 (資料, 寬, 高, 標頭長度)，失敗回 None。"""
    arg = " -d " + BG["sf"] if BG["on"] and BG["sf"] else ""
    r = subprocess.run(["su", "-c", "screencap" + arg], capture_output=True)
    ph = parse_raw_header(r.stdout)
    return (r.stdout, ph[0], ph[1], ph[2]) if ph else None


def settle(max_wait, poll=capture_raw):
    """動作後等畫面穩定再往下，取代固定睡 max_wait 秒：先睡 0.4 秒讓動作生效，然後每截一張（約 0.3 秒）原始畫面比一次指紋——
    畫面已經跟動作前不同、而且連兩張一樣 = 穩定，馬上回傳。畫面一直不變（例如動作本來就不會改變畫面）或一直在動（影片、轉圈圈）就等到上限，
    行為等於以前的固定等待，所以不會比以前更差。回傳實際等了幾秒。"""
    t0 = time.time()
    time.sleep(min(0.4, max_wait))
    base, prev = LAST_SIG[0], None
    while time.time() - t0 < max_wait:
        cap = poll()
        if not cap:
            time.sleep(max(0.0, max_wait - (time.time() - t0)))  # 截不到就退回固定等待
            break
        sig = raw_sig(cap[0], cap[3], cap[1], cap[2])
        if prev is not None and sig == prev and sig != base:
            break
        prev = sig
    return time.time() - t0


def shot_fast():
    """快速截圖：不經過 PNG 編碼／解碼（1080x2340 的 PNG 光編碼就要將近 1 秒），原始像素直接交給 ImageMagick 縮小。
    成功回 (寬, 高)；任何地方不對回 None（呼叫端退回舊做法）。"""
    t0 = time.time()
    cap = take_cached() or capture_raw()
    t1 = time.time()
    if not cap:
        return None
    data, w, h, hdr = cap
    LAST_SIG[0] = raw_sig(data, hdr, w, h)
    LAST_SIG[1] = screen_richness(data, hdr, w, h)
    # -scale = 方塊平均縮小，比 -resize（Lanczos）快很多，縮成 432 寬畫質夠用（文字、小圖示仍清楚）
    m = subprocess.run(["magick", "-size", "%dx%d" % (w, h), "-depth", "8", "rgba:-", "-alpha", "off", "-scale", "%dx" % WIDTH, SMALL],
                       input=data[hdr:], capture_output=True)
    if m.returncode != 0 or not os.path.exists(SMALL):
        return None
    print("[截圖] 擷取 %.1f · 縮小 %.1f（秒）· 畫面豐富度 %d" % (t1 - t0, time.time() - t1, LAST_SIG[1]))
    return w, h


def shot():
    close_sheet()
    if os.environ.get("AGENT_SHOT_FAST", "1") != "0":
        try:
            os.remove(SMALL)
        except OSError:
            pass
        try:
            wh = shot_fast()
        except Exception as e:
            print("快速截圖出錯，改用舊做法:", repr(e))
            wh = None
        if wh:
            return wh[0], wh[1], hashlib.md5(open(SMALL, "rb").read()).hexdigest()
    take = lambda: su(f"rm -f {SHOT}; screencap{' -d ' + BG['sf'] if BG['on'] and BG['sf'] else ''} -p {SHOT}; "
                      f"chown {os.getuid()}:{os.getgid()} {SHOT}")
    take()
    if BG["on"] and BG.get("sf_before") is not None and not os.path.exists(SHOT):
        # 直接顯示：預覽打開／關閉時背景螢幕換了輸出，系統會給它新的截圖編號 → 重新找一次再截
        sf = pick_new_sf(su("dumpsys SurfaceFlinger --display-id"), BG["sf_before"])
        if sf and sf != BG["sf"]:
            print("背景模式：截圖編號換成 %s" % sf)
            BG["sf"] = sf
            take()
    subprocess.run(["magick", SHOT, "-resize", f"{WIDTH}x", SMALL])
    r = subprocess.run(["identify", "-format", "%w %h", SHOT], capture_output=True, text=True)
    w, h = r.stdout.split()
    return int(w), int(h), hashlib.md5(open(SMALL, "rb").read()).hexdigest()


def clear_field():
    """清空目前聚焦的輸入框：Ctrl+A 全選，再按一次刪除鍵。要不要清空由 AI 看畫面決定（輸入框是空的就不呼叫，省時間）。
    用 input 指令送鍵（背景模式會指定螢幕）。"""
    su("; ".join([inp("keycombination 113 29"),    # Ctrl+A 全選
                  inp("keyevent 67")]))            # 刪除鍵（刪掉選取的）


def type_text(text):
    """輸入文字到已選取的輸入框。優先用剪貼簿貼上（不受目前鍵盤語言影響，中英文都行，已驗證）；
    剪貼簿失敗且是純英數，才退回 input text（會受目前鍵盤影響，中文鍵盤會打出注音）。
    貼完會盡量還原原本的剪貼簿內容。"""
    if not text:
        return False
    old = subprocess.run(["termux-clipboard-get"], capture_output=True, text=True).stdout
    r = subprocess.run(["termux-clipboard-set", text], capture_output=True, text=True)
    if r.returncode == 0:
        su(inp("keyevent 279"))  # KEYCODE_PASTE
        time.sleep(1)
        subprocess.run(["termux-clipboard-set"], input=old, text=True)
        return True
    if text.isascii() and "%" not in text and "\n" not in text:
        su(inp("text " + shlex.quote(text.replace(" ", "%s"))))
        return True
    return False


def resolve_pkg(pkg, name):
    """模型給的套件名稱沒裝（猜錯很常見）時，改用 App 顯示名稱去 ~/apps.tsv 查。"""
    if re.fullmatch(r"[A-Za-z0-9_.]+", pkg or "") and ("package:" + pkg) in su("pm list packages " + pkg).split():
        return pkg
    if name and applist:
        found = applist.find(name)
        if found:
            return found
    return pkg


def pick_app_from_reply(text, table):
    """解析「先開哪個 App」的 AI 回答 {"app": "名稱"或null}。名稱必須是清單裡有的（用 applist 的正規化比對，所以標點大小寫不影響）；
    應用商店、Termux 不自動開。回傳 (名稱, 套件) 或 None。"""
    obj = parse(text or "")
    name = obj.get("app") if isinstance(obj, dict) else None
    if not isinstance(name, str) or not name.strip():
        return None
    key = applist.norm(name) if applist else re.sub(r"\W", "", name).lower()
    pkg = (table or {}).get(key)
    if not pkg or pkg in NO_OPEN or pkg == "com.termux":
        return None
    return name.strip(), pkg


BATCH_OK = ("tap", "swipe", "longpress", "drag", "multitouch", "scroll", "wait", "back", "home", "enter", "type")  # 連續動作裡允許的動作
PENDING = []  # 連續動作（batch）還沒做的後續動作：下一圈直接做，不截圖、不問模型


def filter_batch(actions, limit=6):
    """連續動作最多 limit 個，只留允許的動作。拿掉 ask/done/fail/search/open/拖到一半（那些需要看畫面或使用者確認）。"""
    return [x for x in (actions or []) if isinstance(x, dict) and x.get("action") in BATCH_OK][:limit]


def action_sig(act):
    """把動作變成可以比較的「簽名」（動作類型 + 大約位置），用來發現 AI 一直用同一招。"""
    a = act.get("action")
    keys = {"tap": ("x", "y"), "longpress": ("x", "y"), "drag_start": ("x", "y", "to_x", "to_y"),
            "drag": ("x1", "y1", "x2", "y2"), "swipe": ("x1", "y1", "x2", "y2")}.get(a)
    if not keys:
        return None
    try:
        return (a,) + tuple(float(act[k]) for k in keys)
    except (KeyError, TypeError, ValueError):
        return None


def same_trick(sigs, n=3, tol=60):
    """最近 n 個動作是不是同一招（同類型、每個座標都差不到 tol，座標 0~1000）。"""
    last = [x for x in sigs if x][-n:]  # 跳過沒有位置的動作（drag_cancel、wait…），start→cancel→start 來回也抓得到
    if len(last) < n or len({x[0] for x in last}) != 1:
        return False
    return all(abs(v - last[0][k]) <= tol for x in last for k, v in enumerate(x) if k > 0)


def haiku_text(prompt, max_tokens=300):
    """問最便宜的模型（Haiku，只送文字）一個問題，回傳文字；連不上回 None。用量計入 STATS。"""
    base = {"model": MODELS[0], "max_tokens": max_tokens, "messages": [{"role": "user", "content": prompt}]}
    for extra in ({"thinking": {"type": "between_tools"}, "output_config": {"effort": "low"}}, {"output_config": {"effort": "low"}}, {}):
        req = urllib.request.Request("https://api.anthropic.com/v1/messages", data=json.dumps({**base, **extra}).encode(),
            headers={"x-api-key": KEY, "anthropic-version": "2023-06-01", "content-type": "application/json"})
        try:
            r = json.loads(urllib.request.urlopen(req, timeout=20).read())
        except urllib.error.HTTPError as e:
            if e.code == 400 and extra:
                continue
            return None
        except Exception:
            return None
        add_stats(r.get("usage", {"input_tokens": 0, "output_tokens": 0}), MODELS[0])
        return "".join(b.get("text", "") for b in r.get("content", []) if b.get("type") == "text")
    return None


def ai_plan_first(task):
    """第一步預先規劃：問 Haiku（只送文字，約 1 秒）這個任務的第一步能不能直接用一個命令做到（搜尋、開設定頁、設鬧鐘、查電量…）。
    能就回 {"name","args","desc","wait"}，後面直接執行，省掉「開 App、等 3 秒、截圖、Sonnet 看圖」；不能或任何一關沒過回 None，照原本流程。
    AGENT_PRE_INTENT=0 可關。"""
    if not intents or os.environ.get("AGENT_PRE_INTENT", "1") == "0":
        return None
    disp = BG["id"] if BG["on"] and BG["id"] else None
    plan = intents.plan_by_rules(task, display=disp, has_pkg=has_pkg)   # 任務寫得很明確：不問 Haiku
    if plan:
        print("第一步規劃（規則命中，沒問 Haiku）")
    else:
        out = haiku_text(intents.plan_prompt(task), 200)
        if not out:
            return None
        plan = intents.plan_check(parse(out), task, display=disp, has_pkg=has_pkg)
    if plan and os.environ.get("AGENT_PLAN_WAIT"):   # 校準用：故意縮短命令後的等待，拍到「載入中」的畫面
        try:
            plan["wait"] = float(os.environ["AGENT_PLAN_WAIT"])
        except ValueError:
            pass
    return plan


def ai_pick_app(task, table):
    """問 Haiku（只送文字）：這個任務一開始要先開哪個 App？連不上或回答看不懂時，退回字串比對（pre_open）。"""
    if not table:
        return None
    labels = "、".join(sorted(table)[:400])
    prompt = ("使用者要手機助手做這件事：「" + task + "」\n下面是手機上有的App名稱：\n" + labels + "\n"
              "這個任務一開始最適合先打開哪一個App？只能從清單裡選一個，名稱照清單原樣寫。"
              "如果不需要先開App（例如開關Wi-Fi、下拉通知欄、桌面上的操作），或不確定，就回null。"
              '只輸出一行JSON，不要解釋：{"app":"名稱"} 或 {"app":null}')
    out = haiku_text(prompt, 200)
    return pre_open(task, table) if out is None else pick_app_from_reply(out, table)


def lesson_ok(text):
    """經驗要通用、短、不含個人資料：太長、含長串數字（帳號、卡號、驗證碼）、提到密碼就不存。"""
    t = str(text or "").strip()
    return bool(t) and len(t) <= 200 and not re.search(r"\d{6,}", t) and "密碼" not in t and "password" not in t.lower()


def parse_lesson(text, app_pkg):
    """解析 {"lesson": "...", "app_specific": true/false}。回傳 (經驗, 適用的套件或 None) 或 None。"""
    obj = parse(text or "")
    if not isinstance(obj, dict) or not lesson_ok(obj.get("lesson")):
        return None
    return str(obj["lesson"]).strip(), (app_pkg if obj.get("app_specific") is True and app_pkg else None)


def save_lesson(task, hist, say_text, app_pkg):
    """任務結束後問 Haiku：這次有沒有值得記下來的經驗？有就存進記憶（沒有期限，使用者可在 App 的記憶頁刪除）。回傳存下的經驗或 None。"""
    if os.environ.get("AGENT_LESSONS", "1") == "0" or not memory or STATS.get("steps", 0) < 4 or not hist:
        return None
    prompt = ("手機助手剛做完一個任務。任務：「" + task + "」；結果：「" + str(say_text or "")[:80] + "」；過程中的動作：" + "；".join(hist[-25:])[:1500] + "\n"
              "有沒有學到「下次做同類事會更快、或能避免失誤」的具體經驗？例如：某個App的操作訣竅、哪種手勢有效、哪個做法行不通。"
              "規則：一句話（60字內）、要通用（不要寫這次任務的結果或個人資料、帳號、密碼）、沒有就回null。"
              "如果這個經驗只適用於特定App，app_specific設true。"
              '只輸出一行JSON：{"lesson":"一句話經驗","app_specific":true或false} 或 {"lesson":null}')
    got = parse_lesson(haiku_text(prompt, 250), app_pkg)
    if not got:
        return None
    text, app = got
    return text if memory.add_lesson(text, app) else None


def pre_open(task, table):
    """任務文字裡提到某個已安裝的 App（名稱）時，回傳 (名稱, 套件) 讓程式先直接開它，省掉第一次「看畫面再決定先開哪個」。
    取最早出現的名稱（同位置取較長的）。中文名稱至少 2 字；英文名稱至少 3 字且前後不是英數字（免得 "config" 比對到 "ig"）。
    應用商店、Termux 不自動開。"""
    t = re.sub(r"[\s\-_，。,.·!！?？:：'’]", "", task or "").lower()
    best = None
    for k, pkg in (table or {}).items():
        if pkg in NO_OPEN or pkg == "com.termux":
            continue
        if k.isascii():
            m = re.search(r"(?<![a-z0-9])" + re.escape(k) + r"(?![a-z0-9])", t) if len(k) >= 3 else None
            pos = m.start() if m else -1
        else:
            pos = t.find(k) if len(k) >= 2 else -1
        if pos >= 0 and (best is None or (pos, -len(k)) < best[0]):
            best = ((pos, -len(k)), k, pkg)
    return (best[1], best[2]) if best else None


def is_dup_type(hist, text, window=8):
    """最近幾個動作裡是不是已經輸入過同樣的文字（模型有時按完 Enter 後覺得「沒反應」就重打一次，白花好幾步）。"""
    return ("輸入了「%s」" % text) in hist[-window:]


def parse_launcher_component(text, pkg):
    """解析 `cmd package resolve-activity --brief …` 的輸出，回傳「套件/入口」；找不到回 None。"""
    for line in reversed((text or "").strip().splitlines()):
        line = line.strip()
        if re.fullmatch(re.escape(pkg) + r"/[\w.$]+", line):
            return line
    return None


def launcher_component(pkg):
    """查 App 的桌面啟動入口。背景模式不用「-p 套件 + LAUNCHER 類別」讓 am 自己猜：這支手機有雙開／安全資料夾（多使用者），
    實測蝦皮、ChatGPT 用那種寫法會 `unable to resolve Intent`，直接指定入口（-n）才開得起來。"""
    return parse_launcher_component(su(
        "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER " + pkg), pkg)


PKGS = []   # 已安裝套件（第一次用到才查，之後沿用）


def has_pkg(pkg):
    if not PKGS:
        PKGS.append({l.split(":", 1)[1].strip() for l in su("pm list packages").splitlines() if l.startswith("package:")})
    return pkg in PKGS[0]


def open_app(pkg):
    """用 root 直接開 App（比點桌面圖示快）。套件必須已安裝；回傳 (成功?, 訊息)。"""
    if not re.fullmatch(r"[A-Za-z0-9_.]+", pkg or ""):
        return False, "不允許的套件名稱"
    if ("package:" + pkg) not in su("pm list packages " + pkg).split():
        return False, "沒有安裝 " + pkg
    if BG["on"] and BG["id"]:
        comp = launcher_component(pkg)
        implicit = "-a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p " + pkg
        # 先試指定入口（-n），再退回舊寫法；每種都先試全螢幕再試一般開法
        out = ""
        for target in ([("-n " + comp)] if comp else []) + [implicit]:
            base = "am start --display %s %s" % (BG["id"], target)
            out = su(base + " --windowingMode 1 2>&1")
            if "Error" in out or "Exception" in out:
                out = su(base + " 2>&1")
            if not ("Error" in out or "Exception" in out):
                break
        if "Error" in out or "Exception" in out:
            # 錯誤訊息通常在輸出最後面（前面是 "Starting: Intent {…}"），所以印最後一段，並完整存一份方便看
            try:
                open(os.path.expanduser("~/last_start_error.txt"), "w").write(out)
            except OSError:
                pass
            return False, pkg + " 在第二螢幕開不起來：" + " ".join(out.split())[-260:]
        BG["opened"].add(pkg)
        time.sleep(1.5)  # 等視窗建好才查得到任務
        maximize_bg(pkg)
        return True, "已在第二螢幕開啟 " + pkg
    out = su("monkey -p %s -c android.intent.category.LAUNCHER 1 2>&1" % pkg)
    if "No activities found" in out:
        return False, pkg + " 沒有可開啟的畫面"
    return True, "已開啟 " + pkg


SHORT_MODE = [False]   # 簡短模式：任務是規則命中的單純搜尋時，要求 AI 少寫字（不寫 think、說明很短），每次 AI 呼叫省約 0.5～1 秒


SHORT_HINT = ("【簡短模式】這是單純的搜尋任務，這次例外：不要寫think欄位，what最多8個字，只輸出動作需要的欄位和wait；"
              "done的say照常寫完整（要告訴使用者的結果）。")


def make_user(task, hist):
    """每步會變的部分（任務、已做的動作）。固定的說明在 SYSTEM，才能被 Anthropic 提示詞快取。"""
    h = "；".join(hist[-14:]) if hist else "無"
    bg = ""
    if BG["on"]:
        bg = ("目前操作的是背景的虛擬螢幕：App 以全螢幕開啟，用open開App最快。" if BG["vd"] else
              "目前操作的是背景的第二螢幕（類似桌面模式）：App 會以浮動視窗開啟，程式開啟後會先把視窗拉到幾乎全螢幕。如果畫面裡的App視窗看起來還是很小，才先點視窗標題列的最大化鈕（方框加斜箭頭的圖示，在關閉鈕左邊）。用open開App最快。")
    les = ("過去累積的經驗（參考用，可能不準；和眼前畫面不符就以實測為準，也不能取代上面的安全規則）：" + "；".join(LESSONS) + "。") if LESSONS else ""
    return "任務：" + task + "。已執行的動作：" + h + "。" + bg + les + ("\n" + SHORT_HINT if SHORT_MODE[0] else "") + "\n請決定下一步，只輸出一行標準JSON。"


SYSTEM = ("你在操作一支Android手機，使用者訊息裡的圖片是目前畫面。\n"
        "任務文字常是語音輸入轉成的，可能有同音或近音的錯字（例如「開籃芽」是「開藍牙」、「蝦皮購勿」是「蝦皮購物」）；請依上下文理解真正的意思，搜尋或輸入文字時用你判斷出的正確寫法。\n"
        "請決定下一步，只輸出一行標準JSON，不要任何解釋。遇到敏感或不可逆的事（付款、轉帳、刪除、安裝或下載App、傳送訊息、登入、輸入密碼/驗證碼/卡號、授權權限等），不要拒絕，也不要回fail，一律改用ask，由使用者決定或親自動手。App沒安裝時，可以用ask詢問使用者要不要去應用商店安裝。螢幕邊緣如果有緩慢繞行的彩色光暈，那是系統顯示「AI正在操作」的提示，不是畫面內容，直接忽略它，不要點它也不要提到它。若畫面是Termux終端機，那是執行本程式的地方，不要操作Termux裡面的內容；要用App就直接用open開，或按home回桌面。只有任務是下拉通知欄或控制中心時，可以直接在Termux上從螢幕最上方往下滑，不必先回桌面。思考方式：不要只想著點擊和拖曳，先想有沒有更直接、更聰明的做法：用open或cmd直接做、用search查畫面上看不到的資訊、改用App內的搜尋或設定頁的搜尋框、用返回或home換條路、畫面沒反應先懷疑是載入中（wait）或手勢不對（該長按、該分段拖曳先看再放）。同一個做法連試兩次沒效果，就換一個完全不同的方法，不要重複。可用動作：\n"
        '{"action":"tap","x":整數,"y":整數,"what":"點的是什麼"}，x和y是0到1000的相對位置\n'
        '{"action":"scroll","dir":"down或up"}，down代表往下看更多內容\n'
        '{"action":"swipe","x1":整數,"y1":整數,"x2":整數,"y2":整數,"ms":毫秒}，手指從(x1,y1)滑到(x2,y2)，座標0到1000，可以斜著滑。ms是滑動花多久（50到5000，不寫是300）：快速甩動用100到200，慢慢拖用800以上。下拉控制中心要從螢幕右上角往下滑（x1、x2約900，y1約5，y2約500）；下拉通知欄才從正中間上方往下滑（x約500）。兩者不一樣，不要搞混\n'
        '{"action":"longpress","x":整數,"y":整數,"ms":毫秒}，長按（ms不寫是800，300到5000）\n'
        '{"action":"drag","x1":整數,"y1":整數,"x2":整數,"y2":整數,"hold_ms":整數,"ms":整數}，先在(x1,y1)按住hold_ms（不寫是600）再拖到(x2,y2)，拖動花ms（不寫是500），用於移動桌面圖示、排序、拖曳滑桿\n'
        '{"action":"batch","actions":[動作1,動作2,...]}，一次連續做完最多6個動作，中間不截圖也不問我（可用tap、swipe、longpress、drag、multitouch、scroll、wait、back、home、enter、type）。已經摸清楚規律、而且你能預測每個動作之後的畫面時才用，例如已經知道方塊放下的位置怎麼換算，就把同一回合的三個方塊一次放完，做完我才給你看新畫面。畫面可能被前一個動作改變（彈出視窗、換頁）而不確定時，不要用batch\n'
        '任何動作都可以多加一個"remember":"一句通用經驗"，把你摸索出來的規律馬上記起來，之後的任務和下一步都會看到。要寫得具體可用，可以含換算方式。例如玩方塊遊戲，用分段拖曳量出「手指在(x,y)、方塊實際出現在手指上方約120」之後，記成"remember":"Block Blast 拖方塊時方塊會出現在手指上方約12%螢幕高度，目標格的下方約12%就是手指要放的位置"。沒有新發現就不要寫，一個任務最多記3則，同樣的意思不要換句話重複記。只記「以後同類操作都適用的規則」（座標換算、手指與方塊的位置關係、哪種手勢有效、哪個按鈕在哪），不要記這一刻的畫面狀態（哪格有方塊、目前分數、這一局的盤面），那些下一步就過時了\n'
        '拖方塊／拖物件的遊戲，常見的規則是「物件跟著手指的『位移』走」，不是跳到手指所在的位置：手指從起點移動多少，物件就移動多少（有些遊戲在按下的瞬間物件會往手指上方浮起一個固定距離L）。所以要把物件從目前中心B放到目標中心T：手指起點放在物件上（最好正中心B），終點 = 起點 +（T − B）；有浮起時再補償L（第一次分段拖曳時量出L = 物件中心與手指的垂直距離，記起來）。不要用「目標位置再往下多少」這種絕對位置的想法，每次起點不一樣就會偏。座標先用肉眼估，放完看結果，偏了就依偏差修正終點，不要整個重量\n'
        '摸索新規則時：第一次用下面的分段拖曳量出手指和方塊的位置關係，最多量3次就要下結論，確定規律後立刻用remember記起來，之後同類操作直接用drag或batch一次做完，不要每一步都停下來看；只在結果可能出錯時才再用分段拖曳確認\n'
        '分段拖曳（拖到一半先看畫面，再決定放不放開；拼圖、放方塊、對位這類遊戲用，因為方塊常不是正好跟著手指，放開後如果位置不對會彈回去，你就看不到哪裡錯）：{"action":"drag_start","x":整數,"y":整數,"to_x":整數,"to_y":整數}，在(x,y)按下並拖到(to_x,to_y)，手指**還按著**，然後我會給你新的畫面，這時你看得到方塊現在停在哪裡、會落在哪一格；{"action":"drag_move","x":整數,"y":整數}，手指還按著，移到新位置微調；{"action":"drag_end"}放開手指（也可以加x、y，先移過去再放開）；{"action":"drag_cancel"}取消。拖到一半不要做別的動作，一定要用drag_end或drag_cancel收尾\n'
        '{"action":"multitouch","fingers":[{"x1":整數,"y1":整數,"x2":整數,"y2":整數,"ms":整數},...],"hold_ms":整數}，最多4根手指同時動，每根手指各自從起點滑到終點（起點終點相同=那根手指按住不動）；hold_ms是先全部按住再一起移動（不寫是0）。例：兩指張開放大 = 兩根手指從中心附近往兩側斜向外滑；兩指同時上滑；一根手指按住不動、另一根拖曳\n'
        '{"action":"type","text":"要輸入的文字","clear":true,"enter":false}，在已選取的輸入框輸入文字，輸入前要先點一下輸入框；點過輸入框之後你會再看到一次畫面，這時先判斷輸入框是不是空的（只有灰色提示字或只有游標都算空的）：不是空的（有舊字）就寫"clear":true，程式會先全選再按一次刪除鍵清空，才貼上新的字；是空的就寫"clear":false，程式不會刪除；沒寫clear時當作true（保險起見會清空）；要接在原本文字後面也寫"clear":false。輸入完要不要送出（搜尋、傳送…）也在這一步決定：要就寫"enter":true，程式貼完會自動按Enter，不必再另外用一次enter動作；密碼、驗證碼、卡號不要自己輸入，改用ask（manual設true）請使用者自己輸入；不要點系統跳出的「貼上」浮動按鈕或鍵盤上的剪貼簿建議，輸入文字只用type動作；輸入並按Enter送出後，頁面常要幾秒才載入完，畫面還沒變就用wait多等幾秒再看一次，不要重複輸入同樣的字、也不要重複點搜尋列\n'
        '{"action":"enter"}，按下Enter送出輸入框內容\n'
        '{"action":"open","name":"App顯示名稱","pkg":"套件名稱"}，用指令直接開啟已安裝的App，比在桌面找圖示快；一定要寫name（例如Chrome、設定、LINE），pkg只在你很確定時才寫，程式會用name自己查套件\n'
        '{"action":"cmd","name":"wifi_on|wifi_off|bluetooth_on|bluetooth_off|volume_up|volume_down"}，直接開關Wi-Fi、藍牙或調音量，不用點畫面\n'
        '能用open或cmd做的事優先用它們，其餘才用畫面操作。\n'
        '{"action":"search","q":"搜尋關鍵字"}，到網路查資料。只有需要畫面上看不到的最新資訊（新聞、價格、不認得的名詞）才用；每個任務最多2次，要花錢，能靠畫面操作做完就不要搜尋；q不要放個人資料\n'
        '{"action":"shell","cmd":"唯讀指令","why":"為什麼要查"}，只讀不寫：查系統資訊（dumpsys battery、getprop、date、cat /proc/…、pm list packages、cmd wifi status…），結果寫在下一步的歷史裡，不用開畫面，查到就可以直接done回答。不能寫入、刪除、改設定；有管線只能接grep、head、tail、sort、cut、wc等過濾。查得到就不要去點畫面。查手機的規格或版本（Android 版本、型號、容量）一律用 getprop 或 dumpsys，不要打開設定頁一頁一頁找\n'
        '{"action":"wait","wait":秒數}，什麼都不做，只等待（App或遊戲載入中、廣告倒數時用，0.5到6秒）\n'
        '{"action":"back"}、{"action":"home"}\n'
        '{"action":"ask","why":"原因","manual":false}，下一步涉及付款、轉帳、刪除、安裝App、傳送訊息、登入或其他不可逆/敏感操作時用，why要寫清楚要做什麼；需要使用者自己動手（輸入密碼、驗證碼、指紋、授權）時manual設true，使用者做完會按是，之後你再看畫面繼續；ask只能問「是／否」的問題（使用者只能按是或否，沒辦法打字回答）：需要使用者選擇做法、補充說明，或任務太模糊不知道怎麼做時，不要用ask，也不要自己猜著動手，改用下一行的ask_user問他，不要用ask，也不要自己猜著動手\n'
        '{"action":"ask_user","question":"要問使用者的問題"}，需要使用者選擇做法、補充說明、回答你的問題，或任務太模糊時用；會跳出助理面板，使用者可以用語音或打字回答，答案會寫在下一步的歷史裡（使用者沒回答就視為取消任務）；問題要具體、一次只問一件事，可以附上你想到的選項（例如「要依類型分資料夾，還是依使用頻率排列？」）\n'
        '{"action":"done","say":"要告訴使用者的話"}，只有確定任務已完成才用\n'
        '{"action":"fail","say":"原因"}，真的做不到時用（不是因為內容敏感）\n'
        '每個回答都要加一個"think":"一句話（40字內）：上一步的結果跟預期一樣嗎？不一樣的話原因是什麼？這一步為什麼這樣做"，例如"上次方塊落在目標左邊一格，所以終點往右修正一格"。我會把它記在已執行的動作裡，下一步你看得到自己的想法。think 一定要放在 JSON 的最後一個欄位（在 wait 之後）：我讀到 think 之前就會先開始動作，所以動作需要的欄位都要寫在它前面\n'
        '決定之前先在心裡走一遍：①上一步有沒有達到目的（看畫面，不要假設成功）②沒達到的話，是位置偏了、時機不對、手勢不對，還是根本方向錯了 ③有沒有完全不同、更省步的路。偏一點就微調；同樣的做法失敗兩次，就退一步換一個完全不同的思路，例如換起點、換手勢、換入口、先關掉擋住的東西、去查資料或請使用者幫忙\n'
        '除了done和fail，每個動作都可以多加一個"wait":秒數（0.5到6），代表做完這個動作後，畫面大概要多久才會穩定，我才會截下一張圖。'
        '例如點選單項目或捲動約1，開啟App約3，網頁載入約4，輸入文字約1；不確定就省略。')
if intents:   # 命令層：能用命令做的事優先用命令（白名單，AI 只能選名稱與參數）
    SYSTEM = SYSTEM.replace("能用open或cmd做的事優先用它們，其餘才用畫面操作。\n", "能用open、cmd或intent做的事優先用它們，其餘才用畫面操作。\n" + intents.PROMPT)


TREE_MODE = os.environ.get("AGENT_TREE", "0") == "1"   # 實驗（省錢測試）：不附截圖，改附 App 無障礙讀到的畫面元件清單；讀不到才截圖
_SCREEN_WH = []


def screen_wh():
    """主螢幕的像素寬高（`wm size`，有 Override 取最後一行）。讀不到用 1080x2340。"""
    if not _SCREEN_WH:
        m = re.findall(r"(\d+)x(\d+)", su("wm size") or "")
        _SCREEN_WH.append((int(m[-1][0]), int(m[-1][1])) if m else (1080, 2340))
    return _SCREEN_WH[0]


def tree_text():
    """這一步的畫面元件清單 (文字, 寬, 高)；不是元件清單模式、背景模式（無障礙只看得到主螢幕）、App 沒連上、讀不到元件都回 None。"""
    if not (TREE_MODE and OV and OV.ok and intents) or BG["on"]:
        return None
    try:
        items = OV.tree(0, 200)
        if not items:
            return None
        w, h = screen_wh()
        if max(int(i.get("x", 0)) for i in items) > w or max(int(i.get("y", 0)) for i in items) > h:
            w, h = h, w   # 橫向
        t = intents.format_tree(items, w, h)
        return (t, w, h, items) if t else None
    except Exception as e:
        print("元件清單讀取出錯（改截圖）:", repr(e))
        return None


def grab_screen():
    """取得這一步給 AI 的畫面：元件清單模式優先用元件清單（不截圖），讀不到或 AI 要求看圖才截圖。回傳 (w, h, hash)，跟 shot() 一樣。"""
    if TREE_MODE and not STATS.pop("force_img", False):
        r = tree_text()
        if r:
            t, w, h, items = r
            STATS["tree_text"] = t
            STATS["tree_items"] = (items, w, h)
            STATS["tree_steps"] = STATS.get("tree_steps", 0) + 1
            print("  · 元件清單模式：%d 行（約 %d 字），這一步不截圖" % (t.count("\n") + 1, len(t)))
            return w, h, hashlib.md5(t.encode()).hexdigest()
    STATS.pop("tree_text", None)
    STATS.pop("tree_items", None)
    STATS["shot_steps"] = STATS.get("shot_steps", 0) + 1
    return shot()


def make_body(task, hist, model):
    parts = []
    txt = make_user(task, hist)
    if STATS.get("tree_text"):   # 元件清單模式：不附圖，附文字清單
        txt += ("\n（這一步沒有附圖，改附畫面上的元件清單。每行：編號、*=可點、文字、(x,y)。要點哪個就回{\"action\":\"pick\",\"n\":編號}，程式會點那個元件的中心，你不用算座標。"
                "清單看不到的東西（圖片、遊戲畫面、沒有文字的圖示）沒列出來；需要看圖才能判斷時回{\"action\":\"need_image\"}，下一步我改附截圖。）\n" + STATS["tree_text"])
    elif STATS.get("no_img"):   # 剛查完資料：不附畫面（省一次截圖與圖片 token）
        txt += "\n（這一步沒有附畫面：查詢結果夠回答就直接done；不夠就再用shell或intent查；要操作畫面請先回{\"action\":\"wait\",\"wait\":0.5}，下一步我會附畫面。）"
    else:
        img = base64.b64encode(open(SMALL, "rb").read()).decode()
        parts.append({"type": "image", "source": {"type": "base64", "media_type": "image/png", "data": img}})
    parts.append({"type": "text", "text": txt})
    body = {"model": model, "max_tokens": 800,
            "system": [{"type": "text", "text": SYSTEM, "cache_control": {"type": "ephemeral"}}],
            "messages": [{"role": "user", "content": parts}]}
    if "sonnet" in model:
        body["thinking"] = {"type": "between_tools"}
    elif "haiku" in model:
        body["thinking"] = {"type": "disabled"}  # Haiku 5.5 在 effort high 以下可關思考（官方文件）；API 若說不行會在下面退回不送
    return body


# 提早執行用：這些動作「需要的欄位」都出現了，就不必等後面的 think 寫完（不在表裡的動作照舊等整段回答）
EARLY_REQUIRED = {"tap": ("x", "y"), "longpress": ("x", "y"), "swipe": ("x1", "y1", "x2", "y2"), "type": ("text",),
                  "enter": (), "back": (), "home": (), "open": ("name",), "wait": (), "done": ("say",), "fail": ("say",)}


def core_before_think(text):
    """串流到一半的回答：「think」欄位開始了、而且它前面的欄位已經是一個完整的動作，就回傳（去掉 think 的）完整 JSON 字串，否則回 None。
    動作需要的欄位（座標、文字…）沒到齊也回 None——寧可多等一下，不能執行不完整的動作。"""
    m = re.search(r',\s*"think"\s*:', text)
    start = text.find("{")
    if not m or start < 0 or start > m.start():
        return None
    core = text[start:m.start()] + "}"
    try:
        obj = json.loads(core)
    except ValueError:
        return None
    need = EARLY_REQUIRED.get(obj.get("action")) if isinstance(obj, dict) else None
    if need is None or any(k not in obj for k in need):
        return None
    return core


def sse_events(resp):
    """Anthropic 串流回應（SSE）→ 一個個事件（dict）。"""
    for raw in resp:
        line = raw.decode("utf-8", "replace").strip()
        if line.startswith("data:"):
            try:
                yield json.loads(line[5:].strip())
            except ValueError:
                continue


def stream_consume(events, state, early=True):
    """把串流事件累積進 state（text 文字、usage 用量）。early=True 時，動作一完整就回傳去掉 think 的 JSON（還沒讀完）；讀完回 None。"""
    for ev in events:
        t = ev.get("type")
        if t == "content_block_start":
            state.setdefault("blocks", []).append((ev.get("content_block") or {}).get("type", "?"))
        elif t == "message_start":
            u = (ev.get("message") or {}).get("usage") or {}
            for k in ("input_tokens", "cache_read_input_tokens", "cache_creation_input_tokens"):
                state["usage"][k] = u.get(k, 0) or 0
        elif t == "content_block_delta" and (ev.get("delta") or {}).get("type") == "text_delta":
            state["text"] += ev["delta"].get("text", "")
            if early:
                core = core_before_think(state["text"])
                if core:
                    return core
        elif t == "message_delta":
            state["usage"]["output_tokens"] = (ev.get("usage") or {}).get("output_tokens", 0) or 0
            state["stop"] = (ev.get("delta") or {}).get("stop_reason") or state.get("stop")
        elif t == "error":
            raise RuntimeError(str(ev.get("error"))[:200])
    return None


LATE = {"thread": None, "state": None, "model": None}


def finish_late():
    """上一次回答是提早收下的：等背景把剩下的收完，回傳完整文字（含 think）；輸出 token 這時才記進用量。沒有就回 None。"""
    th, st = LATE["thread"], LATE["state"]
    LATE["thread"] = None
    if not th:
        return None
    th.join(10)
    add_stats({"input_tokens": 0, "output_tokens": st["usage"]["output_tokens"]}, LATE["model"])
    return st["text"]


def ask_claude_stream(task, hist, model):
    """串流版：動作一完整就回傳（不等 think 與後面的字寫完，每步省下輸出後半段的時間）；剩下的在背景收完。
    回傳 (文字, 用量)；出任何狀況回 None，呼叫端改用一般（非串流）版。"""
    finish_late()
    body = make_body(task, hist, model)
    body["stream"] = True
    req = urllib.request.Request("https://api.anthropic.com/v1/messages", data=json.dumps(body).encode(),
                                 headers={"x-api-key": KEY, "anthropic-version": "2023-06-01", "content-type": "application/json"})
    resp = urllib.request.urlopen(req, timeout=30)
    state = {"text": "", "usage": {"input_tokens": 0, "output_tokens": 0, "cache_read_input_tokens": 0, "cache_creation_input_tokens": 0}}
    events = sse_events(resp)
    core = stream_consume(events, state)
    LATE["last_state"] = state   # 給失敗時印診斷用
    if core is None:
        resp.close()
        return state["text"], state["usage"]
    def rest():
        try:
            stream_consume(events, state, early=False)
        except Exception:
            pass
        finally:
            try:
                resp.close()
            except Exception:
                pass
    th = threading.Thread(target=rest, daemon=True)
    th.start()
    LATE.update(thread=th, state=state, model=model)
    return core, dict(state["usage"])


def ask_claude_fast(task, hist, model):
    """每步問模型：優先串流提早執行；串流失敗（連線、格式）就用一般版，連續失敗 2 次之後本次任務不再嘗試串流。預設關閉，AGENT_STREAM=1 才開。"""
    if os.environ.get("AGENT_STREAM", "0") == "1" and STATS.get("stream_fail", 0) < 2:   # 預設關：實測沒有明顯加速
        try:
            out, info = ask_claude_stream(task, hist, model)
            if out and parse(out) and "action" in parse(out):
                STATS["stream_fail"] = 0
                return out, info
            st = LATE.get("last_state") or {}
            raise ValueError("串流回答沒有可用的動作（結束原因=%s 內容類型=%s 文字=%r）" % (st.get("stop"), st.get("blocks"), (st.get("text") or "")[:60]))
        except Exception as e:
            STATS["stream_fail"] = STATS.get("stream_fail", 0) + 1
            print("[串流失敗（%s），改用一般方式]" % str(e)[:80])
    return ask_claude(task, hist, model)


def ask_claude(task, hist, model):
    body = make_body(task, hist, model)
    for attempt in (0, 1, 2):
        req = urllib.request.Request("https://api.anthropic.com/v1/messages",
            data=json.dumps(body).encode(),
            headers={"x-api-key": KEY, "anthropic-version": "2023-06-01",
                     "content-type": "application/json"})
        try:
            r = json.loads(urllib.request.urlopen(req, timeout=30 if attempt < 2 else 60).read())   # 平常 2～5 秒就回；30 秒沒回就重試（最後一次給 60 秒）
            out = "".join(b.get("text", "") for b in r["content"] if b.get("type") == "text")
            if not out.strip():
                print("[空回答] 結束原因=%s 內容類型=%s 用量=%s" % (r.get("stop_reason"), [b.get("type") for b in r.get("content", [])], r.get("usage")))
            if not out.strip() and r.get("stop_reason") == "max_tokens" and body["max_tokens"] < 3000:
                # 思考把額度用光、還沒寫出答案（實測：遊戲畫面複雜時 300 token 全拿去思考，回空字串）：放大額度重問一次
                print("[回答被截斷（思考用光 %s token），放大額度重問]" % body["max_tokens"])
                add_stats(r["usage"], model)
                body["max_tokens"] = 3000
                continue
            return out, r["usage"]
        except urllib.error.HTTPError as e:
            detail = e.read().decode()[:200]
            if e.code == 400 and "cache" in detail.lower() and not isinstance(body["system"], str):
                body["system"] = SYSTEM  # 這個模型不吃 cache_control：退回一般寫法
                continue
            if e.code == 400 and "thinking" in detail.lower() and "thinking" in body:
                print("[%s 不接受 thinking 參數，改成不送] %s" % (short(model), detail[:120]))
                del body["thinking"]
                continue
            return None, "HTTP %s %s" % (e.code, detail)
        except Exception as e:
            if isinstance(e, OSError) and attempt < 2:  # 逾時／連線中斷：換一條連線再問，不要乾等
                print("[API 沒回應（%s），重試 %d/2]" % (str(e)[:60], attempt + 1))
                continue
            return None, str(e)


SEARCH_MODEL = "claude-sonnet-5-5"
SEARCH_MAX = 2  # 一個任務最多搜尋幾次（每次約 $0.01 + token，官方價每千次 $10）


def web_search(query):
    """用 Anthropic 的網頁搜尋（server tool）查一個問題，回傳繁體中文摘要；失敗回傳 None。
    只送模型寫的關鍵字，不送截圖。基本款 web_search_20250305（不用動態過濾，少一層程式執行）。"""
    msgs = [{"role": "user", "content": "請用繁體中文回答，根據網路搜尋結果，只講事實，不超過120字：" + query}]
    tool = {"type": "web_search_20250305", "name": "web_search", "max_uses": 1,
            "user_location": {"type": "approximate", "country": "TW", "timezone": "Asia/Taipei"}}
    n_search, text = 0, ""
    for _ in range(3):  # pause_turn：原樣把暫停的回答送回去繼續
        body = {"model": SEARCH_MODEL, "max_tokens": 700, "tools": [tool], "messages": msgs,
                "thinking": {"type": "between_tools"}}
        req = urllib.request.Request("https://api.anthropic.com/v1/messages", data=json.dumps(body).encode(),
            headers={"x-api-key": KEY, "anthropic-version": "2023-06-01", "content-type": "application/json"})
        try:
            r = json.loads(urllib.request.urlopen(req, timeout=60).read())
        except urllib.error.HTTPError as e:
            print("搜尋失敗: HTTP %s %s" % (e.code, e.read().decode()[:200]))  # 例如 Console 把網頁搜尋關了會是 400
            return None
        except Exception as e:
            print("搜尋失敗:", e)
            return None
        u = r.get("usage", {})
        n_search += (u.get("server_tool_use") or {}).get("web_search_requests", 0) or 0
        STATS["in"] += u.get("input_tokens", 0)
        STATS["out"] += u.get("output_tokens", 0)
        STATS["cost"] = STATS.get("cost", 0) + u.get("input_tokens", 0) * 2 / 1e6 + u.get("output_tokens", 0) * 10 / 1e6
        for b in r.get("content", []):
            if b.get("type") == "web_search_tool_result" and isinstance(b.get("content"), dict):
                print("搜尋工具錯誤:", b["content"].get("error_code"))  # 錯誤是 200 回應裡的物件，不是例外
        text += "".join(b.get("text", "") for b in r.get("content", []) if b.get("type") == "text")
        if r.get("stop_reason") != "pause_turn":
            break
        msgs.append({"role": "assistant", "content": r["content"]})
    STATS["searches"] = STATS.get("searches", 0) + n_search
    STATS["cost"] = STATS.get("cost", 0) + n_search * 0.01
    return text.strip() or None


def parse(out):
    """從模型回答裡取「最後一個」JSON 物件。要能處理巢狀（multitouch 的 fingers 是物件陣列），所以不用正規式切，
    改成從每個 { 開始試著解碼，成功就跳過它的內容。"""
    dec, found, i = json.JSONDecoder(), None, 0
    out = out or ""
    while True:
        i = out.find("{", i)
        if i < 0:
            return found
        try:
            obj, end = dec.raw_decode(out, i)
        except ValueError:
            i += 1
            continue
        if isinstance(obj, dict):
            found = obj
        i = end


def can_escalate(level, why, hist):
    if level >= len(MODELS) - 1 or STATS["esc"] >= MAX_ESC:
        return False
    STATS["esc"] += 1
    hist.append("上一步卡住（" + why + "），請換個方法，不要重複剛才的動作")
    print("卡住（%s），升級為 %s 再試一次" % (why, MODELS[level + 1]))
    return True


SMALL_WORDS = ["圖示", "圖標", "icon", "按鈕", "開關", "鈕", "選單", "勾選", "關閉", "×", "✕"]  # 目標通常很小，座標容易偏
TAP_TOL = 40  # 兩次回答的座標（0~1000）差在這個範圍內就算一致，約螢幕寬的 4%


def add_stats(info, model):
    """把一次 API 回應的用量記進 STATS（輸入含快取讀寫）。"""
    cr, cw = info.get("cache_read_input_tokens", 0) or 0, info.get("cache_creation_input_tokens", 0) or 0
    STATS["in"] += info["input_tokens"] + cr + cw
    STATS["out"] += info["output_tokens"]
    STATS["cr"], STATS["cw"] = STATS.get("cr", 0) + cr, STATS.get("cw", 0) + cw
    pi, po = price(model)
    # 快取讀約 0.1 倍、快取寫約 1.25 倍輸入價（記憶中的官方做法，未對 Console 驗證）
    STATS["cost"] = STATS.get("cost", 0) + (info["input_tokens"] + cr * 0.1 + cw * 1.25) * pi / 1e6 + info["output_tokens"] * po / 1e6
    return cr, cw


def pick_point(pts, tol=TAP_TOL):
    """pts = [(x,y), ...]（照問的順序）。前兩次一致回平均；否則看有沒有任兩次一致；都不一致回 None。"""
    for i in range(len(pts)):
        for j in range(i + 1, len(pts)):
            if abs(pts[i][0] - pts[j][0]) <= tol and abs(pts[i][1] - pts[j][1]) <= tol:
                return ((pts[i][0] + pts[j][0]) / 2, (pts[i][1] + pts[j][1]) / 2)
    return None


def verify_tap(task, hist, model, first):
    """小目標再問一次同一張畫面；兩次差太多就問第三次，取一致的兩次。回傳 (x, y)（0~1000）。"""
    pts = [first]
    for _ in range(2):
        out, info = ask_claude(task, hist, model)
        if out is None:
            break
        add_stats(info, model)
        act = parse(out)
        try:
            if act and act.get("action") == "tap":
                pts.append((float(act["x"]), float(act["y"])))
        except (KeyError, TypeError, ValueError):
            pass
        pt = pick_point(pts)
        if pt:
            if len(pts) > 2:
                print("座標複查：問了 %d 次，取一致的兩次 %s" % (len(pts), pts))
            return pt
    if len(pts) > 1:
        print("座標複查：幾次回答都不一致 %s，用第一次（原本的答案）" % pts)
    return pts[0]


def main():
    if len(sys.argv) < 2:
        print('用法: python ~/agent2.py "任務" [最大步數]')
        return
    task = sys.argv[1]
    try:
        print("腳本版本:", open(os.path.expanduser("~/.phoneai_version")).read().strip())  # bash install.sh 時寫入
    except OSError:
        print("腳本版本: 未知（用 git pull 後再跑一次 bash install.sh 就會有）")
    TASK_TEXT.append(task)
    maxsteps = int(sys.argv[2]) if len(sys.argv) > 2 else 12
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
    if not bg_setup():
        return
    wait = float(os.environ.get("AGENT_WAIT", "0"))
    if wait >= 3:
        print("%d 秒內切到起始畫面..." % wait)
    time.sleep(wait)
    level = 0
    hist, last_hash, same, last_tap = [], None, 0, None
    PRE_OPEN = PRE_INTENT = None
    ADAPT_THR = None   # 任務在校準過的 App（蝦皮、YouTube）裡時，點擊後的等待也改成看畫面
    STATS["t0"] = time.time()
    STATS["hist"] = hist
    LESSONS[:] = []
    if memory and os.environ.get("AGENT_LESSONS", "1") != "0":
        try:
            LESSONS.extend(memory.lessons(None))  # App 還不知道時先帶通用的；選好 App 後下面再補
        except Exception:
            pass
    sigs = []  # 最近動作的簽名（偵測一直用同一招）
    unknown = 0  # 看不懂的動作累計次數（3 次就停，免得空轉）
    try:
        t_plan = time.time()
        PRE_INTENT = ai_plan_first(task)
        STATS["plan_secs"] = time.time() - t_plan
    except Exception as e:
        print("第一步規劃出錯（照原本流程）:", repr(e))
        PRE_INTENT = None
    if PRE_INTENT:
        ADAPT_THR = PRE_INTENT.get("tap_richness")
        SHORT_MODE[0] = PRE_INTENT.get("source") == "rule" and os.environ.get("AGENT_SHORT", "1") != "0"   # 只有規則命中的單純搜尋才用簡短模式
        pk = intents.PLAN_PKG.get(PRE_INTENT["name"])
        if pk:
            PRE_APP[:] = [pk]
            if memory:
                LESSONS[:] = memory.lessons(pk)
        print("第一步規劃：%s（直接用命令，不用先開 App、不用先看畫面）" % PRE_INTENT["desc"])
    elif os.environ.get("AGENT_PRE_OPEN", "1") != "0" and applist:
        try:
            PRE_OPEN = ai_pick_app(task, applist.load())
            if PRE_OPEN:
                PRE_APP[:] = [PRE_OPEN[1]]
                if memory:
                    LESSONS[:] = memory.lessons(PRE_OPEN[1])
        except Exception:
            PRE_OPEN = None
    for i in range(1, maxsteps * 8 + 20):  # 真正的上限是下面的 i - esc > maxsteps；連續動作的後續動作會加到 queued，不算步數
        if i - STATS["esc"] - STATS.get("queued", 0) > maxsteps:
            break
        t_shot = t_verify = 0.0  # 這一步各階段花的秒數（印在每步最後的 [耗時]，用來找出哪裡慢）
        t_loop0 = time.time()
        queued = PENDING.pop(0) if PENDING else None
        TEXT_ONLY = bool(STATS.pop("text_only", False)) and not queued   # 上一步是唯讀查詢：這一步不截圖，只靠查詢結果直接回答
        STATS["no_img"] = TEXT_ONLY
        STATS.pop("tree_text", None)
        if queued:
            STATS["queued"] = STATS.get("queued", 0) + 1  # 連續動作的後續動作不算步數（也不能動 esc：那是升級模型的次數）
        STATS["steps"] = i - STATS["esc"] - STATS.get("queued", 0)
        if BG["on"]:
            wait_user_control(OV, hist)  # 使用者在預覽上接手操作時，AI 先停下來
        if queued:
            print("  · 連續動作（不截圖）")
        else:
            print("\n=== 第 %d/%d 步（模型: %s）===" % (i - STATS["esc"] - STATS.get("queued", 0), maxsteps, MODELS[level]))
            status("第 %d/%d 步 · 看畫面中（%s）" % (i - STATS["esc"] - STATS.get("queued", 0), maxsteps, short(MODELS[level])),
                   i - STATS["esc"] - STATS.get("queued", 0), maxsteps)
            if OV:
                OV.hide()  # 截圖前清掉漣漪，免得 Claude 看到自己的特效
            t_s0 = time.time()
            try:
                if (i == 1 and (PRE_OPEN or PRE_INTENT)) or TEXT_ONLY:
                    w, h, hh = 0, 0, None   # 第一步是程式預先決定的（開 App／命令），或剛查完資料：不需要畫面，省一次截圖
                    if TEXT_ONLY:
                        print("  · 剛查完資料：這一步不截圖，直接用查詢結果回答")
                else:
                    w, h, hh = grab_screen()
            except Exception as e:
                print("截圖失敗:", e)
                return
            if os.environ.get("AGENT_DEBUG"):
                su("cp %s /sdcard/Download/agent_step_%d.png" % (SMALL, i))
            if hh is not None and hh == last_hash:  # 可能只是畫面還沒更新完：多等一下再拍一次，才算沒變
                time.sleep(2)
                if OV:
                    OV.hide()  # 重拍前再清一次漣漪
                w, h, hh = grab_screen()
            t_shot = time.time() - t_s0
            if hh is not None:
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
        if queued:
            queued.setdefault("wait", 0.4)
            out, info = json.dumps(queued, ensure_ascii=False), {"input_tokens": 0, "output_tokens": 0}
        elif i == 1 and PRE_INTENT:  # 第一步可以直接用命令：Haiku 規劃、程式驗證過，不用看畫面
            out = json.dumps({"action": "intent", "name": PRE_INTENT["name"], "args": PRE_INTENT["args"], "wait": PRE_INTENT["wait"]}, ensure_ascii=False)
            info = {"input_tokens": 0, "output_tokens": 0}
        elif i == 1 and PRE_OPEN:  # 任務提到某個 App：直接開，不用先花一次模型呼叫看畫面（預設的第一步幾乎都是 open 或 home）
            out = json.dumps({"action": "open", "name": PRE_OPEN[0], "pkg": PRE_OPEN[1], "wait": 3}, ensure_ascii=False)
            info = {"input_tokens": 0, "output_tokens": 0}
            print("AI 判斷先開「%s」，不用先看畫面" % PRE_OPEN[0])
        else:
            out, info = ask_claude_fast(task, hist, MODELS[level])
        if out is None:
            print("呼叫失敗:", info)
            prob = intents.api_problem(str(info)) if intents else None
            say(prob or "連線失敗", card=bool(prob))   # 額度用完、Key 錯等：用卡片把原因顯示給使用者
            return
        t_ai = time.time() - t0
        t_act0 = time.time()   # 從這裡到固定等待之前 = 執行動作本身（用 root 點擊、啟動命令、狀態通知…），以前沒算進 [耗時]
        cr, cw = add_stats(info, MODELS[level])
        print("Claude: %s (%.1f 秒, %d入/%d出, 快取讀%d/寫%d)" % (out.strip(), time.time() - t0,
              info["input_tokens"] + cr + cw, info["output_tokens"], cr, cw))
        act = parse(out)
        if act and "action" in act:
            status("第 %d/%d 步 · %s" % (i - STATS["esc"] - STATS.get("queued", 0), maxsteps, describe(act)), i - STATS["esc"] - STATS.get("queued", 0), maxsteps)
        if os.environ.get("AGENT_TEST_ESCALATE") and level == 0:
            act = None  # 測試用
        if not act or "action" not in act:
            if can_escalate(level, "回答格式錯誤", hist):
                level += 1
                continue
            print("看不懂回答，停止")
            return
        a = act["action"]
        if a == "need_image" and TREE_MODE:   # 元件清單不夠用：下一步改附截圖（這一步不算動作）
            STATS["force_img"] = True
            hist.append("元件清單看不出來，下一步改附截圖")
            continue
        if TEXT_ONLY and a not in ("done", "fail", "shell", "intent", "wait", "search", "ask_user", "ask", "open", "cmd"):
            print("沒有畫面時 AI 想操作畫面（%s），改成先等一下，下一步附畫面" % a)   # 沒看到畫面不能亂點
            hist.append("剛才沒附畫面，不能直接操作；下一步會給你畫面")
            act, a = {"action": "wait", "wait": 0.3}, "wait"
        if a not in ("done", "fail", "ask_user", "search", "shell") and not (a == "intent" and act.get("name") == "query"):
            SHEET["keep"] = False   # 要動畫面了：先把面板收起來
            close_sheet()
        if DRAG["down"] and not a.startswith("drag_") and a != "wait":
            release_drag(hist)
        rem = act.get("remember")
        if isinstance(rem, str) and memory and lesson_ok(rem) and os.environ.get("AGENT_LESSONS", "1") != "0" and STATS.get("remember_n", 0) < MAX_REMEMBER:
            STATS["remember_n"] = STATS.get("remember_n", 0) + 1
            try:  # 摸索出來的規律馬上記下來（不用等任務結束），下一步就會看到
                STATS["remembered"] = True
                if memory.add_lesson(rem, PRE_APP[0] if PRE_APP else None):
                    LESSONS.append(rem.strip()[:200])
                    print("已記下經驗：" + rem.strip()[:200])
                    subprocess.run(["termux-notification", "--id", "7195", "--title", "AI 學到的經驗（可在 App 記憶頁刪除）", "--content", rem.strip()[:200]],
                                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            except Exception as e:
                print("記下經驗出錯:", repr(e))
        if a == "batch":
            subs = filter_batch(act.get("actions"))
            if not subs:
                hist.append("batch 裡沒有可執行的動作，請直接給單一動作")
            else:
                PENDING[:] = subs
                hist.append("連續執行%d個動作（中間不看畫面）" % len(subs))
                print("連續動作 %d 個：%s" % (len(subs), [x.get("action") for x in subs]))
            continue
        if a == "done":
            say(act.get("say", "完成了"), card=True)
            return
        if a == "fail":
            if can_escalate(level, "模型說做不到", hist):
                level += 1
                continue
            say(act.get("say", "我做不到"), card=True)
            return
        if a == "search":
            q = str(act.get("q", "")).strip()[:100]
            if not q or STATS.get("searches_asked", 0) >= SEARCH_MAX:
                hist.append("搜尋次數已用完或沒有關鍵字，請改用畫面操作")
                continue
            STATS["searches_asked"] = STATS.get("searches_asked", 0) + 1
            status("第 %d/%d 步 · 搜尋網路中" % (i - STATS["esc"] - STATS.get("queued", 0), maxsteps), i - STATS["esc"] - STATS.get("queued", 0), maxsteps)
            print("搜尋:", q)
            ans = web_search(q)
            hist.append(("搜尋「%s」得到：%s" % (q, ans[:300])) if ans else ("搜尋「%s」失敗，請改用畫面操作" % q))
            same, last_tap = 0, None
            continue
        if a == "ask_user":
            q = str(act.get("question", "")).strip()
            if not q:
                hist.append("ask_user 要有 question，沒有執行；請照格式重新給")
                continue
            status("第 %d/%d 步 · 等你回答" % (i - STATS["esc"] - STATS.get("queued", 0), maxsteps), i - STATS["esc"] - STATS.get("queued", 0), maxsteps)
            print("問使用者:", q)
            ans = ask_user_text(q)
            if ans is None:
                say("沒辦法向你提問，已停止")
                return
            if not ans:
                say("沒有收到回答，已取消")
                return
            print("使用者回答:", ans)
            hist.append("你問了使用者「%s」，使用者回答：%s" % (q[:80], ans[:300]))
            continue
        if a == "ask":
            why = str(act.get("why", ""))
            if act.get("manual") is True:
                bg_ui = bool(BG["on"] and OV and OV.ok)
                if bg_ui:
                    OV.takeover(True, why)  # 自動打開預覽並進入接手模式：使用者直接在預覽上操作背景螢幕
                    alert("需要你接手：" + why)
                agreed = confirm("需要你自己動手：" + why + "。做完按「是」繼續，不做按「否」取消。")
                if bg_ui:
                    OV.takeover(False)
                if not agreed:
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
            if a == "pick":   # 元件清單模式：點第 n 號元件（程式換算成中心座標，之後走一般點擊流程）
                tp = STATS.get("tree_items")
                hit = intents.tree_pick(tp[0], act.get("n"), tp[1], tp[2]) if (tp and intents) else None
                if hit is None:
                    print("元件編號不在清單裡:", act.get("n"))
                    hist.append("元件編號 %s 不在清單裡；請用清單裡的編號，或回 need_image 改看截圖" % act.get("n"))
                    continue
                act = {"action": "tap", "x": hit[0], "y": hit[1], "what": hit[2], "wait": act.get("wait")}
                a = "tap"
            if a == "tap":
                what = str(act.get("what", ""))
                if any(b in what for b in CONFIRM):
                    if not confirm("要點擊「" + what + "」，確定嗎？"):
                        say("已取消")
                        return
                tx, ty = float(act["x"]), float(act["y"])
                if os.environ.get("AGENT_VERIFY", "1") != "0" and not STATS.get("tree_text") and any(k in what for k in SMALL_WORDS):
                    t_v0 = time.time()
                    tx, ty = verify_tap(task, hist, MODELS[level], (tx, ty))  # 小目標：多問一次，座標差太多就再問
                    t_verify = time.time() - t_v0
                px = min(max(int(tx * w / 1000), 0), w - 1)
                py = min(max(int(ty * h / 1000), 0), h - 1)
                if last_tap == (px, py):
                    if can_escalate(level, "連續點同一位置", hist):
                        level += 1
                        last_tap = None
                        continue
                    say("連續點同一位置，停止")
                    return
                last_tap = (px, py)
                print("點擊 (%d,%d) %s" % (px, py, what))
                fd = fx_display()
                if OV and fd is not None:
                    OV.ripple(px, py, fd)
                su(inp("tap %d %d" % (px, py)))
                hist.append("點了" + what)
            elif a == "scroll":
                down = act.get("dir") == "down"
                a1, a2 = (0.5, 0.25) if BG["on"] else (0.7, 0.3)  # 背景模式：App 可能是小浮動視窗，只在畫面中間偏上滑，才會落在視窗裡
                y1, y2 = (int(h * a1), int(h * a2)) if down else (int(h * a2), int(h * a1))
                print("捲動 (%d,%d)->(%d,%d)" % (w // 2, y1, w // 2, y2))
                fd = fx_display()
                if OV and fd is not None:
                    OV.swipe(w // 2, y1, w // 2, y2, fd)
                su(inp("swipe %d %d %d %d 300" % (w // 2, y1, w // 2, y2)))
                hist.append("往下捲動" if down else "往上捲動")
            elif a in ("swipe", "longpress", "drag", "multitouch"):
                g = build_gesture(act, w, h)
                if not g:
                    hist.append("手勢參數錯誤，沒有執行")
                else:
                    fingers, hold = g
                    print("%s %s 按住%dms" % (a, [(f["x1"], f["y1"], f["x2"], f["y2"], f["ms"]) for f in fingers], hold))
                    fd = fx_display()
                    if OV and fd is not None:
                        for f in fingers:
                            if a == "longpress":
                                OV.hold(f["x1"], f["y1"], f["ms"], fd)             # 一圈慢慢往外，放手時到最外圈
                            elif a == "drag":
                                OV.hold(f["x1"], f["y1"], hold, fd)                # 先按住的那段
                                OV.swipe(f["x1"], f["y1"], f["x2"], f["y2"], fd, delay=hold)   # 按住結束才畫拖曳軌跡
                            else:
                                OV.swipe(f["x1"], f["y1"], f["x2"], f["y2"], fd)
                    res = OV.gesture(fingers, hold, fd or 0) if (OV and OV.ok and (len(fingers) > 1 or hold)) else None
                    if res != "ok":
                        cmd = gesture_fallback_cmd(fingers, hold)
                        if cmd:
                            su(cmd)
                            if res in ("nocap", "nosvc", "fail"):
                                print("手勢功能沒成功(%s)，改用 input 指令" % res)
                        else:
                            hist.append("多指手勢沒成功(%s)。若是 nocap：到 設定→無障礙 把 PhoneAI Overlay 關掉再打開" % res)
                            print("多指手勢失敗:", res)
                    hist.append({"swipe": "滑動", "longpress": "長按", "drag": "拖曳", "multitouch": "多指手勢"}[a])
            elif a == "type":
                text = str(act.get("text", ""))
                if sensitive_task or any(b in text for b in CONFIRM):
                    if not confirm("要輸入文字：「" + text + "」，確定嗎？"):
                        say("已取消")
                        return
                if is_dup_type(hist, text) and STATS.setdefault("dup_skips", {}).get(text, 0) < 1:
                    # 同樣的字剛輸入過：這次不執行（只擋一次，之後如果模型堅持就照做，避免卡死），告訴模型「已經輸入過了」
                    STATS["dup_skips"][text] = STATS["dup_skips"].get(text, 0) + 1
                    print("略過重複輸入:", text)
                    hist.append("「%s」剛剛已經輸入並送出過了，不要重複輸入；畫面沒變就先等幾秒再看，或改用別的操作" % text)
                    time.sleep(2)
                    continue
                want_enter = act.get("enter") is True
                if want_enter and sensitive_task and not confirm("輸入後要按 Enter 送出，確定嗎？"):
                    say("已取消")
                    return
                if act.get("clear", True) is not False:
                    clear_field()   # 先清空輸入框（以前要 AI 先截圖發現有舊字、點 X、再輸入，多花好幾步）
                    time.sleep(0.2)
                ok = type_text(text)
                print("輸入文字:", text, "(成功)" if ok else "(失敗)")
                hist.append("輸入了「" + text + "」" if ok else "輸入文字失敗")
                if ok and want_enter:
                    time.sleep(0.3)   # 讓輸入法吃完剛貼上的字
                    su(inp("keyevent 66"))
                    hist.append("按了Enter送出「" + text + "」（頁面可能要幾秒才載入完，下一步先看畫面，不要重複輸入同樣的字）")
            elif a == "enter":
                if sensitive_task and not confirm("要按 Enter 送出，確定嗎？"):
                    say("已取消")
                    return
                su(inp("keyevent 66"))
                last_typed = next((h for h in reversed(hist[-3:]) if h.startswith("輸入了「")), None)
                hist.append(("按了Enter送出" + last_typed[3:] + "（頁面可能要幾秒才載入完，下一步先看畫面，不要重複輸入同樣的字）")
                            if last_typed else "按了Enter")
            elif a == "open":
                pkg = resolve_pkg(str(act.get("pkg", "")), str(act.get("name", "")))
                if pkg in NO_OPEN and not confirm("要開啟應用商店（可能會安裝 App），確定嗎？"):
                    say("已取消")
                    return
                ok, msg = open_app(pkg)
                if BG["on"]:
                    # 背景模式：開不起來、或開了卻消失／一直黑屏，就自動退回前景（並提醒使用者），不讓 AI 在背景空轉
                    why = msg if (not ok and "第二螢幕" in msg) else None  # 沒安裝之類不是背景才有的問題，照舊處理
                    if ok and os.environ.get("AGENT_TEST_BG_FAIL"):
                        why = "測試用：假裝這個 App 不能在背景執行"  # 手邊沒有真的會失敗的 App 時，用來測「退回前景並提醒」
                    elif ok:
                        alive, why2 = bg_app_ok(pkg)
                        why = None if alive else why2
                    if why:
                        # 改到前景會搶走使用者正在用的主螢幕，所以先問；沒回應/按「否」就取消任務（跟其他確認框一樣，沒回應 = 不同意）
                        alert("這個 App 在背景不能用（%s），等你確認" % why)
                        if not confirm("這個 App 在背景不能用（%s）。要改在前景（你的主螢幕）執行嗎？按「否」會取消任務。" % why):
                            bg_cleanup()
                            BG.update(on=False, vd=False, id=None, sf=None, ripple=None)
                            BG["opened"].clear()
                            say("已取消（這個 App 不支援背景）")
                            return
                        bg_fallback(pkg, why)
                        status("改在前景執行（這個 App 不支援背景）", i - STATS["esc"] - STATS.get("queued", 0), maxsteps)
                        ok, msg = open_app(pkg)  # BG 已關，這次開在主螢幕
                        msg = "背景模式不能用（%s），已改在主螢幕前景操作。%s" % (why, msg)
                print("開啟App:", msg)
                hist.append(msg if ok else "開App失敗（" + msg + "），改用畫面操作")
                last_tap = None
            elif a == "intent":
                nm = str(act.get("name", ""))
                try:
                    b = intents.build(nm, act.get("args"), display=BG["id"] if BG["on"] and BG["id"] else None, has_pkg=has_pkg) if intents else None
                    if b is None:
                        raise ValueError("命令層沒有載入")
                except Exception as e:
                    if intents and not isinstance(e, intents.IntentError):
                        raise
                    print("命令不能用:", e)
                    hist.append("intent「%s」不能用：%s；請換別的命令或改用畫面操作" % (nm, e))
                else:
                    if b["ask"] and not confirm(b["ask"]):
                        say("已取消")
                        return
                    res = su(b["cmd"]).strip()
                    flat = " ".join(res.split())
                    print("命令:", b["desc"], "→", flat[-120:])
                    if b["query"]:
                        SHEET["keep"] = True   # 查資料：結果已經在手上，下一步多半直接 done 回答，面板（光球）留著
                        STATS["text_only"] = True   # 下一步不截圖
                        hist.append("查詢結果（%s）：%s" % (b["desc"], flat[:300] or "（沒有輸出）"))
                    elif re.search(r"Error|Exception|unable to resolve|Permission Denial|not found", res, re.I):
                        hist.append("命令「%s」失敗（%s），請改用畫面操作" % (b["desc"], flat[-100:]))
                    else:
                        hist.append("用命令做了：" + b["desc"])
                    act.setdefault("wait", b["wait"])   # AI 有指定等待秒數就照它的；沒有用命令建議的
            elif a == "shell":   # 唯讀查詢：白名單檢查在 intents.run_read_only；不通過就告訴 AI 原因，不執行
                SHEET["keep"] = True
                STATS["text_only"] = True   # 下一步不截圖（查詢結果在歷史裡）
                cmd_s = str(act.get("cmd", ""))
                if intents is None:
                    hist.append("唯讀查詢不能用（命令層沒有載入）")
                else:
                    res, why = intents.run_read_only(cmd_s, su)
                    if why:
                        print("唯讀查詢被擋:", why)
                        hist.append("唯讀查詢「%s」不能執行：%s；請換別的查法" % (cmd_s[:80], why))
                    else:
                        flat = " ".join(res.split())
                        print("唯讀查詢:", cmd_s, "→", flat[-120:])
                        hist.append("查詢結果（%s）：%s" % (cmd_s[:80], flat[:600] or "（沒有輸出）"))
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
                su(inp("keyevent 4"))
                hist.append("按了返回")
            elif a == "home":
                su(inp("keyevent 3"))
                hist.append("回主畫面")
            elif a in ("drag_start", "drag_move", "drag_end", "drag_cancel"):
                p2 = lambda v, size: clampi(float(v) * size / 1000, 0, size - 1)
                if a == "drag_start" and STATS.get("measures", 0) >= MAX_MEASURES:
                    # 實測：AI 量過一次規律後還是一直重複量，25 步全花光。超過次數就不再執行，逼它用 remember 記下來、改用 drag / batch
                    hist.append("你已經分段拖曳量過%d次了，不要再量。現在用 remember 記下你量到的規律，之後直接用 drag 或 batch 做" % STATS["measures"])
                    print("分段拖曳量測已達上限 %d 次，不再執行" % MAX_MEASURES)
                    cur = tgt = None
                elif a == "drag_start":
                    STATS["measures"] = STATS.get("measures", 0) + 1
                    if STATS["measures"] >= 2 and not STATS.get("remembered"):
                        hist.append("你已經量了%d次還沒用 remember 記下規律；記下來，之後就不用每次都量" % STATS["measures"])
                    release_drag(hist, "重新開始拖曳")
                    cur, tgt = (p2(act["x"], w), p2(act["y"], h)), ((p2(act["to_x"], w), p2(act["to_y"], h)) if "to_x" in act else None)
                    op = "start"
                else:
                    if not DRAG["down"]:
                        hist.append("現在沒有按著的手指，請先用 drag_start")
                        cur = tgt = None
                    else:
                        cur = (DRAG["x"], DRAG["y"])
                        tgt = (p2(act["x"], w), p2(act["y"], h)) if "x" in act else None
                        op = a[5:]
                if cur is not None:
                    script, end = drag_script(op, cur, tgt)
                    print("%s %s -> %s" % (a, cur, tgt))
                    fd = fx_display()
                    if OV and fd is not None:
                        if op == "start":
                            OV.hold(cur[0], cur[1], 600, fd)
                        if tgt and tgt != cur:
                            OV.swipe(cur[0], cur[1], tgt[0], tgt[1], fd, delay=350 if op == "start" else 0)
                    out = su(script)
                    if "xception" in (out or "") or "sage" in (out or ""):
                        print("motionevent 失敗:", (out or "")[:200])
                        hist.append("拖曳指令失敗（這支手機的 input 可能不支援 motionevent），改用 drag 動作")
                        DRAG["down"] = False
                    else:
                        DRAG.update(down=op in ("start", "move"), x=end[0], y=end[1])
                        hist.append({"start": "按住並拖到(%d,%d)，手指還按著，請看畫面決定微調或放開" % end, "move": "手指移到(%d,%d)，還按著" % end,
                                     "end": "放開手指", "cancel": "取消拖曳"}[op])
            elif a == "wait":
                hist.append("等了一下（畫面載入中）")  # 什麼都不做，下面照 wait 秒數等完再截圖
            else:
                print("未知動作:", a)
                hist.append("「%s」不是可用的動作，請改用清單裡的動作" % str(a)[:20])
                unknown += 1
                if unknown >= 3:
                    return
        except (KeyError, ValueError, TypeError) as e:
            # 實測：模型偶爾打錯欄位名（例如把 to_y 寫成第二個 y），以前整個任務就直接結束。改成告訴它哪裡錯、讓它重給，累計 3 次才停
            print("動作資料不完整:", repr(e))
            hist.append("上一個動作的欄位不完整或格式錯誤（%s），沒有執行；請照格式重新給完整的欄位" % str(e)[:40])
            unknown += 1
            if unknown >= 3:
                return
        th = act.get("think")
        if not th:  # 串流提早執行的回答沒有 think：這時背景早就收完了，補上
            full = parse(finish_late() or "")
            th = full.get("think") if full else None
        if isinstance(th, str) and th.strip():
            hist.append("（我的想法：%s）" % th.strip()[:60])
            print("想法:", th.strip()[:80])
        sigs.append(action_sig(act))
        if same_trick(sigs):
            # 實測（Block Blast）：drag_start→cancel 同一個位置來回好幾次。連續 3 次同一招就明講，要它換思路
            hist.append("注意：你已經連續3次用幾乎一樣的動作，結果顯然沒變好。不要再微調同一招，換一個完全不同的做法")
            print("偵測到連續 3 次同一招，提醒 AI 換方法")
            sigs.clear()
        try:
            ws = min(max(float(act.get("wait", 3.0 if a == "open" else DEFAULT_WAIT)), MIN_WAIT), MAX_WAIT)
        except (TypeError, ValueError):
            ws = DEFAULT_WAIT
        # 等畫面穩定預設關（AGENT_SETTLE=1 才開）：實測「畫面不動」不等於「載入完」，太早放行讓 AI 看到半成品畫面、點錯、多繞好幾步
        t_act = time.time() - t_act0
        if (i == 1 and PRE_INTENT and PRE_INTENT.get("min_richness") and not PENDING and a == "intent"
                and os.environ.get("AGENT_SHOT_FAST", "1") != "0" and not os.environ.get("AGENT_PLAN_WAIT")):
            # 校準過的命令（蝦皮、YouTube）：看畫面載入完（豐富度夠）就繼續，不傻等固定秒數
            waited = wait_loaded(PRE_INTENT.get("min_wait", 1.5), 9.0, PRE_INTENT["min_richness"])
        elif (ADAPT_THR and a == "tap" and not PENDING and os.environ.get("AGENT_SHOT_FAST", "1") != "0"
              and os.environ.get("AGENT_ADAPT", "1") != "0" and not os.environ.get("AGENT_PLAN_WAIT")):
            # 已經知道這個任務在蝦皮／YouTube（第一步命令校準過）：點擊後看「已變、穩定、豐富」就繼續，不用傻等 AI 寫的秒數
            waited = wait_changed(ws, ADAPT_THR, LAST_SIG[0], before_poll=(OV.hide if OV else None))
        elif PENDING or os.environ.get("AGENT_SETTLE", "0") != "1" or os.environ.get("AGENT_SHOT_FAST", "1") == "0":
            print("等 %.1f 秒再截圖" % ws)
            time.sleep(ws)
            waited = ws
        else:
            waited = settle(ws)
            print("等畫面穩定：%.1f 秒（上限 %.1f）" % (waited, ws))
        other = (time.time() - t_loop0) - (t_shot + t_ai + t_verify + t_act + waited)   # 整輪實際時間減掉已量到的：狀態通知、清漣漪、接手檢查…
        print("[耗時] 截圖 %.1f · AI 看圖 %.1f · 座標複查 %.1f · 執行動作 %.1f · 等待 %.1f · 其他 %.1f（秒）" % (t_shot, t_ai, t_verify, t_act, waited, max(0.0, other)))
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
if Overlay:
    OV = Overlay()
    print("PhoneAI Overlay：", "已連線（漣漪 + 即時通知）" if OV.connect() else "沒有使用（App 沒裝/連不上，改用一般通知）")
    # 助理面板送出後停著等回答：不在這裡收——查電量這類只要回答的任務，光球要一直轉到答案出現；要截圖或操作畫面前才收（close_sheet）
try:
    main()
finally:
    SHEET["keep"] = False
    close_sheet()   # 還沒顯示回答卡片就結束（失敗、取消）：面板別一直停著
    try:
        release_drag()
    except Exception as e:
        print("放開手指出錯:", repr(e))
    # 先收掉使用者看得到的東西（Now Bar、光暈、預覽），再做慢的收尾：以前先總結經驗（呼叫 Haiku，步數 ≥ 4 才會做，約 3～4 秒）
    # 才關 Now Bar，答案早就給使用者了，Now Bar 和光暈還要再多留幾秒（Chrome 4～5 步的任務收尾 5～6 秒，蝦皮 3 步只要 2～3 秒）
    if OV and OV.ok:
        if BG["vd"]:
            try:
                OV.vd(None)   # 背景虛擬螢幕要消失了：預覽視窗先關
            except Exception:
                pass
        print("Overlay 診斷:", OV.check())
        try:  # 回報摘要給 App（主畫面顯示）：任務、步數、用量、搜尋、估價、結果
            ok = OV.report("%s · %d步 · %d入/%d出 · 搜尋%d次 · 約$%.4f · %s" % (
                TASK_TEXT[-1][:30], STATS.get("steps", 0), STATS["in"], STATS["out"],
                STATS.get("searches", 0), STATS.get("cost", 0), str(STATS.get("say", "（沒有回報結果）"))[:40]))
            print("回報給 App:", "成功" if ok else "失敗（App 沒回 ok；連線可能已斷）")
        except Exception as e:
            print("回報給 App 出錯:", repr(e))
        OV.end()
    try:
        _l = save_lesson(TASK_TEXT[-1] if TASK_TEXT else "", STATS.get("hist"), STATS.get("say"), (PRE_APP[0] if PRE_APP else None))
        if _l:
            print("已記下經驗：" + _l)
            subprocess.run(["termux-notification", "--id", "7195", "--title", "AI 學到的經驗（可在 App 記憶頁刪除）", "--content", _l],
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    except Exception as e:
        print("記下經驗出錯:", repr(e))
    try:
        bg_cleanup()
    except Exception as e:
        print("背景模式清理出錯:", repr(e))
    subprocess.run(["termux-wake-unlock"])
    subprocess.run(["termux-notification-remove", NOTIF_ID], stderr=subprocess.DEVNULL)
    if _touch_old is not None:
        su("settings put system show_touches " + _touch_old)
    for f in (SHOT, SMALL):
        try:
            os.remove(f)
        except OSError:
            pass
    if STATS.get("t0"):
        total = time.time() - STATS["t0"]
        ans = STATS.get("t_answer")
        print("總耗時: %.1f 秒（第一步預先規劃 %.1f 秒）%s" % (total, STATS.get("plan_secs", 0),
              "；答案給出 %.1f 秒、之後收尾 %.1f 秒" % (ans, total - ans) if ans else ""))
    if TREE_MODE:
        print("畫面來源：元件清單 %d 步、截圖 %d 步（元件清單模式實驗）" % (STATS.get("tree_steps", 0), STATS.get("shot_steps", 0)))
    cost = STATS.get("cost", 0)
    print("本次用量: %d 入（快取讀 %d / 寫 %d）/ %d 出，搜尋 %d 次，估計約 $%.4f（Sonnet 每百萬 $2/$10、Opus $4/$20、搜尋每千次 $10；快取讀 0.1 倍、寫 1.25 倍為估算）"
          % (STATS["in"], STATS.get("cr", 0), STATS.get("cw", 0), STATS["out"], STATS.get("searches", 0), cost))
