package area.server.AREA_Back.service;

import area.server.AREA_Back.entity.ActionDefinition;
import area.server.AREA_Back.entity.ActionInstance;
import area.server.AREA_Back.entity.ActivationMode;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.entity.enums.ActivationModeType;
import area.server.AREA_Back.repository.ActionInstanceRepository;
import area.server.AREA_Back.repository.ActivationModeRepository;
import area.server.AREA_Back.service.Area.ExecutionTriggerService;
import area.server.AREA_Back.service.Area.Services.SpotifyActionService;
import area.server.AREA_Back.service.Polling.SpotifyEventPollingService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpotifyEventPollingServiceTest {

    @Mock
    private SpotifyActionService spotifyActionService;

    @Mock
    private ActionInstanceRepository actionInstanceRepository;

    @Mock
    private ActivationModeRepository activationModeRepository;

    @Mock
    private ExecutionTriggerService executionTriggerService;

    private SimpleMeterRegistry meterRegistry;
    private SpotifyEventPollingService spotifyEventPollingService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        spotifyEventPollingService = new SpotifyEventPollingService(
            spotifyActionService,
            actionInstanceRepository,
            activationModeRepository,
            meterRegistry,
            executionTriggerService
        );
    }

    @Test
    void testInitMetrics() {
        // When
        spotifyEventPollingService.initMetrics();

        // Then
        Counter pollingCycles = meterRegistry.find("spotify_polling_cycles").counter();
        Counter eventsFound = meterRegistry.find("spotify_events_found").counter();
        Counter pollingFailures = meterRegistry.find("spotify_polling_failures").counter();

        assertNotNull(pollingCycles, "Polling cycles counter should be initialized");
        assertNotNull(eventsFound, "Events found counter should be initialized");
        assertNotNull(pollingFailures, "Polling failures counter should be initialized");
    }

    @Test
    void testPollSpotifyEventsWithNoActionInstances() {
        // Given
        spotifyEventPollingService.initMetrics();
        when(actionInstanceRepository.findActiveSpotifyActionInstances()).thenReturn(Collections.emptyList());

        // When
        spotifyEventPollingService.pollSpotifyEvents();

        // Then
        Counter pollingCycles = meterRegistry.find("spotify_polling_cycles").counter();
        assertEquals(1.0, pollingCycles.count(), "Polling cycles should be incremented");
        verify(actionInstanceRepository, times(1)).findActiveSpotifyActionInstances();
    }

    @Test
    void testPollSpotifyEventsIncrementsPollingCycles() {
        // Given
        spotifyEventPollingService.initMetrics();
        when(actionInstanceRepository.findActiveSpotifyActionInstances()).thenReturn(Collections.emptyList());

        Counter pollingCycles = meterRegistry.find("spotify_polling_cycles").counter();
        double beforeCount = pollingCycles.count();

        // When
        spotifyEventPollingService.pollSpotifyEvents();

        // Then
        double afterCount = pollingCycles.count();
        assertEquals(beforeCount + 1.0, afterCount, "Polling cycles should be incremented by 1");
    }

    @Test
    void testPollSpotifyEventsWithException() {
        // Given
        spotifyEventPollingService.initMetrics();
        when(actionInstanceRepository.findActiveSpotifyActionInstances())
            .thenThrow(new RuntimeException("Database error"));

        Counter pollingFailures = meterRegistry.find("spotify_polling_failures").counter();
        double beforeFailures = pollingFailures.count();

        // When
        spotifyEventPollingService.pollSpotifyEvents();

        // Then
        double afterFailures = pollingFailures.count();
        assertEquals(beforeFailures + 1.0, afterFailures, "Polling failures should be incremented");
    }

    @Test
    void testProcessActionInstanceWithDisabledAction() throws Exception {
        // Given
        spotifyEventPollingService.initMetrics();
        ActionInstance actionInstance = createActionInstance(false);

        Method processMethod = SpotifyEventPollingService.class.getDeclaredMethod(
            "processActionInstance", ActionInstance.class);
        processMethod.setAccessible(true);

        // When
        processMethod.invoke(spotifyEventPollingService, actionInstance);

        // Then
        verify(activationModeRepository, never()).findByActionInstanceAndTypeAndEnabled(any(), any(), anyBoolean());
    }

    @Test
    void testProcessActionInstanceWithNoActivationModes() throws Exception {
        // Given
        spotifyEventPollingService.initMetrics();
        ActionInstance actionInstance = createActionInstance(true);

        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(
            actionInstance, ActivationModeType.POLL, true))
            .thenReturn(Collections.emptyList());

        Method processMethod = SpotifyEventPollingService.class.getDeclaredMethod(
            "processActionInstance", ActionInstance.class);
        processMethod.setAccessible(true);

        // When
        processMethod.invoke(spotifyEventPollingService, actionInstance);

        // Then
        verify(spotifyActionService, never()).checkSpotifyEvents(any(), any(), any(), any());
    }

    @Test
    void testProcessActionInstanceWithEvents() throws Exception {
        // Given
        spotifyEventPollingService.initMetrics();
        ActionInstance actionInstance = createActionInstance(true);
        ActivationMode activationMode = createActivationMode(actionInstance, 60);

        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(
            actionInstance, ActivationModeType.POLL, true))
            .thenReturn(List.of(activationMode));

        List<Map<String, Object>> events = Arrays.asList(
            Map.of("event", "new_track", "trackId", "123"),
            Map.of("event", "new_track", "trackId", "456")
        );

        when(spotifyActionService.checkSpotifyEvents(
            eq("spotify_action"),
            any(),
            any(),
            any(LocalDateTime.class)))
            .thenReturn(events);

        Counter eventsFound = meterRegistry.find("spotify_events_found").counter();
        double beforeEventsCount = eventsFound.count();

        Method processMethod = SpotifyEventPollingService.class.getDeclaredMethod(
            "processActionInstance", ActionInstance.class);
        processMethod.setAccessible(true);

        // When
        processMethod.invoke(spotifyEventPollingService, actionInstance);

        // Then
        double afterEventsCount = eventsFound.count();
        assertEquals(beforeEventsCount + 2.0, afterEventsCount, "Events found should be incremented by 2");
        verify(executionTriggerService, times(2)).triggerAreaExecution(
            eq(actionInstance),
            eq(ActivationModeType.POLL),
            anyMap()
        );
    }

    @Test
    void testProcessActionInstanceWithNoEvents() throws Exception {
        // Given
        spotifyEventPollingService.initMetrics();
        ActionInstance actionInstance = createActionInstance(true);
        ActivationMode activationMode = createActivationMode(actionInstance, 60);

        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(
            actionInstance, ActivationModeType.POLL, true))
            .thenReturn(List.of(activationMode));

        when(spotifyActionService.checkSpotifyEvents(
            eq("spotify_action"),
            any(),
            any(),
            any(LocalDateTime.class)))
            .thenReturn(Collections.emptyList());

        Method processMethod = SpotifyEventPollingService.class.getDeclaredMethod(
            "processActionInstance", ActionInstance.class);
        processMethod.setAccessible(true);

        // When
        processMethod.invoke(spotifyEventPollingService, actionInstance);

        // Then
        verify(executionTriggerService, never()).triggerAreaExecution(any(), any(), any());
    }

    @Test
    void testProcessActionInstanceWithSpotifyServiceException() throws Exception {
        // Given
        spotifyEventPollingService.initMetrics();
        ActionInstance actionInstance = createActionInstance(true);
        ActivationMode activationMode = createActivationMode(actionInstance, 60);

        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(
            actionInstance, ActivationModeType.POLL, true))
            .thenReturn(List.of(activationMode));

        when(spotifyActionService.checkSpotifyEvents(
            any(), any(), any(), any(LocalDateTime.class)))
            .thenThrow(new RuntimeException("Spotify API error"));

        Method processMethod = SpotifyEventPollingService.class.getDeclaredMethod(
            "processActionInstance", ActionInstance.class);
        processMethod.setAccessible(true);

        // When/Then - should not throw exception
        assertDoesNotThrow(() -> processMethod.invoke(spotifyEventPollingService, actionInstance));
        verify(executionTriggerService, never()).triggerAreaExecution(any(), any(), any());
    }

    @Test
    void testShouldPollNowWithNoPreviousPoll() throws Exception {
        // Given
        ActionInstance actionInstance = createActionInstance(true);
        ActivationMode activationMode = createActivationMode(actionInstance, 60);

        Method shouldPollMethod = SpotifyEventPollingService.class.getDeclaredMethod(
            "shouldPollNow", ActivationMode.class);
        shouldPollMethod.setAccessible(true);

        // When
        boolean result = (boolean) shouldPollMethod.invoke(spotifyEventPollingService, activationMode);

        // Then
        assertTrue(result, "Should poll when there's no previous poll time");
    }

    @Test
    void testShouldPollNowWithRecentPoll() throws Exception {
        // Given
        ActionInstance actionInstance = createActionInstance(true);
        ActivationMode activationMode = createActivationMode(actionInstance, 60);

        // Set last poll time to now
        Field lastPollTimesField = SpotifyEventPollingService.class.getDeclaredField("lastPollTimes");
        lastPollTimesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, LocalDateTime> lastPollTimes = (Map<UUID, LocalDateTime>) lastPollTimesField.get(spotifyEventPollingService);
        lastPollTimes.put(actionInstance.getId(), LocalDateTime.now());

        Method shouldPollMethod = SpotifyEventPollingService.class.getDeclaredMethod(
            "shouldPollNow", ActivationMode.class);
        shouldPollMethod.setAccessible(true);

        // When
        boolean result = (boolean) shouldPollMethod.invoke(spotifyEventPollingService, activationMode);

        // Then
        assertFalse(result, "Should not poll when interval hasn't elapsed");
    }

    @Test
    void testShouldPollNowWithElapsedInterval() throws Exception {
        // Given
        ActionInstance actionInstance = createActionInstance(true);
        ActivationMode activationMode = createActivationMode(actionInstance, 60);

        // Set last poll time to 2 minutes ago
        Field lastPollTimesField = SpotifyEventPollingService.class.getDeclaredField("lastPollTimes");
        lastPollTimesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, LocalDateTime> lastPollTimes = (Map<UUID, LocalDateTime>) lastPollTimesField.get(spotifyEventPollingService);
        lastPollTimes.put(actionInstance.getId(), LocalDateTime.now().minusMinutes(2));

        Method shouldPollMethod = SpotifyEventPollingService.class.getDeclaredMethod(
            "shouldPollNow", ActivationMode.class);
        shouldPollMethod.setAccessible(true);

        // When
        boolean result = (boolean) shouldPollMethod.invoke(spotifyEventPollingService, activationMode);

        // Then
        assertTrue(result, "Should poll when interval has elapsed");
    }

    @Test
    void testGetPollingIntervalWithDefaultValue() throws Exception {
        // Given
        ActionInstance actionInstance = createActionInstance(true);
        ActivationMode activationMode = new ActivationMode();
        activationMode.setActionInstance(actionInstance);
        activationMode.setConfig(new HashMap<>());

        Method getIntervalMethod = SpotifyEventPollingService.class.getDeclaredMethod(
            "getPollingInterval", ActivationMode.class);
        getIntervalMethod.setAccessible(true);

        // When
        int interval = (int) getIntervalMethod.invoke(spotifyEventPollingService, activationMode);

        // Then
        assertEquals(60, interval, "Should return default interval of 60 seconds");
    }

    @Test
    void testGetPollingIntervalWithCustomValue() throws Exception {
        // Given
        ActionInstance actionInstance = createActionInstance(true);
        ActivationMode activationMode = createActivationMode(actionInstance, 120);

        Method getIntervalMethod = SpotifyEventPollingService.class.getDeclaredMethod(
            "getPollingInterval", ActivationMode.class);
        getIntervalMethod.setAccessible(true);

        // When
        int interval = (int) getIntervalMethod.invoke(spotifyEventPollingService, activationMode);

        // Then
        assertEquals(120, interval, "Should return custom interval of 120 seconds");
    }

    @Test
    void testCalculateLastCheckTimeWithNoPreviousPoll() throws Exception {
        // Given
        ActionInstance actionInstance = createActionInstance(true);
        ActivationMode activationMode = createActivationMode(actionInstance, 60);

        Method calculateMethod = SpotifyEventPollingService.class.getDeclaredMethod(
            "calculateLastCheckTime", ActivationMode.class);
        calculateMethod.setAccessible(true);

        LocalDateTime beforeCall = LocalDateTime.now().minusSeconds(60);

        // When
        LocalDateTime result = (LocalDateTime) calculateMethod.invoke(spotifyEventPollingService, activationMode);

        // Then
        assertNotNull(result, "Result should not be null");
        assertTrue(result.isAfter(beforeCall.minusSeconds(1)) && result.isBefore(beforeCall.plusSeconds(2)),
            "Should return approximately polling interval seconds ago");
    }

    @Test
    void testCalculateLastCheckTimeWithPreviousPoll() throws Exception {
        // Given
        ActionInstance actionInstance = createActionInstance(true);
        ActivationMode activationMode = createActivationMode(actionInstance, 60);

        LocalDateTime previousPollTime = LocalDateTime.now().minusMinutes(1);
        Field lastPollTimesField = SpotifyEventPollingService.class.getDeclaredField("lastPollTimes");
        lastPollTimesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, LocalDateTime> lastPollTimes = (Map<UUID, LocalDateTime>) lastPollTimesField.get(spotifyEventPollingService);
        lastPollTimes.put(actionInstance.getId(), previousPollTime);

        Method calculateMethod = SpotifyEventPollingService.class.getDeclaredMethod(
            "calculateLastCheckTime", ActivationMode.class);
        calculateMethod.setAccessible(true);

        // When
        LocalDateTime result = (LocalDateTime) calculateMethod.invoke(spotifyEventPollingService, activationMode);

        // Then
        assertEquals(previousPollTime, result, "Should return the previous poll time");
    }

    @Test
    void testPollSpotifyEventsWithMultipleActionInstances() {
        // Given
        spotifyEventPollingService.initMetrics();
        
        ActionInstance actionInstance1 = createActionInstance(true);
        ActionInstance actionInstance2 = createActionInstance(true);
        
        List<ActionInstance> actionInstances = Arrays.asList(actionInstance1, actionInstance2);
        when(actionInstanceRepository.findActiveSpotifyActionInstances()).thenReturn(actionInstances);
        
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(
            any(), eq(ActivationModeType.POLL), eq(true)))
            .thenReturn(Collections.emptyList());

        // When
        spotifyEventPollingService.pollSpotifyEvents();

        // Then
        verify(activationModeRepository, times(2)).findByActionInstanceAndTypeAndEnabled(
            any(), eq(ActivationModeType.POLL), eq(true));
    }

    @Test
    void testProcessActionInstanceUpdatesLastPollTime() throws Exception {
        // Given
        spotifyEventPollingService.initMetrics();
        ActionInstance actionInstance = createActionInstance(true);
        ActivationMode activationMode = createActivationMode(actionInstance, 60);

        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(
            actionInstance, ActivationModeType.POLL, true))
            .thenReturn(List.of(activationMode));

        when(spotifyActionService.checkSpotifyEvents(
            any(), any(), any(), any(LocalDateTime.class)))
            .thenReturn(Collections.emptyList());

        Field lastPollTimesField = SpotifyEventPollingService.class.getDeclaredField("lastPollTimes");
        lastPollTimesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, LocalDateTime> lastPollTimes = (Map<UUID, LocalDateTime>) lastPollTimesField.get(spotifyEventPollingService);

        Method processMethod = SpotifyEventPollingService.class.getDeclaredMethod(
            "processActionInstance", ActionInstance.class);
        processMethod.setAccessible(true);

        LocalDateTime beforeProcess = LocalDateTime.now();

        // When
        processMethod.invoke(spotifyEventPollingService, actionInstance);

        // Then
        assertTrue(lastPollTimes.containsKey(actionInstance.getId()),
            "Last poll time should be recorded");
        LocalDateTime recordedTime = lastPollTimes.get(actionInstance.getId());
        assertTrue(recordedTime.isAfter(beforeProcess.minusSeconds(1)),
            "Recorded time should be approximately now");
    }

    @Test
    void testProcessActionInstanceWithExecutionTriggerException() throws Exception {
        // Given
        spotifyEventPollingService.initMetrics();
        ActionInstance actionInstance = createActionInstance(true);
        ActivationMode activationMode = createActivationMode(actionInstance, 60);

        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(
            actionInstance, ActivationModeType.POLL, true))
            .thenReturn(List.of(activationMode));

        List<Map<String, Object>> events = List.of(
            Map.of("event", "new_track", "trackId", "123")
        );

        when(spotifyActionService.checkSpotifyEvents(
            any(), any(), any(), any(LocalDateTime.class)))
            .thenReturn(events);

        doThrow(new RuntimeException("Execution trigger failed"))
            .when(executionTriggerService)
            .triggerAreaExecution(any(), any(), any());

        Method processMethod = SpotifyEventPollingService.class.getDeclaredMethod(
            "processActionInstance", ActionInstance.class);
        processMethod.setAccessible(true);

        // When/Then - should not throw exception
        assertDoesNotThrow(() -> processMethod.invoke(spotifyEventPollingService, actionInstance));
    }

    // Helper methods

    private ActionInstance createActionInstance(boolean enabled) {
        ActionInstance actionInstance = new ActionInstance();
        actionInstance.setId(UUID.randomUUID());
        actionInstance.setEnabled(enabled);

        ActionDefinition actionDefinition = new ActionDefinition();
        actionDefinition.setKey("spotify_action");
        actionInstance.setActionDefinition(actionDefinition);

        User user = new User();
        user.setId(UUID.randomUUID());
        actionInstance.setUser(user);

        actionInstance.setParams(new HashMap<>());

        return actionInstance;
    }

    private ActivationMode createActivationMode(ActionInstance actionInstance, int pollingInterval) {
        ActivationMode activationMode = new ActivationMode();
        activationMode.setActionInstance(actionInstance);
        activationMode.setType(ActivationModeType.POLL);
        activationMode.setEnabled(true);

        Map<String, Object> config = new HashMap<>();
        config.put("poll_interval", pollingInterval);
        activationMode.setConfig(config);

        return activationMode;
    }
}
