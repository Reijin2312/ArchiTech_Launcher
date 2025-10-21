package org.architech.launcher.authentication.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import javafx.application.Platform;
import javafx.scene.control.*;
import org.architech.launcher.ArchiTechLauncher;
import org.architech.launcher.authentication.account.Account;
import org.architech.launcher.authentication.account.AccountManager;
import org.architech.launcher.gui.error.ErrorPanel;
import org.architech.launcher.utils.Jsons;
import org.architech.launcher.utils.Utils;
import org.architech.launcher.utils.logging.LogManager;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.architech.launcher.ArchiTechLauncher.BACKEND_URL;

public final class BrowserAuth {

    private BrowserAuth() {}

    public static void openLogin(Button triggerBtn, Consumer<Account> onSuccess) {
        startWebFlow(triggerBtn, "/web/login.html", onSuccess);
    }

    public static void openRegistration(Button triggerBtn, Consumer<Account> onSuccess) {
        startWebFlow(triggerBtn, "/web/register.html", onSuccess);
    }

    private static void startWebFlow(Button triggerBtn, String pagePath, Consumer<Account> onSuccess) {
        if (triggerBtn != null) Platform.runLater(() -> triggerBtn.setDisable(true));

        ArchiTechLauncher.backgroundExecutor.submit(() -> {
            HttpServer server = null;
            final AtomicBoolean gotPayload = new AtomicBoolean(false);
            try {
                // 1) поднимаем локальный колбэк
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                final int port = server.getAddress().getPort();
                final String callbackPath = "/cb";
                final CompletableFuture<String> payload = new CompletableFuture<>();

                // /cb с CORS и OPTIONS
                HttpServer finalServer = server;
                server.createContext(callbackPath, ex -> handleCallback(ex, payload, gotPayload, finalServer));

                server.setExecutor(ArchiTechLauncher.backgroundExecutor);
                server.start();

                // 2) открываем страницу на бэке, передаём redirect_uri
                String redirect = "http://127.0.0.1:" + port + callbackPath;
                String url = BACKEND_URL + pagePath + "?redirect_uri=" + URLEncoder.encode(redirect, StandardCharsets.UTF_8);
                Utils.openInBrowser(url);

                // 3) ждём JSON с токенами
                String json = payload.get(180, TimeUnit.SECONDS);
                JsonNode n = Jsons.MAPPER.readTree(json);

                String access  = n.path("accessToken").asText(null);
                String refresh = n.path("refreshToken").asText(null);
                JsonNode user  = n.path("user");

                if (access == null || refresh == null) {
                    throw new IllegalStateException("Страница не вернула токены");
                }

                Account a = new Account();
                a.setAccessToken(access);
                a.setRefreshToken(refresh);
                if (user != null) {
                    a.setUuid(user.path("id").asText(null));
                    a.setUsername(user.path("username").asText(null));
                    a.setEmail(user.path("email").asText(null));
                }
                AccountManager.setCurrentAccount(a);

                if (onSuccess != null) {
                    Platform.runLater(() -> {
                        try { onSuccess.accept(a); }
                        catch (Exception e) { LogManager.getLogger().warning("onSuccess failed: " + e); }
                    });
                }

            } catch (Exception ex) {
                LogManager.getLogger().severe("Web auth failed: " + ex);
                final String details = safeStack(ex);
                Platform.runLater(() -> ErrorPanel.showError("Вход в браузере не удался", details));
            } finally {
                if (triggerBtn != null) Platform.runLater(() -> triggerBtn.setDisable(false));
                try { if (server != null && !gotPayload.get()) server.stop(0); } catch (Exception ignore) {}
            }
        });
    }

    private static void handleCallback(HttpExchange ex, CompletableFuture<String> payload, AtomicBoolean gotPayload, HttpServer server) {
        try {
            ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
            ex.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");

            String method = ex.getRequestMethod();
            if ("OPTIONS".equalsIgnoreCase(method)) {
                ex.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(method)) {
                ex.sendResponseHeaders(405, -1);
                return;
            }

            String json = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            payload.complete(json);
            gotPayload.set(true);

            byte[] ok = "OK".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            ex.getResponseHeaders().set("Cache-Control", "no-store");
            ex.sendResponseHeaders(200, ok.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(ok); }

        } catch (Exception e) {
            try { ex.sendResponseHeaders(500, -1); } catch (Exception ignore) {}
        } finally {
            try { ex.close(); } catch (Exception ignore) {}
            if (gotPayload.get()) {
                try { server.stop(0); } catch (Exception ignore) {}
            }
        }
    }

    private static String safeStack(Throwable t) {
        try (var sw = new java.io.StringWriter(); var pw = new java.io.PrintWriter(sw)) {
            t.printStackTrace(pw);
            return sw.toString();
        } catch (Exception e) {
            return Objects.requireNonNullElse(t.getMessage(), t.toString());
        }
    }
}
