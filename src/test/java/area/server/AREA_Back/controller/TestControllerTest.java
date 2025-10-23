package area.server.AREA_Back.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TestController Tests")
class TestControllerTest {

    @InjectMocks
    private TestController testController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(testController).build();
    }

    @Test
    @DisplayName("Should return health check status with 200")
    void testHealth() throws Exception {
        mockMvc.perform(get("/api/test/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.message").value("The AREA API is working correctly"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Should return swagger test status with 200")
    void testSwaggerTest() throws Exception {
        mockMvc.perform(get("/api/test/swagger-test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.swagger").value("OK"))
                .andExpect(jsonPath("$.openapi").value("WORKING"))
                .andExpect(jsonPath("$.documentation").value("AVAILABLE"));
    }

    @Test
    @DisplayName("Health endpoint should return valid timestamp format")
    void testHealthTimestampFormat() throws Exception {
        mockMvc.perform(get("/api/test/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    @DisplayName("Swagger test endpoint should return all required fields")
    void testSwaggerTestFields() throws Exception {
        mockMvc.perform(get("/api/test/swagger-test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.swagger").exists())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.documentation").exists());
    }
}
