#!/usr/bin/env python3
"""命令層（intent）：能用系統命令、網址、Android intent 直接做的事，就不要點畫面。
做法跟「用 API 呼叫」一樣：AI 只能從白名單裡選「名稱＋參數」，這裡把它組成 `am start …` 之類的指令；AI 自己不能寫任何指令（所以不會被騙去執行任意命令）。
所有動態的字串都經過 shlex.quote／URL 編碼；數字、電話、網址都先驗證格式。

build(name, args, display=None, has_pkg=None) → {"cmd": 要用 root 跑的指令, "desc": 給人看的說明, "ask": 要先問使用者的話或 None, "query": 是不是「查資料」（輸出要回報給 AI）, "wait": 做完建議等幾秒}
不認得的名稱、參數不合格式都丟 IntentError（呼叫端告訴 AI 改用畫面操作）。
"""
import re
import shlex
import subprocess
import urllib.parse


class IntentError(ValueError):
    pass


def _enc(v):
    return urllib.parse.quote(str(v), safe="")


def _text(args, key, maxlen=200, required=True):
    v = args.get(key)
    if v is None or v == "":
        if required:
            raise IntentError("缺少參數 %s" % key)
        return ""
    v = str(v).replace("\x00", "").strip()
    if len(v) > maxlen:
        raise IntentError("參數 %s 太長（上限 %d 字）" % (key, maxlen))
    return v


def _int(args, key, lo, hi, required=True, default=None):
    v = args.get(key, default)
    if v is None:
        if required:
            raise IntentError("缺少參數 %s" % key)
        return None
    try:
        n = int(float(v))
    except (TypeError, ValueError):
        raise IntentError("參數 %s 要是數字" % key)
    if not lo <= n <= hi:
        raise IntentError("參數 %s 要在 %d～%d 之間" % (key, lo, hi))
    return n


def _phone(args):
    v = _text(args, "number", 25)
    if not re.fullmatch(r"[+*#0-9() -]{3,25}", v):
        raise IntentError("電話號碼格式不對")
    return v.replace(" ", "")


def _am(display, action, data=None, pkg=None, extras=(), has_pkg=None):
    """組 am start 指令；display 有值（背景虛擬螢幕）就開在那個螢幕上；pkg 有裝才指定（沒裝就讓系統挑預設的）。"""
    parts = ["am", "start"]
    if display:
        parts += ["--display", str(int(display))]
    parts += ["-a", action]
    if data:
        parts += ["-d", data]
    if pkg and (has_pkg is None or has_pkg(pkg)):
        parts += ["-p", pkg]
    parts += list(extras)
    return " ".join(shlex.quote(p) for p in parts) + " 2>&1"


SETTINGS_PAGES = {
    "wifi": "android.settings.WIFI_SETTINGS", "bluetooth": "android.settings.BLUETOOTH_SETTINGS",
    "display": "android.settings.DISPLAY_SETTINGS", "sound": "android.settings.SOUND_SETTINGS",
    "battery": "android.settings.BATTERY_SAVER_SETTINGS", "apps": "android.settings.APPLICATION_SETTINGS",
    "location": "android.settings.LOCATION_SOURCE_SETTINGS", "accessibility": "android.settings.ACCESSIBILITY_SETTINGS",
    "storage": "android.settings.INTERNAL_STORAGE_SETTINGS", "date": "android.settings.DATE_SETTINGS",
    "language": "android.settings.LOCALE_SETTINGS", "airplane": "android.settings.AIRPLANE_MODE_SETTINGS",
    "nfc": "android.settings.NFC_SETTINGS", "vpn": "android.settings.VPN_SETTINGS", "wifi_ip": "android.settings.WIFI_IP_SETTINGS",
    "notification": "android.settings.NOTIFICATION_SETTINGS", "privacy": "android.settings.PRIVACY_SETTINGS",
    "security": "android.settings.SECURITY_SETTINGS", "settings": "android.settings.SETTINGS",
}

KEYS = {"play_pause": 85, "next": 87, "prev": 88, "stop": 86}
VOLUME = {"up": 24, "down": 25, "mute": 164}


