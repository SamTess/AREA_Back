# Google Provider - Technical Documentation

## Overview

The Google provider enables OAuth2 authentication and integration with Google Workspace services within the AREA platform. It allows users to authenticate using their Google account and interact with Gmail, Google Calendar, Google Drive, and Google Sheets through automated actions and event triggers.

## Architecture

### Core Components

#### 1. OAuth Service (`OAuthGoogleService.java`)
- **Location**: `src/main/java/area/server/AREA_Back/service/OAuthGoogleService.java`
- **Extends**: `OAuthService` (abstract base class)
- **Purpose**: Handles Google OAuth2 authentication and token management

**Key Features**:
- OAuth2 authorization code flow
- User authentication and registration
- Account linking to existing users
- Access token and refresh token management
- Token encryption and storage
- Token expiration tracking
- Prometheus metrics integration

#### 2. Action Service (`GoogleActionService.java`)
- **Location**: `src/main/java/area/server/AREA_Back/service/GoogleActionService.java`
- **Purpose**: Executes Google Workspace actions and monitors Google events

#### 3. Event Polling Service (`GoogleEventPollingService.java`)
- **Location**: `src/main/java/area/server/AREA_Back/service/GoogleEventPollingService.java`
- **Purpose**: Polls Google services for new events at regular intervals
- **Scheduling**: Runs every 10 seconds (`@Scheduled(fixedRate = 10000)`)

## OAuth2 Implementation

### Configuration

The Google provider requires the following environment variables:

```properties
spring.security.oauth2.client.registration.google.client-id=<your-client-id>
spring.security.oauth2.client.registration.google.client-secret=<your-client-secret>
OAUTH_REDIRECT_BASE_URL=http://localhost:3000
```

### OAuth Scopes

The provider requests comprehensive access to Google Workspace services:

```
openid
email
profile
https://www.googleapis.com/auth/gmail.readonly
https://www.googleapis.com/auth/gmail.send
https://www.googleapis.com/auth/gmail.modify
https://www.googleapis.com/auth/calendar
https://www.googleapis.com/auth/calendar.events
https://www.googleapis.com/auth/drive
https://www.googleapis.com/auth/drive.file
https://www.googleapis.com/auth/spreadsheets
```

**Additional Parameters**:
- `access_type=offline`: Requests refresh token for offline access
- `prompt=consent`: Forces consent screen to ensure refresh token

### OAuth Flow

1. **Authorization Request**
   - User initiates OAuth flow via `/api/oauth/google/authorize`
   - Redirects to Google OAuth consent screen
   - Callback URL: `{OAUTH_REDIRECT_BASE_URL}/oauth-callback`

2. **Token Exchange**
   - Authorization code is exchanged for access token and refresh token
   - Token endpoint: `https://oauth2.googleapis.com/token`
   - Both tokens are encrypted before storage

3. **User Profile Retrieval**
   - Fetches user profile from `https://www.googleapis.com/oauth2/v2/userinfo`
   - Extracts email, name, picture, locale, and verification status
   - Creates or updates user account

4. **Session Creation**
   - Generates JWT access and refresh tokens
   - Sets secure HTTP-only cookies
   - Stores encrypted Google access token and refresh token in database
   - Records token expiration time

### Token Management

#### Access Token
- Encrypted and stored in `access_token_enc` field
- Expires after specified duration (typically 3600 seconds)
- Automatically refreshed using refresh token when needed

#### Refresh Token
- Encrypted and stored in `refresh_token_enc` field
- Long-lived token for obtaining new access tokens
- Only granted with `access_type=offline` and `prompt=consent`

#### Expiration Tracking
```java
private LocalDateTime calculateExpirationTime(Integer expiresIn) {
    if (expiresIn == null) {
        return LocalDateTime.now().plusHours(1);
    }
    return LocalDateTime.now().plusSeconds(expiresIn);
}
```

### Account Linking

The provider supports linking Google accounts to existing authenticated users:

```java
public UserOAuthIdentity linkToExistingUser(User existingUser, String authorizationCode)
```

