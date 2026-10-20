#!/data/data/com.termux/files/usr/bin/bash
# 把 repo 裡的腳本複製到家目錄 ~（腳本之間用 ~/xxx 路徑互相呼叫）。
# 注意：會覆蓋同名檔案。先備份:  mkdir -p ~/backup && cp ~/*.py ~/*.sh ~/backup/ 2>/dev/null
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
cp "$DIR"/scripts/*.py "$DIR"/scripts/*.sh ~/
chmod +x ~/look.sh ~/snap.sh ~/sizetest.sh ~/overlay_install.sh ~/schedule_tick.sh
mkdir -p ~/.shortcuts/tasks
cp "$DIR"/scripts/widget/assistant.sh ~/.shortcuts/tasks/assistant.sh
chmod +x ~/.shortcuts/tasks/assistant.sh
# 記下這份腳本是哪個 commit：agent2 啟動時會印出來，跟 App 主畫面的版本對照，就知道兩邊有沒有都更新成功
{ git -C "$DIR" rev-parse --short HEAD 2>/dev/null || echo dev; date "+%m/%d %H:%M"; } | paste -sd' ' > ~/.phoneai_version
# 記下這份腳本來自哪個 GitHub repo（fork 的人才會更新到自己的 repo；update_app.py 讀這個檔）
SLUG="$(git -C "$DIR" remote get-url origin 2>/dev/null | sed -E 's#^(https://github.com/|git@github.com:)##; s#\.git$##')"
case "$SLUG" in */*) echo "$SLUG" > ~/.phoneai_repo ;; esac
# 以前版本在 ~/.bashrc 加過「開 Termux 自動印上一次任務」的兩行（# phoneai-last-run）；現在改在 App 的「上一次任務的過程」頁看，把它清掉
sed -i '/# phoneai-last-run/,+1d' ~/.bashrc 2>/dev/null || true
echo "安裝完成（腳本版本：$(cat ~/.phoneai_version)）。"
# 只有還沒存 Key 時才提醒（每次更新都印很吵）
[ -s ~/.claude_key ] || echo "還沒有 API Key：請自行存到 ~/.claude_key（見 docs/SETUP.md），不要貼給任何人、不要放進 repo。"
