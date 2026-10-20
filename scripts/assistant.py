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
import subprocess, os, re, sys, json, time, urllib.request, urllib.error
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
try:
    import applist  # ~/apps.tsv：App 顯示名稱 -> 套件名稱（由 PhoneAI Overlay App 匯出）
except ImportError:
    applist = None

HOME = os.path.expanduser("~")
# 本地模型（path.py）預設關閉：它讓手機很燙、而且慢（約 20 秒一步）。要用就設環境變數 ASSIST_LOCAL=1。
USE_LOCAL = os.environ.get("ASSIST_LOCAL") == "1"

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
    # 以下指令的 shell 寫法是依 Android 一般做法寫的，還沒在這支手機上實測：失敗時會說「執行失敗」，請把畫面貼回來
    (["開勿擾", "打開勿擾", "開啟勿擾"], ("sh", "cmd notification set_dnd priority"), "已開啟勿擾模式"),
    (["關勿擾", "關閉勿擾"], ("sh", "cmd notification set_dnd off"), "已關閉勿擾模式"),
    (["開自動旋轉", "打開自動旋轉", "開啟自動旋轉"], ("sh", "settings put system accelerometer_rotation 1"), "已開啟自動旋轉"),
    (["關自動旋轉", "關閉自動旋轉", "鎖定直向"], ("sh", "settings put system accelerometer_rotation 0"), "已關閉自動旋轉"),
    (["開深色模式", "打開深色模式", "開啟深色模式"], ("sh", "cmd uimode night yes"), "已開啟深色模式"),
    (["關深色模式", "關閉深色模式", "開淺色模式"], ("sh", "cmd uimode night no"), "已關閉深色模式"),
    (["開省電", "打開省電", "開啟省電"], ("sh", "settings put global low_power 1"), "已開啟省電模式"),
    (["關省電", "關閉省電"], ("sh", "settings put global low_power 0"), "已關閉省電模式"),
    # 飛航模式會斷掉網路（連 AI 都連不上），所以先跳確認框（kind = "shc"）
    (["開飛航", "打開飛航", "開啟飛航"], ("shc", "cmd connectivity airplane-mode enable"), "已開啟飛航模式"),
    (["關飛航", "關閉飛航"], ("shc", "cmd connectivity airplane-mode disable"), "已關閉飛航模式"),
    # 「進入 Wi-Fi 頁面」不放在這裡：原本這條走本地模型（path.py，慢、要等 5 秒），說法不同時 Haiku 有時選它有時不選，結果不固定。
    # 現在一律交給 agent2.py（Claude，約 2 秒一步）。想改回本地路徑點擊可加回：(["wifi頁面", "wifi設定"], ("path", "設定,連線,Wi-Fi"), ""),
]

# 說法 -> 套件名稱（用 `su -c "pm list packages" | grep -i 名稱` 查）
APPS = {
    "ig": "com.instagram.android",
    "instagram": "com.instagram.android",
    "spotify": "com.spotify.music",
    "line": "jp.naver.line.android",
    "chrome": "com.android.chrome",
}


# 手寫的 APPS 優先；其餘用 App 匯出的整份清單（沒有就只用手寫的）
ALL_APPS = dict(applist.load() if applist else {})
ALL_APPS.update({re.sub(r"[\s\-_，。,.·]", "", k).lower(): v for k, v in APPS.items()})


def norm(t):
    return re.sub(r"[\s\-_，。,.]", "", t).lower()


