package area.server.AREA_Back.service;

import area.server.AREA_Back.entity.UserOAuthIdentity;
import area.server.AREA_Back.repository.UserOAuthIdentityRepository;
import area.server.AREA_Back.service.Area.Services.DiscordActionService;
import area.server.AREA_Back.service.Auth.OAuthTokenRefreshService;
import area.server.AREA_Back.service.Auth.TokenEncryptionService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DiscordActionServiceTest {

    @Mock
    private UserOAuthIdentityRepository userOAuthIdentityRepository;

    @Mock
    private TokenEncryptionService tokenEncryptionService;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private OAuthTokenRefreshService oauthTokenRefreshService;

    private SimpleMeterRegistry meterRegistry;
    private DiscordActionService discordActionService;

    private static final String TEST_TOKEN = "test_token_123";
    private static final String TEST_BOT_TOKEN = "bot_token_123";
    private static final UUID TEST_USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        discordActionService = new DiscordActionService(
            userOAuthIdentityRepository,
            tokenEncryptionService,
            restTemplate,
            meterRegistry,
            oauthTokenRefreshService
        );

        ReflectionTestUtils.setField(discordActionService, "discordBotToken", TEST_BOT_TOKEN);
        ReflectionTestUtils.setField(discordActionService, "discordClientId", "test-client-id");
        ReflectionTestUtils.setField(discordActionService, "discordClientSecret", "test-client-secret");

        try {
            var initMethod = DiscordActionService.class.getDeclaredMethod("init");
            initMethod.setAccessible(true);
            initMethod.invoke(discordActionService);
        } catch (Exception e) {
            fail("Failed to initialize metrics: " + e.getMessage());
        }
    }

    @Test
    void testServiceInitialization() {
        assertNotNull(discordActionService);
        assertNotNull(meterRegistry);
    }

    @Test
    void testMetricsAreRegistered() {
        assertNotNull(meterRegistry.find("discord_actions_executed_total").counter());
        assertNotNull(meterRegistry.find("discord_actions_failed_total").counter());
    }

    // ==================== executeDiscordAction Tests ====================

    @Test
    void testExecuteDiscordAction_SendMessage() {
        setupOAuthToken();
        Map<String, Object> params = new HashMap<>();
        params.put("channel_id", "123456");
        params.put("content", "Test message");

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("id", "msg123");
        ResponseEntity<Map<String, Object>> response = new ResponseEntity<>(responseBody, HttpStatus.OK);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
            any(ParameterizedTypeReference.class))).thenReturn(response);

        Map<String, Object> result = discordActionService.executeDiscordAction(
            "send_message", new HashMap<>(), params, TEST_USER_ID);

        assertTrue((Boolean) result.get("success"));
        assertEquals("msg123", result.get("message_id"));
    }

    @Test
    void testExecuteDiscordAction_CreateChannel() {
        setupOAuthToken();
        Map<String, Object> params = new HashMap<>();
        params.put("guild_id", "guild123");
        params.put("name", "test-channel");

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("id", "channel123");
        ResponseEntity<Map<String, Object>> response = new ResponseEntity<>(responseBody, HttpStatus.OK);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
            any(ParameterizedTypeReference.class))).thenReturn(response);

        Map<String, Object> result = discordActionService.executeDiscordAction(
            "create_channel", new HashMap<>(), params, TEST_USER_ID);

        assertTrue((Boolean) result.get("success"));
        assertEquals("channel123", result.get("channel_id"));
    }

    @Test
    void testExecuteDiscordAction_AddReaction() {
        setupOAuthToken();
        Map<String, Object> params = new HashMap<>();
        params.put("channel_id", "123456");
        params.put("message_id", "msg123");
        params.put("emoji", "👍");

        ResponseEntity<Void> response = new ResponseEntity<>(HttpStatus.NO_CONTENT);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(Void.class)))
            .thenReturn(response);

        Map<String, Object> result = discordActionService.executeDiscordAction(
            "add_reaction", new HashMap<>(), params, TEST_USER_ID);

        assertTrue((Boolean) result.get("success"));
        assertEquals("👍", result.get("emoji"));
    }

    @Test
    void testExecuteDiscordAction_SendDM() {
        setupOAuthToken();
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", "user123");
        params.put("content", "DM content");

        Map<String, Object> dmChannelResponse = new HashMap<>();
        dmChannelResponse.put("id", "dm_channel123");
        ResponseEntity<Map<String, Object>> dmResponse = new ResponseEntity<>(dmChannelResponse, HttpStatus.OK);

        Map<String, Object> messageResponse = new HashMap<>();
        messageResponse.put("id", "dm_msg123");
        ResponseEntity<Map<String, Object>> msgResponse = new ResponseEntity<>(messageResponse, HttpStatus.OK);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
            any(ParameterizedTypeReference.class)))
            .thenReturn(dmResponse)
            .thenReturn(msgResponse);

        Map<String, Object> result = discordActionService.executeDiscordAction(
            "send_dm", new HashMap<>(), params, TEST_USER_ID);

        assertTrue((Boolean) result.get("success"));
        assertEquals("user123", result.get("user_id"));
    }

    @Test
    void testExecuteDiscordAction_AddRole() {
        setupOAuthToken();
        Map<String, Object> params = new HashMap<>();
        params.put("guild_id", "guild123");
        params.put("user_id", "user123");
        params.put("role_id", "role123");

        ResponseEntity<Void> response = new ResponseEntity<>(HttpStatus.NO_CONTENT);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(Void.class)))
            .thenReturn(response);

        Map<String, Object> result = discordActionService.executeDiscordAction(
            "add_role", new HashMap<>(), params, TEST_USER_ID);

        assertTrue((Boolean) result.get("success"));
        assertEquals("role123", result.get("role_id"));
    }

    @Test
    void testExecuteDiscordAction_RemoveRole() {
        setupOAuthToken();
        Map<String, Object> params = new HashMap<>();
        params.put("guild_id", "guild123");
        params.put("user_id", "user123");
        params.put("role_id", "role123");

        ResponseEntity<Void> response = new ResponseEntity<>(HttpStatus.NO_CONTENT);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(Void.class)))
            .thenReturn(response);

        Map<String, Object> result = discordActionService.executeDiscordAction(
            "remove_role", new HashMap<>(), params, TEST_USER_ID);

        assertTrue((Boolean) result.get("success"));
        assertEquals("role123", result.get("role_id"));
    }

    @Test
    void testExecuteDiscordAction_DeleteMessage() {
        setupOAuthToken();
        Map<String, Object> params = new HashMap<>();
        params.put("channel_id", "channel123");
        params.put("message_id", "msg123");

        ResponseEntity<Void> response = new ResponseEntity<>(HttpStatus.NO_CONTENT);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(Void.class)))
            .thenReturn(response);

        Map<String, Object> result = discordActionService.executeDiscordAction(
            "delete_message", new HashMap<>(), params, TEST_USER_ID);

        assertTrue((Boolean) result.get("success"));
        assertEquals("msg123", result.get("message_id"));
    }

    @Test
    void testExecuteDiscordAction_PinMessage() {
        setupOAuthToken();
        Map<String, Object> params = new HashMap<>();
        params.put("channel_id", "channel123");
        params.put("message_id", "msg123");

        ResponseEntity<Void> response = new ResponseEntity<>(HttpStatus.NO_CONTENT);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(Void.class)))
            .thenReturn(response);

        Map<String, Object> result = discordActionService.executeDiscordAction(
            "pin_message", new HashMap<>(), params, TEST_USER_ID);

        assertTrue((Boolean) result.get("success"));
        assertEquals("msg123", result.get("message_id"));
    }

    @Test
    void testExecuteDiscordAction_KickMember() {
        setupOAuthToken();
        Map<String, Object> params = new HashMap<>();
        params.put("guild_id", "guild123");
        params.put("user_id", "user123");

        ResponseEntity<Void> response = new ResponseEntity<>(HttpStatus.NO_CONTENT);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(Void.class)))
            .thenReturn(response);

        Map<String, Object> result = discordActionService.executeDiscordAction(
            "kick_member", new HashMap<>(), params, TEST_USER_ID);

        assertTrue((Boolean) result.get("success"));
        assertEquals("user123", result.get("user_id"));
    }

    @Test
    void testExecuteDiscordAction_BanMember() {
        setupOAuthToken();
        Map<String, Object> params = new HashMap<>();
        params.put("guild_id", "guild123");
        params.put("user_id", "user123");

        ResponseEntity<Void> response = new ResponseEntity<>(HttpStatus.NO_CONTENT);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(Void.class)))
            .thenReturn(response);

        Map<String, Object> result = discordActionService.executeDiscordAction(
            "ban_member", new HashMap<>(), params, TEST_USER_ID);

        assertTrue((Boolean) result.get("success"));
        assertEquals("user123", result.get("user_id"));
    }

    @Test
    void testExecuteDiscordAction_UnknownAction() {
        setupOAuthToken();
        
        assertThrows(RuntimeException.class, () -> {
            discordActionService.executeDiscordAction(
                "unknown_action", new HashMap<>(), new HashMap<>(), TEST_USER_ID);
        });
    }

    @Test
    void testExecuteDiscordAction_NoToken() {
        when(userOAuthIdentityRepository.findByUserIdAndProvider(TEST_USER_ID, "discord"))
            .thenReturn(Optional.empty());

        Map<String, Object> params = new HashMap<>();
        params.put("channel_id", "123456");

        assertThrows(RuntimeException.class, () -> {
            discordActionService.executeDiscordAction(
                "create_channel", new HashMap<>(), params, TEST_USER_ID);
        });
    }

    @Test
    void testExecuteDiscordAction_BotTokenNotConfigured() {
        ReflectionTestUtils.setField(discordActionService, "discordBotToken", null);

        Map<String, Object> params = new HashMap<>();
        params.put("channel_id", "123456");
        params.put("content", "Test");

        assertThrows(RuntimeException.class, () -> {
            discordActionService.executeDiscordAction(
                "send_message", new HashMap<>(), params, TEST_USER_ID);
        });
    }

    // ==================== checkDiscordEvents Tests ====================

    @Test
    void testCheckDiscordEvents_NewMessage() throws Exception {
        setupOAuthToken();
        Map<String, Object> params = new HashMap<>();
        params.put("channel_id", "channel123");

        List<Map<String, Object>> messages = createMockMessages();
        ResponseEntity<List<Map<String, Object>>> response = new ResponseEntity<>(messages, HttpStatus.OK);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
            any(ParameterizedTypeReference.class))).thenReturn(response);

        List<Map<String, Object>> result = discordActionService.checkDiscordEvents(
            "new_message", params, TEST_USER_ID, LocalDateTime.now().minusHours(1));

        assertFalse(result.isEmpty());
    }

    @Test
    void testCheckDiscordEvents_NewMember() {
        setupOAuthToken();
        Map<String, Object> params = new HashMap<>();
        params.put("guild_id", "guild123");

        List<Map<String, Object>> members = createMockMembers();
        ResponseEntity<List<Map<String, Object>>> response = new ResponseEntity<>(members, HttpStatus.OK);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
            any(ParameterizedTypeReference.class))).thenReturn(response);

        List<Map<String, Object>> result = discordActionService.checkDiscordEvents(
            "new_member", params, TEST_USER_ID, LocalDateTime.now().minusHours(1));

        assertFalse(result.isEmpty());
    }

    @Test
    void testCheckDiscordEvents_MessageReaction() {
        setupOAuthToken();
        Map<String, Object> params = new HashMap<>();

        List<Map<String, Object>> result = discordActionService.checkDiscordEvents(
            "message_reaction", params, TEST_USER_ID, LocalDateTime.now().minusHours(1));

        assertTrue(result.isEmpty());
    }

    @Test
    void testCheckDiscordEvents_NoToken() {
        when(userOAuthIdentityRepository.findByUserIdAndProvider(TEST_USER_ID, "discord"))
            .thenReturn(Optional.empty());

        List<Map<String, Object>> result = discordActionService.checkDiscordEvents(
            "new_message", new HashMap<>(), TEST_USER_ID, LocalDateTime.now());

        assertTrue(result.isEmpty());
    }

    // ==================== Private Method Tests via Reflection ====================

    @Test
    void testParseDiscordTimestamp_Valid() throws Exception {
        Method method = DiscordActionService.class.getDeclaredMethod("parseDiscordTimestamp", String.class);
        method.setAccessible(true);

        String timestamp = ZonedDateTime.now().toString();
        LocalDateTime result = (LocalDateTime) method.invoke(discordActionService, timestamp);

        assertNotNull(result);
    }

    @Test
    void testParseDiscordTimestamp_Invalid() throws Exception {
        Method method = DiscordActionService.class.getDeclaredMethod("parseDiscordTimestamp", String.class);
        method.setAccessible(true);

        LocalDateTime result = (LocalDateTime) method.invoke(discordActionService, "invalid-timestamp");

        assertNull(result);
    }

    @Test
    void testParseDiscordTimestamp_Null() throws Exception {
        Method method = DiscordActionService.class.getDeclaredMethod("parseDiscordTimestamp", String.class);
        method.setAccessible(true);

        LocalDateTime result = (LocalDateTime) method.invoke(discordActionService, (String) null);

        assertNull(result);
    }

    @Test
    void testRequiresBotToken() throws Exception {
        Method method = DiscordActionService.class.getDeclaredMethod("requiresBotToken", String.class);
        method.setAccessible(true);

        assertTrue((Boolean) method.invoke(discordActionService, "send_message"));
        assertTrue((Boolean) method.invoke(discordActionService, "add_role"));
        assertTrue((Boolean) method.invoke(discordActionService, "remove_role"));
        assertFalse((Boolean) method.invoke(discordActionService, "create_channel"));
        assertFalse((Boolean) method.invoke(discordActionService, "send_dm"));
    }

    @Test
    void testGetRequiredParam_Valid() throws Exception {
        Method method = DiscordActionService.class.getDeclaredMethod(
            "getRequiredParam", Map.class, String.class, Class.class);
        method.setAccessible(true);

        Map<String, Object> params = new HashMap<>();
        params.put("test_key", "test_value");

        String result = (String) method.invoke(discordActionService, params, "test_key", String.class);

        assertEquals("test_value", result);
    }

    @Test
    void testGetRequiredParam_Missing() throws Exception {
        Method method = DiscordActionService.class.getDeclaredMethod(
            "getRequiredParam", Map.class, String.class, Class.class);
        method.setAccessible(true);

        Map<String, Object> params = new HashMap<>();

        try {
            method.invoke(discordActionService, params, "missing_key", String.class);
            fail("Expected exception");
        } catch (Exception e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
        }
    }

    @Test
    void testGetRequiredParam_WrongType() throws Exception {
        Method method = DiscordActionService.class.getDeclaredMethod(
            "getRequiredParam", Map.class, String.class, Class.class);
        method.setAccessible(true);

        Map<String, Object> params = new HashMap<>();
        params.put("test_key", 123);

        try {
            method.invoke(discordActionService, params, "test_key", String.class);
            fail("Expected exception");
        } catch (Exception e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
        }
    }

    @Test
    void testGetOptionalParam_Present() throws Exception {
        Method method = DiscordActionService.class.getDeclaredMethod(
            "getOptionalParam", Map.class, String.class, Class.class, Object.class);
        method.setAccessible(true);

        Map<String, Object> params = new HashMap<>();
        params.put("test_key", "test_value");

        String result = (String) method.invoke(discordActionService, params, "test_key", String.class, "default");

        assertEquals("test_value", result);
    }

    @Test
    void testGetOptionalParam_Missing() throws Exception {
        Method method = DiscordActionService.class.getDeclaredMethod(
            "getOptionalParam", Map.class, String.class, Class.class, Object.class);
        method.setAccessible(true);

        Map<String, Object> params = new HashMap<>();

        String result = (String) method.invoke(discordActionService, params, "missing_key", String.class, "default");

        assertEquals("default", result);
    }

    @Test
    void testGetOptionalParam_WrongType() throws Exception {
        Method method = DiscordActionService.class.getDeclaredMethod(
            "getOptionalParam", Map.class, String.class, Class.class, Object.class);
        method.setAccessible(true);

        Map<String, Object> params = new HashMap<>();
        params.put("test_key", 123);

        String result = (String) method.invoke(discordActionService, params, "test_key", String.class, "default");

        assertEquals("default", result);
    }

    @Test
    void testCreateDiscordHeaders_BotToken() throws Exception {
        Method method = DiscordActionService.class.getDeclaredMethod("createDiscordHeaders", String.class);
        method.setAccessible(true);

        Object headers = method.invoke(discordActionService, TEST_BOT_TOKEN);

        assertNotNull(headers);
    }

    @Test
    void testCreateDiscordHeaders_UserToken() throws Exception {
        Method method = DiscordActionService.class.getDeclaredMethod("createDiscordHeaders", String.class);
        method.setAccessible(true);

        Object headers = method.invoke(discordActionService, "user_token_123");

        assertNotNull(headers);
    }

    @Test
    void testGetDiscordToken_Success() throws Exception {
        UserOAuthIdentity identity = new UserOAuthIdentity();
        identity.setAccessTokenEnc("encrypted_token");
        identity.setExpiresAt(LocalDateTime.now().plusHours(1));

        when(userOAuthIdentityRepository.findByUserIdAndProvider(TEST_USER_ID, "discord"))
            .thenReturn(Optional.of(identity));
        when(oauthTokenRefreshService.needsRefresh(identity)).thenReturn(false);
        when(tokenEncryptionService.decryptToken("encrypted_token")).thenReturn(TEST_TOKEN);

        Method method = DiscordActionService.class.getDeclaredMethod("getDiscordToken", UUID.class);
        method.setAccessible(true);

        String result = (String) method.invoke(discordActionService, TEST_USER_ID);

        assertEquals(TEST_TOKEN, result);
    }

    @Test
    void testGetDiscordToken_NeedsRefresh() throws Exception {
        UserOAuthIdentity identity = new UserOAuthIdentity();
        identity.setAccessTokenEnc("encrypted_token");
        identity.setExpiresAt(LocalDateTime.now().minusHours(1));

        UserOAuthIdentity refreshedIdentity = new UserOAuthIdentity();
        refreshedIdentity.setAccessTokenEnc("new_encrypted_token");

        when(userOAuthIdentityRepository.findByUserIdAndProvider(TEST_USER_ID, "discord"))
            .thenReturn(Optional.of(identity))
            .thenReturn(Optional.of(refreshedIdentity));
        when(oauthTokenRefreshService.needsRefresh(identity)).thenReturn(true);
        when(oauthTokenRefreshService.refreshDiscordToken(eq(identity), anyString(), anyString())).thenReturn(true);
        when(tokenEncryptionService.decryptToken("new_encrypted_token")).thenReturn("new_token");

        Method method = DiscordActionService.class.getDeclaredMethod("getDiscordToken", UUID.class);
        method.setAccessible(true);

        String result = (String) method.invoke(discordActionService, TEST_USER_ID);

        assertEquals("new_token", result);
    }

    @Test
    void testGetDiscordToken_RefreshFailed() throws Exception {
        UserOAuthIdentity identity = new UserOAuthIdentity();
        identity.setAccessTokenEnc("encrypted_token");
        identity.setExpiresAt(LocalDateTime.now().minusHours(1));

        when(userOAuthIdentityRepository.findByUserIdAndProvider(TEST_USER_ID, "discord"))
            .thenReturn(Optional.of(identity));
        when(oauthTokenRefreshService.needsRefresh(identity)).thenReturn(true);
        when(oauthTokenRefreshService.refreshDiscordToken(eq(identity), anyString(), anyString())).thenReturn(false);

        Method method = DiscordActionService.class.getDeclaredMethod("getDiscordToken", UUID.class);
        method.setAccessible(true);

        String result = (String) method.invoke(discordActionService, TEST_USER_ID);

        assertNull(result);
    }

    @Test
    void testAddRoleToMember_HttpError() {
        setupOAuthToken();
        Map<String, Object> params = new HashMap<>();
        params.put("guild_id", "guild123");
        params.put("user_id", "user123");
        params.put("role_id", "role123");

        when(restTemplate.exchange(anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(Void.class)))
            .thenThrow(new HttpClientErrorException(HttpStatus.FORBIDDEN, "Forbidden"));

        assertThrows(RuntimeException.class, () -> {
            discordActionService.executeDiscordAction("add_role", new HashMap<>(), params, TEST_USER_ID);
        });
    }

    @Test
    void testFetchMessagesFromChannel_WithFilter() {
        setupOAuthToken();
        Map<String, Object> params = new HashMap<>();
        params.put("channel_id", "channel123");
        params.put("contains_text", "hello");

        List<Map<String, Object>> messages = createMockMessages();
        ResponseEntity<List<Map<String, Object>>> response = new ResponseEntity<>(messages, HttpStatus.OK);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
            any(ParameterizedTypeReference.class))).thenReturn(response);

        List<Map<String, Object>> result = discordActionService.checkDiscordEvents(
            "new_message", params, TEST_USER_ID, LocalDateTime.now().minusHours(1));

        assertNotNull(result);
    }

    @Test
    void testCheckNewMessages_WithGuildId() {
        setupOAuthToken();
        Map<String, Object> params = new HashMap<>();
        params.put("guild_id", "guild123");

        List<Map<String, Object>> channels = createMockChannels();
        ResponseEntity<List<Map<String, Object>>> channelResponse = new ResponseEntity<>(channels, HttpStatus.OK);

        List<Map<String, Object>> messages = createMockMessages();
        ResponseEntity<List<Map<String, Object>>> messageResponse = new ResponseEntity<>(messages, HttpStatus.OK);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
            any(ParameterizedTypeReference.class)))
            .thenReturn(channelResponse)
            .thenReturn(messageResponse);

        List<Map<String, Object>> result = discordActionService.checkDiscordEvents(
            "new_message", params, TEST_USER_ID, LocalDateTime.now().minusHours(1));

        assertNotNull(result);
    }

    @Test
    void testCheckNewMessages_NoChannelOrGuild() {
        setupOAuthToken();
        Map<String, Object> params = new HashMap<>();

        List<Map<String, Object>> result = discordActionService.checkDiscordEvents(
            "new_message", params, TEST_USER_ID, LocalDateTime.now().minusHours(1));

        assertTrue(result.isEmpty());
    }

    @Test
    void testSendMessage_WithEmbed() {
        setupOAuthToken();
        Map<String, Object> params = new HashMap<>();
        params.put("channel_id", "123456");
        params.put("content", "Test message");
        Map<String, Object> embed = new HashMap<>();
        embed.put("title", "Test Embed");
        params.put("embed", embed);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("id", "msg123");
        ResponseEntity<Map<String, Object>> response = new ResponseEntity<>(responseBody, HttpStatus.OK);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
            any(ParameterizedTypeReference.class))).thenReturn(response);

        Map<String, Object> result = discordActionService.executeDiscordAction(
            "send_message", new HashMap<>(), params, TEST_USER_ID);

        assertTrue((Boolean) result.get("success"));
    }

    @Test
    void testCreateChannel_WithTopic() {
        setupOAuthToken();
        Map<String, Object> params = new HashMap<>();
        params.put("guild_id", "guild123");
        params.put("name", "test-channel");
        params.put("topic", "Test topic");
        params.put("type", 0);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("id", "channel123");
        ResponseEntity<Map<String, Object>> response = new ResponseEntity<>(responseBody, HttpStatus.OK);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
            any(ParameterizedTypeReference.class))).thenReturn(response);

        Map<String, Object> result = discordActionService.executeDiscordAction(
            "create_channel", new HashMap<>(), params, TEST_USER_ID);

        assertTrue((Boolean) result.get("success"));
    }

    @Test
    void testBanMember_WithCustomDays() {
        setupOAuthToken();
        Map<String, Object> params = new HashMap<>();
        params.put("guild_id", "guild123");
        params.put("user_id", "user123");
        params.put("reason", "Bad behavior");
        params.put("delete_message_days", 7);

        ResponseEntity<Void> response = new ResponseEntity<>(HttpStatus.NO_CONTENT);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(Void.class)))
            .thenReturn(response);

        Map<String, Object> result = discordActionService.executeDiscordAction(
            "ban_member", new HashMap<>(), params, TEST_USER_ID);

        assertTrue((Boolean) result.get("success"));
    }

    @Test
    void testKickMember_WithReason() {
        setupOAuthToken();
        Map<String, Object> params = new HashMap<>();
        params.put("guild_id", "guild123");
        params.put("user_id", "user123");
        params.put("reason", "Custom reason");

        ResponseEntity<Void> response = new ResponseEntity<>(HttpStatus.NO_CONTENT);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(Void.class)))
            .thenReturn(response);

        Map<String, Object> result = discordActionService.executeDiscordAction(
            "kick_member", new HashMap<>(), params, TEST_USER_ID);

        assertTrue((Boolean) result.get("success"));
    }

    @Test
    void testCheckNewMembers_NoGuildId() {
        setupOAuthToken();
        Map<String, Object> params = new HashMap<>();

        List<Map<String, Object>> result = discordActionService.checkDiscordEvents(
            "new_member", params, TEST_USER_ID, LocalDateTime.now());

        assertTrue(result.isEmpty());
    }

    @Test
    void testSendDirectMessage_FailedToCreateDM() {
        setupOAuthToken();
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", "user123");
        params.put("content", "DM content");

        ResponseEntity<Map<String, Object>> dmResponse = new ResponseEntity<>(HttpStatus.BAD_REQUEST);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
            any(ParameterizedTypeReference.class))).thenReturn(dmResponse);

        assertThrows(RuntimeException.class, () -> {
            discordActionService.executeDiscordAction("send_dm", new HashMap<>(), params, TEST_USER_ID);
        });
    }

    // ==================== Helper Methods ====================

    private void setupOAuthToken() {
        UserOAuthIdentity identity = new UserOAuthIdentity();
        identity.setAccessTokenEnc("encrypted_token");
        identity.setExpiresAt(LocalDateTime.now().plusHours(1));

        when(userOAuthIdentityRepository.findByUserIdAndProvider(TEST_USER_ID, "discord"))
            .thenReturn(Optional.of(identity));
        when(oauthTokenRefreshService.needsRefresh(identity)).thenReturn(false);
        when(tokenEncryptionService.decryptToken("encrypted_token")).thenReturn(TEST_TOKEN);
    }

    private List<Map<String, Object>> createMockMessages() {
        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> message = new HashMap<>();
        message.put("id", "msg123");
        message.put("content", "Hello world");
        message.put("timestamp", ZonedDateTime.now().toString());
        Map<String, Object> author = new HashMap<>();
        author.put("id", "author123");
        author.put("username", "TestUser");
        author.put("bot", false);
        message.put("author", author);
        messages.add(message);
        return messages;
    }

    private List<Map<String, Object>> createMockMembers() {
        List<Map<String, Object>> members = new ArrayList<>();
        Map<String, Object> member = new HashMap<>();
        member.put("joined_at", ZonedDateTime.now().toString());
        Map<String, Object> user = new HashMap<>();
        user.put("id", "user123");
        user.put("username", "NewMember");
        member.put("user", user);
        members.add(member);
        return members;
    }

    private List<Map<String, Object>> createMockChannels() {
        List<Map<String, Object>> channels = new ArrayList<>();
        Map<String, Object> channel = new HashMap<>();
        channel.put("id", "channel123");
        channel.put("type", 0);
        channel.put("name", "general");
        channels.add(channel);
        return channels;
    }
}
