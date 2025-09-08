package org.architech.launcher.gui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.OverrunStyle;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.architech.launcher.MCLauncher;
import org.architech.launcher.utils.LogManager;
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

    public Parent createContent() {
        BorderPane modsRoot = new BorderPane();
        modsRoot.getStyleClass().add("mods-pane");

        VBox modsList = new VBox(10);
        modsList.setFillWidth(true);
        modsList.setPadding(new Insets(8, 16, 16, 16));

        Path modsDir = GAME_DIR.resolve("mods");
        try { Files.createDirectories(modsDir); } catch (Exception ignored) {}

        // header (готовим один раз)
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-font-weight: bold; -fx-padding: 4 0 8 0;");
        header.getChildren().addAll(
                fixedHeaderLabel("Название", COL_NAME),
                fixedHeaderLabel("Версия",   COL_VER),
                fixedHeaderLabel("Обновлён", COL_DATE),
                fixedHeaderLabel("Активен",  COL_TOGGLE)
        );

        // индикатор загрузки пока собираем данные
        ProgressIndicator loading = new ProgressIndicator();
        loading.setPrefSize(48, 48);
        StackPane loadingHolder = new StackPane(loading);
        loadingHolder.setPadding(new Insets(24));
        modsRoot.setCenter(loadingHolder);

        // модель, собираем в фоне
        record ModMeta(Path path, String fileName, boolean disabled, String displayName, String version, String lastUpdated) {}

        MCLauncher.submitBackground(() -> {
            List<ModMeta> metas = new ArrayList<>();
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(modsDir, "*.jar*")) {
                for (Path modPath : ds) {
                    try {
                        String fn = modPath.getFileName().toString();
                        boolean disabled = fn.endsWith(".disabled");
                        String display = fn.replace(".disabled", "");
                        String version = parseModVersion(modPath);
                        String lastUpdated = formatFileTime(Files.getLastModifiedTime(modPath));
                        metas.add(new ModMeta(modPath, fn, disabled, display, version == null ? "" : version, lastUpdated));
                    } catch (Exception exInner) {
                        // пропустить проблемный файл, но логнуть
                        LogManager.getLogger().warning("mods: read meta failed for " + modPath + ": " + exInner.getMessage());
                    }
                }
            } catch (Exception ex) {
                Platform.runLater(() -> LauncherUI.showError("Ошибка загрузки списка модов", ex.getMessage()));
                return;
            }

            Platform.runLater(() -> {
                modsList.getChildren().clear();
                modsList.getChildren().add(header);

                for (ModMeta m : metas) {
                    HBox row = new HBox(12);
                    row.getStyleClass().add("mod-row");
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.setMinHeight(48);

                    // placeholder icon
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
                            LauncherUI.showError("Не удалось переключить мод", ex.getMessage());
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

                    // фоновая загрузка иконки: читаем байты в фоне, создаём Image в UI-потоке
                    MCLauncher.submitBackground(() -> {
                        byte[] bytes = robustLoadIconBytes(m.path());
                        if (bytes != null && bytes.length > 0) {
                            Platform.runLater(() -> {
                                try {
                                    icon.setImage(new Image(new ByteArrayInputStream(bytes)));
                                } catch (Exception ignored) {}
                            });
                        }
                    });
                }

                ScrollPane scroll = new ScrollPane(modsList);
                scroll.setFitToWidth(true);
                modsRoot.setCenter(scroll);

                modsList.setStyle("-fx-background-color: transparent;");
                scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
                if (scroll.getContent() != null) scroll.getContent().setStyle("-fx-background-color: transparent;");

            });
        });

        return modsRoot;
    }

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

    private static final String FALLBACK_PNG_BASE64 = "iVBORw0KGgoAAAANSUhEUgAAAAoAAAAKCAYAAACNMs+9AAAAF0lEQVR42mP8z/CfAQgwYGBgYGLAAQBVmgJ3jzg9HwAAAABJRU5ErkJggg==";

    private byte[] robustLoadIconBytes(Path jarPath) {
        try (FileSystem fs = FileSystems.newFileSystem(jarPath, (ClassLoader) null)) {
            String modId = extractFirstModId(fs);
            List<IconCandidate> candidates = new ArrayList<>();

            addTomlIcons(fs, candidates);
            for (String jfn : new String[]{"fabric.mod.json", "quilt.mod.json", "mods.json", "mod.json"}) {
                addJsonIcons(fs, jfn, candidates);
            }
            addAssetsIcons(fs, modId, candidates);

            for (String rootName : new String[]{"pack.png", "icon.png", "logo.png", "mod_icon.png"}) {
                Path p = safeFsPath(fs, rootName);
                if (p != null && Files.exists(p)) candidates.add(new IconCandidate(p, 150));
            }

            for (Path root : fs.getRootDirectories()) {
                try (Stream<Path> walk = Files.walk(root, 6)) {
                    walk.filter(p -> {
                        Path fn = p.getFileName();
                        return fn != null && fn.toString().toLowerCase().endsWith(".png");
                    }).forEach(p -> {
                        String fn = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        int score = 10;
                        if (fn.contains("logo")) score += 60;
                        if (fn.contains("icon")) score += 50;
                        if (fn.contains("pack")) score += 40;
                        if (fn.contains("mod")) score += 20;
                        if (modId != null && p.toString().toLowerCase().contains(modId.toLowerCase(Locale.ROOT))) score += 40;
                        score += Math.max(0, 20 - p.getNameCount());
                        candidates.add(new IconCandidate(p, score));
                    });
                } catch (Exception ignored) {}
            }

            return candidates.stream()
                    .filter(c -> Files.exists(c.path))
                    .max(Comparator.comparingInt(c -> c.score))
                    .map(c -> {
                        try {
                            return Files.readAllBytes(c.path);
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .orElseGet(this::fallbackIconBytes);
        } catch (Exception e) {
            return fallbackIconBytes();
        }
    }

    private void addTomlIcons(FileSystem fs, List<IconCandidate> candidates) {
        try {
            Path toml = fs.getPath("META-INF", "mods.toml");
            if (Files.exists(toml)) {
                String content = Files.readString(toml, StandardCharsets.UTF_8);
                Matcher m = Pattern.compile("(?m)^\\s*logoFile\\s*=\\s*\"([^\"]+)\"").matcher(content);
                if (m.find()) {
                    Path p = safeFsPath(fs, m.group(1));
                    if (p != null && Files.exists(p)) candidates.add(new IconCandidate(p, 200));
                }
                Matcher anyPng = Pattern.compile("([\\w/\\\\\\-]+\\.(?i:png))").matcher(content);
                while (anyPng.find()) {
                    Path p = safeFsPath(fs, anyPng.group(1));
                    if (p != null && Files.exists(p)) candidates.add(new IconCandidate(p, 140));
                }
            }
        } catch (Exception ignored) {}
    }

    private void addJsonIcons(FileSystem fs, String fileName, List<IconCandidate> candidates) {
        try {
            Path json = fs.getPath(fileName);
            if (!Files.exists(json)) return;
            String content = Files.readString(json, StandardCharsets.UTF_8);

            Matcher m1 = Pattern.compile("\"icon\"\\s*:\\s*\"([^\"]+\\.png)\"", Pattern.CASE_INSENSITIVE).matcher(content);
            if (m1.find()) addIfExists(fs, m1.group(1), candidates, 190);

            Matcher mArr = Pattern.compile("\"icon\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL | Pattern.CASE_INSENSITIVE).matcher(content);
            if (mArr.find()) {
                Matcher png = Pattern.compile("\"([^\"]+\\.png)\"", Pattern.CASE_INSENSITIVE).matcher(mArr.group(1));
                if (png.find()) addIfExists(fs, png.group(1), candidates, 180);
            }

            Matcher mObj = Pattern.compile("\"icon\"\\s*:\\s*\\{([^}]+)}", Pattern.DOTALL | Pattern.CASE_INSENSITIVE).matcher(content);
            if (mObj.find()) {
                Matcher mf = Pattern.compile("\"file\"\\s*:\\s*\"([^\"]+\\.png)\"", Pattern.CASE_INSENSITIVE).matcher(mObj.group(1));
                if (mf.find()) addIfExists(fs, mf.group(1), candidates, 185);
            }
        } catch (Exception ignored) {}
    }

    private void addAssetsIcons(FileSystem fs, String modId, List<IconCandidate> candidates) {
        try {
            Path assets = fs.getPath("assets");
            if (!Files.exists(assets) || !Files.isDirectory(assets)) return;
            try (DirectoryStream<Path> ns = Files.newDirectoryStream(assets)) {
                for (Path nsDir : ns) {
                    for (String fname : new String[]{"icon.png", "logo.png", "textures/icon.png", "textures/logo.png", "textures/gui/logo.png", "textures/gui/icon.png"}) {
                        Path candidate = nsDir.resolve(fname);
                        if (Files.exists(candidate)) {
                            int score = 180 + (nsDir.getFileName().toString().equalsIgnoreCase(modId) ? 20 : 0);
                            candidates.add(new IconCandidate(candidate, score));
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private byte[] fallbackIconBytes() {
        try (InputStream res = getClass().getResourceAsStream("/img/mod_placeholder.png")) {
            if (res != null) return res.readAllBytes();
        } catch (Exception ignored) {}
        return Base64.getDecoder().decode(FALLBACK_PNG_BASE64);
    }

    private void addIfExists(FileSystem fs, String rawPath, List<IconCandidate> candidates, int score) {
        Path p = safeFsPath(fs, rawPath);
        if (p != null && Files.exists(p)) candidates.add(new IconCandidate(p, score));
    }

    private record IconCandidate(Path path, int score) {}

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
                Matcher mods = Pattern.compile("\\[\\[mods]](.*?)(?=\\n\\[\\[|$)", Pattern.DOTALL).matcher(content);
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
            Matcher mods = Pattern.compile("\\[\\[mods]](.*?)(?=\\n\\[\\[|$)", Pattern.DOTALL).matcher(content);
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
