package org.architech.launcher.gui;

import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import org.architech.launcher.ArchiTechLauncher;
import org.architech.launcher.utils.logging.LogManager;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;

public final class BackgroundCache {
    private static final ConcurrentMap<String, Image> CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, CompletableFuture<Image>> IN_FLIGHT = new ConcurrentHashMap<>();

    private BackgroundCache() {}

    public static void preload(Path p) {
        if (p == null || !Files.exists(p)) return;
        String k = p.toAbsolutePath().toString();
        if (CACHE.containsKey(k) || IN_FLIGHT.containsKey(k)) return;
        loadImageFuture(p);
    }

    private static CompletableFuture<Image> loadImageFuture(Path p) {
        String k = p.toAbsolutePath().toString();
        return IN_FLIGHT.computeIfAbsent(k, key -> {
            CompletableFuture<byte[]> readBytes = CompletableFuture.supplyAsync(() -> {
                try {
                    return Files.readAllBytes(p);
                } catch (Exception e) {
                    LogManager.getLogger().warning("BackgroundCache: read failed for " + p + " : " + e.getMessage());
                    return null;
                }
            }, ArchiTechLauncher.backgroundExecutor);

            return readBytes.thenCompose(bytes -> {
                if (bytes == null) {
                    IN_FLIGHT.remove(k);
                    return CompletableFuture.failedFuture(new RuntimeException("read failed"));
                }
                CompletableFuture<Image> created = new CompletableFuture<>();
                Platform.runLater(() -> {
                    try {
                        Image img = new Image(new ByteArrayInputStream(bytes));
                        if (img.isError() || img.getWidth() <= 0) {
                            LogManager.getLogger().warning("BackgroundCache: decode produced invalid image for " + p);
                            created.completeExceptionally(new RuntimeException("decode failed or zero-size"));
                        } else {
                            CACHE.put(k, img);
                            created.complete(img);
                        }
                    } catch (Exception e) {
                        LogManager.getLogger().warning("BackgroundCache: decode failed for " + p + " : " + e.getMessage());
                        created.completeExceptionally(e);
                    } finally {
                        IN_FLIGHT.remove(k);
                    }
                });
                return created;
            });
        });
    }

    public static void apply(Path p, Region region) {
        if (region == null) return;
        if (p == null || !Files.exists(p)) {
            Platform.runLater(() -> region.setStyle("-fx-background-color: linear-gradient(to bottom, #1e1e1e, #2a2a2a);"));
            return;
        }
        String k = p.toAbsolutePath().toString();
        Image cached = CACHE.get(k);
        if (cached != null) {
            applyImage(region, cached);
            return;
        }

        loadImageFuture(p).thenAccept(img -> {
            if (img != null) applyImage(region, img);
        }).exceptionally(ex -> {
            LogManager.getLogger().warning("BackgroundCache.apply failed for " + p + " : " + (ex == null ? "null" : ex.getMessage()));
            Platform.runLater(() -> region.setStyle("-fx-background-color: linear-gradient(to bottom, #1e1e1e, #2a2a2a);"));
            return null;
        });
    }

    private static void applyImage(Region region, Image img) {
        if (img == null || img.isError() || img.getWidth() <= 0) {
            Platform.runLater(() -> region.setStyle("-fx-background-color: linear-gradient(to bottom, #1e1e1e, #2a2a2a);"));
            return;
        }
        Platform.runLater(() -> {
            region.setStyle(null);
            BackgroundSize bs = new BackgroundSize(100, 100, true, true, false, true);
            BackgroundImage bi = new BackgroundImage(img,
                    BackgroundRepeat.NO_REPEAT,
                    BackgroundRepeat.NO_REPEAT,
                    BackgroundPosition.CENTER,
                    bs);
            region.setBackground(new Background(bi));
        });
    }
}
