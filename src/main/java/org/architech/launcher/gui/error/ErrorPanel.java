package org.architech.launcher.gui.error;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.architech.launcher.gui.LauncherUI;
import org.architech.launcher.utils.Utils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class ErrorPanel {

    public static void showError(String msg, String details) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Критическая ошибка");
        a.setHeaderText("Кажется, что-то сломалось :(");
        a.setContentText(msg);

        DialogPane pane = a.getDialogPane();
        pane.setStyle("-fx-background-color: #2b2b2b; -fx-font-size: 14px;");
        pane.lookup(".content.label").setStyle("-fx-text-fill: white;");

        TextArea textArea = new TextArea(details);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setStyle("-fx-control-inner-background: #1e1e1e; -fx-text-fill: white;");
        textArea.setPrefRowCount(Math.min(10, details.split("\n").length + 1)); // подгон по строкам
        textArea.setMaxWidth(Double.MAX_VALUE);
        textArea.setPrefWidth(500);

        Label detailsLabel = new Label("Подробности:");
        detailsLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");

        ImageView gifView = new ImageView(new Image(
                Objects.requireNonNull(LauncherUI.class.getResourceAsStream("/images/cat.gif"))
        ));
        gifView.setPreserveRatio(true);
        gifView.setFitWidth(500);

        VBox expandableBox = new VBox(8, detailsLabel, textArea, gifView);
        expandableBox.setAlignment(Pos.CENTER);
        expandableBox.setStyle("-fx-background-color: #2b2b2b;");
        expandableBox.setPadding(new Insets(10));

        pane.setPrefWidth(500);
        pane.setMinWidth(500);
        pane.setMaxWidth(500);

        a.getDialogPane().setExpandableContent(expandableBox);

        ButtonType reportBtn = new ButtonType("Сообщить", ButtonBar.ButtonData.OTHER);
        a.getButtonTypes().setAll(reportBtn);

        Node reportNode = a.getDialogPane().lookupButton(reportBtn);
        if (reportNode instanceof Button) {
            reportNode.setStyle("-fx-font-size: 11px; -fx-padding: 4 8;");
        }

        Stage stage = (Stage) pane.getScene().getWindow();
        stage.getIcons().add(new Image(
                Objects.requireNonNull(LauncherUI.class.getResourceAsStream("/images/icon.jpg"))
        ));
        stage.setResizable(false);

        a.showAndWait().ifPresent(response -> {
            if (response == reportBtn) {
                String encoded = URLEncoder.encode(msg + "\n\n" + details, StandardCharsets.UTF_8);
                Utils.openInBrowser("https://t.me/Raijin2312?text=" + encoded);
            }
        });
    }

}
