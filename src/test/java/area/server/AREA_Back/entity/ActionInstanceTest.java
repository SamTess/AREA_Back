package area.server.AREA_Back.entity;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour ActionInstance
 * Type: Tests Unitaires
 * Description: Teste la validation et les propriétés de l'entité ActionInstance
 */
@DisplayName("ActionInstance - Tests Unitaires")
class ActionInstanceTest {

    private Validator validator;
    private ActionInstance actionInstance;
    private User testUser;
    private Area testArea;
    private ActionDefinition testActionDefinition;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();

        // Setup test entities
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("test@example.com");
        testUser.setIsActive(true);

        testArea = new Area();
        testArea.setId(UUID.randomUUID());
        testArea.setName("Test Area");
        testArea.setUser(testUser);
        testArea.setEnabled(true);

        testActionDefinition = new ActionDefinition();
        testActionDefinition.setId(UUID.randomUUID());
        testActionDefinition.setKey("test-action");
        testActionDefinition.setName("Test Action");
        testActionDefinition.setInputSchema(new HashMap<>());
        testActionDefinition.setOutputSchema(new HashMap<>());

        // Setup valid ActionInstance
        actionInstance = new ActionInstance();
        actionInstance.setId(UUID.randomUUID());
        actionInstance.setUser(testUser);
        actionInstance.setArea(testArea);
        actionInstance.setActionDefinition(testActionDefinition);
        actionInstance.setName("Test Action Instance");
        actionInstance.setEnabled(true);
        
        Map<String, Object> params = new HashMap<>();
        params.put("key1", "value1");
        actionInstance.setParams(params);
        
