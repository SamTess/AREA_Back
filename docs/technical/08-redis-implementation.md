# Redis Implementation and Security - Technical Documentation

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Implementation Details](#implementation-details)
4. [Security Features](#security-features)
5. [Configuration](#configuration)
6. [TLS/SSL Implementation](#tlsssl-implementation)
7. [Caching Strategy](#caching-strategy)
8. [Redis Streams](#redis-streams)
9. [Performance Optimization](#performance-optimization)
10. [Monitoring and Logging](#monitoring-and-logging)
11. [Production Considerations](#production-considerations)
12. [Troubleshooting](#troubleshooting)

---

## Overview

### Purpose

Redis is implemented in the AREA backend as a high-performance in-memory data store serving multiple purposes:

- **Caching Layer:** Service definitions, user sessions, and API responses
- **Event Streaming:** Real-time AREA (Action-Reaction) event processing via Redis Streams
- **Rate Limiting:** API request throttling and abuse prevention
- **Session Management:** User authentication state and temporary data

### Technology Stack

- **Redis:** Latest version (8.2.1)
- **Spring Data Redis:** Integration framework
- **Lettuce:** Async Redis client (default in Spring Boot)
- **Docker:** Containerized deployment
- **TLS/SSL:** Encrypted communication (TLSv1.2/1.3)

---

## Architecture

### System Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    Docker Network (area-network)            │
│                                                               │
│  ┌──────────────┐          ┌──────────────┐                 │
│  │              │   TLS    │              │                 │
│  │ AREA Backend │◄────────►│  Redis       │                 │
│  │  (Java/      │  :6379   │  Server      │                 │
│  │   Spring)    │          │              │                 │
│  └──────────────┘          └──────────────┘                 │
│       │                           │                          │
│       │                           │                          │
│  ┌────▼──────┐              ┌────▼──────┐                  │
│  │ Postgres  │              │  Volumes  │                  │
│  │ Database  │              │  - Data   │                  │
│  └───────────┘              │  - Certs  │                  │
│                             └───────────┘                  │
└─────────────────────────────────────────────────────────────┘
```

### Component Interaction

1. **Backend Application** connects to Redis via TLS-encrypted connection
2. **Redis Streams** handle asynchronous AREA event processing
3. **Cache Manager** stores frequently accessed data
4. **Health Checks** monitor Redis availability
5. **Volume Persistence** ensures data durability

---

## Implementation Details

### Spring Configuration

#### RedisConfig.java

The main configuration class handles:

```java
@Configuration
@EnableCaching
@EnableRedisRepositories
@Profile("!unit-test")
@Slf4j
@RequiredArgsConstructor
public class RedisConfig {
    // Connection factory with TLS support
    // Cache manager with custom TTL configurations
    // Stream message listener for event processing
}
```

**Key Features:**
- Conditional SSL/TLS activation via environment variables
- Custom cache configurations per data type
- Redis Streams integration for event-driven architecture
- Connection pooling for optimal performance

### Connection Factory

```java
@Bean
public LettuceConnectionFactory redisConnectionFactory() {
    RedisStandaloneConfiguration redisConfig = 
        new RedisStandaloneConfiguration();
    redisConfig.setHostName(redisHost);
    redisConfig.setPort(redisPort);
    redisConfig.setPassword(redisPassword);
    
    // TLS configuration
    if (sslEnabled) {
        clientConfigBuilder
            .useSsl()
            .disablePeerVerification()  // Dev only!
            .and()
            .clientOptions(clientOptions);
    }
    
    return new LettuceConnectionFactory(redisConfig, 
                                       clientConfigBuilder.build());
}
```

### Redis Template

Provides a high-level abstraction for Redis operations:

```java
@Bean
public RedisTemplate<String, Object> redisTemplate(
        RedisConnectionFactory connectionFactory) {
    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(connectionFactory);
    
    // JSON serialization for complex objects
    template.setKeySerializer(new StringRedisSerializer());
    template.setValueSerializer(
        new GenericJackson2JsonRedisSerializer());
    
    return template;
}
```

---

## Security Features

### Multi-Layer Security Architecture

```
┌─────────────────────────────────────────────────────────┐
│  Security Layer 1: Network Isolation                   │
│  - Docker network isolation (area-network)             │
│  - No external port exposure                           │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│  Security Layer 2: TLS/SSL Encryption                  │
│  - TLSv1.2 and TLSv1.3 only                           │
│  - Strong cipher suites                                │
│  - Certificate-based authentication                    │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│  Security Layer 3: Authentication                      │
│  - Password-based authentication (requirepass)         │
│  - 44-character cryptographically strong password      │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│  Security Layer 4: Command Restriction                 │
│  - Dangerous commands disabled                         │
│  - FLUSHDB, CONFIG, DEBUG, EVAL blocked                │
└─────────────────────────────────────────────────────────┘
```

### 1. Network Security

**Docker Network Isolation:**
```yaml
networks:
  area-network:
    driver: bridge
```

**No Public Exposure:**
```yaml
# Redis port NOT exposed to host
ports: []  # Internal only: 6379/tcp
```

**Benefits:**
- ✅ Redis accessible only within Docker network
- ✅ No direct internet access
- ✅ Additional firewall layer via Docker

### 2. TLS/SSL Encryption

**Configuration:** `redis.conf`

```properties
# Disable non-TLS connections
port 0

# Enable TLS on standard port
tls-port 6379

# Certificate configuration
tls-cert-file /usr/local/etc/redis/certs/redis-cert.pem
tls-key-file /usr/local/etc/redis/certs/redis-key.pem
tls-ca-cert-file /usr/local/etc/redis/certs/ca-cert.pem

# Security protocols
tls-protocols "TLSv1.2 TLSv1.3"

# Secure cipher suites
tls-ciphers DEFAULT:!MEDIUM:!LOW:!aNULL:!eNULL:!EXPORT:!DES:!3DES:!MD5:!PSK:!RC4
```

**Protocol Support:**
- ✅ TLSv1.2 (minimum)
- ✅ TLSv1.3 (preferred)
- ❌ TLSv1.0, TLSv1.1 (deprecated, disabled)

### 3. Authentication

**Password Requirements:**
- Minimum 32 characters (current: 44)
- Base64-encoded random bytes
- Stored in environment variables (`.env`)
- Never hardcoded in source code

**Configuration:**
```properties
requirepass ${REDIS_PASSWORD}
protected-mode yes
```

### 4. Command Restriction

**Disabled Dangerous Commands:**

```properties
# Database destruction
rename-command FLUSHDB ""
rename-command FLUSHALL ""

# Configuration tampering
rename-command CONFIG ""

# Debugging/introspection
rename-command DEBUG ""

# Script execution
rename-command EVAL ""
rename-command EVALSHA ""

# Server control
rename-command SHUTDOWN ""
```

**Rationale:**

| Command | Risk | Impact |
|---------|------|--------|
| `FLUSHDB` | Database wipe | Data loss |
| `CONFIG` | Runtime config change | Security bypass |
| `EVAL` | Arbitrary Lua execution | Code injection |
| `DEBUG` | Memory inspection | Information disclosure |
| `SHUTDOWN` | Server termination | Denial of Service |

### 5. Resource Limits

**Memory Management:**
```properties
maxmemory 256mb
maxmemory-policy allkeys-lru
```

**Connection Limits:**
```properties
maxclients 10000
timeout 300  # 5 minutes idle timeout
```

**Benefits:**
- Prevents memory exhaustion attacks
- Limits concurrent connections
- Automatic eviction of old data

---

## Configuration

### Environment Variables

**File:** `.env`

```bash
# Redis Connection
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=<strong-password-here>
REDIS_DATABASE=0

# Security
REDIS_SSL=true
REDIS_TIMEOUT=2000ms

# Connection Pool
REDIS_POOL_MAX_ACTIVE=8
REDIS_POOL_MAX_IDLE=8
REDIS_POOL_MIN_IDLE=0
REDIS_POOL_MAX_WAIT=-1ms

# Redis Streams
REDIS_STREAM_NAME=areas:events
REDIS_CONSUMER_GROUP=area-processors
REDIS_CONSUMER_NAME=<auto-generated>
REDIS_STREAM_BATCH_SIZE=10
REDIS_STREAM_THREAD_POOL_SIZE=4
REDIS_STREAM_POLL_TIMEOUT_MS=100
```

### Docker Configuration

**File:** `Docker/docker-compose.back.yaml`

```yaml
services:
  redis:
    build:
      context: ./redis
      dockerfile: Dockerfile
    container_name: area-redis
    environment:
      - REDIS_PASSWORD=${REDIS_PASSWORD}
      - REDIS_SSL=${REDIS_SSL:-false}
    volumes:
      - redis_data:/data
      - redis_certs:/usr/local/etc/redis/certs
    healthcheck:
      test: ["CMD", "sh", "-c", "if [ \"$$REDIS_SSL\" = \"true\" ]; then redis-cli --tls --cert /usr/local/etc/redis/certs/redis-cert.pem --key /usr/local/etc/redis/certs/redis-key.pem --cacert /usr/local/etc/redis/certs/ca-cert.pem --no-auth-warning -a $$REDIS_PASSWORD ping 2>/dev/null | grep PONG; else redis-cli --no-auth-warning -a $$REDIS_PASSWORD ping 2>/dev/null | grep PONG; fi"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 10s
    networks:
      - area-network
    restart: unless-stopped

volumes:
  redis_data:
  redis_certs:

networks:
  area-network:
    driver: bridge
```

---

## TLS/SSL Implementation

### Certificate Generation

**File:** `Docker/redis/generate-certs.sh`

Automatically generates self-signed certificates on first run:

```bash
#!/bin/bash
set -e

CERT_DIR="/usr/local/etc/redis/certs"
DAYS_VALID=365

# Generate CA certificate
openssl genrsa -out ca-key.pem 4096
openssl req -new -x509 -days $DAYS_VALID -key ca-key.pem \
    -out ca-cert.pem \
    -subj "/C=US/ST=State/L=City/O=AREA/CN=Redis CA"

# Generate server certificate
openssl genrsa -out redis-key.pem 4096
openssl req -new -key redis-key.pem -out redis-req.pem \
    -subj "/C=US/ST=State/L=City/O=AREA/CN=redis"
openssl x509 -req -days $DAYS_VALID -in redis-req.pem \
    -CA ca-cert.pem -CAkey ca-key.pem -CAcreateserial \
    -out redis-cert.pem

# Set permissions
chmod 644 ca-cert.pem redis-cert.pem
chmod 600 ca-key.pem redis-key.pem
```

### TLS Handshake Flow

```
Client (Backend)                    Server (Redis)
     │                                   │
     │──── TLS ClientHello ─────────────►│
     │                                   │
     │◄─── TLS ServerHello ──────────────│
     │◄─── Certificate ──────────────────│
     │◄─── ServerKeyExchange ────────────│
     │◄─── ServerHelloDone ──────────────│
     │                                   │
     │──── ClientKeyExchange ───────────►│
     │──── ChangeCipherSpec ────────────►│
     │──── Finished ────────────────────►│
     │                                   │
     │◄─── ChangeCipherSpec ─────────────│
     │◄─── Finished ─────────────────────│
     │                                   │
     │══════ Encrypted Data ════════════►│
     │◄═══════ Encrypted Data ═══════════│
```

### Certificate Management

**Lifecycle:**
1. **Generation:** Automatic on container first run
2. **Storage:** Persistent volume (`redis_certs`)
3. **Rotation:** Manual (365-day validity)
4. **Renewal:** Replace certificates before expiration

**Files:**
- `ca-cert.pem` - Certificate Authority public certificate
- `ca-key.pem` - CA private key (keep secure!)
- `redis-cert.pem` - Redis server certificate
- `redis-key.pem` - Redis server private key

---

## Caching Strategy

### Cache Hierarchies

```
┌─────────────────────────────────────────────────────────┐
│  Application Layer (Spring Boot)                        │
└────────────┬────────────────────────────────────────────┘
             │
┌────────────▼────────────────────────────────────────────┐
│  Redis Cache Manager                                    │
│  ┌─────────────────────────────────────────────────┐   │
│  │ services-catalog     TTL: 1 hour                │   │
│  │ - Service definitions, action templates         │   │
│  └─────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────┐   │
│  │ services             TTL: 1 hour                │   │
│  │ - User service configurations                   │   │
│  └─────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────┐   │
│  │ actionDefinitions    TTL: 30 minutes            │   │
│  │ - AREA action definitions                       │   │
│  └─────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────┐   │
│  │ userSessions         TTL: 20 minutes            │   │
│  │ - Active user sessions, authentication state    │   │
│  └─────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────┐   │
│  │ tokens               TTL: 15 minutes            │   │
│  │ - API tokens, temporary credentials             │   │
│  └─────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────┐   │
│  │ rateLimits           TTL: 1 minute              │   │
│  │ - API rate limiting counters                    │   │
│  └─────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

### Cache Configuration

```java
@Bean
public RedisCacheManager cacheManager(
        RedisConnectionFactory connectionFactory) {
    
    RedisCacheConfiguration defaultConfig = 
        RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(30))
            .serializeKeysWith(/*...*/)
            .serializeValuesWith(/*...*/);
    
    Map<String, RedisCacheConfiguration> caches = new HashMap<>();
    
    caches.put("services-catalog", defaultConfig
        .entryTtl(Duration.ofHours(1)));
    
    caches.put("tokens", defaultConfig
        .entryTtl(Duration.ofMinutes(15)));
    
    // ... more cache configurations
    
    return RedisCacheManager.builder(connectionFactory)
        .cacheDefaults(defaultConfig)
        .withInitialCacheConfigurations(caches)
        .build();
}
```

### Cache Usage Patterns

**1. Service Catalog Caching:**
```java
@Cacheable(value = "services-catalog", key = "#serviceId")
public ServiceDefinition getServiceDefinition(String serviceId) {
    // Expensive operation - only called on cache miss
    return serviceRepository.findById(serviceId);
}
```

**2. Rate Limiting:**
```java
@Cacheable(value = "rateLimits", key = "#userId")
public RateLimitInfo getRateLimit(String userId) {
    return new RateLimitInfo(/* ... */);
}
```

**3. Session Management:**
```java
@Cacheable(value = "userSessions", key = "#sessionId")
public UserSession getSession(String sessionId) {
    return sessionStore.findById(sessionId);
}
```

---

## Redis Streams

### Event-Driven Architecture

Redis Streams enable asynchronous processing of AREA events:

```
┌────────────┐         ┌─────────────┐         ┌────────────┐
│            │ Produce │             │ Consume │            │
│  Triggers  │────────►│   Redis     │────────►│ AREA Event │
│  (Actions) │  Event  │   Stream    │  Event  │ Processor  │
│            │         │             │         │            │
└────────────┘         └─────────────┘         └────────────┘
                             │
                             │ Persistence
                             ▼
                       ┌─────────────┐
                       │  AOF/RDB    │
                       │  Snapshots  │
                       └─────────────┘
```

### Stream Configuration

```java
@Bean
public StreamMessageListenerContainer<String, ?> 
        streamMessageListenerContainer(
            RedisConnectionFactory connectionFactory) {
    
    StreamMessageListenerContainerOptions<String, ?> options = 
        StreamMessageListenerContainerOptions.builder()
            .batchSize(10)
            .executor(Executors.newFixedThreadPool(4))
            .pollTimeout(Duration.ofMillis(100))
            .build();
    
    return StreamMessageListenerContainer.create(
        connectionFactory, options);
}
```

### Stream Properties

```properties
# Stream name
REDIS_STREAM_NAME=areas:events

# Consumer group for load balancing
REDIS_CONSUMER_GROUP=area-processors

# Processing configuration
REDIS_STREAM_BATCH_SIZE=10
REDIS_STREAM_THREAD_POOL_SIZE=4
REDIS_STREAM_POLL_TIMEOUT_MS=100
```

### Event Processing Flow

1. **Action Triggered** → Event created and added to stream
2. **Stream Polling** → Listener continuously polls for new events
3. **Batch Processing** → Events processed in batches of 10
4. **Consumer Group** → Multiple instances share processing load
5. **Acknowledgment** → Successful processing acknowledged
6. **Error Handling** → Failed events requeued for retry

---

## Performance Optimization

### Connection Pooling

**Lettuce Configuration:**
```properties
REDIS_POOL_MAX_ACTIVE=8    # Maximum connections
REDIS_POOL_MAX_IDLE=8      # Idle connections kept alive
REDIS_POOL_MIN_IDLE=0      # Minimum idle connections
REDIS_POOL_MAX_WAIT=-1ms   # Wait indefinitely for connection
```

**Benefits:**
- ✅ Connection reuse (no TCP handshake overhead)
- ✅ Reduced latency for subsequent requests
- ✅ Better resource utilization

### Memory Optimization

```properties
# Eviction policy
maxmemory 256mb
maxmemory-policy allkeys-lru

# Persistence tuning
save 900 1      # Save after 900s if ≥1 key changed
save 300 10     # Save after 300s if ≥10 keys changed
save 60 10000   # Save after 60s if ≥10000 keys changed
```

**LRU (Least Recently Used) Strategy:**
- Automatically evicts oldest unused keys
- Prevents memory overflow
- Maintains hot data in cache

### Network Performance

```properties
# Keep connections alive
tcp-keepalive 300

# Timeout idle clients
timeout 300

# Disable slow commands
rename-command KEYS ""  # Use SCAN instead
```

---

## Monitoring and Logging

### Health Checks

**Docker Health Check:**
```yaml
healthcheck:
  test: ["CMD", "redis-cli", "--tls", "--cert", 
         "/path/to/cert.pem", "-a", "$$REDIS_PASSWORD", "ping"]
  interval: 30s
  timeout: 10s
  retries: 3
  start_period: 10s
```

**Status Indicators:**
- `healthy` - Redis responding to PING
- `unhealthy` - Connection failed or timeout
- `starting` - Container initializing

### Logging Configuration

```properties
# Log level
loglevel notice

# Log to stdout (Docker captures)
logfile ""

# Slow query log
slowlog-log-slower-than 10000  # 10ms threshold
slowlog-max-len 128            # Keep last 128 slow queries
```

### Metrics to Monitor

| Metric | Description | Alert Threshold |
|--------|-------------|-----------------|
| `connected_clients` | Active connections | > 8000 (80% of max) |
| `used_memory` | Memory consumption | > 204MB (80% of max) |
| `evicted_keys` | Keys evicted by LRU | High rate indicates insufficient memory |
| `keyspace_hits` | Cache hit rate | < 80% suggests inefficient caching |
| `total_commands_processed` | Throughput | Sudden drops indicate issues |

---

## Production Considerations

### ⚠️ Critical: Remove Development-Only Settings

**Current Development Configuration:**
```java
clientConfigBuilder
    .useSsl()
    .disablePeerVerification()  // ⚠️ DANGEROUS IN PRODUCTION
    .and()
```

**Production Configuration:**
```java
clientConfigBuilder
    .useSsl()
    // Remove .disablePeerVerification()
    .and()
```

### Production Checklist

#### Security
- [ ] Replace self-signed certificates with CA-signed (Let's Encrypt)
- [ ] Remove `.disablePeerVerification()` from Java configuration
- [ ] Enable mutual TLS (`tls-auth-clients yes`)
- [ ] Rotate passwords every 90 days
- [ ] Use Secrets Manager (AWS Secrets, Vault, etc.)
- [ ] Enable audit logging
- [ ] Disable `KEYS` command completely

#### Reliability
- [ ] Configure Redis Sentinel or Cluster for HA
- [ ] Set up automated backups (RDB + AOF)
- [ ] Implement backup retention policy
- [ ] Configure monitoring and alerting
- [ ] Set up log aggregation (ELK, Datadog, etc.)
- [ ] Plan disaster recovery procedures

#### Performance
- [ ] Tune memory limits based on workload
- [ ] Optimize eviction policies
- [ ] Configure appropriate TTLs
- [ ] Monitor slow queries
- [ ] Benchmark under expected load

#### Compliance
- [ ] Ensure data encryption at rest
- [ ] Configure data retention policies
- [ ] Document security controls
- [ ] Schedule security audits

### Certificate Renewal (Production)

**Using Let's Encrypt:**
```bash
# Install Certbot
sudo apt install certbot

# Generate certificates
sudo certbot certonly --standalone \
    -d redis.yourdomain.com \
    --email admin@yourdomain.com

# Auto-renewal cron job
0 2 * * * certbot renew --quiet \
    --deploy-hook "docker restart area-redis"
```

**Certificate Monitoring:**
```bash
#!/bin/bash
# Check certificate expiration
CERT="/etc/letsencrypt/live/redis.yourdomain.com/cert.pem"
DAYS_LEFT=$(openssl x509 -enddate -noout -in "$CERT" | \
    awk -F= '{print $2}' | xargs -I{} date -d {} +%s | \
    awk -v now=$(date +%s) '{print int(($1-now)/86400)}')

if [ $DAYS_LEFT -lt 30 ]; then
    echo "⚠️ Certificate expires in $DAYS_LEFT days!"
    # Send alert
fi
```

---

## Troubleshooting

### Common Issues

#### 1. Connection Refused

**Symptoms:**
```
io.lettuce.core.RedisConnectionException: 
Unable to connect to redis:6379
```

**Solutions:**
- ✅ Check Redis container is running: `docker ps | grep redis`
- ✅ Verify network configuration: `docker network inspect area-network`
- ✅ Check firewall rules
- ✅ Verify environment variables are set

#### 2. TLS Certificate Errors

**Symptoms:**
```
error:0A000416:SSL routines::sslv3 alert certificate unknown
```

**Solutions:**
- ✅ Verify certificate paths in `redis.conf`
- ✅ Check certificate permissions (644 for certs, 600 for keys)
- ✅ Ensure certificates are not expired
- ✅ Validate certificate chain

#### 3. Authentication Failed

**Symptoms:**
```
NOAUTH Authentication required
```

**Solutions:**
- ✅ Verify `REDIS_PASSWORD` environment variable
- ✅ Check password in `.env` file
- ✅ Ensure `requirepass` in `redis.conf`
- ✅ Test with `redis-cli -a <password> ping`

#### 4. Memory Issues

**Symptoms:**
```
OOM command not allowed when used memory > 'maxmemory'
```

**Solutions:**
- ✅ Increase `maxmemory` limit
- ✅ Review eviction policy (`maxmemory-policy`)
- ✅ Check for memory leaks in application
- ✅ Analyze key sizes: `redis-cli --bigkeys`

### Debug Commands

```bash
# Check Redis logs
docker logs area-redis

# Connect to Redis CLI (TLS)
docker exec -it area-redis redis-cli \
    --tls \
    --cert /usr/local/etc/redis/certs/redis-cert.pem \
    --key /usr/local/etc/redis/certs/redis-key.pem \
    --cacert /usr/local/etc/redis/certs/ca-cert.pem \
    -a <REDIS_PASSWORD>

# Monitor Redis commands in real-time
docker exec -it area-redis redis-cli \
    --tls [...] -a <password> MONITOR

# Get Redis info
docker exec -it area-redis redis-cli \
    --tls [...] -a <password> INFO

# Check slow queries
docker exec -it area-redis redis-cli \
    --tls [...] -a <password> SLOWLOG GET 10
```

---

## References

### Official Documentation
- [Redis Documentation](https://redis.io/docs/)
- [Redis Security](https://redis.io/docs/manual/security/)
- [Redis TLS/SSL](https://redis.io/docs/manual/security/encryption/)
- [Spring Data Redis](https://docs.spring.io/spring-data/redis/docs/current/reference/html/)
- [Lettuce Documentation](https://lettuce.io/core/release/reference/)

### Related Documents
- [REDIS_TLS_PRODUCTION_GUIDE.md](./REDIS_TLS_PRODUCTION_GUIDE.md) - Production deployment guide
- [REDIS_SECURITY_AUDIT.md](./REDIS_SECURITY_AUDIT.md) - Security audit report
- [REDIS_TLS_IMPLEMENTATION.md](./REDIS_TLS_IMPLEMENTATION.md) - Implementation summary

---

## Appendix

### A. Environment Variables Reference

| Variable | Type | Default | Description |
|----------|------|---------|-------------|
| `REDIS_HOST` | String | `localhost` | Redis server hostname |
| `REDIS_PORT` | Integer | `6379` | Redis server port |
| `REDIS_PASSWORD` | String | - | Authentication password |
| `REDIS_SSL` | Boolean | `false` | Enable TLS/SSL |
| `REDIS_DATABASE` | Integer | `0` | Database index (0-15) |
| `REDIS_TIMEOUT` | Duration | `2000ms` | Connection timeout |
| `REDIS_POOL_MAX_ACTIVE` | Integer | `8` | Max active connections |
| `REDIS_POOL_MAX_IDLE` | Integer | `8` | Max idle connections |
| `REDIS_STREAM_NAME` | String | - | Stream key name |
| `REDIS_CONSUMER_GROUP` | String | - | Consumer group name |

### B. Redis Commands Cheat Sheet

```bash
# Key operations
SET key value
GET key
DEL key
EXISTS key
EXPIRE key seconds

# Hash operations
HSET hash field value
HGET hash field
HDEL hash field

# Stream operations
XADD stream * field value
XREAD STREAMS stream 0
XGROUP CREATE stream group 0

# Server
PING
INFO
CONFIG GET parameter
```

### C. Certificate Generation Script

See: `Docker/redis/generate-certs.sh`
