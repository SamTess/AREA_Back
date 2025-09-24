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
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateUserRequestTest {

    private Validator validator;
    private CreateUserRequest createUserRequest;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        
        createUserRequest = new CreateUserRequest();
        createUserRequest.setEmail("test@example.com");
        createUserRequest.setPassword("password123");
        createUserRequest.setAvatarUrl("https://example.com/avatar.jpg");
    }

    @Test
    void testValidCreateUserRequest() {
        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(createUserRequest);
        assertTrue(violations.isEmpty());
    }

    @Test
    void testCreateUserRequestWithInvalidEmail() {
        createUserRequest.setEmail("invalid-email");
        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(createUserRequest);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Email should be valid")));
    }

    @Test
    void testCreateUserRequestWithBlankEmail() {
        createUserRequest.setEmail("");
        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(createUserRequest);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Email is required")));
    }

    @Test
    void testCreateUserRequestWithNullEmail() {
        createUserRequest.setEmail(null);
        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(createUserRequest);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Email is required")));
    }

    @Test
    void testCreateUserRequestWithShortPassword() {
        createUserRequest.setPassword("short");
        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(createUserRequest);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getMessage().contains("Password must be at least 8 characters")));
    }

    @Test
    void testCreateUserRequestWithNullPassword() {
        createUserRequest.setPassword(null);
        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(createUserRequest);
        // Password is required (@NotNull annotation)
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Password is required")));
    }

    @Test
    void testCreateUserRequestWithNullAvatarUrl() {
        createUserRequest.setAvatarUrl(null);
        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(createUserRequest);
        assertTrue(violations.isEmpty()); // Avatar URL can be null
    }

    @Test
    void testCreateUserRequestEqualsAndHashCode() {
        CreateUserRequest request1 = new CreateUserRequest();
        request1.setEmail("test@example.com");
        request1.setPassword("password123");
        
        CreateUserRequest request2 = new CreateUserRequest();
        request2.setEmail("test@example.com");
        request2.setPassword("password123");
        
        assertEquals(request1, request2);
        assertEquals(request1.hashCode(), request2.hashCode());
    }

    @Test
    void testCreateUserRequestToString() {
        String requestString = createUserRequest.toString();
        assertNotNull(requestString);
        assertTrue(requestString.contains("test@example.com"));
    }

    @Test
    void testCreateUserRequestSettersAndGetters() {
        String email = "newtest@example.com";
        String password = "newpassword123";
        String avatarUrl = "https://newexample.com/avatar.jpg";

        createUserRequest.setEmail(email);
        createUserRequest.setPassword(password);
        createUserRequest.setAvatarUrl(avatarUrl);

        assertEquals(email, createUserRequest.getEmail());
        assertEquals(password, createUserRequest.getPassword());
        assertEquals(avatarUrl, createUserRequest.getAvatarUrl());
    }

    @Test
    void testCreateUserRequestConstructors() {
        // Test no-args constructor
        CreateUserRequest request1 = new CreateUserRequest();
        assertNotNull(request1);

        // Test all-args constructor
        CreateUserRequest request2 = new CreateUserRequest("test@example.com", "password123", "https://example.com/avatar.jpg");
        assertEquals("test@example.com", request2.getEmail());
        assertEquals("password123", request2.getPassword());
        assertEquals("https://example.com/avatar.jpg", request2.getAvatarUrl());
    }

    @Test
    void testCreateUserRequestWithMinimumPassword() {
        createUserRequest.setPassword("12345678"); // Exactly 8 characters
        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(createUserRequest);
        assertTrue(violations.isEmpty());
    }

    @Test
    void testCreateUserRequestWithLongPassword() {
        createUserRequest.setPassword("verylongpasswordwithmorethan8characters");
        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(createUserRequest);
        assertTrue(violations.isEmpty());
    }
}