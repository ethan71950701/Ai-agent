# 更新說明 · Changelog

每一條寫成「中文｜English」，App 依介面語言只顯示對應的那一半；Release 說明會用整段 `## Unreleased` 內容。
Each entry is written as "中文｜English"; the app shows the half that matches the interface language. The release notes use the whole `## Unreleased` section.

## Unreleased

## 1.0.1649

- 玻璃回答框加邊緣亮線、底色稍深，邊界更明顯｜Glass answer card gets a bright edge line and a slightly darker tint so its boundary is clearer

## 1.0.1648

- 回答框改成跟輸入列一樣的液態玻璃（顏色增強）；按住按鈕時圓角的變化放慢，不再瞬間跳變｜The answer card now uses the same liquid glass as the input bar (vivid glass); the corner change when pressing a button is slower, no more instant jump

## 1.0.1647

- 底欄與輸入列玻璃加「顏色增強」（像 iOS 液態玻璃：蓋住的地方飽和度拉高、暗背景提亮、黑底變淡）；設定 → 底欄玻璃（進階）可關｜Bottom bar and input bar glass get a "Vivid glass" option (like iOS Liquid Glass: boosted saturation, brighter over dark backgrounds, lighter dark tint); can be turned off in Settings → Bottom bar glass (advanced)

## 1.0.1646

- 助理面板的聊天回答改成邊收邊顯示（Claude；其他服務與帶網頁搜尋的回答仍是一次出現），不用等整段回完才看到字｜Assistant panel chat answers now appear as they stream in (Claude; other services and answers with built-in web search still appear all at once)
- 定時網路查詢：通知附上參考的網站，並跟上一次的結果比較（例如價格漲跌）；路由的提示詞只附上跟這句話有關的 App 名稱，更短更快｜Scheduled web lookups: the notification lists the source sites and compares with the previous result (e.g. price changes); the router prompt now only includes app names related to the request, so it is shorter and faster

## 1.0.1644

- 功能頁新增「定時任務」清單：點一項可「立刻試跑一次」或刪除；也可以說「試跑定時 1」｜New "Scheduled tasks" list in Features: tap one to test-run it now or delete it
- 定時網路查詢：對助理說「每天早上 8 點幫我查某某商品的價格」「每 3 天查一次…」，到時間自動用網路搜尋查、整理後用通知告訴你（只查公開網頁；「我的定時」「取消定時 1」可看與取消）｜Scheduled web lookups: tell the assistant "every day at 8 look up the price of …" or "every 3 days …"; at that time it searches the web and sends a summary notification (public web only; say "my scheduled tasks" / "cancel schedule 1" to view or cancel)

## 1.0.1642

- 不像操作手機的話（問問題、閒聊、記事情）直接用便宜模型聊天，不再先問路由；聊天時如果它發現其實要操作手機，會自己交給自動操作（再問路由決定是固定指令、開 App 還是操作畫面）｜Messages that don't look like phone actions now go straight to the cheap chat model without the router; if it finds the request needs phone operation it hands over to automatic operation (the router then picks command / open app / screen operation)

## 1.0.1641

- 「你好」「謝謝」這類寒暄不再先問路由（以前要多等好幾秒）；診斷的路由那行多顯示「聊天回答」花的時間，慢的時候能看出是路由慢還是回答慢｜Greetings like "hello"/"thanks" skip the router (it used to add seconds); the route diagnostic line now also shows the chat reply time

## 1.0.1640

- 回答後再長按電源鍵：接著上一輪繼續對話（回答卡還在、10 分鐘內），不是開新的；輸入列比較透明有玻璃感；已經設定過 Key 的服務可以選「換新的」（Claude 也能在這裡換 Key）｜Long-press power after an answer now continues the same conversation (answer card kept, within 10 min); the input bar is more see-through; services that already have a key can now be set to a new key (Claude too)

## 1.0.1638

- 續問時新的回答出現：舊卡像水一樣一塊一塊滲掉、新卡在同一處浮現（不再變半透明）｜Follow-up answers: the old card soaks away like water while the new one surfaces in place (no more see-through card)

## 1.0.1637

- 修正接著說第二句話時，面板上一輪的回答卡和底下的輸入列變半透明、看起來怪怪的：等新回答時它們保持原樣｜Fixed the previous answer card and the input row turning half-transparent while waiting for the next reply in a follow-up; they now stay as they are
- 網頁搜尋一律改用 Tavily（便宜）：聊天、畫圈／帶圖問答、自動操作的「搜尋」動作都是；Claude 內建的網頁搜尋（另外計費、比較貴）預設關閉，設定裡可以打開｜All web searches now use Tavily (cheaper): chat, circle-to-ask and image questions, and the automation "search" action; Claude's built-in web search (billed separately and more expensive) is off by default and can be turned on in Settings

## 1.0.1635

- 從模型清單選模型時，會先送一則極短的請求測試這個模型你的帳號能不能用（免費方案清單裡常有一堆不能用的）；不能用就顯示原因、不儲存，回到清單重選｜When you pick a model from the list, a tiny test request now checks whether your account can actually use it (free plans often list many unusable models); if not, the reason is shown, nothing is saved, and you can pick again

## 1.0.1634

- AI 請求遇到偶發的網路問題（連不上、逾時、伺服器暫時忙）會自動重試，不再「有時候連不上」；連線逾時由 20 秒縮短到 10 秒，失敗與重試都記在診斷頁的事件紀錄（按停止不會重試）｜AI requests now retry automatically on transient network problems (cannot connect, timeouts, temporary server errors) instead of sometimes failing; the connect timeout drops from 20 s to 10 s, every failure and retry is logged in the diagnostics events (pressing stop does not retry)

## 1.0.1633

- 聊天變快：便宜模型把一句話判成「聊天」時，就算它沒把握也直接聊，不再多問一次較強的模型來重新判斷（聊天真的需要操作手機時，本來就會自動改走自動操作）；任務過程的路由那行會顯示判斷花了幾毫秒｜Chat is faster: when the cheap model routes a message to chat, even with low confidence, it just chats instead of asking a stronger model to re-route (if chat really needs to operate the phone it already falls back to automation); the routing line in the task log now shows how many milliseconds it took
- 帶圖片的訊息不再逾時：面板等回答的時間由 30 秒延長到 2 分鐘，網路讀取逾時由 60 秒延長到 2 分鐘；診斷頁事件紀錄會記錄每次帶圖片聊天用了多久、成功或失敗的原因｜Messages with images no longer time out: the panel now waits up to 2 minutes instead of 30 seconds and the network read timeout is 2 minutes instead of 60 seconds; the diagnostics events log how long each image chat took and why it failed

## 1.0.1632

- 助理面板與首頁的「＋」選單新增「拍照」：用系統相機拍一張，跟上傳圖片一樣加到輸入列上方的縮圖，連同問題一起問（不需要相機權限）｜The "+" menu on the assistant panel and the home page now has "Take a photo": snap one with the system camera and it is added as a thumbnail above the input row just like an uploaded image, sent with your question (no camera permission needed)

## 1.0.1631

- 修正首頁標誌、聲波、極光、光暈的動畫在手機開機幾天後變得一格一格跳：動畫時間的精度不夠，現在改用不會失準的算法｜Fixed the home logo, voice wave, aurora and edge glow animations becoming choppy after the phone has been on for several days: the animation clock lost precision and now uses a calculation that stays accurate

## 1.0.1630

- 修正首頁標誌的波卡卡的：首頁的底欄玻璃與選取圓底不再跟著標誌的波每一幀重算（它們後面是純色），顯示晶片不再被拖住，波可以每一格都更新｜Fixed the choppy home logo wave: the home bottom-bar glass and its indicator no longer recompute on every frame of the logo animation (they sit on a solid color), so the GPU is no longer held back and the wave updates every frame

## 1.0.1629

- 修正打開「進階與診斷」會卡一下：診斷內容改在背景整理好再顯示｜Fixed a hitch when opening Advanced & diagnostics: the report is now built in the background

## 1.0.1628

- 修正更新時 Now Bar 的下載進度停在 50 幾 % 然後直接跳到安裝：通知改成最多每 0.4 秒更新一次，不會再被系統丟掉｜Fixed the Now Bar download progress freezing in the 50s and then jumping to install: the notification now updates at most every 0.4 s so the system no longer drops updates

## 1.0.1627

- 叫出面板時放大鏡帶跟面板幾乎同時出現，不再等輸入列出來後才有；首頁標誌的波改用跟其他動畫一樣的高更新率｜The panel's magnifying band now starts almost together with the panel instead of after the input row appears; the home logo wave uses the same high refresh rate as other animations

## 1.0.1626

- 首頁標誌的波改成要求穩定的更新率，不再忽快忽慢；叫出面板的放大鏡帶形變減弱、移動改成接近等速，開頭不再像跳一下｜The home logo wave now requests a steady refresh rate so it no longer stutters; the panel's magnifying band is gentler and moves at a near-constant speed so the start no longer jumps

## 1.0.1625

- 減少卡頓：叫出面板時，畫面截圖先在背景傳到 GPU，打開時不再頓一下；首頁的底欄玻璃不再每一幀重畫（底下本來就是純色），首頁說話時的聲波比較順｜Less stutter: the screenshot shown when the panel opens is uploaded to the GPU in the background, so opening no longer hitches; the home bottom-bar glass no longer redraws every frame (it sits on a solid color), making the home voice wave smoother

## 1.0.1624

- 修正「檢查並更新」查到新版本卻沒有跳出對話框、之後再按都沒反應：檢查完就不再卡在「忙碌中」，對話框顯示不了時改跳通知，卡住超過 3 分鐘也能重來｜Fixed "Check and update" finding a new version but showing no dialog and then ignoring further taps: it no longer stays busy after the check, falls back to a notification if the dialog cannot be shown, and recovers after 3 minutes if stuck

## 1.0.1623

- 自動檢查新版本改成打開 App 時最多每小時一次（以前一天一次，常常看不到新版提醒）｜Automatic update checks now run at most once an hour when you open the app (previously once a day, so new versions were often missed)

## 1.0.1622

- 叫出面板的開場：放大鏡帶放慢到約 1.1 秒、面板浮現放慢一點；開始前先讓畫面準備好效果，減少剛開始卡一下｜Panel opening: the magnifying band slows to about 1.1 s and the panel emerges a bit slower; the effects are prepared before they start to reduce the initial stutter

