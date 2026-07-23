// SPDX-FileCopyrightText: 2026 Raijin2312
// SPDX-License-Identifier: GPL-3.0-only

package org.architech.launcher.managment;

import static org.architech.launcher.ArchiTechLauncher.BACKEND_URL;
import static org.architech.launcher.ArchiTechLauncher.UI;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.architech.launcher.ArchiTechLauncher;
import org.architech.launcher.utils.FileEntry;
import org.architech.launcher.utils.Jsons;
import org.architech.launcher.utils.SafePaths;
import org.architech.launcher.utils.Utils;
import org.architech.launcher.utils.logging.LogManager;

public final class ModsManager {
    private static final long MAX_MANIFEST_BYTES = 4L * 1024L * 1024L;
    private static final int MAX_DOWNLOAD_THREADS = 8;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(networkTimeout())
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private ModsManager() {}

    public static void syncMods(Path gameDir) throws Exception {
        Path root = gameDir.toAbsolutePath().normalize();
        SafePaths.createParentDirectoriesSecurely(root, root.resolve(".root-check"));
        SafePaths.rejectSymbolicLink(root);

        Manifest newManifest = fetchAndValidateManifest(root);
        String manifestJson = Jsons.MAPPER.writeValueAsString(newManifest);
        byte[] manifestBytes = manifestJson.getBytes(StandardCharsets.UTF_8);
        if (manifestBytes.length > MAX_MANIFEST_BYTES) {
            throw new IOException("Manifest is too large after normalization");
        }

        Path localManifest = SafePaths.resolveInside(root, "manifest.json");
        Manifest oldManifest = readLocalManifest(localManifest);

        Set<String> oldPaths = safeOldPaths(root, oldManifest);
        Set<String> newPaths =
                newManifest.files.stream().map(file -> file.path).collect(Collectors.toCollection(HashSet::new));

        deleteRemovedFiles(root, oldPaths, newPaths);

        List<FileEntry> toDownload = new ArrayList<>();
        Map<String, Boolean> desiredDisabledState = new HashMap<>();

        for (FileInfo file : newManifest.files) {
            Path enabled = SafePaths.resolveInside(root, file.path);
            Path disabled = SafePaths.resolveInside(root, file.path + ".disabled");
            SafePaths.verifyNoSymlinkParents(root, enabled);
            SafePaths.verifyNoSymlinkParents(root, disabled);
            rejectExistingSymlink(enabled);
            rejectExistingSymlink(disabled);

            boolean enabledExists = Files.exists(enabled, LinkOption.NOFOLLOW_LINKS);
            boolean disabledExists = Files.exists(disabled, LinkOption.NOFOLLOW_LINKS);
            boolean enabledValid = enabledExists && matchesFile(enabled, file);
            boolean disabledValid = disabledExists && matchesFile(disabled, file);

            if (enabledValid) {
                Files.deleteIfExists(disabled);
                continue;
            }
            if (disabledValid) {
                Files.deleteIfExists(enabled);
                continue;
            }

            // Preserve the user's disabled state only when there is no enabled copy.
            boolean remainDisabled = disabledExists && !enabledExists;
            Path target = remainDisabled ? disabled : enabled;
            desiredDisabledState.put(file.path, remainDisabled);
            SafePaths.createParentDirectoriesSecurely(root, target);

            String url = backendFileUrl(file.path);
            toDownload.add(new FileEntry(
                    "mod", file.path, url, target, file.size, blankToNull(file.sha1), blankToNull(file.sha256)));
        }

        ArchiTechLauncher.DOWNLOAD_MANAGER.resetTotals();
        long planned = ArchiTechLauncher.DOWNLOAD_MANAGER.computeTotalBytesToDownload(toDownload);
        ArchiTechLauncher.DOWNLOAD_MANAGER.setTotalBytesPlanned(planned);

        int threads =
                Math.min(MAX_DOWNLOAD_THREADS, Math.max(2, Runtime.getRuntime().availableProcessors()));
        List<FileEntry> failed =
                ArchiTechLauncher.DOWNLOAD_MANAGER.downloadFilesInParallel(toDownload, threads, 3, true);

        if (!failed.isEmpty()) {
            String names = failed.stream().map(entry -> entry.name).collect(Collectors.joining(", "));
            updateUi("Не удалось скачать: " + names, -1);
            throw new IOException("Не удалось скачать файлы из манифеста: " + names);
        }

        cleanupDuplicateStates(root, desiredDisabledState);
        writeManifestAtomically(localManifest, manifestBytes);
    }

