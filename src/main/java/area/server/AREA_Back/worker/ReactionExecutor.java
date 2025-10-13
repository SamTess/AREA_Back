package area.server.AREA_Back.worker;

import area.server.AREA_Back.dto.ExecutionResult;
import area.server.AREA_Back.entity.Execution;
import area.server.AREA_Back.entity.ActionDefinition;
import area.server.AREA_Back.entity.ActionInstance;
import area.server.AREA_Back.service.GitHubActionService;
import area.server.AREA_Back.service.GoogleActionService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReactionExecutor {

    private static final int MIN_EXECUTION_TIME = 100;
    private static final int MAX_EXECUTION_TIME = 2000;
    private static final int EMAIL_MIN_LATENCY = 200;
    private static final int EMAIL_MAX_LATENCY = 800;
    private static final int SLACK_MIN_LATENCY = 300;
    private static final int SLACK_MAX_LATENCY = 1000;
    private static final int WEBHOOK_MIN_LATENCY = 500;
    private static final int WEBHOOK_MAX_LATENCY = 1500;
    private static final int DB_MIN_LATENCY = 100;
    private static final int DB_MAX_LATENCY = 500;
    private static final int NOTIFICATION_MIN_LATENCY = 150;
    private static final int NOTIFICATION_MAX_LATENCY = 600;
    private static final int GENERIC_MIN_LATENCY = 200;
    private static final int GENERIC_MAX_LATENCY = 1000;
    private static final int UUID_SUBSTRING_END = 8;
    private static final int MAX_ROWS_AFFECTED = 10;
    private static final int HTTP_OK = 200;
    private static final long MILLIS_TO_SECONDS = 1000;

    private final RetryManager retryManager;
    private final GitHubActionService gitHubActionService;
    private final GoogleActionService googleActionService;
    private final MeterRegistry meterRegistry;

    public ExecutionResult executeReaction(final Execution execution) {
        Timer.Sample sample = Timer.start(meterRegistry);
        LocalDateTime startTime = LocalDateTime.now();
        try {
            log.info("Starting execution of reaction for execution { } (attempt { })",
                    execution.getId(), execution.getAttempt() + 1);

            ActionInstance actionInstance = execution.getActionInstance();
            ActionDefinition actionDefinition = actionInstance.getActionDefinition();

            if (!actionDefinition.getIsExecutable()) {
                throw new IllegalStateException("Action definition is not executable: " + actionDefinition.getKey());
            }

            Map<String, Object> result = executeReactionByService(
                actionDefinition.getService().getKey(),
                actionDefinition.getKey(),
                execution.getInputPayload(),
                actionInstance.getParams(),
                execution
            );

            log.info("Successfully executed reaction for execution { } in { }ms",
                    execution.getId(),
                    java.time.Duration.between(startTime, LocalDateTime.now()).toMillis());

            sample.stop(Timer.builder("area_reaction_execution_duration")
                    .tag("status", "success")
                    .register(meterRegistry));
            return ExecutionResult.success(execution.getId(), result, startTime);

        } catch (Exception e) {
            log.error("Failed to execute reaction for execution { }: { }",
                     execution.getId(), e.getMessage(), e);

            boolean shouldRetry = retryManager.shouldRetry(execution.getAttempt(), e);
            LocalDateTime nextRetryAt = null;
            if (shouldRetry) {
                nextRetryAt = retryManager.calculateNextRetryTime(execution.getAttempt());
            }

            Map<String, Object> errorDetails = createErrorDetails(e, execution);

            sample.stop(Timer.builder("area_reaction_execution_duration")
                    .tag("status", "failure")
                    .register(meterRegistry));
            return ExecutionResult.failure(
                execution.getId(),
                e.getMessage(),
                errorDetails,
                startTime,
                shouldRetry,
                nextRetryAt
            );
        }
    }

    private Map<String, Object> executeReactionByService(final String serviceKey, final String actionKey,
                                                        final Map<String, Object> inputPayload,
                                                        final Map<String, Object> actionParams,
                                                        final Execution execution) {
        Map<String, Object> result = new HashMap<>();

        result.put("service", serviceKey);
        result.put("action", actionKey);
        result.put("executedAt", LocalDateTime.now());
        result.put("executionDuration", ThreadLocalRandom.current().nextInt(MIN_EXECUTION_TIME, MAX_EXECUTION_TIME)
            + "ms");

        switch (serviceKey.toLowerCase()) {
            case "email":
                result.putAll(executeEmailAction(actionKey, inputPayload, actionParams));
                break;
            case "slack":
                result.putAll(executeSlackAction(actionKey, inputPayload, actionParams));
                break;
            case "github":
                result.putAll(executeGitHubAction(actionKey, inputPayload, actionParams, execution));
                break;
            case "google":
                result.putAll(executeGoogleAction(actionKey, inputPayload, actionParams, execution));
                break;
            case "webhook":
                result.putAll(executeWebhookAction(actionKey, inputPayload, actionParams));
                break;
            case "database":
                result.putAll(executeDatabaseAction(actionKey, inputPayload, actionParams));
                break;
            case "notification":
                result.putAll(executeNotificationAction(actionKey, inputPayload, actionParams));
                break;
            default:
                result.putAll(executeGenericAction(serviceKey, actionKey, inputPayload, actionParams));
        }

        return result;
    }

    private Map<String, Object> executeEmailAction(final String actionKey, final Map<String, Object> input,
                                                  final Map<String, Object> params) {
        simulateLatency(EMAIL_MIN_LATENCY, EMAIL_MAX_LATENCY);

        return Map.of(
            "type", "email",
            "action", actionKey,
            "to", params.getOrDefault("to", "unknown@example.com"),
            "subject", params.getOrDefault("subject", "AREA Notification"),
            "status", "sent",
            "messageId", "msg_" + java.util.UUID.randomUUID().toString().substring(0, UUID_SUBSTRING_END)
        );
    }

    private Map<String, Object> executeSlackAction(final String actionKey, final Map<String, Object> input,
                                                  final Map<String, Object> params) {
        simulateLatency(SLACK_MIN_LATENCY, SLACK_MAX_LATENCY);

        return Map.of(
            "type", "slack",
            "action", actionKey,
            "channel", params.getOrDefault("channel", "#general"),
            "message", params.getOrDefault("message", "AREA triggered"),
            "status", "posted",
            "timestamp", System.currentTimeMillis() / MILLIS_TO_SECONDS
        );
    }

    private Map<String, Object> executeWebhookAction(final String actionKey, final Map<String, Object> input,
                                                    final Map<String, Object> params) {
        simulateLatency(WEBHOOK_MIN_LATENCY, WEBHOOK_MAX_LATENCY);

        return Map.of(
            "type", "webhook",
            "action", actionKey,
            "url", params.getOrDefault("url", "https://example.com/webhook"),
            "method", params.getOrDefault("method", "POST"),
            "status", "sent",
            "responseCode", HTTP_OK
        );
    }

    private Map<String, Object> executeDatabaseAction(final String actionKey, final Map<String, Object> input,
                                                     final Map<String, Object> params) {
        simulateLatency(DB_MIN_LATENCY, DB_MAX_LATENCY);

        return Map.of(
            "type", "database",
            "action", actionKey,
            "operation", actionKey,
            "status", "executed",
            "rowsAffected", ThreadLocalRandom.current().nextInt(1, MAX_ROWS_AFFECTED)
        );
    }

    private Map<String, Object> executeNotificationAction(final String actionKey, final Map<String, Object> input,
                                                         final Map<String, Object> params) {
        simulateLatency(NOTIFICATION_MIN_LATENCY, NOTIFICATION_MAX_LATENCY);

        return Map.of(
            "type", "notification",
            "action", actionKey,
            "recipient", params.getOrDefault("recipient", "user"),
            "title", params.getOrDefault("title", "AREA Notification"),
            "status", "delivered",
            "notificationId", java.util.UUID.randomUUID().toString()
        );
    }

    private Map<String, Object> executeGenericAction(final String serviceKey, final String actionKey,
                                                    final Map<String, Object> input, final Map<String, Object> params) {
        simulateLatency(GENERIC_MIN_LATENCY, GENERIC_MAX_LATENCY);

        return Map.of(
            "type", "generic",
            "service", serviceKey,
            "action", actionKey,
            "status", "executed",
            "result", "success"
        );
    }

    private Map<String, Object> executeGitHubAction(final String actionKey, final Map<String, Object> input,
                                                   final Map<String, Object> params, final Execution execution) {
        try {
            UUID userId = execution.getActionInstance().getUser().getId();
            Map<String, Object> result = gitHubActionService.executeGitHubAction(actionKey, input, params, userId);
            result.put("type", "github");
            result.put("executedAt", LocalDateTime.now());
            result.put("executionId", execution.getId());
            return result;
        } catch (Exception e) {
            log.error("Failed to execute GitHub action { }: { }", actionKey, e.getMessage(), e);
            throw new RuntimeException("GitHub action execution failed: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> executeGoogleAction(final String actionKey, final Map<String, Object> input,
                                                    final Map<String, Object> params, final Execution execution) {
        try {
            UUID userId = execution.getActionInstance().getUser().getId();
            Map<String, Object> result = googleActionService.executeGoogleAction(actionKey, input, params, userId);
            result.put("type", "google");
            result.put("executedAt", LocalDateTime.now());
            result.put("executionId", execution.getId());
            return result;
        } catch (Exception e) {
            log.error("Failed to execute Google action {}: {}", actionKey, e.getMessage(), e);
            throw new RuntimeException("Google action execution failed: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> createErrorDetails(final Exception e, final Execution execution) {
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("exception", e.getClass().getSimpleName());
        errorDetails.put("message", e.getMessage());
        errorDetails.put("executionId", execution.getId());
        errorDetails.put("attemptNumber", execution.getAttempt());
        errorDetails.put("actionInstance", execution.getActionInstance().getId());
        errorDetails.put("timestamp", LocalDateTime.now());
        if (e.getCause() != null) {
            errorDetails.put("cause", e.getCause().getMessage());
        }
        return errorDetails;
    }

    private void simulateLatency(final int minMs, final int maxMs) {
        try {
            int delay = ThreadLocalRandom.current().nextInt(minMs, maxMs);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Execution interrupted", e);
        }
    }
}