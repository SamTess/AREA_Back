package area.server.AREA_Back.service;

import area.server.AREA_Back.dto.OAuthLoginRequest;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.repository.UserOAuthIdentityRepository;
import area.server.AREA_Back.repository.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class OAuthGithubServiceTest {

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

    private SimpleMeterRegistry meterRegistry;
    private OAuthGithubService oauthGithubService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        oauthGithubService = new OAuthGithubService(
            "test-client-id",
            "test-client-secret",
            "http://localhost:3000",
            jwtService,
            meterRegistry,
            redisTokenService,
            passwordEncoder,
            authService
        );
        // Manually initialize metrics since @PostConstruct won't run in tests
        try {
            var initMetricsMethod = OAuthGithubService.class.getDeclaredMethod("initMetrics");
            initMetricsMethod.setAccessible(true);
            initMetricsMethod.invoke(oauthGithubService);
        } catch (Exception e) {
            fail("Failed to initialize metrics: " + e.getMessage());
        }
        // Set the repositories via reflection since they're private
        try {
            var userRepoField = OAuthGithubService.class.getDeclaredField("userRepository");
            userRepoField.setAccessible(true);
            userRepoField.set(oauthGithubService, userRepository);

            var oauthRepoField = OAuthGithubService.class.getDeclaredField("userOAuthIdentityRepository");
            oauthRepoField.setAccessible(true);
            oauthRepoField.set(oauthGithubService, userOAuthIdentityRepository);

            var tokenEncryptionField = OAuthGithubService.class.getDeclaredField("tokenEncryptionService");
            tokenEncryptionField.setAccessible(true);
            tokenEncryptionField.set(oauthGithubService, tokenEncryptionService);
        } catch (Exception e) {
            fail("Failed to set up test dependencies: " + e.getMessage());
        }
    }

    @Test
    void testServiceInitialization() {
        // Test that the service is properly initialized with metrics
        assertNotNull(oauthGithubService);
        assertNotNull(meterRegistry);
    }

    @Test
    void testAuthenticateWithInvalidCode() {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode("invalid-code");

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            oauthGithubService.authenticate(request, httpServletResponse);
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
            oauthGithubService.linkToExistingUser(existingUser, "invalid-code");
        });
    }
}