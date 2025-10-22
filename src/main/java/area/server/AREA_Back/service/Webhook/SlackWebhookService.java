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

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SlackWebhookService {

    private final MeterRegistry meterRegistry;
    private final ActionInstanceRepository actionInstanceRepository;
    private final ExecutionTriggerService executionTriggerService;

    private Counter webhookCounter;
    private Counter messageEventCounter;
    private Counter reactionEventCounter;
    private Counter channelEventCounter;
    private Counter memberEventCounter;
    private Counter fileEventCounter;
    private Counter webhookProcessingFailures;
    private Counter urlVerificationCounter;

    @PostConstruct
    public void initMetrics() {
        webhookCounter = Counter.builder("slack.webhook.processed")
                .description("Total number of Slack webhooks processed")
                .register(meterRegistry);

        messageEventCounter = Counter.builder("slack.webhook.message")
                .description("Total number of Slack message events processed")
                .register(meterRegistry);

        reactionEventCounter = Counter.builder("slack.webhook.reaction")
                .description("Total number of Slack reaction events processed")
                .register(meterRegistry);

        channelEventCounter = Counter.builder("slack.webhook.channel")
                .description("Total number of Slack channel events processed")
                .register(meterRegistry);

        memberEventCounter = Counter.builder("slack.webhook.member")
                .description("Total number of Slack member events processed")
                .register(meterRegistry);

        fileEventCounter = Counter.builder("slack.webhook.file")
                .description("Total number of Slack file events processed")
                .register(meterRegistry);

        webhookProcessingFailures = Counter.builder("slack.webhook.failures")
                .description("Total number of Slack webhook processing failures")
                .register(meterRegistry);

        urlVerificationCounter = Counter.builder("slack.webhook.url_verification")
                .description("Total number of Slack URL verification requests")
                .register(meterRegistry);
    }

    public Map<String, Object> processWebhook(Map<String, Object> payload) {
        webhookCounter.increment();
        log.info("Processing Slack webhook");

        Map<String, Object> result = new HashMap<>();
        result.put("status", "processed");
        result.put("processedAt", LocalDateTime.now().toString());

        try {
            String type = (String) payload.get("type");

            if ("url_verification".equals(type)) {
                urlVerificationCounter.increment();
                return handleUrlVerification(payload);
            }

            if ("event_callback".equals(type)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> event = (Map<String, Object>) payload.get("event");
                if (event != null) {
                    String eventType = (String) event.get("type");
                    result.putAll(processEvent(eventType, event, payload));
                }
            }

            return result;

        } catch (Exception e) {
            webhookProcessingFailures.increment();
            log.error("Error processing Slack webhook: {}", e.getMessage(), e);
            result.put("status", "error");
            result.put("error", e.getMessage());
            return result;
        }
    }

    private Map<String, Object> handleUrlVerification(Map<String, Object> payload) {
        log.info("Handling Slack URL verification");
        Map<String, Object> result = new HashMap<>();
        result.put("challenge", payload.get("challenge"));
        return result;
    }

    private Map<String, Object> processEvent(String eventType, Map<String, Object> event,
                                              Map<String, Object> payload) {
        log.debug("Processing Slack event type: {}", eventType);

        Map<String, Object> result = new HashMap<>();
        result.put("eventType", eventType);

        if (eventType == null) {
            return result;
        }

        switch (eventType) {
            case "message":
                messageEventCounter.increment();
                result.putAll(processMessageEvent(event, payload));
                break;
            case "reaction_added":
                reactionEventCounter.increment();
                result.putAll(processReactionEvent(event, payload));
                break;
            case "channel_created":
            case "channel_rename":
                channelEventCounter.increment();
                result.putAll(processChannelEvent(event, payload));
                break;
            case "member_joined_channel":
            case "team_join":
                memberEventCounter.increment();
                result.putAll(processMemberEvent(event, payload));
                break;
            case "file_shared":
                fileEventCounter.increment();
                result.putAll(processFileEvent(event, payload));
                break;
            default:
                log.warn("Unhandled Slack event type: {}", eventType);
                result.put("warning", "Event type not yet supported: " + eventType);
        }

        return result;
    }

    private Map<String, Object> processMessageEvent(Map<String, Object> event, Map<String, Object> payload) {
        log.debug("Processing Slack message event");

        String channel = (String) event.get("channel");
        String text = (String) event.get("text");
        String user = (String) event.get("user");
        String ts = (String) event.get("ts");

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("type", "message");
        eventData.put("channel", channel);
        eventData.put("text", text);
        eventData.put("user", user);
        eventData.put("ts", ts);

        triggerMatchingActions("new_message", eventData);

        String channelValue;
        if (channel != null) {
            channelValue = channel;
        } else {
            channelValue = "unknown";
        }

        String userValue;
        if (user != null) {
            userValue = user;
        } else {
            userValue = "unknown";
        }

        return Map.of(
            "action", "message",
            "channel", channelValue,
            "user", userValue
        );
    }

    private Map<String, Object> processReactionEvent(Map<String, Object> event, Map<String, Object> payload) {
        log.debug("Processing Slack reaction event");

        String reaction = (String) event.get("reaction");
        String user = (String) event.get("user");

        @SuppressWarnings("unchecked")
        Map<String, Object> item = (Map<String, Object>) event.get("item");
        String channel;
        String ts;
        if (item != null) {
            channel = (String) item.get("channel");
        } else {
            channel = null;
        }
        if (item != null) {
            ts = (String) item.get("ts");
        } else {
            ts = null;
        }

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("type", "reaction_added");
        eventData.put("reaction", reaction);
        eventData.put("user", user);
        eventData.put("channel", channel);
        eventData.put("message_ts", ts);

        triggerMatchingActions("reaction_added", eventData);

        String reactionValue;
        if (reaction != null) {
            reactionValue = reaction;
        } else {
            reactionValue = "unknown";
        }

        String channelValue;
        if (channel != null) {
            channelValue = channel;
        } else {
            channelValue = "unknown";
        }

        return Map.of(
            "action", "reaction_added",
            "reaction", reactionValue,
            "channel", channelValue
        );
    }

    private Map<String, Object> processChannelEvent(Map<String, Object> event, Map<String, Object> payload) {
        log.debug("Processing Slack channel event");

        @SuppressWarnings("unchecked")
        Map<String, Object> channel = (Map<String, Object>) event.get("channel");

        String channelId;
        String channelName;
        if (channel != null) {
            channelId = (String) channel.get("id");
        } else {
            channelId = null;
        }
        if (channel != null) {
            channelName = (String) channel.get("name");
        } else {
            channelName = null;
        }

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("type", "channel_created");
        eventData.put("channel_id", channelId);
        eventData.put("channel_name", channelName);

        triggerMatchingActions("new_channel", eventData);

        String channelIdValue;
        if (channelId != null) {
            channelIdValue = channelId;
        } else {
            channelIdValue = "unknown";
        }

        String channelNameValue;
        if (channelName != null) {
            channelNameValue = channelName;
        } else {
            channelNameValue = "unknown";
        }

        return Map.of(
            "action", "channel_event",
            "channelId", channelIdValue,
            "channelName", channelNameValue
        );
    }

    private Map<String, Object> processMemberEvent(Map<String, Object> event, Map<String, Object> payload) {
        log.debug("Processing Slack member event");

        String user = (String) event.get("user");
        String channel = (String) event.get("channel");

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("type", "user_joined");
        eventData.put("user", user);
        eventData.put("channel", channel);

        triggerMatchingActions("user_joined", eventData);

        String userValue;
        if (user != null) {
            userValue = user;
        } else {
            userValue = "unknown";
        }

        String channelValue;
        if (channel != null) {
            channelValue = channel;
        } else {
            channelValue = "unknown";
        }

        return Map.of(
            "action", "member_joined",
            "user", userValue,
            "channel", channelValue
        );
    }

    private Map<String, Object> processFileEvent(Map<String, Object> event, Map<String, Object> payload) {
        log.debug("Processing Slack file event");

        @SuppressWarnings("unchecked")
        Map<String, Object> file = (Map<String, Object>) event.get("file");

        String fileId;
        String fileName;
        if (file != null) {
            fileId = (String) file.get("id");
        } else {
            fileId = null;
        }
        if (file != null) {
            fileName = (String) file.get("name");
        } else {
            fileName = null;
        }
        String user = (String) event.get("user_id");

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("type", "file_shared");
        eventData.put("file_id", fileId);
        eventData.put("file_name", fileName);
        eventData.put("user", user);

        triggerMatchingActions("file_shared", eventData);

        String fileIdValue;
        if (fileId != null) {
            fileIdValue = fileId;
        } else {
            fileIdValue = "unknown";
        }

        String fileNameValue;
        if (fileName != null) {
            fileNameValue = fileName;
        } else {
            fileNameValue = "unknown";
        }

        return Map.of(
            "action", "file_shared",
            "fileId", fileIdValue,
            "fileName", fileNameValue
        );
    }

    private void triggerMatchingActions(String actionKey, Map<String, Object> eventData) {
        try {
            List<ActionInstance> matchingInstances = actionInstanceRepository
                .findEnabledActionInstancesByService("slack");

            for (ActionInstance actionInstance : matchingInstances) {
                if (actionInstance.getActionDefinition().getKey().equals(actionKey)) {
                    try {
                        executionTriggerService.triggerAreaExecution(
                            actionInstance,
                            ActivationModeType.WEBHOOK,
                            eventData
                        );
                    } catch (Exception e) {
                        log.error("Failed to trigger execution for Slack webhook event: {}", e.getMessage(), e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to find matching Slack action instances: {}", e.getMessage(), e);
        }
    }
}
