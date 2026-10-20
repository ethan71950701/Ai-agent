"""assistant.py — 打字版指令助手（規則式，沒有 AI 判斷）

判斷順序（寫死的規則，不是 AI）：
  1. 「開 / 打開 / 開啟 + App 名稱」且名稱在 APPS 裡 -> 用 monkey 啟動 App
  2. 指令表 COMMANDS 有命中（取最長關鍵字）-> sh: root 直接下指令 / path: 呼叫 path.py（本地模型點螢幕）
  3. 以「點 / 按 / 選」開頭 -> 呼叫 path.py 做視覺點擊（本地模型找目標並點）
  4. 其餘 -> 先問 Haiku 5.5（最低思考強度）是不是表裡某項的另一種說法（錯字/同音字/口語），
     是 -> 照表執行（只能選表裡有的）；不是 -> 丟給 agent2.py（Claude 自己看畫面、點擊/滑動/輸入，卡住自動升級 Opus）
     句子開頭加「ai」可跳過規則直接交給 Claude，例如「ai 幫我打開設定裡的Wi-Fi」
     （第 4 項 2026-10-17 新增，尚未在手機上實測）

用法：
  python ~/assistant.py                  互動模式
  python ~/assistant.py --once "開藍牙"   單次執行（桌面小工具用）

安全設計：只有表裡寫過的指令才會執行。不要加入付款、轉帳、刪除、傳訊息類指令。
注意：「已送出…」只代表指令沒報錯，不代表手機真的照做。
"""
import subprocess, os, re, sys, json, urllib.request, urllib.error

HOME = os.path.expanduser("~")

# (關鍵字清單, (類型, 內容), 完成後說的話)
COMMANDS = [
    (["開wifi", "打開wifi", "開啟wifi"], ("sh", "svc wifi enable"), "已送出開啟 Wi-Fi"),
    (["關wifi", "關閉wifi"], ("sh", "svc wifi disable"), "已送出關閉 Wi-Fi"),
    (["開藍牙", "打開藍牙", "開啟藍牙", "開藍芽", "打開藍芽", "開啟藍芽"], ("sh", "svc bluetooth enable"), "已送出開啟藍牙"),
    (["關藍牙", "關閉藍牙", "關藍芽", "關閉藍芽"], ("sh", "svc bluetooth disable"), "已送出關閉藍牙"),
    (["回桌面", "回主畫面"], ("sh", "input keyevent 3"), "已回到主畫面"),
    (["返回", "上一頁"], ("sh", "input keyevent 4"), "已按返回"),
    (["音量大", "大聲"], ("sh", "input keyevent 24"), "已調大音量"),
    (["音量小", "小聲"], ("sh", "input keyevent 25"), "已調小音量"),
    (["wifi頁面", "wifi設定"], ("path", "設定,連線,Wi-Fi"), ""),
]

# 說法 -> 套件名稱（用 `su -c "pm list packages" | grep -i 名稱` 查）
APPS = {
    "ig": "com.instagram.android",
    "instagram": "com.instagram.android",
    "spotify": "com.spotify.music",
    "line": "jp.naver.line.android",
    "chrome": "com.android.chrome",
}


def norm(t):
    return re.sub(r"[\s\-_，。,.]", "", t).lower()


def say(t):
    print("->", t)
    # -s MUSIC：走媒體音量；預設的通知音量在這支手機上沒聲音
    subprocess.run(["termux-tts-speak", "-s", "MUSIC", t], stderr=subprocess.DEVNULL)


def match(text):
    t = norm(text)
    best = None
    for kws, action, reply in COMMANDS:
        for k in kws:
            if k in t and (best is None or len(k) > best[0]):
                best = (len(k), action, reply)
    return best


ROUTER_MODEL = "claude-haiku-5-5"


