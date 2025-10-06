package area.server.AREA_Back.service;

import area.server.AREA_Back.dto.AreaActionRequest;
import area.server.AREA_Back.dto.AreaReactionRequest;
import area.server.AREA_Back.dto.AreaResponse;
import area.server.AREA_Back.dto.CreateAreaWithActionsRequest;
import area.server.AREA_Back.entity.ActionDefinition;
import area.server.AREA_Back.entity.ActionInstance;
import area.server.AREA_Back.entity.ActivationMode;
import area.server.AREA_Back.entity.Area;
import area.server.AREA_Back.entity.Execution;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AreaService {

    private final AreaRepository areaRepository;
    private final UserRepository userRepository;
    private final ActionDefinitionRepository actionDefinitionRepository;
    private final ActionInstanceRepository actionInstanceRepository;
    private final ActivationModeRepository activationModeRepository;
    private final ServiceAccountRepository serviceAccountRepository;
    private final JsonSchemaValidationService jsonSchemaValidationService;
    private final ExecutionTriggerService executionTriggerService;

    /**
     * Creates a new AREA with actions and reactions.
     *
     * @param request the area creation request
     * @return the created area response
     * @throws IllegalArgumentException if validation fails
     */
    public AreaResponse createAreaWithActions(CreateAreaWithActionsRequest request) {
        log.info("Creating new AREA: {} for user: {}", request.getName(), request.getUserId());

        User user = userRepository.findById(request.getUserId())
            .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + request.getUserId()));

        validateActionsAndReactions(request.getActions(), request.getReactions());

        Area area = new Area();
        area.setName(request.getName());
        area.setDescription(request.getDescription());
        area.setUser(user);
        area.setEnabled(true);

        area.setActions(convertActionsToJsonb(request.getActions()));
        area.setReactions(convertReactionsToJsonb(request.getReactions()));

        Area savedArea = areaRepository.save(area);

        createActionInstances(savedArea, request.getActions(), request.getReactions());

        log.info("Successfully created AREA with ID: {}", savedArea.getId());
        return convertToResponse(savedArea);
    }

    private void validateActionsAndReactions(List<AreaActionRequest> actions, List<AreaReactionRequest> reactions) {
        for (AreaActionRequest action : actions) {
            ActionDefinition actionDef = actionDefinitionRepository.findById(action.getActionDefinitionId())
                .orElseThrow(() -> new IllegalArgumentException(
                    "Action definition not found: " + action.getActionDefinitionId()));

            if (!actionDef.getIsEventCapable()) {
                throw new IllegalArgumentException(
                    "Action definition must be event capable (trigger): " + actionDef.getName());
            }

            jsonSchemaValidationService.validateParameters(
                actionDef.getInputSchema(), action.getParameters());

            if (action.getServiceAccountId() != null) {
                validateServiceAccount(action.getServiceAccountId(), actionDef.getService().getId());
            }
        }

        for (AreaReactionRequest reaction : reactions) {
            ActionDefinition actionDef = actionDefinitionRepository.findById(reaction.getActionDefinitionId())
                .orElseThrow(() -> new IllegalArgumentException(
                    "Action definition not found: " + reaction.getActionDefinitionId()));

            if (!actionDef.getIsExecutable()) {
                throw new IllegalArgumentException(
                    "Action definition must be executable (reaction): " + actionDef.getName());
            }

            jsonSchemaValidationService.validateParameters(
                actionDef.getInputSchema(), reaction.getParameters());

            if (reaction.getServiceAccountId() != null) {
                validateServiceAccount(reaction.getServiceAccountId(), actionDef.getService().getId());
            }
        }
    }

    private void validateServiceAccount(UUID serviceAccountId, UUID serviceId) {
        ServiceAccount serviceAccount = serviceAccountRepository.findById(serviceAccountId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Service account not found: " + serviceAccountId));

        if (!serviceAccount.getService().getId().equals(serviceId)) {
            throw new IllegalArgumentException(
                "Service account does not match the required service");
        }
    }

    private List<Map<String, Object>> convertActionsToJsonb(List<AreaActionRequest> actions) {
        List<Map<String, Object>> jsonbActions = new ArrayList<>();
        for (AreaActionRequest action : actions) {
            Map<String, Object> jsonAction = new HashMap<>();
            jsonAction.put("actionDefinitionId", action.getActionDefinitionId().toString());
            jsonAction.put("name", action.getName());
            jsonAction.put("description", action.getDescription());

            Map<String, Object> parameters = action.getParameters();
            if (parameters != null) {
                jsonAction.put("parameters", parameters);
            } else {
                jsonAction.put("parameters", Map.of());
            }

            Map<String, Object> activationConfig = action.getActivationConfig();
            if (activationConfig != null) {
                jsonAction.put("activationConfig", activationConfig);
            } else {
                jsonAction.put("activationConfig", Map.of());
            }

            if (action.getServiceAccountId() != null) {
                jsonAction.put("serviceAccountId", action.getServiceAccountId().toString());
            }
            jsonbActions.add(jsonAction);
        }
        return jsonbActions;
    }

    private List<Map<String, Object>> convertReactionsToJsonb(List<AreaReactionRequest> reactions) {
        List<Map<String, Object>> jsonbReactions = new ArrayList<>();
        for (AreaReactionRequest reaction : reactions) {
            Map<String, Object> jsonReaction = new HashMap<>();
            jsonReaction.put("actionDefinitionId", reaction.getActionDefinitionId().toString());
            jsonReaction.put("name", reaction.getName());
            jsonReaction.put("description", reaction.getDescription());

            Map<String, Object> parameters = reaction.getParameters();
            if (parameters != null) {
                jsonReaction.put("parameters", parameters);
            } else {
                jsonReaction.put("parameters", Map.of());
            }

            Map<String, Object> mapping = reaction.getMapping();
            if (mapping != null) {
                jsonReaction.put("mapping", mapping);
            } else {
                jsonReaction.put("mapping", Map.of());
            }

            Map<String, Object> condition = reaction.getCondition();
            if (condition != null) {
                jsonReaction.put("condition", condition);
            } else {
                jsonReaction.put("condition", Map.of());
            }

            jsonReaction.put("order", reaction.getOrder());
            if (reaction.getServiceAccountId() != null) {
                jsonReaction.put("serviceAccountId", reaction.getServiceAccountId().toString());
            }
            jsonbReactions.add(jsonReaction);
        }
        return jsonbReactions;
    }

    private void createActionInstances(Area area, List<AreaActionRequest> actions,
                                      List<AreaReactionRequest> reactions) {
        for (AreaActionRequest action : actions) {
            ActionDefinition actionDef = actionDefinitionRepository
                .findById(action.getActionDefinitionId()).get();
            ServiceAccount serviceAccount = null;
            if (action.getServiceAccountId() != null) {
                Optional<ServiceAccount> optionalServiceAccount = serviceAccountRepository
                    .findById(action.getServiceAccountId());
                serviceAccount = optionalServiceAccount.orElse(null);
            }

            ActionInstance actionInstance = new ActionInstance();
            actionInstance.setUser(area.getUser());
            actionInstance.setArea(area);
            actionInstance.setActionDefinition(actionDef);
            actionInstance.setServiceAccount(serviceAccount);
            actionInstance.setName(action.getName());
            actionInstance.setEnabled(true);

            Map<String, Object> params = action.getParameters();
            if (params != null) {
                actionInstance.setParams(params);
            } else {
                actionInstance.setParams(Map.of());
            }

            ActionInstance savedActionInstance = actionInstanceRepository.save(actionInstance);
            
            // Create activation modes if specified
            if (action.getActivationConfig() != null) {
                createActivationModes(savedActionInstance, action.getActivationConfig());
            }
        }

        for (AreaReactionRequest reaction : reactions) {
            ActionDefinition actionDef = actionDefinitionRepository
                .findById(reaction.getActionDefinitionId()).get();
            ServiceAccount serviceAccount = null;
            if (reaction.getServiceAccountId() != null) {
                Optional<ServiceAccount> optionalServiceAccount = serviceAccountRepository
                    .findById(reaction.getServiceAccountId());
                serviceAccount = optionalServiceAccount.orElse(null);
            }

            ActionInstance actionInstance = new ActionInstance();
            actionInstance.setUser(area.getUser());
            actionInstance.setArea(area);
            actionInstance.setActionDefinition(actionDef);
            actionInstance.setServiceAccount(serviceAccount);
            actionInstance.setName(reaction.getName());
            actionInstance.setEnabled(true);

            Map<String, Object> params = reaction.getParameters();
            if (params != null) {
                actionInstance.setParams(params);
            } else {
                actionInstance.setParams(Map.of());
            }

            actionInstanceRepository.save(actionInstance);
        }
    }

    public AreaResponse convertToResponse(Area area) {
        AreaResponse response = new AreaResponse();
        response.setId(area.getId());
        response.setName(area.getName());
        response.setDescription(area.getDescription());
        response.setEnabled(area.getEnabled());
        response.setUserId(area.getUser().getId());
        response.setUserEmail(area.getUser().getEmail());
        response.setActions(area.getActions());
        response.setReactions(area.getReactions());
        response.setCreatedAt(area.getCreatedAt());
        response.setUpdatedAt(area.getUpdatedAt());
        return response;
    }

    /**
     * Manually triggers execution of an AREA
     */
    public Map<String, Object> triggerAreaManually(UUID areaId, Map<String, Object> inputPayload) {
        log.info("Manually triggering AREA: {}", areaId);

        Area area = areaRepository.findById(areaId)
            .orElseThrow(() -> new IllegalArgumentException("AREA not found with ID: " + areaId));

        if (!area.getEnabled()) {
            throw new IllegalArgumentException("AREA is disabled: " + areaId);
        }

        // Get action instances for this area (actions are triggers)
        List<ActionInstance> actionInstances = actionInstanceRepository.findEnabledByArea(area);
        
        if (actionInstances.isEmpty()) {
            throw new IllegalArgumentException("No enabled action instances found for AREA: " + areaId);
        }

        // Use the first action instance as trigger for manual execution
        ActionInstance triggerAction = actionInstances.get(0);

        // Prepare input payload
        Map<String, Object> payload = inputPayload != null ? inputPayload : Map.of(
            "triggered_by", "manual",
            "trigger_time", LocalDateTime.now().toString()
        );

        // Trigger execution
        Execution execution = executionTriggerService.triggerManualExecution(triggerAction, payload);

        return Map.of(
            "status", "triggered",
            "areaId", areaId,
            "executionId", execution.getId(),
            "message", "AREA execution triggered successfully",
            "timestamp", LocalDateTime.now().toString()
        );
    }

    /**
     * Creates activation modes for an action instance
     */
    private void createActivationModes(ActionInstance actionInstance, Map<String, Object> activationConfig) {
        if (activationConfig == null || activationConfig.isEmpty()) {
            return;
        }

        String type = (String) activationConfig.get("type");
        if (type == null) {
            log.warn("No activation type specified for action instance: {}", actionInstance.getId());
            return;
        }

        try {
            ActivationModeType activationModeType = ActivationModeType.valueOf(type.toUpperCase());
            
            ActivationMode activationMode = new ActivationMode();
            activationMode.setActionInstance(actionInstance);
            activationMode.setType(activationModeType);
            activationMode.setEnabled(true);
            activationMode.setDedup(DedupStrategy.NONE);
            
            // Copy configuration
            Map<String, Object> config = new HashMap<>(activationConfig);
            config.remove("type"); // Remove type as it's stored separately
            activationMode.setConfig(config);
            
            // Set specific configurations based on type
            switch (activationModeType) {
                case WEBHOOK:
                    // Webhook specific configuration
                    activationMode.setMaxConcurrency((Integer) config.getOrDefault("max_concurrency", 10));
                    break;
                case CRON:
                    // CRON specific configuration
                    if (!config.containsKey("cron_expression")) {
                        throw new IllegalArgumentException("CRON activation mode requires 'cron_expression'");
                    }
                    break;
                case POLL:
                    // Polling specific configuration
                    if (!config.containsKey("interval_seconds")) {
                        config.put("interval_seconds", 300); // Default to 5 minutes
                    }
                    break;
                case MANUAL:
                    // Manual mode doesn't need specific config
                    break;
                case CHAIN:
                    // Chain mode configuration
                    activationMode.setMaxConcurrency((Integer) config.getOrDefault("max_concurrency", 5));
                    break;
            }
            
            activationModeRepository.save(activationMode);
            
            log.info("Created activation mode {} for action instance: {}", 
                    activationModeType, actionInstance.getId());
                    
        } catch (IllegalArgumentException e) {
            log.error("Invalid activation mode type '{}' for action instance {}: {}", 
                    type, actionInstance.getId(), e.getMessage());
            throw new IllegalArgumentException("Invalid activation mode type: " + type, e);
        }
    }
}