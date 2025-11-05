package org.architech.launcher.gui.settings;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.architech.launcher.discord.DiscordIntegration;
import org.architech.launcher.gui.news.NewsList;
import org.architech.launcher.gui.settings.tab.*;

public record MainSettingsUI(Stage stage, Scene parentScene) {

    public void show() {
        Scene scene = (stage.getScene() != null) ? stage.getScene() : parentScene;
        Node oldRoot = scene.getRoot();

        BorderPane root = new BorderPane();
        TabPane tabs = new TabPane();
        tabs.getStyleClass().add("tab-pane");
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        ModsTab modsUI = new ModsTab();
        ResourcePacksTab rpUI = new ResourcePacksTab();
        ShaderPacksTab shUI = new ShaderPacksTab();
        SettingsTab allSettingsUI = new SettingsTab(stage);
        BackgroundsTab bgUI = new BackgroundsTab(stage);

        Tab modsTab      = new Tab("Моды",        modsUI.createContent());
        Tab resourceTab  = new Tab("Ресурспаки",  rpUI.createContent());
        Tab shadersTab   = new Tab("Шейдеры",     shUI.createContent());
        Tab settingsTab  = new Tab("Настройки",   allSettingsUI.createContent());
        Tab backgroundsTab = new Tab("Фоны",      bgUI.createContent());
        tabs.getTabs().addAll(modsTab, resourceTab, shadersTab, settingsTab, backgroundsTab);

        modsTab.setOnSelectionChanged(e -> { if (modsTab.isSelected()) modsUI.replayAnimations(); });
        resourceTab.setOnSelectionChanged(e -> { if (resourceTab.isSelected()) rpUI.replayAnimations(); });
        shadersTab.setOnSelectionChanged(e -> { if (shadersTab.isSelected()) shUI.replayAnimations(); });

        tabs.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n == modsTab)       DiscordIntegration.update("В лаунчере", "Просматривает моды");
            else if (n == shadersTab) DiscordIntegration.update("В лаунчере", "Просматривает шейдер-паки");
            else if (n == resourceTab) DiscordIntegration.update("В лаунчере", "Просматривает ресурс-паки");
            else                      DiscordIntegration.update("В лаунчере", "Просматривает настройки");
        });

        BorderPane wrapper = new BorderPane();
        wrapper.getStyleClass().add("settings-pane");
        wrapper.setCenter(tabs);
        root.setCenter(wrapper);

        Button backBtn = new Button("Назад");
        styleMainButton(backBtn);
        HBox bottom = new HBox(backBtn);
        bottom.setAlignment(Pos.CENTER_LEFT);
        bottom.setPadding(new Insets(12, 16, 16, 16));
        root.setBottom(bottom);

        if (oldRoot instanceof Region oldR) {
            root.setBackground(oldR.getBackground());
            if (scene.getFill() == null) scene.setFill(javafx.scene.paint.Color.web("#0e141c"));
        }

        scene.setRoot(root);

        backBtn.setOnAction(e -> {
            DiscordIntegration.update("В лаунчере", "На главной странице");
            scene.setRoot((Parent) oldRoot);
            NewsList.replayNewsAnimations();
        });

        Platform.runLater(() -> {
            Tab cur = tabs.getSelectionModel().getSelectedItem();
            if      (cur == modsTab)      DiscordIntegration.update("В лаунчере", "Просматривает моды");
            else if (cur == shadersTab)   DiscordIntegration.update("В лаунчере", "Просматривает шейдер-паки");
            else if (cur == resourceTab)  DiscordIntegration.update("В лаунчере", "Просматривает ресурс-паки");
            else                          DiscordIntegration.update("В лаунчере", "Просматривает настройки");
        });
    }

    private void styleMainButton(Button btn) {
        btn.setStyle("-fx-background-color: #4caf50; -fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;");
    }
}
