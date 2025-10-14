package area.server.AREA_Back.service;

import area.server.AREA_Back.entity.ActionInstance;
import area.server.AREA_Back.entity.ActivationMode;
import area.server.AREA_Back.entity.enums.ActivationModeType;
import area.server.AREA_Back.repository.ActionInstanceRepository;
import area.server.AREA_Back.repository.ActivationModeRepository;
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

/**
 * Service for checking GitHub events (polling-based implementation)
 * This service runs periodically to check for new GitHub events
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GitHubEventPollingService {

    private static final int DEFAULT_POLLING_INTERVAL_SECONDS = 300;

    private final GitHubActionService gitHubActionService;
    private final ActionInstanceRepository actionInstanceRepository;
    private final ActivationModeRepository activationModeRepository;
    private final MeterRegistry meterRegistry;

    private Counter pollingCycles;
    private Counter eventsFound;
    private Counter pollingFailures;

    @PostConstruct
    public void initMetrics() {
        pollingCycles = Counter.builder("github.polling.cycles")
                .description("Total number of GitHub polling cycles executed")
                .register(meterRegistry);

        eventsFound = Counter.builder("github.polling.events_found")
                .description("Total number of GitHub events found during polling")
                .register(meterRegistry);

        pollingFailures = Counter.builder("github.polling.failures")
                .description("Total number of GitHub polling failures")
                .register(meterRegistry);
    }
    private final ExecutionTriggerService executionTriggerService;

    private final Map<UUID, LocalDateTime> lastPollTimes = new ConcurrentHashMap<>();

    @Scheduled(fixedRate = 10000)
    public void pollGitHubEvents() {
        pollingCycles.increment();

        try {
            List<ActionInstance> githubActionInstances = actionInstanceRepository
                .findActiveGitHubActionInstances();

            for (ActionInstance actionInstance : githubActionInstances) {
                try {
                    processActionInstance(actionInstance);
                } catch (Exception e) {
                    pollingFailures.increment();
                    log.error("Failed to process GitHub action instance { }: { }",
                             actionInstance.getId(), e.getMessage(), e);
                }
            }

        } catch (Exception e) {
            pollingFailures.increment();
            log.error("Failed to complete GitHub events polling cycle: { }", e.getMessage(), e);
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
            return;
        }

        LocalDateTime lastCheck = calculateLastCheckTime(activationMode);

        try {
            List<Map<String, Object>> events = gitHubActionService.checkGitHubEvents(
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
                        log.error("Failed to trigger execution for GitHub event from action instance {}: {}",
                                actionInstance.getId(), e.getMessage(), e);
                    }
                }
            }

        } catch (Exception e) {
            log.error("Failed to check GitHub events for action instance {}: {}",
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

        int intervalSeconds = getPollingInterval(activationMode);
        LocalDateTime nextPollTime = lastPoll.plusSeconds(intervalSeconds);

        return LocalDateTime.now().isAfter(nextPollTime);
    }

    private int getPollingInterval(ActivationMode activationMode) {
        Map<String, Object> config = activationMode.getConfig();
        return (Integer) config.getOrDefault("interval_seconds", DEFAULT_POLLING_INTERVAL_SECONDS);
    }

    private LocalDateTime calculateLastCheckTime(ActivationMode activationMode) {
        Map<String, Object> config = activationMode.getConfig();
        Integer intervalSeconds = (Integer) config.getOrDefault("interval_seconds", DEFAULT_POLLING_INTERVAL_SECONDS);

        return LocalDateTime.now().minusSeconds(intervalSeconds);
    }
}