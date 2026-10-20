"""selftest.py — 一鍵自我檢查：哪一項壞了，直接告訴你怎麼修。

用法:
    python ~/selftest.py          檢查所有「環境」項目（約 5 秒，不呼叫 API、不花錢、不動你的畫面）
    python ~/selftest.py --full   另外實際建立一次背景虛擬螢幕再收掉（約 15 秒，會短暫在背景開「設定」）
結果：✅ 正常 / ⚠️ 不影響主要功能但建議處理 / ❌ 壞了。最後一行是總結。
不會印出、也不會讀取 API Key 的內容（只檢查檔案存在與權限）。
"""
import hashlib, json, os, re, subprocess, sys

HOME = os.path.expanduser("~")
JAR = HOME + "/scrcpy-server.jar"
JAR_SHA256 = "764eb6f79811d5211fe9df341120882ba9994c7a61b897d7bf3fb662e53bc536"  # scrcpy-server-v5.0.1 官方 release 的雜湊值
PKG = "com.phoneai.overlay"

OK, WARN, BAD = "✅", "⚠️", "❌"


def run(cmd, timeout=15):
    """跑一個 shell 指令，回傳 (結束碼, 輸出)。"""
    try:
        r = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=timeout)
        return r.returncode, (r.stdout + r.stderr).strip()
    except subprocess.TimeoutExpired:
        return 124, "逾時"


def su(cmd, timeout=15):
    return run("su -c %s" % json.dumps(cmd), timeout)


def sha256_of(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 16), b""):
            h.update(chunk)
    return h.hexdigest()


def a11y_enabled(setting_text, pkg=PKG):
    """enabled_accessibility_services 的值裡有沒有我們的服務（用冒號分隔的清單）。"""
    return any(item.startswith(pkg + "/") for item in (setting_text or "").split(":"))


# ---------- 各項檢查：每個回傳 (狀態, 說明, 修法) ----------

def check_root():
    code, out = su("id -u")
    return (OK, "root 可用", "") if code == 0 and out.strip() == "0" else (
        BAD, "拿不到 root（su 失敗）", "確認 root 管理 App（Magisk/KernelSU）有授權給 Termux，再重開 Termux")


def check_key():
    p = HOME + "/.claude_key"
    if not os.path.exists(p):
        return BAD, "找不到 ~/.claude_key", "把 API Key 存進 ~/.claude_key（見 docs/SETUP.md；不要貼給任何人）"
    mode = os.stat(p).st_mode & 0o777
    if os.path.getsize(p) < 20:
        return BAD, "~/.claude_key 看起來是空的或太短", "重新寫入 Key"
    if mode & 0o077:
        return WARN, "~/.claude_key 權限是 %o，別人可能讀得到" % mode, "chmod 600 ~/.claude_key"
    return OK, "API Key 檔案存在且權限正確（沒有讀取內容）", ""


def check_magick():
    code, _ = run("magick -version")
    return (OK, "ImageMagick 可用（截圖縮放）", "") if code == 0 else (BAD, "沒有 ImageMagick", "pkg install imagemagick")


def check_termux_api():
    miss = [c for c in ("termux-notification", "termux-tts-speak", "termux-clipboard-get", "termux-dialog")
            if run("command -v " + c)[0] != 0]
    if miss:
        return BAD, "缺少 Termux:API 指令：" + "、".join(miss), "pkg install termux-api，並安裝 Termux:API 這個 App"
    code, out = run("termux-clipboard-get", timeout=8)
    if code != 0:
        return WARN, "termux-clipboard-get 失敗（輸入文字靠剪貼簿貼上）", "開啟 Termux:API App 一次，並確認它沒被電池最佳化殺掉"
    return OK, "Termux:API 指令齊全、剪貼簿可用", ""


def check_app():
    code, out = su("pm list packages " + PKG)
    if ("package:" + PKG) not in out.split():
        return BAD, "沒有安裝 PhoneAI Overlay App", "下載 Actions 的 APK 後執行 bash ~/overlay_install.sh（見 HANDOFF 第 10 節）"
    ver = su("dumpsys package %s | grep -m1 versionName" % PKG)[1].strip()
    return OK, "App 已安裝（%s）" % (ver or "版本未知"), ""


def check_a11y():
    code, out = su("settings get secure enabled_accessibility_services")
    if a11y_enabled(out):
        return OK, "無障礙服務已啟用（漣漪、預覽視窗要用）", ""
    return BAD, "無障礙服務沒開", "bash ~/overlay_install.sh 會幫你開；或到 設定→無障礙 手動開 PhoneAI Overlay"


