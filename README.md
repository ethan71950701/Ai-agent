# phone-ai-agent

在**已 root 的 Android 手機（Samsung S25+）**上，用 **Termux** 做一個類似 Gemini 的手機助手：
說（目前是打字）一句話，AI 或 root 指令幫你操作手機。全部在手機上跑，不需要電腦。

> 這是個人實驗專案，作者是新手；很多部分「在作者手機上驗證過」但不保證在你的機型能用。
> **接手/繼續開發請先讀 [`HANDOFF.md`](HANDOFF.md)。**

## 它能做什麼

用一個小 Android App（**PhoneAI Overlay**，`android-overlay/`）當操作介面，Termux 裡的 Python 當大腦：

| 怎麼叫出來 | 說明 |
|---|---|
| App 主畫面的輸入框 | 打字，或按「🎤 語音」（辨識完倒數 5 秒才送出，期間可修改） |
| 快速設定磁貼「AI 助手」 | 下拉面板點一下，跳出輸入視窗 |
| 長按電源鍵 | 把 App 設成「預設數位助理」（三星要不要改側邊鍵設定視機型而定） |
| 螢幕邊緣的浮動「AI」圓鈕 | App 主畫面勾選；可拖曳 |

送出的句子由 `assistant.py` 分流：

| 方式 | 例子 | 速度 | 需要網路/API |
|---|---|---|---|
| root 直接指令 / 用名稱開任何 App | 開關藍牙/Wi-Fi、返回、音量、「開相機」「開B站」 | 瞬間 | 否 |
| Claude 自動迴圈 `agent2.py`（Sonnet 5.5，卡住升 Opus 5.5 做一步後降回；看截圖點擊、滑動、輸入；必要時**聯網搜尋**，每任務最多 2 次） | 「打開 Chrome 搜尋今天天氣」「進入Wi-Fi頁面」 | 約 2 秒/步 | 是 |
| Haiku 5.5 路由 | 「藍芽打開」（錯字也行）-> 指令表；其餘交給 Claude 迴圈 | 約 1 秒 | 是 |
| 本地模型點螢幕（Qwen3-VL-2B） | **預設關閉**（手機會很燙、約 20 秒/步）；`ASSIST_LOCAL=1` 才啟用 | 慢 | 否 |

執行時：左上角 **Now Bar 膠囊**（「AI 3/8」）、鎖定畫面通知、每次點擊處的**藍色漣漪**、通知上的「停止」鈕。
App 主畫面會列出最近 5 筆任務的步數、token、搜尋次數與**估價**。

狀態：以上都在作者的 Samsung S25+（Android 16 / One UI）上實測過，唯一還沒實測的是 App 的原生確認框；細節見 [`HANDOFF.md`](HANDOFF.md) 第 6 節狀態表。
成本：同一個「進入 Wi-Fi 頁面」任務（4 步）約 $0.012（提示詞快取後，估算；以 Anthropic Console 的 Usage 為準）。

## 快速開始
1. 依 [`docs/SETUP.md`](docs/SETUP.md) 安裝 Termux（本地模型是選用，不裝也能用 Claude 版）。
2. `bash install.sh` 把腳本複製到 `~`。
3. 把 Anthropic API Key 存到 `~/.claude_key`（**不要提交進 git**，做法見 SETUP 第 6 節）。
4. 安裝 App：到 GitHub 的 Actions 下載最新的 `phoneai-overlay-apk`（zip，解壓出 `app-debug.apk`），再在 Termux 跑 `bash ~/overlay_install.sh`
   （步驟與踩坑見 SETUP 第 10 節）。
5. 打開 App，在輸入框送「開藍牙」或「進入設定的Wi-Fi頁面」。
6. 遇到問題看 [`docs/TROUBLESHOOTING.md`](docs/TROUBLESHOOTING.md)。

## 目錄
```
CLAUDE.md               給 Claude Code 的專案說明（指向 HANDOFF.md）
HANDOFF.md              給接手 AI/人的完整交接（目標、架構、狀態、決策、下一步）
android-overlay/        PhoneAI Overlay App（Java，無 AndroidX）：漣漪、Now Bar 通知、輸入/語音/磁貼/浮動按鈕、確認框
.github/workflows/      GitHub Actions：雲端編譯 APK（使用者沒有電腦）
docs/                   SETUP（安裝）、TROUBLESHOOTING（問題與解法）、EXPERIMENTS（實測數據）、ROADMAP_OVERLAY（Now Bar/漣漪/App 的研究與決策紀錄）
scripts/                assistant.py（分流）、agent2.py（Claude 迴圈）、overlay.py / applist.py（跟 App 溝通）、path.py（本地模型，預設關）...
install.sh              複製腳本到 ~
```

## 安全
- 會用 root 權限點擊螢幕，App 也被授權 root（用於「停止」鈕）。App 沒有任何對外開放、能收指令的入口（Termux 到 App 的連線只開在手機內部，且要有只有 root 讀得到的 token）。只放行白名單指令；付款/轉帳/刪除/安裝等敏感操作一律跳確認框由你決定（不是直接拒絕）；有最大步數、卡住偵測，通知上有「停止」鈕。
- Claude 版每一步會把**截圖傳到 Anthropic**；不要在銀行、私訊、含個資的畫面使用。
- 不要提交 Key、截圖、紀錄檔（`.gitignore` 已排除）。

## 授權
Apache License 2.0，見 [`LICENSE`](LICENSE)。
