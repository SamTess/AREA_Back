# GitHub Provider - Technical Documentation

## Overview

The GitHub provider enables OAuth2 authentication and integration with GitHub services within the AREA platform. It allows users to authenticate using their GitHub account and interact with GitHub repositories through automated actions and event triggers.

## Architecture

### Core Components

#### 1. OAuth Service (`OAuthGithubService.java`)
- **Location**: `src/main/java/area/server/AREA_Back/service/OAuthGithubService.java`
- **Extends**: `OAuthService` (abstract base class)
- **Purpose**: Handles GitHub OAuth2 authentication and token management

**Key Features**:
- OAuth2 authorization code flow
- User authentication and registration
- Account linking to existing users
- Access token encryption and storage
- Prometheus metrics integration

#### 2. Action Service (`GitHubActionService.java`)
- **Location**: `src/main/java/area/server/AREA_Back/service/GitHubActionService.java`
- **Purpose**: Executes GitHub actions and monitors GitHub events

#### 3. Event Polling Service (`GitHubEventPollingService.java`)
- **Location**: `src/main/java/area/server/AREA_Back/service/GitHubEventPollingService.java`
- **Purpose**: Polls GitHub for new events at regular intervals
- **Scheduling**: Runs every 10 seconds (`@Scheduled(fixedRate = 10000)`)

## OAuth2 Implementation

### Configuration

The GitHub provider requires the following environment variables:

```properties
spring.security.oauth2.client.registration.github.client-id=<your-client-id>
spring.security.oauth2.client.registration.github.client-secret=<your-client-secret>
OAUTH_REDIRECT_BASE_URL=http://localhost:3000
```

### OAuth Flow

1. **Authorization Request**
   - User initiates OAuth flow via `/api/oauth/github/authorize`
   - Redirects to GitHub with required scopes: `user:email`
   - Callback URL: `{OAUTH_REDIRECT_BASE_URL}/oauth-callback`

2. **Token Exchange**
   - Authorization code is exchanged for access token
   - Token endpoint: `https://github.com/login/oauth/access_token`
   - Access token is encrypted before storage

3. **User Profile Retrieval**
   - Fetches user profile from `https://api.github.com/user`
   - Retrieves email from `https://api.github.com/user/emails` if not public
   - Creates or updates user account

4. **Session Creation**
   - Generates JWT access and refresh tokens
   - Sets secure HTTP-only cookies
   - Stores encrypted GitHub access token in database

### Account Linking

The provider supports linking GitHub accounts to existing authenticated users:

```java
public UserOAuthIdentity linkToExistingUser(User existingUser, String authorizationCode)
```

**Endpoint**: `/api/oauth-link/github/exchange`

**Features**:
- Links GitHub account to current session user
- Validates email uniqueness
- Prevents duplicate linking
- Stores GitHub profile metadata (name, login, avatar_url)

## Implemented Services

### Actions (Executable Operations)

#### 1. Create Issue
- **Action Key**: `create_issue`
- **Description**: Creates a new issue in a GitHub repository
- **Parameters**:
  - `repository` (required): Repository in format `owner/repo`
  - `title` (required): Issue title
  - `body` (optional): Issue description
  - `labels` (optional): Array of label names
  - `assignees` (optional): Array of usernames to assign

**Example**:
```json
{
  "repository": "owner/repo",
  "title": "Bug fix needed",
  "body": "Description of the issue",
  "labels": ["bug", "high-priority"],
  "assignees": ["username"]
}
```

#### 2. Comment on Issue
- **Action Key**: `comment_issue`
- **Description**: Adds a comment to an existing issue
- **Parameters**:
  - `repository` (required): Repository in format `owner/repo`
  - `issue_number` (required): Issue number
  - `comment` (required): Comment text

#### 3. Close Issue
- **Action Key**: `close_issue`
- **Description**: Closes an existing issue
- **Parameters**:
  - `repository` (required): Repository in format `owner/repo`
  - `issue_number` (required): Issue number
  - `comment` (optional): Closing comment

#### 4. Add Label to Issue
- **Action Key**: `add_label`
- **Description**: Adds labels to an issue
- **Parameters**:
  - `repository` (required): Repository in format `owner/repo`
  - `issue_number` (required): Issue number
  - `labels` (required): Array of label names

