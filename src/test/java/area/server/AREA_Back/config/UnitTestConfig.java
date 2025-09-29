package area.server.AREA_Back.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Profile;

/**
 * Configuration de test pour les tests unitaires.
 * Cette configuration désactive certains beans pour les tests unitaires.
 */
@TestConfiguration
@Profile("unit-test")
public class UnitTestConfig {
    // Configuration spécifique aux tests unitaires
    // Les beans Redis et Database sont automatiquement mockés
}