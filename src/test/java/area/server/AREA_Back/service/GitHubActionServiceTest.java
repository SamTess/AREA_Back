package area.server.AREA_Back.service;

import area.server.AREA_Back.repository.UserOAuthIdentityRepository;
import area.server.AREA_Back.repository.UserRepository;
import area.server.AREA_Back.service.Area.Services.GitHubActionService;
import area.server.AREA_Back.service.Auth.ServiceAccountService;
import area.server.AREA_Back.service.Auth.TokenEncryptionService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class GitHubActionServiceTest {

    @Mock
    private UserOAuthIdentityRepository userOAuthIdentityRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenEncryptionService tokenEncryptionService;

    @Mock
    private ServiceAccountService serviceAccountService;

    private SimpleMeterRegistry meterRegistry;
    private GitHubActionService gitHubActionService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        gitHubActionService = new GitHubActionService(
            userOAuthIdentityRepository,
            userRepository,
            tokenEncryptionService,
            serviceAccountService,
            meterRegistry
        );
        // Manually initialize metrics since @PostConstruct won't run in tests
        gitHubActionService.init();
    }

    @Test
    void testServiceInitialization() {
        // Test that the service is properly initialized with metrics
        assertNotNull(gitHubActionService);
        assertNotNull(meterRegistry);
    }

    @Test
    void testExecuteGitHubActionWithInvalidActionKey() {
        // Given
        String invalidActionKey = "invalid_action";
        Map<String, Object> inputPayload = Map.of();
        Map<String, Object> actionParams = Map.of();
        UUID userId = UUID.randomUUID();

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            gitHubActionService.executeGitHubAction(invalidActionKey, inputPayload, actionParams, userId);
        });
        assertTrue(exception.getMessage().contains("No GitHub token found"));
    }

    @Test
    void testExecuteGitHubActionWithNullToken() {
        // Given
        String actionKey = "create_issue";
        Map<String, Object> inputPayload = Map.of();
        Map<String, Object> actionParams = Map.of(
            "repository", "owner/repo",
            "title", "Test Issue",
            "body", "Test body"
        );
        UUID userId = UUID.randomUUID();

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            gitHubActionService.executeGitHubAction(actionKey, inputPayload, actionParams, userId);
        });
        assertTrue(exception.getMessage().contains("GitHub action execution failed"));
    }

    @Test
    void testCheckGitHubEventsWithInvalidActionKey() {
        // Given
        String invalidActionKey = "invalid_event";
        Map<String, Object> actionParams = Map.of();
        UUID userId = UUID.randomUUID();

        // When
        var result = gitHubActionService.checkGitHubEvents(invalidActionKey, actionParams, userId, null);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testCheckGitHubEventsWithNullToken() {
        // Given
        String actionKey = "new_issue";
        Map<String, Object> actionParams = Map.of("repository", "owner/repo");
        UUID userId = UUID.randomUUID();

        // When
        var result = gitHubActionService.checkGitHubEvents(actionKey, actionParams, userId, null);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}