# 路線圖：AI 在背景操作，不影響前台（研究與實驗計畫，尚未開發）

> 狀態：**階段 0 已在使用者手機（S25+、Android 16）驗證成功（2026-10-18，見下方「階段 0 實測結果」）；階段 1 的實驗版已加進 `agent2.py`（`AGENT_BG=1`），尚未實測。** 提出者：使用者（2026-10-17），參考努比亞 NaviX Ultra（搭載豆包手機助手）。

## 1. 想要什麼
AI 在「背景」自己截圖、自己點，使用者同時在前台做別的事；使用者也能看 AI 在做什麼。

## 2. 查到的資料（網路搜尋，二手資訊）
- 努比亞 NaviX Ultra + 豆包手機助手：官方說 AI 操作時使用者仍可正常使用手機（IT之家 7 月報導）；**沒有公開實作細節**。V2EX 上有人推測是「虛擬螢幕」，作者自己說沒有真機、沒有 Root，純屬推測。
- 它是**系統級權限**，不是普通 App；上線後微信、淘寶、多家銀行 App 出現相容問題，豆包後來主動關掉了銀行/支付類 App 的 AI 操作。
- scrcpy 3.0 的 `--new-display`（搭配 `--start-app`）可以建立虛擬螢幕並把 App 開在上面，只用 shell 權限，Android 11 以上。→ 「在虛擬螢幕開別人的 App」這個原語在 Android 上是做得到的。我們有 root，權限比 shell 大。

## 3. 能不能做到？（我的判斷，標明是推測）
- **有機會**，但不確定：三星 One UI 對「App 開在第二個螢幕」可能有限制；部分 App 會拒絕或顯示不正常；要自己寫用到系統隱藏介面的程式（工程不小）。
- 所以先做**小實驗**確認方向，不好就不要投入。

## 4. 階段
**階段 0：可行性實驗（需要手機，不寫 App；以下指令名稱依我對 AOSP 的印象，未驗證）**
1. 建立模擬的第二螢幕（開發人員選項「模擬輔助螢幕」同一個設定）：
   `su -c 'settings put global overlay_display_devices "1080x2340/420"'`（畫面上會出現一個浮動的小視窗＝第二螢幕）
2. 查它的 display id：`su -c "dumpsys display" | grep -iE "mDisplayId|OverlayDisplay|Display [0-9]+"`
3. 把設定開在第二螢幕：`su -c "am start --display <ID> -n com.android.settings/.Settings"`
4. 看小視窗裡有沒有出現設定；再試 `su -c "input -d <ID> tap <x> <y>"`，前台操作有沒有被干擾。
5. 清理：`su -c "settings delete global overlay_display_devices"`。
要回報：步驟 3 開得起來嗎、畫面正常嗎、步驟 4 點得到嗎、前台有沒有被影響、常用 App（Chrome、LINE、設定）各自的表現。

**階段 1（實驗成功才做）**：自己寫一個在 root 下執行的小程式（`app_process` 跑本 App 裡的類別），用 `DisplayManager` 建立虛擬螢幕並用 `ImageReader` 拿畫面（`screencap -d` 對這種螢幕不一定能用），提供「截圖」「在指定螢幕點擊」給 `agent2.py`；用 `am start --display`、`input -d` 操作。

**階段 2**：浮動小視窗讓使用者看 AI 在背景做什麼（可拖曳、可縮小）；有敏感操作時才把它拉到前台請使用者確認。

## 5. 風險
- 銀行/支付/驗證類 App 常拒絕在虛擬螢幕執行（豆包也遇到），這類任務應該改回前台並請使用者確認。
- 輸入法、剪貼簿貼上在虛擬螢幕上的行為未知（我們的 `type` 動作靠剪貼簿貼上）。
- 同時間前台與背景兩邊都在用相機/麥克風等獨佔資源會衝突。

