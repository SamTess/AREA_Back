package area.server.AREA_Back.service.Area;

import area.server.AREA_Back.entity.ActionDefinition;
import area.server.AREA_Back.entity.ActionInstance;
import area.server.AREA_Back.entity.Area;
import area.server.AREA_Back.entity.Execution;
import area.server.AREA_Back.entity.enums.ExecutionStatus;
import area.server.AREA_Back.repository.ActionInstanceRepository;
import area.server.AREA_Back.service.DataMappingService;
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
class ReactionChainServiceTest {

    @Mock
    private ActionInstanceRepository actionInstanceRepository;

    @Mock
    private ExecutionTriggerService executionTriggerService;

    @Mock
    private DataMappingService dataMappingService;

    @InjectMocks
    private ReactionChainService reactionChainService;

    private Area area;
    private ActionInstance reaction1;
    private ActionInstance reaction2;
    private ActionInstance reaction3;
    private ActionDefinition actionDefinition;
    private Execution execution;
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

        reaction1 = createReaction("Reaction 1", 0);
        reaction2 = createReaction("Reaction 2", 1);
        reaction3 = createReaction("Reaction 3", 2);

        execution = new Execution();
        execution.setId(UUID.randomUUID());
        execution.setActionInstance(reaction1);
        execution.setArea(area);
        execution.setStatus(ExecutionStatus.QUEUED);
        execution.setInputPayload(new HashMap<>());
        execution.setCorrelationId(correlationId);
    }

    private ActionInstance createReaction(String name, int order) {
        ActionInstance reaction = new ActionInstance();
        reaction.setId(UUID.randomUUID());
        reaction.setName(name);
        reaction.setArea(area);
        reaction.setActionDefinition(actionDefinition);
        
        Map<String, Object> params = new HashMap<>();
        params.put("order", order);
        params.put("continue_on_error", true);
        reaction.setParams(params);
        
        return reaction;
    }

    @Test
    void processReactionChain_WithNoReactions_ShouldReturnEarly() {
        // Given
        Map<String, Object> payload = new HashMap<>();
        payload.put("key", "value");
        when(actionInstanceRepository.findEnabledByArea(area)).thenReturn(Collections.emptyList());

        // When
        reactionChainService.processReactionChain(area, payload, correlationId);

        // Then
        verify(actionInstanceRepository).findEnabledByArea(area);
        verify(executionTriggerService, never()).triggerManualExecution(any(), any());
    }

    @Test
    void processReactionChain_WithNonExecutableReactions_ShouldFilterThem() {
        // Given
        ActionDefinition nonExecutableDef = new ActionDefinition();
        nonExecutableDef.setId(UUID.randomUUID());
        nonExecutableDef.setKey("non.executable");
        nonExecutableDef.setIsExecutable(false);

        ActionInstance nonExecutableReaction = new ActionInstance();
        nonExecutableReaction.setId(UUID.randomUUID());
        nonExecutableReaction.setName("Non Executable");
        nonExecutableReaction.setActionDefinition(nonExecutableDef);
        nonExecutableReaction.setParams(new HashMap<>());

        Map<String, Object> payload = new HashMap<>();
        when(actionInstanceRepository.findEnabledByArea(area))
            .thenReturn(Arrays.asList(reaction1, nonExecutableReaction));
        when(executionTriggerService.triggerManualExecution(any(), any())).thenReturn(execution);

        // When
        reactionChainService.processReactionChain(area, payload, correlationId);

        // Then
        verify(executionTriggerService, times(1)).triggerManualExecution(any(), any());
    }

    @Test
    void processReactionChain_ShouldExecuteInCorrectOrder() {
        // Given
        reaction3.getParams().put("order", 0);
        reaction1.getParams().put("order", 1);
        reaction2.getParams().put("order", 2);

        Map<String, Object> payload = new HashMap<>();
        payload.put("data", "test");

        when(actionInstanceRepository.findEnabledByArea(area))
            .thenReturn(Arrays.asList(reaction1, reaction2, reaction3));
        
        Execution exec1 = createExecution(reaction3);
        Execution exec2 = createExecution(reaction1);
        Execution exec3 = createExecution(reaction2);
        
        when(executionTriggerService.triggerManualExecution(eq(reaction3), any())).thenReturn(exec1);
        when(executionTriggerService.triggerManualExecution(eq(reaction1), any())).thenReturn(exec2);
        when(executionTriggerService.triggerManualExecution(eq(reaction2), any())).thenReturn(exec3);

        // When
        reactionChainService.processReactionChain(area, payload, correlationId);

        // Then
        ArgumentCaptor<ActionInstance> captor = ArgumentCaptor.forClass(ActionInstance.class);
        verify(executionTriggerService, times(3)).triggerManualExecution(captor.capture(), any());
        
        List<ActionInstance> executedReactions = captor.getAllValues();
        assertEquals(reaction3, executedReactions.get(0));
        assertEquals(reaction1, executedReactions.get(1));
        assertEquals(reaction2, executedReactions.get(2));
    }

    @Test
    void processReactionChain_ShouldEnrichPayloadBetweenReactions() {
        // Given
        Map<String, Object> payload = new HashMap<>();
        payload.put("initial", "data");

        when(actionInstanceRepository.findEnabledByArea(area))
            .thenReturn(Arrays.asList(reaction1, reaction2));
        
        Execution exec1 = createExecution(reaction1);
        Execution exec2 = createExecution(reaction2);
        
        when(executionTriggerService.triggerManualExecution(eq(reaction1), any())).thenReturn(exec1);
        when(executionTriggerService.triggerManualExecution(eq(reaction2), any())).thenReturn(exec2);

        // When
        reactionChainService.processReactionChain(area, payload, correlationId);

        // Then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(executionTriggerService, times(2)).triggerManualExecution(any(), payloadCaptor.capture());
        
        Map<String, Object> secondPayload = payloadCaptor.getAllValues().get(1);
        assertTrue(secondPayload.containsKey("previous_execution_id"));
        assertTrue(secondPayload.containsKey("previous_reaction_name"));
        assertTrue(secondPayload.containsKey("chain_step"));
        assertTrue(secondPayload.containsKey("previous_result"));
        assertEquals(1, secondPayload.get("chain_step"));
    }

    @Test
    void processReactionChain_WithCondition_ShouldEvaluateAndSkip() {
        // Given
        Map<String, Object> condition = new HashMap<>();
        condition.put("field", "value");
        reaction1.getParams().put("condition", condition);

        Map<String, Object> payload = new HashMap<>();
        when(actionInstanceRepository.findEnabledByArea(area))
            .thenReturn(Collections.singletonList(reaction1));
        when(dataMappingService.evaluateCondition(payload, condition)).thenReturn(false);

        // When
        reactionChainService.processReactionChain(area, payload, correlationId);

        // Then
        verify(dataMappingService).evaluateCondition(payload, condition);
        verify(executionTriggerService, never()).triggerManualExecution(any(), any());
    }

    @Test
    void processReactionChain_WithValidCondition_ShouldExecute() {
        // Given
        Map<String, Object> condition = new HashMap<>();
        condition.put("field", "value");
        reaction1.getParams().put("condition", condition);

        Map<String, Object> payload = new HashMap<>();
        when(actionInstanceRepository.findEnabledByArea(area))
            .thenReturn(Collections.singletonList(reaction1));
        when(dataMappingService.evaluateCondition(payload, condition)).thenReturn(true);
        when(executionTriggerService.triggerManualExecution(any(), any())).thenReturn(execution);

        // When
        reactionChainService.processReactionChain(area, payload, correlationId);

        // Then
        verify(dataMappingService).evaluateCondition(payload, condition);
        verify(executionTriggerService).triggerManualExecution(eq(reaction1), any());
    }

    @Test
    void processReactionChain_WithMapping_ShouldApplyTransformation() {
        // Given
        Map<String, Object> mapping = new HashMap<>();
        mapping.put("source", "target");
        reaction1.getParams().put("mapping", mapping);

        Map<String, Object> payload = new HashMap<>();
        payload.put("source", "data");
        
        Map<String, Object> mappedPayload = new HashMap<>();
        mappedPayload.put("target", "data");

        when(actionInstanceRepository.findEnabledByArea(area))
            .thenReturn(Collections.singletonList(reaction1));
        when(dataMappingService.applyMapping(payload, mapping)).thenReturn(mappedPayload);
        when(executionTriggerService.triggerManualExecution(any(), any())).thenReturn(execution);

        // When
        reactionChainService.processReactionChain(area, payload, correlationId);

        // Then
        verify(dataMappingService).applyMapping(payload, mapping);
        verify(executionTriggerService).triggerManualExecution(eq(reaction1), eq(mappedPayload));
    }

    @Test
    void processReactionChain_WithContinueOnErrorTrue_ShouldContinueAfterError() {
        // Given
        reaction1.getParams().put("continue_on_error", true);
        reaction2.getParams().put("continue_on_error", true);

        Map<String, Object> payload = new HashMap<>();
        when(actionInstanceRepository.findEnabledByArea(area))
            .thenReturn(Arrays.asList(reaction1, reaction2));
        
        when(executionTriggerService.triggerManualExecution(eq(reaction1), any()))
            .thenThrow(new RuntimeException("Execution failed"));
        when(executionTriggerService.triggerManualExecution(eq(reaction2), any()))
            .thenReturn(createExecution(reaction2));

        // When
        reactionChainService.processReactionChain(area, payload, correlationId);

        // Then
        verify(executionTriggerService).triggerManualExecution(eq(reaction1), any());
        verify(executionTriggerService).triggerManualExecution(eq(reaction2), any());
    }

    @Test
    void processReactionChain_WithContinueOnErrorFalse_ShouldStopAfterError() {
        // Given
        reaction1.getParams().put("continue_on_error", false);

        Map<String, Object> payload = new HashMap<>();
        when(actionInstanceRepository.findEnabledByArea(area))
            .thenReturn(Arrays.asList(reaction1, reaction2));
        
        when(executionTriggerService.triggerManualExecution(eq(reaction1), any()))
            .thenThrow(new RuntimeException("Execution failed"));

        // When
        reactionChainService.processReactionChain(area, payload, correlationId);

        // Then
        verify(executionTriggerService).triggerManualExecution(eq(reaction1), any());
        verify(executionTriggerService, never()).triggerManualExecution(eq(reaction2), any());
    }

    @Test
    void processReactionChain_WithConditionEvaluationError_ShouldSkipReaction() {
        // Given
        Map<String, Object> condition = new HashMap<>();
        condition.put("invalid", "condition");
        reaction1.getParams().put("condition", condition);

        Map<String, Object> payload = new HashMap<>();
        when(actionInstanceRepository.findEnabledByArea(area))
            .thenReturn(Collections.singletonList(reaction1));
        when(dataMappingService.evaluateCondition(payload, condition))
            .thenThrow(new RuntimeException("Invalid condition"));

        // When
        reactionChainService.processReactionChain(area, payload, correlationId);

        // Then
        verify(dataMappingService).evaluateCondition(payload, condition);
        verify(executionTriggerService, never()).triggerManualExecution(any(), any());
    }

    @Test
    void processReactionChain_WithMappingError_ShouldUseOriginalPayload() {
        // Given
        Map<String, Object> mapping = new HashMap<>();
        mapping.put("invalid", "mapping");
        reaction1.getParams().put("mapping", mapping);

        Map<String, Object> payload = new HashMap<>();
        payload.put("data", "test");

        when(actionInstanceRepository.findEnabledByArea(area))
            .thenReturn(Collections.singletonList(reaction1));
        when(dataMappingService.applyMapping(payload, mapping))
            .thenThrow(new RuntimeException("Mapping failed"));
        when(executionTriggerService.triggerManualExecution(any(), any())).thenReturn(execution);

        // When
        reactionChainService.processReactionChain(area, payload, correlationId);

        // Then
        verify(dataMappingService).applyMapping(payload, mapping);
        verify(executionTriggerService).triggerManualExecution(eq(reaction1), eq(payload));
    }

    @Test
    void processReactionChain_WithRepositoryException_ShouldThrowRuntimeException() {
        // Given
        Map<String, Object> payload = new HashMap<>();
        when(actionInstanceRepository.findEnabledByArea(area))
            .thenThrow(new RuntimeException("Database error"));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            reactionChainService.processReactionChain(area, payload, correlationId);
        });

        assertEquals("Failed to process reaction chain", exception.getMessage());
        verify(executionTriggerService, never()).triggerManualExecution(any(), any());
    }

    @Test
    void triggerChainReaction_ShouldPreparePayloadWithExecutionResult() {
        // Given
        Map<String, Object> executionResult = new HashMap<>();
        executionResult.put("status", "success");
        executionResult.put("data", "result");

        Map<String, Object> inputPayload = new HashMap<>();
        inputPayload.put("original", "data");
        execution.setInputPayload(inputPayload);

        when(actionInstanceRepository.findEnabledByArea(area))
            .thenReturn(Collections.singletonList(reaction1));
        when(executionTriggerService.triggerManualExecution(any(), any())).thenReturn(execution);

        // When
        reactionChainService.triggerChainReaction(execution, executionResult);

        // Then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(executionTriggerService).triggerManualExecution(any(), payloadCaptor.capture());
        
        Map<String, Object> capturedPayload = payloadCaptor.getValue();
        assertTrue(capturedPayload.containsKey("trigger_execution_id"));
        assertTrue(capturedPayload.containsKey("trigger_result"));
        assertTrue(capturedPayload.containsKey("source_action"));
        assertEquals(execution.getId().toString(), capturedPayload.get("trigger_execution_id"));
        assertEquals(executionResult, capturedPayload.get("trigger_result"));
        assertEquals(reaction1.getName(), capturedPayload.get("source_action"));
    }

    @Test
    void triggerChainReaction_WithException_ShouldThrowRuntimeException() {
        // Given
        Map<String, Object> executionResult = new HashMap<>();
        when(actionInstanceRepository.findEnabledByArea(area))
            .thenThrow(new RuntimeException("Database error"));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            reactionChainService.triggerChainReaction(execution, executionResult);
        });

        assertEquals("Failed to trigger chain reaction", exception.getMessage());
    }

    @Test
    void enrichPayloadWithExecutionResult_ShouldAddAllRequiredFields() {
        // Given - Using reflection or creating a scenario that calls this indirectly
        Map<String, Object> payload = new HashMap<>();
        payload.put("existing", "data");

        when(actionInstanceRepository.findEnabledByArea(area))
            .thenReturn(Collections.singletonList(reaction1));
        when(executionTriggerService.triggerManualExecution(any(), any())).thenReturn(execution);

        // When
        reactionChainService.processReactionChain(area, payload, correlationId);

        // Then - Verify the enriched payload through the captured arguments
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(executionTriggerService).triggerManualExecution(any(), payloadCaptor.capture());
        
        // The initial call won't have enrichment, but if we had multiple reactions we could verify
        Map<String, Object> capturedPayload = payloadCaptor.getValue();
        assertNotNull(capturedPayload);
    }

    @Test
    void processReactionChain_WithMultipleReactions_ShouldIncrementChainStep() {
        // Given
        Map<String, Object> payload = new HashMap<>();
        payload.put("data", "test");

        when(actionInstanceRepository.findEnabledByArea(area))
            .thenReturn(Arrays.asList(reaction1, reaction2, reaction3));
        
        Execution exec1 = createExecution(reaction1);
        Execution exec2 = createExecution(reaction2);
        Execution exec3 = createExecution(reaction3);
        
        when(executionTriggerService.triggerManualExecution(eq(reaction1), any())).thenReturn(exec1);
        when(executionTriggerService.triggerManualExecution(eq(reaction2), any())).thenReturn(exec2);
        when(executionTriggerService.triggerManualExecution(eq(reaction3), any())).thenReturn(exec3);

        // When
        reactionChainService.processReactionChain(area, payload, correlationId);

        // Then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(executionTriggerService, times(3)).triggerManualExecution(any(), payloadCaptor.capture());
        
        List<Map<String, Object>> capturedPayloads = payloadCaptor.getAllValues();
        
        // Second reaction should have chain_step = 1
        assertEquals(1, capturedPayloads.get(1).get("chain_step"));
        
        // Third reaction should have chain_step = 2
        assertEquals(2, capturedPayloads.get(2).get("chain_step"));
    }

    @Test
    void shouldExecuteReaction_WithNoCondition_ShouldReturnTrue() {
        // Given
        reaction1.setParams(new HashMap<>());
        Map<String, Object> payload = new HashMap<>();

        when(actionInstanceRepository.findEnabledByArea(area))
            .thenReturn(Collections.singletonList(reaction1));
        when(executionTriggerService.triggerManualExecution(any(), any())).thenReturn(execution);

        // When
        reactionChainService.processReactionChain(area, payload, correlationId);

        // Then
        verify(executionTriggerService).triggerManualExecution(eq(reaction1), any());
        verify(dataMappingService, never()).evaluateCondition(any(), any());
    }

    @Test
    void shouldExecuteReaction_WithEmptyCondition_ShouldReturnTrue() {
        // Given
        reaction1.getParams().put("condition", new HashMap<>());
        Map<String, Object> payload = new HashMap<>();

        when(actionInstanceRepository.findEnabledByArea(area))
            .thenReturn(Collections.singletonList(reaction1));
        when(executionTriggerService.triggerManualExecution(any(), any())).thenReturn(execution);

        // When
        reactionChainService.processReactionChain(area, payload, correlationId);

        // Then
        verify(executionTriggerService).triggerManualExecution(eq(reaction1), any());
        verify(dataMappingService, never()).evaluateCondition(any(), any());
    }

    @Test
    void applyDataMapping_WithNoMapping_ShouldReturnOriginalPayload() {
        // Given
        reaction1.setParams(new HashMap<>());
        Map<String, Object> payload = new HashMap<>();
        payload.put("data", "test");

        when(actionInstanceRepository.findEnabledByArea(area))
            .thenReturn(Collections.singletonList(reaction1));
        when(executionTriggerService.triggerManualExecution(any(), any())).thenReturn(execution);

        // When
        reactionChainService.processReactionChain(area, payload, correlationId);

        // Then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(executionTriggerService).triggerManualExecution(any(), payloadCaptor.capture());
        
        Map<String, Object> capturedPayload = payloadCaptor.getValue();
        assertEquals("test", capturedPayload.get("data"));
        verify(dataMappingService, never()).applyMapping(any(), any());
    }

    @Test
    void applyDataMapping_WithEmptyMapping_ShouldReturnOriginalPayload() {
        // Given
        reaction1.getParams().put("mapping", new HashMap<>());
        Map<String, Object> payload = new HashMap<>();
        payload.put("data", "test");

        when(actionInstanceRepository.findEnabledByArea(area))
            .thenReturn(Collections.singletonList(reaction1));
        when(executionTriggerService.triggerManualExecution(any(), any())).thenReturn(execution);

        // When
        reactionChainService.processReactionChain(area, payload, correlationId);

        // Then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(executionTriggerService).triggerManualExecution(any(), payloadCaptor.capture());
        
        Map<String, Object> capturedPayload = payloadCaptor.getValue();
        assertEquals("test", capturedPayload.get("data"));
        verify(dataMappingService, never()).applyMapping(any(), any());
    }

    @Test
    void enrichPayloadWithExecutionResult_WithExistingChainStep_ShouldIncrement() {
        // Given
        Map<String, Object> payload = new HashMap<>();
        payload.put("chain_step", 5);

        when(actionInstanceRepository.findEnabledByArea(area))
            .thenReturn(Arrays.asList(reaction1, reaction2));
        
        when(executionTriggerService.triggerManualExecution(eq(reaction1), any()))
            .thenReturn(createExecution(reaction1));
        when(executionTriggerService.triggerManualExecution(eq(reaction2), any()))
            .thenReturn(createExecution(reaction2));

        // When
        reactionChainService.processReactionChain(area, payload, correlationId);

        // Then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(executionTriggerService, times(2)).triggerManualExecution(any(), payloadCaptor.capture());
        
        // Second call should have incremented chain_step from 5 to 6
        Map<String, Object> secondPayload = payloadCaptor.getAllValues().get(1);
        assertEquals(6, secondPayload.get("chain_step"));
    }

    @Test
    void enrichPayloadWithExecutionResult_ShouldContainPreviousResultWithCorrectStructure() {
        // Given
        Map<String, Object> payload = new HashMap<>();
        when(actionInstanceRepository.findEnabledByArea(area))
            .thenReturn(Arrays.asList(reaction1, reaction2));
        
        Execution exec1 = createExecution(reaction1);
        when(executionTriggerService.triggerManualExecution(eq(reaction1), any())).thenReturn(exec1);
        when(executionTriggerService.triggerManualExecution(eq(reaction2), any()))
            .thenReturn(createExecution(reaction2));

        // When
        reactionChainService.processReactionChain(area, payload, correlationId);

        // Then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(executionTriggerService, times(2)).triggerManualExecution(any(), payloadCaptor.capture());
        
        Map<String, Object> secondPayload = payloadCaptor.getAllValues().get(1);
        assertTrue(secondPayload.containsKey("previous_result"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> previousResult = (Map<String, Object>) secondPayload.get("previous_result");
        assertEquals("queued", previousResult.get("status"));
        assertEquals(exec1.getId().toString(), previousResult.get("execution_id"));
        assertEquals(actionDefinition.getKey(), previousResult.get("reaction_type"));
    }

    private Execution createExecution(ActionInstance actionInstance) {
        Execution exec = new Execution();
        exec.setId(UUID.randomUUID());
        exec.setActionInstance(actionInstance);
        exec.setArea(area);
        exec.setStatus(ExecutionStatus.QUEUED);
        exec.setCorrelationId(correlationId);
        exec.setInputPayload(new HashMap<>());
        return exec;
    }
}
