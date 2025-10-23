package area.server.AREA_Back.dto;

import area.server.AREA_Back.service.Auth.OAuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour OAuthProvider
 * Type: Tests Unitaires
 * Description: Teste le DTO OAuthProvider
 */
@DisplayName("OAuthProvider - Tests Unitaires")
class OAuthProviderTest {

    @Test
    @DisplayName("Doit créer une instance avec tous les champs")
    void shouldCreateInstanceWithAllFields() {
        // Given
        String providerKey = "google";
        String providerLabel = "Google";
        String providerLogoUrl = "https://example.com/google-logo.png";
        String userAuthUrl = "https://accounts.google.com/o/oauth2/auth";
        String clientId = "client-id-123";

        // When
        OAuthProvider provider = new OAuthProvider(
            providerKey,
            providerLabel,
            providerLogoUrl,
            userAuthUrl,
            clientId
        );

        // Then
        assertNotNull(provider);
        assertEquals(providerKey, provider.getProviderKey());
        assertEquals(providerLabel, provider.getProviderLabel());
        assertEquals(providerLogoUrl, provider.getProviderLogoUrl());
        assertEquals(userAuthUrl, provider.getUserAuthUrl());
        assertEquals(clientId, provider.getClientId());
    }

    @Test
    @DisplayName("Doit créer une instance depuis OAuthService")
    void shouldCreateInstanceFromOAuthService() {
        // Given - Create a concrete implementation of OAuthService for testing
        OAuthService service = new OAuthService(
            "github",
            "GitHub",
            "https://example.com/github-logo.png",
            "https://github.com/login/oauth/authorize",
            "client-id-456",
            "client-secret",
            null,
            null
        ) {
            @Override
            public area.server.AREA_Back.dto.AuthResponse authenticate(
                area.server.AREA_Back.dto.OAuthLoginRequest request,
                jakarta.servlet.http.HttpServletResponse response) {
                return null;
            }
        };

        // When
        OAuthProvider provider = OAuthProvider.fromService(service);

        // Then
        assertNotNull(provider);
        assertEquals("github", provider.getProviderKey());
        assertEquals("GitHub", provider.getProviderLabel());
        assertEquals("https://example.com/github-logo.png", provider.getProviderLogoUrl());
        assertEquals("https://github.com/login/oauth/authorize", provider.getUserAuthUrl());
        assertEquals("client-id-456", provider.getClientId());
    }

    @Test
    @DisplayName("Doit retourner null quand OAuthService est null")
    void shouldReturnNullWhenServiceIsNull() {
        // When
        OAuthProvider provider = OAuthProvider.fromService(null);

        // Then
        assertNull(provider);
    }

    @Test
    @DisplayName("Doit être égal à lui-même")
    void shouldBeEqualToItself() {
        // Given
        OAuthProvider provider = new OAuthProvider(
            "google",
            "Google",
            "https://example.com/logo.png",
            "https://accounts.google.com/auth",
            "client-id"
        );

        // When & Then
        assertEquals(provider, provider);
    }

    @Test
    @DisplayName("Doit être égal à une autre instance avec les mêmes valeurs")
    void shouldBeEqualToAnotherInstanceWithSameValues() {
        // Given
        OAuthProvider provider1 = new OAuthProvider(
            "google",
            "Google",
            "https://example.com/logo.png",
            "https://accounts.google.com/auth",
            "client-id"
        );

        OAuthProvider provider2 = new OAuthProvider(
            "google",
            "Google",
            "https://example.com/logo.png",
            "https://accounts.google.com/auth",
            "client-id"
        );

        // When & Then
        assertEquals(provider1, provider2);
        assertEquals(provider2, provider1);
    }

    @Test
    @DisplayName("Ne doit pas être égal à null")
    void shouldNotBeEqualToNull() {
        // Given
        OAuthProvider provider = new OAuthProvider(
            "google",
            "Google",
            "https://example.com/logo.png",
            "https://accounts.google.com/auth",
            "client-id"
        );

        // When & Then
        assertNotEquals(null, provider);
    }

    @Test
    @DisplayName("Ne doit pas être égal à un objet d'une autre classe")
    void shouldNotBeEqualToObjectOfDifferentClass() {
        // Given
        OAuthProvider provider = new OAuthProvider(
            "google",
            "Google",
            "https://example.com/logo.png",
            "https://accounts.google.com/auth",
            "client-id"
        );

        // When & Then
        assertNotEquals(provider, "not an OAuthProvider");
    }

    @Test
    @DisplayName("Ne doit pas être égal quand providerKey diffère")
    void shouldNotBeEqualWhenProviderKeyDiffers() {
        // Given
        OAuthProvider provider1 = new OAuthProvider(
            "google",
            "Google",
            "https://example.com/logo.png",
            "https://accounts.google.com/auth",
            "client-id"
        );

        OAuthProvider provider2 = new OAuthProvider(
            "github",
            "Google",
            "https://example.com/logo.png",
            "https://accounts.google.com/auth",
            "client-id"
        );

        // When & Then
        assertNotEquals(provider1, provider2);
    }

