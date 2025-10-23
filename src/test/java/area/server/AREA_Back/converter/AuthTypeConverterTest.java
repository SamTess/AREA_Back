package area.server.AREA_Back.converter;

import area.server.AREA_Back.entity.Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour AuthTypeConverter
 * Type: Tests Unitaires
 * Description: Teste la conversion entre AuthType et valeurs de base de données
 */
@DisplayName("AuthTypeConverter - Tests Unitaires")
class AuthTypeConverterTest {

    private AuthTypeConverter converter;

    @BeforeEach
    void setUp() {
        converter = new AuthTypeConverter();
    }

    @Test
    @DisplayName("Doit convertir OAUTH2 vers la base de données")
    void shouldConvertOAuth2ToDatabase() {
        // When
        Object result = converter.convertToDatabaseColumn(Service.AuthType.OAUTH2);

        // Then
        assertNotNull(result);
        assertEquals("OAUTH2", result.toString());
    }

    @Test
    @DisplayName("Doit convertir APIKEY vers la base de données")
    void shouldConvertApiKeyToDatabase() {
        // When
        Object result = converter.convertToDatabaseColumn(Service.AuthType.APIKEY);

        // Then
        assertNotNull(result);
        assertEquals("APIKEY", result.toString());
    }

    @Test
    @DisplayName("Doit convertir NONE vers la base de données")
    void shouldConvertNoneToDatabase() {
        // When
        Object result = converter.convertToDatabaseColumn(Service.AuthType.NONE);

        // Then
        assertNotNull(result);
        assertEquals("NONE", result.toString());
    }

    @Test
    @DisplayName("Doit retourner null pour null vers la base de données")
    void shouldReturnNullForNullToDatabase() {
        // When
        Object result = converter.convertToDatabaseColumn(null);

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("Doit convertir oauth2 depuis la base de données")
    void shouldConvertOAuth2FromDatabase() {
        // When
        Service.AuthType result = converter.convertToEntityAttribute("oauth2");

        // Then
        assertNotNull(result);
        assertEquals(Service.AuthType.OAUTH2, result);
    }

    @Test
    @DisplayName("Doit convertir OAUTH2 en majuscules depuis la base de données")
    void shouldConvertOAuth2UppercaseFromDatabase() {
        // When
        Service.AuthType result = converter.convertToEntityAttribute("OAUTH2");

        // Then
        assertNotNull(result);
        assertEquals(Service.AuthType.OAUTH2, result);
    }

    @Test
    @DisplayName("Doit convertir apikey depuis la base de données")
    void shouldConvertApiKeyFromDatabase() {
        // When
        Service.AuthType result = converter.convertToEntityAttribute("apikey");

        // Then
        assertNotNull(result);
        assertEquals(Service.AuthType.APIKEY, result);
    }

    @Test
    @DisplayName("Doit convertir APIKEY en majuscules depuis la base de données")
    void shouldConvertApiKeyUppercaseFromDatabase() {
        // When
        Service.AuthType result = converter.convertToEntityAttribute("APIKEY");

        // Then
        assertNotNull(result);
        assertEquals(Service.AuthType.APIKEY, result);
    }

    @Test
    @DisplayName("Doit convertir none depuis la base de données")
    void shouldConvertNoneFromDatabase() {
        // When
        Service.AuthType result = converter.convertToEntityAttribute("none");

        // Then
        assertNotNull(result);
        assertEquals(Service.AuthType.NONE, result);
    }

    @Test
    @DisplayName("Doit convertir NONE en majuscules depuis la base de données")
    void shouldConvertNoneUppercaseFromDatabase() {
        // When
        Service.AuthType result = converter.convertToEntityAttribute("NONE");

        // Then
        assertNotNull(result);
        assertEquals(Service.AuthType.NONE, result);
    }

    @Test
    @DisplayName("Doit retourner null pour null depuis la base de données")
    void shouldReturnNullForNullFromDatabase() {
        // When
        Service.AuthType result = converter.convertToEntityAttribute(null);

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

        assertTrue(exception.getMessage().contains("Unknown auth type"));
        assertTrue(exception.getMessage().contains("invalid"));
    }

    @Test
    @DisplayName("Doit gérer les casses mixtes correctement")
    void shouldHandleMixedCase() {
        // When
        Service.AuthType result1 = converter.convertToEntityAttribute("OAuth2");
        Service.AuthType result2 = converter.convertToEntityAttribute("ApiKey");
        Service.AuthType result3 = converter.convertToEntityAttribute("NoNe");

        // Then
        assertEquals(Service.AuthType.OAUTH2, result1);
        assertEquals(Service.AuthType.APIKEY, result2);
        assertEquals(Service.AuthType.NONE, result3);
    }

    @Test
    @DisplayName("Doit être insensible à la casse")
    void shouldBeCaseInsensitive() {
        // When
        Service.AuthType lower = converter.convertToEntityAttribute("oauth2");
        Service.AuthType upper = converter.convertToEntityAttribute("OAUTH2");
        Service.AuthType mixed = converter.convertToEntityAttribute("OaUtH2");

        // Then
        assertEquals(Service.AuthType.OAUTH2, lower);
        assertEquals(Service.AuthType.OAUTH2, upper);
        assertEquals(Service.AuthType.OAUTH2, mixed);
    }

    @Test
    @DisplayName("Doit effectuer une conversion bidirectionnelle correctement")
    void shouldPerformRoundTripConversionCorrectly() {
        // Given
        Service.AuthType[] authTypes = {
            Service.AuthType.OAUTH2,
            Service.AuthType.APIKEY,
            Service.AuthType.NONE
        };

        for (Service.AuthType authType : authTypes) {
            // When
            Object dbValue = converter.convertToDatabaseColumn(authType);
            Service.AuthType reconverted = converter.convertToEntityAttribute(dbValue);

            // Then
            assertEquals(authType, reconverted,
                "Round-trip conversion failed for " + authType);
        }
    }
}
