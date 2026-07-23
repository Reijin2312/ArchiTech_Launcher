// SPDX-FileCopyrightText: 2026 Raijin2312
// SPDX-License-Identifier: GPL-3.0-only

package org.architech.launcher.gui.player.skin.item;

import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;
import org.architech.launcher.gui.player.skin.animation.AnimationPose;
import org.architech.launcher.gui.player.skin.animation.BoneTransform;
import org.architech.launcher.gui.player.skin.animation.SkinBone;

/** Procedural third-person item inspections adapted from Inspect Animations. */
public enum HeldItemAnimation {
    NONE(null, 1.0, 0),
    TURN("item_turn", 0.6, 6),
    TOSS("item_toss", 0.6, 1),
    FLIP("item_flip", 0.6, 1),
    FLOURISH("item_flourish", 0.8, 9);

    private static final double MODEL_UNITS = 16.0;
    private final String id;
    private final double cycleSeconds;
    private final int maxLoop;

    HeldItemAnimation(String id, double cycleSeconds, int maxLoop) {
        this.id = id;
        this.cycleSeconds = cycleSeconds;
        this.maxLoop = maxLoop;
    }

    public static HeldItemAnimation forId(String id) {
        if (id != null) {
            for (HeldItemAnimation animation : values()) {
                if (id.equals(animation.id)) {
                    return animation;
                }
            }
        }
        return NONE;
    }

    public boolean bodyRelative() {
        return this == FLOURISH;
    }

    public AnimationPose applyArmPose(
            AnimationPose source,
            double elapsedSeconds,
            double blend) {
        AnimationPose result = source.copy();
        Phase phase = phase(elapsedSeconds);
        switch (this) {
            case TURN -> setHeadRelativeArm(
                    result,
                    source,
                    SkinBone.RIGHT_ARM,
                    -72.0 * (phase.loop > 0 ? 1.0 : phase.percent),
                    blend);
            case FLIP -> setHeadRelativeArm(result, source, SkinBone.RIGHT_ARM, -72.0, blend);
            case TOSS -> {
                double percent = phase.loop < maxLoop ? 1.0 - phase.percent : phase.percent;
                setHeadRelativeArm(
                        result,
                        source,
                        SkinBone.RIGHT_ARM,
                        percent > 0.75 ? -54.0 : -90.0,
                        blend);
            }
            case FLOURISH -> applyFlourishArms(result, source, phase, blend);
            case NONE -> { }
        }
        return result;
    }

    public void applyItemTransform(HeldItemView view, double elapsedSeconds) {
        Phase phase = phase(elapsedSeconds);
        double eased = ease(phase.percent);
        switch (this) {
            case TURN -> applyTurn(view, phase);
            case TOSS -> {
                double height = phase.loop < maxLoop ? eased : 1.0 - eased;
                double angle = 360.0 * phase.percent;
                view.setInspectionTransforms(
                        new Translate(0.0, height * MODEL_UNITS, 0.0),
                        new Rotate(-angle, Rotate.X_AXIS),
                        new Rotate(-angle, Rotate.Y_AXIS));
            }
            case FLIP -> {
                double angle = 360.0 * phase.percent;
                view.setInspectionTransforms(
                        new Rotate(angle, Rotate.X_AXIS),
                        new Translate(0.06 * MODEL_UNITS, 0.0625 * MODEL_UNITS, -0.125 * MODEL_UNITS),
                        new Rotate(90.0, Rotate.Y_AXIS));
            }
            case FLOURISH -> {
                double percent = phase.loop % 2 == 0 ? phase.percent : 1.0 - phase.percent;
                view.setInspectionTransforms(
                        new Translate(0.33 * MODEL_UNITS, 0.40 * MODEL_UNITS, 0.5 * MODEL_UNITS),
                        new Rotate(70.0, Rotate.X_AXIS),
                        new Translate(ease(percent) * -0.79 * MODEL_UNITS, 0.0, 0.0),
                        new Rotate(eased * 360.0, Rotate.X_AXIS),
                        new Rotate(-21.0 + ease(percent) * 42.0, Rotate.Y_AXIS));
            }
            case NONE -> view.setInspectionTransforms();
        }
    }

    private static void applyTurn(HeldItemView view, Phase phase) {
        double x;
        double y;
        double z;
        double translateY;
        if (phase.loop < 1) {
            x = lerp(0, -5, phase.percent);
            y = lerp(0, -25, phase.percent);
            z = lerp(0, 75, phase.percent);
            translateY = lerp(0, -0.1, phase.percent);
        } else if (phase.loop < 3) {
            x = -5;
            y = -25;
            z = 75;
            translateY = -0.1;
        } else if (phase.loop < 4) {
            x = lerp(-5, 0, phase.percent);
            y = lerp(-25, 55, phase.percent);
            z = lerp(75, -35, phase.percent);
            translateY = lerp(-0.1, 0, phase.percent);
        } else if (phase.loop < 6) {
            x = 0;
            y = 55;
            z = -35;
            translateY = 0;
        } else {
            x = 0;
            y = lerp(55, 0, phase.percent);
            z = lerp(-35, 0, phase.percent);
            translateY = 0;
        }
        view.setInspectionTransforms(
                new Translate(0.0, translateY * MODEL_UNITS, 0.0),
                new Rotate(x, Rotate.X_AXIS),
                new Rotate(y, Rotate.Y_AXIS),
                new Rotate(z, Rotate.Z_AXIS));
    }

    private static void applyFlourishArms(
            AnimationPose pose,
            AnimationPose source,
            Phase phase,
            double blend) {
        boolean even = phase.loop % 2 == 0;
        if (phase.percent < 0.25) {
            setArm(
                    pose,
                    source,
                    even ? SkinBone.RIGHT_ARM : SkinBone.LEFT_ARM,
                    -72.0,
                    even ? 18.0 : -18.0,
                    blend);
        } else if (phase.percent > 0.65) {
            setArm(
                    pose,
                    source,
                    even ? SkinBone.LEFT_ARM : SkinBone.RIGHT_ARM,
                    -72.0,
                    even ? -18.0 : 18.0,
                    blend);
        }
    }

    private static void setHeadRelativeArm(
            AnimationPose pose,
            AnimationPose source,
            SkinBone bone,
            double pitch,
            double blend) {
        BoneTransform head = source.bone(SkinBone.HEAD);
        setArm(pose, source, bone, pitch + head.pitch(), head.yaw(), blend);
    }

    private static void setArm(
            AnimationPose pose,
            AnimationPose source,
            SkinBone bone,
            double pitch,
            double yaw,
            double blend) {
        BoneTransform base = source.bone(bone);
        BoneTransform target = new BoneTransform(
                pitch,
                yaw,
                base.roll(),
                base.offsetX(),
                base.offsetY(),
                base.offsetZ());
        pose.set(bone, BoneTransform.blend(base, target, blend));
    }

    private Phase phase(double elapsedSeconds) {
        double safe = Math.max(0.0, elapsedSeconds);
        int loop = Math.min(maxLoop, (int) (safe / cycleSeconds));
        double percent = Math.min(1.0, (safe - loop * cycleSeconds) / cycleSeconds);
        return new Phase(loop, percent);
    }

    private static double lerp(double start, double end, double amount) {
        return start + (end - start) * amount;
    }

    private static double ease(double value) {
        double t = Math.max(0.0, Math.min(1.0, value));
        return t < 0.5
                ? 8.0 * t * t * t * t
                : 1.0 - Math.pow(-2.0 * t + 2.0, 4.0) / 2.0;
    }

    private record Phase(int loop, double percent) {}
}
