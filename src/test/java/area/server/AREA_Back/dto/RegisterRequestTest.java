package area.server.AREA_Back.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegisterRequestTest {

    private Validator validator;
    private RegisterRequest registerRequest;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();

        registerRequest = new RegisterRequest();
        registerRequest.setEmail("test@example.com");
        registerRequest.setPassword("password123");
        registerRequest.setAvatarUrl("https://example.com/avatar.jpg");
    }

    @Test
    void testValidRegisterRequest() {
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(registerRequest);
        assertTrue(violations.isEmpty());
    }

    @Test
    void testValidRegisterRequestWithoutAvatar() {
        registerRequest.setAvatarUrl(null);
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(registerRequest);
        assertTrue(violations.isEmpty());
    }

    @Test
    void testRegisterRequestWithInvalidEmail() {
        registerRequest.setEmail("invalid-email");
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(registerRequest);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Email should be valid")));
    }

    @Test
    void testRegisterRequestWithNullEmail() {
        registerRequest.setEmail(null);
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(registerRequest);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Email is required")));
    }

    @Test
    void testRegisterRequestWithBlankEmail() {
        registerRequest.setEmail("   ");
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(registerRequest);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Email is required")));
    }

    @Test
    void testRegisterRequestWithNullPassword() {
        registerRequest.setPassword(null);
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(registerRequest);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Password is required")));
    }

    @Test
    void testRegisterRequestWithBlankPassword() {
        registerRequest.setPassword("   ");
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(registerRequest);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Password is required")));
    }

    @Test
    void testRegisterRequestWithShortPassword() {
        registerRequest.setPassword("short");
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(registerRequest);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(
                v -> v.getMessage().contains("Password must be at least 8 characters")));
    }

    @Test
    void testGettersAndSetters() {
        String testEmail = "getter@example.com";
        String testPassword = "getterPassword123";
        String testAvatarUrl = "https://example.com/getter-avatar.jpg";

        registerRequest.setEmail(testEmail);
        registerRequest.setPassword(testPassword);
        registerRequest.setAvatarUrl(testAvatarUrl);

        assertEquals(testEmail, registerRequest.getEmail());
        assertEquals(testPassword, registerRequest.getPassword());
        assertEquals(testAvatarUrl, registerRequest.getAvatarUrl());
    }

    @Test
    void testConstructors() {
        // Test no-args constructor
        RegisterRequest noArgsRequest = new RegisterRequest();
        assertNotNull(noArgsRequest);
        assertNull(noArgsRequest.getEmail());
        assertNull(noArgsRequest.getPassword());
        assertNull(noArgsRequest.getAvatarUrl());

        // Test all-args constructor
        String testEmail = "constructor@example.com";
        String testPassword = "constructorPassword123";
        String testAvatarUrl = "https://example.com/constructor-avatar.jpg";

        RegisterRequest allArgsRequest = new RegisterRequest(testEmail, testPassword, null, null, testAvatarUrl);

        assertEquals(testEmail, allArgsRequest.getEmail());
        assertEquals(testPassword, allArgsRequest.getPassword());
        assertEquals(testAvatarUrl, allArgsRequest.getAvatarUrl());
    }

    @Test
    void testToString() {
        String result = registerRequest.toString();
        assertNotNull(result);
        assertTrue(result.contains("RegisterRequest"));
    }

    @Test
    void testEqualsAndHashCode() {
        RegisterRequest request1 = new RegisterRequest("test@example.com", "password123", null, null, "https://example.com/avatar.jpg");
        RegisterRequest request2 = new RegisterRequest("test@example.com", "password123", null, null, "https://example.com/avatar.jpg");
        RegisterRequest request3 = new RegisterRequest("different@example.com", "password123", null, null, "https://example.com/avatar.jpg");

        assertEquals(request1, request2);
        assertEquals(request1.hashCode(), request2.hashCode());

        assertFalse(request1.equals(request3));
    }
}