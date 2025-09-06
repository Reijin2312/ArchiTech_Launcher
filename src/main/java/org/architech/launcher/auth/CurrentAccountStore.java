    package org.architech.launcher.auth;

    import com.google.gson.Gson;
    import com.google.gson.GsonBuilder;
    import org.architech.launcher.MCLauncher;
    import org.architech.launcher.utils.LogManager;

    import java.io.IOException;
    import java.nio.charset.StandardCharsets;
    import java.nio.file.*;

    public final class CurrentAccountStore {
        private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
        private static final Path FILE = MCLauncher.ACCOUNT_FILE;

        public static Account load(){
            try {
                if(Files.exists(FILE)){
                    String raw = Files.readString(FILE, StandardCharsets.UTF_8);
                    return GSON.fromJson(raw, Account.class);
                }
            } catch(Exception ex)
            {
                LogManager.getLogger().severe("Ошибка загрузки файла аккаунта: " + ex.getMessage());
            }

            Account a = new Account();
            a.type = AccountType.OFFLINE; a.userType = "legacy";
            a.username = "Player"; a.uuid = Uuids.offlineUuid(a.username);
            a.accessToken = "0"; a.expiresAtSec = System.currentTimeMillis()/1000 + 31536000L;
            return a;
        }

        public static void save(Account a){
            try {
                if(a==null){
                    Files.deleteIfExists(FILE); return;
                }
                Files.createDirectories(FILE.getParent());
                String json = GSON.toJson(a);
                Files.writeString(FILE, json, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch(IOException ex) {
                LogManager.getLogger().severe("Ошибка сохранения файла аккаунта: " + ex.getMessage());
            }
        }
    }