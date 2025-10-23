package area.server.AREA_Back.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour JwtCookieProperties
 * Type: Tests Unitaires
 * Description: Teste la configuration des propriétés des cookies JWT
 */
@DisplayName("JwtCookieProperties - Tests Unitaires")
class JwtCookiePropertiesTest {

    private JwtCookieProperties properties;

    @BeforeEach
    void setUp() {
        properties = new JwtCookieProperties();
    }

    @Test
    @DisplayName("Doit avoir des valeurs par défaut correctes")
    void shouldHaveCorrectDefaultValues() {
        // When & Then
        assertFalse(properties.isSecure());
        assertEquals("None", properties.getSameSite());
        assertNull(properties.getDomain());
        assertEquals(86400, properties.getAccessTokenExpiry());
        assertEquals(604800, properties.getRefreshTokenExpiry());
    }

    @Test
    @DisplayName("Doit permettre de définir le flag secure")
    void shouldAllowSettingSecureFlag() {
        // When
        properties.setSecure(true);

        // Then
        assertTrue(properties.isSecure());
    }

    @Test
    @DisplayName("Doit permettre de définir le sameSite")
    void shouldAllowSettingSameSite() {
        // When
        properties.setSameSite("Strict");

        // Then
        assertEquals("Strict", properties.getSameSite());
    }

    @Test
    @DisplayName("Doit permettre de définir le domaine")
    void shouldAllowSettingDomain() {
        // When
        properties.setDomain("example.com");

        // Then
        assertEquals("example.com", properties.getDomain());
    }

    @Test
    @DisplayName("Doit permettre de définir l'expiration du access token")
    void shouldAllowSettingAccessTokenExpiry() {
        // When
        properties.setAccessTokenExpiry(3600);

        // Then
        assertEquals(3600, properties.getAccessTokenExpiry());
    }

    @Test
    @DisplayName("Doit permettre de définir l'expiration du refresh token")
    void shouldAllowSettingRefreshTokenExpiry() {
        // When
        properties.setRefreshTokenExpiry(1209600);

        // Then
        assertEquals(1209600, properties.getRefreshTokenExpiry());
    }

    @Test
    @DisplayName("Doit permettre de définir toutes les propriétés")
    void shouldAllowSettingAllProperties() {
        // When
        properties.setSecure(true);
        properties.setSameSite("Lax");
        properties.setDomain("test.com");
        properties.setAccessTokenExpiry(7200);
        properties.setRefreshTokenExpiry(2419200);

        // Then
        assertTrue(properties.isSecure());
        assertEquals("Lax", properties.getSameSite());
        assertEquals("test.com", properties.getDomain());
        assertEquals(7200, properties.getAccessTokenExpiry());
        assertEquals(2419200, properties.getRefreshTokenExpiry());
    }

    @Test
    @DisplayName("Doit accepter des valeurs null pour le domaine")
    void shouldAcceptNullForDomain() {
        // When
        properties.setDomain(null);

        // Then
        assertNull(properties.getDomain());
    }

    @Test
    @DisplayName("Doit accepter des valeurs null pour le sameSite")
    void shouldAcceptNullForSameSite() {
        // When
        properties.setSameSite(null);

        // Then
        assertNull(properties.getSameSite());
    }

    @Test
    @DisplayName("Doit permettre de définir des valeurs d'expiration personnalisées")
    void shouldAllowCustomExpiryValues() {
        // Given
        int customAccessExpiry = 1800;  // 30 minutes
        int customRefreshExpiry = 86400;  // 1 day

        // When
        properties.setAccessTokenExpiry(customAccessExpiry);
        properties.setRefreshTokenExpiry(customRefreshExpiry);

        // Then
        assertEquals(customAccessExpiry, properties.getAccessTokenExpiry());
        assertEquals(customRefreshExpiry, properties.getRefreshTokenExpiry());
    }

    @Test
    @DisplayName("Doit supporter différentes valeurs de SameSite")
    void shouldSupportDifferentSameSiteValues() {
        // Test different SameSite values
        String[] sameSiteValues = {"None", "Lax", "Strict"};

        for (String value : sameSiteValues) {
            properties.setSameSite(value);
            assertEquals(value, properties.getSameSite());
        }
    }

    @Test
    @DisplayName("Doit permettre de réinitialiser les valeurs")
    void shouldAllowResettingValues() {
        // Given
        properties.setSecure(true);
        properties.setSameSite("Strict");
        properties.setDomain("example.com");
        properties.setAccessTokenExpiry(1800);
        properties.setRefreshTokenExpiry(86400);

        // When - Reset to defaults
        properties.setSecure(false);
        properties.setSameSite("None");
        properties.setDomain(null);
        properties.setAccessTokenExpiry(86400);
        properties.setRefreshTokenExpiry(604800);

        // Then
        assertFalse(properties.isSecure());
        assertEquals("None", properties.getSameSite());
        assertNull(properties.getDomain());
        assertEquals(86400, properties.getAccessTokenExpiry());
        assertEquals(604800, properties.getRefreshTokenExpiry());
    }
}
