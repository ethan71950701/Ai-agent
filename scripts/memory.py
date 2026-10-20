"""memory.py — 助理的「記憶」：待辦事項與筆記，全在手機本機，不呼叫 AI、不花錢。

assistant.py 在做任何事之前先問這裡：這句話是不是「記住 / 提醒我 / 待辦 / 有什麼待辦 / 完成第 N 項 / 刪掉…」？
是就在這裡處理完；不是就回 None，照原本流程走（開 App、指令表、交給 Claude）。

資料：~/memory.json（chmod 600；只有你和 root 讀得到；不進 repo）。格式：
  {"next_id": 3, "items": [{"id": 1, "kind": "todo"|"note", "text": "買牛奶", "created": "2026-10-18T09:30",
                            "done": false, "done_at": null, "deleted": false}, ...]}
不會做的事（先說清楚）：時間到了自動提醒（「下午三點開會」只會把這句話存起來）；把筆記餵給 AI。

用法：
    python ~/memory.py "記住我今天要買牛奶"     直接測試一句話
    python ~/memory.py list                     列出待辦
"""
import json, os, re, sys, time

PATH = os.path.expanduser("~/memory.json")

# 語音輸入常是簡體：只轉「判斷指令用」的幾個字（存起來的文字保持使用者原話）
_S2T = str.maketrans({"记": "記", "办": "辦", "项": "項", "该": "該", "么": "麼", "还": "還", "没": "沒", "时": "時",
                      "买": "買", "缴": "繳", "删": "刪", "个": "個", "给": "給", "后": "後", "开": "開", "会": "會",
                      "说": "說", "们": "們", "单": "單", "关": "關", "两": "兩"})

_NUM = {"零": 0, "〇": 0, "一": 1, "二": 2, "兩": 2, "两": 2, "三": 3, "四": 4, "五": 5, "六": 6, "七": 7, "八": 8, "九": 9}


def trad(t):
    return t.translate(_S2T)


def to_int(s):
    """阿拉伯數字或一到九十九的中文數字 → int；看不懂回 None。"""
    s = s.strip()
    if s.isdigit():
        return int(s)
    if not s or any(c not in _NUM and c != "十" for c in s):
        return None
    if "十" in s:
        a, _, b = s.partition("十")
        tens = _NUM.get(a, 1) if a else 1
        return tens * 10 + (_NUM.get(b, 0) if b else 0)
    return _NUM.get(s) if len(s) == 1 else None


# ---------- 判斷這句話是什麼 ----------

_ADD_TODO = re.compile(r"^(?:請|幫我|麻煩)?(?:你)?(?:提醒我|加入待辦|新增待辦|加待辦|待辦)[:：，,、\s]*(.+)$")
_ADD_REMEMBER = re.compile(r"^(?:請|幫我|麻煩)?(?:你)?(?:記住|記得|記下來|記下|記起來|記一下|幫我記|記)[:：，,、\s]*(.+)$")
_TODO_HINT = re.compile(r"(要|得|需要|必須|別忘|不要忘|去|買|繳|寄|打給|打電話|開會|準備|預約|訂|處理|完成|提醒|做)")
_LIST_WORDS = re.compile(r"^(事項|清單|列表|有哪些|有什麼|是什麼|有幾項|還有什麼)")  # 「待辦事項」「待辦清單」是在問清單，不是要新增
_LIST_TODO = re.compile(r"(待辦|代辦|還有什麼(事)?(沒做|要做)|有什麼事(要做|情要做)?$|要做什麼|要做的事|做什麼事|有哪些事)")
_LIST_NOTE = re.compile(r"(你|妳)?記得(什麼|哪些)|記了(什麼|哪些)|記住了(什麼|哪些)|我(叫|讓|請)你記(什麼|了什麼)|筆記(清單|列表)?$")
_DONE_NUM = re.compile(r"^(?:幫我)?(?:把)?(?:第)?([0-9零〇一二兩两三四五六七八九十]+)(?:項|個|件|條)?(?:待辦)?(?:已經)?(?:完成了?|做完了?|搞定了?|做好了?)$")
_DONE_NUM2 = re.compile(r"^(?:完成|做完|搞定|劃掉)了?(?:第)?([0-9零〇一二兩两三四五六七八九十]+)(?:項|個|件|條)?(?:待辦)?$")
_DONE_KEY = re.compile(r"^(?:幫我)?(?:把)?(.+?)(?:已經)?(?:做完了?|完成了?|搞定了?|做好了?)$")
_DONE_KEY2 = re.compile(r"^(?:完成|做完|搞定|劃掉)了?(.+)$")
_DEL_NUM = re.compile(r"^(?:幫我)?(?:刪除|刪掉|刪|移除|取消|忘掉)(?:待辦)?(?:第)?([0-9零〇一二兩两三四五六七八九十]+)(?:項|個|件|條)?$")
_DEL_KEY = re.compile(r"^(?:幫我)?(?:刪除|刪掉|刪|移除|取消|忘掉)(?:待辦)?(.+)$")


