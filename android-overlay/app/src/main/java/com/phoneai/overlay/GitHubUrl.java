package com.phoneai.overlay;

/** GitHub 網址換成適合讀的來源（純 Java，只認公開的 github.com 網址）：repo 首頁→README＋根目錄清單；tree→資料夾清單；blob→原始檔。 */
final class GitHubUrl {
    private GitHubUrl() { }

    static final int REPO = 1, TREE = 2, BLOB = 3;

    static final class Target {
        int kind;
        String owner, repo, ref = "", path = "";

        /** 取檔案內容的網址（BLOB：原始檔；REPO：README 的 API）。 */
        String contentUrl() {
            if (kind == BLOB) return "https://raw.githubusercontent.com/" + owner + "/" + repo + "/" + ref + "/" + path;
            if (kind == REPO) return "https://api.github.com/repos/" + owner + "/" + repo + "/readme";
            return null;
        }

        /** 資料夾清單的網址（TREE：那個資料夾；REPO：根目錄）。 */
        String listUrl() {
            if (kind == TREE) return "https://api.github.com/repos/" + owner + "/" + repo + "/contents/" + path + (ref.isEmpty() ? "" : "?ref=" + ref);
            if (kind == REPO) return "https://api.github.com/repos/" + owner + "/" + repo + "/contents";
            return null;
        }
    }

    private static boolean safe(String s) { return s != null && s.matches("[A-Za-z0-9._\\-]{1,100}"); }

    private static boolean safePath(String s) { return s != null && s.length() <= 500 && s.matches("[A-Za-z0-9._\\-/%+~@ ]*") && !s.contains(".."); }

    /** 不是 github.com 的網址、或看不懂的格式回 null（呼叫端就當一般網頁讀）。 */
    static Target parse(String url) {
        if (url == null) return null;
        String u = url.trim();
        String rest;
        if (u.startsWith("https://github.com/")) rest = u.substring("https://github.com/".length());
        else if (u.startsWith("https://www.github.com/")) rest = u.substring("https://www.github.com/".length());
        else return null;
        int cut = Math.min(rest.indexOf('?') < 0 ? rest.length() : rest.indexOf('?'), rest.indexOf('#') < 0 ? rest.length() : rest.indexOf('#'));
        rest = rest.substring(0, cut);
        while (rest.endsWith("/")) rest = rest.substring(0, rest.length() - 1);
        String[] p = rest.split("/");
        if (p.length < 2 || !safe(p[0]) || !safe(p[1])) return null;
        Target t = new Target();
        t.owner = p[0];
        t.repo = p[1].endsWith(".git") ? p[1].substring(0, p[1].length() - 4) : p[1];
        if (p.length == 2) { t.kind = REPO; return t; }
        if (p.length >= 4 && (p[2].equals("tree") || p[2].equals("blob")) && safe(p[3])) {
            t.kind = p[2].equals("tree") ? TREE : BLOB;
            t.ref = p[3];   // 分支名稱有斜線的看不出來，只取第一段
            StringBuilder sb = new StringBuilder();
            for (int i = 4; i < p.length; i++) { if (sb.length() > 0) sb.append('/'); sb.append(p[i]); }
            t.path = sb.toString();
            if (!safePath(t.path) || (t.kind == BLOB && t.path.isEmpty())) return null;
            return t;
        }
        return null;
    }
}