def build(name, args, display=None, has_pkg=None):
    args = args if isinstance(args, dict) else {}
    name = str(name or "")

    def out(cmd, desc, ask=None, query=False, wait=3.0):
        return {"cmd": cmd, "desc": desc, "ask": ask, "query": query, "wait": wait}

    def view(url, pkg, desc):
        return out(_am(display, "android.intent.action.VIEW", url, pkg, has_pkg=has_pkg), desc)

    if name == "web_search":
        q = _text(args, "q")
        return view("https://www.google.com/search?q=" + _enc(q), "com.android.chrome", "用 Chrome 搜尋「%s」" % q)
    if name == "shopee_search":
        q = _text(args, "q")
        return view("https://shopee.tw/search?keyword=" + _enc(q), "com.shopee.tw", "在蝦皮搜尋「%s」" % q)
    if name == "youtube_search":
        q = _text(args, "q")
        return view("https://www.youtube.com/results?search_query=" + _enc(q), "com.google.android.youtube", "在 YouTube 搜尋「%s」" % q)
    if name == "maps_search":
        q = _text(args, "q")
        return view("geo:0,0?q=" + _enc(q), "com.google.android.apps.maps", "在地圖搜尋「%s」" % q)
    if name == "maps_navigate":
        q = _text(args, "q")
        return view("google.navigation:q=" + _enc(q), "com.google.android.apps.maps", "導航到「%s」" % q)
    if name == "open_url":
        u = _text(args, "url", 500)
        if not re.fullmatch(r"https?://[^\s\"'<>`\\]+", u):
            raise IntentError("只允許 http／https 網址")
        return out(_am(display, "android.intent.action.VIEW", u, None, has_pkg=has_pkg), "開啟網址 " + u[:60])
    if name == "settings_page":
        page = _text(args, "page", 30)
        if page not in SETTINGS_PAGES:
            raise IntentError("沒有這個設定頁：%s（可用：%s）" % (page, "、".join(sorted(SETTINGS_PAGES))))
        return out(_am(display, SETTINGS_PAGES[page], has_pkg=has_pkg), "開啟設定頁「%s」" % page, wait=2.0)
    if name == "set_alarm":
        h, m = _int(args, "hour", 0, 23), _int(args, "minute", 0, 59)
        label = _text(args, "label", 40, required=False)
        extras = ["--ei", "android.intent.extra.alarm.HOUR", str(h), "--ei", "android.intent.extra.alarm.MINUTES", str(m),
                  "--ez", "android.intent.extra.alarm.SKIP_UI", "true"]
        if label:
            extras += ["--es", "android.intent.extra.alarm.MESSAGE", label]
        return out(_am(display, "android.intent.action.SET_ALARM", extras=extras, has_pkg=has_pkg),
                   "設定鬧鐘 %02d:%02d%s" % (h, m, "（%s）" % label if label else ""), wait=1.5)
    if name == "set_timer":
        sec = _int(args, "seconds", 1, 86400)
        extras = ["--ei", "android.intent.extra.alarm.LENGTH", str(sec), "--ez", "android.intent.extra.alarm.SKIP_UI", "true"]
        return out(_am(display, "android.intent.action.SET_TIMER", extras=extras, has_pkg=has_pkg), "設定計時器 %d 秒" % sec, wait=1.5)
    if name == "dial":
        n = _phone(args)
        return out(_am(display, "android.intent.action.DIAL", "tel:" + n, has_pkg=has_pkg), "開啟撥號畫面（%s，還沒撥出）" % n, wait=2.0)
    if name == "call":
        n = _phone(args)
        return out(_am(display, "android.intent.action.CALL", "tel:" + n, has_pkg=has_pkg), "撥打電話 " + n,
                   ask="要直接撥打電話給 %s，確定嗎？" % n, wait=2.0)
    if name == "sms_compose":
        n = _phone(args)
        body = _text(args, "text", 300, required=False)
        extras = ["--es", "sms_body", body] if body else []
        return out(_am(display, "android.intent.action.SENDTO", "smsto:" + n, extras=extras, has_pkg=has_pkg),
                   "開啟簡訊編輯（%s，還沒送出）" % n, wait=2.0)
    if name == "media":
        k = _text(args, "action", 20)
        if k not in KEYS:
            raise IntentError("media 只能是 %s" % "、".join(KEYS))
        return out("input keyevent %d" % KEYS[k], "媒體控制 " + k, wait=0.5)
    if name == "volume":
        k = _text(args, "dir", 10)
        if k not in VOLUME:
            raise IntentError("volume 只能是 %s" % "、".join(VOLUME))
        return out("input keyevent %d" % VOLUME[k], "音量 " + k, wait=0.5)
    if name == "brightness":
        n = _int(args, "level", 0, 255)
        return out("settings put system screen_brightness_mode 0; settings put system screen_brightness %d" % n, "螢幕亮度設為 %d（0～255）" % n, wait=0.5)
    # ---- 查資料（輸出會回報給 AI，不用開畫面）----
    if name == "query":
        what = _text(args, "what", 20)
        qs = {"battery": ('dumpsys battery | grep -E "^ +(level|status|AC powered|USB powered|Wireless powered|temperature):"', "電池狀態"),
              "time": ('date "+%Y-%m-%d %H:%M:%S %A"', "目前時間"),
              "foreground_app": ('dumpsys window | grep -m1 mCurrentFocus', "目前最上層的 App"),
              "wifi": ("cmd wifi status | head -8", "Wi-Fi 狀態")}
        if what not in qs:
            raise IntentError("query 只能是 %s" % "、".join(qs))
        return out(qs[what][0], "查詢" + qs[what][1], query=True, wait=0.2)
    raise IntentError("沒有這個命令：%s（可用：%s）" % (name, "、".join(NAMES)))


