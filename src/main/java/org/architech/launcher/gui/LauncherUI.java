package org.architech.launcher.gui;

import com.sun.net.httpserver.HttpServer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import org.architech.launcher.ArchiTechLauncher;
import org.architech.launcher.authentication.account.Account;
import org.architech.launcher.authentication.account.AccountType;
import org.architech.launcher.authentication.auth.Auth;
import org.architech.launcher.authentication.auth.AuthService;
import org.architech.launcher.authentication.ely_by.ElyOAuth;
import org.architech.launcher.authentication.requests.GameParams;
import org.architech.launcher.discord.DiscordIntegration;
import org.architech.launcher.gui.error.ErrorPanel;
import org.architech.launcher.gui.player.PlayerPopup;
import org.architech.launcher.gui.player.head.HeadImage;
import org.architech.launcher.gui.news.NewsList;
import org.architech.launcher.gui.settings.MainSettingsUI;
import org.architech.launcher.gui.timer.Timer;
import org.architech.launcher.utils.logging.LogManager;
import org.architech.launcher.utils.serverinfo.ArchiTechServerInfo;
import org.architech.launcher.utils.Utils;
import javafx.scene.paint.Color;
import java.awt.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import static org.architech.launcher.ArchiTechLauncher.LAUNCHER_DIR;

public class LauncherUI {
    private final Button launchBtn;
    private final TextField usernameField;
    private final ProgressBar progressBar;
    private final Label progressLabel;
    private final Label percentLabel;
    private static Scene mainScene;
    private final Button accountBtn;
    private final ContextMenu accountMenu;
    private final ImageView headView;
    private final Label onlineLabelField = new Label("Онлайн: —");
    private final Label pingLabelField = new Label("Пинг: — ms");
    private final Circle pingDotField = new Circle(6);

    private Account currentAccount;

    public LauncherUI(Stage stage, Consumer<String> launchHandler, Runnable updateHandler) {

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(20));

        usernameField = new TextField("Имя пользователя");
        usernameField.getStyleClass().add("username-field");
        usernameField.setPrefColumnCount(14);
        usernameField.setPrefHeight(30);
        usernameField.setEditable(false);
        usernameField.setFocusTraversable(false);
        usernameField.setMouseTransparent(true);

        headView = new ImageView();
        headView.setFitWidth(20);
        headView.setFitHeight(20);
        headView.setPreserveRatio(true);
        headView.setSmooth(true);
        headView.setMouseTransparent(true);

        StackPane usernameStack = new StackPane(usernameField, headView);
        StackPane.setAlignment(headView, Pos.CENTER_RIGHT);
        StackPane.setMargin(headView, new Insets(0, 8, 0, 0));

        accountMenu = new ContextMenu();

        Label chevron = new Label("⌄");
        chevron.getStyleClass().add("chevron");

        accountBtn = new Button();
        accountBtn.setPrefHeight(usernameField.getPrefHeight());
        accountBtn.setPrefWidth(accountBtn.getPrefHeight());
        accountBtn.getStyleClass().add("account-btn");
        accountBtn.setGraphic(chevron);
        accountBtn.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        accountBtn.setOnAction(e -> {
            rebuildAccountMenu();
            accountMenu.show(accountBtn, Side.BOTTOM, 0, 0);
        });

        setCurrentAccount(Auth.current());

        Label timerLabel = new Label("Времени прошло: 00:00:00");
        timerLabel.getStyleClass().add("timer-label");
        org.architech.launcher.gui.timer.Timer.timerLabel = timerLabel;

        launchBtn = new Button("ЗАПУСТИТЬ");
        launchBtn.getStyleClass().add("launch-btn");
        launchBtn.setMaxWidth(Double.MAX_VALUE);
        launchBtn.setOnAction(e -> {
            Timer.startTimer();
            launchHandler.accept(usernameField.getText());
        });

        HBox buttons = new HBox(12);
        buttons.setAlignment(Pos.CENTER);

