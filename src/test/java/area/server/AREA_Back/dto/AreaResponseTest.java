package area.server.AREA_Back.dto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AreaResponseTest {

    private AreaResponse areaResponse;

    @BeforeEach
    void setUp() {
        areaResponse = new AreaResponse();
        areaResponse.setId(UUID.randomUUID());
        areaResponse.setName("Test Area");
        areaResponse.setDescription("Test Description");
        areaResponse.setEnabled(true);
        areaResponse.setUserId(UUID.randomUUID());
        areaResponse.setUserEmail("user@example.com");
        areaResponse.setCreatedAt(LocalDateTime.now());
        areaResponse.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    void testAreaResponseSettersAndGetters() {
        UUID id = UUID.randomUUID();
        String name = "New Area";
        String description = "New Description";
        Boolean enabled = false;
        UUID userId = UUID.randomUUID();
        String userEmail = "newuser@example.com";
        LocalDateTime createdAt = LocalDateTime.now();
        LocalDateTime updatedAt = LocalDateTime.now().plusHours(1);

        areaResponse.setId(id);
        areaResponse.setName(name);
        areaResponse.setDescription(description);
        areaResponse.setEnabled(enabled);
        areaResponse.setUserId(userId);
        areaResponse.setUserEmail(userEmail);
        areaResponse.setCreatedAt(createdAt);
        areaResponse.setUpdatedAt(updatedAt);

        assertEquals(id, areaResponse.getId());
        assertEquals(name, areaResponse.getName());
        assertEquals(description, areaResponse.getDescription());
        assertEquals(enabled, areaResponse.getEnabled());
        assertEquals(userId, areaResponse.getUserId());
        assertEquals(userEmail, areaResponse.getUserEmail());
        assertEquals(createdAt, areaResponse.getCreatedAt());
        assertEquals(updatedAt, areaResponse.getUpdatedAt());
    }

    @Test
    void testAreaResponseEqualsAndHashCode() {
        AreaResponse response1 = new AreaResponse();
        response1.setId(UUID.randomUUID());
        response1.setName("Test Area");

        AreaResponse response2 = new AreaResponse();
        response2.setId(response1.getId());
        response2.setName("Test Area");

        assertEquals(response1, response2);
        assertEquals(response1.hashCode(), response2.hashCode());
    }

    @Test
    void testAreaResponseToString() {
        String responseString = areaResponse.toString();
        assertNotNull(responseString);
        assertTrue(responseString.contains("Test Area"));
    }

    @Test
    void testAreaResponseWithNullValues() {
        AreaResponse response = new AreaResponse();
        response.setId(UUID.randomUUID());
        response.setName("Test Area");
        response.setDescription(null);
        response.setUserEmail(null);

        assertNotNull(response.getId());
        assertEquals("Test Area", response.getName());
        assertNull(response.getDescription());
        assertNull(response.getUserEmail());
    }
}