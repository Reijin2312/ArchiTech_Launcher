package org.architech.launcher.utils;

import com.sun.management.OperatingSystemMXBean;
import javafx.scene.control.Alert;
import org.architech.launcher.ArchiTechLauncher;
import org.architech.launcher.gui.error.ErrorPanel;
import org.architech.launcher.utils.logging.LogManager;
import javax.net.ssl.SSLContext;
import java.awt.*;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static org.architech.launcher.ArchiTechLauncher.GAME_DIR;

public class Utils {

    public static double clamp01(double v)
    {

        return v < 0 ? 0 : Math.min(v, 1);
    }

    public static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }

    public static long tryHeadSize(String url) {
        try {
            URI uri = URI.create(url);
            HttpURLConnection c = (HttpURLConnection) uri.toURL().openConnection();
            c.setRequestMethod("HEAD");
            c.connect();
            return c.getContentLengthLong();
        } catch (Exception ignored) {}
        return 0;
    }

    public static String sha1Hex(Path path) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        try (InputStream fis = Files.newInputStream(path);
             DigestInputStream dis = new DigestInputStream(fis, md)) {
             byte[] buf = new byte[8192];
             while (dis.read(buf) != -1) {}
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    public static void deleteDirectory(Path path) throws IOException {
        if (Files.notExists(path)) {
            return;
        }
        Files.walk(path).sorted(Comparator.reverseOrder()).forEach(p -> {
            try {
                Files.delete(p);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    public static String readUrl(String urlStr) throws Exception {
        SSLContext ssl = SSLContext.getDefault();
        HttpResponse<String> resp;
        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(ArchiTechLauncher.HTTP_TIMEOUT))
                .version(HttpClient.Version.HTTP_1_1)
                .sslContext(ssl)
                .build()) {

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(urlStr))
                    .timeout(Duration.ofSeconds(ArchiTechLauncher.HTTP_TIMEOUT))
                    .header("User-Agent", "ArchiTech-Launcher/1.0")
                    .GET()
                    .build();

            resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }
        if (resp.statusCode() >= 400) throw new IOException("HTTP error " + resp.statusCode() + " for " + urlStr);
        return resp.body();
    }

    public static String sha1(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            byte[] hash = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    public static void openInBrowser(String url) {
        try {
            URI uri = new URI(url);

            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uri);
            } else {
                String os = System.getProperty("os.name").toLowerCase();
                Runtime rt = Runtime.getRuntime();

                if (os.contains("win")) {
                    rt.exec(new String[]{"rundll32", "url.dll,FileProtocolHandler", uri.toString()});
                } else if (os.contains("mac")) {
                    rt.exec(new String[]{"open", uri.toString()});
                } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
                    rt.exec(new String[]{"xdg-open", uri.toString()});
                } else {
                    throw new UnsupportedOperationException("Неизвестная ОС: " + os);
                }
            }
        } catch (Exception e) {
            LogManager.getLogger().severe("Не удалось открыть браузер: " + e.getMessage());
            ErrorPanel.showError("Не удалось открыть браузер", e.getMessage());
        }
    }

    public static void openWebpage(String url) {
        try {
            URI uri = new URI(url);

            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uri);
            } else {
                String os = System.getProperty("os.name").toLowerCase();
                Runtime rt = Runtime.getRuntime();

                if (os.contains("win")) {
                    rt.exec(new String[]{"rundll32", "url.dll,FileProtocolHandler", uri.toString()});
                } else if (os.contains("mac")) {
                    rt.exec(new String[]{"open", uri.toString()});
                } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
                    rt.exec(new String[]{"xdg-open", uri.toString()});
                } else {
                    throw new UnsupportedOperationException("Неизвестная ОС: " + os);
                }
            }
        } catch (Exception e) {
            LogManager.getLogger().severe("Ошибка открытия веб-страницы: " + e.getMessage());
        }
    }

    public static void openGameDir() {
        try {
            File dir = GAME_DIR.toFile();
            String os = System.getProperty("os.name").toLowerCase();
            Runtime rt = Runtime.getRuntime();

            if (os.contains("win")) {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    Desktop.getDesktop().open(dir);
                } else {
                    rt.exec(new String[]{"explorer", dir.getAbsolutePath()});
                }
            } else if (os.contains("mac")) {
                rt.exec(new String[]{"open", dir.getAbsolutePath()});
            } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
                rt.exec(new String[]{"/usr/bin/xdg-open", dir.getAbsolutePath()});
            } else {
                throw new UnsupportedOperationException("Неизвестная ОС: " + os);
            }

        } catch (Exception ex) {
            LogManager.getLogger().severe("Не удалось открыть папку игры: " + ex.getMessage());
            ErrorPanel.showError("Упс! Не удалось открыть папку игры :(", ex.getMessage());
        }
    }

    public void showInfo(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg);
        a.showAndWait();
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
                                    if (ver.startsWith("21")) return Path.of(sd.getAbsolutePath());
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

    public static List<String> detectGPUs() {
        List<String> result = new ArrayList<>();
        String os = System.getProperty("os.name").toLowerCase();

        try {
            Process p;
            if (os.contains("win")) {
                p = new ProcessBuilder("wmic", "path", "win32_VideoController", "get", "Name").start();
            } else if (os.contains("mac")) {
                p = new ProcessBuilder("system_profiler", "SPDisplaysDataType").start();
            } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
                p = new ProcessBuilder("lspci").start();
            } else {
                throw new UnsupportedOperationException("Неизвестная ОС: " + os);
            }

            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    if (os.contains("win")) {
                        if (!line.toLowerCase(Locale.ROOT).contains("name")) result.add(line);
                    } else if (os.contains("mac")) {
                        if (line.startsWith("Chipset Model:") || line.startsWith("Graphics:")) {
                            result.add(line.split(":")[1].trim());
                        }
                    } else {
                        if (line.toLowerCase().contains("vga") || line.toLowerCase().contains("3d")) {
                            String[] parts = line.split(":", 2);
                            if (parts.length == 2) result.add(parts[1].trim());
                        }
                    }
                }
            }
        } catch (Exception ex) {
            LogManager.getLogger().severe("Ошибка обнаружения GPU: " + ex.getMessage());
        }

        return result;
    }

    public static int getRecommendedMemory() {
        int recommended = 2048;
        try {
            OperatingSystemMXBean os = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            long totalMb = os.getTotalMemorySize() / (1024L * 1024L);
            if (totalMb > 0) {
                recommended = (int) Math.max(1024, Math.min(totalMb / 4, 32768));
            }
        } catch (Throwable ignored) {
        }
        return recommended;
    }

    public static int roundRam(int raw) {
        return (int) Math.round(raw/1024.0) * 1024;
    }

    public static String sha1Hex(String data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }


}

