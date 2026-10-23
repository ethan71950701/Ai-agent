"""電腦版視窗（tkinter）：開啟互傳開關、配對、已配對裝置、最近記錄、傳輸進度與停止。"""
import os
import socket
import threading
import tkinter as tk
from tkinter import filedialog, messagebox, simpledialog, ttk

from . import net
from .service import Service, Stopped, _size


class App:
    def __init__(self):
        self.svc = Service()
        self.root = tk.Tk()
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
        ttk.Checkbutton(top, text="開啟互傳檔案", variable=self.enabled, command=lambda: self._set_enabled(self.enabled.get())).pack(side="left")
        self.state_label = ttk.Label(top, text="")
        self.state_label.pack(side="right")

        me = ttk.LabelFrame(self.root, text="這台電腦")
        me.pack(fill="x", **pad)
        self.me_label = ttk.Label(me, text="", justify="left")
        self.me_label.pack(side="left", padx=8, pady=6)
        ttk.Button(me, text="改名稱", command=self._rename).pack(side="right", padx=8)

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
        ttk.Button(row, text="傳檔案過去", command=self._send).pack(side="left", expand=True, fill="x")
        ttk.Button(row, text="從它取檔案", command=self._fetch).pack(side="left", expand=True, fill="x", padx=4)
        ttk.Button(row, text="移除", command=self._remove).pack(side="left", expand=True, fill="x")

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
        keyword = simpledialog.askstring("取檔案", f"要從「{p['name']}」找的檔名關鍵字：", parent=self.root)
        if not keyword:
            return

        def find_then_pick():
            try:
                c = self.svc.client_for(p["id"])
                rows = [l.split("\t", 3) for l in c.find(keyword).splitlines() if l.count("\t") >= 3]
            except Exception as e:
                self.root.after(0, lambda: messagebox.showerror("找不到", str(e), parent=self.root))
                return
            self.root.after(0, lambda: self._pick_remote(p, rows))
        threading.Thread(target=find_then_pick, daemon=True).start()

    def _pick_remote(self, p, rows):
        if not rows:
            messagebox.showinfo("取檔案", "沒有找到。", parent=self.root)
            return
        win = tk.Toplevel(self.root)
        win.title("選擇要取回的檔案")
        win.geometry("560x320")
        lb = tk.Listbox(win)
        lb.pack(fill="both", expand=True, padx=12, pady=12)
        for r in rows:
            lb.insert("end", ("[資料夾] " if r[0] == "D" else "") + r[3] + f"  {_size(int(r[1]))}")

        def choose():
            sel = lb.curselection()
            if sel and rows[sel[0]][0] == "F":
                path = rows[sel[0]][3]
                win.destroy()
                self._run(lambda: self.svc.fetch_file(p["id"], path, self.svc.store.inbox()))

        ttk.Button(win, text="取回", command=choose).pack(pady=8)

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
