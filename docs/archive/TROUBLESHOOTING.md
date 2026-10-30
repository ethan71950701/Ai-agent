# 問題與解法（都是這個專案實際遇過的）

> 📦 **歷史紀錄（2026-10-30 歸檔）：** Termux／Python 時期的問題與解法。App 現在不需要 Termux，指令與路徑多半已不存在，只作參考。

## Termux / 編譯
- **開啟 Termux 跳「Android 應用程式相容性 / 16 KB」彈窗**：無害，按「不要再顯示」。
- **`cmake`: `CANNOT LINK EXECUTABLE ... cannot locate symbol`**：只做了 `pkg update`。執行 `pkg upgrade -y`，必要時
  `pkg reinstall libc++ cmake`，再編譯。
- **Termux 打不了中文**：Termux 預設是「直接傳按鍵」模式。(1) 在快捷鍵列（ESC/CTRL/方向鍵）上**往左滑**到變成空白輸入框，
  點它再用中文輸入法，按 Enter 送出；(2) 或在別處打好、複製，再長按終端機「貼上」。
- **`~/storage/downloads` 不存在**：沒執行過 `termux-setup-storage`。或用 root：`su -c "cp 來源 /sdcard/Download/"`。
- **指令被重複貼兩次黏在一起**（如 `…6python ~/agent.py…`）：貼之前看輸入列只有一行；清畫面用 `clear`。

## 截圖
- `screencap` 存到 `/sdcard` 後 Termux 讀不到/複製失敗：直接存到 Termux 家目錄並改擁有者：
  `su -c "screencap -p ~/s.png; chown $(id -u):$(id -g) ~/s.png"`（腳本裡用完整路徑 `/data/data/com.termux/files/home/`）。
- **`magick: no decode delegate` / 檔名多了 `cd`**：兩行指令貼在一起。分開貼。

## 本地模型（llama.cpp）
- **`Killed`**：上下文預設太大 + 原圖太大。加 `-c 2048`，並把截圖縮到寬 360~540。
- **Termux 在背景時慢 4 倍（17 秒 -> 76 秒）**：不是發熱、也不是 App 被殺。解法：截圖後用
  `su -c "am start -n com.termux/com.termux.app.TermuxActivity"` 把 Termux 拉前景再推論。
- **回到原 App 後頁面變回首頁**：不要用 `monkey`，也不要不加旗標的 `am start`（會重開新畫面）。用
  `am start -n pkg/activity -f 0x10020000`（把既有畫面移到最前），桌面用 `input keyevent 3`，並用
  `dumpsys window | grep mCurrentFocus` 確認；沒回去就不點。
- **模型回答格式不固定**：整數 0~1000、0~1 小數、`y:732` 少引號、`bbox_2d:[x1,y1,x2,y2]`、`click` vs `tap`。
  解析要寬鬆：小數 <= 1.5 乘 1000；取最後一個 `{...}`；`click` 當 `tap`。
- **同一問題有時給離群座標**：小模型的隨機性。重要操作要二次確認；找不到就回 `{"found":false}`（提示詞要給這個出口）。
- **本地模型自己規劃（agent.py）失敗**：太早 `done`、點到上一層頁面、連續返回。改成「人給路徑，模型只找座標」。

## 聲音 / 語音
- **`termux-tts-speak` 沒聲音**：預設走通知音量（勿擾或音量 0 就靜音）。用 `-s MUSIC`；也檢查 `termux-volume`。
  系統的「文字轉語音輸出」要有引擎（Google 語音服務）。
- **`termux-tts-engines` 沒輸出**：當時其實是音量串流問題；`termux-battery-status` 能回傳代表 Termux:API 是通的。
- **`termux-speech-to-text` 沒彈視窗（未解決）**：嘗試方向（都未證實）：Termux:API 的麥克風權限、
  「顯示在其他應用程式上層」、Google App 是否存在/更新、`su -c "logcat -d | grep -iE 'speech|recogni|termux'"`。
  備案：whisper.cpp。

## Anthropic API
- **401 `invalid x-api-key`**：Key 內容有問題。檢查 `wc -c ~/.claude_key`（約 100+；4 代表還是測試用的 `123`）、
  `head -c 12`、隱藏字元（`tr -d '[:print:]' < ~/.claude_key | wc -c` 應為 1）。使用者遇到的是
  **個人 Key（`sk-ant-usr-` 開頭）**，改建「未連結 + 預設工作區」的 Key 後成功。官方文件說沒綁工作區的 Key 每個請求要帶
  `anthropic-workspace-id` 標頭（我們的腳本不帶）；另有說法稱預設工作區沒有 ID。沒有逐項驗證。
- **`read -s` 貼上後長度 0**：剪貼簿是空的（Console 只顯示一次 Key，複製後別再複製其他東西）。
- **`KeyError: 'text'`**：`content[0]` 可能是 `thinking` 區塊。把所有 `type=="text"` 的區塊串起來；`max_tokens` 別設太小。
- **Sonnet 5.5 的 thinking 參數**：`{"type":"disabled"}` 會 400；要用 `{"type":"between_tools"}`。思考深度用
  `output_config.effort`（low/medium/high/xhigh/max）。Opus 的行為沒驗證，所以 `agent2.py` 對 Opus 不送 thinking。
- 偶發「跑很久」：腳本計時只算 API 呼叫；用 `time` 量整體，單次整體 `real` 約 2 秒，沒再重現。

## 桌面入口（未驗證）
- `termux-dialog` 不彈：Termux:API 開「顯示在其他應用程式上層」。
- 點小工具沒反應：Termux:Widget 電池設無限制；腳本要 `chmod +x` 且在 `~/.shortcuts/tasks/`。紀錄在 `~/assistant.log`。

## agent2.py（Claude 迴圈）
- **`input text` 在中文（注音）鍵盤打出怪符號**：它送的是按鍵事件，會被目前輸入法解讀。改用剪貼簿貼上
  （`termux-clipboard-set "文字"` 然後 `su -c "input keyevent 279"`），不受鍵盤語言影響（中文也行）。
- **跑到一半 agent 說「目前畫面是 Claude 聊天 App，沒有網址列」並 fail**：使用者在執行途中切換了 App。
  agent 照眼前的畫面做事。測試時不要切換、不要碰手機。先用 `AGENT_DEBUG=1` 看每步畫面再下結論。
- **升級後馬上「達到最大步數」，Opus 沒跑到**：舊版把升級那一步算進上限。已修：記錄升級次數，迴圈多留空間。
- **Chrome 點網址列後跳出「貼上」浮動按鈕**：這是 Android 的系統按鈕。提示詞已要求模型不要點它（會貼上任意剪貼簿內容）。
  `type` 動作貼上時不需要它。
- **`type` 顯示「成功」但字沒出現**：「成功」只代表指令沒報錯。要看畫面（`AGENT_DEBUG=1`）；欄位沒焦點或擋貼上都可能。
- **輸入 token 比單純找座標高（約 1400 vs 約 940）**：提示詞變長（動作清單）加上歷史；這是對數字的推測，沒拆開量。
