package area.server.AREA_Back.service;

import area.server.AREA_Back.entity.UserOAuthIdentity;
import area.server.AREA_Back.repository.UserOAuthIdentityRepository;
import area.server.AREA_Back.service.Area.Services.SlackActionService;
import area.server.AREA_Back.service.Auth.TokenEncryptionService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class SlackActionServiceTest {

    @Mock
    private UserOAuthIdentityRepository userOAuthIdentityRepository;

    @Mock
    private TokenEncryptionService tokenEncryptionService;

    @Mock
    private RestTemplate restTemplate;

    private SimpleMeterRegistry meterRegistry;
    private SlackActionService slackActionService;

    private UUID testUserId;
    private String testToken;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        slackActionService = new SlackActionService(
            userOAuthIdentityRepository,
            tokenEncryptionService,
            restTemplate,
            meterRegistry
        );
        slackActionService.init();

        testUserId = UUID.randomUUID();
        testToken = "xoxb-test-token";
    }

    // Test init()
    @Test
    void testServiceInitialization() {
        assertNotNull(slackActionService);
        assertNotNull(meterRegistry);
        assertEquals(0.0, meterRegistry.counter("slack_actions_executed_total").count());
        assertEquals(0.0, meterRegistry.counter("slack_actions_failed_total").count());
    }

    // Test getSlackToken() - with bot token
    @Test
    void testGetSlackTokenWithBotToken() {
        UserOAuthIdentity identity = new UserOAuthIdentity();
        Map<String, Object> tokenMeta = new HashMap<>();
        tokenMeta.put("bot_access_token_enc", "encrypted-bot-token");
        identity.setTokenMeta(tokenMeta);
        identity.setAccessTokenEnc("encrypted-user-token");

        when(userOAuthIdentityRepository.findByUserIdAndProvider(testUserId, "slack"))
            .thenReturn(Optional.of(identity));
        when(tokenEncryptionService.decryptToken("encrypted-bot-token"))
            .thenReturn("decrypted-bot-token");

        Map<String, Object> params = Map.of("channel", "C123", "text", "test");
        Map<String, Object> response = Map.of("ok", true, "channel", "C123", "ts", "123456");

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        Map<String, Object> result = slackActionService.executeSlackAction(
            "send_message", Map.of(), params, testUserId
        );

        verify(tokenEncryptionService).decryptToken("encrypted-bot-token");
        assertTrue((Boolean) result.get("success"));
    }

    // Test getSlackToken() - with user token
    @Test
    void testGetSlackTokenWithUserToken() {
        UserOAuthIdentity identity = new UserOAuthIdentity();
        identity.setAccessTokenEnc("encrypted-user-token");
        identity.setTokenMeta(new HashMap<>());

        when(userOAuthIdentityRepository.findByUserIdAndProvider(testUserId, "slack"))
            .thenReturn(Optional.of(identity));
        when(tokenEncryptionService.decryptToken("encrypted-user-token"))
            .thenReturn("decrypted-user-token");

        Map<String, Object> params = Map.of("channel", "C123", "text", "test");
        Map<String, Object> response = Map.of("ok", true, "channel", "C123", "ts", "123456");

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        Map<String, Object> result = slackActionService.executeSlackAction(
            "send_message", Map.of(), params, testUserId
        );

        verify(tokenEncryptionService).decryptToken("encrypted-user-token");
        assertTrue((Boolean) result.get("success"));
    }

    // Test getSlackToken() - no identity found
    @Test
    void testGetSlackTokenNoIdentity() {
        when(userOAuthIdentityRepository.findByUserIdAndProvider(testUserId, "slack"))
            .thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            slackActionService.executeSlackAction("send_message", Map.of(), Map.of(), testUserId);
        });

        assertTrue(exception.getMessage().contains("No Slack token found"));
    }

    // Test getSlackToken() - empty token
    @Test
    void testGetSlackTokenEmptyToken() {
        UserOAuthIdentity identity = new UserOAuthIdentity();
        identity.setAccessTokenEnc("");
        identity.setTokenMeta(new HashMap<>());

        when(userOAuthIdentityRepository.findByUserIdAndProvider(testUserId, "slack"))
            .thenReturn(Optional.of(identity));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            slackActionService.executeSlackAction("send_message", Map.of(), Map.of(), testUserId);
        });

        assertTrue(exception.getMessage().contains("No Slack token found"));
    }

    // Test executeSlackAction() - unknown action
    @Test
    void testExecuteSlackActionUnknownAction() {
        setupMockToken();

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            slackActionService.executeSlackAction("unknown_action", Map.of(), Map.of(), testUserId);
        });

        assertTrue(exception.getMessage().contains("Unknown Slack action"));
        assertEquals(1.0, meterRegistry.counter("slack_actions_failed_total").count());
    }

    // Test sendMessage() - success
    @Test
    void testSendMessageSuccess() {
        setupMockToken();

        Map<String, Object> params = Map.of(
            "channel", "C123456",
            "text", "Hello, Slack!"
        );

        Map<String, Object> response = Map.of(
            "ok", true,
            "channel", "C123456",
            "ts", "1234567890.123456"
        );

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        Map<String, Object> result = slackActionService.executeSlackAction(
            "send_message", Map.of(), params, testUserId
        );

        assertTrue((Boolean) result.get("success"));
        assertEquals("C123456", result.get("channel"));
        assertEquals("1234567890.123456", result.get("ts"));
    }

    // Test sendMessage() - with thread and blocks
    @Test
    void testSendMessageWithThreadAndBlocks() {
        setupMockToken();

        List<Map<String, Object>> blocks = List.of(
            Map.of("type", "section", "text", Map.of("type", "mrkdwn", "text", "Hello"))
        );

        Map<String, Object> params = Map.of(
            "channel", "C123456",
            "text", "Hello",
            "thread_ts", "1234567890.123456",
            "blocks", blocks
        );

        Map<String, Object> response = Map.of(
            "ok", true,
            "channel", "C123456",
            "ts", "1234567890.123457"
        );

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        Map<String, Object> result = slackActionService.executeSlackAction(
            "send_message", Map.of(), params, testUserId
        );

        assertTrue((Boolean) result.get("success"));
    }

    // Test sendMessage() - missing required parameter
    @Test
    void testSendMessageMissingChannel() {
        setupMockToken();

        Map<String, Object> params = Map.of("text", "Hello");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            slackActionService.executeSlackAction("send_message", Map.of(), params, testUserId);
        });

        assertTrue(exception.getMessage().contains("Required parameter 'channel' is missing"));
    }

    // Test sendMessage() - API error
    @Test
    void testSendMessageApiError() {
        setupMockToken();

        Map<String, Object> params = Map.of("channel", "C123", "text", "test");
        Map<String, Object> response = Map.of("ok", false, "error", "channel_not_found");

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            slackActionService.executeSlackAction("send_message", Map.of(), params, testUserId);
        });

        assertTrue(exception.getMessage().contains("Failed to send Slack message"));
    }

    // Test sendMessage() - null response
    @Test
    void testSendMessageNullResponse() {
        setupMockToken();

        Map<String, Object> params = Map.of("channel", "C123", "text", "test");

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            slackActionService.executeSlackAction("send_message", Map.of(), params, testUserId);
        });

        assertTrue(exception.getMessage().contains("Failed to send Slack message"));
    }

    // Test createChannel() - success
    @Test
    void testCreateChannelSuccess() {
        setupMockToken();

        Map<String, Object> params = Map.of("name", "new-channel");
        Map<String, Object> channel = Map.of("id", "C789", "name", "new-channel");
        Map<String, Object> response = Map.of("ok", true, "channel", channel);

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        Map<String, Object> result = slackActionService.executeSlackAction(
            "create_channel", Map.of(), params, testUserId
        );

        assertTrue((Boolean) result.get("success"));
        assertEquals("C789", result.get("channel_id"));
        assertEquals("new-channel", result.get("channel_name"));
    }

    // Test createChannel() - private channel
    @Test
    void testCreatePrivateChannel() {
        setupMockToken();

        Map<String, Object> params = Map.of("name", "private-channel", "is_private", true);
        Map<String, Object> channel = Map.of("id", "C789", "name", "private-channel");
        Map<String, Object> response = Map.of("ok", true, "channel", channel);

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        Map<String, Object> result = slackActionService.executeSlackAction(
            "create_channel", Map.of(), params, testUserId
        );

        assertTrue((Boolean) result.get("success"));
    }

    // Test createChannel() - API error
    @Test
    void testCreateChannelApiError() {
        setupMockToken();

        Map<String, Object> params = Map.of("name", "test-channel");
        Map<String, Object> response = Map.of("ok", false, "error", "name_taken");

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            slackActionService.executeSlackAction("create_channel", Map.of(), params, testUserId);
        });

        assertTrue(exception.getMessage().contains("Failed to create Slack channel"));
    }

    // Test createChannel() - null channel in response
    @Test
    void testCreateChannelNullChannelInResponse() {
        setupMockToken();

        Map<String, Object> params = Map.of("name", "test-channel");
        Map<String, Object> response = Map.of("ok", true);

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        Map<String, Object> result = slackActionService.executeSlackAction(
            "create_channel", Map.of(), params, testUserId
        );

        assertTrue((Boolean) result.get("success"));
        assertNull(result.get("channel_id"));
    }

    // Test addReaction() - success
    @Test
    void testAddReactionSuccess() {
        setupMockToken();

        Map<String, Object> params = Map.of(
            "channel", "C123",
            "timestamp", "1234567890.123456",
            "emoji", ":thumbsup:"
        );

        Map<String, Object> response = Map.of("ok", true);

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        Map<String, Object> result = slackActionService.executeSlackAction(
            "add_reaction", Map.of(), params, testUserId
        );

        assertTrue((Boolean) result.get("success"));
        assertEquals(":thumbsup:", result.get("emoji"));
    }

    // Test addReaction() - emoji without colons
    @Test
    void testAddReactionEmojiWithoutColons() {
        setupMockToken();

        Map<String, Object> params = Map.of(
            "channel", "C123",
            "timestamp", "1234567890.123456",
            "emoji", "thumbsup"
        );

        Map<String, Object> response = Map.of("ok", true);

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        Map<String, Object> result = slackActionService.executeSlackAction(
            "add_reaction", Map.of(), params, testUserId
        );

        assertTrue((Boolean) result.get("success"));
    }

    // Test addReaction() - API error
    @Test
    void testAddReactionApiError() {
        setupMockToken();

        Map<String, Object> params = Map.of(
            "channel", "C123",
            "timestamp", "1234567890.123456",
            "emoji", ":thumbsup:"
        );

        Map<String, Object> response = Map.of("ok", false, "error", "message_not_found");

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            slackActionService.executeSlackAction("add_reaction", Map.of(), params, testUserId);
        });

        assertTrue(exception.getMessage().contains("Failed to add Slack reaction"));
    }

    // Test pinMessage() - success
    @Test
    void testPinMessageSuccess() {
        setupMockToken();

        Map<String, Object> params = Map.of(
            "channel", "C123",
            "timestamp", "1234567890.123456"
        );

        Map<String, Object> response = Map.of("ok", true);

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        Map<String, Object> result = slackActionService.executeSlackAction(
            "pin_message", Map.of(), params, testUserId
        );

        assertTrue((Boolean) result.get("success"));
    }

    // Test pinMessage() - API error
    @Test
    void testPinMessageApiError() {
        setupMockToken();

        Map<String, Object> params = Map.of(
            "channel", "C123",
            "timestamp", "1234567890.123456"
        );

        Map<String, Object> response = Map.of("ok", false, "error", "message_not_found");

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            slackActionService.executeSlackAction("pin_message", Map.of(), params, testUserId);
        });

        assertTrue(exception.getMessage().contains("Failed to pin Slack message"));
    }

    // Test inviteToChannel() - success
    @Test
    void testInviteToChannelSuccess() {
        setupMockToken();

        Map<String, Object> params = Map.of(
            "channel", "C123",
            "users", "U123,U456"
        );

        Map<String, Object> response = Map.of("ok", true);

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        Map<String, Object> result = slackActionService.executeSlackAction(
            "invite_to_channel", Map.of(), params, testUserId
        );

        assertTrue((Boolean) result.get("success"));
        assertEquals("C123", result.get("channel"));
    }

    // Test inviteToChannel() - API error
    @Test
    void testInviteToChannelApiError() {
        setupMockToken();

        Map<String, Object> params = Map.of(
            "channel", "C123",
            "users", "U123"
        );

        Map<String, Object> response = Map.of("ok", false, "error", "channel_not_found");

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            slackActionService.executeSlackAction("invite_to_channel", Map.of(), params, testUserId);
        });

        assertTrue(exception.getMessage().contains("Failed to invite to Slack channel"));
    }

    // Test checkSlackEvents() - unknown event
    @Test
    void testCheckSlackEventsUnknownEvent() {
        setupMockToken();

        List<Map<String, Object>> result = slackActionService.checkSlackEvents(
            "unknown_event", Map.of(), testUserId, LocalDateTime.now()
        );

        assertTrue(result.isEmpty());
    }

    // Test checkSlackEvents() - no token
    @Test
    void testCheckSlackEventsNoToken() {
        when(userOAuthIdentityRepository.findByUserIdAndProvider(testUserId, "slack"))
            .thenReturn(Optional.empty());

        List<Map<String, Object>> result = slackActionService.checkSlackEvents(
            "new_message", Map.of("channel", "C123"), testUserId, LocalDateTime.now()
        );

        assertTrue(result.isEmpty());
    }

    // Test checkNewMessages() - success
    @Test
    void testCheckNewMessagesSuccess() {
        setupMockToken();

        Map<String, Object> params = Map.of("channel", "C123");
        LocalDateTime lastCheck = LocalDateTime.now().minusHours(1);

        List<Map<String, Object>> messages = List.of(
            Map.of("text", "Hello", "user", "U123", "ts", "1234567890.123456"),
            Map.of("text", "World", "user", "U456", "ts", "1234567890.123457")
        );

        Map<String, Object> response = Map.of("ok", true, "messages", messages);

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        List<Map<String, Object>> result = slackActionService.checkSlackEvents(
            "new_message", params, testUserId, lastCheck
        );

        assertEquals(2, result.size());
        assertEquals("message", result.get(0).get("type"));
        assertEquals("Hello", result.get(0).get("text"));
    }

    // Test checkNewMessages() - channel not found
    @Test
    void testCheckNewMessagesChannelNotFound() {
        setupMockToken();

        Map<String, Object> params = Map.of("channel", "C123");
        Map<String, Object> response = Map.of("ok", false, "error", "channel_not_found");

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        List<Map<String, Object>> result = slackActionService.checkSlackEvents(
            "new_message", params, testUserId, LocalDateTime.now()
        );

        assertTrue(result.isEmpty());
    }

    // Test checkNewMessages() - other API error
    @Test
    void testCheckNewMessagesOtherError() {
        setupMockToken();

        Map<String, Object> params = Map.of("channel", "C123");
        Map<String, Object> response = Map.of("ok", false, "error", "invalid_auth");

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        List<Map<String, Object>> result = slackActionService.checkSlackEvents(
            "new_message", params, testUserId, LocalDateTime.now()
        );

        assertTrue(result.isEmpty());
    }

    // Test checkNewMessages() - null response
    @Test
    void testCheckNewMessagesNullResponse() {
        setupMockToken();

        Map<String, Object> params = Map.of("channel", "C123");

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

        List<Map<String, Object>> result = slackActionService.checkSlackEvents(
            "new_message", params, testUserId, LocalDateTime.now()
        );

        assertTrue(result.isEmpty());
    }

    // Test checkNewMessages() - null messages
    @Test
    void testCheckNewMessagesNullMessages() {
        setupMockToken();

        Map<String, Object> params = Map.of("channel", "C123");
        Map<String, Object> response = Map.of("ok", true);

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        List<Map<String, Object>> result = slackActionService.checkSlackEvents(
            "new_message", params, testUserId, LocalDateTime.now()
        );

        assertTrue(result.isEmpty());
    }

    // Test checkNewChannels() - success
    @Test
    void testCheckNewChannelsSuccess() {
        setupMockToken();

        LocalDateTime lastCheck = LocalDateTime.now().minusHours(1);
        long lastCheckEpoch = lastCheck.toEpochSecond(ZoneOffset.UTC);

        List<Map<String, Object>> channels = List.of(
            Map.of("id", "C123", "name", "new-channel", "created", lastCheckEpoch + 100),
            Map.of("id", "C456", "name", "old-channel", "created", lastCheckEpoch - 100)
        );

        Map<String, Object> response = Map.of("ok", true, "channels", channels);

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        List<Map<String, Object>> result = slackActionService.checkSlackEvents(
            "new_channel", Map.of(), testUserId, lastCheck
        );

        assertEquals(1, result.size());
        assertEquals("channel_created", result.get(0).get("type"));
        assertEquals("new-channel", result.get(0).get("channel_name"));
    }

    // Test checkNewChannels() - API error
    @Test
    void testCheckNewChannelsApiError() {
        setupMockToken();

        Map<String, Object> response = Map.of("ok", false, "error", "invalid_auth");

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        List<Map<String, Object>> result = slackActionService.checkSlackEvents(
            "new_channel", Map.of(), testUserId, LocalDateTime.now()
        );

        assertTrue(result.isEmpty());
    }

    // Test checkNewChannels() - null channels
    @Test
    void testCheckNewChannelsNullChannels() {
        setupMockToken();

        Map<String, Object> response = Map.of("ok", true);

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        List<Map<String, Object>> result = slackActionService.checkSlackEvents(
            "new_channel", Map.of(), testUserId, LocalDateTime.now()
        );

        assertTrue(result.isEmpty());
    }

    // Test checkUserJoined() - success
    @Test
    void testCheckUserJoinedSuccess() {
        setupMockToken();

        Map<String, Object> params = Map.of("channel", "C123");
        List<String> members = List.of("U123", "U456", "U789");

        Map<String, Object> response = Map.of("ok", true, "members", members);

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        List<Map<String, Object>> result = slackActionService.checkSlackEvents(
            "user_joined", params, testUserId, LocalDateTime.now()
        );

        assertEquals(3, result.size());
        assertEquals("user_joined", result.get(0).get("type"));
        assertEquals("U123", result.get(0).get("user"));
    }

    // Test checkUserJoined() - API error
    @Test
    void testCheckUserJoinedApiError() {
        setupMockToken();

        Map<String, Object> params = Map.of("channel", "C123");
        Map<String, Object> response = Map.of("ok", false, "error", "channel_not_found");

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        List<Map<String, Object>> result = slackActionService.checkSlackEvents(
            "user_joined", params, testUserId, LocalDateTime.now()
        );

        assertTrue(result.isEmpty());
    }

    // Test checkUserJoined() - null members
    @Test
    void testCheckUserJoinedNullMembers() {
        setupMockToken();

        Map<String, Object> params = Map.of("channel", "C123");
        Map<String, Object> response = Map.of("ok", true);

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        List<Map<String, Object>> result = slackActionService.checkSlackEvents(
            "user_joined", params, testUserId, LocalDateTime.now()
        );

        assertTrue(result.isEmpty());
    }

    // Test checkReactionAdded() - success
    @Test
    void testCheckReactionAddedSuccess() {
        setupMockToken();

        Map<String, Object> params = Map.of("channel", "C123");

        List<Map<String, Object>> reactions = List.of(
            Map.of("name", "thumbsup", "count", 5),
            Map.of("name", "heart", "count", 3)
        );

        List<Map<String, Object>> messages = List.of(
            Map.of("ts", "1234567890.123456", "reactions", reactions),
            Map.of("ts", "1234567890.123457")
        );

        Map<String, Object> response = Map.of("ok", true, "messages", messages);

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        List<Map<String, Object>> result = slackActionService.checkSlackEvents(
            "reaction_added", params, testUserId, LocalDateTime.now()
        );

        assertEquals(2, result.size());
        assertEquals("reaction_added", result.get(0).get("type"));
        assertEquals("thumbsup", result.get(0).get("emoji"));
    }

    // Test checkReactionAdded() - API error
    @Test
    void testCheckReactionAddedApiError() {
        setupMockToken();

        Map<String, Object> params = Map.of("channel", "C123");
        Map<String, Object> response = Map.of("ok", false, "error", "invalid_auth");

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        List<Map<String, Object>> result = slackActionService.checkSlackEvents(
            "reaction_added", params, testUserId, LocalDateTime.now()
        );

        assertTrue(result.isEmpty());
    }

    // Test checkReactionAdded() - null messages
    @Test
    void testCheckReactionAddedNullMessages() {
        setupMockToken();

        Map<String, Object> params = Map.of("channel", "C123");
        Map<String, Object> response = Map.of("ok", true);

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        List<Map<String, Object>> result = slackActionService.checkSlackEvents(
            "reaction_added", params, testUserId, LocalDateTime.now()
        );

        assertTrue(result.isEmpty());
    }

    // Test checkFileShared() - success
    @Test
    void testCheckFileSharedSuccess() {
        setupMockToken();

        Map<String, Object> params = Map.of("channel", "C123");

        List<Map<String, Object>> files = List.of(
            Map.of("id", "F123", "name", "file1.txt", "user", "U123", "timestamp", 1234567890),
            Map.of("id", "F456", "name", "file2.pdf", "user", "U456", "timestamp", 1234567891)
        );

        Map<String, Object> response = Map.of("ok", true, "files", files);

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        List<Map<String, Object>> result = slackActionService.checkSlackEvents(
            "file_shared", params, testUserId, LocalDateTime.now()
        );

        assertEquals(2, result.size());
        assertEquals("file_shared", result.get(0).get("type"));
        assertEquals("file1.txt", result.get(0).get("file_name"));
    }

    // Test checkFileShared() - API error
    @Test
    void testCheckFileSharedApiError() {
        setupMockToken();

        Map<String, Object> params = Map.of("channel", "C123");
        Map<String, Object> response = Map.of("ok", false, "error", "invalid_auth");

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        List<Map<String, Object>> result = slackActionService.checkSlackEvents(
            "file_shared", params, testUserId, LocalDateTime.now()
        );

        assertTrue(result.isEmpty());
    }

    // Test checkFileShared() - null files
    @Test
    void testCheckFileSharedNullFiles() {
        setupMockToken();

        Map<String, Object> params = Map.of("channel", "C123");
        Map<String, Object> response = Map.of("ok", true);

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        List<Map<String, Object>> result = slackActionService.checkSlackEvents(
            "file_shared", params, testUserId, LocalDateTime.now()
        );

        assertTrue(result.isEmpty());
    }

    // Test getRequiredParam() - success
    @Test
    void testGetRequiredParamSuccess() {
        setupMockToken();

        Map<String, Object> params = Map.of("channel", "C123", "text", "test");
        Map<String, Object> response = Map.of("ok", true, "channel", "C123", "ts", "123456");

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        Map<String, Object> result = slackActionService.executeSlackAction(
            "send_message", Map.of(), params, testUserId
        );

        assertTrue((Boolean) result.get("success"));
    }

    // Test getRequiredParam() - wrong type
    @Test
    void testGetRequiredParamWrongType() {
        setupMockToken();

        Map<String, Object> params = Map.of("channel", 123, "text", "test");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            slackActionService.executeSlackAction("send_message", Map.of(), params, testUserId);
        });

        assertTrue(exception.getMessage().contains("Parameter 'channel' must be of type String"));
    }

    // Test getOptionalParam() - success with default
    @Test
    void testGetOptionalParamWithDefault() {
        setupMockToken();

        Map<String, Object> params = Map.of("name", "test-channel");
        Map<String, Object> channel = Map.of("id", "C789", "name", "test-channel");
        Map<String, Object> response = Map.of("ok", true, "channel", channel);

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        Map<String, Object> result = slackActionService.executeSlackAction(
            "create_channel", Map.of(), params, testUserId
        );

        assertTrue((Boolean) result.get("success"));
    }

    // Test getOptionalParam() - wrong type uses default
    @Test
    void testGetOptionalParamWrongTypeUsesDefault() {
        setupMockToken();

        Map<String, Object> params = Map.of("name", "test-channel", "is_private", "not-a-boolean");
        Map<String, Object> channel = Map.of("id", "C789", "name", "test-channel");
        Map<String, Object> response = Map.of("ok", true, "channel", channel);

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        Map<String, Object> result = slackActionService.executeSlackAction(
            "create_channel", Map.of(), params, testUserId
        );

        assertTrue((Boolean) result.get("success"));
    }

    // Test createSlackHeaders()
    @Test
    void testCreateSlackHeaders() {
        setupMockToken();

        Map<String, Object> params = Map.of("channel", "C123", "text", "test");
        Map<String, Object> response = Map.of("ok", true, "channel", "C123", "ts", "123456");

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        slackActionService.executeSlackAction("send_message", Map.of(), params, testUserId);

        verify(restTemplate).exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        );
    }

    // Helper method to setup mock token
    private void setupMockToken() {
        UserOAuthIdentity identity = new UserOAuthIdentity();
        identity.setAccessTokenEnc("encrypted-token");
        identity.setTokenMeta(new HashMap<>());

        when(userOAuthIdentityRepository.findByUserIdAndProvider(testUserId, "slack"))
            .thenReturn(Optional.of(identity));
        when(tokenEncryptionService.decryptToken("encrypted-token"))
            .thenReturn(testToken);
    }
}
