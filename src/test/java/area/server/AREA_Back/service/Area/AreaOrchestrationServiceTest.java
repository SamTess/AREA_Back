package area.server.AREA_Back.service.Area;

import area.server.AREA_Back.entity.ActionDefinition;
import area.server.AREA_Back.entity.ActionInstance;
import area.server.AREA_Back.entity.ActivationMode;
import area.server.AREA_Back.entity.Area;
import area.server.AREA_Back.entity.Execution;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.entity.enums.ActivationModeType;
import area.server.AREA_Back.entity.enums.ExecutionStatus;
import area.server.AREA_Back.repository.ActivationModeRepository;
import area.server.AREA_Back.repository.AreaRepository;
import area.server.AREA_Back.service.CronSchedulerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AreaOrchestrationServiceTest {

    @Mock
    private AreaRepository areaRepository;

    @Mock
    private ActivationModeRepository activationModeRepository;

    @Mock
    private CronSchedulerService cronSchedulerService;

    @Mock
    private ReactionChainService reactionChainService;

    @InjectMocks
    private AreaOrchestrationService areaOrchestrationService;

    private Area area;
    private User user;
    private ActionInstance actionInstance;
    private ActionDefinition actionDefinition;
    private ActivationMode cronActivationMode;
    private ActivationMode webhookActivationMode;
    private ActivationMode chainActivationMode;
    private Execution execution;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("testuser");

        area = new Area();
        area.setId(UUID.randomUUID());
        area.setName("Test Area");
        area.setEnabled(true);
        area.setUser(user);

        actionDefinition = new ActionDefinition();
        actionDefinition.setId(UUID.randomUUID());
        actionDefinition.setName("Test Action");
        actionDefinition.setIsExecutable(true);

        actionInstance = new ActionInstance();
        actionInstance.setId(UUID.randomUUID());
        actionInstance.setArea(area);
        actionInstance.setActionDefinition(actionDefinition);
        actionInstance.setEnabled(true);
        actionInstance.setParams(new HashMap<>());

        cronActivationMode = new ActivationMode();
        cronActivationMode.setId(UUID.randomUUID());
        cronActivationMode.setType(ActivationModeType.CRON);
        cronActivationMode.setEnabled(true);
        cronActivationMode.setActionInstance(actionInstance);
        cronActivationMode.setConfig(Map.of("cron", "0 0 * * * *"));

        webhookActivationMode = new ActivationMode();
        webhookActivationMode.setId(UUID.randomUUID());
        webhookActivationMode.setType(ActivationModeType.WEBHOOK);
        webhookActivationMode.setEnabled(true);
        webhookActivationMode.setActionInstance(actionInstance);

        chainActivationMode = new ActivationMode();
        chainActivationMode.setId(UUID.randomUUID());
        chainActivationMode.setType(ActivationModeType.CHAIN);
        chainActivationMode.setEnabled(true);
        chainActivationMode.setActionInstance(actionInstance);

        execution = new Execution();
        execution.setId(UUID.randomUUID());
        execution.setArea(area);
        execution.setActionInstance(actionInstance);
        execution.setStatus(ExecutionStatus.OK);
        execution.setQueuedAt(LocalDateTime.now());
    }

    @Test
    void testInitialize() {
        // Given
        List<ActivationMode> cronModes = Arrays.asList(cronActivationMode);
        when(activationModeRepository.findByTypeAndEnabledWithActionInstance(
            ActivationModeType.CRON, true)).thenReturn(cronModes);

        // When
        areaOrchestrationService.initialize();

        // Then
        verify(activationModeRepository).findByTypeAndEnabledWithActionInstance(
            ActivationModeType.CRON, true);
        verify(cronSchedulerService).scheduleActivationMode(cronActivationMode);
    }

    @Test
    void testInitializeWithException() {
        // Given
        when(activationModeRepository.findByTypeAndEnabledWithActionInstance(
            ActivationModeType.CRON, true)).thenThrow(new RuntimeException("Database error"));

        // When/Then - Should not throw exception, just log error
        assertDoesNotThrow(() -> areaOrchestrationService.initialize());
    }

    @Test
    void testInitializeCronActivations() {
        // Given
        List<ActivationMode> cronModes = Arrays.asList(cronActivationMode);
        when(activationModeRepository.findByTypeAndEnabledWithActionInstance(
            ActivationModeType.CRON, true)).thenReturn(cronModes);

        // When
        areaOrchestrationService.initializeCronActivations();

        // Then
        verify(activationModeRepository).findByTypeAndEnabledWithActionInstance(
            ActivationModeType.CRON, true);
        verify(cronSchedulerService).scheduleActivationMode(cronActivationMode);
    }

    @Test
    void testInitializeCronActivationsWithMultipleModes() {
        // Given
        ActivationMode cronMode2 = new ActivationMode();
        cronMode2.setId(UUID.randomUUID());
        cronMode2.setType(ActivationModeType.CRON);
        cronMode2.setEnabled(true);
        cronMode2.setActionInstance(actionInstance);

        List<ActivationMode> cronModes = Arrays.asList(cronActivationMode, cronMode2);
        when(activationModeRepository.findByTypeAndEnabledWithActionInstance(
            ActivationModeType.CRON, true)).thenReturn(cronModes);

        // When
        areaOrchestrationService.initializeCronActivations();

        // Then
        verify(cronSchedulerService, times(2)).scheduleActivationMode(any(ActivationMode.class));
    }

    @Test
    void testInitializeCronActivationsWithSchedulingError() {
        // Given
        List<ActivationMode> cronModes = Arrays.asList(cronActivationMode);
        when(activationModeRepository.findByTypeAndEnabledWithActionInstance(
            ActivationModeType.CRON, true)).thenReturn(cronModes);
        doThrow(new RuntimeException("Scheduling error"))
            .when(cronSchedulerService).scheduleActivationMode(cronActivationMode);

        // When/Then - Should continue despite error
        assertDoesNotThrow(() -> areaOrchestrationService.initializeCronActivations());
        verify(cronSchedulerService).scheduleActivationMode(cronActivationMode);
    }

    @Test
    void testInitializeCronActivationsWithNoModes() {
        // Given
        when(activationModeRepository.findByTypeAndEnabledWithActionInstance(
            ActivationModeType.CRON, true)).thenReturn(Collections.emptyList());

        // When
        areaOrchestrationService.initializeCronActivations();

        // Then
        verify(activationModeRepository).findByTypeAndEnabledWithActionInstance(
            ActivationModeType.CRON, true);
        verify(cronSchedulerService, never()).scheduleActivationMode(any());
    }

    @Test
    void testHandleAreaStateChangeEnableWithCron() {
        // Given
        area.setEnabled(true);
        List<ActivationMode> allModes = Arrays.asList(cronActivationMode, webhookActivationMode);
        when(activationModeRepository.findAllEnabled()).thenReturn(allModes);

        // When
        areaOrchestrationService.handleAreaStateChange(area, true);

        // Then
        verify(activationModeRepository).findAllEnabled();
        verify(cronSchedulerService).scheduleActivationMode(cronActivationMode);
        verify(cronSchedulerService, never()).cancelScheduledTask(any());
    }

    @Test
    void testHandleAreaStateChangeDisableWithCron() {
        // Given
        area.setEnabled(false);
        List<ActivationMode> allModes = Arrays.asList(cronActivationMode, webhookActivationMode);
        when(activationModeRepository.findAllEnabled()).thenReturn(allModes);

        // When
        areaOrchestrationService.handleAreaStateChange(area, false);

        // Then
        verify(activationModeRepository).findAllEnabled();
        verify(cronSchedulerService).cancelScheduledTask(cronActivationMode.getId());
        verify(cronSchedulerService, never()).scheduleActivationMode(any());
    }

    @Test
    void testHandleAreaStateChangeWithDisabledActivationMode() {
        // Given
        cronActivationMode.setEnabled(false);
        List<ActivationMode> allModes = Arrays.asList(cronActivationMode);
        when(activationModeRepository.findAllEnabled()).thenReturn(allModes);

        // When
        areaOrchestrationService.handleAreaStateChange(area, true);

        // Then
        verify(cronSchedulerService).cancelScheduledTask(cronActivationMode.getId());
        verify(cronSchedulerService, never()).scheduleActivationMode(any());
    }

    @Test
    void testHandleAreaStateChangeWithNonCronMode() {
        // Given
        List<ActivationMode> allModes = Arrays.asList(webhookActivationMode);
        when(activationModeRepository.findAllEnabled()).thenReturn(allModes);

        // When
        areaOrchestrationService.handleAreaStateChange(area, true);

        // Then
        verify(activationModeRepository).findAllEnabled();
        verify(cronSchedulerService, never()).scheduleActivationMode(any());
        verify(cronSchedulerService, never()).cancelScheduledTask(any());
    }

    @Test
    void testHandleAreaStateChangeWithException() {
        // Given
        when(activationModeRepository.findAllEnabled())
            .thenThrow(new RuntimeException("Database error"));

        // When/Then - Should not throw exception
        assertDoesNotThrow(() -> areaOrchestrationService.handleAreaStateChange(area, true));
    }

    @Test
    void testHandleAreaStateChangeWithDifferentArea() {
        // Given
        Area differentArea = new Area();
        differentArea.setId(UUID.randomUUID());
        List<ActivationMode> allModes = Arrays.asList(cronActivationMode);
        when(activationModeRepository.findAllEnabled()).thenReturn(allModes);

        // When
        areaOrchestrationService.handleAreaStateChange(differentArea, true);

        // Then
        verify(activationModeRepository).findAllEnabled();
        verify(cronSchedulerService, never()).scheduleActivationMode(any());
        verify(cronSchedulerService, never()).cancelScheduledTask(any());
    }

    @Test
    void testHandleActivationModeChangeCronEnable() {
        // Given
        cronActivationMode.setEnabled(true);

        // When
        areaOrchestrationService.handleActivationModeChange(cronActivationMode, true);

        // Then
        verify(cronSchedulerService).rescheduleActivationMode(cronActivationMode);
        verify(cronSchedulerService, never()).cancelScheduledTask(any());
    }

    @Test
    void testHandleActivationModeChangeCronDisable() {
        // Given
        cronActivationMode.setEnabled(false);

        // When
        areaOrchestrationService.handleActivationModeChange(cronActivationMode, false);

        // Then
        verify(cronSchedulerService).cancelScheduledTask(cronActivationMode.getId());
        verify(cronSchedulerService, never()).rescheduleActivationMode(any());
    }

    @Test
    void testHandleActivationModeChangeNonCron() {
        // Given
        webhookActivationMode.setEnabled(true);

        // When
        areaOrchestrationService.handleActivationModeChange(webhookActivationMode, true);

        // Then
        verify(cronSchedulerService, never()).rescheduleActivationMode(any());
        verify(cronSchedulerService, never()).cancelScheduledTask(any());
    }

    @Test
    void testHandleActivationModeChangeWithException() {
        // Given
        doThrow(new RuntimeException("Scheduler error"))
            .when(cronSchedulerService).rescheduleActivationMode(cronActivationMode);

        // When/Then - Should not throw exception
        assertDoesNotThrow(() -> 
            areaOrchestrationService.handleActivationModeChange(cronActivationMode, true));
    }

    @Test
    void testHandleExecutionCompletionWithChainMode() {
        // Given
        Map<String, Object> result = Map.of("status", "success", "data", "test");
        List<ActivationMode> allModes = Arrays.asList(chainActivationMode);
        when(activationModeRepository.findAllEnabled()).thenReturn(allModes);

        // When
        areaOrchestrationService.handleExecutionCompletion(execution, result);

        // Then
        verify(activationModeRepository).findAllEnabled();
        verify(reactionChainService).triggerChainReaction(execution, result);
    }

    @Test
    void testHandleExecutionCompletionWithoutChainMode() {
        // Given
        Map<String, Object> result = Map.of("status", "success");
        List<ActivationMode> allModes = Arrays.asList(cronActivationMode, webhookActivationMode);
        when(activationModeRepository.findAllEnabled()).thenReturn(allModes);

        // When
        areaOrchestrationService.handleExecutionCompletion(execution, result);

        // Then
        verify(activationModeRepository).findAllEnabled();
        verify(reactionChainService, never()).triggerChainReaction(any(), any());
    }

    @Test
    void testHandleExecutionCompletionWithMultipleChainModes() {
        // Given
        Map<String, Object> result = Map.of("status", "success");
        
        ActivationMode chainMode2 = new ActivationMode();
        chainMode2.setId(UUID.randomUUID());
        chainMode2.setType(ActivationModeType.CHAIN);
        chainMode2.setEnabled(true);
        chainMode2.setActionInstance(actionInstance);

        List<ActivationMode> allModes = Arrays.asList(chainActivationMode, chainMode2);
        when(activationModeRepository.findAllEnabled()).thenReturn(allModes);

        // When
        areaOrchestrationService.handleExecutionCompletion(execution, result);

        // Then
        verify(reactionChainService).triggerChainReaction(execution, result);
    }

    @Test
    void testHandleExecutionCompletionWithDifferentArea() {
        // Given
        Area differentArea = new Area();
        differentArea.setId(UUID.randomUUID());
        
        Execution differentExecution = new Execution();
        differentExecution.setId(UUID.randomUUID());
        differentExecution.setArea(differentArea);

        Map<String, Object> result = Map.of("status", "success");
        List<ActivationMode> allModes = Arrays.asList(chainActivationMode);
        when(activationModeRepository.findAllEnabled()).thenReturn(allModes);

        // When
        areaOrchestrationService.handleExecutionCompletion(differentExecution, result);

        // Then
        verify(activationModeRepository).findAllEnabled();
        verify(reactionChainService, never()).triggerChainReaction(any(), any());
    }

    @Test
    void testHandleExecutionCompletionWithException() {
        // Given
        Map<String, Object> result = Map.of("status", "success");
        when(activationModeRepository.findAllEnabled())
            .thenThrow(new RuntimeException("Database error"));

        // When/Then - Should not throw exception
        assertDoesNotThrow(() -> 
            areaOrchestrationService.handleExecutionCompletion(execution, result));
    }

    @Test
    void testGetActivationStatistics() {
        // Given
        Area area2 = new Area();
        area2.setId(UUID.randomUUID());
        area2.setEnabled(false);

        List<Area> areas = Arrays.asList(area, area2);
        when(areaRepository.count()).thenReturn(2L);
        when(areaRepository.findAll()).thenReturn(areas);

        ActivationMode pollMode = new ActivationMode();
        pollMode.setType(ActivationModeType.POLL);
        pollMode.setEnabled(true);

        ActivationMode manualMode = new ActivationMode();
        manualMode.setType(ActivationModeType.MANUAL);
        manualMode.setEnabled(true);

        List<ActivationMode> allModes = Arrays.asList(
            cronActivationMode, 
            webhookActivationMode, 
            chainActivationMode,
            pollMode,
            manualMode
        );
        when(activationModeRepository.findAllEnabled()).thenReturn(allModes);
        when(cronSchedulerService.getActiveTasksCount()).thenReturn(3);

        // When
        Map<String, Object> stats = areaOrchestrationService.getActivationStatistics();

        // Then
        assertNotNull(stats);
        assertEquals(2L, stats.get("total_areas"));
        assertEquals(1L, stats.get("enabled_areas"));
        assertEquals(3, stats.get("active_cron_tasks"));
        assertEquals("running", stats.get("system_status"));

        @SuppressWarnings("unchecked")
        Map<ActivationModeType, Long> modeCount = 
            (Map<ActivationModeType, Long>) stats.get("activation_modes");
        assertNotNull(modeCount);
        assertEquals(1L, modeCount.get(ActivationModeType.CRON));
        assertEquals(1L, modeCount.get(ActivationModeType.WEBHOOK));
        assertEquals(1L, modeCount.get(ActivationModeType.CHAIN));
        assertEquals(1L, modeCount.get(ActivationModeType.POLL));
        assertEquals(1L, modeCount.get(ActivationModeType.MANUAL));

        verify(areaRepository).count();
        verify(areaRepository).findAll();
        verify(activationModeRepository).findAllEnabled();
        verify(cronSchedulerService).getActiveTasksCount();
    }

    @Test
    void testGetActivationStatisticsWithNoAreas() {
        // Given
        when(areaRepository.count()).thenReturn(0L);
        when(areaRepository.findAll()).thenReturn(Collections.emptyList());
        when(activationModeRepository.findAllEnabled()).thenReturn(Collections.emptyList());
        when(cronSchedulerService.getActiveTasksCount()).thenReturn(0);

        // When
        Map<String, Object> stats = areaOrchestrationService.getActivationStatistics();

        // Then
        assertNotNull(stats);
        assertEquals(0L, stats.get("total_areas"));
        assertEquals(0L, stats.get("enabled_areas"));
        assertEquals(0, stats.get("active_cron_tasks"));
    }

    @Test
    void testGetActivationStatisticsWithOnlyEnabledAreas() {
        // Given
        List<Area> areas = Arrays.asList(area);
        when(areaRepository.count()).thenReturn(1L);
        when(areaRepository.findAll()).thenReturn(areas);
        when(activationModeRepository.findAllEnabled()).thenReturn(Collections.emptyList());
        when(cronSchedulerService.getActiveTasksCount()).thenReturn(0);

        // When
        Map<String, Object> stats = areaOrchestrationService.getActivationStatistics();

        // Then
        assertEquals(1L, stats.get("total_areas"));
        assertEquals(1L, stats.get("enabled_areas"));
    }

    @Test
    void testGetActivationStatisticsWithException() {
        // Given
        when(areaRepository.count()).thenThrow(new RuntimeException("Database error"));

        // When
        Map<String, Object> stats = areaOrchestrationService.getActivationStatistics();

        // Then
        assertNotNull(stats);
        assertTrue(stats.containsKey("error"));
        assertEquals("Failed to get statistics", stats.get("error"));
        assertEquals("error", stats.get("system_status"));
        assertTrue(stats.containsKey("message"));
    }

    @Test
    void testGetActivationStatisticsWithNoCronTasks() {
        // Given
        when(areaRepository.count()).thenReturn(1L);
        when(areaRepository.findAll()).thenReturn(Arrays.asList(area));
        when(activationModeRepository.findAllEnabled()).thenReturn(Collections.emptyList());
        when(cronSchedulerService.getActiveTasksCount()).thenReturn(0);

        // When
        Map<String, Object> stats = areaOrchestrationService.getActivationStatistics();

        // Then
        assertEquals(0, stats.get("active_cron_tasks"));
    }

    @Test
    void testGetActivationStatisticsWithMixedActivationModes() {
        // Given
        when(areaRepository.count()).thenReturn(1L);
        when(areaRepository.findAll()).thenReturn(Arrays.asList(area));

        ActivationMode cronMode2 = new ActivationMode();
        cronMode2.setType(ActivationModeType.CRON);

        List<ActivationMode> allModes = Arrays.asList(cronActivationMode, cronMode2, webhookActivationMode);
        when(activationModeRepository.findAllEnabled()).thenReturn(allModes);
        when(cronSchedulerService.getActiveTasksCount()).thenReturn(2);

        // When
        Map<String, Object> stats = areaOrchestrationService.getActivationStatistics();

        // Then
        @SuppressWarnings("unchecked")
        Map<ActivationModeType, Long> modeCount = 
            (Map<ActivationModeType, Long>) stats.get("activation_modes");
        assertEquals(2L, modeCount.get(ActivationModeType.CRON));
        assertEquals(1L, modeCount.get(ActivationModeType.WEBHOOK));
    }

    @Test
    void testShutdown() {
        // When/Then - Should not throw exception
        assertDoesNotThrow(() -> areaOrchestrationService.shutdown());
    }

    @Test
    void testShutdownWithException() {
        // This test ensures shutdown handles exceptions gracefully
        // Since shutdown only logs, we just verify it doesn't throw

        // When/Then
        assertDoesNotThrow(() -> areaOrchestrationService.shutdown());
    }

    @Test
    void testHandleAreaStateChangeWithMultipleActivationModes() {
        // Given
        ActivationMode cronMode2 = new ActivationMode();
        cronMode2.setId(UUID.randomUUID());
        cronMode2.setType(ActivationModeType.CRON);
        cronMode2.setEnabled(true);
        cronMode2.setActionInstance(actionInstance);

        List<ActivationMode> allModes = Arrays.asList(
            cronActivationMode, 
            cronMode2, 
            webhookActivationMode
        );
        when(activationModeRepository.findAllEnabled()).thenReturn(allModes);

        // When
        areaOrchestrationService.handleAreaStateChange(area, true);

        // Then
        verify(cronSchedulerService, times(2)).scheduleActivationMode(any(ActivationMode.class));
    }

    @Test
    void testInitializeWithSchedulingFailure() {
        // Given
        List<ActivationMode> cronModes = Arrays.asList(cronActivationMode);
        when(activationModeRepository.findByTypeAndEnabledWithActionInstance(
            ActivationModeType.CRON, true)).thenReturn(cronModes);
        doThrow(new RuntimeException("Scheduling failed"))
            .when(cronSchedulerService).scheduleActivationMode(any());

        // When/Then
        assertDoesNotThrow(() -> areaOrchestrationService.initialize());
        verify(cronSchedulerService).scheduleActivationMode(any());
    }

    @Test
    void testHandleExecutionCompletionWithEmptyResult() {
        // Given
        Map<String, Object> emptyResult = Collections.emptyMap();
        List<ActivationMode> allModes = Arrays.asList(chainActivationMode);
        when(activationModeRepository.findAllEnabled()).thenReturn(allModes);

        // When
        areaOrchestrationService.handleExecutionCompletion(execution, emptyResult);

        // Then
        verify(reactionChainService).triggerChainReaction(execution, emptyResult);
    }

    @Test
    void testHandleActivationModeChangePollingMode() {
        // Given
        ActivationMode pollMode = new ActivationMode();
        pollMode.setId(UUID.randomUUID());
        pollMode.setType(ActivationModeType.POLL);
        pollMode.setEnabled(true);

        // When
        areaOrchestrationService.handleActivationModeChange(pollMode, true);

        // Then
        verify(cronSchedulerService, never()).rescheduleActivationMode(any());
        verify(cronSchedulerService, never()).cancelScheduledTask(any());
    }

    @Test
    void testHandleActivationModeChangeManualMode() {
        // Given
        ActivationMode manualMode = new ActivationMode();
        manualMode.setId(UUID.randomUUID());
        manualMode.setType(ActivationModeType.MANUAL);
        manualMode.setEnabled(true);

        // When
        areaOrchestrationService.handleActivationModeChange(manualMode, true);

        // Then
        verify(cronSchedulerService, never()).rescheduleActivationMode(any());
        verify(cronSchedulerService, never()).cancelScheduledTask(any());
    }
}
