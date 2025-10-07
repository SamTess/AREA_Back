package area.server.AREA_Back.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Service for encrypting and decrypting OAuth tokens using AES-256-GCM
 */
@Service
@Slf4j
public class TokenEncryptionService {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96 bits
    private static final int GCM_TAG_LENGTH = 16; // 128 bits
    private static final int KEY_LENGTH = 256; // bits
    private static final int BITS_PER_BYTE = 8;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom;
    private final MeterRegistry meterRegistry;

    private Counter encryptTokenCalls;
    private Counter decryptTokenCalls;
    private Counter encryptFailures;
    private Counter decryptFailures;

    public TokenEncryptionService(@Value("${app.encryption.key:}") String base64Key, MeterRegistry meterRegistry) {
        this.secureRandom = new SecureRandom();
        this.secretKey = initializeKey(base64Key);
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void initMetrics() {
        encryptTokenCalls = Counter.builder("encryption.token.encrypt.calls")
                .description("Total number of token encryption calls")
                .register(meterRegistry);

        decryptTokenCalls = Counter.builder("encryption.token.decrypt.calls")
                .description("Total number of token decryption calls")
                .register(meterRegistry);

        encryptFailures = Counter.builder("encryption.token.encrypt.failures")
                .description("Total number of token encryption failures")
                .register(meterRegistry);

        decryptFailures = Counter.builder("encryption.token.decrypt.failures")
                .description("Total number of token decryption failures")
                .register(meterRegistry);
    }

    private SecretKey initializeKey(String base64Key) {
        if (base64Key != null && !base64Key.trim().isEmpty()) {
            try {
                byte[] keyBytes = Base64.getDecoder().decode(base64Key);
                log.info("Using provided encryption key from configuration");
                return new SecretKeySpec(keyBytes, ALGORITHM);
            } catch (Exception e) {
                log.warn("Invalid encryption key in configuration, generating new one: { }", e.getMessage());
                return generateKey();
            }
        } else {
            SecretKey newKey = generateKey();
            log.warn("No encryption key provided, generated new key: { }",
                Base64.getEncoder().encodeToString(newKey.getEncoded()));
            log.warn("For production, set app.encryption.key in your configuration");
            return newKey;
        }
    }

    /**
     * Encrypt a token using AES-256-GCM
     * @param plainToken the token to encrypt
     * @return Base64 encoded encrypted token with IV prepended
     */
    public String encryptToken(String plainToken) {
        if (plainToken == null || plainToken.trim().isEmpty()) {
            throw new IllegalArgumentException("Token cannot be null or empty");
        }

        encryptTokenCalls.increment();
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * BITS_PER_BYTE, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
            byte[] plainBytes = plainToken.getBytes(StandardCharsets.UTF_8);
            byte[] encryptedBytes = cipher.doFinal(plainBytes);
            byte[] encryptedWithIv = new byte[GCM_IV_LENGTH + encryptedBytes.length];
            System.arraycopy(iv, 0, encryptedWithIv, 0, GCM_IV_LENGTH);
            System.arraycopy(encryptedBytes, 0, encryptedWithIv, GCM_IV_LENGTH, encryptedBytes.length);

            return Base64.getEncoder().encodeToString(encryptedWithIv);

        } catch (Exception e) {
            encryptFailures.increment();
            log.error("Failed to encrypt token: { }", e.getMessage());
            throw new RuntimeException("Token encryption failed", e);
        }
    }

    /**
     * Decrypt a token using AES-256-GCM
     * @param encryptedToken Base64 encoded encrypted token with IV prepended
     * @return decrypted plain token
     */
    public String decryptToken(String encryptedToken) {
        if (encryptedToken == null || encryptedToken.trim().isEmpty()) {
            throw new IllegalArgumentException("Encrypted token cannot be null or empty");
        }

        decryptTokenCalls.increment();
        try {
            byte[] encryptedWithIv = Base64.getDecoder().decode(encryptedToken);

            if (encryptedWithIv.length < GCM_IV_LENGTH) {
                throw new IllegalArgumentException("Invalid encrypted token format");
            }
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encryptedBytes = new byte[encryptedWithIv.length - GCM_IV_LENGTH];
            System.arraycopy(encryptedWithIv, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(encryptedWithIv, GCM_IV_LENGTH, encryptedBytes, 0, encryptedBytes.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * BITS_PER_BYTE, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);

        } catch (Exception e) {
            decryptFailures.increment();
            log.error("Failed to decrypt token: { }", e.getMessage());
            throw new RuntimeException("Token decryption failed", e);
        }
    }

    /**
     * Generate a new AES-256 key
     */
    private SecretKey generateKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
            keyGenerator.init(KEY_LENGTH);
            return keyGenerator.generateKey();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate encryption key", e);
        }
    }

    /**
     * Get the current key as Base64 string (for configuration)
     */
    public String getKeyAsBase64() {
        return Base64.getEncoder().encodeToString(secretKey.getEncoded());
    }

    /**
     * Generate a new AES-256 key and return it as Base64 string
     * Useful for generating keys for configuration
     */
    public static String generateNewKeyAsBase64() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            keyGenerator.init(KEY_LENGTH);
            SecretKey key = keyGenerator.generateKey();
            return Base64.getEncoder().encodeToString(key.getEncoded());
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate encryption key", e);
        }
    }
}