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
        private static final SecretStorage SECRETS = initSecretStorage();

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

        private static String secretKeyName(Account a, String name) {
            String id = a.getUuid() != null ? a.getUuid() : "current";
            return "architech:" + a.getType() + ":" + id + ":" + name;
        }

        public static Account load() {
            try {
                if (Files.exists(FILE)) {
                    String raw = Files.readString(FILE, StandardCharsets.UTF_8);
                    Account a = Jsons.MAPPER.readValue(raw, Account.class);

                    try {
                        SECRETS.getSecret(secretKeyName(a, "launcherToken")).ifPresent(a::setLauncherToken);
                        SECRETS.getSecret(secretKeyName(a, "msaRefreshToken")).ifPresent(a::setMsaRefreshToken);
                        SECRETS.getSecret(secretKeyName(a, "mcAccessToken")).ifPresent(a::setAccessToken);
                    } catch (Exception e) {
                        LogManager.getLogger().warning("Не удалось прочитать секреты: " + e.getMessage());
                    }

                    boolean migrated = false;
                    if (a.getLauncherToken() != null) {
                        try { SECRETS.putSecret(secretKeyName(a, "launcherToken"), a.getLauncherToken()); }
                        catch (Exception ignore) {}
                        a.setLauncherToken(null); migrated = true;
                    }
                    if (a.getMsaRefreshToken() != null) {
                        try { SECRETS.putSecret(secretKeyName(a, "msaRefreshToken"), a.getMsaRefreshToken()); }
                        catch (Exception ignore) {}
                        a.setMsaRefreshToken(null); migrated = true;
                    }
                    if (a.getAccessToken() != null) {
                        try { SECRETS.putSecret(secretKeyName(a, "mcAccessToken"), a.getAccessToken()); }
                        catch (Exception ignore) {}
                        a.setAccessToken(null); migrated = true;
                    }
                    if (migrated) save(a);
                    return a;
                }

            } catch (Exception ex) {
                LogManager.getLogger().severe("Ошибка загрузки файла аккаунта: " + ex.getMessage());
            }

            Account a = new Account();
            a.setType(AccountType.OFFLINE);
            a.setUserType("legacy");
            a.setUsername("Player");
            a.setUuid(UUIDs.offlineUuid(a.getUsername()));
            a.setAccessToken("0");
            a.setExpiresAtSec(System.currentTimeMillis() / 1000 + 31536000L);
            return a;
        }

        public static void save(Account a){
            try {
                if(a == null){
                    Files.deleteIfExists(FILE);
                    try {
                        SECRETS.deleteSecret("architech:current:launcherToken");
                    } catch (Exception ignored) {}
                    return;
                }

                try {
                    if (a.getLauncherToken() != null) SECRETS.putSecret(secretKeyName(a, "launcherToken"), a.getLauncherToken());
                    if (a.getMsaRefreshToken() != null) SECRETS.putSecret(secretKeyName(a, "msaRefreshToken"), a.getMsaRefreshToken());
                    if (a.getAccessToken() != null) SECRETS.putSecret(secretKeyName(a, "mcAccessToken"), a.getAccessToken());
                } catch (Exception e) {
                    LogManager.getLogger().warning("Не удалось сохранить секреты: " + e.getMessage());
                }

                Account copy = Jsons.MAPPER.readValue(Jsons.MAPPER.writeValueAsString(a), Account.class);
                copy.setLauncherToken(null);
                copy.setMsaRefreshToken(null);
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