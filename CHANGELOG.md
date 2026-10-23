# 更新說明 · Changelog

每一條寫成「中文｜English」，App 依介面語言只顯示對應的那一半；Release 說明會用整段 `## Unreleased` 內容。
Each entry is written as "中文｜English"; the app shows the half that matches the interface language. The release notes use the whole `## Unreleased` section.

## Unreleased

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
