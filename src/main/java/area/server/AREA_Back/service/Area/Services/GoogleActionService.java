package area.server.AREA_Back.service.Area.Services;

import area.server.AREA_Back.service.Area.Services.Google.GoogleApiUtils;
import area.server.AREA_Back.service.Area.Services.Google.GoogleCalendarService;
import area.server.AREA_Back.service.Area.Services.Google.GoogleDriveService;
import area.server.AREA_Back.service.Area.Services.Google.GoogleGmailService;
import area.server.AREA_Back.service.Area.Services.Google.GoogleSheetsService;
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
    private final GoogleCalendarService calendarService;
    private final GoogleDriveService driveService;
    private final GoogleSheetsService sheetsService;
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
        actionHandlers.put("calendar_create_event", calendarService::createCalendarEvent);
        actionHandlers.put("calendar_delete_event", calendarService::deleteCalendarEvent);
        actionHandlers.put("drive_create_folder", driveService::createDriveFolder);
        actionHandlers.put("drive_upload_file", driveService::uploadDriveFile);
        actionHandlers.put("drive_share_file", driveService::shareDriveFile);
        actionHandlers.put("sheets_add_row", sheetsService::addSheetRow);
        actionHandlers.put("sheets_update_cell", sheetsService::updateSheetCell);
        actionHandlers.put("sheets_create_spreadsheet", sheetsService::createSpreadsheet);
    }

    private void initializeEventHandlers() {
        eventHandlers = new HashMap<>();
        eventHandlers.put("gmail_new_email", gmailService::checkNewGmailMessages);
        eventHandlers.put("calendar_new_event", calendarService::checkNewCalendarEvents);
        eventHandlers.put("calendar_event_starting", calendarService::checkUpcomingCalendarEvents);
        eventHandlers.put("drive_new_file", driveService::checkNewDriveFiles);
        eventHandlers.put("drive_file_modified", driveService::checkModifiedDriveFiles);
        eventHandlers.put("sheets_row_added", sheetsService::checkNewSheetRows);
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