## 6. 階段 0 實測結果（2026-10-18，使用者的 S25+，root）
| 步驟 | 指令 | 結果 |
|---|---|---|
| 1 建模擬螢幕 | `settings put global overlay_display_devices "1080x2340/420"` | ✅ 螢幕上出現小視窗，是三星 DeX 風格的桌面（有工具列、App 圖示） |
| 2 查編號 | `dumpsys display` | ✅ 系統編號 **12**（手機本身是 0） |
| 3 在第二螢幕開 App | `am start --display 12 -n com.android.settings/.Settings` | ✅ 設定開在第二螢幕，主螢幕沒被換掉；App 是**浮動視窗**，不是全螢幕 |
| 4 單獨操作第二螢幕 | `input -d 12 swipe 540 1200 540 600 300` | ✅ 設定清單捲動，主螢幕完全不受影響（第一次沒反應是因為起點落在視窗外） |
| 5 截圖（系統編號） | `screencap -d 12` | ❌ `Display Id '12' is not valid` |
| 6 查截圖用編號 | `dumpsys SurfaceFlinger --display-id` | ✅ 第二螢幕有自己的 SurfaceFlinger 編號（一長串數字，名稱「第 1 個重疊效果」，Virtual display），**跟系統編號不同** |
| 7 截圖（SF 編號） | `screencap -d <SF編號> -p ...` | ✅ 1080x2340，內容確實是第二螢幕的桌面（含設定視窗） |
結論：模擬第二螢幕可以「在背景開 App、單獨操作、單獨截圖」，主螢幕不受影響。**這證明了前面說的有機會，不代表 AI 能穩定完成任務**——還沒測 Claude 在這個螢幕上實際操作、輸入文字（剪貼簿貼上）、各 App 的相容性。
實驗版：`AGENT_BG=1 python ~/agent2.py "..."`（`agent2.py` 自動找/建第二螢幕；截圖 `screencap -d`、點擊/滑動/按鍵 `input -d`、開 App 用 `am start --display`；背景模式不畫漣漪）。
2026-10-18 第一次 `AGENT_BG=1` 實測：開設定成功（在第二螢幕），但「往下捲動」沒生效（起點 y=70% 落在小視窗外）→ 改開 App 時要求全螢幕（`--windowingMode 1`，不支援就退回），背景捲動起點改在畫面中間偏上（待測）。
已知限制/待確認：浮動視窗太小時 AI 可能看不清（可點視窗的最大化鈕）；`home` 在第二螢幕的行為未知；螢幕上會一直有那個小視窗（用 `settings delete global overlay_display_devices` 移除）。

2026-10-18 第二次 `AGENT_BG=1` 實測：開設定 + 往下捲動成功（捲動起點 (540,1170)->(540,585) 落在小視窗內），3 步、約 $0.0057，主螢幕不受影響。`--windowingMode 1` 沒生效（視窗仍是小浮動視窗，也沒報錯）。第二螢幕重建後，「設定」會被系統放回去，要先 `am force-stop` 才是乾淨狀態。

2026-10-18 續測（設定 App，540 寬）：tap（進「連接」頁）成功；type（設定搜尋框輸入 wifi）成功，代表剪貼簿貼上（`input -d <id> keyevent 279`）在第二螢幕可用。背景模式目前**不畫漣漪**（漣漪由主螢幕的無障礙懸浮層畫，座標對不上第二螢幕）；想在第二螢幕上畫要改 App（用 `createDisplayContext` 對第二螢幕加無障礙懸浮層，**是否可行未驗證**）。

2026-10-18 第二螢幕漣漪（實作完成，**待手機實測**）：`ripple`/`swipe` 指令加選用參數 `display`（系統螢幕編號，0/沒給=主螢幕，舊版 App 相容）；App 用 `createDisplayContext(Display)` 取得第二螢幕的 WindowManager 加 `TYPE_ACCESSIBILITY_OVERLAY`；`hide`（截圖前）會清掉所有螢幕的漣漪。`agent2.py` 檢查 App 診斷有 `bgripple=1` 才畫。若系統拒絕，logcat tag `PhoneAI` 會有 `second-display addView` 錯誤，agent2 會安靜地不畫。

2026-10-18 Chrome 在第二螢幕實測成功（540 寬）：open、點網址列、type 中文（剪貼簿貼上）、enter、辨識搜尋結果，5 步完成。Chrome 強制關閉後重開仍會還原上次分頁，測試任務要用沒搜過的詞，否則 AI 一看畫面已完成就直接結束。

