package area.server.AREA_Back.service.Area;

import area.server.AREA_Back.dto.ActionLinkResponse;
import area.server.AREA_Back.dto.BatchCreateActionLinksRequest;
import area.server.AREA_Back.dto.CreateActionLinkRequest;
import area.server.AREA_Back.entity.ActionInstance;
import area.server.AREA_Back.entity.ActionLink;
import area.server.AREA_Back.entity.Area;
import area.server.AREA_Back.entity.Execution;
import area.server.AREA_Back.repository.ActionInstanceRepository;
import area.server.AREA_Back.repository.ActionLinkRepository;
import area.server.AREA_Back.repository.AreaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActionLinkService {

    private final ActionLinkRepository actionLinkRepository;
    private final ActionInstanceRepository actionInstanceRepository;
    private final AreaRepository areaRepository;

    @Transactional
    public ActionLinkResponse createActionLink(CreateActionLinkRequest request, UUID areaId) {
        Area area = areaRepository.findById(areaId)
                .orElseThrow(() -> new RuntimeException("Area not found with id: " + areaId));

        ActionInstance sourceAction = actionInstanceRepository
                .findById(request.getSourceActionInstanceId())
                .orElseThrow(() -> new RuntimeException(
                        "Source action not found with id: " + request.getSourceActionInstanceId()));

        ActionInstance targetAction = actionInstanceRepository
                .findById(request.getTargetActionInstanceId())
                .orElseThrow(() -> new RuntimeException(
                        "Target action not found with id: " + request.getTargetActionInstanceId()));

        if (!sourceAction.getArea().getId().equals(areaId)) {
            throw new RuntimeException("Source action does not belong to the specified area");
        }

        if (!targetAction.getArea().getId().equals(areaId)) {
            throw new RuntimeException("Target action does not belong to the specified area");
        }

        if (actionLinkRepository.existsBySourceActionInstanceAndTargetActionInstance(
                sourceAction, targetAction)) {
            throw new RuntimeException("Link already exists between these actions");
        }

        ActionLink actionLink = new ActionLink();
        actionLink.setSourceActionInstance(sourceAction);
        actionLink.setTargetActionInstance(targetAction);
        actionLink.setArea(area);
        actionLink.setLinkType(request.getLinkType());

        if (request.getMapping() != null) {
            actionLink.setMapping(request.getMapping());
        } else {
            actionLink.setMapping(new HashMap<>());
        }

        if (request.getCondition() != null) {
            actionLink.setCondition(request.getCondition());
        } else {
            actionLink.setCondition(new HashMap<>());
        }

        actionLink.setOrder(request.getOrder());

        ActionLink savedLink = actionLinkRepository.save(actionLink);

        return mapToResponse(savedLink);
    }

    @Transactional
    public List<ActionLinkResponse> createActionLinksBatch(BatchCreateActionLinksRequest request) {
        Area area = areaRepository.findById(request.getAreaId())
                .orElseThrow(() -> new RuntimeException("Area not found with id: " + request.getAreaId()));

        List<ActionLink> linksToSave = request.getLinks().stream()
                .map(linkData -> {
                    ActionInstance sourceAction = actionInstanceRepository
                            .findById(linkData.getSourceActionInstanceId())
                            .orElseThrow(() -> new RuntimeException(
                                    "Source action not found with id: "
                                    + linkData.getSourceActionInstanceId()));

                    ActionInstance targetAction = actionInstanceRepository
                            .findById(linkData.getTargetActionInstanceId())
                            .orElseThrow(() -> new RuntimeException(
                                    "Target action not found with id: "
                                    + linkData.getTargetActionInstanceId()));

                    ActionLink actionLink = new ActionLink();
                    actionLink.setSourceActionInstance(sourceAction);
                    actionLink.setTargetActionInstance(targetAction);
                    actionLink.setArea(area);
                    actionLink.setLinkType(linkData.getLinkType());

                    if (linkData.getMapping() != null) {
                        actionLink.setMapping(linkData.getMapping());
                    } else {
                        actionLink.setMapping(new HashMap<>());
                    }

                    if (linkData.getCondition() != null) {
                        actionLink.setCondition(linkData.getCondition());
                    } else {
                        actionLink.setCondition(new HashMap<>());
                    }

                    actionLink.setOrder(linkData.getOrder());

                    return actionLink;
                })
                .collect(Collectors.toList());

        List<ActionLink> savedLinks = actionLinkRepository.saveAll(linksToSave);

        return savedLinks.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<ActionLinkResponse> getActionLinksByArea(UUID areaId) {
        List<ActionLink> links = actionLinkRepository.findByAreaIdOrderByOrder(areaId);
        return links.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteActionLink(UUID sourceActionInstanceId, UUID targetActionInstanceId) {
        actionInstanceRepository.findById(sourceActionInstanceId)
                .orElseThrow(() -> new RuntimeException("Source action not found with id: " + sourceActionInstanceId));

        actionInstanceRepository.findById(targetActionInstanceId)
                .orElseThrow(() -> new RuntimeException("Target action not found with id: " + targetActionInstanceId));

        actionLinkRepository.deleteById(
                new area.server.AREA_Back.entity.ActionLinkId(
                        sourceActionInstanceId, targetActionInstanceId));
    }

    @Transactional
    public void deleteActionLinksByArea(UUID areaId) {
        Area area = areaRepository.findById(areaId)
                .orElseThrow(() -> new RuntimeException("Area not found with id: " + areaId));

        actionLinkRepository.deleteByArea(area);
    }

    @Transactional
    public void deleteAllLinksForArea(UUID areaId) {
        List<ActionLink> links = actionLinkRepository.findByAreaIdOrderByOrder(areaId);
        actionLinkRepository.deleteAll(links);
    }

    public List<ActionLinkResponse> getActionLinksByActionInstance(UUID actionInstanceId) {
        List<ActionLink> links = actionLinkRepository.findByActionInstanceId(actionInstanceId);
        return links.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private ActionLinkResponse mapToResponse(ActionLink actionLink) {
        ActionLinkResponse response = new ActionLinkResponse();
        response.setSourceActionInstanceId(actionLink.getSourceActionInstance().getId());
        response.setTargetActionInstanceId(actionLink.getTargetActionInstance().getId());
        response.setSourceActionName(actionLink.getSourceActionInstance().getName());
        response.setTargetActionName(actionLink.getTargetActionInstance().getName());
        response.setAreaId(actionLink.getArea().getId());
        response.setLinkType(actionLink.getLinkType());
        response.setMapping(actionLink.getMapping());
        response.setCondition(actionLink.getCondition());
        response.setOrder(actionLink.getOrder());
        response.setCreatedAt(actionLink.getCreatedAt());
        return response;
    }

    @Transactional
    public void triggerLinkedActions(Execution execution) {
        try {
            List<ActionLink> linkedActions = actionLinkRepository
                .findBySourceActionInstanceId(execution.getActionInstance().getId());

            if (linkedActions.isEmpty()) {
                return;
            }

            for (ActionLink link : linkedActions) {
                try {
                    triggerLinkedAction(link, execution);
                } catch (Exception e) {
                    log.error("Failed to trigger linked action {} for execution {}: {}",
                             link.getTargetActionInstance().getId(), execution.getId(), e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error("Error triggering linked actions for execution {}: {}",
                     execution.getId(), e.getMessage(), e);
            throw e;
        }
    }

    private void triggerLinkedAction(ActionLink link, Execution sourceExecution) {
        // Trigger logic placeholder
    }
}
