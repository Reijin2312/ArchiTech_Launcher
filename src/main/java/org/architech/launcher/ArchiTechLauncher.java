package org.architech.launcher;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.architech.launcher.discord.DiscordIntegration;
import org.architech.launcher.gui.BackgroundCache;
import org.architech.launcher.gui.error.ErrorPanel;
import org.architech.launcher.gui.settings.tab.SettingsTab;
import org.architech.launcher.gui.LauncherUI;
import org.architech.launcher.gui.timer.Timer;
import org.architech.launcher.managment.DownloadManager;
import org.architech.launcher.managment.ModsManager;
import org.architech.launcher.managment.NativesManager;
import org.architech.launcher.managment.VersionManager;
import org.architech.launcher.managment.NeoForgeManager;
import org.architech.launcher.utils.*;
import org.architech.launcher.utils.logging.LogManager;
import org.architech.launcher.utils.serverinfo.ServersDatGenerator;
import org.architech.launcher.utils.serverinfo.ServersDatWriter;
import java.io.*;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import static org.architech.launcher.managment.NeoForgeManager.getInstalledVersion;
import static org.architech.launcher.utils.serverinfo.ServersDatWriter.writeServersDat;

public class ArchiTechLauncher extends Application {

    public static final String MINECRAFT_VERSION = "1.21.1";

    public static Path GAME_DIR = Paths.get(System.getProperty("user.home"), ".architech");
    public static Path CONFIG_PATH;
    public static Path LAUNCHER_DIR;
    public static Path VERSIONS_DIR;
    public static Path LIBRARIES_DIR;
    public static Path ASSETS_DIR;
    public static Path JAVA_PATH;
    public static Path ACCOUNT_FILE;
    public static String BACKEND_URL;
    public static String LAUNCHER_BACKGROUND;
    public static int HTTP_TIMEOUT;
    public static boolean CLOSE_ON_LAUNCH = false;
    public static boolean AUTO_UPDATE_CLIENT = true;

    public static LauncherUI UI;
    public static final DownloadManager DOWNLOAD_MANAGER = new DownloadManager();

    private static final ExecutorService launcherExecutor = Executors.newSingleThreadExecutor(daemonFactory("launcher-worker"));
    public static final ExecutorService backgroundExecutor = Executors.newCachedThreadPool(daemonFactory("bg"));
    public static final ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor(daemonFactory("sched"));
    private static final AtomicReference<Future<?>> launchFuture = new AtomicReference<>();
    private static final AtomicReference<Future<?>> updateFuture = new AtomicReference<>();
    private static final AtomicReference<DownloadManager> activeDownloadManager = new AtomicReference<>();
    private static volatile Process currentGameProcess = null;

