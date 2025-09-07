package org.architech.launcher.utils;

import javax.net.ssl.SSLContext;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;

public class UtilsNet {
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
}
