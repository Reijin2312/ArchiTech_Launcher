package org.architech.launcher;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.architech.launcher.authentication.auth.AuthService;
import org.architech.launcher.discord.DiscordIntegration;
import org.architech.launcher.gui.BackgroundCache;
import org.architech.launcher.gui.error.ErrorPanel;
import org.architech.launcher.gui.settings.tab.SettingsTab;
import org.architech.launcher.gui.LauncherUI;
import org.architech.launcher.authentication.account.Account;
import org.architech.launcher.authentication.account.AccountManager;
import org.architech.launcher.authentication.auth.JoinTicketService;
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
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.ProcessHandle;

import static org.architech.launcher.managment.NeoForgeManager.getInstalledVersion;
import static org.architech.launcher.utils.serverinfo.ServersDatWriter.writeServersDat;

public class ArchiTechLauncher extends Application {

    public static final String MINECRAFT_VERSION = "1.21.1";

    public static Path GAME_DIR = Paths.get(System.getProperty("user.home"), ".architech");
    public static String FRONTEND_URL = "https://architech-mc.ru";
    public static String MINESERVER_URL = "architech-mc.online";
    public static String BACKEND_URL = "https://launcher.architech-mc.ru";
    public static Path CONFIG_PATH;
    public static Path LAUNCHER_DIR;
    public static Path VERSIONS_DIR;
    public static Path LIBRARIES_DIR;
    public static Path ASSETS_DIR;
    public static Path JAVA_PATH;
    public static Path ACCOUNT_FILE;
    public static Path LEGACY_GAME_LOCK_FILE;
    public static Path GAME_LOCK_FILE;
    public static String GAME_LANGUAGE_TAG = "ru-RU";
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
    private static volatile String lastJoinId = null;
    private static volatile ProcessHandle trackedProcessHandle = null;
    private static final AtomicBoolean launchCancelled = new AtomicBoolean(false);

    @Override
    public void start(Stage stage) throws IOException, URISyntaxException {
        LogManager.setupLogger();

        LAUNCHER_DIR = Paths.get(SettingsTab.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
        CONFIG_PATH = LAUNCHER_DIR.resolve("launcher_config.json");
        ACCOUNT_FILE = LAUNCHER_DIR.resolve(".account.json");
        LEGACY_GAME_LOCK_FILE = LAUNCHER_DIR.resolve(".game.lock.json");

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
                if (cfg.containsKey("language")) GAME_LANGUAGE_TAG = mapLanguageTag(String.valueOf(cfg.get("language")));
                if (cfg.containsKey("background")) {
                    Object bg = cfg.get("background");
                    LAUNCHER_BACKGROUND = (bg instanceof String) ? ((String) bg).trim() : "CherryAndRiver.png";
                } else {
                    LAUNCHER_BACKGROUND = "CherryAndRiver.png";
                }
            }
        }
        GAME_LOCK_FILE = GAME_DIR.resolve(".game.lock.json");
        loadExistingGameLock();
        Files.createDirectories(ACCOUNT_FILE.getParent());

        Path initialBg = LAUNCHER_DIR.resolve("backgrounds").resolve(LAUNCHER_BACKGROUND);
        BackgroundCache.preload(initialBg);

        VERSIONS_DIR = GAME_DIR.resolve("versions");
        LIBRARIES_DIR = GAME_DIR.resolve("libraries");
        ASSETS_DIR = GAME_DIR.resolve("assets");

        backgroundExecutor.submit(() -> {
            try {
                if (AuthService.ensureValidTokens()) {
                    AuthService.updateProfile();
                    Platform.runLater(() -> {
                        if (UI != null) {
                            UI.refreshAccountDisplay();
                        }
                    });
                }
            } catch (Exception e) {
                LogManager.getLogger().warning("РћС€РёР±РєР° РѕР±РЅРѕРІР»РµРЅРёСЏ С‚РѕРєРµРЅРѕРІ/РїСЂРѕС„РёР»СЏ: " + e.getMessage());
            }
        });

        UI = new LauncherUI(stage, this::onLaunchClicked, this::onCheckUpdatesClicked);

