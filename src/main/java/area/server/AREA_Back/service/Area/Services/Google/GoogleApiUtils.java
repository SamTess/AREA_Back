package area.server.AREA_Back.service.Area.Services.Google;

import area.server.AREA_Back.repository.UserOAuthIdentityRepository;
import area.server.AREA_Back.repository.UserRepository;
import area.server.AREA_Back.service.Auth.ServiceAccountService;
import area.server.AREA_Back.service.Auth.TokenEncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Utility class for Google API operations.
 * Provides common methods for authentication, parameter handling, and JSON conversion.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GoogleApiUtils {

    public static final String GOOGLE_API_BASE = "https://www.googleapis.com";
    public static final String GMAIL_API = GOOGLE_API_BASE + "/gmail/v1";
    public static final String CALENDAR_API = GOOGLE_API_BASE + "/calendar/v3";
    public static final String DRIVE_API = GOOGLE_API_BASE + "/drive/v3";
    public static final String SHEETS_API = GOOGLE_API_BASE + "/sheets/v4";
    public static final String GOOGLE_PROVIDER_KEY = "google";

    private final UserOAuthIdentityRepository userOAuthIdentityRepository;
    private final UserRepository userRepository;
    private final TokenEncryptionService tokenEncryptionService;
    private final ServiceAccountService serviceAccountService;

    /**
     * Retrieve the Google OAuth token for a given user.
     *
     * @param userId The user's UUID
     * @return The decrypted Google access token, or null if not found
     */
    public String getGoogleToken(UUID userId) {
        Optional<String> serviceToken = serviceAccountService.getAccessToken(userId, GOOGLE_PROVIDER_KEY);
        if (serviceToken.isPresent()) {
            log.debug("Google token found in service accounts for user: {}", userId);
            return serviceToken.get();
        }

        Optional<area.server.AREA_Back.entity.User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            log.warn("User not found: {}", userId);
            return null;
        }

        Optional<area.server.AREA_Back.entity.UserOAuthIdentity> oauthOpt =
            userOAuthIdentityRepository.findByUserAndProvider(userOpt.get(), GOOGLE_PROVIDER_KEY);

        if (oauthOpt.isEmpty()) {
            log.warn("No Google OAuth identity found for user: {}", userId);
            return null;
        }

        area.server.AREA_Back.entity.UserOAuthIdentity oauth = oauthOpt.get();
        String encryptedToken = oauth.getAccessTokenEnc();

        if (encryptedToken == null || encryptedToken.trim().isEmpty()) {
            log.warn("Google token is null or empty for user: {}", userId);
            return null;
        }

        try {
            String decryptedToken = tokenEncryptionService.decryptToken(encryptedToken);
            log.debug("Google token successfully decrypted for user: {}", userId);
            return decryptedToken;
        } catch (Exception e) {
            log.error("Error decrypting Google token for user {}: {}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * Create HTTP headers for Google API requests.
     *
     * @param token The OAuth access token
     * @return Configured HTTP headers
     */
    public HttpHeaders createGoogleHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    /**
     * Get a required parameter from the params map.
     *
     * @param params The parameters map
     * @param key The parameter key
     * @param type The expected type
     * @return The parameter value
     * @throws IllegalArgumentException if the parameter is missing
     */
    @SuppressWarnings("unchecked")
    public <T> T getRequiredParam(Map<String, Object> params, String key, Class<T> type) {
        Object value = params.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Required parameter missing: " + key);
        }
        return (T) value;
    }

    /**
     * Get an optional parameter from the params map with a default value.
     *
     * @param params The parameters map
     * @param key The parameter key
     * @param type The expected type
     * @param defaultValue The default value if parameter is missing
     * @return The parameter value or default value
     */
    @SuppressWarnings("unchecked")
    public <T> T getOptionalParam(
            Map<String, Object> params,
            String key,
            Class<T> type,
            T defaultValue) {
        Object value = params.get(key);
        return value != null ? (T) value : defaultValue;
    }

    /**
     * Convert a Map to JSON string format.
     *
     * @param map The map to convert
     * @return JSON string representation
     */
    public String mapToJson(Map<String, Object> map) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                json.append(",");
            }
            first = false;

            json.append("\"").append(entry.getKey()).append("\":");
            json.append(valueToJson(entry.getValue()));
        }

        json.append("}");
        return json.toString();
    }

    /**
     * Convert a List to JSON array format.
     *
     * @param list The list to convert
     * @return JSON array string
     */
    public String listToJson(List<?> list) {
        StringBuilder json = new StringBuilder("[");
        boolean first = true;

        for (Object item : list) {
            if (!first) {
                json.append(",");
            }
            first = false;
            json.append(valueToJson(item));
        }

        json.append("]");
        return json.toString();
    }

    /**
     * Convert any value to its JSON representation.
     *
     * @param value The value to convert
     * @return JSON string representation of the value
     */
    @SuppressWarnings("unchecked")
    private String valueToJson(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            return "\"" + escapeJson((String) value) + "\"";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof List) {
            return listToJson((List<?>) value);
        }
        if (value instanceof Map) {
            return mapToJson((Map<String, Object>) value);
        }
        return "\"" + escapeJson(value.toString()) + "\"";
    }

    /**
     * Escape special characters in JSON strings.
     *
     * @param str The string to escape
     * @return Escaped string
     */
    public String escapeJson(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
