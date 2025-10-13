package area.server.AREA_Back.service;

import area.server.AREA_Back.entity.ActionInstance;
import area.server.AREA_Back.entity.Area;
import area.server.AREA_Back.entity.Execution;
import area.server.AREA_Back.repository.ActionInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing reaction chains and sequential execution
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReactionChainService {

    private final ActionInstanceRepository actionInstanceRepository;
    private final ExecutionTriggerService executionTriggerService;
    private final DataMappingService dataMappingService;

    /**
     * Processes a chain of reactions for an AREA
     */
    public void processReactionChain(Area area, Map<String, Object> initialPayload, UUID correlationId) {
        log.info("Starting reaction chain processing for AREA: {}", area.getId());

        try {
            List<ActionInstance> reactions = actionInstanceRepository.findEnabledByArea(area)
                .stream()
                .filter(ai -> ai.getActionDefinition().getIsExecutable())
                .collect(Collectors.toList());

            if (reactions.isEmpty()) {
                log.info("No reactions found for AREA: {}", area.getId());
                return;
            }

            reactions.sort((r1, r2) -> {
                Integer order1 = (Integer) r1.getParams().getOrDefault("order", 0);
                Integer order2 = (Integer) r2.getParams().getOrDefault("order", 0);
                return order1.compareTo(order2);
            });

            log.info("Processing {} reactions in chain for AREA: {}", reactions.size(), area.getId());

            Map<String, Object> currentPayload = new HashMap<>(initialPayload);
            for (int i = 0; i < reactions.size(); i++) {
                ActionInstance reaction = reactions.get(i);
                try {
                    log.debug("Processing reaction {} of {}: {} ({})",
                            i + 1, reactions.size(), reaction.getName(), reaction.getId());

                    if (!shouldExecuteReaction(reaction, currentPayload)) {
                        log.debug("Skipping reaction {} due to conditions", reaction.getName());
                        continue;
                    }

                    Map<String, Object> mappedPayload = applyDataMapping(reaction, currentPayload);

                    Execution execution = executionTriggerService.triggerManualExecution(reaction, mappedPayload);

                    currentPayload = enrichPayloadWithExecutionResult(currentPayload, execution, reaction);

                    log.debug("Successfully processed reaction: {} (execution: {})",
                            reaction.getName(), execution.getId());

                } catch (Exception e) {
                    log.error("Failed to process reaction {} in chain for AREA {}: {}",
                            reaction.getName(), area.getId(), e.getMessage(), e);
                    boolean continueOnError = (Boolean) reaction.getParams().getOrDefault("continue_on_error", true);
                    if (!continueOnError) {
                        log.warn("Stopping reaction chain due to error in reaction: {}", reaction.getName());
                        break;
                    }
                }
            }

            log.info("Completed reaction chain processing for AREA: {}", area.getId());

        } catch (Exception e) {
            log.error("Failed to process reaction chain for AREA {}: {}", area.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to process reaction chain", e);
        }
    }

    /**
     * Checks if a reaction should be executed based on its conditions
     */
    private boolean shouldExecuteReaction(ActionInstance reaction, Map<String, Object> payload) {
        @SuppressWarnings("unchecked")
        Map<String, Object> condition = (Map<String, Object>) reaction.getParams().get("condition");
        if (condition == null || condition.isEmpty()) {
            return true;
        }

        try {
            return dataMappingService.evaluateCondition(payload, condition);
        } catch (Exception e) {
            log.warn("Failed to evaluate condition for reaction {}: {}", reaction.getName(), e.getMessage());
            return false;
        }
    }

    /**
     * Applies data mapping to transform the payload for a specific reaction
     */
    private Map<String, Object> applyDataMapping(ActionInstance reaction, Map<String, Object> payload) {
        @SuppressWarnings("unchecked")
        Map<String, Object> mapping = (Map<String, Object>) reaction.getParams().get("mapping");
        if (mapping == null || mapping.isEmpty()) {
            return payload;
        }

        try {
            return dataMappingService.applyMapping(payload, mapping);
        } catch (Exception e) {
            log.warn("Failed to apply mapping for reaction {}: {}", reaction.getName(), e.getMessage());
            return payload;
        }
    }

    /**
     * Enriches the payload with execution result for the next reaction in chain
     */
    private Map<String, Object> enrichPayloadWithExecutionResult(Map<String, Object> currentPayload,
                                                               Execution execution,
                                                               ActionInstance reaction) {
        Map<String, Object> enriched = new HashMap<>(currentPayload);
        enriched.put("previous_execution_id", execution.getId().toString());
        enriched.put("previous_reaction_name", reaction.getName());
        Object currentStep = enriched.getOrDefault("chain_step", 0);
        int stepNumber;
        if (currentStep instanceof Integer) {
            stepNumber = (Integer) currentStep;
        } else {
            stepNumber = 0;
        }
        enriched.put("chain_step", stepNumber + 1);
        enriched.put("previous_result", Map.of(
            "status", "queued",
            "execution_id", execution.getId().toString(),
            "reaction_type", reaction.getActionDefinition().getKey()
        ));
        return enriched;
    }

    /**
     * Triggers a chain reaction from a completed execution
     */
    public void triggerChainReaction(Execution completedExecution, Map<String, Object> executionResult) {
        try {
            ActionInstance sourceAction = completedExecution.getActionInstance();
            Area area = completedExecution.getArea();

            log.info("Triggering chain reaction for completed execution: {} in AREA: {}",
                    completedExecution.getId(), area.getId());

            // Prepare payload with execution result
            Map<String, Object> chainPayload = new HashMap<>(completedExecution.getInputPayload());
            chainPayload.put("trigger_execution_id", completedExecution.getId().toString());
            chainPayload.put("trigger_result", executionResult);
            chainPayload.put("source_action", sourceAction.getName());

            processReactionChain(area, chainPayload, completedExecution.getCorrelationId());

        } catch (Exception e) {
            log.error("Failed to trigger chain reaction for execution {}: {}",
                    completedExecution.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to trigger chain reaction", e);
        }
    }
}