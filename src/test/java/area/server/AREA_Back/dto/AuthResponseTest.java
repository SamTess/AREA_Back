package area.server.AREA_Back.dto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthResponseTest {

    private AuthResponse authResponse;
    private UserResponse userResponse;

    @BeforeEach
    void setUp() {
        userResponse = new UserResponse(
            UUID.randomUUID(),
            "test@example.com",
            true,
            false,
            true, // isVerified
            LocalDateTime.now(),
            LocalDateTime.now(),
            "https://example.com/avatar.jpg"
        );

        authResponse = new AuthResponse();
        authResponse.setMessage("Authentication successful");
        authResponse.setUser(userResponse);
    }

    @Test
    void testValidAuthResponse() {
        assertNotNull(authResponse);
        assertEquals("Authentication successful", authResponse.getMessage());
        assertEquals(userResponse, authResponse.getUser());
    }

    @Test
    void testAuthResponseWithNullUser() {
        authResponse.setUser(null);
        assertNotNull(authResponse);
        assertEquals("Authentication successful", authResponse.getMessage());
        assertNull(authResponse.getUser());
    }

    @Test
    void testAuthResponseWithNullMessage() {
        authResponse.setMessage(null);
        assertNotNull(authResponse);
        assertNull(authResponse.getMessage());
        assertEquals(userResponse, authResponse.getUser());
    }

    @Test
    void testGettersAndSetters() {
        String testMessage = "Test message";
        UserResponse testUser = new UserResponse(
            UUID.randomUUID(),
            "getter@example.com",
            false,
            true,
            false, // isVerified
            LocalDateTime.now().minusDays(1),
            null,
            null
        );

        authResponse.setMessage(testMessage);
        authResponse.setUser(testUser);

        assertEquals(testMessage, authResponse.getMessage());
        assertEquals(testUser, authResponse.getUser());
    }

    @Test
    void testConstructors() {
        // Test no-args constructor
        AuthResponse noArgsResponse = new AuthResponse();
        assertNotNull(noArgsResponse);
        assertNull(noArgsResponse.getMessage());
        assertNull(noArgsResponse.getUser());

        // Test all-args constructor
        String testMessage = "Constructor message";
        UserResponse testUser = new UserResponse(
            UUID.randomUUID(),
            "constructor@example.com",
            true,
            false,
            true, // isVerified
            LocalDateTime.now(),
            LocalDateTime.now(),
            "https://example.com/constructor-avatar.jpg"
        );

        AuthResponse allArgsResponse = new AuthResponse(testMessage, testUser);

        assertEquals(testMessage, allArgsResponse.getMessage());
        assertEquals(testUser, allArgsResponse.getUser());
    }

    @Test
    void testToString() {
        String result = authResponse.toString();
        assertNotNull(result);
        assertTrue(result.contains("AuthResponse"));
    }

    @Test
    void testEqualsAndHashCode() {
        AuthResponse response1 = new AuthResponse("Test message", userResponse);
        AuthResponse response2 = new AuthResponse("Test message", userResponse);
        AuthResponse response3 = new AuthResponse("Different message", userResponse);

        assertEquals(response1, response2);
        assertEquals(response1.hashCode(), response2.hashCode());

        assertFalse(response1.equals(response3));
    }

    @Test
    void testErrorResponse() {
        AuthResponse errorResponse = new AuthResponse("Invalid credentials", null);

        assertEquals("Invalid credentials", errorResponse.getMessage());
        assertNull(errorResponse.getUser());
    }

    @Test
    void testSuccessResponse() {
        AuthResponse successResponse = new AuthResponse("Login successful", userResponse);

        assertEquals("Login successful", successResponse.getMessage());
        assertNotNull(successResponse.getUser());
        assertEquals(userResponse.getEmail(), successResponse.getUser().getEmail());
    }
}