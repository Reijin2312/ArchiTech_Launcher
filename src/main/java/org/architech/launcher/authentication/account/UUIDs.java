package org.architech.launcher.authentication.account;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class UUIDs {
    public static String dashify(String raw) {
        String s = raw==null?"":raw.replace("-","");
        if(s.length()!=32) return raw; // already dashed or unexpected
        return s.substring(0,8)+"-"+s.substring(8,12)+"-"+s.substring(12,16)+"-"+s.substring(16,20)+"-"+s.substring(20);
    }

    public static String offlineUuid(String name) {
        String basis = "OfflinePlayer:" + (name==null?"Player":name);
        return dashify(UUID.nameUUIDFromBytes(basis.getBytes(StandardCharsets.UTF_8)).toString());
    }
}