# Authentication System Class Diagram

This diagram focuses on the authentication and authorization components of the system.

```mermaid
classDiagram
    class User {
        -UUID id
        -String email
        -String username
        -Boolean isActive
        -Boolean isAdmin
        -Boolean isVerified
        -LocalDateTime lastLoginAt
        -List~UserOAuthIdentity~ oauthIdentities
        -List~ServiceAccount~ serviceAccounts
    }

    class UserOAuthIdentity {
        -UUID id
        -String provider
        -String providerUserId
        -String email
        -Map~String,Object~ profile
        -LocalDateTime createdAt
        -User user
    }

    class ServiceAccount {
        -UUID id
        -String accountIdentifier
        -String accessToken
        -String refreshToken
        -LocalDateTime tokenExpiresAt
        -Map~String,Object~ metadata
        -Boolean isActive
        -User user
        -Service service
    }

    class Service {
        -UUID id
        -String key
        -String name
        -AuthType auth
        -String docsUrl
        -Boolean isActive
    }

    class JwtService {
        <<service>>
        +String generateToken(User user)
        +Claims validateToken(String token)
        +UUID extractUserId(String token)
        +Boolean isTokenExpired(String token)
    }

    class AuthService {
        <<service>>
        +UserResponse login(LoginRequest request)
        +UserResponse register(RegisterRequest request)
        +void logout(UUID userId)
        +UserResponse refreshToken(String refreshToken)
    }

    class OAuthService {
        <<service>>
        +String initiateOAuthFlow(String provider)
        +UserResponse handleOAuthCallback(String provider, String code)
        +UserOAuthIdentity linkOAuthIdentity(UUID userId, String provider, String code)
    }

    class ServiceAccountService {
        <<service>>
        +ServiceAccount createServiceAccount(UUID userId, UUID serviceId, OAuthTokens tokens)
        +ServiceAccount refreshServiceAccount(UUID accountId)
        +void revokeServiceAccount(UUID accountId)
        +String getValidAccessToken(UUID accountId)
    }

    class SecurityConfig {
        <<configuration>>
        +SecurityFilterChain filterChain()
        +PasswordEncoder passwordEncoder()
        +AuthenticationManager authManager()
    }

    class JwtAuthFilter {
        <<filter>>
        +void doFilterInternal(HttpServletRequest request, HttpServletResponse response)
        -String extractToken(HttpServletRequest request)
        -void setAuthenticationContext(String token)
    }

    %% Relationships
    User "1" --> "*" UserOAuthIdentity : has
    User "1" --> "*" ServiceAccount : owns
    ServiceAccount "*" --> "1" Service : connects_to

    AuthService --> User : manages
    AuthService --> JwtService : uses
    
    OAuthService --> UserOAuthIdentity : creates
    OAuthService --> User : authenticates
    
    ServiceAccountService --> ServiceAccount : manages
    ServiceAccountService --> Service : integrates
    
    JwtAuthFilter --> JwtService : validates_with
    SecurityConfig --> JwtAuthFilter : configures

    %% Enumerations
    class AuthType {
        <<enumeration>>
        OAUTH2
        APIKEY
        NONE
    }

    Service --> AuthType : authenticates_via
```

## Component Descriptions

### Authentication Services

#### AuthService
Handles user authentication (login/register) and JWT token generation. Manages user sessions and token refresh.

#### OAuthService
Manages OAuth 2.0 flows for user authentication via external providers (Google, GitHub). Creates and links OAuth identities to users.

#### ServiceAccountService
Manages service-level OAuth credentials for accessing third-party APIs. Handles token refresh and revocation.

#### JwtService
Generates and validates JWT tokens for securing API endpoints. Extracts user claims and checks token expiration.

### Security Components

#### SecurityConfig
Spring Security configuration defining authentication rules, password encoding, and security filter chains.

#### JwtAuthFilter
Request filter that intercepts HTTP requests, extracts JWT tokens, validates them, and sets the security context.

### Data Models

#### UserOAuthIdentity
Stores OAuth credentials from external providers used for user login (Google, GitHub, etc.).

#### ServiceAccount
Stores OAuth credentials for third-party service integrations, allowing the system to act on behalf of users.

## Authentication Flow

1. **User Login**: User provides credentials → AuthService validates → JwtService generates token
2. **OAuth Login**: User redirects to provider → OAuthService handles callback → Creates/links UserOAuthIdentity
3. **API Request**: JwtAuthFilter intercepts → JwtService validates token → Sets authentication context
4. **Service Integration**: User authorizes service → ServiceAccountService creates ServiceAccount → Stores tokens
5. **Token Refresh**: ServiceAccountService checks expiration → Refreshes tokens automatically

## Security Features

- JWT-based stateless authentication
- OAuth 2.0 integration for user login
- Separate service accounts for API integrations
- Token refresh mechanism
- Role-based access control (isAdmin flag)
- Account verification system (isVerified flag)
