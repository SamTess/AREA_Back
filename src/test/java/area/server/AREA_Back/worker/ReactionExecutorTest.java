package area.server.AREA_Back.worker;

import area.server.AREA_Back.dto.ExecutionResult;
import area.server.AREA_Back.entity.Execution;
import area.server.AREA_Back.entity.ActionInstance;
import area.server.AREA_Back.entity.ActionDefinition;
import area.server.AREA_Back.entity.Service;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.entity.enums.ExecutionStatus;
import area.server.AREA_Back.service.Area.Services.DiscordActionService;
import area.server.AREA_Back.service.Area.Services.GitHubActionService;
import area.server.AREA_Back.service.Area.Services.GoogleActionService;
import area.server.AREA_Back.service.Area.Services.NotionActionService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;

@ExtendWith(MockitoExtension.class)
class ReactionExecutorTest {

    @Mock
    private RetryManager retryManager;

    @Mock
    private GitHubActionService gitHubActionService;

    @Mock
    private GoogleActionService googleActionService;

    @Mock
    private DiscordActionService discordActionService;

    @Mock
    private area.server.AREA_Back.service.Area.Services.SlackActionService slackActionService;

    @Mock
    private area.server.AREA_Back.service.Area.Services.SpotifyActionService spotifyActionService;

    @Mock
    private NotionActionService notionActionService;

    private SimpleMeterRegistry meterRegistry;

    private ReactionExecutor reactionExecutor;

    private Execution execution;
    private ActionInstance actionInstance;
    private ActionDefinition actionDefinition;
    private Service service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();

        // Create ReactionExecutor manually with all dependencies
        reactionExecutor = new ReactionExecutor(
            retryManager,
            gitHubActionService,
            googleActionService,
            discordActionService,
            slackActionService,
            spotifyActionService,
            notionActionService,
            meterRegistry
        );

        service = new Service();
        service.setKey("github");
        service.setName("GitHub Service");

        actionDefinition = new ActionDefinition();
        actionDefinition.setKey("create_issue");
        actionDefinition.setName("Create Issue");
        actionDefinition.setIsExecutable(true);
        actionDefinition.setService(service);

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("test@example.com");

        actionInstance = new ActionInstance();
        actionInstance.setId(UUID.randomUUID());
        actionInstance.setActionDefinition(actionDefinition);
        actionInstance.setUser(user);
        actionInstance.setParams(Map.of(
            "repository", "owner/repo",
            "title", "Test Issue"
        ));

