package com.phoneai.overlay;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.provider.AlarmClock;

/**
 * 設鬧鐘：把「設定鬧鐘」的請求交給手機自己的時鐘 App（系統的標準做法，不需要權限、不用背景服務）。
 * 會打開時鐘的畫面，鬧鐘要在時鐘裡按儲存才會真的設定，這樣使用者看得到。
 */
final class AlarmTool {
    private AlarmTool() { }

    static String set(Context c, int hour, int minute, String label) {
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return "錯誤：時間不對（hour 0-23、minute 0-59）";
        Intent it = new Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                .putExtra(AlarmClock.EXTRA_MESSAGE, label == null ? "" : label)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(it);
            return String.format(java.util.Locale.US, "已打開時鐘，要在時鐘裡按儲存，%02d:%02d 的鬧鐘才會設定", hour, minute);
        } catch (ActivityNotFoundException e) {
            return "錯誤：找不到可以設鬧鐘的時鐘 App";
        }
    }
}
