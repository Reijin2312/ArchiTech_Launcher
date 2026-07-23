// SPDX-FileCopyrightText: 2026 Raijin2312
// SPDX-License-Identifier: GPL-3.0-only

package org.architech.launcher.process;

import java.time.Instant;

public record GameProcessRecord(
        long pid,
        Instant startedAt,
        String command,
        String gameDirectory) {}
