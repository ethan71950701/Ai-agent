package com.phoneai.overlay;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** 錯誤紀錄：App 當機或出錯時留下的紀錄（不只是問 AI 的部分）。可以長按選取、分享、存到資料夾、清除。 */
public class ErrorLogActivity extends Activity {
    private TextView body, status;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout[] c = new LinearLayout[1];
        setContentView(Ui.screen(this, L.t("錯誤紀錄"), true, c));
        Ui.enterSpring(this);
        LinearLayout box = c[0];
        status = Ui.infoCard(this, box, 13, false, true);
        Ui.buttons(this, box,
                Ui.button(this, L.t("分享"), v -> LogExport.shareText(this, CrashLog.read(this), "phoneai_errors_")),
                Ui.button(this, L.t("存到 PhoneAI 資料夾"), v -> LogExport.saveText(this, CrashLog.read(this), "phoneai_errors_")),
                Ui.button(this, L.t("清除"), v -> new MaterialAlertDialogBuilder(this).setTitle(L.t("清除錯誤紀錄？"))
                        .setPositiveButton(L.t("清除"), (d, w) -> { CrashLog.clear(this); reload(); })
                        .setNegativeButton(L.t("取消"), null).show()));
        body = Ui.infoCard(this, box, 11, true, true);   // 等寬字、可長按選取
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        String t = CrashLog.read(this);
        if (t.trim().isEmpty()) {
            status.setText(L.t("目前沒有錯誤紀錄。App 當機或出錯時，會自動記在這裡；分享給開發者就能查原因。不含 API Key。"));
            body.setText("");
        } else {
            status.setText(L.t("最新的在最下面。長按可以選取文字；分享或存檔前請自己看過。"));
            body.setText(t);
        }
    }
}
