# HANDOFF — 給接手 AI 的交接文件

> 最後更新：2026-10-17。**接手後每做完一件事，請更新第 6 節「狀態表」與第 11 節「下一步」。**

## 0. TL;DR（English）

A beginner user is building a Gemini-like phone assistant on a **rooted Samsung S25+ (12 GB)**, running entirely
inside **Termux** (no PC). The assistant takes a typed command (voice later) and either (a) runs a whitelisted
root command (`svc bluetooth`, `input keyevent`, `monkey` ...), (b) uses a **local VLM** (Qwen3-VL-2B via llama.cpp)
to find UI coordinates and tap, or (c) uses the **Claude API** (Sonnet 5.5, escalating to Opus 5.5) as a
screenshot→action agent loop. Verified on device: root commands, local grounding + tap, Claude grounding.
`agent2.py` (Claude agent loop) works end-to-end on device: it can `open` apps via root, tap / swipe / type (clipboard paste) / press enter, asks the user for confirmation instead of refusing on sensitive steps, escalates Sonnet -> Opus when stuck and drops back afterwards, and lets the model choose how long to wait before the next screenshot. `assistant.py` routes typed commands: rule table -> Haiku 5.5 paraphrase router (+ step-budget estimate) -> `agent2.py`. A Termux:Widget desktop icon works. **Not verified yet**: Haiku router output after the max_tokens fix, tap-level confirm, `scroll`/`ask(manual)` actions, the running-status notification, voice input, web search. **Next big item (user request): a small companion Android app for a Samsung Now Bar live status + tap-ripple overlay — see `docs/ROADMAP_OVERLAY.md`.** The user plans to continue with **Claude Code** (see `CLAUDE.md`).
The user is a non-expert who needs **one small step at a time, in Traditional Chinese** (see section 3).

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
8. 價格資料來自第三方整理頁面，不是官方，引用時要註明並建議看 Console 的 Usage。
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
| `scripts/cfind.py` | 用 Claude 找座標（只畫圈，測準確度/token；支援 NOTHINK / EFFORT） |
| `scripts/ctest.py` / `cvision.py` | 測 API 連線 / 測識圖速度與 token |
| `scripts/look.sh` | 用本地模型找座標，只看不點，畫紅圈 |
| `scripts/snap.sh` | 等 5 秒後截圖縮圖（給 cfind/cvision 用） |
| `scripts/sizetest.sh` | 同一張圖測 360/540/720 的本地模型耗時與座標 |
| `scripts/widget/assistant.sh` | Termux:Widget 桌面入口（輸入框 -> assistant.py）（**未驗證**） |
| `CLAUDE.md` | 給 Claude Code 自動讀取的專案說明（指向本文件） |
| `docs/ROADMAP_OVERLAY.md` | Now Bar 即時狀態 + 點擊漣漪的計畫（尚未開發） |
| `install.sh` | 把腳本複製到 `~`（腳本互相用 `~/xxx` 呼叫，所以要放在家目錄） |

已放棄/刪除的舊腳本（不在 repo）：`agent.py`（本地模型自己規劃，常誤判完成或點錯，見第 7 節）、
`tap.sh`/`tap2.sh`（路徑腳本的前身）、`diag.sh`（一次性診斷）。

## 6. 狀態表

✅ = 使用者在自己手機上實測過；🟡 = 寫好但沒驗證；❌ = 確認不行/放棄。

