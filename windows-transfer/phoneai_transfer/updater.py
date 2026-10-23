"""一鍵更新：去 GitHub 看 windows-latest 這個 Release，有新版就下載，替換自己後重新開啟。"""
import json
import os
import ssl
import subprocess
import sys
import urllib.request

from . import version


def parse_release(data):
    """從 GitHub 的 release JSON 取出 (版本號, 下載網址)；找不到就回 (None, None)。"""
    try:
        build = int(str(data.get("name", "")).rsplit("-", 1)[-1])
    except ValueError:
        return None, None
    for a in data.get("assets", []):
        if a.get("name") == version.ASSET_NAME:
            return build, a.get("browser_download_url")
    return None, None


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
    url = f"https://api.github.com/repos/{version.REPO}/releases/tags/{version.RELEASE_TAG}"
    req = urllib.request.Request(url, headers={"Accept": "application/vnd.github+json", "User-Agent": "PhoneAI-Transfer"})
    with urllib.request.urlopen(req, timeout=15, context=_ssl_context()) as r:
        return parse_release(json.loads(r.read().decode("utf-8")))


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
