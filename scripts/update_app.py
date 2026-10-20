"""update_app.py — 一行更新：拉最新程式 + 下載「最新的 GitHub Release」的 APK 並安裝。

用法:
    python ~/update_app.py            更新腳本，並在 App 不是最新時下載、安裝最新 APK
    python ~/update_app.py --check    只檢查，不下載、不安裝
    python ~/update_app.py --init-repo  把「ZIP 解壓縮來的資料夾」換成 Git 版（只做一次）

公開的 repo 不需要任何權杖。只有 repo 是私人的時候才需要權杖（放在 ~/.gh_token，唯讀；
產生方式見 docs/PUBLISH_CHECKLIST.md 或 App 的「功能 → 維護 → GitHub 權杖」）。
權杖只放在 ~/.gh_token（chmod 600），不進 repo、不印出、只送給 api.github.com。
下載檔案時 GitHub 會轉址到另一個網站，轉址後的請求**不帶**權杖。

repo 位置：預設是 DEFAULT_REPO；fork 的人用環境變數 PHONEAI_REPO=owner/repo，
或 ~/.phoneai_repo 檔案（install.sh 會依 git remote 自動寫）。
"""
import base64, json, os, re, shutil, subprocess, sys, urllib.error, urllib.request

DEFAULT_REPO = "ethan71950701/phone-ai-agent"
HOME = os.path.expanduser("~")


def repo_slug():
    """owner/repo：環境變數 > ~/.phoneai_repo > 預設。"""
    v = os.environ.get("PHONEAI_REPO", "").strip()
    if not v:
        try:
            v = open(HOME + "/.phoneai_repo").read().strip()
        except OSError:
            v = ""
    return v if v.count("/") == 1 and " " not in v else DEFAULT_REPO


OWNER, REPO = repo_slug().split("/")
API = "https://api.github.com/repos/%s/%s" % (OWNER, REPO)
TOKEN_FILE = HOME + "/.gh_token"
REPO_DIR = HOME + "/phone-ai-agent"


SETUP_HELP = """  （公開的 repo 不需要權杖，這一步只有私人 repo 才要做）
  1. 手機瀏覽器開 https://github.com/settings/personal-access-tokens/new
  2. Token name 隨便填；Expiration 選 90 天；Repository access 選「Only select repositories」→ 只選這個 repo
  3. Repository permissions：Contents = Read-only（Metadata 會自動是 Read）
  4. Generate token，複製（只會顯示一次），貼到 App 的「功能 → 維護 → GitHub 權杖」
  權杖不要貼給任何人（包括 AI 助手）。"""


# ---------- 純函式（有離線測試） ----------

def pick_release(release_json):
    """從「最新 Release」的回應挑出 APK：回傳 {sha, tag, name, url(瀏覽器下載), api_url(權杖用), size}；沒有 APK 回 None。
    commit 從 Release 說明的「commit: <40 碼>」讀；讀不到就用 tag 最後面的 7 碼。"""
    r = release_json or {}
    asset = None
    for a in r.get("assets", []):
        if a.get("name", "").endswith(".apk") and a.get("state", "uploaded") == "uploaded":
            asset = a
            break
    if not asset:
        return None
    m = re.search(r"commit:\s*([0-9a-f]{7,40})", r.get("body") or "")
    sha = m.group(1) if m else (r.get("tag_name", "").rsplit("-", 1)[-1] if "-" in r.get("tag_name", "") else "")
    return {"sha": sha, "tag": r.get("tag_name", ""), "name": asset["name"], "url": asset.get("browser_download_url", ""),
            "api_url": asset.get("url", ""), "size": asset.get("size", 0)}


def app_is_current(installed_sha7, run_sha):
    """App 診斷裡的 build=<commit 前 7 碼> 是不是就是這次編譯的 commit。"""
    return bool(installed_sha7) and installed_sha7 != "dev" and bool(run_sha) and run_sha.startswith(installed_sha7)