NAMES = ["web_search", "shopee_search", "youtube_search", "maps_search", "maps_navigate", "open_url", "settings_page", "set_alarm",
         "set_timer", "dial", "call", "sms_compose", "media", "volume", "brightness", "query"]

# 給提示詞用：每個命令的名稱與參數
PROMPT = (
    '{"action":"intent","name":"命令名稱","args":{參數},"wait":秒數}，用系統命令直接把事情做完或直接跳到結果頁，比一步步點畫面快很多，也比較準；能用intent做的事一律優先用它，不要去點畫面。命令（args）：'
    'web_search{q}用Chrome搜尋、shopee_search{q}蝦皮搜尋、youtube_search{q}YouTube搜尋、maps_search{q}地圖搜尋、maps_navigate{q}導航、open_url{url}開網址、'
    'settings_page{page}直接開設定頁（page：%s）、set_alarm{hour,minute,label}設鬧鐘、set_timer{seconds}設計時器、dial{number}開撥號畫面、call{number}直接撥打（會先問使用者）、'
    'sms_compose{number,text}開簡訊編輯畫面（還沒送出）、media{action:play_pause|next|prev|stop}、volume{dir:up|down|mute}、brightness{level:0到255}、'
    'query{what:battery|time|foreground_app|wifi}查資料（結果我會寫在下一步的歷史裡，不用開畫面，查到就可以直接done回答）。'
    '用搜尋類命令跳到結果頁之後，下一步我會給你看畫面，確認結果再繼續（例如點第一個商品）；命令失敗或沒有對應的命令才用畫面操作\n'
) % "、".join(sorted(SETTINGS_PAGES))


# ---- 任務開始前的「第一步預先規劃」（agent2 用 Haiku 只看文字判斷，不用先開 App、截圖、問 Sonnet）----
PLAN_NAMES = ["web_search", "shopee_search", "youtube_search", "maps_search", "maps_navigate", "settings_page", "set_alarm", "set_timer",
              "dial", "media", "volume", "brightness", "query"]   # 不放 call／sms_compose／open_url：撥號簽訊與任意網址不讓小模型第一步就決定
PLAN_PKG = {"web_search": "com.android.chrome", "shopee_search": "com.shopee.tw", "youtube_search": "com.google.android.youtube",
            "maps_search": "com.google.android.apps.maps", "maps_navigate": "com.google.android.apps.maps"}
