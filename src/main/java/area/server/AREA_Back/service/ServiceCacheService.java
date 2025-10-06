package area.server.AREA_Back.service;

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

/**
 * Service for managing cached service data.
 * Provides caching functionality with fallback to database when Redis is unavailable.
 */
@org.springframework.stereotype.Service
@Slf4j
public class ServiceCacheService {

    @Autowired
    private ServiceRepository serviceRepository;

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
        try {
            // Try to access cache - this will fail if Redis is down
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
}