        actionInstance.setCreatedAt(LocalDateTime.now());
        actionInstance.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("Doit valider une ActionInstance correcte")
    void shouldValidateCorrectActionInstance() {
        // When
        Set<ConstraintViolation<ActionInstance>> violations = validator.validate(actionInstance);

        // Then
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("Doit échouer quand le user est null")
    void shouldFailWhenUserIsNull() {
        // Given
        actionInstance.setUser(null);

        // When
        Set<ConstraintViolation<ActionInstance>> violations = validator.validate(actionInstance);

        // Then
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getMessage().contains("User is required")));
    }

    @Test
    @DisplayName("Doit échouer quand l'area est null")
    void shouldFailWhenAreaIsNull() {
        // Given
        actionInstance.setArea(null);

        // When
        Set<ConstraintViolation<ActionInstance>> violations = validator.validate(actionInstance);

        // Then
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getMessage().contains("Area is required")));
    }

    @Test
    @DisplayName("Doit échouer quand la définition d'action est null")
    void shouldFailWhenActionDefinitionIsNull() {
        // Given
        actionInstance.setActionDefinition(null);

        // When
        Set<ConstraintViolation<ActionInstance>> violations = validator.validate(actionInstance);

        // Then
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getMessage().contains("Action definition is required")));
    }

    @Test
    @DisplayName("Doit échouer quand le nom est vide")
    void shouldFailWhenNameIsEmpty() {
        // Given
        actionInstance.setName("");

        // When
        Set<ConstraintViolation<ActionInstance>> violations = validator.validate(actionInstance);

        // Then
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getMessage().contains("Name is required")));
    }

    @Test
    @DisplayName("Doit échouer quand le nom est null")
    void shouldFailWhenNameIsNull() {
        // Given
        actionInstance.setName(null);

        // When
        Set<ConstraintViolation<ActionInstance>> violations = validator.validate(actionInstance);

        // Then
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getMessage().contains("Name is required")));
    }

    @Test
    @DisplayName("Doit permettre un serviceAccount null")
    void shouldAllowNullServiceAccount() {
        // Given
        actionInstance.setServiceAccount(null);

        // When
        Set<ConstraintViolation<ActionInstance>> violations = validator.validate(actionInstance);

        // Then
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("Doit avoir la valeur par défaut enabled = true")
    void shouldHaveDefaultEnabledValue() {
        // Given
        ActionInstance newInstance = new ActionInstance();

        // Then
        assertTrue(newInstance.getEnabled());
    }

    @Test
    @DisplayName("Doit définir et récupérer l'ID")
    void shouldSetAndGetId() {
        // Given
        UUID id = UUID.randomUUID();

        // When
        actionInstance.setId(id);

        // Then
        assertEquals(id, actionInstance.getId());
    }

    @Test
    @DisplayName("Doit définir et récupérer le user")
    void shouldSetAndGetUser() {
        // Given
        User newUser = new User();
        newUser.setId(UUID.randomUUID());
        newUser.setEmail("new@example.com");

        // When
        actionInstance.setUser(newUser);

        // Then
        assertEquals(newUser, actionInstance.getUser());
    }

    @Test
    @DisplayName("Doit définir et récupérer l'area")
    void shouldSetAndGetArea() {
        // Given
        Area newArea = new Area();
        newArea.setId(UUID.randomUUID());
        newArea.setName("New Area");

        // When
        actionInstance.setArea(newArea);

        // Then
        assertEquals(newArea, actionInstance.getArea());
    }

    @Test
    @DisplayName("Doit définir et récupérer la définition d'action")
    void shouldSetAndGetActionDefinition() {
        // Given
        ActionDefinition newDef = new ActionDefinition();
        newDef.setId(UUID.randomUUID());
        newDef.setKey("new-action");

        // When
        actionInstance.setActionDefinition(newDef);

        // Then
        assertEquals(newDef, actionInstance.getActionDefinition());
    }

    @Test
    @DisplayName("Doit définir et récupérer le serviceAccount")
    void shouldSetAndGetServiceAccount() {
        // Given
        ServiceAccount sa = new ServiceAccount();
        sa.setId(UUID.randomUUID());

        // When
        actionInstance.setServiceAccount(sa);

        // Then
        assertEquals(sa, actionInstance.getServiceAccount());
    }

    @Test
    @DisplayName("Doit définir et récupérer le nom")
    void shouldSetAndGetName() {
        // Given
        String name = "New Action Name";

        // When
        actionInstance.setName(name);

        // Then
        assertEquals(name, actionInstance.getName());
    }

    @Test
    @DisplayName("Doit définir et récupérer enabled")
    void shouldSetAndGetEnabled() {
        // When
        actionInstance.setEnabled(false);

        // Then
        assertFalse(actionInstance.getEnabled());
    }

    @Test
    @DisplayName("Doit définir et récupérer les params")
    void shouldSetAndGetParams() {
        // Given
        Map<String, Object> params = new HashMap<>();
        params.put("key1", "value1");
        params.put("key2", 42);
        params.put("key3", true);

        // When
        actionInstance.setParams(params);

        // Then
        assertEquals(params, actionInstance.getParams());
        assertEquals(3, actionInstance.getParams().size());
    }

    @Test
    @DisplayName("Doit gérer les params vides")
    void shouldHandleEmptyParams() {
        // Given
        Map<String, Object> emptyParams = new HashMap<>();

        // When
        actionInstance.setParams(emptyParams);

        // Then
        assertNotNull(actionInstance.getParams());
        assertTrue(actionInstance.getParams().isEmpty());
    }

    @Test
    @DisplayName("Doit tester equals et hashCode")
    void shouldTestEqualsAndHashCode() {
        // Given
        UUID id = UUID.randomUUID();
        
        ActionInstance instance1 = new ActionInstance();
        instance1.setId(id);
        instance1.setName("Test");

        ActionInstance instance2 = new ActionInstance();
        instance2.setId(id);
        instance2.setName("Test");

        // Then
        assertEquals(instance1, instance2);
        assertEquals(instance1.hashCode(), instance2.hashCode());
    }

    @Test
    @DisplayName("Doit tester toString")
    void shouldTestToString() {
        // When
        String toString = actionInstance.toString();

        // Then
        assertNotNull(toString);
        assertTrue(toString.contains("Test Action Instance"));
    }

    @Test
    @DisplayName("Doit créer avec constructeur no-args")
    void shouldCreateWithNoArgsConstructor() {
        // When
        ActionInstance instance = new ActionInstance();

        // Then
        assertNotNull(instance);
        assertNull(instance.getId());
        assertTrue(instance.getEnabled());
    }

    @Test
    @DisplayName("Doit créer avec constructeur all-args")
    void shouldCreateWithAllArgsConstructor() {
        // Given
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> params = new HashMap<>();
        params.put("test", "value");

        // When
        ActionInstance instance = new ActionInstance(
            id, testUser, testArea, testActionDefinition, 
            null, "Test Name", true, params, now, now
        );

        // Then
        assertNotNull(instance);
        assertEquals(id, instance.getId());
        assertEquals("Test Name", instance.getName());
        assertTrue(instance.getEnabled());
    }

    @Test
    @DisplayName("Doit gérer les timestamps")
    void shouldHandleTimestamps() {
        // Given
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1);
        LocalDateTime updatedAt = LocalDateTime.now();

        // When
        actionInstance.setCreatedAt(createdAt);
        actionInstance.setUpdatedAt(updatedAt);

        // Then
        assertEquals(createdAt, actionInstance.getCreatedAt());
        assertEquals(updatedAt, actionInstance.getUpdatedAt());
        assertTrue(actionInstance.getUpdatedAt().isAfter(actionInstance.getCreatedAt()));
    }

    @Test
    @DisplayName("Doit gérer les params avec types complexes")
    void shouldHandleComplexParamTypes() {
        // Given
        Map<String, Object> params = new HashMap<>();
        params.put("string", "value");
        params.put("number", 123);
        params.put("boolean", true);
        params.put("nested", Map.of("inner", "value"));

        // When
        actionInstance.setParams(params);

        // Then
        assertEquals("value", actionInstance.getParams().get("string"));
        assertEquals(123, actionInstance.getParams().get("number"));
        assertEquals(true, actionInstance.getParams().get("boolean"));
        assertTrue(actionInstance.getParams().get("nested") instanceof Map);
    }

    @Test
    @DisplayName("Doit valider avec nom contenant des espaces")
    void shouldValidateWithNameContainingSpaces() {
        // Given
        actionInstance.setName("Action Name With Spaces");

        // When
        Set<ConstraintViolation<ActionInstance>> violations = validator.validate(actionInstance);

        // Then
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("Doit valider avec nom contenant des caractères spéciaux")
    void shouldValidateWithNameContainingSpecialChars() {
        // Given
        actionInstance.setName("Action & Name - Special #123");

        // When
        Set<ConstraintViolation<ActionInstance>> violations = validator.validate(actionInstance);

        // Then
        assertTrue(violations.isEmpty());
    }
}
