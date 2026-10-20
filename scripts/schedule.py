#!/usr/bin/env python3
"""定時任務（只支援「純查詢」）：每天固定時間查一個資訊（電量、儲存空間、現在幾點…），用通知告訴你。
為什麼只有純查詢：定時任務沒人在旁邊看著，不能讓 AI 自己操作畫面（點按、輸入、付款都不行）。
說法：「每天早上 8 點告訴我電量」「每天晚上 10 點半查儲存空間」「我的定時」「取消定時 1」「取消全部定時」。
實作：~/schedules.json 存清單；Termux:API 的 termux-job-scheduler 每 15 分鐘叫一次 schedule_tick.sh → `schedule.py --run-due`。
所以時間誤差最多約 15 分鐘（Android 排程的最小間隔）；手機關機或沒網路錯過的，3 小時內補執行、超過就當天略過。
"""
import json, os, re, subprocess, sys, time, datetime

HOME = os.path.expanduser("~")
STORE = os.path.join(HOME, "schedules.json")
TICK = os.path.join(HOME, "schedule_tick.sh")
JOB_ID = "7198"
MAX_LATE_S = 3 * 3600
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

_PERIOD = {"早上": 0, "早晨": 0, "上午": 0, "中午": 12, "下午": 12, "傍晚": 12, "晚上": 12, "半夜": 0, "凌晨": 0}
_NUM = r"([0-9]{1,2}|[零〇一二兩两三四五六七八九十]{1,3})"
_PAT = re.compile(r"^每天\s*(早上|早晨|上午|中午|下午|傍晚|晚上|半夜|凌晨)?\s*" + _NUM + r"\s*(?:點|点|時|时|:|：)\s*(半|" + _NUM + r")?\s*分?\s*[，,、。\s]*(?:幫我|帮我|請|请|要|提醒我)?\s*(.+)$")


def _int(s):
    try:
        import memory
        return memory.to_int(s)
    except Exception:
        return int(s) if s.isdigit() else None


def parse_schedule(text):
    """「每天晚上 10 點半查儲存空間」→ (22, 30, "查儲存空間")；不是這個句型回 None。"""
    t = (text or "").strip()
    m = _PAT.match(t)
    if not m:
        return None
    period, hs, ms, task = m.group(1), m.group(2), m.group(3), m.group(m.lastindex)
    h = _int(hs)
    if h is None:
        return None
    mi = 30 if ms == "半" else (_int(ms) if ms else 0)
    if mi is None:
        return None
    if period in ("下午", "傍晚", "晚上") and h < 12:
        h += 12
    if period == "中午" and h < 11:
        h += 12
    if period in ("半夜", "凌晨") and h == 12:
        h = 0
    if not (0 <= h <= 23 and 0 <= mi <= 59):
        return None
    task = task.strip(" ，,。")
    return (h, mi, task) if task else None


def load(path=None):
    try:
        with open(path or STORE, encoding="utf-8") as f:
            d = json.load(f)
        return d if isinstance(d, list) else []
    except (OSError, ValueError):
        return []


def save(items, path=None):
    p = path or STORE
    tmp = p + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(items, f, ensure_ascii=False, indent=1)
    os.replace(tmp, p)


def add(items, h, mi, task):
    nid = max([i.get("id", 0) for i in items] + [0]) + 1
    items.append({"id": nid, "h": h, "m": mi, "task": task, "last": ""})
    return nid


def due(items, now):
    """現在該跑的項目：今天的時間到了、今天還沒跑過、而且遲到不超過 3 小時。"""
    out = []
    today = now.strftime("%Y-%m-%d")
    for it in items:
        at = now.replace(hour=it["h"], minute=it["m"], second=0, microsecond=0)
        late = (now - at).total_seconds()
        if it.get("last") != today and 0 <= late <= MAX_LATE_S:
            out.append(it)
    return out


def ensure_job():
    """註冊（或重新註冊）每 15 分鐘一次的排程；已經註冊的再註冊一次只是覆蓋，不會變兩個。失敗（沒裝 Termux:API）回 False。"""
    try:
        r = subprocess.run(["termux-job-scheduler", "--job-id", JOB_ID, "--script", TICK, "--period-ms", "900000",
                            "--persisted", "true"], capture_output=True, text=True, timeout=15)
        return r.returncode == 0
    except Exception:
        return False


def _fmt(it):
    return "%d. 每天 %02d:%02d %s" % (it["id"], it["h"], it["m"], it["task"])


def handle(text, confirm):
    """面板／終端機的句子：建立、列出、取消定時。不是定時相關回 None；否則回要顯示的文字。confirm(msg) → bool。"""
    t = re.sub(r"\s+", "", text or "")
    if re.match(r"^(我的定時|有哪些定時|定時清單|定時列表|看定時)", t):
        items = load()
        if not items:
            return "目前沒有定時任務。"
        return "目前定時：\n" + "\n".join(_fmt(i) for i in items)
    m = re.match(r"^(取消|刪除|删除)(全部|所有)定時", t)
    if m:
        save([])
        return "已取消全部定時任務。"
    m = re.match(r"^(取消|刪除|删除)定時(?:第)?([0-9一二三四五六七八九十]+)", t)
    if m:
        n = _int(m.group(2))
        items = load()
        keep = [i for i in items if i.get("id") != n]
        if len(keep) == len(items):
            return "找不到定時 %s。" % m.group(2)
        save(keep)
        return "已取消定時 %s。" % m.group(2)
    p = parse_schedule(text)
    if not p:
        return None
    h, mi, task = p
    import intents
    if not intents.is_quick_question(task):
        return "定時任務目前只支援純查詢（電量、儲存空間、記憶體、現在幾點、Wi-Fi、亮度…）。沒人看著的時候我不會自己操作螢幕。"
    if not confirm("每天 %02d:%02d 自動「%s」並用通知告訴你（時間誤差最多約 15 分鐘）。要建立嗎？" % (h, mi, task)):
        return "已取消，沒有建立定時。"
    items = load()
    nid = add(items, h, mi, task)
    save(items)
    ok = ensure_job()
    return "已建立定時 %d：每天 %02d:%02d %s。%s" % (nid, h, mi, task, "" if ok else "（排程註冊失敗：請確認有安裝 Termux:API，再試一次）")


def run_due(now=None, runner=None):
    """排程每 15 分鐘叫一次：把該跑的查詢跑掉，用通知回報。回傳跑了幾個。"""
    import intents
    now = now or datetime.datetime.now()
    items = load()
    n = 0
    for it in due(items, now):
        ans = intents.quick_answer(it["task"], runner)
        it["last"] = now.strftime("%Y-%m-%d")
        if ans:
            subprocess.run(["termux-notification", "--id", "7197", "--title", "定時：" + it["task"], "--content", ans],
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            print(time.strftime("%m/%d %H:%M"), "定時", it["id"], "→", ans)
            n += 1
    save(items)
    return n


if __name__ == "__main__":
    if "--run-due" in sys.argv:
        run_due()
    elif "--register" in sys.argv:
        print("排程註冊", "成功" if ensure_job() else "失敗")
    else:
        for i in load():
            print(_fmt(i))
