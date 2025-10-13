package area.server.AREA_Back.service.Area.Services;

import area.server.AREA_Back.repository.UserOAuthIdentityRepository;
import area.server.AREA_Back.repository.UserRepository;
import area.server.AREA_Back.service.Auth.ServiceAccountService;
import area.server.AREA_Back.service.Auth.TokenEncryptionService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleActionService {

    private static final String GOOGLE_API_BASE = "https://www.googleapis.com";
    private static final String GMAIL_API = GOOGLE_API_BASE + "/gmail/v1";
    private static final String CALENDAR_API = GOOGLE_API_BASE + "/calendar/v3";
    private static final String DRIVE_API = GOOGLE_API_BASE + "/drive/v3";
    private static final String SHEETS_API = GOOGLE_API_BASE + "/sheets/v4";
    private static final String GOOGLE_PROVIDER_KEY = "google";

    private final UserOAuthIdentityRepository userOAuthIdentityRepository;
    private final UserRepository userRepository;
    private final TokenEncryptionService tokenEncryptionService;
    private final ServiceAccountService serviceAccountService;
    private final RestTemplate restTemplate = new RestTemplate();

    private final MeterRegistry meterRegistry;
    private Counter googleActionsExecuted;
    private Counter googleActionsFailed;

    @PostConstruct
    public void init() {
        googleActionsExecuted = meterRegistry.counter("google_actions_executed_total");
        googleActionsFailed = meterRegistry.counter("google_actions_failed_total");
    }

    public Map<String, Object> executeGoogleAction(String actionKey,
                                                   Map<String, Object> inputPayload,
                                                   Map<String, Object> actionParams,
                                                   UUID userId) {
        try {
            googleActionsExecuted.increment();

            String googleToken = getGoogleToken(userId);
            if (googleToken == null) {
                throw new RuntimeException("No Google token found for user: " + userId);
            }

            return switch (actionKey) {
                case "gmail_send_email" -> sendGmail(googleToken, inputPayload, actionParams);
                case "gmail_add_label" -> addGmailLabel(googleToken, inputPayload, actionParams);
                case "calendar_create_event" -> createCalendarEvent(googleToken, inputPayload, actionParams);
                case "calendar_delete_event" -> deleteCalendarEvent(googleToken, inputPayload, actionParams);
                case "drive_create_folder" -> createDriveFolder(googleToken, inputPayload, actionParams);
                case "drive_upload_file" -> uploadDriveFile(googleToken, inputPayload, actionParams);
                case "drive_share_file" -> shareDriveFile(googleToken, inputPayload, actionParams);
                case "sheets_add_row" -> addSheetRow(googleToken, inputPayload, actionParams);
                case "sheets_update_cell" -> updateSheetCell(googleToken, inputPayload, actionParams);
                case "sheets_create_spreadsheet" -> createSpreadsheet(googleToken, inputPayload, actionParams);
                default -> throw new IllegalArgumentException("Unknown Google action: " + actionKey);
            };
        } catch (Exception e) {
            googleActionsFailed.increment();
            log.error("Failed to execute Google action {}: {}", actionKey, e.getMessage(), e);
            throw new RuntimeException("Google action execution failed: " + e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> checkGoogleEvents(String actionKey,
                                                        Map<String, Object> actionParams,
                                                        UUID userId,
                                                        LocalDateTime lastCheck) {
        try {
            log.debug("Checking Google events: {} for user: {}", actionKey, userId);

            String googleToken = getGoogleToken(userId);
            if (googleToken == null) {
                log.warn("No Google token found for user: {}", userId);
                return Collections.emptyList();
            }

            return switch (actionKey) {
                case "gmail_new_email" -> checkNewGmailMessages(googleToken, actionParams, lastCheck);
                case "calendar_new_event" -> checkNewCalendarEvents(googleToken, actionParams, lastCheck);
                case "calendar_event_starting" -> checkUpcomingCalendarEvents(googleToken, actionParams, lastCheck);
                case "drive_new_file" -> checkNewDriveFiles(googleToken, actionParams, lastCheck);
                case "drive_file_modified" -> checkModifiedDriveFiles(googleToken, actionParams, lastCheck);
                case "sheets_row_added" -> checkNewSheetRows(googleToken, actionParams, lastCheck);
                default -> {
                    log.warn("Unknown Google event action: {}", actionKey);
                    yield Collections.emptyList();
                }
            };
        } catch (Exception e) {
            log.error("Failed to check Google events {}: {}", actionKey, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private Map<String, Object> sendGmail(String token, Map<String, Object> input, Map<String, Object> params) {
        String to = getRequiredParam(params, "to", String.class);
        String subject = getRequiredParam(params, "subject", String.class);
        String body = getRequiredParam(params, "body", String.class);
        String cc = getOptionalParam(params, "cc", String.class, null);
        String bcc = getOptionalParam(params, "bcc", String.class, null);

        StringBuilder emailBuilder = new StringBuilder();
        emailBuilder.append("To: ").append(to).append("\r\n");
        if (cc != null && !cc.isEmpty()) {
            emailBuilder.append("Cc: ").append(cc).append("\r\n");
        }
        if (bcc != null && !bcc.isEmpty()) {
            emailBuilder.append("Bcc: ").append(bcc).append("\r\n");
        }
        emailBuilder.append("Subject: ").append(subject).append("\r\n");
        emailBuilder.append("\r\n");
        emailBuilder.append(body);

        String encodedEmail = Base64.getUrlEncoder().encodeToString(
            emailBuilder.toString().getBytes()
        ).replace("+", "-").replace("/", "_").replace("=", "");

        String url = GMAIL_API + "/users/me/messages/send";

        Map<String, Object> requestBody = Map.of("raw", encodedEmail);
        HttpHeaders headers = createGoogleHeaders(token);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.POST, request,
            new ParameterizedTypeReference<>() { }
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to send Gmail: " + response.getStatusCode());
        }

        Map<String, Object> responseBody = response.getBody();
        Map<String, Object> result = new HashMap<>();
        result.put("message_id", responseBody.get("id"));
        result.put("thread_id", responseBody.get("threadId"));
        result.put("sent_at", LocalDateTime.now().toString());

        return result;
    }

    private Map<String, Object> addGmailLabel(String token, Map<String, Object> input, Map<String, Object> params) {
        String messageId = getRequiredParam(params, "message_id", String.class);
        String labelName = getRequiredParam(params, "label_name", String.class);

        String url = GMAIL_API + "/users/me/messages/" + messageId + "/modify";

        Map<String, Object> requestBody = Map.of(
            "addLabelIds", List.of(labelName)
        );

        HttpHeaders headers = createGoogleHeaders(token);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.POST, request,
            new ParameterizedTypeReference<>() { }
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to add Gmail label: " + response.getStatusCode());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("message_id", messageId);
        result.put("labels", List.of(labelName));
        result.put("updated_at", LocalDateTime.now().toString());

        return result;
    }

    private List<Map<String, Object>> checkNewGmailMessages(String token,
                                                             Map<String, Object> params,
                                                             LocalDateTime lastCheck) {
        String label = getOptionalParam(params, "label", String.class, "INBOX");
        String from = getOptionalParam(params, "from", String.class, null);
        String subjectContains = getOptionalParam(params, "subject_contains", String.class, null);

        StringBuilder query = new StringBuilder("in:" + label);

        String afterDate = lastCheck.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        query.append(" after:").append(afterDate);

        if (from != null && !from.isEmpty()) {
            query.append(" from:").append(from);
        }
        if (subjectContains != null && !subjectContains.isEmpty()) {
            query.append(" subject:(").append(subjectContains).append(")");
        }

        String url = GMAIL_API + "/users/me/messages?q=" + java.net.URLEncoder.encode(
            query.toString(),
            java.nio.charset.StandardCharsets.UTF_8
        );

        HttpHeaders headers = createGoogleHeaders(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.GET, request,
            new ParameterizedTypeReference<>() { }
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            log.warn("Failed to fetch Gmail messages: {}", response.getStatusCode());
            return Collections.emptyList();
        }

        Map<String, Object> responseBody = response.getBody();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) responseBody.get("messages");

        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }

        final int maxMessages = 10;
        return messages.stream()
            .limit(maxMessages)
            .map(msg -> fetchGmailMessage(token, (String) msg.get("id")))
            .toList();
    }

    private Map<String, Object> fetchGmailMessage(String token, String messageId) {
        String url = GMAIL_API + "/users/me/messages/" + messageId;

        HttpHeaders headers = createGoogleHeaders(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.GET, request,
            new ParameterizedTypeReference<>() { }
        );

        Map<String, Object> message = response.getBody();
        Map<String, Object> event = new HashMap<>();
        event.put("message_id", message.get("id"));
        event.put("thread_id", message.get("threadId"));
        event.put("snippet", message.get("snippet"));

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) message.get("payload");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> emailHeaders = (List<Map<String, Object>>) payload.get("headers");

        for (Map<String, Object> header : emailHeaders) {
            String name = (String) header.get("name");
            String value = (String) header.get("value");
            switch (name.toLowerCase()) {
                case "from":
                    event.put("from", value);
                    break;
                case "to":
                    event.put("to", List.of(value.split(",")));
                    break;
                case "subject":
                    event.put("subject", value);
                    break;
                case "date":
                    event.put("received_at", value);
                    break;
                default:
                    // Ignore other headers
                    break;
            }
        }

        @SuppressWarnings("unchecked")
        List<String> labelIds = (List<String>) message.get("labelIds");
        List<String> labels;
        if (labelIds != null) {
            labels = labelIds;
        } else {
            labels = List.of();
        }
        event.put("labels", labels);

        return event;
    }

    private Map<String, Object> createCalendarEvent(
            String token,
            Map<String, Object> input,
            Map<String, Object> params) {
        String calendarId = getOptionalParam(params, "calendar_id", String.class, "primary");
        String summary = getRequiredParam(params, "summary", String.class);
        String description = getOptionalParam(params, "description", String.class, "");
        String location = getOptionalParam(params, "location", String.class, "");
        String startTime = getRequiredParam(params, "start_time", String.class);
        String endTime = getRequiredParam(params, "end_time", String.class);

        @SuppressWarnings("unchecked")
        List<String> attendees = getOptionalParam(params, "attendees", List.class, Collections.emptyList());

        String url = CALENDAR_API + "/calendars/" + calendarId + "/events";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("summary", summary);
        requestBody.put("description", description);
        requestBody.put("location", location);
        requestBody.put("start", Map.of("dateTime", startTime, "timeZone", "UTC"));
        requestBody.put("end", Map.of("dateTime", endTime, "timeZone", "UTC"));

        if (!attendees.isEmpty()) {
            List<Map<String, String>> attendeeList = attendees.stream()
                .map(email -> Map.of("email", email))
                .toList();
            requestBody.put("attendees", attendeeList);
        }

        HttpHeaders headers = createGoogleHeaders(token);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.POST, request,
            new ParameterizedTypeReference<>() { }
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to create calendar event: " + response.getStatusCode());
        }

        Map<String, Object> responseBody = response.getBody();
        Map<String, Object> result = new HashMap<>();
        result.put("event_id", responseBody.get("id"));
        result.put("html_link", responseBody.get("htmlLink"));
        result.put("created_at", responseBody.get("created"));

        return result;
    }

    private Map<String, Object> deleteCalendarEvent(
            String token,
            Map<String, Object> input,
            Map<String, Object> params) {
        String calendarId = getOptionalParam(params, "calendar_id", String.class, "primary");
        String eventId = getRequiredParam(params, "event_id", String.class);

        String url = CALENDAR_API + "/calendars/" + calendarId + "/events/" + eventId;

        HttpHeaders headers = createGoogleHeaders(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Void> response = restTemplate.exchange(
            url, HttpMethod.DELETE, request, Void.class
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to delete calendar event: " + response.getStatusCode());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("event_id", eventId);
        result.put("deleted", true);
        result.put("deleted_at", LocalDateTime.now().toString());

        return result;
    }

    private List<Map<String, Object>> checkNewCalendarEvents(String token,
                                                              Map<String, Object> params,
                                                              LocalDateTime lastCheck) {
        String calendarId = getOptionalParam(params, "calendar_id", String.class, "primary");
        final int minutesBack = 5;
        String timeMin = ZonedDateTime.now()
            .minusMinutes(minutesBack)
            .format(DateTimeFormatter.ISO_INSTANT);

        String url = CALENDAR_API + "/calendars/" + calendarId + "/events?timeMin="
            + timeMin + "&orderBy=startTime&singleEvents=true";

        HttpHeaders headers = createGoogleHeaders(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.GET, request,
            new ParameterizedTypeReference<>() { }
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            log.warn("Failed to fetch calendar events: {}", response.getStatusCode());
            return Collections.emptyList();
        }

        Map<String, Object> responseBody = response.getBody();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) responseBody.get("items");

        if (items == null) {
            return Collections.emptyList();
        }

        final int maxEvents = 10;
        return items.stream()
            .limit(maxEvents)
            .map(event -> parseCalendarEvent(event))
            .toList();
    }

    private List<Map<String, Object>> checkUpcomingCalendarEvents(String token,
                                                                   Map<String, Object> params,
                                                                   LocalDateTime lastCheck) {
        String calendarId = getOptionalParam(params, "calendar_id", String.class, "primary");
        final int defaultMinutes = 15;
        Integer minutesBefore = getOptionalParam(
            params,
            "minutes_before",
            Integer.class,
            defaultMinutes
        );

        ZonedDateTime now = ZonedDateTime.now();
        String timeMin = now.format(DateTimeFormatter.ISO_INSTANT);
        String timeMax = now.plusMinutes(minutesBefore).format(DateTimeFormatter.ISO_INSTANT);

        String url = CALENDAR_API + "/calendars/" + calendarId + "/events?timeMin=" + timeMin
            + "&timeMax=" + timeMax + "&orderBy=startTime&singleEvents=true";

        HttpHeaders headers = createGoogleHeaders(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.GET, request,
            new ParameterizedTypeReference<>() { }
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            log.warn("Failed to fetch upcoming calendar events: {}", response.getStatusCode());
            return Collections.emptyList();
        }

        Map<String, Object> responseBody = response.getBody();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) responseBody.get("items");

        if (items == null) {
            return Collections.emptyList();
        }

        return items.stream()
            .map(event -> parseCalendarEvent(event))
            .toList();
    }

    private Map<String, Object> parseCalendarEvent(Map<String, Object> event) {
        Map<String, Object> result = new HashMap<>();
        result.put("event_id", event.get("id"));
        result.put("summary", event.get("summary"));
        result.put("description", event.get("description"));
        result.put("location", event.get("location"));

        @SuppressWarnings("unchecked")
        Map<String, Object> start = (Map<String, Object>) event.get("start");
        @SuppressWarnings("unchecked")
        Map<String, Object> end = (Map<String, Object>) event.get("end");

        result.put("start_time", start.get("dateTime"));
        result.put("end_time", end.get("dateTime"));
        result.put("created_at", event.get("created"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> attendees = (List<Map<String, Object>>) event.get("attendees");
        if (attendees != null) {
            result.put("attendees", attendees.stream()
                .map(a -> a.get("email"))
                .toList());
        }

        return result;
    }

    private Map<String, Object> createDriveFolder(String token, Map<String, Object> input, Map<String, Object> params) {
        String name = getRequiredParam(params, "name", String.class);
        String parentFolderId = getOptionalParam(params, "parent_folder_id", String.class, null);

        String url = DRIVE_API + "/files";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("name", name);
        requestBody.put("mimeType", "application/vnd.google-apps.folder");

        if (parentFolderId != null && !parentFolderId.isEmpty()) {
            requestBody.put("parents", List.of(parentFolderId));
        }

        HttpHeaders headers = createGoogleHeaders(token);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.POST, request,
            new ParameterizedTypeReference<>() { }
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to create Drive folder: " + response.getStatusCode());
        }

        Map<String, Object> responseBody = response.getBody();
        Map<String, Object> result = new HashMap<>();
        result.put("folder_id", responseBody.get("id"));
        result.put("name", responseBody.get("name"));
        result.put("web_view_link", responseBody.get("webViewLink"));
        result.put("created_at", responseBody.get("createdTime"));

        return result;
    }

    private Map<String, Object> uploadDriveFile(String token, Map<String, Object> input, Map<String, Object> params) {
        String name = getRequiredParam(params, "name", String.class);
        String content = getRequiredParam(params, "content", String.class);
        String mimeType = getOptionalParam(params, "mime_type", String.class, "text/plain");
        String parentFolderId = getOptionalParam(params, "parent_folder_id", String.class, null);

        String url = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart";

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("name", name);
        metadata.put("mimeType", mimeType);

        if (parentFolderId != null && !parentFolderId.isEmpty()) {
            metadata.put("parents", List.of(parentFolderId));
        }

        String boundary = "boundary_" + System.currentTimeMillis();
        StringBuilder multipartBody = new StringBuilder();

        multipartBody.append("--").append(boundary).append("\r\n");
        multipartBody.append("Content-Type: application/json; charset=UTF-8\r\n\r\n");
        multipartBody.append(mapToJson(metadata)).append("\r\n");

        multipartBody.append("--").append(boundary).append("\r\n");
        multipartBody.append("Content-Type: ").append(mimeType).append("\r\n\r\n");
        multipartBody.append(content).append("\r\n");
        multipartBody.append("--").append(boundary).append("--");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.parseMediaType("multipart/related; boundary=" + boundary));

        HttpEntity<String> request = new HttpEntity<>(multipartBody.toString(), headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.POST, request,
            new ParameterizedTypeReference<>() { }
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to upload Drive file: " + response.getStatusCode());
        }

        Map<String, Object> responseBody = response.getBody();
        Map<String, Object> result = new HashMap<>();
        result.put("file_id", responseBody.get("id"));
        result.put("name", responseBody.get("name"));
        result.put("web_view_link", responseBody.get("webViewLink"));
        result.put("web_content_link", responseBody.get("webContentLink"));
        result.put("created_at", responseBody.get("createdTime"));
        result.put("size", responseBody.get("size"));

        return result;
    }

    private Map<String, Object> shareDriveFile(String token, Map<String, Object> input, Map<String, Object> params) {
        String fileId = getRequiredParam(params, "file_id", String.class);
        String email = getRequiredParam(params, "email", String.class);
        String role = getOptionalParam(params, "role", String.class, "reader");

        String url = DRIVE_API + "/files/" + fileId + "/permissions";

        Map<String, Object> requestBody = Map.of(
            "type", "user",
            "role", role,
            "emailAddress", email
        );

        HttpHeaders headers = createGoogleHeaders(token);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.POST, request,
            new ParameterizedTypeReference<>() { }
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to share Drive file: " + response.getStatusCode());
        }

        Map<String, Object> responseBody = response.getBody();
        Map<String, Object> result = new HashMap<>();
        result.put("permission_id", responseBody.get("id"));
        result.put("email", email);
        result.put("role", role);
        result.put("shared_at", LocalDateTime.now().toString());

        return result;
    }

    private List<Map<String, Object>> checkNewDriveFiles(String token,
                                                          Map<String, Object> params,
                                                          LocalDateTime lastCheck) {
        String folderId = getOptionalParam(params, "folder_id", String.class, null);
        String fileType = getOptionalParam(params, "file_type", String.class, "any");

        StringBuilder query = new StringBuilder("trashed=false");

        if (folderId != null && !folderId.isEmpty()) {
            query.append(" and '").append(folderId).append("' in parents");
        }

        if (!"any".equals(fileType)) {
            String mimeType = switch (fileType) {
                case "document" -> "application/vnd.google-apps.document";
                case "spreadsheet" -> "application/vnd.google-apps.spreadsheet";
                case "presentation" -> "application/vnd.google-apps.presentation";
                case "folder" -> "application/vnd.google-apps.folder";
                case "pdf" -> "application/pdf";
                case "image" -> "image/";
                default -> null;
            };

            if (mimeType != null) {
                if (mimeType.endsWith("/")) {
                    query.append(" and mimeType contains '").append(mimeType).append("'");
                } else {
                    query.append(" and mimeType='").append(mimeType).append("'");
                }
            }
        }

        String url = DRIVE_API + "/files?q=" + query + "&orderBy=createdTime desc&pageSize=10";

        HttpHeaders headers = createGoogleHeaders(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.GET, request,
            new ParameterizedTypeReference<>() { }
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            log.warn("Failed to fetch Drive files: {}", response.getStatusCode());
            return Collections.emptyList();
        }

        Map<String, Object> responseBody = response.getBody();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> files = (List<Map<String, Object>>) responseBody.get("files");

        if (files == null) {
            return Collections.emptyList();
        }

        return files.stream()
            .map(this::parseDriveFile)
            .toList();
    }

    private List<Map<String, Object>> checkModifiedDriveFiles(String token,
                                                               Map<String, Object> params,
                                                               LocalDateTime lastCheck) {
        String folderId = getOptionalParam(params, "folder_id", String.class, null);
        String fileType = getOptionalParam(params, "file_type", String.class, "any");

        StringBuilder query = new StringBuilder("trashed=false");

        String modifiedTime = lastCheck.format(DateTimeFormatter.ISO_INSTANT);
        query.append(" and modifiedTime > '").append(modifiedTime).append("'");

        if (folderId != null && !folderId.isEmpty()) {
            query.append(" and '").append(folderId).append("' in parents");
        }

        if (!"any".equals(fileType)) {
            String mimeType = switch (fileType) {
                case "document" -> "application/vnd.google-apps.document";
                case "spreadsheet" -> "application/vnd.google-apps.spreadsheet";
                case "presentation" -> "application/vnd.google-apps.presentation";
                case "folder" -> "application/vnd.google-apps.folder";
                case "pdf" -> "application/pdf";
                case "image" -> "image/";
                default -> null;
            };

            if (mimeType != null) {
                if (mimeType.endsWith("/")) {
                    query.append(" and mimeType contains '").append(mimeType).append("'");
                } else {
                    query.append(" and mimeType='").append(mimeType).append("'");
                }
            }
        }

        String url = DRIVE_API + "/files?q=" + query + "&orderBy=modifiedTime desc&pageSize=10"
            + "&fields=files(id,name,mimeType,webViewLink,createdTime,modifiedTime,owners,version)";

        HttpHeaders headers = createGoogleHeaders(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.GET, request,
            new ParameterizedTypeReference<>() { }
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            log.warn("Failed to fetch modified Drive files: {}", response.getStatusCode());
            return Collections.emptyList();
        }

        Map<String, Object> responseBody = response.getBody();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> files = (List<Map<String, Object>>) responseBody.get("files");

        if (files == null) {
            return Collections.emptyList();
        }

        return files.stream()
            .map(file -> {
                Map<String, Object> result = parseDriveFile(file);
                result.put("version", file.get("version"));
                result.put("event_type", "modified");
                return result;
            })
            .toList();
    }

    private Map<String, Object> parseDriveFile(Map<String, Object> file) {
        Map<String, Object> result = new HashMap<>();
        result.put("file_id", file.get("id"));
        result.put("name", file.get("name"));
        result.put("mime_type", file.get("mimeType"));
        result.put("web_view_link", file.get("webViewLink"));
        result.put("created_time", file.get("createdTime"));
        result.put("modified_time", file.get("modifiedTime"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> owners = (List<Map<String, Object>>) file.get("owners");
        if (owners != null && !owners.isEmpty()) {
            result.put("owner", owners.get(0).get("emailAddress"));
        }

        return result;
    }

    private Map<String, Object> addSheetRow(String token, Map<String, Object> input, Map<String, Object> params) {
        String spreadsheetId = getRequiredParam(params, "spreadsheet_id", String.class);
        String sheetName = getOptionalParam(params, "sheet_name", String.class, "Sheet1");

        @SuppressWarnings("unchecked")
        List<String> values = getRequiredParam(params, "values", List.class);

        String range = sheetName + "!A:Z";
        String url = SHEETS_API + "/spreadsheets/" + spreadsheetId + "/values/" + range
            + ":append?valueInputOption=USER_ENTERED";

        Map<String, Object> requestBody = Map.of(
            "values", List.of(values)
        );

        HttpHeaders headers = createGoogleHeaders(token);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.POST, request,
            new ParameterizedTypeReference<>() { }
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to add row to Sheet: " + response.getStatusCode());
        }

        Map<String, Object> responseBody = response.getBody();
        Map<String, Object> result = new HashMap<>();
        result.put("spreadsheet_id", spreadsheetId);
        result.put("sheet_name", sheetName);
        result.put("updated_range", responseBody.get("updates"));
        result.put("updated_rows", 1);
        result.put("updated_at", LocalDateTime.now().toString());

        return result;
    }

    private Map<String, Object> updateSheetCell(String token, Map<String, Object> input, Map<String, Object> params) {
        String spreadsheetId = getRequiredParam(params, "spreadsheet_id", String.class);
        String sheetName = getOptionalParam(params, "sheet_name", String.class, "Sheet1");
        String cell = getRequiredParam(params, "cell", String.class);
        String value = getRequiredParam(params, "value", String.class);

        String range = sheetName + "!" + cell;
        String url = SHEETS_API + "/spreadsheets/" + spreadsheetId + "/values/" + range
            + "?valueInputOption=USER_ENTERED";

        Map<String, Object> requestBody = Map.of(
            "values", List.of(List.of(value))
        );

        HttpHeaders headers = createGoogleHeaders(token);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.PUT, request,
            new ParameterizedTypeReference<>() { }
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to update Sheet cell: " + response.getStatusCode());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("spreadsheet_id", spreadsheetId);
        result.put("updated_range", range);
        result.put("updated_cells", 1);
        result.put("updated_at", LocalDateTime.now().toString());

        return result;
    }

    private Map<String, Object> createSpreadsheet(String token, Map<String, Object> input, Map<String, Object> params) {
        String title = getRequiredParam(params, "title", String.class);

        @SuppressWarnings("unchecked")
        List<String> sheetNames = getOptionalParam(params, "sheet_names", List.class, List.of("Sheet1"));

        String url = SHEETS_API + "/spreadsheets";

        List<Map<String, Object>> sheets = new ArrayList<>();
        for (String sheetName : sheetNames) {
            sheets.add(Map.of("properties", Map.of("title", sheetName)));
        }

        Map<String, Object> requestBody = Map.of(
            "properties", Map.of("title", title),
            "sheets", sheets
        );

        HttpHeaders headers = createGoogleHeaders(token);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.POST, request,
            new ParameterizedTypeReference<>() { }
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to create Spreadsheet: " + response.getStatusCode());
        }

        Map<String, Object> responseBody = response.getBody();
        Map<String, Object> result = new HashMap<>();
        result.put("spreadsheet_id", responseBody.get("spreadsheetId"));
        result.put("spreadsheet_url", responseBody.get("spreadsheetUrl"));
        result.put("created_at", LocalDateTime.now().toString());

        return result;
    }

    private List<Map<String, Object>> checkNewSheetRows(String token,
                                                         Map<String, Object> params,
                                                         LocalDateTime lastCheck) {
        String spreadsheetId = getRequiredParam(params, "spreadsheet_id", String.class);
        String sheetName = getOptionalParam(params, "sheet_name", String.class, "Sheet1");
        Integer lastKnownRowCount = getOptionalParam(params, "last_row_count", Integer.class, 0);

        String range = sheetName + "!A:Z";
        String url = SHEETS_API + "/spreadsheets/" + spreadsheetId + "/values/" + range;

        HttpHeaders headers = createGoogleHeaders(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.GET, request,
            new ParameterizedTypeReference<>() { }
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            log.warn("Failed to fetch Sheet data: {}", response.getStatusCode());
            return Collections.emptyList();
        }

        Map<String, Object> responseBody = response.getBody();
        @SuppressWarnings("unchecked")
        List<List<Object>> values = (List<List<Object>>) responseBody.get("values");

        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }

        int currentRowCount = values.size();

        // If no rows were added since last check
        if (currentRowCount <= lastKnownRowCount) {
            return Collections.emptyList();
        }

        // Get only the new rows
        List<Map<String, Object>> newRows = new ArrayList<>();
        for (int i = lastKnownRowCount; i < currentRowCount; i++) {
            Map<String, Object> rowEvent = new HashMap<>();
            rowEvent.put("spreadsheet_id", spreadsheetId);
            rowEvent.put("sheet_name", sheetName);
            rowEvent.put("row_number", i + 1);
            rowEvent.put("row_data", values.get(i));
            rowEvent.put("detected_at", LocalDateTime.now().toString());
            rowEvent.put("current_row_count", currentRowCount);
            newRows.add(rowEvent);
        }

        // Limit to avoid too many events at once
        final int maxNewRows = 20;
        if (newRows.size() > maxNewRows) {
            return newRows.stream().limit(maxNewRows).toList();
        }

        return newRows;
    }

    private String getGoogleToken(UUID userId) {
        Optional<String> serviceToken = serviceAccountService.getAccessToken(userId, GOOGLE_PROVIDER_KEY);
        if (serviceToken.isPresent()) {
            log.debug("Google token found in service accounts for user: {}", userId);
            return serviceToken.get();
        }

        Optional<area.server.AREA_Back.entity.User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            log.warn("User not found: {}", userId);
            return null;
        }

        Optional<area.server.AREA_Back.entity.UserOAuthIdentity> oauthOpt =
            userOAuthIdentityRepository.findByUserAndProvider(userOpt.get(), GOOGLE_PROVIDER_KEY);

        if (oauthOpt.isEmpty()) {
            log.warn("No Google OAuth identity found for user: {}", userId);
            return null;
        }

        area.server.AREA_Back.entity.UserOAuthIdentity oauth = oauthOpt.get();
        String encryptedToken = oauth.getAccessTokenEnc();

        if (encryptedToken == null || encryptedToken.trim().isEmpty()) {
            log.warn("Google token is null or empty for user: {}", userId);
            return null;
        }

        try {
            String decryptedToken = tokenEncryptionService.decryptToken(encryptedToken);
            log.debug("Google token successfully decrypted for user: {}", userId);
            return decryptedToken;
        } catch (Exception e) {
            log.error("Error decrypting Google token for user {}: {}", userId, e.getMessage());
            return null;
        }
    }

    private HttpHeaders createGoogleHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    @SuppressWarnings("unchecked")
    private <T> T getRequiredParam(Map<String, Object> params, String key, Class<T> type) {
        Object value = params.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Required parameter missing: " + key);
        }
        return (T) value;
    }

    @SuppressWarnings("unchecked")
    private <T> T getOptionalParam(
            Map<String, Object> params,
            String key,
            Class<T> type,
            T defaultValue) {
        Object value = params.get(key);
        T result;
        if (value != null) {
            result = (T) value;
        } else {
            result = defaultValue;
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private String mapToJson(Map<String, Object> map) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                json.append(",");
            }
            first = false;

            json.append("\"").append(entry.getKey()).append("\":");

            Object value = entry.getValue();
            if (value == null) {
                json.append("null");
            } else if (value instanceof String) {
                json.append("\"").append(escapeJson((String) value)).append("\"");
            } else if (value instanceof Number || value instanceof Boolean) {
                json.append(value);
            } else if (value instanceof List) {
                json.append(listToJson((List<?>) value));
            } else if (value instanceof Map) {
                json.append(mapToJson((Map<String, Object>) value));
            } else {
                json.append("\"").append(escapeJson(value.toString())).append("\"");
            }
        }

        json.append("}");
        return json.toString();
    }

    @SuppressWarnings("unchecked")
    private String listToJson(List<?> list) {
        StringBuilder json = new StringBuilder("[");
        boolean first = true;

        for (Object item : list) {
            if (!first) {
                json.append(",");
            }
            first = false;

            if (item == null) {
                json.append("null");
            } else if (item instanceof String) {
                json.append("\"").append(escapeJson((String) item)).append("\"");
            } else if (item instanceof Number || item instanceof Boolean) {
                json.append(item);
            } else if (item instanceof List) {
                json.append(listToJson((List<?>) item));
            } else if (item instanceof Map) {
                json.append(mapToJson((Map<String, Object>) item));
            } else {
                json.append("\"").append(escapeJson(item.toString())).append("\"");
            }
        }

        json.append("]");
        return json.toString();
    }

    private String escapeJson(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
