# 路線圖：Now Bar 即時狀態 + 點擊漣漪特效（尚未開發）

> 狀態（2026-10-17 更新）：**App 程式已寫好、雲端編譯通過，未在手機實測**（見第 8 節）。下面標「未驗證」的都是推測。
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

## 8. 實作現況（2026-10-17，Claude Code）

和第 4 節計畫的差異：
- **指令通道改成 localhost socket**，不用 `am broadcast`：`am` 每次要開一個 JVM（推測數百 ms），漣漪會比點擊晚出現。
  App 前景服務聽 `127.0.0.1:47195`，一行一個 JSON（`ripple`/`swipe`/`hide`/`status`/`diag`/`end`）。
  第一行要送 token（App 私有資料夾 `files/token`，只有 root 讀得到），其他 App 不能亂下指令。
- agent2 連線斷掉（結束、當掉、被殺）App 就自動收掉通知與懸浮層；啟動 60 秒沒人連也自己結束。
- 懸浮層 `alpha=0.8`：Android 12+ 規定別的 App 的懸浮層不透明度超過 0.8 會擋住底下觸控（推測也包含 `input tap`）。
- 截圖前 agent2 送 `hide` 清掉特效，不靠動畫時間。
- 通知「停止」由 App 用 `su -c "pkill -f agent2.py"`（第一次會跳 Magisk 授權）。
- Live Update：extra `android.requestPromotedOngoing` + `setShortCriticalText("3/8")` + `ProgressStyle`。
  三星 Now Bar 是否收第三方 Live Update **未知**；App 畫面「最近一次通知診斷」會顯示 `canPostPromoted`、`promotable`、`promoted`。

測試順序（每步一小步，給使用者）：
1. GitHub → Actions → 最新的「Android APK」→ Artifacts 下載 `phoneai-overlay-apk`（zip），解壓出 `app-debug.apk` 到「下載」。
2. Termux：`cd ~/phone-ai-agent && git pull && bash install.sh`（repo 位置依使用者實際而定），再 `bash ~/overlay_install.sh`。
3. 打開「PhoneAI Overlay」App → 按「4. 測試」：看漣漪、滑動軌跡、通知 1/3→3/3、Now Bar；回 App 看診斷那行。
4. Termux：`python ~/overlay.py`（測 Termux→App 通道，印出診斷）。
5. `python ~/agent2.py "進入設定裡的Wi-Fi頁面"`：漣漪位置準不準、點擊有沒有被擋、按通知「停止」能不能中斷。
6. 不想用 App：`AGENT_OVERLAY=0`，行為同以前。

### 8b. 實測發現與修正（2026-10-17，使用者手機）
- ✅ App 在 Termux/App 自己的畫面看得到漣漪；`overlay.py` 通道、`promoted=true` 都成功。
- ❌ **Now Bar 沒出現**（鎖定畫面、設定裡都沒有「即時通知」選項）。`promoted=true` 只代表 Android 接受，三星 Now Bar 另有條件（未查明）。使用者手機有 LSPosed，但先不走這條。
- ❌ 在「設定」前景時漣漪看不到。`dumpsys window windows` 顯示我們的懸浮層 `mIsForceHiddenNonSystemOverlayWindow=true`、`isVisible=false`：
  系統因為前景 App（推測是「設定」）要求隱藏非系統懸浮層。Chrome 前景是否也被隱藏沒有可靠資料。
- 修正（**未實測**）：新增 `RippleAccessibilityService`，改用 `TYPE_ACCESSIBILITY_OVERLAY` 畫漣漪（推測不受該隱藏規則影響）；
  `overlay_install.sh` 用 root 寫入 `enabled_accessibility_services` 開啟。診斷字串多一個 `a11y=true/false`。
- 若無障礙懸浮層仍被藏：備案是 LSPosed 模組、或把 App 變成系統 App（需 Magisk 模組，使用者目前是臨時 root）。

