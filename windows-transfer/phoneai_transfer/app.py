"""電腦版視窗（tkinter）：開啟互傳開關、配對、已配對裝置、最近記錄、傳輸進度與停止。"""
import os
import socket
import sys
import threading
import tkinter as tk
from tkinter import filedialog, messagebox, simpledialog, ttk

try:   # 拖曳檔案進視窗需要 tkinterdnd2；沒有的話仍可以用按鈕傳檔案
    from tkinterdnd2 import DND_FILES, TkinterDnD
    HAS_DND = True
except ImportError:
    HAS_DND = False

try:   # 縮到右下角的系統匣圖示需要 pystray 和 Pillow；沒有的話，關閉視窗就是結束
    import pystray
    from PIL import Image, ImageDraw
    HAS_TRAY = True
except ImportError:
    HAS_TRAY = False

try:   # Windows 11 風格的外觀主題；沒有的話用系統預設樣式
    import sv_ttk
    HAS_SVTTK = True
except ImportError:
    HAS_SVTTK = False

from . import net, startup, updater, version
from .service import Service, Stopped, _size


class RemoteBrowser:
    """直接瀏覽已配對裝置的資料夾：雙擊資料夾進去，雙擊檔案就接收到電腦的「下載\\PhoneAI」。"""

    def __init__(self, app, peer):
        self.app = app
        self.peer = peer
        self.client = None
        self.path = ""
        self.rows = []
        self.win = tk.Toplevel(app.root)
        self.win.title(f"瀏覽「{peer['name']}」")
        self.win.geometry("600x460")
        top = ttk.Frame(self.win)
        top.pack(fill="x", padx=12, pady=8)
        ttk.Button(top, text="上一層", command=lambda: self.load(parent_of(self.path))).pack(side="left")
        self.where = ttk.Label(top, text="")
        self.where.pack(side="left", padx=8)
        self.lb = tk.Listbox(self.win)
        self.lb.pack(fill="both", expand=True, padx=12)
        self.lb.bind("<Double-Button-1>", lambda e: self._open())
        self.status = ttk.Label(self.win, text="連線中…")
        self.status.pack(anchor="w", padx=12, pady=6)
        threading.Thread(target=self._connect, daemon=True).start()

    def _connect(self):
        try:
            self.client = self.app.svc.client_for(self.peer["id"])
        except Exception as e:
            self.app.root.after(0, lambda e=e: self.status.config(text=f"連不上：{e}"))
            return
        self.load("")

    def load(self, target):
        if self.client is None:
            return
        self.status.config(text="讀取中…")

        def work():
            try:
                rows = net.parse_listing(self.client.list(target))
            except Exception as e:
                self.app.root.after(0, lambda e=e: self.status.config(text=f"讀取失敗：{e}"))
                return
            self.app.root.after(0, lambda: self._show(target, rows))
        threading.Thread(target=work, daemon=True).start()

    def _show(self, target, rows):
        self.path = target
        self.rows = rows
        self.where.config(text="位置：" + (target or "（磁碟）"))
        self.lb.delete(0, "end")
        for t, size, p in rows:
            name = p.rstrip("/").rsplit("/", 1)[-1] or p
            self.lb.insert("end", ("📁 " + name) if t == "D" else f"📄 {name}   {_size(size)}")
        self.status.config(text="" if rows else "這個資料夾是空的")

    def _open(self):
        sel = self.lb.curselection()
        if not sel or sel[0] >= len(self.rows):
            return
        t, size, p = self.rows[sel[0]]
        if t == "D":
            self.load(p)
        elif messagebox.askyesno("接收", f"接收「{p.rsplit('/', 1)[-1]}」到電腦的下載\\PhoneAI？", parent=self.win):
            self.win.destroy()
            self.app._run(lambda: self.app.svc.fetch_file(self.peer["id"], p, self.app.svc.store.inbox()))


def parent_of(p):
    """上一層：「C:/Users」→「C:/」→「」（磁碟列表）；手機的「Download/PhoneAI」→「Download」→「」。"""
    if not p:
        return ""
    s = p.rstrip("/")
    if "/" not in s:
        return ""
    par = s.rsplit("/", 1)[0]
    if par == "":
        return "/"
    if len(par) == 2 and par[1] == ":":
        return par + "/"
    return par


