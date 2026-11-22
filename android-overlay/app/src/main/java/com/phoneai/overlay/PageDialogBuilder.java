package com.phoneai.overlay;

import android.content.Context;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * 整頁式對話框：用法跟 MaterialAlertDialogBuilder 一樣（標題、內容、按鈕都照舊），
 * 但視窗填滿整個畫面、從右邊滑進來，看起來跟其他整頁（互傳檔案、任務過程…）一致。
 * 只給「從設定或功能頁點進去的入口」用；裡面再跳出的確認、編輯小窗仍然用一般的對話框。
 */
final class PageDialogBuilder extends MaterialAlertDialogBuilder {
    PageDialogBuilder(Context c) { super(c, R.style.PageDialog); }

    @Override
    public AlertDialog create() {
        AlertDialog d = super.create();
        Window w = d.getWindow();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            w.getDecorView().setOnApplyWindowInsetsListener((v, ins) -> {   // 狀態列、導覽列的位置留給系統，標題與按鈕不被蓋住
                android.graphics.Insets i = ins.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                v.setPadding(i.left, i.top, i.right, i.bottom);
                return WindowInsets.CONSUMED;
            });
        }
        return d;
    }
}
