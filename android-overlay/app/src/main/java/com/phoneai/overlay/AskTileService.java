package com.phoneai.overlay;

import android.app.PendingIntent;
import android.content.Intent;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** 快速設定磁貼：點一下開輸入視窗。 */
public class AskTileService extends TileService {
    @Override
    public void onStartListening() {
        Tile t = getQsTile();
        if (t != null) {
            t.setState(Tile.STATE_INACTIVE);
            t.setLabel("AI 助手");
            t.updateTile();
        }
    }

    @Override
    public void onClick() {
        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, AskActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        startActivityAndCollapse(pi);
    }
}