def say(t, full=None):
    """唸出來，同時用通知顯示文字（聲音關掉也看得到）。full = 通知要顯示的完整內容（預設就是 t）。"""
    print("->", t)
    subprocess.run(["termux-notification", "--id", "7199", "--title", "AI 助手", "--content", full or t],
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
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
    apps = "、".join(sorted(ALL_APPS)[:400])
    prompt = ("使用者對手機助手說：「" + text + "」\n"
        "請判斷這句話是不是下列某個固定指令或App的另一種說法（錯字、同音字、口語都算，例如「藍芽」=藍牙）。\n"
        "固定指令:\n" + cmds + "\nApp名稱: " + apps + "\n"
        "規則：開和關、大聲和小聲是相反的動作，絕對不能搞混；意思不完全一樣就不要硬選。"
        "記憶與聊天：使用者要記住東西、問記過的事、改或刪待辦/筆記、問知識、閒聊，都回 chat（聊天助理有記憶工具可以自己處理）。"
        "只輸出一行JSON，不要解釋：\n"
        '{"route":"cmd","i":指令編號} 或 {"route":"app","name":"App名稱"} 或 {"route":"chat"} 或 {"route":"agent","steps":整數}\n'
        "chat = 純聊天或問答：問知識、解釋、翻譯、建議、閒聊、寫文字、算數，答案用你的常識就能直接說，完全不需要操作手機或查即時資料。"
        "需要即時資訊（今天天氣、新聞、股價、營業時間）或要操作手機的，回 agent。\n"
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
            body = e.read().decode(errors="ignore")
            try:
                import intents
                intents.note_api_error(e.code, body)
            except Exception:
                pass
            if e.code == 400 and extra and "credit balance" not in body.lower():   # 400 可能只是不吃這組參數，退回再試；額度用完就別再試了
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
        elif d.get("route") == "app" and norm(str(d.get("name", ""))) in ALL_APPS:
            return ("app", ALL_APPS[norm(str(d["name"]))])
        elif d.get("route") == "chat":
            return ("chat", None)
        elif d.get("route") == "agent":
            try:
                return ("agent", min(max(int(d.get("steps", 8)), 3), 15))
            except (TypeError, ValueError):
                return ("agent", 8)
        return None
    return None


def confirm_user(msg):
    """跳確認框（App 的原生對話框，不可用才用 termux-dialog）。回 True 才算同意；沒回應 = 否。"""
    try:
        import overlay
        o = overlay.Overlay()
        if o.connect():
            a = o.confirm(msg)
            o.end()
            if a is not None:
                return a
    except Exception:
        pass
    r = subprocess.run(["termux-dialog", "confirm", "-t", "需要你確認", "-i", msg], capture_output=True, text=True)
    try:
        return str(json.loads(r.stdout).get("text", "")).strip().lower() in ("yes", "是")
    except Exception:
        return False


def show_card(text, title="AI 助手"):
    """文字回答：請 App 在螢幕底部浮出卡片（像 Gemini，字會留著）。App 不可用就只有通知，不影響其他流程。"""
    try:
        import overlay
        o = overlay.Overlay()
        if o.connect():
            o.reply(text, title)
            o.end()
    except Exception as e:
        print("文字卡片失敗:", e)


def show_cards(text, gag=None, title="AI 助手"):
    """文字回答的卡片；有 gag 就在同一條連線上，0.5 秒後再送第二張（彩蛋：先「可以」，再跳封禁卡）。App 不可用只有通知。"""
    try:
        import overlay
        o = overlay.Overlay()
        if o.connect():
            o.reply(text, title)
            if gag:
                time.sleep(0.5)
                o.reply(gag, "PhoneAI")
            o.end()
    except Exception as e:
        print("文字卡片失敗:", e)


def show_memory(spoken, full):
    """記憶指令的結果：印出、唸出來；清單（多行）另外用通知顯示完整內容，唸太長聽不完。"""
    print(full)
    show_card(spoken)  # 卡片顯示的文字 = 唸出來的文字（完整清單在通知）
    say(spoken, full)


def ui_done():
    """按送出時 App 已先顯示 Now Bar/光暈（準備中）。立刻就做完的指令（開藍牙、開 App…）沒有 agent2 會去收它，
    所以做完後主動通知 App 結束；失敗就算了（App 15 秒後也會自己收）。"""
    try:
        import overlay
        o = overlay.Overlay()
        if o.connect():
            o.sheet_close()  # 面板還停著等回答的話收起來（已經顯示回答卡片的不會動）
            o.end()
    except Exception:
        pass


_STEPS_RE = re.compile(r"[，,、。\s]*(?:最多|最高|至多|給你|给你|限制在?|限|用|花|走|跑|可以用|能用)?\s*(?<!第)(?<![0-9零〇一二兩两三四五六七八九十])([0-9]{1,3}|[零〇一二兩两三四五六七八九十]{1,3})\s*(?:步驟|步骤|個步驟|个步骤|步)(?:以內|以内|就好|就行|完成|為止|为止|內|内)?[，,、。\s]*")


def parse_steps(text):
    """語音輸入常會直接講「最多 30 步」「給你 50 步」「20 步以內」。抓出明確要的步數（夾在 3~100），並把這句從任務文字拿掉。
    沒講回 (原文, None)。「第 3 步」這種指某一步的不算。步數只是上限：做完就停，多給不會多花錢。"""
    import memory
    m = _STEPS_RE.search(text)
    if not m:
        return text, None
    n = memory.to_int(m.group(1))
    if n is None or n <= 0:
        return text, None
    rest = (text[:m.start()] + " " + text[m.end():]).strip(" ，,、。")
    return (rest or text), min(max(n, 3), 100)


def step_budget(est):
    """Haiku 估的是「順利時」要幾步，實際常因為點錯、載入慢多花幾步。上限放大一倍再加 2，至少 10、最多 25；
    估不到用 12。這只是上限：任務做完就停，多給不會多花錢；卡住有「畫面沒變/連點同位置」偵測會提早停。"""
    try:
        return min(25, max(10, int(est) * 2 + 2))
    except (TypeError, ValueError):
        return 12


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
        if t.startswith(pre) and t[len(pre):] in ALL_APPS:
            return ALL_APPS[t[len(pre):]]
    return None


def run(text):
    text, want_steps = parse_steps(text)  # 使用者明確講了要幾步就照他的（交給 Claude 操作時才用到）
    if not re.match(r"^\s*ai", text, re.I):  # 句首 ai = 明確要交給 Claude，不當記憶指令
        try:
            import memory  # 待辦與筆記（本機處理，不呼叫 AI）
            r = memory.handle(text, confirm=confirm_user)
        except Exception as e:  # 記憶功能出錯不能害整個助理不能用
            print("記憶功能出錯:", e)
            r = None
        if r is not None:
            show_memory(*r)
            ui_done()
            return
    try:
        import chat
        egg = chat.egg_reply(text)  # 彩蛋不靠 Haiku 判斷，只要句子對就一定觸發（不呼叫 API）
    except Exception as e:
        print("彩蛋出錯:", e)
        egg = None
    if egg:
        gag = sys.modules["chat"].PENDING_GAG
        show_cards(egg, gag.pop(0) if gag else None)
        say(egg, egg)
        ui_done()
        return
    try:
        import schedule as sched   # 定時任務（只支援純查詢）：建立／列出／取消
        sr = sched.handle(text, confirm_user)
    except Exception as e:
        print("定時功能出錯:", e)
        sr = None
    if sr:
        show_card(sr)
        say(sr, sr)
        ui_done()
        return
    try:
        import intents
        qa = intents.quick_answer(text)   # 電量、現在幾點：直接讀系統資訊，不用 AI、不截圖、不開光暈
    except Exception as e:
        print("快速回答出錯:", e)
        qa = None
    if qa:
        show_card(qa)
        say(qa, qa)
        ui_done()
        return
    pkg = launch_app(text)
    if pkg:
        start_app(pkg)
        ui_done()
        return
    m = None if re.match(r"^\s*ai", text, re.I) else match(text)
    if not m:
        m2 = re.match(r"^\s*(點擊|點選|點|按|選擇|選)\s*(.+)$", text) if USE_LOCAL else None
        if m2:
            target = m2.group(2).strip().replace("，", ",")
            subprocess.run(["python", f"{HOME}/path.py", target],
                           env=dict(os.environ, PATH_WAIT="1"))
            say("已嘗試點擊，請確認畫面")
            return
        # 規則沒命中：先讓 Haiku 判斷是不是指令表某項的另一種說法（句首 ai 跳過這步）
        forced = re.match(r"^\s*ai", text, re.I)
        r = route(text, agent_only=bool(forced))
        try:   # 額度用完／Key 錯這類問題：後面的 agent2 一定也會失敗，直接把原因顯示給使用者，不要讓面板默默消失
            import intents
            prob = intents.api_problem() if r is None else None
        except Exception:
            prob = None
        if prob:
            show_card(prob)
            say(prob, prob)
            ui_done()
            return
        if r and r[0] == "app":
            print("理解成：開啟 App", r[1])
            start_app(r[1])
            ui_done()
            return
        if r and r[0] == "chat" and not forced:
            print("理解成：聊天")
            try:
                import chat
                reply = chat.ask(text, confirm=confirm_user)
            except Exception as e:
                print("聊天出錯:", e)
                reply = None
            if reply is None:
                try:
                    import intents
                    reply = intents.api_problem() or "我現在連不上 AI，請稍後再試"
                except Exception:
                    reply = "我現在連不上 AI，請稍後再試"
            gag = sys.modules["chat"].PENDING_GAG if "chat" in sys.modules else []
            show_cards(reply, gag.pop(0) if gag else None)
            say(reply, reply)
            ui_done()
            return
        if r and r[0] == "cmd":
            m = r[1]
            print("理解成：", m[2] or m[1][1])
        else:
            # 都不是：交給 Claude 自己看畫面操作（會花 API 費用，每步約 NT$0.1~0.3）
            task = re.sub(r"^\s*ai[\s:：]*", "", text, flags=re.I)
            print("交給 Claude 操作（會用 API 額度）")
            env = dict(os.environ)  # AGENT_WAIT 預設 0：直接截圖（畫面若是 Termux，Claude 會自己用 open/home 離開）
            steps = step_budget(r[1]) if r and r[0] == "agent" else step_budget(None)  # Haiku 估的步數放大成上限；估不到用 12
            if want_steps:
                steps = want_steps
                print("照你說的步數")
            print("最多 %d 步" % steps)
            subprocess.run(["python", f"{HOME}/agent2.py", task, str(steps)], env=env)
            return
    _, (kind, content), reply = m
    if kind == "shc":   # 會影響連線的開關：先問使用者（沒回應 = 不同意）
        if not confirm_user("要執行：%s 嗎？（飛航模式會斷掉網路）" % reply.replace("已", "")):
            say("已取消")
            ui_done()
            return
        kind = "sh"
    if kind == "sh":
        r = subprocess.run(["su", "-c", content], capture_output=True, text=True)
        if r.returncode != 0:
            print("指令失敗:", r.stderr.strip())
            say("執行失敗")
            ui_done()
            return
        say(reply)
        ui_done()
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


if __name__ == "__main__":  # 被 import（例如測試）時不能自己跑起互動模式
    main()
