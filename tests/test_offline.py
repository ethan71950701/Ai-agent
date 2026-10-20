"""離線測試：不需要手機、不呼叫 API。雲端與手機都能跑：  python -m unittest discover -s tests -v
涵蓋：App 名稱查找、指令分流規則、agent2 的回答解析與敏感字、Termux↔App 連線協定（假伺服器）、
App 清單檔（只有該開放的元件對外開放）、repo 裡沒有誤放的金鑰。"""
import json, os, re, socket, subprocess, sys, tempfile, threading, unittest, xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SCRIPTS = os.path.join(ROOT, "scripts")
sys.path.insert(0, SCRIPTS)
os.environ["HOME"] = tempfile.mkdtemp()  # agent2/assistant 會讀 ~/.claude_key、~/apps.tsv
open(os.path.join(os.environ["HOME"], ".claude_key"), "w").write("test-key")
open(os.path.join(os.environ["HOME"], "apps.tsv"), "w", encoding="utf-8").write(
    "Chrome\tcom.android.chrome\n設定\tcom.android.settings\n哔哩哔哩\ttv.danmaku.bili\n相機\tcom.sec.android.app.camera\n")

import applist  # noqa: E402
applist.PATH = os.path.join(os.environ["HOME"], "apps.tsv")
import overlay  # noqa: E402


def load_agent2():
    src = open(os.path.join(SCRIPTS, "agent2.py"), encoding="utf-8").read()
    ns = {"__name__": "agent2_test"}
    exec(src[:src.index("import signal")], ns)  # 不執行最底下會真的跑 main 的部分
    return ns


class AppList(unittest.TestCase):
    def test_find(self):
        t = applist.load()
        self.assertEqual(applist.find("chrome", t), "com.android.chrome")
        self.assertEqual(applist.find("開啟 Chrome 瀏覽器", t), "com.android.chrome")
        self.assertEqual(applist.find("哔哩", t), "tv.danmaku.bili")

    def test_refuse_unsure(self):
        t = applist.load()
        self.assertIsNone(applist.find("a", t))
        self.assertIsNone(applist.find("不存在的app", t))

    def test_bad_lines_ignored(self):
        p = os.path.join(tempfile.mkdtemp(), "x.tsv")
        open(p, "w", encoding="utf-8").write("沒有tab\nX\tbad pkg!\nOK\tcom.ok.app\n")
        old, applist.PATH = applist.PATH, p
        try:
            self.assertEqual(applist.load(), {"ok": "com.ok.app"})
        finally:
            applist.PATH = old


