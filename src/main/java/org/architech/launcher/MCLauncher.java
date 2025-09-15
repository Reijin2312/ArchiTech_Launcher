package org.architech.launcher;

import com.google.gson.JsonObject;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.architech.launcher.discord.DiscordIntegration;
import org.architech.launcher.gui.tab.SettingsTab;
import org.architech.launcher.gui.LauncherUI;
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

import static org.architech.launcher.gui.tab.SettingsTab.GSON;
import static org.architech.launcher.managment.NeoForgeManager.getInstalledVersion;
import static org.architech.launcher.utils.serverinfo.ServersDatWriter.writeServersDat;

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
    public static String BACKEND_URL;
    public static boolean closeOnLaunch = false;

    public static LauncherUI UI;
    public static DownloadManager DOWNLOAD_MANAGER = null;

    private static final ExecutorService launcherExecutor = Executors.newSingleThreadExecutor(daemonFactory("launcher-worker"));
    private static final ExecutorService backgroundExecutor = Executors.newCachedThreadPool(daemonFactory("bg"));
    public static final ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor(daemonFactory("sched"));
    private static final AtomicReference<Future<?>> launchFuture = new AtomicReference<>();
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
                cfg = GSON.fromJson(r, Map.class);
                if (cfg == null) return;
                if (cfg.containsKey("gameDir")) GAME_DIR = Path.of(cfg.get("gameDir").toString());
                if (cfg.containsKey("javaPath")) JAVA_PATH = Path.of(cfg.get("javaPath").toString());
                if (cfg.containsKey("closeOnLaunch")) closeOnLaunch = (boolean) cfg.get("closeOnLaunch");
            }
        }

        VERSIONS_DIR = GAME_DIR.resolve("versions");
        LIBRARIES_DIR = GAME_DIR.resolve("libraries");
        ASSETS_DIR = GAME_DIR.resolve("assets");

        UI = new LauncherUI(stage, this::onLaunchClicked);

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
                    UI.startTimer();
                });

                UI.updateProgress("Подготовка к запуску...", 0);

                VersionManager versionManager = new VersionManager();
                JsonObject versionJson = versionManager.loadVersionJson(MINECRAFT_VERSION);
                List<FileEntry> files = versionManager.buildRequiredFiles(versionJson, MINECRAFT_VERSION);

                files.sort(Comparator.comparingInt(fEnt -> {
                    if ("assetIndex".equals(fEnt.kind)) return 0;
                    if ("client".equals(fEnt.kind)) return 1;
                    if ("lib".equals(fEnt.kind)) return 2;
                    if ("natives".equals(fEnt.kind)) return 3;
                    if ("asset".equals(fEnt.kind)) return 4;
                    return 5;
                }));

                DOWNLOAD_MANAGER = new DownloadManager();
                activeDownloadManager.set(DOWNLOAD_MANAGER);

                long total = DOWNLOAD_MANAGER.computeTotalBytesToDownload(files);
                DOWNLOAD_MANAGER.setTotalBytesPlanned(total);

                int threads = Math.max(2, Runtime.getRuntime().availableProcessors());
                int rounds = 3;
                UI.updateProgress("Запускаю параллельную загрузку (" + threads + " потоков)...", 0);

                List<FileEntry> failed = DOWNLOAD_MANAGER.downloadFilesInParallel(files, threads, rounds);

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
                    Platform.runLater(() -> LauncherUI.showError("Ошибка синхронизации модов", ex.getMessage()));
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

                try {
                    while (true) {
                        if (Thread.currentThread().isInterrupted()) {
                            try { p.destroyForcibly(); } catch (Exception ignored) {}
                            throw new InterruptedException();
                        }
                        try {
                            int exit = p.exitValue();
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
                Platform.runLater(() -> LauncherUI.showError("Ошибка запуска", e.getMessage()));
            } finally {
                activeDownloadManager.set(null);
                currentGameProcess = null;
                launchFuture.set(null);
                if (DOWNLOAD_MANAGER != null) DOWNLOAD_MANAGER.cancelAllDownloads();

                Platform.runLater(() -> {
                    UI.stopTimer();
                    UI.setLaunchingState(false);
                });
            }
        });

        launchFuture.set(f);
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
                UI.stopTimer();
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

    public static <T> Future<T> submitBackground(Callable<T> c) {
        return backgroundExecutor.submit(c);
    }
}