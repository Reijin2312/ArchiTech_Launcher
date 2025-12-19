package org.architech.launcher.authentication.keystorage;

import com.fasterxml.jackson.core.type.TypeReference;
import org.architech.launcher.utils.Jsons;
import org.architech.launcher.utils.logging.LogManager;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple AES-GCM encrypted secret storage that keeps data next to .account.json.
 * Does not depend on OS keyrings or external services; uses a locally generated key file.
 */
public class LocalEncryptedSecretStorage implements SecretStorage {
    private static final int KEY_LEN = 32;
    private static final int IV_LEN = 12;
    private static final int TAG_LEN = 128;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {};

    private final Path storeFile;
    private final Path keyFile;
    private final Map<String, String> encrypted = new ConcurrentHashMap<>();
    private final SecretKeySpec aesKey;

    public LocalEncryptedSecretStorage(Path storeFile, Path keyFile) throws IOException {
        this.storeFile = storeFile;
        this.keyFile = keyFile;
        Files.createDirectories(storeFile.getParent());
        this.aesKey = new SecretKeySpec(readOrCreateKey(), "AES");
        loadExisting();
    }

    @Override
    public synchronized void putSecret(String key, String secret) throws Exception {
        if (secret == null) {
            deleteSecret(key);
            return;
        }
        encrypted.put(key, encrypt(secret));
        persist();
    }

    @Override
    public synchronized Optional<String> getSecret(String key) throws Exception {
        String blob = encrypted.get(key);
        if (blob == null) return Optional.empty();
        try {
            return Optional.ofNullable(decrypt(blob));
        } catch (Exception ex) {
            LogManager.getLogger().warning("Secret decrypt failed for key " + key + ": " + ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public synchronized void deleteSecret(String key) throws Exception {
        if (encrypted.remove(key) != null) {
            persist();
        }
    }

    private void loadExisting() {
        if (!Files.exists(storeFile)) return;
        try {
            byte[] raw = Files.readAllBytes(storeFile);
            if (raw.length == 0) return;
            Map<String, String> loaded = Jsons.MAPPER.readValue(raw, MAP_TYPE);
            if (loaded != null) encrypted.putAll(loaded);
        } catch (Exception e) {
            LogManager.getLogger().warning("Failed to parse existing secrets store, recreating: " + e.getMessage());
            try { Files.deleteIfExists(storeFile); } catch (Exception ignored) {}
            encrypted.clear();
        }
    }

    private byte[] readOrCreateKey() throws IOException {
        if (Files.exists(keyFile)) {
            byte[] data = Files.readAllBytes(keyFile);
            if (data.length >= KEY_LEN) {
                return trim(data, KEY_LEN);
            }
            LogManager.getLogger().warning("Invalid key length found, recreating key file");
            safeDelete(keyFile);
        }

        byte[] fresh = new byte[KEY_LEN];
        RANDOM.nextBytes(fresh);
        Path tmp = keyFile.resolveSibling(keyFile.getFileName().toString() + ".tmp");
        Files.write(tmp, fresh, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        moveAtomic(tmp, keyFile);
        tryRestrictPermissions(keyFile);
        return fresh;
    }

    private String encrypt(String plain) throws Exception {
        byte[] iv = new byte[IV_LEN];
        RANDOM.nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(TAG_LEN, iv));
        byte[] cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));

        byte[] packed = new byte[iv.length + cipherText.length];
        System.arraycopy(iv, 0, packed, 0, iv.length);
        System.arraycopy(cipherText, 0, packed, iv.length, cipherText.length);

        return Base64.getEncoder().encodeToString(packed);
    }

    private String decrypt(String blob) throws Exception {
        byte[] packed = Base64.getDecoder().decode(blob);
        if (packed.length <= IV_LEN) throw new IllegalArgumentException("cipher blob too short");
        byte[] iv = new byte[IV_LEN];
        byte[] ct = new byte[packed.length - IV_LEN];
        System.arraycopy(packed, 0, iv, 0, IV_LEN);
        System.arraycopy(packed, IV_LEN, ct, 0, ct.length);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(TAG_LEN, iv));
        byte[] plain = cipher.doFinal(ct);
        return new String(plain, StandardCharsets.UTF_8);
    }

    private void persist() throws IOException {
        Path tmp = storeFile.resolveSibling(storeFile.getFileName().toString() + ".tmp");
        byte[] bytes = Jsons.MAPPER.writeValueAsBytes(encrypted);
        Files.write(tmp, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        moveAtomic(tmp, storeFile);
        tryRestrictPermissions(storeFile);
    }

    private static void moveAtomic(Path tmp, Path target) throws IOException {
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static byte[] trim(byte[] src, int len) {
        byte[] out = new byte[len];
        System.arraycopy(src, 0, out, 0, Math.min(src.length, len));
        return out;
    }

    private static void tryRestrictPermissions(Path file) {
        try {
            Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
            Files.setPosixFilePermissions(file, perms);
        } catch (Exception ignored) {
            // best-effort; may be unsupported on Windows
        }
    }

    private static void safeDelete(Path p) {
        try { Files.deleteIfExists(p); } catch (Exception ignored) {}
    }
}
