package area.server.AREA_Back.entity;

import area.server.AREA_Back.entity.enums.ExecutionStatus;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Execution - Tests Unitaires")
class ExecutionTest {

    private static Validator validator;
    private ActionInstance testActionInstance;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @BeforeEach
    void setUp() {
        testActionInstance = new ActionInstance();
        testActionInstance.setId(UUID.randomUUID());
        testActionInstance.setName("Test Action");
    }

    @Test
    @DisplayName("Doit créer une Execution valide")
    void shouldCreateValidExecution() {
        // Given
        Execution execution = new Execution();
        execution.setActionInstance(testActionInstance);
        execution.setStatus(ExecutionStatus.QUEUED);

        // When
        Set<ConstraintViolation<Execution>> violations = validator.validate(execution);

        // Then
        assertTrue(violations.isEmpty());
        assertEquals(testActionInstance, execution.getActionInstance());
        assertEquals(ExecutionStatus.QUEUED, execution.getStatus());
    }

    @Test
    @DisplayName("Doit échouer si actionInstance est null")
    void shouldFailWhenActionInstanceIsNull() {
        // Given
        Execution execution = new Execution();
        execution.setActionInstance(null);
        execution.setStatus(ExecutionStatus.QUEUED);

        // When
        Set<ConstraintViolation<Execution>> violations = validator.validate(execution);

        // Then
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getMessage().contains("Action instance is required")));
    }

    @Test
    @DisplayName("Doit échouer si status est null")
    void shouldFailWhenStatusIsNull() {
        // Given
        Execution execution = new Execution();
        execution.setActionInstance(testActionInstance);
        execution.setStatus(null);

        // When
        Set<ConstraintViolation<Execution>> violations = validator.validate(execution);

        // Then
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getMessage().contains("Status is required")));
    }

    @Test
    @DisplayName("Doit avoir status QUEUED par défaut")
    void shouldHaveQueuedStatusByDefault() {
        // Given & When
        Execution execution = new Execution();

        // Then
        assertEquals(ExecutionStatus.QUEUED, execution.getStatus());
    }

    @Test
    @DisplayName("Doit avoir attempt à 0 par défaut")
    void shouldHaveAttemptZeroByDefault() {
        // Given & When
        Execution execution = new Execution();

        // Then
        assertEquals(0, execution.getAttempt());
    }

    @Test
    @DisplayName("Doit gérer le status RUNNING")
    void shouldHandleRunningStatus() {
        // Given
        Execution execution = new Execution();
        execution.setActionInstance(testActionInstance);
        execution.setStatus(ExecutionStatus.RUNNING);
        execution.setStartedAt(LocalDateTime.now());

        // When
        Set<ConstraintViolation<Execution>> violations = validator.validate(execution);

        // Then
        assertTrue(violations.isEmpty());
        assertEquals(ExecutionStatus.RUNNING, execution.getStatus());
        assertNotNull(execution.getStartedAt());
    }

    @Test
    @DisplayName("Doit gérer le status OK")
    void shouldHandleOkStatus() {
        // Given
        Execution execution = new Execution();
        execution.setActionInstance(testActionInstance);
        execution.setStatus(ExecutionStatus.OK);
        execution.setFinishedAt(LocalDateTime.now());

        // When
        Set<ConstraintViolation<Execution>> violations = validator.validate(execution);

        // Then
        assertTrue(violations.isEmpty());
        assertEquals(ExecutionStatus.OK, execution.getStatus());
        assertNotNull(execution.getFinishedAt());
    }

    @Test
    @DisplayName("Doit gérer le status FAILED")
    void shouldHandleFailedStatus() {
        // Given
        Execution execution = new Execution();
        execution.setActionInstance(testActionInstance);
        execution.setStatus(ExecutionStatus.FAILED);
        execution.setAttempt(3);

        // When
        Set<ConstraintViolation<Execution>> violations = validator.validate(execution);

        // Then
        assertTrue(violations.isEmpty());
        assertEquals(ExecutionStatus.FAILED, execution.getStatus());
        assertEquals(3, execution.getAttempt());
    }

    @Test
    @DisplayName("Doit gérer inputPayload")
    void shouldHandleInputPayload() {
        // Given
        Map<String, Object> inputPayload = new HashMap<>();
        inputPayload.put("key", "value");
        inputPayload.put("number", 42);

        Execution execution = new Execution();
        execution.setActionInstance(testActionInstance);
        execution.setStatus(ExecutionStatus.QUEUED);
        execution.setInputPayload(inputPayload);

        // When
        Set<ConstraintViolation<Execution>> violations = validator.validate(execution);

        // Then
        assertTrue(violations.isEmpty());
        assertEquals(2, execution.getInputPayload().size());
        assertEquals("value", execution.getInputPayload().get("key"));
    }

    @Test
    @DisplayName("Doit gérer outputPayload")
    void shouldHandleOutputPayload() {
        // Given
        Map<String, Object> outputPayload = new HashMap<>();
        outputPayload.put("result", "success");

        Execution execution = new Execution();
        execution.setActionInstance(testActionInstance);
        execution.setStatus(ExecutionStatus.OK);
        execution.setOutputPayload(outputPayload);

        // When
        Set<ConstraintViolation<Execution>> violations = validator.validate(execution);

        // Then
        assertTrue(violations.isEmpty());
        assertEquals(1, execution.getOutputPayload().size());
        assertEquals("success", execution.getOutputPayload().get("result"));
    }

    @Test
    @DisplayName("Doit gérer error")
    void shouldHandleError() {
        // Given
        Map<String, Object> error = new HashMap<>();
        error.put("message", "Connection timeout");
        error.put("code", 500);

        Execution execution = new Execution();
        execution.setActionInstance(testActionInstance);
        execution.setStatus(ExecutionStatus.FAILED);
        execution.setError(error);

        // When
        Set<ConstraintViolation<Execution>> violations = validator.validate(execution);

        // Then
        assertTrue(violations.isEmpty());
        assertEquals(2, execution.getError().size());
        assertEquals("Connection timeout", execution.getError().get("message"));
    }

    @Test
    @DisplayName("Doit gérer correlationId")
    void shouldHandleCorrelationId() {
        // Given
        UUID correlationId = UUID.randomUUID();
        
        Execution execution = new Execution();
        execution.setActionInstance(testActionInstance);
        execution.setStatus(ExecutionStatus.QUEUED);
        execution.setCorrelationId(correlationId);

        // When
        Set<ConstraintViolation<Execution>> violations = validator.validate(execution);

        // Then
        assertTrue(violations.isEmpty());
        assertEquals(correlationId, execution.getCorrelationId());
    }

    @Test
    @DisplayName("Doit gérer dedupKey")
    void shouldHandleDedupKey() {
        // Given
        String dedupKey = "unique-key-123";
        
        Execution execution = new Execution();
        execution.setActionInstance(testActionInstance);
        execution.setStatus(ExecutionStatus.QUEUED);
        execution.setDedupKey(dedupKey);

        // When
        Set<ConstraintViolation<Execution>> violations = validator.validate(execution);

        // Then
        assertTrue(violations.isEmpty());
        assertEquals(dedupKey, execution.getDedupKey());
    }

    @Test
    @DisplayName("Doit gérer activationMode")
    void shouldHandleActivationMode() {
        // Given
        ActivationMode activationMode = new ActivationMode();
        activationMode.setId(UUID.randomUUID());

        Execution execution = new Execution();
        execution.setActionInstance(testActionInstance);
        execution.setStatus(ExecutionStatus.QUEUED);
        execution.setActivationMode(activationMode);

        // When
        Set<ConstraintViolation<Execution>> violations = validator.validate(execution);

        // Then
        assertTrue(violations.isEmpty());
        assertEquals(activationMode, execution.getActivationMode());
    }

    @Test
    @DisplayName("Doit gérer area")
    void shouldHandleArea() {
        // Given
        Area area = new Area();
        area.setId(UUID.randomUUID());

        Execution execution = new Execution();
        execution.setActionInstance(testActionInstance);
        execution.setStatus(ExecutionStatus.QUEUED);
        execution.setArea(area);

        // When
        Set<ConstraintViolation<Execution>> violations = validator.validate(execution);

        // Then
        assertTrue(violations.isEmpty());
        assertEquals(area, execution.getArea());
    }

    @Test
    @DisplayName("Doit gérer plusieurs tentatives")
    void shouldHandleMultipleAttempts() {
        // Given
        Execution execution = new Execution();
        execution.setActionInstance(testActionInstance);
        execution.setStatus(ExecutionStatus.QUEUED);
        execution.setAttempt(5);

        // When
        Set<ConstraintViolation<Execution>> violations = validator.validate(execution);

        // Then
        assertTrue(violations.isEmpty());
        assertEquals(5, execution.getAttempt());
    }

    @Test
    @DisplayName("Doit gérer les timestamps")
    void shouldHandleTimestamps() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        
        Execution execution = new Execution();
        execution.setActionInstance(testActionInstance);
        execution.setStatus(ExecutionStatus.OK);
        execution.setQueuedAt(now);
        execution.setStartedAt(now.plusSeconds(10));
        execution.setFinishedAt(now.plusSeconds(20));

        // When
        Set<ConstraintViolation<Execution>> violations = validator.validate(execution);

        // Then
        assertTrue(violations.isEmpty());
        assertNotNull(execution.getQueuedAt());
        assertNotNull(execution.getStartedAt());
        assertNotNull(execution.getFinishedAt());
    }

    @Test
    @DisplayName("Doit gérer equals() et hashCode()")
    void shouldHandleEqualsAndHashCode() {
        // Given
        UUID id = UUID.randomUUID();
        
        Execution execution1 = new Execution();
        execution1.setId(id);
        execution1.setActionInstance(testActionInstance);
        execution1.setStatus(ExecutionStatus.QUEUED);

        Execution execution2 = new Execution();
        execution2.setId(id);
        execution2.setActionInstance(testActionInstance);
        execution2.setStatus(ExecutionStatus.QUEUED);

        // When & Then
        assertEquals(execution1, execution2);
        assertEquals(execution1.hashCode(), execution2.hashCode());
    }

    @Test
    @DisplayName("Doit gérer toString()")
    void shouldHandleToString() {
        // Given
        Execution execution = new Execution();
        execution.setId(UUID.randomUUID());
        execution.setActionInstance(testActionInstance);
        execution.setStatus(ExecutionStatus.QUEUED);

        // When
        String toString = execution.toString();

        // Then
        assertNotNull(toString);
        assertTrue(toString.contains("QUEUED"));
    }
}
