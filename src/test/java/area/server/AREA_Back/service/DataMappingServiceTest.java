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

    // ========== Tests pour evaluateSimpleCondition ==========

    @Test
    @DisplayName("Doit évaluer la condition 'equals' correctement")
    void shouldEvaluateEqualsCondition() {
        Map<String, Object> data = new HashMap<>();
        data.put("status", "active");

        Map<String, Object> condition = new HashMap<>();
        condition.put("field", "status");
        condition.put("operator", "equals");
        condition.put("value", "active");

        assertTrue(dataMappingService.evaluateCondition(data, condition));
    }

    @Test
    @DisplayName("Doit évaluer la condition 'not_equals' correctement")
    void shouldEvaluateNotEqualsCondition() {
        Map<String, Object> data = new HashMap<>();
        data.put("status", "active");

        Map<String, Object> condition = new HashMap<>();
        condition.put("field", "status");
        condition.put("operator", "not_equals");
        condition.put("value", "inactive");

        assertTrue(dataMappingService.evaluateCondition(data, condition));
    }

    @Test
    @DisplayName("Doit évaluer la condition 'contains' correctement")
    void shouldEvaluateContainsCondition() {
        Map<String, Object> data = new HashMap<>();
        data.put("message", "Hello World");

        Map<String, Object> condition = new HashMap<>();
        condition.put("field", "message");
        condition.put("operator", "contains");
        condition.put("value", "World");

        assertTrue(dataMappingService.evaluateCondition(data, condition));
    }

    @Test
    @DisplayName("Doit évaluer la condition 'not_contains' correctement")
    void shouldEvaluateNotContainsCondition() {
        Map<String, Object> data = new HashMap<>();
        data.put("message", "Hello World");

        Map<String, Object> condition = new HashMap<>();
        condition.put("field", "message");
        condition.put("operator", "not_contains");
        condition.put("value", "Goodbye");

        assertTrue(dataMappingService.evaluateCondition(data, condition));
    }

    @Test
    @DisplayName("Doit évaluer la condition 'starts_with' correctement")
    void shouldEvaluateStartsWithCondition() {
        Map<String, Object> data = new HashMap<>();
        data.put("name", "John Doe");

        Map<String, Object> condition = new HashMap<>();
        condition.put("field", "name");
        condition.put("operator", "starts_with");
        condition.put("value", "John");

        assertTrue(dataMappingService.evaluateCondition(data, condition));
    }

    @Test
    @DisplayName("Doit évaluer la condition 'ends_with' correctement")
    void shouldEvaluateEndsWithCondition() {
        Map<String, Object> data = new HashMap<>();
        data.put("file", "document.pdf");

        Map<String, Object> condition = new HashMap<>();
        condition.put("field", "file");
        condition.put("operator", "ends_with");
        condition.put("value", ".pdf");

        assertTrue(dataMappingService.evaluateCondition(data, condition));
    }

    @Test
    @DisplayName("Doit évaluer la condition 'regex' correctement")
    void shouldEvaluateRegexCondition() {
        Map<String, Object> data = new HashMap<>();
        data.put("email", "test@example.com");

        Map<String, Object> condition = new HashMap<>();
        condition.put("field", "email");
        condition.put("operator", "regex");
        condition.put("value", ".*@example\\.com");

        assertTrue(dataMappingService.evaluateCondition(data, condition));
    }

    @Test
    @DisplayName("Doit gérer les regex invalides")
    void shouldHandleInvalidRegex() {
        Map<String, Object> data = new HashMap<>();
        data.put("text", "test");

        Map<String, Object> condition = new HashMap<>();
        condition.put("field", "text");
        condition.put("operator", "regex");
        condition.put("value", "[invalid(regex");

        assertFalse(dataMappingService.evaluateCondition(data, condition));
    }

    @Test
    @DisplayName("Doit évaluer la condition 'greater_than' correctement")
    void shouldEvaluateGreaterThanCondition() {
        Map<String, Object> data = new HashMap<>();
        data.put("age", 30);

        Map<String, Object> condition = new HashMap<>();
        condition.put("field", "age");
        condition.put("operator", "greater_than");
        condition.put("value", 25);

        assertTrue(dataMappingService.evaluateCondition(data, condition));
    }

    @Test
    @DisplayName("Doit évaluer la condition 'less_than' correctement")
    void shouldEvaluateLessThanCondition() {
        Map<String, Object> data = new HashMap<>();
        data.put("age", 20);

        Map<String, Object> condition = new HashMap<>();
        condition.put("field", "age");
        condition.put("operator", "less_than");
        condition.put("value", 25);

        assertTrue(dataMappingService.evaluateCondition(data, condition));
    }

    @Test
    @DisplayName("Doit évaluer la condition 'greater_equal' correctement")
    void shouldEvaluateGreaterEqualCondition() {
        Map<String, Object> data = new HashMap<>();
        data.put("score", 100);

        Map<String, Object> condition = new HashMap<>();
        condition.put("field", "score");
        condition.put("operator", "greater_equal");
        condition.put("value", 100);

        assertTrue(dataMappingService.evaluateCondition(data, condition));
    }

    @Test
    @DisplayName("Doit évaluer la condition 'less_equal' correctement")
    void shouldEvaluateLessEqualCondition() {
        Map<String, Object> data = new HashMap<>();
        data.put("score", 50);

        Map<String, Object> condition = new HashMap<>();
        condition.put("field", "score");
        condition.put("operator", "less_equal");
        condition.put("value", 50);

        assertTrue(dataMappingService.evaluateCondition(data, condition));
    }

    @Test
    @DisplayName("Doit évaluer la condition 'exists' correctement")
    void shouldEvaluateExistsCondition() {
        Map<String, Object> data = new HashMap<>();
        data.put("name", "John");

        Map<String, Object> condition = new HashMap<>();
        condition.put("field", "name");
        condition.put("operator", "exists");

        assertTrue(dataMappingService.evaluateCondition(data, condition));
    }

    @Test
    @DisplayName("Doit évaluer la condition 'not_exists' correctement")
    void shouldEvaluateNotExistsCondition() {
        Map<String, Object> data = new HashMap<>();
        data.put("name", "John");

        Map<String, Object> condition = new HashMap<>();
        condition.put("field", "age");
        condition.put("operator", "not_exists");

        assertTrue(dataMappingService.evaluateCondition(data, condition));
    }

    @Test
    @DisplayName("Doit gérer un opérateur inconnu")
    void shouldHandleUnknownOperator() {
        Map<String, Object> data = new HashMap<>();
        data.put("field", "value");

        Map<String, Object> condition = new HashMap<>();
        condition.put("field", "field");
        condition.put("operator", "unknown_operator");
        condition.put("value", "value");

        assertFalse(dataMappingService.evaluateCondition(data, condition));
    }

    @Test
    @DisplayName("Doit utiliser 'equals' comme opérateur par défaut")
    void shouldUseEqualsAsDefaultOperator() {
        Map<String, Object> data = new HashMap<>();
        data.put("status", "active");

        Map<String, Object> condition = new HashMap<>();
        condition.put("field", "status");
        condition.put("value", "active");

        assertTrue(dataMappingService.evaluateCondition(data, condition));
    }

    // ========== Tests pour applyFormat ==========

    @Test
    @DisplayName("Doit formater en uppercase")
    void shouldFormatToUppercase() {
        Map<String, Object> input = new HashMap<>();
        input.put("text", "hello");

        Map<String, Object> transform = new HashMap<>();
        transform.put("type", "format");
        transform.put("source", "text");
        transform.put("format", "uppercase");

        Map<String, Object> mapping = new HashMap<>();
        mapping.put("result", transform);

        Map<String, Object> result = dataMappingService.applyMapping(input, mapping);

        assertEquals("HELLO", result.get("result"));
    }

    @Test
    @DisplayName("Doit formater en lowercase")
    void shouldFormatToLowercase() {
        Map<String, Object> input = new HashMap<>();
        input.put("text", "HELLO");

        Map<String, Object> transform = new HashMap<>();
        transform.put("type", "format");
        transform.put("source", "text");
        transform.put("format", "lowercase");

        Map<String, Object> mapping = new HashMap<>();
        mapping.put("result", transform);

        Map<String, Object> result = dataMappingService.applyMapping(input, mapping);

        assertEquals("hello", result.get("result"));
    }

    @Test
    @DisplayName("Doit appliquer trim")
    void shouldApplyTrim() {
        Map<String, Object> input = new HashMap<>();
        input.put("text", "  hello  ");

        Map<String, Object> transform = new HashMap<>();
        transform.put("type", "format");
        transform.put("source", "text");
        transform.put("format", "trim");

        Map<String, Object> mapping = new HashMap<>();
        mapping.put("result", transform);

        Map<String, Object> result = dataMappingService.applyMapping(input, mapping);

        assertEquals("hello", result.get("result"));
    }

    @Test
    @DisplayName("Doit gérer un format String.format")
    void shouldHandleStringFormat() {
        Map<String, Object> input = new HashMap<>();
        input.put("number", 42);

        Map<String, Object> transform = new HashMap<>();
        transform.put("type", "format");
        transform.put("source", "number");
        transform.put("format", "Value: %d");

        Map<String, Object> mapping = new HashMap<>();
        mapping.put("result", transform);

        Map<String, Object> result = dataMappingService.applyMapping(input, mapping);

        assertEquals("Value: 42", result.get("result"));
    }

    @Test
    @DisplayName("Doit gérer un format invalide")
    void shouldHandleInvalidFormat() {
        Map<String, Object> input = new HashMap<>();
        input.put("text", "hello");

        Map<String, Object> transform = new HashMap<>();
        transform.put("type", "format");
        transform.put("source", "text");
        transform.put("format", "%d");

        Map<String, Object> mapping = new HashMap<>();
        mapping.put("result", transform);

        Map<String, Object> result = dataMappingService.applyMapping(input, mapping);

        assertEquals("hello", result.get("result"));
    }

    @Test
    @DisplayName("Doit gérer une valeur null dans applyFormat")
    void shouldHandleNullValueInFormat() {
        Map<String, Object> input = new HashMap<>();
        input.put("text", null);

        Map<String, Object> transform = new HashMap<>();
        transform.put("type", "format");
        transform.put("source", "text");
        transform.put("format", "uppercase");

        Map<String, Object> mapping = new HashMap<>();
        mapping.put("result", transform);

        Map<String, Object> result = dataMappingService.applyMapping(input, mapping);

        assertNull(result.get("result"));
    }

    // ========== Tests pour convertToBoolean ==========

    @Test
    @DisplayName("Doit convertir un Boolean en Boolean")
    void shouldConvertBooleanToBoolean() {
        Map<String, Object> input = new HashMap<>();
        input.put("flag", true);

        Map<String, Object> transform = new HashMap<>();
        transform.put("type", "boolean");
        transform.put("source", "flag");

        Map<String, Object> mapping = new HashMap<>();
        mapping.put("result", transform);

        Map<String, Object> result = dataMappingService.applyMapping(input, mapping);

        assertEquals(true, result.get("result"));
    }

    @Test
    @DisplayName("Doit convertir 'true' en Boolean")
    void shouldConvertTrueStringToBoolean() {
        Map<String, Object> input = new HashMap<>();
        input.put("flag", "true");

        Map<String, Object> transform = new HashMap<>();
        transform.put("type", "boolean");
        transform.put("source", "flag");

        Map<String, Object> mapping = new HashMap<>();
        mapping.put("result", transform);

        Map<String, Object> result = dataMappingService.applyMapping(input, mapping);

        assertEquals(true, result.get("result"));
    }

    @Test
    @DisplayName("Doit convertir 'yes' en Boolean")
    void shouldConvertYesStringToBoolean() {
        Map<String, Object> input = new HashMap<>();
        input.put("flag", "yes");

        Map<String, Object> transform = new HashMap<>();
        transform.put("type", "boolean");
        transform.put("source", "flag");

        Map<String, Object> mapping = new HashMap<>();
        mapping.put("result", transform);

        Map<String, Object> result = dataMappingService.applyMapping(input, mapping);

        assertEquals(true, result.get("result"));
    }

    @Test
    @DisplayName("Doit convertir '1' en Boolean")
    void shouldConvertOneStringToBoolean() {
        Map<String, Object> input = new HashMap<>();
        input.put("flag", "1");

        Map<String, Object> transform = new HashMap<>();
        transform.put("type", "boolean");
        transform.put("source", "flag");

        Map<String, Object> mapping = new HashMap<>();
        mapping.put("result", transform);

        Map<String, Object> result = dataMappingService.applyMapping(input, mapping);

        assertEquals(true, result.get("result"));
    }

    @Test
    @DisplayName("Doit convertir 'false' en Boolean")
    void shouldConvertFalseStringToBoolean() {
        Map<String, Object> input = new HashMap<>();
        input.put("flag", "false");

        Map<String, Object> transform = new HashMap<>();
        transform.put("type", "boolean");
        transform.put("source", "flag");

        Map<String, Object> mapping = new HashMap<>();
        mapping.put("result", transform);

        Map<String, Object> result = dataMappingService.applyMapping(input, mapping);

        assertEquals(false, result.get("result"));
    }

    @Test
    @DisplayName("Doit convertir un nombre non-zéro en true")
    void shouldConvertNonZeroNumberToTrue() {
        Map<String, Object> input = new HashMap<>();
        input.put("flag", 42);

        Map<String, Object> transform = new HashMap<>();
        transform.put("type", "boolean");
        transform.put("source", "flag");

        Map<String, Object> mapping = new HashMap<>();
        mapping.put("result", transform);

        Map<String, Object> result = dataMappingService.applyMapping(input, mapping);

        assertEquals(true, result.get("result"));
    }

    @Test
    @DisplayName("Doit convertir zéro en false")
    void shouldConvertZeroToFalse() {
        Map<String, Object> input = new HashMap<>();
        input.put("flag", 0);

        Map<String, Object> transform = new HashMap<>();
        transform.put("type", "boolean");
        transform.put("source", "flag");

        Map<String, Object> mapping = new HashMap<>();
        mapping.put("result", transform);

        Map<String, Object> result = dataMappingService.applyMapping(input, mapping);

        assertEquals(false, result.get("result"));
    }

    @Test
    @DisplayName("Doit convertir un objet autre en false")
    void shouldConvertOtherObjectToFalse() {
        Map<String, Object> input = new HashMap<>();
        input.put("flag", new Object());

        Map<String, Object> transform = new HashMap<>();
        transform.put("type", "boolean");
        transform.put("source", "flag");

        Map<String, Object> mapping = new HashMap<>();
        mapping.put("result", transform);

        Map<String, Object> result = dataMappingService.applyMapping(input, mapping);

        assertEquals(false, result.get("result"));
    }

    // ========== Tests pour applyTemplate ==========

    @Test
    @DisplayName("Doit appliquer un template simple")
    void shouldApplySimpleTemplate() {
        Map<String, Object> input = new HashMap<>();
        input.put("name", "John");
        input.put("city", "Paris");
        input.put("source", "dummy");

        Map<String, Object> transform = new HashMap<>();
        transform.put("type", "template");
        transform.put("source", "source");
        transform.put("template", "Hello {{name}} from {{city}}");

        Map<String, Object> mapping = new HashMap<>();
        mapping.put("result", transform);

        Map<String, Object> result = dataMappingService.applyMapping(input, mapping);

        assertEquals("Hello John from Paris", result.get("result"));
    }

    @Test
    @DisplayName("Doit gérer un template avec valeur manquante")
    void shouldHandleTemplateMissingValue() {
        Map<String, Object> input = new HashMap<>();
        input.put("name", "John");
        input.put("source", "dummy");

        Map<String, Object> transform = new HashMap<>();
        transform.put("type", "template");
        transform.put("source", "source");
        transform.put("template", "Hello {{name}}, age: {{age}}");

        Map<String, Object> mapping = new HashMap<>();
        mapping.put("result", transform);

        Map<String, Object> result = dataMappingService.applyMapping(input, mapping);

        assertEquals("Hello John, age: ", result.get("result"));
    }

    @Test
    @DisplayName("Doit gérer un template null")
    void shouldHandleNullTemplate() {
        Map<String, Object> input = new HashMap<>();
        input.put("name", "John");
        input.put("source", "dummy");

        Map<String, Object> transform = new HashMap<>();
        transform.put("type", "template");
        transform.put("source", "source");
        transform.put("template", null);

        Map<String, Object> mapping = new HashMap<>();
        mapping.put("result", transform);

        Map<String, Object> result = dataMappingService.applyMapping(input, mapping);

        assertNull(result.get("result"));
    }

    @Test
    @DisplayName("Doit appliquer un template avec chemins nested")
    void shouldApplyTemplateWithNestedPaths() {
        Map<String, Object> address = new HashMap<>();
        address.put("city", "Paris");

        Map<String, Object> input = new HashMap<>();
        input.put("name", "John");
        input.put("address", address);
        input.put("source", "dummy");

        Map<String, Object> transform = new HashMap<>();
        transform.put("type", "template");
        transform.put("source", "source");
        transform.put("template", "{{name}} lives in {{address.city}}");

        Map<String, Object> mapping = new HashMap<>();
        mapping.put("result", transform);

        Map<String, Object> result = dataMappingService.applyMapping(input, mapping);

        assertEquals("John lives in Paris", result.get("result"));
    }

    // ========== Tests pour applyTransformation ==========

    @Test
    @DisplayName("Doit transformer en string")
    void shouldTransformToString() {
        Map<String, Object> input = new HashMap<>();
        input.put("number", 42);

        Map<String, Object> transform = new HashMap<>();
        transform.put("type", "string");
        transform.put("source", "number");

        Map<String, Object> mapping = new HashMap<>();
        mapping.put("result", transform);

        Map<String, Object> result = dataMappingService.applyMapping(input, mapping);

        assertEquals("42", result.get("result"));
    }

    @Test
    @DisplayName("Doit transformer en number")
    void shouldTransformToNumber() {
        Map<String, Object> input = new HashMap<>();
        input.put("text", "42");

        Map<String, Object> transform = new HashMap<>();
        transform.put("type", "number");
        transform.put("source", "text");

        Map<String, Object> mapping = new HashMap<>();
        mapping.put("result", transform);

        Map<String, Object> result = dataMappingService.applyMapping(input, mapping);

        assertEquals(42.0, result.get("result"));
    }

    @Test
    @DisplayName("Doit utiliser la valeur par défaut si source null")
    void shouldUseDefaultValueIfSourceNull() {
        Map<String, Object> input = new HashMap<>();

        Map<String, Object> transform = new HashMap<>();
        transform.put("type", "string");
        transform.put("source", "missing");
        transform.put("default", "default_value");

        Map<String, Object> mapping = new HashMap<>();
        mapping.put("result", transform);

        Map<String, Object> result = dataMappingService.applyMapping(input, mapping);

        assertEquals("default_value", result.get("result"));
    }

    @Test
    @DisplayName("Doit gérer une transformation sans type (direct)")
    void shouldHandleDirectTransformation() {
        Map<String, Object> input = new HashMap<>();
        input.put("value", "test");

        Map<String, Object> transform = new HashMap<>();
        transform.put("source", "value");

        Map<String, Object> mapping = new HashMap<>();
        mapping.put("result", transform);

        Map<String, Object> result = dataMappingService.applyMapping(input, mapping);

        assertEquals("test", result.get("result"));
    }

    // ========== Tests pour evaluateAndCondition ==========

    @Test
    @DisplayName("Doit évaluer une condition AND vraie")
    void shouldEvaluateAndConditionTrue() {
        Map<String, Object> data = new HashMap<>();
        data.put("age", 30);
        data.put("status", "active");

        Map<String, Object> cond1 = new HashMap<>();
        cond1.put("field", "age");
        cond1.put("operator", "greater_than");
        cond1.put("value", 25);

        Map<String, Object> cond2 = new HashMap<>();
        cond2.put("field", "status");
        cond2.put("operator", "equals");
        cond2.put("value", "active");

        Map<String, Object> condition = new HashMap<>();
        condition.put("operator", "and");
        condition.put("conditions", Arrays.asList(cond1, cond2));

        assertTrue(dataMappingService.evaluateCondition(data, condition));
    }

    @Test
    @DisplayName("Doit évaluer une condition AND fausse")
    void shouldEvaluateAndConditionFalse() {
        Map<String, Object> data = new HashMap<>();
        data.put("age", 20);
        data.put("status", "active");

        Map<String, Object> cond1 = new HashMap<>();
        cond1.put("field", "age");
        cond1.put("operator", "greater_than");
        cond1.put("value", 25);

        Map<String, Object> cond2 = new HashMap<>();
        cond2.put("field", "status");
        cond2.put("operator", "equals");
        cond2.put("value", "active");

        Map<String, Object> condition = new HashMap<>();
        condition.put("operator", "and");
        condition.put("conditions", Arrays.asList(cond1, cond2));

        assertFalse(dataMappingService.evaluateCondition(data, condition));
    }

    @Test
    @DisplayName("Doit gérer une condition AND vide")
    void shouldHandleEmptyAndCondition() {
        Map<String, Object> data = new HashMap<>();

        Map<String, Object> condition = new HashMap<>();
        condition.put("operator", "and");
        condition.put("conditions", new ArrayList<>());

        assertTrue(dataMappingService.evaluateCondition(data, condition));
    }

    @Test
    @DisplayName("Doit gérer une condition AND null")
    void shouldHandleNullAndCondition() {
        Map<String, Object> data = new HashMap<>();

        Map<String, Object> condition = new HashMap<>();
        condition.put("operator", "and");

        assertTrue(dataMappingService.evaluateCondition(data, condition));
    }

    // ========== Tests pour evaluateOrCondition ==========

    @Test
    @DisplayName("Doit évaluer une condition OR vraie")
    void shouldEvaluateOrConditionTrue() {
        Map<String, Object> data = new HashMap<>();
        data.put("age", 20);
        data.put("status", "active");

        Map<String, Object> cond1 = new HashMap<>();
        cond1.put("field", "age");
        cond1.put("operator", "greater_than");
        cond1.put("value", 25);

        Map<String, Object> cond2 = new HashMap<>();
        cond2.put("field", "status");
        cond2.put("operator", "equals");
        cond2.put("value", "active");

        Map<String, Object> condition = new HashMap<>();
        condition.put("operator", "or");
        condition.put("conditions", Arrays.asList(cond1, cond2));

        assertTrue(dataMappingService.evaluateCondition(data, condition));
    }

    @Test
    @DisplayName("Doit évaluer une condition OR fausse")
    void shouldEvaluateOrConditionFalse() {
        Map<String, Object> data = new HashMap<>();
        data.put("age", 20);
        data.put("status", "inactive");

        Map<String, Object> cond1 = new HashMap<>();
        cond1.put("field", "age");
        cond1.put("operator", "greater_than");
        cond1.put("value", 25);

        Map<String, Object> cond2 = new HashMap<>();
        cond2.put("field", "status");
        cond2.put("operator", "equals");
        cond2.put("value", "active");

        Map<String, Object> condition = new HashMap<>();
        condition.put("operator", "or");
        condition.put("conditions", Arrays.asList(cond1, cond2));

        assertFalse(dataMappingService.evaluateCondition(data, condition));
    }

    @Test
    @DisplayName("Doit gérer une condition OR vide")
    void shouldHandleEmptyOrCondition() {
        Map<String, Object> data = new HashMap<>();

        Map<String, Object> condition = new HashMap<>();
        condition.put("operator", "or");
        condition.put("conditions", new ArrayList<>());

        assertFalse(dataMappingService.evaluateCondition(data, condition));
    }

    @Test
    @DisplayName("Doit gérer une condition OR null")
    void shouldHandleNullOrCondition() {
        Map<String, Object> data = new HashMap<>();

        Map<String, Object> condition = new HashMap<>();
        condition.put("operator", "or");

        assertFalse(dataMappingService.evaluateCondition(data, condition));
    }

    // ========== Tests pour evaluateNotCondition ==========

    @Test
    @DisplayName("Doit évaluer une condition NOT")
    void shouldEvaluateNotCondition() {
        Map<String, Object> data = new HashMap<>();
        data.put("status", "active");

        Map<String, Object> subCondition = new HashMap<>();
        subCondition.put("field", "status");
        subCondition.put("operator", "equals");
        subCondition.put("value", "inactive");

        Map<String, Object> condition = new HashMap<>();
        condition.put("operator", "not");
        condition.put("condition", subCondition);

        assertTrue(dataMappingService.evaluateCondition(data, condition));
    }

    @Test
    @DisplayName("Doit inverser une condition vraie")
    void shouldInvertTrueCondition() {
        Map<String, Object> data = new HashMap<>();
        data.put("status", "active");

        Map<String, Object> subCondition = new HashMap<>();
        subCondition.put("field", "status");
        subCondition.put("operator", "equals");
        subCondition.put("value", "active");

        Map<String, Object> condition = new HashMap<>();
        condition.put("operator", "not");
        condition.put("condition", subCondition);

        assertFalse(dataMappingService.evaluateCondition(data, condition));
    }

    // ========== Tests pour compareNumbers ==========

    @Test
    @DisplayName("Doit comparer des nombres égaux")
    void shouldCompareEqualNumbers() {
        Map<String, Object> data = new HashMap<>();
        data.put("value", 42);

        Map<String, Object> condition = new HashMap<>();
        condition.put("field", "value");
        condition.put("operator", "equals");
        condition.put("value", 42);

        assertTrue(dataMappingService.evaluateCondition(data, condition));
    }

    @Test
    @DisplayName("Doit comparer des nombres avec valeur null")
    void shouldCompareNumbersWithNullValue() {
        Map<String, Object> data = new HashMap<>();
        data.put("value", null);

        Map<String, Object> condition = new HashMap<>();
        condition.put("field", "value");
        condition.put("operator", "greater_than");
        condition.put("value", 10);

        assertFalse(dataMappingService.evaluateCondition(data, condition));
    }

    @Test
    @DisplayName("Doit comparer des strings comme nombres")
    void shouldCompareStringAsNumbers() {
        Map<String, Object> data = new HashMap<>();
        data.put("value", "100");

        Map<String, Object> condition = new HashMap<>();
        condition.put("field", "value");
        condition.put("operator", "greater_than");
        condition.put("value", "50");

        assertTrue(dataMappingService.evaluateCondition(data, condition));
    }

    // ========== Tests pour stringMatchesRegex ==========

    @Test
    @DisplayName("Doit matcher un regex valide")
    void shouldMatchValidRegex() {
        Map<String, Object> data = new HashMap<>();
        data.put("code", "ABC123");

        Map<String, Object> condition = new HashMap<>();
        condition.put("field", "code");
        condition.put("operator", "regex");
        condition.put("value", "[A-Z]{3}\\d{3}");

        assertTrue(dataMappingService.evaluateCondition(data, condition));
    }

    @Test
    @DisplayName("Doit gérer un regex avec valeur null")
    void shouldHandleRegexWithNullValue() {
        Map<String, Object> data = new HashMap<>();
        data.put("code", null);

        Map<String, Object> condition = new HashMap<>();
        condition.put("field", "code");
        condition.put("operator", "regex");
        condition.put("value", ".*");

        assertFalse(dataMappingService.evaluateCondition(data, condition));
    }

    // ========== Tests pour convertToNumber ==========

    @Test
    @DisplayName("Doit convertir un nombre en nombre")
    void shouldConvertNumberToNumber() {
        Map<String, Object> input = new HashMap<>();
        input.put("value", 42);

        Map<String, Object> transform = new HashMap<>();
        transform.put("type", "number");
        transform.put("source", "value");

        Map<String, Object> mapping = new HashMap<>();
        mapping.put("result", transform);

        Map<String, Object> result = dataMappingService.applyMapping(input, mapping);

        assertEquals(42, result.get("result"));
    }

    @Test
    @DisplayName("Doit convertir une string en nombre")
    void shouldConvertStringToNumber() {
        Map<String, Object> input = new HashMap<>();
        input.put("value", "42.5");

        Map<String, Object> transform = new HashMap<>();
        transform.put("type", "number");
        transform.put("source", "value");

        Map<String, Object> mapping = new HashMap<>();
        mapping.put("result", transform);

        Map<String, Object> result = dataMappingService.applyMapping(input, mapping);

        assertEquals(42.5, result.get("result"));
    }

    @Test
    @DisplayName("Doit lever une exception pour une valeur non numérique")
    void shouldThrowExceptionForNonNumericValue() {
        Map<String, Object> input = new HashMap<>();
        input.put("value", "not_a_number");

        Map<String, Object> transform = new HashMap<>();
        transform.put("type", "number");
        transform.put("source", "value");

        Map<String, Object> mapping = new HashMap<>();
        mapping.put("result", transform);

        assertThrows(IllegalArgumentException.class, () -> {
            dataMappingService.applyMapping(input, mapping);
        });
    }

    // ========== Tests pour stringContains ==========

    @Test
    @DisplayName("Doit vérifier que la string contient la valeur")
    void shouldCheckStringContains() {
        Map<String, Object> data = new HashMap<>();
        data.put("message", "Hello World");

        Map<String, Object> condition = new HashMap<>();
        condition.put("field", "message");
        condition.put("operator", "contains");
        condition.put("value", "World");

        assertTrue(dataMappingService.evaluateCondition(data, condition));
    }

    @Test
    @DisplayName("Doit retourner false si la string ne contient pas la valeur")
    void shouldReturnFalseIfStringDoesNotContain() {
        Map<String, Object> data = new HashMap<>();
        data.put("message", "Hello World");

        Map<String, Object> condition = new HashMap<>();
        condition.put("field", "message");
        condition.put("operator", "contains");
        condition.put("value", "Goodbye");

        assertFalse(dataMappingService.evaluateCondition(data, condition));
    }

    @Test
    @DisplayName("Doit gérer une valeur null dans stringContains")
    void shouldHandleNullValueInStringContains() {
        Map<String, Object> data = new HashMap<>();
        data.put("message", null);

        Map<String, Object> condition = new HashMap<>();
        condition.put("field", "message");
        condition.put("operator", "contains");
        condition.put("value", "test");

        assertFalse(dataMappingService.evaluateCondition(data, condition));
    }

    // ========== Tests pour stringStartsWith ==========

    @Test
    @DisplayName("Doit vérifier que la string commence par la valeur")
    void shouldCheckStringStartsWith() {
        Map<String, Object> data = new HashMap<>();
        data.put("name", "John Doe");

        Map<String, Object> condition = new HashMap<>();
        condition.put("field", "name");
        condition.put("operator", "starts_with");
        condition.put("value", "John");

        assertTrue(dataMappingService.evaluateCondition(data, condition));
    }

    @Test
    @DisplayName("Doit retourner false si la string ne commence pas par la valeur")
    void shouldReturnFalseIfStringDoesNotStartWith() {
        Map<String, Object> data = new HashMap<>();
        data.put("name", "John Doe");

        Map<String, Object> condition = new HashMap<>();
        condition.put("field", "name");
        condition.put("operator", "starts_with");
        condition.put("value", "Doe");

        assertFalse(dataMappingService.evaluateCondition(data, condition));
    }

    @Test
    @DisplayName("Doit gérer une valeur null dans stringStartsWith")
    void shouldHandleNullValueInStringStartsWith() {
        Map<String, Object> data = new HashMap<>();
        data.put("name", null);

        Map<String, Object> condition = new HashMap<>();
        condition.put("field", "name");
        condition.put("operator", "starts_with");
        condition.put("value", "test");

        assertFalse(dataMappingService.evaluateCondition(data, condition));
    }

    // ========== Tests pour stringEndsWith ==========

    @Test
    @DisplayName("Doit vérifier que la string se termine par la valeur")
    void shouldCheckStringEndsWith() {
        Map<String, Object> data = new HashMap<>();
        data.put("file", "document.pdf");

        Map<String, Object> condition = new HashMap<>();
        condition.put("field", "file");
        condition.put("operator", "ends_with");
        condition.put("value", ".pdf");

        assertTrue(dataMappingService.evaluateCondition(data, condition));
    }

    @Test
    @DisplayName("Doit retourner false si la string ne se termine pas par la valeur")
    void shouldReturnFalseIfStringDoesNotEndWith() {
        Map<String, Object> data = new HashMap<>();
        data.put("file", "document.pdf");

        Map<String, Object> condition = new HashMap<>();
        condition.put("field", "file");
        condition.put("operator", "ends_with");
        condition.put("value", ".doc");

        assertFalse(dataMappingService.evaluateCondition(data, condition));
    }

    @Test
    @DisplayName("Doit gérer une valeur null dans stringEndsWith")
    void shouldHandleNullValueInStringEndsWith() {
        Map<String, Object> data = new HashMap<>();
        data.put("file", null);

        Map<String, Object> condition = new HashMap<>();
        condition.put("field", "file");
        condition.put("operator", "ends_with");
        condition.put("value", ".pdf");

        assertFalse(dataMappingService.evaluateCondition(data, condition));
    }

    // ========== Tests pour objectsEqual ==========

    @Test
    @DisplayName("Doit comparer deux objets égaux")
    void shouldCompareTwoEqualObjects() {
        Map<String, Object> data = new HashMap<>();
        data.put("status", "active");

        Map<String, Object> condition = new HashMap<>();
        condition.put("field", "status");
        condition.put("operator", "equals");
        condition.put("value", "active");

        assertTrue(dataMappingService.evaluateCondition(data, condition));
    }

    @Test
    @DisplayName("Doit comparer deux objets null")
    void shouldCompareTwoNullObjects() {
        Map<String, Object> data = new HashMap<>();
        data.put("status", null);

        Map<String, Object> condition = new HashMap<>();
        condition.put("field", "status");
        condition.put("operator", "equals");
        condition.put("value", null);

        assertTrue(dataMappingService.evaluateCondition(data, condition));
    }

    @Test
    @DisplayName("Doit retourner false si un objet est null")
    void shouldReturnFalseIfOneObjectIsNull() {
        Map<String, Object> data = new HashMap<>();
        data.put("status", "active");

        Map<String, Object> condition = new HashMap<>();
        condition.put("field", "status");
        condition.put("operator", "equals");
        condition.put("value", null);

        assertFalse(dataMappingService.evaluateCondition(data, condition));
    }

    @Test
    @DisplayName("Doit comparer des objets de types différents")
    void shouldCompareDifferentTypeObjects() {
        Map<String, Object> data = new HashMap<>();
        data.put("value", 42);

        Map<String, Object> condition = new HashMap<>();
        condition.put("field", "value");
        condition.put("operator", "equals");
        condition.put("value", "42");

        assertTrue(dataMappingService.evaluateCondition(data, condition));
    }
}
