# PhoneAI Overlay

[English](README.en.md) | 繁體中文

在**已 root 的 Android 手機**上，用一個 App 做出的 AI 手機助手：用說的或打字下指令，它就能幫你開 App、開關設定、看著螢幕自己點擊、滑動、輸入，需要時還能上網搜尋回答。

整個專案就是一個 App，**不需要電腦、不需要 Termux、不需要 Python**。

> 這是個人實驗專案，很多功能只在作者自己的手機上驗證過，不保證每台機型都能用。**接手或繼續開發請先讀 [`CLAUDE.md`](CLAUDE.md) 與 [`HANDOFF.md`](HANDOFF.md)。**

## 它能做什麼

| 功能 | 說明 |
|---|---|
| 助理面板 | 打字或語音輸入。可從 App 主畫面、快速設定磁貼、長按電源鍵（設為預設數位助理）或螢幕邊緣的浮動按鈕叫出 |
| 固定指令（root，瞬間） | 開關 Wi-Fi／藍牙、返回、音量、用名稱開任何 App |
| 聊天與查詢 | 直接問問題；電量、儲存空間、時間等簡單問題在本機回答，不用網路 |
| 自動操作畫面 | Claude 看著截圖，一步一步點擊、滑動、拖曳、輸入，完成你交代的事（最多 8 步，卡住會升級到更強的模型） |
| 聯網搜尋 | 需要最新資訊時由 Claude 搜尋（每個任務有次數上限） |
| 記憶與定時 | 待辦與筆記、「每天早上 8 點提醒我…」這類定時任務 |
| 語音辨識 | 離線的 SenseVoice 模型（在 App 內下載），辨識完倒數幾秒才送出，期間可修改 |
| 執行時的提示 | 每次點擊處會有漣漪、通知上有「停止」鈕；三星手機還會出現 Now Bar 膠囊（選用） |
| 底欄與玻璃效果 | 液態玻璃風格的底欄與透鏡按鈕，主題色與介面語言可在設定調整 |

## 需求

- 已 root 的 Android 13 以上手機
- 一組 [Anthropic API Key](https://console.anthropic.com/)（Key 只存在手機上 App 的私有資料夾，不進 repo）
- 無障礙服務與懸浮視窗權限（App 內的「設定導覽」會一步一步帶你檢查）

## 安裝

1. 到本 repo 的 **Releases** 下載最新的 `app-debug.apk` 並安裝（APK 使用固定金鑰簽名，之後可直接覆蓋安裝；App 內的「功能 → 維護 → 檢查並更新」也能一鍵更新）。
2. 打開 App，跟著「設定導覽」完成：root 授權、懸浮視窗、無障礙服務、通知權限。
3. 到「功能 → 維護 → Claude API Key」貼上你的 Key。
4. 在首頁輸入框送出「開藍牙」或「進入設定裡的 Wi-Fi 頁面」試試看。

## 安全與隱私

- 會用 root 權限點擊螢幕。App 沒有對外網路入口；內部只有一個只連 127.0.0.1 的本機連線，而且需要只有 root 讀得到的 token 才能下指令。
- 付款、轉帳、刪除、安裝、傳訊息、登入、密碼等**敏感操作一律跳確認框**，沒回應就視為不同意。
- 自動操作每一步會把**截圖傳到 Anthropic**；不要在銀行、私訊或含個資的畫面使用。
- API Key 絕對不要貼到聊天或提交進 git。截圖、紀錄檔、模型檔都已在 `.gitignore` 排除。

## 自己編譯

```bash
cd android-overlay
gradle assembleDebug          # 需要 Android SDK 36 與 Gradle 8.14 以上
```

正式 APK 由 GitHub Actions（`.github/workflows/android.yml`）編譯並發布到 Releases。

## 專案結構

```
CLAUDE.md          給 Claude Code 的專案說明（先讀這個）
HANDOFF.md         完整交接：目標、狀態表、決策、下一步
android-overlay/   App 原始碼（Java、Gradle）
.github/workflows/ 雲端編譯 APK
docs/              研究與決策紀錄、疑難排解（部分是 Termux 時期的歷史紀錄）
```

## 致謝

- 離線語音辨識：[sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) 與 SenseVoice。
- 底欄玻璃效果的設計參考了 KernelSU 管理器的 FloatingBottomBar／Lens 與 Kyant 的 AndroidLiquidGlass（只參考設計，沒有複製它們的程式碼）。

## 授權

Apache License 2.0，見 [`LICENSE`](LICENSE)。
