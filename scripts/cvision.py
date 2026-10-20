"""cvision.py — 測 Claude 識圖速度與 token（用 ~/s_small.png，連測 3 次）。先跑 snap.sh 截圖。"""
import json, os, time, base64, urllib.request, urllib.error
key = open(os.path.expanduser("~/.claude_key")).read().strip()
img = base64.b64encode(open(os.path.expanduser("~/s_small.png"), "rb").read()).decode()
body = {"model": "claude-sonnet-5-5", "max_tokens": 100,
  "messages": [{"role": "user", "content": [
    {"type": "image", "source": {"type": "base64", "media_type": "image/png", "data": img}},
    {"type": "text", "text": "描述這張圖最上面有什麼，一句話。"}]}]}
req = urllib.request.Request("https://api.anthropic.com/v1/messages",
  data=json.dumps(body).encode(),
  headers={"x-api-key": key, "anthropic-version": "2023-06-01", "content-type": "application/json"})
for i in range(3):
    t = time.time()
    try:
        r = json.loads(urllib.request.urlopen(req, timeout=60).read())
        text = "".join(b.get("text", "") for b in r["content"] if b.get("type") == "text")
        print(f"第{i+1}次: {time.time()-t:.1f} 秒 | {r['usage']['input_tokens']} 入 / {r['usage']['output_tokens']} 出")
        print("  ", text)
    except urllib.error.HTTPError as e:
        print("錯誤", e.code, e.read().decode())
        break
