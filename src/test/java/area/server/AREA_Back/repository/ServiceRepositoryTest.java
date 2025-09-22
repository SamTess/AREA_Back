package area.server.AREA_Back.repository;

import area.server.AREA_Back.entity.Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ServiceRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ServiceRepository serviceRepository;

    private Service testService;

    @BeforeEach
    void setUp() {
        testService = new Service();
        testService.setName("Gmail");
        testService.setDescription("Google Gmail service");
        testService.setIconUrl("https://gmail.com/icon.png");
        testService.setApiEndpoint("https://gmail.googleapis.com");
        testService.setAuthType(Service.AuthType.OAUTH2);
        testService.setEnabled(true);
    }

    @Test
    void shouldFindServiceByName() {
        // Given
        entityManager.persistAndFlush(testService);

        // When
        Optional<Service> found = serviceRepository.findByName("Gmail");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getDescription()).isEqualTo("Google Gmail service");
    }

    @Test
    void shouldReturnTrueWhenServiceExistsByName() {
        // Given
        entityManager.persistAndFlush(testService);

        // When
        boolean exists = serviceRepository.existsByName("Gmail");

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    void shouldReturnFalseWhenServiceDoesNotExistByName() {
        // When
        boolean exists = serviceRepository.existsByName("NonExistentService");

        // Then
        assertThat(exists).isFalse();
    }

    @Test
    void shouldFindAllEnabledServices() {
        // Given
        Service disabledService = new Service();
        disabledService.setName("DisabledService");
        disabledService.setDescription("Disabled service");
        disabledService.setIconUrl("https://disabled.com/icon.png");
        disabledService.setEnabled(false);

        entityManager.persistAndFlush(testService);
        entityManager.persistAndFlush(disabledService);

        // When
        List<Service> enabledServices = serviceRepository.findAllEnabledServices();

        // Then
        assertThat(enabledServices).hasSize(1);
        assertThat(enabledServices.get(0).getName()).isEqualTo("Gmail");
    }

    @Test
    void shouldFindServicesByAuthType() {
        // Given
        Service apiKeyService = new Service();
        apiKeyService.setName("WeatherAPI");
        apiKeyService.setDescription("Weather service");
        apiKeyService.setIconUrl("https://weather.com/icon.png");
        apiKeyService.setAuthType(Service.AuthType.API_KEY);
        apiKeyService.setEnabled(true);

        entityManager.persistAndFlush(testService);
        entityManager.persistAndFlush(apiKeyService);

        // When
        List<Service> oauth2Services = serviceRepository.findByAuthType(Service.AuthType.OAUTH2);
        List<Service> apiKeyServices = serviceRepository.findByAuthType(Service.AuthType.API_KEY);

        // Then
        assertThat(oauth2Services).hasSize(1);
        assertThat(oauth2Services.get(0).getName()).isEqualTo("Gmail");
        
        assertThat(apiKeyServices).hasSize(1);
        assertThat(apiKeyServices.get(0).getName()).isEqualTo("WeatherAPI");
    }

    @Test
    void shouldFindServicesByNameContainingIgnoreCase() {
        // Given
        Service slackService = new Service();
        slackService.setName("Slack");
        slackService.setDescription("Slack messaging service");
        slackService.setIconUrl("https://slack.com/icon.png");
        slackService.setEnabled(true);

        entityManager.persistAndFlush(testService);
        entityManager.persistAndFlush(slackService);

        // When
        List<Service> services = serviceRepository.findByNameContainingIgnoreCase("mai");

        // Then
        assertThat(services).hasSize(1);
        assertThat(services.get(0).getName()).isEqualTo("Gmail");
    }

    @Test
    void shouldReturnEmptyListWhenNoServicesMatchNameSearch() {
        // Given
        entityManager.persistAndFlush(testService);

        // When
        List<Service> services = serviceRepository.findByNameContainingIgnoreCase("nonexistent");

        // Then
        assertThat(services).isEmpty();
    }
}