class Assistant(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        import assistant
        cls.a = assistant

    def test_parse_steps_from_voice(self):
        ps = self.a.parse_steps
        self.assertEqual(ps("去Block Blast玩到1000分，最多30步"), ("去Block Blast玩到1000分", 30))
        self.assertEqual(ps("給你五十步，幫我整理相簿"), ("幫我整理相簿", 50))
        self.assertEqual(ps("用20步以內打開設定")[1], 20)
        self.assertEqual(ps("给你 15 个步骤 打开设置")[1], 15)
        self.assertEqual(ps("最多500步玩遊戲")[1], 100)                      # 上限 100
        self.assertEqual(ps("打開設定"), ("打開設定", None))
        self.assertEqual(ps("看第3步的畫面"), ("看第3步的畫面", None))        # 「第 N 步」不是要步數

    def test_commands_hit(self):
        m = self.a.match("幫我開藍牙")
        self.assertIsNotNone(m)
        self.assertIn("bluetooth enable", m[1][1])
        self.assertIn("bluetooth disable", self.a.match("關閉藍牙")[1][1])

    def test_open_vs_close_not_mixed(self):
        self.assertNotEqual(self.a.match("開藍牙")[1][1], self.a.match("關藍牙")[1][1])

    def test_launch_by_name(self):
        self.assertEqual(self.a.launch_app("打開相機"), "com.sec.android.app.camera")
        self.assertEqual(self.a.launch_app("開chrome"), "com.android.chrome")
        self.assertIsNone(self.a.launch_app("打開不存在的東西"))

    def test_wifi_page_not_local_model(self):
        # 進入 Wi-Fi 頁面一律交給 agent2（Claude），不能再走本地模型 path.py
        for text in ("進入設定的WiFi頁面", "設定到WiFi界面", "wifi頁面"):
            m = self.a.match(text)
            self.assertTrue(m is None or m[1][0] != "path", text)

    def test_step_budget_has_headroom(self):
        b = self.a.step_budget
        self.assertEqual(b(None), 12)
        self.assertEqual(b(3), 10)       # 估 3 步也至少給 10
        self.assertEqual(b(6), 14)       # 估 6 步 → 14（約 2 倍多一點）
        self.assertEqual(b(15), 25)      # 上限 25
        self.assertEqual(b("x"), 12)

    def test_local_model_off_by_default(self):
        self.assertFalse(self.a.USE_LOCAL)


class Agent2(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.ns = load_agent2()

    def test_parse_takes_last_json(self):
        p = self.ns["parse"]
        self.assertEqual(p('想一下 {"a":1} 然後 {"action":"done","say":"好"}')["action"], "done")
        m = p('好 {"action":"multitouch","fingers":[{"x1":1,"y1":2,"x2":3,"y2":4},{"x1":5,"y1":6,"x2":7,"y2":8}],"hold_ms":100,"wait":1}')
        self.assertEqual((m["action"], len(m["fingers"]), m["hold_ms"]), ("multitouch", 2, 100))   # 巢狀 JSON（兩指手勢）
        self.assertEqual(p('{"action":"tap","x":1,"y":2,"what":"含 {大括號} 的字"}')["x"], 1)
        self.assertIsNone(p("沒有 json"))
        self.assertIsNone(p("{壞掉"))

    def test_sensitive_words(self):
        tb, cf = self.ns["TASK_BLOCK"], self.ns["CONFIRM"]
        for w in ("付款", "轉帳", "刪除", "密碼"):
            self.assertIn(w, tb)
        for w in ("送出", "登入", "安裝"):
            self.assertIn(w, cf)

    def test_cmd_whitelist_is_small(self):
        c = self.ns["CMDS"]
        self.assertEqual(set(c), {"wifi_on", "wifi_off", "bluetooth_on", "bluetooth_off", "volume_up", "volume_down"})
        for cmd in c.values():
            self.assertRegex(cmd, r"^(svc (wifi|bluetooth) (enable|disable)|input keyevent \d+)$")

    def test_no_play_store_open_without_confirm_list(self):
        self.assertIn("com.android.vending", self.ns["NO_OPEN"])

    def test_prompt_split_for_cache(self):
        s, u = self.ns["SYSTEM"], self.ns["make_user"]("任務X", ["點了A"])
        self.assertNotIn("任務X", s)  # 會變的東西不能放進要快取的 system，否則快取每次失效
        self.assertIn("任務X", u)
        self.assertIn("點了A", u)

    def test_price(self):
        self.assertEqual(self.ns["price"]("claude-sonnet-5-5"), (2, 10))
        self.assertEqual(self.ns["price"]("claude-opus-5-5"), (4, 20))
        self.assertEqual(self.ns["price"]("claude-haiku-5-5"), (0.10, 0.50))

    def test_pick_point(self):
        pp = self.ns["pick_point"]
        self.assertEqual(pp([(500, 300), (510, 310)]), (505, 305))      # 前兩次一致 -> 平均
        self.assertIsNone(pp([(500, 300), (800, 300)]))                 # 差太多
        self.assertEqual(pp([(500, 300), (800, 300), (790, 310)]), (795, 305))  # 第三次與其中一次一致

    def _verify(self, first, replies):
        ns = self.ns
        it = iter(replies)
        calls = []
        info = {"input_tokens": 1, "output_tokens": 1}
        ns["ask_claude"] = lambda *a: (calls.append(1), ('{"action":"tap","x":%d,"y":%d}' % next(it), info))[1]
        return ns["verify_tap"]("t", [], "claude-sonnet-5-5", first), len(calls)

    def test_verify_tap_one_extra_call_when_consistent(self):
        pt, n = self._verify((500, 300), [(510, 310)])
        self.assertEqual((pt, n), ((505, 305), 1))

    def test_verify_tap_third_call_when_inconsistent(self):
        pt, n = self._verify((800, 300), [(500, 300), (790, 310)])
        self.assertEqual((pt, n), ((795, 305), 2))

    def test_verify_tap_all_different_uses_first(self):
        # 三次都對不上時用原本的答案（實測：以前「用最後一次」選到離譜的離群值，點到鍵盤上）
        pt, n = self._verify((100, 100), [(500, 500), (900, 900)])
        self.assertEqual((pt, n), ((100, 100), 2))

    def test_parse_overlay_ids_from_real_phone_output(self):
        # 內容取自使用者手機的實際輸出（2026-10-18）
        d = "    Display 0 com.android.server.display.LocalDisplayAdapter$LocalDisplayDevice@d2214c6\n    Display 12 com.android.server.display.OverlayDisplayAdapter$OverlayDisplayHandle$1@8d69137\n"
        sf = 'Display 4630946532547925907 (HWC display 0): port=147 pnpId=QCM screenPartStatus=UNSUPPORTED displayName=""\nDisplay 11529215047884742424 (Virtual display): displayName="第 1 個重疊效果"\n'
        self.assertEqual(self.ns["parse_overlay"](d, sf), ("12", "11529215047884742424"))
        self.assertEqual(self.ns["parse_overlay"]("", ""), (None, None))

    def test_inp_targets_second_display_only_in_background_mode(self):
        bg = self.ns["BG"]
        old = dict(bg)
        try:
            bg.update(on=False, id=None)
            self.assertEqual(self.ns["inp"]("tap 1 2"), "input tap 1 2")
            bg.update(on=True, id="12")
            self.assertEqual(self.ns["inp"]("tap 1 2"), "input -d 12 tap 1 2")
        finally:
            bg.update(old)

    def test_parse_tasks_from_real_am_stack_list(self):
        # 內容取自使用者手機的實際輸出（2026-10-18），只留相關行
        dump = (
            "RootTask id=1543 bounds=[0,0][1080,2340] displayId=14 userId=0\n"
            " configuration={1.0 mWindowingMode=freeform}\n"
            "  taskId=1554: com.android.settings/com.android.settings.Settings bounds=[81,297][999,1467] userId=0 visible=true topActivity=ComponentInfo{x}\n"
            "RootTask id=1541 bounds=[0,0][1080,2340] displayId=14 userId=0\n"
            "  taskId=1542: com.sec.android.app.launcher/com.honeyspace.dexservice.SecondaryLauncher bounds=[0,0][1080,2340] userId=0 visible=false\n"
            "RootTask id=1401 bounds=[0,0][1440,3120] displayId=0 userId=0\n"
            "  taskId=1401: com.termux/com.termux.app.TermuxActivity bounds=[0,0][1440,3120] userId=0 visible=true\n")
        tasks, size = self.ns["parse_tasks"](dump, 14)
        self.assertEqual(size, (1080, 2340))
        self.assertIn((1554, "com.android.settings", (81, 297, 999, 1467)), tasks)
        self.assertNotIn("com.termux", [t[1] for t in tasks])  # 主螢幕的任務不算
        self.assertEqual(self.ns["parse_tasks"](dump, 99), ([], None))
        self.assertEqual(self.ns["parse_tasks"]("", 14), ([], None))

    def test_parse_roots_for_cleanup(self):
        dump = (
            "RootTask id=1543 bounds=[0,0][1080,2340] displayId=14 userId=0\n"
            "  taskId=1554: com.android.settings/com.android.settings.Settings bounds=[81,297][999,1467] userId=0\n"
            "RootTask id=1541 bounds=[0,0][1080,2340] displayId=14 userId=0\n"
            "  taskId=1542: com.sec.android.app.launcher/com.honeyspace.dexservice.SecondaryLauncher bounds=[0,0][1080,2340] userId=0\n"
            "RootTask id=1401 bounds=[0,0][1440,3120] displayId=0 userId=0\n"
            "  taskId=1401: com.termux/com.termux.app.TermuxActivity bounds=[0,0][1440,3120] userId=0\n")
        roots = self.ns["parse_roots"](dump, 14)
        self.assertEqual(roots, [(1543, ["com.android.settings"]), (1541, ["com.sec.android.app.launcher"])])
        self.assertEqual(self.ns["parse_roots"](dump, 0), [(1401, ["com.termux"])])

    def test_parse_vd_json(self):
        f = self.ns["parse_vd_json"]
        self.assertEqual(f('{"id": 16, "sf": "11529215049832447560"}'), ("16", "11529215049832447560"))
        self.assertEqual(f(""), (None, None))
        self.assertEqual(f("not json"), (None, None))
        self.assertEqual(f('{"id": "x"}'), (None, None))
        sz = self.ns["parse_vd_size"]
        self.assertEqual(sz('{"id": 1, "sf": "2", "w": 1080, "h": 1800}'), (1080, 1800))
        self.assertEqual(sz('{"id": 1, "sf": "2"}'), (1080, 2340))  # 舊格式用預設
        self.assertEqual(sz(""), (1080, 2340))

    def test_parse_launcher_component_from_real_phone_output(self):
        # 內容取自使用者手機（2026-10-18）：蝦皮的 resolve-activity 輸出
        out = ("priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=false\n"
               "com.shopee.tw/com.shopee.app.biz.home.ui.home.HomeActivity\n")
        f = self.ns["parse_launcher_component"]
        self.assertEqual(f(out, "com.shopee.tw"), "com.shopee.tw/com.shopee.app.biz.home.ui.home.HomeActivity")
        self.assertIsNone(f(out, "com.other.app"))   # 不是這個套件的入口不能拿來用
        self.assertIsNone(f("No activity found\n", "com.shopee.tw"))
        self.assertIsNone(f("", "com.shopee.tw"))

    def test_is_dup_type(self):
        f = self.ns["is_dup_type"]
        h = ["開了Chrome", "點了網址列", "輸入了「澎湖旅遊」", "按了Enter"]
        self.assertTrue(f(h, "澎湖旅遊"))
        self.assertFalse(f(h, "墾丁住宿"))
        self.assertFalse(f(["輸入了「澎湖旅遊」"] + ["x"] * 8, "澎湖旅遊"))  # 太久以前的不算
        self.assertFalse(f([], "澎湖旅遊"))

    def test_bg_fallback_turns_background_mode_off_and_alerts(self):
        ns = self.ns
        old = {k: ns[k] for k in ("alert", "su", "vd_stop", "bg_cleanup")}
        bg = ns["BG"]
        saved = dict(bg)
        alerts, cleaned = [], []
        try:
            ns["alert"] = alerts.append
            ns["bg_cleanup"] = lambda: cleaned.append(True)
            bg.update(on=True, vd=True, id="40", sf="123", ripple=True)
            bg["opened"] = {"com.shopee.tw"}
            ns["bg_fallback"]("com.shopee.tw", "App 一開就消失了")
            self.assertFalse(bg["on"])
            self.assertFalse(bg["vd"])
            self.assertIsNone(bg["id"])
            self.assertEqual(len(bg["opened"]), 0)
            self.assertEqual(cleaned, [True])  # 先收掉背景螢幕再關 BG 模式
            self.assertIn("前景", alerts[0])
            self.assertIn("App 一開就消失了", alerts[0])
            # 退回前景後，輸入/截圖都回到主螢幕
            self.assertEqual(ns["inp"]("tap 1 2"), "input tap 1 2")
        finally:
            ns.update(old)
            bg.clear()
            bg.update(saved)

    def test_wait_user_control_pauses_while_user_has_taken_over(self):
        class FakeOv:
            ok = True
            def __init__(self, replies): self.replies = list(replies)
            def paused(self): return self.replies.pop(0) if self.replies else "no"
        f = self.ns["wait_user_control"]
        hist, naps = [], []
        n = f(FakeOv(["yes", "yes", "no"]), hist, sleep=naps.append)
        self.assertEqual((n, len(naps)), (2, 2))               # 等了兩次
        self.assertEqual(len(hist), 1)                          # 結束後註明使用者操作過
        self.assertIn("親自操作", hist[0])
        hist = []
        self.assertEqual(f(FakeOv(["no"]), hist, sleep=naps.append), 0)
        self.assertEqual(hist, [])                              # 沒接手過：什麼都不加
        self.assertEqual(f(FakeOv(["done"]), hist, sleep=naps.append), 0)
        self.assertEqual(len(hist), 1)                          # 剛結束接手（AI 沒碰到暫停）也要註明
        self.assertEqual(f(None, hist, sleep=naps.append), 0)   # 沒有 App 連線就不等
        # 使用者一直不放手也不能永遠卡住
        self.assertEqual(f(FakeOv(["yes"] * 20), [], sleep=naps.append, max_wait=3), 3)

    def test_default_is_sonnet_first(self):
        self.assertEqual(self.ns["MODELS"][0], "claude-sonnet-5-5")

    def test_describe_hides_typed_text(self):
        d = self.ns["describe"]({"action": "type", "text": "秘密密碼123"})
        self.assertNotIn("秘密", d)  # 通知（鎖定畫面看得到）只顯示字數


class IntentsLayer(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        import importlib.util
        spec = importlib.util.spec_from_file_location("intents_test", os.path.join(SCRIPTS, "intents.py"))
        cls.m = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.m)

    def test_search_urls_are_encoded_and_quoted(self):
        b = self.m.build("web_search", {"q": "台北 天氣&x=1;rm -rf /"}, has_pkg=lambda p: True)
        self.assertIn("https://www.google.com/search?q=", b["cmd"])
        self.assertNotIn(";rm", b["cmd"])            # 分號等特殊字元被編碼，不能跳出指令
        self.assertIn("-p com.android.chrome", b["cmd"])
        self.assertNotIn("-p ", self.m.build("web_search", {"q": "a"}, has_pkg=lambda p: False)["cmd"])   # 沒裝就不指定套件

    def test_background_display_and_unknown_names(self):
        self.assertIn("am start --display 7 ", self.m.build("settings_page", {"page": "wifi"}, display=7)["cmd"])
        for bad in ("rm", "", None):
            with self.assertRaises(self.m.IntentError):
                self.m.build(bad, {})
        with self.assertRaises(self.m.IntentError):
            self.m.build("settings_page", {"page": "../x"})

    def test_argument_validation(self):
        with self.assertRaises(self.m.IntentError):
            self.m.build("open_url", {"url": "file:///data/x"})
        with self.assertRaises(self.m.IntentError):
            self.m.build("open_url", {"url": "https://a.com/x y"})
        with self.assertRaises(self.m.IntentError):
            self.m.build("dial", {"number": "123; reboot"})
        with self.assertRaises(self.m.IntentError):
            self.m.build("set_alarm", {"hour": 25, "minute": 0})
        with self.assertRaises(self.m.IntentError):
            self.m.build("brightness", {"level": 300})
        self.assertEqual(self.m.build("set_alarm", {"hour": "7", "minute": 5, "label": "起床"})["desc"], "設定鬧鐘 07:05（起床）")

    def test_sensitive_and_query_flags(self):
        self.assertIn("0912", self.m.build("call", {"number": "0912-345-678"})["ask"])   # 打電話要先問
        self.assertIsNone(self.m.build("dial", {"number": "0912"})["ask"])               # 只開撥號畫面不用問
        q = self.m.build("query", {"what": "battery"})
        self.assertTrue(q["query"])
        with self.assertRaises(self.m.IntentError):
            self.m.build("query", {"what": "contacts"})

    def test_plan_check_accepts_valid_and_rejects_made_up(self):
        m = self.m
        task = "打開蝦皮，搜尋「充電線」，點進第一個商品查看"
        ok = m.plan_check({"name": "shopee_search", "args": {"q": "充電線"}}, task, has_pkg=lambda p: True)
        self.assertEqual((ok["name"], ok["args"], ok["wait"]), ("shopee_search", {"q": "充電線"}, 6.0))
        self.assertIsNone(m.plan_check({"name": "shopee_search", "args": {"q": "手機殼"}}, task))      # q 不在任務文字裡：編造
        self.assertIsNone(m.plan_check({"name": "call", "args": {"number": "0912345678"}}, "打電話給 0912345678"))   # 不在預先規劃白名單
        self.assertIsNone(m.plan_check({"name": "open_url", "args": {"url": "https://a.com"}}, "開 https://a.com"))
        self.assertIsNone(m.plan_check({"name": None}, task))
        self.assertIsNone(m.plan_check({"name": "settings_page", "args": {"page": "nope"}}, "開設定頁 nope"))
        self.assertIsNone(m.plan_check("not a dict", task))
        alarm = m.plan_check({"name": "set_alarm", "args": {"hour": 7, "minute": 30, "label": "起床"}}, "明天早上七點半設鬧鐘叫我起床")
        self.assertEqual(alarm["desc"], "設定鬧鐘 07:30（起床）")
        self.assertEqual(m.plan_check({"name": "query", "args": {"what": "battery"}}, "現在電量多少")["wait"], 0.2)
        self.assertIn("充電線", m.plan_prompt("搜尋充電線"))

    def test_prompt_is_in_system_prompt(self):
        a = load_agent2()
        self.assertIn('"action":"intent"', a["SYSTEM"])
        self.assertIn("shopee_search", a["SYSTEM"])


class StreamEarly(unittest.TestCase):
    def setUp(self):
        self.a = load_agent2()

    def test_core_before_think_waits_for_required_fields(self):
        c = self.a["core_before_think"]
        full = '{"action":"tap","x":300,"y":660,"what":"A","wait":1,"think":"因為'
        self.assertEqual(json.loads(c(full)), {"action": "tap", "x": 300, "y": 660, "what": "A", "wait": 1})
        self.assertIsNone(c('{"action":"tap","x":300,"y":660,"what":"A"'))            # think 還沒開始
        self.assertIsNone(c('{"think":"先想","action":"tap","x":1,"y":2}'))           # think 在最前面：不提早
        self.assertIsNone(c('{"action":"tap","think":"x","x":1,"y":2}'))             # think 前面座標還沒到齊：不能執行不完整的動作
        self.assertIsNone(c('{"action":"drag_start","x":1,"y":2,"think":"x"}'))      # 不在提早清單的動作照舊等整段
        self.assertEqual(json.loads(c('好的 {"action":"back","think":"返回')), {"action": "back"})
        self.assertEqual(json.loads(c('{"action":"done","say":"完成, 有 \\"think\\" 字樣","think":"x')), {"action": "done", "say": '完成, 有 "think" 字樣'})

    def test_stream_consume_returns_core_then_finishes_in_background(self):
        evs = [{"type": "message_start", "message": {"usage": {"input_tokens": 10, "cache_read_input_tokens": 5}}},
               {"type": "content_block_delta", "delta": {"type": "text_delta", "text": '{"action":"tap","x":1,"y":2,'}},
               {"type": "content_block_delta", "delta": {"type": "text_delta", "text": '"wait":1,"think":"因為'}},
               {"type": "content_block_delta", "delta": {"type": "text_delta", "text": '要點它"}'}},
               {"type": "message_delta", "usage": {"output_tokens": 33}}]
        it = iter(evs)
        st = {"text": "", "usage": {"input_tokens": 0, "output_tokens": 0, "cache_read_input_tokens": 0, "cache_creation_input_tokens": 0}}
        core = self.a["stream_consume"](it, st)
        self.assertEqual(json.loads(core), {"action": "tap", "x": 1, "y": 2, "wait": 1})
        self.assertEqual(st["usage"]["input_tokens"], 10)
        self.assertIsNone(self.a["stream_consume"](it, st, early=False))      # 剩下的在背景收完
        self.assertEqual(self.a["parse"](st["text"])["think"], "因為要點它")
        self.assertEqual(st["usage"]["output_tokens"], 33)

    def test_stream_consume_without_think_just_reads_everything(self):
        evs = [{"type": "content_block_delta", "delta": {"type": "text_delta", "text": '{"action":"wait","wait":2}'}},
               {"type": "message_delta", "usage": {"output_tokens": 9}}]
        st = {"text": "", "usage": {"input_tokens": 0, "output_tokens": 0, "cache_read_input_tokens": 0, "cache_creation_input_tokens": 0}}
        self.assertIsNone(self.a["stream_consume"](iter(evs), st))
        self.assertEqual(st["text"], '{"action":"wait","wait":2}')
        self.assertEqual(st["usage"]["output_tokens"], 9)


class UITree(unittest.TestCase):
    XML = ('<?xml version="1.0"?><hierarchy rotation="0">'
           '<node index="0" text="" resource-id="" class="android.widget.FrameLayout" package="p" content-desc="" clickable="false" password="false" scrollable="false" bounds="[0,0][1080,2340]">'
           '<node index="0" text="連接" resource-id="p:id/title" class="android.widget.TextView" package="p" content-desc="" clickable="true" password="false" scrollable="false" bounds="[100,500][500,600]"/>'
           '<node index="1" text="" resource-id="" class="android.widget.ImageButton" package="p" content-desc="更多選項" clickable="true" password="false" scrollable="false" bounds="[900,100][1000,200]"/>'
           '<node index="2" text="secret" resource-id="" class="android.widget.EditText" package="p" content-desc="" clickable="true" password="true" scrollable="false" bounds="[0,700][1080,800]"/>'
           '<node index="3" text="看不見" resource-id="" class="android.widget.TextView" package="p" content-desc="" clickable="false" password="false" scrollable="false" bounds="[0,0][0,0]"/>'
           '<node index="4" text="清單" resource-id="" class="android.widget.ListView" package="p" content-desc="" clickable="false" password="false" scrollable="true" bounds="[0,900][1080,2000]"/>'
           '</node></hierarchy>')

    def test_parse_keeps_useful_nodes_and_skips_password_and_invisible(self):
        import importlib.util
        spec = importlib.util.spec_from_file_location("uitree_test", os.path.join(SCRIPTS, "uitree.py"))
        m = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(m)
        items = m.parse(self.XML)
        labels = [m.line(i) for i in items]
        self.assertEqual(labels, ["0* 連接 (300,550)", "1* 更多選項 (950,150)", "2 清單 (540,1450)"])
        self.assertFalse(any("secret" in l or "看不見" in l for l in labels))
        self.assertEqual(m.parse("not xml"), [])


class VDisplayStream(unittest.TestCase):
    """vdisplay.py 的 H.264 轉送：切 NAL、記住 SPS/PPS 與最近關鍵幀以來的資料（新預覽連上要馬上有畫面）。"""

    @classmethod
    def setUpClass(cls):
        import importlib.util
        spec = importlib.util.spec_from_file_location("vdisplay_test", os.path.join(SCRIPTS, "vdisplay.py"))
        cls.mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.mod)

    S4, S3 = b"\x00\x00\x00\x01", b"\x00\x00\x01"

    def stream(self):
        # SPS(67) PPS(68) IDR(65) P(41) P(41) IDR(65) P(41)，混用 4/3 位元組起始碼
        return (self.S4 + b"\x67AA" + self.S4 + b"\x68BB" + self.S4 + b"\x65CCCC" + self.S3 + b"\x41DD"
                + self.S4 + b"\x41EE" + self.S3 + b"\x65FFFF" + self.S4 + b"\x41GG" + self.S4 + b"\x41HH")

    def test_nal_split_and_gop_cache_even_with_tiny_chunks(self):
        p = self.mod.StreamProxy()
        data = self.stream()
        got = []
        for i in range(0, len(data), 3):   # 每 3 個位元組餵一次，測切在任何位置都對
            got += p.feed(data[i:i + 3])
        types = [n[4] & 0x1F for n in got]
        self.assertEqual(types, [7, 8, 5, 1, 1, 5, 1])  # 最後一個 NAL（HH）還沒收完整，不會回傳
        # 最近的關鍵幀是第二個 IDR：備份 = SPS + PPS + IDR(FFFF) + P(GG)
        self.assertEqual(p.backlog(), self.S4 + b"\x67AA" + self.S4 + b"\x68BB" + self.S4 + b"\x65FFFF" + self.S4 + b"\x41GG")
        self.assertEqual(p.frames, 5)   # 畫面數 = 類型 1、5 的 NAL（用來量串流幀率）

    def test_backlog_before_first_keyframe_is_just_config(self):
        p = self.mod.StreamProxy()
        p.feed(self.S4 + b"\x67AA" + self.S4 + b"\x68BB" + self.S4 + b"\x41ZZ")
        self.assertEqual(p.backlog(), self.S4 + b"\x67AA" + self.S4 + b"\x68BB")
        self.assertEqual(self.mod.StreamProxy().backlog(), b"")

    def test_frame_meta_packets_are_forwarded_without_waiting_for_next_frame(self):
        def pkt(payload):
            return b"\x00" * 8 + len(payload).to_bytes(4, "big") + payload
        f, p = self.mod.FrameSplitter(), self.mod.StreamProxy()
        data = pkt(self.S4 + b"\x67AA" + self.S4 + b"\x68BB") + pkt(self.S4 + b"\x65CCCC")
        got = []
        for i in range(0, len(data), 5):   # 切在任何位置都對
            for k in f.feed(data[i:i + 5]):
                got += p.feed(k + self.mod.AUD)
        types = [n[4] & 0x1F for n in got if n[4] & 0x1F != 9]
        self.assertEqual(types, [7, 8, 5])   # IDR 不用等下一張畫面就送出
        self.assertEqual(f.buf, b"")

    def test_parse_raw_header(self):
        import struct
        a = load_agent2()
        px = b"\x00" * (4 * 3 * 2)            # 3x2 的 RGBA 像素
        h12 = struct.pack("<III", 3, 2, 1)
        self.assertEqual(a["parse_raw_header"](h12 + px), (3, 2, 12))
        self.assertEqual(a["parse_raw_header"](h12 + b"\x00" * 4 + px), (3, 2, 16))    # 新版多一個 4 位元組（色彩空間）
        self.assertIsNone(a["parse_raw_header"](struct.pack("<III", 3, 2, 4) + px))      # 格式不是 RGBA_8888
        self.assertIsNone(a["parse_raw_header"](h12 + px[:-1]))                           # 長度對不上（輸出被弄壞）
        self.assertIsNone(a["parse_raw_header"](b"error"))

    @staticmethod
    def _frame(inner, border, w=100, h=100):
        """w x h 的假畫面（RGBA）：中間 inner 值、最外圈 border 值（模擬邊緣光暈在動）。"""
        m = int(w * 0.14)
        px = bytearray()
        for y in range(h):
            for x in range(w):
                px += bytes([border if (x < m or x >= w - m or y < m or y >= h - m) else inner]) * 4
        return bytes(px), w, h, 0

    def test_raw_sig_ignores_border_but_sees_content(self):
        a = load_agent2()
        sig = lambda f: a["raw_sig"](f[0], f[3], f[1], f[2])
        self.assertEqual(sig(self._frame(10, 1)), sig(self._frame(10, 200)))   # 只有邊緣（光暈）變：指紋不變
        self.assertNotEqual(sig(self._frame(10, 1)), sig(self._frame(11, 1)))   # 內容變：指紋變

    def test_wait_loaded_continues_once_rich_enough(self):
        a = load_agent2()
        w = h = 96
        flat = (b"".join(bytes([128, 128, 128, 255]) for _ in range(w * h)), w, h, 0)
        rich = (b"".join(bytes([(x * 7) % 256, (y * 5) % 256, ((x + y) * 3) % 256, 255]) for y in range(h) for x in range(w)), w, h, 0)
        frames = iter([flat, flat, rich, rich])
        calls, clock = [], [0.0]
        def poll():
            calls.append(1)
            return next(frames)
        sleep = lambda s: clock.__setitem__(0, clock[0] + s)
        now = lambda: clock[0]
        got = a["wait_loaded"](1.5, 9.0, 150, poll=poll, sleep=sleep, now=now)
        self.assertEqual(len(calls), 3)                       # 前兩張是骨架畫面：繼續等；第三張夠豐富就結束
        self.assertAlmostEqual(got, 1.5 + 0.4 * 2, places=3)
        self.assertIsNotNone(a["RAW_CACHE"]["cap"])           # 最後那張留給下一步截圖用
        self.assertIsNotNone(a["take_cached"](now=now))
        self.assertIsNone(a["take_cached"](now=now))          # 只能用一次

    def test_wait_loaded_gives_up_at_max_wait(self):
        a = load_agent2()
        w = h = 96
        flat = (b"".join(bytes([128, 128, 128, 255]) for _ in range(w * h)), w, h, 0)
        clock = [0.0]
        sleep = lambda s: clock.__setitem__(0, clock[0] + s)
        got = a["wait_loaded"](1.5, 5.0, 150, poll=lambda: flat, sleep=sleep, now=lambda: clock[0])
        self.assertGreaterEqual(got, 5.0)
        self.assertLess(got, 6.0)

    def test_short_mode_hint_only_when_enabled(self):
        a = load_agent2()
        self.assertNotIn("簡短模式", a["make_user"]("搜尋", []))
        a["SHORT_MODE"][0] = True
        u = a["make_user"]("搜尋", [])
        self.assertIn("簡短模式", u)
        self.assertIn("不要寫think", u)
        self.assertTrue(u.endswith("請決定下一步，只輸出一行標準JSON。"))

    def test_plan_by_rules(self):
        import importlib.util
        spec = importlib.util.spec_from_file_location("intents_t3", os.path.join(SCRIPTS, "intents.py"))
        m = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(m)
        r = m.plan_by_rules("打開蝦皮，搜尋「充電線」，點進第一個商品查看", has_pkg=lambda p: True)
        self.assertEqual((r["name"], r["args"]), ("shopee_search", {"q": "充電線"}))
        self.assertEqual(r["source"], "rule")
        self.assertEqual(m.plan_by_rules("在 YouTube 搜尋「貓咪」，點進第一支影片")["name"], "youtube_search")
        self.assertEqual(m.plan_by_rules('用 Chrome 搜尋 "台北天氣"')["name"], "web_search")
        self.assertIsNone(m.plan_by_rules("搜尋「充電線」"))                              # 沒指名 App：交給 Haiku
        self.assertIsNone(m.plan_by_rules("在蝦皮和 YouTube 搜尋「貓咪」"))                # 兩個 App
        self.assertIsNone(m.plan_by_rules("在蝦皮搜尋「充電線」再搜尋「手機殼」"))          # 兩個關鍵字
        self.assertIsNone(m.plan_by_rules("打開蝦皮，把「充電線」加入購物車"))              # 沒有搜尋動詞
        self.assertIsNone(m.plan_by_rules("在蝦皮搜尋充電線"))                              # 沒有引號：不猜
        self.assertEqual(m.plan_by_rules("蝦皮搜尋「充電線」")["min_richness"], 150)

    def test_wait_changed_needs_change_stability_and_richness(self):
        a = load_agent2()
        w = h = 96
        mk = lambda f: (b"".join(bytes(f(x, y)) + b"\xff" for y in range(h) for x in range(w)), w, h, 0)
        # 注意：raw_sig 只看畫面中間 72%，所以每張的差異都放在中間
        old = mk(lambda x, y: ((x * 7) % 256, (y * 5) % 256, 10))            # 點擊前（豐富）
        mid = mk(lambda x, y: ((x * 3) % 256, 40, (y * 9) % 256))             # 轉場中途（豐富但還會變）
        skel = mk(lambda x, y: (128, 128, 128))                               # 灰色骨架（穩定但不豐富）
        new = mk(lambda x, y: ((x * 11) % 256, (y * 13) % 256, 90))           # 載入完（豐富、穩定）
        sig = lambda c: a["raw_sig"](c[0], c[3], c[1], c[2])
        calls, clock = [], [0.0]
        frames = iter([old, mid, skel, skel, new, new])
        def poll():
            calls.append(1)
            return next(frames)
        sleep = lambda s: clock.__setitem__(0, clock[0] + s)
        got = a["wait_changed"](9.0, 150, sig(old), poll=poll, sleep=sleep, now=lambda: clock[0])
        self.assertEqual(len(calls), 6)        # old（還沒變）、mid（變了但前一張不同）、skel（穩定但不豐富）×2、new（變了，前一張是 skel 不同）、new（穩定又豐富）→ 才結束
        self.assertLess(got, 9.0)

    def test_wait_changed_never_waits_longer_than_max(self):
        a = load_agent2()
        w = h = 96
        flat = (b"".join(bytes([128, 128, 128, 255]) for _ in range(w * h)), w, h, 0)
        clock = [0.0]
        sleep = lambda s: clock.__setitem__(0, clock[0] + s)
        got = a["wait_changed"](2.0, 150, a["raw_sig"](flat[0], 0, w, h), poll=lambda: flat, sleep=sleep, now=lambda: clock[0] + 0.0)
        self.assertGreaterEqual(got, 2.0)
        self.assertLess(got, 3.0)

    def test_plan_check_marks_calibrated_intents(self):
        import importlib.util
        spec = importlib.util.spec_from_file_location("intents_t2", os.path.join(SCRIPTS, "intents.py"))
        m = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(m)
        yt = m.plan_check({"name": "youtube_search", "args": {"q": "貓咪"}}, "在 YouTube 搜尋貓咪")
        self.assertEqual((yt["min_richness"], yt["min_wait"]), (150, 1.5))
        web = m.plan_check({"name": "web_search", "args": {"q": "天氣"}}, "搜尋天氣")
        self.assertEqual((web["min_wait"], web["min_richness"], web["tap_richness"]), (1.0, 110, 80))   # 載入中 86 < 110 < 載入完 133；氣象署頁面只有 108～126 所以點擊後用 80
        self.assertEqual(yt["tap_richness"], 150)
        self.assertEqual(m.plan_check({"name": "web_search", "args": {"q": "天氣"}}, "搜尋天氣")["min_richness"], 110)
        self.assertIsNone(m.plan_check({"name": "maps_search", "args": {"q": "咖啡"}}, "地圖搜尋咖啡")["min_richness"])   # 地圖還沒校準

    def test_screen_richness_flat_vs_colourful(self):
        a = load_agent2()
        w = h = 96
        flat = b"".join(bytes([128, 128, 128, 255]) for _ in range(w * h))
        rich = b"".join(bytes([(x * 7) % 256, (y * 5) % 256, ((x + y) * 3) % 256, 255]) for y in range(h) for x in range(w))
        self.assertEqual(a["screen_richness"](flat, 0, w, h), 1)
        self.assertGreater(a["screen_richness"](rich, 0, w, h), 50)

    def test_settle_stops_once_changed_and_stable_even_if_border_glows(self):
        a = load_agent2()
        sig = lambda f: a["raw_sig"](f[0], f[3], f[1], f[2])
        A, B = self._frame(10, 1), self._frame(50, 1)
        a["LAST_SIG"][0] = sig(A)
        frames = iter([self._frame(10, 5), self._frame(50, 9), self._frame(50, 77), self._frame(99, 1)])   # 動作前的內容、變了、穩定（邊緣一直在動）
        calls = []
        def poll():
            calls.append(1)
            return next(frames)
        real_sleep = a["time"].sleep
        a["time"].sleep = lambda s: None
        try:
            a["settle"](30, poll=poll)
        finally:
            a["time"].sleep = real_sleep
        self.assertEqual(len(calls), 3)

    def test_settle_gives_up_at_max_wait_when_nothing_changes(self):
        a = load_agent2()
        f = self._frame(10, 1)
        a["LAST_SIG"][0] = a["raw_sig"](f[0], f[3], f[1], f[2])
        calls = []
        def same():
            calls.append(1)
            return f
        clock = [0.0]
        real_sleep, real_time = a["time"].sleep, a["time"].time
        a["time"].sleep = lambda s: clock.__setitem__(0, clock[0] + s)
        a["time"].time = lambda: (clock.__setitem__(0, clock[0] + 0.3), clock[0])[1]
        try:
            a["settle"](2.0, poll=same)
        finally:
            a["time"].sleep, a["time"].time = real_sleep, real_time
        self.assertTrue(1 <= len(calls) <= 8)

    def test_clear_field_sends_select_all_then_one_delete(self):
        a = load_agent2()
        sent = []
        a["su"] = lambda c: sent.append(c) or ""
        a["clear_field"]()
        cmd = sent[0]
        self.assertIn("input keycombination 113 29", cmd)            # Ctrl+A
        self.assertTrue(cmd.endswith("input keyevent 67"))            # 再按一次刪除鍵
        self.assertEqual(cmd.count("keyevent"), 1)                    # 只有一次（沒有連按退格）
        a["BG"].update(on=True, id="7")
        sent.clear()
        a["clear_field"]()
        self.assertIn("input -d 7 keycombination 113 29", sent[0])   # 背景模式指定螢幕

    def test_pick_new_sf(self):
        a = load_agent2()
        dump = "Display 4619827259835644672 (HWC display 0)\nDisplay 111 (Virtual display)\nDisplay 222 (Virtual display)\n"
        self.assertEqual(a["pick_new_sf"](dump, ["111"]), "222")
        self.assertIsNone(a["pick_new_sf"](dump, []))       # 兩個都是新的：不確定是哪個
        self.assertIsNone(a["pick_new_sf"](dump, ["111", "222"]))

    def test_parse_dpi(self):
        self.assertEqual(self.mod.parse_dpi("1080x1800/420"), 420)
        self.assertEqual(self.mod.parse_dpi("1080x1800"), 420)
        self.assertEqual(self.mod.parse_dpi("1080x1800/360"), 360)

    def test_frame_splitter_skips_scrcpy_session_header(self):
        f = self.mod.FrameSplitter()
        session = b"\x80\x00\x00\x00" + (1080).to_bytes(4, "big") + (1800).to_bytes(4, "big")
        frame = b"\x00" * 8 + (6).to_bytes(4, "big") + self.S4 + b"\x41Z"
        self.assertEqual(f.feed(session + frame), [self.S4 + b"\x41Z"])


class SelfTest(unittest.TestCase):
    """selftest.py 的純函式與不碰手機的檢查。"""

    @classmethod
    def setUpClass(cls):
        import importlib.util
        spec = importlib.util.spec_from_file_location("selftest_test", os.path.join(SCRIPTS, "selftest.py"))
        cls.mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.mod)

    def test_a11y_enabled(self):
        f = self.mod.a11y_enabled
        self.assertTrue(f("com.a/.S:com.phoneai.overlay/com.phoneai.overlay.RippleAccessibilityService"))
        self.assertFalse(f("com.a/.S:com.b/.T"))
        self.assertFalse(f("null"))
        self.assertFalse(f(""))
        self.assertFalse(f("com.phoneai.overlayx/.S"))  # 套件名只是前綴相同，不算

    def test_sha256_of_and_expected_hash_is_a_sha256(self):
        p = os.path.join(tempfile.mkdtemp(), "f")
        open(p, "wb").write(b"abc")
        self.assertEqual(self.mod.sha256_of(p), "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
        self.assertRegex(self.mod.JAR_SHA256, r"^[0-9a-f]{64}$")

    def test_check_key_never_reads_content_and_checks_permissions(self):
        mod = self.mod
        old = mod.HOME
        mod.HOME = tempfile.mkdtemp()
        try:
            self.assertEqual(mod.check_key()[0], mod.BAD)            # 沒有檔案
            p = os.path.join(mod.HOME, ".claude_key")
            open(p, "w").write("x" * 40)
            os.chmod(p, 0o644)
            self.assertEqual(mod.check_key()[0], mod.WARN)           # 別人讀得到
            os.chmod(p, 0o600)
            st, msg, _ = mod.check_key()
            self.assertEqual(st, mod.OK)
            self.assertNotIn("x" * 10, msg)                           # 訊息裡不會出現 Key 的內容
        finally:
            mod.HOME = old

    def test_every_check_returns_status_message_fix(self):
        for name, fn in self.mod.CHECKS:
            self.assertTrue(callable(fn), name)


class AuditSecrets(unittest.TestCase):
    """audit_secrets.py：在暫時的 git repo 裡造出「曾經提交過 Key」「真實 Email」「金鑰庫」，確認都抓得到。"""

    @classmethod
    def setUpClass(cls):
        import importlib.util
        spec = importlib.util.spec_from_file_location("audit_test", os.path.join(SCRIPTS, "audit_secrets.py"))
        cls.mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.mod)

    def _repo(self, email):
        d = tempfile.mkdtemp()
        env = dict(os.environ, GIT_AUTHOR_NAME="某人", GIT_AUTHOR_EMAIL=email,
                   GIT_COMMITTER_NAME="某人", GIT_COMMITTER_EMAIL=email)
        def git(*a):
            subprocess.run(["git", "-C", d] + list(a), check=True, capture_output=True, env=env)
        git("init", "-q")
        fake_key = "sk-" + "ant-api03-" + "A" * 20  # 執行時才組出來，免得這個測試檔本身被「沒有 Key 被提交」的檢查抓到
        open(os.path.join(d, "a.py"), "w").write('KEY = "%s"\n' % fake_key)
        open(os.path.join(d, "notes.md"), "w").write("我是某人\n")
        open(os.path.join(d, "x.keystore"), "wb").write(b"\x00\x01")
        git("add", "-A")
        git("commit", "-qm", "init")
        open(os.path.join(d, "a.py"), "w").write("KEY = None\n")  # 現在已刪掉，但歷史還在
        git("commit", "-qam", "remove key")
        return d

    def test_finds_history_secret_real_email_keystore_and_missing_license(self):
        d = self._repo("me@example.com")
        res = self.mod.audit(d, words=["某人", "不存在的字"])
        titles = [t for _, t, _ in res]
        text = "\n".join(titles)
        self.assertIn("歷史裡有金鑰", text)                       # 工作樹乾淨了但歷史還有
        self.assertIn("x.keystore", text)
        self.assertIn("me@example.com", text)
        self.assertIn("沒有 LICENSE", text)
        self.assertTrue(any(st == self.mod.WARN and "某人" in t for st, t, _ in res))
        self.assertTrue(any(st == self.mod.OK and "不存在的字" in t for st, t, _ in res))
        self.assertFalse(any("工作樹裡有金鑰" in t for t in titles))

    def test_clean_repo_with_noreply_and_license_is_ok(self):
        d = tempfile.mkdtemp()
        env = dict(os.environ, GIT_AUTHOR_NAME="a", GIT_AUTHOR_EMAIL="123+a@users.noreply.github.com",
                   GIT_COMMITTER_NAME="a", GIT_COMMITTER_EMAIL="123+a@users.noreply.github.com")
        open(os.path.join(d, "LICENSE"), "w").write("MIT")
        open(os.path.join(d, "a.py"), "w").write("print(1)\n")
        subprocess.run(["git", "-C", d, "init", "-q"], check=True, capture_output=True)
        subprocess.run(["git", "-C", d, "add", "-A"], check=True, capture_output=True)
        subprocess.run(["git", "-C", d, "commit", "-qm", "x"], check=True, capture_output=True, env=env)
        res = self.mod.audit(d)
        self.assertTrue(all(st == self.mod.OK for st, _, _ in res), res)


