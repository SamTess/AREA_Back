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

class LoginRequestTest {

    private Validator validator;
    private LocalLoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();

        loginRequest = new LocalLoginRequest();
        loginRequest.setEmail("test@example.com");
        loginRequest.setPassword("password123");
    }

    @Test
    void testValidLoginRequest() {
        Set<ConstraintViolation<LocalLoginRequest>> violations = validator.validate(loginRequest);
        assertTrue(violations.isEmpty());
    }

    @Test
    void testLoginRequestWithInvalidEmail() {
        loginRequest.setEmail("invalid-email");
        Set<ConstraintViolation<LocalLoginRequest>> violations = validator.validate(loginRequest);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Email should be valid")));
    }

    @Test
    void testLoginRequestWithNullEmail() {
        loginRequest.setEmail(null);
        Set<ConstraintViolation<LocalLoginRequest>> violations = validator.validate(loginRequest);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Email is required")));
    }

    @Test
    void testLoginRequestWithBlankEmail() {
        loginRequest.setEmail("   ");
        Set<ConstraintViolation<LocalLoginRequest>> violations = validator.validate(loginRequest);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Email is required")));
    }

    @Test
    void testLoginRequestWithNullPassword() {
        loginRequest.setPassword(null);
        Set<ConstraintViolation<LocalLoginRequest>> violations = validator.validate(loginRequest);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Password is required")));
    }

    @Test
    void testLoginRequestWithBlankPassword() {
        loginRequest.setPassword("   ");
        Set<ConstraintViolation<LocalLoginRequest>> violations = validator.validate(loginRequest);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Password is required")));
    }

    @Test
    void testGettersAndSetters() {
        String testEmail = "getter@example.com";
        String testPassword = "getterPassword123";

        loginRequest.setEmail(testEmail);
        loginRequest.setPassword(testPassword);

        assertEquals(testEmail, loginRequest.getEmail());
        assertEquals(testPassword, loginRequest.getPassword());
    }

    @Test
    void testConstructors() {
        // Test no-args constructor
        LocalLoginRequest noArgsRequest = new LocalLoginRequest();
        assertNotNull(noArgsRequest);

        // Test all-args constructor
        String testEmail = "constructor@example.com";
        String testPassword = "constructorPassword123";

        LocalLoginRequest allArgsRequest = new LocalLoginRequest(testEmail, testPassword);

        assertEquals(testEmail, allArgsRequest.getEmail());
        assertEquals(testPassword, allArgsRequest.getPassword());
    }

    @Test
    void testToString() {
        String result = loginRequest.toString();
        assertNotNull(result);
        assertTrue(result.contains("LoginRequest"));
    }

    @Test
    void testEqualsAndHashCode() {
        LocalLoginRequest request1 = new LocalLoginRequest("test@example.com", "password123");
        LocalLoginRequest request2 = new LocalLoginRequest("test@example.com", "password123");
        LocalLoginRequest request3 = new LocalLoginRequest("different@example.com", "password123");

        assertEquals(request1, request2);
        assertEquals(request1.hashCode(), request2.hashCode());

        assertFalse(request1.equals(request3));
    }
}