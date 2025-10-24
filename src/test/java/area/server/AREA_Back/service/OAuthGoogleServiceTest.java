package area.server.AREA_Back.service;

import area.server.AREA_Back.config.JwtCookieProperties;
import area.server.AREA_Back.dto.OAuthLoginRequest;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.repository.UserOAuthIdentityRepository;
import area.server.AREA_Back.repository.UserRepository;
import area.server.AREA_Back.service.Auth.JwtService;
import area.server.AREA_Back.service.Auth.OAuthGoogleService;
import area.server.AREA_Back.service.Auth.TokenEncryptionService;
import area.server.AREA_Back.service.Redis.RedisTokenService;
import area.server.AREA_Back.service.Webhook.GoogleWatchService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.lenient;

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
}
