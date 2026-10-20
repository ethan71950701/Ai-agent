#!/data/data/com.termux/files/usr/bin/bash
# look.sh — 用「本地模型」找座標，只看不點：把它認為的位置畫成紅圈，存到 /sdcard/Download/look_<編號>.png
# 用法: ~/look.sh "Wi-Fi選項" 3     （第二個參數是「數字」編號，只是輸出檔名）
# 流程: 等 5 秒切畫面 -> 截圖 -> 縮 360 寬 -> 把 Termux 拉回前景（背景會慢 4 倍）-> 模型 -> 畫圈
TARGET="$1"; N="${2:-1}"
echo "5 秒內切到要測的畫面..."
sleep 5
su -c "screencap -p /data/data/com.termux/files/home/s.png; chown $(id -u):$(id -g) /data/data/com.termux/files/home/s.png"
magick ~/s.png -resize 360x ~/s_small.png
su -c "am start -n com.termux/com.termux.app.TermuxActivity" >/dev/null 2>&1; echo "已截圖，Termux 已回到前景"
START=$SECONDS
OUT=$(~/llama.cpp/build/bin/llama-mtmd-cli \
  -m ~/model/Qwen3VL-2B-Instruct-Q4_K_M.gguf \
  --mmproj ~/model/mmproj-Qwen3VL-2B-Instruct-Q8_0.gguf \
  --image ~/s_small.png -c 2048 \
  -p "找出${TARGET}。只回傳標準JSON。找到就回傳{\"found\":true,\"x\":中心x,\"y\":中心y}，x和y都是0到1000的整數，不要小數。畫面上沒有就回傳{\"found\":false}" 2>~/last_err.log)
echo "原始回答: $OUT"
echo "耗時: $((SECONDS-START)) 秒"
J=$(echo "$OUT" | tr -d ' \n')
if echo "$J" | grep -q '"found":false'; then echo "結果: 它說找不到"; exit 0; fi
X=$(echo "$J" | grep -oE '"?x"?:[0-9.]+' | head -1 | sed 's/.*://')
Y=$(echo "$J" | grep -oE '"?y"?:[0-9.]+' | head -1 | sed 's/.*://')
if [ -z "$X" ] || [ -z "$Y" ]; then echo "結果: 無法解析座標"; exit 1; fi
X=$(awk -v v="$X" 'BEGIN{ if (v<=1.5) v=v*1000; printf "%d", v }')
Y=$(awk -v v="$Y" 'BEGIN{ if (v<=1.5) v=v*1000; printf "%d", v }')
echo "換算後相對座標: $X,$Y"
W=$(identify -format %w ~/s_small.png); H=$(identify -format %h ~/s_small.png)
PX=$((X*W/1000)); PY=$((Y*H/1000))
magick ~/s_small.png -fill none -stroke red -strokewidth 6 -draw "circle $PX,$PY $PX,$((PY+30))" ~/look_$N.png
su -c "cp /data/data/com.termux/files/home/look_$N.png /sdcard/Download/look_$N.png"
echo "結果: 已輸出 look_$N.png 到「下載」，紅圈是它認為的位置"
