package org.architech.launcher.authentication.keystorage;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Base64;
import com.fasterxml.jackson.databind.ObjectMapper;

public class FileEncryptedSecretStorage implements SecretStorage {
    private static final Logger LOG = Logger.getLogger(FileEncryptedSecretStorage.class.getName());

    private static final int KEY_LEN = 32; // 256 bits
    private static final int IV_LEN = 12;  // 96 bits recommended for GCM
    private static final int TAG_LEN = 128; // bits
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path storeFile; // e.g. ~/.architech/secrets.bin
    private final Path keyFile;   // e.g. ~/.architech/secrets.bin.key
    private final SecretKey aesKey;
    // map: secretKey -> base64(iv + ciphertext)
    private final Map<String, String> encryptedMap = new ConcurrentHashMap<>();

    public FileEncryptedSecretStorage(Path storeFile) throws IOException {
        this.storeFile = storeFile;
        this.keyFile = storeFile.resolveSibling(storeFile.getFileName().toString() + ".key");
        ensureParentExists();

        byte[] keyBytes = readOrCreateKey();
        this.aesKey = new SecretKeySpec(keyBytes, "AES");

        // load existing store if present
        if (Files.exists(this.storeFile)) {
            try {
                byte[] raw = Files.readAllBytes(this.storeFile);
                if (raw.length > 0) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> loaded = JSON.readValue(raw, Map.class);
                    if (loaded != null) encryptedMap.putAll(loaded);
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Не удалось прочитать/десериализовать secrets.bin — будет пустое хранилище: " + e.getMessage(), e);
            }
        }
    }

    private void ensureParentExists() throws IOException {
        Path parent = storeFile.getParent();
        if (parent != null) Files.createDirectories(parent);
    }

    private byte[] readOrCreateKey() throws IOException {
        if (Files.exists(keyFile)) {
            byte[] kb = Files.readAllBytes(keyFile);
            if (kb.length >= KEY_LEN) {
                return Arrays.copyOf(kb, KEY_LEN);
            } else {
                LOG.warning("Файл ключа меньше ожидаемого — перегенерируем ключ.");
                secureDeleteFile(keyFile);
            }
        }

        // create new key
        byte[] newKey = new byte[KEY_LEN];
        RANDOM.nextBytes(newKey);

        // write atomically
        Path tmp = keyFile.resolveSibling(keyFile.getFileName().toString() + ".tmp");
        Files.write(tmp, newKey, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        try {
            Files.move(tmp, keyFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tmp, keyFile, StandardCopyOption.REPLACE_EXISTING);
        }

        // try set secure perms (best effort)
        trySetSecurePermissions(keyFile);

        return newKey;
    }

    private void trySetSecurePermissions(Path p) {
        try {
            // POSIX
            try {
                Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
                Files.setPosixFilePermissions(p, perms);
                return;
            } catch (UnsupportedOperationException ignored) {}

            // Windows ACL best-effort: give only current user read/write/delete
            try {
                AclFileAttributeView aclView = Files.getFileAttributeView(p, AclFileAttributeView.class);
                if (aclView != null) {
                    UserPrincipalLookupService lookup = p.getFileSystem().getUserPrincipalLookupService();
                    UserPrincipal user = lookup.lookupPrincipalByName(System.getProperty("user.name"));

                    AclEntry.Builder builder = AclEntry.newBuilder();
                    builder.setType(AclEntryType.ALLOW);
                    builder.setPrincipal(user);
                    Set<AclEntryPermission> perms = EnumSet.of(
                            AclEntryPermission.READ_DATA,
                            AclEntryPermission.WRITE_DATA,
                            AclEntryPermission.DELETE,
                            AclEntryPermission.READ_ATTRIBUTES,
                            AclEntryPermission.WRITE_ATTRIBUTES
                    );
                    builder.setPermissions(perms);

                    List<AclEntry> entries = Collections.singletonList(builder.build());
                    aclView.setAcl(entries);
                }
            } catch (Throwable t) {
                LOG.fine("Не удалось установить Windows ACL: " + t.getMessage());
            }
        } catch (Throwable t) {
            LOG.fine("Не удалось задать права файла: " + t.getMessage());
        }
    }

    private String encrypt(String plain) throws Exception {
        byte[] iv = new byte[IV_LEN];
        RANDOM.nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(TAG_LEN, iv);
        c.init(Cipher.ENCRYPT_MODE, aesKey, spec);
        byte[] ciphertext = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        byte[] out = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(ciphertext, 0, out, iv.length, ciphertext.length);
        return Base64.getEncoder().encodeToString(out);
    }

    private String decrypt(String encoded) throws Exception {
        byte[] all = Base64.getDecoder().decode(encoded);
        if (all.length < IV_LEN) throw new IllegalArgumentException("invalid cipher blob");
        byte[] iv = Arrays.copyOfRange(all, 0, IV_LEN);
        byte[] ct = Arrays.copyOfRange(all, IV_LEN, all.length);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(TAG_LEN, iv));
        byte[] plain = c.doFinal(ct);
        return new String(plain, StandardCharsets.UTF_8);
    }

