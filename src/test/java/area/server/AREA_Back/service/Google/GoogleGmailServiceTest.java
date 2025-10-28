package area.server.AREA_Back.service.Google;

import area.server.AREA_Back.service.Area.Services.Google.GoogleApiUtils;
import area.server.AREA_Back.service.Area.Services.Google.GoogleGmailService;
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
 * Unit tests for GoogleGmailService
 * Tests Gmail operations: sending emails, managing labels, and checking messages
 */
@ExtendWith(MockitoExtension.class)
class GoogleGmailServiceTest {

    @Mock
    private GoogleApiUtils googleApiUtils;

    @Mock
    private RestTemplate restTemplate;

    private GoogleGmailService googleGmailService;

    @BeforeEach
    void setUp() {
        googleGmailService = new GoogleGmailService(googleApiUtils);
        
        // Inject mocked RestTemplate
        try {
            var field = GoogleGmailService.class.getDeclaredField("restTemplate");
            field.setAccessible(true);
            field.set(googleGmailService, restTemplate);
        } catch (Exception e) {
            fail("Failed to inject RestTemplate: " + e.getMessage());
        }
    }

    @Test
    void testSendGmailSuccess() {
        // Given
        String token = "valid-token";
        Map<String, Object> input = new HashMap<>();
        Map<String, Object> params = new HashMap<>();
        params.put("to", "recipient@example.com");
        params.put("subject", "Test Subject");
        params.put("body", "Test body content");

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("id", "msg-123");
        responseBody.put("threadId", "thread-456");

        when(googleApiUtils.getRequiredParam(params, "to", String.class)).thenReturn("recipient@example.com");
        when(googleApiUtils.getRequiredParam(params, "subject", String.class)).thenReturn("Test Subject");
        when(googleApiUtils.getRequiredParam(params, "body", String.class)).thenReturn("Test body content");
        when(googleApiUtils.getOptionalParam(params, "cc", String.class, null)).thenReturn(null);
        when(googleApiUtils.getOptionalParam(params, "bcc", String.class, null)).thenReturn(null);
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        // When
        Map<String, Object> result = googleGmailService.sendGmail(token, input, params);

        // Then
        assertNotNull(result);
        assertEquals("msg-123", result.get("message_id"));
        assertEquals("thread-456", result.get("thread_id"));
        assertNotNull(result.get("sent_at"));
    }

    @Test
    void testSendGmailWithCcAndBcc() {
        // Given
        String token = "valid-token";
        Map<String, Object> input = new HashMap<>();
        Map<String, Object> params = new HashMap<>();
        params.put("to", "recipient@example.com");
        params.put("subject", "Test Subject");
        params.put("body", "Test body content");
        params.put("cc", "cc@example.com");
        params.put("bcc", "bcc@example.com");

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("id", "msg-789");
        responseBody.put("threadId", "thread-101");

        when(googleApiUtils.getRequiredParam(params, "to", String.class)).thenReturn("recipient@example.com");
        when(googleApiUtils.getRequiredParam(params, "subject", String.class)).thenReturn("Test Subject");
        when(googleApiUtils.getRequiredParam(params, "body", String.class)).thenReturn("Test body content");
        when(googleApiUtils.getOptionalParam(params, "cc", String.class, null)).thenReturn("cc@example.com");
        when(googleApiUtils.getOptionalParam(params, "bcc", String.class, null)).thenReturn("bcc@example.com");
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        // When
        Map<String, Object> result = googleGmailService.sendGmail(token, input, params);

        // Then
        assertNotNull(result);
        assertEquals("msg-789", result.get("message_id"));
    }

