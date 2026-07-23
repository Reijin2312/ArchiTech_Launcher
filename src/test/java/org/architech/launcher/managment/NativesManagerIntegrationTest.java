package org.architech.launcher.managment;

import org.architech.launcher.utils.FileEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NativesManagerIntegrationTest {
    @TempDir
    Path tempDir;

    @Test
    void removesStaleNestedFilesAndExtractsCurrentNatives() throws Exception {
        Path stale = tempDir.resolve("natives/test-version/old/nested/stale.dll");
        Files.createDirectories(stale.getParent());
        Files.writeString(stale, "stale");

        Path zip = createZip(Map.of(
                "current/native.dll", "current".getBytes(StandardCharsets.UTF_8),
                "META-INF/MANIFEST.MF", "ignored".getBytes(StandardCharsets.UTF_8)
        ));
        FileEntry entry = new FileEntry("natives", "natives", "unused", zip, Files.size(zip), null);

        new NativesManager(tempDir, "test-version").prepareNatives(List.of(entry));

        Path root = tempDir.resolve("natives/test-version");
        assertFalse(Files.exists(stale));
        assertEquals("current", Files.readString(root.resolve("current/native.dll")));
        assertFalse(Files.exists(root.resolve("META-INF/MANIFEST.MF")));
    }

    @Test
    void blocksZipSlipThroughManagerIntegration() throws Exception {
        Path zip = createZip(Map.of(
                "../../escaped.dll", "owned".getBytes(StandardCharsets.UTF_8)
        ));
        FileEntry entry = new FileEntry("natives", "natives", "unused", zip, Files.size(zip), null);

        assertThrows(
                IOException.class,
                () -> new NativesManager(tempDir, "test-version").prepareNatives(List.of(entry))
        );
        assertFalse(Files.exists(tempDir.resolve("escaped.dll")));
    }

    private Path createZip(Map<String, byte[]> entries) throws IOException {
        Path zip = Files.createTempFile(tempDir, "natives-", ".zip");
        try (OutputStream output = Files.newOutputStream(zip);
             ZipOutputStream zipOutput = new ZipOutputStream(output)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zipOutput.putNextEntry(new ZipEntry(entry.getKey()));
                zipOutput.write(entry.getValue());
                zipOutput.closeEntry();
            }
        }
        return zip;
    }
}
