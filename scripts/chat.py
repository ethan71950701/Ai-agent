"""chat.py — 一般聊天＋記憶（Haiku 5.5，會用工具）：問知識、閒聊、建議，也能自己查／記／改／刪待辦與筆記。不操作手機。

assistant.py 的 Haiku 路由判斷「不用操作手機、也不是固定指令」時呼叫這裡。
做法（tool use）：不是我們寫死「哪句話對應哪個動作」，而是把記憶工具（memory_add / memory_update / memory_delete /
memory_clear / memory_list）交給模型，由它自己判斷要不要呼叫、用什麼參數；結果回給它，它再用自然語言回答。
安全邊界（寫在 memory.run_tool，不靠模型自律）：只能動 ~/memory.json 的待辦與筆記；刪除是標記；一次刪超過 3 項或清空要跳確認框。
模型**不能**執行任意程式碼。
記憶（都在手機本機，只在聊天時隨問題送給 API）：最近的對話（~/chat_history.json，最多 20 則、24 小時、chmod 600）、
待辦與筆記（連同編號放進提示）。
限制：沒有網路搜尋，不知道今天的新聞、天氣、股價（這類路由會交給 agent2 去查）。
用法: python ~/chat.py "你好"
"""
import json, os, re, sys, time, urllib.error, urllib.request

HOME = os.path.expanduser("~")
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
HIST = HOME + "/chat_history.json"
MODEL = "claude-haiku-5-5"
MAX_MSGS, MAX_AGE, MAX_TURNS = 20, 24 * 3600, 6

SYSTEM = ("你是使用者手機上的語音助理，像 Gemini 一樣自然地聊天回答。規則：\n"
          "0. 使用者的話常是語音輸入轉成的文字，可能有同音或近音的錯字（例如「開籃芽」是「開藍牙」、「西元」寫成「西園」）；請依上下文理解他真正的意思，不要照字面回答，也不要糾正他的字。\n"
          "1. 一律用繁體中文，口語、簡短（一般 1~3 句、100 字內；使用者要求詳細才長），不要用 Markdown 符號、不要條列符號，因為回答會被唸出來。\n"
          "2. 你不能操作手機，也沒有網路，不知道今天的新聞、天氣、股價；被問到就老實說不知道，不要編造。\n"
          "3. 你有記憶工具（待辦 todo 與筆記 note）。使用者要你記住事情、改、刪、標記完成，就呼叫工具去做，做完用一句話說你做了什麼。"
          "下面列出目前的記憶與編號（#編號）；用編號操作，不要猜。\n"
          "4. 使用者問到記憶裡的某一點，只回答那一點，不要把整份清單念出來；除非他要整份清單。\n"
          "5. 使用者說出關於他自己、值得長期記住的資訊（名字、家人、喜好、習慣、過敏…）時，主動用 memory_add 存成 note，並在回答裡說「已記住…」。"
          "臨時的事（明天要考試）是 todo 或不用記，不要存成 note。\n"
          "6. 標了📌的是永久記憶，你不能改也不能刪（工具會拒絕），使用者要刪就告訴他到 App 的「助理的記憶」頁手動刪。\n"
          "7. 要刪很多項或清空時照樣呼叫工具，確認框由程式負責跳出來；使用者拒絕就照實說沒有刪除。")

TOOLS = [
    {"name": "memory_add", "description": "新增一則待辦（要做的事）或筆記（資訊、事實）。",
     "input_schema": {"type": "object", "properties": {"kind": {"type": "string", "enum": ["todo", "note"]},
                                                       "text": {"type": "string", "description": "內容，用使用者的原話或簡短整理"},
                                                       "permanent": {"type": "boolean", "description": "永久記憶：使用者說「永遠記住／一直記著／不要忘」才設 true；永久記憶你之後不能改或刪，只有使用者在 App 裡手動刪"}},
                      "required": ["kind", "text"]}},
    {"name": "memory_update", "description": "修改某一項的文字，或把待辦標成完成／改回未完成。",
     "input_schema": {"type": "object", "properties": {"id": {"type": "integer"}, "text": {"type": "string"}, "done": {"type": "boolean"}},
                      "required": ["id"]}},
    {"name": "memory_delete", "description": "刪除指定編號的項目（一次超過 3 項會跳確認框）。",
     "input_schema": {"type": "object", "properties": {"ids": {"type": "array", "items": {"type": "integer"}}}, "required": ["ids"]}},
    {"name": "memory_clear", "description": "刪除全部待辦／全部筆記／全部（一定會跳確認框，使用者按否就不刪）。",
     "input_schema": {"type": "object", "properties": {"scope": {"type": "string", "enum": ["todo", "note", "all"]}}, "required": ["scope"]}},
    {"name": "memory_list", "description": "列出記憶（含編號）。提示裡已有未完成的項目；要看已完成的才需要呼叫。",
     "input_schema": {"type": "object", "properties": {"include_done": {"type": "boolean"}}}},
]


def load_history(path=None, now=None):
    now = now or time.time()
    try:
        h = json.load(open(path or HIST, encoding="utf-8"))
        return [m for m in h if isinstance(m, dict) and m.get("role") in ("user", "assistant")
                and isinstance(m.get("content"), str) and now - m.get("t", 0) < MAX_AGE][-MAX_MSGS:]
    except (OSError, ValueError):
        return []


