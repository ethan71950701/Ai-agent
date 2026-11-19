package com.phoneai.overlay;

import android.app.Activity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 「公告」頁：收到過的公告都在這裡，隨時可以再看、刪除，或把「不要再提醒」的恢復。 */
final class AnnouncementsUi {
    private AnnouncementsUi() { }

    static String summary(Activity a) {
        int n = Announcements.history(a).size();
        return n == 0 ? L.t("還沒有收到公告") : String.format(L.t("收到過 %d 則（點進去看）"), n);
    }

    static void open(final Activity a, final Runnable done) {
        final List<Announcements.Entry> list = Announcements.history(a);
        final Set<String> muted = Announcements.mutedIds(a);
        if (list.isEmpty()) {
            new MaterialAlertDialogBuilder(a).setTitle(L.t("公告"))
                    .setMessage(L.t("還沒有收到公告。有新公告時，打開 App 就會跳出來，收到過的會留在這裡。"))
                    .setPositiveButton(L.t("好"), null)
                    .setNeutralButton(L.t("立即看公告"), (d, w) -> Announcements.check(a, true)).show();
            return;
        }
        SimpleDateFormat f = new SimpleDateFormat("M/d", Locale.US);
        String[] names = new String[list.size()];
        for (int i = 0; i < names.length; i++) {
            Announcements.Entry e = list.get(i);
            names[i] = (e.severity.equals("critical") ? "⚠ " : "") + (e.title.isEmpty() ? L.t("公告") : e.title) + "　" + f.format(new Date(e.at))
                    + (muted.contains(e.id) ? "　" + L.t("（已不再提醒）") : "");
        }
        new MaterialAlertDialogBuilder(a).setTitle(L.t("公告")).setItems(names, (d, which) -> detail(a, list.get(which), muted.contains(list.get(which).id), done))
                .setNeutralButton(L.t("立即看公告"), (d, w) -> Announcements.check(a, true))
                .setNegativeButton(L.t("關閉"), null).show();
    }

    private static void detail(final Activity a, final Announcements.Entry e, final boolean isMuted, final Runnable done) {
        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(a)
                .setTitle((e.severity.equals("critical") ? "⚠ " : "") + (e.title.isEmpty() ? L.t("公告") : e.title))
                .setMessage(e.body)
                .setPositiveButton(L.t("關閉"), null)
                .setNegativeButton(L.t("刪除"), (d, w) -> { Announcements.deleteHistory(a, e.id); if (done != null) done.run(); });
        if (isMuted) b.setNeutralButton(L.t("恢復提醒"), (d, w) -> { Announcements.mute(a, e.id, false); android.widget.Toast.makeText(a, L.t("之後打開 App 會再提醒這則"), android.widget.Toast.LENGTH_SHORT).show(); });
        b.show();
    }
}
