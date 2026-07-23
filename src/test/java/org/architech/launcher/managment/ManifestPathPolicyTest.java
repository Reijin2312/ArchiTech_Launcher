package org.architech.launcher.managment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ManifestPathPolicyTest {
    @TempDir
    Path tempDir;

    @Test
    void normalizesSafeManifestPath() throws Exception {
        assertEquals(
                "mods/example.jar",
                ManifestPathPolicy.validate(tempDir, "mods\\example.jar")
        );
    }

    @Test
    void rejectsTraversalAndAbsolutePaths() {
        assertThrows(IOException.class, () -> ManifestPathPolicy.validate(tempDir, "../evil.jar"));
        assertThrows(IOException.class, () -> ManifestPathPolicy.validate(tempDir, "/tmp/evil.jar"));
        assertThrows(IOException.class, () -> ManifestPathPolicy.validate(tempDir, "C:\\evil.jar"));
    }

    @Test
    void rejectsProtectedTopLevelDirectoriesCaseInsensitively() {
        assertThrows(IOException.class, () -> ManifestPathPolicy.validate(tempDir, "launcher/update.jar"));
        assertThrows(IOException.class, () -> ManifestPathPolicy.validate(tempDir, "NeoForge/file.jar"));
        assertThrows(IOException.class, () -> ManifestPathPolicy.validate(tempDir, "NEWS/item.json"));
    }

    @Test
    void rejectsLauncherReservedAndStateSuffixFiles() {
        assertThrows(IOException.class, () -> ManifestPathPolicy.validate(tempDir, "manifest.json"));
        assertThrows(IOException.class, () -> ManifestPathPolicy.validate(tempDir, "manifest.json.tmp"));
        assertThrows(IOException.class, () -> ManifestPathPolicy.validate(tempDir, "mod.jar.disabled"));
        assertThrows(IOException.class, () -> ManifestPathPolicy.validate(tempDir, "mod.jar.part"));
    }

    @Test
    void rejectsCaseInsensitiveDuplicatesForCrossPlatformSafety() {
        assertThrows(
                IOException.class,
                () -> ManifestPathPolicy.validateAll(
                        tempDir,
                        List.of("mods/Example.jar", "mods/example.jar")
                )
        );
    }

    @Test
    void returnsNormalizedImmutablePathList() throws Exception {
        List<String> result = ManifestPathPolicy.validateAll(
                tempDir,
                List.of("mods\\one.jar", "config/two.toml")
        );

        assertEquals(List.of("mods/one.jar", "config/two.toml"), result);
        assertThrows(UnsupportedOperationException.class, () -> result.add("another"));
    }
}
