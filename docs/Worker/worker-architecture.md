# AREA Worker System - Architecture Deep Dive

## System Architecture Overview

The AREA Worker System follows a distributed, event-driven architecture designed for scalability, fault tolerance, and loose coupling between components.

## High-Level Architecture

```mermaid
graph TB
    subgraph "Event Sources"
        WH[Webhooks]
        PL[Pollers]
        CR[Cron Jobs]
        API[Manual API]
    end
    
    subgraph "Message Queue"
        RS[Redis Stream<br/>areas:events]
        CG[Consumer Group<br/>area-processors]
    end
    
    subgraph "Worker Layer"
        W1[Worker Instance 1]
        W2[Worker Instance 2]
        WN[Worker Instance N]
    end
    
    subgraph "Execution Layer"
        RE[Reaction Executor]
        RM[Retry Manager]
        SVC[Service Integrations]
    end
    
    subgraph "Persistence"
        DB[(PostgreSQL<br/>Executions)]
        RDS[(Redis<br/>Cache)]
    end
    
    subgraph "Monitoring"
        API_MON[REST API<br/>Monitoring]
        LOGS[Structured Logs]
        METRICS[Metrics]
    end
    
    WH --> RS
    PL --> RS
    CR --> RS
    API --> RS
    
    RS --> CG
    CG --> W1
    CG --> W2
    CG --> WN
    
    W1 --> RE
    W2 --> RE
    WN --> RE
    
    RE --> RM
    RE --> SVC
    
    W1 --> DB
    W2 --> DB
    WN --> DB
    
    W1 --> RDS
    
    W1 --> API_MON
    W1 --> LOGS
    W1 --> METRICS
```

## Detailed Component Architecture

### 1. Message Flow Architecture

```mermaid
sequenceDiagram
    participant T as Trigger
    participant R as Redis Stream
    participant W as Worker
    participant E as Executor
    participant D as Database
    participant S as External Service
    
    T->>R: Publish Event
    Note over R: areas:events stream
    
    W->>R: Consume Event (XREADGROUP)
    Note over W: Batch processing
    
    W->>D: Mark as RUNNING
    W->>E: Execute Reaction
    
    E->>S: Call External API
    S-->>E: Response/Error
    
    alt Success
        E->>D: Update status to OK
        W->>R: ACK message
    else Retryable Error
        E->>D: Update status to RETRY
        W->>R: ACK message
        Note over W: Schedule retry
    else Non-retryable Error
        E->>D: Update status to FAILED
        W->>R: ACK message
    end
```

### 2. Data Flow Architecture

```mermaid
graph LR
    subgraph "Input Layer"
        WH[Webhook Payload]
        EV[Event Data]
        MD[Metadata]
    end
    
    subgraph "Processing Layer"
        VAL[Validation]
        TRANS[Transformation]
        ENR[Enrichment]
    end
    
    subgraph "Execution Layer"
        RT[Route to Service]
        EXEC[Execute Action]
        FMT[Format Response]
    end
    
    subgraph "Output Layer"
        RES[Response Data]
        LOG[Execution Logs]
        STAT[Status Update]
    end
    
    WH --> VAL
    EV --> VAL
    MD --> VAL
    
    VAL --> TRANS
    TRANS --> ENR
    
    ENR --> RT
    RT --> EXEC
    EXEC --> FMT
    
    FMT --> RES
    FMT --> LOG
    FMT --> STAT
```

## Threading Model

### Worker Thread Pools

The system uses two distinct thread pools for optimal performance:

```java
// Worker Executor (Scheduled Tasks)
@Bean(name = "areaWorkerExecutor")
- Core Pool Size: 4 threads
- Max Pool Size: 10 threads  
- Queue Capacity: 100 tasks
- Keep Alive: 60 seconds

// Reaction Executor (Actual Executions)
@Bean(name = "reactionTaskExecutor")
- Core Pool Size: 2 threads
- Max Pool Size: 6 threads
- Queue Capacity: 50 tasks
- Keep Alive: 60 seconds
```

### Thread Allocation Strategy

```mermaid
graph TB
    subgraph "Worker Thread Pool"
        T1[Thread 1<br/>Stream Reader]
        T2[Thread 2<br/>Queued Processor]
        T3[Thread 3<br/>Retry Processor]
        T4[Thread 4<br/>Cleanup Task]
    end
    
    subgraph "Reaction Thread Pool"
        RT1[Reaction Thread 1]
        RT2[Reaction Thread 2]
        RT3[Reaction Thread 3]
        RTN[Reaction Thread N]
    end
    
    T1 --> RT1
    T2 --> RT2
    T3 --> RT3
    T4 --> RTN
    
    style T1 fill:#e1f5fe
    style T2 fill:#e8f5e8
    style T3 fill:#fff3e0
    style T4 fill:#fce4ec
```

