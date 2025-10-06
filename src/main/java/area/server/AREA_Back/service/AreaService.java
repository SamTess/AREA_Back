package area.server.AREA_Back.service;

import area.server.AREA_Back.dto.AreaActionRequest;
import area.server.AREA_Back.dto.AreaReactionRequest;
import area.server.AREA_Back.dto.AreaResponse;
import area.server.AREA_Back.dto.CreateAreaWithActionsRequest;
import area.server.AREA_Back.entity.ActionDefinition;
import area.server.AREA_Back.entity.ActionInstance;
import area.server.AREA_Back.entity.Area;
import area.server.AREA_Back.entity.ServiceAccount;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.repository.ActionDefinitionRepository;
import area.server.AREA_Back.repository.ActionInstanceRepository;
import area.server.AREA_Back.repository.AreaRepository;
import area.server.AREA_Back.repository.ServiceAccountRepository;
import area.server.AREA_Back.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final ServiceAccountRepository serviceAccountRepository;
    private final JsonSchemaValidationService jsonSchemaValidationService;

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

            actionInstanceRepository.save(actionInstance);
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
}