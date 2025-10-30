# Webhook System - Sequence Diagram

This diagram shows webhook registration, event reception, and processing.

## Webhook Registration Flow

```mermaid
sequenceDiagram
    participant AreaService
    participant WebhookRegistry
    participant ServiceClient
    participant ExternalService as External Service<br/>(GitHub/Slack)
    participant Database

    AreaService->>AreaService: Create ActionInstance with WEBHOOK mode
    AreaService->>WebhookRegistry: registerWebhook(actionInstance)
    activate WebhookRegistry

    WebhookRegistry->>Database: Get ServiceAccount for authentication
    Database-->>WebhookRegistry: ServiceAccount with tokens

    WebhookRegistry->>WebhookRegistry: Build webhook URL
    Note over WebhookRegistry: {baseUrl}/webhooks/{service}/{actionInstanceId}

    WebhookRegistry->>WebhookRegistry: Get activation config
    Note over WebhookRegistry: Extract: events, filters,<br/>secret generation

    WebhookRegistry->>WebhookRegistry: Generate webhook secret
    Note over WebhookRegistry: For signature verification

    WebhookRegistry->>ServiceClient: Create webhook subscription
    ServiceClient->>ExternalService: POST /hooks (Create webhook)
    Note over ServiceClient,ExternalService: Body: {<br/>  url: callbackUrl,<br/>  events: ["issue.created"],<br/>  secret: webhookSecret<br/>}

    alt Webhook created successfully
        ExternalService-->>ServiceClient: 201 Created + webhook_id
        ServiceClient-->>WebhookRegistry: Webhook registered (webhook_id)

        WebhookRegistry->>Database: Store webhook metadata
        Note over Database: Store: webhook_id,<br/>secret, events,<br/>service, actionInstanceId
        Database-->>WebhookRegistry: Saved

        WebhookRegistry-->>AreaService: Registration successful
    else Registration failed
        ExternalService-->>ServiceClient: 4xx/5xx Error
        ServiceClient-->>WebhookRegistry: Registration failed
        WebhookRegistry-->>AreaService: Registration error
    end
    deactivate WebhookRegistry
```

## Webhook Event Reception and Validation

```mermaid
sequenceDiagram
    participant ExternalService as External Service
    participant WebhookController
    participant WebhookValidator
    participant WebhookHandler
    participant Database

    ExternalService->>WebhookController: POST /webhooks/{service}/{instanceId}
    Note over ExternalService,WebhookController: Headers:<br/>X-Hub-Signature: sha256=...<br/>X-GitHub-Event: issues<br/>Content-Type: application/json

    activate WebhookController
    WebhookController->>WebhookController: Extract headers & body
    
    WebhookController->>Database: Get webhook metadata by instanceId
    Database-->>WebhookController: Webhook config (secret, events)

    WebhookController->>WebhookValidator: validateSignature(body, signature, secret)
    
    WebhookValidator->>WebhookValidator: Compute HMAC-SHA256
    Note over WebhookValidator: HMAC(secret, requestBody)
    
    WebhookValidator->>WebhookValidator: Compare signatures
    
    alt Signature valid
        WebhookValidator-->>WebhookController: Validation success
        
        WebhookController->>WebhookValidator: validateEvent(eventType, allowedEvents)
        WebhookValidator->>WebhookValidator: Check event in allowed list
        
        alt Event allowed
            WebhookValidator-->>WebhookController: Event valid
            
            WebhookController->>WebhookHandler: processWebhook(instanceId, event, payload)
            WebhookHandler->>WebhookHandler: Process event
            Note over WebhookHandler: See AREA Execution Flow
            WebhookHandler-->>WebhookController: Processing initiated
            
            WebhookController-->>ExternalService: 200 OK
        else Event not allowed
            WebhookValidator-->>WebhookController: Event not allowed
            WebhookController->>Database: Log rejected event
            WebhookController-->>ExternalService: 200 OK (acknowledge)
            Note over WebhookController,ExternalService: Return 200 to prevent<br/>retry from service
        end
    else Signature invalid
        WebhookValidator-->>WebhookController: Validation failed
        WebhookController->>Database: Log security violation
        WebhookController-->>ExternalService: 401 Unauthorized
    end
    deactivate WebhookController
```

## Webhook Deregistration Flow

