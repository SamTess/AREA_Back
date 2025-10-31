package area.server.AREA_Back.service.Webhook;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * Service for validating webhook signatures from different providers
 */
@Service
@Slf4j
public class WebhookSignatureValidator {

    private static final String GITHUB_SIGNATURE_PREFIX = "sha256=";
    private static final String SLACK_SIGNATURE_PREFIX = "v0=";
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final int ED25519_PUBLIC_KEY_LENGTH = 32;
    private static final int ED25519_SIGNATURE_LENGTH = 64;
    private static final int HEX_STEP = 2;
    private static final int RADIX_HEX = 16;

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

            if (!isValid) {
                log.warn("Slack signature mismatch");
            }

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
     * Validates Discord webhook signature
     * Discord uses Ed25519 signatures with timestamp + body
     *
     * @param payload The raw payload bytes
     * @param signature The signature from X-Signature-Ed25519 header
     * @param publicKey The webhook public key (hex format)
     * @param timestamp The timestamp from X-Signature-Timestamp header
     * @return true if signature is valid
     */
    public boolean validateDiscordSignature(final byte[] payload, final String signature,
                                          final String publicKey, final String timestamp) {
        if (publicKey == null || publicKey.trim().isEmpty()) {
            log.warn("No Discord public key configured for signature validation");
            return false;
        }

        if (signature == null || timestamp == null) {
            log.info("Discord ping verification - allowing null signature or timestamp");
            return true;
        }

        try {
            byte[] publicKeyBytes = hexToBytes(publicKey);
            if (publicKeyBytes.length != ED25519_PUBLIC_KEY_LENGTH) {
                log.warn("Invalid Discord public key length: expected {} bytes, got {}",
                    ED25519_PUBLIC_KEY_LENGTH, publicKeyBytes.length);
                return false;
            }

            byte[] signatureBytes = hexToBytes(signature);
            if (signatureBytes.length != ED25519_SIGNATURE_LENGTH) {
                log.warn("Invalid Discord signature length: expected {} bytes, got {}",
                    ED25519_SIGNATURE_LENGTH, signatureBytes.length);
                return false;
            }

            String message = timestamp + new String(payload, StandardCharsets.UTF_8);
            byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
            Ed25519PublicKeyParameters publicKeyParams = new Ed25519PublicKeyParameters(publicKeyBytes, 0);
            Ed25519Signer signer = new Ed25519Signer();
            signer.init(false, publicKeyParams);
            signer.update(messageBytes, 0, messageBytes.length);

            boolean isValid = signer.verifySignature(signatureBytes);

            if (!isValid) {
                log.warn("Discord signature verification failed - invalid signature");
            } else {
                log.info("Discord signature verification PASSED");
            }

            return isValid;

        } catch (Exception e) {
            log.error("Error validating Discord signature: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Validates webhook signature based on provider type
     *
     * @param provider The provider name (github, slack, etc.)
     * @param payload The raw payload bytes
     * @param signature The signature to validate
     * @param secret The secret key (or public key for Discord)
     * @param timestamp Optional timestamp for providers that require it
     * @return true if signature is valid
     */
    public boolean validateSignature(final String provider, final byte[] payload, final String signature,
                                   final String secret, final String timestamp) {
        if ("discord".equalsIgnoreCase(provider)) {
            return validateDiscordSignature(payload, signature, secret, timestamp);
        }

        if (secret == null || secret.trim().isEmpty()) {
            log.warn("No secret provided for signature validation for provider: {}", provider);
            return false;
        }

        switch (provider.toLowerCase()) {
            case "github":
                return validateGitHubSignature(payload, signature, secret);
            case "slack":
                return validateSlackSignature(payload, signature, secret, timestamp);
            case "google":
                return validateGoogleSignature(payload, signature, secret, timestamp);
            case "discord":
                return validateDiscordSignature(payload, signature, secret, timestamp);
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
     * Converts hexadecimal string to byte array
     */
    private byte[] hexToBytes(final String hex) {
        if (hex.length() % HEX_STEP != 0) {
            throw new IllegalArgumentException("Hex string must have even length");
        }
        byte[] bytes = new byte[hex.length() / HEX_STEP];
        for (int i = 0; i < hex.length(); i += HEX_STEP) {
            bytes[i / HEX_STEP] = (byte) Integer.parseInt(hex.substring(i, i + HEX_STEP), RADIX_HEX);
        }
        return bytes;
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