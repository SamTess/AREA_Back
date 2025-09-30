package area.server.AREA_Back.worker;

import area.server.AREA_Back.config.RedisConfig;
import area.server.AREA_Back.dto.ExecutionResult;
import area.server.AREA_Back.entity.ActionInstance;
import area.server.AREA_Back.entity.Execution;
import area.server.AREA_Back.entity.enums.ExecutionStatus;
import area.server.AREA_Back.service.ExecutionService;
import area.server.AREA_Back.service.RedisEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AreaReactionWorkerTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private RedisEventService redisEventService;

    @Mock
    private ExecutionService executionService;

    @Mock
    private ReactionExecutor reactionExecutor;

    @Mock
    private StreamOperations<String, Object, Object> streamOperations;

    @InjectMocks
    private AreaReactionWorker areaReactionWorker;

    private Execution testExecution;
    private ActionInstance actionInstance;
    private ExecutionResult executionResult;

    @BeforeEach
    void setUp() {
        // Setup test data
        actionInstance = new ActionInstance();
        actionInstance.setId(UUID.randomUUID());

        testExecution = new Execution();
        testExecution.setId(UUID.randomUUID());
        testExecution.setActionInstance(actionInstance);
        testExecution.setStatus(ExecutionStatus.QUEUED);
        testExecution.setAttempt(1);
        testExecution.setStartedAt(LocalDateTime.now());

        executionResult = ExecutionResult.success(
            testExecution.getId(),
            Map.of("result", "success"),
            testExecution.getStartedAt()
        );
    }

    @Test
    void testProcessExecution_Success() {
        // Given
        when(reactionExecutor.executeReaction(testExecution)).thenReturn(executionResult);

        // When
        areaReactionWorker.processExecution(testExecution);

        // Then
        verify(executionService).markExecutionAsStarted(testExecution.getId());
        verify(reactionExecutor).executeReaction(testExecution);
        verify(executionService).updateExecutionWithResult(executionResult);
    }

    @Test
    void testProcessExecution_WithException() {
        // Given
        RuntimeException exception = new RuntimeException("Test exception");
        when(reactionExecutor.executeReaction(testExecution)).thenThrow(exception);

        // When
        areaReactionWorker.processExecution(testExecution);

        // Then
        verify(executionService).markExecutionAsStarted(testExecution.getId());
        verify(reactionExecutor).executeReaction(testExecution);

        ArgumentCaptor<ExecutionResult> resultCaptor = ArgumentCaptor.forClass(ExecutionResult.class);
        verify(executionService).updateExecutionWithResult(resultCaptor.capture());

        ExecutionResult capturedResult = resultCaptor.getValue();
        assertEquals(testExecution.getId(), capturedResult.getExecutionId());
        assertEquals(ExecutionStatus.FAILED, capturedResult.getStatus());
        assertTrue(capturedResult.getErrorMessage().contains("Worker processing error"));
    }

    @Test
    void testProcessExecution_WithUpdateException() {
        // Given
        RuntimeException executionException = new RuntimeException("Execution error");
        RuntimeException updateException = new RuntimeException("Update error");

        when(reactionExecutor.executeReaction(testExecution)).thenThrow(executionException);
        doThrow(updateException).when(executionService).updateExecutionWithResult(any(ExecutionResult.class));

        // When & Then - should not throw exception
        assertDoesNotThrow(() -> areaReactionWorker.processExecution(testExecution));

        verify(executionService).markExecutionAsStarted(testExecution.getId());
        verify(executionService).updateExecutionWithResult(any(ExecutionResult.class));
    }

    @Test
    void testCleanupTimedOutExecutions_WithTimedOutExecutions() {
        // Given
        List<Execution> timedOutExecutions = Arrays.asList(testExecution);

        when(executionService.getTimedOutExecutions(any(LocalDateTime.class))).thenReturn(timedOutExecutions);

        // When
        areaReactionWorker.cleanupTimedOutExecutions();

        // Then
        verify(executionService).getTimedOutExecutions(any(LocalDateTime.class));

        ArgumentCaptor<ExecutionResult> resultCaptor = ArgumentCaptor.forClass(ExecutionResult.class);
        verify(executionService).updateExecutionWithResult(resultCaptor.capture());

        ExecutionResult capturedResult = resultCaptor.getValue();
        assertEquals(testExecution.getId(), capturedResult.getExecutionId());
        assertEquals(ExecutionStatus.FAILED, capturedResult.getStatus());
        assertEquals("Execution timed out", capturedResult.getErrorMessage());
        assertFalse(capturedResult.isShouldRetry());
    }

    @Test
    void testCleanupTimedOutExecutions_NoTimedOutExecutions() {
        // Given
        when(executionService.getTimedOutExecutions(any(LocalDateTime.class))).thenReturn(Collections.emptyList());

        // When
        areaReactionWorker.cleanupTimedOutExecutions();

        // Then
        verify(executionService).getTimedOutExecutions(any(LocalDateTime.class));
        verify(executionService, never()).updateExecutionWithResult(any(ExecutionResult.class));
    }

    @Test
    void testCleanupTimedOutExecutions_WithException() {
        // Given
        when(executionService.getTimedOutExecutions(any(LocalDateTime.class)))
            .thenThrow(new RuntimeException("Database error"));

        // When & Then - should not throw exception
        assertDoesNotThrow(() -> areaReactionWorker.cleanupTimedOutExecutions());

        verify(executionService).getTimedOutExecutions(any(LocalDateTime.class));
    }

    @Test
    void testProcessEventRecord_Success() {
        // Given
        String executionId = testExecution.getId().toString();
        Map<Object, Object> recordValues = Map.of("executionId", executionId);
        MapRecord<String, Object, Object> record = MapRecord.create(
            RedisConfig.AREAS_EVENTS_STREAM,
            recordValues
        ).withId(RecordId.of("1234567890123-0"));

        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        when(executionService.getQueuedExecutions()).thenReturn(Arrays.asList(testExecution));
        when(reactionExecutor.executeReaction(testExecution)).thenReturn(executionResult);

        // When
        areaReactionWorker.processEventRecord(record);

        // Then
        verify(executionService).getQueuedExecutions();
        verify(executionService).markExecutionAsStarted(testExecution.getId());
        verify(reactionExecutor).executeReaction(testExecution);
        verify(streamOperations).acknowledge(
            RedisConfig.AREAS_EVENTS_STREAM,
            RedisConfig.AREAS_CONSUMER_GROUP,
            record.getId()
        );
    }

    @Test
    void testProcessEventRecord_ExecutionNotFound() {
        // Given
        String executionId = UUID.randomUUID().toString();
        Map<Object, Object> recordValues = Map.of("executionId", executionId);
        MapRecord<String, Object, Object> record = MapRecord.create(
            RedisConfig.AREAS_EVENTS_STREAM,
            recordValues
        ).withId(RecordId.of("1234567890123-0"));

        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        when(executionService.getQueuedExecutions()).thenReturn(Collections.emptyList());

        // When
        areaReactionWorker.processEventRecord(record);

        // Then
        verify(executionService).getQueuedExecutions();
        verify(executionService, never()).markExecutionAsStarted(any(UUID.class));
        verify(streamOperations).acknowledge(
            RedisConfig.AREAS_EVENTS_STREAM,
            RedisConfig.AREAS_CONSUMER_GROUP,
            record.getId()
        );
    }

    @Test
    void testProcessEventRecord_NoExecutionId() {
        // Given
        Map<Object, Object> recordValues = Map.of("otherField", "value");
        MapRecord<String, Object, Object> record = MapRecord.create(
            RedisConfig.AREAS_EVENTS_STREAM,
            recordValues
        ).withId(RecordId.of("1234567890123-0"));

        when(redisTemplate.opsForStream()).thenReturn(streamOperations);

        // When
        areaReactionWorker.processEventRecord(record);

        // Then
        verify(executionService, never()).getQueuedExecutions();
        verify(streamOperations).acknowledge(
            RedisConfig.AREAS_EVENTS_STREAM,
            RedisConfig.AREAS_CONSUMER_GROUP,
            record.getId()
        );
    }

    @Test
    void testProcessRetryExecutions_WithRetryExecutions() {
        // Given
        List<Execution> retryExecutions = Arrays.asList(testExecution);
        when(executionService.getExecutionsReadyForRetry(any(LocalDateTime.class))).thenReturn(retryExecutions);
        when(reactionExecutor.executeReaction(testExecution)).thenReturn(executionResult);

        // When
        areaReactionWorker.processRetryExecutions();

        // Then
        verify(executionService).getExecutionsReadyForRetry(any(LocalDateTime.class));
        verify(executionService).markExecutionAsStarted(testExecution.getId());
        verify(reactionExecutor).executeReaction(testExecution);
        verify(executionService).updateExecutionWithResult(executionResult);
    }

    @Test
    void testProcessRetryExecutions_NoRetryExecutions() {
        // Given
        when(executionService.getExecutionsReadyForRetry(any(LocalDateTime.class)))
            .thenReturn(Collections.emptyList());

        // When
        areaReactionWorker.processRetryExecutions();

        // Then
        verify(executionService).getExecutionsReadyForRetry(any(LocalDateTime.class));
        verify(executionService, never()).markExecutionAsStarted(any(UUID.class));
        verify(reactionExecutor, never()).executeReaction(any(Execution.class));
    }

    @Test
    void testProcessRetryExecutions_WithException() {
        // Given
        when(executionService.getExecutionsReadyForRetry(any(LocalDateTime.class)))
            .thenThrow(new RuntimeException("Database error"));

        // When & Then - should not throw exception
        assertDoesNotThrow(() -> areaReactionWorker.processRetryExecutions());

        verify(executionService).getExecutionsReadyForRetry(any(LocalDateTime.class));
    }

    @Test
    void testProcessQueuedExecutions_WithQueuedExecutions() {
        // Given
        List<Execution> queuedExecutions = Arrays.asList(testExecution);
        when(executionService.getQueuedExecutions()).thenReturn(queuedExecutions);
        when(reactionExecutor.executeReaction(testExecution)).thenReturn(executionResult);

        // When
        areaReactionWorker.processQueuedExecutions();

        // Then
        verify(executionService).getQueuedExecutions();
        verify(executionService).markExecutionAsStarted(testExecution.getId());
        verify(reactionExecutor).executeReaction(testExecution);
        verify(executionService).updateExecutionWithResult(executionResult);
    }

    @Test
    void testProcessQueuedExecutions_NoQueuedExecutions() {
        // Given
        when(executionService.getQueuedExecutions()).thenReturn(Collections.emptyList());

        // When
        areaReactionWorker.processQueuedExecutions();

        // Then
        verify(executionService).getQueuedExecutions();
        verify(executionService, never()).markExecutionAsStarted(any(UUID.class));
        verify(reactionExecutor, never()).executeReaction(any(Execution.class));
    }

    @Test
    void testProcessQueuedExecutions_WithException() {
        // Given
        when(executionService.getQueuedExecutions()).thenThrow(new RuntimeException("Database error"));

        // When & Then - should not throw exception
        assertDoesNotThrow(() -> areaReactionWorker.processQueuedExecutions());

        verify(executionService).getQueuedExecutions();
    }

    @Test
    void testShutdown() {
        // When
        areaReactionWorker.shutdown();

        // Then
        Map<String, Object> status = areaReactionWorker.getWorkerStatus();
        assertFalse((Boolean) status.get("running"));
    }

    @Test
    void testGetWorkerStatus() {
        // Given
        Map<String, Object> streamInfo = Map.of("length", 10, "groups", 1);
        when(redisEventService.getStreamInfo()).thenReturn(streamInfo);

        // When
        Map<String, Object> status = areaReactionWorker.getWorkerStatus();

        // Then
        assertNotNull(status);
        assertTrue(status.containsKey("consumerName"));
        assertTrue(status.containsKey("running"));
        assertTrue(status.containsKey("streamInfo"));
        assertTrue((Boolean) status.get("running")); // Should be true by default
        assertEquals(streamInfo, status.get("streamInfo"));
        assertNotNull(status.get("consumerName"));
        assertTrue(status.get("consumerName").toString().startsWith("worker-"));
    }

    @Test
    void testInitialize() {
        // When
        areaReactionWorker.initialize();

        // Then
        verify(redisEventService).initializeStream();
    }

    @Test
    void testLogStatistics() {
        // Given
        Map<String, Long> stats = Map.of(
            "queued", 5L,
            "running", 2L,
            "ok", 10L,
            "retry", 1L,
            "failed", 2L,
            "canceled", 0L
        );
        when(executionService.getExecutionStatistics()).thenReturn(stats);

        // When & Then - should not throw exception
        assertDoesNotThrow(() -> areaReactionWorker.logStatistics());

        verify(executionService).getExecutionStatistics();
    }

    @Test
    void testLogStatistics_WithException() {
        // Given
        when(executionService.getExecutionStatistics()).thenThrow(new RuntimeException("Stats error"));

        // When & Then - should not throw exception
        assertDoesNotThrow(() -> areaReactionWorker.logStatistics());

        verify(executionService).getExecutionStatistics();
    }
}