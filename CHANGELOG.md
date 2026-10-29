# 更新說明 · Changelog

每一條寫成「中文｜English」，App 依介面語言只顯示對應的那一半；Release 說明會用整段 `## Unreleased` 內容。
Each entry is written as "中文｜English"; the app shows the half that matches the interface language. The release notes use the whole `## Unreleased` section.

## Unreleased

## 1.0.1295

- 「元件清單模式（省錢）」改成預設開啟（設定 → 執行任務時，可以關掉）；已經設定過的人不受影響｜"Element list mode (saves money)" is now on by default (Settings → While running a task, can be turned off); people who already set it are not affected

## 1.0.1294

- 新增直接切換深色模式、勿擾、自動旋轉、定位的指令；元件清單也能讀拉下來的通知欄、快速設定；助理面板蓋住畫面時先收起再讀；查詢指令的輸出縮短，省 token｜Direct commands for dark mode, Do Not Disturb, auto-rotate and location; the element list can read the pulled-down notification shade and quick settings; the assistant panel is hidden first when it covers the screen; query command output is shortened to save tokens

## 1.0.1293

- 修正：Haiku 把額度花在思考、沒留下答案時，先放大額度再問一次，不再直接升級 Sonnet；App 還在載入、讀不到元件時先等一下再讀｜Fixes: when Haiku uses its whole budget thinking and leaves no answer, it is asked again with a bigger budget before escalating to Sonnet; when an app is still loading and no elements can be read, it waits and reads again

## 1.0.1292

- 元件清單標出「已聚焦」的輸入框，AI 不會再為了確認焦點而多點一次搜尋欄；Haiku 的回覆看不懂而改用 Sonnet 時，任務過程會顯示 Haiku 回覆的開頭｜The element list marks the focused input box so the AI no longer taps the search bar twice just to check focus; when Haiku's reply is not understood and Sonnet is asked again, the task details show the start of that reply

## 1.0.1291

- 修正：輸入後改用輸入法的「搜尋／前往」鍵送出（Google 地圖搜尋框不吃假的 Enter）；AI 偶爾回成 XML 工具呼叫格式時也能看懂；新增直接打開通知欄、快速設定的指令｜Fixes: text entry now submits with the keyboard's search/go action (Google Maps ignored the fake Enter key); replies in the XML tool-call format are understood; new commands to open the notification shade and quick settings directly

## 1.0.1290

- 設定新增「操作完成後的回答卡片」：自動（預設）、一律顯示、一律不顯示；聊天問答的回答不受影響｜New setting "Answer card after operating the phone": auto (default), always show, never show; chat answers are not affected

## 1.0.1289

- 調亮度、開關 Wi-Fi／藍牙、調音量這類指令，成功後若任務已完成就直接結束，不再多問一次 AI，更快也更省｜Commands like brightness, Wi-Fi, Bluetooth and volume now finish the task right after they succeed when nothing else is needed, without asking the AI once more, so they are faster and cheaper

## 1.0.1288

- 新增直接調亮度的動作（不用拖滑桿）；Haiku 的回覆看不懂時自動用 Sonnet 重問一次，錯誤訊息會附上回覆開頭；相機這類本來不會變的畫面不再被誤判成卡住｜Added a direct brightness action (no slider drag); when a Haiku reply cannot be understood it retries once with Sonnet and the error shows the start of the reply; screens that normally do not change, like the camera, are no longer mistaken for being stuck

## 1.0.1287

- 通用經驗改成依任務相關程度挑 6 則（不夠相關的才用最新的補），不再只取最新 4 則，舊經驗也用得上｜General lessons are now picked by relevance to the task (6 of them, topped up with the newest), instead of only the newest 4, so older lessons are still used

## 1.0.1286

- 內建「計算機」經驗（小膠囊不是已輸入的內容），只在用到計算機時才帶進提示；沒有 open 的任務，經驗也會記在目前前景的 App 上，不再存成通用｜Built-in calculator lesson (suggestion chips are not entered text) that is only loaded when the calculator is involved; lessons from tasks that did not open an app are now saved against the foreground app instead of as general ones

- 元件清單實驗：清單標出輸入框／按鈕／文字與位置，避免把計算機上方的建議膠囊當成已輸入；可用 batch 一次連點多個編號；畫面連續沒變時改用截圖＋Sonnet｜Element-list experiment: the list now marks input boxes, buttons and text with positions, so suggestion chips above a calculator are not mistaken for entered text; batch can tap several numbers at once; if the screen does not change for several steps it falls back to a screenshot with Sonnet

## 1.0.1284

- 任務一開始要先開某個 App 時，由判斷路由的 Haiku 5.5 直接指定，程式先打開它，不用再花一步請模型決定，更快也更省｜When a task starts by opening an app, the routing step (Haiku 5.5) now names it and the app opens it first, saving a model step so tasks are faster and cheaper

## 1.0.1283

- 元件清單實驗：第一步讀不到元件（桌面）時，先用 Haiku＋截圖，需要座標時才升級 Sonnet，省下第一步的花費｜Element-list experiment: when the first step reads no elements (home screen), it now uses Haiku with a screenshot first and only escalates to Sonnet when coordinates are needed, saving the first step's cost

## 1.0.1282

- 修正：AI 只給 App 名稱（沒給套件名稱）時會打不開，現在會依名稱自己找已安裝的 App；任務過程也顯示這一步有沒有用元件清單｜Fix: when the AI gives only an app name (no package), the app now looks the package up by name instead of failing; task details also show whether each step used the element list

## 1.0.1281

- 任務過程每一步多顯示快取寫入與讀取的 token，可以看出快取有沒有命中｜Task details show cache-written and cache-read tokens for each step, so you can see whether the cache is hit

## 1.0.1279

- 任務過程顯示每一步用的模型、輸入與輸出 token，以及估價（第三方價格，以 Console 為準）｜Task details now show each step's model, input and output tokens, and an estimated cost (third-party prices; check the Console)

## 1.0.1278

- 省錢實驗「元件清單」：開關打開後不截圖，改給 Haiku 看畫面元件編號，點不到時自動改回截圖＋Sonnet（預設關閉）｜Cost experiment "element list": when on, the assistant reads numbered on-screen elements instead of screenshots and uses Haiku, falling back to screenshots and Sonnet when a number is not found (off by default)

## 1.0.1277

- 問「我的車在哪」這類自己的事，助理會先查記憶直接回答，不再打開地圖去找｜Questions about your own things (e.g. where your car is) now check memory first instead of opening maps

## 1.0.1276

- 設鬧鐘改成直接設定好，不再打開時鐘畫面｜Alarms are now set directly without opening the clock screen

## 1.0.1275

- 設鬧鐘改用 root 打開時鐘，修正三星時鐘擋住「設定鬧鐘」請求的問題｜Alarms now open the clock through root, fixing Samsung clock blocking the "set alarm" request

## 1.0.1274

- 「連不上 AI」現在會顯示真正的原因（例如每日用量上限、網路錯誤、還沒設 API Key），方便找出問題｜"Cannot reach the AI" now shows the actual reason (for example the daily usage limit, a network error, or no API key set yet), so the cause is easier to find

## 1.0.1273

- 新增「指令一覽」頁（功能 → 進階）：列出固定指令、快速問題、AI 工具、定時任務與手動操作｜New "Command list" page (Features → Advanced): lists fixed commands, quick questions, AI tools, scheduled tasks and manual actions