| 項目 | 狀態 | 備註 |
|---|---|---|
| Termux + llama.cpp 編譯（`llama-mtmd-cli`） | ✅ | 需先 `pkg upgrade -y`，否則 cmake 報 CANNOT LINK |
| Qwen3-VL-2B Q4_K_M + mmproj 本地找座標 | ✅ | 17~22 秒；一定要 `-c 2048`，否則被 Killed |
| 座標為 0~1000 相對座標 | ✅ | 用紅/藍圈對照確認 |
| 截圖 `su -c screencap` 存到 Termux 家目錄 | ✅ | 要 `chown` 回 Termux 使用者 |
| `path.py` 路徑點擊（設定→連線→Wi-Fi）含回原畫面 | ✅ | 成功多次；曾因回去方式錯誤而失敗 |
| 直接 root 指令（svc bluetooth/wifi、input keyevent） | ✅ | 使用者回報「都正常」 |
| 開 App（monkey） | ✅ | 使用者回報「可以了」 |
| TTS 朗讀 `termux-tts-speak -s MUSIC` | ✅ | 要 `-s MUSIC`；預設通知音量沒聲音 |
| Termux 中文輸入 | ✅ | 左滑快捷鍵列，或別處打好再貼上 |
| Anthropic API 連線（`ctest.py`） | ✅ | |
| Claude 識圖 / 找座標（`cvision.py`、`cfind.py`） | ✅ | 約 2 秒；見 EXPERIMENTS |
| `assistant.py` 的「點X」視覺點擊 | 🟡 | 補丁有給，使用者沒回報結果 |
| Termux:Widget 桌面入口（`widget/assistant.sh`）+ `--once` | ✅ | 使用者回報可用；背景執行時確認框要先給 Termux:API「顯示在其他應用程式上層」權限 |
| `agent2.py` 動作 `open`（root 開 App）+ 動態 `wait` 秒數 | ✅ | 實測「打開 Chrome 搜尋今天天氣」：open→tap→type→enter→done 共 5 步；建議等待 3/1/1/4 秒合理；8962 入/181 出 ≈ $0.0197 |
| `agent2.py` 敏感/安裝任務一律跳確認框、開頭等待改 0 | ✅ | 使用者回報「可以了」；細項（manual 手動接手）未逐項測 |
| `assistant.py` Haiku 路由（藍芽=藍牙）與步數估計 | 🟡 | 第一次實測 Haiku 在 effort=low、max_tokens=60 下輸出 60 token 但沒有文字（steps 落回 8）；已改成 between_tools+low、max_tokens=400，**修正後尚未回報** |
| `agent2.py` 執行中常駐通知 + 停止鈕 + `AGENT_TOUCHES` | 🟡 | 剛寫好；三星 Now Bar 是否顯示未知 |
| `agent2.py` 基本迴圈：tap、done、結束刪截圖 | ✅ | 「進入Wi-Fi頁面」類任務；一輪 3 步約 $0.009（估算） |
| `agent2.py` swipe（左滑桌面） | ✅ | |
| `agent2.py` type（剪貼簿貼上，網址列輸入 hello world） | ✅ | 不受鍵盤語言影響；`input text` 在中文鍵盤會打出注音 |
| `agent2.py` 敏感任務確認框（任務層級，按「否」取消） | ✅ | `termux-dialog confirm` 可用 |
| `agent2.py` 模型升級 Sonnet -> Opus 5.5 並完成任務 | ✅ | `AGENT_TEST_ESCALATE=1` 測「進入Wi-Fi頁面」成功；全程 7197 入 / 783 出；不對 Opus 送 thinking 也沒 400 |
| `agent2.py` 點擊層級確認框、enter、scroll、ask 動作 | 🟡 | 沒測過 |
| 語音輸入 `termux-speech-to-text` | ❌（暫時） | 沒彈出視窗；原因未查明（見 TROUBLESHOOTING） |
| 聯網搜尋（web_search 工具） | 🟡 | 只查過資料沒寫程式 |
| `agent.py` 本地模型自己規劃 | ❌ | 見第 7 節 |
| 模型常駐（llama-server） | 未做 | 估計只省約 3 秒（載入），不值得 |

> 提醒：repo 裡的 `path.py` / `assistant.py` / `look.sh` 是我把使用者手機上「原版 + 多次補丁」合併重寫的版本，
> **合併後的版本沒有整份重新跑過**（語法檢查通過）。覆蓋手機上能用的檔案前，先備份，並重跑一次路徑測試。

## 7. 重要決策與理由

1. **規劃與定位分離**：本地 2B 模型自己決定下一步時，會太早回 `done`、點到同一處、`click`/`tap` 亂用；
   只問「找出 X」則很準。所以本地走「人給路徑 + 模型找座標」；規劃交給 Claude。
2. **Termux 必須在前景跑本地模型**：同一指令前景約 17 秒、背景 76 秒（Samsung 背景限速，不是發熱，
   也不是 App 被殺——程序編號前後相同）。解法：截圖後 `am start` 把 Termux 拉前景，推論完再回去。
   `agent2.py` 因為推論在雲端，不需要這招。
3. **回到原畫面**：桌面用 `input keyevent 3`；其他 App 用 `am start -n pkg/activity -f 0x10020000`
   （把既有畫面移到最前面），並用 `dumpsys window | grep mCurrentFocus` 確認真的回去，否則**不點擊**。
   `monkey` 與不加旗標的 `am start` 會重開新畫面，頁面會跑掉。
