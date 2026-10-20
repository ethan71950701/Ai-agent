#!/data/data/com.termux/files/usr/bin/bash
# 把 repo 裡的腳本複製到家目錄 ~（腳本之間用 ~/xxx 路徑互相呼叫）。
# 注意：會覆蓋同名檔案。先備份:  mkdir -p ~/backup && cp ~/*.py ~/*.sh ~/backup/ 2>/dev/null
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
cp "$DIR"/scripts/*.py "$DIR"/scripts/*.sh ~/
chmod +x ~/look.sh ~/snap.sh ~/sizetest.sh
mkdir -p ~/.shortcuts/tasks
cp "$DIR"/scripts/widget/assistant.sh ~/.shortcuts/tasks/assistant.sh
chmod +x ~/.shortcuts/tasks/assistant.sh
echo "安裝完成。Key 請自行存到 ~/.claude_key（見 docs/SETUP.md），不要放進 repo。"
