package area.server.AREA_Back.controller;

import area.server.AREA_Back.dto.ServiceResponse;
import area.server.AREA_Back.repository.ServiceRepository;
import area.server.AREA_Back.service.ServiceCacheService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'intégration pour les nouveaux endpoints de cache du ServiceController
 */
@WebMvcTest(ServiceController.class)
@WithMockUser
class ServiceControllerCacheTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ServiceRepository serviceRepository;

    @MockBean
    private ServiceCacheService serviceCacheService;

    @Test
    void testGetServicesCatalog() throws Exception {
        // Given
        List<ServiceResponse> mockResponse = Arrays.asList();
        when(serviceCacheService.getAllServicesCached()).thenReturn(mockResponse);

        // When & Then
        mockMvc.perform(get("/api/services/catalog"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void testGetEnabledServicesCatalog() throws Exception {
        // Given
        List<ServiceResponse> mockResponse = Arrays.asList();
        when(serviceCacheService.getEnabledServicesCached()).thenReturn(mockResponse);

        // When & Then
        mockMvc.perform(get("/api/services/catalog/enabled"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());
    }
}