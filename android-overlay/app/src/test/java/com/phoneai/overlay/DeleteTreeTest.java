package com.phoneai.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class DeleteTreeTest {
    @Test
    public void deletesAWholeTree() throws Exception {
        Path root = Files.createTempDirectory("del");
        Files.createDirectories(root.resolve("a/b"));
        Files.write(root.resolve("a/b/x.txt"), new byte[]{1});
        Files.write(root.resolve("a/y.txt"), new byte[]{1});
        assertTrue(FileTools.deleteTree(root.resolve("a").toFile()));
        assertFalse(Files.exists(root.resolve("a")));
    }

    @Test
    public void aSymlinkInsideTheTreeIsRemovedButNotFollowed() throws Exception {
        Path base = Files.createTempDirectory("del2");
        Path outside = Files.createDirectory(base.resolve("outside"));
        Files.write(outside.resolve("keep.txt"), new byte[]{1, 2, 3});
        Path inside = Files.createDirectory(base.resolve("share"));
        Files.write(inside.resolve("mine.txt"), new byte[]{1});
        Files.createSymbolicLink(inside.resolve("link-dir"), outside);
        Files.createSymbolicLink(inside.resolve("link-file"), outside.resolve("keep.txt"));
        assertTrue(FileTools.deleteTree(inside.toFile()));
        assertFalse(Files.exists(inside));
        assertTrue("連結指到的外面資料夾不能被刪：", new File(outside.toFile(), "keep.txt").isFile());
        assertEquals(3, new File(outside.toFile(), "keep.txt").length());
    }
}
