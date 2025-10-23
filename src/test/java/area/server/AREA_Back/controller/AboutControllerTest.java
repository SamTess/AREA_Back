package area.server.AREA_Back.controller;

import area.server.AREA_Back.entity.ActionDefinition;
import area.server.AREA_Back.entity.Service;
import area.server.AREA_Back.repository.ActionDefinitionRepository;
import area.server.AREA_Back.repository.ServiceRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour AboutController
 * Type: Tests Unitaires
 * Description: Teste le contrôleur About
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AboutController - Tests Unitaires")
class AboutControllerTest {

    @Mock
    private ServiceRepository serviceRepository;

    @Mock
    private ActionDefinitionRepository actionDefinitionRepository;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private AboutController aboutController;

    private Service testService;
    private ActionDefinition testAction;
    private ActionDefinition testReaction;

    @BeforeEach
    void setUp() {
        testService = new Service();
        testService.setId(UUID.randomUUID());
        testService.setKey("test-service");
        testService.setName("Test Service");
        testService.setIsActive(true);

        testAction = new ActionDefinition();
        testAction.setId(UUID.randomUUID());
        testAction.setService(testService);
        testAction.setKey("test-action");
        testAction.setName("Test Action");
        testAction.setDescription("Test action description");
        testAction.setIsEventCapable(true);
        testAction.setIsExecutable(false);

        testReaction = new ActionDefinition();
        testReaction.setId(UUID.randomUUID());
        testReaction.setService(testService);
        testReaction.setKey("test-reaction");
        testReaction.setName("Test Reaction");
        testReaction.setDescription("Test reaction description");
        testReaction.setIsEventCapable(false);
        testReaction.setIsExecutable(true);
    }

    @Test
    @DisplayName("Doit retourner les informations about avec succès")
    void shouldReturnAboutInformationSuccessfully() {
        // Given
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
        when(serviceRepository.findAllEnabledServices()).thenReturn(List.of(testService));
        when(actionDefinitionRepository.findByServiceKey("test-service"))
            .thenReturn(List.of(testAction, testReaction));

        // When
        ResponseEntity<Map<String, Object>> response = aboutController.getAbout(request);

        // Then
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertTrue(body.containsKey("client"));
        assertTrue(body.containsKey("server"));

        @SuppressWarnings("unchecked")
        Map<String, Object> client = (Map<String, Object>) body.get("client");
        assertEquals("192.168.1.1", client.get("host"));

        @SuppressWarnings("unchecked")
        Map<String, Object> server = (Map<String, Object>) body.get("server");
        assertTrue(server.containsKey("current_time"));
        assertTrue(server.containsKey("services"));
    }

    @Test
    @DisplayName("Doit extraire l'IP depuis X-Forwarded-For")
    void shouldExtractIpFromXForwardedFor() {
        // Given
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.1, 198.51.100.1");
        when(serviceRepository.findAllEnabledServices()).thenReturn(Collections.emptyList());

        // When
        ResponseEntity<Map<String, Object>> response = aboutController.getAbout(request);

        // Then
        @SuppressWarnings("unchecked")
        Map<String, Object> client = (Map<String, Object>) response.getBody().get("client");
        assertEquals("203.0.113.1", client.get("host"));
    }

    @Test
    @DisplayName("Doit extraire l'IP depuis X-Real-IP")
    void shouldExtractIpFromXRealIp() {
        // Given
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn("203.0.113.2");
        when(serviceRepository.findAllEnabledServices()).thenReturn(Collections.emptyList());

        // When
        ResponseEntity<Map<String, Object>> response = aboutController.getAbout(request);

        // Then
        @SuppressWarnings("unchecked")
        Map<String, Object> client = (Map<String, Object>) response.getBody().get("client");
        assertEquals("203.0.113.2", client.get("host"));
    }

    @Test
    @DisplayName("Doit utiliser getRemoteAddr par défaut")
    void shouldUseRemoteAddrByDefault() {
        // Given
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");
        when(serviceRepository.findAllEnabledServices()).thenReturn(Collections.emptyList());

        // When
        ResponseEntity<Map<String, Object>> response = aboutController.getAbout(request);

        // Then
        @SuppressWarnings("unchecked")
        Map<String, Object> client = (Map<String, Object>) response.getBody().get("client");
        assertEquals("192.168.1.100", client.get("host"));
    }

    @Test
    @DisplayName("Doit inclure les services avec actions et reactions")
    void shouldIncludeServicesWithActionsAndReactions() {
        // Given
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(serviceRepository.findAllEnabledServices()).thenReturn(List.of(testService));
        when(actionDefinitionRepository.findByServiceKey("test-service"))
            .thenReturn(List.of(testAction, testReaction));

        // When
        ResponseEntity<Map<String, Object>> response = aboutController.getAbout(request);

        // Then
        @SuppressWarnings("unchecked")
        Map<String, Object> server = (Map<String, Object>) response.getBody().get("server");
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> services = (List<Map<String, Object>>) server.get("services");
        
        assertNotNull(services);
        assertEquals(1, services.size());
        
        Map<String, Object> service = services.get(0);
        assertEquals("Test Service", service.get("name"));
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> actions = (List<Map<String, Object>>) service.get("actions");
        assertEquals(1, actions.size());
        assertEquals("Test Action", actions.get(0).get("name"));
        assertEquals("Test action description", actions.get(0).get("description"));
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> reactions = (List<Map<String, Object>>) service.get("reactions");
        assertEquals(1, reactions.size());
        assertEquals("Test Reaction", reactions.get(0).get("name"));
        assertEquals("Test reaction description", reactions.get(0).get("description"));
    }

