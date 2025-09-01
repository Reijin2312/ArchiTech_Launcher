package org.architech.launcher.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import static org.architech.launcher.MCLauncher.CONFIG_PATH;
import static org.architech.launcher.MCLauncher.GAME_DIR;

public class LauncherSettingsUI {
    private final Stage stage;
    private final Scene parentScene;

    private TextField gameDirField;
    private TextField javaField;
    private ComboBox<String> gpuCombo;

    private Slider ramSlider;
    private Label ramValueLabel;

    private CheckBox closeOnLaunch;
    private TextField widthField;
    private TextField heightField;

    private ComboBox<String> languageCombo;
    private ComboBox<String> themeCombo;
    private CheckBox fullscreenCheck;
    private Spinner<Integer> fpsSpinner;
    private CheckBox autoUpdate;
    private TextArea jvmArgsArea;
    private TextField proxyField;
    private Spinner<Integer> netTimeoutSpinner;
    private Slider soundVolumeSlider;
    private Slider scaleSlider;

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public LauncherSettingsUI(Stage stage, Scene parentScene) {
        this.stage = stage;
        this.parentScene = parentScene;
    }

    public void show() {
        double w = (stage.getScene() != null ? stage.getScene().getWidth() : parentScene.getWidth());
        double h = (stage.getScene() != null ? stage.getScene().getHeight() : parentScene.getHeight());

        BorderPane root = new BorderPane();
        root.getStyleClass().add("settings-pane");

        Label title = new Label("Настройки лаунчера и игры");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        BorderPane.setAlignment(title, Pos.CENTER);
        BorderPane.setMargin(title, new Insets(16,16,8,16));
        root.setTop(title);

        VBox box = new VBox(14);
        box.setPadding(new Insets(0,16,16,16));
        box.getStyleClass().add("settings-card");

        // ---------- СИСТЕМА ----------
        box.getChildren().add(categoryHeader("Система"));

        HBox gameDirRow = leftRow("Путь установки игры:", gameDirField = new TextField(GAME_DIR.toAbsolutePath().toString()), true);
        Button browseGameDir = new Button("...");
        browseGameDir.getStyleClass().add("lookup");
        gameDirRow.getChildren().add(browseGameDir);
        browseGameDir.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Выберите папку игры");
            java.io.File f = chooser.showDialog(stage);
            if (f != null) gameDirField.setText(f.getAbsolutePath());
        });
        box.getChildren().add(gameDirRow);

        HBox javaRow = leftRow("Java:", javaField = new TextField(System.getProperty("java.home")), true);
        Button browseJava = new Button("...");
        browseJava.getStyleClass().add("lookup");
        javaRow.getChildren().add(browseJava);
        browseJava.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Выберите папку Java");
            java.io.File f = chooser.showDialog(stage);
            if (f != null) javaField.setText(f.getAbsolutePath());
        });
        box.getChildren().add(javaRow);

        box.getChildren().add(leftRow("Видеокарта:", gpuCombo = new ComboBox<>(), false));
        List<String> gpus = detectGPUs();
        if (gpus.isEmpty()) gpuCombo.getItems().add("Автоматический выбор");
        else gpuCombo.getItems().addAll(gpus);
        gpuCombo.getSelectionModel().selectFirst();

        // ---------- ИНТЕРФЕЙС ----------
        box.getChildren().add(categoryHeader("Интерфейс"));

        box.getChildren().add(leftRow("Язык:", languageCombo = new ComboBox<>(), false));
        languageCombo.getItems().addAll("Русский", "English");
        languageCombo.getSelectionModel().selectFirst();

        box.getChildren().add(leftRow("Тема:", themeCombo = new ComboBox<>(), false));
        themeCombo.getItems().addAll("Тёмная", "Светлая");
        themeCombo.getSelectionModel().selectFirst();

        HBox scaleRow = leftRow("Масштаб UI:", scaleSlider = new Slider(50,200,100), false);
        Label scaleVal = new Label("100%");
        scaleSlider.valueProperty().addListener((obs, ov, nv) -> {
            double s = nv.doubleValue();
            scaleVal.setText((int) s + "%");
            root.setScaleX(s/100.0);
            root.setScaleY(s/100.0);
        });
        scaleRow.getChildren().add(scaleVal);
        box.getChildren().add(scaleRow);

        // ---------- ИГРА ----------
        box.getChildren().add(categoryHeader("Игра"));

        autoUpdate = new CheckBox("Автоматически обновлять клиент");
        fullscreenCheck = new CheckBox("Полноэкранный режим");
        box.getChildren().addAll(autoUpdate, fullscreenCheck);

        box.getChildren().add(leftRow("Лимит FPS:", fpsSpinner = new Spinner<>(30,240,60,1), false));
        fpsSpinner.setEditable(true);

        HBox ramRow = leftRow("Память (МБ):", ramSlider = new Slider(512,32768,2048), false);
        ramSlider.setBlockIncrement(256);
        ramSlider.setSnapToTicks(true);
        ramValueLabel = new Label("2048");
        ramSlider.valueProperty().addListener((o,ov,nv)-> ramValueLabel.setText(String.valueOf(roundRam(nv.intValue()))));
        ramRow.getChildren().add(ramValueLabel);
        box.getChildren().add(ramRow);

        closeOnLaunch = new CheckBox("Закрывать лаунчер после запуска");
        box.getChildren().add(closeOnLaunch);

        HBox winRow = leftRow("Размер окна:", new HBox(6, widthField = new TextField("854"), new Label("x"), heightField = new TextField("480")), true);
        box.getChildren().add(winRow);

        // ---------- СЕТЬ ----------
        box.getChildren().add(categoryHeader("Сеть"));
        box.getChildren().add(leftRow("Прокси:", proxyField = new TextField(), true));
        box.getChildren().add(leftRow("HTTP таймаут (сек):", netTimeoutSpinner = new Spinner<>(5,120,30,5), false));

        // ---------- ДОПОЛНИТЕЛЬНО ----------
        box.getChildren().add(categoryHeader("Дополнительно"));
        box.getChildren().add(leftRow("Громкость:", soundVolumeSlider = new Slider(0,100,80), false));

        Label jvmLabel = new Label("Аргументы JVM:");
        jvmArgsArea = new TextArea();
        jvmArgsArea.setPrefRowCount(3);
        VBox.setMargin(jvmLabel, new Insets(8,0,0,0));
        box.getChildren().addAll(jvmLabel, jvmArgsArea);

        ScrollPane scroll = new ScrollPane(box);
        scroll.setFitToWidth(true);
        root.setCenter(scroll);

        Button back = new Button("Назад");
        back.setOnAction(e -> stage.setScene(parentScene));
        Button save = new Button("Сохранить");
        save.setOnAction(e -> { saveConfig(); stage.setScene(parentScene); });
        HBox bottom = new HBox(10, back, save);
        bottom.setAlignment(Pos.CENTER_RIGHT);
        bottom.setPadding(new Insets(12,16,16,16));
        root.setBottom(bottom);

        Scene settingsScene = new Scene(root, w, h);
        settingsScene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/styles.css")).toExternalForm());
        stage.setScene(settingsScene);
        stage.setMinWidth(600);
        stage.setMinHeight(500);

        loadConfig();
    }
    
    private HBox leftRow(String label, javafx.scene.Node control, boolean grow) {
        Label lbl = new Label(label);
        HBox row = new HBox(10, lbl, control);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(control, grow ? Priority.ALWAYS : Priority.NEVER);
        if (control instanceof TextField) ((TextField)control).setPrefHeight(28);

        if (control instanceof ComboBox<?> cb) {
            cb.setMinHeight(28);
            cb.setPrefHeight(28);
            cb.setMaxHeight(28);
        }
        if (control instanceof Spinner<?> sp) {
            sp.setMinHeight(28);
            sp.setPrefHeight(28);
            sp.setMaxHeight(28);
        }

        return row;
    }

    private VBox categoryHeader(String name) {
        Label label = new Label(name);
        label.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        HBox line = new HBox();
        line.setStyle("-fx-background-color: rgba(255,255,255,0.25);");
        line.setMinHeight(1);
        VBox.setMargin(label, new Insets(12,0,2,0));
        VBox wrap = new VBox(label, line);
        wrap.setAlignment(Pos.CENTER);
        return wrap;
    }

    private void saveConfig() {
        Map<String,Object> cfg = new LinkedHashMap<>();
        cfg.put("gameDir", gameDirField.getText());
        cfg.put("javaPath", javaField.getText());
        cfg.put("gpu", gpuCombo.getValue());
        cfg.put("maxMemory", roundRam((int) ramSlider.getValue()));
        cfg.put("closeOnLaunch", closeOnLaunch.isSelected());
        cfg.put("winWidth", widthField.getText());
        cfg.put("winHeight", heightField.getText());
        cfg.put("language", languageCombo.getValue());
        cfg.put("theme", themeCombo.getValue());
        cfg.put("fullscreen", fullscreenCheck.isSelected());
        cfg.put("fpsLimit", fpsSpinner.getValue());
        cfg.put("autoUpdate", autoUpdate.isSelected());
        cfg.put("jvmArgs", jvmArgsArea.getText());
        cfg.put("proxy", proxyField.getText());
        cfg.put("netTimeout", netTimeoutSpinner.getValue());
        cfg.put("soundVolume", (int) soundVolumeSlider.getValue());
        cfg.put("uiScale", (int) scaleSlider.getValue());
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer w = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(cfg, w);
            }
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Ошибка сохранения файла настроек: " + e.getMessage()).showAndWait();
        }
    }

    private void loadConfig() {
        if (!Files.exists(CONFIG_PATH)) return;
        try (Reader r = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
            Map<?,?> cfg = GSON.fromJson(r, Map.class);
            if (cfg == null) return;
            if (cfg.containsKey("gameDir")) gameDirField.setText(String.valueOf(cfg.get("gameDir")));
            if (cfg.containsKey("javaPath")) javaField.setText(String.valueOf(cfg.get("javaPath")));
            if (cfg.containsKey("gpu")) gpuCombo.setValue(String.valueOf(cfg.get("gpu")));
            if (cfg.containsKey("maxMemory")) ramSlider.setValue(((Number)cfg.get("maxMemory")).doubleValue());
            if (cfg.containsKey("closeOnLaunch")) closeOnLaunch.setSelected((Boolean)cfg.get("closeOnLaunch"));
            if (cfg.containsKey("winWidth")) widthField.setText(String.valueOf(cfg.get("winWidth")));
            if (cfg.containsKey("winHeight")) heightField.setText(String.valueOf(cfg.get("winHeight")));
            if (cfg.containsKey("language")) languageCombo.setValue(String.valueOf(cfg.get("language")));
            if (cfg.containsKey("theme")) themeCombo.setValue(String.valueOf(cfg.get("theme")));
            if (cfg.containsKey("fullscreen")) fullscreenCheck.setSelected((Boolean)cfg.get("fullscreen"));
            if (cfg.containsKey("fpsLimit")) fpsSpinner.getValueFactory().setValue(((Number)cfg.get("fpsLimit")).intValue());
            if (cfg.containsKey("autoUpdate")) autoUpdate.setSelected((Boolean)cfg.get("autoUpdate"));
            if (cfg.containsKey("jvmArgs")) jvmArgsArea.setText(String.valueOf(cfg.get("jvmArgs")));
            if (cfg.containsKey("proxy")) proxyField.setText(String.valueOf(cfg.get("proxy")));
            if (cfg.containsKey("netTimeout")) netTimeoutSpinner.getValueFactory().setValue(((Number)cfg.get("netTimeout")).intValue());
            if (cfg.containsKey("soundVolume")) soundVolumeSlider.setValue(((Number)cfg.get("soundVolume")).doubleValue());
            if (cfg.containsKey("uiScale")) scaleSlider.setValue(((Number)cfg.get("uiScale")).doubleValue());
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Ошибка загрузки файла настроек: " + e.getMessage()).showAndWait();
        }
    }

    private int roundRam(int raw) { return (int)Math.round(raw/256.0)*256; }

    private static List<String> detectGPUs() {
        List<String> result = new ArrayList<>();
        try {
            Process p = new ProcessBuilder("wmic","path","win32_VideoController","get","Name").start();
            try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (!line.trim().isEmpty() && !line.toLowerCase(Locale.ROOT).contains("name")) result.add(line.trim());
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

public static void createDefaultConfigIfMissing() {
        try {
            if (Files.exists(CONFIG_PATH)) return;

            Map<String, Object> def = new LinkedHashMap<>();

            def.put("gameDir", GAME_DIR.toAbsolutePath().toString());
            def.put("javaPath", System.getProperty("java.home"));

            List<String> gpus = detectGPUs();
            def.put("gpu", (gpus.isEmpty() ? "Автоматический выбор" : gpus.getFirst()));

            int recommended = getRecommended();
            def.put("maxMemory", recommended);

            def.put("closeOnLaunch", false);

            try {
                java.awt.Dimension screen = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
                int width = Math.max(854, Math.min(screen.width - 200, 1920));
                int height = Math.max(480, Math.min(screen.height - 200, 1080));
                def.put("winWidth", String.valueOf(width));
                def.put("winHeight", String.valueOf(height));
            } catch (Throwable t) {
                def.put("winWidth", "854");
                def.put("winHeight", "480");
            }


            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer w = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(def, w);
            }
        } catch (IOException e) {
            throw new RuntimeException("Не удалось создать дефолтный конфиг: " + e.getMessage(), e);
        }
    }

    private static int getRecommended() {
        int recommended = 2048;
        try {
            com.sun.management.OperatingSystemMXBean os =
                    (com.sun.management.OperatingSystemMXBean)
                            java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            long totalMb = os.getTotalMemorySize() / (1024L * 1024L);
            if (totalMb > 0) {
                recommended = (int) Math.max(1024, Math.min(totalMb / 4, 32768));
            }
        } catch (Throwable ignored) {
        }
        return recommended;
    }
}

