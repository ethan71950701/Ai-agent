# 更新說明 · Changelog

每一條寫成「中文｜English」，App 依介面語言只顯示對應的那一半；Release 說明會用整段 `## Unreleased` 內容。
Each entry is written as "中文｜English"; the app shows the half that matches the interface language. The release notes use the whole `## Unreleased` section.

## Unreleased

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
