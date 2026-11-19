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

    /** 會洩漏通知、帳號、剪貼簿、聯絡人、電話、簡訊內容的 dumpsys 服務名稱。 */
    private static final String[] DUMPSYS_PRIVATE = {"notification", "account", "clipboard", "contacts", "telecom", "sms", "content", "user"};

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
        String low = cmd.toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ");   // 多個空格、Tab 先變成一個空格再比對（以前「dumpsys    notification」能繞過黑名單）
        for (String d : SHELL_DENY) if (low.contains(d)) return false;
        if (cmd.matches(".*[;&`$<>*?{}()\\\\\\[\\]!~#'\"].*") || cmd.contains("||")) return false;   // 引號也擋：dumpsys "notification" 這種寫法會讓黑名單比對失效
        for (int k = 0; k < cmd.length(); k++) { char ch = cmd.charAt(k); if (ch < 0x20 && ch != '\t') return false; if (ch == 0x7f) return false; }   // 控制字元（NUL、ESC…）一律擋
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
            case "dumpsys": {   // 白名單：一定要指定服務，而且服務在 DUMPSYS_OK 內（單獨 dumpsys 會傾印全部服務，含通知、帳號…）
                int k = 1;
                if (k < w.length && w[k].equals("-t") && k + 1 < w.length && w[k + 1].matches("\\d{1,3}")) k += 2;   // dumpsys -t 秒數 服務
                if (k >= w.length || !in(DUMPSYS_OK, w[k].toLowerCase(java.util.Locale.ROOT))) return false;
                for (int m = k + 1; m < w.length; m++) {
                    String a = w[m].toLowerCase(java.util.Locale.ROOT);
                    if (in(DUMPSYS_MUTATE, a) || in(DUMPSYS_PRIVATE, a)) return false;
                }
                return true;
            }
            case "cat":   // 只准讀這幾類系統資訊檔，不是任意路徑（黑名單擋不完：/proc/<pid>/environ、其他 App 的行程資訊…）
                if (w.length < 2) return false;
                for (int k = 1; k < w.length; k++) if (w[k].startsWith("-") || !catPathOk(w[k])) return false;
                return true;
            case "ls":   // 遞迴列出會繞過路徑黑名單（ls -R / 會列出 /data、/sdcard 底下的名稱）
                for (int k = 1; k < w.length; k++) if (w[k].matches("-[a-zA-Z]*[Rr][a-zA-Z]*") || w[k].startsWith("--recursive")) return false;
                return true;
            case "top":   // 沒有 -n 的 top 不會結束
                for (int k = 1; k < w.length; k++) if (w[k].matches("-[a-zA-Z]*n[a-zA-Z]*")) return true;
                return false;
            default: return true;   // getprop、ls、cat、df、uptime、free、ps、top、uname：本身只讀（寫檔要靠 > ，已被擋）
        }
    }

    /** dumpsys 允許的服務（唯讀的系統狀態）。不在這裡的（通知、帳號、剪貼簿、聯絡人、電話、簡訊、位置、使用紀錄、wifi…）一律不行。 */
    private static final String[] DUMPSYS_OK = {"battery", "meminfo", "cpuinfo", "display", "power", "thermalservice", "diskstats", "window", "surfaceflinger", "connectivity", "-l"};

    private static final String[] CAT_OK_PREFIX = {"/proc/meminfo", "/proc/cpuinfo", "/proc/version", "/proc/uptime", "/proc/loadavg", "/proc/stat", "/proc/vmstat", "/proc/zoneinfo",
            "/sys/class/power_supply/", "/sys/class/thermal/", "/sys/devices/system/cpu/", "/system/build.prop", "/vendor/build.prop"};

    static boolean catPathOk(String path) {
        if (path.contains("..") || path.contains("//")) return false;
        for (String p : CAT_OK_PREFIX) if (path.equals(p) || (p.endsWith("/") && path.startsWith(p))) return true;
        return false;
    }

    /**
     * 管線後面的文字處理工具：只准處理前一段的輸出（標準輸入），**不准任何檔案參數**——
     * 不然 `getprop | grep . /proc/self/environ` 就能讓 grep 讀白名單外的檔案（外部審查發現）。
     * 做法：把參數分成「選項」「選項的值」「一般參數（可能是檔案）」，一般參數數量必須剛好符合（grep 一個＝搜尋字串；tr 一到兩個＝字元集；其他零個），
     * 而且搜尋字串與字元集不能含「/」；會讀檔、遞迴、寫檔、執行程式、不會結束的選項一律拒絕。
     */
    private static boolean pipeSegmentSafe(String prog, String[] w) {
        final java.util.Set<String> valueOpts;   // 需要下一個參數當值的選項
        switch (prog) {
            case "grep": valueOpts = new java.util.HashSet<>(java.util.Arrays.asList("-m", "-A", "-B", "-C")); break;
            case "head": case "tail": valueOpts = new java.util.HashSet<>(java.util.Arrays.asList("-n", "-c")); break;
            case "sort": valueOpts = new java.util.HashSet<>(java.util.Arrays.asList("-k", "-t")); break;
            case "cut": valueOpts = new java.util.HashSet<>(java.util.Arrays.asList("-d", "-f", "-c", "-b")); break;
            case "uniq": valueOpts = new java.util.HashSet<>(java.util.Arrays.asList("-f", "-s", "-w")); break;
            default: valueOpts = new java.util.HashSet<>(); break;   // wc、tr
        }
        java.util.List<String> operands = new java.util.ArrayList<>();
        boolean skipNext = false;
        for (int k = 1; k < w.length; k++) {
            String t = w[k];
            if (skipNext) { skipNext = false; continue; }   // 選項的值（數字、分隔字元…）；這些選項的值不會被當成檔案
            if (t.startsWith("-") && t.length() > 1) {
                if (t.startsWith("--")) {   // 長選項一律不收（--file、--recursive、--output、--follow…），只留常見的無害旗標
                    if (!(t.equals("--count") || t.equals("--ignore-case") || t.equals("--invert-match") || t.equals("--line-number"))) return false;
                    continue;
                }
                if (prog.equals("grep") && t.matches("-[a-zA-Z]*[frRdDeEPXz][a-zA-Z]*") && !t.matches("-[a-zA-Z]*E[a-zA-Z]*")) return false;   // -f 檔案、-r/-R 遞迴、-d/-D 目錄與裝置、-e 另一種給搜尋字串的方式
                if (prog.equals("sort") && t.matches("-[a-zA-Z]*[oT][a-zA-Z]*")) return false;      // -o 寫檔、-T 暫存目錄
                if (prog.equals("tail") && t.matches("-[a-zA-Z]*[fF][a-zA-Z]*")) return false;      // -f 不會結束
                if (valueOpts.contains(t)) skipNext = true;
                continue;
            }
            operands.add(t);
        }
        switch (prog) {
            case "grep":
                return operands.size() == 1 && operands.get(0).matches("[^/\\\\\\s]{1,80}");
            case "tr":
                if (operands.isEmpty() || operands.size() > 2) return false;
                for (String o : operands) if (!o.matches("[A-Za-z0-9\\-:,._]{1,40}")) return false;
                return true;
            default:
                return operands.isEmpty();   // head、tail、wc、sort、cut、uniq：不能有任何檔案參數
        }
    }

    private static boolean in(String[] arr, String s) {
        for (String x : arr) if (x.equals(s)) return true;
        return false;
    }
}
