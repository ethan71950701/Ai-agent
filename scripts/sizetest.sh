#!/data/data/com.termux/files/usr/bin/bash
# sizetest.sh — 同一張截圖用不同寬度各跑一次「本地模型」，比較耗時與座標（只畫圈，不點擊）
# 用法: ~/sizetest.sh "手電筒圖示"
TARGET="$1"
echo "5 秒內切到要測的畫面，之後請不要碰手機..."
sleep 5
su -c "screencap -p /data/data/com.termux/files/home/s.png; chown $(id -u):$(id -g) /data/data/com.termux/files/home/s.png"
su -c "am start -n com.termux/com.termux.app.TermuxActivity" >/dev/null 2>&1
sleep 2
for CFG in "360:" "540:" "720:" "540:--image-min-tokens 1024"; do
  W="${CFG%%:*}"; EXTRA="${CFG#*:}"
  LABEL="$W"; [ -n "$EXTRA" ] && LABEL="${W}m"
  magick ~/s.png -resize ${W}x ~/t_$LABEL.png
  START=$SECONDS
  OUT=$(~/llama.cpp/build/bin/llama-mtmd-cli \
    -m ~/model/Qwen3VL-2B-Instruct-Q4_K_M.gguf \
    --mmproj ~/model/mmproj-Qwen3VL-2B-Instruct-Q8_0.gguf \
    --image ~/t_$LABEL.png -c 2048 $EXTRA \
    -p "找出${TARGET}。只回傳標準JSON。找到就回傳{\"found\":true,\"x\":中心x,\"y\":中心y}，x和y都是0到1000的整數，不要小數。畫面上沒有就回傳{\"found\":false}" 2>/dev/null)
  T=$((SECONDS-START))
  J=$(echo "$OUT" | tr -d ' \n')
  if echo "$J" | grep -q '"found":false'; then echo "[$LABEL] ${T}秒 說找不到"; continue; fi
  X=$(echo "$J" | grep -oE '"?x"?:[0-9.]+' | head -1 | sed 's/.*://')
  Y=$(echo "$J" | grep -oE '"?y"?:[0-9.]+' | head -1 | sed 's/.*://')
  if [ -z "$X" ] || [ -z "$Y" ]; then echo "[$LABEL] ${T}秒 無法解析: $OUT"; continue; fi
  X=$(awk -v v="$X" 'BEGIN{ if (v<=1.5) v=v*1000; printf "%d", v }')
  Y=$(awk -v v="$Y" 'BEGIN{ if (v<=1.5) v=v*1000; printf "%d", v }')
  IW=$(identify -format %w ~/t_$LABEL.png); IH=$(identify -format %h ~/t_$LABEL.png)
  PX=$((X*IW/1000)); PY=$((Y*IH/1000))
  magick ~/t_$LABEL.png -fill none -stroke red -strokewidth 5 -draw "circle $PX,$PY $PX,$((PY+25))" ~/size_$LABEL.png
  su -c "cp /data/data/com.termux/files/home/size_$LABEL.png /sdcard/Download/size_$LABEL.png"
  echo "[$LABEL] ${T}秒 座標 $X,$Y"
done
echo "完成，請到「下載」資料夾看 size_*.png 的紅圈"
