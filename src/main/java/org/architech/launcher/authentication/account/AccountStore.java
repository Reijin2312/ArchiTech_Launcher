package org.architech.launcher.authentication.account;

import com.fasterxml.jackson.core.type.TypeReference;
import org.architech.launcher.ArchiTechLauncher;
import org.architech.launcher.authentication.keystorage.LocalEncryptedSecretStorage;
import org.architech.launcher.authentication.keystorage.SecretStorage;
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
import java.util.*;
import java.util.logging.Level;

/**
 * Stores non-sensitive account data on disk, while tokens live in a local AES-encrypted store
 * located next to .account.json in the launcher directory. No OS keyring dependency.
 */
public final class AccountStore {
    private static final Path SECRET_FILE = ArchiTechLauncher.ACCOUNT_FILE.resolveSibling(".account.secrets");
    private static final Path SECRET_KEY_FILE = SECRET_FILE.resolveSibling(SECRET_FILE.getFileName().toString() + ".key");
    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {};

    private static final SecretStorage STORE = initStore();

    private AccountStore() {}

    private static SecretStorage initStore() {
        try {
            LocalEncryptedSecretStorage storage = new LocalEncryptedSecretStorage(SECRET_FILE, SECRET_KEY_FILE);
            migrateLegacySecrets(storage);
            return storage;
        } catch (IOException e) {
            LogManager.getLogger().severe("Secret store initialization failed: " + e.getMessage());
            return null;
        }
    }

    public static Optional<String> getSecret(Account account, String name) {
        if (STORE == null) return Optional.empty();
        for (String key : secretKeys(account, name)) {
            try {
                Optional<String> v = STORE.getSecret(key);
                if (v.isPresent()) return v;
            } catch (Exception e) {
                LogManager.getLogger().warning("Secret read failed for " + key + ": " + e.getMessage());
            }
        }
        return Optional.empty();
    }

    private static void writeSecret(Account account, String name, String value) {
        if (value == null) {
            deleteSecret(account, name);
            return;
        }
        if (STORE == null) return;
        for (String key : secretKeys(account, name)) {
            try {
                STORE.putSecret(key, value);
            } catch (Exception e) {
                LogManager.getLogger().severe("Unable to store secret (" + key + "): " + e.getMessage());
            }
        }
    }

    private static void deleteSecret(Account account, String name) {
        if (STORE == null) return;
        for (String key : secretKeys(account, name)) {
            try { STORE.deleteSecret(key); } catch (Exception ignored) {}
        }
    }

    public static Account load() {
        Path primary = ArchiTechLauncher.ACCOUNT_FILE;
        Path secondary = ArchiTechLauncher.GAME_DIR.resolve("config").resolve(".account.json"); // legacy/migration
        Path[] candidates = primary.equals(secondary) ? new Path[]{primary} : new Path[]{primary, secondary};
        for (Path p : candidates) {
            try {
                if (Files.exists(p)) {
                    String raw = Files.readString(p, StandardCharsets.UTF_8);
                    Account a = Jsons.MAPPER.readValue(raw, Account.class);
                    if (a != null) {
                        getSecret(a, "refreshToken").ifPresent(a::setRefreshToken);
                        if (!p.equals(primary)) {
                            save(a); // migrate from legacy location
                        }
                        return a;
                    }
                }
            } catch (Exception ex) {
                LogManager.getLogger().warning("Failed to read account file " + p + ": " + ex.getMessage());
            }
        }

        Account a = new Account();
        a.setUsername("Player");
        a.setUuid(UUIDs.offlineUuid(a.getUsername()));
        a.setRefreshToken("0");
        a.setRefreshExpiresAtSec(System.currentTimeMillis() / 1000 + 31536000L);
        LogManager.getLogger().warning("No account file found; created default guest account");
        return a;
    }

    public static void save(Account a){
        Path target = ArchiTechLauncher.ACCOUNT_FILE;
        try {
            if(a == null) {
                Files.deleteIfExists(target);
                deleteSecret(null, "launcherToken");
                deleteSecret(null, "refreshToken");
                return;
            }

            if (a.getRefreshToken() != null) {
                writeSecret(a, "refreshToken", a.getRefreshToken());
            }
            deleteSecret(a, "launcherToken");
            deleteSecret(a, "msaRefreshToken");
            deleteSecret(a, "mcAccessToken");

            Account copy = Jsons.MAPPER.readValue(Jsons.MAPPER.writeValueAsString(a), Account.class);
            copy.setRefreshToken(null);
            copy.setAccessToken(null);

            Files.createDirectories(target.getParent());
            Path tmp = target.resolveSibling(target.getFileName().toString() + ".tmp");
            String json = Jsons.MAPPER.writeValueAsString(copy);
            Files.writeString(tmp, json, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
                Files.setPosixFilePermissions(target, perms);
            } catch (UnsupportedOperationException ignored) {}
        } catch (IOException ex) {
            LogManager.getLogger().log(Level.SEVERE, "Failed to write account file", ex);
        }
    }

