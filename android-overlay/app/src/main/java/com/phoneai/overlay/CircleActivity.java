package com.phoneai.overlay;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Base64;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;

/**
 * 圈選提問：助理面板按「圈選」→ 面板收起、截下目前畫面 → 這裡顯示凍結的畫面，用手指圈出想問的地方 →
 * 把圈起來的區域（加一張縮小的整個畫面當上下文）連同問題交給聊天模型回答，可以接著追問。
 * 截圖用無障礙或 root／Shizuku；沒有權限或畫面是受保護的（銀行 App 會是黑的）就截不到。
 */
public class CircleActivity extends Activity {
    private static volatile Bitmap shot;

    /** 收起面板、截圖、打開圈選畫面（在主執行緒呼叫）。 */
    static void begin(final Activity act, Runnable hideSheet) {
        final Context app = act.getApplicationContext();
        try { hideSheet.run(); } catch (Throwable t) { toast(app, t); return; }
        new Thread(() -> {
            try {
                try { Thread.sleep(500); } catch (InterruptedException ignored) { }   // 等面板收掉再截，才不會截到面板
                Bitmap b = AgentLoop.screenshotBitmap();
                if (b == null) {
                    toast(app, L.t("截圖失敗（需要開啟無障礙服務，或使用 root／Shizuku 模式）"));
                    return;
                }
                shot = b;
                app.startActivity(new Intent(app, CircleActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS));
            } catch (Throwable t) {   // 背景執行緒的例外會讓整個 App 當掉：一律攔下來顯示
                toast(app, t);
            }
        }, "circle-shot").start();
    }

    private static void toast(Context c, Throwable t) {
        CrashLog.w("圈選提問", t);
        toast(c, L.t("圈選失敗：") + t);
    }

