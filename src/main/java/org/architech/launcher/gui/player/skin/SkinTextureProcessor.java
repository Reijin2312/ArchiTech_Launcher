// SPDX-FileCopyrightText: 2026 Raijin2312
// SPDX-License-Identifier: GPL-3.0-only

package org.architech.launcher.gui.player.skin;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

/**
 * Normalizes classic and modern Minecraft skins.
 *
 * <p>The legacy conversion, opaque-overlay repair, and slim-arm detection are
 * adapted from bs-community/skinview-utils (MIT).
 */
final class SkinTextureProcessor {
    private static final int LOGICAL_SKIN_SIZE = 64;

    private SkinTextureProcessor() {}

    static ProcessedSkin process(Image source) {
        if (source == null || source.isError() || source.getPixelReader() == null) {
            throw new IllegalArgumentException("Skin image could not be decoded");
        }

        int width = (int) source.getWidth();
        int height = (int) source.getHeight();
        if (width < LOGICAL_SKIN_SIZE
                || width % LOGICAL_SKIN_SIZE != 0
                || (height != width && height * 2 != width)) {
            throw new IllegalArgumentException("Unsupported skin size: " + width + "x" + height);
        }

        int scale = width / LOGICAL_SKIN_SIZE;
        WritableImage normalized = new WritableImage(width, width);
        copy(source.getPixelReader(), normalized.getPixelWriter(), 0, 0, width, height, 0, 0);

        boolean legacy = height * 2 == width;
        if (legacy) {
            convertLegacySkin(normalized, scale);
        }
        repairOpaqueOverlay(normalized, scale, !legacy);

        boolean slim = !legacy && inferSlim(normalized.getPixelReader(), scale);
        return new ProcessedSkin(upscaleForNearestFiltering(normalized), slim, scale);
    }

    static Image createFallbackSkin() {
        WritableImage image = new WritableImage(LOGICAL_SKIN_SIZE, LOGICAL_SKIN_SIZE);
        PixelWriter writer = image.getPixelWriter();
        for (int y = 0; y < LOGICAL_SKIN_SIZE; y++) {
            for (int x = 0; x < LOGICAL_SKIN_SIZE; x++) {
                int checker = ((x / 4) + (y / 4)) & 1;
                writer.setArgb(x, y, checker == 0 ? 0xff4caf50 : 0xff3d8f41);
            }
        }
        repairOpaqueOverlay(image, 1, true);
        return upscaleForNearestFiltering(image);
    }

    private static void convertLegacySkin(WritableImage image, int scale) {
        PixelReader source = image.getPixelReader();
        PixelWriter target = image.getPixelWriter();

        mirrorRegion(source, target, scale, 4, 16, 4, 4, 20, 48);
        mirrorRegion(source, target, scale, 8, 16, 4, 4, 24, 48);
        mirrorRegion(source, target, scale, 0, 20, 4, 12, 24, 52);
        mirrorRegion(source, target, scale, 4, 20, 4, 12, 20, 52);
        mirrorRegion(source, target, scale, 8, 20, 4, 12, 16, 52);
        mirrorRegion(source, target, scale, 12, 20, 4, 12, 28, 52);

        mirrorRegion(source, target, scale, 44, 16, 4, 4, 36, 48);
        mirrorRegion(source, target, scale, 48, 16, 4, 4, 40, 48);
        mirrorRegion(source, target, scale, 40, 20, 4, 12, 40, 52);
        mirrorRegion(source, target, scale, 44, 20, 4, 12, 36, 52);
        mirrorRegion(source, target, scale, 48, 20, 4, 12, 32, 52);
        mirrorRegion(source, target, scale, 52, 20, 4, 12, 44, 52);
    }

    private static void mirrorRegion(
            PixelReader source,
            PixelWriter target,
            int scale,
            int sourceX,
            int sourceY,
            int width,
            int height,
            int targetX,
            int targetY) {
        int pixelWidth = width * scale;
        int pixelHeight = height * scale;
        int sourcePixelX = sourceX * scale;
        int sourcePixelY = sourceY * scale;
        int targetPixelX = targetX * scale;
        int targetPixelY = targetY * scale;

        int[] pixels = new int[pixelWidth * pixelHeight];
        for (int y = 0; y < pixelHeight; y++) {
            for (int x = 0; x < pixelWidth; x++) {
                pixels[y * pixelWidth + x] = source.getArgb(sourcePixelX + x, sourcePixelY + y);
            }
        }
        for (int y = 0; y < pixelHeight; y++) {
            for (int x = 0; x < pixelWidth; x++) {
                target.setArgb(
                        targetPixelX + x,
                        targetPixelY + y,
                        pixels[y * pixelWidth + pixelWidth - 1 - x]);
            }
        }
    }

