// SPDX-FileCopyrightText: 2026 Raijin2312
// SPDX-License-Identifier: GPL-3.0-only

package org.architech.launcher.utils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Secure ZIP extraction with Zip Slip, symlink and zip-bomb protection. */
public final class SafeZipExtractor {
    public static final Limits DEFAULT_LIMITS = new Limits(10_000, 256L * 1024L * 1024L, 1024L * 1024L * 1024L);

    private SafeZipExtractor() {}

    public static void extract(Path zipFile, Path destination) throws IOException {
        extract(zipFile, destination, ignored -> true, DEFAULT_LIMITS);
    }

    public static void extract(Path zipFile, Path destination, Predicate<String> includeEntry) throws IOException {
        extract(zipFile, destination, includeEntry, DEFAULT_LIMITS);
    }

    public static void extract(Path zipFile, Path destination, Predicate<String> includeEntry, Limits limits)
            throws IOException {
        Objects.requireNonNull(zipFile, "zipFile");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(includeEntry, "includeEntry");
        Objects.requireNonNull(limits, "limits");

        Path root = destination.toAbsolutePath().normalize();
        SafePaths.createParentDirectoriesSecurely(root, root.resolve(".extract-root-check"));
        SafePaths.rejectSymbolicLink(root);

        int entryCount = 0;
        long totalExtracted = 0;

        try (InputStream fileInput = Files.newInputStream(zipFile);
                ZipInputStream zipInput = new ZipInputStream(new BufferedInputStream(fileInput))) {
            ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > limits.maxEntries()) {
                    throw new IOException("ZIP contains too many entries: " + entryCount);
                }

                String normalizedName = SafePaths.normalizeRelative(entry.getName());
                Path output = SafePaths.resolveInside(root, normalizedName);

                if (entry.isDirectory()) {
                    SafePaths.createParentDirectoriesSecurely(root, output.resolve(".directory-check"));
                    continue;
                }
                if (!includeEntry.test(normalizedName)) {
                    continue;
                }
                if (entry.getSize() > limits.maxEntryBytes()) {
                    throw new IOException("ZIP entry is too large: " + normalizedName);
                }

                SafePaths.createParentDirectoriesSecurely(root, output);
                if (Files.exists(output, LinkOption.NOFOLLOW_LINKS)) {
                    SafePaths.rejectSymbolicLink(output);
                    if (Files.isDirectory(output, LinkOption.NOFOLLOW_LINKS)) {
                        throw new IOException("ZIP file entry collides with a directory: " + normalizedName);
                    }
                }

                Path temp = Files.createTempFile(
                        output.getParent(), output.getFileName().toString() + ".", ".part");
                boolean moved = false;
                try {
                    long entryBytes = copyEntry(zipInput, temp, normalizedName, limits, totalExtracted);
                    totalExtracted = Math.addExact(totalExtracted, entryBytes);
                    moveAtomically(temp, output);
                    moved = true;
                } catch (ArithmeticException overflow) {
                    throw new IOException("ZIP expanded size overflow", overflow);
                } finally {
                    if (!moved) {
                        Files.deleteIfExists(temp);
                    }
                }
            }
        }
    }

    private static long copyEntry(
            ZipInputStream zipInput, Path temp, String entryName, Limits limits, long totalBeforeEntry)
            throws IOException {
        long entryBytes = 0;
        byte[] buffer = new byte[16 * 1024];

        try (OutputStream output = new BufferedOutputStream(
                Files.newOutputStream(temp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING))) {
            int read;
            while ((read = zipInput.read(buffer)) != -1) {
                entryBytes += read;
                if (entryBytes > limits.maxEntryBytes()) {
                    throw new IOException("ZIP entry exceeds size limit: " + entryName);
                }
                if (totalBeforeEntry + entryBytes > limits.maxTotalBytes()) {
                    throw new IOException("ZIP exceeds total expanded-size limit");
                }
                output.write(buffer, 0, read);
            }
        }
        return entryBytes;
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public record Limits(int maxEntries, long maxEntryBytes, long maxTotalBytes) {
        public Limits {
            if (maxEntries <= 0) {
                throw new IllegalArgumentException("maxEntries must be positive");
            }
            if (maxEntryBytes <= 0) {
                throw new IllegalArgumentException("maxEntryBytes must be positive");
            }
            if (maxTotalBytes <= 0) {
                throw new IllegalArgumentException("maxTotalBytes must be positive");
            }
            if (maxTotalBytes < maxEntryBytes) {
                throw new IllegalArgumentException("maxTotalBytes must be >= maxEntryBytes");
            }
        }
    }
}
