package org.architech.launcher.authentication.auth;

import com.fasterxml.jackson.databind.JsonNode;
import org.architech.launcher.ArchiTechLauncher;
import org.architech.launcher.authentication.account.Account;
import org.architech.launcher.authentication.account.AccountManager;
import org.architech.launcher.authentication.requests.GameParams;
import org.architech.launcher.utils.Jsons;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public class AuthService {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private AuthService() {}

    public static boolean refreshTokens() {
        try {
            Account a = AccountManager.getCurrentAccount();
            if (a == null || a.getRefreshToken() == null) return false;

            String body = "{\"refreshToken\":\"" + a.getRefreshToken() + "\"}";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(ArchiTechLauncher.BACKEND_URL + "/api/auth/refresh"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) return false;

            JsonNode n = Jsons.MAPPER.readTree(res.body());
            String access  = n.path("accessToken").asText(null);
            String refresh = n.path("refreshToken").asText(null);
            if (access == null) return false;

            a.setAccessToken(access);
            if (refresh != null && !refresh.isBlank()) a.setRefreshToken(refresh); // ротация
            AccountManager.setCurrentAccount(a);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static GameParams getGameParams() throws Exception {
        Account a = AccountManager.getCurrentAccount();
        if (a == null || a.getAccessToken() == null)
            throw new IllegalStateException("Вход не выполнен");

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(ArchiTechLauncher.BACKEND_URL + "/api/ely/game-params"))
                .header("Authorization", "Bearer " + a.getAccessToken())
                .GET()
                .build();

        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 401 && refreshTokens()) {
            req = HttpRequest.newBuilder()
                    .uri(URI.create(ArchiTechLauncher.BACKEND_URL + "/api/ely/game-params"))
                    .header("Authorization", "Bearer " + AccountManager.getCurrentAccount().getAccessToken())
                    .GET()
                    .build();
            res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        }
        if (res.statusCode() != 200)
            throw new Exception("Не удалось получить игровые параметры: HTTP " + res.statusCode() + " — " + res.body());

        GameParams params = Jsons.MAPPER.readValue(res.body(), GameParams.class);

        if (params != null && params.selectedProfile != null) {
            a.setUsername(params.selectedProfile.name);
            a.setUuid(params.selectedProfile.uuid);
            try {
                JsonNode n = Jsons.MAPPER.readTree(res.body());
                String skinUrl = n.path("skinUrl").asText(null);
                if (skinUrl != null && !skinUrl.isBlank()) a.setSkinUrl(skinUrl);
            } catch (Exception ignore) {}
            AccountManager.setCurrentAccount(a);
        }
        return params;
    }

    public static void logout() {
        try {
            Account a = AccountManager.getCurrentAccount();
            if (a != null && a.getRefreshToken() != null) {
                String body = "{\"refreshToken\":\"" + a.getRefreshToken() + "\"}";
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(ArchiTechLauncher.BACKEND_URL + "/api/auth/logout"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build();
                HTTP.send(req, HttpResponse.BodyHandlers.discarding());
            }
        } catch (Exception ignore) {
        } finally {
            AccountManager.clear();
        }
    }

}