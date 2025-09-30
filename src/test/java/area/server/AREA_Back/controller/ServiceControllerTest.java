package area.server.AREA_Back.controller;

import area.server.AREA_Back.dto.CreateServiceRequest;
import area.server.AREA_Back.entity.Service;
import area.server.AREA_Back.repository.ServiceRepository;
import area.server.AREA_Back.service.ServiceCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ServiceController.class)
@ActiveProfiles("unit-test")
@WithMockUser
class ServiceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ServiceRepository serviceRepository;

    @MockitoBean
    private ServiceCacheService serviceCacheService;

    @Autowired
    private ObjectMapper objectMapper;

    private Service testService;
    private CreateServiceRequest createServiceRequest;

    @BeforeEach
    void setUp() {
        testService = new Service();
        testService.setId(UUID.randomUUID());
        testService.setKey("test-service");
        testService.setName("Test Service");
        testService.setAuth(Service.AuthType.OAUTH2);
        testService.setDocsUrl("https://docs.example.com");
        testService.setIconLightUrl("https://example.com/light.png");
        testService.setIconDarkUrl("https://example.com/dark.png");
        testService.setIsActive(true);
        testService.setCreatedAt(LocalDateTime.now());
        testService.setUpdatedAt(LocalDateTime.now());

        createServiceRequest = new CreateServiceRequest();
        createServiceRequest.setKey("new-service");
        createServiceRequest.setName("New Service");
        createServiceRequest.setAuth(Service.AuthType.APIKEY);
        createServiceRequest.setDocsUrl("https://newdocs.example.com");
        createServiceRequest.setIconLightUrl("https://newexample.com/light.png");
        createServiceRequest.setIconDarkUrl("https://newexample.com/dark.png");
    }

    @Test
    void testGetAllServices() throws Exception {
        Service service1 = new Service();
        service1.setId(UUID.randomUUID());
        service1.setKey("service-1");
        service1.setName("Service 1");
        service1.setAuth(Service.AuthType.OAUTH2);
        service1.setIsActive(true);

        Service service2 = new Service();
        service2.setId(UUID.randomUUID());
        service2.setKey("service-2");
        service2.setName("Service 2");
        service2.setAuth(Service.AuthType.APIKEY);
        service2.setIsActive(false);

        Page<Service> servicePage = new PageImpl<>(Arrays.asList(service1, service2));

        when(serviceRepository.findAll(any(PageRequest.class))).thenReturn(servicePage);

        mockMvc.perform(get("/api/services")
                .param("page", "0")
                .param("size", "20")
                .param("sortBy", "name")
                .param("sortDir", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].key").value("service-1"))
                .andExpect(jsonPath("$.content[1].key").value("service-2"));

        verify(serviceRepository).findAll(PageRequest.of(0, 20, Sort.by("name").ascending()));
    }

    @Test
    void testGetServiceById() throws Exception {
        when(serviceRepository.findById(testService.getId())).thenReturn(Optional.of(testService));

        mockMvc.perform(get("/api/services/{id}", testService.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testService.getId().toString()))
                .andExpect(jsonPath("$.key").value("test-service"))
                .andExpect(jsonPath("$.name").value("Test Service"))
                .andExpect(jsonPath("$.auth").value("OAUTH2"))
                .andExpect(jsonPath("$.isActive").value(true));

        verify(serviceRepository).findById(testService.getId());
    }

    @Test
    void testGetServiceByIdNotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        when(serviceRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/services/{id}", nonExistentId))
                .andExpect(status().isNotFound());

        verify(serviceRepository).findById(nonExistentId);
    }

    @Test
    void testGetEnabledServices() throws Exception {
        Service enabledService1 = new Service();
        enabledService1.setId(UUID.randomUUID());
        enabledService1.setKey("enabled-1");
        enabledService1.setName("Enabled Service 1");
        enabledService1.setIsActive(true);

        Service enabledService2 = new Service();
        enabledService2.setId(UUID.randomUUID());
        enabledService2.setKey("enabled-2");
        enabledService2.setName("Enabled Service 2");
        enabledService2.setIsActive(true);

        when(serviceRepository.findAllEnabledServices()).thenReturn(Arrays.asList(enabledService1, enabledService2));

        mockMvc.perform(get("/api/services/enabled"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].key").value("enabled-1"))
                .andExpect(jsonPath("$[1].key").value("enabled-2"));

        verify(serviceRepository).findAllEnabledServices();
    }

    @Test
    void testCreateService() throws Exception {
        when(serviceRepository.existsByKey(createServiceRequest.getKey())).thenReturn(false);
        when(serviceRepository.save(any(Service.class))).thenReturn(testService);

        mockMvc.perform(post("/api/services")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(objectMapper.writeValueAsString(createServiceRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value(testService.getKey()))
                .andExpect(jsonPath("$.name").value(testService.getName()))
                .andExpect(jsonPath("$.auth").value(testService.getAuth().toString()))
                .andExpect(jsonPath("$.isActive").value(true));

        verify(serviceRepository).existsByKey(createServiceRequest.getKey());
        verify(serviceRepository).save(any(Service.class));
    }

    @Test
    void testCreateServiceWithExistingKey() throws Exception {
        when(serviceRepository.existsByKey(createServiceRequest.getKey())).thenReturn(true);

        mockMvc.perform(post("/api/services")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(objectMapper.writeValueAsString(createServiceRequest)))
                .andExpect(status().isConflict());

        verify(serviceRepository).existsByKey(createServiceRequest.getKey());
        verify(serviceRepository, never()).save(any(Service.class));
    }

    @Test
    void testCreateServiceWithInvalidData() throws Exception {
        CreateServiceRequest invalidRequest = new CreateServiceRequest();
        invalidRequest.setKey(""); // Blank key should be invalid
        invalidRequest.setName("Valid Name");

        mockMvc.perform(post("/api/services")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verify(serviceRepository, never()).save(any(Service.class));
    }

    @Test
    void testUpdateService() throws Exception {
        when(serviceRepository.findById(testService.getId())).thenReturn(Optional.of(testService));

        Service updatedService = new Service();
        updatedService.setId(testService.getId());
        updatedService.setKey("updated-service");
        updatedService.setName("Updated Service");
        updatedService.setAuth(Service.AuthType.NONE);
        updatedService.setDocsUrl("https://updateddocs.example.com");
        updatedService.setIsActive(testService.getIsActive());

        when(serviceRepository.save(any(Service.class))).thenReturn(updatedService);

        CreateServiceRequest updateRequest = new CreateServiceRequest();
        updateRequest.setKey("updated-service");
        updateRequest.setName("Updated Service");
        updateRequest.setAuth(Service.AuthType.NONE);
        updateRequest.setDocsUrl("https://updateddocs.example.com");

        mockMvc.perform(put("/api/services/{id}", testService.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testService.getId().toString()))
                .andExpect(jsonPath("$.key").value("updated-service"))
                .andExpect(jsonPath("$.name").value("Updated Service"))
                .andExpect(jsonPath("$.auth").value("NONE"));

        verify(serviceRepository).findById(testService.getId());
        verify(serviceRepository).save(any(Service.class));
    }

    @Test
    void testUpdateServiceNotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        when(serviceRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/services/{id}", nonExistentId)
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(objectMapper.writeValueAsString(createServiceRequest)))
                .andExpect(status().isNotFound());

        verify(serviceRepository).findById(nonExistentId);
        verify(serviceRepository, never()).save(any(Service.class));
    }

    @Test
    void testDeleteService() throws Exception {
        when(serviceRepository.existsById(testService.getId())).thenReturn(true);

        mockMvc.perform(delete("/api/services/{id}", testService.getId())
                .with(csrf()))
                .andExpect(status().isNoContent());

        verify(serviceRepository).existsById(testService.getId());
        verify(serviceRepository).deleteById(testService.getId());
    }

    @Test
    void testDeleteServiceNotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        when(serviceRepository.existsById(nonExistentId)).thenReturn(false);

        mockMvc.perform(delete("/api/services/{id}", nonExistentId)
                .with(csrf()))
                .andExpect(status().isNotFound());

        verify(serviceRepository).existsById(nonExistentId);
        verify(serviceRepository, never()).deleteById(any(UUID.class));
    }

    @Test
    void testSearchServices() throws Exception {
        Service searchService1 = new Service();
        searchService1.setId(UUID.randomUUID());
        searchService1.setKey("google-service");
        searchService1.setName("Google Integration");
        searchService1.setIsActive(true);

        Service searchService2 = new Service();
        searchService2.setId(UUID.randomUUID());
        searchService2.setKey("gmail-service");
        searchService2.setName("Gmail API");
        searchService2.setIsActive(true);

        List<Service> searchResults = Arrays.asList(searchService1, searchService2);
        when(serviceRepository.findByNameContainingIgnoreCase("google")).thenReturn(searchResults);

        mockMvc.perform(get("/api/services/search")
                .with(csrf())
                .param("name", "google"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].key").value("google-service"))
                .andExpect(jsonPath("$[1].key").value("gmail-service"));

        verify(serviceRepository).findByNameContainingIgnoreCase("google");
    }

    @Test
    void testGetAllServicesWithDescendingSort() throws Exception {
        Page<Service> servicePage = new PageImpl<>(Arrays.asList(testService));
        when(serviceRepository.findAll(any(PageRequest.class))).thenReturn(servicePage);

        mockMvc.perform(get("/api/services")
                .param("page", "0")
                .param("size", "10")
                .param("sortBy", "createdAt")
                .param("sortDir", "desc"))
                .andExpect(status().isOk());

        verify(serviceRepository).findAll(PageRequest.of(0, 10, Sort.by("createdAt").descending()));
    }

    @Test
    void testGetAllServicesWithDefaultParameters() throws Exception {
        Page<Service> servicePage = new PageImpl<>(Arrays.asList(testService));
        when(serviceRepository.findAll(any(PageRequest.class))).thenReturn(servicePage);

        mockMvc.perform(get("/api/services"))
                .andExpect(status().isOk());

        verify(serviceRepository).findAll(PageRequest.of(0, 20, Sort.by("id").ascending()));
    }

    @Test
    void testCreateServiceWithDifferentAuthTypes() throws Exception {
        // Test OAUTH2
        CreateServiceRequest oauth2Request = new CreateServiceRequest();
        oauth2Request.setKey("oauth2-service");
        oauth2Request.setName("OAuth2 Service");
        oauth2Request.setAuth(Service.AuthType.OAUTH2);

        when(serviceRepository.existsByKey("oauth2-service")).thenReturn(false);
        when(serviceRepository.save(any(Service.class))).thenReturn(testService);

        mockMvc.perform(post("/api/services")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(objectMapper.writeValueAsString(oauth2Request)))
                .andExpect(status().isCreated());

        // Test APIKEY
        CreateServiceRequest apikeyRequest = new CreateServiceRequest();
        apikeyRequest.setKey("apikey-service");
        apikeyRequest.setName("API Key Service");
        apikeyRequest.setAuth(Service.AuthType.APIKEY);

        when(serviceRepository.existsByKey("apikey-service")).thenReturn(false);

        mockMvc.perform(post("/api/services")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(objectMapper.writeValueAsString(apikeyRequest)))
                .andExpect(status().isCreated());

        // Test NONE
        CreateServiceRequest noneRequest = new CreateServiceRequest();
        noneRequest.setKey("none-service");
        noneRequest.setName("No Auth Service");
        noneRequest.setAuth(Service.AuthType.NONE);

        when(serviceRepository.existsByKey("none-service")).thenReturn(false);

        mockMvc.perform(post("/api/services")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(objectMapper.writeValueAsString(noneRequest)))
                .andExpect(status().isCreated());

        verify(serviceRepository, times(3)).save(any(Service.class));
    }
}