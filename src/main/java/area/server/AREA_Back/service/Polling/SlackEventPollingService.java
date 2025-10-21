package area.server.AREA_Back.service.Polling;

import area.server.AREA_Back.entity.ActionInstance;
import area.server.AREA_Back.entity.ActivationMode;
import area.server.AREA_Back.entity.enums.ActivationModeType;
import area.server.AREA_Back.repository.ActionInstanceRepository;
import area.server.AREA_Back.repository.ActivationModeRepository;
import area.server.AREA_Back.service.Area.ExecutionTriggerService;
import area.server.AREA_Back.service.Area.Services.SlackActionService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class SlackEventPollingService {

    private static final int DEFAULT_POLLING_INTERVAL_SECONDS = 300;

    private final SlackActionService slackActionService;
    private final ActionInstanceRepository actionInstanceRepository;
    private final ActivationModeRepository activationModeRepository;
    private final MeterRegistry meterRegistry;
    private final ExecutionTriggerService executionTriggerService;

    private Counter pollingCycles;
    private Counter eventsFound;
    private Counter pollingFailures;

    private final Map<UUID, LocalDateTime> lastPollTimes = new ConcurrentHashMap<>();

    @PostConstruct
    public void initMetrics() {
        pollingCycles = meterRegistry.counter("slack_polling_cycles");
        eventsFound = meterRegistry.counter("slack_events_found");
        pollingFailures = meterRegistry.counter("slack_polling_failures");
    }

    @Scheduled(fixedRate = 10000)
    public void pollSlackEvents() {
        pollingCycles.increment();
        log.info("Starting Slack events polling cycle");

        try {
            List<ActionInstance> slackActionInstances = actionInstanceRepository
                .findActiveSlackActionInstances();

            log.info("Found {} Slack action instances to check", slackActionInstances.size());

            for (ActionInstance actionInstance : slackActionInstances) {
                try {
                    processActionInstance(actionInstance);
                } catch (Exception e) {
                    pollingFailures.increment();
                    log.error("Failed to process Slack action instance {}: {}",
                             actionInstance.getId(), e.getMessage(), e);
                }
            }

            log.info("Completed Slack events polling cycle, processed {} instances",
                     slackActionInstances.size());

        } catch (Exception e) {
            pollingFailures.increment();
            log.error("Failed to complete Slack events polling cycle: {}", e.getMessage(), e);
        }
    }

    @Transactional
    private void processActionInstance(ActionInstance actionInstance) {
        if (!actionInstance.getEnabled()) {
            return;
        }

        List<ActivationMode> activationModes = activationModeRepository
            .findByActionInstanceAndTypeAndEnabled(actionInstance, ActivationModeType.POLL, true);

        if (activationModes.isEmpty()) {
            return;
        }

        ActivationMode activationMode = activationModes.get(0);

        if (!shouldPollNow(activationMode)) {
            log.debug("Skipping poll for action instance {} - interval not elapsed",
                     actionInstance.getId());
            return;
        }

        LocalDateTime lastCheck = calculateLastCheckTime(activationMode);

        log.info("Polling Slack events for action instance {} with interval {} seconds",
                 actionInstance.getId(), getPollingInterval(activationMode));

        try {
            List<Map<String, Object>> events = slackActionService.checkSlackEvents(
                actionInstance.getActionDefinition().getKey(),
                actionInstance.getParams(),
                actionInstance.getUser().getId(),
                lastCheck
            );

            if (!events.isEmpty()) {
                eventsFound.increment(events.size());

                for (Map<String, Object> event : events) {
                    try {
                        executionTriggerService.triggerAreaExecution(
                            actionInstance,
                            ActivationModeType.POLL,
                            event
                        );
                    } catch (Exception e) {
                        log.error("Failed to trigger execution for Slack event from action instance {}: {}",
                                actionInstance.getId(), e.getMessage(), e);
                    }
                }
            }

        } catch (Exception e) {
            log.error("Failed to check Slack events for action instance {}: {}",
                     actionInstance.getId(), e.getMessage(), e);
        } finally {
            lastPollTimes.put(actionInstance.getId(), LocalDateTime.now());
        }
    }

    private boolean shouldPollNow(ActivationMode activationMode) {
        UUID actionInstanceId = activationMode.getActionInstance().getId();
        LocalDateTime lastPoll = lastPollTimes.get(actionInstanceId);

        if (lastPoll == null) {
            return true;
        }

        int pollingInterval = getPollingInterval(activationMode);
        LocalDateTime nextPollTime = lastPoll.plusSeconds(pollingInterval);

        return LocalDateTime.now().isAfter(nextPollTime);
    }

    private int getPollingInterval(ActivationMode activationMode) {
        Map<String, Object> config = activationMode.getConfig();
        return (Integer) config.getOrDefault("pollingInterval", DEFAULT_POLLING_INTERVAL_SECONDS);
    }

    private LocalDateTime calculateLastCheckTime(ActivationMode activationMode) {
        UUID actionInstanceId = activationMode.getActionInstance().getId();
        LocalDateTime lastPoll = lastPollTimes.get(actionInstanceId);

        if (lastPoll != null) {
            return lastPoll;
        }

        int pollingInterval = getPollingInterval(activationMode);
        return LocalDateTime.now().minusSeconds(pollingInterval);
    }
}
