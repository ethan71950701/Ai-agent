# 從零安裝（在已 root 的 Android 手機的 Termux 上）

> 以下是使用者實際走過的流程整理。每個指令一次貼一行/一段，貼之前看一下沒有重複。

## 1. 安裝 App
- **Termux**：從 GitHub 發布頁安裝（不要用 Play 商店舊版）。開啟時若跳出「16 KB 對齊」相容性警告，按「不要再顯示」，無害。
- **Termux:API**（跟 Termux 同一個來源，簽章要相同）：安裝後開一次。權限：麥克風允許；若要彈出視窗，開「顯示在其他應用程式上層」。
- （選用）**Termux:Widget**：桌面入口用。電池設為「無限制」。

## 2. 基本套件
```bash
pkg update
pkg upgrade -y          # 一定要做，否則後面 cmake 會報 CANNOT LINK
pkg install -y git cmake clang python imagemagick wget termux-api
termux-setup-storage    # 跳出權限視窗按允許（建立 ~/storage 捷徑）
```

## 3. 編譯 llama.cpp（本地模型引擎）
```bash
git clone https://github.com/ggml-org/llama.cpp --depth=1
cd llama.cpp
cmake -B build
cmake --build build -j4 --target llama-mtmd-cli    # 幾分鐘，手機會熱，插電
ls build/bin/llama-mtmd-cli
```

## 4. 下載模型（約 1.5GB，用 Wi-Fi；`-c` 可續傳）
```bash
mkdir -p ~/model && cd ~/model
wget -c https://huggingface.co/Qwen/Qwen3-VL-2B-Instruct-GGUF/resolve/main/Qwen3VL-2B-Instruct-Q4_K_M.gguf
wget -c https://huggingface.co/Qwen/Qwen3-VL-2B-Instruct-GGUF/resolve/main/mmproj-Qwen3VL-2B-Instruct-Q8_0.gguf
ls -lh ~/model          # 約 1.1G 與 425M
```
若 404：到 `https://huggingface.co/Qwen/Qwen3-VL-2B-Instruct-GGUF` 的 Files 頁看實際檔名。

## 5. 安裝腳本
```bash
cd ~ && git clone <你的 repo 網址> phone-ai-agent    # 或把 repo 解壓到 ~/phone-ai-agent
mkdir -p ~/backup && cp ~/*.py ~/*.sh ~/backup/ 2>/dev/null
bash ~/phone-ai-agent/install.sh
```

## 6. Anthropic API Key
1. console.anthropic.com 註冊 -> Plans & Billing 儲值（預付制；先 $5）-> Limits 設每月上限。
2. Settings -> API keys -> Create key：名稱 `phone-agent`、**Linked account 選「未連結」**、**工作區選「預設工作區」**。
   （選「你的帳號」會拿到 `sk-ant-usr-` 個人 Key，這個專案用它得到 401。）
3. 複製後立刻在 Termux 執行（貼上時畫面不顯示字，正常）：
```bash
read -s -p "貼上API Key後按Enter: " K; echo; echo "長度: ${#K}"; echo "$K" > ~/.claude_key; chmod 600 ~/.claude_key; unset K
```
   「長度」應為一百多；是 0 代表剪貼簿是空的，回 Console 重建 Key 再貼。
4. 驗證（不要 cat Key）：`head -c 10 ~/.claude_key; echo` ，然後 `python ~/ctest.py` 應印出 `回答: OK`。

## 7. 驗證本地模型
```bash
~/look.sh "設定圖示" 1     # 5 秒內切到桌面；看 /sdcard/Download/look_1.png 的紅圈
python ~/path.py "設定,連線,Wi-Fi"   # 從桌面開始
```

## 8. 朗讀
```bash
termux-tts-speak -s MUSIC "測試"     # 要 -s MUSIC
```

## 9. 桌面入口（未驗證）
`install.sh` 已把 `scripts/widget/assistant.sh` 放到 `~/.shortcuts/tasks/`。
單顆桌面圖示：桌面長按 -> 小工具 -> Termux:Widget 的「shortcut」-> 選 `assistant`。
按下去 -> 跳輸入框 -> assistant.py --once 自己判斷：指令表命中直接下指令，沒命中交給 agent2.py（Claude 看畫面操作）。
需要：Termux:Widget 與 Termux:API 都裝（跟 Termux 同來源）、Termux 電池設無限制、可能要開「顯示在其他應用程式上層」。
（整條路未在手機驗證）

## 10. 安裝 PhoneAI Overlay App（操作介面）
1. 電腦或手機瀏覽器登入 GitHub → 本 repo 的 **Actions** → 最新一次成功的「Android APK」→ 最下面 **Artifacts** 的 `phoneai-overlay-apk`（zip）→ 解壓縮，把 `app-debug.apk` 放進手機的「下載」。
2. 在 Termux（repo 要在 `main`）：
```bash
cd ~/phone-ai-agent && git pull && bash install.sh && bash ~/overlay_install.sh
```
   `overlay_install.sh` 會：用 root 安裝 APK（安裝來源標成 Google 套件安裝程式）、開「顯示在其他應用程式上層」與通知權限、
   把三星 Now Bar 的測試開關 `enable_notification_nowbar_test` 設成 1（見 ROADMAP 8e）、讓 Termux 允許外部 App 呼叫（`allow-external-apps`）並授權 RUN_COMMAND。
3. **開無障礙服務**（漣漪與浮動按鈕靠它；若手機上已有別的無障礙服務，要保留，下面以 SCENE 為例，請換成 `su -c "settings get secure enabled_accessibility_services"` 印出的原值再加上我們的）：
```bash
su -c "settings put secure enabled_accessibility_services <原本的服務>:com.phoneai.overlay/com.phoneai.overlay.RippleAccessibilityService"
```
4. 打開 App，在 root 管理 App 按允許。主畫面最上面就是輸入框。
5. （選用）下拉快速設定面板編輯，把「AI 助手」磁貼拖進去；App 主畫面可勾選浮動按鈕、設定預設數位助理。
- 之後的更新：下載新 APK 覆蓋「下載」裡的檔案，再跑第 2 步（APK 用固定金鑰簽名，直接覆蓋安裝；只有從固定金鑰之前的舊版升級那一次要先解除安裝）。
- 看所有 App 的包名：`python ~/applist.py`。
- 副作用提醒：Now Bar 測試開關設成 1 後，其他 App 的「可升級通知」也可能出現在 Now Bar；要還原：`su -c "settings put secure enable_notification_nowbar_test 0"`（我們的膠囊也會消失）。

