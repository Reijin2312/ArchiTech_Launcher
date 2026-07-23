// SPDX-FileCopyrightText: 2026 Raijin2312
// SPDX-License-Identifier: GPL-3.0-only

package org.architech.launcher.runtime;

import java.nio.file.Path;

public record JavaRuntimeInfo(
        Path javaHome,
        Path executable,
        String version,
        int majorVersion) {}
