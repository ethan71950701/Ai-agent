# CLAUDE.md — 給 Claude Code 的專案說明

這個 repo 是「在已 root 的 Android 手機上，用一個 App（PhoneAI Overlay）做的 AI 助手」。
**App 就是全部**：不需要 Termux、不需要 Python 腳本。

## 架構（現在）
- `android-overlay/`：Android App（Java，Gradle）。
  - 助理面板（`AssistantSheet`）：文字或語音輸入。
  - 路由（`Assist` 關鍵字 → `Router` Haiku 5.5 判斷）：固定指令（root）／開 App／聊天（`ChatEngine`）／操作畫面（`AgentLoop`）。
  - 自動操作迴圈（`AgentLoop` + `AgentPrompt`）：截圖 → Claude Sonnet 5.5 看圖 → root 執行動作（點擊、滑動、分段拖曳、多指觸控、輸入…）。
  - 記憶（`Memory`，檔案在 App 資料夾，格式跟舊版 memory.json 相同）、定時與短問題（`Schedule`）、通知與朗讀（`Notify`）。
  - 語音辨識：SenseVoice 離線模型（`SherpaEngine`）。
- API Key：App 資料夾的 `claude_key`（權限 600）。**絕不要把 Key 貼到聊天或印出來。**

## 怎麼建置
- 雲端可用 Android SDK + Gradle 先編譯確認：`cd android-overlay && gradle assembleDebug`。
- 單元測試（純邏輯，不需要手機）：`cd android-overlay && gradle testDebugUnitTest`，測試在 `app/src/test/`；CI 編譯前會先跑。
- 正式 APK 由 GitHub Actions 產生（`.github/workflows/android.yml`），使用者從 Release 下載安裝。

## 規則
- 使用者看得到的改動，要在 `CHANGELOG.md` 的 `## Unreleased` 加一條「中文｜English」。
- 發布時 CI 會自動把 `## Unreleased` 的內容移到以版本號命名的標題底下（例如 `## 1.0.1183`，commit 回 `main` 並帶 `[skip ci]`），所以 `Unreleased` 只留下一次的新改動；不用手動移。
- 編譯通過的改動**直接合併到 `main`**（使用者已同意，不用每次再問）；GitHub Actions 只在 `main` 自動編譯並發布。
- 介面有繁體中文與英文：使用者看得到的字串一律寫 `L.t("中文原文")`，並在 `LEn.java` 加對應英文；改完跑 `python android-overlay/tools/check_i18n.py`。語言選項在「設定 → 外觀 → Language / 語言」（跟隨系統：系統語言是中文就用中文，其他用英文）；選了就即時切換（重建畫面）；靜態字串陣列要存中文原文、顯示時才 `L.t(...)`，不能在靜態初始化裡先翻譯。
- 使用者用繁體中文，回覆簡短；一次一小步。
- 不提交截圖、記錄檔、金鑰、模型檔（`.gitignore` 已排除）。
- 敏感操作（付款、轉帳、刪除、安裝、傳訊息、登入…）一律跳確認框，確認框沒回應 = 不同意。
- 每完成一件事，更新 `HANDOFF.md` 的狀態表；✅ 只能是使用者在手機上實測過的。
