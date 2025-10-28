package area.server.AREA_Back.service.Auth;

import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.entity.UserOAuthIdentity;
import area.server.AREA_Back.repository.UserOAuthIdentityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour OAuthTokenRefreshService
 * Type: Tests Unitaires
 * Description: Teste le rafraîchissement des tokens OAuth pour différents providers
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OAuthTokenRefreshService - Tests Unitaires")
@SuppressWarnings("unchecked")
class OAuthTokenRefreshServiceTest {

    @Mock
    private TokenEncryptionService tokenEncryptionService;

    @Mock
    private UserOAuthIdentityRepository userOAuthIdentityRepository;

    @Mock
    private RestTemplate restTemplate;

    private OAuthTokenRefreshService oauthTokenRefreshService;

    private User testUser;
    private UserOAuthIdentity spotifyIdentity;
    private UserOAuthIdentity googleIdentity;
    private UserOAuthIdentity discordIdentity;

    private static final String CLIENT_ID = "test-client-id";
    private static final String CLIENT_SECRET = "test-client-secret";
    private static final String ENCRYPTED_ACCESS_TOKEN = "encrypted-access-token";
    private static final String ENCRYPTED_REFRESH_TOKEN = "encrypted-refresh-token";
    private static final String DECRYPTED_REFRESH_TOKEN = "decrypted-refresh-token";
    private static final String NEW_ACCESS_TOKEN = "new-access-token";
    private static final String NEW_REFRESH_TOKEN = "new-refresh-token";
    private static final String NEW_ENCRYPTED_ACCESS_TOKEN = "new-encrypted-access-token";
    private static final String NEW_ENCRYPTED_REFRESH_TOKEN = "new-encrypted-refresh-token";
    private static final Integer EXPIRES_IN = 3600;

    @BeforeEach
    void setUp() {
        oauthTokenRefreshService = new OAuthTokenRefreshService(
            tokenEncryptionService,
            userOAuthIdentityRepository,
            restTemplate
        );

        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("test@example.com");

        spotifyIdentity = new UserOAuthIdentity();
        spotifyIdentity.setId(UUID.randomUUID());
        spotifyIdentity.setUser(testUser);
        spotifyIdentity.setProvider("SPOTIFY");
        spotifyIdentity.setAccessTokenEnc(ENCRYPTED_ACCESS_TOKEN);
        spotifyIdentity.setRefreshTokenEnc(ENCRYPTED_REFRESH_TOKEN);
        spotifyIdentity.setExpiresAt(LocalDateTime.now().plusHours(1));

        googleIdentity = new UserOAuthIdentity();
        googleIdentity.setId(UUID.randomUUID());
        googleIdentity.setUser(testUser);
        googleIdentity.setProvider("GOOGLE");
        googleIdentity.setAccessTokenEnc(ENCRYPTED_ACCESS_TOKEN);
        googleIdentity.setRefreshTokenEnc(ENCRYPTED_REFRESH_TOKEN);
        googleIdentity.setExpiresAt(LocalDateTime.now().plusHours(1));

        discordIdentity = new UserOAuthIdentity();
        discordIdentity.setId(UUID.randomUUID());
        discordIdentity.setUser(testUser);
        discordIdentity.setProvider("DISCORD");
        discordIdentity.setAccessTokenEnc(ENCRYPTED_ACCESS_TOKEN);
        discordIdentity.setRefreshTokenEnc(ENCRYPTED_REFRESH_TOKEN);
        discordIdentity.setExpiresAt(LocalDateTime.now().plusHours(1));
    }

    // ==================== Tests pour refreshSpotifyToken ====================