# 搜尋類命令會把 App 冷啟動，實測蝦皮 4 秒還在載入灰色骨架（多繞一步），所以等 6 秒
# 校準過「載入完的畫面豐富度」的命令（三星實測：蝦皮骨架畫面 55、載入完 266～359；YouTube 載入完 317～399）：
# 命令後不傻等固定秒數，改成「畫面豐富度 ≥ 門檻才繼續」。其他命令沒校準過，維持固定等待。
# web_search（Chrome／Google）第一步（Google 搜尋結果頁）：載入完 133／189／200、**載入中 86**（三星實測，2026-10-19）。
# 先前暫定 80 太低，86 的半成品被放行，AI 多 wait 一輪（答案 26 秒，固定等待版 13 秒）→ 改 110（夾在 86 與 133 之間，樣本少、邊界窄）。
PLAN_RICH = {"shopee_search": 150, "youtube_search": 150, "web_search": 110}
# 後續點擊（wait_changed）的門檻：搜尋結果頁以外的頁面（例如氣象署）載入完只有 108～126，所以 web_search 點擊後用較低的 80。
PLAN_RICH_TAP = {"web_search": 80}
# 命令後「最短先等多久才拍第一張」（預設 1.5 秒）。Chrome 冷啟動不到 0.5 秒，但 Google 結果頁要連網路：0.8 秒時還是半成品（86），
# 1.5～1.9 秒才載入完 → 最短等 1.0 秒再拍第一張。
PLAN_MINWAIT = {"web_search": 1.0}
PLAN_WAIT = {"web_search": 6.0, "shopee_search": 6.0, "youtube_search": 6.0, "maps_search": 6.0, "maps_navigate": 6.0}


def plan_prompt(task):
    return ("使用者要手機助手做這件事：「" + task + "」\n"
            "這個任務的「第一步」能不能直接用下面其中一個命令做到？（例如任務要搜尋某個關鍵字、開某個設定頁、設鬧鐘、查電量）\n"
            "命令：web_search{q}（Chrome搜尋）、shopee_search{q}（蝦皮搜尋）、youtube_search{q}（YouTube搜尋）、maps_search{q}（地圖搜尋）、maps_navigate{q}（導航）、"
            "settings_page{page}（page只能是：" + "、".join(sorted(SETTINGS_PAGES)) + "）、set_alarm{hour,minute,label}、set_timer{seconds}、dial{number}、"
            "media{action:play_pause|next|prev|stop}、volume{dir:up|down|mute}、brightness{level:0到255}、query{what:battery|time|foreground_app|wifi}。\n"
            "規則：只能用上面的命令；關鍵字q一定要是任務文字裡明確寫出的字，照原樣寫，不要編造或改寫；任務有指定在哪個App搜尋就用那個App的命令，沒指定才用web_search；"
            "不確定、或第一步需要看畫面才知道怎麼做，就回null。只輸出一行JSON，不要解釋："
            '{"name":"命令名稱","args":{參數}} 或 {"name":null}')


def _norm(t):
    return re.sub(r"[\s「」『』\"'“”‘’，。,.!！?？:：]", "", str(t)).lower()


def plan_check(obj, task, display=None, has_pkg=None):
    """檢查小模型回的第一步規劃：名稱在白名單、參數過 build 驗證、文字參數（q、label）一定要出現在任務文字裡（防止亂編）。
    通過回 {"name","args","desc","wait"}；否則回 None（呼叫端照原本流程）。"""
    if not isinstance(obj, dict) or obj.get("name") not in PLAN_NAMES or not isinstance(obj.get("args"), dict):
        return None
    name, args = obj["name"], obj["args"]
    t = _norm(task)
    for k in ("q", "label"):
        if args.get(k) not in (None, "") and _norm(args[k]) not in t:
            return None
    try:
        b = build(name, args, display=display, has_pkg=has_pkg)
    except IntentError:
        return None
    return {"name": name, "args": args, "desc": b["desc"], "wait": PLAN_WAIT.get(name, b["wait"]), "min_richness": PLAN_RICH.get(name), "tap_richness": PLAN_RICH_TAP.get(name, PLAN_RICH.get(name)),
            "min_wait": PLAN_MINWAIT.get(name, 1.5)}


# ---- A：規則快速路徑（任務寫得很明確時，不問 Haiku，程式自己組命令，省約 1.5 秒與一次 API 費用）----
_APPS = [("shopee_search", re.compile(r"蝦皮|shopee", re.I)), ("youtube_search", re.compile(r"youtube|油管", re.I)),
         ("web_search", re.compile(r"chrome|google|谷歌", re.I))]