```mermaid
sequenceDiagram
    participant User
    participant AreaService
    participant WebhookRegistry
    participant ServiceClient
    participant ExternalService as External Service
    participant Database

    User->>AreaService: Delete AREA or disable action
    activate AreaService
    
    AreaService->>Database: Get ActionInstance with webhook mode
    Database-->>AreaService: ActionInstance details
    
    AreaService->>Database: Get webhook registration
    Database-->>AreaService: Webhook metadata (webhook_id)
    
    AreaService->>WebhookRegistry: unregisterWebhook(actionInstanceId)
    activate WebhookRegistry
    
    WebhookRegistry->>Database: Get ServiceAccount
    Database-->>WebhookRegistry: ServiceAccount with tokens
    
    WebhookRegistry->>ServiceClient: Delete webhook
    ServiceClient->>ExternalService: DELETE /hooks/{webhook_id}
    
    alt Deletion successful
        ExternalService-->>ServiceClient: 204 No Content
        ServiceClient-->>WebhookRegistry: Webhook deleted
        
        WebhookRegistry->>Database: Remove webhook metadata
        Database-->>WebhookRegistry: Removed
        
        WebhookRegistry-->>AreaService: Unregistration successful
    else Webhook not found (already deleted)
        ExternalService-->>ServiceClient: 404 Not Found
        ServiceClient-->>WebhookRegistry: Already deleted
        
        WebhookRegistry->>Database: Clean up metadata
        WebhookRegistry-->>AreaService: Cleanup complete
    else Deletion failed
        ExternalService-->>ServiceClient: Error
        ServiceClient-->>WebhookRegistry: Deletion failed
        
        WebhookRegistry->>Database: Mark for retry
        WebhookRegistry-->>AreaService: Scheduled for retry
    end
    deactivate WebhookRegistry
    
    AreaService->>Database: Delete/disable ActionInstance
    AreaService-->>User: Action removed
    deactivate AreaService
```

## Webhook Retry Mechanism

```mermaid
sequenceDiagram
    participant ExternalService as External Service
    participant WebhookController
    participant RetryQueue
    participant Database

    ExternalService->>WebhookController: POST /webhooks/{service}/{instanceId}
    activate WebhookController
    
    WebhookController->>WebhookController: Process webhook
    
    alt Processing fails (temp error)
        WebhookController->>RetryQueue: Enqueue for retry
        activate RetryQueue
        
        RetryQueue->>Database: Store webhook payload & metadata
        Note over Database: Store attempt count,<br/>next retry time,<br/>original payload
        
        RetryQueue-->>WebhookController: Queued
        deactivate RetryQueue
        
        WebhookController-->>ExternalService: 503 Service Unavailable
        Note over ExternalService: Service will retry<br/>automatically
    else Processing successful
        WebhookController-->>ExternalService: 200 OK
    end
    deactivate WebhookController
    
    Note over RetryQueue: Wait for retry interval
    
    RetryQueue->>Database: Get pending webhooks
    Database-->>RetryQueue: List of failed webhooks
    
    loop For each failed webhook
        RetryQueue->>RetryQueue: Check retry count < max
        alt Can retry
            RetryQueue->>WebhookController: Retry processing
            WebhookController->>WebhookController: Process webhook
            
            alt Success
                WebhookController-->>RetryQueue: Success
                RetryQueue->>Database: Remove from retry queue
            else Still failing
                WebhookController-->>RetryQueue: Failed
                RetryQueue->>Database: Increment retry count
                RetryQueue->>Database: Update next retry time
            end
        else Max retries exceeded
            RetryQueue->>Database: Move to dead letter queue
            RetryQueue->>Database: Log permanent failure
        end
    end
```

## Service-Specific Webhook Configurations

### GitHub Webhooks
```json
{
  "events": ["push", "pull_request", "issues"],
  "contentType": "application/json",
  "signatureHeader": "X-Hub-Signature-256",
  "eventHeader": "X-GitHub-Event",
  "deliveryHeader": "X-GitHub-Delivery"
}
```

### Slack Webhooks
```json
{
  "events": ["message.channels", "app_mention"],
  "contentType": "application/json",
  "signatureHeader": "X-Slack-Signature",
  "timestampHeader": "X-Slack-Request-Timestamp",
  "verificationToken": true
}
```

## Security Features

1. **Signature Verification**: HMAC-SHA256 validation
2. **Event Filtering**: Only process allowed event types
3. **Rate Limiting**: Prevent webhook flooding
4. **Secret Rotation**: Periodic webhook secret updates
5. **IP Whitelisting**: Accept webhooks only from known IPs
6. **Replay Protection**: Timestamp validation for Slack

## Error Handling

- **Invalid Signature**: Return 401, log security event
- **Unknown Event**: Return 200, log and ignore
- **Processing Error**: Return 503, queue for retry
- **Missing ActionInstance**: Return 404, clean up webhook
- **Disabled AREA**: Return 200, skip processing