4. **解析要寬鬆、失敗就不點擊**：模型輸出格式會變（整數 / 0~1 小數 / `y:732` 少引號 / `bbox_2d`）。
5. **assistant.py 用白名單**：只有表裡的指令才執行；不要加付款/轉帳/刪除/傳訊息。
6. **Claude 用 540 寬、本地用 360 寬**：Claude 在小圖示（手電筒）上 540 為 9/9，360 為 9/11；540 每次多約 $0.001。
7. **Sonnet 5.5 用 `thinking={"type":"between_tools"}`**：關掉前置思考，輸出從約 190 token 降到約 18，
   更快更便宜（已驗證）。`{"type":"disabled"}` 在 Sonnet 5.5 會 400（官方文件說法，未實測）。
   `output_config.effort="low"` 有效但模型會把推算寫進答案，不穩定。
8. **模型升級策略**：先 Sonnet 5.5，卡住才升 Opus 5.5；升級的那一步做完就降回 Sonnet，整個任務最多升 2 次（MAX_ESC，控費）。Haiku 5.5 尚未試。（降回與 MAX_ESC 為 2026-10-17 新增，**未在手機實測**）
9. **敏感字眼：確認，不是拒絕**（使用者明確要求）。任務含付款/轉帳/刪除/密碼等字會跳確認框；之後 type/enter、
   以及要點「送出/登入/安裝…」時再問一次。限制：是否跳出取決於模型回報的 `what`；提示詞另要求模型不輸入密碼/驗證碼/卡號、
   遇到時回 `ask`。`path.py`/`assistant.py` 的敏感字檢查仍是直接拒絕（沒改）。
10. **輸入文字用剪貼簿貼上**：`input text` 是送按鍵事件，會被目前輸入法解讀（中文鍵盤會變注音）；貼上不經輸入法，
    中英文都行（使用者實測）。貼完會還原原本剪貼簿（盡力而為）。貼上內容會暫時經過剪貼簿，所以不讓模型輸入敏感內容。
    有些欄位（部分密碼欄/特殊 App）可能擋貼上。
11. **提示詞禁止點系統的「貼上」浮動按鈕/鍵盤剪貼簿建議**：那個按鈕會貼上當下剪貼簿的任何內容。
13. **成本是分模型算的**：Sonnet 5.5 每百萬 $2/$10，Opus 5.5 $4/$20（第三方價格，Opus 價格另有資料稱官方頁面一致，仍請以 Console Usage 為準）。
    `agent2.py` 結尾的「本次用量」已改成分模型計價。Opus 一步通常比 Sonnet 貴很多（價格 2 倍、輸出又含思考），所以升級只用一次。
12. **除錯用 `AGENT_DEBUG=1`**：每步畫面存到 `/sdcard/Download/agent_step_N.png`，能直接看模型看到什麼；含畫面內容，用完要刪。

### 7b. 2026-10-17 後半新增的功能與決策（除非註明，皆未逐項實測；使用者回報整體「可以了」）