        Button openFolder = new Button("📂");
        styleSmallButton(openFolder);
        openFolder.setOnAction(e -> Utils.openGameDir());

        Button checkUpdates = new Button("⟳");
        styleSmallButton(checkUpdates);
        checkUpdates.setOnAction(e -> updateHandler.run());

        Image telegramIcon = new Image(Objects.requireNonNull(getClass().getResource("/images/tg.png")).toExternalForm());
        ImageView telegramView = new ImageView(telegramIcon);
        telegramView.setFitWidth(20);
        telegramView.setFitHeight(20);

        Button faqBtn = new Button();
        styleSmallButton(faqBtn);
        faqBtn.setGraphic(telegramView);
        faqBtn.setOnAction(e -> {
            try {
                URI uri = new URI("https://t.me/archi_tech_official");

                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(uri);
                } else {
                    String os = System.getProperty("os.name").toLowerCase();
                    Runtime rt = Runtime.getRuntime();

                    if (os.contains("win")) {
                        rt.exec(new String[]{"rundll32", "url.dll,FileProtocolHandler", uri.toString()});
                    } else if (os.contains("mac")) {
                        rt.exec(new String[]{"open", uri.toString()});
                    } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
                        rt.exec(new String[]{"xdg-open", uri.toString()});
                    } else {
                        throw new UnsupportedOperationException("Неизвестная ОС: " + os);
                    }
                }
            } catch (Exception ex) {
                LogManager.getLogger().severe("Не удалось открыть Telegram: " + ex.getMessage());
                ErrorPanel.showError("Упс! Не удалось открыть Telegram :(", ex.getMessage());
            }
        });

        Button settingsBtn = new Button("⚙");
        styleSmallButton(settingsBtn);

        buttons.getChildren().addAll(openFolder, checkUpdates, faqBtn, settingsBtn);

        HBox userRow = new HBox(8, usernameStack, accountBtn);
        userRow.setAlignment(Pos.CENTER_LEFT);

        VBox controls = new VBox(15, userRow, launchBtn, buttons);
        controls.setAlignment(Pos.CENTER);
        BorderPane left = new BorderPane();
        left.setBottom(controls);
        root.setLeft(left);

        buttons.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(buttons, Priority.NEVER);

        for (Button b : new Button[]{openFolder, checkUpdates, faqBtn, settingsBtn}) {
            b.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(b, Priority.ALWAYS);
        }

        ArchiTechLauncher.scheduledExecutor.scheduleAtFixedRate(() -> {
            try {
                ArchiTechServerInfo.ServerStatus s = ArchiTechServerInfo.fetchStatus("architech.mc-world.xyz", 25565, 3000);
                Platform.runLater(() -> {
                    onlineLabelField.setText("Онлайн: " + s.online() + "/" + s.max());
                    pingLabelField.setText("Пинг: " + s.pingMs() + " ms");
                    PlayerPopup.latestOnlinePlayers = (s.sample() == null) ? Collections.emptyList() : List.copyOf(s.sample());
                    if (s.pingMs() <= 100) pingDotField.setFill(Color.GREEN);
                    else if (s.pingMs() <= 250) pingDotField.setFill(Color.GOLD);
                    else pingDotField.setFill(Color.ORANGERED);
                });
            } catch (Throwable t) {
                Platform.runLater(() -> {
                    onlineLabelField.setText("Онлайн: —");
                    pingLabelField.setText("Пинг: —");
                    pingDotField.setFill(Color.GRAY);
                });
            }
        }, 0, 10, TimeUnit.SECONDS);

        Label newsTitle = new Label("Новости проекта");
        newsTitle.getStyleClass().add("main-news-title");
        HBox titleBox = new HBox(newsTitle);
        titleBox.setAlignment(Pos.CENTER);

        ScrollPane newsScroll = NewsList.buildNewsList();
        newsScroll.setFitToWidth(true);
        newsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        VBox newsContainer = new VBox(8);
        newsContainer.setPadding(new Insets(10));
        newsContainer.setAlignment(Pos.TOP_CENTER);
        newsContainer.setFillWidth(true);
        newsContainer.getChildren().add(titleBox);

        NewsList.replayNewsAnimations();

        Region sepTop = new Region();
        sepTop.setPrefHeight(2);
        sepTop.setPrefWidth(220);
        sepTop.getStyleClass().add("sep-top");

        HBox sepTopBox = new HBox(sepTop);
        sepTopBox.setAlignment(Pos.CENTER);
        newsContainer.getChildren().add(sepTopBox);

        HBox infoBar = new HBox(12);
        infoBar.setAlignment(Pos.CENTER);
        infoBar.setPadding(new Insets(6, 12, 6, 12));
        infoBar.getStyleClass().add("info-bar");

        HBox leftInfo = new HBox(6);
        leftInfo.setAlignment(Pos.CENTER);

        Label personIcon = new Label("\uD83D\uDC64"); // 👤
        personIcon.getStyleClass().add("person-icon");

        onlineLabelField.getStyleClass().add("online-label-field");
        leftInfo.getChildren().addAll(personIcon, onlineLabelField);

        HBox rightInfo = new HBox(6);
        rightInfo.setAlignment(Pos.CENTER);
        pingLabelField.getStyleClass().add("ping-label-field");
        rightInfo.getChildren().addAll(pingDotField, pingLabelField);

        infoBar.getChildren().addAll(leftInfo, new Label("|"), rightInfo);
        newsContainer.getChildren().add(infoBar);

        Region sepBottom = new Region();
        sepBottom.setPrefHeight(2);
        sepBottom.setPrefWidth(180);
        sepBottom.getStyleClass().add("sep-bottom");

        HBox sepBottomBox = new HBox(sepBottom);
        sepBottomBox.setAlignment(Pos.CENTER);
        newsContainer.getChildren().add(sepBottomBox);

        newsScroll.setMaxHeight(Region.USE_COMPUTED_SIZE);
        newsContainer.getChildren().add(newsScroll);

        StackPane newsWrapper = new StackPane(newsContainer);
        newsWrapper.setPadding(new Insets(6));
        newsWrapper.getStyleClass().add("news-wrapper");

        VBox centerBox = new VBox(10, newsWrapper);
        centerBox.setAlignment(Pos.TOP_CENTER);
        VBox.setVgrow(newsWrapper, Priority.ALWAYS);

        root.setCenter(centerBox);
        BorderPane.setMargin(centerBox, new Insets(0, 0, 0, 20));

        percentLabel = new Label("0%");
        percentLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(Double.MAX_VALUE);
        progressBar.setStyle("-fx-accent: #4caf50;");

        StackPane barWrapper = new StackPane(progressBar);
        barWrapper.setAlignment(Pos.CENTER);
        barWrapper.getChildren().add(percentLabel);

        progressLabel = new Label("Ожидание...");
        progressLabel.setStyle("-fx-text-fill: white;");

        BorderPane bottomLine = new BorderPane();
        bottomLine.setLeft(progressLabel);
        bottomLine.setRight(timerLabel);

        PlayerPopup.setup(leftInfo, onlineLabelField);

        VBox bottom = new VBox(8, bottomLine, barWrapper);
        bottom.setPadding(new Insets(10, 0, 0, 0));
        root.setBottom(bottom);

        stage.setTitle("ArchiTech - лаунчер");
        stage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResource("/images/icon.jpg")).toExternalForm()));

        mainScene = new Scene(root, 900, 560);
        mainScene.getStylesheets().addAll(
                Objects.requireNonNull(getClass().getResource("/css/base.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/css/layout.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/css/components.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/css/controls.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/css/tabs.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/css/scroll.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/css/backgrounds.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/css/theme-dark.css")).toExternalForm()
        );

        root.setStyle("-fx-background-color: linear-gradient(to bottom, #1e1e1e, #2a2a2a);");

        settingsBtn.setOnAction(e -> new MainSettingsUI(stage, mainScene).show());

        DiscordIntegration.update("В лаунчере", "На главной странице");
        stage.setScene(mainScene);
        stage.show();
        loadBackgroundFromConfig();
    }

    private void rebuildAccountMenu() {
        accountMenu.getItems().clear();

        MenuItem miOffline = new MenuItem("Войти под оффлайн-ником…");
        miOffline.setOnAction(e -> showOfflineDialog());

        MenuItem miMs = new MenuItem("Войти через Microsoft…");
        miMs.setOnAction(e -> loginMicrosoftBrowser());

        MenuItem miEly = new MenuItem("Войти через Ely.by…");
        miEly.setOnAction(e -> loginElyBrowser());

        accountMenu.getItems().addAll(miOffline, miEly, miMs);
    }

    private void showOfflineDialog() {
        TextInputDialog d = new TextInputDialog((currentAccount != null && currentAccount.type == AccountType.OFFLINE)
                ? currentAccount.username
                : (usernameField.getText() == null ? "Player" : usernameField.getText()));
        d.setTitle("Оффлайн вход");
        d.setHeaderText("Введите ник для оффлайн-режима");
        d.setContentText("Ник:");

        d.showAndWait().ifPresent(nick -> {
            if (nick.isBlank()) return;
            try {
                Account a = AuthService.offlineLogin(nick.trim());
                setCurrentAccount(a);
                Auth.set(a);
            } catch (Exception ex) {
                LogManager.getLogger().severe("Не удалось установить оффлайн ник " + ex.getMessage());
                ErrorPanel.showError("Упс! Не удалось установить оффлайн ник :(", ex.getMessage());
            }
        });
    }

    private void loginMicrosoftBrowser() {
        try {
            AuthService.MsAuthContext msCtx = new AuthService.MsAuthContext();
            String url = AuthService.buildMicrosoftLoginUrl(msCtx);
            Utils.openInBrowser(url);
        } catch (Exception e) {
            ErrorPanel.showError("Не удалось открыть авторизацию Microsoft", e.getMessage());
        }
    }

    private void loginElyBrowser() {
        accountBtn.setDisable(true);
        new Thread(() -> {
            try {
                Account silent = AuthService.tryElySilentLogin();
                if (silent != null) {
                    try {
                        GameParams params = AuthService.getGameParams(silent.launcherToken);
                        if (params != null && params.selectedProfile != null) {
                            if (params.selectedProfile.name != null && !params.selectedProfile.name.isBlank())
                                silent.username = params.selectedProfile.name;
                            if (params.selectedProfile.uuid != null && !params.selectedProfile.uuid.isBlank())
                                silent.uuid = params.selectedProfile.uuid;
                            if (params.accessToken != null && !params.accessToken.isBlank())
                                silent.accessToken = params.accessToken;
                            if (params.selectedProfile.name != null && !params.selectedProfile.name.isBlank())
                                silent.skinUrl = "http://skinsystem.ely.by/skins/" + params.selectedProfile.name + ".png";
                            Auth.set(silent);
                        }
                    } catch (Exception ex) {
                        LogManager.getLogger().severe("Ошибка получения параметров аккаунта: " + ex.getMessage());
                        Platform.runLater(() -> ErrorPanel.showError("Упс! :( Ошибка получения параметров аккаунта", ex.getMessage()));
                    }
                    final Account accSilent = silent;
                    Platform.runLater(() -> setCurrentAccount(accSilent));
                    return;
                }

                String state = UUID.randomUUID().toString();
                String url = ElyOAuth.buildAuthorizeUrl(state, null);
                Utils.openInBrowser(url);

                String code = waitForAuthCodeAndValidateState(state);
                Account a = AuthService.finishElyLoginWithCode(code);

                if (a.type == AccountType.ELY) {
                    try {
                        GameParams params = AuthService.getGameParams(a.launcherToken);
                        if (params != null && params.selectedProfile != null) {
                            if (params.selectedProfile.name != null && !params.selectedProfile.name.isBlank())
                                a.username = params.selectedProfile.name;
                            if (params.selectedProfile.uuid != null && !params.selectedProfile.uuid.isBlank())
                                a.uuid = params.selectedProfile.uuid;
                            if (params.accessToken != null && !params.accessToken.isBlank())
                                a.accessToken = params.accessToken;
                            if (params.selectedProfile.name != null && !params.selectedProfile.name.isBlank())
                                a.skinUrl = "http://skinsystem.ely.by/skins/" + params.selectedProfile.name + ".png";
                            Auth.set(a);
                        }
                    } catch (Exception ex) {
                        LogManager.getLogger().severe("Ошибка получения параметров аккаунта: " + ex.getMessage());
                        Platform.runLater(() -> ErrorPanel.showError("Упс! :( Ошибка получения параметров аккаунта", ex.getMessage()));
                    }
                }

                final Account acc = a;
                Platform.runLater(() -> setCurrentAccount(acc));

            } catch (Exception ex) {
                try {
                    Account fallback = AuthService.tryElySilentLogin();
                    if (fallback != null) {
                        Platform.runLater(() -> setCurrentAccount(fallback));
                        return;
                    }
                } catch (Exception ignore) {}
                Platform.runLater(() -> ErrorPanel.showError("Вход через ely.by не удался", ex.getMessage()));
            } finally {
                Platform.runLater(() -> accountBtn.setDisable(false));
            }
        }, "ely-login").start();
    }

    private String waitForAuthCodeAndValidateState(String expectedState) throws Exception {
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

    private String buildCallbackHtml(String title) {
        String bgCss = "background: linear-gradient(180deg,#202938 0%,#141a26 100%);";
        try (InputStream is = getClass().getResourceAsStream("images/bg2.png")) {
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

    private Map<String, String> parseQuery(String query) {
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

    private void setCurrentAccount(Account a) {
        this.currentAccount = a;
        if (a == null || a.type == AccountType.OFFLINE) {
            if (a != null) usernameField.setText(a.username);
        } else {
            usernameField.setText(a.username != null ? a.username : "");
            Image img = HeadImage.forAccount(a, 20);
            headView.setImage(img);
        }
    }

    private void styleSmallButton(Button btn) {
        btn.setStyle("-fx-background-color: #3c3f41; -fx-text-fill: white; -fx-font-size: 14px;");
        btn.setPrefWidth(40);
        btn.setPrefHeight(30);
    }

    public void updateProgress(String text, double progress01) {
        Platform.runLater(() -> {
            progressLabel.setText(text);
            if (progress01 >= 0) {
                int percent = (int) (progress01 * 100);
                percentLabel.setText(percent + "%");
            }
            progressBar.setProgress(progress01 < 0 ? ProgressIndicator.INDETERMINATE_PROGRESS : Utils.clamp01(progress01));
        });
    }

    public void setLaunchingState(boolean launching) {
        Platform.runLater(() -> {
            if (launching) {
                launchBtn.setStyle("-fx-background-color: #939393; -fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;");
                launchBtn.setText("ОТМЕНА");
            } else {
                launchBtn.setStyle("-fx-background-color: #4caf50; -fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;");
                launchBtn.setText("ЗАПУСТИТЬ");
            }
        });
    }

    public static void applyBackground(Path imgPath) {
        if (mainScene != null && mainScene.getRoot() instanceof Region region) {
            BackgroundCache.apply(imgPath, region);
        }
    }

    private void loadBackgroundFromConfig() {
        Path p = LAUNCHER_DIR.resolve("backgrounds").resolve(ArchiTechLauncher.LAUNCHER_BACKGROUND);
        if (Files.exists(p)) applyBackground(p);
    }

}