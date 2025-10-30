# Core Entities Class Diagram

This diagram shows the core entity model of the AREA system, focusing on the main domain objects and their relationships.

```mermaid
classDiagram
    class User {
        -UUID id
        -String email
        -String username
        -String firstname
        -String lastname
        -Boolean isActive
        -Boolean isAdmin
        -Boolean isVerified
        -LocalDateTime createdAt
        -LocalDateTime lastLoginAt
        -String avatarUrl
        -List~Area~ areas
        -List~UserOAuthIdentity~ oauthIdentities
        -List~ServiceAccount~ serviceAccounts
    }

    class Area {
        -UUID id
        -String name
        -String description
        -Boolean enabled
        -List~Map~ actions
        -List~Map~ reactions
        -LocalDateTime createdAt
        -LocalDateTime updatedAt
        -User user
    }

    class Service {
        -UUID id
        -String key
        -String name
        -AuthType auth
        -String docsUrl
        -String iconLightUrl
        -String iconDarkUrl
        -Boolean isActive
        -LocalDateTime createdAt
        -LocalDateTime updatedAt
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
        -Boolean enabled
        -Map~String,Object~ parameters
        -Map~String,Object~ mapping
        -Map~String,Object~ condition
        -Integer order
        -LocalDateTime createdAt
        -LocalDateTime updatedAt
        -User user
        -Area area
        -ActionDefinition actionDefinition
        -ServiceAccount serviceAccount
    }

    class ServiceAccount {
        -UUID id
        -String accountIdentifier
        -String accessToken
        -String refreshToken
        -LocalDateTime tokenExpiresAt
        -Map~String,Object~ metadata
        -Boolean isActive
        -LocalDateTime createdAt
        -LocalDateTime updatedAt
        -User user
        -Service service
    }

    class ActivationMode {
        -UUID id
        -ActivationModeType type
        -Map~String,Object~ config
        -Boolean enabled
        -DedupStrategy dedup
        -Integer maxConcurrency
        -Map~String,Object~ rateLimit
        -ActionInstance actionInstance
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

    %% Relationships
    User "1" --> "*" Area : owns
    User "1" --> "*" ServiceAccount : has
    User "1" --> "*" UserOAuthIdentity : has
    User "1" --> "*" ActionInstance : creates
    
    Area "1" --> "*" ActionInstance : contains
    
    Service "1" --> "*" ActionDefinition : provides
    Service "1" --> "*" ServiceAccount : authenticates
    
    ActionDefinition "1" --> "*" ActionInstance : templates
    
    ActionInstance "1" --> "0..1" ServiceAccount : uses
    ActionInstance "1" --> "*" ActivationMode : configured_by

    %% Enumerations
    class AuthType {
        <<enumeration>>
        OAUTH2
        APIKEY
        NONE
    }

    class ActivationModeType {
        <<enumeration>>
        WEBHOOK
        CRON
        POLL
        MANUAL
    }

    class DedupStrategy {
        <<enumeration>>
        NONE
        SIMPLE
        ADVANCED
    }

    Service --> AuthType : uses
    ActivationMode --> ActivationModeType : uses
    ActivationMode --> DedupStrategy : uses
```

## Entity Descriptions

### User
The main user entity representing an authenticated user in the system. Can own multiple Areas and service connections.

### Area
Represents an automation workflow created by a user. Contains actions (triggers) and reactions (responses) stored as JSONB.

### Service
Third-party service integration (e.g., GitHub, Gmail, Slack). Defines how the service authenticates and what actions it provides.

### ActionDefinition
Template for an action or reaction. Defines the input/output schema and whether it can be used as a trigger or response.

### ActionInstance
Concrete instance of an ActionDefinition within an Area. Contains user-specific parameters and configuration.

### ServiceAccount
OAuth credentials linking a User to a Service, allowing authenticated API calls.

### ActivationMode
Defines how an ActionInstance should be triggered (webhook, cron schedule, polling).

### UserOAuthIdentity
OAuth identity from external providers (Google, GitHub) for user authentication.

## Key Relationships

- **User owns Areas**: One user can create multiple automation workflows
- **Area contains ActionInstances**: Each automation workflow has multiple action and reaction instances
- **Service provides ActionDefinitions**: Each service defines what actions/reactions are available
- **ActionInstance uses ServiceAccount**: Actions need authentication to access external APIs
- **ActionInstance configured by ActivationMode**: Defines trigger mechanism (webhook, cron, poll)
