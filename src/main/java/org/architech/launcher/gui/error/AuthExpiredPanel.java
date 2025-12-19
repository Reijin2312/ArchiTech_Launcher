package org.architech.launcher.gui.error;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.architech.launcher.authentication.account.AccountManager;
import org.architech.launcher.authentication.auth.BrowserAuth;
import org.architech.launcher.gui.LauncherUI;

import java.util.Objects;

public class AuthExpiredPanel {

    /**
     * Показывает уведомление о том, что сессия истекла и требуется повторный вход
     */
    public static void showAuthExpiredDialog(Runnable onRelogin) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Сессия истекла");
        alert.setHeaderText("Требуется повторный вход");
        alert.setContentText("Ваша сессия истекла. Пожалуйста, войдите в аккаунт заново.");

        DialogPane pane = alert.getDialogPane();
        pane.setStyle("-fx-background-color: #2b2b2b; -fx-font-size: 14px;");
        pane.lookup(".content.label").setStyle("-fx-text-fill: white;");
        pane.lookup(".header-panel").setStyle("-fx-background-color: #3a3a3a;");

        // Добавляем иконку с замком
        ImageView iconView = new ImageView();
        try {
            Image lockIcon = new Image(
                    Objects.requireNonNull(LauncherUI.class.getResourceAsStream("/images/icon.jpg"))
            );
            iconView.setImage(lockIcon);
            iconView.setFitWidth(48);
            iconView.setFitHeight(48);
            iconView.setPreserveRatio(true);
            alert.setGraphic(iconView);
        } catch (Exception ignored) {}

        // Дополнительная информация
        Label infoLabel = new Label("Это могло произойти по следующим причинам:");
        infoLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-weight: bold;");
        
        Label reason1 = new Label("• Токен авторизации истек");
        reason1.setStyle("-fx-text-fill: #aaaaaa;");
        
        Label reason2 = new Label("• Вы вышли из аккаунта на другом устройстве");
        reason2.setStyle("-fx-text-fill: #aaaaaa;");
        
        Label reason3 = new Label("• Пароль был изменен");
        reason3.setStyle("-fx-text-fill: #aaaaaa;");

        Label tipLabel = new Label("\n💡 После входа все данные будут восстановлены");
        tipLabel.setStyle("-fx-text-fill: #4caf50; -fx-font-style: italic;");

        VBox infoBox = new VBox(8, infoLabel, reason1, reason2, reason3, tipLabel);
        infoBox.setAlignment(Pos.CENTER_LEFT);
        infoBox.setStyle("-fx-background-color: #2b2b2b;");
        infoBox.setPadding(new Insets(10));

        pane.setExpandableContent(infoBox);
        pane.setExpanded(false);

        // Кастомные кнопки
        ButtonType loginButton = new ButtonType("Войти", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Позже", ButtonBar.ButtonData.CANCEL_CLOSE);
        
        alert.getButtonTypes().setAll(loginButton, cancelButton);

        // Стилизация кнопок
        Node loginNode = pane.lookupButton(loginButton);
        if (loginNode instanceof Button btn) {
            btn.setStyle("-fx-background-color: #4caf50; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 16;");
        }

        Node cancelNode = pane.lookupButton(cancelButton);
        if (cancelNode instanceof Button btn) {
            btn.setStyle("-fx-background-color: #666666; -fx-text-fill: white; -fx-padding: 8 16;");
        }

        pane.setPrefWidth(450);
        pane.setMinWidth(450);

        Stage stage = (Stage) pane.getScene().getWindow();
        try {
            stage.getIcons().add(new Image(
                    Objects.requireNonNull(LauncherUI.class.getResourceAsStream("/images/icon.jpg"))
            ));
        } catch (Exception ignored) {}
        stage.setResizable(false);

        alert.showAndWait().ifPresent(response -> {
            if (response == loginButton) {
                // Очищаем текущий аккаунт и открываем окно входа
                AccountManager.clear();
                BrowserAuth.openLogin(null, acc -> {
                    if (onRelogin != null) {
                        onRelogin.run();
                    }
                });
            }
        });
    }

    /**
     * Компактное уведомление в стиле тоста (опциональная альтернатива)
     */
    public static void showQuickAuthExpiredNotification() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Уведомление");
        alert.setHeaderText(null);
        alert.setContentText("⚠️ Сессия истекла. Войдите в аккаунт для полного функционала.");

        DialogPane pane = alert.getDialogPane();
        pane.setStyle("-fx-background-color: #3a3a3a; -fx-font-size: 13px;");
        pane.lookup(".content.label").setStyle("-fx-text-fill: #ffcc00;");

        ButtonType okButton = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        alert.getButtonTypes().setAll(okButton);

        Stage stage = (Stage) pane.getScene().getWindow();
        try {
            stage.getIcons().add(new Image(
                    Objects.requireNonNull(LauncherUI.class.getResourceAsStream("/images/icon.jpg"))
            ));
        } catch (Exception ignored) {}

        alert.show();
    }
}
