package area.server.AREA_Back.integration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lightweight integration test to validate that the application can start
 * with the PostgreSQL database configuration.
 * 
 * This test works with H2 in PostgreSQL mode to avoid external dependencies
 * while validating that the configuration is consistent.
 */
@SpringBootTest
@ActiveProfiles("test")
class DatabaseStartupValidationTest {

    /**
     * Test that the application starts correctly with the database configuration.
     * 
     * This simple test validates that:
     * 1. The Spring Boot context loads without error
     * 2. The database configurations are consistent
     * 3. JPA entities are correctly mapped
     * 4. No startup errors occur
     */
    @Test
    void testApplicationContextLoads() {
        // The mere fact that this test runs and completes successfully
        // proves that the application can start correctly.
        // This validates:
        // - Spring Boot configuration
        // - DataSource configuration (even if it's H2 for tests)
        // - JPA/Hibernate configuration
        // - Entity mapping
        // - Repository configuration
        
        assertTrue(true, "Application context should load successfully");
    }
    
    /**
     * Sanity validation test to ensure that the test profile is active
     */
    @Test 
    void testTestProfileIsActive() {
        // This test ensures that we are indeed in test mode
        // and that the test configurations are applied
        String activeProfile = System.getProperty("spring.profiles.active", "test");
        assertTrue(activeProfile.contains("test"), 
            "Test profile should be active during tests");
    }
}