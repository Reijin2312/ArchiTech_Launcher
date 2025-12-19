package org.architech.launcher.authentication.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.scene.control.Button;
import org.architech.launcher.ArchiTechLauncher;
import org.architech.launcher.authentication.account.Account;
import org.architech.launcher.authentication.account.AccountManager;
import org.architech.launcher.gui.error.ErrorPanel;

import java.awt.Desktop;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.architech.launcher.ArchiTechLauncher.BACKEND_URL;
import static org.architech.launcher.ArchiTechLauncher.FRONTEND_URL;

public final class BrowserAuth {

    private static final ObjectMapper M = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(Math.max(5, ArchiTechLauncher.HTTP_TIMEOUT)))
            .build();

    private BrowserAuth() {}

    public static void openLogin(Button triggerBtn, Consumer<Account> onSuccess) {
        startWebFlow("/auth/login", triggerBtn, onSuccess);
    }

    public static void openRegister(Button triggerBtn, Consumer<Account> onSuccess) {
        startWebFlow("/auth/register", triggerBtn, onSuccess);
    }

    private static void startWebFlow(String route, Button triggerBtn, Consumer<Account> onSuccess) {
        if (triggerBtn != null) Platform.runLater(() -> triggerBtn.setDisable(true));

        ArchiTechLauncher.backgroundExecutor.submit(() -> {
            try {
                DeviceSession session = beginDeviceSession();
                URI loginUrl = buildFrontendUri(route, session.deviceCode());
                openInBrowser(loginUrl);

                JsonNode payload = pollForPayload(session.pollToken(), session.expiresIn());
                Account account = buildAccount(payload);

                AccountManager.setCurrentAccount(account);
                if (onSuccess != null) {
                    Platform.runLater(() -> onSuccess.accept(account));
                }

            } catch (Exception e) {
                Platform.runLater(() -> ErrorPanel.showError("Ошибка авторизации", stack(e)));
            } finally {
                if (triggerBtn != null) Platform.runLater(() -> triggerBtn.setDisable(false));
            }
        });
    }

    private static DeviceSession beginDeviceSession() throws Exception {
        HttpRequest beginReq = HttpRequest.newBuilder(URI.create(BACKEND_URL + "/api/auth/device/begin"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(Math.max(5, ArchiTechLauncher.HTTP_TIMEOUT)))
                .POST(HttpRequest.BodyPublishers.ofString("{}", UTF_8))
                .build();
        HttpResponse<String> beginResp = HTTP.send(beginReq, HttpResponse.BodyHandlers.ofString(UTF_8));
        if (beginResp.statusCode() / 100 != 2) throw new IllegalStateException("device begin failed: " + beginResp.statusCode());
        JsonNode begin = M.readTree(beginResp.body());
        String deviceCode = textOrNull(begin, "deviceCode");
        String pollToken = textOrNull(begin, "pollToken");
        int expiresIn = begin.hasNonNull("expiresIn") ? begin.get("expiresIn").asInt() : 180;
        if (deviceCode == null || pollToken == null) throw new IllegalStateException("missing device codes");
        return new DeviceSession(deviceCode, pollToken, expiresIn);
    }

    private static JsonNode pollForPayload(String pollToken, int expiresIn) throws Exception {
        long deadlineMs = System.currentTimeMillis() + (long) Math.max(1, expiresIn) * 1000L;
        String body = M.writeValueAsString(M.createObjectNode().put("pollToken", pollToken));

        while (System.currentTimeMillis() < deadlineMs) {
            HttpRequest pollReq = HttpRequest.newBuilder(URI.create(BACKEND_URL + "/api/auth/device/poll"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(Math.max(5, ArchiTechLauncher.HTTP_TIMEOUT)))
                    .POST(HttpRequest.BodyPublishers.ofString(body, UTF_8))
                    .build();

            HttpResponse<String> pollResp = HTTP.send(pollReq, HttpResponse.BodyHandlers.ofString(UTF_8));
            int code = pollResp.statusCode();
            if (code == 200) {
                JsonNode poll = M.readTree(pollResp.body());
                return poll.path("payload");
            } else if (code == 202) {
                Thread.sleep(2000);
                continue;
            } else if (code == 404) {
                throw new TimeoutException("device code expired");
            } else {
                throw new IllegalStateException("poll status " + code);
            }
        }

        throw new TimeoutException("no payload received");
    }

    private static Account buildAccount(JsonNode payload) throws Exception {
        if (payload == null || payload.isMissingNode()) throw new IllegalStateException("empty payload");
        String access = textOrNull(payload, "accessToken");
        String refresh = textOrNull(payload, "refreshToken");
        if (access == null || refresh == null) throw new IllegalStateException("tokens missing in payload");

        Account a = new Account();
        a.setAccessToken(access);
        a.setRefreshToken(refresh);

        String accessExp = textOrNull(payload, "accessExpiresAt");
        String refreshExp = textOrNull(payload, "refreshExpiresAt");
        if (accessExp != null) a.setAccessExpiresAtSec(parseEpoch(accessExp));
        if (refreshExp != null) a.setRefreshExpiresAtSec(parseEpoch(refreshExp));

        JsonNode user = payload.path("user");
        if (user != null && !user.isMissingNode()) {
            a.setUuid(textOrNull(user, "id"));
            a.setUsername(textOrNull(user, "username"));
            a.setEmail(textOrNull(user, "email"));
        }

        try {
            HttpRequest meReq = HttpRequest.newBuilder(URI.create(BACKEND_URL + "/api/v1/profile"))
                    .header("Authorization", "Bearer " + access)
                    .timeout(Duration.ofSeconds(Math.max(5, ArchiTechLauncher.HTTP_TIMEOUT)))
                    .GET()
                    .build();

            HttpResponse<String> r = HTTP.send(meReq, HttpResponse.BodyHandlers.ofString(UTF_8));
            if (r.statusCode() / 100 == 2) {
                JsonNode me = M.readTree(r.body());
                if (me.hasNonNull("username")) a.setUsername(me.get("username").asText());
                if (me.hasNonNull("avatarUrl")) a.setAvatarUrl(me.get("avatarUrl").asText());
                if (me.hasNonNull("skinUrl")) a.setSkinUrl(me.get("skinUrl").asText());
            }
        } catch (Exception ignored) {}

        return a;
    }

    private static URI buildFrontendUri(String route, String deviceCode) throws URISyntaxException {
        Objects.requireNonNull(route);
        URI api = URI.create(FRONTEND_URL);
        URI base = new URI(api.getScheme(), api.getUserInfo(), api.getHost(), api.getPort(), "/", null, null);
        String q = "device=" + URLEncoder.encode(deviceCode, StandardCharsets.UTF_8);
        String p = route.startsWith("/") ? route : ("/" + route);
        return new URI(base.getScheme(), base.getUserInfo(), base.getHost(), base.getPort(), p, q, null);
    }

    private static void openInBrowser(URI uri) throws Exception {
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(uri);
            return;
        }
        String u = uri.toString();
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", u).start();
        else if (os.contains("mac")) new ProcessBuilder("open", u).start();
        else new ProcessBuilder("xdg-open", u).start();
    }

    private static String textOrNull(JsonNode n, String f){
        if (n == null) return null;
        JsonNode x = n.path(f);
        return x.isMissingNode() || x.isNull() ? null : x.asText();
    }

    private static long parseEpoch(String iso) {
        try {
            return Instant.parse(iso).getEpochSecond();
        } catch (Exception e) {
            return 0;
        }
    }

    private static String stack(Throwable t){
        try (var sw = new StringWriter(); var pw = new PrintWriter(sw)) { t.printStackTrace(pw); return sw.toString(); }
        catch (Exception e){ return t.getMessage(); }
    }

    private record DeviceSession(String deviceCode, String pollToken, int expiresIn) {}
}
