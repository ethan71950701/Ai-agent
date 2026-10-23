"""版本資訊。GitHub Actions 打包時會把 BUILD 換成執行編號（見 .github/workflows/windows-transfer.yml）。"""
BUILD = 0                                   # 0 = 開發中（不會自動更新）
REPO = "ethan71950701/ai-agent"
RELEASE_TAG = "windows-latest"
ASSET_NAME = "PhoneAI.exe"          # GitHub 會把檔名裡的中文去掉，所以用英文名稱上傳
