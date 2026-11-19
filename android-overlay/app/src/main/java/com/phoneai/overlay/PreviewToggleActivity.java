package com.phoneai.overlay;

import android.app.Activity;
import android.os.Bundle;

/** 看不見的跳板：點 Now Bar／通知時開關背景預覽窗，做完馬上關掉自己（沒有畫面）。 */
public class PreviewToggleActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try { RippleAccessibilityService.togglePreview(OverlayService.vdSf); } catch (Throwable ignored) { }
        finish();
        overridePendingTransition(0, 0);
    }
}
