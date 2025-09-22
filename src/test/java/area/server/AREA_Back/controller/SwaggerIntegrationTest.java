package area.server.AREA_Back.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@Import(SwaggerIntegrationTest.TestConfig.class)
@ActiveProfiles("integration-test")
class SwaggerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldAccessSwaggerUI() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }

    @Test
    void shouldAccessSwaggerUIIndex() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }

    @Test
    void shouldAccessOpenAPIDocumentation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.info").exists())
                .andExpect(jsonPath("$.info.title").value("AREA Backend API"))
                .andExpect(jsonPath("$.info.version").value("1.0.0"))
                .andExpect(jsonPath("$.paths").exists());
    }

    @Test
    @WithMockUser
    void shouldAccessOpenAPIDocumentationInYamlFormat() throws Exception {
        mockMvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Type"));
    }

    @Test
    void shouldIncludeUserEndpointsInOpenAPIDoc() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.paths['/api/users']").exists())
                .andExpect(jsonPath("$.paths['/api/users'].get").exists())
                .andExpect(jsonPath("$.paths['/api/users'].post").exists())
                .andExpect(jsonPath("$.paths['/api/users/{id}']").exists())
                .andExpect(jsonPath("$.paths['/api/users/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/users/{id}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/users/{id}'].delete").exists());
    }

    @Test
    void shouldIncludeServiceEndpointsInOpenAPIDoc() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.paths['/api/services']").exists())
                .andExpect(jsonPath("$.paths['/api/services'].get").exists())
                .andExpect(jsonPath("$.paths['/api/services'].post").exists())
                .andExpect(jsonPath("$.paths['/api/services/{id}']").exists())
                .andExpect(jsonPath("$.paths['/api/services/enabled']").exists());
    }

    @Test
    void shouldIncludeAreaEndpointsInOpenAPIDoc() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.paths['/api/areas']").exists())
                .andExpect(jsonPath("$.paths['/api/areas'].get").exists())
                .andExpect(jsonPath("$.paths['/api/areas'].post").exists())
                .andExpect(jsonPath("$.paths['/api/areas/{id}']").exists())
                .andExpect(jsonPath("$.paths['/api/areas/user/{userId}']").exists());
    }

    @Test
    void shouldIncludeComponentSchemasInOpenAPIDoc() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.components").exists())
                .andExpect(jsonPath("$.components.schemas").exists())
                // Check for DTO schemas
                .andExpect(jsonPath("$.components.schemas.UserResponse").exists())
                .andExpect(jsonPath("$.components.schemas.CreateUserRequest").exists())
                .andExpect(jsonPath("$.components.schemas.ServiceResponse").exists())
                .andExpect(jsonPath("$.components.schemas.CreateServiceRequest").exists())
                .andExpect(jsonPath("$.components.schemas.AreaResponse").exists())
                .andExpect(jsonPath("$.components.schemas.CreateAreaRequest").exists());
    }

    @Test
    void shouldIncludeTagsInOpenAPIDoc() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.tags").exists())
                .andExpect(jsonPath("$.tags[?(@.name == 'Users')]").exists())
                .andExpect(jsonPath("$.tags[?(@.name == 'Services')]").exists())
                .andExpect(jsonPath("$.tags[?(@.name == 'Areas')]").exists());
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