## 1.0.1621

- 叫出面板的開場照小米澎湃 2 的水波重做：一道柔和的「放大鏡帶」從輸入列出現的地方往外掃過目前的畫面（內容被微微放大、推擠，過了就恢復，約 0.65 秒，沒有光環、沒有彩色），面板同時從模糊中浮現、微微放大｜The panel's opening is redone after Xiaomi HyperOS 2's ripple: a soft magnifying band sweeps outward from where the input row appears across the current screen (content briefly magnified and pushed, about 0.65 s, no rings, no colors), while the panel emerges from a blur and grows slightly

## 1.0.1620

- 叫出面板的水波改慢、改得更像水：像一滴水滴在面板出現的地方，一圈圈往上散開（2 秒、越走越慢、越散越淡），波峰亮波谷暗有立體感，彩邊變淡｜The panel's opening ripple is slower and more water-like: like a drop falling where the panel appears, rings spread upward (2 s, slowing and fading as they go), with lighter crests and darker troughs for depth and a fainter color fringe

## 1.0.1619

- 叫出面板的玻璃漣漪收斂：只剩一圈主波加一圈很淡的尾波，形變減半，1.1 秒、很快變淡，不再晃得頭暈｜The panel's glass ripple is toned down: one main ring plus one faint trailing ring, half the distortion, 1.1 s and fading quickly, so it no longer feels dizzying

## 1.0.1618

- 叫出面板的開場改成玻璃漣漪：波前後面拖著幾圈越來越弱的波，把目前的畫面折射、形變得更明顯，速度稍慢（1.4 秒）；也修正面板剛出現時這一層還沒量好大小、玻璃波沒有播的問題｜The panel's opening is now a glass ripple: a few fading rings trail the wave front and refract and deform the current screen more visibly, a little slower (1.4 s); also fixed the wave sometimes not playing because the layer had not been measured yet

## 1.0.1617

- 設定列與功能磚塊的按壓改成 Material 3 Expressive 的「形狀變化」：按下去圓角變大、底色微亮，放開用彈簧彈回；不再有水波｜Rows and tiles now use Material 3 Expressive shape morphing on press: corners grow rounder and the color lifts slightly, then spring back on release; the water effect is gone
- 長按電源鍵叫出助理面板的開場動畫改成玻璃波：一道透明的波從螢幕上方往下擴散，把目前的畫面折射一下（邊緣有一點彩色光邊、白色反光），光環與光點改成白色，不再彩色｜The assistant panel's opening animation (long-press power) is now a glass wave: a clear wave spreads down from the top and refracts the current screen (slight color fringe, white highlight); the rings and sparkles are white instead of colorful

## 1.0.1616

- 水波按壓效果改乾淨：按住時內容保持清楚（不模糊、不染色、不抖），只有一圈波從手指擴散一次（經過時折射、帶彩色邊），手指底下一團很淡的光；放開時那團光一塊塊滲進卡片裡｜Cleaner water press effect: content stays crisp while held (no blur, tint or wobble); a single wave spreads from your finger once (refracting with a color edge as it passes) and a faint light sits under your finger; on release the light soaks into the card patch by patch

## 1.0.1615

- 水波按壓效果參考小米澎湃 2 的水波改成「毛玻璃」：水蓋住的地方內容變模糊、微微泛白，按住時幾乎不抖，波前有較明顯的彩色邊；放開時毛玻璃一塊塊變清楚｜The water press effect now follows Xiaomi HyperOS 2's ripple: the covered area turns frosted (blurred, slightly milky) and barely moves while held, the wave front has a clearer rainbow edge, and on release the frost clears patch by patch

## 1.0.1614

- 按壓效果改回水波：按住時拿掉水面亮斑，水色更淡（靠手指稍濃）、流動更輕，畫面更乾淨；放開時「滲進卡片裡」的動畫不變｜The press effect is back to water: while holding, the surface highlights are gone, the tint is fainter (a bit stronger near the finger) and the flow is gentler for a cleaner look; the soak-in on release is unchanged

## 1.0.1613

- 液態玻璃按壓效果改成整張卡片：卡片整圈邊緣（跟著卡片自己的圓角）變成玻璃邊（折射、色散、亮線），手指底下柔和地鼓起並帶一點高光，放開淡出；不再是一顆圓球｜The liquid-glass press effect now treats the whole card as the glass: the card's entire border (following its own corner shape) becomes a glass edge (refraction, color fringing, a bright line), with a soft bulge and highlight under your finger that fades on release — no longer just a round ball

## 1.0.1612

- 設定列與功能磚塊的按壓效果由「水」改成「液態玻璃」：手指底下浮出一顆玻璃透鏡（中間微放大、邊緣折射與色散、左上亮邊與高光、底下有影子），手指滑動時透鏡帶一點延遲跟著走，放開縮小融回卡片；跟底欄同一種質感｜The press effect on rows and tiles changed from water to liquid glass: a glass lens appears under your finger (slightly magnified in the middle, refraction and color fringing at the edge, a bright upper-left rim and highlight, a soft shadow), follows your finger with a little lag, and shrinks back into the card on release, matching the bottom bar

## 1.0.1611

- 水波按壓效果：按住時的水面反光大幅變淡、變小，不再像一大片煙霧；放開滲進去的動畫不變｜Water press effect: the surface highlights while holding are much fainter and smaller, no longer like a big patch of smoke; the soak-in on release is unchanged

## 1.0.1610

- 水波按壓效果：拿掉怪怪的網狀亮紋，改成幾塊寬而柔的水面反光（由水面起伏算出來，慢慢飄）｜Water press effect: the odd net-like light lines are gone, replaced by a few wide, soft highlights on the water surface (computed from the surface slope, drifting slowly)

## 1.0.1609

- 水波按壓效果：水看得出來了（淡淡的水色、水面飄動的亮紋、折射），放開時不再縮回去，而是「滲進卡片裡」——水面先靜下來，一塊塊水漬由外向內被吸收（邊緣有亮邊），手指按的地方最後才乾｜Water press effect: the water is now visible (a faint water tint, drifting light patterns, refraction) and on release it no longer shrinks back but soaks into the card — the surface calms, patches of water are absorbed from the outside in (with a bright edge), and the spot under your finger dries last

## 1.0.1608

- 水波按壓效果：按著不放時，水面改成像池水一樣緩慢、不規則地流動（不再一圈圈往外冒小波）；放開時波前的環先淡掉，水再平順地縮回手指的位置（收回的節奏也改成先慢、中間快、最後慢）｜Water press effect: while you hold, the surface now flows slowly and irregularly like a pool instead of emitting rings; on release the front ring fades first and the water then recedes smoothly to your fingertip (slow–fast–slow timing)

## 1.0.1607

- 水波按壓效果：水蓋住的整片範圍都有折射（水面上有行進中的小波、內容像隔著水微微放大），手指不放水就一直蓋著，放開時水收回手指的位置｜Water press effect: everything the water covers now refracts (small waves travelling across the surface, content slightly magnified as if seen through water); the water stays while you hold and retracts to your fingertip when you let go

## 1.0.1606

- 水波按壓效果加強：折射更明顯（位移 10→24 px）、色散與波峰反光加大、波更寬、持續更久｜Stronger water press effect: more visible refraction (displacement 10→24 px), more color fringing and crest highlight, wider waves, longer duration

## 1.0.1605

- 設定列與功能磚塊按下去改成「水波」：從手指的位置一圈圈擴散，波經過的地方內容會折射（被推擠、邊緣有一點色散），波峰有水面反光，越擴散越淡；捲動時不會誤觸發。如果這支手機的著色器跑不起來，會自動退回系統水波紋｜Rows and tiles now react to a press like water: rings spread from your finger, the content refracts as the wave passes (pushed around, with a little color fringing), crests catch a bit of light, and it fades as it spreads; scrolling does not trigger it. If the shader cannot run on this phone it falls back to the system ripple

## 1.0.1604

- 設定列與功能磚塊按下去改回水波紋（從手指位置擴散），但顏色加深到 20%，在深色卡片上看得清楚｜Rows and tiles are back to the radial press ripple (spreading from your finger), now at 20% opacity so it is clearly visible on dark cards

## 1.0.1603

- 功能頁的大磚塊按下去也改用狀態層（跟設定列一致），不再有水波紋｜The big tiles on the Features page now also use the press state layer (same as the settings rows) instead of a ripple

## 1.0.1602

- 設定與功能頁的列、卡片，按下去的回饋由「從手指擴散的水波紋」改成 Material 3 的狀態層：整張蓋一層淡色，按下淡入、放開淡出｜Rows and cards on the settings and features pages now use a Material 3 state layer instead of the radial ripple on press: a faint tint that fades in on press and out on release

## 1.0.1601

- 更新時 App 裡也會顯示下載進度視窗（進度條、百分比、MB；可選「背景進行」，通知列與 Now Bar 的進度照舊）；底欄玻璃那組展開再放慢一點｜Updating now shows a download progress dialog inside the app (progress bar, percentage, MB; you can choose "Continue in background", and the notification and Now Bar progress stay); the glass group opens a little slower

## 1.0.1600

- 設定群組展開變快：彈簧數值改用 Material 3 Expressive 的空間彈簧（約 0.26 秒就穩定，很高的群組稍慢）｜Settings groups open faster: the spring now uses Material 3 Expressive's spatial spring values (settles in about 0.26 s, a bit slower for very tall groups)

## 1.0.1599

- 設定群組的展開、收合改用彈簧動畫（非線性、有慣性）：展開會輕輕衝過頭再回穩，收合不回彈；越高的群組越軟、越久，連點會接著當下的速度動｜Settings groups now expand and collapse with spring physics (non-linear, with inertia): a gentle overshoot when opening, none when closing; taller groups are softer and slower, and tapping again continues from the current velocity

## 1.0.1598

- 設定裡群組展開、收合的動畫改柔和：時間跟著內容高度走（越高越久），不再一瞬間開完（底欄玻璃那一組以前看起來像沒有動畫）｜Settings groups now expand and collapse more softly: the duration follows the content height, so tall groups no longer snap open (the glass group used to look like it had no animation)

## 1.0.1597

- 登入頁、銀行類畫面要你接手時也恢復用懸浮卡片（以前點不到的原因是多餘的懸浮層，已經修好，不是卡片）｜Handoff on login pages and banking screens uses the floating card again (the taps were blocked by the extra overlay, which is fixed, not by the card)

