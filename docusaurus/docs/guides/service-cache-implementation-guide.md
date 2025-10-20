# Services Catalog API with Redis Caching

## 📋 Summary

Complete implementation of a services catalog endpoint with Redis caching and automatic cache invalidation during admin updates.

## 🚀 Implemented Features

### ✅ New Endpoints
- `GET /api/services/catalog` - Complete services catalog with Redis cache
- `GET /api/services/catalog/enabled` - Active services catalog with Redis cache

### ✅ Redis Cache
- `services-catalog` cache with configurable 1-hour TTL
- Automatic fallback to database if Redis is unavailable
- JSON serialization with Jackson

### ✅ Automatic Invalidation
- Cache invalidation on service CREATE/UPDATE/DELETE operations
- `invalidateServicesCache()` method in ServiceCacheService

### ✅ Architecture Compliance
- **Service Layer**: `ServiceCacheService` for cache logic
- **Repository Layer**: Existing `ServiceRepository` reused
- **Controller Layer**: `ServiceController` enhanced with new endpoints

## 🏗️ Architecture

```
ServiceController
       ↓
ServiceCacheService
       ↓
ServiceRepository
       ↓
   Database
```

### Data Flow

1. **Read**: Controller → ServiceCacheService → Redis Cache → (fallback) Repository → Database
2. **Write**: Controller → Repository → Database → ServiceCacheService.invalidateCache()

## 📁 Created/Modified Files

### New Files
- `src/main/java/area/server/AREA_Back/service/ServiceCacheService.java`
- `src/test/java/area/server/AREA_Back/service/ServiceCacheServiceTest.java`
- `src/test/java/area/server/AREA_Back/controller/ServiceControllerCacheTest.java`
- `src/test/java/area/server/AREA_Back/config/RedisConfigTest.java`
- `src/test/resources/application-redis-test.properties`

### Modified Files
- `src/main/java/area/server/AREA_Back/config/RedisConfig.java` (added services-catalog cache)
- `src/main/java/area/server/AREA_Back/controller/ServiceController.java` (new endpoints + invalidation)

## 🔧 Configuration

### Redis Cache
```java
// Configurable TTL in RedisConfig.java
private static final int SERVICES_CATALOG_TTL_HOURS = 1;

cacheConfigurations.put("services-catalog", defaultConfig
    .entryTtl(Duration.ofHours(SERVICES_CATALOG_TTL_HOURS)));
```

### Environment Variables
```bash
# Redis Configuration (optional, defaults to localhost:6379)
REDIS_HOST=localhost
REDIS_PORT=6379
```

## 🧪 Testing

### Unit Tests
```bash
# Cache service
./gradlew test --tests="area.server.AREA_Back.service.ServiceCacheServiceTest"

# Cache endpoints
./gradlew test --tests="area.server.AREA_Back.controller.ServiceControllerCacheTest"

# Redis configuration
./gradlew test --tests="area.server.AREA_Back.config.RedisConfigTest"
```

### Manual Testing
```bash
# Start the application
./gradlew bootRun

# Test catalog endpoint
curl http://localhost:8080/api/services/catalog

# Test active services endpoint
curl http://localhost:8080/api/services/catalog/enabled
```

## 🔄 Cache Operation

### Caching Process
1. First call to `/api/services/catalog` → Database → Redis Cache (TTL: 1h)
2. Subsequent calls → Redis Cache directly

### Cache Invalidation
1. Admin creates/updates/deletes a service → `serviceCacheService.invalidateServicesCache()`
2. Cache cleared → Next call reloads from database

### Fallback Mechanism
If Redis is unavailable:
1. `getAllServicesCached()` throws exception
2. Controller calls `getAllServicesUncached()`
3. Data retrieved directly from database

## 📊 Metrics and Monitoring

### Logging
- Cache hit/miss logged at DEBUG level in ServiceCacheService
- Redis errors logged at WARN level with automatic fallback

### Useful Endpoints
- `/actuator/caches` - Cache status
- `/actuator/health` - Redis health included

## 🔒 Security

- Public endpoints (`/catalog`) for consultation
- Admin endpoints (`/services/*`) protected by existing Spring Security
- No sensitive data in cache (only public metadata)

## 🚀 Deployment

### Prerequisites
1. Accessible Redis server
2. Configuration of DATABASE_URL, DATABASE_USERNAME, DATABASE_PASSWORD

### Production
1. Adjust `SERVICES_CATALOG_TTL_HOURS` according to needs
2. Redis monitoring recommended
3. Redis backup/restore optional (cache is rebuildable)

## 🔧 Advanced Configuration

### Custom TTL per Environment
```properties
# application-prod.properties
spring.cache.redis.time-to-live=3600000  # 1 hour in ms

# application-dev.properties
spring.cache.redis.time-to-live=300000   # 5 minutes in ms
```

### Cache Metrics
```java
// Add to application.properties
management.metrics.cache.instrument-cache-manager=true
```

## 📈 Performance

### Expected Improvements
- **Without cache**: ~50-100ms (DB query)
- **With cache**: ~1-5ms (local Redis)
- **DB load reduction**: ~95% for repeated reads

### Capacity
- Lightweight cache: ~1KB per service
- 1000 services = ~1MB in Redis memory
- 1h TTL = automatic renewal

## 🔍 API Usage Examples

### Get All Services (Cached)
```bash
GET /api/services/catalog
Accept: application/json

Response:
[
  {
    "id": "uuid",
    "name": "GitHub",
    "description": "GitHub service integration",
    "enabled": true,
    "authType": "OAUTH2",
    "baseUrl": "https://api.github.com",
    "iconUrl": "https://github.com/favicon.ico",
    "createdAt": "2024-01-01T00:00:00Z",
    "updatedAt": "2024-01-01T00:00:00Z"
  }
]
```

### Get Enabled Services Only (Cached)
```bash
GET /api/services/catalog/enabled
Accept: application/json

Response: Same format but only enabled services
```

## 🐛 Troubleshooting

### Common Issues

#### Cache Not Working
1. Check Redis connection: `redis-cli ping`
2. Verify Redis configuration in logs
3. Check `spring.cache.type=redis` is set

#### Cache Not Invalidating
1. Verify admin operations call `invalidateServicesCache()`
2. Check cache manager configuration
3. Monitor cache eviction logs

#### Performance Issues
1. Monitor Redis memory usage
2. Adjust TTL based on update frequency
3. Consider cache warming strategies

### Debug Commands
```bash
# Check Redis cache content
redis-cli KEYS "*services*"
redis-cli GET "services-catalog::SimpleKey []"

# Monitor cache operations
redis-cli MONITOR

# Check application cache metrics
curl http://localhost:8080/actuator/caches
```

## 📚 Additional Resources

- [Spring Cache Documentation](https://spring.io/guides/gs/caching/)
- [Redis Configuration](https://docs.spring.io/spring-data/redis/docs/current/reference/html/)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)

## 🎯 Next Steps

1. **Monitoring**: Implement Redis monitoring and alerting
2. **Analytics**: Add cache hit/miss rate metrics
3. **Optimization**: Fine-tune TTL based on usage patterns
4. **Scaling**: Consider Redis clustering for high availability
5. **Documentation**: API documentation with cache behavior details
