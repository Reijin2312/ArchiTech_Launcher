package org.architech.launcher.authentication.auth;

import com.sun.net.httpserver.HttpServer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.architech.launcher.ArchiTechLauncher;
import org.architech.launcher.authentication.account.Account;
import org.architech.launcher.authentication.account.AccountManager;
import org.architech.launcher.authentication.auth.ely_by.ElyOAuth;
import org.architech.launcher.authentication.requests.GameParams;
import org.architech.launcher.gui.LauncherUI;
import org.architech.launcher.gui.error.ErrorPanel;
import org.architech.launcher.utils.Utils;
import org.architech.launcher.utils.logging.LogManager;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class BrowserAuth {

    public static void showOfflineDialog(Account currentAccount, TextField usernameField, Consumer<Account> updateUsernameField) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle("Оффлайн вход");
        a.setHeaderText("Введите ник для оффлайн-режима");

        DialogPane pane = a.getDialogPane();
        pane.setStyle("-fx-background-color: #2b2b2b; -fx-font-size: 14px;");
        pane.lookup(".header-panel .label").setStyle("-fx-text-fill: white;");

        TextField input = new TextField(
                (currentAccount != null && currentAccount.getType() == AccountType.OFFLINE)
                        ? currentAccount.getUsername()
                        : (usernameField.getText() == null ? "Player" : usernameField.getText())
        );
        input.setStyle("-fx-control-inner-background: #1e1e1e; -fx-text-fill: white; -fx-border-color: #555;");
        input.setPrefWidth(300);

        VBox content = new VBox(10, new Label("Ник:"), input);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setStyle("-fx-background-color: #2b2b2b;");
        content.setPadding(new Insets(10));
        content.getChildren().getFirst().setStyle("-fx-text-fill: white;");

        pane.setContent(content);
        pane.setPrefWidth(400);
        pane.setMinWidth(400);
        pane.setMaxWidth(400);

        ButtonType okBtn = new ButtonType("Войти", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelBtn = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        a.getButtonTypes().setAll(okBtn, cancelBtn);

        Node okNode = a.getDialogPane().lookupButton(okBtn);
        if (okNode instanceof Button) {
            okNode.setStyle("-fx-font-size: 12px; -fx-padding: 4 12;");
        }
        Node cancelNode = a.getDialogPane().lookupButton(cancelBtn);
        if (cancelNode instanceof Button) {
            cancelNode.setStyle("-fx-font-size: 12px; -fx-padding: 4 12;");
        }

        Stage stage = (Stage) pane.getScene().getWindow();
        stage.getIcons().add(new Image(
                Objects.requireNonNull(LauncherUI.class.getResourceAsStream("/images/icon.jpg"))
        ));
        stage.setResizable(false);

        a.showAndWait().ifPresent(response -> {
            if (response == okBtn) {
                String nick = input.getText();
                if (nick.isBlank()) return;
                try {
                    Account acc = AuthService.offlineLogin(nick.trim());
                    AccountManager.setCurrentAccount(acc);
                    updateUsernameField.accept(acc);
                } catch (Exception ex) {
                    LogManager.getLogger().severe("Не удалось установить оффлайн ник " + ex.getMessage());
                    ErrorPanel.showError("Упс! Не удалось установить оффлайн ник :(", ex.getMessage());
                }
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
        ArchiTechLauncher.backgroundExecutor.submit(() -> {
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
                            AccountManager.setCurrentAccount(silent);
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

                if (code == null) return;

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
                            AccountManager.setCurrentAccount(a);
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
        });
    }

    private static String waitForAuthCodeAndValidateState(String expectedState) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> codeRef = new AtomicReference<>();
        final AtomicReference<String> errorRef = new AtomicReference<>();
        final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 47833), 0);
        final String callbackPath = "/callback";

        server.createContext(callbackPath, exchange -> {
            try {
                String query = exchange.getRequestURI().getRawQuery();
                Map<String, String> params = parseQuery(query);

                if (query != null && !query.isEmpty()) {
                    for (String pair : query.split("&")) {
                        int idx = pair.indexOf('=');
                        if (idx > 0) {
                            String k = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                            String v = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                            params.put(k, v);
                        } else {
                            params.put(URLDecoder.decode(pair, StandardCharsets.UTF_8), "");
                        }
                    }
                }

                String code = params.get("code");
                String state = params.get("state");

                byte[] respBytes;
                if (state == null || !state.equals(expectedState)) {
                    errorRef.set("invalid_state");
                    String html = buildCallbackHtml("Неверный state");
                    respBytes = html.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                    exchange.getResponseHeaders().add("Cache-Control", "no-store");
                    exchange.getResponseHeaders().add("X-Content-Type-Options", "nosniff");
                    exchange.getResponseHeaders().add("Content-Security-Policy",
                            "default-src 'none'; style-src 'unsafe-inline'");
                    exchange.sendResponseHeaders(400, respBytes.length);
                } else if (code == null || code.isEmpty()) {
                    errorRef.set("missing_code");
                    String html = buildCallbackHtml("Код авторизации не найден");
                    respBytes = html.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                    exchange.getResponseHeaders().add("Cache-Control", "no-store");
                    exchange.getResponseHeaders().add("X-Content-Type-Options", "nosniff");
                    exchange.getResponseHeaders().add("Content-Security-Policy",
                            "default-src 'none'; style-src 'unsafe-inline'");
                    exchange.sendResponseHeaders(400, respBytes.length);
                } else {
                    codeRef.set(code);
                    String html = buildCallbackHtml("Авторизация успешно завершена");
                    respBytes = html.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                    exchange.getResponseHeaders().add("Cache-Control", "no-store");
                    exchange.getResponseHeaders().add("X-Content-Type-Options", "nosniff");
                    exchange.getResponseHeaders().add("Content-Security-Policy",
                            "default-src 'none'; style-src 'unsafe-inline'");
                    exchange.sendResponseHeaders(200, respBytes.length);
                }

                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(respBytes);
                }
            } catch (Exception e) {
                errorRef.set("handler_exception");
                try {
                    String html = "<html><body><h1>Ошибка сервера</h1></body></html>";
                    byte[] resp = html.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(500, resp.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(resp);
                    }
                } catch (IOException ignored) {}
            } finally {
                exchange.close();
                try {
                    server.stop(0);
                } finally {
                    latch.countDown();
                }
            }
        });

        server.setExecutor(ArchiTechLauncher.backgroundExecutor);
        server.start();

        try {
            boolean signaled = latch.await(5, TimeUnit.SECONDS);
            if (!signaled) {
                server.stop(0);
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            server.stop(0);
            return null;
        } finally {
            server.stop(0);
        }

        return codeRef.get();
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
