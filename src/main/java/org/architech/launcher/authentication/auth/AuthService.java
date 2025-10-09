package org.architech.launcher.authentication.auth;

import org.architech.launcher.authentication.account.Account;
import org.architech.launcher.authentication.account.AccountManager;
import org.architech.launcher.authentication.auth.ely_by.ElyApp;
import org.architech.launcher.authentication.auth.ely_by.ElyProfile;
import org.architech.launcher.authentication.auth.microsoft.MicrosoftOAuth;
import org.architech.launcher.authentication.auth.microsoft.MinecraftMicrosoftFlow;
import org.architech.launcher.authentication.auth.microsoft.Pkce;
import org.architech.launcher.authentication.requests.ExchangeRequest;
import org.architech.launcher.authentication.requests.ExchangeResponse;
import org.architech.launcher.authentication.requests.GameParams;
import org.architech.launcher.authentication.requests.RefreshRequest;
import org.architech.launcher.utils.Jsons;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.architech.launcher.ArchiTechLauncher.BACKEND_URL;

public class AuthService {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    public static Account tryElySilentLogin() throws Exception {
        Account a = AccountManager.getCurrentAccount();
        if (a == null || a.getType() != AccountType.ELY || a.getLauncherToken() == null) return null;

        RefreshRequest reqBody = new RefreshRequest();
        reqBody.launcherToken = a.getLauncherToken();
        String json = Jsons.PRETTY.writeValueAsString(reqBody);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BACKEND_URL + "/api/ely/refresh"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) return null;

        ExchangeResponse resp = Jsons.MAPPER.readValue(res.body(), ExchangeResponse.class);
        a.setLauncherToken(resp.launcherToken);
        a.setExpiresAtSec(Instant.parse(resp.launcherTokenExpiresAt).getEpochSecond());
        updateAccountFromProfile(a, resp.profile);
        AccountManager.setCurrentAccount(a);
        return a;
    }

    public static Account finishElyLoginWithCode(String authCode) throws Exception {
        ExchangeRequest reqBody = new ExchangeRequest();
        reqBody.code = authCode;
        reqBody.redirectUri = ElyApp.REDIRECT_URI;
        String json = Jsons.PRETTY.writeValueAsString(reqBody);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BACKEND_URL + "/api/ely/exchange"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) throw new Exception("Exchange failed: " + res.body());

        ExchangeResponse resp = Jsons.MAPPER.readValue(res.body(), ExchangeResponse.class);
        Account a = new Account();
        a.setType(AccountType.ELY);
        a.setLauncherToken(resp.launcherToken);
        a.setExpiresAtSec(Instant.parse(resp.launcherTokenExpiresAt).getEpochSecond());
        updateAccountFromProfile(a, resp.profile);
        AccountManager.setCurrentAccount(a);
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

        GameParams params = Jsons.MAPPER.readValue(res.body(), GameParams.class);

        Account a = AccountManager.getCurrentAccount();
        if (a != null && a.getType() == AccountType.ELY) {
            a.setUsername(params.selectedProfile.name);
            a.setUuid(params.selectedProfile.uuid);
            a.setAccessToken(params.accessToken);
            a.setSkinUrl("http://skinsystem.ely.by/skins/" + params.selectedProfile.name + ".png");
            AccountManager.setCurrentAccount(a);
        }

        return params;
    }

    private static void updateAccountFromProfile(Account a, ElyProfile p) {
        a.setUuid(p.uuid);
        a.setUsername(p.username);
        a.setSkinUrl("http://skinsystem.ely.by/skins/" + p.username + ".png");
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

    public static Account finishMicrosoftLogin(String authCode, MsAuthContext ctx) throws Exception {
        var msa = MicrosoftOAuth.exchangeCode(authCode, ctx.codeVerifier);
        ctx.refreshToken = msa.refreshToken;

        var session = MinecraftMicrosoftFlow.fullLogin(msa.accessToken);

        Account acc = new Account();
        acc.setType(AccountType.MICROSOFT);
        acc.setUsername(session.username);
        acc.setUuid(session.uuid);
        //acc.mcAccessToken = session.mcAccessToken;
        //acc.mcAccessTokenExpiresAt = session.mcAccessTokenExp;
        //acc.msaRefreshToken = ctx.refreshToken;
        //acc.ownsMinecraft = session.ownsMinecraft;
        return acc;
    }

    public static Account tryMicrosoftSilentRefresh(Account acc) throws Exception {
        //if (acc == null || acc.msaRefreshToken == null) return null;
        //var msa = MicrosoftOAuth.refresh(acc.msaRefreshToken);
        //acc.msaRefreshToken = msa.refreshToken; // MS может вернуть новый refresh
        //var session = MinecraftMicrosoftFlow.fullLogin(msa.accessToken);
        //acc.mcAccessToken = session.mcAccessToken;
        //acc.mcAccessTokenExpiresAt = session.mcAccessTokenExp;
        //acc.username = session.username;
        //acc.uuid = session.uuid;
        //acc.ownsMinecraft = session.ownsMinecraft;
        return acc;
    }

    public static Account offlineLogin(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            nickname = "Player";
        }

        Account acc = new Account();

        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + nickname).getBytes(StandardCharsets.UTF_8));

        acc.setType(AccountType.OFFLINE);
        acc.setUserType("legacy");
        acc.setUsername(nickname);
        acc.setUuid(uuid.toString());
        acc.setAccessToken("0");
        acc.setExpiresAtSec(System.currentTimeMillis() / 1000L + (365L * 24 * 3600));
        return acc;
    }
}