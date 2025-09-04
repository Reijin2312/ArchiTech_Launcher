package org.architech.launcher.gui;

import com.sun.net.httpserver.HttpServer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.architech.launcher.auth.*;
import org.architech.launcher.utils.Utils;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.architech.launcher.MCLauncher.GAME_DIR;

public class LauncherUI {
    private final TextField usernameField;
    private final ProgressBar progressBar;
    private final Label progressLabel;
    private final Label percentLabel;
    private final Label timerLabel;
    private final Scene mainScene;
    private final Button accountBtn;
    private final ContextMenu accountMenu;
    private ImageView accountAvatar;
    private Account currentAccount;

    private long startTimeMs;
    private ScheduledExecutorService timerExecutor;

    private void rebuildAccountMenu() {
        accountMenu.getItems().clear();

        MenuItem miOffline = new MenuItem("Войти под простым ником…");
        miOffline.setOnAction(e -> showOfflineDialog());

        MenuItem miMs = new MenuItem("Войти через Microsoft (официально)...");
        miMs.setOnAction(e -> loginMicrosoftBrowser());

        MenuItem miEly = new MenuItem("Войти через Ely.by…");
        miEly.setOnAction(e -> loginElyBrowser());

        accountMenu.getItems().addAll(miOffline, miMs, miEly);
    }

    private void showOfflineDialog() {
        TextInputDialog d = new TextInputDialog(
                (currentAccount != null && currentAccount.type == AccountType.OFFLINE)
                        ? currentAccount.username
                        : (usernameField.getText() == null ? "Player" : usernameField.getText())
        );
        d.setTitle("Оффлайн вход");
        d.setHeaderText("Введите ник для оффлайн-режима");
        d.setContentText("Ник:");
        d.showAndWait().ifPresent(nick -> {
            if (nick.isBlank()) return;
            try {
                //Account a = AuthService.loginOffline(nick.trim());
                //setCurrentAccount(a); // оффлайн-акк с детерминированным UUID
            } catch (Exception ex) {
                showError("Не удалось установить оффлайн ник: " + ex.getMessage());
            }
        });
    }

    private void loginMicrosoftBrowser() {
        try {
            AuthService.MsAuthContext msCtx = new AuthService.MsAuthContext();
            String url = AuthService.buildMicrosoftLoginUrl(msCtx);
            openInBrowser(url);
        } catch (Exception e) {
            showError("Не удалось открыть авторизацию Microsoft: " + e.getMessage());
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
                    } catch (Exception ignored) {}
                    final Account accSilent = silent;
                    javafx.application.Platform.runLater(() -> setCurrentAccount(accSilent));
                    return;
                }

