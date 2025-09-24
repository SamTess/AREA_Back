package area.server.AREA_Back.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateAreaRequestTest {

    private Validator validator;
    private CreateAreaRequest createAreaRequest;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        
        createAreaRequest = new CreateAreaRequest();
        createAreaRequest.setUserId(UUID.randomUUID());
        createAreaRequest.setName("Test Area");
        createAreaRequest.setDescription("Test Description");
    }

    @Test
    void testValidCreateAreaRequest() {
        Set<ConstraintViolation<CreateAreaRequest>> violations = validator.validate(createAreaRequest);
        assertTrue(violations.isEmpty());
    }

    @Test
    void testCreateAreaRequestWithNullUserId() {
        createAreaRequest.setUserId(null);
        Set<ConstraintViolation<CreateAreaRequest>> violations = validator.validate(createAreaRequest);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("User ID is required")));
    }

    @Test
    void testCreateAreaRequestWithBlankName() {
        createAreaRequest.setName("");
        Set<ConstraintViolation<CreateAreaRequest>> violations = validator.validate(createAreaRequest);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Area name is required")));
    }

    @Test
    void testCreateAreaRequestWithNullName() {
        createAreaRequest.setName(null);
        Set<ConstraintViolation<CreateAreaRequest>> violations = validator.validate(createAreaRequest);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Area name is required")));
    }

    @Test
    void testCreateAreaRequestWithNullDescription() {
        createAreaRequest.setDescription(null);
        Set<ConstraintViolation<CreateAreaRequest>> violations = validator.validate(createAreaRequest);
        assertTrue(violations.isEmpty()); // Description can be null
    }

    @Test
    void testCreateAreaRequestEqualsAndHashCode() {
        UUID userId = UUID.randomUUID();
        
        CreateAreaRequest request1 = new CreateAreaRequest();
        request1.setUserId(userId);
        request1.setName("Test Area");
        request1.setDescription("Test Description");
        
        CreateAreaRequest request2 = new CreateAreaRequest();
        request2.setUserId(userId);
        request2.setName("Test Area");
        request2.setDescription("Test Description");
        
        assertEquals(request1, request2);
        assertEquals(request1.hashCode(), request2.hashCode());
    }

    @Test
    void testCreateAreaRequestToString() {
        String requestString = createAreaRequest.toString();
        assertNotNull(requestString);
        assertTrue(requestString.contains("Test Area"));
    }

    @Test
    void testCreateAreaRequestSettersAndGetters() {
        UUID userId = UUID.randomUUID();
        String name = "New Area";
        String description = "New Description";

        createAreaRequest.setUserId(userId);
        createAreaRequest.setName(name);
        createAreaRequest.setDescription(description);

        assertEquals(userId, createAreaRequest.getUserId());
        assertEquals(name, createAreaRequest.getName());
        assertEquals(description, createAreaRequest.getDescription());
    }

    @Test
    void testCreateAreaRequestConstructors() {
        // Test no-args constructor
        CreateAreaRequest request1 = new CreateAreaRequest();
        assertNotNull(request1);

        // Test all-args constructor (name, description, userId)
        UUID userId = UUID.randomUUID();
        CreateAreaRequest request2 = new CreateAreaRequest("Constructor Area", "Constructor Description", userId);
        
        assertEquals(userId, request2.getUserId());
        assertEquals("Constructor Area", request2.getName());
        assertEquals("Constructor Description", request2.getDescription());
    }

    @Test
    void testCreateAreaRequestWithEmptyDescription() {
        createAreaRequest.setDescription("");
        Set<ConstraintViolation<CreateAreaRequest>> violations = validator.validate(createAreaRequest);
        assertTrue(violations.isEmpty()); // Empty description is allowed
    }

    @Test
    void testCreateAreaRequestWithLongName() {
        createAreaRequest.setName("Very long area name that might exceed normal limits");
        Set<ConstraintViolation<CreateAreaRequest>> violations = validator.validate(createAreaRequest);
        assertTrue(violations.isEmpty()); // No length restriction on name
    }

    @Test
    void testCreateAreaRequestWithLongDescription() {
        createAreaRequest.setDescription("Very long description that might exceed normal limits "
            + "but should still be valid because there are no constraints on the description length in the DTO");
        Set<ConstraintViolation<CreateAreaRequest>> violations = validator.validate(createAreaRequest);
        assertTrue(violations.isEmpty()); // No length restriction on description
    }
}