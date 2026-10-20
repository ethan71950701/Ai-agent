#!/data/data/com.termux/files/usr/bin/bash
# snap.sh — 等 5 秒（讓你切到要測的畫面）後截圖並縮圖，輸出 ~/s_small.png
# 用法: ~/snap.sh [寬度，預設 540]
sleep 5
su -c "screencap -p /data/data/com.termux/files/home/s.png; chown $(id -u):$(id -g) /data/data/com.termux/files/home/s.png"
magick ~/s.png -resize ${1:-540}x ~/s_small.png
echo "已截圖"
