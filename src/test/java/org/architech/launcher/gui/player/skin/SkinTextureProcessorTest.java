// SPDX-FileCopyrightText: 2026 Raijin2312
// SPDX-License-Identifier: GPL-3.0-only

package org.architech.launcher.gui.player.skin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.Test;

class SkinTextureProcessorTest {
    @Test
    void detectsSlimArmsFromTransparentUnusedColumns() {
        WritableImage source = opaqueImage(64, 64, 0xff6a7b8c);
        PixelWriter writer = source.getPixelWriter();
        clear(writer, 50, 16, 2, 4);
        clear(writer, 54, 20, 2, 12);
        clear(writer, 42, 48, 2, 4);
        clear(writer, 46, 52, 2, 12);

        SkinTextureProcessor.ProcessedSkin result = SkinTextureProcessor.process(source);

        assertTrue(result.slim());
        assertEquals(512, (int) result.texture().getWidth());
        assertEquals(512, (int) result.texture().getHeight());
    }

    @Test
    void keepsOpaqueClassicArmsClassic() {
        WritableImage source = opaqueImage(64, 64, 0xff6a7b8c);

        SkinTextureProcessor.ProcessedSkin result = SkinTextureProcessor.process(source);

        assertFalse(result.slim());
    }

    @Test
    void convertsLegacyLeftLegByMirroringRightLegAtlas() {
        WritableImage source = opaqueImage(64, 32, 0xff213141);
        source.getPixelWriter().setArgb(4, 16, 0xffff0044);
        source.getPixelWriter().setArgb(7, 16, 0xff00dd88);

        SkinTextureProcessor.ProcessedSkin result = SkinTextureProcessor.process(source);

        int outputScale = (int) result.texture().getWidth() / 64;
        assertEquals(
                0xff00dd88,
                result.texture().getPixelReader().getArgb(20 * outputScale, 48 * outputScale));
        assertEquals(
                0xffff0044,
                result.texture().getPixelReader().getArgb(23 * outputScale, 48 * outputScale));
        assertFalse(result.slim());
    }

    @Test
    void acceptsHighDefinitionSkinsWithoutResamplingTheirAtlas() {
        WritableImage source = opaqueImage(128, 128, 0xff918273);

        SkinTextureProcessor.ProcessedSkin result = SkinTextureProcessor.process(source);

        assertEquals(512, (int) result.texture().getWidth());
        assertFalse(result.slim());
    }

    private static WritableImage opaqueImage(int width, int height, int color) {
        WritableImage image = new WritableImage(width, height);
        PixelWriter writer = image.getPixelWriter();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                writer.setArgb(x, y, color);
            }
        }
        return image;
    }

    private static void clear(PixelWriter writer, int x, int y, int width, int height) {
        for (int yy = y; yy < y + height; yy++) {
            for (int xx = x; xx < x + width; xx++) {
                writer.setArgb(xx, yy, 0);
            }
        }
    }
}
