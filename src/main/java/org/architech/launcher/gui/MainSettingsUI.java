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
import java.util.Objects;

public record MainSettingsUI(Stage stage, Scene parentScene) {

    public void show() {
        double w = (stage.getScene() != null ? stage.getScene().getWidth() : parentScene.getWidth());
        double h = (stage.getScene() != null ? stage.getScene().getHeight() : parentScene.getHeight());

        BorderPane root = new BorderPane();

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        ModsUI modsUI = new ModsUI(stage, parentScene);
        AllSettingsUI allSettingsUI = new AllSettingsUI(stage);

        Tab modsTab = new Tab("Моды", modsUI.createContent());
        Tab settingsTab = new Tab("Настройки", allSettingsUI.createContent());

        tabs.getTabs().addAll(modsTab, settingsTab);
        root.setCenter(tabs);

        Button backBtn = new Button("Назад");
        styleMainButton(backBtn);
        backBtn.setOnAction(e -> stage.setScene(parentScene));
        HBox bottom = new HBox(backBtn);
        bottom.setAlignment(Pos.CENTER_LEFT);
        bottom.setPadding(new Insets(12, 16, 16, 16));
        root.setBottom(bottom);

        Scene scene = new Scene(root, w, h);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/styles.css")).toExternalForm());
        stage.setScene(scene);
    }

    private void styleMainButton(Button btn) {
        btn.setStyle("-fx-background-color: #4caf50; -fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;");
    }
}
