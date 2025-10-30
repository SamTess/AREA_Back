# Service Integration Architecture Class Diagram

This diagram shows how external services are integrated and how actions/reactions are executed.

```mermaid
classDiagram
    class Service {
        -UUID id
        -String key
        -String name
        -AuthType auth
        -String docsUrl
        -Boolean isActive
    }

    class ActionDefinition {
        -UUID id
        -String key
        -String name
        -String description
        -Map~String,Object~ inputSchema
        -Map~String,Object~ outputSchema
        -Boolean isEventCapable
        -Boolean isExecutable
        -String version
        -Service service
    }

    class ActionInstance {
        -UUID id
        -String name
        -Map~String,Object~ parameters
        -Map~String,Object~ mapping
        -Map~String,Object~ condition
        -Boolean enabled
        -ActionDefinition actionDefinition
        -ServiceAccount serviceAccount
    }

    class ServiceAccount {
        -UUID id
        -String accountIdentifier
        -String accessToken
        -String refreshToken
        -LocalDateTime tokenExpiresAt
        -Boolean isActive
        -Service service
    }

    class ActivationMode {
        -UUID id
        -ActivationModeType type
        -Map~String,Object~ config
        -Boolean enabled
        -DedupStrategy dedup
        -ActionInstance actionInstance
    }

    class ActionExecutor {
        <<interface>>
        +ExecutionResult execute(ActionInstance instance, Map context)
        +Boolean supports(String serviceKey)
    }

    class GitHubActionExecutor {
        <<service>>
        +ExecutionResult execute(ActionInstance instance, Map context)
        +Boolean supports(String serviceKey)
        -GitHubClient client
    }

    class GmailActionExecutor {
        <<service>>
        +ExecutionResult execute(ActionInstance instance, Map context)
        +Boolean supports(String serviceKey)
        -GmailClient client
    }

    class SlackActionExecutor {
        <<service>>
        +ExecutionResult execute(ActionInstance instance, Map context)
        +Boolean supports(String serviceKey)
        -SlackClient client
    }

    class ActionExecutorRegistry {
        <<service>>
        -Map~String,ActionExecutor~ executors
        +ActionExecutor getExecutor(String serviceKey)
        +void registerExecutor(String serviceKey, ActionExecutor executor)
    }

    class WebhookHandler {
        <<service>>
        +void handleWebhook(String serviceKey, String eventType, Map payload)
        -void processEvent(ActionInstance instance, Map payload)
        -void triggerReactions(UUID areaId, Map context)
    }

    class CronScheduler {
        <<service>>
        +void scheduleAction(ActionInstance instance, String cronExpression)
        +void unscheduleAction(UUID instanceId)
        -void executeScheduledAction(UUID instanceId)
    }

    class PollingService {
        <<service>>
        +void startPolling(ActionInstance instance, Integer interval)
        +void stopPolling(UUID instanceId)
        -void pollAndExecute(UUID instanceId)
    }

    class AreaExecutionService {
        <<service>>
        +void executeArea(UUID areaId, Map triggerContext)
        +void executeAction(UUID actionInstanceId, Map context)
        -Map applyMapping(Map input, Map mapping)
        -Boolean evaluateCondition(Map data, Map condition)
    }

    class ServiceClient {
        <<interface>>
        +T makeRequest(String endpoint, Map params, String accessToken)
        +void refreshToken(ServiceAccount account)
    }

    %% Relationships
    Service "1" --> "*" ActionDefinition : provides
    ActionDefinition "1" --> "*" ActionInstance : templates
    ActionInstance "1" --> "0..1" ServiceAccount : authenticates_with
    ActionInstance "1" --> "*" ActivationMode : configured_by
    ServiceAccount "*" --> "1" Service : connects_to

    ActionExecutor <|.. GitHubActionExecutor : implements
    ActionExecutor <|.. GmailActionExecutor : implements
    ActionExecutor <|.. SlackActionExecutor : implements
    
    ActionExecutorRegistry --> ActionExecutor : manages
    ActionExecutorRegistry --> GitHubActionExecutor : registers
    ActionExecutorRegistry --> GmailActionExecutor : registers
    ActionExecutorRegistry --> SlackActionExecutor : registers

    AreaExecutionService --> ActionExecutorRegistry : uses
    AreaExecutionService --> ActionInstance : executes
    
    WebhookHandler --> AreaExecutionService : triggers
    CronScheduler --> AreaExecutionService : triggers
    PollingService --> AreaExecutionService : triggers

    GitHubActionExecutor --> ServiceClient : uses
    GmailActionExecutor --> ServiceClient : uses
    SlackActionExecutor --> ServiceClient : uses

    %% Enumerations
    class ActivationModeType {
        <<enumeration>>
        WEBHOOK
        CRON
        POLL
        MANUAL
    }

    class AuthType {
        <<enumeration>>
        OAUTH2
        APIKEY
        NONE
    }

    class DedupStrategy {
        <<enumeration>>
        NONE
        SIMPLE
        ADVANCED
    }

    ActivationMode --> ActivationModeType : uses
    ActivationMode --> DedupStrategy : uses
    Service --> AuthType : uses
```

## Component Descriptions

### Service Integration Layer

#### ActionExecutor (Interface)
Defines the contract for executing actions on external services. Each service has its own implementation.

#### Service-Specific Executors
- **GitHubActionExecutor**: Handles GitHub API calls (create issue, star repo, etc.)
- **GmailActionExecutor**: Manages Gmail operations (send email, search emails)
- **SlackActionExecutor**: Handles Slack integrations (post message, create channel)

#### ActionExecutorRegistry
Central registry that maps service keys to their corresponding executors. Enables dynamic executor discovery.

#### ServiceClient (Interface)
Abstraction for making HTTP requests to external APIs with OAuth token management.

### Execution Orchestration

#### AreaExecutionService
Main orchestrator for AREA execution. Handles the flow from trigger to reactions, applies data mapping and conditions.

#### WebhookHandler
Receives and processes webhook events from external services, triggering appropriate AREAs.

#### CronScheduler
Manages scheduled action execution based on cron expressions (e.g., "every day at 9 AM").

#### PollingService
Periodically polls external services for changes when webhooks aren't available.

### Data Models

#### Service
Represents a third-party integration with authentication configuration.

#### ActionDefinition
Template defining what an action can do, its inputs, and outputs.

#### ActionInstance
User-configured instance of an action with specific parameters.

#### ActivationMode
Defines how an action is triggered (webhook, cron, poll, manual).

#### ServiceAccount
OAuth credentials for accessing external service APIs.

## Execution Flow

### 1. Webhook-Triggered Execution
```
External Service → Webhook → WebhookHandler → AreaExecutionService → ActionExecutor
```

### 2. Cron-Scheduled Execution
```
CronScheduler → AreaExecutionService → ActionExecutor
```

### 3. Polling-Based Execution
```
PollingService → AreaExecutionService → ActionExecutor
```

### 4. Manual Execution
```
User Request → AreaExecutionService → ActionExecutor
```

## Key Features

- **Pluggable Architecture**: New services can be added by implementing ActionExecutor
- **Multiple Activation Modes**: Supports webhooks, cron, polling, and manual triggers
- **Data Mapping**: Transform trigger output to match reaction input schema
- **Conditional Execution**: Evaluate conditions before executing reactions
- **Token Management**: Automatic OAuth token refresh
- **Deduplication**: Prevent duplicate executions of the same event
