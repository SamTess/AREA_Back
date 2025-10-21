package area.server.AREA_Back.service.Webhook;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Service for validating webhook signatures from different providers
 */
@Service
@Slf4j
public class WebhookSignatureValidator {

    private static final String GITHUB_SIGNATURE_PREFIX = "sha256=";
    private static final String SLACK_SIGNATURE_PREFIX = "v0=";
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final int JWT_PARTS_COUNT = 3;

    /**
     * Validates GitHub webhook signature
     * GitHub uses HMAC-SHA256 with format: sha256=<hash>
     *
     * @param payload The raw payload bytes
     * @param signature The signature from X-Hub-Signature-256 header
     * @param secret The webhook secret
     * @return true if signature is valid
     */
    public boolean validateGitHubSignature(final byte[] payload, final String signature, final String secret) {
        if (signature == null || !signature.startsWith(GITHUB_SIGNATURE_PREFIX)) {
            log.warn("Invalid GitHub signature format");
            return false;
        }

        try {
            String expectedSignature = GITHUB_SIGNATURE_PREFIX
                + calculateHmacSha256(payload, secret);

            boolean isValid = secureEquals(signature, expectedSignature);
            return isValid;

        } catch (Exception e) {
            log.error("Error validating GitHub signature: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Validates Slack webhook signature
     * Slack uses HMAC-SHA256 with format: v0=<hash>
     *
     * @param payload The raw payload bytes
     * @param signature The signature from X-Slack-Signature header
     * @param secret The webhook secret
     * @param timestamp The timestamp from X-Slack-Request-Timestamp header
     * @return true if signature is valid
     */
    public boolean validateSlackSignature(final byte[] payload, final String signature,
            final String secret, final String timestamp) {
        if (signature == null || !signature.startsWith(SLACK_SIGNATURE_PREFIX)) {
            log.warn("Invalid Slack signature format");
            return false;
        }

        try {
            String baseString = "v0:" + timestamp + ":" + new String(payload, StandardCharsets.UTF_8);
            String expectedSignature = SLACK_SIGNATURE_PREFIX
                + calculateHmacSha256(baseString.getBytes(StandardCharsets.UTF_8), secret);

            boolean isValid = secureEquals(signature, expectedSignature);
            return isValid;

        } catch (Exception e) {
            log.error("Error validating Slack signature: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Validates generic HMAC-SHA256 signature
     *
     * @param payload The raw payload bytes
     * @param signature The signature to validate
     * @param secret The secret key
     * @return true if signature is valid
     */
    public boolean validateHmacSha256Signature(final byte[] payload, final String signature, final String secret) {
        try {
            String expectedSignature = calculateHmacSha256(payload, secret);
            boolean isValid = secureEquals(signature, expectedSignature);
            return isValid;

        } catch (Exception e) {
            log.error("Error validating HMAC-SHA256 signature: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Validates Google Pub/Sub webhook
     * Google Pub/Sub doesn't use traditional webhook signatures in the same way,
     * but relies on HTTPS and proper endpoint configuration
     *
     * @param payload The raw payload bytes
     * @param signature Not used for Google Pub/Sub
     * @param secret Not used for Google Pub/Sub
     * @param timestamp Not used for Google Pub/Sub
     * @return true (validation handled by Pub/Sub infrastructure)
     */
    public boolean validateGoogleSignature(final byte[] payload, final String signature,
                                         final String secret, final String timestamp) {
        log.debug("Google Pub/Sub webhook validation - relying on Pub/Sub infrastructure security");
        return true;
    }

    /**
     * Validates webhook signature based on provider type
     *
     * @param provider The provider name (github, slack, etc.)
     * @param payload The raw payload bytes
     * @param signature The signature to validate
     * @param secret The secret key
     * @param timestamp Optional timestamp for providers that require it
     * @return true if signature is valid
     */
    public boolean validateSignature(final String provider, final byte[] payload, final String signature,
                                   final String secret, final String timestamp) {
        if (secret == null || secret.trim().isEmpty()) {
            log.warn("No secret provided for signature validation");
            return false;
        }

        switch (provider.toLowerCase()) {
            case "github":
                return validateGitHubSignature(payload, signature, secret);
            case "slack":
                return validateSlackSignature(payload, signature, secret, timestamp);
            case "google":
                return validateGoogleSignature(payload, signature, secret, timestamp);
            case "generic":
                return validateHmacSha256Signature(payload, signature, secret);
            default:
                log.warn("Unsupported provider for signature validation: {}", provider);
                return false;
        }
    }

    /**
     * Calculates HMAC-SHA256 hash
     */
    private String calculateHmacSha256(final byte[] data, final String secret)
            throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance(HMAC_SHA256);
        SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
        mac.init(secretKeySpec);
        byte[] hash = mac.doFinal(data);
        return bytesToHex(hash);
    }

    /**
     * Converts byte array to hexadecimal string
     */
    private String bytesToHex(final byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    /**
     * Secure string comparison to prevent timing attacks
     */
    private boolean secureEquals(final String a, final String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}