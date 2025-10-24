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
public class NotionWebhookService {

    private final MeterRegistry meterRegistry;
    private final ActionInstanceRepository actionInstanceRepository;
    private final ExecutionTriggerService executionTriggerService;

    private Counter webhookCounter;
    private Counter pageCreatedCounter;
    private Counter pageUpdatedCounter;
    private Counter webhookProcessingFailures;

    @PostConstruct
    public void initMetrics() {
        webhookCounter = Counter.builder("notion.webhook.processed")
                .description("Total number of Notion webhooks processed")
                .register(meterRegistry);

        pageCreatedCounter = Counter.builder("notion.webhook.page_created")
                .description("Total number of Notion page created events processed")
                .register(meterRegistry);

        pageUpdatedCounter = Counter.builder("notion.webhook.page_updated")
                .description("Total number of Notion page updated events processed")
                .register(meterRegistry);

        webhookProcessingFailures = Counter.builder("notion.webhook.failures")
                .description("Total number of Notion webhook processing failures")
                .register(meterRegistry);
    }

    /**
     * Process Notion webhook event
     *
     * @param payload The webhook payload from Notion
     * @return Processing result
     */
    public Map<String, Object> processWebhook(Map<String, Object> payload) {
        webhookCounter.increment();
        log.info("Processing Notion webhook");

        log.info("=== NOTION WEBHOOK PAYLOAD ===");
        log.info("Full payload: {}", payload);
        log.info("Payload keys: {}", payload.keySet());

        if (payload.containsKey("secret")) {
            log.info("Notion webhook secret found in payload: {}", payload.get("secret"));
        }
        if (payload.containsKey("signature")) {
            log.info("Notion webhook signature found in payload: {}", payload.get("signature"));
        }
        if (payload.containsKey("token")) {
            log.info("Notion webhook token found in payload: {}", payload.get("token"));
        }
        log.info("==============================");

        Map<String, Object> result = new HashMap<>();
        result.put("status", "processed");
        result.put("processedAt", LocalDateTime.now().toString());

        try {
            String eventType = (String) payload.get("type");

            if (eventType == null) {
                log.warn("Notion webhook missing event type");
                result.put("warning", "Missing event type");
                return result;
            }

            log.debug("Processing Notion event type: {}", eventType);
            result.put("eventType", eventType);

            switch (eventType) {
                case "page.created":
                    pageCreatedCounter.increment();
                    result.putAll(processPageCreatedEvent(payload));
                    break;
                case "page.updated":
                case "page.content_updated":
                    pageUpdatedCounter.increment();
                    result.putAll(processPageUpdatedEvent(payload));
                    break;
                default:
                    log.info("Unhandled Notion event type: {}", eventType);
                    result.put("info", "Event type noted but no specific handler: " + eventType);
            }

            return result;

        } catch (Exception e) {
            webhookProcessingFailures.increment();
            log.error("Error processing Notion webhook: {}", e.getMessage(), e);
            result.put("status", "error");
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * Process page.created event
     */
    private Map<String, Object> processPageCreatedEvent(Map<String, Object> payload) {
        log.debug("Processing Notion page.created event");

        @SuppressWarnings("unchecked")
        Map<String, Object> page = (Map<String, Object>) payload.get("data");

        if (page == null) {
            log.warn("No page data in Notion page.created event");
            return Map.of("warning", "Missing page data");
        }

        Map<String, Object> eventData = extractPageEventData(page, "created");

        triggerMatchingActions("new_page", eventData);

        return Map.of(
            "action", "page_created",
            "page_id", eventData.get("page_id"),
            "title", eventData.getOrDefault("title", "Untitled")
        );
    }

    /**
     * Process page.updated event
     */
    private Map<String, Object> processPageUpdatedEvent(Map<String, Object> payload) {
        log.debug("Processing Notion page.updated/page.content_updated event");

        @SuppressWarnings("unchecked")
        Map<String, Object> entity = (Map<String, Object>) payload.get("entity");

        @SuppressWarnings("unchecked")
        Map<String, Object> page;
        if (entity != null) {
            page = entity;
        } else {
            page = (Map<String, Object>) payload.get("data");
        }

        if (page == null) {
            log.warn("No page data in Notion page.updated event");
            return Map.of("warning", "Missing page data");
        }

        String pageId = (String) page.get("id");

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("page_id", pageId);
        eventData.put("event_type", "updated");

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) payload.get("data");
        if (data != null) {
            eventData.put("webhook_data", data);
        }

        triggerMatchingActions("page_updated", eventData);

        return Map.of(
            "action", "page_updated",
            "page_id", pageId
        );
    }

    /**
     * Extract event data from a Notion page object
     */
    private Map<String, Object> extractPageEventData(Map<String, Object> page, String eventType) {
        Map<String, Object> eventData = new HashMap<>();

        eventData.put("page_id", page.get("id"));
        eventData.put("title", extractNotionTitle(page));
        eventData.put("url", page.get("url"));
        eventData.put("created_time", page.get("created_time"));
        eventData.put("last_edited_time", page.get("last_edited_time"));

        @SuppressWarnings("unchecked")
        Map<String, Object> createdBy = (Map<String, Object>) page.get("created_by");
        if (createdBy != null) {
            eventData.put("created_by", createdBy.get("id"));
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> lastEditedBy = (Map<String, Object>) page.get("last_edited_by");
        if (lastEditedBy != null) {
            eventData.put("last_edited_by", lastEditedBy.get("id"));
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> parent = (Map<String, Object>) page.get("parent");
        if (parent != null) {
            String parentType = (String) parent.get("type");
            eventData.put("parent_type", parentType);
            eventData.put("parent_id", parent.get(parentType));
        }

        if (page.containsKey("properties")) {
            eventData.put("properties", page.get("properties"));
        }

        eventData.put("event_type", eventType);

        return eventData;
    }

    /**
     * Extract title from Notion page/database object
     */
    private String extractNotionTitle(Map<String, Object> object) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> titleArray = (List<Map<String, Object>>) object.get("title");
            if (titleArray != null && !titleArray.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> text = (Map<String, Object>) titleArray.get(0).get("text");
                if (text != null) {
                    return (String) text.get("content");
                }
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) object.get("properties");
            if (properties != null) {
                for (Map.Entry<String, Object> entry : properties.entrySet()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> prop = (Map<String, Object>) entry.getValue();
                    if ("title".equals(prop.get("type"))) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> propTitle = (List<Map<String, Object>>) prop.get("title");
                        if (propTitle != null && !propTitle.isEmpty()) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> text = (Map<String, Object>) propTitle.get(0).get("text");
                            if (text != null) {
                                return (String) text.get("content");
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error extracting Notion title: {}", e.getMessage());
        }

        return "Untitled";
    }

    /**
     * Trigger matching action instances for the given event
     */
    @Transactional
    private void triggerMatchingActions(String actionKey, Map<String, Object> eventData) {
        try {
            List<ActionInstance> matchingInstances = actionInstanceRepository
                .findEnabledActionInstancesByService("notion");

            log.debug("Found {} enabled Notion action instances, filtering by key: {}",
                matchingInstances.size(), actionKey);

            for (ActionInstance actionInstance : matchingInstances) {
                String definitionKey = actionInstance.getActionDefinition().getKey();

                if (definitionKey.equals(actionKey)) {
                    if (shouldTriggerInstance(actionInstance, eventData)) {
                        try {
                            log.info("Triggering AREA execution for Notion event: {} (instance: {})",
                                actionKey, actionInstance.getId());
                            executionTriggerService.triggerAreaExecution(
                                actionInstance,
                                ActivationModeType.WEBHOOK,
                                eventData
                            );
                        } catch (Exception e) {
                            log.error("Failed to trigger execution for Notion webhook event: {}",
                                e.getMessage(), e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to find matching Notion action instances: {}", e.getMessage(), e);
        }
    }

    /**
     * Check if an action instance should be triggered based on its parameters
     */
    private boolean shouldTriggerInstance(ActionInstance actionInstance, Map<String, Object> eventData) {
        Map<String, Object> params = actionInstance.getParams();
        if (params == null || params.isEmpty()) {
            return true;
        }

        String paramDatabaseId = (String) params.get("database_id");
        if (paramDatabaseId != null) {
            String eventDatabaseId = (String) eventData.get("database_id");
            String eventParentId = (String) eventData.get("parent_id");

            if (!paramDatabaseId.equals(eventDatabaseId) && !paramDatabaseId.equals(eventParentId)) {
                log.debug("Database ID filter mismatch: expected {}, got database_id={}, parent_id={}",
                    paramDatabaseId, eventDatabaseId, eventParentId);
                return false;
            }
        }

        String paramPageId = (String) params.get("page_id");
        if (paramPageId != null) {
            String eventPageId = (String) eventData.get("page_id");
            if (!paramPageId.equals(eventPageId)) {
                log.debug("Page ID filter mismatch: expected {}, got {}",
                    paramPageId, eventPageId);
                return false;
            }
        }

        return true;
    }
}
