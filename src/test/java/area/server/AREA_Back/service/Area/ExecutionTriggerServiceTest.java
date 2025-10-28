package area.server.AREA_Back.service.Area;

import area.server.AREA_Back.dto.AreaEventMessage;
import area.server.AREA_Back.entity.*;
import area.server.AREA_Back.entity.enums.ActivationModeType;
import area.server.AREA_Back.entity.enums.ExecutionStatus;
import area.server.AREA_Back.repository.ActionLinkRepository;
import area.server.AREA_Back.service.Redis.RedisEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExecutionTriggerServiceTest {

    @Mock
    private ExecutionService executionService;

    @Mock
    private RedisEventService redisEventService;

    @Mock
    private ActionLinkRepository actionLinkRepository;

    @InjectMocks
    private ExecutionTriggerService executionTriggerService;

    private Area area;
    private ActionInstance actionInstance;
    private ActionInstance targetActionInstance;
    private ActionDefinition actionDefinition;
    private Execution execution;
    private Map<String, Object> inputPayload;
    private UUID correlationId;

    @BeforeEach
    void setUp() {
        correlationId = UUID.randomUUID();

        area = new Area();
        area.setId(UUID.randomUUID());
        area.setName("Test Area");

        actionDefinition = new ActionDefinition();
        actionDefinition.setId(UUID.randomUUID());
        actionDefinition.setKey("test.action");
        actionDefinition.setIsExecutable(true);

        actionInstance = new ActionInstance();
        actionInstance.setId(UUID.randomUUID());
        actionInstance.setName("Test Action");
        actionInstance.setArea(area);
        actionInstance.setActionDefinition(actionDefinition);
        actionInstance.setParams(new HashMap<>());

        targetActionInstance = new ActionInstance();
        targetActionInstance.setId(UUID.randomUUID());
        targetActionInstance.setName("Target Action");
        targetActionInstance.setArea(area);
        targetActionInstance.setActionDefinition(actionDefinition);
        targetActionInstance.setParams(new HashMap<>());

        execution = new Execution();
        execution.setId(UUID.randomUUID());
        execution.setActionInstance(actionInstance);
        execution.setArea(area);
        execution.setStatus(ExecutionStatus.QUEUED);
        execution.setCorrelationId(correlationId);

        inputPayload = new HashMap<>();
        inputPayload.put("key", "value");
        inputPayload.put("data", "test data");
    }

    @Test
    void triggerAreaExecution_WithManualActivation_ShouldCreateExecutionAndPublishEvent() {
        // Given
        when(actionLinkRepository.findBySourceActionInstanceIdWithTargetFetch(actionInstance.getId()))
                .thenReturn(Collections.emptyList());
        when(executionService.createExecutionWithActivationType(
                eq(actionInstance),
                eq(ActivationModeType.MANUAL),
                eq(inputPayload),
                any(UUID.class)
        )).thenReturn(execution);

        // When
        executionTriggerService.triggerAreaExecution(actionInstance, ActivationModeType.MANUAL, inputPayload);

        // Then
        verify(actionLinkRepository).findBySourceActionInstanceIdWithTargetFetch(actionInstance.getId());
        verify(executionService).createExecutionWithActivationType(
                eq(actionInstance),
                eq(ActivationModeType.MANUAL),
                eq(inputPayload),
                any(UUID.class)
        );

        ArgumentCaptor<AreaEventMessage> messageCaptor = ArgumentCaptor.forClass(AreaEventMessage.class);
        verify(redisEventService).publishAreaEvent(messageCaptor.capture());

        AreaEventMessage capturedMessage = messageCaptor.getValue();
        assertEquals(execution.getId(), capturedMessage.getExecutionId());
        assertEquals(actionInstance.getId(), capturedMessage.getActionInstanceId());
        assertEquals(area.getId(), capturedMessage.getAreaId());
        assertEquals("manual", capturedMessage.getEventType());
        assertEquals("trigger_service", capturedMessage.getSource());
        assertEquals(inputPayload, capturedMessage.getPayload());
        assertNotNull(capturedMessage.getCorrelationId());
    }

    @Test
    void triggerAreaExecution_WithWebhookActivation_ShouldCreateExecutionWithWebhookType() {
        // Given
        when(actionLinkRepository.findBySourceActionInstanceIdWithTargetFetch(actionInstance.getId()))
                .thenReturn(Collections.emptyList());
        when(executionService.createExecutionWithActivationType(
                eq(actionInstance),
                eq(ActivationModeType.WEBHOOK),
                eq(inputPayload),
                any(UUID.class)
        )).thenReturn(execution);

        // When
        executionTriggerService.triggerAreaExecution(actionInstance, ActivationModeType.WEBHOOK, inputPayload);

        // Then
        verify(executionService).createExecutionWithActivationType(
                eq(actionInstance),
                eq(ActivationModeType.WEBHOOK),
                eq(inputPayload),
                any(UUID.class)
        );

        ArgumentCaptor<AreaEventMessage> messageCaptor = ArgumentCaptor.forClass(AreaEventMessage.class);
        verify(redisEventService).publishAreaEvent(messageCaptor.capture());

        AreaEventMessage capturedMessage = messageCaptor.getValue();
        assertEquals("webhook", capturedMessage.getEventType());
    }

    @Test
    void triggerAreaExecution_WithLinkedActions_ShouldTriggerLinkedReactions() {
        // Given
        ActionLink actionLink = new ActionLink();
        actionLink.setSourceActionInstance(actionInstance);
        actionLink.setTargetActionInstance(targetActionInstance);
        actionLink.setArea(area);

        when(actionLinkRepository.findBySourceActionInstanceIdWithTargetFetch(actionInstance.getId()))
                .thenReturn(List.of(actionLink));
        when(actionLinkRepository.findBySourceActionInstanceIdWithTargetFetch(targetActionInstance.getId()))
                .thenReturn(Collections.emptyList());
        when(executionService.createExecutionWithActivationType(
                eq(targetActionInstance),
                eq(ActivationModeType.MANUAL),
                eq(inputPayload),
                any(UUID.class)
        )).thenReturn(execution);

        // When
        executionTriggerService.triggerAreaExecution(actionInstance, ActivationModeType.WEBHOOK, inputPayload);

        // Then
        verify(actionLinkRepository).findBySourceActionInstanceIdWithTargetFetch(actionInstance.getId());
        verify(actionLinkRepository).findBySourceActionInstanceIdWithTargetFetch(targetActionInstance.getId());
        
        // Should not create execution for the source action
        verify(executionService, never()).createExecutionWithActivationType(
                eq(actionInstance),
                any(),
                any(),
                any()
        );
        
        // Should create execution for the target action
        verify(executionService).createExecutionWithActivationType(
                eq(targetActionInstance),
                eq(ActivationModeType.MANUAL),
                eq(inputPayload),
                any(UUID.class)
        );
    }

    @Test
    void triggerAreaExecution_WithMultipleLinkedActions_ShouldTriggerAllReactions() {
        // Given
        ActionInstance targetAction2 = new ActionInstance();
        targetAction2.setId(UUID.randomUUID());
        targetAction2.setName("Target Action 2");
        targetAction2.setArea(area);
        targetAction2.setActionDefinition(actionDefinition);

        ActionLink actionLink1 = new ActionLink();
        actionLink1.setSourceActionInstance(actionInstance);
        actionLink1.setTargetActionInstance(targetActionInstance);
        actionLink1.setArea(area);

        ActionLink actionLink2 = new ActionLink();
        actionLink2.setSourceActionInstance(actionInstance);
        actionLink2.setTargetActionInstance(targetAction2);
        actionLink2.setArea(area);

        when(actionLinkRepository.findBySourceActionInstanceIdWithTargetFetch(actionInstance.getId()))
                .thenReturn(List.of(actionLink1, actionLink2));
        when(actionLinkRepository.findBySourceActionInstanceIdWithTargetFetch(targetActionInstance.getId()))
                .thenReturn(Collections.emptyList());
        when(actionLinkRepository.findBySourceActionInstanceIdWithTargetFetch(targetAction2.getId()))
                .thenReturn(Collections.emptyList());
        when(executionService.createExecutionWithActivationType(
                any(ActionInstance.class),
                eq(ActivationModeType.MANUAL),
                eq(inputPayload),
                any(UUID.class)
        )).thenReturn(execution);

        // When
        executionTriggerService.triggerAreaExecution(actionInstance, ActivationModeType.WEBHOOK, inputPayload);

        // Then
        verify(actionLinkRepository).findBySourceActionInstanceIdWithTargetFetch(actionInstance.getId());
        verify(executionService, times(2)).createExecutionWithActivationType(
                any(ActionInstance.class),
                eq(ActivationModeType.MANUAL),
                eq(inputPayload),
                any(UUID.class)
        );
    }

    @Test
    void triggerAreaExecution_WithLinkedActionFailure_ShouldContinueWithOtherReactions() {
        // Given
        ActionInstance targetAction2 = new ActionInstance();
        targetAction2.setId(UUID.randomUUID());
        targetAction2.setName("Target Action 2");
        targetAction2.setArea(area);
        targetAction2.setActionDefinition(actionDefinition);

        ActionLink actionLink1 = new ActionLink();
        actionLink1.setSourceActionInstance(actionInstance);
        actionLink1.setTargetActionInstance(targetActionInstance);
        actionLink1.setArea(area);

        ActionLink actionLink2 = new ActionLink();
        actionLink2.setSourceActionInstance(actionInstance);
        actionLink2.setTargetActionInstance(targetAction2);
        actionLink2.setArea(area);

        when(actionLinkRepository.findBySourceActionInstanceIdWithTargetFetch(actionInstance.getId()))
                .thenReturn(List.of(actionLink1, actionLink2));
        when(actionLinkRepository.findBySourceActionInstanceIdWithTargetFetch(targetActionInstance.getId()))
                .thenThrow(new RuntimeException("Test error"));
        when(actionLinkRepository.findBySourceActionInstanceIdWithTargetFetch(targetAction2.getId()))
                .thenReturn(Collections.emptyList());
        when(executionService.createExecutionWithActivationType(
                eq(targetAction2),
                eq(ActivationModeType.MANUAL),
                eq(inputPayload),
                any(UUID.class)
        )).thenReturn(execution);

        // When
        executionTriggerService.triggerAreaExecution(actionInstance, ActivationModeType.WEBHOOK, inputPayload);

        // Then
        verify(actionLinkRepository).findBySourceActionInstanceIdWithTargetFetch(actionInstance.getId());
        verify(actionLinkRepository).findBySourceActionInstanceIdWithTargetFetch(targetActionInstance.getId());
        verify(actionLinkRepository).findBySourceActionInstanceIdWithTargetFetch(targetAction2.getId());
        
        // Should still process the second action despite first failure
        verify(executionService).createExecutionWithActivationType(
                eq(targetAction2),
                eq(ActivationModeType.MANUAL),
                eq(inputPayload),
                any(UUID.class)
        );
    }

    @Test
    void triggerAreaExecution_WithEmptyPayload_ShouldWork() {
        // Given
        Map<String, Object> emptyPayload = new HashMap<>();
        when(actionLinkRepository.findBySourceActionInstanceIdWithTargetFetch(actionInstance.getId()))
                .thenReturn(Collections.emptyList());
        when(executionService.createExecutionWithActivationType(
                eq(actionInstance),
                eq(ActivationModeType.MANUAL),
                eq(emptyPayload),
                any(UUID.class)
        )).thenReturn(execution);

        // When
        executionTriggerService.triggerAreaExecution(actionInstance, ActivationModeType.MANUAL, emptyPayload);

        // Then
        verify(executionService).createExecutionWithActivationType(
                eq(actionInstance),
                eq(ActivationModeType.MANUAL),
                eq(emptyPayload),
                any(UUID.class)
        );
        verify(redisEventService).publishAreaEvent(any(AreaEventMessage.class));
    }

    @Test
    void triggerAreaExecution_WhenExecutionServiceFails_ShouldThrowException() {
        // Given
        when(actionLinkRepository.findBySourceActionInstanceIdWithTargetFetch(actionInstance.getId()))
                .thenReturn(Collections.emptyList());
        when(executionService.createExecutionWithActivationType(
                any(),
                any(),
                any(),
                any()
        )).thenThrow(new RuntimeException("Database error"));

        // When & Then
        assertThrows(RuntimeException.class, () -> 
            executionTriggerService.triggerAreaExecution(actionInstance, ActivationModeType.MANUAL, inputPayload)
        );

        verify(redisEventService, never()).publishAreaEvent(any());
    }

    @Test
    void triggerAreaExecution_WhenRedisServiceFails_ShouldThrowException() {
        // Given
        when(actionLinkRepository.findBySourceActionInstanceIdWithTargetFetch(actionInstance.getId()))
                .thenReturn(Collections.emptyList());
        when(executionService.createExecutionWithActivationType(
                any(),
                any(),
                any(),
                any()
        )).thenReturn(execution);
        doThrow(new RuntimeException("Redis connection error"))
                .when(redisEventService).publishAreaEvent(any());

        // When & Then
        assertThrows(RuntimeException.class, () -> 
            executionTriggerService.triggerAreaExecution(actionInstance, ActivationModeType.MANUAL, inputPayload)
        );
    }

    @Test
    void triggerManualExecution_ShouldCreateExecutionAndPublishEvent() {
        // Given
        when(executionService.createExecutionWithActivationType(
                eq(actionInstance),
                eq(ActivationModeType.MANUAL),
                eq(inputPayload),
                any(UUID.class)
        )).thenReturn(execution);

        // When
        Execution result = executionTriggerService.triggerManualExecution(actionInstance, inputPayload);

        // Then
        assertNotNull(result);
        assertEquals(execution.getId(), result.getId());
        assertEquals(execution.getActionInstance(), result.getActionInstance());

        verify(executionService).createExecutionWithActivationType(
                eq(actionInstance),
                eq(ActivationModeType.MANUAL),
                eq(inputPayload),
                any(UUID.class)
        );

        ArgumentCaptor<AreaEventMessage> messageCaptor = ArgumentCaptor.forClass(AreaEventMessage.class);
        verify(redisEventService).publishAreaEvent(messageCaptor.capture());

        AreaEventMessage capturedMessage = messageCaptor.getValue();
        assertEquals(execution.getId(), capturedMessage.getExecutionId());
        assertEquals(actionInstance.getId(), capturedMessage.getActionInstanceId());
        assertEquals(area.getId(), capturedMessage.getAreaId());
        assertEquals("manual", capturedMessage.getEventType());
        assertEquals("manual_trigger", capturedMessage.getSource());
        assertEquals(inputPayload, capturedMessage.getPayload());
        assertNotNull(capturedMessage.getCorrelationId());
    }

    @Test
    void triggerManualExecution_WithEmptyPayload_ShouldWork() {
        // Given
        Map<String, Object> emptyPayload = new HashMap<>();
        when(executionService.createExecutionWithActivationType(
                eq(actionInstance),
                eq(ActivationModeType.MANUAL),
                eq(emptyPayload),
                any(UUID.class)
        )).thenReturn(execution);

        // When
        Execution result = executionTriggerService.triggerManualExecution(actionInstance, emptyPayload);

        // Then
        assertNotNull(result);
        verify(executionService).createExecutionWithActivationType(
                eq(actionInstance),
                eq(ActivationModeType.MANUAL),
                eq(emptyPayload),
                any(UUID.class)
        );
        verify(redisEventService).publishAreaEvent(any(AreaEventMessage.class));
    }

    @Test
    void triggerManualExecution_WhenExecutionServiceFails_ShouldThrowException() {
        // Given
        when(executionService.createExecutionWithActivationType(
                any(),
                any(),
                any(),
                any()
        )).thenThrow(new RuntimeException("Database error"));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> 
            executionTriggerService.triggerManualExecution(actionInstance, inputPayload)
        );

        assertTrue(exception.getMessage().contains("Failed to trigger manual execution"));
        verify(redisEventService, never()).publishAreaEvent(any());
    }

    @Test
    void triggerManualExecution_WhenRedisServiceFails_ShouldThrowException() {
        // Given
        when(executionService.createExecutionWithActivationType(
                any(),
                any(),
                any(),
                any()
        )).thenReturn(execution);
        doThrow(new RuntimeException("Redis connection error"))
                .when(redisEventService).publishAreaEvent(any());

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> 
            executionTriggerService.triggerManualExecution(actionInstance, inputPayload)
        );

        assertTrue(exception.getMessage().contains("Failed to trigger manual execution"));
    }

    @Test
    void triggerManualExecution_WithNullPayload_ShouldWork() {
        // Given
        when(executionService.createExecutionWithActivationType(
                eq(actionInstance),
                eq(ActivationModeType.MANUAL),
                isNull(),
                any(UUID.class)
        )).thenReturn(execution);

        // When
        Execution result = executionTriggerService.triggerManualExecution(actionInstance, null);

        // Then
        assertNotNull(result);
        verify(executionService).createExecutionWithActivationType(
                eq(actionInstance),
                eq(ActivationModeType.MANUAL),
                isNull(),
                any(UUID.class)
        );

        ArgumentCaptor<AreaEventMessage> messageCaptor = ArgumentCaptor.forClass(AreaEventMessage.class);
        verify(redisEventService).publishAreaEvent(messageCaptor.capture());

        AreaEventMessage capturedMessage = messageCaptor.getValue();
        assertNull(capturedMessage.getPayload());
    }

    @Test
    void triggerAreaExecution_WithCronActivation_ShouldUseCorrectEventType() {
        // Given
        when(actionLinkRepository.findBySourceActionInstanceIdWithTargetFetch(actionInstance.getId()))
                .thenReturn(Collections.emptyList());
        when(executionService.createExecutionWithActivationType(
                eq(actionInstance),
                eq(ActivationModeType.CRON),
                eq(inputPayload),
                any(UUID.class)
        )).thenReturn(execution);

        // When
        executionTriggerService.triggerAreaExecution(actionInstance, ActivationModeType.CRON, inputPayload);

        // Then
        ArgumentCaptor<AreaEventMessage> messageCaptor = ArgumentCaptor.forClass(AreaEventMessage.class);
        verify(redisEventService).publishAreaEvent(messageCaptor.capture());

        AreaEventMessage capturedMessage = messageCaptor.getValue();
        assertEquals("cron", capturedMessage.getEventType());
    }

    @Test
    void triggerAreaExecution_WithPollActivation_ShouldUseCorrectEventType() {
        // Given
        when(actionLinkRepository.findBySourceActionInstanceIdWithTargetFetch(actionInstance.getId()))
                .thenReturn(Collections.emptyList());
        when(executionService.createExecutionWithActivationType(
                eq(actionInstance),
                eq(ActivationModeType.POLL),
                eq(inputPayload),
                any(UUID.class)
        )).thenReturn(execution);

        // When
        executionTriggerService.triggerAreaExecution(actionInstance, ActivationModeType.POLL, inputPayload);

        // Then
        ArgumentCaptor<AreaEventMessage> messageCaptor = ArgumentCaptor.forClass(AreaEventMessage.class);
        verify(redisEventService).publishAreaEvent(messageCaptor.capture());

        AreaEventMessage capturedMessage = messageCaptor.getValue();
        assertEquals("poll", capturedMessage.getEventType());
    }

    @Test
    void triggerAreaExecution_WithComplexPayload_ShouldPreservePayloadStructure() {
        // Given
        Map<String, Object> complexPayload = new HashMap<>();
        complexPayload.put("user", Map.of("id", 123, "name", "John Doe"));
        complexPayload.put("items", List.of("item1", "item2", "item3"));
        complexPayload.put("metadata", Map.of("timestamp", 1234567890L, "version", "1.0"));

        when(actionLinkRepository.findBySourceActionInstanceIdWithTargetFetch(actionInstance.getId()))
                .thenReturn(Collections.emptyList());
        when(executionService.createExecutionWithActivationType(
                eq(actionInstance),
                eq(ActivationModeType.MANUAL),
                eq(complexPayload),
                any(UUID.class)
        )).thenReturn(execution);

        // When
        executionTriggerService.triggerAreaExecution(actionInstance, ActivationModeType.MANUAL, complexPayload);

        // Then
        ArgumentCaptor<AreaEventMessage> messageCaptor = ArgumentCaptor.forClass(AreaEventMessage.class);
        verify(redisEventService).publishAreaEvent(messageCaptor.capture());

        AreaEventMessage capturedMessage = messageCaptor.getValue();
        assertEquals(complexPayload, capturedMessage.getPayload());
    }
}
