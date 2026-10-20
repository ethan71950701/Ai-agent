# 路線圖：Now Bar 即時狀態 + 點擊漣漪特效（尚未開發）

> 狀態：**只有計畫**。下面標「未驗證」的都是推測，動手前請用「步驟 0」的小實驗確認。
> 提出者：使用者（2026-10-17）。

## 1. 使用者想要什麼（原話意思）

1. AI 在操作手機時，**接入 Android 的即時通知**，並顯示在三星 One UI 的 **Now Bar**，顯示「正在執行」。
2. 同時顯示**正在點什麼、滑什麼**。
3. 點擊的位置要有**類似漣漪擴散的特效**（滑動也最好有軌跡）。

## 2. 目前已有的（agent2.py，皆在 Termux 內，**未實測**）

- `status()`：用 `termux-notification` 發常駐通知（id 7195），每步更新「第 N/M 步 · 點擊『網址列』」，輸入文字只顯示字數；
  通知上有「停止」鈕（`pkill -f agent2.py`，SIGTERM 已轉成正常結束以便清理）。`AGENT_NOTIFY=0` 關閉。
- `AGENT_TOUCHES=1`：任務期間打開系統「顯示點觸」(`settings put system show_touches 1`)，結束還原。
  **未驗證**：用 `input tap` 注入的點擊會不會被畫成圓點。
- 這些是**一般通知**；Termux 沒辦法在別的 App 上面畫東西，所以沒有漣漪。

## 3. 步驟 0：先做的小實驗（不用寫 App，15 分鐘）

請使用者在手機上跑，並把結果貼回來：

```bash
getprop ro.build.version.sdk          # Android API 等級（Android 16 = 36）
getprop ro.build.version.oneui        # One UI 版本（一般是 80000 這種數字，One UI 8 = 80000，未驗證）
AGENT_TOUCHES=1 python ~/agent2.py "進入設定裡的Wi-Fi頁面"
```
觀察：
- (a) 通知欄有沒有「AI 正在操作手機」、每步會不會更新、按「停止」能不能中斷。
- (b) **Now Bar（鎖定畫面/狀態列膠囊）有沒有出現**這則通知？（預期：沒有，因為不是 Live Updates 規格 —— 推測）
- (c) 點擊時螢幕上有沒有白色圓點？

結論決定下一步：
- (b)(c) 都不行 -> 走第 4 節自製 App。
- (c) 有圓點但不夠漂亮 -> 仍建議自製 App（要漣漪）。

## 4. 自製 Android App（建議方案）

名稱暫定 `PhoneAI Overlay`，套件名暫定 `com.example.phoneaioverlay`（開發時再改）。**不依賴 AndroidX/Compose**，
盡量只用原生 Java/Kotlin + View，減少相依，方便 CI 編譯。

### 4.1 功能 A：懸浮層畫點擊漣漪（解決需求 3）

- 一個前景 `Service`（需 `FOREGROUND_SERVICE` + 通知）持有一個**全螢幕透明** `View`，用 `WindowManager.addView`：
  `type = TYPE_APPLICATION_OVERLAY`，`flags = FLAG_NOT_TOUCHABLE | FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_NO_LIMITS`，
  `format = TRANSLUCENT`。因為 `FLAG_NOT_TOUCHABLE`，**不會擋住底下 App 的觸控**。
- 權限：「顯示在其他應用程式上層」(`SYSTEM_ALERT_WINDOW`)。手機已 root，可用
  `su -c "appops set <pkg> SYSTEM_ALERT_WINDOW allow"`，否則請使用者在設定裡手動開。
- 接收指令：`BroadcastReceiver`（action 例如 `<pkg>.RIPPLE`，extras：`x`、`y`，**單位是螢幕實際像素，
  與 `input tap` 同座標系，這台是 1440x3120**）。畫 2~3 圈擴散的圓環，約 500~700ms，`ValueAnimator` 改半徑與透明度。
  另有 `<pkg>.SWIPE`（`x1,y1,x2,y2`）畫軌跡線與終點圓點。
- 從 Termux 觸發：`su -c "am broadcast -n <pkg>/.CmdReceiver -a <pkg>.RIPPLE --ei x 547 --ei y 2954"`
  （用 root 發、指定元件，Receiver 可設 `exported=false` 仍可由 root 呼叫 —— **未驗證**，若不行就設 exported 並加 permission）。
  為了不拖慢主流程，agent2 用 `Popen` 在 `input tap` 之前背景送出。

### 4.2 功能 B：Now Bar 即時狀態（解決需求 1、2）

