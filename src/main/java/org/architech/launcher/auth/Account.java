// org.architech.launcher.auth.Account
package org.architech.launcher.auth;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class Account {
    public AccountType type;
    public String userType;
    public String username;
    public String uuid;
    public String accessToken;
    public String refreshToken;
    public long expiresAtSec;
    public String avatarUrl;
    public String skinUrl;
    public String email;
    public String mcAccessToken;
    public long  mcAccessTokenExpiresAt;
    public String msaRefreshToken;
    public boolean ownsMinecraft;
    public String launcherToken;

    public static Account offline(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            nickname = "Player";
        }

        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + nickname).getBytes(StandardCharsets.UTF_8));

        Account a = new Account();
        a.type = AccountType.OFFLINE;
        a.userType = "legacy";
        a.username = nickname;
        a.uuid = uuid.toString();
        a.accessToken = "0";
        a.refreshToken = null;
        a.expiresAtSec = System.currentTimeMillis() / 1000L + (365L * 24 * 3600); // год вперёд
        a.avatarUrl = "https://crafatar.com/avatars/" + uuid.toString().replace("-", "") + "?size=20&overlay";
        return a;
    }
}