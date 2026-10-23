# PhoneAI 互傳檔案（Windows 版）

與手機版 App 的「互傳檔案」互通，協定與加密格式相同（見 `docs/FILE_TRANSFER.md`）。

## 執行（開發用）
```
pip install -r requirements.txt
python run_app.py
```

## 測試
```
python -m unittest discover -s tests -v
```
`tests/test_interop.py` 會用 `javac` 編譯手機版的 Java 程式碼，跟 Python 雙向互通測試；沒有 javac 會跳過。

## 打包成 .exe
GitHub Actions 的 `.github/workflows/windows-transfer.yml` 會在 Windows 機器上打包，產物是 `PhoneAI互傳.exe`。

## 資料位置
- 設定、已配對裝置與金鑰：`%APPDATA%\PhoneAI互傳\config.json`（只存在這台電腦）
- 收到的檔案：`下載\PhoneAI`
