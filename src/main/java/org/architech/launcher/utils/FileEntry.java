package org.architech.launcher.utils;

import java.nio.file.Path;

public class FileEntry {
    public String kind;
    public String name;
    public String url;
    public Path path;
    public long size;
    public String sha1;

    public FileEntry(String kind, String name, String url, Path path, long size, String sha1) {
        this.kind = kind;
        this.name = name;
        this.url = url;
        this.path = path;
        this.size = size;
        this.sha1 = sha1;
    }
}