### 8c. Now Bar：參考 LiveBridge 2.1.4（2026-10-17，未實測）
使用者提供 LiveBridge（`com.locnall.KimGiSa`，會把別的 App 通知轉成 Live Update；Flutter + Kotlin）。**只做靜態分析（jadx 反編譯），沒有在任何裝置執行。**
- 結論：不需要 LSPosed。它從一般 App 發通知，在 `Notification` extras 加三星欄位，三星 One UI 就把它放進 Now Bar / 狀態列膠囊。
  欄位（`FlashlightNotificationBuilder` / `NetworkSpeedNotificationBuilder`）：`android.ongoingActivityNoti.style`(int，1=一般，2=只在鎖定畫面)、
  `primaryInfo`、`secondaryInfo`、`chipExpandedText`、`nowbarPrimaryInfo`、`nowbarSecondaryInfo`、`chipBgColor`(int)、`chipIcon`/`nowbarIcon`(Icon)，
  另有 `android.showSmallIcon=true`；並同時 `setRequestPromotedOngoing(true)` + `setShortCriticalText`。通知本身：ongoing、`setShowWhen(false)`、
  VISIBILITY_PUBLIC、category progress、`setForegroundServiceBehavior(IMMEDIATE)`。它只在 `Build.MANUFACTURER` 是 samsung 時才加三星欄位。
- 已套用到 `OverlayService.build()`；為貼近已知可行做法，拿掉了 ProgressStyle（要進度條之後再加回去實測）。
- 注意：LiveBridge 是別人的 App，授權/來源不明，**我們沒有安裝或使用它**，只參考欄位名稱。

### 8d. Now Bar 實測（2026-10-17）
- 三星 extras 有效果：通知欄的卡片變成三星圓角樣式；但**左上角膠囊與鎖定畫面沒有出現**。LiveBridge 本身在同一支手機可以用。
- `dumpsys notification --noredact` 並排比對：我們與 LiveBridge 的 `ongoingActivityNoti.*` 欄位、`requestPromotedOngoing` 完全相同；
  差別只有頻道 importance（LiveBridge=2、我們=3）。改成新頻道 `status_low`（IMPORTANCE_LOW、鎖定畫面可見）——**推測，待實測**。
- 實測：LiveBridge 也能進左上角/鎖定畫面；三星「設定→鎖定螢幕和AOD→Now Bar」的 App 清單有 LiveBridge、**沒有我們**；`dumpsys` 比對後通知旗標（含 `PROMOTED_ONGOING`）與 extras 都相同，只剩頻道 importance（已改仍無效）。
- 比對 LiveBridge 清單：application 層有 `<meta-data com.samsung.android.support.ongoing_activity=true>`，我們沒有。已加入（**推測是列入 Now Bar 清單的條件，待實測**）。
- 加了 ongoing_activity meta-data 後 Now Bar 清單仍沒有我們。`dumpsys package` 比對：LiveBridge `installerPackageName=com.google.android.packageinstaller`，我們（root `pm install`）是 `null`。
  `overlay_install.sh` 改用 `pm install -i com.google.android.packageinstaller`（**推測安裝來源會影響清單，待實測**）。
  系統設定裡有 `key_now_bar_<套件>`（每 App 開關）與隱藏的 `enable_notification_nowbar_test=0`，意義未查明。
- **logcat 找到原因（2026-10-17）**：`su -c "logcat -c"; python ~/overlay.py; su -c "logcat -d" | grep -iE "ongoingact|nowbar|..."`。
  三星 SystemUI 其實接受了我們的通知（`OngoingActivityController: onEntryUpdated … is added`、`OAID_ONGOING_CHIP_GENERATED`、`NowBarItemManager: onNowBarItemUpdated added`），
  但 `OngoingActivityDataHelper: shouldHide() return true … intent.component:null`，且 `mPendingIntent=null` → 我們沒設 `contentIntent`。
  已加 `setContentIntent`（開 MainActivity）——**推測是膠囊被藏的原因，待實測**。（前面加的 ongoing_activity meta-data、頻道 importance、安裝來源可能都不是必要條件，沒有逐項驗證。）

### 8e. 讀 SystemUI 程式碼找到真正原因（2026-10-17）
使用者把手機的 `SystemUI.apk`（`pm path com.android.systemui`）分段傳上來，用 jadx 靜態反編譯（只看、不執行）。
- `OngoingActivityDataHelper.shouldHide()` 只有在：不是前景 App、`mStyle<=1`、**`entry.isPromotedState()`**、非群組摘要、非 `mIsBindCutOff`、不在 PiP 清單，時才回 false（不隱藏）。
- `NotificationEntry.isPromotedState()`：`((isDevelopRonTestAllowed() || isAutomation() || !allowListUsing) && mIsRon) ? true : mIsPromoted`；
  `mIsPromoted = ranking.isOngoingActivityTurnedOn()`（Now Bar 設定清單裡每個 App 的開關，清單只列三星允許的 App）。
