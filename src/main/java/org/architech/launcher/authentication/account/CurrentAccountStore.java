    package org.architech.launcher.authentication.account;

    import org.architech.launcher.MCLauncher;
    import org.architech.launcher.utils.Jsons;
    import org.architech.launcher.utils.logging.LogManager;
    import java.io.IOException;
    import java.nio.charset.StandardCharsets;
    import java.nio.file.*;
    import java.nio.file.attribute.PosixFilePermission;
    import java.nio.file.attribute.PosixFilePermissions;
    import java.util.Set;
    import java.util.logging.Level;

    public final class CurrentAccountStore {
        private static final Path FILE = MCLauncher.ACCOUNT_FILE;

        public static Account load() {
            try {
                if (Files.exists(FILE)) {
                    String raw = Files.readString(FILE, StandardCharsets.UTF_8);
                    return Jsons.MAPPER.readValue(raw, Account.class);
                }
            } catch (Exception ex) {
                LogManager.getLogger().severe("Ошибка загрузки файла аккаунта: " + ex.getMessage());
            }

            Account a = new Account();
            a.type = AccountType.OFFLINE;
            a.userType = "legacy";
            a.username = "Player";
            a.uuid = UUIDs.offlineUuid(a.username);
            a.accessToken = "0";
            a.expiresAtSec = System.currentTimeMillis() / 1000 + 31536000L;
            return a;
        }

        public static void save(Account a){
            try {
                if(a == null){
                    Files.deleteIfExists(FILE);
                    return;
                }
                Files.createDirectories(FILE.getParent());
                Path tmp = FILE.resolveSibling(FILE.getFileName().toString() + ".tmp");
                String json = Jsons.MAPPER.writeValueAsString(a);
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