# AREA Creation Flow - Sequence Diagram

This diagram shows the complete process of creating an automation workflow (AREA) with actions, reactions, and links.

```mermaid
sequenceDiagram
    actor User
    participant Frontend
    participant AreaController
    participant AreaService
    participant ActionInstanceRepo
    participant ActionLinkService
    participant ActivationModeRepo
    participant Database

    User->>Frontend: Design AREA workflow
    Note over User,Frontend: Select trigger (action)<br/>Configure reactions<br/>Map data between steps

    Frontend->>Frontend: Build CreateAreaRequest
    Note over Frontend: {<br/>  name, description,<br/>  actions: [trigger],<br/>  reactions: [reaction1, reaction2],<br/>  connections: [links]<br/>}

    Frontend->>AreaController: POST /areas/with-actions-and-links
    activate AreaController
    AreaController->>AreaController: Validate JWT & extract userId
    AreaController->>AreaService: createAreaWithActionsAndLinks(request)
    activate AreaService

    %% Validation Phase
    AreaService->>Database: Find User by ID
    Database-->>AreaService: User entity

    AreaService->>AreaService: Validate actions & reactions
    loop For each action/reaction
        AreaService->>Database: Verify ActionDefinition exists
        Database-->>AreaService: ActionDefinition
        AreaService->>Database: Verify ServiceAccount exists (if specified)
        Database-->>AreaService: ServiceAccount
        AreaService->>AreaService: Validate service matches
    end

    %% Area Creation
    AreaService->>Database: Create Area entity
    Note over Database: Store name, description<br/>actions/reactions as JSONB
    Database-->>AreaService: Saved Area with ID

    %% Action/Reaction Instance Creation
    AreaService->>AreaService: Create action instances with mapping
    loop For each action (trigger)
        AreaService->>ActionInstanceRepo: Create ActionInstance
        Note over ActionInstanceRepo: Store parameters,<br/>mapping, conditions
        ActionInstanceRepo->>Database: INSERT ActionInstance
        Database-->>ActionInstanceRepo: ActionInstance with ID
        ActionInstanceRepo-->>AreaService: ActionInstance + serviceId mapping
    end

    loop For each reaction
        AreaService->>ActionInstanceRepo: Create ActionInstance
        Note over ActionInstanceRepo: Store parameters,<br/>mapping, conditions,<br/>execution order
        ActionInstanceRepo->>Database: INSERT ActionInstance
        Database-->>ActionInstanceRepo: ActionInstance with ID
        ActionInstanceRepo-->>AreaService: ActionInstance + serviceId mapping
    end

    %% Link Creation
    alt Connections provided
        AreaService->>ActionLinkService: Create action links
        activate ActionLinkService
        loop For each connection
            ActionLinkService->>ActionLinkService: Resolve service IDs to instance IDs
            ActionLinkService->>Database: Create ActionLink
            Note over Database: Links source action<br/>to target reaction
            Database-->>ActionLinkService: ActionLink created
        end
        ActionLinkService-->>AreaService: Links created
        deactivate ActionLinkService
    end

    %% Activation Mode Setup
    loop For each ActionInstance
        alt Has activation config
            AreaService->>AreaService: Extract activation config
            AreaService->>ActivationModeRepo: Create ActivationMode
            Note over ActivationModeRepo: Configure trigger type:<br/>WEBHOOK, CRON, POLL
            ActivationModeRepo->>Database: INSERT ActivationMode
            Database-->>ActivationModeRepo: Created
        end
    end

    %% Register Webhooks (if applicable)
    loop For webhook-activated actions
        AreaService->>AreaService: Check if WEBHOOK mode
        alt Is Webhook
            AreaService->>AreaService: Register webhook with service
            Note over AreaService: Subscribe to events<br/>from external service
        end
    end

    %% Schedule Cron Jobs (if applicable)
    loop For cron-activated actions
        AreaService->>AreaService: Check if CRON mode
        alt Is Cron
            AreaService->>AreaService: Schedule cron job
            Note over AreaService: Register cron expression<br/>with scheduler
        end
    end

    %% Build Response
    AreaService->>Database: Fetch all ActionInstances for area
    Database-->>AreaService: List of ActionInstances
    AreaService->>ActionLinkService: Get links by area ID
    ActionLinkService-->>AreaService: List of ActionLinks
    AreaService->>AreaService: Enrich actions/reactions with instance IDs
    AreaService->>AreaService: Build AreaResponse

    AreaService-->>AreaController: AreaResponse
    deactivate AreaService
    AreaController-->>Frontend: 201 Created + AreaResponse
    deactivate AreaController
    Frontend-->>User: Show "AREA created successfully" ✓
    Frontend->>Frontend: Navigate to AREA dashboard
```

## Request Structure Example

```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "name": "GitHub to Slack Notifier",
  "description": "Send Slack message when GitHub issue is created",
  "actions": [
    {
      "actionDefinitionId": "github-issue-created-def-id",
      "name": "GitHub Issue Trigger",
      "serviceAccountId": "github-account-id",
      "parameters": {
        "repository": "user/repo"
      },
      "activationConfig": {
        "type": "WEBHOOK",
        "config": {
          "events": ["issues.opened"]
        }
      }
    }
  ],
  "reactions": [
    {
      "actionDefinitionId": "slack-send-message-def-id",
      "name": "Send Slack Alert",
      "serviceAccountId": "slack-account-id",
      "parameters": {
        "channel": "#alerts"
      },
      "mapping": {
        "message": "{{trigger.issue.title}} - {{trigger.issue.url}}"
      },
      "order": 1
    }
  ],
  "connections": [
    {
      "sourceServiceId": "action-0",
      "targetServiceId": "reaction-0"
    }
  ]
}
```

## Key Processes

### 1. Validation Phase
- Verify user exists and is active
- Validate all ActionDefinitions exist
- Verify ServiceAccounts belong to user and match services
- Ensure proper action/reaction configuration

### 2. Entity Creation
- Create Area entity with JSONB actions/reactions
- Create ActionInstance for each action and reaction
- Store parameters, mappings, and conditions
- Assign execution order to reactions

### 3. Link Establishment
- Resolve temporary service IDs to actual ActionInstance IDs
- Create ActionLink entities connecting triggers to reactions
- Enable data flow between actions

### 4. Activation Setup
- Parse activation configuration
- Create ActivationMode entities
- Register webhooks with external services
- Schedule cron jobs for periodic triggers
- Start polling services if needed

### 5. Response Building
- Enrich JSONB data with ActionInstance IDs
- Include all created links
- Return complete AREA configuration

## Data Mapping

The system supports sophisticated data mapping using JSON path expressions:

```json
{
  "mapping": {
    "message": "New issue: {{trigger.issue.title}}",
    "url": "{{trigger.issue.html_url}}",
    "author": "{{trigger.issue.user.login}}"
  }
}
```

## Conditional Execution

Reactions can have conditions evaluated before execution:

```json
{
  "condition": {
    "operator": "AND",
    "rules": [
      {
        "field": "{{trigger.issue.labels}}",
        "operator": "contains",
        "value": "bug"
      },
      {
        "field": "{{trigger.issue.state}}",
        "operator": "equals",
        "value": "open"
      }
    ]
  }
}
```
