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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
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
 * Tests unitaires pour OAuthNotionService
 * Type: Tests Unitaires
 * Description: Teste l'authentification OAuth Notion et la liaison de comptes
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OAuthNotionService - Tests Unitaires")
class OAuthNotionServiceTest {

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
    private OAuthNotionService oauthNotionService;

    private static final String CLIENT_ID = "test-client-id";
    private static final String CLIENT_SECRET = "test-client-secret";
    private static final String REDIRECT_BASE_URL = "http://localhost:3000";
    private static final String AUTHORIZATION_CODE = "test-auth-code";
    private static final String ACCESS_TOKEN = "test-access-token";
    private static final String ENCRYPTED_ACCESS_TOKEN = "encrypted-access-token";
    private static final String USER_EMAIL = "test@notion.com";
    private static final String USER_ID = "notion-user-123";
    private static final String USER_NAME = "Test User";
    private static final String AVATAR_URL = "https://avatar.url/image.png";
    private static final String WORKSPACE_NAME = "Test Workspace";
    private static final String WORKSPACE_ID = "workspace-123";
    private static final String WORKSPACE_ICON = "https://workspace.icon/icon.png";
    private static final String BOT_ID = "bot-123";
    private static final String DUPLICATED_TEMPLATE_ID = "template-123";

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        oauthNotionService = new OAuthNotionService(
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
            java.lang.reflect.Method initMetricsMethod = OAuthNotionService.class.getDeclaredMethod("initMetrics");
            initMetricsMethod.setAccessible(true);
            initMetricsMethod.invoke(oauthNotionService);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize metrics", e);
        }
    }

    // ==================== Tests pour le constructeur ====================

    @Test
    @DisplayName("Doit créer une instance avec les bonnes propriétés")
    void shouldCreateInstanceWithCorrectProperties() {
        // Le service est déjà créé dans setUp()
        assertNotNull(oauthNotionService);

        // Vérifier que les métriques sont configurées
        assertNotNull(meterRegistry.find("oauth.notion.login.success").counter());
        assertNotNull(meterRegistry.find("oauth.notion.login.failure").counter());
        assertNotNull(meterRegistry.find("oauth.notion.authenticate.calls").counter());
        assertNotNull(meterRegistry.find("oauth.notion.token_exchange.calls").counter());
        assertNotNull(meterRegistry.find("oauth.notion.token_exchange.failures").counter());
    }

    // ==================== Tests pour initMetrics() ====================

    @Test
    @DisplayName("Doit initialiser toutes les métriques au démarrage")
    void shouldInitializeAllMetrics() {
        // Les métriques sont initialisées dans le @PostConstruct
        // Vérifier qu'elles existent
        assertNotNull(meterRegistry.find("oauth.notion.login.success").counter());
        assertNotNull(meterRegistry.find("oauth.notion.login.failure").counter());
        assertNotNull(meterRegistry.find("oauth.notion.authenticate.calls").counter());
        assertNotNull(meterRegistry.find("oauth.notion.token_exchange.calls").counter());
        assertNotNull(meterRegistry.find("oauth.notion.token_exchange.failures").counter());
    }

    // ==================== Tests pour authenticate() ====================

    @Test
    @DisplayName("Doit authentifier un utilisateur avec succès via Notion OAuth")
    void shouldAuthenticateUserSuccessfully() {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode(AUTHORIZATION_CODE);

        Map<String, Object> tokenResponse = createTokenResponseMap();

        when(restTemplate.exchange(
            eq("https://api.notion.com/v1/oauth/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

        User user = createTestUser();
        UserResponse userResponse = createTestUserResponse(user);
        AuthResponse authResponse = new AuthResponse();
        authResponse.setUser(userResponse);

        when(authService.oauthLogin(eq(USER_EMAIL), eq(AVATAR_URL), eq(httpServletResponse)))
            .thenReturn(authResponse);
        when(userRepository.findById(userResponse.getId())).thenReturn(Optional.of(user));
        when(tokenEncryptionService.encryptToken(ACCESS_TOKEN)).thenReturn(ENCRYPTED_ACCESS_TOKEN);
        when(userOAuthIdentityRepository.findByUserAndProvider(user, "notion"))
            .thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.save(any(UserOAuthIdentity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        AuthResponse result = oauthNotionService.authenticate(request, httpServletResponse);

        // Then
        assertNotNull(result);
        assertEquals(userResponse, result.getUser());
        verify(authService).oauthLogin(USER_EMAIL, AVATAR_URL, httpServletResponse);
        verify(userOAuthIdentityRepository).save(any(UserOAuthIdentity.class));

        // Vérifier les métriques
        Counter successCounter = meterRegistry.find("oauth.notion.login.success").counter();
        assertNotNull(successCounter);
        assertEquals(1.0, successCounter.count());
    }

    @Test
    @DisplayName("Doit échouer si l'email Notion est manquant")
    void shouldFailWhenNotionEmailIsMissing() {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode(AUTHORIZATION_CODE);

        Map<String, Object> tokenResponse = createTokenResponseMapWithoutEmail();

        when(restTemplate.exchange(
            eq("https://api.notion.com/v1/oauth/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

        Counter failureCounterBefore = meterRegistry.find("oauth.notion.login.failure").counter();
        double beforeCount = failureCounterBefore != null ? failureCounterBefore.count() : 0;

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            oauthNotionService.authenticate(request, httpServletResponse)
        );

        assertTrue(exception.getMessage().contains("email is required for authentication"));

        // Vérifier que les métriques d'échec ont été incrémentées
        Counter failureCounter = meterRegistry.find("oauth.notion.login.failure").counter();
        assertNotNull(failureCounter);
        assertTrue(failureCounter.count() > beforeCount);
    }

    @Test
    @DisplayName("Doit échouer si l'email Notion est vide")
    void shouldFailWhenNotionEmailIsBlank() {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode(AUTHORIZATION_CODE);

        Map<String, Object> tokenResponse = createTokenResponseMapWithBlankEmail();

        when(restTemplate.exchange(
            eq("https://api.notion.com/v1/oauth/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            oauthNotionService.authenticate(request, httpServletResponse)
        );

        assertTrue(exception.getMessage().contains("email is required for authentication"));

        // Vérifier que les métriques d'échec ont été incrémentées
        Counter failureCounter = meterRegistry.find("oauth.notion.login.failure").counter();
        assertNotNull(failureCounter);
        assertTrue(failureCounter.count() >= 1.0);
    }

    @Test
    @DisplayName("Doit échouer quand l'échange de token échoue")
    void shouldFailWhenTokenExchangeFails() {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode(AUTHORIZATION_CODE);

        when(restTemplate.exchange(
            eq("https://api.notion.com/v1/oauth/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenThrow(new RestClientException("Token exchange failed"));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            oauthNotionService.authenticate(request, httpServletResponse)
        );

        assertTrue(exception.getMessage().contains("OAuth authentication failed"));

        // Vérifier les métriques
        Counter failureCounter = meterRegistry.find("oauth.notion.login.failure").counter();
        assertNotNull(failureCounter);
        assertEquals(1.0, failureCounter.count());

        Counter tokenExchangeFailures = meterRegistry.find("oauth.notion.token_exchange.failures").counter();
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

        when(restTemplate.exchange(
            eq("https://api.notion.com/v1/oauth/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

        User user = createTestUser();
        UserOAuthIdentity existingOAuth = new UserOAuthIdentity();
        existingOAuth.setUser(user);
        existingOAuth.setProvider("notion");
        existingOAuth.setProviderUserId("old-user-id");

        AuthResponse authResponse = new AuthResponse();
        UserResponse userResponse = createTestUserResponse(user);
        authResponse.setUser(userResponse);

        when(authService.oauthLogin(eq(USER_EMAIL), eq(AVATAR_URL), eq(httpServletResponse)))
            .thenReturn(authResponse);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(tokenEncryptionService.encryptToken(ACCESS_TOKEN)).thenReturn(ENCRYPTED_ACCESS_TOKEN);
        when(userOAuthIdentityRepository.findByUserAndProvider(user, "notion"))
            .thenReturn(Optional.of(existingOAuth));
        when(userOAuthIdentityRepository.save(any(UserOAuthIdentity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        AuthResponse result = oauthNotionService.authenticate(request, httpServletResponse);

        // Then
        assertNotNull(result);
        ArgumentCaptor<UserOAuthIdentity> captor = ArgumentCaptor.forClass(UserOAuthIdentity.class);
        verify(userOAuthIdentityRepository).save(captor.capture());

        UserOAuthIdentity savedOAuth = captor.getValue();
        assertEquals(USER_ID, savedOAuth.getProviderUserId());
        assertEquals(ENCRYPTED_ACCESS_TOKEN, savedOAuth.getAccessTokenEnc());
    }

    @Test
    @DisplayName("Doit incrémenter les métriques authenticate.calls")
    void shouldIncrementAuthenticateCallsMetric() {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode(AUTHORIZATION_CODE);

        Map<String, Object> tokenResponse = createTokenResponseMap();

        when(restTemplate.exchange(anyString(), any(), any(), any(ParameterizedTypeReference.class)))
            .thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

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

        Counter beforeCounter = meterRegistry.find("oauth.notion.authenticate.calls").counter();
        double beforeCount = beforeCounter != null ? beforeCounter.count() : 0;

        // When
        oauthNotionService.authenticate(request, httpServletResponse);

        // Then
        Counter afterCounter = meterRegistry.find("oauth.notion.authenticate.calls").counter();
        assertNotNull(afterCounter);
        assertEquals(beforeCount + 1, afterCounter.count());
    }

    @Test
    @DisplayName("Doit gérer l'exception quand l'utilisateur n'est pas trouvé après login")
    void shouldHandleUserNotFoundAfterLogin() {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode(AUTHORIZATION_CODE);

        Map<String, Object> tokenResponse = createTokenResponseMap();

        when(restTemplate.exchange(
            eq("https://api.notion.com/v1/oauth/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

        User user = createTestUser();
        UserResponse userResponse = createTestUserResponse(user);
        AuthResponse authResponse = new AuthResponse();
        authResponse.setUser(userResponse);

        when(authService.oauthLogin(eq(USER_EMAIL), eq(AVATAR_URL), eq(httpServletResponse)))
            .thenReturn(authResponse);
        when(userRepository.findById(userResponse.getId())).thenReturn(Optional.empty());

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            oauthNotionService.authenticate(request, httpServletResponse)
        );

        assertTrue(exception.getMessage().contains("User not found after login"));
        
        // Vérifier les métriques d'échec
        Counter failureCounter = meterRegistry.find("oauth.notion.login.failure").counter();
        assertNotNull(failureCounter);
        assertEquals(1.0, failureCounter.count());
    }

    // ==================== Tests pour exchangeCodeForToken() ====================

    @Test
    @DisplayName("Doit échanger le code pour un token avec succès")
    void shouldExchangeCodeForTokenSuccessfully() {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode(AUTHORIZATION_CODE);

        Map<String, Object> tokenResponse = createTokenResponseMap();

        when(restTemplate.exchange(
            eq("https://api.notion.com/v1/oauth/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

        User user = createTestUser();
        UserResponse userResponse = createTestUserResponse(user);
        AuthResponse authResponse = new AuthResponse();
        authResponse.setUser(userResponse);

        when(authService.oauthLogin(anyString(), anyString(), any())).thenReturn(authResponse);
        when(userRepository.findById(any())).thenReturn(Optional.of(user));
        when(tokenEncryptionService.encryptToken(anyString())).thenReturn("encrypted");
        when(userOAuthIdentityRepository.findByUserAndProvider(any(), anyString()))
            .thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        oauthNotionService.authenticate(request, httpServletResponse);

        // Then
        Counter tokenExchangeCalls = meterRegistry.find("oauth.notion.token_exchange.calls").counter();
        assertNotNull(tokenExchangeCalls);
        assertEquals(1.0, tokenExchangeCalls.count());
    }

    @Test
    @DisplayName("Doit échouer quand le token response n'a pas d'access_token")
    void shouldFailWhenTokenResponseHasNoAccessToken() {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode(AUTHORIZATION_CODE);

        Map<String, Object> tokenResponse = new HashMap<>();
        tokenResponse.put("workspace_name", WORKSPACE_NAME);
        // Pas d'access_token

        when(restTemplate.exchange(
            eq("https://api.notion.com/v1/oauth/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

        Counter tokenExchangeFailuresBefore = meterRegistry.find("oauth.notion.token_exchange.failures").counter();
        double beforeCount = tokenExchangeFailuresBefore != null ? tokenExchangeFailuresBefore.count() : 0;

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            oauthNotionService.authenticate(request, httpServletResponse)
        );

        assertTrue(exception.getMessage().contains("No access_token in Notion response") ||
                   exception.getMessage().contains("OAuth authentication failed"));

        Counter tokenExchangeFailures = meterRegistry.find("oauth.notion.token_exchange.failures").counter();
        assertNotNull(tokenExchangeFailures);
        assertTrue(tokenExchangeFailures.count() > beforeCount);
    }

    @Test
    @DisplayName("Doit échouer quand le status n'est pas 2xx")
    void shouldFailWhenTokenResponseStatusIsNot2xx() {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode(AUTHORIZATION_CODE);

        when(restTemplate.exchange(
            eq("https://api.notion.com/v1/oauth/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(null, HttpStatus.BAD_REQUEST));

        Counter tokenExchangeFailuresBefore = meterRegistry.find("oauth.notion.token_exchange.failures").counter();
        double beforeCount = tokenExchangeFailuresBefore != null ? tokenExchangeFailuresBefore.count() : 0;

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            oauthNotionService.authenticate(request, httpServletResponse)
        );

        assertTrue(exception.getMessage().contains("Failed to exchange token with Notion") ||
                   exception.getMessage().contains("OAuth authentication failed"));

        Counter tokenExchangeFailures = meterRegistry.find("oauth.notion.token_exchange.failures").counter();
        assertNotNull(tokenExchangeFailures);
        assertTrue(tokenExchangeFailures.count() > beforeCount);
    }

    @Test
    @DisplayName("Doit gérer les champs optionnels du token response")
    void shouldHandleOptionalFieldsInTokenResponse() {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode(AUTHORIZATION_CODE);

        Map<String, Object> tokenResponse = createMinimalTokenResponseMap();

        when(restTemplate.exchange(
            eq("https://api.notion.com/v1/oauth/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

        User user = createTestUser();
        UserResponse userResponse = createTestUserResponse(user);
        AuthResponse authResponse = new AuthResponse();
        authResponse.setUser(userResponse);

        when(authService.oauthLogin(anyString(), nullable(String.class), any())).thenReturn(authResponse);
        when(userRepository.findById(any())).thenReturn(Optional.of(user));
        when(tokenEncryptionService.encryptToken(anyString())).thenReturn("encrypted");
        when(userOAuthIdentityRepository.findByUserAndProvider(any(), anyString()))
            .thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        AuthResponse result = oauthNotionService.authenticate(request, httpServletResponse);

        // Then
        assertNotNull(result);
        verify(userOAuthIdentityRepository).save(any(UserOAuthIdentity.class));
    }

    @Test
    @DisplayName("Doit inclure duplicated_template_id quand présent")
    void shouldIncludeDuplicatedTemplateIdWhenPresent() {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode(AUTHORIZATION_CODE);

        Map<String, Object> tokenResponse = createTokenResponseMap();

        when(restTemplate.exchange(
            eq("https://api.notion.com/v1/oauth/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

        User user = createTestUser();
        UserResponse userResponse = createTestUserResponse(user);
        AuthResponse authResponse = new AuthResponse();
        authResponse.setUser(userResponse);

        when(authService.oauthLogin(anyString(), anyString(), any())).thenReturn(authResponse);
        when(userRepository.findById(any())).thenReturn(Optional.of(user));
        when(tokenEncryptionService.encryptToken(anyString())).thenReturn("encrypted");
        when(userOAuthIdentityRepository.findByUserAndProvider(any(), anyString()))
            .thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        oauthNotionService.authenticate(request, httpServletResponse);

        // Then
        ArgumentCaptor<UserOAuthIdentity> captor = ArgumentCaptor.forClass(UserOAuthIdentity.class);
        verify(userOAuthIdentityRepository).save(captor.capture());

        UserOAuthIdentity savedOAuth = captor.getValue();
        Map<String, Object> tokenMeta = savedOAuth.getTokenMeta();
        assertEquals(DUPLICATED_TEMPLATE_ID, tokenMeta.get("duplicated_template_id"));
    }

    // ==================== Tests pour extractOwnerProfile() ====================

    @Test
    @DisplayName("Doit extraire le profil utilisateur correctement")
    void shouldExtractOwnerProfileSuccessfully() {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode(AUTHORIZATION_CODE);

        Map<String, Object> tokenResponse = createTokenResponseMap();

        when(restTemplate.exchange(
            eq("https://api.notion.com/v1/oauth/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

        User user = createTestUser();
        UserResponse userResponse = createTestUserResponse(user);
        AuthResponse authResponse = new AuthResponse();
        authResponse.setUser(userResponse);

        when(authService.oauthLogin(anyString(), anyString(), any())).thenReturn(authResponse);
        when(userRepository.findById(any())).thenReturn(Optional.of(user));
        when(tokenEncryptionService.encryptToken(anyString())).thenReturn("encrypted");
        when(userOAuthIdentityRepository.findByUserAndProvider(any(), anyString()))
            .thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        oauthNotionService.authenticate(request, httpServletResponse);

        // Then
        verify(authService).oauthLogin(eq(USER_EMAIL), eq(AVATAR_URL), any());
    }

    @Test
    @DisplayName("Doit utiliser bot_id comme fallback si user_id n'est pas disponible")
    void shouldUseBotIdAsFallbackWhenUserIdNotAvailable() {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode(AUTHORIZATION_CODE);

        Map<String, Object> tokenResponse = createTokenResponseMapWithBotIdFallback();

        when(restTemplate.exchange(
            eq("https://api.notion.com/v1/oauth/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

        User user = createTestUser();
        UserResponse userResponse = createTestUserResponse(user);
        AuthResponse authResponse = new AuthResponse();
        authResponse.setUser(userResponse);

        when(authService.oauthLogin(anyString(), nullable(String.class), any())).thenReturn(authResponse);
        when(userRepository.findById(any())).thenReturn(Optional.of(user));
        when(tokenEncryptionService.encryptToken(anyString())).thenReturn("encrypted");
        when(userOAuthIdentityRepository.findByUserAndProvider(any(), anyString()))
            .thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        oauthNotionService.authenticate(request, httpServletResponse);

        // Then
        ArgumentCaptor<UserOAuthIdentity> captor = ArgumentCaptor.forClass(UserOAuthIdentity.class);
        verify(userOAuthIdentityRepository).save(captor.capture());

        UserOAuthIdentity savedOAuth = captor.getValue();
        assertEquals(BOT_ID, savedOAuth.getProviderUserId());
    }

    @Test
    @DisplayName("Doit échouer si ni user_id ni bot_id ne sont disponibles")
    void shouldFailWhenNeitherUserIdNorBotIdAvailable() {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode(AUTHORIZATION_CODE);

        Map<String, Object> tokenResponse = createTokenResponseMapWithoutUserIdentifier();

        when(restTemplate.exchange(
            eq("https://api.notion.com/v1/oauth/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            oauthNotionService.authenticate(request, httpServletResponse)
        );

        assertTrue(exception.getMessage().contains("Could not extract user identifier") ||
                   exception.getMessage().contains("OAuth authentication failed"));
    }

    @Test
    @DisplayName("Doit utiliser un nom par défaut quand le nom est manquant")
    void shouldUseDefaultNameWhenNameIsMissing() {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode(AUTHORIZATION_CODE);

        Map<String, Object> tokenResponse = createTokenResponseMapWithoutName();

        when(restTemplate.exchange(
            eq("https://api.notion.com/v1/oauth/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

        User user = createTestUser();
        UserResponse userResponse = createTestUserResponse(user);
        AuthResponse authResponse = new AuthResponse();
        authResponse.setUser(userResponse);

        when(authService.oauthLogin(anyString(), nullable(String.class), any())).thenReturn(authResponse);
        when(userRepository.findById(any())).thenReturn(Optional.of(user));
        when(tokenEncryptionService.encryptToken(anyString())).thenReturn("encrypted");
        when(userOAuthIdentityRepository.findByUserAndProvider(any(), anyString()))
            .thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        oauthNotionService.authenticate(request, httpServletResponse);

        // Then
        ArgumentCaptor<UserOAuthIdentity> captor = ArgumentCaptor.forClass(UserOAuthIdentity.class);
        verify(userOAuthIdentityRepository).save(captor.capture());

        UserOAuthIdentity savedOAuth = captor.getValue();
        Map<String, Object> tokenMeta = savedOAuth.getTokenMeta();
        assertEquals("Notion User", tokenMeta.get("name"));
    }

    @Test
    @DisplayName("Doit gérer l'absence d'avatar_url")
    void shouldHandleMissingAvatarUrl() {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode(AUTHORIZATION_CODE);

        Map<String, Object> tokenResponse = createTokenResponseMapWithoutAvatar();

        when(restTemplate.exchange(
            eq("https://api.notion.com/v1/oauth/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

        User user = createTestUser();
        UserResponse userResponse = createTestUserResponse(user);
        AuthResponse authResponse = new AuthResponse();
        authResponse.setUser(userResponse);

        when(authService.oauthLogin(eq(USER_EMAIL), isNull(), any())).thenReturn(authResponse);
        when(userRepository.findById(any())).thenReturn(Optional.of(user));
        when(tokenEncryptionService.encryptToken(anyString())).thenReturn("encrypted");
        when(userOAuthIdentityRepository.findByUserAndProvider(any(), anyString()))
            .thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        oauthNotionService.authenticate(request, httpServletResponse);

        // Then
        verify(authService).oauthLogin(eq(USER_EMAIL), isNull(), any());
    }

    // ==================== Tests pour linkToExistingUser() ====================

    @Test
    @DisplayName("Doit lier un compte Notion à un utilisateur existant avec succès")
    void shouldLinkNotionAccountToExistingUserSuccessfully() {
        // Given
        User existingUser = createTestUser();
        Map<String, Object> tokenResponse = createTokenResponseMap();

        when(restTemplate.exchange(
            eq("https://api.notion.com/v1/oauth/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

        when(tokenEncryptionService.encryptToken(ACCESS_TOKEN)).thenReturn(ENCRYPTED_ACCESS_TOKEN);
        when(userOAuthIdentityRepository.findByProviderAndProviderUserId("notion", USER_ID))
            .thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.findByUserAndProvider(existingUser, "notion"))
            .thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.save(any(UserOAuthIdentity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        UserOAuthIdentity result = oauthNotionService.linkToExistingUser(existingUser, AUTHORIZATION_CODE);

        // Then
        assertNotNull(result);
        assertEquals(existingUser, result.getUser());
        assertEquals("notion", result.getProvider());
        assertEquals(USER_ID, result.getProviderUserId());
        verify(userOAuthIdentityRepository).save(any(UserOAuthIdentity.class));
    }

    @Test
    @DisplayName("Doit mettre à jour l'identité OAuth existante lors du lien")
    void shouldUpdateExistingOAuthIdentityWhenLinking() {
        // Given
        User existingUser = createTestUser();
        UserOAuthIdentity existingOAuth = new UserOAuthIdentity();
        existingOAuth.setUser(existingUser);
        existingOAuth.setProvider("notion");
        existingOAuth.setProviderUserId("old-id");

        Map<String, Object> tokenResponse = createTokenResponseMap();

        when(restTemplate.exchange(
            eq("https://api.notion.com/v1/oauth/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

        when(tokenEncryptionService.encryptToken(ACCESS_TOKEN)).thenReturn(ENCRYPTED_ACCESS_TOKEN);
        when(userOAuthIdentityRepository.findByProviderAndProviderUserId("notion", USER_ID))
            .thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.findByUserAndProvider(existingUser, "notion"))
            .thenReturn(Optional.of(existingOAuth));
        when(userOAuthIdentityRepository.save(any(UserOAuthIdentity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        UserOAuthIdentity result = oauthNotionService.linkToExistingUser(existingUser, AUTHORIZATION_CODE);

        // Then
        assertNotNull(result);
        assertEquals(USER_ID, result.getProviderUserId());
        assertEquals(ENCRYPTED_ACCESS_TOKEN, result.getAccessTokenEnc());
    }

    @Test
    @DisplayName("Doit échouer si le compte Notion est déjà lié à un autre utilisateur")
    void shouldFailWhenNotionAccountAlreadyLinkedToAnotherUser() {
        // Given
        User existingUser = createTestUser();
        User otherUser = createTestUser();
        otherUser.setId(UUID.randomUUID());

        UserOAuthIdentity existingOAuth = new UserOAuthIdentity();
        existingOAuth.setUser(otherUser);
        existingOAuth.setProvider("notion");
        existingOAuth.setProviderUserId(USER_ID);

        Map<String, Object> tokenResponse = createTokenResponseMap();

        when(restTemplate.exchange(
            eq("https://api.notion.com/v1/oauth/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

        when(userOAuthIdentityRepository.findByProviderAndProviderUserId("notion", USER_ID))
            .thenReturn(Optional.of(existingOAuth));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            oauthNotionService.linkToExistingUser(existingUser, AUTHORIZATION_CODE)
        );

        assertTrue(exception.getMessage().contains("already linked to another user"));
    }

    @Test
    @DisplayName("Doit échouer si l'email est manquant lors du lien")
    void shouldFailWhenEmailMissingDuringLink() {
        // Given
        User existingUser = createTestUser();
        Map<String, Object> tokenResponse = createTokenResponseMapWithoutEmail();

        when(restTemplate.exchange(
            eq("https://api.notion.com/v1/oauth/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            oauthNotionService.linkToExistingUser(existingUser, AUTHORIZATION_CODE)
        );

        assertTrue(exception.getMessage().contains("email is required for account linking"));
    }

    @Test
    @DisplayName("Doit gérer les erreurs de sauvegarde lors du lien")
    void shouldHandleSaveErrorDuringLink() {
        // Given
        User existingUser = createTestUser();
        Map<String, Object> tokenResponse = createTokenResponseMap();

        when(restTemplate.exchange(
            eq("https://api.notion.com/v1/oauth/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

        when(tokenEncryptionService.encryptToken(ACCESS_TOKEN)).thenReturn(ENCRYPTED_ACCESS_TOKEN);
        when(userOAuthIdentityRepository.findByProviderAndProviderUserId("notion", USER_ID))
            .thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.findByUserAndProvider(existingUser, "notion"))
            .thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.save(any(UserOAuthIdentity.class)))
            .thenThrow(new RuntimeException("Database error"));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            oauthNotionService.linkToExistingUser(existingUser, AUTHORIZATION_CODE)
        );

        assertTrue(exception.getMessage().contains("Failed to link Notion account"));
    }

    // ==================== Tests pour buildTokenMeta() ====================

    @Test
    @DisplayName("Doit construire token meta avec tous les champs")
    void shouldBuildTokenMetaWithAllFields() {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode(AUTHORIZATION_CODE);

        Map<String, Object> tokenResponse = createTokenResponseMap();

        when(restTemplate.exchange(
            eq("https://api.notion.com/v1/oauth/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

        User user = createTestUser();
        UserResponse userResponse = createTestUserResponse(user);
        AuthResponse authResponse = new AuthResponse();
        authResponse.setUser(userResponse);

        when(authService.oauthLogin(anyString(), anyString(), any())).thenReturn(authResponse);
        when(userRepository.findById(any())).thenReturn(Optional.of(user));
        when(tokenEncryptionService.encryptToken(anyString())).thenReturn("encrypted");
        when(userOAuthIdentityRepository.findByUserAndProvider(any(), anyString()))
            .thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        oauthNotionService.authenticate(request, httpServletResponse);

        // Then
        ArgumentCaptor<UserOAuthIdentity> captor = ArgumentCaptor.forClass(UserOAuthIdentity.class);
        verify(userOAuthIdentityRepository).save(captor.capture());

        UserOAuthIdentity savedOAuth = captor.getValue();
        Map<String, Object> tokenMeta = savedOAuth.getTokenMeta();

        assertEquals(USER_EMAIL, tokenMeta.get("email"));
        assertEquals(USER_NAME, tokenMeta.get("name"));
        assertEquals(AVATAR_URL, tokenMeta.get("avatar_url"));
        assertEquals(WORKSPACE_NAME, tokenMeta.get("workspace_name"));
        assertEquals(WORKSPACE_ID, tokenMeta.get("workspace_id"));
        assertEquals(WORKSPACE_ICON, tokenMeta.get("workspace_icon"));
        assertEquals(BOT_ID, tokenMeta.get("bot_id"));
        assertEquals(DUPLICATED_TEMPLATE_ID, tokenMeta.get("duplicated_template_id"));
    }

    @Test
    @DisplayName("Doit exclure duplicated_template_id s'il est null ou vide")
    void shouldExcludeDuplicatedTemplateIdWhenNullOrBlank() {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode(AUTHORIZATION_CODE);

        Map<String, Object> tokenResponse = createTokenResponseMapWithoutDuplicatedTemplateId();

        when(restTemplate.exchange(
            eq("https://api.notion.com/v1/oauth/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

        User user = createTestUser();
        UserResponse userResponse = createTestUserResponse(user);
        AuthResponse authResponse = new AuthResponse();
        authResponse.setUser(userResponse);

        when(authService.oauthLogin(anyString(), anyString(), any())).thenReturn(authResponse);
        when(userRepository.findById(any())).thenReturn(Optional.of(user));
        when(tokenEncryptionService.encryptToken(anyString())).thenReturn("encrypted");
        when(userOAuthIdentityRepository.findByUserAndProvider(any(), anyString()))
            .thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        oauthNotionService.authenticate(request, httpServletResponse);

        // Then
        ArgumentCaptor<UserOAuthIdentity> captor = ArgumentCaptor.forClass(UserOAuthIdentity.class);
        verify(userOAuthIdentityRepository).save(captor.capture());

        UserOAuthIdentity savedOAuth = captor.getValue();
        Map<String, Object> tokenMeta = savedOAuth.getTokenMeta();

        assertFalse(tokenMeta.containsKey("duplicated_template_id"));
    }

    // ==================== Tests pour handleUserAuthentication() ====================

    @Test
    @DisplayName("Doit créer une nouvelle identité OAuth si elle n'existe pas")
    void shouldCreateNewOAuthIdentityWhenNotExists() {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode(AUTHORIZATION_CODE);

        Map<String, Object> tokenResponse = createTokenResponseMap();

        when(restTemplate.exchange(
            eq("https://api.notion.com/v1/oauth/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

        User user = createTestUser();
        UserResponse userResponse = createTestUserResponse(user);
        AuthResponse authResponse = new AuthResponse();
        authResponse.setUser(userResponse);

        when(authService.oauthLogin(anyString(), anyString(), any())).thenReturn(authResponse);
        when(userRepository.findById(any())).thenReturn(Optional.of(user));
        when(tokenEncryptionService.encryptToken(anyString())).thenReturn("encrypted");
        when(userOAuthIdentityRepository.findByUserAndProvider(any(), anyString()))
            .thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        oauthNotionService.authenticate(request, httpServletResponse);

        // Then
        ArgumentCaptor<UserOAuthIdentity> captor = ArgumentCaptor.forClass(UserOAuthIdentity.class);
        verify(userOAuthIdentityRepository).save(captor.capture());

        UserOAuthIdentity savedOAuth = captor.getValue();
        assertNotNull(savedOAuth.getCreatedAt());
        assertNotNull(savedOAuth.getUpdatedAt());
    }

    // ==================== Tests pour createOAuthIdentity() ====================

    @Test
    @DisplayName("Doit créer une nouvelle identité OAuth avec tous les champs")
    void shouldCreateOAuthIdentityWithAllFields() {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode(AUTHORIZATION_CODE);

        Map<String, Object> tokenResponse = createTokenResponseMap();

        when(restTemplate.exchange(
            eq("https://api.notion.com/v1/oauth/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

        User user = createTestUser();
        UserResponse userResponse = createTestUserResponse(user);
        AuthResponse authResponse = new AuthResponse();
        authResponse.setUser(userResponse);

        when(authService.oauthLogin(anyString(), anyString(), any())).thenReturn(authResponse);
        when(userRepository.findById(any())).thenReturn(Optional.of(user));
        when(tokenEncryptionService.encryptToken(anyString())).thenReturn(ENCRYPTED_ACCESS_TOKEN);
        when(userOAuthIdentityRepository.findByUserAndProvider(any(), anyString()))
            .thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        oauthNotionService.authenticate(request, httpServletResponse);

        // Then
        ArgumentCaptor<UserOAuthIdentity> captor = ArgumentCaptor.forClass(UserOAuthIdentity.class);
        verify(userOAuthIdentityRepository).save(captor.capture());

        UserOAuthIdentity savedOAuth = captor.getValue();
        assertEquals(user, savedOAuth.getUser());
        assertEquals("notion", savedOAuth.getProvider());
        assertEquals(USER_ID, savedOAuth.getProviderUserId());
        assertEquals(ENCRYPTED_ACCESS_TOKEN, savedOAuth.getAccessTokenEnc());
        assertNotNull(savedOAuth.getCreatedAt());
        assertNotNull(savedOAuth.getUpdatedAt());
        assertNotNull(savedOAuth.getTokenMeta());
    }

    // ==================== Tests pour updateOAuthIdentity() ====================

    @Test
    @DisplayName("Doit mettre à jour l'identité OAuth existante")
    void shouldUpdateExistingOAuthIdentity() {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode(AUTHORIZATION_CODE);

        Map<String, Object> tokenResponse = createTokenResponseMap();

        when(restTemplate.exchange(
            eq("https://api.notion.com/v1/oauth/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

        User user = createTestUser();
        UserOAuthIdentity existingOAuth = new UserOAuthIdentity();
        existingOAuth.setUser(user);
        existingOAuth.setProvider("notion");
        existingOAuth.setProviderUserId("old-id");
        existingOAuth.setAccessTokenEnc("old-token");
        existingOAuth.setCreatedAt(LocalDateTime.now().minusDays(1));
        existingOAuth.setUpdatedAt(LocalDateTime.now().minusDays(1));

        UserResponse userResponse = createTestUserResponse(user);
        AuthResponse authResponse = new AuthResponse();
        authResponse.setUser(userResponse);

        when(authService.oauthLogin(anyString(), anyString(), any())).thenReturn(authResponse);
        when(userRepository.findById(any())).thenReturn(Optional.of(user));
        when(tokenEncryptionService.encryptToken(anyString())).thenReturn(ENCRYPTED_ACCESS_TOKEN);
        when(userOAuthIdentityRepository.findByUserAndProvider(any(), anyString()))
            .thenReturn(Optional.of(existingOAuth));
        when(userOAuthIdentityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        oauthNotionService.authenticate(request, httpServletResponse);

        // Then
        ArgumentCaptor<UserOAuthIdentity> captor = ArgumentCaptor.forClass(UserOAuthIdentity.class);
        verify(userOAuthIdentityRepository).save(captor.capture());

        UserOAuthIdentity savedOAuth = captor.getValue();
        assertEquals(USER_ID, savedOAuth.getProviderUserId());
        assertEquals(ENCRYPTED_ACCESS_TOKEN, savedOAuth.getAccessTokenEnc());
        assertTrue(savedOAuth.getUpdatedAt().isAfter(existingOAuth.getCreatedAt()));
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
        response.put("workspace_name", WORKSPACE_NAME);
        response.put("workspace_id", WORKSPACE_ID);
        response.put("workspace_icon", WORKSPACE_ICON);
        response.put("bot_id", BOT_ID);
        response.put("duplicated_template_id", DUPLICATED_TEMPLATE_ID);

        Map<String, Object> owner = new HashMap<>();
        owner.put("type", "user");

        Map<String, Object> user = new HashMap<>();
        user.put("id", USER_ID);
        user.put("name", USER_NAME);
        user.put("avatar_url", AVATAR_URL);

        Map<String, Object> person = new HashMap<>();
        person.put("email", USER_EMAIL);
        user.put("person", person);

        owner.put("user", user);
        response.put("owner", owner);

        return response;
    }

    private Map<String, Object> createTokenResponseMapWithoutEmail() {
        Map<String, Object> response = new HashMap<>();
        response.put("access_token", ACCESS_TOKEN);
        response.put("workspace_name", WORKSPACE_NAME);
        response.put("bot_id", BOT_ID);

        Map<String, Object> owner = new HashMap<>();
        owner.put("type", "user");

        Map<String, Object> user = new HashMap<>();
        user.put("id", USER_ID);
        user.put("name", USER_NAME);

        Map<String, Object> person = new HashMap<>();
        // Pas d'email
        user.put("person", person);

        owner.put("user", user);
        response.put("owner", owner);

        return response;
    }

    private Map<String, Object> createTokenResponseMapWithBlankEmail() {
        Map<String, Object> response = new HashMap<>();
        response.put("access_token", ACCESS_TOKEN);
        response.put("workspace_name", WORKSPACE_NAME);
        response.put("bot_id", BOT_ID);

        Map<String, Object> owner = new HashMap<>();
        owner.put("type", "user");

        Map<String, Object> user = new HashMap<>();
        user.put("id", USER_ID);
        user.put("name", USER_NAME);

        Map<String, Object> person = new HashMap<>();
        person.put("email", "");  // Email vide
        user.put("person", person);

        owner.put("user", user);
        response.put("owner", owner);

        return response;
    }

    private Map<String, Object> createMinimalTokenResponseMap() {
        Map<String, Object> response = new HashMap<>();
        response.put("access_token", ACCESS_TOKEN);
        response.put("bot_id", BOT_ID);

        Map<String, Object> owner = new HashMap<>();
        owner.put("type", "user");

        Map<String, Object> user = new HashMap<>();
        user.put("id", USER_ID);
        user.put("name", USER_NAME);

        Map<String, Object> person = new HashMap<>();
        person.put("email", USER_EMAIL);
        user.put("person", person);

        owner.put("user", user);
        response.put("owner", owner);

        return response;
    }

    private Map<String, Object> createTokenResponseMapWithBotIdFallback() {
        Map<String, Object> response = new HashMap<>();
        response.put("access_token", ACCESS_TOKEN);
        response.put("workspace_name", WORKSPACE_NAME);
        response.put("bot_id", BOT_ID);

        Map<String, Object> owner = new HashMap<>();
        owner.put("type", "user");

        Map<String, Object> user = new HashMap<>();
        // Pas d'id
        user.put("name", USER_NAME);

        Map<String, Object> person = new HashMap<>();
        person.put("email", USER_EMAIL);
        user.put("person", person);

        owner.put("user", user);
        response.put("owner", owner);

        return response;
    }

    private Map<String, Object> createTokenResponseMapWithoutUserIdentifier() {
        Map<String, Object> response = new HashMap<>();
        response.put("access_token", ACCESS_TOKEN);
        response.put("workspace_name", WORKSPACE_NAME);
        // Pas de bot_id

        Map<String, Object> owner = new HashMap<>();
        owner.put("type", "user");

        Map<String, Object> user = new HashMap<>();
        // Pas d'id

        Map<String, Object> person = new HashMap<>();
        person.put("email", USER_EMAIL);
        user.put("person", person);

        owner.put("user", user);
        response.put("owner", owner);

        return response;
    }

    private Map<String, Object> createTokenResponseMapWithoutName() {
        Map<String, Object> response = new HashMap<>();
        response.put("access_token", ACCESS_TOKEN);
        response.put("workspace_name", WORKSPACE_NAME);
        response.put("bot_id", BOT_ID);

        Map<String, Object> owner = new HashMap<>();
        owner.put("type", "user");

        Map<String, Object> user = new HashMap<>();
        user.put("id", USER_ID);
        // Pas de name

        Map<String, Object> person = new HashMap<>();
        person.put("email", USER_EMAIL);
        user.put("person", person);

        owner.put("user", user);
        response.put("owner", owner);

        return response;
    }

    private Map<String, Object> createTokenResponseMapWithoutAvatar() {
        Map<String, Object> response = new HashMap<>();
        response.put("access_token", ACCESS_TOKEN);
        response.put("workspace_name", WORKSPACE_NAME);
        response.put("bot_id", BOT_ID);

        Map<String, Object> owner = new HashMap<>();
        owner.put("type", "user");

        Map<String, Object> user = new HashMap<>();
        user.put("id", USER_ID);
        user.put("name", USER_NAME);
        // Pas d'avatar_url

        Map<String, Object> person = new HashMap<>();
        person.put("email", USER_EMAIL);
        user.put("person", person);

        owner.put("user", user);
        response.put("owner", owner);

        return response;
    }

    private Map<String, Object> createTokenResponseMapWithoutDuplicatedTemplateId() {
        Map<String, Object> response = new HashMap<>();
        response.put("access_token", ACCESS_TOKEN);
        response.put("workspace_name", WORKSPACE_NAME);
        response.put("workspace_id", WORKSPACE_ID);
        response.put("workspace_icon", WORKSPACE_ICON);
        response.put("bot_id", BOT_ID);
        // Pas de duplicated_template_id

        Map<String, Object> owner = new HashMap<>();
        owner.put("type", "user");

        Map<String, Object> user = new HashMap<>();
        user.put("id", USER_ID);
        user.put("name", USER_NAME);
        user.put("avatar_url", AVATAR_URL);

        Map<String, Object> person = new HashMap<>();
        person.put("email", USER_EMAIL);
        user.put("person", person);

        owner.put("user", user);
        response.put("owner", owner);

        return response;
    }
}