def route(text, agent_only=False):
    """規則沒命中時，用 Haiku 5.5（最低思考強度）判斷這句話是不是指令表/App 清單裡某一項的另一種說法
    （錯字、同音字、口語）。它只能「選」表裡既有的項目，不能自創指令，所以安全白名單不變。
    同時估計交給 agent2 時需要幾步（steps，夾在 3~15，agent2 的最大步數就用它）。
    agent_only=True（句首 ai）：不選指令，只估步數。
    回傳 ("cmd", (長度, action, reply)) / ("app", 套件名) / ("agent", 步數) / None（失敗，用預設步數）。
    Key 讀不到、網路失敗、回答看不懂，一律回 None。"""
    try:
        key = open(f"{HOME}/.claude_key").read().strip()
    except OSError:
        return None
    cmds = "\n".join(f"{i}: {kws[0]}（{reply or kws[0]}）" for i, (kws, _, reply) in enumerate(COMMANDS))
    apps = "、".join(sorted(APPS))
    prompt = ("使用者對手機助手說：「" + text + "」\n"
        "請判斷這句話是不是下列某個固定指令或App的另一種說法（錯字、同音字、口語都算，例如「藍芽」=藍牙）。\n"
        "固定指令:\n" + cmds + "\nApp名稱: " + apps + "\n"
        "規則：開和關、大聲和小聲是相反的動作，絕對不能搞混；意思不完全一樣就不要硬選。"
        "只輸出一行JSON，不要解釋：\n"
        '{"route":"cmd","i":指令編號} 或 {"route":"app","name":"App名稱"} 或 {"route":"agent","steps":整數}\n'
        "不是上述任何一項，或需要看畫面、點擊、輸入文字、搜尋、多步驟操作，一律回 agent。"
        "回 agent 時 steps 是預估完成需要的畫面操作次數（每次點擊、滑動、輸入算一步；能直接用指令開App或開關Wi-Fi/藍牙的不算步數），再多留2步緩衝，範圍3到15。")
    if agent_only:
        prompt = ("使用者對手機助手說：「" + text + "」\n這個任務要由AI看畫面操作手機完成。"
            "預估需要幾步畫面操作（每次點擊、滑動、輸入算一步；能直接用指令開App或開關Wi-Fi/藍牙的不算），再多留2步緩衝，範圍3到15。"
            '只輸出一行JSON，不要解釋：{"route":"agent","steps":整數}')
    base = {"model": ROUTER_MODEL, "max_tokens": 400,
            "messages": [{"role": "user", "content": prompt}]}
    # 實測：只送 effort=low 時 Haiku 仍把 60 token 全拿去思考、沒輸出答案。所以先試「關前置思考+最低強度」，
    # API 回 400（不吃這組參數）就逐步退回；max_tokens 也放大到 400 當保險。哪組成功會印出來。
    for extra, label in (({"thinking": {"type": "between_tools"}, "output_config": {"effort": "low"}}, "between_tools+low"),
                         ({"output_config": {"effort": "low"}}, "effort=low"), ({}, "無參數")):
        req = urllib.request.Request("https://api.anthropic.com/v1/messages",
            data=json.dumps({**base, **extra}).encode(),
            headers={"x-api-key": key, "anthropic-version": "2023-06-01",
                     "content-type": "application/json"})
        try:
            r = json.loads(urllib.request.urlopen(req, timeout=20).read())
        except urllib.error.HTTPError as e:
            if e.code == 400 and extra:
                continue
            print("路由失敗:", e.code)
            return None
        except Exception as e:
            print("路由失敗:", e)
            return None
        out = "".join(b.get("text", "") for b in r["content"] if b.get("type") == "text")
        u = r.get("usage", {})
        print("路由(%s, %s入/%s出, 結束原因=%s, 區塊=%s): %s" % (label, u.get("input_tokens"), u.get("output_tokens"),
              r.get("stop_reason"), [b.get("type") for b in r["content"]], out.strip()))
        objs = re.findall(r"\{[^{}]*\}", out)
        try:
            d = json.loads(objs[-1])
        except Exception:
            return None
        if d.get("route") == "cmd":
            i = d.get("i")
            if isinstance(i, int) and 0 <= i < len(COMMANDS):
                return ("cmd", (0, COMMANDS[i][1], COMMANDS[i][2]))
        elif d.get("route") == "app" and d.get("name") in APPS:
            return ("app", APPS[d["name"]])
        elif d.get("route") == "agent":
            try:
                return ("agent", min(max(int(d.get("steps", 8)), 3), 15))
            except (TypeError, ValueError):
                return ("agent", 8)
        return None
    return None


