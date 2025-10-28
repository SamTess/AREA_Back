package area.server.AREA_Back.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AreaActionRequest - Tests Unitaires")
class AreaActionRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("Doit créer une AreaActionRequest valide")
    void shouldCreateValidAreaActionRequest() {
        // Given
        UUID actionDefId = UUID.randomUUID();
        String name = "Test Action";
        Map<String, Object> params = new HashMap<>();
        params.put("key", "value");

        // When
        AreaActionRequest request = new AreaActionRequest();
        request.setActionDefinitionId(actionDefId);
        request.setName(name);
        request.setParameters(params);

        Set<ConstraintViolation<AreaActionRequest>> violations = validator.validate(request);

        // Then
        assertTrue(violations.isEmpty());
        assertEquals(actionDefId, request.getActionDefinitionId());
        assertEquals(name, request.getName());
        assertEquals(params, request.getParameters());
    }

    @Test
    @DisplayName("Doit échouer si actionDefinitionId est null")
    void shouldFailWhenActionDefinitionIdIsNull() {
        // Given
        AreaActionRequest request = new AreaActionRequest();
        request.setName("Test");

        // When
        Set<ConstraintViolation<AreaActionRequest>> violations = validator.validate(request);

        // Then
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getMessage().contains("Action definition ID is required")));
    }

    @Test
    @DisplayName("Doit échouer si name est vide")
    void shouldFailWhenNameIsBlank() {
        // Given
        AreaActionRequest request = new AreaActionRequest();
        request.setActionDefinitionId(UUID.randomUUID());
        request.setName("");

        // When
        Set<ConstraintViolation<AreaActionRequest>> violations = validator.validate(request);

        // Then
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getMessage().contains("Action name is required")));
    }

    @Test
    @DisplayName("Doit accepter un serviceAccountId null")
    void shouldAcceptNullServiceAccountId() {
        // Given
        AreaActionRequest request = new AreaActionRequest();
        request.setActionDefinitionId(UUID.randomUUID());
        request.setName("Test");
        request.setServiceAccountId(null);

        // When
        Set<ConstraintViolation<AreaActionRequest>> violations = validator.validate(request);

        // Then
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("Doit gérer tous les champs avec le constructeur all-args")
    void shouldHandleAllFieldsWithAllArgsConstructor() {
        // Given
        UUID actionDefId = UUID.randomUUID();
        UUID serviceAccountId = UUID.randomUUID();
        String name = "Test";
        String description = "Description";
        Map<String, Object> params = new HashMap<>();
        Map<String, Object> config = new HashMap<>();

        // When
        AreaActionRequest request = new AreaActionRequest(
            actionDefId, name, description, serviceAccountId, params, config
        );

        // Then
        assertEquals(actionDefId, request.getActionDefinitionId());
        assertEquals(name, request.getName());
        assertEquals(description, request.getDescription());
        assertEquals(serviceAccountId, request.getServiceAccountId());
        assertEquals(params, request.getParameters());
        assertEquals(config, request.getActivationConfig());
    }

    @Test
    @DisplayName("Doit gérer parameters vide")
    void shouldHandleEmptyParameters() {
        // Given
        AreaActionRequest request = new AreaActionRequest();
        request.setActionDefinitionId(UUID.randomUUID());
        request.setName("Test");
        request.setParameters(new HashMap<>());

        // When
        Set<ConstraintViolation<AreaActionRequest>> violations = validator.validate(request);

        // Then
        assertTrue(violations.isEmpty());
        assertTrue(request.getParameters().isEmpty());
    }

    @Test
    @DisplayName("Doit gérer activationConfig")
    void shouldHandleActivationConfig() {
        // Given
        Map<String, Object> config = new HashMap<>();
        config.put("interval", 60);
        config.put("enabled", true);

        AreaActionRequest request = new AreaActionRequest();
        request.setActionDefinitionId(UUID.randomUUID());
        request.setName("Test");
        request.setActivationConfig(config);

        // When
        Set<ConstraintViolation<AreaActionRequest>> violations = validator.validate(request);

        // Then
        assertTrue(violations.isEmpty());
        assertEquals(2, request.getActivationConfig().size());
    }
}
