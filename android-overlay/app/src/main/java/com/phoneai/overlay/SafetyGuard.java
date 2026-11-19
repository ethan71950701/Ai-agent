package com.phoneai.overlay;

/**
 * 安全規則的純邏輯（不依賴 Android，可以直接單元測試）。從 AgentLoop 慢慢搬出來的第一塊：
 * 「唯讀 shell 指令」檢查——給聊天助理的 run_shell 與自動操作的 shell 動作用，只准查詢、不准改東西。
 * 規則細節見 {@link #readOnly}；改這裡要同步跑 LogicTest 的 readOnly* 測試。
 */
final class SafetyGuard {
    private SafetyGuard() { }

    private static final String[] READ_BINS = {"dumpsys", "getprop", "date", "settings", "cmd", "pm", "am", "ls", "cat", "df", "uptime", "free", "ps", "top", "wm", "uname"};
    private static final String[] PIPE_OK = {"grep", "head", "tail", "sort", "cut", "wc", "uniq", "tr"};

    /** root／Shizuku 讀得到所有 App 的私有資料（含 API Key）：唯讀指令也不能碰這些路徑，或會洩漏通知、帳號、剪貼簿內容的 dumpsys。 */
    private static final String[] SHELL_DENY = {"/data", "/sdcard", "/storage", "/mnt", "..", "claude_key", "openai_key", "key_", "token", "password", "keystore", "noredact",
            "dumpsys notification", "dumpsys account", "dumpsys clipboard", "dumpsys contacts", "dumpsys telecom", "dumpsys sms"};

    /** dumpsys 的子命令或參數裡出現這些字，表示會改系統狀態（例如 dumpsys battery set level 5、unplug）。 */
    private static final String[] DUMPSYS_MUTATE = {"set", "reset", "unplug", "suspend", "restore", "enable", "disable", "start", "stop", "kill", "force", "write", "clear", "add", "remove", "delete", "trigger"};

    /**
     * 唯讀指令檢查（root／Shizuku 的 run_shell 與自動操作的 shell 動作都用它）。規則：
     *  1. 任何情況都拒絕 shell 控制字元（; & ` $ < > * ? { } ( ) \ [ ] ! ~ #）與 ||——以前含 | 時整段跳過這個檢查，
     *     「getprop | grep x; settings put …」能通過（外部審查發現）。
     *  2. 只允許用 | 串起來的簡單指令；每一段都逐字檢查參數，不只看第一個程式名稱。
     *  3. 第一段的程式在白名單內，而且子命令／參數也要唯讀（settings 只能 get／list、am／cmd／wm／date／dumpsys 另有規則）。
     *  4. | 後面只能接純文字處理工具，而且不能寫檔（sort -o、uniq 的輸出檔參數）、不能卡住（tail -f）。
     */
    static boolean readOnly(String cmd) {
        if (cmd == null || cmd.trim().isEmpty() || cmd.contains("\n") || cmd.contains("\r")) return false;
        String low = cmd.toLowerCase(java.util.Locale.ROOT);
        for (String d : SHELL_DENY) if (low.contains(d)) return false;
        if (cmd.matches(".*[;&`$<>*?{}()\\\\\\[\\]!~#].*") || cmd.contains("||")) return false;
        String[] parts = cmd.split("\\|", -1);
        for (int i = 0; i < parts.length; i++) {
            String seg = parts[i].trim();
            if (seg.isEmpty()) return false;
            String[] w = seg.split("\\s+");
            String prog = w[0];
            if (i == 0) {
                if (!in(READ_BINS, prog) || !firstSegmentReadOnly(prog, w)) return false;
            } else {
                if (!in(PIPE_OK, prog) || !pipeSegmentSafe(prog, w)) return false;
            }
        }
        return true;
    }

    private static boolean firstSegmentReadOnly(String prog, String[] w) {
        switch (prog) {
            case "settings": return w.length >= 2 && (w[1].equals("get") || w[1].equals("list"));
            case "pm": return w.length >= 2 && (w[1].equals("list") || w[1].equals("path") || w[1].equals("-h"));
            case "am": return w.length >= 2 && (w[1].equals("get-config") || w[1].equals("-h"));
            case "wm": return w.length == 2 && (w[1].equals("size") || w[1].equals("density"));
            case "cmd":
                return w.length >= 2 && (w[1].equals("-l")
                        || (w[1].equals("wifi") && w.length == 3 && w[2].equals("status"))
                        || (w[1].equals("package") && w.length >= 3 && w[2].equals("list")));
            case "date":   // date -s／date 月日時分 會改系統時間：只允許 +格式、-u、-R
                for (int k = 1; k < w.length; k++) if (!(w[k].startsWith("+") || w[k].equals("-u") || w[k].equals("-R"))) return false;
                return true;
            case "dumpsys":
                for (int k = 1; k < w.length; k++) if (in(DUMPSYS_MUTATE, w[k].toLowerCase(java.util.Locale.ROOT))) return false;
                return true;
            default: return true;   // getprop、ls、cat、df、uptime、free、ps、top、uname：本身只讀（寫檔要靠 > ，已被擋）
        }
    }

    private static boolean pipeSegmentSafe(String prog, String[] w) {
        switch (prog) {
            case "sort":   // sort -o 檔案 會寫檔；--compress-program 會執行程式；-T 指定暫存目錄
                for (int k = 1; k < w.length; k++) {
                    if (w[k].matches("-[a-zA-Z]*[oT][a-zA-Z]*") || w[k].startsWith("--output") || w[k].startsWith("--compress-program") || w[k].startsWith("--temporary-directory")) return false;
                }
                return true;
            case "uniq":   // uniq 輸入 輸出：第二個非選項參數是輸出檔
                for (int k = 1; k < w.length; k++) if (!w[k].startsWith("-")) return false;
                return true;
            case "tail":   // tail -f 不會結束
                for (int k = 1; k < w.length; k++) if (w[k].equals("-f") || w[k].equals("-F") || w[k].startsWith("--follow")) return false;
                return true;
            default: return true;
        }
    }

    private static boolean in(String[] arr, String s) {
        for (String x : arr) if (x.equals(s)) return true;
        return false;
    }
}
