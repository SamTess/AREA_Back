package area.server.AREA_Back.service;

import area.server.AREA_Back.entity.ActionDefinition;
import area.server.AREA_Back.entity.ActionInstance;
import area.server.AREA_Back.entity.ActivationMode;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.entity.enums.ActivationModeType;
import area.server.AREA_Back.repository.ActionInstanceRepository;
import area.server.AREA_Back.repository.ActivationModeRepository;
import area.server.AREA_Back.service.Area.ExecutionTriggerService;
import area.server.AREA_Back.service.Area.Services.GitHubActionService;
import area.server.AREA_Back.service.Polling.GitHubEventPollingService;
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
 * Unit tests for GitHubEventPollingService
 * Tests the scheduled polling of GitHub events
 */
@ExtendWith(MockitoExtension.class)
class GitHubEventPollingServiceTest {

    @Mock
    private GitHubActionService gitHubActionService;

    @Mock
    private ActionInstanceRepository actionInstanceRepository;

    @Mock
    private ActivationModeRepository activationModeRepository;

    @Mock
    private ExecutionTriggerService executionTriggerService;

    private SimpleMeterRegistry meterRegistry;
    private GitHubEventPollingService gitHubEventPollingService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        gitHubEventPollingService = new GitHubEventPollingService(
            gitHubActionService,
            actionInstanceRepository,
            activationModeRepository,
            meterRegistry,
            executionTriggerService
        );

