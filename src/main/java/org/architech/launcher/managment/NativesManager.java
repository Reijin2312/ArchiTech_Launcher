package org.architech.launcher.managment;

import org.architech.launcher.utils.FileEntry;

import java.nio.file.*;
import java.util.List;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;
import java.io.*;

public record NativesManager(Path gameDir, String version) {

    public void prepareNatives(List<FileEntry> files) throws Exception {
        Path nativesRoot = gameDir.resolve("natives").resolve(version);
        Files.createDirectories(nativesRoot);
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(nativesRoot)) {
            for (Path p : ds) Files.deleteIfExists(p);
        } catch (IOException ignored) {
        }
        for (FileEntry f : files) {
            if (!"natives".equals(f.kind)) continue;
            if (!Files.exists(f.path)) continue;
            unzip(f.path, nativesRoot);
        }
    }

    private void unzip(Path zipFile, Path destDir) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                Path out = destDir.resolve(entry.getName()).normalize();
                Files.createDirectories(out.getParent());
                try (OutputStream os = Files.newOutputStream(out, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = zis.read(buf)) != -1) os.write(buf, 0, len);
                }
            }
        }
    }
}