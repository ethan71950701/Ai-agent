package com.phoneai.overlay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * 背景虛擬螢幕的「預覽」卡片：只負責「畫」，不處理觸控（觸控由 PreviewTouch 那個透明小視窗收）。
 * 它放在一個全螢幕、不攔觸控的透明視窗裡，位置靠 translationX/Y 決定、縮放靠 scale，所以移動／縮放／關閉動畫
 * 全部是 GPU 合成，不需要叫系統重新排版視窗（之前每幀 updateViewLayout 就是不順的原因）。
 * 畫面來源（優先）：root 端 vdisplay.py 轉送的 H.264 即時影片，硬體解碼（H264Stream）。
 * 退回方案：連不上串流（或 5 秒內沒畫面）時，改成用 root 每半秒 `screencap -d <SurfaceFlinger 編號> -p` 截一張（約每秒 1~2 張，會有點卡）。
 */
final class PreviewView extends FrameLayout {
    static final float BASE_RADIUS_DP = 16f;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ImageView img;
    private final TextureView video;
    private final H264Stream stream;
    private volatile boolean gotVideo, fellBack;
    private volatile boolean running = true;
    private final String sf;
    private int width;
    private final float aspect;  // 高/寬，跟背景虛擬螢幕的尺寸一致（agent2 用 vd 指令告知）
    private final float d;
    private final GradientDrawable bg = new GradientDrawable();
    private float radius;
    private DotLayer dots;
    private boolean takeover;

    /** 預覽上已經沒有任何按鈕（卡片整個是主螢幕）：永遠不是控制區。保留給 PreviewTouch 呼叫。 */
    static int ctrlHit(float x, float y, float cardW, boolean takeover, float d) { return 0; }

    /** 接手時你點的地方會閃一個圈，讓你知道點到哪（只是畫面回饋，不影響送出的指令）。 */
    private static final class DotLayer extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float d;
        private float x, y;
        private long start;

