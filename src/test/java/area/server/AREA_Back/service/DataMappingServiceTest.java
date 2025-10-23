package area.server.AREA_Back.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour DataMappingService
 * Type: Tests Unitaires
 * Description: Teste le service de mapping de données
 */
@DisplayName("DataMappingService - Tests Unitaires")
class DataMappingServiceTest {

    private DataMappingService dataMappingService;

    @BeforeEach
    void setUp() {
        dataMappingService = new DataMappingService();
    }

    @Test
    @DisplayName("Doit appliquer un mapping simple")
    void shouldApplySimpleMapping() {
        // Given
        Map<String, Object> input = new HashMap<>();
        input.put("name", "John");
        input.put("age", 30);

        Map<String, Object> mapping = new HashMap<>();
        mapping.put("userName", "name");
        mapping.put("userAge", "age");

        // When
        Map<String, Object> result = dataMappingService.applyMapping(input, mapping);

        // Then
        assertEquals("John", result.get("userName"));
        assertEquals(30, result.get("userAge"));
    }

    @Test
    @DisplayName("Doit extraire des valeurs nested")
    void shouldExtractNestedValues() {
        // Given
        Map<String, Object> address = new HashMap<>();
        address.put("city", "Paris");
        address.put("country", "France");

        Map<String, Object> input = new HashMap<>();
        input.put("name", "John");
        input.put("address", address);

        Map<String, Object> mapping = new HashMap<>();
        mapping.put("city", "address.city");
        mapping.put("country", "address.country");

        // When
        Map<String, Object> result = dataMappingService.applyMapping(input, mapping);

        // Then
        assertEquals("Paris", result.get("city"));
        assertEquals("France", result.get("country"));
    }

    @Test
    @DisplayName("Doit gérer les valeurs avec template")
    void shouldHandleTemplateValues() {
        // Given
        Map<String, Object> input = new HashMap<>();
        input.put("firstName", "John");
        input.put("lastName", "Doe");

        Map<String, Object> mapping = new HashMap<>();
        mapping.put("first", "{{firstName}}");
        mapping.put("last", "{{lastName}}");

        // When
        Map<String, Object> result = dataMappingService.applyMapping(input, mapping);

        // Then
        assertEquals("John", result.get("first"));
        assertEquals("Doe", result.get("last"));
    }

    @Test
    @DisplayName("Doit retourner l'input quand mapping est null")
    void shouldReturnInputWhenMappingIsNull() {
        // Given
        Map<String, Object> input = new HashMap<>();
        input.put("key", "value");

        // When
        Map<String, Object> result = dataMappingService.applyMapping(input, null);

        // Then
        assertEquals(input, result);
    }

    @Test
    @DisplayName("Doit retourner l'input quand mapping est vide")
    void shouldReturnInputWhenMappingIsEmpty() {
        // Given
        Map<String, Object> input = new HashMap<>();
        input.put("key", "value");

        Map<String, Object> mapping = new HashMap<>();

        // When
        Map<String, Object> result = dataMappingService.applyMapping(input, mapping);

        // Then
        assertEquals(input, result);
    }

    @Test
    @DisplayName("Doit gérer les chemins qui n'existent pas")
    void shouldHandleNonExistentPaths() {
        // Given
        Map<String, Object> input = new HashMap<>();
        input.put("name", "John");

        Map<String, Object> mapping = new HashMap<>();
        mapping.put("age", "nonExistent");

        // When
        Map<String, Object> result = dataMappingService.applyMapping(input, mapping);

        // Then
        assertNull(result.get("age"));
    }

    @Test
    @DisplayName("Doit gérer les chemins nested qui n'existent pas")
    void shouldHandleNonExistentNestedPaths() {
        // Given
        Map<String, Object> input = new HashMap<>();
        input.put("name", "John");

        Map<String, Object> mapping = new HashMap<>();
        mapping.put("city", "address.city");

        // When
        Map<String, Object> result = dataMappingService.applyMapping(input, mapping);

        // Then
        assertNull(result.get("city"));
    }

    @Test
    @DisplayName("Doit évaluer une condition simple vraie")
    void shouldEvaluateTrueSimpleCondition() {
        // Given
        Map<String, Object> data = new HashMap<>();
        data.put("age", 25);

        Map<String, Object> condition = new HashMap<>();
        condition.put("field", "age");
        condition.put("operator", "equals");
        condition.put("value", 25);

        // When
        boolean result = dataMappingService.evaluateCondition(data, condition);

        // Then
        assertTrue(result);
    }

    @Test
    @DisplayName("Doit évaluer une condition null comme vraie")
    void shouldEvaluateNullConditionAsTrue() {
        // Given
        Map<String, Object> data = new HashMap<>();
        data.put("key", "value");

        // When
        boolean result = dataMappingService.evaluateCondition(data, null);

        // Then
        assertTrue(result);
    }

    @Test
    @DisplayName("Doit évaluer une condition vide comme vraie")
    void shouldEvaluateEmptyConditionAsTrue() {
        // Given
        Map<String, Object> data = new HashMap<>();
        data.put("key", "value");

        Map<String, Object> condition = new HashMap<>();

        // When
        boolean result = dataMappingService.evaluateCondition(data, condition);

        // Then
        assertTrue(result);
    }

