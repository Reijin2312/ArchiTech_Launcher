// SPDX-FileCopyrightText: 2026 Raijin2312
// SPDX-License-Identifier: GPL-3.0-only

package org.architech.launcher.gui.player.skin.animation;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

final class KeyframeTrack {
    private final double defaultValue;
    private final boolean easeBeforeKeyframe;
    private final NavigableMap<Double, Keyframe> keyframes = new TreeMap<>();

    KeyframeTrack(double defaultValue, boolean easeBeforeKeyframe) {
        this.defaultValue = defaultValue;
        this.easeBeforeKeyframe = easeBeforeKeyframe;
    }

    void add(double timeSeconds, double value, Easing easing) {
        keyframes.put(timeSeconds, new Keyframe(timeSeconds, value, easing));
    }

    double sample(double timeSeconds) {
        if (keyframes.isEmpty()) {
            return defaultValue;
        }

        var nextEntry = keyframes.ceilingEntry(timeSeconds);
        var previousEntry = keyframes.floorEntry(timeSeconds);
        if (previousEntry == null) {
            Keyframe next = nextEntry.getValue();
            if (next.timeSeconds <= 0.0) {
                return next.value;
            }
            double progress = next.easing.apply(timeSeconds / next.timeSeconds);
            return interpolate(defaultValue, next.value, progress);
        }
        if (nextEntry == null || nextEntry.getKey().equals(previousEntry.getKey())) {
            return previousEntry.getValue().value;
        }

        Keyframe previous = previousEntry.getValue();
        Keyframe next = nextEntry.getValue();
        double progress = (timeSeconds - previous.timeSeconds) / (next.timeSeconds - previous.timeSeconds);
        Easing easing = easeBeforeKeyframe ? next.easing : previous.easing;
        return interpolate(previous.value, next.value, easing.apply(progress));
    }

    List<Keyframe> keyframes() {
        return new ArrayList<>(keyframes.values());
    }

    private static double interpolate(double from, double to, double amount) {
        return from + (to - from) * amount;
    }

    record Keyframe(double timeSeconds, double value, Easing easing) {}
}