class UpdateApp(unittest.TestCase):
    """update_app.py：挑選邏輯，以及最重要的安全性——轉址到別的網站時不能把權杖一起送過去。"""

    @classmethod
    def setUpClass(cls):
        import importlib.util
        spec = importlib.util.spec_from_file_location("update_app_test", os.path.join(SCRIPTS, "update_app.py"))
        cls.mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.mod)

    def test_git_env_passes_token_without_putting_it_on_the_command_line(self):
        import base64
        env = self.mod.git_env("TOKEN123")
        self.assertEqual(env["GIT_CONFIG_KEY_0"], "http.extraHeader")
        self.assertIn(base64.b64encode(b"x-access-token:TOKEN123").decode(), env["GIT_CONFIG_VALUE_0"])
        self.assertEqual(env["GIT_TERMINAL_PROMPT"], "0")                           # 不要卡在帳密提示

    def test_init_repo_restores_old_folder_when_clone_fails(self):
        m = self.mod
        d = tempfile.mkdtemp()
        old_dir, old_sh = m.REPO_DIR, m.sh
        m.REPO_DIR = d + "/phone-ai-agent"
        os.makedirs(m.REPO_DIR)
        open(m.REPO_DIR + "/keep.txt", "w").write("zip version")
        calls = []
        m.sh = lambda cmd, timeout=120, env=None: (calls.append((cmd, env)) or (128, "fatal: auth failed"))
        try:
            self.assertEqual(m.init_repo("TOKEN123"), 1)
            self.assertTrue(os.path.exists(m.REPO_DIR + "/keep.txt"))              # 失敗要還原，不能把使用者的資料夾弄丟
            self.assertFalse(os.path.exists(m.REPO_DIR + ".zipbak"))
            self.assertNotIn("TOKEN123", calls[0][0])                               # 權杖不在指令列
            self.assertEqual(m.init_repo("TOKEN123") , 1)
        finally:
            m.REPO_DIR, m.sh = old_dir, old_sh

    def test_pick_release_finds_apk_and_commit(self):
        rel = {"tag_name": "build-12-abc1234", "body": "commit: " + "a" * 40 + "\n說明",
               "assets": [{"name": "notes.txt", "browser_download_url": "x"},
                          {"name": "app-debug.apk", "browser_download_url": "http://dl/apk", "url": "http://api/asset/1", "size": 99, "state": "uploaded"}]}
        r = self.mod.pick_release(rel)
        self.assertEqual((r["sha"], r["url"], r["api_url"], r["size"]), ("a" * 40, "http://dl/apk", "http://api/asset/1", 99))
        rel["body"] = "沒有 commit 行"
        self.assertEqual(self.mod.pick_release(rel)["sha"], "abc1234")          # 退而求其次：tag 最後面的 7 碼
        self.assertIsNone(self.mod.pick_release({"tag_name": "t", "assets": []}))
        self.assertIsNone(self.mod.pick_release(None))
        rel["assets"][1]["state"] = "starter"                                    # 還在上傳中的檔案不能用
        self.assertIsNone(self.mod.pick_release(rel))

    def test_repo_slug_can_be_overridden_for_forks(self):
        old = os.environ.get("PHONEAI_REPO")
        try:
            os.environ["PHONEAI_REPO"] = "someone/their-fork"
            self.assertEqual(self.mod.repo_slug(), "someone/their-fork")
            os.environ["PHONEAI_REPO"] = "not a repo"                             # 格式不對就用預設，不要壞掉
            self.assertEqual(self.mod.repo_slug(), self.mod.DEFAULT_REPO)
        finally:
            if old is None: os.environ.pop("PHONEAI_REPO", None)
            else: os.environ["PHONEAI_REPO"] = old

    def test_download_without_token_sends_no_authorization(self):
        import http.server
        seen = {}

        class H(http.server.BaseHTTPRequestHandler):
            def do_GET(self):
                seen["auth"] = self.headers.get("Authorization")
                self.send_response(200); self.end_headers(); self.wfile.write(b"APK")
            def log_message(self, *a): pass
        sv = http.server.HTTPServer(("127.0.0.1", 0), H)
        threading.Thread(target=sv.serve_forever, daemon=True).start()
        try:
            data = self.mod.download_asset("http://127.0.0.1:%d/a" % sv.server_port)
        finally:
            sv.shutdown()
        self.assertEqual(data, b"APK")
        self.assertIsNone(seen["auth"])

    def test_app_is_current(self):
        f = self.mod.app_is_current
        self.assertTrue(f("abc1234", "abc1234def" + "0" * 30))
        self.assertFalse(f("abc1234", "fff1234def" + "0" * 30))
        self.assertFalse(f("dev", "abc1234" + "0" * 33))   # 自己編譯的 dev 版一律當作要更新
        self.assertFalse(f(None, "abc1234" + "0" * 33))

    def test_http_error_messages_never_mention_token(self):
        for code in (401, 403, 404, 500):
            self.assertNotIn("ghp_", self.mod.explain_http_error(code))

    def test_redirect_to_other_host_does_not_carry_the_token(self):
        import http.server
        seen = {}

        class B(http.server.BaseHTTPRequestHandler):  # 模擬 GitHub 轉址後的檔案儲存網站
            def do_GET(self):
                seen["auth_b"] = self.headers.get("Authorization")
                self.send_response(200)
                self.end_headers()
                self.wfile.write(b"ZIPDATA")
            def log_message(self, *a): pass
        sb = http.server.HTTPServer(("127.0.0.1", 0), B)
        threading.Thread(target=sb.serve_forever, daemon=True).start()

        class A(http.server.BaseHTTPRequestHandler):  # 模擬 api.github.com 的下載網址（回 302）
            def do_GET(self):
                seen["auth_a"] = self.headers.get("Authorization")
                self.send_response(302)
                self.send_header("Location", "http://127.0.0.1:%d/blob" % sb.server_port)
                self.end_headers()
            def log_message(self, *a): pass
        sa = http.server.HTTPServer(("127.0.0.1", 0), A)
        threading.Thread(target=sa.serve_forever, daemon=True).start()
        try:
            data = self.mod.download_asset("http://127.0.0.1:%d/zip" % sa.server_port, "SECRETTOKEN")
        finally:
            sa.shutdown(); sb.shutdown()
        self.assertEqual(data, b"ZIPDATA")
        self.assertEqual(seen["auth_a"], "Bearer SECRETTOKEN")   # 第一次（GitHub）要帶
        self.assertIsNone(seen["auth_b"])                         # 轉址後（別的網站）絕對不能帶


