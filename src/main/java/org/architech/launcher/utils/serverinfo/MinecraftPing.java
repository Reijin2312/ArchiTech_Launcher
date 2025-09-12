package org.architech.launcher.utils.serverinfo;

import org.json.JSONObject;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public final class MinecraftPing {
    public record ServerStatus(int online, int max, int pingMs, String motd) {}

    public static ServerStatus fetchStatus(String host, int port, int timeoutMs) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);

            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            // --- handshake (state = 1 -> status)
            ByteArrayOutputStream handshake = new ByteArrayOutputStream();
            writeVarInt(handshake, 0x00);                 // packet id: handshake
            writeVarInt(handshake, 47);                   // protocol version (47 работает для опроса)
            writeString(handshake, host);
            handshake.write((port >> 8) & 0xFF);
            handshake.write(port & 0xFF);
            writeVarInt(handshake, 1);                    // next state = status

            writeVarInt(out, handshake.size());
            handshake.writeTo(out);
            out.flush();

            // --- status request (packet id 0x00)
            ByteArrayOutputStream req = new ByteArrayOutputStream();
            writeVarInt(req, 0x00);
            writeVarInt(out, req.size());
            req.writeTo(out);
            out.flush();

            // --- read status response
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

            JSONObject root = new JSONObject(json);
            JSONObject players = root.optJSONObject("players");
            int online = players != null ? players.optInt("online", 0) : 0;
            int max = players != null ? players.optInt("max", 0) : 0;
            String motd = root.optString("description", root.optString("motd", ""));

            // --- ping (send ping packet id 0x01 with payload long)
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

            return new ServerStatus(online, max, pingMs, motd);
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
