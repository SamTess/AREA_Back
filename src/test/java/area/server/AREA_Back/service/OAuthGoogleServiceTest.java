package area.server.AREA_Back.service;

import area.server.AREA_Back.config.JwtCookieProperties;
import area.server.AREA_Back.dto.AuthResponse;
import area.server.AREA_Back.dto.OAuthLoginRequest;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.entity.UserOAuthIdentity;
import area.server.AREA_Back.repository.UserOAuthIdentityRepository;
import area.server.AREA_Back.repository.UserRepository;
import area.server.AREA_Back.service.Auth.JwtService;
import area.server.AREA_Back.service.Auth.OAuthGoogleService;
import area.server.AREA_Back.service.Auth.TokenEncryptionService;
import area.server.AREA_Back.service.Redis.RedisTokenService;
import area.server.AREA_Back.service.Webhook.GoogleWatchService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for OAuthGoogleService
 * Tests Google OAuth authentication, token exchange, and user linking
 */
@ExtendWith(MockitoExtension.class)
class OAuthGoogleServiceTest {

    @Mock
    private UserOAuthIdentityRepository userOAuthIdentityRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private HttpServletResponse httpServletResponse;

    @Mock
    private TokenEncryptionService tokenEncryptionService;

    @Mock
    private JwtService jwtService;

    @Mock
    private RedisTokenService redisTokenService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private JwtCookieProperties jwtCookieProperties;

    @Mock
    private GoogleWatchService googleWatchService;

    private SimpleMeterRegistry meterRegistry;
    private OAuthGoogleService oauthGoogleService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();

        // Setup JwtCookieProperties mock with lenient() to avoid UnnecessaryStubbingException
        lenient().when(jwtCookieProperties.getAccessTokenExpiry()).thenReturn(900);
        lenient().when(jwtCookieProperties.getRefreshTokenExpiry()).thenReturn(604800);
        lenient().when(jwtCookieProperties.isSecure()).thenReturn(false);
        lenient().when(jwtCookieProperties.getSameSite()).thenReturn("Strict");
        lenient().when(jwtCookieProperties.getDomain()).thenReturn(null);

        oauthGoogleService = new OAuthGoogleService(
            "test-google-client-id",
            "test-google-client-secret",
            "http://localhost:3000",
            jwtService,
            jwtCookieProperties,
            meterRegistry,
            redisTokenService,
            passwordEncoder,
            tokenEncryptionService,
            userOAuthIdentityRepository,
            userRepository,
            restTemplate,
            googleWatchService
        );

