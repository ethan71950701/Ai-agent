package com.phoneai.overlay;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 受保護的 App（銀行、支付、錢包這類，加上使用者自己勾選的）：AI 看這些 App 的畫面或操作之前，一定先問使用者。
 * 沒問過就不會把畫面文字或截圖送給 AI（餘額、帳號這類資料不該隨便送出去）。
 */
final class ProtectedApps {
    private ProtectedApps() { }

    private static final Pattern LABEL = Pattern.compile(
            "銀行|银行|證券|证券|保險|保险|信用卡|支付|錢包|钱包|街口|悠遊|(?<![a-z])(pay|bank|wallet)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PACKAGE = Pattern.compile("bank|wallet|paypal|spay|\\.pay", Pattern.CASE_INSENSITIVE);

    /** 依顯示名稱與套件名稱自動判斷是不是金融／支付類（純文字比對，不碰手機）。 */
    static boolean autoMatch(String label, String pkg) {
        return (label != null && LABEL.matcher(label).find()) || (pkg != null && PACKAGE.matcher(pkg).find());
    }

    private static SharedPreferences cfg(Context c) { return c.getSharedPreferences("cfg", Context.MODE_PRIVATE); }

    static Set<String> userSet(Context c) { return new HashSet<>(cfg(c).getStringSet("protected_apps", new HashSet<>())); }

    static void setUserSet(Context c, Set<String> s) { cfg(c).edit().putStringSet("protected_apps", new HashSet<>(s)).apply(); }

    static String label(Context c, String pkg) {
        try {
            android.content.pm.PackageManager pm = c.getPackageManager();
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (Exception e) {
            return pkg;
        }
    }

    static boolean isProtected(Context c, String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        return userSet(c).contains(pkg) || autoMatch(label(c, pkg), pkg);
    }
}
