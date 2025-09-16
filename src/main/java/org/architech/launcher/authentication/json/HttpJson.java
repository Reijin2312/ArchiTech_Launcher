package org.architech.launcher.authentication.json;

import com.fasterxml.jackson.databind.JsonNode;
import org.architech.launcher.utils.Jsons;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

public final class HttpJson {
    private static final HttpClient C = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public static JsonNode postForm(String url, Map<String,String> form) throws Exception {
        String body = UrlEncoded.form(form);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return sendJson(req);
    }

    public static JsonNode postJson(String url, Object payload) throws Exception {
        String body = (payload instanceof String) ? (String) payload : Jsons.MAPPER.writeValueAsString(payload);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return sendJson(req);
    }

    public static JsonNode getJson(String url, String bearer) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json");
        if (bearer != null && !bearer.isEmpty()) b.header("Authorization", "Bearer " + bearer);
        return sendJson(b.GET().build());
    }

    private static JsonNode sendJson(HttpRequest req) throws Exception {
        HttpResponse<String> res = C.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() / 100 != 2) throw new RuntimeException("HTTP " + res.statusCode() + " " + res.body());
        return Jsons.MAPPER.readTree(res.body());
    }

    private HttpJson() {}
}