    private static void repairOpaqueOverlay(WritableImage image, int scale, boolean modern) {
        PixelReader reader = image.getPixelReader();
        int checkedHeight = modern ? imageHeight(image) : imageHeight(image) / 2;
        if (hasTransparency(reader, 0, 0, imageWidth(image), checkedHeight)) {
            return;
        }

        PixelWriter writer = image.getPixelWriter();
        clear(writer, scale, 40, 0, 8, 8);
        clear(writer, scale, 48, 0, 8, 8);
        clear(writer, scale, 32, 8, 8, 8);
        clear(writer, scale, 40, 8, 8, 8);
        clear(writer, scale, 48, 8, 8, 8);
        clear(writer, scale, 56, 8, 8, 8);

        if (!modern) {
            return;
        }

        clearCuboidOverlay(writer, scale, 0, 32, 4, 12, 4);
        clearCuboidOverlay(writer, scale, 16, 32, 8, 12, 4);
        clearCuboidOverlay(writer, scale, 40, 32, 4, 12, 4);
        clearCuboidOverlay(writer, scale, 0, 48, 4, 12, 4);
        clearCuboidOverlay(writer, scale, 48, 48, 4, 12, 4);
    }

    private static void clearCuboidOverlay(
            PixelWriter writer, int scale, int u, int v, int width, int height, int depth) {
        clear(writer, scale, u + depth, v, width, depth);
        clear(writer, scale, u + width + depth, v, width, depth);
        clear(writer, scale, u, v + depth, depth, height);
        clear(writer, scale, u + depth, v + depth, width, height);
        clear(writer, scale, u + width + depth, v + depth, depth, height);
        clear(writer, scale, u + width + depth * 2, v + depth, width, height);
    }

    private static void clear(PixelWriter writer, int scale, int x, int y, int width, int height) {
        for (int yy = y * scale; yy < (y + height) * scale; yy++) {
            for (int xx = x * scale; xx < (x + width) * scale; xx++) {
                writer.setArgb(xx, yy, 0);
            }
        }
    }

    private static boolean inferSlim(PixelReader reader, int scale) {
        Region[] unused = {
            new Region(50, 16, 2, 4),
            new Region(54, 20, 2, 12),
            new Region(42, 48, 2, 4),
            new Region(46, 52, 2, 12)
        };

        for (Region region : unused) {
            if (hasTransparency(
                    reader,
                    region.x * scale,
                    region.y * scale,
                    region.width * scale,
                    region.height * scale)) {
                return true;
            }
        }
        return allColor(reader, unused, scale, 0xff000000)
                || allColor(reader, unused, scale, 0xffffffff);
    }

    private static boolean allColor(PixelReader reader, Region[] regions, int scale, int color) {
        for (Region region : regions) {
            for (int y = region.y * scale; y < (region.y + region.height) * scale; y++) {
                for (int x = region.x * scale; x < (region.x + region.width) * scale; x++) {
                    if (reader.getArgb(x, y) != color) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean hasTransparency(
            PixelReader reader, int x, int y, int width, int height) {
        for (int yy = y; yy < y + height; yy++) {
            for (int xx = x; xx < x + width; xx++) {
                if ((reader.getArgb(xx, yy) >>> 24) != 0xff) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Image upscaleForNearestFiltering(Image source) {
        int width = imageWidth(source);
        if (width >= 512) {
            return source;
        }
        int multiplier = Math.max(1, 512 / width);
        WritableImage result = new WritableImage(width * multiplier, width * multiplier);
        PixelReader reader = source.getPixelReader();
        PixelWriter writer = result.getPixelWriter();
        for (int y = 0; y < width; y++) {
            for (int x = 0; x < width; x++) {
                int color = reader.getArgb(x, y);
                for (int dy = 0; dy < multiplier; dy++) {
                    for (int dx = 0; dx < multiplier; dx++) {
                        writer.setArgb(x * multiplier + dx, y * multiplier + dy, color);
                    }
                }
            }
        }
        return result;
    }

    private static void copy(
            PixelReader source,
            PixelWriter target,
            int sourceX,
            int sourceY,
            int width,
            int height,
            int targetX,
            int targetY) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                target.setArgb(targetX + x, targetY + y, source.getArgb(sourceX + x, sourceY + y));
            }
        }
    }

    private static int imageWidth(Image image) {
        return (int) image.getWidth();
    }

    private static int imageHeight(Image image) {
        return (int) image.getHeight();
    }

    record ProcessedSkin(Image texture, boolean slim, int atlasScale) {}

    private record Region(int x, int y, int width, int height) {}
}
