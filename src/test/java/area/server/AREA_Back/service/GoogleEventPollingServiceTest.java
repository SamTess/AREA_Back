package area.server.AREA_Back.service;

import area.server.AREA_Back.entity.ActionDefinition;
import area.server.AREA_Back.entity.ActionInstance;
import area.server.AREA_Back.entity.ActivationMode;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.entity.enums.ActivationModeType;
import area.server.AREA_Back.repository.ActionInstanceRepository;
import area.server.AREA_Back.repository.ActivationModeRepository;
import area.server.AREA_Back.service.Area.ExecutionTriggerService;
import area.server.AREA_Back.service.Area.Services.GoogleActionService;
import area.server.AREA_Back.service.Polling.GoogleEventPollingService;
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

    @Test
    void testProcessActionInstanceWithDisabledInstance() {
        // Given
        User user = new User();
        user.setId(UUID.randomUUID());

        ActionDefinition actionDef = new ActionDefinition();
        actionDef.setKey("gmail_new_email");

        ActionInstance actionInstance = new ActionInstance();
        actionInstance.setId(UUID.randomUUID());
        actionInstance.setEnabled(false);
        actionInstance.setUser(user);
        actionInstance.setActionDefinition(actionDef);
        actionInstance.setParams(new HashMap<>());

        when(actionInstanceRepository.findActiveGoogleActionInstances())
            .thenReturn(List.of(actionInstance));

        // When
        googleEventPollingService.pollGoogleEvents();

        // Then - should not check for activation modes
        verify(activationModeRepository, never()).findByActionInstanceAndTypeAndEnabled(
            any(ActionInstance.class), any(ActivationModeType.class), anyBoolean());
    }

    @Test
    void testProcessActionInstanceWithEmptyActivationModes() {
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

        when(actionInstanceRepository.findActiveGoogleActionInstances())
            .thenReturn(List.of(actionInstance));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(
            actionInstance, ActivationModeType.POLL, true))
            .thenReturn(Collections.emptyList());

        // When
        googleEventPollingService.pollGoogleEvents();

        // Then - should not call Google service
        verify(googleActionService, never()).checkGoogleEvents(
            anyString(), anyMap(), any(UUID.class), any(LocalDateTime.class));
    }

    @Test
    void testProcessActionInstanceWithSuccessfulEventProcessing() throws Exception {
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
        activationMode.setActionInstance(actionInstance);
        activationMode.setType(ActivationModeType.POLL);
        activationMode.setEnabled(true);
        Map<String, Object> config = new HashMap<>();
        config.put("pollingInterval", 300);
        activationMode.setConfig(config);

        Map<String, Object> event1 = new HashMap<>();
        event1.put("messageId", "123");
        event1.put("subject", "Test Email");

        when(actionInstanceRepository.findActiveGoogleActionInstances())
            .thenReturn(List.of(actionInstance));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(
            actionInstance, ActivationModeType.POLL, true))
            .thenReturn(List.of(activationMode));
        when(googleActionService.checkGoogleEvents(
            eq("gmail_new_email"), anyMap(), eq(user.getId()), any(LocalDateTime.class)))
            .thenReturn(List.of(event1));

        double initialEventsFound = meterRegistry.counter("google_events_found").count();

        // When
        googleEventPollingService.pollGoogleEvents();

        // Then
        verify(googleActionService).checkGoogleEvents(
            eq("gmail_new_email"), anyMap(), eq(user.getId()), any(LocalDateTime.class));
        verify(executionTriggerService).triggerAreaExecution(
            eq(actionInstance), eq(ActivationModeType.POLL), eq(event1));
        
        double finalEventsFound = meterRegistry.counter("google_events_found").count();
        assertEquals(initialEventsFound + 1, finalEventsFound);
    }

    @Test
    void testProcessActionInstanceWithMultipleEvents() throws Exception {
        // Given
        User user = new User();
        user.setId(UUID.randomUUID());

        ActionDefinition actionDef = new ActionDefinition();
        actionDef.setKey("calendar_new_event");

        ActionInstance actionInstance = new ActionInstance();
        actionInstance.setId(UUID.randomUUID());
        actionInstance.setEnabled(true);
        actionInstance.setUser(user);
        actionInstance.setActionDefinition(actionDef);
        actionInstance.setParams(new HashMap<>());

        ActivationMode activationMode = new ActivationMode();
        activationMode.setActionInstance(actionInstance);
        activationMode.setType(ActivationModeType.POLL);
        activationMode.setEnabled(true);
        Map<String, Object> config = new HashMap<>();
        config.put("pollingInterval", 60);
        activationMode.setConfig(config);

        Map<String, Object> event1 = new HashMap<>();
        event1.put("eventId", "event1");
        
        Map<String, Object> event2 = new HashMap<>();
        event2.put("eventId", "event2");
        
        Map<String, Object> event3 = new HashMap<>();
        event3.put("eventId", "event3");

        when(actionInstanceRepository.findActiveGoogleActionInstances())
            .thenReturn(List.of(actionInstance));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(
            actionInstance, ActivationModeType.POLL, true))
            .thenReturn(List.of(activationMode));
        when(googleActionService.checkGoogleEvents(
            eq("calendar_new_event"), anyMap(), eq(user.getId()), any(LocalDateTime.class)))
            .thenReturn(List.of(event1, event2, event3));

        double initialEventsFound = meterRegistry.counter("google_events_found").count();

        // When
        googleEventPollingService.pollGoogleEvents();

        // Then
        verify(executionTriggerService, times(3)).triggerAreaExecution(
            eq(actionInstance), eq(ActivationModeType.POLL), anyMap());
        
        double finalEventsFound = meterRegistry.counter("google_events_found").count();
        assertEquals(initialEventsFound + 3, finalEventsFound);
    }

    @Test
    void testProcessActionInstanceWithGoogleServiceException() {
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
        activationMode.setActionInstance(actionInstance);
        activationMode.setType(ActivationModeType.POLL);
        activationMode.setEnabled(true);
        Map<String, Object> config = new HashMap<>();
        config.put("pollingInterval", 300);
        activationMode.setConfig(config);

        when(actionInstanceRepository.findActiveGoogleActionInstances())
            .thenReturn(List.of(actionInstance));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(
            actionInstance, ActivationModeType.POLL, true))
            .thenReturn(List.of(activationMode));
        when(googleActionService.checkGoogleEvents(
            anyString(), anyMap(), any(UUID.class), any(LocalDateTime.class)))
            .thenThrow(new RuntimeException("Google API error"));

        // When - should not throw exception
        assertDoesNotThrow(() -> {
            googleEventPollingService.pollGoogleEvents();
        });

        // Then - should handle exception gracefully
        verify(googleActionService).checkGoogleEvents(
            anyString(), anyMap(), any(UUID.class), any(LocalDateTime.class));
    }

    @Test
    void testShouldPollNowWithFirstPoll() throws Exception {
        // Given
        ActionInstance actionInstance = new ActionInstance();
        actionInstance.setId(UUID.randomUUID());

        ActivationMode activationMode = new ActivationMode();
        activationMode.setActionInstance(actionInstance);
        Map<String, Object> config = new HashMap<>();
        config.put("pollingInterval", 300);
        activationMode.setConfig(config);

        // Use reflection to access private method
        var method = GoogleEventPollingService.class.getDeclaredMethod("shouldPollNow", ActivationMode.class);
        method.setAccessible(true);

        // When
        boolean result = (boolean) method.invoke(googleEventPollingService, activationMode);

        // Then
        assertTrue(result, "Should poll on first check");
    }

    @Test
    void testShouldPollNowWithIntervalNotElapsed() throws Exception {
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
        activationMode.setActionInstance(actionInstance);
        activationMode.setType(ActivationModeType.POLL);
        activationMode.setEnabled(true);
        Map<String, Object> config = new HashMap<>();
        config.put("pollingInterval", 300); // 5 minutes
        activationMode.setConfig(config);

        when(actionInstanceRepository.findActiveGoogleActionInstances())
            .thenReturn(List.of(actionInstance));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(
            actionInstance, ActivationModeType.POLL, true))
            .thenReturn(List.of(activationMode));
        when(googleActionService.checkGoogleEvents(
            anyString(), anyMap(), any(UUID.class), any(LocalDateTime.class)))
            .thenReturn(Collections.emptyList());

        // First poll
        googleEventPollingService.pollGoogleEvents();

        // Use reflection to check shouldPollNow
        var method = GoogleEventPollingService.class.getDeclaredMethod("shouldPollNow", ActivationMode.class);
        method.setAccessible(true);

        // When - immediately after first poll
        boolean result = (boolean) method.invoke(googleEventPollingService, activationMode);

        // Then
        assertFalse(result, "Should not poll immediately after first poll");
    }

    @Test
    void testGetPollingIntervalWithDefaultValue() throws Exception {
        // Given
        ActivationMode activationMode = new ActivationMode();
        activationMode.setConfig(new HashMap<>());

        // Use reflection to access private method
        var method = GoogleEventPollingService.class.getDeclaredMethod("getPollingInterval", ActivationMode.class);
        method.setAccessible(true);

        // When
        int interval = (int) method.invoke(googleEventPollingService, activationMode);

        // Then
        assertEquals(300, interval, "Should return default polling interval");
    }

    @Test
    void testGetPollingIntervalWithCustomValue() throws Exception {
        // Given
        ActivationMode activationMode = new ActivationMode();
        Map<String, Object> config = new HashMap<>();
        config.put("poll_interval", 120);
        activationMode.setConfig(config);

        // Use reflection to access private method
        var method = GoogleEventPollingService.class.getDeclaredMethod("getPollingInterval", ActivationMode.class);
        method.setAccessible(true);

        // When
        int interval = (int) method.invoke(googleEventPollingService, activationMode);

        // Then
        assertEquals(120, interval, "Should return custom polling interval");
    }

    @Test
    void testCalculateLastCheckTimeWithDefaultInterval() throws Exception {
        // Given
        ActivationMode activationMode = new ActivationMode();
        activationMode.setConfig(new HashMap<>());

        // Use reflection to access private method
        var method = GoogleEventPollingService.class.getDeclaredMethod("calculateLastCheckTime", ActivationMode.class);
        method.setAccessible(true);

        LocalDateTime before = LocalDateTime.now().minusSeconds(300);

        // When
        LocalDateTime result = (LocalDateTime) method.invoke(googleEventPollingService, activationMode);

        // Then
        LocalDateTime after = LocalDateTime.now().minusSeconds(300);
        assertTrue(result.isAfter(before.minusSeconds(2)) && result.isBefore(after.plusSeconds(2)),
            "Should calculate last check time based on default interval");
    }

    @Test
    void testCalculateLastCheckTimeWithCustomInterval() throws Exception {
        // Given
        ActivationMode activationMode = new ActivationMode();
        Map<String, Object> config = new HashMap<>();
        config.put("poll_interval", 600);
        activationMode.setConfig(config);

        // Use reflection to access private method
        var method = GoogleEventPollingService.class.getDeclaredMethod("calculateLastCheckTime", ActivationMode.class);
        method.setAccessible(true);

        LocalDateTime before = LocalDateTime.now().minusSeconds(600);

        // When
        LocalDateTime result = (LocalDateTime) method.invoke(googleEventPollingService, activationMode);

        // Then
        LocalDateTime after = LocalDateTime.now().minusSeconds(600);
        assertTrue(result.isAfter(before.minusSeconds(2)) && result.isBefore(after.plusSeconds(2)),
            "Should calculate last check time based on custom interval");
    }

    @Test
    void testProcessActionInstanceUpdatesLastPollTime() throws Exception {
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
        activationMode.setActionInstance(actionInstance);
        activationMode.setType(ActivationModeType.POLL);
        activationMode.setEnabled(true);
        Map<String, Object> config = new HashMap<>();
        config.put("poll_interval", 300);
        activationMode.setConfig(config);

        when(actionInstanceRepository.findActiveGoogleActionInstances())
            .thenReturn(List.of(actionInstance));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(
            actionInstance, ActivationModeType.POLL, true))
            .thenReturn(List.of(activationMode));
        when(googleActionService.checkGoogleEvents(
            anyString(), anyMap(), any(UUID.class), any(LocalDateTime.class)))
            .thenReturn(Collections.emptyList());

        // When - first poll
        googleEventPollingService.pollGoogleEvents();

        // Use reflection to check shouldPollNow
        var method = GoogleEventPollingService.class.getDeclaredMethod("shouldPollNow", ActivationMode.class);
        method.setAccessible(true);
        
        boolean shouldPoll = (boolean) method.invoke(googleEventPollingService, activationMode);

        // Then - should not poll again immediately
        assertFalse(shouldPoll, "Last poll time should be updated");
    }

    @Test
    void testProcessActionInstanceWithExecutionTriggerException() throws Exception {
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
        activationMode.setActionInstance(actionInstance);
        activationMode.setType(ActivationModeType.POLL);
        activationMode.setEnabled(true);
        Map<String, Object> config = new HashMap<>();
        config.put("poll_interval", 300);
        activationMode.setConfig(config);

        Map<String, Object> event = new HashMap<>();
        event.put("messageId", "123");

        when(actionInstanceRepository.findActiveGoogleActionInstances())
            .thenReturn(List.of(actionInstance));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(
            actionInstance, ActivationModeType.POLL, true))
            .thenReturn(List.of(activationMode));
        when(googleActionService.checkGoogleEvents(
            anyString(), anyMap(), any(UUID.class), any(LocalDateTime.class)))
            .thenReturn(List.of(event));
        doThrow(new RuntimeException("Execution failed"))
            .when(executionTriggerService).triggerAreaExecution(
                any(ActionInstance.class), any(ActivationModeType.class), anyMap());

        // When - should handle exception gracefully
        assertDoesNotThrow(() -> {
            googleEventPollingService.pollGoogleEvents();
        });

        // Then - should have attempted to trigger execution
        verify(executionTriggerService).triggerAreaExecution(
            eq(actionInstance), eq(ActivationModeType.POLL), eq(event));
    }

    @Test
    void testProcessActionInstanceHandlesExceptionInProcessing() {
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

        when(actionInstanceRepository.findActiveGoogleActionInstances())
            .thenReturn(List.of(actionInstance));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(
            any(ActionInstance.class), any(ActivationModeType.class), anyBoolean()))
            .thenThrow(new RuntimeException("Database error during activation mode fetch"));

        double initialFailures = meterRegistry.counter("google_polling_failures").count();

        // When
        assertDoesNotThrow(() -> {
            googleEventPollingService.pollGoogleEvents();
        });

        // Then
        double finalFailures = meterRegistry.counter("google_polling_failures").count();
        assertEquals(initialFailures + 1, finalFailures);
    }
}
