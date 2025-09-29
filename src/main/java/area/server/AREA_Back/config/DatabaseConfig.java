package area.server.AREA_Back.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

/**
 * PostgreSQL database configuration.
 * This class explicitly configures the DataSource for PostgreSQL
 * and validates the configuration at application startup.
 *
 * Note: This configuration is only active for non-test profiles
 * to avoid conflicts with test configurations.
 */
@Configuration
@Profile("!test & !repository-test & !integration-test & !service-test & !unit-test & !redis-test & !default")
public class DatabaseConfig {

    @Value("${spring.datasource.url}")
    private String databaseUrl;

    @Value("${spring.datasource.username}")
    private String databaseUsername;

    @Value("${spring.datasource.driver-class-name}")
    private String driverClassName;

    /**
     * Main DataSource configuration for PostgreSQL.
     * This method validates that all database properties are set.
     *
     * @return DataSource configured for PostgreSQL
     * @throws IllegalStateException if database properties are not set
     */
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    @ConditionalOnProperty(name = "spring.datasource.url")
    public DataSource dataSource() {
        validateDatabaseConfiguration();

        return DataSourceBuilder.create()
                .driverClassName(driverClassName)
                .url(databaseUrl)
                .username(databaseUsername)
                .build();
    }

    /**
     * Validates that all required database properties are set.
     * This validation will fail application startup if properties are missing.
     */
    private void validateDatabaseConfiguration() {
        if (databaseUrl == null || databaseUrl.trim().isEmpty()) {
            throw new IllegalStateException(
                "DATABASE_URL environment variable is required but not set. "
                + "Please set DATABASE_URL to your PostgreSQL connection string."
            );
        }

        if (databaseUsername == null || databaseUsername.trim().isEmpty()) {
            throw new IllegalStateException(
                "DATABASE_USERNAME environment variable is required but not set. "
                + "Please set DATABASE_USERNAME to your PostgreSQL username."
            );
        }

        if (!databaseUrl.contains("postgresql")) {
            throw new IllegalStateException(
                "DATABASE_URL must be a PostgreSQL connection string. "
                + "Expected URL format: jdbc:postgresql://host:port/database"
            );
        }
    }
}