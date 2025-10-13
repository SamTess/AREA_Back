package area.server.AREA_Back.service.Auth;

import area.server.AREA_Back.dto.ServiceResponse;
import area.server.AREA_Back.entity.Service;
import area.server.AREA_Back.repository.ServiceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.util.List;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Service for managing cached service data.
 * Provides caching functionality with fallback to database when Redis is unavailable.
 */
@org.springframework.stereotype.Service
@Slf4j
public class ServiceCacheService {

    @Autowired
    private ServiceRepository serviceRepository;

    private final MeterRegistry meterRegistry;

    private Counter getAllServicesCalls;
    private Counter getEnabledServicesCalls;
    private Counter cacheEvictions;
    private Counter databaseFallbacks;
    private Counter cacheAvailabilityChecks;

    @Autowired
    public ServiceCacheService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Gets all services from cache or database as fallback.
     * Cache key: 'all-services'
     * TTL: 1 hour (configured in RedisConfig)
     *
     * @return List of all services
     */
    @Cacheable(value = "services-catalog", key = "'all-services'")
    public List<ServiceResponse> getAllServicesCached() {
        log.debug("Fetching all services from database (cache miss or unavailable)");
        getAllServicesCalls.increment();
        databaseFallbacks.increment();
        try {
            List<Service> services = serviceRepository.findAll();
            return services.stream()
                    .map(this::convertToResponse)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to fetch services from database", e);
            throw new RuntimeException("Unable to fetch services", e);
        }
    }

    /**
     * Gets enabled services from cache or database as fallback.
     * Cache key: 'enabled-services'
     * TTL: 1 hour (configured in RedisConfig)
     *
     * @return List of enabled services
     */
    @Cacheable(value = "services-catalog", key = "'enabled-services'")
    public List<ServiceResponse> getEnabledServicesCached() {
        log.debug("Fetching enabled services from database (cache miss or unavailable)");
        getEnabledServicesCalls.increment();
        databaseFallbacks.increment();
        try {
            List<Service> services = serviceRepository.findAllEnabledServices();
            return services.stream()
                    .map(this::convertToResponse)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to fetch enabled services from database", e);
            throw new RuntimeException("Unable to fetch enabled services", e);
        }
    }

    /**
     * Invalidates all service cache entries.
     * Called when services are created, updated, or deleted.
     */
    @CacheEvict(value = "services-catalog", allEntries = true)
    public void invalidateServicesCache() {
        log.info("Invalidating services cache");
        cacheEvictions.increment();
    }

    /**
     * Gets all services without caching (direct database access).
     * Used for admin operations or when cache should be bypassed.
     *
     * @return List of all services from database
     */
    public List<ServiceResponse> getAllServicesUncached() {
        log.debug("Fetching all services from database (uncached)");
        List<Service> services = serviceRepository.findAll();
        return services.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Checks if Redis cache is available.
     *
     * @return true if Redis is available, false otherwise
     */
    public boolean isCacheAvailable() {
        cacheAvailabilityChecks.increment();
        try {
            getAllServicesCached();
            return true;
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis cache is not available: { }", e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("Cache availability check failed: { }", e.getMessage());
            return false;
        }
    }

    /**
     * Converts Service entity to ServiceResponse DTO.
     *
     * @param service Service entity
     * @return ServiceResponse DTO
     */
    private ServiceResponse convertToResponse(Service service) {
        ServiceResponse response = new ServiceResponse();
        response.setId(service.getId());
        response.setKey(service.getKey());
        response.setName(service.getName());
        response.setAuth(service.getAuth());
        response.setDocsUrl(service.getDocsUrl());
        response.setIconLightUrl(service.getIconLightUrl());
        response.setIconDarkUrl(service.getIconDarkUrl());
        response.setIsActive(service.getIsActive());
        response.setCreatedAt(service.getCreatedAt());
        response.setUpdatedAt(service.getUpdatedAt());
        return response;
    }

    @PostConstruct
    private void init() {
        getAllServicesCalls = Counter.builder("service_cache.get_all_services.calls")
                .description("Total number of getAllServicesCached calls")
                .register(meterRegistry);

        getEnabledServicesCalls = Counter.builder("service_cache.get_enabled_services.calls")
                .description("Total number of getEnabledServicesCached calls")
                .register(meterRegistry);

        cacheEvictions = Counter.builder("service_cache.evictions")
                .description("Total number of cache evictions")
                .register(meterRegistry);

        databaseFallbacks = Counter.builder("service_cache.database_fallbacks")
                .description("Total number of database fallbacks when cache fails")
                .register(meterRegistry);

        cacheAvailabilityChecks = Counter.builder("service_cache.availability_checks")
                .description("Total number of cache availability checks")
                .register(meterRegistry);
    }
}