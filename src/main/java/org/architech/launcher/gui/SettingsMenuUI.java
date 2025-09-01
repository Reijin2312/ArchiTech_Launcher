package org.architech.launcher.gui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.util.Objects;

public class SettingsMenuUI {
    private final Stage stage;
    private final Scene parentScene; // LauncherUI main scene

    public SettingsMenuUI(Stage stage, Scene parentScene) {
        this.stage = stage;
        this.parentScene = parentScene;
    }

    public void show() {
        double w = (stage.getScene() != null ? stage.getScene().getWidth() : parentScene.getWidth());
        double h = (stage.getScene() != null ? stage.getScene().getHeight() : parentScene.getHeight());

        BorderPane root = new BorderPane();

        Label title = new Label("Меню настроек");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        BorderPane.setAlignment(title, Pos.CENTER);
        BorderPane.setMargin(title, new Insets(16,16,8,16));
        root.setTop(title);

        VBox menu = new VBox(15);
        menu.setPadding(new Insets(20));
        menu.setAlignment(Pos.CENTER);

        Button modsBtn = new Button("Управление модами");
        styleMainButton(modsBtn);
        modsBtn.setOnAction(e -> new ModsUI(stage, buildScene(root)).show());

        Button settingsBtn = new Button("Настройки лаунчера");
        styleMainButton(settingsBtn);
        settingsBtn.setOnAction(e -> new LauncherSettingsUI(stage, parentScene).show());

        Button backBtn = new Button("Назад в главное меню");
        styleMainButton(backBtn);
        backBtn.setOnAction(e -> stage.setScene(parentScene));

        menu.getChildren().addAll(modsBtn, settingsBtn, backBtn);

        root.setCenter(menu);

        Scene scene = new Scene(root, w, h);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/styles.css")).toExternalForm());
        stage.setScene(scene);
    }

    private Scene buildScene(BorderPane root) {
        return root.getScene();
    }

    private void styleMainButton(Button btn) {
        btn.setStyle("-fx-background-color: #4caf50; -fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;");
        btn.setPrefWidth(250);
        btn.setPrefHeight(40);
    }
}
