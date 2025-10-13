package area.server.AREA_Back.worker;

import area.server.AREA_Back.dto.ExecutionResult;
import area.server.AREA_Back.entity.Execution;
import area.server.AREA_Back.entity.ActionInstance;
import area.server.AREA_Back.entity.ActionDefinition;
import area.server.AREA_Back.entity.Service;
import area.server.AREA_Back.entity.enums.ExecutionStatus;
import area.server.AREA_Back.service.GitHubActionService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

@ExtendWith(MockitoExtension.class)
class ReactionExecutorTest {

    @Mock
    private RetryManager retryManager;

    @Mock
    private GitHubActionService gitHubActionService;

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
            meterRegistry
        );

        service = new Service();
        service.setKey("email");
        service.setName("Email Service");

        actionDefinition = new ActionDefinition();
        actionDefinition.setKey("send_email");
        actionDefinition.setName("Send Email");
        actionDefinition.setIsExecutable(true);
        actionDefinition.setService(service);

        actionInstance = new ActionInstance();
        actionInstance.setId(UUID.randomUUID());
        actionInstance.setActionDefinition(actionDefinition);
        actionInstance.setParams(Map.of(
            "to", "test@example.com",
            "subject", "Test Subject"
        ));

        execution = new Execution();
        execution.setId(UUID.randomUUID());
        execution.setActionInstance(actionInstance);
        execution.setStatus(ExecutionStatus.QUEUED);
        execution.setAttempt(0);
        execution.setInputPayload(Map.of("data", "test"));
    }

    @Test
    void executeReactionSuccess() {
        // When
        ExecutionResult result = reactionExecutor.executeReaction(execution);

        // Then
        assertNotNull(result);
        assertEquals(execution.getId(), result.getExecutionId());
        assertEquals(ExecutionStatus.OK, result.getStatus());
        assertNotNull(result.getOutputPayload());
        assertNotNull(result.getStartedAt());
        assertNotNull(result.getFinishedAt());
        assertTrue(result.getDurationMs() > 0);
        assertFalse(result.isShouldRetry());
    }

    @Test
    void executeReactionEmailService() {
        // When
        ExecutionResult result = reactionExecutor.executeReaction(execution);

        // Then
        assertEquals(ExecutionStatus.OK, result.getStatus());
        assertNotNull(result.getOutputPayload());
        assertEquals("email", result.getOutputPayload().get("type"));
        assertEquals("send_email", result.getOutputPayload().get("action"));
        assertEquals("test@example.com", result.getOutputPayload().get("to"));
        assertEquals("Test Subject", result.getOutputPayload().get("subject"));
        assertEquals("sent", result.getOutputPayload().get("status"));
    }

    @Test
    void executeReactionSlackService() {
        // Given
        service.setKey("slack");
        actionDefinition.setKey("post_message");
        actionInstance.setParams(Map.of(
            "channel", "#general",
            "message", "Test message"
        ));

        // When
        ExecutionResult result = reactionExecutor.executeReaction(execution);

        // Then
        assertEquals(ExecutionStatus.OK, result.getStatus());
        assertNotNull(result.getOutputPayload());
        assertEquals("slack", result.getOutputPayload().get("type"));
        assertEquals("post_message", result.getOutputPayload().get("action"));
        assertEquals("#general", result.getOutputPayload().get("channel"));
        assertEquals("Test message", result.getOutputPayload().get("message"));
        assertEquals("posted", result.getOutputPayload().get("status"));
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
    void executeReactionGenericService() {
        // Given
        service.setKey("unknown");
        actionDefinition.setKey("generic_action");

        // When
        ExecutionResult result = reactionExecutor.executeReaction(execution);

        // Then
        assertEquals(ExecutionStatus.OK, result.getStatus());
        assertNotNull(result.getOutputPayload());
        assertEquals("generic", result.getOutputPayload().get("type"));
        assertEquals("unknown", result.getOutputPayload().get("service"));
        assertEquals("generic_action", result.getOutputPayload().get("action"));
        assertEquals("executed", result.getOutputPayload().get("status"));
        assertEquals("success", result.getOutputPayload().get("result"));
    }
}