class App:
    def __init__(self):
        updater.cleanup_old()
        self.svc = Service()
        self.root = TkinterDnD.Tk() if HAS_DND else tk.Tk()
        self.root.title("PhoneAI 互傳檔案")
        self.root.geometry("540x780")
        self.root.minsize(500, 680)
        self.enabled = tk.BooleanVar(value=self.svc.store.enabled())
        self._build()
        if self.enabled.get():
            self._set_enabled(True)
        self.refresh()
        self.root.after(200, self._pump)
        self.root.protocol("WM_DELETE_WINDOW", self._close)
        self.tray = None
        if HAS_TRAY and os.name == "nt":
            self._setup_tray()
        if "--tray" in sys.argv and self.tray:   # 開機自動啟動時：直接縮到右下角
            self.root.withdraw()

    # ---- 畫面 ----
    def _style(self):
        """Windows 11 風格的主題（sv_ttk）；沒有安裝時用系統預設樣式。"""
        self.accent = "TButton"
        self.switch = "TCheckbutton"
        font = ("Microsoft JhengHei UI", 10)
        if HAS_SVTTK:
            sv_ttk.set_theme("dark")
            self.accent = "Accent.TButton"
            self.switch = "Switch.TCheckbutton"
            # tk 原生的清單和文字框不會跟著主題變色，要自己設
            self.root.option_add("*Listbox.background", "#2b2b2b")
            self.root.option_add("*Listbox.foreground", "#f2f2f2")
            self.root.option_add("*Listbox.selectBackground", "#0067c0")
            self.root.option_add("*Listbox.relief", "flat")
            self.root.option_add("*Listbox.highlightThickness", 0)
            self.root.option_add("*Text.background", "#2b2b2b")
            self.root.option_add("*Text.foreground", "#e6e6e6")
            self.root.option_add("*Text.relief", "flat")
        # 只改預設字型的字體（Windows 的微軟正黑體），不用 option_add("*Font")，那會蓋掉標題等元件自己的字型
        import tkinter.font as tkfont
        for name in ("TkDefaultFont", "TkTextFont", "TkMenuFont", "TkHeadingFont", "TkCaptionFont"):
            try:
                tkfont.nametofont(name).configure(family=font[0])
            except tk.TclError:
                pass
        st = ttk.Style()
        st.configure("Title.TLabel", font=("Microsoft JhengHei UI", 18, "bold"))
        st.configure("Hint.TLabel", foreground="#9a9a9a")
        st.configure("Big.TButton", font=("Microsoft JhengHei UI", 11))

    def _build(self):
        self._style()
        outer = ttk.Frame(self.root, padding=16)
        outer.pack(fill="both", expand=True)
        if HAS_DND:
            self.root.drop_target_register(DND_FILES)
            self.root.dnd_bind("<<Drop>>", self._on_drop)

        # 上方：標題、開關、狀態（一直顯示）
        head = ttk.Frame(outer)
        head.pack(fill="x")
        ttk.Label(head, text="PhoneAI 互傳檔案", style="Title.TLabel").pack(side="left")
        self.state_label = ttk.Label(head, text="", style="Hint.TLabel")
        self.state_label.pack(side="right")
        row1 = ttk.Frame(outer)
        row1.pack(fill="x", pady=(10, 0))
        ttk.Checkbutton(row1, text="開啟互傳檔案", variable=self.enabled, style=self.switch,
                        command=lambda: self._set_enabled(self.enabled.get())).pack(side="left")
        ttk.Button(row1, text="檢查更新", command=self._check_update).pack(side="right")
        ttk.Button(row1, text="改名稱", command=self._rename).pack(side="right", padx=6)
        self.me_label = ttk.Label(outer, text="", style="Hint.TLabel", justify="left", wraplength=500)
        self.me_label.pack(anchor="w", pady=(8, 0))
        if startup.supported():
            self.autostart = tk.BooleanVar(value=startup.is_enabled())
            ttk.Checkbutton(outer, text="開機自動啟動（縮在右下角）", variable=self.autostart,
                            command=lambda: startup.set_enabled(self.autostart.get())).pack(anchor="w", pady=(6, 0))

        # 中間：分頁（裝置／找檔案／記錄），不再全部擠在一頁
        tabs = ttk.Notebook(outer)
        tabs.pack(fill="both", expand=True, pady=(14, 8))

        dev_tab = ttk.Frame(tabs, padding=14)
        tabs.add(dev_tab, text="  裝置  ")
        ttk.Button(dev_tab, text="配對另一台", style=self.accent, command=self._pair_out).pack(fill="x")
        ttk.Label(dev_tab, text="兩台都打開互傳，在任一台按「配對另一台」，核對 6 位數確認碼即可。",
                  style="Hint.TLabel", wraplength=480).pack(anchor="w", pady=(6, 12))
        ttk.Label(dev_tab, text="已配對的裝置").pack(anchor="w")
        self.dev_list = tk.Listbox(dev_tab, height=6, activestyle="none")
        self.dev_list.pack(fill="both", expand=True, pady=(6, 8), ipady=4)
        self.dev_list.bind("<Double-Button-1>", lambda e: self._remove())
        btns = ttk.Frame(dev_tab)
        btns.pack(fill="x")
        ttk.Button(btns, text="傳送過去", style=self.accent, command=self._send).pack(side="left", expand=True, fill="x")
        ttk.Button(btns, text="接收", command=self._fetch).pack(side="left", expand=True, fill="x", padx=(8, 0))
        hint = "雙擊裝置名字可以移除"
        if HAS_DND:
            hint += "；也可以把檔案直接拖進這個視窗，再選要傳給哪一台"
        ttk.Label(dev_tab, text=hint, style="Hint.TLabel", wraplength=480).pack(anchor="w", pady=(8, 0))

        find_tab = ttk.Frame(tabs, padding=14)
        tabs.add(find_tab, text="  找檔案  ")
        ttk.Label(find_tab, text="手動搜尋檔名（不用 AI）", style="Hint.TLabel").pack(anchor="w")
        srow = ttk.Frame(find_tab)
        srow.pack(fill="x", pady=(6, 8))
        self.query = ttk.Entry(srow)
        self.query.pack(side="left", fill="x", expand=True)
        self.query.bind("<Return>", lambda e: self._search_local())
        ttk.Button(srow, text="搜本機", style=self.accent, command=self._search_local).pack(side="left", padx=(8, 4))
        ttk.Button(srow, text="搜選的裝置", command=self._search_device).pack(side="left")
        self.results = tk.Listbox(find_tab, height=8, activestyle="none")
        self.results.pack(fill="both", expand=True, ipady=4)
        self.results.bind("<Double-Button-1>", lambda e: self._do_result())
        ttk.Button(find_tab, text="傳給選的裝置／取回", command=self._do_result).pack(fill="x", pady=(8, 0))
        self.result_rows = []
        self.result_source = None   # None = 本機（傳給已配對裝置）；裝置 dict = 從那台取回

        log_tab = ttk.Frame(tabs, padding=14)
        tabs.add(log_tab, text="  記錄  ")
        self.log = tk.Text(log_tab, height=10, wrap="word", state="disabled")
        self.log.pack(fill="both", expand=True)

        # 下方：傳輸進度（一直顯示）
        prog = ttk.Frame(outer)
        prog.pack(fill="x")
        self.prog_title = ttk.Label(prog, text="沒有進行中的傳輸", wraplength=500)
        self.prog_title.pack(anchor="w")
        self.bar = ttk.Progressbar(prog, maximum=100)
        self.bar.pack(fill="x", pady=(6, 4))
        bottom = ttk.Frame(prog)
        bottom.pack(fill="x")
        self.prog_text = ttk.Label(bottom, text="", style="Hint.TLabel")
        self.prog_text.pack(side="left")
        self.stop_btn = ttk.Button(bottom, text="停止", command=self._stop, state="disabled")
        self.stop_btn.pack(side="right")

    def refresh(self):
        ip = ", ".join(net._local_ips()) or "?"
        self.me_label.config(text=f"名稱：{self.svc.store.my_name()}\n收到的檔案：{self.svc.store.inbox()}")
        if self.svc.running:
            self.state_label.config(text=f"運作中 · {ip}")
        else:
            self.state_label.config(text="沒有在運作" + (f"（{self.svc.last_error}）" if self.svc.last_error else ""))
        self.dev_list.delete(0, "end")
        peers = self.svc.store.peers()
        for p in peers:
            self.dev_list.insert("end", "   " + p["name"])
        if not peers:   # 還沒有配對：清單不要空白一大塊，給個說明
            self.dev_list.insert("end", "   還沒有配對的裝置，按上面的「配對另一台」開始")
            self.dev_list.itemconfig(0, foreground="#8a8a8a")
        self.log.config(state="normal")
        self.log.delete("1.0", "end")
        self.log.insert("end", "\n".join(self.svc.store.recent(15)))
        self.log.config(state="disabled")

    # ---- 拖曳檔案進來：選裝置後傳出去 ----
    def _on_drop(self, event):
        paths = [p for p in self.root.tk.splitlist(event.data) if os.path.isfile(p)]
        if not paths:
            return event.action
        peers = self.svc.store.peers()
        if not peers:
            messagebox.showinfo("傳檔案", "還沒有配對過的裝置。", parent=self.root)
            return event.action
        win = tk.Toplevel(self.root)
        win.title(f"傳給哪一台（{len(paths)} 個檔案）")
        win.geometry("360x260")
        lb = tk.Listbox(win)
        lb.pack(fill="both", expand=True, padx=12, pady=12)
        for p in peers:
            lb.insert("end", p["name"])
        lb.selection_set(0)

        def send():
            sel = lb.curselection()
            if not sel:
                return
            peer = peers[sel[0]]
            win.destroy()
            self._run(lambda: [self.svc.send_file(peer["id"], p) for p in paths])

        ttk.Button(win, text="傳送", command=send).pack(pady=8)
        return event.action

    # ---- 手動搜尋 ----
    def _search_local(self):
        self._search(None)

    def _search_device(self):
        p = self._selected()
        if p:
            self._search(p)

    def _search(self, peer):
        q = self.query.get().strip()
        if not q:
            return
        self._status(f"搜尋中：{q}")

        def work():
            try:
                rows = self.svc.search_local(q) if peer is None else self.svc.search_peer(peer["id"], q)
            except Exception as e:
                self.root.after(0, lambda e=e: messagebox.showerror("搜尋失敗", str(e), parent=self.root))
                return
            self.root.after(0, lambda: self._show_results(rows, peer))
        threading.Thread(target=work, daemon=True).start()

    def _show_results(self, rows, peer):
        self._status("")
        self.result_rows = rows
        self.result_source = peer
        self.results.delete(0, "end")
        for t, size, path in rows:
            if t == "F":
                self.results.insert("end", f"{path}   {_size(size)}")
            else:
                self.results.insert("end", f"[資料夾] {path}")
        if not rows:
            self.results.insert("end", "沒有找到")

    def _do_result(self):
        sel = self.results.curselection()
        if not sel or sel[0] >= len(self.result_rows):
            return
        t, size, path = self.result_rows[sel[0]]
        if t != "F":
            return
        if self.result_source is None:
            peer = self._selected()
            if not peer:
                return
            self._run(lambda: self.svc.send_file(peer["id"], path))
        else:
            peer = self.result_source
            self._run(lambda: self.svc.fetch_file(peer["id"], path, self.svc.store.inbox()))

    def _set_enabled(self, on):
        self.svc.store.set_enabled(on)
        if on:
            self.svc.start()
        else:
            self.svc.stop()
        self.refresh()

    # ---- 按鈕 ----
    def _rename(self):
        name = simpledialog.askstring("改名稱", "這台電腦的名稱：", initialvalue=self.svc.store.my_name(), parent=self.root)
        if name:
            self.svc.store.set_name(name)
            self.refresh()

    # ---- 一鍵更新 ----
    def _check_update(self):
        self._status("檢查更新中…")

        def work():
            try:
                build, url = updater.fetch_latest()
            except Exception as e:
                self.root.after(0, lambda e=e: (self._status(f"檢查更新失敗：{e}"), messagebox.showerror("檢查更新", f"連不上 GitHub：{e}", parent=self.root)))
                return
            self.root.after(0, lambda: self._offer_update(build, url))
        threading.Thread(target=work, daemon=True).start()

    def _offer_update(self, build, url):
        self._status("")
        if build is None:
            messagebox.showwarning("檢查更新", "更新來源找不到電腦版的檔案（檔名對不上）。\n請到 GitHub 的 windows-latest 頁面，手動下載 PhoneAI.exe 換一次，之後就能一鍵更新。", parent=self.root)
            return
        if not updater.is_newer(build):
            messagebox.showinfo("檢查更新", f"已經是最新版（{version.BUILD}）。", parent=self.root)
            return
        if not getattr(sys, "frozen", False):
            messagebox.showinfo("檢查更新", f"有新版（{build}），但目前是開發模式，請自己下載。", parent=self.root)
            return
        if not messagebox.askyesno("檢查更新", f"有新版（{build}），要現在更新嗎？\n更新後會自動重新開啟。", parent=self.root):
            return
        self._status("下載更新中…")

        def work():
            try:
                new_exe = sys.executable + ".new"
                updater.download(url, new_exe)
                self.root.after(0, lambda: self._apply_update(new_exe))
            except Exception as e:
                self.root.after(0, lambda e=e: (self._status(""), messagebox.showerror("更新失敗", str(e), parent=self.root)))
        threading.Thread(target=work, daemon=True).start()

    def _apply_update(self, new_exe):
        try:
            self.svc.stop()
            updater.apply_and_restart(new_exe)
        except Exception as e:
            messagebox.showerror("更新失敗", str(e), parent=self.root)
            return
        if self.tray:
            self.tray.stop()
        self.root.destroy()

    def _selected(self):
        sel = self.dev_list.curselection()
        peers = self.svc.store.peers()
        if not sel or sel[0] >= len(peers):
            messagebox.showinfo("互傳檔案", "請先在上面選一台裝置。", parent=self.root)
            return None
        return peers[sel[0]]

    def _pair_out(self):
        if not self.svc.running:
            messagebox.showinfo("互傳檔案", "請先打開「開啟互傳檔案」。", parent=self.root)
            return
        self.svc.open_pairing(120)
        self._status("搜尋裝置中…")
        threading.Thread(target=self._scan_and_pick, daemon=True).start()

    def _scan_and_pick(self):
        found = list(self.svc.scan().values())
        self.root.after(0, lambda: self._pick_device(found))

    def _pick_device(self, found):
        if not found:
            ip = simpledialog.askstring("找不到裝置", "沒有找到。請對方也打開互傳並停在配對頁。\n如果知道對方的 IP，可以直接輸入：",
                                        parent=self.root)
            if ip:
                self._start_pair(ip.strip(), net.PORT)
            return
        win = tk.Toplevel(self.root)
        win.title("選擇要配對的裝置")
        win.geometry("360x260")
        lb = tk.Listbox(win)
        lb.pack(fill="both", expand=True, padx=12, pady=12)
        for f in found:
            lb.insert("end", f"{f.name}（{f.ip}）")

        def choose():
            sel = lb.curselection()
            if sel:
                f = found[sel[0]]
                win.destroy()
                self._start_pair(f.ip, f.port)

        ttk.Button(win, text="配對", command=choose).pack(pady=8)

    def _start_pair(self, ip, port):
        def work():
            try:
                offer = self.svc.pair_outgoing(ip, port)
            except Exception as e:
                self.root.after(0, lambda e=e: messagebox.showerror("配對失敗", str(e), parent=self.root))
                return
            self.root.after(0, lambda: self._confirm_out(offer, ip, port))
        threading.Thread(target=work, daemon=True).start()

    def _confirm_out(self, offer, ip, port):
        ok = messagebox.askyesno("確認碼", f"確認碼：{offer.sas}\n\n對方（{offer.peer_name}）的畫面上是同一組數字嗎？\n相符按「是」，然後請對方也按相符。",
                                 parent=self.root)
        if not ok:
            return
        self._status("等對方按相符…")

        def wait():
            done = self.svc.confirm_outgoing(offer, ip, port)
            self.root.after(0, lambda: self._pair_done(done, offer.peer_name))
        threading.Thread(target=wait, daemon=True).start()

    def _pair_done(self, ok, name):
        self._status("")
        if ok:
            messagebox.showinfo("配對完成", f"已配對：{name}", parent=self.root)
        else:
            messagebox.showwarning("配對", "對方沒有接受或逾時，沒有配對。", parent=self.root)
        self.refresh()

    def _remove(self):
        p = self._selected()
        if p and messagebox.askyesno("移除", f"移除「{p['name']}」？移除後不能再取檔案，要重新配對。", parent=self.root):
            self.svc.store.remove_peer(p["id"])
            self.refresh()

    def _send(self):
        p = self._selected()
        if not p:
            return
        path = filedialog.askopenfilename(parent=self.root, title="選擇要傳過去的檔案")
        if path:
            self._run(lambda: self.svc.send_file(p["id"], path))

    def _fetch(self):
        p = self._selected()
        if not p:
            return
        RemoteBrowser(self, p)

    def _run(self, job):
        self.stop_btn.config(state="normal")

        def work():
            try:
                job()
            except Stopped:
                self.root.after(0, lambda: self._status("已停止，未完成的檔案已刪除"))
            except Exception as e:
                self.root.after(0, lambda e=e: messagebox.showerror("傳檔案失敗", str(e), parent=self.root))
            finally:
                self.root.after(0, self.refresh)
        threading.Thread(target=work, daemon=True).start()

    def _stop(self):
        self.svc.request_stop()

    def _status(self, text):
        self.prog_title.config(text=text or "沒有進行中的傳輸")

    # ---- 事件 ----
    def _pump(self):
        try:
            while True:
                kind, data = self.svc.events.get_nowait()
                if kind == "progress":
                    title, name, done, total = data
                    self.prog_title.config(text=title)
                    pct = int(done * 100 / total) if total else 0
                    self.bar.config(value=pct)
                    self.prog_text.config(text=f"{name}　{pct}%　{_size(done)} / {_size(total)}")
                    self.stop_btn.config(state="normal")
                elif kind == "progress_end":
                    self.bar.config(value=0)
                    self.prog_text.config(text="")
                    self.prog_title.config(text="沒有進行中的傳輸")
                    self.stop_btn.config(state="disabled")
                    self.refresh()
                elif kind == "log":
                    self.refresh()
                elif kind == "notice":
                    self.prog_text.config(text=data)
                    self.root.bell()
                elif kind == "error":
                    messagebox.showerror("互傳檔案", data, parent=self.root)
                elif kind == "pair_request":
                    self._pair_request(data)
        except Exception:
            pass   # 佇列空了
        self.root.after(200, self._pump)

    def _pair_request(self, p):
        ok = messagebox.askyesno("手機想配對", f"「{p['name']}」想配對。\n確認碼：{p['sas']}\n\n手機上是同一組數字嗎？相符按「是」。",
                                 parent=self.root)
        self.svc.finish_pair(ok)
        self.refresh()

    def _setup_tray(self):
        """右下角的系統匣圖示：關閉視窗只是縮起來，服務繼續跑；要結束請用圖示選單的「結束」。"""
        img = Image.new("RGB", (64, 64), (30, 120, 220))
        d = ImageDraw.Draw(img)
        d.ellipse((12, 12, 52, 52), fill=(255, 255, 255))
        d.ellipse((24, 24, 40, 40), fill=(30, 120, 220))
        menu = pystray.Menu(
            pystray.MenuItem("開啟視窗", lambda: self.root.after(0, self._show), default=True),
            pystray.MenuItem("結束", lambda: self.root.after(0, self._quit)))
        self.tray = pystray.Icon("phoneai", img, "PhoneAI 互傳檔案", menu)
        self.tray.run_detached()

    def _show(self):
        self.root.deiconify()
        self.root.lift()

    def _quit(self):
        self.svc.stop()
        if self.tray:
            self.tray.stop()
        self.root.destroy()

    def _close(self):
        if self.tray:
            self.root.withdraw()   # 縮到系統匣，服務繼續運作
        else:
            self._quit()

    def run(self):
        self.root.mainloop()


def main():
    App().run()


if __name__ == "__main__":
    main()