def save_history(h, path=None):
    path = path or HIST
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(h[-MAX_MSGS:], f, ensure_ascii=False)
    os.chmod(tmp, 0o600)
    os.replace(tmp, path)


def build_system(items, today):
    """items = memory.live 的項目（含 id）。"""
    def fmt(kind):
        rows = ["#%d %s%s" % (i["id"], "📌" if i.get("pinned") else "", i["text"][:100]) for i in items if i["kind"] == kind]
        return "；".join(rows) if rows else "（沒有）"
    return "\n".join([SYSTEM, "今天是 " + today + "。", "待辦（未完成）：" + fmt("todo"), "筆記：" + fmt("note")])


def call(messages, system, key):
    """Haiku 5.5 + 工具，先試「最低思考強度」，API 回 400 就退回。回傳整個回應 dict；失敗回 None。"""
    base = {"model": MODEL, "max_tokens": 1000, "system": system, "messages": messages, "tools": TOOLS}
    for extra in ({"thinking": {"type": "between_tools"}, "output_config": {"effort": "low"}},
                  {"output_config": {"effort": "low"}}, {}):
        req = urllib.request.Request("https://api.anthropic.com/v1/messages", data=json.dumps({**base, **extra}).encode(),
                                     headers={"x-api-key": key, "anthropic-version": "2023-06-01", "content-type": "application/json"})
        try:
            return json.loads(urllib.request.urlopen(req, timeout=40).read())
        except urllib.error.HTTPError as e:
            body = e.read().decode(errors="ignore")
            try:
                import intents
                intents.note_api_error(e.code, body)
            except Exception:
                pass
            if e.code == 400 and extra and "credit balance" not in body.lower():   # 400 可能只是不吃這組參數，退回再試；額度用完就別再試了
                continue
            print("聊天失敗:", e.code)
            return None
        except Exception as e:
            print("聊天失敗:", e)
            return None
    return None


# 彩蛋：問「能不能用中文跟你對話」就回一句玩笑話（不呼叫 API、不花錢）。只比對明確的問法，一般聊天不受影響。
EGG_CN = "可以。"
EGG_GAG = "你的帳號已被封禁"
PENDING_GAG = []  # 彩蛋的第二張卡：assistant 顯示完「可以」之後，過 0.5 秒再跳出來


def easter_egg(text):
    """彩蛋（不呼叫 API、不花錢）：問能不能用中文 → 回「可以。」，同時排一張玩笑封禁卡。回傳回答或 None。"""
    t = re.sub(r"[\s，。！!？?]", "", text or "")
    if not t or len(t) > 24:
        return None
    if re.search(r"(可以|能|能不能|可不可以).*(中文|國語|繁體|華語).*(對話|聊天|溝通|說話|講話)|(用|說|講)?(中文|國語|繁體|華語)(跟|和|與)(你|妳|您|助理|ai|AI)(對話|聊天|溝通|說話|講話)", t, re.I):
        return EGG_CN
    return None


def egg_reply(text, hist_path=None):
    """彩蛋：命中就回傳回答（寫進對話紀錄、排好第二張卡）；沒命中回 None。不呼叫 API。assistant 在路由之前就會先呼叫它。"""
    egg = easter_egg(text)
    if not egg:
        return None
    prev = load_history(hist_path)
    now = time.time()
    save_history(prev + [{"role": "user", "content": text, "t": now}, {"role": "assistant", "content": egg, "t": now}], hist_path)
    PENDING_GAG[:] = [EGG_GAG]
    return egg


def ask(text, hist_path=None, mem_path=None, caller=call, confirm=None):
    """回傳回答文字；連不上回 None。模型呼叫工具時在這裡執行（最多 MAX_TURNS 輪），成功才更新對話紀錄。"""
    egg = egg_reply(text, hist_path)
    if egg:
        return egg
    try:
        key = open(HOME + "/.claude_key").read().strip()
    except OSError:
        return None
    import memory
    items = memory.live(memory.load(mem_path), "todo") + memory.live(memory.load(mem_path), "note")
    system = build_system(items, time.strftime("%Y-%m-%d（週%w）").replace("週0", "週日"))
    hist = load_history(hist_path)
    msgs = [{"role": m["role"], "content": m["content"]} for m in hist] + [{"role": "user", "content": text}]
    reply = None
    for _ in range(MAX_TURNS):
        r = caller(msgs, system, key)
        if not r:
            return None
        content = r.get("content", [])
        texts = "".join(b.get("text", "") for b in content if b.get("type") == "text").strip()
        uses = [b for b in content if b.get("type") == "tool_use"]
        if not uses:
            reply = texts
            break
        msgs.append({"role": "assistant", "content": content})
        results = []
        for u in uses:
            out = memory.run_tool(u.get("name"), u.get("input"), mem_path, confirm)
            print("記憶工具 %s %s -> %s" % (u.get("name"), json.dumps(u.get("input"), ensure_ascii=False), out))
            results.append({"type": "tool_result", "tool_use_id": u.get("id"), "content": out})
        msgs.append({"role": "user", "content": results})
        reply = texts or None  # 工具跑完模型沒再說話時，用這輪的文字（可能是空的）
    if not reply:
        return None
    now = time.time()
    hist += [{"role": "user", "content": text, "t": now}, {"role": "assistant", "content": reply, "t": now}]
    save_history(hist, hist_path)
    return reply


if __name__ == "__main__":
    print(ask(" ".join(sys.argv[1:]) or "你好") or "（沒有回應）")
