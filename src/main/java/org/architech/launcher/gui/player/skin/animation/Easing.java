// SPDX-FileCopyrightText: 2026 Raijin2312
// SPDX-License-Identifier: GPL-3.0-only

package org.architech.launcher.gui.player.skin.animation;

import java.util.Locale;

enum Easing {
    LINEAR,
    CONSTANT,
    IN_QUAD,
    OUT_QUAD,
    IN_OUT_QUAD,
    IN_SINE,
    OUT_SINE,
    IN_OUT_SINE,
    IN_CIRC,
    OUT_CIRC,
    IN_OUT_CIRC,
    OUT_QUART,
    OUT_EXPO;

    static Easing parse(String value) {
        String normalized = value == null
                ? "LINEAR"
                : value.toUpperCase(Locale.ROOT).replace("EASE", "").replace("_", "");
        return switch (normalized) {
            case "CONSTANT" -> CONSTANT;
            case "INQUAD" -> IN_QUAD;
            case "OUTQUAD" -> OUT_QUAD;
            case "INOUTQUAD" -> IN_OUT_QUAD;
            case "INSINE" -> IN_SINE;
            case "OUTSINE" -> OUT_SINE;
            case "INOUTSINE" -> IN_OUT_SINE;
            case "INCIRC" -> IN_CIRC;
            case "OUTCIRC" -> OUT_CIRC;
            case "INOUTCIRC" -> IN_OUT_CIRC;
            case "OUTQUART" -> OUT_QUART;
            case "OUTEXPO" -> OUT_EXPO;
            default -> LINEAR;
        };
    }

    double apply(double value) {
        double t = Math.max(0.0, Math.min(1.0, value));
        return switch (this) {
            case LINEAR -> t;
            case CONSTANT -> 0.0;
            case IN_QUAD -> t * t;
            case OUT_QUAD -> 1.0 - (1.0 - t) * (1.0 - t);
            case IN_OUT_QUAD -> t < 0.5
                    ? 2.0 * t * t
                    : 1.0 - Math.pow(-2.0 * t + 2.0, 2.0) / 2.0;
            case IN_SINE -> 1.0 - Math.cos(t * Math.PI / 2.0);
            case OUT_SINE -> Math.sin(t * Math.PI / 2.0);
            case IN_OUT_SINE -> -(Math.cos(Math.PI * t) - 1.0) / 2.0;
            case IN_CIRC -> 1.0 - Math.sqrt(1.0 - t * t);
            case OUT_CIRC -> Math.sqrt(1.0 - Math.pow(t - 1.0, 2.0));
            case IN_OUT_CIRC -> t < 0.5
                    ? (1.0 - Math.sqrt(1.0 - Math.pow(2.0 * t, 2.0))) / 2.0
                    : (Math.sqrt(1.0 - Math.pow(-2.0 * t + 2.0, 2.0)) + 1.0) / 2.0;
            case OUT_QUART -> 1.0 - Math.pow(1.0 - t, 4.0);
            case OUT_EXPO -> t >= 1.0 ? 1.0 : 1.0 - Math.pow(2.0, -10.0 * t);
        };
    }
}
