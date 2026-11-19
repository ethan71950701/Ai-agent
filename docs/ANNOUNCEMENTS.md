# 公告與功能開關（Announcements）

App 打開時（最多每 10 分鐘一次）會讀 repo `main` 分支根目錄的 `announcement.json`：
`https://raw.githubusercontent.com/<repo>/main/announcement.json`。**改這個檔案、推到 `main`，使用者不用更新 App 就能看到公告**。
設定 →「更新」→「開 App 時看公告」可以關掉（關掉就收不到公告，也收不到功能停用）。

## 格式

```json
{
  "id": "2026-11-20-bug",
  "severity": "critical",
  "title": { "zh": "請盡快更新", "en": "Please update soon" },
  "body":  { "zh": "舊版的某某功能有問題…", "en": "Older versions have a problem with …" },
  "min_version_below": 1700,
  "expires": "2026-12-31",
  "disable": ["background_mode"]
}
```

| 欄位 | 說明 |
|---|---|
| `id` | 公告編號。**沒有 id 或沒有 title／body 就不會顯示**（空檔案＝沒有公告）。同一個 id 每台手機只彈一次。 |
| `severity` | `info`（一般）、`warn`、`critical`。`warn`／`critical` 會多一顆「前往更新」鈕；`critical` 加上 `min_version_below` 時，**還沒更新就每次打開都提醒**。 |
| `title`、`body` | `{"zh":"…","en":"…"}` 或純字串。標題最多 80 字、內文最多 600 字，只顯示純文字。 |
| `min_version_below` | 版本號（versionCode，Release 標籤 `build-1684-…` 裡的 1684）。**只有安裝的版本號小於這個數字才顯示**；省略＝所有版本都看到。 |
| `expires` | `yyyy-MM-dd`，（UTC）那天之後不再顯示。 |
| `disable` | 暫時停用的功能名稱：`background_mode`、`file_transfer`、`scheduled_lookup`、`chat_stream`、`run_shell`。其他名稱一律忽略。 |

## 功能開關的規則

- 每次成功抓到檔案，就以那份檔案的 `disable` 為準；**檔案裡沒有寫的功能就恢復**。
- 抓不到網路超過 7 天，停用自動失效（不會永遠關著）。
- 停用的功能：`background_mode`（背景模式不啟用）、`file_transfer`（互傳檔案拒絕連線）、`scheduled_lookup`（定時網路查詢不建立也不執行）、`chat_stream`（聊天改成一次顯示）、`run_shell`（唯讀 shell 工具一律拒絕）。

## 安全設計

- 只讀固定的 https 網址；檔案大小上限 20 KB；不傳送任何使用者資料。
- 只顯示文字。**檔案裡寫的網址不會被開啟或下載**；「前往更新」用 App 內建的更新流程。
- 功能開關只認白名單，所以就算檔案被竄改，最多只能讓使用者看到一段文字、或暫時停用上面五個功能，做不了別的事。
- 這個檔案放在公開 repo，內容大家都看得到；不要寫敏感資訊。
- 限制：App 要有開啟、有網路才會收到；Android 沒辦法強制使用者更新。

## 範例

```json
{ "id": "2026-11-20-update", "severity": "warn",
  "title": { "zh": "有新版本", "en": "New version" },
  "body": { "zh": "修正了重要的安全問題，建議更新。", "en": "Fixes an important security issue; please update." },
  "min_version_below": 1700 }
```

暫時停用背景模式（不顯示公告）：

```json
{ "id": "", "disable": ["background_mode"] }
```

恢復：把 `disable` 拿掉（或整個檔案回到空的 `announcement.json`）。