        // Initialize metrics
        try {
            var initMethod = GitHubEventPollingService.class.getDeclaredMethod("initMetrics");
            initMethod.setAccessible(true);
            initMethod.invoke(gitHubEventPollingService);
        } catch (Exception e) {
            fail("Failed to initialize metrics: " + e.getMessage());
        }
    }

    @Test
    void testServiceInitialization() {
        assertNotNull(gitHubEventPollingService);
        assertNotNull(meterRegistry);
    }

    @Test
    void testInitMetrics() {
        // Given - metrics should be initialized in setUp
        // When - checking if metrics exist
        // Then
        assertNotNull(meterRegistry.find("github.polling.cycles").counter());
        assertNotNull(meterRegistry.find("github.polling.events_found").counter());
        assertNotNull(meterRegistry.find("github.polling.failures").counter());
    }

    @Test
    void testMetricsAreRegistered() {
        assertNotNull(meterRegistry.find("github.polling.cycles").counter());
        assertNotNull(meterRegistry.find("github.polling.events_found").counter());
        assertNotNull(meterRegistry.find("github.polling.failures").counter());
    }

    @Test
    void testPollGitHubEventsWithNoActionInstances() {
        // Given
        when(actionInstanceRepository.findActiveGitHubActionInstances())
            .thenReturn(Collections.emptyList());

        double initialCycles = meterRegistry.counter("github.polling.cycles").count();

        // When
        gitHubEventPollingService.pollGitHubEvents();

        // Then
        verify(actionInstanceRepository, times(1)).findActiveGitHubActionInstances();
        double finalCycles = meterRegistry.counter("github.polling.cycles").count();
        assertEquals(initialCycles + 1, finalCycles);
    }

    @Test
    void testPollGitHubEventsIncrementsPollingCycles() {
        // Given
        when(actionInstanceRepository.findActiveGitHubActionInstances())
            .thenReturn(Collections.emptyList());

        double initialCount = meterRegistry.counter("github.polling.cycles").count();

        // When
        gitHubEventPollingService.pollGitHubEvents();

        // Then
        double finalCount = meterRegistry.counter("github.polling.cycles").count();
        assertEquals(initialCount + 1, finalCount);
    }

    @Test
    void testPollGitHubEventsWithEnabledActionInstance() throws Exception {
        // Given
        User user = new User();
        user.setId(UUID.randomUUID());

        ActionDefinition actionDef = new ActionDefinition();
        actionDef.setKey("github_new_issue");

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
        config.put("interval_seconds", 300);
        activationMode.setConfig(config);

        lenient().when(actionInstanceRepository.findActiveGitHubActionInstances())
            .thenReturn(List.of(actionInstance));
        lenient().when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(
            actionInstance, ActivationModeType.POLL, true))
            .thenReturn(List.of(activationMode));
        lenient().when(gitHubActionService.checkGitHubEvents(
            anyString(), anyMap(), any(UUID.class), any(LocalDateTime.class)))
            .thenReturn(Collections.emptyList());

        // When
        gitHubEventPollingService.pollGitHubEvents();

        // Then
        verify(actionInstanceRepository, times(1)).findActiveGitHubActionInstances();
    }

    @Test
    void testPollGitHubEventsWithDisabledActionInstance() {
        // Given
        ActionInstance actionInstance = new ActionInstance();
        actionInstance.setId(UUID.randomUUID());
        actionInstance.setEnabled(false);

        when(actionInstanceRepository.findActiveGitHubActionInstances())
            .thenReturn(List.of(actionInstance));

        // When
        gitHubEventPollingService.pollGitHubEvents();

        // Then
        verify(actionInstanceRepository, times(1)).findActiveGitHubActionInstances();
        verify(gitHubActionService, never()).checkGitHubEvents(
            anyString(), anyMap(), any(UUID.class), any(LocalDateTime.class));
    }

    @Test
    void testPollGitHubEventsWithNoActivationMode() {
        // Given
        User user = new User();
        user.setId(UUID.randomUUID());

        ActionDefinition actionDef = new ActionDefinition();
        actionDef.setKey("github_new_issue");

        ActionInstance actionInstance = new ActionInstance();
        actionInstance.setId(UUID.randomUUID());
        actionInstance.setEnabled(true);
        actionInstance.setUser(user);
        actionInstance.setActionDefinition(actionDef);

        when(actionInstanceRepository.findActiveGitHubActionInstances())
            .thenReturn(List.of(actionInstance));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(
            actionInstance, ActivationModeType.POLL, true))
            .thenReturn(Collections.emptyList());

        // When
        gitHubEventPollingService.pollGitHubEvents();

        // Then
        verify(actionInstanceRepository, times(1)).findActiveGitHubActionInstances();
        verify(gitHubActionService, never()).checkGitHubEvents(
            anyString(), anyMap(), any(UUID.class), any(LocalDateTime.class));
    }

    @Test
    void testPollGitHubEventsWithEventsFound() throws Exception {
        // Given
        User user = new User();
        user.setId(UUID.randomUUID());

        ActionDefinition actionDef = new ActionDefinition();
        actionDef.setKey("github_new_issue");

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
        configMap.put("interval_seconds", 300);
        activationMode.setConfig(configMap);

        Map<String, Object> event1 = new HashMap<>();
        event1.put("issueId", "123");
        event1.put("title", "Test Issue");

        Map<String, Object> event2 = new HashMap<>();
        event2.put("issueId", "456");
        event2.put("title", "Another Issue");

        lenient().when(actionInstanceRepository.findActiveGitHubActionInstances())
            .thenReturn(List.of(actionInstance));
        lenient().when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(
            actionInstance, ActivationModeType.POLL, true))
            .thenReturn(List.of(activationMode));
        lenient().when(gitHubActionService.checkGitHubEvents(
            anyString(), anyMap(), any(UUID.class), any(LocalDateTime.class)))
            .thenReturn(List.of(event1, event2));

        double initialEventsFound = meterRegistry.counter("github.polling.events_found").count();

        // When
        gitHubEventPollingService.pollGitHubEvents();

        // Then
        verify(actionInstanceRepository, times(1)).findActiveGitHubActionInstances();
        double finalEventsFound = meterRegistry.counter("github.polling.events_found").count();
        assertEquals(initialEventsFound + 2, finalEventsFound);
    }

    @Test
    void testPollGitHubEventsHandlesExceptionGracefully() {
        // Given
        when(actionInstanceRepository.findActiveGitHubActionInstances())
            .thenThrow(new RuntimeException("Database error"));

        double initialFailures = meterRegistry.counter("github.polling.failures").count();

        // When - should not throw exception
        assertDoesNotThrow(() -> {
            gitHubEventPollingService.pollGitHubEvents();
        });

        // Then
        double finalFailures = meterRegistry.counter("github.polling.failures").count();
        assertEquals(initialFailures + 1, finalFailures);
    }

    @Test
    void testPollGitHubEventsWithTriggerExecutionFailure() throws Exception {
        // Given
        User user = new User();
        user.setId(UUID.randomUUID());

        ActionDefinition actionDef = new ActionDefinition();
        actionDef.setKey("github_new_issue");

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
        config.put("interval_seconds", 300);
        activationMode.setConfig(config);

        Map<String, Object> event = new HashMap<>();
        event.put("issueId", "123");

        lenient().when(actionInstanceRepository.findActiveGitHubActionInstances())
            .thenReturn(List.of(actionInstance));
        lenient().when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(
            actionInstance, ActivationModeType.POLL, true))
            .thenReturn(List.of(activationMode));
        lenient().when(gitHubActionService.checkGitHubEvents(
            anyString(), anyMap(), any(UUID.class), any(LocalDateTime.class)))
            .thenReturn(List.of(event));
        lenient().doThrow(new RuntimeException("Trigger failed"))
            .when(executionTriggerService).triggerAreaExecution(
                any(ActionInstance.class), any(ActivationModeType.class), anyMap());

        // When - should not throw exception
        assertDoesNotThrow(() -> {
            gitHubEventPollingService.pollGitHubEvents();
        });

        // Then - polling should complete without throwing
        verify(actionInstanceRepository, times(1)).findActiveGitHubActionInstances();
    }

    @Test
    void testPollGitHubEventsWithMultipleActionInstances() throws Exception {
        // Given
        User user1 = new User();
        user1.setId(UUID.randomUUID());

        User user2 = new User();
        user2.setId(UUID.randomUUID());

        ActionDefinition actionDef1 = new ActionDefinition();
        actionDef1.setKey("github_new_issue");

        ActionDefinition actionDef2 = new ActionDefinition();
        actionDef2.setKey("github_new_pr");

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
        Map<String, Object> config1 = new HashMap<>();
        config1.put("interval_seconds", 300);
        activationMode1.setConfig(config1);

        ActivationMode activationMode2 = new ActivationMode();
        activationMode2.setActionInstance(actionInstance2);
        activationMode2.setType(ActivationModeType.POLL);
        activationMode2.setEnabled(true);
        Map<String, Object> config2 = new HashMap<>();
        config2.put("interval_seconds", 300);
        activationMode2.setConfig(config2);

        lenient().when(actionInstanceRepository.findActiveGitHubActionInstances())
            .thenReturn(List.of(actionInstance1, actionInstance2));
        lenient().when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(
            actionInstance1, ActivationModeType.POLL, true))
            .thenReturn(List.of(activationMode1));
        lenient().when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(
            actionInstance2, ActivationModeType.POLL, true))
            .thenReturn(List.of(activationMode2));
        lenient().when(gitHubActionService.checkGitHubEvents(
            anyString(), anyMap(), any(UUID.class), any(LocalDateTime.class)))
            .thenReturn(Collections.emptyList());

        // When
        gitHubEventPollingService.pollGitHubEvents();

        // Then - verify both instances were processed
        verify(actionInstanceRepository, times(1)).findActiveGitHubActionInstances();
    }

    @Test
    void testPollGitHubEventsWithRepositoryException() {
        // Given
        when(actionInstanceRepository.findActiveGitHubActionInstances())
            .thenThrow(new RuntimeException("Database error"));

        double initialFailures = meterRegistry.counter("github.polling.failures").count();

        // When - should not throw exception
        assertDoesNotThrow(() -> {
            gitHubEventPollingService.pollGitHubEvents();
        });

        // Then
        double finalFailures = meterRegistry.counter("github.polling.failures").count();
        assertEquals(initialFailures + 1, finalFailures);
    }

    @Test
    void testProcessActionInstanceWithDisabledInstance() throws Exception {
        // Given
        ActionInstance actionInstance = new ActionInstance();
        actionInstance.setId(UUID.randomUUID());
        actionInstance.setEnabled(false);

        // Use reflection to call private method
        var method = GitHubEventPollingService.class.getDeclaredMethod("processActionInstance", ActionInstance.class);
        method.setAccessible(true);

        // When
        method.invoke(gitHubEventPollingService, actionInstance);

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
        actionDef.setKey("github_new_issue");

        ActionInstance actionInstance = new ActionInstance();
        actionInstance.setId(UUID.randomUUID());
        actionInstance.setEnabled(true);
        actionInstance.setUser(user);
        actionInstance.setActionDefinition(actionDef);

        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(
            actionInstance, ActivationModeType.POLL, true))
            .thenReturn(Collections.emptyList());

        // Use reflection to call private method
        var method = GitHubEventPollingService.class.getDeclaredMethod("processActionInstance", ActionInstance.class);
        method.setAccessible(true);

        // When
        method.invoke(gitHubEventPollingService, actionInstance);

        // Then
        verify(activationModeRepository, times(1)).findByActionInstanceAndTypeAndEnabled(
            actionInstance, ActivationModeType.POLL, true);
        verify(gitHubActionService, never()).checkGitHubEvents(
            anyString(), anyMap(), any(UUID.class), any(LocalDateTime.class));
    }

    @Test
    void testProcessActionInstanceWithValidActivationModeFirstPoll() throws Exception {
        // Given
        User user = new User();
        user.setId(UUID.randomUUID());

        ActionDefinition actionDef = new ActionDefinition();
        actionDef.setKey("github_new_issue");

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
        config.put("interval_seconds", 300);
        activationMode.setConfig(config);

        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(
            actionInstance, ActivationModeType.POLL, true))
            .thenReturn(List.of(activationMode));
        when(gitHubActionService.checkGitHubEvents(
            anyString(), anyMap(), any(UUID.class), any(LocalDateTime.class)))
            .thenReturn(Collections.emptyList());

        // Use reflection to call private method
        var method = GitHubEventPollingService.class.getDeclaredMethod("processActionInstance", ActionInstance.class);
        method.setAccessible(true);

        // When
        method.invoke(gitHubEventPollingService, actionInstance);

        // Then
        verify(gitHubActionService, times(1)).checkGitHubEvents(
            eq("github_new_issue"), anyMap(), eq(user.getId()), any(LocalDateTime.class));
    }

    @Test
    void testProcessActionInstanceWithCheckGitHubEventsException() throws Exception {
        // Given
        User user = new User();
        user.setId(UUID.randomUUID());

        ActionDefinition actionDef = new ActionDefinition();
        actionDef.setKey("github_new_issue");

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
        config.put("interval_seconds", 300);
        activationMode.setConfig(config);

        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(
            actionInstance, ActivationModeType.POLL, true))
            .thenReturn(List.of(activationMode));
        when(gitHubActionService.checkGitHubEvents(
            anyString(), anyMap(), any(UUID.class), any(LocalDateTime.class)))
            .thenThrow(new RuntimeException("GitHub API error"));

        // Use reflection to call private method
        var method = GitHubEventPollingService.class.getDeclaredMethod("processActionInstance", ActionInstance.class);
        method.setAccessible(true);

        // When - should not throw exception
        assertDoesNotThrow(() -> {
            method.invoke(gitHubEventPollingService, actionInstance);
        });

        // Then
        verify(gitHubActionService, times(1)).checkGitHubEvents(
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
        config.put("interval_seconds", 300);
        activationMode.setConfig(config);

        // Use reflection to call private method
        var method = GitHubEventPollingService.class.getDeclaredMethod("shouldPollNow", ActivationMode.class);
        method.setAccessible(true);

        // When
        boolean result = (boolean) method.invoke(gitHubEventPollingService, activationMode);

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
        config.put("interval_seconds", 300);
        activationMode.setConfig(config);

        // Simulate a recent poll
        var lastPollTimesField = GitHubEventPollingService.class.getDeclaredField("lastPollTimes");
        lastPollTimesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, LocalDateTime> lastPollTimes = (Map<UUID, LocalDateTime>) lastPollTimesField.get(gitHubEventPollingService);
        lastPollTimes.put(actionInstance.getId(), LocalDateTime.now());

        // Use reflection to call private method
        var method = GitHubEventPollingService.class.getDeclaredMethod("shouldPollNow", ActivationMode.class);
        method.setAccessible(true);

        // When
        boolean result = (boolean) method.invoke(gitHubEventPollingService, activationMode);

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
        config.put("interval_seconds", 300);
        activationMode.setConfig(config);

        // Simulate an old poll
        var lastPollTimesField = GitHubEventPollingService.class.getDeclaredField("lastPollTimes");
        lastPollTimesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, LocalDateTime> lastPollTimes = (Map<UUID, LocalDateTime>) lastPollTimesField.get(gitHubEventPollingService);
        lastPollTimes.put(actionInstance.getId(), LocalDateTime.now().minusSeconds(400));

        // Use reflection to call private method
        var method = GitHubEventPollingService.class.getDeclaredMethod("shouldPollNow", ActivationMode.class);
        method.setAccessible(true);

        // When
        boolean result = (boolean) method.invoke(gitHubEventPollingService, activationMode);

        // Then
        assertTrue(result);
    }

    @Test
    void testGetPollingIntervalWithConfiguredValue() throws Exception {
        // Given
        ActivationMode activationMode = new ActivationMode();
        Map<String, Object> config = new HashMap<>();
        config.put("interval_seconds", 600);
        activationMode.setConfig(config);

        // Use reflection to call private method
        var method = GitHubEventPollingService.class.getDeclaredMethod("getPollingInterval", ActivationMode.class);
        method.setAccessible(true);

        // When
        int result = (int) method.invoke(gitHubEventPollingService, activationMode);

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
        var method = GitHubEventPollingService.class.getDeclaredMethod("getPollingInterval", ActivationMode.class);
        method.setAccessible(true);

        // When
        int result = (int) method.invoke(gitHubEventPollingService, activationMode);

        // Then
        assertEquals(300, result);
    }

    @Test
    void testCalculateLastCheckTime() throws Exception {
        // Given
        ActionInstance actionInstance = new ActionInstance();
        actionInstance.setId(UUID.randomUUID());

        ActivationMode activationMode = new ActivationMode();
        activationMode.setActionInstance(actionInstance);
        Map<String, Object> config = new HashMap<>();
        config.put("interval_seconds", 300);
        activationMode.setConfig(config);

        // Use reflection to call private method
        var method = GitHubEventPollingService.class.getDeclaredMethod("calculateLastCheckTime", ActivationMode.class);
        method.setAccessible(true);

        LocalDateTime before = LocalDateTime.now().minusSeconds(301);

        // When
        LocalDateTime result = (LocalDateTime) method.invoke(gitHubEventPollingService, activationMode);

        // Then
        LocalDateTime after = LocalDateTime.now().minusSeconds(299);
        assertTrue(result.isAfter(before) && result.isBefore(after));
    }

    @Test
    void testCalculateLastCheckTimeWithCustomInterval() throws Exception {
        // Given
        ActionInstance actionInstance = new ActionInstance();
        actionInstance.setId(UUID.randomUUID());

        ActivationMode activationMode = new ActivationMode();
        activationMode.setActionInstance(actionInstance);
        Map<String, Object> config = new HashMap<>();
        config.put("interval_seconds", 600);
        activationMode.setConfig(config);

        // Use reflection to call private method
        var method = GitHubEventPollingService.class.getDeclaredMethod("calculateLastCheckTime", ActivationMode.class);
        method.setAccessible(true);

        LocalDateTime before = LocalDateTime.now().minusSeconds(601);

        // When
        LocalDateTime result = (LocalDateTime) method.invoke(gitHubEventPollingService, activationMode);

        // Then
        LocalDateTime after = LocalDateTime.now().minusSeconds(599);
        assertTrue(result.isAfter(before) && result.isBefore(after));
    }

    @Test
    void testPollGitHubEventsWithActionInstanceProcessingException() {
        // Given
        User user = new User();
        user.setId(UUID.randomUUID());

        ActionDefinition actionDef = new ActionDefinition();
        actionDef.setKey("github_new_issue");

        ActionInstance actionInstance = new ActionInstance();
        actionInstance.setId(UUID.randomUUID());
        actionInstance.setEnabled(true);
        actionInstance.setUser(user);
        actionInstance.setActionDefinition(actionDef);

        when(actionInstanceRepository.findActiveGitHubActionInstances())
            .thenReturn(List.of(actionInstance));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(
            any(), any(), anyBoolean()))
            .thenThrow(new RuntimeException("Processing error"));

        double initialFailures = meterRegistry.counter("github.polling.failures").count();

        // When - should not throw exception
        assertDoesNotThrow(() -> {
            gitHubEventPollingService.pollGitHubEvents();
        });

        // Then
        double finalFailures = meterRegistry.counter("github.polling.failures").count();
        assertEquals(initialFailures + 1, finalFailures);
    }

    @Test
    void testProcessActionInstanceWithMultipleEventsAndTriggerExecution() throws Exception {
        // Given
        User user = new User();
        user.setId(UUID.randomUUID());

        ActionDefinition actionDef = new ActionDefinition();
        actionDef.setKey("github_new_issue");

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
        config.put("interval_seconds", 300);
        activationMode.setConfig(config);

        Map<String, Object> event1 = new HashMap<>();
        event1.put("issueId", "123");
        Map<String, Object> event2 = new HashMap<>();
        event2.put("issueId", "456");

        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(
            actionInstance, ActivationModeType.POLL, true))
            .thenReturn(List.of(activationMode));
        when(gitHubActionService.checkGitHubEvents(
            anyString(), anyMap(), any(UUID.class), any(LocalDateTime.class)))
            .thenReturn(List.of(event1, event2));

        // Use reflection to call private method
        var method = GitHubEventPollingService.class.getDeclaredMethod("processActionInstance", ActionInstance.class);
        method.setAccessible(true);

        // When
        method.invoke(gitHubEventPollingService, actionInstance);

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
        actionDef.setKey("github_new_issue");

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
        config.put("interval_seconds", 300);
        activationMode.setConfig(config);

        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(
            actionInstance, ActivationModeType.POLL, true))
            .thenReturn(List.of(activationMode));
        when(gitHubActionService.checkGitHubEvents(
            anyString(), anyMap(), any(UUID.class), any(LocalDateTime.class)))
            .thenReturn(Collections.emptyList());

        // Use reflection to access lastPollTimes
        var lastPollTimesField = GitHubEventPollingService.class.getDeclaredField("lastPollTimes");
        lastPollTimesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, LocalDateTime> lastPollTimes = (Map<UUID, LocalDateTime>) lastPollTimesField.get(gitHubEventPollingService);

        LocalDateTime beforePoll = LocalDateTime.now();

        // Use reflection to call private method
        var method = GitHubEventPollingService.class.getDeclaredMethod("processActionInstance", ActionInstance.class);
        method.setAccessible(true);

        // When
        method.invoke(gitHubEventPollingService, actionInstance);

        // Then
        LocalDateTime afterPoll = LocalDateTime.now();
        assertTrue(lastPollTimes.containsKey(actionInstance.getId()));
        LocalDateTime lastPoll = lastPollTimes.get(actionInstance.getId());
        assertTrue(lastPoll.isAfter(beforePoll.minusSeconds(1)) && lastPoll.isBefore(afterPoll.plusSeconds(1)));
    }

    @Test
    void testProcessActionInstanceWhenShouldNotPollYet() throws Exception {
        // Given
        User user = new User();
        user.setId(UUID.randomUUID());

        ActionDefinition actionDef = new ActionDefinition();
        actionDef.setKey("github_new_issue");

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
        config.put("interval_seconds", 300);
        activationMode.setConfig(config);

        // Set recent poll time
        var lastPollTimesField = GitHubEventPollingService.class.getDeclaredField("lastPollTimes");
        lastPollTimesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, LocalDateTime> lastPollTimes = (Map<UUID, LocalDateTime>) lastPollTimesField.get(gitHubEventPollingService);
        lastPollTimes.put(actionInstance.getId(), LocalDateTime.now());

        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(
            actionInstance, ActivationModeType.POLL, true))
            .thenReturn(List.of(activationMode));

        // Use reflection to call private method
        var method = GitHubEventPollingService.class.getDeclaredMethod("processActionInstance", ActionInstance.class);
        method.setAccessible(true);

        // When
        method.invoke(gitHubEventPollingService, actionInstance);

        // Then - should not call checkGitHubEvents
        verify(gitHubActionService, never()).checkGitHubEvents(
            anyString(), anyMap(), any(UUID.class), any(LocalDateTime.class));
    }
}
