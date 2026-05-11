package org.architech.launcher.utils.serverinfo;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;

import org.architech.launcher.ArchiTechLauncher;

public class ServersDatGenerator {

    public static void createServersDat(Path file) throws IOException {
        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(file.toFile()))) {
            dos.writeByte(10);
            dos.writeShort(1);
            dos.writeBytes("s");
            dos.writeByte(9);
            dos.writeShort(8);
            dos.writeBytes("servers");
            dos.writeByte(10);
            dos.writeInt(1);
            dos.writeByte(10);
            dos.writeShort(0);
            dos.writeByte(8);
            dos.writeShort(4);
            dos.writeBytes("name");
            dos.writeShort(15);
            dos.writeBytes("Сервер Minecraft");
            dos.writeByte(8);
            dos.writeShort(2);
            dos.writeBytes("ip");
            dos.writeShort(21);
            dos.writeBytes(ArchiTechLauncher.MINESERVER_URL);
            dos.writeByte(1);
            dos.writeShort(6);
            dos.writeBytes("hidden");
            dos.writeByte(0);
            dos.writeByte(0);
            dos.writeByte(0);
            dos.writeByte(0);
        }
    }
}
