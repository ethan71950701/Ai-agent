"""ctest.py — 測試 Anthropic API 連線（純文字）。Key 從 ~/.claude_key 讀，不寫在程式裡。"""
import json, os, urllib.request, urllib.error
key = open(os.path.expanduser("~/.claude_key")).read().strip()
body = {"model": "claude-sonnet-5-5", "max_tokens": 50,
        "messages": [{"role": "user", "content": "只回答OK兩個字"}]}
req = urllib.request.Request(
    "https://api.anthropic.com/v1/messages",
    data=json.dumps(body).encode(),
    headers={"x-api-key": key, "anthropic-version": "2023-06-01",
             "content-type": "application/json"})
try:
    r = json.loads(urllib.request.urlopen(req, timeout=60).read())
    print("回答:", r["content"][0]["text"])
    print("用量:", r["usage"])
except urllib.error.HTTPError as e:
    print("錯誤", e.code, e.read().decode())
