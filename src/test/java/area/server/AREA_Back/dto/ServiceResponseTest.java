package area.server.AREA_Back.dto;

import area.server.AREA_Back.entity.Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceResponseTest {

    private ServiceResponse serviceResponse;

    @BeforeEach
    void setUp() {
        serviceResponse = new ServiceResponse();
        serviceResponse.setId(UUID.randomUUID());
        serviceResponse.setKey("test-service");
        serviceResponse.setName("Test Service");
        serviceResponse.setAuth(Service.AuthType.OAUTH2);
        serviceResponse.setDocsUrl("https://docs.example.com");
        serviceResponse.setIconLightUrl("https://example.com/light.png");
        serviceResponse.setIconDarkUrl("https://example.com/dark.png");
        serviceResponse.setIsActive(true);
        serviceResponse.setCreatedAt(LocalDateTime.now());
        serviceResponse.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    void testServiceResponseSettersAndGetters() {
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

        serviceResponse.setId(id);
        serviceResponse.setKey(key);
        serviceResponse.setName(name);
        serviceResponse.setAuth(auth);
        serviceResponse.setDocsUrl(docsUrl);
        serviceResponse.setIconLightUrl(iconLightUrl);
        serviceResponse.setIconDarkUrl(iconDarkUrl);
        serviceResponse.setIsActive(isActive);
        serviceResponse.setCreatedAt(createdAt);
        serviceResponse.setUpdatedAt(updatedAt);

        assertEquals(id, serviceResponse.getId());
        assertEquals(key, serviceResponse.getKey());
        assertEquals(name, serviceResponse.getName());
        assertEquals(auth, serviceResponse.getAuth());
        assertEquals(docsUrl, serviceResponse.getDocsUrl());
        assertEquals(iconLightUrl, serviceResponse.getIconLightUrl());
        assertEquals(iconDarkUrl, serviceResponse.getIconDarkUrl());
        assertEquals(isActive, serviceResponse.getIsActive());
        assertEquals(createdAt, serviceResponse.getCreatedAt());
        assertEquals(updatedAt, serviceResponse.getUpdatedAt());
    }

    @Test
    void testServiceResponseEqualsAndHashCode() {
        ServiceResponse response1 = new ServiceResponse();
        response1.setId(UUID.randomUUID());
        response1.setKey("test-service");
        response1.setName("Test Service");
        
        ServiceResponse response2 = new ServiceResponse();
        response2.setId(response1.getId());
        response2.setKey("test-service");
        response2.setName("Test Service");
        
        assertEquals(response1, response2);
        assertEquals(response1.hashCode(), response2.hashCode());
    }

    @Test
    void testServiceResponseToString() {
        String responseString = serviceResponse.toString();
        assertNotNull(responseString);
        assertTrue(responseString.contains("test-service"));
        assertTrue(responseString.contains("Test Service"));
    }

    @Test
    void testServiceResponseConstructors() {
        // Test no-args constructor
        ServiceResponse response1 = new ServiceResponse();
        assertNotNull(response1);

        // Test all-args constructor
        UUID id = UUID.randomUUID();
        String key = "constructor-service";
        String name = "Constructor Service";
        Service.AuthType auth = Service.AuthType.NONE;
        String docsUrl = "https://constructor.example.com";
        String iconLightUrl = "https://constructor.com/light.png";
        String iconDarkUrl = "https://constructor.com/dark.png";
        Boolean isActive = false;
        LocalDateTime createdAt = LocalDateTime.now();
        LocalDateTime updatedAt = LocalDateTime.now().plusHours(1);

        ServiceResponse response2 = new ServiceResponse(id, key, name, auth, docsUrl, 
                                                       iconLightUrl, iconDarkUrl, isActive, 
                                                       createdAt, updatedAt);
        
        assertEquals(id, response2.getId());
        assertEquals(key, response2.getKey());
        assertEquals(name, response2.getName());
        assertEquals(auth, response2.getAuth());
        assertEquals(docsUrl, response2.getDocsUrl());
        assertEquals(iconLightUrl, response2.getIconLightUrl());
        assertEquals(iconDarkUrl, response2.getIconDarkUrl());
        assertEquals(isActive, response2.getIsActive());
        assertEquals(createdAt, response2.getCreatedAt());
        assertEquals(updatedAt, response2.getUpdatedAt());
    }

    @Test
    void testServiceResponseWithNullValues() {
        ServiceResponse response = new ServiceResponse();
        response.setId(UUID.randomUUID());
        response.setKey("test-service");
        response.setName("Test Service");
        response.setDocsUrl(null);
        response.setIconLightUrl(null);
        response.setIconDarkUrl(null);

        assertNotNull(response.getId());
        assertEquals("test-service", response.getKey());
        assertEquals("Test Service", response.getName());
        assertNull(response.getDocsUrl());
        assertNull(response.getIconLightUrl());
        assertNull(response.getIconDarkUrl());
    }

    @Test
    void testServiceResponseWithDifferentAuthTypes() {
        serviceResponse.setAuth(Service.AuthType.OAUTH2);
        assertEquals(Service.AuthType.OAUTH2, serviceResponse.getAuth());

        serviceResponse.setAuth(Service.AuthType.APIKEY);
        assertEquals(Service.AuthType.APIKEY, serviceResponse.getAuth());

        serviceResponse.setAuth(Service.AuthType.NONE);
        assertEquals(Service.AuthType.NONE, serviceResponse.getAuth());
    }
}