package com.phoneai.overlay;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;

/** 圈選提問的小工具：背景截圖、圖片轉 base64、組成 Claude 格式的圖片內容。 */
final class CircleShot {
    private CircleShot() { }

    /** 面板打開時在背景截一張畫面，好了在主執行緒回呼（截不到回 null）。 */
    /** 截圖當下的前景 App 與畫面有沒有密碼輸入框（面板之後會蓋在上面，要在截圖的同時記下來）。 */
    static volatile String lastPkg;
    static volatile boolean lastPassword;
    /** 同一張截圖的「硬體點陣圖」版本（已經在背景執行緒上傳到 GPU）：面板顯示它，第一幀才不用把 18MB 的圖現場上傳而頓一下。截圖給 AI 用的還是一般版本。 */
    static volatile Bitmap lastHw;

    /** 這張截圖要不要先問使用者才能送給 AI：回 {記號, 問的話}，沒事回 null。 */
    static String[] sensitive(Context c) {
        String pkg = lastPkg;
        if (pkg == null || pkg.isEmpty()) return null;
        String name = ProtectedApps.label(c, pkg);
        if (ProtectedApps.isProtected(c, pkg))
            return new String[]{pkg, String.format(L.t("你圈選的畫面是「%s」（金融／支付類，或你標記保護的 App）。要把選取的畫面傳給 AI 嗎？"), name)};
        if (lastPassword)
            return new String[]{pkg + "#pw", String.format(L.t("你圈選的畫面（「%s」）有密碼輸入框，可能是登入頁。要把選取的畫面傳給 AI 嗎？"), name)};
        return null;
    }

    static void prefetch(final Context app, final java.util.function.Consumer<Bitmap> onReady) {
        new Thread(() -> {
            try {
                lastPkg = ElementList.foregroundPackage();   // null＝面板下面是 Vertex 自己的畫面，或讀不到別的 App 的視窗（無障礙沒開）
                lastPassword = ElementList.hasPasswordField();
                final Bitmap b = AgentLoop.screenshotBitmap();
                lastHw = null;
                if (b != null) try { lastHw = b.copy(Bitmap.Config.HARDWARE, false); } catch (Throwable ignored) { }
                if (onReady != null) new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> onReady.accept(b));   // 截不到時 b 是 null
            } catch (Throwable t) {
                CrashLog.w("圈選預先截圖", t);
                if (onReady != null) new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> onReady.accept(null));
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
