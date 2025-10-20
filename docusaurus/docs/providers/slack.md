# Slack Provider - Technical Documentation

## Overview

The Slack provider enables OAuth2 authentication and integration with Slack workspaces within the AREA platform. It allows users to authenticate using their Slack account and interact with channels, messages, and workspace members through automated actions and event triggers.

## Architecture

### Core Components

#### 1. OAuth Service (`OAuthSlackService.java`)
- **Location**: `src/main/java/area/server/AREA_Back/service/Auth/OAuthSlackService.java`
- **Extends**: `OAuthService` (abstract base class)
- **Purpose**: Handles Slack OAuth2 authentication and token management

**Key Features**:
- OAuth2 authorization code flow
- User authentication and registration
- Account linking to existing users
- Access token encryption and storage
- Workspace information retrieval
- Prometheus metrics integration

#### 2. Action Service (`SlackActionService.java`)
- **Location**: `src/main/java/area/server/AREA_Back/service/Area/Services/SlackActionService.java`
- **Purpose**: Executes Slack actions (send messages, manage channels, etc.)

#### 3. Webhook Service (`SlackWebhookService.java`)
- **Location**: `src/main/java/area/server/AREA_Back/service/Webhook/SlackWebhookService.java`
- **Purpose**: Processes incoming Slack webhook events

## OAuth2 Implementation

### Configuration

The Slack provider requires the following environment variables:

```properties
spring.security.oauth2.client.registration.slack.client-id=<your-client-id>
spring.security.oauth2.client.registration.slack.client-secret=<your-client-secret>
app.webhook.slack.secret=<your-signing-secret>
OAUTH_REDIRECT_BASE_URL=http://localhost:3000
```

### OAuth Flow

1. **Authorization Request**
   - User initiates OAuth flow via `/api/oauth/slack/authorize`
   - Redirects to Slack with required scopes
   - Callback URL: `{OAUTH_REDIRECT_BASE_URL}/oauth-callback`

2. **Token Exchange**
   - Authorization code is exchanged for access token
   - Token endpoint: `https://slack.com/api/oauth.v2.access`
   - Access token is encrypted before storage

3. **User Profile Retrieval**
   - Fetches user identity from `https://slack.com/api/users.identity`
   - Retrieves workspace information
   - Creates or updates user account

4. **Session Creation**
   - Generates JWT access and refresh tokens
   - Sets secure HTTP-only cookies
   - Stores encrypted Slack access token in database

### Required Scopes

```
channels:read       - View basic channel information
channels:write      - Manage public channels
chat:write          - Send messages
users:read          - View users in workspace
users:read.email    - View user email addresses
reactions:read      - View reactions on messages
reactions:write     - Add reactions to messages
files:read          - View files shared in channels
files:write         - Upload and share files
```

### Account Linking

The provider supports linking Slack accounts to existing authenticated users:

```java
public UserOAuthIdentity linkToExistingUser(User existingUser, String authorizationCode)
```

**Endpoint**: `/api/oauth-link/slack/exchange`

**Features**:
- Links Slack account to current session user
- Validates email uniqueness
- Prevents duplicate linking
- Stores Slack profile metadata (user_id, team_id, workspace_name)

## Implemented Services

### Actions (Executable Operations)

#### 1. Send Message
- **Action Key**: `send_message`
- **Description**: Sends a message to a Slack channel
- **Parameters**:
  - `channel` (required): Channel ID or name
  - `text` (required): Message text
  - `blocks` (optional): Rich message blocks
  - `thread_ts` (optional): Thread timestamp for replies

**Example**:
```json
{
  "channel": "C1234567890",
  "text": "Hello from AREA!",
  "blocks": [
    {
      "type": "section",
      "text": {
        "type": "mrkdwn",
        "text": "*Notification*\nYour automation was triggered"
      }
    }
  ]
}
```

**API Endpoint**: `POST https://slack.com/api/chat.postMessage`

#### 2. Create Channel
- **Action Key**: `create_channel`
- **Description**: Creates a new public or private channel
- **Parameters**:
  - `name` (required): Channel name (lowercase, no spaces)
  - `is_private` (optional): Create private channel (default: false)
  - `team_id` (optional): Workspace ID

**API Endpoint**: `POST https://slack.com/api/conversations.create`

