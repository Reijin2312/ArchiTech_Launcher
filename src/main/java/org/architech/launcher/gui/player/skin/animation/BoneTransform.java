// SPDX-FileCopyrightText: 2026 Raijin2312
// SPDX-License-Identifier: GPL-3.0-only

package org.architech.launcher.gui.player.skin.animation;

public record BoneTransform(
        double pitch,
        double yaw,
        double roll,
        double offsetX,
        double offsetY,
        double offsetZ) {
    public static final BoneTransform IDENTITY = new BoneTransform(0, 0, 0, 0, 0, 0);

    public static BoneTransform blend(BoneTransform from, BoneTransform to, double amount) {
        return new BoneTransform(
                lerp(from.pitch, to.pitch, amount),
                lerp(from.yaw, to.yaw, amount),
                lerp(from.roll, to.roll, amount),
                lerp(from.offsetX, to.offsetX, amount),
                lerp(from.offsetY, to.offsetY, amount),
                lerp(from.offsetZ, to.offsetZ, amount));
    }

    private static double lerp(double from, double to, double amount) {
        return from + (to - from) * amount;
    }
}