_CLEAR_VERB = re.compile(r"(刪除|刪掉|刪光|清空|清除|清掉|忘掉|忘記|全部刪)")
_ALL_WORD = re.compile(r"(全部|所有|通通|統統|一切|都|清空|刪光)")
_SCOPE = re.compile(r"(記憶|記錄|待辦|代辦|筆記|事項)")
_OTHER_DAY = re.compile(r"(明天|後天|昨天|前天|下週|下個?禮拜|下星期|週[一二三四五六日末]|禮拜[一二三四五六日天]|星期[一二三四五六日天]|\d+[號月]|[一二三四五六七八九十]+[號月])")


def parse(text):
    """回傳 ("add", kind, 內容) / ("list", kind) / ("done", 參考) / ("delete", 參考)，不是記憶指令回 None。
    參考 = ("num", N) 或 ("key", 關鍵字)。"""
    t = trad(text).strip()
    if not t or re.match(r"^\s*(打開|開啟|開|關|點|按)", t):  # 開 App、關 Wi-Fi 之類不是記憶指令（「開」前綴交給原本流程）
        return None
    if re.match(r"^\s*ai\b", t, re.I):  # 句首 ai = 明確要交給 Claude
        return None
    if len(t) <= 20 and _CLEAR_VERB.search(t) and _ALL_WORD.search(t) and not re.search(r"第[0-9零〇一二兩三四五六七八九十]", t):
        sc = _SCOPE.search(t)  # 「把記憶都刪除」「清空待辦」「刪除全部」；沒說範圍 = 全部
        if sc or re.match(r"^(?:請|幫我|麻煩|把|你)*(?:刪除|刪掉|清空|清除|清掉|刪光|忘掉|忘記)(?:全部|所有|通通|統統|一切)$", t) or t in ("清空", "全部刪除", "全部刪掉"):
            w = sc.group(1) if sc else ""
            return ("clear", "todo" if w in ("待辦", "代辦", "事項") else "note" if w == "筆記" else "all")
    if _LIST_NOTE.search(t):  # 先於「記得」的新增判斷，否則「你記得什麼」會被當成要記下「什麼」
        return ("list", "note")
    m = _ADD_TODO.match(t)
    if m:
        if _LIST_WORDS.match(m.group(1)):
            return ("list", "todo")
        return ("add", "todo", _strip_text(text, m))
    m = _ADD_REMEMBER.match(t)
    if m:
        content = _strip_text(text, m)
        trigger = t[:len(t) - len(m.group(1))]
        # 「記得…」本身就是提醒的語氣，一律當待辦；其他說法看內容有沒有「要/買/去…」這類要做的事
        return ("add", "todo" if ("記得" in trigger or _TODO_HINT.search(m.group(1))) else "note", content)
    for rx, kind in ((_DEL_NUM, "delete"), (_DONE_NUM, "done"), (_DONE_NUM2, "done")):
        m = rx.match(t)
        if m and to_int(m.group(1)) is not None:
            return (kind, ("num", to_int(m.group(1))))
    if _LIST_TODO.search(t) and not _OTHER_DAY.search(t):  # 問的是某一天（明天/週五…）→ 不是要整份清單，交給聊天從記憶裡找
        return ("list", "todo")
    for rx, kind in ((_DEL_KEY, "delete"), (_DONE_KEY, "done"), (_DONE_KEY2, "done")):
        m = rx.match(t)
        if m and 1 <= len(m.group(1).strip()) <= 30:
            return (kind, ("key", m.group(1).strip()))
    return None


