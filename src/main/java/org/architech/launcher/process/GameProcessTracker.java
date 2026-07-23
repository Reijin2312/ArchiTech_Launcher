// SPDX-FileCopyrightText: 2026 Raijin2312
// SPDX-License-Identifier: GPL-3.0-only

package org.architech.launcher.process;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.architech.launcher.utils.Jsons;
import org.architech.launcher.utils.logging.LogManager;

/**
 * Persists the identity of the running Minecraft process and restores it after
 * the launcher is restarted.
 *
 * <p>The process start instant is stored in addition to the PID because an
 * operating system may reuse a PID after the original process exits.</p>
 */
public final class GameProcessTracker {
    private static final Duration LEGACY_START_TOLERANCE = Duration.ofSeconds(30);

    private final Path lockFile;
    private final Path legacyLockFile;
    private final AtomicReference<TrackedProcess> tracked = new AtomicReference<>();

    public GameProcessTracker(Path lockFile) {
        this(lockFile, null);
    }

    public GameProcessTracker(Path lockFile, Path legacyLockFile) {
        this.lockFile = Objects.requireNonNull(lockFile, "lockFile").toAbsolutePath().normalize();
        this.legacyLockFile =
                legacyLockFile == null ? null : legacyLockFile.toAbsolutePath().normalize();
    }

    public synchronized Optional<ProcessHandle> restore() {
        return restore(null);
    }

    public synchronized Optional<ProcessHandle> restore(Runnable onExit) {
        TrackedProcess current = tracked.get();
        if (current != null && matches(current.record(), current.handle())) {
            return Optional.of(current.handle());
        }

        tracked.set(null);
        Path source = existingLockPath();
        if (source == null) {
            return Optional.empty();
        }

        try {
            GameProcessRecord record = readRecord(source);
            Optional<ProcessHandle> process = ProcessHandle.of(record.pid());
            if (process.isEmpty() || !matches(record, process.get())) {
                deleteLockFiles();
                return Optional.empty();
            }

            TrackedProcess restored = new TrackedProcess(process.get(), record);
            tracked.set(restored);
            registerExitCleanup(restored, onExit);

            if (!source.equals(lockFile)) {
                writeRecord(record);
                Files.deleteIfExists(source);
            }

            return process;
        } catch (Exception e) {
            LogManager.getLogger()
                    .warning("Не удалось восстановить процесс игры: " + e.getMessage());
            deleteLockFilesQuietly();
            return Optional.empty();
        }
    }

    public synchronized ProcessHandle track(
            Process process, Path gameDirectory, Runnable onExit) throws IOException {
        Objects.requireNonNull(process, "process");

        ProcessHandle handle = process.toHandle();
        ProcessHandle.Info info = handle.info();
        GameProcessRecord record =
                new GameProcessRecord(
                        handle.pid(),
                        info.startInstant().orElse(null),
                        info.command().orElse(null),
                        gameDirectory == null
                                ? null
                                : gameDirectory.toAbsolutePath().normalize().toString());

        writeRecord(record);

        TrackedProcess trackedProcess = new TrackedProcess(handle, record);
        tracked.set(trackedProcess);
        registerExitCleanup(trackedProcess, onExit);
        return handle;
    }

    public boolean isRunning() {
        TrackedProcess current = tracked.get();
        if (current != null && matches(current.record(), current.handle())) {
            return true;
        }
        return restore().isPresent();
    }

    public Optional<ProcessHandle> currentHandle() {
        return isRunning()
                ? Optional.ofNullable(tracked.get()).map(TrackedProcess::handle)
                : Optional.empty();
    }

