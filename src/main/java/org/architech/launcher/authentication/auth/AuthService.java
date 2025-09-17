package org.architech.launcher.authentication.auth;

import org.architech.launcher.authentication.account.Account;
import org.architech.launcher.authentication.account.AccountType;
import org.architech.launcher.authentication.ely_by.ElyApp;
import org.architech.launcher.authentication.ely_by.ElyProfile;
import org.architech.launcher.authentication.microsoft.MicrosoftOAuth;
import org.architech.launcher.authentication.microsoft.MinecraftMicrosoftFlow;
import org.architech.launcher.authentication.microsoft.Pkce;
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
        Account a = Auth.current();
        if (a == null || a.type != AccountType.ELY || a.launcherToken == null) return null;

        RefreshRequest reqBody = new RefreshRequest();
        reqBody.launcherToken = a.launcherToken;
        String json = Jsons.PRETTY.writeValueAsString(reqBody);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BACKEND_URL + "/api/ely/refresh"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) return null;

        ExchangeResponse resp = Jsons.MAPPER.readValue(res.body(), ExchangeResponse.class);
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

        GameParams params = Jsons.MAPPER.readValue(res.body(), GameParams.class);

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

    public static Account offlineLogin(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            nickname = "Player";
        }

        Account acc = new Account();

        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + nickname).getBytes(StandardCharsets.UTF_8));

        acc.type = AccountType.OFFLINE;
        acc.userType = "legacy";
        acc.username = nickname;
        acc.uuid = uuid.toString();
        acc.accessToken = "0";
        acc.expiresAtSec = System.currentTimeMillis() / 1000L + (365L * 24 * 3600); // год вперёд
        acc.avatarUrl = "https://crafatar.com/avatars/" + uuid.toString().replace("-", "") + "?size=20&overlay";
        return acc;
    }
}