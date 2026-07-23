// SPDX-FileCopyrightText: 2026 Raijin2312
// SPDX-License-Identifier: GPL-3.0-only

package org.architech.launcher.managment;

/**
 * Immutable view of the current download session. Values are safe to pass from
 * download worker threads to the JavaFX application thread.
 */
public record DownloadSnapshot(
        boolean active,
        boolean paused,
        long bytesDownloaded,
        long bytesPlanned,
        long bytesWritten,
        long diskBytesPlanned,
        double bytesPerSecond,
        String currentFile) {

    public DownloadSnapshot {
        bytesDownloaded = Math.max(0L, bytesDownloaded);
        bytesPlanned = Math.max(0L, bytesPlanned);
        bytesWritten = Math.max(0L, bytesWritten);
        diskBytesPlanned = Math.max(0L, diskBytesPlanned);
        bytesPerSecond = Math.max(0.0, bytesPerSecond);
        currentFile = currentFile == null ? "" : currentFile;
    }

    public double progress() {
        if (bytesPlanned <= 0L) {
            return active ? -1.0 : 1.0;
        }
        return Math.clamp((double) bytesDownloaded / bytesPlanned, 0.0, 1.0);
    }

    public static DownloadSnapshot idle() {
        return new DownloadSnapshot(
                false,
                false,
                0L,
                0L,
                0L,
                0L,
                0.0,
                "");
    }
}