    @Test
    void testSendGmailFailure() {
        // Given
        String token = "valid-token";
        Map<String, Object> input = new HashMap<>();
        Map<String, Object> params = new HashMap<>();
        params.put("to", "recipient@example.com");
        params.put("subject", "Test Subject");
        params.put("body", "Test body");

        when(googleApiUtils.getRequiredParam(params, "to", String.class)).thenReturn("recipient@example.com");
        when(googleApiUtils.getRequiredParam(params, "subject", String.class)).thenReturn("Test Subject");
        when(googleApiUtils.getRequiredParam(params, "body", String.class)).thenReturn("Test body");
        when(googleApiUtils.getOptionalParam(params, "cc", String.class, null)).thenReturn(null);
        when(googleApiUtils.getOptionalParam(params, "bcc", String.class, null)).thenReturn(null);
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(HttpStatus.BAD_REQUEST));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            googleGmailService.sendGmail(token, input, params);
        });
    }

    @Test
    void testAddGmailLabelSuccess() {
        // Given
        String token = "valid-token";
        Map<String, Object> input = new HashMap<>();
        Map<String, Object> params = new HashMap<>();
        params.put("message_id", "msg-123");
        params.put("label_name", "IMPORTANT");

        Map<String, Object> responseBody = new HashMap<>();

        when(googleApiUtils.getRequiredParam(params, "message_id", String.class)).thenReturn("msg-123");
        when(googleApiUtils.getRequiredParam(params, "label_name", String.class)).thenReturn("IMPORTANT");
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        // When
        Map<String, Object> result = googleGmailService.addGmailLabel(token, input, params);

        // Then
        assertNotNull(result);
        assertEquals("msg-123", result.get("message_id"));
        assertTrue(result.get("labels") instanceof List);
        assertNotNull(result.get("updated_at"));
    }

    @Test
    void testAddGmailLabelFailure() {
        // Given
        String token = "valid-token";
        Map<String, Object> input = new HashMap<>();
        Map<String, Object> params = new HashMap<>();
        params.put("message_id", "msg-123");
        params.put("label_name", "IMPORTANT");

        when(googleApiUtils.getRequiredParam(params, "message_id", String.class)).thenReturn("msg-123");
        when(googleApiUtils.getRequiredParam(params, "label_name", String.class)).thenReturn("IMPORTANT");
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(HttpStatus.FORBIDDEN));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            googleGmailService.addGmailLabel(token, input, params);
        });
    }

    @Test
    void testCheckNewGmailMessagesSuccess() {
        // Given
        String token = "valid-token";
        Map<String, Object> params = new HashMap<>();
        LocalDateTime lastCheck = LocalDateTime.now().minusHours(1);

        Map<String, Object> messagesList = new HashMap<>();
        messagesList.put("messages", Arrays.asList(
            Map.of("id", "msg-1"),
            Map.of("id", "msg-2")
        ));

        Map<String, Object> messageDetail = new HashMap<>();
        messageDetail.put("id", "msg-1");
        messageDetail.put("threadId", "thread-1");
        messageDetail.put("snippet", "Message snippet");
        messageDetail.put("labelIds", Arrays.asList("INBOX", "UNREAD"));
        
        Map<String, Object> payload = new HashMap<>();
        List<Map<String, Object>> headers = Arrays.asList(
            Map.of("name", "From", "value", "sender@example.com"),
            Map.of("name", "To", "value", "recipient@example.com"),
            Map.of("name", "Subject", "value", "Test Subject"),
            Map.of("name", "Date", "value", "Mon, 1 Jan 2025 10:00:00 +0000")
        );
        payload.put("headers", headers);
        messageDetail.put("payload", payload);

        when(googleApiUtils.getOptionalParam(params, "label", String.class, "INBOX")).thenReturn("INBOX");
        when(googleApiUtils.getOptionalParam(params, "from", String.class, null)).thenReturn(null);
        when(googleApiUtils.getOptionalParam(params, "subject_contains", String.class, null)).thenReturn(null);
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        
        when(restTemplate.exchange(
            contains("/messages?q="),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(messagesList, HttpStatus.OK));

        when(restTemplate.exchange(
            contains("/messages/msg-"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(messageDetail, HttpStatus.OK));

        // When
        List<Map<String, Object>> result = googleGmailService.checkNewGmailMessages(token, params, lastCheck);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test
    void testCheckNewGmailMessagesWithFilters() {
        // Given
        String token = "valid-token";
        Map<String, Object> params = new HashMap<>();
        params.put("label", "SENT");
        params.put("from", "specific@example.com");
        params.put("subject_contains", "important");
        LocalDateTime lastCheck = LocalDateTime.now().minusHours(1);

        Map<String, Object> messagesList = new HashMap<>();
        messagesList.put("messages", Collections.emptyList());

        when(googleApiUtils.getOptionalParam(params, "label", String.class, "INBOX")).thenReturn("SENT");
        when(googleApiUtils.getOptionalParam(params, "from", String.class, null)).thenReturn("specific@example.com");
        when(googleApiUtils.getOptionalParam(params, "subject_contains", String.class, null)).thenReturn("important");
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(messagesList, HttpStatus.OK));

        // When
        List<Map<String, Object>> result = googleGmailService.checkNewGmailMessages(token, params, lastCheck);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testCheckNewGmailMessagesFailure() {
        // Given
        String token = "valid-token";
        Map<String, Object> params = new HashMap<>();
        LocalDateTime lastCheck = LocalDateTime.now().minusHours(1);

        when(googleApiUtils.getOptionalParam(params, "label", String.class, "INBOX")).thenReturn("INBOX");
        when(googleApiUtils.getOptionalParam(params, "from", String.class, null)).thenReturn(null);
        when(googleApiUtils.getOptionalParam(params, "subject_contains", String.class, null)).thenReturn(null);
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(HttpStatus.UNAUTHORIZED));

        // When
        List<Map<String, Object>> result = googleGmailService.checkNewGmailMessages(token, params, lastCheck);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testCheckNewGmailMessagesNullMessages() {
        // Given
        String token = "valid-token";
        Map<String, Object> params = new HashMap<>();
        LocalDateTime lastCheck = LocalDateTime.now().minusHours(1);

        Map<String, Object> messagesList = new HashMap<>();
        messagesList.put("messages", null);

        when(googleApiUtils.getOptionalParam(params, "label", String.class, "INBOX")).thenReturn("INBOX");
        when(googleApiUtils.getOptionalParam(params, "from", String.class, null)).thenReturn(null);
        when(googleApiUtils.getOptionalParam(params, "subject_contains", String.class, null)).thenReturn(null);
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(messagesList, HttpStatus.OK));

        // When
        List<Map<String, Object>> result = googleGmailService.checkNewGmailMessages(token, params, lastCheck);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testCheckNewGmailMessagesEmptyMessages() {
        // Given
        String token = "valid-token";
        Map<String, Object> params = new HashMap<>();
        LocalDateTime lastCheck = LocalDateTime.now().minusHours(1);

        Map<String, Object> messagesList = new HashMap<>();
        messagesList.put("messages", Collections.emptyList());

        when(googleApiUtils.getOptionalParam(params, "label", String.class, "INBOX")).thenReturn("INBOX");
        when(googleApiUtils.getOptionalParam(params, "from", String.class, null)).thenReturn(null);
        when(googleApiUtils.getOptionalParam(params, "subject_contains", String.class, null)).thenReturn(null);
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(messagesList, HttpStatus.OK));

        // When
        List<Map<String, Object>> result = googleGmailService.checkNewGmailMessages(token, params, lastCheck);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFetchGmailMessageWithAllHeaders() {
        // Given
        String token = "valid-token";
        Map<String, Object> params = new HashMap<>();
        LocalDateTime lastCheck = LocalDateTime.now().minusHours(1);

        Map<String, Object> messagesList = new HashMap<>();
        messagesList.put("messages", Collections.singletonList(Map.of("id", "msg-complete")));

        Map<String, Object> messageDetail = new HashMap<>();
        messageDetail.put("id", "msg-complete");
        messageDetail.put("threadId", "thread-complete");
        messageDetail.put("snippet", "Complete message");
        
        Map<String, Object> payload = new HashMap<>();
        List<Map<String, Object>> headers = Arrays.asList(
            Map.of("name", "From", "value", "from@example.com"),
            Map.of("name", "To", "value", "to@example.com"),
            Map.of("name", "Subject", "value", "Complete Subject"),
            Map.of("name", "Date", "value", "2025-01-01"),
            Map.of("name", "Other", "value", "ignored")
        );
        payload.put("headers", headers);
        messageDetail.put("payload", payload);
        messageDetail.put("labelIds", null);

        when(googleApiUtils.getOptionalParam(params, "label", String.class, "INBOX")).thenReturn("INBOX");
        when(googleApiUtils.getOptionalParam(params, "from", String.class, null)).thenReturn(null);
        when(googleApiUtils.getOptionalParam(params, "subject_contains", String.class, null)).thenReturn(null);
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        
        when(restTemplate.exchange(
            contains("/messages?q="),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(messagesList, HttpStatus.OK));

        when(restTemplate.exchange(
            contains("/messages/msg-"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(messageDetail, HttpStatus.OK));

        // When
        List<Map<String, Object>> result = googleGmailService.checkNewGmailMessages(token, params, lastCheck);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        Map<String, Object> message = result.get(0);
        assertEquals("from@example.com", message.get("from"));
        assertTrue(message.get("to") instanceof List);
        assertEquals("Complete Subject", message.get("subject"));
        assertEquals("2025-01-01", message.get("received_at"));
        assertTrue(message.get("labels") instanceof List);
    }
}
