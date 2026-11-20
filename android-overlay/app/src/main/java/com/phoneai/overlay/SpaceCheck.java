package com.phoneai.overlay;

/** 傳大檔案前，先用對方回報的上限與剩餘空間判斷收不收得下（純 Java），不夠就直接說原因，不用傳到一半才被掛斷。 */
final class SpaceCheck {
    private SpaceCheck() { }

    /** 收不下回原因，收得下回 null。free＝對方現在可用的空間（已扣掉別人預留的），keep＝收完要保留的，max＝單檔上限。 */
    static String problem(long size, long free, long keep, long max) {
        if (size < 0) return null;
        if (size > max) return "檔案太大：對方一次最多收 " + human(max) + "，這個檔案是 " + human(size);
        if (free - size < keep) return "對方儲存空間不夠：現在剩 " + human(free) + "，這個檔案要 " + human(size) + "，而且收完要保留 " + human(keep)
                + "（請對方清出至少 " + human(Math.max(0, size + keep - free)) + "）";
        return null;
    }

    static String human(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024L * 1024) return (b / 1024) + " KB";
        if (b < 1024L * 1024 * 1024) return String.format(java.util.Locale.US, "%.1f MB", b / 1048576.0);
        return String.format(java.util.Locale.US, "%.2f GB", b / 1073741824.0);
    }

    /** 對方的回覆「free\tkeep\tmax」；格式不對回 null（對方是舊版就不檢查）。 */
    static long[] parse(String reply) {
        try {
            String[] p = reply.trim().split("\\t");
            if (p.length < 3) return null;
            return new long[]{Long.parseLong(p[0]), Long.parseLong(p[1]), Long.parseLong(p[2])};
        } catch (Exception e) {
            return null;
        }
    }
}
