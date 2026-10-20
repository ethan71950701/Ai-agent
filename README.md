# phone-ai-agent

在**已 root 的 Android 手機（Samsung S25+）**上，用 **Termux** 做一個類似 Gemini 的手機助手：
說（目前是打字）一句話，AI 或 root 指令幫你操作手機。全部在手機上跑，不需要電腦。

> 這是個人實驗專案，作者是新手；很多部分「在作者手機上驗證過」但不保證在你的機型能用。
> **接手/繼續開發請先讀 [`HANDOFF.md`](HANDOFF.md)。**

## 它能做什麼

| 方式 | 例子 | 速度 | 需要網路/API |
|---|---|---|---|
| root 直接指令 | 開關藍牙/Wi-Fi、返回、音量、開 App | 瞬間 | 否 |
| 本地模型點螢幕（Qwen3-VL-2B） | 「點Wi-Fi」、路徑 `設定,連線,Wi-Fi` | 約 17~20 秒/步 | 否 |
| Claude 自動迴圈（Sonnet 5.5，卡住升 Opus 5.5 做一步後降回；可用 `open` 指令開 App、`cmd` 開關藍牙） | 「打開 Chrome 搜尋今天天氣」 | 約 2~3 秒/步 | 是 |
| Haiku 5.5 路由 | 「藍芽打開」（錯字也行）-> 指令表；其餘交給 Claude 迴圈 | 約 1 秒 | 是 |

狀態（細節見 `HANDOFF.md` 第 6 節）：前兩種已在作者手機驗證；Claude 自動迴圈 `agent2.py` **部分驗證**
（點擊、滑動、輸入文字、確認框、升級到 Opus 並完成任務；點擊層級確認框與 enter/scroll/ask 未驗證）；桌面小工具入口已可用；語音輸入尚未完成。**下一步計畫：Now Bar 即時狀態 + 點擊漣漪（`docs/ROADMAP_OVERLAY.md`）。**

## 快速開始
1. 依 [`docs/SETUP.md`](docs/SETUP.md) 安裝 Termux、llama.cpp、模型。
2. `bash install.sh` 把腳本複製到 `~`。
3. 把 Anthropic API Key 存到 `~/.claude_key`（**不要提交進 git**）。
4. `python ~/assistant.py` 然後輸入 `開藍牙`、`開IG`、`點Wi-Fi`。
5. 遇到問題看 [`docs/TROUBLESHOOTING.md`](docs/TROUBLESHOOTING.md)。

## 目錄
```
CLAUDE.md               給 Claude Code 的專案說明（指向 HANDOFF.md）
HANDOFF.md              給接手 AI/人的完整交接（目標、架構、狀態、決策、下一步）
docs/                   ROADMAP_OVERLAY（下一步：Now Bar 狀態 + 點擊漣漪計畫）、SETUP（安裝）、TROUBLESHOOTING（問題與解法）、EXPERIMENTS（實測數據）
scripts/                所有腳本（assistant.py、path.py、agent2.py、cfind.py ...）
install.sh              複製腳本到 ~
```

## 安全
- 會用 root 權限點擊螢幕。只放行白名單指令；付款/轉帳/刪除/安裝等敏感操作一律跳確認框由你決定（不是直接拒絕）；有最大步數、卡住偵測，通知上有「停止」鈕。
- Claude 版每一步會把**截圖傳到 Anthropic**；不要在銀行、私訊、含個資的畫面使用。
- 不要提交 Key、截圖、紀錄檔（`.gitignore` 已排除）。

## 授權
Apache License 2.0，見 [`LICENSE`](LICENSE)。
