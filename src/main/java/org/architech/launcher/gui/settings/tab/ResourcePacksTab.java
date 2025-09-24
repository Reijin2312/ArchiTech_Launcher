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
import org.architech.launcher.discord.DiscordIntegration;
import org.architech.launcher.gui.LauncherUI;
import org.architech.launcher.utils.logging.LogManager;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.architech.launcher.ArchiTechLauncher.GAME_DIR;

public class ResourcePacksTab {
    private static final double COL_NAME   = 320;
    private static final double COL_VER    = 140;
    private static final double COL_DATE   = 180;
    private static final double COL_TOGGLE = 80;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

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
                        String version = parseResourcePackVersion(p);
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
                            LauncherUI.showError("Не удалось переключить пакет", ex.getMessage());
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

                    // background icon load
                    ArchiTechLauncher.submitBackground(() -> {
                        byte[] bytes = robustLoadPackIconBytes(m.path());
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
        });

        return root;
    }

    private static Pane fixedCell(javafx.scene.Node node, double width, Pos align) {
        StackPane box = new StackPane(node);
        box.setAlignment(align);
        box.setMinWidth(width); box.setPrefWidth(width); box.setMaxWidth(width);
        return box;
    }

    private static Pane fixedHeaderLabel(String text, double width) {
        Label l = new Label(text); l.setAlignment(Pos.CENTER); return fixedCell(l, width, Pos.CENTER);
    }

    private String formatFileTime(FileTime ft) {
        Instant ins = Instant.ofEpochMilli(ft.toMillis());
        return DATE_FMT.format(ins.atZone(ZoneId.systemDefault()));
    }

    private String parseResourcePackVersion(Path packPath) {
        try {
            if (Files.isDirectory(packPath)) {
                Path mcmeta = packPath.resolve("pack.mcmeta");
                if (Files.exists(mcmeta)) {
                    String content = Files.readString(mcmeta, StandardCharsets.UTF_8);
                    Matcher m = Pattern.compile("\"pack_format\"\\s*:\\s*(\\d+)").matcher(content);
                    if (m.find()) return "pack_format " + m.group(1);
                }
            } else {
                try (FileSystem fs = FileSystems.newFileSystem(packPath, (ClassLoader) null)) {
                    Path mcmeta = fs.getPath("pack.mcmeta");
                    if (Files.exists(mcmeta)) {
                        String content = Files.readString(mcmeta, StandardCharsets.UTF_8);
                        Matcher m = Pattern.compile("\"pack_format\"\\s*:\\s*(\\d+)").matcher(content);
                        if (m.find()) return "pack_format " + m.group(1);
                    }
                }
            }
        } catch (Exception ignored) {}
        return "—";
    }

    private byte[] robustLoadPackIconBytes(Path packPath) {
        try {
            if (Files.isDirectory(packPath)) {
                Path p = packPath.resolve("pack.png");
                if (Files.exists(p)) return Files.readAllBytes(p);
            } else {
                try (FileSystem fs = FileSystems.newFileSystem(packPath, (ClassLoader) null)) {
                    Path p = fs.getPath("pack.png");
                    if (Files.exists(p)) return Files.readAllBytes(p);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
