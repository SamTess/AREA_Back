package area.server.AREA_Back.dto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour ActionDefinitionResponse
 * Type: Tests Unitaires
 * Description: Teste les DTOs de réponse pour les définitions d'actions
 */
@DisplayName("ActionDefinitionResponse - Tests Unitaires")
class ActionDefinitionResponseTest {

    private ActionDefinitionResponse response;
    private UUID testId;
    private UUID testServiceId;
    private LocalDateTime testDateTime;
    private Map<String, Object> testInputSchema;
    private Map<String, Object> testOutputSchema;
    private Map<String, Object> testThrottlePolicy;

    @BeforeEach
    void setUp() {
        testId = UUID.randomUUID();
        testServiceId = UUID.randomUUID();
        testDateTime = LocalDateTime.now();
        
        testInputSchema = new HashMap<>();
        testInputSchema.put("type", "object");
        testInputSchema.put("properties", Map.of("param1", "string"));
        
        testOutputSchema = new HashMap<>();
        testOutputSchema.put("type", "object");
        testOutputSchema.put("properties", Map.of("result", "string"));
        
        testThrottlePolicy = new HashMap<>();
        testThrottlePolicy.put("maxRequests", 100);
        testThrottlePolicy.put("timeWindow", 60);
        
        response = new ActionDefinitionResponse();
    }

    @Test
    @DisplayName("Doit créer un ActionDefinitionResponse avec constructeur no-args")
    void shouldCreateWithNoArgsConstructor() {
        // When
        ActionDefinitionResponse newResponse = new ActionDefinitionResponse();

        // Then
        assertNotNull(newResponse);
        assertNull(newResponse.getId());
        assertNull(newResponse.getName());
    }

    @Test
    @DisplayName("Doit créer un ActionDefinitionResponse avec constructeur all-args")
    void shouldCreateWithAllArgsConstructor() {
        // When
        ActionDefinitionResponse newResponse = new ActionDefinitionResponse(
            testId,
            testServiceId,
            "github",
            "GitHub",
            "create-issue",
            "Create Issue",
            "Creates a new issue in a repository",
            testInputSchema,
            testOutputSchema,
            "https://docs.github.com",
            true,
            true,
            1,
            300,
            testThrottlePolicy,
            testDateTime,
            testDateTime
        );

        // Then
        assertNotNull(newResponse);
        assertEquals(testId, newResponse.getId());
        assertEquals("github", newResponse.getServiceKey());
        assertEquals("Create Issue", newResponse.getName());
    }

    @Test
    @DisplayName("Doit définir et récupérer l'ID")
    void shouldSetAndGetId() {
        // When
        response.setId(testId);

        // Then
        assertEquals(testId, response.getId());
    }

    @Test
    @DisplayName("Doit définir et récupérer le serviceId")
    void shouldSetAndGetServiceId() {
        // When
        response.setServiceId(testServiceId);

        // Then
        assertEquals(testServiceId, response.getServiceId());
    }

    @Test
    @DisplayName("Doit définir et récupérer le serviceKey")
    void shouldSetAndGetServiceKey() {
        // When
        response.setServiceKey("github");

        // Then
        assertEquals("github", response.getServiceKey());
    }

    @Test
    @DisplayName("Doit définir et récupérer le serviceName")
    void shouldSetAndGetServiceName() {
        // When
        response.setServiceName("GitHub");

        // Then
        assertEquals("GitHub", response.getServiceName());
    }

    @Test
    @DisplayName("Doit définir et récupérer la clé")
    void shouldSetAndGetKey() {
        // When
        response.setKey("create-issue");

        // Then
        assertEquals("create-issue", response.getKey());
    }

    @Test
    @DisplayName("Doit définir et récupérer le nom")
    void shouldSetAndGetName() {
        // When
        response.setName("Create Issue");

        // Then
        assertEquals("Create Issue", response.getName());
    }

    @Test
    @DisplayName("Doit définir et récupérer la description")
    void shouldSetAndGetDescription() {
        // When
        response.setDescription("Creates a new issue");

        // Then
        assertEquals("Creates a new issue", response.getDescription());
    }

    @Test
    @DisplayName("Doit définir et récupérer inputSchema")
    void shouldSetAndGetInputSchema() {
        // When
        response.setInputSchema(testInputSchema);

        // Then
        assertEquals(testInputSchema, response.getInputSchema());
        assertTrue(response.getInputSchema().containsKey("type"));
    }

    @Test
    @DisplayName("Doit définir et récupérer outputSchema")
    void shouldSetAndGetOutputSchema() {
        // When
        response.setOutputSchema(testOutputSchema);

        // Then
        assertEquals(testOutputSchema, response.getOutputSchema());
        assertTrue(response.getOutputSchema().containsKey("type"));
    }

    @Test
    @DisplayName("Doit définir et récupérer docsUrl")
    void shouldSetAndGetDocsUrl() {
        // When
        response.setDocsUrl("https://docs.example.com");

        // Then
        assertEquals("https://docs.example.com", response.getDocsUrl());
    }

    @Test
    @DisplayName("Doit définir et récupérer isEventCapable")
    void shouldSetAndGetIsEventCapable() {
        // When
        response.setIsEventCapable(true);

        // Then
        assertTrue(response.getIsEventCapable());
    }

    @Test
    @DisplayName("Doit définir et récupérer isExecutable")
    void shouldSetAndGetIsExecutable() {
        // When
        response.setIsExecutable(true);

        // Then
        assertTrue(response.getIsExecutable());
    }

    @Test
    @DisplayName("Doit définir et récupérer la version")
    void shouldSetAndGetVersion() {
        // When
        response.setVersion(2);

        // Then
        assertEquals(2, response.getVersion());
    }