def _strip_text(original, m):
    """內容要存使用者原話（不是轉成繁體的），所以用比對到的長度從原文尾端取。"""
    n = len(m.group(1))
    return original.strip()[-n:].strip()


# ---------- 儲存 ----------

def load(path=None):
    path = path or PATH
    try:
        d = json.load(open(path, encoding="utf-8"))
        if isinstance(d, dict) and isinstance(d.get("items"), list):
            d.setdefault("next_id", 1 + max([i.get("id", 0) for i in d["items"]] or [0]))
            return d
    except (OSError, ValueError):
        pass
    return {"next_id": 1, "items": []}


def save(d, path=None):
    path = path or PATH
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(d, f, ensure_ascii=False, indent=1)
    os.chmod(tmp, 0o600)
    os.replace(tmp, path)


def add(d, kind, text, now=None, pinned=False, app=None):
    """pinned = 永久記憶：沒有期限，AI 不能改或刪，只能使用者在 App 的記憶頁手動刪除。app = 經驗（kind=lesson）適用的 App 套件。"""
    item = {"id": d["next_id"], "kind": kind, "text": text, "created": now or time.strftime("%Y-%m-%dT%H:%M"),
            "done": False, "done_at": None, "deleted": False, "pinned": bool(pinned)}
    if app:
        item["app"] = app
    d["next_id"] += 1
    d["items"].append(item)
    return item


def live(d, kind):
    """還沒完成、沒刪除的項目（照建立順序）。"""
    return [i for i in d["items"] if i["kind"] == kind and not i["done"] and not i["deleted"]]


def find(d, kind, ref):
    """依參考找項目。回傳 (項目或 None, 說明)。數字是清單裡的第幾項；關鍵字要剛好比對到一筆。"""
    items = live(d, kind)
    typ, val = ref
    if typ == "num":
        if 1 <= val <= len(items):
            return items[val - 1], ""
        return None, "沒有第 %d 項（目前只有 %d 項）" % (val, len(items))
    hits = [i for i in items if val in i["text"]]
    if len(hits) == 1:
        return hits[0], ""
    if not hits:
        return None, "找不到包含「%s」的項目" % val
    return None, "有 %d 項都包含「%s」，請說第幾項" % (len(hits), val)


def _date_tag(item, today):
    c = item["created"][:10]
    if c == today:
        return "今天"
    return "%d/%d" % (int(c[5:7]), int(c[8:10]))


def render(d, kind, today=None):
    """回傳 (唸出來的短版, 完整清單)。"""
    today = today or time.strftime("%Y-%m-%d")
    items = live(d, kind)
    label = "待辦" if kind == "todo" else "筆記"
    if not items:
        msg = "目前沒有%s" % label
        return msg, msg
    lines = ["%d. %s（%s）" % (n, i["text"], _date_tag(i, today)) for n, i in enumerate(items, 1)]
    spoken = "你有 %d 項%s：" % (len(items), label) + "；".join("第%d項，%s" % (n, i["text"]) for n, i in enumerate(items[:5], 1))
    if len(items) > 5:
        spoken += "；還有 %d 項沒唸，完整清單在通知" % (len(items) - 5)
    return spoken, "\n".join(lines)


# ---------- 對外：處理一句話 ----------

def handle(text, path=None, today=None, now=None, confirm=None):
    """是記憶指令就處理並回傳 (唸出來的話, 完整內容)；不是回 None（交還給原本流程）。"""
    p = parse(text)
    if p is None:
        return None
    return execute(p, path, today, now, confirm=confirm)


def note_lines(path=None, limit=30):
    """給聊天用的筆記（最近的 limit 則）。"""
    return [i["text"][:100] for i in live(load(path), "note")[-limit:]]