    private static void toast(Context c, String msg) {
        android.util.Log.w("CircleActivity", msg);
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> Toast.makeText(c, msg, Toast.LENGTH_LONG).show());
    }

    private Bitmap bmp;
    private CircleView cv;
    private LinearLayout panel;
    private ImageView thumb;
    private EditText input;
    private TextView reply;
    private ScrollView replyScroll;
    private TextView sendBtn;
    private RectF box;                       // 圈選範圍（螢幕座標）
    private final JSONArray msgs = new JSONArray();
    private boolean busy;
    private float d;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        bmp = shot;
        shot = null;
        if (bmp == null) { finish(); return; }
        try {
            build();
        } catch (Throwable t) {
            toast(getApplicationContext(), t);
            finish();
        }
    }

    private void build() {
        d = getResources().getDisplayMetrics().density;
        getWindow().setDecorFitsSystemWindows(false);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.BLACK));

        FrameLayout root = new FrameLayout(this);
        ImageView img = new ImageView(this);
        img.setImageBitmap(bmp);
        img.setScaleType(ImageView.ScaleType.FIT_XY);
        root.addView(img, new FrameLayout.LayoutParams(-1, -1));
        cv = new CircleView(this);
        root.addView(cv, new FrameLayout.LayoutParams(-1, -1));

        TextView hint = new TextView(this);
        hint.setText(L.t("用手指圈出想問的地方"));
        hint.setTextColor(Color.WHITE);
        hint.setTextSize(15);
        hint.setPadding((int) (16 * d), (int) (10 * d), (int) (16 * d), (int) (10 * d));
        hint.setBackground(round(0xB0202428, 24));
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        hlp.topMargin = (int) (56 * d);
        root.addView(hint, hlp);
        cv.hint = hint;

        TextView close = new TextView(this);
        close.setText("✕");
        close.setTextColor(Color.WHITE);
        close.setTextSize(20);
        close.setGravity(Gravity.CENTER);
        close.setBackground(round(0xB0202428, 22));
        close.setOnClickListener(v -> finish());
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams((int) (44 * d), (int) (44 * d), Gravity.TOP | Gravity.END);
        clp.topMargin = (int) (48 * d);
        clp.rightMargin = (int) (16 * d);
        root.addView(close, clp);

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(round(0xEE202428, 28));
        panel.setPadding((int) (14 * d), (int) (12 * d), (int) (14 * d), (int) (14 * d));
        panel.setVisibility(View.GONE);

        replyScroll = new ScrollView(this);
        reply = new TextView(this);
        reply.setTextColor(Color.WHITE);
        reply.setTextSize(16);
        reply.setTextIsSelectable(true);
        reply.setPadding(0, 0, 0, (int) (8 * d));
        replyScroll.addView(reply);
        replyScroll.setVisibility(View.GONE);
        panel.addView(replyScroll, new LinearLayout.LayoutParams(-1, (int) (260 * d)));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        thumb = new ImageView(this);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.setBackground(round(0x33FFFFFF, 12));
        thumb.setClipToOutline(true);
        thumb.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        row.addView(thumb, new LinearLayout.LayoutParams((int) (56 * d), (int) (56 * d)));
        input = new EditText(this);
        input.setHint(L.t("問這個…（可以空白）"));
        input.setHintTextColor(0xA0FFFFFF);
        input.setTextColor(Color.WHITE);
        input.setTextSize(16);
        input.setSingleLine(true);
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEND);
        input.setOnEditorActionListener((v, id, ev) -> { if (id == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) { submit(); return true; } return false; });
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, -2, 1f);
        ilp.leftMargin = (int) (10 * d);
        row.addView(input, ilp);
        sendBtn = new TextView(this);
        sendBtn.setText(L.t("送出"));
        sendBtn.setTextColor(Color.WHITE);
        sendBtn.setTextSize(15);
        sendBtn.setGravity(Gravity.CENTER);
        sendBtn.setBackground(round(OverlayService.CHIP_COLOR, 22));
        sendBtn.setPadding((int) (16 * d), (int) (10 * d), (int) (16 * d), (int) (10 * d));
        sendBtn.setOnClickListener(v -> submit());
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
        slp.leftMargin = (int) (8 * d);
        row.addView(sendBtn, slp);
        panel.addView(row, new LinearLayout.LayoutParams(-1, -2));

        TextView redo = new TextView(this);
        redo.setText(L.t("重新圈選"));
        redo.setTextColor(0xFF8AB4F8);
        redo.setTextSize(14);
        redo.setPadding(0, (int) (10 * d), 0, 0);
        redo.setOnClickListener(v -> resetSelection());
        panel.addView(redo, new LinearLayout.LayoutParams(-2, -2));

        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        plp.leftMargin = plp.rightMargin = (int) (12 * d);
        plp.bottomMargin = (int) (16 * d);
        root.addView(panel, plp);
        setContentView(root);
        android.view.WindowInsetsController wc = root.getWindowInsetsController();   // 要在 setContentView 之後才拿得到（之前是 null，會當機）
        if (wc != null) wc.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
        root.setOnApplyWindowInsetsListener((v, ins) -> {   // 鍵盤出現時，面板推到鍵盤上面
            int ime = ins.getInsets(WindowInsets.Type.ime()).bottom;
            plp.bottomMargin = (int) (16 * d) + ime;
            panel.setLayoutParams(plp);
            return ins;
        });
    }

    private GradientDrawable round(int color, int dp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp * d);
        return g;
    }

    /** 手指圈完了：記下範圍，顯示提問面板。 */
    private void onSelected(RectF r) {
        box = r;
        float sx = bmp.getWidth() / (float) cv.getWidth(), sy = bmp.getHeight() / (float) cv.getHeight();
        Bitmap crop = cropOf(r, sx, sy);
        thumb.setImageBitmap(crop);
        panel.setVisibility(View.VISIBLE);
        input.requestFocus();
    }

    private Bitmap cropOf(RectF r, float sx, float sy) {
        int pad = (int) (12 * d);
        int l = Math.max(0, (int) (r.left * sx) - pad), t = Math.max(0, (int) (r.top * sy) - pad);
        int rr = Math.min(bmp.getWidth(), (int) (r.right * sx) + pad), bb = Math.min(bmp.getHeight(), (int) (r.bottom * sy) + pad);
        if (rr - l < 8 || bb - t < 8) return bmp;
        return Bitmap.createBitmap(bmp, l, t, rr - l, bb - t);
    }

    private void resetSelection() {
        if (busy) return;
        cv.clear();
        box = null;
        while (msgs.length() > 0) msgs.remove(0);
        replyScroll.setVisibility(View.GONE);
        panel.setVisibility(View.GONE);
        cv.showHint();
    }

    private static String jpeg64(Bitmap b, int maxSide, int quality) {
        float s = Math.min(1f, maxSide / (float) Math.max(b.getWidth(), b.getHeight()));
        Bitmap x = s < 1f ? Bitmap.createScaledBitmap(b, Math.max(1, Math.round(b.getWidth() * s)), Math.max(1, Math.round(b.getHeight() * s)), true) : b;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        x.compress(Bitmap.CompressFormat.JPEG, quality, out);
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }

    private void submit() {
        if (busy || box == null) return;
        String q = input.getText().toString().trim();
        busy = true;
        input.setText("");
        sendBtn.setAlpha(0.5f);
        replyScroll.setVisibility(View.VISIBLE);
        reply.setText(L.t("思考中…"));
        try {
            JSONArray content = new JSONArray();
            if (msgs.length() == 0) {
                float sx = bmp.getWidth() / (float) cv.getWidth(), sy = bmp.getHeight() / (float) cv.getHeight();
                content.put(img(jpeg64(cropOf(box, sx, sy), 1280, 85)));
                content.put(img(jpeg64(bmp, 800, 70)));
                content.put(new JSONObject().put("type", "text").put("text", (L.en()
                        ? "The first image is the region the user circled; the second is the whole screen for context. The user's question: "
                        : "第一張圖是使用者圈選的區域，第二張是整個畫面（給你看上下文）。使用者的問題：")
                        + (q.isEmpty() ? (L.en() ? "What is this? Explain briefly and add useful related information." : "這是什麼？請簡短說明，並補充有用的相關資訊。") : q)));
            } else {
                content.put(new JSONObject().put("type", "text").put("text", q.isEmpty() ? (L.en() ? "Tell me more." : "再多說一點。") : q));
            }
            msgs.put(new JSONObject().put("role", "user").put("content", content));
        } catch (Exception e) {
            busy = false;
            return;
        }
        final Context app = getApplicationContext();
        new Thread(() -> {
            String r = ChatEngine.visionChat(app, msgs);
            runOnUiThread(() -> {
                busy = false;
                sendBtn.setAlpha(1f);
                if (r == null) {
                    reply.setText(L.t("連不上 AI：") + ChatEngine.lastError);
                    if (msgs.length() > 0) msgs.remove(msgs.length() - 1);   // 失敗的這一輪不留，重送才不會疊兩次
                    return;
                }
                try { msgs.put(new JSONObject().put("role", "assistant").put("content", r)); } catch (Exception ignored) { }
                reply.setText(Md.render(r));
                replyScroll.post(() -> replyScroll.fullScroll(View.FOCUS_UP));
            });
        }, "circle-ask").start();
    }

    private static JSONObject img(String b64) throws Exception {
        return new JSONObject().put("type", "image").put("source", new JSONObject().put("type", "base64").put("media_type", "image/jpeg").put("data", b64));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        bmp = null;
    }

    /** 全螢幕的畫圈層：手指畫出的軌跡（白色發光線），放開後把範圍外變暗。 */
    private final class CircleView extends View {
        private final Path path = new Path();
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG), glow = new Paint(Paint.ANTI_ALIAS_FLAG), dim = new Paint();
        private float minX, minY, maxX, maxY;
        private boolean has, done;
        TextView hint;

        CircleView(Context c) {
            super(c);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(4 * d);
            line.setColor(Color.WHITE);
            line.setStrokeCap(Paint.Cap.ROUND);
            line.setStrokeJoin(Paint.Join.ROUND);
            glow.set(line);
            glow.setStrokeWidth(12 * d);
            glow.setColor(0x558AB4F8);
            dim.setColor(0x99000000);
        }

        void clear() {
            path.reset();
            has = done = false;
            invalidate();
        }

        void showHint() { if (hint != null) hint.setVisibility(View.VISIBLE); }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (done) return true;
            float x = e.getX(), y = e.getY();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    path.reset();
                    path.moveTo(x, y);
                    minX = maxX = x;
                    minY = maxY = y;
                    has = true;
                    if (hint != null) hint.setVisibility(View.GONE);
                    break;
                case MotionEvent.ACTION_MOVE:
                    path.lineTo(x, y);
                    minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                    break;
                case MotionEvent.ACTION_UP:
                    if (maxX - minX < 40 * d && maxY - minY < 40 * d) { clear(); if (hint != null) hint.setVisibility(View.VISIBLE); return true; }   // 太小（只是點一下）：不算圈選
                    path.close();
                    done = true;
                    onSelected(new RectF(minX, minY, maxX, maxY));
                    break;
                default: break;
            }
            invalidate();
            return true;
        }

        @Override
        protected void onDraw(Canvas c) {
            if (!has) return;
            if (done) {   // 圈外變暗：只留圈選範圍亮著
                c.save();
                c.clipOutPath(path);
                c.drawRect(0, 0, getWidth(), getHeight(), dim);
                c.restore();
            }
            c.drawPath(path, glow);
            c.drawPath(path, line);
        }
    }
}