def explain_http_error(code):
    return {
        401: "權杖無效或過期（公開的 repo 不需要權杖，可以刪掉 ~/.gh_token）",
        403: "被 GitHub 限流或權杖沒有權限：稍後再試，或確認權杖有 Contents 讀取權限",
        404: "找不到（repo 還沒有任何 Release、repo 名稱不對，或 repo 是私人的但沒有權杖）",
    }.get(code, "HTTP %s" % code)


# ---------- 網路與系統 ----------

def read_token():
    try:
        t = open(TOKEN_FILE).read().strip()
    except OSError:
        return None
    return t or None


def gh_json(path, token=None):
    h = {"Accept": "application/vnd.github+json", "X-GitHub-Api-Version": "2022-11-28", "User-Agent": "phoneai-update"}
    if token:
        h["Authorization"] = "Bearer " + token
    return json.loads(urllib.request.urlopen(urllib.request.Request(API + path, headers=h), timeout=30).read())


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *a, **k):
        return None


def download_asset(url, token=None):
    """下載 Release 檔案。沒有權杖：直接下載（公開 repo）。有權杖（私人 repo）：第一次請求帶權杖、取得轉址網址；
    第二次（另一個網站）不帶權杖。"""
    if not token:
        return urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "phoneai-update"}), timeout=180).read()
    opener = urllib.request.build_opener(_NoRedirect)
    req = urllib.request.Request(url, headers={"Authorization": "Bearer " + token, "User-Agent": "phoneai-update",
                                               "Accept": "application/octet-stream"})
    try:
        r = opener.open(req, timeout=30)
        return r.read()  # 沒轉址就直接是內容
    except urllib.error.HTTPError as e:
        if e.code not in (301, 302, 303, 307, 308):
            raise
        loc = e.headers.get("Location")
    return urllib.request.urlopen(urllib.request.Request(loc, headers={"User-Agent": "phoneai-update"}), timeout=180).read()


def git_env(token):
    """讓 git 對 github.com 帶權杖，但**不寫進 .git/config、不放在指令列**（用環境變數傳設定）。私人 repo 的 clone／pull 要用。"""
    if not token:
        return dict(os.environ, GIT_TERMINAL_PROMPT="0")
    header = "Authorization: Basic " + base64.b64encode(("x-access-token:" + token).encode()).decode()
    return dict(os.environ, GIT_CONFIG_COUNT="1", GIT_CONFIG_KEY_0="http.extraHeader", GIT_CONFIG_VALUE_0=header,
                GIT_TERMINAL_PROMPT="0")


def sh(cmd, timeout=120, env=None):
    r = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=timeout, env=env)
    return r.returncode, (r.stdout + r.stderr).strip()


def init_repo(token=None):
    """把 ~/phone-ai-agent 換成 Git 版：舊的 ZIP 資料夾改名備份成 ~/phone-ai-agent.zipbak，clone 失敗就改回來。成功回 0。"""
    if os.path.isdir(REPO_DIR + "/.git"):
        print("已經是 Git 版，不用再轉。")
        return 0
    backup = REPO_DIR + ".zipbak"
    shutil.rmtree(backup, ignore_errors=True)
    if os.path.isdir(REPO_DIR):
        os.rename(REPO_DIR, backup)
    print("下載專案（git clone）…")
    code, out = sh("git clone --depth 50 https://github.com/%s/%s.git %s" % (OWNER, REPO, REPO_DIR), timeout=300, env=git_env(token))
    if code != 0:
        shutil.rmtree(REPO_DIR, ignore_errors=True)
        if os.path.isdir(backup):
            os.rename(backup, REPO_DIR)
        print("   ❌ clone 失敗，已還原原本的資料夾：" + out[-300:])
        print("   常見原因：權杖沒有 Contents 讀取權限、權杖過期，或沒選到這個 repo。")
        return 1
    code, out = sh("bash %s/install.sh" % REPO_DIR)
    print("   " + (out.splitlines()[-1] if out else "完成"))
    print("完成。舊的 ZIP 版資料夾在 %s（確認沒問題後可以刪掉）。之後只要執行 python ~/update_app.py 就會一行更新。" % backup)
    return 0


