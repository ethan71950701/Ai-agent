package com.phoneai.overlay;

import android.os.Bundle;
import android.service.voice.VoiceInteractionSession;
import android.service.voice.VoiceInteractionSessionService;

public class AssistSessionService extends VoiceInteractionSessionService {
    @Override
    public VoiceInteractionSession onNewSession(Bundle args) {
        return new Session(this);
    }

    static class Session extends VoiceInteractionSession {
        Session(android.content.Context c) { super(c); }

        @Override
        public void onShow(Bundle args, int flags) {
            super.onShow(args, flags);
            startAssistantActivity(new android.content.Intent(getContext(), AskActivity.class).putExtra("voice", true));
            hide();
        }
    }
}
