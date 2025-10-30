package area.server.AREA_Back.service.Area;

import area.server.AREA_Back.dto.AreaActionRequest;
import area.server.AREA_Back.dto.AreaReactionRequest;
import area.server.AREA_Back.dto.AreaResponse;
import area.server.AREA_Back.dto.CreateAreaWithActionsRequest;
import area.server.AREA_Back.dto.CreateAreaWithActionsAndLinksRequest;
import area.server.AREA_Back.entity.ActionDefinition;
import area.server.AREA_Back.entity.ActionInstance;
import area.server.AREA_Back.entity.ActivationMode;
import area.server.AREA_Back.entity.Area;
import area.server.AREA_Back.entity.Service;
import area.server.AREA_Back.entity.ServiceAccount;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.entity.enums.ActivationModeType;
import area.server.AREA_Back.entity.enums.DedupStrategy;
import area.server.AREA_Back.repository.ActionDefinitionRepository;
import area.server.AREA_Back.repository.ActionInstanceRepository;
import area.server.AREA_Back.repository.ActivationModeRepository;
import area.server.AREA_Back.repository.AreaRepository;
import area.server.AREA_Back.repository.ServiceAccountRepository;
import area.server.AREA_Back.repository.UserRepository;
import area.server.AREA_Back.service.JsonSchemaValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AreaServiceTest {

    @Mock
    private AreaRepository areaRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ActionDefinitionRepository actionDefinitionRepository;

    @Mock
    private ActionInstanceRepository actionInstanceRepository;

    @Mock
    private ActivationModeRepository activationModeRepository;

    @Mock
    private ServiceAccountRepository serviceAccountRepository;

    @Mock
    private JsonSchemaValidationService jsonSchemaValidationService;

    @Mock
    private ActionLinkService actionLinkService;

    @Mock
    private ExecutionTriggerService executionTriggerService;

    @InjectMocks
    private AreaService areaService;

    private User testUser;
    private Area testArea;
    private ActionDefinition eventActionDef;
    private ActionDefinition executableActionDef;
    private ActionDefinition mixedActionDef;
    private Service testService;
    private ServiceAccount testServiceAccount;
    private UUID testUserId;
    private UUID testAreaId;
    private UUID eventActionDefId;
    private UUID executableActionDefId;
    private UUID serviceAccountId;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testAreaId = UUID.randomUUID();
        eventActionDefId = UUID.randomUUID();
        executableActionDefId = UUID.randomUUID();
        serviceAccountId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();

        testUser = new User();
        testUser.setId(testUserId);
        testUser.setEmail("test@example.com");
        testUser.setUsername("testuser");

        testService = new Service();
        testService.setId(serviceId);
        testService.setName("TestService");

        testServiceAccount = new ServiceAccount();
        testServiceAccount.setId(serviceAccountId);
        testServiceAccount.setService(testService);
        testServiceAccount.setUser(testUser);

        eventActionDef = new ActionDefinition();
        eventActionDef.setId(eventActionDefId);
        eventActionDef.setName("Test Event Action");
        eventActionDef.setKey("test.event");
        eventActionDef.setIsEventCapable(true);
        eventActionDef.setIsExecutable(false);
        eventActionDef.setService(testService);
        eventActionDef.setInputSchema(Map.of());

        executableActionDef = new ActionDefinition();
        executableActionDef.setId(executableActionDefId);
        executableActionDef.setName("Test Executable Action");
        executableActionDef.setKey("test.executable");
        executableActionDef.setIsEventCapable(false);
        executableActionDef.setIsExecutable(true);
        executableActionDef.setService(testService);
        executableActionDef.setInputSchema(Map.of());

        mixedActionDef = new ActionDefinition();
        mixedActionDef.setId(UUID.randomUUID());
        mixedActionDef.setName("Test Mixed Action");
        mixedActionDef.setKey("test.mixed");
        mixedActionDef.setIsEventCapable(true);
        mixedActionDef.setIsExecutable(true);
        mixedActionDef.setService(testService);
        mixedActionDef.setInputSchema(Map.of());

        testArea = new Area();
        testArea.setId(testAreaId);
        testArea.setName("Test Area");
        testArea.setDescription("Test Description");
        testArea.setUser(testUser);
        testArea.setEnabled(true);
        testArea.setCreatedAt(LocalDateTime.now());
        testArea.setUpdatedAt(LocalDateTime.now());
        testArea.setActions(new ArrayList<>());
        testArea.setReactions(new ArrayList<>());
    }

    @Test
    void testCreateAreaWithActions_Success() {
        // Arrange
        CreateAreaWithActionsRequest request = new CreateAreaWithActionsRequest();
        request.setUserId(testUserId);
        request.setName("Test Area");
        request.setDescription("Test Description");

        AreaActionRequest actionRequest = new AreaActionRequest();
        actionRequest.setActionDefinitionId(eventActionDefId);
        actionRequest.setName("Test Action");
        actionRequest.setDescription("Action Description");
        actionRequest.setParameters(Map.of("key", "value"));
        actionRequest.setActivationConfig(Map.of("type", "webhook"));

        AreaReactionRequest reactionRequest = new AreaReactionRequest();
        reactionRequest.setActionDefinitionId(executableActionDefId);
        reactionRequest.setName("Test Reaction");
        reactionRequest.setDescription("Reaction Description");
        reactionRequest.setParameters(Map.of("key", "value"));
        reactionRequest.setOrder(0);

        request.setActions(List.of(actionRequest));
        request.setReactions(List.of(reactionRequest));

        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(actionDefinitionRepository.findById(eventActionDefId)).thenReturn(Optional.of(eventActionDef));
        when(actionDefinitionRepository.findById(executableActionDefId)).thenReturn(Optional.of(executableActionDef));
        when(areaRepository.save(any(Area.class))).thenReturn(testArea);
        when(actionInstanceRepository.save(any(ActionInstance.class))).thenAnswer(i -> {
            ActionInstance instance = i.getArgument(0);
            instance.setId(UUID.randomUUID());
            return instance;
        });
        when(activationModeRepository.save(any(ActivationMode.class))).thenAnswer(i -> i.getArgument(0));
        when(actionLinkService.getActionLinksByArea(any(UUID.class))).thenReturn(new ArrayList<>());

        // Act
        AreaResponse response = areaService.createAreaWithActions(request);

        // Assert
        assertNotNull(response);
        assertEquals("Test Area", response.getName());
        assertEquals(testUserId, response.getUserId());
        verify(areaRepository, times(2)).save(any(Area.class)); // Saved twice: initially and after adding instance IDs
        verify(actionInstanceRepository, times(2)).save(any(ActionInstance.class)); // 1 action + 1 reaction
    }

    @Test
    void testCreateAreaWithActions_UserNotFound() {
        // Arrange
        CreateAreaWithActionsRequest request = new CreateAreaWithActionsRequest();
        request.setUserId(testUserId);
        request.setName("Test Area");
        request.setActions(new ArrayList<>());
        request.setReactions(new ArrayList<>());

        when(userRepository.findById(testUserId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> areaService.createAreaWithActions(request));
    }

    @Test
    void testValidateActionsAndReactions_ActionNotEventCapable() {
        // Arrange
        AreaActionRequest actionRequest = new AreaActionRequest();
        actionRequest.setActionDefinitionId(executableActionDefId);
        actionRequest.setName("Test Action");

        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(actionDefinitionRepository.findById(executableActionDefId)).thenReturn(Optional.of(executableActionDef));

        // Act & Assert
        assertThrows(IllegalArgumentException.class, 
            () -> areaService.createAreaWithActions(createMinimalRequest(List.of(actionRequest), new ArrayList<>())));
    }

    @Test
    void testValidateActionsAndReactions_ReactionNotExecutable() {
        // Arrange
        AreaReactionRequest reactionRequest = new AreaReactionRequest();
        reactionRequest.setActionDefinitionId(eventActionDefId);
        reactionRequest.setName("Test Reaction");

        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(actionDefinitionRepository.findById(eventActionDefId)).thenReturn(Optional.of(eventActionDef));

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
            () -> areaService.createAreaWithActions(createMinimalRequest(new ArrayList<>(), List.of(reactionRequest))));
    }

    @Test
    void testValidateServiceAccount_Success() {
        // Arrange
        AreaActionRequest actionRequest = new AreaActionRequest();
        actionRequest.setActionDefinitionId(eventActionDefId);
        actionRequest.setName("Test Action");
        actionRequest.setServiceAccountId(serviceAccountId);
        actionRequest.setParameters(Map.of());

        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(actionDefinitionRepository.findById(eventActionDefId)).thenReturn(Optional.of(eventActionDef));
        when(serviceAccountRepository.findById(serviceAccountId)).thenReturn(Optional.of(testServiceAccount));
        when(areaRepository.save(any(Area.class))).thenReturn(testArea);
        when(actionInstanceRepository.save(any(ActionInstance.class))).thenAnswer(i -> {
            ActionInstance instance = i.getArgument(0);
            instance.setId(UUID.randomUUID());
            return instance;
        });
        when(actionLinkService.getActionLinksByArea(any(UUID.class))).thenReturn(new ArrayList<>());

        CreateAreaWithActionsRequest request = createMinimalRequest(List.of(actionRequest), new ArrayList<>());

        // Act
        AreaResponse response = areaService.createAreaWithActions(request);

        // Assert
        assertNotNull(response);
        verify(serviceAccountRepository, atLeast(1)).findById(serviceAccountId);
    }

    @Test
    void testValidateServiceAccount_ServiceMismatch() {
        // Arrange
        Service wrongService = new Service();
        wrongService.setId(UUID.randomUUID());
        
        ServiceAccount wrongServiceAccount = new ServiceAccount();
        wrongServiceAccount.setId(serviceAccountId);
        wrongServiceAccount.setService(wrongService);

        AreaActionRequest actionRequest = new AreaActionRequest();
        actionRequest.setActionDefinitionId(eventActionDefId);
        actionRequest.setName("Test Action");
        actionRequest.setServiceAccountId(serviceAccountId);

        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(actionDefinitionRepository.findById(eventActionDefId)).thenReturn(Optional.of(eventActionDef));
        when(serviceAccountRepository.findById(serviceAccountId)).thenReturn(Optional.of(wrongServiceAccount));

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
            () -> areaService.createAreaWithActions(createMinimalRequest(List.of(actionRequest), new ArrayList<>())));
    }

    @Test
    void testConvertActionsToJsonb() {
        // Arrange
        AreaActionRequest actionRequest = new AreaActionRequest();
        actionRequest.setActionDefinitionId(eventActionDefId);
        actionRequest.setName("Test Action");
        actionRequest.setDescription("Action Description");
        actionRequest.setParameters(Map.of("key", "value"));
        actionRequest.setActivationConfig(Map.of("type", "webhook"));
        actionRequest.setServiceAccountId(serviceAccountId);

        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(actionDefinitionRepository.findById(eventActionDefId)).thenReturn(Optional.of(eventActionDef));
        when(serviceAccountRepository.findById(serviceAccountId)).thenReturn(Optional.of(testServiceAccount));
        when(areaRepository.save(any(Area.class))).thenAnswer(i -> {
            Area savedArea = i.getArgument(0);
            savedArea.setId(testAreaId);
            return savedArea;
        });
        when(actionInstanceRepository.save(any(ActionInstance.class))).thenAnswer(i -> {
            ActionInstance instance = i.getArgument(0);
            instance.setId(UUID.randomUUID());
            return instance;
        });
        when(activationModeRepository.save(any(ActivationMode.class))).thenAnswer(i -> i.getArgument(0));
        when(actionLinkService.getActionLinksByArea(any(UUID.class))).thenReturn(new ArrayList<>());

        CreateAreaWithActionsRequest request = createMinimalRequest(List.of(actionRequest), new ArrayList<>());

        // Act
        AreaResponse response = areaService.createAreaWithActions(request);

        // Assert
        assertNotNull(response);
        assertNotNull(response.getActions());
        assertFalse(response.getActions().isEmpty());
    }

    @Test
    void testConvertActionsToJsonb_NullParameters() {
        // Arrange
        AreaActionRequest actionRequest = new AreaActionRequest();
        actionRequest.setActionDefinitionId(eventActionDefId);
        actionRequest.setName("Test Action");
        actionRequest.setParameters(null);
        actionRequest.setActivationConfig(null);

        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(actionDefinitionRepository.findById(eventActionDefId)).thenReturn(Optional.of(eventActionDef));
        when(areaRepository.save(any(Area.class))).thenReturn(testArea);
        when(actionInstanceRepository.save(any(ActionInstance.class))).thenAnswer(i -> {
            ActionInstance instance = i.getArgument(0);
            instance.setId(UUID.randomUUID());
            return instance;
        });
        when(actionLinkService.getActionLinksByArea(any(UUID.class))).thenReturn(new ArrayList<>());

        CreateAreaWithActionsRequest request = createMinimalRequest(List.of(actionRequest), new ArrayList<>());

        // Act
        AreaResponse response = areaService.createAreaWithActions(request);

        // Assert
        assertNotNull(response);
        assertNotNull(response.getActions());
    }

    @Test
    void testConvertReactionsToJsonb() {
        // Arrange
        AreaReactionRequest reactionRequest = new AreaReactionRequest();
        reactionRequest.setActionDefinitionId(executableActionDefId);
        reactionRequest.setName("Test Reaction");
        reactionRequest.setDescription("Reaction Description");
        reactionRequest.setParameters(Map.of("key", "value"));
        reactionRequest.setMapping(Map.of("field1", "value1"));
        reactionRequest.setCondition(Map.of("condition", "true"));
        reactionRequest.setOrder(1);
        reactionRequest.setServiceAccountId(serviceAccountId);

        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(actionDefinitionRepository.findById(executableActionDefId)).thenReturn(Optional.of(executableActionDef));
        when(serviceAccountRepository.findById(serviceAccountId)).thenReturn(Optional.of(testServiceAccount));
        when(areaRepository.save(any(Area.class))).thenAnswer(i -> {
            Area savedArea = i.getArgument(0);
            savedArea.setId(testAreaId);
            return savedArea;
        });
        when(actionInstanceRepository.save(any(ActionInstance.class))).thenAnswer(i -> {
            ActionInstance instance = i.getArgument(0);
            instance.setId(UUID.randomUUID());
            return instance;
        });
        when(activationModeRepository.save(any(ActivationMode.class))).thenAnswer(i -> i.getArgument(0));
        when(actionLinkService.getActionLinksByArea(any(UUID.class))).thenReturn(new ArrayList<>());

        CreateAreaWithActionsRequest request = createMinimalRequest(new ArrayList<>(), List.of(reactionRequest));

        // Act
        AreaResponse response = areaService.createAreaWithActions(request);

        // Assert
        assertNotNull(response);
        assertNotNull(response.getReactions());
        assertFalse(response.getReactions().isEmpty());
    }

    @Test
    void testConvertReactionsToJsonb_NullFields() {
        // Arrange
        AreaReactionRequest reactionRequest = new AreaReactionRequest();
        reactionRequest.setActionDefinitionId(executableActionDefId);
        reactionRequest.setName("Test Reaction");
        reactionRequest.setParameters(null);
        reactionRequest.setMapping(null);
        reactionRequest.setCondition(null);

        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(actionDefinitionRepository.findById(executableActionDefId)).thenReturn(Optional.of(executableActionDef));
        when(areaRepository.save(any(Area.class))).thenReturn(testArea);
        when(actionInstanceRepository.save(any(ActionInstance.class))).thenAnswer(i -> {
            ActionInstance instance = i.getArgument(0);
            instance.setId(UUID.randomUUID());
            return instance;
        });
        when(activationModeRepository.save(any(ActivationMode.class))).thenAnswer(i -> i.getArgument(0));
        when(actionLinkService.getActionLinksByArea(any(UUID.class))).thenReturn(new ArrayList<>());

        CreateAreaWithActionsRequest request = createMinimalRequest(new ArrayList<>(), List.of(reactionRequest));

        // Act
        AreaResponse response = areaService.createAreaWithActions(request);

        // Assert
        assertNotNull(response);
        assertNotNull(response.getReactions());
    }

    @Test
    void testCreateActionInstances_WithActivationConfig() {
        // Arrange
        AreaActionRequest actionRequest = new AreaActionRequest();
        actionRequest.setActionDefinitionId(eventActionDefId);
        actionRequest.setName("Test Action");
        actionRequest.setParameters(Map.of("key", "value"));
        actionRequest.setActivationConfig(Map.of("type", "webhook"));

        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(actionDefinitionRepository.findById(eventActionDefId)).thenReturn(Optional.of(eventActionDef));
        when(areaRepository.save(any(Area.class))).thenReturn(testArea);
        when(actionInstanceRepository.save(any(ActionInstance.class))).thenAnswer(i -> {
            ActionInstance instance = i.getArgument(0);
            instance.setId(UUID.randomUUID());
            return instance;
        });
        when(activationModeRepository.save(any(ActivationMode.class))).thenAnswer(i -> i.getArgument(0));
        when(actionLinkService.getActionLinksByArea(any(UUID.class))).thenReturn(new ArrayList<>());

        CreateAreaWithActionsRequest request = createMinimalRequest(List.of(actionRequest), new ArrayList<>());

        // Act
        areaService.createAreaWithActions(request);

        // Assert
        verify(activationModeRepository, atLeastOnce()).save(any(ActivationMode.class));
    }

    @Test
    void testCreateActionInstances_WithoutActivationConfig() {
        // Arrange
        AreaActionRequest actionRequest = new AreaActionRequest();
        actionRequest.setActionDefinitionId(eventActionDefId);
        actionRequest.setName("Test Action");
        actionRequest.setParameters(Map.of());
        actionRequest.setActivationConfig(null);

        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(actionDefinitionRepository.findById(eventActionDefId)).thenReturn(Optional.of(eventActionDef));
        when(areaRepository.save(any(Area.class))).thenReturn(testArea);
        when(actionInstanceRepository.save(any(ActionInstance.class))).thenAnswer(i -> {
            ActionInstance instance = i.getArgument(0);
            instance.setId(UUID.randomUUID());
            return instance;
        });
        when(actionLinkService.getActionLinksByArea(any(UUID.class))).thenReturn(new ArrayList<>());

        CreateAreaWithActionsRequest request = createMinimalRequest(List.of(actionRequest), new ArrayList<>());

        // Act
        areaService.createAreaWithActions(request);

        // Assert
        verify(actionInstanceRepository, atLeast(1)).save(any(ActionInstance.class));
    }

    @Test
    void testCreateActionInstances_ReactionWithDefaultChain() {
        // Arrange
        AreaReactionRequest reactionRequest = new AreaReactionRequest();
        reactionRequest.setActionDefinitionId(executableActionDefId);
        reactionRequest.setName("Test Reaction");
        reactionRequest.setParameters(Map.of());
        reactionRequest.setActivationConfig(null);

        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(actionDefinitionRepository.findById(executableActionDefId)).thenReturn(Optional.of(executableActionDef));
        when(areaRepository.save(any(Area.class))).thenReturn(testArea);
        when(actionInstanceRepository.save(any(ActionInstance.class))).thenAnswer(i -> {
            ActionInstance instance = i.getArgument(0);
            instance.setId(UUID.randomUUID());
            return instance;
        });
        when(activationModeRepository.save(any(ActivationMode.class))).thenAnswer(i -> i.getArgument(0));
        when(actionLinkService.getActionLinksByArea(any(UUID.class))).thenReturn(new ArrayList<>());

        CreateAreaWithActionsRequest request = createMinimalRequest(new ArrayList<>(), List.of(reactionRequest));

        // Act
        areaService.createAreaWithActions(request);

        // Assert
        ArgumentCaptor<ActivationMode> captor = ArgumentCaptor.forClass(ActivationMode.class);
        verify(activationModeRepository, atLeastOnce()).save(captor.capture());
        
        ActivationMode savedMode = captor.getValue();
        assertEquals(ActivationModeType.CHAIN, savedMode.getType());
        assertEquals(DedupStrategy.NONE, savedMode.getDedup());
    }

    @Test
    void testCreateActivationModes_Webhook() {
        // Arrange
        AreaActionRequest actionRequest = new AreaActionRequest();
        actionRequest.setActionDefinitionId(eventActionDefId);
        actionRequest.setName("Test Action");
        actionRequest.setActivationConfig(Map.of("type", "webhook", "max_concurrency", 10));

        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(actionDefinitionRepository.findById(eventActionDefId)).thenReturn(Optional.of(eventActionDef));
        when(areaRepository.save(any(Area.class))).thenReturn(testArea);
        when(actionInstanceRepository.save(any(ActionInstance.class))).thenAnswer(i -> {
            ActionInstance instance = i.getArgument(0);
            instance.setId(UUID.randomUUID());
            return instance;
        });
        when(activationModeRepository.save(any(ActivationMode.class))).thenAnswer(i -> i.getArgument(0));
        when(actionLinkService.getActionLinksByArea(any(UUID.class))).thenReturn(new ArrayList<>());

        CreateAreaWithActionsRequest request = createMinimalRequest(List.of(actionRequest), new ArrayList<>());

        // Act
        areaService.createAreaWithActions(request);

        // Assert
        ArgumentCaptor<ActivationMode> captor = ArgumentCaptor.forClass(ActivationMode.class);
        verify(activationModeRepository, atLeastOnce()).save(captor.capture());
        
        ActivationMode savedMode = captor.getValue();
        assertEquals(ActivationModeType.WEBHOOK, savedMode.getType());
        assertEquals(10, savedMode.getMaxConcurrency());
    }

    @Test
    void testCreateActivationModes_Cron() {
        // Arrange
        AreaReactionRequest reactionRequest = new AreaReactionRequest();
        reactionRequest.setActionDefinitionId(executableActionDefId);
        reactionRequest.setName("Test Reaction");
        reactionRequest.setActivationConfig(Map.of("type", "cron", "cron_expression", "0 0 * * *"));

        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(actionDefinitionRepository.findById(executableActionDefId)).thenReturn(Optional.of(executableActionDef));
        when(areaRepository.save(any(Area.class))).thenReturn(testArea);
        when(actionInstanceRepository.save(any(ActionInstance.class))).thenAnswer(i -> {
            ActionInstance instance = i.getArgument(0);
            instance.setId(UUID.randomUUID());
            return instance;
        });
        when(activationModeRepository.save(any(ActivationMode.class))).thenAnswer(i -> i.getArgument(0));
        when(actionLinkService.getActionLinksByArea(any(UUID.class))).thenReturn(new ArrayList<>());

        CreateAreaWithActionsRequest request = createMinimalRequest(new ArrayList<>(), List.of(reactionRequest));

        // Act
        areaService.createAreaWithActions(request);

        // Assert
        ArgumentCaptor<ActivationMode> captor = ArgumentCaptor.forClass(ActivationMode.class);
        verify(activationModeRepository, atLeastOnce()).save(captor.capture());
        
        ActivationMode savedMode = captor.getValue();
        assertEquals(ActivationModeType.CRON, savedMode.getType());
        assertTrue(savedMode.getConfig().containsKey("cron_expression"));
    }

    @Test
    void testCreateActivationModes_CronMissingExpression() {
        // Arrange
        AreaReactionRequest reactionRequest = new AreaReactionRequest();
        reactionRequest.setActionDefinitionId(executableActionDefId);
        reactionRequest.setName("Test Reaction");
        reactionRequest.setActivationConfig(Map.of("type", "cron"));

        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(actionDefinitionRepository.findById(executableActionDefId)).thenReturn(Optional.of(executableActionDef));
        when(areaRepository.save(any(Area.class))).thenReturn(testArea);
        when(actionInstanceRepository.save(any(ActionInstance.class))).thenAnswer(i -> {
            ActionInstance instance = i.getArgument(0);
            instance.setId(UUID.randomUUID());
            return instance;
        });

        CreateAreaWithActionsRequest request = createMinimalRequest(new ArrayList<>(), List.of(reactionRequest));

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> areaService.createAreaWithActions(request));
    }

    @Test
    void testCreateActivationModes_Poll() {
        // Arrange
        AreaActionRequest actionRequest = new AreaActionRequest();
        actionRequest.setActionDefinitionId(eventActionDefId);
        actionRequest.setName("Test Action");
        actionRequest.setActivationConfig(Map.of("type", "poll", "interval_seconds", 60));

        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(actionDefinitionRepository.findById(eventActionDefId)).thenReturn(Optional.of(eventActionDef));
        when(areaRepository.save(any(Area.class))).thenReturn(testArea);
        when(actionInstanceRepository.save(any(ActionInstance.class))).thenAnswer(i -> {
            ActionInstance instance = i.getArgument(0);
            instance.setId(UUID.randomUUID());
            return instance;
        });
        when(activationModeRepository.save(any(ActivationMode.class))).thenAnswer(i -> i.getArgument(0));
        when(actionLinkService.getActionLinksByArea(any(UUID.class))).thenReturn(new ArrayList<>());

        CreateAreaWithActionsRequest request = createMinimalRequest(List.of(actionRequest), new ArrayList<>());

        // Act
        areaService.createAreaWithActions(request);

        // Assert
        ArgumentCaptor<ActivationMode> captor = ArgumentCaptor.forClass(ActivationMode.class);
        verify(activationModeRepository, atLeastOnce()).save(captor.capture());
        
        ActivationMode savedMode = captor.getValue();
        assertEquals(ActivationModeType.POLL, savedMode.getType());
        assertEquals(60, savedMode.getConfig().get("interval_seconds"));
    }

    @Test
    void testCreateActivationModes_PollWithDefaultInterval() {
        // Arrange
        AreaActionRequest actionRequest = new AreaActionRequest();
        actionRequest.setActionDefinitionId(eventActionDefId);
        actionRequest.setName("Test Action");
        actionRequest.setActivationConfig(Map.of("type", "poll"));

        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(actionDefinitionRepository.findById(eventActionDefId)).thenReturn(Optional.of(eventActionDef));
        when(areaRepository.save(any(Area.class))).thenReturn(testArea);
        when(actionInstanceRepository.save(any(ActionInstance.class))).thenAnswer(i -> {
            ActionInstance instance = i.getArgument(0);
            instance.setId(UUID.randomUUID());
            return instance;
        });
        when(activationModeRepository.save(any(ActivationMode.class))).thenAnswer(i -> i.getArgument(0));
        when(actionLinkService.getActionLinksByArea(any(UUID.class))).thenReturn(new ArrayList<>());

        CreateAreaWithActionsRequest request = createMinimalRequest(List.of(actionRequest), new ArrayList<>());

        // Act
        areaService.createAreaWithActions(request);

        // Assert
        ArgumentCaptor<ActivationMode> captor = ArgumentCaptor.forClass(ActivationMode.class);
        verify(activationModeRepository, atLeastOnce()).save(captor.capture());
        
        ActivationMode savedMode = captor.getValue();
        assertEquals(ActivationModeType.POLL, savedMode.getType());
        assertEquals(300, savedMode.getConfig().get("interval_seconds"));
    }

    @Test
    void testValidateActivationModeCompatibility_EventWithCron() {
        // Arrange
        AreaActionRequest actionRequest = new AreaActionRequest();
        actionRequest.setActionDefinitionId(eventActionDefId);
        actionRequest.setName("Test Action");
        actionRequest.setActivationConfig(Map.of("type", "cron", "cron_expression", "0 0 * * *"));

        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(actionDefinitionRepository.findById(eventActionDefId)).thenReturn(Optional.of(eventActionDef));
        when(areaRepository.save(any(Area.class))).thenReturn(testArea);
        when(actionInstanceRepository.save(any(ActionInstance.class))).thenAnswer(i -> {
            ActionInstance instance = i.getArgument(0);
            instance.setId(UUID.randomUUID());
            return instance;
        });

        CreateAreaWithActionsRequest request = createMinimalRequest(List.of(actionRequest), new ArrayList<>());

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> areaService.createAreaWithActions(request));
    }

    @Test
    void testValidateActivationModeCompatibility_EventWithChain() {
        // Arrange
        AreaActionRequest actionRequest = new AreaActionRequest();
        actionRequest.setActionDefinitionId(eventActionDefId);
        actionRequest.setName("Test Action");
        actionRequest.setActivationConfig(Map.of("type", "chain"));

        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(actionDefinitionRepository.findById(eventActionDefId)).thenReturn(Optional.of(eventActionDef));
        when(areaRepository.save(any(Area.class))).thenReturn(testArea);
        when(actionInstanceRepository.save(any(ActionInstance.class))).thenAnswer(i -> {
            ActionInstance instance = i.getArgument(0);
            instance.setId(UUID.randomUUID());
            return instance;
        });

        CreateAreaWithActionsRequest request = createMinimalRequest(List.of(actionRequest), new ArrayList<>());

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> areaService.createAreaWithActions(request));
    }

    @Test
    void testValidateActivationModeCompatibility_ReactionWithWebhook() {
        // Arrange
        AreaReactionRequest reactionRequest = new AreaReactionRequest();
        reactionRequest.setActionDefinitionId(executableActionDefId);
        reactionRequest.setName("Test Reaction");
        reactionRequest.setActivationConfig(Map.of("type", "webhook"));

        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(actionDefinitionRepository.findById(executableActionDefId)).thenReturn(Optional.of(executableActionDef));
        when(areaRepository.save(any(Area.class))).thenReturn(testArea);
        when(actionInstanceRepository.save(any(ActionInstance.class))).thenAnswer(i -> {
            ActionInstance instance = i.getArgument(0);
            instance.setId(UUID.randomUUID());
            return instance;
        });

        CreateAreaWithActionsRequest request = createMinimalRequest(new ArrayList<>(), List.of(reactionRequest));

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> areaService.createAreaWithActions(request));
    }

    @Test
    void testValidateActivationModeCompatibility_ReactionWithPoll() {
        // Arrange
        AreaReactionRequest reactionRequest = new AreaReactionRequest();
        reactionRequest.setActionDefinitionId(executableActionDefId);
        reactionRequest.setName("Test Reaction");
        reactionRequest.setActivationConfig(Map.of("type", "poll", "interval_seconds", 60));

        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(actionDefinitionRepository.findById(executableActionDefId)).thenReturn(Optional.of(executableActionDef));
        when(areaRepository.save(any(Area.class))).thenReturn(testArea);
        when(actionInstanceRepository.save(any(ActionInstance.class))).thenAnswer(i -> {
            ActionInstance instance = i.getArgument(0);
            instance.setId(UUID.randomUUID());
            return instance;
        });

        CreateAreaWithActionsRequest request = createMinimalRequest(new ArrayList<>(), List.of(reactionRequest));

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> areaService.createAreaWithActions(request));
    }

    @Test
    void testValidateActivationModeCompatibility_MixedAction() {
        // Arrange
        AreaActionRequest actionRequest = new AreaActionRequest();
        actionRequest.setActionDefinitionId(mixedActionDef.getId());
        actionRequest.setName("Test Mixed Action");
        actionRequest.setActivationConfig(Map.of("type", "webhook"));

        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(actionDefinitionRepository.findById(mixedActionDef.getId())).thenReturn(Optional.of(mixedActionDef));
        when(areaRepository.save(any(Area.class))).thenReturn(testArea);
        when(actionInstanceRepository.save(any(ActionInstance.class))).thenAnswer(i -> {
            ActionInstance instance = i.getArgument(0);
            instance.setId(UUID.randomUUID());
            return instance;
        });
        when(activationModeRepository.save(any(ActivationMode.class))).thenAnswer(i -> i.getArgument(0));
        when(actionLinkService.getActionLinksByArea(any(UUID.class))).thenReturn(new ArrayList<>());

        CreateAreaWithActionsRequest request = createMinimalRequest(List.of(actionRequest), new ArrayList<>());

        // Act & Assert - Should not throw
        assertDoesNotThrow(() -> areaService.createAreaWithActions(request));
    }

    @Test
    void testConvertToResponse() {
        // Arrange
        when(actionLinkService.getActionLinksByArea(testAreaId)).thenReturn(new ArrayList<>());

        // Act
        AreaResponse response = areaService.convertToResponse(testArea);

        // Assert
        assertNotNull(response);
        assertEquals(testAreaId, response.getId());
        assertEquals("Test Area", response.getName());
        assertEquals("Test Description", response.getDescription());
        assertTrue(response.getEnabled());
        assertEquals(testUserId, response.getUserId());
        assertEquals("test@example.com", response.getUserEmail());
        assertNotNull(response.getCreatedAt());
        assertNotNull(response.getUpdatedAt());
    }

    @Test
    void testTriggerAreaManually_Success() {
        // Arrange
        Map<String, Object> inputPayload = Map.of("key", "value");
        
        ActionInstance actionInstance = new ActionInstance();
        actionInstance.setId(UUID.randomUUID());
        actionInstance.setEnabled(true);
        actionInstance.setActionDefinition(executableActionDef);
        actionInstance.setName("Test Instance");

        when(areaRepository.findById(testAreaId)).thenReturn(Optional.of(testArea));
        when(actionInstanceRepository.findByAreaId(testAreaId)).thenReturn(List.of(actionInstance));
        doNothing().when(executionTriggerService).triggerAreaExecution(any(), any(), any());

        // Act
        Map<String, Object> response = areaService.triggerAreaManually(testAreaId, inputPayload);

        // Assert
        assertNotNull(response);
        assertEquals("triggered", response.get("status"));
        assertEquals(testAreaId, response.get("areaId"));
        assertEquals(inputPayload, response.get("payload"));
        assertEquals(1, response.get("actionInstancesTriggered"));
        verify(executionTriggerService, times(1)).triggerAreaExecution(
            eq(actionInstance), eq(ActivationModeType.MANUAL), eq(inputPayload));
    }

    @Test
    void testTriggerAreaManually_NullPayload() {
        // Arrange
        ActionInstance actionInstance = new ActionInstance();
        actionInstance.setId(UUID.randomUUID());
        actionInstance.setEnabled(true);
        actionInstance.setActionDefinition(executableActionDef);
        actionInstance.setName("Test Instance");

        when(areaRepository.findById(testAreaId)).thenReturn(Optional.of(testArea));
        when(actionInstanceRepository.findByAreaId(testAreaId)).thenReturn(List.of(actionInstance));
        doNothing().when(executionTriggerService).triggerAreaExecution(any(), any(), any());

        // Act
        Map<String, Object> response = areaService.triggerAreaManually(testAreaId, null);

        // Assert
        assertNotNull(response);
        assertEquals("triggered", response.get("status"));
        assertEquals(Map.of(), response.get("payload"));
        verify(executionTriggerService, times(1)).triggerAreaExecution(
            eq(actionInstance), eq(ActivationModeType.MANUAL), any());
    }

    @Test
    void testTriggerAreaManually_AreaNotFound() {
        // Arrange
        when(areaRepository.findById(testAreaId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalArgumentException.class, 
            () -> areaService.triggerAreaManually(testAreaId, Map.of()));
    }

    @Test
    void testTriggerAreaManually_AreaDisabled() {
        // Arrange
        testArea.setEnabled(false);
        when(areaRepository.findById(testAreaId)).thenReturn(Optional.of(testArea));

        // Act & Assert
        assertThrows(IllegalStateException.class, 
            () -> areaService.triggerAreaManually(testAreaId, Map.of()));
    }

    @Test
    void testTriggerAreaManually_NoActionInstances() {
        // Arrange
        when(areaRepository.findById(testAreaId)).thenReturn(Optional.of(testArea));
        when(actionInstanceRepository.findByAreaId(testAreaId)).thenReturn(new ArrayList<>());

        // Act
        Map<String, Object> response = areaService.triggerAreaManually(testAreaId, Map.of());

        // Assert
        assertNotNull(response);
        assertEquals("triggered", response.get("status"));
        assertEquals(0, response.get("actionInstancesTriggered"));
        verify(executionTriggerService, never()).triggerAreaExecution(any(), any(), any());
    }

    @Test
    void testTriggerAreaManually_DisabledActionInstance() {
        // Arrange
        ActionInstance disabledInstance = new ActionInstance();
        disabledInstance.setId(UUID.randomUUID());
        disabledInstance.setEnabled(false);
        disabledInstance.setActionDefinition(executableActionDef);

        when(areaRepository.findById(testAreaId)).thenReturn(Optional.of(testArea));
        when(actionInstanceRepository.findByAreaId(testAreaId)).thenReturn(List.of(disabledInstance));

        // Act
        Map<String, Object> response = areaService.triggerAreaManually(testAreaId, Map.of());

        // Assert
        assertNotNull(response);
        verify(executionTriggerService, never()).triggerAreaExecution(any(), any(), any());
    }

    @Test
    void testTriggerAreaManually_NonExecutableAction() {
        // Arrange
        ActionInstance nonExecutableInstance = new ActionInstance();
        nonExecutableInstance.setId(UUID.randomUUID());
        nonExecutableInstance.setEnabled(true);
        nonExecutableInstance.setActionDefinition(eventActionDef);

        when(areaRepository.findById(testAreaId)).thenReturn(Optional.of(testArea));
        when(actionInstanceRepository.findByAreaId(testAreaId)).thenReturn(List.of(nonExecutableInstance));

        // Act
        Map<String, Object> response = areaService.triggerAreaManually(testAreaId, Map.of());

        // Assert
        assertNotNull(response);
        verify(executionTriggerService, never()).triggerAreaExecution(any(), any(), any());
    }

    @Test
    void testCreateAreaWithActionsAndLinks_Success() {
        // Arrange
        CreateAreaWithActionsAndLinksRequest request = new CreateAreaWithActionsAndLinksRequest();
        request.setUserId(testUserId);
        request.setName("Test Area");
        request.setDescription("Test Description");
        request.setLayoutMode("linear");

        AreaActionRequest actionRequest = new AreaActionRequest();
        actionRequest.setActionDefinitionId(eventActionDefId);
        actionRequest.setName("Test Action");
        actionRequest.setParameters(Map.of());

        AreaReactionRequest reactionRequest = new AreaReactionRequest();
        reactionRequest.setActionDefinitionId(executableActionDefId);
        reactionRequest.setName("Test Reaction");
        reactionRequest.setParameters(Map.of());

        request.setActions(List.of(actionRequest));
        request.setReactions(List.of(reactionRequest));

        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(actionDefinitionRepository.findById(eventActionDefId)).thenReturn(Optional.of(eventActionDef));
        when(actionDefinitionRepository.findById(executableActionDefId)).thenReturn(Optional.of(executableActionDef));
        when(areaRepository.save(any(Area.class))).thenReturn(testArea);
        when(actionInstanceRepository.save(any(ActionInstance.class))).thenAnswer(i -> {
            ActionInstance instance = i.getArgument(0);
            instance.setId(UUID.randomUUID());
            return instance;
        });
        when(activationModeRepository.save(any(ActivationMode.class))).thenAnswer(i -> i.getArgument(0));
        when(actionLinkService.createActionLink(any(), any())).thenReturn(null);
        when(actionLinkService.getActionLinksByArea(any(UUID.class))).thenReturn(new ArrayList<>());

        // Act
        AreaResponse response = areaService.createAreaWithActionsAndLinks(request);

        // Assert
        assertNotNull(response);
        assertEquals("Test Area", response.getName());
        verify(actionLinkService, atLeastOnce()).createActionLink(any(), eq(testAreaId));
    }

    @Test
    void testCreateAreaWithActionsAndLinks_WithConnections() {
        // Arrange
        CreateAreaWithActionsAndLinksRequest request = new CreateAreaWithActionsAndLinksRequest();
        request.setUserId(testUserId);
        request.setName("Test Area");
        request.setDescription("Test Description");

        AreaActionRequest actionRequest = new AreaActionRequest();
        actionRequest.setActionDefinitionId(eventActionDefId);
        actionRequest.setName("Test Action");
        actionRequest.setParameters(Map.of());

        AreaReactionRequest reactionRequest = new AreaReactionRequest();
        reactionRequest.setActionDefinitionId(executableActionDefId);
        reactionRequest.setName("Test Reaction");
        reactionRequest.setParameters(Map.of());

        CreateAreaWithActionsAndLinksRequest.ActionConnectionRequest connection = 
            new CreateAreaWithActionsAndLinksRequest.ActionConnectionRequest();
        connection.setSourceServiceId("action_0");
        connection.setTargetServiceId("reaction_0");
        connection.setLinkType("chain");
        connection.setOrder(0);

        request.setActions(List.of(actionRequest));
        request.setReactions(List.of(reactionRequest));
        request.setConnections(List.of(connection));

        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(actionDefinitionRepository.findById(eventActionDefId)).thenReturn(Optional.of(eventActionDef));
        when(actionDefinitionRepository.findById(executableActionDefId)).thenReturn(Optional.of(executableActionDef));
        when(areaRepository.save(any(Area.class))).thenReturn(testArea);
        when(actionInstanceRepository.save(any(ActionInstance.class))).thenAnswer(i -> {
            ActionInstance instance = i.getArgument(0);
            instance.setId(UUID.randomUUID());
            return instance;
        });
        when(activationModeRepository.save(any(ActivationMode.class))).thenAnswer(i -> i.getArgument(0));
        when(actionLinkService.createActionLinksBatch(any())).thenReturn(null);
        when(actionLinkService.getActionLinksByArea(any(UUID.class))).thenReturn(new ArrayList<>());

        // Act
        AreaResponse response = areaService.createAreaWithActionsAndLinks(request);

        // Assert
        assertNotNull(response);
        verify(actionLinkService, times(1)).createActionLinksBatch(any());
    }

    @Test
    void testUpdateAreaWithActionsAndLinks_Success() {
        // Arrange
        CreateAreaWithActionsAndLinksRequest request = new CreateAreaWithActionsAndLinksRequest();
        request.setUserId(testUserId);
        request.setName("Updated Area");
        request.setDescription("Updated Description");
        request.setLayoutMode("linear");

        AreaActionRequest actionRequest = new AreaActionRequest();
        actionRequest.setActionDefinitionId(eventActionDefId);
        actionRequest.setName("Updated Action");
        actionRequest.setParameters(Map.of());

        AreaReactionRequest reactionRequest = new AreaReactionRequest();
        reactionRequest.setActionDefinitionId(executableActionDefId);
        reactionRequest.setName("Updated Reaction");
        reactionRequest.setParameters(Map.of());

        request.setActions(List.of(actionRequest));
        request.setReactions(List.of(reactionRequest));

        when(areaRepository.findById(testAreaId)).thenReturn(Optional.of(testArea));
        when(actionDefinitionRepository.findById(eventActionDefId)).thenReturn(Optional.of(eventActionDef));
        when(actionDefinitionRepository.findById(executableActionDefId)).thenReturn(Optional.of(executableActionDef));
        when(areaRepository.save(any(Area.class))).thenReturn(testArea);
        when(actionInstanceRepository.save(any(ActionInstance.class))).thenAnswer(i -> {
            ActionInstance instance = i.getArgument(0);
            instance.setId(UUID.randomUUID());
            return instance;
        });
        when(activationModeRepository.save(any(ActivationMode.class))).thenAnswer(i -> i.getArgument(0));
        when(actionLinkService.createActionLink(any(), any())).thenReturn(null);
        when(actionLinkService.getActionLinksByArea(any(UUID.class))).thenReturn(new ArrayList<>());
        doNothing().when(actionLinkService).deleteAllLinksForArea(testAreaId);
        doNothing().when(actionInstanceRepository).deleteByAreaId(testAreaId);

        // Act
        AreaResponse response = areaService.updateAreaWithActionsAndLinks(testAreaId, request);

        // Assert
        assertNotNull(response);
        verify(actionLinkService, times(1)).deleteAllLinksForArea(testAreaId);
        verify(actionInstanceRepository, times(1)).deleteByAreaId(testAreaId);
    }

    @Test
    void testUpdateAreaWithActionsAndLinks_AreaNotFound() {
        // Arrange
        CreateAreaWithActionsAndLinksRequest request = new CreateAreaWithActionsAndLinksRequest();
        request.setUserId(testUserId);
        request.setName("Updated Area");
        request.setActions(new ArrayList<>());
        request.setReactions(new ArrayList<>());

        when(areaRepository.findById(testAreaId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalArgumentException.class, 
            () -> areaService.updateAreaWithActionsAndLinks(testAreaId, request));
    }

    @Test
    void testUpdateAreaWithActionsAndLinks_WrongUser() {
        // Arrange
        UUID wrongUserId = UUID.randomUUID();
        CreateAreaWithActionsAndLinksRequest request = new CreateAreaWithActionsAndLinksRequest();
        request.setUserId(wrongUserId);
        request.setName("Updated Area");
        request.setActions(new ArrayList<>());
        request.setReactions(new ArrayList<>());

        when(areaRepository.findById(testAreaId)).thenReturn(Optional.of(testArea));

        // Act & Assert
        assertThrows(IllegalArgumentException.class, 
            () -> areaService.updateAreaWithActionsAndLinks(testAreaId, request));
    }

    @Test
    void testCreateActionInstancesWithMapping() {
        // Arrange
        AreaActionRequest actionRequest1 = new AreaActionRequest();
        actionRequest1.setActionDefinitionId(eventActionDefId);
        actionRequest1.setName("Action 1");
        actionRequest1.setParameters(Map.of());

        AreaActionRequest actionRequest2 = new AreaActionRequest();
        actionRequest2.setActionDefinitionId(eventActionDefId);
        actionRequest2.setName("Action 2");
        actionRequest2.setParameters(Map.of());

        AreaReactionRequest reactionRequest1 = new AreaReactionRequest();
        reactionRequest1.setActionDefinitionId(executableActionDefId);
        reactionRequest1.setName("Reaction 1");
        reactionRequest1.setParameters(Map.of());

        CreateAreaWithActionsAndLinksRequest request = new CreateAreaWithActionsAndLinksRequest();
        request.setUserId(testUserId);
        request.setName("Test Area");
        request.setActions(List.of(actionRequest1, actionRequest2));
        request.setReactions(List.of(reactionRequest1));

        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(actionDefinitionRepository.findById(eventActionDefId)).thenReturn(Optional.of(eventActionDef));
        when(actionDefinitionRepository.findById(executableActionDefId)).thenReturn(Optional.of(executableActionDef));
        when(areaRepository.save(any(Area.class))).thenReturn(testArea);
        when(actionInstanceRepository.save(any(ActionInstance.class))).thenAnswer(i -> {
            ActionInstance instance = i.getArgument(0);
            instance.setId(UUID.randomUUID());
            return instance;
        });
        when(activationModeRepository.save(any(ActivationMode.class))).thenAnswer(i -> i.getArgument(0));
        when(actionLinkService.getActionLinksByArea(any(UUID.class))).thenReturn(new ArrayList<>());

        // Act
        AreaResponse response = areaService.createAreaWithActionsAndLinks(request);

        // Assert
        assertNotNull(response);
        verify(actionInstanceRepository, times(3)).save(any(ActionInstance.class));
    }

    @Test
    void testCreateLinearActionLinks() {
        // Arrange
        AreaActionRequest action1 = new AreaActionRequest();
        action1.setActionDefinitionId(eventActionDefId);
        action1.setName("Action 1");
        action1.setParameters(Map.of());

        AreaActionRequest action2 = new AreaActionRequest();
        action2.setActionDefinitionId(eventActionDefId);
        action2.setName("Action 2");
        action2.setParameters(Map.of());

        AreaReactionRequest reaction1 = new AreaReactionRequest();
        reaction1.setActionDefinitionId(executableActionDefId);
        reaction1.setName("Reaction 1");
        reaction1.setParameters(Map.of());

        CreateAreaWithActionsAndLinksRequest request = new CreateAreaWithActionsAndLinksRequest();
        request.setUserId(testUserId);
        request.setName("Test Area");
        request.setLayoutMode("linear");
        request.setActions(List.of(action1, action2));
        request.setReactions(List.of(reaction1));

        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(actionDefinitionRepository.findById(eventActionDefId)).thenReturn(Optional.of(eventActionDef));
        when(actionDefinitionRepository.findById(executableActionDefId)).thenReturn(Optional.of(executableActionDef));
        when(areaRepository.save(any(Area.class))).thenReturn(testArea);
        when(actionInstanceRepository.save(any(ActionInstance.class))).thenAnswer(i -> {
            ActionInstance instance = i.getArgument(0);
            instance.setId(UUID.randomUUID());
            return instance;
        });
        when(activationModeRepository.save(any(ActivationMode.class))).thenAnswer(i -> i.getArgument(0));
        when(actionLinkService.createActionLink(any(), any())).thenReturn(null);
        when(actionLinkService.getActionLinksByArea(any(UUID.class))).thenReturn(new ArrayList<>());

        // Act
        AreaResponse response = areaService.createAreaWithActionsAndLinks(request);

        // Assert
        assertNotNull(response);
        // Should create 2 links: action1->action2, action2->reaction1
        verify(actionLinkService, times(2)).createActionLink(any(), eq(testAreaId));
    }

    @Test
    void testCreateActionLinks_InvalidSourceServiceId() {
        // Arrange
        CreateAreaWithActionsAndLinksRequest.ActionConnectionRequest connection = 
            new CreateAreaWithActionsAndLinksRequest.ActionConnectionRequest();
        connection.setSourceServiceId("invalid_source");
        connection.setTargetServiceId("reaction_0");
        connection.setLinkType("chain");

        AreaReactionRequest reactionRequest = new AreaReactionRequest();
        reactionRequest.setActionDefinitionId(executableActionDefId);
        reactionRequest.setName("Reaction");
        reactionRequest.setParameters(Map.of());

        CreateAreaWithActionsAndLinksRequest request = new CreateAreaWithActionsAndLinksRequest();
        request.setUserId(testUserId);
        request.setName("Test Area");
        request.setActions(new ArrayList<>());
        request.setReactions(List.of(reactionRequest));
        request.setConnections(List.of(connection));

        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(actionDefinitionRepository.findById(executableActionDefId)).thenReturn(Optional.of(executableActionDef));
        when(areaRepository.save(any(Area.class))).thenReturn(testArea);
        when(actionInstanceRepository.save(any(ActionInstance.class))).thenAnswer(i -> {
            ActionInstance instance = i.getArgument(0);
            instance.setId(UUID.randomUUID());
            return instance;
        });
        when(activationModeRepository.save(any(ActivationMode.class))).thenAnswer(i -> i.getArgument(0));
        when(actionLinkService.getActionLinksByArea(any(UUID.class))).thenReturn(new ArrayList<>());

        // Act
        AreaResponse response = areaService.createAreaWithActionsAndLinks(request);

        // Assert
        assertNotNull(response);
        // Should not create any link because source is invalid
        verify(actionLinkService, never()).createActionLink(any(), any());
    }

    // Helper methods
    private CreateAreaWithActionsRequest createMinimalRequest(List<AreaActionRequest> actions, 
                                                              List<AreaReactionRequest> reactions) {
        CreateAreaWithActionsRequest request = new CreateAreaWithActionsRequest();
        request.setUserId(testUserId);
        request.setName("Test Area");
        request.setDescription("Test Description");
        request.setActions(actions);
        request.setReactions(reactions);
        return request;
    }
}