## State Management

### Execution State Machine

```mermaid
stateDiagram-v2
    [*] --> QUEUED: Create Execution
    
    QUEUED --> RUNNING: Worker Picks Up
    RUNNING --> OK: Success
    RUNNING --> RETRY: Retryable Error
    RUNNING --> FAILED: Non-retryable Error
    RUNNING --> CANCELED: Manual Cancel
    
    RETRY --> RUNNING: Retry Attempt
    RETRY --> FAILED: Max Retries Reached
    RETRY --> CANCELED: Manual Cancel
    
    OK --> [*]
    FAILED --> [*]
    CANCELED --> [*]
    
    note right of RETRY
        Exponential backoff:
        2^attempt seconds
        Max 5 attempts
    end note
```

### State Transitions

| From | To | Condition | Action |
|------|----|-----------| -------|
| QUEUED | RUNNING | Worker starts processing | Set `started_at` |
| RUNNING | OK | Execution succeeds | Set `finished_at`, `output_payload` |
| RUNNING | RETRY | Retryable error + attempts < 5 | Increment `attempt`, schedule retry |
| RUNNING | FAILED | Non-retryable error OR max attempts | Set `finished_at`, `error` |
| RETRY | RUNNING | Retry time reached | Update `started_at` |
| * | CANCELED | Manual cancellation | Set `finished_at`, `error` |

## Fault Tolerance Design

### 1. Retry Strategy

```mermaid
graph TB
    subgraph "Retry Decision Tree"
        START[Error Occurred]
        CHECK_ATTEMPTS{Attempts < 5?}
        CHECK_TYPE{Retryable Error?}
        CALC_DELAY[Calculate Backoff]
        SCHEDULE[Schedule Retry]
        FAIL[Mark as FAILED]
        
        START --> CHECK_ATTEMPTS
        CHECK_ATTEMPTS -->|No| FAIL
        CHECK_ATTEMPTS -->|Yes| CHECK_TYPE
        CHECK_TYPE -->|No| FAIL
        CHECK_TYPE -->|Yes| CALC_DELAY
        CALC_DELAY --> SCHEDULE
    end
    
    subgraph "Backoff Calculation"
        BASE[Base Delay: 2^attempt]
        JITTER[Add Jitter: ±10%]
        CAP[Cap at 300 seconds]
        
        BASE --> JITTER
        JITTER --> CAP
    end
```

### 2. Error Classification

```java
// Non-retryable errors (fail immediately)
- Authentication/Authorization errors
- Validation errors (400 Bad Request)
- Resource not found (404)
- IllegalArgumentException
- SecurityException

// Retryable errors (with backoff)
- Network timeouts
- Service unavailable (503)
- Rate limiting (429)
- Temporary server errors (5xx)
- RuntimeException (generic)
```

### 3. Circuit Breaker Pattern

```mermaid
stateDiagram-v2
    [*] --> CLOSED
    
    CLOSED --> OPEN: Failure Threshold Exceeded
    OPEN --> HALF_OPEN: Timeout Elapsed
    HALF_OPEN --> CLOSED: Success
    HALF_OPEN --> OPEN: Failure
    
    note right of OPEN
        All requests fail fast
        No actual execution
    end note
    
    note right of HALF_OPEN
        Limited requests allowed
        Test service health
    end note
```

## Scalability Patterns

### 1. Horizontal Scaling

```mermaid
graph TB
    subgraph "Load Balancer"
        LB[Application Load Balancer]
    end
    
    subgraph "Worker Instances"
        W1[Worker Instance 1<br/>worker-abc123]
        W2[Worker Instance 2<br/>worker-def456]
        W3[Worker Instance 3<br/>worker-ghi789]
    end
    
    subgraph "Redis Cluster"
        R1[Redis Node 1]
        R2[Redis Node 2]
        R3[Redis Node 3]
    end
    
    subgraph "Database"
        DB1[(Primary DB)]
        DB2[(Read Replica)]
    end
    
    LB --> W1
    LB --> W2
    LB --> W3
    
    W1 --> R1
    W2 --> R2
    W3 --> R3
    
    W1 --> DB1
    W2 --> DB1
    W3 --> DB1
    
    W1 --> DB2
    W2 --> DB2
    W3 --> DB2
```

