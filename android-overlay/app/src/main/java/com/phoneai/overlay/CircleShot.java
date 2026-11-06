package com.phoneai.overlay;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;

/** 圈選提問的小工具：背景截圖、圖片轉 base64、組成 Claude 格式的圖片內容。 */
final class CircleShot {
    private CircleShot() { }

    /** 面板打開時在背景截一張畫面，好了在主執行緒回呼。 */
    static void prefetch(final Context app, final java.util.function.Consumer<Bitmap> onReady) {
        new Thread(() -> {
            try {
                Bitmap b = AgentLoop.screenshotBitmap();
                if (b == null) return;
                if (onReady != null) new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> onReady.accept(b));
            } catch (Throwable t) {
                CrashLog.w("圈選預先截圖", t);
            }
        }, "circle-prefetch").start();
    }

    static String jpeg64(Bitmap b, int maxSide, int quality) {
        float s = Math.min(1f, maxSide / (float) Math.max(b.getWidth(), b.getHeight()));
        Bitmap x = s < 1f ? Bitmap.createScaledBitmap(b, Math.max(1, Math.round(b.getWidth() * s)), Math.max(1, Math.round(b.getHeight() * s)), true) : b;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        x.compress(Bitmap.CompressFormat.JPEG, quality, out);
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }

    static JSONObject img(String b64) throws Exception {
        return new JSONObject().put("type", "image").put("source", new JSONObject().put("type", "base64").put("media_type", "image/jpeg").put("data", b64));
    }
}
