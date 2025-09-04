package org.architech.launcher.gui;

import javafx.geometry.Pos;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;

public class TextFieldWithIcon extends StackPane {
    private final TextField field;
    private final ImageView icon;

    public TextFieldWithIcon(Image img) {
        field = new TextField();
        icon = new ImageView(img);
        icon.setFitWidth(20);
        icon.setFitHeight(20);

        HBox box = new HBox();
        box.setAlignment(Pos.CENTER_RIGHT);
        box.getChildren().add(icon);

        getChildren().addAll(field, box);
        HBox.setHgrow(field, Priority.ALWAYS);

        field.setStyle("-fx-padding: 0 25 0 0;");
    }

    public TextField getTextField() {
        return field;
    }

    public ImageView getIconView() {
        return icon;
    }
}
