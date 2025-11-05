package org.architech.launcher.managment;

import org.architech.launcher.ArchiTechLauncher;
import org.architech.launcher.utils.FileEntry;
import org.architech.launcher.utils.Jsons;
import org.architech.launcher.utils.logging.LogManager;
import org.architech.launcher.utils.Utils;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.architech.launcher.ArchiTechLauncher.BACKEND_URL;
import static org.architech.launcher.ArchiTechLauncher.UI;

public class ModsManager {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(ArchiTechLauncher.HTTP_TIMEOUT)).build();

    public static void syncMods(Path modsDir) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BACKEND_URL + "/api/files/manifest")).
                timeout(Duration.ofSeconds(ArchiTechLauncher.HTTP_TIMEOUT)).
                GET().build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() != 200) {
            LogManager.getLogger().severe("manifest HTTP " + res.statusCode() + ": " + res.body());
            throw new IOException("manifest HTTP " + res.statusCode() + ": " + res.body());
        }

        String manifestJson = res.body();
        Manifest newManifest = Jsons.MAPPER.readValue(res.body(), Manifest.class);

        if (newManifest == null) {
            LogManager.getLogger().severe("manifest parse error: empty/invalid JSON");
            throw new IOException("manifest parse error: empty/invalid JSON");
        }

        if (newManifest.files == null) {
            newManifest.files = new ArrayList<>();
        }

        newManifest.files = newManifest.files.stream()
                .filter(f -> !normalizePath(f.path).startsWith("launcher/"))
                .collect(Collectors.toList());
        newManifest.files = newManifest.files.stream()
                .filter(f -> !normalizePath(f.path).startsWith("neoforge/"))
                .collect(Collectors.toList());
        newManifest.files = newManifest.files.stream()
                .filter(f -> !normalizePath(f.path).startsWith("news/"))
                .collect(Collectors.toList());

        Files.createDirectories(modsDir);

        Path localManifest = modsDir.resolve("manifest.json");
        Manifest oldManifest = null;
        if (Files.exists(localManifest)) {
            try {
                String oldJson = Files.readString(localManifest, StandardCharsets.UTF_8);
                oldManifest = Jsons.MAPPER.readValue(oldJson, Manifest.class);
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
                if (UI != null) UI.updateProgress("Ошибка при удалении мода: " + rel + " — " + ex.getMessage(), -1);
                LogManager.getLogger().severe("Ошибка при удалении мода: " + rel + " — " + ex.getMessage());
            }
        }

        List<FileEntry> toDownload = new java.util.ArrayList<>();

        assert newManifest.files != null;
        for (FileInfo f : newManifest.files) {
            String relPath = normalizePath(f.path);
            Path enabled  = modsDir.resolve(relPath);
            Path disabled = modsDir.resolve(relPath + ".disabled");

            boolean enabledExists  = Files.exists(enabled);
            boolean disabledExists = Files.exists(disabled);

            boolean validPresent = false;
            try {
                if (enabledExists) {
                    if (matchesFile(enabled, f)) validPresent = true;
                } else if (disabledExists) {
                    if (matchesFile(disabled, f)) validPresent = true;
                }
            } catch (Exception ignored) {}

            if (validPresent) {
                try { if (enabledExists && disabledExists) Files.deleteIfExists(disabled); } catch (Exception ignored) {}
                continue;
            }

            Path target = disabledExists ? disabled : enabled;
            String url = BACKEND_URL + "/api/files/file/" + encodePathForUri(f.path.replace("\\","/"));

            toDownload.add(new FileEntry("mod", relPath, url, target, Math.max(0, f.size), (f.sha1 != null && !f.sha1.isBlank()) ? f.sha1 : null
            ));
        }

        try {
            ArchiTechLauncher.DOWNLOAD_MANAGER.resetTotals();
        } catch (Throwable ignored) {}

        long planned = ArchiTechLauncher.DOWNLOAD_MANAGER.computeTotalBytesToDownload(toDownload);
        ArchiTechLauncher.DOWNLOAD_MANAGER.setTotalBytesPlanned(planned);

        int threads = Math.max(2, Runtime.getRuntime().availableProcessors());
        java.util.List<FileEntry> failed =
                ArchiTechLauncher.DOWNLOAD_MANAGER.downloadFilesInParallel(toDownload, threads, 3, true);

        boolean allOk = failed.isEmpty();
        if (!allOk && UI != null) {
            UI.updateProgress("Не удалось скачать: " +
                    failed.stream().map(fe -> fe.name).collect(java.util.stream.Collectors.joining(", ")), -1);
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
                if (UI != null) UI.updateProgress("Не удалось обновить локальный манифест: " + ex.getMessage(), -1);
            }
        } else {
            if (UI != null) UI.updateProgress("Синхронизация модов завершилась с ошибками. Локальный манифест не изменён.", -1);
        }
    }

    private static String normalizePath(String p) {
        return p == null ? "" : p.replace("\\", "/");
    }

    private static boolean matchesFile(Path file, FileInfo expected) throws Exception {
        if (!Files.exists(file)) return false;
        if (expected.sha256 != null && !expected.sha256.isBlank()) {
            return expected.sha256.equalsIgnoreCase(Utils.sha256Hex(file));
        } else if (expected.sha1 != null && !expected.sha1.isBlank()) {
            return expected.sha1.equalsIgnoreCase(Utils.sha1Hex(file));
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
        public String sha256;
    }
}