- 助理多了「查時間」工具，問幾點、星期幾或要算幾小時後時，會先查手機的時間｜The assistant has a get-time tool: it checks the phone clock when asked the time, weekday, or for an offset like "in one hour"

## 1.0.1271

- 助理知道現在的時間，設鬧鐘支援「幾分鐘後／幾小時後」（例如一小時後）｜The assistant now knows the current time, and alarms support "N minutes from now" (e.g. in one hour)

## 1.0.1270

- 助理可以設鬧鐘：說「幫我設 7 點的鬧鐘」，會打開手機的時鐘，在時鐘裡按儲存即可（用系統的標準做法，不用背景服務）｜The assistant can set an alarm: say "set an alarm for 7" and it opens the phone clock to save (uses the standard system method, no background service)

## 1.0.1268

- 互傳檔案的服務只在互傳頁打開、或正在傳輸時運行，離開頁面就停止，不再一直在背景；移除沒有在用的遠端指令（Telegram）｜The file transfer service now runs only while the transfer page is open or a transfer is running, and stops when you leave; removed the unused Telegram remote commands

## 1.0.1267

- 拖動玻璃滑桿時，底欄會即時更新（不再有時候不刷新）｜Dragging a glass slider now updates the bottom bar live (no more occasional stale frames)

## 1.0.1266

- 底欄暗度滑桿修正為真的會改變暗度（之前的深色層是固定值）；底欄暗度加了滑桿（設定 → 底欄玻璃（進階）→ 底欄暗度），拖動即時生效｜Added a bottom bar darkness slider (Settings → Bottom bar glass (advanced) → Bottom bar darkness), live

## 1.0.1265

- 底欄暗度加了滑桿（設定 → 底欄玻璃（進階）→ 底欄暗度），拖動即時生效｜Added a bottom bar darkness slider (Settings → Bottom bar glass (advanced) → Bottom bar darkness), live

## 1.0.1264

- 手機更新說明不再列出電腦版的改動（電腦版條目標記為「【電腦版】」）｜The phone update notes no longer list computer-app changes (those entries are marked 【電腦版】)

- 【電腦版】外觀重做：Windows 11 風格深色主題、分頁（裝置／找檔案／記錄）、標題與狀態一直顯示、傳輸進度固定在下方｜Windows app redesigned: Windows 11-style dark theme, tabs (Devices / Find files / Log), title and status always visible, transfer progress pinned at the bottom

## 1.0.1263

- 【電腦版】外觀重做：Windows 11 風格深色主題、分頁（裝置／找檔案／記錄）、標題與狀態一直顯示、傳輸進度固定在下方｜Windows app redesigned: Windows 11-style dark theme, tabs (Devices / Find files / Log), title and status always visible, transfer progress pinned at the bottom

- 底部玻璃欄調亮一點、不要那麼黑｜The bottom glass bar is a bit lighter and less dark

- 【電腦版】修正「檢查更新」和各種錯誤對話框不出現的問題；關閉視窗會縮到右下角繼續運作；可以勾選「開機自動啟動」｜Windows app: fixed the update check and error dialogs that never appeared; closing the window minimizes to the tray and keeps running; new Start with Windows option

## 1.0.1262

- 遠端指令（Telegram 機器人）：在群組用「@機器人 <裝置名稱> 開／關／狀態」控制這台手機的 Tailscale，只回應自己的名字；機器人 token 只存在這台手機｜Remote commands (Telegram bot): in a group, send "@bot <device name> on/off/status" to switch this phone Tailscale; each device answers only to its own name; the bot token stays on this phone

- 遠端指令（Telegram 機器人）：在群組用「@機器人 <裝置名稱> 開／關／狀態」控制這台手機的 Tailscale，只回應自己的名字；機器人 token 只存在這台手機｜Remote commands (Telegram bot): in a group, send "@bot <device name> on/off/status" to switch this phone Tailscale; each device answers only to its own name; the bot token stays on this phone

## 1.0.1261

## 1.0.1260


## 1.0.1259

- 外網區塊顯示 Tailscale 開／關（看記下的位置附近是不是有顏色），可以檢查狀態或切換｜The remote section shows whether Tailscale is on or off (by checking color at the saved spot), with check and toggle buttons

## 1.0.1258

- 設定 Tailscale 點擊位置改成直接在畫面上點一下記錄，不用輸入座標｜Setting the Tailscale tap position is now done by tapping it on screen, no coordinates to type

## 1.0.1257

- 互傳頁新增「打開 Tailscale 並連線」：用 root 打開 Tailscale、點一下設定的位置，再回到 PhoneAI；點擊位置可以在設定裡調整（需要 root）｜New "Open Tailscale and connect" button on the transfer page: with root, it opens Tailscale, taps the configured spot, and returns to PhoneAI; the tap position is adjustable (requires root)

## 1.0.1256

- 連線時會帶上自己的 Tailscale 位址，對方記下來；在區網配對過，換成行動網路也能連回去｜Requests now carry each device's own Tailscale address, which the other side remembers, so a pairing made on the LAN still works after switching networks

## 1.0.1255

- 對方連上來時，會自動記下它的位址（區網或 Tailscale），不用兩邊都手動輸入 IP｜When a paired device connects, its address (LAN or Tailscale) is remembered automatically, so IPs need not be typed on both sides

## 1.0.1254

- 手動輸入 IP 可以連續輸入多個「.」（之前第二個點就按不下去）｜Manual IP entry now allows several dots, so a full address like 100.x.x.x can be typed

## 1.0.1253

- 連線順序改成：先區網搜尋（在家裡最快），找不到才用 Tailscale 位址，最後才用上次的區網位址；區網與 Tailscale 位址分開記住｜Connection order is now: LAN discovery first (fastest at home), then the Tailscale address, then the last LAN address; LAN and Tailscale addresses are remembered separately

## 1.0.1252

- 手動輸入 IP 的鍵盤改成有「.」的數字鍵盤，才能輸入 Tailscale 位址（100.x.x.x）｜Manual IP entry now shows a decimal point key so Tailscale addresses (100.x.x.x) can be typed

- 電腦版檢查更新修正：打包後改用內建憑證連線，失敗的原因會顯示在視窗上｜Windows app update check fixed: uses bundled certificates when packaged; failure reasons show in the window

## 1.0.1251

- 外網連線（Tailscale）：伺服器接受 100.64.0.0/10 位址；記住對方的位址，區網搜不到時直接用它連線｜Remote connection via Tailscale: servers accept 100.64.0.0/10 addresses; each device remembers the peer address and uses it when LAN discovery finds nothing

## 1.0.1250

- 瀏覽與搜尋結果的列恢復原本的細高度（保留按下去的水波紋）｜Browse and search result rows are back to their original thin height (press ripple kept)

## 1.0.1249

- 文字不再可以選取（選取會吃掉點擊）；搜尋結果列加大、按下去有水波紋｜Text is no longer selectable (selection was swallowing taps); search result rows are taller with a press ripple

## 1.0.1248

- 瀏覽頁的資料夾列加大、按下去有水波紋，比較容易按到｜Browse page rows are taller and show a ripple when pressed, so they are easier to tap

## 1.0.1247

- 瀏覽頁的毫秒數改成量從點下去到畫面出現的總時間，方便找出慢在哪一段｜The browse page timing now measures the total time from tap to the list appearing

- 電腦版檢查更新：找不到更新檔時會明確提示，不再顯示「已經是最新版」｜Windows app update check: when the update file cannot be found it says so, instead of reporting the app is up to date

## 1.0.1246

