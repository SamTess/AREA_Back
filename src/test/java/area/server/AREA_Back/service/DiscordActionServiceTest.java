package area.server.AREA_Back.service;

import area.server.AREA_Back.repository.UserOAuthIdentityRepository;
import area.server.AREA_Back.service.Area.Services.DiscordActionService;
import area.server.AREA_Back.service.Auth.TokenEncryptionService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

@ExtendWith(MockitoExtension.class)
class DiscordActionServiceTest {

    @Mock
    private UserOAuthIdentityRepository userOAuthIdentityRepository;

    @Mock
    private TokenEncryptionService tokenEncryptionService;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private area.server.AREA_Back.service.Auth.OAuthTokenRefreshService oauthTokenRefreshService;

    private SimpleMeterRegistry meterRegistry;
    private DiscordActionService discordActionService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        discordActionService = new DiscordActionService(
            userOAuthIdentityRepository,
            tokenEncryptionService,
            restTemplate,
            meterRegistry,
            oauthTokenRefreshService
        );

        try {
            var initMethod = DiscordActionService.class.getDeclaredMethod("init");
            initMethod.setAccessible(true);
            initMethod.invoke(discordActionService);
        } catch (Exception e) {
            fail("Failed to initialize metrics: " + e.getMessage());
        }
    }

    @Test
    void testServiceInitialization() {
        assertNotNull(discordActionService);
        assertNotNull(meterRegistry);
    }

    @Test
    void testMetricsAreRegistered() {
        assertNotNull(meterRegistry.find("discord_actions_executed_total").counter());
        assertNotNull(meterRegistry.find("discord_actions_failed_total").counter());
    }
}
