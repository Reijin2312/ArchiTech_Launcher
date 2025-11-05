package org.architech.launcher.utils;

import java.nio.file.Path;

public class FileEntry {
    public String kind;
    public String name;
    public String url;
    public Path path;
    public long size;
    public String sha1;
    public String sha256;

    public FileEntry(String kind, String name, String url, Path path, long size, String sha1) {
        this(kind, name, url, path, size, sha1, null);
    }

    public FileEntry(String kind, String name, String url, Path path, long size, String sha1, String sha256) {
        this.kind = kind; this.name = name; this.url = url; this.path = path;
        this.size = size; this.sha1 = sha1; this.sha256 = sha256;
    }
}

