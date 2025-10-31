package area.server.AREA_Back.service.Auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service for creating and validating secure OAuth state parameters.
 * Implements CSRF protection with HMAC signatures, expiration, and nonce validation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OAuthStateService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final long STATE_EXPIRATION_MS = 300000;
    private static final String REDIS_NONCE_PREFIX = "oauth:nonce:";
    private static final Duration NONCE_TTL = Duration.ofMinutes(10);

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.oauth.state-secret:}")
    private String stateSecret;

    /**
     * Create a secure OAuth state parameter with HMAC signature.
     *
     * @param mobileRedirect Mobile redirect URL (optional)
     * @param origin Origin of the request (web/mobile)
     * @param mode OAuth mode (login/link)
     * @param provider OAuth provider name
     * @return Base64-encoded secure state parameter
     */
    public String createSecureState(String mobileRedirect, String origin, String mode, String provider) {
        try {
            String nonce = UUID.randomUUID().toString();
            long expiresAt = System.currentTimeMillis() + STATE_EXPIRATION_MS;

            Map<String, Object> stateData = new HashMap<>();
            stateData.put("nonce", nonce);
            stateData.put("expires", expiresAt);
            if (mobileRedirect != null && !mobileRedirect.isEmpty()) {
                stateData.put("mobile_redirect", mobileRedirect);
            }
            if (origin != null) {
                stateData.put("origin", origin);
            }
            if (mode != null) {
                stateData.put("mode", mode);
            }
            if (provider != null) {
                stateData.put("provider", provider);
            }

            String stateJson = objectMapper.writeValueAsString(stateData);
            String signature = computeHMAC(stateJson);
            String secureState = stateJson + "." + signature;

            return Base64.getEncoder().encodeToString(secureState.getBytes(StandardCharsets.UTF_8));

        } catch (Exception e) {
            log.error("Failed to create secure OAuth state", e);
            throw new RuntimeException("Failed to create OAuth state", e);
        }
    }

    /**
     * Validate and parse a secure OAuth state parameter.
     *
     * @param encodedState Base64-encoded state parameter
     * @return Map containing state data
     * @throws SecurityException if state is invalid, expired, or already used
     */
    public Map<String, String> validateAndParseState(String encodedState) {
        try {
            if (encodedState == null || encodedState.isEmpty()) {
                throw new SecurityException("OAuth state is missing");
            }

            byte[] decoded = Base64.getDecoder().decode(encodedState);
            String decodedState = new String(decoded, StandardCharsets.UTF_8);
            String[] parts = decodedState.split("\\.", 2);

            if (parts.length != 2) {
                throw new SecurityException("Invalid OAuth state format");
            }

            String stateJson = parts[0];
            String receivedSignature = parts[1];

            String expectedSignature = computeHMAC(stateJson);
            if (!expectedSignature.equals(receivedSignature)) {
                log.warn("OAuth state signature verification failed");
                throw new SecurityException("OAuth state signature invalid - possible CSRF attack");
            }

            JsonNode stateData = objectMapper.readTree(stateJson);

            if (!stateData.has("expires")) {
                throw new SecurityException("OAuth state missing expiration");
            }
            long expiresAt = stateData.get("expires").asLong();
            if (expiresAt < System.currentTimeMillis()) {
                log.warn("OAuth state expired (age: {} ms)", System.currentTimeMillis() - expiresAt);
                throw new SecurityException("OAuth state expired");
            }

            if (!stateData.has("nonce")) {
                throw new SecurityException("OAuth state missing nonce");
            }
            String nonce = stateData.get("nonce").asText();
            String nonceKey = REDIS_NONCE_PREFIX + nonce;

            Boolean wasSet = redisTemplate.opsForValue()
                .setIfAbsent(nonceKey, "used", NONCE_TTL);

            if (wasSet == null || !wasSet) {
                log.warn("OAuth state nonce already used: {}", nonce);
                throw new SecurityException("OAuth state already used - possible replay attack");
            }

            Map<String, String> result = new HashMap<>();
            if (stateData.has("mobile_redirect")) {
                result.put("mobile_redirect", stateData.get("mobile_redirect").asText());
            }
            if (stateData.has("origin")) {
                result.put("origin", stateData.get("origin").asText());
            }
            if (stateData.has("mode")) {
                result.put("mode", stateData.get("mode").asText());
            }
            if (stateData.has("provider")) {
                result.put("provider", stateData.get("provider").asText());
            }

            log.debug("OAuth state validated successfully for provider: {}", result.get("provider"));
            return result;

        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to validate OAuth state", e);
            throw new SecurityException("Invalid OAuth state format", e);
        }
    }

    /**
     * Compute HMAC-SHA256 signature for state data.
     */
    private String computeHMAC(String data) {
        try {
            String secret = getStateSecret();
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                HMAC_ALGORITHM
            );
            mac.init(secretKeySpec);

            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmacBytes);

        } catch (Exception e) {
            log.error("Failed to compute HMAC", e);
            throw new RuntimeException("HMAC computation failed", e);
        }
    }

    /**
     * Get OAuth state secret, with fallback generation if not configured.
     */
    private String getStateSecret() {
        if (stateSecret == null || stateSecret.trim().isEmpty()) {
            log.warn("OAuth state secret not configured, using fallback (not recommended for production)");
            return UUID.randomUUID().toString() + UUID.randomUUID().toString();
        }
        return stateSecret;
    }
}
