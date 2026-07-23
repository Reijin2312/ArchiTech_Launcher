// SPDX-FileCopyrightText: 2026 Raijin2312
// SPDX-License-Identifier: GPL-3.0-only

package org.architech.launcher.managment;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.architech.launcher.utils.FileEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DownloadManagerIntegrationTest {
    @TempDir
    Path tempDir;

    @Test
    void downloadsFileAtomicallyAndChecksSha256() throws Exception {
        byte[] body = "verified payload".getBytes(StandardCharsets.UTF_8);
        try (MiniHttpServer server = new MiniHttpServer(Map.of("/file", Response.ok(body)))) {
            Path target = tempDir.resolve("downloads/file.bin");
            FileEntry entry =
                    new FileEntry("test", "file.bin", server.url("/file"), target, body.length, null, sha256(body));

            DownloadManager manager = new DownloadManager();
            manager.setTotalBytesPlanned(body.length);
            List<FileEntry> failed = manager.downloadFilesInParallel(List.of(entry), 1, 1, false);

            assertTrue(failed.isEmpty());
            assertArrayEquals(body, Files.readAllBytes(target));
            assertFalse(Files.exists(target.resolveSibling("file.bin.part")));
        }
    }

    @Test
    void rejectsWrongHashAndDoesNotPublishPartialFile() throws Exception {
        byte[] body = "tampered payload".getBytes(StandardCharsets.UTF_8);
        try (MiniHttpServer server = new MiniHttpServer(Map.of("/file", Response.ok(body)))) {
            Path target = tempDir.resolve("file.bin");
            FileEntry entry =
                    new FileEntry("test", "file.bin", server.url("/file"), target, body.length, null, "00".repeat(32));

            List<FileEntry> failed = new DownloadManager().downloadFilesInParallel(List.of(entry), 1, 1, false);

            assertEquals(List.of(entry), failed);
            assertFalse(Files.exists(target));
            assertFalse(Files.exists(target.resolveSibling("file.bin.part")));
        }
    }

    @Test
    void keepsExistingTargetWhenReplacementDownloadFailsValidation() throws Exception {
        byte[] oldBody = "known-good old file".getBytes(StandardCharsets.UTF_8);
        byte[] badReplacement = "bad replacement".getBytes(StandardCharsets.UTF_8);
        Path target = tempDir.resolve("file.bin");
        Files.write(target, oldBody);

        try (MiniHttpServer server = new MiniHttpServer(Map.of("/file", Response.ok(badReplacement)))) {
            FileEntry entry = new FileEntry(
                    "test",
                    "file.bin",
                    server.url("/file"),
                    target,
                    badReplacement.length,
                    null,
                    sha256("expected replacement".getBytes(StandardCharsets.UTF_8)));

            List<FileEntry> failed = new DownloadManager().downloadFilesInParallel(List.of(entry), 1, 1, false);

            assertEquals(1, failed.size());
            assertArrayEquals(oldBody, Files.readAllBytes(target));
        }
    }

    @Test
    void reportsHttpErrorsAsFailedDownloads() throws Exception {
        try (MiniHttpServer server = new MiniHttpServer(
                Map.of("/missing", new Response(500, "server error".getBytes(StandardCharsets.UTF_8))))) {
            Path target = tempDir.resolve("missing.bin");
            FileEntry entry = new FileEntry("test", "missing.bin", server.url("/missing"), target, 0, null);

            List<FileEntry> failed = new DownloadManager().downloadFilesInParallel(List.of(entry), 1, 1, false);

            assertEquals(List.of(entry), failed);
            assertFalse(Files.exists(target));
        }
    }

    @Test
    void downloadsSeveralFilesInParallel() throws Exception {
        Map<String, Response> responses = new ConcurrentHashMap<>();
        List<FileEntry> entries = new ArrayList<>();

        try (MiniHttpServer server = new MiniHttpServer(responses)) {
            for (int index = 0; index < 6; index++) {
                byte[] body = ("payload-" + index).getBytes(StandardCharsets.UTF_8);
                String path = "/file-" + index;
                responses.put(path, Response.ok(body));
                entries.add(new FileEntry(
                        "test",
                        "file-" + index,
                        server.url(path),
                        tempDir.resolve("parallel/file-" + index + ".bin"),
                        body.length,
                        null,
                        sha256(body)));
            }

            List<FileEntry> failed = new DownloadManager().downloadFilesInParallel(entries, 3, 1, false);

            assertTrue(failed.isEmpty());
            for (int index = 0; index < entries.size(); index++) {
                assertEquals("payload-" + index, Files.readString(entries.get(index).path));
            }
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private record Response(int status, byte[] body) {
        static Response ok(byte[] body) {
            return new Response(200, body);
        }
    }

    private static final class MiniHttpServer implements Closeable {
        private final ServerSocket serverSocket;
        private final Map<String, Response> responses;
        private final AtomicBoolean running = new AtomicBoolean(true);
        private final ExecutorService workers = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "test-http-worker");
            thread.setDaemon(true);
            return thread;
        });
        private final Thread acceptThread;

        private MiniHttpServer(Map<String, Response> responses) throws IOException {
            this.responses = responses;
            this.serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            this.acceptThread = new Thread(this::acceptLoop, "test-http-accept");
            this.acceptThread.setDaemon(true);
            this.acceptThread.start();
        }

        String url(String path) {
            return "http://" + serverSocket.getInetAddress().getHostAddress() + ":" + serverSocket.getLocalPort()
                    + path;
        }

        private void acceptLoop() {
            while (running.get()) {
                try {
                    Socket socket = serverSocket.accept();
                    workers.submit(() -> handle(socket));
                } catch (IOException failure) {
                    if (running.get()) {
                        throw new RuntimeException(failure);
                    }
                }
            }
        }

        private void handle(Socket socket) {
            try (socket;
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                    BufferedWriter writer = new BufferedWriter(
                            new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII))) {
                String requestLine = reader.readLine();
                if (requestLine == null || requestLine.isBlank()) {
                    return;
                }
                String[] parts = requestLine.split(" ");
                String path = URI.create(parts[1]).getPath();
                String header;
                while ((header = reader.readLine()) != null && !header.isEmpty()) {
                    // Consume request headers.
                }

                Response response =
                        responses.getOrDefault(path, new Response(404, "not found".getBytes(StandardCharsets.UTF_8)));
                writer.write("HTTP/1.1 " + response.status() + " " + reason(response.status()) + "\r\n");
                writer.write("Content-Length: " + response.body().length + "\r\n");
                writer.write("Connection: close\r\n");
                writer.write("\r\n");
                writer.flush();
                socket.getOutputStream().write(response.body());
                socket.getOutputStream().flush();
            } catch (IOException ignored) {
                // The downloader can close a connection while testing cancellation/failure paths.
            }
        }

        private static String reason(int status) {
            return switch (status) {
                case 200 -> "OK";
                case 404 -> "Not Found";
                case 500 -> "Internal Server Error";
                default -> "Status";
            };
        }

        @Override
        public void close() throws IOException {
            running.set(false);
            serverSocket.close();
            workers.shutdownNow();
        }
    }
}
