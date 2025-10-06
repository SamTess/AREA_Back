package area.server.AREA_Back.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Assertions;

/**
 * Tests pour le service de chiffrement des tokens
 */
class TokenEncryptionServiceTest {

    private TokenEncryptionService tokenEncryptionService;

    @BeforeEach
    void setUp() {
        // Initialiser avec une clé vide pour déclencher la génération automatique
        tokenEncryptionService = new TokenEncryptionService("");
    }

    @Test
    void testEncryptAndDecrypt() {
        String originalToken = "github_pat_11AAAA7ZQ0abc123def456ghi789jkl";
        
        // Chiffrer le token
        String encryptedToken = tokenEncryptionService.encryptToken(originalToken);
        Assertions.assertNotNull(encryptedToken);
        Assertions.assertNotEquals(originalToken, encryptedToken);
        
        // Déchiffrer le token
        String decryptedToken = tokenEncryptionService.decryptToken(encryptedToken);
        Assertions.assertEquals(originalToken, decryptedToken);
    }

    @Test
    void testEncryptionProducesUniqueResults() {
        String originalToken = "test_token_123";
        
        String encrypted1 = tokenEncryptionService.encryptToken(originalToken);
        String encrypted2 = tokenEncryptionService.encryptToken(originalToken);
        
        // Les résultats doivent être différents (IV aléatoire)
        Assertions.assertNotEquals(encrypted1, encrypted2);
        
        // Mais les deux doivent déchiffrer vers le même token
        Assertions.assertEquals(originalToken, tokenEncryptionService.decryptToken(encrypted1));
        Assertions.assertEquals(originalToken, tokenEncryptionService.decryptToken(encrypted2));
    }

    @Test
    void testEncryptNullOrEmptyToken() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            tokenEncryptionService.encryptToken(null);
        });
        
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            tokenEncryptionService.encryptToken("");
        });
        
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            tokenEncryptionService.encryptToken("   ");
        });
    }

    @Test
    void testDecryptInvalidToken() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            tokenEncryptionService.decryptToken(null);
        });
        
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            tokenEncryptionService.decryptToken("");
        });
        
        Assertions.assertThrows(RuntimeException.class, () -> {
            tokenEncryptionService.decryptToken("invalid_base64_string");
        });
    }

    @Test
    void testKeyGeneration() {
        String keyBase64 = TokenEncryptionService.generateNewKeyAsBase64();
        Assertions.assertNotNull(keyBase64);
        
        // Vérifier que la clé est bien en Base64 et fait 32 bytes (256 bits)
        byte[] keyBytes = java.util.Base64.getDecoder().decode(keyBase64);
        Assertions.assertEquals(32, keyBytes.length);
    }

    @Test
    void testWithProvidedKey() {
        String providedKey = TokenEncryptionService.generateNewKeyAsBase64();
        TokenEncryptionService serviceWithKey = new TokenEncryptionService(providedKey);
        
        String originalToken = "test_with_provided_key";
        String encrypted = serviceWithKey.encryptToken(originalToken);
        String decrypted = serviceWithKey.decryptToken(encrypted);
        
        Assertions.assertEquals(originalToken, decrypted);
    }
}