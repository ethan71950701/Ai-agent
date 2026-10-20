#!/data/data/com.termux/files/usr/bin/bash
# 桌面小工具（Termux:Widget）用。放在 ~/.shortcuts/tasks/ 會在背景執行，不會開出 Termux 畫面。
# 流程: 跳出原生輸入框（中文輸入法可用）-> 把文字交給 assistant.py --once
#       assistant.py 會自己判斷：指令表命中 -> 直接下指令（免費）；沒命中 -> agent2.py 讓 Claude 看畫面操作（花 API）；句首加 ai 強制走 Claude
# 狀態: 寫好，尚未在使用者手機上驗證。需要 Termux:API，且可能要開「顯示在其他應用程式上層」。
TEXT=$(termux-dialog text -t "要做什麼？" -i "例如：開藍牙、開IG；其他句子交給AI操作" | python -c "
import sys, json
try:
    d = json.load(sys.stdin)
    print(d.get('text', '') if d.get('code') == -1 else '')
except Exception:
    print('')
")
[ -z "$TEXT" ] && exit 0
sleep 1
python ~/assistant.py --once "$TEXT" >> ~/assistant.log 2>&1
