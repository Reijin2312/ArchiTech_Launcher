// SPDX-FileCopyrightText: 2026 Raijin2312
// SPDX-License-Identifier: GPL-3.0-only

package org.architech.launcher.gui.player.skin.item;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.shape.VertexFormat;

/** Bakes Minecraft's generated-item sprite into an alpha-contoured extruded mesh. */
final class VoxelItemMesh {
    private static final double ALPHA_CUTOFF = 1.0 / 255.0;
    private static final float DEPTH = 1.0f;

    private VoxelItemMesh() {}

    static MeshView create(Image texture, Color tint, int textureScale) {
        int width = (int) texture.getWidth() / textureScale;
        int height = (int) texture.getHeight() / textureScale;
        PixelReader pixels = texture.getPixelReader();
        TriangleMesh mesh = new TriangleMesh(VertexFormat.POINT_TEXCOORD);
        float pixelWidth = 16.0f / width;
        float pixelHeight = 16.0f / height;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!opaque(pixels, x, y, width, height, textureScale)) {
                    continue;
                }

                float x0 = x * pixelWidth - 8.0f;
                float x1 = x0 + pixelWidth;
                // GeneratedItemModel maps texture V=0 to model Y=16 and V=1
                // to Y=0. JavaFX image coordinates grow downwards, so mapping
                // rows directly to Y would make the player grip the blade/head.
                float y0 = 8.0f - y * pixelHeight;
                float y1 = y0 - pixelHeight;
                float front = -DEPTH / 2.0f;
                float back = DEPTH / 2.0f;
                float u0 = (float) x / width;
                float u1 = (float) (x + 1) / width;
                float v0 = (float) y / height;
                float v1 = (float) (y + 1) / height;
                float uc = (u0 + u1) / 2.0f;
                float vc = (v0 + v1) / 2.0f;

                quad(mesh, x0, y0, front, x0, y1, front, x1, y1, front, x1, y0, front,
                        u0, v0, u0, v1, u1, v1, u1, v0);
                quad(mesh, x1, y0, back, x1, y1, back, x0, y1, back, x0, y0, back,
                        u1, v0, u1, v1, u0, v1, u0, v0);

                if (!opaque(pixels, x - 1, y, width, height, textureScale)) {
                    solidQuad(mesh, x0, y0, back, x0, y1, back, x0, y1, front, x0, y0, front, uc, vc);
                }
                if (!opaque(pixels, x + 1, y, width, height, textureScale)) {
                    solidQuad(mesh, x1, y0, front, x1, y1, front, x1, y1, back, x1, y0, back, uc, vc);
                }
                if (!opaque(pixels, x, y - 1, width, height, textureScale)) {
                    solidQuad(mesh, x0, y0, front, x1, y0, front, x1, y0, back, x0, y0, back, uc, vc);
                }
                if (!opaque(pixels, x, y + 1, width, height, textureScale)) {
                    solidQuad(mesh, x0, y1, back, x1, y1, back, x1, y1, front, x0, y1, front, uc, vc);
                }
            }
        }

        PhongMaterial material = new PhongMaterial(tint == null ? Color.WHITE : tint);
        material.setDiffuseMap(texture);
        material.setSpecularColor(Color.BLACK);
        MeshView result = new MeshView(mesh);
        result.setMaterial(material);
        result.setCullFace(CullFace.NONE);
        return result;
    }

    private static boolean opaque(
            PixelReader pixels,
            int x,
            int y,
            int width,
            int height,
            int textureScale) {
        return x >= 0
                && y >= 0
                && x < width
                && y < height
                && pixels.getColor(x * textureScale, y * textureScale).getOpacity() >= ALPHA_CUTOFF;
    }

    private static void solidQuad(
            TriangleMesh mesh,
            float x0, float y0, float z0,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float u, float v) {
        quad(mesh, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3,
                u, v, u, v, u, v, u, v);
    }

    private static void quad(
            TriangleMesh mesh,
            float x0, float y0, float z0,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float u0, float v0,
            float u1, float v1,
            float u2, float v2,
            float u3, float v3) {
        int point = mesh.getPoints().size() / 3;
        int texCoord = mesh.getTexCoords().size() / 2;
        mesh.getPoints().addAll(x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3);
        mesh.getTexCoords().addAll(u0, v0, u1, v1, u2, v2, u3, v3);
        mesh.getFaces().addAll(
                point, texCoord,
                point + 1, texCoord + 1,
                point + 2, texCoord + 2,
                point, texCoord,
                point + 2, texCoord + 2,
                point + 3, texCoord + 3);
        mesh.getFaceSmoothingGroups().addAll(0, 0);
    }
}