                String state = java.util.UUID.randomUUID().toString();
                String url = ElyOAuth.buildAuthorizeUrl(state, null);
                openInBrowser(url);

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
                    } catch (Exception e) {
                        final String em = e.getMessage();
                        javafx.application.Platform.runLater(() -> showError("Ошибка получения игровых параметров: " + em));
                    }
                }

                final Account acc = a;
                javafx.application.Platform.runLater(() -> setCurrentAccount(acc));

            } catch (Exception ex) {
                try {
                    Account fallback = AuthService.tryElySilentLogin();
                    if (fallback != null) {
                        javafx.application.Platform.runLater(() -> setCurrentAccount(fallback));
                        return;
                    }
                } catch (Exception ignore) {}
                final String msg = ex.getMessage();
                javafx.application.Platform.runLater(() -> showError("Ely.by вход не удался: " + msg));
            } finally {
                javafx.application.Platform.runLater(() -> accountBtn.setDisable(false));
            }
        }, "ely-login").start();
    }

    private void openInBrowser(String url) {
        try {
            java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
        } catch (Exception e) {
            showError("Не удалось открыть браузер: " + e.getMessage());
        }
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

            String html = buildCallbackHtml(ok ? "Авторизация успешно завершена"
                    : "Ошибка авторизации");
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
        try (InputStream is = getClass().getResourceAsStream("images/bg.png")) {
            if (is != null) {
                String b64 = Base64.getEncoder().encodeToString(is.readAllBytes());
                bgCss = "background-image:url('data:image/png;base64," + b64 + "');" +
                        "background-size:cover;background-position:center;background-repeat:no-repeat;";
            }
        } catch (IOException e) {
            e.printStackTrace();
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

        usernameField.setEditable(false);
        usernameField.setFocusTraversable(false);
        usernameField.setMouseTransparent(true);

        if (a == null || a.type == AccountType.OFFLINE) {
            //accountBtn.setText("Войти");
            //accountAvatar.setImage(null);
            if (a != null) usernameField.setText(a.username);
        } else {
           // accountBtn.setText(a.username != null ? a.username : "Аккаунт");
            usernameField.setText(a.username != null ? a.username : "");

            if (a.avatarUrl != null && !a.avatarUrl.isBlank()) {
                try {
                    Image img = HeadImage.forAccount(a, 20);
                    if (accountAvatar != null) accountAvatar.setImage(img);
                } catch (Exception ignored) {}
            } else {
                // fallback — всегда ставим картинку через HeadImage (учёт uuid/name)
                try {
                    Image img = HeadImage.forAccount(a, 20);
                    if (accountAvatar != null) accountAvatar.setImage(img);
                } catch (Exception ignored) {}
            }
        }
    }

    public LauncherUI(Stage stage, java.util.function.Consumer<String> launchHandler) {

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(20));

        // Левая панель управления
        usernameField = new TextField("Имя пользователя");
        usernameField.setStyle("-fx-background-color: #3c3f41; -fx-text-fill: white; -fx-border-color: #6b6b6b;");
        usernameField.setPrefColumnCount(14);

        accountBtn = new Button();
      //  accountAvatar = new ImageView();
      //  accountAvatar.setFitWidth(20);
      //  accountAvatar.setFitHeight(20);
      //  accountBtn.setGraphic(accountAvatar);
      //  accountBtn.setContentDisplay(ContentDisplay.LEFT);

        usernameField.setPrefHeight(30);
        accountBtn.setPrefHeight(usernameField.getPrefHeight());
        accountBtn.setPrefWidth(accountBtn.getPrefHeight());
        accountBtn.setStyle("-fx-background-color: #3c3f41; -fx-text-fill: white; -fx-font-size: 14px;");
        Label chevron = new Label("⌄");
        chevron.setStyle("-fx-text-fill: black; -fx-font-size: 14px;");
        accountBtn.setGraphic(chevron);
        accountBtn.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);

        accountMenu = new ContextMenu();
        accountBtn.setOnAction(e -> {
            rebuildAccountMenu();
            accountMenu.show(accountBtn, Side.BOTTOM, 0, 0);
        });

        setCurrentAccount(org.architech.launcher.auth.Auth.current());

        Button launchBtn = new Button("ЗАПУСТИТЬ");
        styleMainButton(launchBtn);
        launchBtn.setMaxWidth(Double.MAX_VALUE);
        launchBtn.setOnAction(e -> {
            startTimer();
            launchHandler.accept(usernameField.getText());
        });

        HBox buttons = new HBox(12);
        buttons.setAlignment(Pos.CENTER);

        Button openFolder = new Button("📂");
        styleSmallButton(openFolder);
        openFolder.setOnAction(e -> openGameDir());

        Button checkUpdates = new Button("⟳");
        styleSmallButton(checkUpdates);
        checkUpdates.setOnAction(e -> showInfo("Проверка обновлений пока не реализована"));

        Image telegramIcon = new Image(
                Objects.requireNonNull(getClass().getResource("/images/tg.png")).toExternalForm()
        );
        ImageView telegramView = new ImageView(telegramIcon);
        telegramView.setFitWidth(20);
        telegramView.setFitHeight(20);

        Button faqBtn = new Button();
        styleSmallButton(faqBtn);
        faqBtn.setGraphic(telegramView);
        faqBtn.setOnAction(e -> {
            try {
                java.awt.Desktop.getDesktop().browse(new URI("https://t.me/archi_tech_official"));
            } catch (Exception ex) {
                showError("Не удалось открыть Telegram");
            }
        });

        Button settingsBtn = new Button("⚙");
        styleSmallButton(settingsBtn);
        //settingsBtn.setOnAction(e -> openModsSettings(stage));


        buttons.getChildren().addAll(openFolder, checkUpdates, faqBtn, settingsBtn);

        HBox userRow = new HBox(8, usernameField, accountBtn);
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

        Label newsTitle = new Label("Новости проекта");
        newsTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: white;");
        HBox titleBox = new HBox(newsTitle);
        titleBox.setAlignment(Pos.CENTER);
        titleBox.setPadding(new Insets(0, 0, 6, 0));

        List<NewsItem> newsItems = List.of(
                new NewsItem("Заголовок 1", "https://example.com/img1.png"),
                new NewsItem("Заголовок 2", "https://example.com/img2.png"),
                new NewsItem("Заголовок 3", "https://example.com/img2.png"),
                new NewsItem("Заголовок 4", "https://example.com/img2.png"),
                new NewsItem("Заголовок 5", "https://example.com/img2.png"),
                new NewsItem("Заголовок 6", "https://example.com/img2.png"),
                new NewsItem("Заголовок 7", "https://example.com/img2.png")
        );

        ScrollPane newsScroll = buildNewsList(newsItems);

        StackPane newsWrapper = new StackPane(newsScroll);
        newsWrapper.setPadding(new Insets(6));
        newsWrapper.setStyle("-fx-background-color: rgba(30,30,30,0.55); -fx-background-radius: 8;");

        VBox centerBox = new VBox(10, titleBox, newsWrapper);
        centerBox.setAlignment(Pos.TOP_CENTER);
        VBox.setVgrow(newsWrapper, Priority.ALWAYS);

        root.setCenter(centerBox);
        BorderPane.setMargin(centerBox, new Insets(0, 0, 0, 20));

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(Double.MAX_VALUE);
        progressBar.setStyle("-fx-accent: #4caf50;");

        progressLabel = new Label("Ожидание...");
        progressLabel.setStyle("-fx-text-fill: white;");

        percentLabel = new Label("0%");
        percentLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");

        timerLabel = new Label("Времени прошло: 00:00:00");
        timerLabel.setStyle("-fx-text-fill: white;");

        newsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        newsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        newsScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        if (newsScroll.getContent() instanceof Region r) {
            r.setBackground(Background.EMPTY);
        }


        root.setCenter(centerBox);
        BorderPane.setMargin(centerBox, new Insets(0, 0, 0, 20));

        StackPane barWrapper = new StackPane(progressBar);
        barWrapper.setAlignment(Pos.CENTER);
        barWrapper.getChildren().add(percentLabel);

        BorderPane bottomLine = new BorderPane();
        bottomLine.setLeft(progressLabel);
        bottomLine.setRight(timerLabel);

        VBox bottom = new VBox(8, bottomLine, barWrapper);
        bottom.setPadding(new Insets(10, 0, 0, 0));
        root.setBottom(bottom);

        stage.setTitle("ArchiTech - лаунчер");
        stage.getIcons().add(
                new Image(Objects.requireNonNull(getClass().getResource("/images/icon.jpg")).toExternalForm())
        );

        mainScene = new Scene(root, 900, 560);
        mainScene.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/styles.css")).toExternalForm()
        );
        root.setStyle("-fx-background-color: linear-gradient(to bottom, #1e1e1e, #2a2a2a);");

        settingsBtn.setOnAction(e -> new MainSettingsUI(stage, mainScene).show());

        stage.setScene(mainScene);
        stage.show();
    }

    private void styleMainButton(Button btn) {
        btn.setStyle("-fx-background-color: #4caf50; -fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;");
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

    private void startTimer() {
        startTimeMs = System.currentTimeMillis();
        if (timerExecutor != null) timerExecutor.shutdownNow();
        timerExecutor = Executors.newSingleThreadScheduledExecutor();
        timerExecutor.scheduleAtFixedRate(() -> {
            long elapsed = System.currentTimeMillis() - startTimeMs;
            long sec = elapsed / 1000;
            long h = sec / 3600;
            long m = (sec % 3600) / 60;
            long s = sec % 60;
            String formatted = String.format("Времени прошло: %02d:%02d:%02d", h, m, s);
            Platform.runLater(() -> timerLabel.setText(formatted));
        }, 0, 1, TimeUnit.SECONDS);
    }

    public void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg);
        a.showAndWait();
    }

    public void showInfo(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg);
        a.showAndWait();
    }

    private void openGameDir() {
        try { java.awt.Desktop.getDesktop().open(GAME_DIR.toFile()); } catch (Exception e) { showError("Не удалось открыть папку"); }
    }

    private void openModsSettings(Stage stage) {
        new ModsUI(stage, mainScene).show();
    }

    private record NewsItem(String title, String imageUrl) {}

    private ScrollPane buildNewsList(List<NewsItem> items) {
        VBox list = new VBox(12);
        list.setPadding(new Insets(10));

        for (NewsItem n : items) {
            BorderPane card = new BorderPane();
            card.getStyleClass().add("news-card");
            card.setPrefWidth(640);
            card.setMaxWidth(640);
            card.setPrefWidth(32);
            card.setMaxHeight(32);

            ImageView img = new ImageView();
            try {
                Image im = new Image(n.imageUrl, 32, 32, true, true);
                img.setImage(im);
            } catch (Exception ignored) {}
            img.setFitWidth(64);
            img.setFitHeight(64);
            img.setPreserveRatio(true);
            card.setTop(img);

            Label title = new Label(n.title);
            title.getStyleClass().add("news-title");
            title.setWrapText(true);
            BorderPane.setMargin(title, new Insets(8,8,8,8));
            card.setBottom(title);

            list.getChildren().add(card);
        }

        ScrollPane sp = new ScrollPane(list);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        sp.getStyleClass().add("news-scroll");
        sp.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        if (sp.getContent() != null) sp.getContent().setStyle("-fx-background-color: transparent;");

        return sp;
    }
}
