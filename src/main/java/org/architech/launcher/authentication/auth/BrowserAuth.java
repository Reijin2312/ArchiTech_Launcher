package org.architech.launcher.authentication.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.*;
import javafx.application.Platform;
import javafx.scene.control.Button;
import org.architech.launcher.ArchiTechLauncher;
import org.architech.launcher.authentication.account.Account;
import org.architech.launcher.authentication.account.AccountManager;
import org.architech.launcher.gui.error.ErrorPanel;
import java.awt.Desktop;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Consumer;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.architech.launcher.ArchiTechLauncher.BACKEND_URL;

public final class BrowserAuth {

    private static final ObjectMapper M = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(ArchiTechLauncher.HTTP_TIMEOUT)).build();

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
            HttpServer srv = null;
            try {
                var bind = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0);
                srv = HttpServer.create(bind, 0);
                String cbPath = "/cb";
                var latch = new CountDownLatch(1);

                final String[] tokens = new String[2];
                final JsonNode[] userBox = new JsonNode[1];

                srv.createContext(cbPath, exchange -> {
                    try {
                        addCors(exchange);
                        String method = exchange.getRequestMethod();
                        if ("OPTIONS".equalsIgnoreCase(method)) {
                            exchange.sendResponseHeaders(204, -1);
                            return;
                        }
                        if (!"POST".equalsIgnoreCase(method)) {
                            write(exchange, 405, "{\"error\":\"method\"}");
                            return;
                        }
                        byte[] body = exchange.getRequestBody().readAllBytes();
                        JsonNode n = M.readTree(new String(body, UTF_8));
                        tokens[0] = textOrNull(n, "accessToken");
                        tokens[1] = textOrNull(n, "refreshToken");
                        userBox[0] = n.path("user").isMissingNode() ? null : n.path("user");

                        if (tokens[0] == null || tokens[1] == null) {
                            write(exchange, 400, "{\"ok\":false,\"msg\":\"missing tokens\"}");
                            return;
                        }
                        write(exchange, 200, "{\"ok\":true}");
                    } catch (Exception e) {
                        writeSafe(exchange, 500, "{\"ok\":false}");
                    } finally {
                        latch.countDown();
                    }
                });
                srv.setExecutor(ArchiTechLauncher.backgroundExecutor);
                srv.start();

                int port = srv.getAddress().getPort();
                String cb = "http://127.0.0.1:" + port + cbPath;

                URI loginUrl = buildFrontendUri(route, cb);
                openInBrowser(loginUrl);

                boolean ok = latch.await(180, TimeUnit.SECONDS);
                if (!ok) throw new TimeoutException("no callback");

                String access = tokens[0], refresh = tokens[1];
                JsonNode user = userBox[0];

                Account a = new Account();
                a.setAccessToken(access);
                a.setRefreshToken(refresh);
                if (user != null) {
                    a.setUuid(textOrNull(user, "id"));
                    a.setUsername(textOrNull(user, "username"));
                    a.setEmail(textOrNull(user, "email"));
                }

                try {
                    HttpRequest meReq = HttpRequest.newBuilder(URI.create(BACKEND_URL + "/api/v1/profile"))
                            .header("Authorization", "Bearer " + access)
                            .GET().timeout(Duration.ofSeconds(10)).build();
                    HttpResponse<String> r = HTTP.send(meReq, HttpResponse.BodyHandlers.ofString(UTF_8));
                    if (r.statusCode() / 100 == 2) {
                        JsonNode me = M.readTree(r.body());
                        if (me.hasNonNull("username")) a.setUsername(me.get("username").asText());
                        if (me.hasNonNull("avatarUrl")) a.setAvatarUrl(me.get("avatarUrl").asText());
                        if (me.hasNonNull("skinUrl"))   a.setSkinUrl(me.get("skinUrl").asText());
                    }
                } catch (Exception ignored) {}

                AccountManager.setCurrentAccount(a);
                if (onSuccess != null) {
                    Platform.runLater(() -> onSuccess.accept(a));
                }

            } catch (Exception e) {
                Platform.runLater(() -> ErrorPanel.showError("Авторизация не выполнена", stack(e)));
            } finally {
                if (srv != null) srv.stop(0);
                if (triggerBtn != null) Platform.runLater(() -> triggerBtn.setDisable(false));
            }
        });
    }

    private static URI buildFrontendUri(String route, String redirectUri) throws URISyntaxException {
        Objects.requireNonNull(route);
        URI api = URI.create(BACKEND_URL);
        URI base = new URI(api.getScheme(), api.getUserInfo(), api.getHost(), api.getPort(), "/", null, null);
        String q = "redirect_uri=" + URLEncoder.encode(redirectUri, UTF_8);
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

    private static void addCors(HttpExchange ex){
        Headers h = ex.getResponseHeaders();
        h.set("Access-Control-Allow-Origin", "*");
        h.set("Access-Control-Allow-Methods", "POST, OPTIONS");
        h.set("Access-Control-Allow-Headers", "Content-Type");
    }

    private static void write(HttpExchange ex, int code, String body) throws IOException {
        byte[] b = body.getBytes(UTF_8);
        ex.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }
    private static void writeSafe(HttpExchange ex, int code, String body){
        try { write(ex, code, body); } catch (Exception ignored) {}
    }

    private static String textOrNull(JsonNode n, String f){
        if (n == null) return null;
        JsonNode x = n.path(f);
        return x.isMissingNode() || x.isNull() ? null : x.asText();
    }

    private static String stack(Throwable t){
        try (var sw = new StringWriter(); var pw = new PrintWriter(sw)) { t.printStackTrace(pw); return sw.toString(); }
        catch (Exception e){ return t.getMessage(); }
    }
}