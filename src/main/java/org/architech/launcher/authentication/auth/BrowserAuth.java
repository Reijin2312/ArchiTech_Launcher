package org.architech.launcher.authentication.auth;

import com.sun.net.httpserver.HttpServer;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import org.architech.launcher.authentication.account.Account;
import org.architech.launcher.authentication.account.AccountType;
import org.architech.launcher.authentication.ely_by.ElyOAuth;
import org.architech.launcher.authentication.requests.GameParams;
import org.architech.launcher.gui.error.ErrorPanel;
import org.architech.launcher.utils.Utils;
import org.architech.launcher.utils.logging.LogManager;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class BrowserAuth {

    public static void showOfflineDialog(Account currentAccount, TextField usernameField, Consumer<Account> setCurrentAccount) {
        TextInputDialog d = new TextInputDialog((currentAccount != null && currentAccount.getType() == AccountType.OFFLINE)
                ? currentAccount.getUsername()
                : (usernameField.getText() == null ? "Player" : usernameField.getText()));
        d.setTitle("Оффлайн вход");
        d.setHeaderText("Введите ник для оффлайн-режима");
        d.setContentText("Ник:");

        d.showAndWait().ifPresent(nick -> {
            if (nick.isBlank()) return;
            try {
                Account a = AuthService.offlineLogin(nick.trim());
                setCurrentAccount.accept(a);
                Auth.set(a);
            } catch (Exception ex) {
                LogManager.getLogger().severe("Не удалось установить оффлайн ник " + ex.getMessage());
                ErrorPanel.showError("Упс! Не удалось установить оффлайн ник :(", ex.getMessage());
            }
        });
    }

    public static void loginMicrosoftBrowser() {
        try {
            AuthService.MsAuthContext msCtx = new AuthService.MsAuthContext();
            String url = AuthService.buildMicrosoftLoginUrl(msCtx);
            Utils.openInBrowser(url);
        } catch (Exception e) {
            ErrorPanel.showError("Не удалось открыть авторизацию Microsoft", e.getMessage());
        }
    }

    public static void loginElyBrowser(Button accountBtn, Consumer<Account> setCurrentAccount) {
        accountBtn.setDisable(true);
        new Thread(() -> {
            try {
                Account silent = AuthService.tryElySilentLogin();
                if (silent != null) {
                    try {
                        GameParams params = AuthService.getGameParams(silent.getLauncherToken());
                        if (params != null && params.selectedProfile != null) {
                            if (params.selectedProfile.name != null && !params.selectedProfile.name.isBlank())
                                silent.setUsername(params.selectedProfile.name);
                            if (params.selectedProfile.uuid != null && !params.selectedProfile.uuid.isBlank())
                                silent.setUuid(params.selectedProfile.uuid);
                            if (params.accessToken != null && !params.accessToken.isBlank())
                                silent.setAccessToken(params.accessToken);
                            if (params.selectedProfile.name != null && !params.selectedProfile.name.isBlank())
                                silent.setSkinUrl("http://skinsystem.ely.by/skins/" + params.selectedProfile.name + ".png");
                            Auth.set(silent);
                        }
                    } catch (Exception ex) {
                        LogManager.getLogger().severe("Ошибка получения параметров аккаунта: " + ex.getMessage());
                        Platform.runLater(() -> ErrorPanel.showError("Упс! :( Ошибка получения параметров аккаунта", ex.getMessage()));
                    }
                    final Account accSilent = silent;
                    Platform.runLater(() -> setCurrentAccount.accept(accSilent));
                    return;
                }

                String state = UUID.randomUUID().toString();
                String url = ElyOAuth.buildAuthorizeUrl(state, null);
                Utils.openInBrowser(url);

                String code = waitForAuthCodeAndValidateState(state);
                Account a = AuthService.finishElyLoginWithCode(code);

                if (a.getType() == AccountType.ELY) {
                    try {
                        GameParams params = AuthService.getGameParams(a.getLauncherToken());
                        if (params != null && params.selectedProfile != null) {
                            if (params.selectedProfile.name != null && !params.selectedProfile.name.isBlank())
                                a.setUsername(params.selectedProfile.name);
                            if (params.selectedProfile.uuid != null && !params.selectedProfile.uuid.isBlank())
                                a.setUuid(params.selectedProfile.uuid);
                            if (params.accessToken != null && !params.accessToken.isBlank())
                                a.setAccessToken(params.accessToken);
                            if (params.selectedProfile.name != null && !params.selectedProfile.name.isBlank())
                                a.setSkinUrl("http://skinsystem.ely.by/skins/" + params.selectedProfile.name + ".png");
                            Auth.set(a);
                        }
                    } catch (Exception ex) {
                        LogManager.getLogger().severe("Ошибка получения параметров аккаунта: " + ex.getMessage());
                        Platform.runLater(() -> ErrorPanel.showError("Упс! :( Ошибка получения параметров аккаунта", ex.getMessage()));
                    }
                }

                final Account acc = a;
                Platform.runLater(() -> setCurrentAccount.accept(acc));

            } catch (Exception ex) {
                try {
                    Account fallback = AuthService.tryElySilentLogin();
                    if (fallback != null) {
                        Platform.runLater(() -> setCurrentAccount.accept(fallback));
                        return;
                    }
                } catch (Exception ignore) {}
                Platform.runLater(() -> ErrorPanel.showError("Вход через ely.by не удался", ex.getMessage()));
            } finally {
                Platform.runLater(() -> accountBtn.setDisable(false));
            }
        }, "ely-login").start();
    }

    private static String waitForAuthCodeAndValidateState(String expectedState) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        final String[] codeHolder = new String[1];

        String callbackPath = "/callback";

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 47833), 0);
        server.createContext(callbackPath, exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            Map<String, String> params = parseQuery(query);
            String code = params.get("code");
            String state = params.get("state");

            boolean ok = expectedState != null && expectedState.equals(state) &&
                    code != null && code.matches("^[A-Za-z0-9\\-_.]+$");
            if (ok) {
                codeHolder[0] = code;
            }

            String html = buildCallbackHtml(ok ? "Авторизация успешно завершена" : "Ошибка авторизации");
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.getResponseHeaders().add("Cache-Control", "no-store");
            exchange.getResponseHeaders().add("X-Content-Type-Options", "nosniff");
            exchange.getResponseHeaders().add("Content-Security-Policy",
                    "default-src 'none'; style-src 'unsafe-inline'");
            exchange.sendResponseHeaders(ok ? 200 : 400, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
            server.stop(0);
            latch.countDown();
        });
        server.start();

        if (!latch.await(180, TimeUnit.SECONDS)) {
            server.stop(0);
            return null;
        }
        return codeHolder[0];
    }

    private static String buildCallbackHtml(String title) {
        String bgCss = "background: linear-gradient(180deg,#202938 0%,#141a26 100%);";
        try (InputStream is = BrowserAuth.class.getResourceAsStream("images/bg2.png")) {
            if (is != null) {
                String b64 = Base64.getEncoder().encodeToString(is.readAllBytes());
                bgCss = "background-image:url('data:image/png;base64," + b64 + "');" +
                        "background-size:cover;background-position:center;background-repeat:no-repeat;";
            }
        } catch (IOException e) {
            LogManager.getLogger().warning("Не удалось получить фон для страницы логина " + e.getMessage());
        }

        return "<!doctype html><html lang=\"ru\"><head><meta charset=\"utf-8\">" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
                "<title>" + title + "</title></head>" +
                "<body style=\"" + bgCss + "margin:0;height:100vh;display:flex;" +
                "align-items:center;justify-content:center;\">" +
                "<div style=\"font-family:-apple-system,BlinkMacSystemFont,'Segoe UI'," +
                "Roboto,Helvetica,Arial,sans-serif;font-size:32px;font-weight:700;" +
                "letter-spacing:.3px;color:#fff;text-align:center;" +
                "text-shadow:0 2px 12px rgba(0,0,0,.45)\">" + title +
                "<br><span style=\"font-weight:400;font-size:18px;opacity:.85\">" +
                "Это окно можно закрыть</span></div>" +
                "</body></html>";
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isBlank()) return map;
        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                try {
                    String k = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                    String v = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                    map.put(k, v);
                } catch (Exception ignored) {}
            }
        }
        return map;
    }

}
