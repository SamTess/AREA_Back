package area.server.AREA_Back.service.Webhook;

import area.server.AREA_Back.entity.ActionInstance;
import area.server.AREA_Back.entity.ActivationMode;
import area.server.AREA_Back.entity.Execution;
import area.server.AREA_Back.entity.enums.ActivationModeType;
import area.server.AREA_Back.repository.ActionInstanceRepository;
import area.server.AREA_Back.repository.ActivationModeRepository;
import area.server.AREA_Back.service.Area.ExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for processing webhook events and triggering AREA executions
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookEventProcessingService {

    private final ActionInstanceRepository actionInstanceRepository;
    private final ActivationModeRepository activationModeRepository;
    private final ExecutionService executionService;

    /**
     * Processes a webhook event and triggers matching action instances
     *
     * @param service The service that sent the webhook (e.g., "github", "slack")
     * @param action The action type (e.g., "issues", "pull_request", "message")
     * @param payload The webhook payload
     * @param userId Optional user ID to scope the search
     * @return List of created executions
     */
    @Transactional
    public List<Execution> processWebhookEvent(String service, String action,
                                             Map<String, Object> payload, UUID userId) {
        log.info("Processing webhook event: service={}, action={}, userId={}", service, action, userId);

        List<ActionInstance> matchingInstances = findMatchingActionInstances(service, action, userId);
        log.debug("Found {} matching action instances for webhook event", matchingInstances.size());

        return matchingInstances.stream()
            .map(instance -> createExecutionForWebhook(instance, payload))
            .filter(execution -> execution != null)
            .toList();
    }

    /**
     * Processes a webhook event for a specific user
     *
     * @param service The service that sent the webhook
     * @param action The action type
     * @param payload The webhook payload
     * @param userId The user ID
     * @return List of created executions
     */
    @Transactional
    public List<Execution> processWebhookEventForUser(String service, String action,
                                                    Map<String, Object> payload, UUID userId) {
        return processWebhookEvent(service, action, payload, userId);
    }

    /**
     * Processes a webhook event globally (for all users)
     *
     * @param service The service that sent the webhook
     * @param action The action type
     * @param payload The webhook payload
     * @return List of created executions
     */
    @Transactional
    public List<Execution> processWebhookEventGlobally(String service, String action,
                                                     Map<String, Object> payload) {
        return processWebhookEvent(service, action, payload, null);
    }

    /**
     * Finds action instances that should be triggered by this webhook
     */
    private List<ActionInstance> findMatchingActionInstances(String service, String action, UUID userId) {
        List<ActionInstance> instances;
        if (userId != null) {
            instances = actionInstanceRepository.findEnabledActionInstancesByUserAndService(userId, service);
        } else {
            instances = actionInstanceRepository.findEnabledActionInstancesByService(service);
        }

        return instances.stream()
            .filter(instance -> hasWebhookActivationMode(instance))
            .filter(instance -> matchesActionType(instance, action))
            .toList();
    }

    /**
     * Checks if an action instance has webhook activation mode enabled
     */
    private boolean hasWebhookActivationMode(ActionInstance instance) {
        List<ActivationMode> activationModes = activationModeRepository
            .findByActionInstanceAndEnabled(instance, true);
        return activationModes.stream()
            .anyMatch(mode -> mode.getType() == ActivationModeType.WEBHOOK);
    }

    /**
     * Checks if the action instance matches the webhook action type
     */
    private boolean matchesActionType(ActionInstance instance, String action) {
        String actionKey = instance.getActionDefinition().getKey();
        if (actionKey.equals(action)) {
            return true;
        }
        if ("github".equals(instance.getActionDefinition().getService().getKey())) {
            return matchesGitHubAction(actionKey, action);
        }
        if ("slack".equals(instance.getActionDefinition().getService().getKey())) {
            return matchesSlackAction(actionKey, action);
        }
        return actionKey.contains(action) || action.contains(actionKey);
    }

    /**
     * Maps GitHub webhook events to action definitions
     */
    private boolean matchesGitHubAction(String actionKey, String webhookAction) {
        return switch (webhookAction.toLowerCase()) {
            case "issues" -> actionKey.equals("new_issue") || actionKey.equals("issue_updated");
            case "pull_request" -> actionKey.equals("new_pull_request") || actionKey.equals("pr_updated");
            case "push" -> actionKey.equals("push_to_branch") || actionKey.equals("commit_pushed");
            case "release" -> actionKey.equals("new_release");
            case "star" -> actionKey.equals("repository_starred");
            case "fork" -> actionKey.equals("repository_forked");
            case "ping" -> true;
            default -> actionKey.equals(webhookAction);
        };
    }

    /**
     * Maps Slack webhook events to action definitions
     */
    private boolean matchesSlackAction(String actionKey, String webhookAction) {
        return switch (webhookAction.toLowerCase()) {
            case "message" -> actionKey.equals("new_message") || actionKey.equals("message_posted");
            case "reaction_added" -> actionKey.equals("reaction_added");
            case "member_joined_channel" -> actionKey.equals("member_joined");
            case "app_mention" -> actionKey.equals("app_mention");
            default -> actionKey.equals(webhookAction);
        };
    }

    /**
     * Creates an execution for a webhook event
     */
    private Execution createExecutionForWebhook(ActionInstance instance, Map<String, Object> payload) {
        try {
            List<ActivationMode> activationModes = activationModeRepository
                .findByActionInstanceAndTypeAndEnabled(instance, ActivationModeType.WEBHOOK, true);
            if (activationModes.isEmpty()) {
                log.warn("No webhook activation mode found for action instance {}", instance.getId());
                return null;
            }
            ActivationMode webhookActivationMode = activationModes.get(0); // Use first webhook mode
            UUID correlationId = UUID.randomUUID();
            Execution execution = executionService.createExecution(
                instance,
                webhookActivationMode,
                payload,
                correlationId
            );
            log.info("Created execution {} for webhook event on action instance {}",
                    execution.getId(), instance.getId());
            return execution;
        } catch (Exception e) {
            log.error("Failed to create execution for action instance {}: {}",
                     instance.getId(), e.getMessage(), e);
            return null;
        }
    }

}