#### 3. Invite User to Channel
- **Action Key**: `invite_user`
- **Description**: Invites user to a channel
- **Parameters**:
  - `channel` (required): Channel ID
  - `users` (required): Comma-separated user IDs

**API Endpoint**: `POST https://slack.com/api/conversations.invite`

#### 4. Add Reaction
- **Action Key**: `add_reaction`
- **Description**: Adds a reaction emoji to a message
- **Parameters**:
  - `channel` (required): Channel ID
  - `timestamp` (required): Message timestamp
  - `name` (required): Emoji name (without colons)

**Example**:
```json
{
  "channel": "C1234567890",
  "timestamp": "1234567890.123456",
  "name": "thumbsup"
}
```

**API Endpoint**: `POST https://slack.com/api/reactions.add`

#### 5. Pin Message
- **Action Key**: `pin_message`
- **Description**: Pins a message to a channel
- **Parameters**:
  - `channel` (required): Channel ID
  - `timestamp` (required): Message timestamp

**API Endpoint**: `POST https://slack.com/api/pins.add`

#### 6. Upload File
- **Action Key**: `upload_file`
- **Description**: Uploads a file to Slack
- **Parameters**:
  - `channels` (required): Comma-separated channel IDs
  - `content` (optional): File content
  - `file` (optional): File URL or path
  - `title` (optional): File title
  - `initial_comment` (optional): Initial comment

**API Endpoint**: `POST https://slack.com/api/files.upload`

#### 7. Archive Channel
- **Action Key**: `archive_channel`
- **Description**: Archives a channel
- **Parameters**:
  - `channel` (required): Channel ID

**API Endpoint**: `POST https://slack.com/api/conversations.archive`

### Events (Triggers)

#### 1. New Message
- **Event Key**: `message.channels`
- **Description**: Triggers when a message is posted in a channel
- **Webhook Event**: `message`
- **Parameters**:
  - `channel_id` (optional): Filter by specific channel
  - `user_id` (optional): Filter by specific user
  - `text_contains` (optional): Filter by message content

**Payload**:
```json
{
  "type": "message",
  "channel": "C1234567890",
  "user": "U1234567890",
  "text": "Hello, world!",
  "ts": "1234567890.123456",
  "thread_ts": "1234567890.123456",
  "channel_type": "channel"
}
```

#### 2. Message Deleted
- **Event Key**: `message_deleted`
- **Description**: Triggers when a message is deleted
- **Webhook Event**: `message_deleted`

#### 3. Reaction Added
- **Event Key**: `reaction_added`
- **Description**: Triggers when a reaction is added to a message
- **Webhook Event**: `reaction_added`
- **Parameters**:
  - `emoji` (optional): Filter by specific emoji

**Payload**:
```json
{
  "type": "reaction_added",
  "user": "U1234567890",
  "reaction": "thumbsup",
  "item": {
    "type": "message",
    "channel": "C1234567890",
    "ts": "1234567890.123456"
  },
  "event_ts": "1234567890.123456"
}
```

#### 4. Channel Created
- **Event Key**: `channel_created`
- **Description**: Triggers when a channel is created
- **Webhook Event**: `channel_created`

**Payload**:
```json
{
  "type": "channel_created",
  "channel": {
    "id": "C1234567890",
    "name": "new-channel",
    "created": 1234567890,
    "creator": "U1234567890"
  }
}
```

#### 5. User Joined Channel
- **Event Key**: `member_joined_channel`
- **Description**: Triggers when a user joins a channel
- **Webhook Event**: `member_joined_channel`

#### 6. File Shared
- **Event Key**: `file_shared`
- **Description**: Triggers when a file is shared
- **Webhook Event**: `file_shared`

## API Integration

### Base URL
```
https://slack.com/api
```

### Authentication

All API requests require authentication via Bearer token:

```http
Authorization: Bearer <access_token>
```

### Response Format

Slack API responses follow a consistent format:

```json
{
  "ok": true,
  "channel": "C1234567890",
  "ts": "1234567890.123456",
  "message": {
    "text": "Message content"
  }
}
```

Error response:
```json
{
  "ok": false,
  "error": "channel_not_found"
}
```

### Rate Limiting

