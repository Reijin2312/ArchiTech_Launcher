// SPDX-FileCopyrightText: 2026 Raijin2312
// SPDX-License-Identifier: GPL-3.0-only

package org.architech.launcher.gui.player.skin.animation;

import java.util.EnumMap;
import java.util.Map;

public final class SkinAnimationClip {
    private final String id;
    private final double durationSeconds;
    private final EnumMap<SkinBone, BoneTracks> bones;

    SkinAnimationClip(String id, double durationSeconds, Map<SkinBone, BoneTracks> bones) {
        this.id = id;
        this.durationSeconds = durationSeconds;
        this.bones = new EnumMap<>(bones);
    }

    public String id() {
        return id;
    }

    public double durationSeconds() {
        return durationSeconds;
    }

    public AnimationPose sample(double timeSeconds) {
        AnimationPose result = new AnimationPose();
        double time = Math.max(0.0, Math.min(durationSeconds, timeSeconds));
        for (var entry : bones.entrySet()) {
            result.set(entry.getKey(), entry.getValue().sample(time));
        }
        return result;
    }

    static final class BoneTracks {
        final KeyframeTrack pitch;
        final KeyframeTrack yaw;
        final KeyframeTrack roll;
        final KeyframeTrack x;
        final KeyframeTrack y;
        final KeyframeTrack z;
        private final double baseX;
        private final double baseY;
        private final double baseZ;

        BoneTracks(double baseX, double baseY, double baseZ, boolean easeBefore) {
            this.baseX = baseX;
            this.baseY = baseY;
            this.baseZ = baseZ;
            pitch = new KeyframeTrack(0.0, easeBefore);
            yaw = new KeyframeTrack(0.0, easeBefore);
            roll = new KeyframeTrack(0.0, easeBefore);
            x = new KeyframeTrack(baseX, easeBefore);
            y = new KeyframeTrack(baseY, easeBefore);
            z = new KeyframeTrack(baseZ, easeBefore);
        }

        private BoneTransform sample(double time) {
            return new BoneTransform(
                    pitch.sample(time),
                    yaw.sample(time),
                    roll.sample(time),
                    x.sample(time) - baseX,
                    y.sample(time) - baseY,
                    z.sample(time) - baseZ);
        }
    }
}
