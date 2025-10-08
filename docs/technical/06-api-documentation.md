# API Documentation Guide

## Table of Contents
- [Overview](#overview)
- [OpenAPI Configuration](#openapi-configuration)
- [Authentication Endpoints](#authentication-endpoints)
- [Area Management Endpoints](#area-management-endpoints)
- [Service Management Endpoints](#service-management-endpoints)
- [OAuth Integration Endpoints](#oauth-integration-endpoints)
- [Webhook Endpoints](#webhook-endpoints)
- [Error Handling](#error-handling)
- [Request/Response Examples](#requestresponse-examples)

## Overview

The AREA backend provides a comprehensive REST API documented with OpenAPI 3.0 (Swagger). The API follows RESTful principles with consistent patterns for resource management, error handling, and authentication.

**Base URL**: `http://localhost:8080/api`  
**Documentation URL**: `http://localhost:8080/swagger-ui.html`  
**OpenAPI Spec**: `http://localhost:8080/v3/api-docs`

## OpenAPI Configuration

### Configuration Class
```java
@Configuration
public class OpenApiConfig {
    
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("AREA Backend API")
                .description("API for managing users, services, and areas in the AREA application")
                .version("1.0.0")
                .contact(new Contact()
                    .name("AREA Team")
                    .email("support@area.com"))
                .license(new License()
                    .name("MIT License")
                    .url("https://opensource.org/licenses/MIT")))
            .addSecurityItem(new SecurityRequirement().addList("cookieAuth"))
            .components(new Components()
                .addSecuritySchemes("cookieAuth", 
                    new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.COOKIE)
                        .name("authToken")));
    }
}
```

### Documentation Annotations
```java
@RestController
@RequestMapping("/api/areas")
@Tag(name = "Areas", description = "API for managing areas (automations)")
@Slf4j
public class AreaController {
    
    @PostMapping("/with-actions")
    @Operation(summary = "Create a new AREA automation with actions and reactions",
               description = "Creates a new AREA with specified actions (triggers) and reactions. "
                           + "Validates JSON schemas and creates necessary action instances.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "AREA created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data or validation failure"),
        @ApiResponse(responseCode = "404", description = "User, action definition, or service account not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<AreaResponse> createAreaWithActions(
            @Valid @RequestBody CreateAreaWithActionsRequest request,
            HttpServletRequest httpRequest) {
        // Implementation
    }
}
```

## Authentication Endpoints

### Register User
```http
POST /api/auth/register
Content-Type: application/json

{
    "email": "user@example.com",
    "password": "securePassword123"
}
```

**Response (201 Created):**
```json
{
    "accessToken": "jwt-access-token",
    "refreshToken": "jwt-refresh-token",
    "user": {
        "id": "123e4567-e89b-12d3-a456-426614174000",
        "email": "user@example.com",
        "isActive": true,
        "isAdmin": false,
        "createdAt": "2024-01-01T10:00:00Z"
    }
}
```

### Login User
```http
POST /api/auth/login
Content-Type: application/json

{
    "email": "user@example.com", 
    "password": "securePassword123"
}
```

**Response (200 OK):**
```json
{
    "accessToken": "jwt-access-token",
    "refreshToken": "jwt-refresh-token",
    "user": {
        "id": "123e4567-e89b-12d3-a456-426614174000",
        "email": "user@example.com",
        "isActive": true,
        "isAdmin": false,
        "lastLoginAt": "2024-01-01T10:30:00Z"
    }
}
```

### Refresh Token
```http
POST /api/auth/refresh
Content-Type: application/json

{
    "refreshToken": "jwt-refresh-token"
}
```

### Logout User
```http
POST /api/auth/logout
Cookie: authToken=jwt-access-token; refreshToken=jwt-refresh-token
```

### Get Current User
```http
GET /api/auth/me
Cookie: authToken=jwt-access-token
```

**Response (200 OK):**
```json
{
    "id": "123e4567-e89b-12d3-a456-426614174000",
    "email": "user@example.com",
    "isActive": true,
    "isAdmin": false,
    "avatarUrl": "https://example.com/avatar.jpg",
    "createdAt": "2024-01-01T10:00:00Z",
    "lastLoginAt": "2024-01-01T10:30:00Z"
}
```

## Area Management Endpoints

### Create Basic Area
```http
POST /api/areas
Content-Type: application/json
Cookie: authToken=jwt-access-token

{
    "name": "My First Area",
    "description": "Basic automation area",
    "userId": "123e4567-e89b-12d3-a456-426614174000"
}
```

**Response (201 Created):**
```json
{
    "id": "456e7890-e89b-12d3-a456-426614174001",
    "name": "My First Area",
    "description": "Basic automation area",
    "enabled": true,
    "user": {
        "id": "123e4567-e89b-12d3-a456-426614174000",
        "email": "user@example.com"
    },
    "actions": [],
    "reactions": [],
    "createdAt": "2024-01-01T11:00:00Z",
    "updatedAt": "2024-01-01T11:00:00Z"
}
```

### Create Area with Actions and Reactions
```http
POST /api/areas/with-actions
Content-Type: application/json
Cookie: authToken=jwt-access-token

{
    "name": "GitHub to Slack Integration",
    "description": "Send Slack notification when GitHub issue is created",
    "userId": "123e4567-e89b-12d3-a456-426614174000",
    "actions": [
        {
            "actionDefinitionId": "789e0123-e89b-12d3-a456-426614174002",
            "serviceAccountId": "abc12345-e89b-12d3-a456-426614174003",
            "parameters": {
                "repository": "owner/repo",
                "event_types": ["opened", "reopened"]
            }
        }
    ],
    "reactions": [
        {
            "actionDefinitionId": "def45678-e89b-12d3-a456-426614174004",
            "serviceAccountId": "ghi90123-e89b-12d3-a456-426614174005",
            "parameters": {
                "channel": "#notifications",
                "template": "New issue: {{issue.title}} - {{issue.url}}"
            }
        }
    ]
}
```

### Get User Areas
```http
GET /api/areas/user/123e4567-e89b-12d3-a456-426614174000?page=0&size=20&sortBy=createdAt&sortDir=desc
Cookie: authToken=jwt-access-token
```

**Response (200 OK):**
```json
[
    {
        "id": "456e7890-e89b-12d3-a456-426614174001",
        "name": "GitHub to Slack Integration",
        "description": "Send Slack notification when GitHub issue is created",
        "enabled": true,
        "actions": [
            {
                "actionDefinitionId": "789e0123-e89b-12d3-a456-426614174002",
                "actionDefinitionName": "GitHub Issue Created",
                "serviceKey": "github",
                "parameters": {
                    "repository": "owner/repo",
                    "event_types": ["opened", "reopened"]
                }
            }
        ],
        "reactions": [
            {
                "actionDefinitionId": "def45678-e89b-12d3-a456-426614174004",
                "actionDefinitionName": "Slack Send Message",
                "serviceKey": "slack",
                "parameters": {
                    "channel": "#notifications",
                    "template": "New issue: {{issue.title}} - {{issue.url}}"
                }
            }
        ],
        "createdAt": "2024-01-01T11:00:00Z",
        "updatedAt": "2024-01-01T11:00:00Z"
    }
]
```

### Get Area by ID
```http
GET /api/areas/456e7890-e89b-12d3-a456-426614174001
Cookie: authToken=jwt-access-token
```

### Update Area
```http
PUT /api/areas/456e7890-e89b-12d3-a456-426614174001
Content-Type: application/json
Cookie: authToken=jwt-access-token

{
    "name": "Updated Area Name",
    "description": "Updated description",
    "enabled": true
}
```

### Delete Area
```http
DELETE /api/areas/456e7890-e89b-12d3-a456-426614174001
Cookie: authToken=jwt-access-token
```

### Get All Areas (Paginated)
```http
GET /api/areas?page=0&size=20&sortBy=createdAt&sortDir=desc
Cookie: authToken=jwt-access-token
```

**Response (200 OK):**
```json
{
    "content": [
        {
            "id": "456e7890-e89b-12d3-a456-426614174001",
            "name": "GitHub to Slack Integration",
            "enabled": true,
            "user": {
                "id": "123e4567-e89b-12d3-a456-426614174000",
                "email": "user@example.com"
            },
            "createdAt": "2024-01-01T11:00:00Z"
        }
    ],
    "pageable": {
        "pageNumber": 0,
        "pageSize": 20,
        "sort": {
            "sorted": true,
            "direction": "DESC",
            "properties": ["createdAt"]
        }
    },
    "totalElements": 1,
    "totalPages": 1,
    "first": true,
    "last": true,
    "numberOfElements": 1
}
```

## Service Management Endpoints

### Get All Services
```http
GET /api/services?page=0&size=20&sortBy=name&sortDir=asc
```

**Response (200 OK):**
```json
{
    "content": [
        {
            "id": "service-123e-4567-e89b-12d3a456",
            "key": "github",
            "name": "GitHub",
            "auth": "OAUTH2",
            "docsUrl": "https://docs.github.com/en/rest",
            "iconLightUrl": "https://github.com/favicon.ico",
            "iconDarkUrl": "https://github.com/favicon-dark.ico",
            "isActive": true,
            "createdAt": "2024-01-01T00:00:00Z"
        },
        {
            "id": "service-456e-7890-e89b-12d3a456",
            "key": "slack",
            "name": "Slack",
            "auth": "OAUTH2",
            "docsUrl": "https://api.slack.com/web",
            "iconLightUrl": "https://slack.com/favicon.ico",
            "iconDarkUrl": "https://slack.com/favicon-dark.ico",
            "isActive": true,
            "createdAt": "2024-01-01T00:00:00Z"
        }
    ],
    "pageable": {
        "pageNumber": 0,
        "pageSize": 20
    },
    "totalElements": 2,
    "totalPages": 1
}
```

### Get Enabled Services
```http
GET /api/services/catalog/enabled
```

### Get Service by ID
```http
GET /api/services/service-123e-4567-e89b-12d3a456
```

### Get Action Definitions
```http
GET /api/action-definitions?page=0&size=50&serviceKey=github
```

**Response (200 OK):**
```json
{
    "content": [
        {
            "id": "789e0123-e89b-12d3-a456-426614174002",
            "key": "github-issue-created",
            "name": "GitHub Issue Created",
            "description": "Triggered when a new issue is created in a repository",
            "serviceKey": "github",
            "isEventCapable": true,
            "isExecutable": false,
            "inputSchema": {
                "type": "object",
                "properties": {
                    "repository": {
                        "type": "string",
                        "pattern": "^[a-zA-Z0-9_-]+/[a-zA-Z0-9_-]+$",
                        "description": "Repository in format owner/repo"
                    },
                    "event_types": {
                        "type": "array",
                        "items": {
                            "type": "string",
                            "enum": ["opened", "closed", "reopened", "edited"]
                        },
                        "description": "Issue events to monitor"
                    }
                },
                "required": ["repository"]
            },
            "outputSchema": {
                "type": "object",
                "properties": {
                    "issue": {
                        "type": "object",
                        "properties": {
                            "id": {"type": "number"},
                            "title": {"type": "string"},
                            "body": {"type": "string"},
                            "url": {"type": "string"},
                            "state": {"type": "string"},
                            "author": {"type": "string"}
                        }
                    }
                }
            }
        }
    ]
}
```

## OAuth Integration Endpoints

### Get OAuth Providers
```http
GET /api/oauth/providers
```

**Response (200 OK):**
```json
[
    {
        "key": "github",
        "label": "GitHub",
        "logoUrl": "https://github.com/favicon.ico",
        "authorizeUrl": "/api/oauth/github/authorize"
    },
    {
        "key": "google",
        "label": "Google",
        "logoUrl": "https://google.com/favicon.ico",
        "authorizeUrl": "/api/oauth/google/authorize"
    }
]
```

### OAuth Authorization
```http
GET /api/oauth/github/authorize
```

**Response (302 Found):**
```
Location: https://github.com/login/oauth/authorize?client_id=xxx&scope=repo&state=random-state
```

### OAuth Token Exchange
```http
POST /api/oauth/github/exchange
Content-Type: application/json

{
    "code": "authorization_code_from_github"
}
```

**Response (200 OK):**
```json
{
    "accessToken": "jwt-access-token",
    "refreshToken": "jwt-refresh-token", 
    "user": {
        "id": "123e4567-e89b-12d3-a456-426614174000",
        "email": "user@example.com",
        "avatarUrl": "https://avatars.githubusercontent.com/u/12345"
    }
}
```

## Webhook Endpoints

### GitHub Webhook
```http
POST /api/webhooks/github
X-GitHub-Event: issues
X-GitHub-Delivery: 12345678-1234-1234-1234-123456789abc
X-Hub-Signature-256: sha256=signature_hash
Content-Type: application/json

{
    "action": "opened",
    "issue": {
        "id": 123,
        "number": 1,
        "title": "Bug found in login system",
        "body": "When users try to login with special characters...",
        "state": "open",
        "html_url": "https://github.com/owner/repo/issues/1",
        "user": {
            "login": "username",
            "avatar_url": "https://avatars.githubusercontent.com/u/12345"
        }
    },
    "repository": {
        "id": 456,
        "name": "repo",
        "full_name": "owner/repo",
        "html_url": "https://github.com/owner/repo"
    }
}
```

**Response (200 OK):**
```json
{
    "message": "Webhook processed successfully",
    "deliveryId": "12345678-1234-1234-1234-123456789abc",
    "processedAt": "2024-01-01T12:00:00Z"
}
```

## Error Handling

### Standard Error Response Format
```json
{
    "error": "ERROR_CODE",
    "message": "Human-readable error message",
    "details": {
        "field": "specific_field",
        "rejectedValue": "invalid_value",
        "additionalInfo": "Extra context"
    },
    "timestamp": "2024-01-01T12:00:00Z",
    "path": "/api/areas"
}
```

### Common Error Codes

#### 400 Bad Request
```json
{
    "error": "VALIDATION_ERROR",
    "message": "Invalid request data",
    "details": {
        "field": "email",
        "rejectedValue": "invalid-email",
        "message": "Email should be valid"
    },
    "timestamp": "2024-01-01T12:00:00Z",
    "path": "/api/auth/register"
}
```

#### 401 Unauthorized
```json
{
    "error": "AUTHENTICATION_REQUIRED",
    "message": "Authentication token is required",
    "timestamp": "2024-01-01T12:00:00Z",
    "path": "/api/areas"
}
```

#### 403 Forbidden
```json
{
    "error": "ACCESS_DENIED",
    "message": "Insufficient permissions to access this resource",
    "details": {
        "requiredRole": "ADMIN",
        "currentRole": "USER"
    },
    "timestamp": "2024-01-01T12:00:00Z",
    "path": "/api/admin/users"
}
```

#### 404 Not Found
```json
{
    "error": "RESOURCE_NOT_FOUND",
    "message": "Area not found",
    "details": {
        "resourceType": "Area",
        "resourceId": "456e7890-e89b-12d3-a456-426614174001"
    },
    "timestamp": "2024-01-01T12:00:00Z",
    "path": "/api/areas/456e7890-e89b-12d3-a456-426614174001"
}
```

#### 409 Conflict
```json
{
    "error": "RESOURCE_CONFLICT",
    "message": "Email already registered",
    "details": {
        "field": "email",
        "conflictingValue": "user@example.com"
    },
    "timestamp": "2024-01-01T12:00:00Z",
    "path": "/api/auth/register"
}
```

#### 422 Unprocessable Entity
```json
{
    "error": "JSON_SCHEMA_VALIDATION_ERROR",
    "message": "Action parameters validation failed",
    "details": {
        "actionDefinitionId": "789e0123-e89b-12d3-a456-426614174002",
        "validationErrors": [
            {
                "field": "repository",
                "message": "Repository format must be owner/repo",
                "rejectedValue": "invalid-repo-name"
            }
        ]
    },
    "timestamp": "2024-01-01T12:00:00Z",
    "path": "/api/areas/with-actions"
}
```

#### 500 Internal Server Error
```json
{
    "error": "INTERNAL_SERVER_ERROR",
    "message": "An unexpected error occurred",
    "details": {
        "errorId": "err-123e4567-e89b-12d3-a456",
        "supportContact": "support@area.com"
    },
    "timestamp": "2024-01-01T12:00:00Z",
    "path": "/api/areas"
}
```

## Request/Response Examples

### Complete Area Creation Example

**Request:**
```http
POST /api/areas/with-actions
Content-Type: application/json
Cookie: authToken=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...

{
    "name": "GitHub Issue to Slack Notification",
    "description": "Automatically send Slack notifications when issues are created or updated in GitHub repositories",
    "userId": "123e4567-e89b-12d3-a456-426614174000",
    "actions": [
        {
            "actionDefinitionId": "github-issue-created-uuid",
            "serviceAccountId": "github-service-account-uuid",
            "parameters": {
                "repository": "myorg/myrepo",
                "event_types": ["opened", "reopened", "closed"]
            }
        }
    ],
    "reactions": [
        {
            "actionDefinitionId": "slack-send-message-uuid",
            "serviceAccountId": "slack-service-account-uuid",
            "parameters": {
                "channel": "#development",
                "template": "🚨 GitHub Issue {{action}}: **{{issue.title}}**\n\n{{issue.body}}\n\n🔗 [View Issue]({{issue.url}})\n👤 Reporter: {{issue.user.login}}"
            }
        }
    ]
}
```

**Response (201 Created):**
```json
{
    "id": "area-456e7890-e89b-12d3-a456-426614174001",
    "name": "GitHub Issue to Slack Notification",
    "description": "Automatically send Slack notifications when issues are created or updated in GitHub repositories",
    "enabled": true,
    "user": {
        "id": "123e4567-e89b-12d3-a456-426614174000",
        "email": "developer@example.com"
    },
    "actions": [
        {
            "id": "action-instance-789e0123-e89b-12d3",
            "actionDefinitionId": "github-issue-created-uuid",
            "actionDefinitionName": "GitHub Issue Created",
            "serviceKey": "github",
            "serviceAccountId": "github-service-account-uuid",
            "parameters": {
                "repository": "myorg/myrepo",
                "event_types": ["opened", "reopened", "closed"]
            },
            "activationMode": "WEBHOOK",
            "enabled": true
        }
    ],
    "reactions": [
        {
            "id": "reaction-instance-def45678-e89b-12d3",
            "actionDefinitionId": "slack-send-message-uuid",
            "actionDefinitionName": "Slack Send Message",
            "serviceKey": "slack",
            "serviceAccountId": "slack-service-account-uuid",
            "parameters": {
                "channel": "#development",
                "template": "🚨 GitHub Issue {{action}}: **{{issue.title}}**\n\n{{issue.body}}\n\n🔗 [View Issue]({{issue.url}})\n👤 Reporter: {{issue.user.login}}"
            },
            "enabled": true
        }
    ],
    "createdAt": "2024-01-01T12:00:00Z",
    "updatedAt": "2024-01-01T12:00:00Z"
}
```

### Service Discovery Example

**Request:**
```http
GET /about.json
```

**Response (200 OK):**
```json
{
    "client": {
        "host": "localhost:8080"
    },
    "server": {
        "current_time": "2024-01-01T12:00:00Z"
    },
    "services": [
        {
            "name": "GitHub",
            "key": "github",
            "actions": [
                {
                    "name": "Issue Created",
                    "key": "github-issue-created",
                    "description": "Triggered when a new issue is created"
                },
                {
                    "name": "Pull Request Opened",
                    "key": "github-pr-opened", 
                    "description": "Triggered when a pull request is opened"
                }
            ],
            "reactions": [
                {
                    "name": "Create Issue",
                    "key": "github-create-issue",
                    "description": "Creates a new issue in a repository"
                },
                {
                    "name": "Add Comment",
                    "key": "github-add-comment",
                    "description": "Adds a comment to an issue or pull request"
                }
            ]
        },
        {
            "name": "Slack",
            "key": "slack",
            "actions": [],
            "reactions": [
                {
                    "name": "Send Message",
                    "key": "slack-send-message",
                    "description": "Sends a message to a Slack channel"
                },
                {
                    "name": "Create Channel",
                    "key": "slack-create-channel",
                    "description": "Creates a new Slack channel"
                }
            ]
        }
    ]
}
```

## API Testing

### Using curl
```bash
# Register a new user
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email": "test@example.com", "password": "password123"}' \
  -c cookies.txt

# Create an area (using saved cookies)
curl -X POST http://localhost:8080/api/areas \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{
    "name": "Test Area",
    "description": "My test area",
    "userId": "user-uuid-here"
  }'
```

### Using JavaScript/Fetch
```javascript
// Register user
const registerResponse = await fetch('/api/auth/register', {
    method: 'POST',
    headers: {
        'Content-Type': 'application/json'
    },
    credentials: 'include', // Include cookies
    body: JSON.stringify({
        email: 'test@example.com',
        password: 'password123'
    })
});

const authData = await registerResponse.json();

// Create area
const areaResponse = await fetch('/api/areas/with-actions', {
    method: 'POST',
    headers: {
        'Content-Type': 'application/json'
    },
    credentials: 'include', // Include cookies for authentication
    body: JSON.stringify({
        name: 'GitHub to Slack Integration',
        description: 'Integration description',
        userId: authData.user.id,
        actions: [
            {
                actionDefinitionId: 'github-action-uuid',
                serviceAccountId: 'github-account-uuid',
                parameters: {
                    repository: 'owner/repo'
                }
            }
        ],
        reactions: [
            {
                actionDefinitionId: 'slack-reaction-uuid',
                serviceAccountId: 'slack-account-uuid',
                parameters: {
                    channel: '#notifications'
                }
            }
        ]
    })
});
```

## Rate Limiting (Planned)

Future API versions will include rate limiting:

```http
HTTP/1.1 429 Too Many Requests
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1640995200
Retry-After: 60

{
    "error": "RATE_LIMIT_EXCEEDED",
    "message": "Too many requests. Please try again later.",
    "retryAfter": 60
}
```

## API Versioning

The API uses URL versioning for future compatibility:

- Current: `/api/...` (v1 implicit)
- Future: `/api/v2/...`

Version headers will be supported:
```http
Accept: application/vnd.area.v2+json
```