Slack implements rate limiting:
- **Tier 1**: 1 request per minute (e.g., `users.list`)
- **Tier 2**: 20 requests per minute (e.g., `chat.postMessage`)
- **Tier 3**: 50 requests per minute (e.g., `conversations.history`)
- **Tier 4**: 100+ requests per minute (e.g., `conversations.list`)

**Response Headers**:
- `Retry-After`: Seconds to wait before retry

**Handling**:
```java
if (response.getStatusCode() == 429) {
    int retryAfter = Integer.parseInt(
        response.getHeaders().getFirst("Retry-After")
    );
    Thread.sleep(retryAfter * 1000);
    // Retry request
}
```

### Key Endpoints Used

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/oauth.v2.access` | POST | Exchange authorization code |
| `/users.identity` | GET | Get current user identity |
| `/chat.postMessage` | POST | Send message |
| `/conversations.create` | POST | Create channel |
| `/conversations.invite` | POST | Invite user to channel |
| `/reactions.add` | POST | Add reaction |
| `/files.upload` | POST | Upload file |

## Webhook System

### Webhook Registration

To receive Slack events, set up Event Subscriptions in your Slack app:

1. Go to Slack API Dashboard
2. Select your app
3. Navigate to Event Subscriptions
4. Enable Events
5. Add Request URL: `https://your-domain.com/api/webhooks/slack`
6. Subscribe to bot events

### URL Verification

Slack sends a challenge request to verify the webhook URL:

```java
@PostMapping("/api/webhooks/slack")
public ResponseEntity<?> handleWebhook(@RequestBody Map<String, Object> payload) {
    // Handle URL verification
    if ("url_verification".equals(payload.get("type"))) {
        return ResponseEntity.ok(Map.of(
            "challenge", payload.get("challenge")
        ));
    }
    
    // Process event
    processEvent(payload);
    return ResponseEntity.ok().build();
}
```

### Webhook Signature Validation

Slack signs webhooks using HMAC-SHA256:

```java
public boolean validateSignature(String signature, String timestamp, 
                                 String body, String signingSecret) {
    // Check timestamp freshness (max 5 minutes)
    long requestTime = Long.parseLong(timestamp);
    long currentTime = System.currentTimeMillis() / 1000;
    if (Math.abs(currentTime - requestTime) > 300) {
        return false;
    }
    
    // Compute signature
    String baseString = "v0:" + timestamp + ":" + body;
    Mac hmac = Mac.getInstance("HmacSHA256");
    SecretKeySpec secretKey = new SecretKeySpec(
        signingSecret.getBytes(), 
        "HmacSHA256"
    );
    hmac.init(secretKey);
    byte[] hash = hmac.doFinal(baseString.getBytes());
    String computed = "v0=" + Hex.encodeHexString(hash);
    
    return MessageDigest.isEqual(
        signature.getBytes(),
        computed.getBytes()
    );
}
```

**Headers**:
- `X-Slack-Request-Timestamp`: Request timestamp
- `X-Slack-Signature`: Request signature (v0=...)

### Webhook Event Processing

```java
@PostMapping("/api/webhooks/slack")
public ResponseEntity<?> handleWebhook(
    @RequestHeader("X-Slack-Request-Timestamp") String timestamp,
    @RequestHeader("X-Slack-Signature") String signature,
    @RequestBody String body
) {
    // Validate signature
    if (!webhookSignatureValidator.validate("slack", signature, timestamp, body)) {
        return ResponseEntity.status(401).build();
    }
    
    Map<String, Object> payload = objectMapper.readValue(body, Map.class);
    
    // Handle URL verification
    if ("url_verification".equals(payload.get("type"))) {
        return ResponseEntity.ok(Map.of("challenge", payload.get("challenge")));
    }
    
    // Handle event callback
    if ("event_callback".equals(payload.get("type"))) {
        Map<String, Object> event = (Map<String, Object>) payload.get("event");
        webhookEventProcessingService.processWebhook("slack", event);
    }
    
    return ResponseEntity.ok().build();
}
```

### Deduplication

Slack webhooks are deduplicated using event IDs:

```java
String eventId = payload.get("event_id").toString();
if (webhookDeduplicationService.isDuplicate("slack", eventId)) {
    return; // Skip duplicate event
}
```

## Token Management

### Storage

Slack OAuth tokens are stored encrypted in the database:

