package org.architech.launcher.gui.player;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Window;
import javafx.util.Duration;
import org.architech.launcher.ArchiTechLauncher;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class PlayersPopup {

    private static final Popup playersPopup = new Popup();
    public static List<String> latestOnlinePlayers = Collections.emptyList();
    private static final ConcurrentHashMap<String, CompletableFuture<Image>> inflight = new ConcurrentHashMap<>();

    private static boolean popupHovered = false;

    public static void setup(HBox leftInfo, Label onlineLabelField) {
        playersPopup.setAutoFix(true);
        playersPopup.setAutoHide(false);
        playersPopup.setHideOnEscape(true);

        leftInfo.setOnMouseEntered(e -> {
            if (!latestOnlinePlayers.isEmpty()) {
                warmupHeads(latestOnlinePlayers);
                PlayersPopup.showPlayersPopup(e.getScreenX(), e.getScreenY(), onlineLabelField);
            }
        });
        leftInfo.setOnMouseMoved(e -> {
            if (playersPopup.isShowing()) {
                playersPopup.setX(e.getScreenX() + 12);
                playersPopup.setY(e.getScreenY() + 12);
            }
        });
        leftInfo.setOnMouseExited(e -> {
            PauseTransition pt = new PauseTransition(Duration.millis(120));
            pt.setOnFinished(ev -> {
                if (!popupHovered) playersPopup.hide();
            });
            pt.play();
        });
    }

    private static void showPlayersPopup(double screenX, double screenY, Label onlineLabelField) {
        Platform.runLater(() -> {
            VBox content = new VBox(6);
            content.setPadding(new Insets(6));
            content.setStyle("-fx-background-color: rgba(24,24,24,0.95); -fx-padding: 6; -fx-background-radius: 6; -fx-border-color: rgba(255,255,255,0.04); -fx-border-radius:6;");
            content.setPrefWidth(220);

            if (latestOnlinePlayers.isEmpty()) {
                Label l = new Label("Список игроков недоступен");
                l.setStyle("-fx-text-fill: #ddd;");
                content.getChildren().add(l);
            } else {
                for (String name : latestOnlinePlayers) {
                    HBox row = new HBox(8);
                    row.setAlignment(Pos.CENTER_LEFT);

                    ImageView iv = new ImageView();
                    Image placeholder = null;
                    Image fallback = null;
                    setHeadAsync(iv, name, 20, placeholder, fallback);

                    Label nameLbl = new Label(name);
                    nameLbl.setStyle("-fx-text-fill: white;");

                    row.getChildren().addAll(iv, nameLbl);
                    content.getChildren().add(row);
                }
            }

            content.setOnMouseEntered(ev -> popupHovered = true);
            content.setOnMouseExited(ev -> {
                popupHovered = false;
                playersPopup.hide();
            });

            playersPopup.getContent().clear();
            playersPopup.getContent().add(content);

            Window win = onlineLabelField.getScene().getWindow();
            playersPopup.show(win, screenX + 12, screenY + 12);
        });
    }

    private static CompletableFuture<Image> loadHead(String name, int size) {
        final String key = name.toLowerCase(Locale.ROOT) + ":" + size;
        return inflight.computeIfAbsent(key, k -> {
            CompletableFuture<Image> cf = new CompletableFuture<>();
            ArchiTechLauncher.submitBackground(() -> {
                try {
                    Image img = AvatarImage.fromName(name, size);
                    cf.complete(img);
                } catch (Throwable t) {
                    cf.completeExceptionally(t);
                } finally {
                    inflight.remove(k);
                }
            });
            return cf;
        });
    }

    private static void setHeadAsync(ImageView iv, String name, int size, Image placeholder, Image fallback) {
        iv.setFitWidth(size);
        iv.setFitHeight(size);
        if (placeholder != null) iv.setImage(placeholder);

        final Object guard = new Object();
        iv.getProperties().put("head.guard", guard);

        loadHead(name, size).whenComplete((img, err) -> Platform.runLater(() -> {
            if (iv.getProperties().get("head.guard") != guard) return;
            if (err == null && img != null) iv.setImage(img);
            else if (fallback != null) iv.setImage(fallback);
        }));
    }

    private static void warmupHeads(Collection<String> names) {
        if (names == null || names.isEmpty()) return;
        ArchiTechLauncher.submitBackground(() -> {
            for (String n : names) loadHead(n, 20);
        });
    }

}
