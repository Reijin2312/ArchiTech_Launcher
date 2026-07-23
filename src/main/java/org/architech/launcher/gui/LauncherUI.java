// SPDX-FileCopyrightText: 2026 Raijin2312
// SPDX-License-Identifier: GPL-3.0-only

package org.architech.launcher.gui;

import static org.architech.launcher.ArchiTechLauncher.LAUNCHER_DIR;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.architech.launcher.ArchiTechLauncher;
import org.architech.launcher.authentication.account.Account;
import org.architech.launcher.authentication.account.AccountManager;
import org.architech.launcher.authentication.auth.BrowserAuth;
import org.architech.launcher.discord.DiscordIntegration;
import org.architech.launcher.gui.download.DownloadStatusPanel;
import org.architech.launcher.gui.news.NewsList;
import org.architech.launcher.gui.player.AvatarImage;
import org.architech.launcher.gui.player.PlayersPopup;
import org.architech.launcher.gui.player.skin.MinecraftSkinView;
import org.architech.launcher.gui.settings.MainSettingsUI;
import org.architech.launcher.gui.timer.Timer;
import org.architech.launcher.managment.DownloadSnapshot;
import org.architech.launcher.utils.Utils;
import org.architech.launcher.utils.serverinfo.ArchiTechServerInfo;

public class LauncherUI {
    private final Button launchBtn;
    private final TextField usernameField;
    private final DownloadStatusPanel downloadStatusPanel;

    private TranslateTransition shineAnim;

    private final Button accountBtn;
    private final ContextMenu accountMenu;
    private boolean accountMenuHiding;

    private final ImageView headView;
    private final MinecraftSkinView skinView;
    private final Label onlineLabelField = new Label("Онлайн: —");
    private final Label pingLabelField = new Label("Пинг: — ms");
    private final Circle pingDotField = new Circle(6);

    private static Scene mainScene;
    private static Region MAIN_ROOT;

