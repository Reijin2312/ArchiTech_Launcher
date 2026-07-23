// SPDX-FileCopyrightText: 2026 Raijin2312
// SPDX-License-Identifier: GPL-3.0-only

package org.architech.launcher;

import static org.architech.launcher.managment.NeoForgeManager.getInstalledVersion;
import static org.architech.launcher.utils.serverinfo.ServersDatWriter.writeServersDat;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.architech.launcher.authentication.account.Account;
import org.architech.launcher.authentication.account.AccountManager;
import org.architech.launcher.authentication.auth.AuthService;
import org.architech.launcher.authentication.auth.JoinTicketService;
import org.architech.launcher.discord.DiscordIntegration;
import org.architech.launcher.gui.BackgroundCache;
import org.architech.launcher.gui.LauncherUI;
import org.architech.launcher.gui.error.ErrorPanel;
import org.architech.launcher.gui.settings.tab.SettingsTab;
import org.architech.launcher.gui.timer.Timer;
import org.architech.launcher.managment.DownloadManager;
import org.architech.launcher.managment.ModsManager;
import org.architech.launcher.managment.NativesManager;
import org.architech.launcher.managment.NeoForgeManager;
import org.architech.launcher.managment.VersionManager;
import org.architech.launcher.process.GameProcessTracker;
import org.architech.launcher.runtime.JavaRuntimeMode;
import org.architech.launcher.runtime.JavaRuntimeResolver;
import org.architech.launcher.utils.*;
import org.architech.launcher.utils.logging.LogManager;
import org.architech.launcher.utils.serverinfo.ServersDatGenerator;
import org.architech.launcher.utils.serverinfo.ServersDatWriter;

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
    public static JavaRuntimeMode JAVA_RUNTIME_MODE = JavaRuntimeMode.BUNDLED;
    public static Path CUSTOM_JAVA_PATH;
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

    private static final ExecutorService launcherExecutor =
            Executors.newSingleThreadExecutor(daemonFactory("launcher-worker"));
    public static final ExecutorService backgroundExecutor = Executors.newCachedThreadPool(daemonFactory("bg"));
    public static final ScheduledExecutorService scheduledExecutor =
            Executors.newSingleThreadScheduledExecutor(daemonFactory("sched"));
    private static final AtomicReference<Future<?>> launchFuture = new AtomicReference<>();
    private static final AtomicReference<Future<?>> updateFuture = new AtomicReference<>();
    private static final AtomicReference<DownloadManager> activeDownloadManager = new AtomicReference<>();
    private static volatile String lastJoinId = null;
    private static volatile GameProcessTracker gameProcessTracker;
    private static final AtomicBoolean launchCancelled = new AtomicBoolean(false);
    private static final AtomicBoolean gameTerminationInProgress = new AtomicBoolean(false);

    @Override
    public void start(Stage stage) throws IOException, URISyntaxException {
        LogManager.setupLogger();

        LAUNCHER_DIR = Paths.get(SettingsTab.class
                        .getProtectionDomain()
                        .getCodeSource()
                        .getLocation()
                        .toURI())
                .getParent();
        CONFIG_PATH = LAUNCHER_DIR.resolve("launcher_config.json");
        ACCOUNT_FILE = LAUNCHER_DIR.resolve(".account.json");
        LEGACY_GAME_LOCK_FILE = LAUNCHER_DIR.resolve(".game.lock.json");

        SettingsTab.createDefaultConfigIfMissing();

        if (Files.exists(CONFIG_PATH)) {
            Map<?, ?> cfg;
            try (Reader r = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
                cfg = Jsons.MAPPER.readValue(r, Map.class);
                if (cfg == null) {
                    cfg = Collections.emptyMap();
                }

                if (cfg.containsKey("gameDir")) {
                    GAME_DIR = Path.of(cfg.get("gameDir").toString());
                }

                JAVA_RUNTIME_MODE = JavaRuntimeMode.fromConfig(cfg.get("javaMode"));

                if (cfg.containsKey("customJavaPath")
                        && cfg.get("customJavaPath") != null
                        && !String.valueOf(cfg.get("customJavaPath")).isBlank()) {
                    CUSTOM_JAVA_PATH = Path.of(String.valueOf(cfg.get("customJavaPath")));
                } else if (!cfg.containsKey("javaMode")
                        && cfg.containsKey("javaPath")
                        && cfg.get("javaPath") != null
                        && !String.valueOf(cfg.get("javaPath")).isBlank()) {

                    JAVA_RUNTIME_MODE = JavaRuntimeMode.CUSTOM;
                    CUSTOM_JAVA_PATH = Path.of(String.valueOf(cfg.get("javaPath")));
                }

                JAVA_PATH = JavaRuntimeResolver.resolveOrBundled(JAVA_RUNTIME_MODE, CUSTOM_JAVA_PATH);

                if (cfg.containsKey("closeOnLaunch")) CLOSE_ON_LAUNCH = (boolean) cfg.get("closeOnLaunch");
                if (cfg.containsKey("netTimeout")) HTTP_TIMEOUT = (int) cfg.get("netTimeout");
                if (cfg.containsKey("autoUpdate")) AUTO_UPDATE_CLIENT = (boolean) cfg.get("autoUpdate");
                if (cfg.containsKey("language"))
                    GAME_LANGUAGE_TAG = mapLanguageTag(String.valueOf(cfg.get("language")));
                if (cfg.containsKey("background")) {
                    Object bg = cfg.get("background");
                    LAUNCHER_BACKGROUND = (bg instanceof String) ? ((String) bg).trim() : "CherryAndRiver.png";
                } else {
                    LAUNCHER_BACKGROUND = "CherryAndRiver.png";
                }
            }
        }

        GAME_LOCK_FILE = GAME_DIR.resolve(".game.lock.json");
        gameProcessTracker =
                new GameProcessTracker(
                        GAME_LOCK_FILE,
                        LEGACY_GAME_LOCK_FILE);

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
                LogManager.getLogger().warning("Ошибка обновления токенов/профиля: " + e.getMessage());
            }
        });

        UI =
                new LauncherUI(
                        stage,
                        this::onLaunchClicked,
                        this::onCheckUpdatesClicked);

        DOWNLOAD_MANAGER.setSnapshotListener(
                snapshot -> {
                    LauncherUI currentUi = UI;
                    if (currentUi != null) {
                        currentUi.updateDownloadSnapshot(snapshot);
                    }
                });

        Optional<ProcessHandle> restoredGame =
                gameProcessTracker.restore(
                        () ->
                                Platform.runLater(
                                        () -> {
                                            if (UI != null) {
                                                UI.setGameRunningState(false);
                                                UI.updateProgress(
                                                        "Готово. Клиент завершил работу.",
                                                        1.0);
                                            }
                                        }));

        if (restoredGame.isPresent()) {
            UI.setGameRunningState(true);
            UI.updateProgress("Игра уже запущена.", 1.0);
        }

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
        launcherExecutor.shutdownNow();
        backgroundExecutor.shutdownNow();
        scheduledExecutor.shutdownNow();
        DiscordIntegration.stop();
    }

    private void onLaunchClicked(String playerName) {
        if (isGameProcessRunning()) {
            requestGameTermination();
            return;
        }

        Future<?> running = launchFuture.get();
        if (running != null && !running.isDone()) {
            cancelLaunch();
            return;
        }

        Future<?> updating = updateFuture.get();
        if (updating != null && !updating.isDone()) {
            cancelUpdate();
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

                    DOWNLOAD_MANAGER.prepareDownloadPlan(files);

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

                ModsManager.syncMods(GAME_DIR);

                checkCancelled();

                Path serversDat = GAME_DIR.resolve("servers.dat");
                if (!Files.exists(serversDat)) ServersDatGenerator.createServersDat(serversDat);

                ServersDatWriter.ServerEntry mySrv =
                        new ServersDatWriter.ServerEntry("ArchiTech MC", MINESERVER_URL).withHidden(false);
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

                Process process =
                        MinecraftLauncher.launchMinecraft(GAME_DIR, "neoforge-" + getInstalledVersion(GAME_DIR));

                String completedJoinId = lastJoinId;

                gameProcessTracker.track(process, GAME_DIR, () -> {
                    Account accountOnExit = AccountManager.getCurrentAccount();

                    if (completedJoinId != null && accountOnExit != null) {
                        submitBackground(() -> {
                            try {
                                JoinTicketService.consume(accountOnExit, completedJoinId);
                            } catch (Exception error) {
                                LogManager.getLogger()
                                        .warning("Не удалось завершить join ticket: " + error.getMessage());
                            }
                        });
                    }

                    Platform.runLater(
                            () -> {
                                if (UI != null) {
                                    UI.setGameRunningState(false);
                                    UI.updateProgress(
                                            "Готово. Клиент завершил работу.",
                                            1.0);
                                }
                            });
                });

                lastJoinId = null;

                UI.setGameRunningState(true);
                UI.updateProgress("Клиент запущен...", 1.0);

                if (CLOSE_ON_LAUNCH) {
                    Platform.runLater(Platform::exit);
                }
            } catch (InterruptedException ie) {
                Platform.runLater(() -> UI.updateProgress("Отмена...", 1.0));
            } catch (Exception e) {
                LogManager.getLogger().severe("Ошибка при запуске игры: " + e.getMessage());
                Platform.runLater(() -> ErrorPanel.showError("Ошибка при запуске игры", e.getMessage()));
            } finally {
                activeDownloadManager.set(null);
                launchFuture.set(null);
                DOWNLOAD_MANAGER.cancelAllDownloads();

                Platform.runLater(
                        () -> {
                            Timer.stopTimer();
                            if (UI != null) {
                                UI.setDownloadPauseAvailable(false);
                                if (isGameProcessRunning()) {
                                    UI.setGameRunningState(true);
                                } else {
                                    UI.setLaunchingState(false);
                                }
                            }
                        });

                String pendingJoinId = lastJoinId;
                lastJoinId = null;
                Account account = AccountManager.getCurrentAccount();

                if (pendingJoinId != null && account != null) {
                    submitBackground(() -> {
                        try {
                            JoinTicketService.consume(account, pendingJoinId);
                        } catch (Exception error) {
                            LogManager.getLogger()
                                    .warning("Не удалось отменить неиспользованный join ticket: " + error.getMessage());
                        }
                    });
                }
            }
        });

        launchFuture.set(f);
    }

    private void onCheckUpdatesClicked() {
        Future<?> running = updateFuture.get();
        if (running != null && !running.isDone()) {
            cancelUpdate();
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

                DOWNLOAD_MANAGER.prepareDownloadPlan(files);

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

                ModsManager.syncMods(GAME_DIR);

                Path serversDat = GAME_DIR.resolve("servers.dat");
                if (!Files.exists(serversDat)) ServersDatGenerator.createServersDat(serversDat);

                ServersDatWriter.ServerEntry mySrv =
                        new ServersDatWriter.ServerEntry("ArchiTech MC", MINESERVER_URL).withHidden(false);
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
                Platform.runLater(
                        () -> {
                            Timer.stopTimer();
                            if (UI != null) {
                                UI.setDownloadPauseAvailable(false);
                                UI.setLaunchingState(false);
                            }
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

        Platform.runLater(
                () -> {
                    if (UI != null) {
                        Timer.stopTimer();
                        UI.setDownloadPauseAvailable(false);
                        UI.setLaunchingState(false);
                    }
                });
    }

    private static void cancelUpdate() {
        Future<?> future = updateFuture.getAndSet(null);
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }

        DownloadManager manager = activeDownloadManager.getAndSet(null);
        if (manager != null) {
            manager.cancelAllDownloads();
        }

        Platform.runLater(
                () -> {
                    if (UI != null) {
                        Timer.stopTimer();
                        UI.setDownloadPauseAvailable(false);
                        UI.setLaunchingState(false);
                        UI.updateProgress("Операция отменена.", 1.0);
                    }
                });
    }

    public static void toggleDownloadPause() {
        DownloadManager manager = activeDownloadManager.get();
        if (manager == null || !manager.hasActiveDownloadSession()) {
            Platform.runLater(
                    () -> {
                        if (UI != null) {
                            UI.setDownloadPauseAvailable(false);
                        }
                    });
            return;
        }

        boolean paused = manager.togglePause();
        Platform.runLater(
                () -> {
                    if (UI != null) {
                        UI.setDownloadPaused(paused);
                        UI.updateProgress(
                                paused
                                        ? "Загрузка приостановлена"
                                        : "Загрузка продолжена",
                                -1);
                    }
                });
    }

    private void requestGameTermination() {
        if (!gameTerminationInProgress.compareAndSet(false, true)) {
            return;
        }

        Platform.runLater(
                () -> {
                    if (UI != null) {
                        UI.setGameStoppingState();
                        UI.updateProgress(
                                "Завершаю клиент...",
                                -1);
                    }
                });

        submitBackground(
                () -> {
                    boolean terminated = false;
                    String failureMessage = null;

                    try {
                        terminated = terminateRunningGame();
                        if (!terminated && isGameProcessRunning()) {
                            failureMessage =
                                    "Не удалось завершить процесс Minecraft.";
                        }
                    } catch (Exception error) {
                        failureMessage = error.getMessage();
                    } finally {
                        gameTerminationInProgress.set(false);
                    }

                    boolean stopped =
                            terminated || !isGameProcessRunning();
                    String finalFailureMessage = failureMessage;

                    Platform.runLater(
                            () -> {
                                if (UI == null) {
                                    return;
                                }

                                if (stopped) {
                                    UI.setGameRunningState(false);
                                    UI.updateProgress(
                                            "Клиент завершён.",
                                            1.0);
                                } else {
                                    UI.setGameRunningState(true);
                                    ErrorPanel.showError(
                                            "Не удалось завершить игру",
                                            finalFailureMessage == null
                                                    ? "Процесс Minecraft продолжает работать."
                                                    : finalFailureMessage);
                                }
                            });
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

    private static boolean isGameProcessRunning() {
        return gameProcessTracker != null && gameProcessTracker.isRunning();
    }

    public static boolean terminateRunningGame() {
        return gameProcessTracker != null && gameProcessTracker.terminate(java.time.Duration.ofSeconds(5));
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