- 電腦版回應不等待合併（TCP_NODELAY）；手機瀏覽頁顯示連線與讀取花了多少毫秒，方便找出慢在哪裡｜Windows app responds without Nagle delay; the phone browse page shows connect and read times in milliseconds to find where the delay is

- 電腦版列資料夾更快：直接使用目錄列舉附帶的大小與時間，不再為每個檔案重新開啟｜Windows app folder listing is faster: uses the size and time from the directory listing instead of reopening every file

- 電腦版可以把檔案直接拖進視窗，再選要傳給哪一台裝置｜Windows app: drag files into the window, then choose which device to send them to

## 1.0.1245

- 瀏覽電腦資料夾變快：電腦版不再為每次瀏覽寫記錄、重畫視窗，列資料夾只讀一次目錄；手機讀取中不接受重複點擊｜Faster computer folder browsing: the Windows app no longer logs or redraws for each browse and reads each folder once; the phone ignores repeated taps while loading

## 1.0.1244

- 瀏覽與找檔案頁套用主題色；磁碟（C:、D:）名稱會正確顯示｜Browse and find-files pages use the theme color; drive names (C:, D:) now show correctly

- 修正電腦版「檢查更新」找不到檔案：GitHub 會去掉檔名中的中文，改用英文檔名 PhoneAI.exe 上傳｜Fix: Windows app update check could not find the file (GitHub strips Chinese from file names), so the file is now uploaded as PhoneAI.exe

## 1.0.1243

- 接收改成直接瀏覽對方的資料夾：手機和電腦都可以點進資料夾、點檔案接收，不用再搜檔名｜Receive now browses the other device's folders directly (phone and computer): open folders and tap a file to receive it, no name search needed

## 1.0.1242

- 手機可以從其他 App 的「分享」選 PhoneAI 傳檔案，挑裝置後傳出去；電腦版的按鈕改成「傳送過去」和「接收」，移除改成雙擊裝置名字｜Phone: share a file from any app to PhoneAI and pick the device to send it to; Computer app: buttons are now Send and Receive, and remove is double-clicking the device name

## 1.0.1241

- 互傳檔案頁：「找檔案」改放在每台裝置旁邊（直接搜那台）；移除改成點裝置名字｜Transfer page: Find files now sits next to each device (searches that device); remove by tapping the device name

- 電腦版加上「檢查更新」一鍵更新：從 GitHub 的 windows-latest 預發行版下載新的 exe，替換後自動重新開啟｜The Windows app has a Check for updates button: it downloads the new exe from the windows-latest pre-release, replaces itself and restarts

- 新增「找檔案」手動搜尋（不用 AI）：手機與電腦都有搜尋框，可以搜本機或已配對的裝置，點結果就傳過去或取回來｜New manual file search (no AI) on the phone and the computer: search this device or a paired device, tap a result to send it or fetch it

## 1.0.1239

- 互傳檔案頁：已配對的裝置各自一張卡片，按鈕更大好按；新增「傳檔案過去」，從手機選檔案直接傳給已配對的裝置（電腦或另一台手機）｜File transfer page: each paired device has its own card with bigger buttons; new "Send file" button sends a file picked on the phone to a paired device (computer or another phone)

- 電腦版找檔案改成所有磁碟輪流找（每個磁碟都會找到），跳過 Windows 系統資料夾，最多 12 秒｜Windows app file search now takes turns across all drives so each drive gets searched; skips Windows system folders; up to 12 seconds

## 1.0.1238

- 助理的電腦檔案說明改成正確的 Windows 路徑寫法（C:/Users），找不到資料夾時會提示正確寫法｜The assistant now uses Windows path format (C:/Users) for computer files and hints the correct form when a folder is not found

- 助理可以找電腦上的檔案：電腦版會搜尋整台電腦的所有磁碟，助理也知道電腦的路徑要用 C:/ 開頭｜The assistant can find files on a computer: the Windows app searches all drives, and the assistant knows computer paths start with C:/

- 新功能：Windows 版互傳檔案（視窗、配對、傳檔、取檔、停止，與手機版互通）；由 GitHub Actions 打包成 .exe｜New: Windows version of file transfer (window, pairing, send, fetch, stop; works with the phone app); packaged as a .exe by GitHub Actions

## 1.0.1236

- 任務過程改成點進去看詳細頁：整個任務用了多少 token，每一步的思考、動作、結果與用了多少 token；卡片會一張一張彈出來｜Task history opens a detail page: total tokens for the task and, for each step, the thinking, action, result and tokens used; cards bounce in one by one

## 1.0.1235

- 助理的記憶的編輯視窗拿掉「永久記住」開關（列表右邊的星星已經可以點）；灰紫色的按鈕改用主題色｜Removed the Keep forever switch from the memory edit dialog (the star in the list already does it); grey-purple buttons now use the theme color

## 1.0.1234

- 助理開始傳檔案時，面板自動收起來，進度只看通知｜When the assistant starts a file transfer, the panel closes automatically and progress is shown only in the notification

## 1.0.1233

- 互傳檔案的進度通知改成跟 App 更新一樣的設定，可以顯示在超級島／Now Bar 上｜File transfer progress notifications now use the same settings as app updates, so they can show in the Super Island / Now Bar

## 1.0.1232

- 互傳檔案頁在傳輸中會出現「停止傳輸」按鈕，不必靠通知列（有些手機不會把進度通知顯示成浮動通知）｜The file transfer page shows a Stop transfer button while a transfer runs, so stopping does not depend on the notification (some phones do not show the progress notification as a floating one)

## 1.0.1231

- 互傳檔案的進度通知加上「停止」按鈕；按下後中斷傳輸，未完成的檔案會被刪掉｜The file transfer progress notification has a Stop button; pressing it cancels the transfer and deletes the unfinished file

## 1.0.1230

- 互傳檔案傳輸中會顯示進度通知：檔名、百分比、已傳／總大小，以及存到或傳到哪裡；結束後再發一則結果通知｜File transfers show a live progress notification with the file name, percentage, bytes done / total, and where it is saved or sent; a result notification follows when it finishes

## 1.0.1229

- 互傳檔案頁的按鈕改用主題色；對話框（包括移除裝置的確認）改用 App 統一的樣式｜Transfer page buttons now use the theme color; its dialogs, including the remove-device confirmation, use the app's shared style

## 1.0.1228

- 「傳檔案」改名為「互傳檔案」；頁面精簡成幾個區塊；改名後，已配對的裝置每次連上時就會更新名字｜Renamed File transfer to "Transfer files"; the page is shorter; a renamed device now updates its name on the other phone at the next connection

## 1.0.1227

- Claude API Key 移到「資料」區最上面｜The Claude API Key moved to the top of the Data section

## 1.0.1226

- 修正：傳檔案的圖示與「檢查並更新」不再重複；「用量與上限」移到維護區最下面，說明改成「限制 Claude 可用的 token 數」；傳檔案頁的按鈕與對話框按鈕改用主題色｜Fixed: the File transfer icon no longer duplicates the update icon; Usage and limits moved to the bottom of Maintenance with the description "Limit how many Claude tokens can be used"; File transfer buttons and dialog buttons now use the theme color

## 1.0.1225

- 助理可以刪除手機儲存空間裡的檔案或資料夾（不含 Android/），每次刪除都先跳確認框，按否就不刪｜The assistant can delete files or folders in phone storage (except Android/); every delete asks for confirmation first, and nothing is deleted if you decline

## 1.0.1224

