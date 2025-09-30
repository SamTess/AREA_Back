package area.server.AREA_Back.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Profile;

@TestConfiguration
@Profile("unit-test")
public class UnitTestConfig {
    // Configuration spécifique aux tests unitaires
    // Les beans Redis et Database sont automatiquement mockés
}