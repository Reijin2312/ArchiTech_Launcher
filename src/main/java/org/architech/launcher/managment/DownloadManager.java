// SPDX-FileCopyrightText: 2026 Raijin2312
// SPDX-License-Identifier: GPL-3.0-only

package org.architech.launcher.managment;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

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
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocketFactory;
import org.architech.launcher.ArchiTechLauncher;
import org.architech.launcher.utils.FileEntry;
import org.architech.launcher.utils.Utils;
import org.architech.launcher.utils.logging.LogManager;

public class DownloadManager {
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    private static final long SNAPSHOT_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(100);
    private static final double SPEED_SMOOTHING = 0.28;

    private static final AtomicInteger DOWNLOAD_THREAD_SEQUENCE = new AtomicInteger(1);
    private static final ConcurrentHashMap<Path, Object> FILE_LOCKS = new ConcurrentHashMap<>();

    private final AtomicLong totalBytesPlanned = new AtomicLong();
    private final AtomicLong totalBytesDone = new AtomicLong();
    private final AtomicLong totalBytesWritten = new AtomicLong();
    private final AtomicLong totalDiskBytesPlanned = new AtomicLong();

    private final ConcurrentMap<Path, Closeable> activeDownloads = new ConcurrentHashMap<>();
    private final ConcurrentMap<ExecutorService, Boolean> activePools = new ConcurrentHashMap<>();
    private final AtomicInteger activeDownloadSessions = new AtomicInteger();
    private final AtomicReference<String> currentFile = new AtomicReference<>("");

    private final Object pauseMonitor = new Object();
    private final AtomicBoolean paused = new AtomicBoolean(false);

    private final Object metricsMonitor = new Object();
    private long lastSpeedSampleNanos = System.nanoTime();
    private long lastSpeedSampleBytes;
    private double smoothedBytesPerSecond;
    private long lastSnapshotNanos;

    private volatile Consumer<DownloadSnapshot> snapshotListener = snapshot -> {};

    public void setSnapshotListener(Consumer<DownloadSnapshot> listener) {
        snapshotListener = listener == null ? snapshot -> {} : listener;
        publishSnapshot(true);
    }

    public void setTotalBytesPlanned(long total) {
        totalBytesPlanned.set(Math.max(0L, total));
        totalBytesDone.set(0L);
        totalBytesWritten.set(0L);
        totalDiskBytesPlanned.set(Math.max(0L, total));
        resetSpeedMeter();
        publishSnapshot(true);
    }

    public long getTotalBytesPlanned() {
        return totalBytesPlanned.get();
    }

    public long getTotalBytesDone() {
        return totalBytesDone.get();
    }

    public long computeTotalBytesToDownload(List<FileEntry> files) throws Exception {
        return analyzeDownloadPlan(files).downloadBytes();
    }

    /**
     * Builds metrics for one manifest/update scope. Disk progress includes only
     * the files from this list: valid files already present plus bytes written
     * by the current download.
     */
    public long prepareDownloadPlan(List<FileEntry> files) throws Exception {
        DownloadPlan plan = analyzeDownloadPlan(files);
        totalBytesPlanned.set(plan.downloadBytes());
        totalBytesDone.set(0L);
        totalBytesWritten.set(plan.readyDiskBytes());
        totalDiskBytesPlanned.set(plan.plannedDiskBytes());
        resetSpeedMeter();
        publishSnapshot(true);
        return plan.downloadBytes();
    }

