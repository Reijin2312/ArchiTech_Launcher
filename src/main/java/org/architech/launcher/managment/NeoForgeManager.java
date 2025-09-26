package org.architech.launcher.managment;

import org.architech.launcher.ArchiTechLauncher;
import org.architech.launcher.utils.FileEntry;
import org.architech.launcher.utils.Jsons;
import org.architech.launcher.utils.logging.LogManager;
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

import static org.architech.launcher.ArchiTechLauncher.UI;

public class NeoForgeManager {

    private static final Path MANIFEST_DIR = Paths.get(".neoforge");

    public static void ensureFakeLauncherProfile(Path gameDir, String suggestedVersion) throws IOException {
        Path profile = gameDir.resolve("launcher_profiles.json");
        if (Files.exists(profile)) return;
        LogManager.getLogger().info("Создаю фейковый профиль лаунчера...");
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

        String json = Jsons.PRETTY.writeValueAsString(root);
        Files.writeString(profile, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public static boolean isNeoForgeInstalledAndValid(Path gameDir) {
        LogManager.getLogger().info("Проверяю валидность установки neoforge...");
        Path manifest = gameDir.resolve(MANIFEST_DIR).resolve("installed.json");
        if (!Files.exists(manifest)) {
            LogManager.getLogger().severe("installed.json не найден: Neoforge установлен неправильно ;(");
            return false;
        }
        try {
            String json = Files.readString(manifest, StandardCharsets.UTF_8);
            if (json.isBlank() || json.trim().equalsIgnoreCase("null")) {
                LogManager.getLogger().severe("installed.json пуст или некорректен");
                return false;
            }
            InstalledManifest im = Jsons.MAPPER.readValue(json, InstalledManifest.class);
            if (im == null) {
                LogManager.getLogger().severe("installed.json распарсился в null");
                return false;
            }
            if (im.files == null || im.files.isEmpty()) {
                LogManager.getLogger().severe("installed.json не содержит списка файлов — установка неполная");
                return false;
            }
            for (InstalledManifest.Entry e : im.files) {
                if (e == null || e.path == null || e.sha1 == null) {
                    LogManager.getLogger().warning("Найдена некорректная запись в installed.json, считаю установку невалидной.");
                    return false;
                }
                Path p = gameDir.resolve(e.path);
                if (!Files.exists(p)) {
                    LogManager.getLogger().severe("Neoforge установлен неправильно ;( отсутствует " + p);
                    return false;
                }
                String actual = Utils.sha1Hex(p);
                if (!actual.equalsIgnoreCase(e.sha1)) {
                    LogManager.getLogger().severe("Neoforge установлен неправильно ;( sha1 не совпадает для " + p);
                    return false;
                }
            }
            LogManager.getLogger().info("Neoforge установлен правильно");
            return true;
        } catch (Exception ex) {
            LogManager.getLogger().severe("Neoforge установлен неправильно ;( " + ex.getMessage());
            return false;
        }
    }


    public static void ensureInstalledAndReady(Path gameDir, String mcVersion) throws Exception {
        String latest = fetchPinnedServerVersionAndSave(gameDir);

        if (latest == null) {
            LogManager.getLogger().severe("Не удалось получить актуальную версию NeoForge!");
            throw new IOException("Не удалось получить актуальную версию NeoForge!");
        }

        String installedVersion = getInstalledVersion(gameDir);

        if (installedVersion != null && !installedVersion.equals(latest)) {
            if (UI != null) UI.updateProgress("Удаляем старую версию NeoForge..." + installedVersion, 0.2);
            uninstallInstalled(gameDir);
        }

        if (isNeoForgeInstalledAndValid(gameDir)) {
            if (UI != null) UI.updateProgress("NeoForge уже установлен и проверен", 1.0);
            return;
        }

        ensureFakeLauncherProfile(gameDir, mcVersion);

        try {
            Files.createDirectories(gameDir);
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(gameDir, "neoforge-installer.jar")) {
                for (Path p : ds) {
                    try {
                        Files.deleteIfExists(p);
                        if (UI != null) UI.updateProgress("Удаляю старый инсталлятор", 0.3);
                        LogManager.getLogger().info("Удаляю старый инсталлятор: " + p);
                    } catch (Exception ignore) {}
                }
            }
        } catch (Exception e) {
            LogManager.getLogger().warning("Не удалось подчистить старые инсталляторы: " + e.getMessage());
        }

        String serverInstallerPath = "neoforge/neoforge-" + latest + "-installer.jar";
        String encoded = encodePathForUri(serverInstallerPath);
        String url = ArchiTechLauncher.BACKEND_URL + "/api/files/file/" + encoded;
        Path installer = gameDir.resolve("neoforge-installer.jar");
        FileEntry entry = new FileEntry("neoforge", "NeoForge installer", url, installer, 0, null);

        ArchiTechLauncher.DOWNLOAD_MANAGER.ensureFilePresentAndValid(entry, true);

        if (UI != null) UI.updateProgress("Установка neoforge...", 0.5);

        String javaBin = Utils.isWindows() ? "java.exe" : "java";
        ProcessBuilder pb = new ProcessBuilder(ArchiTechLauncher.JAVA_PATH.resolve("bin").resolve(javaBin).toString(), "-jar", installer.toString(), "--installClient", gameDir.toString());

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

        if (im.files == null || im.files.isEmpty()) {
            LogManager.getLogger().severe("Инсталлятор не создал ни одного файла neoforge, откат...");
            throw new IOException("Инсталлятор не создал ни одного файла neoforge, откат...");
        }

        boolean foundVersion = im.files.stream().anyMatch(e -> e.path.contains("/libraries/net/neoforged/neoforge/" + latest) ||
                        e.path.contains("/versions/neoforge-" + latest + "/")
        );

        if (!foundVersion) {
            LogManager.getLogger().warning("Не обнаружено явного следа версии " + latest + " в scanInstalledNeoForge — но продолжаю, т.к. файлы есть.");
        }

        im.version = latest;
        Path manifestDir = gameDir.resolve(MANIFEST_DIR);
        Files.createDirectories(manifestDir);

        Path out = manifestDir.resolve("installed.json");
        Path tmp = manifestDir.resolve("installed.json.tmp");
        Files.writeString(tmp, Jsons.PRETTY.writeValueAsString(im), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        try {
            Files.move(tmp, out, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ex) {
            Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
        }
        LogManager.getLogger().info("NeoForge установлен, записан manifest (" + im.files.size() + " файлов) версия=" + im.version);


        if (UI != null) UI.updateProgress("NeoForge установлен и проверен", 1.0);
    }

    private static String fetchPinnedServerVersionAndSave(Path gameDir) {
        if (UI != null) UI.updateProgress("Проверяю версию neoforge на сервере...", 0);
        LogManager.getLogger().info("Проверяю версию neoforge на сервере...");
        try {
            String serverPath = "neoforge/version";
            String encoded = encodePathForUri(serverPath);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(ArchiTechLauncher.BACKEND_URL + "/api/files/file/" + encoded))
                    .timeout(Duration.ofSeconds(ArchiTechLauncher.HTTP_TIMEOUT))
                    .GET()
                    .build();
            HttpResponse<byte[]> res = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofByteArray());

            if (res.statusCode() != 200) return null;
            String ver = new String(res.body(), StandardCharsets.UTF_8).trim();

            if (ver.isEmpty()) return null;
            Path manifestDir = gameDir.resolve(MANIFEST_DIR);
            Files.createDirectories(manifestDir);
            Files.writeString(manifestDir.resolve("server_version"), ver, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            LogManager.getLogger().info("Версия neoforge на сервере: " + ver);
            return ver;
        } catch (Exception ex) {
            LogManager.getLogger().severe("Ошибка проверки версии neoforge на сервере ;( " + ex.getMessage());
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
        return c == '!' || c == '$' || c == '&' || c == '\'' || c == '(' || c == ')' ||
                c == '*' || c == '+' || c == ',' || c == ';' || c == '=' || c == ':' || c == '@';
    }

    public static String getInstalledVersion(Path gameDir) {
        Path manifest = gameDir.resolve(MANIFEST_DIR).resolve("installed.json");
        if (!Files.exists(manifest)) return null;
        try {
            String json = Files.readString(manifest, StandardCharsets.UTF_8);
            if (json.isBlank() || json.trim().equalsIgnoreCase("null")) {
                LogManager.getLogger().warning("installed.json пуст или содержит 'null'");
                return null;
            }
            InstalledManifest im = Jsons.MAPPER.readValue(json, InstalledManifest.class);
            if (im == null || im.version == null || im.version.isBlank()) {
                LogManager.getLogger().warning("installed.json не содержит поле version или повреждён");
                return null;
            }
            LogManager.getLogger().info("Установленная версия neoforge " + gameDir + " " + im.version);
            return im.version;
        } catch (Exception ex) {
            LogManager.getLogger().severe("Ошибка получения установленной версии neoforge " + gameDir + " " + ex.getMessage());
            return null;
        }
    }

    private static void uninstallInstalled(Path gameDir) {
        Path manifest = gameDir.resolve(MANIFEST_DIR).resolve("installed.json");
        String installedVersion = null;
        try {
            if (Files.exists(manifest)) {
                InstalledManifest im = Jsons.MAPPER.readValue(Files.readString(manifest, StandardCharsets.UTF_8), InstalledManifest.class);
                installedVersion = (im != null) ? im.version : null;
            }
        } catch (Exception e) {
            LogManager.getLogger().warning("Не удалось прочитать installed.json: " + e.getMessage());
        }

        if (installedVersion == null || installedVersion.isBlank()) {
            try {
                Path versionsRoot = gameDir.resolve("versions");
                if (Files.exists(versionsRoot)) {
                    Optional<Path> last = Files.list(versionsRoot)
                            .filter(p -> p.getFileName().toString().startsWith("neoforge-"))
                            .max(Comparator.naturalOrder());
                    if (last.isPresent()) {
                        installedVersion = last.get().getFileName().toString().substring("neoforge-".length());
                        LogManager.getLogger().info("uninstallInstalled: обнаружена версия для удаления из папки versions: " + installedVersion);
                    }
                }
            } catch (Exception e) {
                LogManager.getLogger().warning("Не удалось найти версии в versions/: " + e.getMessage());
            }
        }

        if (installedVersion != null && !installedVersion.isBlank()) {
            try {
                Utils.deleteDirectory(gameDir.resolve("versions").resolve("neoforge-" + installedVersion));
            } catch (Exception ex) {
                LogManager.getLogger().warning("Не удалось удалить versions/neoforge-" + installedVersion + " : " + ex.getMessage());
            }
            try {
                Utils.deleteDirectory(gameDir.resolve("libraries/net/neoforged/neoforge").resolve(installedVersion));
            } catch (Exception ex) {
                LogManager.getLogger().warning("Не удалось удалить libraries/net/neoforged/neoforge/" + installedVersion + " : " + ex.getMessage());
            }
        } else {
            LogManager.getLogger().warning("Не найдено, какую версию удалять — пропускаю удаление директорий версий.");
        }

        try { Files.deleteIfExists(manifest); } catch (Exception e) { LogManager.getLogger().warning("Не удалось удалить installed.json: " + e.getMessage()); }
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
        Path versionsRoot = gameDir.resolve("versions");
        InstalledManifest im = new InstalledManifest();
        im.installedAt = Instant.now().toString();
        im.files = new ArrayList<>();

        java.util.function.Consumer<Path> add = p -> {
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
        };

        if (Files.exists(libRoot)) {
            Files.walk(libRoot)
                    .filter(p -> p.toString().endsWith(".jar"))
                    .forEach(add);
        }

        if (Files.exists(versionsRoot)) {
            try {
                Files.list(versionsRoot)
                        .filter(p -> p.getFileName().toString().startsWith("neoforge-"))
                        .forEach(vdir -> {
                            try {
                                Files.walk(vdir)
                                        .filter(p -> p.toString().endsWith(".jar") || p.toString().endsWith(".json"))
                                        .forEach(add);
                            } catch (Exception e) {
                                LogManager.getLogger().warning("Ошибка сканирования " + vdir + " : " + e.getMessage());
                            }
                        });
            } catch (Exception e) {
                LogManager.getLogger().warning("Не удалось просканировать versions/: " + e.getMessage());
            }
        }

        im.files = im.files.stream().sorted(Comparator.comparing(a -> a.path)).collect(Collectors.toList());
        LogManager.getLogger().info("scanInstalledNeoForge: найдено файлов = " + im.files.size());
        return im;
    }

    public static class InstalledManifest {
        public String installedAt;
        public String version;
        public List<Entry> files;
        public static class Entry { public String path; public String sha1; }
    }
}
