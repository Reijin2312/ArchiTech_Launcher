package org.architech.launcher.gui.settings.tab;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.architech.launcher.ArchiTechLauncher;
import org.architech.launcher.gui.LauncherUI;
import org.architech.launcher.gui.error.ErrorPanel;
import org.architech.launcher.gui.settings.MainSettingsUI;
import org.architech.launcher.utils.Jsons;
import org.architech.launcher.utils.logging.LogManager;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.architech.launcher.ArchiTechLauncher.LAUNCHER_DIR;

public class BackgroundsTab {
    private static final String SELECTED_STYLE = "-fx-border-color: -fx-focus-color, -fx-focus-color; -fx-border-width: 3; -fx-border-radius: 6; -fx-background-radius: 6;";
    private static final String NON_SELECTED_STYLE = "-fx-border-color: transparent; -fx-border-width: 0; -fx-border-radius: 6; -fx-background-radius: 6;";

    private final Stage stage;
    private final Path backgroundsDir = LAUNCHER_DIR.resolve("backgrounds");
    private final GridPane grid = new GridPane();
    private final List<Path> items = new ArrayList<>();
    private volatile String selectedFileName = null;

    public BackgroundsTab(Stage stage) {
        this.stage = stage;
        try { Files.createDirectories(backgroundsDir); } catch (Exception ignored) {}
    }

    public Parent createContent() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("mods-pane");

        HBox top = new HBox(8);
        top.setPadding(new Insets(8,16,8,16));
        TextField search = new TextField();
        search.setPromptText("Поиск по имени файла");
        Button searchBtn = new Button("Поиск");
        Button addBtn = new Button("+");
        addBtn.setPrefSize(36,28);
        HBox.setHgrow(search, Priority.ALWAYS);
        top.getChildren().addAll(search, searchBtn, addBtn);
        root.setTop(top);

        grid.setHgap(12);
        grid.setVgap(12);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setPercentWidth(50);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setPercentWidth(50);
        grid.getColumnConstraints().addAll(c1, c2);

        ScrollPane sp = new ScrollPane(grid);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setFitToWidth(true);
        root.setCenter(sp);