- `NotificationEntry.setSbn()`：secure `enable_notification_nowbar_test != 1` 時，只有套件名在 `allowedList` 才算數；=1 則略過名單，`mIsRon = hasPromotableCharacteristics()`。
- **`isOngoingActivity()`：extras `android.ongoingActivityNoti.style > 0` 時會把 `mIsRon` 設成 false**，所以帶三星欄位會落到只認 `mIsPromoted` 的舊路徑（要在清單裡）。
- 結論：LiveBridge 能用，推測是因為套件名 `com.locnall.KimGiSa`（Kakao T 司機端）在三星允許名單內。我們不冒用別人套件名；
  做法改成：**不帶 `ongoingActivityNoti.*`，只用標準 Live Update（requestPromotedOngoing + shortCriticalText）+ 把 `enable_notification_nowbar_test` 設 1**（`overlay_install.sh` 會設）。
  先前加 `ongoing_activity` meta-data、頻道 importance、installer、`ongoingActivityNoti` 欄位，推測都不是必要條件（沒有逐項驗證）。**待實測。**
- 已試過、沒效：開關設 1 + 帶 style 欄位（因為 isOngoingActivity() 把 mIsRon 洗掉）、重啟 SystemUI、關掉懸浮層。

## 9. App 變成操作介面（2026-10-17，程式完成、**全部未在手機實測**）
架構：App 只當前端，大腦仍是 Termux 的 `assistant.py` / `agent2.py`（已實測的白名單、確認規則都不動）。
- **送指令**：`CommandSender` 用 Termux 的 `RUN_COMMAND`（`com.termux.app.RunCommandService`，背景、以 Termux 身分）跑
  `bash -c 'python "$HOME/assistant.py" --once "$1" >> "$HOME/assistant.log" 2>&1' _ "<文字>"`（文字走 `$1`，不經 shell 解析）。
  需要 `~/.termux/termux.properties` 的 `allow-external-apps=true` 與本 App 的 `com.termux.permission.RUN_COMMAND`（`overlay_install.sh` 設）。
  安全提醒：有 RUN_COMMAND 權限的 App 能在 Termux 跑任意指令，所以只授權給本 App、`AskActivity` 不 exported。
- **輸入框 + 語音**：`AskPanel`（主畫面與 `AskActivity` 共用）。語音用 `RecognizerIntent`（系統的 Google 語音視窗，zh-TW）；
  辨識完**倒數 5 秒才送出**（改字或再按語音會取消），避免聽錯直接執行。沒有 Google 語音服務就提示改打字。
  （先前 `termux-speech-to-text` 沒彈視窗的原因沒查明，這條路不確定行不行。）
- **三種叫出方式**：(1) 快速設定磁貼「AI 助手」（`AskTileService`，要在下拉面板編輯加入）；
  (2) 預設數位助理（`AssistService`/`AssistSessionService`，系統設定選本 App 後長按電源鍵；三星的側邊鍵設定可能要改成「數位助理」；**不確定三星是否允許，待實測**）；
  (3) 浮動按鈕（`FloatButton`，無障礙懸浮層，主畫面勾選；可拖曳；agent 執行中自動隱藏避免被截進畫面）。
- **確認框**：`agent2.py` 的 `confirm()` 先走 `overlay.confirm()` → App 的 `ConfirmActivity`（原生是/否，120 秒沒回=否，4 秒內沒跳出會退回 `termux-dialog`）。
- 實作風險：從背景啟動 Activity（`ConfirmActivity`、浮動按鈕開 `AskActivity`）受 Android 背景啟動限制，我們靠懸浮層權限/可見視窗的豁免，是否成立待實測。
- **備援路線（root）**：`CommandSender.viaRoot()`。沒有 RUN_COMMAND 權限或被 Termux 拒絕時，App 用已有的 root：`su` 的標準輸入餵腳本，
  `stat -c %u /data/data/com.termux` 取得 Termux uid → `su <uid> -c '<export HOME/PREFIX/PATH/LD_LIBRARY_PATH…; python assistant.py --once <單引號跳脫的文字>>>'`。
  為什麼不直接以 root 跑 python：`termux-notification`/`termux-tts-speak` 等 Termux:API 要用 Termux 自己的身分。文字跳脫用 sh 模擬測過（單引號、`$`、反引號、`;` 都原樣當文字）；
  **整條路未在手機實測**，失敗時只會寫進 `~/assistant.log`（App 不會顯示錯誤）。
