package com.phoneai.overlay;

import android.content.Intent;
import android.speech.RecognitionService;
import android.speech.SpeechRecognizer;

/** 助理服務的設定檔要求有一個 RecognitionService；我們實際用系統的語音辨識視窗，所以這裡只回錯誤。 */
public class NoRecognitionService extends RecognitionService {
    @Override
    protected void onStartListening(Intent intent, Callback cb) {
        try { cb.error(SpeechRecognizer.ERROR_CLIENT); } catch (Exception ignored) { }
    }

    @Override
    protected void onCancel(Callback cb) { }

    @Override
    protected void onStopListening(Callback cb) { }
}
