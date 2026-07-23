// SPDX-FileCopyrightText: 2026 Raijin2312
// SPDX-License-Identifier: GPL-3.0-only

package org.architech.launcher.managment;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.architech.launcher.ArchiTechLauncher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModsManagerIntegrationTest {
    @TempDir
    Path tempDir;

    private String originalBackendUrl;
    private int originalTimeout;

    @AfterEach
    void restoreLauncherSettings() {
        if (originalBackendUrl != null) {
            ArchiTechLauncher.BACKEND_URL = originalBackendUrl;
            ArchiTechLauncher.HTTP_TIMEOUT = originalTimeout;
        }
        ArchiTechLauncher.DOWNLOAD_MANAGER.cancelAllDownloads();
    }

    @Test
    void downloadsManifestFileUsingSha256AndPublishesManifestAtomically() throws Exception {
        byte[] mod = "verified-mod".getBytes(StandardCharsets.UTF_8);
        String manifest = """
                {"files":[
                  {"path":"mods/example.jar","size":%d,"sha256":"%s"},
                  {"path":"launcher/ignored.bin","size":1,"sha256":"%s"}
                ]}
                """.formatted(mod.length, sha256(mod), "00".repeat(32));

        try (TestServer server = new TestServer(manifest, mod)) {
            pointLauncherAt(server);

            ModsManager.syncMods(tempDir);

            assertArrayEquals(mod, Files.readAllBytes(tempDir.resolve("mods/example.jar")));
            assertTrue(Files.isRegularFile(tempDir.resolve("manifest.json")));
            assertFalse(Files.exists(tempDir.resolve("manifest.json.tmp")));
            assertFalse(Files.exists(tempDir.resolve("launcher/ignored.bin")));
        }
    }

    @Test
    void rejectsRemoteTraversalBeforeDownloadingAnything() throws Exception {
        String manifest = """
                {"files":[{"path":"../escaped.jar","size":4,"sha256":"%s"}]}
                """.formatted("00".repeat(32));

        try (TestServer server = new TestServer(manifest, "data".getBytes(StandardCharsets.UTF_8))) {
            pointLauncherAt(server);

            assertThrows(IOException.class, () -> ModsManager.syncMods(tempDir));
            assertFalse(Files.exists(tempDir.getParent().resolve("escaped.jar")));
            assertFalse(Files.exists(tempDir.resolve("manifest.json")));
        }
    }

    @Test
    void ignoresUnsafePathFromOldLocalManifestInsteadOfDeletingOutsideFile() throws Exception {
        Path outside = tempDir.getParent().resolve("must-survive.txt");
        Files.writeString(outside, "keep");
        Files.writeString(
                tempDir.resolve("manifest.json"),
                "{\"files\":[{\"path\":\"../must-survive.txt\",\"size\":4}]}"
        );

        try (TestServer server = new TestServer("{\"files\":[]}", new byte[0])) {
            pointLauncherAt(server);

            ModsManager.syncMods(tempDir);

            assertTrue(Files.exists(outside));
            assertTrue(Files.readString(outside).equals("keep"));
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void preservesDisabledStateWhileReplacingInvalidFile() throws Exception {
        byte[] expected = "new-disabled-mod".getBytes(StandardCharsets.UTF_8);
        Path disabled = tempDir.resolve("mods/example.jar.disabled");
        Files.createDirectories(disabled.getParent());
        Files.writeString(disabled, "stale");

        String manifest = """
                {"files":[{"path":"mods/example.jar","size":%d,"sha256":"%s"}]}
                """.formatted(expected.length, sha256(expected));

        try (TestServer server = new TestServer(manifest, expected)) {
            pointLauncherAt(server);

            ModsManager.syncMods(tempDir);

            assertArrayEquals(expected, Files.readAllBytes(disabled));
            assertFalse(Files.exists(tempDir.resolve("mods/example.jar")));
        }
    }

    private void pointLauncherAt(TestServer server) {
        originalBackendUrl = ArchiTechLauncher.BACKEND_URL;
        originalTimeout = ArchiTechLauncher.HTTP_TIMEOUT;
        ArchiTechLauncher.BACKEND_URL = server.baseUrl();
        ArchiTechLauncher.HTTP_TIMEOUT = 5;
        ArchiTechLauncher.UI = null;
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static final class TestServer implements AutoCloseable {
        private final HttpServer server;
        private final byte[] manifest;
        private final byte[] file;

        TestServer(String manifest, byte[] file) throws IOException {
            this.manifest = manifest.getBytes(StandardCharsets.UTF_8);
            this.file = file;
            this.server = HttpServer.create(
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                    0
            );
            server.createContext("/api/files/manifest", exchange -> send(exchange, 200, this.manifest));
            server.createContext("/api/files/file/mods/example.jar", exchange -> send(exchange, 200, this.file));
            server.setExecutor(Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, "mods-test-http");
                thread.setDaemon(true);
                return thread;
            }));
            server.start();
        }

        String baseUrl() {
            String host = server.getAddress().getAddress().getHostAddress();
            if (host.contains(":")) {
                host = "[" + host + "]";
            }
            return "http://" + host + ":" + server.getAddress().getPort();
        }

        private static void send(HttpExchange exchange, int status, byte[] body) throws IOException {
            try (exchange) {
                exchange.sendResponseHeaders(status, body.length);
                exchange.getResponseBody().write(body);
            }
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
