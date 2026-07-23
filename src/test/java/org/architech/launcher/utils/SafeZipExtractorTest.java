// SPDX-FileCopyrightText: 2026 Raijin2312
// SPDX-License-Identifier: GPL-3.0-only

package org.architech.launcher.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeZipExtractorTest {
    @TempDir
    Path tempDir;

    @Test
    void extractsNestedFiles() throws Exception {
        Path zip = createZip(Map.of(
                "bin/native.dll", "native-data".getBytes(StandardCharsets.UTF_8),
                "readme.txt", "hello".getBytes(StandardCharsets.UTF_8)
        ));
        Path destination = tempDir.resolve("out");

        SafeZipExtractor.extract(zip, destination);

        assertEquals("native-data", Files.readString(destination.resolve("bin/native.dll")));
        assertEquals("hello", Files.readString(destination.resolve("readme.txt")));
    }

    @Test
    void blocksZipSlipTraversal() throws Exception {
        Path zip = createZip(Map.of(
                "../escaped.txt", "owned".getBytes(StandardCharsets.UTF_8)
        ));
        Path destination = tempDir.resolve("out");

        assertThrows(IOException.class, () -> SafeZipExtractor.extract(zip, destination));
        assertFalse(Files.exists(tempDir.resolve("escaped.txt")));
    }

    @Test
    void blocksBackslashTraversal() throws Exception {
        Path zip = createZip(Map.of(
                "..\\escaped.txt", "owned".getBytes(StandardCharsets.UTF_8)
        ));

        assertThrows(
                IOException.class,
                () -> SafeZipExtractor.extract(zip, tempDir.resolve("out"))
        );
    }

    @Test
    void canExcludeMetaInfEntries() throws Exception {
        Path zip = createZip(Map.of(
                "META-INF/MANIFEST.MF", "metadata".getBytes(StandardCharsets.UTF_8),
                "native.dll", "native".getBytes(StandardCharsets.UTF_8)
        ));
        Path destination = tempDir.resolve("out");

        SafeZipExtractor.extract(
                zip,
                destination,
                name -> !name.startsWith("META-INF/")
        );

        assertFalse(Files.exists(destination.resolve("META-INF/MANIFEST.MF")));
        assertTrue(Files.exists(destination.resolve("native.dll")));
    }

    @Test
    void enforcesPerEntryLimitAndDeletesTemporaryFile() throws Exception {
        Path zip = createZip(Map.of(
                "large.bin", new byte[64]
        ));
        Path destination = tempDir.resolve("out");
        SafeZipExtractor.Limits limits = new SafeZipExtractor.Limits(10, 32, 128);

        assertThrows(
                IOException.class,
                () -> SafeZipExtractor.extract(zip, destination, ignored -> true, limits)
        );
        assertFalse(Files.exists(destination.resolve("large.bin")));
        if (Files.exists(destination)) {
            try (var files = Files.list(destination)) {
                assertFalse(files.anyMatch(path -> path.getFileName().toString().endsWith(".part")));
            }
        }
    }

    @Test
    void enforcesTotalExpandedSizeLimit() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("one.bin", new byte[30]);
        entries.put("two.bin", new byte[30]);
        Path zip = createZip(entries);
        SafeZipExtractor.Limits limits = new SafeZipExtractor.Limits(10, 40, 50);

        assertThrows(
                IOException.class,
                () -> SafeZipExtractor.extract(
                        zip,
                        tempDir.resolve("out"),
                        ignored -> true,
                        limits
                )
        );
    }

    @Test
    void refusesExistingSymlinkParentWhenSupported() throws Exception {
        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        Path destination = Files.createDirectory(tempDir.resolve("out"));
        try {
            Files.createSymbolicLink(destination.resolve("link"), outside);
        } catch (UnsupportedOperationException | IOException | SecurityException unsupported) {
            return;
        }
        Path zip = createZip(Map.of(
                "link/escaped.txt", "owned".getBytes(StandardCharsets.UTF_8)
        ));

        assertThrows(IOException.class, () -> SafeZipExtractor.extract(zip, destination));
        assertFalse(Files.exists(outside.resolve("escaped.txt")));
    }

    private Path createZip(Map<String, byte[]> entries) throws IOException {
        Path zip = Files.createTempFile(tempDir, "archive-", ".zip");
        try (OutputStream fileOutput = Files.newOutputStream(zip);
             ZipOutputStream zipOutput = new ZipOutputStream(fileOutput)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zipOutput.putNextEntry(new ZipEntry(entry.getKey()));
                zipOutput.write(entry.getValue());
                zipOutput.closeEntry();
            }
        }
        return zip;
    }
}