_SEARCH = re.compile(r"(?:搜尋|搜索|search)\s*[:：]?\s*[「『\"“]([^」』\"”]{1,60})[」』\"”]", re.I)


def plan_by_rules(task, display=None, has_pkg=None):
    """任務同時有：恰好一個指名的 App（蝦皮／YouTube／Chrome／Google）＋「搜尋「關鍵字」」這種緊貼引號的寫法，就直接規劃成搜尋命令。
    不符合、有兩個以上的 App 或關鍵字、或關鍵字驗證不過，一律回 None（照原本問 Haiku）。回傳格式同 plan_check。"""
    task = str(task or "")
    qs = _SEARCH.findall(task)
    apps = [name for name, rx in _APPS if rx.search(task)]
    if len(qs) != 1 or len(apps) != 1:
        return None
    plan = plan_check({"name": apps[0], "args": {"q": qs[0].strip()}}, task, display=display, has_pkg=has_pkg)
    if plan:
        plan["source"] = "rule"
    return plan


# ---- 純查詢的快速回答：不用 AI、不用截圖，程式直接讀系統資訊回答（assistant.py 在送去 AI 之前先試）----
_BATT_RE = re.compile(r"(電量|电量|電池|电池)")
_BATT_Q = re.compile(r"(多少|幾|几|剩|還有|还有|看|查|現在|现在|怎麼樣|怎么样|如何|%|百分)")
_TIME_RE = re.compile(r"(現在|现在|目前)?(幾點|几点|時間|时间)(了|是)?|(現在|现在)(是)?(星期|禮拜|礼拜)?幾|今天(星期|禮拜|礼拜)幾|今天几号|今天幾號")


_YEAR_RE = re.compile(r"(今年|現在|现在|目前)(是)?(西元|公元)?(幾年|几年|哪一年|哪年)|(西元|公元)(幾年|几年)|(今年|現在|现在)(幾|几)年")


_ANDROID_RE = re.compile(r"(安卓|android|Android)(系統)?(版本|幾|几|多少)|(系統|系统)版本")


_STORE_RE = re.compile(r"(儲存|存儲|存储|容量|硬碟|硬盘|空間|空间)")
_MEM_RE = re.compile(r"(記憶體|内存|內存|ram)", re.I)
_MODEL_RE = re.compile(r"(型號|型号|機型|机型|什麼手機|什么手机|哪一支手機|哪支手机)")
_BRIGHT_RE = re.compile(r"亮度")
_WIFI_RE = re.compile(r"(wifi|wi-fi|無線網路|无线网络)", re.I)
_VOL_RE = re.compile(r"(音量|聲音大小|声音大小)")
_Q_RE = re.compile(r"(多少|幾|几|剩|還有|还有|夠|够|用了|用到|現在|现在|目前|是什麼|是什么|哪|連|连|狀態|状态|名稱|名称)")


def _gb(kb):
    return kb / 1024 / 1024