2026-10-18 面板開關（待手機實測）：助理面板光球上方多一個「背景模式：關/開」藥丸（點一下切換，存在偏好 `bg_on`），主畫面也有同樣的勾選框；開著時 `CommandSender` 送出任務前先 `export AGENT_BG=1 AGENT_WIDTH=540`（`assistant.py` 會把環境變數傳給 `agent2.py`）。確認框仍出現在主螢幕。立即完成的指令（開藍牙等）不受影響。

2026-10-18 解析度問題（使用者提出）：第二螢幕是 1080x2340，視窗小時只佔約 70% 寬，再縮成 432/540 寬送給 AI，字會很小。使用者回報 AI 執行中曾自己把視窗放大成全螢幕（推測是 AI 照提示詞點了最大化鈕，未確認）。網路搜尋沒找到 `am start --windowingMode` 在三星 One UI 第二螢幕的可靠說明（只知道 Android 13 起顯示器本身是全螢幕、task display area 才是 freeform；非 resizable 的 App 在 freeform 顯示器上可能被改成 freeform）。對策（由簡到繁）：
1. 提示詞要求 AI 開 App 後先點最大化鈕（已改，待測）。
2. 查 `am task help` / `cmd window help` 有沒有能直接設 windowing mode 或 resize 的指令（待使用者在手機查）。
3. 沒全螢幕時依視窗邊界裁切截圖再縮放，等於把有效解析度拉高（要能取得視窗邊界，`dumpsys activity activities` 可能有；工程較大，視 1、2 結果再決定）。

2026-10-18 解析度對策 2 實測成功：`am help` 有 `task resize <TASK_ID> <左> <上> <右> <下>`（只對自由視窗模式有效）。`am stack list` 輸出格式為 `RootTask id=… bounds=[0,0][1080,2340] displayId=14` 加上每行 `taskId=N: 套件/畫面 bounds=[l,t][r,b]`；使用者手動 `am task resize 1554 0 0 1080 2214`（設定視窗原本 [81,297][999,1467]）→ 視窗變大且 App 重新排版。`agent2.py` 背景模式現在開 App 後自動做這件事（`maximize_bg`，底部留 126 px 給 DeX 工具列，這個數字是估算；`AGENT_BG_MAX=0` 可關），提示詞改成「視窗還是很小才點最大化鈕」。**程式自動化待手機實測**。

2026-10-18 自動放大實測：Chrome 開在第二螢幕後，程式印出「把視窗拉大（task 1570 → 1080x2214）」，AI 看得到大畫面（4338 入、約 $0.0077）。注意該次 Chrome 還原了上次的「高雄天氣」分頁，AI 一看畫面已完成就直接結束，沒測到輸入。
任務結束清理（待手機實測）：`bg_cleanup` 在結束時（含失敗/中斷）把這次在第二螢幕開的 App 關掉：解析 `am stack list` 找出含這些套件的 RootTask，執行 `am stack remove <id>`；沒移掉、且該 App 沒在主螢幕使用時才 `am force-stop`。`AGENT_BG_KEEP=1` 保留視窗。
實測（Chrome 搜「台南美食」）：自動拉大、輸入、Enter 都成功；結束時 `am stack remove` 沒有移掉視窗，由 `force-stop` 補上，視窗確實消失。

2026-10-18 隱形虛擬螢幕（使用者要「平常看不到小視窗」；系統的模擬螢幕視窗做不到：它是系統畫的，手動拖到螢幕外會被擋回來，必須完整露出）。方向 B：用 scrcpy 的伺服器（`scrcpy-server-v5.0.1`，使用者已下載到 `~/scrcpy-server.jar`，SHA-256 `764eb6f79811d5211fe9df341120882ba9994c7a61b897d7bf3fb662e53bc536`，與官方 release 的 SHA256SUMS.txt 一致）以 `new_display=1080x2340/420` 建立沒有視窗的虛擬螢幕。伺服器選項取自官方 Options.java：`new_display`、`vd_destroy_content`、`vd_system_decorations`、`tunnel_forward`、`raw_stream`、`control`、`cleanup`；單獨執行方式取自官方 develop.md（版本引數必須與 jar 一致）。實驗腳本 `scripts/vdisplay.py`（建立、找新螢幕編號、開設定、截圖、清理），**未在手機實測**；root 身分被拒時用 `--shell`（改 uid 2000）。通過後才接進 agent2 的背景模式，並在 Now Bar 加「顯示預覽／關閉」。

