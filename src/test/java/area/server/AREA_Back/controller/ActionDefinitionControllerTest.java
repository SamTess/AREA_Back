package area.server.AREA_Back.controller;

import area.server.AREA_Back.dto.ActionDefinitionResponse;
import area.server.AREA_Back.entity.ActionDefinition;
import area.server.AREA_Back.entity.Service;
import area.server.AREA_Back.repository.ActionDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActionDefinitionControllerTest {

    @Mock
    private ActionDefinitionRepository actionDefinitionRepository;

    @InjectMocks
    private ActionDefinitionController actionDefinitionController;

    private ActionDefinition testActionDefinition;
    private Service testService;
    private UUID testActionId;
    private UUID testServiceId;

    @BeforeEach
    void setUp() {
        testServiceId = UUID.randomUUID();
        testActionId = UUID.randomUUID();

        testService = new Service();
        testService.setId(testServiceId);
        testService.setName("TestService");
        testService.setKey("test-service");

        Map<String, Object> inputSchema = new HashMap<>();
        inputSchema.put("field1", "string");

        Map<String, Object> outputSchema = new HashMap<>();
        outputSchema.put("result", "object");

        testActionDefinition = new ActionDefinition();
        testActionDefinition.setId(testActionId);
        testActionDefinition.setKey("test-action");
        testActionDefinition.setName("Test Action");
        testActionDefinition.setDescription("Test action description");
        testActionDefinition.setService(testService);
        testActionDefinition.setInputSchema(inputSchema);
        testActionDefinition.setOutputSchema(outputSchema);
        testActionDefinition.setVersion(1);
        testActionDefinition.setIsEventCapable(true);
        testActionDefinition.setIsExecutable(false);
    }

    @Test
    void getAllActionDefinitionsShouldReturnPagedResults() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20, Sort.by("name").ascending());
        List<ActionDefinition> actionDefinitions = Arrays.asList(testActionDefinition);
        Page<ActionDefinition> page = new PageImpl<>(actionDefinitions, pageable, 1);

        when(actionDefinitionRepository.findAll(pageable)).thenReturn(page);

        // Act
        ResponseEntity<Page<ActionDefinitionResponse>> response =
                actionDefinitionController.getAllActionDefinitions(0, 20, "name", "asc");

        // Assert
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());

        Page<ActionDefinitionResponse> responseBody = response.getBody();
        assertNotNull(responseBody);
        assertEquals(1, responseBody.getTotalElements());
        assertEquals(1, responseBody.getContent().size());

        ActionDefinitionResponse dto = responseBody.getContent().get(0);
        assertEquals(testActionDefinition.getId(), dto.getId());
        assertEquals(testActionDefinition.getName(), dto.getName());
        assertEquals(testActionDefinition.getDescription(), dto.getDescription());
        assertEquals(testService.getName(), dto.getServiceName());
        assertEquals(testService.getKey(), dto.getServiceKey());
        assertEquals(testActionDefinition.getVersion(), dto.getVersion());
        assertEquals(testActionDefinition.getIsEventCapable(), dto.getIsEventCapable());
    }

    @Test
    void getAllActionDefinitionsWithDescendingSortShouldReturnSortedResults() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20, Sort.by("name").descending());
        List<ActionDefinition> actionDefinitions = Arrays.asList(testActionDefinition);
        Page<ActionDefinition> page = new PageImpl<>(actionDefinitions, pageable, 1);

        when(actionDefinitionRepository.findAll(pageable)).thenReturn(page);

        // Act
        ResponseEntity<Page<ActionDefinitionResponse>> response =
                actionDefinitionController.getAllActionDefinitions(0, 20, "name", "desc");

        // Assert
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        verify(actionDefinitionRepository).findAll(pageable);
    }

    @Test
    void getActionDefinitionByIdWithValidIdShouldReturnActionDefinition() {
        // Arrange
        when(actionDefinitionRepository.findById(testActionId)).thenReturn(Optional.of(testActionDefinition));

        // Act
        ResponseEntity<ActionDefinitionResponse> response =
                actionDefinitionController.getActionDefinitionById(testActionId);

        // Assert
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());

        ActionDefinitionResponse dto = response.getBody();
        assertNotNull(dto);
        assertEquals(testActionDefinition.getId(), dto.getId());
        assertEquals(testActionDefinition.getName(), dto.getName());
        assertEquals(testActionDefinition.getKey(), dto.getKey());
        assertEquals(testService.getName(), dto.getServiceName());
    }

    @Test
    void getActionDefinitionByIdWithInvalidIdShouldReturnNotFound() {
        // Arrange
        UUID invalidId = UUID.randomUUID();
        when(actionDefinitionRepository.findById(invalidId)).thenReturn(Optional.empty());

        // Act
        ResponseEntity<ActionDefinitionResponse> response =
                actionDefinitionController.getActionDefinitionById(invalidId);

        // Assert
        assertNotNull(response);
        assertEquals(404, response.getStatusCode().value());
        assertNull(response.getBody());
    }

    @Test
    void getActionDefinitionsByServiceKeyShouldReturnFilteredResults() {
        // Arrange
        String serviceKey = "test-service";
        List<ActionDefinition> actionDefinitions = Arrays.asList(testActionDefinition);

        when(actionDefinitionRepository.findByServiceKey(serviceKey)).thenReturn(actionDefinitions);

        // Act
        ResponseEntity<List<ActionDefinitionResponse>> response =
                actionDefinitionController.getActionDefinitionsByServiceKey(serviceKey);

        // Assert
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());

        List<ActionDefinitionResponse> responseBody = response.getBody();
        assertNotNull(responseBody);
        assertEquals(1, responseBody.size());

        ActionDefinitionResponse dto = responseBody.get(0);
        assertEquals(testActionDefinition.getId(), dto.getId());
        assertEquals(testService.getKey(), dto.getServiceKey());
    }

    @Test
    void getEventCapableActionsShouldReturnOnlyEventCapableActions() {
        // Arrange
        List<ActionDefinition> eventCapableActions = Arrays.asList(testActionDefinition);

        when(actionDefinitionRepository.findEventCapableActions()).thenReturn(eventCapableActions);

        // Act
        ResponseEntity<List<ActionDefinitionResponse>> response =
                actionDefinitionController.getEventCapableActions();

        // Assert
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());

        List<ActionDefinitionResponse> responseBody = response.getBody();
        assertNotNull(responseBody);
        assertEquals(1, responseBody.size());

        ActionDefinitionResponse dto = responseBody.get(0);
        assertTrue(dto.getIsEventCapable());
    }

    @Test
    void getExecutableActionsShouldReturnOnlyExecutableActions() {
        // Arrange
        ActionDefinition executableAction = new ActionDefinition();
        executableAction.setId(UUID.randomUUID());
        executableAction.setService(testService);
        executableAction.setIsExecutable(true);
        executableAction.setIsEventCapable(false);
        executableAction.setName("Executable Action");
        executableAction.setKey("executable-action");
        executableAction.setVersion(1);

        List<ActionDefinition> executableActions = Arrays.asList(executableAction);

        when(actionDefinitionRepository.findExecutableActions()).thenReturn(executableActions);

        // Act
        ResponseEntity<List<ActionDefinitionResponse>> response =
                actionDefinitionController.getExecutableActions();

        // Assert
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());

        List<ActionDefinitionResponse> responseBody = response.getBody();
        assertNotNull(responseBody);
        assertEquals(1, responseBody.size());

        ActionDefinitionResponse dto = responseBody.get(0);
        assertTrue(dto.getIsExecutable());
    }

    @Test
    void getAllActionDefinitionsWithLargePageSizeShouldHandleCorrectly() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 100, Sort.by("name").ascending());
        List<ActionDefinition> actionDefinitions = Arrays.asList(testActionDefinition);
        Page<ActionDefinition> page = new PageImpl<>(actionDefinitions, pageable, 1);

        when(actionDefinitionRepository.findAll(pageable)).thenReturn(page);

        // Act
        ResponseEntity<Page<ActionDefinitionResponse>> response =
                actionDefinitionController.getAllActionDefinitions(0, 100, "name", "asc");

        // Assert
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        verify(actionDefinitionRepository).findAll(pageable);
    }

    @Test
    void getAllActionDefinitionsWithHighPageNumberShouldReturnEmptyPage() {
        // Arrange
        Pageable pageable = PageRequest.of(10, 20, Sort.by("name").ascending());
        Page<ActionDefinition> emptyPage = new PageImpl<>(Arrays.asList(), pageable, 0);

        when(actionDefinitionRepository.findAll(pageable)).thenReturn(emptyPage);

        // Act
        ResponseEntity<Page<ActionDefinitionResponse>> response =
                actionDefinitionController.getAllActionDefinitions(10, 20, "name", "asc");

        // Assert
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());

        Page<ActionDefinitionResponse> responseBody = response.getBody();
        assertNotNull(responseBody);
        assertEquals(0, responseBody.getTotalElements());
    }

    @Test
    void convertToResponseShouldMapAllFields() {
        // This tests the private convertToResponse method indirectly through getAllActionDefinitions

        // Arrange
        Pageable pageable = PageRequest.of(0, 20, Sort.by("name").ascending());
        List<ActionDefinition> actionDefinitions = Arrays.asList(testActionDefinition);
        Page<ActionDefinition> page = new PageImpl<>(actionDefinitions, pageable, 1);

        when(actionDefinitionRepository.findAll(pageable)).thenReturn(page);

        // Act
        ResponseEntity<Page<ActionDefinitionResponse>> response =
                actionDefinitionController.getAllActionDefinitions(0, 20, "name", "asc");

        // Assert
        ActionDefinitionResponse dto = response.getBody().getContent().get(0);

        assertEquals(testActionDefinition.getId(), dto.getId());
        assertEquals(testService.getId(), dto.getServiceId());
        assertEquals(testService.getKey(), dto.getServiceKey());
        assertEquals(testService.getName(), dto.getServiceName());
        assertEquals(testActionDefinition.getKey(), dto.getKey());
        assertEquals(testActionDefinition.getName(), dto.getName());
        assertEquals(testActionDefinition.getDescription(), dto.getDescription());
        assertEquals(testActionDefinition.getInputSchema(), dto.getInputSchema());
        assertEquals(testActionDefinition.getOutputSchema(), dto.getOutputSchema());
        assertEquals(testActionDefinition.getIsEventCapable(), dto.getIsEventCapable());
        assertEquals(testActionDefinition.getIsExecutable(), dto.getIsExecutable());
        assertEquals(testActionDefinition.getVersion(), dto.getVersion());
    }
}