### Events (Triggers)

#### 1. New Issue
- **Event Key**: `new_issue`
- **Description**: Triggers when a new issue is created
- **Parameters**:
  - `repository` (required): Repository in format `owner/repo`
  - `labels` (optional): Filter by specific labels
- **Polling**: Checks for issues created since last poll
- **Output**:
  ```json
  {
    "issue_number": 123,
    "title": "Issue title",
    "body": "Issue description",
    "state": "open",
    "created_at": "2024-01-15T10:30:00Z",
    "user": "username",
    "labels": ["bug", "enhancement"],
    "url": "https://github.com/owner/repo/issues/123"
  }
  ```

#### 2. New Pull Request
- **Event Key**: `new_pull_request`
- **Description**: Triggers when a new pull request is created
- **Parameters**:
  - `repository` (required): Repository in format `owner/repo`
  - `base_branch` (optional): Filter by base branch
- **Polling**: Checks for PRs created since last poll
- **Output**:
  ```json
  {
    "pr_number": 45,
    "title": "PR title",
    "body": "PR description",
    "state": "open",
    "created_at": "2024-01-15T10:30:00Z",
    "user": "username",
    "head_branch": "feature-branch",
    "base_branch": "main",
    "url": "https://github.com/owner/repo/pull/45"
  }
  ```

#### 3. Push to Branch
- **Event Key**: `push_to_branch`
- **Description**: Triggers when commits are pushed to a branch
- **Parameters**:
  - `repository` (required): Repository in format `owner/repo`
  - `branch` (required): Branch name to monitor
- **Polling**: Checks for new commits since last poll
- **Output**:
  ```json
  {
    "sha": "abc123...",
    "message": "Commit message",
    "author": "username",
    "committed_date": "2024-01-15T10:30:00Z",
    "url": "https://github.com/owner/repo/commit/abc123",
    "branch": "main"
  }
  ```

## Token Management

### Token Storage
- Access tokens are encrypted using `TokenEncryptionService`
- Stored in `user_oauth_identities` table
- Associated with user and provider key ("github")

### Token Retrieval
The system supports two token sources:
1. **User OAuth Identity**: Personal GitHub account
2. **Service Account**: Shared service account for actions

```java
private String getGitHubToken(UUID userId) {
    // Check service accounts first
    Optional<String> serviceToken = serviceAccountService.getAccessToken(userId, "github");
    if (serviceToken.isPresent()) {
        return serviceToken.get();
    }
    
    // Fall back to user's OAuth identity
    // Returns decrypted token
}
```

### Token Metadata
Stored in `token_meta` JSONB field:
```json
{
  "name": "John Doe",
  "login": "johndoe",
  "avatar_url": "https://avatars.githubusercontent.com/u/12345"
}
```

## Monitoring & Metrics

The GitHub provider exposes Prometheus metrics:

### OAuth Metrics
- `github_oauth_login_success_total`: Successful login count
- `github_oauth_login_failure_total`: Failed login count
- `github_authenticate_calls_total`: Total authentication attempts
- `github_token_exchange_calls_total`: Token exchange attempts
- `github_token_exchange_failures_total`: Token exchange failures

### Action Metrics
- `github_actions_executed_total`: Total actions executed
- `github_actions_failed_total`: Failed action executions

### Event Polling Metrics
- `github_event_polling_cycles_total`: Total polling cycles
- `github_events_found_total`: Total events detected
- `github_event_polling_failures_total`: Polling failures

## API Integration

### GitHub API Endpoints Used

| Purpose | Endpoint | Method |
|---------|----------|--------|
| Get user profile | `/user` | GET |
| Get user emails | `/user/emails` | GET |
| List issues | `/repos/{owner}/{repo}/issues` | GET |
| Create issue | `/repos/{owner}/{repo}/issues` | POST |
| Comment on issue | `/repos/{owner}/{repo}/issues/{number}/comments` | POST |
| Update issue | `/repos/{owner}/{repo}/issues/{number}` | PATCH |
| Add labels | `/repos/{owner}/{repo}/issues/{number}/labels` | POST |
| List pull requests | `/repos/{owner}/{repo}/pulls` | GET |
| List commits | `/repos/{owner}/{repo}/commits` | GET |