def _quick_system(t, runner):
    """儲存空間、記憶體、型號、亮度、Wi-Fi、音量。格式是對 Android 輸出的推測：解析不出來一律回 None（交給 AI 用唯讀 shell 查），不亂回答。"""
    if not _Q_RE.search(t):
        return None
    try:
        if _STORE_RE.search(t):
            line = [l for l in runner("df -k /data").splitlines() if l.strip()][-1].split()
            total, used, free = int(line[1]), int(line[2]), int(line[3])
            return "儲存空間：已用 %.1f GB／共 %.1f GB，剩 %.1f GB。" % (_gb(used), _gb(total), _gb(free))
        if _MEM_RE.search(t):
            mi = dict(re.findall(r"^(\w+):\s+(\d+)", runner("cat /proc/meminfo"), re.M))
            total, avail = int(mi["MemTotal"]), int(mi["MemAvailable"])
            return "記憶體：共 %.1f GB，目前可用 %.1f GB（已用約 %d%%）。" % (_gb(total), _gb(avail), round(100 * (total - avail) / total))
        if _MODEL_RE.search(t):
            brand = (runner("getprop ro.product.manufacturer") or "").strip()
            model = (runner("getprop ro.product.model") or "").strip()
            if model:
                return "這支手機是 %s %s。" % (brand, model) if brand else "這支手機是 %s。" % model
        if _BRIGHT_RE.search(t):
            v = int((runner("settings get system screen_brightness") or "").strip().split(".")[0])
            return "目前螢幕亮度約 %d%%（系統值 %d／255）。" % (round(100 * v / 255), v)
        if _WIFI_RE.search(t):
            out = runner("cmd wifi status") or ""
            if re.search(r"wifi is disabled", out, re.I):
                return "Wi-Fi 目前是關閉的。"
            m = re.search(r'SSID:\s*"?([^",\n]+)"?', out)
            if m and m.group(1).strip() not in ("<unknown ssid>", "unknown"):
                return "Wi-Fi 已開啟，連線中的網路是「%s」。" % m.group(1).strip()
            if re.search(r"wifi is enabled", out, re.I):
                return "Wi-Fi 已開啟（讀不到目前連的網路名稱）。"
        if _VOL_RE.search(t):
            m = re.search(r"volume is (\d+) in range \[(\d+)\.\.(\d+)\]", runner("cmd media_session volume --get --stream 3") or "")
            if m:
                cur, lo, hi = int(m.group(1)), int(m.group(2)), int(m.group(3))
                return "目前媒體音量 %d／%d（約 %d%%）。" % (cur, hi, round(100 * (cur - lo) / max(1, hi - lo)))
    except (ValueError, KeyError, IndexError, ZeroDivisionError):
        return None
    return None


def is_quick_question(text):
    """這句話是不是 quick_answer 能直接回答的那幾類純查詢（只看句型，不執行任何東西）。定時任務只允許這種：沒人看著時不操作畫面。"""
    t = re.sub(r"[\s，。,.？?！!]", "", text or "")
    if not t or len(t) > 18 or t.lower().startswith("ai"):
        return False
    if re.match(r"^(開|關|打開|關閉|开|关|打开|关闭|調|调|設|设)", t):
        return False
    if _BATT_RE.search(t) and _BATT_Q.search(t):
        return True
    if _ANDROID_RE.search(t) or _YEAR_RE.search(t) or _TIME_RE.search(t):
        return True
    if _Q_RE.search(t) and any(r.search(t) for r in (_STORE_RE, _MEM_RE, _MODEL_RE, _BRIGHT_RE, _WIFI_RE, _VOL_RE)):
        return True
    return False


def quick_answer(text, runner=None):
    """能直接讀系統資訊回答的短問題（電量、現在幾點）。回傳要顯示／唸的字串；不是這類問題回 None。
    runner(cmd) -> 輸出文字，預設用 su 執行（測試可注入）。句子太長（超過 18 字）視為別的任務，不攔截。"""
    t = re.sub(r"[\s，。,.？?！!]", "", text or "")
    if not t or len(t) > 18 or t.lower().startswith("ai"):
        return None
    if runner is None:
        def runner(cmd):
            return subprocess.run(["su", "-c", cmd], capture_output=True, text=True, timeout=10).stdout
    if _BATT_RE.search(t) and _BATT_Q.search(t):
        info = {}
        for ln in runner("dumpsys battery").splitlines():
            m = re.match(r"\s*([A-Za-z ]+):\s*(\S+)", ln)
            if m:
                info[m.group(1).strip()] = m.group(2)
        if "level" not in info:
            return None
        charging = info.get("status") in ("2", "5") or "true" in (info.get("AC powered"), info.get("USB powered"), info.get("Wireless powered"))
        s = "目前電量 %s%%" % info["level"]
        s += "，充電中" if charging else "，沒有在充電"
        try:
            s += "，電池溫度約 %.1f°C" % (int(info["temperature"]) / 10)
        except (KeyError, ValueError):
            pass
        return s + "。"
    if not re.match(r"^(開|關|打開|關閉|开|关|打开|关闭|調|调|設|设)", t):   # 「開wifi」「調亮度」是指令不是提問，交給指令表
        r = _quick_system(t, runner)
        if r:
            return r
    if _ANDROID_RE.search(t):
        ver = (runner("getprop ro.build.version.release") or "").strip()
        if not ver:
            return None
        return "這支手機的 Android 版本是 %s。" % ver
    if _YEAR_RE.search(t):
        import datetime
        y = datetime.date.today().year
        return "今年是西元 %d 年（民國 %d 年）。" % (y, y - 1911)
    if _TIME_RE.search(t):
        import datetime
        n = datetime.datetime.now()
        wd = "一二三四五六日"[n.weekday()]
        return "現在是 %d 月 %d 日（星期%s）%02d:%02d。" % (n.month, n.day, wd, n.hour, n.minute)
    return None


