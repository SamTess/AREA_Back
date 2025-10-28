package area.server.AREA_Back.service.Google;

import area.server.AREA_Back.service.Area.Services.Google.GoogleApiUtils;
import area.server.AREA_Back.service.Area.Services.Google.GoogleCalendarService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GoogleCalendarService
 * Tests Calendar operations: creating/deleting events and checking for new or upcoming events
 */
@ExtendWith(MockitoExtension.class)
class GoogleCalendarServiceTest {

    @Mock
    private GoogleApiUtils googleApiUtils;

    @Mock
    private RestTemplate restTemplate;

    private GoogleCalendarService googleCalendarService;

    @BeforeEach
    void setUp() {
        googleCalendarService = new GoogleCalendarService(googleApiUtils);
        
        // Inject mocked RestTemplate
        try {
            var field = GoogleCalendarService.class.getDeclaredField("restTemplate");
            field.setAccessible(true);
            field.set(googleCalendarService, restTemplate);
        } catch (Exception e) {
            fail("Failed to inject RestTemplate: " + e.getMessage());
        }
    }

    @Test
    void testCreateCalendarEventSuccess() {
        // Given
        String token = "valid-token";
        Map<String, Object> input = new HashMap<>();
        Map<String, Object> params = new HashMap<>();
        params.put("summary", "Team Meeting");
        params.put("start_time", "2025-01-15T10:00:00Z");
        params.put("end_time", "2025-01-15T11:00:00Z");

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("id", "event-123");
        responseBody.put("htmlLink", "https://calendar.google.com/event-123");
        responseBody.put("created", "2025-01-01T10:00:00Z");

        when(googleApiUtils.getOptionalParam(params, "calendar_id", String.class, "primary")).thenReturn("primary");
        when(googleApiUtils.getRequiredParam(params, "summary", String.class)).thenReturn("Team Meeting");
        when(googleApiUtils.getOptionalParam(params, "description", String.class, "")).thenReturn("");
        when(googleApiUtils.getOptionalParam(params, "location", String.class, "")).thenReturn("");
        when(googleApiUtils.getRequiredParam(params, "start_time", String.class)).thenReturn("2025-01-15T10:00:00Z");
        when(googleApiUtils.getRequiredParam(params, "end_time", String.class)).thenReturn("2025-01-15T11:00:00Z");
        when(googleApiUtils.getOptionalParam(eq(params), eq("attendees"), eq(List.class), anyList())).thenReturn(Collections.emptyList());
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        // When
        Map<String, Object> result = googleCalendarService.createCalendarEvent(token, input, params);

        // Then
        assertNotNull(result);
        assertEquals("event-123", result.get("event_id"));
        assertEquals("https://calendar.google.com/event-123", result.get("html_link"));
        assertEquals("2025-01-01T10:00:00Z", result.get("created_at"));
    }

    @Test
    void testCreateCalendarEventWithAttendees() {
        // Given
        String token = "valid-token";
        Map<String, Object> input = new HashMap<>();
        Map<String, Object> params = new HashMap<>();
        params.put("summary", "Project Review");
        params.put("description", "Quarterly review");
        params.put("location", "Conference Room A");
        params.put("start_time", "2025-01-20T14:00:00Z");
        params.put("end_time", "2025-01-20T15:00:00Z");
        params.put("attendees", Arrays.asList("user1@example.com", "user2@example.com"));

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("id", "event-456");
        responseBody.put("htmlLink", "https://calendar.google.com/event-456");
        responseBody.put("created", "2025-01-01T11:00:00Z");

        when(googleApiUtils.getOptionalParam(params, "calendar_id", String.class, "primary")).thenReturn("primary");
        when(googleApiUtils.getRequiredParam(params, "summary", String.class)).thenReturn("Project Review");
        when(googleApiUtils.getOptionalParam(params, "description", String.class, "")).thenReturn("Quarterly review");
        when(googleApiUtils.getOptionalParam(params, "location", String.class, "")).thenReturn("Conference Room A");
        when(googleApiUtils.getRequiredParam(params, "start_time", String.class)).thenReturn("2025-01-20T14:00:00Z");
        when(googleApiUtils.getRequiredParam(params, "end_time", String.class)).thenReturn("2025-01-20T15:00:00Z");
        when(googleApiUtils.getOptionalParam(eq(params), eq("attendees"), eq(List.class), anyList()))
            .thenReturn(Arrays.asList("user1@example.com", "user2@example.com"));
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        // When
        Map<String, Object> result = googleCalendarService.createCalendarEvent(token, input, params);

        // Then
        assertNotNull(result);
        assertEquals("event-456", result.get("event_id"));
    }

