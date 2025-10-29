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
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class SlackEventPollingService {

    private static final int DEFAULT_POLLING_INTERVAL_SECONDS = 300;
    private static final int MINIMUM_POLLING_INTERVAL_SECONDS = 1;
    private static final int SCHEDULER_CHECK_INTERVAL_SECONDS = 5;
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 10;

    private final SlackActionService slackActionService;
    private final ActionInstanceRepository actionInstanceRepository;
    private final ActivationModeRepository activationModeRepository;
    private final MeterRegistry meterRegistry;
    private final ExecutionTriggerService executionTriggerService;

    private Counter pollingCycles;
    private Counter eventsFound;
    private Counter pollingFailures;

    private final Map<UUID, LocalDateTime> lastPollTimes = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> managerTask;

    @PostConstruct
    public void init() {
        initMetrics();
        startScheduler();
    }

    public void initMetrics() {
        pollingCycles = meterRegistry.counter("slack_polling_cycles");
        eventsFound = meterRegistry.counter("slack_events_found");
        pollingFailures = meterRegistry.counter("slack_polling_failures");
    }

    public void startScheduler() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "slack-polling-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        managerTask = scheduler.scheduleAtFixedRate(
            this::managePollingTasks,
            0,
            SCHEDULER_CHECK_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );

        log.info("SlackEventPollingService scheduler started");
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down SlackEventPollingService");

        if (managerTask != null) {
            managerTask.cancel(false);
        }

        scheduledTasks.values().forEach(task -> task.cancel(false));
        scheduledTasks.clear();

        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private void managePollingTasks() {
        try {
            List<ActionInstance> slackActionInstances = actionInstanceRepository
                .findActiveSlackActionInstances();

            log.debug("Managing polling tasks for {} Slack action instances", slackActionInstances.size());

            scheduledTasks.keySet().removeIf(actionInstanceId -> {
                boolean exists = slackActionInstances.stream()
                    .anyMatch(ai -> ai.getId().equals(actionInstanceId));
                if (!exists) {
                    ScheduledFuture<?> task = scheduledTasks.get(actionInstanceId);
                    if (task != null) {
                        task.cancel(false);
                    }
                    log.debug("Removed polling task for deleted action instance {}", actionInstanceId);
                    return true;
                }
                return false;
            });

            for (ActionInstance actionInstance : slackActionInstances) {
                if (!actionInstance.getEnabled()) {
                    continue;
                }

                if (actionInstance.getArea() != null && !actionInstance.getArea().getEnabled()) {
                    continue;
                }

                List<ActivationMode> activationModes = activationModeRepository
                    .findByActionInstanceAndTypeAndEnabled(actionInstance, ActivationModeType.POLL, true);

                if (activationModes.isEmpty()) {
                    continue;
                }

                ActivationMode activationMode = activationModes.get(0);
                int pollingInterval = getPollingInterval(activationMode);
                UUID actionInstanceId = actionInstance.getId();

                ScheduledFuture<?> existingTask = scheduledTasks.get(actionInstanceId);
                if (existingTask == null || existingTask.isCancelled() || existingTask.isDone()) {
                    if (managerTask == null) {
                        if (shouldPollNow(activationMode)) {
                            processActionInstance(actionInstance);
                        }
                    } else {
                        ScheduledFuture<?> newTask = scheduler.scheduleAtFixedRate(
                            () -> processActionInstance(actionInstance),
                            0,
                            pollingInterval,
                            TimeUnit.SECONDS
                        );
                        scheduledTasks.put(actionInstanceId, newTask);
                        log.info("Scheduled polling task for action instance {} with interval {} seconds",
                                actionInstanceId, pollingInterval);
                    }
                }
            }

        } catch (Exception e) {
            pollingFailures.increment();
            log.error("Error managing polling tasks: {}", e.getMessage(), e);
        }
    }

    @Transactional
    private void processActionInstance(ActionInstance actionInstance) {
        if (!actionInstance.getEnabled()) {
            return;
        }

        if (actionInstance.getArea() != null && !actionInstance.getArea().getEnabled()) {
            log.debug("Skipping action instance {} - AREA {} is disabled",
                     actionInstance.getId(), actionInstance.getArea().getId());
            return;
        }

        List<ActivationMode> activationModes = activationModeRepository
            .findByActionInstanceAndTypeAndEnabled(actionInstance, ActivationModeType.POLL, true);

        if (activationModes.isEmpty()) {
            return;
        }

        ActivationMode activationMode = activationModes.get(0);

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
            pollingFailures.increment();
            log.error("Failed to check Slack events for action instance {}: {}",
                     actionInstance.getId(), e.getMessage(), e);
        } finally {
            lastPollTimes.put(actionInstance.getId(), LocalDateTime.now());
        }
    }

    private int getPollingInterval(ActivationMode activationMode) {
        Map<String, Object> config = activationMode.getConfig();
        Integer configuredInterval = (Integer) config.getOrDefault("pollingInterval", DEFAULT_POLLING_INTERVAL_SECONDS);

        if (configuredInterval != null && configuredInterval < MINIMUM_POLLING_INTERVAL_SECONDS) {
            log.warn("Polling interval {} seconds is below minimum {}, using minimum",
                    configuredInterval, MINIMUM_POLLING_INTERVAL_SECONDS);
            return MINIMUM_POLLING_INTERVAL_SECONDS;
        }

        if (configuredInterval != null) {
            return configuredInterval;
        } else {
            return DEFAULT_POLLING_INTERVAL_SECONDS;
        }
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

    /**
     * Public method to trigger polling for testing purposes.
     */
    public void pollSlackEvents() {
        pollingCycles.increment();
        managePollingTasks();
    }
}
