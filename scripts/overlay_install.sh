#!/data/data/com.termux/files/usr/bin/bash
# 安裝 PhoneAI Overlay 的 APK，並用 root 直接開好權限（懸浮層、通知）。
# 用法: bash ~/overlay_install.sh [APK 路徑，預設 /sdcard/Download/app-debug.apk]
# 狀態（2026-10-17）：未在手機實測。
APK="${1:-/sdcard/Download/app-debug.apk}"
PKG=com.phoneai.overlay
if ! su -c "test -f '$APK'"; then
  echo "找不到 $APK（GitHub 下載的是 zip，要先解壓縮出 app-debug.apk）"; exit 1
fi
# -i：把安裝來源標成 Google 套件安裝程式（LiveBridge 是這樣裝的，Now Bar 清單有它；我們用 root 裝時來源是 null，是否影響是推測）
# 現在 APK 用 repo 裡固定的除錯金鑰簽名（android-overlay/debug.keystore），之後的更新可以直接覆蓋安裝、保留 root 授權與設定。
# 只有「從舊的（每次簽名都不同的）版本升級到固定金鑰」那一次會簽名不符，下面的退路會先移除舊版再裝。
su -c "cp '$APK' /data/local/tmp/phoneai.apk && chmod 644 /data/local/tmp/phoneai.apk"
# --user 0：一律裝在主空間。不指定的話，三星雙開（DUAL_APP）等空間會多出一份「分身」。
if ! su -c "pm install -r --user 0 -i com.google.android.packageinstaller /data/local/tmp/phoneai.apk"; then
  echo "覆蓋安裝失敗（簽名不同），移除舊版後重裝..."
  su -c "pm uninstall --user 0 $PKG; pm install --user 0 -i com.google.android.packageinstaller /data/local/tmp/phoneai.apk" || { echo "安裝失敗"; exit 1; }
fi
su -c "rm -f /data/local/tmp/phoneai.apk"
# 清掉其他空間（雙開、安全資料夾等）裡的 PhoneAI 副本，只留主空間 0
for U in $(su -c "pm list users" | grep -o '{[0-9]*:' | tr -d '{:' ); do
  [ "$U" = "0" ] && continue
  if su -c "pm list packages --user $U" | grep -q "package:$PKG"; then
    su -c "pm uninstall --user $U $PKG" >/dev/null 2>&1 && echo "已移除空間 $U 的分身" || echo "空間 $U 的分身移除失敗（請到手機的應用程式設定手動刪）"
  fi
done
su -c "appops set $PKG SYSTEM_ALERT_WINDOW allow"
su -c "pm grant $PKG android.permission.POST_NOTIFICATIONS" 2>/dev/null
# 開無障礙服務（只畫漣漪、不讀畫面）：一般懸浮層在「設定」等 App 前景時會被系統強制隱藏，無障礙懸浮層不會（推測，待實測）
SVC=$PKG/$PKG.RippleAccessibilityService
CUR=$(su -c "settings get secure enabled_accessibility_services" | tr -d '\r')
case "$CUR" in
  *"$SVC"*) ;;
  null|"") su -c "settings put secure enabled_accessibility_services $SVC" ;;
  *) su -c "settings put secure enabled_accessibility_services '$CUR:$SVC'" ;;
esac
su -c "settings put secure accessibility_enabled 1"
echo "無障礙服務：$(su -c 'settings get secure enabled_accessibility_services')"
# 三星 Now Bar 允許名單：SystemUI 預設只讓名單內的 App 的即時通知進膠囊/Now Bar。這個隱藏的測試開關 =1 會略過名單
# （NotificationEntry.setSbn / SettingsHelper.isDevelopRonTestAllowed）。副作用：其他 App 的「可升級通知」也可能出現在 Now Bar。還原: settings put secure enable_notification_nowbar_test 0
su -c "settings put secure enable_notification_nowbar_test 1"
# App 內的輸入框/語音/磁貼/浮動按鈕要靠 Termux 的 RUN_COMMAND 把指令交給 assistant.py：
# 1) Termux 要允許外部 App 呼叫（~/.termux/termux.properties），2) 本 App 要拿到 RUN_COMMAND 權限
mkdir -p ~/.termux
if ! grep -q '^allow-external-apps *= *true' ~/.termux/termux.properties 2>/dev/null; then
  sed -i '/^#\? *allow-external-apps/d' ~/.termux/termux.properties 2>/dev/null
  echo "allow-external-apps=true" >> ~/.termux/termux.properties
fi
termux-reload-settings 2>/dev/null
su -c "pm grant $PKG android.permission.RECORD_AUDIO" || echo "（麥克風授權失敗：第一次開助理面板時再按允許）"
su -c "pm grant $PKG com.termux.permission.RUN_COMMAND" || echo "（RUN_COMMAND 授權失敗：Termux 版本可能太舊）"
echo "安裝來源：$(su -c "dumpsys package $PKG" | grep installerPackageName)"
echo "安裝完成。權限："
su -c "appops get $PKG SYSTEM_ALERT_WINDOW"
# 從 App 內按「檢查並更新」時（UPDATE_FROM_APP=1）：安裝會把 App 關掉，裝完自動重新打開
if [ "$UPDATE_FROM_APP" = "1" ]; then
  su -c "am start -n $PKG/.MainActivity" >/dev/null 2>&1
fi
