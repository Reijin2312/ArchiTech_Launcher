package org.architech.launcher.gui.settings;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.architech.launcher.ArchiTechLauncher;
import org.architech.launcher.discord.DiscordIntegration;
import org.architech.launcher.gui.BackgroundCache;
import org.architech.launcher.gui.LauncherUI;
import org.architech.launcher.gui.settings.tab.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.architech.launcher.ArchiTechLauncher.LAUNCHER_DIR;

public record MainSettingsUI(Stage stage, Scene parentScene) {

    private static Scene MAIN_SETTINGS_SCENE;

    public void show() {
        double w = (stage.getScene() != null ? stage.getScene().getWidth() : parentScene.getWidth());
        double h = (stage.getScene() != null ? stage.getScene().getHeight() : parentScene.getHeight());

        BorderPane root = new BorderPane();

        TabPane tabs = new TabPane();
        tabs.getStyleClass().add("tab-pane");
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        ModsTab modsUI = new ModsTab();
        ResourcePacksTab rpUI = new ResourcePacksTab();
        ShaderPacksTab shUI = new ShaderPacksTab();
        SettingsTab allSettingsUI = new SettingsTab(stage);

        Tab modsTab = new Tab("Моды", modsUI.createContent());
        Tab resourceTab = new Tab("Ресурспаки", rpUI.createContent());
        Tab shadersTab = new Tab("Шейдеры", shUI.createContent());
        Tab settingsTab = new Tab("Настройки", allSettingsUI.createContent());

        BackgroundsTab bgUI = new BackgroundsTab(stage);
        Tab backgroundsTab = new Tab("Фоны", bgUI.createContent());
        tabs.getTabs().addAll(modsTab, resourceTab, shadersTab, settingsTab, backgroundsTab);

        modsTab.setOnSelectionChanged(e -> { if (modsTab.isSelected()) modsUI.replayAnimations(); });
        resourceTab.setOnSelectionChanged(e -> { if (resourceTab.isSelected()) rpUI.replayAnimations(); });
        shadersTab.setOnSelectionChanged(e -> { if (shadersTab.isSelected()) shUI.replayAnimations(); });

        tabs.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab == modsTab) DiscordIntegration.update("В лаунчере", "Просматривает моды");
            else if (newTab == shadersTab) DiscordIntegration.update("В лаунчере", "Просматривает шейдер-паки");
            else if (newTab == resourceTab) DiscordIntegration.update("В лаунчере", "Просматривает ресурс-паки");
            else if (newTab == settingsTab) DiscordIntegration.update("В лаунчере", "Просматривает настройки");
            else DiscordIntegration.update("В лаунчере", "Просматривает настройки");
        });

        BorderPane wrapper = new BorderPane();
        wrapper.getStyleClass().add("settings-pane");
        wrapper.setCenter(tabs);

        root.setCenter(wrapper);

        Button backBtn = new Button("Назад");
        styleMainButton(backBtn);
        backBtn.setOnAction(e -> {
            DiscordIntegration.update("В лаунчере", "На главной странице");
            stage.setScene(parentScene);
            LauncherUI.replayNewsAnimations();
        });

        HBox bottom = new HBox(backBtn);
        bottom.setAlignment(Pos.CENTER_LEFT);
        bottom.setPadding(new Insets(12, 16, 16, 16));
        root.setBottom(bottom);

        Scene scene = new Scene(root, w, h);
        loadBackgroundFromConfig();
        MAIN_SETTINGS_SCENE = scene;

        scene.getStylesheets().addAll(
                Objects.requireNonNull(getClass().getResource("/css/base.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/css/layout.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/css/components.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/css/controls.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/css/tabs.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/css/scroll.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/css/backgrounds.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/css/theme-dark.css")).toExternalForm()
        );

        stage.setScene(scene);

        Platform.runLater(() -> {
            Tab cur = tabs.getSelectionModel().getSelectedItem();
            if (cur == modsTab) DiscordIntegration.update("В лаунчере", "Просматривает моды");
            else if (cur == shadersTab) DiscordIntegration.update("В лаунчере", "Просматривает шейдер-паки");
            else if (cur == resourceTab) DiscordIntegration.update("В лаунчере", "Просматривает ресурс-паки");
            else DiscordIntegration.update("В лаунчере", "Просматривает настройки");
        });
    }

    private void styleMainButton(Button btn) {
        btn.setStyle("-fx-background-color: #4caf50; -fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;");
    }

    public static void applyBackground(Path imgPath) {
        if (MAIN_SETTINGS_SCENE != null && MAIN_SETTINGS_SCENE.getRoot() instanceof Region region) {
            BackgroundCache.apply(imgPath, region);
        }
    }

    private void loadBackgroundFromConfig() {
        Path p = LAUNCHER_DIR.resolve("backgrounds").resolve(ArchiTechLauncher.LAUNCHER_BACKGROUND);
        if (Files.exists(p)) applyBackground(p);
    }
}
