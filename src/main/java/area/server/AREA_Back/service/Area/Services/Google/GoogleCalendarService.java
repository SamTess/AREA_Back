package area.server.AREA_Back.service.Area.Services.Google;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for handling Google Calendar operations.
 * Provides methods for creating/deleting events and checking for new or upcoming events.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleCalendarService {

    private final GoogleApiUtils utils;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Create a new calendar event.
     *
     * @param token Google OAuth token
     * @param input Input payload (unused)
     * @param params Parameters including summary, description, location, start_time, end_time, attendees
     * @return Result map with event_id, html_link, created_at
     */
    public Map<String, Object> createCalendarEvent(
            String token,
            Map<String, Object> input,
            Map<String, Object> params) {
        String calendarId = utils.getOptionalParam(params, "calendar_id", String.class, "primary");
        String summary = utils.getRequiredParam(params, "summary", String.class);
        String description = utils.getOptionalParam(params, "description", String.class, "");
        String location = utils.getOptionalParam(params, "location", String.class, "");
        String startTime = utils.getRequiredParam(params, "start_time", String.class);
        String endTime = utils.getRequiredParam(params, "end_time", String.class);

        @SuppressWarnings("unchecked")
        List<String> attendees = utils.getOptionalParam(params, "attendees", List.class, Collections.emptyList());

        String url = GoogleApiUtils.CALENDAR_API + "/calendars/" + calendarId + "/events";

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

        HttpHeaders headers = utils.createGoogleHeaders(token);
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

    /**
     * Delete a calendar event.
     *
     * @param token Google OAuth token
     * @param input Input payload (unused)
     * @param params Parameters including calendar_id, event_id
     * @return Result map with event_id, deleted, deleted_at
     */
    public Map<String, Object> deleteCalendarEvent(
            String token,
            Map<String, Object> input,
            Map<String, Object> params) {
        String calendarId = utils.getOptionalParam(params, "calendar_id", String.class, "primary");
        String eventId = utils.getRequiredParam(params, "event_id", String.class);

        String url = GoogleApiUtils.CALENDAR_API + "/calendars/" + calendarId + "/events/" + eventId;

        HttpHeaders headers = utils.createGoogleHeaders(token);
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

    /**
     * Check for new calendar events.
     *
     * @param token Google OAuth token
     * @param params Parameters including calendar_id
     * @param lastCheck Timestamp of the last check
     * @return List of new events
     */
    public List<Map<String, Object>> checkNewCalendarEvents(String token,
                                                              Map<String, Object> params,
                                                              LocalDateTime lastCheck) {
        String calendarId = utils.getOptionalParam(params, "calendar_id", String.class, "primary");
        final int minutesBack = 5;
        String timeMin = ZonedDateTime.now()
            .minusMinutes(minutesBack)
            .format(DateTimeFormatter.ISO_INSTANT);

        String url = GoogleApiUtils.CALENDAR_API + "/calendars/" + calendarId + "/events?timeMin="
            + timeMin + "&orderBy=startTime&singleEvents=true";

        HttpHeaders headers = utils.createGoogleHeaders(token);
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
            .map(this::parseCalendarEvent)
            .toList();
    }

    /**
     * Check for upcoming calendar events.
     *
     * @param token Google OAuth token
     * @param params Parameters including calendar_id, minutes_before
     * @param lastCheck Timestamp of the last check
     * @return List of upcoming events
     */
    public List<Map<String, Object>> checkUpcomingCalendarEvents(String token,
                                                                   Map<String, Object> params,
                                                                   LocalDateTime lastCheck) {
        String calendarId = utils.getOptionalParam(params, "calendar_id", String.class, "primary");
        final int defaultMinutes = 15;
        Integer minutesBefore = utils.getOptionalParam(
            params,
            "minutes_before",
            Integer.class,
            defaultMinutes
        );

        ZonedDateTime now = ZonedDateTime.now();
        String timeMin = now.format(DateTimeFormatter.ISO_INSTANT);
        String timeMax = now.plusMinutes(minutesBefore).format(DateTimeFormatter.ISO_INSTANT);

        String url = GoogleApiUtils.CALENDAR_API + "/calendars/" + calendarId + "/events?timeMin=" + timeMin
            + "&timeMax=" + timeMax + "&orderBy=startTime&singleEvents=true";

        HttpHeaders headers = utils.createGoogleHeaders(token);
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
            .map(this::parseCalendarEvent)
            .toList();
    }

    /**
     * Parse a calendar event from Google API response.
     *
     * @param event The event data from API
     * @return Parsed event map
     */
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
}