def clear(d, scope, path=None, now=None, confirm=None):
    """刪除全部（待辦／筆記／兩者；「全部」也包含 AI 記下的經驗）。一定要使用者按「是」才做；沒有確認方式或沒回應 = 不刪。
    已完成的待辦也算（它們還在記憶頁裡）。永久記憶（pinned）不會被刪，只能在 App 的記憶頁手動刪。"""
    kinds = {"todo": ("todo",), "note": ("note",)}.get(scope, ("todo", "note", "lesson"))
    alive = [i for i in d["items"] if i["kind"] in kinds and not i["deleted"]]
    targets = [i for i in alive if not i.get("pinned")]
    kept = len(alive) - len(targets)
    label = {"todo": "待辦", "note": "筆記"}.get(scope, "記憶（待辦、筆記與經驗）")
    keep_note = "（另有 %d 項永久記憶沒有刪，要刪請到 App 的記憶頁）" % kept if kept else ""
    if not targets:
        return ("目前沒有%s可刪%s" % (label, keep_note),) * 2
    msg = "要刪除全部%s嗎？共 %d 項，刪除後在 App 裡也看不到。%s" % (label, len(targets), keep_note)
    if confirm is None or confirm(msg) is not True:
        return ("已取消，沒有刪除任何東西",) * 2
    for i in targets:
        i["deleted"] = True
    save(d, path)
    if scope == "all" and path is None:  # 全部記憶 = 連聊天的對話紀錄也清掉
        try:
            os.remove(os.path.expanduser("~/chat_history.json"))
        except OSError:
            pass
    return ("已刪除全部%s，共 %d 項%s" % (label, len(targets), keep_note),) * 2


def lessons(app=None, path=None, per_app=8, generic=4):
    """agent2 開始任務前要帶進提示的經驗：這個 App 的最新幾則 + 通用的最新幾則（不分 App）。"""
    items = [i for i in load(path)["items"] if i["kind"] == "lesson" and not i["deleted"]]
    mine = [i["text"] for i in items if app and i.get("app") == app][-per_app:]
    gen = [i["text"] for i in items if not i.get("app")][-generic:]
    return gen + mine


def add_lesson(text, app=None, path=None, now=None):
    """記下一則經驗（沒有期限，重複的不再存）。回傳 True = 新增。"""
    text = str(text or "").strip()[:200]
    if not text:
        return False
    d = load(path)
    if any(i["kind"] == "lesson" and not i["deleted"] and i["text"] == text and i.get("app") == app for i in d["items"]):
        return False
    add(d, "lesson", text, now, app=app)
    save(d, path)
    return True


def run_tool(name, args, path=None, confirm=None, now=None):
    """聊天模型用的記憶工具（Anthropic tool use）。回傳給模型看的結果字串；錯誤也用字串回，讓模型自己修正或告訴使用者。
    安全：只能動 ~/memory.json 裡的待辦／筆記；刪除是標記；一次刪超過 3 項或清空都要使用者按確認框。"""
    args = args if isinstance(args, dict) else {}
    d = load(path)
    by_id = {i["id"]: i for i in d["items"] if not i["deleted"]}
    if name == "memory_add":
        kind, text = args.get("kind"), str(args.get("text") or "").strip()[:300]
        if kind not in ("todo", "note") or not text:
            return "錯誤：kind 要是 todo 或 note，text 不能空"
        it = add(d, kind, text, now, pinned=args.get("permanent") is True)
        save(d, path)
        return "已新增 #%d（%s%s）：%s" % (it["id"], kind, "，永久" if it["pinned"] else "", text)
    if name == "memory_update":
        it = by_id.get(args.get("id"))
        if not it:
            return "錯誤：找不到 #%s" % args.get("id")
        if it.get("pinned"):
            return "錯誤：#%d %s" % (it["id"], "這是永久記憶，只能在 App 的「助理的記憶」頁手動刪除或修改")
        if isinstance(args.get("text"), str) and args["text"].strip():
            it["text"] = args["text"].strip()[:300]
        if isinstance(args.get("done"), bool) and it["kind"] == "todo":
            it["done"], it["done_at"] = args["done"], (now or time.strftime("%Y-%m-%dT%H:%M")) if args["done"] else None
        save(d, path)
        return "已更新 #%d：%s%s" % (it["id"], it["text"], "（已完成）" if it["done"] else "")
    if name == "memory_delete":
        ids = [x for x in (args.get("ids") or []) if isinstance(x, int)]
        hit = [by_id[x] for x in ids if x in by_id and not by_id[x].get("pinned")]
        locked = [x for x in ids if x in by_id and by_id[x].get("pinned")]
        if not hit:
            return ("錯誤：#%s %s" % (locked, "這是永久記憶，只能在 App 的「助理的記憶」頁手動刪除或修改")) if locked else "錯誤：找不到這些 id：%s" % ids
        if len(hit) > 3 and (confirm is None or confirm("要刪除這 %d 項嗎？\n" % len(hit) + "\n".join("・" + i["text"][:30] for i in hit[:8])) is not True):
            return "使用者沒有同意，沒有刪除"
        for i in hit:
            i["deleted"] = True
        save(d, path)
        return "已刪除 %d 項：%s%s" % (len(hit), "、".join(i["text"] for i in hit), ("（#%s 是永久記憶沒有刪）" % locked) if locked else "")
    if name == "memory_clear":
        scope = args.get("scope") if args.get("scope") in ("todo", "note") else "all"
        return clear(d, scope, path, now, confirm)[0]
    if name == "memory_list":
        rows = [i for i in d["items"] if not i["deleted"] and (args.get("include_done") or not i["done"])]
        return "\n".join("#%d %s%s %s（%s）" % (i["id"], i["kind"], "✓" if i["done"] else "", i["text"], i["created"][:10]) for i in rows) or "（空的）"
    return "錯誤：沒有這個工具 " + str(name)


