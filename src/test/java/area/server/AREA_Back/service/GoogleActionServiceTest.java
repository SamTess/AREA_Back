package area.server.AREA_Back.service;

import area.server.AREA_Back.service.Area.Services.GoogleActionService;
import area.server.AREA_Back.service.Area.Services.Google.GoogleApiUtils;
import area.server.AREA_Back.service.Area.Services.Google.GoogleGmailService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GoogleActionService
 * Tests Google API interactions for Gmail, Calendar, Drive, and Sheets
 */
@ExtendWith(MockitoExtension.class)
class GoogleActionServiceTest {

    @Mock
    private GoogleApiUtils googleApiUtils;

    @Mock
    private GoogleGmailService gmailService;

    private SimpleMeterRegistry meterRegistry;
    private GoogleActionService googleActionService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        googleActionService = new GoogleActionService(
            googleApiUtils,
            gmailService,
            meterRegistry
        );

        // Manually initialize metrics
        try {
            var initMethod = GoogleActionService.class.getDeclaredMethod("init");
            initMethod.setAccessible(true);
            initMethod.invoke(googleActionService);
        } catch (Exception e) {
            fail("Failed to initialize metrics: " + e.getMessage());
        }
    }

    @Test
    void testServiceInitialization() {
        assertNotNull(googleActionService);
        assertNotNull(meterRegistry);
    }

    @Test
    void testMetricsAreRegistered() {
        assertNotNull(meterRegistry.find("google_actions_executed_total").counter());
        assertNotNull(meterRegistry.find("google_actions_failed_total").counter());
    }

    @Test
    void testExecuteGoogleActionWithoutToken() {
        // Given
        UUID userId = UUID.randomUUID();
        Map<String, Object> inputPayload = new HashMap<>();
        Map<String, Object> actionParams = new HashMap<>();

        when(googleApiUtils.getGoogleToken(userId)).thenReturn(null);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            googleActionService.executeGoogleAction("gmail_send_email", inputPayload, actionParams, userId);
        });

        assertTrue(exception.getMessage().contains("No Google token found"));
    }

    @Test
    void testExecuteGoogleActionWithUnknownAction() {
        // Given
        UUID userId = UUID.randomUUID();
        Map<String, Object> inputPayload = new HashMap<>();
        Map<String, Object> actionParams = new HashMap<>();

        when(googleApiUtils.getGoogleToken(userId)).thenReturn("valid-token");

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            googleActionService.executeGoogleAction("unknown_action", inputPayload, actionParams, userId);
        });

        assertTrue(exception.getMessage().contains("Unknown Google action"));
    }

    @Test
    void testExecuteGoogleActionIncrementsMetrics() {
        // Given
        UUID userId = UUID.randomUUID();
        Map<String, Object> inputPayload = new HashMap<>();
        Map<String, Object> actionParams = new HashMap<>();

        when(googleApiUtils.getGoogleToken(userId)).thenReturn(null);

        double initialExecuted = meterRegistry.counter("google_actions_executed_total").count();
        double initialFailed = meterRegistry.counter("google_actions_failed_total").count();

        // When
        try {
            googleActionService.executeGoogleAction("gmail_send_email", inputPayload, actionParams, userId);
        } catch (Exception e) {
            // Expected to fail
        }

        // Then
        double finalExecuted = meterRegistry.counter("google_actions_executed_total").count();
        double finalFailed = meterRegistry.counter("google_actions_failed_total").count();

        assertEquals(initialExecuted + 1, finalExecuted);
        assertEquals(initialFailed + 1, finalFailed);
    }

    @Test
    void testCheckGoogleEventsWithoutToken() {
        // Given
        UUID userId = UUID.randomUUID();
        Map<String, Object> actionParams = new HashMap<>();
        LocalDateTime lastCheck = LocalDateTime.now().minusMinutes(5);

        when(googleApiUtils.getGoogleToken(userId)).thenReturn(null);

        // When
        List<Map<String, Object>> events = googleActionService.checkGoogleEvents(
            "gmail_new_email", actionParams, userId, lastCheck
        );

        // Then
        assertNotNull(events);
        assertTrue(events.isEmpty());
    }

    @Test
    void testCheckGoogleEventsWithUnknownAction() {
        // Given
        UUID userId = UUID.randomUUID();
        Map<String, Object> actionParams = new HashMap<>();
        LocalDateTime lastCheck = LocalDateTime.now().minusMinutes(5);

        when(googleApiUtils.getGoogleToken(userId)).thenReturn("valid-token");

        // When
        List<Map<String, Object>> events = googleActionService.checkGoogleEvents(
            "unknown_event", actionParams, userId, lastCheck
        );

        // Then
        assertNotNull(events);
        assertTrue(events.isEmpty());
    }

    @Test
    void testCheckGoogleEventsHandlesException() {
        // Given
        UUID userId = UUID.randomUUID();
        Map<String, Object> actionParams = new HashMap<>();
        LocalDateTime lastCheck = LocalDateTime.now().minusMinutes(5);

        when(googleApiUtils.getGoogleToken(userId)).thenReturn("valid-token");
        when(gmailService.checkNewGmailMessages(anyString(), any(), any()))
            .thenThrow(new RuntimeException("API call failed"));

        // When
        List<Map<String, Object>> events = googleActionService.checkGoogleEvents(
            "gmail_new_email", actionParams, userId, lastCheck
        );

        // Then - should return empty list instead of throwing
        assertNotNull(events);
        assertTrue(events.isEmpty());
    }

    @Test
    void testGmailSendEmailActionWithMissingRequiredParams() {
        // Given
        UUID userId = UUID.randomUUID();
        Map<String, Object> inputPayload = new HashMap<>();
        Map<String, Object> actionParams = new HashMap<>();
        // Missing required params: to, subject, body

        when(googleApiUtils.getGoogleToken(userId)).thenReturn("valid-token");
        when(gmailService.sendGmail(anyString(), any(), any()))
            .thenThrow(new IllegalArgumentException("Missing required parameter"));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            googleActionService.executeGoogleAction("gmail_send_email", inputPayload, actionParams, userId);
        });
    }

    @Test
    void testCheckNewGmailMessagesEventKey() {
        // Given
        UUID userId = UUID.randomUUID();
        Map<String, Object> actionParams = new HashMap<>();
        LocalDateTime lastCheck = LocalDateTime.now().minusMinutes(5);

        when(googleApiUtils.getGoogleToken(userId)).thenReturn("valid-token");
        when(gmailService.checkNewGmailMessages(anyString(), any(), any()))
            .thenReturn(Collections.emptyList());

        // When - should fail or return empty list due to API call failure
        List<Map<String, Object>> events = googleActionService.checkGoogleEvents(
            "gmail_new_email", actionParams, userId, lastCheck
        );

        // Then - returns empty list on API failure
        assertNotNull(events);
    }
}
