// SPDX-FileCopyrightText: 2026 Raijin2312
// SPDX-License-Identifier: GPL-3.0-only

package org.architech.launcher.managment;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.architech.launcher.ArchiTechLauncher;
import org.architech.launcher.utils.FileEntry;
import org.architech.launcher.utils.Utils;
import org.architech.launcher.utils.logging.LogManager;

public class DownloadManager {
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    private static final long ROUND_TIMEOUT_MINUTES = 10;

    private final AtomicLong totalBytesPlanned = new AtomicLong();
    private final AtomicLong totalBytesDone = new AtomicLong();

    private static final AtomicInteger DOWNLOAD_THREAD_SEQUENCE = new AtomicInteger(1);
    private static final Object[] FILE_LOCK_STRIPES = createLockStripes(256);

    private final ConcurrentMap<Path, Closeable> activeDownloads = new ConcurrentHashMap<>();
    private final ConcurrentMap<ExecutorService, Boolean> activePools = new ConcurrentHashMap<>();

    public void setTotalBytesPlanned(long total) {
        totalBytesPlanned.set(Math.max(0, total));
    }

    public long getTotalBytesPlanned() {
        return totalBytesPlanned.get();
    }

    public long getTotalBytesDone() {
        return totalBytesDone.get();
    }

    public long computeTotalBytesToDownload(List<FileEntry> files) throws Exception {
        Objects.requireNonNull(files, "files");

        long total = 0;
        for (FileEntry file : files) {
            validateEntry(file);
            if (isFileValid(file)) {
                continue;
            }

            long size = file.size > 0 ? file.size : Utils.tryHeadSize(file.url);
            if (size > 0) {
                total = Math.addExact(total, size);
            }
        }
        return total;
    }

    public void ensureFilePresentAndValid(FileEntry file, boolean updateUI) throws Exception {
        validateEntry(file);
        boolean ok = downloadWithRoundsSingleFile(file, 3, 2_000, updateUI);
        if (ok) {
            return;
        }

        if (ArchiTechLauncher.UI != null) {
            ArchiTechLauncher.UI.updateProgress("Ожидание...", 1);
        }
        LogManager.getLogger().warning("Файл не удалось корректно скачать: " + file.name);
        throw new IOException("Файл не удалось корректно скачать: " + file.name);
    }

