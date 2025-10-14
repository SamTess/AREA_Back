package area.server.AREA_Back.service;

import area.server.AREA_Back.dto.ServiceResponse;
import area.server.AREA_Back.entity.Service;
import area.server.AREA_Back.repository.ServiceRepository;
import area.server.AREA_Back.service.Auth.ServiceCacheService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceCacheServiceTest {

    @Mock
    private ServiceRepository serviceRepository;

    @Mock
    private CacheManager cacheManager;

    private SimpleMeterRegistry meterRegistry;

    private ServiceCacheService serviceCacheService;

    private Service testService1;
    private Service testService2;
    private List<Service> testServices;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        serviceCacheService = new ServiceCacheService(meterRegistry);
        // Inject the mocked repository
        ReflectionTestUtils.setField(serviceCacheService, "serviceRepository", serviceRepository);
        // Initialize metrics
        ReflectionTestUtils.invokeMethod(serviceCacheService, "init");

        testService1 = new Service();
        testService1.setId(UUID.randomUUID());
        testService1.setKey("test-service-1");
        testService1.setName("Test Service 1");
        testService1.setAuth(Service.AuthType.OAUTH2);
        testService1.setDocsUrl("https://docs.example.com");
        testService1.setIconLightUrl("https://example.com/light.png");
        testService1.setIconDarkUrl("https://example.com/dark.png");
        testService1.setIsActive(true);
        testService1.setCreatedAt(LocalDateTime.now());
        testService1.setUpdatedAt(LocalDateTime.now());

        testService2 = new Service();
        testService2.setId(UUID.randomUUID());
        testService2.setKey("test-service-2");
        testService2.setName("Test Service 2");
        testService2.setAuth(Service.AuthType.APIKEY);
        testService2.setDocsUrl("https://docs2.example.com");
        testService2.setIconLightUrl("https://example2.com/light.png");
        testService2.setIconDarkUrl("https://example2.com/dark.png");
        testService2.setIsActive(true);
        testService2.setCreatedAt(LocalDateTime.now());
        testService2.setUpdatedAt(LocalDateTime.now());

        testServices = Arrays.asList(testService1, testService2);
    }

    @Test
    void testGetAllServicesCached() {
        // Given
        when(serviceRepository.findAll()).thenReturn(testServices);

        // When
        List<ServiceResponse> result = serviceCacheService.getAllServicesCached();

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("test-service-1", result.get(0).getKey());
        assertEquals("test-service-2", result.get(1).getKey());
        verify(serviceRepository, times(1)).findAll();
    }

    @Test
    void testGetEnabledServicesCached() {
        // Given
        when(serviceRepository.findAllEnabledServices()).thenReturn(testServices);

        // When
        List<ServiceResponse> result = serviceCacheService.getEnabledServicesCached();

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("test-service-1", result.get(0).getKey());
        assertEquals("test-service-2", result.get(1).getKey());
        verify(serviceRepository, times(1)).findAllEnabledServices();
    }

    @Test
    void testGetAllServicesUncached() {
        // Given
        when(serviceRepository.findAll()).thenReturn(testServices);

        // When
        List<ServiceResponse> result = serviceCacheService.getAllServicesUncached();

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("test-service-1", result.get(0).getKey());
        assertEquals("test-service-2", result.get(1).getKey());
        verify(serviceRepository, times(1)).findAll();
    }

    @Test
    void testGetAllServicesCachedDatabaseFailure() {
        // Given
        when(serviceRepository.findAll()).thenThrow(new RuntimeException("Database connection failed"));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> serviceCacheService.getAllServicesCached());
        assertEquals("Unable to fetch services", exception.getMessage());
        verify(serviceRepository, times(1)).findAll();
    }

    @Test
    void testGetEnabledServicesCachedDatabaseFailure() {
        // Given
        when(serviceRepository.findAllEnabledServices()).thenThrow(new RuntimeException("Database connection failed"));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> serviceCacheService.getEnabledServicesCached());
        assertEquals("Unable to fetch enabled services", exception.getMessage());
        verify(serviceRepository, times(1)).findAllEnabledServices();
    }

    @Test
    void testInvalidateServicesCache() {
        // When & Then - Should not throw any exception
        assertDoesNotThrow(() -> serviceCacheService.invalidateServicesCache());
    }

    @Test
    void testConvertToResponse() {
        // Given
        when(serviceRepository.findAll()).thenReturn(Arrays.asList(testService1));

        // When
        List<ServiceResponse> result = serviceCacheService.getAllServicesCached();

        // Then
        ServiceResponse response = result.get(0);
        assertEquals(testService1.getId(), response.getId());
        assertEquals(testService1.getKey(), response.getKey());
        assertEquals(testService1.getName(), response.getName());
        assertEquals(testService1.getAuth(), response.getAuth());
        assertEquals(testService1.getDocsUrl(), response.getDocsUrl());
        assertEquals(testService1.getIconLightUrl(), response.getIconLightUrl());
        assertEquals(testService1.getIconDarkUrl(), response.getIconDarkUrl());
        assertEquals(testService1.getIsActive(), response.getIsActive());
        assertEquals(testService1.getCreatedAt(), response.getCreatedAt());
        assertEquals(testService1.getUpdatedAt(), response.getUpdatedAt());
    }

    @Test
    void testGetAllServicesCachedWithEmptyList() {
        // Given
        when(serviceRepository.findAll()).thenReturn(Arrays.asList());

        // When
        List<ServiceResponse> result = serviceCacheService.getAllServicesCached();

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(serviceRepository, times(1)).findAll();
    }

    @Test
    void testGetEnabledServicesCachedWithEmptyList() {
        // Given
        when(serviceRepository.findAllEnabledServices()).thenReturn(Arrays.asList());

        // When
        List<ServiceResponse> result = serviceCacheService.getEnabledServicesCached();

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(serviceRepository, times(1)).findAllEnabledServices();
    }

    @Test
    void testServiceConversionWithNullFields() {
        // Given
        Service serviceWithNulls = new Service();
        serviceWithNulls.setId(UUID.randomUUID());
        serviceWithNulls.setKey("minimal-service");
        serviceWithNulls.setName("Minimal Service");
        serviceWithNulls.setAuth(Service.AuthType.NONE);
        serviceWithNulls.setIsActive(false);
        serviceWithNulls.setCreatedAt(LocalDateTime.now());
        serviceWithNulls.setUpdatedAt(LocalDateTime.now());
        // docsUrl, iconLightUrl, iconDarkUrl are null

        when(serviceRepository.findAll()).thenReturn(Arrays.asList(serviceWithNulls));

        // When
        List<ServiceResponse> result = serviceCacheService.getAllServicesCached();

        // Then
        ServiceResponse response = result.get(0);
        assertEquals(serviceWithNulls.getId(), response.getId());
        assertEquals("minimal-service", response.getKey());
        assertEquals("Minimal Service", response.getName());
        assertEquals(Service.AuthType.NONE, response.getAuth());
        assertNull(response.getDocsUrl());
        assertNull(response.getIconLightUrl());
        assertNull(response.getIconDarkUrl());
        assertFalse(response.getIsActive());
    }
}