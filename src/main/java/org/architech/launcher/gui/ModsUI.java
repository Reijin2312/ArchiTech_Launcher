package org.architech.launcher.gui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.OverrunStyle;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.architech.launcher.MCLauncher.GAME_DIR;

public class ModsUI {
    private final Stage stage;
    private final Scene settingsMenuScene;

    private static final double COL_NAME   = 320;
    private static final double COL_VER    = 140;
    private static final double COL_DATE   = 180;
    private static final double COL_TOGGLE = 80;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public ModsUI(Stage stage, Scene settingsMenuScene) {
        this.stage = stage;
        this.settingsMenuScene = settingsMenuScene;
    }

    public void show() {
        double w = (stage.getScene() != null ? stage.getScene().getWidth() : settingsMenuScene.getWidth());
        double h = (stage.getScene() != null ? stage.getScene().getHeight() : settingsMenuScene.getHeight());

        BorderPane modsRoot = new BorderPane();
        modsRoot.getStyleClass().add("mods-pane");

        Label title = new Label("Управление модами");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        BorderPane.setAlignment(title, Pos.CENTER);
        BorderPane.setMargin(title, new Insets(16, 16, 8, 16));
        modsRoot.setTop(title);

        VBox modsList = new VBox(10);
        modsList.setFillWidth(true);
        modsList.setPadding(new Insets(8, 16, 16, 16));

        Path modsDir = GAME_DIR.resolve("mods");
        try {
            Files.createDirectories(modsDir);

            // Шапка (без "Иконка"), заголовки по центру
            HBox header = new HBox(12);
            header.setAlignment(Pos.CENTER_LEFT);
            header.setStyle("-fx-font-weight: bold; -fx-padding: 4 0 8 0;");
            header.getChildren().addAll(
                    fixedHeaderLabel("Название", COL_NAME),
                    fixedHeaderLabel("Версия",   COL_VER),
                    fixedHeaderLabel("Обновлён", COL_DATE),
                    fixedHeaderLabel("Активен",  COL_TOGGLE)
            );
            modsList.getChildren().add(header);

            try (DirectoryStream<Path> ds = Files.newDirectoryStream(modsDir, "*.jar*")) {
                for (Path modPath : ds) {
                    HBox row = new HBox(12);
                    row.getStyleClass().add("mod-row");
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.setMinHeight(48);

                    // robust icon loader
                    Image iconImg = robustLoadIcon(modPath);
                    ImageView icon = new ImageView(iconImg);
                    icon.setFitHeight(24);
                    icon.setFitWidth(24);
                    icon.setPreserveRatio(true);

                    final String[] fileName = { modPath.getFileName().toString() };
                    final boolean[] disabled = { fileName[0].endsWith(".disabled") };
                    if (disabled[0]) row.getStyleClass().add("disabled");

                    Label nameLabel = new Label(fileName[0].replace(".disabled", ""));
                    nameLabel.setGraphic(icon);
                    nameLabel.setGraphicTextGap(8);
                    nameLabel.setTextOverrun(OverrunStyle.ELLIPSIS);

                    String version = parseModVersion(modPath);
                    Label versionLabel = new Label(version);
                    versionLabel.setTextOverrun(OverrunStyle.ELLIPSIS);

                    String lastUpdated = formatFileTime(Files.getLastModifiedTime(modPath));
                    Label dateLabel = new Label(lastUpdated);

                    CheckBox toggle = new CheckBox();
                    toggle.getStyleClass().add("mods-checkbox");
                    toggle.setSelected(!disabled[0]);
                    toggle.selectedProperty().addListener((obs, oldVal, val) -> {
                        try {
                            Path current = modsDir.resolve(fileName[0]);
                            if (val && disabled[0]) {
                                Path newName = modsDir.resolve(fileName[0].replace(".disabled", ""));
                                Files.move(current, newName, StandardCopyOption.REPLACE_EXISTING);
                                nameLabel.setText(newName.getFileName().toString());
                                fileName[0] = newName.getFileName().toString();
                                disabled[0] = false;
                                row.getStyleClass().remove("disabled");
                            } else if (!val && !disabled[0]) {
                                Path newName = modsDir.resolve(fileName[0] + ".disabled");
                                Files.move(current, newName, StandardCopyOption.REPLACE_EXISTING);
                                nameLabel.setText(newName.getFileName().toString().replace(".disabled", ""));
                                fileName[0] = newName.getFileName().toString();
                                disabled[0] = true;
                                row.getStyleClass().add("disabled");
                            }
                        } catch (Exception ex) {
                            showError("Не удалось переключить мод: " + ex.getMessage());
                            toggle.setSelected(oldVal);
                        }
                    });

                    row.getChildren().addAll(
                            fixedCell(nameLabel, COL_NAME, Pos.CENTER_LEFT),
                            fixedCell(versionLabel, COL_VER, Pos.CENTER),
                            fixedCell(dateLabel, COL_DATE, Pos.CENTER),
                            fixedCell(toggle, COL_TOGGLE, Pos.CENTER)
                    );

                    modsList.getChildren().add(row);
                }
            }
        } catch (Exception e) {
            showError("Ошибка загрузки списка модов: " + e.getMessage());
        }

        ScrollPane scroll = new ScrollPane(modsList);
        scroll.setFitToWidth(true);
        modsRoot.setCenter(scroll);

        modsList.setStyle("-fx-background-color: transparent;");
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        if (scroll.getContent() != null) scroll.getContent().setStyle("-fx-background-color: transparent;");

        Button back = new Button("Назад");
        styleMainButton(back);
        back.setOnAction(e -> stage.setScene(settingsMenuScene));

        HBox bottom = new HBox(back);
        bottom.setAlignment(Pos.CENTER_LEFT);
        bottom.setPadding(new Insets(12, 16, 16, 16));
        modsRoot.setBottom(bottom);

        Scene modsScene = new Scene(modsRoot, w, h);
        modsScene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/styles.css")).toExternalForm());
        stage.setScene(modsScene);

        stage.setMinWidth(w);
        stage.setMinHeight(h);
    }

