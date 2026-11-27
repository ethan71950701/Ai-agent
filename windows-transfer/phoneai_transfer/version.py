"""版本資訊。GitHub Actions 打包時會把 BUILD 換成執行編號（見 .github/workflows/windows-transfer.yml）。"""
BUILD = 0                                   # 0 = 開發中（不會自動更新）；打包時換成 Release 標籤的編號
REPO = "ethan71950701/ai-agent"
# 電腦版跟手機 APK 發布在同一個 Release（標籤 build-<編號>-<提交>）；BUILD 跟標籤的編號一樣
ASSET_NAME = "PhoneAI.exe"          # GitHub 會把檔名裡的中文去掉，所以用英文名稱上傳
