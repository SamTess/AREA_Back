# Discord Provider - Technical Documentation

## Overview

The Discord provider enables OAuth2 authentication and integration with Discord services within the AREA platform. It allows users to authenticate using their Discord account and interact with Discord servers, channels, and messages through automated actions and event triggers.

## Architecture

### Core Components

#### 1. OAuth Service (`OAuthDiscordService.java`)
- **Location**: `src/main/java/area/server/AREA_Back/service/Auth/OAuthDiscordService.java`
- **Extends**: `OAuthService` (abstract base class)
- **Purpose**: Handles Discord OAuth2 authentication and token management

**Key Features**:
- OAuth2 authorization code flow
- User authentication and registration
- Account linking to existing users
- Access token encryption and storage
- Prometheus metrics integration

#### 2. Action Service (`DiscordActionService.java`)
- **Location**: `src/main/java/area/server/AREA_Back/service/Area/Services/DiscordActionService.java`
- **Purpose**: Executes Discord actions (send messages, manage channels, etc.)

#### 3. Webhook Service (`DiscordWebhookService.java`)
- **Location**: `src/main/java/area/server/AREA_Back/service/Webhook/DiscordWebhookService.java`
- **Purpose**: Processes incoming Discord webhook events

## OAuth2 Implementation

### Configuration

The Discord provider requires the following environment variables:

```properties
spring.security.oauth2.client.registration.discord.client-id=<your-client-id>
spring.security.oauth2.client.registration.discord.client-secret=<your-client-secret>
app.webhook.discord.secret=<your-webhook-secret>
OAUTH_REDIRECT_BASE_URL=http://localhost:3000
```

### OAuth Flow

1. **Authorization Request**
   - User initiates OAuth flow via `/api/oauth/discord/authorize`
   - Redirects to Discord with required scopes: `identify`, `email`, `guilds`, `webhook.incoming`
   - Callback URL: `{OAUTH_REDIRECT_BASE_URL}/oauth-callback`

2. **Token Exchange**
   - Authorization code is exchanged for access token
   - Token endpoint: `https://discord.com/api/oauth2/token`
   - Access token is encrypted before storage

3. **User Profile Retrieval**
   - Fetches user profile from `https://discord.com/api/users/@me`
   - Retrieves user guilds from `https://discord.com/api/users/@me/guilds`
   - Creates or updates user account

4. **Session Creation**
   - Generates JWT access and refresh tokens
   - Sets secure HTTP-only cookies
   - Stores encrypted Discord access token in database

### Required Scopes

```
identify         - Access to user's identity
email            - Access to user's email
guilds           - Access to user's guilds (servers)
webhook.incoming - Create and manage webhooks
messages.read    - Read message history
bot              - Add bot to guilds (optional)
```

### Account Linking

The provider supports linking Discord accounts to existing authenticated users:

```java
public UserOAuthIdentity linkToExistingUser(User existingUser, String authorizationCode)
```

**Endpoint**: `/api/oauth-link/discord/exchange`

**Features**:
- Links Discord account to current session user
- Validates email uniqueness
- Prevents duplicate linking
- Stores Discord profile metadata (username, discriminator, avatar, guilds)

## Implemented Services

### Actions (Executable Operations)

#### 1. Send Message to Channel
- **Action Key**: `send_message`
- **Description**: Sends a message to a Discord channel
- **Parameters**:
  - `channel_id` (required): Discord channel ID
  - `content` (required): Message content (max 2000 characters)
  - `embeds` (optional): Array of embed objects
  - `tts` (optional): Text-to-speech flag

**Example**:
```json
{
  "channel_id": "123456789012345678",
  "content": "Hello from AREA!",
  "embeds": [{
    "title": "Notification",
    "description": "Your automation was triggered",
    "color": 3447003,
    "fields": [
      {"name": "Status", "value": "Success", "inline": true}
    ]
  }]
}
```

**API Endpoint**: `POST https://discord.com/api/v10/channels/{channel_id}/messages`

