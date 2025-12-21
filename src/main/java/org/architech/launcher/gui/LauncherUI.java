package org.architech.launcher.gui;

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
import javafx.animation.ScaleTransition;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.animation.Animation;
import javafx.util.Duration;
import org.architech.launcher.ArchiTechLauncher;
import org.architech.launcher.authentication.account.Account;
import org.architech.launcher.authentication.account.AccountManager;
import org.architech.launcher.authentication.auth.BrowserAuth;
import org.architech.launcher.discord.DiscordIntegration;
import org.architech.launcher.gui.player.PlayersPopup;
import org.architech.launcher.gui.player.AvatarImage;
import org.architech.launcher.gui.news.NewsList;
import org.architech.launcher.gui.settings.MainSettingsUI;
import org.architech.launcher.gui.timer.Timer;
import org.architech.launcher.utils.serverinfo.ArchiTechServerInfo;
import org.architech.launcher.utils.Utils;
import javafx.scene.paint.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.architech.launcher.ArchiTechLauncher.LAUNCHER_DIR;

public class LauncherUI {
    private final Button launchBtn;
    private final TextField usernameField;
    private final ProgressBar progressBar;
    private final Label progressLabel;
    private final Label percentLabel;
    private TranslateTransition shineAnim;
    private final Button accountBtn;
    private final ContextMenu accountMenu;
    private boolean accountMenuHiding = false;
    private final ImageView headView;
    private final Label onlineLabelField = new Label("Онлайн: —");
    private final Label pingLabelField = new Label("Пинг: — ms");
    private final Circle pingDotField = new Circle(6);

    private static Scene mainScene;
    private static Region MAIN_ROOT;

