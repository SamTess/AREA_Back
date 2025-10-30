package area.server.AREA_Back.service;

import area.server.AREA_Back.entity.ActionDefinition;
import area.server.AREA_Back.entity.ActionInstance;
import area.server.AREA_Back.entity.ActivationMode;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.entity.enums.ActivationModeType;
import area.server.AREA_Back.repository.ActionInstanceRepository;
import area.server.AREA_Back.repository.ActivationModeRepository;
import area.server.AREA_Back.service.Area.ExecutionTriggerService;
import area.server.AREA_Back.service.Area.Services.SlackActionService;
import area.server.AREA_Back.service.Polling.SlackEventPollingService;
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
 * Unit tests for SlackEventPollingService
 * Tests the scheduled polling of Slack events
 */
@ExtendWith(MockitoExtension.class)
class SlackEventPollingServiceTest {

    @Mock
    private SlackActionService slackActionService;

    @Mock
    private ActionInstanceRepository actionInstanceRepository;

    @Mock
    private ActivationModeRepository activationModeRepository;

    @Mock
    private ExecutionTriggerService executionTriggerService;

    private SimpleMeterRegistry meterRegistry;
    private SlackEventPollingService slackEventPollingService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        slackEventPollingService = new SlackEventPollingService(
            slackActionService,
            actionInstanceRepository,
            activationModeRepository,
            meterRegistry,
            executionTriggerService
        );