```java
@Entity
@Table(name = "a_user_oauth_identities", schema = "area")
public class UserOAuthIdentity {
    private String provider = "slack";
    private String accessTokenEnc;    // Encrypted access token
    private LocalDateTime expiresAt;  // Token expiration (Slack tokens don't expire by default)
    private Map<String, Object> scopes; // OAuth scopes
    private Map<String, Object> tokenMeta; // Slack workspace metadata
}
```

### Retrieval

```java
public String getAccessToken(UUID userId) {
    ServiceAccount account = serviceAccountRepository
        .findByUserIdAndServiceKey(userId, "slack")
        .orElseThrow(() -> new RuntimeException("Slack not connected"));
    
    return tokenEncryptionService.decrypt(account.getAccessTokenEnc());
}
```

### Token Refresh

Slack bot tokens do not expire by default, but user tokens may need refresh:

```java
public String refreshToken(UUID userId) {
    // Slack user tokens rarely expire
    // If needed, re-authenticate user via OAuth flow
    throw new UnsupportedOperationException(
        "Slack tokens do not support refresh. Re-authenticate user."
    );
}
```

## Monitoring & Metrics

### Prometheus Metrics

```java
@PostConstruct
private void initMetrics() {
    oauthLoginSuccessCounter = Counter.builder("slack_oauth_login_success")
        .description("Slack OAuth login successes")
        .register(meterRegistry);
    
    oauthLoginFailureCounter = Counter.builder("slack_oauth_login_failure")
        .description("Slack OAuth login failures")
        .register(meterRegistry);
    
    apiCallCounter = Counter.builder("slack_api_calls")
        .tag("endpoint", "various")
        .description("Slack API calls")
        .register(meterRegistry);
    
    webhookEventCounter = Counter.builder("slack_webhook_events")
        .tag("event_type", "various")
        .description("Slack webhook events received")
        .register(meterRegistry);
}
```

### Exposed Metrics

- `slack_oauth_login_success_total`: Successful OAuth logins
- `slack_oauth_login_failure_total`: Failed OAuth logins
- `slack_api_calls_total`: Total API calls made
- `slack_webhook_events_total`: Webhook events received
- `slack_rate_limit_hits_total`: Rate limit encounters

## Database Schema

### Service Entry

```sql
INSERT INTO area.a_services (key, name, auth, is_active, docs_url, icon_light_url, icon_dark_url) 
VALUES (
    'slack',
    'Slack',
    'OAUTH2',
    true,
    'https://api.slack.com/docs',
    'https://cdn.simpleicons.org/slack/4A154B',
    'https://cdn.simpleicons.org/slack/FFFFFF'
);
```

### Action Definitions

Action definitions for Slack are stored in the migration file:
- Location: `src/main/resources/db/migration/V13__Add_slack_service_and_actions.sql`

## Error Handling

### Common Errors

| Error Code | Description | Handling |
|------------|-------------|----------|
| invalid_auth | Invalid token | Re-authenticate user |
| channel_not_found | Channel doesn't exist | Verify channel ID |
| cant_invite_self | Can't invite self | Check user ID |
| not_in_channel | Bot not in channel | Invite bot first |
| rate_limited | Rate limit exceeded | Implement backoff |
| missing_scope | Missing permission | Request additional scopes |

### Error Response Format

```json
{
  "ok": false,
  "error": "channel_not_found"
}
```

### Implementation

```java
public Map<String, Object> executeAction(String actionKey, 
                                         Map<String, Object> inputPayload,
                                         Map<String, Object> actionParams,
                                         UUID userId) {
    try {
        String accessToken = getAccessToken(userId);
        
        return switch (actionKey) {
            case "send_message" -> sendMessage(accessToken, actionParams);
            case "create_channel" -> createChannel(accessToken, actionParams);
            default -> throw new IllegalArgumentException("Unknown action: " + actionKey);
        };
    } catch (HttpClientErrorException e) {
        Map<String, Object> errorResponse = objectMapper.readValue(
            e.getResponseBodyAsString(),
            Map.class
        );
        
        String error = (String) errorResponse.get("error");
        
        if ("invalid_auth".equals(error)) {
            throw new RuntimeException("Slack token expired. Please re-authenticate.");
        }
        
        throw new RuntimeException("Slack API error: " + error);
    }
}
```

