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
            boolean isExecutable = actionInstance.getActionDefinition().getIsExecutable();
            Execution execution = null;
            if (isExecutable) {
                execution = executionService.createExecutionWithActivationType(
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

                log.info("Successfully triggered execution: {} for action instance: {}",
                        execution.getId(), actionInstance.getName());
            } else {
                log.debug("Action instance {} is not executable (event-only), skipping execution",
                         actionInstance.getName());
            }
            var linkedActions = actionLinkRepository
                    .findBySourceActionInstanceIdWithTargetFetch(actionInstance.getId());

            if (!linkedActions.isEmpty()) {
                log.info("Action {} has {} linked reactions, triggering them in order",
                         actionInstance.getName(), linkedActions.size());
                linkedActions.sort((l1, l2) -> {
                    Integer order1;
                    if (l1.getOrder() != null) {
                        order1 = l1.getOrder();
                    } else {
                        order1 = 0;
                    }
                    Integer order2;
                    if (l2.getOrder() != null) {
                        order2 = l2.getOrder();
                    } else {
                        order2 = 0;
                    }
                    return order1.compareTo(order2);
                });

                for (var link : linkedActions) {
                    try {
                        log.info("Triggering chained reaction {} (order: {}) from {}",
                                link.getTargetActionInstance().getName(),
                                link.getOrder(),
                                actionInstance.getName());
                        triggerAreaExecution(link.getTargetActionInstance(), ActivationModeType.CHAIN, inputPayload);
                    } catch (Exception e) {
                        log.error("Failed to trigger chained reaction {} from {}: {}",
                                 link.getTargetActionInstance().getName(),
                                 actionInstance.getName(),
                                 e.getMessage());
                    }
                }
            }

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