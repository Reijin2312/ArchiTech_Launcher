package org.architech.launcher.managment;

import org.architech.launcher.utils.FileEntry;
import org.architech.launcher.gui.LauncherUI;
import org.architech.launcher.utils.LogManager;
import org.architech.launcher.utils.Utils;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;

public class DownloadManager {
    private final LauncherUI ui;
    private final AtomicLong totalBytesPlanned = new AtomicLong(0);
    private final AtomicLong totalBytesDone = new AtomicLong(0);

    private static final ConcurrentHashMap<Path, Object> fileLocks = new ConcurrentHashMap<>();

    private final ConcurrentMap<Path, Closeable> activeDownloads = new ConcurrentHashMap<>();

    public DownloadManager(LauncherUI ui) { this.ui = ui; }

    public void setTotalBytesPlanned(long total) { this.totalBytesPlanned.set(total); }

    public long computeTotalBytesToDownload(List<FileEntry> files) throws Exception {
        long sum = 0;
        for (FileEntry f : files) {
            if (!Files.exists(f.path)) {
                if (f.size > 0) sum += f.size;
                else sum += Utils.tryHeadSize(f.url);
            } else {
                if (f.sha1 != null) {
                    String local = Utils.sha1Hex(f.path);
                    if (!f.sha1.equalsIgnoreCase(local)) {
                        sum += (f.size > 0 ? f.size : Utils.tryHeadSize(f.url));
                    }
                } else if (f.size > 0) {
                    long local = Files.size(f.path);
                    if (local != f.size) sum += f.size;
                }
            }
        }
        return sum;
    }

    public void ensureFilePresentAndValid(FileEntry f) throws Exception {
        boolean ok = downloadWithRoundsSingleFile(f, 3, 2000);
        if (!ok) {
            LogManager.getLogger().warning("Файл не удалось корректно скачать: " + f.name);
            throw new IOException("Файл не удалось корректно скачать: " + f.name);
        }
    }