2026-10-18 `vdisplay.py` 實測（S25+，root 身分，不用 `--shell`）：scrcpy 伺服器 v5.0.1 在 One UI（Android 16）上**可以建立虛擬螢幕**：`New display: 1080x2340/420 (id=15)`；`am start --display 15` 開設定成功，且**預設就是全螢幕**（任務 bounds=[0,0][1080,2340]，不需要 `am task resize`）；`screencap -d <SF編號>` 截圖成功（160 KB）。第一次實測曾因 Termux 的 LD_LIBRARY_PATH 汙染 app_process 而連結失敗，已修。收尾 bug：只 terminate 外層 sh，真正的 app_process 沒死、虛擬螢幕沒消失，已改成 `exec` + 斷開串流 + `pkill`（待再測）。待確認：螢幕上真的沒有小視窗（使用者回報中）；觸控 `input -d`、輸入、各 App 是否正常；最後再接進 agent2。

2026-10-18 vdisplay.py 修正後再測：螢幕上**沒有任何小視窗**，結束後螢幕消失（`['0']`）。已接進 agent2 背景模式（待手機實測）：`vd_start()` 用 `vdisplay.py --serve`（root）建立虛擬螢幕並把 `{"id","sf"}` 寫到 `/data/local/tmp/vdisplay.json`；`vd_stop()` 建立 `/data/local/tmp/vdisplay.stop` 通知收掉（保險：逾時 15 分鐘、`pkill`）；虛擬螢幕上 App 預設全螢幕，所以不做 `am task resize`，結束時整個螢幕收掉、App 一起消失。沒有 jar 或建立失敗就退回系統模擬螢幕。**下一步**：Now Bar 的「顯示預覽／關閉」（預覽可用 `screencap -d` 每秒截圖顯示在 App 的浮動視窗）。

2026-10-18 隱形虛擬螢幕接進 agent2 後實測成功（Chrome 搜「屏東景點」，沒有任何視窗）。預覽（待手機實測）：agent2 用新指令 `{"cmd":"vd","sf":"<截圖編號>"}` 告訴 App 背景螢幕出現（空字串＝消失）；App 重發通知，contentIntent 改成 `ACTION_PREVIEW`（沒有背景螢幕時仍是開 App），並多一個「預覽」按鈕；`RippleAccessibilityService.togglePreview` 用 `TYPE_ACCESSIBILITY_OVERLAY` 開 `PreviewView`（寬為螢幕 42%、可拖曳、標題列「✕ 關閉」），內容每 0.5 秒由 root 執行 `screencap -d <SF編號> -p` 更新；agent2 連線斷掉或背景螢幕收掉時預覽自動關。預覽只看，觸控不會傳到背景螢幕。

2026-10-18 預覽實測：點 Now Bar 出現小視窗並更新，結束後隱形螢幕消失。比例與手機相同；使用者覺得偏長、希望關閉方式改成像小米小窗「從底部上滑消失」→ 改了：寬度 36%，移除「✕ 關閉」，改成從視窗下方 30% 上滑（跟手、漸淡、超過 80dp 才關、否則彈回），拖曳其他區域移動視窗，底部有把手。待手機實測。

2026-10-18 預覽改成即時影片（待手機實測）：使用者說預覽卡。原因：截圖法每次要起一個 root 程序、PNG 壓縮/解壓，約每秒 1~2 張。改法：scrcpy 伺服器本來就輸出 H.264（原本被丟掉），`vdisplay.py --serve` 的 `StreamProxy` 切成 NAL 並記住 SPS/PPS 與最近關鍵幀起的資料，經 `127.0.0.1:47196` 轉送（第一行送 App 的 token，只有 root 與 App 讀得到），新預覽先收備份才不用等關鍵幀；App 的 `H264Stream` 用 MediaCodec 硬體解碼直接畫到 TextureView（預覽視窗加 `FLAG_HARDWARE_ACCELERATED`）。伺服器多加選項：`video_bit_rate=4000000 max_fps=30 video_codec_options=i-frame-interval=2`（官方 Options.java 有這些鍵，但沒在這支手機驗證過）。連不上或 5 秒內沒畫面就退回截圖模式。

