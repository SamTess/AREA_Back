package area.server.AREA_Back.service.Area.Services;

import area.server.AREA_Back.entity.UserOAuthIdentity;
import area.server.AREA_Back.repository.UserOAuthIdentityRepository;
import area.server.AREA_Back.service.Auth.TokenEncryptionService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
import java.time.format.DateTimeParseException;
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
    private static final int SECONDS_IN_DAY = 86400;
    private static final int MAX_MEMBERS_LIMIT = 1000;
    private static final int MAX_AUTH_LOG_LENGTH = 20;
    private static final int CHANNEL_CHECK_DELAY_MS = 100;

    private final UserOAuthIdentityRepository userOAuthIdentityRepository;
    private final TokenEncryptionService tokenEncryptionService;
    private final RestTemplate restTemplate;
    private final MeterRegistry meterRegistry;

    @Value("${DISCORD_BOT_TOKEN:#{null}}")
    private String discordBotToken;

    private Counter discordActionsExecuted;
    private Counter discordActionsFailed;

    @PostConstruct
    public void init() {
        discordActionsExecuted = meterRegistry.counter("discord_actions_executed_total");
        discordActionsFailed = meterRegistry.counter("discord_actions_failed_total");
    }

    private LocalDateTime parseDiscordTimestamp(String timestampStr) {
        if (timestampStr == null || timestampStr.isEmpty()) {
            return null;
        }

        try {
            ZonedDateTime zonedDateTime = ZonedDateTime.parse(timestampStr,
                DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            return zonedDateTime.toLocalDateTime();
        } catch (DateTimeParseException e) {
            log.warn("Failed to parse Discord timestamp '{}': {}", timestampStr, e.getMessage());
            try {
                return LocalDateTime.parse(timestampStr, DateTimeFormatter.ISO_DATE_TIME);
            } catch (DateTimeParseException ex) {
                log.error("Failed to parse Discord timestamp with fallback: '{}'", timestampStr, ex);
                return null;
            }
        }
    }

    public Map<String, Object> executeDiscordAction(String actionKey,
                                                    Map<String, Object> inputPayload,
                                                    Map<String, Object> actionParams,
                                                    UUID userId) {
        try {
            discordActionsExecuted.increment();

            String token;
            if (requiresBotToken(actionKey)) {
                if (discordBotToken == null || discordBotToken.trim().isEmpty()) {
                    log.error("Discord bot token not configured for action: {} "
                        + "(DISCORD_BOT_TOKEN env var missing)", actionKey);
                    throw new RuntimeException("Discord bot token not configured for action: " + actionKey);
                }
                token = discordBotToken;
                log.info("Using bot token for Discord action: {} (token configured: {})",
                    actionKey, discordBotToken != null && !discordBotToken.isEmpty());
            } else {
                token = getDiscordToken(userId);
                if (token == null) {
                    throw new RuntimeException("No Discord token found for user: " + userId);
                }
                log.debug("Using user OAuth token for Discord action: {}", actionKey);
            }

            switch (actionKey) {
                case "send_message":
                    return sendMessage(token, inputPayload, actionParams);
                case "create_channel":
                    return createChannel(token, inputPayload, actionParams);
                case "add_reaction":
                    return addReaction(token, inputPayload, actionParams);
                case "send_dm":
                    return sendDirectMessage(token, inputPayload, actionParams);
                case "add_role":
                    return addRoleToMember(token, inputPayload, actionParams);
                case "remove_role":
                    return removeRoleFromMember(token, inputPayload, actionParams);
                case "delete_message":
                    return deleteMessage(token, inputPayload, actionParams);
                case "pin_message":
                    return pinMessage(token, inputPayload, actionParams);
                case "kick_member":
                    return kickMember(token, inputPayload, actionParams);
                case "ban_member":
                    return banMember(token, inputPayload, actionParams);
                default:
                    throw new IllegalArgumentException("Unknown Discord action: " + actionKey);
            }
        } catch (Exception e) {
            discordActionsFailed.increment();
            log.error("Failed to execute Discord action {}: {}", actionKey, e.getMessage(), e);
            throw new RuntimeException("Discord action execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Determines if an action requires bot token instead of user OAuth token
     */
    private boolean requiresBotToken(String actionKey) {
        return actionKey.equals("send_message")
            || actionKey.equals("add_role")
            || actionKey.equals("remove_role")
            || actionKey.equals("add_reaction")
            || actionKey.equals("delete_message")
            || actionKey.equals("pin_message")
            || actionKey.equals("kick_member")
            || actionKey.equals("ban_member");
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
        Object messageId = null;
        if (response.getBody() != null) {
            messageId = response.getBody().get("id");
        }
        result.put("message_id", messageId);
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
        Object channelIdResult = null;
        if (response.getBody() != null) {
            channelIdResult = response.getBody().get("id");
        }
        result.put("channel_id", channelIdResult);
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
        Object messageIdResult = null;
        if (messageResponse.getBody() != null) {
            messageIdResult = messageResponse.getBody().get("id");
        }
        result.put("message_id", messageIdResult);
        return result;
    }

    private Map<String, Object> addRoleToMember(String token, Map<String, Object> input,
                                                 Map<String, Object> params) {
        String guildId = getRequiredParam(params, "guild_id", String.class);
        String userId = getRequiredParam(params, "user_id", String.class);
        String roleId = getRequiredParam(params, "role_id", String.class);

        String url = String.format("%s/guilds/%s/members/%s/roles/%s",
            DISCORD_API_BASE, guildId, userId, roleId);

        HttpHeaders headers = createDiscordHeaders(token);
        headers.setContentLength(0);
        headers.set("X-Audit-Log-Reason", "AREA automation: add_role action");

        boolean isBotToken = token != null && token.equals(discordBotToken);
        log.info("Adding role: guild={}, user={}, role={}, usingBotToken={}",
            guildId, userId, roleId, isBotToken);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<Void> response = restTemplate.exchange(
                url, HttpMethod.PUT, request, Void.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Successfully added role {} to user {} in guild {} (status: {})",
                    roleId, userId, guildId, response.getStatusCode());
            } else {
                throw new RuntimeException("Failed to add role to Discord member: " + response.getStatusCode());
            }

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("guild_id", guildId);
            result.put("user_id", userId);
            result.put("role_id", roleId);
            return result;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("Discord API error adding role (guild={}, user={}, role={}): {} - {}",
                guildId, userId, roleId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Failed to add role to Discord member: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to add role to Discord member (guild={}, user={}, role={}): {}",
                guildId, userId, roleId, e.getMessage());
            throw new RuntimeException("Failed to add role to Discord member: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> removeRoleFromMember(String token, Map<String, Object> input,
                                                      Map<String, Object> params) {
        String guildId = getRequiredParam(params, "guild_id", String.class);
        String userId = getRequiredParam(params, "user_id", String.class);
        String roleId = getRequiredParam(params, "role_id", String.class);

        String url = String.format("%s/guilds/%s/members/%s/roles/%s",
            DISCORD_API_BASE, guildId, userId, roleId);

        HttpHeaders headers = createDiscordHeaders(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<Void> response = restTemplate.exchange(
                url, HttpMethod.DELETE, request, Void.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Successfully removed role {} from user {} in guild {}", roleId, userId, guildId);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("guild_id", guildId);
            result.put("user_id", userId);
            result.put("role_id", roleId);
            return result;
        } catch (Exception e) {
            log.error("Failed to remove role from Discord member: {}", e.getMessage());
            throw new RuntimeException("Failed to remove role from Discord member: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> deleteMessage(String token, Map<String, Object> input,
                                               Map<String, Object> params) {
        String channelId = getRequiredParam(params, "channel_id", String.class);
        String messageId = getRequiredParam(params, "message_id", String.class);

        String url = String.format("%s/channels/%s/messages/%s",
            DISCORD_API_BASE, channelId, messageId);

        HttpHeaders headers = createDiscordHeaders(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<Void> response = restTemplate.exchange(
                url, HttpMethod.DELETE, request, Void.class
            );

            log.info("Successfully deleted message {} in channel {}", messageId, channelId);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("channel_id", channelId);
            result.put("message_id", messageId);
            return result;
        } catch (Exception e) {
            log.error("Failed to delete Discord message: {}", e.getMessage());
            throw new RuntimeException("Failed to delete Discord message: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> pinMessage(String token, Map<String, Object> input,
                                            Map<String, Object> params) {
        String channelId = getRequiredParam(params, "channel_id", String.class);
        String messageId = getRequiredParam(params, "message_id", String.class);

        String url = String.format("%s/channels/%s/pins/%s",
            DISCORD_API_BASE, channelId, messageId);

        HttpHeaders headers = createDiscordHeaders(token);
        headers.setContentLength(0);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<Void> response = restTemplate.exchange(
                url, HttpMethod.PUT, request, Void.class
            );

            log.info("Successfully pinned message {} in channel {}", messageId, channelId);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("channel_id", channelId);
            result.put("message_id", messageId);
            return result;
        } catch (Exception e) {
            log.error("Failed to pin Discord message: {}", e.getMessage());
            throw new RuntimeException("Failed to pin Discord message: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> kickMember(String token, Map<String, Object> input,
                                            Map<String, Object> params) {
        String guildId = getRequiredParam(params, "guild_id", String.class);
        String userId = getRequiredParam(params, "user_id", String.class);
        String reason = getOptionalParam(params, "reason", String.class, "Kicked by AREA automation");

        String url = String.format("%s/guilds/%s/members/%s",
            DISCORD_API_BASE, guildId, userId);

        HttpHeaders headers = createDiscordHeaders(token);
        headers.set("X-Audit-Log-Reason", reason);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<Void> response = restTemplate.exchange(
                url, HttpMethod.DELETE, request, Void.class
            );

            log.info("Successfully kicked user {} from guild {}", userId, guildId);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("guild_id", guildId);
            result.put("user_id", userId);
            return result;
        } catch (Exception e) {
            log.error("Failed to kick Discord member: {}", e.getMessage());
            throw new RuntimeException("Failed to kick Discord member: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> banMember(String token, Map<String, Object> input,
                                           Map<String, Object> params) {
        String guildId = getRequiredParam(params, "guild_id", String.class);
        String userId = getRequiredParam(params, "user_id", String.class);
        String reason = getOptionalParam(params, "reason", String.class, "Banned by AREA automation");
        Integer deleteMessageDays = getOptionalParam(params, "delete_message_days", Integer.class, 0);

        String url = String.format("%s/guilds/%s/bans/%s",
            DISCORD_API_BASE, guildId, userId);

        HttpHeaders headers = createDiscordHeaders(token);
        headers.set("X-Audit-Log-Reason", reason);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("delete_message_seconds", deleteMessageDays * SECONDS_IN_DAY);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Void> response = restTemplate.exchange(
                url, HttpMethod.PUT, request, Void.class
            );

            log.info("Successfully banned user {} from guild {}", userId, guildId);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("guild_id", guildId);
            result.put("user_id", userId);
            return result;
        } catch (Exception e) {
            log.error("Failed to ban Discord member: {}", e.getMessage());
            throw new RuntimeException("Failed to ban Discord member: " + e.getMessage(), e);
        }
    }

    private List<Map<String, Object>> checkNewMessages(String token, Map<String, Object> params,
                                                        LocalDateTime lastCheck) {
        String channelId = getOptionalParam(params, "channel_id", String.class, null);
        String guildId = getOptionalParam(params, "guild_id", String.class, null);
        String containsText = getOptionalParam(params, "contains_text", String.class, null);

        List<Map<String, Object>> allNewMessages = new ArrayList<>();

        try {
            if (channelId != null) {
                List<Map<String, Object>> messages = fetchMessagesFromChannel(token, channelId,
                    lastCheck, containsText);
                allNewMessages.addAll(messages);
            } else if (guildId != null) {
                List<String> channelIds = getGuildTextChannels(token, guildId);
                for (String chId : channelIds) {
                    try {
                        List<Map<String, Object>> messages = fetchMessagesFromChannel(token, chId,
                            lastCheck, containsText);
                        allNewMessages.addAll(messages);
                        Thread.sleep(CHANNEL_CHECK_DELAY_MS);
                    } catch (Exception e) {
                        log.warn("Failed to check messages in channel {}: {}", chId, e.getMessage());
                    }
                }
            } else {
                log.warn("Either channel_id or guild_id must be provided for Discord message checking");
                return Collections.emptyList();
            }

            allNewMessages.sort((a, b) -> {
                String timeA = (String) a.get("timestamp");
                String timeB = (String) b.get("timestamp");
                if (timeA == null || timeB == null) {
                    return 0;
                }
                return timeA.compareTo(timeB);
            });

            log.debug("Found {} new Discord messages", allNewMessages.size());
            return allNewMessages;

        } catch (Exception e) {
            log.error("Failed to check new Discord messages: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> fetchMessagesFromChannel(String token, String channelId,
                                                              LocalDateTime lastCheck, String containsText) {
        String url = String.format("%s/channels/%s/messages?limit=50", DISCORD_API_BASE, channelId);

        HttpHeaders headers = createDiscordHeaders(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                url, HttpMethod.GET, request,
                new ParameterizedTypeReference<List<Map<String, Object>>>() { }
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("Failed to fetch messages from channel {}: {}", channelId, response.getStatusCode());
                return Collections.emptyList();
            }

            List<Map<String, Object>> newMessages = new ArrayList<>();
            for (Map<String, Object> message : response.getBody()) {
                String timestampStr = (String) message.get("timestamp");
                String content = (String) message.get("content");

                @SuppressWarnings("unchecked")
                Map<String, Object> author = (Map<String, Object>) message.get("author");
                boolean isBot = false;
                if (author != null) {
                    Object bot = author.get("bot");
                    isBot = bot != null && Boolean.TRUE.equals(bot);
                }

                if (timestampStr != null && !isBot) {
                    LocalDateTime messageTime = parseDiscordTimestamp(timestampStr);
                    if (messageTime != null && messageTime.isAfter(lastCheck)) {
                        if (containsText == null
                            || containsText.isEmpty()
                            || (content != null && content.toLowerCase().contains(containsText.toLowerCase()))) {

                            Map<String, Object> event = new HashMap<>();
                            event.put("message_id", message.get("id"));
                            event.put("content", content);
                            event.put("author", author);
                            event.put("timestamp", timestampStr);
                            event.put("channel_id", channelId);
                            newMessages.add(event);
                        }
                    }
                }
            }

            return newMessages;
        } catch (Exception e) {
            log.error("Failed to fetch messages from channel {}: {}", channelId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private List<String> getGuildTextChannels(String token, String guildId) {
        String url = String.format("%s/guilds/%s/channels", DISCORD_API_BASE, guildId);

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

            List<String> textChannelIds = new ArrayList<>();
            for (Map<String, Object> channel : response.getBody()) {
                Integer type = (Integer) channel.get("type");
                if (type != null && type == 0) {
                    String channelId = (String) channel.get("id");
                    if (channelId != null) {
                        textChannelIds.add(channelId);
                    }
                }
            }

            return textChannelIds;
        } catch (Exception e) {
            log.error("Failed to get guild channels for guild {}: {}", guildId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> checkNewMembers(String token, Map<String, Object> params,
                                                       LocalDateTime lastCheck) {
        String guildId = getOptionalParam(params, "guild_id", String.class, null);

        if (guildId == null) {
            log.warn("guild_id is required for checking new Discord members");
            return Collections.emptyList();
        }

        String url = String.format("%s/guilds/%s/members?limit=%d", DISCORD_API_BASE, guildId, MAX_MEMBERS_LIMIT);

        HttpHeaders headers = createDiscordHeaders(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                url, HttpMethod.GET, request,
                new ParameterizedTypeReference<List<Map<String, Object>>>() { }
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("Failed to fetch guild members for guild {}: {}", guildId, response.getStatusCode());
                return Collections.emptyList();
            }

            List<Map<String, Object>> newMembers = new ArrayList<>();
            for (Map<String, Object> member : response.getBody()) {
                String joinedAtStr = (String) member.get("joined_at");
                if (joinedAtStr != null) {
                    LocalDateTime joinedAt = parseDiscordTimestamp(joinedAtStr);
                    if (joinedAt != null && joinedAt.isAfter(lastCheck)) {
                        Map<String, Object> event = new HashMap<>();
                        event.put("user", member.get("user"));
                        event.put("joined_at", joinedAtStr);
                        event.put("guild_id", guildId);
                        newMembers.add(event);
                    }
                }
            }

            log.debug("Found {} new Discord members in guild {}", newMembers.size(), guildId);
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

        UserOAuthIdentity identity = oauthIdentity.get();
        String encryptedToken = identity.getAccessTokenEnc();
        if (encryptedToken == null) {
            log.warn("No Discord access token found for user: {}", userId);
            return null;
        }

        if (identity.getExpiresAt() != null && identity.getExpiresAt().isBefore(LocalDateTime.now())) {
            log.warn("Discord access token expired for user: {}", userId);
            return null;
        }

        try {
            return tokenEncryptionService.decryptToken(encryptedToken);
        } catch (Exception e) {
            log.error("Failed to decrypt Discord token for user {}: {}", userId, e.getMessage());
            return null;
        }
    }

    private HttpHeaders createDiscordHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null && discordBotToken != null && token.equals(discordBotToken)) {
            headers.set("Authorization", "Bot " + token);
            log.debug("Using Bot token authorization (token length: {})", token.length());
        } else {
            headers.setBearerAuth(token);
            log.debug("Using Bearer (OAuth) token authorization");
        }
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        String authHeader = headers.getFirst("Authorization");
        if (authHeader != null) {
            log.debug("Authorization header format: {} (length: {})",
                authHeader.substring(0, Math.min(MAX_AUTH_LOG_LENGTH, authHeader.length()))
                    + "...",
                authHeader.length());
        }

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
