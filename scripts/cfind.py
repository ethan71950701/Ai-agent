"""cfind.py — 用 Claude 找座標（只畫圈，不點擊），拿來測準確度/速度/token

用法（先用 snap.sh 截圖）:
  ~/snap.sh 540
  NOTHINK=1 python ~/cfind.py "手電筒圖示" 5
環境變數:
  NOTHINK=1      送 thinking={"type":"between_tools"}（關掉前置思考，已驗證可用，最省）
  EFFORT=low     送 output_config={"effort":"low"}（已驗證可用，但模型會把推算寫進答案，不穩定，不建議）
輸出: 秒數與 token、原始回答、相對座標(0~1000)，並把紅圈圖存到 /sdcard/Download/cfind_<n>.png
"""
import json, os, re, sys, time, base64, subprocess, urllib.request, urllib.error

home = os.path.expanduser("~")
key = open(f"{home}/.claude_key").read().strip()
target = sys.argv[1]
n = sys.argv[2] if len(sys.argv) > 2 else "1"
img = base64.b64encode(open(f"{home}/s_small.png", "rb").read()).decode()
prompt = (f"找出{target}。只回傳標準JSON。找到就回傳"
          '{"found":true,"x":中心x,"y":中心y}，x和y都是0到1000的整數，代表在圖片中的相對位置。'
          '畫面上沒有就回傳{"found":false}。不要任何解釋或計算過程，只輸出那一行JSON。')
body = {"model": "claude-sonnet-5-5", "max_tokens": 300,
        "messages": [{"role": "user", "content": [
            {"type": "image", "source": {"type": "base64", "media_type": "image/png", "data": img}},
            {"type": "text", "text": prompt}]}]}
eff = os.environ.get("EFFORT")
if eff:
    body["output_config"] = {"effort": eff}
if os.environ.get("NOTHINK"):
    body["thinking"] = {"type": "between_tools"}
req = urllib.request.Request(
    "https://api.anthropic.com/v1/messages",
    data=json.dumps(body).encode(),
    headers={"x-api-key": key, "anthropic-version": "2023-06-01",
             "content-type": "application/json"})
t = time.time()
try:
    r = json.loads(urllib.request.urlopen(req, timeout=60).read())
except urllib.error.HTTPError as e:
    print("錯誤", e.code, e.read().decode())
    sys.exit(1)
# 回應第一塊可能是 thinking，不是 text；要把所有 text 區塊串起來
blocks = r["content"]
out = "".join(b.get("text", "") for b in blocks if b.get("type") == "text")
print("區塊類型:", [b.get("type") for b in blocks], "| 結束原因:", r.get("stop_reason"))
if not out:
    print("沒有文字內容，完整回應:", json.dumps(r, ensure_ascii=False)[:600])
    sys.exit(1)
print(f"{time.time()-t:.1f} 秒 | {r['usage']['input_tokens']} 入 / {r['usage']['output_tokens']} 出")
print("原始回答:", out)
objs = re.findall(r"\{[^{}]*\}", out)
if objs:
    out = objs[-1]  # 取最後一個 JSON
if re.search(r'found"?\s*:\s*false', out):
    print("結果: 它說找不到")
    sys.exit(0)
x = re.search(r'(?<![A-Za-z])x"?\s*:\s*([0-9.]+)', out)
y = re.search(r'(?<![A-Za-z])y"?\s*:\s*([0-9.]+)', out)
if not (x and y):
    print("結果: 無法解析")
    sys.exit(1)
xv, yv = float(x.group(1)), float(y.group(1))
if xv <= 1.5 and yv <= 1.5:
    xv, yv = xv * 1000, yv * 1000
w, h = subprocess.run(["identify", "-format", "%w %h", f"{home}/s_small.png"],
                      capture_output=True, text=True).stdout.split()
px, py = int(xv * int(w) / 1000), int(yv * int(h) / 1000)
subprocess.run(["magick", f"{home}/s_small.png", "-fill", "none", "-stroke", "red",
                "-strokewidth", "5", "-draw", f"circle {px},{py} {px},{py+25}",
                f"{home}/cfind_{n}.png"])
subprocess.run(["su", "-c", f"cp {home}/cfind_{n}.png /sdcard/Download/cfind_{n}.png"])
print(f"結果: 相對座標 {int(xv)},{int(yv)}，已輸出 cfind_{n}.png 到「下載」，紅圈是它認為的位置")
