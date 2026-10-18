package io.github.rareities.protonbridge.util;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class PrivateFileStagingTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void rejectsProviderPathsAndSymlinkEscapes() throws Exception {
        File root = temporary.newFolder("preview");
        for (String name : new String[]{"", ".", "..", "../rclone.conf", "/absolute", "child/name", "child\\name", "bad\0name"}) {
            assertThrows(IOException.class, () -> PrivateFileStaging.target(root, name));
        }
        File outside = temporary.newFile("outside");
        Files.createSymbolicLink(new File(root, "link").toPath(), outside.toPath());
        assertThrows(IOException.class, () -> PrivateFileStaging.target(root, "link"));
    }

    @Test public void preservesLegitimateBasenamesAndIsolatesCopies() throws Exception {
        File cache = temporary.newFolder("cache");
        File first = PrivateFileStaging.directory(cache, "share_staging");
        File second = PrivateFileStaging.directory(cache, "share_staging");
        assertNotEquals(first, second);
        for (String name : new String[]{"blåbær.txt", "chữ Quốc ngữ.txt", " a file ", "--flag", "line\nname"})
            assertEquals(name, PrivateFileStaging.target(first, name).getName());
        assertNotEquals(PrivateFileStaging.target(first, "same.txt"), PrivateFileStaging.target(second, "same.txt"));
    }

    @Test public void rejectsPrivateSourceButNotSiblingWithSimilarPrefix() throws Exception {
        File privateRoot = temporary.newFolder("data");
        assertThrows(IOException.class, () -> PrivateFileStaging.requireOutside(new File(privateRoot, "rclone.conf"), privateRoot));
        File link = new File(temporary.getRoot(), "alias");
        Files.createSymbolicLink(link.toPath(), privateRoot.toPath());
        assertThrows(IOException.class, () -> PrivateFileStaging.requireOutside(new File(link, "rclone.conf"), privateRoot));
        PrivateFileStaging.requireOutside(new File(temporary.getRoot(), "data-public/photo.jpg"), privateRoot);
    }
}
