package org.architech.launcher.authentication.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.architech.launcher.ArchiTechLauncher;
import org.architech.launcher.authentication.account.Account;
import org.architech.launcher.authentication.account.AccountManager;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.architech.launcher.ArchiTechLauncher.BACKEND_URL;
import static org.architech.launcher.ArchiTechLauncher.FRONTEND_URL;

public final class JoinTicketService {
    private static final ObjectMapper M = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(ArchiTechLauncher.HTTP_TIMEOUT))
            .build();

    private JoinTicketService() {}

    public record JoinTicket(String joinId, String expiresAt, String clientIp) {}

    public static JoinTicket request(Account acc, String serverTag) throws Exception {
        if (acc == null) {
            throw new IllegalStateException("Not logged in");
        }
        String access = acc.getAccessToken();
        if (access == null || access.isBlank()) {
            if (!refresh(acc)) throw new IllegalStateException("Missing access token");
            access = acc.getAccessToken();
        }

        ObjectNode body = M.createObjectNode();
        if (serverTag != null && !serverTag.isBlank()) {
            body.put("server", serverTag);
        }

        HttpRequest req = HttpRequest.newBuilder(URI.create(BACKEND_URL + "/api/v1/mc/join/prepare"))
                .header("Authorization", "Bearer " + access)
                .header("Content-Type", "application/json; charset=utf-8")
                .timeout(Duration.ofSeconds(ArchiTechLauncher.HTTP_TIMEOUT))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), UTF_8))
                .build();

        HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString(UTF_8));
        if (r.statusCode() == 401 && refresh(acc)) {
            return request(acc, serverTag);
        }
        if (r.statusCode() / 100 != 2) {
            throw new IOException("Join ticket request failed: HTTP " + r.statusCode());
        }
        JsonNode n = M.readTree(r.body());
        return new JoinTicket(
                textOrNull(n, "joinId"),
                textOrNull(n, "expiresAt"),
                textOrNull(n, "clientIp")
        );
    }

    public static boolean consume(Account acc, String joinId) {
        if (joinId == null || joinId.isBlank()) return true;
        String access = acc != null ? acc.getAccessToken() : null;
        if (access == null || access.isBlank()) {
            if (!refresh(acc)) return false;
            access = acc.getAccessToken();
        }
        try {
            ObjectNode body = M.createObjectNode();
            body.put("joinId", joinId);

            HttpRequest req = HttpRequest.newBuilder(URI.create(BACKEND_URL + "/api/v1/mc/join/consume"))
                    .header("Authorization", "Bearer " + access)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .timeout(Duration.ofSeconds(ArchiTechLauncher.HTTP_TIMEOUT))
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), UTF_8))
                    .build();

            HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString(UTF_8));
            if (r.statusCode() == 401 && refresh(acc)) {
                return consume(acc, joinId);
            }
            return r.statusCode() / 100 == 2;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean refresh(Account acc) {
        String refresh = acc.getRefreshToken();
        if (refresh == null || refresh.isBlank()) return false;
        try {
            ObjectNode body = M.createObjectNode();
            body.put("refreshToken", refresh);

            HttpRequest refreshReq = HttpRequest.newBuilder(URI.create(BACKEND_URL + "/api/auth/refresh"))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Origin", FRONTEND_URL)
                    .timeout(Duration.ofSeconds(ArchiTechLauncher.HTTP_TIMEOUT))
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), UTF_8))
                    .build();

            HttpResponse<String> resp = HTTP.send(refreshReq, HttpResponse.BodyHandlers.ofString(UTF_8));
            if (resp.statusCode() / 100 != 2) return false;
            JsonNode n = M.readTree(resp.body());
            String newAccess = textOrNull(n, "accessToken");
            String newRefresh = textOrNull(n, "refreshToken");
            acc.setAccessToken(newAccess);
            if (newRefresh != null) acc.setRefreshToken(newRefresh);
            acc.setAccessExpiresAtSec(parseEpoch(n, "accessExpiresAt"));
            acc.setRefreshExpiresAtSec(parseEpoch(n, "refreshExpiresAt"));
            AccountManager.setCurrentAccount(acc);
            return newAccess != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static long parseEpoch(JsonNode n, String field) {
        String v = textOrNull(n, field);
        if (v == null) return 0L;
        try {
            return Instant.parse(v).getEpochSecond();
        } catch (Exception e) {
            return 0L;
        }
    }

    private static String textOrNull(JsonNode n, String field) {
        if (n == null || !n.has(field) || n.get(field).isNull()) return null;
        return n.get(field).asText();
    }
}
