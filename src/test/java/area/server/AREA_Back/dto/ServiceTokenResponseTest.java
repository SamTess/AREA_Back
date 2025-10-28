package area.server.AREA_Back.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ServiceTokenResponse - Tests Unitaires")
class ServiceTokenResponseTest {

    @Test
    @DisplayName("Doit créer un ServiceTokenResponse")
    void shouldCreateServiceTokenResponse() {
        // Given & When
        ServiceTokenResponse response = new ServiceTokenResponse();
        response.setId("token-123");
        response.setServiceKey("spotify");
        response.setServiceName("Spotify");

        // Then
        assertEquals("token-123", response.getId());
        assertEquals("spotify", response.getServiceKey());
        assertEquals("Spotify", response.getServiceName());
    }

    @Test
    @DisplayName("Doit gérer les tokens")
    void shouldHandleTokens() {
        // Given
        ServiceTokenResponse response = new ServiceTokenResponse();
        response.setHasAccessToken(true);
        response.setHasRefreshToken(true);

        // When & Then
        assertTrue(response.isHasAccessToken());
        assertTrue(response.isHasRefreshToken());
    }

    @Test
    @DisplayName("Doit gérer l'expiration")
    void shouldHandleExpiration() {
        // Given
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);
        ServiceTokenResponse response = new ServiceTokenResponse();
        response.setExpiresAt(expiresAt);
        response.setExpired(false);

        // When & Then
        assertEquals(expiresAt, response.getExpiresAt());
        assertFalse(response.isExpired());
    }

    @Test
    @DisplayName("Doit gérer les scopes")
    void shouldHandleScopes() {
        // Given
        Map<String, Object> scopes = new HashMap<>();
        scopes.put("read", true);
        scopes.put("write", true);

        ServiceTokenResponse response = new ServiceTokenResponse();
        response.setScopes(scopes);

        // When & Then
        assertEquals(2, response.getScopes().size());
        assertTrue((Boolean) response.getScopes().get("read"));
    }

    @Test
    @DisplayName("Doit gérer remoteAccountId")
    void shouldHandleRemoteAccountId() {
        // Given
        ServiceTokenResponse response = new ServiceTokenResponse();
        response.setRemoteAccountId("remote-123");

        // When & Then
        assertEquals("remote-123", response.getRemoteAccountId());
    }

    @Test
    @DisplayName("Doit gérer tokenVersion")
    void shouldHandleTokenVersion() {
        // Given
        ServiceTokenResponse response = new ServiceTokenResponse();
        response.setTokenVersion(2);

        // When & Then
        assertEquals(2, response.getTokenVersion());
    }

    @Test
    @DisplayName("Doit gérer lastRefreshAt")
    void shouldHandleLastRefreshAt() {
        // Given
        LocalDateTime lastRefresh = LocalDateTime.now();
        ServiceTokenResponse response = new ServiceTokenResponse();
        response.setLastRefreshAt(lastRefresh);

        // When & Then
        assertEquals(lastRefresh, response.getLastRefreshAt());
    }

    @Test
    @DisplayName("Doit gérer les timestamps")
    void shouldHandleTimestamps() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        ServiceTokenResponse response = new ServiceTokenResponse();
        response.setCreatedAt(now);
        response.setUpdatedAt(now);

        // When & Then
        assertEquals(now, response.getCreatedAt());
        assertEquals(now, response.getUpdatedAt());
    }

    @Test
    @DisplayName("Doit gérer toString()")
    void shouldHandleToString() {
        // Given
        ServiceTokenResponse response = new ServiceTokenResponse();
        response.setId("token-123");
        response.setServiceKey("spotify");

        // When
        String toString = response.toString();

        // Then
        assertNotNull(toString);
        assertTrue(toString.contains("token-123"));
    }

    @Test
    @DisplayName("Doit gérer equals() et hashCode()")
    void shouldHandleEqualsAndHashCode() {
        // Given
        ServiceTokenResponse response1 = new ServiceTokenResponse();
        response1.setId("token-123");
        response1.setServiceKey("spotify");

        ServiceTokenResponse response2 = new ServiceTokenResponse();
        response2.setId("token-123");
        response2.setServiceKey("spotify");

        // When & Then
        assertEquals(response1, response2);
        assertEquals(response1.hashCode(), response2.hashCode());
    }
}
