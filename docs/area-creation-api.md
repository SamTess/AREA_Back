# AREA Creation API Implementation

## Overview

This implementation adds a new API endpoint `/api/areas/with-actions` that allows creating AREA automations with actions and reactions in a single request. The implementation includes JSON schema validation and JSONB storage for flexibility.

## Features Implemented

### 1. Enhanced AREA Entity
- Added JSONB columns `actions` and `reactions` to store action/reaction configurations
- Maintains backward compatibility with existing basic AREA creation

### 2. New DTOs
- `CreateAreaWithActionsRequest`: Main request DTO for AREA creation
- `AreaActionRequest`: DTO for action (trigger) configuration
- `AreaReactionRequest`: DTO for reaction configuration
- Enhanced `AreaResponse`: Now includes actions and reactions in the response

### 3. JSON Schema Validation
- `JsonSchemaValidationService`: Service for validating parameters against JSON schemas
- Validates action/reaction parameters against their respective action definition schemas
- Provides proper error messages for validation failures

### 4. Business Logic Service
- `AreaService`: Handles AREA creation business logic
- Validates action definitions (triggers must be event capable, reactions must be executable)
- Creates action instances for both actions and reactions
- Validates service accounts if provided

### 5. Enhanced Controller
- New endpoint `POST /api/areas/with-actions` for creating AREA with actions/reactions
- Maintains existing `POST /api/areas` for basic AREA creation
- Proper error handling and HTTP status codes

### 6. Database Migration
- `V2__Add_jsonb_actions_reactions_to_areas.sql`: Adds JSONB columns to existing areas table
- Includes GIN indexes for efficient JSON querying

## API Usage

### Creating an AREA with Actions and Reactions

```http
POST /api/areas/with-actions
Content-Type: application/json

{
  "name": "GitHub to Slack Notification",
  "description": "Send Slack notification when GitHub issue is created",
  "userId": "12345678-1234-1234-1234-123456789abc",
  "actions": [
    {
      "actionDefinitionId": "87654321-4321-4321-4321-cba987654321",
      "name": "GitHub Issue Created",
      "description": "Triggers when a new issue is created",
      "parameters": {
        "repository": "my-org/my-repo",
        "labels": ["bug", "urgent"]
      },
      "activationConfig": {
        "type": "webhook",
        "path": "/webhooks/github/issues"
      },
      "serviceAccountId": "11111111-1111-1111-1111-111111111111"
    }
  ],
  "reactions": [
    {
      "actionDefinitionId": "22222222-2222-2222-2222-222222222222",
      "name": "Send Slack Message",
      "description": "Posts message to Slack channel",
      "parameters": {
        "channel": "#development",
        "message": "New issue created: {{issue.title}}"
      },
      "mapping": {
        "issue.title": "title",
        "issue.url": "url"
      },
      "condition": {
        "operator": "and",
        "conditions": [
          {
            "field": "issue.state",
            "operator": "equals",
            "value": "open"
          }
        ]
      },
      "order": 1,
      "serviceAccountId": "33333333-3333-3333-3333-333333333333"
    }
  ]
}
```

### Response

```json
{
  "id": "abcdef12-3456-7890-abcd-ef1234567890",
  "name": "GitHub to Slack Notification",
  "description": "Send Slack notification when GitHub issue is created",
  "enabled": true,
  "userId": "12345678-1234-1234-1234-123456789abc",
  "userEmail": "user@example.com",
  "actions": [
    {
      "actionDefinitionId": "87654321-4321-4321-4321-cba987654321",
      "name": "GitHub Issue Created",
      "description": "Triggers when a new issue is created",
      "parameters": {
        "repository": "my-org/my-repo",
        "labels": ["bug", "urgent"]
      },
      "activationConfig": {
        "type": "webhook",
        "path": "/webhooks/github/issues"
      },
      "serviceAccountId": "11111111-1111-1111-1111-111111111111"
    }
  ],
  "reactions": [
    {
      "actionDefinitionId": "22222222-2222-2222-2222-222222222222",
      "name": "Send Slack Message",
      "description": "Posts message to Slack channel",
      "parameters": {
        "channel": "#development",
        "message": "New issue created: {{issue.title}}"
      },
      "mapping": {
        "issue.title": "title",
        "issue.url": "url"
      },
      "condition": {
        "operator": "and",
        "conditions": [
          {
            "field": "issue.state",
            "operator": "equals",
            "value": "open"
          }
        ]
      },
      "order": 1,
      "serviceAccountId": "33333333-3333-3333-3333-333333333333"
    }
  ],
  "createdAt": "2025-10-06T10:30:00Z",
  "updatedAt": "2025-10-06T10:30:00Z"
}
```

## Validation Rules

### Area Level
- Name is required and must not be blank
- User ID is required and user must exist
- At least one action is required
- At least one reaction is required

### Action Level
- Action definition ID is required and must exist
- Action definition must be event capable (`isEventCapable = true`)
- Name is required and must not be blank
- Parameters must conform to the action definition's input schema
- Service account ID (if provided) must exist and match the service

### Reaction Level
- Action definition ID is required and must exist
- Action definition must be executable (`isExecutable = true`)
- Name is required and must not be blank
- Parameters must conform to the action definition's input schema
- Service account ID (if provided) must exist and match the service
- Order defaults to 0 if not provided

## Error Responses

### 400 Bad Request - Validation Error
```json
{
  "error": "Validation failed",
  "message": "Required parameter 'repository' is missing"
}
```

### 404 Not Found - Resource Not Found
```json
{
  "error": "Validation failed",
  "message": "User not found with ID: 12345678-1234-1234-1234-123456789abc"
}
```

### 500 Internal Server Error
```json
{
  "error": "Internal server error",
  "message": "An unexpected error occurred"
}
```

## Database Schema Changes

The implementation adds two new JSONB columns to the `a_areas` table:

```sql
ALTER TABLE area.a_areas 
ADD COLUMN IF NOT EXISTS actions jsonb,
ADD COLUMN IF NOT EXISTS reactions jsonb;

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_areas_actions ON area.a_areas USING gin(actions);
CREATE INDEX IF NOT EXISTS idx_areas_reactions ON area.a_areas USING gin(reactions);
```

## Testing

The implementation includes comprehensive unit tests covering:
- Successful AREA creation with actions and reactions
- Validation errors for missing users
- Validation errors for missing action definitions
- JSON schema validation
- Service account validation

## Future Enhancements

1. **Enhanced JSON Schema Validation**: Implement full JSON Schema library support for more complex validation rules
2. **Action Ordering**: Add support for ordering actions within an AREA
3. **Conditional Logic**: Enhance condition evaluation for more complex workflows
4. **Template Support**: Add support for template variables in reaction parameters
5. **Bulk Operations**: Support for creating multiple AREAs in a single request

## Notes

- The implementation maintains backward compatibility with existing AREA creation
- JSONB storage provides flexibility for storing complex action/reaction configurations
- Action instances are automatically created for both actions and reactions
- The design supports future extensions without breaking changes