package area.server.AREA_Back.entity;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceTest {

    private Validator validator;
    private Service service;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        
        service = new Service();
        service.setId(UUID.randomUUID());
        service.setKey("test-service");
        service.setName("Test Service");
        service.setAuth(Service.AuthType.OAUTH2);
        service.setDocsUrl("https://docs.example.com");
        service.setIconLightUrl("https://example.com/light.png");
        service.setIconDarkUrl("https://example.com/dark.png");
        service.setIsActive(true);
        service.setCreatedAt(LocalDateTime.now());
        service.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    void testValidService() {
        Set<ConstraintViolation<Service>> violations = validator.validate(service);
        assertTrue(violations.isEmpty());
    }

    @Test
    void testServiceWithBlankKey() {
        service.setKey("");
        Set<ConstraintViolation<Service>> violations = validator.validate(service);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Service key is required")));
    }

    @Test
    void testServiceWithNullKey() {
        service.setKey(null);
        Set<ConstraintViolation<Service>> violations = validator.validate(service);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Service key is required")));
    }

    @Test
    void testServiceWithBlankName() {
        service.setName("");
        Set<ConstraintViolation<Service>> violations = validator.validate(service);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Service name is required")));
    }

    @Test
    void testServiceWithNullName() {
        service.setName(null);
        Set<ConstraintViolation<Service>> violations = validator.validate(service);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Service name is required")));
    }

    @Test
    void testServiceDefaultValues() {
        Service newService = new Service();
        newService.setKey("default-service");
        newService.setName("Default Service");
        
        assertEquals(Service.AuthType.OAUTH2, newService.getAuth());
        assertTrue(newService.getIsActive());
    }

    @Test
    void testServiceAuthTypes() {
        service.setAuth(Service.AuthType.APIKEY);
        assertEquals(Service.AuthType.APIKEY, service.getAuth());
        
        service.setAuth(Service.AuthType.OAUTH2);
        assertEquals(Service.AuthType.OAUTH2, service.getAuth());
        
        service.setAuth(Service.AuthType.NONE);
        assertEquals(Service.AuthType.NONE, service.getAuth());
    }

    @Test
    void testServiceEqualsAndHashCode() {
        Service service1 = new Service();
        service1.setId(UUID.randomUUID());
        service1.setKey("test-service");
        service1.setName("Test Service");
        
        Service service2 = new Service();
        service2.setId(service1.getId());
        service2.setKey("test-service");
        service2.setName("Test Service");
        
        assertEquals(service1, service2);
        assertEquals(service1.hashCode(), service2.hashCode());
    }

    @Test
    void testServiceToString() {
        String serviceString = service.toString();
        assertNotNull(serviceString);
        assertTrue(serviceString.contains("test-service"));
        assertTrue(serviceString.contains("Test Service"));
    }

    @Test
    void testServiceSettersAndGetters() {
        UUID id = UUID.randomUUID();
        String key = "new-service";
        String name = "New Service";
        Service.AuthType auth = Service.AuthType.APIKEY;
        String docsUrl = "https://newdocs.example.com";
        String iconLightUrl = "https://newexample.com/light.png";
        String iconDarkUrl = "https://newexample.com/dark.png";
        Boolean isActive = false;
        LocalDateTime createdAt = LocalDateTime.now();
        LocalDateTime updatedAt = LocalDateTime.now().plusHours(1);

        service.setId(id);
        service.setKey(key);
        service.setName(name);
        service.setAuth(auth);
        service.setDocsUrl(docsUrl);
        service.setIconLightUrl(iconLightUrl);
        service.setIconDarkUrl(iconDarkUrl);
        service.setIsActive(isActive);
        service.setCreatedAt(createdAt);
        service.setUpdatedAt(updatedAt);

        assertEquals(id, service.getId());
        assertEquals(key, service.getKey());
        assertEquals(name, service.getName());
        assertEquals(auth, service.getAuth());
        assertEquals(docsUrl, service.getDocsUrl());
        assertEquals(iconLightUrl, service.getIconLightUrl());
        assertEquals(iconDarkUrl, service.getIconDarkUrl());
        assertEquals(isActive, service.getIsActive());
        assertEquals(createdAt, service.getCreatedAt());
        assertEquals(updatedAt, service.getUpdatedAt());
    }

    @Test
    void testServiceConstructors() {
        // Test no-args constructor
        Service service1 = new Service();
        assertNotNull(service1);

        // Test using setters after construction
        Service service2 = new Service();
        service2.setKey("constructor-service");
        service2.setName("Constructor Service");
        
        assertEquals("constructor-service", service2.getKey());
        assertEquals("Constructor Service", service2.getName());
    }
}