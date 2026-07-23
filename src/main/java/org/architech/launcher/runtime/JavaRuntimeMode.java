// SPDX-FileCopyrightText: 2026 Raijin2312
// SPDX-License-Identifier: GPL-3.0-only

package org.architech.launcher.runtime;

import java.util.Locale;

public enum JavaRuntimeMode {
    BUNDLED("bundled", "Встроенная Java 21 (рекомендуется)"),
    CUSTOM("custom", "Пользовательская Java");

    private final String configValue;
    private final String displayName;

    JavaRuntimeMode(String configValue, String displayName) {
        this.configValue = configValue;
        this.displayName = displayName;
    }

    public String configValue() {
        return configValue;
    }

    @Override
    public String toString() {
        return displayName;
    }

    public static JavaRuntimeMode fromConfig(Object value) {
        if (value == null) {
            return BUNDLED;
        }

        String normalized = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        for (JavaRuntimeMode mode : values()) {
            if (mode.configValue.equals(normalized)
                    || mode.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return mode;
            }
        }
        return BUNDLED;
    }
}
