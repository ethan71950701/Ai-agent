package com.phoneai.overlay;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/** 桌面小工具：點一下就開助理面板並開始聽（跟長按電源鍵叫出的面板一樣）。 */
public class VoiceWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context c, AppWidgetManager mgr, int[] ids) {
        Intent i = new Intent(c, AskActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(c, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        for (int id : ids) {
            RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_voice);
            rv.setOnClickPendingIntent(R.id.widget_root, pi);
            mgr.updateAppWidget(id, rv);
        }
    }
}
