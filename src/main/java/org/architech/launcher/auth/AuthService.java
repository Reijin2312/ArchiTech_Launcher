// org.architech.launcher.auth.AuthService
package org.architech.launcher.auth;

import com.google.gson.Gson;
import org.architech.launcher.auth.App.ElyApp;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import static org.architech.launcher.MCLauncher.BACKEND_URL;

public class AuthService {

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Gson GSON = new Gson();

    public static Account tryElySilentLogin() throws Exception {
        Account a = Auth.current();
        if (a == null || a.type != AccountType.ELY || a.launcherToken == null) return null;

        RefreshRequest reqBody = new RefreshRequest();
        reqBody.launcherToken = a.launcherToken;
        String json = GSON.toJson(reqBody);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BACKEND_URL + "/api/ely/refresh"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) return null;

        ExchangeResponse resp = GSON.fromJson(res.body(), ExchangeResponse.class);
        a.launcherToken = resp.launcherToken;
        a.expiresAtSec = Instant.parse(resp.launcherTokenExpiresAt).getEpochSecond();
        updateAccountFromProfile(a, resp.profile);
        Auth.set(a);
        return a;
    }

    public static Account finishElyLoginWithCode(String authCode) throws Exception {
        ExchangeRequest reqBody = new ExchangeRequest();
        reqBody.code = authCode;
        reqBody.redirectUri = ElyApp.REDIRECT_URI;
        String json = GSON.toJson(reqBody);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BACKEND_URL + "/api/ely/exchange"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) throw new Exception("Exchange failed: " + res.body());

        ExchangeResponse resp = GSON.fromJson(res.body(), ExchangeResponse.class);
        Account a = new Account();
        a.type = AccountType.ELY;
        a.launcherToken = resp.launcherToken;
        a.expiresAtSec = Instant.parse(resp.launcherTokenExpiresAt).getEpochSecond();
        updateAccountFromProfile(a, resp.profile);
        Auth.set(a);
        return a;
    }

    public static GameParams getGameParams(String launcherToken) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BACKEND_URL + "/api/ely/game-params"))
                .header("X-Launcher-Token", launcherToken)
                .GET()
                .build();

        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() != 200) {
            throw new Exception("Ошибка получения игровых параметров: " + res.statusCode());
        }

        GameParams params = GSON.fromJson(res.body(), GameParams.class);

        Account a = Auth.current();
        if (a != null && a.type == AccountType.ELY) {
            a.username = params.selectedProfile.name;
            a.uuid = params.selectedProfile.uuid;
            a.accessToken = params.accessToken;
            a.skinUrl = "http://skinsystem.ely.by/skins/" + params.selectedProfile.name + ".png";
            Auth.set(a);
        }

        return params;
    }

    private static void updateAccountFromProfile(Account a, ElyProfile p) {
        a.uuid = p.uuid;
        a.username = p.username;
        a.skinUrl = "http://skinsystem.ely.by/skins/" + p.username + ".png";
        a.avatarUrl = "https://ely.by/head/" + p.id;
    }

    public static class MsAuthContext {
        public final String state = UUID.randomUUID().toString();
        public final String codeVerifier = Pkce.newCodeVerifier();
        public final String codeChallenge = Pkce.codeChallengeS256(codeVerifier);
        public String refreshToken; // сохраняй для «тихого» логина
    }

    public static String buildMicrosoftLoginUrl(MsAuthContext ctx) {
        return MicrosoftOAuth.buildAuthorizeUrl(ctx.state, ctx.codeChallenge);
    }

    // вызывается после возврата из браузера с ?code=..&state=..
    public static Account finishMicrosoftLogin(String authCode, MsAuthContext ctx) throws Exception {
        // 1) exchange code -> MSA tokens
        var msa = MicrosoftOAuth.exchangeCode(authCode, ctx.codeVerifier);
        ctx.refreshToken = msa.refreshToken;

        // 2) XBL -> XSTS -> MC, затем профиль/владение
        var session = MinecraftMicrosoftFlow.fullLogin(msa.accessToken);

        // 3) Сохраняем/возвращаем аккаунт
        Account acc = new Account();
        acc.type = AccountType.MICROSOFT;
        acc.username = session.username;
        acc.uuid = session.uuid;
        acc.mcAccessToken = session.mcAccessToken;
        //acc.mcAccessTokenExpiresAt = session.mcAccessTokenExp;
        acc.msaRefreshToken = ctx.refreshToken;
        acc.ownsMinecraft = session.ownsMinecraft;
        return acc;
    }

    // «Тихий» вход: обновить refresh->access и снова прокрутить XBL/XSTS/MC
    public static Account tryMicrosoftSilentRefresh(Account acc) throws Exception {
        if (acc == null || acc.msaRefreshToken == null) return null;
        var msa = MicrosoftOAuth.refresh(acc.msaRefreshToken);
        acc.msaRefreshToken = msa.refreshToken; // MS может вернуть новый refresh
        var session = MinecraftMicrosoftFlow.fullLogin(msa.accessToken);
        acc.mcAccessToken = session.mcAccessToken;
        //acc.mcAccessTokenExpiresAt = session.mcAccessTokenExp;
        acc.username = session.username;
        acc.uuid = session.uuid;
        acc.ownsMinecraft = session.ownsMinecraft;
        return acc;
    }
}