# ---- 唯讀 shell：AI 可以自己下「只讀不寫」的指令（不用等命令表列出來）----
# 規則：第一段的程式在 READ_BINS 裡，後面只能接 PIPE_OK 的過濾程式；不能有寫入、跳脫、子命令、萬用字元、多行；
# 讀得到的東西不能是通訊內容、帳號、密碼、相簿（DENY_RE）。通過才會以 root 執行，輸出截短後回給 AI。
READ_BINS = {"dumpsys", "getprop", "date", "settings", "cmd", "pm", "am", "ls", "cat", "df", "uptime", "free", "ps", "top",
             "ip", "uname", "id", "wc", "du", "stat", "getenforce", "hostname"}
PIPE_OK = {"grep", "head", "tail", "wc", "sort", "cut", "uniq", "tr"}
READ_SUB = {"settings": {"get"}, "cmd": {"wifi", "bluetooth", "connectivity", "display"}, "pm": {"list", "path"},
            "am": {"stack"}, "ip": {"addr", "route", "-br"}}
DUMP_OK = {"battery", "power", "wifi", "connectivity", "display", "audio", "window", "meminfo", "cpuinfo", "thermalservice",
           "telephony.registry", "package", "usagestats", "alarm", "sensorservice", "bluetooth_manager", "activity"}
DENY_RE = re.compile(r"(claude_key|gh_token|/data/data|/data/user|/data/system/users|sdcard|/storage|/mnt/|contacts|sms|"
                     r"clipboard|notification|account|password|shadow|passwd|\.ssh|\.gnupg|keystore|cookie|token|secret|"
                     r"/proc/self|/proc/[0-9]+/(environ|cmdline|fd|maps|mem))", re.I)
SHELL_BAD_RE = re.compile(r"[;&<>`$(){}\\\n\r]|\.\./|\*")


def check_read_only(cmd):
    """唯讀 shell 的白名單檢查。通過回 None；不通過回原因（字串）。只做字串判斷，不執行任何東西。"""
    c = (cmd or "").strip()
    if not c or len(c) > 200:
        return "指令是空的或太長"
    if SHELL_BAD_RE.search(c):
        return "含有不允許的符號（寫入、跳脫、子命令、萬用字元）"
    if DENY_RE.search(c):
        return "碰到隱私或帳號相關的路徑或內容，不讀"
    parts = [p.strip() for p in c.split("|")]
    first = parts[0].split()
    if not first or first[0] not in READ_BINS:
        return "這個程式不在唯讀清單：%s" % (first[0] if first else "")
    prog, args = first[0], first[1:]
    if prog == "dumpsys":
        if not args or args[0] not in DUMP_OK or len(args) > 3:
            return "dumpsys 只能查：%s" % "、".join(sorted(DUMP_OK))
    elif prog in READ_SUB:
        if not args or args[0] not in READ_SUB[prog]:
            return "%s 只能用：%s" % (prog, "、".join(sorted(READ_SUB[prog])))
        if prog == "cmd" and (len(args) != 2 or args[1] not in ("status", "get-wifi-state")):
            return "cmd 只能查狀態（例如 cmd wifi status）"
        if prog == "am" and args[:2] != ["stack", "list"]:
            return "am 只能用 stack list"
    elif prog == "cat":
        if not args or any(not (a.startswith("/proc/") or a.startswith("/sys/")) for a in args):
            return "cat 只能讀 /proc 或 /sys 底下的檔案"
    elif prog == "ls":
        if any(a.startswith("-") and set(a) - set("-lahdR1") for a in args):
            return "ls 參數不支援"
        if any(not a.startswith("-") and not a.startswith(("/system", "/proc", "/sys")) for a in args):
            return "ls 只能看 /system、/proc、/sys"
    elif prog == "top" and "-n" not in args:
        return "top 要加 -n 1（只抓一次）"
    for seg in parts[1:]:
        w = seg.split()
        if not w or w[0] not in PIPE_OK:
            return "管線後面只能接：%s" % "、".join(sorted(PIPE_OK))
    return None


