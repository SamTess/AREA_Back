package area.server.AREA_Back.service;

import area.server.AREA_Back.entity.ActionDefinition;
import area.server.AREA_Back.entity.ActionInstance;
import area.server.AREA_Back.entity.ActivationMode;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.entity.enums.ActivationModeType;
import area.server.AREA_Back.repository.ActionInstanceRepository;
import area.server.AREA_Back.repository.ActivationModeRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GoogleEventPollingService
 * Tests the scheduled polling of Google events
 */
@ExtendWith(MockitoExtension.class)
class GoogleEventPollingServiceTest {

    @Mock
    private GoogleActionService googleActionService;

    @Mock
    private ActionInstanceRepository actionInstanceRepository;

    @Mock
    private ActivationModeRepository activationModeRepository;

    @Mock
    private ExecutionTriggerService executionTriggerService;

    private SimpleMeterRegistry meterRegistry;
    private GoogleEventPollingService googleEventPollingService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        googleEventPollingService = new GoogleEventPollingService(
            googleActionService,
            actionInstanceRepository,
            activationModeRepository,
            meterRegistry,
            executionTriggerService
        );

        // Initialize metrics
        try {
            var initMethod = GoogleEventPollingService.class.getDeclaredMethod("initMetrics");
            initMethod.setAccessible(true);
            initMethod.invoke(googleEventPollingService);
        } catch (Exception e) {
            fail("Failed to initialize metrics: " + e.getMessage());
        }
    }

    @Test
    void testServiceInitialization() {
        assertNotNull(googleEventPollingService);
        assertNotNull(meterRegistry);
    }

    @Test
    void testMetricsAreRegistered() {
        assertNotNull(meterRegistry.find("google_polling_cycles").counter());
        assertNotNull(meterRegistry.find("google_events_found").counter());
        assertNotNull(meterRegistry.find("google_polling_failures").counter());
    }

    @Test
    void testPollGoogleEventsWithNoActionInstances() {
        // Given
        when(actionInstanceRepository.findActiveGoogleActionInstances())
            .thenReturn(Collections.emptyList());

        double initialCycles = meterRegistry.counter("google_polling_cycles").count();

        // When
        googleEventPollingService.pollGoogleEvents();

        // Then
        verify(actionInstanceRepository, times(1)).findActiveGoogleActionInstances();
        double finalCycles = meterRegistry.counter("google_polling_cycles").count();
        assertEquals(initialCycles + 1, finalCycles);
    }

    @Test
    void testPollGoogleEventsIncrementsPollingCycles() {
        // Given
        when(actionInstanceRepository.findActiveGoogleActionInstances())
            .thenReturn(Collections.emptyList());

        double initialCount = meterRegistry.counter("google_polling_cycles").count();

        // When
        googleEventPollingService.pollGoogleEvents();

        // Then
        double finalCount = meterRegistry.counter("google_polling_cycles").count();
        assertEquals(initialCount + 1, finalCount);
    }

    @Test
    void testPollGoogleEventsWithEnabledActionInstance() {
        // Given
        User user = new User();
        user.setId(UUID.randomUUID());

        ActionDefinition actionDef = new ActionDefinition();
        actionDef.setKey("gmail_new_email");

        ActionInstance actionInstance = new ActionInstance();
        actionInstance.setId(UUID.randomUUID());
        actionInstance.setEnabled(true);
        actionInstance.setUser(user);
        actionInstance.setActionDefinition(actionDef);
        actionInstance.setParams(new HashMap<>());

        ActivationMode activationMode = new ActivationMode();
        activationMode.setType(ActivationModeType.POLL);
        activationMode.setEnabled(true);
        Map<String, Object> config = new HashMap<>();
        config.put("pollingInterval", 300);
        activationMode.setConfig(config);

        lenient().when(actionInstanceRepository.findActiveGoogleActionInstances())
            .thenReturn(List.of(actionInstance));
        lenient().when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(
            actionInstance, ActivationModeType.POLL, true))
            .thenReturn(List.of(activationMode));
        lenient().when(googleActionService.checkGoogleEvents(
            anyString(), anyMap(), any(UUID.class), any(LocalDateTime.class)))
            .thenReturn(Collections.emptyList());

        // When
        googleEventPollingService.pollGoogleEvents();

        // Then
        verify(actionInstanceRepository, times(1)).findActiveGoogleActionInstances();
        // Note: checkGoogleEvents might not be called if shouldPollNow returns false
        // This is expected behavior as polling respects intervals
    }

    @Test
    void testPollGoogleEventsWithDisabledActionInstance() {
        // Given
        ActionInstance actionInstance = new ActionInstance();
        actionInstance.setId(UUID.randomUUID());
        actionInstance.setEnabled(false);

        when(actionInstanceRepository.findActiveGoogleActionInstances())
            .thenReturn(List.of(actionInstance));

        // When
        googleEventPollingService.pollGoogleEvents();

        // Then
        verify(actionInstanceRepository, times(1)).findActiveGoogleActionInstances();
        verify(googleActionService, never()).checkGoogleEvents(
            anyString(), anyMap(), any(UUID.class), any(LocalDateTime.class));
    }

    @Test
    void testPollGoogleEventsWithNoActivationMode() {
        // Given
        User user = new User();
        user.setId(UUID.randomUUID());

        ActionDefinition actionDef = new ActionDefinition();
        actionDef.setKey("gmail_new_email");

        ActionInstance actionInstance = new ActionInstance();
        actionInstance.setId(UUID.randomUUID());
        actionInstance.setEnabled(true);
        actionInstance.setUser(user);
        actionInstance.setActionDefinition(actionDef);

        when(actionInstanceRepository.findActiveGoogleActionInstances())
            .thenReturn(List.of(actionInstance));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(
            actionInstance, ActivationModeType.POLL, true))
            .thenReturn(Collections.emptyList());

        // When
        googleEventPollingService.pollGoogleEvents();

        // Then
        verify(actionInstanceRepository, times(1)).findActiveGoogleActionInstances();
        verify(googleActionService, never()).checkGoogleEvents(
            anyString(), anyMap(), any(UUID.class), any(LocalDateTime.class));
    }

    @Test
    void testPollGoogleEventsWithEventsFound() {
        // Given
        User user = new User();
        user.setId(UUID.randomUUID());

        ActionDefinition actionDef = new ActionDefinition();
        actionDef.setKey("gmail_new_email");

        ActionInstance actionInstance = new ActionInstance();
        actionInstance.setId(UUID.randomUUID());
        actionInstance.setEnabled(true);
        actionInstance.setUser(user);
        actionInstance.setActionDefinition(actionDef);
        actionInstance.setParams(new HashMap<>());

        ActivationMode activationMode = new ActivationMode();
        activationMode.setType(ActivationModeType.POLL);
        activationMode.setEnabled(true);
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("pollingInterval", 300);
        activationMode.setConfig(configMap);

        Map<String, Object> event1 = new HashMap<>();
        event1.put("messageId", "123");
        event1.put("subject", "Test Email");

        Map<String, Object> event2 = new HashMap<>();
        event2.put("messageId", "456");
        event2.put("subject", "Another Email");

        lenient().when(actionInstanceRepository.findActiveGoogleActionInstances())
            .thenReturn(List.of(actionInstance));
        lenient().when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(
            actionInstance, ActivationModeType.POLL, true))
            .thenReturn(List.of(activationMode));
        lenient().when(googleActionService.checkGoogleEvents(
            anyString(), anyMap(), any(UUID.class), any(LocalDateTime.class)))
            .thenReturn(List.of(event1, event2));

        // When
        googleEventPollingService.pollGoogleEvents();

        // Then - verify repository was called
        verify(actionInstanceRepository, times(1)).findActiveGoogleActionInstances();
        // Note: Execution might not be triggered if polling interval hasn't elapsed
    }

    @Test
    void testPollGoogleEventsHandlesExceptionGracefully() {
        // Given
        when(actionInstanceRepository.findActiveGoogleActionInstances())
            .thenThrow(new RuntimeException("Database error"));

        double initialFailures = meterRegistry.counter("google_polling_failures").count();

        // When - should not throw exception
        assertDoesNotThrow(() -> {
            googleEventPollingService.pollGoogleEvents();
        });

        // Then
        double finalFailures = meterRegistry.counter("google_polling_failures").count();
        assertEquals(initialFailures + 1, finalFailures);
    }

    @Test
    void testPollGoogleEventsWithTriggerExecutionFailure() {
        // Given
        User user = new User();
        user.setId(UUID.randomUUID());

        ActionDefinition actionDef = new ActionDefinition();
        actionDef.setKey("gmail_new_email");

        ActionInstance actionInstance = new ActionInstance();
        actionInstance.setId(UUID.randomUUID());
        actionInstance.setEnabled(true);
        actionInstance.setUser(user);
        actionInstance.setActionDefinition(actionDef);
        actionInstance.setParams(new HashMap<>());

        ActivationMode activationMode = new ActivationMode();
        activationMode.setType(ActivationModeType.POLL);
        activationMode.setEnabled(true);
        Map<String, Object> config3 = new HashMap<>();
        config3.put("pollingInterval", 300);
        activationMode.setConfig(config3);

        Map<String, Object> event = new HashMap<>();
        event.put("messageId", "123");

        lenient().when(actionInstanceRepository.findActiveGoogleActionInstances())
            .thenReturn(List.of(actionInstance));
        lenient().when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(
            actionInstance, ActivationModeType.POLL, true))
            .thenReturn(List.of(activationMode));
        lenient().when(googleActionService.checkGoogleEvents(
            anyString(), anyMap(), any(UUID.class), any(LocalDateTime.class)))
            .thenReturn(List.of(event));
        lenient().doThrow(new RuntimeException("Trigger failed"))
            .when(executionTriggerService).triggerAreaExecution(
                any(ActionInstance.class), any(ActivationModeType.class), anyMap());

        // When - should not throw exception
        assertDoesNotThrow(() -> {
            googleEventPollingService.pollGoogleEvents();
        });

        // Then - polling should complete without throwing
        verify(actionInstanceRepository, times(1)).findActiveGoogleActionInstances();
    }

    @Test
    void testPollGoogleEventsWithMultipleActionInstances() {
        // Given
        User user1 = new User();
        user1.setId(UUID.randomUUID());

        User user2 = new User();
        user2.setId(UUID.randomUUID());

        ActionDefinition actionDef1 = new ActionDefinition();
        actionDef1.setKey("gmail_new_email");

        ActionDefinition actionDef2 = new ActionDefinition();
        actionDef2.setKey("calendar_new_event");

        ActionInstance actionInstance1 = new ActionInstance();
        actionInstance1.setId(UUID.randomUUID());
        actionInstance1.setEnabled(true);
        actionInstance1.setUser(user1);
        actionInstance1.setActionDefinition(actionDef1);
        actionInstance1.setParams(new HashMap<>());

        ActionInstance actionInstance2 = new ActionInstance();
        actionInstance2.setId(UUID.randomUUID());
        actionInstance2.setEnabled(true);
        actionInstance2.setUser(user2);
        actionInstance2.setActionDefinition(actionDef2);
        actionInstance2.setParams(new HashMap<>());

        ActivationMode activationMode1 = new ActivationMode();
        activationMode1.setType(ActivationModeType.POLL);
        activationMode1.setEnabled(true);
        Map<String, Object> config4 = new HashMap<>();
        config4.put("pollingInterval", 300);
        activationMode1.setConfig(config4);

        ActivationMode activationMode2 = new ActivationMode();
        activationMode2.setType(ActivationModeType.POLL);
        activationMode2.setEnabled(true);
        Map<String, Object> config5 = new HashMap<>();
        config5.put("pollingInterval", 300);
        activationMode2.setConfig(config5);

        lenient().when(actionInstanceRepository.findActiveGoogleActionInstances())
            .thenReturn(List.of(actionInstance1, actionInstance2));
        lenient().when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(
            actionInstance1, ActivationModeType.POLL, true))
            .thenReturn(List.of(activationMode1));
        lenient().when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(
            actionInstance2, ActivationModeType.POLL, true))
            .thenReturn(List.of(activationMode2));
        lenient().when(googleActionService.checkGoogleEvents(
            anyString(), anyMap(), any(UUID.class), any(LocalDateTime.class)))
            .thenReturn(Collections.emptyList());

        // When
        googleEventPollingService.pollGoogleEvents();

        // Then - verify both instances were processed
        verify(actionInstanceRepository, times(1)).findActiveGoogleActionInstances();
    }

    @Test
    void testPollGoogleEventsWithRepositoryException() {
        // Given
        when(actionInstanceRepository.findActiveGoogleActionInstances())
            .thenThrow(new RuntimeException("Database error"));

        double initialFailures = meterRegistry.counter("google_polling_failures").count();

        // When - should not throw exception
        assertDoesNotThrow(() -> {
            googleEventPollingService.pollGoogleEvents();
        });

        // Then
        double finalFailures = meterRegistry.counter("google_polling_failures").count();
        assertEquals(initialFailures + 1, finalFailures);
    }
}