**Endpoint**: `/api/oauth-link/google/exchange`

**Features**:
- Links Google account to current session user
- Validates email uniqueness
- Prevents duplicate linking
- Stores Google profile metadata (name, given_name, family_name, picture, locale, verified_email)

## Implemented Services

### Gmail Services

#### Actions

##### 1. Send Email
- **Action Key**: `gmail_send_email`
- **Description**: Sends an email via Gmail
- **Parameters**:
  - `to` (required): Recipient email address
  - `subject` (required): Email subject
  - `body` (required): Email body (plain text)
  - `cc` (optional): CC recipients (comma-separated)
  - `bcc` (optional): BCC recipients (comma-separated)

**Example**:
```json
{
  "to": "recipient@example.com",
  "subject": "Meeting Reminder",
  "body": "Don't forget our meeting tomorrow at 10 AM.",
  "cc": "manager@example.com"
}
```

**Implementation**: Encodes email in RFC 2822 format, Base64 URL-safe encoding

##### 2. Add Gmail Label
- **Action Key**: `gmail_add_label`
- **Description**: Adds a label to a Gmail message
- **Parameters**:
  - `message_id` (required): Gmail message ID
  - `label_id` (required): Gmail label ID

#### Events

##### 1. New Email
- **Event Key**: `gmail_new_email`
- **Description**: Triggers when a new email is received
- **Parameters**:
  - `from` (optional): Filter by sender email
  - `subject_contains` (optional): Filter by subject keywords
  - `label_id` (optional): Filter by label
- **Polling**: Checks for emails received since last poll
- **Output**:
  ```json
  {
    "message_id": "18c1a2b3c4d5e6f7",
    "thread_id": "18c1a2b3c4d5e6f7",
    "from": "sender@example.com",
    "to": ["recipient@example.com"],
    "subject": "Important notification",
    "snippet": "Email preview text...",
    "received_at": "2024-01-15T10:30:00Z",
    "labels": ["INBOX", "UNREAD"]
  }
  ```

### Google Calendar Services

#### Actions

##### 1. Create Event
- **Action Key**: `calendar_create_event`
- **Description**: Creates a new calendar event
- **Parameters**:
  - `calendar_id` (optional): Calendar ID (defaults to "primary")
  - `summary` (required): Event title
  - `description` (optional): Event description
  - `start_time` (required): Start time (ISO 8601 format)
  - `end_time` (required): End time (ISO 8601 format)
  - `location` (optional): Event location
  - `attendees` (optional): Array of attendee emails

**Example**:
```json
{
  "summary": "Team Meeting",
  "description": "Weekly sync-up",
  "start_time": "2024-01-20T14:00:00Z",
  "end_time": "2024-01-20T15:00:00Z",
  "location": "Conference Room A",
  "attendees": ["team@example.com"]
}
```

##### 2. Delete Event
- **Action Key**: `calendar_delete_event`
- **Description**: Deletes a calendar event
- **Parameters**:
  - `calendar_id` (optional): Calendar ID (defaults to "primary")
  - `event_id` (required): Event ID to delete

#### Events

##### 1. New Event
- **Event Key**: `calendar_new_event`
- **Description**: Triggers when a new calendar event is created
- **Parameters**:
  - `calendar_id` (optional): Calendar ID to monitor
- **Output**:
  ```json
  {
    "event_id": "abc123xyz",
    "summary": "Team Meeting",
    "description": "Weekly sync-up",
    "start_time": "2024-01-20T14:00:00Z",
    "end_time": "2024-01-20T15:00:00Z",
    "location": "Conference Room A",
    "creator": "user@example.com",
    "attendees": ["team@example.com"],
    "created_at": "2024-01-15T10:30:00Z"
  }
  ```

##### 2. Event Starting Soon
- **Event Key**: `calendar_event_starting`
- **Description**: Triggers when an event is about to start
- **Parameters**:
  - `calendar_id` (optional): Calendar ID to monitor
  - `minutes_before` (optional): Minutes before event start (default: 15)
