# 更新說明 · Changelog

每一條寫成「中文｜English」，App 依介面語言只顯示對應的那一半；Release 說明會用整段 `## Unreleased` 內容。
Each entry is written as "中文｜English"; the app shows the half that matches the interface language. The release notes use the whole `## Unreleased` section.

## Unreleased

## 1.0.1399

- 圈選提問的動畫與轉場：按 ◎ 的瞬間面板畫面就藏起來、馬上截圖，圈選畫面開啟時沒有空窗（畫面像直接凍結，帶一下閃光）；圈的線改成流動發光的漸層光（藍紫粉青，跟助理面板的極光同色），手指前有光點，圈完那一瞬間光暈變亮再呼吸，圈外慢慢變暗｜Circle to ask animation and transition: the panel disappears the instant you tap ◎ and the screenshot is taken right away, so there is no empty gap (the screen just freezes, with a flash); the circle line is now a flowing glowing gradient (blue, purple, pink, cyan, the same colors as the panel's aurora) with a light dot at the fingertip, the glow bursts when you finish the circle and then breathes, and the outside fades dark.

## 1.0.1398

- 圈選提問改版：要重圈直接在畫面上再圈一次（拿掉「重新圈選」鈕）；雙指可以放大縮小、移動截圖，好圈小東西；提問列、送出鈕、回答卡片改成跟助理面板同一種風格｜Circle to ask reworked: to redo a selection just circle again (the "Circle again" button is gone); pinch with two fingers to zoom and pan the screenshot so small things are easier to circle; the question bar, send button and answer card now share the assistant panel's style.

## 1.0.1397

- 「錯誤紀錄」搬到「進階與診斷」頁最下面，直接顯示在畫面上（可長按選取），有「分享」「存到 PhoneAI 資料夾」「清除」鈕；功能頁的獨立入口移除｜The "Error log" moved to the bottom of the Advanced & diagnostics page and is shown right on the page (long-press to select), with Share, Save to PhoneAI folder and Clear buttons; the separate entry on the Functions page is gone.
- 新增「錯誤紀錄」（進階與診斷）：App 當機或出錯時自動記下錯誤堆疊，可在 App 裡看、分享、存到 PhoneAI 資料夾、清除；匯出任務紀錄時也會附上錯誤紀錄與本 App 的系統記錄（不含 API Key）｜New "Error log" (Functions → Maintenance): crashes and errors are recorded automatically with their stack traces; view, share, save to the PhoneAI folder or clear them in the app; exporting the task log now also includes the error log and the app's own system log (no API keys).

## 1.0.1396

- 新增「錯誤紀錄」（功能 → 維護）：App 當機或出錯時自動記下錯誤堆疊，可在 App 裡看、分享、存到 PhoneAI 資料夾、清除；匯出任務紀錄時也會附上錯誤紀錄與本 App 的系統記錄（不含 API Key）｜New "Error log" (Functions → Maintenance): crashes and errors are recorded automatically with their stack traces; view, share, save to the PhoneAI folder or clear them in the app; exporting the task log now also includes the error log and the app's own system log (no API keys).
- 修正圈選提問一按就當機（在畫面建立之前取用系統列控制器，得到空值）｜Fixed Circle to ask crashing right away (the system-bar controller was requested before the window existed and was null).

## 1.0.1395

- 修正助理面板的背景模式（主螢幕／副螢幕）按鈕第二次叫出面板就消失：每個任務開始前重新檢查 root 時，會先把「有 root」的結果清成不知道，面板就當成沒有 root；現在舊結果先留著，檢查完再更新｜Fixed the background-mode (main/second screen) button vanishing the second time the assistant panel opens: the root recheck at the start of every task cleared the known result, so the panel thought there was no root; the old result is now kept until the recheck finishes.

## 1.0.1394

- 圈選提問：截圖或開啟畫面時的錯誤改成顯示提示，不再讓整個 App 當掉；接續對話時背景模式鈕不再被藏起來（以前看起來像被圈選鈕擠掉）｜Circle to ask: errors while taking the screenshot or opening the screen now show a message instead of crashing the whole app; in follow-up replies the background-mode button is no longer hidden (it used to look like the circle button pushed it out).

## 1.0.1393

- 新功能「圈選提問」：助理面板輸入列左邊多一顆 ◎，按下後面板收起、截下目前畫面，用手指圈出想問的地方，AI 看著圈起來的區域（加一張縮小的整個畫面當上下文）回答，還能接著追問；需要無障礙服務或 root／Shizuku 才能截圖｜New "Circle to ask": a ◎ button next to the assistant input row hides the panel and freezes the screen; circle something with your finger and the AI answers about that region (plus a small copy of the whole screen for context) and you can follow up; needs the accessibility service or root / Shizuku to take the screenshot.

## 1.0.1392

- 防止畫面文字誘導 AI：唯讀 shell 指令不能讀 /data、/sdcard、金鑰檔，也不能看通知、帳號、剪貼簿內容；AI 自己記的經驗若含網址、叫人忽略規則或略過確認、涉及密碼金額，一律不存｜Hardening against prompt injection from on-screen text: read-only shell commands can no longer read /data, /sdcard or key files, or dump notifications, accounts or the clipboard; lessons the AI saves for itself are rejected if they contain URLs, tell it to ignore rules or skip confirmations, or involve passwords or money.
- 發布的 APK 改成正式版（release，不再是 debug 版）並改名為 PhoneAI.apk；簽名金鑰不變，可直接覆蓋安裝｜Published APKs are now release builds (no longer debug builds) named PhoneAI.apk; the signing key is unchanged, so it installs over the previous version.

## 1.0.1391

- 傳訊息的「要送出嗎」確認，在沒有元件清單時（截圖模式、背景螢幕）也會跳出，不再被略過；修正檢查 root 時 `su` 卡住會讓 10 秒逾時失效｜The "send this message?" confirmation now also appears when there is no element list (screenshot mode, background screen) instead of being skipped; fixed the root check where a hung `su` made the 10-second timeout ineffective.

## 1.0.1390

- 匯出紀錄除了分享，也能直接存到「下載/PhoneAI」資料夾（按鈕和說明寫清楚了位置）｜Export log can now also be saved straight to the Download/PhoneAI folder (the button and its description now say where).

## 1.0.1389

- 匯出紀錄除了分享，也能直接存到「下載/PhoneAI」資料夾｜Export log can now also be saved straight to the Download/PhoneAI folder, not just shared.

## 1.0.1388

- 任務過程頁新增「匯出紀錄」：把最近 20 筆任務（每一步的思考、動作、結果、token）加診斷資訊存成文字檔，用分享選單傳出去；不含 API Key，也會遮蔽看起來像 Key 的字串｜The task history page has a new "Export log" button: it saves the last 20 tasks (each step's thinking, action, result, tokens) plus diagnostics as a text file you can share; it contains no API keys and masks anything that looks like one.

## 1.0.1387

- 選 AI 服務時，如果這個服務的 Key 已經有了，不再跳出「怎麼申請 Key」的教學｜When choosing an AI service whose key is already saved, the "how to get a key" walkthrough is skipped.

## 1.0.1386

- 換 AI 服務時，已經輸入過 Key 的服務不用再輸入；修正 Gemini 用到工具（查電量、App 數量）時連不上（工具回呼要帶回 thought_signature、不送空參數）；系統指令輸出太長時附上行數，數量改用 wc -l｜Switching AI service no longer asks again for a key you already entered; fixed Gemini failing when tools are used (battery, app count): tool calls now carry back the thought_signature and empty parameter schemas are not sent; long command output now reports its line count and counting uses wc -l.

## 1.0.1385

- 電量、儲存空間、幾點這類短問題不再用關鍵字攔截，平常交給 Haiku 5.5 聊天助理回答；只有連不上 AI（沒網路）時才用關鍵字直接回答（定時任務裡的短查詢不變）｜Short questions (battery, storage, time) are no longer caught by keywords; the Haiku 5.5 chat assistant answers them. Keyword answers are only used when the AI is unreachable (offline); scheduled short queries are unchanged.

## 1.0.1384

- 一句話問了好幾件事（電量加裝了幾個 App）不再只回答第一件，改交給聊天助理｜A question with several parts (battery plus how many apps) is no longer answered with only the first part; it goes to the chat assistant.

## 1.0.1383

- 問手機本身的資訊（電量、裝了幾個 App…）改走聊天，不再切到操作模式｜Questions about the phone itself (battery, how many apps…) now go to chat instead of the screen-operating mode.

## 1.0.1382

- 聊天模式也能新增三星提醒、執行唯讀系統指令（查電量、儲存空間、已安裝 App…），不必進操作畫面模式，更快更省；會改東西的指令一律拒絕｜Chat mode can now create Samsung reminders and run read-only system commands (battery, storage, installed apps…) without the screen-operating mode, so it is faster and cheaper; anything that changes something is refused.

## 1.0.1381

- 三星提醒可以直接新增（含日期時間或全天）：用 Android 16 的 AppFunctions（跟 Gemini 同一條路），不用開畫面也不用按儲存；需要 root 或 Shizuku，沒有時退回開新增畫面｜Samsung Reminder can now be created directly (with date/time or all-day) through Android 16 AppFunctions, the same path Gemini uses, with no screen and no Save tap; needs root or Shizuku, otherwise it falls back to opening the new-reminder screen.

## 1.0.1380

- 修正更新下載進度又沒有出現在 Now Bar（進度通知補上點擊動作與顏色，跟任務的 Now Bar 一致）｜Fixed the update download progress not showing on the Now Bar (the progress notification now has a tap action and colour, like the task Now Bar).
- 明確說「三星提醒」時，改由 AI 操作三星提醒 App，不再被當成行事曆事件｜When you explicitly say Samsung Reminder, the AI now operates that app instead of creating a calendar event.
- 新增「三星提醒」：AI 用三星提醒公開的分享入口直接開新增畫面並填好標題（不用 root），再自己設定時間並儲存｜Samsung Reminder: the AI opens its new-reminder screen with the title pre-filled via its public share entry (no root needed), then sets the time and saves.
- 要你接手時改用懸浮窗：畫面上方出現說明和「我做完了，繼續」按鈕，卡片可以拖動，其他地方照常可以操作（沒開無障礙服務才退回通知）｜Take-over requests now use a floating card at the top with a "Done, continue" button the card can be dragged and the rest of the screen stays usable (falls back to a notification only if the accessibility service is off).
- 要你接手時：Now Bar 只放短字（避免掉回一般通知），另外跳一則會響、會彈出的「請你接手」通知，上面有「繼續」按鈕｜Take-over requests: the Now Bar shows short text only (so it stays a Live Update) and a separate heads-up "Please take over" notification with a Continue button sounds and pops up.
- 批次動作一次最多 10 個（原本 6 個），連點版本號碼 7 次這類操作可以一次做完｜Batch actions now allow up to 10 steps (was 6), so things like tapping the build number 7 times fit in one batch.
- AI 遇到要你親自處理的事（解鎖圖形、PIN、密碼、驗證碼、指紋）會暫停並通知你接手，不再跳出蓋住畫面的對話框；做完按 Now Bar 的「繼續」，等你的這一步不算進步數｜When the AI hits something only you can do (unlock pattern, PIN, password, code, fingerprint) it now pauses and asks you to take over instead of showing a dialog that covers the screen; press Continue on the Now Bar when done, and the wait does not count as a step.
- 內建的計算機經驗只種手機上實際裝的那個計算機｜The built-in calculator lesson is now seeded only for the calculator app actually installed.
- 經驗去重：幾乎一樣的經驗不會重複存，帶進提示時也不會重複｜Lesson dedupe: near-identical lessons are no longer saved twice or repeated in the prompt.

## 1.0.1370

- AI 更會自己記經驗：走錯路（點錯、按過返回）或 5 步以上才完成時，會自動請便宜模型總結一句規律存成該 App 的經驗；提示也要求 AI 一找到正確位置就當場記下。移除手寫的內建經驗，改由 AI 自己學｜The AI learns experiences by itself more reliably: after a wrong turn (wrong tap, Back press) or a 5+ step success the cheap model summarises a one-sentence rule and saves it for that app, and the prompt asks the AI to note the right place the moment it finds it. The hand-written built-in experience was removed so the AI learns it itself

- 巨集不記任何有捲動或返回的做法（捲到的位置每次不一樣、返回代表走錯路），舊版已記下的這類巨集也不再重播｜Macros no longer record any run with a scroll or a Back press (scroll positions differ each time, Back means a wrong turn) and old macros of that kind are no longer replayed; a built-in experience says that on Samsung Settings Wi-Fi, Bluetooth and NFC are under Connections and "Connected devices" is not a switch page, so the AI takes fewer wrong turns

## 1.0.1368

- 巨集只記走得乾淨的那一次：中途有「返回」、捲動超過 2 次、或超過 8 步的做法不記（重播只會重走冤枉路）；任務過程會寫原因。先靠自動總結的經驗讓 AI 走對路，走乾淨的那次才升級成巨集｜Macros now record only clean runs: a run with a Back press, more than 2 scrolls or more than 8 steps is not recorded (replaying it would just redo the detour) and the task log says why; the auto-summarised experience first helps the AI find the right path, and the clean run then becomes a macro

## 1.0.1367

- 走了冤枉路（6 步以上）才完成的任務，AI 沒自己記經驗時，會請便宜模型用一句話總結「下次可以直接走的路徑」（例如「三星設定：藍牙在「連接」頁裡」）存成該 App 的經驗，並通知你；多花一次便宜呼叫（約 $0.0003）｜After a task that needed 6+ steps succeeds and the AI saved no lesson itself, the cheap model summarises the reusable path in one sentence (e.g. "Samsung Settings: Bluetooth is inside the Connections page") and saves it as that app's experience, with a notification; costs one cheap call (about $0.0003)

## 1.0.1366

- 巨集可以認出「說法不同的同一件事」：路由用的便宜模型（同一次呼叫，不多花一次）會看你記住的任務清單，確定是同一件事就改用那個巨集重播；不確定、對象或內容不同（不同的人、數字、頁面）就照原本一步步做｜Macros now recognise the same task said differently: the cheap router model (same call, no extra request) sees your remembered tasks and, when it is sure it is the same thing, replays that macro; if it is unsure or the target or content differs (another person, number or page) it works step by step as before

## 1.0.1365

- 巨集：可以點的整列（沒有自己文字的容器）會用裡面的第一個文字當名稱，這樣設定頁那類任務才記得下來；任務做完卻沒記住時，任務過程會寫「這次沒有記住成巨集：原因」｜Macros: a clickable row with no text of its own is now named after the first text inside it, so Settings-style tasks can be remembered; when a task finishes but was not remembered the task log now says why

## 1.0.1364

- Now Bar 按「暫停／繼續」時會短震動回饋，通知文字也立刻變成「已暫停」或「繼續中」，不用猜有沒有按到｜Pause / Resume on the Now Bar now gives a short vibration and the notification text changes at once to "Paused" or "Resuming", so you know the press registered

## 1.0.1363

- 「保護的 App」清單把讀過的 App 名稱存起來，下次打開只讀新裝的 App，幾乎立刻出現｜The protected-apps list caches the app names it has read, so next time only newly installed apps are read and it opens almost instantly

## 1.0.1362

- 「保護的 App」清單改在背景讀取 App 名稱，打開時不再卡一下｜The protected-apps list now loads app names in the background so opening it no longer stutters

## 1.0.1361

- 傳訊息不再一直問你：認得「去ig傳456給謝小明」「跟小明說…」這類說法；對話列表上的「17分鐘前傳送」「看過」是狀態文字，不再當成敏感的傳送按鈕；傳訊息時 AI 自己問過的確認算同意，程式不再多跳第二個｜Messaging no longer keeps asking: phrasings like "send 456 to Xiao Ming" are recognised; status text such as "sent 17 minutes ago" or "seen" in the chat list is no longer treated as a sensitive Send button; in messaging tasks the AI's own confirmation counts as your approval so the app no longer shows a second one

## 1.0.1360

- 確認框按下後會等它真的關掉、下面的 App 取回焦點再繼續（之前 AI 的下一個點擊可能打在還沒關完的對話框上，造成輸入框點了卻沒跳出鍵盤）｜After you answer a confirmation the app now waits until the dialog is really gone and the app below has regained focus before continuing (before, the AI's next tap could land on the closing dialog, so tapping an input box did not bring up the keyboard)

## 1.0.1359

- 傳訊息一定先問你：輸入框裡有了要傳的字、AI 要送出之前，程式一定跳確認框「要送出這則訊息嗎？」（不再只靠提示詞，Haiku 有時會跳過）；你同意過一次就不再問｜Messaging always asks first: once the text to send is in the input box and the AI is about to send it, the app itself shows "Send this message?" (no longer relying on the prompt, which Haiku sometimes skipped); asked only once per task

## 1.0.1358

- 傳訊息：輸入框不回報焦點（如 Instagram）時改找畫面最下面的輸入框輸入，並掃畫面確認文字真的進去，沒進去就回報失敗；傳訊息任務不再重播／記住巨集；沒輸入過任何字就說完成會被擋下（對話裡以前傳過的舊訊息不算）；AI 用了不存在的動作時，回報可用的動作清單｜Messaging: when the input box does not report focus (like Instagram) the lowest input box on screen is used and the screen is scanned to confirm the text really landed (otherwise it fails); messaging tasks no longer replay or record macros; finishing without ever typing is blocked (old messages in the chat do not count); an unknown AI action now gets the list of valid actions

## 1.0.1357

- 傳訊息：判斷「已送出」改成要在對話裡看到那段文字才算（輸入框空了但對話裡沒有，代表文字可能根本沒輸入進去）｜Messaging: "sent" now requires the text to show up in the conversation (an empty input box without the text in the chat means it may never have been typed)

- 輸入文字後會檢查文字真的進了輸入框：有些 App（如 Instagram 聊天室）無障礙回報成功、文字卻沒進去，會改用鍵盤輸入／貼上，還是不行就回報「輸入失敗」並提示先點輸入框，不再假裝已輸入｜After typing it now checks that the text really landed in the input box: some apps (like Instagram chat) report success through accessibility without taking the text, so it falls back to keyboard input / paste and reports "input failed" with a hint if that fails too, instead of pretending it typed

## 1.0.1356

- 傳訊息：用座標點到沒有名稱的傳送鈕、而且輸入框清空後，會自動把「這個 App 傳送鈕的位置」存進經驗，下次直接用座標點，不用再截圖找｜Messaging: when an unnamed Send button was tapped by coordinates and the input box then emptied, the button's position is saved as an experience for that app so next time it is tapped directly without a screenshot

- 傳訊息任務點「傳送」鈕不再多跳一次敏感操作確認（要不要送出已經問過你了）｜In messaging tasks tapping the Send button no longer triggers an extra sensitive-action confirmation (you were already asked whether to send)

- 修正傳訊息任務卡在 Enter 之後：AI 新增 look 動作（要看截圖時用）、傳訊息時只看元件清單確認有沒有送出（輸入框還留著字＝沒送出，清空＝已送出），不用截圖，最大步數從 8 提高到 12（傳訊息本來就要 8～10 步），提示也改成「要點傳送鈕，找不到就 look」｜Fixed messaging tasks stalling after Enter: the AI gets a look action (ask for a screenshot), in messaging tasks it checks whether the message was sent from the element list alone (text still in the input box = not sent, empty = sent) with no screenshot, the step limit rose from 8 to 12 (messaging needs 8-10 steps) and the prompt now says tap the Send button and use look when it is not in the element list

## 1.0.1355

- 傳訊息任務點「傳送」鈕不再多跳一次敏感操作確認（要不要送出已經問過你了）｜In messaging tasks tapping the Send button no longer triggers an extra sensitive-action confirmation (you were already asked whether to send)

- 修正傳訊息任務卡在 Enter 之後：AI 新增 look 動作（要看截圖時用）、傳訊息按 Enter 後下一步一定給截圖來確認是否送出，最大步數從 8 提高到 12（傳訊息本來就要 8～10 步），提示也改成「要點傳送鈕，找不到就 look」｜Fixed messaging tasks stalling after Enter: the AI gets a look action (ask for a screenshot), the step after pressing Enter in a messaging task always comes with a screenshot to check it was sent, the step limit rose from 8 to 12 (messaging needs 8-10 steps) and the prompt now says tap the Send button and use look when it is not in the element list

## 1.0.1354

- 「記住的步驟」從設定移到「功能 → 資料」｜"Remembered steps" moved from Settings to Functions → Data

## 1.0.1353

- 「保護的 App」清單改成看得見的勾選框並顯示已選幾個；「清除記住的步驟」改成「記住的步驟」，點進去可以看有哪些任務、重播過幾次，並單獨刪除或全部清除｜The protected-apps list now has visible checkboxes and a count; "Clear remembered steps" became "Remembered steps": tap to see which tasks are remembered and how often they were replayed, and delete one or clear all

## 1.0.1352

- 傳訊息任務：AI 說要確認一次後，同一件事不再重複問你；結尾前會檢查輸入的字是不是還留在輸入框，還在就代表沒送出，會擋下 done 並要 AI 點傳送鈕，避免「說傳好了其實沒送出」｜Messaging tasks: after you confirm once the AI no longer asks about the same thing again, and before finishing it checks whether the typed text is still in the input box (meaning it was not sent), blocks done and makes the AI tap Send, so it no longer claims "sent" when it wasn't

## 1.0.1351

- AI 比較測試新增「困難題」：捲動找軟體資訊、連續算數 (123+45)×6−78、讀取螢幕逾時、搜尋後進入結果、跨 App；可選簡單題、困難題或全部，困難題不保證每個模型都做得到｜AI comparison test now has a hard set: scrolling to find Software information, chained arithmetic (123+45)×6−78, reading the screen timeout, searching then opening a result and a two-app task; pick the easy set, the hard set or both, and the hard ones are not guaranteed to succeed

## 1.0.1350

- AI 比較測試的選單改成看得見的勾選框，並顯示「已勾選 N 個」｜The AI comparison picker now has visible checkboxes and shows how many are selected

## 1.0.1349

- AI 比較測試：不再有「目前設定」，直接列出所有存好 Key 的 API（Claude、Gemini…），勾選要比的兩個以上即可；模型沿用你現在設定的｜AI comparison test: no more "Current settings"; it lists every API with a saved key (Claude, Gemini...) and you tick two or more to compare, using the models you have set

## 1.0.1348

- AI 比較測試：「目前設定」改顯示實際的模型名稱，新增「自訂組合」可以自己挑服務並向它查詢模型、各選便宜與看圖模型｜AI comparison test: "Current settings" now shows the actual models, and a new "Custom combination" lets you pick a service, look up its models and choose the cheap and vision models

## 1.0.1347

- 新增「功能 → AI 比較測試」：同一批小任務（Wi-Fi 頁面、算 123＋45、時鐘碼錶、設定搜尋藍牙）用不同的 AI 服務各跑一遍，比成功與否、步數、時間與預估花費（需要 root 或 Shizuku，每個任務後強制關閉 App 以公平比較）｜New Functions → AI comparison test: runs the same small tasks with different AI services and compares success, steps, time and estimated cost (needs root or Shizuku; the app is force-stopped after each task for fairness)

## 1.0.1346

- AI 服務出錯時改用看得懂的說明：429＝額度用完（含重設時間與建議）、401 Key 不對、404 模型不存在、400 不支援看圖等，後面附原始回應｜AI service errors now come with a readable explanation: 429 = quota used up (with the reset time and what to do), 401 wrong key, 404 model not found, 400 vision not supported and so on, followed by the raw response

## 1.0.1345

- Now Bar 新增「暫停／繼續」：AI 在下一步開始前停住，等你按繼續（主螢幕與背景螢幕都能用），繼續時 AI 會知道畫面可能已被你改過｜The Now Bar gets a Pause / Resume button: the AI stops before its next step and waits for you to resume (main and background screens), and knows the screen may have changed when it continues

## 1.0.1344

- 背景模式：你在預覽視窗上用手接手時，AI 會暫停，放手後（或按「完成」）才繼續，並知道畫面可能已被你改過｜Background mode: when you take over on the preview window by hand the AI pauses and continues after you let go (or tap Done), knowing the screen may have changed

## 1.0.1343

- 「測試背景螢幕」的結果會一併顯示背景螢幕截到的畫面｜The "Test background screen" result now also shows the screenshot taken on the background screen

- 背景模式分成兩層：設定裡的「背景模式按鈕」（有 root 預設開，決定面板上顯示不顯示按鈕），和面板上的按鈕本身（每次自己按，預設關，開著才在第二螢幕執行任務）｜Background mode now has two layers: the "Background mode button" in Settings (on by default with root, decides whether the panel shows the button) and the button itself in the panel (you press it each time, off by default, tasks run on the second screen only when it is on)

## 1.0.1341

- 背景模式搬進 App（有 root 才能用）：開啟後任務在隱形的第二螢幕執行，不動目前畫面；任務結束自動收掉。設定新增「測試背景螢幕」可先檢查手機支不支援。目前只用截圖模式，銀行、支付類 App 不會在背景操作；預設仍是關閉｜Background mode is back in the app (root only): when on, the task runs on an invisible second screen without touching your current screen and is removed afterwards. Settings has a new "Test background screen" check. Screenshot mode only for now, banking and payment apps are never operated in the background; still off by default

## 1.0.1340

- 沒有 root 時，助理面板的背景模式按鈕和設定裡的「背景模式」「無障礙自動修復」不再顯示（這些都要 root）｜Without root the background-mode button in the assistant panel and the Background mode and Accessibility auto-repair rows in Settings are no longer shown (they need root)

## 1.0.1339

- 加入 Gemini Flash／Flash-Lite 的官網價格（含快取讀取價；3.6／3.8 Flash 在 2027-01-01 起自動改用翻倍後的價格）｜Added the official Gemini Flash / Flash-Lite prices (including the cache read rate; the 3.6 / 3.8 Flash prices automatically double from 2027-01-01)

## 1.0.1338

- 加入 OpenAI GPT-6 系列的官網價格（Astra、Sol、Terra、Luna），OpenAI 預設改成便宜 Luna、看圖 Terra｜Added the official OpenAI GPT-6 prices (Astra, Sol, Terra, Luna); OpenAI now defaults to Luna for cheap and Terra for vision

## 1.0.1337

- 價格改成官網的數字：Claude（Haiku／Sonnet／Opus 5.5 與快取讀寫的實際倍率，Sonnet 5.5 快取讀取是 $0.10）與 DeepSeek（deepseek-flash／v4-pro）；DeepSeek 預設改用 deepseek-flash（官網寫支援看圖）｜Prices now follow the official pages: Claude (Haiku/Sonnet/Opus 5.5 with the real cache write/read rates; Sonnet 5.5 cache read is $0.10) and DeepSeek (deepseek-flash / v4-pro); DeepSeek now defaults to deepseek-flash (the website says it supports vision)

## 1.0.1336

- 非 Claude 的模型也會估價（Gemini、GPT-4o、DeepSeek 內建粗估價），任務過程和「用量與上限」會顯示預估花費；在用量頁點「預估價格」可以改成官網的價格（免費方案實際不收費，這是「如果付費」的估算）｜Models other than Claude now get a cost estimate (rough built-in prices for Gemini, GPT-4o, DeepSeek), shown in the task log and in Usage and limits; tap "Estimated price" on the usage page to enter the website's price (free tiers aren't actually charged, this is the "if it were paid" estimate)

## 1.0.1335

- 「用量與上限」現在會分服務顯示今天與這個月的請求次數和 token（Claude、Gemini…）；OpenRouter、DeepSeek 可以直接在 App 查餘額，其他服務有按鈕開啟官網用量頁｜"Usage and limits" now shows requests and tokens per service for today and this month (Claude, Gemini...); OpenRouter and DeepSeek balances can be checked in the app, and other services get a button that opens their usage page

## 1.0.1334

- 任務過程顯示實際用的模型（換成 Gemini 等服務後不再顯示成 Haiku 5.5）｜The task log now shows the model actually used (no more "Haiku 5.5" after switching to Gemini or another service)

## 1.0.1333

- 選常見服務（Gemini、OpenAI…）時不再顯示網址與模型表單：直接輸入 Key，再選要不要查詢模型；只有「其他」才需要填網址｜Picking a known service (Gemini, OpenAI...) no longer shows the URL/model form: you just enter the key and choose whether to look up models; only "Other" asks for a URL

- Gemini 教學更正：Key 也可能以 AQ. 開頭｜Gemini guide corrected: keys may also start with AQ.

## 1.0.1331

- 修正「AI 服務」選單只顯示說明、選不了的問題；入口改到「功能 → 資料 → AI 服務與 API Key」（設定裡的那一列移除），設定導覽第 6 步也改成同一個流程｜Fixed the "AI service" dialog showing only text with nothing to pick; the entry moved to Functions → Data → "AI service and API key" (the Settings row is gone) and setup guide step 6 now uses the same flow

## 1.0.1330

- 「AI 服務」可以讓便宜模型和看圖模型各用不同的服務（例如便宜的 DeepSeek＋看圖的 Gemini）；Key 存好後可以向服務查詢現有的模型再選，不怕模型改名；OpenRouter 預設改用 openrouter/free，Groq 與 DeepSeek 的教學加上混搭建議｜"AI service" now lets the cheap model and the vision model use different services (e.g. DeepSeek for cheap + Gemini for vision); after saving the key you can look up the service's current models and pick one so renamed models don't matter; OpenRouter defaults to openrouter/free, and the Groq and DeepSeek guides suggest mixing services

## 1.0.1329

- 「AI 服務」改成先選服務：Claude、Google Gemini（有免費額度）、OpenAI、Groq、OpenRouter、DeepSeek 或其他；選了會顯示怎麼申請 Key 的步驟（可直接開申請頁面），網址和便宜／看圖模型自動填好預設值｜"AI service" now starts with a picker: Claude, Google Gemini (free tier), OpenAI, Groq, OpenRouter, DeepSeek or Other; it shows how to get a key (with a button to open the sign-up page) and pre-fills the URL and cheap/vision models

## 1.0.1328

- 新增 OpenAI 相容的 API 支援（OpenAI、DeepSeek、Groq、OpenRouter、Ollama 等）：到「設定 → 執行任務時 → AI 服務」填網址、便宜模型、看圖模型與 Key。操作手機的提示是照 Claude 調的，換別家要自己實測；網頁搜尋與費用統計仍只支援 Claude｜Added OpenAI-compatible API support (OpenAI, DeepSeek, Groq, OpenRouter, Ollama...): Settings → While running tasks → AI service, enter the URL, a cheap model, a vision model and the key. The phone-operating prompts are tuned for Claude so test other models yourself; web search and cost stats remain Claude-only

## 1.0.1327

- 設定導覽第一頁變簡潔，新增「三種模式比較」可以點開看 root／Shizuku／普通 的差別｜The first setup page is simpler and has a "Compare the three modes" link that explains the difference between root, Shizuku and Normal

## 1.0.1326

- 新增三種執行模式：root、Shizuku（不用 root，用 adb 等級權限）、普通（只用無障礙）。在設定導覽第一頁選，之後可到「設定 → 執行任務時 → 執行模式」改；Shizuku 模式下開關 Wi-Fi／藍牙、輸入、截圖等都走 Shizuku｜Three execution modes: root, Shizuku (no root, adb-level permission) and Normal (accessibility only). Pick one on the first page of the setup guide, change it later in Settings; in Shizuku mode Wi-Fi/Bluetooth toggles, input and screenshots all go through Shizuku

## 1.0.1325

- 固定指令：說「音量調到 30%」「聲音百分之八十」之類（0～100 的數字）會直接設定媒體音量，不經 AI、不花 token、不用 root｜Fixed command: saying "volume to 30%" (any number 0-100) sets the media volume directly, with no AI call, no tokens and no root

## 1.0.1324

- AI 新增「直接設定音量」動作：調到最小、最大或某個百分比一步完成（有沒有 root 都可以），不用一格一格按｜New "set volume" action for the AI: minimum, maximum or any percentage in one step (with or without root) instead of pressing one notch at a time

## 1.0.1322

- 沒 root 更新改用系統安裝程式的標準畫面（跟從瀏覽器安裝 APK 一樣，有「安裝中／完成／開啟」），不再按下確認後畫面就消失｜Non-root update now uses the system installer's normal screen (same as installing an APK from the browser, with installing / done / open) instead of the confirm screen closing right after you tap

## 1.0.1321

- 更新完成後會跳通知「已更新到 1.0.xxxx，點這裡開啟」（沒 root 更新時系統確認畫面按下後就收起，原本沒有任何完成提示）｜After an update finishes a notification says "Updated to 1.0.xxxx, tap here to open" (without root the system confirm screen closes as soon as you tap and there was no completion hint)

## 1.0.1320

- 沒 root 更新：安裝結果改由系統回呼處理，App 被收掉也不會漏掉安裝確認；背景被擋時用通知「點這裡完成安裝」，失敗會用通知顯示原因｜Non-root update: the install result is now handled by a manifest receiver so it isn't lost if the app is killed; if a background launch is blocked a "tap to finish installing" notification appears, and failures show their reason in a notification

## 1.0.1318

- 記憶頁不再需要 root：直接讀寫 App 自己的檔案（之前沒 root 會顯示「每種 su 寫法都讀不到」）；匯出／匯入沒 root 時改用檔案權限｜The memory page no longer needs root: it reads and writes the app's own file directly (before, without root it showed "every su variant failed"); export/import use file access when there is no root

## 1.0.1316

- 沒 root 更新：修正安裝時出現 Files still open；缺「安裝未知應用程式」權限時保留已下載的 APK，授權後不用重新下載｜Non-root update: fixed the "Files still open" install error; when the "install unknown apps" permission is missing the downloaded APK is kept so you don't download again after granting it

## 1.0.1314

- 沒 root 也能在 App 內更新：改用系統安裝程式，按一下「安裝」即可（第一次要允許「安裝未知應用程式」）｜In-app update now works without root: it uses the system installer and you tap "Install" (allow "install unknown apps" the first time)

## 1.0.1313

- 沒 root 時調亮度、自動旋轉缺「修改系統設定」權限，會先收起助理面板，不再蓋住系統的權限說明頁｜Without root, when brightness or auto-rotate needs the "modify system settings" permission, the assistant panel now closes first so it no longer covers the system permission page

## 1.0.1312

- 調音量後會回報目前音量（例如 3/15，0 就是最小），AI 不用再一格一格猜｜After changing the volume the result now reports the current level (e.g. 3/15; 0 = minimum), so the AI no longer has to guess step by step

## 1.0.1311

- 在 root 管理器撤銷 PhoneAI 的 root 之後，下一個任務會自動改用無障礙模式，不用重開 App｜After root is revoked for PhoneAI in the root manager, the next task automatically switches to the accessibility mode without restarting the app

## 1.0.1310

- 設定導覽把 root 改成「選用」：沒有 root 或還沒給可以直接跳過；更正無障礙服務的說明（現在也會讀畫面上的按鈕與文字）；判斷成沒有 root 只記一分鐘，之後才給 root 也會自動生效｜The setup guide now marks root as optional: skip it if you have no root or have not granted it yet; the accessibility description is corrected (it now also reads the buttons and text on screen); "no root" is remembered for only a minute, so granting root later takes effect automatically

## 1.0.1309

- 沒有 root 也能用：點擊、滑動、按鍵、截圖改走無障礙服務，開 App 走一般方式，亮度、旋轉、勿擾、音量用系統 API，Wi-Fi、藍牙、深色模式等只能打開對應的設定頁；設定裡可勾「不使用 root」模擬測試｜Works without root: taps, swipes, keys and screenshots use the accessibility service, apps open the normal way, brightness, rotation, Do Not Disturb and volume use system APIs, and Wi-Fi, Bluetooth, dark mode etc. can only open the matching settings page; a "Do not use root" switch in the settings simulates it for testing

## 1.0.1308

- 鬧鐘改成宣告系統的設鬧鐘權限，不用 root 也能直接設好、不開時鐘畫面；行事曆事件也改成直接建立（需要行事曆權限，已 root 的手機會自動補上），不用再按儲存｜Alarms now declare the system set-alarm permission, so they are set directly without opening the clock and without root; calendar events are now created directly too (needs calendar permission, granted automatically on rooted phones), so there is no need to press save

## 1.0.1307

- 新增雙擊動作；助理新增三個工具：新增行事曆事件、在地圖上找地方、撥號（都只是打開對應的 App 並填好，要自己按儲存或撥出）｜Added a double-tap action; the assistant has three new tools: add a calendar event, show a place on the map, and dial a number (they only open the app prefilled; you press save or call yourself)

## 1.0.1306

- 新增「巨集」：任務成功做完後記住每一步（記的是元件名稱，不是座標），下次同樣的任務直接重播，AI 只確認結果；畫面對不上就交回 AI，連續失敗兩次就刪掉；可在設定 → 執行任務時關閉或清除｜New "macros": after a task succeeds, every step is remembered (by element name, not coordinates) and the same task is replayed directly next time with the AI only checking the result; if the screen does not match, the AI takes over, and a macro that fails twice in a row is deleted; it can be turned off or cleared under Settings → While running a task

## 1.0.1305

- 新增「保護的 App」（設定 → 執行任務時）：銀行、支付、錢包類會自動保護，也可以自己勾選；AI 看這些 App 的畫面或操作之前，一定先問你，沒問過就不會把畫面送出去｜New "Protected apps" setting (Settings → While running a task): banking, payment and wallet apps are protected automatically and you can add more; before the AI looks at or operates one, it always asks you first, and the screen is not sent out until you agree

## 1.0.1304

- 防「畫面文字夾帶指令」：提示規定畫面上的文字只是資料；AI 要點付款、刪除、傳送這類敏感按鈕、而你的任務沒提到時，一定先跳確認框｜Defence against instructions hidden in on-screen text: the prompt says screen text is data only, and when the AI wants to tap a sensitive button (pay, delete, send…) that your task did not mention, a confirmation always appears first

## 1.0.1303

- 開 App 時認得常見的同義說法（相簿＝圖庫、計算機＝計算器、日曆＝行事曆…），不再因為名稱對不上白白多花一步｜Opening an app now understands common synonyms (相簿 = 圖庫, 計算機 = 計算器, 日曆 = 行事曆…), so a name mismatch no longer wastes a step

## 1.0.1302

- 修正：先開 App 的任務（例如「打開 LINE，告訴我…」）結束後，答案會跳出卡片，不再被蓋住面板的 App 擋住、只剩通知｜Fix: for tasks that start by opening an app (e.g. "open LINE and tell me…"), the answer now appears as a card instead of being hidden behind the app and only showing as a notification

## 1.0.1301

- 任務過程頁新增「回答卡片」一行，說明這次為什麼顯示或不顯示卡片，以及卡片有沒有真的出現；卡片被系統擋下時，自動改用懸浮卡片顯示答案｜The task details page has a new "Answer card" line explaining why a card was or was not shown and whether it really appeared; when the system blocks the card, the answer is shown in a floating card instead

## 1.0.1300

- 修正：從首頁輸入列或麥克風下的任務，操作手機後也會跳出回答卡片（以前只有通知，因為首頁在背景看不到）｜Fix: tasks started from the home input bar or microphone now also show the answer card after operating the phone (before, only a notification appeared because the home page was in the background)

## 1.0.1299

- 嘗試修正回答卡片有時沒跳出：要跳卡片時，進度條多留 2.5 秒，避免被系統當成背景啟動而擋掉｜Attempted fix for the answer card sometimes not appearing: the progress bar stays 2.5 seconds longer when a card is about to show, so the system does not block it as a background launch

## 1.0.1298

- 查手機本身的資訊（Android 版本、電量、容量、時間）時，不再先打開設定等 App，直接用指令查｜When asking for the phone's own information (Android version, battery, storage, time), the assistant no longer opens Settings or another app first and queries it with a command directly

## 1.0.1297

- 問資訊的任務（例如「告訴我最上面一個聊天室的名稱」），答案再短也會跳出回答卡片，不再只有通知｜Tasks that ask for information (e.g. "tell me the name of the top chat") now always show the answer card, even when the answer is short, instead of only a notification

## 1.0.1296

- 固定指令新增 16 個（0 token、不叫 AI）：開／關定位、NFC、行動數據（關閉會先確認）、自動亮度，亮度調最低／最高，打開／收起通知欄、快速設定，截圖、鎖螢幕、最近使用的 App；句子含「然後」等連接詞、或多出很多字的多件事任務，改交給 AI，不再只做前半｜16 new fixed commands (0 tokens, no AI): location, NFC, mobile data (turning it off asks first), auto-brightness, brightness minimum/maximum, notification shade and quick settings, screenshot, lock screen, recent apps; tasks with several steps ("then …") or a lot of extra words now go to the AI instead of only doing the first part

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
