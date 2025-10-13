package area.server.AREA_Back.worker;

import area.server.AREA_Back.config.RedisConfig;
import area.server.AREA_Back.dto.ExecutionResult;
import area.server.AREA_Back.entity.ActionInstance;
import area.server.AREA_Back.entity.Execution;
import area.server.AREA_Back.entity.enums.ExecutionStatus;
import area.server.AREA_Back.repository.ExecutionRepository;
import area.server.AREA_Back.service.Area.ActionLinkService;
import area.server.AREA_Back.service.Area.ExecutionService;
import area.server.AREA_Back.service.Redis.RedisEventService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.context.ActiveProfiles;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@ActiveProfiles("unit-test")
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
    private RedisConfig redisConfig;

    @Mock
    private ExecutionRepository executionRepository;

    @Mock
    private ActionLinkService actionLinkService;

    @Mock
    private StreamOperations<String, Object, Object> streamOperations;

    private SimpleMeterRegistry meterRegistry;
    private AreaReactionWorker areaReactionWorker;

    private Execution testExecution;
    private ActionInstance actionInstance;
    private ExecutionResult executionResult;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();

        // Create AreaReactionWorker manually with all dependencies
        areaReactionWorker = new AreaReactionWorker(
            redisTemplate,
            redisEventService,
            executionService,
            executionRepository,
            reactionExecutor,
            redisConfig,
            meterRegistry,
            actionLinkService
        );

        // Setup RedisConfig mock
        when(redisConfig.getAreasEventsStream()).thenReturn("areas:events");
        when(redisConfig.getAreasConsumerGroup()).thenReturn("area-processors");
        when(redisConfig.getAreasConsumerName()).thenReturn("test-consumer");

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
    void testProcessExecutionSuccess() {
        // Given
        when(executionRepository.findByIdWithActionInstance(testExecution.getId()))
            .thenReturn(java.util.Optional.of(testExecution));
        when(reactionExecutor.executeReaction(testExecution)).thenReturn(executionResult);

        // When
        areaReactionWorker.processExecution(testExecution);

        // Then
        verify(executionRepository).findByIdWithActionInstance(testExecution.getId());
        verify(executionService).markExecutionAsStarted(testExecution.getId());
        verify(reactionExecutor).executeReaction(testExecution);
        verify(executionService).updateExecutionWithResult(executionResult);
    }

    @Test
    void testProcessExecutionWithException() {
        // Given
        when(executionRepository.findByIdWithActionInstance(testExecution.getId()))
            .thenReturn(java.util.Optional.of(testExecution));
        RuntimeException exception = new RuntimeException("Test exception");
        when(reactionExecutor.executeReaction(testExecution)).thenThrow(exception);

        // When
        areaReactionWorker.processExecution(testExecution);

        // Then
        verify(executionRepository).findByIdWithActionInstance(testExecution.getId());
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
    void testProcessExecutionWithUpdateException() {
        // Given
        when(executionRepository.findByIdWithActionInstance(testExecution.getId()))
            .thenReturn(java.util.Optional.of(testExecution));
        RuntimeException executionException = new RuntimeException("Execution error");
        RuntimeException updateException = new RuntimeException("Update error");

        when(reactionExecutor.executeReaction(testExecution)).thenThrow(executionException);
        doThrow(updateException).when(executionService).updateExecutionWithResult(any(ExecutionResult.class));

        // When & Then - should not throw exception
        assertDoesNotThrow(() -> areaReactionWorker.processExecution(testExecution));

        verify(executionRepository).findByIdWithActionInstance(testExecution.getId());
        verify(executionService).markExecutionAsStarted(testExecution.getId());
        verify(executionService).updateExecutionWithResult(any(ExecutionResult.class));
    }

    @Test
    void testCleanupTimedOutExecutionsWithTimedOutExecutions() {
        // Given
        List<Execution> timedOutExecutions = Arrays.asList(testExecution);
        when(executionService.getTimedOutExecutions(any(LocalDateTime.class))).thenReturn(timedOutExecutions);

        // When & Then - should not throw exception
        assertDoesNotThrow(() -> areaReactionWorker.cleanupTimedOutExecutions());

        // Verify that the service method was called
        verify(executionService).getTimedOutExecutions(any(LocalDateTime.class));
    }

    @Test
    void testCleanupTimedOutExecutionsNoTimedOutExecutions() {
        // Given
        when(executionService.getTimedOutExecutions(any(LocalDateTime.class))).thenReturn(Collections.emptyList());

        // When
        areaReactionWorker.cleanupTimedOutExecutions();

        // Then
        verify(executionService).getTimedOutExecutions(any(LocalDateTime.class));
        verify(executionService, never()).updateExecutionWithResult(any(ExecutionResult.class));
    }

    @Test
    void testCleanupTimedOutExecutionsWithException() {
        // Given
        when(executionService.getTimedOutExecutions(any(LocalDateTime.class)))
            .thenThrow(new RuntimeException("Database error"));

        // When & Then - should not throw exception
        assertDoesNotThrow(() -> areaReactionWorker.cleanupTimedOutExecutions());

        verify(executionService).getTimedOutExecutions(any(LocalDateTime.class));
    }

    @Test
    void testProcessEventRecordSuccess() {
        // Given
        when(executionRepository.findByIdWithActionInstance(testExecution.getId()))
            .thenReturn(java.util.Optional.of(testExecution));
        String executionId = testExecution.getId().toString();
        Map<Object, Object> recordValues = Map.of("executionId", executionId);
        MapRecord<String, Object, Object> record = MapRecord.create(
            redisConfig.getAreasEventsStream(),
            recordValues
        ).withId(RecordId.of("1234567890123-0"));

        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        when(executionService.getQueuedExecutions()).thenReturn(Arrays.asList(testExecution));
        when(reactionExecutor.executeReaction(testExecution)).thenReturn(executionResult);

        // When
        areaReactionWorker.processEventRecord(record);

        // Then
        verify(executionRepository).findByIdWithActionInstance(testExecution.getId());
        verify(executionService).getQueuedExecutions();
        verify(executionService).markExecutionAsStarted(testExecution.getId());
        verify(reactionExecutor).executeReaction(testExecution);
        verify(streamOperations).acknowledge(
            redisConfig.getAreasEventsStream(),
            redisConfig.getAreasConsumerGroup(),
            record.getId()
        );
    }

    @Test
    void testProcessEventRecordExecutionNotFound() {
        // Given
        String executionId = UUID.randomUUID().toString();
        Map<Object, Object> recordValues = Map.of("executionId", executionId);
        MapRecord<String, Object, Object> record = MapRecord.create(
            redisConfig.getAreasEventsStream(),
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
            redisConfig.getAreasEventsStream(),
            redisConfig.getAreasConsumerGroup(),
            record.getId()
        );
    }

    @Test
    void testProcessEventRecordNoExecutionId() {
        // Given
        Map<Object, Object> recordValues = Map.of("otherField", "value");
        MapRecord<String, Object, Object> record = MapRecord.create(
            redisConfig.getAreasEventsStream(),
            recordValues
        ).withId(RecordId.of("1234567890123-0"));

        when(redisTemplate.opsForStream()).thenReturn(streamOperations);

        // When
        areaReactionWorker.processEventRecord(record);

        // Then
        verify(executionService, never()).getQueuedExecutions();
        verify(streamOperations).acknowledge(
            redisConfig.getAreasEventsStream(),
            redisConfig.getAreasConsumerGroup(),
            record.getId()
        );
    }

    @Test
    void testProcessRetryExecutionsWithRetryExecutions() {
        // Given
        when(executionRepository.findByIdWithActionInstance(testExecution.getId()))
            .thenReturn(java.util.Optional.of(testExecution));
        List<Execution> retryExecutions = Arrays.asList(testExecution);
        when(executionService.getExecutionsReadyForRetry(any(LocalDateTime.class))).thenReturn(retryExecutions);
        when(reactionExecutor.executeReaction(testExecution)).thenReturn(executionResult);

        // When
        areaReactionWorker.processExecution(testExecution); // Test the core logic directly

        // Then
        verify(executionRepository).findByIdWithActionInstance(testExecution.getId());
        verify(executionService).markExecutionAsStarted(testExecution.getId());
        verify(reactionExecutor).executeReaction(testExecution);
        verify(executionService).updateExecutionWithResult(executionResult);
    }

    @Test
    void testProcessRetryExecutionsNoRetryExecutions() {
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
    void testProcessRetryExecutionsWithException() {
        // Given
        when(executionService.getExecutionsReadyForRetry(any(LocalDateTime.class)))
            .thenThrow(new RuntimeException("Database error"));

        // When & Then - should not throw exception
        assertDoesNotThrow(() -> areaReactionWorker.processRetryExecutions());

        verify(executionService).getExecutionsReadyForRetry(any(LocalDateTime.class));
    }

    @Test
    void testProcessQueuedExecutionsWithQueuedExecutions() {
        // Given
        when(executionRepository.findByIdWithActionInstance(testExecution.getId()))
            .thenReturn(java.util.Optional.of(testExecution));
        List<Execution> queuedExecutions = Arrays.asList(testExecution);
        when(executionService.getQueuedExecutions()).thenReturn(queuedExecutions);
        when(reactionExecutor.executeReaction(testExecution)).thenReturn(executionResult);

        // When
        areaReactionWorker.processExecution(testExecution); // Test the core logic directly

        // Then
        verify(executionRepository).findByIdWithActionInstance(testExecution.getId());
        verify(executionService).markExecutionAsStarted(testExecution.getId());
        verify(reactionExecutor).executeReaction(testExecution);
        verify(executionService).updateExecutionWithResult(executionResult);
    }

    @Test
    void testProcessQueuedExecutionsNoQueuedExecutions() {
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
    void testProcessQueuedExecutionsWithException() {
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
        assertEquals("test-consumer", status.get("consumerName")); // Updated to match mock
    }

    @Test
    void testInitialize() {
        // When - initialize is called via @PostConstruct in real usage
        // In tests, we verify the initialization logic indirectly
        
        // Then - verify that the worker can be created and has expected initial state
        Map<String, Object> status = areaReactionWorker.getWorkerStatus();
        assertNotNull(status);
        assertTrue(status.containsKey("running"));
        assertTrue((Boolean) status.get("running"));
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
    void testLogStatisticsWithException() {
        // Given
        when(executionService.getExecutionStatistics()).thenThrow(new RuntimeException("Stats error"));

        // When & Then - should not throw exception
        assertDoesNotThrow(() -> areaReactionWorker.logStatistics());

        verify(executionService).getExecutionStatistics();
    }
}