def run_read_only(cmd, runner):
    """通過檢查才執行（runner(cmd) -> 輸出）。回 (輸出, 不通過的原因或 None)。輸出最多 1200 字。"""
    why = check_read_only(cmd)
    if why:
        return "", why
    out = (runner(cmd) or "").strip()
    if len(out) > 1200:
        out = out[:1200] + "…（後面省略）"
    return out, None


# ---- API 出錯時給使用者看得懂的原因（額度用完、Key 錯、限流、伺服器忙）----
LAST_API_ERROR = ""   # 最近一次 API 失敗的「HTTP 碼 + 內容」，各個呼叫 API 的地方出錯時記下來


def note_api_error(code, body=""):
    global LAST_API_ERROR
    LAST_API_ERROR = "HTTP %s %s" % (code, (body or "")[:300])


def api_problem(detail=None):
    """把 API 錯誤翻成一句人話；不是已知的情況回 None（呼叫端照原本流程）。"""
    d = (LAST_API_ERROR if detail is None else detail or "")
    low = d.lower()
    m = re.search(r"http (\d+)", low)
    code = int(m.group(1)) if m else 0
    if "credit balance" in low or "purchase credits" in low or "plans & billing" in low or "plans and billing" in low:
        return "API 額度用完了。請到 Anthropic Console 的 Billing 充值後再試。（快速回答、開關指令、定時查詢不需要 API，還能用）"
    if code == 401 or "invalid x-api-key" in low or "authentication_error" in low:
        return "API Key 無效或沒設定。請確認手機上的 ~/.claude_key（不要貼給任何人）。"
    if code == 429 or "rate_limit" in low:
        return "API 暫時被限流（太頻繁）。等一兩分鐘再試。"
    if code in (500, 502, 503, 529) or "overloaded" in low:
        return "Anthropic 的伺服器現在忙碌或出問題，稍後再試。"
    return None


# ---- 畫面元件清單（代替截圖給 AI 看；agent2 的 AGENT_TREE=1 用）----
def format_tree(items, w, h, limit=120):
    """App 無障礙讀到的元件（每個 {n,t,d,id,c,x,y}，x、y 是像素中心）→ 給 AI 的文字清單；元件太少（讀不到）回 None。
    每行：「編號 *文字 (x,y)」，* = 可點，x、y 換成 0~1000 的相對位置（跟 tap 動作用同一套座標）。螢幕轉向時（x 或 y 超出 w、h）自動對調。"""
    if not items or w <= 0 or h <= 0:
        return None
    try:
        if max(int(i.get("x", 0)) for i in items) > w or max(int(i.get("y", 0)) for i in items) > h:
            w, h = h, w
    except (TypeError, ValueError):
        return None
    lines = []
    for it in items:
        label = (it.get("t") or it.get("d") or (it.get("id") or "").rsplit("/", 1)[-1] or "").strip()
        if it.get("n") == 0 and label.startswith("（除錯）"):
            return None   # App 讀不到任何元件時回的除錯訊息
        if not label and not it.get("c"):
            continue
        label = " ".join(label.split())[:30]
        x, y = round(int(it.get("x", 0)) * 1000 / w), round(int(it.get("y", 0)) * 1000 / h)
        lines.append("%s%s%s (%d,%d)" % (it.get("n", len(lines)), "*" if it.get("c") else " ", label, x, y))
        if len(lines) >= limit:
            break
    return "\n".join(lines) if len(lines) >= 3 else None


def tree_pick(items, n, w, h):
    """AI 說「點第 n 號元件」：回傳它的中心（0~1000 相對座標，跟 tap 同一套）與文字；找不到回 None。
    items 是 format_tree 用的同一份清單（編號 n 對得上）；w、h 是那一步截圖（清單）的像素寬高。"""
    try:
        n = int(n)
    except (TypeError, ValueError):
        return None
    for it in items:
        if int(it.get("n", -1)) == n:
            label = (it.get("t") or it.get("d") or "").strip()
            return round(int(it["x"]) * 1000 / w), round(int(it["y"]) * 1000 / h), label
    return None
