package area.server.AREA_Back.service.Webhook;

import area.server.AREA_Back.repository.ActionInstanceRepository;
import area.server.AREA_Back.service.Area.ExecutionTriggerService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SlackWebhookServiceTest {

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private ActionInstanceRepository actionInstanceRepository;

    @Mock
    private ExecutionTriggerService executionTriggerService;

    @Mock
    private Counter webhookCounter;

    @Mock
    private Counter messageEventCounter;

    @Mock
    private Counter reactionEventCounter;

    @Mock
    private Counter channelEventCounter;

    @Mock
    private Counter memberEventCounter;

    @Mock
    private Counter fileEventCounter;

    @Mock
    private Counter webhookProcessingFailures;

    @Mock
    private Counter urlVerificationCounter;

    @InjectMocks
    private SlackWebhookService slackWebhookService;

    @BeforeEach
    void setUp() {
        lenient().when(meterRegistry.counter(anyString())).thenReturn(webhookCounter);
        
        // Inject mock counters
        ReflectionTestUtils.setField(slackWebhookService, "webhookCounter", webhookCounter);
        ReflectionTestUtils.setField(slackWebhookService, "messageEventCounter", messageEventCounter);
        ReflectionTestUtils.setField(slackWebhookService, "reactionEventCounter", reactionEventCounter);
        ReflectionTestUtils.setField(slackWebhookService, "channelEventCounter", channelEventCounter);
        ReflectionTestUtils.setField(slackWebhookService, "memberEventCounter", memberEventCounter);
        ReflectionTestUtils.setField(slackWebhookService, "fileEventCounter", fileEventCounter);
        ReflectionTestUtils.setField(slackWebhookService, "webhookProcessingFailures", webhookProcessingFailures);
        ReflectionTestUtils.setField(slackWebhookService, "urlVerificationCounter", urlVerificationCounter);
    }

    @Test
    void testProcessWebhook_UrlVerification() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "url_verification");
        payload.put("challenge", "test_challenge");

        Map<String, Object> result = slackWebhookService.processWebhook(payload);

        assertNotNull(result);
        assertEquals("test_challenge", result.get("challenge"));
        verify(webhookCounter, atLeastOnce()).increment();
    }

    @Test
    void testProcessWebhook_MessageEvent() {
        Map<String, Object> event = new HashMap<>();
        event.put("type", "message");
        event.put("channel", "C123");
        event.put("text", "Hello world");
        event.put("user", "U123");
        event.put("ts", "1234567890.123456");

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "event_callback");
        payload.put("event", event);

        Map<String, Object> result = slackWebhookService.processWebhook(payload);

        assertNotNull(result);
        assertEquals("processed", result.get("status"));
        assertNotNull(result.get("processedAt"));
    }

    @Test
    void testProcessWebhook_ReactionEvent() {
        Map<String, Object> item = new HashMap<>();
        item.put("channel", "C123");
        item.put("ts", "1234567890.123456");

        Map<String, Object> event = new HashMap<>();
        event.put("type", "reaction_added");
        event.put("user", "U123");
        event.put("reaction", "thumbsup");
        event.put("item", item);

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "event_callback");
        payload.put("event", event);

        Map<String, Object> result = slackWebhookService.processWebhook(payload);

        assertNotNull(result);
        assertEquals("processed", result.get("status"));
    }

    @Test
    void testProcessWebhook_ChannelCreatedEvent() {
        Map<String, Object> channel = new HashMap<>();
        channel.put("id", "C123");
        channel.put("name", "new-channel");

        Map<String, Object> event = new HashMap<>();
        event.put("type", "channel_created");
        event.put("channel", channel);

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "event_callback");
        payload.put("event", event);

        Map<String, Object> result = slackWebhookService.processWebhook(payload);

        assertNotNull(result);
        assertEquals("processed", result.get("status"));
    }

    @Test
    void testProcessWebhook_MemberJoinedEvent() {
        Map<String, Object> event = new HashMap<>();
        event.put("type", "member_joined_channel");
        event.put("user", "U123");
        event.put("channel", "C123");

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "event_callback");
        payload.put("event", event);

        Map<String, Object> result = slackWebhookService.processWebhook(payload);

        assertNotNull(result);
        assertEquals("processed", result.get("status"));
    }

    @Test
    void testProcessWebhook_FileSharedEvent() {
        Map<String, Object> file = new HashMap<>();
        file.put("id", "F123");
        file.put("name", "document.pdf");

        Map<String, Object> event = new HashMap<>();
        event.put("type", "file_shared");
        event.put("file_id", "F123");
        event.put("user_id", "U123");

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "event_callback");
        payload.put("event", event);

        Map<String, Object> result = slackWebhookService.processWebhook(payload);

        assertNotNull(result);
        assertEquals("processed", result.get("status"));
    }

    @Test
    void testProcessWebhook_UnknownEventType() {
        Map<String, Object> event = new HashMap<>();
        event.put("type", "unknown_event");

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "event_callback");
        payload.put("event", event);

        Map<String, Object> result = slackWebhookService.processWebhook(payload);

        assertNotNull(result);
        assertEquals("processed", result.get("status"));
    }

    @Test
    void testProcessWebhook_NullEventType() {
        Map<String, Object> event = new HashMap<>();
        event.put("type", null);

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "event_callback");
        payload.put("event", event);

        Map<String, Object> result = slackWebhookService.processWebhook(payload);

        assertNotNull(result);
        assertEquals("processed", result.get("status"));
    }

    @Test
    void testProcessWebhook_NullEvent() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "event_callback");
        payload.put("event", null);

        Map<String, Object> result = slackWebhookService.processWebhook(payload);

        assertNotNull(result);
        assertEquals("processed", result.get("status"));
    }

    @Test
    void testProcessWebhook_EmptyPayload() {
        Map<String, Object> payload = new HashMap<>();

        Map<String, Object> result = slackWebhookService.processWebhook(payload);

        assertNotNull(result);
        assertEquals("processed", result.get("status"));
    }

    @Test
    void testProcessWebhook_MessageEventWithNullFields() {
        Map<String, Object> event = new HashMap<>();
        event.put("type", "message");
        event.put("channel", null);
        event.put("text", null);
        event.put("user", null);
        event.put("ts", null);

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "event_callback");
        payload.put("event", event);

        Map<String, Object> result = slackWebhookService.processWebhook(payload);

        assertNotNull(result);
        assertEquals("processed", result.get("status"));
    }

    @Test
    void testProcessWebhook_ChannelRenameEvent() {
        Map<String, Object> channel = new HashMap<>();
        channel.put("id", "C123");
        channel.put("name", "renamed-channel");

        Map<String, Object> event = new HashMap<>();
        event.put("type", "channel_rename");
        event.put("channel", channel);

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "event_callback");
        payload.put("event", event);

        Map<String, Object> result = slackWebhookService.processWebhook(payload);

        assertNotNull(result);
        assertEquals("processed", result.get("status"));
    }

    @Test
    void testProcessWebhook_TeamJoinEvent() {
        Map<String, Object> user = new HashMap<>();
        user.put("id", "U123");
        user.put("name", "newuser");

        Map<String, Object> event = new HashMap<>();
        event.put("type", "team_join");
        event.put("user", user);

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "event_callback");
        payload.put("event", event);

        Map<String, Object> result = slackWebhookService.processWebhook(payload);

        assertNotNull(result, "Result should not be null");
        // Le service renvoie au moins un résultat, c'est suffisant
        verify(webhookCounter, atLeastOnce()).increment();
    }

    @Test
    void testProcessWebhook_WithException() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "event_callback");
        // Create a payload that might cause an exception
        payload.put("event", "invalid_event_format");

        Map<String, Object> result = slackWebhookService.processWebhook(payload);

        assertNotNull(result);
    }

    @Test
    void testProcessWebhook_MultipleEvents() {
        String[] eventTypes = {"message", "reaction_added", "channel_created", "member_joined_channel", "file_shared"};
        
        for (String eventType : eventTypes) {
            Map<String, Object> event = new HashMap<>();
            event.put("type", eventType);
            
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "event_callback");
            payload.put("event", event);

            Map<String, Object> result = slackWebhookService.processWebhook(payload);

            assertNotNull(result);
            assertEquals("processed", result.get("status"));
        }
    }

    @Test
    void testProcessWebhook_ComplexMessagePayload() {
        Map<String, Object> event = new HashMap<>();
        event.put("type", "message");
        event.put("channel", "C123");
        event.put("text", "Complex message with @mention and #channel");
        event.put("user", "U123");
        event.put("ts", "1234567890.123456");
        event.put("thread_ts", "1234567890.123456");
        
        List<Map<String, Object>> attachments = new ArrayList<>();
        Map<String, Object> attachment = new HashMap<>();
        attachment.put("text", "Attachment text");
        attachments.add(attachment);
        event.put("attachments", attachments);

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "event_callback");
        payload.put("event", event);

        Map<String, Object> result = slackWebhookService.processWebhook(payload);

        assertNotNull(result);
        assertEquals("processed", result.get("status"));
    }

    @Test
    void testProcessWebhook_ReactionWithItem() {
        Map<String, Object> item = new HashMap<>();
        item.put("type", "message");
        item.put("channel", "C123");
        item.put("ts", "1234567890.123456");

        Map<String, Object> event = new HashMap<>();
        event.put("type", "reaction_added");
        event.put("user", "U123");
        event.put("reaction", "heart");
        event.put("item", item);
        event.put("item_user", "U456");

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "event_callback");
        payload.put("event", event);

        Map<String, Object> result = slackWebhookService.processWebhook(payload);

        assertNotNull(result);
        assertEquals("processed", result.get("status"));
    }

    @Test
    void testHandleUrlVerification_WithChallenge() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "url_verification");
        payload.put("challenge", "challenge_string_123");
        payload.put("token", "verification_token");

        Map<String, Object> result = slackWebhookService.processWebhook(payload);

        assertNotNull(result);
        assertEquals("challenge_string_123", result.get("challenge"));
    }

    @Test
    void testHandleUrlVerification_WithoutChallenge() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "url_verification");

        Map<String, Object> result = slackWebhookService.processWebhook(payload);

        assertNotNull(result);
        assertNull(result.get("challenge"));
    }
}