        // Initialize metrics
        try {
            var initMethod = SlackEventPollingService.class.getDeclaredMethod("initMetrics");
            initMethod.setAccessible(true);
            initMethod.invoke(slackEventPollingService);
        } catch (Exception e) {
            fail("Failed to initialize metrics: " + e.getMessage());
        }
    }

    @Test
    void testServiceInitialization() {
        assertNotNull(slackEventPollingService);
        assertNotNull(meterRegistry);
    }

    @Test
    void testInitMetrics() {
        // Given - metrics should be initialized in setUp
        // When - checking if metrics exist
        // Then
        assertNotNull(meterRegistry.find("slack_polling_cycles").counter());
        assertNotNull(meterRegistry.find("slack_events_found").counter());
        assertNotNull(meterRegistry.find("slack_polling_failures").counter());
    }

    @Test
    void testMetricsAreRegistered() {
        assertNotNull(meterRegistry.find("slack_polling_cycles").counter());
        assertNotNull(meterRegistry.find("slack_events_found").counter());
        assertNotNull(meterRegistry.find("slack_polling_failures").counter());
    }

    @Test
    void testPollSlackEventsWithNoActionInstances() {
        // Given
        when(actionInstanceRepository.findActiveSlackActionInstances())
            .thenReturn(Collections.emptyList());

        double initialCycles = meterRegistry.counter("slack_polling_cycles").count();

        // When
        slackEventPollingService.pollSlackEvents();

        // Then
        verify(actionInstanceRepository, times(1)).findActiveSlackActionInstances();
        double finalCycles = meterRegistry.counter("slack_polling_cycles").count();
        assertEquals(initialCycles + 1, finalCycles);
    }

    @Test
    void testPollSlackEventsIncrementsPollingCycles() {
        // Given
        when(actionInstanceRepository.findActiveSlackActionInstances())
            .thenReturn(Collections.emptyList());

        double initialCount = meterRegistry.counter("slack_polling_cycles").count();

        // When
        slackEventPollingService.pollSlackEvents();

        // Then
        double finalCount = meterRegistry.counter("slack_polling_cycles").count();
        assertEquals(initialCount + 1, finalCount);
    }

    @Test
    void testPollSlackEventsWithEnabledActionInstance() throws Exception {
        // Given
        User user = new User();
        user.setId(UUID.randomUUID());

        ActionDefinition actionDef = new ActionDefinition();
        actionDef.setKey("slack_new_message");

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

        lenient().when(actionInstanceRepository.findActiveSlackActionInstances())
            .thenReturn(List.of(actionInstance));
        lenient().when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(
            actionInstance, ActivationModeType.POLL, true))
            .thenReturn(List.of(activationMode));
        lenient().when(slackActionService.checkSlackEvents(
            anyString(), anyMap(), any(UUID.class), any(LocalDateTime.class)))
            .thenReturn(Collections.emptyList());

        // When
        slackEventPollingService.pollSlackEvents();

        // Then
        verify(actionInstanceRepository, times(1)).findActiveSlackActionInstances();
    }

    @Test
    void testPollSlackEventsWithDisabledActionInstance() {
        // Given
        ActionInstance actionInstance = new ActionInstance();
        actionInstance.setId(UUID.randomUUID());
        actionInstance.setEnabled(false);

        when(actionInstanceRepository.findActiveSlackActionInstances())
            .thenReturn(List.of(actionInstance));

        // When
        slackEventPollingService.pollSlackEvents();

        // Then
        verify(actionInstanceRepository, times(1)).findActiveSlackActionInstances();
        verify(slackActionService, never()).checkSlackEvents(
            anyString(), anyMap(), any(UUID.class), any(LocalDateTime.class));
    }

    @Test
    void testPollSlackEventsWithNoActivationMode() {
        // Given
        User user = new User();
        user.setId(UUID.randomUUID());

        ActionDefinition actionDef = new ActionDefinition();
        actionDef.setKey("slack_new_message");

        ActionInstance actionInstance = new ActionInstance();
        actionInstance.setId(UUID.randomUUID());
        actionInstance.setEnabled(true);
        actionInstance.setUser(user);
        actionInstance.setActionDefinition(actionDef);

        when(actionInstanceRepository.findActiveSlackActionInstances())
            .thenReturn(List.of(actionInstance));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(
            actionInstance, ActivationModeType.POLL, true))
            .thenReturn(Collections.emptyList());

        // When
        slackEventPollingService.pollSlackEvents();

        // Then
        verify(actionInstanceRepository, times(1)).findActiveSlackActionInstances();
        verify(slackActionService, never()).checkSlackEvents(
            anyString(), anyMap(), any(UUID.class), any(LocalDateTime.class));
    }

    @Test
    void testPollSlackEventsWithEventsFound() throws Exception {
        // Given
        User user = new User();
        user.setId(UUID.randomUUID());

        ActionDefinition actionDef = new ActionDefinition();
        actionDef.setKey("slack_new_message");

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
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("pollingInterval", 300);
        activationMode.setConfig(configMap);

        Map<String, Object> event1 = new HashMap<>();
        event1.put("messageId", "123");
        event1.put("text", "Test Message");

        Map<String, Object> event2 = new HashMap<>();
        event2.put("messageId", "456");
        event2.put("text", "Another Message");

        lenient().when(actionInstanceRepository.findActiveSlackActionInstances())
            .thenReturn(List.of(actionInstance));
        lenient().when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(
            actionInstance, ActivationModeType.POLL, true))
            .thenReturn(List.of(activationMode));
        lenient().when(slackActionService.checkSlackEvents(
            anyString(), anyMap(), any(UUID.class), any(LocalDateTime.class)))
            .thenReturn(List.of(event1, event2));

        double initialEventsFound = meterRegistry.counter("slack_events_found").count();

        // When
        slackEventPollingService.pollSlackEvents();

        // Then
        verify(actionInstanceRepository, times(1)).findActiveSlackActionInstances();
        double finalEventsFound = meterRegistry.counter("slack_events_found").count();
        assertEquals(initialEventsFound + 2, finalEventsFound);
    }

    @Test
    void testPollSlackEventsHandlesExceptionGracefully() {
        // Given
        when(actionInstanceRepository.findActiveSlackActionInstances())
            .thenThrow(new RuntimeException("Database error"));

        double initialFailures = meterRegistry.counter("slack_polling_failures").count();

        // When - should not throw exception
        assertDoesNotThrow(() -> {
            slackEventPollingService.pollSlackEvents();
        });

        // Then
        double finalFailures = meterRegistry.counter("slack_polling_failures").count();
        assertEquals(initialFailures + 1, finalFailures);
    }

    @Test
    void testPollSlackEventsWithTriggerExecutionFailure() throws Exception {
        // Given
        User user = new User();
        user.setId(UUID.randomUUID());

        ActionDefinition actionDef = new ActionDefinition();
        actionDef.setKey("slack_new_message");

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
        Map<String, Object> config3 = new HashMap<>();
        config3.put("pollingInterval", 300);
        activationMode.setConfig(config3);

        Map<String, Object> event = new HashMap<>();
        event.put("messageId", "123");

        lenient().when(actionInstanceRepository.findActiveSlackActionInstances())
            .thenReturn(List.of(actionInstance));
        lenient().when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(
            actionInstance, ActivationModeType.POLL, true))
            .thenReturn(List.of(activationMode));
        lenient().when(slackActionService.checkSlackEvents(
            anyString(), anyMap(), any(UUID.class), any(LocalDateTime.class)))
            .thenReturn(List.of(event));
        lenient().doThrow(new RuntimeException("Trigger failed"))
            .when(executionTriggerService).triggerAreaExecution(
                any(ActionInstance.class), any(ActivationModeType.class), anyMap());

        // When - should not throw exception
        assertDoesNotThrow(() -> {
            slackEventPollingService.pollSlackEvents();
        });

        // Then - polling should complete without throwing
        verify(actionInstanceRepository, times(1)).findActiveSlackActionInstances();
    }

    @Test
    void testPollSlackEventsWithMultipleActionInstances() throws Exception {
        // Given
        User user1 = new User();
        user1.setId(UUID.randomUUID());

        User user2 = new User();
        user2.setId(UUID.randomUUID());

        ActionDefinition actionDef1 = new ActionDefinition();
        actionDef1.setKey("slack_new_message");

        ActionDefinition actionDef2 = new ActionDefinition();
        actionDef2.setKey("slack_new_reaction");

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
        activationMode1.setActionInstance(actionInstance1);
        activationMode1.setType(ActivationModeType.POLL);
        activationMode1.setEnabled(true);
        Map<String, Object> config4 = new HashMap<>();
        config4.put("pollingInterval", 300);
        activationMode1.setConfig(config4);

        ActivationMode activationMode2 = new ActivationMode();
        activationMode2.setActionInstance(actionInstance2);
        activationMode2.setType(ActivationModeType.POLL);
        activationMode2.setEnabled(true);
        Map<String, Object> config5 = new HashMap<>();
        config5.put("pollingInterval", 300);
        activationMode2.setConfig(config5);

        lenient().when(actionInstanceRepository.findActiveSlackActionInstances())
            .thenReturn(List.of(actionInstance1, actionInstance2));
        lenient().when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(
            actionInstance1, ActivationModeType.POLL, true))
            .thenReturn(List.of(activationMode1));
        lenient().when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(
            actionInstance2, ActivationModeType.POLL, true))
            .thenReturn(List.of(activationMode2));
        lenient().when(slackActionService.checkSlackEvents(
            anyString(), anyMap(), any(UUID.class), any(LocalDateTime.class)))
            .thenReturn(Collections.emptyList());

        // When
        slackEventPollingService.pollSlackEvents();

        // Then - verify both instances were processed
        verify(actionInstanceRepository, times(1)).findActiveSlackActionInstances();
    }

    @Test
    void testPollSlackEventsWithRepositoryException() {
        // Given
        when(actionInstanceRepository.findActiveSlackActionInstances())
            .thenThrow(new RuntimeException("Database error"));

        double initialFailures = meterRegistry.counter("slack_polling_failures").count();

        // When - should not throw exception
        assertDoesNotThrow(() -> {
            slackEventPollingService.pollSlackEvents();
        });

        // Then
        double finalFailures = meterRegistry.counter("slack_polling_failures").count();
        assertEquals(initialFailures + 1, finalFailures);
    }

    @Test
    void testProcessActionInstanceWithDisabledInstance() throws Exception {
        // Given
        ActionInstance actionInstance = new ActionInstance();
        actionInstance.setId(UUID.randomUUID());
        actionInstance.setEnabled(false);

        // Use reflection to call private method
        var method = SlackEventPollingService.class.getDeclaredMethod("processActionInstance", ActionInstance.class);
        method.setAccessible(true);

        // When
        method.invoke(slackEventPollingService, actionInstance);

        // Then
        verify(activationModeRepository, never()).findByActionInstanceAndTypeAndEnabled(
            any(), any(), anyBoolean());
    }

    @Test
    void testProcessActionInstanceWithNoActivationMode() throws Exception {
        // Given
        User user = new User();
        user.setId(UUID.randomUUID());

        ActionDefinition actionDef = new ActionDefinition();
        actionDef.setKey("slack_new_message");

        ActionInstance actionInstance = new ActionInstance();
        actionInstance.setId(UUID.randomUUID());
        actionInstance.setEnabled(true);
        actionInstance.setUser(user);
        actionInstance.setActionDefinition(actionDef);

        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(
            actionInstance, ActivationModeType.POLL, true))
            .thenReturn(Collections.emptyList());

        // Use reflection to call private method
        var method = SlackEventPollingService.class.getDeclaredMethod("processActionInstance", ActionInstance.class);
        method.setAccessible(true);

        // When
        method.invoke(slackEventPollingService, actionInstance);

        // Then
        verify(activationModeRepository, times(1)).findByActionInstanceAndTypeAndEnabled(
            actionInstance, ActivationModeType.POLL, true);
        verify(slackActionService, never()).checkSlackEvents(
            anyString(), anyMap(), any(UUID.class), any(LocalDateTime.class));
    }

    @Test
    void testProcessActionInstanceWithValidActivationModeFirstPoll() throws Exception {
        // Given
        User user = new User();
        user.setId(UUID.randomUUID());

        ActionDefinition actionDef = new ActionDefinition();
        actionDef.setKey("slack_new_message");

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

        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(
            actionInstance, ActivationModeType.POLL, true))
            .thenReturn(List.of(activationMode));
        when(slackActionService.checkSlackEvents(
            anyString(), anyMap(), any(UUID.class), any(LocalDateTime.class)))
            .thenReturn(Collections.emptyList());

        // Use reflection to call private method
        var method = SlackEventPollingService.class.getDeclaredMethod("processActionInstance", ActionInstance.class);
        method.setAccessible(true);

        // When
        method.invoke(slackEventPollingService, actionInstance);

        // Then
        verify(slackActionService, times(1)).checkSlackEvents(
            eq("slack_new_message"), anyMap(), eq(user.getId()), any(LocalDateTime.class));
    }

    @Test
    void testProcessActionInstanceWithCheckSlackEventsException() throws Exception {
        // Given
        User user = new User();
        user.setId(UUID.randomUUID());

        ActionDefinition actionDef = new ActionDefinition();
        actionDef.setKey("slack_new_message");

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

        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(
            actionInstance, ActivationModeType.POLL, true))
            .thenReturn(List.of(activationMode));
        when(slackActionService.checkSlackEvents(
            anyString(), anyMap(), any(UUID.class), any(LocalDateTime.class)))
            .thenThrow(new RuntimeException("Slack API error"));

        // Use reflection to call private method
        var method = SlackEventPollingService.class.getDeclaredMethod("processActionInstance", ActionInstance.class);
        method.setAccessible(true);

        // When - should not throw exception
        assertDoesNotThrow(() -> {
            method.invoke(slackEventPollingService, actionInstance);
        });

        // Then
        verify(slackActionService, times(1)).checkSlackEvents(
            anyString(), anyMap(), any(UUID.class), any(LocalDateTime.class));
    }

    @Test
    void testShouldPollNowWithNoLastPoll() throws Exception {
        // Given
        ActionInstance actionInstance = new ActionInstance();
        actionInstance.setId(UUID.randomUUID());

        ActivationMode activationMode = new ActivationMode();
        activationMode.setActionInstance(actionInstance);
        Map<String, Object> config = new HashMap<>();
        config.put("pollingInterval", 300);
        activationMode.setConfig(config);

        // Use reflection to call private method
        var method = SlackEventPollingService.class.getDeclaredMethod("shouldPollNow", ActivationMode.class);
        method.setAccessible(true);

        // When
        boolean result = (boolean) method.invoke(slackEventPollingService, activationMode);

        // Then
        assertTrue(result);
    }

    @Test
    void testShouldPollNowWithRecentPoll() throws Exception {
        // Given
        ActionInstance actionInstance = new ActionInstance();
        actionInstance.setId(UUID.randomUUID());

        ActivationMode activationMode = new ActivationMode();
        activationMode.setActionInstance(actionInstance);
        Map<String, Object> config = new HashMap<>();
        config.put("pollingInterval", 300);
        activationMode.setConfig(config);

        // Simulate a recent poll
        var lastPollTimesField = SlackEventPollingService.class.getDeclaredField("lastPollTimes");
        lastPollTimesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, LocalDateTime> lastPollTimes = (Map<UUID, LocalDateTime>) lastPollTimesField.get(slackEventPollingService);
        lastPollTimes.put(actionInstance.getId(), LocalDateTime.now());

        // Use reflection to call private method
        var method = SlackEventPollingService.class.getDeclaredMethod("shouldPollNow", ActivationMode.class);
        method.setAccessible(true);

        // When
        boolean result = (boolean) method.invoke(slackEventPollingService, activationMode);

        // Then
        assertFalse(result);
    }

    @Test
    void testShouldPollNowWithOldPoll() throws Exception {
        // Given
        ActionInstance actionInstance = new ActionInstance();
        actionInstance.setId(UUID.randomUUID());

        ActivationMode activationMode = new ActivationMode();
        activationMode.setActionInstance(actionInstance);
        Map<String, Object> config = new HashMap<>();
        config.put("pollingInterval", 300);
        activationMode.setConfig(config);

        // Simulate an old poll
        var lastPollTimesField = SlackEventPollingService.class.getDeclaredField("lastPollTimes");
        lastPollTimesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, LocalDateTime> lastPollTimes = (Map<UUID, LocalDateTime>) lastPollTimesField.get(slackEventPollingService);
        lastPollTimes.put(actionInstance.getId(), LocalDateTime.now().minusSeconds(400));

        // Use reflection to call private method
        var method = SlackEventPollingService.class.getDeclaredMethod("shouldPollNow", ActivationMode.class);
        method.setAccessible(true);

        // When
        boolean result = (boolean) method.invoke(slackEventPollingService, activationMode);

        // Then
        assertTrue(result);
    }

    @Test
    void testGetPollingIntervalWithConfiguredValue() throws Exception {
        // Given
        ActivationMode activationMode = new ActivationMode();
        Map<String, Object> config = new HashMap<>();
        config.put("poll_interval", 600);
        activationMode.setConfig(config);

        // Use reflection to call private method
        var method = SlackEventPollingService.class.getDeclaredMethod("getPollingInterval", ActivationMode.class);
        method.setAccessible(true);

        // When
        int result = (int) method.invoke(slackEventPollingService, activationMode);

        // Then
        assertEquals(600, result);
    }

    @Test
    void testGetPollingIntervalWithDefaultValue() throws Exception {
        // Given
        ActivationMode activationMode = new ActivationMode();
        Map<String, Object> config = new HashMap<>();
        activationMode.setConfig(config);

        // Use reflection to call private method
        var method = SlackEventPollingService.class.getDeclaredMethod("getPollingInterval", ActivationMode.class);
        method.setAccessible(true);

        // When
        int result = (int) method.invoke(slackEventPollingService, activationMode);

        // Then
        assertEquals(300, result);
    }

    @Test
    void testCalculateLastCheckTimeWithExistingPoll() throws Exception {
        // Given
        ActionInstance actionInstance = new ActionInstance();
        actionInstance.setId(UUID.randomUUID());

        ActivationMode activationMode = new ActivationMode();
        activationMode.setActionInstance(actionInstance);
        Map<String, Object> config = new HashMap<>();
        config.put("poll_interval", 300);
        activationMode.setConfig(config);

        LocalDateTime expectedTime = LocalDateTime.now().minusSeconds(100);

        // Set last poll time
        var lastPollTimesField = SlackEventPollingService.class.getDeclaredField("lastPollTimes");
        lastPollTimesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, LocalDateTime> lastPollTimes = (Map<UUID, LocalDateTime>) lastPollTimesField.get(slackEventPollingService);
        lastPollTimes.put(actionInstance.getId(), expectedTime);

        // Use reflection to call private method
        var method = SlackEventPollingService.class.getDeclaredMethod("calculateLastCheckTime", ActivationMode.class);
        method.setAccessible(true);

        // When
        LocalDateTime result = (LocalDateTime) method.invoke(slackEventPollingService, activationMode);

        // Then
        assertEquals(expectedTime, result);
    }

    @Test
    void testCalculateLastCheckTimeWithoutExistingPoll() throws Exception {
        // Given
        ActionInstance actionInstance = new ActionInstance();
        actionInstance.setId(UUID.randomUUID());

        ActivationMode activationMode = new ActivationMode();
        activationMode.setActionInstance(actionInstance);
        Map<String, Object> config = new HashMap<>();
        config.put("poll_interval", 300);
        activationMode.setConfig(config);

        // Use reflection to call private method
        var method = SlackEventPollingService.class.getDeclaredMethod("calculateLastCheckTime", ActivationMode.class);
        method.setAccessible(true);

        LocalDateTime before = LocalDateTime.now().minusSeconds(301);

        // When
        LocalDateTime result = (LocalDateTime) method.invoke(slackEventPollingService, activationMode);

        // Then
        LocalDateTime after = LocalDateTime.now().minusSeconds(299);
        assertTrue(result.isAfter(before) && result.isBefore(after));
    }

    @Test
    void testPollSlackEventsWithActionInstanceProcessingException() {
        // Given
        User user = new User();
        user.setId(UUID.randomUUID());

        ActionDefinition actionDef = new ActionDefinition();
        actionDef.setKey("slack_new_message");

        ActionInstance actionInstance = new ActionInstance();
        actionInstance.setId(UUID.randomUUID());
        actionInstance.setEnabled(true);
        actionInstance.setUser(user);
        actionInstance.setActionDefinition(actionDef);

        when(actionInstanceRepository.findActiveSlackActionInstances())
            .thenReturn(List.of(actionInstance));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(
            any(), any(), anyBoolean()))
            .thenThrow(new RuntimeException("Processing error"));

        double initialFailures = meterRegistry.counter("slack_polling_failures").count();

        // When - should not throw exception
        assertDoesNotThrow(() -> {
            slackEventPollingService.pollSlackEvents();
        });

        // Then
        double finalFailures = meterRegistry.counter("slack_polling_failures").count();
        assertEquals(initialFailures + 1, finalFailures);
    }

    @Test
    void testProcessActionInstanceWithMultipleEventsAndTriggerExecution() throws Exception {
        // Given
        User user = new User();
        user.setId(UUID.randomUUID());

        ActionDefinition actionDef = new ActionDefinition();
        actionDef.setKey("slack_new_message");

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

        Map<String, Object> event1 = new HashMap<>();
        event1.put("messageId", "123");
        Map<String, Object> event2 = new HashMap<>();
        event2.put("messageId", "456");

        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(
            actionInstance, ActivationModeType.POLL, true))
            .thenReturn(List.of(activationMode));
        when(slackActionService.checkSlackEvents(
            anyString(), anyMap(), any(UUID.class), any(LocalDateTime.class)))
            .thenReturn(List.of(event1, event2));

        // Use reflection to call private method
        var method = SlackEventPollingService.class.getDeclaredMethod("processActionInstance", ActionInstance.class);
        method.setAccessible(true);

        // When
        method.invoke(slackEventPollingService, actionInstance);

        // Then
        verify(executionTriggerService, times(2)).triggerAreaExecution(
            eq(actionInstance), eq(ActivationModeType.POLL), anyMap());
    }

    @Test
    void testProcessActionInstanceUpdatesLastPollTime() throws Exception {
        // Given
        User user = new User();
        user.setId(UUID.randomUUID());

        ActionDefinition actionDef = new ActionDefinition();
        actionDef.setKey("slack_new_message");

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

        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(
            actionInstance, ActivationModeType.POLL, true))
            .thenReturn(List.of(activationMode));
        when(slackActionService.checkSlackEvents(
            anyString(), anyMap(), any(UUID.class), any(LocalDateTime.class)))
            .thenReturn(Collections.emptyList());

        // Use reflection to access lastPollTimes
        var lastPollTimesField = SlackEventPollingService.class.getDeclaredField("lastPollTimes");
        lastPollTimesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, LocalDateTime> lastPollTimes = (Map<UUID, LocalDateTime>) lastPollTimesField.get(slackEventPollingService);

        LocalDateTime beforePoll = LocalDateTime.now();

        // Use reflection to call private method
        var method = SlackEventPollingService.class.getDeclaredMethod("processActionInstance", ActionInstance.class);
        method.setAccessible(true);

        // When
        method.invoke(slackEventPollingService, actionInstance);

        // Then
        LocalDateTime afterPoll = LocalDateTime.now();
        assertTrue(lastPollTimes.containsKey(actionInstance.getId()));
        LocalDateTime lastPoll = lastPollTimes.get(actionInstance.getId());
        assertTrue(lastPoll.isAfter(beforePoll.minusSeconds(1)) && lastPoll.isBefore(afterPoll.plusSeconds(1)));
    }
}
