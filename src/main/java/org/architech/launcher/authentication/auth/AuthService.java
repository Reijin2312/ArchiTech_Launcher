package org.architech.launcher.authentication.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import org.architech.launcher.ArchiTechLauncher;
import org.architech.launcher.authentication.account.Account;
import org.architech.launcher.authentication.account.AccountManager;
import org.architech.launcher.gui.error.AuthExpiredPanel;
import org.architech.launcher.utils.logging.LogManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.architech.launcher.ArchiTechLauncher.BACKEND_URL;

public final class AuthService {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(ArchiTechLauncher.HTTP_TIMEOUT))
            .build();

    private AuthService() {}

    /**
     * Проверяет и обновляет токены если необходимо.
     * Возвращает true если токены валидны/обновлены, false если требуется повторная авторизация.
     * @param showDialogOnFailure если true, показывает диалог пользователю при невалидном токене
     */
    public static boolean ensureValidTokens(boolean showDialogOnFailure) {
        Account acc = AccountManager.getCurrentAccount();
        if (acc == null || acc.getRefreshToken() == null || acc.getRefreshToken().equals("0")) {
            return false;
        }

        long now = System.currentTimeMillis() / 1000;
        
        // Если access token истекает в течение 5 минут, обновляем
        if (acc.getAccessExpiresAtSec() > 0 && acc.getAccessExpiresAtSec() - now < 300) {
            LogManager.getLogger().info("Access token истекает, обновляем...");
            boolean success = refreshTokens(acc);
            if (!success && showDialogOnFailure) {
                showAuthExpiredDialog();
            }
            return success;
        }

        // Если access token уже есть и не истек, проверяем валидность
        if (acc.getAccessToken() != null && acc.getAccessExpiresAtSec() > now) {
            return true;
        }

        // Токена нет или истек - пробуем обновить
        boolean success = refreshTokens(acc);
        if (!success && showDialogOnFailure) {
            showAuthExpiredDialog();
        }
        return success;
    }

    /**
     * Проверяет и обновляет токены без показа диалога
     */
    public static boolean ensureValidTokens() {
        return ensureValidTokens(false);
    }

    /**
     * Обновляет access token используя refresh token.
     */
    private static boolean refreshTokens(Account acc) {
        try {
            Map<String, String> body = new HashMap<>();
            body.put("refreshToken", acc.getRefreshToken());

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BACKEND_URL + "/api/auth/refresh"))
                    .header("Content-Type", "application/json")
                    .header("Origin", ArchiTechLauncher.FRONTEND_URL) // backend CSRF check requires Origin
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body), StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(ArchiTechLauncher.HTTP_TIMEOUT))
                    .build();

            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int code = resp.statusCode();

            if (code == 200) {
                JsonNode json = MAPPER.readTree(resp.body());
                
                String newAccess = json.path("accessToken").asText(null);
                String newRefresh = json.path("refreshToken").asText(null);
                
                if (newAccess != null) {
                    acc.setAccessToken(newAccess);
                    
                    // Парсим JWT для определения времени истечения
                    long expiresAt = parseJwtExpiry(newAccess);
                    if (expiresAt > 0) {
                        acc.setAccessExpiresAtSec(expiresAt);
                    }
                }
                
                if (newRefresh != null) {
                    acc.setRefreshToken(newRefresh);
                }
                
                AccountManager.setCurrentAccount(acc);
                LogManager.getLogger().info("Токены успешно обновлены");
                return true;
            } else if (code == 401 || code == 403) {
                LogManager.getLogger().warning("Refresh token отклонён (" + code + "), требуется повторный вход");
                AccountManager.clear(); // Очищаем невалидный токен
                return false;
            } else {
                LogManager.getLogger().warning("Ошибка обновления токенов: " + code);
                return false;
            }
        } catch (Exception e) {
            LogManager.getLogger().severe("Ошибка при обновлении токенов: " + e.getMessage());
            return false;
        }
    }

    /**
     * Загружает актуальные данные профиля (username, avatar, skin).
     */
    public static void updateProfile() {
        Account acc = AccountManager.getCurrentAccount();
        if (acc == null || acc.getAccessToken() == null) {
            return;
        }

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BACKEND_URL + "/api/v1/profile"))
                    .header("Authorization", "Bearer " + acc.getAccessToken())
                    .GET()
                    .timeout(Duration.ofSeconds(ArchiTechLauncher.HTTP_TIMEOUT))
                    .build();

            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (resp.statusCode() == 200) {
                JsonNode profile = MAPPER.readTree(resp.body());
                
                if (profile.has("id")) {
                    acc.setUuid(profile.get("id").asText());
                }
                if (profile.has("username")) {
                    acc.setUsername(profile.get("username").asText());
                }
                if (profile.has("email")) {
                    acc.setEmail(profile.get("email").asText());
                }
                if (profile.has("avatarUrl")) {
                    acc.setAvatarUrl(profile.get("avatarUrl").asText());
                }
                if (profile.has("skinUrl")) {
                    acc.setSkinUrl(profile.get("skinUrl").asText());
                }
                
                AccountManager.setCurrentAccount(acc);
                LogManager.getLogger().info("Профиль успешно обновлен: " + acc.getUsername());
            } else if (resp.statusCode() == 401) {
                LogManager.getLogger().warning("Токен невалиден при загрузке профиля");
                // Пробуем обновить токены и повторить
                if (ensureValidTokens()) {
                    updateProfile(); // Рекурсивный вызов после обновления токенов
                }
            } else {
                LogManager.getLogger().warning("Ошибка загрузки профиля: " + resp.statusCode());
            }
        } catch (Exception e) {
            LogManager.getLogger().warning("Не удалось обновить профиль: " + e.getMessage());
        }
    }

    /**
     * Парсит JWT токен и извлекает время истечения (exp claim).
     * Возвращает Unix timestamp в секундах или 0 если не удалось распарсить.
     */
    private static long parseJwtExpiry(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) return 0;
            
            String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            JsonNode json = MAPPER.readTree(payload);
            
            if (json.has("exp")) {
                return json.get("exp").asLong(0);
            }
        } catch (Exception e) {
            LogManager.getLogger().warning("Не удалось распарсить JWT: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Показывает диалог о необходимости повторного входа
     */
    private static void showAuthExpiredDialog() {
        Platform.runLater(() -> {
            AuthExpiredPanel.showAuthExpiredDialog(() -> {
                // После успешного входа обновляем UI
                if (ArchiTechLauncher.UI != null) {
                    ArchiTechLauncher.UI.refreshAccountDisplay();
                }
            });
        });
    }
}