    public List<FileEntry> downloadFilesInParallel(List<FileEntry> files, int threads, int rounds, boolean updateUI)
            throws InterruptedException {
        Objects.requireNonNull(files, "files");

        int workerCount = Math.max(1, threads);
        int maxRounds = Math.max(1, rounds);
        List<FileEntry> remaining = new ArrayList<>();

        for (FileEntry file : files) {
            try {
                validateEntry(file);
                if (!isFileValid(file)) {
                    remaining.add(file);
                }
            } catch (Exception failure) {
                LogManager.getLogger()
                        .warning("Не удалось проверить файл " + safeName(file) + ": " + failure.getMessage());
                remaining.add(file);
            }
        }

        for (int round = 1; round <= maxRounds && !remaining.isEmpty(); round++) {
            ExecutorService pool = Executors.newFixedThreadPool(workerCount, runnable -> {
                Thread thread = new Thread(runnable, "download-worker-" + DOWNLOAD_THREAD_SEQUENCE.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            });
            activePools.put(pool, Boolean.TRUE);

            List<DownloadTask> tasks = new ArrayList<>(remaining.size());
            try {
                for (FileEntry file : remaining) {
                    Future<FileEntry> future = pool.submit(() -> {
                        try {
                            Path parent = file.path.getParent();
                            if (parent != null) {
                                Files.createDirectories(parent);
                            }
                            return downloadWithRoundsSingleFile(file, 1, 0, updateUI) ? null : file;
                        } catch (Exception failure) {
                            LogManager.getLogger()
                                    .warning("Ошибка скачивания " + safeName(file) + ": " + failure.getMessage());
                            return file;
                        }
                    });
                    tasks.add(new DownloadTask(file, future));
                }

                pool.shutdown();
                boolean finished = pool.awaitTermination(ROUND_TIMEOUT_MINUTES, TimeUnit.MINUTES);
                if (!finished) {
                    pool.shutdownNow();
                }
            } catch (InterruptedException interrupted) {
                cancelTasks(tasks);
                pool.shutdownNow();
                throw interrupted;
            } finally {
                activePools.remove(pool);
            }

            List<FileEntry> next = collectFailures(tasks);
            if (!next.isEmpty() && round < maxRounds) {
                Thread.sleep(1_000L * round);
            }
            remaining = next;
        }

        return List.copyOf(remaining);
    }

    private List<FileEntry> collectFailures(List<DownloadTask> tasks) throws InterruptedException {
        List<FileEntry> failed = new ArrayList<>();
        for (DownloadTask task : tasks) {
            Future<FileEntry> future = task.future();
            if (!future.isDone()) {
                future.cancel(true);
                failed.add(task.file());
                continue;
            }

            try {
                FileEntry result = future.get();
                if (result != null) {
                    failed.add(result);
                }
            } catch (CancellationException cancelled) {
                failed.add(task.file());
            } catch (ExecutionException execution) {
                Throwable cause = execution.getCause();
                if (cause instanceof Error error) {
                    throw error;
                }
                LogManager.getLogger()
                        .warning("Фоновая загрузка завершилась ошибкой для " + safeName(task.file()) + ": "
                                + (cause == null ? execution.getMessage() : cause.getMessage()));
                failed.add(task.file());
            }
        }
        return failed;
    }

    private boolean downloadWithRoundsSingleFile(
            FileEntry file, int maxAttempts, long waitBetweenAttemptsMs, boolean updateUI) {
        Path key = file.path.toAbsolutePath().normalize();
        Object lock = FILE_LOCK_STRIPES[Math.floorMod(key.hashCode(), FILE_LOCK_STRIPES.length)];

        synchronized (lock) {
            if (isFileValid(file)) {
                return true;
            }

            int attempts = Math.max(1, maxAttempts);
            for (int attempt = 1; attempt <= attempts; attempt++) {
                if (Thread.currentThread().isInterrupted()) {
                    return false;
                }
                try {
                    downloadAtomicOnce(file, updateUI);
                    if (isFileValid(file)) {
                        return true;
                    }
                    LogManager.getLogger().warning("Скачанный файл не прошёл финальную проверку: " + file.name);
                } catch (Exception failure) {
                    LogManager.getLogger()
                            .warning("Попытка скачивания " + attempt + " провалилась для файла " + file.name + ": "
                                    + failure.getMessage());
                }

                deleteTemporaryFile(file.path);
                if (attempt < attempts && waitBetweenAttemptsMs > 0) {
                    try {
                        Thread.sleep(waitBetweenAttemptsMs);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }
            return false;
        }
    }

    private void downloadAtomicOnce(FileEntry file, boolean updateUI) throws Exception {
        URI uri = new URI(file.url);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IOException("Unsupported protocol for URL: " + file.url);
        }

        URLConnection rawConnection = uri.toURL().openConnection();
        if (!(rawConnection instanceof HttpURLConnection connection)) {
            throw new IOException("Unsupported protocol for URL: " + file.url);
        }

        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "ArchiTech-Launcher/1.0");

        Path parent = file.path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = temporaryPath(file.path);
        Path downloadKey = file.path.toAbsolutePath().normalize();

        long addedToGlobal = 0;
        long downloadedFile = 0;
        try {
            connection.connect();
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("HTTP error " + status + " for " + file.url);
            }

            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                    OutputStream output = new BufferedOutputStream(Files.newOutputStream(
                            temporary,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.WRITE))) {
                activeDownloads.put(downloadKey, input);
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (!activeDownloads.containsKey(downloadKey)) {
                        throw new IOException("Загрузка прервана: " + file.name);
                    }

                    output.write(buffer, 0, read);
                    downloadedFile += read;
                    addedToGlobal += read;
                    totalBytesDone.addAndGet(read);
                    updateProgress(file, downloadedFile, updateUI);
                }
                output.flush();
            } finally {
                activeDownloads.remove(downloadKey);
            }

