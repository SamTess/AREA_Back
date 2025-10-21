package area.server.AREA_Back.service.Area.Services;

import area.server.AREA_Back.service.Area.Services.Google.GoogleApiUtils;
import area.server.AREA_Back.service.Area.Services.Google.GoogleGmailService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleActionService {

    private final GoogleApiUtils googleApiUtils;
    private final GoogleGmailService gmailService;
    private final MeterRegistry meterRegistry;

    private Counter googleActionsExecuted;
    private Counter googleActionsFailed;

    private Map<String, ActionHandler> actionHandlers;
    private Map<String, EventHandler> eventHandlers;

    @PostConstruct
    public void init() {
        googleActionsExecuted = meterRegistry.counter("google_actions_executed_total");
        googleActionsFailed = meterRegistry.counter("google_actions_failed_total");

        initializeActionHandlers();
        initializeEventHandlers();

        log.info("GoogleActionService initialized with metrics");
    }

    private void initializeActionHandlers() {
        actionHandlers = new HashMap<>();
        actionHandlers.put("gmail_send_email", gmailService::sendGmail);
        actionHandlers.put("gmail_add_label", gmailService::addGmailLabel);
    }

    private void initializeEventHandlers() {
        eventHandlers = new HashMap<>();
        eventHandlers.put("gmail_new_email", gmailService::checkNewGmailMessages);
    }

    @FunctionalInterface
    interface ActionHandler {
        Map<String, Object> execute(String token, Map<String, Object> inputPayload, Map<String, Object> actionParams);
    }

    @FunctionalInterface
    interface EventHandler {
        List<Map<String, Object>> check(String token, Map<String, Object> actionParams, LocalDateTime lastCheck);
    }

    public Map<String, Object> executeGoogleAction(
            String actionKey,
            Map<String, Object> inputPayload,
            Map<String, Object> actionParams,
            UUID userId) {

        try {
            googleActionsExecuted.increment();
            log.info("Executing Google action: {} for user: {}", actionKey, userId);

            String googleToken = googleApiUtils.getGoogleToken(userId);
            if (googleToken == null) {
                throw new RuntimeException("No Google token found for user: " + userId);
            }

            ActionHandler handler = actionHandlers.get(actionKey);

            if (handler == null) {
                throw new IllegalArgumentException("Unknown Google action: " + actionKey);
            }

            Map<String, Object> result = handler.execute(googleToken, inputPayload, actionParams);
            log.debug("Successfully executed Google action: {}", actionKey);
            return result;

        } catch (IllegalArgumentException e) {
            googleActionsFailed.increment();
            log.error("Invalid action key {}: {}", actionKey, e.getMessage());
            throw e;
        } catch (Exception e) {
            googleActionsFailed.increment();
            log.error("Failed to execute Google action {} for user {}: {}",
                actionKey, userId, e.getMessage(), e);
            throw new RuntimeException("Google action execution failed: " + e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> checkGoogleEvents(
            String actionKey,
            Map<String, Object> actionParams,
            UUID userId,
            LocalDateTime lastCheck) {

        try {
            log.debug("Checking Google events: {} for user: {} since {}", actionKey, userId, lastCheck);

            String googleToken = googleApiUtils.getGoogleToken(userId);
            if (googleToken == null) {
                log.warn("No Google token found for user: {}", userId);
                return Collections.emptyList();
            }

            EventHandler handler = eventHandlers.get(actionKey);

            if (handler == null) {
                log.warn("Unknown Google event action: {}", actionKey);
                return Collections.emptyList();
            }

            List<Map<String, Object>> events = handler.check(googleToken, actionParams, lastCheck);
            log.debug("Found {} events for action: {}", events.size(), actionKey);
            return events;

        } catch (Exception e) {
            log.error("Failed to check Google events {} for user {}: {}",
                actionKey, userId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }
}
