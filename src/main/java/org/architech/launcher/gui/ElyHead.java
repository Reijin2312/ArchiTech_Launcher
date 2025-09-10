package org.architech.launcher.gui;

import javafx.scene.image.Image;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;

public final class ElyHead {
    private ElyHead() {}

    public static Image fromEly(String username, int size) {
        try {
            String url = "http://skinsystem.ely.by/skins/" + username +".png";
            InputStream is = URI.create(url).toURL().openStream();
            BufferedImage skin = ImageIO.read(is);
            is.close();

            if (skin == null) return null;

            BufferedImage head = skin.getSubimage(8, 8, 8, 8);

            BufferedImage hat = skin.getSubimage(40, 8, 8, 8);

            BufferedImage result = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = result.createGraphics();
            g.drawImage(head, 0, 0, null);
            g.drawImage(hat, 0, 0, null);
            g.dispose();

            BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = scaled.createGraphics();
            g2.drawImage(result, 0, 0, size, size, null);
            g2.dispose();

            ByteArrayOutputStream os = new ByteArrayOutputStream();
            ImageIO.write(scaled, "png", os);
            ByteArrayInputStream is2 = new ByteArrayInputStream(os.toByteArray());
            return new Image(is2);
        } catch (Exception e) {
            return null;
        }
    }
}
