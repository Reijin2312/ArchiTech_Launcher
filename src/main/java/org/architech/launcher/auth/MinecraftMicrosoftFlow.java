package org.architech.launcher.auth;

import org.json.JSONArray;
import org.json.JSONObject;
import java.time.Instant;

public class MinecraftMicrosoftFlow {
    public static class McSession {
        public final String mcAccessToken;         // токен Minecraft Services (Bearer)
        public final Instant mcAccessTokenExp;     // приблизительная дата истечения (из expires_in)
        public final String uuid;                  // UUID без дефисов
        public final String username;              // ник
        public final boolean ownsMinecraft;        // проверка владения

        public McSession(String token, long expiresIn, String uuid, String name, boolean owns) {
            this.mcAccessToken = token;
            this.mcAccessTokenExp = Instant.now().plusSeconds(expiresIn - 30);
            this.uuid = uuid;
            this.username = name;
            this.ownsMinecraft = owns;
        }
    }

    // 4.1: Microsoft access_token -> Xbox Live user token (RPS)
    private static JSONObject xboxLiveAuthenticate(String msaAccessToken) throws Exception {
        JSONObject payload = new JSONObject()
                .put("Properties", new JSONObject()
                        .put("AuthMethod", "RPS")
                        .put("SiteName", "user.auth.xboxlive.com")
                        .put("RpsTicket", "d=" + msaAccessToken))
                .put("RelyingParty", "http://auth.xboxlive.com")
                .put("TokenType", "JWT");

        return HttpJson.postJson("https://user.auth.xboxlive.com/user/authenticate", payload);
    }

    // 4.2: XBL -> XSTS (для Minecraft services)
    private static JSONObject xstsAuthorize(String xblToken) throws Exception {
        JSONObject payload = new JSONObject()
                .put("Properties", new JSONObject()
                        .put("SandboxId", "RETAIL")
                        .put("UserTokens", new JSONArray().put(xblToken)))
                .put("RelyingParty", "rp://api.minecraftservices.com/")
                .put("TokenType", "JWT");

        return HttpJson.postJson("https://xsts.auth.xboxlive.com/xsts/authorize", payload);
    }

    // 4.3: XSTS -> Minecraft access_token
    private static JSONObject minecraftLoginWithXbox(String uhs, String xstsToken) throws Exception {
        JSONObject payload = new JSONObject()
                .put("identityToken", "XBL3.0 x=" + uhs + ";" + xstsToken);
        return HttpJson.postJson("https://api.minecraftservices.com/authentication/login_with_xbox", payload);
    }

    // 4.4: Проверка владения лицензией Java Edition
    private static boolean checkOwnership(String mcAccessToken) throws Exception {
        // Если items[].length > 0 — владеет (историческое «mcstore»).
        JSONObject ent = HttpJson.getJson("https://api.minecraftservices.com/entitlements/mcstore", mcAccessToken);
        JSONArray items = ent.optJSONArray("items");
        return items != null && items.length() > 0;
    }

    // 4.5: Профиль Minecraft (UUID + ник)
    private static JSONObject minecraftProfile(String mcAccessToken) throws Exception {
        return HttpJson.getJson("https://api.minecraftservices.com/minecraft/profile", mcAccessToken);
    }

    public static McSession fullLogin(String msaAccessToken) throws Exception {
        // XBL
        JSONObject xbl = xboxLiveAuthenticate(msaAccessToken);
        String xblToken = xbl.getString("Token");
        String uhs = xbl.getJSONObject("DisplayClaims").getJSONArray("xui")
                .getJSONObject(0).getString("uhs");

        // XSTS
        JSONObject xsts = xstsAuthorize(xblToken);
        String xstsToken = xsts.getString("Token");
        // (uhs обычно совпадает, но берем из XSTS при желании)
        String uhs2 = xsts.getJSONObject("DisplayClaims").getJSONArray("xui")
                .getJSONObject(0).getString("uhs");

        // MC token
        JSONObject mc = minecraftLoginWithXbox(uhs2, xstsToken);
        String mcAccess = mc.getString("access_token");
        long expiresIn = mc.getLong("expires_in");

        // Владелец?
        boolean owns = checkOwnership(mcAccess);

        // Профиль
        JSONObject prof = minecraftProfile(mcAccess);
        String uuid = prof.getString("id");
        String name = prof.getString("name");

        return new McSession(mcAccess, expiresIn, uuid, name, owns);
    }
}
