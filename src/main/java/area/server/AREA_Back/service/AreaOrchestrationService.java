package area.server.AREA_Back.service;

import area.server.AREA_Back.entity.ActivationMode;
import area.server.AREA_Back.entity.Area;
import area.server.AREA_Back.entity.Execution;
import area.server.AREA_Back.entity.enums.ActivationModeType;
import area.server.AREA_Back.repository.ActivationModeRepository;
import area.server.AREA_Back.repository.AreaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;

/**
 * Service that coordinates different AREA activation modes and execution flows
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AreaOrchestrationService {

    private final AreaRepository areaRepository;
    private final ActivationModeRepository activationModeRepository;
    private final CronSchedulerService cronSchedulerService;
    private final ReactionChainService reactionChainService;

    @PostConstruct
    public void initialize() {
        log.info("Initializing AREA Orchestration Service");

        initializeCronActivations();

        log.info("AREA Orchestration Service initialized successfully");
    }

    /**
     * Initializes all CRON activation modes by scheduling them
     */
    public void initializeCronActivations() {
        try {
            List<ActivationMode> cronModes = activationModeRepository
                .findByTypeAndEnabledWithActionInstance(ActivationModeType.CRON, true);

            log.info("Found {} CRON activation modes to initialize", cronModes.size());

            for (ActivationMode activationMode : cronModes) {
                try {
                    cronSchedulerService.scheduleActivationMode(activationMode);
                } catch (Exception e) {
                    log.error("Failed to schedule CRON activation mode {}: {}",
                             activationMode.getId(), e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error("Failed to initialize CRON activations: {}", e.getMessage(), e);
        }
    }

    /**
     * Handles area state changes (enable/disable)
     */
    public void handleAreaStateChange(Area area, boolean enabled) {
        log.info("Handling state change for AREA {}: enabled={}", area.getId(), enabled);

        try {
            List<ActivationMode> allActivationModes = activationModeRepository
                .findAllEnabled();

            List<ActivationMode> areaActivationModes = allActivationModes.stream()
                .filter(am -> am.getActionInstance().getArea().getId().equals(area.getId()))
                .toList();

            for (ActivationMode activationMode : areaActivationModes) {
                if (activationMode.getType() == ActivationModeType.CRON) {
                    if (enabled && activationMode.getEnabled()) {
                        cronSchedulerService.scheduleActivationMode(activationMode);
                    } else {
                        cronSchedulerService.cancelScheduledTask(activationMode.getId());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to handle state change for AREA {}: {}",
                     area.getId(), e.getMessage(), e);
        }
    }

    /**
     * Handles activation mode changes
     */
    public void handleActivationModeChange(ActivationMode activationMode, boolean enabled) {
        log.info("Handling activation mode change: {} enabled={}",
                activationMode.getId(), enabled);

        try {
            if (activationMode.getType() == ActivationModeType.CRON) {
                if (enabled) {
                    cronSchedulerService.rescheduleActivationMode(activationMode);
                } else {
                    cronSchedulerService.cancelScheduledTask(activationMode.getId());
                }
            }
        } catch (Exception e) {
            log.error("Failed to handle activation mode change {}: {}",
                     activationMode.getId(), e.getMessage(), e);
        }
    }

    /**
     * Processes execution completion and triggers chain reactions if needed
     */
    public void handleExecutionCompletion(Execution execution, Map<String, Object> result) {
        try {
            log.debug("Handling execution completion: {}", execution.getId());

            Area area = execution.getArea();

            List<ActivationMode> allActivationModes = activationModeRepository.findAllEnabled();
            List<ActivationMode> chainModes = allActivationModes.stream()
                .filter(am -> am.getType() == ActivationModeType.CHAIN)
                .filter(am -> am.getActionInstance().getArea().getId().equals(area.getId()))
                .toList();

            if (!chainModes.isEmpty()) {
                log.info("Triggering chain reactions for completed execution: {}", execution.getId());
                reactionChainService.triggerChainReaction(execution, result);
            }

        } catch (Exception e) {
            log.error("Failed to handle execution completion for {}: {}",
                     execution.getId(), e.getMessage(), e);
        }
    }

    /**
     * Gets statistics about AREA activations
     */
    public Map<String, Object> getActivationStatistics() {
        try {
            long totalAreas = areaRepository.count();
            List<Area> allAreas = areaRepository.findAll();
            long enabledAreas = allAreas.stream().filter(Area::getEnabled).count();

            List<ActivationMode> allModes = activationModeRepository.findAllEnabled();
            Map<ActivationModeType, Long> modeCount = Map.of(
                ActivationModeType.CRON, 
                    allModes.stream().filter(am -> am.getType() == ActivationModeType.CRON).count(),
                ActivationModeType.WEBHOOK, 
                    allModes.stream().filter(am -> am.getType() == ActivationModeType.WEBHOOK).count(),
                ActivationModeType.POLL, 
                    allModes.stream().filter(am -> am.getType() == ActivationModeType.POLL).count(),
                ActivationModeType.MANUAL, 
                    allModes.stream().filter(am -> am.getType() == ActivationModeType.MANUAL).count(),
                ActivationModeType.CHAIN, 
                    allModes.stream().filter(am -> am.getType() == ActivationModeType.CHAIN).count()
            );

            int activeCronTasks = cronSchedulerService.getActiveTasksCount();

            return Map.of(
                "total_areas", totalAreas,
                "enabled_areas", enabledAreas,
                "activation_modes", modeCount,
                "active_cron_tasks", activeCronTasks,
                "system_status", "running"
            );

        } catch (Exception e) {
            log.error("Failed to get activation statistics: {}", e.getMessage(), e);
            return Map.of(
                "error", "Failed to get statistics",
                "message", e.getMessage(),
                "system_status", "error"
            );
        }
    }

    /**
     * Shutdown hook to clean up resources
     */
    public void shutdown() {
        log.info("Shutting down AREA Orchestration Service");
        try {
            log.info("AREA Orchestration Service shutdown completed");
        } catch (Exception e) {
            log.error("Error during AREA Orchestration Service shutdown: {}", e.getMessage(), e);
        }
    }
}