## 1.0.1596

- 修正 AI 做任務時，Google 登入頁這類 App 整頁點不到：任務進行中不再多放一層不受信任的全螢幕懸浮層（無障礙服務已經有受信任的那一層）｜Fixed apps such as the Google sign-in page ignoring all taps while the AI runs a task: an extra untrusted full-screen overlay is no longer added during tasks (the accessibility service already provides a trusted one)

## 1.0.1595

- 「請你接手」恢復成預設顯示懸浮卡片；只有登入頁、銀行類、Google 登入這類會因為卡片而點不到的畫面，才自動改跳通知（有「繼續」）｜The "please take over" card is back by default; only on screens where it would block taps (login pages, banking apps, Google sign-in) does it automatically switch to a notification (with "Continue")

## 1.0.1594

- 要你自己輸入密碼、解鎖時，預設改成只跳一則通知（有「繼續」），不再放懸浮卡片（部分 App 例如 Google 登入頁，卡片出現時畫面點不到）；想要卡片可在設定「接手時顯示懸浮卡片」打開｜When you need to type a password or unlock yourself, only a notification (with "Continue") is shown by default and the floating card is gone (some apps, such as the Google sign-in page, ignored taps while the card was up); turn on "Show a floating card for handoff" in Settings to get the card back

## 1.0.1593

- 畫圈提問改成只送一張圖：整個畫面，選取的地方畫一個紅框（白邊），不再另外裁一塊、也不再多送一張縮小圖｜Circle-to-ask now sends a single image: the whole screen with a red rectangle (white outline) around your selection, instead of a separate crop plus a shrunken full screenshot

## 1.0.1592

- 「請你接手」卡片明確設成卡片以外的觸控穿透到底下的畫面；卡片出現與關閉時，把螢幕上所有視窗記進診斷頁的事件紀錄，畫圈提問送出時也記下判斷結果，方便查「點不到畫面」與「沒有先問」｜The "please take over" card is now explicitly non-modal so touches outside the card reach the screen below; when it appears and closes, every window on screen is logged to the diagnostics events, and circle-to-ask logs its sensitivity decision, to help debug "cannot tap the screen" and "did not ask first"

## 1.0.1591

- 敏感畫面先問你再傳給 AI：①畫圈提問以前完全不檢查，現在在金融／支付／密碼管理類 App 會先跳確認框 ②自動判斷多了密碼管理器與驗證器（Authenticator、Bitwarden、1Password…）③不是這些 App，但畫面有密碼輸入框（登入頁）也會先問；自動操作與畫圈都適用，同一個任務同意過就不再問｜Sensitive screens now ask before they are sent to the AI: (1) circle-to-ask used to skip the check and now asks in finance/payment/password-manager apps; (2) automatic detection now includes password managers and authenticators (Authenticator, Bitwarden, 1Password…); (3) any other app whose screen has a password field (a login page) also asks first; this applies to automation and circle-to-ask, and is asked once per task

## 1.0.1590

- 修正「我已經取消永久記憶，AI 還說是永久記憶」：AI 會沿用先前對話裡過時的說法，現在只看當下的記憶清單、要改要刪一律先呼叫工具；在記憶頁改星星時也會清掉對話紀錄｜Fixed the AI still calling a note permanent after you un-starred it: it carried over an outdated statement from earlier in the chat; it now trusts only the current memory list, always calls the tool instead of refusing on its own, and the chat history is cleared when you change a star on the memory page
- 診斷頁新增「記憶筆記（AI 看到的）」：列出每則筆記的編號、有沒有 📌、字數和標題，方便查「AI 說是永久記憶」這類問題｜The diagnostics page now lists "Memory notes (what the AI sees)": each note's id, whether it is pinned, its length and title, to help debug cases like the AI claiming a note is permanent

## 1.0.1588

- 長筆記被 AI 改成很短的字（例如把標題當成內容寫進去）時，會先跳確認框問你；每次改內容也會留一份上一版｜When the AI tries to shrink a long note to a few words (for example writing a title as the content), a confirmation dialog now asks first; the previous text is also kept on every content change

## 1.0.1587

- AI 現在可以只改某則筆記的標題或摘要（不用重寫全文）；記憶頁切換星星（永久記憶）改成每次重新讀檔、只改那一項再存，避免資料過時沒存進去｜The AI can now change just a note's title or summary without rewriting the whole text; toggling the star (permanent memory) on the memory page now re-reads the file and changes only that item, so a stale copy can no longer swallow the change

## 1.0.1586

- 更新後第一次打開，會跳視窗列出跳過的這幾版的更新內容（先列 8 版，更早的點「顯示更早的 N 版」展開）；也可以從「功能 → 更新 → 更新內容」隨時看｜After an update, the first launch shows what changed in every version you skipped (8 at first; tap "Show N older versions" for the rest); you can also open it any time from Features → Update → What's new

## 1.0.1584

- 點開長筆記時，內容上面多了「標題」「摘要」兩格，可以自己改｜Opening a long note now shows editable "Title" and "Summary" fields above the content

## 1.0.1583

- 助理的記憶頁：超過 60 字的長筆記改成顯示「標題＋一行摘要」，點開才是全文；搜尋也會找標題和摘要。短筆記、待辦、經驗照舊｜Assistant memory page: notes longer than 60 characters now show a title and a one-line summary (tap to open the full text); search also matches titles and summaries. Short notes, to-dos and lessons are unchanged

## 1.0.1579

- 助理面板上傳圖片後，訊息跟一般聊天完全一樣（記憶、行事曆、鬧鐘等工具都能用），只是這一輪改用看圖模型多看一次圖｜In the assistant panel, a message with uploaded images is now handled exactly like a normal chat (memory, calendar, alarms and other tools all work); the vision model just gets one extra look at the images for that turn
- 回答「從記憶的課表或清單查資料再推算」這類問題（例如下一節是什麼課）時，AI 會先在隱藏區塊逐項寫出對應再回答，減少差一格的錯誤；隱藏的推算不會顯示｜For questions that look something up in a timetable or list in memory and then work it out (e.g. what the next class is), the AI now writes the matching step by step in a hidden block before answering, to avoid off-by-one mistakes; the hidden working is never shown
- 輸入列右邊的麥克風、紙飛機和左邊的「＋」改成同一套規格（高度 22dp、線寬 1.9dp、同一個白色）；正在聽時不再是實心藍圓，改成麥克風外面一圈主色圓環｜The microphone and send icons on the right and the "+" on the left now share one spec (22dp tall, 1.9dp stroke, same white); while listening it is no longer a solid blue disc but a mic inside an accent-colored ring
- 首頁輸入列左邊也有「＋」了：可以上傳圖片、切換在這個畫面或背景螢幕操作，跟助理面板一樣｜The home page input row now has the "+" too: upload images and switch between operating on this screen or the background screen, same as the assistant panel

## 1.0.1578

- 助理面板左邊的背景模式鈕換成「＋」：點開可選「在這個畫面操作／在背景螢幕操作」，也能「上傳圖片」（從相簿選最多 4 張，輸入列上方有縮圖可移除），圖片連同問題一起交給看圖的模型回答，之後可接著問｜The assistant panel's background-mode button is now a "+": tap it to choose "Operate on this screen / on the background screen" or "Upload images" (up to 4 from the gallery, shown as removable thumbnails above the input row); images are sent with your question to the vision model and you can keep asking

## 1.0.1577

- 自動操作的步數不再固定 12 步：一開始給 20 步（設定 → 執行任務時可調 8～40），用完還沒完成就跳確認框問要不要再加 10 步（最多 60 步）｜Automation is no longer capped at a fixed 12 steps: it starts with 20 (adjustable 8-40 in Settings → While running tasks), and when they run out a dialog asks whether to give it 10 more (60 at most)

## 1.0.1576

