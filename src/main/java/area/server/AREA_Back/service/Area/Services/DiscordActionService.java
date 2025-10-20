package area.server.AREA_Back.service.Area.Services;

import area.server.AREA_Back.entity.UserOAuthIdentity;
import area.server.AREA_Back.repository.UserOAuthIdentityRepository;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiscordActionService {

    private static final String DISCORD_API_BASE = "https://discord.com/api/v10";
    private static final String DISCORD_PROVIDER_KEY = "discord";

    private final UserOAuthIdentityRepository userOAuthIdentityRepository;
    private final TokenEncryptionService tokenEncryptionService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final MeterRegistry meterRegistry;

    private Counter discordActionsExecuted;
    private Counter discordActionsFailed;

    @PostConstruct
    public void init() {
        discordActionsExecuted = meterRegistry.counter("discord_actions_executed_total");
        discordActionsFailed = meterRegistry.counter("discord_actions_failed_total");
    }

    public Map<String, Object> executeDiscordAction(String actionKey,
                                                    Map<String, Object> inputPayload,
                                                    Map<String, Object> actionParams,
                                                    UUID userId) {
        try {
            discordActionsExecuted.increment();

            String discordToken = getDiscordToken(userId);
            if (discordToken == null) {
                throw new RuntimeException("No Discord token found for user: " + userId);
            }

            switch (actionKey) {
                case "send_message":
                    return sendMessage(discordToken, inputPayload, actionParams);
                case "create_channel":
                    return createChannel(discordToken, inputPayload, actionParams);
                case "add_reaction":
                    return addReaction(discordToken, inputPayload, actionParams);
                case "send_dm":
                    return sendDirectMessage(discordToken, inputPayload, actionParams);
                default:
                    throw new IllegalArgumentException("Unknown Discord action: " + actionKey);
            }
        } catch (Exception e) {
            discordActionsFailed.increment();
            log.error("Failed to execute Discord action {}: {}", actionKey, e.getMessage(), e);
            throw new RuntimeException("Discord action execution failed: " + e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> checkDiscordEvents(String actionKey,
                                                         Map<String, Object> actionParams,
                                                         UUID userId,
                                                         LocalDateTime lastCheck) {
        try {
            log.debug("Checking Discord events: {} for user: {}", actionKey, userId);

            String discordToken = getDiscordToken(userId);
            if (discordToken == null) {
                log.warn("No Discord token found for user: {}", userId);
                return Collections.emptyList();
            }

            switch (actionKey) {
                case "new_message":
                    return checkNewMessages(discordToken, actionParams, lastCheck);
                case "new_member":
                    return checkNewMembers(discordToken, actionParams, lastCheck);
                case "message_reaction":
                    return checkMessageReactions(discordToken, actionParams, lastCheck);
                default:
                    log.warn("Unknown Discord event action: {}", actionKey);
                    return Collections.emptyList();
            }
        } catch (Exception e) {
            log.error("Failed to check Discord events {}: {}", actionKey, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private Map<String, Object> sendMessage(String token, Map<String, Object> input, Map<String, Object> params) {
        String channelId = getRequiredParam(params, "channel_id", String.class);
        String content = getRequiredParam(params, "content", String.class);

        String url = String.format("%s/channels/%s/messages", DISCORD_API_BASE, channelId);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("content", content);

        if (params.containsKey("embed")) {
            requestBody.put("embeds", List.of(params.get("embed")));
        }

        HttpHeaders headers = createDiscordHeaders(token);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.POST, request,
            new ParameterizedTypeReference<Map<String, Object>>() { }
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to send Discord message: " + response.getStatusCode());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message_id", response.getBody() != null ? response.getBody().get("id") : null);
        result.put("channel_id", channelId);
        return result;
    }

    private Map<String, Object> createChannel(String token, Map<String, Object> input, Map<String, Object> params) {
        String guildId = getRequiredParam(params, "guild_id", String.class);
        String name = getRequiredParam(params, "name", String.class);
        Integer type = getOptionalParam(params, "type", Integer.class, 0);

        String url = String.format("%s/guilds/%s/channels", DISCORD_API_BASE, guildId);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("name", name);
        requestBody.put("type", type);

        if (params.containsKey("topic")) {
            requestBody.put("topic", params.get("topic"));
        }

        HttpHeaders headers = createDiscordHeaders(token);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.POST, request,
            new ParameterizedTypeReference<Map<String, Object>>() { }
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to create Discord channel: " + response.getStatusCode());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("channel_id", response.getBody() != null ? response.getBody().get("id") : null);
        result.put("channel_name", name);
        return result;
    }

    private Map<String, Object> addReaction(String token, Map<String, Object> input, Map<String, Object> params) {
        String channelId = getRequiredParam(params, "channel_id", String.class);
        String messageId = getRequiredParam(params, "message_id", String.class);
        String emoji = getRequiredParam(params, "emoji", String.class);

        String url = String.format("%s/channels/%s/messages/%s/reactions/%s/@me",
            DISCORD_API_BASE, channelId, messageId, emoji);

        HttpHeaders headers = createDiscordHeaders(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Void> response = restTemplate.exchange(
            url, HttpMethod.PUT, request, Void.class
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to add Discord reaction: " + response.getStatusCode());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("emoji", emoji);
        result.put("message_id", messageId);
        return result;
    }

    private Map<String, Object> sendDirectMessage(String token, Map<String, Object> input,
                                                   Map<String, Object> params) {
        String userId = getRequiredParam(params, "user_id", String.class);
        String content = getRequiredParam(params, "content", String.class);

        String createDmUrl = String.format("%s/users/@me/channels", DISCORD_API_BASE);
        Map<String, Object> dmRequestBody = new HashMap<>();
        dmRequestBody.put("recipient_id", userId);

        HttpHeaders headers = createDiscordHeaders(token);
        HttpEntity<Map<String, Object>> dmRequest = new HttpEntity<>(dmRequestBody, headers);

        ResponseEntity<Map<String, Object>> dmResponse = restTemplate.exchange(
            createDmUrl, HttpMethod.POST, dmRequest,
            new ParameterizedTypeReference<Map<String, Object>>() { }
        );

        if (!dmResponse.getStatusCode().is2xxSuccessful() || dmResponse.getBody() == null) {
            throw new RuntimeException("Failed to create DM channel");
        }

        String channelId = dmResponse.getBody().get("id").toString();

        String sendMessageUrl = String.format("%s/channels/%s/messages", DISCORD_API_BASE, channelId);
        Map<String, Object> messageRequestBody = new HashMap<>();
        messageRequestBody.put("content", content);

        HttpEntity<Map<String, Object>> messageRequest = new HttpEntity<>(messageRequestBody, headers);

        ResponseEntity<Map<String, Object>> messageResponse = restTemplate.exchange(
            sendMessageUrl, HttpMethod.POST, messageRequest,
            new ParameterizedTypeReference<Map<String, Object>>() { }
        );

        if (!messageResponse.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to send DM: " + messageResponse.getStatusCode());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("user_id", userId);
        result.put("message_id", messageResponse.getBody() != null ? messageResponse.getBody().get("id") : null);
        return result;
    }

    private List<Map<String, Object>> checkNewMessages(String token, Map<String, Object> params,
                                                        LocalDateTime lastCheck) {
        String channelId = getRequiredParam(params, "channel_id", String.class);

        String url = String.format("%s/channels/%s/messages?limit=100", DISCORD_API_BASE, channelId);

        HttpHeaders headers = createDiscordHeaders(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                url, HttpMethod.GET, request,
                new ParameterizedTypeReference<List<Map<String, Object>>>() { }
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return Collections.emptyList();
            }

            List<Map<String, Object>> newMessages = new ArrayList<>();
            for (Map<String, Object> message : response.getBody()) {
                String timestampStr = (String) message.get("timestamp");
                if (timestampStr != null) {
                    LocalDateTime messageTime = LocalDateTime.parse(timestampStr,
                        DateTimeFormatter.ISO_DATE_TIME);
                    if (messageTime.isAfter(lastCheck)) {
                        Map<String, Object> event = new HashMap<>();
                        event.put("message_id", message.get("id"));
                        event.put("content", message.get("content"));
                        event.put("author", message.get("author"));
                        event.put("timestamp", timestampStr);
                        event.put("channel_id", channelId);
                        newMessages.add(event);
                    }
                }
            }

            return newMessages;
        } catch (Exception e) {
            log.error("Failed to check new Discord messages: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> checkNewMembers(String token, Map<String, Object> params,
                                                       LocalDateTime lastCheck) {
        String guildId = getRequiredParam(params, "guild_id", String.class);

        String url = String.format("%s/guilds/%s/members?limit=1000", DISCORD_API_BASE, guildId);

        HttpHeaders headers = createDiscordHeaders(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                url, HttpMethod.GET, request,
                new ParameterizedTypeReference<List<Map<String, Object>>>() { }
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return Collections.emptyList();
            }

            List<Map<String, Object>> newMembers = new ArrayList<>();
            for (Map<String, Object> member : response.getBody()) {
                String joinedAtStr = (String) member.get("joined_at");
                if (joinedAtStr != null) {
                    LocalDateTime joinedAt = LocalDateTime.parse(joinedAtStr,
                        DateTimeFormatter.ISO_DATE_TIME);
                    if (joinedAt.isAfter(lastCheck)) {
                        Map<String, Object> event = new HashMap<>();
                        event.put("user", member.get("user"));
                        event.put("joined_at", joinedAtStr);
                        event.put("guild_id", guildId);
                        newMembers.add(event);
                    }
                }
            }

            return newMembers;
        } catch (Exception e) {
            log.error("Failed to check new Discord members: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> checkMessageReactions(String token, Map<String, Object> params,
                                                             LocalDateTime lastCheck) {
        log.debug("Checking message reactions (webhook-based event)");
        return Collections.emptyList();
    }

    private String getDiscordToken(UUID userId) {
        Optional<UserOAuthIdentity> oauthIdentity = userOAuthIdentityRepository
            .findByUserIdAndProvider(userId, DISCORD_PROVIDER_KEY);

        if (oauthIdentity.isEmpty()) {
            log.warn("No Discord OAuth identity found for user: {}", userId);
            return null;
        }

        String encryptedToken = oauthIdentity.get().getAccessTokenEnc();
        if (encryptedToken == null) {
            log.warn("No Discord access token found for user: {}", userId);
            return null;
        }

        return tokenEncryptionService.decryptToken(encryptedToken);
    }

    private HttpHeaders createDiscordHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    @SuppressWarnings("unchecked")
    private <T> T getRequiredParam(Map<String, Object> params, String key, Class<T> type) {
        Object value = params.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required parameter: " + key);
        }
        if (!type.isInstance(value)) {
            throw new IllegalArgumentException("Parameter " + key + " must be of type " + type.getSimpleName());
        }
        return (T) value;
    }

    @SuppressWarnings("unchecked")
    private <T> T getOptionalParam(Map<String, Object> params, String key, Class<T> type, T defaultValue) {
        Object value = params.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (!type.isInstance(value)) {
            return defaultValue;
        }
        return (T) value;
    }
}
