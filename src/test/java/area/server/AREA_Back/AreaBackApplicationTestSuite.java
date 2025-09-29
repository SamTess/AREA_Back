package area.server.AREA_Back;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties")
class AreaBackApplicationTestSuite {

    @Test
    void contextLoads() {
        // Test that Spring context loads successfully
        // This is a smoke test to ensure all configurations are correct
    }
}