    @Test
    @DisplayName("refreshSpotifyToken - Succès du rafraîchissement")
    void testRefreshSpotifyToken_Success() {
        // Arrange
        when(tokenEncryptionService.decryptToken(ENCRYPTED_REFRESH_TOKEN))
            .thenReturn(DECRYPTED_REFRESH_TOKEN);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("access_token", NEW_ACCESS_TOKEN);
        responseBody.put("refresh_token", NEW_REFRESH_TOKEN);
        responseBody.put("expires_in", EXPIRES_IN);

        ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(
            responseBody,
            HttpStatus.OK
        );

        when(restTemplate.exchange(
            eq("https://accounts.spotify.com/api/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(responseEntity);

        when(tokenEncryptionService.encryptToken(NEW_ACCESS_TOKEN))
            .thenReturn(NEW_ENCRYPTED_ACCESS_TOKEN);
        when(tokenEncryptionService.encryptToken(NEW_REFRESH_TOKEN))
            .thenReturn(NEW_ENCRYPTED_REFRESH_TOKEN);

        when(userOAuthIdentityRepository.save(any(UserOAuthIdentity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        boolean result = oauthTokenRefreshService.refreshSpotifyToken(
            spotifyIdentity,
            CLIENT_ID,
            CLIENT_SECRET
        );

        // Assert
        assertTrue(result);
        verify(tokenEncryptionService).decryptToken(ENCRYPTED_REFRESH_TOKEN);
        verify(tokenEncryptionService).encryptToken(NEW_ACCESS_TOKEN);
        verify(tokenEncryptionService).encryptToken(NEW_REFRESH_TOKEN);
        verify(userOAuthIdentityRepository).save(spotifyIdentity);

        assertEquals(NEW_ENCRYPTED_ACCESS_TOKEN, spotifyIdentity.getAccessTokenEnc());
        assertEquals(NEW_ENCRYPTED_REFRESH_TOKEN, spotifyIdentity.getRefreshTokenEnc());
        assertNotNull(spotifyIdentity.getExpiresAt());
        assertNotNull(spotifyIdentity.getUpdatedAt());
    }

    @Test
    @DisplayName("refreshSpotifyToken - Échec si pas de refresh token")
    void testRefreshSpotifyToken_NoRefreshToken() {
        // Arrange
        spotifyIdentity.setRefreshTokenEnc(null);

        // Act
        boolean result = oauthTokenRefreshService.refreshSpotifyToken(
            spotifyIdentity,
            CLIENT_ID,
            CLIENT_SECRET
        );

        // Assert
        assertFalse(result);
        verify(tokenEncryptionService, never()).decryptToken(anyString());
        verify(restTemplate, never()).exchange(anyString(), any(), any(), any(ParameterizedTypeReference.class));
    }

    @Test
    @DisplayName("refreshSpotifyToken - Échec si refresh token vide")
    void testRefreshSpotifyToken_EmptyRefreshToken() {
        // Arrange
        spotifyIdentity.setRefreshTokenEnc("");

        // Act
        boolean result = oauthTokenRefreshService.refreshSpotifyToken(
            spotifyIdentity,
            CLIENT_ID,
            CLIENT_SECRET
        );

        // Assert
        assertFalse(result);
        verify(tokenEncryptionService, never()).decryptToken(anyString());
        verify(restTemplate, never()).exchange(anyString(), any(), any(), any(ParameterizedTypeReference.class));
    }

    @Test
    @DisplayName("refreshSpotifyToken - Utilise BasicAuth")
    void testRefreshSpotifyToken_UsesBasicAuth() {
        // Arrange
        when(tokenEncryptionService.decryptToken(ENCRYPTED_REFRESH_TOKEN))
            .thenReturn(DECRYPTED_REFRESH_TOKEN);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("access_token", NEW_ACCESS_TOKEN);
        responseBody.put("expires_in", EXPIRES_IN);

        ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(
            responseBody,
            HttpStatus.OK
        );

        ArgumentCaptor<HttpEntity<MultiValueMap<String, String>>> requestCaptor = 
            ArgumentCaptor.forClass(HttpEntity.class);

        when(restTemplate.exchange(
            eq("https://accounts.spotify.com/api/token"),
            eq(HttpMethod.POST),
            requestCaptor.capture(),
            any(ParameterizedTypeReference.class)
        )).thenReturn(responseEntity);

        when(tokenEncryptionService.encryptToken(NEW_ACCESS_TOKEN))
            .thenReturn(NEW_ENCRYPTED_ACCESS_TOKEN);

        when(userOAuthIdentityRepository.save(any(UserOAuthIdentity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        boolean result = oauthTokenRefreshService.refreshSpotifyToken(
            spotifyIdentity,
            CLIENT_ID,
            CLIENT_SECRET
        );

        // Assert
        assertTrue(result);
        HttpEntity<MultiValueMap<String, String>> capturedRequest = requestCaptor.getValue();
        assertNotNull(capturedRequest.getHeaders().getFirst("Authorization"));
        assertTrue(capturedRequest.getHeaders().getFirst("Authorization").startsWith("Basic "));
    }

    // ==================== Tests pour refreshGoogleToken ====================

    @Test
    @DisplayName("refreshGoogleToken - Succès du rafraîchissement")
    void testRefreshGoogleToken_Success() {
        // Arrange
        when(tokenEncryptionService.decryptToken(ENCRYPTED_REFRESH_TOKEN))
            .thenReturn(DECRYPTED_REFRESH_TOKEN);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("access_token", NEW_ACCESS_TOKEN);
        responseBody.put("expires_in", EXPIRES_IN);

        ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(
            responseBody,
            HttpStatus.OK
        );

        when(restTemplate.exchange(
            eq("https://oauth2.googleapis.com/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(responseEntity);

        when(tokenEncryptionService.encryptToken(NEW_ACCESS_TOKEN))
            .thenReturn(NEW_ENCRYPTED_ACCESS_TOKEN);

        when(userOAuthIdentityRepository.save(any(UserOAuthIdentity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        boolean result = oauthTokenRefreshService.refreshGoogleToken(
            googleIdentity,
            CLIENT_ID,
            CLIENT_SECRET
        );

        // Assert
        assertTrue(result);
        verify(tokenEncryptionService).decryptToken(ENCRYPTED_REFRESH_TOKEN);
        verify(tokenEncryptionService).encryptToken(NEW_ACCESS_TOKEN);
        verify(userOAuthIdentityRepository).save(googleIdentity);

        assertEquals(NEW_ENCRYPTED_ACCESS_TOKEN, googleIdentity.getAccessTokenEnc());
        assertNotNull(googleIdentity.getExpiresAt());
    }

    @Test
    @DisplayName("refreshGoogleToken - N'utilise pas BasicAuth")
    void testRefreshGoogleToken_NoBasicAuth() {
        // Arrange
        when(tokenEncryptionService.decryptToken(ENCRYPTED_REFRESH_TOKEN))
            .thenReturn(DECRYPTED_REFRESH_TOKEN);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("access_token", NEW_ACCESS_TOKEN);
        responseBody.put("expires_in", EXPIRES_IN);

        ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(
            responseBody,
            HttpStatus.OK
        );

        ArgumentCaptor<HttpEntity<MultiValueMap<String, String>>> requestCaptor = 
            ArgumentCaptor.forClass(HttpEntity.class);

        when(restTemplate.exchange(
            eq("https://oauth2.googleapis.com/token"),
            eq(HttpMethod.POST),
            requestCaptor.capture(),
            any(ParameterizedTypeReference.class)
        )).thenReturn(responseEntity);

        when(tokenEncryptionService.encryptToken(NEW_ACCESS_TOKEN))
            .thenReturn(NEW_ENCRYPTED_ACCESS_TOKEN);

        when(userOAuthIdentityRepository.save(any(UserOAuthIdentity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        boolean result = oauthTokenRefreshService.refreshGoogleToken(
            googleIdentity,
            CLIENT_ID,
            CLIENT_SECRET
        );

        // Assert
        assertTrue(result);
        HttpEntity<MultiValueMap<String, String>> capturedRequest = requestCaptor.getValue();
        assertNull(capturedRequest.getHeaders().getFirst("Authorization"));
        
        MultiValueMap<String, String> body = capturedRequest.getBody();
        assertNotNull(body);
        assertEquals(CLIENT_ID, body.getFirst("client_id"));
        assertEquals(CLIENT_SECRET, body.getFirst("client_secret"));
    }

    // ==================== Tests pour refreshDiscordToken ====================

    @Test
    @DisplayName("refreshDiscordToken - Succès du rafraîchissement")
    void testRefreshDiscordToken_Success() {
        // Arrange
        when(tokenEncryptionService.decryptToken(ENCRYPTED_REFRESH_TOKEN))
            .thenReturn(DECRYPTED_REFRESH_TOKEN);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("access_token", NEW_ACCESS_TOKEN);
        responseBody.put("refresh_token", NEW_REFRESH_TOKEN);
        responseBody.put("expires_in", EXPIRES_IN);

        ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(
            responseBody,
            HttpStatus.OK
        );

        when(restTemplate.exchange(
            eq("https://discord.com/api/oauth2/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(responseEntity);

        when(tokenEncryptionService.encryptToken(NEW_ACCESS_TOKEN))
            .thenReturn(NEW_ENCRYPTED_ACCESS_TOKEN);
        when(tokenEncryptionService.encryptToken(NEW_REFRESH_TOKEN))
            .thenReturn(NEW_ENCRYPTED_REFRESH_TOKEN);

        when(userOAuthIdentityRepository.save(any(UserOAuthIdentity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        boolean result = oauthTokenRefreshService.refreshDiscordToken(
            discordIdentity,
            CLIENT_ID,
            CLIENT_SECRET
        );

        // Assert
        assertTrue(result);
        verify(tokenEncryptionService).decryptToken(ENCRYPTED_REFRESH_TOKEN);
        verify(tokenEncryptionService).encryptToken(NEW_ACCESS_TOKEN);
        verify(tokenEncryptionService).encryptToken(NEW_REFRESH_TOKEN);
        verify(userOAuthIdentityRepository).save(discordIdentity);

        assertEquals(NEW_ENCRYPTED_ACCESS_TOKEN, discordIdentity.getAccessTokenEnc());
        assertEquals(NEW_ENCRYPTED_REFRESH_TOKEN, discordIdentity.getRefreshTokenEnc());
        assertNotNull(discordIdentity.getExpiresAt());
    }

    // ==================== Tests pour refreshToken (méthode générique) ====================

    @Test
    @DisplayName("refreshToken - Gère le nouveau refresh token optionnel")
    void testRefreshToken_OptionalNewRefreshToken() {
        // Arrange
        when(tokenEncryptionService.decryptToken(ENCRYPTED_REFRESH_TOKEN))
            .thenReturn(DECRYPTED_REFRESH_TOKEN);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("access_token", NEW_ACCESS_TOKEN);
        // Pas de nouveau refresh_token dans la réponse
        responseBody.put("expires_in", EXPIRES_IN);

        ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(
            responseBody,
            HttpStatus.OK
        );

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(responseEntity);

        when(tokenEncryptionService.encryptToken(NEW_ACCESS_TOKEN))
            .thenReturn(NEW_ENCRYPTED_ACCESS_TOKEN);

        when(userOAuthIdentityRepository.save(any(UserOAuthIdentity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        String originalRefreshToken = spotifyIdentity.getRefreshTokenEnc();

        // Act
        boolean result = oauthTokenRefreshService.refreshSpotifyToken(
            spotifyIdentity,
            CLIENT_ID,
            CLIENT_SECRET
        );

        // Assert
        assertTrue(result);
        assertEquals(originalRefreshToken, spotifyIdentity.getRefreshTokenEnc());
        verify(tokenEncryptionService, times(1)).encryptToken(any());
    }

    @Test
    @DisplayName("refreshToken - Gère expires_in comme Integer")
    void testRefreshToken_ExpiresInAsInteger() {
        // Arrange
        when(tokenEncryptionService.decryptToken(ENCRYPTED_REFRESH_TOKEN))
            .thenReturn(DECRYPTED_REFRESH_TOKEN);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("access_token", NEW_ACCESS_TOKEN);
        responseBody.put("expires_in", 7200); // Integer

        ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(
            responseBody,
            HttpStatus.OK
        );

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(responseEntity);

        when(tokenEncryptionService.encryptToken(NEW_ACCESS_TOKEN))
            .thenReturn(NEW_ENCRYPTED_ACCESS_TOKEN);

        when(userOAuthIdentityRepository.save(any(UserOAuthIdentity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        boolean result = oauthTokenRefreshService.refreshSpotifyToken(
            spotifyIdentity,
            CLIENT_ID,
            CLIENT_SECRET
        );

        // Assert
        assertTrue(result);
        assertNotNull(spotifyIdentity.getExpiresAt());
        assertTrue(spotifyIdentity.getExpiresAt().isAfter(LocalDateTime.now().plusHours(1)));
    }

    @Test
    @DisplayName("refreshToken - Gère expires_in comme String")
    void testRefreshToken_ExpiresInAsString() {
        // Arrange
        when(tokenEncryptionService.decryptToken(ENCRYPTED_REFRESH_TOKEN))
            .thenReturn(DECRYPTED_REFRESH_TOKEN);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("access_token", NEW_ACCESS_TOKEN);
        responseBody.put("expires_in", "3600"); // String

        ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(
            responseBody,
            HttpStatus.OK
        );

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(responseEntity);

        when(tokenEncryptionService.encryptToken(NEW_ACCESS_TOKEN))
            .thenReturn(NEW_ENCRYPTED_ACCESS_TOKEN);

        when(userOAuthIdentityRepository.save(any(UserOAuthIdentity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        boolean result = oauthTokenRefreshService.refreshSpotifyToken(
            spotifyIdentity,
            CLIENT_ID,
            CLIENT_SECRET
        );

        // Assert
        assertTrue(result);
        assertNotNull(spotifyIdentity.getExpiresAt());
    }

    @Test
    @DisplayName("refreshToken - Gère expires_in invalide")
    void testRefreshToken_InvalidExpiresIn() {
        // Arrange
        when(tokenEncryptionService.decryptToken(ENCRYPTED_REFRESH_TOKEN))
            .thenReturn(DECRYPTED_REFRESH_TOKEN);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("access_token", NEW_ACCESS_TOKEN);
        responseBody.put("expires_in", "invalid"); // String non parsable

        ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(
            responseBody,
            HttpStatus.OK
        );

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(responseEntity);

        when(tokenEncryptionService.encryptToken(NEW_ACCESS_TOKEN))
            .thenReturn(NEW_ENCRYPTED_ACCESS_TOKEN);

        when(userOAuthIdentityRepository.save(any(UserOAuthIdentity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        LocalDateTime originalExpiresAt = spotifyIdentity.getExpiresAt();

        // Act
        boolean result = oauthTokenRefreshService.refreshSpotifyToken(
            spotifyIdentity,
            CLIENT_ID,
            CLIENT_SECRET
        );

        // Assert
        assertTrue(result);
        // expiresAt ne devrait pas être mis à jour si le parsing échoue
        assertEquals(originalExpiresAt, spotifyIdentity.getExpiresAt());
    }

    @Test
    @DisplayName("refreshToken - Échoue si status HTTP non 2xx")
    void testRefreshToken_HttpError() {
        // Arrange
        when(tokenEncryptionService.decryptToken(ENCRYPTED_REFRESH_TOKEN))
            .thenReturn(DECRYPTED_REFRESH_TOKEN);

        ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(
            HttpStatus.UNAUTHORIZED
        );

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(responseEntity);

        // Act
        boolean result = oauthTokenRefreshService.refreshSpotifyToken(
            spotifyIdentity,
            CLIENT_ID,
            CLIENT_SECRET
        );

        // Assert
        assertFalse(result);
        verify(userOAuthIdentityRepository, never()).save(any());
    }

    @Test
    @DisplayName("refreshToken - Échoue si body est null")
    void testRefreshToken_NullResponseBody() {
        // Arrange
        when(tokenEncryptionService.decryptToken(ENCRYPTED_REFRESH_TOKEN))
            .thenReturn(DECRYPTED_REFRESH_TOKEN);

        ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(
            null,
            HttpStatus.OK
        );

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(responseEntity);

        // Act
        boolean result = oauthTokenRefreshService.refreshSpotifyToken(
            spotifyIdentity,
            CLIENT_ID,
            CLIENT_SECRET
        );

        // Assert
        assertFalse(result);
        verify(userOAuthIdentityRepository, never()).save(any());
    }

    @Test
    @DisplayName("refreshToken - Échoue si access_token manquant dans la réponse")
    void testRefreshToken_MissingAccessToken() {
        // Arrange
        when(tokenEncryptionService.decryptToken(ENCRYPTED_REFRESH_TOKEN))
            .thenReturn(DECRYPTED_REFRESH_TOKEN);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("expires_in", EXPIRES_IN);
        // Pas d'access_token

        ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(
            responseBody,
            HttpStatus.OK
        );

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(responseEntity);

        // Act
        boolean result = oauthTokenRefreshService.refreshSpotifyToken(
            spotifyIdentity,
            CLIENT_ID,
            CLIENT_SECRET
        );

        // Assert
        assertFalse(result);
        verify(userOAuthIdentityRepository, never()).save(any());
    }

    @Test
    @DisplayName("refreshToken - Gère les exceptions REST")
    void testRefreshToken_RestClientException() {
        // Arrange
        when(tokenEncryptionService.decryptToken(ENCRYPTED_REFRESH_TOKEN))
            .thenReturn(DECRYPTED_REFRESH_TOKEN);

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenThrow(new RestClientException("Network error"));

        // Act
        boolean result = oauthTokenRefreshService.refreshSpotifyToken(
            spotifyIdentity,
            CLIENT_ID,
            CLIENT_SECRET
        );

        // Assert
        assertFalse(result);
        verify(userOAuthIdentityRepository, never()).save(any());
    }

    @Test
    @DisplayName("refreshToken - Gère les exceptions de décryptage")
    void testRefreshToken_DecryptionException() {
        // Arrange
        when(tokenEncryptionService.decryptToken(ENCRYPTED_REFRESH_TOKEN))
            .thenThrow(new RuntimeException("Decryption failed"));

        // Act
        boolean result = oauthTokenRefreshService.refreshSpotifyToken(
            spotifyIdentity,
            CLIENT_ID,
            CLIENT_SECRET
        );

        // Assert
        assertFalse(result);
        verify(restTemplate, never()).exchange(anyString(), any(), any(), any(ParameterizedTypeReference.class));
        verify(userOAuthIdentityRepository, never()).save(any());
    }

    @Test
    @DisplayName("refreshToken - Met à jour updatedAt lors du rafraîchissement")
    void testRefreshToken_UpdatesUpdatedAt() {
        // Arrange
        when(tokenEncryptionService.decryptToken(ENCRYPTED_REFRESH_TOKEN))
            .thenReturn(DECRYPTED_REFRESH_TOKEN);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("access_token", NEW_ACCESS_TOKEN);
        responseBody.put("expires_in", EXPIRES_IN);

        ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(
            responseBody,
            HttpStatus.OK
        );

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(responseEntity);

        when(tokenEncryptionService.encryptToken(NEW_ACCESS_TOKEN))
            .thenReturn(NEW_ENCRYPTED_ACCESS_TOKEN);

        when(userOAuthIdentityRepository.save(any(UserOAuthIdentity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        LocalDateTime beforeUpdate = LocalDateTime.now();

        // Act
        boolean result = oauthTokenRefreshService.refreshSpotifyToken(
            spotifyIdentity,
            CLIENT_ID,
            CLIENT_SECRET
        );

        // Assert
        assertTrue(result);
        assertNotNull(spotifyIdentity.getUpdatedAt());
        assertTrue(spotifyIdentity.getUpdatedAt().isAfter(beforeUpdate) || 
                   spotifyIdentity.getUpdatedAt().isEqual(beforeUpdate));
    }

    // ==================== Tests pour needsRefresh ====================

    @Test
    @DisplayName("needsRefresh - Retourne false si expiresAt est null")
    void testNeedsRefresh_NullExpiresAt() {
        // Arrange
        spotifyIdentity.setExpiresAt(null);

        // Act
        boolean result = oauthTokenRefreshService.needsRefresh(spotifyIdentity);

        // Assert
        assertFalse(result);
    }

    @Test
    @DisplayName("needsRefresh - Retourne true si token expiré")
    void testNeedsRefresh_TokenExpired() {
        // Arrange
        spotifyIdentity.setExpiresAt(LocalDateTime.now().minusHours(1));

        // Act
        boolean result = oauthTokenRefreshService.needsRefresh(spotifyIdentity);

        // Assert
        assertTrue(result);
    }

    @Test
    @DisplayName("needsRefresh - Retourne true si token expire dans moins de 5 minutes")
    void testNeedsRefresh_TokenExpiringWithinBufferTime() {
        // Arrange
        spotifyIdentity.setExpiresAt(LocalDateTime.now().plusMinutes(3));

        // Act
        boolean result = oauthTokenRefreshService.needsRefresh(spotifyIdentity);

        // Assert
        assertTrue(result);
    }

    @Test
    @DisplayName("needsRefresh - Retourne true si token expire exactement dans 5 minutes")
    void testNeedsRefresh_TokenExpiringExactlyAtBufferTime() {
        // Arrange
        spotifyIdentity.setExpiresAt(LocalDateTime.now().plusMinutes(5));

        // Act
        boolean result = oauthTokenRefreshService.needsRefresh(spotifyIdentity);

        // Assert
        assertTrue(result);
    }

    @Test
    @DisplayName("needsRefresh - Retourne false si token expire dans plus de 5 minutes")
    void testNeedsRefresh_TokenNotExpiring() {
        // Arrange
        spotifyIdentity.setExpiresAt(LocalDateTime.now().plusMinutes(10));

        // Act
        boolean result = oauthTokenRefreshService.needsRefresh(spotifyIdentity);

        // Assert
        assertFalse(result);
    }

    @Test
    @DisplayName("needsRefresh - Retourne false si token expire dans 1 heure")
    void testNeedsRefresh_TokenValidForLongTime() {
        // Arrange
        spotifyIdentity.setExpiresAt(LocalDateTime.now().plusHours(1));

        // Act
        boolean result = oauthTokenRefreshService.needsRefresh(spotifyIdentity);

        // Assert
        assertFalse(result);
    }

    @Test
    @DisplayName("needsRefresh - Teste le cas limite juste avant le buffer")
    void testNeedsRefresh_JustBeforeBuffer() {
        // Arrange
        spotifyIdentity.setExpiresAt(LocalDateTime.now().plusMinutes(6));

        // Act
        boolean result = oauthTokenRefreshService.needsRefresh(spotifyIdentity);

        // Assert
        assertFalse(result);
    }

    @Test
    @DisplayName("needsRefresh - Teste le cas limite juste après le buffer")
    void testNeedsRefresh_JustAfterBuffer() {
        // Arrange
        spotifyIdentity.setExpiresAt(LocalDateTime.now().plusMinutes(4));

        // Act
        boolean result = oauthTokenRefreshService.needsRefresh(spotifyIdentity);

        // Assert
        assertTrue(result);
    }
}
