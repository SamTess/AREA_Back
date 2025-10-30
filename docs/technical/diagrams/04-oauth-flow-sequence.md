# OAuth Authentication Flow - Sequence Diagram

This diagram shows the complete OAuth 2.0 authentication flow for user login and service integration.

## User Authentication with OAuth

```mermaid
sequenceDiagram
    actor User
    participant Frontend
    participant Backend
    participant OAuthService
    participant Provider as OAuth Provider (Google/GitHub)
    participant Database

    User->>Frontend: Click "Login with Google"
    Frontend->>Backend: GET /oauth/authorize/{provider}
    Backend->>OAuthService: initiateOAuthFlow(provider)
    OAuthService->>OAuthService: Generate state token
    OAuthService->>OAuthService: Build authorization URL
    OAuthService-->>Backend: Authorization URL + state
    Backend-->>Frontend: Redirect URL
    Frontend->>Provider: Redirect to OAuth provider
    Provider->>User: Show consent screen
    User->>Provider: Approve access
    Provider->>Frontend: Redirect to callback with code
    
    Frontend->>Backend: GET /oauth/callback/{provider}?code=xxx&state=yyy
    Backend->>OAuthService: handleOAuthCallback(provider, code, state)
    OAuthService->>OAuthService: Verify state token
    
    OAuthService->>Provider: POST /oauth/token (exchange code)
    Provider-->>OAuthService: Access token + user info
    
    OAuthService->>Database: Find user by provider ID
    
    alt User exists
        Database-->>OAuthService: Existing user
        OAuthService->>Database: Update last login
    else New user
        OAuthService->>Database: Create new User
        OAuthService->>Database: Create UserOAuthIdentity
        Database-->>OAuthService: New user created
    end
    
    OAuthService->>OAuthService: Generate JWT token
    OAuthService-->>Backend: UserResponse + JWT
    Backend-->>Frontend: JWT token + user data
    Frontend->>Frontend: Store JWT in localStorage
    Frontend-->>User: Redirect to dashboard
```

## Service Account Connection (OAuth for Third-Party Services)

```mermaid
sequenceDiagram
    actor User
    participant Frontend
    participant Backend
    participant ServiceAccountService
    participant OAuthService
    participant Provider as Third-Party Service (GitHub/Slack)
    participant Database

    User->>Frontend: Connect GitHub service
    Frontend->>Backend: POST /services/{serviceId}/connect
    Backend->>Backend: Verify JWT & get userId
    Backend->>OAuthService: initiateOAuthFlow("github", scopes)
    OAuthService->>OAuthService: Generate state with userId + serviceId
    OAuthService-->>Backend: Authorization URL
    Backend-->>Frontend: Authorization URL
    Frontend->>Provider: Redirect to GitHub OAuth
    Provider->>User: Show permissions screen
    User->>Provider: Approve permissions
    Provider->>Frontend: Redirect with code
    
    Frontend->>Backend: GET /oauth/callback/service/github?code=xxx&state=yyy
    Backend->>OAuthService: handleServiceOAuthCallback(code, state)
    OAuthService->>OAuthService: Decode state (userId, serviceId)
    
    OAuthService->>Provider: POST /oauth/token (exchange code)
    Provider-->>OAuthService: Access + Refresh tokens
    
    OAuthService->>Provider: GET /user (get account info)
    Provider-->>OAuthService: User account details
    
    OAuthService->>ServiceAccountService: createServiceAccount(tokens, accountInfo)
    ServiceAccountService->>Database: Check existing account
    
    alt Account exists
        Database-->>ServiceAccountService: Existing ServiceAccount
        ServiceAccountService->>Database: Update tokens & metadata
    else New account
        ServiceAccountService->>Database: Create new ServiceAccount
        Database-->>ServiceAccountService: ServiceAccount created
    end
    
    ServiceAccountService-->>Backend: ServiceAccount details
    Backend-->>Frontend: Connection successful
    Frontend-->>User: Show "GitHub connected" ✓
```

## Token Refresh Flow

```mermaid
sequenceDiagram
    participant AreaExecutionService
    participant ActionExecutor
    participant ServiceAccountService
    participant Provider as Third-Party API
    participant Database

    AreaExecutionService->>ActionExecutor: execute(actionInstance, context)
    ActionExecutor->>ActionExecutor: Get ServiceAccount
    ActionExecutor->>ActionExecutor: Check token expiration
    
    alt Token expired or about to expire
        ActionExecutor->>ServiceAccountService: refreshServiceAccount(accountId)
        ServiceAccountService->>Database: Get ServiceAccount with refresh token
        Database-->>ServiceAccountService: ServiceAccount data
        
        ServiceAccountService->>Provider: POST /oauth/token (refresh)
        Note over ServiceAccountService,Provider: grant_type=refresh_token
        Provider-->>ServiceAccountService: New access + refresh tokens
        
        ServiceAccountService->>Database: Update tokens & expiration
        Database-->>ServiceAccountService: Updated
        ServiceAccountService-->>ActionExecutor: New access token
    else Token valid
        ActionExecutor->>ActionExecutor: Use existing access token
    end
    
    ActionExecutor->>Provider: API Request with access token
    
    alt API call successful
        Provider-->>ActionExecutor: Response data
        ActionExecutor-->>AreaExecutionService: Execution result
    else Token invalid (401)
        Provider-->>ActionExecutor: 401 Unauthorized
        ActionExecutor->>ServiceAccountService: Force token refresh
        ServiceAccountService->>Provider: Refresh token request
        Provider-->>ServiceAccountService: New tokens
        ServiceAccountService->>Database: Update tokens
        ActionExecutor->>Provider: Retry API call
        Provider-->>ActionExecutor: Response data
        ActionExecutor-->>AreaExecutionService: Execution result
    end
```

## Key Points

### User OAuth Flow
1. State token prevents CSRF attacks
2. User approves permissions on provider site
3. Code is exchanged for access token server-side
4. User account is created or linked with OAuth identity
5. JWT token is generated for session management

### Service Account OAuth Flow
1. State includes userId and serviceId for context
2. Scopes request specific permissions needed for actions
3. Both access and refresh tokens are stored
4. Account identifier links to provider account
5. Tokens are automatically refreshed when expired

### Security Considerations
- State tokens prevent CSRF attacks
- Authorization codes are single-use
- Access tokens have limited lifetime
- Refresh tokens enable automatic renewal
- All OAuth operations are server-side to protect secrets