#### 2. Create Channel Webhook
- **Action Key**: `create_webhook`
- **Description**: Creates a webhook for a Discord channel
- **Parameters**:
  - `channel_id` (required): Discord channel ID
  - `name` (required): Webhook name
  - `avatar_url` (optional): Webhook avatar URL

**API Endpoint**: `POST https://discord.com/api/v10/channels/{channel_id}/webhooks`

#### 3. Send Webhook Message
- **Action Key**: `send_webhook_message`
- **Description**: Sends a message via webhook (no authentication required)
- **Parameters**:
  - `webhook_url` (required): Complete webhook URL with token
  - `content` (required): Message content
  - `username` (optional): Override webhook username
  - `avatar_url` (optional): Override webhook avatar

#### 4. Add Reaction
- **Action Key**: `add_reaction`
- **Description**: Adds a reaction emoji to a message
- **Parameters**:
  - `channel_id` (required): Discord channel ID
  - `message_id` (required): Discord message ID
  - `emoji` (required): Unicode emoji or custom emoji ID

**API Endpoint**: `PUT https://discord.com/api/v10/channels/{channel_id}/messages/{message_id}/reactions/{emoji}/@me`

#### 5. Create Guild Channel
- **Action Key**: `create_channel`
- **Description**: Creates a new channel in a guild
- **Parameters**:
  - `guild_id` (required): Discord guild (server) ID
  - `name` (required): Channel name
  - `type` (optional): Channel type (0=text, 2=voice, 4=category)
  - `topic` (optional): Channel topic

**API Endpoint**: `POST https://discord.com/api/v10/guilds/{guild_id}/channels`

#### 6. Delete Message
- **Action Key**: `delete_message`
- **Description**: Deletes a message
- **Parameters**:
  - `channel_id` (required): Discord channel ID
  - `message_id` (required): Discord message ID

**API Endpoint**: `DELETE https://discord.com/api/v10/channels/{channel_id}/messages/{message_id}`

### Events (Triggers)

#### 1. New Message
- **Event Key**: `new_message`
- **Description**: Triggers when a new message is posted
- **Webhook Event**: `MESSAGE_CREATE`
- **Parameters**:
  - `channel_id` (optional): Filter by specific channel
  - `guild_id` (optional): Filter by specific guild
  - `content_contains` (optional): Filter by message content

**Payload**:
```json
{
  "message_id": "123456789012345678",
  "channel_id": "123456789012345678",
  "guild_id": "123456789012345678",
  "author": {
    "id": "123456789012345678",
    "username": "username",
    "discriminator": "1234",
    "avatar": "avatar_hash"
  },
  "content": "Message content",
  "timestamp": "2024-01-15T10:30:00.000Z",
  "attachments": [],
  "embeds": [],
  "mentions": []
}
```

#### 2. Message Deleted
- **Event Key**: `message_deleted`
- **Description**: Triggers when a message is deleted
- **Webhook Event**: `MESSAGE_DELETE`
- **Parameters**:
  - `channel_id` (optional): Filter by specific channel

#### 3. New Guild Member
- **Event Key**: `member_joined`
- **Description**: Triggers when a user joins a guild
- **Webhook Event**: `GUILD_MEMBER_ADD`
- **Parameters**:
  - `guild_id` (required): Discord guild ID

**Payload**:
```json
{
  "guild_id": "123456789012345678",
  "user": {
    "id": "123456789012345678",
    "username": "newmember",
    "discriminator": "5678",
    "avatar": "avatar_hash"
  },
  "joined_at": "2024-01-15T10:30:00.000Z",
  "roles": []
}
```

#### 4. Guild Member Removed
- **Event Key**: `member_left`
- **Description**: Triggers when a user leaves or is kicked from a guild
- **Webhook Event**: `GUILD_MEMBER_REMOVE`
- **Parameters**:
  - `guild_id` (required): Discord guild ID

#### 5. Reaction Added
- **Event Key**: `reaction_added`
- **Description**: Triggers when a reaction is added to a message
- **Webhook Event**: `MESSAGE_REACTION_ADD`
- **Parameters**:
  - `channel_id` (optional): Filter by specific channel
  - `emoji` (optional): Filter by specific emoji