    public LauncherUI(
            Stage stage,
            Consumer<String> launchHandler,
            Runnable updateHandler) {
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

        skinView = new MinecraftSkinView();

        StackPane usernameStack =
                new StackPane(usernameField, headView);
        StackPane.setAlignment(
                headView,
                Pos.CENTER_RIGHT);
        StackPane.setMargin(
                headView,
                new Insets(0, 8, 0, 0));

        accountMenu = new ContextMenu();
        accountMenu.setOnShowing(
                event -> {
                    accountMenuHiding = false;

                    Platform.runLater(
                            () -> {
                                if (accountMenu.getSkin() == null
                                        || accountMenu
                                                        .getSkin()
                                                        .getNode()
                                                == null) {
                                    return;
                                }

                                var node =
                                        accountMenu
                                                .getSkin()
                                                .getNode();

                                node.setOpacity(0);
                                node.setScaleX(0.96);
                                node.setScaleY(0.96);

                                ScaleTransition scale =
                                        new ScaleTransition(
                                                Duration.millis(140),
                                                node);
                                scale.setFromX(0.96);
                                scale.setFromY(0.96);
                                scale.setToX(1.0);
                                scale.setToY(1.0);

                                FadeTransition fade =
                                        new FadeTransition(
                                                Duration.millis(140),
                                                node);
                                fade.setFromValue(0);
                                fade.setToValue(1);

                                new ParallelTransition(
                                                scale,
                                                fade)
                                        .play();
                            });
                });

        accountMenu.setOnAutoHide(
                event -> {
                    if (accountMenuHiding) {
                        return;
                    }

                    event.consume();
                    animateAccountMenuHide();
                });

        Label chevron = new Label("⌄");
        chevron.getStyleClass().add("chevron");

        accountBtn = new Button();
        accountBtn.setPrefHeight(
                usernameField.getPrefHeight());
        accountBtn.setPrefWidth(
                accountBtn.getPrefHeight());
        accountBtn.getStyleClass().add("account-btn");
        accountBtn.setGraphic(chevron);
        accountBtn.setContentDisplay(
                ContentDisplay.GRAPHIC_ONLY);
        accountBtn.setOnAction(
                event -> {
                    if (accountMenu.isShowing()) {
                        animateAccountMenuHide();
                    } else {
                        rebuildAccountMenu();
                        accountMenu.show(
                                accountBtn,
                                Side.BOTTOM,
                                0,
                                0);
                    }
                });

        updateUsernameField(
                AccountManager.getCurrentAccount());

        Label timerLabel =
                new Label("Времени прошло: 00:00:00");
        timerLabel.getStyleClass().add("timer-label");
        Timer.timerLabel = timerLabel;

        launchBtn = new Button("ЗАПУСТИТЬ");
        launchBtn.getStyleClass().add("launch-btn");
        launchBtn.setMaxWidth(Double.MAX_VALUE);
        launchBtn.setOnAction(
                event -> {
                    Timer.startTimer();
                    launchHandler.accept(
                            usernameField.getText());
                });

        Pane launchShine = new Pane();
        launchShine
                .getStyleClass()
                .add("launch-shine");
        launchShine.setMouseTransparent(true);
        launchShine.setPrefWidth(96);
        launchShine.setPrefHeight(30);
        launchShine.setMinHeight(30);
        launchShine.setMaxHeight(30);

        StackPane launchBtnWrap =
                new StackPane(
                        launchBtn,
                        launchShine);
        launchBtnWrap.setMaxWidth(
                Double.MAX_VALUE);
        launchBtnWrap.setAlignment(
                Pos.CENTER);

        Rectangle launchClip = new Rectangle();
        launchClip.setArcWidth(12);
        launchClip.setArcHeight(12);
        launchClip
                .widthProperty()
                .bind(
                        launchBtnWrap
                                .widthProperty());
        launchClip
                .heightProperty()
                .bind(
                        launchBtnWrap
                                .heightProperty());
        launchBtnWrap.setClip(launchClip);

        startLaunchShineAnimation(
                launchShine,
                launchBtnWrap);

        HBox buttons = new HBox(12);
        buttons.setAlignment(Pos.CENTER);

        Button openFolder = new Button("📂");
        openFolder
                .getStyleClass()
                .add("small-button");
        openFolder.setOnAction(
                event -> Utils.openGameDir());

        Button checkUpdates = new Button("⟳");
        checkUpdates
                .getStyleClass()
                .add("small-button");
        checkUpdates.setOnAction(
                event -> updateHandler.run());

        Image telegramIcon =
                new Image(
                        Objects.requireNonNull(
                                        getClass()
                                                .getResource(
                                                        "/images/tg.png"))
                                .toExternalForm());

        ImageView telegramView =
                new ImageView(telegramIcon);
        telegramView.setFitWidth(20);
        telegramView.setFitHeight(20);

        Button faqBtn = new Button();
        faqBtn.getStyleClass().add("small-button");
        faqBtn.setGraphic(telegramView);
        faqBtn.setOnAction(
                event ->
                        Utils.openInBrowser(
                                "https://t.me/archi_tech_official"));

        Button settingsBtn = new Button("⚙");
        settingsBtn
                .getStyleClass()
                .add("small-button");
        settingsBtn.setOnAction(
                event ->
                        new MainSettingsUI(
                                        stage,
                                        mainScene)
                                .show());

        buttons.getChildren()
                .addAll(
                        openFolder,
                        checkUpdates,
                        faqBtn,
                        settingsBtn);

        HBox userRow =
                new HBox(
                        8,
                        usernameStack,
                        accountBtn);
        userRow.setAlignment(Pos.CENTER_LEFT);

        VBox controls =
                new VBox(
                        15,
                        userRow,
                        launchBtnWrap,
                        buttons);
        controls.setAlignment(Pos.CENTER);

        BorderPane left = new BorderPane();
        skinView.setPrefWidth(250);
        skinView.setPrefHeight(290);
        BorderPane.setMargin(skinView, new Insets(0, 0, 16, 0));
        BorderPane.setAlignment(skinView, Pos.TOP_CENTER);
        left.setTop(skinView);
        left.setBottom(controls);
        root.setLeft(left);

        buttons.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(
                buttons,
                Priority.NEVER);

        for (Button button :
                new Button[] {
                    openFolder,
                    checkUpdates,
                    faqBtn,
                    settingsBtn
                }) {
            button.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(
                    button,
                    Priority.ALWAYS);
        }

        ArchiTechLauncher.scheduledExecutor
                .scheduleAtFixedRate(
                        () -> {
                            try {
                                ArchiTechServerInfo.ServerStatus
                                        status =
                                                ArchiTechServerInfo
                                                        .fetchStatus(
                                                                ArchiTechLauncher
                                                                        .MINESERVER_URL,
                                                                25565,
                                                                3000);

                                Platform.runLater(
                                        () -> {
                                            onlineLabelField
                                                    .setText(
                                                            "Онлайн: "
                                                                    + status
                                                                            .online()
                                                                    + "/"
                                                                    + status
                                                                            .max());

                                            pingLabelField
                                                    .setText(
                                                            "Пинг: "
                                                                    + status
                                                                            .pingMs()
                                                                    + " ms");

                                            PlayersPopup
                                                            .latestOnlinePlayers =
                                                    status.sample()
                                                                    == null
                                                            ? Collections
                                                                    .emptyList()
                                                            : List.copyOf(
                                                                    status
                                                                            .sample());

                                            if (status.pingMs()
                                                    <= 100) {
                                                pingDotField
                                                        .setFill(
                                                                Color.GREEN);
                                            } else if (status
                                                            .pingMs()
                                                    <= 250) {
                                                pingDotField
                                                        .setFill(
                                                                Color.GOLD);
                                            } else {
                                                pingDotField
                                                        .setFill(
                                                                Color.ORANGERED);
                                            }
                                        });
                            } catch (Throwable error) {
                                Platform.runLater(
                                        () -> {
                                            onlineLabelField
                                                    .setText(
                                                            "Онлайн: —");
                                            pingLabelField
                                                    .setText(
                                                            "Пинг: —");
                                            pingDotField
                                                    .setFill(
                                                            Color.GRAY);
                                        });
                            }
                        },
                        0,
                        10,
                        TimeUnit.SECONDS);

        Image newsLogo =
                new Image(
                        Objects.requireNonNull(
                                        getClass()
                                                .getResource(
                                                        "/images/logo.png"))
                                .toExternalForm());

        ImageView newsLogoView =
                new ImageView(newsLogo);
        newsLogoView.setPreserveRatio(true);
        newsLogoView.setFitHeight(100);
        newsLogoView.setSmooth(true);

        HBox titleBox =
                new HBox(newsLogoView);
        titleBox.setAlignment(Pos.CENTER);

        ScrollPane newsScroll =
                NewsList.buildNewsList();
        newsScroll.setFitToWidth(true);
        newsScroll.setHbarPolicy(
                ScrollPane.ScrollBarPolicy.NEVER);

        VBox newsContainer = new VBox(8);
        newsContainer.setPadding(new Insets(10));
        newsContainer.setAlignment(
                Pos.TOP_CENTER);
        newsContainer.setFillWidth(true);
        newsContainer
                .getChildren()
                .add(titleBox);

        NewsList.replayNewsAnimations();

        Region sepTop = new Region();
        sepTop.setPrefHeight(2);
        sepTop.setPrefWidth(220);
        sepTop.getStyleClass().add("sep-top");

        HBox sepTopBox = new HBox(sepTop);
        sepTopBox.setAlignment(Pos.CENTER);
        newsContainer
                .getChildren()
                .add(sepTopBox);

        HBox infoBar = new HBox(12);
        infoBar.setAlignment(Pos.CENTER);
        infoBar.setPadding(
                new Insets(6, 12, 6, 12));
        infoBar.getStyleClass().add("info-bar");

        HBox leftInfo = new HBox(6);
        leftInfo.setAlignment(Pos.CENTER);

        Label personIcon = new Label("👤");
        personIcon.getStyleClass().add("person-icon");
        onlineLabelField
                .getStyleClass()
                .add("online-label-field");

        leftInfo.getChildren()
                .addAll(
                        personIcon,
                        onlineLabelField);

        HBox rightInfo = new HBox(6);
        rightInfo.setAlignment(Pos.CENTER);

        pingLabelField
                .getStyleClass()
                .add("ping-label-field");

        rightInfo.getChildren()
                .addAll(
                        pingDotField,
                        pingLabelField);

        startPingAnimation();

        infoBar.getChildren()
                .addAll(
                        leftInfo,
                        new Label("|"),
                        rightInfo);

        newsContainer
                .getChildren()
                .add(infoBar);

        Region sepBottom = new Region();
        sepBottom.setPrefHeight(2);
        sepBottom.setPrefWidth(180);
        sepBottom
                .getStyleClass()
                .add("sep-bottom");

        HBox sepBottomBox =
                new HBox(sepBottom);
        sepBottomBox.setAlignment(Pos.CENTER);

        newsContainer
                .getChildren()
                .add(sepBottomBox);

        newsScroll.setMaxHeight(
                Region.USE_COMPUTED_SIZE);
        newsContainer
                .getChildren()
                .add(newsScroll);

        StackPane newsWrapper =
                new StackPane(newsContainer);
        newsWrapper.setPadding(new Insets(6));
        newsWrapper
                .getStyleClass()
                .add("news-wrapper");

        VBox centerBox =
                new VBox(
                        10,
                        newsWrapper);
        centerBox.setAlignment(
                Pos.TOP_CENTER);

        VBox.setVgrow(
                newsWrapper,
                Priority.ALWAYS);

        root.setCenter(centerBox);
        BorderPane.setMargin(
                centerBox,
                new Insets(0, 0, 0, 20));

        downloadStatusPanel =
                new DownloadStatusPanel(
                        timerLabel,
                        ArchiTechLauncher::toggleDownloadPause);
        downloadStatusPanel.updateProgress("Ожидание...", 1.0);

        StackPane downloadPanelSlot =
                new StackPane(downloadStatusPanel);
        downloadPanelSlot.setMaxWidth(Double.MAX_VALUE);
        downloadPanelSlot.setAlignment(Pos.CENTER);
        BorderPane.setMargin(
                downloadPanelSlot,
                new Insets(12, 0, 0, 0));

        // A bottom child of BorderPane may otherwise keep only its computed
        // width. The slot always occupies the full content area and gives the
        // status panel a stable width instead of compressing it around the
        // pause button.
        downloadStatusPanel
                .prefWidthProperty()
                .bind(downloadPanelSlot.widthProperty());

        PlayersPopup.setup(
                leftInfo,
                onlineLabelField);

        root.setBottom(downloadPanelSlot);

        root.setStyle(
                "-fx-background-color: "
                        + "linear-gradient(to bottom, #1e1e1e, #2a2a2a);");

        stage.setTitle("ArchiTech - лаунчер");
        stage.getIcons()
                .add(
                        new Image(
                                Objects.requireNonNull(
                                                getClass()
                                                        .getResource(
                                                                "/images/icon.jpg"))
                                        .toExternalForm()));

        mainScene = new Scene(root, 900, 560);
        mainScene.getStylesheets()
                .addAll(
                        Objects.requireNonNull(
                                        getClass()
                                                .getResource(
                                                        "/css/MainScreen.css"))
                                .toExternalForm(),
                        Objects.requireNonNull(
                                        getClass()
                                                .getResource(
                                                        "/css/layout.css"))
                                .toExternalForm(),
                        Objects.requireNonNull(
                                        getClass()
                                                .getResource(
                                                        "/css/components.css"))
                                .toExternalForm(),
                        Objects.requireNonNull(
                                        getClass()
                                                .getResource(
                                                        "/css/controls.css"))
                                .toExternalForm(),
                        Objects.requireNonNull(
                                        getClass()
                                                .getResource(
                                                        "/css/Tabs.css"))
                                .toExternalForm(),
                        Objects.requireNonNull(
                                        getClass()
                                                .getResource(
                                                        "/css/scroll.css"))
                                .toExternalForm(),
                        Objects.requireNonNull(
                                        getClass()
                                                .getResource(
                                                        "/css/backgrounds.css"))
                                .toExternalForm(),
                        Objects.requireNonNull(
                                        getClass()
                                                .getResource(
                                                        "/css/theme-dark.css"))
                                .toExternalForm(),
                        Objects.requireNonNull(
                                        getClass()
                                                .getResource(
                                                        "/css/download-panel.css"))
                                .toExternalForm());

        stage.setScene(mainScene);
        stage.show();

        loadBackgroundFromConfig();

        DiscordIntegration.update(
                "В лаунчере",
                "На главной странице");
    }

