package org.architech.launcher.managment;

import org.architech.launcher.utils.SafePaths;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Validation policy for file paths supplied by the remote launcher manifest. */
public final class ManifestPathPolicy {
    private static final Set<String> PROTECTED_TOP_LEVEL = Set.of(
            "launcher",
            "neoforge",
            "news"
    );

    private static final Set<String> RESERVED_NAMES = Set.of(
            "manifest.json",
            "manifest.json.tmp"
    );

    private ManifestPathPolicy() {
    }

    /**
     * These categories are managed by other launcher components and must not be
     * touched by the mods synchronizer. The check is deliberately tolerant of
     * slash direction and case because the manifest is remote input.
     */
    public static boolean isProtectedTopLevel(String rawPath) {
        if (rawPath == null) {
            return false;
        }
        String portable = rawPath.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
        int slash = portable.indexOf('/');
        String first = slash >= 0 ? portable.substring(0, slash) : portable;
        return PROTECTED_TOP_LEVEL.contains(first);
    }

    public static String validate(Path root, String rawPath) throws IOException {
        String normalized = SafePaths.normalizeRelative(rawPath);
        SafePaths.resolveInside(root, normalized);

        String lower = normalized.toLowerCase(Locale.ROOT);
        String topLevel = lower.contains("/")
                ? lower.substring(0, lower.indexOf('/'))
                : lower;

        if (PROTECTED_TOP_LEVEL.contains(topLevel)) {
            throw new IOException("Manifest path targets a protected directory: " + rawPath);
        }
        if (RESERVED_NAMES.contains(lower)) {
            throw new IOException("Manifest path uses a reserved launcher file: " + rawPath);
        }
        if (lower.endsWith(".disabled")) {
            throw new IOException("Manifest path conflicts with the launcher disabled-file suffix: " + rawPath);
        }
        if (lower.endsWith(".part")) {
            throw new IOException("Manifest path conflicts with the downloader temporary-file suffix: " + rawPath);
        }
        return normalized;
    }

    /**
     * Validates and normalizes all paths and rejects case-insensitive
     * duplicates so the same manifest behaves safely on Windows, Linux and macOS.
     */
    public static List<String> validateAll(Path root, Collection<String> rawPaths) throws IOException {
        if (rawPaths == null) {
            return List.of();
        }

        List<String> normalized = new ArrayList<>(rawPaths.size());
        Set<String> unique = new HashSet<>();
        for (String rawPath : rawPaths) {
            String path = validate(root, rawPath);
            String collisionKey = path.toLowerCase(Locale.ROOT);
            if (!unique.add(collisionKey)) {
                throw new IOException("Manifest contains duplicate or case-colliding path: " + rawPath);
            }
            normalized.add(path);
        }
        return List.copyOf(normalized);
    }
}