#### 6. Channel Created
- **Event Key**: `channel_created`
- **Description**: Triggers when a channel is created
- **Webhook Event**: `CHANNEL_CREATE`
- **Parameters**:
  - `guild_id` (required): Discord guild ID
  - `type` (optional): Filter by channel type

## API Integration

### Base URL
```
https://discord.com/api/v10
```

### Authentication

All API requests require authentication via Bearer token:

```http
Authorization: Bearer <access_token>
```

### Rate Limiting

Discord implements rate limiting:
- **Global Rate Limit**: 50 requests per second
- **Per-Route Rate Limits**: Vary by endpoint
- **Response Headers**:
  - `X-RateLimit-Limit`: Maximum requests
  - `X-RateLimit-Remaining`: Remaining requests
  - `X-RateLimit-Reset`: Unix timestamp when limit resets

**Handling**:
```java
if (response.getStatusCode() == 429) {
    int retryAfter = response.getHeaders().get("Retry-After");
    Thread.sleep(retryAfter * 1000);
    // Retry request
}
```

### Key Endpoints Used

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/oauth2/token` | POST | Exchange authorization code |
| `/users/@me` | GET | Get current user profile |
| `/users/@me/guilds` | GET | Get user's guilds |
| `/channels/{channel_id}/messages` | POST | Send message |
| `/channels/{channel_id}/messages` | GET | Get channel messages |
| `/channels/{channel_id}/webhooks` | POST | Create webhook |
| `/guilds/{guild_id}/channels` | POST | Create channel |

## Webhook System

### Webhook Registration

To receive Discord events, register a webhook in your Discord application:

1. Go to Discord Developer Portal
2. Select your application
3. Navigate to Webhooks section
4. Add webhook URL: `https://your-domain.com/api/webhooks/discord`

### Webhook Signature Validation

Discord signs webhooks using Ed25519:

```java
public boolean validateSignature(String signature, String timestamp, String body) {
    // Discord uses Ed25519 signature verification
    String message = timestamp + body;
    return Ed25519.verify(signature, message, publicKey);
}
```

**Headers**:
- `X-Signature-Ed25519`: Request signature
- `X-Signature-Timestamp`: Request timestamp

### Webhook Event Processing

```java
@PostMapping("/api/webhooks/discord")
public ResponseEntity<?> handleWebhook(
    @RequestHeader("X-Signature-Ed25519") String signature,
    @RequestHeader("X-Signature-Timestamp") String timestamp,
    @RequestBody Map<String, Object> payload
) {
    // Validate signature
    if (!webhookSignatureValidator.validate("discord", signature, timestamp, payload)) {
        return ResponseEntity.status(401).build();
    }
    
    // Handle interaction verification
    if (payload.get("type").equals(1)) {
        return ResponseEntity.ok(Map.of("type", 1));
    }
    
    // Process webhook event
    webhookEventProcessingService.processWebhook("discord", payload);
    return ResponseEntity.ok().build();
}
```

### Deduplication

Discord webhooks are deduplicated using event IDs:

```java
String eventId = payload.get("id").toString();
if (webhookDeduplicationService.isDuplicate("discord", eventId)) {
    return; // Skip duplicate event
}
```

## Token Management

### Storage

Discord OAuth tokens are stored encrypted in the database:

```java
@Entity
@Table(name = "a_user_oauth_identities", schema = "area")
public class UserOAuthIdentity {
    private String provider = "discord";
    private String accessTokenEnc;    // Encrypted access token
    private String refreshTokenEnc;   // Encrypted refresh token
    private LocalDateTime expiresAt;  // Token expiration
    private Map<String, Object> scopes; // OAuth scopes
    private Map<String, Object> tokenMeta; // Discord user metadata
}
```

### Retrieval

```java
public String getAccessToken(UUID userId) {
    ServiceAccount account = serviceAccountRepository
        .findByUserIdAndServiceKey(userId, "discord")
        .orElseThrow(() -> new RuntimeException("Discord not connected"));
    
    return tokenEncryptionService.decrypt(account.getAccessTokenEnc());
}
```

