package com.phoneai.overlay;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.WindowManager;

/**
 * 預覽卡片的觸控層：一個跟卡片同大小、完全透明的小視窗，只負責收觸控並改變卡片（PreviewView）的位置／大小／關閉動畫。
 * 卡片在另一個全螢幕、不攔觸控的視窗裡，用 translation／scale 動，所以動畫全是 GPU 合成。
 * 重點：手指按下之後，後續觸控一定會送到這個視窗，所以拖曳／上滑「過程中」不需要搬動它（搬視窗是跨程序呼叫，
 * 每個觸控事件都做會卡住主執行緒、掉幀）；只在放手、動畫結束後對齊一次。
 *
 * 手勢：
 *  - 右上角控制區：點「接手／完成」切換接手模式；接手中左邊的「輸入」開文字輸入框。
 *  - 卡片本身整個是「主螢幕」：碰它就是操作背景螢幕，點、長按、滑動會依比例換算成背景螢幕座標，用 `input -d` 送過去（一個手勢一個指令）。
 *    沒接手的話，第一次碰會自動進入接手（AI 在下一步開始前看到就暫停）；右上角的「輸入」「完成」只在接手中出現，按「完成」AI 才繼續。
 *  - 卡片下面那一列（視窗比卡片多出 HANDLE_H_DP 那一條，把手區）才是視窗操作：中間的小白條拖曳 = 移動視窗，明顯往上滑 = 關閉
 *    （彈簧物理，帶手指的速度；縮放與圓角跟進度連動；過門檻震一下）；最右邊 44dp 按住拖曳 = 改變大小（維持比例，放手記住）。
 *    拖動中小白條先藏起來，放手對齊後再出現。
 */
final class PreviewTouch extends View {
    // 手感參數（質量 1 的彈簧：剛度 k，阻尼比 ζ；ζ=1 臨界阻尼不過衝，<1 會過衝）
    private static final float FOLLOW_K = 700f, FOLLOW_Z = 0.85f;      // 跟手：略有彈性的黏著
    private static final float DISMISS_K = 260f, DISMISS_Z = 1.0f;      // 放手關閉：飛出
    private static final float BACK_K = 300f, BACK_Z = 0.62f;           // 沒滑夠：回彈（明顯一點）
    private static final float GLIDE_S = 0.10f;                         // 放手後的慣性：依速度再滑 0.1 秒的距離

    /** 卡片正下方的把手區高度（dp）：觸控視窗比卡片高這麼多；卡片本身整個都留給背景螢幕操作。 */
    static final float HANDLE_H_DP = 26f;

    private final PreviewView card;
    private final WindowManager wm;
    private final WindowManager.LayoutParams lp;  // 本透明視窗（螢幕座標 = 卡片左上角）
    private final Runnable onClose;
    private final float d;
    private final int screenW, screenH, minW, maxW;

    private float startRawX, startRawY, startW, baseX, baseY, targetX, targetY;
    private boolean resizing, dismissing, past, touching, controlling;
    private boolean edge;               // 這次觸控從卡片左／右邊緣開始：往內滑 = 返回（背景螢幕沒有系統手勢列，要自己做）
    private float edgeStartX;
    private boolean live, kbd;        // live：這次觸控正在即時注入（LiveInput）；kbd：按在小白條列最左邊的鍵盤圖示
    private long lastMove;
    private final Runnable idleEnd = this::endTakeoverIfIdle;
    private boolean handle, handleUndecided, pillOn = true;   // handle：這次觸控是按在卡片下面的小白條上；pillOn：小白條要不要畫（拖動中先藏起來）
    private final android.graphics.Paint pillPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    private int ctrlHit;                  // 這次觸控落在右上角控制區的哪個鈕（0 = 沒有）
    private float downX, downY;           // 接手手勢的起點（觸控層座標 = 卡片座標）
    private VelocityTracker vt;
    private final Spring springX = new Spring(), springY = new Spring();
    private int corrX, corrY;  // 觸控層與卡片在螢幕上的實際偏差（量出來後補償）
    private boolean xDone = true, yDone = true;

