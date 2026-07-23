// SPDX-FileCopyrightText: 2026 Raijin2312
// SPDX-License-Identifier: GPL-3.0-only

package org.architech.launcher.gui.download;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.application.Platform;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.architech.launcher.managment.DownloadSnapshot;
import org.architech.launcher.utils.Utils;

/** Steam-inspired download status panel used at the bottom of the launcher. */
public final class DownloadStatusPanel extends BorderPane {
    private static final Pattern TIMER_VALUE =
            Pattern.compile("(\\d{1,3}:\\d{2}:\\d{2}|\\d{1,3}:\\d{2})$");

    private static final String PAUSE_PATH =
            "M5 3h5v18H5zM14 3h5v18h-5z";
    private static final String PLAY_PATH =
            "M7 3v18l14-9z";
    private static final String DOWNLOAD_PATH =
            "M11 2h2v11.17l3.59-3.58L18 11l-6 6-6-6 1.41-1.41L11 13.17zM4 19h16v3H4z";
    private static final String DISK_PATH =
            "M4 3h16l2 5v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8zm1.35 2L4.2 8h15.6l-1.15-3zM6 12v7h12v-7zm8 2h2v3h-2z";
    private static final String SPEED_PATH =
            "M12 3a9 9 0 1 0 9 9 9 9 0 0 0-9-9zm0 2a7 7 0 0 1 6.06 10.5l-1.73-1a5 5 0 1 0-8.66 0l-1.73 1A7 7 0 0 1 12 5zm4.95 3.64-3.54 3.54a2 2 0 1 1-1.42-1.42l3.54-3.54z";

    private final Label elapsedLabel = new Label("00:00:00");
    private final Label statusLabel = new Label("ОЖИДАНИЕ");
    private final ProgressBar progressBar = new ProgressBar(1.0);
    private final Button pauseButton = new Button();

    private final StatValue downloadedStat = new StatValue(DOWNLOAD_PATH);
    private final StatValue diskStat = new StatValue(DISK_PATH);
    private final StatValue speedStat = new StatValue(SPEED_PATH);

    private final Runnable pauseHandler;
    private DownloadSnapshot snapshot = DownloadSnapshot.idle();
    private String operationText = "ОЖИДАНИЕ";
    private double operationProgress = 1.0;

    public DownloadStatusPanel(Label timerSource, Runnable pauseHandler) {
        this.pauseHandler = Objects.requireNonNull(pauseHandler, "pauseHandler");

        getStyleClass().add("download-panel");
        setPadding(new Insets(7, 10, 7, 12));
        setMinWidth(0);
        setPrefWidth(Region.USE_COMPUTED_SIZE);
        setMaxWidth(Double.MAX_VALUE);
        setMinHeight(72);
        setPrefHeight(72);
        setMaxHeight(72);

        elapsedLabel.getStyleClass().add("download-panel-timer");
        statusLabel.getStyleClass().add("download-panel-status");
        statusLabel.setMaxWidth(Double.MAX_VALUE);
        statusLabel.setAlignment(Pos.CENTER);

        StackPane header = new StackPane(statusLabel, elapsedLabel);
        header.setMinHeight(19);
        header.setPrefHeight(19);
        header.setMaxHeight(19);
        header.setMaxWidth(Double.MAX_VALUE);
        StackPane.setAlignment(statusLabel, Pos.CENTER);
        StackPane.setAlignment(elapsedLabel, Pos.CENTER_LEFT);

        progressBar.getStyleClass().add("download-panel-progress");
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setPrefWidth(Region.USE_COMPUTED_SIZE);
        progressBar.setMinHeight(6);
        progressBar.setPrefHeight(6);
        progressBar.setMaxHeight(6);

        GridPane stats = new GridPane();
        stats.getStyleClass().add("download-panel-stats");
        stats.setMaxWidth(Double.MAX_VALUE);
        stats.setMinHeight(22);
        stats.setPrefHeight(22);
        stats.setMaxHeight(22);
        for (int column = 0; column < 3; column++) {
            ColumnConstraints statColumn = new ColumnConstraints();
            statColumn.setPercentWidth(100.0 / 3.0);
            stats.getColumnConstraints().add(statColumn);
        }
        stats.add(downloadedStat.node(), 0, 0);
        stats.add(diskStat.node(), 1, 0);
        stats.add(speedStat.node(), 2, 0);
        GridPane.setHalignment(downloadedStat.node(), HPos.LEFT);
        GridPane.setHalignment(diskStat.node(), HPos.CENTER);
        GridPane.setHalignment(speedStat.node(), HPos.RIGHT);
        GridPane.setFillWidth(downloadedStat.node(), false);
        GridPane.setFillWidth(diskStat.node(), false);
        GridPane.setFillWidth(speedStat.node(), false);

        configurePauseButton();

        VBox content = new VBox(5, header, progressBar, stats);
        content.setFillWidth(true);
        content.setMaxWidth(Double.MAX_VALUE);

        setCenter(content);
        setRight(pauseButton);
        BorderPane.setAlignment(pauseButton, Pos.CENTER_RIGHT);
        BorderPane.setMargin(pauseButton, new Insets(0, 0, 0, 12));

        bindTimer(timerSource);
        renderSnapshot(snapshot);
    }

    public void updateProgress(String text, double progress01) {
        runOnFxThread(
                () -> {
                    operationText = normalizeStatus(text);
                    operationProgress = progress01;

                    if (!snapshot.active()) {
                        renderOperation();
                    }
                });
    }

    public void applySnapshot(DownloadSnapshot nextSnapshot) {
        Objects.requireNonNull(nextSnapshot, "nextSnapshot");
        runOnFxThread(
                () -> {
                    snapshot = nextSnapshot;
                    renderSnapshot(nextSnapshot);
                });
    }