### Token Refresh

Discord access tokens expire after 7 days:

```java
public String refreshToken(UUID userId) {
    UserOAuthIdentity identity = userOAuthIdentityRepository
        .findByUserIdAndProvider(userId, "discord")
        .orElseThrow();
    
    String refreshToken = tokenEncryptionService.decrypt(identity.getRefreshTokenEnc());
    
    // Exchange refresh token
    MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    params.add("grant_type", "refresh_token");
    params.add("refresh_token", refreshToken);
    params.add("client_id", clientId);
    params.add("client_secret", clientSecret);
    
    // Update stored tokens
    // ...
}
```

## Monitoring & Metrics

### Prometheus Metrics

```java
@PostConstruct
private void initMetrics() {
    oauthLoginSuccessCounter = Counter.builder("discord_oauth_login_success")
        .description("Discord OAuth login successes")
        .register(meterRegistry);
    
    oauthLoginFailureCounter = Counter.builder("discord_oauth_login_failure")
        .description("Discord OAuth login failures")
        .register(meterRegistry);
    
    apiCallCounter = Counter.builder("discord_api_calls")
        .tag("endpoint", "various")
        .description("Discord API calls")
        .register(meterRegistry);
    
    webhookEventCounter = Counter.builder("discord_webhook_events")
        .tag("event_type", "various")
        .description("Discord webhook events received")
        .register(meterRegistry);
}
```

### Exposed Metrics

- `discord_oauth_login_success_total`: Successful OAuth logins
- `discord_oauth_login_failure_total`: Failed OAuth logins
- `discord_api_calls_total`: Total API calls made
- `discord_webhook_events_total`: Webhook events received
- `discord_rate_limit_hits_total`: Rate limit encounters

## Database Schema

### Service Entry

```sql
INSERT INTO area.a_services (key, name, auth, is_active, docs_url, icon_light_url, icon_dark_url) 
VALUES (
    'discord',
    'Discord',
    'OAUTH2',
    true,
    'https://discord.com/developers/docs',
    'https://cdn.simpleicons.org/discord/5865F2',
    'https://cdn.simpleicons.org/discord/FFFFFF'
);
```

### Action Definitions

Action definitions for Discord are stored in the migration file:
- Location: `src/main/resources/db/migration/V12__Add_discord_service_and_actions.sql`

## Error Handling

### Common Errors

| Error Code | Description | Handling |
|------------|-------------|----------|
| 401 | Unauthorized | Refresh token or re-authenticate |
| 403 | Forbidden | Check bot permissions |
| 404 | Not Found | Verify channel/guild/message ID |
| 429 | Rate Limited | Wait and retry with exponential backoff |
| 50001 | Missing Access | User lacks permission |
| 50013 | Missing Permissions | Bot lacks permission |
| 50035 | Invalid Form Body | Validate request parameters |

### Error Response Format

```json
{
  "code": 50035,
  "message": "Invalid Form Body",
  "errors": {
    "content": {
      "_errors": [
        {
          "code": "BASE_TYPE_REQUIRED",
          "message": "This field is required"
        }
      ]
    }
  }
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
            case "create_webhook" -> createWebhook(accessToken, actionParams);
            default -> throw new IllegalArgumentException("Unknown action: " + actionKey);
        };
    } catch (HttpClientErrorException e) {
        if (e.getStatusCode().value() == 401) {
            // Token expired, try refresh
            refreshToken(userId);
            return executeAction(actionKey, inputPayload, actionParams, userId);
        }
        throw new RuntimeException("Discord API error: " + e.getMessage());
    }
}
```

## Security Considerations

### Token Security

1. **Encryption**: All tokens encrypted at rest using AES-256
2. **Transmission**: Tokens only transmitted over HTTPS
3. **Scope Limitation**: Request minimum required scopes
4. **Token Rotation**: Implement automatic token refresh

### Webhook Security

