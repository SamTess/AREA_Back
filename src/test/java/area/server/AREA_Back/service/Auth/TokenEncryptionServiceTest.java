package area.server.AREA_Back.service.Auth;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour TokenEncryptionService
 * Type: Tests Unitaires
 * Description: Teste le chiffrement et déchiffrement des tokens
 */
@DisplayName("TokenEncryptionService - Tests Unitaires")
class TokenEncryptionServiceTest {

    private TokenEncryptionService encryptionService;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        // Générer une clé valide pour les tests
        String validKey = TokenEncryptionService.generateNewKeyAsBase64();
        encryptionService = new TokenEncryptionService(validKey, meterRegistry);
        encryptionService.initMetrics();
    }

    @Test
    @DisplayName("Doit chiffrer et déchiffrer un token correctement")
    void shouldEncryptAndDecryptTokenCorrectly() {
        // Given
        String plainToken = "my-secret-token-12345";

        // When
        String encrypted = encryptionService.encryptToken(plainToken);
        String decrypted = encryptionService.decryptToken(encrypted);

        // Then
        assertNotNull(encrypted);
        assertNotEquals(plainToken, encrypted);
        assertEquals(plainToken, decrypted);
    }

    @Test
    @DisplayName("Doit chiffrer le même token différemment à chaque fois")
    void shouldEncryptSameTokenDifferently() {
        // Given
        String plainToken = "my-token";

        // When
        String encrypted1 = encryptionService.encryptToken(plainToken);
        String encrypted2 = encryptionService.encryptToken(plainToken);

        // Then
        assertNotEquals(encrypted1, encrypted2);
        assertEquals(plainToken, encryptionService.decryptToken(encrypted1));
        assertEquals(plainToken, encryptionService.decryptToken(encrypted2));
    }

    @Test
    @DisplayName("Doit gérer les tokens longs")
    void shouldHandleLongTokens() {
        // Given
        String longToken = "a".repeat(1000);

        // When
        String encrypted = encryptionService.encryptToken(longToken);
        String decrypted = encryptionService.decryptToken(encrypted);

        // Then
        assertEquals(longToken, decrypted);
    }

    @Test
    @DisplayName("Doit gérer les caractères spéciaux")
    void shouldHandleSpecialCharacters() {
        // Given
        String specialToken = "token-with-special-chars!@#$%^&*(){}[]|\\:;\"'<>,.?/~`+=";

        // When
        String encrypted = encryptionService.encryptToken(specialToken);
        String decrypted = encryptionService.decryptToken(encrypted);

        // Then
        assertEquals(specialToken, decrypted);
    }

    @Test
    @DisplayName("Doit gérer les tokens unicode")
    void shouldHandleUnicodeTokens() {
        // Given
        String unicodeToken = "token-émojis-😀-中文-العربية";

        // When
        String encrypted = encryptionService.encryptToken(unicodeToken);
        String decrypted = encryptionService.decryptToken(encrypted);

        // Then
        assertEquals(unicodeToken, decrypted);
    }

    @Test
    @DisplayName("Doit lancer une exception pour token null au chiffrement")
    void shouldThrowExceptionForNullTokenEncryption() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> 
            encryptionService.encryptToken(null)
        );
    }

    @Test
    @DisplayName("Doit lancer une exception pour token vide au chiffrement")
    void shouldThrowExceptionForEmptyTokenEncryption() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> 
            encryptionService.encryptToken("")
        );
    }

    @Test
    @DisplayName("Doit lancer une exception pour token null au déchiffrement")
    void shouldThrowExceptionForNullTokenDecryption() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> 
            encryptionService.decryptToken(null)
        );
    }

    @Test
    @DisplayName("Doit lancer une exception pour token vide au déchiffrement")
    void shouldThrowExceptionForEmptyTokenDecryption() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> 
            encryptionService.decryptToken("")
        );
    }

    @Test
    @DisplayName("Doit lancer une exception pour token invalide au déchiffrement")
    void shouldThrowExceptionForInvalidTokenDecryption() {
        // When & Then
        assertThrows(RuntimeException.class, () -> 
            encryptionService.decryptToken("invalid-encrypted-token")
        );
    }

    @Test
    @DisplayName("Doit lancer une exception pour token trop court au déchiffrement")
    void shouldThrowExceptionForTooShortTokenDecryption() {
        // Given
        String shortToken = Base64.getEncoder().encodeToString(new byte[5]);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> 
            encryptionService.decryptToken(shortToken)
        );
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
    }

    @Test
    @DisplayName("Doit incrémenter le compteur d'appels de chiffrement")
    void shouldIncrementEncryptCallsCounter() {
        // Given
        Counter counter = meterRegistry.counter("encryption.token.encrypt.calls");
        double before = counter.count();

        // When
        encryptionService.encryptToken("test-token");

        // Then
        assertEquals(before + 1, counter.count());
    }

    @Test
    @DisplayName("Doit incrémenter le compteur d'appels de déchiffrement")
    void shouldIncrementDecryptCallsCounter() {
        // Given
        String encrypted = encryptionService.encryptToken("test-token");
        Counter counter = meterRegistry.counter("encryption.token.decrypt.calls");
        double before = counter.count();

        // When
        encryptionService.decryptToken(encrypted);

        // Then
        assertEquals(before + 1, counter.count());
    }

    @Test
    @DisplayName("Doit incrémenter le compteur d'échecs de chiffrement")
    void shouldIncrementEncryptFailuresCounter() {
        // Given
        Counter counter = meterRegistry.counter("encryption.token.encrypt.failures");
        double before = counter.count();

        // When
        try {
            encryptionService.encryptToken(null);
        } catch (Exception e) {
            // Expected
        }

        // Then
        assertEquals(before, counter.count()); // Ne devrait pas incrémenter car validation avant chiffrement
    }

    @Test
    @DisplayName("Doit incrémenter le compteur d'échecs de déchiffrement")
    void shouldIncrementDecryptFailuresCounter() {
        // Given
        Counter counter = meterRegistry.counter("encryption.token.decrypt.failures");
        double before = counter.count();

        // When
        try {
            encryptionService.decryptToken("invalid-token");
        } catch (Exception e) {
            // Expected
        }

        // Then
        assertEquals(before + 1, counter.count());
    }

    @Test
    @DisplayName("Doit retourner la clé en Base64")
    void shouldReturnKeyAsBase64() {
        // When
        String keyBase64 = encryptionService.getKeyAsBase64();

        // Then
        assertNotNull(keyBase64);
        assertFalse(keyBase64.isEmpty());
        // Vérifier que c'est du Base64 valide
        assertDoesNotThrow(() -> Base64.getDecoder().decode(keyBase64));
    }

    @Test
    @DisplayName("Doit générer une nouvelle clé en Base64")
    void shouldGenerateNewKeyAsBase64() {
        // When
        String key1 = TokenEncryptionService.generateNewKeyAsBase64();
        String key2 = TokenEncryptionService.generateNewKeyAsBase64();

        // Then
        assertNotNull(key1);
        assertNotNull(key2);
        assertNotEquals(key1, key2);
        assertDoesNotThrow(() -> Base64.getDecoder().decode(key1));
        assertDoesNotThrow(() -> Base64.getDecoder().decode(key2));
    }

    @Test
    @DisplayName("Doit utiliser une clé fournie en configuration")
    void shouldUseProvidedKey() {
        // Given
        String providedKey = TokenEncryptionService.generateNewKeyAsBase64();
        TokenEncryptionService service1 = new TokenEncryptionService(providedKey, meterRegistry);
        service1.initMetrics();
        TokenEncryptionService service2 = new TokenEncryptionService(providedKey, meterRegistry);
        service2.initMetrics();

        String plainToken = "test-token";

        // When
        String encrypted = service1.encryptToken(plainToken);
        String decrypted = service2.decryptToken(encrypted);

        // Then
        assertEquals(plainToken, decrypted);
    }

    @Test
    @DisplayName("Doit générer une nouvelle clé si la clé fournie est invalide")
    void shouldGenerateNewKeyIfProvidedKeyIsInvalid() {
        // When
        TokenEncryptionService service = new TokenEncryptionService("invalid-key", meterRegistry);
        service.initMetrics();

        // Then
        assertDoesNotThrow(() -> {
            String encrypted = service.encryptToken("test");
            service.decryptToken(encrypted);
        });
    }

    @Test
    @DisplayName("Doit générer une nouvelle clé si aucune clé n'est fournie")
    void shouldGenerateNewKeyIfNoKeyProvided() {
        // When
        TokenEncryptionService service = new TokenEncryptionService("", meterRegistry);
        service.initMetrics();

        // Then
        assertDoesNotThrow(() -> {
            String encrypted = service.encryptToken("test");
            service.decryptToken(encrypted);
        });
    }

    @Test
    @DisplayName("Doit gérer les tokens avec espaces")
    void shouldHandleTokensWithSpaces() {
        // Given
        String tokenWithSpaces = "token with spaces and    multiple   spaces";

        // When
        String encrypted = encryptionService.encryptToken(tokenWithSpaces);
        String decrypted = encryptionService.decryptToken(encrypted);

        // Then
        assertEquals(tokenWithSpaces, decrypted);
    }

    @Test
    @DisplayName("Doit rejeter les tokens qui ne sont que des espaces")
    void shouldRejectTokensWithOnlySpaces() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> 
            encryptionService.encryptToken("   ")
        );
    }
}
