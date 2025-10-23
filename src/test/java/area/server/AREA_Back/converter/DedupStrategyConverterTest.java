package area.server.AREA_Back.converter;

import area.server.AREA_Back.entity.enums.DedupStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour DedupStrategyConverter
 * Type: Tests Unitaires
 * Description: Teste la conversion entre DedupStrategy et valeurs de base de données
 */
@DisplayName("DedupStrategyConverter - Tests Unitaires")
class DedupStrategyConverterTest {

    private DedupStrategyConverter converter;

    @BeforeEach
    void setUp() {
        converter = new DedupStrategyConverter();
    }

    @Test
    @DisplayName("Doit convertir NONE vers la base de données")
    void shouldConvertNoneToDatabase() {
        // When
        String result = converter.convertToDatabaseColumn(DedupStrategy.NONE);

        // Then
        assertNotNull(result);
        assertEquals("NONE", result);
    }

    @Test
    @DisplayName("Doit convertir BY_PAYLOAD_HASH vers la base de données")
    void shouldConvertByPayloadHashToDatabase() {
        // When
        String result = converter.convertToDatabaseColumn(DedupStrategy.BY_PAYLOAD_HASH);

        // Then
        assertNotNull(result);
        assertEquals("BY_PAYLOAD_HASH", result);
    }

    @Test
    @DisplayName("Doit convertir BY_EXTERNAL_ID vers la base de données")
    void shouldConvertByExternalIdToDatabase() {
        // When
        String result = converter.convertToDatabaseColumn(DedupStrategy.BY_EXTERNAL_ID);

        // Then
        assertNotNull(result);
        assertEquals("BY_EXTERNAL_ID", result);
    }

    @Test
    @DisplayName("Doit retourner null pour null vers la base de données")
    void shouldReturnNullForNullToDatabase() {
        // When
        String result = converter.convertToDatabaseColumn(null);

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("Doit convertir none depuis la base de données")
    void shouldConvertNoneFromDatabase() {
        // When
        DedupStrategy result = converter.convertToEntityAttribute("none");

        // Then
        assertNotNull(result);
        assertEquals(DedupStrategy.NONE, result);
    }

    @Test
    @DisplayName("Doit convertir NONE en majuscules depuis la base de données")
    void shouldConvertNoneUppercaseFromDatabase() {
        // When
        DedupStrategy result = converter.convertToEntityAttribute("NONE");

        // Then
        assertNotNull(result);
        assertEquals(DedupStrategy.NONE, result);
    }

    @Test
    @DisplayName("Doit convertir by_payload_hash depuis la base de données")
    void shouldConvertByPayloadHashFromDatabase() {
        // When
        DedupStrategy result = converter.convertToEntityAttribute("by_payload_hash");

        // Then
        assertNotNull(result);
        assertEquals(DedupStrategy.BY_PAYLOAD_HASH, result);
    }

    @Test
    @DisplayName("Doit convertir BY_PAYLOAD_HASH en majuscules depuis la base de données")
    void shouldConvertByPayloadHashUppercaseFromDatabase() {
        // When
        DedupStrategy result = converter.convertToEntityAttribute("BY_PAYLOAD_HASH");

        // Then
        assertNotNull(result);
        assertEquals(DedupStrategy.BY_PAYLOAD_HASH, result);
    }

    @Test
    @DisplayName("Doit convertir by_external_id depuis la base de données")
    void shouldConvertByExternalIdFromDatabase() {
        // When
        DedupStrategy result = converter.convertToEntityAttribute("by_external_id");

        // Then
        assertNotNull(result);
        assertEquals(DedupStrategy.BY_EXTERNAL_ID, result);
    }

    @Test
    @DisplayName("Doit convertir BY_EXTERNAL_ID en majuscules depuis la base de données")
    void shouldConvertByExternalIdUppercaseFromDatabase() {
        // When
        DedupStrategy result = converter.convertToEntityAttribute("BY_EXTERNAL_ID");

        // Then
        assertNotNull(result);
        assertEquals(DedupStrategy.BY_EXTERNAL_ID, result);
    }

    @Test
    @DisplayName("Doit retourner null pour null depuis la base de données")
    void shouldReturnNullForNullFromDatabase() {
        // When
        DedupStrategy result = converter.convertToEntityAttribute(null);

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("Doit lancer IllegalArgumentException pour valeur inconnue")
    void shouldThrowExceptionForUnknownValue() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> converter.convertToEntityAttribute("invalid")
        );

        assertTrue(exception.getMessage().contains("Unknown dedup strategy"));
        assertTrue(exception.getMessage().contains("invalid"));
    }

    @Test
    @DisplayName("Doit lancer IllegalArgumentException pour valeur vide")
    void shouldThrowExceptionForEmptyValue() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> converter.convertToEntityAttribute("")
        );

        assertTrue(exception.getMessage().contains("Unknown dedup strategy"));
    }

    @Test
    @DisplayName("Doit convertir casse mixte vers lowercase")
    void shouldConvertMixedCaseToLowercase() {
        // When
        DedupStrategy result1 = converter.convertToEntityAttribute("NoNe");
        DedupStrategy result2 = converter.convertToEntityAttribute("By_Payload_Hash");
        DedupStrategy result3 = converter.convertToEntityAttribute("By_External_Id");

        // Then
        assertEquals(DedupStrategy.NONE, result1);
        assertEquals(DedupStrategy.BY_PAYLOAD_HASH, result2);
        assertEquals(DedupStrategy.BY_EXTERNAL_ID, result3);
    }

    @Test
    @DisplayName("Doit convertir toutes les valeurs de l'enum correctement")
    void shouldConvertAllEnumValues() {
        // Given
        DedupStrategy[] allStrategies = DedupStrategy.values();

        // When & Then
        for (DedupStrategy strategy : allStrategies) {
            String dbValue = converter.convertToDatabaseColumn(strategy);
            assertNotNull(dbValue);
            
            DedupStrategy convertedBack = converter.convertToEntityAttribute(dbValue);
            assertEquals(strategy, convertedBack);
        }
    }
}
