# HANDOFF 舊版（Termux／Python 時期，2026-10-17～22 以前）

> 這是 `HANDOFF.md` 舊版的第 0～5 節與第 8～12 節原文，已過時：裡面提到的 `scripts/`、`assistant.py`、`agent2.py`、`~/.claude_key`、Termux 檢查指令都已不存在。保留作歷史參考。現行內容見根目錄的 [`HANDOFF.md`](../../HANDOFF.md)；舊版第 6、7 節（狀態表與決策）仍在根目錄的 HANDOFF.md 裡。

# HANDOFF — 給接手 AI 的交接文件

> 最後更新：2026-10-17（Now Bar、漣漪、App 操作介面、搜尋、快取都已實測；README/SETUP 已同步）。**接手後每做完一件事，請更新第 6 節「狀態表」與第 11 節「下一步」。**

> ⚠️ **現況（2026-10-22）：App 已經是全部，不再需要 Termux 與 Python 腳本。** `scripts/`、`install.sh`、`tests/` 都已刪除，助理面板、路由、操作畫面迴圈（`AgentLoop`）、記憶、排程、通知與朗讀、語音辨識都在 `android-overlay/` 裡。
> 現行架構與規則請看 [`CLAUDE.md`](CLAUDE.md)。**本文件下面提到 Termux、`assistant.py`、`agent2.py`、`~/.claude_key`、`update_app.py` 的段落都是歷史紀錄**（當時的實測結果仍有參考價值，但指令與路徑已經不存在）。
> 底欄／透鏡液態玻璃：著色器移植自使用者的 Samsung Glass 模組（設計參考 KernelSU 的 FloatingBottomBar／Lens），在 `GlassBar.java`；按住時透鏡與底欄放大、加速度驅動的彈簧形變、圖示只在透鏡下變色。使用者在手機上確認過效果。

## 0. TL;DR（English）

