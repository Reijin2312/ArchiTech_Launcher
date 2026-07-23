// SPDX-FileCopyrightText: 2026 Raijin2312
// SPDX-License-Identifier: GPL-3.0-only

package org.architech.launcher.gui.player.skin.animation;

import java.util.EnumMap;
import java.util.Map;

public final class AnimationPose {
    private final EnumMap<SkinBone, BoneTransform> bones = new EnumMap<>(SkinBone.class);

    public BoneTransform bone(SkinBone bone) {
        return bones.getOrDefault(bone, BoneTransform.IDENTITY);
    }

    public AnimationPose set(SkinBone bone, BoneTransform transform) {
        bones.put(bone, transform);
        return this;
    }

    public static AnimationPose blend(AnimationPose from, AnimationPose to, double amount) {
        AnimationPose result = new AnimationPose();
        for (SkinBone bone : SkinBone.values()) {
            result.set(bone, BoneTransform.blend(from.bone(bone), to.bone(bone), amount));
        }
        return result;
    }

    Map<SkinBone, BoneTransform> bones() {
        return Map.copyOf(bones);
    }
}