- **Output**: Similar to new event output

### Google Drive Services

#### Actions

##### 1. Create Folder
- **Action Key**: `drive_create_folder`
- **Description**: Creates a new folder in Google Drive
- **Parameters**:
  - `name` (required): Folder name
  - `parent_id` (optional): Parent folder ID (defaults to root)

**Example**:
```json
{
  "name": "Project Files",
  "parent_id": "root"
}
```

##### 2. Upload File
- **Action Key**: `drive_upload_file`
- **Description**: Uploads a file to Google Drive
- **Parameters**:
  - `name` (required): File name
  - `content` (required): File content (Base64 encoded)
  - `mime_type` (required): MIME type
  - `parent_id` (optional): Parent folder ID

##### 3. Share File
- **Action Key**: `drive_share_file`
- **Description**: Shares a Drive file with specific users
- **Parameters**:
  - `file_id` (required): File ID to share
  - `email` (required): Email of user to share with
  - `role` (required): Permission role ("reader", "writer", "commenter")

**Example**:
```json
{
  "file_id": "1abc2def3ghi",
  "email": "colleague@example.com",
  "role": "writer"
}
```

#### Events

##### 1. New File
- **Event Key**: `drive_new_file`
- **Description**: Triggers when a new file is created
- **Parameters**:
  - `folder_id` (optional): Monitor specific folder
  - `mime_type` (optional): Filter by file type
- **Output**:
  ```json
  {
    "file_id": "1abc2def3ghi",
    "name": "Document.pdf",
    "mime_type": "application/pdf",
    "created_time": "2024-01-15T10:30:00Z",
    "modified_time": "2024-01-15T10:30:00Z",
    "size": 102400,
    "owner": "user@example.com",
    "web_view_link": "https://drive.google.com/file/d/1abc2def3ghi"
  }
  ```

##### 2. File Modified
- **Event Key**: `drive_file_modified`
- **Description**: Triggers when a file is modified
- **Parameters**:
  - `folder_id` (optional): Monitor specific folder
  - `file_id` (optional): Monitor specific file
- **Output**: Similar to new file output with modification details

### Google Sheets Services

#### Actions

##### 1. Add Row
- **Action Key**: `sheets_add_row`
- **Description**: Appends a row to a Google Sheet
- **Parameters**:
  - `spreadsheet_id` (required): Spreadsheet ID
  - `sheet_name` (optional): Sheet name (defaults to first sheet)
  - `values` (required): Array of cell values

**Example**:
```json
{
  "spreadsheet_id": "1abc2def3ghi",
  "sheet_name": "Sheet1",
  "values": ["John Doe", "john@example.com", "2024-01-15"]
}
```

##### 2. Update Cell
- **Action Key**: `sheets_update_cell`
- **Description**: Updates a specific cell value
- **Parameters**:
  - `spreadsheet_id` (required): Spreadsheet ID
  - `sheet_name` (optional): Sheet name
  - `cell` (required): Cell reference (e.g., "A1")
  - `value` (required): New cell value

**Example**:
```json
{
  "spreadsheet_id": "1abc2def3ghi",
  "sheet_name": "Sheet1",
  "cell": "B5",
  "value": "Updated Value"
}
```

##### 3. Create Spreadsheet
- **Action Key**: `sheets_create_spreadsheet`
- **Description**: Creates a new Google Spreadsheet
- **Parameters**:
  - `title` (required): Spreadsheet title
  - `sheet_names` (optional): Array of sheet names

#### Events

##### 1. Row Added
- **Event Key**: `sheets_row_added`
- **Description**: Triggers when a new row is added to a sheet
- **Parameters**:
  - `spreadsheet_id` (required): Spreadsheet ID to monitor
  - `sheet_name` (optional): Specific sheet to monitor
- **Output**:
  ```json
  {
    "spreadsheet_id": "1abc2def3ghi",
    "sheet_name": "Sheet1",
    "row_index": 42,
    "values": ["Data1", "Data2", "Data3"],
    "added_at": "2024-01-15T10:30:00Z"
  }
  ```

