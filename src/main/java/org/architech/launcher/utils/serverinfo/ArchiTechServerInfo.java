package org.architech.launcher.utils.serverinfo;

import com.fasterxml.jackson.databind.JsonNode;
import org.architech.launcher.utils.Jsons;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class ArchiTechServerInfo {
    public record ServerStatus(int online, int max, int pingMs, String motd, List<String> sample) {}

    public static ServerStatus fetchStatus(String host, int port, int timeoutMs) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);

            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            ByteArrayOutputStream handshake = new ByteArrayOutputStream();
            writeVarInt(handshake, 0x00);
            writeVarInt(handshake, 47);
            writeString(handshake, host);
            handshake.write((port >> 8) & 0xFF);
            handshake.write(port & 0xFF);
            writeVarInt(handshake, 1);

            writeVarInt(out, handshake.size());
            handshake.writeTo(out);
            out.flush();

            ByteArrayOutputStream req = new ByteArrayOutputStream();
            writeVarInt(req, 0x00);
            writeVarInt(out, req.size());
            req.writeTo(out);
            out.flush();

            int len = readVarInt(in);
            byte[] data = new byte[len];
            readFully(in, data);
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            int packetId = readVarInt(bais);
            String json;
            if (packetId == 0x00) {
                json = readString(bais);
            } else {
                throw new IOException("Unexpected packetId " + packetId);
            }


            JsonNode root = Jsons.MAPPER.readTree(json);
            JsonNode players = root.path("players");
            int online = players.path("online").asInt(0);
            int max = players.path("max").asInt(0);

            String motd = "";
            JsonNode desc = root.path("description");
            if (desc.isTextual()) {
                motd = desc.asText();
            } else if (desc.isObject()) {
                motd = desc.path("text").asText("");
            } else if (desc.isArray() && !desc.isEmpty()) {
                JsonNode first = desc.get(0);
                if (first.isObject()) {
                    motd = first.path("text").asText("");
                } else if (first.isTextual()) {
                    motd = first.asText();
                }
            } else {
                motd = root.path("motd").asText("");
            }

            List<String> sampleNames = new ArrayList<>();
            if (!players.isMissingNode()) {
                JsonNode sampleNode = players.path("sample");
                if (sampleNode.isArray()) {
                    for (JsonNode item : sampleNode) {
                        if (item.isTextual()) {
                            String name = item.asText();
                            if (name != null && !name.isBlank()) sampleNames.add(name);
                        } else if (item.isObject()) {
                            String name = item.path("name").asText(null);
                            if ((name == null || name.isBlank())) name = item.path("id").asText(null);
                            if (name != null && !name.isBlank()) sampleNames.add(name);
                        } else {
                            String name = item.asText(null);
                            if (name != null && !name.isBlank()) sampleNames.add(name);
                        }
                    }
                }
            }

            ByteArrayOutputStream pingBuf = new ByteArrayOutputStream();
            writeVarInt(pingBuf, 0x01);
            long ts = System.currentTimeMillis();
            DataOutputStream dos = new DataOutputStream(pingBuf);
            dos.writeLong(ts);

            writeVarInt(out, pingBuf.size());
            pingBuf.writeTo(out);
            out.flush();

            // read pong
            int len2 = readVarInt(in);
            byte[] data2 = new byte[len2];
            readFully(in, data2);
            ByteArrayInputStream bais2 = new ByteArrayInputStream(data2);
            int pid2 = readVarInt(bais2);
            long pong = -1;
            if (pid2 == 0x01) {
                DataInputStream dis = new DataInputStream(bais2);
                pong = dis.readLong();
            }

            int pingMs = (int) (System.currentTimeMillis() - ts);

            return new ServerStatus(online, max, pingMs, motd, sampleNames);
        }
    }

    private static void writeVarInt(OutputStream out, int value) throws IOException {
        while ((value & 0xFFFFFF80) != 0x0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value & 0x7F);
    }

    private static int readVarInt(InputStream in) throws IOException {
        int numRead = 0;
        int result = 0;
        int read;
        do {
            read = in.read();
            if (read == -1) throw new EOFException();
            int value = (read & 0x7F);
            result |= (value << (7 * numRead));
            numRead++;
            if (numRead > 5) throw new IOException("VarInt too big");
        } while ((read & 0x80) == 0x80);
        return result;
    }

    private static void writeString(OutputStream out, String s) throws IOException {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, b.length);
        out.write(b);
    }

    private static String readString(InputStream in) throws IOException {
        int len = readVarInt(in);
        byte[] buf = new byte[len];
        readFully(in, buf);
        return new String(buf, StandardCharsets.UTF_8);
    }

    private static void readFully(InputStream in, byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int r = in.read(buf, off, buf.length - off);
            if (r == -1) throw new EOFException();
            off += r;
        }
    }
}