    private static void migrateLegacySecrets(LocalEncryptedSecretStorage storage) {
        Path dir = SECRET_FILE.getParent();
        if (dir == null) return;

        boolean migrated = false;
        migrated |= importPlain(dir.resolve("secrets.json"), storage);
        migrated |= importEncrypted(dir.resolve("secrets.bin"), dir.resolve("secrets.bin.key"), storage);

        if (migrated) {
            LogManager.getLogger().info("Migrated legacy secret storage into new local encrypted store");
        }
    }

    private static boolean importPlain(Path plainFile, LocalEncryptedSecretStorage storage) {
        if (!Files.exists(plainFile)) return false;
        try {
            Map<String, String> data = Jsons.MAPPER.readValue(Files.readString(plainFile, StandardCharsets.UTF_8), MAP_TYPE);
            if (data != null && !data.isEmpty()) {
                for (Map.Entry<String, String> e : data.entrySet()) {
                    if (e.getValue() != null) {
                        storage.putSecret(e.getKey(), e.getValue());
                    }
                }
                try { Files.deleteIfExists(plainFile); } catch (Exception ignored) {}
                return true;
            }
        } catch (Exception e) {
            LogManager.getLogger().warning("Plain secret migration failed: " + e.getMessage());
        }
        return false;
    }

    private static boolean importEncrypted(Path encFile, Path keyFile, LocalEncryptedSecretStorage storage) {
        if (!Files.exists(encFile) || !Files.exists(keyFile)) return false;
        try {
            byte[] keyBytes = Files.readAllBytes(keyFile);
            if (keyBytes.length < 32) {
                LogManager.getLogger().warning("Legacy key file is invalid, skipping encrypted migration");
                return false;
            }
            SecretKeySpec legacyKey = new SecretKeySpec(Arrays.copyOf(keyBytes, 32), "AES");
            Map<String, String> blobs = Jsons.MAPPER.readValue(Files.readAllBytes(encFile), MAP_TYPE);
            if (blobs == null || blobs.isEmpty()) return false;

            int imported = 0;
            for (Map.Entry<String, String> e : blobs.entrySet()) {
                if (e.getValue() == null) continue;
                try {
                    String plain = decryptLegacy(e.getValue(), legacyKey);
                    storage.putSecret(e.getKey(), plain);
                    imported++;
                } catch (Exception ex) {
                    LogManager.getLogger().warning("Failed to migrate legacy secret " + e.getKey() + ": " + ex.getMessage());
                }
            }
            if (imported > 0) {
                try { Files.deleteIfExists(encFile); } catch (Exception ignored) {}
                try { Files.deleteIfExists(keyFile); } catch (Exception ignored) {}
                return true;
            }
        } catch (Exception e) {
            LogManager.getLogger().warning("Encrypted secret migration failed: " + e.getMessage());
        }
        return false;
    }

    private static List<String> secretKeys(Account a, String name) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        if (a != null) {
            if (notBlank(a.getUuid())) keys.add(buildKey(a.getUuid(), name));
            if (notBlank(a.getEmail())) keys.add(buildKey(a.getEmail(), name));
            if (notBlank(a.getUsername())) keys.add(buildKey(a.getUsername(), name));
        }
        keys.add(buildKey("current", name));
        return new ArrayList<>(keys);
    }

    private static String buildKey(String id, String name) {
        return "architech:user:" + id + ":" + name;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String decryptLegacy(String encoded, SecretKeySpec key) throws Exception {
        byte[] packed = Base64.getDecoder().decode(encoded);
        if (packed.length <= 12) throw new IllegalArgumentException("cipher blob too short");
        byte[] iv = Arrays.copyOfRange(packed, 0, 12);
        byte[] ct = Arrays.copyOfRange(packed, 12, packed.length);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
        byte[] plain = cipher.doFinal(ct);
        return new String(plain, StandardCharsets.UTF_8);
    }
}
