package org.architech.launcher.gui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.architech.launcher.gui.tab.ModsTab;
import org.architech.launcher.gui.tab.SettingsTab;
import org.architech.launcher.gui.tab.ResourcePacksTab;
import org.architech.launcher.gui.tab.ShaderPacksTab;

import java.util.Objects;

public record MainSettingsUI(Stage stage, Scene parentScene) {

    public void show() {
        double w = (stage.getScene() != null ? stage.getScene().getWidth() : parentScene.getWidth());
        double h = (stage.getScene() != null ? stage.getScene().getHeight() : parentScene.getHeight());

        BorderPane root = new BorderPane();

        TabPane tabs = new TabPane();
        tabs.getStyleClass().add("tab-pane");
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        ModsTab modsUI = new ModsTab();
        ResourcePacksTab rpUI = new ResourcePacksTab(stage, parentScene);
        ShaderPacksTab shUI = new ShaderPacksTab(stage, parentScene);
        SettingsTab allSettingsUI = new SettingsTab(stage);

        Tab modsTab = new Tab("Моды", modsUI.createContent());
        Tab resourceTab = new Tab("Ресурспаки", rpUI.createContent());
        Tab shadersTab = new Tab("Шейдеры", shUI.createContent());
        Tab settingsTab = new Tab("Настройки", allSettingsUI.createContent());

        tabs.getTabs().addAll(modsTab, resourceTab, shadersTab, settingsTab);

        BorderPane wrapper = new BorderPane();
        wrapper.getStyleClass().add("settings-pane");
        wrapper.setCenter(tabs);

        root.setCenter(wrapper);

        Button backBtn = new Button("Назад");
        styleMainButton(backBtn);
        backBtn.setOnAction(e -> stage.setScene(parentScene));
        HBox bottom = new HBox(backBtn);
        bottom.setAlignment(Pos.CENTER_LEFT);
        bottom.setPadding(new Insets(12, 16, 16, 16));
        root.setBottom(bottom);

        Scene scene = new Scene(root, w, h);
        scene.getStylesheets().addAll(
                Objects.requireNonNull(getClass().getResource("/css/base.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/css/layout.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/css/components.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/css/controls.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/css/tabs.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/css/scroll.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/css/theme-dark.css")).toExternalForm()
        );
        stage.setScene(scene);
    }

    private void styleMainButton(Button btn) {
        btn.setStyle("-fx-background-color: #4caf50; -fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;");
    }
}
