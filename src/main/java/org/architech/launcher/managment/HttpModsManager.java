package org.architech.launcher.managment;

import com.google.gson.Gson;
import org.architech.launcher.gui.LauncherUI;
import org.architech.launcher.utils.Utils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.architech.launcher.MCLauncher.BACKEND_URL;

public class HttpModsManager {
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Gson GSON = new Gson();

    public static void syncMods(Path modsDir, LauncherUI ui) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BACKEND_URL + "/api/files/manifest"))
                .GET()
                .build();
        String manifestJson = HTTP.send(req, HttpResponse.BodyHandlers.ofString()).body();
        Manifest newManifest = GSON.fromJson(manifestJson, Manifest.class);
        if (newManifest == null || newManifest.files == null) newManifest = new Manifest();

        if (newManifest.files != null) {
            newManifest.files = newManifest.files.stream()
                    .filter(f -> !normalizePath(f.path).startsWith("launcher/"))
                    .collect(Collectors.toList());
        }

        Files.createDirectories(modsDir);

        Path localManifest = modsDir.resolve("manifest.json");
        Manifest oldManifest = null;
        if (Files.exists(localManifest)) {
            try {
                String oldJson = Files.readString(localManifest, StandardCharsets.UTF_8);
                oldManifest = GSON.fromJson(oldJson, Manifest.class);
            } catch (Exception ignored) {}
        }

        Set<String> oldPaths = (oldManifest == null || oldManifest.files == null)
                ? Collections.emptySet()
                : oldManifest.files.stream().map(f -> normalizePath(f.path)).collect(Collectors.toSet());

        Set<String> newPaths = (newManifest.files == null)
                ? Collections.emptySet()
                : newManifest.files.stream().map(f -> normalizePath(f.path)).collect(Collectors.toSet());

        Set<String> toDelete = new HashSet<>(oldPaths);
        toDelete.removeAll(newPaths);

        for (String rel : toDelete) {
            try {
                Path enabled = modsDir.resolve(rel);
                Path disabled = modsDir.resolve(rel + ".disabled");
                Files.deleteIfExists(enabled);
                Files.deleteIfExists(disabled);
            } catch (Exception ex) {
                if (ui != null) ui.updateProgress("Ошибка при удалении мода: " + rel + " — " + ex.getMessage(), -1);
            }
        }

        assert newManifest.files != null;
        long totalBytes = newManifest.files.stream().mapToLong(f -> Math.max(0, f.size)).sum();
        long downloaded = 0;
        boolean allOk = true;

        for (FileInfo f : newManifest.files) {
            String relPath = normalizePath(f.path);
            Path enabled = modsDir.resolve(relPath);
            Path disabled = modsDir.resolve(relPath + ".disabled");

            boolean enabledExists = Files.exists(enabled);
            boolean disabledExists = Files.exists(disabled);

            boolean validPresent = false;
            try {
                if (enabledExists) {
                    if (matchesFile(enabled, f)) validPresent = true;
                } else if (disabledExists) {
                    if (matchesFile(disabled, f)) validPresent = true;
                }
            } catch (Exception ex) {}

            if (validPresent) {
                try {
                    if (enabledExists && disabledExists) Files.deleteIfExists(disabled);
                } catch (Exception ignored) {}
                continue;
            }

            boolean shouldPreserveDisabled = disabledExists;

            if (ui != null) ui.updateProgress("Скачивание файла: " + relPath, totalBytes > 0 ? (double) downloaded / totalBytes : -1);

            try {
                String pathForUrl = f.path.replace("\\", "/");
                String encodedPath = encodePathForUri(pathForUrl);
                HttpRequest dlReq = HttpRequest.newBuilder()
                        .uri(URI.create(BACKEND_URL + "/api/files/file/" + encodedPath))
                        .GET()
                        .build();
                byte[] data = HTTP.send(dlReq, HttpResponse.BodyHandlers.ofByteArray()).body();

                Path target = shouldPreserveDisabled ? disabled : enabled;
                Files.createDirectories(target.getParent());
                Files.write(target, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

                if (!matchesFile(target, f)) {
                    allOk = false;
                    if (ui != null) ui.updateProgress("Скачанный файл не прошёл проверку: " + relPath, -1);
                    break;
                }

                downloaded += Math.max(0, f.size);

            } catch (Exception ex) {
                allOk = false;
                if (ui != null) ui.updateProgress("Ошибка при скачивании: " + relPath + " — " + ex.getMessage(), -1);
                break;
            }
        }

        if (allOk) {
            try {
                Path tmp = modsDir.resolve("manifest.json.tmp");
                Files.writeString(tmp, manifestJson, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                try {
                    Files.move(tmp, localManifest, REPLACE_EXISTING, ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException amnse) {
                    Files.move(tmp, localManifest, REPLACE_EXISTING);
                }
            } catch (Exception ex) {
                if (ui != null) ui.updateProgress("Не удалось обновить локальный манифест: " + ex.getMessage(), -1);
            }
        } else {
            if (ui != null) ui.updateProgress("Синхронизация модов завершилась с ошибками. Локальный манифест не изменён.", -1);
        }
    }

    private static String normalizePath(String p) {
        return p == null ? "" : p.replace("\\", "/");
    }

    private static boolean matchesFile(Path file, FileInfo expected) throws Exception {
        if (!Files.exists(file)) return false;
        if (expected.sha1 != null && !expected.sha1.isBlank()) {
            String actual = Utils.sha1Hex(file);
            return expected.sha1.equalsIgnoreCase(actual);
        } else if (expected.size > 0) {
            return Files.size(file) == expected.size;
        } else {
            return true;
        }
    }

    private static String encodePathForUri(String path) {
        String[] segments = path.split("/");
        StringBuilder encoded = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) encoded.append("/");
            encoded.append(encodeSegment(segments[i]));
        }
        return encoded.toString();
    }

    private static String encodeSegment(String segment) {
        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < segment.length(); j++) {
            char c = segment.charAt(j);
            if (isAllowedInPath(c)) {
                sb.append(c);
            } else {
                byte[] bytes = String.valueOf(c).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                for (byte b : bytes) sb.append(String.format("%%%02X", b));
            }
        }
        return sb.toString();
    }

    private static boolean isAllowedInPath(char c) {
        if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') ||
                c == '-' || c == '_' || c == '.' || c == '~') return true;
        if (c == '!' || c == '$' || c == '&' || c == '\'' || c == '(' || c == ')' ||
                c == '*' || c == '+' || c == ',' || c == ';' || c == '=' || c == ':' || c == '@') return true;
        return false;
    }

    public static class Manifest {
        public List<FileInfo> files;
    }

    public static class FileInfo {
        public String path;
        public long size;
        public String sha1;
    }
}
