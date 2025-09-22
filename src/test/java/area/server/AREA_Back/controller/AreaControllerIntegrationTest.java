package area.server.AREA_Back.controller;

import area.server.AREA_Back.dto.CreateAreaRequest;
import area.server.AREA_Back.entity.Area;
import area.server.AREA_Back.entity.Service;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.repository.AreaRepository;
import area.server.AREA_Back.repository.ServiceRepository;
import area.server.AREA_Back.repository.UserRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
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
@Import(AreaControllerIntegrationTest.TestConfig.class)
@ActiveProfiles("integration-test")
@Transactional
class AreaControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AreaRepository areaRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private User testUser;
    private Service actionService;
    private Service reactionService;
    private Area testArea;

    @BeforeEach
    void setUp() {
        areaRepository.deleteAll();
        serviceRepository.deleteAll();
        userRepository.deleteAll();

        // Create test user
        testUser = new User();
        testUser.setEmail("test@example.com");
        testUser.setUsername("testuser");
        testUser.setPassword(passwordEncoder.encode("password123"));
        testUser.setFirstName("Test");
        testUser.setLastName("User");
        testUser.setEnabled(true);
        testUser = userRepository.save(testUser);

        // Create action service
        actionService = new Service();
        actionService.setName("GitHub");
        actionService.setDescription("GitHub service");
        actionService.setIconUrl("https://github.com/favicon.ico");
        actionService.setEnabled(true);
        actionService.setApiEndpoint("https://api.github.com");
        actionService.setAuthType(Service.AuthType.OAUTH2);
        actionService = serviceRepository.save(actionService);

        // Create reaction service
        reactionService = new Service();
        reactionService.setName("Discord");
        reactionService.setDescription("Discord service");
        reactionService.setIconUrl("https://discord.com/favicon.ico");
        reactionService.setEnabled(true);
        reactionService.setApiEndpoint("https://discord.com/api");
        reactionService.setAuthType(Service.AuthType.OAUTH2);
        reactionService = serviceRepository.save(reactionService);

        // Create test area
        testArea = new Area();
        testArea.setName("GitHub to Discord");
        testArea.setDescription("Send Discord message when GitHub issue is created");
        testArea.setEnabled(true);
        testArea.setUser(testUser);
        testArea.setActionService(actionService);
        testArea.setActionType("issue_created");
        testArea.setActionConfig("{\"repository\": \"test-repo\"}");
        testArea.setReactionService(reactionService);
        testArea.setReactionType("send_message");
        testArea.setReactionConfig("{\"channel\": \"general\", \"message\": \"New issue created!\"}");
        testArea = areaRepository.save(testArea);
    }

    @Test
    void shouldGetAllAreas() throws Exception {
        mockMvc.perform(get("/api/areas")
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("GitHub to Discord"))
                .andExpect(jsonPath("$.content[0].description").value("Send Discord message when GitHub issue is created"))
                .andExpect(jsonPath("$.content[0].enabled").value(true))
                .andExpect(jsonPath("$.content[0].actionType").value("issue_created"))
                .andExpect(jsonPath("$.content[0].reactionType").value("send_message"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void shouldGetAreaById() throws Exception {
        mockMvc.perform(get("/api/areas/{id}", testArea.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(testArea.getId()))
                .andExpect(jsonPath("$.name").value("GitHub to Discord"))
                .andExpect(jsonPath("$.actionType").value("issue_created"))
                .andExpect(jsonPath("$.reactionType").value("send_message"));
    }

    @Test
    void shouldReturnNotFoundForNonExistentArea() throws Exception {
        mockMvc.perform(get("/api/areas/{id}", 99999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldGetAreasByUserId() throws Exception {
        mockMvc.perform(get("/api/areas/user/{userId}", testUser.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("GitHub to Discord"))
                .andExpect(jsonPath("$[0].userId").value(testUser.getId()));
    }

    @Test
    void shouldCreateNewArea() throws Exception {
        CreateAreaRequest request = new CreateAreaRequest();
        request.setName("Discord to Email");
        request.setDescription("Send email when Discord message is received");
        request.setUserId(testUser.getId());
        request.setActionServiceId(reactionService.getId()); // Using Discord as action
        request.setActionType("message_received");
        request.setActionConfig("{\"channel\": \"general\"}");
        request.setReactionServiceId(actionService.getId()); // Using GitHub as reaction (for testing)
        request.setReactionType("create_issue");
        request.setReactionConfig("{\"repository\": \"notifications\"}");

        mockMvc.perform(post("/api/areas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.name").value("Discord to Email"))
                .andExpect(jsonPath("$.description").value("Send email when Discord message is received"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.actionType").value("message_received"))
                .andExpect(jsonPath("$.reactionType").value("create_issue"));

        // Verify area was actually created in database
        assertThat(areaRepository.count()).isEqualTo(2);
    }

    @Test
    void shouldValidateCreateAreaRequest() throws Exception {
        CreateAreaRequest invalidRequest = new CreateAreaRequest();
        // Missing required fields

        mockMvc.perform(post("/api/areas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldNotCreateAreaWithNonExistentUser() throws Exception {
        CreateAreaRequest request = new CreateAreaRequest();
        request.setName("Invalid Area");
        request.setDescription("Area with non-existent user");
        request.setUserId(99999L); // Non-existent user
        request.setActionServiceId(actionService.getId());
        request.setActionType("test");
        request.setReactionServiceId(reactionService.getId());
        request.setReactionType("test");

        mockMvc.perform(post("/api/areas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldUpdateExistingArea() throws Exception {
        CreateAreaRequest request = new CreateAreaRequest();
        request.setName("Updated GitHub to Discord");
        request.setDescription("Updated description");
        request.setUserId(testUser.getId());
        request.setActionServiceId(actionService.getId());
        request.setActionType("pull_request_opened");
        request.setActionConfig("{\"repository\": \"updated-repo\"}");
        request.setReactionServiceId(reactionService.getId());
        request.setReactionType("send_message");
        request.setReactionConfig("{\"channel\": \"development\", \"message\": \"New PR opened!\"}");

        mockMvc.perform(put("/api/areas/{id}", testArea.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.name").value("Updated GitHub to Discord"))
                .andExpect(jsonPath("$.description").value("Updated description"))
                .andExpect(jsonPath("$.actionType").value("pull_request_opened"));

        // Verify update in database
        Area updatedArea = areaRepository.findById(testArea.getId()).orElseThrow();
        assertThat(updatedArea.getName()).isEqualTo("Updated GitHub to Discord");
        assertThat(updatedArea.getActionType()).isEqualTo("pull_request_opened");
    }

    @Test
    void shouldReturnNotFoundWhenUpdatingNonExistentArea() throws Exception {
        CreateAreaRequest request = new CreateAreaRequest();
        request.setName("NonExistent");
        request.setDescription("This area does not exist");
        request.setUserId(testUser.getId());
        request.setActionServiceId(actionService.getId());
        request.setActionType("test");
        request.setReactionServiceId(reactionService.getId());
        request.setReactionType("test");

        mockMvc.perform(put("/api/areas/{id}", 99999L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldDeleteExistingArea() throws Exception {
        mockMvc.perform(delete("/api/areas/{id}", testArea.getId()))
                .andExpect(status().isNoContent());

        // Verify area was actually deleted
        assertThat(areaRepository.findById(testArea.getId())).isEmpty();
    }

    @Test
    void shouldReturnNotFoundWhenDeletingNonExistentArea() throws Exception {
        mockMvc.perform(delete("/api/areas/{id}", 99999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldSearchAreasByName() throws Exception {
        mockMvc.perform(get("/api/areas/search")
                .param("name", "GitHub"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("GitHub to Discord"));
    }

    @Test
    void shouldReturnEmptyResultForNonMatchingAreaSearch() throws Exception {
        mockMvc.perform(get("/api/areas/search")
                .param("name", "nonexistent"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(0)));
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