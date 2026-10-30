# CLAUDE.md — 給 Claude Code 的專案說明

這個 repo 是「用一個 App（PhoneAI Overlay）做的 AI 手機助手」，在 Android 手機上用 root、Shizuku 或普通（無障礙）三種模式之一執行。
**App 就是全部**：不需要 Termux、不需要 Python 腳本。**接手前先讀 [`HANDOFF.md`](HANDOFF.md)**（目標、架構、狀態表、決策、安全規則、下一步）。

## 架構（現在）
- `android-overlay/`：Android App（Java，Gradle，minSdk 33）。
  - 助理面板（`AssistantSheet`）：文字或語音輸入。
  - 路由（`Assist` 關鍵字 → `Router` Haiku 5.5 判斷）：固定指令／開 App／聊天（`ChatEngine`，有記憶、鬧鐘、行事曆、三星提醒、地圖、撥號、互傳檔案、唯讀 shell 等工具）／操作畫面（`AgentLoop`）。
  - 自動操作迴圈（`AgentLoop` + `AgentPrompt`）：無障礙元件清單（必要時截圖）→ 模型選動作 → 用 root／Shizuku／無障礙執行（點擊、滑動、拖曳、批次、輸入、`reminder_create`…）；便宜模型先做，卡住升級；巨集（`Macros`）與經驗（`Memory` lessons）。
  - AI 服務：Claude（預設）或 OpenAI 相容的服務（Gemini、DeepSeek…）；`LlmConfig` 管「便宜」「看圖」兩個欄位，`LlmAdapter` 轉格式，出口是 `ChatEngine.post`。
  - 執行模式：`Root`／`ShizukuBackend`／`ExecMode`／`Phone`（普通模式）。
  - 記憶（`Memory`）、定時與短問題（`Schedule`）、通知與朗讀（`Notify`）、語音辨識（`SherpaEngine`，SenseVoice 離線模型）、互傳檔案（`Transfer*`）。
- `windows-transfer/`：互傳檔案的 Windows 版（Python，協定與手機版相同）。
- `docs/`：`APP_FUNCTIONS.md`（Android 16 AppFunctions，三星提醒怎麼呼叫）、`NO_ROOT.md`（三種模式對照）、`FILE_TRANSFER.md`、`PUBLISH_CHECKLIST.md`；`docs/archive/` 是 Termux 時期與已完成路線圖的歷史紀錄。
- API Key：App 資料夾的 `claude_key`、`key_<網址>`（權限 600）。**絕不要把 Key 貼到聊天或印出來。**

## 怎麼建置
- 雲端可用 Android SDK + Gradle 先編譯確認：`cd android-overlay && gradle assembleDebug`。
- 單元測試（純邏輯，不需要手機）：`cd android-overlay && gradle testDebugUnitTest`，測試在 `app/src/test/`；CI 編譯前會先跑。
- 正式 APK 由 GitHub Actions 產生（`.github/workflows/android.yml`），使用者從 Release 下載安裝。

## 規則
- 使用者看得到的改動，要在 `CHANGELOG.md` 的 `## Unreleased` 加一條「中文｜English」（加在**行首**的 `## Unreleased` 標題底下；檔案開頭的說明文字裡也提到這個字，不要加到那裡）。
- 發布時 CI 會自動把 `## Unreleased` 的內容移到以版本號命名的標題底下（例如 `## 1.0.1183`，commit 回 `main` 並帶 `[skip ci]`），所以 `Unreleased` 只留下一次的新改動；不用手動移。
- 編譯通過的改動**直接合併到 `main`**（流程與 CHANGELOG 衝突的處理見 `HANDOFF.md` 第 11b 節）（使用者已同意，不用每次再問）；GitHub Actions 只在 `main` 自動編譯並發布。
- 介面有繁體中文與英文：使用者看得到的字串一律寫 `L.t("中文原文")`，並在 `LEn.java` 加對應英文；改完跑 `python android-overlay/tools/check_i18n.py`。語言選項在「設定 → 外觀 → Language / 語言」（跟隨系統：系統語言是中文就用中文，其他用英文）；選了就即時切換（重建畫面）；靜態字串陣列要存中文原文、顯示時才 `L.t(...)`，不能在靜態初始化裡先翻譯。
- 使用者用繁體中文，回覆簡短；一次一小步。
- 不提交截圖、記錄檔、金鑰、模型檔（`.gitignore` 已排除）。
- 敏感操作（付款、轉帳、刪除、安裝、傳訊息、登入…）一律跳確認框，確認框沒回應 = 不同意。
- 每完成一件事，更新 `HANDOFF.md` 的狀態表；✅ 只能是使用者在手機上實測過的。
