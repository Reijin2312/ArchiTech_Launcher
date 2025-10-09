    package org.architech.launcher.authentication.account;

    import org.architech.launcher.ArchiTechLauncher;
    import org.architech.launcher.authentication.keystorage.FileEncryptedSecretStorage;
    import org.architech.launcher.authentication.keystorage.OsKeyringSecretStorage;
    import org.architech.launcher.authentication.keystorage.SecretStorage;
    import org.architech.launcher.utils.Jsons;
    import org.architech.launcher.utils.logging.LogManager;
    import java.io.IOException;
    import java.nio.charset.StandardCharsets;
    import java.nio.file.*;
    import java.nio.file.attribute.PosixFilePermission;
    import java.nio.file.attribute.PosixFilePermissions;
    import java.util.Set;
    import java.util.logging.Level;

    public final class AccountStore {
        private static final Path FILE = ArchiTechLauncher.ACCOUNT_FILE;
        public static final SecretStorage SECRETS = initSecretStorage();

        private static SecretStorage initSecretStorage() {
            try {
                return new OsKeyringSecretStorage("org.architech.launcher");
            } catch (Throwable t) {
                Path secretsPath = Path.of(System.getProperty("user.home"), ".architech", "secrets.bin");
                try {
                    return new FileEncryptedSecretStorage(secretsPath);
                } catch (IOException e) {
                    LogManager.getLogger().warning("Не удалось инициализировать FileEncryptedSecretStorage: " + e.getMessage());
                    return null;
                }
            }
        }

        public static String secretKeyName(Account a, String name) {
            String id = a != null && a.getUuid() != null ? a.getUuid() : "current";
            return "architech:user:" + id + ":" + name;
        }

        public static Account load() {
            try {
                if (Files.exists(FILE)) {
                    String raw = Files.readString(FILE, StandardCharsets.UTF_8);
                    Account a = Jsons.MAPPER.readValue(raw, Account.class);
                    if (SECRETS != null && a != null) {
                        try {
                            SECRETS.getSecret(secretKeyName(a, "refreshToken")).ifPresent(a::setRefreshToken);
                        } catch (Exception e) {
                            LogManager.getLogger().warning("Не удалось прочитать секреты: " + e.getMessage());
                        }
                    }
                    return a;
                }
            } catch (Exception ex) {
                LogManager.getLogger().severe("Ошибка загрузки файла аккаунта: " + ex.getMessage());
            }

            Account a = new Account();
            a.setUsername("Player");
            a.setUuid(UUIDs.offlineUuid(a.getUsername()));
            a.setRefreshToken("0");
            a.setRefreshExpiresAtSec(System.currentTimeMillis() / 1000 + 31536000L);
            return a;
        }

        public static void save(Account a){
            try {
                if(a == null) {
                    Files.deleteIfExists(FILE);
                    if (SECRETS != null) {
                        try { SECRETS.deleteSecret("architech:current:launcherToken"); } catch (Exception ignored) {}
                        try { SECRETS.deleteSecret("architech:user:current:refreshToken"); } catch (Exception ignored) {}
                    }
                    return;
                }

                try {
                    if (SECRETS != null) {
                        if (a.getRefreshToken() != null) SECRETS.putSecret(secretKeyName(a, "refreshToken"), a.getRefreshToken());
                        try { SECRETS.deleteSecret(secretKeyName(a, "launcherToken")); } catch (Exception ignored) {}
                        try { SECRETS.deleteSecret(secretKeyName(a, "msaRefreshToken")); } catch (Exception ignored) {}
                        try { SECRETS.deleteSecret(secretKeyName(a, "mcAccessToken")); } catch (Exception ignored) {}
                    }
                } catch (Exception e) {
                    LogManager.getLogger().warning("Не удалось сохранить секреты: " + e.getMessage());
                }

                Account copy = Jsons.MAPPER.readValue(Jsons.MAPPER.writeValueAsString(a), Account.class);
                copy.setRefreshToken(null);
                copy.setAccessToken(null);

                Files.createDirectories(FILE.getParent());
                Path tmp = FILE.resolveSibling(FILE.getFileName().toString() + ".tmp");
                String json = Jsons.MAPPER.writeValueAsString(copy);
                Files.writeString(tmp, json, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                try {
                    Files.move(tmp, FILE, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING);
                }
                try {
                    Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
                    Files.setPosixFilePermissions(FILE, perms);
                } catch (UnsupportedOperationException ignored) {}
            } catch (IOException ex) {
                LogManager.getLogger().log(Level.SEVERE, "Ошибка сохранения файла аккаунта", ex);
            }
        }

    }