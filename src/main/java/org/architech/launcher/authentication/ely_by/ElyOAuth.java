package org.architech.launcher.authentication.ely_by;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class ElyOAuth {
    private static final String AUTH_URL = "https://account.ely.by/oauth2/v1";

    public static String buildAuthorizeUrl(String state, String loginHint) {
        StringBuilder sb = new StringBuilder(AUTH_URL)
                .append("?client_id=").append(enc(ElyApp.CLIENT_ID))
                .append("&redirect_uri=").append(enc(ElyApp.REDIRECT_URI))
                .append("&response_type=code")
                .append("&scope=").append(enc(ElyApp.SCOPES))
                .append("&state=").append(enc(state));
        if (loginHint != null && !loginHint.isEmpty()) {
            sb.append("&login_hint=").append(enc(loginHint));
        }
        return sb.toString();
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    private ElyOAuth() {}
}
