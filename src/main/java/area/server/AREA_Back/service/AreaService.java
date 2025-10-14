package area.server.AREA_Back.service;

import area.server.AREA_Back.dto.AreaActionRequest;
import area.server.AREA_Back.dto.AreaReactionRequest;
import area.server.AREA_Back.dto.AreaResponse;
import area.server.AREA_Back.dto.CreateAreaWithActionsRequest;
import area.server.AREA_Back.dto.CreateAreaWithActionsAndLinksRequest;
import area.server.AREA_Back.entity.ActionDefinition;
import area.server.AREA_Back.entity.ActionInstance;
import area.server.AREA_Back.entity.ActivationMode;
import area.server.AREA_Back.entity.Area;
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
    private final ActionLinkService actionLinkService;

    private static final int DEFAULT_MAX_CONCURRENCY = 5;
    private static final int DEFAULT_RETRY_DELAY_SECONDS = 10;
    private static final int DEFAULT_TIMEOUT_SECONDS = 300;

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

            if (action.getActivationConfig() != null && !action.getActivationConfig().isEmpty()) {
                createActivationModes(savedActionInstance, action.getActivationConfig());
            } else {
                log.info("No activation config provided for action {}, skipping activation mode creation",
                    savedActionInstance.getId());
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

            ActionInstance savedReactionInstance = actionInstanceRepository.save(actionInstance);

            if (reaction.getActivationConfig() != null && !reaction.getActivationConfig().isEmpty()) {
                createActivationModes(savedReactionInstance, reaction.getActivationConfig());
            } else {
                ActivationMode chainActivationMode = new ActivationMode();
                chainActivationMode.setActionInstance(savedReactionInstance);
                chainActivationMode.setType(ActivationModeType.CHAIN);
                chainActivationMode.setEnabled(true);
                chainActivationMode.setDedup(DedupStrategy.NONE);
                chainActivationMode.setConfig(Map.of());
                chainActivationMode.setMaxConcurrency(DEFAULT_MAX_CONCURRENCY);
                activationModeRepository.save(chainActivationMode);
                log.info("Created default CHAIN activation mode for reaction: {}", savedReactionInstance.getId());
            }
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

    public Map<String, Object> triggerAreaManually(UUID areaId, Map<String, Object> inputPayload) {
        try {
            Area area = areaRepository.findById(areaId)
                .orElseThrow(() -> new IllegalArgumentException("Area not found: " + areaId));

            if (!area.getEnabled()) {
                throw new IllegalStateException("Area is disabled: " + areaId);
            }

            log.info("Manually triggering AREA: {} with payload: {}", areaId, inputPayload);

            return Map.of(
                "status", "triggered",
                "areaId", areaId,
                "timestamp", LocalDateTime.now().toString(),
                "payload", inputPayload
            );

        } catch (Exception e) {
            log.error("Failed to trigger AREA manually: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void validateActivationModeCompatibility(ActionDefinition actionDef, ActivationModeType activationMode) {
        boolean isEvent = actionDef.getIsEventCapable();
        boolean isExecutable = actionDef.getIsExecutable();

        if (isEvent && !isExecutable) {
            if (activationMode == ActivationModeType.CRON) {
                throw new IllegalArgumentException(
                    String.format("CRON activation mode is not compatible with event action '%s'. "
                                + "Events can only use WEBHOOK, POLL, or MANUAL activation modes.",
                                actionDef.getKey()));
            }
            if (activationMode == ActivationModeType.CHAIN) {
                throw new IllegalArgumentException(
                    String.format("CHAIN activation mode is not compatible with event action '%s'. "
                                + "Events are triggers, not reactions in a chain.",
                                actionDef.getKey()));
            }
        } else if (!isEvent && isExecutable) {
            if (activationMode == ActivationModeType.WEBHOOK) {
                throw new IllegalArgumentException(
                    String.format("WEBHOOK activation mode is not compatible with reaction action '%s'. "
                                + "Reactions should use CRON, MANUAL, or CHAIN activation modes.",
                                actionDef.getKey()));
            }
            if (activationMode == ActivationModeType.POLL) {
                throw new IllegalArgumentException(
                    String.format("POLL activation mode is not compatible with reaction action '%s'. "
                                + "Reactions should use CRON, MANUAL, or CHAIN activation modes.",
                                actionDef.getKey()));
            }
        } else if (isEvent && isExecutable) {
            log.info("Mixed action '{}' accepts any activation mode", actionDef.getKey());
        } else {
            throw new IllegalArgumentException(
                String.format("Invalid action definition '%s': must be either event-capable or executable",
                            actionDef.getKey()));
        }
    }

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

            ActionDefinition actionDef = actionInstance.getActionDefinition();
            validateActivationModeCompatibility(actionDef, activationModeType);

            ActivationMode activationMode = new ActivationMode();
            activationMode.setActionInstance(actionInstance);
            activationMode.setType(activationModeType);
            activationMode.setEnabled(true);
            activationMode.setDedup(DedupStrategy.NONE);

            Map<String, Object> config = new HashMap<>(activationConfig);
            config.remove("type");
            activationMode.setConfig(config);

            switch (activationModeType) {
                case WEBHOOK:
                    activationMode.setMaxConcurrency(
                            (Integer) config.getOrDefault("max_concurrency", DEFAULT_RETRY_DELAY_SECONDS));
                    break;
                case CRON:
                    if (!config.containsKey("cron_expression")) {
                        throw new IllegalArgumentException("CRON activation mode requires 'cron_expression'");
                    }
                    break;
                case POLL:
                    if (!config.containsKey("interval_seconds")) {
                        config.put("interval_seconds", DEFAULT_TIMEOUT_SECONDS);
                    }
                    break;
                case MANUAL:
                    break;
                case CHAIN:
                    activationMode.setMaxConcurrency(
                            (Integer) config.getOrDefault("max_concurrency", DEFAULT_MAX_CONCURRENCY));
                    break;
                default:
                    throw new IllegalArgumentException("Unknown activation mode type: " + activationModeType);
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

    public AreaResponse createAreaWithActionsAndLinks(CreateAreaWithActionsAndLinksRequest request) {
        log.info("Creating new AREA with links: {} for user: {}", request.getName(), request.getUserId());

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

        Map<String, UUID> serviceIdMapping = createActionInstancesWithMapping(
                savedArea, request.getActions(), request.getReactions());

        if (request.getConnections() != null && !request.getConnections().isEmpty()) {
            createActionLinks(savedArea, request.getConnections(), serviceIdMapping);
        } else {
            String layoutMode = request.getLayoutMode();
            if ("linear".equals(layoutMode)) {
                createLinearActionLinks(savedArea, request.getActions(),
                        request.getReactions(), serviceIdMapping);
            }
        }

        int connectionCount;
        if (request.getConnections() != null) {
            connectionCount = request.getConnections().size();
        } else {
            connectionCount = 0;
        }

        log.info("Successfully created AREA with ID: {} and {} links",
                savedArea.getId(), connectionCount);
        return convertToResponse(savedArea);
    }

    public AreaResponse updateAreaWithActionsAndLinks(UUID areaId, CreateAreaWithActionsAndLinksRequest request) {

        Area area = areaRepository.findById(areaId)
            .orElseThrow(() -> new IllegalArgumentException("Area not found with ID: " + areaId));

        if (!area.getUser().getId().equals(request.getUserId())) {
            throw new IllegalArgumentException("Area does not belong to user: " + request.getUserId());
        }

        validateActionsAndReactions(request.getActions(), request.getReactions());
        actionLinkService.deleteAllLinksForArea(areaId);
        actionInstanceRepository.deleteByAreaId(areaId);
        area.setName(request.getName());
        area.setDescription(request.getDescription());
        area.setActions(convertActionsToJsonb(request.getActions()));
        area.setReactions(convertReactionsToJsonb(request.getReactions()));

        Area savedArea = areaRepository.save(area);

        Map<String, UUID> serviceIdMapping = createActionInstancesWithMapping(
                savedArea, request.getActions(), request.getReactions());

        if (request.getConnections() != null && !request.getConnections().isEmpty()) {
            createActionLinks(savedArea, request.getConnections(), serviceIdMapping);
        } else {
            String layoutMode = request.getLayoutMode();
            if ("linear".equals(layoutMode)) {
                createLinearActionLinks(savedArea, request.getActions(),
                        request.getReactions(), serviceIdMapping);
            }
        }
        return convertToResponse(savedArea);
    }

    private Map<String, UUID> createActionInstancesWithMapping(Area area,
            List<AreaActionRequest> actions, List<AreaReactionRequest> reactions) {
        Map<String, UUID> serviceIdMapping = new HashMap<>();

        for (int i = 0; i < actions.size(); i++) {
            AreaActionRequest action = actions.get(i);
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
            serviceIdMapping.put("action_" + i, savedActionInstance.getId());

            if (action.getActivationConfig() != null && !action.getActivationConfig().isEmpty()) {
                createActivationModes(savedActionInstance, action.getActivationConfig());
            }
        }

        for (int i = 0; i < reactions.size(); i++) {
            AreaReactionRequest reaction = reactions.get(i);
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

            ActionInstance savedActionInstance = actionInstanceRepository.save(actionInstance);
            serviceIdMapping.put("reaction_" + i, savedActionInstance.getId());

            if (reaction.getActivationConfig() != null && !reaction.getActivationConfig().isEmpty()) {
                createActivationModes(savedActionInstance, reaction.getActivationConfig());
            }
        }

        return serviceIdMapping;
    }

    private void createActionLinks(Area area,
            List<CreateAreaWithActionsAndLinksRequest.ActionConnectionRequest> connections,
            Map<String, UUID> serviceIdMapping) {
        for (CreateAreaWithActionsAndLinksRequest.ActionConnectionRequest connection : connections) {
            UUID sourceActionId = serviceIdMapping.get(connection.getSourceServiceId());
            UUID targetActionId = serviceIdMapping.get(connection.getTargetServiceId());

            if (sourceActionId != null && targetActionId != null) {
                try {
                    area.server.AREA_Back.dto.CreateActionLinkRequest linkRequest =
                        new area.server.AREA_Back.dto.CreateActionLinkRequest();
                    linkRequest.setSourceActionInstanceId(sourceActionId);
                    linkRequest.setTargetActionInstanceId(targetActionId);
                    linkRequest.setLinkType(connection.getLinkType());
                    linkRequest.setMapping(connection.getMapping());
                    linkRequest.setCondition(connection.getCondition());
                    linkRequest.setOrder(connection.getOrder());

                    actionLinkService.createActionLink(linkRequest, area.getId());
                    log.debug("Created link: {} -> {}", sourceActionId, targetActionId);
                } catch (Exception e) {
                    log.error("Failed to create action link from {} to {}: {}",
                            sourceActionId, targetActionId, e.getMessage());
                }
            } else {
                log.warn("Cannot create link: source {} or target {} not found in mapping",
                        connection.getSourceServiceId(), connection.getTargetServiceId());
            }
        }
    }

    private void createLinearActionLinks(Area area, List<AreaActionRequest> actions,
            List<AreaReactionRequest> reactions, Map<String, UUID> serviceIdMapping) {
        List<String> serviceOrder = new ArrayList<>();

        for (int i = 0; i < actions.size(); i++) {
            serviceOrder.add("action_" + i);
        }

        for (int i = 0; i < reactions.size(); i++) {
            serviceOrder.add("reaction_" + i);
        }

        for (int i = 0; i < serviceOrder.size() - 1; i++) {
            String sourceServiceId = serviceOrder.get(i);
            String targetServiceId = serviceOrder.get(i + 1);

            UUID sourceActionId = serviceIdMapping.get(sourceServiceId);
            UUID targetActionId = serviceIdMapping.get(targetServiceId);

            if (sourceActionId != null && targetActionId != null) {
                try {
                    area.server.AREA_Back.dto.CreateActionLinkRequest linkRequest =
                        new area.server.AREA_Back.dto.CreateActionLinkRequest();
                    linkRequest.setSourceActionInstanceId(sourceActionId);
                    linkRequest.setTargetActionInstanceId(targetActionId);
                    linkRequest.setLinkType("chain");
                    linkRequest.setOrder(i);

                    actionLinkService.createActionLink(linkRequest, area.getId());
                    log.debug("Created linear link {}: {} -> {}", i, sourceActionId, targetActionId);
                } catch (Exception e) {
                    log.error("Failed to create linear action link from {} to {}: {}",
                            sourceActionId, targetActionId, e.getMessage());
                }
            }
        }
    }
}