def installed_build():
    """向 App 問它的 commit 前 7 碼（診斷裡的 build=…）；連不上回 None。"""
    try:
        sys.path.insert(0, HOME)
        from overlay import Overlay
        ov = Overlay()
        if not ov.connect():
            return None
        diag = ov.check()
        ov.end()
        for part in diag.split():
            if part.startswith("build="):
                return part[len("build="):]
    except Exception:
        return None
    return None


def main():
    check_only = "--check" in sys.argv
    token = read_token()   # 可選：只有私人 repo 才需要

    if "--init-repo" in sys.argv:
        return init_repo(token)
    if not check_only and os.path.isdir(REPO_DIR) and not os.path.isdir(REPO_DIR + "/.git"):
        print("這份專案資料夾是 ZIP 解壓縮來的（沒有 Git），沒辦法一行更新。先執行一次： python ~/update_app.py --init-repo")
        return 1
    if not check_only and os.path.isdir(REPO_DIR):
        print("1/4 更新腳本（git pull）…")
        code, out = sh("git -C %s pull --ff-only" % REPO_DIR, env=git_env(token))
        print("   " + out.splitlines()[-1] if out else "   完成")
        if code != 0:
            print("   ❌ git pull 失敗，先處理它（可能有本機改動）")
            return 1
        code, out = sh("bash %s/install.sh" % REPO_DIR)
        print("   " + (out.splitlines()[-1] if out else "完成"))

    print("2/4 查詢最新 Release（%s/%s）…" % (OWNER, REPO))
    try:
        rel = pick_release(gh_json("/releases/latest", token))
    except urllib.error.HTTPError as e:
        if e.code == 404:   # 還沒有任何發布版本：沒有比現在更新的，當作已是最新
            print("   目前沒有可以更新的發布版本，已是最新版本。")
            return 0
        print("   ❌ " + explain_http_error(e.code))
        return 1
    except Exception as e:
        print("   ❌ 連線失敗：%s" % e)
        return 1
    if not rel:   # 最新發布沒有 APK（通常是編譯還沒跑完）：沒有可安裝的新版本，不算錯誤
        print("   最新的發布版本沒有可安裝的 APK，已是最新版本（或編譯還沒跑完，稍後再試）。")
        return 0
    print("   最新版本：%s（%s）" % (rel["sha"][:7] or rel["tag"], rel["tag"]))

    cur = installed_build()
    if app_is_current(cur, rel["sha"]):
        print("3/4 App 已經是最新（%s），不用重裝。" % cur)
        return 0
    print("3/4 App 是 %s，最新是 %s。%s" % (cur or "（讀不到）", rel["sha"][:7] or rel["tag"], "（--check 只檢查，不安裝）" if check_only else ""))
    if check_only:
        return 0

    try:
        print("4/4 下載並安裝 APK（%d KB）…" % (rel["size"] // 1024))
        blob = download_asset(rel["api_url"] if token else rel["url"], token)
    except urllib.error.HTTPError as e:
        print("   ❌ " + explain_http_error(e.code))
        return 1
    except Exception as e:
        print("   ❌ 下載失敗：%s" % e)
        return 1
    d = HOME + "/apk_update"
    os.makedirs(d, exist_ok=True)
    apk = d + "/app-debug.apk"
    open(apk, "wb").write(blob)
    code = subprocess.call(["bash", HOME + "/overlay_install.sh", apk])
    try:
        os.remove(apk)
    except OSError:
        pass
    print("完成。" if code == 0 else "❌ 安裝腳本失敗（看上面的訊息）")
    return code


if __name__ == "__main__":
    code = main()
    print("=== 結束 ===")   # App 的「更新進度」頁看到這行就停止自動刷新
    sys.exit(code)
