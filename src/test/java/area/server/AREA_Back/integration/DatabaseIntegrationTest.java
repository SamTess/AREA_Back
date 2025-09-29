package area.server.AREA_Back.integration;

import area.server.AREA_Back.TestcontainersConfiguration;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.entity.Service;
import area.server.AREA_Back.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests d'intégration pour valider le démarrage de l'application
 * avec Testcontainers PostgreSQL et la création automatique du schéma via Flyway.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("integration-test")
@TestPropertySource(locations = "classpath:application-integration-test.properties")
@Transactional
class DatabaseIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private UserRepository userRepository;

    /**
     * Test que l'application démarre correctement avec PostgreSQL via Testcontainers
     * et que Flyway applique les migrations automatiquement.
     */
    @Test
    void testApplicationStartupWithPostgreSQL() {
        // Le simple fait que ce test s'exécute prouve que :
        // 1. L'application démarre sans erreur
        // 2. La connexion PostgreSQL fonctionne
        // 3. Flyway a appliqué les migrations
        assertNotNull(dataSource, "DataSource should be configured");
        assertNotNull(userRepository, "UserRepository should be available");
    }

    /**
     * Test que le schéma 'area' est créé et que les tables essentielles existent.
     */
    @Test
    void testDatabaseSchemaCreation() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            
            // Vérifier que les tables essentielles existent
            Set<String> expectedTables = Set.of(
                "a_users", "a_services", "a_areas", "a_action_definitions",
                "a_action_instances", "a_activation_modes", "a_executions"
            );
            
            Set<String> foundTables = new HashSet<>();
            
            try (ResultSet tables = metaData.getTables(null, "area", "%", new String[]{"TABLE"})) {
                while (tables.next()) {
                    foundTables.add(tables.getString("TABLE_NAME"));
                }
            }
            
            for (String expectedTable : expectedTables) {
                assertTrue(foundTables.contains(expectedTable),
                    "Table '" + expectedTable + "' should exist in schema 'area'. Found tables: " + foundTables);
            }
        }
    }

    /**
     * Test que les tables users et services ont les bonnes contraintes et colonnes.
     */
    @Test
    void testUsersAndServicesTablesStructure() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            
            // Test de la table a_users
            Set<String> userColumns = new HashSet<>();
            try (ResultSet columns = metaData.getColumns(null, "area", "a_users", "%")) {
                while (columns.next()) {
                    userColumns.add(columns.getString("COLUMN_NAME"));
                }
            }
            
            Set<String> expectedUserColumns = Set.of(
                "id", "email", "password_hash", "is_active", "is_admin",
                "created_at", "confirmed_at", "last_login_at", "avatar_url"
            );
            
            for (String expectedColumn : expectedUserColumns) {
                assertTrue(userColumns.contains(expectedColumn),
                    "Column '" + expectedColumn + "' should exist in a_users table");
            }
            
            // Test de la table a_services
            Set<String> serviceColumns = new HashSet<>();
            try (ResultSet columns = metaData.getColumns(null, "area", "a_services", "%")) {
                while (columns.next()) {
                    serviceColumns.add(columns.getString("COLUMN_NAME"));
                }
            }
            
            Set<String> expectedServiceColumns = Set.of(
                "id", "key", "name", "auth", "docs_url", "icon_light_url",
                "icon_dark_url", "is_active", "created_at", "updated_at"
            );
            
            for (String expectedColumn : expectedServiceColumns) {
                assertTrue(serviceColumns.contains(expectedColumn),
                    "Column '" + expectedColumn + "' should exist in a_services table");
            }
        }
    }

    /**
     * Test que les entités peuvent être sauvegardées dans la base de données via Flyway.
     */
    @Test
    void testEntityPersistenceWithFlywaySchema() {
        // Créer et sauvegarder un utilisateur
        User user = new User();
        user.setEmail("integration.test@example.com");
        user.setPasswordHash("hashed_password");
        user.setIsActive(true);
        user.setIsAdmin(false);
        user.setCreatedAt(LocalDateTime.now());
        
        User savedUser = userRepository.save(user);
        
        assertNotNull(savedUser.getId(), "Saved user should have an ID");
        assertEquals("integration.test@example.com", savedUser.getEmail());
        assertTrue(savedUser.getIsActive());
        assertFalse(savedUser.getIsAdmin());
        
        // Vérifier que l'utilisateur peut être récupéré
        User foundUser = userRepository.findByEmail("integration.test@example.com").orElse(null);
        assertNotNull(foundUser, "User should be found by email");
        assertEquals(savedUser.getId(), foundUser.getId());
    }

    /**
     * Test que Flyway a correctement configuré les contraintes de base de données.
     */
    @Test
    void testDatabaseConstraints() {
        // Test de contrainte d'unicité sur l'email
        User user1 = new User();
        user1.setEmail("unique.test@example.com");
        user1.setPasswordHash("password1");
        user1.setIsActive(true);
        user1.setIsAdmin(false);
        user1.setCreatedAt(LocalDateTime.now());
        
        userRepository.save(user1);
        
        // Tentative de créer un deuxième utilisateur avec le même email
        User user2 = new User();
        user2.setEmail("unique.test@example.com");
        user2.setPasswordHash("password2");
        user2.setIsActive(true);
        user2.setIsAdmin(false);
        user2.setCreatedAt(LocalDateTime.now());
        
        // Cela devrait lever une exception due à la contrainte d'unicité
        assertThrows(Exception.class, () -> {
            userRepository.save(user2);
            userRepository.flush(); // Force l'exécution de la requête
        }, "Duplicate email should violate unique constraint");
    }

    /**
     * Test que la connexion PostgreSQL est bien configurée (pas H2 ou autre).
     */
    @Test
    void testPostgreSQLConfiguration() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String databaseProductName = metaData.getDatabaseProductName();
            
            assertEquals("PostgreSQL", databaseProductName,
                "Database should be PostgreSQL, not " + databaseProductName);
        }
    }
}