        execution = new Execution();
        execution.setId(UUID.randomUUID());
        execution.setActionInstance(actionInstance);
        execution.setStatus(ExecutionStatus.QUEUED);
        execution.setAttempt(0);
        execution.setInputPayload(Map.of("data", "test"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void executeReactionSuccess() {
        // Given
        Map<String, Object> mockResult = new HashMap<>();
        mockResult.put("issue_number", 42);
        mockResult.put("html_url", "https://github.com/owner/repo/issues/42");
        mockResult.put("status", "created");
        
        when(gitHubActionService.executeGitHubAction(
            anyString(), any(Map.class), any(Map.class), any(UUID.class)
        )).thenReturn(mockResult);

        // When
        ExecutionResult result = reactionExecutor.executeReaction(execution);

        // Then
        assertNotNull(result);
        assertEquals(execution.getId(), result.getExecutionId());
        assertEquals(ExecutionStatus.OK, result.getStatus());
        assertNotNull(result.getOutputPayload());
        assertNotNull(result.getStartedAt());
        assertNotNull(result.getFinishedAt());
        assertTrue(result.getDurationMs() >= 0);  // Allow 0ms for very fast execution
        assertFalse(result.isShouldRetry());
    }

    @Test
    @SuppressWarnings("unchecked")
    void executeReactionGitHubService() {
        // Given
        Map<String, Object> mockResult = new HashMap<>();
        mockResult.put("issue_number", 42);
        mockResult.put("html_url", "https://github.com/owner/repo/issues/42");
        mockResult.put("status", "created");
        
        when(gitHubActionService.executeGitHubAction(
            anyString(), any(Map.class), any(Map.class), any(UUID.class)
        )).thenReturn(mockResult);

        // When
        ExecutionResult result = reactionExecutor.executeReaction(execution);

        // Then
        assertEquals(ExecutionStatus.OK, result.getStatus());
        assertNotNull(result.getOutputPayload());
        assertEquals("github", result.getOutputPayload().get("type"));
        assertEquals("create_issue", result.getOutputPayload().get("action"));
        assertEquals(42, result.getOutputPayload().get("issue_number"));
        assertEquals("https://github.com/owner/repo/issues/42", result.getOutputPayload().get("html_url"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void executeReactionSlackService() {
        // Given
        service.setKey("slack");
        actionDefinition.setKey("send_message");
        actionInstance.setParams(Map.of(
            "channel", "#general",
            "text", "Test message"
        ));

        Map<String, Object> mockResult = new HashMap<>();
        mockResult.put("success", true);
        mockResult.put("channel", "#general");
        mockResult.put("ts", "1234567890.123456");

        when(slackActionService.executeSlackAction(
            anyString(), any(Map.class), any(Map.class), any(UUID.class)
        )).thenReturn(mockResult);

        // When
        ExecutionResult result = reactionExecutor.executeReaction(execution);

        // Then
        assertEquals(ExecutionStatus.OK, result.getStatus());
        assertNotNull(result.getOutputPayload());
        assertTrue((Boolean) result.getOutputPayload().get("success"));
        assertEquals("#general", result.getOutputPayload().get("channel"));
        assertNotNull(result.getOutputPayload().get("ts"));
    }

    @Test
    void executeReactionNonExecutableAction() {
        // Given
        actionDefinition.setIsExecutable(false);
        when(retryManager.shouldRetry(anyInt(), any())).thenReturn(false);

        // When
        ExecutionResult result = reactionExecutor.executeReaction(execution);

        // Then
        assertNotNull(result);
        assertEquals(ExecutionStatus.FAILED, result.getStatus());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("not executable"));
        assertFalse(result.isShouldRetry());
    }

    @Test
    void executeReactionWithRetry() {
        // Given
        actionDefinition.setIsExecutable(false); // This will cause an exception
        when(retryManager.shouldRetry(anyInt(), any())).thenReturn(true);
        when(retryManager.calculateNextRetryTime(anyInt())).thenReturn(java.time.LocalDateTime.now().plusMinutes(1));

        // When
        ExecutionResult result = reactionExecutor.executeReaction(execution);

        // Then
        assertEquals(ExecutionStatus.RETRY, result.getStatus());
        assertTrue(result.isShouldRetry());
        assertNotNull(result.getNextRetryAt());
        assertNotNull(result.getErrorMessage());
        assertNotNull(result.getError());
    }

    @Test
    void executeReactionWithoutRetry() {
        // Given
        actionDefinition.setIsExecutable(false); // This will cause an exception
        when(retryManager.shouldRetry(anyInt(), any())).thenReturn(false);

        // When
        ExecutionResult result = reactionExecutor.executeReaction(execution);

        // Then
        assertEquals(ExecutionStatus.FAILED, result.getStatus());
        assertFalse(result.isShouldRetry());
        assertNull(result.getNextRetryAt());
        assertNotNull(result.getErrorMessage());
        assertNotNull(result.getError());
    }

    @Test
    void executeReactionUnsupportedService() {
        // Given
        service.setKey("unknown");
        actionDefinition.setKey("generic_action");
        when(retryManager.shouldRetry(anyInt(), any())).thenReturn(false);

        // When
        ExecutionResult result = reactionExecutor.executeReaction(execution);

        // Then
        assertEquals(ExecutionStatus.FAILED, result.getStatus());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("Service not supported"));
        assertFalse(result.isShouldRetry());
    }
}