class Memory(unittest.TestCase):
    """memory.py：判斷是不是記憶指令（含簡體）、待辦/筆記的新增列出完成刪除，以及不誤攔截一般指令。"""

    @classmethod
    def setUpClass(cls):
        import importlib.util
        spec = importlib.util.spec_from_file_location("memory_test", os.path.join(SCRIPTS, "memory.py"))
        cls.mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.mod)

    def test_pinned_memory_cannot_be_changed_by_ai_and_lessons(self):
        m = self.mod
        path = os.path.join(tempfile.mkdtemp(), "m.json")
        self.assertIn("永久", m.run_tool("memory_add", {"kind": "note", "text": "我對花生過敏", "permanent": True}, path))
        m.run_tool("memory_add", {"kind": "note", "text": "普通筆記"}, path)
        self.assertIn("手動", m.run_tool("memory_update", {"id": 1, "text": "改掉"}, path))
        self.assertIn("手動", m.run_tool("memory_delete", {"ids": [1]}, path))
        self.assertIn("已刪除 1 項", m.run_tool("memory_delete", {"ids": [1, 2]}, path))                  # 普通的刪了、永久的留著
        self.assertIn("手動", m.handle("忘掉花生", path)[0])                                             # 規則路徑也一樣
        r = m.run_tool("memory_clear", {"scope": "all"}, path, lambda msg: True)
        self.assertIn("可刪", r)
        self.assertIn("1 項永久記憶", r)
        self.assertEqual(m.note_lines(path), ["我對花生過敏"])
        # 經驗
        self.assertTrue(m.add_lesson("先看落點再放開", "com.block.juggle", path))
        self.assertFalse(m.add_lesson("先看落點再放開", "com.block.juggle", path))                       # 重複不存
        m.add_lesson("通用技巧", None, path)
        self.assertEqual(m.lessons("com.block.juggle", path), ["通用技巧", "先看落點再放開"])
        self.assertEqual(m.lessons("com.other", path), ["通用技巧"])
        self.assertEqual(m.note_lines(path), ["我對花生過敏"])                                           # 經驗不會混進筆記

    def test_clear_all_needs_confirmation(self):
        m = self.mod
        self.assertEqual(m.parse("把記憶都刪除"), ("clear", "all"))
        self.assertEqual(m.parse("清空待辦"), ("clear", "todo"))
        self.assertEqual(m.parse("刪掉全部筆記"), ("clear", "note"))
        self.assertEqual(m.parse("刪除第2項"), ("delete", ("num", 2)))      # 單項刪除不受影響
        path = os.path.join(tempfile.mkdtemp(), "m.json")
        m.handle("記住我要買牛奶", path)
        m.handle("記住車牌ABC", path)
        self.assertIn("沒有刪除", m.handle("把記憶都刪除", path)[0])                  # 沒有確認方式 = 不刪
        self.assertIn("沒有刪除", m.handle("把記憶都刪除", path, confirm=lambda msg: False)[0])
        self.assertEqual(len(m.live(m.load(path), "todo")), 1)
        asked = []
        r = m.handle("把記憶都刪除", path, confirm=lambda msg: asked.append(msg) or True)
        self.assertIn("共 2 項", asked[0])
        self.assertIn("已刪除全部", r[0])
        self.assertEqual(m.live(m.load(path), "todo") + m.live(m.load(path), "note"), [])
        self.assertIn("沒有", m.handle("清空待辦", path, confirm=lambda msg: True)[0])

    def test_parse(self):
        p = self.mod.parse
        self.assertIsNone(p("明天有什麼事"))              # 問某一天不是要整份清單
        self.assertIsNone(p("我的車牌是多少"))
        self.assertEqual(p("今天有什麼事"), ("list", "todo"))
        self.assertEqual(p("記住我今天要買牛奶"), ("add", "todo", "我今天要買牛奶"))
        self.assertEqual(p("幫我記我的車牌是ABC-1234"), ("add", "note", "我的車牌是ABC-1234"))
        self.assertEqual(p("提醒我下午三點開會"), ("add", "todo", "下午三點開會"))
        self.assertEqual(p("待辦：寄信"), ("add", "todo", "寄信"))
        self.assertEqual(p("记得关瓦斯"), ("add", "todo", "关瓦斯"))              # 簡體；存的是使用者原話
        self.assertEqual(p("记住我明天要去缴电费"), ("add", "todo", "我明天要去缴电费"))
        self.assertEqual(p("待辦事項"), ("list", "todo"))                        # 在問清單，不是要新增「事項」
        self.assertEqual(p("我有什麼待办事项"), ("list", "todo"))
        self.assertEqual(p("今天要做什麼"), ("list", "todo"))
        self.assertEqual(p("你記得什麼"), ("list", "note"))                      # 不是要記下「什麼」
        self.assertEqual(p("第2項完成了"), ("done", ("num", 2)))
        self.assertEqual(p("完成第二項"), ("done", ("num", 2)))
        self.assertEqual(p("買牛奶做完了"), ("done", ("key", "買牛奶")))
        self.assertEqual(p("刪掉第三項"), ("delete", ("num", 3)))
        for t in ("開藍牙", "打開待辦事項", "ai 幫我查待辦", "幫我查明天天氣", "進入Wi-Fi頁面", ""):
            self.assertIsNone(p(t), t)                                           # 一般指令不能被攔走

    def test_to_int(self):
        f = self.mod.to_int
        self.assertEqual([f(x) for x in ("3", "三", "十", "十二", "二十", "二十五", "兩")], [3, 3, 10, 12, 20, 25, 2])
        self.assertIsNone(f("很多"))

    def test_add_list_done_delete_flow_and_file_is_private(self):
        mod = self.mod
        path = os.path.join(tempfile.mkdtemp(), "memory.json")
        h = lambda t: mod.handle(t, path=path, today="2026-10-18", now="2026-10-18T09:30")
        self.assertIn("已加入待辦", h("記住我今天要買牛奶")[0])
        h("提醒我下午三點開會")
        h("記住我的車牌是ABC-1234")                                              # 筆記，不會出現在待辦清單
        spoken, full = h("我有什麼待辦事項")
        self.assertIn("2 項待辦", spoken)
        self.assertIn("1. 我今天要買牛奶（今天）", full)
        self.assertIn("2. 下午三點開會（今天）", full)
        self.assertNotIn("車牌", full)
        self.assertIn("車牌是ABC-1234", h("你記得什麼")[1])
        r = h("第1項完成了")[0]
        self.assertIn("已完成：我今天要買牛奶", r)
        self.assertIn("還剩 1 項", r)
        r = h("第1項完成了")[0]                                                  # 完成後編號會重排：原本第 2 項變第 1 項
        self.assertIn("已完成：下午三點開會", r)
        self.assertIn("還剩 0 項", r)
        self.assertIn("目前沒有待辦", h("有什麼待辦")[0])
        self.assertEqual(os.stat(path).st_mode & 0o777, 0o600)                   # 只有自己讀得到
        h("提醒我寄信")
        h("提醒我寄包裹")
        self.assertIn("2 項都包含", h("寄做完了")[0])                            # 關鍵字對到兩筆：要說第幾項
        self.assertIn("已刪除：寄信", h("刪掉第1項")[0])
        self.assertEqual([i["id"] for i in mod.load(path)["items"]], [1, 2, 3, 4, 5])  # id 不重複、刪除只是標記

    def test_does_not_intercept_ordinary_commands(self):
        mod = self.mod
        path = os.path.join(tempfile.mkdtemp(), "memory.json")
        self.assertIsNone(mod.handle("刪除這張照片", path=path))                  # 完全沒有待辦
        self.assertIsNone(mod.handle("完成這個表單", path=path))
        mod.handle("提醒我買牛奶", path=path)
        self.assertIsNone(mod.handle("刪除這張照片", path=path))                  # 有待辦，但關鍵字對不到任何一筆 -> 照原本流程
        self.assertIsNone(mod.handle("完成這個表單", path=path))
        self.assertIn("沒有第 5 項", mod.handle("刪掉第5項", path=path)[0])       # 數字超出範圍才報錯
        self.assertIsNone(mod.handle("開藍牙", path=path))

    def test_bad_or_missing_file_is_treated_as_empty(self):
        path = os.path.join(tempfile.mkdtemp(), "memory.json")
        open(path, "w").write("{壞掉")
        self.assertEqual(self.mod.load(path)["items"], [])
        self.assertIn("已加入待辦", self.mod.handle("提醒我買牛奶", path=path)[0])   # 壞檔案不會讓指令失敗


