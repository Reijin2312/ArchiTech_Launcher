package org.architech.launcher.gui.player;

import javafx.scene.image.Image;
import org.architech.launcher.ArchiTechLauncher;
import org.architech.launcher.authentication.account.Account;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AvatarImage {
    private static final Map<String, Image> CACHE = new ConcurrentHashMap<>();
    private static final Image FALLBACK = loadFallback();

    public static Image forAccount(Account a, int size) {
        if (a == null) return FALLBACK;

        String av = a.getAvatarUrl();
        if (av != null && !av.isBlank()) {
            String url = ArchiTechLauncher.FRONTEND_URL + av;
            return loadUrlCached(url, size);
        }
        return FALLBACK;
    }

    public static Image fromName(String username, int size) {
        if (username == null || username.isBlank()) return FALLBACK;

        try {
            String api = ArchiTechLauncher.FRONTEND_URL
                    + "/api/v1/users/by-name?username="
                    + URLEncoder.encode(username, StandardCharsets.UTF_8);

            HttpURLConnection c = (HttpURLConnection) new URL(api).openConnection();
            c.setConnectTimeout(3000);
            c.setReadTimeout(3000);
            c.setRequestMethod("GET");
            c.setRequestProperty("Accept", "application/json");

            if (c.getResponseCode() == 200) {
                String json = new String(c.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                String av = findField(json, "avatarUrl");
                if (av != null && !av.isBlank()) {
                    String url = av.startsWith("http") ? av
                            : (ArchiTechLauncher.FRONTEND_URL + av);
                    return loadUrlCached(url, size);
                }
            }
        } catch (Exception ignore) {}

        String enc = URLEncoder.encode(username, StandardCharsets.UTF_8);
        String url = "https://minotar.net/avatar/" + enc + "/" + size + ".png";
        return loadUrlCached(url, size);
    }

    private static String findField(String json, String name) {
        int i = json.indexOf("\"" + name + "\"");
        if (i < 0) return null;
        i = json.indexOf(':', i);
        if (i < 0) return null;
        int q1 = json.indexOf('"', i + 1);
        if (q1 < 0) return null;
        int q2 = json.indexOf('"', q1 + 1);
        if (q2 < 0) return null;
        return json.substring(q1 + 1, q2);
    }

    private static Image loadUrlCached(String url, int size) {
        String key = url + "#" + size;
        Image cached = CACHE.get(key);
        if (cached != null) return cached;
        try {
            Image img = new Image(url, size, size, true, true);
            if (img.isError()) return FALLBACK;
            CACHE.put(key, img);
            return img;
        } catch (Exception e) {
            return FALLBACK;
        }
    }

    private static Image loadFallback() {
        try (InputStream is = AvatarImage.class.getResourceAsStream("/images/icon.jpg")) {
            if (is != null) return new Image(is);
        } catch (Exception ignored) {}
        return new Image("https://crafatar.com/avatars/00000000000000000000000000000000?size=16&overlay");
    }
}
