package area.server.AREA_Back.dto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserResponseTest {

    private UserResponse userResponse;

    @BeforeEach
    void setUp() {
        userResponse = new UserResponse();
        userResponse.setId(UUID.randomUUID());
        userResponse.setEmail("test@example.com");
        userResponse.setIsActive(true);
        userResponse.setIsAdmin(false);
        userResponse.setIsVerified(true);
        userResponse.setCreatedAt(LocalDateTime.now());
        userResponse.setLastLoginAt(LocalDateTime.now().plusDays(1));
        userResponse.setAvatarUrl("https://example.com/avatar.jpg");
    }

    @Test
    void testUserResponseSettersAndGetters() {
        UUID id = UUID.randomUUID();
        String email = "newtest@example.com";
        Boolean isActive = false;
        Boolean isAdmin = true;
        Boolean isVerified = false;
        LocalDateTime createdAt = LocalDateTime.now();
        LocalDateTime lastLoginAt = LocalDateTime.now().plusDays(2);
        String avatarUrl = "https://newexample.com/avatar.jpg";

        userResponse.setId(id);
        userResponse.setEmail(email);
        userResponse.setIsActive(isActive);
        userResponse.setIsAdmin(isAdmin);
        userResponse.setIsVerified(isVerified);
        userResponse.setCreatedAt(createdAt);
        userResponse.setLastLoginAt(lastLoginAt);
        userResponse.setAvatarUrl(avatarUrl);

        assertEquals(id, userResponse.getId());
        assertEquals(email, userResponse.getEmail());
        assertEquals(isActive, userResponse.getIsActive());
        assertEquals(isAdmin, userResponse.getIsAdmin());
        assertEquals(isVerified, userResponse.getIsVerified());
        assertEquals(createdAt, userResponse.getCreatedAt());
        assertEquals(lastLoginAt, userResponse.getLastLoginAt());
        assertEquals(avatarUrl, userResponse.getAvatarUrl());
    }

    @Test
    void testUserResponseEqualsAndHashCode() {
        UserResponse response1 = new UserResponse();
        response1.setId(UUID.randomUUID());
        response1.setEmail("test@example.com");

        UserResponse response2 = new UserResponse();
        response2.setId(response1.getId());
        response2.setEmail("test@example.com");

        assertEquals(response1, response2);
        assertEquals(response1.hashCode(), response2.hashCode());
    }

    @Test
    void testUserResponseToString() {
        String responseString = userResponse.toString();
        assertNotNull(responseString);
        assertTrue(responseString.contains("test@example.com"));
    }

    @Test
    void testUserResponseConstructors() {
        // Test no-args constructor
        UserResponse response1 = new UserResponse();
        assertNotNull(response1);

        // Test all-args constructor
        UUID id = UUID.randomUUID();
        String email = "constructor@example.com";
        String firstname = "John";
        String lastname = "Doe";
        Boolean isActive = true;
        Boolean isAdmin = false;
        Boolean isVerified = true;
        LocalDateTime createdAt = LocalDateTime.now();
        LocalDateTime lastLoginAt = LocalDateTime.now().plusDays(1);
        String avatarUrl = "https://constructor.com/avatar.jpg";

        UserResponse response2 = new UserResponse(id, email, firstname, lastname,
                                                 isActive, isAdmin, isVerified,
                                                 createdAt, lastLoginAt, avatarUrl);

        assertEquals(id, response2.getId());
        assertEquals(email, response2.getEmail());
        assertEquals(firstname, response2.getFirstname());
        assertEquals(lastname, response2.getLastname());
        assertEquals(isActive, response2.getIsActive());
        assertEquals(isAdmin, response2.getIsAdmin());
        assertEquals(isVerified, response2.getIsVerified());
        assertEquals(createdAt, response2.getCreatedAt());
        assertEquals(lastLoginAt, response2.getLastLoginAt());
        assertEquals(avatarUrl, response2.getAvatarUrl());
    }

    @Test
    void testUserResponseWithNullValues() {
        UserResponse response = new UserResponse();
        response.setId(UUID.randomUUID());
        response.setEmail("test@example.com");
        response.setLastLoginAt(null);
        response.setAvatarUrl(null);

        assertNotNull(response.getId());
        assertEquals("test@example.com", response.getEmail());
        assertNull(response.getLastLoginAt());
        assertNull(response.getAvatarUrl());
    }

    @Test
    void testUserResponseDefaultBooleanValues() {
        UserResponse response = new UserResponse();
        response.setIsActive(null);
        response.setIsAdmin(null);

        assertNull(response.getIsActive());
        assertNull(response.getIsAdmin());
    }
}