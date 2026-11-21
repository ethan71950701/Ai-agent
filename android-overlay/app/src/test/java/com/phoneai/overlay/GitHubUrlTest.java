package com.phoneai.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class GitHubUrlTest {
    @Test
    public void repoHomeGivesReadmeAndRootListing() {
        GitHubUrl.Target t = GitHubUrl.parse("https://github.com/ethan71950701/Ai-agent");
        assertNotNull(t);
        assertEquals(GitHubUrl.REPO, t.kind);
        assertEquals("https://api.github.com/repos/ethan71950701/Ai-agent/readme", t.contentUrl());
        assertEquals("https://api.github.com/repos/ethan71950701/Ai-agent/contents", t.listUrl());
        assertEquals("trailing slash, query, .git", "Ai-agent", GitHubUrl.parse("https://github.com/ethan71950701/Ai-agent.git/?tab=readme#x").repo);
    }

    @Test
    public void treeAndBlobUrls() {
        GitHubUrl.Target tree = GitHubUrl.parse("https://github.com/o/r/tree/main/android-overlay/app");
        assertEquals(GitHubUrl.TREE, tree.kind);
        assertEquals("https://api.github.com/repos/o/r/contents/android-overlay/app?ref=main", tree.listUrl());
        GitHubUrl.Target blob = GitHubUrl.parse("https://github.com/o/r/blob/main/README.md");
        assertEquals(GitHubUrl.BLOB, blob.kind);
        assertEquals("https://raw.githubusercontent.com/o/r/main/README.md", blob.contentUrl());
    }

    @Test
    public void otherSitesAndOddUrlsAreNotTreatedAsGithubRepos() {
        assertNull(GitHubUrl.parse("https://example.com/o/r"));
        assertNull(GitHubUrl.parse("https://github.com/o"));
        assertNull(GitHubUrl.parse("https://github.com/o/r/issues/1"));
        assertNull("路徑不能帶 ..", GitHubUrl.parse("https://github.com/o/r/blob/main/../../etc"));
        assertNull(GitHubUrl.parse("https://github.com/o/r/blob/main"));
        assertNull(GitHubUrl.parse("https://github.com/o r/x"));
        assertNull(GitHubUrl.parse(null));
        assertNull("http 不收", GitHubUrl.parse("http://github.com/o/r"));
    }
}
