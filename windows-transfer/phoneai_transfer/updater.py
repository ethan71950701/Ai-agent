"""一鍵更新：去 GitHub 看最近的 Release（跟手機 APK 同一個），挑編號最大且帶電腦版檔案的，有新版就下載，替換自己後重新開啟。"""
import json
import os
import re
import ssl
import subprocess
import sys
import urllib.request

from . import version


def parse_release(data):
    """從 GitHub 的 release JSON 取出 (版本號, 下載網址)；版本號是標籤 build-<編號>-<提交> 的編號。
    找不到編號或電腦版檔案就回 (None, None)。"""
    m = re.match(r"build-(\d+)(?:-|$)", str(data.get("tag_name", "")))
    if not m:
        return None, None
    for a in data.get("assets", []):
        if a.get("name") == version.ASSET_NAME:
            return int(m.group(1)), a.get("browser_download_url")
    return None, None


def pick_latest(releases):
    """從最近幾個 Release 挑編號最大、而且有電腦版檔案的（跟手機版一樣自己挑，不靠 /releases/latest；
    有些版本可能沒有 exe，例如電腦版那次打包失敗）。"""
    best = (None, None)
    for rel in releases:
        if rel.get("draft") or rel.get("prerelease"):
            continue
        build, url = parse_release(rel)
        if build is not None and (best[0] is None or build > best[0]):
            best = (build, url)
    return best


def is_newer(remote_build, local_build=None):
    local = version.BUILD if local_build is None else local_build
    return remote_build is not None and remote_build > local


def _ssl_context():
    """打包成 .exe 後 Python 找不到系統憑證，會連不上 HTTPS；改用 certifi 內建的憑證檔。"""
    try:
        import certifi
        return ssl.create_default_context(cafile=certifi.where())
    except ImportError:
        return None


def fetch_latest():
    url = f"https://api.github.com/repos/{version.REPO}/releases?per_page=15"
    req = urllib.request.Request(url, headers={"Accept": "application/vnd.github+json", "User-Agent": "PhoneAI-Transfer"})
    with urllib.request.urlopen(req, timeout=15, context=_ssl_context()) as r:
        return pick_latest(json.loads(r.read().decode("utf-8")))


def download(url, dest):
    req = urllib.request.Request(url, headers={"User-Agent": "PhoneAI-Transfer"})
    with urllib.request.urlopen(req, timeout=120, context=_ssl_context()) as r, open(dest, "wb") as f:
        while True:
            chunk = r.read(64 * 1024)
            if not chunk:
                break
            f.write(chunk)


def apply_and_restart(new_exe):
    """替換正在執行的 exe（Windows 允許先改名執行中的檔案），然後開新的程式。"""
    current = sys.executable
    old = current + ".old"
    try:
        os.remove(old)
    except OSError:
        pass
    os.replace(current, old)
    os.replace(new_exe, current)
    subprocess.Popen([current], close_fds=True)


def cleanup_old():
    """啟動時清掉上次更新留下的 .old 檔。"""
    if getattr(sys, "frozen", False):
        try:
            os.remove(sys.executable + ".old")
        except OSError:
            pass