def check_overlay_socket():
    try:
        sys.path.insert(0, HOME)
        from overlay import Overlay
    except ImportError:
        return BAD, "找不到 ~/overlay.py", "cd ~/phone-ai-agent && bash install.sh"
    ov = Overlay()
    if not ov.connect():
        return WARN, "連不上 App 的服務（agent2 會退回一般通知，沒有漣漪／Now Bar）", "先開一次 App，或跑 bash ~/overlay_install.sh"
    diag = ov.check()
    ov.end()
    return OK, "App 服務連線正常（%s）" % diag[:80], ""


def check_allow_external():
    p = HOME + "/.termux/termux.properties"
    try:
        txt = open(p).read()
    except OSError:
        txt = ""
    if re.search(r"^\s*allow-external-apps\s*=\s*true", txt, re.M):
        return OK, "Termux 允許 App 呼叫（面板送出任務要用）", ""
    return BAD, "Termux 沒開 allow-external-apps（面板送出的任務會失敗）", "bash ~/overlay_install.sh 會設好；之後執行 termux-reload-settings"


def check_scrcpy():
    if not os.path.exists(JAR):
        return WARN, "沒有 ~/scrcpy-server.jar（背景模式的隱形螢幕要用，沒有會退回有小視窗的做法）", \
            "curl -fL -o ~/scrcpy-server.jar https://github.com/Genymobile/scrcpy/releases/download/v5.0.1/scrcpy-server-v5.0.1"
    got = sha256_of(JAR)
    if got != JAR_SHA256:
        return BAD, "scrcpy 檔案的雜湊值不對（%s…）：不是官方 v5.0.1，或下載壞了／被換過" % got[:12], \
            "刪掉重新下載：rm ~/scrcpy-server.jar，再用上面那行 curl。不要以 root 執行來路不明的檔案"
    return OK, "scrcpy 檔案存在，雜湊值與官方 v5.0.1 一致", ""


def check_apps_list():
    p = HOME + "/apps.tsv"
    if os.path.exists(p) and os.path.getsize(p) > 0:
        n = sum(1 for _ in open(p, encoding="utf-8"))
        return OK, "App 清單 %d 筆（用名稱開 App 要用）" % n, ""
    return WARN, "還沒有 ~/apps.tsv（第一次跑 agent2 會自動向 App 要）", "python ~/applist.py"


def check_versions():
    try:
        v = open(HOME + "/.phoneai_version").read().strip()
    except OSError:
        v = None
    app = su("dumpsys package %s | grep -m1 versionName" % PKG)[1].strip()
    if not v:
        return WARN, "腳本版本未知", "cd ~/phone-ai-agent && git pull && bash install.sh"
    return OK, "腳本版本：%s；App：%s（App 的 commit 看主畫面「版本」那行，兩邊 commit 一樣就代表都更新了）" % (v, app or "?"), ""


def check_vdisplay_full():
    """實際建立一次背景虛擬螢幕再收掉（--full）。"""
    code, out = run("python %s/vdisplay.py 8" % HOME, timeout=60)
    created = re.search(r"新螢幕: 系統編號=(\d+) 截圖編號=(\d+)", out)
    gone = re.search(r"關閉伺服器後的螢幕: \['0'\]", out)
    if created and gone:
        return OK, "背景虛擬螢幕建立、開 App、截圖、收掉都成功", ""
    if created:
        return WARN, "建立成功，但收掉後螢幕沒有消失", "su -c \"pkill -f com.genymobile.scrcpy.Server\""
    return BAD, "背景虛擬螢幕建立失敗", "看輸出最後幾行：" + " | ".join(out.splitlines()[-3:])[:200]


CHECKS = [
    ("root", check_root),
    ("API Key 檔案", check_key),
    ("ImageMagick", check_magick),
    ("Termux:API", check_termux_api),
    ("PhoneAI Overlay App", check_app),
    ("無障礙服務", check_a11y),
    ("App 服務連線", check_overlay_socket),
    ("Termux 允許 App 呼叫", check_allow_external),
    ("scrcpy 檔案", check_scrcpy),
    ("App 清單", check_apps_list),
    ("版本", check_versions),
]


def main():
    full = "--full" in sys.argv
    checks = CHECKS + ([("背景虛擬螢幕（實際建立）", check_vdisplay_full)] if full else [])
    bad = warn = 0
    for name, fn in checks:
        try:
            st, msg, fix = fn()
        except Exception as e:  # 檢查本身出錯也要印出來，不能讓整個自我檢查中斷
            st, msg, fix = BAD, "檢查時出錯：%r" % e, ""
        print("%s %s：%s" % (st, name, msg))
        if fix and st != OK:
            print("     → " + fix)
        bad += st == BAD
        warn += st == WARN
    print()
    if bad:
        print("結果：%d 項壞了、%d 項建議處理。先修 ❌ 的。" % (bad, warn))
    elif warn:
        print("結果：沒有壞的，%d 項建議處理。" % warn)
    else:
        print("結果：全部正常 ✅")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