    PreviewTouch(Context c, PreviewView card, WindowManager wm, WindowManager.LayoutParams lp, Runnable onClose) {
        super(c);
        this.card = card;
        this.wm = wm;
        this.lp = lp;
        this.onClose = onClose;
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        this.d = dm.density;
        this.screenW = dm.widthPixels;
        this.screenH = dm.heightPixels;
        this.minW = (int) (dm.widthPixels * 0.22f);
        this.maxW = (int) (dm.widthPixels * 0.85f);
    }

    /** 卡片下面的把手區（y 超過卡片高度）：只有這裡拖動才是移動／上滑關閉視窗。 */
    private boolean inHandleZone(MotionEvent e) { return e.getY() > card.cardHeight(); }

    /** 畫卡片正下方那一列：中間一條細細的小白條（跟系統手勢條同風格：抓它拖動視窗、往上滑關閉），最右邊兩條斜線（抓它拖曳縮放）。 */
    @Override
    protected void onDraw(android.graphics.Canvas c) {
        drawBackHint(c);
        if (!pillOn) return;
        float cx = getWidth() / 2f, cy = card.cardHeight() + (HANDLE_H_DP * 0.45f) * d;
        pillPaint.setColor(OverlayService.takeover ? 0xB3FFFFFF : 0x88FFFFFF);
        pillPaint.setStyle(android.graphics.Paint.Style.FILL);
        c.drawRoundRect(cx - 18 * d, cy - 1.5f * d, cx + 18 * d, cy + 1.5f * d, 2 * d, 2 * d, pillPaint);
        pillPaint.setStyle(android.graphics.Paint.Style.STROKE);
        pillPaint.setStrokeWidth(Math.max(1, 1.2f * d));
        float kx = 10 * d;   // 最左邊：小鍵盤圖示（點它開輸入框）
        c.drawRoundRect(kx, cy - 4 * d, kx + 14 * d, cy + 4 * d, 2 * d, 2 * d, pillPaint);
        pillPaint.setStyle(android.graphics.Paint.Style.FILL);
        for (int i = 0; i < 3; i++) c.drawCircle(kx + (3.5f + i * 3.5f) * d, cy - 1.2f * d, 0.7f * d, pillPaint);
        c.drawRect(kx + 4 * d, cy + 0.8f * d, kx + 10 * d, cy + 1.8f * d, pillPaint);
        pillPaint.setStyle(android.graphics.Paint.Style.STROKE);
        pillPaint.setStrokeWidth(Math.max(2, 1.6f * d));
        pillPaint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
        float rx = getWidth() - 8 * d;
        c.drawLine(rx, cy - 5 * d, rx - 8 * d, cy + 3 * d, pillPaint);
        c.drawLine(rx, cy - 1 * d, rx - 4 * d, cy + 3 * d, pillPaint);
    }

    // 邊緣返回的提示（仿三星側滑返回）：跟著手指從邊緣滑出來、由淡變濃；形狀從圓角正方形隨距離變圓，
    // 到觸發位置剛好是正圓並震一下；放手快速淡出。不變色。
    private boolean hintOn, hintCommit;
    private float hintDx, hintY, hintAlpha = 1f;
    private boolean hintLeft;
    private android.animation.ValueAnimator hintFade;