1. **Signature Validation**: Always validate Ed25519 signatures
2. **Timestamp Check**: Reject requests older than 5 minutes
3. **Secret Management**: Store webhook secrets in environment variables
4. **HTTPS Only**: Only accept webhook requests over HTTPS

### Permission Management

```java
// Check bot permissions before action execution
public boolean hasPermission(String guildId, String permission) {
    // Fetch bot member in guild
    // Check permission flags
    return true; // or false
}
```

## Integration Examples

### Frontend OAuth Initiation

```typescript
export const connectDiscord = (): void => {
  localStorage.setItem('oauth_provider', 'discord');
  const oauthUrl = `${API_CONFIG.baseURL}/api/oauth/discord/authorize`;
  window.location.href = oauthUrl;
};
```

### Backend AREA Creation

```java
CreateAreaWithActionsRequest request = new CreateAreaWithActionsRequest();
request.setName("Discord Notification");
request.setDescription("Send message when GitHub issue created");

// Action (trigger)
ActionLinkRequest action = new ActionLinkRequest();
action.setActionDefinitionKey("github.new_issue");
action.setServiceAccountId(githubAccountId);
action.setActionParams(Map.of("repository", "owner/repo"));

// Reaction
ActionLinkRequest reaction = new ActionLinkRequest();
reaction.setActionDefinitionKey("discord.send_message");
reaction.setServiceAccountId(discordAccountId);
reaction.setActionParams(Map.of(
    "channel_id", "123456789012345678",
    "content", "New issue: {{issue_title}}"
));

request.setActions(List.of(action));
request.setReactions(List.of(reaction));
```

## Testing

### Unit Tests Location
- `src/test/java/area/server/AREA_Back/service/OAuthDiscordServiceTest.java`
- `src/test/java/area/server/AREA_Back/service/DiscordActionServiceTest.java`
- `src/test/java/area/server/AREA_Back/service/DiscordWebhookServiceTest.java`

### Test Coverage

- Invalid action keys
- Missing tokens
- Token expiration and refresh
- Webhook signature validation
- Event deduplication
- Error handling scenarios
- Rate limiting scenarios
- Permission validation

### Mock Data

```java
@Test
void testSendMessage() {
    // Mock Discord API response
    when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
        .thenReturn(ResponseEntity.ok(Map.of(
            "id", "123456789012345678",
            "content", "Test message"
        )));
    
    Map<String, Object> result = discordActionService.executeAction(
        "send_message",
        Map.of(),
        Map.of("channel_id", "123", "content", "Test"),
        userId
    );
    
    assertNotNull(result);
    assertEquals("123456789012345678", result.get("message_id"));
}
```

## Future Enhancements

### Planned Features

1. **Voice Channel Support**
   - Join/leave voice channels
   - Send voice messages
   - Stream audio

2. **Advanced Moderation**
   - Auto-moderation rules
   - Ban/kick members
   - Timeout management

3. **Slash Commands**
   - Register slash commands
   - Handle command interactions
   - Command permissions

4. **Thread Support**
   - Create/manage threads
   - Thread events
   - Thread permissions

5. **Forum Channels**
   - Create forum posts
   - Manage tags
   - Forum events

6. **Stage Channels**
   - Create stage instances
   - Manage speakers
   - Stage events

7. **Scheduled Events**
   - Create guild events
   - Manage RSVPs
   - Event reminders

### API Version Updates

Discord API is currently at v10. Monitor for:
- New event types
- Deprecated endpoints
- New permissions
- Gateway intents changes

## Resources

- **Discord Developer Portal**: https://discord.com/developers/docs
- **OAuth2 Documentation**: https://discord.com/developers/docs/topics/oauth2
- **API Reference**: https://discord.com/developers/docs/reference
- **Community Resources**: https://discord.gg/discord-developers
- **Rate Limit Guide**: https://discord.com/developers/docs/topics/rate-limits
- **Gateway Documentation**: https://discord.com/developers/docs/topics/gateway

## Support

For issues related to Discord integration:
1. Check Discord API status: https://discordstatus.com
2. Review API documentation
3. Check AREA backend logs
4. Verify OAuth credentials
5. Test webhook connectivity
