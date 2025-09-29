package area.server.AREA_Back.controller;

import area.server.AREA_Back.entity.ActionDefinition;
import area.server.AREA_Back.entity.Service;
import area.server.AREA_Back.repository.ActionDefinitionRepository;
import area.server.AREA_Back.repository.ServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AboutController.class)
@WithMockUser
@TestPropertySource(locations = "classpath:application-test.properties")
class AboutControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ServiceRepository serviceRepository;

    @MockitoBean
    private ActionDefinitionRepository actionDefinitionRepository;

    private Service testService;
    private ActionDefinition testActionDefinition;

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

        testActionDefinition = new ActionDefinition();
        testActionDefinition.setId(UUID.randomUUID());
        testActionDefinition.setService(testService);
        testActionDefinition.setKey("test-action");
        testActionDefinition.setName("Test Action");
        testActionDefinition.setDescription("Test action description");
        testActionDefinition.setIsEventCapable(true);
        testActionDefinition.setIsExecutable(true);
        testActionDefinition.setVersion(1);
        testActionDefinition.setCreatedAt(LocalDateTime.now());
        testActionDefinition.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    void testGetAbout() throws Exception {
        List<Service> services = Arrays.asList(testService);
        List<ActionDefinition> actionDefinitions = Arrays.asList(testActionDefinition);

        when(serviceRepository.findAllEnabledServices()).thenReturn(services);
        when(actionDefinitionRepository.findByServiceKey("test-service")).thenReturn(actionDefinitions);

        mockMvc.perform(get("/about.json"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.client.host").exists())
                .andExpect(jsonPath("$.server.current_time").exists())
                .andExpect(jsonPath("$.services").isArray())
                .andExpect(jsonPath("$.services.length()").value(1))
                .andExpect(jsonPath("$.services[0].name").value("Test Service"))
                .andExpect(jsonPath("$.services[0].key").value("test-service"))
                .andExpect(jsonPath("$.services[0].actions").isArray())
                .andExpect(jsonPath("$.services[0].actions.length()").value(1))
                .andExpect(jsonPath("$.services[0].actions[0].name").value("Test Action"))
                .andExpect(jsonPath("$.services[0].actions[0].key").value("test-action"))
                .andExpect(jsonPath("$.services[0].reactions").isArray());

        verify(serviceRepository).findAllEnabledServices();
        verify(actionDefinitionRepository, times(2)).findByServiceKey("test-service");
    }

    @Test
    void testGetAboutWithMultipleServices() throws Exception {
        Service service2 = new Service();
        service2.setId(UUID.randomUUID());
        service2.setKey("service-2");
        service2.setName("Service 2");
        service2.setAuth(Service.AuthType.APIKEY);
        service2.setIsActive(true);
        service2.setCreatedAt(LocalDateTime.now());

        ActionDefinition action2 = new ActionDefinition();
        action2.setId(UUID.randomUUID());
        action2.setService(service2);
        action2.setKey("action-2");
        action2.setName("Action 2");
        action2.setDescription("Second action");
        action2.setIsEventCapable(false);
        action2.setIsExecutable(true);
        action2.setVersion(2);
        action2.setCreatedAt(LocalDateTime.now());

        List<Service> services = Arrays.asList(testService, service2);
        List<ActionDefinition> actionDefinitions1 = Arrays.asList(testActionDefinition);
        List<ActionDefinition> actionDefinitions2 = Arrays.asList(action2);

        when(serviceRepository.findAllEnabledServices()).thenReturn(services);
        when(actionDefinitionRepository.findByServiceKey("test-service")).thenReturn(actionDefinitions1);
        when(actionDefinitionRepository.findByServiceKey("service-2")).thenReturn(actionDefinitions2);

        mockMvc.perform(get("/about.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.services").isArray())
                .andExpect(jsonPath("$.services.length()").value(2))
                .andExpect(jsonPath("$.services[0].name").value("Test Service"))
                .andExpect(jsonPath("$.services[1].name").value("Service 2"))
                .andExpect(jsonPath("$.services[0].actions.length()").value(1))
                .andExpect(jsonPath("$.services[1].actions.length()").value(0)); // action2 has isEventCapable=false

        verify(serviceRepository).findAllEnabledServices();
        verify(actionDefinitionRepository, times(2)).findByServiceKey("test-service");
        verify(actionDefinitionRepository, times(2)).findByServiceKey("service-2");
    }

    @Test
    void testGetAboutWithNoServices() throws Exception {
        when(serviceRepository.findAllEnabledServices()).thenReturn(Arrays.asList());

        mockMvc.perform(get("/about.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.services").isArray())
                .andExpect(jsonPath("$.services.length()").value(0))
                .andExpect(jsonPath("$.client.host").exists())
                .andExpect(jsonPath("$.server.current_time").exists());

        verify(serviceRepository).findAllEnabledServices();
    }

    @Test
    void testGetAboutWithServiceWithoutActions() throws Exception {
        Service serviceWithoutActions = new Service();
        serviceWithoutActions.setId(UUID.randomUUID());
        serviceWithoutActions.setKey("no-actions-service");
        serviceWithoutActions.setName("No Actions Service");
        serviceWithoutActions.setAuth(Service.AuthType.NONE);
        serviceWithoutActions.setIsActive(true);
        serviceWithoutActions.setCreatedAt(LocalDateTime.now());

        List<Service> services = Arrays.asList(serviceWithoutActions);
        List<ActionDefinition> actionDefinitions = Arrays.asList(); // No actions

        when(serviceRepository.findAllEnabledServices()).thenReturn(services);
        when(actionDefinitionRepository.findByServiceKey("no-actions-service")).thenReturn(actionDefinitions);

        mockMvc.perform(get("/about.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.services").isArray())
                .andExpect(jsonPath("$.services.length()").value(1))
                .andExpect(jsonPath("$.services[0].name").value("No Actions Service"))
                .andExpect(jsonPath("$.services[0].actions").isArray())
                .andExpect(jsonPath("$.services[0].actions.length()").value(0))
                .andExpect(jsonPath("$.services[0].reactions").isArray())
                .andExpect(jsonPath("$.services[0].reactions.length()").value(0));

        verify(serviceRepository).findAllEnabledServices();
        verify(actionDefinitionRepository, times(2)).findByServiceKey("no-actions-service");
    }
}