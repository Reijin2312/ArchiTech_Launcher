package org.architech.launcher.authentication.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.architech.launcher.authentication.account.Account;
import org.architech.launcher.authentication.account.AccountManager;
import java.awt.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class WebFlow {
    private static final ObjectMapper M = new ObjectMapper();

    public static void startRegistration(String baseUrl) throws Exception {
        receiveTokensViaBrowser(baseUrl + "/web/register.html");
    }

    public static void startLogin(String baseUrl) throws Exception {
        receiveTokensViaBrowser(baseUrl + "/web/login.html");
    }

    private static void receiveTokensViaBrowser(String pageUrl) throws Exception {
        int port = pickPort();
        HttpServer srv = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        CompletableFuture<String> payload = new CompletableFuture<>();
        srv.createContext("/cb", ex -> {
            try {
                if (!"POST".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(405, -1); return; }
                byte[] b = ex.getRequestBody().readAllBytes();
                payload.complete(new String(b, StandardCharsets.UTF_8));
                byte[] ok = "OK".getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(200, ok.length);
                ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                ex.getResponseHeaders().set("Cache-Control", "no-store");
                ex.getResponseBody().write(ok);
            } catch (Exception ignore) {} finally { try { ex.close(); } catch (Exception ignored) {} srv.stop(0);} });
        srv.start();

        String redirect = "http://127.0.0.1:" + port + "/cb";
        Desktop.getDesktop().browse(URI.create(pageUrl + "?redirect_uri=" + URLEncoder.encode(redirect, StandardCharsets.UTF_8)));

        String json = payload.get(180, TimeUnit.SECONDS);
        JsonNode n = M.readTree(json);
        String access = n.path("accessToken").asText(null);
        String refresh = n.path("refreshToken").asText(null);
        JsonNode user = n.path("user");

        Account a = new Account();
        a.setUsername(user.path("username").asText(null));
        a.setEmail(user.path("email").asText(null));
        a.setUuid(user.path("id").asText(null));
        a.setAccessToken(access);
        a.setRefreshToken(refresh);
        AccountManager.setCurrentAccount(a);
    }

    private static int pickPort() throws Exception {
        try (var s = new java.net.ServerSocket(0, 0, java.net.InetAddress.getByName("127.0.0.1"))) { return s.getLocalPort(); }
    }
}