    private DownloadPlan analyzeDownloadPlan(List<FileEntry> files) throws Exception {
        Objects.requireNonNull(files, "files");

        long downloadBytes = 0L;
        long readyDiskBytes = 0L;
        long plannedDiskBytes = 0L;
        for (FileEntry file : files) {
            validateEntry(file);
            boolean valid = isFileValid(file);

            long size = file.size;
            if (size <= 0L) {
                size = valid ? Files.size(file.path) : Utils.tryHeadSize(file.url);
            }

            if (size > 0L) {
                plannedDiskBytes = Math.addExact(plannedDiskBytes, size);
                if (valid) {
                    readyDiskBytes = Math.addExact(readyDiskBytes, size);
                } else {
                    downloadBytes = Math.addExact(downloadBytes, size);
                }
            }
        }
        return new DownloadPlan(downloadBytes, readyDiskBytes, plannedDiskBytes);
    }

    private record DownloadPlan(long downloadBytes, long readyDiskBytes, long plannedDiskBytes) {}

    public void ensureFilePresentAndValid(FileEntry file, boolean updateUI) throws Exception {
        validateEntry(file);
        if (isFileValid(file)) {
            return;
        }

        if (totalBytesPlanned.get() <= 0L && file.size > 0L) {
            setTotalBytesPlanned(file.size);
        }

        beginDownloadSession();
        try {
            boolean ok = downloadWithRoundsSingleFile(file, 3, 2_000L, updateUI);
            if (ok) {
                return;
            }

            if (ArchiTechLauncher.UI != null) {
                ArchiTechLauncher.UI.updateProgress("Ожидание...", 1.0);
            }

            LogManager.getLogger().warning("Файл не удалось корректно скачать: " + file.name);
            throw new IOException("Файл не удалось корректно скачать: " + file.name);
        } finally {
            endDownloadSession();
        }
    }

    public List<FileEntry> downloadFilesInParallel(
            List<FileEntry> files,
            int threads,
            int rounds,
            boolean updateUI)
            throws InterruptedException {
        Objects.requireNonNull(files, "files");

        List<FileEntry> remaining = new ArrayList<>();
        for (FileEntry file : files) {
            try {
                validateEntry(file);
                if (!isFileValid(file)) {
                    remaining.add(file);
                }
            } catch (Exception error) {
                remaining.add(file);
            }
        }

        if (remaining.isEmpty()) {
            publishSnapshot(true);
            return List.of();
        }

        beginDownloadSession();
        try {
            int workerCount = Math.max(1, threads);
            int maxRounds = Math.max(1, rounds);

            for (int round = 1; round <= maxRounds && !remaining.isEmpty(); round++) {
                ConcurrentLinkedQueue<FileEntry> failedThisRound = new ConcurrentLinkedQueue<>();
                ExecutorService pool =
                        Executors.newFixedThreadPool(
                                workerCount,
                                runnable -> {
                                    Thread thread =
                                            new Thread(
                                                    runnable,
                                                    "download-worker-"
                                                            + DOWNLOAD_THREAD_SEQUENCE.getAndIncrement());
                                    thread.setDaemon(true);
                                    return thread;
                                });

                activePools.put(pool, Boolean.TRUE);
                try {
                    for (FileEntry file : remaining) {
                        pool.submit(
                                () -> {
                                    try {
                                        Path parent = file.path.getParent();
                                        if (parent != null) {
                                            Files.createDirectories(parent);
                                        }

                                        if (!downloadWithRoundsSingleFile(file, 1, 0L, updateUI)) {
                                            failedThisRound.add(file);
                                        }
                                    } catch (Throwable error) {
                                        failedThisRound.add(file);
                                    }
                                });
                    }

                    pool.shutdown();
                    boolean finished = pool.awaitTermination(10, TimeUnit.MINUTES);
                    if (!finished) {
                        pool.shutdownNow();
                    }
                } catch (InterruptedException interrupted) {
                    pool.shutdownNow();
                    throw interrupted;
                } finally {
                    activePools.remove(pool);
                }

                List<FileEntry> next = new ArrayList<>(failedThisRound);
                if (!next.isEmpty() && round < maxRounds) {
                    try {
                        Thread.sleep(1_000L * round);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw interrupted;
                    }
                }
                remaining = next;
            }

            return List.copyOf(remaining);
        } finally {
            endDownloadSession();
        }
    }

