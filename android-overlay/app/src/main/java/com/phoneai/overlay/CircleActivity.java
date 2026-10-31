package com.phoneai.overlay;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Base64;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;

/**
 * 圈選提問：助理面板按「◎」→ 面板收起、截下目前畫面 → 這裡顯示凍結的畫面，用手指圈出想問的地方
 * （雙指可以放大縮小、移動畫面，比較好圈；要重圈就直接再圈一次）→ 把圈起來的區域（加一張縮小的整個畫面當上下文）
 * 連同問題交給聊天模型回答，可以接著追問。介面跟助理面板同一種風格（深色玻璃輸入列、送出鈕、回答卡片）。
 * 截圖用無障礙或 root／Shizuku；沒有權限或畫面是受保護的（銀行 App 會是黑的）就截不到。
 */
public class CircleActivity extends Activity {
    private static volatile Bitmap shot;

    /**
     * 圈選的轉場：按下 ◎ 的瞬間把面板「畫面」藏起來（改成不可見，下一個畫面就生效，不等收起動畫），
     * 馬上截下底下的 App，再打開圈選畫面（沒有轉場動畫：畫面跟底下一模一樣，看起來就是畫面凍結），
     * 圈選畫面蓋好之後才把面板真的關掉。這樣中間不會有「面板收起、什麼都沒有」的空窗。截不到就把面板還原。
     */
    static void begin(final Activity act, final Runnable hideInstant, final Runnable showAgain, final Runnable closeSheet) {
        final Context app = act.getApplicationContext();
        final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
        try { hideInstant.run(); } catch (Throwable t) { toast(app, t); return; }
        new Thread(() -> {
            try {
                try { Thread.sleep(80); } catch (InterruptedException ignored) { }   // 讓「藏起來」那一幀真的畫出去再截
                Bitmap b = AgentLoop.screenshotBitmap();
                if (b == null) {
                    ui.post(showAgain);
                    toast(app, L.t("截圖失敗（需要開啟無障礙服務，或使用 root／Shizuku 模式）"));
                    return;
                }
                shot = b;
                app.startActivity(new Intent(app, CircleActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS));
                ui.postDelayed(closeSheet, 450);   // 圈選畫面蓋好之後再關面板
            } catch (Throwable t) {   // 背景執行緒的例外會讓整個 App 當掉：一律攔下來顯示
                ui.post(showAgain);
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
    private LinearLayout bottom;
    private View scrim;
    private FrameLayout replyHolder;
    private ImageView thumb;
    private EditText input;
    private SendIconView send;
    private TextView hint;
    private RectF box;                       // 圈選範圍（圖片座標）
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

    private GradientDrawable glass(int dp) {   // 跟助理面板輸入列一樣的半透明深色玻璃
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x99121218);
        g.setStroke((int) Math.max(1, d), 0x50FFFFFF);
        g.setCornerRadius(dp * d);
        return g;
    }

    private void build() {
        d = getResources().getDisplayMetrics().density;
        getWindow().setDecorFitsSystemWindows(false);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.BLACK));

        FrameLayout root = new FrameLayout(this);
        cv = new CircleView(this);
        root.addView(cv, new FrameLayout.LayoutParams(-1, -1));
        final View flash = new View(this);   // 截圖的閃光：一瞬間亮一下再淡掉，告訴使用者「畫面凍結了」
        flash.setBackgroundColor(Color.WHITE);
        flash.setAlpha(0.35f);
        flash.setClickable(false);
        root.addView(flash, new FrameLayout.LayoutParams(-1, -1));
        flash.animate().alpha(0f).setDuration(320).withEndAction(() -> flash.setVisibility(View.GONE)).start();

        scrim = new View(this);   // 提問時底部的暗幕，讓白字讀得到
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, 0xCC000000}));
        scrim.setVisibility(View.GONE);
        root.addView(scrim, new FrameLayout.LayoutParams(-1, (int) (360 * d), Gravity.BOTTOM));

        hint = new TextView(this);
        hint.setText(L.t("用手指圈出想問的地方（雙指可以放大縮小）"));
        hint.setTextColor(Color.WHITE);
        hint.setTextSize(15);
        hint.setPadding((int) (18 * d), (int) (10 * d), (int) (18 * d), (int) (10 * d));
        hint.setBackground(glass(24));
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        hlp.topMargin = (int) (56 * d);
        root.addView(hint, hlp);
        hint.setAlpha(0f);
        hint.setTranslationY(-12 * d);
        hint.animate().alpha(1f).translationY(0f).setStartDelay(150).setDuration(350).start();

        TextView close = new TextView(this);
        close.setText("✕");
        close.setTextColor(Color.WHITE);
        close.setTextSize(20);
        close.setGravity(Gravity.CENTER);
        GradientDrawable cg = glass(22);
        cg.setShape(GradientDrawable.OVAL);
        close.setBackground(cg);
        close.setOnClickListener(v -> finish());
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams((int) (44 * d), (int) (44 * d), Gravity.TOP | Gravity.END);
        clp.topMargin = (int) (48 * d);
        clp.rightMargin = (int) (16 * d);
        root.addView(close, clp);

        bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setVisibility(View.GONE);
        replyHolder = new FrameLayout(this);
        replyHolder.setVisibility(View.GONE);
        LinearLayout.LayoutParams rhp = new LinearLayout.LayoutParams(-1, -2);
        rhp.bottomMargin = (int) (10 * d);
        bottom.addView(replyHolder, rhp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        thumb = new ImageView(this);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable tg = glass(26);
        tg.setShape(GradientDrawable.OVAL);
        thumb.setBackground(tg);
        thumb.setClipToOutline(true);
        thumb.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams((int) (52 * d), (int) (52 * d));
        tlp.rightMargin = (int) (4 * d);
        row.addView(thumb, tlp);
        input = new EditText(this);
        input.setHint(L.t("問這個…（可以空白）"));
        input.setHintTextColor(0xA0FFFFFF);
        input.setTextColor(Color.WHITE);
        input.setTextSize(17);
        input.setBackground(glass(40));
        input.setPadding((int) (20 * d), (int) (14 * d), (int) (20 * d), (int) (14 * d));
        input.setSingleLine(true);
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEND);
        input.setOnEditorActionListener((v, id, ev) -> { if (id == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) { submit(); return true; } return false; });
        row.addView(input, new LinearLayout.LayoutParams(0, -2, 1f));
        send = new SendIconView(this);
        send.setOnClickListener(v -> submit());
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams((int) (52 * d), (int) (52 * d));
        slp.leftMargin = (int) (4 * d);
        row.addView(send, slp);
        bottom.addView(row, new LinearLayout.LayoutParams(-1, -2));

        final FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        plp.leftMargin = (int) (14 * d);
        plp.rightMargin = (int) (14 * d);
        plp.bottomMargin = (int) (18 * d);
        root.addView(bottom, plp);
        setContentView(root);
        android.view.WindowInsetsController wc = root.getWindowInsetsController();   // 要在 setContentView 之後才拿得到（之前是 null，會當機）
        if (wc != null) wc.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
        root.setOnApplyWindowInsetsListener((v, ins) -> {   // 鍵盤出現時，輸入列推到鍵盤上面
            plp.bottomMargin = (int) (18 * d) + ins.getInsets(WindowInsets.Type.ime()).bottom;
            bottom.setLayoutParams(plp);
            return ins;
        });
    }

    /** 手指圈完了：記下範圍（圖片座標），顯示提問列。 */
    private void onSelected(RectF r) {
        box = r;
        thumb.setImageBitmap(cropOf(r));
        scrim.setVisibility(View.VISIBLE);
        bottom.setVisibility(View.VISIBLE);
        input.requestFocus();
    }

    /** 開始畫新的一圈（或圈選被清掉）：提問列收起，這個區域的對話重來。 */
    private void onCleared() {
        if (busy) return;
        box = null;
        while (msgs.length() > 0) msgs.remove(0);
        replyHolder.removeAllViews();
        replyHolder.setVisibility(View.GONE);
        bottom.setVisibility(View.GONE);
        scrim.setVisibility(View.GONE);
        hint.setVisibility(View.GONE);
        android.view.inputmethod.InputMethodManager imm = getSystemService(android.view.inputmethod.InputMethodManager.class);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
    }

    private Bitmap cropOf(RectF r) {
        int pad = (int) (12 * d);
        int l = Math.max(0, (int) r.left - pad), t = Math.max(0, (int) r.top - pad);
        int rr = Math.min(bmp.getWidth(), (int) r.right + pad), bb = Math.min(bmp.getHeight(), (int) r.bottom + pad);
        if (rr - l < 8 || bb - t < 8) return bmp;
        return Bitmap.createBitmap(bmp, l, t, rr - l, bb - t);
    }

    private static String jpeg64(Bitmap b, int maxSide, int quality) {
        float s = Math.min(1f, maxSide / (float) Math.max(b.getWidth(), b.getHeight()));
        Bitmap x = s < 1f ? Bitmap.createScaledBitmap(b, Math.max(1, Math.round(b.getWidth() * s)), Math.max(1, Math.round(b.getHeight() * s)), true) : b;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        x.compress(Bitmap.CompressFormat.JPEG, quality, out);
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }

    private void showReply(String text) {   // 回答卡片：跟助理面板同一張卡片
        replyHolder.removeAllViews();
        int maxH = (int) (getResources().getDisplayMetrics().heightPixels * 0.42f);
        FrameLayout card = ReplyCard.build(this, "", text, () -> { }, maxH);
        replyHolder.addView(card, new FrameLayout.LayoutParams(-1, -2));
        replyHolder.setVisibility(View.VISIBLE);
    }

    private void submit() {
        if (busy || box == null) return;
        String q = input.getText().toString().trim();
        busy = true;
        input.setText("");
        send.setAlpha(0.35f);
        input.setHint(L.t("思考中…"));
        try {
            JSONArray content = new JSONArray();
            if (msgs.length() == 0) {
                content.put(img(jpeg64(cropOf(box), 1280, 85)));
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
            send.setAlpha(1f);
            return;
        }
        final Context app = getApplicationContext();
        final boolean wantSearch = ChatEngine.wantsSearch(q);
        final String qf = q;
        new Thread(() -> {
            boolean tavily = false;
            if (wantSearch) {   // 你明說「幫我查」：先用 Tavily 搜尋（沒設 Key 就用免 Key 模式），把結果接在問題後面（標成資料，不是指令）
                String digest = TavilySearch.search(app, qf);
                if (digest != null) {
                    try {
                        JSONArray last = msgs.getJSONObject(msgs.length() - 1).getJSONArray("content");
                        JSONObject tb = last.getJSONObject(last.length() - 1);
                        tb.put("text", tb.optString("text") + TavilySearch.block(digest));
                        tavily = true;
                    } catch (Exception ignored) { }
                }
            }
            final boolean usedTavily = tavily;
            String r = ChatEngine.visionChat(app, msgs, !tavily);
            runOnUiThread(() -> {
                busy = false;
                send.setAlpha(1f);
                input.setHint(L.t("繼續問…"));
                if (r == null) {
                    showReply(L.t("連不上 AI：") + ChatEngine.lastError);
                    if (msgs.length() > 0) msgs.remove(msgs.length() - 1);   // 失敗的這一輪不留，重送才不會疊兩次
                    return;
                }
                try { msgs.put(new JSONObject().put("role", "assistant").put("content", r)); } catch (Exception ignored) { }
                showReply(usedTavily ? r + TavilySearch.sourcesBlock() : r);
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

    /**
     * 整個畫面：自己畫截圖（可以雙指放大縮小、移動），單指畫圈。軌跡存成圖片座標，放大縮小後圈選位置不會跑掉；
     * 放開後圈外變暗，只留圈選範圍亮著。
     */
    private final class CircleView extends View {
        private static final float MAX_ZOOM = 6f;
        private final Path path = new Path();                  // 圖片座標
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG), glow = new Paint(Paint.ANTI_ALIAS_FLAG), core = new Paint(Paint.ANTI_ALIAS_FLAG),
                dim = new Paint(), pic = new Paint(Paint.FILTER_BITMAP_FLAG), dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix m = new Matrix(), gm = new Matrix();
        private LinearGradient gradient;                       // 流動的光：藍、紫、粉、青（跟助理面板的極光同一組色）
        private float gradLen = 1f;
        private float s0 = 1f, zoom = 1f, tx, ty;               // s0 = 剛好填滿的比例；zoom = 使用者放大倍數；tx、ty = 平移（畫面像素）
        private float minX, minY, maxX, maxY, downX, downY, headX, headY;
        private boolean has, done, drawing, zooming;
        private long doneAt;
        private float lastDist, lastMx, lastMy;

        CircleView(Context c) {
            super(c);
            for (Paint p : new Paint[]{line, glow, core}) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStrokeJoin(Paint.Join.ROUND);
            }
            core.setColor(0xFFFFFFFF);
            dim.setColor(0xFF000000);
            dot.setColor(0xFFFFFFFF);
        }

        private float scale() { return s0 * zoom; }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            s0 = Math.min(w / (float) bmp.getWidth(), h / (float) bmp.getHeight());
            zoom = 1f;
            tx = (w - bmp.getWidth() * s0) / 2f;
            ty = (h - bmp.getHeight() * s0) / 2f;
            gradLen = Math.max(1f, bmp.getWidth() * 0.7f);
            gradient = new LinearGradient(0, 0, gradLen, gradLen * 0.4f,
                    new int[]{0xFF4285F4, 0xFFA142F4, 0xFFFF6BB5, 0xFF2DE2E6, 0xFF4285F4}, null, Shader.TileMode.REPEAT);
        }

        private void clamp() {   // 放大後畫面邊緣不要拉出空白
            float S = scale(), iw = bmp.getWidth() * S, ih = bmp.getHeight() * S;
            tx = iw <= getWidth() ? (getWidth() - iw) / 2f : Math.max(getWidth() - iw, Math.min(0, tx));
            ty = ih <= getHeight() ? (getHeight() - ih) / 2f : Math.max(getHeight() - ih, Math.min(0, ty));
        }

        private float bx(float x) { return (x - tx) / scale(); }
        private float by(float y) { return (y - ty) / scale(); }

        void clearSel() {
            path.reset();
            has = done = false;
            invalidate();
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getX();
                    downY = e.getY();
                    drawing = false;
                    zooming = false;
                    return true;
                case MotionEvent.ACTION_POINTER_DOWN:   // 第二根手指下來：改成縮放／移動，正在畫的這一圈不算
                    if (drawing && !done) { path.reset(); has = false; drawing = false; }
                    zooming = true;
                    lastDist = dist(e);
                    lastMx = midX(e);
                    lastMy = midY(e);
                    invalidate();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (zooming) {
                        if (e.getPointerCount() >= 2) {
                            float nd = dist(e), mx = midX(e), my = midY(e);
                            if (lastDist > 0) {
                                float nz = Math.max(1f, Math.min(MAX_ZOOM, zoom * nd / lastDist));
                                float r = nz / zoom;
                                tx = mx - (lastMx - tx) * r;
                                ty = my - (lastMy - ty) * r;
                                zoom = nz;
                            }
                            clamp();
                            lastDist = nd;
                            lastMx = mx;
                            lastMy = my;
                            invalidate();
                        }
                        return true;
                    }
                    if (!drawing) {
                        if (Math.hypot(e.getX() - downX, e.getY() - downY) < 8 * d) return true;   // 還沒真的在畫（只是點到）
                        drawing = true;                       // 開始畫新的一圈：舊的圈選與提問列收掉
                        clearSel();
                        onCleared();
                        path.reset();
                        path.moveTo(bx(downX), by(downY));
                        minX = maxX = bx(downX);
                        minY = maxY = by(downY);
                        has = true;
                    }
                    float px = bx(e.getX()), py = by(e.getY());
                    headX = px;
                    headY = py;
                    path.lineTo(px, py);
                    minX = Math.min(minX, px); maxX = Math.max(maxX, px);
                    minY = Math.min(minY, py); maxY = Math.max(maxY, py);
                    invalidate();
                    return true;
                case MotionEvent.ACTION_POINTER_UP:
                    lastDist = 0;   // 放掉一根手指：剩下的手指不要接著畫，等全部放開
                    lastMx = e.getX(e.getPointerCount() > 1 && e.getActionIndex() == 0 ? 1 : 0);
                    lastMy = e.getY(e.getPointerCount() > 1 && e.getActionIndex() == 0 ? 1 : 0);
                    return true;
                case MotionEvent.ACTION_UP:
                    if (drawing && has && !zooming) {
                        float S = scale();
                        if ((maxX - minX) * S < 40 * d && (maxY - minY) * S < 40 * d) {   // 太小：不算圈選
                            clearSel();
                            hint.setVisibility(View.VISIBLE);
                        } else {
                            path.close();
                            done = true;
                            doneAt = android.os.SystemClock.uptimeMillis();
                            onSelected(new RectF(minX, minY, maxX, maxY));
                        }
                    }
                    drawing = false;
                    zooming = false;
                    invalidate();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    drawing = false;
                    zooming = false;
                    return true;
                default:
                    return true;
            }
        }

        private float dist(MotionEvent e) { return (float) Math.hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1)); }
        private float midX(MotionEvent e) { return (e.getX(0) + e.getX(1)) / 2f; }
        private float midY(MotionEvent e) { return (e.getY(0) + e.getY(1)) / 2f; }

        @Override
        protected void onDraw(Canvas c) {
            float S = scale();
            m.setScale(S, S);
            m.postTranslate(tx, ty);
            long now = android.os.SystemClock.uptimeMillis();
            c.save();
            c.concat(m);
            c.drawBitmap(bmp, 0, 0, pic);
            if (has) {
                float phase = (now % 2600L) / 2600f;
                float since = done ? now - doneAt : 0f;
                if (done) {   // 圈外慢慢變暗：只留圈選範圍亮著
                    dim.setAlpha((int) (0x99 * Math.min(1f, since / 350f)));
                    c.save();
                    c.clipOutPath(path);
                    c.drawRect(0, 0, bmp.getWidth(), bmp.getHeight(), dim);
                    c.restore();
                }
                gm.setTranslate(phase * gradLen, 0);   // 光沿著線流動
                gradient.setLocalMatrix(gm);
                line.setShader(gradient);
                glow.setShader(gradient);
                // 剛圈完的那一瞬間線條變粗、光暈變亮，再回到平常；之後光暈跟著呼吸
                float burst = done ? 1f + 0.9f * Math.max(0f, 1f - since / 600f) : 1f;
                float breathe = done ? 0.7f + 0.3f * (float) Math.sin(phase * 2 * Math.PI) : 0.85f;
                float[] w = {26f, 17f, 10f}, al = {0.16f, 0.26f, 0.42f};
                for (int i = 0; i < w.length; i++) {   // 一層一層疊出柔和的光暈（不用模糊濾鏡）
                    glow.setStrokeWidth(w[i] * d * burst / S);
                    glow.setAlpha((int) (255 * al[i] * breathe * (done ? 1.2f : 1f)));
                    c.drawPath(path, glow);
                }
                line.setStrokeWidth(4.5f * d * burst / S);
                c.drawPath(path, line);
                core.setStrokeWidth(1.6f * d / S);
                core.setAlpha(0xCC);
                c.drawPath(path, core);
                if (drawing && !done) {   // 手指前面一顆發亮的光點
                    dot.setShader(null);
                    dot.setAlpha(0x55);
                    c.drawCircle(headX, headY, 16 * d / S, dot);
                    dot.setAlpha(0xFF);
                    c.drawCircle(headX, headY, 6 * d / S, dot);
                }
                postInvalidateOnAnimation();   // 有圈選時持續重畫，光才會流動
            }
            c.restore();
        }
    }
}