    private void drawBackHint(android.graphics.Canvas c) {
        if (!hintOn) return;
        float full = 40 * d;
        float p = Math.max(0f, Math.min(1f, hintDx / full));
        float appear = Math.max(0f, Math.min(1f, hintDx / (14 * d)));   // 剛開始滑幾 dp 內由淡變濃，不會突然冒出來
        float half = (10 + 6 * p) * d;
        float rad = half * (0.3f + 0.7f * p);                           // p=1 → 圓角 = 半邊長 = 正圓
        float off = (-6 + 36 * p) * d;                                  // 圓心離邊緣：從半藏在邊緣外慢慢滑進來
        float cx = hintLeft ? off : getWidth() - off;
        // 圖示放在手指上方（不被手指擋住）；手指太靠近卡片上緣放不下時，改放在手指下方
        float gap = 66 * d;
        float cy = hintY - gap;
        if (cy - half < 4 * d) cy = hintY + gap;
        cy = Math.max(half, Math.min(card.cardHeight() - half, cy));
        float al = appear * hintAlpha;
        pillPaint.setStyle(android.graphics.Paint.Style.FILL);
        pillPaint.setColor(((int) (0x99 * al) << 24) | 0x8E8E93);   // 半透明灰（像三星）
        c.drawRoundRect(cx - half, cy - half, cx + half, cy + half, rad, rad, pillPaint);
        pillPaint.setStyle(android.graphics.Paint.Style.STROKE);
        pillPaint.setColor(((int) (255 * al) << 24) | 0xFFFFFF);
        pillPaint.setStrokeWidth(1.9f * d);
        pillPaint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
        pillPaint.setStrokeJoin(android.graphics.Paint.Join.ROUND);
        float a = 4.6f * d, sgn = hintLeft ? 1f : -1f;                   // 左邊緣箭頭「<」、右邊緣「>」
        android.graphics.Path path = new android.graphics.Path();
        path.moveTo(cx + sgn * a * 0.4f, cy - a);
        path.lineTo(cx - sgn * a * 0.6f, cy);
        path.lineTo(cx + sgn * a * 0.4f, cy + a);
        c.drawPath(path, pillPaint);
    }

