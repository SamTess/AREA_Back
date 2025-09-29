package area.server.AREA_Back.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour la configuration de la base de données.
 * Valide que la configuration DataSource fonctionne correctement
 * et que les validations d'erreur sont appropriées.
 */
@SpringJUnitConfig
class DatabaseConfigTest {

    private DatabaseConfig databaseConfig;

    @BeforeEach
    void setUp() {
        databaseConfig = new DatabaseConfig();
    }

    @Test
    void testDataSourceConfigurationWithValidProperties() {
        // Given
        ReflectionTestUtils.setField(databaseConfig, "databaseUrl", 
            "jdbc:postgresql://localhost:5432/test_db");
        ReflectionTestUtils.setField(databaseConfig, "databaseUsername", "test_user");
        ReflectionTestUtils.setField(databaseConfig, "driverClassName", 
            "org.postgresql.Driver");

        // When
        DataSource dataSource = databaseConfig.dataSource();

        // Then
        assertNotNull(dataSource, "DataSource should not be null");
    }

    @Test
    void testDataSourceFailsWithEmptyUrl() {
        // Given
        ReflectionTestUtils.setField(databaseConfig, "databaseUrl", "");
        ReflectionTestUtils.setField(databaseConfig, "databaseUsername", "test_user");
        ReflectionTestUtils.setField(databaseConfig, "driverClassName", 
            "org.postgresql.Driver");

        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class, 
            () -> databaseConfig.dataSource());
        
        assertTrue(exception.getMessage().contains("DATABASE_URL environment variable is required"),
            "Exception message should mention DATABASE_URL is required");
    }

    @Test
    void testDataSourceFailsWithNullUrl() {
        // Given
        ReflectionTestUtils.setField(databaseConfig, "databaseUrl", null);
        ReflectionTestUtils.setField(databaseConfig, "databaseUsername", "test_user");
        ReflectionTestUtils.setField(databaseConfig, "driverClassName", 
            "org.postgresql.Driver");

        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class, 
            () -> databaseConfig.dataSource());
        
        assertTrue(exception.getMessage().contains("DATABASE_URL environment variable is required"),
            "Exception message should mention DATABASE_URL is required");
    }

    @Test
    void testDataSourceFailsWithEmptyUsername() {
        // Given
        ReflectionTestUtils.setField(databaseConfig, "databaseUrl", 
            "jdbc:postgresql://localhost:5432/test_db");
        ReflectionTestUtils.setField(databaseConfig, "databaseUsername", "");
        ReflectionTestUtils.setField(databaseConfig, "driverClassName", 
            "org.postgresql.Driver");

        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class, 
            () -> databaseConfig.dataSource());
        
        assertTrue(exception.getMessage().contains("DATABASE_USERNAME environment variable is required"),
            "Exception message should mention DATABASE_USERNAME is required");
    }

    @Test
    void testDataSourceFailsWithNullUsername() {
        // Given
        ReflectionTestUtils.setField(databaseConfig, "databaseUrl", 
            "jdbc:postgresql://localhost:5432/test_db");
        ReflectionTestUtils.setField(databaseConfig, "databaseUsername", null);
        ReflectionTestUtils.setField(databaseConfig, "driverClassName", 
            "org.postgresql.Driver");

        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class, 
            () -> databaseConfig.dataSource());
        
        assertTrue(exception.getMessage().contains("DATABASE_USERNAME environment variable is required"),
            "Exception message should mention DATABASE_USERNAME is required");
    }

    @Test
    void testDataSourceFailsWithNonPostgreSQLUrl() {
        // Given
        ReflectionTestUtils.setField(databaseConfig, "databaseUrl", 
            "jdbc:mysql://localhost:3306/test_db");
        ReflectionTestUtils.setField(databaseConfig, "databaseUsername", "test_user");
        ReflectionTestUtils.setField(databaseConfig, "driverClassName", 
            "com.mysql.cj.jdbc.Driver");

        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class, 
            () -> databaseConfig.dataSource());
        
        assertTrue(exception.getMessage().contains("DATABASE_URL must be a PostgreSQL connection string"),
            "Exception message should mention PostgreSQL requirement");
    }

    @Test
    void testDataSourceValidatesPostgreSQLUrl() {
        // Given - Valid PostgreSQL URLs
        String[] validUrls = {
            "jdbc:postgresql://localhost:5432/area_db",
            "jdbc:postgresql://postgres.example.com:5432/area_db",
            "jdbc:postgresql://127.0.0.1:5432/area_db?ssl=true"
        };

        for (String url : validUrls) {
            ReflectionTestUtils.setField(databaseConfig, "databaseUrl", url);
            ReflectionTestUtils.setField(databaseConfig, "databaseUsername", "test_user");
            ReflectionTestUtils.setField(databaseConfig, "driverClassName", 
                "org.postgresql.Driver");

            // When & Then
            assertDoesNotThrow(() -> databaseConfig.dataSource(),
                "Valid PostgreSQL URL should not throw exception: " + url);
        }
    }

    /**
     * Configuration de test pour isoler les tests unitaires.
     */
    @TestConfiguration
    static class TestConfig {
        @Bean
        DatabaseConfig databaseConfig() {
            return new DatabaseConfig();
        }
    }
}