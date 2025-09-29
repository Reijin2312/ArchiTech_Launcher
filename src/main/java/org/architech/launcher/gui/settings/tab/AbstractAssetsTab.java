// file: AbstractAssetsTab.java
package org.architech.launcher.gui.settings.tab;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

public abstract class AbstractAssetsTab {
    protected static final double COL_NAME   = 320;
    protected static final double COL_VER    = 140;
    protected static final double COL_DATE   = 180;
    protected static final double COL_TOGGLE = 80;
    protected static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    protected VBox modsListRef;
    protected Node modsHeaderRef;

    protected static Pane fixedCell(javafx.scene.Node node, double width, Pos align) {
        StackPane box = new StackPane(node);
        box.setAlignment(align);
        box.setMinWidth(width); box.setPrefWidth(width); box.setMaxWidth(width);
        return box;
    }
    protected static Pane fixedHeaderLabel(String text, double width) {
        Label l = new Label(text); l.setAlignment(Pos.CENTER); return fixedCell(l, width, Pos.CENTER);
    }

    protected String formatFileTime(FileTime ft) {
        Instant ins = Instant.ofEpochMilli(ft.toMillis());
        return DATE_FMT.format(ins.atZone(ZoneId.systemDefault()));
    }

    protected boolean isGood(String v) {
        return v != null && !v.isBlank() && !v.contains("${") && !v.equalsIgnoreCase("unspecified");
    }

    protected String readVersionFromModsToml(FileSystem fs) {
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

    protected String readVersionFromJson(FileSystem fs, String fileName) {
        Path json = fs.getPath(fileName);
        if (!Files.exists(json)) return null;
        try {
            Matcher m = Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"").matcher(Files.readString(json, StandardCharsets.UTF_8));
            if (m.find()) return m.group(1);
        } catch (Exception ignored) {}
        return null;
    }

    protected String readVersionFromManifest(FileSystem fs) {
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

    protected String parseModVersion(Path jarPath) {
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

    protected String parseShaderPackVersion(Path packPath) {
        try {
            if (Files.isDirectory(packPath)) {
                Path p1 = packPath.resolve("shaderpack.properties");
                if (Files.exists(p1)) {
                    String s = Files.readString(p1, StandardCharsets.UTF_8);
                    Matcher m = Pattern.compile("(?m)^\\s*version\\s*=\\s*(.+)$").matcher(s);
                    if (m.find()) return m.group(1).trim();
                }
                Path p2 = packPath.resolve("shaderpack.txt");
                if (Files.exists(p2)) {
                    String s = Files.readString(p2, StandardCharsets.UTF_8);
                    Matcher m = Pattern.compile("(?m)^\\s*version\\s*[:=]\\s*(.+)$").matcher(s);
                    if (m.find()) return m.group(1).trim();
                }
            } else {
                try (FileSystem fs = FileSystems.newFileSystem(packPath, (ClassLoader) null)) {
                    Path p1 = fs.getPath("shaderpack.properties");
                    if (Files.exists(p1)) {
                        String s = Files.readString(p1, StandardCharsets.UTF_8);
                        Matcher m = Pattern.compile("(?m)^\\s*version\\s*=\\s*(.+)$").matcher(s);
                        if (m.find()) return m.group(1).trim();
                    }
                    Path p2 = fs.getPath("shaderpack.txt");
                    if (Files.exists(p2)) {
                        String s = Files.readString(p2, StandardCharsets.UTF_8);
                        Matcher m = Pattern.compile("(?m)^\\s*version\\s*[:=]\\s*(.+)$").matcher(s);
                        if (m.find()) return m.group(1).trim();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "—";
    }

    protected byte[] robustLoadShaderPackIconBytes(Path packPath) {
        try {
            if (Files.isDirectory(packPath)) {
                Path p = packPath.resolve("preview.png");
                if (Files.exists(p)) return Files.readAllBytes(p);
                p = packPath.resolve("pack.png");
                if (Files.exists(p)) return Files.readAllBytes(p);
            } else {
                try (FileSystem fs = FileSystems.newFileSystem(packPath, (ClassLoader) null)) {
                    Path p = fs.getPath("preview.png");
                    if (Files.exists(p)) return Files.readAllBytes(p);
                    p = fs.getPath("pack.png");
                    if (Files.exists(p)) return Files.readAllBytes(p);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    protected void playRowAnimations(VBox modsList, Node header) {
        int i = 0;
        for (Node node : modsList.getChildren()) {
            if (node == header) continue;
            node.setOpacity(0);
            node.setTranslateY(10);

            FadeTransition ft = new FadeTransition(Duration.millis(400), node);
            ft.setFromValue(0);
            ft.setToValue(1);

            TranslateTransition tt = new TranslateTransition(Duration.millis(400), node);
            tt.setFromY(10);
            tt.setToY(0);

            ft.setDelay(Duration.millis(i * 100));
            tt.setDelay(Duration.millis(i * 100));

            new ParallelTransition(ft, tt).play();
            i++;
        }
    }

    public void replayAnimations() {
        if (modsListRef == null || modsHeaderRef == null) return;
        Platform.runLater(() -> playRowAnimations(modsListRef, modsHeaderRef));
    }
}
