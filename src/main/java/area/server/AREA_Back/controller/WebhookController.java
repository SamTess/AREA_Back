package area.server.AREA_Back.controller;

import area.server.AREA_Back.service.GitHubWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Controller for handling GitHub webhooks
 */
@RestController
@RequestMapping("/api/webhooks")
@Tag(name = "Webhooks", description = "API for handling external service webhooks")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final GitHubWebhookService gitHubWebhookService;

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

    @PostMapping("/github/test/{userId}")
    @Operation(summary = "Test GitHub webhook endpoint",
               description = "Test endpoint for GitHub webhook validation")
    public ResponseEntity<Map<String, Object>> testGitHubWebhook(
            @PathVariable UUID userId,
            @RequestBody(required = false) Map<String, Object> payload) {

        log.info("Test GitHub webhook called for user {}", userId);

        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "Webhook endpoint is working",
            "userId", userId.toString(),
            "timestamp", java.time.LocalDateTime.now().toString()
        ));
    }
}