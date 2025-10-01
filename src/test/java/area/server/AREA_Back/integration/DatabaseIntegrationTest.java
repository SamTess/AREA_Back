package area.server.AREA_Back.integration;

import area.server.AREA_Back.TestcontainersConfiguration;
import area.server.AREA_Back.entity.User;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests to validate application startup
 * with Testcontainers PostgreSQL and automatic schema creation via Flyway.
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
     * Test that the application starts correctly with PostgreSQL via Testcontainers
     * and that Flyway applies the migrations automatically.
     */
    @Test
    void testApplicationStartupWithPostgreSQL() {
        // The fact that this test runs proves that:
        // 1. The application starts without error
        // 2. The PostgreSQL connection works
        // 3. Flyway has applied the migrations
        assertNotNull(dataSource, "DataSource should be configured");
        assertNotNull(userRepository, "UserRepository should be available");
    }

    /**
     * Test that the 'area' schema is created and that the essential tables exist.
     */
    @Test
    void testDatabaseSchemaCreation() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();

            // Check that the essential tables exist
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
     * Test that the users and services tables have the correct constraints and columns.
     */
    @Test
    void testUsersAndServicesTablesStructure() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();

            // Test the a_users table
            Set<String> userColumns = new HashSet<>();
            try (ResultSet columns = metaData.getColumns(null, "area", "a_users", "%")) {
                while (columns.next()) {
                    userColumns.add(columns.getString("COLUMN_NAME"));
                }
            }

            Set<String> expectedUserColumns = Set.of(
                "id", "email", "is_active", "is_admin",
                "created_at", "confirmed_at", "last_login_at", "avatar_url"
            );

            for (String expectedColumn : expectedUserColumns) {
                assertTrue(userColumns.contains(expectedColumn),
                    "Column '" + expectedColumn + "' should exist in a_users table");
            }

            // Test the a_services table
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
     * Test that entities can be saved in the database via Flyway.
     */
    @Test
    void testEntityPersistenceWithFlywaySchema() {
        // Create and save a user
        User user = new User();
        user.setEmail("test@example.com");
        user.setIsActive(true);
        user.setIsAdmin(false);
        user.setCreatedAt(LocalDateTime.now());

        User savedUser = userRepository.save(user);

        assertNotNull(savedUser.getId(), "Saved user should have an ID");
        assertEquals("test@example.com", savedUser.getEmail());
        assertTrue(savedUser.getIsActive());
        assertFalse(savedUser.getIsAdmin());

        // Check that the user can be retrieved
        User foundUser = userRepository.findByEmail("test@example.com").orElse(null);
        assertNotNull(foundUser, "User should be found by email");
        assertEquals(savedUser.getId(), foundUser.getId());
    }

    /**
     * Test that Flyway has correctly configured the database constraints.
     */
    @Test
    void testDatabaseConstraints() {
        // Test unique constraint on email
        User user1 = new User();
        user1.setEmail("unique.test@example.com");
        user1.setIsActive(true);
        user1.setIsAdmin(false);
        user1.setCreatedAt(LocalDateTime.now());

        userRepository.save(user1);

        // Attempt to create a second user with the same email
        User user2 = new User();
        user2.setEmail("unique.test@example.com");
        user2.setIsActive(true);
        user2.setIsAdmin(false);
        user2.setCreatedAt(LocalDateTime.now());

        // This should throw an exception due to the unique constraint
        assertThrows(Exception.class, () -> {
            userRepository.save(user2);
            userRepository.flush(); // Force query execution
        }, "Duplicate email should violate unique constraint");
    }

    /**
     * Test that the PostgreSQL connection is properly configured (not H2 or another).
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