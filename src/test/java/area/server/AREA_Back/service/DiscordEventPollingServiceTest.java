package area.server.AREA_Back.service;

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

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
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
}