### 2. Consumer Group Distribution

```mermaid
graph LR
    subgraph "Redis Stream"
        S[areas:events]
    end
    
    subgraph "Consumer Group: area-processors"
        C1[worker-abc123]
        C2[worker-def456]
        C3[worker-ghi789]
    end
    
    S --> C1
    S --> C2
    S --> C3
    
    style C1 fill:#e1f5fe
    style C2 fill:#e8f5e8
    style C3 fill:#fff3e0
```

### 3. Database Sharding Strategy

```mermaid
graph TB
    subgraph "Application Layer"
        APP[Worker Applications]
    end
    
    subgraph "Sharding Logic"
        SH[Shard by area_id]
    end
    
    subgraph "Database Shards"
        DB1[(Shard 1<br/>areas 0-33%)]
        DB2[(Shard 2<br/>areas 34-66%)]
        DB3[(Shard 3<br/>areas 67-100%)]
    end
    
    APP --> SH
    SH --> DB1
    SH --> DB2
    SH --> DB3
```

## Performance Characteristics

### 1. Throughput Analysis

| Component | Typical Throughput | Bottleneck Factor |
|-----------|-------------------|-------------------|
| Redis Stream | 10,000+ ops/sec | Memory, Network |
| Worker Processing | 50-100 exec/sec/worker | External API calls |
| Database Writes | 1,000+ ops/sec | Disk I/O, Indexing |
| Reaction Execution | 1-10 exec/sec | Service latency |

### 2. Latency Breakdown

```mermaid
gantt
    title Execution Latency Breakdown
    dateFormat X
    axisFormat %s
    
    section Event Flow
    Queue Wait Time     :0, 100
    Worker Pickup       :100, 110
    Execution Start     :110, 120
    Service Call        :120, 620
    Response Processing :620, 650
    DB Update          :650, 680
    Total Latency      :0, 680
```

### 3. Memory Usage Pattern

```mermaid
graph LR
    subgraph "Memory Allocation"
        HEAP[JVM Heap<br/>256MB-1GB]
        REDIS[Redis Memory<br/>50MB-200MB]
        DB[DB Connection Pool<br/>10MB-50MB]
    end
    
    subgraph "Per Execution"
        PAYLOAD[Event Payload<br/>1-10KB]
        CONTEXT[Execution Context<br/>0.5-2KB]
        RESULT[Result Data<br/>1-5KB]
    end
    
    HEAP --> PAYLOAD
    HEAP --> CONTEXT
    HEAP --> RESULT
```

## Security Architecture

### 1. Access Control

```mermaid
graph TB
    subgraph "Authentication Layer"
        JWT[JWT Tokens]
        AUTH[Spring Security]
    end
    
    subgraph "Authorization Layer"
        RBAC[Role-Based Access]
        PERM[Permissions]
    end
    
    subgraph "API Security"
        RATE[Rate Limiting]
        VAL[Input Validation]
        SAN[Data Sanitization]
    end
    
    subgraph "Service Security"
        TLS[TLS Encryption]
        API_KEY[API Key Management]
        OAUTH[OAuth2 Tokens]
    end
    
    JWT --> RBAC
    AUTH --> PERM
    RBAC --> RATE
    PERM --> VAL
    RATE --> SAN
    VAL --> TLS
    SAN --> API_KEY
    TLS --> OAUTH
```

### 2. Data Protection

```mermaid
graph LR
    subgraph "Data at Rest"
        DB_ENC[Database Encryption]
        REDIS_ENC[Redis Encryption]
        LOG_ENC[Log Encryption]
    end
    
    subgraph "Data in Transit"
        TLS_DB[TLS to Database]
        TLS_REDIS[TLS to Redis]
        TLS_API[TLS for APIs]
    end
    
    subgraph "Data in Memory"
        MEM_CLEAR[Memory Clearing]
        HEAP_DUMP[Heap Dump Protection]
        GC_SEC[Secure Garbage Collection]
    end
```

## Monitoring and Observability

### 1. Metrics Collection

