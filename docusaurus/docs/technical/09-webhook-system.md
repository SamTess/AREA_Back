# Webhook System - Technical Documentation

## Table of Contents
- [Overview](#overview)
- [Architecture](#architecture)
- [Core Components](#core-components)
- [Webhook Flow](#webhook-flow)
- [Signature Validation](#signature-validation)
- [Event Deduplication](#deduplication)
- [Payload Mapping](#payload-mapping)
- [Service Integration](#service-integration)
- [Error Handling](#error-handling)
- [Security](#security)
- [Monitoring](#monitoring)
- [Testing](#testing)

## Overview

The Webhook System provides a robust infrastructure for receiving, validating, and processing real-time events from external services (GitHub, Slack, Discord, etc.). It ensures secure event delivery, prevents duplicate processing, and orchestrates automated workflow executions.

### Key Features

- **Multi-Service Support**: Handle webhooks from GitHub, Slack, Discord, and more
- **Signature Validation**: Cryptographic verification of webhook authenticity
- **Event Deduplication**: Prevent duplicate event processing using Redis
- **Payload Mapping**: Transform webhook payloads to internal action parameters
- **Automatic Orchestration**: Trigger workflows based on incoming events
- **Monitoring**: Prometheus metrics for webhook performance and reliability

## Architecture

### Component Diagram

```
External Service (GitHub/Discord/Slack)
          ↓
    [Webhook Endpoint]
          ↓
[Signature Validator] ← [Webhook Secret Service]
          ↓
[Deduplication Service] ← [Redis Cache]
          ↓
[Event Processing Service]
          ↓
    [Payload Mapper]
          ↓
  [Execution Service] → [Worker Queue]
```

### Request Flow

1. External service sends webhook POST request
2. Controller receives and logs request
3. Signature validation against stored secret
4. Event ID extraction and deduplication check
5. Match webhook to action instances
6. Map payload to action parameters
7. Create and queue execution
8. Return success response

## Core Components

### 1. WebhookController

**Location**: `src/main/java/area/server/AREA_Back/controller/WebhookController.java`

Main entry point for all webhook requests.

#### Endpoints

```java
POST /api/hooks/{service}/{action}
POST /api/hooks/{service}/{action}?userId={uuid}
```

#### Parameters

- `service`: Service identifier (github, slack, discord)
- `action`: Event type (issues, pull_request, message, etc.)
- `userId`: Optional user scoping
- Request Body: JSON payload from external service
- Headers: Service-specific signature headers

#### Example Request

```http
POST /api/hooks/github/issues HTTP/1.1
Host: api.area.com
Content-Type: application/json
X-GitHub-Event: issues
X-Hub-Signature-256: sha256=abc123...
X-GitHub-Delivery: 12345-67890

{
  "action": "opened",
  "issue": {
    "number": 42,
    "title": "Bug report",
    "body": "Issue description",
    "user": {
      "login": "username"
    }
  },
  "repository": {
    "full_name": "owner/repo"
  }
}
```

#### Response Format

**Success (200 OK)**:
```json
{
  "status": "processed",
  "message": "Webhook processed successfully",
  "eventId": "12345-67890",
  "service": "github",
  "action": "issues",
  "executionCount": 2,
  "executionIds": [
    "550e8400-e29b-41d4-a716-446655440000",
    "550e8400-e29b-41d4-a716-446655440001"
  ],
  "processingTimeMs": 145,
  "timestamp": "2024-01-15T10:30:00"
}
```

**Duplicate (200 OK)**:
```json
{
  "status": "duplicate",
  "message": "Event already processed",
  "eventId": "12345-67890",
  "service": "github",
  "action": "issues",
  "timestamp": "2024-01-15T10:30:00"
}
```

**Invalid Signature (401 Unauthorized)**:
```json
{
  "error": "Invalid signature",
  "service": "github",
  "action": "issues",
  "timestamp": "2024-01-15T10:30:00"
}
```

### 2. WebhookSignatureValidator

**Location**: `src/main/java/area/server/AREA_Back/service/Webhook/WebhookSignatureValidator.java`

Validates webhook signatures using service-specific algorithms.

#### Supported Algorithms

| Service | Algorithm | Header |
|---------|-----------|--------|
| GitHub | HMAC-SHA256 | `X-Hub-Signature-256` |
| Slack | HMAC-SHA256 | `X-Slack-Signature` |
| Discord | Ed25519 | `X-Signature-Ed25519` |

#### GitHub Validation

```java
public boolean validateGitHubSignature(String signature, String payload, String secret) {
    try {
        Mac hmac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
        hmac.init(secretKey);
        
        byte[] hash = hmac.doFinal(payload.getBytes());
        String computed = "sha256=" + Hex.encodeHexString(hash);
        
        return MessageDigest.isEqual(
            signature.getBytes(),
            computed.getBytes()
        );
    } catch (Exception e) {
        log.error("GitHub signature validation failed", e);
        return false;
    }
}
```

#### Slack Validation

```java
public boolean validateSlackSignature(String signature, String timestamp, 
                                      String body, String secret) {
    // Check timestamp freshness (max 5 minutes)
    long requestTime = Long.parseLong(timestamp);
    long currentTime = System.currentTimeMillis() / 1000;
    if (Math.abs(currentTime - requestTime) > 300) {
        return false;
    }
    
    // Compute signature
    String baseString = "v0:" + timestamp + ":" + body;
    String computed = "v0=" + hmacSha256(baseString, secret);
    
    return MessageDigest.isEqual(
        signature.getBytes(),
        computed.getBytes()
    );
}
```

#### Discord Validation

```java
public boolean validateDiscordSignature(String signature, String timestamp,
                                       String body, String publicKey) {
    try {
        byte[] message = (timestamp + body).getBytes();
        byte[] signatureBytes = Hex.decodeHex(signature);
        byte[] publicKeyBytes = Hex.decodeHex(publicKey);
        
        return Ed25519.verify(signatureBytes, message, publicKeyBytes);
    } catch (Exception e) {
        log.error("Discord signature validation failed", e);
        return false;
    }
}
```

### 3. WebhookSecretService

**Location**: `src/main/java/area/server/AREA_Back/service/Webhook/WebhookSecretService.java`

Manages webhook secrets for signature validation.

#### Configuration

Secrets are loaded from environment variables:

```properties
app.webhook.github.secret=${GITHUB_WEBHOOK_SECRET}
app.webhook.slack.secret=${SLACK_WEBHOOK_SECRET}
app.webhook.discord.secret=${DISCORD_WEBHOOK_PUBLIC_KEY}
```

#### Secret Retrieval

```java
@Service
@RequiredArgsConstructor
public class WebhookSecretService {
    
    @Value("${app.webhook.github.secret:#{null}}")
    private String githubWebhookSecret;
    
    @Value("${app.webhook.slack.secret:#{null}}")
    private String slackWebhookSecret;
    
    @Value("${app.webhook.discord.secret:#{null}}")
    private String discordWebhookSecret;
    
    private final Map<String, String> secretCache = new HashMap<>();
    
    public String getServiceSecret(String service) {
        if (secretCache.containsKey(service.toLowerCase())) {
            return secretCache.get(service.toLowerCase());
        }
        
        String secret = switch (service.toLowerCase()) {
            case "github" -> githubWebhookSecret;
            case "slack" -> slackWebhookSecret;
            case "discord" -> discordWebhookSecret;
            default -> null;
        };
        
        if (secret != null) {
            secretCache.put(service.toLowerCase(), secret);
        }
        
        return secret;
    }
}
```

### 4. WebhookDeduplicationService

**Location**: `src/main/java/area/server/AREA_Back/service/Webhook/WebhookDeduplicationService.java`

Prevents duplicate webhook processing using Redis.

#### Implementation

```java
@Service
@RequiredArgsConstructor
public class WebhookDeduplicationService {
    
    private final RedisTemplate<String, String> redisTemplate;
    private static final Duration DEDUP_TTL = Duration.ofHours(24);
    
    public boolean checkAndMark(String eventId, String service) {
        if (eventId == null || service == null) {
            return false;
        }
        
        String key = buildKey(service, eventId);
        
        // Try to set key with NX (only if not exists)
        Boolean wasSet = redisTemplate.opsForValue()
            .setIfAbsent(key, "processed", DEDUP_TTL);
        
        // If wasSet is false, key already existed (duplicate)
        return !Boolean.TRUE.equals(wasSet);
    }
    
    public boolean isDuplicate(String service, String eventId) {
        String key = buildKey(service, eventId);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
    
    private String buildKey(String service, String eventId) {
        return String.format("webhook:dedup:%s:%s", service, eventId);
    }
}
```

#### Redis Key Format

```
webhook:dedup:{service}:{eventId}
```

**Examples**:
- `webhook:dedup:github:12345-67890-abcdef`
- `webhook:dedup:slack:1234567890.123456`
- `webhook:dedup:discord:987654321098765432`

#### TTL Strategy

- Default TTL: 24 hours
- Prevents reprocessing of late/retry deliveries
- Automatic cleanup via Redis expiration
- Memory-efficient with automatic purging

### 5. WebhookEventProcessingService

**Location**: `src/main/java/area/server/AREA_Back/service/Webhook/WebhookEventProcessingService.java`

Processes validated webhook events and triggers workflow executions.

#### Main Methods

```java
@Service
@RequiredArgsConstructor
public class WebhookEventProcessingService {
    
    /**
     * Process webhook for specific user
     */
    public List<Execution> processWebhookEventForUser(
        String service,
        String action,
        Map<String, Object> payload,
        UUID userId
    ) {
        List<ActionInstance> matchingInstances = 
            findMatchingActionInstances(service, action, userId);
        
        return matchingInstances.stream()
            .map(instance -> createExecutionForWebhook(instance, payload))
            .filter(Objects::nonNull)
            .toList();
    }
    
    /**
     * Process webhook globally (all users)
     */
    public List<Execution> processWebhookEventGlobally(
        String service,
        String action,
        Map<String, Object> payload
    ) {
        return processWebhookEvent(service, action, payload, null);
    }
}
```

#### Action Matching

The service matches incoming webhooks to action instances:

```java
private boolean matchesActionType(ActionInstance instance, String action) {
    String actionKey = instance.getActionDefinition().getKey();
    String serviceKey = instance.getActionDefinition().getService().getKey();
    
    // Exact match
    if (actionKey.equals(action)) {
        return true;
    }
    
    // Service-specific matching
    return switch (serviceKey) {
        case "github" -> matchesGitHubAction(actionKey, action);
        case "slack" -> matchesSlackAction(actionKey, action);
        case "discord" -> matchesDiscordAction(actionKey, action);
        default -> actionKey.contains(action) || action.contains(actionKey);
    };
}

private boolean matchesGitHubAction(String actionKey, String webhookAction) {
    return switch (webhookAction.toLowerCase()) {
        case "issues" -> actionKey.equals("new_issue") || 
                        actionKey.equals("issue_updated");
        case "pull_request" -> actionKey.equals("new_pull_request") || 
                              actionKey.equals("pr_updated");
        case "push" -> actionKey.equals("push_to_branch") || 
                      actionKey.equals("commit_pushed");
        case "release" -> actionKey.equals("new_release");
        case "star" -> actionKey.equals("repository_starred");
        default -> false;
    };
}
```

#### Execution Creation

```java
private Execution createExecutionForWebhook(
    ActionInstance instance,
    Map<String, Object> payload
) {
    try {
        // Map webhook payload to action parameters
        Map<String, Object> mappedPayload = payloadMappingService
            .mapWebhookPayload(
                instance.getActionDefinition().getService().getKey(),
                instance.getActionDefinition().getKey(),
                payload
            );
        
        // Create execution
        return executionService.createExecution(
            instance.getActionLink().getArea(),
            instance.getActionLink(),
            mappedPayload,
            "Triggered by webhook event"
        );
    } catch (Exception e) {
        log.error("Failed to create execution for webhook", e);
        return null;
    }
}
```

### 6. PayloadMappingService

**Location**: `src/main/java/area/server/AREA_Back/service/Webhook/PayloadMappingService.java`

Transforms webhook payloads to internal action parameter format.

#### Mapping Strategy

```java
@Service
public class PayloadMappingService {
    
    public Map<String, Object> mapWebhookPayload(
        String service,
        String actionKey,
        Map<String, Object> webhookPayload
    ) {
        return switch (service.toLowerCase()) {
            case "github" -> mapGitHubPayload(actionKey, webhookPayload);
            case "slack" -> mapSlackPayload(actionKey, webhookPayload);
            case "discord" -> mapDiscordPayload(actionKey, webhookPayload);
            default -> webhookPayload;
        };
    }
}
```

#### GitHub Mapping Examples

```java
private Map<String, Object> mapGitHubPayload(
    String actionKey,
    Map<String, Object> payload
) {
    return switch (actionKey) {
        case "new_issue" -> Map.of(
            "issue_number", getPropertyValue(payload, "issue.number"),
            "title", getPropertyValue(payload, "issue.title"),
            "body", getPropertyValue(payload, "issue.body"),
            "state", getPropertyValue(payload, "issue.state"),
            "user", getPropertyValue(payload, "issue.user.login"),
            "repository", getPropertyValue(payload, "repository.full_name"),
            "url", getPropertyValue(payload, "issue.html_url"),
            "created_at", getPropertyValue(payload, "issue.created_at")
        );
        
        case "new_pull_request" -> Map.of(
            "pr_number", getPropertyValue(payload, "pull_request.number"),
            "title", getPropertyValue(payload, "pull_request.title"),
            "body", getPropertyValue(payload, "pull_request.body"),
            "state", getPropertyValue(payload, "pull_request.state"),
            "user", getPropertyValue(payload, "pull_request.user.login"),
            "repository", getPropertyValue(payload, "repository.full_name"),
            "url", getPropertyValue(payload, "pull_request.html_url"),
            "head", getPropertyValue(payload, "pull_request.head.ref"),
            "base", getPropertyValue(payload, "pull_request.base.ref")
        );
        
        default -> payload;
    };
}
```

#### Property Extraction

```java
private Object getPropertyValue(Map<String, Object> payload, String path) {
    String[] parts = path.split("\\.");
    Object current = payload;
    
    for (String part : parts) {
        if (current instanceof Map) {
            current = ((Map<?, ?>) current).get(part);
        } else {
            return null;
        }
    }
    
    return current;
}
```

## Webhook Flow

### Complete Flow Diagram

```
1. External Service
   ↓ POST webhook
2. WebhookController.handleWebhook()
   ↓
3. Extract Event ID
   ↓
4. WebhookSignatureValidator.validate()
   ├─ Valid → Continue
   └─ Invalid → Return 401
   ↓
5. WebhookDeduplicationService.checkAndMark()
   ├─ New → Continue
   └─ Duplicate → Return 200 (duplicate status)
   ↓
6. WebhookEventProcessingService.processWebhook()
   ↓
7. Find Matching Action Instances
   ├─ Filter by service
   ├─ Filter by action type
   └─ Filter by activation mode (WEBHOOK)
   ↓
8. For each matching instance:
   ├─ PayloadMappingService.mapPayload()
   ├─ ExecutionService.createExecution()
   └─ Queue execution for worker
   ↓
9. Return Success Response
   └─ Include execution IDs and metrics
```

### Example Flow: GitHub Issue Webhook

```
GitHub → POST /api/hooks/github/issues
       Headers:
         X-GitHub-Event: issues
         X-Hub-Signature-256: sha256=...
         X-GitHub-Delivery: abc-123
       Body:
         {"action": "opened", "issue": {...}}
         
Controller → Extract eventId: "abc-123"
          → Validate signature ✓
          → Check deduplication ✓
          → Find matching instances:
              - User A: "new_issue" action
              - User B: "new_issue" action
              
Processing → Map payload:
             {
               "issue_number": 42,
               "title": "Bug report",
               "repository": "owner/repo",
               ...
             }
          → Create executions:
              - Execution 1 (User A)
              - Execution 2 (User B)
              
Response ← 200 OK
          {
            "status": "processed",
            "executionCount": 2,
            "executionIds": [...]
          }
```

## Service Integration

### GitHub

**Webhook Setup**:
1. Go to repository Settings → Webhooks
2. Add webhook URL: `https://api.area.com/api/hooks/github/issues`
3. Select events: Issues, Pull Requests, Push, etc.
4. Set secret from `GITHUB_WEBHOOK_SECRET`

**Supported Events**:
- `issues`: Issue created, edited, closed
- `pull_request`: PR opened, closed, merged
- `push`: Code pushed to repository
- `release`: Release published
- `star`: Repository starred
- `fork`: Repository forked

### Slack

**Webhook Setup**:
1. Create Slack App
2. Enable Event Subscriptions
3. Set Request URL: `https://api.area.com/api/hooks/slack/message`
4. Subscribe to events: `message.channels`, `reaction_added`, etc.

**Supported Events**:
- `message`: New message posted
- `reaction_added`: Reaction added to message
- `channel_created`: Channel created
- `member_joined_channel`: User joined channel

### Discord

**Webhook Setup**:
1. Create Discord Application
2. Add webhook URL in Webhooks section
3. Set public key for Ed25519 validation

**Supported Events**:
- `MESSAGE_CREATE`: New message
- `MESSAGE_DELETE`: Message deleted
- `GUILD_MEMBER_ADD`: Member joined
- `MESSAGE_REACTION_ADD`: Reaction added

## Error Handling

### Error Response Format

```java
@ExceptionHandler(WebhookProcessingException.class)
public ResponseEntity<Map<String, Object>> handleWebhookError(
    WebhookProcessingException e
) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
        "error", "Webhook processing failed",
        "message", e.getMessage(),
        "service", e.getService(),
        "action", e.getAction(),
        "timestamp", LocalDateTime.now().toString()
    ));
}
```

### Common Errors

| Error | HTTP Code | Cause | Solution |
|-------|-----------|-------|----------|
| Invalid Signature | 401 | Wrong secret or algorithm | Verify webhook secret configuration |
| Missing Headers | 400 | Required headers missing | Check service webhook configuration |
| Payload Parse Error | 400 | Invalid JSON | Verify webhook payload format |
| No Matching Actions | 200 | No configured workflows | Create AREA with webhook trigger |
| Execution Failed | 500 | Internal error | Check logs and worker status |

### Retry Strategy

Most services implement automatic retry with exponential backoff:

```
Attempt 1: Immediate
Attempt 2: After 1 minute
Attempt 3: After 5 minutes
Attempt 4: After 30 minutes
Attempt 5: After 2 hours
```

Deduplication prevents multiple executions from retries.

## Security

### Best Practices

1. **Always Validate Signatures**
   - Never process unsigned webhooks
   - Use constant-time comparison
   - Rotate secrets regularly

2. **Implement Rate Limiting**
   ```java
   @RateLimiter(name = "webhook", fallbackMethod = "webhookRateLimitFallback")
   public ResponseEntity<?> handleWebhook(...) {
       // Process webhook
   }
   ```

3. **Use HTTPS Only**
   - Reject HTTP webhook requests
   - Enforce TLS 1.2+

4. **Validate Timestamps**
   - Reject old requests (> 5 minutes)
   - Prevent replay attacks

5. **Log Security Events**
   - Invalid signatures
   - Suspicious patterns
   - Rate limit violations

### Secret Management

```bash
# Environment variables
export GITHUB_WEBHOOK_SECRET="your-github-secret"
export SLACK_WEBHOOK_SECRET="your-slack-secret"
export DISCORD_WEBHOOK_PUBLIC_KEY="your-discord-public-key"

# Docker Compose
services:
  backend:
    environment:
      - GITHUB_WEBHOOK_SECRET=${GITHUB_WEBHOOK_SECRET}
      - SLACK_WEBHOOK_SECRET=${SLACK_WEBHOOK_SECRET}
      - DISCORD_WEBHOOK_PUBLIC_KEY=${DISCORD_WEBHOOK_PUBLIC_KEY}
```

## Monitoring

### Prometheus Metrics

```java
// Webhook received counter
Counter.builder("webhook_received_total")
    .tag("service", service)
    .tag("action", action)
    .description("Total webhooks received")
    .register(meterRegistry);

// Webhook processing time
Timer.builder("webhook_processing_duration")
    .tag("service", service)
    .description("Webhook processing duration")
    .register(meterRegistry);

// Signature validation failures
Counter.builder("webhook_signature_validation_failures_total")
    .tag("service", service)
    .description("Signature validation failures")
    .register(meterRegistry);

// Duplicate webhooks
Counter.builder("webhook_duplicates_total")
    .tag("service", service)
    .description("Duplicate webhook events")
    .register(meterRegistry);
```

### Key Metrics

- `webhook_received_total{service, action}`: Total webhooks by service
- `webhook_processing_duration{service}`: Processing time distribution
- `webhook_signature_validation_failures_total{service}`: Validation failures
- `webhook_duplicates_total{service}`: Duplicate event count
- `webhook_executions_created_total{service}`: Executions triggered

### Grafana Dashboard

Create dashboard with panels for:
- Webhook request rate by service
- Processing time p50, p95, p99
- Error rate and types
- Deduplication effectiveness
- Execution creation rate

## Testing

### Unit Tests

```java
@Test
void testWebhookSignatureValidation() {
    String payload = "{\"test\": \"data\"}";
    String secret = "test-secret";
    String signature = generateHmacSha256(payload, secret);
    
    boolean valid = signatureValidator.validateGitHubSignature(
        "sha256=" + signature,
        payload,
        secret
    );
    
    assertTrue(valid);
}

@Test
void testWebhookDeduplication() {
    String eventId = "test-event-123";
    String service = "github";
    
    // First call should not be duplicate
    assertFalse(deduplicationService.checkAndMark(eventId, service));
    
    // Second call should be duplicate
    assertTrue(deduplicationService.checkAndMark(eventId, service));
}

@Test
void testPayloadMapping() {
    Map<String, Object> githubPayload = Map.of(
        "issue", Map.of(
            "number", 42,
            "title", "Test Issue"
        )
    );
    
    Map<String, Object> mapped = payloadMappingService
        .mapWebhookPayload("github", "new_issue", githubPayload);
    
    assertEquals(42, mapped.get("issue_number"));
    assertEquals("Test Issue", mapped.get("title"));
}
```

### Integration Tests

```java
@SpringBootTest
@AutoConfigureMockMvc
class WebhookControllerIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void testGitHubWebhook() throws Exception {
        String payload = loadResource("github-issue-webhook.json");
        String signature = generateSignature(payload);
        
        mockMvc.perform(post("/api/hooks/github/issues")
                .header("X-GitHub-Event", "issues")
                .header("X-Hub-Signature-256", signature)
                .header("X-GitHub-Delivery", "test-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("processed"))
            .andExpect(jsonPath("$.executionCount").exists());
    }
}
```

### Mock Webhook Testing

Tool for testing webhook endpoints locally:

```bash
# Install webhook testing tool
npm install -g @webhooksite/cli

# Test GitHub webhook
curl -X POST http://localhost:8080/api/hooks/github/issues \
  -H "Content-Type: application/json" \
  -H "X-GitHub-Event: issues" \
  -H "X-Hub-Signature-256: sha256=$(echo -n '{...}' | openssl dgst -sha256 -hmac 'secret')" \
  -H "X-GitHub-Delivery: test-123" \
  -d @test-payloads/github-issue.json
```

## Resources

- [GitHub Webhooks Documentation](https://docs.github.com/en/developers/webhooks-and-events/webhooks)
- [Slack Events API](https://api.slack.com/apis/connections/events-api)
- [Discord Webhooks](https://discord.com/developers/docs/resources/webhook)
- [HMAC Signature Validation](https://en.wikipedia.org/wiki/HMAC)
- [Ed25519 Signatures](https://ed25519.cr.yp.to/)