    // === layout helpers ===
    private static Pane fixedCell(javafx.scene.Node node, double width, Pos align) {
        StackPane box = new StackPane(node);
        box.setAlignment(align);
        box.setMinWidth(width);
        box.setPrefWidth(width);
        box.setMaxWidth(width);
        return box;
    }

    private static Pane fixedHeaderLabel(String text, double width) {
        Label l = new Label(text);
        l.setAlignment(Pos.CENTER);
        return fixedCell(l, width, Pos.CENTER);
    }

    private String formatFileTime(FileTime ft) {
        Instant ins = Instant.ofEpochMilli(ft.toMillis());
        return DATE_FMT.format(ins.atZone(ZoneId.systemDefault()));
    }

    private void styleMainButton(Button btn) {
        btn.setStyle("-fx-background-color: #4caf50; -fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;");
    }

    private void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg);
        a.showAndWait();
    }

    // === robust icon loading ===

    private static final String FALLBACK_PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAoAAAAKCAYAAACNMs+9AAAAF0lEQVR42mP8z/CfAQgwYGBgYGLAAQBVmgJ3jzg9HwAAAABJRU5ErkJggg==";

    private Image robustLoadIcon(Path jarPath) {
        try (FileSystem fs = FileSystems.newFileSystem(jarPath, (ClassLoader) null)) {
            // get potential modId for heuristics
            String modId = extractFirstModId(fs);

            List<IconCandidate> candidates = new ArrayList<>();

            // 1) mods.toml explicit logoFile (highest priority)
            try {
                Path toml = fs.getPath("META-INF", "mods.toml");
                if (Files.exists(toml)) {
                    String content = Files.readString(toml, StandardCharsets.UTF_8);
                    Matcher m = Pattern.compile("(?m)^\\s*logoFile\\s*=\\s*\"([^\"]+)\"").matcher(content);
                    if (m.find()) {
                        Path p = safeFsPath(fs, m.group(1));
                        if (p != null && Files.exists(p)) candidates.add(new IconCandidate(p, 200));
                    }
                    // also try to pick any png paths referenced in the toml (fallback)
                    Matcher anyPng = Pattern.compile("([\\w/\\\\\\-]+\\.(?i:png))").matcher(content);
                    while (anyPng.find()) {
                        Path p = safeFsPath(fs, anyPng.group(1));
                        if (p != null && Files.exists(p)) candidates.add(new IconCandidate(p, 140));
                    }
                }
            } catch (Exception ignored) {}

            // 2) fabric/quilt json icon entries (high priority)
            for (String jfn : new String[]{"fabric.mod.json", "quilt.mod.json", "mods.json", "mod.json"}) {
                try {
                    Path json = fs.getPath(jfn);
                    if (Files.exists(json)) {
                        String content = Files.readString(json, StandardCharsets.UTF_8);
                        // "icon": "file.png"
                        Matcher m1 = Pattern.compile("\"icon\"\\s*:\\s*\"([^\"]+\\.png)\"", Pattern.CASE_INSENSITIVE).matcher(content);
                        if (m1.find()) {
                            Path p = safeFsPath(fs, m1.group(1));
                            if (p != null && Files.exists(p)) candidates.add(new IconCandidate(p, 190));
                        }
                        // "icon": [ "a.png", ... ]
                        Matcher mArr = Pattern.compile("\"icon\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL | Pattern.CASE_INSENSITIVE).matcher(content);
                        if (mArr.find()) {
                            Matcher png = Pattern.compile("\"([^\"]+\\.png)\"", Pattern.CASE_INSENSITIVE).matcher(mArr.group(1));
                            if (png.find()) {
                                Path p = safeFsPath(fs, png.group(1));
                                if (p != null && Files.exists(p)) candidates.add(new IconCandidate(p, 180));
                            }
                        }
                        // "icon": { "file": "..." }
                        Matcher mObj = Pattern.compile("\"icon\"\\s*:\\s*\\{([^}]+)\\}", Pattern.DOTALL | Pattern.CASE_INSENSITIVE).matcher(content);
                        if (mObj.find()) {
                            Matcher mf = Pattern.compile("\"file\"\\s*:\\s*\"([^\"]+\\.png)\"", Pattern.CASE_INSENSITIVE).matcher(mObj.group(1));
                            if (mf.find()) {
                                Path p = safeFsPath(fs, mf.group(1));
                                if (p != null && Files.exists(p)) candidates.add(new IconCandidate(p, 185));
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            // 3) assets/* typical locations (assets/<ns>/icon.png, assets/<ns>/logo.png, assets/<ns>/textures/gui/logo.png)
            try {
                Path assets = fs.getPath("assets");
                if (Files.exists(assets) && Files.isDirectory(assets)) {
                    try (DirectoryStream<Path> ns = Files.newDirectoryStream(assets)) {
                        for (Path nsDir : ns) {
                            // try common filenames
                            for (String fname : new String[]{"icon.png", "logo.png", "textures/icon.png", "textures/logo.png", "textures/gui/logo.png", "textures/gui/icon.png"}) {
                                Path candidate = nsDir.resolve(fname);
                                if (Files.exists(candidate)) candidates.add(new IconCandidate(candidate, 180 + (modId != null && nsDir.getFileName().toString().equalsIgnoreCase(modId) ? 20 : 0)));
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}

            // 4) root-level typical files: pack.png, icon.png, logo.png
            for (String rootName : new String[]{"pack.png", "icon.png", "logo.png", "mod_icon.png"}) {
                try {
                    Path p = fs.getPath(rootName);
                    if (Files.exists(p)) candidates.add(new IconCandidate(p, 150));
                } catch (Exception ignored) {}
            }

            // 5) deep scan: find any png and score by name & depth
            try {
                for (Path root : fs.getRootDirectories()) {
                    try (Stream<Path> walk = Files.walk(root, 6)) {
                        walk.filter(p -> {
                            Path fn = p.getFileName();
                            return fn != null && fn.toString().toLowerCase().endsWith(".png");
                        }).forEach(p -> {
                            String fn = p.getFileName().toString().toLowerCase(Locale.ROOT);
                            int score = 0;
                            if (fn.contains("logo")) score += 60;
                            if (fn.contains("icon")) score += 50;
                            if (fn.contains("pack")) score += 40;
                            if (fn.contains("mod")) score += 20;
                            if (modId != null && p.toString().toLowerCase().contains(modId.toLowerCase(Locale.ROOT))) score += 40;
                            // depth heuristic (shallower better)
                            int depth = p.getNameCount();
                            score += Math.max(0, 20 - depth);
                            // base fallback
                            score += 10;
                            candidates.add(new IconCandidate(p, score));
                        });
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}

            // choose best candidate by score (and existence)
            Optional<IconCandidate> best = candidates.stream()
                    .filter(c -> c.path != null)
                    .filter(c -> {
                        try { return Files.exists(c.path); } catch (Exception ex) { return false; }
                    })
                    .max(Comparator.comparingInt((IconCandidate c) -> c.score));

            if (best.isPresent()) {
                try {
                    byte[] data = Files.readAllBytes(best.get().path);
                    return new Image(new ByteArrayInputStream(data));
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) { }

        // fallback resource
        try (InputStream res = getClass().getResourceAsStream("/img/mod_placeholder.png")) {
            if (res != null) return new Image(res);
        } catch (Exception ignored) {}

        byte[] png = Base64.getDecoder().decode(FALLBACK_PNG_BASE64);
        return new Image(new ByteArrayInputStream(png));
    }

    private static class IconCandidate {
        final Path path;
        final int score;
        IconCandidate(Path p, int score) { this.path = p; this.score = score; }
    }

    private Path safeFsPath(FileSystem fs, String raw) {
        if (raw == null) return null;
        String s = raw.replaceFirst("^[\\\\/]+", "");
        try {
            return fs.getPath(s);
        } catch (Exception ex) {
            return null;
        }
    }

    private String extractFirstModId(FileSystem fs) {
        try {
            Path toml = fs.getPath("META-INF", "mods.toml");
            if (Files.exists(toml)) {
                String content = Files.readString(toml, StandardCharsets.UTF_8);
                Matcher mods = Pattern.compile("\\[\\[mods\\]\\](.*?)(?=\\n\\[\\[|$)", Pattern.DOTALL).matcher(content);
                if (mods.find()) {
                    String block = mods.group(1);
                    Matcher mid = Pattern.compile("(?m)\\b(modId|modid|id)\\s*=\\s*\"([^\"]+)\"").matcher(block);
                    if (mid.find()) return mid.group(2);
                }
            }
        } catch (Exception ignored) {}
        for (String jfn : new String[]{"fabric.mod.json", "quilt.mod.json", "mods.json", "mod.json"}) {
            try {
                Path json = fs.getPath(jfn);
                if (Files.exists(json)) {
                    String content = Files.readString(json, StandardCharsets.UTF_8);
                    Matcher m = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(content);
                    if (m.find()) return m.group(1);
                    Matcher m2 = Pattern.compile("\"modid\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(content);
                    if (m2.find()) return m2.group(1);
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private String parseModVersion(Path jarPath) {
        try (FileSystem fs = FileSystems.newFileSystem(jarPath, (ClassLoader) null)) {
            String v = readVersionFromModsToml(fs);
            if (isGood(v)) return v;
            v = readVersionFromJson(fs, "fabric.mod.json");
            if (isGood(v)) return v;
            v = readVersionFromJson(fs, "quilt.mod.json");
            if (isGood(v)) return v;
            v = readVersionFromManifest(fs);
            if (isGood(v)) return v;
        } catch (Exception ignored) {}
        return "???";
    }

    private boolean isGood(String v) {
        return v != null && !v.isBlank() && !v.contains("${") && !v.equalsIgnoreCase("unspecified");
    }

    private String readVersionFromModsToml(FileSystem fs) {
        Path toml = fs.getPath("META-INF", "mods.toml");
        if (!Files.exists(toml)) return null;
        try {
            String content = Files.readString(toml, StandardCharsets.UTF_8);
            Matcher mods = Pattern.compile("\\[\\[mods\\]\\](.*?)(?=\\n\\[\\[|$)", Pattern.DOTALL).matcher(content);
            if (mods.find()) {
                String block = mods.group(1);
                Matcher v = Pattern.compile("version\\s*=\\s*\"([^\"]+)\"").matcher(block);
                if (v.find()) return v.group(1);
            }
            Matcher glob = Pattern.compile("(?m)^\\s*version\\s*=\\s*\"([^\"]+)\"\\s*$").matcher(content);
            if (glob.find()) return glob.group(1);
        } catch (Exception ignored) {}
        return null;
    }

    private String readVersionFromJson(FileSystem fs, String fileName) {
        Path json = fs.getPath(fileName);
        if (!Files.exists(json)) return null;
        try {
            Matcher m = Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"").matcher(Files.readString(json, StandardCharsets.UTF_8));
            if (m.find()) return m.group(1);
        } catch (Exception ignored) {}
        return null;
    }

    private String readVersionFromManifest(FileSystem fs) {
        Path mf = fs.getPath("META-INF", "MANIFEST.MF");
        if (!Files.exists(mf)) return null;
        try (InputStream in = Files.newInputStream(mf)) {
            Manifest man = new Manifest(in);
            Attributes a = man.getMainAttributes();
            String v = a.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
            if (isGood(v)) return v;
            v = a.getValue("Bundle-Version");
            if (isGood(v)) return v;
            v = a.getValue(Attributes.Name.SPECIFICATION_VERSION);
            if (isGood(v)) return v;
        } catch (IOException ignored) {}
        return null;
    }
}