        // Manually initialize metrics since @PostConstruct won't run in tests
        try {
            var initMetricsMethod = OAuthGoogleService.class.getDeclaredMethod("initMetrics");
            initMetricsMethod.setAccessible(true);
            initMetricsMethod.invoke(oauthGoogleService);
        } catch (Exception e) {
            fail("Failed to initialize metrics: " + e.getMessage());
        }
    }

    @Test
    void testServiceInitialization() {
        // Test that the service is properly initialized
        assertNotNull(oauthGoogleService);
        assertEquals("google", oauthGoogleService.getProviderKey());
        assertEquals("Google", oauthGoogleService.getProviderLabel());
        assertNotNull(oauthGoogleService.getUserAuthUrl());
        assertTrue(oauthGoogleService.getUserAuthUrl().contains("accounts.google.com"));
    }

    @Test
    void testGetProviderKey() {
        assertEquals("google", oauthGoogleService.getProviderKey());
    }

    @Test
    void testGetProviderLabel() {
        assertEquals("Google", oauthGoogleService.getProviderLabel());
    }

    @Test
    void testGetProviderLogoUrl() {
        assertEquals("https://img.icons8.com/?size=100&id=17949&format=png&color=000000", oauthGoogleService.getProviderLogoUrl());
    }

    @Test
    void testUserAuthUrlContainsRequiredScopes() {
        String authUrl = oauthGoogleService.getUserAuthUrl();

        // Verify the URL contains all required scopes (Gmail only)
        assertTrue(authUrl.contains("openid"));
        assertTrue(authUrl.contains("email"));
        assertTrue(authUrl.contains("profile"));
        assertTrue(authUrl.contains("gmail.readonly"));
        assertTrue(authUrl.contains("gmail.send"));
        assertTrue(authUrl.contains("gmail.modify"));
    }

    @Test
    void testUserAuthUrlContainsOfflineAccess() {
        String authUrl = oauthGoogleService.getUserAuthUrl();

        // Verify offline access for refresh tokens
        assertTrue(authUrl.contains("access_type=offline"));
        assertTrue(authUrl.contains("prompt=consent"));
    }

    @Test
    void testAuthenticateWithInvalidCode() {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode("invalid-code");

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            oauthGoogleService.authenticate(request, httpServletResponse);
        });

        // Verify failure counter was incremented
        double failureCount = meterRegistry.counter("oauth.google.login.failure").count();
        assertTrue(failureCount > 0);
    }

    @Test
    void testAuthenticateWithNullCode() {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode(null);

        // When & Then
        assertThrows(Exception.class, () -> {
            oauthGoogleService.authenticate(request, httpServletResponse);
        });
    }

    @Test
    void testAuthenticateWithEmptyCode() {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode("");

        // When & Then
        assertThrows(Exception.class, () -> {
            oauthGoogleService.authenticate(request, httpServletResponse);
        });
    }

    @Test
    void testLinkToExistingUserWithInvalidCode() {
        // Given
        User existingUser = new User();
        existingUser.setId(UUID.randomUUID());
        existingUser.setEmail("test@example.com");

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            oauthGoogleService.linkToExistingUser(existingUser, "invalid-code");
        });
    }

    @Test
    void testLinkToExistingUserWithNullUser() {
        // When & Then
        assertThrows(Exception.class, () -> {
            oauthGoogleService.linkToExistingUser(null, "some-code");
        });
    }

    @Test
    void testLinkToExistingUserWithNullCode() {
        // Given
        User existingUser = new User();
        existingUser.setId(UUID.randomUUID());
        existingUser.setEmail("test@example.com");

        // When & Then
        assertThrows(Exception.class, () -> {
            oauthGoogleService.linkToExistingUser(existingUser, null);
        });
    }

    @Test
    void testMetricsAreRegistered() {
        // Verify all metrics are registered
        assertNotNull(meterRegistry.find("oauth.google.login.success").counter());
        assertNotNull(meterRegistry.find("oauth.google.login.failure").counter());
        assertNotNull(meterRegistry.find("oauth.google.authenticate.calls").counter());
        assertNotNull(meterRegistry.find("oauth.google.token_exchange.calls").counter());
        assertNotNull(meterRegistry.find("oauth.google.token_exchange.failures").counter());
    }

    @Test
    void testAuthenticateIncrementsCallCounter() {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode("invalid-code");

        double initialCount = meterRegistry.counter("oauth.google.authenticate.calls").count();

        // When
        try {
            oauthGoogleService.authenticate(request, httpServletResponse);
        } catch (Exception e) {
            // Expected to fail
        }

        // Then
        double finalCount = meterRegistry.counter("oauth.google.authenticate.calls").count();
        assertEquals(initialCount + 1, finalCount);
    }

    @Test
    void testAuthenticateIncrementsCalls() {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode("invalid-code");

        // When
        try {
            oauthGoogleService.authenticate(request, httpServletResponse);
        } catch (Exception e) {
            // Expected
        }

        // Then - authenticate.calls counter should be incremented
        double authenticateCalls = meterRegistry.counter("oauth.google.authenticate.calls").count();
        assertTrue(authenticateCalls > 0);
    }

    @Test
    void testLinkToExistingUserRequiresUser() {
        // When & Then
        assertThrows(Exception.class, () -> {
            oauthGoogleService.linkToExistingUser(null, "some-code");
        });
    }

    @Test
    void testLinkToExistingUserRequiresCode() {
        // Given
        User user = new User();
        user.setId(UUID.randomUUID());

        // When & Then
        assertThrows(Exception.class, () -> {
            oauthGoogleService.linkToExistingUser(user, null);
        });
    }

    @Test
    void testAuthUrlContainsCorrectRedirectUri() {
        String authUrl = oauthGoogleService.getUserAuthUrl();
        assertTrue(authUrl.contains("redirect_uri=http://localhost:3000/oauth-callback"));
    }

    @Test
    void testAuthUrlContainsClientId() {
        String authUrl = oauthGoogleService.getUserAuthUrl();
        assertTrue(authUrl.contains("client_id=test-google-client-id"));
    }

    @Test
    void testAuthUrlUsesAuthorizationCodeFlow() {
        String authUrl = oauthGoogleService.getUserAuthUrl();
        assertTrue(authUrl.contains("response_type=code"));
    }

    @Test
    void testAuthenticateSuccessfully() {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode("valid-code");

        // Mock token exchange response
        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("access_token", "test-access-token");
        tokenResponseBody.put("refresh_token", "test-refresh-token");
        tokenResponseBody.put("expires_in", 3600);
        ResponseEntity<Map<String, Object>> tokenResponse = new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://oauth2.googleapis.com/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponse);

        // Mock user profile response
        Map<String, Object> userProfileBody = new HashMap<>();
        userProfileBody.put("id", "google-user-123");
        userProfileBody.put("email", "test@gmail.com");
        userProfileBody.put("name", "Test User");
        userProfileBody.put("given_name", "Test");
        userProfileBody.put("family_name", "User");
        userProfileBody.put("picture", "https://example.com/pic.jpg");
        userProfileBody.put("locale", "en");
        userProfileBody.put("verified_email", true);
        ResponseEntity<Map<String, Object>> userProfileResponse = new ResponseEntity<>(userProfileBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://www.googleapis.com/oauth2/v2/userinfo"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(userProfileResponse);

        // Mock user and oauth identity
        User existingUser = new User();
        existingUser.setId(UUID.randomUUID());
        existingUser.setEmail("test@gmail.com");
        existingUser.setIsActive(true);
        existingUser.setIsAdmin(false);
        existingUser.setCreatedAt(LocalDateTime.now());

        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenReturn(existingUser);

        UserOAuthIdentity oauthIdentity = new UserOAuthIdentity();
        oauthIdentity.setId(UUID.randomUUID());
        oauthIdentity.setUser(existingUser);
        oauthIdentity.setProvider("google");
        oauthIdentity.setProviderUserId("google-user-123");

        when(userOAuthIdentityRepository.findByUserAndProvider(any(User.class), eq("google")))
            .thenReturn(Optional.of(oauthIdentity));
        when(userOAuthIdentityRepository.save(any(UserOAuthIdentity.class))).thenReturn(oauthIdentity);

        // Mock encryption
        when(tokenEncryptionService.encryptToken(anyString())).thenReturn("encrypted-token");

        // Mock JWT generation
        when(jwtService.generateAccessToken(any(UUID.class), anyString())).thenReturn("jwt-access-token");
        when(jwtService.generateRefreshToken(any(UUID.class), anyString())).thenReturn("jwt-refresh-token");

        // Mock Redis
        lenient().doNothing().when(redisTokenService).storeAccessToken(anyString(), any(UUID.class));
        lenient().doNothing().when(redisTokenService).storeRefreshToken(any(UUID.class), anyString());

        // Mock Google Watch Service
        when(googleWatchService.startGmailWatch(any(UUID.class))).thenReturn(new HashMap<>());

        // When
        AuthResponse response = oauthGoogleService.authenticate(request, httpServletResponse);

        // Then
        assertNotNull(response);
        assertEquals("Login successful", response.getMessage());
        assertNotNull(response.getUser());
        assertEquals("test@gmail.com", response.getUser().getEmail());

        // Verify metrics
        double successCount = meterRegistry.counter("oauth.google.login.success").count();
        assertTrue(successCount > 0);

        // Verify Gmail watch was started
        verify(googleWatchService, times(1)).startGmailWatch(any(UUID.class));
    }

    @Test
    void testAuthenticateWithNewUser() {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode("valid-code");

        // Mock token exchange response
        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("access_token", "test-access-token");
        tokenResponseBody.put("refresh_token", "test-refresh-token");
        tokenResponseBody.put("expires_in", 3600);
        ResponseEntity<Map<String, Object>> tokenResponse = new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://oauth2.googleapis.com/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponse);

        // Mock user profile response
        Map<String, Object> userProfileBody = new HashMap<>();
        userProfileBody.put("id", "google-new-user-456");
        userProfileBody.put("email", "newuser@gmail.com");
        userProfileBody.put("name", "New User");
        userProfileBody.put("given_name", "New");
        userProfileBody.put("family_name", "User");
        userProfileBody.put("picture", "https://example.com/newpic.jpg");
        userProfileBody.put("locale", "fr");
        userProfileBody.put("verified_email", true);
        ResponseEntity<Map<String, Object>> userProfileResponse = new ResponseEntity<>(userProfileBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://www.googleapis.com/oauth2/v2/userinfo"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(userProfileResponse);

        // Mock no existing user
        when(userRepository.findByEmail("newuser@gmail.com")).thenReturn(Optional.empty());

        User newUser = new User();
        newUser.setId(UUID.randomUUID());
        newUser.setEmail("newuser@gmail.com");
        newUser.setIsActive(true);
        newUser.setIsAdmin(false);
        newUser.setCreatedAt(LocalDateTime.now());
        newUser.setAvatarUrl("https://example.com/newpic.jpg");

        when(userRepository.save(any(User.class))).thenReturn(newUser);

        UserOAuthIdentity oauthIdentity = new UserOAuthIdentity();
        oauthIdentity.setId(UUID.randomUUID());
        oauthIdentity.setUser(newUser);
        oauthIdentity.setProvider("google");
        oauthIdentity.setProviderUserId("google-new-user-456");

        when(userOAuthIdentityRepository.findByUserAndProvider(any(User.class), eq("google")))
            .thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.save(any(UserOAuthIdentity.class))).thenReturn(oauthIdentity);

        // Mock encryption
        when(tokenEncryptionService.encryptToken(anyString())).thenReturn("encrypted-token");

        // Mock JWT generation
        when(jwtService.generateAccessToken(any(UUID.class), anyString())).thenReturn("jwt-access-token");
        when(jwtService.generateRefreshToken(any(UUID.class), anyString())).thenReturn("jwt-refresh-token");

        // Mock Redis
        lenient().doNothing().when(redisTokenService).storeAccessToken(anyString(), any(UUID.class));
        lenient().doNothing().when(redisTokenService).storeRefreshToken(any(UUID.class), anyString());

        // Mock Google Watch Service
        when(googleWatchService.startGmailWatch(any(UUID.class))).thenReturn(new HashMap<>());

        // When
        AuthResponse response = oauthGoogleService.authenticate(request, httpServletResponse);

        // Then
        assertNotNull(response);
        assertEquals("Login successful", response.getMessage());
        assertNotNull(response.getUser());
        assertEquals("newuser@gmail.com", response.getUser().getEmail());

        // Verify new user was created
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void testAuthenticateWithMissingEmail() {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode("valid-code");

        // Mock token exchange response
        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("access_token", "test-access-token");
        tokenResponseBody.put("refresh_token", "test-refresh-token");
        tokenResponseBody.put("expires_in", 3600);
        ResponseEntity<Map<String, Object>> tokenResponse = new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://oauth2.googleapis.com/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponse);

        // Mock user profile response without email
        Map<String, Object> userProfileBody = new HashMap<>();
        userProfileBody.put("id", "google-user-no-email");
        userProfileBody.put("name", "No Email User");
        ResponseEntity<Map<String, Object>> userProfileResponse = new ResponseEntity<>(userProfileBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://www.googleapis.com/oauth2/v2/userinfo"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(userProfileResponse);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            oauthGoogleService.authenticate(request, httpServletResponse);
        });

        assertTrue(exception.getMessage().contains("email is required"));
    }

    @Test
    void testAuthenticateWithGmailWatchFailure() {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode("valid-code");

        // Mock token exchange response
        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("access_token", "test-access-token");
        tokenResponseBody.put("refresh_token", "test-refresh-token");
        tokenResponseBody.put("expires_in", 3600);
        ResponseEntity<Map<String, Object>> tokenResponse = new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://oauth2.googleapis.com/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponse);

        // Mock user profile response
        Map<String, Object> userProfileBody = new HashMap<>();
        userProfileBody.put("id", "google-user-123");
        userProfileBody.put("email", "test@gmail.com");
        userProfileBody.put("name", "Test User");
        userProfileBody.put("verified_email", true);
        ResponseEntity<Map<String, Object>> userProfileResponse = new ResponseEntity<>(userProfileBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://www.googleapis.com/oauth2/v2/userinfo"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(userProfileResponse);

        // Mock user and oauth identity
        User existingUser = new User();
        existingUser.setId(UUID.randomUUID());
        existingUser.setEmail("test@gmail.com");
        existingUser.setIsActive(true);
        existingUser.setIsAdmin(false);
        existingUser.setCreatedAt(LocalDateTime.now());

        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenReturn(existingUser);

        UserOAuthIdentity oauthIdentity = new UserOAuthIdentity();
        oauthIdentity.setId(UUID.randomUUID());
        oauthIdentity.setUser(existingUser);
        oauthIdentity.setProvider("google");
        oauthIdentity.setProviderUserId("google-user-123");

        when(userOAuthIdentityRepository.findByUserAndProvider(any(User.class), eq("google")))
            .thenReturn(Optional.of(oauthIdentity));
        when(userOAuthIdentityRepository.save(any(UserOAuthIdentity.class))).thenReturn(oauthIdentity);

        // Mock encryption
        when(tokenEncryptionService.encryptToken(anyString())).thenReturn("encrypted-token");

        // Mock JWT generation
        when(jwtService.generateAccessToken(any(UUID.class), anyString())).thenReturn("jwt-access-token");
        when(jwtService.generateRefreshToken(any(UUID.class), anyString())).thenReturn("jwt-refresh-token");

        // Mock Redis
        lenient().doNothing().when(redisTokenService).storeAccessToken(anyString(), any(UUID.class));
        lenient().doNothing().when(redisTokenService).storeRefreshToken(any(UUID.class), anyString());

        // Mock Google Watch Service to fail (but should not prevent authentication)
        doThrow(new RuntimeException("Watch service failed")).when(googleWatchService).startGmailWatch(any(UUID.class));

        // When - should succeed despite watch failure
        AuthResponse response = oauthGoogleService.authenticate(request, httpServletResponse);

        // Then
        assertNotNull(response);
        assertEquals("Login successful", response.getMessage());
    }

    @Test
    void testLinkToExistingUserSuccessfully() {
        // Given
        User existingUser = new User();
        existingUser.setId(UUID.randomUUID());
        existingUser.setEmail("existing@example.com");

        // Mock token exchange response
        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("access_token", "test-access-token");
        tokenResponseBody.put("refresh_token", "test-refresh-token");
        tokenResponseBody.put("expires_in", 3600);
        ResponseEntity<Map<String, Object>> tokenResponse = new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://oauth2.googleapis.com/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponse);

        // Mock user profile response
        Map<String, Object> userProfileBody = new HashMap<>();
        userProfileBody.put("id", "google-link-user-789");
        userProfileBody.put("email", "link@gmail.com");
        userProfileBody.put("name", "Link User");
        userProfileBody.put("given_name", "Link");
        userProfileBody.put("family_name", "User");
        userProfileBody.put("picture", "https://example.com/linkpic.jpg");
        userProfileBody.put("locale", "en");
        userProfileBody.put("verified_email", true);
        ResponseEntity<Map<String, Object>> userProfileResponse = new ResponseEntity<>(userProfileBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://www.googleapis.com/oauth2/v2/userinfo"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(userProfileResponse);

        // No existing OAuth identity with this provider user ID
        when(userOAuthIdentityRepository.findByProviderAndProviderUserId("google", "google-link-user-789"))
            .thenReturn(Optional.empty());

        // No existing OAuth identity for this user
        when(userOAuthIdentityRepository.findByUserAndProvider(existingUser, "google"))
            .thenReturn(Optional.empty());

        UserOAuthIdentity newOAuthIdentity = new UserOAuthIdentity();
        newOAuthIdentity.setId(UUID.randomUUID());
        newOAuthIdentity.setUser(existingUser);
        newOAuthIdentity.setProvider("google");
        newOAuthIdentity.setProviderUserId("google-link-user-789");

        when(userOAuthIdentityRepository.save(any(UserOAuthIdentity.class))).thenReturn(newOAuthIdentity);

        // Mock encryption
        when(tokenEncryptionService.encryptToken(anyString())).thenReturn("encrypted-token");

        // Mock Google Watch Service
        when(googleWatchService.startGmailWatch(any(UUID.class))).thenReturn(new HashMap<>());

        // When
        UserOAuthIdentity result = oauthGoogleService.linkToExistingUser(existingUser, "valid-code");

        // Then
        assertNotNull(result);
        assertEquals("google", result.getProvider());
        verify(userOAuthIdentityRepository, times(1)).save(any(UserOAuthIdentity.class));
        verify(googleWatchService, times(1)).startGmailWatch(existingUser.getId());
    }

    @Test
    void testLinkToExistingUserUpdateExisting() {
        // Given
        User existingUser = new User();
        existingUser.setId(UUID.randomUUID());
        existingUser.setEmail("existing@example.com");

        // Mock token exchange response
        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("access_token", "new-access-token");
        tokenResponseBody.put("refresh_token", "new-refresh-token");
        tokenResponseBody.put("expires_in", 7200);
        ResponseEntity<Map<String, Object>> tokenResponse = new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://oauth2.googleapis.com/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponse);

        // Mock user profile response
        Map<String, Object> userProfileBody = new HashMap<>();
        userProfileBody.put("id", "google-existing-999");
        userProfileBody.put("email", "existing-google@gmail.com");
        userProfileBody.put("name", "Existing Google User");
        userProfileBody.put("verified_email", true);
        ResponseEntity<Map<String, Object>> userProfileResponse = new ResponseEntity<>(userProfileBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://www.googleapis.com/oauth2/v2/userinfo"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(userProfileResponse);

        // No OAuth identity with this provider user ID linked to a different user
        when(userOAuthIdentityRepository.findByProviderAndProviderUserId("google", "google-existing-999"))
            .thenReturn(Optional.empty());

        // Existing OAuth identity for this user
        UserOAuthIdentity existingOAuth = new UserOAuthIdentity();
        existingOAuth.setId(UUID.randomUUID());
        existingOAuth.setUser(existingUser);
        existingOAuth.setProvider("google");
        existingOAuth.setProviderUserId("old-google-id");

        when(userOAuthIdentityRepository.findByUserAndProvider(existingUser, "google"))
            .thenReturn(Optional.of(existingOAuth));
        when(userOAuthIdentityRepository.save(any(UserOAuthIdentity.class))).thenReturn(existingOAuth);

        // Mock encryption
        when(tokenEncryptionService.encryptToken(anyString())).thenReturn("encrypted-token");

        // Mock Google Watch Service
        when(googleWatchService.startGmailWatch(any(UUID.class))).thenReturn(new HashMap<>());

        // When
        UserOAuthIdentity result = oauthGoogleService.linkToExistingUser(existingUser, "valid-code");

        // Then
        assertNotNull(result);
        verify(userOAuthIdentityRepository, times(1)).save(any(UserOAuthIdentity.class));
    }

    @Test
    void testLinkToExistingUserAlreadyLinkedToAnotherUser() {
        // Given
        User existingUser = new User();
        existingUser.setId(UUID.randomUUID());
        existingUser.setEmail("existing@example.com");

        User otherUser = new User();
        otherUser.setId(UUID.randomUUID());
        otherUser.setEmail("other@example.com");

        // Mock token exchange response
        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("access_token", "test-access-token");
        tokenResponseBody.put("expires_in", 3600);
        ResponseEntity<Map<String, Object>> tokenResponse = new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://oauth2.googleapis.com/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponse);

        // Mock user profile response
        Map<String, Object> userProfileBody = new HashMap<>();
        userProfileBody.put("id", "google-already-linked");
        userProfileBody.put("email", "linked@gmail.com");
        ResponseEntity<Map<String, Object>> userProfileResponse = new ResponseEntity<>(userProfileBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://www.googleapis.com/oauth2/v2/userinfo"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(userProfileResponse);

        // OAuth identity already linked to another user
        UserOAuthIdentity otherUserOAuth = new UserOAuthIdentity();
        otherUserOAuth.setId(UUID.randomUUID());
        otherUserOAuth.setUser(otherUser);
        otherUserOAuth.setProvider("google");
        otherUserOAuth.setProviderUserId("google-already-linked");

        when(userOAuthIdentityRepository.findByProviderAndProviderUserId("google", "google-already-linked"))
            .thenReturn(Optional.of(otherUserOAuth));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            oauthGoogleService.linkToExistingUser(existingUser, "valid-code");
        });

        assertTrue(exception.getMessage().contains("already linked to another user"));
    }

    @Test
    void testLinkToExistingUserWithMissingEmail() {
        // Given
        User existingUser = new User();
        existingUser.setId(UUID.randomUUID());
        existingUser.setEmail("existing@example.com");

        // Mock token exchange response
        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("access_token", "test-access-token");
        tokenResponseBody.put("expires_in", 3600);
        ResponseEntity<Map<String, Object>> tokenResponse = new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://oauth2.googleapis.com/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponse);

        // Mock user profile response without email
        Map<String, Object> userProfileBody = new HashMap<>();
        userProfileBody.put("id", "google-no-email");
        userProfileBody.put("name", "No Email");
        ResponseEntity<Map<String, Object>> userProfileResponse = new ResponseEntity<>(userProfileBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://www.googleapis.com/oauth2/v2/userinfo"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(userProfileResponse);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            oauthGoogleService.linkToExistingUser(existingUser, "valid-code");
        });

        assertTrue(exception.getMessage().contains("email is required"));
    }

    @Test
    void testExchangeCodeForTokenFailure() {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode("invalid-code");

        // Mock failed token exchange
        when(restTemplate.exchange(
            eq("https://oauth2.googleapis.com/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenThrow(new RestClientException("Invalid authorization code"));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            oauthGoogleService.authenticate(request, httpServletResponse);
        });

        // Verify metrics
        double exchangeFailures = meterRegistry.counter("oauth.google.token_exchange.failures").count();
        assertTrue(exchangeFailures > 0);
    }

    @Test
    void testExchangeCodeForTokenWithoutAccessToken() {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode("code-without-access-token");

        // Mock token exchange response without access_token
        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("expires_in", 3600);
        ResponseEntity<Map<String, Object>> tokenResponse = new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://oauth2.googleapis.com/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponse);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            oauthGoogleService.authenticate(request, httpServletResponse);
        });

        assertTrue(exception.getMessage().contains("No access_token"));
    }

    @Test
    void testFetchUserProfileFailure() {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode("valid-code");

        // Mock successful token exchange
        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("access_token", "test-access-token");
        tokenResponseBody.put("expires_in", 3600);
        ResponseEntity<Map<String, Object>> tokenResponse = new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://oauth2.googleapis.com/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponse);

        // Mock failed user profile fetch
        when(restTemplate.exchange(
            eq("https://www.googleapis.com/oauth2/v2/userinfo"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenThrow(new RestClientException("Failed to fetch user profile"));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            oauthGoogleService.authenticate(request, httpServletResponse);
        });
    }

    @Test
    void testFetchUserProfileWithoutUserId() {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode("valid-code");

        // Mock token exchange response
        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("access_token", "test-access-token");
        tokenResponseBody.put("expires_in", 3600);
        ResponseEntity<Map<String, Object>> tokenResponse = new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://oauth2.googleapis.com/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponse);

        // Mock user profile response without id
        Map<String, Object> userProfileBody = new HashMap<>();
        userProfileBody.put("email", "test@gmail.com");
        userProfileBody.put("name", "Test User");
        ResponseEntity<Map<String, Object>> userProfileResponse = new ResponseEntity<>(userProfileBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://www.googleapis.com/oauth2/v2/userinfo"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(userProfileResponse);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            oauthGoogleService.authenticate(request, httpServletResponse);
        });

        assertTrue(exception.getMessage().contains("user id is missing"));
    }

    @Test
    void testCalculateExpirationTimeWithValidExpiresIn() {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode("valid-code");

        // Mock token exchange response with specific expires_in
        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("access_token", "test-access-token");
        tokenResponseBody.put("refresh_token", "test-refresh-token");
        tokenResponseBody.put("expires_in", 7200); // 2 hours
        ResponseEntity<Map<String, Object>> tokenResponse = new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://oauth2.googleapis.com/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponse);

        // Mock user profile response
        Map<String, Object> userProfileBody = new HashMap<>();
        userProfileBody.put("id", "google-user-exp");
        userProfileBody.put("email", "exp@gmail.com");
        userProfileBody.put("verified_email", true);
        ResponseEntity<Map<String, Object>> userProfileResponse = new ResponseEntity<>(userProfileBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://www.googleapis.com/oauth2/v2/userinfo"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(userProfileResponse);

        User newUser = new User();
        newUser.setId(UUID.randomUUID());
        newUser.setEmail("exp@gmail.com");
        newUser.setIsActive(true);
        newUser.setIsAdmin(false);

        when(userRepository.findByEmail("exp@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(newUser);

        UserOAuthIdentity oauthIdentity = new UserOAuthIdentity();
        oauthIdentity.setUser(newUser);

        when(userOAuthIdentityRepository.findByUserAndProvider(any(User.class), eq("google")))
            .thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.save(any(UserOAuthIdentity.class))).thenReturn(oauthIdentity);

        when(tokenEncryptionService.encryptToken(anyString())).thenReturn("encrypted");
        when(jwtService.generateAccessToken(any(UUID.class), anyString())).thenReturn("jwt-access");
        when(jwtService.generateRefreshToken(any(UUID.class), anyString())).thenReturn("jwt-refresh");
        lenient().doNothing().when(redisTokenService).storeAccessToken(anyString(), any(UUID.class));
        lenient().doNothing().when(redisTokenService).storeRefreshToken(any(UUID.class), anyString());
        when(googleWatchService.startGmailWatch(any(UUID.class))).thenReturn(new HashMap<>());

        // When
        AuthResponse response = oauthGoogleService.authenticate(request, httpServletResponse);

        // Then
        assertNotNull(response);
        // Expiration time should be calculated based on expires_in
        verify(userOAuthIdentityRepository, times(1)).save(any(UserOAuthIdentity.class));
    }

    @Test
    void testCalculateExpirationTimeWithNullExpiresIn() {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode("valid-code");

        // Mock token exchange response without expires_in
        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("access_token", "test-access-token");
        tokenResponseBody.put("refresh_token", "test-refresh-token");
        // expires_in is null
        ResponseEntity<Map<String, Object>> tokenResponse = new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://oauth2.googleapis.com/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponse);

        // Mock user profile response
        Map<String, Object> userProfileBody = new HashMap<>();
        userProfileBody.put("id", "google-user-null-exp");
        userProfileBody.put("email", "nullexp@gmail.com");
        userProfileBody.put("verified_email", true);
        ResponseEntity<Map<String, Object>> userProfileResponse = new ResponseEntity<>(userProfileBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://www.googleapis.com/oauth2/v2/userinfo"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(userProfileResponse);

        User newUser = new User();
        newUser.setId(UUID.randomUUID());
        newUser.setEmail("nullexp@gmail.com");
        newUser.setIsActive(true);

        when(userRepository.findByEmail("nullexp@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(newUser);

        UserOAuthIdentity oauthIdentity = new UserOAuthIdentity();
        oauthIdentity.setUser(newUser);

        when(userOAuthIdentityRepository.findByUserAndProvider(any(User.class), eq("google")))
            .thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.save(any(UserOAuthIdentity.class))).thenReturn(oauthIdentity);

        when(tokenEncryptionService.encryptToken(anyString())).thenReturn("encrypted");
        when(jwtService.generateAccessToken(any(UUID.class), anyString())).thenReturn("jwt-access");
        when(jwtService.generateRefreshToken(any(UUID.class), anyString())).thenReturn("jwt-refresh");
        lenient().doNothing().when(redisTokenService).storeAccessToken(anyString(), any(UUID.class));
        lenient().doNothing().when(redisTokenService).storeRefreshToken(any(UUID.class), anyString());
        when(googleWatchService.startGmailWatch(any(UUID.class))).thenReturn(new HashMap<>());

        // When
        AuthResponse response = oauthGoogleService.authenticate(request, httpServletResponse);

        // Then
        assertNotNull(response);
        // Should default to 1 hour when expires_in is null
    }

    @Test
    void testJwtGenerationFailure() {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode("valid-code");

        // Mock successful token exchange and user profile
        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("access_token", "test-access-token");
        tokenResponseBody.put("refresh_token", "test-refresh-token");
        tokenResponseBody.put("expires_in", 3600);
        ResponseEntity<Map<String, Object>> tokenResponse = new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://oauth2.googleapis.com/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponse);

        Map<String, Object> userProfileBody = new HashMap<>();
        userProfileBody.put("id", "google-jwt-fail");
        userProfileBody.put("email", "jwtfail@gmail.com");
        userProfileBody.put("verified_email", true);
        ResponseEntity<Map<String, Object>> userProfileResponse = new ResponseEntity<>(userProfileBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://www.googleapis.com/oauth2/v2/userinfo"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(userProfileResponse);

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("jwtfail@gmail.com");

        when(userRepository.findByEmail("jwtfail@gmail.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        UserOAuthIdentity oauth = new UserOAuthIdentity();
        oauth.setUser(user);

        when(userOAuthIdentityRepository.findByUserAndProvider(any(User.class), eq("google")))
            .thenReturn(Optional.of(oauth));
        when(userOAuthIdentityRepository.save(any(UserOAuthIdentity.class))).thenReturn(oauth);
        when(tokenEncryptionService.encryptToken(anyString())).thenReturn("encrypted");

        // Mock JWT generation failure
        when(jwtService.generateAccessToken(any(UUID.class), anyString()))
            .thenThrow(new RuntimeException("JWT generation failed"));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            oauthGoogleService.authenticate(request, httpServletResponse);
        });

        assertTrue(exception.getMessage().contains("JWT token generation failed"));
    }

    @Test
    void testRedisStorageFailure() {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode("valid-code");

        // Mock successful token exchange and user profile
        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("access_token", "test-access-token");
        tokenResponseBody.put("refresh_token", "test-refresh-token");
        tokenResponseBody.put("expires_in", 3600);
        ResponseEntity<Map<String, Object>> tokenResponse = new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://oauth2.googleapis.com/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponse);

        Map<String, Object> userProfileBody = new HashMap<>();
        userProfileBody.put("id", "google-redis-fail");
        userProfileBody.put("email", "redisfail@gmail.com");
        userProfileBody.put("verified_email", true);
        ResponseEntity<Map<String, Object>> userProfileResponse = new ResponseEntity<>(userProfileBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://www.googleapis.com/oauth2/v2/userinfo"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(userProfileResponse);

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("redisfail@gmail.com");

        when(userRepository.findByEmail("redisfail@gmail.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        UserOAuthIdentity oauth = new UserOAuthIdentity();
        oauth.setUser(user);

        when(userOAuthIdentityRepository.findByUserAndProvider(any(User.class), eq("google")))
            .thenReturn(Optional.of(oauth));
        when(userOAuthIdentityRepository.save(any(UserOAuthIdentity.class))).thenReturn(oauth);
        when(tokenEncryptionService.encryptToken(anyString())).thenReturn("encrypted");
        when(jwtService.generateAccessToken(any(UUID.class), anyString())).thenReturn("jwt-access");
        when(jwtService.generateRefreshToken(any(UUID.class), anyString())).thenReturn("jwt-refresh");

        // Mock Redis storage failure
        doThrow(new RuntimeException("Redis connection failed"))
            .when(redisTokenService).storeAccessToken(anyString(), any(UUID.class));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            oauthGoogleService.authenticate(request, httpServletResponse);
        });

        assertTrue(exception.getMessage().contains("Redis token storage failed"));
    }

    @Test
    void testHandleUserAuthenticationWithDuplicateKey() {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode("valid-code");

        // Mock token exchange
        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("access_token", "test-access-token");
        tokenResponseBody.put("refresh_token", "test-refresh-token");
        tokenResponseBody.put("expires_in", 3600);
        ResponseEntity<Map<String, Object>> tokenResponse = new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://oauth2.googleapis.com/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponse);

        // Mock user profile
        Map<String, Object> userProfileBody = new HashMap<>();
        userProfileBody.put("id", "google-dup-key");
        userProfileBody.put("email", "dupkey@gmail.com");
        userProfileBody.put("verified_email", true);
        ResponseEntity<Map<String, Object>> userProfileResponse = new ResponseEntity<>(userProfileBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://www.googleapis.com/oauth2/v2/userinfo"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(userProfileResponse);

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("dupkey@gmail.com");

        when(userRepository.findByEmail("dupkey@gmail.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        UserOAuthIdentity existingOAuth = new UserOAuthIdentity();
        existingOAuth.setUser(user);
        existingOAuth.setProvider("google");

        // First save throws duplicate key, then return existing
        when(userOAuthIdentityRepository.findByUserAndProvider(any(User.class), eq("google")))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(existingOAuth));

        when(userOAuthIdentityRepository.save(any(UserOAuthIdentity.class)))
            .thenThrow(new RuntimeException("duplicate key value violates unique constraint"))
            .thenReturn(existingOAuth);

        when(tokenEncryptionService.encryptToken(anyString())).thenReturn("encrypted");
        when(jwtService.generateAccessToken(any(UUID.class), anyString())).thenReturn("jwt-access");
        when(jwtService.generateRefreshToken(any(UUID.class), anyString())).thenReturn("jwt-refresh");
        lenient().doNothing().when(redisTokenService).storeAccessToken(anyString(), any(UUID.class));
        lenient().doNothing().when(redisTokenService).storeRefreshToken(any(UUID.class), anyString());
        when(googleWatchService.startGmailWatch(any(UUID.class))).thenReturn(new HashMap<>());

        // When
        AuthResponse response = oauthGoogleService.authenticate(request, httpServletResponse);

        // Then
        assertNotNull(response);
        assertEquals("Login successful", response.getMessage());
    }

    @Test
    void testTokenExchangeWithNonSuccessStatus() {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode("bad-code");

        // Mock token exchange with error status
        ResponseEntity<Map<String, Object>> tokenResponse = new ResponseEntity<>(HttpStatus.BAD_REQUEST);

        when(restTemplate.exchange(
            eq("https://oauth2.googleapis.com/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponse);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            oauthGoogleService.authenticate(request, httpServletResponse);
        });

        assertTrue(exception.getMessage().contains("Failed to exchange token"));
    }

    @Test
    void testFetchUserProfileWithNonSuccessStatus() {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode("valid-code");

        // Mock successful token exchange
        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("access_token", "test-access-token");
        tokenResponseBody.put("expires_in", 3600);
        ResponseEntity<Map<String, Object>> tokenResponse = new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://oauth2.googleapis.com/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponse);

        // Mock user profile with error status
        ResponseEntity<Map<String, Object>> userProfileResponse = new ResponseEntity<>(HttpStatus.UNAUTHORIZED);

        when(restTemplate.exchange(
            eq("https://www.googleapis.com/oauth2/v2/userinfo"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(userProfileResponse);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            oauthGoogleService.authenticate(request, httpServletResponse);
        });

        assertTrue(exception.getMessage().contains("Failed to fetch Google user profile"));
    }
}
