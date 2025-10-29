package area.server.AREA_Back.service.Area;

import area.server.AREA_Back.dto.AreaEventMessage;
import area.server.AREA_Back.entity.ActionInstance;
import area.server.AREA_Back.entity.Execution;
import area.server.AREA_Back.entity.enums.ActivationModeType;
import area.server.AREA_Back.repository.ActionLinkRepository;
import area.server.AREA_Back.service.Redis.RedisEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Service for triggering AREA executions from various sources
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutionTriggerService {

    private final ExecutionService executionService;
    private final RedisEventService redisEventService;
    private final ActionLinkRepository actionLinkRepository;

    /**
     * Triggers execution of an AREA based on an action instance
     */
    @Transactional
    public void triggerAreaExecution(ActionInstance actionInstance,
                                   ActivationModeType activationMode,
                                   Map<String, Object> inputPayload) {

        UUID correlationId = UUID.randomUUID();

        log.info("Triggering AREA execution for action instance: {} (activation: {})",
                actionInstance.getId(), activationMode);

        if (actionInstance.getArea() != null && !actionInstance.getArea().getEnabled()) {
            log.warn("Skipping execution for action instance {} - AREA {} is disabled",
                    actionInstance.getId(), actionInstance.getArea().getId());
            return;
        }

        if (!actionInstance.getEnabled()) {
            log.warn("Skipping execution for action instance {} - ActionInstance is disabled",
                    actionInstance.getId());
            return;
        }

        try {
            var linkedActions = actionLinkRepository
                    .findBySourceActionInstanceIdWithTargetFetch(actionInstance.getId());

            if (!linkedActions.isEmpty()) {
                log.info("Action {} is a trigger with {} linked reactions, triggering them directly",
                         actionInstance.getName(), linkedActions.size());

                for (var link : linkedActions) {
                    try {
                        triggerAreaExecution(link.getTargetActionInstance(), ActivationModeType.MANUAL, inputPayload);
                    } catch (Exception e) {
                        log.error("Failed to trigger linked reaction {} for trigger {}: {}",
                                 link.getTargetActionInstance().getName(), actionInstance.getName(), e.getMessage());
                    }
                }
                return;
            }

            Execution execution = executionService.createExecutionWithActivationType(
                actionInstance,
                activationMode,
                inputPayload,
                correlationId
            );

            AreaEventMessage message = new AreaEventMessage();
            message.setExecutionId(execution.getId());
            message.setActionInstanceId(actionInstance.getId());
            message.setAreaId(actionInstance.getArea().getId());
            message.setEventType(activationMode.toString().toLowerCase());
            message.setSource("trigger_service");
            message.setPayload(inputPayload);
            message.setCorrelationId(correlationId);

            redisEventService.publishAreaEvent(message);

            log.info("Successfully triggered execution: {} for AREA: {}",
                    execution.getId(), actionInstance.getArea().getId());

        } catch (Exception e) {
            log.error("Failed to trigger AREA execution for action instance {}: {}",
                     actionInstance.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to trigger AREA execution", e);
        }
    }

    /**
     * Triggers manual execution of an AREA
     */
    public Execution triggerManualExecution(ActionInstance actionInstance,
                                          Map<String, Object> inputPayload) {

        UUID correlationId = UUID.randomUUID();

        log.info("Triggering manual execution for action instance: {}", actionInstance.getId());

        try {
            Execution execution = executionService.createExecutionWithActivationType(
                actionInstance,
                ActivationModeType.MANUAL,
                inputPayload,
                correlationId
            );

            AreaEventMessage message = new AreaEventMessage();
            message.setExecutionId(execution.getId());
            message.setActionInstanceId(actionInstance.getId());
            message.setAreaId(actionInstance.getArea().getId());
            message.setEventType("manual");
            message.setSource("manual_trigger");
            message.setPayload(inputPayload);
            message.setCorrelationId(correlationId);

            redisEventService.publishAreaEvent(message);

            log.info("Successfully triggered manual execution: {} for AREA: {}",
                    execution.getId(), actionInstance.getArea().getId());

            return execution;

        } catch (Exception e) {
            log.error("Failed to trigger manual execution for action instance {}: {}",
                     actionInstance.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to trigger manual execution", e);
        }
    }
}