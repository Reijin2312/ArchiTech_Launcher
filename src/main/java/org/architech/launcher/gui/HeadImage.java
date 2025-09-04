package org.architech.launcher.gui;

import javafx.scene.image.Image;
import org.architech.launcher.auth.Account;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class HeadImage {
    private static final Map<String, Image> CACHE = new ConcurrentHashMap<>();
    private static final Image FALLBACK = loadFallback();

    private HeadImage() {}

    public static Image forAccount(Account a, int size) {
        if (a == null) return FALLBACK;
        try {
            if (a.username != null) {
                return ElyHead.fromEly(a.username, size);
            }
            else if (a.avatarUrl != null && !a.avatarUrl.isBlank()) {
                return loadUrlCached(adjustSizeInUrl(a.avatarUrl, size), size);
            }
            else if (a.uuid != null && !a.uuid.isBlank()) {
                return fromUuid(a.uuid, size);
            }
            else if (a.username != null && !a.username.isBlank()) {
                return fromName(a.username, size);
            }
        } catch (Exception ignored) {}
        return FALLBACK;
    }

    public static Image fromUuid(String uuid, int size) {
        if (uuid == null) return FALLBACK;
        String raw = uuid.replace("-", "");
        String url = "https://crafatar.com/avatars/" + raw + "?size=" + size + "&overlay";
        return loadUrlCached(url, size);
    }

    public static Image fromName(String username, int size) {
        if (username == null) return FALLBACK;
        String enc = URLEncoder.encode(username, StandardCharsets.UTF_8);
        String url = "https://minotar.net/avatar/" + enc + "/" + size + ".png";
        return loadUrlCached(url, size);
    }

    private static String adjustSizeInUrl(String url, int size) {
        try {
            if (url.contains("size=")) return url;
            if (url.contains("?")) return url + "&size=" + size;
            return url + "?size=" + size;
        } catch (Exception e) {
            return url;
        }
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
        try (InputStream is = HeadImage.class.getResourceAsStream("/images/icon.jpg")) {
            if (is != null) return new Image(is);
        } catch (Exception ignored) {}
        return new Image("https://crafatar.com/avatars/00000000000000000000000000000000?size=16&overlay");
    }
}