class Gestures(unittest.TestCase):
    """agent2 的手勢動作：座標換算、時間夾範圍、多指上限，以及 App 手勢不能用時單指退回 input 指令。"""

    @classmethod
    def setUpClass(cls):
        cls.ns = load_agent2()

    def test_swipe_diagonal_with_duration(self):
        fs, hold = self.ns["build_gesture"]({"action": "swipe", "x1": 800, "y1": 200, "x2": 200, "y2": 800, "ms": 9999}, 1000, 2000)
        self.assertEqual((fs, hold), ([{"x1": 800, "y1": 400, "x2": 200, "y2": 1600, "ms": 5000}], 0))   # 斜滑；ms 夾到 5000

    def test_longpress_drag_multitouch(self):
        b = self.ns["build_gesture"]
        fs, hold = b({"action": "longpress", "x": 500, "y": 500}, 1080, 2340)
        self.assertEqual((fs[0]["x1"], fs[0]["x2"], fs[0]["ms"], hold), (540, 540, 800, 0))
        fs, hold = b({"action": "drag", "x1": 100, "y1": 100, "x2": 900, "y2": 900}, 1000, 1000)
        self.assertEqual((fs[0]["ms"], hold), (500, 600))
        fs, hold = b({"action": "multitouch", "hold_ms": 100, "fingers": [{"x1": 450, "y1": 500, "x2": 100, "y2": 100},
                                                                         {"x1": 550, "y1": 500, "x2": 900, "y2": 900}] * 3}, 1000, 1000)
        self.assertEqual((len(fs), hold), (4, 100))                                                       # 最多 4 指
        self.assertIsNone(b({"action": "multitouch", "fingers": []}, 1000, 1000))
        self.assertIsNone(b({"action": "swipe", "x1": "a"}, 1000, 1000))                                  # 參數錯誤不丟例外
        self.assertEqual(b({"action": "swipe", "x1": -50, "y1": 2000, "x2": 0, "y2": 0}, 1000, 1000)[0][0]["x1"], 0)  # 超出範圍夾回

    def test_pick_app_from_ai_reply(self):
        f = self.ns["pick_app_from_reply"]
        t = {"blockblast": "com.block.juggle", "chrome": "com.android.chrome", "termux": "com.termux"}
        self.assertEqual(f('{"app":"Block Blast!"}', t), ("Block Blast!", "com.block.juggle"))     # 標點大小寫不影響
        self.assertEqual(f('好的 {"app": "Chrome"}', t)[1], "com.android.chrome")
        self.assertIsNone(f('{"app":null}', t))
        self.assertIsNone(f('{"app":"不存在的App"}', t))                                          # 不在清單 = 不開
        self.assertIsNone(f('{"app":"Termux"}', t))
        self.assertIsNone(f("看不懂", t))

    def test_drag_script_keeps_finger_down_until_end(self):
        ds = self.ns["drag_script"]
        self.ns["BG"]["on"] = False
        cmd, end = ds("start", (100, 200), (100, 500))
        self.assertTrue(cmd.startswith("input motionevent DOWN 100 200"))
        self.assertNotIn("UP", cmd)                                      # start 之後手指還按著
        self.assertIn("MOVE 100 500", cmd)
        self.assertEqual(end, (100, 500))
        cmd, end = ds("move", (100, 500), (140, 560))
        self.assertNotIn("DOWN", cmd)
        self.assertNotIn(" UP ", " " + cmd + " ")
        cmd, end = ds("end", (140, 560), None)
        self.assertEqual(cmd, "input motionevent UP 140 560")
        self.assertEqual(ds("cancel", (1, 2))[0], "input motionevent CANCEL 1 2")
        self.ns["BG"].update(on=True, id="7")
        try:
            self.assertIn("input -d 7 motionevent UP 1 2", ds("end", (1, 2))[0])                    # 背景模式指定螢幕
        finally:
            self.ns["BG"]["on"] = False

    def test_lessons_filter_parse_and_prompt(self):
        ns = self.ns
        self.assertTrue(ns["lesson_ok"]("Block Blast 拖方塊要用分段拖曳先看落點"))
        for bad in ("", "x" * 201, "驗證碼是 12345678", "我的密碼是 abc", "password is abc"):
            self.assertFalse(ns["lesson_ok"](bad))
        self.assertEqual(ns["parse_lesson"]('{"lesson":"先看落點再放開","app_specific":true}', "com.block.juggle"), ("先看落點再放開", "com.block.juggle"))
        self.assertEqual(ns["parse_lesson"]('{"lesson":"通用技巧","app_specific":false}', "com.x"), ("通用技巧", None))
        self.assertIsNone(ns["parse_lesson"]('{"lesson":null}', "com.x"))
        ns["LESSONS"][:] = ["先看落點再放開"]
        try:
            self.assertIn("先看落點再放開", ns["make_user"]("玩遊戲", []))
            self.assertIn("不能取代上面的安全規則", ns["make_user"]("玩遊戲", []))
            self.assertIn("可能不準", ns["make_user"]("玩遊戲", []))
        finally:
            ns["LESSONS"][:] = []
        self.assertNotIn("過去累積的經驗", ns["make_user"]("玩遊戲", []))

    def test_batch_filter(self):
        fb = self.ns["filter_batch"]
        acts = [{"action": "drag", "x1": 1, "y1": 2, "x2": 3, "y2": 4}, {"action": "ask", "why": "x"}, {"action": "done"},
                {"action": "search", "q": "x"}, {"action": "batch", "actions": []}, "垃圾", {"action": "tap", "x": 1, "y": 2}] + [{"action": "wait"}] * 9
        out = fb(acts)
        self.assertEqual([x["action"] for x in out][:2], ["drag", "tap"])         # ask/done/search/巢狀 batch 都被拿掉
        self.assertLessEqual(len(out), 6)
        self.assertEqual(fb(None), [])

    def test_same_trick_detection(self):
        sig, st = self.ns["action_sig"], self.ns["same_trick"]
        d = lambda x: sig({"action": "drag_start", "x": 197, "y": 764, "to_x": x, "to_y": 500})
        self.assertTrue(st([d(380), d(330), d(360)]))                   # 同一招只差一點點
        self.assertFalse(st([d(380), d(330)]))                           # 才兩次
        self.assertFalse(st([d(100), d(330), d(600)]))                   # 終點差很多 = 有在換方法
        self.assertFalse(st([d(380), sig({"action": "tap", "x": 1, "y": 2}), d(380)]))
        self.assertFalse(st([None, None, None]))
        self.assertTrue(st([d(380), None, d(330), None, d(360)]))     # start→cancel→start→cancel 來回（實測的情況）
        self.assertIsNone(sig({"action": "wait"}))

    def test_pre_open(self):
        po = self.ns["pre_open"]
        t = {"chrome": "com.android.chrome", "設定": "com.android.settings", "line": "jp.naver.line.android",
             "ig": "com.instagram.android", "termux": "com.termux", "playstore": "com.android.vending", "相機": "com.sec.android.app.camera"}
        self.assertEqual(po("進入設定裡的Wi-Fi頁面", t), ("設定", "com.android.settings"))
        self.assertEqual(po("用 Chrome 搜尋相機推薦", t), ("chrome", "com.android.chrome"))        # 取最早提到的
        self.assertIsNone(po("check my config deadline", t))                                           # 不能因為 config 含 "ig" 就開 IG
        self.assertIsNone(po("在 Termux 裡跑指令", t))
        self.assertIsNone(po("長按桌面空白處", t))
        self.assertIsNone(po("", t))
        self.assertEqual(po("去Block Blast經典模式玩", {"blockblast": "com.block.juggle"}), ("blockblast", "com.block.juggle"))   # 實測：任務沒打驚嘆號也要找得到
        self.assertEqual(po("玩 Block Blast! 經典模式", {"blockblast": "com.block.juggle"})[1], "com.block.juggle")

    def test_fallback_commands(self):
        ns = self.ns
        ns["BG"]["on"] = False
        f = lambda **k: {"x1": 1, "y1": 2, "x2": 3, "y2": 4, "ms": 500, **k}
        self.assertEqual(ns["gesture_fallback_cmd"]([f()], 0), "input swipe 1 2 3 4 500")
        self.assertEqual(ns["gesture_fallback_cmd"]([f()], 600), "input draganddrop 1 2 3 4 1100")
        self.assertEqual(ns["gesture_fallback_cmd"]([f(x2=1, y2=2)], 600), "input swipe 1 2 1 2 1100")      # 原地按住 = 長按
        self.assertIsNone(ns["gesture_fallback_cmd"]([f(), f()], 0))                                      # 多指沒有退路