def todo_lines(path=None, limit=20):
    """給 Haiku 參考的目前待辦（「我買完牛奶了」才對得到哪一項）。"""
    return [i["text"][:60] for i in live(load(path), "todo")[:limit]]


def execute(p, path=None, today=None, now=None, strict=False, confirm=None):
    d = load(path)
    action = p[0]
    if action == "clear":
        return clear(d, p[1], path, now, confirm)
    if action == "add":
        _, kind, content = p
        if not content:
            return ("要記什麼？", "要記什麼？")
        add(d, kind, content, now)
        save(d, path)
        n = len(live(d, kind))
        msg = ("已加入待辦：%s（目前 %d 項）" if kind == "todo" else "已記下：%s（目前 %d 則筆記）") % (content, n)
        return (msg, msg)
    if action == "list":
        return render(d, p[1], today)
    ref = p[1]
    # 刪除用關鍵字時，待辦沒有就再找筆記（「忘掉車牌」）；完成、用編號都只看待辦
    kinds = ("todo", "note") if (action == "delete" and ref[0] == "key") else ("todo",)
    # 完成/刪除要避免誤攔截一般指令（例如「完成這個表單」「刪除這張照片」）：沒有待辦、或關鍵字對不到，就交還給原本流程
    if not any(live(d, k) for k in kinds):
        return ("目前沒有待辦", "目前沒有待辦") if strict else None
    item, why = None, ""
    for kind in kinds:
        if not live(d, kind):
            continue
        item, why = find(d, kind, ref)
        if item or why.startswith("有 "):  # 找到了，或不只一筆（要請使用者說清楚）
            break
    if not item:
        if not strict and ref[0] == "key" and not why.startswith("有 "):
            return None
        return (why, why)
    if item.get("pinned"):
        return ("「%s」%s" % (item["text"][:20], "這是永久記憶，只能在 App 的「助理的記憶」頁手動刪除或修改"),) * 2
    if action == "done":
        item["done"], item["done_at"] = True, now or time.strftime("%Y-%m-%dT%H:%M")
        save(d, path)
        left = len(live(d, kind))
        msg = "已完成：%s（還剩 %d 項）" % (item["text"], left)
        return (msg, msg)
    item["deleted"] = True  # 刪除 = 標記為刪除（檔案裡還保留，不再顯示）
    save(d, path)
    msg = "已刪除：%s" % item["text"]
    return (msg, msg)


if __name__ == "__main__":
    arg = " ".join(sys.argv[1:]).strip()
    if arg in ("", "list"):
        arg = "我有什麼待辦"
    r = handle(arg)
    print(r[1] if r else "（不是記憶指令）")
