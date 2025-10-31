package area.server.AREA_Back.service.Webhook;

import area.server.AREA_Back.entity.ActionInstance;
import area.server.AREA_Back.entity.enums.ActivationModeType;
import area.server.AREA_Back.repository.ActionInstanceRepository;
import area.server.AREA_Back.service.Area.ExecutionTriggerService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiscordWebhookService {

    private final MeterRegistry meterRegistry;
    private final ActionInstanceRepository actionInstanceRepository;
    private final ExecutionTriggerService executionTriggerService;
    private final WebhookSignatureValidator signatureValidator;
    private final WebhookSecretService webhookSecretService;
    private final WebhookDeduplicationService deduplicationService;

    private Counter webhookCounter;
    private Counter messageCreateCounter;
    private Counter messageReactionAddCounter;
    private Counter webhookProcessingFailures;
    private Counter signatureValidationFailures;

    @PostConstruct
    public void initMetrics() {
        webhookCounter = Counter.builder("discord.webhook.processed")
                .description("Total number of Discord webhooks processed")
                .register(meterRegistry);

        messageCreateCounter = Counter.builder("discord.webhook.message_create")
                .description("Total number of Discord MESSAGE_CREATE events processed")
                .register(meterRegistry);

        messageReactionAddCounter = Counter.builder("discord.webhook.message_reaction_add")
                .description("Total number of Discord MESSAGE_REACTION_ADD events processed")
                .register(meterRegistry);

        webhookProcessingFailures = Counter.builder("discord.webhook.failures")
                .description("Total number of Discord webhook processing failures")
                .register(meterRegistry);

        signatureValidationFailures = Counter.builder("discord.webhook.signature_failures")
                .description("Total number of Discord webhook signature validation failures")
                .register(meterRegistry);
    }

    @Transactional
    public Map<String, Object> processWebhook(Map<String, Object> payload, String signature, String timestamp, byte[] rawBodyBytes) {
        webhookCounter.increment();
        log.info("Processing Discord webhook");

        Map<String, Object> result = new HashMap<>();
        result.put("status", "processed");
        result.put("processedAt", LocalDateTime.now().toString());

        try {
            Integer type = (Integer) payload.get("type");
            if (type != null && type == 1) {
                log.info("Discord ping received");
                result.put("type", "ping");
                result.put("response", Map.of("type", 1));
                return result;
            }

            String secret = webhookSecretService.getServiceSecret("discord");
            if (secret != null && signature != null) {
                boolean isValidSignature = signatureValidator.validateSignature(
                    "discord",
                    rawBodyBytes,
                    signature,
                    secret,
                    timestamp
                );
                if (!isValidSignature) {
                    signatureValidationFailures.increment();
                    log.warn("Invalid Discord webhook signature");
                    result.put("status", "signature_invalid");
                    result.put("error", "Invalid signature");
                    return result;
                }
            }

            String eventType = (String) payload.get("t");
            @SuppressWarnings("unchecked")
            Map<String, Object> eventData = (Map<String, Object>) payload.get("d");

            if (eventType == null) {
                log.warn("Discord webhook missing event type");
                result.put("warning", "Missing event type");
                return result;
            }

            if (eventData == null) {
                log.warn("Discord webhook missing event data");
                result.put("warning", "Missing event data");
                return result;
            }

            result.putAll(processEvent(eventType, eventData, payload));
            return result;

        } catch (Exception e) {
            webhookProcessingFailures.increment();
            log.error("Error processing Discord webhook: {}", e.getMessage(), e);
            result.put("status", "error");
            result.put("error", e.getMessage());
            return result;
        }
    }

    private Map<String, Object> processEvent(String eventType, Map<String, Object> eventData,
                                              Map<String, Object> payload) {
        log.debug("Processing Discord event type: {}", eventType);

        Map<String, Object> result = new HashMap<>();
        result.put("eventType", eventType);

        if (eventType == null) {
            return result;
        }

        switch (eventType.toUpperCase()) {
            case "MESSAGE_CREATE":
                messageCreateCounter.increment();
                result.putAll(processMessageCreateEvent(eventData, payload));
                break;
            case "MESSAGE_REACTION_ADD":
                messageReactionAddCounter.increment();
                result.putAll(processMessageReactionAddEvent(eventData, payload));
                break;
            default:
                log.warn("Unhandled Discord event type: {} (only MESSAGE_CREATE and "
                    + "MESSAGE_REACTION_ADD are supported)", eventType);
                result.put("warning", "Event type not yet supported: " + eventType);
        }

        return result;
    }

    private Map<String, Object> processMessageCreateEvent(Map<String, Object> eventData, Map<String, Object> payload) {
        log.debug("Processing Discord MESSAGE_CREATE event");

        String messageId = (String) eventData.get("id");
        String channelId = (String) eventData.get("channel_id");
        String content = (String) eventData.get("content");
        String authorId = null;
        String authorUsername = null;

        @SuppressWarnings("unchecked")
        Map<String, Object> author = (Map<String, Object>) eventData.get("author");
        if (author != null) {
            authorId = (String) author.get("id");
            authorUsername = (String) author.get("username");
        }

        if (messageId != null) {
            String dedupeKey = "discord_message_" + messageId;
            if (deduplicationService.checkAndMark(dedupeKey, "discord")) {
                log.info("Duplicate Discord message event detected for message {}", messageId);
                return Map.of("status", "duplicate", "messageId", messageId);
            }
        }

        Map<String, Object> eventPayload = new HashMap<>();
        eventPayload.put("action", "message_create");
        eventPayload.put("message_id", messageId);
        eventPayload.put("channel_id", channelId);
        eventPayload.put("content", content);
        eventPayload.put("author_id", authorId);
        eventPayload.put("author_username", authorUsername);

        triggerMatchingActions("message_created", eventPayload);

        return Map.of(
            "action", "message_create",
            "messageId", messageId,
            "channelId", channelId,
            "authorId", authorId
        );
    }

    private Map<String, Object> processMessageReactionAddEvent(Map<String, Object> eventData,
                                                                     Map<String, Object> payload) {
        log.debug("Processing Discord MESSAGE_REACTION_ADD event");

        String messageId = (String) eventData.get("message_id");
        String channelId = (String) eventData.get("channel_id");
        String userId = (String) eventData.get("user_id");

        @SuppressWarnings("unchecked")
        Map<String, Object> emoji = (Map<String, Object>) eventData.get("emoji");
        String emojiName = null;
        String emojiId = null;
        if (emoji != null) {
            emojiName = (String) emoji.get("name");
            emojiId = (String) emoji.get("id");
        }

        String reactionKey = messageId + "_" + userId + "_";
        if (emojiId != null) {
            reactionKey += emojiId;
        } else {
            reactionKey += emojiName;
        }
        String dedupeKey = "discord_reaction_" + reactionKey;
        if (deduplicationService.checkAndMark(dedupeKey, "discord")) {
            log.info("Duplicate Discord reaction event detected for reaction {}", reactionKey);
            return Map.of("status", "duplicate", "reactionKey", reactionKey);
        }

        Map<String, Object> eventPayload = new HashMap<>();
        eventPayload.put("action", "message_reaction_add");
        eventPayload.put("message_id", messageId);
        eventPayload.put("channel_id", channelId);
        eventPayload.put("user_id", userId);
        eventPayload.put("emoji_name", emojiName);
        eventPayload.put("emoji_id", emojiId);

        triggerMatchingActions("reaction_added", eventPayload);

        return Map.of(
            "action", "message_reaction_add",
            "messageId", messageId,
            "channelId", channelId,
            "userId", userId,
            "emoji", emojiName
        );
    }

    private void triggerMatchingActions(String actionKey, Map<String, Object> eventData) {
        try {
            List<ActionInstance> matchingInstances = actionInstanceRepository
                .findEnabledActionInstancesByService("discord");

            log.debug("Found {} enabled Discord action instances, filtering by key: {}",
                matchingInstances.size(), actionKey);

            for (ActionInstance actionInstance : matchingInstances) {
                String definitionKey = actionInstance.getActionDefinition().getKey();
                log.debug("Checking action instance with definition key: {}", definitionKey);

                if (definitionKey.equals(actionKey)) {
                    if (!eventMatchesActionParams(actionInstance, eventData)) {
                        log.debug("Event does not match action instance {} params, skipping",
                                actionInstance.getId());
                        continue;
                    }

                    try {
                        log.info("Triggering AREA execution for Discord event: {} (instance: {})",
                            actionKey, actionInstance.getId());
                        executionTriggerService.triggerAreaExecution(
                            actionInstance,
                            ActivationModeType.WEBHOOK,
                            eventData
                        );
                    } catch (Exception e) {
                        log.error("Failed to trigger execution for Discord webhook event: {}", e.getMessage(), e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to find matching Discord action instances: {}", e.getMessage(), e);
        }
    }

    /**
     * Check if the event data matches the action instance's configured parameters
     */
    private boolean eventMatchesActionParams(ActionInstance actionInstance, Map<String, Object> eventData) {
        Map<String, Object> params = actionInstance.getParams();
        if (params == null || params.isEmpty()) {
            return true;
        }

        if (params.containsKey("channel_id")) {
            String configuredChannel = (String) params.get("channel_id");
            String eventChannel = (String) eventData.get("channel_id");

            if (configuredChannel != null && !configuredChannel.isEmpty()) {
                if (!configuredChannel.equals(eventChannel)) {
                    log.trace("Channel mismatch: configured={}, event={}", configuredChannel, eventChannel);
                    return false;
                }
            }
        }

        if (params.containsKey("guild_id")) {
            String configuredGuild = (String) params.get("guild_id");
            String eventGuild = (String) eventData.get("guild_id");

            if (configuredGuild != null && !configuredGuild.isEmpty()) {
                if (!configuredGuild.equals(eventGuild)) {
                    log.trace("Guild mismatch: configured={}, event={}", configuredGuild, eventGuild);
                    return false;
                }
            }
        }

        return true;
    }
}
