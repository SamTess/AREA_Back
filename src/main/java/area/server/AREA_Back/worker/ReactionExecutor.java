package area.server.AREA_Back.worker;

import area.server.AREA_Back.dto.ExecutionResult;
import area.server.AREA_Back.entity.Execution;
import area.server.AREA_Back.entity.ActionDefinition;
import area.server.AREA_Back.entity.ActionInstance;
import area.server.AREA_Back.service.Area.Services.DiscordActionService;
import area.server.AREA_Back.service.Area.Services.GitHubActionService;
import area.server.AREA_Back.service.Area.Services.GoogleActionService;
import area.server.AREA_Back.service.Area.Services.NotionActionService;
import area.server.AREA_Back.service.Area.Services.SlackActionService;
import area.server.AREA_Back.service.Area.Services.SpotifyActionService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReactionExecutor {

    private final RetryManager retryManager;
    private final GitHubActionService gitHubActionService;
    private final GoogleActionService googleActionService;
    private final DiscordActionService discordActionService;
    private final SlackActionService slackActionService;
    private final SpotifyActionService spotifyActionService;
    private final NotionActionService notionActionService;
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

        switch (serviceKey.toLowerCase()) {
            case "slack":
                result.putAll(executeSlackAction(actionKey, inputPayload, actionParams, execution));
                break;
            case "spotify":
                result.putAll(executeSpotifyAction(actionKey, inputPayload, actionParams, execution));
                break;
            case "github":
                result.putAll(executeGitHubAction(actionKey, inputPayload, actionParams, execution));
                break;
            case "google":
                result.putAll(executeGoogleAction(actionKey, inputPayload, actionParams, execution));
                break;
            case "discord":
                result.putAll(executeDiscordAction(actionKey, inputPayload, actionParams, execution));
                break;
            case "notion":
                result.putAll(executeNotionAction(actionKey, inputPayload, actionParams, execution));
                break;
            default:
                throw new UnsupportedOperationException("Service not supported: " + serviceKey);
        }

        return result;
    }

    private Map<String, Object> executeSlackAction(final String actionKey, final Map<String, Object> input,
                                                  final Map<String, Object> params, final Execution execution) {
        UUID userId = execution.getActionInstance().getUser().getId();

        try {
            return slackActionService.executeSlackAction(actionKey, input, params, userId);
        } catch (Exception e) {
            log.error("Failed to execute Slack action: {}", e.getMessage(), e);
            throw new RuntimeException("Slack action execution failed: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> executeSpotifyAction(final String actionKey, final Map<String, Object> input,
                                                     final Map<String, Object> params, final Execution execution) {
        UUID userId = execution.getActionInstance().getUser().getId();

        try {
            return spotifyActionService.executeSpotifyAction(actionKey, input, params, userId);
        } catch (Exception e) {
            log.error("Failed to execute Spotify action: {}", e.getMessage(), e);
            throw new RuntimeException("Spotify action execution failed: " + e.getMessage(), e);
        }
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

    private Map<String, Object> executeDiscordAction(final String actionKey, final Map<String, Object> input,
                                                     final Map<String, Object> params, final Execution execution) {
        try {
            UUID userId = execution.getActionInstance().getUser().getId();
            Map<String, Object> result = discordActionService.executeDiscordAction(actionKey, input, params, userId);
            result.put("type", "discord");
            result.put("executedAt", LocalDateTime.now());
            result.put("executionId", execution.getId());
            return result;
        } catch (Exception e) {
            log.error("Failed to execute Discord action {}: {}", actionKey, e.getMessage(), e);
            throw new RuntimeException("Discord action execution failed: " + e.getMessage(), e);
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

    private Map<String, Object> executeNotionAction(final String actionKey, final Map<String, Object> input,
                                                    final Map<String, Object> params, final Execution execution) {
        try {
            UUID userId = execution.getActionInstance().getUser().getId();
            Map<String, Object> result = notionActionService.executeNotionAction(actionKey, input, params, userId);
            result.put("type", "notion");
            result.put("executedAt", LocalDateTime.now());
            result.put("executionId", execution.getId());
            return result;
        } catch (Exception e) {
            log.error("Failed to execute Notion action {}: {}", actionKey, e.getMessage(), e);
            throw new RuntimeException("Notion action execution failed: " + e.getMessage(), e);
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
}