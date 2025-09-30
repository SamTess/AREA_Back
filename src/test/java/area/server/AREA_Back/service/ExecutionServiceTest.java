package area.server.AREA_Back.service;

import area.server.AREA_Back.dto.ExecutionResult;
import area.server.AREA_Back.entity.ActionInstance;
import area.server.AREA_Back.entity.ActivationMode;
import area.server.AREA_Back.entity.Area;
import area.server.AREA_Back.entity.Execution;
import area.server.AREA_Back.entity.enums.ExecutionStatus;
import area.server.AREA_Back.repository.ExecutionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExecutionServiceTest {

    @Mock
    private ExecutionRepository executionRepository;

    @InjectMocks
    private ExecutionService executionService;

    private ActionInstance actionInstance;
    private ActivationMode activationMode;
    private Area area;
    private Execution execution;

    @BeforeEach
    void setUp() {
        area = new Area();
        area.setId(UUID.randomUUID());

        actionInstance = new ActionInstance();
        actionInstance.setId(UUID.randomUUID());
        actionInstance.setArea(area);

        activationMode = new ActivationMode();
        activationMode.setId(UUID.randomUUID());

        execution = new Execution();
        execution.setId(UUID.randomUUID());
        execution.setActionInstance(actionInstance);
        execution.setArea(area);
        execution.setStatus(ExecutionStatus.QUEUED);
        execution.setAttempt(0);
    }

    @Test
    void createExecutionSuccess() {
        // Given
        Map<String, Object> inputPayload = Map.of("data", "test");
        UUID correlationId = UUID.randomUUID();
        when(executionRepository.save(any(Execution.class))).thenReturn(execution);

        // When
        Execution result = executionService.createExecution(
            actionInstance, activationMode, inputPayload, correlationId);

        // Then
        assertNotNull(result);
        verify(executionRepository).save(any(Execution.class));
    }

    @Test
    void updateExecutionWithResultSuccess() {
        // Given
        ExecutionResult result = ExecutionResult.success(
            execution.getId(),
            Map.of("result", "success"),
            LocalDateTime.now()
        );
        when(executionRepository.findById(execution.getId())).thenReturn(Optional.of(execution));
        when(executionRepository.save(any(Execution.class))).thenReturn(execution);

        // When
        Execution updatedExecution = executionService.updateExecutionWithResult(result);

        // Then
        assertNotNull(updatedExecution);
        verify(executionRepository).findById(execution.getId());
        verify(executionRepository).save(any(Execution.class));
    }

    @Test
    void updateExecutionWithResultExecutionNotFound() {
        // Given
        ExecutionResult result = ExecutionResult.success(
            UUID.randomUUID(),
            Map.of("result", "success"),
            LocalDateTime.now()
        );
        when(executionRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            executionService.updateExecutionWithResult(result);
        });
    }

    @Test
    void markExecutionAsStartedSuccess() {
        // Given
        when(executionRepository.findById(execution.getId())).thenReturn(Optional.of(execution));
        when(executionRepository.save(any(Execution.class))).thenReturn(execution);

        // When
        Execution result = executionService.markExecutionAsStarted(execution.getId());

        // Then
        assertNotNull(result);
        verify(executionRepository).findById(execution.getId());
        verify(executionRepository).save(any(Execution.class));
    }

    @Test
    void getQueuedExecutionsSuccess() {
        // Given
        List<Execution> queuedExecutions = Arrays.asList(execution);
        when(executionRepository.findQueuedExecutionsOrderedByQueueTime()).thenReturn(queuedExecutions);

        // When
        List<Execution> result = executionService.getQueuedExecutions();

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(executionRepository).findQueuedExecutionsOrderedByQueueTime();
    }

    @Test
    void getExecutionsReadyForRetrySuccess() {
        // Given
        LocalDateTime retryThreshold = LocalDateTime.now().minusMinutes(5);
        List<Execution> retryExecutions = Arrays.asList(execution);
        when(executionRepository.findExecutionsReadyForRetry(retryThreshold)).thenReturn(retryExecutions);

        // When
        List<Execution> result = executionService.getExecutionsReadyForRetry(retryThreshold);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(executionRepository).findExecutionsReadyForRetry(retryThreshold);
    }

    @Test
    void cancelExecutionSuccess() {
        // Given
        String reason = "Test cancellation";
        when(executionRepository.findById(execution.getId())).thenReturn(Optional.of(execution));
        when(executionRepository.save(any(Execution.class))).thenReturn(execution);

        // When
        Execution result = executionService.cancelExecution(execution.getId(), reason);

        // Then
        assertNotNull(result);
        verify(executionRepository).findById(execution.getId());
        verify(executionRepository).save(any(Execution.class));
    }

    @Test
    void getExecutionStatisticsSuccess() {
        // Given
        when(executionRepository.countByStatus(ExecutionStatus.QUEUED)).thenReturn(5L);
        when(executionRepository.countByStatus(ExecutionStatus.RUNNING)).thenReturn(2L);
        when(executionRepository.countByStatus(ExecutionStatus.OK)).thenReturn(10L);
        when(executionRepository.countByStatus(ExecutionStatus.RETRY)).thenReturn(1L);
        when(executionRepository.countByStatus(ExecutionStatus.FAILED)).thenReturn(3L);
        when(executionRepository.countByStatus(ExecutionStatus.CANCELED)).thenReturn(0L);

        // When
        Map<String, Long> stats = executionService.getExecutionStatistics();

        // Then
        assertNotNull(stats);
        assertEquals(5L, stats.get("queued"));
        assertEquals(2L, stats.get("running"));
        assertEquals(10L, stats.get("ok"));
        assertEquals(1L, stats.get("retry"));
        assertEquals(3L, stats.get("failed"));
        assertEquals(0L, stats.get("canceled"));
    }

    @Test
    void hasExcessiveFailuresTrue() {
        // Given
        LocalDateTime since = LocalDateTime.now().minusHours(1);
        when(executionRepository.countFailedExecutionsSince(actionInstance, since)).thenReturn(5L);

        // When
        boolean result = executionService.hasExcessiveFailures(actionInstance, 3, since);

        // Then
        assertTrue(result);
    }

    @Test
    void hasExcessiveFailuresFalse() {
        // Given
        LocalDateTime since = LocalDateTime.now().minusHours(1);
        when(executionRepository.countFailedExecutionsSince(actionInstance, since)).thenReturn(2L);

        // When
        boolean result = executionService.hasExcessiveFailures(actionInstance, 3, since);

        // Then
        assertFalse(result);
    }
}