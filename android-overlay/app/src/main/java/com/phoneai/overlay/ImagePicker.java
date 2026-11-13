package com.phoneai.overlay;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** 上傳圖片的共用零件（助理面板與首頁都用）：開系統相片選擇器、把選到的圖縮小成 JPEG、縮圖列（可移除）。 */
final class ImagePicker {
    static final int REQ = 4714, REQ_CAMERA = 4715, MAX = 4;
    private static volatile java.io.File cameraFile;   // 這次拍照要存的檔（相機 App 寫進去；Activity 重建了也還在）

    /** 開系統相機拍一張（不需要相機權限，由系統相機 App 拍好交回）。回 false = 打不開。 */
    static boolean launchCamera(Activity act) {
        try {
            java.io.File dir = new java.io.File(act.getCacheDir(), "camera");
            dir.mkdirs();
            java.io.File f = new java.io.File(dir, "shot_" + System.currentTimeMillis() + ".jpg");
            cameraFile = f;
            Uri u = androidx.core.content.FileProvider.getUriForFile(act, act.getPackageName() + ".update", f);
            Intent i = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).putExtra(android.provider.MediaStore.EXTRA_OUTPUT, u)
                    .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            act.startActivityForResult(i, REQ_CAMERA);
            return true;
        } catch (Throwable t) {
            CrashLog.w("開相機", t);
            return false;
        }
    }

    /** 拍照回來：回一個帶著照片 Uri 的 Intent，交給 load；沒拍到回 null。 */
    static Intent cameraResult(Activity act) {
        java.io.File f = cameraFile;
        if (f == null || !f.exists() || f.length() < 100) return null;
        return new Intent().setData(androidx.core.content.FileProvider.getUriForFile(act, act.getPackageName() + ".update", f));
    }

    /** 清掉拍照留下的暫存檔（已經讀進記憶體了）。 */
    static void cleanCamera(Context c) {
        try {
            java.io.File[] fs = new java.io.File(c.getCacheDir(), "camera").listFiles();
            if (fs != null) for (java.io.File f : fs) f.delete();
        } catch (Throwable ignored) { }
        cameraFile = null;
    }

    private ImagePicker() { }

    /** 已加入的圖片：base64 JPEG（長邊 1280，給模型）＋小縮圖（只用來顯示）。 */
    static final class Set {
        final ArrayList<String> b64 = new ArrayList<>();
        final ArrayList<Bitmap> thumbs = new ArrayList<>();
        boolean isEmpty() { return b64.isEmpty(); }
        void clear() { b64.clear(); thumbs.clear(); }
    }

    /** 開系統的相片選擇器（Android 13+ 內建，不用讀取相簿的權限）；沒有的話退回一般的選檔案。回 false = 打不開。 */
    static boolean launch(Activity act, int room) {
        try {
            Intent i = new Intent(android.provider.MediaStore.ACTION_PICK_IMAGES);
            if (room > 1) i.putExtra(android.provider.MediaStore.EXTRA_PICK_IMAGES_MAX, Math.min(room, android.provider.MediaStore.getPickImagesMaxLimit()));
            act.startActivityForResult(i, REQ);
            return true;
        } catch (Exception e) {
            try {
                Intent i = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE, room > 1);
                act.startActivityForResult(Intent.createChooser(i, null), REQ);
                return true;
            } catch (Exception e2) {
                return false;
            }
        }
    }

    /** 讀選到的圖（背景執行緒），好了在主執行緒交給 done；讀不到的略過。 */
    static void load(final Activity act, Intent data, final int room, final java.util.function.Consumer<Set> done) {
        final ArrayList<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri());
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        final Context app = act.getApplicationContext();
        new Thread(() -> {
            final Set out = new Set();
            for (Uri u : uris) {
                if (out.b64.size() >= room) break;
                try {
                    Bitmap b = android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(app.getContentResolver(), u), (dec, info, src) -> {
                        dec.setAllocator(android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE);
                        int m = Math.max(info.getSize().getWidth(), info.getSize().getHeight());
                        if (m > 1280) { float f = 1280f / m; dec.setTargetSize(Math.max(1, Math.round(info.getSize().getWidth() * f)), Math.max(1, Math.round(info.getSize().getHeight() * f))); }
                    });
                    out.b64.add(CircleShot.jpeg64(b, 1280, 85));
                    float s = 160f / Math.max(b.getWidth(), b.getHeight());
                    out.thumbs.add(Bitmap.createScaledBitmap(b, Math.max(1, Math.round(b.getWidth() * s)), Math.max(1, Math.round(b.getHeight() * s)), true));
                } catch (Throwable t) {
                    CrashLog.w("讀取上傳的圖片", t);
                }
            }
            cleanCamera(app);
            act.runOnUiThread(() -> done.accept(out));
        }, "pick-images").start();
    }

    /** 縮圖列：每張右上角有 ✕ 可以移除。沒有圖片時整列隱藏。 */
    static HorizontalScrollView strip(Context c) {
        HorizontalScrollView sv = new HorizontalScrollView(c);
        sv.setHorizontalScrollBarEnabled(false);
        sv.setClipChildren(false);
        sv.setVisibility(View.GONE);
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.HORIZONTAL);
        float d = c.getResources().getDisplayMetrics().density;
        box.setPadding((int) (8 * d), 0, (int) (8 * d), 0);
        sv.addView(box, new ViewGroup.LayoutParams(-2, -2));
        return sv;
    }

    static void render(Context c, HorizontalScrollView sv, final Set set, final Runnable changed) {
        final float d = c.getResources().getDisplayMetrics().density;
        LinearLayout box = (LinearLayout) sv.getChildAt(0);
        box.removeAllViews();
        sv.setVisibility(set.isEmpty() ? View.GONE : View.VISIBLE);
        final int size = (int) (64 * d);
        for (int i = 0; i < set.thumbs.size(); i++) {
            final int idx = i;
            FrameLayout cell = new FrameLayout(c);
            ImageView iv = new ImageView(c);
            iv.setImageBitmap(set.thumbs.get(i));
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setClipToOutline(true);
            iv.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 14 * d); }
            });
            cell.addView(iv, new FrameLayout.LayoutParams(size, size));
            TextView x = new TextView(c);
            x.setText("✕");
            x.setTextColor(Color.WHITE);
            x.setTextSize(11);
            x.setGravity(Gravity.CENTER);
            GradientDrawable xb = new GradientDrawable();
            xb.setShape(GradientDrawable.OVAL);
            xb.setColor(0xCC000000);
            x.setBackground(xb);
            x.setContentDescription(L.t("移除圖片"));
            x.setOnClickListener(v -> {
                if (idx < set.b64.size()) { set.b64.remove(idx); set.thumbs.remove(idx); }
                render(c, sv, set, changed);
                if (changed != null) changed.run();
            });
            cell.addView(x, new FrameLayout.LayoutParams((int) (22 * d), (int) (22 * d), Gravity.TOP | Gravity.END));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(size, size);
            cp.rightMargin = (int) (8 * d);
            box.addView(cell, cp);
        }
    }
}
