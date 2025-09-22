package area.server.AREA_Back.controller;

import area.server.AREA_Back.dto.CreateServiceRequest;
import area.server.AREA_Back.entity.Service;
import area.server.AREA_Back.repository.ServiceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@Import(ServiceControllerIntegrationTest.TestConfig.class)
@ActiveProfiles("integration-test")
@Transactional
class ServiceControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Service testService;

    @BeforeEach
    void setUp() {
        serviceRepository.deleteAll();

        testService = new Service();
        testService.setName("GitHub");
        testService.setDescription("GitHub service for managing repositories");
        testService.setIconUrl("https://github.com/favicon.ico");
        testService.setEnabled(true);
        testService.setApiEndpoint("https://api.github.com");
        testService.setAuthType(Service.AuthType.OAUTH2);

        testService = serviceRepository.save(testService);
    }

    @Test
    void shouldGetAllServices() throws Exception {
        mockMvc.perform(get("/api/services")
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("GitHub"))
                .andExpect(jsonPath("$.content[0].description").value("GitHub service for managing repositories"))
                .andExpect(jsonPath("$.content[0].iconUrl").value("https://github.com/favicon.ico"))
                .andExpect(jsonPath("$.content[0].enabled").value(true))
                .andExpect(jsonPath("$.content[0].authType").value("OAUTH2"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void shouldGetServiceById() throws Exception {
        mockMvc.perform(get("/api/services/{id}", testService.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(testService.getId()))
                .andExpect(jsonPath("$.name").value("GitHub"))
                .andExpect(jsonPath("$.description").value("GitHub service for managing repositories"))
                .andExpect(jsonPath("$.authType").value("OAUTH2"));
    }

    @Test
    void shouldReturnNotFoundForNonExistentService() throws Exception {
        mockMvc.perform(get("/api/services/{id}", 99999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldGetOnlyEnabledServices() throws Exception {
        // Create a disabled service
        Service disabledService = new Service();
        disabledService.setName("Disabled Service");
        disabledService.setDescription("This service is disabled");
        disabledService.setIconUrl("https://example.com/icon.png");
        disabledService.setEnabled(false);
        disabledService.setAuthType(Service.AuthType.API_KEY);
        serviceRepository.save(disabledService);

        mockMvc.perform(get("/api/services/enabled"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("GitHub"))
                .andExpect(jsonPath("$[0].enabled").value(true));
    }

    @Test
    void shouldCreateNewService() throws Exception {
        CreateServiceRequest request = new CreateServiceRequest();
        request.setName("Discord");
        request.setDescription("Discord messaging service");
        request.setIconUrl("https://discord.com/favicon.ico");
        request.setApiEndpoint("https://discord.com/api");
        request.setAuthType(Service.AuthType.OAUTH2);

        mockMvc.perform(post("/api/services")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.name").value("Discord"))
                .andExpect(jsonPath("$.description").value("Discord messaging service"))
                .andExpect(jsonPath("$.iconUrl").value("https://discord.com/favicon.ico"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.authType").value("OAUTH2"));

        // Verify service was actually created in database
        assertThat(serviceRepository.findByName("Discord")).isPresent();
    }

    @Test
    void shouldValidateCreateServiceRequest() throws Exception {
        CreateServiceRequest invalidRequest = new CreateServiceRequest();
        // Missing required fields

        mockMvc.perform(post("/api/services")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldNotCreateServiceWithDuplicateName() throws Exception {
        CreateServiceRequest request = new CreateServiceRequest();
        request.setName("GitHub"); // Same as existing service
        request.setDescription("Another GitHub service");
        request.setIconUrl("https://github.com/favicon.ico");

        mockMvc.perform(post("/api/services")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldUpdateExistingService() throws Exception {
        CreateServiceRequest request = new CreateServiceRequest();
        request.setName("GitHub");
        request.setDescription("Updated GitHub service description");
        request.setIconUrl("https://github.com/favicon.ico");
        request.setApiEndpoint("https://api.github.com/v4");
        request.setAuthType(Service.AuthType.OAUTH2);

        mockMvc.perform(put("/api/services/{id}", testService.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.description").value("Updated GitHub service description"))
                .andExpect(jsonPath("$.apiEndpoint").value("https://api.github.com/v4"))
                .andExpect(jsonPath("$.name").value("GitHub")); // Should remain the same

        // Verify update in database
        Service updatedService = serviceRepository.findById(testService.getId()).orElseThrow();
        assertThat(updatedService.getDescription()).isEqualTo("Updated GitHub service description");
        assertThat(updatedService.getApiEndpoint()).isEqualTo("https://api.github.com/v4");
    }

    @Test
    void shouldReturnNotFoundWhenUpdatingNonExistentService() throws Exception {
        CreateServiceRequest request = new CreateServiceRequest();
        request.setName("NonExistent");
        request.setDescription("This service does not exist");
        request.setIconUrl("https://example.com/icon.png");

        mockMvc.perform(put("/api/services/{id}", 99999L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldDeleteExistingService() throws Exception {
        mockMvc.perform(delete("/api/services/{id}", testService.getId()))
                .andExpect(status().isNoContent());

        // Verify service was actually deleted
        assertThat(serviceRepository.findById(testService.getId())).isEmpty();
    }

    @Test
    void shouldReturnNotFoundWhenDeletingNonExistentService() throws Exception {
        mockMvc.perform(delete("/api/services/{id}", 99999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldSearchServicesByName() throws Exception {
        mockMvc.perform(get("/api/services/search")
                .param("name", "GitHub"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("GitHub"));
    }

    @Test
    void shouldReturnEmptyResultForNonMatchingSearch() throws Exception {
        mockMvc.perform(get("/api/services/search")
                .param("name", "nonexistent"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void shouldSearchServicesByPartialName() throws Exception {
        mockMvc.perform(get("/api/services/search")
                .param("name", "Git"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("GitHub"));
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgresContainer() {
            return new PostgreSQLContainer<>(DockerImageName.parse("postgres:latest"));
        }

        @Bean
        @ServiceConnection(name = "redis")
        GenericContainer<?> redisContainer() {
            return new GenericContainer<>(DockerImageName.parse("redis:latest")).withExposedPorts(6379);
        }
    }
}