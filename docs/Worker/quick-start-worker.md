# AREA Worker System - Quick Start Guide

## 🚀 Quick Start

### 1. Prerequisites

- Java 17+
- Docker & Docker Compose
- Gradle 7.6+

### 2. Start the System

```bash
# Start Redis and PostgreSQL
docker-compose up -d

# Run the application
./gradlew bootRun
```

### 3. Initialize the Worker

```bash
# Initialize Redis stream and consumer group
curl -X POST http://localhost:8080/api/worker/initialize-stream
```

### 4. Check Worker Status

```bash
# Check if worker is running
curl http://localhost:8080/api/worker/status

# Get execution statistics
curl http://localhost:8080/api/worker/statistics
```

## 📖 Basic Usage Examples

### Example 1: Send a Test Event

```bash
curl -X POST "http://localhost:8080/api/worker/test-event" \
  -d "actionInstanceId=550e8400-e29b-41d4-a716-446655440000" \
  -d "areaId=550e8400-e29b-41d4-a716-446655440001"
```

### Example 2: Create and Execute a Reaction

```java
// 1. Create an execution
Execution execution = executionService.createExecution(
    actionInstance,
    null,
    Map.of("message", "Hello World!", "recipient", "user@example.com"),
    UUID.randomUUID()
);

// 2. Publish to worker
String eventId = redisEventService.publishExecutionEvent(
    execution.getId(),
    actionInstance.getId(),
    area.getId(),
    Map.of("trigger", "webhook", "data", "example")
);

// 3. Worker automatically processes the execution
```

### Example 3: Monitor Processing

```bash
# Watch worker statistics in real-time
watch -n 2 "curl -s http://localhost:8080/api/worker/statistics | jq"
```

## 🔧 Configuration Examples

### Environment Variables

```bash
# Redis configuration
export REDIS_HOST=localhost
export REDIS_PORT=6379
export REDIS_PASSWORD=yourpassword

# Database configuration
export DATABASE_URL=jdbc:postgresql://localhost:5432/area
export DATABASE_USERNAME=area
export DATABASE_PASSWORD=password
```

### Application Properties

```yaml
# application-prod.yml
spring:
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD:}
    timeout: 2000ms
    lettuce:
      pool:
        max-active: 8
        max-idle: 8
        min-idle: 0

# Worker thread pool configuration
area:
  worker:
    core-pool-size: 4
    max-pool-size: 10
    queue-capacity: 100
  reaction:
    core-pool-size: 2
    max-pool-size: 6
    queue-capacity: 50
```

## 🧪 Testing Guide

### Run Unit Tests

```bash
# All unit tests
./gradlew test

# Specific test class
./gradlew test --tests AreaReactionWorkerTest

# With coverage report
./gradlew jacocoTestReport
```

### Run Integration Tests

```bash
# Integration tests (requires Docker)
./gradlew integrationTest

# Specific integration test
./gradlew test --tests WorkerIntegrationTest
```

### Manual Testing

```bash
# 1. Start the application
./gradlew bootRun

# 2. In another terminal, send test events
for i in {1..10}; do
  curl -X POST "http://localhost:8080/api/worker/test-event" \
    -d "actionInstanceId=$(uuidgen)" \
    -d "areaId=$(uuidgen)"
  sleep 1
done

# 3. Monitor results
curl http://localhost:8080/api/worker/statistics
```

## 📊 Monitoring Examples

### Health Check Script

```bash
#!/bin/bash
# health-check.sh

WORKER_URL="http://localhost:8080/api/worker"

echo "=== Worker Health Check ==="
echo "Worker Status:"
curl -s "$WORKER_URL/status" | jq

echo -e "\nExecution Statistics:"
curl -s "$WORKER_URL/statistics" | jq

echo -e "\nRedis Stream Info:"
curl -s "$WORKER_URL/stream-info" | jq
```

### Prometheus Metrics (if enabled)

```bash
# Get worker metrics
curl http://localhost:8080/actuator/prometheus | grep area_worker

# Example metrics:
# area_worker_executions_total{status="ok"} 150
# area_worker_executions_total{status="failed"} 5
# area_worker_retry_attempts_total 12
```

## 🚨 Troubleshooting Examples

### Problem: Worker Not Processing Events

```bash
# 1. Check Redis connection
redis-cli ping
# Expected: PONG

# 2. Check stream exists
redis-cli XINFO STREAM areas:events
# Should show stream information

# 3. Check consumer group
redis-cli XINFO GROUPS areas:events
# Should show "area-processors" group

# 4. Check application logs
tail -f logs/application.log | grep -i worker
```

### Problem: High Failure Rate

```bash
# 1. Check execution statistics
curl http://localhost:8080/api/worker/statistics

# 2. Look for error patterns in database
psql -d area -c "
SELECT error->>'exception' as error_type, COUNT(*) 
FROM a_executions 
WHERE status = 'FAILED' 
  AND created_at > NOW() - INTERVAL '1 hour'
GROUP BY error->>'exception'
ORDER BY count DESC;
"

# 3. Check recent failures
psql -d area -c "
SELECT id, error->>'message' as error_message, created_at 
FROM a_executions 
WHERE status = 'FAILED' 
ORDER BY created_at DESC 
LIMIT 10;
"
```

### Problem: Memory Issues

```bash
# 1. Check JVM memory usage
curl http://localhost:8080/actuator/metrics/jvm.memory.used

# 2. Check thread pool usage
curl http://localhost:8080/actuator/metrics/executor.active

# 3. Monitor Redis memory
redis-cli INFO memory
```

## 📚 Common Patterns

### Pattern 1: Batch Processing

```java
// Process multiple executions at once
@Scheduled(fixedDelay = 5000)
public void processBatch() {
    List<Execution> executions = executionService.getQueuedExecutions();
    executions.parallelStream()
        .forEach(this::processExecution);
}
```

### Pattern 2: Priority Processing

```java
// Process high-priority executions first
public void processWithPriority() {
    // Get executions sorted by priority
    List<Execution> executions = executionService.getQueuedExecutionsByPriority();
    
    for (Execution execution : executions) {
        if (shouldProcessImmediately(execution)) {
            processExecution(execution);
        }
    }
}
```

### Pattern 3: Graceful Shutdown

```java
@PreDestroy
public void shutdown() {
    log.info("Shutting down worker gracefully...");
    running = false;
    
    // Wait for current executions to complete
    awaitTermination(Duration.ofSeconds(30));
}
```

## 🔗 Related Documentation

- [API Documentation](./api-documentation.md)
- [Database Schema](./database-schema.md)
- [Redis Configuration](./redis-configuration.md)
- [Testing Guide](./testing-guide.md)
- [Deployment Guide](./deployment.md)

---

*For more detailed information, see the [complete documentation](./area-reaction-worker.md).*