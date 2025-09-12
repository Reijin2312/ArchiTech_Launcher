package org.architech.launcher.authentication.microsoft;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class Pkce {
    public static String newCodeVerifier() {
        byte[] buf = new byte[32];
        new SecureRandom().nextBytes(buf);
        return base64UrlNoPadding(buf);
    }
    public static String codeChallengeS256(String verifier) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha256.digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return base64UrlNoPadding(digest);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    private static String base64UrlNoPadding(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
    private Pkce() {}
}