## Security Considerations

### Token Security

1. **Encryption**: All tokens encrypted at rest using AES-256
2. **Transmission**: Tokens only transmitted over HTTPS
3. **Scope Limitation**: Request minimum required scopes
4. **Token Storage**: Store in secure database with encryption

### Webhook Security

1. **Signature Validation**: Always validate HMAC-SHA256 signatures
2. **Timestamp Check**: Reject requests older than 5 minutes
3. **Secret Management**: Store signing secret in environment variables
4. **HTTPS Only**: Only accept webhook requests over HTTPS

### Permission Management

```java
// Check bot permissions before action execution
public boolean hasPermission(String channel, String permission) {
    // Fetch bot member in channel
    // Check permission scopes
    return true; // or false
}
```

## Integration Examples

### Frontend OAuth Initiation

```typescript
export const connectSlack = (): void => {
  localStorage.setItem('oauth_provider', 'slack');
  const oauthUrl = `${API_CONFIG.baseURL}/api/oauth/slack/authorize`;
  window.location.href = oauthUrl;
};
```

### Backend AREA Creation

```java
CreateAreaWithActionsRequest request = new CreateAreaWithActionsRequest();
request.setName("Slack Notification");
request.setDescription("Send Slack message when GitHub issue created");

// Action (trigger)
ActionLinkRequest action = new ActionLinkRequest();
action.setActionDefinitionKey("github.new_issue");
action.setServiceAccountId(githubAccountId);
action.setActionParams(Map.of("repository", "owner/repo"));

// Reaction
ActionLinkRequest reaction = new ActionLinkRequest();
reaction.setActionDefinitionKey("slack.send_message");
reaction.setServiceAccountId(slackAccountId);
reaction.setActionParams(Map.of(
    "channel", "C1234567890",
    "text", "New issue: {{issue_title}}"
));

request.setActions(List.of(action));
request.setReactions(List.of(reaction));
```

## Testing

### Unit Tests Location
- `src/test/java/area/server/AREA_Back/service/OAuthSlackServiceTest.java`
- `src/test/java/area/server/AREA_Back/service/SlackActionServiceTest.java`
- `src/test/java/area/server/AREA_Back/service/SlackWebhookServiceTest.java`

### Test Coverage

- Invalid action keys
- Missing tokens
- Token validation
- Webhook signature validation
- Event deduplication
- Error handling scenarios
- Rate limiting scenarios
- Permission validation

### Mock Data

```java
@Test
void testSendMessage() {
    // Mock Slack API response
    when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
        .thenReturn(ResponseEntity.ok(Map.of(
            "ok", true,
            "channel", "C1234567890",
            "ts", "1234567890.123456"
        )));
    
    Map<String, Object> result = slackActionService.executeAction(
        "send_message",
        Map.of(),
        Map.of("channel", "C123", "text", "Test"),
        userId
    );
    
    assertNotNull(result);
    assertTrue((Boolean) result.get("ok"));
}
```

## Future Enhancements

### Planned Features

1. **Slack Connect**
   - Cross-workspace channels
   - External collaboration

2. **Workflow Builder Integration**
   - Custom workflow steps
   - Workflow triggers

3. **Modal/Block Kit**
   - Interactive modals
   - Rich UI components
   - Form submissions

4. **Slash Commands**
   - Register custom commands
   - Handle command interactions

5. **App Home**
   - Customize app home tab
   - User-specific content

6. **Scheduled Messages**
   - Schedule message delivery
   - Recurring messages

7. **Thread Management**
   - Reply to threads
   - Thread subscriptions

## Resources

- **Slack API Documentation**: https://api.slack.com/docs
- **OAuth2 Guide**: https://api.slack.com/authentication/oauth-v2
- **Event Subscriptions**: https://api.slack.com/events-api
- **Block Kit Builder**: https://app.slack.com/block-kit-builder
- **Rate Limiting**: https://api.slack.com/docs/rate-limits
- **Webhook Signing**: https://api.slack.com/authentication/verifying-requests-from-slack

## Support

For issues related to Slack integration:
1. Check Slack API status: https://status.slack.com
2. Review API documentation
3. Check AREA backend logs
4. Verify OAuth credentials
5. Test webhook connectivity
