# AREA Reaction Worker System

## Overview

The AREA Reaction Worker is a distributed, fault-tolerant system designed to execute AREA reactions asynchronously. It decouples webhook triggers and polling mechanisms from the actual reaction execution, ensuring scalability and reliability.

## Architecture

```
┌─────────────────┐    ┌──────────────┐    ┌─────────────────┐    ┌──────────────┐
│   Webhooks/     │    │    Redis     │    │  AREA Reaction  │    │   Database   │
│   Pollers       │───▶│   Stream     │───▶│    Worker       │───▶│ (Executions) │
│                 │    │              │    │                 │    │              │
└─────────────────┘    └──────────────┘    └─────────────────┘    └──────────────┘
```

### Components

1. **Redis Stream** (`areas:events`): Message queue for execution events
2. **AreaReactionWorker**: Main worker consuming from Redis stream
3. **ReactionExecutor**: Executes individual reactions
4. **ExecutionService**: Manages execution lifecycle in database
5. **RetryManager**: Handles retry logic with exponential backoff

## Core Features

### ✅ Implemented Features

- **Redis Stream Processing**: Consumes events from `areas:events` stream
- **Multiple Processing Modes**: 
  - Real-time event processing from Redis stream
  - Batch processing of queued executions
  - Retry processing with exponential backoff
  - Timeout cleanup for stalled executions
- **Fault Tolerance**: Automatic retries with exponential backoff (max 5 attempts)
- **Database Integration**: Complete execution tracking with status, logs, and timestamps
- **Monitoring**: REST API for worker status and execution statistics
- **Async Processing**: Separate thread pools for worker and reaction execution
- **Comprehensive Testing**: Unit and integration tests with Testcontainers

## Configuration

### Redis Configuration

```yaml
# application.yml
spring:
  redis:
    host: localhost
    port: 6379
    password: ${REDIS_PASSWORD:}
  cache:
    type: redis
```

### Worker Configuration

The worker uses the following constants (configurable in `RedisConfig`):

```java
public static final String AREAS_EVENTS_STREAM = "areas:events";
public static final String AREAS_CONSUMER_GROUP = "area-processors";
private static final int STREAM_BATCH_SIZE = 10;
private static final int STREAM_THREAD_POOL_SIZE = 4;
```

### Async Configuration

Thread pools are configured in `AsyncConfig`:

```java
// Worker pool for scheduled tasks
@Bean(name = "areaWorkerExecutor")
- Core pool size: 4
- Max pool size: 10
- Queue capacity: 100

// Reaction execution pool
@Bean(name = "reactionTaskExecutor") 
- Core pool size: 2
- Max pool size: 6
- Queue capacity: 50
```

## Usage

### 1. Publishing Events to Worker

Events are published to the Redis stream via `RedisEventService`:

```java
@Autowired
private RedisEventService redisEventService;

// Publish an execution event
String eventId = redisEventService.publishExecutionEvent(
    executionId,           // UUID of the execution
    actionInstanceId,      // UUID of the action instance
    areaId,               // UUID of the AREA
    payload               // Map<String, Object> event payload
);
```

### 2. Creating Executions

Create executions in the database using `ExecutionService`:

```java
@Autowired
private ExecutionService executionService;

Execution execution = executionService.createExecution(
    actionInstance,        // ActionInstance entity
    activationMode,        // ActivationMode entity (can be null)
    inputPayload,         // Map<String, Object> input data
    correlationId         // UUID for grouping related executions
);
```

### 3. Monitoring Worker Status

#### Get Worker Status
```bash
GET /api/worker/status
```

Response:
```json
{
  "consumerName": "worker-a1b2c3d4",
  "running": true,
  "streamInfo": {
    "streamKey": "areas:events",
    "consumerGroup": "area-processors",
    "streamInfo": "..."
  }
}
```

#### Get Execution Statistics
```bash
GET /api/worker/statistics
```

Response:
```json
{
  "queued": 5,
  "running": 2,
  "ok": 150,
  "retry": 3,
  "failed": 2,
  "canceled": 1
}
```

#### Get Redis Stream Information
```bash
GET /api/worker/stream-info
```

### 4. Manual Worker Operations

#### Cancel an Execution
```bash
POST /api/worker/executions/{executionId}/cancel?reason=Manual+cancellation
```

#### Send Test Event
```bash
POST /api/worker/test-event?actionInstanceId={uuid}&areaId={uuid}
```