- Android 16 (API 36) 的 **Live Updates / promoted ongoing notification**：
  `Notification.Builder.setOngoing(true)` + `setRequestPromotedOngoing(true)`，可加 `setShortCriticalText("3/8")`、
  `Notification.ProgressStyle`（進度條）；manifest 需 `POST_NOTIFICATIONS` 與 `POST_PROMOTED_NOTIFICATIONS`。（以上 API 名稱來自對 Android 16 的印象，**未驗證，請查官方文件**）
- One UI 8 的 Now Bar 對第三方 Live Updates 的支援程度**未知**，要實機測。備案：三星自己的 ongoing-activity 通知有專用 extras
  （名稱類似 `android.ongoingActivityNoti.*`，**未驗證**），第三方是否能用不確定。
- 接收指令：`<pkg>.STATUS`（extras：`title`、`text`、`step`、`max`、`ended`），agent2 每步送一次，取代 `termux-notification`。
- 通知保留「停止」按鈕（PendingIntent -> 廣播 -> `su -c pkill -f agent2.py` 或讓 App 發 intent 給 Termux 的 RUN_COMMAND，後者需在 Termux 設 `allow-external-apps`）。

### 4.3 建置（手機上沒有 Android SDK、使用者沒有電腦）

- 用 **GitHub Actions**：`ubuntu-latest` + `actions/setup-java@v4`（17）+ Android SDK（`android-actions/setup-android`）+ Gradle，
  編出 **debug APK** 並 `upload-artifact`（或附到 Release）。debug 簽名即可，**不要**用需要 secret 的 release 簽名。
- 使用者在手機瀏覽器登入 GitHub 下載 APK，再用 root 安裝：`su -c "pm install -r /sdcard/Download/app-debug.apk"`（或點檔案安裝）。
- 專案放 `android-overlay/`（與 `scripts/` 並列）；workflow 放 `.github/workflows/android.yml`。
- 在雲端（例如 Claude Code 的環境）可能連不到 Google 的 SDK 下載站，所以**編譯交給 GitHub Actions**，開發環境只改程式。

### 4.4 與 agent2.py 整合

- 新環境變數 `AGENT_OVERLAY=1`（或自動偵測 App 已安裝：`pm list packages | grep <pkg>`）。
- `tap`：先背景送 RIPPLE，再 `input tap`；`swipe`/`scroll`：送 SWIPE；每步 `status()` 改送 STATUS；
  App 不在或廣播失敗 -> **自動退回現在的 `termux-notification`**，不可讓特效壞掉任務。
- 關鍵細節：**`screencap` 會把懸浮層一起截進去**。漣漪動畫要在下一次截圖前結束（動畫 <700ms，`wait` 預設 ≥1.0 秒，OK），
  狀態列/膠囊不要蓋住畫面中間，否則 Claude 會看到自己的特效而誤判。必要時截圖前先送 `HIDE`。

## 5. 風險與未知

| 風險 | 說明 |
|---|---|
| Now Bar 不收第三方通知 | 最大未知；只能實機測。若不收，退而求其次：一般常駐通知 + 狀態列膠囊（Live Updates 在狀態列顯示的 chip）。 |
| 懸浮層被三星擋 | 某些畫面（安全畫面、錢包、Knox）會擋 overlay；漣漪消失不影響任務。 |
| 截圖含特效 | 見 4.4。 |
| root 發廣播延遲 | `am` 啟動約 50~150ms，背景送出可接受。 |
| 使用者不熟開發 | 要把每個手動步驟（開權限、裝 APK）拆成一小步，繁體中文。 |

## 6. 建議的拆工（給 Claude Code / 下一個 AI）

1. **T1** 跑步驟 0 實驗，結果寫進 `docs/EXPERIMENTS.md`。
2. **T2** 建 `android-overlay/` 骨架 + GitHub Actions，先只做「按一個按鈕就在螢幕中央畫漣漪」，確認 CI 能出 APK、使用者能安裝。
3. **T3** 加廣播接收 RIPPLE/SWIPE，從 Termux `am broadcast` 觸發，量延遲，確認不擋觸控。
4. **T4** 加 STATUS Live Update 通知，實測 Now Bar。
5. **T5** 整合進 `agent2.py`（含自動退回、截圖前隱藏），更新 `HANDOFF.md` 狀態表。
6. 每做完一項：更新狀態表、請使用者 commit。

## 7. 驗收

- 手機鎖定/在別的 App 時，Now Bar 或狀態列看得到「第 N/M 步 · 動作」。
- 點擊處出現漣漪，且**不影響**被點的 App、不被 Claude 當成畫面內容。
- 按「停止」能在 2 秒內中斷任務並清掉截圖。
- App 沒裝時，行為與現在完全一樣。