## API Integration

### Google API Endpoints Used

| Service | Purpose | Endpoint | Method |
|---------|---------|----------|--------|
| OAuth | Get user info | `/oauth2/v2/userinfo` | GET |
| Gmail | List messages | `/gmail/v1/users/me/messages` | GET |
| Gmail | Get message | `/gmail/v1/users/me/messages/{id}` | GET |
| Gmail | Send message | `/gmail/v1/users/me/messages/send` | POST |
| Gmail | Modify message | `/gmail/v1/users/me/messages/{id}/modify` | POST |
| Calendar | List events | `/calendar/v3/calendars/{calendarId}/events` | GET |
| Calendar | Create event | `/calendar/v3/calendars/{calendarId}/events` | POST |
| Calendar | Delete event | `/calendar/v3/calendars/{calendarId}/events/{eventId}` | DELETE |
| Drive | List files | `/drive/v3/files` | GET |
| Drive | Create file | `/drive/v3/files` | POST |
| Drive | Create permission | `/drive/v3/files/{fileId}/permissions` | POST |
| Sheets | Get values | `/sheets/v4/spreadsheets/{spreadsheetId}/values/{range}` | GET |
| Sheets | Append values | `/sheets/v4/spreadsheets/{spreadsheetId}/values/{range}:append` | POST |
| Sheets | Update values | `/sheets/v4/spreadsheets/{spreadsheetId}/values/{range}` | PUT |
| Sheets | Create spreadsheet | `/sheets/v4/spreadsheets` | POST |

### Authentication
All Google API requests use Bearer token authentication:
```
Authorization: Bearer <google-access-token>
```

## Token Management

### Token Storage
- Access tokens and refresh tokens are encrypted using `TokenEncryptionService`
- Stored in `user_oauth_identities` table
- Associated with user and provider key ("google")

### Token Retrieval
The system supports two token sources:
1. **User OAuth Identity**: Personal Google account
2. **Service Account**: Shared service account for actions

```java
private String getGoogleToken(UUID userId) {
    // Check service accounts first
    Optional<String> serviceToken = serviceAccountService.getAccessToken(userId, "google");
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
  "given_name": "John",
  "family_name": "Doe",
  "picture": "https://lh3.googleusercontent.com/...",
  "locale": "en",
  "verified_email": true
}
```

## Monitoring & Metrics

The Google provider exposes Prometheus metrics:

### OAuth Metrics
- `google_oauth_login_success_total`: Successful login count
- `google_oauth_login_failure_total`: Failed login count
- `google_authenticate_calls_total`: Total authentication attempts
- `google_token_exchange_calls_total`: Token exchange attempts
- `google_token_exchange_failures_total`: Token exchange failures

### Action Metrics
- `google_actions_executed_total`: Total actions executed
- `google_actions_failed_total`: Failed action executions

### Event Polling Metrics
- `google_event_polling_cycles_total`: Total polling cycles
- `google_events_found_total`: Total events detected
- `google_event_polling_failures_total`: Polling failures

## Database Schema

### user_oauth_identities Table
```sql
{
  id: UUID,
  user_id: UUID,
  provider: "google",
  provider_user_id: "123456789012345678901",
  access_token_enc: "encrypted_access_token",
  refresh_token_enc: "encrypted_refresh_token",
  token_meta: JSONB,
  expires_at: TIMESTAMP,  -- Access token expiration
  created_at: TIMESTAMP,
  updated_at: TIMESTAMP
}
```

## Error Handling

### Common Errors

1. **Missing Token**
   - Occurs when user hasn't connected Google account
   - Returns: `RuntimeException("No Google token found for user: {userId}")`

2. **Token Expired**
   - Access token expired (after ~1 hour)
   - Should automatically refresh using refresh token
   - Re-authentication required if refresh token is invalid

3. **Insufficient Permissions**
   - Occurs when required OAuth scopes not granted
   - HTTP 403 from Google API
   - User must re-authorize with additional scopes

