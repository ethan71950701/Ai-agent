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
            "銀行|银行|證券|证券|保險|保险|信用卡|支付|錢包|钱包|街口|悠遊|密碼|密码|驗證器|验证器|(?<![a-z])(pay|bank|wallet|password|authenticator)|(?<![a-z])otp(?![a-z])", Pattern.CASE_INSENSITIVE);
    private static final Pattern PACKAGE = Pattern.compile("bank|wallet|paypal|spay|\\.pay|authenticator|(?<![a-z])otp(?![a-z])|1password|bitwarden|lastpass|keepass|keepersecurity|passwords?(?![a-z])", Pattern.CASE_INSENSITIVE);

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

    /**
     * 這個畫面要不要先問使用者才送給 AI：回 {已同意的記號, 要問的話}；沒事回 null。
     * 金融／支付／密碼管理類（或你標記保護的 App）→ 記號是套件名稱；其他 App 但畫面有密碼輸入框（登入頁）→ 記號是「套件名稱#pw」。
     * approved 是這個任務已經同意過的記號，同意過就不再問（也不再掃描畫面）。
     */
    static String[] sensitive(Context c, String pkg, Set<String> approved) {
        if (pkg == null || pkg.isEmpty() || approved.contains(pkg)) return null;
        if (isProtected(c, pkg))
            return new String[]{pkg, String.format(L.t("目前畫面是「%s」（金融／支付類，或你標記保護的 App）。要讓 AI 看畫面並操作嗎？"), label(c, pkg))};
        if (approved.contains(pkg + "#pw")) return null;
        if (ElementList.hasPasswordField())
            return new String[]{pkg + "#pw", String.format(L.t("目前畫面（「%s」）有密碼輸入框，可能是登入頁。要讓 AI 看畫面並操作嗎？"), label(c, pkg))};
        return null;
    }

    /**
     * 目前前景畫面放懸浮卡片會不會讓它點不到：金融／支付／密碼管理類、Google 登入（gms）、畫面有密碼輸入框的登入頁，
     * 這些畫面常有「上面有別的視窗就不接受點擊」的防護（使用者實測 Google 登入頁真的點不到）。回 true = 接手提示改用通知，不放卡片。
     */
    static boolean overlayUnsafe(Context c, String fgPkg) {
        if (fgPkg == null || fgPkg.isEmpty()) return false;
        return isProtected(c, fgPkg) || fgPkg.startsWith("com.google.android.gms") || fgPkg.startsWith("com.google.android.gsf") || ElementList.hasPasswordField();
    }

    static boolean isProtected(Context c, String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        return userSet(c).contains(pkg) || autoMatch(label(c, pkg), pkg);
    }
}
