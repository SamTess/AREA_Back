# OAuth Providers Documentation

This directory contains comprehensive technical documentation for OAuth2 providers implemented in the AREA platform.

## Overview

The AREA platform integrates with external services through OAuth2 authentication, allowing users to connect their accounts and automate workflows across different platforms. Each provider documentation includes detailed information about implementation, available services, and integration guidelines.

## Available Providers

### [GitHub Provider](./github.md)
**Status**: ✅ Implemented  
**Services**: Repository management, Issues, Pull Requests  
**Scopes**: `user:email`

Key features:
- Create and manage GitHub issues
- Monitor new issues, pull requests, and commits
- Add comments and labels
- Close issues programmatically

### [Google Provider](./google.md)
**Status**: ✅ Implemented  
**Services**: Gmail, Google Calendar, Google Drive, Google Sheets  
**Scopes**: Multiple (Gmail, Calendar, Drive, Sheets)

Key features:
- **Gmail**: Send emails, monitor inbox, manage labels
- **Calendar**: Create events, monitor new/upcoming events
- **Drive**: Create folders, upload files, share documents, monitor changes
- **Sheets**: Add rows, update cells, create spreadsheets, monitor changes

## Documentation Structure

Each provider documentation follows a consistent structure:

1. **Overview**: Brief introduction to the provider and its purpose
2. **Architecture**: Core components and their responsibilities
3. **OAuth2 Implementation**: Configuration, flow, and token management
4. **Implemented Services**: Detailed description of actions and events
5. **API Integration**: Endpoints used and authentication methods
6. **Token Management**: Storage, retrieval, and security
7. **Monitoring & Metrics**: Prometheus metrics exposed
8. **Database Schema**: Data storage structure
9. **Error Handling**: Common errors and solutions
10. **Event Polling Strategy**: How events are monitored
11. **Security Considerations**: Best practices and safeguards
12. **Integration Examples**: Code samples for frontend and backend
13. **Testing**: Test coverage and guidelines
14. **Future Enhancements**: Planned features and improvements
15. **References**: External documentation links

## Common Concepts

### OAuth2 Flow

All providers follow a standard OAuth2 authorization code flow:

1. User clicks "Connect [Provider]" button
2. Redirected to provider's authorization page
3. User grants permissions
4. Provider redirects back with authorization code
5. Backend exchanges code for access token
6. Token is encrypted and stored
7. User session is created with JWT tokens

### Actions vs Events

**Actions** are executable operations that perform a task:
- Create an issue on GitHub
- Send an email via Gmail
- Create a calendar event
- Upload a file to Google Drive

**Events** are triggers that monitor for changes:
- New issue created on GitHub
- New email received in Gmail
- New calendar event created
- New file uploaded to Google Drive

### Token Management

All providers implement secure token management:
- **Encryption**: All tokens encrypted at rest using AES
- **Storage**: Tokens stored in `user_oauth_identities` table
- **Refresh**: Access tokens automatically refreshed when expired (Google)
- **Revocation**: Users can revoke access through provider settings

### Event Polling

Providers use a polling mechanism to check for new events:
- **Scheduler**: Runs every 10 seconds
- **Interval**: Configurable per action instance (default: 5 minutes)
- **Last Check**: Tracks last poll time to avoid duplicate events
- **Concurrency**: Uses concurrent hashmap for thread-safe operations

## Adding a New Provider

To add a new OAuth provider to the platform, follow these steps:

### 1. Create OAuth Service

Extend the abstract `OAuthService` class:

```java
@Service
@ConditionalOnProperty(name = "spring.security.oauth2.client.registration.provider.client-id")
public class OAuthProviderService extends OAuthService {
    
    public OAuthProviderService(
        @Value("${spring.security.oauth2.client.registration.provider.client-id}") String clientId,
        @Value("${spring.security.oauth2.client.registration.provider.client-secret}") String clientSecret,
        JwtService jwtService
    ) {
        super("provider", "Provider Name", "/oauth-icons/provider.svg",
              "https://provider.com/oauth/authorize?client_id=" + clientId,
              clientId, clientSecret, jwtService);
    }
    
    @Override
    public AuthResponse authenticate(OAuthLoginRequest request, HttpServletResponse response) {
        // Implement authentication logic
    }
}
```

