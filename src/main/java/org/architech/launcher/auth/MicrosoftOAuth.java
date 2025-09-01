package org.architech.launcher.auth;

import org.architech.launcher.auth.App.MsaApp;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class MicrosoftOAuth {
    public static class Token {
        public final String accessToken;
        public final String refreshToken;
        public final Instant expiresAt;
        public Token(String at, String rt, long expiresInSec) {
            this.accessToken = at;
            this.refreshToken = rt;
            this.expiresAt = Instant.now().plusSeconds(expiresInSec - 30);
        }
    }

    public static String buildAuthorizeUrl(String state, String codeChallenge) {
        String scope = String.join(" ", MsaApp.SCOPES);
        return MsaApp.AUTH_URL
                + "?client_id=" + enc(MsaApp.CLIENT_ID)
                + "&response_type=code"
                + "&redirect_uri=" + enc(MsaApp.REDIRECT_URI)
                + "&response_mode=query"
                + "&scope=" + enc(scope)
                + "&prompt=select_account"
                + "&code_challenge_method=S256"
                + "&code_challenge=" + enc(codeChallenge)
                + "&state=" + enc(state);
    }

    public static Token exchangeCode(String code, String codeVerifier) throws Exception {
        Map<String,String> form = new LinkedHashMap<>();
        form.put("client_id", MsaApp.CLIENT_ID);
        form.put("grant_type", "authorization_code");
        form.put("code", code);
        form.put("redirect_uri", MsaApp.REDIRECT_URI);
        form.put("scope", String.join(" ", MsaApp.SCOPES));
        form.put("code_verifier", codeVerifier);

        JSONObject j = HttpJson.postForm(MsaApp.TOKEN_URL, form);
        return new Token(j.getString("access_token"),
                j.optString("refresh_token", null),
                j.getLong("expires_in"));
    }

    public static Token refresh(String refreshToken) throws Exception {
        Map<String,String> form = new LinkedHashMap<>();
        form.put("client_id", MsaApp.CLIENT_ID);
        form.put("grant_type", "refresh_token");
        form.put("refresh_token", refreshToken);
        form.put("scope", String.join(" ", MsaApp.SCOPES));

        JSONObject j = HttpJson.postForm(MsaApp.TOKEN_URL, form);
        return new Token(j.getString("access_token"),
                j.optString("refresh_token", refreshToken),
                j.getLong("expires_in"));
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}