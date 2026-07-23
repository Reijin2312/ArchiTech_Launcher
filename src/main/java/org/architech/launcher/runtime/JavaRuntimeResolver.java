// SPDX-FileCopyrightText: 2026 Raijin2312
// SPDX-License-Identifier: GPL-3.0-only

package org.architech.launcher.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.architech.launcher.utils.logging.LogManager;

public final class JavaRuntimeResolver {
    private static final Duration INSPECTION_TIMEOUT = Duration.ofSeconds(8);
    private static final Pattern QUOTED_VERSION =
            Pattern.compile("version\\s+\"([^\"]+)\"");

    private JavaRuntimeResolver() {}

    public static Path bundledJavaHome() {
        return Path.of(System.getProperty("java.home"))
                .toAbsolutePath()
                .normalize();
    }

    public static Path resolve(
            JavaRuntimeMode mode,
            Path customJavaHome)
            throws IOException {
        JavaRuntimeMode resolvedMode =
                mode == null
                        ? JavaRuntimeMode.BUNDLED
                        : mode;

        if (resolvedMode == JavaRuntimeMode.BUNDLED) {
            JavaRuntimeInfo bundled = inspectBundledRuntime();
            requireJava21(bundled);
            return bundled.javaHome();
        }

        Path candidate =
                Objects.requireNonNull(
                        customJavaHome,
                        "Для пользовательской Java необходимо выбрать папку");

        JavaRuntimeInfo custom = inspect(candidate);
        requireJava21(custom);
        return custom.javaHome();
    }

    /**
     * Старый или повреждённый путь к пользовательской Java не должен делать
     * лаунчер полностью неработоспособным. В таком случае используется
     * встроенная Java лаунчера.
     */
    public static Path resolveOrBundled(
            JavaRuntimeMode mode,
            Path customJavaHome)
            throws IOException {
        try {
            return resolve(mode, customJavaHome);
        } catch (Exception customFailure) {
            if (mode != JavaRuntimeMode.CUSTOM) {
                if (customFailure instanceof IOException io) {
                    throw io;
                }
                throw new IOException(
                        customFailure.getMessage(),
                        customFailure);
            }

            LogManager.getLogger()
                    .warning(
                            "Пользовательская Java недоступна, "
                                    + "используется встроенная: "
                                    + customFailure.getMessage());

            JavaRuntimeInfo bundled = inspectBundledRuntime();
            requireJava21(bundled);
            return bundled.javaHome();
        }
    }

    /**
     * Проверяет внешнюю Java. Для встроенной Java используй
     * {@link #inspectBundledRuntime()}, чтобы не запускать лишний дочерний
     * процесс при старте лаунчера.
     */
    public static JavaRuntimeInfo inspect(Path javaHome)
            throws IOException {
        if (javaHome == null) {
            throw new IOException("Путь к Java не задан");
        }

        Path normalized =
                javaHome.toAbsolutePath().normalize();

        if (normalized.equals(bundledJavaHome())) {
            return inspectBundledRuntime();
        }

        Path executable = javaExecutable(normalized);
        if (!Files.isRegularFile(executable)) {
            throw new IOException(
                    "Не найден исполняемый файл Java: "
                            + executable);
        }

        Path outputFile =
                Files.createTempFile(
                        "architech-java-version-",
                        ".log");

        Process process = null;
        try {
            process =
                    new ProcessBuilder(
                                    executable.toString(),
                                    "-version")
                            .redirectErrorStream(true)
                            .redirectOutput(outputFile.toFile())
                            .start();

            boolean finished;
            try {
                finished =
                        process.waitFor(
                                INSPECTION_TIMEOUT.toMillis(),
                                TimeUnit.MILLISECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
                throw new IOException(
                        "Проверка Java была прервана",
                        interrupted);
            }

            if (!finished) {
                process.destroyForcibly();
                waitQuietly(process);

                throw new IOException(
                        "Java не ответила за "
                                + INSPECTION_TIMEOUT.toSeconds()
                                + " секунд: "
                                + executable);
            }

            String output =
                    Files.readString(
                            outputFile,
                            StandardCharsets.UTF_8);

            if (process.exitValue() != 0) {
                throw new IOException(
                        "Команда java -version завершилась с кодом "
                                + process.exitValue()
                                + ": "
                                + output.strip());
            }

            String version = extractVersion(output);
            int major = parseMajorVersion(version);

            return new JavaRuntimeInfo(
                    normalized,
                    executable,
                    version,
                    major);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            Files.deleteIfExists(outputFile);
        }
    }

    public static JavaRuntimeInfo inspectBundledRuntime()
            throws IOException {
        Path javaHome = bundledJavaHome();
        Path executable = javaExecutable(javaHome);

        if (!Files.isRegularFile(executable)) {
            throw new IOException(
                    "Не найден исполняемый файл встроенной Java: "
                            + executable);
        }

        String version =
                System.getProperty(
                        "java.version",
                        Runtime.version().toString());

        return new JavaRuntimeInfo(
                javaHome,
                executable,
                version,
                Runtime.version().feature());
    }

    public static Path javaExecutable(Path javaHome) {
        String executableName =
                isWindows() ? "java.exe" : "java";

        return javaHome
                .resolve("bin")
                .resolve(executableName);
    }

    public static void requireJava21(JavaRuntimeInfo info)
            throws IOException {
        if (info.majorVersion() != 21) {
            throw new IOException(
                    "Требуется Java 21, найдена Java "
                            + info.version()
                            + " в "
                            + info.javaHome());
        }
    }

    private static String extractVersion(String output)
            throws IOException {
        Matcher quoted = QUOTED_VERSION.matcher(output);
        if (quoted.find()) {
            return quoted.group(1).trim();
        }

        throw new IOException(
                "Не удалось определить версию Java: "
                        + output.strip());
    }

    static int parseMajorVersion(String version)
            throws IOException {
        try {
            String normalized = version.trim();

            if (normalized.startsWith("1.")) {
                normalized = normalized.substring(2);
            }

            int end = 0;
            while (end < normalized.length()
                    && Character.isDigit(
                            normalized.charAt(end))) {
                end++;
            }

            if (end == 0) {
                throw new NumberFormatException(version);
            }

            return Integer.parseInt(
                    normalized.substring(0, end));
        } catch (RuntimeException error) {
            throw new IOException(
                    "Некорректная версия Java: "
                            + version,
                    error);
        }
    }

    private static void waitQuietly(Process process) {
        try {
            process.waitFor(1, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("win");
    }
}