2026-10-18 H.264 即時預覽實測：順了很多、不卡。使用者要求：更新率至少 60fps（`max_fps` 30→60、碼率 8 Mbps；是否真的有 60fps 取決於背景螢幕實際產出幀率與手機解碼，**未驗證**）；預覽視窗右下角拖曳改變大小（仿小米小窗，維持 1080:2340 比例、記住大小；待手機實測）。

2026-10-18 使用者回饋：預覽「跟手機同比例但還是太長」「上滑動畫用透明的不夠細膩」。改法（待手機實測）：背景虛擬螢幕尺寸可調，預設改 `1080x1800/420`（`AGENT_BG_SIZE`，`vdisplay.py --size=…`）；JSON 與 `vd` 指令帶 w/h，App 的預覽用 `OverlayService.vdW/vdH` 算比例、H264 解碼器格式也用它。上滑關閉動畫改為移動「視窗本身」（`WindowManager.updateViewLayout`，因為視窗內容移出視窗邊界會被裁掉）＋微縮（到 90%）；過 80dp 門檻時震動回饋；放手依速度（>1800px/s 也算）加速飛出螢幕上緣並縮到 82%；沒滑夠用 OvershootInterpolator 回彈。不再用變淡。

2026-10-18 使用者回報：比例舒服多了；但沒有震動、動畫還是不夠順、底部操作區太大。原因與改法（待手機實測）：震動——`performHapticFeedback` 在 `FLAG_NOT_FOCUSABLE` 的浮動視窗上常常不生效，改直接用 `Vibrator`（`EFFECT_CLICK/TICK`），補 `VIBRATE` 權限；動畫頓——上一版每一幀都 `updateViewLayout` 重排視窗，改成手勢一開始把視窗（透明根 `previewHost`，卡片貼底）往上撐高一次，之後上滑／飛出／回彈都用 `View.animate()` 動卡片的 translationY/scale，GPU 合成，不碰視窗；區域——上滑關閉只剩卡片底部 30dp，縮放只剩右下 44dp，其餘全部拖曳移動。

2026-10-18 動畫重做（待手機實測）：使用者說震動與操作區 OK，但動畫手感差，要參考小米。判斷兩個結構問題：①手勢開始時把視窗撐高一次會有一格卡頓；②動畫是固定時間/曲線，不隨手速。新架構：全螢幕不攔觸控的視窗放卡片（`PreviewView`，只畫，位置用 translation）＋同大小的透明觸控視窗（`PreviewTouch`，只收觸控、手指按下後即使稍微落後卡片也持續收到事件）；關閉/回彈用自寫的彈簧（質量 1、`c = 2ζ√k`、Choreographer 逐幀、1/240s 子步長），初速度 = 放手瞬間手指速度；判斷用預測落點；縮放/圓角隨進度連動；往下拉橡皮筋。小米實際做法未知（沒有原始碼），這是依常見原則實作。

2026-10-18 背景接手（待手機實測）：預覽右上角「接手」→ 接手模式（變「完成」，多一顆「輸入」）。視窗中間的觸控換算成背景螢幕座標，`RootInput` 以 root 執行 `input -d <id> tap|swipe`（長按 = 原地 700ms 的 swipe）；參數都是整數，沒有使用者文字進 shell。文字輸入走 `TakeoverInputActivity`：使用者在主螢幕鍵盤打字 → 放進剪貼簿（標 IS_SENSITIVE）→ `input -d <id> keyevent 279`（PASTE）→ 1.5 秒後 `clearPrimaryClip`。AI 與使用者不衝突：agent2 每步開始前問 `paused`（App 回 yes/done/no）；`ask manual` 時 agent2 送 `takeover on` 並在確認框回答後送 `takeover off`。