    private boolean downloadWithRoundsSingleFile(
            FileEntry file,
            int maxAttempts,
            long waitBetweenAttemptsMs,
            boolean updateUI) {
        Path key = file.path.toAbsolutePath().normalize();
        Object lock = FILE_LOCKS.computeIfAbsent(key, ignored -> new Object());

        synchronized (lock) {
            try {
                if (isFileValid(file)) {
                    return true;
                }

                for (int attempt = 1; attempt <= Math.max(1, maxAttempts); attempt++) {
                    if (Thread.currentThread().isInterrupted()) {
                        return false;
                    }

                    try {
                        downloadAtomicOnce(file, updateUI);
                        if (isFileValid(file)) {
                            return true;
                        }
                        Files.deleteIfExists(file.path);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return false;
                    } catch (Exception error) {
                        LogManager.getLogger()
                                .warning(
                                        "Попытка скачивания "
                                                + attempt
                                                + " провалилась для файла "
                                                + file.name
                                                + ": "
                                                + error.getMessage());
                        deleteTemporaryFile(file.path);
                    }

                    if (attempt < maxAttempts && waitBetweenAttemptsMs > 0L) {
                        try {
                            Thread.sleep(waitBetweenAttemptsMs);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            return false;
                        }
                    }
                }
                return false;
            } catch (Exception error) {
                throw new RuntimeException(error);
            } finally {
                FILE_LOCKS.remove(key, lock);
            }
        }
    }

