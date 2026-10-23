"""電腦版視窗（tkinter）：開啟互傳開關、配對、已配對裝置、最近記錄、傳輸進度與停止。"""
import os
import socket
import threading
import tkinter as tk
from tkinter import filedialog, messagebox, simpledialog, ttk

try:   # 拖曳檔案進視窗需要 tkinterdnd2；沒有的話仍可以用按鈕傳檔案
    from tkinterdnd2 import DND_FILES, TkinterDnD
    HAS_DND = True
except ImportError:
    HAS_DND = False

from . import net, updater, version
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
            self.app.root.after(0, lambda: self.status.config(text=f"連不上：{e}"))
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
                self.app.root.after(0, lambda: self.status.config(text=f"讀取失敗：{e}"))
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
        self.root.geometry("460x720")
        self.root.minsize(420, 600)
        self.enabled = tk.BooleanVar(value=self.svc.store.enabled())
        self._build()
        if self.enabled.get():
            self._set_enabled(True)
        self.refresh()
        self.root.after(200, self._pump)
        self.root.protocol("WM_DELETE_WINDOW", self._close)

    # ---- 畫面 ----
    def _build(self):
        pad = {"padx": 12, "pady": 6}
        top = ttk.Frame(self.root)
        top.pack(fill="x", **pad)
        if HAS_DND:
            self.root.drop_target_register(DND_FILES)
            self.root.dnd_bind("<<Drop>>", self._on_drop)
            ttk.Label(self.root, text="可以把檔案直接拖進這個視窗，再選要傳給哪一台").pack(anchor="w", padx=12)
        ttk.Checkbutton(top, text="開啟互傳檔案", variable=self.enabled, command=lambda: self._set_enabled(self.enabled.get())).pack(side="left")
        self.state_label = ttk.Label(top, text="")
        self.state_label.pack(side="right")

        me = ttk.LabelFrame(self.root, text="這台電腦")
        me.pack(fill="x", **pad)
        self.me_label = ttk.Label(me, text="", justify="left")
        self.me_label.pack(side="left", padx=8, pady=6)
        ttk.Button(me, text="改名稱", command=self._rename).pack(side="right", padx=8)
        ttk.Button(me, text="檢查更新", command=self._check_update).pack(side="right", padx=4)

        pair = ttk.LabelFrame(self.root, text="配對")
        pair.pack(fill="x", **pad)
        ttk.Label(pair, text="兩台都打開互傳，在任一台按「配對另一台」，核對 6 位數確認碼即可。", wraplength=400).pack(anchor="w", padx=8, pady=4)
        ttk.Button(pair, text="配對另一台", command=self._pair_out).pack(fill="x", padx=8, pady=6)

        devs = ttk.LabelFrame(self.root, text="已配對的裝置")
        devs.pack(fill="x", **pad)
        self.dev_list = tk.Listbox(devs, height=5)
        self.dev_list.pack(fill="x", padx=8, pady=6)
        row = ttk.Frame(devs)
        row.pack(fill="x", padx=8, pady=(0, 8))
        ttk.Button(row, text="傳送過去", command=self._send).pack(side="left", expand=True, fill="x")
        ttk.Button(row, text="接收", command=self._fetch).pack(side="left", expand=True, fill="x", padx=4)
        ttk.Label(devs, text="雙擊裝置名字可以移除").pack(anchor="w", padx=8, pady=(0, 6))
        self.dev_list.bind("<Double-Button-1>", lambda e: self._remove())

        prog = ttk.LabelFrame(self.root, text="傳輸")
        prog.pack(fill="x", **pad)
        self.prog_title = ttk.Label(prog, text="沒有進行中的傳輸", wraplength=400)
        self.prog_title.pack(anchor="w", padx=8, pady=4)
        self.bar = ttk.Progressbar(prog, maximum=100)
        self.bar.pack(fill="x", padx=8)
        self.prog_text = ttk.Label(prog, text="")
        self.prog_text.pack(anchor="w", padx=8, pady=4)
        self.stop_btn = ttk.Button(prog, text="停止", command=self._stop, state="disabled")
        self.stop_btn.pack(anchor="e", padx=8, pady=(0, 8))

        search = ttk.LabelFrame(self.root, text="找檔案（手動搜尋，不用 AI）")
        search.pack(fill="x", **pad)
        srow = ttk.Frame(search)
        srow.pack(fill="x", padx=8, pady=6)
        self.query = ttk.Entry(srow)
        self.query.pack(side="left", fill="x", expand=True)
        self.query.bind("<Return>", lambda e: self._search_local())
        ttk.Button(srow, text="搜本機", command=self._search_local).pack(side="left", padx=4)
        ttk.Button(srow, text="搜選的裝置", command=self._search_device).pack(side="left")
        self.results = tk.Listbox(search, height=6)
        self.results.pack(fill="x", padx=8)
        self.results.bind("<Double-Button-1>", lambda e: self._do_result())
        ttk.Button(search, text="傳給選的裝置／取回", command=self._do_result).pack(fill="x", padx=8, pady=6)
        self.result_rows = []
        self.result_source = None   # None = 本機（傳給已配對裝置）；裝置 dict = 從那台取回

        logf = ttk.LabelFrame(self.root, text="最近記錄")
        logf.pack(fill="both", expand=True, **pad)
        self.log = tk.Text(logf, height=8, wrap="word", state="disabled")
        self.log.pack(fill="both", expand=True, padx=8, pady=6)

    def refresh(self):
        ip = ", ".join(net._local_ips()) or "?"
        self.me_label.config(text=f"名稱：{self.svc.store.my_name()}\n收到的檔案：{self.svc.store.inbox()}")
        if self.svc.running:
            self.state_label.config(text=f"運作中 · {ip}")
        else:
            self.state_label.config(text="沒有在運作" + (f"（{self.svc.last_error}）" if self.svc.last_error else ""))
        self.dev_list.delete(0, "end")
        for p in self.svc.store.peers():
            self.dev_list.insert("end", p["name"])
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
                self.root.after(0, lambda: messagebox.showerror("搜尋失敗", str(e), parent=self.root))
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
                self.root.after(0, lambda: (self._status(f"檢查更新失敗：{e}"), messagebox.showerror("檢查更新", f"連不上 GitHub：{e}", parent=self.root)))
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
                self.root.after(0, lambda: (self._status(""), messagebox.showerror("更新失敗", str(e), parent=self.root)))
        threading.Thread(target=work, daemon=True).start()

    def _apply_update(self, new_exe):
        try:
            self.svc.stop()
            updater.apply_and_restart(new_exe)
        except Exception as e:
            messagebox.showerror("更新失敗", str(e), parent=self.root)
            return
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
                self.root.after(0, lambda: messagebox.showerror("配對失敗", str(e), parent=self.root))
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
                self.root.after(0, lambda: messagebox.showerror("傳檔案失敗", str(e), parent=self.root))
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

    def _close(self):
        self.svc.stop()
        self.root.destroy()

    def run(self):
        self.root.mainloop()


def main():
    App().run()


if __name__ == "__main__":
    main()
