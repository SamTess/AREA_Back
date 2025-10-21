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
import java.time.ZoneOffset;
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
public class SlackActionService {

    private static final String SLACK_API_BASE = "https://slack.com/api";
    private static final String SLACK_PROVIDER_KEY = "slack";

    private final UserOAuthIdentityRepository userOAuthIdentityRepository;
    private final TokenEncryptionService tokenEncryptionService;
    private final RestTemplate restTemplate;
    private final MeterRegistry meterRegistry;

    private Counter slackActionsExecuted;
    private Counter slackActionsFailed;

    @PostConstruct
    public void init() {
        slackActionsExecuted = meterRegistry.counter("slack_actions_executed_total");
        slackActionsFailed = meterRegistry.counter("slack_actions_failed_total");
    }

    public Map<String, Object> executeSlackAction(String actionKey,
                                                  Map<String, Object> inputPayload,
                                                  Map<String, Object> actionParams,
                                                  UUID userId) {
        try {
            slackActionsExecuted.increment();

            String slackToken = getSlackToken(userId);
            if (slackToken == null) {
                throw new RuntimeException("No Slack token found for user: " + userId);
            }

            switch (actionKey) {
                case "send_message":
                    return sendMessage(slackToken, inputPayload, actionParams);
                case "create_channel":
                    return createChannel(slackToken, inputPayload, actionParams);
                case "add_reaction":
                    return addReaction(slackToken, inputPayload, actionParams);
                case "pin_message":
                    return pinMessage(slackToken, inputPayload, actionParams);
                case "set_status":
                    return setStatus(slackToken, inputPayload, actionParams);
                case "invite_to_channel":
                    return inviteToChannel(slackToken, inputPayload, actionParams);
                default:
                    throw new IllegalArgumentException("Unknown Slack action: " + actionKey);
            }
        } catch (Exception e) {
            slackActionsFailed.increment();
            log.error("Failed to execute Slack action {}: {}", actionKey, e.getMessage(), e);
            throw new RuntimeException("Slack action execution failed: " + e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> checkSlackEvents(String actionKey,
                                                       Map<String, Object> actionParams,
                                                       UUID userId,
                                                       LocalDateTime lastCheck) {
        try {
            log.debug("Checking Slack events: {} for user: {}", actionKey, userId);

            String slackToken = getSlackToken(userId);
            if (slackToken == null) {
                log.warn("No Slack token found for user: {}", userId);
                return Collections.emptyList();
            }

            switch (actionKey) {
                case "new_message":
                    return checkNewMessages(slackToken, actionParams, lastCheck);
                case "new_channel":
                    return checkNewChannels(slackToken, actionParams, lastCheck);
                case "user_joined":
                    return checkUserJoined(slackToken, actionParams, lastCheck);
                case "reaction_added":
                    return checkReactionAdded(slackToken, actionParams, lastCheck);
                case "file_shared":
                    return checkFileShared(slackToken, actionParams, lastCheck);
                default:
                    log.warn("Unknown Slack event action: {}", actionKey);
                    return Collections.emptyList();
            }
        } catch (Exception e) {
            log.error("Failed to check Slack events {}: {}", actionKey, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private Map<String, Object> sendMessage(String token, Map<String, Object> input, Map<String, Object> params) {
        String channel = getRequiredParam(params, "channel", String.class);
        String text = getRequiredParam(params, "text", String.class);

        String url = String.format("%s/chat.postMessage", SLACK_API_BASE);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("channel", channel);
        requestBody.put("text", text);

        if (params.containsKey("thread_ts")) {
            requestBody.put("thread_ts", params.get("thread_ts"));
        }

        if (params.containsKey("blocks")) {
            requestBody.put("blocks", params.get("blocks"));
        }

        HttpHeaders headers = createSlackHeaders(token);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.POST, request,
            new ParameterizedTypeReference<Map<String, Object>>() { }
        );

        Map<String, Object> responseBody = response.getBody();
        if (responseBody == null || !Boolean.TRUE.equals(responseBody.get("ok"))) {
            String errorMsg = responseBody != null ? String.valueOf(responseBody.get("error")) : "Unknown error";
            throw new RuntimeException("Failed to send Slack message: "
                + errorMsg);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("channel", responseBody.get("channel"));
        result.put("ts", responseBody.get("ts"));
        return result;
    }

    private Map<String, Object> createChannel(String token, Map<String, Object> input, Map<String, Object> params) {
        String name = getRequiredParam(params, "name", String.class);
        Boolean isPrivate = getOptionalParam(params, "is_private", Boolean.class, false);

        String url = String.format("%s/conversations.create", SLACK_API_BASE);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("name", name);
        requestBody.put("is_private", isPrivate);

        HttpHeaders headers = createSlackHeaders(token);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.POST, request,
            new ParameterizedTypeReference<Map<String, Object>>() { }
        );

        Map<String, Object> responseBody = response.getBody();
        if (responseBody == null || !Boolean.TRUE.equals(responseBody.get("ok"))) {
            String errorMsg = responseBody != null ? String.valueOf(responseBody.get("error")) : "Unknown error";
            throw new RuntimeException("Failed to create Slack channel: "
                + errorMsg);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> channel = (Map<String, Object>) responseBody.get("channel");

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        if (channel != null) {
            result.put("channel_id", channel.get("id"));
        }
        result.put("channel_name", name);
        return result;
    }

    private Map<String, Object> addReaction(String token, Map<String, Object> input, Map<String, Object> params) {
        String channel = getRequiredParam(params, "channel", String.class);
        String timestamp = getRequiredParam(params, "timestamp", String.class);
        String emoji = getRequiredParam(params, "emoji", String.class);

        String url = String.format("%s/reactions.add", SLACK_API_BASE);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("channel", channel);
        requestBody.put("timestamp", timestamp);
        requestBody.put("name", emoji.replaceAll(":", ""));

        HttpHeaders headers = createSlackHeaders(token);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.POST, request,
            new ParameterizedTypeReference<Map<String, Object>>() { }
        );

        Map<String, Object> responseBody = response.getBody();
        if (responseBody == null || !Boolean.TRUE.equals(responseBody.get("ok"))) {
            String errorMsg = responseBody != null ? String.valueOf(responseBody.get("error")) : "Unknown error";
            throw new RuntimeException("Failed to add Slack reaction: "
                + errorMsg);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("emoji", emoji);
        return result;
    }

    private Map<String, Object> pinMessage(String token, Map<String, Object> input, Map<String, Object> params) {
        String channel = getRequiredParam(params, "channel", String.class);
        String timestamp = getRequiredParam(params, "timestamp", String.class);

        String url = String.format("%s/pins.add", SLACK_API_BASE);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("channel", channel);
        requestBody.put("timestamp", timestamp);

        HttpHeaders headers = createSlackHeaders(token);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.POST, request,
            new ParameterizedTypeReference<Map<String, Object>>() { }
        );

        Map<String, Object> responseBody = response.getBody();
        if (responseBody == null || !Boolean.TRUE.equals(responseBody.get("ok"))) {
            String errorMsg = responseBody != null ? String.valueOf(responseBody.get("error")) : "Unknown error";
            throw new RuntimeException("Failed to pin Slack message: "
                + errorMsg);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return result;
    }

    private Map<String, Object> setStatus(String token, Map<String, Object> input, Map<String, Object> params) {
        String statusText = getRequiredParam(params, "status_text", String.class);
        String statusEmoji = getOptionalParam(params, "status_emoji", String.class, ":speech_balloon:");

        String url = String.format("%s/users.profile.set", SLACK_API_BASE);

        Map<String, Object> profile = new HashMap<>();
        profile.put("status_text", statusText);
        profile.put("status_emoji", statusEmoji);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("profile", profile);

        HttpHeaders headers = createSlackHeaders(token);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.POST, request,
            new ParameterizedTypeReference<Map<String, Object>>() { }
        );

        Map<String, Object> responseBody = response.getBody();
        if (responseBody == null || !Boolean.TRUE.equals(responseBody.get("ok"))) {
            String errorMsg = responseBody != null ? String.valueOf(responseBody.get("error")) : "Unknown error";
            throw new RuntimeException("Failed to set Slack status: "
                + errorMsg);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("status_text", statusText);
        return result;
    }

    private Map<String, Object> inviteToChannel(String token, Map<String, Object> input, Map<String, Object> params) {
        String channel = getRequiredParam(params, "channel", String.class);
        String users = getRequiredParam(params, "users", String.class);

        String url = String.format("%s/conversations.invite", SLACK_API_BASE);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("channel", channel);
        requestBody.put("users", users);

        HttpHeaders headers = createSlackHeaders(token);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.POST, request,
            new ParameterizedTypeReference<Map<String, Object>>() { }
        );

        Map<String, Object> responseBody = response.getBody();
        if (responseBody == null || !Boolean.TRUE.equals(responseBody.get("ok"))) {
            String errorMsg = responseBody != null ? String.valueOf(responseBody.get("error")) : "Unknown error";
            throw new RuntimeException("Failed to invite to Slack channel: "
                + errorMsg);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("channel", channel);
        return result;
    }

    private List<Map<String, Object>> checkNewMessages(String token, Map<String, Object> params,
                                                        LocalDateTime lastCheck) {
        String channel = getRequiredParam(params, "channel", String.class);

        String url = String.format("%s/conversations.history?channel=%s&oldest=%s",
            SLACK_API_BASE, channel, lastCheck.toEpochSecond(ZoneOffset.UTC));

        HttpHeaders headers = createSlackHeaders(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.GET, request,
            new ParameterizedTypeReference<Map<String, Object>>() { }
        );

        Map<String, Object> responseBody = response.getBody();
        if (responseBody == null || !Boolean.TRUE.equals(responseBody.get("ok"))) {
            String errorMsg = responseBody != null ? String.valueOf(responseBody.get("error")) : "Unknown error";
            log.warn("Failed to fetch Slack messages: {}", errorMsg);
            return Collections.emptyList();
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) responseBody.get("messages");
        if (messages == null) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> events = new ArrayList<>();
        for (Map<String, Object> message : messages) {
            Map<String, Object> event = new HashMap<>();
            event.put("type", "message");
            event.put("channel", channel);
            event.put("text", message.get("text"));
            event.put("user", message.get("user"));
            event.put("ts", message.get("ts"));
            events.add(event);
        }

        return events;
    }

    private List<Map<String, Object>> checkNewChannels(String token, Map<String, Object> params,
                                                        LocalDateTime lastCheck) {
        String url = String.format("%s/conversations.list?types=public_channel,private_channel", SLACK_API_BASE);

        HttpHeaders headers = createSlackHeaders(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.GET, request,
            new ParameterizedTypeReference<Map<String, Object>>() { }
        );

        Map<String, Object> responseBody = response.getBody();
        if (responseBody == null || !Boolean.TRUE.equals(responseBody.get("ok"))) {
            return Collections.emptyList();
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> channels = (List<Map<String, Object>>) responseBody.get("channels");
        if (channels == null) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> events = new ArrayList<>();
        long lastCheckEpoch = lastCheck.toEpochSecond(ZoneOffset.UTC);

        for (Map<String, Object> channel : channels) {
            Object createdObj = channel.get("created");
            if (createdObj instanceof Number) {
                long created = ((Number) createdObj).longValue();
                if (created > lastCheckEpoch) {
                    Map<String, Object> event = new HashMap<>();
                    event.put("type", "channel_created");
                    event.put("channel_id", channel.get("id"));
                    event.put("channel_name", channel.get("name"));
                    event.put("created", created);
                    events.add(event);
                }
            }
        }

        return events;
    }

    private List<Map<String, Object>> checkUserJoined(String token, Map<String, Object> params,
                                                       LocalDateTime lastCheck) {
        String channel = getRequiredParam(params, "channel", String.class);

        String url = String.format("%s/conversations.members?channel=%s", SLACK_API_BASE, channel);

        HttpHeaders headers = createSlackHeaders(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.GET, request,
            new ParameterizedTypeReference<Map<String, Object>>() { }
        );

        Map<String, Object> responseBody = response.getBody();
        if (responseBody == null || !Boolean.TRUE.equals(responseBody.get("ok"))) {
            return Collections.emptyList();
        }

        @SuppressWarnings("unchecked")
        List<String> members = (List<String>) responseBody.get("members");
        if (members == null) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> events = new ArrayList<>();
        for (String userId : members) {
            Map<String, Object> event = new HashMap<>();
            event.put("type", "user_joined");
            event.put("channel", channel);
            event.put("user", userId);
            events.add(event);
        }

        return events;
    }

    private List<Map<String, Object>> checkReactionAdded(String token, Map<String, Object> params,
                                                          LocalDateTime lastCheck) {
        String channel = getRequiredParam(params, "channel", String.class);

        String url = String.format("%s/conversations.history?channel=%s&oldest=%s",
            SLACK_API_BASE, channel, lastCheck.toEpochSecond(ZoneOffset.UTC));

        HttpHeaders headers = createSlackHeaders(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.GET, request,
            new ParameterizedTypeReference<Map<String, Object>>() { }
        );

        Map<String, Object> responseBody = response.getBody();
        if (responseBody == null || !Boolean.TRUE.equals(responseBody.get("ok"))) {
            return Collections.emptyList();
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) responseBody.get("messages");
        if (messages == null) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> events = new ArrayList<>();
        for (Map<String, Object> message : messages) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> reactions = (List<Map<String, Object>>) message.get("reactions");
            if (reactions != null) {
                for (Map<String, Object> reaction : reactions) {
                    Map<String, Object> event = new HashMap<>();
                    event.put("type", "reaction_added");
                    event.put("channel", channel);
                    event.put("message_ts", message.get("ts"));
                    event.put("emoji", reaction.get("name"));
                    event.put("count", reaction.get("count"));
                    events.add(event);
                }
            }
        }

        return events;
    }

    private List<Map<String, Object>> checkFileShared(String token, Map<String, Object> params,
                                                       LocalDateTime lastCheck) {
        String channel = getRequiredParam(params, "channel", String.class);

        String url = String.format("%s/files.list?channel=%s&ts_from=%s",
            SLACK_API_BASE, channel, lastCheck.toEpochSecond(ZoneOffset.UTC));

        HttpHeaders headers = createSlackHeaders(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.GET, request,
            new ParameterizedTypeReference<Map<String, Object>>() { }
        );

        Map<String, Object> responseBody = response.getBody();
        if (responseBody == null || !Boolean.TRUE.equals(responseBody.get("ok"))) {
            return Collections.emptyList();
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> files = (List<Map<String, Object>>) responseBody.get("files");
        if (files == null) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> events = new ArrayList<>();
        for (Map<String, Object> file : files) {
            Map<String, Object> event = new HashMap<>();
            event.put("type", "file_shared");
            event.put("file_id", file.get("id"));
            event.put("file_name", file.get("name"));
            event.put("user", file.get("user"));
            event.put("timestamp", file.get("timestamp"));
            events.add(event);
        }

        return events;
    }

    private String getSlackToken(UUID userId) {
        Optional<UserOAuthIdentity> oauthIdentity = userOAuthIdentityRepository
            .findByUserIdAndProvider(userId, SLACK_PROVIDER_KEY);

        if (oauthIdentity.isEmpty()) {
            log.warn("No Slack OAuth identity found for user: {}", userId);
            return null;
        }

        UserOAuthIdentity identity = oauthIdentity.get();

        Map<String, Object> tokenMeta = identity.getTokenMeta();
        if (tokenMeta != null && tokenMeta.containsKey("bot_access_token_enc")) {
            String encryptedBotToken = tokenMeta.get("bot_access_token_enc").toString();
            if (encryptedBotToken != null && !encryptedBotToken.isEmpty()) {
                log.debug("Using bot token for user: {}", userId);
                return tokenEncryptionService.decryptToken(encryptedBotToken);
            }
        }

        String encryptedToken = identity.getAccessTokenEnc();
        if (encryptedToken == null || encryptedToken.isEmpty()) {
            log.warn("Slack token is empty for user: {}", userId);
            return null;
        }

        log.debug("Using user token for user: {}", userId);
        return tokenEncryptionService.decryptToken(encryptedToken);
    }

    private HttpHeaders createSlackHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    @SuppressWarnings("unchecked")
    private <T> T getRequiredParam(Map<String, Object> params, String key, Class<T> type) {
        Object value = params.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Required parameter '" + key + "' is missing");
        }
        if (!type.isInstance(value)) {
            throw new IllegalArgumentException("Parameter '" + key + "' must be of type " + type.getSimpleName());
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
            log.warn("Parameter '{}' is not of type {}, using default value", key, type.getSimpleName());
            return defaultValue;
        }
        return (T) value;
    }
}
