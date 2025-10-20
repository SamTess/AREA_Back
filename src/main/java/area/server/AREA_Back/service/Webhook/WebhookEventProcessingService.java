package area.server.AREA_Back.service.Webhook;

import area.server.AREA_Back.entity.ActionInstance;
import area.server.AREA_Back.entity.ActionLink;
import area.server.AREA_Back.entity.ActivationMode;
import area.server.AREA_Back.entity.Execution;
import area.server.AREA_Back.entity.enums.ActivationModeType;
import area.server.AREA_Back.repository.ActionInstanceRepository;
import area.server.AREA_Back.repository.ActionLinkRepository;
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
    private final ActionLinkRepository actionLinkRepository;
    private final ExecutionService executionService;
    private final PayloadMappingService payloadMappingService;

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
     * Creates executions for a webhook event
     * Instead of executing the trigger, we immediately trigger linked reactions
     */
    private Execution createExecutionForWebhook(ActionInstance triggerInstance, Map<String, Object> payload) {
        try {
            // Check if webhook activation mode exists for trigger
            List<ActivationMode> activationModes = activationModeRepository
                .findByActionInstanceAndTypeAndEnabled(triggerInstance, ActivationModeType.WEBHOOK, true);
            if (activationModes.isEmpty()) {
                log.warn("No webhook activation mode found for action instance {}", triggerInstance.getId());
                return null;
            }

            // Check if the event sub-type matches what this trigger expects
            String eventAction = payload.get("action") != null ? payload.get("action").toString() : null;
            if (!matchesEventSubType(triggerInstance, eventAction)) {
                log.debug("Skipping webhook: event action '{}' does not match trigger expectations for instance {}",
                        eventAction, triggerInstance.getId());
                return null;
            }

            // Find linked reactions
            List<ActionLink> links = actionLinkRepository.findBySourceActionInstanceIdWithTargetFetch(triggerInstance.getId());
            
            if (links.isEmpty()) {
                log.warn("No linked actions found for trigger action instance {}", triggerInstance.getId());
                return null;
            }

            UUID correlationId = UUID.randomUUID();
            Execution firstExecution = null;
            
            // Create executions for each linked reaction
            for (ActionLink link : links) {
                ActionInstance targetInstance = link.getTargetActionInstance();
                
                // Check if target has CHAIN activation mode
                List<ActivationMode> chainModes = activationModeRepository
                    .findByActionInstanceAndTypeAndEnabled(targetInstance, ActivationModeType.CHAIN, true);
                
                if (chainModes.isEmpty()) {
                    log.warn("No CHAIN activation mode found for linked action instance {}", targetInstance.getId());
                    continue;
                }
                
                ActivationMode chainActivationMode = chainModes.get(0);
                
                // Apply payload mapping if configured
                Map<String, Object> mappedPayload = applyPayloadMapping(payload, link.getMapping());
                
                // Create execution for the reaction
                Execution execution = executionService.createExecution(
                    targetInstance,
                    chainActivationMode,
                    mappedPayload,
                    correlationId
                );
                
                if (firstExecution == null) {
                    firstExecution = execution;
                }
                
                log.info("Created execution {} for linked reaction {} (correlation: {})",
                        execution.getId(), targetInstance.getId(), correlationId);
            }
            
            return firstExecution;
            
        } catch (Exception e) {
            log.error("Failed to create executions for trigger action instance {}: {}",
                     triggerInstance.getId(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * Checks if the webhook event sub-type matches what the trigger expects
     *
     * @param triggerInstance The trigger action instance
     * @param eventAction The event sub-type (e.g., "opened", "closed", "edited")
     * @return true if matches or no specific sub-type check is needed
     */
    private boolean matchesEventSubType(ActionInstance triggerInstance, String eventAction) {
        if (eventAction == null) {
            // If no action specified, allow it (ping events, etc.)
            return true;
        }

        String actionKey = triggerInstance.getActionDefinition().getKey();
        String service = triggerInstance.getActionDefinition().getService().getKey();

        if ("github".equals(service)) {
            return matchesGitHubEventSubType(actionKey, eventAction);
        }

        if ("slack".equals(service)) {
            return matchesSlackEventSubType(actionKey, eventAction);
        }

        // For other services, accept all sub-types
        return true;
    }

    /**
     * Matches GitHub-specific event sub-types
     */
    private boolean matchesGitHubEventSubType(String actionKey, String eventAction) {
        return switch (actionKey) {
            case "new_issue" -> "opened".equals(eventAction);
            case "issue_updated" -> "edited".equals(eventAction) || "labeled".equals(eventAction);
            case "issue_closed" -> "closed".equals(eventAction);
            case "new_pull_request" -> "opened".equals(eventAction);
            case "pr_updated" -> "edited".equals(eventAction) || "synchronize".equals(eventAction);
            case "pr_merged" -> "closed".equals(eventAction); // Check merged flag separately
            default -> true; // Accept all sub-types for unknown action keys
        };
    }

    /**
     * Matches Slack-specific event sub-types
     */
    private boolean matchesSlackEventSubType(String actionKey, String eventAction) {
        return switch (actionKey) {
            case "new_message" -> "message".equals(eventAction);
            case "reaction_added" -> "reaction_added".equals(eventAction);
            case "member_joined" -> "member_joined_channel".equals(eventAction);
            default -> true;
        };
    }

    /**
     * Applies payload mapping transformation if configured
     */
    private Map<String, Object> applyPayloadMapping(Map<String, Object> sourcePayload, Map<String, Object> mappingConfig) {
        if (mappingConfig == null || mappingConfig.isEmpty()) {
            return sourcePayload;
        }

        try {
            // Convert mapping config to JSON string
            String mappingJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(mappingConfig);
            return payloadMappingService.applyMapping(sourcePayload, mappingJson);
        } catch (Exception e) {
            log.warn("Failed to apply payload mapping, using original payload: {}", e.getMessage());
            return sourcePayload;
        }
    }

    /**
     * Generates a deduplication key for a webhook event
     *
     * @param service The service name
     * @param action The action type
     * @param payload The webhook payload
     * @return A unique deduplication key
     */
    public String generateDeduplicationKey(String service, String action, Map<String, Object> payload) {
        Object eventId = extractEventId(service, payload);
        Object eventAction = payload.get("action");
        
        return String.format("%s:%s:%s:%s",
                service.toLowerCase(),
                action.toLowerCase(),
                eventId != null ? eventId.toString() : "unknown",
                eventAction != null ? eventAction.toString() : "default");
    }

    /**
     * Extracts the event ID from a webhook payload
     */
    private Object extractEventId(String service, Map<String, Object> payload) {
        return switch (service.toLowerCase()) {
            case "github" -> extractGitHubEventId(payload);
            case "slack" -> payload.get("event_id");
            case "discord" -> payload.get("id");
            default -> payload.get("id");
        };
    }

    /**
     * Extracts GitHub event ID from payload
     */
    private Object extractGitHubEventId(Map<String, Object> payload) {
        // Try different ID fields based on event type
        if (payload.containsKey("issue")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> issue = (Map<String, Object>) payload.get("issue");
            return issue != null ? issue.get("id") : null;
        }
        if (payload.containsKey("pull_request")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> pr = (Map<String, Object>) payload.get("pull_request");
            return pr != null ? pr.get("id") : null;
        }
        return payload.get("id");
    }

}