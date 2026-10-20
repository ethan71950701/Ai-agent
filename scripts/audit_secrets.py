"""audit_secrets.py — 公開 repo 之前的清查（**只讀**，不改任何檔案、不改 git）。

用法（在 repo 裡）:
    python scripts/audit_secrets.py                       掃目前的 repo
    python scripts/audit_secrets.py --words 王小明,my-wifi  另外搜尋你指定的個人字眼（名字、Wi-Fi 名稱…，逗號分隔）
檢查：
  1. 追蹤中的檔案有沒有不該公開的類型（截圖、紀錄、APK、金鑰庫、模型檔、.env…）
  2. 工作樹與「全部歷史」有沒有 API Key / 權杖 / 私鑰的樣子
  3. 提交作者有沒有用真實 Email（公開後會永久顯示）
  4. 有沒有 LICENSE
  5. --words 指定的個人字眼，在檔案與歷史裡有沒有出現
結果：❌ 公開前一定要處理 / ⚠️ 建議處理 / ✅ 正常。
"""
import re, subprocess, sys

OK, WARN, BAD = "✅", "⚠️", "❌"

SECRET_RE = (r"sk-ant-[A-Za-z0-9_-]{10,}|ghp_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|AKIA[0-9A-Z]{12,}"
             r"|-----BEGIN [A-Z ]*PRIVATE KEY|xox[baprs]-[A-Za-z0-9-]{10,}|AIza[0-9A-Za-z_-]{30,}")
RISKY_FILE_RE = re.compile(r"(\.(png|jpe?g|gif|mp4|apk|aab|jar|keystore|jks|p12|log|tsv|gguf|bin|zip)$)|(^|/)\.env|secret|token|id_rsa", re.I)
RISKY_NOTE = {
    "keystore": "簽名金鑰庫：公開等於把簽名金鑰交給別人，別人可以做出同樣簽名的假 App",
    "jks": "簽名金鑰庫", "p12": "憑證/金鑰", "apk": "編譯產物，不該進 repo", "jar": "第三方執行檔，不該進 repo",
    "png": "截圖可能含個人畫面", "jpg": "圖片可能含個人畫面", "log": "紀錄檔可能含個人資料",
    "tsv": "App 清單（個人裝置資訊）", "gguf": "模型檔", "zip": "壓縮檔",
}
NOREPLY_RE = re.compile(r"noreply|no-reply", re.I)


def git(repo, *args):
    r = subprocess.run(["git", "-C", repo] + list(args), capture_output=True, text=True)
    return r.stdout


def tracked_risky(repo):
    out = []
    for f in git(repo, "ls-files").splitlines():
        if RISKY_FILE_RE.search(f):
            ext = f.rsplit(".", 1)[-1].lower() if "." in f else ""
            out.append((f, RISKY_NOTE.get(ext, "名稱像是機密檔")))
    return out


def history_files(repo):
    """歷史上出現過、現在可能已刪掉的風險檔名（公開歷史就等於公開它們）。"""
    names = set(git(repo, "log", "--all", "--name-only", "--pretty=format:").splitlines())
    return sorted(n for n in names if n and RISKY_FILE_RE.search(n))


def grep_secrets(repo, pattern, history=True):
    """回傳 (工作樹命中的檔案, 歷史命中的 (commit, 檔案))。"""
    work = [l for l in git(repo, "grep", "-IlE", pattern).splitlines() if l]
    hist = []
    if history:
        revs = git(repo, "rev-list", "--all").split()
        for i in range(0, len(revs), 200):  # 分批，避免命令列太長
            chunk = revs[i:i + 200]
            for line in git(repo, "grep", "-IlE", pattern, *chunk).splitlines():
                if ":" in line:
                    hist.append(tuple(line.split(":", 1)))
    return work, hist


def author_emails(repo):
    """{ 作者: 提交數 }，只含不是 noreply 的真實 Email。"""
    counts = {}
    for line in git(repo, "log", "--all", "--format=%an <%ae>").splitlines():
        if line and not NOREPLY_RE.search(line):
            counts[line] = counts.get(line, 0) + 1
    return counts


def has_license(repo):
    return any(f.upper().startswith(("LICENSE", "COPYING")) for f in git(repo, "ls-files").splitlines())


def audit(repo=".", words=()):
    """回傳 [(狀態, 標題, 細節或修法), ...]。"""
    res = []
    risky = tracked_risky(repo)
    if risky:
        for f, note in risky:
            res.append((BAD, "追蹤中的敏感/不該公開的檔案：" + f, note))
    else:
        res.append((OK, "沒有追蹤截圖、紀錄、APK、金鑰庫等檔案", ""))
    past = [f for f in history_files(repo) if f not in {r[0] for r in risky}]
    if past:
        res.append((WARN, "歷史上曾出現過（現在已刪）的風險檔案：" + "、".join(past[:6]),
                    "公開歷史就等於公開它們；要公開請用「全新的 repo」，不要公開含舊歷史的這個"))
    work, hist = grep_secrets(repo, SECRET_RE)
    if work or hist:
        for f in work:
            res.append((BAD, "工作樹裡有金鑰/權杖的樣子：" + f, "立刻撤銷那把金鑰，再從檔案移除"))
        for c, f in hist[:5]:
            res.append((BAD, "歷史裡有金鑰/權杖的樣子：%s（commit %s）" % (f, c[:8]), "撤銷那把金鑰；只刪檔案沒用，歷史還在"))
    else:
        res.append((OK, "工作樹與全部歷史都沒有 API Key / 權杖 / 私鑰的樣子", ""))
    emails = author_emails(repo)
    if emails:
        res.append((BAD, "提交作者含真實 Email：" + "；".join("%s（%d 個提交）" % kv for kv in emails.items()),
                    "公開後會永久顯示在每個提交上。公開用全新的 repo，並用 GitHub 的 noreply 信箱重新提交"))
    else:
        res.append((OK, "提交作者都是 noreply 信箱", ""))
    if not has_license(repo):
        res.append((WARN, "沒有 LICENSE", "公開後別人預設不能合法使用；選一個授權（例如 MIT 或 Apache-2.0）"))
    else:
        res.append((OK, "有 LICENSE", ""))
    for w in [x.strip() for x in words if x.strip()]:
        work_w = [l for l in git(repo, "grep", "-IliF", w).splitlines() if l]
        revs = git(repo, "rev-list", "--all").split()
        hist_w = set()
        for i in range(0, len(revs), 200):
            for line in git(repo, "grep", "-IliF", w, *revs[i:i + 200]).splitlines():
                if ":" in line:
                    hist_w.add(line.split(":", 1)[1])
        if work_w or hist_w:
            where = sorted(set(work_w) | hist_w)
            res.append((WARN, "個人字眼「%s」出現在：%s" % (w, "、".join(where[:6])), "確認這些地方要不要公開；歷史裡的需要新 repo 才清得掉"))
        else:
            res.append((OK, "個人字眼「%s」沒有出現在檔案與歷史裡" % w, ""))
    return res


def main():
    args = sys.argv[1:]
    words = []
    if "--words" in args:
        words = args[args.index("--words") + 1].split(",")
    repo = next((a for a in args if not a.startswith("--") and a not in words and a != (",".join(words))), ".")
    bad = warn = 0
    for st, title, fix in audit(repo, words):
        print("%s %s" % (st, title))
        if fix and st != OK:
            print("     → " + fix)
        bad += st == BAD
        warn += st == WARN
    print()
    print("結果：%d 項必須處理、%d 項建議處理。" % (bad, warn) if (bad or warn) else "結果：全部正常 ✅")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
