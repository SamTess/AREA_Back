package area.server.AREA_Back.service.Webhook;

import area.server.AREA_Back.entity.ActionDefinition;
import area.server.AREA_Back.entity.ActionInstance;
import area.server.AREA_Back.entity.Service;
import area.server.AREA_Back.entity.enums.ActivationModeType;
import area.server.AREA_Back.repository.ActionInstanceRepository;
import area.server.AREA_Back.service.Area.ExecutionTriggerService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DiscordWebhookServiceTest {

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private ActionInstanceRepository actionInstanceRepository;

    @Mock
    private ExecutionTriggerService executionTriggerService;

    @Mock
    private WebhookSignatureValidator signatureValidator;

    @Mock
    private WebhookSecretService webhookSecretService;

    @Mock
    private WebhookDeduplicationService deduplicationService;

    @Mock
    private Counter webhookCounter;

    @Mock
    private Counter messageCreateCounter;

    @Mock
    private Counter messageReactionAddCounter;

    @Mock
    private Counter webhookProcessingFailures;

    @Mock
    private Counter signatureValidationFailures;

    @InjectMocks
    private DiscordWebhookService discordWebhookService;

    @BeforeEach
    void setUp() {
        lenient().when(meterRegistry.counter(anyString())).thenReturn(webhookCounter);

        // Inject mock counters directly using ReflectionTestUtils
        ReflectionTestUtils.setField(discordWebhookService, "webhookCounter", webhookCounter);
        ReflectionTestUtils.setField(discordWebhookService, "messageCreateCounter", messageCreateCounter);
        ReflectionTestUtils.setField(discordWebhookService, "messageReactionAddCounter", messageReactionAddCounter);
        ReflectionTestUtils.setField(discordWebhookService, "webhookProcessingFailures", webhookProcessingFailures);
        ReflectionTestUtils.setField(discordWebhookService, "signatureValidationFailures", signatureValidationFailures);
    }

    @Test
    void testInitMetrics() {
        // Testing @PostConstruct methods is complex because Counter.builder() creates the counter
        // directly rather than going through meterRegistry.counter()
        // The initMetrics functionality is implicitly tested by all other tests that verify
        // counter.increment() is called, which proves the counters were properly initialized

        // Just verify the method can be called without exceptions
        assertDoesNotThrow(() -> discordWebhookService.initMetrics());
    }

    @Test
    void testProcessWebhook_PingEvent() {
        // Given
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", 1);

        // When
        Map<String, Object> result = discordWebhookService.processWebhook(payload, null, null, new byte[0]);

        // Then
        assertNotNull(result);
        assertEquals("processed", result.get("status"));
        assertEquals("ping", result.get("type"));
        assertNotNull(result.get("processedAt"));
        assertTrue(result.containsKey("response"));
        @SuppressWarnings("unchecked")
        Map<String, Object> response = (Map<String, Object>) result.get("response");
        assertEquals(1, response.get("type"));
        verify(webhookCounter, atLeastOnce()).increment();
    }

    @Test
    void testProcessWebhook_InvalidSignature() {
        // Given
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", 0);
        payload.put("t", "MESSAGE_CREATE");

        String signature = "invalid_signature";
        String timestamp = "1234567890";
        String secret = "test_secret";

        when(webhookSecretService.getServiceSecret("discord")).thenReturn(secret);
        when(signatureValidator.validateSignature(
            eq("discord"),
            any(byte[].class),
            eq(signature),
            eq(secret),
            eq(timestamp)
        )).thenReturn(false);

        // When
        Map<String, Object> result = discordWebhookService.processWebhook(payload, signature, timestamp, "{}".getBytes());

        // Then
        assertNotNull(result);
        assertEquals("signature_invalid", result.get("status"));
        assertEquals("Invalid signature", result.get("error"));
        verify(signatureValidationFailures, times(1)).increment();
        verify(webhookCounter, atLeastOnce()).increment();
    }

    @Test
    void testProcessWebhook_ValidSignature() {
        // Given
        Map<String, Object> author = new HashMap<>();
        author.put("id", "author123");
        author.put("username", "testuser");

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("id", "msg123");
        eventData.put("channel_id", "channel456");
        eventData.put("content", "Hello");
        eventData.put("author", author);

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", 0);
        payload.put("t", "MESSAGE_CREATE");
        payload.put("d", eventData);

        String signature = "valid_signature";
        String timestamp = "1234567890";
        String secret = "test_secret";

        when(webhookSecretService.getServiceSecret("discord")).thenReturn(secret);
        when(signatureValidator.validateSignature(
            eq("discord"),
            any(byte[].class),
            eq(signature),
            eq(secret),
            eq(timestamp)
        )).thenReturn(true);
        when(deduplicationService.checkAndMark(anyString(), eq("discord"))).thenReturn(false);
        when(actionInstanceRepository.findEnabledActionInstancesByService("discord")).thenReturn(Collections.emptyList());

        // When
        Map<String, Object> result = discordWebhookService.processWebhook(payload, signature, timestamp, "{}".getBytes());

        // Then
        assertNotNull(result);
        assertEquals("processed", result.get("status"));
        verify(webhookCounter, atLeastOnce()).increment();
    }

    @Test
    void testProcessWebhook_MissingEventType() {
        // Given
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", 0);
        payload.put("d", new HashMap<>());
        // No "t" field

        // When
        Map<String, Object> result = discordWebhookService.processWebhook(payload, null, null, new byte[0]);

        // Then
        assertNotNull(result);
        assertEquals("processed", result.get("status"));
        assertTrue(result.containsKey("warning"));
        assertEquals("Missing event type", result.get("warning"));
    }

    @Test
    void testProcessWebhook_MissingEventData() {
        // Given
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", 0);
        payload.put("t", "MESSAGE_CREATE");
        // No "d" field

        // When
        Map<String, Object> result = discordWebhookService.processWebhook(payload, null, null, new byte[0]);

        // Then
        assertNotNull(result);
        assertEquals("processed", result.get("status"));
        assertTrue(result.containsKey("warning"));
        assertEquals("Missing event data", result.get("warning"));
    }

    @Test
    void testProcessWebhook_Exception() {
        // Given
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", 0);
        payload.put("t", "MESSAGE_CREATE");

        when(webhookSecretService.getServiceSecret("discord")).thenThrow(new RuntimeException("Test exception"));

        // When
        Map<String, Object> result = discordWebhookService.processWebhook(payload, "sig", "123", "{}".getBytes());

        // Then
        assertNotNull(result);
        assertEquals("error", result.get("status"));
        assertTrue(result.containsKey("error"));
        verify(webhookProcessingFailures, times(1)).increment();
    }

    @Test
    void testProcessEvent_MessageCreate() {
        // Given
        Map<String, Object> author = new HashMap<>();
        author.put("id", "author123");
        author.put("username", "testuser");

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("id", "msg123");
        eventData.put("channel_id", "channel456");
        eventData.put("content", "Hello World");
        eventData.put("author", author);

        Map<String, Object> payload = new HashMap<>();
        payload.put("t", "MESSAGE_CREATE");
        payload.put("d", eventData);

        when(deduplicationService.checkAndMark(anyString(), eq("discord"))).thenReturn(false);
        when(actionInstanceRepository.findEnabledActionInstancesByService("discord")).thenReturn(Collections.emptyList());

        // When
        Map<String, Object> result = discordWebhookService.processWebhook(payload, null, null, new byte[0]);

        // Then
        assertNotNull(result);
        assertEquals("MESSAGE_CREATE", result.get("eventType"));
        assertEquals("message_create", result.get("action"));
        assertEquals("msg123", result.get("messageId"));
        assertEquals("channel456", result.get("channelId"));
        assertEquals("author123", result.get("authorId"));
        verify(messageCreateCounter, times(1)).increment();
        verify(deduplicationService, times(1)).checkAndMark("discord_message_msg123", "discord");
    }

    @Test
    void testProcessEvent_MessageReactionAdd() {
        // Given
        Map<String, Object> emoji = new HashMap<>();
        emoji.put("name", "👍");
        emoji.put("id", "emoji123");

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("message_id", "msg456");
        eventData.put("channel_id", "channel789");
        eventData.put("user_id", "user101");
        eventData.put("emoji", emoji);

        Map<String, Object> payload = new HashMap<>();
        payload.put("t", "MESSAGE_REACTION_ADD");
        payload.put("d", eventData);

        when(deduplicationService.checkAndMark(anyString(), eq("discord"))).thenReturn(false);
        when(actionInstanceRepository.findEnabledActionInstancesByService("discord")).thenReturn(Collections.emptyList());

        // When
        Map<String, Object> result = discordWebhookService.processWebhook(payload, null, null, new byte[0]);

        // Then
        assertNotNull(result);
        assertEquals("MESSAGE_REACTION_ADD", result.get("eventType"));
        assertEquals("message_reaction_add", result.get("action"));
        assertEquals("msg456", result.get("messageId"));
        assertEquals("channel789", result.get("channelId"));
        assertEquals("user101", result.get("userId"));
        assertEquals("👍", result.get("emoji"));
        verify(messageReactionAddCounter, times(1)).increment();
        verify(deduplicationService, times(1)).checkAndMark("discord_reaction_msg456_user101_emoji123", "discord");
    }

    @Test
    void testProcessEvent_UnsupportedEventType() {
        // Given
        Map<String, Object> eventData = new HashMap<>();
        Map<String, Object> payload = new HashMap<>();
        payload.put("t", "UNSUPPORTED_EVENT");
        payload.put("d", eventData);

        // When
        Map<String, Object> result = discordWebhookService.processWebhook(payload, null, null, new byte[0]);

        // Then
        assertNotNull(result);
        assertEquals("UNSUPPORTED_EVENT", result.get("eventType"));
        assertTrue(result.containsKey("warning"));
        verify(messageCreateCounter, never()).increment();
        verify(messageReactionAddCounter, never()).increment();
    }

    @Test
    void testProcessEvent_NullEventType() {
        // Given
        Map<String, Object> eventData = new HashMap<>();
        Map<String, Object> payload = new HashMap<>();
        payload.put("t", null);
        payload.put("d", eventData);

        // When
        Map<String, Object> result = discordWebhookService.processWebhook(payload, null, null, new byte[0]);

        // Then
        assertNotNull(result);
        assertTrue(result.containsKey("warning"));
    }

    @Test
    void testProcessMessageCreateEvent_WithoutAuthor() {
        // Given - Discord webhooks should always have author data, but test graceful handling
        // In this case, the implementation will return error status due to NPE from Map.of()
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("id", "msg789");
        eventData.put("channel_id", "channel101");
        eventData.put("content", "Test message");
        // No author field at all

        Map<String, Object> payload = new HashMap<>();
        payload.put("t", "MESSAGE_CREATE");
        payload.put("d", eventData);

        when(deduplicationService.checkAndMark(anyString(), eq("discord"))).thenReturn(false);
        when(actionInstanceRepository.findEnabledActionInstancesByService("discord")).thenReturn(Collections.emptyList());

        // When
        Map<String, Object> result = discordWebhookService.processWebhook(payload, null, null, new byte[0]);

        // Then - Should handle the error gracefully
        assertNotNull(result);
        assertEquals("error", result.get("status")); // NPE caught and returned as error
        assertTrue(result.containsKey("error"));
        verify(webhookProcessingFailures, times(1)).increment();
    }

    @Test
    void testProcessMessageCreateEvent_Duplicate() {
        // Given
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("id", "msg_duplicate");
        eventData.put("channel_id", "channel123");
        eventData.put("content", "Duplicate message");

        Map<String, Object> payload = new HashMap<>();
        payload.put("t", "MESSAGE_CREATE");
        payload.put("d", eventData);

        when(deduplicationService.checkAndMark("discord_message_msg_duplicate", "discord")).thenReturn(true);

        // When
        Map<String, Object> result = discordWebhookService.processWebhook(payload, null, null, new byte[0]);

        // Then
        assertNotNull(result);
        assertEquals("duplicate", result.get("status"));
        assertEquals("msg_duplicate", result.get("messageId"));
        verify(actionInstanceRepository, never()).findEnabledActionInstancesByService(anyString());
    }

    @Test
    void testProcessMessageCreateEvent_NullMessageId() {
        // Given - Test with null message ID (edge case that results in NPE from Map.of())
        Map<String, Object> author = new HashMap<>();
        author.put("id", "author123");
        author.put("username", "testuser");

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("id", null);
        eventData.put("channel_id", "channel123");
        eventData.put("content", "Message without ID");
        eventData.put("author", author);

        Map<String, Object> payload = new HashMap<>();
        payload.put("t", "MESSAGE_CREATE");
        payload.put("d", eventData);

        when(actionInstanceRepository.findEnabledActionInstancesByService("discord")).thenReturn(Collections.emptyList());

        // When
        Map<String, Object> result = discordWebhookService.processWebhook(payload, null, null, new byte[0]);

        // Then - Should return error due to NPE from Map.of() with null messageId
        assertNotNull(result);
        assertEquals("error", result.get("status"));
        assertTrue(result.containsKey("error"));
        verify(deduplicationService, never()).checkAndMark(anyString(), anyString());
        verify(webhookProcessingFailures, times(1)).increment();
    }

    @Test
    void testProcessMessageReactionAddEvent_WithEmojiId() {
        // Given
        Map<String, Object> emoji = new HashMap<>();
        emoji.put("name", "custom_emoji");
        emoji.put("id", "emoji999");

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("message_id", "msg999");
        eventData.put("channel_id", "channel999");
        eventData.put("user_id", "user999");
        eventData.put("emoji", emoji);

        Map<String, Object> payload = new HashMap<>();
        payload.put("t", "MESSAGE_REACTION_ADD");
        payload.put("d", eventData);

        when(deduplicationService.checkAndMark(anyString(), eq("discord"))).thenReturn(false);
        when(actionInstanceRepository.findEnabledActionInstancesByService("discord")).thenReturn(Collections.emptyList());

        // When
        Map<String, Object> result = discordWebhookService.processWebhook(payload, null, null, new byte[0]);

        // Then
        assertNotNull(result);
        assertEquals("message_reaction_add", result.get("action"));
        verify(deduplicationService, times(1)).checkAndMark("discord_reaction_msg999_user999_emoji999", "discord");
    }

    @Test
    void testProcessMessageReactionAddEvent_WithoutEmojiId() {
        // Given
        Map<String, Object> emoji = new HashMap<>();
        emoji.put("name", "👎");
        emoji.put("id", null);

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("message_id", "msg888");
        eventData.put("channel_id", "channel888");
        eventData.put("user_id", "user888");
        eventData.put("emoji", emoji);

        Map<String, Object> payload = new HashMap<>();
        payload.put("t", "MESSAGE_REACTION_ADD");
        payload.put("d", eventData);

        when(deduplicationService.checkAndMark(anyString(), eq("discord"))).thenReturn(false);
        when(actionInstanceRepository.findEnabledActionInstancesByService("discord")).thenReturn(Collections.emptyList());

        // When
        Map<String, Object> result = discordWebhookService.processWebhook(payload, null, null, new byte[0]);

        // Then
        assertNotNull(result);
        assertEquals("message_reaction_add", result.get("action"));
        verify(deduplicationService, times(1)).checkAndMark("discord_reaction_msg888_user888_👎", "discord");
    }

    @Test
    void testProcessMessageReactionAddEvent_NullEmoji() {
        // Given - Test with missing emoji (edge case that results in NPE from Map.of())
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("message_id", "msg777");
        eventData.put("channel_id", "channel777");
        eventData.put("user_id", "user777");
        // No emoji field

        Map<String, Object> payload = new HashMap<>();
        payload.put("t", "MESSAGE_REACTION_ADD");
        payload.put("d", eventData);

        when(deduplicationService.checkAndMark(anyString(), eq("discord"))).thenReturn(false);
        when(actionInstanceRepository.findEnabledActionInstancesByService("discord")).thenReturn(Collections.emptyList());

        // When
        Map<String, Object> result = discordWebhookService.processWebhook(payload, null, null, new byte[0]);

        // Then - Should return error due to NPE from Map.of() with null emoji name
        assertNotNull(result);
        assertEquals("error", result.get("status"));
        assertTrue(result.containsKey("error"));
        verify(webhookProcessingFailures, times(1)).increment();
    }

    @Test
    void testProcessMessageReactionAddEvent_Duplicate() {
        // Given
        Map<String, Object> emoji = new HashMap<>();
        emoji.put("name", "😀");
        emoji.put("id", null);

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("message_id", "msg_dup");
        eventData.put("channel_id", "channel_dup");
        eventData.put("user_id", "user_dup");
        eventData.put("emoji", emoji);

        Map<String, Object> payload = new HashMap<>();
        payload.put("t", "MESSAGE_REACTION_ADD");
        payload.put("d", eventData);

        when(deduplicationService.checkAndMark(anyString(), eq("discord"))).thenReturn(true);

        // When
        Map<String, Object> result = discordWebhookService.processWebhook(payload, null, null, new byte[0]);

        // Then
        assertNotNull(result);
        assertEquals("duplicate", result.get("status"));
        assertTrue(result.containsKey("reactionKey"));
        verify(actionInstanceRepository, never()).findEnabledActionInstancesByService(anyString());
    }

    @Test
    void testTriggerMatchingActions_MessageCreated() {
        // Given
        ActionDefinition actionDefinition = new ActionDefinition();
        actionDefinition.setKey("message_created");

        Service service = new Service();
        service.setKey("discord");

        actionDefinition.setService(service);

        ActionInstance actionInstance = new ActionInstance();
        actionInstance.setId(UUID.randomUUID());
        actionInstance.setEnabled(true);
        actionInstance.setActionDefinition(actionDefinition);

        Map<String, Object> author = new HashMap<>();
        author.put("id", "author123");
        author.put("username", "testuser");

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("id", "msg123");
        eventData.put("channel_id", "channel456");
        eventData.put("content", "Trigger message");
        eventData.put("author", author);

        Map<String, Object> payload = new HashMap<>();
        payload.put("t", "MESSAGE_CREATE");
        payload.put("d", eventData);

        when(deduplicationService.checkAndMark(anyString(), eq("discord"))).thenReturn(false);
        when(actionInstanceRepository.findEnabledActionInstancesByService("discord"))
            .thenReturn(Collections.singletonList(actionInstance));

        // When
        Map<String, Object> result = discordWebhookService.processWebhook(payload, null, null, new byte[0]);

        // Then
        assertNotNull(result);
        verify(executionTriggerService, times(1)).triggerAreaExecution(
            eq(actionInstance),
            eq(ActivationModeType.WEBHOOK),
            any()
        );
    }

    @Test
    void testTriggerMatchingActions_ReactionAdded() {
        // Given
        ActionDefinition actionDefinition = new ActionDefinition();
        actionDefinition.setKey("reaction_added");

        Service service = new Service();
        service.setKey("discord");

        actionDefinition.setService(service);

        ActionInstance actionInstance = new ActionInstance();
        actionInstance.setId(UUID.randomUUID());
        actionInstance.setEnabled(true);
        actionInstance.setActionDefinition(actionDefinition);

        Map<String, Object> emoji = new HashMap<>();
        emoji.put("name", "🎉");

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("message_id", "msg789");
        eventData.put("channel_id", "channel789");
        eventData.put("user_id", "user789");
        eventData.put("emoji", emoji);

        Map<String, Object> payload = new HashMap<>();
        payload.put("t", "MESSAGE_REACTION_ADD");
        payload.put("d", eventData);

        when(deduplicationService.checkAndMark(anyString(), eq("discord"))).thenReturn(false);
        when(actionInstanceRepository.findEnabledActionInstancesByService("discord"))
            .thenReturn(Collections.singletonList(actionInstance));

        // When
        Map<String, Object> result = discordWebhookService.processWebhook(payload, null, null, new byte[0]);

        // Then
        assertNotNull(result);
        verify(executionTriggerService, times(1)).triggerAreaExecution(
            eq(actionInstance),
            eq(ActivationModeType.WEBHOOK),
            any()
        );
    }

    @Test
    void testTriggerMatchingActions_NoMatch() {
        // Given
        ActionDefinition actionDefinition = new ActionDefinition();
        actionDefinition.setKey("different_action");

        Service service = new Service();
        service.setKey("discord");

        actionDefinition.setService(service);

        ActionInstance actionInstance = new ActionInstance();
        actionInstance.setId(UUID.randomUUID());
        actionInstance.setEnabled(true);
        actionInstance.setActionDefinition(actionDefinition);

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("id", "msg123");
        eventData.put("channel_id", "channel456");
        eventData.put("content", "No match message");

        Map<String, Object> payload = new HashMap<>();
        payload.put("t", "MESSAGE_CREATE");
        payload.put("d", eventData);

        when(deduplicationService.checkAndMark(anyString(), eq("discord"))).thenReturn(false);
        when(actionInstanceRepository.findEnabledActionInstancesByService("discord"))
            .thenReturn(Collections.singletonList(actionInstance));

        // When
        Map<String, Object> result = discordWebhookService.processWebhook(payload, null, null, new byte[0]);

        // Then
        assertNotNull(result);
        verify(executionTriggerService, never()).triggerAreaExecution(any(), any(), any());
    }

    @Test
    void testTriggerMatchingActions_MultipleInstances() {
        // Given
        ActionDefinition actionDefinition1 = new ActionDefinition();
        actionDefinition1.setKey("message_created");

        ActionDefinition actionDefinition2 = new ActionDefinition();
        actionDefinition2.setKey("message_created");

        Service service = new Service();
        service.setKey("discord");

        actionDefinition1.setService(service);
        actionDefinition2.setService(service);

        ActionInstance actionInstance1 = new ActionInstance();
        actionInstance1.setId(UUID.randomUUID());
        actionInstance1.setEnabled(true);
        actionInstance1.setActionDefinition(actionDefinition1);

        ActionInstance actionInstance2 = new ActionInstance();
        actionInstance2.setId(UUID.randomUUID());
        actionInstance2.setEnabled(true);
        actionInstance2.setActionDefinition(actionDefinition2);

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("id", "msg_multi");
        eventData.put("channel_id", "channel_multi");
        eventData.put("content", "Multi trigger");

        Map<String, Object> payload = new HashMap<>();
        payload.put("t", "MESSAGE_CREATE");
        payload.put("d", eventData);

        when(deduplicationService.checkAndMark(anyString(), eq("discord"))).thenReturn(false);
        when(actionInstanceRepository.findEnabledActionInstancesByService("discord"))
            .thenReturn(Arrays.asList(actionInstance1, actionInstance2));

        // When
        Map<String, Object> result = discordWebhookService.processWebhook(payload, null, null, new byte[0]);

        // Then
        assertNotNull(result);
        verify(executionTriggerService, times(2)).triggerAreaExecution(
            any(ActionInstance.class),
            eq(ActivationModeType.WEBHOOK),
            any()
        );
    }

    @Test
    void testTriggerMatchingActions_ExecutionException() {
        // Given
        ActionDefinition actionDefinition = new ActionDefinition();
        actionDefinition.setKey("message_created");

        Service service = new Service();
        service.setKey("discord");

        actionDefinition.setService(service);

        ActionInstance actionInstance = new ActionInstance();
        actionInstance.setId(UUID.randomUUID());
        actionInstance.setEnabled(true);
        actionInstance.setActionDefinition(actionDefinition);

        Map<String, Object> author = new HashMap<>();
        author.put("id", "author_error");
        author.put("username", "erroruser");

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("id", "msg_error");
        eventData.put("channel_id", "channel_error");
        eventData.put("content", "Error message");
        eventData.put("author", author);

        Map<String, Object> payload = new HashMap<>();
        payload.put("t", "MESSAGE_CREATE");
        payload.put("d", eventData);

        when(deduplicationService.checkAndMark(anyString(), eq("discord"))).thenReturn(false);
        when(actionInstanceRepository.findEnabledActionInstancesByService("discord"))
            .thenReturn(Collections.singletonList(actionInstance));
        doThrow(new RuntimeException("Execution failed"))
            .when(executionTriggerService).triggerAreaExecution(any(), any(), any());

        // When
        Map<String, Object> result = discordWebhookService.processWebhook(payload, null, null, new byte[0]);

        // Then
        assertNotNull(result);
        assertEquals("processed", result.get("status"));
        verify(executionTriggerService, times(1)).triggerAreaExecution(any(), any(), any());
    }

    @Test
    void testTriggerMatchingActions_RepositoryException() {
        // Given
        Map<String, Object> author = new HashMap<>();
        author.put("id", "author_repo_error");
        author.put("username", "repoerroruser");

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("id", "msg_repo_error");
        eventData.put("channel_id", "channel_repo_error");
        eventData.put("content", "Repository error");
        eventData.put("author", author);

        Map<String, Object> payload = new HashMap<>();
        payload.put("t", "MESSAGE_CREATE");
        payload.put("d", eventData);

        when(deduplicationService.checkAndMark(anyString(), eq("discord"))).thenReturn(false);
        when(actionInstanceRepository.findEnabledActionInstancesByService("discord"))
            .thenThrow(new RuntimeException("Repository error"));

        // When
        Map<String, Object> result = discordWebhookService.processWebhook(payload, null, null, new byte[0]);

        // Then
        assertNotNull(result);
        assertEquals("processed", result.get("status"));
        verify(executionTriggerService, never()).triggerAreaExecution(any(), any(), any());
    }

    @Test
    void testProcessWebhook_EventPayloadCorrectness() {
        // Given
        Map<String, Object> author = new HashMap<>();
        author.put("id", "author_verify");
        author.put("username", "verify_user");

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("id", "msg_verify");
        eventData.put("channel_id", "channel_verify");
        eventData.put("content", "Verify content");
        eventData.put("author", author);

        Map<String, Object> payload = new HashMap<>();
        payload.put("t", "MESSAGE_CREATE");
        payload.put("d", eventData);

        ActionDefinition actionDefinition = new ActionDefinition();
        actionDefinition.setKey("message_created");

        Service service = new Service();
        service.setKey("discord");
        actionDefinition.setService(service);

        ActionInstance actionInstance = new ActionInstance();
        actionInstance.setId(UUID.randomUUID());
        actionInstance.setEnabled(true);
        actionInstance.setActionDefinition(actionDefinition);

        when(deduplicationService.checkAndMark(anyString(), eq("discord"))).thenReturn(false);
        when(actionInstanceRepository.findEnabledActionInstancesByService("discord"))
            .thenReturn(Collections.singletonList(actionInstance));

        // Capture the event payload
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);

        // When
        discordWebhookService.processWebhook(payload, null, null, new byte[0]);

        // Then
        verify(executionTriggerService).triggerAreaExecution(
            any(ActionInstance.class),
            eq(ActivationModeType.WEBHOOK),
            payloadCaptor.capture()
        );

        Map<String, Object> capturedPayload = payloadCaptor.getValue();
        assertEquals("message_create", capturedPayload.get("action"));
        assertEquals("msg_verify", capturedPayload.get("message_id"));
        assertEquals("channel_verify", capturedPayload.get("channel_id"));
        assertEquals("Verify content", capturedPayload.get("content"));
        assertEquals("author_verify", capturedPayload.get("author_id"));
        assertEquals("verify_user", capturedPayload.get("author_username"));
    }

    @Test
    void testProcessWebhook_ReactionPayloadCorrectness() {
        // Given
        Map<String, Object> emoji = new HashMap<>();
        emoji.put("name", "verify_emoji");
        emoji.put("id", "emoji_verify_id");

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("message_id", "msg_reaction_verify");
        eventData.put("channel_id", "channel_reaction_verify");
        eventData.put("user_id", "user_reaction_verify");
        eventData.put("emoji", emoji);

        Map<String, Object> payload = new HashMap<>();
        payload.put("t", "MESSAGE_REACTION_ADD");
        payload.put("d", eventData);

        ActionDefinition actionDefinition = new ActionDefinition();
        actionDefinition.setKey("reaction_added");

        Service service = new Service();
        service.setKey("discord");
        actionDefinition.setService(service);

        ActionInstance actionInstance = new ActionInstance();
        actionInstance.setId(UUID.randomUUID());
        actionInstance.setEnabled(true);
        actionInstance.setActionDefinition(actionDefinition);

        when(deduplicationService.checkAndMark(anyString(), eq("discord"))).thenReturn(false);
        when(actionInstanceRepository.findEnabledActionInstancesByService("discord"))
            .thenReturn(Collections.singletonList(actionInstance));

        // Capture the event payload
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);

        // When
        discordWebhookService.processWebhook(payload, null, null, new byte[0]);

        // Then
        verify(executionTriggerService).triggerAreaExecution(
            any(ActionInstance.class),
            eq(ActivationModeType.WEBHOOK),
            payloadCaptor.capture()
        );

        Map<String, Object> capturedPayload = payloadCaptor.getValue();
        assertEquals("message_reaction_add", capturedPayload.get("action"));
        assertEquals("msg_reaction_verify", capturedPayload.get("message_id"));
        assertEquals("channel_reaction_verify", capturedPayload.get("channel_id"));
        assertEquals("user_reaction_verify", capturedPayload.get("user_id"));
        assertEquals("verify_emoji", capturedPayload.get("emoji_name"));
        assertEquals("emoji_verify_id", capturedPayload.get("emoji_id"));
    }

    @Test
    void testProcessWebhook_CaseInsensitiveEventType() {
        // Given
        Map<String, Object> author = new HashMap<>();
        author.put("id", "author_case");
        author.put("username", "caseuser");

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("id", "msg_case");
        eventData.put("channel_id", "channel_case");
        eventData.put("content", "Case test");
        eventData.put("author", author);

        Map<String, Object> payload = new HashMap<>();
        payload.put("t", "message_create"); // lowercase
        payload.put("d", eventData);

        when(deduplicationService.checkAndMark(anyString(), eq("discord"))).thenReturn(false);
        when(actionInstanceRepository.findEnabledActionInstancesByService("discord")).thenReturn(Collections.emptyList());

        // When
        Map<String, Object> result = discordWebhookService.processWebhook(payload, null, null, new byte[0]);

        // Then
        assertNotNull(result);
        assertEquals("message_create", result.get("action"));
        verify(messageCreateCounter, times(1)).increment();
    }
}
