package area.server.AREA_Back.service.Webhook;

import area.server.AREA_Back.entity.UserOAuthIdentity;
import area.server.AREA_Back.repository.UserOAuthIdentityRepository;
import area.server.AREA_Back.service.Auth.TokenEncryptionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing Google API watch channels
 * Handles starting and stopping watch channels for Gmail
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleWatchService {

    private final MeterRegistry meterRegistry;
    private final UserOAuthIdentityRepository userOAuthIdentityRepository;
    private final TokenEncryptionService tokenEncryptionService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${GOOGLE_PUBSUB_TOPIC_GMAIL:projects/${GOOGLE_CLOUD_PROJECT}/topics/gmail-watch}")
    private String gmailTopicName;

    @Value("${GOOGLE_WEBHOOK_BASE_URL:http://localhost:8080}")
    private String webhookBaseUrl;

    private Counter watchStartCounter;
    private Counter watchStopCounter;
    private Counter watchFailures;

    @PostConstruct
    public void initMetrics() {
        watchStartCounter = Counter.builder("google.watch.start")
                .description("Total number of Google watch channels started")
                .register(meterRegistry);

        watchStopCounter = Counter.builder("google.watch.stop")
                .description("Total number of Google watch channels stopped")
                .register(meterRegistry);

        watchFailures = Counter.builder("google.watch.failures")
                .description("Total number of Google watch channel failures")
                .register(meterRegistry);
    }

    /**
     * Start watching Gmail for a user
     *
     * @param userId User ID
     * @param labelIds Gmail label IDs to watch (optional, defaults to INBOX)
     * @return Watch response containing channel details
     */
    public Map<String, Object> startGmailWatch(UUID userId, String... labelIds) {
        watchStartCounter.increment();
        log.info("Starting Gmail watch for user: {}", userId);

        try {
            UserOAuthIdentity oauth = getUserOAuthIdentity(userId);
            String accessToken = tokenEncryptionService.decryptToken(oauth.getAccessTokenEnc());

            String url = "https://gmail.googleapis.com/gmail/v1/users/me/watch";

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("topicName", gmailTopicName);

            String[] labels;
            if (labelIds.length > 0) {
                labels = labelIds;
            } else {
                labels = new String[]{"INBOX"};
            }
            requestBody.put("labelIds", labels);
            requestBody.put("labelFilterAction", "include");

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode responseBody = objectMapper.readTree(response.getBody());
                Map<String, Object> result = new HashMap<>();
                result.put("status", "success");
                result.put("service", "gmail");
                result.put("userId", userId);
                result.put("historyId", responseBody.get("historyId").asText());
                result.put("expiration", responseBody.get("expiration").asText());

                log.info("Gmail watch started successfully for user {}: historyId={}",
                    userId, responseBody.get("historyId").asText());
                return result;
            } else {
                throw new RuntimeException("Failed to start Gmail watch: " + response.getBody());
            }

        } catch (Exception e) {
            watchFailures.increment();
            log.error("Failed to start Gmail watch for user {}: {}", userId, e.getMessage());
            throw new RuntimeException("Failed to start Gmail watch: " + e.getMessage(), e);
        }
    }

    /**
     * Stop Gmail watch channel
     *
     * @param userId User ID
     * @return Stop response
     */
    public Map<String, Object> stopGmailWatch(UUID userId) {
        watchStopCounter.increment();
        log.info("Stopping Gmail watch for user {}", userId);

        try {
            UserOAuthIdentity oauth = getUserOAuthIdentity(userId);
            String accessToken = tokenEncryptionService.decryptToken(oauth.getAccessTokenEnc());

            String url = "https://gmail.googleapis.com/gmail/v1/users/me/stop";

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            HttpEntity<?> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> result = new HashMap<>();
                result.put("status", "success");
                result.put("service", "gmail");
                result.put("userId", userId);

                log.info("Gmail watch stopped successfully for user {}", userId);
                return result;
            } else {
                throw new RuntimeException("Failed to stop Gmail watch: " + response.getBody());
            }

        } catch (Exception e) {
            watchFailures.increment();
            log.error("Failed to stop Gmail watch for user {}: {}", userId, e.getMessage());
            throw new RuntimeException("Failed to stop Gmail watch: " + e.getMessage(), e);
        }
    }

    /**
     * Get OAuth identity for user
     */
    private UserOAuthIdentity getUserOAuthIdentity(UUID userId) {
        Optional<UserOAuthIdentity> oauthOpt = userOAuthIdentityRepository
            .findByUserIdAndProvider(userId, "google");

        if (oauthOpt.isEmpty()) {
            throw new RuntimeException("No Google OAuth identity found for user: " + userId);
        }

        return oauthOpt.get();
    }

    /**
     * Refresh Gmail watch channel
     *
     * @param userId User ID
     * @param labelIds Gmail labels (optional)
     * @return New watch response
     */
    public Map<String, Object> refreshGmailWatch(UUID userId, String... labelIds) {
        log.info("Refreshing Gmail watch for user: {}", userId);
        return startGmailWatch(userId, labelIds);
    }
}
