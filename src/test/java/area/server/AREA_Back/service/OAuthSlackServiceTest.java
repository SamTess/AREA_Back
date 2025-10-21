package area.server.AREA_Back.service;

import area.server.AREA_Back.config.JwtCookieProperties;
import area.server.AREA_Back.dto.OAuthLoginRequest;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.repository.UserOAuthIdentityRepository;
import area.server.AREA_Back.repository.UserRepository;
import area.server.AREA_Back.service.Auth.AuthService;
import area.server.AREA_Back.service.Auth.JwtService;
import area.server.AREA_Back.service.Auth.OAuthSlackService;
import area.server.AREA_Back.service.Auth.TokenEncryptionService;
import area.server.AREA_Back.service.Redis.RedisTokenService;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class OAuthSlackServiceTest {

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
    private AuthService authService;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private JwtCookieProperties jwtCookieProperties;

    private SimpleMeterRegistry meterRegistry;
    private OAuthSlackService oauthSlackService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();

        lenient().when(jwtCookieProperties.getAccessTokenExpiry()).thenReturn(900);
        lenient().when(jwtCookieProperties.getRefreshTokenExpiry()).thenReturn(604800);
        lenient().when(jwtCookieProperties.isSecure()).thenReturn(false);
        lenient().when(jwtCookieProperties.getSameSite()).thenReturn("Strict");
        lenient().when(jwtCookieProperties.getDomain()).thenReturn(null);

        oauthSlackService = new OAuthSlackService(
            "test-slack-client-id",
            "test-slack-client-secret",
            "http://localhost:3000",
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

        try {
            var initMetricsMethod = OAuthSlackService.class.getDeclaredMethod("initMetrics");
            initMetricsMethod.setAccessible(true);
            initMetricsMethod.invoke(oauthSlackService);
        } catch (Exception e) {
            fail("Failed to initialize metrics: " + e.getMessage());
        }
    }

    @Test
    void testServiceInitialization() {
        assertNotNull(oauthSlackService);
        assertNotNull(meterRegistry);
        assertEquals("slack", oauthSlackService.getProviderKey());
        assertEquals("Slack", oauthSlackService.getProviderLabel());
    }

    @Test
    void testAuthenticateWithInvalidCode() {
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode("invalid-code");

        assertThrows(RuntimeException.class, () -> {
            oauthSlackService.authenticate(request, httpServletResponse);
        });
    }

    @Test
    void testLinkToExistingUserWithInvalidCode() {
        User existingUser = new User();
        existingUser.setId(UUID.randomUUID());
        existingUser.setEmail("test@example.com");

        assertThrows(RuntimeException.class, () -> {
            oauthSlackService.linkToExistingUser(existingUser, "invalid-code");
        });
    }

    @Test
    void testGetProviderKey() {
        assertEquals("slack", oauthSlackService.getProviderKey());
    }

    @Test
    void testGetProviderLabel() {
        assertEquals("Slack", oauthSlackService.getProviderLabel());
    }

    @Test
    void testGetClientId() {
        assertEquals("test-slack-client-id", oauthSlackService.getClientId());
    }
}