- 傳檔案頁改用跟其他頁面一樣的主題色；助理可以直接刪除「下載/PhoneAI」裡收到的檔案（只限這個資料夾，不另外確認）｜The File transfer page now uses the app theme color like the other pages; the assistant can delete received files in Download/PhoneAI directly (that folder only, no extra confirmation)

## 1.0.1223

- 修正：傳檔案開關打開了但服務沒有在運作（打開頁面或 App 啟動時自動補啟動，啟動失敗會在頁面顯示原因）｜Fix: File transfer was switched on but the service was not running (it is now started when the page opens or the app starts, and a startup failure shows its reason on the page)

## 1.0.1222

- 修正：傳檔案配對找不到另一台裝置（加上廣播鎖，廣播被擋時改逐一試同網段位址；傳檔案頁顯示運作狀態與本機位址）｜Fix: file-transfer pairing could not find the other device (hold a multicast lock, fall back to probing the local subnet when broadcast is blocked; the page now shows status and this device's address)

## 1.0.1220

- 新功能：裝置之間傳檔案（第一階段，同一個 Wi-Fi 的手機對手機）。到「功能 → 傳檔案」開啟並配對一次（兩台都停在配對頁、任一台按「配對另一台」，核對 6 位數確認碼），之後對助理說「把 X 傳到我的手機」「從〇〇手機拿 X」「看看〇〇裡有什麼」就會自動找檔案並傳送；內容加密，只開放儲存空間，每次取檔都有通知與記錄｜New: file transfer between your own devices (phase 1, phone to phone on the same Wi-Fi). Turn it on and pair once under Features → File transfer (both stay on the pairing page, either one presses \"Pair another device\", then check the 6-digit code), then tell the assistant "send X to my phone", "get X from <device>" or "what is in <device>" and it finds and transfers the file; content is encrypted, only shared storage is exposed, and every fetch is notified and logged

## 1.0.1219

- 輸入文字改用無障礙直接填進目前的輸入框，中文鍵盤也能正確輸入英文；無障礙只讀取正在輸入的欄位，其他畫面仍只用截圖｜Typing now fills the focused field through accessibility, so English is typed correctly with a Chinese keyboard; accessibility only reads the field being typed into, and the rest of the screen is still screenshots only
- 中文鍵盤時輸入英文會先切到英文鍵盤，輸完再切回來；找不到英文鍵盤時不會亂打出中文｜With a Chinese keyboard, English text is typed after switching to an English keyboard, then the keyboard is switched back; if there is no English keyboard, nothing gibberish is typed
- 安裝與訂閱 App 前一律先跳確認框；搜尋 Google Play 改用網址，不再靠貼上；剪貼簿寫不進去時不會貼上舊內容｜Installing or subscribing to an app now always asks for confirmation first; searching Google Play uses a URL instead of pasting; if the clipboard cannot be written, old content is no longer pasted
- 光球只在 AI 真的要操作手機時才收起；思考、提問、要你確認時光球留著，確認框直接在它上面彈出｜The orb only goes away when the AI actually operates the phone; while it thinks, asks or waits for your confirmation the orb stays and the dialog appears on top
- 更新說明太長時完整顯示（對話框可以捲動），不再在中間被截掉｜Long update notes are shown in full (the dialog scrolls) instead of being cut off in the middle
- 星星改成圓角形狀，點下去有彈簧動畫；記憶頁的永久記憶星星改用主題色｜Stars are now rounded with a spring bounce when tapped; the permanent-memory star on the memory page uses the theme color
- 確認框改成跟 App 一致的 Material 樣式（主題色）｜Confirmation dialogs now use the app's Material style and theme color
- 回答卡片：光區上緣慢慢淡入，太長時上下緣淡出並可上下滑動｜Answer card: the glow fades in softly, and long text fades at the edges and scrolls
- 退出面板時停止朗讀；太長的朗讀自動切段，引擎沒準備好時不再丟掉第一次朗讀；診斷頁顯示朗讀狀態｜Leaving the panel stops speech; long speech is split into chunks, the first speech is no longer dropped while the engine starts, and the diagnostics page shows the speech status
- 任務過程：點一下任務可以展開 AI 每一步做了什麼；按停止會立刻中斷等待中的請求，Now Bar 顯示「已停止」，等你回答或確認時也會顯示｜Task log: tap a task to see what the AI did at each step; Stop now aborts the pending request right away, and the Now Bar shows "Stopped" and when it is waiting for you
- 記憶匯出檔名帶時間，匯入時可以選擇要哪一個備份｜Memory backups are named with a timestamp, and import lets you choose which backup
- 桌面小工具改成漸層底加麥克風圖示｜The home-screen widget now has a gradient background and a mic icon
- 數學式：聊天助理用 LaTeX 寫算式，畫面排成上標、根號、斜體的算式，朗讀念成口語（「3 的二次方 加 x 的三次方 加 根號 5 等於 3」）｜Math: the chat assistant writes formulas in LaTeX, the screen typesets them with superscripts, roots and italics, and speech reads them naturally ("3 squared plus x cubed plus root 5 equals 3")
- 回答卡片從上方排起、高度限制在輸入列上方，長文可以捲動，不再超出螢幕｜The answer card is laid out from the top and limited to the space above the input row; long text scrolls instead of running off the screen
- 朗讀與通知自動略過 Markdown 符號，聊天助理可以用簡單的 Markdown 整理重點｜Speech and notifications skip Markdown symbols, and the chat assistant may use simple Markdown to organize points
- 聊天助理不能只用文字宣稱「已記住／已刪除」，必須先呼叫工具｜The chat assistant may no longer claim it remembered or deleted something without actually calling the tool
- 設定 → 語音 新增「開面板時自動聽」開關｜New "Listen when the panel opens" switch under Settings → Voice
- 記憶頁每一列右邊有星星，點一下就設成或取消永久記憶｜Memory rows have a star on the right; tap it to make an item permanent or not
- 「進階與診斷」顯示目前使用的模型｜The diagnostics page shows which models are in use
- 聊天助理在你明確要求時可以用 Markdown 或條列｜The chat assistant can use Markdown or lists when you explicitly ask for them
- 重新叫出助理面板時，會清掉上一輪的對話畫面｜Reopening the assistant panel clears the previous conversation from the screen
- Now Bar 的「準備中」文字會跟著語言｜The Now Bar "Preparing…" text follows the interface language
- 修正在助理面板請 AI 刪除待辦等需要確認的操作會閃退；確認框與 AI 的提問輸入框恢復正常｜Fixed a crash when asking the assistant panel to do something that needs confirmation (like deleting all to-dos); confirmation dialogs and the AI's question box work again
- 通知上的「停止」鈕現在真的能停止自動操作，也會停止朗讀；按麥克風或送出時會先打斷朗讀｜The Stop button on the notification now really stops automatic actions and speech; tapping the mic or sending interrupts speech
- 自動操作結束後收掉 Now Bar；搜尋結果會用回答卡片顯示｜The Now Bar is dismissed when automatic actions finish, and search results are shown on an answer card
- 修正「打開設定的 Wi-Fi 頁面」有時被當成聊天、回答「我不能操作手機」：話裡有明顯的操作詞就改走自動操作｜Fixed "open the Wi-Fi settings page" sometimes being treated as chat and answered with "I can't operate the phone"; clear action words now go to the automatic actions

## 1.0.1218

- 中文鍵盤時輸入英文會先切到英文鍵盤，輸完再切回來；找不到英文鍵盤時不會亂打出中文｜With a Chinese keyboard, English text is typed after switching to an English keyboard, then the keyboard is switched back; if there is no English keyboard, nothing gibberish is typed
- 安裝與訂閱 App 前一律先跳確認框；搜尋 Google Play 改用網址，不再靠貼上；剪貼簿寫不進去時不會貼上舊內容｜Installing or subscribing to an app now always asks for confirmation first; searching Google Play uses a URL instead of pasting; if the clipboard cannot be written, old content is no longer pasted
- 光球只在 AI 真的要操作手機時才收起；思考、提問、要你確認時光球留著，確認框直接在它上面彈出｜The orb only goes away when the AI actually operates the phone; while it thinks, asks or waits for your confirmation the orb stays and the dialog appears on top
- 更新說明太長時完整顯示（對話框可以捲動），不再在中間被截掉｜Long update notes are shown in full (the dialog scrolls) instead of being cut off in the middle
- 星星改成圓角形狀，點下去有彈簧動畫；記憶頁的永久記憶星星改用主題色｜Stars are now rounded with a spring bounce when tapped; the permanent-memory star on the memory page uses the theme color
- 確認框改成跟 App 一致的 Material 樣式（主題色）｜Confirmation dialogs now use the app's Material style and theme color
- 回答卡片：光區上緣慢慢淡入，太長時上下緣淡出並可上下滑動｜Answer card: the glow fades in softly, and long text fades at the edges and scrolls
- 退出面板時停止朗讀；太長的朗讀自動切段，引擎沒準備好時不再丟掉第一次朗讀；診斷頁顯示朗讀狀態｜Leaving the panel stops speech; long speech is split into chunks, the first speech is no longer dropped while the engine starts, and the diagnostics page shows the speech status
- 任務過程：點一下任務可以展開 AI 每一步做了什麼；按停止會立刻中斷等待中的請求，Now Bar 顯示「已停止」，等你回答或確認時也會顯示｜Task log: tap a task to see what the AI did at each step; Stop now aborts the pending request right away, and the Now Bar shows "Stopped" and when it is waiting for you
- 記憶匯出檔名帶時間，匯入時可以選擇要哪一個備份｜Memory backups are named with a timestamp, and import lets you choose which backup
- 桌面小工具改成漸層底加麥克風圖示｜The home-screen widget now has a gradient background and a mic icon
- 數學式：聊天助理用 LaTeX 寫算式，畫面排成上標、根號、斜體的算式，朗讀念成口語（「3 的二次方 加 x 的三次方 加 根號 5 等於 3」）｜Math: the chat assistant writes formulas in LaTeX, the screen typesets them with superscripts, roots and italics, and speech reads them naturally ("3 squared plus x cubed plus root 5 equals 3")
- 回答卡片從上方排起、高度限制在輸入列上方，長文可以捲動，不再超出螢幕｜The answer card is laid out from the top and limited to the space above the input row; long text scrolls instead of running off the screen
- 朗讀與通知自動略過 Markdown 符號，聊天助理可以用簡單的 Markdown 整理重點｜Speech and notifications skip Markdown symbols, and the chat assistant may use simple Markdown to organize points
- 聊天助理不能只用文字宣稱「已記住／已刪除」，必須先呼叫工具｜The chat assistant may no longer claim it remembered or deleted something without actually calling the tool
- 設定 → 語音 新增「開面板時自動聽」開關｜New "Listen when the panel opens" switch under Settings → Voice
- 記憶頁每一列右邊有星星，點一下就設成或取消永久記憶｜Memory rows have a star on the right; tap it to make an item permanent or not
- 「進階與診斷」顯示目前使用的模型｜The diagnostics page shows which models are in use
- 聊天助理在你明確要求時可以用 Markdown 或條列｜The chat assistant can use Markdown or lists when you explicitly ask for them
- 重新叫出助理面板時，會清掉上一輪的對話畫面｜Reopening the assistant panel clears the previous conversation from the screen
- Now Bar 的「準備中」文字會跟著語言｜The Now Bar "Preparing…" text follows the interface language
- 修正在助理面板請 AI 刪除待辦等需要確認的操作會閃退；確認框與 AI 的提問輸入框恢復正常｜Fixed a crash when asking the assistant panel to do something that needs confirmation (like deleting all to-dos); confirmation dialogs and the AI's question box work again
- 通知上的「停止」鈕現在真的能停止自動操作，也會停止朗讀；按麥克風或送出時會先打斷朗讀｜The Stop button on the notification now really stops automatic actions and speech; tapping the mic or sending interrupts speech
- 自動操作結束後收掉 Now Bar；搜尋結果會用回答卡片顯示｜The Now Bar is dismissed when automatic actions finish, and search results are shown on an answer card
- 修正「打開設定的 Wi-Fi 頁面」有時被當成聊天、回答「我不能操作手機」：話裡有明顯的操作詞就改走自動操作｜Fixed "open the Wi-Fi settings page" sometimes being treated as chat and answered with "I can't operate the phone"; clear action words now go to the automatic actions

## 1.0.1216

- 光球只在 AI 真的要操作手機時才收起；思考、提問、要你確認時光球留著，確認框直接在它上面彈出｜The orb only goes away when the AI actually operates the phone; while it thinks, asks or waits for your confirmation the orb stays and the dialog appears on top
- 更新說明太長時完整顯示（對話框可以捲動），不再在中間被截掉｜Long update notes are shown in full (the dialog scrolls) instead of being cut off in the middle
- 星星改成圓角形狀，點下去有彈簧動畫；記憶頁的永久記憶星星改用主題色｜Stars are now rounded with a spring bounce when tapped; the permanent-memory star on the memory page uses the theme color
- 確認框改成跟 App 一致的 Material 樣式（主題色）｜Confirmation dialogs now use the app's Material style and theme color
- 回答卡片：光區上緣慢慢淡入，太長時上下緣淡出並可上下滑動｜Answer card: the glow fades in softly, and long text fades at the edges and scrolls
- 退出面板時停止朗讀；太長的朗讀自動切段，引擎沒準備好時不再丟掉第一次朗讀；診斷頁顯示朗讀狀態｜Leaving the panel stops speech; long speech is split into chunks, the first speech is no longer dropped while the engine starts, and the diagnostics page shows the speech status
- 任務過程：點一下任務可以展開 AI 每一步做了什麼；按停止會立刻中斷等待中的請求，Now Bar 顯示「已停止」，等你回答或確認時也會顯示｜Task log: tap a task to see what the AI did at each step; Stop now aborts the pending request right away, and the Now Bar shows "Stopped" and when it is waiting for you
- 記憶匯出檔名帶時間，匯入時可以選擇要哪一個備份｜Memory backups are named with a timestamp, and import lets you choose which backup
- 桌面小工具改成漸層底加麥克風圖示｜The home-screen widget now has a gradient background and a mic icon
- 數學式：聊天助理用 LaTeX 寫算式，畫面排成上標、根號、斜體的算式，朗讀念成口語（「3 的二次方 加 x 的三次方 加 根號 5 等於 3」）｜Math: the chat assistant writes formulas in LaTeX, the screen typesets them with superscripts, roots and italics, and speech reads them naturally ("3 squared plus x cubed plus root 5 equals 3")
- 回答卡片從上方排起、高度限制在輸入列上方，長文可以捲動，不再超出螢幕｜The answer card is laid out from the top and limited to the space above the input row; long text scrolls instead of running off the screen
- 朗讀與通知自動略過 Markdown 符號，聊天助理可以用簡單的 Markdown 整理重點｜Speech and notifications skip Markdown symbols, and the chat assistant may use simple Markdown to organize points
- 聊天助理不能只用文字宣稱「已記住／已刪除」，必須先呼叫工具｜The chat assistant may no longer claim it remembered or deleted something without actually calling the tool
- 設定 → 語音 新增「開面板時自動聽」開關｜New "Listen when the panel opens" switch under Settings → Voice
- 記憶頁每一列右邊有星星，點一下就設成或取消永久記憶｜Memory rows have a star on the right; tap it to make an item permanent or not
- 「進階與診斷」顯示目前使用的模型｜The diagnostics page shows which models are in use
- 聊天助理在你明確要求時可以用 Markdown 或條列｜The chat assistant can use Markdown or lists when you explicitly ask for them
- 重新叫出助理面板時，會清掉上一輪的對話畫面｜Reopening the assistant panel clears the previous conversation from the screen
- Now Bar 的「準備中」文字會跟著語言｜The Now Bar "Preparing…" text follows the interface language
- 修正在助理面板請 AI 刪除待辦等需要確認的操作會閃退；確認框與 AI 的提問輸入框恢復正常｜Fixed a crash when asking the assistant panel to do something that needs confirmation (like deleting all to-dos); confirmation dialogs and the AI's question box work again
- 通知上的「停止」鈕現在真的能停止自動操作，也會停止朗讀；按麥克風或送出時會先打斷朗讀｜The Stop button on the notification now really stops automatic actions and speech; tapping the mic or sending interrupts speech
- 自動操作結束後收掉 Now Bar；搜尋結果會用回答卡片顯示｜The Now Bar is dismissed when automatic actions finish, and search results are shown on an answer card
- 修正「打開設定的 Wi-Fi 頁面」有時被當成聊天、回答「我不能操作手機」：話裡有明顯的操作詞就改走自動操作｜Fixed "open the Wi-Fi settings page" sometimes being treated as chat and answered with "I can't operate the phone"; clear action words now go to the automatic actions

## 1.0.1215

- 更新說明太長時完整顯示（對話框可以捲動），不再在中間被截掉｜Long update notes are shown in full (the dialog scrolls) instead of being cut off in the middle
- 星星改成圓角形狀，點下去有彈簧動畫；記憶頁的永久記憶星星改用主題色｜Stars are now rounded with a spring bounce when tapped; the permanent-memory star on the memory page uses the theme color
- 確認框改成跟 App 一致的 Material 樣式（主題色）｜Confirmation dialogs now use the app's Material style and theme color
- 回答卡片：光區上緣慢慢淡入，太長時上下緣淡出並可上下滑動｜Answer card: the glow fades in softly, and long text fades at the edges and scrolls
- 退出面板時停止朗讀；太長的朗讀自動切段，引擎沒準備好時不再丟掉第一次朗讀；診斷頁顯示朗讀狀態｜Leaving the panel stops speech; long speech is split into chunks, the first speech is no longer dropped while the engine starts, and the diagnostics page shows the speech status
- 任務過程：點一下任務可以展開 AI 每一步做了什麼；按停止會立刻中斷等待中的請求，Now Bar 顯示「已停止」，等你回答或確認時也會顯示｜Task log: tap a task to see what the AI did at each step; Stop now aborts the pending request right away, and the Now Bar shows "Stopped" and when it is waiting for you
- 記憶匯出檔名帶時間，匯入時可以選擇要哪一個備份｜Memory backups are named with a timestamp, and import lets you choose which backup
- 桌面小工具改成漸層底加麥克風圖示｜The home-screen widget now has a gradient background and a mic icon
- 數學式：聊天助理用 LaTeX 寫算式，畫面排成上標、根號、斜體的算式，朗讀念成口語（「3 的二次方 加 x 的三次方 加 根號 5 等於 3」）｜Math: the chat assistant writes formulas in LaTeX, the screen typesets them with superscripts, roots and italics, and speech reads them naturally ("3 squared plus x cubed plus root 5 equals 3")
- 回答卡片從上方排起、高度限制在輸入列上方，長文可以捲動，不再超出螢幕｜The answer card is laid out from the top and limited to the space above the input row; long text scrolls instead of running off the screen
- 朗讀與通知自動略過 Markdown 符號，聊天助理可以用簡單的 Markdown 整理重點｜Speech and notifications skip Markdown symbols, and the chat assistant may use simple Markdown to organize points
- 聊天助理不能只用文字宣稱「已記住／已刪除」，必須先呼叫工具｜The chat assistant may no longer claim it remembered or deleted something without actually calling the tool
- 設定 → 語音 新增「開面板時自動聽」開關｜New "Listen when the panel opens" switch under Settings → Voice
- 記憶頁每一列右邊有星星，點一下就設成或取消永久記憶｜Memory rows have a star on the right; tap it to make an item permanent or not
- 「進階與診斷」顯示目前使用的模型｜The diagnostics page shows which models are in use
- 聊天助理在你明確要求時可以用 Markdown 或條列｜The chat assistant can use Markdown or lists when you explicitly ask for them
- 重新叫出助理面板時，會清掉上一輪的對話畫面｜Reopening the assistant panel clears the previous conversation from the screen
- Now Bar 的「準備中」文字會跟著語言｜The Now Bar "Preparing…" text follows the interface language
- 修正在助理面板請 AI 刪除待辦等需要確認的操作會閃退；確認框與 AI 的提問輸入框恢復正常｜Fixed a crash when asking the assistant panel to do something that needs confirmation (like deleting all to-dos); confirmation dialogs and the AI's question box work again
- 通知上的「停止」鈕現在真的能停止自動操作，也會停止朗讀；按麥克風或送出時會先打斷朗讀｜The Stop button on the notification now really stops automatic actions and speech; tapping the mic or sending interrupts speech
- 自動操作結束後收掉 Now Bar；搜尋結果會用回答卡片顯示｜The Now Bar is dismissed when automatic actions finish, and search results are shown on an answer card
- 修正「打開設定的 Wi-Fi 頁面」有時被當成聊天、回答「我不能操作手機」：話裡有明顯的操作詞就改走自動操作｜Fixed "open the Wi-Fi settings page" sometimes being treated as chat and answered with "I can't operate the phone"; clear action words now go to the automatic actions

## 1.0.1214

- 星星改成圓角形狀，點下去有彈簧動畫；記憶頁的永久記憶星星改用主題色｜Stars are now rounded with a spring bounce when tapped; the permanent-memory star on the memory page uses the theme color
- 確認框改成跟 App 一致的 Material 樣式（主題色）｜Confirmation dialogs now use the app's Material style and theme color
- 回答卡片：光區上緣慢慢淡入，太長時上下緣淡出並可上下滑動｜Answer card: the glow fades in softly, and long text fades at the edges and scrolls
- 退出面板時停止朗讀；太長的朗讀自動切段，引擎沒準備好時不再丟掉第一次朗讀；診斷頁顯示朗讀狀態｜Leaving the panel stops speech; long speech is split into chunks, the first speech is no longer dropped while the engine starts, and the diagnostics page shows the speech status
- 任務過程：點一下任務可以展開 AI 每一步做了什麼；按停止會立刻中斷等待中的請求，Now Bar 顯示「已停止」，等你回答或確認時也會顯示｜Task log: tap a task to see what the AI did at each step; Stop now aborts the pending request right away, and the Now Bar shows "Stopped" and when it is waiting for you
- 記憶匯出檔名帶時間，匯入時可以選擇要哪一個備份｜Memory backups are named with a timestamp, and import lets you choose which backup
- 桌面小工具改成漸層底加麥克風圖示｜The home-screen widget now has a gradient background and a mic icon
- 數學式：聊天助理用 LaTeX 寫算式，畫面排成上標、根號、斜體的算式，朗讀念成口語（「3 的二次方 加 x 的三次方 加 根號 5 等於 3」）｜Math: the chat assistant writes formulas in LaTeX, the screen typesets them with superscripts, roots and italics, and speech reads them naturally ("3 squared plus x cubed plus root 5 equals 3")
- 回答卡片從上方排起、高度限制在輸入列上方，長文可以捲動，不再超出螢幕｜The answer card is laid out from the top and limited to the space above the input row; long text scrolls instead of running off the screen
- 朗讀與通知自動略過 Markdown 符號，聊天助理可以用簡單的 Markdown 整理重點｜Speech and notifications skip Markdown symbols, and the chat assistant may use simple Markdown to organize points
- 聊天助理不能只用文字宣稱「已記住／已刪除」，必須先呼叫工具｜The chat assistant may no longer claim it remembered or deleted something without actually calling the tool
- 設定 → 語音 新增「開面板時自動聽」開關｜New "Listen when the panel opens" switch under Settings → Voice
- 記憶頁每一列右邊有星星，點一下就設成或取消永久記憶｜Memory rows have a star on the right; tap it to make an item permanent or not
- 「進階與診斷」顯示目前使用的模型｜The diagnostics page shows which models are in use
- 聊天助理在你明確要求時可以用 Markdown 或條列｜The chat assistant can use Markdown or lists when you explicitly ask for them
- 重新叫出助理面板時，會清掉上一輪的對話畫面｜Reopening the assistant panel clears the previous conversation from the screen
- Now Bar 的「準備中」文字會跟著語言｜The Now Bar "Preparing…" text follows the interface language
- 修正在助理面板請 AI 刪除待辦等需要確認的操作會閃退；確認框與 AI 的提問輸入框恢復正常｜Fixed a crash when asking the assistant panel to do something that needs confirmation (like deleting all to-dos); confirmation dialogs and the AI's question box work again
- 通知上的「停止」鈕現在真的能停止自動操作，也會停止朗讀；按麥克風或送出時會先打斷朗讀｜The Stop button on the notification now really stops automatic actions and speech; tapping the mic or sending interrupts speech
- 自動操作結束後收掉 Now Bar；搜尋結果會用回答卡片顯示｜The Now Bar is dismissed when automatic actions finish, and search results are shown on an answer card
- 修正「打開設定的 Wi-Fi 頁面」有時被當成聊天、回答「我不能操作手機」：話裡有明顯的操作詞就改走自動操作｜Fixed "open the Wi-Fi settings page" sometimes being treated as chat and answered with "I can't operate the phone"; clear action words now go to the automatic actions

## 1.0.1213

- 數學式：聊天助理用 LaTeX 寫算式，畫面排成上標、根號、斜體的算式，朗讀念成口語（「3 的二次方 加 x 的三次方 加 根號 5 等於 3」）｜Math: the chat assistant writes formulas in LaTeX, the screen typesets them with superscripts, roots and italics, and speech reads them naturally ("3 squared plus x cubed plus root 5 equals 3")
- 回答卡片從上方排起、高度限制在輸入列上方，長文可以捲動，不再超出螢幕｜The answer card is laid out from the top and limited to the space above the input row; long text scrolls instead of running off the screen
- 朗讀與通知自動略過 Markdown 符號，聊天助理可以用簡單的 Markdown 整理重點｜Speech and notifications skip Markdown symbols, and the chat assistant may use simple Markdown to organize points
- 聊天助理不能只用文字宣稱「已記住／已刪除」，必須先呼叫工具｜The chat assistant may no longer claim it remembered or deleted something without actually calling the tool
- 設定 → 語音 新增「開面板時自動聽」開關｜New "Listen when the panel opens" switch under Settings → Voice
- 記憶頁每一列右邊有星星，點一下就設成或取消永久記憶｜Memory rows have a star on the right; tap it to make an item permanent or not
- 「進階與診斷」顯示目前使用的模型｜The diagnostics page shows which models are in use
- 聊天助理在你明確要求時可以用 Markdown 或條列｜The chat assistant can use Markdown or lists when you explicitly ask for them
- 重新叫出助理面板時，會清掉上一輪的對話畫面｜Reopening the assistant panel clears the previous conversation from the screen
- Now Bar 的「準備中」文字會跟著語言｜The Now Bar "Preparing…" text follows the interface language
- 修正在助理面板請 AI 刪除待辦等需要確認的操作會閃退；確認框與 AI 的提問輸入框恢復正常｜Fixed a crash when asking the assistant panel to do something that needs confirmation (like deleting all to-dos); confirmation dialogs and the AI's question box work again
- 通知上的「停止」鈕現在真的能停止自動操作，也會停止朗讀；按麥克風或送出時會先打斷朗讀｜The Stop button on the notification now really stops automatic actions and speech; tapping the mic or sending interrupts speech
- 自動操作結束後收掉 Now Bar；搜尋結果會用回答卡片顯示｜The Now Bar is dismissed when automatic actions finish, and search results are shown on an answer card
- 修正「打開設定的 Wi-Fi 頁面」有時被當成聊天、回答「我不能操作手機」：話裡有明顯的操作詞就改走自動操作｜Fixed "open the Wi-Fi settings page" sometimes being treated as chat and answered with "I can't operate the phone"; clear action words now go to the automatic actions

## 1.0.1212

- 回答卡片從上方排起、高度限制在輸入列上方，長文可以捲動，不再超出螢幕｜The answer card is laid out from the top and limited to the space above the input row; long text scrolls instead of running off the screen
- 朗讀與通知自動略過 Markdown 符號，聊天助理可以用簡單的 Markdown 整理重點｜Speech and notifications skip Markdown symbols, and the chat assistant may use simple Markdown to organize points
- 聊天助理不能只用文字宣稱「已記住／已刪除」，必須先呼叫工具｜The chat assistant may no longer claim it remembered or deleted something without actually calling the tool
- 設定 → 語音 新增「開面板時自動聽」開關｜New "Listen when the panel opens" switch under Settings → Voice
- 記憶頁每一列右邊有星星，點一下就設成或取消永久記憶｜Memory rows have a star on the right; tap it to make an item permanent or not
- 「進階與診斷」顯示目前使用的模型｜The diagnostics page shows which models are in use
- 聊天助理在你明確要求時可以用 Markdown 或條列｜The chat assistant can use Markdown or lists when you explicitly ask for them
- 重新叫出助理面板時，會清掉上一輪的對話畫面｜Reopening the assistant panel clears the previous conversation from the screen
- Now Bar 的「準備中」文字會跟著語言｜The Now Bar "Preparing…" text follows the interface language
- 修正在助理面板請 AI 刪除待辦等需要確認的操作會閃退；確認框與 AI 的提問輸入框恢復正常｜Fixed a crash when asking the assistant panel to do something that needs confirmation (like deleting all to-dos); confirmation dialogs and the AI's question box work again
- 通知上的「停止」鈕現在真的能停止自動操作，也會停止朗讀；按麥克風或送出時會先打斷朗讀｜The Stop button on the notification now really stops automatic actions and speech; tapping the mic or sending interrupts speech
- 自動操作結束後收掉 Now Bar；搜尋結果會用回答卡片顯示｜The Now Bar is dismissed when automatic actions finish, and search results are shown on an answer card
- 修正「打開設定的 Wi-Fi 頁面」有時被當成聊天、回答「我不能操作手機」：話裡有明顯的操作詞就改走自動操作｜Fixed "open the Wi-Fi settings page" sometimes being treated as chat and answered with "I can't operate the phone"; clear action words now go to the automatic actions

## 1.0.1211

- 記憶頁每一列右邊有星星，點一下就設成或取消永久記憶｜Memory rows have a star on the right; tap it to make an item permanent or not
- 「進階與診斷」顯示目前使用的模型｜The diagnostics page shows which models are in use
- 聊天助理在你明確要求時可以用 Markdown 或條列｜The chat assistant can use Markdown or lists when you explicitly ask for them
- 重新叫出助理面板時，會清掉上一輪的對話畫面｜Reopening the assistant panel clears the previous conversation from the screen
- Now Bar 的「準備中」文字會跟著語言｜The Now Bar "Preparing…" text follows the interface language
- 修正在助理面板請 AI 刪除待辦等需要確認的操作會閃退；確認框與 AI 的提問輸入框恢復正常｜Fixed a crash when asking the assistant panel to do something that needs confirmation (like deleting all to-dos); confirmation dialogs and the AI's question box work again
- 通知上的「停止」鈕現在真的能停止自動操作，也會停止朗讀；按麥克風或送出時會先打斷朗讀｜The Stop button on the notification now really stops automatic actions and speech; tapping the mic or sending interrupts speech
- 自動操作結束後收掉 Now Bar；搜尋結果會用回答卡片顯示｜The Now Bar is dismissed when automatic actions finish, and search results are shown on an answer card
- 修正「打開設定的 Wi-Fi 頁面」有時被當成聊天、回答「我不能操作手機」：話裡有明顯的操作詞就改走自動操作｜Fixed "open the Wi-Fi settings page" sometimes being treated as chat and answered with "I can't operate the phone"; clear action words now go to the automatic actions

## 1.0.1209

- 修正「打開設定的 Wi-Fi 頁面」有時被當成聊天、回答「我不能操作手機」：話裡有明顯的操作詞就改走自動操作｜Fixed "open the Wi-Fi settings page" sometimes being treated as chat and answered with "I can't operate the phone"; clear action words now go to the automatic actions

## 1.0.1208

- 自動操作恢復點擊漣漪，Now Bar 也會顯示每一步的步數｜Automatic actions show tap ripples again, and the Now Bar shows the step count for each step

## 1.0.1207

- 「用量與上限」顯示今天的快取寫入與快取讀取，方便確認快取有沒有生效｜The usage dialog now shows today's cache writes and cache reads, to confirm caching works

## 1.0.1206

- 自動操作改用提示詞快取，重複的系統提示詞費用大幅降低｜Automatic actions now use prompt caching, greatly lowering the cost of the repeated system prompt

## 1.0.1205

- 底欄玻璃的預設值改成調好的一套（折射 0.56、色散 0.30、圖示放大 0.08、拖動歪斜 0）；已經調過的設定不受影響，按「恢復預設」才會套用｜New glass defaults (refraction 0.56, dispersion 0.30, icon zoom 0.08, drag skew 0); settings you already changed are kept, and "Restore defaults" applies them

## 1.0.1204

- 拖動底欄時透鏡每一幀平滑跟著手指走，不再一跳一跳｜While dragging the bar, the lens now follows the finger smoothly every frame instead of jumping

## 1.0.1203

- 透鏡在圖示上輕微移動時，圖示不再跟著抖（圖示不歪斜，微小移動不產生歪斜）｜When the lens moves slightly over the icons, the icons no longer twitch (icons are not skewed, and tiny movements do not skew the lens)

## 1.0.1202

- 拖動時只有透鏡邊緣會形變，中間的圖示不會跟著變形；形變改成臨界阻尼，不再一抽一抽｜While dragging only the lens edge deforms; the icons in the middle stay still, and the stretch is critically damped so it no longer twitches

## 1.0.1201

- 拖動底欄時，輕微的抖動不再讓畫面跟著抖（形變加上門檻）；透鏡的彩虹邊變細｜Small finger jitter no longer makes the bar wobble while dragging (the stretch has a threshold); the rainbow edge is thinner

## 1.0.1200

- 透鏡靜止時整塊微微提亮（磨砂感），按住時慢慢消失｜At rest the lens body is slightly lifted to look frosted; it fades out while pressed
- 透鏡的彩虹邊只在按住時出現，沒按住時不顯示｜The lens rainbow edge only appears while pressed; it is hidden at rest
- 透鏡加上一圈彩虹色的邊緣環，黑底上也看得出邊界（仿酷安的多色邊緣）｜The lens gets a rainbow edge ring that marks its boundary even on a dark background (like Coolapk's multicolor edge)

## 1.0.1197

- 透鏡內部不再加底色或變暗，圖示不加彩虹色散，看得更清楚｜The lens interior no longer has a tint or darkening, and icons no longer get rainbow dispersion, so they are clearer

## 1.0.1196

- 關掉透鏡的主題色邊緣線（只保留彩虹色散的邊界）｜Turned off the theme-colored lens edge line; the rainbow dispersion alone marks the boundary

## 1.0.1194

- 透鏡的色散改成沿紅到紫的 7 點光譜取樣，邊緣的彩虹線更接近酷安｜Lens dispersion now samples 7 spectral points from red to violet, so the rainbow edge is closer to Coolapk

## 1.0.1193

- 透鏡加上一圈半透明的邊緣線，左上較亮，讓透鏡邊界在沒有反射時也看得出來｜The lens gets a faint translucent edge line, brighter at the top-left, so its boundary shows even without reflections

## 1.0.1190

- 用量上限：每日、每月可設 token 上限（功能 → 維護 → 用量與上限），超過就先不送出 Claude 請求｜Claude usage limits: set daily and monthly token caps (Features → Maintenance → Usage and limits); over the cap, Claude requests are not sent
- 分享到 PhoneAI：在別的 App 的「分享」選單選 PhoneAI，文字會填進助理面板，確認後再送出｜Share to PhoneAI: pick PhoneAI in another app's share menu; the text goes into the assistant panel and you send it after checking
- 記憶可以搜尋，並能匯出、匯入下載資料夾的備份（匯入只加入還沒有的項目）｜Memory search, plus export and import of a backup in the Downloads folder (import only adds items you do not have yet)
- 回答卡片支援簡單的 Markdown 排版（粗體、清單、程式碼、標題）｜Answer cards show simple Markdown formatting (bold, lists, code, headings)
- 桌面小工具「PhoneAI 語音」：一點就開助理面板並開始聽｜Home-screen widget "PhoneAI Voice": one tap opens the assistant and starts listening

## 1.0.1189

- 任務歷史：每次送出的任務都會記下來，可以加星星，有星的排在最前面｜Task history: every task is recorded, you can star tasks, and starred tasks stay at the top

## 1.0.1183


- 英文介面與語言選項（設定 → 外觀 → Language），可即時切換｜English interface and a language option (Settings → Appearance → Language), switches instantly
- 英文語音指令、英文快速問答、英文定時任務｜English voice commands, English quick answers and English scheduled tasks
- 底欄液態玻璃改用移植的膠囊著色器：按住時透鏡與整條底欄放大、加速度驅動的彈簧形變、透鏡下的圖示變主題色｜Liquid-glass bar uses a ported capsule shader: lens and whole bar grow while pressed, acceleration-driven spring stretch, icons under the lens take the theme color
- 首頁與設定的標題區縮小；教學頁文字置中｜Smaller title area on the home and settings pages; onboarding text centered
