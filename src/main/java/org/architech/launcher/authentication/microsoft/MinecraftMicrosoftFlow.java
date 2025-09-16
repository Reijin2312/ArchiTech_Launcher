package org.architech.launcher.authentication.microsoft;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.architech.launcher.authentication.json.HttpJson;
import org.architech.launcher.utils.Jsons;

import java.time.Instant;

public class MinecraftMicrosoftFlow {

    public static class McSession {
        public final String mcAccessToken;
        public final Instant mcAccessTokenExp;
        public final String uuid;
        public final String username;
        public final boolean ownsMinecraft;

        public McSession(String token, long expiresIn, String uuid, String name, boolean owns) {
            this.mcAccessToken = token;
            this.mcAccessTokenExp = Instant.now().plusSeconds(expiresIn - 30);
            this.uuid = uuid;
            this.username = name;
            this.ownsMinecraft = owns;
        }
    }

    // 4.1: Microsoft access_token -> Xbox Live user token (RPS)
    private static JsonNode xboxLiveAuthenticate(String msaAccessToken) throws Exception {
        ObjectNode properties = Jsons.MAPPER.createObjectNode();
        properties.put("AuthMethod", "RPS");
        properties.put("SiteName", "user.auth.xboxlive.com");
        properties.put("RpsTicket", "d=" + msaAccessToken);

        ObjectNode payload = Jsons.MAPPER.createObjectNode();
        payload.set("Properties", properties);
        payload.put("RelyingParty", "http://auth.xboxlive.com");
        payload.put("TokenType", "JWT");

        return HttpJson.postJson("https://user.auth.xboxlive.com/user/authenticate", payload);
    }

    // 4.2: XBL -> XSTS (для Minecraft services)
    private static JsonNode xstsAuthorize(String xblToken) throws Exception {
        ObjectNode properties = Jsons.MAPPER.createObjectNode();
        properties.put("SandboxId", "RETAIL");
        ArrayNode userTokens = Jsons.MAPPER.createArrayNode();
        userTokens.add(xblToken);
        properties.set("UserTokens", userTokens);

        ObjectNode payload = Jsons.MAPPER.createObjectNode();
        payload.set("Properties", properties);
        payload.put("RelyingParty", "rp://api.minecraftservices.com/");
        payload.put("TokenType", "JWT");

        return HttpJson.postJson("https://xsts.auth.xboxlive.com/xsts/authorize", payload);
    }

    // 4.3: XSTS -> Minecraft access_token
    private static JsonNode minecraftLoginWithXbox(String uhs, String xstsToken) throws Exception {
        ObjectNode payload = Jsons.MAPPER.createObjectNode();
        payload.put("identityToken", "XBL3.0 x=" + uhs + ";" + xstsToken);
        return HttpJson.postJson("https://api.minecraftservices.com/authentication/login_with_xbox", payload);
    }

    // 4.4: Проверка владения лицензией Java Edition
    private static boolean checkOwnership(String mcAccessToken) throws Exception {
        JsonNode ent = HttpJson.getJson("https://api.minecraftservices.com/entitlements/mcstore", mcAccessToken);
        JsonNode items = ent.get("items");
        return items != null && items.isArray() && items.size() > 0;
    }

    // 4.5: Профиль Minecraft (UUID + ник)
    private static JsonNode minecraftProfile(String mcAccessToken) throws Exception {
        return HttpJson.getJson("https://api.minecraftservices.com/minecraft/profile", mcAccessToken);
    }

    public static McSession fullLogin(String msaAccessToken) throws Exception {
        // XBL
        JsonNode xbl = xboxLiveAuthenticate(msaAccessToken);
        String xblToken = xbl.get("Token").asText();
        String uhs = xbl.get("DisplayClaims").get("xui").get(0).get("uhs").asText();

        // XSTS
        JsonNode xsts = xstsAuthorize(xblToken);
        String xstsToken = xsts.get("Token").asText();
        String uhs2 = xsts.get("DisplayClaims").get("xui").get(0).get("uhs").asText();

        // MC token
        JsonNode mc = minecraftLoginWithXbox(uhs2, xstsToken);
        String mcAccess = mc.get("access_token").asText();
        long expiresIn = mc.get("expires_in").asLong();

        // Владелец?
        boolean owns = checkOwnership(mcAccess);

        // Профиль
        JsonNode prof = minecraftProfile(mcAccess);
        String uuid = prof.get("id").asText();
        String name = prof.get("name").asText();

        return new McSession(mcAccess, expiresIn, uuid, name, owns);
    }
}