    @Test
    @DisplayName("Doit filtrer les actions non-event-capable")
    void shouldFilterNonEventCapableActions() {
        // Given
        ActionDefinition nonEventAction = new ActionDefinition();
        nonEventAction.setService(testService);
        nonEventAction.setIsEventCapable(false);
        nonEventAction.setIsExecutable(false);

        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(serviceRepository.findAllEnabledServices()).thenReturn(List.of(testService));
        when(actionDefinitionRepository.findByServiceKey("test-service"))
            .thenReturn(List.of(testAction, nonEventAction));

        // When
        ResponseEntity<Map<String, Object>> response = aboutController.getAbout(request);

        // Then
        @SuppressWarnings("unchecked")
        Map<String, Object> server = (Map<String, Object>) response.getBody().get("server");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> services = (List<Map<String, Object>>) server.get("services");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> actions = (List<Map<String, Object>>) services.get(0).get("actions");
        
        assertEquals(1, actions.size());
    }

    @Test
    @DisplayName("Doit filtrer les reactions non-executable")
    void shouldFilterNonExecutableReactions() {
        // Given
        ActionDefinition nonExecutableReaction = new ActionDefinition();
        nonExecutableReaction.setService(testService);
        nonExecutableReaction.setIsEventCapable(false);
        nonExecutableReaction.setIsExecutable(false);

        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(serviceRepository.findAllEnabledServices()).thenReturn(List.of(testService));
        when(actionDefinitionRepository.findByServiceKey("test-service"))
            .thenReturn(List.of(testReaction, nonExecutableReaction));

        // When
        ResponseEntity<Map<String, Object>> response = aboutController.getAbout(request);

        // Then
        @SuppressWarnings("unchecked")
        Map<String, Object> server = (Map<String, Object>) response.getBody().get("server");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> services = (List<Map<String, Object>>) server.get("services");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> reactions = (List<Map<String, Object>>) services.get(0).get("reactions");
        
        assertEquals(1, reactions.size());
    }

    @Test
    @DisplayName("Doit gérer plusieurs services")
    void shouldHandleMultipleServices() {
        // Given
        Service service2 = new Service();
        service2.setId(UUID.randomUUID());
        service2.setKey("service-2");
        service2.setName("Service 2");
        service2.setIsActive(true);

        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(serviceRepository.findAllEnabledServices()).thenReturn(List.of(testService, service2));
        when(actionDefinitionRepository.findByServiceKey("test-service"))
            .thenReturn(List.of(testAction));
        when(actionDefinitionRepository.findByServiceKey("service-2"))
            .thenReturn(Collections.emptyList());

        // When
        ResponseEntity<Map<String, Object>> response = aboutController.getAbout(request);

        // Then
        @SuppressWarnings("unchecked")
        Map<String, Object> server = (Map<String, Object>) response.getBody().get("server");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> services = (List<Map<String, Object>>) server.get("services");
        
        assertEquals(2, services.size());
    }

    @Test
    @DisplayName("Doit gérer l'absence de services")
    void shouldHandleNoServices() {
        // Given
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(serviceRepository.findAllEnabledServices()).thenReturn(Collections.emptyList());

        // When
        ResponseEntity<Map<String, Object>> response = aboutController.getAbout(request);

        // Then
        @SuppressWarnings("unchecked")
        Map<String, Object> server = (Map<String, Object>) response.getBody().get("server");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> services = (List<Map<String, Object>>) server.get("services");
        
        assertNotNull(services);
        assertTrue(services.isEmpty());
    }

    @Test
    @DisplayName("Doit inclure le timestamp actuel")
    void shouldIncludeCurrentTimestamp() {
        // Given
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(serviceRepository.findAllEnabledServices()).thenReturn(Collections.emptyList());
        long before = System.currentTimeMillis() / 1000;

        // When
        ResponseEntity<Map<String, Object>> response = aboutController.getAbout(request);

        // Then
        long after = System.currentTimeMillis() / 1000;
        @SuppressWarnings("unchecked")
        Map<String, Object> server = (Map<String, Object>) response.getBody().get("server");
        Long timestamp = (Long) server.get("current_time");
        
        assertNotNull(timestamp);
        assertTrue(timestamp >= before);
        assertTrue(timestamp <= after);
    }

    @Test
    @DisplayName("Doit gérer X-Forwarded-For avec espaces")
    void shouldHandleXForwardedForWithSpaces() {
        // Given
        when(request.getHeader("X-Forwarded-For")).thenReturn(" 203.0.113.1 , 198.51.100.1 ");
        when(serviceRepository.findAllEnabledServices()).thenReturn(Collections.emptyList());

        // When
        ResponseEntity<Map<String, Object>> response = aboutController.getAbout(request);

        // Then
        @SuppressWarnings("unchecked")
        Map<String, Object> client = (Map<String, Object>) response.getBody().get("client");
        assertEquals("203.0.113.1", client.get("host"));
    }

    @Test
    @DisplayName("Doit gérer X-Forwarded-For vide")
    void shouldHandleEmptyXForwardedFor() {
        // Given
        when(request.getHeader("X-Forwarded-For")).thenReturn("");
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(serviceRepository.findAllEnabledServices()).thenReturn(Collections.emptyList());

        // When
        ResponseEntity<Map<String, Object>> response = aboutController.getAbout(request);

        // Then
        @SuppressWarnings("unchecked")
        Map<String, Object> client = (Map<String, Object>) response.getBody().get("client");
        assertEquals("127.0.0.1", client.get("host"));
    }
}
