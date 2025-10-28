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

@DisplayName("AreaReactionRequest - Tests Unitaires")
class AreaReactionRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("Doit créer une AreaReactionRequest valide")
    void shouldCreateValidAreaReactionRequest() {
        // Given
        UUID actionDefId = UUID.randomUUID();
        String name = "Test Reaction";
        
        // When
        AreaReactionRequest request = new AreaReactionRequest();
        request.setActionDefinitionId(actionDefId);
        request.setName(name);

        Set<ConstraintViolation<AreaReactionRequest>> violations = validator.validate(request);

        // Then
        assertTrue(violations.isEmpty());
        assertEquals(actionDefId, request.getActionDefinitionId());
        assertEquals(name, request.getName());
    }

    @Test
    @DisplayName("Doit échouer si actionDefinitionId est null")
    void shouldFailWhenActionDefinitionIdIsNull() {
        // Given
        AreaReactionRequest request = new AreaReactionRequest();
        request.setName("Test");

        // When
        Set<ConstraintViolation<AreaReactionRequest>> violations = validator.validate(request);

        // Then
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getMessage().contains("Action definition ID is required")));
    }

    @Test
    @DisplayName("Doit échouer si name est vide")
    void shouldFailWhenNameIsBlank() {
        // Given
        AreaReactionRequest request = new AreaReactionRequest();
        request.setActionDefinitionId(UUID.randomUUID());
        request.setName("");

        // When
        Set<ConstraintViolation<AreaReactionRequest>> violations = validator.validate(request);

        // Then
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getMessage().contains("Reaction name is required")));
    }

    @Test
    @DisplayName("Doit avoir order par défaut à 0")
    void shouldHaveDefaultOrderZero() {
        // Given & When
        AreaReactionRequest request = new AreaReactionRequest();
        request.setActionDefinitionId(UUID.randomUUID());
        request.setName("Test");

        // Then
        assertEquals(0, request.getOrder());
    }

    @Test
    @DisplayName("Doit gérer mapping")
    void shouldHandleMapping() {
        // Given
        Map<String, Object> mapping = new HashMap<>();
        mapping.put("outputField", "{{inputField}}");

        AreaReactionRequest request = new AreaReactionRequest();
        request.setActionDefinitionId(UUID.randomUUID());
        request.setName("Test");
        request.setMapping(mapping);

        // When
        Set<ConstraintViolation<AreaReactionRequest>> violations = validator.validate(request);

        // Then
        assertTrue(violations.isEmpty());
        assertEquals(1, request.getMapping().size());
    }

    @Test
    @DisplayName("Doit gérer condition")
    void shouldHandleCondition() {
        // Given
        Map<String, Object> condition = new HashMap<>();
        condition.put("field", "value");
        condition.put("operator", "equals");

        AreaReactionRequest request = new AreaReactionRequest();
        request.setActionDefinitionId(UUID.randomUUID());
        request.setName("Test");
        request.setCondition(condition);

        // When
        Set<ConstraintViolation<AreaReactionRequest>> violations = validator.validate(request);

        // Then
        assertTrue(violations.isEmpty());
        assertEquals(2, request.getCondition().size());
    }

    @Test
    @DisplayName("Doit gérer order personnalisé")
    void shouldHandleCustomOrder() {
        // Given
        AreaReactionRequest request = new AreaReactionRequest();
        request.setActionDefinitionId(UUID.randomUUID());
        request.setName("Test");
        request.setOrder(5);

        // When
        Set<ConstraintViolation<AreaReactionRequest>> violations = validator.validate(request);

        // Then
        assertTrue(violations.isEmpty());
        assertEquals(5, request.getOrder());
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
        Map<String, Object> mapping = new HashMap<>();
        Map<String, Object> condition = new HashMap<>();
        Integer order = 3;
        Map<String, Object> config = new HashMap<>();

        // When
        AreaReactionRequest request = new AreaReactionRequest(
            actionDefId, name, description, serviceAccountId, params, 
            mapping, condition, order, config
        );

        // Then
        assertEquals(actionDefId, request.getActionDefinitionId());
        assertEquals(name, request.getName());
        assertEquals(description, request.getDescription());
        assertEquals(serviceAccountId, request.getServiceAccountId());
        assertEquals(params, request.getParameters());
        assertEquals(mapping, request.getMapping());
        assertEquals(condition, request.getCondition());
        assertEquals(order, request.getOrder());
        assertEquals(config, request.getActivationConfig());
    }

    @Test
    @DisplayName("Doit accepter serviceAccountId null")
    void shouldAcceptNullServiceAccountId() {
        // Given
        AreaReactionRequest request = new AreaReactionRequest();
        request.setActionDefinitionId(UUID.randomUUID());
        request.setName("Test");
        request.setServiceAccountId(null);

        // When
        Set<ConstraintViolation<AreaReactionRequest>> violations = validator.validate(request);

        // Then
        assertTrue(violations.isEmpty());
        assertNull(request.getServiceAccountId());
    }

    @Test
    @DisplayName("Doit gérer parameters complexes")
    void shouldHandleComplexParameters() {
        // Given
        Map<String, Object> params = new HashMap<>();
        params.put("string", "value");
        params.put("number", 42);
        params.put("boolean", true);
        
        Map<String, Object> nested = new HashMap<>();
        nested.put("key", "value");
        params.put("nested", nested);

        AreaReactionRequest request = new AreaReactionRequest();
        request.setActionDefinitionId(UUID.randomUUID());
        request.setName("Test");
        request.setParameters(params);

        // When
        Set<ConstraintViolation<AreaReactionRequest>> violations = validator.validate(request);

        // Then
        assertTrue(violations.isEmpty());
        assertEquals(4, request.getParameters().size());
    }
}
