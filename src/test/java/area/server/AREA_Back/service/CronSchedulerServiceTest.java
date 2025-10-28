package area.server.AREA_Back.service;

import area.server.AREA_Back.entity.ActionInstance;
import area.server.AREA_Back.entity.ActivationMode;
import area.server.AREA_Back.entity.Area;
import area.server.AREA_Back.entity.enums.ActivationModeType;
import area.server.AREA_Back.repository.ActivationModeRepository;
import area.server.AREA_Back.service.Area.ExecutionTriggerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class CronSchedulerServiceTest {

    @Mock
    private ActivationModeRepository activationModeRepository;

    @Mock
    private TaskScheduler taskScheduler;

    @Mock
    private ExecutionTriggerService executionTriggerService;

    @Mock
    private ScheduledFuture<?> scheduledFuture;

    private CronSchedulerService cronSchedulerService;

    private ActionInstance actionInstance;
    private ActivationMode activationMode;
    private Area area;

    @BeforeEach
    void setUp() {
        cronSchedulerService = new CronSchedulerService(
            activationModeRepository,
            taskScheduler,
            executionTriggerService
        );

        area = new Area();
        area.setId(UUID.randomUUID());
        area.setName("Test Area");

        actionInstance = new ActionInstance();
        actionInstance.setId(UUID.randomUUID());
        actionInstance.setName("Test Action");
        actionInstance.setEnabled(true);
        actionInstance.setArea(area);

        activationMode = new ActivationMode();
        activationMode.setId(UUID.randomUUID());
        activationMode.setType(ActivationModeType.CRON);
        activationMode.setEnabled(true);
        activationMode.setActionInstance(actionInstance);

        Map<String, Object> config = new HashMap<>();
        config.put("cron_expression", "0 0 * * * *"); // Every hour
        activationMode.setConfig(config);
    }

    @Test
    void testInitialize() {
        // Given
        List<ActivationMode> activationModes = Arrays.asList(activationMode);
        when(activationModeRepository.findByTypeAndEnabledWithActionInstance(
            ActivationModeType.CRON, true))
            .thenReturn(activationModes);
        when(taskScheduler.schedule(any(Runnable.class), isA(Trigger.class)))
            .thenAnswer(invocation -> scheduledFuture);

        // When
        cronSchedulerService.initialize();

        // Then
        verify(activationModeRepository).findByTypeAndEnabledWithActionInstance(
            ActivationModeType.CRON, true);
        verify(taskScheduler).schedule(any(Runnable.class), isA(Trigger.class));
        assertEquals(1, cronSchedulerService.getActiveTasksCount());
    }

    @Test
    void testLoadAndScheduleAllCronActivations() {
        // Given
        List<ActivationMode> activationModes = Arrays.asList(activationMode);
        when(activationModeRepository.findByTypeAndEnabledWithActionInstance(
            ActivationModeType.CRON, true))
            .thenReturn(activationModes);
        when(taskScheduler.schedule(any(Runnable.class), isA(Trigger.class)))
            .thenAnswer(invocation -> scheduledFuture);

        // When
        cronSchedulerService.loadAndScheduleAllCronActivations();

        // Then
        verify(activationModeRepository).findByTypeAndEnabledWithActionInstance(
            ActivationModeType.CRON, true);
        verify(taskScheduler).schedule(any(Runnable.class), isA(Trigger.class));
    }

    @Test
    void testLoadAndScheduleAllCronActivationsWithMultiple() {
        // Given
        ActivationMode activationMode2 = new ActivationMode();
        activationMode2.setId(UUID.randomUUID());
        activationMode2.setType(ActivationModeType.CRON);
        activationMode2.setEnabled(true);
        activationMode2.setActionInstance(actionInstance);
        Map<String, Object> config2 = new HashMap<>();
        config2.put("cron_expression", "0 */30 * * * *"); // Every 30 minutes
        activationMode2.setConfig(config2);

        List<ActivationMode> activationModes = Arrays.asList(activationMode, activationMode2);
        when(activationModeRepository.findByTypeAndEnabledWithActionInstance(
            ActivationModeType.CRON, true))
            .thenReturn(activationModes);
        when(taskScheduler.schedule(any(Runnable.class), isA(Trigger.class)))
            .thenAnswer(invocation -> scheduledFuture);

        // When
        cronSchedulerService.loadAndScheduleAllCronActivations();

        // Then
        verify(activationModeRepository).findByTypeAndEnabledWithActionInstance(
            ActivationModeType.CRON, true);
        verify(taskScheduler, times(2)).schedule(any(Runnable.class), isA(Trigger.class));
    }

    @Test
    void testLoadAndScheduleAllCronActivationsWithError() {
        // Given
        Map<String, Object> invalidConfig = new HashMap<>();
        invalidConfig.put("cron_expression", ""); // Invalid empty expression
        activationMode.setConfig(invalidConfig);

        List<ActivationMode> activationModes = Arrays.asList(activationMode);
        when(activationModeRepository.findByTypeAndEnabledWithActionInstance(
            ActivationModeType.CRON, true))
            .thenReturn(activationModes);

        // When
        cronSchedulerService.loadAndScheduleAllCronActivations();

        // Then
        verify(activationModeRepository).findByTypeAndEnabledWithActionInstance(
            ActivationModeType.CRON, true);
        verify(taskScheduler, never()).schedule(any(Runnable.class), isA(Trigger.class));
    }

    @Test
    void testScheduleActivationModeSuccess() {
        // Given
        when(taskScheduler.schedule(any(Runnable.class), isA(Trigger.class)))
            .thenAnswer(invocation -> scheduledFuture);

        // When
        cronSchedulerService.scheduleActivationMode(activationMode);

        // Then
        verify(taskScheduler).schedule(any(Runnable.class), isA(Trigger.class));
    }

    @Test
    void testScheduleActivationModeWithInvalidType() {
        // Given
        activationMode.setType(ActivationModeType.WEBHOOK);

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            cronSchedulerService.scheduleActivationMode(activationMode);
        });
    }

    @Test
    void testScheduleActivationModeWithNullCronExpression() {
        // Given
        Map<String, Object> config = new HashMap<>();
        config.put("cron_expression", null);
        activationMode.setConfig(config);

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            cronSchedulerService.scheduleActivationMode(activationMode);
        });
    }

    @Test
    void testScheduleActivationModeWithEmptyCronExpression() {
        // Given
        Map<String, Object> config = new HashMap<>();
        config.put("cron_expression", "   ");
        activationMode.setConfig(config);

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            cronSchedulerService.scheduleActivationMode(activationMode);
        });
    }

    @Test
    void testScheduleActivationModeWithInvalidCronExpression() {
        // Given
        Map<String, Object> config = new HashMap<>();
        config.put("cron_expression", "invalid cron");
        activationMode.setConfig(config);

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            cronSchedulerService.scheduleActivationMode(activationMode);
        });
    }

    @Test
    void testScheduleActivationModeReschedulesExisting() {
        // Given
        when(taskScheduler.schedule(any(Runnable.class), isA(Trigger.class)))
            .thenAnswer(invocation -> scheduledFuture);
        when(scheduledFuture.cancel(false)).thenReturn(true);

        // Schedule first time
        cronSchedulerService.scheduleActivationMode(activationMode);

        // When - Schedule again
        cronSchedulerService.scheduleActivationMode(activationMode);

        // Then
        verify(taskScheduler, times(2)).schedule(any(Runnable.class), isA(Trigger.class));
        verify(scheduledFuture).cancel(false);
    }

    @Test
    void testCancelScheduledTask() {
        // Given
        when(taskScheduler.schedule(any(Runnable.class), isA(Trigger.class)))
            .thenAnswer(invocation -> scheduledFuture);
        when(scheduledFuture.cancel(false)).thenReturn(true);

        cronSchedulerService.scheduleActivationMode(activationMode);

        // When
        cronSchedulerService.cancelScheduledTask(activationMode.getId());

        // Then
        verify(scheduledFuture).cancel(false);
    }

    @Test
    void testCancelScheduledTaskNonExistent() {
        // Given
        UUID nonExistentId = UUID.randomUUID();

        // When & Then - Should not throw exception
        assertDoesNotThrow(() -> {
            cronSchedulerService.cancelScheduledTask(nonExistentId);
        });
    }

    @Test
    void testRescheduleActivationModeEnabled() {
        // Given
        when(taskScheduler.schedule(any(Runnable.class), isA(Trigger.class)))
            .thenAnswer(invocation -> scheduledFuture);
        when(scheduledFuture.cancel(false)).thenReturn(true);

        cronSchedulerService.scheduleActivationMode(activationMode);
        activationMode.setEnabled(true);

        // When
        cronSchedulerService.rescheduleActivationMode(activationMode);

        // Then
        verify(scheduledFuture).cancel(false);
        verify(taskScheduler, times(2)).schedule(any(Runnable.class), isA(Trigger.class));
    }

    @Test
    void testRescheduleActivationModeDisabled() {
        // Given
        when(taskScheduler.schedule(any(Runnable.class), isA(Trigger.class)))
            .thenAnswer(invocation -> scheduledFuture);
        when(scheduledFuture.cancel(false)).thenReturn(true);

        cronSchedulerService.scheduleActivationMode(activationMode);
        activationMode.setEnabled(false);

        // When
        cronSchedulerService.rescheduleActivationMode(activationMode);

        // Then
        verify(scheduledFuture).cancel(false);
        verify(taskScheduler, times(1)).schedule(any(Runnable.class), isA(Trigger.class));
    }

    @Test
    void testReloadAllCronActivations() {
        // Given
        when(taskScheduler.schedule(any(Runnable.class), isA(Trigger.class)))
            .thenAnswer(invocation -> scheduledFuture);
        when(scheduledFuture.cancel(false)).thenReturn(true);

        List<ActivationMode> activationModes = Arrays.asList(activationMode);
        when(activationModeRepository.findByTypeAndEnabledWithActionInstance(
            ActivationModeType.CRON, true))
            .thenReturn(activationModes);

        cronSchedulerService.scheduleActivationMode(activationMode);

        // When
        cronSchedulerService.reloadAllCronActivations();

        // Then
        verify(scheduledFuture).cancel(false);
        verify(activationModeRepository).findByTypeAndEnabledWithActionInstance(
            ActivationModeType.CRON, true);
        verify(taskScheduler, times(2)).schedule(any(Runnable.class), isA(Trigger.class));
    }

    @Test
    void testExecuteCronTaskSuccess() throws Exception {
        // Given
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        when(taskScheduler.schedule(any(Runnable.class), isA(Trigger.class)))
            .thenAnswer(invocation -> scheduledFuture);

        cronSchedulerService.scheduleActivationMode(activationMode);

        verify(taskScheduler).schedule(runnableCaptor.capture(), isA(Trigger.class));
        Runnable cronTask = runnableCaptor.getValue();

        // When
        cronTask.run();

        // Then
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(executionTriggerService).triggerAreaExecution(
            eq(actionInstance),
            eq(ActivationModeType.CRON),
            payloadCaptor.capture()
        );

        Map<String, Object> payload = payloadCaptor.getValue();
        assertEquals("cron", payload.get("triggered_by"));
        assertEquals(activationMode.getId().toString(), payload.get("activation_mode_id"));
        assertEquals("0 0 * * * *", payload.get("cron_expression"));
        assertNotNull(payload.get("execution_time"));
    }

    @Test
    void testExecuteCronTaskWithDisabledActionInstance() throws Exception {
        // Given
        actionInstance.setEnabled(false);

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        when(taskScheduler.schedule(any(Runnable.class), isA(Trigger.class)))
            .thenAnswer(invocation -> scheduledFuture);

        cronSchedulerService.scheduleActivationMode(activationMode);

        verify(taskScheduler).schedule(runnableCaptor.capture(), isA(Trigger.class));
        Runnable cronTask = runnableCaptor.getValue();

        // When
        cronTask.run();

        // Then
        verify(executionTriggerService, never()).triggerAreaExecution(
            any(), any(), any()
        );
    }

    @Test
    void testExecuteCronTaskWithDisabledActivationMode() throws Exception {
        // Given
        activationMode.setEnabled(false);

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        when(taskScheduler.schedule(any(Runnable.class), isA(Trigger.class)))
            .thenAnswer(invocation -> scheduledFuture);

        cronSchedulerService.scheduleActivationMode(activationMode);

        verify(taskScheduler).schedule(runnableCaptor.capture(), isA(Trigger.class));
        Runnable cronTask = runnableCaptor.getValue();

        // When
        cronTask.run();

        // Then
        verify(executionTriggerService, never()).triggerAreaExecution(
            any(), any(), any()
        );
    }

    @Test
    void testExecuteCronTaskWithException() throws Exception {
        // Given
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        when(taskScheduler.schedule(any(Runnable.class), isA(Trigger.class)))
            .thenAnswer(invocation -> scheduledFuture);

        doThrow(new RuntimeException("Test exception"))
            .when(executionTriggerService)
            .triggerAreaExecution(any(), any(), any());

        cronSchedulerService.scheduleActivationMode(activationMode);

        verify(taskScheduler).schedule(runnableCaptor.capture(), isA(Trigger.class));
        Runnable cronTask = runnableCaptor.getValue();

        // When & Then - Should not throw
        assertDoesNotThrow(() -> cronTask.run());
    }

    @Test
    void testGetScheduledTasksStatus() {
        // Given
        when(taskScheduler.schedule(any(Runnable.class), isA(Trigger.class)))
            .thenAnswer(invocation -> scheduledFuture);
        when(scheduledFuture.isCancelled()).thenReturn(false);
        when(scheduledFuture.isDone()).thenReturn(false);

        cronSchedulerService.scheduleActivationMode(activationMode);

        // When
        Map<UUID, Boolean> status = cronSchedulerService.getScheduledTasksStatus();

        // Then
        assertNotNull(status);
        assertEquals(1, status.size());
        assertTrue(status.containsKey(activationMode.getId()));
        assertTrue(status.get(activationMode.getId()));
    }

    @Test
    void testGetScheduledTasksStatusWithCancelledTask() {
        // Given
        when(taskScheduler.schedule(any(Runnable.class), isA(Trigger.class)))
            .thenAnswer(invocation -> scheduledFuture);
        when(scheduledFuture.isCancelled()).thenReturn(true);

        cronSchedulerService.scheduleActivationMode(activationMode);

        // When
        Map<UUID, Boolean> status = cronSchedulerService.getScheduledTasksStatus();

        // Then
        assertNotNull(status);
        assertEquals(1, status.size());
        assertFalse(status.get(activationMode.getId()));
    }

    @Test
    void testGetScheduledTasksStatusWithDoneTask() {
        // Given
        when(taskScheduler.schedule(any(Runnable.class), isA(Trigger.class)))
            .thenAnswer(invocation -> scheduledFuture);
        when(scheduledFuture.isCancelled()).thenReturn(false);
        when(scheduledFuture.isDone()).thenReturn(true);
        
        cronSchedulerService.scheduleActivationMode(activationMode);

        // When
        Map<UUID, Boolean> status = cronSchedulerService.getScheduledTasksStatus();

        // Then
        assertNotNull(status);
        assertEquals(1, status.size());
        assertFalse(status.get(activationMode.getId()));
    }        @Test
    void testGetActiveTasksCount() {
        // Given
        when(taskScheduler.schedule(any(Runnable.class), isA(Trigger.class)))
            .thenAnswer(invocation -> scheduledFuture);
        when(scheduledFuture.isCancelled()).thenReturn(false);
        when(scheduledFuture.isDone()).thenReturn(false);
        
        cronSchedulerService.scheduleActivationMode(activationMode);

        // When
        int count = cronSchedulerService.getActiveTasksCount();

        // Then
        assertEquals(1, count);
    }

    @Test
    void testGetActiveTasksCountWithCancelledTask() {
        // Given
        when(taskScheduler.schedule(any(Runnable.class), isA(Trigger.class)))
            .thenAnswer(invocation -> scheduledFuture);
        when(scheduledFuture.isCancelled()).thenReturn(true);
        
        cronSchedulerService.scheduleActivationMode(activationMode);

        // When
        int count = cronSchedulerService.getActiveTasksCount();

        // Then
        assertEquals(0, count);
    }

    @Test
    void testGetActiveTasksCountWithMultipleTasks() {
        // Given
        ActivationMode activationMode2 = new ActivationMode();
        activationMode2.setId(UUID.randomUUID());
        activationMode2.setType(ActivationModeType.CRON);
        activationMode2.setEnabled(true);
        activationMode2.setActionInstance(actionInstance);
        Map<String, Object> config2 = new HashMap<>();
        config2.put("cron_expression", "0 */15 * * * *");
        activationMode2.setConfig(config2);

        ScheduledFuture<?> scheduledFuture2 = mock(ScheduledFuture.class);

        when(taskScheduler.schedule(any(Runnable.class), isA(Trigger.class)))
            .thenAnswer(invocation -> scheduledFuture)
            .thenAnswer(invocation -> scheduledFuture2);
        when(scheduledFuture.isCancelled()).thenReturn(false);
        when(scheduledFuture.isDone()).thenReturn(false);
        when(scheduledFuture2.isCancelled()).thenReturn(false);
        when(scheduledFuture2.isDone()).thenReturn(false);

        cronSchedulerService.scheduleActivationMode(activationMode);
        cronSchedulerService.scheduleActivationMode(activationMode2);

        // When
        int count = cronSchedulerService.getActiveTasksCount();

        // Then
        assertEquals(2, count);
    }

    @Test
    void testShutdown() {
        // Given
        when(taskScheduler.schedule(any(Runnable.class), isA(Trigger.class)))
            .thenAnswer(invocation -> scheduledFuture);
        when(scheduledFuture.cancel(false)).thenReturn(true);

        cronSchedulerService.scheduleActivationMode(activationMode);

        // When
        cronSchedulerService.shutdown();

        // Then
        verify(scheduledFuture).cancel(false);
        assertEquals(0, cronSchedulerService.getActiveTasksCount());
    }

    @Test
    void testShutdownWithMultipleTasks() {
        // Given
        ActivationMode activationMode2 = new ActivationMode();
        activationMode2.setId(UUID.randomUUID());
        activationMode2.setType(ActivationModeType.CRON);
        activationMode2.setEnabled(true);
        activationMode2.setActionInstance(actionInstance);
        Map<String, Object> config2 = new HashMap<>();
        config2.put("cron_expression", "0 */20 * * * *");
        activationMode2.setConfig(config2);

        ScheduledFuture<?> scheduledFuture2 = mock(ScheduledFuture.class);

        when(taskScheduler.schedule(any(Runnable.class), isA(Trigger.class)))
            .thenAnswer(invocation -> scheduledFuture)
            .thenAnswer(invocation -> scheduledFuture2);
        when(scheduledFuture.cancel(false)).thenReturn(true);
        when(scheduledFuture2.cancel(false)).thenReturn(true);

        cronSchedulerService.scheduleActivationMode(activationMode);
        cronSchedulerService.scheduleActivationMode(activationMode2);

        // When
        cronSchedulerService.shutdown();

        // Then
        verify(scheduledFuture).cancel(false);
        verify(scheduledFuture2).cancel(false);
        assertEquals(0, cronSchedulerService.getActiveTasksCount());
    }

    @Test
    void testCronTriggerNextExecutionTime() throws Exception {
        // Given
        ArgumentCaptor<org.springframework.scheduling.Trigger> triggerCaptor =
            ArgumentCaptor.forClass(org.springframework.scheduling.Trigger.class);
        when(taskScheduler.schedule(any(Runnable.class), isA(Trigger.class)))
            .thenAnswer(invocation -> scheduledFuture);

        // When
        cronSchedulerService.scheduleActivationMode(activationMode);

        // Then
        verify(taskScheduler).schedule(any(Runnable.class), triggerCaptor.capture());
        org.springframework.scheduling.Trigger trigger = triggerCaptor.getValue();
        assertNotNull(trigger);

        // Test trigger with null lastCompletion
        org.springframework.scheduling.TriggerContext triggerContext =
            mock(org.springframework.scheduling.TriggerContext.class);
        when(triggerContext.lastCompletion()).thenReturn(null);

        Instant nextExecution = trigger.nextExecution(triggerContext);
        assertNotNull(nextExecution);
    }

    @Test
    void testCronTriggerNextExecutionTimeWithLastCompletion() throws Exception {
        // Given
        ArgumentCaptor<org.springframework.scheduling.Trigger> triggerCaptor =
            ArgumentCaptor.forClass(org.springframework.scheduling.Trigger.class);
        when(taskScheduler.schedule(any(Runnable.class), isA(Trigger.class)))
            .thenAnswer(invocation -> scheduledFuture);

        // When
        cronSchedulerService.scheduleActivationMode(activationMode);

        // Then
        verify(taskScheduler).schedule(any(Runnable.class), triggerCaptor.capture());
        org.springframework.scheduling.Trigger trigger = triggerCaptor.getValue();
        assertNotNull(trigger);

        // Test trigger with lastCompletion
        org.springframework.scheduling.TriggerContext triggerContext =
            mock(org.springframework.scheduling.TriggerContext.class);
        Instant lastCompletion = Instant.now().minusSeconds(3600);
        when(triggerContext.lastCompletion()).thenReturn(lastCompletion);

        Instant nextExecution = trigger.nextExecution(triggerContext);
        assertNotNull(nextExecution);
        assertTrue(nextExecution.isAfter(lastCompletion));
    }
}