        searchBtn.setOnAction(e -> applyFilter(search.getText()));
        search.setOnAction(e -> applyFilter(search.getText()));
        addBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Добавить фон");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Изображения", "*.png", "*.jpg", "*.jpeg"));
            java.io.File f = chooser.showOpenDialog(stage);
            if (f == null) return;
            try {
                String name = System.currentTimeMillis() + "-" + f.getName();
                Path dest = backgroundsDir.resolve(name);
                Files.copy(f.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);
                loadAndShow();
            } catch (Exception ex) {
                LogManager.getLogger().warning("add background failed: " + ex.getMessage());
                Platform.runLater(() -> ErrorPanel.showError("Не удалось добавить фон", ex.getMessage()));
            }
        });

        loadAndShow();
        return root;
    }

    private void loadAndShow() {
        ArchiTechLauncher.submitBackground(() -> {
            items.clear();
            try (Stream<Path> s = Files.list(backgroundsDir)) {
                items.addAll(s.filter(p -> {
                    String n = p.getFileName().toString().toLowerCase();
                    return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg");
                }).sorted().toList());
            } catch (Exception ignored) {}

            readSelectedFromConfig();

            Platform.runLater(() -> {
                if (selectedFileName != null && !selectedFileName.isBlank()) {
                    String cfg = selectedFileName.trim();
                    Path match = null;
                    for (Path p : items) {
                        if (cfg.equalsIgnoreCase(p.getFileName().toString().trim())) {
                            match = p;
                            break;
                        }
                    }
                    if (match != null) {
                        try {
                            LauncherUI.applyBackground(match);
                            MainSettingsUI.applyBackground(match);
                            ArchiTechLauncher.LAUNCHER_BACKGROUND = match.getFileName().toString().trim();
                            selectedFileName = match.getFileName().toString().trim();
                        } catch (Exception ex) {
                            LogManager.getLogger().warning("apply background on load failed: " + ex.getMessage());
                        }
                    } else {
                        selectedFileName = selectedFileName.trim();
                    }
                }
                rebuildGrid(items);
                clearFocus();
            });
        });
    }

    private void readSelectedFromConfig() {
        selectedFileName = null;
        try {
            if (!Files.exists(ArchiTechLauncher.CONFIG_PATH)) return;
            try (Reader r = Files.newBufferedReader(ArchiTechLauncher.CONFIG_PATH, StandardCharsets.UTF_8)) {
                Map<?,?> old = Jsons.MAPPER.readValue(r, Map.class);
                if (old != null) {
                    Object bg = old.get("background");
                    if (bg instanceof String) {
                        selectedFileName = ((String) bg).trim();
                        LogManager.getLogger().info("BackgroundsTab: read selected from config -> '" + selectedFileName + "'");
                    }
                }
            } catch (Exception ex) {
                LogManager.getLogger().warning("BackgroundsTab: failed read config: " + ex.getMessage());
            }
        } catch (Exception ex) {
            LogManager.getLogger().warning("BackgroundsTab: readSelectedFromConfig outer failed: " + ex.getMessage());
        }
    }

    private void applyFilter(String q) {
        String qq = (q == null) ? "" : q.trim().toLowerCase();
        List<Path> filtered = items.stream()
                .filter(p -> p.getFileName().toString().toLowerCase().contains(qq))
                .collect(Collectors.toList());
        rebuildGrid(filtered);
        clearFocus();
    }

    private void rebuildGrid(List<Path> list) {
        grid.getChildren().clear();
        for (int i = 0; i < list.size(); i++) {
            Path p = list.get(i);
            int col = i % 2;
            int row = i / 2;

            VBox tile = new VBox(6);
            tile.setAlignment(Pos.TOP_CENTER);
            tile.getStyleClass().add("background-tile");
            tile.setPadding(new Insets(6));

            // запретить плитке получать фокус (чтобы CSS :focused не подкрашивал первый)
            tile.setFocusTraversable(false);

            ImageView iv = new ImageView();
            iv.setPreserveRatio(true);
            iv.setSmooth(true);
            iv.setCache(true);
            iv.fitWidthProperty().bind(tile.widthProperty().subtract(16));
            iv.setFitHeight(120);
            iv.setFocusTraversable(false);

            try {
                Image img = new Image(p.toUri().toString(), true);
                iv.setImage(img);
            } catch (Exception ignored) {}

            Label name = new Label(p.getFileName().toString());
            name.setMaxWidth(Double.MAX_VALUE);
            name.setWrapText(true);
            name.getStyleClass().add("mods-label");
            name.setFocusTraversable(false);

            tile.getChildren().addAll(iv, name);

            String tileName = p.getFileName().toString().trim();
            boolean isSelected = (selectedFileName != null)
                    && !selectedFileName.isBlank()
                    && selectedFileName.trim().equalsIgnoreCase(tileName);

            LogManager.getLogger().fine("BackgroundsTab: compare cfg='" + selectedFileName + "' vs tile='" + tileName + "' => " + isSelected);

            if (isSelected) {
                tile.setStyle(SELECTED_STYLE);
                if (!tile.getStyleClass().contains("selected-bg")) tile.getStyleClass().add("selected-bg");
            } else {
                tile.setStyle(NON_SELECTED_STYLE);
                tile.getStyleClass().remove("selected-bg");
            }

            ContextMenu menu = new ContextMenu();
            MenuItem deleteItem = getDeleteItem(p);
            menu.getItems().add(deleteItem);

            tile.setOnMouseClicked(ev -> {
                if (ev.getButton() == MouseButton.PRIMARY) {
                    applyAndSaveBackground(p);
                    // сразу установить локально для мгновенного отклика
                    selectedFileName = p.getFileName().toString().trim();
                    rebuildGrid(items);
                    clearFocus();
                } else if (ev.getButton() == MouseButton.SECONDARY) {
                    menu.show(tile, ev.getScreenX(), ev.getScreenY());
                }
            });

            tile.setOnContextMenuRequested(evt -> menu.show(tile, evt.getScreenX(), evt.getScreenY()));

            grid.add(tile, col, row);
        }
    }

    private MenuItem getDeleteItem(Path p) {
        MenuItem deleteItem = new MenuItem("Удалить");
        deleteItem.setOnAction(ae -> {
            ArchiTechLauncher.submitBackground(() -> {
                try {
                    Files.deleteIfExists(p);

                    try {
                        Map<String,Object> cfg = new LinkedHashMap<>();
                        if (Files.exists(ArchiTechLauncher.CONFIG_PATH)) {
                            try (Reader r = Files.newBufferedReader(ArchiTechLauncher.CONFIG_PATH, StandardCharsets.UTF_8)) {
                                Map<?,?> old = Jsons.MAPPER.readValue(r, Map.class);
                                if (old != null) cfg.putAll((Map) old);
                            } catch (Exception ignored) {}
                        }

                        Object bgObj = cfg.get("background");
                        String current = (bgObj instanceof String) ? ((String) bgObj).trim() : null;
                        if (current != null && current.equalsIgnoreCase(p.getFileName().toString().trim())) {
                            cfg.remove("background");
                            Files.createDirectories(ArchiTechLauncher.CONFIG_PATH.getParent());
                            try (Writer w = Files.newBufferedWriter(ArchiTechLauncher.CONFIG_PATH, StandardCharsets.UTF_8)) {
                                Jsons.MAPPER.writerWithDefaultPrettyPrinter().writeValue(w, cfg);
                            } catch (Exception ignored) {}
                            selectedFileName = null;
                            Platform.runLater(() -> {
                                try { MainSettingsUI.applyBackground(null); } catch (Exception ignored) {}
                                try { LauncherUI.applyBackground(null); } catch (Exception ignored) {}
                            });
                        }
                    } catch (Exception ex) {
                        LogManager.getLogger().warning("Не удалось обновить конфиг после удаления: " + ex.getMessage());
                    }

                    Platform.runLater(this::loadAndShow);
                } catch (Exception ex) {
                    Platform.runLater(() -> ErrorPanel.showError("Не удалось удалить фон", ex.getMessage()));
                    LogManager.getLogger().warning("delete background failed: " + ex.getMessage());
                }
            });
        });
        return deleteItem;
    }

    private void applyAndSaveBackground(Path p) {
        try {
            LauncherUI.applyBackground(p);
        } catch (Exception ex) {
            LogManager.getLogger().warning("LauncherUI.applyBackground failed: " + ex.getMessage());
        }
        try {
            MainSettingsUI.applyBackground(p);
        } catch (Exception ex) {
            LogManager.getLogger().warning("MainSettingsUI.applyBackground failed: " + ex.getMessage());
        }

        try {
            Map<String,Object> cfg = new LinkedHashMap<>();
            if (Files.exists(ArchiTechLauncher.CONFIG_PATH)) {
                try (Reader r = Files.newBufferedReader(ArchiTechLauncher.CONFIG_PATH, StandardCharsets.UTF_8)) {
                    Map<?,?> old = Jsons.MAPPER.readValue(r, Map.class);
                    if (old != null) cfg.putAll((Map) old);
                } catch (Exception ignored) {}
            }

            String fileName = p.getFileName().toString().trim();
            cfg.put("background", fileName);
            Files.createDirectories(ArchiTechLauncher.CONFIG_PATH.getParent());
            try (Writer w = Files.newBufferedWriter(ArchiTechLauncher.CONFIG_PATH, StandardCharsets.UTF_8)) {
                Jsons.MAPPER.writerWithDefaultPrettyPrinter().writeValue(w, cfg);
            }

            selectedFileName = fileName;
            // синхронизировать состояние списка из файловой системы
            loadAndShow();
        } catch (Exception ex) {
            LogManager.getLogger().warning("Не удалось записать background в конфиг: " + ex.getMessage());
        }
    }

    private void clearFocus() {
        // убрать фокус с плиток — вызывает отсутствие визуального "первого" выделения из-за :focused в CSS
        Platform.runLater(() -> {
            try {
                grid.requestFocus();
            } catch (Exception ignored) {}
        });
    }
}
