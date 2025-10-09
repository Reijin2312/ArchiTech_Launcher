package org.architech.launcher.authentication.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.architech.launcher.authentication.account.Account;
import org.architech.launcher.authentication.account.AccountManager;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class HttpAuth {
    private static final ObjectMapper M = new ObjectMapper();

    public static HttpURLConnection open(String method, String url, byte[] body) throws Exception {
        Account a = AccountManager.getCurrentAccount();
        if (a == null) throw new IllegalStateException("not logged");

        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(15_000);
        c.setReadTimeout(15_000);
        c.setDoInput(true);
        if (body != null && ("POST".equals(method) || "PUT".equals(method))) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
        }
        if (a.getAccessToken() != null) c.setRequestProperty("Authorization", "Bearer " + a.getAccessToken());

        if (body != null) c.getOutputStream().write(body);
        int code = c.getResponseCode();
        if (code == 401 && a.getRefreshToken() != null) {

            if (refreshTokens()) {

                Account a2 = AccountManager.getCurrentAccount();
                c = (HttpURLConnection) new URL(url).openConnection();
                c.setRequestMethod(method);
                c.setConnectTimeout(15_000);
                c.setReadTimeout(15_000);
                c.setDoInput(true);
                if (body != null && ("POST".equals(method) || "PUT".equals(method))) {
                    c.setDoOutput(true);
                    c.setRequestProperty("Content-Type", "application/json");
                }
                if (a2.getAccessToken() != null) c.setRequestProperty("Authorization", "Bearer " + a2.getAccessToken());
                if (body != null) c.getOutputStream().write(body);
            }
        }
        return c;
    }

    private static boolean refreshTokens() {
        try {
            Account a = AccountManager.getCurrentAccount();
            if (a == null || a.getRefreshToken() == null) return false;
            URL u = new URL(System.getProperty("architech.backend.base", "http://127.0.0.1:51789") + "/api/auth/refresh");
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            String json = "{\"refreshToken\":\"" + a.getRefreshToken() + "\"}";
            c.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));
            int code = c.getResponseCode();
            if (code != 200) return false;
            JsonNode n = M.readTree(c.getInputStream());
            String access = n.path("accessToken").asText(null);
            String refresh = n.path("refreshToken").asText(null);
            if (access == null) return false;
            a.setAccessToken(access);
            if (refresh != null) a.setRefreshToken(refresh);
            AccountManager.setCurrentAccount(a);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

}