4. **Rate Limiting**
   - Google API quotas vary by service
   - Gmail: 250 quota units/user/second
   - Calendar: 500 quota units/user/100 seconds
   - Drive: 1,000 quota units/user/100 seconds
   - Should implement exponential backoff

5. **Invalid Request**
   - Malformed parameters or missing required fields
   - HTTP 400 from Google API

## Event Polling Strategy

### Polling Configuration
- **Default Interval**: 300 seconds (5 minutes)
- **Fixed Rate**: 10 seconds (scheduler interval)
- **Activation Mode**: POLL type must be enabled

### Polling Process
1. Service retrieves all active Google action instances
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
- Implements pagination for large result sets

## Security Considerations

### Token Encryption
- All access tokens and refresh tokens encrypted at rest
- Uses `TokenEncryptionService` with AES encryption
- Encryption key stored securely in environment

### Scope Minimization
- Requests only necessary scopes for implemented features
- Additional scopes require code changes
- Users can revoke access via Google Account settings

### Account Validation
- Email verification required for authentication
- Verified email status checked from Google
- Duplicate account linking prevented
- Existing OAuth identities checked before creation

### Data Privacy
- Email content never stored permanently
- Only metadata and IDs cached temporarily
- Calendar event details accessed on-demand
- Drive file content not stored in AREA database

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
    AuthResponse authResponse = oauthGoogleService.authenticate(request, response);
    return ResponseEntity.ok(authResponse);
}
```

## Testing

### Unit Tests Location
- Test files should be created in `src/test/java/area/server/AREA_Back/service/GoogleActionServiceTest.java`

### Test Coverage
- Invalid action keys
- Missing tokens
- Token expiration and refresh
- Event checking with various parameters
- Error handling scenarios
- API rate limiting scenarios

## Future Enhancements

### Potential Features

#### Gmail
1. **Search Messages**: Advanced search with filters
2. **Create Draft**: Create draft emails
3. **Manage Filters**: Create and manage Gmail filters
4. **Manage Labels**: Create, update, delete labels
5. **Attachments**: Handle email attachments
6. **HTML Emails**: Support HTML email composition

#### Calendar
1. **Recurring Events**: Support recurring event patterns
2. **Event Reminders**: Manage event reminders
3. **Calendar Sharing**: Share calendars with users
4. **Free/Busy**: Check availability
5. **Time Zone Support**: Better handling of time zones

#### Drive
1. **File Search**: Advanced search capabilities
2. **Folder Management**: Move, rename, delete folders
3. **File Versions**: Access and manage file versions
4. **Comments**: Add and manage file comments
5. **Export Formats**: Export Google Docs/Sheets/Slides

#### Sheets
1. **Formulas**: Support cell formulas
2. **Formatting**: Cell formatting and styles
3. **Charts**: Create and manage charts
4. **Data Validation**: Set cell validation rules
5. **Batch Operations**: Bulk cell updates

#### Additional Services
1. **Google Tasks**: Task management integration
2. **Google Keep**: Notes integration
3. **Google Contacts**: Contact management
4. **YouTube**: Video management and monitoring

### Performance Improvements
1. Implement webhook/push notifications where available
2. Cache frequently accessed data
3. Batch API requests where possible
4. Implement exponential backoff for rate limiting
5. Use partial responses to reduce payload size

## References

- [Google OAuth 2.0 Documentation](https://developers.google.com/identity/protocols/oauth2)
- [Gmail API Reference](https://developers.google.com/gmail/api/reference/rest)
- [Google Calendar API Reference](https://developers.google.com/calendar/api/v3/reference)
- [Google Drive API Reference](https://developers.google.com/drive/api/v3/reference)
- [Google Sheets API Reference](https://developers.google.com/sheets/api/reference/rest)
- [Google API Client Libraries](https://developers.google.com/api-client-library)
- [Spring Security OAuth2 Client](https://docs.spring.io/spring-security/reference/servlet/oauth2/client/index.html)
