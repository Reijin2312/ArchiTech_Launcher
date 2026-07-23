// SPDX-FileCopyrightText: 2026 Raijin2312
// SPDX-License-Identifier: GPL-3.0-only

package org.architech.launcher;

import static org.architech.launcher.ArchiTechLauncher.CONFIG_PATH;
import static org.architech.launcher.ArchiTechLauncher.JAVA_PATH;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.Locale;
import java.util.zip.*;
import org.architech.launcher.authentication.account.Account;
import org.architech.launcher.authentication.account.AccountManager;
import org.architech.launcher.authentication.account.AccountStore;
import org.architech.launcher.gui.error.ErrorPanel;
import org.architech.launcher.utils.Jsons;
import org.architech.launcher.utils.SafePaths;
import org.architech.launcher.utils.SafeZipExtractor;
import org.architech.launcher.utils.Utils;
import org.architech.launcher.utils.logging.LogManager;

public class MinecraftLauncher {

    public static Process launchMinecraft(Path gameDir, String version) throws IOException {
        if (JAVA_PATH == null) {
            LogManager.getLogger().severe("Java 21 не найдена.");
            ErrorPanel.showError("Java 21 не найдена. Установите JDK 21.", "");
            throw new IllegalStateException("Java 21 не найдена. Установите JDK 21.");
        }

        List<String> args = new ArrayList<>();

        String javaBin = Utils.isWindows() ? "java.exe" : "java";
        args.add(JAVA_PATH.resolve("bin").resolve(javaBin).toString());

        String osName = System.getProperty("os.name").toLowerCase();
        String osArch = System.getProperty("os.arch").toLowerCase();

        Path nativesDir = gameDir.resolve("natives").resolve(version);
        Files.createDirectories(nativesDir);

        List<JsonNode> versionChain = getVersionChain(gameDir, version);

        extractNatives(gameDir, versionChain, nativesDir, osName, osArch);

        List<String> classpathEntries = getAllClasspathEntries(gameDir, versionChain);

        String assetIndex = null;
        for (JsonNode node : versionChain) {
            if (node.has("assetIndex") && node.get("assetIndex").has("id")) {
                assetIndex = node.get("assetIndex").get("id").asText();
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

        Account acc = AccountManager.getCurrentAccount();
        ensureSecretsLoaded(acc);
        placeholders.put("auth_player_name", acc == null || acc.getUsername() == null ? "" : acc.getUsername());
        placeholders.put(
                "auth_uuid",
                acc == null || acc.getUuid() == null ? "" : acc.getUuid().replace("-", ""));
        placeholders.put("auth_access_token", acc == null || acc.getAccessToken() == null ? "" : acc.getAccessToken());

        // placeholders.put("user_type", acc.userType);

        placeholders.put("version_type", "release");
        placeholders.put("launcher_name", "custom");
        placeholders.put("launcher_version", "1.0");
        placeholders.put("classpath", String.join(File.pathSeparator, classpathEntries));
        placeholders.put("clientid", "");
        placeholders.put("auth_xuid", "");
        Locale locale = Locale.forLanguageTag(
                ArchiTechLauncher.GAME_LANGUAGE_TAG == null ? "en-US" : ArchiTechLauncher.GAME_LANGUAGE_TAG);
        args.add("-Duser.language=" + locale.getLanguage());
        if (!locale.getCountry().isEmpty()) args.add("-Duser.country=" + locale.getCountry());

        if (Files.exists(CONFIG_PATH)) {
            Map<?, ?> cfg;
            try (Reader r = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
                cfg = Jsons.MAPPER.readValue(r, Map.class);
            }
            if (cfg == null) cfg = Collections.emptyMap();

            int maxMemory = ((Number) (cfg.containsKey("maxMemory") ? cfg.get("maxMemory") : 2048)).intValue();
            args.add("-Xmx" + maxMemory + "M");
        }

        List<String> jvmArgs = new ArrayList<>();
        for (JsonNode versionObj : versionChain) {
            if (versionObj.has("arguments") && versionObj.get("arguments").has("jvm")) {
                ArrayNode jvm = (ArrayNode) versionObj.get("arguments").get("jvm");
                for (JsonNode el : jvm) {
                    addProcessedArg(el, jvmArgs, placeholders, osName, osArch);
                }
            }
        }
        args.addAll(jvmArgs);

        String mainClass = versionChain.getFirst().get("mainClass").asText();
        args.add(mainClass);

        List<String> gameArgs = new ArrayList<>();
        for (JsonNode versionObj : versionChain) {
            if (versionObj.has("arguments") && versionObj.get("arguments").has("game")) {
                ArrayNode game = (ArrayNode) versionObj.get("arguments").get("game");
                for (JsonNode el : game) {
                    addProcessedArg(el, gameArgs, placeholders, osName, osArch);
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
        return pb.start();
    }

    private static List<JsonNode> getVersionChain(Path gameDir, String version) throws IOException {
        List<JsonNode> chain = new ArrayList<>();
        String current = version;
        Set<String> seen = new HashSet<>();
        while (current != null && !seen.contains(current)) {
            seen.add(current);
            Path versionJson = gameDir.resolve("versions").resolve(current).resolve(current + ".json");
            if (!Files.exists(versionJson)) break;
            JsonNode obj = Jsons.MAPPER.readTree(Files.readString(versionJson));
            chain.add(obj);
            current = obj.has("inheritsFrom") ? obj.get("inheritsFrom").asText() : null;
        }
        return chain;
    }

    private static List<String> getAllClasspathEntries(Path gameDir, List<JsonNode> versionChain) {
        List<String> classpathEntries = new ArrayList<>();
        Set<String> addedPaths = new HashSet<>();
        boolean isModded = versionChain.size() > 1;
        for (JsonNode versionObj : versionChain) {
            if (versionObj.has("libraries") && versionObj.get("libraries").isArray()) {
                ArrayNode libs = (ArrayNode) versionObj.get("libraries");
                for (JsonNode lib : libs) {
                    if (!lib.has("downloads")) continue;

                    JsonNode downloads = lib.get("downloads");
                    if (!downloads.has("artifact")) continue;

                    JsonNode artifact = downloads.get("artifact");
                    if (!artifact.has("path")) continue;

                    String path = artifact.get("path").asText();
                    Path jarPath = gameDir.resolve("libraries").resolve(path);
                    String absPath = jarPath.toString();
                    if (Files.exists(jarPath) && !addedPaths.contains(absPath)) {
                        classpathEntries.add(absPath);
                        addedPaths.add(absPath);
                    }
                }
            }
            if (versionObj.has("id")) {
                String ver = versionObj.get("id").asText();
                Path clientJar = gameDir.resolve("versions").resolve(ver).resolve(ver + ".jar");
                String jarAbs = clientJar.toString();
                if (Files.exists(clientJar) && !addedPaths.contains(jarAbs)) {
                    if (!isModded || versionObj.has("inheritsFrom")) {
                        classpathEntries.add(jarAbs);
                        addedPaths.add(jarAbs);
                    }
                }
            }
        }
        return classpathEntries;
    }

    private static void extractNatives(
            Path gameDir, List<JsonNode> versionChain, Path nativesDir, String osName, String osArch)
            throws IOException {
        String osKey;
        if (osName.contains("win")) {
            osKey = "windows";
        } else if (osName.contains("mac")) {
            osKey = "osx";
        } else if (osName.contains("linux")) {
            osKey = "linux";
        } else {
            LogManager.getLogger().warning("Unsupported OS for natives: " + osName);
            return;
        }

        String archBits = osArch.contains("64") || osArch.contains("aarch64") ? "64" : "32";
        Path librariesRoot = gameDir.resolve("libraries").toAbsolutePath().normalize();
        Set<Path> extractedArchives = new HashSet<>();

        for (JsonNode versionObject : versionChain) {
            if (!versionObject.path("libraries").isArray()) {
                continue;
            }

            for (JsonNode library : versionObject.path("libraries")) {
                JsonNode natives = library.path("natives");
                if (!natives.hasNonNull(osKey)) {
                    continue;
                }

                String nativeClassifier = natives.get(osKey).asText().replace("${arch}", archBits);
                JsonNode artifact =
                        library.path("downloads").path("classifiers").path(nativeClassifier);
                if (!artifact.hasNonNull("path")) {
                    continue;
                }

                Path nativeArchive = SafePaths.resolveInside(
                        librariesRoot, artifact.get("path").asText());
                SafePaths.verifyNoSymlinkParents(librariesRoot, nativeArchive);
                SafePaths.rejectSymbolicLink(nativeArchive);

                if (!Files.isRegularFile(nativeArchive) || !extractedArchives.add(nativeArchive)) {
                    continue;
                }

                SafeZipExtractor.extract(
                        nativeArchive,
                        nativesDir,
                        name -> !name.toUpperCase(Locale.ROOT).startsWith("META-INF/"));
            }
        }
    }

    private static void addProcessedArg(
            JsonNode el, List<String> target, Map<String, String> placeholders, String osName, String osArch) {
        if (el == null) return;

        if (el.isTextual()) {
            String val = replacePlaceholders(el.asText(), placeholders);
            target.add(val);
            return;
        }

        if (el.isObject()) {
            ObjectNode ruleObj = (ObjectNode) el;

            if (ruleObj.has("rules")) {
                boolean allow = false;
                ArrayNode rules = (ArrayNode) ruleObj.get("rules");
                for (JsonNode r : rules) {
                    if (!r.isObject()) continue;
                    String action = r.has("action") ? r.get("action").asText() : "allow";
                    boolean ruleMatch = true;
                    if (r.has("os")) {
                        JsonNode os = r.get("os");
                        if (os.has("name")) {
                            String osReq = os.get("name").asText();
                            if (!osName.contains(osReq)) {
                                ruleMatch = false;
                            }
                        }
                        if (os.has("arch")) {
                            String archReq = os.get("arch").asText();
                            if (!osArch.equals(archReq)) {
                                ruleMatch = false;
                            }
                        }
                    }
                    if (r.has("features")) {
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
                    JsonNode value = ruleObj.has("value")
                            ? ruleObj.get("value")
                            : ruleObj.has("values") ? ruleObj.get("values") : null;
                    if (value != null) {
                        if (value.isTextual()) {
                            String val = replacePlaceholders(value.asText(), placeholders);
                            target.add(val);
                        } else if (value.isArray()) {
                            for (JsonNode v : value) {
                                if (v.isTextual()) {
                                    String val = replacePlaceholders(v.asText(), placeholders);
                                    target.add(val);
                                }
                            }
                        }
                    }
                }
            } else {
                JsonNode value = ruleObj.has("value")
                        ? ruleObj.get("value")
                        : ruleObj.has("values") ? ruleObj.get("values") : null;
                if (value != null) {
                    if (value.isTextual()) {
                        String val = replacePlaceholders(value.asText(), placeholders);
                        target.add(val);
                    } else if (value.isArray()) {
                        for (JsonNode v : value) {
                            if (v.isTextual()) {
                                String val = replacePlaceholders(v.asText(), placeholders);
                                target.add(val);
                            }
                        }
                    }
                }
            }
        }
    }

    private static String replacePlaceholders(String input, Map<String, String> placeholders) {
        if (input == null) return null;
        if (placeholders == null || placeholders.isEmpty()) return input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String replacement = entry.getValue();
            if (replacement == null) replacement = "";
            input = input.replace("${" + entry.getKey() + "}", replacement);
        }
        return input;
    }

    private static void ensureSecretsLoaded(Account acc) {
        if (acc == null) return;
        try {
            AccountStore.getSecret(acc, "mcAccessToken").ifPresent(acc::setAccessToken);
            AccountStore.getSecret(acc, "uuid").ifPresent(acc::setUuid);
            AccountStore.getSecret(acc, "username").ifPresent(acc::setUsername);
        } catch (Exception e) {
            LogManager.getLogger().warning("Failed to load secrets from storage: " + e.getMessage());
        }
    }
}
