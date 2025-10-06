package area.server.AREA_Back.service;

import area.server.AREA_Back.dto.ExecutionResult;
import area.server.AREA_Back.entity.Execution;
import area.server.AREA_Back.entity.ActionInstance;
import area.server.AREA_Back.entity.ActivationMode;
import area.server.AREA_Back.entity.enums.ExecutionStatus;
import area.server.AREA_Back.repository.ExecutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutionService {

    private final ExecutionRepository executionRepository;

    @Transactional
    public Execution createExecution(ActionInstance actionInstance,
                                   ActivationMode activationMode,
                                   Map<String, Object> inputPayload,
                                   UUID correlationId) {
        Execution execution = new Execution();
        execution.setActionInstance(actionInstance);
        execution.setActivationMode(activationMode);
        execution.setArea(actionInstance.getArea());
        execution.setStatus(ExecutionStatus.QUEUED);
        execution.setAttempt(0);
        execution.setInputPayload(inputPayload);
        execution.setCorrelationId(correlationId);

        return executionRepository.save(execution);
    }

    @Transactional
    public Execution updateExecutionWithResult(ExecutionResult result) {
        Optional<Execution> executionOpt = executionRepository.findById(result.getExecutionId());
        if (executionOpt.isEmpty()) {
            throw new IllegalArgumentException("Execution not found: " + result.getExecutionId());
        }

        Execution execution = executionOpt.get();
        execution.setStatus(result.getStatus());
        execution.setStartedAt(result.getStartedAt());
        execution.setFinishedAt(result.getFinishedAt());
        execution.setOutputPayload(result.getOutputPayload());
        execution.setError(result.getError());

        if (result.getStatus() == ExecutionStatus.RETRY) {
            execution.setAttempt(execution.getAttempt() + 1);
        }

        log.info("Updated execution { } with status { }, attempt { }",
                execution.getId(), execution.getStatus(), execution.getAttempt());

        return executionRepository.save(execution);
    }

    @Transactional
    public Execution markExecutionAsStarted(UUID executionId) {
        Optional<Execution> executionOpt = executionRepository.findById(executionId);
        if (executionOpt.isEmpty()) {
            throw new IllegalArgumentException("Execution not found: " + executionId);
        }

        Execution execution = executionOpt.get();
        execution.setStatus(ExecutionStatus.RUNNING);
        execution.setStartedAt(LocalDateTime.now());

        return executionRepository.save(execution);
    }

    public List<Execution> getQueuedExecutions() {
        return executionRepository.findQueuedExecutionsOrderedByQueueTime();
    }

    public List<Execution> getExecutionsReadyForRetry(LocalDateTime retryThreshold) {
        return executionRepository.findExecutionsReadyForRetry(retryThreshold);
    }

    public List<Execution> getTimedOutExecutions(LocalDateTime timeoutThreshold) {
        return executionRepository.findTimedOutExecutions(timeoutThreshold);
    }

    @Transactional
    public Execution cancelExecution(UUID executionId, String reason) {
        Optional<Execution> executionOpt = executionRepository.findById(executionId);
        if (executionOpt.isEmpty()) {
            throw new IllegalArgumentException("Execution not found: " + executionId);
        }

        Execution execution = executionOpt.get();
        execution.setStatus(ExecutionStatus.CANCELED);
        execution.setFinishedAt(LocalDateTime.now());

        if (reason != null) {
            execution.setError(Map.of("reason", reason, "canceledAt", LocalDateTime.now().toString()));
        }

        log.info("Canceled execution { } with reason: { }", executionId, reason);
        return executionRepository.save(execution);
    }

    public Map<String, Long> getExecutionStatistics() {
        return Map.of(
            "queued", executionRepository.countByStatus(ExecutionStatus.QUEUED),
            "running", executionRepository.countByStatus(ExecutionStatus.RUNNING),
            "ok", executionRepository.countByStatus(ExecutionStatus.OK),
            "retry", executionRepository.countByStatus(ExecutionStatus.RETRY),
            "failed", executionRepository.countByStatus(ExecutionStatus.FAILED),
            "canceled", executionRepository.countByStatus(ExecutionStatus.CANCELED)
        );
    }

    public boolean hasExcessiveFailures(ActionInstance actionInstance, int maxFailures,
                                       LocalDateTime since) {
        long failureCount = executionRepository.countFailedExecutionsSince(actionInstance, since);
        return failureCount >= maxFailures;
    }
}