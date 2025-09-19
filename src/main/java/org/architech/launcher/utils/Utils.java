package org.architech.launcher.utils;

import javafx.scene.control.Alert;
import org.architech.launcher.utils.logging.LogManager;

import javax.net.ssl.SSLContext;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Comparator;
import java.util.Locale;

import static org.architech.launcher.ArchiTechLauncher.GAME_DIR;
import static org.architech.launcher.gui.LauncherUI.showError;

public class Utils {
    public static double clamp01(double v) { return v < 0 ? 0 : Math.min(v, 1); }
    public static boolean isWindows() { return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win"); }

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
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_1_1)
                .sslContext(ssl)
                .build()) {

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(urlStr))
                    .timeout(Duration.ofSeconds(30))
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
            showError("Не удалось открыть браузер", e.getMessage());
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
                    // Windows
                    rt.exec(new String[]{"rundll32", "url.dll,FileProtocolHandler", uri.toString()});
                } else if (os.contains("mac")) {
                    // macOS
                    rt.exec(new String[]{"open", uri.toString()});
                } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
                    // Linux/Unix
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

            //// if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            //   Desktop.getDesktop().open(dir);
            // } else {
            String os = System.getProperty("os.name").toLowerCase();
            Runtime rt = Runtime.getRuntime();

            if (os.contains("win")) {
                rt.exec(new String[]{"explorer", dir.getAbsolutePath()});
            } else if (os.contains("mac")) {
                rt.exec(new String[]{"open", dir.getAbsolutePath()});
            } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
                rt.exec(new String[]{"xdg-open", dir.getAbsolutePath()});
            } else {
                throw new UnsupportedOperationException("Неизвестная ОС: " + os);
            }
            // }
        } catch (Exception ex) {
            LogManager.getLogger().severe("Не удалось открыть папку игры: " + ex.getMessage());
            showError("Упс! Не удалось открыть папку игры :(", ex.getMessage());
        }
    }

    public void showInfo(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg);
        a.showAndWait();
    }

}