    private void rebuildAccountMenu() {
        accountMenu.getItems().clear();

        MenuItem signIn =
                new MenuItem("Войти…");
        signIn.setOnAction(
                event ->
                        BrowserAuth.openLogin(
                                accountBtn,
                                this::updateUsernameField));

        MenuItem signUp =
                new MenuItem("Регистрация…");
        signUp.setOnAction(
                event ->
                        BrowserAuth.openRegister(
                                accountBtn,
                                this::updateUsernameField));

        accountMenu.getItems()
                .addAll(
                        signIn,
                        signUp);
    }

    private void updateUsernameField(Account account) {
        if (account == null) {
            usernameField.setText("Player");
            headView.setImage(null);
            skinView.setSkinUrl(null);
            return;
        }

        usernameField.setText(
                account.getUsername() == null
                        ? ""
                        : account.getUsername());

        Image image =
                AvatarImage.forAccount(
                        account,
                        20);
        headView.setImage(image);
        skinView.setSkinUrl(resolveSkinUrl(account.getSkinUrl()));
    }

    private static String resolveSkinUrl(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*$")) {
            return trimmed;
        }

        String base = ArchiTechLauncher.BACKEND_URL;
        boolean baseHasSlash = base.endsWith("/");
        boolean pathHasSlash = trimmed.startsWith("/");
        if (baseHasSlash && pathHasSlash) {
            return base + trimmed.substring(1);
        }
        if (!baseHasSlash && !pathHasSlash) {
            return base + "/" + trimmed;
        }
        return base + trimmed;
    }

    public void refreshAccountDisplay() {
        updateUsernameField(
                AccountManager.getCurrentAccount());
    }

    private void startPingAnimation() {
        try {
            ScaleTransition transition =
                    new ScaleTransition(
                            Duration.millis(1400),
                            pingDotField);
            transition.setFromX(1.0);
            transition.setFromY(1.0);
            transition.setToX(1.25);
            transition.setToY(1.25);
            transition.setAutoReverse(true);
            transition.setCycleCount(
                    ScaleTransition.INDEFINITE);
            transition.play();
        } catch (Exception ignored) {
        }
    }

    private void startShineAnimation(
            Pane shine,
            Region wrapper) {
        try {
            shineAnim =
                    new TranslateTransition(
                            Duration.millis(2600),
                            shine);
            shineAnim.setCycleCount(
                    Animation.INDEFINITE);
            shineAnim.setAutoReverse(false);
            shineAnim.setInterpolator(
                    Interpolator.LINEAR);

            Runnable refresh =
                    () -> {
                        double width = wrapper.getWidth();
                        double shineWidth =
                                shine.getLayoutBounds()
                                        .getWidth();

                        if (shineWidth <= 1) {
                            shineWidth =
                                    shine.prefWidth(-1);
                        }
                        if (shineWidth <= 1) {
                            shineWidth = 90;
                        }

                        double overshoot =
                                shineWidth + 12;
                        double from =
                                -width / 2 - overshoot;
                        double to =
                                width / 2 + overshoot;

                        shineAnim.stop();
                        shineAnim.setFromX(from);
                        shineAnim.setToX(to);
                        shine.setTranslateX(from);
                        shineAnim.playFromStart();
                    };

            wrapper.widthProperty()
                    .addListener(
                            (observable, oldValue, newValue) ->
                                    refresh.run());

            Platform.runLater(refresh);
        } catch (Exception ignored) {
        }
    }

    private void startLaunchShineAnimation(
            Pane shine,
            Region wrapper) {
        try {
            TranslateTransition launchShineAnim =
                    new TranslateTransition(
                            Duration.millis(3600),
                            shine);
            launchShineAnim.setCycleCount(
                    Animation.INDEFINITE);
            launchShineAnim.setAutoReverse(false);
            launchShineAnim.setInterpolator(
                    Interpolator.LINEAR);

            Runnable refresh =
                    () -> {
                        double width = wrapper.getWidth();
                        double shineWidth =
                                shine.getLayoutBounds()
                                        .getWidth();

                        if (shineWidth <= 1) {
                            shineWidth =
                                    shine.prefWidth(-1);
                        }
                        if (shineWidth <= 1) {
                            shineWidth = 96;
                        }

                        double overshoot =
                                shineWidth + 16;
                        double from =
                                -width / 2 - overshoot;
                        double to =
                                width / 2 + overshoot;

                        launchShineAnim.stop();
                        launchShineAnim.setFromX(from);
                        launchShineAnim.setToX(to);
                        shine.setTranslateX(from);
                        launchShineAnim.playFromStart();
                    };

            wrapper.widthProperty()
                    .addListener(
                            (observable, oldValue, newValue) ->
                                    refresh.run());

            Platform.runLater(refresh);
        } catch (Exception ignored) {
        }
    }

    private void animateAccountMenuHide() {
        var skin = accountMenu.getSkin();

        if (skin == null
                || skin.getNode() == null) {
            accountMenu.hide();
            return;
        }

        if (accountMenuHiding) {
            return;
        }

        accountMenuHiding = true;

        var node = skin.getNode();

        FadeTransition fade =
                new FadeTransition(
                        Duration.millis(140),
                        node);
        fade.setFromValue(1.0);
        fade.setToValue(0.0);

        ScaleTransition scale =
                new ScaleTransition(
                        Duration.millis(140),
                        node);
        scale.setFromX(node.getScaleX());
        scale.setFromY(node.getScaleY());
        scale.setToX(0.96);
        scale.setToY(0.96);

        ParallelTransition parallel =
                new ParallelTransition(
                        fade,
                        scale);

        parallel.setOnFinished(
                event -> {
                    accountMenu.hide();
                    node.setOpacity(1);
                    node.setScaleX(1);
                    node.setScaleY(1);
                    accountMenuHiding = false;
                });

        parallel.play();
    }

    public void updateProgress(
            String text,
            double progress01) {
        downloadStatusPanel.updateProgress(text, progress01);
    }

    public void updateDownloadSnapshot(DownloadSnapshot snapshot) {
        downloadStatusPanel.applySnapshot(snapshot);
    }

    public void setLaunchingState(boolean launching) {
        Platform.runLater(
                () -> {
                    launchBtn.setDisable(false);

                    if (launching) {
                        launchBtn.setStyle(
                                "-fx-background-color: #939393;"
                                        + " -fx-text-fill: white;"
                                        + " -fx-font-size: 14px;"
                                        + " -fx-font-weight: bold;");
                        launchBtn.setText("ОТМЕНА");
                    } else {
                        launchBtn.setStyle(
                                "-fx-background-color: #4caf50;"
                                        + " -fx-text-fill: white;"
                                        + " -fx-font-size: 14px;"
                                        + " -fx-font-weight: bold;");
                        launchBtn.setText("ЗАПУСТИТЬ");
                    }
                });
    }

    public void setGameRunningState(boolean running) {
        Platform.runLater(
                () -> {
                    launchBtn.setDisable(false);

                    if (running) {
                        launchBtn.setStyle(
                                "-fx-background-color: #d32f2f;"
                                        + " -fx-text-fill: white;"
                                        + " -fx-font-size: 14px;"
                                        + " -fx-font-weight: bold;");
                        launchBtn.setText("ЗАВЕРШИТЬ");
                    } else {
                        launchBtn.setStyle(
                                "-fx-background-color: #4caf50;"
                                        + " -fx-text-fill: white;"
                                        + " -fx-font-size: 14px;"
                                        + " -fx-font-weight: bold;");
                        launchBtn.setText("ЗАПУСТИТЬ");
                    }
                });
    }

    public void setGameStoppingState() {
        Platform.runLater(
                () -> {
                    launchBtn.setDisable(true);
                    launchBtn.setStyle(
                            "-fx-background-color: #8b1a1a;"
                                    + " -fx-text-fill: white;"
                                    + " -fx-font-size: 14px;"
                                    + " -fx-font-weight: bold;");
                    launchBtn.setText("ЗАВЕРШЕНИЕ...");
                });
    }

    public void setDownloadPauseAvailable(boolean available) {
        downloadStatusPanel.setDownloadActive(available);
    }

    public void setDownloadPaused(boolean paused) {
        downloadStatusPanel.setPaused(paused);
    }

    public static void applyBackground(Path imgPath) {
        if (mainScene == null) {
            return;
        }

        var current = mainScene.getRoot();

        if (current instanceof Region region) {
            BackgroundCache.apply(
                    imgPath,
                    region);
        }

        if (MAIN_ROOT != null
                && current != MAIN_ROOT) {
            BackgroundCache.apply(
                    imgPath,
                    MAIN_ROOT);
        }
    }

    private void loadBackgroundFromConfig() {
        Path path =
                LAUNCHER_DIR
                        .resolve("backgrounds")
                        .resolve(
                                ArchiTechLauncher
                                        .LAUNCHER_BACKGROUND);

        if (Files.exists(path)) {
            applyBackground(path);
        }
    }
}
