package org.architech.launcher.gui.news;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.architech.launcher.ArchiTechLauncher;
import org.architech.launcher.utils.FileEntry;
import org.architech.launcher.utils.Jsons;
import org.architech.launcher.utils.Utils;
import org.architech.launcher.utils.logging.LogManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class NewsList {

    private record NewsItem(String title, String imageUrl, String shortDesc, String date) {}

    private static VBox newsListRef;

    public static ScrollPane buildNewsList() {

        List<NewsItem> items = getNewsFromServer();
        VBox list = new VBox(12);
        list.setPadding(new Insets(10));
        newsListRef = list;

        Path cacheDir = Paths.get(System.getProperty("user.home"), ".architech", "cache", "news-icons");
        try {
            Files.createDirectories(cacheDir);
        } catch (IOException e) {
            LogManager.getLogger().severe("Ошибка создания каталога кэша: " + e.getMessage());
        }

        for (NewsItem n : items) {
            BorderPane card = new BorderPane();
            card.getStyleClass().add("news-card");
            card.setPrefWidth(640);
            card.setMaxWidth(640);
            card.setMinHeight(88);

            ImageView img = new ImageView();
            img.setFitWidth(64);
            img.setFitHeight(64);
            img.setPreserveRatio(true);
            img.setSmooth(true);
            img.getStyleClass().add("news-image");

            ArchiTechLauncher.scheduledExecutor.execute(() -> {
                try {
                    String fname = Utils.sha1Hex(n.imageUrl()) + ".img";
                    Path target = cacheDir.resolve(fname);

                    FileEntry fe = new FileEntry("news-icon", n.title(), n.imageUrl(), target, 0L, null);

                    ArchiTechLauncher.DOWNLOAD_MANAGER.ensureFilePresentAndValid(fe, false);

                    if (Files.exists(target) && Files.size(target) > 0) {
                        Platform.runLater(() -> {
                            try {
                                Image imgObj = new Image(target.toUri().toString(), 64, 64, true, true);
                                if (imgObj.isError()) {
                                    LogManager.getLogger().severe("Ошибка загрузки Image: " + imgObj.getException());
                                } else {
                                    img.setImage(imgObj);
                                }
                            } catch (Exception e) {
                                LogManager.getLogger().severe("Ошибка чтения иконки из кэша: " + e.getMessage());
                            }
                        });
                    } else {
                        LogManager.getLogger().warning("Файл иконки пустой или отсутствует: " + target);
                    }
                } catch (Exception ex) {
                    LogManager.getLogger().severe("Не удалось скачать иконку новости: " + ex.getMessage());
                }
            });

            Label title = new Label(n.title());
            title.getStyleClass().add("news-title");
            title.setWrapText(true);

            Label date = new Label(n.date());
            date.getStyleClass().add("news-date");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            HBox topRow = new HBox(8, title, spacer, date);

            Label desc = new Label(n.shortDesc());
            desc.getStyleClass().add("news-desc");
            desc.setWrapText(true);
            desc.setMaxWidth(Double.MAX_VALUE);
            desc.setMaxHeight(Region.USE_PREF_SIZE);

            VBox content = new VBox(6, topRow, desc);
            content.setAlignment(Pos.TOP_LEFT);
            content.setFillWidth(true);
            HBox.setHgrow(content, Priority.ALWAYS);

            HBox row = new HBox(12, img, content);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(8));

            card.setCenter(row);

            list.getChildren().add(card);
        }

        ScrollPane sp = new ScrollPane(list);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        sp.getStyleClass().add("news-scroll");
        sp.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        if (sp.getContent() != null) sp.getContent().setStyle("-fx-background-color: transparent;");

        return sp;
    }

    private static List<NewsItem> getNewsFromServer() {
        List<NewsItem> newsItems;
        try {
            String json = Utils.readUrl(ArchiTechLauncher.BACKEND_URL + "/api/files/file/news/news.json");
            JsonNode arr = Jsons.MAPPER.readTree(json);
            List<NewsItem> tmp = new ArrayList<>();
            if (arr.isArray()) {
                for (JsonNode n : arr) {
                    String title = n.path("title").asText("");
                    String img = n.path("imageUrl").asText("");
                    String desc = n.path("shortDesc").asText("");
                    String date = n.path("date").asText("");
                    tmp.add(new NewsItem(title, img, desc, date));
                }
            }
            newsItems = List.copyOf(tmp);
        } catch (Exception ex) {
            LogManager.getLogger().warning("Не удалось загрузить новости: " + ex.getMessage());
            newsItems = List.of();
        }
        return newsItems;
    }

    public static void replayNewsAnimations() {
        if (newsListRef == null) return;
        Platform.runLater(() -> {
            int i = 0;
            for (Node node : newsListRef.getChildren()) {
                node.setOpacity(0);
                node.setTranslateY(10);

                FadeTransition ft = new FadeTransition(Duration.millis(400), node);
                ft.setFromValue(0); ft.setToValue(1);

                TranslateTransition tt = new TranslateTransition(Duration.millis(400), node);
                tt.setFromY(10); tt.setToY(0);

                ft.setDelay(Duration.millis(i * 100));
                tt.setDelay(Duration.millis(i * 100));

                new ParallelTransition(ft, tt).play();
                i++;
            }
        });
    }

}
