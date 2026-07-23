package org.architech.launcher.utils;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Utilities for resolving paths received from manifests and archives.
 *
 * <p>The checks are intentionally platform-independent: Windows drive paths,
 * UNC paths and backslash traversal are rejected even when the launcher is
 * currently running on Linux or macOS.</p>
 */
public final class SafePaths {
    private SafePaths() {
    }

    public static String normalizeRelative(String untrustedPath) throws IOException {
        Path relative = parseRelative(untrustedPath);
        return relative.toString().replace('\\', '/');
    }

    public static Path resolveInside(Path root, String untrustedPath) throws IOException {
        Objects.requireNonNull(root, "root");

        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path relative = parseRelative(untrustedPath);
        Path resolved = normalizedRoot.resolve(relative).normalize();

        if (!resolved.startsWith(normalizedRoot)) {
            throw new IOException("Path escapes root directory: " + untrustedPath);
        }
        return resolved;
    }

    /**
     * Creates the parent directory tree one component at a time and refuses to
     * traverse symbolic links. This prevents an existing symlink inside the
     * destination tree from redirecting writes outside the trusted root.
     */
    public static void createParentDirectoriesSecurely(Path root, Path target) throws IOException {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(target, "target");

        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedTarget = target.toAbsolutePath().normalize();
        if (!normalizedTarget.startsWith(normalizedRoot)) {
            throw new IOException("Target escapes root directory: " + target);
        }

        createDirectoryWithoutFollowingLinks(normalizedRoot);

        Path parent = normalizedTarget.getParent();
        if (parent == null) {
            throw new IOException("Target has no parent directory: " + target);
        }

        Path current = normalizedRoot;
        Path relativeParent = normalizedRoot.relativize(parent);
        for (Path component : relativeParent) {
            current = current.resolve(component);
            createDirectoryWithoutFollowingLinks(current);
        }
    }

    public static void verifyNoSymlinkParents(Path root, Path target) throws IOException {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(target, "target");

        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedTarget = target.toAbsolutePath().normalize();
        if (!normalizedTarget.startsWith(normalizedRoot)) {
            throw new IOException("Target escapes root directory: " + target);
        }

        if (Files.exists(normalizedRoot, LinkOption.NOFOLLOW_LINKS)
                && Files.isSymbolicLink(normalizedRoot)) {
            throw new IOException("Refusing to traverse symbolic link: " + normalizedRoot);
        }

        Path parent = normalizedTarget.getParent();
        if (parent == null) {
            throw new IOException("Target has no parent directory: " + target);
        }

        Path current = normalizedRoot;
        for (Path component : normalizedRoot.relativize(parent)) {
            current = current.resolve(component);
            if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                return;
            }
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Refusing to traverse symbolic link: " + current);
            }
            if (!Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Expected a directory but found a file: " + current);
            }
        }
    }

    public static void rejectSymbolicLink(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isSymbolicLink(path)) {
            throw new IOException("Symbolic links are not allowed here: " + path);
        }
    }

    private static Path parseRelative(String untrustedPath) throws IOException {
        if (untrustedPath == null) {
            throw new IOException("Path is null");
        }
        if (untrustedPath.indexOf('\0') >= 0) {
            throw new IOException("Path contains a NUL character");
        }

        String portable = untrustedPath.trim().replace('\\', '/');
        if (portable.isEmpty()) {
            throw new IOException("Path is empty");
        }
        if (portable.startsWith("/") || portable.startsWith("//")) {
            throw new IOException("Absolute paths are not allowed: " + untrustedPath);
        }
        if (looksLikeWindowsDrivePath(portable)) {
            throw new IOException("Windows drive paths are not allowed: " + untrustedPath);
        }
        for (String segment : portable.split("/", -1)) {
            if ("..".equals(segment) || ".".equals(segment)) {
                throw new IOException("Dot path segments are not allowed: " + untrustedPath);
            }
        }

        final Path relative;
        try {
            relative = Path.of(portable).normalize();
        } catch (InvalidPathException ex) {
            throw new IOException("Invalid path: " + untrustedPath, ex);
        }

        String normalized = relative.toString();
        if (relative.isAbsolute()
                || normalized.isEmpty()
                || ".".equals(normalized)
                || relative.startsWith("..")) {
            throw new IOException("Unsafe relative path: " + untrustedPath);
        }
        return relative;
    }

    private static boolean looksLikeWindowsDrivePath(String path) {
        return path.length() >= 2
                && Character.isLetter(path.charAt(0))
                && path.charAt(1) == ':';
    }

    private static void createDirectoryWithoutFollowingLinks(Path directory) throws IOException {
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(directory)) {
                throw new IOException("Refusing to traverse symbolic link: " + directory);
            }
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Expected a directory but found a file: " + directory);
            }
            return;
        }

        try {
            Files.createDirectory(directory);
        } catch (FileAlreadyExistsException race) {
            if (Files.isSymbolicLink(directory)
                    || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Directory was replaced while being created: " + directory, race);
            }
        }
    }
}