            validateDownloadedTemporaryFile(file, temporary, downloadedFile);
            moveAtomically(temporary, file.path);
            Files.setLastModifiedTime(file.path, FileTime.from(Instant.now()));
        } catch (Exception failure) {
            if (addedToGlobal > 0) {
                totalBytesDone.addAndGet(-addedToGlobal);
            }
            Files.deleteIfExists(temporary);
            throw failure;
        } finally {
            activeDownloads.remove(downloadKey);
            connection.disconnect();
        }
    }

    private void validateDownloadedTemporaryFile(FileEntry file, Path temporary, long downloadedBytes)
            throws Exception {
        if (file.size > 0 && downloadedBytes != file.size) {
            throw new IOException("Размер не совпал для файла " + file.name
                    + ": скачано " + downloadedBytes
                    + ", ожидалось " + file.size);
        }

        if (hasText(file.sha256)) {
            String actual = Utils.sha256Hex(temporary);
            if (!file.sha256.equalsIgnoreCase(actual)) {
                throw new IOException("SHA-256 не совпал для файла " + file.name);
            }
        } else if (hasText(file.sha1)) {
            String actual = Utils.sha1Hex(temporary);
            if (!file.sha1.equalsIgnoreCase(actual)) {
                throw new IOException("SHA-1 не совпал для файла " + file.name);
            }
        }
    }

    private void updateProgress(FileEntry file, long downloadedFile, boolean updateUI) {
        if (!updateUI || ArchiTechLauncher.UI == null) {
            return;
        }

        long planned = totalBytesPlanned.get();
        long done = totalBytesDone.get();
        double globalProgress = planned > 0 ? Math.min(1.0, Math.max(0.0, (double) done / planned)) : -1;
        String text =
                file.name + " (" + (downloadedFile / 1024) + (file.size > 0 ? " / " + (file.size / 1024) : "") + " КБ)";
        ArchiTechLauncher.UI.updateProgress(text, globalProgress);
    }

    private boolean isFileValid(FileEntry file) {
        try {
            if (file == null || file.path == null || !Files.isRegularFile(file.path)) {
                return false;
            }
            if (hasText(file.sha256)) {
                return file.sha256.equalsIgnoreCase(Utils.sha256Hex(file.path));
            }
            if (hasText(file.sha1)) {
                return file.sha1.equalsIgnoreCase(Utils.sha1Hex(file.path));
            }
            if (file.size > 0) {
                return Files.size(file.path) == file.size;
            }
            return true;
        } catch (Exception failure) {
            LogManager.getLogger().warning("Найден невалидный файл: " + safePath(file) + ": " + failure.getMessage());
            return false;
        }
    }

    public void resetTotals() {
        totalBytesPlanned.set(0);
        totalBytesDone.set(0);
    }

    public void cancelAllDownloads() {
        for (Closeable closeable : activeDownloads.values()) {
            try {
                closeable.close();
            } catch (Exception failure) {
                LogManager.getLogger().fine("Не удалось закрыть загрузку: " + failure.getMessage());
            }
        }
        for (ExecutorService pool : activePools.keySet()) {
            pool.shutdownNow();
        }

        activeDownloads.clear();
        activePools.clear();
        resetTotals();
    }

    private static void validateEntry(FileEntry file) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(file.path, "file.path");
        if (file.url == null || file.url.isBlank()) {
            throw new IllegalArgumentException("file.url must not be blank");
        }
        if (file.name == null || file.name.isBlank()) {
            file.name = file.path.getFileName() == null
                    ? file.path.toString()
                    : file.path.getFileName().toString();
        }
    }

    private static void cancelTasks(List<DownloadTask> tasks) {
        for (DownloadTask task : tasks) {
            task.future().cancel(true);
        }
    }

    private static void deleteTemporaryFile(Path target) {
        try {
            Files.deleteIfExists(temporaryPath(target));
        } catch (IOException failure) {
            LogManager.getLogger()
                    .warning(
                            "Не удалось удалить временный файл " + temporaryPath(target) + ": " + failure.getMessage());
        }
    }

    private static Path temporaryPath(Path target) {
        return target.resolveSibling(target.getFileName().toString() + ".part");
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Object[] createLockStripes(int count) {
        Object[] stripes = new Object[count];
        for (int index = 0; index < count; index++) {
            stripes[index] = new Object();
        }
        return stripes;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String safeName(FileEntry file) {
        return file == null ? "<null>" : String.valueOf(file.name);
    }

    private static String safePath(FileEntry file) {
        return file == null ? "<null>" : String.valueOf(file.path);
    }

    private record DownloadTask(FileEntry file, Future<FileEntry> future) {}
}
