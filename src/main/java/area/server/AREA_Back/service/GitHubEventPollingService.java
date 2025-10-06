package area.server.AREA_Back.service;

import area.server.AREA_Back.entity.ActionInstance;
import area.server.AREA_Back.entity.ActivationMode;
import area.server.AREA_Back.entity.enums.ActivationModeType;
import area.server.AREA_Back.repository.ActionInstanceRepository;
import area.server.AREA_Back.repository.ActivationModeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Service for checking GitHub events (polling-based implementation)
 * This service runs periodically to check for new GitHub events
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GitHubEventPollingService {

    private final GitHubActionService gitHubActionService;
    private final ActionInstanceRepository actionInstanceRepository;
    private final ActivationModeRepository activationModeRepository;

    /**
     * Poll GitHub events every 5 minutes
     * This method checks all active GitHub action instances with POLL activation mode
     */
    @Scheduled(fixedRate = 300000) // 5 minutes in milliseconds
    public void pollGitHubEvents() {
        log.debug("Starting GitHub events polling cycle");
        
        try {
            // Find all active GitHub action instances with POLL activation mode
            List<ActionInstance> githubActionInstances = actionInstanceRepository
                .findActiveGitHubActionInstances();

            for (ActionInstance actionInstance : githubActionInstances) {
                try {
                    processActionInstance(actionInstance);
                } catch (Exception e) {
                    log.error("Failed to process GitHub action instance {}: {}", 
                             actionInstance.getId(), e.getMessage(), e);
                }
            }
            
            log.debug("Completed GitHub events polling cycle, processed {} instances", 
                     githubActionInstances.size());
                     
        } catch (Exception e) {
            log.error("Failed to complete GitHub events polling cycle: {}", e.getMessage(), e);
        }
    }

    private void processActionInstance(ActionInstance actionInstance) {
        if (!actionInstance.getEnabled()) {
            return;
        }

        // Get the activation mode for this action instance
        List<ActivationMode> activationModes = activationModeRepository
            .findByActionInstanceAndTypeAndEnabled(actionInstance, ActivationModeType.POLL, true);

        if (activationModes.isEmpty()) {
            return;
        }

        ActivationMode activationMode = activationModes.get(0); // Take the first one
        
        // Calculate last check time based on polling interval
        LocalDateTime lastCheck = calculateLastCheckTime(activationMode);
        
        try {
            // Check for new events
            List<Map<String, Object>> events = gitHubActionService.checkGitHubEvents(
                actionInstance.getActionDefinition().getKey(),
                actionInstance.getParams(),
                actionInstance.getUser().getId(),
                lastCheck
            );

            if (!events.isEmpty()) {
                log.info("Found {} new GitHub events for action instance {}", 
                        events.size(), actionInstance.getId());
                
                // TODO: Trigger the linked reactions for each event
                // This would involve creating executions and adding them to the execution queue
                for (Map<String, Object> event : events) {
                    log.debug("GitHub event: {}", event);
                    // Here you would:
                    // 1. Create an Execution entity
                    // 2. Set the input payload to the event data
                    // 3. Queue it for processing by the worker
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to check GitHub events for action instance {}: {}", 
                     actionInstance.getId(), e.getMessage(), e);
        }
    }

    private LocalDateTime calculateLastCheckTime(ActivationMode activationMode) {
        // Get polling interval from activation mode config
        Map<String, Object> config = activationMode.getConfig();
        Integer intervalSeconds = (Integer) config.getOrDefault("interval_seconds", 300); // Default 5 minutes
        
        return LocalDateTime.now().minusSeconds(intervalSeconds);
    }
}