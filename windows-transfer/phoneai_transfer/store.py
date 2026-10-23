"""本機設定與資料：這台電腦的 id 與名稱、已配對裝置與長期金鑰、最近記錄。
放在 %APPDATA%\\PhoneAI互傳（金鑰只存在這裡，不進 repo、不印出來）。"""
import json
import os
import threading
import time
import uuid

from . import crypto


def data_dir():
    base = os.environ.get("APPDATA") or os.path.expanduser("~")
    d = os.path.join(base, "PhoneAI互傳")
    os.makedirs(d, exist_ok=True)
    return d


class Store:
    def __init__(self, folder=None, inbox=None):
        self.folder = folder or data_dir()
        self._inbox = inbox
        os.makedirs(self.folder, exist_ok=True)
        self._lock = threading.Lock()
        self._path = os.path.join(self.folder, "config.json")
        self._cfg = self._load()
        if "id" not in self._cfg:
            self._cfg["id"] = uuid.uuid4().hex[:16]
            self._save()
        self._log_path = os.path.join(self.folder, "transfer.log")

    def _load(self):
        try:
            with open(self._path, encoding="utf-8") as f:
                return json.load(f)
        except (OSError, ValueError):
            return {"peers": []}

    def _save(self):
        tmp = self._path + ".tmp"
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(self._cfg, f, ensure_ascii=False, indent=1)
        os.replace(tmp, self._path)

    # ---- 基本設定 ----
    def my_id(self):
        return self._cfg["id"]

    def my_name(self):
        return self._cfg.get("name") or os.environ.get("COMPUTERNAME", "PC")

    def set_name(self, name):
        with self._lock:
            self._cfg["name"] = name.strip().replace("|", " ").replace("\n", " ")[:40] or self.my_name()
            self._save()

    def enabled(self):
        return bool(self._cfg.get("enabled", False))

    def set_enabled(self, on):
        with self._lock:
            self._cfg["enabled"] = bool(on)
            self._save()

    def inbox(self):
        """收到的檔案放在「下載\\PhoneAI」（跟手機一樣）。"""
        return self._inbox or os.path.join(os.path.expanduser("~"), "Downloads", "PhoneAI")

    # ---- 已配對的裝置 ----
    def peers(self):
        return [dict(p) for p in self._cfg.get("peers", [])]

    def add_peer(self, peer_id, name, key):
        with self._lock:
            peers = [p for p in self._cfg.get("peers", []) if p["id"] != peer_id]
            peers.append({"id": peer_id, "name": name, "key": crypto.hexs(key)})
            self._cfg["peers"] = peers
            self._save()

    def remove_peer(self, peer_id):
        with self._lock:
            self._cfg["peers"] = [p for p in self._cfg.get("peers", []) if p["id"] != peer_id]
            self._save()

    def peer(self, peer_id):
        for p in self._cfg.get("peers", []):
            if p["id"] == peer_id:
                return p
        return None

    def key_of(self, peer_id):
        p = self.peer(peer_id)
        return crypto.unhex(p["key"]) if p else None

    def update_peer_name(self, peer_id, name):
        with self._lock:
            changed = False
            for p in self._cfg.get("peers", []):
                if p["id"] == peer_id and p["name"] != name:
                    p["name"] = name
                    changed = True
            if changed:
                self._save()

    # ---- 記錄 ----
    def log(self, text):
        line = time.strftime("%m/%d %H:%M") + "  " + text
        with self._lock:
            try:
                with open(self._log_path, encoding="utf-8") as f:
                    lines = f.read().splitlines()
            except OSError:
                lines = []
            lines.append(line)
            with open(self._log_path, "w", encoding="utf-8") as f:
                f.write("\n".join(lines[-300:]) + "\n")

    def recent(self, n=15):
        try:
            with open(self._log_path, encoding="utf-8") as f:
                return f.read().splitlines()[-n:][::-1]
        except OSError:
            return []
