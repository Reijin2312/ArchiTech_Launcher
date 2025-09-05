package org.architech.launcher.neoforge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.architech.launcher.managment.DownloadManager;
import org.architech.launcher.utils.FileEntry;
import org.architech.launcher.gui.LauncherUI;
import org.architech.launcher.utils.LogManager;
import org.architech.launcher.utils.Utils;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class NeoForgeInstaller {

    private static final Path MANIFEST_DIR = Paths.get(".neoforge");
    private static final String MAVEN_METADATA_URL = "https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void ensureFakeLauncherProfile(Path gameDir, String suggestedVersion) throws IOException {
        Path profile = gameDir.resolve("launcher_profiles.json");
        if (Files.exists(profile)) return;

        Files.createDirectories(gameDir);
        Map<String,Object> root = new LinkedHashMap<>();
        Map<String,Object> profiles = new LinkedHashMap<>();
        Map<String,Object> p = new LinkedHashMap<>();
        p.put("name", "NeoForge-Installer-Fake");
        p.put("gameDir", gameDir.toAbsolutePath().toString());
        p.put("lastVersionId", (suggestedVersion == null ? "1.21.1" : suggestedVersion) + "-neoforge");
        p.put("type", "custom");
        profiles.put("NeoForge-Installer-Fake", p);
        root.put("profiles", profiles);
        root.put("selectedProfile", "NeoForge-Installer-Fake");
        root.put("clientToken", UUID.randomUUID().toString());
        root.put("authenticationDatabase", new LinkedHashMap<>());

        String json = GSON.toJson(root);
        Files.writeString(profile, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public static boolean isNeoForgeInstalledAndValid(Path gameDir) {
        Path manifest = gameDir.resolve(MANIFEST_DIR).resolve("installed.json");
        if (!Files.exists(manifest)) return false;
        try {
            InstalledManifest im = GSON.fromJson(Files.readString(manifest, StandardCharsets.UTF_8), InstalledManifest.class);
            for (InstalledManifest.Entry e : im.files) {
                Path p = gameDir.resolve(e.path);
                if (!Files.exists(p)) return false;
                String actual = Utils.sha1Hex(p);
                if (!actual.equalsIgnoreCase(e.sha1)) return false;
            }
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public static void ensureInstalledAndReady(Path gameDir, String mcVersion, LauncherUI ui) throws Exception {
        String pinned = fetchPinnedServerVersionAndSave(gameDir);
        String latest = (pinned != null && !pinned.isBlank()) ? pinned : fetchLatestVersion();
        if (latest == null) {
            LogManager.getLogger().severe("Не удалось получить актуальную версию NeoForge!");
            throw new IOException("Не удалось получить актуальную версию NeoForge!");
        }

        String installedVersion = getInstalledVersion(gameDir);

        if (installedVersion != null && !installedVersion.equals(latest)) {
            if (ui != null) ui.updateProgress("Удаляем старую версию NeoForge " + installedVersion, 0.2);
            uninstallInstalled(gameDir);
        }

        if (isNeoForgeInstalledAndValid(gameDir)) {
            if (ui != null) ui.updateProgress("NeoForge уже установлен и проверен", 1.0);
            return;
        }

        ensureFakeLauncherProfile(gameDir, mcVersion);

        String url = "https://maven.neoforged.net/releases/net/neoforged/neoforge/" + latest + "/neoforge-" + latest + "-installer.jar";
        Path installer = gameDir.resolve("neoforge-installer.jar");
        FileEntry entry = new FileEntry("neoforge", "NeoForge installer", url, installer, 0, null);
        new DownloadManager(ui).ensureFilePresentAndValid(entry);

        Path javaBin = Paths.get(System.getProperty("java.home"), "bin", Utils.isWindows() ? "java.exe" : "java");
        ProcessBuilder pb = new ProcessBuilder(javaBin.toString(), "-jar", installer.toString(), "--installClient", gameDir.toString());
        pb.directory(gameDir.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();

        String output = readProcessAllOutputWithEncodingFallback(p.getInputStream());
        int rc = p.waitFor();

        if (rc != 0) {
            LogManager.getLogger().severe("Упс! Установщик NeoForge сломался :( (exit " + rc + "):\n" + output);
            throw new IOException("Упс! Установщик NeoForge сломался :( (exit " + rc + "):\n" + output);
        }

        InstalledManifest im = scanInstalledNeoForge(gameDir);
        im.version = latest;
        Path manifestDir = gameDir.resolve(MANIFEST_DIR);
        Files.createDirectories(manifestDir);
        Files.writeString(manifestDir.resolve("installed.json"), GSON.toJson(im), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        if (ui != null) ui.updateProgress("NeoForge установлен и проверен", 1.0);
    }

    private static String fetchPinnedServerVersionAndSave(Path gameDir) {
        try {
            String serverPath = "neoforge/version";
            String encoded = encodePathForUri(serverPath);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(org.architech.launcher.MCLauncher.BACKEND_URL + "/api/files/file/" + encoded))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<byte[]> res = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofByteArray());

            if (res.statusCode() != 200) return null;
            String ver = new String(res.body(), StandardCharsets.UTF_8).trim();

            if (ver.isEmpty()) return null;
            Path manifestDir = gameDir.resolve(MANIFEST_DIR);
            Files.createDirectories(manifestDir);
            Files.writeString(manifestDir.resolve("server_version"), ver, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return ver;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
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
                byte[] bytes = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
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

    public static String fetchLatestVersion() throws Exception {
        HttpResponse<String> res;
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(MAVEN_METADATA_URL)).GET().build();
            res = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }
        if (res.statusCode() != 200) return null;

        String xml = res.body();
        int start = xml.indexOf("<release>");
        int end = xml.indexOf("</release>");
        if (start == -1 || end == -1) return null;
        return xml.substring(start + 9, end).trim();
    }

    public static String getInstalledVersion(Path gameDir) {
        Path manifest = gameDir.resolve(MANIFEST_DIR).resolve("installed.json");
        if (!Files.exists(manifest)) return null;
        try {
            InstalledManifest im = GSON.fromJson(Files.readString(manifest, StandardCharsets.UTF_8), InstalledManifest.class);
            return im.version;
        } catch (Exception ex) {
            LogManager.getLogger().severe("Ошибка получения установленной версии neoforge " + gameDir + " " + ex.getMessage());
            return null;
        }
    }

    private static void uninstallInstalled(Path gameDir) {
        Path manifest = gameDir.resolve(MANIFEST_DIR).resolve("installed.json");
        if (!Files.exists(manifest)) return;
        try {
            InstalledManifest im = GSON.fromJson(Files.readString(manifest, StandardCharsets.UTF_8), InstalledManifest.class);
            if (im.files != null) {
                for (InstalledManifest.Entry e : im.files) {
                    try {
                        Files.deleteIfExists(gameDir.resolve(e.path));
                    } catch (Exception ignored) {}
                }
            }
            Utils.deleteDirectory(gameDir.resolve("versions").resolve("neoforge-"+getInstalledVersion(gameDir)));
            Utils.deleteDirectory(gameDir.resolve("libraries/net/neoforged/neoforge").resolve(Objects.requireNonNull(getInstalledVersion(gameDir))));
            Files.deleteIfExists(manifest);
        } catch (Exception ex) {
            LogManager.getLogger().severe("Ошибка удаления старой версии neoforge " + gameDir + " " + ex.getMessage());
        }
    }

    private static String readProcessAllOutputWithEncodingFallback(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int r;
        while ((r = in.read(buf)) != -1) baos.write(buf, 0, r);
        byte[] all = baos.toByteArray();

        String s = new String(all, StandardCharsets.UTF_8);
        if (seemsBinaryOrGarbage(s)) {
            try { s = new String(all, Charset.forName("windows-1251")); } catch (Exception ignored) {}
        }
        if (seemsBinaryOrGarbage(s)) {
            try { s = new String(all, Charset.forName("CP866")); } catch (Exception ignored) {}
        }
        return s;
    }

    private static boolean seemsBinaryOrGarbage(String s) {
        if (s == null || s.isBlank()) return false;
        long repl = s.chars().filter(ch -> ch == '\uFFFD').count();
        if (repl > 3) return true;
        long nonPrintable = s.chars().filter(ch -> ch < 32 && ch != '\n' && ch != '\r' && ch != '\t').count();
        return nonPrintable > 10;
    }

    private static InstalledManifest scanInstalledNeoForge(Path gameDir) throws Exception {
        Path libRoot = gameDir.resolve("libraries").resolve("net").resolve("neoforged");
        InstalledManifest im = new InstalledManifest();
        im.installedAt = Instant.now().toString();
        im.files = new ArrayList<>();
        if (Files.exists(libRoot)) {
            Files.walk(libRoot)
                    .filter(p -> p.toString().endsWith(".jar"))
                    .forEach(p -> {
                        try {
                            String sha = Utils.sha1Hex(p);
                            Path rel = gameDir.relativize(p);
                            InstalledManifest.Entry e = new InstalledManifest.Entry();
                            e.path = rel.toString().replace('\\','/');
                            e.sha1 = sha;
                            im.files.add(e);
                        } catch (Exception ex) {
                            LogManager.getLogger().warning("Пропускаю проблемный файл " + p.getFileName() + " " + ex.getMessage());
                        }
                    });
        }
        im.files = im.files.stream().sorted(Comparator.comparing(a -> a.path)).collect(Collectors.toList());
        return im;
    }

    public static class InstalledManifest {
        public String installedAt;
        public String version;
        public List<Entry> files;
        public static class Entry { public String path; public String sha1; }
    }
}