    private void downloadAtomicOnce(FileEntry file, boolean updateUI) throws Exception {
        URI uri = new URI(file.url);
        URLConnection rawConnection = uri.toURL().openConnection();
        if (!(rawConnection instanceof HttpURLConnection connection)) {
            throw new IOException("Unsupported protocol for URL: " + file.url);
        }

        if (connection instanceof HttpsURLConnection httpsConnection) {
            httpsConnection.setSSLSocketFactory(
                    (SSLSocketFactory) SSLSocketFactory.getDefault());
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
        long addedToGlobal = 0L;
        long downloadedFile = 0L;

        try {
            connection.connect();
            int status = connection.getResponseCode();
            if (status >= 400) {
                throw new IOException("HTTP error " + status + " for " + file.url);
            }

            currentFile.set(file.name);
            publishSnapshot(true);

            InputStream input = new BufferedInputStream(connection.getInputStream());
            activeDownloads.put(downloadKey, input);

            try (input;
                    OutputStream output =
                            new BufferedOutputStream(
                                    Files.newOutputStream(temporary, CREATE, TRUNCATE_EXISTING))) {
                byte[] buffer = new byte[16 * 1024];

                while (true) {
                    awaitIfPaused();
                    ensureDownloadStillActive(downloadKey, file.name);

                    int read;
                    try {
                        read = input.read(buffer);
                    } catch (SSLHandshakeException error) {
                        LogManager.getLogger()
                                .warning(
                                        "Сервер закрыл TLS-соединение после передачи "
                                                + file.name
                                                + ": "
                                                + error.getMessage());
                        break;
                    }

                    if (read == -1) {
                        break;
                    }

                    // Pause may have been pressed while the worker was blocked on network I/O.
                    awaitIfPaused();
                    ensureDownloadStillActive(downloadKey, file.name);

                    output.write(buffer, 0, read);
                    downloadedFile += read;
                    addedToGlobal += read;
                    recordDownloadedBytes(read);
                    updateLegacyProgress(file, downloadedFile, updateUI);
                }
                output.flush();
            } finally {
                activeDownloads.remove(downloadKey);
            }

            validateDownloadedFile(file, temporary, downloadedFile);
            moveAtomically(temporary, file.path);
            Files.setLastModifiedTime(file.path, FileTime.from(Instant.now()));
        } catch (Exception error) {
            rollbackDownloadedBytes(addedToGlobal);
            Files.deleteIfExists(temporary);
            throw error;
        } finally {
            activeDownloads.remove(downloadKey);
            connection.disconnect();
            publishSnapshot(true);
        }
    }

    private void ensureDownloadStillActive(Path key, String fileName) throws IOException {
        if (!activeDownloads.containsKey(key) || Thread.currentThread().isInterrupted()) {
            throw new IOException("Загрузка прервана: " + fileName);
        }
    }

    private void validateDownloadedFile(FileEntry file, Path temporary, long downloadedBytes)
            throws Exception {
        if (file.size > 0L && downloadedBytes != file.size) {
            throw new IOException(
                    "Размер не совпал для файла "
                            + file.name
                            + ": скачано "
                            + downloadedBytes
                            + ", ожидалось "
                            + file.size);
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

    private void updateLegacyProgress(FileEntry file, long downloadedFile, boolean updateUI) {
        if (!updateUI || ArchiTechLauncher.UI == null) {
            return;
        }

        long planned = totalBytesPlanned.get();
        long done = totalBytesDone.get();
        double progress = planned > 0L ? Math.clamp((double) done / planned, 0.0, 1.0) : -1.0;

        String text =
                file.name
                        + " ("
                        + (downloadedFile / 1024)
                        + (file.size > 0L ? " / " + (file.size / 1024) : "")
                        + " КБ)";
        ArchiTechLauncher.UI.updateProgress(text, progress);
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
            return file.size <= 0L || Files.size(file.path) == file.size;
        } catch (Exception error) {
            LogManager.getLogger()
                    .warning(
                            "Найден невалидный файл: "
                                    + (file == null ? "<null>" : file.path)
                                    + ": "
                                    + error.getMessage());
            return false;
        }
    }

    public boolean hasActiveDownloadSession() {
        return activeDownloadSessions.get() > 0;
    }

    public boolean isPaused() {
        return paused.get();
    }

    public boolean togglePause() {
        if (!hasActiveDownloadSession()) {
            return false;
        }
        if (paused.compareAndSet(false, true)) {
            resetSpeedMeter();
            publishSnapshot(true);
            return true;
        }
        resumeDownloads();
        return false;
    }

    public void pauseDownloads() {
        if (hasActiveDownloadSession() && paused.compareAndSet(false, true)) {
            resetSpeedMeter();
            publishSnapshot(true);
        }
    }

    public void resumeDownloads() {
        synchronized (pauseMonitor) {
            paused.set(false);
            resetSpeedMeter();
            pauseMonitor.notifyAll();
        }
        publishSnapshot(true);
    }

    private void awaitIfPaused() throws InterruptedException {
        synchronized (pauseMonitor) {
            while (paused.get()) {
                pauseMonitor.wait();
            }
        }
    }

    private void beginDownloadSession() {
        int sessions = activeDownloadSessions.incrementAndGet();
        if (sessions == 1) {
            paused.set(false);
            resetSpeedMeter();
        }
        publishSnapshot(true);
    }

    private void endDownloadSession() {
        int sessions =
                activeDownloadSessions.updateAndGet(current -> Math.max(0, current - 1));
        if (sessions == 0) {
            synchronized (pauseMonitor) {
                paused.set(false);
                pauseMonitor.notifyAll();
            }
            currentFile.set("");
            resetSpeedMeter();
        }
        publishSnapshot(true);
    }

    private void recordDownloadedBytes(int count) {
        totalBytesDone.addAndGet(count);
        totalBytesWritten.addAndGet(count);

        synchronized (metricsMonitor) {
            long now = System.nanoTime();
            long elapsed = now - lastSpeedSampleNanos;
            if (elapsed >= TimeUnit.MILLISECONDS.toNanos(250)) {
                long currentBytes = totalBytesDone.get();
                long delta = Math.max(0L, currentBytes - lastSpeedSampleBytes);
                double instantRate = delta * 1_000_000_000.0 / elapsed;
                smoothedBytesPerSecond =
                        smoothedBytesPerSecond <= 0.0
                                ? instantRate
                                : smoothedBytesPerSecond * (1.0 - SPEED_SMOOTHING)
                                        + instantRate * SPEED_SMOOTHING;
                lastSpeedSampleBytes = currentBytes;
                lastSpeedSampleNanos = now;
            }
        }
        publishSnapshot(false);
    }

    private void rollbackDownloadedBytes(long count) {
        if (count <= 0L) {
            return;
        }
        totalBytesDone.updateAndGet(current -> Math.max(0L, current - count));
        totalBytesWritten.updateAndGet(current -> Math.max(0L, current - count));
        resetSpeedMeter();
        publishSnapshot(true);
    }

    private void resetSpeedMeter() {
        synchronized (metricsMonitor) {
            lastSpeedSampleNanos = System.nanoTime();
            lastSpeedSampleBytes = totalBytesDone.get();
            smoothedBytesPerSecond = 0.0;
        }
    }

    private void publishSnapshot(boolean force) {
        long now = System.nanoTime();
        synchronized (metricsMonitor) {
            if (!force && now - lastSnapshotNanos < SNAPSHOT_INTERVAL_NANOS) {
                return;
            }
            lastSnapshotNanos = now;
        }

        DownloadSnapshot snapshot =
                new DownloadSnapshot(
                        hasActiveDownloadSession(),
                        paused.get(),
                        totalBytesDone.get(),
                        totalBytesPlanned.get(),
                        totalBytesWritten.get(),
                        totalDiskBytesPlanned.get(),
                        paused.get() ? 0.0 : currentSpeed(),
                        currentFile.get());

        try {
            snapshotListener.accept(snapshot);
        } catch (RuntimeException error) {
            LogManager.getLogger()
                    .warning("Не удалось обновить панель загрузки: " + error.getMessage());
        }
    }

    private double currentSpeed() {
        synchronized (metricsMonitor) {
            return smoothedBytesPerSecond;
        }
    }

    public void resetTotals() {
        totalBytesPlanned.set(0L);
        totalBytesDone.set(0L);
        totalBytesWritten.set(0L);
        totalDiskBytesPlanned.set(0L);
        currentFile.set("");
        resetSpeedMeter();
        publishSnapshot(true);
    }

    public void cancelAllDownloads() {
        // Wake paused workers before closing streams and interrupting their pools.
        synchronized (pauseMonitor) {
            paused.set(false);
            pauseMonitor.notifyAll();
        }

        for (Closeable closeable : activeDownloads.values()) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
        }
        for (ExecutorService pool : activePools.keySet()) {
            try {
                pool.shutdownNow();
            } catch (Exception ignored) {
            }
        }

        activeDownloads.clear();
        activePools.clear();
        activeDownloadSessions.set(0);
        resetTotals();
    }

    private static void validateEntry(FileEntry file) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(file.path, "file.path");
        if (file.url == null || file.url.isBlank()) {
            throw new IllegalArgumentException("file.url must not be blank");
        }
        if (file.name == null || file.name.isBlank()) {
            file.name =
                    file.path.getFileName() == null
                            ? file.path.toString()
                            : file.path.getFileName().toString();
        }
    }

    private static void deleteTemporaryFile(Path target) {
        try {
            Files.deleteIfExists(temporaryPath(target));
        } catch (IOException error) {
            LogManager.getLogger()
                    .warning(
                            "Не удалось удалить временный файл "
                                    + temporaryPath(target)
                                    + ": "
                                    + error.getMessage());
        }
    }

    private static Path temporaryPath(Path target) {
        return target.resolveSibling(target.getFileName() + ".part");
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, REPLACE_EXISTING, ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, REPLACE_EXISTING);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