    @Test
    @DisplayName("Doit définir et récupérer defaultPollIntervalSeconds")
    void shouldSetAndGetDefaultPollIntervalSeconds() {
        // When
        response.setDefaultPollIntervalSeconds(300);

        // Then
        assertEquals(300, response.getDefaultPollIntervalSeconds());
    }

    @Test
    @DisplayName("Doit définir et récupérer throttlePolicy")
    void shouldSetAndGetThrottlePolicy() {
        // When
        response.setThrottlePolicy(testThrottlePolicy);

        // Then
        assertEquals(testThrottlePolicy, response.getThrottlePolicy());
        assertEquals(100, response.getThrottlePolicy().get("maxRequests"));
    }

    @Test
    @DisplayName("Doit définir et récupérer createdAt")
    void shouldSetAndGetCreatedAt() {
        // When
        response.setCreatedAt(testDateTime);

        // Then
        assertEquals(testDateTime, response.getCreatedAt());
    }

    @Test
    @DisplayName("Doit définir et récupérer updatedAt")
    void shouldSetAndGetUpdatedAt() {
        // When
        response.setUpdatedAt(testDateTime);

        // Then
        assertEquals(testDateTime, response.getUpdatedAt());
    }

    @Test
    @DisplayName("Doit gérer les valeurs null")
    void shouldHandleNullValues() {
        // When
        response.setId(null);
        response.setDescription(null);
        response.setDocsUrl(null);
        response.setInputSchema(null);
        response.setOutputSchema(null);
        response.setThrottlePolicy(null);

        // Then
        assertNull(response.getId());
        assertNull(response.getDescription());
        assertNull(response.getDocsUrl());
        assertNull(response.getInputSchema());
        assertNull(response.getOutputSchema());
        assertNull(response.getThrottlePolicy());
    }

    @Test
    @DisplayName("Doit tester equals et hashCode")
    void shouldTestEqualsAndHashCode() {
        // Given
        ActionDefinitionResponse response1 = new ActionDefinitionResponse();
        response1.setId(testId);
        response1.setKey("test-key");
        response1.setName("Test Action");

        ActionDefinitionResponse response2 = new ActionDefinitionResponse();
        response2.setId(testId);
        response2.setKey("test-key");
        response2.setName("Test Action");

        // Then
        assertEquals(response1, response2);
        assertEquals(response1.hashCode(), response2.hashCode());
    }

    @Test
    @DisplayName("Doit tester toString")
    void shouldTestToString() {
        // Given
        response.setId(testId);
        response.setKey("test-action");
        response.setName("Test Action");

        // When
        String toString = response.toString();

        // Then
        assertNotNull(toString);
        assertTrue(toString.contains("test-action"));
        assertTrue(toString.contains("Test Action"));
    }

    @Test
    @DisplayName("Doit gérer les schemas vides")
    void shouldHandleEmptySchemas() {
        // Given
        Map<String, Object> emptySchema = new HashMap<>();

        // When
        response.setInputSchema(emptySchema);
        response.setOutputSchema(emptySchema);

        // Then
        assertNotNull(response.getInputSchema());
        assertNotNull(response.getOutputSchema());
        assertTrue(response.getInputSchema().isEmpty());
        assertTrue(response.getOutputSchema().isEmpty());
    }

    @Test
    @DisplayName("Doit gérer les flags booléens false")
    void shouldHandleBooleanFlagsAsFalse() {
        // When
        response.setIsEventCapable(false);
        response.setIsExecutable(false);

        // Then
        assertFalse(response.getIsEventCapable());
        assertFalse(response.getIsExecutable());
    }

    @Test
    @DisplayName("Doit gérer les valeurs par défaut des versions")
    void shouldHandleDefaultVersionValues() {
        // When
        response.setVersion(1);

        // Then
        assertEquals(1, response.getVersion());
    }

    @Test
    @DisplayName("Doit gérer les schemas complexes")
    void shouldHandleComplexSchemas() {
        // Given
        Map<String, Object> complexSchema = new HashMap<>();
        complexSchema.put("type", "object");
        complexSchema.put("required", java.util.Arrays.asList("field1", "field2"));
        Map<String, Object> properties = new HashMap<>();
        properties.put("field1", Map.of("type", "string", "minLength", 1));
        properties.put("field2", Map.of("type", "integer", "minimum", 0));
        complexSchema.put("properties", properties);

        // When
        response.setInputSchema(complexSchema);

        // Then
        assertNotNull(response.getInputSchema());
        assertEquals("object", response.getInputSchema().get("type"));
        assertTrue(response.getInputSchema().containsKey("properties"));
    }

    @Test
    @DisplayName("Doit créer une réponse complète valide")
    void shouldCreateCompleteValidResponse() {
        // When
        response.setId(testId);
        response.setServiceId(testServiceId);
        response.setServiceKey("github");
        response.setServiceName("GitHub");
        response.setKey("create-issue");
        response.setName("Create Issue");
        response.setDescription("Creates a GitHub issue");
        response.setInputSchema(testInputSchema);
        response.setOutputSchema(testOutputSchema);
        response.setDocsUrl("https://docs.github.com");
        response.setIsEventCapable(true);
        response.setIsExecutable(true);
        response.setVersion(1);
        response.setDefaultPollIntervalSeconds(300);
        response.setThrottlePolicy(testThrottlePolicy);
        response.setCreatedAt(testDateTime);
        response.setUpdatedAt(testDateTime);

        // Then
        assertNotNull(response.getId());
        assertNotNull(response.getServiceId());
        assertNotNull(response.getKey());
        assertNotNull(response.getName());
        assertNotNull(response.getInputSchema());
        assertNotNull(response.getOutputSchema());
        assertTrue(response.getIsEventCapable());
        assertTrue(response.getIsExecutable());
    }
}
