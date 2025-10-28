package area.server.AREA_Back.service.Area;

import area.server.AREA_Back.dto.ActionLinkResponse;
import area.server.AREA_Back.dto.BatchCreateActionLinksRequest;
import area.server.AREA_Back.dto.CreateActionLinkRequest;
import area.server.AREA_Back.entity.ActionInstance;
import area.server.AREA_Back.entity.ActionLink;
import area.server.AREA_Back.entity.ActionLinkId;
import area.server.AREA_Back.entity.Area;
import area.server.AREA_Back.entity.Execution;
import area.server.AREA_Back.repository.ActionInstanceRepository;
import area.server.AREA_Back.repository.ActionLinkRepository;
import area.server.AREA_Back.repository.AreaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActionLinkServiceTest {

    @Mock
    private ActionLinkRepository actionLinkRepository;

    @Mock
    private ActionInstanceRepository actionInstanceRepository;

    @Mock
    private AreaRepository areaRepository;

    @InjectMocks
    private ActionLinkService actionLinkService;

    private UUID areaId;
    private UUID sourceActionInstanceId;
    private UUID targetActionInstanceId;
    private Area area;
    private ActionInstance sourceAction;
    private ActionInstance targetAction;
    private ActionLink actionLink;
    private CreateActionLinkRequest createRequest;

    @BeforeEach
    void setUp() {
        areaId = UUID.randomUUID();
        sourceActionInstanceId = UUID.randomUUID();
        targetActionInstanceId = UUID.randomUUID();

        area = new Area();
        area.setId(areaId);

        sourceAction = new ActionInstance();
        sourceAction.setId(sourceActionInstanceId);
        sourceAction.setArea(area);
        sourceAction.setName("Source Action");

        targetAction = new ActionInstance();
        targetAction.setId(targetActionInstanceId);
        targetAction.setArea(area);
        targetAction.setName("Target Action");

        actionLink = new ActionLink();
        actionLink.setSourceActionInstance(sourceAction);
        actionLink.setTargetActionInstance(targetAction);
        actionLink.setArea(area);
        actionLink.setLinkType("chain");
        actionLink.setMapping(new HashMap<>());
        actionLink.setCondition(new HashMap<>());
        actionLink.setOrder(0);
        actionLink.setCreatedAt(LocalDateTime.now());

        createRequest = new CreateActionLinkRequest();
        createRequest.setSourceActionInstanceId(sourceActionInstanceId);
        createRequest.setTargetActionInstanceId(targetActionInstanceId);
        createRequest.setLinkType("chain");
        createRequest.setMapping(new HashMap<>());
        createRequest.setCondition(new HashMap<>());
        createRequest.setOrder(0);
    }

    @Test
    void createActionLink_Success() {
        // Given
        when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));
        when(actionInstanceRepository.findById(sourceActionInstanceId))
                .thenReturn(Optional.of(sourceAction));
        when(actionInstanceRepository.findById(targetActionInstanceId))
                .thenReturn(Optional.of(targetAction));
        when(actionLinkRepository.existsBySourceActionInstanceAndTargetActionInstance(
                sourceAction, targetAction)).thenReturn(false);
        when(actionLinkRepository.save(any(ActionLink.class))).thenReturn(actionLink);

        // When
        ActionLinkResponse response = actionLinkService.createActionLink(createRequest, areaId);

        // Then
        assertNotNull(response);
        assertEquals(sourceActionInstanceId, response.getSourceActionInstanceId());
        assertEquals(targetActionInstanceId, response.getTargetActionInstanceId());
        assertEquals(areaId, response.getAreaId());
        assertEquals("chain", response.getLinkType());
        verify(actionLinkRepository).save(any(ActionLink.class));
    }

    @Test
    void createActionLink_AreaNotFound_ThrowsException() {
        // Given
        when(areaRepository.findById(areaId)).thenReturn(Optional.empty());

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                actionLinkService.createActionLink(createRequest, areaId)
        );
        assertEquals("Area not found with id: " + areaId, exception.getMessage());
        verify(actionLinkRepository, never()).save(any(ActionLink.class));
    }

    @Test
    void createActionLink_SourceActionNotFound_ThrowsException() {
        // Given
        when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));
        when(actionInstanceRepository.findById(sourceActionInstanceId))
                .thenReturn(Optional.empty());

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                actionLinkService.createActionLink(createRequest, areaId)
        );
        assertEquals("Source action not found with id: " + sourceActionInstanceId,
                exception.getMessage());
        verify(actionLinkRepository, never()).save(any(ActionLink.class));
    }

    @Test
    void createActionLink_TargetActionNotFound_ThrowsException() {
        // Given
        when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));
        when(actionInstanceRepository.findById(sourceActionInstanceId))
                .thenReturn(Optional.of(sourceAction));
        when(actionInstanceRepository.findById(targetActionInstanceId))
                .thenReturn(Optional.empty());

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                actionLinkService.createActionLink(createRequest, areaId)
        );
        assertEquals("Target action not found with id: " + targetActionInstanceId,
                exception.getMessage());
        verify(actionLinkRepository, never()).save(any(ActionLink.class));
    }

    @Test
    void createActionLink_SourceActionNotInArea_ThrowsException() {
        // Given
        Area differentArea = new Area();
        differentArea.setId(UUID.randomUUID());
        sourceAction.setArea(differentArea);

        when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));
        when(actionInstanceRepository.findById(sourceActionInstanceId))
                .thenReturn(Optional.of(sourceAction));
        when(actionInstanceRepository.findById(targetActionInstanceId))
                .thenReturn(Optional.of(targetAction));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                actionLinkService.createActionLink(createRequest, areaId)
        );
        assertEquals("Source action does not belong to the specified area", exception.getMessage());
        verify(actionLinkRepository, never()).save(any(ActionLink.class));
    }

    @Test
    void createActionLink_TargetActionNotInArea_ThrowsException() {
        // Given
        Area differentArea = new Area();
        differentArea.setId(UUID.randomUUID());
        targetAction.setArea(differentArea);

        when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));
        when(actionInstanceRepository.findById(sourceActionInstanceId))
                .thenReturn(Optional.of(sourceAction));
        when(actionInstanceRepository.findById(targetActionInstanceId))
                .thenReturn(Optional.of(targetAction));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                actionLinkService.createActionLink(createRequest, areaId)
        );
        assertEquals("Target action does not belong to the specified area", exception.getMessage());
        verify(actionLinkRepository, never()).save(any(ActionLink.class));
    }

    @Test
    void createActionLink_LinkAlreadyExists_ThrowsException() {
        // Given
        when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));
        when(actionInstanceRepository.findById(sourceActionInstanceId))
                .thenReturn(Optional.of(sourceAction));
        when(actionInstanceRepository.findById(targetActionInstanceId))
                .thenReturn(Optional.of(targetAction));
        when(actionLinkRepository.existsBySourceActionInstanceAndTargetActionInstance(
                sourceAction, targetAction)).thenReturn(true);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                actionLinkService.createActionLink(createRequest, areaId)
        );
        assertEquals("Link already exists between these actions", exception.getMessage());
        verify(actionLinkRepository, never()).save(any(ActionLink.class));
    }

    @Test
    void createActionLink_WithNullMapping_UsesEmptyMap() {
        // Given
        createRequest.setMapping(null);
        when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));
        when(actionInstanceRepository.findById(sourceActionInstanceId))
                .thenReturn(Optional.of(sourceAction));
        when(actionInstanceRepository.findById(targetActionInstanceId))
                .thenReturn(Optional.of(targetAction));
        when(actionLinkRepository.existsBySourceActionInstanceAndTargetActionInstance(
                sourceAction, targetAction)).thenReturn(false);
        when(actionLinkRepository.save(any(ActionLink.class))).thenReturn(actionLink);

        // When
        ActionLinkResponse response = actionLinkService.createActionLink(createRequest, areaId);

        // Then
        assertNotNull(response);
        ArgumentCaptor<ActionLink> captor = ArgumentCaptor.forClass(ActionLink.class);
        verify(actionLinkRepository).save(captor.capture());
        assertNotNull(captor.getValue().getMapping());
        assertTrue(captor.getValue().getMapping().isEmpty());
    }

    @Test
    void createActionLink_WithNullCondition_UsesEmptyMap() {
        // Given
        createRequest.setCondition(null);
        when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));
        when(actionInstanceRepository.findById(sourceActionInstanceId))
                .thenReturn(Optional.of(sourceAction));
        when(actionInstanceRepository.findById(targetActionInstanceId))
                .thenReturn(Optional.of(targetAction));
        when(actionLinkRepository.existsBySourceActionInstanceAndTargetActionInstance(
                sourceAction, targetAction)).thenReturn(false);
        when(actionLinkRepository.save(any(ActionLink.class))).thenReturn(actionLink);

        // When
        ActionLinkResponse response = actionLinkService.createActionLink(createRequest, areaId);

        // Then
        assertNotNull(response);
        ArgumentCaptor<ActionLink> captor = ArgumentCaptor.forClass(ActionLink.class);
        verify(actionLinkRepository).save(captor.capture());
        assertNotNull(captor.getValue().getCondition());
        assertTrue(captor.getValue().getCondition().isEmpty());
    }

    @Test
    void createActionLinksBatch_Success() {
        // Given
        BatchCreateActionLinksRequest.ActionLinkData linkData =
                new BatchCreateActionLinksRequest.ActionLinkData();
        linkData.setSourceActionInstanceId(sourceActionInstanceId);
        linkData.setTargetActionInstanceId(targetActionInstanceId);
        linkData.setLinkType("chain");
        linkData.setMapping(new HashMap<>());
        linkData.setCondition(new HashMap<>());
        linkData.setOrder(0);

        BatchCreateActionLinksRequest batchRequest = new BatchCreateActionLinksRequest();
        batchRequest.setAreaId(areaId);
        batchRequest.setLinks(List.of(linkData));

        when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));
        when(actionInstanceRepository.findById(sourceActionInstanceId))
                .thenReturn(Optional.of(sourceAction));
        when(actionInstanceRepository.findById(targetActionInstanceId))
                .thenReturn(Optional.of(targetAction));
        when(actionLinkRepository.saveAll(anyList())).thenReturn(List.of(actionLink));

        // When
        List<ActionLinkResponse> responses = actionLinkService.createActionLinksBatch(batchRequest);

        // Then
        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals(sourceActionInstanceId, responses.get(0).getSourceActionInstanceId());
        verify(actionLinkRepository).saveAll(anyList());
    }

    @Test
    void createActionLinksBatch_AreaNotFound_ThrowsException() {
        // Given
        BatchCreateActionLinksRequest batchRequest = new BatchCreateActionLinksRequest();
        batchRequest.setAreaId(areaId);
        batchRequest.setLinks(new ArrayList<>());

        when(areaRepository.findById(areaId)).thenReturn(Optional.empty());

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                actionLinkService.createActionLinksBatch(batchRequest)
        );
        assertEquals("Area not found with id: " + areaId, exception.getMessage());
        verify(actionLinkRepository, never()).saveAll(anyList());
    }

    @Test
    void createActionLinksBatch_SourceActionNotFound_ThrowsException() {
        // Given
        BatchCreateActionLinksRequest.ActionLinkData linkData =
                new BatchCreateActionLinksRequest.ActionLinkData();
        linkData.setSourceActionInstanceId(sourceActionInstanceId);
        linkData.setTargetActionInstanceId(targetActionInstanceId);

        BatchCreateActionLinksRequest batchRequest = new BatchCreateActionLinksRequest();
        batchRequest.setAreaId(areaId);
        batchRequest.setLinks(List.of(linkData));

        when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));
        when(actionInstanceRepository.findById(sourceActionInstanceId))
                .thenReturn(Optional.empty());

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                actionLinkService.createActionLinksBatch(batchRequest)
        );
        assertTrue(exception.getMessage().contains("Source action not found with id"));
        verify(actionLinkRepository, never()).saveAll(anyList());
    }

    @Test
    void createActionLinksBatch_TargetActionNotFound_ThrowsException() {
        // Given
        BatchCreateActionLinksRequest.ActionLinkData linkData =
                new BatchCreateActionLinksRequest.ActionLinkData();
        linkData.setSourceActionInstanceId(sourceActionInstanceId);
        linkData.setTargetActionInstanceId(targetActionInstanceId);

        BatchCreateActionLinksRequest batchRequest = new BatchCreateActionLinksRequest();
        batchRequest.setAreaId(areaId);
        batchRequest.setLinks(List.of(linkData));

        when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));
        when(actionInstanceRepository.findById(sourceActionInstanceId))
                .thenReturn(Optional.of(sourceAction));
        when(actionInstanceRepository.findById(targetActionInstanceId))
                .thenReturn(Optional.empty());

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                actionLinkService.createActionLinksBatch(batchRequest)
        );
        assertTrue(exception.getMessage().contains("Target action not found with id"));
        verify(actionLinkRepository, never()).saveAll(anyList());
    }

    @Test
    void createActionLinksBatch_WithNullMapping_UsesEmptyMap() {
        // Given
        BatchCreateActionLinksRequest.ActionLinkData linkData =
                new BatchCreateActionLinksRequest.ActionLinkData();
        linkData.setSourceActionInstanceId(sourceActionInstanceId);
        linkData.setTargetActionInstanceId(targetActionInstanceId);
        linkData.setMapping(null);

        BatchCreateActionLinksRequest batchRequest = new BatchCreateActionLinksRequest();
        batchRequest.setAreaId(areaId);
        batchRequest.setLinks(List.of(linkData));

        when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));
        when(actionInstanceRepository.findById(sourceActionInstanceId))
                .thenReturn(Optional.of(sourceAction));
        when(actionInstanceRepository.findById(targetActionInstanceId))
                .thenReturn(Optional.of(targetAction));
        when(actionLinkRepository.saveAll(anyList())).thenReturn(List.of(actionLink));

        // When
        List<ActionLinkResponse> responses = actionLinkService.createActionLinksBatch(batchRequest);

        // Then
        assertNotNull(responses);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ActionLink>> captor = ArgumentCaptor.forClass(List.class);
        verify(actionLinkRepository).saveAll(captor.capture());
        ActionLink savedLink = captor.getValue().get(0);
        assertNotNull(savedLink.getMapping());
        assertTrue(savedLink.getMapping().isEmpty());
    }

    @Test
    void createActionLinksBatch_WithNullCondition_UsesEmptyMap() {
        // Given
        BatchCreateActionLinksRequest.ActionLinkData linkData =
                new BatchCreateActionLinksRequest.ActionLinkData();
        linkData.setSourceActionInstanceId(sourceActionInstanceId);
        linkData.setTargetActionInstanceId(targetActionInstanceId);
        linkData.setCondition(null);

        BatchCreateActionLinksRequest batchRequest = new BatchCreateActionLinksRequest();
        batchRequest.setAreaId(areaId);
        batchRequest.setLinks(List.of(linkData));

        when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));
        when(actionInstanceRepository.findById(sourceActionInstanceId))
                .thenReturn(Optional.of(sourceAction));
        when(actionInstanceRepository.findById(targetActionInstanceId))
                .thenReturn(Optional.of(targetAction));
        when(actionLinkRepository.saveAll(anyList())).thenReturn(List.of(actionLink));

        // When
        List<ActionLinkResponse> responses = actionLinkService.createActionLinksBatch(batchRequest);

        // Then
        assertNotNull(responses);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ActionLink>> captor = ArgumentCaptor.forClass(List.class);
        verify(actionLinkRepository).saveAll(captor.capture());
        ActionLink savedLink = captor.getValue().get(0);
        assertNotNull(savedLink.getCondition());
        assertTrue(savedLink.getCondition().isEmpty());
    }

    @Test
    void getActionLinksByArea_Success() {
        // Given
        when(actionLinkRepository.findByAreaIdOrderByOrder(areaId))
                .thenReturn(List.of(actionLink));

        // When
        List<ActionLinkResponse> responses = actionLinkService.getActionLinksByArea(areaId);

        // Then
        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals(sourceActionInstanceId, responses.get(0).getSourceActionInstanceId());
        verify(actionLinkRepository).findByAreaIdOrderByOrder(areaId);
    }

    @Test
    void getActionLinksByArea_EmptyList() {
        // Given
        when(actionLinkRepository.findByAreaIdOrderByOrder(areaId))
                .thenReturn(new ArrayList<>());

        // When
        List<ActionLinkResponse> responses = actionLinkService.getActionLinksByArea(areaId);

        // Then
        assertNotNull(responses);
        assertTrue(responses.isEmpty());
        verify(actionLinkRepository).findByAreaIdOrderByOrder(areaId);
    }

    @Test
    void deleteActionLink_Success() {
        // Given
        when(actionInstanceRepository.findById(sourceActionInstanceId))
                .thenReturn(Optional.of(sourceAction));
        when(actionInstanceRepository.findById(targetActionInstanceId))
                .thenReturn(Optional.of(targetAction));
        doNothing().when(actionLinkRepository).deleteById(any(ActionLinkId.class));

        // When
        actionLinkService.deleteActionLink(sourceActionInstanceId, targetActionInstanceId);

        // Then
        ArgumentCaptor<ActionLinkId> captor = ArgumentCaptor.forClass(ActionLinkId.class);
        verify(actionLinkRepository).deleteById(captor.capture());
        assertEquals(sourceActionInstanceId, captor.getValue().getSourceActionInstance());
        assertEquals(targetActionInstanceId, captor.getValue().getTargetActionInstance());
    }

    @Test
    void deleteActionLink_SourceActionNotFound_ThrowsException() {
        // Given
        when(actionInstanceRepository.findById(sourceActionInstanceId))
                .thenReturn(Optional.empty());

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                actionLinkService.deleteActionLink(sourceActionInstanceId, targetActionInstanceId)
        );
        assertEquals("Source action not found with id: " + sourceActionInstanceId,
                exception.getMessage());
        verify(actionLinkRepository, never()).deleteById(any(ActionLinkId.class));
    }

    @Test
    void deleteActionLink_TargetActionNotFound_ThrowsException() {
        // Given
        when(actionInstanceRepository.findById(sourceActionInstanceId))
                .thenReturn(Optional.of(sourceAction));
        when(actionInstanceRepository.findById(targetActionInstanceId))
                .thenReturn(Optional.empty());

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                actionLinkService.deleteActionLink(sourceActionInstanceId, targetActionInstanceId)
        );
        assertEquals("Target action not found with id: " + targetActionInstanceId,
                exception.getMessage());
        verify(actionLinkRepository, never()).deleteById(any(ActionLinkId.class));
    }

    @Test
    void deleteActionLinksByArea_Success() {
        // Given
        when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));
        doNothing().when(actionLinkRepository).deleteByArea(area);

        // When
        actionLinkService.deleteActionLinksByArea(areaId);

        // Then
        verify(areaRepository).findById(areaId);
        verify(actionLinkRepository).deleteByArea(area);
    }

    @Test
    void deleteActionLinksByArea_AreaNotFound_ThrowsException() {
        // Given
        when(areaRepository.findById(areaId)).thenReturn(Optional.empty());

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                actionLinkService.deleteActionLinksByArea(areaId)
        );
        assertEquals("Area not found with id: " + areaId, exception.getMessage());
        verify(actionLinkRepository, never()).deleteByArea(any(Area.class));
    }

    @Test
    void deleteAllLinksForArea_Success() {
        // Given
        when(actionLinkRepository.findByAreaIdOrderByOrder(areaId))
                .thenReturn(List.of(actionLink));
        doNothing().when(actionLinkRepository).deleteAll(anyList());

        // When
        actionLinkService.deleteAllLinksForArea(areaId);

        // Then
        verify(actionLinkRepository).findByAreaIdOrderByOrder(areaId);
        verify(actionLinkRepository).deleteAll(anyList());
    }

    @Test
    void deleteAllLinksForArea_EmptyList() {
        // Given
        when(actionLinkRepository.findByAreaIdOrderByOrder(areaId))
                .thenReturn(new ArrayList<>());
        doNothing().when(actionLinkRepository).deleteAll(anyList());

        // When
        actionLinkService.deleteAllLinksForArea(areaId);

        // Then
        verify(actionLinkRepository).findByAreaIdOrderByOrder(areaId);
        verify(actionLinkRepository).deleteAll(anyList());
    }

    @Test
    void getActionLinksByActionInstance_Success() {
        // Given
        when(actionLinkRepository.findByActionInstanceId(sourceActionInstanceId))
                .thenReturn(List.of(actionLink));

        // When
        List<ActionLinkResponse> responses =
                actionLinkService.getActionLinksByActionInstance(sourceActionInstanceId);

        // Then
        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals(sourceActionInstanceId, responses.get(0).getSourceActionInstanceId());
        verify(actionLinkRepository).findByActionInstanceId(sourceActionInstanceId);
    }

    @Test
    void getActionLinksByActionInstance_EmptyList() {
        // Given
        when(actionLinkRepository.findByActionInstanceId(sourceActionInstanceId))
                .thenReturn(new ArrayList<>());

        // When
        List<ActionLinkResponse> responses =
                actionLinkService.getActionLinksByActionInstance(sourceActionInstanceId);

        // Then
        assertNotNull(responses);
        assertTrue(responses.isEmpty());
        verify(actionLinkRepository).findByActionInstanceId(sourceActionInstanceId);
    }

    @Test
    void mapToResponse_Success() {
        // Given
        Map<String, Object> mapping = new HashMap<>();
        mapping.put("key", "value");
        Map<String, Object> condition = new HashMap<>();
        condition.put("condition", "test");

        actionLink.setMapping(mapping);
        actionLink.setCondition(condition);

        // When
        List<ActionLinkResponse> responses = actionLinkService.getActionLinksByArea(areaId);
        when(actionLinkRepository.findByAreaIdOrderByOrder(areaId))
                .thenReturn(List.of(actionLink));
        responses = actionLinkService.getActionLinksByArea(areaId);

        // Then
        assertNotNull(responses);
        assertEquals(1, responses.size());
        ActionLinkResponse response = responses.get(0);
        assertEquals(sourceActionInstanceId, response.getSourceActionInstanceId());
        assertEquals(targetActionInstanceId, response.getTargetActionInstanceId());
        assertEquals("Source Action", response.getSourceActionName());
        assertEquals("Target Action", response.getTargetActionName());
        assertEquals(areaId, response.getAreaId());
        assertEquals("chain", response.getLinkType());
        assertEquals(mapping, response.getMapping());
        assertEquals(condition, response.getCondition());
        assertEquals(0, response.getOrder());
        assertNotNull(response.getCreatedAt());
    }

    @Test
    void triggerLinkedActions_Success() {
        // Given
        Execution execution = new Execution();
        execution.setId(UUID.randomUUID());
        execution.setActionInstance(sourceAction);

        when(actionLinkRepository.findBySourceActionInstanceId(sourceActionInstanceId))
                .thenReturn(List.of(actionLink));

        // When
        actionLinkService.triggerLinkedActions(execution);

        // Then
        verify(actionLinkRepository).findBySourceActionInstanceId(sourceActionInstanceId);
    }

    @Test
    void triggerLinkedActions_NoLinkedActions() {
        // Given
        Execution execution = new Execution();
        execution.setId(UUID.randomUUID());
        execution.setActionInstance(sourceAction);

        when(actionLinkRepository.findBySourceActionInstanceId(sourceActionInstanceId))
                .thenReturn(new ArrayList<>());

        // When
        actionLinkService.triggerLinkedActions(execution);

        // Then
        verify(actionLinkRepository).findBySourceActionInstanceId(sourceActionInstanceId);
    }

    @Test
    void triggerLinkedActions_MultipleLinks() {
        // Given
        Execution execution = new Execution();
        execution.setId(UUID.randomUUID());
        execution.setActionInstance(sourceAction);

        ActionInstance targetAction2 = new ActionInstance();
        targetAction2.setId(UUID.randomUUID());
        targetAction2.setArea(area);
        targetAction2.setName("Target Action 2");

        ActionLink actionLink2 = new ActionLink();
        actionLink2.setSourceActionInstance(sourceAction);
        actionLink2.setTargetActionInstance(targetAction2);
        actionLink2.setArea(area);
        actionLink2.setLinkType("chain");
        actionLink2.setMapping(new HashMap<>());
        actionLink2.setCondition(new HashMap<>());
        actionLink2.setOrder(1);

        when(actionLinkRepository.findBySourceActionInstanceId(sourceActionInstanceId))
                .thenReturn(List.of(actionLink, actionLink2));

        // When
        actionLinkService.triggerLinkedActions(execution);

        // Then
        verify(actionLinkRepository).findBySourceActionInstanceId(sourceActionInstanceId);
    }

    @Test
    void triggerLinkedActions_HandlesExceptionInTrigger() {
        // Given
        Execution execution = new Execution();
        execution.setId(UUID.randomUUID());
        execution.setActionInstance(sourceAction);

        when(actionLinkRepository.findBySourceActionInstanceId(sourceActionInstanceId))
                .thenReturn(List.of(actionLink));

        // When - execution should not throw even if internal trigger fails
        assertDoesNotThrow(() -> actionLinkService.triggerLinkedActions(execution));

        // Then
        verify(actionLinkRepository).findBySourceActionInstanceId(sourceActionInstanceId);
    }

    @Test
    void triggerLinkedActions_ExceptionInRepository_Propagates() {
        // Given
        Execution execution = new Execution();
        execution.setId(UUID.randomUUID());
        execution.setActionInstance(sourceAction);

        when(actionLinkRepository.findBySourceActionInstanceId(sourceActionInstanceId))
                .thenThrow(new RuntimeException("Database error"));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                actionLinkService.triggerLinkedActions(execution)
        );
        assertEquals("Database error", exception.getMessage());
    }

    @Test
    void createActionLinksBatch_MultipleLinks_Success() {
        // Given
        UUID targetActionInstanceId2 = UUID.randomUUID();
        ActionInstance targetAction2 = new ActionInstance();
        targetAction2.setId(targetActionInstanceId2);
        targetAction2.setArea(area);
        targetAction2.setName("Target Action 2");

        BatchCreateActionLinksRequest.ActionLinkData linkData1 =
                new BatchCreateActionLinksRequest.ActionLinkData();
        linkData1.setSourceActionInstanceId(sourceActionInstanceId);
        linkData1.setTargetActionInstanceId(targetActionInstanceId);
        linkData1.setLinkType("chain");
        linkData1.setOrder(0);

        BatchCreateActionLinksRequest.ActionLinkData linkData2 =
                new BatchCreateActionLinksRequest.ActionLinkData();
        linkData2.setSourceActionInstanceId(sourceActionInstanceId);
        linkData2.setTargetActionInstanceId(targetActionInstanceId2);
        linkData2.setLinkType("parallel");
        linkData2.setOrder(1);

        BatchCreateActionLinksRequest batchRequest = new BatchCreateActionLinksRequest();
        batchRequest.setAreaId(areaId);
        batchRequest.setLinks(List.of(linkData1, linkData2));

        ActionLink actionLink2 = new ActionLink();
        actionLink2.setSourceActionInstance(sourceAction);
        actionLink2.setTargetActionInstance(targetAction2);
        actionLink2.setArea(area);
        actionLink2.setLinkType("parallel");
        actionLink2.setMapping(new HashMap<>());
        actionLink2.setCondition(new HashMap<>());
        actionLink2.setOrder(1);
        actionLink2.setCreatedAt(LocalDateTime.now());

        when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));
        when(actionInstanceRepository.findById(sourceActionInstanceId))
                .thenReturn(Optional.of(sourceAction));
        when(actionInstanceRepository.findById(targetActionInstanceId))
                .thenReturn(Optional.of(targetAction));
        when(actionInstanceRepository.findById(targetActionInstanceId2))
                .thenReturn(Optional.of(targetAction2));
        when(actionLinkRepository.saveAll(anyList()))
                .thenReturn(List.of(actionLink, actionLink2));

        // When
        List<ActionLinkResponse> responses = actionLinkService.createActionLinksBatch(batchRequest);

        // Then
        assertNotNull(responses);
        assertEquals(2, responses.size());
        verify(actionLinkRepository).saveAll(anyList());
    }
}
