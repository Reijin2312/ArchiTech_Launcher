package org.architech.launcher.authentication.auth.microsoft;

public final class MsaApp {
    public static final String CLIENT_ID     = "YOUR-AZURE-APP-CLIENT-ID";
    public static final String REDIRECT_URI  = "http://127.0.0.1:51789/callback"; // совпадает с локальным сервером
    public static final String[] SCOPES      = new String[]{
            "XboxLive.signin", "offline_access", "openid", "profile", "email"
    };

    public static final String AUTH_URL  = "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize";
    public static final String TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";

    private MsaApp() {}
}
