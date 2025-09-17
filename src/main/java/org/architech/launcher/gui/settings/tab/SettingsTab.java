package org.architech.launcher.gui.settings.tab;

import com.sun.management.OperatingSystemMXBean;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.architech.launcher.utils.Jsons;
import org.architech.launcher.utils.logging.LogManager;
import org.architech.launcher.utils.Utils;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import static org.architech.launcher.ArchiTechLauncher.*;

public class SettingsTab {
    private final Stage stage;

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

    public SettingsTab(Stage stage) { this.stage = stage; }

    public Parent createContent() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("settings-pane");

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

        languageCombo.valueProperty().addListener((obs, o, n) -> saveConfig());
        themeCombo.valueProperty().addListener((obs, o, n) -> saveConfig());
        ramSlider.valueProperty().addListener((obs, o, n) -> saveConfig());
        soundVolumeSlider.valueProperty().addListener((obs, o, n) -> saveConfig());
        scaleSlider.valueProperty().addListener((obs, o, n) -> saveConfig());
        gpuCombo.valueProperty().addListener((obs, o, n) -> saveConfig());
        fpsSpinner.valueProperty().addListener((obs, o, n) -> saveConfig());
        netTimeoutSpinner.valueProperty().addListener((obs, o, n) -> saveConfig());
        closeOnLaunch.selectedProperty().addListener((obs, o, n) -> saveConfig());
        fullscreenCheck.selectedProperty().addListener((obs, o, n) -> saveConfig());
        autoUpdate.selectedProperty().addListener((obs, o, n) -> saveConfig());
        gameDirField.textProperty().addListener((obs, o, n) -> saveConfig());
        javaField.textProperty().addListener((obs, o, n) -> saveConfig());
        widthField.textProperty().addListener((obs, o, n) -> saveConfig());
        heightField.textProperty().addListener((obs, o, n) -> saveConfig());
        proxyField.textProperty().addListener((obs, o, n) -> saveConfig());
        jvmArgsArea.textProperty().addListener((obs, o, n) -> saveConfig());

        loadConfig();
        return root;
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
                Jsons.MAPPER.writerWithDefaultPrettyPrinter().writeValue(w, cfg);
            }
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Ошибка сохранения файла настроек: " + e.getMessage()).showAndWait();
        }
    }

    private void loadConfig() {
        if (!Files.exists(CONFIG_PATH)) return;
        try (Reader r = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
            Map<?,?> cfg = Jsons.MAPPER.readValue(r, Map.class);
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

    public static void createDefaultConfigIfMissing() {
        try {
            if (Files.exists(CONFIG_PATH)) return;

            LogManager.getLogger().info("Не найден файл настроек, создаю...");

            Map<String, Object> def = new LinkedHashMap<>();

            def.put("gameDir", GAME_DIR.toAbsolutePath().toString());

            String type = Utils.isWindows() ? "java.exe" : "java";

            Path systemJavaDir = Path.of(System.getProperty("java.home"));

            if (Files.isExecutable(systemJavaDir.resolve("bin").resolve(type))) {
                def.put("javaPath", systemJavaDir.toString());
            } else {
                LogManager.getLogger().severe("Не найдена java");
                Path base = Objects.requireNonNull(findJava21());
                Path candidate = base.resolve("bin").resolve(type);
                if (Files.isExecutable(candidate)) {
                    def.put("javaPath", base.toString());
                } else {
                    LogManager.getLogger().severe("Не найдена java в " + base);
                    throw new IllegalStateException("Не найдена java в " + base);
                }
            }

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
                Jsons.MAPPER.writerWithDefaultPrettyPrinter().writeValue(w, def);
            }
        } catch (IOException e) {
            LogManager.getLogger().severe("Не удалось создать файл настроек: " + e.getMessage());
            throw new RuntimeException("Не удалось создать дефолтный конфиг: " + e.getMessage(), e);
        }
    }

    public static Path findJava21() {
        String[] searchDirs = {"C:/Program Files/Java", "C:/Program Files (x86)/Java", "/usr/lib/jvm", "/Library/Java/JavaVirtualMachines"};
        for (String base : searchDirs) {
            File dir = new File(base);
            if (!dir.exists()) continue;
            File[] subdirs = dir.listFiles(File::isDirectory);
            if (subdirs == null) continue;
            for (File sd : subdirs) {
                Path javaBin = Paths.get(sd.getAbsolutePath(), "bin", Utils.isWindows() ? "java.exe" : "java");
                if (!Files.exists(javaBin)) continue;
                try {
                    Process p = new ProcessBuilder(javaBin.toString(), "-version")
                            .redirectErrorStream(true)
                            .start();
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            if (line.contains("version")) {
                                int i1 = line.indexOf('"');
                                int i2 = line.indexOf('"', i1 + 1);
                                if (i1 >= 0 && i2 > i1) {
                                    String ver = line.substring(i1 + 1, i2);
                                    if (ver.startsWith("21")) return Path.of(sd.getAbsolutePath());
                                }
                            }
                        }
                    }
                } catch (IOException ex) {
                    LogManager.getLogger().severe("Ошибка нахождения Java 21 " + ex.getMessage());
                }
            }
        }
        return null;
    }

    private static List<String> detectGPUs() {
        List<String> result = new ArrayList<>();
        String os = System.getProperty("os.name").toLowerCase();

        try {
            Process p;
            if (os.contains("win")) {
                p = new ProcessBuilder("wmic", "path", "win32_VideoController", "get", "Name").start();
            } else if (os.contains("mac")) {
                p = new ProcessBuilder("system_profiler", "SPDisplaysDataType").start();
            } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
                p = new ProcessBuilder("lspci").start();
            } else {
                throw new UnsupportedOperationException("Неизвестная ОС: " + os);
            }

            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    if (os.contains("win")) {
                        if (!line.toLowerCase(Locale.ROOT).contains("name")) result.add(line);
                    } else if (os.contains("mac")) {
                        if (line.startsWith("Chipset Model:") || line.startsWith("Graphics:")) {
                            result.add(line.split(":")[1].trim());
                        }
                    } else {
                        if (line.toLowerCase().contains("vga") || line.toLowerCase().contains("3d")) {
                            String[] parts = line.split(":", 2);
                            if (parts.length == 2) result.add(parts[1].trim());
                        }
                    }
                }
            }
        } catch (Exception ex) {
            LogManager.getLogger().severe("Ошибка обнаружения GPU: " + ex.getMessage());
        }

        return result;
    }

    private static int getRecommended() {
        int recommended = 2048;
        try {
            OperatingSystemMXBean os = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            long totalMb = os.getTotalMemorySize() / (1024L * 1024L);
            if (totalMb > 0) {
                recommended = (int) Math.max(1024, Math.min(totalMb / 4, 32768));
            }
        } catch (Throwable ignored) {
        }
        return recommended;
    }
}

