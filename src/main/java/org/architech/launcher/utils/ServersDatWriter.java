package org.architech.launcher.utils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class ServersDatWriter {

    private static final int TAG_End = 0;
    private static final int TAG_Byte = 1;
    private static final int TAG_String = 8;
    private static final int TAG_List = 9;
    private static final int TAG_Compound = 10;

    public static class ServerEntry {
        public String name;
        public String ip;
        public String iconBase64;
        public Boolean hidden;

        public ServerEntry(String name, String ip) {
            this.name = name;
            this.ip = ip;
        }

        public ServerEntry withHidden(boolean hidden) {
            this.hidden = hidden;
            return this;
        }
    }

    public static void writeServersDat(Path file, List<ServerEntry> servers) throws IOException {
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(file))) {
            writeTagHeader(out, TAG_Compound, "");
            writeTagHeader(out, TAG_List, "servers");
            out.writeByte(TAG_Compound);
            out.writeInt(servers.size());
            for (ServerEntry s : servers) {
                writeCompoundEntry(out, s);
            }
            out.writeByte(TAG_End);
        }
    }

    private static void writeCompoundEntry(DataOutputStream out, ServerEntry s) throws IOException {
        writeTagHeader(out, TAG_String, "name");
        writeString(out, s.name);
        writeTagHeader(out, TAG_String, "ip");
        writeString(out, s.ip);

        if (s.iconBase64 != null && !s.iconBase64.isEmpty()) {
            writeTagHeader(out, TAG_String, "icon");
            writeString(out, s.iconBase64);
        }

        if (s.hidden != null) {
            writeTagHeader(out, TAG_Byte, "hidden");
            out.writeByte(s.hidden ? 1 : 0);
        }

        out.writeByte(TAG_End);
    }


    private static void writeTagHeader(DataOutputStream out, int tagId, String name) throws IOException {
        out.writeByte(tagId);
        writeString(out, name);
    }

    private static void writeString(DataOutputStream out, String s) throws IOException {
        byte[] utf = s.getBytes(StandardCharsets.UTF_8);
        if (utf.length > 65535) throw new IOException("NBT string too long");
        out.writeShort(utf.length);
        out.write(utf);
    }

}