    @Test
    @DisplayName("Ne doit pas être égal quand providerLabel diffère")
    void shouldNotBeEqualWhenProviderLabelDiffers() {
        // Given
        OAuthProvider provider1 = new OAuthProvider(
            "google",
            "Google",
            "https://example.com/logo.png",
            "https://accounts.google.com/auth",
            "client-id"
        );

        OAuthProvider provider2 = new OAuthProvider(
            "google",
            "GitHub",
            "https://example.com/logo.png",
            "https://accounts.google.com/auth",
            "client-id"
        );

        // When & Then
        assertNotEquals(provider1, provider2);
    }

    @Test
    @DisplayName("Ne doit pas être égal quand providerLogoUrl diffère")
    void shouldNotBeEqualWhenProviderLogoUrlDiffers() {
        // Given
        OAuthProvider provider1 = new OAuthProvider(
            "google",
            "Google",
            "https://example.com/logo1.png",
            "https://accounts.google.com/auth",
            "client-id"
        );

        OAuthProvider provider2 = new OAuthProvider(
            "google",
            "Google",
            "https://example.com/logo2.png",
            "https://accounts.google.com/auth",
            "client-id"
        );

        // When & Then
        assertNotEquals(provider1, provider2);
    }

    @Test
    @DisplayName("Ne doit pas être égal quand userAuthUrl diffère")
    void shouldNotBeEqualWhenUserAuthUrlDiffers() {
        // Given
        OAuthProvider provider1 = new OAuthProvider(
            "google",
            "Google",
            "https://example.com/logo.png",
            "https://accounts.google.com/auth1",
            "client-id"
        );

        OAuthProvider provider2 = new OAuthProvider(
            "google",
            "Google",
            "https://example.com/logo.png",
            "https://accounts.google.com/auth2",
            "client-id"
        );

        // When & Then
        assertNotEquals(provider1, provider2);
    }

    @Test
    @DisplayName("Ne doit pas être égal quand clientId diffère")
    void shouldNotBeEqualWhenClientIdDiffers() {
        // Given
        OAuthProvider provider1 = new OAuthProvider(
            "google",
            "Google",
            "https://example.com/logo.png",
            "https://accounts.google.com/auth",
            "client-id-1"
        );

        OAuthProvider provider2 = new OAuthProvider(
            "google",
            "Google",
            "https://example.com/logo.png",
            "https://accounts.google.com/auth",
            "client-id-2"
        );

        // When & Then
        assertNotEquals(provider1, provider2);
    }

    @Test
    @DisplayName("Doit avoir le même hashCode pour les instances égales")
    void shouldHaveSameHashCodeForEqualInstances() {
        // Given
        OAuthProvider provider1 = new OAuthProvider(
            "google",
            "Google",
            "https://example.com/logo.png",
            "https://accounts.google.com/auth",
            "client-id"
        );

        OAuthProvider provider2 = new OAuthProvider(
            "google",
            "Google",
            "https://example.com/logo.png",
            "https://accounts.google.com/auth",
            "client-id"
        );

        // When & Then
        assertEquals(provider1.hashCode(), provider2.hashCode());
    }

    @Test
    @DisplayName("Doit avoir des hashCodes différents pour les instances différentes")
    void shouldHaveDifferentHashCodesForDifferentInstances() {
        // Given
        OAuthProvider provider1 = new OAuthProvider(
            "google",
            "Google",
            "https://example.com/logo.png",
            "https://accounts.google.com/auth",
            "client-id-1"
        );

        OAuthProvider provider2 = new OAuthProvider(
            "github",
            "GitHub",
            "https://example.com/logo2.png",
            "https://github.com/login/oauth/authorize",
            "client-id-2"
        );

        // When & Then
        assertNotEquals(provider1.hashCode(), provider2.hashCode());
    }

    @Test
    @DisplayName("Doit gérer les valeurs null dans le constructeur")
    void shouldHandleNullValuesInConstructor() {
        // When
        OAuthProvider provider = new OAuthProvider(
            null,
            null,
            null,
            null,
            null
        );

        // Then
        assertNotNull(provider);
        assertNull(provider.getProviderKey());
        assertNull(provider.getProviderLabel());
        assertNull(provider.getProviderLogoUrl());
        assertNull(provider.getUserAuthUrl());
        assertNull(provider.getClientId());
    }

    @Test
    @DisplayName("Doit gérer les égalités avec des valeurs null")
    void shouldHandleEqualsWithNullValues() {
        // Given
        OAuthProvider provider1 = new OAuthProvider(null, null, null, null, null);
        OAuthProvider provider2 = new OAuthProvider(null, null, null, null, null);

        // When & Then
        assertEquals(provider1, provider2);
        assertEquals(provider1.hashCode(), provider2.hashCode());
    }
}
