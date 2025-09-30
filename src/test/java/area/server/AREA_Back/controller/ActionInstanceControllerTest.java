package area.server.AREA_Back.controller;

import area.server.AREA_Back.dto.ActionInstanceResponse;
import area.server.AREA_Back.dto.CreateActionInstanceRequest;
import area.server.AREA_Back.entity.ActionDefinition;
import area.server.AREA_Back.entity.ActionInstance;
import area.server.AREA_Back.entity.Area;
import area.server.AREA_Back.entity.Service;
import area.server.AREA_Back.entity.ServiceAccount;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.repository.ActionDefinitionRepository;
import area.server.AREA_Back.repository.ActionInstanceRepository;
import area.server.AREA_Back.repository.AreaRepository;
import area.server.AREA_Back.repository.ServiceAccountRepository;
import area.server.AREA_Back.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ActionInstanceControllerTest {

    @Mock
    private ActionInstanceRepository actionInstanceRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AreaRepository areaRepository;

    @Mock
    private ActionDefinitionRepository actionDefinitionRepository;

    @Mock
    private ServiceAccountRepository serviceAccountRepository;

    @InjectMocks
    private ActionInstanceController actionInstanceController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private UUID testUserId;
    private UUID testAreaId;
    private UUID testActionDefinitionId;
    private UUID testActionInstanceId;
    private UUID testServiceAccountId;

    private User testUser;
    private Area testArea;
    private ActionDefinition testActionDefinition;
    private ActionInstance testActionInstance;
    private ServiceAccount testServiceAccount;
    private Service testService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(actionInstanceController).build();
        objectMapper = new ObjectMapper();

        testUserId = UUID.randomUUID();
        testAreaId = UUID.randomUUID();
        testActionDefinitionId = UUID.randomUUID();
        testActionInstanceId = UUID.randomUUID();
        testServiceAccountId = UUID.randomUUID();

        // Setup test entities
        testUser = new User();
        testUser.setId(testUserId);
        testUser.setEmail("test@example.com");
        testUser.setIsActive(true);

        testService = new Service();
        testService.setId(UUID.randomUUID());
        testService.setKey("test-service");
        testService.setName("Test Service");

        testArea = new Area();
        testArea.setId(testAreaId);
        testArea.setName("Test Area");
        testArea.setUser(testUser);

        testActionDefinition = new ActionDefinition();
        testActionDefinition.setId(testActionDefinitionId);
        testActionDefinition.setName("Test Action Definition");
        testActionDefinition.setService(testService);

        testServiceAccount = new ServiceAccount();
        testServiceAccount.setId(testServiceAccountId);
        testServiceAccount.setUser(testUser);

        testActionInstance = new ActionInstance();
        testActionInstance.setId(testActionInstanceId);
        testActionInstance.setUser(testUser);
        testActionInstance.setArea(testArea);
        testActionInstance.setActionDefinition(testActionDefinition);
        testActionInstance.setServiceAccount(testServiceAccount);
        testActionInstance.setName("Test Action Instance");
        testActionInstance.setEnabled(true);
        testActionInstance.setParams(Map.of("param1", "value1"));
        testActionInstance.setCreatedAt(LocalDateTime.now());
        testActionInstance.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    void getActionInstancesByUserShouldReturnActionInstancesWhenUserExists() throws Exception {
        // Given
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(actionInstanceRepository.findByUser(testUser)).thenReturn(Arrays.asList(testActionInstance));

        // When & Then
        mockMvc.perform(get("/api/action-instances/user/{userId}", testUserId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(testActionInstanceId.toString()))
                .andExpect(jsonPath("$[0].name").value("Test Action Instance"))
                .andExpect(jsonPath("$[0].enabled").value(true));

        verify(userRepository).findById(testUserId);
        verify(actionInstanceRepository).findByUser(testUser);
    }

    @Test
    void getActionInstancesByUserShouldReturnNotFoundWhenUserDoesNotExist() throws Exception {
        // Given
        when(userRepository.findById(testUserId)).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(get("/api/action-instances/user/{userId}", testUserId))
                .andExpect(status().isNotFound());

        verify(userRepository).findById(testUserId);
        verify(actionInstanceRepository, never()).findByUser(any());
    }

    @Test
    void getActionInstancesByAreaShouldReturnActionInstancesWhenAreaExists() throws Exception {
        // Given
        when(areaRepository.findById(testAreaId)).thenReturn(Optional.of(testArea));
        when(actionInstanceRepository.findByArea(testArea)).thenReturn(Arrays.asList(testActionInstance));

        // When & Then
        mockMvc.perform(get("/api/action-instances/area/{areaId}", testAreaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(testActionInstanceId.toString()))
                .andExpect(jsonPath("$[0].areaId").value(testAreaId.toString()));

        verify(areaRepository).findById(testAreaId);
        verify(actionInstanceRepository).findByArea(testArea);
    }

    @Test
    void getActionInstancesByAreaShouldReturnNotFoundWhenAreaDoesNotExist() throws Exception {
        // Given
        when(areaRepository.findById(testAreaId)).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(get("/api/action-instances/area/{areaId}", testAreaId))
                .andExpect(status().isNotFound());

        verify(areaRepository).findById(testAreaId);
        verify(actionInstanceRepository, never()).findByArea(any());
    }

    @Test
    void getActionInstanceByIdShouldReturnActionInstanceWhenExists() throws Exception {
        // Given
        when(actionInstanceRepository.findById(testActionInstanceId)).thenReturn(Optional.of(testActionInstance));

        // When & Then
        mockMvc.perform(get("/api/action-instances/{id}", testActionInstanceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testActionInstanceId.toString()))
                .andExpect(jsonPath("$.name").value("Test Action Instance"))
                .andExpect(jsonPath("$.enabled").value(true));

        verify(actionInstanceRepository).findById(testActionInstanceId);
    }

    @Test
    void getActionInstanceByIdShouldReturnNotFoundWhenDoesNotExist() throws Exception {
        // Given
        when(actionInstanceRepository.findById(testActionInstanceId)).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(get("/api/action-instances/{id}", testActionInstanceId))
                .andExpect(status().isNotFound());

        verify(actionInstanceRepository).findById(testActionInstanceId);
    }

    @Test
    void createActionInstanceShouldReturnCreatedWhenValidRequest() throws Exception {
        // Given
        CreateActionInstanceRequest request = new CreateActionInstanceRequest();
        request.setAreaId(testAreaId);
        request.setActionDefinitionId(testActionDefinitionId);
        request.setServiceAccountId(testServiceAccountId);
        request.setName("New Action Instance");
        request.setParams(Map.of("param1", "value1"));

        when(areaRepository.findById(testAreaId)).thenReturn(Optional.of(testArea));
        when(actionDefinitionRepository.findById(testActionDefinitionId)).thenReturn(Optional.of(testActionDefinition));
        when(serviceAccountRepository.findById(testServiceAccountId)).thenReturn(Optional.of(testServiceAccount));
        when(actionInstanceRepository.save(any(ActionInstance.class))).thenReturn(testActionInstance);

        // When & Then
        mockMvc.perform(post("/api/action-instances")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(testActionInstanceId.toString()))
                .andExpect(jsonPath("$.name").value("Test Action Instance"));

        verify(areaRepository).findById(testAreaId);
        verify(actionDefinitionRepository).findById(testActionDefinitionId);
        verify(serviceAccountRepository).findById(testServiceAccountId);
        verify(actionInstanceRepository).save(any(ActionInstance.class));
    }

    @Test
    void createActionInstanceShouldReturnCreatedWhenNoServiceAccount() throws Exception {
        // Given
        CreateActionInstanceRequest request = new CreateActionInstanceRequest();
        request.setAreaId(testAreaId);
        request.setActionDefinitionId(testActionDefinitionId);
        request.setServiceAccountId(null); // No service account
        request.setName("New Action Instance");
        request.setParams(Map.of("param1", "value1"));

        when(areaRepository.findById(testAreaId)).thenReturn(Optional.of(testArea));
        when(actionDefinitionRepository.findById(testActionDefinitionId)).thenReturn(Optional.of(testActionDefinition));
        when(actionInstanceRepository.save(any(ActionInstance.class))).thenReturn(testActionInstance);

        // When & Then
        mockMvc.perform(post("/api/action-instances")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        verify(areaRepository).findById(testAreaId);
        verify(actionDefinitionRepository).findById(testActionDefinitionId);
        verify(serviceAccountRepository, never()).findById(any());
        verify(actionInstanceRepository).save(any(ActionInstance.class));
    }

    @Test
    void createActionInstanceShouldReturnBadRequestWhenAreaNotFound() throws Exception {
        // Given
        CreateActionInstanceRequest request = new CreateActionInstanceRequest();
        request.setAreaId(testAreaId);
        request.setActionDefinitionId(testActionDefinitionId);
        request.setName("New Action Instance");

        when(areaRepository.findById(testAreaId)).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(post("/api/action-instances")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(areaRepository).findById(testAreaId);
        verify(actionDefinitionRepository, never()).findById(any());
        verify(actionInstanceRepository, never()).save(any());
    }

    @Test
    void createActionInstanceShouldReturnBadRequestWhenActionDefinitionNotFound() throws Exception {
        // Given
        CreateActionInstanceRequest request = new CreateActionInstanceRequest();
        request.setAreaId(testAreaId);
        request.setActionDefinitionId(testActionDefinitionId);
        request.setName("New Action Instance");

        when(areaRepository.findById(testAreaId)).thenReturn(Optional.of(testArea));
        when(actionDefinitionRepository.findById(testActionDefinitionId)).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(post("/api/action-instances")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(areaRepository).findById(testAreaId);
        verify(actionDefinitionRepository).findById(testActionDefinitionId);
        verify(actionInstanceRepository, never()).save(any());
    }

    @Test
    void createActionInstanceShouldReturnBadRequestWhenServiceAccountNotFound() throws Exception {
        // Given
        CreateActionInstanceRequest request = new CreateActionInstanceRequest();
        request.setAreaId(testAreaId);
        request.setActionDefinitionId(testActionDefinitionId);
        request.setServiceAccountId(testServiceAccountId);
        request.setName("New Action Instance");

        when(areaRepository.findById(testAreaId)).thenReturn(Optional.of(testArea));
        when(actionDefinitionRepository.findById(testActionDefinitionId)).thenReturn(Optional.of(testActionDefinition));
        when(serviceAccountRepository.findById(testServiceAccountId)).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(post("/api/action-instances")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(areaRepository).findById(testAreaId);
        verify(actionDefinitionRepository).findById(testActionDefinitionId);
        verify(serviceAccountRepository).findById(testServiceAccountId);
        verify(actionInstanceRepository, never()).save(any());
    }

    @Test
    void toggleActionInstanceShouldToggleEnabledWhenExists() throws Exception {
        // Given
        when(actionInstanceRepository.findById(testActionInstanceId)).thenReturn(Optional.of(testActionInstance));
        when(actionInstanceRepository.save(any(ActionInstance.class))).thenReturn(testActionInstance);

        // When & Then
        mockMvc.perform(patch("/api/action-instances/{id}/toggle", testActionInstanceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testActionInstanceId.toString()));

        verify(actionInstanceRepository).findById(testActionInstanceId);
        verify(actionInstanceRepository).save(testActionInstance);
        assertFalse(testActionInstance.getEnabled()); // Should be toggled from true to false
    }

    @Test
    void toggleActionInstanceShouldReturnNotFoundWhenDoesNotExist() throws Exception {
        // Given
        when(actionInstanceRepository.findById(testActionInstanceId)).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(patch("/api/action-instances/{id}/toggle", testActionInstanceId))
                .andExpect(status().isNotFound());

        verify(actionInstanceRepository).findById(testActionInstanceId);
        verify(actionInstanceRepository, never()).save(any());
    }

    @Test
    void deleteActionInstanceShouldReturnNoContentWhenExists() throws Exception {
        // Given
        when(actionInstanceRepository.existsById(testActionInstanceId)).thenReturn(true);

        // When & Then
        mockMvc.perform(delete("/api/action-instances/{id}", testActionInstanceId))
                .andExpect(status().isNoContent());

        verify(actionInstanceRepository).existsById(testActionInstanceId);
        verify(actionInstanceRepository).deleteById(testActionInstanceId);
    }

    @Test
    void deleteActionInstanceShouldReturnNotFoundWhenDoesNotExist() throws Exception {
        // Given
        when(actionInstanceRepository.existsById(testActionInstanceId)).thenReturn(false);

        // When & Then
        mockMvc.perform(delete("/api/action-instances/{id}", testActionInstanceId))
                .andExpect(status().isNotFound());

        verify(actionInstanceRepository).existsById(testActionInstanceId);
        verify(actionInstanceRepository, never()).deleteById(any());
    }

    // Direct method tests for better coverage

    @Test
    void getActionInstancesByUserDirectCallShouldReturnOkWhenUserExists() {
        // Given
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(actionInstanceRepository.findByUser(testUser)).thenReturn(Arrays.asList(testActionInstance));

        // When
        ResponseEntity<List<ActionInstanceResponse>> response =
            actionInstanceController.getActionInstancesByUser(testUserId);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals(testActionInstanceId, response.getBody().get(0).getId());
    }

    @Test
    void getActionInstancesByAreaDirectCallShouldReturnOkWhenAreaExists() {
        // Given
        when(areaRepository.findById(testAreaId)).thenReturn(Optional.of(testArea));
        when(actionInstanceRepository.findByArea(testArea)).thenReturn(Arrays.asList(testActionInstance));

        // When
        ResponseEntity<List<ActionInstanceResponse>> response =
            actionInstanceController.getActionInstancesByArea(testAreaId);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals(testAreaId, response.getBody().get(0).getAreaId());
    }

    @Test
    void getActionInstanceByIdDirectCallShouldReturnOkWhenExists() {
        // Given
        when(actionInstanceRepository.findById(testActionInstanceId)).thenReturn(Optional.of(testActionInstance));

        // When
        ResponseEntity<ActionInstanceResponse> response =
            actionInstanceController.getActionInstanceById(testActionInstanceId);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(testActionInstanceId, response.getBody().getId());
        assertEquals("Test Action Instance", response.getBody().getName());
    }

    @Test
    void createActionInstanceDirectCallShouldReturnCreatedWhenValid() {
        // Given
        CreateActionInstanceRequest request = new CreateActionInstanceRequest();
        request.setAreaId(testAreaId);
        request.setActionDefinitionId(testActionDefinitionId);
        request.setName("New Action Instance");

        when(areaRepository.findById(testAreaId)).thenReturn(Optional.of(testArea));
        when(actionDefinitionRepository.findById(testActionDefinitionId)).thenReturn(Optional.of(testActionDefinition));
        when(actionInstanceRepository.save(any(ActionInstance.class))).thenReturn(testActionInstance);

        // When
        ResponseEntity<ActionInstanceResponse> response =
            actionInstanceController.createActionInstance(request);

        // Then
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(testActionInstanceId, response.getBody().getId());
    }

    @Test
    void toggleActionInstanceDirectCallShouldReturnOkWhenExists() {
        // Given
        when(actionInstanceRepository.findById(testActionInstanceId)).thenReturn(Optional.of(testActionInstance));
        when(actionInstanceRepository.save(any(ActionInstance.class))).thenReturn(testActionInstance);

        // When
        ResponseEntity<ActionInstanceResponse> response =
            actionInstanceController.toggleActionInstance(testActionInstanceId);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(testActionInstanceId, response.getBody().getId());
    }

    @Test
    void deleteActionInstanceDirectCallShouldReturnNoContentWhenExists() {
        // Given
        when(actionInstanceRepository.existsById(testActionInstanceId)).thenReturn(true);

        // When
        ResponseEntity<Void> response = actionInstanceController.deleteActionInstance(testActionInstanceId);

        // Then
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(actionInstanceRepository).deleteById(testActionInstanceId);
    }
}