#### Initialize Redis Stream
```bash
POST /api/worker/initialize-stream
```

## Database Schema

### Executions Table (`a_executions`)

The worker tracks all executions in the database:

```sql
CREATE TABLE a_executions (
  id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  action_instance_id   uuid NOT NULL REFERENCES a_action_instances(id),
  activation_mode_id   uuid REFERENCES a_activation_modes(id),
  area_id              uuid REFERENCES a_areas(id),
  status               execution_status NOT NULL DEFAULT 'QUEUED',
  attempt              integer NOT NULL DEFAULT 0,
  queued_at            timestamptz NOT NULL DEFAULT now(),
  started_at           timestamptz,
  finished_at          timestamptz,
  input_payload        jsonb,
  output_payload       jsonb,
  error                jsonb,
  correlation_id       uuid,
  dedup_key            text
);
```

### Execution Statuses

- **QUEUED**: Execution is waiting to be processed
- **RUNNING**: Execution is currently being processed
- **OK**: Execution completed successfully
- **RETRY**: Execution failed but will be retried
- **FAILED**: Execution failed permanently (max retries reached)
- **CANCELED**: Execution was manually canceled

## Reaction Execution

### Current Implementation

The `ReactionExecutor` currently provides **simulated/mocked** executions for different service types:

- **Email**: Simulates email sending with latency (200-800ms)
- **Slack**: Simulates Slack message posting (300-1000ms)
- **Webhook**: Simulates HTTP webhook calls (500-1500ms)
- **Database**: Simulates database operations (100-500ms)
- **Notification**: Simulates push notifications (150-600ms)
- **Generic**: Default simulation for unknown services (200-1000ms)

### Example Output

```json
{
  "service": "email",
  "action": "send_email",
  "executedAt": "2025-09-30T10:30:00",
  "executionDuration": "456ms",
  "type": "email",
  "to": "user@example.com",
  "subject": "AREA Notification",
  "status": "sent",
  "messageId": "msg_a1b2c3d4"
}
```

## Retry Management

### Retry Strategy

The `RetryManager` implements intelligent retry logic:

- **Maximum Attempts**: 5 retries
- **Exponential Backoff**: `2^attempt` seconds (with jitter)
- **Maximum Delay**: 300 seconds
- **Jitter**: ±10% random variation to prevent thundering herd

### Retry Schedule Example

| Attempt | Base Delay | Actual Delay (with jitter) |
|---------|------------|---------------------------|
| 1       | 2s         | 1.8s - 2.2s              |
| 2       | 4s         | 3.6s - 4.4s              |
| 3       | 8s         | 7.2s - 8.8s              |
| 4       | 16s        | 14.4s - 17.6s            |
| 5       | 32s        | 28.8s - 35.2s            |

### Non-Retryable Errors

The following error types are **not retried**:

- Authentication/Authorization errors
- Validation errors (bad request)
- Resource not found errors
- `IllegalArgumentException`
- `SecurityException`

## Scaling and Performance

### Horizontal Scaling

The worker system supports horizontal scaling:

1. **Multiple Worker Instances**: Each worker has a unique consumer name
2. **Consumer Groups**: Redis streams distribute work across consumers
3. **Load Balancing**: Events are automatically distributed among available workers

### Performance Characteristics

- **Throughput**: Processes ~10-50 executions/second per worker instance
- **Latency**: Typical execution time 100ms-2s (depending on service)
- **Memory**: ~50-100MB per worker instance
- **Redis**: Lightweight message overhead (~1KB per event)

## Monitoring and Observability

### Logs

The worker provides structured logging:

```log
2025-09-30 10:30:15.123 INFO  AreaReactionWorker - Processing execution: id=123, actionInstance=456, attempt=1
2025-09-30 10:30:15.580 INFO  AreaReactionWorker - Completed execution: id=123, status=OK, duration=457ms
```

### Health Checks

Worker health can be monitored via:

- `/api/worker/status` - Worker operational status
- `/actuator/health` - Spring Boot health endpoint
- `/actuator/caches` - Redis cache status

## Testing

### Unit Tests

Comprehensive unit tests cover:

- `AreaReactionWorkerTest`: Worker logic and error handling
- `ReactionExecutorTest`: Reaction execution scenarios
- `RetryManagerTest`: Retry logic and backoff calculation
- `ExecutionServiceTest`: Database operations

### Integration Tests

`WorkerIntegrationTest` provides end-to-end testing:

- Real Redis instance (via Testcontainers)
- Full workflow: Event → Redis → Worker → Database
- Performance and reliability testing

### Running Tests

```bash
# Unit tests only
./gradlew test

# Integration tests (requires Docker)
./gradlew integrationTest

# All tests
./gradlew check
```

## Troubleshooting

### Common Issues

#### Worker Not Processing Events

1. **Check Redis Connection**:
   ```bash
   redis-cli ping
   ```

2. **Verify Stream Exists**:
   ```bash
   redis-cli XINFO STREAM areas:events
   ```

3. **Check Consumer Group**:
   ```bash
   redis-cli XINFO GROUPS areas:events
   ```

#### High Retry Rate

1. **Check Error Patterns** in execution logs
2. **Review Service Configurations** (timeouts, credentials)
3. **Monitor External Service Health**

#### Memory Issues

1. **Monitor Thread Pool Usage** via `/actuator/metrics`
2. **Check Redis Memory Usage**
3. **Review Execution Payload Sizes**

### Debug Commands

```bash
# Check Redis stream length
redis-cli XLEN areas:events

# View recent events
redis-cli XRANGE areas:events - + COUNT 10

# Check consumer group status
redis-cli XINFO CONSUMERS areas:events area-processors

# Monitor Redis operations
redis-cli MONITOR
```

## Development

### Adding New Reaction Types

To add support for a new service type:

1. **Extend ReactionExecutor**:
   ```java
   private Map<String, Object> executeNewServiceAction(String actionKey, 
                                                      Map<String, Object> input,
                                                      Map<String, Object> params) {
       // Implement actual service integration
       // Return execution result
   }
   ```

2. **Add to Service Switch**:
   ```java
   case "newservice":
       result.putAll(executeNewServiceAction(actionKey, inputPayload, actionParams));
       break;
   ```

3. **Add Tests**:
   ```java
   @Test
   void testNewServiceExecution() {
       // Test the new service integration
   }
   ```

### Configuration for Production

For production deployment:

1. **Enable Connection Pooling**:
   ```yaml
   spring:
     redis:
       lettuce:
         pool:
           max-active: 8
           max-idle: 8
   ```

2. **Configure Monitoring**:
   ```yaml
   management:
     endpoints:
       web:
         exposure:
           include: health,metrics,caches
     metrics:
       export:
         prometheus:
           enabled: true
   ```

3. **Set Production Logging**:
   ```yaml
   logging:
     level:
       area.server.AREA_Back.worker: INFO
       org.springframework.data.redis: WARN
   ```

## Roadmap

### Future Enhancements

- **Real Service Integrations**: Replace simulations with actual API clients
- **Dead Letter Queue**: Implement automatic dead letter processing
- **Metrics & Tracing**: Add Prometheus metrics and distributed tracing
- **Rate Limiting**: Per-service rate limiting and throttling
- **Webhook Integration**: Connect webhook receivers to worker system
- **Configuration UI**: Admin interface for worker configuration

## API Reference

### WorkerController Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/worker/status` | Get worker operational status |
| GET | `/api/worker/statistics` | Get execution statistics |
| GET | `/api/worker/stream-info` | Get Redis stream information |
| POST | `/api/worker/executions/{id}/cancel` | Cancel specific execution |
| POST | `/api/worker/test-event` | Send test event to worker |
| POST | `/api/worker/initialize-stream` | Initialize Redis stream |

### RedisEventService Methods

| Method | Description |
|--------|-------------|
| `publishAreaEvent(message)` | Publish event to Redis stream |
| `publishExecutionEvent(...)` | Publish execution event |
| `initializeStream()` | Initialize stream and consumer group |
| `getStreamInfo()` | Get stream status information |

### ExecutionService Methods

| Method | Description |
|--------|-------------|
| `createExecution(...)` | Create new execution in database |
| `updateExecutionWithResult(...)` | Update execution with result |
| `markExecutionAsStarted(...)` | Mark execution as started |
| `getQueuedExecutions()` | Get executions waiting to be processed |
| `getExecutionsReadyForRetry(...)` | Get executions ready for retry |
| `cancelExecution(...)` | Cancel execution manually |
| `getExecutionStatistics()` | Get execution statistics |

---

## Contributing

1. Fork the repository
2. Create a feature branch
3. Add tests for new functionality
4. Ensure all tests pass
5. Submit a pull request

## License

This project is licensed under the MIT License.