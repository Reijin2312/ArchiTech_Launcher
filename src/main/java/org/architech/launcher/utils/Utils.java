package org.architech.launcher.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.Locale;

public class Utils {
    public static double clamp01(double v) { return v < 0 ? 0 : Math.min(v, 1); }
    public static boolean isWindows() { return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win"); }

    public static long tryHeadSize(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
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
}