### Authentication
All GitHub API requests use Bearer token authentication:
```
Authorization: Bearer <github-access-token>
```

## Database Schema

### user_oauth_identities Table
```sql
{
  id: UUID,
  user_id: UUID,
  provider: "github",
  provider_user_id: "12345678",
  access_token_enc: "encrypted_token",
  refresh_token_enc: null,  -- GitHub doesn't provide refresh tokens
  token_meta: JSONB,
  expires_at: null,
  created_at: TIMESTAMP,
  updated_at: TIMESTAMP
}
```

## Error Handling

### Common Errors

1. **Missing Token**
   - Occurs when user hasn't connected GitHub account
   - Returns: `RuntimeException("No GitHub token found for user: {userId}")`

2. **Invalid Repository**
   - Occurs when repository doesn't exist or user lacks access
   - HTTP 404 from GitHub API

3. **Rate Limiting**
   - GitHub API rate limits: 5,000 requests/hour (authenticated)
   - Should implement exponential backoff

4. **Token Expiration**
   - GitHub tokens don't expire by default
   - Manual revocation requires re-authentication

## Event Polling Strategy

### Polling Configuration
- **Default Interval**: 300 seconds (5 minutes)
- **Fixed Rate**: 10 seconds (scheduler interval)
- **Activation Mode**: POLL type must be enabled

### Polling Process
1. Service retrieves all active GitHub action instances
2. For each instance:
   - Checks if polling interval has elapsed
   - Calculates last check time
   - Calls appropriate event checker
   - Triggers executions for new events
3. Updates last poll timestamp

### Performance Optimization
- Uses `ConcurrentHashMap` for last poll times
- Transactional processing per action instance
- Skips disabled instances early

## Security Considerations

### Token Encryption
- All access tokens encrypted at rest
- Uses `TokenEncryptionService` with AES encryption
- Encryption key stored securely in environment

### Scope Minimization
- Requests only `user:email` scope for OAuth
- Additional scopes can be added as needed
- Users can revoke access via GitHub settings

### Account Validation
- Email verification required for authentication
- Duplicate account linking prevented
- Existing OAuth identities checked before creation

## Integration Example

### Frontend OAuth Initiation
```typescript
export const initiateOAuth = (provider: string): void => {
  localStorage.setItem('oauth_provider', provider.toLowerCase());
  const oauthUrl = `${API_CONFIG.baseURL}/api/oauth/${provider}/authorize`;
  window.location.href = oauthUrl;
};
```

### Backend OAuth Callback
```java
@GetMapping("/callback")
public ResponseEntity<?> handleCallback(
    @RequestParam("code") String code,
    HttpServletResponse response
) {
    OAuthLoginRequest request = new OAuthLoginRequest(code);
    AuthResponse authResponse = oauthGithubService.authenticate(request, response);
    return ResponseEntity.ok(authResponse);
}
```

## Testing

### Unit Tests Location
- `src/test/java/area/server/AREA_Back/service/GitHubActionServiceTest.java`

### Test Coverage
- Invalid action keys
- Missing tokens
- Event checking with various parameters
- Error handling scenarios

## Future Enhancements

### Potential Features
1. **Webhook Support**: Replace polling with GitHub webhooks for real-time events
2. **Branch Protection**: Actions to manage branch protection rules
3. **Repository Management**: Create, delete, and manage repositories
4. **Team Management**: Manage organization teams and permissions
5. **Gist Integration**: Create and manage gists
6. **GitHub Actions**: Trigger and monitor workflow runs
7. **Releases**: Create and manage releases
8. **Projects**: Manage GitHub Projects (v2)

### Performance Improvements
1. Implement webhook delivery for events
2. Cache frequently accessed repository data
3. Batch API requests where possible
4. Implement GraphQL API for complex queries

## References

- [GitHub OAuth Documentation](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps)
- [GitHub REST API](https://docs.github.com/en/rest)
- [Spring Security OAuth2 Client](https://docs.spring.io/spring-security/reference/servlet/oauth2/client/index.html)
