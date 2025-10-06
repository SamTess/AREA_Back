package area.server.AREA_Back.service;

import area.server.AREA_Back.entity.ActionInstance;
import area.server.AREA_Back.entity.ActivationMode;
import area.server.AREA_Back.entity.enums.ActivationModeType;
import area.server.AREA_Back.repository.ActivationModeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Service for managing CRON-based scheduled tasks for AREA activations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CronSchedulerService {

    private final ActivationModeRepository activationModeRepository;
    private final TaskScheduler taskScheduler;
    private final ExecutionTriggerService executionTriggerService;

    private final Map<UUID, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    @PostConstruct
    public void initialize() {
        log.info("Initializing CRON Scheduler Service");
        loadAndScheduleAllCronActivations();
        log.info("CRON Scheduler Service initialized successfully");
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down CRON Scheduler Service");
        scheduledTasks.values().forEach(task -> task.cancel(false));
        scheduledTasks.clear();
        log.info("CRON Scheduler Service shut down successfully");
    }

    /**
     * Loads all enabled CRON activation modes and schedules them
     */
    public void loadAndScheduleAllCronActivations() {
        List<ActivationMode> cronActivations = activationModeRepository
            .findByTypeAndEnabled(ActivationModeType.CRON, true);

        log.info("Found {} CRON activation modes to schedule", cronActivations.size());

        for (ActivationMode activationMode : cronActivations) {
            try {
                scheduleActivationMode(activationMode);
            } catch (Exception e) {
                log.error("Failed to schedule CRON activation mode {}: {}", 
                         activationMode.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * Schedules a single activation mode
     */
    public void scheduleActivationMode(ActivationMode activationMode) {
        if (activationMode.getType() != ActivationModeType.CRON) {
            throw new IllegalArgumentException("Activation mode must be of type CRON");
        }

        Map<String, Object> config = activationMode.getConfig();
        String cronExpression = (String) config.get("cron_expression");

        if (cronExpression == null || cronExpression.trim().isEmpty()) {
            throw new IllegalArgumentException("CRON expression is required for CRON activation mode");
        }

        try {
            // Validate CRON expression
            CronExpression.parse(cronExpression);

            // Cancel existing task if any
            cancelScheduledTask(activationMode.getId());

            // Schedule new task
            ScheduledFuture<?> scheduledTask = taskScheduler.schedule(
                () -> executeCronTask(activationMode),
                triggerContext -> {
                    CronExpression cron = CronExpression.parse(cronExpression);
                    if (triggerContext.lastCompletion() != null) {
                        return cron.next(triggerContext.lastCompletion());
                    } else {
                        return cron.next(java.time.Instant.now());
                    }
                }
            );

            scheduledTasks.put(activationMode.getId(), scheduledTask);
            
            log.info("Scheduled CRON task for activation mode {} with expression: {}", 
                    activationMode.getId(), cronExpression);

        } catch (Exception e) {
            log.error("Failed to schedule CRON task for activation mode {}: {}", 
                     activationMode.getId(), e.getMessage(), e);
            throw new IllegalArgumentException("Invalid CRON expression: " + cronExpression, e);
        }
    }

    /**
     * Cancels a scheduled task
     */
    public void cancelScheduledTask(UUID activationModeId) {
        ScheduledFuture<?> existingTask = scheduledTasks.remove(activationModeId);
        if (existingTask != null) {
            existingTask.cancel(false);
            log.info("Cancelled scheduled task for activation mode: {}", activationModeId);
        }
    }

    /**
     * Reschedules an activation mode (useful when configuration changes)
     */
    public void rescheduleActivationMode(ActivationMode activationMode) {
        cancelScheduledTask(activationMode.getId());
        if (activationMode.getEnabled()) {
            scheduleActivationMode(activationMode);
        }
    }

    /**
     * Executes a CRON task
     */
    private void executeCronTask(ActivationMode activationMode) {
        try {
            ActionInstance actionInstance = activationMode.getActionInstance();
            
            if (!actionInstance.getEnabled() || !activationMode.getEnabled()) {
                log.debug("Skipping disabled CRON task for activation mode: {}", activationMode.getId());
                return;
            }

            log.info("Executing CRON task for activation mode: {} (action: {})", 
                    activationMode.getId(), actionInstance.getName());

            // Create execution payload with cron context
            Map<String, Object> cronPayload = Map.of(
                "triggered_by", "cron",
                "execution_time", LocalDateTime.now().toString(),
                "cron_expression", activationMode.getConfig().get("cron_expression"),
                "activation_mode_id", activationMode.getId().toString()
            );

            // Trigger execution
            executionTriggerService.triggerAreaExecution(
                actionInstance,
                ActivationModeType.CRON,
                cronPayload,
                UUID.randomUUID()
            );

        } catch (Exception e) {
            log.error("Failed to execute CRON task for activation mode {}: {}", 
                     activationMode.getId(), e.getMessage(), e);
        }
    }

    /**
     * Gets the status of all scheduled tasks
     */
    public Map<UUID, Boolean> getScheduledTasksStatus() {
        Map<UUID, Boolean> status = new ConcurrentHashMap<>();
        scheduledTasks.forEach((id, task) -> status.put(id, !task.isCancelled() && !task.isDone()));
        return status;
    }

    /**
     * Gets the count of active scheduled tasks
     */
    public int getActiveTasksCount() {
        return (int) scheduledTasks.values().stream()
            .filter(task -> !task.isCancelled() && !task.isDone())
            .count();
    }
}