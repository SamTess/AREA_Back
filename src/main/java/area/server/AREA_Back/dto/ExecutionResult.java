package area.server.AREA_Back.dto;

import area.server.AREA_Back.entity.enums.ExecutionStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionResult {
    private UUID executionId;
    private ExecutionStatus status;
    private Map<String, Object> outputPayload;
    private Map<String, Object> error;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Integer attemptNumber;
    private String errorMessage;
    private Long durationMs;
    private boolean shouldRetry;
    private LocalDateTime nextRetryAt;

    public static ExecutionResult success(final UUID executionId, final Map<String, Object> outputPayload,
                                        final LocalDateTime startedAt) {
        ExecutionResult result = new ExecutionResult();
        result.setExecutionId(executionId);
        result.setStatus(ExecutionStatus.OK);
        result.setOutputPayload(outputPayload);
        result.setStartedAt(startedAt);
        result.setFinishedAt(LocalDateTime.now());
        result.setShouldRetry(false);
        if (startedAt != null) {
            result.setDurationMs(java.time.Duration.between(startedAt, result.getFinishedAt()).toMillis());
        }
        return result;
    }

    public static ExecutionResult failure(final UUID executionId, final String errorMessage,
                                        final Map<String, Object> error, final LocalDateTime startedAt,
                                        final boolean shouldRetry, final LocalDateTime nextRetryAt) {
        ExecutionResult result = new ExecutionResult();
        result.setExecutionId(executionId);
        ExecutionStatus status;
        if (shouldRetry) {
            status = ExecutionStatus.RETRY;
        } else {
            status = ExecutionStatus.FAILED;
        }
        result.setStatus(status);
        result.setError(error);
        result.setErrorMessage(errorMessage);
        result.setStartedAt(startedAt);
        result.setFinishedAt(LocalDateTime.now());
        result.setShouldRetry(shouldRetry);
        result.setNextRetryAt(nextRetryAt);
        if (startedAt != null) {
            result.setDurationMs(java.time.Duration.between(startedAt, result.getFinishedAt()).toMillis());
        }
        return result;
    }
}