class PowerKey(unittest.TestCase):
    """powerkey.py：從 getevent 輸出找出電源鍵裝置、解析按下／放開（內容取自小米 13 的實際輸出）。"""

    @classmethod
    def setUpClass(cls):
        import importlib.util
        spec = importlib.util.spec_from_file_location("powerkey_test", os.path.join(SCRIPTS, "powerkey.py"))
        cls.mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.mod)

    def test_find_power_devices_skips_bark_device(self):
        out = ("add device 1: /dev/input/event7\n  name:     \"fts\"\n  events:\n    KEY (0001): BTN_TOUCH\n"
               "add device 4: /dev/input/event6\n  name:     \"pmic_pwrkey_bark\"\n  events:\n    KEY (0001): KEY_POWER\n"
               "add device 7: /dev/input/event3\n  name:     \"pmic_pwrkey\"\n  events:\n    KEY (0001): KEY_POWER\n"
               "add device 8: /dev/input/event2\n  name:     \"uinput-goodix\"\n  events:\n    KEY (0001): KEY_POWER\n"
               "add device 10: /dev/input/event0\n  name:     \"gpio-keys\"\n  events:\n    KEY (0001): KEY_VOLUMEUP\n")
        self.assertEqual(self.mod.find_power_devices(out), ["/dev/input/event3"])           # 實測：bark、指紋（goodix）都登記了電源鍵，只有 pmic_pwrkey 是真的
        only_bark = "add device 4: /dev/input/event6\n  name:     \"x_bark\"\n  events:\n    KEY (0001): KEY_POWER\n"
        self.assertEqual(self.mod.find_power_devices(only_bark), ["/dev/input/event6"])      # 只剩它時還是用它
        self.assertEqual(self.mod.find_power_devices("add device 1: /dev/input/event7\n KEY (0001): BTN_TOUCH\n"), [])
        self.assertEqual(self.mod.find_power_devices(""), [])

    def test_fire_commands_close_power_menu(self):
        c = self.mod.fire_commands(True)
        self.assertIn("CLOSE_SYSTEM_DIALOGS", c[0])                       # 先關系統選單
        self.assertIn("com.phoneai.overlay/.AskActivity", c[1])             # 再開面板
        self.assertGreaterEqual(sum("CLOSE_SYSTEM_DIALOGS" in x for x in c), 3)   # 之後補送，抓晚出現的選單
        self.assertEqual(self.mod.fire_commands(), ["am start -n com.phoneai.overlay/.AskActivity"])   # 預設不補送關閉指令（會把剛開的面板也關掉）
        self.assertEqual(self.mod.fire_commands(False), self.mod.fire_commands())   # 不自動聽
        self.assertLessEqual(self.mod.HOLD, 0.45)                          # 要比系統電源選單（約 0.5 秒）早

    def test_parse_event_time_and_power_event_with_timestamp(self):
        line = "[  1234.567890] /dev/input/event3: EV_KEY       KEY_POWER            DOWN"
        self.assertAlmostEqual(self.mod.parse_event_time(line), 1234.56789)
        self.assertEqual(self.mod.parse_power_event(line), "DOWN")           # -lt 的輸出前面多了時間，照樣解析得出
        self.assertIsNone(self.mod.parse_event_time("/dev/input/event3: EV_KEY KEY_POWER DOWN"))

    def test_parse_power_event(self):
        p = self.mod.parse_power_event
        self.assertEqual(p("/dev/input/event3: EV_KEY       KEY_POWER            DOWN"), "DOWN")
        self.assertEqual(p("/dev/input/event3: EV_KEY       KEY_POWER            UP "), "UP")
        self.assertIsNone(p("/dev/input/event7: EV_KEY       BTN_TOUCH            DOWN"))
        self.assertIsNone(p("/dev/input/event3: EV_SYN       SYN_REPORT           00000000"))