    /** Compatibility entry point for existing launcher state code. */
    public void setDownloadActive(boolean active) {
        runOnFxThread(
                () -> {
                    pauseButton.setDisable(!active);
                    if (!active) {
                        setPauseGraphic(false);
                    }
                });
    }

    /** Compatibility entry point for existing launcher state code. */
    public void setPaused(boolean paused) {
        runOnFxThread(() -> setPauseGraphic(paused));
    }

    private void configurePauseButton() {
        pauseButton.getStyleClass().add("download-panel-pause");
        pauseButton.setAccessibleRole(AccessibleRole.BUTTON);
        pauseButton.setMinSize(42, 42);
        pauseButton.setPrefSize(42, 42);
        pauseButton.setMaxSize(42, 42);
        pauseButton.setDisable(true);
        pauseButton.setFocusTraversable(false);
        pauseButton.setOnAction(event -> pauseHandler.run());
        setPauseGraphic(false);
    }

    private void renderSnapshot(DownloadSnapshot value) {
        pauseButton.setDisable(!value.active());
        setPauseGraphic(value.paused());

        downloadedStat.set(
                formatBytes(value.bytesDownloaded()),
                " / " + formatBytes(value.bytesPlanned()));
        diskStat.set(
                formatBytes(value.bytesWritten()),
                " / " + formatBytes(value.diskBytesPlanned()));
        speedStat.set(formatRate(value.bytesPerSecond()), "");

        if (!value.active()) {
            renderOperation();
            return;
        }

        double progress = value.progress();
        progressBar.setProgress(
                progress < 0.0
                        ? ProgressIndicator.INDETERMINATE_PROGRESS
                        : Utils.clamp01(progress));

        int percent = progress < 0.0 ? 0 : (int) Math.round(progress * 100.0);
        statusLabel.setText(
                value.paused()
                        ? "ПАУЗА " + percent + "%"
                        : "СКАЧИВАНИЕ " + percent + "%");
    }

    private void renderOperation() {
        progressBar.setProgress(
                operationProgress < 0.0
                        ? ProgressIndicator.INDETERMINATE_PROGRESS
                        : Utils.clamp01(operationProgress));
        statusLabel.setText(operationText);
    }

    private void setPauseGraphic(boolean paused) {
        SVGPath icon = new SVGPath();
        icon.setContent(paused ? PLAY_PATH : PAUSE_PATH);
        icon.getStyleClass().add("download-panel-pause-icon");
        pauseButton.setGraphic(icon);

        String description = paused ? "Продолжить загрузку" : "Приостановить загрузку";
        pauseButton.setAccessibleText(description);
        pauseButton.setTooltip(new Tooltip(description));
        pauseButton.getStyleClass().remove("download-panel-resume");
        if (paused) {
            pauseButton.getStyleClass().add("download-panel-resume");
        }
    }

    private void bindTimer(Label timerSource) {
        Objects.requireNonNull(timerSource, "timerSource");
        elapsedLabel.setText(extractTimer(timerSource.getText()));
        timerSource.textProperty()
                .addListener(
                        (observable, oldValue, newValue) ->
                                elapsedLabel.setText(extractTimer(newValue)));
    }

    private static String extractTimer(String value) {
        if (value == null || value.isBlank()) {
            return "00:00:00";
        }
        Matcher matcher = TIMER_VALUE.matcher(value.trim());
        return matcher.find() ? matcher.group(1) : value.trim();
    }

    private static String normalizeStatus(String value) {
        if (value == null || value.isBlank()) {
            return "ОЖИДАНИЕ";
        }
        String normalized = value.trim();
        if (normalized.equalsIgnoreCase("Ожидание...")) {
            return "ОЖИДАНИЕ";
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private static String formatBytes(long bytes) {
        if (bytes <= 0L) {
            return "0 Б";
        }

        double value = bytes;
        String[] units = {"Б", "КБ", "МБ", "ГБ", "ТБ"};
        int unit = 0;
        while (value >= 1024.0 && unit < units.length - 1) {
            value /= 1024.0;
            unit++;
        }

        if (unit == 0) {
            return String.format(Locale.ROOT, "%.0f %s", value, units[unit]);
        }
        return String.format(Locale.ROOT, value >= 100.0 ? "%.0f %s" : "%.1f %s", value, units[unit]);
    }

    private static String formatRate(double bytesPerSecond) {
        if (bytesPerSecond <= 0.0) {
            return "0 Б/с";
        }
        return formatBytes((long) bytesPerSecond) + "/с";
    }

    private static void runOnFxThread(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    private static final class StatValue {
        private final Text primary = new Text("0 Б");
        private final Text secondary = new Text("");
        private final HBox node;

        private StatValue(String iconPath) {
            SVGPath icon = new SVGPath();
            icon.setContent(iconPath);
            icon.getStyleClass().add("download-panel-stat-icon");

            primary.getStyleClass().add("download-panel-stat-primary");
            secondary.getStyleClass().add("download-panel-stat-secondary");

            TextFlow value = new TextFlow(primary, secondary);
            value.getStyleClass().add("download-panel-stat-value");
            value.setTranslateY(1.0);

            node = new HBox(8, icon, value);
            node.getStyleClass().add("download-panel-stat");
            node.setAlignment(Pos.CENTER_LEFT);
            node.setMinWidth(0);
        }

        private HBox node() {
            return node;
        }

        private void set(String primaryValue, String secondaryValue) {
            primary.setText(primaryValue);
            secondary.setText(secondaryValue);
        }
    }
}
