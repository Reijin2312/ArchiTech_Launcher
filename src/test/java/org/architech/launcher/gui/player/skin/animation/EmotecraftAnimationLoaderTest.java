// SPDX-FileCopyrightText: 2026 Raijin2312
// SPDX-License-Identifier: GPL-3.0-only

package org.architech.launcher.gui.player.skin.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EmotecraftAnimationLoaderTest {
    @Test
    void loadsDegreeBasedAnimationAndConvertsJavaFxAxes() {
        SkinAnimationClip waving = EmotecraftAnimationLoader.load(
                "waving",
                "/animations/emotecraft/waving.json");

        BoneTransform armAtTenTicks = waving.sample(0.5).bone(SkinBone.RIGHT_ARM);

        assertEquals(2.5, waving.durationSeconds(), 0.0001);
        assertEquals(90.0, armAtTenTicks.yaw(), 0.0001);
        assertEquals(185.0, armAtTenTicks.roll(), 0.0001);
    }

    @Test
    void convertsRadianBasedAnimationToDegrees() {
        SkinAnimationClip clap = EmotecraftAnimationLoader.load(
                "clap",
                "/animations/emotecraft/clap.json");

        BoneTransform armAtTenTicks = clap.sample(0.5).bone(SkinBone.RIGHT_ARM);

        assertEquals(Math.toDegrees(-0.3404000997543335), armAtTenTicks.yaw(), 0.0001);
        assertEquals(Math.toDegrees(-1.459968090057373), armAtTenTicks.pitch(), 0.0001);
        assertEquals(Math.toDegrees(-0.32186466455459595), armAtTenTicks.roll(), 0.0001);
    }

    @Test
    void directorAvoidsItsTwoMostRecentAutomaticAnimations() {
        SkinAnimationDirector director = new SkinAnimationDirector(42L);
        List<String> starts = new ArrayList<>();
        String previousState = null;

        for (int frame = 0; frame < 20_000 && starts.size() < 20; frame++) {
            director.update(0.05);
            String current = director.activeAnimationId();
            if (current != null && !current.equals(previousState)) {
                starts.add(current);
            }
            previousState = current;
        }

        assertTrue(starts.size() >= 10);
        for (int index = 1; index < starts.size(); index++) {
            assertNotEquals(starts.get(index - 1), starts.get(index));
            if (index >= 2) {
                assertNotEquals(starts.get(index - 2), starts.get(index));
            }
        }
    }

    @Test
    void easingFunctionsStayWithinExpectedEndpoints() {
        for (Easing easing : Easing.values()) {
            assertEquals(0.0, easing.apply(0.0), 0.0001);
            if (easing != Easing.CONSTANT) {
                assertEquals(1.0, easing.apply(1.0), 0.0001);
            }
        }
    }

}
