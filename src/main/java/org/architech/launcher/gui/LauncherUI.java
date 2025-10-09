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
import org.architech.launcher.ArchiTechLauncher;
import org.architech.launcher.authentication.account.Account;
import org.architech.launcher.authentication.account.AccountManager;
import org.architech.launcher.authentication.auth.BrowserAuth;
import org.architech.launcher.discord.DiscordIntegration;
import org.architech.launcher.gui.player.PlayerPopup;
import org.architech.launcher.gui.player.head.HeadImage;
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
    private final Button accountBtn;
    private final ContextMenu accountMenu;
    private final ImageView headView;
    private final Label onlineLabelField = new Label("Онлайн: —");
    private final Label pingLabelField = new Label("Пинг: — ms");
    private final Circle pingDotField = new Circle(6);

    private static Scene mainScene;

    public LauncherUI(Stage stage, Consumer<String> launchHandler, Runnable updateHandler) {
        BorderPane root = new BorderPane();
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
        percentLabel.getStyleClass().add("percent-label");

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(Double.MAX_VALUE);
        progressBar.getStyleClass().add("progress-bar");

        progressLabel = new Label("Ожидание...");
        progressLabel.getStyleClass().add("progress-label");

        StackPane barWrapper = new StackPane(progressBar);
        barWrapper.setAlignment(Pos.CENTER);
        barWrapper.getChildren().add(percentLabel);

        BorderPane bottomLine = new BorderPane();
        bottomLine.setLeft(progressLabel);
        bottomLine.setRight(timerLabel);

        PlayerPopup.setup(leftInfo, onlineLabelField);

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
        signIn.setOnAction(e -> BrowserAuth.showOfflineDialog(AccountManager.getCurrentAccount(), usernameField, this::updateUsernameField));

        MenuItem signUp = new MenuItem("Регистрация…");
        signUp.setOnAction(e -> BrowserAuth.loginElyBrowser(accountBtn, this::updateUsernameField));

        accountMenu.getItems().addAll(signIn, signUp);
    }

    private void updateUsernameField(Account a) {
        if (a == null) {
            usernameField.setText("Player");
        } else {
            usernameField.setText(a.getUsername() != null ? a.getUsername() : "");
            Image img = HeadImage.forAccount(a, 20);
            headView.setImage(img);
        }
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