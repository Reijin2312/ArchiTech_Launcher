package org.architech.launcher;

import com.google.gson.JsonObject;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.architech.launcher.discord.DiscordIntegration;
import org.architech.launcher.gui.AllSettingsUI;
import org.architech.launcher.gui.LauncherUI;
import org.architech.launcher.managment.DownloadManager;
import org.architech.launcher.managment.HttpModsManager;
import org.architech.launcher.managment.NativesManager;
import org.architech.launcher.managment.VersionManager;
import org.architech.launcher.neoforge.NeoForgeInstaller;
import org.architech.launcher.utils.*;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.architech.launcher.gui.AllSettingsUI.GSON;
import static org.architech.launcher.neoforge.NeoForgeInstaller.getInstalledVersion;
import static org.architech.launcher.utils.ServersDatWriter.writeServersDat;

public class MCLauncher extends Application {

    public static Path GAME_DIR = Paths.get(System.getProperty("user.home"), ".architech");
    public static Path CONFIG_PATH;
    public static Path LAUNCHER_DIR;
    public static Path VERSIONS_DIR;
    public static Path LIBRARIES_DIR;
    public static Path ASSETS_DIR;
    public static Path JAVA_PATH;
    public static Path ACCOUNT_FILE;
    public static final String MINECRAFT_VERSION = "1.21.1";
    public static final String BACKEND_URL = System.getenv().getOrDefault("ARCHITECH_BACKEND_URL", "http://26.66.122.141:51789");
    public static boolean closeOnLaunch = false;
    private LauncherUI ui;

    @Override
    public void start(Stage stage) throws IOException, URISyntaxException {
        LogManager.setupLogger();
        LAUNCHER_DIR = Paths.get(AllSettingsUI.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
        CONFIG_PATH = LAUNCHER_DIR.resolve("launcher_config.json");
        AllSettingsUI.createDefaultConfigIfMissing();

        ACCOUNT_FILE = LAUNCHER_DIR.resolve(".account.json");

        Path base = Objects.requireNonNull(findJava21());
        Path candidate = base.resolve("bin").resolve("java.exe");

        if (Files.isExecutable(base)) {
            JAVA_PATH = base;
        } else if (Files.isExecutable(candidate)) {
            JAVA_PATH = candidate;
        } else {
            LogManager.getLogger().severe("Не найден java.exe в " + base);
            throw new IllegalStateException("Не найден java.exe в " + base);
        }

        if (Files.exists(CONFIG_PATH)) {
            Map<?, ?> cfg;
            try (Reader r = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
                cfg = GSON.fromJson(r, Map.class);
                if (cfg == null) return;
                if (cfg.containsKey("gameDir")) GAME_DIR = Path.of(cfg.get("gameDir").toString());
                if (cfg.containsKey("closeOnLaunch")) closeOnLaunch = (boolean) cfg.get("closeOnLaunch");
            }
        }

        VERSIONS_DIR = GAME_DIR.resolve("versions");
        LIBRARIES_DIR = GAME_DIR.resolve("libraries");
        ASSETS_DIR = GAME_DIR.resolve("assets");

        ui = new LauncherUI(stage, this::onLaunchClicked);
        DiscordIntegration.start();
    }

    public void stop() throws Exception {
        super.stop();
        DiscordIntegration.stop();
    }

    private void onLaunchClicked(String playerName) {
        new Thread(() -> {
            try {
                ui.updateProgress("Подготовка к запуску...", 0);

                VersionManager versionManager = new VersionManager(VERSIONS_DIR, ASSETS_DIR, LIBRARIES_DIR);
                JsonObject versionJson = versionManager.loadVersionJson(MINECRAFT_VERSION);
                List<FileEntry> files = versionManager.buildRequiredFiles(versionJson, MINECRAFT_VERSION);

                files.sort(Comparator.comparingInt(f -> {
                    if ("assetIndex".equals(f.kind)) return 0;
                    if ("client".equals(f.kind)) return 1;
                    if ("lib".equals(f.kind)) return 2;
                    if ("natives".equals(f.kind)) return 3;
                    if ("asset".equals(f.kind)) return 4;
                    return 5;
                }));

                DownloadManager downloadManager = new DownloadManager(ui);

                long total = downloadManager.computeTotalBytesToDownload(files);
                downloadManager.setTotalBytesPlanned(total);

                int threads = Math.max(2, Runtime.getRuntime().availableProcessors());
                int rounds = 3;
                ui.updateProgress("Начало параллельной загрузки (" + threads + " потоков)...", 0);
                List<FileEntry> failed = downloadManager.downloadFilesInParallel(files, threads, rounds);

                if (!failed.isEmpty()) {
                    String names = failed.stream().map(f -> f.name).collect(Collectors.joining(", "));
                    LogManager.getLogger().severe("Не удалось скачать некоторые файлы: " + names);
                    throw new IOException("Не удалось скачать некоторые файлы: " + names);
                }

                NativesManager nativesManager = new NativesManager(GAME_DIR, MINECRAFT_VERSION);
                nativesManager.prepareNatives(files);

                NeoForgeInstaller.ensureInstalledAndReady(GAME_DIR, MINECRAFT_VERSION, ui);

                try {
                    HttpModsManager.syncMods(GAME_DIR, ui);
                } catch (Exception ex) {
                    LogManager.getLogger().severe("Ошибка синхронизации модов: " + ex.getMessage());
                    Platform.runLater(() -> ui.showError("Ошибка синхронизации модов: " + ex.getMessage()));
                }

                Path serversDat = GAME_DIR.resolve("servers.dat");
                if (!Files.exists(serversDat)) ServersDatGenerator.createServersDat(serversDat);

                ServersDatWriter.ServerEntry mySrv = new ServersDatWriter.ServerEntry("Сервер Minecraft", "architech.mc-world.xyz").withHidden(false);

                List<ServersDatWriter.ServerEntry> list = Collections.singletonList(mySrv);

                Files.createDirectories(serversDat.getParent());
                writeServersDat(serversDat, list);

                MinecraftLauncher.launchMinecraft(GAME_DIR, "neoforge-" + getInstalledVersion(GAME_DIR));

                ui.updateProgress("Готово. Клиент запущен...", 1);
            } catch (Exception e) {
                Platform.runLater(() -> ui.showError("Ошибка: " + e.getMessage()));
            }
        }, "Launcher-Worker").start();
    }

    public static Path findJava21() {
        String[] searchDirs = {"C:/Program Files/Java", "C:/Program Files (x86)/Java", "/usr/lib/jvm", "/Library/Java/JavaVirtualMachines"};
        for (String base : searchDirs) {
            File dir = new File(base);
            if (!dir.exists()) continue;
            File[] subdirs = dir.listFiles(File::isDirectory);
            if (subdirs == null) continue;
            for (File sd : subdirs) {
                Path javaBin = Paths.get(sd.getAbsolutePath(), "bin", Utils.isWindows() ? "java.exe" : "java");
                if (!Files.exists(javaBin)) continue;
                try {
                    Process p = new ProcessBuilder(javaBin.toString(), "-version")
                            .redirectErrorStream(true)
                            .start();
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            if (line.contains("version")) {
                                int i1 = line.indexOf('"');
                                int i2 = line.indexOf('"', i1 + 1);
                                if (i1 >= 0 && i2 > i1) {
                                    String ver = line.substring(i1 + 1, i2);
                                    if (ver.startsWith("21")) return javaBin;
                                }
                            }
                        }
                    }
                } catch (IOException ex) {
                    LogManager.getLogger().severe("Ошибка нахождения Java 21 " + ex.getMessage());
                }
            }
        }
        return null;
    }
}