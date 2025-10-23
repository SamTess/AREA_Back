package area.server.AREA_Back.controller;

import area.server.AREA_Back.dto.CreateServiceRequest;
import area.server.AREA_Back.dto.ServiceResponse;
import area.server.AREA_Back.entity.Service;
import area.server.AREA_Back.repository.ServiceRepository;
import area.server.AREA_Back.service.Auth.ServiceCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ServiceController Tests")
class ServiceControllerTest {

    @Mock
    private ServiceRepository serviceRepository;

    @Mock
    private ServiceCacheService serviceCacheService;

    @InjectMocks
    private ServiceController serviceController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(serviceController).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void getEnabledServicesShouldReturnList() throws Exception {
        Service s = new Service();
        s.setId(UUID.randomUUID());
        s.setKey("svc");
        s.setName("Service");

        when(serviceRepository.findAllEnabledServices()).thenReturn(List.of(s));

        mockMvc.perform(get("/api/services/enabled"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("svc"));

        verify(serviceRepository, times(1)).findAllEnabledServices();
    }

    @Test
    void createServiceConflictWhenKeyExists() throws Exception {
        CreateServiceRequest req = new CreateServiceRequest();
        req.setKey("dup");
        req.setName("Dup");

        when(serviceRepository.existsByKey("dup")).thenReturn(true);

        String body = "{\"key\":\"dup\",\"name\":\"Dup\"}";

    mockMvc.perform(post("/api/services").contentType("application/json").content(body))
        .andExpect(status().isConflict());

        verify(serviceRepository, times(1)).existsByKey("dup");
    }

    @Test
    void getServiceByIdNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(serviceRepository.findById(id)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/services/" + id.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getServiceByIdSuccess() throws Exception {
        UUID serviceId = UUID.randomUUID();
        Service service = new Service();
        service.setId(serviceId);
        service.setName("GitHub");
        service.setKey("github");
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(service));

        mockMvc.perform(get("/api/services/" + serviceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("GitHub"));
    }

    @Test
    void updateServiceNotFound() throws Exception {
        UUID serviceId = UUID.randomUUID();
        CreateServiceRequest request = new CreateServiceRequest();
        request.setKey("github");
        request.setName("GitHub");
        request.setAuth(Service.AuthType.OAUTH2);
        
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/services/" + serviceId)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateServiceSuccess() throws Exception {
        UUID serviceId = UUID.randomUUID();
        CreateServiceRequest request = new CreateServiceRequest();
        request.setKey("github");
        request.setName("GitHub Updated");
        request.setAuth(Service.AuthType.OAUTH2);
        
        Service existingService = new Service();
        existingService.setId(serviceId);
        
        Service updatedService = new Service();
        updatedService.setId(serviceId);
        updatedService.setName("GitHub Updated");
        
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(existingService));
        when(serviceRepository.save(any(Service.class))).thenReturn(updatedService);

        mockMvc.perform(put("/api/services/" + serviceId)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
        
        verify(serviceCacheService).invalidateServicesCache();
    }

    @Test
    void deleteServiceNotFound() throws Exception {
        UUID serviceId = UUID.randomUUID();
        when(serviceRepository.existsById(serviceId)).thenReturn(false);

        mockMvc.perform(delete("/api/services/" + serviceId))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteServiceSuccess() throws Exception {
        UUID serviceId = UUID.randomUUID();
        when(serviceRepository.existsById(serviceId)).thenReturn(true);
        doNothing().when(serviceRepository).deleteById(serviceId);

        mockMvc.perform(delete("/api/services/" + serviceId))
                .andExpect(status().isNoContent());
        
        verify(serviceCacheService).invalidateServicesCache();
    }

    @Test
    void getServicesCatalogFromCache() throws Exception {
        when(serviceCacheService.getAllServicesCached()).thenReturn(List.of(new ServiceResponse()));

        mockMvc.perform(get("/api/services/catalog"))
                .andExpect(status().isOk());
    }

    @Test
    void getServicesCatalogFallbackToUncached() throws Exception {
        when(serviceCacheService.getAllServicesCached()).thenThrow(new RuntimeException("Cache error"));
        when(serviceCacheService.getAllServicesUncached()).thenReturn(List.of(new ServiceResponse()));

        mockMvc.perform(get("/api/services/catalog"))
                .andExpect(status().isOk());
    }

    @Test
    void getEnabledServicesCatalogFromCache() throws Exception {
        when(serviceCacheService.getEnabledServicesCached()).thenReturn(List.of(new ServiceResponse()));

        mockMvc.perform(get("/api/services/catalog/enabled"))
                .andExpect(status().isOk());
    }

    @Test
    void getEnabledServicesCatalogFallbackToDatabase() throws Exception {
        Service service = new Service();
        service.setName("GitHub");
        
        when(serviceCacheService.getEnabledServicesCached()).thenThrow(new RuntimeException("Cache error"));
        when(serviceRepository.findAllEnabledServices()).thenReturn(List.of(service));

        mockMvc.perform(get("/api/services/catalog/enabled"))
                .andExpect(status().isOk());
    }

    @Test
    void searchServicesNoMatches() throws Exception {
        when(serviceRepository.findByNameContainingIgnoreCase("xyz")).thenReturn(List.of());

        mockMvc.perform(get("/api/services/search").param("name", "xyz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}
