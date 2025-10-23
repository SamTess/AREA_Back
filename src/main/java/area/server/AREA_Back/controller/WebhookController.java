package area.server.AREA_Back.controller;

import area.server.AREA_Back.entity.Execution;
import area.server.AREA_Back.entity.UserOAuthIdentity;
import area.server.AREA_Back.repository.UserOAuthIdentityRepository;
import area.server.AREA_Back.service.Webhook.GoogleWatchService;
import area.server.AREA_Back.service.Webhook.GoogleWebhookService;
import area.server.AREA_Back.service.Webhook.SlackWebhookService;
import area.server.AREA_Back.service.Webhook.DiscordWebhookService;
import area.server.AREA_Back.service.Webhook.DiscordGatewayService;
import area.server.AREA_Back.service.Webhook.WebhookDeduplicationService;
import area.server.AREA_Back.service.Webhook.WebhookEventProcessingService;
import area.server.AREA_Back.service.Webhook.WebhookSecretService;
import area.server.AREA_Back.service.Webhook.WebhookSignatureValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controller for handling webhooks from external services
 */
@RestController
@RequestMapping("/api/hooks")
@Tag(name = "Webhooks", description = "API for handling external service webhooks")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final GoogleWebhookService googleWebhookService;
    private final GoogleWatchService googleWatchService;
    private final SlackWebhookService slackWebhookService;
    private final DiscordWebhookService discordWebhookService;
    private final DiscordGatewayService discordGatewayService;
    private final WebhookSignatureValidator signatureValidator;
    private final WebhookDeduplicationService deduplicationService;
    private final WebhookEventProcessingService eventProcessingService;
    private final WebhookSecretService webhookSecretService;
    private final UserOAuthIdentityRepository userOAuthIdentityRepository;
    private final ObjectMapper objectMapper;

    /**
     * Generic webhook receiver endpoint
     * POST /api/hooks/{service}/{action}
     */
    @PostMapping("/{service}/{action}")
    @Operation(summary = "Handle webhook events from external services",
               description = "Receives and processes webhook events with signature validation and deduplication")
    public ResponseEntity<Object> handleWebhook(
            @Parameter(description = "Service name (github, slack, etc.)")
            @PathVariable String service,
            @Parameter(description = "Action type (issues, pull_request, message, etc.)")
            @PathVariable String action,
            @Parameter(description = "Optional user ID to scope the webhook")
            @RequestParam(required = false) UUID userId,
            HttpServletRequest request) {

        long startTime = System.currentTimeMillis();

        byte[] rawBody = getRequestBody(request);

        Map<String, Object> payload = parseJsonBody(rawBody);

        String eventId = extractEventId(service, request, payload);
        log.info("Received webhook: service={}, action={}, eventId={}, userId={}",
                service, action, eventId, userId);
        log.info("Webhook payload: {}", payload);
        log.debug("Webhook headers: {}", getRequestHeaders(request));

        try {
            if ("slack".equalsIgnoreCase(service) && "url_verification".equals(payload.get("type"))) {
                Map<String, Object> slackResult = slackWebhookService.processWebhook(payload);
                if (slackResult.containsKey("challenge")) {
                    String challenge = (String) slackResult.get("challenge");
                    log.info("Slack URL verification successful, returning challenge");
                    return ResponseEntity.ok(challenge);
                }
            }

            if (!validateSignature(service, request, rawBody)) {
                log.warn("Webhook signature validation failed for service {} action {}", service, action);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", "Invalid signature",
                    "service", service,
                    "action", action,
                    "timestamp", LocalDateTime.now().toString()
                ));
            }

            if (deduplicationService.checkAndMark(eventId, service)) {
                log.info("Duplicate webhook event detected: {} for service {}", eventId, service);
                return ResponseEntity.ok(Map.of(
                    "status", "duplicate",
                    "message", "Event already processed",
                    "eventId", eventId,
                    "service", service,
                    "action", action,
                    "timestamp", LocalDateTime.now().toString()
                ));
            }

            List<Execution> executions;
            Map<String, Object> serviceResult = new HashMap<>();

            if (userId == null) {
                if ("github".equalsIgnoreCase(service)) {
                    userId = identifyGitHubUser(payload);
                } else if ("google".equalsIgnoreCase(service)) {
                    serviceResult = googleWebhookService.processWebhook(payload);
                    executions = List.of();

                    long processingTime = System.currentTimeMillis() - startTime;
                    log.info("Google webhook processed: action={}, time={}ms", action, processingTime);

                    Map<String, Object> response = new HashMap<>();
                    response.put("status", "processed");
                    response.put("service", service);
                    response.put("action", action);
                    response.put("eventId", eventId);
                    response.put("processingTimeMs", processingTime);
                    response.put("timestamp", LocalDateTime.now().toString());
                    response.put("serviceResult", serviceResult);
                    return ResponseEntity.ok(response);
                } else if ("slack".equalsIgnoreCase(service)) {
                    serviceResult = slackWebhookService.processWebhook(payload);
                    executions = List.of();

                    long processingTime = System.currentTimeMillis() - startTime;
                    log.info("Slack webhook processed: action={}, time={}ms", action, processingTime);

                    Map<String, Object> response = new HashMap<>();
                    response.put("status", "processed");
                    response.put("service", service);
                    response.put("action", action);
                    response.put("eventId", eventId);
                    response.put("processingTimeMs", processingTime);
                    response.put("timestamp", LocalDateTime.now().toString());
                    response.put("serviceResult", serviceResult);
                    return ResponseEntity.ok(response);
                } else if ("discord".equalsIgnoreCase(service)) {
                    String signature = request.getHeader("X-Signature-Ed25519");
                    String timestamp = request.getHeader("X-Signature-Timestamp");
                    serviceResult = discordWebhookService.processWebhook(payload, signature, timestamp);
                    executions = List.of();

                    long processingTime = System.currentTimeMillis() - startTime;
                    log.info("Discord webhook processed: action={}, time={}ms", action, processingTime);

                    Map<String, Object> response = new HashMap<>();
                    response.put("status", "processed");
                    response.put("service", service);
                    response.put("action", action);
                    response.put("eventId", eventId);
                    response.put("processingTimeMs", processingTime);
                    response.put("timestamp", LocalDateTime.now().toString());
                    response.put("serviceResult", serviceResult);
                    return ResponseEntity.ok(response);
                }

                if (userId != null) {
                    log.info("Identified user {} from {} webhook payload", userId, service);
                }
            }

            if (userId != null) {
                executions = eventProcessingService.processWebhookEventForUser(service, action, payload, userId);
            } else {
                log.warn("Could not identify user for {} webhook, skipping processing", service);
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                    "error", "User identification failed",
                    "message", "Could not identify which user this webhook belongs to",
                    "service", service,
                    "action", action,
                    "eventId", eventId,
                    "timestamp", LocalDateTime.now().toString()
                ));
            }

            long processingTime = System.currentTimeMillis() - startTime;
            log.info("Webhook processed successfully: service={}, action={}, executions={}, time={}ms",
                    service, action, executions.size(), processingTime);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "processed");
            response.put("service", service);
            response.put("action", action);
            response.put("eventId", eventId);
            response.put("executionsCreated", executions.size());
            response.put("executionIds", executions.stream().map(e -> e.getId().toString()).toList());
            response.put("processingTimeMs", processingTime);
            response.put("timestamp", LocalDateTime.now().toString());

            if (!serviceResult.isEmpty()) {
                response.put("serviceResult", serviceResult);
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to process webhook: service={}, action={}, error={}",
                     service, action, e.getMessage(), e);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Webhook processing failed",
                "message", e.getMessage(),
                "service", service,
                "action", action,
                "eventId", eventId,
                "timestamp", LocalDateTime.now().toString()
            ));
        }
    }

    @GetMapping("/discord/gateway/status")
    @Operation(summary = "Check Discord Gateway connection status",
               description = "Returns the current status of the Discord Gateway connection")
    public ResponseEntity<Map<String, Object>> getDiscordGatewayStatus() {
        boolean connected = discordGatewayService.isConnected();
        return ResponseEntity.ok(Map.of(
            "service", "discord",
            "gateway_connected", connected,
            "timestamp", LocalDateTime.now().toString()
        ));
    }

    /**
     * Reconnect Discord Gateway
     */
    @PostMapping("/discord/gateway/reconnect")
    @Operation(summary = "Reconnect Discord Gateway",
               description = "Forces a reconnection to the Discord Gateway")
    public ResponseEntity<Map<String, Object>> reconnectDiscordGateway() {
        log.info("Manual Discord Gateway reconnection requested");
        discordGatewayService.disconnect();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
            "status", "reconnection_queued",
            "message", "Discord Gateway reconnection has been queued",
            "timestamp", LocalDateTime.now().toString()
        ));
    }

    /**
     * Handle Discord bot interactions (webhooks)
     */
    @PostMapping("/discord/interactions")
    @Operation(summary = "Handle Discord bot interactions",
               description = "Handles Discord bot webhook events and ping verification")
    public ResponseEntity<String> handleDiscordInteractions(
            @RequestBody String rawBody,
            HttpServletRequest request) {

        log.debug("Received Discord interaction webhook");

        try {
            byte[] rawBodyBytes = rawBody.getBytes(StandardCharsets.UTF_8);
            if (!validateSignature("discord", request, rawBodyBytes)) {
                log.warn("Discord interaction signature validation failed");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("{\"error\": \"Invalid signature\"}");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(rawBody, Map.class);

            Integer type = (Integer) payload.get("type");
            if (type != null && type == 1) {
                log.debug("Responding to Discord ping");
                return ResponseEntity.ok()
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .body("{\"type\":1}");
            }

            return ResponseEntity.ok("{\"type\": 5}");

        } catch (Exception e) {
            log.error("Error processing Discord interaction: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"type\": 4, \"data\": {\"content\": \"Internal server error\"}}");
        }
    }

    /**
     * Start Gmail watch for a user
     */
    @PostMapping("/google/gmail/watch/start")
    @Operation(summary = "Start Gmail watch channel",
               description = "Starts watching Gmail for new emails and changes")
    public ResponseEntity<Map<String, Object>> startGmailWatch(
            @RequestParam String userId,
            @RequestParam(required = false) String[] labelIds) {

        log.info("Starting Gmail watch for user: {}", userId);
        try {
            Map<String, Object> result = googleWatchService.startGmailWatch(UUID.fromString(userId), labelIds);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to start Gmail watch for user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Failed to start Gmail watch",
                "message", e.getMessage(),
                "userId", userId
            ));
        }
    }

    /**
     * Stop Gmail watch channel
     */
    @PostMapping("/google/gmail/watch/stop")
    @Operation(summary = "Stop Gmail watch channel",
               description = "Stops watching Gmail for changes")
    public ResponseEntity<Map<String, Object>> stopGmailWatch(@RequestParam String userId) {

        log.info("Stopping Gmail watch for user: {}", userId);
        try {
            Map<String, Object> result = googleWatchService.stopGmailWatch(UUID.fromString(userId));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to stop Gmail watch for user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Failed to stop Gmail watch",
                "message", e.getMessage(),
                "userId", userId
            ));
        }
    }

    /**
     * Refresh Gmail watch channel
     */
    @PostMapping("/google/gmail/watch/refresh")
    @Operation(summary = "Refresh Gmail watch channel",
               description = "Refreshes an expired Gmail watch channel")
    public ResponseEntity<Map<String, Object>> refreshGmailWatch(
            @RequestParam String userId,
            @RequestParam(required = false) String[] labelIds) {

        log.info("Refreshing Gmail watch for user: {}", userId);
        try {
            String[] labels = labelIds != null ? labelIds : new String[0];
            Map<String, Object> result = googleWatchService.refreshGmailWatch(UUID.fromString(userId), labels);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to refresh Gmail watch for user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Failed to refresh Gmail watch",
                "message", e.getMessage(),
                "userId", userId
            ));
        }
    }

    /**
     * Validates webhook signature based on service provider
     */
    private boolean validateSignature(String service, HttpServletRequest request, byte[] payloadBytes) {
        try {
            String signature = getSignatureHeader(service, request);
            String timestamp = getTimestampHeader(service, request);
            if (signature == null) {
                log.debug("No signature header found for service {}", service);
                return true;
            }

            String secret = getWebhookSecret(service);
            if (secret == null) {
                log.warn("No webhook secret configured for service {}", service);
                return true;
            }

            return signatureValidator.validateSignature(service, payloadBytes, signature, secret, timestamp);
        } catch (Exception e) {
            log.error("Error validating signature for service {}: {}", service, e.getMessage());
            return false;
        }
    }

    /**
     * Extracts event ID from webhook based on service provider
     */
    private String extractEventId(String service, HttpServletRequest request, Map<String, Object> payload) {
        switch (service.toLowerCase()) {
            case "github":
                String deliveryId = request.getHeader("X-GitHub-Delivery");
                if (deliveryId != null) {
                    return deliveryId;
                }
                break;
            case "google":
                @SuppressWarnings("unchecked")
                Map<String, Object> message = (Map<String, Object>) payload.get("message");
                if (message != null) {
                    String messageId = (String) message.get("messageId");
                    if (messageId != null) {
                        return messageId;
                    }
                }
                break;
            case "slack":
                Object eventId = payload.get("event_id");
                if (eventId != null) {
                    return eventId.toString();
                }
                break;
            case "discord":
                Object discordId = payload.get("id");
                if (discordId != null) {
                    return discordId.toString();
                }
                break;
            default:
                log.debug("Unknown service for event ID extraction: {}", service);
                break;
        }
        return service + "_" + System.currentTimeMillis() + "_" + Math.abs(payload.hashCode());
    }

    /**
     * Gets the appropriate signature header for the service
     */
    private String getSignatureHeader(String service, HttpServletRequest request) {
        return switch (service.toLowerCase()) {
            case "github" -> request.getHeader("X-Hub-Signature-256");
            case "slack" -> request.getHeader("X-Slack-Signature");
            case "discord" -> request.getHeader("X-Signature-Ed25519");
            case "google" -> null;
            default -> request.getHeader("X-Signature");
        };
    }

    /**
     * Gets the timestamp header for the service (if required)
     */
    private String getTimestampHeader(String service, HttpServletRequest request) {
        return switch (service.toLowerCase()) {
            case "slack" -> request.getHeader("X-Slack-Request-Timestamp");
            case "discord" -> request.getHeader("X-Signature-Timestamp");
            default -> null;
        };
    }

    /**
     * Gets webhook secret for the service
     */
    private String getWebhookSecret(String service) {
        return webhookSecretService.getServiceSecret(service);
    }

    /**
     * Gets request body as bytes (needed for signature validation)
     */
    private byte[] getRequestBody(HttpServletRequest request) {
        try {
            return request.getInputStream().readAllBytes();
        } catch (IOException e) {
            log.error("Error reading request body: {}", e.getMessage());
            return new byte[0];
        }
    }

    /**
     * Parses JSON body from raw bytes
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonBody(byte[] rawBody) {
        try {
            if (rawBody == null || rawBody.length == 0) {
                return new HashMap<>();
            }
            return objectMapper.readValue(rawBody, Map.class);
        } catch (IOException e) {
            log.error("Error parsing JSON body: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * Gets request headers for debugging
     */
    private Map<String, String> getRequestHeaders(HttpServletRequest request) {
        Map<String, String> headers = new java.util.HashMap<>();
        request.getHeaderNames().asIterator()
            .forEachRemaining(name -> headers.put(name, request.getHeader(name)));
        return headers;
    }

    /**
     * Identify GitHub user from webhook payload based on repository owner
     */
    private UUID identifyGitHubUser(Map<String, Object> payload) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> repository = (Map<String, Object>) payload.get("repository");
            if (repository == null) {
                log.warn("No repository information in GitHub webhook payload");
                return null;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> owner = (Map<String, Object>) repository.get("owner");
            if (owner == null) {
                log.warn("No owner information in GitHub webhook repository");
                return null;
            }

            String githubLogin = (String) owner.get("login");
            if (githubLogin == null) {
                log.warn("No login in GitHub webhook repository owner");
                return null;
            }

            log.debug("Looking up user with GitHub login: {}", githubLogin);

            List<UserOAuthIdentity> githubIdentities = userOAuthIdentityRepository.findByProvider("github");

            for (UserOAuthIdentity identity : githubIdentities) {
                Map<String, Object> tokenMeta = identity.getTokenMeta();
                if (tokenMeta != null && githubLogin.equals(tokenMeta.get("login"))) {
                    log.info("Found GitHub user {} for login {}", identity.getUser().getId(), githubLogin);
                    return identity.getUser().getId();
                }
            }

            log.warn("No user found with GitHub login: {}", githubLogin);
            return null;

        } catch (Exception e) {
            log.error("Error identifying GitHub user from webhook: {}", e.getMessage());
            return null;
        }
    }
}