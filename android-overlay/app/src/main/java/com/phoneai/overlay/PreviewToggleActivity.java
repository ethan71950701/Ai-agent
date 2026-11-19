package com.phoneai.overlay;

import android.app.Activity;
import android.os.Bundle;

/** 看不見的跳板：點 Now Bar／通知時開關背景預覽窗，做完馬上關掉自己（沒有畫面）。 */
public class PreviewToggleActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            if (!OverlayService.vdSf.isEmpty()) RippleAccessibilityService.togglePreview(OverlayService.vdSf);   // 有背景螢幕：開關預覽窗
            else startActivity(new android.content.Intent(this, MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP));   // 沒有：開 App
        } catch (Throwable ignored) { }
        finish();
        overridePendingTransition(0, 0);
    }
}
