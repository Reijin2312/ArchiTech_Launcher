package org.architech.launcher.gui;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URL;

public final class ElyHead {
    private ElyHead() {}

    public static Image fromEly(String username, int size) {
        try {
            // Скачиваем полный скин
            String url = "https://skinsystem.ely.by/skins/" + username;
            InputStream is = new URL(url).openStream();
            BufferedImage skin = ImageIO.read(is);
            is.close();

            if (skin == null) return null;

            // Вырезаем голову (8x8 пикселей)
            BufferedImage head = skin.getSubimage(8, 8, 8, 8);

            // Проверяем слой шляпы (overlay)
            BufferedImage hat = skin.getSubimage(40, 8, 8, 8);

            // Итоговое изображение 8x8 с наложением
            BufferedImage result = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = result.createGraphics();
            g.drawImage(head, 0, 0, null);
            g.drawImage(hat, 0, 0, null); // альфа-канал сохранится
            g.dispose();

            // Масштабируем до нужного размера
            BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = scaled.createGraphics();
            g2.drawImage(result, 0, 0, size, size, null);
            g2.dispose();

            // Переводим в JavaFX Image
            WritableImage fxImage = SwingFXUtils.toFXImage(scaled, null);
            return fxImage;
        } catch (Exception e) {
            return null;
        }
    }
}
