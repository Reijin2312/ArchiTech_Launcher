// SPDX-FileCopyrightText: 2026 Raijin2312
// SPDX-License-Identifier: GPL-3.0-only

package org.architech.launcher.gui.player.skin.item;

import java.util.EnumMap;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Transform;

/** Minecraft-compatible held-item attachment for the right hand. */
public final class HeldItemView extends Group {
    private final EnumMap<HeldItemType, Node> models = new EnumMap<>(HeldItemType.class);
    private final Group display = new Group();
    private final Group inspection = new Group(display);
    private HeldItemType type = HeldItemType.NONE;

    public HeldItemView(double handTranslationX) {
        // ItemInHandLayer applies this translation after the two basis
        // rotations. Keeping it in a child node is important: moving the
        // rotated parent to the arm endpoint changes the item's pivot.
        Group handOffset = new Group(inspection);
        handOffset.setTranslateX(handTranslationX);
        handOffset.setTranslateY(2.0);
        handOffset.setTranslateZ(-10.0);

        Group handBasis = new Group(handOffset);
        handBasis.getTransforms().addAll(
                new Rotate(-90.0, Rotate.X_AXIS),
                new Rotate(180.0, Rotate.Y_AXIS));

        // Vanilla 1.21.1 item/handheld thirdperson_righthand transform.
        display.setTranslateY(4.0);
        display.setTranslateZ(0.5);
        display.setScaleX(0.85);
        display.setScaleY(0.85);
        display.setScaleZ(0.85);
        display.getTransforms().addAll(
                new Rotate(-90.0, Rotate.Y_AXIS),
                new Rotate(55.0, Rotate.Z_AXIS));

        getChildren().add(handBasis);
    }

    void setInspectionTransforms(Transform... transforms) {
        inspection.getTransforms().setAll(transforms);
    }

    public void setType(HeldItemType next) {
        HeldItemType resolved = next == null ? HeldItemType.NONE : next;
        if (type == resolved) {
            return;
        }

        models.values().forEach(node -> node.setVisible(false));
        type = resolved;
        if (resolved == HeldItemType.NONE) {
            return;
        }

        Node model = models.get(resolved);
        if (model == null) {
            model = MinecraftItemModelFactory.create(resolved, Color.WHITE);
            if (model != null) {
                models.put(resolved, model);
                display.getChildren().add(model);
            }
        }
        if (model != null) {
            model.setVisible(true);
        }
    }
}