    public LauncherUI(Stage stage, Consumer<String> launchHandler, Runnable updateHandler) {
        BorderPane root = new BorderPane();
        MAIN_ROOT = root;
        root.setPadding(new Insets(20));

        usernameField = new TextField("Имя пользователя");
        usernameField.getStyleClass().add("username-field");
        usernameField.setPrefColumnCount(14);
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
        accountMenu.setOnShowing(e -> {
            accountMenuHiding = false;
            Platform.runLater(() -> {
                if (accountMenu.getSkin() == null || accountMenu.getSkin().getNode() == null) return;
                var node = accountMenu.getSkin().getNode();
                node.setOpacity(0);
                node.setScaleX(0.96);
                node.setScaleY(0.96);
                ScaleTransition st = new ScaleTransition(Duration.millis(140), node);
                st.setFromX(0.96);
                st.setFromY(0.96);
                st.setToX(1.0);
                st.setToY(1.0);
                FadeTransition ft = new FadeTransition(Duration.millis(140), node);
                ft.setFromValue(0);
                ft.setToValue(1);
                new ParallelTransition(st, ft).play();
            });
        });
        accountMenu.setOnAutoHide(e -> {
            if (accountMenuHiding) return;
            e.consume();
            animateAccountMenuHide();
        });

        Label chevron = new Label("⌄");
        chevron.getStyleClass().add("chevron");

        accountBtn = new Button();
        accountBtn.setPrefHeight(usernameField.getPrefHeight());
        accountBtn.setPrefWidth(accountBtn.getPrefHeight());
        accountBtn.getStyleClass().add("account-btn");
        accountBtn.setGraphic(chevron);
        accountBtn.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        accountBtn.setOnAction(e -> {
            if (accountMenu.isShowing()) {
                animateAccountMenuHide();
            } else {
                rebuildAccountMenu();
                accountMenu.show(accountBtn, Side.BOTTOM, 0, 0);
            }
        });

        updateUsernameField(AccountManager.getCurrentAccount());

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
        openFolder.getStyleClass().add("small-button");
        openFolder.setOnAction(e -> Utils.openGameDir());

        Button checkUpdates = new Button("⟳");
        checkUpdates.getStyleClass().add("small-button");
        checkUpdates.setOnAction(e -> updateHandler.run());

        Image telegramIcon = new Image(Objects.requireNonNull(getClass().getResource("/images/tg.png")).toExternalForm());
        ImageView telegramView = new ImageView(telegramIcon);
        telegramView.setFitWidth(20);
        telegramView.setFitHeight(20);

        Button faqBtn = new Button();
        faqBtn.getStyleClass().add("small-button");
        faqBtn.setGraphic(telegramView);
        faqBtn.setOnAction(e -> Utils.openInBrowser("https://t.me/archi_tech_official"));

        Button settingsBtn = new Button("⚙");
        settingsBtn.getStyleClass().add("small-button");
        settingsBtn.setOnAction(e -> new MainSettingsUI(stage, mainScene).show());

        buttons.getChildren().addAll(openFolder, checkUpdates, faqBtn, settingsBtn);

        HBox userRow = new HBox(8, usernameStack, accountBtn);
        userRow.setAlignment(Pos.CENTER_LEFT);

        VBox controls = new VBox(15, userRow, launchBtn, buttons);
        controls.setAlignment(Pos.CENTER);
        BorderPane left = new BorderPane();

        //MinecraftSkinView view = new MinecraftSkinView();
        //view.setPrefHeight(300);
        //view.setMaxWidth(Double.MAX_VALUE);
        //BorderPane.setMargin(view, new Insets(0, 0, 16, 0));
        //left.setTop(view);

        //var acc = AccountManager.getCurrentAccount();
        //if (acc != null && acc.getUsername() != null) {
        //    view.setSkinUrl(BACKEND_URL + acc.getSkinUrl());
        //} else {
        //    view.setSkinUrl("Steve");
        //}

       // assert acc != null;
       // System.out.println(acc.getSkinUrl());

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
                    PlayersPopup.latestOnlinePlayers = (s.sample() == null) ? Collections.emptyList() : List.copyOf(s.sample());
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
        startPingAnimation();

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
        percentLabel.getStyleClass().add("percent-label");

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(Double.MAX_VALUE);
        progressBar.getStyleClass().add("progress-bar");

        progressLabel = new Label("Ожидание...");
        progressLabel.getStyleClass().add("progress-label");
        updateProgress("Ожидание...", 1);

        StackPane barWrapper = new StackPane(progressBar);
        barWrapper.setAlignment(Pos.CENTER);
        Pane shine = new Pane();
        shine.getStyleClass().add("progress-shine");
        shine.setMouseTransparent(true);
        shine.setPrefWidth(90);
        shine.setPrefHeight(12);
        shine.setMinHeight(12);
        shine.setMaxHeight(12);
        barWrapper.getChildren().addAll(shine, percentLabel);
        shine.toFront();
        startShineAnimation(shine, barWrapper);

        BorderPane bottomLine = new BorderPane();
        bottomLine.setLeft(progressLabel);
        bottomLine.setRight(timerLabel);

        PlayersPopup.setup(leftInfo, onlineLabelField);

        VBox bottom = new VBox(8, bottomLine, barWrapper);
        bottom.setPadding(new Insets(10, 0, 0, 0));
        root.setBottom(bottom);
        root.setStyle("-fx-background-color: linear-gradient(to bottom, #1e1e1e, #2a2a2a);");

        stage.setTitle("ArchiTech - лаунчер");
        stage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResource("/images/icon.jpg")).toExternalForm()));

        mainScene = new Scene(root, 900, 560);
        mainScene.getStylesheets().addAll(
                Objects.requireNonNull(getClass().getResource("/css/MainScreen.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/css/layout.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/css/components.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/css/controls.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/css/Tabs.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/css/scroll.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/css/backgrounds.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/css/theme-dark.css")).toExternalForm()
        );

        stage.setScene(mainScene);
        stage.show();
        loadBackgroundFromConfig();
        DiscordIntegration.update("В лаунчере", "На главной странице");
    }

    private void rebuildAccountMenu() {
        accountMenu.getItems().clear();
        MenuItem signIn = new MenuItem("Войти…");
        signIn.setOnAction(e -> BrowserAuth.openLogin(accountBtn, this::updateUsernameField)
        );
        MenuItem signUp = new MenuItem("Регистрация…");
        signUp.setOnAction(e -> BrowserAuth.openRegister(accountBtn, this::updateUsernameField)
        );
        accountMenu.getItems().addAll(signIn, signUp);
    }

    private void updateUsernameField(Account a) {
        if (a == null) {
            usernameField.setText("Player");
        } else {
            usernameField.setText(a.getUsername() != null ? a.getUsername() : "");
            Image img = AvatarImage.forAccount(a, 20);
            headView.setImage(img);
        }
    }

    public void refreshAccountDisplay() {
        updateUsernameField(AccountManager.getCurrentAccount());
    }

    private void startPingAnimation() {
        try {
            ScaleTransition st = new ScaleTransition(Duration.millis(1400), pingDotField);
            st.setFromX(1.0); st.setFromY(1.0);
            st.setToX(1.25);  st.setToY(1.25);
            st.setAutoReverse(true);
            st.setCycleCount(ScaleTransition.INDEFINITE);
            st.play();
        } catch (Exception ignored) {}
    }

    private void startShineAnimation(Pane shine, Region wrapper) {
        try {
            shineAnim = new TranslateTransition(Duration.millis(1600), shine);
            shineAnim.setCycleCount(Animation.INDEFINITE);
            shineAnim.setAutoReverse(false);

            Runnable refresh = () -> {
                double w = wrapper.getWidth();
                double span = Math.max(180, w + 40);
                double from = -span / 2;
                double to = span / 2;
                shineAnim.stop();
                shineAnim.setFromX(from);
                shineAnim.setToX(to);
                shine.setTranslateX(from);
                shineAnim.playFromStart();
            };

            wrapper.widthProperty().addListener((obs, o, n) -> refresh.run());
            Platform.runLater(refresh);
        } catch (Exception ignored) {}
    }

    private void animateAccountMenuHide() {
        var skin = accountMenu.getSkin();
        if (skin == null || skin.getNode() == null) {
            accountMenu.hide();
            return;
        }
        if (accountMenuHiding) return;
        accountMenuHiding = true;
        var node = skin.getNode();
        FadeTransition ft = new FadeTransition(Duration.millis(140), node);
        ft.setFromValue(1.0);
        ft.setToValue(0.0);
        ScaleTransition st = new ScaleTransition(Duration.millis(140), node);
        st.setFromX(node.getScaleX());
        st.setFromY(node.getScaleY());
        st.setToX(0.96);
        st.setToY(0.96);
        ParallelTransition pt = new ParallelTransition(ft, st);
        pt.setOnFinished(ev -> {
            accountMenu.hide();
            node.setOpacity(1);
            node.setScaleX(1);
            node.setScaleY(1);
            accountMenuHiding = false;
        });
        pt.play();
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
        if (mainScene == null) return;
        var current = mainScene.getRoot();
        if (current instanceof Region r1) BackgroundCache.apply(imgPath, r1);
        if (MAIN_ROOT != null && current != MAIN_ROOT) BackgroundCache.apply(imgPath, MAIN_ROOT);
    }

    private void loadBackgroundFromConfig() {
        Path p = LAUNCHER_DIR.resolve("backgrounds").resolve(ArchiTechLauncher.LAUNCHER_BACKGROUND);
        if (Files.exists(p)) applyBackground(p);
    }

}