14. **動態等待**：模型每個動作可回 `"wait":秒數`，agent2 夾在 0.5~6 秒後才截下一張；畫面沒變時多等 2 秒重拍再判定卡住。（**未實測**，要看模型估的秒數準不準）
15. **assistant.py 接 agent2**：指令表沒命中的句子交給 agent2.py；開頭加「ai」強制交給 Claude。（**未實測**）
16. **Haiku 路由**：規則沒命中時，`route()` 用 claude-haiku-5-5（`output_config.effort=low`，400 則退回無參數）判斷是否為指令表/App 的另一種說法（藍芽=藍牙），只能選表裡既有項目；否則交給 agent2。Haiku 是否吃 effort 參數、準確度、費用皆**未實測**（用 mock 測過流程）。
17. **同一任務混用指令與點擊**：agent2 新增 `open`（root monkey 開 App，檢查套件已安裝、禁止 com.android.vending）與 `cmd`（白名單 CMDS：wifi/藍牙/音量），提示詞要求能用就優先用，其餘畫面操作。（**未實測**；Samsung 套件名由模型猜，不確定時提示詞要它改點圖示）
18. **步數由 Haiku 估**：`route()` 回 agent 時附 steps（3~15，不含 open/cmd 能直接做的），assistant.py 把它當 agent2 的最大步數；句首 ai 走 agent_only 只估步數；估不到用 8。agent2.py 直接執行仍預設 8。（**未實測**，估得準不準待看）
19. **實測發現（2026-10-17）**：Haiku 路由只送 effort=low、max_tokens=60 時，輸出 60 token 卻沒有 text（推測全被思考吃掉），steps 落回預設 8。已改成先試 thinking=between_tools+effort=low、max_tokens=400，400 則逐步退回；印出結束原因與區塊類型供驗證（**修正未實測**）。同次 agent2 實測：open→tap→type→enter→done 共 5 步，8962 入/181 出，約 $0.0197；wait 建議值 3/1/1/4 秒合理。每步輸入約 1780 token，提示詞變長是主因，可考慮精簡。
21. **敏感一律問、不拒絕**：提示詞改為遇到付款/轉帳/刪除/安裝/傳訊息/登入/密碼等一律回 ask，不准 fail；ask 加 manual 欄位（true=請使用者自己輸入密碼/驗證碼，完成按是）。程式端：任務含 TASK_BLOCK 或 下載/安裝 等字，開始前確認；tap/type 再確認；用 open 開 Google Play 前一定確認。移除 INSTALL_TASK 閘門。注意：模型自己的安全判斷無法由程式覆蓋，若它仍回 fail 會當卡住處理並升級。**未實測**。
22. **開始前等待預設 0**：AGENT_WAIT 預設 0 直接截圖；在 Termux 打指令時第一張圖是 Termux，提示詞要求它用 open/home 離開。assistant.py 不再提示「5 秒內切換」（path.py 本地模型路徑仍是 5 秒）。**未實測**。
23. **執行中通知**：agent2 用 termux-notification 發常駐通知（id 7195，每步更新「第N步·點擊X」，輸入文字只顯示字數；有「停止」按鈕 -> pkill agent2.py，SIGTERM 已轉為正常結束以清理）。AGENT_NOTIFY=0 可關。結束發一則普通通知（id 7196）。**未實測**。三星 Now Bar 只吃 Live Updates 規格的通知，termux-notification 未必進得去（**未驗證**）；真要進 Now Bar 或畫點擊漣漪，需要自己的小 App（Android 16 promoted ongoing notification + SYSTEM_ALERT_WINDOW 懸浮層），尚未開發。AGENT_TOUCHES=1 會開系統「顯示點觸」，注入的 input tap 是否會畫圓點**未驗證**。

## 8. 實測數據

見 [`docs/EXPERIMENTS.md`](docs/EXPERIMENTS.md)。

## 9. 已知問題 / 未解決

見 [`docs/TROUBLESHOOTING.md`](docs/TROUBLESHOOTING.md)。未解決的重點：
- `termux-speech-to-text` 沒彈視窗（懷疑 Termux:API 缺「顯示在其他應用程式上層」/麥克風權限/沒有 Google 語音服務）。
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

## 11. 建議下一步（按優先順序）

1. **（使用者最想要）Now Bar 即時狀態 + 點擊漣漪**：詳細計畫在 [`docs/ROADMAP_OVERLAY.md`](docs/ROADMAP_OVERLAY.md)。
   先做「步驟 0 小實驗」（不用寫 App）：測 `termux-notification` 會不會進 Now Bar、`AGENT_TOUCHES=1` 有沒有圓點、查 One UI/Android 版本；
   結果決定要不要寫自製 Android App（GitHub Actions 雲端編譯 APK）。
2. 驗證剛改的東西：Haiku 路由（印出「路由(between_tools+low …)」那行）、常駐通知與停止鈕、`ask(manual)`、tap 層級確認、`scroll`、`enter`。
3. 降成本：每步輸入約 1780 token（圖約 840 + 提示詞約 900），可精簡提示詞；試 Haiku 5.5 當 agent 第一層（價格未查證）。
4. 小圖示準確度：加「連問兩次位置差太大就重問」。
5. **聯網搜尋**：agent 卡住（Opus 也失敗）才呼叫 `web_search_20250305`（`max_uses` 限 1~2），結果摘要寫進歷史；
   價格約每千次 $10 + token（第三方），每次約 NT$0.5~1.3（估算）。使用者問過但還沒決定要不要做。
6. **語音**：先解決 `termux-speech-to-text`；不行就試 whisper.cpp。
7. 擴充指令表與 APPS（問使用者要加什麼；套件名稱用 `su -c "pm list packages" | grep -i 名稱` 查）。

## 12. 接手後先跑這份檢查（在使用者手機的 Termux）

```bash
ls ~/llama.cpp/build/bin/llama-mtmd-cli ~/model/*.gguf     # 引擎與模型在不在
wc -c ~/.claude_key && head -c 10 ~/.claude_key; echo       # Key 有沒有（不要 cat）
python ~/ctest.py                                           # API 通不通
su -c "wm size"; su -c "dumpsys window | grep mCurrentFocus"   # root 與前景偵測
termux-tts-speak -s MUSIC "測試"                              # 朗讀
```