def start_app(pkg):
    r = subprocess.run(
        ["su", "-c", f"monkey -p {pkg} -c android.intent.category.LAUNCHER 1"],
        capture_output=True, text=True)
    if "No activities found" in r.stdout + r.stderr:
        say("這個 App 沒有安裝")
    else:
        say("已送出開啟 App")


def launch_app(text):
    t = norm(text)
    for pre in ("打開", "開啟", "開"):
        if t.startswith(pre) and t[len(pre):] in APPS:
            return APPS[t[len(pre):]]
    return None


def run(text):
    pkg = launch_app(text)
    if pkg:
        start_app(pkg)
        return
    m = None if re.match(r"^\s*ai", text, re.I) else match(text)
    if not m:
        m2 = re.match(r"^\s*(點擊|點選|點|按|選擇|選)\s*(.+)$", text)
        if m2:
            target = m2.group(2).strip().replace("，", ",")
            subprocess.run(["python", f"{HOME}/path.py", target],
                           env=dict(os.environ, PATH_WAIT="1"))
            say("已嘗試點擊，請確認畫面")
            return
        # 規則沒命中：先讓 Haiku 判斷是不是指令表某項的另一種說法（句首 ai 跳過這步）
        forced = re.match(r"^\s*ai", text, re.I)
        r = route(text, agent_only=bool(forced))
        if r and r[0] == "app":
            print("理解成：開啟 App", r[1])
            start_app(r[1])
            return
        if r and r[0] == "cmd":
            m = r[1]
            print("理解成：", m[2] or m[1][1])
        else:
            # 都不是：交給 Claude 自己看畫面操作（會花 API 費用，每步約 NT$0.1~0.3）
            task = re.sub(r"^\s*ai[\s:：]*", "", text, flags=re.I)
            print("交給 Claude 操作（會用 API 額度）")
            env = dict(os.environ)  # AGENT_WAIT 預設 0：直接截圖（畫面若是 Termux，Claude 會自己用 open/home 離開）
            steps = r[1] if r and r[0] == "agent" else 8  # Haiku 估的步數；估不到用 8
            print("最多 %d 步" % steps)
            subprocess.run(["python", f"{HOME}/agent2.py", task, str(steps)], env=env)
            return
    _, (kind, content), reply = m
    if kind == "sh":
        r = subprocess.run(["su", "-c", content], capture_output=True, text=True)
        if r.returncode != 0:
            print("指令失敗:", r.stderr.strip())
            say("執行失敗")
            return
        say(reply)
    else:
        print("5 秒內切到起始畫面，它會自己操作")
        subprocess.run(["python", f"{HOME}/path.py", content])
        say("路徑已執行完，請確認畫面")


def main():
    if len(sys.argv) > 2 and sys.argv[1] == "--once":
        run(sys.argv[2])
        return
    print("輸入指令，輸入 list 看清單，輸入 q 離開")
    while True:
        try:
            text = input("助手> ").strip()
        except (EOFError, KeyboardInterrupt):
            break
        if text in ("q", "quit"):
            break
        if text == "list":
            for kws, (kind, c), _ in COMMANDS:
                print(f"  {kws[0]}  [{kind}]")
            continue
        if text:
            run(text)


main()
