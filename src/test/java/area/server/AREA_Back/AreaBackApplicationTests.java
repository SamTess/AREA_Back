package area.server.AREA_Back;

import area.server.AREA_Back.repository.UserRepository;
import area.server.AREA_Back.repository.ServiceRepository;
import area.server.AREA_Back.repository.AreaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class AreaBackApplicationTests {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private AreaRepository areaRepository;

    @Test
    void contextLoads() {
        // Verify that the Spring context loads successfully
        assertThat(userRepository).isNotNull();
        assertThat(serviceRepository).isNotNull();
        assertThat(areaRepository).isNotNull();
    }

    @Test
    void applicationStartup() {
        // This test verifies that the application can start without errors
        // If this test passes, it means all components are correctly configured
    }
}