        DotLayer(Context c, float d) {
            super(c);
            this.d = d;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2 * d);
        }

        void pulse(float px, float py) {
            x = px; y = py;
            start = android.os.SystemClock.uptimeMillis();
            invalidate();
        }

        @Override protected void onDraw(Canvas c) {
            if (start == 0) return;
            float t = (android.os.SystemClock.uptimeMillis() - start) / 380f;
            if (t >= 1f) { start = 0; return; }
            p.setColor(((int) (255 * (1f - t) * 0.9f) << 24) | 0xFFFFFF);
            c.drawCircle(x, y, (8 + 26 * t) * d, p);
            postInvalidateOnAnimation();
        }
    }

    PreviewView(Context c, String sf, int width, float aspect) {
        super(c);
        this.aspect = aspect;
        this.sf = sf;
        this.width = width;
        this.d = c.getResources().getDisplayMetrics().density;
        this.radius = BASE_RADIUS_DP * d;
        bg.setColor(0xFF15151C);
        bg.setCornerRadius(radius);
        bg.setStroke((int) Math.max(1, d), 0x66FFFFFF);
        setBackground(bg);
        setClipToOutline(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius); }
        });

        // 告訴系統這個視窗在做動畫、要高更新率（三星的自動更新率可能把浮動視窗壓在 60Hz；有沒有用要實測）
        if (android.os.Build.VERSION.SDK_INT >= 35) {   // Android 15 起才有這個設定
            try { setRequestedFrameRate(View.REQUESTED_FRAME_RATE_CATEGORY_HIGH); } catch (Throwable ignored) { }
        }

        int h0 = (int) (width * aspect);
        video = new TextureView(c);
        addView(video, new LayoutParams(width, h0));
        stream = new H264Stream(c, OverlayService.vdW, OverlayService.vdH, new H264Stream.Callback() {
            @Override public void onFirstFrame() { gotVideo = true; }
            @Override public void onFail(String why) { ui.post(PreviewView.this::fallBack); }
        });
        if (DirectHost.ready(OverlayService.vdId)) {
            // 直接顯示：背景螢幕直接畫進這個 TextureView（不經影片壓縮、轉送、解碼）
            direct = true;
            gotVideo = true;
            video.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override public void onSurfaceTextureAvailable(android.graphics.SurfaceTexture st, int w, int h) {
                    st.setDefaultBufferSize(OverlayService.vdW, OverlayService.vdH);
                    directSurface = new android.view.Surface(st);
                    if (!DirectHost.setSurface(directSurface)) android.util.Log.w(OverlayService.TAG, "direct preview: setSurface failed");
                }
                @Override public void onSurfaceTextureSizeChanged(android.graphics.SurfaceTexture st, int w, int h) {
                    st.setDefaultBufferSize(OverlayService.vdW, OverlayService.vdH);  // TextureView 改大小時會把緩衝區改成視圖大小，改回背景螢幕大小
                }
                @Override public boolean onSurfaceTextureDestroyed(android.graphics.SurfaceTexture st) {
                    releaseDirect();
                    return true;
                }
                private long t0 = System.nanoTime();
                private int frames;
                @Override public void onSurfaceTextureUpdated(android.graphics.SurfaceTexture st) {
                    frames++;   // 量「背景螢幕實際畫到預覽」的幀率（logcat: preview direct fps）；畫面靜止時本來就低
                    long now = System.nanoTime();
                    if (now - t0 >= 3_000_000_000L) {
                        android.util.Log.i(OverlayService.TAG, String.format(java.util.Locale.US, "preview direct fps=%.1f",
                                frames * 1e9 / (now - t0)));
                        t0 = now;
                        frames = 0;
                    }
                }
            });
        } else {
            video.setSurfaceTextureListener(stream);
            ui.postDelayed(() -> { if (!gotVideo) fallBack(); }, 5000);  // 5 秒內沒出畫面也退回截圖
        }

        img = new ImageView(c);  // 退回截圖模式才會顯示
        img.setScaleType(ImageView.ScaleType.FIT_XY);
        img.setBackgroundColor(Color.BLACK);
        img.setVisibility(View.GONE);
        addView(img, new LayoutParams(width, h0));

        // 預覽打開時，AI 執行中的光暈改畫在這張小卡片的邊緣（主螢幕的全螢幕光暈暫停）：面積小、更新慢，不會拖慢預覽畫面
        cardGlow = new EdgeGlowView(c, 0.55f, BASE_RADIUS_DP * d, 50);
        cardGlow.setVisibility(View.GONE);
        addView(cardGlow, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        dots = new DotLayer(c, d);
        addView(dots, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /** 進入／離開接手模式：預覽上不再有按鈕，外觀不變（Now Bar 與小白條會反映狀態）。 */
    void setTakeover(boolean on, String why) {
        takeover = on;
    }

    /** 接手時你點的地方閃一個圈（座標是卡片座標）。 */
    void pulse(float x, float y) {
        dots.pulse(x, y);
    }

    int cardWidth() { return width; }

    int cardHeight() { return (int) (width * aspect); }

    float aspect() { return aspect; }

    /** 改變預覽寬度（高度依比例算）：卡片本身與子 View 一起改；左上角位置不動。 */
    void setWidthPx(int w) {
        if (w == width) return;
        width = w;
        int h = (int) (w * aspect);
        for (View v : new View[]{video, img}) {
            ViewGroup.LayoutParams p = v.getLayoutParams();
            p.width = w;
            p.height = h;
            v.setLayoutParams(p);
        }
        ViewGroup.LayoutParams own = getLayoutParams();
        if (own != null) {
            own.width = w;
            own.height = h;
            setLayoutParams(own);
        }
    }

    /** 圓角（像素）：上滑時跟著進度變大。 */
    void setRadius(float px) {
        if (Math.abs(px - radius) < 1.5f * d && px != BASE_RADIUS_DP * d) return;  // 圓角每變 1.5dp 才重算一次，省每幀重算輪廓的開銷
        radius = px;
        bg.setCornerRadius(px);
        invalidateOutline();
    }

    private void fallBack() {
        if (fellBack || gotVideo || !running) return;
        fellBack = true;
        stream.stop();
        video.setVisibility(View.GONE);
        img.setVisibility(View.VISIBLE);
        new Thread(this::loop, "vd-preview").start();
    }

    private void loop() {
        while (running) {
            byte[] png = capture();
            if (png != null && png.length > 0 && running) {
                Bitmap b = BitmapFactory.decodeByteArray(png, 0, png.length);
                if (b != null) ui.post(() -> {
                    if (!running) return;
                    ViewGroup.LayoutParams p = img.getLayoutParams();
                    int h = width * b.getHeight() / Math.max(1, b.getWidth());
                    if (p.height != h) { p.height = h; img.setLayoutParams(p); }
                    img.setImageBitmap(b);
                });
            }
            try { Thread.sleep(500); } catch (InterruptedException e) { return; }
        }
    }

    /** sf 只允許數字（呼叫端已檢查），避免被塞進 shell 指令。 */
    private byte[] capture() {
        try {
            Process p = new ProcessBuilder(Root.su(), "-c", "screencap -d " + sf + " -p").redirectErrorStream(false).start();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream in = p.getInputStream()) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            p.waitFor();
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    void stop() {
        running = false;
        stream.stop();
        releaseDirect();
    }

    private EdgeGlowView cardGlow;

    /** AI 執行中且使用者有開光暈：顯示卡片邊緣的光暈。 */
    void setGlow(boolean on) {
        cardGlow.setVisibility(on ? View.VISIBLE : View.GONE);
    }

    private boolean direct;
    private android.view.Surface directSurface;

    /** 直接顯示：預覽關掉時請主機改畫回自己的 ImageReader，再放掉 Surface。 */
    private void releaseDirect() {
        if (!direct || directSurface == null) return;
        DirectHost.setSurface(null);
        directSurface.release();
        directSurface = null;
    }
}
