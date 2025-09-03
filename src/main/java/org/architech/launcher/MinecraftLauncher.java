package org.architech.launcher;

import com.google.gson.*;
import org.architech.launcher.auth.Account;
import org.architech.launcher.auth.Auth;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

import static org.architech.launcher.MCLauncher.CONFIG_PATH;
import static org.architech.launcher.MCLauncher.JAVA_PATH;
import static org.architech.launcher.gui.AllSettingsUI.GSON;

public class MinecraftLauncher {
    public static void launchMinecraft(Path gameDir, String version) throws IOException {
        if (JAVA_PATH == null) {
            throw new IllegalStateException("Java 21 не найдена. Установите JDK 21.");
        }

        List<String> args = new ArrayList<>();
        args.add(JAVA_PATH.toString());

        String osName = System.getProperty("os.name").toLowerCase();
        String osArch = System.getProperty("os.arch").toLowerCase();

        Path nativesDir = gameDir.resolve("natives").resolve(version);
        Files.createDirectories(nativesDir);

        List<JsonObject> versionChain = getVersionChain(gameDir, version);

        extractNatives(gameDir, versionChain, nativesDir, osName, osArch);

        List<String> classpathEntries = getAllClasspathEntries(gameDir, versionChain);

        String assetIndex = null;
        for (JsonObject obj : versionChain) {
            if (obj.has("assetIndex")) {
                assetIndex = obj.get("assetIndex").getAsJsonObject().get("id").getAsString();
                break;
            }
        }
        if (assetIndex == null) {
            assetIndex = "17";
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("natives_directory", nativesDir.toString());
        placeholders.put("library_directory", gameDir.resolve("libraries").toString());
        placeholders.put("classpath_separator", File.pathSeparator);
        placeholders.put("version_name", version);
        placeholders.put("game_directory", gameDir.toString());
        placeholders.put("assets_root", gameDir.resolve("assets").toString());
        placeholders.put("assets_index_name", assetIndex);

        Account acc = Auth.current();
        placeholders.put("auth_player_name", acc.username);
        placeholders.put("auth_uuid", acc.uuid.replace("-", ""));
        placeholders.put("auth_access_token", acc.accessToken);

        //placeholders.put("user_type", acc.userType);

        placeholders.put("version_type", "release");
        placeholders.put("launcher_name", "custom");
        placeholders.put("launcher_version", "1.0");
        placeholders.put("classpath", String.join(File.pathSeparator, classpathEntries));
        placeholders.put("clientid", "");
        placeholders.put("auth_xuid", "");

        Map<?,?> cfg;
        try (Reader r = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
            cfg = GSON.fromJson(r, Map.class);
        }
        if (cfg == null) cfg = Collections.emptyMap();

        int maxMemory = ((Number) (cfg.containsKey("maxMemory") ? cfg.get("maxMemory") : 2048)).intValue();
        args.add("-Xmx" + maxMemory + "M");

        List<String> jvmArgs = new ArrayList<>();
        for (JsonObject obj : versionChain) {
            if (obj.has("arguments")) {
                JsonObject argsObj = obj.getAsJsonObject("arguments");
                if (argsObj.has("jvm")) {
                    JsonArray jvm = argsObj.getAsJsonArray("jvm");
                    for (JsonElement el : jvm) {
                        addProcessedArg(el, jvmArgs, placeholders, osName, osArch);
                    }
                }
            }
        }
        args.addAll(jvmArgs);

        String mainClass = versionChain.getFirst().get("mainClass").getAsString();
        args.add(mainClass);

        List<String> gameArgs = new ArrayList<>();
        for (JsonObject obj : versionChain) {
            if (obj.has("arguments")) {
                JsonObject argsObj = obj.getAsJsonObject("arguments");
                if (argsObj.has("game")) {
                    JsonArray game = argsObj.getAsJsonArray("game");
                    for (JsonElement el : game) {
                        addProcessedArg(el, gameArgs, placeholders, osName, osArch);
                    }
                }
            }
        }
        args.addAll(gameArgs);

        Path configDir = gameDir.resolve("config");
        Files.createDirectories(configDir);
        Path fmlToml = configDir.resolve("fml.toml");
        if (!Files.exists(fmlToml)) {
            Files.writeString(fmlToml, "earlyWindowControl = true\n");
        }

        ProcessBuilder pb = new ProcessBuilder(args);
        pb.directory(gameDir.toFile());
        pb.inheritIO();
        pb.start();
    }

    private static List<JsonObject> getVersionChain(Path gameDir, String version) throws IOException {
        List<JsonObject> chain = new ArrayList<>();
        String current = version;
        Set<String> seen = new HashSet<>();
        while (current != null && !seen.contains(current)) {
            seen.add(current);
            Path versionJson = gameDir.resolve("versions").resolve(current).resolve(current + ".json");
            if (!Files.exists(versionJson)) break;
            JsonObject obj = JsonParser.parseString(Files.readString(versionJson)).getAsJsonObject();
            chain.add(obj);
            current = obj.has("inheritsFrom") ? obj.get("inheritsFrom").getAsString() : null;
        }
        return chain;
    }

    private static List<String> getAllClasspathEntries(Path gameDir, List<JsonObject> versionChain) {
        List<String> classpathEntries = new ArrayList<>();
        Set<String> addedPaths = new HashSet<>();
        boolean isModded = versionChain.size() > 1;
        for (JsonObject versionObj : versionChain) {
            if (versionObj.has("libraries")) {
                JsonArray libs = versionObj.getAsJsonArray("libraries");
                for (JsonElement el : libs) {
                    JsonObject lib = el.getAsJsonObject();
                    if (!lib.has("downloads")) continue;
                    JsonObject downloads = lib.getAsJsonObject("downloads");
                    if (!downloads.has("artifact")) continue;
                    JsonObject artifact = downloads.getAsJsonObject("artifact");
                    if (!artifact.has("path")) continue;
                    String path = artifact.get("path").getAsString();
                    Path jarPath = gameDir.resolve("libraries").resolve(path);
                    String absPath = jarPath.toString();
                    if (Files.exists(jarPath) && !addedPaths.contains(absPath)) {
                        classpathEntries.add(absPath);
                        addedPaths.add(absPath);
                    }
                }
            }
            String ver = versionObj.get("id").getAsString();
            Path clientJar = gameDir.resolve("versions").resolve(ver).resolve(ver + ".jar");
            String jarAbs = clientJar.toString();
            if (Files.exists(clientJar) && !addedPaths.contains(jarAbs)) {
                if (!isModded || versionObj.has("inheritsFrom")) {
                    classpathEntries.add(jarAbs);
                    addedPaths.add(jarAbs);
                }
            }
        }
        return classpathEntries;
    }

    private static void extractNatives(Path gameDir, List<JsonObject> versionChain, Path nativesDir, String osName, String osArch) throws IOException {
        String classifier = null;
        if (osName.contains("win")) {
            classifier = "natives-windows" + (osArch.contains("64") ? "" : "-32");
        } else if (osName.contains("mac")) {
            classifier = "natives-macos" + (osArch.contains("arm") ? "-arm64" : "");
        } else if (osName.contains("linux")) {
            classifier = "natives-linux";
        }
        if (classifier == null) {
            System.err.println("Unsupported OS for natives: " + osName);
            return;
        }

        for (JsonObject versionObj : versionChain) {
            if (versionObj.has("libraries")) {
                JsonArray libs = versionObj.getAsJsonArray("libraries");
                for (JsonElement el : libs) {
                    JsonObject lib = el.getAsJsonObject();
                    if (lib.has("natives")) {
                        JsonObject natives = lib.get("natives").getAsJsonObject();
                        if (natives.has(classifier)) {
                            String nativeClassifier = natives.get(classifier).getAsString();
                            if (lib.has("downloads")) {
                                JsonObject downloads = lib.get("downloads").getAsJsonObject();
                                if (downloads.has("classifiers")) {
                                    JsonObject classifiers = downloads.get("classifiers").getAsJsonObject();
                                    if (classifiers.has(nativeClassifier)) {
                                        JsonObject artifact = classifiers.get(nativeClassifier).getAsJsonObject();
                                        if (artifact.has("path")) {
                                            String path = artifact.get("path").getAsString();
                                            Path nativeJar = gameDir.resolve("libraries").resolve(path);
                                            if (Files.exists(nativeJar)) {
                                                try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(nativeJar))) {
                                                    ZipEntry entry;
                                                    while ((entry = zis.getNextEntry()) != null) {
                                                        if (entry.isDirectory() || entry.getName().startsWith("META-INF/")) {
                                                            continue;
                                                        }
                                                        Path outPath = nativesDir.resolve(entry.getName());
                                                        Files.createDirectories(outPath.getParent());
                                                        Files.copy(zis, outPath, StandardCopyOption.REPLACE_EXISTING);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static void addProcessedArg(JsonElement el, List<String> target, Map<String, String> placeholders, String osName, String osArch) {
        if (el.isJsonPrimitive()) {
            String val = el.getAsString();
            val = replacePlaceholders(val, placeholders);
            target.add(val);
        } else if (el.isJsonObject()) {
            JsonObject ruleObj = el.getAsJsonObject();
            if (ruleObj.has("rules")) {
                boolean allow = false;
                JsonArray rules = ruleObj.getAsJsonArray("rules");
                for (JsonElement r : rules) {
                    JsonObject rr = r.getAsJsonObject();
                    String action = rr.get("action").getAsString();
                    boolean ruleMatch = true;
                    if (rr.has("os")) {
                        JsonObject os = rr.getAsJsonObject("os");
                        if (os.has("name")) {
                            String osReq = os.get("name").getAsString();
                            if (!osName.contains(osReq)) {
                                ruleMatch = false;
                            }
                        }
                        if (os.has("arch")) {
                            String archReq = os.get("arch").getAsString();
                            if (!osArch.equals(archReq)) {
                                ruleMatch = false;
                            }
                        }
                    }
                    if (rr.has("features")) {
                        ruleMatch = false;
                    }
                    if (ruleMatch) {
                        if ("allow".equals(action)) {
                            allow = true;
                        } else if ("disallow".equals(action)) {
                            allow = false;
                        }
                    }
                }
                if (allow) {
                    JsonElement value = ruleObj.get("value");
                    if (value.isJsonPrimitive()) {
                        String val = value.getAsString();
                        val = replacePlaceholders(val, placeholders);
                        target.add(val);
                    } else if (value.isJsonArray()) {
                        JsonArray arr = value.getAsJsonArray();
                        for (JsonElement v : arr) {
                            String val = v.getAsString();
                            val = replacePlaceholders(val, placeholders);
                            target.add(val);
                        }
                    }
                }
            } else {
                JsonElement value = ruleObj.get("value");
                if (value != null) {
                    if (value.isJsonPrimitive()) {
                        String val = value.getAsString();
                        val = replacePlaceholders(val, placeholders);
                        target.add(val);
                    } else if (value.isJsonArray()) {
                        JsonArray arr = value.getAsJsonArray();
                        for (JsonElement v : arr) {
                            String val = v.getAsString();
                            val = replacePlaceholders(val, placeholders);
                            target.add(val);
                        }
                    }
                }
            }
        }
    }

    private static String replacePlaceholders(String input, Map<String, String> placeholders) {
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            input = input.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        return input;
    }

}