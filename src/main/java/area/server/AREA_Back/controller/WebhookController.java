package area.server.AREA_Back.controller;

import area.server.AREA_Back.entity.Execution;
import area.server.AREA_Back.service.GitHubWebhookService;
import area.server.AREA_Back.service.WebhookDeduplicationService;
import area.server.AREA_Back.service.WebhookEventProcessingService;
import area.server.AREA_Back.service.WebhookSignatureValidator;
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
import java.time.LocalDateTime;
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

    private final GitHubWebhookService gitHubWebhookService;
    private final WebhookSignatureValidator signatureValidator;
    private final WebhookDeduplicationService deduplicationService;
    private final WebhookEventProcessingService eventProcessingService;

    /**
     * Generic webhook receiver endpoint
     * POST /api/hooks/{service}/{action}
     */
    @PostMapping("/{service}/{action}")
    @Operation(summary = "Handle webhook events from external services",
               description = "Receives and processes webhook events with signature validation and deduplication")
    public ResponseEntity<Map<String, Object>> handleWebhook(
            @Parameter(description = "Service name (github, slack, etc.)")
            @PathVariable String service,
            @Parameter(description = "Action type (issues, pull_request, message, etc.)")
            @PathVariable String action,
            @Parameter(description = "Optional user ID to scope the webhook")
            @RequestParam(required = false) UUID userId,
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request) {

        long startTime = System.currentTimeMillis();
        String eventId = extractEventId(service, request, payload);
        log.info("Received webhook: service={}, action={}, eventId={}, userId={}",
                service, action, eventId, userId);

        try {
            if (!validateSignature(service, request, payload)) {
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

            // Process webhook event
            List<Execution> executions;
            if (userId != null) {
                executions = eventProcessingService.processWebhookEventForUser(service, action, payload, userId);
            } else {
                executions = eventProcessingService.processWebhookEventGlobally(service, action, payload);
            }

            long processingTime = System.currentTimeMillis() - startTime;
            log.info("Webhook processed successfully: service={}, action={}, executions={}, time={}ms", 
                    service, action, executions.size(), processingTime);

            return ResponseEntity.ok(Map.of(
                "status", "processed",
                "service", service,
                "action", action,
                "eventId", eventId,
                "executionsCreated", executions.size(),
                "executionIds", executions.stream().map(e -> e.getId().toString()).toList(),
                "processingTimeMs", processingTime,
                "timestamp", LocalDateTime.now().toString()
            ));

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

    /**
     * GitHub-specific webhook endpoint (backward compatibility)
     */
    @PostMapping("/github/{userId}")
    @Operation(summary = "Handle GitHub webhook events",
               description = "Receives and processes GitHub webhook events for a specific user")
    public ResponseEntity<Map<String, Object>> handleGitHubWebhook(
            @Parameter(description = "User ID for which to process the webhook")
            @PathVariable UUID userId,

            @Parameter(description = "GitHub event type (e.g., issues, pull_request, push)")
            @RequestHeader(value = "X-GitHub-Event", required = false) String eventType,

            @Parameter(description = "GitHub webhook signature for verification")
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,

            @Parameter(description = "GitHub webhook delivery ID")
            @RequestHeader(value = "X-GitHub-Delivery", required = false) String deliveryId,

            @RequestBody Map<String, Object> payload) {

        try {
            log.info("Received GitHub webhook for user {}, event type: {}, delivery: {}",
                    userId, eventType, deliveryId);

            Map<String, Object> result = gitHubWebhookService.processWebhook(
                userId, eventType, signature, payload
            );

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Failed to process GitHub webhook for user {}: {}", userId, e.getMessage(), e);

            return ResponseEntity.badRequest().body(Map.of(
                "error", "Webhook processing failed",
                "message", e.getMessage(),
                "userId", userId.toString(),
                "eventType", eventType != null ? eventType : "unknown"
            ));
        }
    }

    /**
     * Test endpoint for webhook validation
     */
    @PostMapping("/test/{service}")
    @Operation(summary = "Test webhook endpoint",
               description = "Test endpoint for webhook validation without processing")
    public ResponseEntity<Map<String, Object>> testWebhook(
            @PathVariable String service,
            @RequestParam(required = false) UUID userId,
            @RequestBody(required = false) Map<String, Object> payload,
            HttpServletRequest request) {

        log.info("Test webhook called for service: {}, userId: {}", service, userId);

        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "Webhook endpoint is working",
            "service", service,
            "userId", userId != null ? userId.toString() : "none",
            "headersReceived", getRequestHeaders(request),
            "timestamp", LocalDateTime.now().toString()
        ));
    }

    /**
     * Validates webhook signature based on service provider
     */
    private boolean validateSignature(String service, HttpServletRequest request, Map<String, Object> payload) {
        try {
            byte[] payloadBytes = getRequestBody(request);
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
            case "slack":
                Object eventId = payload.get("event_id");
                if (eventId != null) {
                    return eventId.toString();
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
            default -> request.getHeader("X-Signature");
        };
    }

    /**
     * Gets the timestamp header for the service (if required)
     */
    private String getTimestampHeader(String service, HttpServletRequest request) {
        return switch (service.toLowerCase()) {
            case "slack" -> request.getHeader("X-Slack-Request-Timestamp");
            default -> null;
        };
    }

    /**
     * Gets webhook secret for the service
     */
    private String getWebhookSecret(String service) {
        // Try to get from service account configuration
        // For now, return null to allow unsigned webhooks
        // FIXME: Implement proper secret management
        return null;
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
     * Gets request headers for debugging
     */
    private Map<String, String> getRequestHeaders(HttpServletRequest request) {
        Map<String, String> headers = new java.util.HashMap<>();
        request.getHeaderNames().asIterator()
            .forEachRemaining(name -> headers.put(name, request.getHeader(name)));
        return headers;
    }
}