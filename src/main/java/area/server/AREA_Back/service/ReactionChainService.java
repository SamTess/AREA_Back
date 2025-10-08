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
            // Get all enabled action instances (reactions) for this area
            List<ActionInstance> reactions = actionInstanceRepository.findEnabledByArea(area)
                .stream()
                .filter(ai -> ai.getActionDefinition().getIsExecutable()) // Only executable actions (reactions)
                .collect(Collectors.toList());

            if (reactions.isEmpty()) {
                log.info("No reactions found for AREA: {}", area.getId());
                return;
            }

            // Sort reactions by order if specified in their params
            reactions.sort((r1, r2) -> {
                Integer order1 = (Integer) r1.getParams().getOrDefault("order", 0);
                Integer order2 = (Integer) r2.getParams().getOrDefault("order", 0);
                return order1.compareTo(order2);
            });

            log.info("Processing {} reactions in chain for AREA: {}", reactions.size(), area.getId());

            // Process reactions in sequence
            Map<String, Object> currentPayload = new HashMap<>(initialPayload);
            
            for (int i = 0; i < reactions.size(); i++) {
                ActionInstance reaction = reactions.get(i);
                
                try {
                    log.debug("Processing reaction {} of {}: {} ({})", 
                            i + 1, reactions.size(), reaction.getName(), reaction.getId());

                    // Check if reaction should be executed based on conditions
                    if (!shouldExecuteReaction(reaction, currentPayload)) {
                        log.debug("Skipping reaction {} due to conditions", reaction.getName());
                        continue;
                    }

                    // Apply data mapping if configured
                    Map<String, Object> mappedPayload = applyDataMapping(reaction, currentPayload);

                    // Trigger the reaction execution
                    Execution execution = executionTriggerService.triggerManualExecution(reaction, mappedPayload);

                    // Update payload for next reaction in chain
                    currentPayload = enrichPayloadWithExecutionResult(currentPayload, execution, reaction);

                    log.debug("Successfully processed reaction: {} (execution: {})", 
                            reaction.getName(), execution.getId());

                } catch (Exception e) {
                    log.error("Failed to process reaction {} in chain for AREA {}: {}", 
                            reaction.getName(), area.getId(), e.getMessage(), e);
                    
                    // Check if we should continue or stop the chain on error
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
            return true; // No conditions means always execute
        }

        try {
            return dataMappingService.evaluateCondition(payload, condition);
        } catch (Exception e) {
            log.warn("Failed to evaluate condition for reaction {}: {}", reaction.getName(), e.getMessage());
            return false; // Fail safe - don't execute if condition evaluation fails
        }
    }

    /**
     * Applies data mapping to transform the payload for a specific reaction
     */
    private Map<String, Object> applyDataMapping(ActionInstance reaction, Map<String, Object> payload) {
        @SuppressWarnings("unchecked")
        Map<String, Object> mapping = (Map<String, Object>) reaction.getParams().get("mapping");
        
        if (mapping == null || mapping.isEmpty()) {
            return payload; // No mapping means use payload as-is
        }

        try {
            return dataMappingService.applyMapping(payload, mapping);
        } catch (Exception e) {
            log.warn("Failed to apply mapping for reaction {}: {}", reaction.getName(), e.getMessage());
            return payload; // Fall back to original payload
        }
    }

    /**
     * Enriches the payload with execution result for the next reaction in chain
     */
    private Map<String, Object> enrichPayloadWithExecutionResult(Map<String, Object> currentPayload, 
                                                               Execution execution, 
                                                               ActionInstance reaction) {
        Map<String, Object> enriched = new HashMap<>(currentPayload);
        
        // Add execution metadata
        enriched.put("previous_execution_id", execution.getId().toString());
        enriched.put("previous_reaction_name", reaction.getName());
        
        // Handle chain step increment safely
        Object currentStep = enriched.getOrDefault("chain_step", 0);
        int stepNumber;
        if (currentStep instanceof Integer) {
            stepNumber = (Integer) currentStep;
        } else {
            stepNumber = 0;
        }
        enriched.put("chain_step", stepNumber + 1);
        
        // Add execution result placeholder (would be populated after execution completes)
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

            // Process the chain
            processReactionChain(area, chainPayload, completedExecution.getCorrelationId());

        } catch (Exception e) {
            log.error("Failed to trigger chain reaction for execution {}: {}", 
                    completedExecution.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to trigger chain reaction", e);
        }
    }
}