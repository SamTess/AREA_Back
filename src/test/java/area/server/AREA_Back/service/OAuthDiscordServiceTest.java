package area.server.AREA_Back.service;

import area.server.AREA_Back.service.Auth.AuthService;
import area.server.AREA_Back.service.Auth.JwtService;
import area.server.AREA_Back.service.Auth.OAuthDiscordService;
import area.server.AREA_Back.service.Auth.TokenEncryptionService;
import area.server.AREA_Back.service.Redis.RedisTokenService;
import area.server.AREA_Back.repository.UserOAuthIdentityRepository;
import area.server.AREA_Back.repository.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@ExtendWith(MockitoExtension.class)
class OAuthDiscordServiceTest {

    @Mock
    private UserOAuthIdentityRepository userOAuthIdentityRepository;

    @Mock
    private UserRepository userRepository;

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

    private SimpleMeterRegistry meterRegistry;
    private OAuthDiscordService oauthDiscordService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        oauthDiscordService = new OAuthDiscordService(
            "test-client-id",
            "test-client-secret",
            "http://localhost:3000",
            jwtService,
            meterRegistry,
            redisTokenService,
            passwordEncoder,
            tokenEncryptionService,
            userOAuthIdentityRepository,
            userRepository,
            authService
        );

        try {
            var initMetricsMethod = OAuthDiscordService.class.getDeclaredMethod("initMetrics");
            initMetricsMethod.setAccessible(true);
            initMetricsMethod.invoke(oauthDiscordService);
        } catch (Exception e) {
            fail("Failed to initialize metrics: " + e.getMessage());
        }
    }

    @Test
    void testServiceInitialization() {
        assertNotNull(oauthDiscordService);
        assertNotNull(meterRegistry);
    }

    @Test
    void testProviderKeyIsDiscord() {
        assertEquals("discord", oauthDiscordService.getProviderKey());
    }

    @Test
    void testProviderLabelIsDiscord() {
        assertEquals("Discord", oauthDiscordService.getProviderLabel());
    }

    @Test
    void testAuthorizationUrlContainsDiscordEndpoint() {
        String authUrl = oauthDiscordService.getUserAuthUrl();
        assertTrue(authUrl.contains("discord.com/api/oauth2/authorize"));
        assertTrue(authUrl.contains("client_id=test-client-id"));
        assertTrue(authUrl.contains("redirect_uri=http://localhost:3000/oauth-callback"));
    }

    @Test
    void testAuthorizationUrlContainsRequiredScopes() {
        String authUrl = oauthDiscordService.getUserAuthUrl();
        assertTrue(authUrl.contains("scope=identify"));
        assertTrue(authUrl.contains("email"));
        assertTrue(authUrl.contains("guilds"));
    }

    @Test
    void testClientIdConfiguration() {
        assertEquals("test-client-id", oauthDiscordService.getClientId());
    }

    @Test
    void testClientSecretConfiguration() {
        assertEquals("test-client-secret", oauthDiscordService.getClientSecret());
    }

    @Test
    void testMetricsRegistration() {
        assertNotNull(meterRegistry.find("oauth.discord.login.success").counter());
        assertNotNull(meterRegistry.find("oauth.discord.login.failure").counter());
        assertNotNull(meterRegistry.find("oauth.discord.authenticate.calls").counter());
        assertNotNull(meterRegistry.find("oauth.discord.token_exchange.calls").counter());
        assertNotNull(meterRegistry.find("oauth.discord.token_exchange.failures").counter());
    }

    @Test
    void testProviderLogoUrl() {
        String logoUrl = oauthDiscordService.getProviderLogoUrl();
        assertNotNull(logoUrl);
        assertTrue(logoUrl.contains("discord"));
    }
}
