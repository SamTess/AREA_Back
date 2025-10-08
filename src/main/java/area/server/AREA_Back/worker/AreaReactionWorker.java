package area.server.AREA_Back.worker;

import area.server.AREA_Back.config.RedisConfig;
import area.server.AREA_Back.dto.ExecutionResult;
import area.server.AREA_Back.entity.Execution;
import area.server.AREA_Back.repository.ExecutionRepository;
import area.server.AREA_Back.service.ActionLinkService;
import area.server.AREA_Back.service.ExecutionService;
import area.server.AREA_Back.service.RedisEventService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class AreaReactionWorker {

    private static final int TIMEOUT_MINUTES = 5;

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisEventService redisEventService;
    private final ExecutionService executionService;
    private final ExecutionRepository executionRepository;
    private final ReactionExecutor reactionExecutor;
    private final RedisConfig redisConfig;
    private final MeterRegistry meterRegistry;
    private final ActionLinkService actionLinkService;
    private volatile boolean running = true;

    private Counter processedEventsCounter;
    private Counter processedExecutionsCounter;
    private Counter processedRetriesCounter;
    private Counter cleanedTimeoutsCounter;
    private Counter successfulExecutionsCounter;
    private Counter failedExecutionsCounter;

    @PostConstruct
    public void initialize() {
        log.info("Initializing AREA Reaction Worker: { }", redisConfig.getAreasConsumerName());
        redisEventService.initializeStream();
        log.info("AREA Reaction Worker initialized successfully");

        processedEventsCounter = meterRegistry.counter("area_worker_events_processed_total");
        processedExecutionsCounter = meterRegistry.counter("area_worker_executions_processed_total");
        processedRetriesCounter = meterRegistry.counter("area_worker_retries_processed_total");
        cleanedTimeoutsCounter = meterRegistry.counter("area_worker_timeouts_cleaned_total");
        successfulExecutionsCounter = meterRegistry.counter("area_worker_executions_successful_total");
        failedExecutionsCounter = meterRegistry.counter("area_worker_executions_failed_total");
    }

    @Scheduled(fixedDelay = 1000)
    @Async("areaWorkerExecutor")
    public void processAreaEvents() {
        if (!running) {
            return;
        }
        try {
            @SuppressWarnings("unchecked")
            var records = redisTemplate.opsForStream().read(
                Consumer.from(redisConfig.getAreasConsumerGroup(), redisConfig.getAreasConsumerName()),
                (StreamOffset<String>) StreamOffset.create(
                    redisConfig.getAreasEventsStream(), ReadOffset.lastConsumed())
            );
            if (records != null && !records.isEmpty()) {
                log.debug("Processing { } events from Redis stream", records.size());
                processedEventsCounter.increment(records.size());
                for (var record : records) {
                    processEventRecord(record);
                }
            }

        } catch (Exception e) {
            log.error("Error processing Redis stream events: { }", e.getMessage(), e);
        }
    }

    @Scheduled(fixedDelay = 5000)
    @Async("areaWorkerExecutor")
    public void processQueuedExecutions() {
        if (!running) {
            return;
        }
        try {
            List<Execution> queuedExecutions = executionService.getQueuedExecutions();
            if (!queuedExecutions.isEmpty()) {
                log.info("Processing { } queued executions", queuedExecutions.size());
                processedExecutionsCounter.increment(queuedExecutions.size());
                for (Execution execution : queuedExecutions) {
                    processExecution(execution);
                }
            }

        } catch (Exception e) {
            log.error("Error processing queued executions: { }", e.getMessage(), e);
        }
    }

    @Scheduled(fixedDelay = 10000)
    @Async("areaWorkerExecutor")
    public void processRetryExecutions() {
        if (!running) {
            return;
        }
        try {
            LocalDateTime retryThreshold = LocalDateTime.now().minusMinutes(1);
            List<Execution> retryExecutions = executionService.getExecutionsReadyForRetry(retryThreshold);

            if (!retryExecutions.isEmpty()) {
                log.info("Processing { } executions ready for retry", retryExecutions.size());
                processedRetriesCounter.increment(retryExecutions.size());
                for (Execution execution : retryExecutions) {
                    processExecution(execution);
                }
            }

        } catch (Exception e) {
            log.error("Error processing retry executions: { }", e.getMessage(), e);
        }
    }

    @Scheduled(fixedDelay = 30000)
    @Async("areaWorkerExecutor")
    public void cleanupTimedOutExecutions() {
        if (!running) {
            return;
        }

        try {
            LocalDateTime timeoutThreshold = LocalDateTime.now().minusMinutes(TIMEOUT_MINUTES);
            List<Execution> timedOutExecutions = executionService.getTimedOutExecutions(timeoutThreshold);
            if (!timedOutExecutions.isEmpty()) {
                log.warn("Found { } timed out executions, marking as failed", timedOutExecutions.size());
                cleanedTimeoutsCounter.increment(timedOutExecutions.size());
                for (Execution execution : timedOutExecutions) {
                    ExecutionResult failureResult = ExecutionResult.failure(
                        execution.getId(),
                        "Execution timed out",
                        Map.of("reason", "timeout", "timeoutAt", LocalDateTime.now().toString()),
                        execution.getStartedAt(),
                        false,
                        null
                    );
                    executionService.updateExecutionWithResult(failureResult);
                    log.info("Marked timed out execution { } as failed", execution.getId());
                }
            }

        } catch (Exception e) {
            log.error("Error cleaning up timed out executions: { }", e.getMessage(), e);
        }
    }

    @Scheduled(fixedDelay = 60000)
    public void logStatistics() {
        try {
            var stats = executionService.getExecutionStatistics();
            log.info("Execution statistics: queued={ }, running={ }, ok={ }, retry={ }, failed={ }, canceled={ }",
                    stats.get("queued"), stats.get("running"), stats.get("ok"),
                    stats.get("retry"), stats.get("failed"), stats.get("canceled"));
        } catch (Exception e) {
            log.warn("Failed to log statistics: { }", e.getMessage());
        }
    }

    @Async("reactionTaskExecutor")
    public void processEventRecord(final MapRecord<String, Object, Object> record) {
        try {
            Map<Object, Object> values = record.getValue();
            log.debug("Processing event record: { }", record.getId());
            Object executionIdObj = values.get("executionId");
            if (executionIdObj != null) {
                String executionIdStr = executionIdObj.toString();
                UUID executionId = UUID.fromString(executionIdStr);
                var executionOpt = executionService.getQueuedExecutions().stream()
                    .filter(e -> e.getId().equals(executionId))
                    .findFirst();
                if (executionOpt.isPresent()) {
                    processExecution(executionOpt.get());
                } else {
                    log.warn("Execution not found for event: { }", executionId);
                }
            }
            redisTemplate.opsForStream().acknowledge(
                redisConfig.getAreasEventsStream(),
                redisConfig.getAreasConsumerGroup(),
                record.getId()
            );

        } catch (Exception e) {
            log.error("Error processing event record { }: { }", record.getId(), e.getMessage(), e);
        }
    }

    @Async("reactionTaskExecutor")
    public void processExecution(final Execution execution) {
        Execution fullExecution = executionRepository.findByIdWithActionInstance(execution.getId())
            .orElseThrow(() -> new IllegalStateException("Execution not found: " + execution.getId()));

        try {
            log.info("Processing execution: id={ }, actionInstance={ }, attempt={ }",
                    fullExecution.getId(),
                    fullExecution.getActionInstance().getId(),
                    fullExecution.getAttempt());
            executionService.markExecutionAsStarted(fullExecution.getId());
            ExecutionResult result = reactionExecutor.executeReaction(fullExecution);
            executionService.updateExecutionWithResult(result);
            successfulExecutionsCounter.increment();
            log.info("Completed execution: id={ }, status={ }, duration={ }ms",
                    fullExecution.getId(),
                    result.getStatus(),
                    result.getDurationMs());

            processedExecutionsCounter.increment();
            if ("SUCCESS".equals(result.getStatus().name())) {
                try {
                    actionLinkService.triggerLinkedActions(fullExecution);
                    log.info("Successfully triggered linked actions for execution: { }", fullExecution.getId());
                } catch (Exception linkError) {
                    log.error("Failed to trigger linked actions for execution { }: { }",
                             fullExecution.getId(), linkError.getMessage(), linkError);
                }
            }

        } catch (Exception e) {
            log.error("Error processing execution { }: { }", fullExecution.getId(), e.getMessage(), e);
            try {
                ExecutionResult failureResult = ExecutionResult.failure(
                    fullExecution.getId(),
                    "Worker processing error: " + e.getMessage(),
                    Map.of("workerError", e.getClass().getSimpleName(), "timestamp", LocalDateTime.now().toString()),
                    execution.getStartedAt(),
                    false,
                    null
                );
                executionService.updateExecutionWithResult(failureResult);

                failedExecutionsCounter.increment();
            } catch (Exception updateError) {
                log.error("Failed to update execution after processing error: { }", updateError.getMessage());
            }
        }
    }

    public void shutdown() {
        log.info("Shutting down AREA Reaction Worker: { }", redisConfig.getAreasConsumerName());
        running = false;
    }

    public Map<String, Object> getWorkerStatus() {
        return Map.of(
            "consumerName", redisConfig.getAreasConsumerName(),
            "running", running,
            "streamInfo", redisEventService.getStreamInfo()
        );
    }
}