### 2. Create Action Service

Create a service to handle provider-specific actions:

```java
@Service
@RequiredArgsConstructor
public class ProviderActionService {
    
    public Map<String, Object> executeAction(String actionKey, 
                                             Map<String, Object> inputPayload,
                                             Map<String, Object> actionParams,
                                             UUID userId) {
        // Implement action execution
    }
    
    public List<Map<String, Object>> checkEvents(String actionKey,
                                                  Map<String, Object> actionParams,
                                                  UUID userId,
                                                  LocalDateTime lastCheck) {
        // Implement event checking
    }
}
```

### 3. Create Event Polling Service (Optional)

If the provider supports event-based triggers:

```java
@Service
@RequiredArgsConstructor
public class ProviderEventPollingService {
    
    @Scheduled(fixedRate = 10000)
    public void pollEvents() {
        // Implement polling logic
    }
}
```

### 4. Configure Environment Variables

Add provider configuration to `.env`:

```properties
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_PROVIDER_CLIENT_ID=your-client-id
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_PROVIDER_CLIENT_SECRET=your-client-secret
```

### 5. Create Documentation

Create a new markdown file in `docs/providers/` following the template:
- `provider-name.md`

Include all sections: Overview, Architecture, OAuth Implementation, Services, etc.

### 6. Update Database

Add action definitions to database migrations:

```sql
INSERT INTO a_action_definitions (service_id, key, name, description, is_event_capable, is_executable)
VALUES (
    (SELECT id FROM a_services WHERE key = 'provider'),
    'action_key',
    'Action Name',
    'Action description',
    false,
    true
);
```

## Testing

Each provider should have comprehensive unit tests:

```java
@SpringBootTest
class ProviderActionServiceTest {
    
    @Test
    void testExecuteActionWithInvalidKey() {
        // Test error handling
    }
    
    @Test
    void testCheckEventsWithValidParams() {
        // Test event checking
    }
}
```

## Metrics

All providers should expose Prometheus metrics:

- `{provider}_oauth_login_success_total`: Successful logins
- `{provider}_oauth_login_failure_total`: Failed logins
- `{provider}_actions_executed_total`: Actions executed
- `{provider}_actions_failed_total`: Failed actions
- `{provider}_events_found_total`: Events found during polling

## Security Best Practices

1. **Minimal Scopes**: Request only necessary permissions
2. **Token Encryption**: Always encrypt tokens at rest
3. **HTTPS Only**: Use HTTPS for all API communications
4. **Token Rotation**: Implement token refresh mechanisms
5. **Rate Limiting**: Respect provider API rate limits
6. **Error Messages**: Don't expose sensitive information in errors
7. **Input Validation**: Validate all user inputs
8. **Audit Logging**: Log authentication and critical operations

## Troubleshooting

### Common Issues

**OAuth Authorization Fails**
- Check client ID and secret configuration
- Verify redirect URL matches provider settings
- Ensure all required scopes are requested

**Token Refresh Fails**
- Verify refresh token is stored and encrypted
- Check if user revoked access
- Re-authenticate user if necessary

**Events Not Triggering**
- Verify polling service is running
- Check activation mode is set to POLL
- Ensure polling interval has elapsed
- Verify user has valid access token

**API Rate Limiting**
- Implement exponential backoff
- Cache frequently accessed data
- Use webhooks instead of polling where available

## Contributing

When adding new features to existing providers:

1. Update the provider's markdown documentation
2. Add comprehensive unit tests
3. Update database migrations if needed
4. Add Prometheus metrics for new operations
5. Update this README if adding a new provider

## Resources

- [OAuth 2.0 Specification](https://oauth.net/2/)
- [Spring Security OAuth2](https://spring.io/projects/spring-security-oauth)
- [REST API Best Practices](https://restfulapi.net/)
- [Prometheus Metrics](https://prometheus.io/docs/practices/naming/)

## License

See the main project LICENSE file.