    @Override
    public void start(Stage stage) throws IOException, URISyntaxException {
        LogManager.setupLogger();

        BACKEND_URL = "http://95.105.113.224:51789";
        LAUNCHER_DIR = Paths.get(SettingsTab.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
        CONFIG_PATH = LAUNCHER_DIR.resolve("launcher_config.json");
        ACCOUNT_FILE = LAUNCHER_DIR.resolve(".account.json");

        SettingsTab.createDefaultConfigIfMissing();

        if (Files.exists(CONFIG_PATH)) {
            Map<?, ?> cfg;
            try (Reader r = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
                cfg = Jsons.MAPPER.readValue(r, Map.class);
                if (cfg == null) return;
                if (cfg.containsKey("gameDir")) GAME_DIR = Path.of(cfg.get("gameDir").toString());
                if (cfg.containsKey("javaPath")) JAVA_PATH = Path.of(cfg.get("javaPath").toString());
                if (cfg.containsKey("closeOnLaunch")) CLOSE_ON_LAUNCH = (boolean) cfg.get("closeOnLaunch");
                if (cfg.containsKey("netTimeout")) HTTP_TIMEOUT = (int) cfg.get("netTimeout");
                if (cfg.containsKey("autoUpdate")) AUTO_UPDATE_CLIENT = (boolean) cfg.get("autoUpdate");
                if (cfg.containsKey("background")) {
                    Object bg = cfg.get("background");
                    LAUNCHER_BACKGROUND = (bg instanceof String) ? ((String) bg).trim() : "CherryAndRiver.png";
                } else {
                    LAUNCHER_BACKGROUND = "CherryAndRiver.png";
                }
            }
        }

        Path initialBg = LAUNCHER_DIR.resolve("backgrounds").resolve(LAUNCHER_BACKGROUND);
        BackgroundCache.preload(initialBg);

        VERSIONS_DIR = GAME_DIR.resolve("versions");
        LIBRARIES_DIR = GAME_DIR.resolve("libraries");
        ASSETS_DIR = GAME_DIR.resolve("assets");

        UI = new LauncherUI(stage, this::onLaunchClicked, this::onCheckUpdatesClicked);

        DiscordIntegration.start();
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        cancelLaunch();
        backgroundExecutor.shutdownNow();
        scheduledExecutor.shutdownNow();
        DiscordIntegration.stop();
    }

    private void onLaunchClicked(String playerName) {
        Future<?> running = launchFuture.get();
        if (running != null && !running.isDone()) {
            cancelLaunch();
            return;
        }

        Future<?> f = launcherExecutor.submit(() -> {
            try {
                Platform.runLater(() -> {
                    UI.setLaunchingState(true);
                    Timer.startTimer();
                });

                UI.updateProgress("Подготовка к запуску...", 0);

                int threads = Math.max(2, Runtime.getRuntime().availableProcessors());
                int rounds = 3;

                activeDownloadManager.set(DOWNLOAD_MANAGER);

                UI.updateProgress("Запускаю параллельную загрузку (" + threads + " потоков)...", 0);

                if (AUTO_UPDATE_CLIENT) {
                    VersionManager versionManager = new VersionManager();
                    JsonNode versionJson = versionManager.loadVersionJson(MINECRAFT_VERSION);
                    List<FileEntry> files = versionManager.buildRequiredFiles(versionJson, MINECRAFT_VERSION);

                    files.sort(Comparator.comparingInt(fEnt -> {
                        if ("assetIndex".equals(fEnt.kind)) return 0;
                        if ("client".equals(fEnt.kind)) return 1;
                        if ("lib".equals(fEnt.kind)) return 2;
                        if ("natives".equals(fEnt.kind)) return 3;
                        if ("asset".equals(fEnt.kind)) return 4;
                        return 5;
                    }));

                    DOWNLOAD_MANAGER.setTotalBytesPlanned(DOWNLOAD_MANAGER.computeTotalBytesToDownload(files));

                    List<FileEntry> failed = DOWNLOAD_MANAGER.downloadFilesInParallel(files, threads, rounds, true);

                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

                    if (!failed.isEmpty()) {
                        String names = failed.stream().map(x -> x.name).collect(Collectors.joining(", "));
                        LogManager.getLogger().severe("Не удалось скачать некоторые файлы: " + names);
                        throw new IOException("Не удалось скачать некоторые файлы: " + names);
                    }

                    NativesManager nativesManager = new NativesManager(GAME_DIR, MINECRAFT_VERSION);
                    nativesManager.prepareNatives(files);
                }

                NeoForgeManager.ensureInstalledAndReady(GAME_DIR, MINECRAFT_VERSION);

                try {
                    ModsManager.syncMods(GAME_DIR);
                } catch (Exception ex) {
                    LogManager.getLogger().severe("Ошибка синхронизации модов: " + ex.getMessage());
                    Platform.runLater(() -> ErrorPanel.showError("Ошибка синхронизации модов", ex.getMessage()));
                }

                Path serversDat = GAME_DIR.resolve("servers.dat");
                if (!Files.exists(serversDat)) ServersDatGenerator.createServersDat(serversDat);

                ServersDatWriter.ServerEntry mySrv = new ServersDatWriter.ServerEntry("Сервер Minecraft", "architech.mc-world.xyz").withHidden(false);
                List<ServersDatWriter.ServerEntry> list = Collections.singletonList(mySrv);

                Files.createDirectories(serversDat.getParent());
                writeServersDat(serversDat, list);

                Process p = MinecraftLauncher.launchMinecraft(GAME_DIR, "neoforge-" + getInstalledVersion(GAME_DIR));
                currentGameProcess = p;

                UI.updateProgress("Клиент запущен...", 1);

                if (CLOSE_ON_LAUNCH) {
                    Platform.runLater(Platform::exit);
                    return;
                }

                try {
                    while (true) {
                        if (Thread.currentThread().isInterrupted()) {
                            try { p.destroyForcibly(); } catch (Exception ignored) {}
                            throw new InterruptedException();
                        }
                        try {
                            p.exitValue();
                            break;
                        } catch (IllegalThreadStateException itse) {
                            Thread.sleep(500);
                        }
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw ie;
                }

                Platform.runLater(() -> UI.updateProgress("Готово. Клиент завершил работу.", 1));
            } catch (InterruptedException ie) {
                Platform.runLater(() -> UI.updateProgress("Ожидание...", 1.0));
            } catch (Exception e) {
                LogManager.getLogger().severe("Ошибка запуска: " + e.getMessage());
                Platform.runLater(() -> ErrorPanel.showError("Ошибка запуска", e.getMessage()));
            } finally {
                activeDownloadManager.set(null);
                currentGameProcess = null;
                launchFuture.set(null);
                DOWNLOAD_MANAGER.cancelAllDownloads();

                Platform.runLater(() -> {
                    Timer.stopTimer();
                    UI.setLaunchingState(false);
                });
            }
        });

        launchFuture.set(f);
    }

    private void onCheckUpdatesClicked() {
        Future<?> running = updateFuture.get();
        if (running != null && !running.isDone()) {
            running.cancel(true);
            return;
        }

        Future<?> f = launcherExecutor.submit(() -> {
            try {
                Platform.runLater(() -> {
                    UI.setLaunchingState(true);
                    Timer.startTimer();
                });

                UI.updateProgress("Проверка/подготовка обновлений...", 0);

                VersionManager versionManager = new VersionManager();
                JsonNode versionJson = versionManager.loadVersionJson(MINECRAFT_VERSION);
                List<FileEntry> files = versionManager.buildRequiredFiles(versionJson, MINECRAFT_VERSION);

                files.sort(Comparator.comparingInt(fEnt -> {
                    if ("assetIndex".equals(fEnt.kind)) return 0;
                    if ("client".equals(fEnt.kind)) return 1;
                    if ("lib".equals(fEnt.kind)) return 2;
                    if ("natives".equals(fEnt.kind)) return 3;
                    if ("asset".equals(fEnt.kind)) return 4;
                    return 5;
                }));

                activeDownloadManager.set(DOWNLOAD_MANAGER);

                long total = DOWNLOAD_MANAGER.computeTotalBytesToDownload(files);
                DOWNLOAD_MANAGER.setTotalBytesPlanned(total);

                int threads = Math.max(2, Runtime.getRuntime().availableProcessors());
                int rounds = 3;
                UI.updateProgress("Запускаю параллельную загрузку (" + threads + " потоков)...", 0);

                List<FileEntry> failed = DOWNLOAD_MANAGER.downloadFilesInParallel(files, threads, rounds, true);

                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

                if (!failed.isEmpty()) {
                    String names = failed.stream().map(x -> x.name).collect(Collectors.joining(", "));
                    LogManager.getLogger().severe("Не удалось скачать некоторые файлы: " + names);
                    throw new IOException("Не удалось скачать некоторые файлы: " + names);
                }

                NativesManager nativesManager = new NativesManager(GAME_DIR, MINECRAFT_VERSION);
                nativesManager.prepareNatives(files);

                NeoForgeManager.ensureInstalledAndReady(GAME_DIR, MINECRAFT_VERSION);

                try {
                    ModsManager.syncMods(GAME_DIR);
                } catch (Exception ex) {
                    LogManager.getLogger().severe("Ошибка синхронизации модов: " + ex.getMessage());
                    Platform.runLater(() -> ErrorPanel.showError("Ошибка синхронизации модов", ex.getMessage()));
                }

                Path serversDat = GAME_DIR.resolve("servers.dat");
                if (!Files.exists(serversDat)) ServersDatGenerator.createServersDat(serversDat);

                ServersDatWriter.ServerEntry mySrv = new ServersDatWriter.ServerEntry("Сервер Minecraft", "architech.mc-world.xyz").withHidden(false);
                List<ServersDatWriter.ServerEntry> list = Collections.singletonList(mySrv);

                Files.createDirectories(serversDat.getParent());
                writeServersDat(serversDat, list);

                Platform.runLater(() -> UI.updateProgress("Обновления применены — всё готово.", 1.0));

            } catch (InterruptedException ie) {
                Platform.runLater(() -> UI.updateProgress("Операция отменена.", 1.0));
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                LogManager.getLogger().severe("Ошибка при проверке/обновлении: " + e.getMessage());
                Platform.runLater(() -> ErrorPanel.showError("Ошибка обновления", e.getMessage()));
            } finally {
                activeDownloadManager.set(null);
                updateFuture.set(null);
                DOWNLOAD_MANAGER.cancelAllDownloads();
                Platform.runLater(() -> {
                    Timer.stopTimer();
                    UI.setLaunchingState(false);
                });
            }
        });

        updateFuture.set(f);
    }

    public static void cancelLaunch() {
        Future<?> f = launchFuture.getAndSet(null);
        if (f != null && !f.isDone()) f.cancel(true);

        DownloadManager dm = activeDownloadManager.getAndSet(null);
        if (dm != null) dm.cancelAllDownloads();

        if (currentGameProcess != null) {
            try { currentGameProcess.destroyForcibly(); } catch (Exception ignored) {}
        }

        Platform.runLater(() -> {
            if (UI != null) {
                Timer.stopTimer();
                UI.setLaunchingState(false);
            }
        });
    }

    private static ThreadFactory daemonFactory(String prefix) {
        final AtomicInteger id = new AtomicInteger(1);
        return r -> {
            Thread t = new Thread(r, prefix + "-" + id.getAndIncrement());
            t.setDaemon(true);
            return t;
        };
    }

    public static void submitBackground(Runnable r) {
        backgroundExecutor.submit(r);
    }
}