class OverlayProtocol(unittest.TestCase):
    def test_roundtrip_and_confirm(self):
        srv = socket.socket()
        srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        srv.bind(("127.0.0.1", overlay.PORT))
        srv.listen()
        seen = []

        def run():
            c, _ = srv.accept()
            f = c.makefile("r", encoding="utf-8")
            c.sendall(b"ok\n" if f.readline().strip() == "tok" else b"denied\n")
            for line in f:
                j = json.loads(line)
                seen.append(j)
                rep = {"confirm": "yes" if "付款" in j.get("msg", "") else "no", "apps": json.dumps(["A\tp.a"]),
                       "ask_text": json.dumps({"text": " 依類型分 " if "怎麼整理" in j.get("question", "") else ""}) if "怎麼整理" in j.get("question", "") or "取消" in j.get("question", "") else "unavailable"}.get(j["cmd"], "ok")
                c.sendall(rep.encode() + b"\n")
        threading.Thread(target=run, daemon=True).start()
        old = overlay._su
        overlay._su = lambda cmd: "package:com.phoneai.overlay\n" if cmd.startswith("pm list") else ("tok\n" if cmd.startswith("cat") else "")
        try:
            o = overlay.Overlay()
            self.assertTrue(o.connect())
            o.ripple(10.7, 20)
            o.ripple(5, 6, 14)  # 背景模式：帶第二螢幕編號
            o.hide()
            o.show()  # 截圖前藏光暈、截完再顯示
            self.assertTrue(o.status("x", 1, 3))
            self.assertIs(o.confirm("要付款嗎"), True)
            self.assertIs(o.confirm("要刪除嗎"), False)
            self.assertEqual(o.sock.gettimeout(), 2)  # confirm 之後要把逾時還原
            self.assertEqual(o.ask_text("你想怎麼整理？"), "依類型分")          # 回答去掉前後空白
            self.assertEqual(o.ask_text("要取消嗎"), "")                       # 使用者沒回答 = 空字串
            self.assertIsNone(o.ask_text("面板沒跳出來"))                       # unavailable → None（呼叫端改用 termux-dialog）
            self.assertEqual(o.sock.gettimeout(), 2)                            # 問完要把逾時還原
            self.assertEqual(o.apps(), [("A", "p.a")])
            self.assertTrue(o.report("測試"))
            self.assertTrue(o.vd("123456", 1080, 1800))   # 背景虛擬螢幕的截圖編號與尺寸
            self.assertTrue(o.vd(None))
            self.assertTrue(o.vd("77", 1080, 1800, 41))     # 帶系統螢幕編號（使用者接手要用）
            self.assertTrue(o.takeover(True, "輸入密碼"))
            self.assertTrue(o.takeover(False))
            self.assertEqual(o.paused(), "no")
            self.assertEqual(seen[0], {"cmd": "ripple", "x": 10, "y": 20})
            self.assertIn({"cmd": "ripple", "x": 5, "y": 6, "display": 14}, seen)
            self.assertIn({"cmd": "show"}, seen)
            self.assertIn({"cmd": "vd", "sf": "123456", "w": 1080, "h": 1800}, seen)
            self.assertIn({"cmd": "vd", "sf": "", "w": 1080, "h": 2340}, seen)
            self.assertIn({"cmd": "vd", "sf": "77", "w": 1080, "h": 1800, "id": 41}, seen)
            self.assertIn({"cmd": "takeover", "on": True, "why": "輸入密碼"}, seen)
            self.assertIn({"cmd": "paused"}, seen)
        finally:
            overlay._su = old
            srv.close()

    def test_wrong_token_denied(self):
        srv = socket.socket()
        srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        srv.bind(("127.0.0.1", overlay.PORT))
        srv.listen()
        threading.Thread(target=lambda: (lambda c: (c[0].makefile().readline(), c[0].sendall(b"denied\n")))(srv.accept()), daemon=True).start()
        old = overlay._su
        overlay._su = lambda cmd: "package:com.phoneai.overlay\n" if cmd.startswith("pm list") else ("bad\n" if cmd.startswith("cat") else "")
        try:
            self.assertFalse(overlay.Overlay().connect())
        finally:
            overlay._su = old
            srv.close()


class Manifest(unittest.TestCase):
    NS = "{http://schemas.android.com/apk/res/android}"

    def test_exported_components(self):
        root = ET.parse(os.path.join(ROOT, "android-overlay/app/src/main/AndroidManifest.xml")).getroot()
        app = root.find("application")
        exported = {}
        for kind in ("activity", "service", "receiver", "provider"):
            for e in app.findall(kind):
                exported[e.get(self.NS + "name")] = (e.get(self.NS + "exported") == "true", e.get(self.NS + "permission"))
        # 預設不對外；對外的只有：桌面入口、系統才能綁的服務（有 BIND_* 權限）、語音辨識存根
        for name, (exp, perm) in exported.items():
            if exp:
                self.assertTrue(
                    name in (".MainActivity", ".NoRecognitionService") or (perm or "").startswith("android.permission.BIND_"),
                    "對外開放但沒有系統 BIND 權限保護：" + name)
        for n in (".OverlayService", ".AskActivity", ".ConfirmActivity"):
            self.assertFalse(exported[n][0], n + " 不該對外開放")

    def test_no_backup_and_no_query_all_packages(self):
        s = open(os.path.join(ROOT, "android-overlay/app/src/main/AndroidManifest.xml"), encoding="utf-8").read()
        self.assertIn('android:allowBackup="false"', s)
        self.assertIsNone(re.search(r'<uses-permission[^>]*QUERY_ALL_PACKAGES', s))  # 註解裡提到不算


class RepoHygiene(unittest.TestCase):
    def test_no_secrets_tracked(self):
        import subprocess
        files = subprocess.run(["git", "ls-files"], cwd=ROOT, capture_output=True, text=True).stdout.split()
        bad = []
        for f in files:
            if f.endswith((".keystore", ".png", ".apk")) and not f.endswith("debug.keystore"):
                bad.append(f)
            p = os.path.join(ROOT, f)
            if os.path.isfile(p) and not f.endswith(".keystore"):
                try:
                    txt = open(p, encoding="utf-8").read()
                except (UnicodeDecodeError, OSError):
                    continue
                if re.search(r"sk-ant-[A-Za-z0-9_-]{20,}", txt):
                    bad.append(f + "（疑似 API Key）")
        self.assertEqual(bad, [])



