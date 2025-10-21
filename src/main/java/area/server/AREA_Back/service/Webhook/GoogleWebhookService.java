package area.server.AREA_Back.service.Webhook;

import area.server.AREA_Back.entity.Execution;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.entity.UserOAuthIdentity;
import area.server.AREA_Back.repository.UserOAuthIdentityRepository;
import area.server.AREA_Back.repository.UserRepository;
import area.server.AREA_Back.service.Auth.TokenEncryptionService;
import area.server.AREA_Back.service.Webhook.WebhookEventProcessingService;
import area.server.AREA_Back.service.Webhook.WebhookDeduplicationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for processing Google API webhooks via Pub/Sub
 * Handles Gmail webhook notifications
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleWebhookService {

    private final MeterRegistry meterRegistry;
    private final UserOAuthIdentityRepository userOAuthIdentityRepository;
    private final UserRepository userRepository;
    private final TokenEncryptionService tokenEncryptionService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final WebhookEventProcessingService webhookEventProcessingService;
    private final WebhookDeduplicationService deduplicationService;

    private Counter webhookCounter;
    private Counter gmailEventCounter;
    private Counter webhookProcessingFailures;

    @PostConstruct
    public void initMetrics() {
        webhookCounter = Counter.builder("google.webhook.processed")
                .description("Total number of Google webhooks processed")
                .register(meterRegistry);

        gmailEventCounter = Counter.builder("google.webhook.gmail")
                .description("Total number of Gmail webhook events processed")
                .register(meterRegistry);

        webhookProcessingFailures = Counter.builder("google.webhook.failures")
                .description("Total number of Google webhook processing failures")
                .register(meterRegistry);
    }

    /**
     * Process a Google webhook event from Pub/Sub
     *
     * @param payload The Pub/Sub webhook payload
     * @return Processing result
     */
    public Map<String, Object> processWebhook(Map<String, Object> payload) {
        webhookCounter.increment();
        log.info("Processing Google webhook from Pub/Sub");

        Map<String, Object> result = new HashMap<>();
        result.put("status", "processed");
        result.put("processedAt", LocalDateTime.now().toString());

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> pubSubMessage = (Map<String, Object>) payload.get("message");
            if (pubSubMessage == null) {
                throw new IllegalArgumentException("Missing Pub/Sub message in payload");
            }

            String data = (String) pubSubMessage.get("data");
            if (data == null) {
                throw new IllegalArgumentException("Missing data in Pub/Sub message");
            }

            String decodedData = new String(Base64.getDecoder().decode(data));
            log.debug("Decoded Pub/Sub data: {}", decodedData);

            JsonNode notification = objectMapper.readTree(decodedData);

            String serviceType = determineServiceType(notification);
            result.put("serviceType", serviceType);

            switch (serviceType.toLowerCase()) {
                case "gmail":
                    gmailEventCounter.increment();
                    result.putAll(processGmailEvent(notification));
                    break;
                default:
                    log.warn("Unknown Google service type: {}", serviceType);
                    result.put("warning", "Unknown service type: " + serviceType);
            }

            return result;

        } catch (Exception e) {
            webhookProcessingFailures.increment();
            log.error("Error processing Google webhook: {}", e.getMessage(), e);
            result.put("status", "error");
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * Determine the Google service type from the notification
     */
    private String determineServiceType(JsonNode notification) {
        if (notification.has("emailAddress") || notification.has("historyId")) {
            return "gmail";
        }

        if (notification.has("resourceId") && notification.has("resourceUri")) {
            String resourceUri = notification.get("resourceUri").asText();
            if (resourceUri.contains("drive.google.com")) {
                return "drive";
            }
        }

        if (notification.has("resourceId") && notification.has("resourceUri")) {
            String resourceUri = notification.get("resourceUri").asText();
            if (resourceUri.contains("calendar.google.com")) {
                return "calendar";
            }
        }

        return "unknown";
    }

    /**
     * Process Gmail webhook event
     */
    private Map<String, Object> processGmailEvent(JsonNode notification) {
        log.debug("Processing Gmail webhook event");

        Map<String, Object> result = new HashMap<>();
        result.put("eventType", "gmail");

        String emailAddress = notification.has("emailAddress") ?
            notification.get("emailAddress").asText() : null;
        String historyId = notification.has("historyId") ?
            notification.get("historyId").asText() : null;

        result.put("emailAddress", emailAddress);
        result.put("historyId", historyId);

        if (emailAddress != null) {
            UUID userId = findUserByGmailAddress(emailAddress);

            if (userId != null) {
                UserOAuthIdentity oauthIdentity = userOAuthIdentityRepository
                    .findByUserIdAndProvider(userId, "google")
                    .orElse(null);

                if (oauthIdentity != null) {
                    result.put("userId", userId.toString());

                    String dedupeKey = "gmail_" + userId + "_" + historyId;
                    if (deduplicationService.checkAndMark(dedupeKey, "google")) {
                        log.info("Duplicate Gmail event detected for user {} historyId {}, skipping", userId, historyId);
                        result.put("status", "duplicate");
                        result.put("executionsTriggered", 0);
                        return result;
                    }

                    List<Execution> executions = fetchAndProcessNewGmailMessages(
                        oauthIdentity, historyId, userId);

                    result.put("executionsTriggered", executions.size());
                    log.info("Gmail event processed for user {}: historyId={}, executions={}",
                        userId, historyId, executions.size());
                } else {
                    log.warn("No Google OAuth identity found for user with email: {}", emailAddress);
                    result.put("warning", "OAuth identity not found for user");
                }
            } else {
                log.warn("No user found with Gmail address: {}", emailAddress);
                result.put("warning", "User not found for email address");
            }
        }

        return result;
    }

    /**
     * Find user by Gmail email address
     */
    private UUID findUserByGmailAddress(String emailAddress) {
        return userRepository.findByEmail(emailAddress)
            .map(User::getId)
            .orElse(null);
    }

    /**
     * Fetch and process new Gmail messages (INBOX only, excluding sent)
     */
    private List<Execution> fetchAndProcessNewGmailMessages(UserOAuthIdentity oauth, String historyId, UUID userId) {
        try {
            String accessToken = tokenEncryptionService.decryptToken(oauth.getAccessTokenEnc());
            log.debug("Fetching recent INBOX messages for user {}", userId);

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

            String url = "https://gmail.googleapis.com/gmail/v1/users/me/messages?labelIds=INBOX&maxResults=5&q=-in:sent";
            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode messagesResponse = objectMapper.readTree(response.getBody());

                if (!messagesResponse.has("messages")) {
                    log.debug("No messages found for user {}", userId);
                    return java.util.Collections.emptyList();
                }

                JsonNode messages = messagesResponse.get("messages");
                List<Execution> allExecutions = new ArrayList<>();

                log.info("Found {} recent INBOX messages for user {}", messages.size(), userId);

                for (JsonNode message : messages) {
                    String messageId = message.get("id").asText();
                    String threadId = message.has("threadId") ? message.get("threadId").asText() : messageId;

                    String messageDedupeKey = "gmail_message_" + userId + "_" + messageId;
                    if (deduplicationService.checkAndMark(messageDedupeKey, "google")) {
                        log.info("Message {} already processed for user {}, skipping", messageId, userId);
                        continue;
                    }

                    Map<String, Object> payload = new HashMap<>();
                    payload.put("action", "message_received");
                    payload.put("messageId", messageId);
                    payload.put("threadId", message.has("threadId") ? message.get("threadId").asText() : null);
                    payload.put("historyId", historyId);

                    List<Execution> executions = webhookEventProcessingService
                        .processWebhookEventForUser("google", "gmail_new_email", payload, userId);

                    allExecutions.addAll(executions);

                    if (!allExecutions.isEmpty()) {
                        log.info("Processed first new message {} for user {}, stopping to prevent spam", messageId, userId);
                        break;
                    }
                }

                return allExecutions;
            } else {
                log.error("Failed to fetch Gmail messages: {}", response.getBody());
                return java.util.Collections.emptyList();
            }
        } catch (Exception e) {
            log.error("Error fetching Gmail messages for user {}: {}", userId, e.getMessage());
            return java.util.Collections.emptyList();
        }
    }
}
