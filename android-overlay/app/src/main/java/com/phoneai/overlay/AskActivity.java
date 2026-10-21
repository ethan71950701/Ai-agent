package com.phoneai.overlay;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.window.OnBackInvokedDispatcher;

/** 助理面板（仿 Gemini 的底部面板）。磁貼、浮動按鈕、電源鍵助理都開這個；預設一開就開始聽（extra "voice" 預設 true）。 */
public class AskActivity extends Activity {
    private AssistantSheet sheet;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setShowWhenLocked(true);
        sheet = new AssistantSheet(this);
        setContentView(sheet.view());
        sheet.enter();
        String preset = getIntent().getStringExtra("text");   // 首頁輸入框送來的文字：直接送出（不自動聽）
        if (preset != null && !preset.isEmpty()) sheet.presetAndSend(preset);
        String q = getIntent().getStringExtra("question");
        if (q != null) sheet.setQuestion(q);   // agent2 要問使用者問題（ask_user）：回答問題模式
        String rt = getIntent().getStringExtra("reply_text");
        if (rt != null) sheet.showReply("", rt);   // 助理的文字回答（面板已經關掉時）：同一個面板直接顯示回答卡片＋接續對話的輸入列
        // targetSdk 36 之後系統預設用「預測返回」，onBackPressed 不會被呼叫，要自己註冊
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, () -> sheet.dismiss());
        A11yGuard.ensure(this);   // 開面板時順便檢查無障礙服務有沒有被關掉
        voicePending = autoVoice(getIntent());   // 要自動聽：等視窗真的取得焦點後才開始（背景時麥克風會被靜音，錄到的是空白）
    }

    private boolean voicePending;

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && voicePending) {
            voicePending = false;
            sheet.view().postDelayed(sheet::startVoice, 150);
        }
    }

    /** 開面板要不要自動開始聽：呼叫端沒說不要（extra voice=false），而且（這支手機沒有「語音一律用系統視窗」，或已經裝了 App 內離線辨識）才聽。
     *  只能用系統視窗的手機自動聽只會跳出醜視窗，所以那種不自動聽；裝了離線辨識就不會跳視窗，可以直接聽。 */
    private boolean autoVoice(Intent i) {
        boolean sysOnly = getSharedPreferences("cfg", MODE_PRIVATE).getBoolean("voice_sys", false) && !SherpaEngine.ready(this);
        return i.getBooleanExtra("voice", true) && !sysOnly;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);  // singleTask：面板已經開著時又被叫出來，重新開始聽
        setIntent(intent);
        String q = intent.getStringExtra("question");
        if (q != null) sheet.setQuestion(q);
        String rt = intent.getStringExtra("reply_text");
        if (rt != null) sheet.showReply("", rt);
        if (autoVoice(intent)) sheet.startVoice();
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] grants) {
        super.onRequestPermissionsResult(req, perms, grants);
        sheet.onPermissionResult(req, grants);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        sheet.onActivityResult(req, res, data);
    }

    @Override
    protected void onDestroy() {
        sheet.destroy();
        super.onDestroy();
    }
}
