package org.architech.launcher.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafePathsTest {
    @TempDir
    Path tempDir;

    @Test
    void resolvesNestedRelativePathInsideRoot() throws Exception {
        Path resolved = SafePaths.resolveInside(tempDir, "mods/example.jar");

        assertEquals(
                tempDir.toAbsolutePath().normalize().resolve("mods/example.jar"),
                resolved
        );
    }

    @Test
    void normalizesWindowsSeparators() throws Exception {
        assertEquals("mods/example.jar", SafePaths.normalizeRelative("mods\\example.jar"));
    }

    @Test
    void rejectsParentTraversal() {
        assertThrows(IOException.class, () -> SafePaths.resolveInside(tempDir, "../escape.txt"));
        assertThrows(IOException.class, () -> SafePaths.resolveInside(tempDir, "mods/../../escape.txt"));
        assertThrows(IOException.class, () -> SafePaths.resolveInside(tempDir, "mods/../escape.txt"));
        assertThrows(IOException.class, () -> SafePaths.resolveInside(tempDir, "mods\\..\\escape.txt"));
    }

    @Test
    void rejectsAbsoluteAndWindowsDrivePathsOnEveryOs() {
        assertThrows(IOException.class, () -> SafePaths.resolveInside(tempDir, "/etc/passwd"));
        assertThrows(IOException.class, () -> SafePaths.resolveInside(tempDir, "C:\\Windows\\win.ini"));
        assertThrows(IOException.class, () -> SafePaths.resolveInside(tempDir, "\\\\server\\share\\file"));
    }

    @Test
    void rejectsEmptyDotAndNulPaths() {
        assertThrows(IOException.class, () -> SafePaths.resolveInside(tempDir, ""));
        assertThrows(IOException.class, () -> SafePaths.resolveInside(tempDir, "."));
        assertThrows(IOException.class, () -> SafePaths.resolveInside(tempDir, "a/./b"));
        assertThrows(IOException.class, () -> SafePaths.resolveInside(tempDir, "bad\0name"));
    }

    @Test
    void createsParentsWithoutFollowingSymlinks() throws Exception {
        Path target = SafePaths.resolveInside(tempDir, "one/two/file.bin");

        SafePaths.createParentDirectoriesSecurely(tempDir, target);

        assertTrue(Files.isDirectory(tempDir.resolve("one/two")));
    }

    @Test
    void refusesSymlinkParentWhenSupported() throws Exception {
        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        Path link = tempDir.resolve("link");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (UnsupportedOperationException | IOException | SecurityException unsupported) {
            return;
        }

        Path target = SafePaths.resolveInside(tempDir, "link/file.txt");
        assertThrows(
                IOException.class,
                () -> SafePaths.createParentDirectoriesSecurely(tempDir, target)
        );
    }
}
