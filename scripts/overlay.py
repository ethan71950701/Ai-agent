"""overlay.py — 跟 PhoneAI Overlay App（android-overlay/）溝通：畫點擊漣漪、更新「AI 正在操作」通知。

給 agent2.py 用（import overlay），也可以單獨跑來測試：
    python ~/overlay.py            # 在螢幕上畫幾個漣漪 + 一條滑動軌跡，通知顯示 1/3~3/3
App 沒裝、沒權限、連不上 -> 所有函式都安靜地什麼都不做（ok=False），agent2 會退回 termux-notification。

做法：App 的前景服務在 127.0.0.1:47195 收指令（一行一個 JSON）。第一行要送 token，
token 在 App 的私有資料夾，只有 root 讀得到（避免其他 App 亂送）。連線一斷 App 就自動收掉通知。
狀態（2026-10-17）：雲端編譯通過，**尚未在手機實測**。
"""
import json, os, socket, subprocess, sys, time

PKG = "com.phoneai.overlay"
PORT = 47195


def _su(cmd):
    return subprocess.run(["su", "-c", cmd], capture_output=True, text=True).stdout


class Overlay:
    def __init__(self):
        self.sock = None
        self.ok = False
        self.denied = False
        self.diag = ""

    def connect(self):
        """App 有裝就啟動它的服務並連線；回傳是否成功。"""
        if os.environ.get("AGENT_OVERLAY", "1") == "0":
            return False
        token_cmd = "cat /data/data/%s/files/token 2>/dev/null" % PKG
        # 快路徑：App 在使用者按送出時就把服務叫起來了，這裡直接讀 token 連線（只需要一次 root 呼叫）
        self.denied = False
        token = _su(token_cmd).strip()
        if token and self._open(token):
            return True
        if self.denied:
            return False  # 伺服器明確說 token 不對：重試沒用
        # 慢路徑：服務還沒起來（例如從 Termux 直接跑 agent2）→ 確認有裝、啟動服務、等它起來再連
        if ("package:" + PKG) not in _su("pm list packages " + PKG).split():
            return False
        _su("am start-foreground-service -n %s/.OverlayService >/dev/null 2>&1" % PKG)
        for _ in range(20):  # 最多等約 2 秒讓服務起來
            token = _su(token_cmd).strip()
            if token and self._open(token):
                return True
            if self.denied:
                return False
            time.sleep(0.1)
        return False

    def _open(self, token):
        """用 token 連線並握手；成功回 True。連不上或被拒回 False。"""
        try:
            s = socket.create_connection(("127.0.0.1", PORT), timeout=1)
        except OSError:
            return False
        try:
            s.settimeout(2)
            self.rf = s.makefile("r", encoding="utf-8")
            s.sendall((token + "\n").encode())
            reply = self.rf.readline().strip()
            if reply == "ok":
                self.sock, self.ok = s, True
                return True
            self.denied = reply == "denied"
        except OSError:
            pass
        s.close()
        return False

    def _send(self, **cmd):
        if not self.ok:
            return None
        try:
            self.sock.sendall((json.dumps(cmd, ensure_ascii=False) + "\n").encode())
            return self.rf.readline().strip()
        except OSError:
            self.ok = False  # 連線斷了：之後全部不做，讓 agent2 退回原本的通知
            return None

    def ripple(self, x, y, display=0):
        """display = 系統螢幕編號；0 = 主螢幕，背景模式傳第二螢幕的編號。"""
        extra = {"display": int(display)} if display else {}  # 主螢幕不送 display，跟舊版 App 完全相容
        self._send(cmd="ripple", x=int(x), y=int(y), **extra)

    def hold(self, x, y, ms, display=0):
        """長按動畫：一圈慢慢往外擴，放手（ms 毫秒後）剛好到最外圈。舊版 App 不認得會回 unknown，沒影響。"""
        extra = {"display": int(display)} if display else {}
        self._send(cmd="hold", x=int(x), y=int(y), ms=int(ms), **extra)

    def swipe(self, x1, y1, x2, y2, display=0, delay=0):
        extra = {"display": int(display)} if display else {}
        if delay:
            extra["delay"] = int(delay)
        self._send(cmd="swipe", x1=int(x1), y1=int(y1), x2=int(x2), y2=int(y2), **extra)

    def gesture(self, fingers, hold=0, display=0):
        """多指／長按／拖曳（像素座標）。fingers = [{x1,y1,x2,y2,ms}]，hold = 先按住幾毫秒再移動。
        回傳 "ok" / "nocap"（無障礙服務沒有手勢權限）/ "fail" / None（連不上或舊版 App 不認得）。"""
        if not self.ok:
            return None
        total = hold + max([f.get("ms", 300) for f in fingers] or [300])
        try:
            self.sock.settimeout(total / 1000.0 + 12)  # 手勢做完 App 才回覆，平常的 2 秒不夠
            extra = {"display": int(display)} if display else {}
            r = self._send(cmd="gesture", fingers=fingers, hold=int(hold), **extra)
            self.sock.settimeout(2)
        except OSError:
            self.ok = False
            return None
        return r if r in ("ok", "nocap", "nosvc", "fail") else None

    def vd(self, sf, w=1080, h=2340, display_id=0):
        """告訴 App 背景虛擬螢幕的截圖編號（SurfaceFlinger id）、像素尺寸（預覽視窗用它算比例）、
        系統螢幕編號（使用者接手時 App 用它對背景螢幕送 input -d），App 就能在 Now Bar 提供「點一下顯示預覽」；
        傳 None/空字串 = 螢幕消失。"""
        extra = {"id": int(display_id)} if display_id else {}
        return self._send(cmd="vd", sf=str(sf or ""), w=int(w), h=int(h), **extra) == "ok"

    def takeover(self, on, why=""):
        """請 App 打開預覽並進入（或結束）「使用者接手」模式（AI 要使用者親自輸入密碼/驗證碼時用）。"""
        return self._send(cmd="takeover", on=bool(on), why=str(why)[:60]) == "ok"

    def paused(self):
        """使用者是不是正在接手背景螢幕。回傳 "yes"（接手中，AI 要等）、"done"（剛結束接手，AI 應該重新看畫面）、"no"。"""
        r = self._send(cmd="paused")
        return r if r in ("yes", "done") else "no"

    def hide(self):
        """截圖前清掉還在播的特效，避免 Claude 看到自己的漣漪。"""
        self._send(cmd="hide")

    def show(self):
        """截完圖後讓螢幕邊緣光暈回來（截圖前 hide 會藏起它）。"""
        self._send(cmd="show")

    def status(self, text, step=0, max_steps=0, title=None):
        kw = {"cmd": "status", "text": text, "step": int(step), "max": int(max_steps)}
        if title:
            kw["title"] = title
        return self._send(**kw) == "ok"

    def confirm(self, msg, timeout=130):
        """請 App 跳原生確認框。回傳 True/False；App 不可用或框沒跳出來回傳 None（呼叫端改用 termux-dialog）。"""
        if not self.ok:
            return None
        try:
            self.sock.settimeout(timeout)  # 使用者可能想一下，平常的 2 秒不夠
            r = self._send(cmd="confirm", msg=str(msg))
            self.sock.settimeout(2)
        except OSError:
            self.ok = False
            return None
        return {"yes": True, "no": False}.get(r)

    def ask_text(self, question, timeout=320):
        """請 App 跳出助理面板問使用者一個問題（語音或打字都行）。回傳回答文字；使用者沒回答（取消、逾時）回 ""；
        面板沒跳出來或連線失敗回 None（呼叫端改用 termux-dialog）。"""
        if not self.ok:
            return None
        try:
            self.sock.settimeout(timeout)   # 使用者可能要想、要說，平常的 2 秒不夠
            r = self._send(cmd="ask_text", question=str(question))
            self.sock.settimeout(2)
        except OSError:
            self.ok = False
            return None
        try:
            return str(json.loads(r).get("text", "")).strip()
        except (TypeError, ValueError, AttributeError):
            return None

    def tree(self, display=0, max_items=200):
        """某個螢幕上看得到的元件清單（文字、描述、能不能點、中心座標）；App 沒開無障礙讀取或連線失敗回 None。"""
        r = self._send(cmd="tree", display=display, max=max_items)
        try:
            items = json.loads(r)
        except (TypeError, ValueError):
            return None
        return items if isinstance(items, list) else None

    def apps(self):
        """向 App 要桌面 App 清單，回傳 [(顯示名稱, 套件名稱), ...]；失敗回傳 None。"""
        r = self._send(cmd="apps")
        try:
            return [tuple(x.split("\t", 1)) for x in json.loads(r) if "\t" in x]
        except (TypeError, ValueError):
            return None

    def close(self):
        """只關連線（不送 end）。App 發現沒有人連著就會自己收掉通知與懸浮層。"""
        try:
            self.sock.close()
        except (OSError, AttributeError):
            pass
        self.ok = False

    def reply(self, text, title="AI 助手"):
        """請 App 在螢幕底部浮出一張文字卡片（助理的文字回答，不會自己消失）。"""
        return self._send(cmd="reply", title=str(title), text=str(text)) == "ok"

    def speak(self, text):
        """請 App 用系統語音合成念出來。回傳 "ok"（念了或已排隊）、"off"（使用者在 App 首頁關了朗讀）、None（連不上 App）。"""
        return self._send(cmd="speak", text=str(text))

    def sheet_close(self):
        """助理面板送出後會停著等回答；這句話要操作手機或開 App 時，請 App 把面板收起來（面板已顯示回答卡片就不動）。"""
        return self._send(cmd="sheet_close") == "ok"

    def report(self, text):
        """任務結束時回報一行摘要給 App（主畫面會列出最近幾筆）。"""
        return self._send(cmd="report", text=str(text)) == "ok"

    def check(self):
        """回傳 App 的診斷字串（Android 版本、通知有沒有被升級成 Live Update、懸浮層在不在）。"""
        self.diag = self._send(cmd="diag") or ""
        return self.diag

    def end(self):
        self._send(cmd="end")
        try:
            self.sock.close()
        except (OSError, AttributeError):
            pass
        self.ok = False


