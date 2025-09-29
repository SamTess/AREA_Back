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
 * Configuration de la base de données PostgreSQL.
 * Cette classe configure explicitement le DataSource pour PostgreSQL
 * et valide la configuration au démarrage de l'application.
 * 
 * Note: Cette configuration n'est active que pour les profils non-test
 * pour éviter les conflits avec les configurations de test.
 */
@Configuration
@Profile("!test & !repository-test & !integration-test & !service-test & !default")
public class DatabaseConfig {

    @Value("${spring.datasource.url}")
    private String databaseUrl;

    @Value("${spring.datasource.username}")
    private String databaseUsername;

    @Value("${spring.datasource.driver-class-name}")
    private String driverClassName;

    /**
     * Configuration du DataSource principal pour PostgreSQL.
     * Cette méthode valide que toutes les propriétés de base de données sont définies.
     *
     * @return DataSource configuré pour PostgreSQL
     * @throws IllegalStateException si les propriétés de base de données ne sont pas définies
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
     * Valide que toutes les propriétés de base de données requises sont définies.
     * Cette validation fait échouer le démarrage de l'application si des propriétés sont manquantes.
     */
    private void validateDatabaseConfiguration() {
        if (databaseUrl == null || databaseUrl.trim().isEmpty()) {
            throw new IllegalStateException(
                "DATABASE_URL environment variable is required but not set. " +
                "Please set DATABASE_URL to your PostgreSQL connection string."
            );
        }
        
        if (databaseUsername == null || databaseUsername.trim().isEmpty()) {
            throw new IllegalStateException(
                "DATABASE_USERNAME environment variable is required but not set. " +
                "Please set DATABASE_USERNAME to your PostgreSQL username."
            );
        }
        
        if (!databaseUrl.contains("postgresql")) {
            throw new IllegalStateException(
                "DATABASE_URL must be a PostgreSQL connection string. " +
                "Expected URL format: jdbc:postgresql://host:port/database"
            );
        }
    }
}