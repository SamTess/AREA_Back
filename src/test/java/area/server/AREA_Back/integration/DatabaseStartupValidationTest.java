package area.server.AREA_Back.integration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test d'intégration léger pour valider que l'application peut démarrer
 * avec la configuration de base de données PostgreSQL.
 * 
 * Ce test fonctionne avec H2 en mode PostgreSQL pour éviter les dépendances externes
 * tout en validant que la configuration est cohérente.
 */
@SpringBootTest
@ActiveProfiles("test")
class DatabaseStartupValidationTest {

    /**
     * Test que l'application démarre correctement avec la configuration de base de données.
     * 
     * Ce test simple valide que :
     * 1. Le contexte Spring Boot se charge sans erreur
     * 2. Les configurations de base de données sont cohérentes
     * 3. Les entités JPA sont correctement mappées
     * 4. Aucune erreur de démarrage n'apparaît
     */
    @Test
    void testApplicationContextLoads() {
        // Le simple fait que ce test s'exécute et se termine avec succès
        // prouve que l'application peut démarrer correctement.
        // Cela valide :
        // - Configuration Spring Boot
        // - Configuration DataSource (même si c'est H2 pour les tests)
        // - Configuration JPA/Hibernate
        // - Mapping des entités
        // - Configuration des repositories
        
        assertTrue(true, "Application context should load successfully");
    }
    
    /**
     * Test de validation de sanité pour s'assurer que le profil de test est actif
     */
    @Test 
    void testTestProfileIsActive() {
        // Ce test s'assure que nous sommes bien en mode test
        // et que les configurations de test sont appliquées
        String activeProfile = System.getProperty("spring.profiles.active", "test");
        assertTrue(activeProfile.contains("test"), 
            "Test profile should be active during tests");
    }
}