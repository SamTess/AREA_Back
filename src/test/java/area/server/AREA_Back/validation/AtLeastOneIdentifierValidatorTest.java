package area.server.AREA_Back.validation;

import area.server.AREA_Back.dto.LocalLoginRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AtLeastOneIdentifier Validation Tests")
class AtLeastOneIdentifierValidatorTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("Should pass validation when email is provided")
    void shouldPassValidationWhenEmailIsProvided() {
        // Given
        LocalLoginRequest request = new LocalLoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("password123");

        // When
        Set<ConstraintViolation<LocalLoginRequest>> violations = validator.validate(request);

        // Then
        assertTrue(violations.isEmpty(), "Should have no violations when email is provided");
    }

    @Test
    @DisplayName("Should pass validation when username is provided")
    void shouldPassValidationWhenUsernameIsProvided() {
        // Given
        LocalLoginRequest request = new LocalLoginRequest();
        request.setUsername("testuser");
        request.setPassword("password123");

        // When
        Set<ConstraintViolation<LocalLoginRequest>> violations = validator.validate(request);

        // Then
        assertTrue(violations.isEmpty(), "Should have no violations when username is provided");
    }

    @Test
    @DisplayName("Should pass validation when both email and username are provided")
    void shouldPassValidationWhenBothAreProvided() {
        // Given
        LocalLoginRequest request = new LocalLoginRequest();
        request.setEmail("test@example.com");
        request.setUsername("testuser");
        request.setPassword("password123");

        // When
        Set<ConstraintViolation<LocalLoginRequest>> violations = validator.validate(request);

        // Then
        assertTrue(violations.isEmpty(), "Should have no violations when both identifiers are provided");
    }

    @Test
    @DisplayName("Should fail validation when neither email nor username is provided")
    void shouldFailValidationWhenNeitherIsProvided() {
        // Given
        LocalLoginRequest request = new LocalLoginRequest();
        request.setPassword("password123");

        // When
        Set<ConstraintViolation<LocalLoginRequest>> violations = validator.validate(request);

        // Then
        boolean hasAtLeastOneIdentifierViolation = violations.stream()
                .anyMatch(v -> v.getMessage().contains("Either email or username must be provided"));
        
        assertTrue(hasAtLeastOneIdentifierViolation, 
                "Should have violation for missing identifiers");
    }

    @Test
    @DisplayName("Should fail validation when email is blank")
    void shouldFailValidationWhenEmailIsBlank() {
        // Given
        LocalLoginRequest request = new LocalLoginRequest();
        request.setEmail("   ");
        request.setPassword("password123");

        // When
        Set<ConstraintViolation<LocalLoginRequest>> violations = validator.validate(request);

        // Then
        boolean hasAtLeastOneIdentifierViolation = violations.stream()
                .anyMatch(v -> v.getMessage().contains("Either email or username must be provided"));
        
        assertTrue(hasAtLeastOneIdentifierViolation, 
                "Should have violation when email is blank");
    }

    @Test
    @DisplayName("Should fail validation when username is blank")
    void shouldFailValidationWhenUsernameIsBlank() {
        // Given
        LocalLoginRequest request = new LocalLoginRequest();
        request.setUsername("   ");
        request.setPassword("password123");

        // When
        Set<ConstraintViolation<LocalLoginRequest>> violations = validator.validate(request);

        // Then
        boolean hasAtLeastOneIdentifierViolation = violations.stream()
                .anyMatch(v -> v.getMessage().contains("Either email or username must be provided"));
        
        assertTrue(hasAtLeastOneIdentifierViolation, 
                "Should have violation when username is blank");
    }

    @Test
    @DisplayName("Should fail validation when both identifiers are blank")
    void shouldFailValidationWhenBothAreBlank() {
        // Given
        LocalLoginRequest request = new LocalLoginRequest();
        request.setEmail("   ");
        request.setUsername("   ");
        request.setPassword("password123");

        // When
        Set<ConstraintViolation<LocalLoginRequest>> violations = validator.validate(request);

        // Then
        boolean hasAtLeastOneIdentifierViolation = violations.stream()
                .anyMatch(v -> v.getMessage().contains("Either email or username must be provided"));
        
        assertTrue(hasAtLeastOneIdentifierViolation, 
                "Should have violation when both identifiers are blank");
    }

    @Test
    @DisplayName("Should fail validation when both identifiers are empty strings")
    void shouldFailValidationWhenBothAreEmptyStrings() {
        // Given
        LocalLoginRequest request = new LocalLoginRequest();
        request.setEmail("");
        request.setUsername("");
        request.setPassword("password123");

        // When
        Set<ConstraintViolation<LocalLoginRequest>> violations = validator.validate(request);

        // Then
        boolean hasAtLeastOneIdentifierViolation = violations.stream()
                .anyMatch(v -> v.getMessage().contains("Either email or username must be provided"));
        
        assertTrue(hasAtLeastOneIdentifierViolation, 
                "Should have violation when both identifiers are empty strings");
    }
}