class Chat(unittest.TestCase):
    """chat.py：模型用工具操作記憶（tool use）、對話記憶；網路用假的 caller 取代。"""

    @classmethod
    def setUpClass(cls):
        import importlib
        sys.path.insert(0, SCRIPTS)
        cls.chat = importlib.import_module("chat")
        cls.mem = importlib.import_module("memory")

    def test_easter_egg_chinese(self):
        ee = self.chat.easter_egg
        for q in ("我可以用中文跟你對話嗎", "可以用中文跟你對話嗎？", "我能用中文和你聊天吗", "能不能用中文跟你說話", "用中文跟AI對話可以嗎"):
            self.assertEqual(ee(q), "可以。", q)
        for q in ("推薦我一個晚餐", "中文怎麼說 apple", "幫我翻譯成中文", "今天天氣如何", "你好"):
            self.assertIsNone(ee(q), q)
        d = tempfile.mkdtemp()
        called = []
        self.chat.PENDING_GAG[:] = []
        self.assertEqual(self.chat.ask("我可以用中文跟你對話嗎", os.path.join(d, "h.json"), d + "/m.json", lambda *a: called.append(1)), "可以。")
        self.assertEqual(called, [])                                          # 彩蛋不呼叫 API
        self.assertEqual(self.chat.PENDING_GAG, [self.chat.EGG_GAG])          # 排了第二張卡
        self.assertIn("帳號已被封禁", self.chat.EGG_GAG)
        self.assertNotIn("Claude", self.chat.EGG_GAG)                         # 內文用 PhoneAI 自己的說法，不照抄 Anthropic 官方通知句型
        self.chat.PENDING_GAG[:] = []

    def test_egg_reply_runs_without_routing(self):
        d = tempfile.mkdtemp()
        self.chat.PENDING_GAG[:] = []
        self.assertEqual(self.chat.egg_reply("我可以用中文跟你對話嗎", os.path.join(d, "h.json")), "可以。")
        self.assertEqual(self.chat.PENDING_GAG, [self.chat.EGG_GAG])
        self.assertIsNone(self.chat.egg_reply("推薦我一個晚餐", os.path.join(d, "h.json")))
        self.chat.PENDING_GAG[:] = []

    def test_history_expires_and_is_private(self):
        path = os.path.join(tempfile.mkdtemp(), "h.json")
        self.chat.save_history([{"role": "user", "content": "舊的", "t": 1000}, {"role": "user", "content": "新的", "t": 10 ** 6}], path)
        self.assertEqual(os.stat(path).st_mode & 0o777, 0o600)
        self.assertEqual([m["content"] for m in self.chat.load_history(path, now=10 ** 6 + 60)], ["新的"])

    def test_system_lists_items_with_ids(self):
        s = self.chat.build_system([{"id": 3, "kind": "todo", "text": "買牛奶"}, {"id": 5, "kind": "note", "text": "車牌ABC"}], "2026-10-18")
        self.assertIn("#3 買牛奶", s)
        self.assertIn("#5 車牌ABC", s)

    def test_read_only_shell(self):
        import intents
        for c in ["dumpsys battery", "getprop ro.build.version.release", "date", "cat /proc/meminfo", "pm list packages | grep chrome",
                  "cmd wifi status", "dumpsys battery | grep level", "df -h /data", "top -n 1 -b | head -20"]:
            self.assertIsNone(intents.check_read_only(c), c)
        for c in ["rm -rf /", "cat ~/.claude_key", "settings put system x 1", "dumpsys notification", "cat /sdcard/a.jpg",
                  "date; reboot", "pm clear com.x", "echo hi > /x", "ls $HOME", "pm list packages | sh", "cat /proc/self/environ",
                  "cmd wifi set-wifi-enabled disabled", "top", "ls -R /", "dumpsys clipboard", ""]:
            self.assertIsNotNone(intents.check_read_only(c), c)
        out, why = intents.run_read_only("date", lambda c: "x" * 2000)
        self.assertIsNone(why)
        self.assertLess(len(out), 1300)
        out, why = intents.run_read_only("rm -rf /", lambda c: self.fail("不該執行"))
        self.assertIsNotNone(why)

    def test_new_toggle_commands(self):
        sys.path.insert(0, SCRIPTS)
        src = open(os.path.join(SCRIPTS, "assistant.py"), encoding="utf-8").read()
        self.assertIn('"shc", "cmd connectivity airplane-mode enable"', src)   # 飛航模式要先確認
        self.assertIn("if kind == \"shc\"", src)
        for kw in ("開勿擾", "關自動旋轉", "開深色模式", "關省電", "開飛航"):
            self.assertIn(kw, src)

    def test_schedule(self):
        import datetime, schedule as sch, intents
        self.assertEqual(sch.parse_schedule("每天早上8點告訴我電量"), (8, 0, "告訴我電量"))
        self.assertEqual(sch.parse_schedule("每天晚上10點半查儲存空間"), (22, 30, "查儲存空間"))
        self.assertEqual(sch.parse_schedule("每天下午三點十五分現在電量多少"), (15, 15, "現在電量多少"))
        self.assertEqual(sch.parse_schedule("每天 7:05 電量多少")[:2], (7, 5))
        self.assertIsNone(sch.parse_schedule("每天25點查電量"))
        self.assertIsNone(sch.parse_schedule("明天八點查電量"))
        self.assertTrue(intents.is_quick_question("電量多少"))
        self.assertTrue(intents.is_quick_question("儲存空間還剩多少"))
        self.assertFalse(intents.is_quick_question("打開蝦皮買東西"))
        self.assertFalse(intents.is_quick_question("刪除所有照片"))
        # 只允許純查詢：會操作畫面的任務一律拒絕，也不會問使用者
        asked = []
        r = sch.handle("每天早上8點打開蝦皮搜尋手機", lambda m: asked.append(m) or True)
        self.assertIn("純查詢", r)
        self.assertEqual(asked, [])
        # due：時間到、今天沒跑過、遲到不超過 3 小時
        items = [{"id": 1, "h": 8, "m": 0, "task": "電量多少", "last": ""}]
        at = lambda hh, mm: datetime.datetime(2026, 10, 20, hh, mm)
        self.assertEqual(sch.due(items, at(7, 59)), [])
        self.assertEqual(len(sch.due(items, at(8, 10))), 1)
        self.assertEqual(sch.due(items, at(11, 30)), [])   # 遲到超過 3 小時，當天略過
        items[0]["last"] = "2026-10-20"
        self.assertEqual(sch.due(items, at(8, 10)), [])    # 今天跑過了
        with tempfile.TemporaryDirectory() as td:
            pth = os.path.join(td, "s.json")
            its = []
            sch.add(its, 8, 0, "電量多少")
            sch.add(its, 9, 0, "現在幾點")
            sch.save(its, pth)
            self.assertEqual([i["id"] for i in sch.load(pth)], [1, 2])

    def test_api_problem_messages(self):
        import intents
        credit = 'HTTP 400 {"type":"error","error":{"type":"invalid_request_error","message":"Your credit balance is too low to access the Anthropic API. Please go to Plans & Billing to upgrade or purchase credits."}}'
        self.assertIn("額度用完", intents.api_problem(credit))
        self.assertIn("Key", intents.api_problem('HTTP 401 {"error":{"type":"authentication_error","message":"invalid x-api-key"}}'))
        self.assertIn("限流", intents.api_problem("HTTP 429 rate_limit_error"))
        self.assertIn("忙碌", intents.api_problem('HTTP 529 {"error":{"type":"overloaded_error"}}'))
        self.assertIsNone(intents.api_problem("HTTP 400 cache_control not supported"))   # 一般 400（參數問題）不是這幾類
        self.assertIsNone(intents.api_problem("timed out"))
        intents.note_api_error(400, "Your credit balance is too low")
        self.assertIn("額度用完", intents.api_problem())     # 沒給參數 = 看最近一次記下的錯誤

    def test_format_tree(self):
        import intents
        items = [{"n": 0, "t": "Wi-Fi", "c": True, "x": 540, "y": 1170}, {"n": 1, "d": "返回", "c": True, "x": 90, "y": 234},
                 {"n": 2, "t": "已連線到 Home5G", "c": False, "x": 540, "y": 1400}, {"n": 3, "c": False, "x": 10, "y": 10}]
        t = intents.format_tree(items, 1080, 2340)
        self.assertIn("0*Wi-Fi (500,500)", t)
        self.assertIn("1*返回 (83,100)", t)
        self.assertIn("2 已連線到 Home5G (500,598)", t)
        self.assertNotIn("3 ", t)                       # 沒文字也不能點的元件略過
        self.assertIsNone(intents.format_tree(items[:2], 1080, 2340))   # 元件太少（讀不到）→ 退回截圖
        self.assertIsNone(intents.format_tree([{"n": 0, "t": "（除錯）看得到的視窗共 2 個"}], 1080, 2340))
        self.assertIsNone(intents.format_tree([], 1080, 2340))
        land = [{"n": i, "t": "x%d" % i, "c": True, "x": 2000, "y": 500} for i in range(4)]   # 橫向：x 超過直向寬度 → 自動對調
        self.assertIn("(855,463)", intents.format_tree(land, 1080, 2340))

    def test_tree_pick(self):
        import intents
        items = [{"n": 0, "t": "設定", "c": True, "x": 540, "y": 1170}, {"n": 5, "d": "返回", "c": True, "x": 90, "y": 234}]
        self.assertEqual(intents.tree_pick(items, 0, 1080, 2340), (500, 500, "設定"))
        self.assertEqual(intents.tree_pick(items, "5", 1080, 2340), (83, 100, "返回"))
        self.assertIsNone(intents.tree_pick(items, 9, 1080, 2340))     # 不在清單裡
        self.assertIsNone(intents.tree_pick(items, "x", 1080, 2340))   # 不是數字

    def test_quick_system_answers(self):
        import intents
        outs = {
            "df -k /data": "Filesystem 1K-blocks Used Available Use% Mounted on\n/dev/block/dm-9 244140625 195312500 48828125 80% /data\n",
            "cat /proc/meminfo": "MemTotal: 12582912 kB\nMemFree: 100000 kB\nMemAvailable: 3145728 kB\n",
            "getprop ro.product.manufacturer": "samsung\n", "getprop ro.product.model": "SM-S936B\n",
            "settings get system screen_brightness": "128\n",
            "cmd wifi status": 'Wifi is enabled\nWifiInfo: SSID: "Home5G", BSSID: aa\n',
            "cmd media_session volume --get --stream 3": "volume is 7 in range [0..15]\n"}
        f = lambda c: outs.get(c, "")
        self.assertIn("剩 46.6 GB", intents.quick_answer("儲存空間還剩多少", f))
        self.assertIn("可用 3.0 GB", intents.quick_answer("記憶體還有多少", f))
        self.assertIn("SM-S936B", intents.quick_answer("這支手機型號是什麼", f))
        self.assertIn("50%", intents.quick_answer("現在亮度多少", f))
        self.assertIn("Home5G", intents.quick_answer("現在連哪個wifi", f))
        self.assertIn("7／15", intents.quick_answer("現在音量多少", f))
        self.assertIsNone(intents.quick_answer("開wifi", f))          # 指令，不是提問
        self.assertIsNone(intents.quick_answer("儲存空間還剩多少", lambda c: ""))   # 解析不出來 → None（交給 AI）
        self.assertIsNone(intents.quick_answer("調亮度到最大", f))

    def test_quick_answer(self):
        import intents
        fake = lambda c: "Current Battery Service state:\n  AC powered: false\n  USB powered: true\n  status: 2\n  level: 63\n  temperature: 312\n"
        r = intents.quick_answer("現在電量多少", fake)
        self.assertIn("63%", r)
        self.assertIn("充電中", r)
        self.assertIn("31.2", r)
        self.assertIsNotNone(intents.quick_answer("现在几点", fake))
        self.assertIn("民國", intents.quick_answer("現在西元幾年", fake))
        self.assertIn("西元", intents.quick_answer("今年是哪一年", fake))
        self.assertIn("Android 版本是 16", intents.quick_answer("Android版本是多少", lambda c: "16\n" if "getprop" in c else ""))
        self.assertIn("Android 版本是 16", intents.quick_answer("安卓幾版", lambda c: "16\n"))
        self.assertIsNone(intents.quick_answer("ai 幫我看電量", fake))
        self.assertIsNone(intents.quick_answer("打開設定然後把電池優化全部關掉再回來告訴我", fake))
        self.assertIsNone(intents.quick_answer("開藍牙", fake))

    def test_run_tool_safety(self):
        m = self.mem
        path = os.path.join(tempfile.mkdtemp(), "m.json")
        self.assertIn("已新增 #1", m.run_tool("memory_add", {"kind": "todo", "text": "買牛奶"}, path))
        for t in "ABCDE":
            m.run_tool("memory_add", {"kind": "note", "text": "筆記" + t}, path)
        self.assertIn("錯誤", m.run_tool("memory_add", {"kind": "evil", "text": "x"}, path))
        self.assertIn("錯誤", m.run_tool("rm_rf", {}, path))
        self.assertIn("已更新 #1", m.run_tool("memory_update", {"id": 1, "done": True}, path))
        self.assertIn("錯誤", m.run_tool("memory_update", {"id": 99, "text": "x"}, path))
        self.assertIn("沒有刪除", m.run_tool("memory_delete", {"ids": [2, 3, 4, 5]}, path))                # 超過 3 項沒有確認 = 不刪
        self.assertIn("沒有刪除", m.run_tool("memory_delete", {"ids": [2, 3, 4, 5]}, path, lambda msg: False))
        self.assertIn("已刪除 1 項", m.run_tool("memory_delete", {"ids": [2]}, path))                    # 少量直接刪
        self.assertIn("沒有刪除", m.run_tool("memory_clear", {"scope": "all"}, path, lambda msg: False))
        self.assertIn("已刪除全部", m.run_tool("memory_clear", {"scope": "note"}, path, lambda msg: True))
        self.assertEqual(m.note_lines(path), [])

    def test_ask_runs_tool_loop_and_remembers_conversation(self):
        open(self.chat.HOME + "/.claude_key", "w").write("k" * 40)
        d = tempfile.mkdtemp()
        hp, mp = d + "/h.json", d + "/m.json"
        calls = []

        def fake(messages, system, k):
            calls.append(messages)
            if len(calls) == 1:  # 第一輪：模型呼叫工具
                return {"content": [{"type": "text", "text": ""}, {"type": "tool_use", "id": "t1", "name": "memory_add",
                                                                    "input": {"kind": "note", "text": "使用者叫小明"}}]}
            if len(calls) == 2:  # 第二輪：看到工具結果後回答
                assert messages[-1]["content"][0]["type"] == "tool_result" and "已新增" in messages[-1]["content"][0]["content"]
                return {"content": [{"type": "text", "text": "好的，已記住你叫小明。"}]}
            return {"content": [{"type": "text", "text": "你叫小明。"}]}
        self.assertEqual(self.chat.ask("我叫小明", hp, mp, fake), "好的，已記住你叫小明。")
        self.assertEqual(self.mem.note_lines(mp), ["使用者叫小明"])
        self.assertEqual(self.chat.ask("我是誰", hp, mp, fake), "你叫小明。")
        self.assertEqual(len(calls[2]), 3)                                  # 上一輪的問與答 + 這一句
        self.assertIsNone(self.chat.ask("hi", hp, mp, lambda *a: None))     # 連不上不更新紀錄
        self.assertEqual(len(self.chat.load_history(hp)), 4)


if __name__ == "__main__":
    unittest.main()
