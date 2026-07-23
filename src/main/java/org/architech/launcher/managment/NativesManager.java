package org.architech.launcher.managment;

import org.architech.launcher.utils.FileEntry;
import org.architech.launcher.utils.SafePaths;
import org.architech.launcher.utils.SafeZipExtractor;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

public record NativesManager(Path gameDir, String version) {
    public NativesManager {
        if (gameDir == null) {
            throw new IllegalArgumentException("gameDir must not be null");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }
    }

    public void prepareNatives(List<FileEntry> files) throws Exception {
        Path gameRoot = gameDir.toAbsolutePath().normalize();
        String relativeNatives = "natives/" + SafePaths.normalizeRelative(version);
        Path nativesRoot = SafePaths.resolveInside(gameRoot, relativeNatives);
        SafePaths.verifyNoSymlinkParents(gameRoot, nativesRoot);
        SafePaths.rejectSymbolicLink(nativesRoot);
        recreateDirectory(nativesRoot);

        if (files == null) {
            return;
        }
        for (FileEntry file : files) {
            if (file == null || !"natives".equals(file.kind)) {
                continue;
            }
            if (file.path == null || !Files.isRegularFile(file.path)) {
                continue;
            }
            SafeZipExtractor.extract(
                    file.path,
                    nativesRoot,
                    name -> !name.startsWith("META-INF/")
            );
        }
    }

    private static void recreateDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            Files.walkFileTree(directory, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException failure) throws IOException {
                    if (failure != null) {
                        throw failure;
                    }
                    if (!dir.equals(directory)) {
                        Files.delete(dir);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        Files.createDirectories(directory);
    }
}