    private void hintFadeOut() {
        if (!hintOn) return;
        if (hintFade != null) hintFade.cancel();
        hintFade = android.animation.ValueAnimator.ofFloat(hintAlpha, 0f).setDuration(120);
        hintFade.addUpdateListener(a -> { hintAlpha = (float) a.getAnimatedValue(); invalidate(); });
        hintFade.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator an) { hintOn = false; hintAlpha = 1f; invalidate(); }
        });
        hintFade.start();
    }

    private void setPill(boolean on) {
        if (pillOn != on) { pillOn = on; invalidate(); }
    }

    /** 卡片左上角在螢幕上的實際座標（卡片層視窗本身的位置 + 卡片的位移）。 */
    private int[] cardOnScreen() {
        int[] o = new int[2];
        ((View) card.getParent()).getLocationOnScreen(o);
        o[0] += (int) card.getTranslationX();
        o[1] += (int) card.getTranslationY();
        return o;
    }

    /** 透明觸控視窗對齊卡片目前的位置與大小（只在放手/動畫結束後呼叫，不在拖曳過程中呼叫）。 */
    private void sync() {
        int[] c = cardOnScreen();
        lp.x = c[0] + corrX;
        lp.y = c[1] + corrY;
        lp.width = card.cardWidth();
        lp.height = card.cardHeight() + (int) (HANDLE_H_DP * d);
        try { wm.updateViewLayout(this, lp); } catch (Exception ignored) { }
        setPill(true);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        post(this::calibrate);
        postDelayed(this::calibrate, 300);  // 卡片層可能還沒排好版，晚一點再量一次
    }

    /** 量一次觸控層與卡片在螢幕上實際差多少，記下來補償（系統可能因為挖孔/狀態列把其中一個視窗推開）。 */
    private void calibrate() {
        if (getWindowToken() == null || touching) return;
        int[] want = cardOnScreen();
        int[] me = new int[2];
        getLocationOnScreen(me);
        int dx = want[0] - me[0], dy = want[1] - me[1];
        android.util.Log.i(OverlayService.TAG, "preview touch calibrate: card=" + want[0] + "," + want[1]
                + " touch=" + me[0] + "," + me[1] + " corr=" + (corrX + dx) + "," + (corrY + dy));
        android.util.Log.i(OverlayService.TAG, "preview touch size: card=" + card.cardWidth() + "x" + card.cardHeight()
                + " touch=" + getWidth() + "x" + getHeight());
        if (dx == 0 && dy == 0) return;
        corrX += dx;
        corrY += dy;
        sync();
    }

    private void buzz(boolean strong) {
        try {
            android.os.Vibrator v = getContext().getSystemService(android.os.Vibrator.class);
            if (v != null) v.vibrate(android.os.VibrationEffect.createPredefined(
                    strong ? android.os.VibrationEffect.EFFECT_CLICK : android.os.VibrationEffect.EFFECT_TICK));
        } catch (Exception ignored) { }
    }

    /** 上滑進度 0..1 → 卡片縮放與圓角（連續連動，不是階段式）。ty = 卡片目前的 translationY。 */
    private void applyProgress(float ty) {
        float dy = ty - baseY;
        float p = Math.max(0f, Math.min(1f, -dy / (card.cardHeight() * 0.6f)));
        float s = 1f - 0.18f * p;
        card.setScaleX(s);
        card.setScaleY(s);
        card.setRadius((PreviewView.BASE_RADIUS_DP + 14f * p) * d);
    }

    private float clampX(float x) { return Math.max(-card.cardWidth() * 0.6f, Math.min(screenW - card.cardWidth() * 0.4f, x)); }

    private float clampY(float y) { return Math.max(0f, Math.min(screenH - card.cardHeight() * 0.3f, y)); }

    /** 開始「跟手」彈簧：卡片追著 target 跑（不是 1:1 黏死，所以軟）。 */
    private void startFollow(boolean x, boolean y) {
        if (x) {
            xDone = false;
            springX.start(card.getTranslationX(), 0f, card.getTranslationX(), FOLLOW_K, FOLLOW_Z, 0.5f, (pos, done) -> {
                card.setTranslationX(pos);
                if (done) { xDone = true; settled(); }
            });
        }
        if (y) {
            yDone = false;
            springY.start(card.getTranslationY(), 0f, card.getTranslationY(), FOLLOW_K, FOLLOW_Z, 0.5f, (pos, done) -> {
                card.setTranslationY(pos);
                if (dismissing) applyProgress(pos);
                if (done) { yDone = true; settled(); }
            });
        }
    }

    /** 兩個軸都停下、而且手指已經離開，才對齊透明觸控層。 */
    // 鍵盤跳出時：預覽縮到不超過螢幕高度 36%、移到最上面；鍵盤收起再回原位（中途你自己拖過就不回去）
    private boolean imeMoved;
    private float imeSaveX, imeSaveY;
    private int imeSaveW;

    void imeChanged(boolean shown) {
        if (touching || dismissing) return;
        if (shown && !imeMoved) {
            imeSaveX = card.getTranslationX();
            imeSaveY = card.getTranslationY();
            imeSaveW = card.cardWidth();
            int w = Math.max(minW, Math.min(imeSaveW, (int) (screenH * 0.36f / card.aspect())));
            card.setWidthPx(w);
            float right = imeSaveX + imeSaveW;  // 保持靠同一邊：右緣不動
            float tx = Math.max(8 * d, Math.min(screenW - w - 8 * d, right - w));
            float top = 28 * d;
            android.view.WindowInsets in = getRootWindowInsets();
            if (in != null) top = Math.max(top, in.getInsets(android.view.WindowInsets.Type.statusBars()).top + 4 * d);
            imeMoved = true;
            moveTo(tx, top);
        } else if (!shown && imeMoved) {
            imeMoved = false;
            card.setWidthPx(imeSaveW);
            moveTo(imeSaveX, imeSaveY);
        }
    }

    private void moveTo(float tx, float ty) {
        xDone = yDone = false;
        springX.start(card.getTranslationX(), 0f, tx, BACK_K, 1.0f, 0.5f, (pos, done) -> {
            card.setTranslationX(pos);
            if (done) { xDone = true; settled(); }
        });
        springY.start(card.getTranslationY(), 0f, ty, BACK_K, 1.0f, 0.5f, (pos, done) -> {
            card.setTranslationY(pos);
            if (done) { yDone = true; settled(); }
        });
    }

    private void settled() {
        if (!touching && xDone && yDone && !dismissing) {
            sync();
            postDelayed(this::calibrate, 150);  // 視窗搬動後再量一次實際位置（系統可能把觸控窗推開，小白條就會錯位到卡片裡）
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                springX.cancel();
                springY.cancel();
                xDone = yDone = true;  // 沒用到的軸視為已停
                touching = true;
                startRawX = e.getRawX();
                startRawY = e.getRawY();
                ctrlHit = PreviewView.ctrlHit(e.getX(), e.getY(), getWidth(), OverlayService.takeover, d);
                // 卡片本身整個是「主螢幕」：碰它就是操作背景螢幕（沒接手的話自動進入接手，AI 先暫停；做完按「完成」AI 才繼續）。
                // 卡片下面那一列（把手區）才是視窗操作：中間小白條 = 拖動移動／往上滑關閉；最右邊 44dp = 拖曳縮放。
                boolean inStrip = ctrlHit == 0 && inHandleZone(e);
                resizing = inStrip && e.getX() > getWidth() - 44 * d;
                kbd = inStrip && e.getX() < 36 * d;
                handle = inStrip && !resizing && !kbd;
                removeCallbacks(idleEnd);
                handleUndecided = handle;
                dismissing = false;
                controlling = ctrlHit == 0 && !inStrip;
                if (controlling && !OverlayService.takeover) {  // 碰到畫面：自動接手（AI 下一步開始前會看到並暫停）
                    OverlayService.applyTakeover(true, "");
                    RippleAccessibilityService.takeoverChanged();
                }
                if (inStrip && !kbd) { setPill(false); imeMoved = false; }  // 你自己動視窗：鍵盤收起時不再自動移回去  // 拖動／縮放視窗時先把小白條藏起來（放手對齊後再出現）
                downX = e.getX();
                downY = e.getY();
                live = false;
                edge = controlling && (downX < EDGE_DP * d || downX > getWidth() - EDGE_DP * d);
                edgeStartX = downX;
                if (controlling) {
                    card.pulse(downX, downY);
                    if (edge) {
                        // 邊緣開始的觸控先不送給背景 App：放手時看是「往內滑 = 返回」還是「點一下」
                    } else if (DirectHost.ready(OverlayService.vdId) || LiveInput.ready()) {
                        live = true;
                        lastMove = 0;
                        liveSend(MotionEvent.ACTION_DOWN, e.getX(), e.getY());
                    }
                }
                startW = card.cardWidth();
                baseX = card.getTranslationX();
                baseY = card.getTranslationY();
                targetX = baseX;
                targetY = baseY;
                past = false;
                if (vt != null) vt.recycle();
                vt = VelocityTracker.obtain();
                vt.addMovement(e);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (vt != null) vt.addMovement(e);
                if (handle && handleUndecided) {
                    // 小白條：先看手指往哪個方向 —— 明顯往上 = 上滑關閉（只跟 Y、縮放連動）；其他方向 = 移動視窗
                    float mx = e.getRawX() - startRawX, my = e.getRawY() - startRawY;
                    if (Math.hypot(mx, my) > 10 * d) {
                        handleUndecided = false;
                        if (my < 0 && -my > 1.2f * Math.abs(mx)) { dismissing = true; startFollow(false, true); }
                        else startFollow(true, true);
                    }
                } else if (controlling) {
                    if (edge) {  // 等放手再決定；先畫提示
                        if (!hintOn || hintFade != null && hintFade.isRunning()) {
                            if (hintFade != null) hintFade.cancel();
                            hintAlpha = 1f;
                        }
                        hintOn = true;
                        hintLeft = edgeStartX < getWidth() / 2f;
                        hintDx = (e.getX() - edgeStartX) * (hintLeft ? 1 : -1);
                        hintY = e.getY();
                        boolean was = hintCommit;
                        hintCommit = hintDx > 40 * d && hintDx > Math.abs(e.getY() - downY) * 1.2f;
                        if (hintCommit && !was) buzz(true);   // 到觸發位置（形狀剛好變正圓）震一下
                        invalidate();
                    } else if (live && e.getEventTime() - lastMove >= 8) {  // 即時注入：邊移動邊送（約 120Hz 上限）
                        lastMove = e.getEventTime();
                        liveSend(MotionEvent.ACTION_MOVE, e.getX(), e.getY());
                    }
                } else if (ctrlHit != 0 || kbd) {
                    // 點鍵盤圖示：過程中什麼都不動，放手時才處理
                } else if (resizing) {
                    // 寬度跟著手指在水平與垂直方向的移動量（垂直量換算成寬度）一起變，維持比例
                    float dx = e.getRawX() - startRawX, dy = e.getRawY() - startRawY;
                    card.setWidthPx((int) Math.max(minW, Math.min(maxW, startW + (dx + dy / card.aspect()) / 2f)));
                } else if (dismissing) {
                    float dy = e.getRawY() - startRawY;
                    targetY = baseY + (dy < 0 ? dy : dy * 0.2f);  // 往下拉有橡皮筋的阻力，只有往上才跟手
                    springY.retarget(targetY);
                    boolean now = -dy > 80 * d;
                    if (now != past) {  // 跨過門檻就震一下：放手會關閉／不會關閉
                        past = now;
                        buzz(now);
                    }
                } else {
                    targetX = clampX(baseX + (e.getRawX() - startRawX));
                    targetY = clampY(baseY + (e.getRawY() - startRawY));
                    springX.retarget(targetX);
                    springY.retarget(targetY);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                touching = false;
                float vx = 0f, vy = 0f;
                if (vt != null) { vt.addMovement(e); vt.computeCurrentVelocity(1000); vx = vt.getXVelocity(); vy = vt.getYVelocity(); }
                boolean up = e.getActionMasked() == MotionEvent.ACTION_UP;
                if (handle && handleUndecided) {
                    setPill(true);  // 只是碰了一下小白條：什麼都不做
                } else if (kbd) {
                    float moved = (float) Math.hypot(e.getX() - downX, e.getY() - downY);
                    if (up && moved < 14 * d) onControlTap(2);
                } else if (controlling) {
                    if (edge) {
                        float dx = (e.getX() - edgeStartX) * (edgeStartX < getWidth() / 2f ? 1 : -1);   // 往內為正
                        float dy = Math.abs(e.getY() - downY);
                        boolean back = up && dx > 40 * d && dx > dy * 1.2f;
                        hintCommit = false;
                        hintFadeOut();   // 放手：快速淡出
                        if (back) {
                            card.pulse(e.getX(), e.getY());
                            if (!DirectHost.key(4)) RootInput.key(OverlayService.vdId, 4);   // 4 = KEYCODE_BACK
                        } else if (up && Math.hypot(e.getX() - downX, e.getY() - downY) < 14 * d) {
                            liveSend(MotionEvent.ACTION_DOWN, downX, downY);   // 只是點邊緣：補送一次點擊
                            liveSend(MotionEvent.ACTION_UP, downX, downY);
                        }
                        edge = false;
                        live = false;
                    } else if (live) {
                        liveSend(up ? MotionEvent.ACTION_UP : MotionEvent.ACTION_CANCEL, e.getX(), e.getY());
                        live = false;
                        card.pulse(e.getX(), e.getY());
                    } else if (up) injectGesture(e);
                    postDelayed(idleEnd, IDLE_END_MS);  // 一段時間沒再碰，自動把操作交回 AI
                } else if (resizing) {
                    getContext().getSharedPreferences("cfg", Context.MODE_PRIVATE).edit().putInt("preview_w", card.cardWidth()).apply();
                    sync();
                } else if (dismissing) {
                    float dy = targetY - baseY;
                    // 用「預測落點」判斷：現在的位移再依手指速度往後推 0.15 秒，過門檻就關（所以快速輕甩也能關）
                    boolean close = up && (dy + vy * 0.15f < -90 * d);
                    if (close) {
                        float endY = -(card.cardHeight() + 40 * d);
                        // 臨界阻尼的彈簧，帶著放手瞬間的速度飛出螢幕上緣；縮放與圓角繼續連動
                        springY.start(card.getTranslationY(), Math.min(vy, 0f), endY, DISMISS_K, DISMISS_Z, 30f, (pos, done) -> {
                            card.setTranslationY(pos);
                            applyProgress(pos);
                            if (done) {
                                dismissing = false;
                                onClose.run();
                            }
                        });
                    } else {
                        // 沒滑夠：帶著速度回彈歸位（阻尼比 0.62，會有一點過衝）
                        yDone = false;
                        springY.start(card.getTranslationY(), vy, baseY, BACK_K, BACK_Z, 0.4f, (pos, done) -> {
                            card.setTranslationY(pos);
                            applyProgress(pos);
                            if (done) { yDone = true; dismissing = false; settled(); }
                        });
                    }
                } else {
                    // 移動：放手後帶一小段慣性（依手指速度再滑一點點），然後停下
                    targetX = clampX(targetX + vx * GLIDE_S);
                    targetY = clampY(targetY + vy * GLIDE_S);
                    springX.retarget(targetX);
                    springY.retarget(targetY);
                }
                if (vt != null) { vt.recycle(); vt = null; }
                resizing = false;
                return true;
            }
            default:
                return true;
        }
    }

    private static final long IDLE_END_MS = 4000;
    private static final float EDGE_DP = 14f;   // 左右邊緣多寬算「邊緣滑動返回」的起點

    /** 即時送一個觸控事件：有直接顯示主機就走 Binder（最快），否則走 LiveInput（root 管道）。 */
    private void liveSend(int action, float x, float y) {
        int vx = mapX(x), vy = mapY(y);
        if (DirectHost.ready(OverlayService.vdId)) { DirectHost.touch(action, vx, vy); return; }
        switch (action) {
            case MotionEvent.ACTION_DOWN: LiveInput.send("D " + vx + " " + vy); break;
            case MotionEvent.ACTION_MOVE: LiveInput.send("M " + vx + " " + vy); break;
            case MotionEvent.ACTION_UP: LiveInput.send("U " + vx + " " + vy); break;
            default: LiveInput.send("C");
        }
    }

    /** 螢幕座標換算：卡片觸控座標 → 背景虛擬螢幕座標。 */
    private int mapX(float x) { return clampI(x * OverlayService.vdW / Math.max(1, getWidth()), OverlayService.vdW); }
    private int mapY(float y) { return clampI(y * OverlayService.vdH / Math.max(1, card.cardHeight()), OverlayService.vdH); }

    /** 你停手一陣子了：結束接手，AI 繼續（打字框開著時不結束）。 */
    private void endTakeoverIfIdle() {
        if (touching || TakeoverInputActivity.active || !OverlayService.takeover) return;
        OverlayService.applyTakeover(false, "");
        RippleAccessibilityService.takeoverChanged();
    }

    void armIdle() {
        removeCallbacks(idleEnd);
        postDelayed(idleEnd, IDLE_END_MS);
    }

    /** 點小白條列最左邊的鍵盤圖示：開輸入框。 */
    private void onControlTap(int which) {
        buzz(false);
        if (which == 2) {
            if (!OverlayService.takeover) {
                OverlayService.applyTakeover(true, "");
                RippleAccessibilityService.takeoverChanged();
            }
            try {  // 明確開在主螢幕：背景螢幕剛被點過時，沒指定的話輸入框會開到背景螢幕裡
                getContext().startActivity(new android.content.Intent(getContext(), TakeoverInputActivity.class)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK), OverlayService.mainDisplayOptions());
            } catch (Exception e) {
                android.util.Log.e(OverlayService.TAG, "takeover input", e);
            }
        }
    }

    /** 接手中的一個手勢結束：依移動距離與時間判斷是點、長按還是滑，換算成背景螢幕座標後送出。 */
    private void injectGesture(MotionEvent up) {
        int id = OverlayService.vdId;
        float w = Math.max(1, getWidth()), h = Math.max(1, card.cardHeight());   // 不含下面的把手區
        int vx1 = clampI(downX * OverlayService.vdW / w, OverlayService.vdW);
        int vy1 = clampI(downY * OverlayService.vdH / h, OverlayService.vdH);
        int vx2 = clampI(up.getX() * OverlayService.vdW / w, OverlayService.vdW);
        int vy2 = clampI(up.getY() * OverlayService.vdH / h, OverlayService.vdH);
        float moved = (float) Math.hypot(up.getX() - downX, up.getY() - downY);
        long dur = up.getEventTime() - up.getDownTime();
        if (moved < 14 * d) {
            if (dur >= 450) RootInput.swipe(id, vx1, vy1, vx1, vy1, 700);  // 長按：原地「滑」700ms
            else RootInput.tap(id, vx1, vy1);
        } else {
            RootInput.swipe(id, vx1, vy1, vx2, vy2, (int) Math.max(120, Math.min(900, dur)));
        }
        card.pulse(up.getX(), up.getY());
    }

    private static int clampI(float v, int max) {
        return Math.max(0, Math.min(max - 1, (int) v));
    }

    void cancelAnimation() {
        springX.cancel();
        springY.cancel();
    }

    /**
     * 簡單的彈簧物理（質量 1）：加速度 = -k·(位置-目標) - c·速度，c = 2·阻尼比·√k。
     * 用 Choreographer 逐幀推進，內部以 1/240 秒為步長，避免大幅時間差時不穩。
     * retarget() 可以在彈簧運作中（或已停下後）改目標，用來做「追著手指跑」的跟手效果。
     */
    static final class Spring {
        interface Step { void on(float pos, boolean done); }

        private final Choreographer ch = Choreographer.getInstance();
        private float pos, vel, target, k, c, eps;
        private long last;
        private boolean running;
        private Step step;

        private final Choreographer.FrameCallback frame = new Choreographer.FrameCallback() {
            @Override public void doFrame(long nanos) {
                if (!running) return;
                float dt = Math.min(0.032f, (nanos - last) / 1e9f);
                last = nanos;
                for (float t = 0; t < dt; t += 1f / 240f) {
                    float h = Math.min(1f / 240f, dt - t);
                    float a = -k * (pos - target) - c * vel;
                    vel += a * h;
                    pos += vel * h;
                }
                boolean done = Math.abs(pos - target) < eps && (Math.abs(vel) < 20f || eps > 5f);
                if (done) pos = target;
                step.on(pos, done);
                if (done) running = false;
                else ch.postFrameCallback(this);
            }
        };

        void start(float from, float velocity, float to, float stiffness, float dampingRatio, float epsilon, Step s) {
            cancel();
            pos = from;
            vel = velocity;
            target = to;
            k = stiffness;
            c = 2f * dampingRatio * (float) Math.sqrt(stiffness);
            eps = epsilon;
            step = s;
            running = true;
            last = System.nanoTime();
            ch.postFrameCallback(frame);
        }

        /** 改目標；彈簧已經停下就用目前位置、速度 0 重新動起來。 */
        void retarget(float to) {
            target = to;
            if (step != null && !running) {
                running = true;
                last = System.nanoTime();
                ch.postFrameCallback(frame);
            }
        }

        void cancel() {
            running = false;
            ch.removeFrameCallback(frame);
        }
    }
}
