#!/data/data/com.termux/files/usr/bin/bash
# 由 termux-job-scheduler 每 15 分鐘叫一次：跑該跑的定時查詢（見 schedule.py）
cd "$HOME" && python "$HOME/schedule.py" --run-due >> "$HOME/schedule.log" 2>&1
