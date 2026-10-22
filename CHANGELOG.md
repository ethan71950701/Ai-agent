# 更新說明 · Changelog

每一條寫成「中文｜English」，App 依介面語言只顯示對應的那一半；Release 說明會用整段 `## Unreleased` 內容。
Each entry is written as "中文｜English"; the app shows the half that matches the interface language. The release notes use the whole `## Unreleased` section.

## Unreleased

- 透鏡加上一圈半透明的邊緣線，左上較亮，讓透鏡邊界在沒有反射時也看得出來｜The lens gets a faint translucent edge line, brighter at the top-left, so its boundary shows even without reflections
- 用量上限：每日、每月可設 token 上限（功能 → 維護 → 用量與上限），超過就先不送出 Claude 請求｜Claude usage limits: set daily and monthly token caps (Features → Maintenance → Usage and limits); over the cap, Claude requests are not sent
- 分享到 PhoneAI：在別的 App 的「分享」選單選 PhoneAI，文字會填進助理面板，確認後再送出｜Share to PhoneAI: pick PhoneAI in another app's share menu; the text goes into the assistant panel and you send it after checking
- 記憶可以搜尋，並能匯出、匯入下載資料夾的備份（匯入只加入還沒有的項目）｜Memory search, plus export and import of a backup in the Downloads folder (import only adds items you do not have yet)
- 回答卡片支援簡單的 Markdown 排版（粗體、清單、程式碼、標題）｜Answer cards show simple Markdown formatting (bold, lists, code, headings)
- 桌面小工具「PhoneAI 語音」：一點就開助理面板並開始聽｜Home-screen widget "PhoneAI Voice": one tap opens the assistant and starts listening
- 任務歷史：每次送出的任務都會記下來，可以加星星，有星的排在最前面｜Task history: every task is recorded, you can star tasks, and starred tasks stay at the top

## 1.0.1183


- 英文介面與語言選項（設定 → 外觀 → Language），可即時切換｜English interface and a language option (Settings → Appearance → Language), switches instantly
- 英文語音指令、英文快速問答、英文定時任務｜English voice commands, English quick answers and English scheduled tasks
- 底欄液態玻璃改用移植的膠囊著色器：按住時透鏡與整條底欄放大、加速度驅動的彈簧形變、透鏡下的圖示變主題色｜Liquid-glass bar uses a ported capsule shader: lens and whole bar grow while pressed, acceleration-driven spring stretch, icons under the lens take the theme color
- 首頁與設定的標題區縮小；教學頁文字置中｜Smaller title area on the home and settings pages; onboarding text centered