    public List<FileEntry> downloadFilesInParallel(List<FileEntry> files, int threads, int rounds) throws InterruptedException {
        Objects.requireNonNull(files);
        List<FileEntry> remaining = new ArrayList<>();
        for (FileEntry f : files) {
            try {
                if (!isFileValid(f)) remaining.add(f);
            } catch (Exception e) {
                remaining.add(f);
            }
        }

        for (int round = 1; round <= Math.max(1, rounds) && !remaining.isEmpty(); round++) {
            ConcurrentLinkedQueue<FileEntry> failedThisRound;
            try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
                failedThisRound = new java.util.concurrent.ConcurrentLinkedQueue<>();

                for (FileEntry f : remaining) {
                    pool.submit(() -> {
                        try {
                            Path parent = f.path.getParent();
                            if (parent != null) Files.createDirectories(parent);
                            boolean ok = downloadWithRoundsSingleFile(f, 1, 0);
                            if (!ok) failedThisRound.add(f);
                        } catch (Throwable t) {
                            failedThisRound.add(f);
                        }
                    });
                }

                pool.shutdown();

                boolean finished = pool.awaitTermination(10, java.util.concurrent.TimeUnit.MINUTES);
                if (!finished) {
                    pool.shutdownNow();
                }
            }

            List<FileEntry> next = new ArrayList<>(failedThisRound);

            if (!next.isEmpty() && round < rounds) {
                try {
                    long backoff = 1000L * round;
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            remaining = next;
        }

        return remaining;
    }

    private boolean downloadWithRoundsSingleFile(FileEntry f, int maxAttempts, long waitBetweenAttemptsMs) {
        Path key = f.path.toAbsolutePath().normalize();
        Object lock = fileLocks.computeIfAbsent(key, k -> new Object());
        synchronized (lock) {
            try {
                if (isFileValid(f)) return true;

                for (int attempt = 1; attempt <= Math.max(1, maxAttempts); attempt++) {
                    try {
                        downloadAtomicOnce(f);
                        if (isFileValid(f)) return true;
                        try {
                            Files.deleteIfExists(f.path);
                        } catch (Exception ex) {
                            LogManager.getLogger().severe("Ошибка удаления файла " + f.path);
                        }
                    } catch (Exception e) {
                        LogManager.getLogger().severe("Попытка скачивания " + attempt + " провалилась для файла" + f.name + ": " + e.getMessage());
                        try {
                            Files.deleteIfExists(f.path.resolveSibling(f.path.getFileName().toString() + ".part"));
                        } catch (Exception ignored) {
                            LogManager.getLogger().severe("Ошибка удаления файла " + f.path.resolveSibling(f.path.getFileName().toString() + ".part"));
                        }
                        if (waitBetweenAttemptsMs > 0) {
                            try {
                                Thread.sleep(waitBetweenAttemptsMs);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }
                }
                return false;
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                fileLocks.remove(key, lock);
            }
        }
    }

    private void downloadAtomicOnce(FileEntry f) throws Exception {
        URI uri = new URI(f.url);
        HttpsURLConnection conn = (HttpsURLConnection) uri.toURL().openConnection();
        conn.setSSLSocketFactory((SSLSocketFactory) SSLSocketFactory.getDefault());
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(30_000);
        conn.setRequestProperty("User-Agent", "ArchiTech-Launcher/1.0");

        Path parent = f.path.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path tmp = f.path.resolveSibling(f.path.getFileName().toString() + ".part");

        long addedToGlobal = 0;
        long downloadedFile = 0;
        try {
            conn.connect();
            int code = conn.getResponseCode();
            if (code >= 400) {
                LogManager.getLogger().severe("HTTP error " + code + " for " + f.url);
                throw new IOException("HTTP error " + code + " for " + f.url);
            }

            InputStream in = new BufferedInputStream(conn.getInputStream());
            activeDownloads.put(f.path, in);

            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(tmp, CREATE, TRUNCATE_EXISTING))) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while (true) {
                    if (!activeDownloads.containsKey(f.path)) {
                        throw new IOException("Загрузка прервана: " + f.name);
                    }
                    try {
                        read = in.read(buffer);
                    } catch (SSLHandshakeException ex) {
                        LogManager.getLogger().severe("Сервер закрыл TLS соединение после завершения передачи: " + f.name + ": " + ex.getMessage());
                        break;
                    }
                    if (read == -1) break;

                    out.write(buffer, 0, read);
                    downloadedFile += read;
                    addedToGlobal += read;
                    totalBytesDone.addAndGet(read);

                    long planned = totalBytesPlanned.get();
                    long done = totalBytesDone.get();
                    double globalProgress = (planned > 0) ? (double) done / planned : -1;
                    int percent = globalProgress >= 0 ? (int) (globalProgress * 100) : -1;

                    String text = f.name + " (" + (downloadedFile / 1024) +
                            (f.size > 0 ? (" / " + (f.size / 1024)) : "") + " КБ)";
                    ui.updateProgress(text + (percent >= 0 ? " | Всего: " + percent + "%" : ""), globalProgress);
                }
                out.flush();
            }  finally {
                activeDownloads.remove(f.path);
            }

            if (f.size > 0) {
                LogManager.getLogger().info(f.name + " -> скачано: " + downloadedFile + " байт, ожидалось: " + f.size + " байт");
            } else {
                LogManager.getLogger().info(f.name + " -> скачано: " + downloadedFile + " байт (ожидаемый размер неизвестен)");
            }

            if (f.sha1 != null) {
                String hex = Utils.sha1Hex(tmp);
                if (!f.sha1.equalsIgnoreCase(hex)) {
                    if (addedToGlobal > 0) totalBytesDone.addAndGet(-addedToGlobal);
                    Files.deleteIfExists(tmp);
                    LogManager.getLogger().warning("Хэш не совпал для файла " + f.name);
                    throw new IOException("Хэш не совпал для файла " + f.name);
                }
            }

            try {
                Files.move(tmp, f.path, REPLACE_EXISTING, ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException amnse) {
                Files.move(tmp, f.path, REPLACE_EXISTING);
            }
            Files.setLastModifiedTime(f.path, FileTime.from(Instant.now()));

        } catch (Exception e) {
            if (addedToGlobal > 0) totalBytesDone.addAndGet(-addedToGlobal);
            throw e;
        } finally {
            conn.disconnect();
        }
    }

    private boolean isFileValid(FileEntry f) {
        try {
            if (!Files.exists(f.path)) return false;
            if (f.sha1 != null) {
                String actual = Utils.sha1Hex(f.path);
                return f.sha1.equalsIgnoreCase(actual);
            } else if (f.size > 0) {
                return Files.size(f.path) == f.size;
            } else {
                return true;
            }
        } catch (Exception ex) {
            LogManager.getLogger().warning("Найден невалидный файл: " + f.path + ": " + ex.getMessage());
            return false;
        }
    }

    public void cancelAllDownloads() {
        for (Closeable c : activeDownloads.values()) {
            try { c.close(); } catch (Exception ignored) {}
        }
        activeDownloads.clear();
    }
}
