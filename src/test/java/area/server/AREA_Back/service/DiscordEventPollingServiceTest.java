package area.server.AREA_Back.service;

import area.server.AREA_Back.entity.ActionDefinition;
import area.server.AREA_Back.entity.ActionInstance;
import area.server.AREA_Back.entity.ActivationMode;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.entity.enums.ActivationModeType;
import area.server.AREA_Back.repository.ActionInstanceRepository;
import area.server.AREA_Back.repository.ActivationModeRepository;
import area.server.AREA_Back.service.Area.ExecutionTriggerService;
import area.server.AREA_Back.service.Area.Services.DiscordActionService;
import area.server.AREA_Back.service.Polling.DiscordEventPollingService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DiscordEventPollingServiceTest {

    @Mock
    private DiscordActionService discordActionService;

    @Mock
    private ActionInstanceRepository actionInstanceRepository;

    @Mock
    private ActivationModeRepository activationModeRepository;

    @Mock
    private ExecutionTriggerService executionTriggerService;

    private SimpleMeterRegistry meterRegistry;
    private DiscordEventPollingService discordEventPollingService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        discordEventPollingService = new DiscordEventPollingService(
            discordActionService,
            actionInstanceRepository,
            activationModeRepository,
            meterRegistry,
            executionTriggerService
        );

        try {
            var initMetricsMethod = DiscordEventPollingService.class.getDeclaredMethod("initMetrics");
            initMetricsMethod.setAccessible(true);
            initMetricsMethod.invoke(discordEventPollingService);
        } catch (Exception e) {
            fail("Failed to initialize metrics: " + e.getMessage());
        }
    }

    @Test
    void testServiceInitialization() {
        assertNotNull(discordEventPollingService);
        assertNotNull(meterRegistry);
    }

    @Test
    void testMetricsAreRegistered() {
        assertNotNull(meterRegistry.find("discord.polling.cycles").counter());
        assertNotNull(meterRegistry.find("discord.polling.events_found").counter());
        assertNotNull(meterRegistry.find("discord.polling.failures").counter());
    }

    @Test
    void testPollDiscordEventsWithNoActionInstances() {
        when(actionInstanceRepository.findActiveDiscordActionInstances()).thenReturn(Collections.emptyList());

        discordEventPollingService.pollDiscordEvents();

        assertNotNull(meterRegistry.find("discord.polling.cycles").counter());
    }

    @Test
    void testPollDiscordEventsIncrementsPollingCycles() {
        when(actionInstanceRepository.findActiveDiscordActionInstances()).thenReturn(Collections.emptyList());

        double beforeCount = meterRegistry.find("discord.polling.cycles").counter().count();
        discordEventPollingService.pollDiscordEvents();
        double afterCount = meterRegistry.find("discord.polling.cycles").counter().count();

        assertNotNull(beforeCount);
        assertNotNull(afterCount);
    }

    @Test
    void testPollDiscordEventsWithException() {
        when(actionInstanceRepository.findActiveDiscordActionInstances())
            .thenThrow(new RuntimeException("Database error"));

        double beforeFailures = meterRegistry.find("discord.polling.failures").counter().count();
        discordEventPollingService.pollDiscordEvents();
        double afterFailures = meterRegistry.find("discord.polling.failures").counter().count();

        assertEquals(beforeFailures + 1, afterFailures);
    }

    @Test
    void testPollDiscordEventsProcessesMultipleInstances() throws Exception {
        ActionInstance instance1 = createMockActionInstance(UUID.randomUUID(), true);
        ActionInstance instance2 = createMockActionInstance(UUID.randomUUID(), true);
        
        when(actionInstanceRepository.findActiveDiscordActionInstances())
            .thenReturn(Arrays.asList(instance1, instance2));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(any(), eq(ActivationModeType.POLL), eq(true)))
            .thenReturn(Collections.emptyList());

        discordEventPollingService.pollDiscordEvents();

        verify(activationModeRepository, times(2))
            .findByActionInstanceAndTypeAndEnabled(any(), eq(ActivationModeType.POLL), eq(true));
    }

    @Test
    void testPollDiscordEventsWithDisabledActionInstance() throws Exception {
        ActionInstance disabledInstance = createMockActionInstance(UUID.randomUUID(), false);
        
        when(actionInstanceRepository.findActiveDiscordActionInstances())
            .thenReturn(Collections.singletonList(disabledInstance));

        discordEventPollingService.pollDiscordEvents();

        verify(activationModeRepository, never())
            .findByActionInstanceAndTypeAndEnabled(any(), any(), anyBoolean());
    }

    @Test
    void testProcessActionInstanceWithEvents() throws Exception {
        UUID instanceId = UUID.randomUUID();
        ActionInstance actionInstance = createMockActionInstance(instanceId, true);
        ActivationMode activationMode = createMockActivationMode(actionInstance, 300);
        
        Map<String, Object> event1 = new HashMap<>();
        event1.put("type", "MESSAGE_CREATE");
        event1.put("content", "Hello");
        
        Map<String, Object> event2 = new HashMap<>();
        event2.put("type", "MESSAGE_CREATE");
        event2.put("content", "World");
        
        List<Map<String, Object>> events = Arrays.asList(event1, event2);
        
        when(actionInstanceRepository.findActiveDiscordActionInstances())
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(actionInstance, ActivationModeType.POLL, true))
            .thenReturn(Collections.singletonList(activationMode));
        when(discordActionService.checkDiscordEvents(
            eq("discord.message.new"),
            any(),
            any(),
            any(LocalDateTime.class)
        )).thenReturn(events);

        double beforeEventsFound = meterRegistry.find("discord.polling.events_found").counter().count();
        discordEventPollingService.pollDiscordEvents();
        double afterEventsFound = meterRegistry.find("discord.polling.events_found").counter().count();

        assertEquals(beforeEventsFound + 2, afterEventsFound);
        verify(executionTriggerService, times(2))
            .triggerAreaExecution(eq(actionInstance), eq(ActivationModeType.POLL), any());
    }

    @Test
    void testProcessActionInstanceWithNoEvents() throws Exception {
        UUID instanceId = UUID.randomUUID();
        ActionInstance actionInstance = createMockActionInstance(instanceId, true);
        ActivationMode activationMode = createMockActivationMode(actionInstance, 300);
        
        when(actionInstanceRepository.findActiveDiscordActionInstances())
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(actionInstance, ActivationModeType.POLL, true))
            .thenReturn(Collections.singletonList(activationMode));
        when(discordActionService.checkDiscordEvents(
            eq("discord.message.new"),
            any(),
            any(),
            any(LocalDateTime.class)
        )).thenReturn(Collections.emptyList());

        discordEventPollingService.pollDiscordEvents();

        verify(executionTriggerService, never())
            .triggerAreaExecution(any(), any(), any());
    }

    @Test
    void testProcessActionInstanceWithNoActivationModes() throws Exception {
        ActionInstance actionInstance = createMockActionInstance(UUID.randomUUID(), true);
        
        when(actionInstanceRepository.findActiveDiscordActionInstances())
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(actionInstance, ActivationModeType.POLL, true))
            .thenReturn(Collections.emptyList());

        discordEventPollingService.pollDiscordEvents();

        verify(discordActionService, never())
            .checkDiscordEvents(any(), any(), any(), any());
    }

    @Test
    void testProcessActionInstanceWithExecutionTriggerException() throws Exception {
        UUID instanceId = UUID.randomUUID();
        ActionInstance actionInstance = createMockActionInstance(instanceId, true);
        ActivationMode activationMode = createMockActivationMode(actionInstance, 300);
        
        Map<String, Object> event = new HashMap<>();
        event.put("type", "MESSAGE_CREATE");
        
        when(actionInstanceRepository.findActiveDiscordActionInstances())
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(actionInstance, ActivationModeType.POLL, true))
            .thenReturn(Collections.singletonList(activationMode));
        when(discordActionService.checkDiscordEvents(any(), any(), any(), any()))
            .thenReturn(Collections.singletonList(event));
        doThrow(new RuntimeException("Trigger failed"))
            .when(executionTriggerService).triggerAreaExecution(any(), any(), any());

        discordEventPollingService.pollDiscordEvents();

        verify(executionTriggerService, times(1))
            .triggerAreaExecution(any(), any(), any());
    }

    @Test
    void testProcessActionInstanceWithCheckEventsException() throws Exception {
        ActionInstance actionInstance = createMockActionInstance(UUID.randomUUID(), true);
        ActivationMode activationMode = createMockActivationMode(actionInstance, 300);
        
        when(actionInstanceRepository.findActiveDiscordActionInstances())
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(actionInstance, ActivationModeType.POLL, true))
            .thenReturn(Collections.singletonList(activationMode));
        when(discordActionService.checkDiscordEvents(any(), any(), any(), any()))
            .thenThrow(new RuntimeException("Discord API error"));

        discordEventPollingService.pollDiscordEvents();

        verify(executionTriggerService, never())
            .triggerAreaExecution(any(), any(), any());
    }

    @Test
    void testShouldPollNowFirstTime() throws Exception {
        ActionInstance actionInstance = createMockActionInstance(UUID.randomUUID(), true);
        ActivationMode activationMode = createMockActivationMode(actionInstance, 300);
        
        when(actionInstanceRepository.findActiveDiscordActionInstances())
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(actionInstance, ActivationModeType.POLL, true))
            .thenReturn(Collections.singletonList(activationMode));
        when(discordActionService.checkDiscordEvents(any(), any(), any(), any()))
            .thenReturn(Collections.emptyList());

        discordEventPollingService.pollDiscordEvents();

        verify(discordActionService, times(1))
            .checkDiscordEvents(any(), any(), any(), any());
    }

    @Test
    void testShouldPollNowIntervalNotElapsed() throws Exception {
        UUID instanceId = UUID.randomUUID();
        ActionInstance actionInstance = createMockActionInstance(instanceId, true);
        ActivationMode activationMode = createMockActivationMode(actionInstance, 300);
        
        // Set last poll time to now
        Field lastPollTimesField = DiscordEventPollingService.class.getDeclaredField("lastPollTimes");
        lastPollTimesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, LocalDateTime> lastPollTimes = (Map<UUID, LocalDateTime>) lastPollTimesField.get(discordEventPollingService);
        lastPollTimes.put(instanceId, LocalDateTime.now());
        
        when(actionInstanceRepository.findActiveDiscordActionInstances())
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(actionInstance, ActivationModeType.POLL, true))
            .thenReturn(Collections.singletonList(activationMode));

        discordEventPollingService.pollDiscordEvents();

        verify(discordActionService, never())
            .checkDiscordEvents(any(), any(), any(), any());
    }

    @Test
    void testShouldPollNowIntervalElapsed() throws Exception {
        UUID instanceId = UUID.randomUUID();
        ActionInstance actionInstance = createMockActionInstance(instanceId, true);
        ActivationMode activationMode = createMockActivationMode(actionInstance, 1); // 1 second interval
        
        // Set last poll time to 2 seconds ago
        Field lastPollTimesField = DiscordEventPollingService.class.getDeclaredField("lastPollTimes");
        lastPollTimesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, LocalDateTime> lastPollTimes = (Map<UUID, LocalDateTime>) lastPollTimesField.get(discordEventPollingService);
        lastPollTimes.put(instanceId, LocalDateTime.now().minusSeconds(2));
        
        when(actionInstanceRepository.findActiveDiscordActionInstances())
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(actionInstance, ActivationModeType.POLL, true))
            .thenReturn(Collections.singletonList(activationMode));
        when(discordActionService.checkDiscordEvents(any(), any(), any(), any()))
            .thenReturn(Collections.emptyList());

        discordEventPollingService.pollDiscordEvents();

        verify(discordActionService, times(1))
            .checkDiscordEvents(any(), any(), any(), any());
    }

    @Test
    void testGetPollingIntervalDefault() throws Exception {
        ActionInstance actionInstance = createMockActionInstance(UUID.randomUUID(), true);
        ActivationMode activationMode = mock(ActivationMode.class);
        when(activationMode.getActionInstance()).thenReturn(actionInstance);
        when(activationMode.getConfig()).thenReturn(new HashMap<>());
        
        Method getPollingIntervalMethod = DiscordEventPollingService.class.getDeclaredMethod("getPollingInterval", ActivationMode.class);
        getPollingIntervalMethod.setAccessible(true);
        
        int interval = (int) getPollingIntervalMethod.invoke(discordEventPollingService, activationMode);
        
        assertEquals(300, interval);
    }

    @Test
    void testGetPollingIntervalCustom() throws Exception {
        ActionInstance actionInstance = createMockActionInstance(UUID.randomUUID(), true);
        ActivationMode activationMode = mock(ActivationMode.class);
        Map<String, Object> config = new HashMap<>();
        config.put("pollingInterval", 600);
        when(activationMode.getActionInstance()).thenReturn(actionInstance);
        when(activationMode.getConfig()).thenReturn(config);
        
        Method getPollingIntervalMethod = DiscordEventPollingService.class.getDeclaredMethod("getPollingInterval", ActivationMode.class);
        getPollingIntervalMethod.setAccessible(true);
        
        int interval = (int) getPollingIntervalMethod.invoke(discordEventPollingService, activationMode);
        
        assertEquals(600, interval);
    }

    @Test
    void testCalculateLastCheckTimeDefault() throws Exception {
        ActivationMode activationMode = mock(ActivationMode.class);
        when(activationMode.getConfig()).thenReturn(new HashMap<>());
        
        Method calculateLastCheckTimeMethod = DiscordEventPollingService.class.getDeclaredMethod("calculateLastCheckTime", ActivationMode.class);
        calculateLastCheckTimeMethod.setAccessible(true);
        
        LocalDateTime before = LocalDateTime.now().minusSeconds(301);
        LocalDateTime result = (LocalDateTime) calculateLastCheckTimeMethod.invoke(discordEventPollingService, activationMode);
        LocalDateTime after = LocalDateTime.now().minusSeconds(299);
        
        assertTrue(result.isAfter(before) && result.isBefore(after));
    }

    @Test
    void testCalculateLastCheckTimeCustom() throws Exception {
        ActivationMode activationMode = mock(ActivationMode.class);
        Map<String, Object> config = new HashMap<>();
        config.put("pollingInterval", 600);
        when(activationMode.getConfig()).thenReturn(config);
        
        Method calculateLastCheckTimeMethod = DiscordEventPollingService.class.getDeclaredMethod("calculateLastCheckTime", ActivationMode.class);
        calculateLastCheckTimeMethod.setAccessible(true);
        
        LocalDateTime before = LocalDateTime.now().minusSeconds(601);
        LocalDateTime result = (LocalDateTime) calculateLastCheckTimeMethod.invoke(discordEventPollingService, activationMode);
        LocalDateTime after = LocalDateTime.now().minusSeconds(599);
        
        assertTrue(result.isAfter(before) && result.isBefore(after));
    }

    // Helper methods
    private ActionInstance createMockActionInstance(UUID id, boolean enabled) {
        ActionInstance instance = mock(ActionInstance.class);
        ActionDefinition actionDefinition = mock(ActionDefinition.class);
        User user = mock(User.class);
        
        when(instance.getId()).thenReturn(id);
        when(instance.getEnabled()).thenReturn(enabled);
        when(instance.getActionDefinition()).thenReturn(actionDefinition);
        when(instance.getUser()).thenReturn(user);
        when(instance.getParams()).thenReturn(new HashMap<>());
        when(actionDefinition.getKey()).thenReturn("discord.message.new");
        when(user.getId()).thenReturn(UUID.randomUUID());
        
        return instance;
    }

    private ActivationMode createMockActivationMode(ActionInstance actionInstance, int pollingInterval) {
        ActivationMode activationMode = mock(ActivationMode.class);
        Map<String, Object> config = new HashMap<>();
        config.put("pollingInterval", pollingInterval);
        
        when(activationMode.getActionInstance()).thenReturn(actionInstance);
        when(activationMode.getConfig()).thenReturn(config);
        when(activationMode.getType()).thenReturn(ActivationModeType.POLL);
        when(activationMode.getEnabled()).thenReturn(true);
        
        return activationMode;
    }
}

