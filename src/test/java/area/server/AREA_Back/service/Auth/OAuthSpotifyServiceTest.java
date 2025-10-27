package area.server.AREA_Back.service.Auth;

import area.server.AREA_Back.config.JwtCookieProperties;
import area.server.AREA_Back.dto.AuthResponse;
import area.server.AREA_Back.dto.OAuthLoginRequest;
import area.server.AREA_Back.dto.UserResponse;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.entity.UserOAuthIdentity;
import area.server.AREA_Back.repository.UserOAuthIdentityRepository;
import area.server.AREA_Back.repository.UserRepository;
import area.server.AREA_Back.service.Redis.RedisTokenService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServletResponse;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour OAuthSpotifyService
 * Type: Tests Unitaires
 * Description: Teste l'authentification OAuth Spotify et la liaison de comptes
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OAuthSpotifyService - Tests Unitaires")
class OAuthSpotifyServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private JwtService jwtService;

    @Mock
    private JwtCookieProperties jwtCookieProperties;

    @Mock
    private RedisTokenService redisTokenService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenEncryptionService tokenEncryptionService;

    @Mock
    private UserOAuthIdentityRepository userOAuthIdentityRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthService authService;

    @Mock
    private HttpServletResponse httpServletResponse;

    private MeterRegistry meterRegistry;
    private OAuthSpotifyService oauthSpotifyService;

    private static final String CLIENT_ID = "test-client-id";
    private static final String CLIENT_SECRET = "test-client-secret";
    private static final String REDIRECT_BASE_URL = "http://localhost:3000";
    private static final String AUTHORIZATION_CODE = "test-auth-code";
    private static final String ACCESS_TOKEN = "test-access-token";
    private static final String REFRESH_TOKEN = "test-refresh-token";
    private static final String ENCRYPTED_ACCESS_TOKEN = "encrypted-access-token";
    private static final String ENCRYPTED_REFRESH_TOKEN = "encrypted-refresh-token";
    private static final String USER_EMAIL = "test@spotify.com";
    private static final String USER_ID = "spotify-user-123";
    private static final String DISPLAY_NAME = "Test User";
    private static final String AVATAR_URL = "https://avatar.url/image.png";
    private static final String COUNTRY = "US";
    private static final String PRODUCT = "premium";
    private static final Integer EXPIRES_IN = 3600;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        oauthSpotifyService = new OAuthSpotifyService(
            CLIENT_ID,
            CLIENT_SECRET,
            REDIRECT_BASE_URL,
            jwtService,
            jwtCookieProperties,
            meterRegistry,
            redisTokenService,
            passwordEncoder,
            tokenEncryptionService,
            userOAuthIdentityRepository,
            userRepository,
            authService,
            restTemplate
        );
        // Appel manuel de initMetrics car @PostConstruct n'est pas appelé dans les tests
        try {
            java.lang.reflect.Method initMetricsMethod = OAuthSpotifyService.class.getDeclaredMethod("initMetrics");
            initMetricsMethod.setAccessible(true);
            initMetricsMethod.invoke(oauthSpotifyService);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize metrics", e);
        }
    }

    // ==================== Tests pour authenticate() ====================

    @Test
    @DisplayName("Doit authentifier un utilisateur avec succès via Spotify OAuth")
    void shouldAuthenticateUserSuccessfully() {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode(AUTHORIZATION_CODE);

        Map<String, Object> tokenResponse = createTokenResponseMap();
        Map<String, Object> userProfileResponse = createUserProfileResponseMap();

        when(restTemplate.exchange(
            eq("https://accounts.spotify.com/api/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

        when(restTemplate.exchange(
            eq("https://api.spotify.com/v1/me"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(userProfileResponse, HttpStatus.OK));

        User user = createTestUser();
        UserResponse userResponse = createTestUserResponse(user);
        AuthResponse authResponse = new AuthResponse();
        authResponse.setUser(userResponse);

        when(authService.oauthLogin(eq(USER_EMAIL), eq(AVATAR_URL), eq(httpServletResponse)))
            .thenReturn(authResponse);
        when(userRepository.findById(userResponse.getId())).thenReturn(Optional.of(user));
        when(tokenEncryptionService.encryptToken(ACCESS_TOKEN)).thenReturn(ENCRYPTED_ACCESS_TOKEN);
        when(tokenEncryptionService.encryptToken(REFRESH_TOKEN)).thenReturn(ENCRYPTED_REFRESH_TOKEN);
        when(userOAuthIdentityRepository.findByUserAndProvider(user, "spotify"))
            .thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.save(any(UserOAuthIdentity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        AuthResponse result = oauthSpotifyService.authenticate(request, httpServletResponse);

        // Then
        assertNotNull(result);
        assertEquals(userResponse, result.getUser());
        verify(authService).oauthLogin(USER_EMAIL, AVATAR_URL, httpServletResponse);
        verify(userOAuthIdentityRepository).save(any(UserOAuthIdentity.class));

        // Vérifier les métriques
        Counter successCounter = meterRegistry.find("oauth.spotify.login.success").counter();
        assertNotNull(successCounter);
        assertEquals(1.0, successCounter.count());
    }

    @Test
    @DisplayName("Doit échouer si l'email Spotify est manquant")
    void shouldFailWhenSpotifyEmailIsMissing() {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode(AUTHORIZATION_CODE);

        Map<String, Object> tokenResponse = createTokenResponseMap();
        Map<String, Object> userProfileResponse = createUserProfileResponseMap();
        userProfileResponse.remove("email"); // Pas d'email

        when(restTemplate.exchange(
            eq("https://accounts.spotify.com/api/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

        when(restTemplate.exchange(
            eq("https://api.spotify.com/v1/me"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(userProfileResponse, HttpStatus.OK));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            oauthSpotifyService.authenticate(request, httpServletResponse)
        );

        assertTrue(exception.getMessage().contains("email is required for authentication"));

        // Vérifier que les métriques d'échec ont été incrémentées
        Counter failureCounterAfter = meterRegistry.find("oauth.spotify.login.failure").counter();
        assertNotNull(failureCounterAfter);
        assertTrue(failureCounterAfter.count() >= 1.0, "Le compteur d'échec doit être >= 1");
    }

    @Test
    @DisplayName("Doit échouer quand l'échange de token échoue")
    void shouldFailWhenTokenExchangeFails() {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode(AUTHORIZATION_CODE);

        when(restTemplate.exchange(
            eq("https://accounts.spotify.com/api/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenThrow(new RestClientException("Token exchange failed"));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            oauthSpotifyService.authenticate(request, httpServletResponse)
        );

        assertTrue(exception.getMessage().contains("OAuth authentication failed"));

        // Vérifier les métriques
        Counter failureCounter = meterRegistry.find("oauth.spotify.login.failure").counter();
        assertNotNull(failureCounter);
        assertEquals(1.0, failureCounter.count());

        Counter tokenExchangeFailures = meterRegistry.find("oauth.spotify.token_exchange.failures").counter();
        assertNotNull(tokenExchangeFailures);
        assertEquals(1.0, tokenExchangeFailures.count());
    }

    @Test
    @DisplayName("Doit mettre à jour un OAuth identity existant lors de l'authentification")
    void shouldUpdateExistingOAuthIdentityOnAuthentication() {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode(AUTHORIZATION_CODE);

        Map<String, Object> tokenResponse = createTokenResponseMap();
        Map<String, Object> userProfileResponse = createUserProfileResponseMap();

        when(restTemplate.exchange(
            eq("https://accounts.spotify.com/api/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

        when(restTemplate.exchange(
            eq("https://api.spotify.com/v1/me"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(userProfileResponse, HttpStatus.OK));

        User user = createTestUser();
        UserOAuthIdentity existingOAuth = new UserOAuthIdentity();
        existingOAuth.setUser(user);
        existingOAuth.setProvider("spotify");
        existingOAuth.setProviderUserId("old-user-id");

        AuthResponse authResponse = new AuthResponse();
        UserResponse userResponse = createTestUserResponse(user);
        authResponse.setUser(userResponse);

        when(authService.oauthLogin(eq(USER_EMAIL), eq(AVATAR_URL), eq(httpServletResponse)))
            .thenReturn(authResponse);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(tokenEncryptionService.encryptToken(ACCESS_TOKEN)).thenReturn(ENCRYPTED_ACCESS_TOKEN);
        when(tokenEncryptionService.encryptToken(REFRESH_TOKEN)).thenReturn(ENCRYPTED_REFRESH_TOKEN);
        when(userOAuthIdentityRepository.findByUserAndProvider(user, "spotify"))
            .thenReturn(Optional.of(existingOAuth));
        when(userOAuthIdentityRepository.save(any(UserOAuthIdentity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        AuthResponse result = oauthSpotifyService.authenticate(request, httpServletResponse);

        // Then
        assertNotNull(result);
        ArgumentCaptor<UserOAuthIdentity> captor = ArgumentCaptor.forClass(UserOAuthIdentity.class);
        verify(userOAuthIdentityRepository).save(captor.capture());

        UserOAuthIdentity savedOAuth = captor.getValue();
        assertEquals(USER_ID, savedOAuth.getProviderUserId());
        assertEquals(ENCRYPTED_ACCESS_TOKEN, savedOAuth.getAccessTokenEnc());
        assertEquals(ENCRYPTED_REFRESH_TOKEN, savedOAuth.getRefreshTokenEnc());
    }

    // ==================== Tests pour linkToExistingUser() ====================

    @Test
    @DisplayName("Doit lier un compte Spotify à un utilisateur existant")
    void shouldLinkSpotifyAccountToExistingUser() {
        // Given
        User existingUser = createTestUser();

        Map<String, Object> tokenResponse = createTokenResponseMap();
        Map<String, Object> userProfileResponse = createUserProfileResponseMap();

        when(restTemplate.exchange(
            eq("https://accounts.spotify.com/api/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

        when(restTemplate.exchange(
            eq("https://api.spotify.com/v1/me"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(userProfileResponse, HttpStatus.OK));

        when(tokenEncryptionService.encryptToken(ACCESS_TOKEN)).thenReturn(ENCRYPTED_ACCESS_TOKEN);
        when(tokenEncryptionService.encryptToken(REFRESH_TOKEN)).thenReturn(ENCRYPTED_REFRESH_TOKEN);
        when(userOAuthIdentityRepository.findByProviderAndProviderUserId("spotify", USER_ID))
            .thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.findByUserAndProvider(existingUser, "spotify"))
            .thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.save(any(UserOAuthIdentity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        UserOAuthIdentity result = oauthSpotifyService.linkToExistingUser(existingUser, AUTHORIZATION_CODE);

        // Then
        assertNotNull(result);
        assertEquals(existingUser, result.getUser());
        assertEquals("spotify", result.getProvider());
        assertEquals(USER_ID, result.getProviderUserId());
        assertEquals(ENCRYPTED_ACCESS_TOKEN, result.getAccessTokenEnc());
        assertEquals(ENCRYPTED_REFRESH_TOKEN, result.getRefreshTokenEnc());

        Map<String, Object> tokenMeta = result.getTokenMeta();
        assertEquals(USER_EMAIL, tokenMeta.get("email"));
        assertEquals(DISPLAY_NAME, tokenMeta.get("display_name"));
        assertEquals(AVATAR_URL, tokenMeta.get("avatar_url"));
        assertEquals(COUNTRY, tokenMeta.get("country"));
        assertEquals(PRODUCT, tokenMeta.get("product"));

        verify(userOAuthIdentityRepository).save(any(UserOAuthIdentity.class));
    }

    @Test
    @DisplayName("Doit échouer si le compte Spotify est déjà lié à un autre utilisateur")
    void shouldFailWhenSpotifyAccountAlreadyLinkedToAnotherUser() {
        // Given
        User existingUser = createTestUser();
        User otherUser = new User();
        otherUser.setId(UUID.randomUUID());
        otherUser.setEmail("other@example.com");

        Map<String, Object> tokenResponse = createTokenResponseMap();
        Map<String, Object> userProfileResponse = createUserProfileResponseMap();

        when(restTemplate.exchange(
            eq("https://accounts.spotify.com/api/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

        when(restTemplate.exchange(
            eq("https://api.spotify.com/v1/me"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(userProfileResponse, HttpStatus.OK));

        UserOAuthIdentity existingOAuth = new UserOAuthIdentity();
        existingOAuth.setUser(otherUser);
        existingOAuth.setProvider("spotify");
        existingOAuth.setProviderUserId(USER_ID);

        when(userOAuthIdentityRepository.findByProviderAndProviderUserId("spotify", USER_ID))
            .thenReturn(Optional.of(existingOAuth));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            oauthSpotifyService.linkToExistingUser(existingUser, AUTHORIZATION_CODE)
        );

        assertTrue(exception.getMessage().contains("already linked to another user"));
        verify(userOAuthIdentityRepository, never()).save(any());
    }

    @Test
    @DisplayName("Doit échouer si l'email Spotify est manquant lors de la liaison")
    void shouldFailWhenEmailMissingDuringLinking() {
        // Given
        User existingUser = createTestUser();

        Map<String, Object> tokenResponse = createTokenResponseMap();
        Map<String, Object> userProfileResponse = createUserProfileResponseMap();
        userProfileResponse.remove("email");

        when(restTemplate.exchange(
            eq("https://accounts.spotify.com/api/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

        when(restTemplate.exchange(
            eq("https://api.spotify.com/v1/me"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(userProfileResponse, HttpStatus.OK));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            oauthSpotifyService.linkToExistingUser(existingUser, AUTHORIZATION_CODE)
        );

        assertTrue(exception.getMessage().contains("email is required for account linking"));
    }

    @Test
    @DisplayName("Doit mettre à jour un OAuth identity existant lors de la liaison")
    void shouldUpdateExistingOAuthIdentityDuringLinking() {
        // Given
        User existingUser = createTestUser();
        UserOAuthIdentity existingOAuth = new UserOAuthIdentity();
        existingOAuth.setUser(existingUser);
        existingOAuth.setProvider("spotify");
        existingOAuth.setProviderUserId("old-id");

        Map<String, Object> tokenResponse = createTokenResponseMap();
        Map<String, Object> userProfileResponse = createUserProfileResponseMap();

        when(restTemplate.exchange(
            eq("https://accounts.spotify.com/api/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

        when(restTemplate.exchange(
            eq("https://api.spotify.com/v1/me"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(userProfileResponse, HttpStatus.OK));

        when(tokenEncryptionService.encryptToken(ACCESS_TOKEN)).thenReturn(ENCRYPTED_ACCESS_TOKEN);
        when(tokenEncryptionService.encryptToken(REFRESH_TOKEN)).thenReturn(ENCRYPTED_REFRESH_TOKEN);
        when(userOAuthIdentityRepository.findByProviderAndProviderUserId("spotify", USER_ID))
            .thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.findByUserAndProvider(existingUser, "spotify"))
            .thenReturn(Optional.of(existingOAuth));
        when(userOAuthIdentityRepository.save(any(UserOAuthIdentity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        UserOAuthIdentity result = oauthSpotifyService.linkToExistingUser(existingUser, AUTHORIZATION_CODE);

        // Then
        assertNotNull(result);
        assertEquals(USER_ID, result.getProviderUserId());
        verify(userOAuthIdentityRepository).save(any(UserOAuthIdentity.class));
    }

    // ==================== Tests pour exchangeCodeForToken() ====================

    @Test
    @DisplayName("Doit échanger le code d'autorisation contre un token")
    void shouldExchangeCodeForToken() {
        // Given
        Map<String, Object> tokenResponse = createTokenResponseMap();

        when(restTemplate.exchange(
            eq("https://accounts.spotify.com/api/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

        // When
        // On doit utiliser authenticate pour tester indirectement exchangeCodeForToken
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode(AUTHORIZATION_CODE);

        Map<String, Object> userProfileResponse = createUserProfileResponseMap();
        when(restTemplate.exchange(
            eq("https://api.spotify.com/v1/me"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(userProfileResponse, HttpStatus.OK));

        User user = createTestUser();
        AuthResponse authResponse = new AuthResponse();
        UserResponse userResponse = createTestUserResponse(user);
        authResponse.setUser(userResponse);

        when(authService.oauthLogin(anyString(), anyString(), any())).thenReturn(authResponse);
        when(userRepository.findById(any())).thenReturn(Optional.of(user));
        when(tokenEncryptionService.encryptToken(anyString())).thenReturn("encrypted");
        when(userOAuthIdentityRepository.findByUserAndProvider(any(), anyString()))
            .thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Then
        assertDoesNotThrow(() -> oauthSpotifyService.authenticate(request, httpServletResponse));

        // Vérifier les métriques
        Counter tokenExchangeCounter = meterRegistry.find("oauth.spotify.token_exchange.calls").counter();
        assertNotNull(tokenExchangeCounter);
        assertEquals(1.0, tokenExchangeCounter.count());
    }

    @Test
    @DisplayName("Doit échouer si le token response n'a pas d'access_token")
    void shouldFailWhenTokenResponseMissingAccessToken() {
        // Given
        Map<String, Object> tokenResponse = new HashMap<>();
        tokenResponse.put("token_type", "Bearer");
        // Pas d'access_token

        when(restTemplate.exchange(
            eq("https://accounts.spotify.com/api/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode(AUTHORIZATION_CODE);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            oauthSpotifyService.authenticate(request, httpServletResponse)
        );

        assertTrue(exception.getMessage().contains("No access_token in Spotify response"));

        // Vérifier que les métriques ont été incrémentées
        Counter tokenExchangeFailures = meterRegistry.find("oauth.spotify.token_exchange.failures").counter();
        assertNotNull(tokenExchangeFailures);
        assertTrue(tokenExchangeFailures.count() >= 1.0, "Le compteur d'échec doit être >= 1");
    }

    @Test
    @DisplayName("Doit gérer un token response sans refresh_token")
    void shouldHandleTokenResponseWithoutRefreshToken() {
        // Given
        Map<String, Object> tokenResponse = new HashMap<>();
        tokenResponse.put("access_token", ACCESS_TOKEN);
        tokenResponse.put("expires_in", EXPIRES_IN);
        // Pas de refresh_token

        when(restTemplate.exchange(
            eq("https://accounts.spotify.com/api/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

        Map<String, Object> userProfileResponse = createUserProfileResponseMap();
        when(restTemplate.exchange(
            eq("https://api.spotify.com/v1/me"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(userProfileResponse, HttpStatus.OK));

        User user = createTestUser();
        AuthResponse authResponse = new AuthResponse();
        UserResponse userResponse = createTestUserResponse(user);
        authResponse.setUser(userResponse);

        when(authService.oauthLogin(anyString(), anyString(), any())).thenReturn(authResponse);
        when(userRepository.findById(any())).thenReturn(Optional.of(user));
        when(tokenEncryptionService.encryptToken(ACCESS_TOKEN)).thenReturn(ENCRYPTED_ACCESS_TOKEN);
        when(userOAuthIdentityRepository.findByUserAndProvider(any(), anyString()))
            .thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode(AUTHORIZATION_CODE);

        // When
        AuthResponse result = oauthSpotifyService.authenticate(request, httpServletResponse);

        // Then
        assertNotNull(result);
        ArgumentCaptor<UserOAuthIdentity> captor = ArgumentCaptor.forClass(UserOAuthIdentity.class);
        verify(userOAuthIdentityRepository).save(captor.capture());

        UserOAuthIdentity savedOAuth = captor.getValue();
        assertNull(savedOAuth.getRefreshTokenEnc());
    }

    @Test
    @DisplayName("Doit gérer les différents formats d'expires_in")
    void shouldHandleDifferentExpiresInFormats() {
        // Given - expires_in comme String
        Map<String, Object> tokenResponse = new HashMap<>();
        tokenResponse.put("access_token", ACCESS_TOKEN);
        tokenResponse.put("refresh_token", REFRESH_TOKEN);
        tokenResponse.put("expires_in", "3600"); // String au lieu d'Integer

        when(restTemplate.exchange(
            eq("https://accounts.spotify.com/api/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

        Map<String, Object> userProfileResponse = createUserProfileResponseMap();
        when(restTemplate.exchange(
            eq("https://api.spotify.com/v1/me"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(userProfileResponse, HttpStatus.OK));

        User user = createTestUser();
        AuthResponse authResponse = new AuthResponse();
        UserResponse userResponse = createTestUserResponse(user);
        authResponse.setUser(userResponse);

        when(authService.oauthLogin(anyString(), anyString(), any())).thenReturn(authResponse);
        when(userRepository.findById(any())).thenReturn(Optional.of(user));
        when(tokenEncryptionService.encryptToken(anyString())).thenReturn("encrypted");
        when(userOAuthIdentityRepository.findByUserAndProvider(any(), anyString()))
            .thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode(AUTHORIZATION_CODE);

        // When & Then
        assertDoesNotThrow(() -> oauthSpotifyService.authenticate(request, httpServletResponse));
    }

    // ==================== Tests pour fetchUserProfile() ====================

    @Test
    @DisplayName("Doit récupérer le profil utilisateur Spotify")
    void shouldFetchUserProfile() {
        // Given
        Map<String, Object> userProfileResponse = createUserProfileResponseMap();

        when(restTemplate.exchange(
            eq("https://api.spotify.com/v1/me"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(userProfileResponse, HttpStatus.OK));

        Map<String, Object> tokenResponse = createTokenResponseMap();
        when(restTemplate.exchange(
            eq("https://accounts.spotify.com/api/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

        User user = createTestUser();
        AuthResponse authResponse = new AuthResponse();
        UserResponse userResponse = createTestUserResponse(user);
        authResponse.setUser(userResponse);

        when(authService.oauthLogin(anyString(), anyString(), any())).thenReturn(authResponse);
        when(userRepository.findById(any())).thenReturn(Optional.of(user));
        when(tokenEncryptionService.encryptToken(anyString())).thenReturn("encrypted");
        when(userOAuthIdentityRepository.findByUserAndProvider(any(), anyString()))
            .thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode(AUTHORIZATION_CODE);

        // When
        AuthResponse result = oauthSpotifyService.authenticate(request, httpServletResponse);

        // Then
        assertNotNull(result);
        verify(restTemplate).exchange(
            eq("https://api.spotify.com/v1/me"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        );
    }

    @Test
    @DisplayName("Doit échouer si le profil utilisateur n'a pas d'ID")
    void shouldFailWhenUserProfileMissingId() {
        // Given
        Map<String, Object> tokenResponse = createTokenResponseMap();
        when(restTemplate.exchange(
            eq("https://accounts.spotify.com/api/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

        Map<String, Object> userProfileResponse = createUserProfileResponseMap();
        userProfileResponse.remove("id"); // Pas d'ID

        when(restTemplate.exchange(
            eq("https://api.spotify.com/v1/me"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(userProfileResponse, HttpStatus.OK));

        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode(AUTHORIZATION_CODE);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            oauthSpotifyService.authenticate(request, httpServletResponse)
        );

        assertTrue(exception.getMessage().contains("user id is missing"));
    }

    @Test
    @DisplayName("Doit gérer un profil sans image")
    void shouldHandleProfileWithoutImage() {
        // Given
        Map<String, Object> userProfileResponse = createUserProfileResponseMap();
        userProfileResponse.remove("images");

        Map<String, Object> tokenResponse = createTokenResponseMap();
        when(restTemplate.exchange(
            eq("https://accounts.spotify.com/api/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

        when(restTemplate.exchange(
            eq("https://api.spotify.com/v1/me"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(userProfileResponse, HttpStatus.OK));

        User user = createTestUser();
        AuthResponse authResponse = new AuthResponse();
        UserResponse userResponse = createTestUserResponse(user);
        authResponse.setUser(userResponse);

        when(authService.oauthLogin(eq(USER_EMAIL), eq(""), any()))
            .thenReturn(authResponse);
        when(userRepository.findById(any())).thenReturn(Optional.of(user));
        when(tokenEncryptionService.encryptToken(anyString())).thenReturn("encrypted");
        when(userOAuthIdentityRepository.findByUserAndProvider(any(), anyString()))
            .thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode(AUTHORIZATION_CODE);

        // When
        AuthResponse result = oauthSpotifyService.authenticate(request, httpServletResponse);

        // Then
        assertNotNull(result);
        verify(authService).oauthLogin(eq(USER_EMAIL), eq(""), any());
    }

    @Test
    @DisplayName("Doit gérer un profil avec des champs optionnels manquants")
    void shouldHandleProfileWithMissingOptionalFields() {
        // Given
        Map<String, Object> userProfileResponse = new HashMap<>();
        userProfileResponse.put("id", USER_ID);
        userProfileResponse.put("email", USER_EMAIL);
        // Pas de display_name, country, product

        Map<String, Object> tokenResponse = createTokenResponseMap();
        when(restTemplate.exchange(
            eq("https://accounts.spotify.com/api/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

        when(restTemplate.exchange(
            eq("https://api.spotify.com/v1/me"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(userProfileResponse, HttpStatus.OK));

        User user = createTestUser();
        AuthResponse authResponse = new AuthResponse();
        UserResponse userResponse = createTestUserResponse(user);
        authResponse.setUser(userResponse);

        when(authService.oauthLogin(anyString(), anyString(), any())).thenReturn(authResponse);
        when(userRepository.findById(any())).thenReturn(Optional.of(user));
        when(tokenEncryptionService.encryptToken(anyString())).thenReturn("encrypted");
        when(userOAuthIdentityRepository.findByUserAndProvider(any(), anyString()))
            .thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode(AUTHORIZATION_CODE);

        // When
        AuthResponse result = oauthSpotifyService.authenticate(request, httpServletResponse);

        // Then
        assertNotNull(result);
        ArgumentCaptor<UserOAuthIdentity> captor = ArgumentCaptor.forClass(UserOAuthIdentity.class);
        verify(userOAuthIdentityRepository).save(captor.capture());

        UserOAuthIdentity savedOAuth = captor.getValue();
        Map<String, Object> tokenMeta = savedOAuth.getTokenMeta();
        assertEquals("", tokenMeta.get("display_name"));
        assertEquals("", tokenMeta.get("country"));
        assertEquals("", tokenMeta.get("product"));
    }

    // ==================== Tests pour calculateExpirationTime() ====================

    @Test
    @DisplayName("Doit calculer le temps d'expiration correctement avec expiresIn valide")
    void shouldCalculateExpirationTimeWithValidExpiresIn() {
        // Test via authenticate qui utilise calculateExpirationTime
        Map<String, Object> tokenResponse = createTokenResponseMap();
        Map<String, Object> userProfileResponse = createUserProfileResponseMap();

        when(restTemplate.exchange(
            eq("https://accounts.spotify.com/api/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

        when(restTemplate.exchange(
            eq("https://api.spotify.com/v1/me"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(userProfileResponse, HttpStatus.OK));

        User user = createTestUser();
        AuthResponse authResponse = new AuthResponse();
        UserResponse userResponse = createTestUserResponse(user);
        authResponse.setUser(userResponse);

        when(authService.oauthLogin(anyString(), anyString(), any())).thenReturn(authResponse);
        when(userRepository.findById(any())).thenReturn(Optional.of(user));
        when(tokenEncryptionService.encryptToken(anyString())).thenReturn("encrypted");
        when(userOAuthIdentityRepository.findByUserAndProvider(any(), anyString()))
            .thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode(AUTHORIZATION_CODE);

        // When
        oauthSpotifyService.authenticate(request, httpServletResponse);

        // Then
        ArgumentCaptor<UserOAuthIdentity> captor = ArgumentCaptor.forClass(UserOAuthIdentity.class);
        verify(userOAuthIdentityRepository).save(captor.capture());

        UserOAuthIdentity savedOAuth = captor.getValue();
        assertNotNull(savedOAuth.getExpiresAt());
        assertTrue(savedOAuth.getExpiresAt().isAfter(LocalDateTime.now()));
    }

    @Test
    @DisplayName("Doit utiliser 7 jours par défaut quand expiresIn est null")
    void shouldUseDefaultExpiryWhenExpiresInIsNull() {
        // Given
        Map<String, Object> tokenResponse = new HashMap<>();
        tokenResponse.put("access_token", ACCESS_TOKEN);
        tokenResponse.put("refresh_token", REFRESH_TOKEN);
        // Pas d'expires_in

        when(restTemplate.exchange(
            eq("https://accounts.spotify.com/api/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

        Map<String, Object> userProfileResponse = createUserProfileResponseMap();
        when(restTemplate.exchange(
            eq("https://api.spotify.com/v1/me"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(userProfileResponse, HttpStatus.OK));

        User user = createTestUser();
        AuthResponse authResponse = new AuthResponse();
        UserResponse userResponse = createTestUserResponse(user);
        authResponse.setUser(userResponse);

        when(authService.oauthLogin(anyString(), anyString(), any())).thenReturn(authResponse);
        when(userRepository.findById(any())).thenReturn(Optional.of(user));
        when(tokenEncryptionService.encryptToken(anyString())).thenReturn("encrypted");
        when(userOAuthIdentityRepository.findByUserAndProvider(any(), anyString()))
            .thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode(AUTHORIZATION_CODE);

        // When
        oauthSpotifyService.authenticate(request, httpServletResponse);

        // Then
        ArgumentCaptor<UserOAuthIdentity> captor = ArgumentCaptor.forClass(UserOAuthIdentity.class);
        verify(userOAuthIdentityRepository).save(captor.capture());

        UserOAuthIdentity savedOAuth = captor.getValue();
        assertNotNull(savedOAuth.getExpiresAt());

        // Devrait être environ 7 jours dans le futur
        LocalDateTime expectedExpiry = LocalDateTime.now().plusDays(7);
        assertTrue(savedOAuth.getExpiresAt().isAfter(LocalDateTime.now().plusDays(6)));
        assertTrue(savedOAuth.getExpiresAt().isBefore(expectedExpiry.plusMinutes(1)));
    }

    @Test
    @DisplayName("Doit utiliser 7 jours par défaut quand expiresIn est <= 0")
    void shouldUseDefaultExpiryWhenExpiresInIsZeroOrNegative() {
        // Given
        Map<String, Object> tokenResponse = new HashMap<>();
        tokenResponse.put("access_token", ACCESS_TOKEN);
        tokenResponse.put("refresh_token", REFRESH_TOKEN);
        tokenResponse.put("expires_in", 0); // Valeur invalide

        when(restTemplate.exchange(
            eq("https://accounts.spotify.com/api/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

        Map<String, Object> userProfileResponse = createUserProfileResponseMap();
        when(restTemplate.exchange(
            eq("https://api.spotify.com/v1/me"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(userProfileResponse, HttpStatus.OK));

        User user = createTestUser();
        AuthResponse authResponse = new AuthResponse();
        UserResponse userResponse = createTestUserResponse(user);
        authResponse.setUser(userResponse);

        when(authService.oauthLogin(anyString(), anyString(), any())).thenReturn(authResponse);
        when(userRepository.findById(any())).thenReturn(Optional.of(user));
        when(tokenEncryptionService.encryptToken(anyString())).thenReturn("encrypted");
        when(userOAuthIdentityRepository.findByUserAndProvider(any(), anyString()))
            .thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode(AUTHORIZATION_CODE);

        // When
        oauthSpotifyService.authenticate(request, httpServletResponse);

        // Then
        ArgumentCaptor<UserOAuthIdentity> captor = ArgumentCaptor.forClass(UserOAuthIdentity.class);
        verify(userOAuthIdentityRepository).save(captor.capture());

        UserOAuthIdentity savedOAuth = captor.getValue();
        assertTrue(savedOAuth.getExpiresAt().isAfter(LocalDateTime.now().plusDays(6)));
    }

    // ==================== Tests pour initMetrics() ====================

    @Test
    @DisplayName("Doit initialiser toutes les métriques au démarrage")
    void shouldInitializeAllMetrics() {
        // Les métriques sont initialisées dans le @PostConstruct
        // Vérifier qu'elles existent
        assertNotNull(meterRegistry.find("oauth.spotify.login.success").counter());
        assertNotNull(meterRegistry.find("oauth.spotify.login.failure").counter());
        assertNotNull(meterRegistry.find("oauth.spotify.authenticate.calls").counter());
        assertNotNull(meterRegistry.find("oauth.spotify.token_exchange.calls").counter());
        assertNotNull(meterRegistry.find("oauth.spotify.token_exchange.failures").counter());
    }

    @Test
    @DisplayName("Doit incrémenter les métriques authenticate.calls")
    void shouldIncrementAuthenticateCallsMetric() {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode(AUTHORIZATION_CODE);

        Map<String, Object> tokenResponse = createTokenResponseMap();
        Map<String, Object> userProfileResponse = createUserProfileResponseMap();

        when(restTemplate.exchange(anyString(), any(), any(), any(ParameterizedTypeReference.class)))
            .thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK))
            .thenReturn(new ResponseEntity<>(userProfileResponse, HttpStatus.OK));

        User user = createTestUser();
        AuthResponse authResponse = new AuthResponse();
        UserResponse userResponse = createTestUserResponse(user);
        authResponse.setUser(userResponse);

        when(authService.oauthLogin(anyString(), anyString(), any())).thenReturn(authResponse);
        when(userRepository.findById(any())).thenReturn(Optional.of(user));
        when(tokenEncryptionService.encryptToken(anyString())).thenReturn("encrypted");
        when(userOAuthIdentityRepository.findByUserAndProvider(any(), anyString()))
            .thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Counter beforeCounter = meterRegistry.find("oauth.spotify.authenticate.calls").counter();
        double beforeCount = beforeCounter != null ? beforeCounter.count() : 0;

        // When
        oauthSpotifyService.authenticate(request, httpServletResponse);

        // Then
        Counter afterCounter = meterRegistry.find("oauth.spotify.authenticate.calls").counter();
        assertNotNull(afterCounter);
        assertEquals(beforeCount + 1, afterCounter.count());
    }

    // ==================== Tests pour le constructeur ====================

    @Test
    @DisplayName("Doit créer une instance avec les bonnes propriétés")
    void shouldCreateInstanceWithCorrectProperties() {
        // Le service est déjà créé dans setUp()
        assertNotNull(oauthSpotifyService);

        // Vérifier que les métriques sont configurées
        assertNotNull(meterRegistry.find("oauth.spotify.login.success").counter());
        assertNotNull(meterRegistry.find("oauth.spotify.login.failure").counter());
        assertNotNull(meterRegistry.find("oauth.spotify.authenticate.calls").counter());
        assertNotNull(meterRegistry.find("oauth.spotify.token_exchange.calls").counter());
        assertNotNull(meterRegistry.find("oauth.spotify.token_exchange.failures").counter());
    }

    // ==================== Méthodes utilitaires ====================

    private User createTestUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(USER_EMAIL);
        user.setUsername("testuser");
        user.setCreatedAt(LocalDateTime.now());
        return user;
    }

    private UserResponse createTestUserResponse(User user) {
        UserResponse userResponse = new UserResponse();
        userResponse.setId(user.getId());
        userResponse.setEmail(user.getEmail());
        userResponse.setUsername(user.getUsername());
        userResponse.setCreatedAt(user.getCreatedAt());
        return userResponse;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> createTokenResponseMap() {
        Map<String, Object> response = new HashMap<>();
        response.put("access_token", ACCESS_TOKEN);
        response.put("refresh_token", REFRESH_TOKEN);
        response.put("expires_in", EXPIRES_IN);
        response.put("token_type", "Bearer");
        response.put("scope", "user-read-email user-read-private");
        return response;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> createUserProfileResponseMap() {
        Map<String, Object> response = new HashMap<>();
        response.put("id", USER_ID);
        response.put("email", USER_EMAIL);
        response.put("display_name", DISPLAY_NAME);
        response.put("country", COUNTRY);
        response.put("product", PRODUCT);

        List<Map<String, Object>> images = new ArrayList<>();
        Map<String, Object> image = new HashMap<>();
        image.put("url", AVATAR_URL);
        image.put("height", 300);
        image.put("width", 300);
        images.add(image);
        response.put("images", images);

        return response;
    }
}
