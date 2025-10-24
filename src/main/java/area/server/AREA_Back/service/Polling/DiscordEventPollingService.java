package area.server.AREA_Back.service.Polling;

import area.server.AREA_Back.entity.ActionInstance;
import area.server.AREA_Back.entity.ActivationMode;
import area.server.AREA_Back.entity.enums.ActivationModeType;
import area.server.AREA_Back.repository.ActionInstanceRepository;
import area.server.AREA_Back.repository.ActivationModeRepository;
import area.server.AREA_Back.service.Area.ExecutionTriggerService;
import area.server.AREA_Back.service.Area.Services.DiscordActionService;
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
public class DiscordEventPollingService {

    private static final int DEFAULT_POLLING_INTERVAL_SECONDS = 300;

    private final DiscordActionService discordActionService;
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
        this.pollingCycles = Counter.builder("discord.polling.cycles")
            .description("Number of Discord polling cycles")
            .register(meterRegistry);

        this.eventsFound = Counter.builder("discord.polling.events_found")
            .description("Number of Discord events found")
            .register(meterRegistry);

        this.pollingFailures = Counter.builder("discord.polling.failures")
            .description("Number of Discord polling failures")
            .register(meterRegistry);
    }

    @Scheduled(fixedRate = 10000)
    public void pollDiscordEvents() {
        pollingCycles.increment();
        log.info("Starting Discord events polling cycle");

        try {
            List<ActionInstance> discordActionInstances = actionInstanceRepository
                .findActiveDiscordActionInstances();

            log.info("Found {} Discord action instances to check", discordActionInstances.size());

            for (ActionInstance actionInstance : discordActionInstances) {
                try {
                    processActionInstance(actionInstance);
                } catch (Exception e) {
                    pollingFailures.increment();
                    log.error("Failed to process Discord action instance {}: {}",
                             actionInstance.getId(), e.getMessage(), e);
                }
            }

            log.info("Completed Discord events polling cycle, processed {} instances",
                     discordActionInstances.size());

        } catch (Exception e) {
            pollingFailures.increment();
            log.error("Failed to complete Discord events polling cycle: {}", e.getMessage(), e);
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

        log.info("Polling Discord events for action instance {} with interval {} seconds",
                 actionInstance.getId(), getPollingInterval(activationMode));

        try {
            List<Map<String, Object>> events = discordActionService.checkDiscordEvents(
                actionInstance.getActionDefinition().getKey(),
                actionInstance.getParams(),
                actionInstance.getUser().getId(),
                lastCheck
            );

            if (!events.isEmpty()) {
                eventsFound.increment(events.size());
                log.info("Found {} new Discord events for action instance {} ({})",
                        events.size(), actionInstance.getId(), actionInstance.getActionDefinition().getKey());

                for (Map<String, Object> event : events) {
                    log.debug("Processing Discord event: {}", event);

                    try {
                        executionTriggerService.triggerAreaExecution(
                            actionInstance,
                            ActivationModeType.POLL,
                            event
                        );

                        log.debug("Successfully triggered execution for Discord event from action instance {}",
                                actionInstance.getId());

                    } catch (Exception e) {
                        log.error("Failed to trigger execution for Discord event from action instance {}: {}",
                                actionInstance.getId(), e.getMessage(), e);
                    }
                }
            } else {
                log.debug("No new Discord events found for action instance {}", actionInstance.getId());

            }

        } catch (Exception e) {
            log.error("Failed to check Discord events for action instance {}: {}",
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
        Map<String, Object> config = activationMode.getConfig();
        Integer intervalSeconds = (Integer) config.getOrDefault("pollingInterval", DEFAULT_POLLING_INTERVAL_SECONDS);

        return LocalDateTime.now().minusSeconds(intervalSeconds);
    }
}