    @Test
    @DisplayName("Doit mapper les valeurs string comme des chemins")
    void shouldMapStringValuesAsPaths() {
        // Given
        Map<String, Object> input = new HashMap<>();
        input.put("name", "John");
        input.put("active", "yes");
        input.put("user", "admin");

        Map<String, Object> mapping = new HashMap<>();
        mapping.put("status", "active");
        mapping.put("type", "user");

        // When
        Map<String, Object> result = dataMappingService.applyMapping(input, mapping);

        // Then
        assertEquals("yes", result.get("status"));
        assertEquals("admin", result.get("type"));
    }

    @Test
    @DisplayName("Doit gérer les valeurs constantes non-String dans le mapping")
    void shouldHandleNonStringConstantValues() {
        // Given
        Map<String, Object> input = new HashMap<>();
        input.put("name", "John");

        Map<String, Object> mapping = new HashMap<>();
        mapping.put("count", 42);
        mapping.put("enabled", true);
        mapping.put("rating", 4.5);

        // When
        Map<String, Object> result = dataMappingService.applyMapping(input, mapping);

        // Then
        assertEquals(42, result.get("count"));
        assertEquals(true, result.get("enabled"));
        assertEquals(4.5, result.get("rating"));
    }

    @Test
    @DisplayName("Doit gérer les valeurs null dans l'input")
    void shouldHandleNullValuesInInput() {
        // Given
        Map<String, Object> input = new HashMap<>();
        input.put("name", null);
        input.put("age", 30);

        Map<String, Object> mapping = new HashMap<>();
        mapping.put("userName", "name");
        mapping.put("userAge", "age");

        // When
        Map<String, Object> result = dataMappingService.applyMapping(input, mapping);

        // Then
        assertNull(result.get("userName"));
        assertEquals(30, result.get("userAge"));
    }

    @Test
    @DisplayName("Doit gérer les nested maps complexes")
    void shouldHandleComplexNestedMaps() {
        // Given
        Map<String, Object> contact = new HashMap<>();
        contact.put("email", "john@example.com");
        contact.put("phone", "123456");

        Map<String, Object> address = new HashMap<>();
        address.put("street", "Main St");
        address.put("contact", contact);

        Map<String, Object> input = new HashMap<>();
        input.put("user", address);

        Map<String, Object> mapping = new HashMap<>();
        mapping.put("email", "user.contact.email");
        mapping.put("street", "user.street");

        // When
        Map<String, Object> result = dataMappingService.applyMapping(input, mapping);

        // Then
        assertEquals("john@example.com", result.get("email"));
        assertEquals("Main St", result.get("street"));
    }

    @Test
    @DisplayName("Doit gérer les chemins vides")
    void shouldHandleEmptyPaths() {
        // Given
        Map<String, Object> input = new HashMap<>();
        input.put("name", "John");

        Map<String, Object> mapping = new HashMap<>();
        mapping.put("value", "");

        // When
        Map<String, Object> result = dataMappingService.applyMapping(input, mapping);

        // Then
        assertNull(result.get("value"));
    }

    @Test
    @DisplayName("Doit gérer les types de données variés")
    void shouldHandleVariousDataTypes() {
        // Given
        Map<String, Object> input = new HashMap<>();
        input.put("string", "text");
        input.put("integer", 42);
        input.put("double", 3.14);
        input.put("boolean", true);
        input.put("list", Arrays.asList("a", "b", "c"));

        Map<String, Object> mapping = new HashMap<>();
        mapping.put("str", "string");
        mapping.put("int", "integer");
        mapping.put("dbl", "double");
        mapping.put("bool", "boolean");
        mapping.put("arr", "list");

        // When
        Map<String, Object> result = dataMappingService.applyMapping(input, mapping);

        // Then
        assertEquals("text", result.get("str"));
        assertEquals(42, result.get("int"));
        assertEquals(3.14, result.get("dbl"));
        assertEquals(true, result.get("bool"));
        assertEquals(Arrays.asList("a", "b", "c"), result.get("arr"));
    }

    @Test
    @DisplayName("Doit gérer les transformations de type Map")
    void shouldHandleMapTransformations() {
        // Given
        Map<String, Object> input = new HashMap<>();
        input.put("name", "John");

        Map<String, Object> transform = new HashMap<>();
        transform.put("type", "uppercase");
        transform.put("source", "name");

        Map<String, Object> mapping = new HashMap<>();
        mapping.put("upperName", transform);

        // When
        Map<String, Object> result = dataMappingService.applyMapping(input, mapping);

        // Then
        assertNotNull(result);
        assertTrue(result.containsKey("upperName"));
    }

    @Test
    @DisplayName("Doit gérer plusieurs mappings simultanément")
    void shouldHandleMultipleMappingsSimultaneously() {
        // Given
        Map<String, Object> input = new HashMap<>();
        input.put("firstName", "John");
        input.put("lastName", "Doe");
        input.put("age", 30);
        input.put("active", "yes");

        Map<String, Object> mapping = new HashMap<>();
        mapping.put("name", "firstName");
        mapping.put("surname", "lastName");
        mapping.put("userAge", "age");
        mapping.put("status", "active");

        // When
        Map<String, Object> result = dataMappingService.applyMapping(input, mapping);

        // Then
        assertEquals(4, result.size());
        assertEquals("John", result.get("name"));
        assertEquals("Doe", result.get("surname"));
        assertEquals(30, result.get("userAge"));
        assertEquals("yes", result.get("status"));
    }
}