    private synchronized void persist() throws IOException {
        // atomic write
        Path tmp = storeFile.resolveSibling(storeFile.getFileName().toString() + ".tmp");
        byte[] bytes = JSON.writeValueAsBytes(encryptedMap);
        Files.write(tmp, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        try {
            Files.move(tmp, storeFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tmp, storeFile, StandardCopyOption.REPLACE_EXISTING);
        }
        // try set secure perms on store file as well
        trySetSecurePermissions(storeFile);
    }

    @Override
    public synchronized void putSecret(String key, String secret) throws Exception {
        if (secret == null) {
            deleteSecret(key);
            return;
        }
        String enc = encrypt(secret);
        encryptedMap.put(key, enc);
        persist();
    }

    @Override
    public synchronized Optional<String> getSecret(String key) throws Exception {
        String enc = encryptedMap.get(key);
        if (enc == null) return Optional.empty();
        try {
            String plain = decrypt(enc);
            return Optional.ofNullable(plain);
        } catch (javax.crypto.AEADBadTagException ae) {
            LOG.log(Level.WARNING, "AEAD error while decrypting secret for key=" + key + " — data may be corrupted/tampered. Removing this entry.");
            // remove corrupted entry and persist change (best-effort)
            encryptedMap.remove(key);
            try { persist(); } catch (IOException ignore) {}
            return Optional.empty();
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Ошибка при дешифровании секрета: " + ex.getMessage(), ex);
            return Optional.empty();
        }
    }

    @Override
    public synchronized void deleteSecret(String key) throws Exception {
        if (encryptedMap.remove(key) != null) {
            persist();
        }
    }

    /**
     * Удалить все данные и удалить ключ (использовать при logout/удалении аккаунта).
     * Best-effort: переписывает файл ключа нулями перед удалением.
     */
    public synchronized void deleteAll() {
        try {
            encryptedMap.clear();
            if (Files.exists(storeFile)) Files.deleteIfExists(storeFile);
        } catch (Exception e) {
            LOG.log(Level.FINE, "Не удалось удалить storeFile: " + e.getMessage());
        }
        try {
            secureDeleteFile(keyFile);
        } catch (Exception e) {
            LOG.log(Level.FINE, "Не удалось удалить keyFile: " + e.getMessage());
        }
    }

    private void secureDeleteFile(Path p) {
        try {
            if (Files.exists(p)) {
                try {
                    byte[] zeros = new byte[Math.max(1, (int)Math.min(1024, Files.size(p)))];
                    Arrays.fill(zeros, (byte)0);
                    Files.write(p, zeros, StandardOpenOption.WRITE);
                } catch (Throwable t) {
                    // best-effort overwrite
                }
                Files.deleteIfExists(p);
            }
        } catch (Throwable t) {
            LOG.fine("secureDeleteFile failed: " + t.getMessage());
        }
    }
}

