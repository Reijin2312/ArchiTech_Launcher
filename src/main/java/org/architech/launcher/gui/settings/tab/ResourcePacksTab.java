// file: ResourcePacksTab.java
package org.architech.launcher.gui.settings.tab;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import org.architech.launcher.ArchiTechLauncher;
import org.architech.launcher.gui.LauncherUI;
import org.architech.launcher.utils.logging.LogManager;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

import static org.architech.launcher.ArchiTechLauncher.GAME_DIR;

public class ResourcePacksTab extends AbstractAssetsTab {

    public Parent createContent() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("mods-pane");

        VBox list = new VBox(10);
        list.setFillWidth(true);
        list.setPadding(new Insets(8, 16, 16, 16));

        Path packsDir = GAME_DIR.resolve("resourcepacks");
        try { Files.createDirectories(packsDir); } catch (Exception ignored) {}

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-font-weight: bold; -fx-padding: 4 0 8 0;");
        header.getChildren().addAll(
                fixedHeaderLabel("Название", COL_NAME),
                fixedHeaderLabel("Версия",   COL_VER),
                fixedHeaderLabel("Обновлён", COL_DATE),
                fixedHeaderLabel("Активен",  COL_TOGGLE)
        );

        ProgressIndicator loading = new ProgressIndicator();
        loading.setPrefSize(48, 48);
        StackPane loadingHolder = new StackPane(loading);
        loadingHolder.setPadding(new Insets(24));
        root.setCenter(loadingHolder);

        record PackMeta(Path path, String fileName, boolean disabled, String displayName, String version, String lastUpdated) {}

        ArchiTechLauncher.submitBackground(() -> {
            List<PackMeta> metas = new ArrayList<>();
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(packsDir)) {
                for (Path p : ds) {
                    try {
                        String fn = p.getFileName().toString();
                        boolean disabled = fn.endsWith(".disabled");
                        String visible = fn.replaceAll("\\.disabled$", "");
                        boolean accepted = Files.isDirectory(p)
                                || visible.toLowerCase().endsWith(".zip");
                        if (!accepted) continue;
                        String version = parseShaderPackVersion(p);
                        String lastUpdated = formatFileTime(Files.getLastModifiedTime(p));
                        metas.add(new PackMeta(p, fn, disabled, visible, version, lastUpdated));
                    } catch (Exception exInner) {
                        LogManager.getLogger().warning("resourcepacks: read meta failed for " + p + ": " + exInner.getMessage());
                    }
                }
            } catch (Exception ex) {
                Platform.runLater(() -> LauncherUI.showError("Ошибка загрузки списка ресурс-паков", ex.getMessage()));
                return;
            }

            Platform.runLater(() -> {
                list.getChildren().clear();
                list.getChildren().add(header);

                for (PackMeta m : metas) {
                    HBox row = new HBox(12);
                    row.getStyleClass().add("mod-row");
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.setMinHeight(48);

                    ImageView icon = new ImageView();
                    icon.setFitHeight(24);
                    icon.setFitWidth(24);
                    icon.setPreserveRatio(true);
                    try (InputStream is = getClass().getResourceAsStream("/img/mod_placeholder.png")) {
                        if (is != null) icon.setImage(new Image(is));
                    } catch (Exception ignore) {}

                    Label nameLabel = new Label(m.displayName());
                    nameLabel.setGraphic(icon);
                    nameLabel.setGraphicTextGap(8);
                    nameLabel.setTextOverrun(OverrunStyle.ELLIPSIS);

                    Label versionLabel = new Label(m.version());
                    versionLabel.setTextOverrun(OverrunStyle.ELLIPSIS);

                    Label dateLabel = new Label(m.lastUpdated());

                    CheckBox toggle = new CheckBox();
                    toggle.getStyleClass().add("mods-checkbox");
                    toggle.setSelected(!m.disabled());

                    final String[] fileName = { m.fileName() };
                    final boolean[] disabled = { m.disabled() };

                    toggle.selectedProperty().addListener((obs, oldVal, val) -> {
                        try {
                            Path current = packsDir.resolve(fileName[0]);
                            if (val && disabled[0]) {
                                Path newName = packsDir.resolve(fileName[0].replace(".disabled", ""));
                                Files.move(current, newName, StandardCopyOption.REPLACE_EXISTING);
                                nameLabel.setText(newName.getFileName().toString());
                                fileName[0] = newName.getFileName().toString();
                                disabled[0] = false;
                                row.getStyleClass().remove("disabled");
                            } else if (!val && !disabled[0]) {
                                Path newName = packsDir.resolve(fileName[0] + ".disabled");
                                Files.move(current, newName, StandardCopyOption.REPLACE_EXISTING);
                                nameLabel.setText(newName.getFileName().toString().replace(".disabled", ""));
                                fileName[0] = newName.getFileName().toString();
                                disabled[0] = true;
                                row.getStyleClass().add("disabled");
                            }
                        } catch (Exception ex) {
                            LauncherUI.showError("Не удалось переключить ресурс-пак", ex.getMessage());
                            toggle.setSelected(oldVal);
                        }
                    });

                    row.getChildren().addAll(
                            fixedCell(nameLabel, COL_NAME, Pos.CENTER_LEFT),
                            fixedCell(versionLabel, COL_VER, Pos.CENTER),
                            fixedCell(dateLabel, COL_DATE, Pos.CENTER),
                            fixedCell(toggle, COL_TOGGLE, Pos.CENTER)
                    );

                    list.getChildren().add(row);

                    ArchiTechLauncher.submitBackground(() -> {
                        byte[] bytes = robustLoadShaderPackIconBytes(m.path());
                        if (bytes != null && bytes.length > 0) {
                            Platform.runLater(() -> {
                                try { icon.setImage(new Image(new ByteArrayInputStream(bytes))); } catch (Exception ignored) {}
                            });
                        }
                    });
                }

                ScrollPane scroll = new ScrollPane(list);
                scroll.setFitToWidth(true);
                root.setCenter(scroll);
                list.setStyle("-fx-background-color: transparent;");
                scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
                if (scroll.getContent() != null) scroll.getContent().setStyle("-fx-background-color: transparent;");
            });

            Platform.runLater(() -> playRowAnimations(list, header));
        });

        return root;
    }
}
