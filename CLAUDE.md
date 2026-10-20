# CLAUDE.md — 給 Claude Code 的專案說明

這個 repo 是「在已 root 的 Samsung S25+ 上，用 Termux 做的手機 AI 助手」。**開始前先完整讀 [`HANDOFF.md`](HANDOFF.md)**
（目標、架構、狀態表、決策、安全規則、下一步），再讀 `docs/TROUBLESHOOTING.md`、`docs/EXPERIMENTS.md`。
Now Bar / 漣漪 / App 操作介面已完成並實測（研究與決策紀錄在 [`docs/ROADMAP_OVERLAY.md`](docs/ROADMAP_OVERLAY.md)）；接下來做什麼看 `HANDOFF.md` 第 11 節。
改 App（`android-overlay/`）：雲端可用 Android SDK + Gradle 先編譯確認（見 HANDOFF 11b），再靠 GitHub Actions 出 APK 給使用者；APK 已用固定金鑰簽名，之後可直接覆蓋安裝。

## 你在哪裡執行、怎麼測試
- 程式要在**使用者的手機（Termux，root）**上跑。你（Claude Code）如果跑在雲端/電腦，**碰不到手機**：你改程式、
  使用者把 repo 更新到手機上測、再把輸出貼回來。如果你就是跑在手機的 Termux 裡，才能直接執行 `scripts/` 並用 `su -c`。
- 先確認自己在哪：`uname -a`、`which su termux-notification`。不要假設。
- 使用者沒有電腦。Android App 要靠 **GitHub Actions** 編譯。

## 與使用者互動
- **繁體中文**（使用者常用語音輸入，訊息可能是簡體，回覆仍用繁體）。使用者是新手，**一次一小步**，每步說明在做什麼、成功的樣子、失敗要貼什麼。
- 手機螢幕小：回覆短、指令放單一 code block、提醒貼之前確認只有一行。
- **不確定就說不確定**，推測要標明並設計小實驗。價格資料是第三方整理的，要註明並請看 Console Usage。
- 不要編造觀察結果；用 `AGENT_DEBUG=1` 看實際畫面、看真正的輸出。先問「中途有沒有切 App」再怪程式。

## 規則（不要放寬）
- **絕不**要使用者貼 API Key；Key 只在手機 `~/.claude_key`（chmod 600），不進 repo、不寫進腳本、不 `cat`。
- 不提交截圖、紀錄檔、模型檔（`.gitignore` 已排除）。
- 敏感操作（付款、轉帳、刪除、安裝、傳訊息、登入、密碼…）**一律跳確認框問使用者，不是直接拒絕**；確認框沒回應 = 不同意。
- 改既有腳本：先 `grep -n` 看現況；用帶 `assert` 的修補方式，找不到就中止。改完 `python -m py_compile` / `bash -n`。
- 每完成一件事：更新 `HANDOFF.md` 的**狀態表（✅/🟡/❌）**與**下一步**，並提醒使用者 commit & push。✅ 只能是使用者在手機上實測過的。

## 常用指令（在手機 Termux）
```bash
python -m unittest discover -s tests              # 離線測試（不呼叫 API、不需要手機；雲端與手機都能跑），改完程式先跑
bash install.sh                                   # 把 scripts/ 複製到 ~
python ~/selftest.py                              # 一鍵自我檢查（root、App、Termux:API、scrcpy…；--full 會實際建立背景螢幕）
python ~/update_app.py                            # 一行更新：git pull + 下載安裝最新 Release 的 APK（公開 repo 不需要權杖；App 內「功能 → 維護 → 檢查並更新」同樣功能）
python scripts/audit_secrets.py --words 名字       # 公開 repo 前的清查（只讀）
python ~/assistant.py                             # 互動；「ai …」強制交給 Claude
python ~/agent2.py "進入設定裡的Wi-Fi頁面" 8        # Claude 迴圈（環境變數見檔案開頭說明）
AGENT_DEBUG=1 AGENT_TOUCHES=1 python ~/agent2.py "…"   # 存每步畫面 / 開系統顯示點觸
```
