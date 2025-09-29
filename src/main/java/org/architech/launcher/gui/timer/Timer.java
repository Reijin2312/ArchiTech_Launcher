package org.architech.launcher.gui.timer;

import javafx.application.Platform;
import javafx.scene.control.Label;
import org.architech.launcher.ArchiTechLauncher;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class Timer {

    private static long startTimeMs;
    private static ScheduledFuture<?> timerFuture;
    public static Label timerLabel;

    public static void startTimer() {
        startTimeMs = System.currentTimeMillis();

        if (timerFuture != null && !timerFuture.isDone()) {
            timerFuture.cancel(true);
        }

        timerFuture = ArchiTechLauncher.scheduledExecutor.scheduleAtFixedRate(() -> {
            if (Thread.currentThread().isInterrupted()) return;

            long elapsed = System.currentTimeMillis() - startTimeMs;
            long sec = elapsed / 1000;
            long h = sec / 3600;
            long m = (sec % 3600) / 60;
            long s = sec % 60;

            String formatted = String.format("Времени прошло: %02d:%02d:%02d", h, m, s);
            Platform.runLater(() -> timerLabel.setText(formatted));
        }, 0, 1, TimeUnit.SECONDS);
    }

    public static void stopTimer() {
        if (timerFuture != null && !timerFuture.isDone()) {
            timerFuture.cancel(true);
            timerFuture = null;
        }
        startTimeMs = System.currentTimeMillis();
        Platform.runLater(() -> timerLabel.setText("Времени прошло: 00:00:00"));
    }

}
