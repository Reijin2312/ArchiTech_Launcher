package org.architech.launcher.managment;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import org.architech.launcher.utils.FileEntry;
import org.architech.launcher.utils.LogManager;
import org.architech.launcher.utils.UtilsNet;
import java.io.StringReader;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;

import static java.nio.file.StandardOpenOption.*;

public class VersionManager {
    private final Path versionsDir;
    private final Path assetsDir;
    private final Path librariesDir;

    public VersionManager(Path versionsDir, Path assetsDir, Path librariesDir) {
        this.versionsDir = versionsDir;
        this.assetsDir = assetsDir;
        this.librariesDir = librariesDir;
    }

    public JsonObject loadVersionJson(String versionId) throws Exception {
        Files.createDirectories(versionsDir.resolve(versionId));
        String manifestUrl = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
        String manifestStr = UtilsNet.readUrl(manifestUrl);

        JsonObject manifest;
        try (JsonReader reader = new JsonReader(new StringReader(manifestStr))) {
            reader.setLenient(true);
            manifest = JsonParser.parseReader(reader).getAsJsonObject();
        }

        JsonArray versions = manifest.getAsJsonArray("versions");
        String versionUrl = null;
        for (JsonElement el : versions) {
            JsonObject v = el.getAsJsonObject();
            if (versionId.equals(v.get("id").getAsString())) {
                versionUrl = v.get("url").getAsString();
                break;
            }
        }
        if (versionUrl == null) throw new RuntimeException("Версия " + versionId + " не найдена в манифесте");

        String versionJsonStr = UtilsNet.readUrl(versionUrl);
        Path jsonPath = versionsDir.resolve(versionId).resolve(versionId + ".json");
        Files.writeString(jsonPath, versionJsonStr, CREATE, TRUNCATE_EXISTING);

        try (JsonReader reader = new JsonReader(new StringReader(versionJsonStr))) {
            reader.setLenient(true);
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    public List<FileEntry> buildRequiredFiles(JsonObject versionJson, String version) throws Exception {
        List<FileEntry> list = new ArrayList<>();

        JsonObject client = versionJson.getAsJsonObject("downloads").getAsJsonObject("client");
        String url = client.get("url").getAsString();
        long size = client.get("size").getAsLong();
        String sha1 = client.get("sha1").getAsString();
        Path target = versionsDir.resolve(version).resolve(version + ".jar");
        validateFile(target, size, sha1);
        list.add(new FileEntry("client", "Minecraft " + version + " client", url, target, size, sha1));

        JsonArray libs = versionJson.getAsJsonArray("libraries");
        String os = detectOS();
        for (JsonElement el : libs) {
            JsonObject lib = el.getAsJsonObject();

            if (!isAllowedByRules(lib, os)) continue;
            if (!lib.has("downloads")) continue;
            JsonObject downloads = lib.getAsJsonObject("downloads");

            if (downloads.has("artifact")) {
                JsonObject art = downloads.getAsJsonObject("artifact");
                String u = get(art, "url", null);
                String pathStr = get(art, "path", null);
                long s = getLong(art, "size", -1);
                String h = get(art, "sha1", null);
                if (u != null && pathStr != null) {
                    Path t = librariesDir.resolve(pathStr);
                    validateFile(t, s, h);
                    list.add(new FileEntry("lib", "Library: " + pathStr, u, t, s, h));
                }
            }

            if (downloads.has("classifiers")) {
                JsonObject classifiers = downloads.getAsJsonObject("classifiers");
                String key = "natives-" + os;
                if (classifiers.has(key)) {
                    JsonObject nat = classifiers.getAsJsonObject(key);
                    String u = get(nat, "url", null);
                    String pathStr = get(nat, "path", null);
                    long s = getLong(nat, "size", -1);
                    String h = get(nat, "sha1", null);
                    if (u != null && pathStr != null) {
                        Path t = librariesDir.resolve(pathStr);
                        validateFile(t, s, h);
                        list.add(new FileEntry("natives", "Natives: " + pathStr, u, t, s, h));
                    }
                }
            }
        }

        JsonObject assetIndex = versionJson.getAsJsonObject("assetIndex");

        System.out.println(assetIndex);

        String assetsId = assetIndex.get("id").getAsString();
        String assetsUrl = assetIndex.get("url").getAsString();

        long aiSize = getLong(assetIndex, "size", -1);
        String aiSha1 = get(assetIndex, "sha1", null);

        Path indexPath = assetsDir.resolve("indexes").resolve(assetsId + ".json");
        Files.createDirectories(indexPath.getParent());
        list.add(new FileEntry("assetIndex", "AssetIndex: " + assetsId, assetsUrl, indexPath, aiSize, aiSha1));

        String assetsJsonStr = Files.exists(indexPath) ? Files.readString(indexPath) : UtilsNet.readUrl(assetsUrl);
        JsonObject assetsJson;
        try (JsonReader reader = new JsonReader(new StringReader(assetsJsonStr))) {
            reader.setLenient(true);
            assetsJson = JsonParser.parseReader(reader).getAsJsonObject();
        }

        JsonObject objects = assetsJson.getAsJsonObject("objects");
        for (Map.Entry<String, JsonElement> e : objects.entrySet()) {
            String logicalName = e.getKey();
            JsonObject obj = e.getValue().getAsJsonObject();
            String hash = obj.get("hash").getAsString();
            long size2 = obj.get("size").getAsLong();
            String sub = hash.substring(0, 2);
            String assetUrl = "https://resources.download.minecraft.net/" + sub + "/" + hash;
            Path assetPath = assetsDir.resolve("objects").resolve(sub).resolve(hash);
            validateFile(assetPath, size2, hash);
            list.add(new FileEntry("asset", "Asset: " + logicalName, assetUrl, assetPath, size2, hash));
        }

        return list;
    }

    private void validateFile(Path file, long expectedSize, String expectedSha1) {
        try {
            if (!Files.exists(file)) return;
            if (expectedSize > 0 && Files.size(file) != expectedSize) {
                Files.deleteIfExists(file);
                return;
            }
            if (expectedSha1 != null) {
                String actualSha1 = UtilsNet.sha1(file);
                if (actualSha1 == null || !actualSha1.equalsIgnoreCase(expectedSha1)) {
                    Files.deleteIfExists(file);
                }
            }
        } catch (Exception ignored) {
            LogManager.getLogger().warning("Ошибка валидации файла " + file);
        }
    }

    private String detectOS() {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) return "windows";
        if (osName.contains("mac")) return "osx";
        return "linux";
    }

    private boolean isAllowedByRules(JsonObject lib, String os) {
        if (!lib.has("rules")) return true;
        JsonArray rules = lib.getAsJsonArray("rules");
        Boolean allow = null;
        for (JsonElement el : rules) {
            JsonObject r = el.getAsJsonObject();
            String action = get(r, "action", "allow");
            String osName = null;
            if (r.has("os")) {
                JsonObject osObj = r.getAsJsonObject("os");
                osName = get(osObj, "name", null);
            }
            boolean osMatch = (osName == null) || osName.equals(os);
            if (osMatch) {
                if ("allow".equals(action)) allow = true;
                else if ("disallow".equals(action)) allow = false;
            }
        }
        return allow == null || allow;
    }

    private static String get(JsonObject o, String k, String def) {
        return o.has(k) ? o.get(k).getAsString() : def;
    }

    private static long getLong(JsonObject o, String k, long def) {
        return o.has(k) ? o.get(k).getAsLong() : def;
    }
}