A beginner user uses a **personal AI phone assistant app, PhoneAI Overlay** (`android-overlay/`, Java and Gradle) on a **rooted Android phone**. The app is the whole product: no Termux, no Python scripts.
Text or voice input (SenseVoice offline speech recognition, or the system recognizer) goes through a router. Fixed root commands run directly; "open app" and chat are handled locally; screen tasks run an automation loop (screenshot, Claude Sonnet 5.5 picks the next action, root executes tap, swipe, drag, multi-touch, typing). Sensitive actions always show a confirmation dialog.
Other parts: a glass bottom bar (`GlassBar`, a capsule shader ported from the user's Samsung Glass module), memory and to-dos, simple scheduled questions, notifications and text-to-speech, in-app updates from GitHub Releases, and a Chinese and English interface (`L.t` plus `LEn`).
Releases are built by GitHub Actions on `main` (the user has no PC). `CHANGELOG.md` holds the bilingual release notes.
The user is a non-expert who needs **one small step at a time**, and speaks Traditional Chinese (see section 3).

## 1. 專案目標

使用者想做「類似 Gemini 的語音助手」：說（或先用打字）一句話，AI 直接操作自己已 root 的手機
（點螢幕、開 App、開關設定），必要時能聯網搜尋回答。要求：**全部在手機上執行（Termux），不依賴電腦**。

## 2. 使用者與環境

- 手機：Samsung Galaxy S25+（12GB RAM），已 root（Magisk 一類；確切方式沒問過）。螢幕解析度 1440x3120。
- Termux 從 GitHub 安裝（不是 Play 商店），另有 Termux:API；Termux:Widget 的步驟給過，使用者**沒回報是否完成**。
- 鍵盤 Gboard。Termux 裡預設不能打中文（解法見 TROUBLESHOOTING）。
- 使用者是新手，不熟 Linux。以繁體中文溝通；常用語音輸入，所以**訊息可能是簡體中文，回覆仍用繁體**。
- 使用者已買 Anthropic API 額度（金額沒確認，建議 $5 起跳）。Key 存在手機的 `~/.claude_key`（**不在 repo**）。
  使用者之前拿到 `sk-ant-usr-…` 開頭的個人 Key 會 401；改建「Linked account=未連結 + 預設工作區」的 Key 後
  `ctest.py` 成功（沒確認新 Key 的前綴）。
- 預算態度：很在意成本，希望簡單任務用便宜模型、卡住才升級。

## 3. 與使用者互動的規則（很重要）

1. **一次只給一小步**，每步說明「這在做什麼、為什麼、怎麼看成功/失敗」。使用者明講過「有些聽不懂」。
2. 手機螢幕小：回覆要短、少表格；指令用單一 code block。**長指令貼上時常被重複貼兩次黏在一起**
   （出現過 `…6python ~/agent.py…` 這種錯），要提醒「貼之前看一下只有一行」。
3. 每步都要有「成功的樣子」和「失敗就貼這個給我」。使用者會貼終端機輸出或截圖。
4. **不要要求使用者貼 API Key**；提醒不要截圖 Key、不要寫進腳本。用 `head -c 10`、`wc -c` 驗證，不要 `cat`。
5. 修改既有檔案時，用「帶 assert 的 Python 修補腳本」（找不到要改的地方就中止，不亂改），並讓使用者貼回結果。
6. 測試完提醒清理截圖（`rm ~/s.png ~/s_small.png`、`/sdcard/Download/` 裡的測試圖）。截圖可能含通知欄/私人畫面。
7. **不確定的事要說不確定**。這個專案裡好幾次我（前一個 AI）推測錯誤（例如以為是「設定被殺掉」其實是回去的方式不對；
   以為 Key 開頭是 `sk-ant-api`）。給推測時標明「這是推測，需實測」，並設計小實驗驗證。
8. 價格：Claude 與 DeepSeek 已照官網價格頁（使用者 2026-10-29 提供截圖：Claude Haiku 5.5 $0.10/$0.50（提示詞 ≤10 萬 token）、Sonnet 5.5 $2/$10、Opus 5.5 $4/$20，快取寫入 1.25 倍、Haiku 快取讀取 $0.01、Sonnet／Opus 快取讀取 $0.10／$0.20；DeepSeek flash 尖峰 $0.30 入／$1.20 出／快取命中 $0.006，離峰半價）。OpenAI GPT-6 系列也照官網截圖（Astra $10/$50、Sol $2/$10、Terra $1/$5、Luna $0.10/$0.50，快取讀取各為 $1／$0.10／$0.05／$0.01）；Gemini Flash 系列照官網頁（2026-10-29：3.8／3.6 Flash $0.75/$3.75（2027-01-01 起翻倍）、3.5 Flash-Lite $0.30/$2.50、3.1 Flash-Lite $0.25/$1.50、2.5 Flash-Lite $0.10/$0.40；`-latest` 別名頁面沒寫對應哪版，假設最新）；只有舊的 GPT-4o 仍是第三方粗估，在 `Usage.guessPrice`，使用者可在用量頁改。其餘價格引用時仍要註明並建議看 Console 的 Usage。
9. **請使用者測試 agent 時，叫他不要切換 App、不要碰手機**：agent 照眼前畫面做事。曾有一次使用者在執行途中切到 Claude App，
   agent 第 4 步看到的是 Claude 聊天畫面而回 fail，我們花了好幾輪才查出來。先問「中途有沒有切 App」，再怪程式。
10. 觀察不到的事不要編。例如曾推測「AI 去點了貼上按鈕」，後來看紀錄根本沒有那個動作。要用 `AGENT_DEBUG=1` 看實際畫面。

## 4. 架構

```
輸入（桌面圖示 Termux:Widget 輸入框 / Termux 打字；語音尚未）
   |
assistant.py
   |-- 「開 X」（APPS 表）           -> monkey 啟動 App                    （免費、瞬間）
   |-- 指令表 COMMANDS 命中          -> su -c "svc ..."/"input keyevent"   （免費、瞬間）
   |                                   或 path.py（本地模型路徑點擊）
   |-- 「點/按/選 X」               -> path.py（本地模型視覺點擊）
   '-- 其他 -> route()：Haiku 5.5（低思考）判斷是不是上面某項的另一種說法（藍芽=藍牙）
                 |-- 是 -> 照表執行（只能選表裡有的）
                 '-- 否 -> agent2.py "<任務>" <Haiku 估的最大步數>
                           句首加「ai」跳過規則，直接 route(agent_only) 估步數後交給 agent2

agent2.py（Claude 迴圈：截圖 -> 一次呼叫決定動作+座標+建議等待秒數 -> 執行）
   動作：open（root 開 App）/ cmd（白名單 wifi、藍牙、音量）/ tap / scroll / swipe / type / enter / back / home / ask / done / fail
   模型：Sonnet 5.5；卡住升 Opus 5.5 做一步後降回（最多升 2 次）
   保護：敏感一律跳確認框（不拒絕）、最大步數、畫面沒變/連點偵測、通知上有「停止」鈕、結束刪截圖
   回報：常駐通知顯示第 N 步與正在做的動作

path.py   本地 Qwen3-VL 2B（llama.cpp，約 16~22 秒/步，360 寬圖，0~1000 相對座標）
```

核心做法：**把「決定要點什麼」和「找座標」拆開**。小模型一次做兩件事（規劃 + 定位）很不準；
告訴它名字再找座標就很準。Claude 則兩件事一次做（`agent2.py`）。

## 5. 檔案清單

| 檔案 | 用途 |
|---|---|
| `scripts/assistant.py` | 打字版指令助手（規則式）。`--once "文字"` 給小工具用 |
| `scripts/path.py` | 本地模型照路徑 `"設定,連線,Wi-Fi"` 逐步找座標並點擊，含回到原畫面檢查 |
| `scripts/agent2.py` | Claude 版自動迴圈 + 模型升級 + 確認框（**部分驗證**，見狀態表） |
| `scripts/vdisplay.py` | 用 scrcpy 伺服器建立「沒有視窗」的背景虛擬螢幕（`--serve` 給 agent2 用；不帶參數是實驗/診斷，`--app=<套件>` 可指定測哪個 App） |
| `scripts/selftest.py` | 一鍵自我檢查（見狀態表） |
| `scripts/update_app.py` | 一行更新：拉程式＋下載安裝最新 APK（見狀態表） |
| `scripts/audit_secrets.py` | 公開 repo 前的清查（只讀；`--words` 可加個人字眼） |
| `scripts/memory.py` | 記憶：待辦與筆記（本機 `~/memory.json`，chmod 600，不呼叫 AI）；assistant.py 先問它 |
| `scripts/cfind.py` | 用 Claude 找座標（只畫圈，測準確度/token；支援 NOTHINK / EFFORT） |
| `scripts/ctest.py` / `cvision.py` | 測 API 連線 / 測識圖速度與 token |
| `scripts/look.sh` | 用本地模型找座標，只看不點，畫紅圈 |
| `scripts/snap.sh` | 等 5 秒後截圖縮圖（給 cfind/cvision 用） |
| `scripts/sizetest.sh` | 同一張圖測 360/540/720 的本地模型耗時與座標 |
| `scripts/widget/assistant.sh` | Termux:Widget 桌面入口（輸入框 -> assistant.py）（**未驗證**） |
| `CLAUDE.md` | 給 Claude Code 自動讀取的專案說明（指向本文件） |
| `docs/archive/ROADMAP_OVERLAY.md` | Now Bar 即時狀態 + 點擊漣漪的計畫與目前進度 |
| `android-overlay/` | **PhoneAI Overlay** Android App（Java、無 AndroidX）：懸浮層畫漣漪/滑動軌跡 + Live Update 常駐通知（含停止鈕） |
| `.github/workflows/android.yml` | GitHub Actions 編譯 debug APK（Artifacts: `phoneai-overlay-apk`） |
| `scripts/overlay.py` | Termux 端跟 App 溝通（127.0.0.1:47195 + root 才讀得到的 token）；`python ~/overlay.py` 單獨測試 |
| `scripts/applist.py` | 讀 `~/apps.tsv`（由 App 匯出的「顯示名稱\t套件名稱」）並用名稱找套件 |
| `scripts/overlay_install.sh` | 用 root 安裝 APK 並直接開懸浮層/通知權限 |
| `install.sh` | 把腳本複製到 `~`（腳本互相用 `~/xxx` 呼叫，所以要放在家目錄） |

已放棄/刪除的舊腳本（不在 repo）：`agent.py`（本地模型自己規劃，常誤判完成或點錯，見第 7 節）、
`tap.sh`/`tap2.sh`（路徑腳本的前身）、`diag.sh`（一次性診斷）。



## 8. 實測數據

見 [`docs/archive/EXPERIMENTS.md`](docs/archive/EXPERIMENTS.md)。

## 9. 已知問題 / 未解決

見 [`docs/archive/TROUBLESHOOTING.md`](docs/archive/TROUBLESHOOTING.md)。未解決的重點：
- （已繞過）`termux-speech-to-text` 沒彈視窗（懷疑 Termux:API 缺「顯示在其他應用程式上層」/麥克風權限/沒有 Google 語音服務）。
  備案：whisper.cpp 本地辨識（沒試過，速度未知）。
- Claude 找小圖示偶爾離群（360 寬約 2/11 錯）；`agent2.py` 還沒加「連問兩次位置差太大就重問」。
- 不同 App 回到前景的行為可能不同，只在「桌面」和「設定」驗證過。

## 10. 安全規則（不要放寬）

- API Key 只放 `~/.claude_key`（`chmod 600`），**不進 repo、不貼聊天、不截圖**；`.gitignore` 已排除。
- Claude 版每一步會把**整張截圖傳到 Anthropic**；本地版不外傳。告知使用者不要在銀行/私訊/含個資畫面使用 Claude 版。
- **敏感一律「問使用者」，不是拒絕**（使用者明確要求）：任務含付款/轉帳/刪除/密碼等字、或下載/安裝，開始前跳確認框；
  tap 的 what 含送出/登入/安裝等、type 輸入敏感內容、用 `open` 開 Google Play 前再跳；模型被要求遇到敏感事回 `ask`
  （`manual:true` = 請使用者自己輸入密碼/驗證碼）。確認框沒回應 = 不同意。
  限制：模型自己的安全判斷程式改不了，它若仍回 fail 會被當成卡住（升級 Opus）。`path.py` 的敏感字檢查仍是直接拒絕（沒改）。
- 一律有最大步數、畫面沒變偵測、連點同位置偵測；結束會刪截圖。
- 之後若加語音：誤聽風險更高，確認機制要保留。

> 2026-10-18 實測回報（續）：按送出後要等好幾秒 Now Bar/光暈才出現 → 原因是它們要等 agent2 連上 App，而前面還有 App→Termux→assistant.py→Haiku 路由→agent2 啟動→root 查套件/啟動服務/讀 token。修法：`CommandSender` 送出成功就發 `ACTION_BEGIN` 讓 App 立刻顯示 Now Bar「準備中」與光暈（15 秒沒人接手就自己收）；`overlay.connect()` 先走快路徑（只讀 token 就連）；立刻做完的指令（開藍牙/開 App）由 `assistant.ui_done()` 通知 App 收掉。🟡 未實測。語音：系統日誌顯示預設辨識服務是離線版（AiAi）缺 zh-TW 語言包 → 12，連帶 11；改成優先用系統目前設定的語音服務（使用者切到三星後實測可用）。
> 2026-10-18 實測回報：語音已正常；步數估太少（中間出錯一兩步就做不完）→ `assistant.py` 新增 `step_budget()`：Haiku 估的步數 ×2+2，至少 10、最多 25，估不到 12（`agent2.py` 直接執行預設也改 12）；光暈顏色太斷層 → 改相近色票與多層淡出，並做成波浪起伏。

### 10b. 安全檢查結果（2026-10-17，讀程式碼，不是滲透測試）
- 沒有找到「文字 → root 指令」的注入路徑：送進 `su` 的字串只有固定指令、白名單 `CMDS`、`input tap/swipe` 的整數座標，以及先過 `^[A-Za-z0-9_.]+$` 檢查的套件名稱；`input text` 用 `shlex.quote`；
  App 端 `viaRoot` 的單引號跳脫用 sh 模擬測過（含 `$`、反引號、`;`）。
- App 對外開放的元件只有：桌面入口、需要系統 `BIND_*` 權限才能綁的服務（磁貼、助理、無障礙）、一個只會回錯誤的語音辨識存根；`OverlayService`/`AskActivity`/`ConfirmActivity` 不對外。測試 `tests/test_offline.py::Manifest` 會守住這點。
- Termux↔App 連線只綁 `127.0.0.1`，要 token（App 私有資料夾，只有 root/App 自己讀得到）。限制：同機任何 App 都能連上去猜 token，沒有限制連線數與次數（128-bit 隨機，實務上猜不到；沒做防暴力）。
- 無障礙服務 `canRetrieveWindowContent=false`，`onAccessibilityEvent` 是空的：不讀畫面內容。
- 已知殘留風險（沒處理）：(1) 任何持有 Termux `RUN_COMMAND` 權限的 App 都能在 Termux 跑指令（Termux 本身的機制，只授權給本 App）；
  (2) 通知設成鎖定畫面公開，鎖屏看得到「第 N 步 · 點擊『…』」這類動作描述（輸入文字只顯示字數）；
  (3) 敏感確認取決於模型回報的 `what`，模型自己不回報就不會問（見第 10 節）；(4) `debug.keystore` 在 repo 裡（私人 repo 才放，見 11b）；
  (5) 三星測試開關 `enable_notification_nowbar_test=1` 的副作用（見 SETUP 第 10 節）。
- 還沒做：**原生確認框在手機上的實測**（見第 11 節第 1 項）。

## 11. 建議下一步（按優先順序）

**目前（2026-10-22）：** 任務歷史、用量上限、分享到助理、記憶搜尋匯出匯入、Markdown 排版、桌面小工具都已合併，狀態表是 🟡，等使用者在手機上實測後再改成 ✅。下面這份清單是較早的 Termux 時期紀錄，參考用。

0. **改完程式先跑離線測試**：`python -m unittest discover -s tests`（21 項，雲端與手機都能跑，不花 API）。
1. **更便宜的設定**：`AGENT_FIRST=haiku` 已實測（單次）：Haiku 5.5 點擊座標連續點錯、沒完成任務，升級 Sonnet 又因換模型要重寫快取而多花（見 EXPERIMENTS），**不建議**，預設維持 Sonnet。
   已試：預設改 `AGENT_WIDTH=432`（2026-10-18，省約 25% 輸入 token，小圖示實測可認）；要更準可設 540。還沒試的：或只把「不需要找座標」的步驟（open/cmd/search）交給便宜模型。
2. **補測還沒測的動作**：`ask(manual)`（要使用者親自輸入密碼/驗證碼的那種）、點擊層級確認（`scroll`、`enter`、`ask` 已通過）。
3. **背景操作（進行中）**：階段 0 已成功；`AGENT_BG=1` 實驗版待手機實測（見 [`docs/archive/ROADMAP_BACKGROUND.md`](docs/archive/ROADMAP_BACKGROUND.md) §6）。
4. **擴充白名單指令**：`assistant.py` 的 `COMMANDS` 與 `agent2.py` 的 `CMDS`（問使用者要加什麼；不要加付款/轉帳/刪除/傳訊息類）。
5. **需要手機實際紀錄才能校準（不要憑空改）**：①「done」那一步偶爾 8～9 秒（看 App 紀錄頁裡該步的 `Claude: … (秒, 入/出)`，有沒有重試或輸出特別長）；②地圖／導航的固定 6 秒等待（要在紀錄頁收集載入中與載入完的「畫面豐富度」樣本）；③拖曳座標不準（要有失敗那次的整段紀錄）。
6. 已完成、不用再做：Now Bar、漣漪、語音（用 Google 的語音視窗，`termux-speech-to-text` 那條路不需要了）、聯網搜尋、提示詞快取、用名稱開任何 App、App 內任務用量紀錄。

## 11b. 開發環境備註（Claude Code 雲端）

**合併到 `main` 的流程（避免 CHANGELOG 衝突）：** CI 每次發版都會往 `main` 推一個「Archive changelog … [skip ci]」提交，所以 `main` 常常比工作分支超前。順序要固定：
1. 先從遠端抓最新的 `main`（`git fetch origin main`），再把 `FETCH_HEAD` 合併進工作分支（`git merge FETCH_HEAD`）。這個倉庫沒有 `origin/main` 追蹤分支，所以不能直接用 `origin/main`；也不能用本地舊的 `main`，否則會漏掉 CI 的歸檔提交。
2. 在工作分支上改程式、CHANGELOG、HANDOFF，提交並推送。
3. 切到 `main`，用 `git merge --ff-only claude/…` 快進（不要用一般合併，免得又衝突），再推送 `main`。
4. 如果第 3 步快進失敗，表示 `main` 在這期間又被推進了：回到第 1 步重來。
不要在 `main` 上直接改 CHANGELOG；Unreleased 的內容只在工作分支上加。

- **APK 簽名金鑰固定**（2026-10-17）：`android-overlay/debug.keystore`（密碼 android，別名 androiddebugkey），`app/build.gradle` 的 `signingConfigs.debug` 使用。
  原本 GitHub Actions 每次都產生新金鑰，簽名不同，只能先解除安裝才能裝（root 授權、無障礙設定都會掉）。雲端兩次獨立編譯的簽名指紋相同（SHA-256 `1a2c4298…cf49`）。
  **這個 repo 是私人的**才這樣放；若改成公開，這把金鑰就公開了，要先換掉並重裝一次。從舊版升級到固定金鑰那一次仍須先解除安裝，之後就能直接覆蓋安裝。

- 雲端容器可以從 `dl.google.com` 下載 Android command-line tools，在 scratchpad 裝 `platforms;android-36`、`build-tools;36.0.0`
  後用 `/opt/gradle` 的 Gradle 8.14.3 直接 `gradle assembleDebug` / `lintDebug`（AGP 8.11.1）。所以改 App 後可以先在雲端編譯確認，再推給 GitHub Actions。
- Android 16 SDK 裡沒有 `Notification.Builder.setRequestPromotedOngoing`，App 改用 extra `android.requestPromotedOngoing=true`
  （AndroidX 的做法；是否生效看 `promoted=`）。`setShortCriticalText`、`ProgressStyle`、`canPostPromotedNotifications` 在 API 36 有。

- **無障礙設定會被清掉**：使用者手機有 SCENE（com.omarea.vtools）也用無障礙服務，重裝 App 後我們的服務沒留在
  `enabled_accessibility_services`（原因是推測，未證實）。要手動寫一次（保留 SCENE）：
  `su -c "settings put secure enabled_accessibility_services com.omarea.vtools/com.omarea.vtools.AccessibilitySceneMode:com.phoneai.overlay/com.phoneai.overlay.RippleAccessibilityService"`。
- **Now Bar 已通（2026-10-17）**：靠反編譯 SystemUI 找到條件，詳見 ROADMAP 8e。不需要 LSPosed、不冒用別人套件名。三星 `ongoingActivityNoti.*` 欄位反而不能加（會落到只認設定清單的舊路徑）。
- 重裝 App 後要重設：無障礙服務（會被清掉）與測試開關（`overlay_install.sh` 設開關，無障礙要手動寫，見上）。

## 12. 接手後先跑這份檢查（在使用者手機的 Termux）

```bash
ls ~/llama.cpp/build/bin/llama-mtmd-cli ~/model/*.gguf     # 引擎與模型在不在
wc -c ~/.claude_key && head -c 10 ~/.claude_key; echo       # Key 有沒有（不要 cat）
python ~/ctest.py                                           # API 通不通
su -c "wm size"; su -c "dumpsys window | grep mCurrentFocus"   # root 與前景偵測
termux-tts-speak -s MUSIC "測試"                              # 朗讀
```