```mermaid
graph TB
    subgraph "Application Metrics"
        EXEC[Execution Counters]
        PERF[Performance Metrics]
        ERR[Error Rates]
    end
    
    subgraph "Infrastructure Metrics"
        CPU[CPU Usage]
        MEM[Memory Usage]
        NET[Network I/O]
        DISK[Disk I/O]
    end
    
    subgraph "Business Metrics"
        THROUGH[Throughput]
        LATENCY[Latency P95/P99]
        SUCCESS[Success Rate]
    end
    
    subgraph "Monitoring Stack"
        PROM[Prometheus]
        GRAF[Grafana]
        ALERT[AlertManager]
    end
    
    EXEC --> PROM
    PERF --> PROM
    ERR --> PROM
    CPU --> PROM
    MEM --> PROM
    NET --> PROM
    DISK --> PROM
    THROUGH --> PROM
    LATENCY --> PROM
    SUCCESS --> PROM
    
    PROM --> GRAF
    PROM --> ALERT
```

### 2. Distributed Tracing

```mermaid
sequenceDiagram
    participant C as Client
    participant A as API Gateway
    participant W as Worker
    participant R as Redis
    participant E as Executor
    participant S as External Service
    participant D as Database
    
    Note over C,D: Trace ID: abc123
    
    C->>A: Request [span: client-request]
    A->>W: Publish Event [span: event-publish]
    W->>R: Read Stream [span: stream-read]
    W->>E: Execute [span: execution]
    E->>S: API Call [span: service-call]
    S-->>E: Response [span: service-response]
    E->>D: Update DB [span: db-update]
    D-->>E: Ack [span: db-ack]
    E-->>W: Result [span: execution-result]
    W-->>A: Complete [span: event-complete]
    A-->>C: Response [span: api-response]
```

## Deployment Architecture

### 1. Container Deployment

```mermaid
graph TB
    subgraph "Kubernetes Cluster"
        subgraph "Worker Namespace"
            POD1[Worker Pod 1]
            POD2[Worker Pod 2]
            POD3[Worker Pod 3]
        end
        
        subgraph "Infrastructure Namespace"
            REDIS[Redis StatefulSet]
            DB[PostgreSQL StatefulSet]
        end
        
        subgraph "Monitoring Namespace"
            PROM[Prometheus]
            GRAF[Grafana]
        end
    end
    
    subgraph "External Services"
        EMAIL[Email Service]
        SLACK[Slack API]
        WEBHOOK[Webhook Endpoints]
    end
    
    POD1 --> REDIS
    POD2 --> REDIS
    POD3 --> REDIS
    
    POD1 --> DB
    POD2 --> DB
    POD3 --> DB
    
    POD1 --> EMAIL
    POD1 --> SLACK
    POD1 --> WEBHOOK
```

### 2. Service Mesh Architecture

```mermaid
graph TB
    subgraph "Service Mesh (Istio)"
        subgraph "Worker Services"
            W1[Worker Service 1]
            W2[Worker Service 2]
            W3[Worker Service 3]
        end
        
        subgraph "Sidecars"
            S1[Envoy Proxy 1]
            S2[Envoy Proxy 2]
            S3[Envoy Proxy 3]
        end
        
        subgraph "Control Plane"
            PILOT[Pilot]
            CITADEL[Citadel]
            GALLEY[Galley]
        end
    end
    
    W1 --- S1
    W2 --- S2
    W3 --- S3
    
    S1 --> PILOT
    S2 --> PILOT
    S3 --> PILOT
    
    PILOT --> CITADEL
    PILOT --> GALLEY
```

## Future Architecture Considerations

### 1. Event Sourcing

```mermaid
graph LR
    subgraph "Event Store"
        ES[(Event Store)]
        SNAP[(Snapshots)]
    end
    
    subgraph "Read Models"
        EXEC[Execution View]
        STATS[Statistics View]
        AUDIT[Audit View]
    end
    
    subgraph "Command Side"
        CMD[Commands]
        AGG[Aggregates]
        EVENTS[Events]
    end
    
    CMD --> AGG
    AGG --> EVENTS
    EVENTS --> ES
    
    ES --> EXEC
    ES --> STATS
    ES --> AUDIT
    
    SNAP --> EXEC
```

### 2. CQRS Implementation

```mermaid
graph TB
    subgraph "Command Side"
        API[Command API]
        CH[Command Handlers]
        AGG[Aggregates]
        ES[(Event Store)]
    end
    
    subgraph "Query Side"
        QA[Query API]
        QH[Query Handlers]
        RM[Read Models]
        DB[(Read Database)]
    end
    
    subgraph "Event Bus"
        EB[Event Bus]
    end
    
    API --> CH
    CH --> AGG
    AGG --> ES
    
    ES --> EB
    EB --> RM
    RM --> DB
    
    QA --> QH
    QH --> DB
```

---

This architecture documentation provides a comprehensive view of the AREA Worker System's design patterns, scalability considerations, and implementation details for future development and maintenance.