        DiscordIntegration.start();
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        Future<?> updating = updateFuture.getAndSet(null);
        if (updating != null && !updating.isDone()) {
            updating.cancel(true);
        }
        DownloadManager dm = activeDownloadManager.getAndSet(null);
        if (dm != null) dm.cancelAllDownloads();
        DOWNLOAD_MANAGER.cancelAllDownloads();
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
        if (isGameProcessRunning()) {
            Platform.runLater(() -> ErrorPanel.showError("Игра уже запущена", "Закройте текущий клиент перед новым запуском."));
            return;
        }

        Future<?> f = launcherExecutor.submit(() -> {
            try {
                launchCancelled.set(false);
                checkCancelled();

                if (!AuthService.ensureValidTokens(true)) {
                    Platform.runLater(() -> UI.updateProgress("Требуется авторизация", 1.0));
                    return;
                }
                checkCancelled();

                Platform.runLater(() -> {
                    UI.setLaunchingState(true);
                    Timer.startTimer();
                });

                UI.updateProgress("Подготовка к запуску...", 0);
                checkCancelled();

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

                    checkCancelled();

                    if (!failed.isEmpty()) {
                        String names = failed.stream().map(x -> x.name).collect(Collectors.joining(", "));
                        LogManager.getLogger().severe("Не удалось скачать следующие файлы: " + names);
                        throw new IOException("Не удалось скачать следующие файлы: " + names);
                    }

                    NativesManager nativesManager = new NativesManager(GAME_DIR, MINECRAFT_VERSION);
                    nativesManager.prepareNatives(files);
                }

                checkCancelled();
                NeoForgeManager.ensureInstalledAndReady(GAME_DIR, MINECRAFT_VERSION);
                checkCancelled();

                try {
                    ModsManager.syncMods(GAME_DIR);
                } catch (Exception ex) {
                    LogManager.getLogger().severe("Ошибка синхронизации модов: " + ex.getMessage());
                    Platform.runLater(() -> ErrorPanel.showError("Ошибка синхронизации модов", ex.getMessage()));
                }

                checkCancelled();

                Path serversDat = GAME_DIR.resolve("servers.dat");
                if (!Files.exists(serversDat)) ServersDatGenerator.createServersDat(serversDat);

                ServersDatWriter.ServerEntry mySrv = new ServersDatWriter.ServerEntry("ArchiTech MC", MINESERVER_URL).withHidden(false);
                List<ServersDatWriter.ServerEntry> list = Collections.singletonList(mySrv);

                Files.createDirectories(serversDat.getParent());
                writeServersDat(serversDat, list);

                Account account = AccountManager.getCurrentAccount();
                if (account == null) {
                    throw new IllegalStateException("Please login in the launcher before starting the game.");
                }
                UI.updateProgress("Запрашиваем доступ к серверу...", 0.9);
                var jt = JoinTicketService.request(account, mySrv.ip);
                lastJoinId = jt != null ? jt.joinId() : null;

                Process p = MinecraftLauncher.launchMinecraft(GAME_DIR, "neoforge-" + getInstalledVersion(GAME_DIR));
                currentGameProcess = p;
                trackedProcessHandle = p.toHandle();
                persistGameLock(p);

                UI.updateProgress("Клиент запущен...", 1);

                if (CLOSE_ON_LAUNCH) {
                    Platform.runLater(Platform::exit);
                    return;
                }

                try {
                    while (true) {
                        if (launchCancelled.get() || Thread.currentThread().isInterrupted()) {
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
                Platform.runLater(() -> UI.updateProgress("Отмена...", 1.0));
            } catch (Exception e) {
                LogManager.getLogger().severe("Ошибка при запуске игры: " + e.getMessage());
                Platform.runLater(() -> ErrorPanel.showError("Ошибка при запуске игры", e.getMessage()));
            } finally {
                activeDownloadManager.set(null);
                currentGameProcess = null;
                trackedProcessHandle = null;
                launchFuture.set(null);
                clearGameLock();
                DOWNLOAD_MANAGER.cancelAllDownloads();

                Platform.runLater(() -> {
                    Timer.stopTimer();
                    UI.setLaunchingState(false);
                });
                String joinIdToConsume = lastJoinId;
                lastJoinId = null;
                Account acc = AccountManager.getCurrentAccount();
                backgroundExecutor.submit(() -> {
                    try { JoinTicketService.consume(acc, joinIdToConsume); }
                    catch (Exception ignored) {}
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
                    LogManager.getLogger().severe("Не удалось скачать следующие файлы: " + names);
                    throw new IOException("Не удалось скачать следующие файлы: " + names);
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

                ServersDatWriter.ServerEntry mySrv = new ServersDatWriter.ServerEntry("ArchiTech MC", MINESERVER_URL).withHidden(false);
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
        launchCancelled.set(true);
        Future<?> f = launchFuture.getAndSet(null);
        if (f != null && !f.isDone()) f.cancel(true);

        DownloadManager dm = activeDownloadManager.getAndSet(null);
        if (dm != null) dm.cancelAllDownloads();

        if (currentGameProcess != null) {
            try { currentGameProcess.destroyForcibly(); } catch (Exception ignored) {}
        }
        clearGameLock();
        trackedProcessHandle = null;
        lastJoinId = null;

        Platform.runLater(() -> {
            if (UI != null) {
                Timer.stopTimer();
                UI.setLaunchingState(false);
            }
        });
    }

    private static void checkCancelled() throws InterruptedException {
        if (launchCancelled.get() || Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("cancelled");
        }
    }

    public static String mapLanguageTag(String value) {
        if (value == null) return GAME_LANGUAGE_TAG;
        String v = value.toLowerCase(Locale.ROOT);
        if (v.contains("ru")) return "ru-RU";
        if (v.contains("en")) return "en-US";
        return GAME_LANGUAGE_TAG == null ? "ru-RU" : GAME_LANGUAGE_TAG;
    }

    private static void loadExistingGameLock() {
        try {
            Path lockPath = GAME_LOCK_FILE;
            if (lockPath == null || !Files.exists(lockPath)) {
                if (LEGACY_GAME_LOCK_FILE != null && Files.exists(LEGACY_GAME_LOCK_FILE)) {
                    lockPath = LEGACY_GAME_LOCK_FILE;
                } else {
                    return;
                }
            }
            Map<?,?> lock = Jsons.MAPPER.readValue(Files.readString(lockPath), Map.class);
            Object pidObj = lock.get("pid");
            if (pidObj instanceof Number num) {
                long pid = num.longValue();
                final Path lp = lockPath;
                ProcessHandle.of(pid).ifPresent(ph -> {
                    if (ph.isAlive()) {
                        trackedProcessHandle = ph;
                        // migrate legacy lock forward for consistency
                        if (!lp.equals(GAME_LOCK_FILE) && GAME_LOCK_FILE != null) {
                            try {
                                Files.createDirectories(GAME_LOCK_FILE.getParent());
                                Files.copy(lp, GAME_LOCK_FILE);
                            } catch (Exception ignored) {}
                        }
                    } else {
                        clearGameLock();
                    }
                });
            }
        } catch (Exception ignored) {
        }
    }

    private static boolean isGameProcessRunning() {
        try {
            if (currentGameProcess != null && currentGameProcess.isAlive()) return true;
        } catch (Exception ignored) {}
        if (trackedProcessHandle != null && trackedProcessHandle.isAlive()) return true;
        loadExistingGameLock();
        return trackedProcessHandle != null && trackedProcessHandle.isAlive();
    }

    private static void persistGameLock(Process p) {
        if (GAME_LOCK_FILE == null || p == null) return;
        try {
            Map<String, Object> lock = new LinkedHashMap<>();
            lock.put("pid", p.pid());
            lock.put("ts", Instant.now().toString());
            Files.createDirectories(GAME_LOCK_FILE.getParent());
            Files.writeString(GAME_LOCK_FILE, Jsons.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(lock), StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
    }

    private static void clearGameLock() {
        try {
            if (GAME_LOCK_FILE != null) Files.deleteIfExists(GAME_LOCK_FILE);
            if (LEGACY_GAME_LOCK_FILE != null) Files.deleteIfExists(LEGACY_GAME_LOCK_FILE);
        } catch (Exception ignored) {}
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










