// SPDX-FileCopyrightText: 2026 Raijin2312
// SPDX-License-Identifier: GPL-3.0-only

package org.architech.launcher.gui.player.skin.item;

/** Item displayed by an item-aware player animation. */
public enum HeldItemType {
    NONE,
    SWORD,
    AXE,
    TRIDENT;

    public static HeldItemType forAnimation(String animationId) {
        if (animationId == null) {
            return NONE;
        }
        return switch (animationId) {
            case "item_turn" -> AXE;
            case "item_toss" -> SWORD;
            case "item_flip" -> TRIDENT;
            case "item_flourish" -> SWORD;
            default -> NONE;
        };
    }
}