    private static Manifest fetchAndValidateManifest(Path root) throws Exception {
        URI endpoint = URI.create(stripTrailingSlash(BACKEND_URL) + "/api/files/manifest");
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(networkTimeout())
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("manifest HTTP " + response.statusCode());
        }
        if (response.body().length > MAX_MANIFEST_BYTES) {
            throw new IOException("Remote manifest is too large");
        }

        Manifest manifest = Jsons.MAPPER.readValue(response.body(), Manifest.class);
        if (manifest == null) {
            throw new IOException("Manifest parse error: empty JSON");
        }
        if (manifest.files == null) {
            manifest.files = new ArrayList<>();
        }

        List<FileInfo> managedFiles = new ArrayList<>();
        Set<String> collisionKeys = new HashSet<>();
        for (FileInfo file : manifest.files) {
            if (file == null) {
                throw new IOException("Manifest contains a null file entry");
            }
            if (ManifestPathPolicy.isProtectedTopLevel(file.path)) {
                continue;
            }

            String normalizedPath = ManifestPathPolicy.validate(root, file.path);
            String collisionKey = normalizedPath.toLowerCase(Locale.ROOT);
            if (!collisionKeys.add(collisionKey)) {
                throw new IOException("Manifest contains duplicate or case-colliding path: " + file.path);
            }
            if (file.size < 0) {
                throw new IOException("Manifest contains a negative size for " + normalizedPath);
            }
            if (file.size == 0 && blankToNull(file.sha1) == null && blankToNull(file.sha256) == null) {
                throw new IOException("Manifest entry has no integrity metadata: " + normalizedPath);
            }

            file.path = normalizedPath;
            file.sha1 = normalizeHash(file.sha1, 40, "SHA-1", normalizedPath);
            file.sha256 = normalizeHash(file.sha256, 64, "SHA-256", normalizedPath);
            managedFiles.add(file);
        }
        manifest.files = managedFiles;
        return manifest;
    }

    private static Manifest readLocalManifest(Path localManifest) {
        if (!Files.isRegularFile(localManifest, LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }
        try {
            return Jsons.MAPPER.readValue(Files.readAllBytes(localManifest), Manifest.class);
        } catch (Exception failure) {
            LogManager.getLogger()
                    .warning("Локальный manifest.json повреждён и будет проигнорирован: " + failure.getMessage());
            return null;
        }
    }

    private static Set<String> safeOldPaths(Path root, Manifest oldManifest) {
        if (oldManifest == null || oldManifest.files == null) {
            return Collections.emptySet();
        }

        Set<String> paths = new HashSet<>();
        for (FileInfo file : oldManifest.files) {
            if (file == null || ManifestPathPolicy.isProtectedTopLevel(file.path)) {
                continue;
            }
            try {
                paths.add(ManifestPathPolicy.validate(root, file.path));
            } catch (IOException unsafePath) {
                LogManager.getLogger()
                        .warning("Небезопасный путь в локальном manifest.json проигнорирован: " + file.path);
            }
        }
        return paths;
    }

    private static void deleteRemovedFiles(Path root, Set<String> oldPaths, Set<String> newPaths) throws IOException {
        Set<String> toDelete = new HashSet<>(oldPaths);
        toDelete.removeAll(newPaths);

        for (String relative : toDelete) {
            Path enabled = SafePaths.resolveInside(root, relative);
            Path disabled = SafePaths.resolveInside(root, relative + ".disabled");
            deleteRegularFileSafely(root, enabled);
            deleteRegularFileSafely(root, disabled);
        }
    }

    private static void cleanupDuplicateStates(Path root, Map<String, Boolean> desiredDisabledState)
            throws IOException {
        for (Map.Entry<String, Boolean> entry : desiredDisabledState.entrySet()) {
            Path enabled = SafePaths.resolveInside(root, entry.getKey());
            Path disabled = SafePaths.resolveInside(root, entry.getKey() + ".disabled");
            if (entry.getValue()) {
                deleteRegularFileSafely(root, enabled);
            } else {
                deleteRegularFileSafely(root, disabled);
            }
        }
    }

    private static void deleteRegularFileSafely(Path root, Path file) throws IOException {
        SafePaths.verifyNoSymlinkParents(root, file);
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        SafePaths.rejectSymbolicLink(file);
        if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Refusing to delete a directory as a manifest file: " + file);
        }
        Files.delete(file);
    }

    private static void rejectExistingSymlink(Path file) throws IOException {
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            SafePaths.rejectSymbolicLink(file);
        }
    }

    private static boolean matchesFile(Path file, FileInfo expected) throws Exception {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        if (expected.sha256 != null) {
            return expected.sha256.equalsIgnoreCase(Utils.sha256Hex(file));
        }
        if (expected.sha1 != null) {
            return expected.sha1.equalsIgnoreCase(Utils.sha1Hex(file));
        }
        return expected.size > 0 && Files.size(file) == expected.size;
    }

    private static void writeManifestAtomically(Path localManifest, byte[] bytes) throws IOException {
        Path parent = localManifest.getParent();
        if (parent == null) {
            throw new IOException("Manifest path has no parent: " + localManifest);
        }
        Path temporary = parent.resolve("manifest.json.tmp");
        Files.write(
                temporary,
                bytes,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        try {
            Files.move(temporary, localManifest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, localManifest, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String backendFileUrl(String normalizedPath) {
        return stripTrailingSlash(BACKEND_URL) + "/api/files/file/" + encodePathForUri(normalizedPath);
    }

    private static String encodePathForUri(String path) {
        return java.util.Arrays.stream(path.split("/", -1))
                .map(ModsManager::encodeSegment)
                .collect(Collectors.joining("/"));
    }

    private static String encodeSegment(String segment) {
        StringBuilder result = new StringBuilder();
        for (byte value : segment.getBytes(StandardCharsets.UTF_8)) {
            int unsigned = value & 0xFF;
            if (isPathSegmentByteAllowed(unsigned)) {
                result.append((char) unsigned);
            } else {
                result.append('%');
                result.append(Character.toUpperCase(Character.forDigit((unsigned >>> 4) & 0xF, 16)));
                result.append(Character.toUpperCase(Character.forDigit(unsigned & 0xF, 16)));
            }
        }
        return result.toString();
    }

    private static boolean isPathSegmentByteAllowed(int value) {
        return (value >= 'A' && value <= 'Z')
                || (value >= 'a' && value <= 'z')
                || (value >= '0' && value <= '9')
                || value == '-'
                || value == '_'
                || value == '.'
                || value == '~'
                || value == '!'
                || value == '$'
                || value == '&'
                || value == '\''
                || value == '('
                || value == ')'
                || value == '*'
                || value == '+'
                || value == ','
                || value == ';'
                || value == '='
                || value == ':'
                || value == '@';
    }

    private static String normalizeHash(String hash, int expectedLength, String algorithm, String path)
            throws IOException {
        String normalized = blankToNull(hash);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        if (normalized.length() != expectedLength || !normalized.matches("[0-9a-f]+")) {
            throw new IOException("Invalid " + algorithm + " for " + path);
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("BACKEND_URL is not configured");
        }
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static Duration networkTimeout() {
        return Duration.ofSeconds(Math.max(1, ArchiTechLauncher.HTTP_TIMEOUT));
    }

    private static void updateUi(String text, double progress) {
        if (UI != null) {
            UI.updateProgress(text, progress);
        }
    }

    public static class Manifest {
        public List<FileInfo> files;
    }

    public static class FileInfo {
        public String path;
        public long size;
        public String sha1;
        public String sha256;
    }
}