def _demo():
    ov = Overlay()
    if not ov.connect():
        print("連不上 PhoneAI Overlay：App 沒裝、服務沒起來，或 root 沒授權給 Termux。")
        return 1
    r = _su("wm size").split()[-1]  # 例如 1440x3120（若有 Override size 會取最後一個）
    w, h = (int(v) for v in r.split("x"))
    print("已連線，螢幕 %dx%d" % (w, h))
    steps = [("點擊「畫面中央」", lambda: ov.ripple(w / 2, h / 2)),
             ("滑動", lambda: ov.swipe(w * 0.8, h * 0.6, w * 0.2, h * 0.6)),
             ("點擊「右上」", lambda: ov.ripple(w * 0.75, h * 0.2))]
    for i, (text, fx) in enumerate(steps, 1):
        ov.status("第 %d/3 步 · %s" % (i, text), i, 3, title="AI 正在操作手機（測試）")
        fx()
        time.sleep(2)
    print("診斷:", ov.check())
    ov.end()
    print("完成（通知應該已消失）")
    return 0


def _tree_demo(display):
    """實驗：印出某個螢幕的元件清單（只印在這個終端機，不會送給 Claude）。display 0 = 主螢幕；背景螢幕用 vd 的 id。"""
    ov = Overlay()
    if not ov.connect():
        print("連不上 PhoneAI Overlay。")
        return 1
    items = ov.tree(display)
    if items is None:
        print("拿不到元件：App 的無障礙服務可能沒開（或權限還沒生效：到 設定→無障礙 把 PhoneAI Overlay 關掉再打開）。")
        return 2
    print("螢幕 %d：%d 個元件" % (display, len(items)))
    for it in items[:60]:
        tag = "可點" if it.get("c") else "  "
        if "x" not in it:  # 除錯資訊（沒有座標）：原樣印出
            print("    " + it.get("t", ""))
            continue
        print("%3d %s (%4d,%4d) %s | %s %s" % (it["n"], tag, it["x"], it["y"], it.get("t", ""), it.get("d", ""), it.get("id", "")))
    ov.end()
    return 0


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "tree":
        sys.exit(_tree_demo(int(sys.argv[2]) if len(sys.argv) > 2 else 0))
    sys.exit(_demo())