    /**
     * Explicitly terminates the tracked game. This method must not be called
     * from the normal "cancel preparation" action.
     */
    public synchronized boolean terminate(Duration gracefulTimeout) {
        Optional<ProcessHandle> optional = currentHandle();
        if (optional.isEmpty()) {
            return false;
        }

        ProcessHandle handle = optional.get();
        try {
            handle.destroy();
            long timeoutMillis =
                    Math.max(1L, Objects.requireNonNull(gracefulTimeout).toMillis());
            handle.onExit().get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (Exception firstFailure) {
            try {
                if (handle.isAlive()) {
                    handle.destroyForcibly();
                }
            } catch (Exception secondFailure) {
                LogManager.getLogger()
                        .warning(
                                "Не удалось завершить процесс игры "
                                        + handle.pid()
                                        + ": "
                                        + secondFailure.getMessage());
                return false;
            }
        }

        if (!handle.isAlive()) {
            tracked.set(null);
            deleteLockFilesQuietly();
            return true;
        }
        return false;
    }

    public synchronized void clearStaleLock() {
        if (!isRunning()) {
            tracked.set(null);
            deleteLockFilesQuietly();
        }
    }

    private void registerExitCleanup(TrackedProcess expected, Runnable onExit) {
        expected.handle()
                .onExit()
                .whenComplete(
                        (ignored, error) -> {
                            synchronized (GameProcessTracker.this) {
                                TrackedProcess current = tracked.get();
                                if (current != null
                                        && sameIdentity(current.record(), expected.record())) {
                                    tracked.set(null);
                                    deleteLockIfOwnedBy(expected.record());
                                }
                            }

                            if (onExit != null) {
                                try {
                                    onExit.run();
                                } catch (RuntimeException callbackError) {
                                    LogManager.getLogger()
                                            .warning(
                                                    "Ошибка обработчика завершения игры: "
                                                            + callbackError.getMessage());
                                }
                            }
                        });
    }

    private boolean matches(GameProcessRecord record, ProcessHandle handle) {
        if (record == null || handle == null || !handle.isAlive()) {
            return false;
        }

        ProcessHandle.Info info = handle.info();
        Optional<Instant> actualStart = info.startInstant();

        if (record.startedAt() != null) {
            if (actualStart.isEmpty()) {
                return false;
            }

            Duration difference =
                    Duration.between(record.startedAt(), actualStart.get()).abs();
            if (difference.compareTo(LEGACY_START_TOLERANCE) > 0) {
                return false;
            }
        }

        if (record.command() != null
                && !record.command().isBlank()
                && info.command().isPresent()
                && !sameCommand(record.command(), info.command().get())) {
            return false;
        }

        return true;
    }

    private static boolean sameCommand(String expected, String actual) {
        try {
            Path expectedPath = Path.of(expected).toAbsolutePath().normalize();
            Path actualPath = Path.of(actual).toAbsolutePath().normalize();
            if (isWindows()) {
                return expectedPath.toString().equalsIgnoreCase(actualPath.toString());
            }
            return expectedPath.equals(actualPath);
        } catch (Exception ignored) {
            return expected.equals(actual);
        }
    }

    private static boolean sameIdentity(
            GameProcessRecord first, GameProcessRecord second) {
        if (first.pid() != second.pid()) {
            return false;
        }
        if (first.startedAt() == null || second.startedAt() == null) {
            return true;
        }
        return Duration.between(first.startedAt(), second.startedAt())
                        .abs()
                        .compareTo(LEGACY_START_TOLERANCE)
                <= 0;
    }

    private Path existingLockPath() {
        if (Files.isRegularFile(lockFile)) {
            return lockFile;
        }
        if (legacyLockFile != null && Files.isRegularFile(legacyLockFile)) {
            return legacyLockFile;
        }
        return null;
    }

    private GameProcessRecord readRecord(Path source) throws IOException {
        JsonNode json = Jsons.MAPPER.readTree(Files.readString(source, StandardCharsets.UTF_8));
        if (json == null || !json.path("pid").canConvertToLong()) {
            throw new IOException("Некорректный game lock: отсутствует PID");
        }

        long pid = json.path("pid").asLong();
        Instant startedAt = parseInstant(json.path("startedAt").asText(null));
        if (startedAt == null) {
            startedAt = parseInstant(json.path("ts").asText(null));
        }

        return new GameProcessRecord(
                pid,
                startedAt,
                blankToNull(json.path("command").asText(null)),
                blankToNull(json.path("gameDirectory").asText(null)));
    }

    private void writeRecord(GameProcessRecord record) throws IOException {
        Path parent = lockFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path temporary =
                lockFile.resolveSibling(lockFile.getFileName().toString() + ".tmp");
        Files.writeString(
                temporary,
                Jsons.PRETTY.writeValueAsString(record),
                StandardCharsets.UTF_8);

        try {
            Files.move(
                    temporary,
                    lockFile,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, lockFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void deleteLockIfOwnedBy(GameProcessRecord expected) {
        try {
            if (!Files.exists(lockFile)) {
                return;
            }
            GameProcessRecord stored = readRecord(lockFile);
            if (sameIdentity(stored, expected)) {
                Files.deleteIfExists(lockFile);
            }
        } catch (Exception e) {
            LogManager.getLogger()
                    .warning("Не удалось удалить game lock: " + e.getMessage());
        }
    }

    private void deleteLockFiles() throws IOException {
        Files.deleteIfExists(lockFile);
        Files.deleteIfExists(
                lockFile.resolveSibling(lockFile.getFileName().toString() + ".tmp"));
        if (legacyLockFile != null) {
            Files.deleteIfExists(legacyLockFile);
        }
    }

    private void deleteLockFilesQuietly() {
        try {
            deleteLockFiles();
        } catch (IOException e) {
            LogManager.getLogger()
                    .warning("Не удалось очистить game lock: " + e.getMessage());
        }
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "")
                .toLowerCase(java.util.Locale.ROOT)
                .contains("win");
    }

    private record TrackedProcess(
            ProcessHandle handle, GameProcessRecord record) {}
}
