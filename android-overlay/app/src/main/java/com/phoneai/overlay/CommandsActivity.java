package com.phoneai.overlay;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 指令一覽：列出 App 能做的事（固定指令、快速問題、AI 工具、定時任務、手動操作），方便查。 */
public class CommandsActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout[] c = new LinearLayout[1];
        setContentView(Ui.screen(this, L.t("指令一覽"), true, c));
        Ui.enterSpring(this);
        LinearLayout box = c[0];

        Ui.section(this, box, L.t("固定指令（直接執行，不用 AI）"));
        LinearLayout fixed = Ui.card(this, box);
        for (String[] r : Assist.describeFixed()) {
            row(fixed, "「" + r[0] + "」", r[1] + (r[2].isEmpty() ? "" : "　（" + r[2] + "）"));
        }

        Ui.section(this, box, L.t("快速問題（手機直接回答，不用 AI）"));
        LinearLayout quick = Ui.card(this, box);
        row(quick, L.t("「現在幾點」「今天幾號」「今年幾年」"), L.t("查時間與日期"));
        row(quick, L.t("「電量多少」"), L.t("查電池電量"));
        row(quick, L.t("「儲存空間」「記憶體」"), L.t("查容量與記憶體"));
        row(quick, L.t("「什麼手機」「安卓版本」"), L.t("查機型與系統版本"));
        row(quick, L.t("「亮度」「音量」「Wi‑Fi 狀態」"), L.t("查目前的設定"));

        Ui.section(this, box, L.t("AI 助理的工具（需要 AI，聊天時會自己用）"));
        LinearLayout tools = Ui.card(this, box);
        row(tools, "get_time", L.t("查現在的日期與時間"));
        row(tools, "set_alarm", L.t("設鬧鐘（幾分鐘後，或指定幾點）"));
        row(tools, "add_calendar_event", L.t("新增行事曆事件（打開行事曆填好，按儲存才建立）"));
        row(tools, "show_on_map / dial", L.t("在地圖上找地方、撥號（只打開並填好，不會自動導航或撥出）"));
        row(tools, "memory_add / list / update / delete / clear", L.t("記住、列出、修改、刪除待辦與筆記"));
        row(tools, "device_list", L.t("列出已配對的裝置與是否在線"));
        row(tools, "file_find / file_list", L.t("搜尋、瀏覽手機或電腦的檔案"));
        row(tools, "file_get / file_send", L.t("從裝置取檔、傳檔到裝置"));
        row(tools, "file_delete", L.t("刪除檔案（每次都會跳確認框）"));

        Ui.section(this, box, L.t("定時任務（每天重複）"));
        LinearLayout sched = Ui.card(this, box);
        row(sched, L.t("「每天早上 8 點查電量」"), L.t("每天定時用通知回報（只支援快速問題，誤差約 15 分鐘）"));

        Ui.section(this, box, L.t("手動操作（不用 AI）"));
        LinearLayout manual = Ui.card(this, box);
        row(manual, L.t("互傳檔案頁 → 找檔案"), L.t("輸入檔名搜尋手機或電腦"));
        row(manual, L.t("互傳檔案頁 → 瀏覽"), L.t("點進對方的資料夾，點檔案接收"));
        row(manual, L.t("互傳檔案頁 → 傳檔案過去"), L.t("選檔案傳給已配對的裝置"));
    }

    private void row(LinearLayout parent, String title, String desc) {
        TextView t = Ui.body(this, title);
        t.setTextSize(15);
        t.setTextColor(Ui.attr(this, com.google.android.material.R.attr.colorOnSurface));
        parent.addView(t);
        parent.addView(Ui.body(this, desc));
    }
}