- 更新檢查改成列出最近的幾個版本、挑編號最大的（以前用 GitHub 的「最新」，順序可能不是編號最大）；打開 App 時每天最多檢查一次，有新版本在通知欄提醒（設定 → 更新，可關）；診斷頁新增「更新檢查」那一行｜The update check now lists recent releases and picks the highest build number (it used GitHub's "latest", which is not always the highest number); when you open the app it checks at most once a day and posts a notification for a new version (Settings → Updates, can be turned off); Diagnostics has a new "Update check" line

## 1.0.1575

- 聲紋可以選模型：CAM++（28 MB，預設）或 ERes2Net large（116 MB，較準較重）；各模型有自己的下載檔與聲紋，換模型要重新下載、重新登錄｜The voiceprint model is selectable: CAM++ (28 MB, default) or ERes2Net large (116 MB, more accurate but heavier); each has its own download and voiceprint, so switching means downloading and enrolling again

## 1.0.1574

- 「打開 Google 地圖導航到某處」：路由不再只回「打開 App」（以前只開了 App 就說完成）；聊天助理新增導航工具，用 Google 地圖直接開始導航（開車／走路／騎車／大眾運輸）｜"Open Google Maps and navigate to somewhere": routing no longer answers with just "open app" (it used to open the app and call it done); the chat assistant has a new navigation tool that starts Google Maps navigation (drive/walk/bike/transit)

## 1.0.1573

- 新增「只回應我的聲音（實驗）」開關與「聲紋門檻」滑桿（設定 → 語音，預設關）：聲音跟登錄的聲紋不像時，不自動送出，文字留在輸入框等你確認｜New "Only respond to my voice (experimental)" switch and "Voiceprint threshold" slider (Settings → Voice, off by default): when the voice does not match your enrolled voiceprint nothing is sent automatically and the text waits in the box for you to confirm

## 1.0.1572

- 思考中的聲波改成「一個波包由左掃到右」，跟聽的時候整條隨聲音起伏明顯不同｜The thinking wave is now a single bump sweeping from left to right, clearly different from the listening wave that rises and falls with your voice across its whole width

## 1.0.1571

- 登錄聲紋改成 4 句話、每句念 2 次（共 8 段），聲紋比較穩｜Voiceprint enrollment is now 4 sentences read twice each (8 clips) for a steadier voiceprint

## 1.0.1570

- 聲紋實驗（診斷頁，只量不攔）：可下載聲紋模型（28MB）、念三句話登錄自己的聲音，之後每次說話都會算「跟你的聲紋有多像」，在診斷頁與最近事件看；不影響任何功能｜Voiceprint experiment (Diagnostics, measure only): download the voiceprint model (28 MB), read three sentences to enroll your voice; afterwards every utterance is scored for how much it resembles your voiceprint, shown in Diagnostics and Recent events; it changes no behavior

## 1.0.1569

- 拿掉實驗功能「頂欄毛玻璃」，標題列回到原本的樣子（漸層淡出）｜Removed the experimental "Frosted top bar"; the title bar is back to the fade version

## 1.0.1568

- 頂欄毛玻璃改成漸進式模糊：標題列後面最糊，往下模糊半徑慢慢變小直到清楚，不再用「淡出」，所以沒有明顯的邊與色差｜Frosted top bar now uses progressive blur: strongest behind the bar and shrinking smoothly to fully sharp below it, instead of a fade, so there is no visible edge or color step

## 1.0.1567

- 頂欄毛玻璃：模糊更大、底色更濃，後面亮卡片和暗卡片造成的明暗落差變小｜Frosted top bar: stronger blur and tint so the brightness gap between a bright card and a dark card behind it is smaller

## 1.0.1566

- 頂欄毛玻璃：修好玻璃位置錯（固定高度的標題列沒有後續通知，玻璃停在高度 0 時算的位置）與標題消失（改用工具列自己的標題）｜Frosted top bar: fixed the glass being misplaced (a fixed-height bar sends no further change notifications, so the glass stayed at the position computed while its height was still 0) and the missing title (it now uses the toolbar's own title)

## 1.0.1565

- 頂欄毛玻璃：標題列改成固定高度、不再收合（大標題往上收時會被切掉一半）；玻璃底色改成跟頁面背景同色，過渡不再有色差斷層｜Frosted top bar: the title bar is now a fixed height and no longer collapses (the large title used to be cut in half as it moved up), and the glass tint is the same color as the page background so the fade has no color seam

## 1.0.1564

- 頂欄毛玻璃：底色改成稍微提亮的淡色（不再偏暗、跟頁面色差大）；標題字往上收的時候不再被切掉一半｜Frosted top bar: the tint is now a slightly lightened soft color (no longer dark and mismatched with the page), and the title is no longer cut in half as it moves up

## 1.0.1563

- 頂欄毛玻璃：底邊不再是一條硬邊，玻璃往下多延伸一小段並漸漸變透明，從模糊自然過渡到清楚的內容｜Frosted top bar: the bottom edge is no longer a hard line; the glass extends a little further down and fades out so it blends smoothly from blurred to sharp content

## 1.0.1562

- 頂欄毛玻璃：修好後面的內容永遠畫成「沒捲動」的頁面頂端（系統錄捲動容器時會減掉捲動量，手動畫要自己減）｜Frosted top bar: fixed the glass always showing the unscrolled top of the page (the system subtracts the scroll offset when it records a scroll container; drawing manually has to do it itself)

## 1.0.1561

- 新增實驗功能「頂欄毛玻璃」（設定 → 外觀，預設關）：功能、設定頁的標題列變成模糊的玻璃，內容捲到它後面會透出來｜New experimental "Frosted top bar" (Settings → Appearance, off by default): the title bar on Features and Settings becomes frosted glass, content scrolling behind it shows through blurred

## 1.0.1559

- 標題列下緣加一段漸層淡出：內容捲到標題列下面時慢慢淡掉，不再被一刀切成一條黑邊｜A soft gradient fade under the title bar: content scrolling beneath it now fades out instead of being cut off in a hard dark line

## 1.0.1558

- 子頁完全展開後四個角也保持螢幕的 R 角（以前展開完把圓角裁切關掉，錄影、截圖看起來是直角）；圓角值改存在各頁自己身上，不再互相影響｜Sub-pages keep the screen's rounded corners after they are fully open (the clip used to be switched off, so recordings and screenshots showed square corners); the corner value now lives on each page instead of being shared

## 1.0.1557

- 預測返回一開始有時是直角：手勢一開始就先設好圓角，不再沿用上一個頁面留下的值｜Predictive back sometimes started with square corners: the corner radius is now set as soon as the gesture starts instead of reusing a value left by the previous page

## 1.0.1556

- 子頁完全展開時的圓角改成手機螢幕的 R 角（以前是直角），縮回卡片時再漸變成卡片的圓角；預測返回一開始滑就是螢幕的 R 角｜When a sub-page is fully open its corners now match the phone's screen corner radius (they used to be square), blending into the card's corners as it shrinks back; predictive back starts with the screen's corner radius

## 1.0.1555

- 所有子頁的展開、返回、預測返回都有圓角：不是點卡片打開的頁面（對話框按鈕、第一次開啟…）改用畫面中間一張虛擬卡片當起點；太久以前點的位置不再被拿來用｜Every sub-page now has rounded corners when opening, closing and on predictive back: pages not opened from a card (dialog buttons, first run...) start from a virtual card in the middle of the screen, and an old tap position is no longer reused

## 1.0.1554

- 預測返回改成跟展開、返回同一套動畫：頁面隨手指縮回你點開的那張卡片（同樣的曲線與圓角），放手再接著縮完；取消就回到原來大小。以前是整頁慢慢變透明｜Predictive back now uses the same container-transform animation as opening and closing: the page shrinks back toward the card you opened it from as you swipe (same curve and corners), finishes shrinking when you release, and returns to full size if you cancel. It used to just fade the whole page

## 1.0.1553

- 任務過程：點進詳細頁再返回，動畫不再壞掉（以前返回時整串卡片被清掉重建，縮回去的那張卡片剛好不見了；現在內容沒變就不重建）｜Task log: opening a detail page and coming back no longer breaks the animation (the whole card list used to be cleared and rebuilt on return, so the card being shrunk back into had vanished; now it is left alone when nothing changed)

## 1.0.1552

- 語音可以一直說下去：說到約 15 秒、遇到停頓時，前面那一段先辨識固定下來，繼續錄下一段；邊說邊顯示只算目前這一段，不會越來越慢。安全上限改成 3 分鐘｜Voice can go on as long as you like: after about 15 seconds, at a pause, the earlier part is recognized and locked in while recording continues; the live text only re-decodes the current part, so it no longer slows down. The safety limit is now 3 minutes

## 1.0.1551

- 修好「回到 App 後底欄玻璃消失、透鏡跑位、圖示漂到頁面上」：上一版重建玻璃時，新的繪圖節點沒有設大小，等於畫在 0×0；現在重建後立刻補上大小｜Fixed the bottom bar glass vanishing, the lens drifting and icons ghosting onto the page after returning to the app: the render nodes recreated last time had no size (0x0); the size is now set right after recreating
- 語音撞到錄音上限（30 秒）時不再自動送出：字留在輸入框，面板按麥克風可以接著說（接在後面）、或按送出｜When recording hits the 30 s limit the text is no longer sent automatically: it stays in the box, and in the panel you can tap the mic to keep talking (it appends) or send

## 1.0.1550

- 診斷頁「最近事件」多記語音的過程：錄音為什麼停（停頓、手動、30 秒上限）、辨識了幾個字、倒數開始、被取消或自動送出，查語音突然自己送出的問題用｜Diagnostics "Recent events" now also record the voice flow: why recording stopped, how many characters were recognized, countdown start, cancel or auto-send, to investigate voice suddenly sending by itself

## 1.0.1549

- 長句語音：邊說邊顯示的文字不再讓前面的字消失（太長只顯示最後 60 字、結果突然變短就先不更新），錄音上限 20 秒改 30 秒｜Long sentences: the live text no longer loses its beginning (only the last 60 characters are shown, and a sudden shorter result is ignored), and the recording limit goes from 20 s to 30 s

## 1.0.1548

- 說話稍微停頓就被切掉：離線辨識的「說完」停頓時間從固定 1 秒改成預設 1.6 秒，並可在設定 → 語音調整（0.8～4 秒）｜Getting cut off when you pause briefly: the pause that ends your sentence in offline recognition is now 1.6 s by default instead of a fixed 1 s, adjustable in Settings → Voice (0.8-4 s)

## 1.0.1547

- 玻璃高光：上層容器的縮放超出合理範圍時不再拉歪邊緣法線（限制在 0.8～1.5）｜Glass highlight: the accumulated parent scale is now limited to 0.8–1.5 so a stale scale cannot skew the edge normals

## 1.0.1546

- 診斷頁新增「最近事件」：離開、回來（背景多久）、系統通知回收記憶體、螢幕開關，查底欄偶爾變形時用｜Diagnostics: a "Recent events" list (leave, return with time in background, memory-trim notices, screen on/off) to help investigate the occasionally deformed bottom bar

## 1.0.1545

- 診斷頁新增「透鏡著色器」那一行（餵給透鏡的尺寸、光源、縮放），以及「重建玻璃」按鈕：底欄變梯形時按一下，看會不會恢復｜Diagnostics: a "Lens shader" line (size, light and scale fed to the lens) and a "Rebuild glass" button to press when the bottom bar is deformed, to see whether it recovers

## 1.0.1544

- 底欄偶爾變梯形（掛在背景再回來）：回到 App 時，玻璃的繪圖節點與著色器改成全部換新的，不再沿用可能壞掉的舊物件｜Bottom bar occasionally turning into a trapezoid (after leaving the app in the background): on return the glass now gets brand-new render nodes and shaders instead of reusing possibly broken ones

## 1.0.1543

- 語音邊說邊顯示文字（離線 SenseVoice 約每 0.8 秒重新辨識一次到目前的整段）；辨識完停 1 秒自動送出，首頁也一樣，這 1 秒內動輸入框就取消｜Speech now shows text while you talk (offline SenseVoice re-decodes everything so far about every 0.8 s); after recognition it sends automatically after 1 second, on the home page too, and touching the input within that second cancels it

## 1.0.1542

- 語音辨識時的聲波整條都會動（以前只有中間在動、兩邊很空），兩端用透明度淡出｜The listening wave now moves across its whole width (before, only the middle moved); the ends fade out with transparency

## 1.0.1541

- 首頁輸入列照 Material 3 搜尋列調整：高 56dp、尾端麥克風不再放灰色圓底（有字或正在聽才是主色實心圓）；底部光暈照舊到處流動，另外左右兩邊各加一團固定的淡底光，擠到一邊時另一邊不會全黑｜Home input bar follows the Material 3 search bar: 56dp tall, the trailing mic has no grey disc (a filled primary circle only when there is text or while listening); the bottom glow still roams freely, with a faint fixed glow on each side so one side never goes fully black

## 1.0.1540

- 首頁輸入列照 Material 3 搜尋列調整：高 56dp、尾端麥克風不再放灰色圓底（有字或正在聽才是主色實心圓）；底部光暈四團各守一段、只在原位附近擺動，不再整團擠到一邊讓另一邊全黑｜Home input bar follows the Material 3 search bar: 56dp tall, the trailing mic has no grey disc (a filled primary circle only when there is text or while listening); the four glow blobs each keep their own region and sway nearby, so one side no longer goes fully black

## 1.0.1539

- 首頁輸入框改成跟助理面板、底欄同一種液態玻璃膠囊（兩端半圓、白字）｜Home input bar is now the same liquid-glass pill as the assistant panel and bottom bar (rounded ends, white text)

## 1.0.1538

- 首頁：底欄後面鋪一層跟背景同色的底，液態玻璃不再染上光暈的彩色漸層｜Home: a background-colored layer sits behind the bottom bar so the liquid glass no longer picks up the glow's color gradient

## 1.0.1537

- 記憶改成「標題＋摘要」索引：長筆記在提示裡只列標題與一句摘要，要看細節時助理用 memory_read 讀全文；標題與摘要由寫入的模型順便產生，不多花呼叫｜Memory now uses a title + summary index: long notes appear in the prompt as a title and one-line summary, and the assistant reads the full text with memory_read when it needs details; the title and summary come from the model that writes the note, at no extra call

## 1.0.1536

- 路由：便宜模型自己說沒把握時，才多問一次較強的模型；聊天助理新增筆記前會先看有沒有同一件事或互相矛盾的舊筆記，有就改那一則，不再並排新增｜Routing: only when the cheap model says it is unsure is a stronger model asked once more; before adding a note the chat assistant now checks for an older note on the same thing or one that contradicts it and updates that one instead of adding a second

## 1.0.1535

- 問「週一英文課在哪一節」這類答案在筆記裡的問題：路由會看到筆記開頭，答案在筆記裡就回聊天、不再去開 App；聊天助理看到的筆記不再截成 100 字（改 300 字）｜Questions whose answer is in your notes (like "when is Monday's English class") now route to chat instead of opening an app; the chat assistant sees notes up to 300 characters instead of 100

## 1.0.1534

- 自動操作修好一個反覆出錯的原因：模型有時先寫一個空的 <invoke name="tap"></invoke>、後面才接正確的 JSON，以前只拿到沒有座標的動作，白花兩步；現在優先用後面的 JSON｜Automation: fixed a recurring failure where the model first writes an empty <invoke name="tap"></invoke> and only then the correct JSON; we used to keep just the empty action and waste two steps, now the JSON is used

## 1.0.1533

- 路由：提示加上「要去別的 App 找東西、看東西，就算還要記，也是自動操作」的說明與例子；任務過程頁顯示便宜模型這次的判斷（chat／agent…）｜Routing: the prompt now says that looking things up in another app is automation even if you also want it remembered, with examples; the task log shows what the cheap model decided (chat/agent/…)

## 1.0.1532

- 自動操作：look 之後的看圖步驟改用較大的截圖（寬 720，原本 432），相簿縮圖、小圖示比較看得清楚｜Automation: the screenshot step after a look now uses a larger image (720 px wide instead of 432) so gallery thumbnails and small icons are easier to read

## 1.0.1531

- 自動操作：tap／雙擊／長按沒給座標時不再點到螢幕左上角還說「已點擊」，會回錯誤讓模型重來；出錯的那一步在任務過程裡附上模型原始回覆｜Automation: tap/double-tap/long-press without coordinates no longer taps the top-left corner and reports success; it returns an error instead, and the failed step shows the model's raw reply in the task log

## 1.0.1530

- 自動操作：看圖那一步模型說了不存在的動作時，下一步還是給截圖重來，不再退回元件清單又 look 一次｜Automation: if the model names a non-existent action on a screenshot step, the next step retries with the screenshot instead of falling back to the element list and looking again

## 1.0.1529

- 路由不再用關鍵字：開 Wi-Fi、音量、截圖這些指令也由便宜模型判斷（多一次很快的判斷，沒網路時固定指令暫時不能用）｜Routing no longer uses keywords: even commands like Wi-Fi, volume and screenshot are decided by the cheap model (one quick extra call; fixed commands do not work offline for now)

## 1.0.1528

- 「去某個 App／相簿看畫面再記起來」這類指令現在由便宜模型判成自動操作（不再因為有「記」字被當成聊天），自動操作新增「存成筆記」動作，看完畫面可以直接存進你的筆記｜Commands like "go look at something in an app/album and remember it" are now routed to screen automation by the cheap model (not chat just because of the word remember), and automation gained a "save as note" action so it can store what it read on screen

## 1.0.1527

- 圈選提問回答後可以接著問，同一段對話有上下文（選新區域才重來）｜After a circle-to-ask answer you can keep asking in the same conversation with context (selecting a new region starts over)

## 1.0.1526

- 圈選截圖不再拍到面板的開場動畫：面板先藏著，截好圖才出現（最多等 1.5 秒）｜Circle-to-ask screenshots no longer capture the panel's opening animation: the panel stays hidden until the shot is taken (up to 1.5 s)

## 1.0.1525

- 圈選截圖變快：root／Shizuku 改讀原始像素，不再編成 PNG 再解開｜Circle-to-ask screenshots are faster: root/Shizuku now read raw pixels instead of encoding and decoding a PNG

## 1.0.1524

- 圈選提問回來了：開面板就直接在畫面上圈，圈完自動變成長方形（可拖角或邊調整、拖裡面移動），再說或打問題問 AI；不需要按鈕｜Circle to ask is back: open the panel and draw directly on the screen; the loop becomes an adjustable rectangle (drag corners or edges, drag inside to move), then ask by voice or text, no button needed

## 1.0.1523

- 圈選提問回來了：助理面板左邊的圈選鈕 → 凍結畫面、隨便圈一圈會自動變成長方形（可拖角或邊調整、拖裡面移動），再輸入問題問 AI｜Circle to ask is back: the new button in the assistant panel freezes the screen; draw any loop and it becomes an adjustable rectangle (drag corners or edges, drag inside to move), then ask the AI

## 1.0.1514

- 首頁問候語改成隨機：依時段加上通用的趣味句子，偶爾出現一問一答的冷笑話（點一下看答案）｜Home greeting is random: time-of-day and playful lines, plus an occasional Q&A cold joke (tap for the answer)

## 1.0.1507

- 首頁改版：中間是三條波的標誌與問候語（早安/午安/晚安），底部膠囊右邊的圓鈕按下去整條變成波開始聽，有字時變成送出｜Home redesign: the three-wave mark and a greeting in the middle; in the bottom pill the round button turns the whole pill into a wave while listening, and becomes send when you have typed text

## 1.0.1506

- 首頁輸入框預設是語音模式：整個框是一條函數波（跟助理面板一樣），按麥克風錄音時隨音量起伏，點框才切成打字｜Home input bar now defaults to voice mode: a function wave like the assistant panel that reacts to your voice; tap the bar to type

## 1.0.1505

- 首頁：問候語下面與輸入框裡加了函數波形，按麥克風時隨音量起伏｜Home: function-wave lines under the greeting and inside the input bar; they react to your voice while recording

## 1.0.1504

- 子頁展開還沒跑完就按返回：現在會從目前的進度接著縮回去，不再跳一下｜Pressing back while a sub-page is still opening now reverses from the current progress instead of jumping

## 1.0.1503

- App 改名為 Vertex（原名 PhoneAI Overlay）；收到的檔案仍放在「下載/PhoneAI」，更新方式不變｜The app is renamed Vertex (formerly PhoneAI Overlay); received files still go to Download/PhoneAI and updating works as before

## 1.0.1502

- 通知列的小圖示換成波形（跟 App 圖示同一個主題）｜The notification status-bar icon is now the wave motif, matching the app icon

## 1.0.1501

- 換了新的 App 圖示：白底、三條點對稱的黑色波；也加了 Android 13 以上主題圖示用的單色版｜New app icon: a white background with three point-symmetric black waves, plus a monochrome layer for themed icons on Android 13 and later

## 1.0.1500

- 換分頁時左上角的大標題也從切換那一邊橫向滑進來，排在所有元件前面｜When switching tabs the big title at the top left now also slides in from the side, ahead of all the components

## 1.0.1499

- 換分頁改成：新頁面的每個元件本來就在左邊或右邊，從上到下一個接一個橫向滑進來（舊頁面很快淡出），不再有空白一格的閃爍｜Switching tabs now works like this: every component of the new page starts off to the left or right and slides in one after another from top to bottom (the old page fades out quickly), with no empty-frame flash

## 1.0.1498

- 換分頁改成像翻頁一樣橫向滑過去：舊頁面往一邊滑出、新頁面（先在畫面外建好）從另一邊滑進來，不再先淡出再淡入，也就沒有閃一下｜Switching tabs now slides sideways like turning a page: the old page slides out and the new one (built off-screen first) slides in, so there is no fade-out-then-fade-in flash

## 1.0.1497

- 修正換分頁時閃一下：新頁的第一段在換頁的同一幀就建好，不再先出現一格空白｜Fix the flash when switching tabs: the first slice of the new page is built in the same frame, so there is no empty frame first

## 1.0.1496

- 換到功能頁、設定頁時內容分成幾段建立（每一幀一段），建好的那一段再從下方浮上來，換分頁掉幀會更少｜Switching to the Functions and Settings tabs now builds the content in slices, one per frame, and each slice rises into view, so there are fewer dropped frames when switching tabs

## 1.0.1495

- 加入基準設定檔（Baseline Profile）：App 剛安裝或更新完，常用的程式碼就先編譯好，不用等手機閒置充電，更新後第一次用動畫與開頁面會比較順｜Added a Baseline Profile: the app's code is pre-compiled right after installing or updating, so animations and page opens are smoother the first time you use them after an update

## 1.0.1494

- 任務過程列表建卡片更輕：每一幀只建一張，列表上的文字不再設成可選取（點整張卡片進詳情）｜Building the task-history cards is lighter: one card per frame, and the list text is no longer selectable (tapping a card opens its details)

## 1.0.1493

- 子頁面返回更順：從子頁面回到主畫面時，重建首頁卡片與查權限延到返回動畫結束後；返回用打開時放好的蓋層，不再臨時加｜Returning from a sub-page is smoother: rebuilding the home cards and checking permissions now wait until the return animation ends, and the return reuses the veil placed when opening instead of adding one on the spot

## 1.0.1492

- 子頁面展開結束那一幀掉幀的修正：蓋層改成隱藏、圓角裁切與繪圖層晚一點才拆；診斷頁的動畫更新率會註明每段動畫是什麼｜Fix the dropped frame at the very end of the sub-page expansion: the veil is now hidden instead of removed and the clip and layer are torn down later; the diagnostics page now labels each animation window

## 1.0.1491

- 內部整理：移除沒有入口的「麥克風測試」頁面｜Internal clean-up: removed the Mic test page, which had no entry point

## 1.0.1490

- 修正打開「設定導覽」「麥克風測試」時整個畫面變黑、返回時出現一層一層殘影：這兩個頁面補上跟其他子頁面一樣的透明主題｜Fix the screen going black when opening Setup guide and Mic test, and trailing ghost images when going back: these two pages now use the same translucent theme as the other sub-pages

- 診斷頁新增「動畫期間的更新率」：量動畫與彈簧期間每一幀的實際間隔｜The diagnostics page now shows the refresh rate measured during animations and springs

## 1.0.1488

- 動畫與彈簧期間要求系統用高更新率（Android 15 以上），修正彈簧動畫幀數很低、看起來一格一格的｜Request a high refresh rate while animations and springs run (Android 15+), fixing springs that looked choppy because the screen dropped its refresh rate

## 1.0.1487

- 診斷頁的子頁面展開量測多記「最長間隔發生在動畫進度的幾 %」｜The sub-page expand stats on the diagnostics page now also record at what percentage of the animation the longest gap happened

## 1.0.1486

- 診斷頁的模型改成顯示你實際選的服務與模型（以前寫死 Claude 的內建名稱）；子頁面展開量測保留最近 5 次｜The diagnostics page now shows the service and model you actually chose (it used to show the built-in Claude names); sub-page expand stats keep the last 5 runs

## 1.0.1485

- 診斷頁的「子頁面展開」在還沒量到時會說明該怎麼做｜The "sub-page expand" line on the diagnostics page now explains what to do when nothing has been measured yet

## 1.0.1484

- 子頁面展開時每一幀建卡片的時間更少、前 0.09 秒先不建；診斷頁新增「子頁面展開」量測（幀數、最長間隔、卡頓次數）｜Sub-page expansion spends less time per frame building cards and waits 0.09 s before starting; the diagnostics page now shows the expand frame stats (frames, longest gap, number of janks)

## 1.0.1483

- 任務過程與任務詳細的卡片改成邊展開邊一小段一小段建立：不卡動畫，也不會展開完才空一下才出現｜Task history and task detail cards are now built in small slices while the page expands: no stutter, and no empty pause after the expansion

## 1.0.1481

- 修正點開任務過程等子頁面時「頓一下」：重的內容改成展開動畫結束後才建立、再淡入；任務卡片改成一張一張淡入升起，不再用彈簧彈出來｜Fix the stutter when opening sub-pages such as Task history: heavy content is now built after the expand animation and faded in, and task cards rise in one by one instead of bouncing in on a spring

## 1.0.1480

- 點開卡片時，卡片上的文字不再瞬間消失變空白：展開一開始跟被點的卡片一模一樣，文字隨展開淡出，新內容再淡入（返回時反過來）｜Opening a card no longer blanks its text instantly: the transition starts looking exactly like the tapped card, its text fades out as it expands and the new content fades in (and the reverse on the way back)

## 1.0.1479

- 卡片、按鈕、方塊的按壓彈簧更柔和：按下不再啪一下縮到底、放開只輕輕回彈一點｜The press springs on cards, buttons and tiles are softer: no snap when pressing and only a gentle rebound on release

- 子頁面展開、返回改用平順的慢進慢出曲線，並等第一幀畫好才開始動畫，修正一開始一格不動、下一格就跳一大段的生硬感｜Sub-page open/close use a smoother ease-in-out curve and wait for the first frame to be drawn before starting, fixing the stiff "nothing, then a big jump" at the start

## 1.0.1477

- 子頁面展開改成「容器轉換」：先是卡片顏色的圓角容器慢慢起步展開，內容晚一點才淡入（不再一開始就看到縮小的整頁，也不再前面衝很快）｜Sub-pages now open like a container transform: a card-coloured rounded container starts gently and grows, and the content fades in later (no more tiny full page at the start and no more sudden rush)

## 1.0.1476

- 修正從任務過程列表點進任務詳細頁（以及互傳檔案的「瀏覽」）時整頁突然冒出再彈一下：現在會從點的卡片展開；沒有位置時的備用動畫改成淡入放大｜Fix pages popping in fully open with a bounce when opened from a task card (and from the file-transfer "browse" button): they now expand from the tapped card, and the fallback animation is a fade-and-grow

## 1.0.1475

- 助理面板改成跟底欄一樣的液態玻璃膠囊（高 64dp），麥克風換成 App 統一的線條圖示｜The assistant panel is now the same liquid-glass pill as the bottom bar (64dp tall), and the mic uses the app's shared line icon

## 1.0.1474

- 照 Material 3 官方數值微調：助理面板高度 56dp、顏色跟著莫奈取色；子頁面從卡片展開改成 500 毫秒｜Tuned to the Material 3 specs: the assistant panel is 56dp tall and takes its colour from the Monet palette; opening a sub-page from a card now takes 500 ms

## 1.0.1473

- 助理面板聽與待機時更窄，打字或有字時才展開；聲波改成五條漸層色、各自起伏的波，空白更少｜The assistant panel is narrower while listening or idle and widens when you type; the sound wave is now five gradient-coloured waves that each breathe, so there is less empty space

## 1.0.1472

- 助理面板改成兩端半圓的膠囊、左右縮進去、去掉頂端橫條；聽的時候的聲波改成函數畫的連續波浪線｜The assistant panel is now a pill with semicircular ends, narrower, without the top handle; the listening sound wave is now smooth function-drawn waves

## 1.0.1471

- 助理面板改成像 Gemini 的底部深色面板：去掉光球，聽的時候中間是聲波條，右邊是麥克風／送出按鈕｜The assistant panel is now a Gemini-style dark bottom panel: the orb is gone, a sound-wave bar shows while listening, and the right button is a mic / send button

## 1.0.1470

- 玻璃畫圖出錯時：還原畫布狀態、記錄錯誤、幾秒後自動再試（以前會永遠退回半透明，重開 App 才好）｜When the glass fails to draw, the canvas state is restored, the error is logged and it retries a few seconds later (it used to stay in the flat fallback until the app was restarted)

## 1.0.1469

- 嘗試修正透鏡偶爾卡成梯形、重開 App 才好：回到 App 或畫面被藏起來時，玻璃重錄所有繪圖暫存｜Attempted fix for the lens occasionally getting stuck as a trapezoid until the app is restarted: the glass now re-records all its render nodes when the app returns or the UI is hidden

## 1.0.1468

- 修正診斷頁「按鈕透鏡」那一行一直是空的｜Fix the "button lens" line on the diagnostics page that was always empty

## 1.0.1467

- 診斷頁的「按鈕透鏡」多顯示形變、縮放、旋轉等狀態，用來查透鏡偶爾變成梯形的原因｜The diagnostics page now shows the lens deformation, scale and rotation, to find why the lens sometimes turns into a trapezoid

## 1.0.1466

- 拖動底欄時透鏡的外形像果凍一樣沿移動方向拉長、上下壓扁，停下來彈回（圖示反向補償不變形）；強度用「拖動形變強度」滑桿調｜While dragging the bar, the lens now squashes and stretches like jelly along the drag direction and springs back (icons are counter-scaled so they stay undistorted); the strength follows the "Drag deformation strength" slider

## 1.0.1465

- 設定 → 底欄玻璃：新增「拖動形變強度」滑桿，預設比以前強兩倍（拖動時透鏡邊緣被拉扯得更明顯）｜Settings → Glass bar: new "Drag deformation strength" slider; the default is now twice as strong (the lens edge is pulled more visibly while dragging)

## 1.0.1464

- 修正透鏡有時變成歪的梯形：放開手指後形變量沒有歸零；著色器取樣超出範圍時邊角被畫成黑色｜Fix the lens sometimes turning into a lopsided trapezoid: the drag stretch was not reset on release, and shader samples outside the node painted the corners black

## 1.0.1463

- 內部整理：移除已停用的圈選提問程式碼與舊的展開動畫，沒有功能變動｜Internal clean-up: removed the retired circle-to-ask code and the old spring-based expand animation; no behaviour change

## 1.0.1462

- AI 服務選 Claude 時也能自己選模型（向 Anthropic 查詢清單，便宜與看圖各選一個；不選就用內建預設）｜Claude can now be set up with models you pick yourself (the list comes from Anthropic; one for cheap and one for vision; skip to keep the built-in defaults)

## 1.0.1461

- 修正用 Claude 整理通知常說「看不懂」：回答長度上限提高（原本太短會被截斷），解不開時改成盡量顯示摘要文字｜Fix the digest often saying it could not understand Claude's answer: the answer length limit is higher (it was truncating), and when the JSON cannot be parsed the digest now shows the summary text it can recover

- 載入動畫拿掉正圓，只在圓角五邊形和橢圓之間變形｜The loading animation no longer includes the plain circle; it morphs between the rounded pentagon and ellipses only

## 1.0.1459

- 載入動畫拿掉三角與方形，只留圓、圓角五邊形、橢圓；動作改成蓄力、突然加速旋轉並換形、輕輕回彈｜The loading animation drops the triangle and square, keeping the circle, rounded pentagon and ellipses; it now winds up, spins fast while changing shape, then settles with a small bounce

## 1.0.1458

- 載入動畫改成沒有外圈、更大，在圓、橢圓、圓角五邊形、圓角方、軟三角之間依序變形旋轉（沒有尖角）｜The loading animation has no outer plate, is larger, and morphs between a circle, ellipses, a rounded pentagon, a rounded square and a soft triangle while rotating (no sharp corners)

## 1.0.1457

- 通知整理的載入動畫改成主題色、會旋轉變形的圓角形狀（仿 Material 3 Expressive）；AI 服務暫時太忙（502、503、504）時自動多試兩次｜The digest loading animation is now a theme-coloured, rotating, morphing rounded shape (in the style of Material 3 Expressive); the digest retries twice automatically when the AI service is temporarily overloaded (502, 503, 504)

## 1.0.1456

- 通知整理：按「立即整理」馬上開啟頁面，中間顯示轉圈圈與說明，整理完才換成結果；失敗會顯示原因與「再試一次」｜Notification digest: "Digest now" opens the page immediately with a centred spinner and a hint, then swaps in the result; failures show the reason and a "Try again" button

## 1.0.1455

- 沒按住時透鏡比底欄微亮，圓底的形狀看得清楚（按住時逐漸消失）｜When not pressed the lens is slightly lighter than the bar so the pill shape reads clearly (fades out while pressed)

## 1.0.1454

- 修正透鏡沒按住時比底欄更黑（透鏡也套用底欄的灰色提亮，按住時逐漸取消）｜Fix the lens looking blacker than the bar when not pressed (it now gets the same grey lift as the bar, fading out while pressed)

## 1.0.1453

- 玻璃透鏡：沒按住時不再穿透底欄、直接折射底下的畫面（跟底欄一樣暗，按住才變清澈）；按住時外面加一圈柔和陰影｜Glass lens: when not pressed it no longer shows the page straight through the bar (it is as dark as the bar and only turns clear when pressed); a soft outer shadow appears while pressed

## 1.0.1452

- 圖示折射與色散改成預設開啟（設定 → 底欄玻璃可關）｜Icon refraction and dispersion are now on by default (can be turned off in Settings → Glass bar)

## 1.0.1451

- 透鏡更像玻璃、不像塑膠：拿掉自己的底色（靜止提亮、上亮下暗漸層），邊緣高光改成更細更淡的一圈｜The lens looks more like glass and less like plastic: removed its own base tint (rest brightening and top/bottom gradient) and made the edge highlight a thinner, subtler rim

## 1.0.1450

- 修正透鏡正中央出現一條白色細線（高光算式在中央遇到零向量變成無效值）｜Fix a thin white line across the middle of the lens (the highlight maths hit a zero vector there and produced an invalid value)

## 1.0.1449

- 移除底欄中間的橫向光帶；透鏡加上淡淡的立體漸層（上亮下暗），背景是平的也有玻璃厚度感｜Removed the horizontal light band in the bar; the lens gets a faint top-light/bottom-dark gradient so it still has depth over a flat background

## 1.0.1448

- 透鏡更像液態玻璃：底下只顯示頁面（不帶底欄暗色與光帶，不再整塊黑、沒有白線），邊緣高光改成柔和光暈｜The lens looks more like liquid glass: it shows only the page behind (no bar darkening or light band, so no black fill or white line), and the edge highlight is a soft glow

## 1.0.1447

- 修正按住底欄時透鏡裡面是整塊黑色、看不到底下的畫面與圖示（透鏡底下改成直接重畫背景）｜Fix the lens being a solid black fill while pressed, hiding the backdrop and icons (the lens now redraws the backdrop directly)

## 1.0.1446

- 透鏡邊緣高光改成整圈連續的細亮線（朝向光源處較亮），不再是一個白點｜The lens edge highlight is now a continuous thin lit rim (brighter toward the light) instead of a single white spot

## 1.0.1445

- 按住底欄時，透鏡邊緣加上被光源照到的高光，背景是平的（沒東西可折射）時也有玻璃感｜While pressed, the lens now has a lit specular edge so it still looks like glass over a flat background

## 1.0.1444

- 按住底欄時，透鏡邊緣不再有彩色環；邊界靠邊緣折射、微暗與陰影呈現｜The lens no longer shows a coloured ring while pressed; its edge is shown by edge refraction, slight darkening and shadow

## 1.0.1443

- 修正開啟「圖示折射與色散」後透鏡變成不透明黑色：圖示層的透明處不再被畫成黑底｜Fix the lens turning opaque black with icon refraction on: transparent parts of the icon layer are no longer drawn as a black fill

## 1.0.1442

- 修正開啟「圖示折射與色散」後，沒按住時上層玻璃偏暗（圖示層重複加了一次暗度）｜Fix the top glass looking darker when icon refraction is on and the bar is not pressed (the icon layer applied the darkening a second time)

## 1.0.1441

- 底欄背景改用 GPU 錄製（預設開，動畫與捲動更順；可在設定 → 底欄玻璃關掉），移除測試用的黃字與診斷量測｜The bar backdrop is now GPU-recorded by default (smoother animation and scrolling; can be turned off in Settings → Glass bar); removed the test text and probe

## 1.0.1440

- 設定 → 底欄玻璃：加回「圖示折射與色散（實驗）」開關（預設關，打開可能出現彩色斑塊）｜Settings → Glass bar: bring back the "Icon refraction and dispersion (experimental)" switch (off by default; may show coloured patches)

## 1.0.1439

- GPU 背景（實驗）：修正頁面捲動後底欄背景卡在最上面那一幀（漏了捲動量）｜GPU backdrop (experimental): fix the bar backdrop freezing on the top frame once the page scrolls (scroll offset was missing)

## 1.0.1438

- GPU 背景（實驗）：背景裡加一行診斷文字｜GPU backdrop (experimental): draw a diagnostic text line into the backdrop

## 1.0.1436

- 診斷頁：GPU 背景錄製的最近 4 次量測｜Diagnostics: last 4 GPU backdrop probes

## 1.0.1435

- 底欄 GPU 錄製（實驗）：頁面每更新一幀底欄就跟著重畫，修正捲動後卡在舊畫面｜Glass bar GPU backdrop (experimental): redraw the bar on every page frame, fixing the stale frame after scrolling

## 1.0.1433

- 底欄 GPU 錄製（實驗）：背景直接錄進折射節點，不再嵌套｜Glass bar GPU backdrop (experimental): record the backdrop straight into the refraction node instead of nesting a RenderNode

- 底欄 GPU 錄製（實驗）：背景先畫成圖層再給折射著色器讀取｜Glass bar GPU backdrop (experimental): render the backdrop into a layer before the refraction shader reads it

## 1.0.1431

- 設定 → 底欄玻璃：新增「GPU 錄製背景（實驗）」開關（預設關），並在診斷頁顯示錄到的背景顏色數，用來查折射消失的原因｜Settings → Glass bar: new experimental "GPU-recorded backdrop" switch (off by default) with a diagnostic colour count in the Diagnostics page, to find why refraction vanished

## 1.0.1430

- 修正底欄折射與色散不見：改回用圖片抓取底下的畫面（GPU 錄製那版讓折射和色散消失），保留「底下有變化就更新」；設定群組的展開／收合改成用時間與曲線連續長高、內容隨高度淡入，不再是整塊淡入｜Fixed the bottom bar losing its refraction and dispersion: it captures what is behind it as an image again (the GPU-recording version lost both), keeping the refresh-on-change fix; expanding and collapsing settings groups now grows the height continuously with a time curve and fades the content in as it grows, instead of fading in one block.

## 1.0.1429

- 修正底欄折射偶爾停在舊畫面（殘留）：底欄原本只在被通知時短暫更新，底下的畫面在這段時間外變了（背景載入資料、轉場結束後換內容）就不會跟著更新；現在會監聽視窗的繪製，偵測到底下有變化就再更新一小段時間｜Fixed the bottom bar's refraction sometimes staying on a stale picture: the bar only refreshed briefly when told to, so changes behind it outside that window (background data loading, content swapped after a transition) were missed; it now watches the window's drawing and refreshes again for a short while whenever something behind it changes.
- 打開子頁面像桌面開 App：整頁從你點的那張卡片的位置和大小展開到全螢幕（圓角跟著變小，底下的首頁看得到），按返回時縮回那張卡片；左上方的大標題換頁時，舊字滑出淡出、新字滑進淡入，不再突然跳掉；切分頁時卡片依序浮上來｜Opening a sub-page now works like opening an app from the home screen: the page grows from the card you tapped to full screen (corners rounding off, with the home page visible behind) and shrinks back into it on back; the big title at the top left now slides and fades between Home, Functions and Settings instead of snapping; cards float in one after another when switching tabs.

## 1.0.1427

- 動畫更順：底欄玻璃改用 GPU 錄製後面的畫面（以前動畫、捲動時每幀都用 CPU 把整頁重畫一次，首頁更是一直在畫，這是很多動畫卡頓的主因），解析度也直接是原始的；打開子頁面的彈出動畫改成小幅度放大、快速停下，動畫期間整頁用獨立的 GPU 層；設定的展開／收合改用版面轉場，不再每幀重新排版｜Smoother animations: the glass bar now records what is behind it on the GPU (it used to redraw the whole page on the CPU every frame during animations and scrolling, and constantly on Home, which was the main cause of stutter), at full resolution; opening a sub-page now pops in with a small, quickly settling scale on its own GPU layer; expanding and collapsing settings groups uses layout transitions instead of re-laying out the page every frame.

## 1.0.1426

- 切換分頁的動畫改成整頁一起滑入、淡入，用「快進、慢慢停下」的非線性曲線，首頁也有動畫；舊頁面往反方向滑出。以前每張卡片各自跑動畫，比較容易卡｜Switching tabs now slides and fades the whole page in with an ease-out curve, including the Home tab, while the old page slides out the other way. Previously every card animated on its own, which stuttered more easily.

## 1.0.1425

- 修正模糊程度大於 0 時，底欄選中那一格的圖示和文字也被模糊：圖示改成另外一層，只折射不模糊｜Fixed the selected bottom-bar icon and label being blurred when blur is above 0: the icon is now drawn on its own layer with refraction only, no blur.
- 通知整理更完整：交給 AI 的通知從 40 則增加到 120 則，而且每個 App 最多 8 則（一直發通知的 App 不會把一天的額度用光），沒列出的會寫是哪些 App 幾則；摘要第一行寫涵蓋的時間範圍，重要的事放前面，最多 15 行。時間、日期選擇器改用 App 的主題色｜Fuller notification digest: up to 120 notifications are sent instead of 40, at most 8 per app (so a chatty app cannot use up the whole day), with a note of which older ones were left out; the summary starts with the time range covered, puts important things first and can run to 15 lines. The time and date pickers now use the app's theme colour.

## 1.0.1423

- 通知整理改讀系統的「通知紀錄」（有 root 時），包含白天已經滑掉的通知：定時整理從上次整理之後開始、手動整理看最近 24 小時；同一則一直更新的只留最新一次。沒有 root 或沒開通知紀錄時，照舊只讀通知欄目前的內容｜The notification digest now reads the system notification history (with root), including notifications you already dismissed: scheduled digests cover everything since the last one, manual ones the last 24 hours, and repeatedly updated notifications are kept once. Without root or with notification history off, it still reads only what is in the shade.

## 1.0.1422

- 通知整理的每一件事都可以先修改再加入：標題可以改，日期和時間點一下用選擇器挑（時間長按清除），可以切換加到行事曆或提醒｜Every item in the notification digest can be edited before adding: change the title, pick the date and time with pickers (long-press to clear the time), and switch between calendar and reminder.

## 1.0.1421

- 新增「通知整理」（功能頁）：設定每天的時間，時間到了 AI 會整理通知欄並跳一則通知；點進去看摘要，有提到日期或時間的事（約會、截止日、繳費、取件）會列出來，按「加入行事曆」或「加入提醒」就會加入。也可以按「立即整理」。AI 整理時沒有任何可以動手的工具，加入一律要你按｜New "Notification digest" (Functions page): pick a daily time; the AI digests your notifications and posts a notification. Tap it to see the summary and the dated items (appointments, deadlines, bills, pickups), each with an "Add to calendar" or "Add reminder" button. "Digest now" runs it immediately. The AI has no tools while digesting; adding always needs your tap.

## 1.0.1420

- 新增「整理通知」：說「我有什麼通知」「整理一下通知」，AI 會讀通知欄，依 App 整理誰傳了什麼、哪些可能要處理。第一次要開「通知存取」（有 root 會自動開）；音樂、導航這類常駐通知不讀，銀行等受保護的 App 不讀內容，驗證碼會遮掉；讀過通知後那一輪 AI 不能做刪檔、傳檔、記事這類動作（防止通知裡藏的指令）｜New "summarize notifications": say "what notifications do I have" and the AI reads the notification shade and groups who sent what and what may need action. The first time it needs notification access (granted automatically with root); ongoing notifications such as music and navigation are skipped, protected apps such as banking are listed without content, verification codes are masked, and after reading notifications the AI cannot delete or send files or save notes in that turn (guarding against instructions hidden in notifications).

## 1.0.1419

- 修正文字經過底欄時變糊、有鋸齒：底欄以前一律只抓 1/4 解析度的背景，模糊設定低（預設 0）時就看得出來；現在模糊越少抓得越清楚，模糊夠大才縮小抓｜Fixed text looking blurry and jagged behind the bottom bar: the bar always captured the background at 1/4 resolution, which shows when blur is low (the default is 0); it now captures at higher resolution the less blur there is, and only downsamples when the blur hides it.
- 拿掉助理面板的麥克風按鈕（圈選拿掉後用不到了）｜Removed the microphone button from the assistant panel (no longer needed now that circling is gone).

## 1.0.1418

- 新增計時：說「計時 10 分鐘」就用系統的計時器開始倒數（不用猜欄位），名稱可以帶上｜New timer: say "time 10 minutes" and the system timer starts counting down (uses the standard timer intent); a name can be included.
- 驗證完成：網頁搜尋（Tavily）、可信任的網站、鬧鐘直接設好、檢查更新的備用路線、錯誤紀錄、匯出與麥克風按鈕都已在手機上確認正常｜Verified on the phone: Tavily web search, trusted sites, alarms set directly, the update-check fallback, the error log, export, and the microphone button all work.

## 1.0.1417

- 暫時拿掉圈選功能（助理面板的 ◎ 與開啟時的預先截圖）；程式碼保留，之後再決定要不要加回來｜The circle-to-ask feature is removed from the panel for now (the ◎ button and the screenshot taken on open); the code is kept for later.

## 1.0.1416

- 叫出助理面板時就先截圖，直接在畫面上圈選（不用按 ◎）；點光球和輸入框以外的地方隱藏光球；圈完後輸入問題送出，會連同圈起來的區域一起問 AI｜Opening the assistant panel now takes a screenshot right away, so you can circle things directly (no ◎ needed); tapping outside the orb and input hides the orb; after circling, send a question and it is asked together with the circled region.

## 1.0.1414

- 助理面板輸入框旁邊加一顆麥克風，光球隱藏後也能直接開始語音辨識｜The assistant panel input row has a microphone button, so voice recognition can start even when the orb is hidden.

## 1.0.1413

- 圈選提問：面板一打開就在背景截一張圖，按 ◎ 時直接用，面板不用先收起來；圈選畫面的雙指縮放可以縮到很小（0.3 倍），讓整張圖縮在面板上面不被擋住｜Circle to ask: a screenshot is taken in the background as soon as the panel opens and used when you tap ◎, so the panel does not need to hide first; pinch-zoom on the circle screen can now shrink the picture to 0.3× so it sits above the panel.

## 1.0.1412

- 圈選提問：面板改成先淡出（約 0.15 秒）再截圖與開啟圈選畫面，不再瞬間消失｜Circle to ask: the panel now fades out (about 0.15 s) before the screenshot and the circle screen, instead of vanishing instantly.

## 1.0.1411

- 移除圈選提問開啟時我們自己加的白色閃光，圈選畫面直接出現｜Removed the white flash the Circle to ask screen added on open; the screen now appears directly.

## 1.0.1410

- 檢查更新被 GitHub 回 403（沒登入的查詢次數用完，同一個網路的人共用）時，改走網頁取得最新版本與下載網址，不再直接失敗（這種情況沒有更新說明）｜When GitHub answers 403 on the update check (the anonymous query limit is used up and shared by everyone on the same network), the app now falls back to the web page to find the latest version and its download link instead of failing (no release notes in that case).

## 1.0.1409

- 修正鬧鐘明明已經直接設好、AI 還是叫你去時鐘按儲存（提示詞還是舊的說法）；現在已設好就照實說，只有時鐘畫面真的跳出來才提醒按儲存｜Fixed the AI telling you to press save in the clock even though the alarm was already set directly (the prompt still had the old wording); it now says the alarm is set, and only reminds you to press save if the clock screen actually appears.

## 1.0.1408

- 修正「幫我查關於獅子的相關知識」這類句子不會觸發網頁搜尋：「幫我／請你」後面接「查」就算（「檢查一下」「查理」不算）｜Fixed sentences like "look up information about lions" not triggering web search: "help me / please" followed by "查" now counts (but "check/inspect" and names such as "Charlie" do not).

## 1.0.1407

- 「可信任的網站」改成清單：列出每個網站，點一個可以修改或刪除，最下面「＋ 新增網站」，不用再照格式打字｜"Trusted sites" is now a list: each site is shown, tap one to edit or delete it, and "+ Add a site" sits at the bottom; no more typing in a special format.

## 1.0.1406

- 網頁搜尋的關鍵字改由便宜模型自己想（會參考上一輪對話，所以「那明天呢」也搜得對；多花一次很便宜的呼叫，失敗就退回程式自己清理）；新增「可信任的網站」（功能頁，自己編輯清單，預設有中央氣象署）：搜尋時模型會挑跟問題有關的網站先查，沒結果再查所有網站｜Web search keywords are now written by the cheap model (it sees the previous turn, so a follow-up like "and tomorrow?" searches correctly; one extra very cheap call, with a fallback to the program's own cleanup); new "Trusted sites" (Functions page, an editable list with the Central Weather Administration by default): the model picks the sites relevant to the question and searches them first, then everything if nothing is found.

## 1.0.1405

- 網頁搜尋改進：搜尋關鍵字會先去掉「幫我查一下」這類客套話；天氣優先只查中央氣象署（沒結果再放寬）；問新聞用新聞模式找最近一週的；回答下面會列出搜尋到的網頁，網址可以點；任務紀錄寫出實際用的關鍵字、限定的網站與網頁數｜Web search improvements: filler such as "look up" is stripped from the keywords; weather questions prefer only the Central Weather Administration (widened if nothing is found); news questions use news mode for the last week; the answer lists the pages found with tappable links; the task log records the keywords actually used, any site restriction and the number of pages.

## 1.0.1404

- 新增「網頁搜尋（Tavily）」：你說「幫我查…／搜尋一下」時，聊天與圈選提問會先用 Tavily 搜尋，把結果交給任何 AI（Gemini、DeepSeek 都行）整理回答；不用設 Key 也能搜（Tavily 的免 Key 模式，有次數限制），想要更穩定的額度可以在功能頁貼 Tavily Key；搜尋結果被當成資料不是指令，這一輪不給 AI 任何工具；Tavily 失敗而且用 Claude 時，改用 Claude 內建搜尋｜New "Web search (Tavily)": when you say "look up…" or "search…", chat and Circle to ask search with Tavily first and hand the results to any AI (Gemini, DeepSeek…) to answer; it works without a key (Tavily keyless mode, with limits), and you can paste a Tavily key on the Functions page for steadier quota; the results are treated as data, not instructions, and the AI gets no tools for that turn; if Tavily fails and you use Claude, Claude's built-in search is used instead.

## 1.0.1402

- 說「幫我查…」的聊天，任務紀錄會註明網頁搜尋有沒有開、有沒有真的搜尋；沒開時說明原因（例如便宜模型不是 Claude）｜For chat requests like "look up…", the task log now says whether web search was on and whether it actually searched; when it is off it says why (for example the cheap model is not Claude).

## 1.0.1401

- 聊天模式也能上網搜尋：只有你明說「幫我查／搜尋一下／上網查」時才開（Claude 專屬，最多 2 次，搜尋另外計費），這類問題也不再被送進操作畫面模式｜Chat mode can search the web too: only when you explicitly say things like "look up" or "search" (Claude only, at most 2 searches, billed separately); such questions are also no longer sent to the screen-operating mode.
- 圈選提問在用 Claude 時可以上網搜尋（需要即時資訊才搜，最多 2 次；搜尋要另外計費）｜Circle to ask can now search the web when using Claude (only when the question needs current information, at most 2 searches; searches are billed separately).

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
