# 沒有 root 也能用（2026-10-29 起）

`Root.available()` 會實際跑一次 `su` 判斷這支手機有沒有 root（結果記下來）。有 root 照舊用 root 指令（更快、更完整）；
沒有 root，或在設定 → 執行任務時勾了「不使用 root（測試用）」，就改用 `Phone.java` 的做法。

## 一定要先開的
- **無障礙服務**：沒有 root 時點擊、滑動、按鍵、截圖都靠它。沒開，任務會直接告訴你「請先開啟無障礙服務」。
- 更新 APK 後如果截圖失敗（無障礙設定檔多了 `canTakeScreenshot`），到系統設定把 PhoneAI 的無障礙服務關掉再打開一次。
- 懸浮視窗權限、通知權限（跟以前一樣）。

## 對照表

| 動作 | 有 root | 沒有 root |
|---|---|---|
| 點擊、長按、滑動、捲動、拖曳、多指 | `input tap/swipe` | 無障礙手勢（`RippleAccessibilityService.gesture`） |
| 分段拖曳（drag_start/move/end） | 輸入伺服器 | **不支援**，請 AI 改用 drag／swipe |
| 返回、首頁、最近使用、通知欄、快速設定、鎖螢幕、截圖鍵 | `input keyevent`、`cmd statusbar` | 無障礙全域動作 |
| Enter／送出 | `input keyevent 66` | 輸入法動作鍵（`ACTION_IME_ENTER`），沒有就請你自己按 |
| 輸入文字 | 無障礙填字，不行再 `input text`／貼上 | 無障礙填字，不行再貼上；要先點選輸入框 |
| 截圖 | `screencap -p` | 無障礙截圖（Android 11+，約一秒一張） |
| 開 App、開網址 | `monkey`、`am start` | `startActivity` |
| 查系統資訊（shell 動作） | `su` 跑唯讀指令 | 一般 `sh` 跑（`getprop` 可以，部分 `dumpsys` 讀不到） |
| 音量 | `input keyevent 24/25` | `AudioManager` |
| 亮度、自動亮度、自動旋轉 | `settings put system …` | `Settings.System`，**第一次要在設定允許 PhoneAI「修改系統設定」** |
| 勿擾 | `cmd notification set_dnd` | `NotificationManager`，**第一次要允許勿擾存取** |
| Wi-Fi、藍牙、深色模式、省電、飛航、定位、NFC、行動數據 | `svc …`、`cmd …` | **只能打開對應的設定頁**，要你自己切換（或讓 AI 接著點） |
| 行事曆事件 | `pm grant` 後直接寫入 | 打開行事曆新增畫面，按儲存 |
| 鬧鐘 | 直接設 | 直接設（有 `SET_ALARM` 權限，不用 root） |

## 沒有 root 就用不了的
- Tailscale 開關（靠 root 截圖＋點擊）、背景模式（虛擬螢幕）、電源鍵長按偵測（`getevent`）、無障礙自動修復（被系統關掉要自己重開）、一鍵補齊權限。
- 固定指令裡 Wi-Fi、藍牙等「只能打開設定頁」的項目，回覆不會說「已…」，讓 AI 知道還沒做完。

## 怎麼測
設定 → 執行任務時 → **不使用 root（測試用）**，在有 root 的手機上也能模擬沒有 root 的情況，
不用另外找一支沒 root 的手機。單元測試（`AssistTest`）會確認每個固定指令都有不用 root 的做法。

## Shizuku 模式（不用 root，用 adb 等級權限）

引導頁第一頁（或「設定 → 執行任務時 → 執行模式」）可以選 root／Shizuku／普通。Shizuku 模式要先安裝並啟動 Shizuku App，再在 PhoneAI 授權。
開關 Wi-Fi／藍牙、`settings put`、`input`、`screencap` 這些指令改走 Shizuku；需要真正 root 的功能（背景螢幕、即時觸控等）不支援。手機重開機後 Shizuku 要再啟動一次。
