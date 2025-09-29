package org.architech.launcher.managment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.architech.launcher.ArchiTechLauncher;
import org.architech.launcher.utils.FileEntry;
import org.architech.launcher.utils.Jsons;
import org.architech.launcher.utils.Utils;
import org.architech.launcher.utils.logging.LogManager;

import java.nio.file.*;
import java.util.*;

import static java.nio.file.StandardOpenOption.*;

public class VersionManager {

    public JsonNode loadVersionJson(String versionId) throws Exception {
        Files.createDirectories(ArchiTechLauncher.VERSIONS_DIR.resolve(versionId));
        String manifestUrl = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
        String manifestStr = Utils.readUrl(manifestUrl);

        JsonNode manifest = org.architech.launcher.utils.Jsons.MAPPER.readTree(manifestStr);
        ArrayNode versions = (ArrayNode) manifest.get("versions");

        String versionUrl = null;
        for (JsonNode el : versions) {
            if (el.has("id") && versionId.equals(el.get("id").asText())) {
                versionUrl = el.has("url") ? el.get("url").asText() : null;
                break;
            }
        }
        if (versionUrl == null) throw new RuntimeException("Версия " + versionId + " не найдена в манифесте");

        String versionJsonStr = Utils.readUrl(versionUrl);
        Path jsonPath = ArchiTechLauncher.VERSIONS_DIR.resolve(versionId).resolve(versionId + ".json");
        Files.writeString(jsonPath, versionJsonStr, CREATE, TRUNCATE_EXISTING);

        return Jsons.MAPPER.readTree(versionJsonStr);
    }

    public List<FileEntry> buildRequiredFiles(JsonNode versionJson, String version) throws Exception {
        List<FileEntry> list = new ArrayList<>();

        JsonNode client = versionJson.path("downloads").path("client");
        if (!client.isMissingNode() && client.has("url")) {
            String url = client.path("url").asText(null);
            long size = client.path("size").asLong(-1);
            String sha1 = client.path("sha1").asText(null);
            Path target = ArchiTechLauncher.VERSIONS_DIR.resolve(version).resolve(version + ".jar");
            validateFile(target, size, sha1);
            list.add(new FileEntry("client", "Minecraft " + version + " client", url, target, size, sha1));
        }

        JsonNode libs = versionJson.path("libraries");
        String os = detectOS();
        if (libs.isArray()) {
            for (JsonNode lib : libs) {
                if (!isAllowedByRules(lib, os)) continue;
                if (!lib.has("downloads")) continue;
                JsonNode downloads = lib.get("downloads");

                if (downloads.has("artifact")) {
                    JsonNode art = downloads.get("artifact");
                    String u = get(art, "url", null);
                    String pathStr = get(art, "path", null);
                    long s = getLong(art);
                    String h = get(art, "sha1", null);
                    if (u != null && pathStr != null) {
                        Path t = ArchiTechLauncher.LIBRARIES_DIR.resolve(pathStr);
                        validateFile(t, s, h);
                        list.add(new FileEntry("lib", "Library: " + pathStr, u, t, s, h));
                    }
                }

                if (downloads.has("classifiers")) {
                    JsonNode classifiers = downloads.get("classifiers");
                    String key = "natives-" + os;
                    if (classifiers.has(key)) {
                        JsonNode nat = classifiers.get(key);
                        String u = get(nat, "url", null);
                        String pathStr = get(nat, "path", null);
                        long s = getLong(nat);
                        String h = get(nat, "sha1", null);
                        if (u != null && pathStr != null) {
                            Path t = ArchiTechLauncher.LIBRARIES_DIR.resolve(pathStr);
                            validateFile(t, s, h);
                            list.add(new FileEntry("natives", "Natives: " + pathStr, u, t, s, h));
                        }
                    }
                }
            }
        }

        JsonNode assetIndex = versionJson.path("assetIndex");
        if (!assetIndex.isMissingNode()) {
            String assetsId = assetIndex.path("id").asText(null);
            String assetsUrl = assetIndex.path("url").asText(null);

            long aiSize = getLong(assetIndex);
            String aiSha1 = get(assetIndex, "sha1", null);

            Path indexPath = ArchiTechLauncher.ASSETS_DIR.resolve("indexes").resolve(assetsId + ".json");
            Files.createDirectories(indexPath.getParent());
            list.add(new FileEntry("assetIndex", "AssetIndex: " + assetsId, assetsUrl, indexPath, aiSize, aiSha1));

            String assetsJsonStr = Files.exists(indexPath) ? Files.readString(indexPath) : Utils.readUrl(assetsUrl);
            JsonNode assetsJson = org.architech.launcher.utils.Jsons.MAPPER.readTree(assetsJsonStr);

            JsonNode objects = assetsJson.path("objects");
            if (objects != null && objects.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = objects.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> e = fields.next();
                    String logicalName = e.getKey();
                    JsonNode obj = e.getValue();
                    String hash = obj.path("hash").asText(null);
                    long size2 = obj.path("size").asLong(-1);
                    if (hash == null) continue;
                    String sub = hash.length() >= 2 ? hash.substring(0, 2) : "";
                    String assetUrl = "https://resources.download.minecraft.net/" + sub + "/" + hash;
                    Path assetPath = ArchiTechLauncher.ASSETS_DIR.resolve("objects").resolve(sub).resolve(hash);
                    validateFile(assetPath, size2, hash);
                    list.add(new FileEntry("asset", "Asset: " + logicalName, assetUrl, assetPath, size2, hash));
                }
            }
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
                String actualSha1 = Utils.sha1(file);
                if (actualSha1 == null || !actualSha1.equalsIgnoreCase(expectedSha1)) {
                    Files.deleteIfExists(file);
                }
            }
        } catch (Exception ex) {
            LogManager.getLogger().warning("Ошибка валидации файла " + file + ": " + ex.getMessage());
        }
    }

    private String detectOS() {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) return "windows";
        if (osName.contains("mac")) return "osx";
        return "linux";
    }

    private boolean isAllowedByRules(JsonNode lib, String os) {
        if (!lib.has("rules")) return true;
        JsonNode rulesNode = lib.get("rules");
        if (!rulesNode.isArray()) return true;
        ArrayNode rules = (ArrayNode) rulesNode;
        Boolean allow = null;
        for (JsonNode el : rules) {
            String action = get(el, "action", "allow");
            String osName = null;
            if (el.has("os")) {
                JsonNode osObj = el.get("os");
                osName = osObj.path("name").asText(null);
            }
            boolean osMatch = (osName == null) || osName.equals(os);
            if (osMatch) {
                if ("allow".equals(action)) allow = true;
                else if ("disallow".equals(action)) allow = false;
            }
        }
        return allow == null || allow;
    }

    private static String get(JsonNode o, String k, String def) {
        return (o != null && o.has(k)) ? o.get(k).asText() : def;
    }


    private static long getLong(JsonNode o) {
        return (o != null && o.has("size")) ? o.get("size").asLong() : -1L;
    }
}
