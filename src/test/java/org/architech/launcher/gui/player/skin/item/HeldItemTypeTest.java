// SPDX-FileCopyrightText: 2026 Raijin2312
// SPDX-License-Identifier: GPL-3.0-only

package org.architech.launcher.gui.player.skin.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HeldItemTypeTest {
    @Test
    void mapsOnlyItemAnimationsToVisibleModels() {
        assertEquals(HeldItemType.AXE, HeldItemType.forAnimation("item_turn"));
        assertEquals(HeldItemType.SWORD, HeldItemType.forAnimation("item_toss"));
        assertEquals(HeldItemType.TRIDENT, HeldItemType.forAnimation("item_flip"));
        assertEquals(HeldItemType.SWORD, HeldItemType.forAnimation("item_flourish"));
        assertEquals(HeldItemType.NONE, HeldItemType.forAnimation("waving"));
        assertEquals(HeldItemType.NONE, HeldItemType.forAnimation(null));
    }
}
