// SPDX-FileCopyrightText: 2026 Raijin2312
// SPDX-License-Identifier: GPL-3.0-only

package org.architech.launcher.gui.player.skin.item;

import static org.architech.launcher.ArchiTechLauncher.GAME_DIR;
import static org.architech.launcher.ArchiTechLauncher.MINECRAFT_VERSION;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import org.architech.launcher.utils.logging.LogManager;

/** Loads the selected Minecraft version's own item texture and bakes its generated model. */
final class MinecraftItemModelFactory {
    private static final int TEXTURE_SCALE = 8;
    private static final Map<HeldItemType, String> TEXTURES = new EnumMap<>(HeldItemType.class);

    static {
        TEXTURES.put(HeldItemType.SWORD, "diamond_sword");
        TEXTURES.put(HeldItemType.AXE, "diamond_axe");
        TEXTURES.put(HeldItemType.TRIDENT, "trident");
    }

    private MinecraftItemModelFactory() {}

    static Node create(HeldItemType type, Color tint) {
        Image texture = loadTexture(type);
        return texture == null ? null : VoxelItemMesh.create(texture, tint, TEXTURE_SCALE);
    }

    private static Image loadTexture(HeldItemType type) {
        String textureName = TEXTURES.get(type);
        if (textureName == null || GAME_DIR == null) {
            return null;
        }

        Path clientJar = GAME_DIR.resolve("versions")
                .resolve(MINECRAFT_VERSION)
                .resolve(MINECRAFT_VERSION + ".jar");
        if (!Files.isRegularFile(clientJar)) {
            return null;
        }

        String entryName = "assets/minecraft/textures/item/" + textureName + ".png";
        try (ZipFile zip = new ZipFile(clientJar.toFile())) {
            ZipEntry entry = zip.getEntry(entryName);
            if (entry == null) {
                return null;
            }
            try (InputStream stream = zip.getInputStream(entry)) {
                Image source = new Image(stream);
                return upscaleNearest(source, TEXTURE_SCALE);
            }
        } catch (IOException | RuntimeException error) {
            LogManager.getLogger().warning(
                    "Could not load Minecraft held-item texture " + entryName + ": " + error.getMessage());
            return null;
        }
    }

    private static Image upscaleNearest(Image source, int scale) {
        int width = (int) source.getWidth();
        int height = (int) source.getHeight();
        WritableImage result = new WritableImage(width * scale, height * scale);
        PixelReader reader = source.getPixelReader();
        for (int y = 0; y < height * scale; y++) {
            for (int x = 0; x < width * scale; x++) {
                result.getPixelWriter().setArgb(x, y, reader.getArgb(x / scale, y / scale));
            }
        }
        return result;
    }
}