    @Test
    void testCreateCalendarEventFailure() {
        // Given
        String token = "valid-token";
        Map<String, Object> input = new HashMap<>();
        Map<String, Object> params = new HashMap<>();
        params.put("summary", "Test Event");
        params.put("start_time", "2025-01-15T10:00:00Z");
        params.put("end_time", "2025-01-15T11:00:00Z");

        when(googleApiUtils.getOptionalParam(params, "calendar_id", String.class, "primary")).thenReturn("primary");
        when(googleApiUtils.getRequiredParam(params, "summary", String.class)).thenReturn("Test Event");
        when(googleApiUtils.getOptionalParam(params, "description", String.class, "")).thenReturn("");
        when(googleApiUtils.getOptionalParam(params, "location", String.class, "")).thenReturn("");
        when(googleApiUtils.getRequiredParam(params, "start_time", String.class)).thenReturn("2025-01-15T10:00:00Z");
        when(googleApiUtils.getRequiredParam(params, "end_time", String.class)).thenReturn("2025-01-15T11:00:00Z");
        when(googleApiUtils.getOptionalParam(eq(params), eq("attendees"), eq(List.class), anyList())).thenReturn(Collections.emptyList());
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(HttpStatus.BAD_REQUEST));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            googleCalendarService.createCalendarEvent(token, input, params);
        });
    }

    @Test
    void testDeleteCalendarEventSuccess() {
        // Given
        String token = "valid-token";
        Map<String, Object> input = new HashMap<>();
        Map<String, Object> params = new HashMap<>();
        params.put("event_id", "event-123");

        when(googleApiUtils.getOptionalParam(params, "calendar_id", String.class, "primary")).thenReturn("primary");
        when(googleApiUtils.getRequiredParam(params, "event_id", String.class)).thenReturn("event-123");
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.DELETE),
            any(HttpEntity.class),
            eq(Void.class)
        )).thenReturn(new ResponseEntity<>(HttpStatus.NO_CONTENT));

        // When
        Map<String, Object> result = googleCalendarService.deleteCalendarEvent(token, input, params);

        // Then
        assertNotNull(result);
        assertEquals("event-123", result.get("event_id"));
        assertEquals(true, result.get("deleted"));
        assertNotNull(result.get("deleted_at"));
    }

    @Test
    void testDeleteCalendarEventFailure() {
        // Given
        String token = "valid-token";
        Map<String, Object> input = new HashMap<>();
        Map<String, Object> params = new HashMap<>();
        params.put("event_id", "event-123");

        when(googleApiUtils.getOptionalParam(params, "calendar_id", String.class, "primary")).thenReturn("primary");
        when(googleApiUtils.getRequiredParam(params, "event_id", String.class)).thenReturn("event-123");
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.DELETE),
            any(HttpEntity.class),
            eq(Void.class)
        )).thenReturn(new ResponseEntity<>(HttpStatus.NOT_FOUND));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            googleCalendarService.deleteCalendarEvent(token, input, params);
        });
    }

    @Test
    void testCheckNewCalendarEventsSuccess() {
        // Given
        String token = "valid-token";
        Map<String, Object> params = new HashMap<>();
        LocalDateTime lastCheck = LocalDateTime.now().minusMinutes(30);

        Map<String, Object> event1 = new HashMap<>();
        event1.put("id", "event-1");
        event1.put("summary", "Meeting 1");
        event1.put("description", "First meeting");
        event1.put("location", "Room A");
        event1.put("created", "2025-01-01T10:00:00Z");
        event1.put("start", Map.of("dateTime", "2025-01-15T10:00:00Z"));
        event1.put("end", Map.of("dateTime", "2025-01-15T11:00:00Z"));
        event1.put("attendees", Arrays.asList(
            Map.of("email", "user1@example.com"),
            Map.of("email", "user2@example.com")
        ));

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("items", Collections.singletonList(event1));

        when(googleApiUtils.getOptionalParam(params, "calendar_id", String.class, "primary")).thenReturn("primary");
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        // When
        List<Map<String, Object>> result = googleCalendarService.checkNewCalendarEvents(token, params, lastCheck);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("event-1", result.get(0).get("event_id"));
        assertEquals("Meeting 1", result.get(0).get("summary"));
    }

    @Test
    void testCheckNewCalendarEventsFailure() {
        // Given
        String token = "valid-token";
        Map<String, Object> params = new HashMap<>();
        LocalDateTime lastCheck = LocalDateTime.now().minusMinutes(30);

        when(googleApiUtils.getOptionalParam(params, "calendar_id", String.class, "primary")).thenReturn("primary");
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(HttpStatus.UNAUTHORIZED));

        // When
        List<Map<String, Object>> result = googleCalendarService.checkNewCalendarEvents(token, params, lastCheck);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testCheckNewCalendarEventsNullItems() {
        // Given
        String token = "valid-token";
        Map<String, Object> params = new HashMap<>();
        LocalDateTime lastCheck = LocalDateTime.now().minusMinutes(30);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("items", null);

        when(googleApiUtils.getOptionalParam(params, "calendar_id", String.class, "primary")).thenReturn("primary");
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        // When
        List<Map<String, Object>> result = googleCalendarService.checkNewCalendarEvents(token, params, lastCheck);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testCheckUpcomingCalendarEventsSuccess() {
        // Given
        String token = "valid-token";
        Map<String, Object> params = new HashMap<>();
        LocalDateTime lastCheck = LocalDateTime.now().minusMinutes(30);

        Map<String, Object> event1 = new HashMap<>();
        event1.put("id", "upcoming-1");
        event1.put("summary", "Upcoming Meeting");
        event1.put("description", "Soon");
        event1.put("location", "Online");
        event1.put("created", "2025-01-01T09:00:00Z");
        event1.put("start", Map.of("dateTime", "2025-01-15T10:00:00Z"));
        event1.put("end", Map.of("dateTime", "2025-01-15T10:30:00Z"));
        event1.put("attendees", null);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("items", Collections.singletonList(event1));

        when(googleApiUtils.getOptionalParam(params, "calendar_id", String.class, "primary")).thenReturn("primary");
        when(googleApiUtils.getOptionalParam(params, "minutes_before", Integer.class, 15)).thenReturn(15);
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        // When
        List<Map<String, Object>> result = googleCalendarService.checkUpcomingCalendarEvents(token, params, lastCheck);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("upcoming-1", result.get(0).get("event_id"));
    }

    @Test
    void testCheckUpcomingCalendarEventsCustomMinutes() {
        // Given
        String token = "valid-token";
        Map<String, Object> params = new HashMap<>();
        params.put("minutes_before", 30);
        LocalDateTime lastCheck = LocalDateTime.now().minusMinutes(30);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("items", Collections.emptyList());

        when(googleApiUtils.getOptionalParam(params, "calendar_id", String.class, "primary")).thenReturn("primary");
        when(googleApiUtils.getOptionalParam(params, "minutes_before", Integer.class, 15)).thenReturn(30);
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        // When
        List<Map<String, Object>> result = googleCalendarService.checkUpcomingCalendarEvents(token, params, lastCheck);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testCheckUpcomingCalendarEventsFailure() {
        // Given
        String token = "valid-token";
        Map<String, Object> params = new HashMap<>();
        LocalDateTime lastCheck = LocalDateTime.now().minusMinutes(30);

        when(googleApiUtils.getOptionalParam(params, "calendar_id", String.class, "primary")).thenReturn("primary");
        when(googleApiUtils.getOptionalParam(params, "minutes_before", Integer.class, 15)).thenReturn(15);
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));

        // When
        List<Map<String, Object>> result = googleCalendarService.checkUpcomingCalendarEvents(token, params, lastCheck);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testCheckUpcomingCalendarEventsNullItems() {
        // Given
        String token = "valid-token";
        Map<String, Object> params = new HashMap<>();
        LocalDateTime lastCheck = LocalDateTime.now().minusMinutes(30);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("items", null);

        when(googleApiUtils.getOptionalParam(params, "calendar_id", String.class, "primary")).thenReturn("primary");
        when(googleApiUtils.getOptionalParam(params, "minutes_before", Integer.class, 15)).thenReturn(15);
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        // When
        List<Map<String, Object>> result = googleCalendarService.checkUpcomingCalendarEvents(token, params, lastCheck);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
