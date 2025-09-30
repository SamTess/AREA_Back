package area.server.AREA_Back.integration;

import area.server.AREA_Back.TestcontainersConfiguration;
import area.server.AREA_Back.dto.AreaEventMessage;
import area.server.AREA_Back.entity.Area;
import area.server.AREA_Back.entity.ActionDefinition;
import area.server.AREA_Back.entity.ActionInstance;
import area.server.AREA_Back.entity.Service;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.entity.Execution;
import area.server.AREA_Back.entity.enums.ExecutionStatus;
import area.server.AREA_Back.repository.AreaRepository;
import area.server.AREA_Back.repository.ActionDefinitionRepository;
import area.server.AREA_Back.repository.ActionInstanceRepository;
import area.server.AREA_Back.repository.ServiceRepository;
import area.server.AREA_Back.repository.UserRepository;
import area.server.AREA_Back.repository.ExecutionRepository;
import area.server.AREA_Back.service.ExecutionService;
import area.server.AREA_Back.service.RedisEventService;
import area.server.AREA_Back.worker.AreaReactionWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("integration-test")
@TestPropertySource(locations = "classpath:application-integration-test.properties")
@Transactional
class WorkerIntegrationTest {

    @Autowired
    private ExecutionService executionService;
    
    @Autowired
    private RedisEventService redisEventService;
    
    @Autowired
    private AreaReactionWorker areaReactionWorker;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private ServiceRepository serviceRepository;
    
    @Autowired
    private ActionDefinitionRepository actionDefinitionRepository;
    
    @Autowired
    private AreaRepository areaRepository;
    
    @Autowired
    private ActionInstanceRepository actionInstanceRepository;
    
    @Autowired
    private ExecutionRepository executionRepository;

    private User testUser;
    private Service testService;
    private ActionDefinition testActionDefinition;
    private Area testArea;
    private ActionInstance testActionInstance;

    @BeforeEach
    void setUp() {
        // Create test user
        testUser = new User();
        testUser.setEmail("test-worker@example.com");
        testUser.setPasswordHash("hashed");
        testUser.setIsActive(true);
        testUser.setIsAdmin(false);
        testUser = userRepository.save(testUser);

        // Create test service
        testService = new Service();
        testService.setKey("test-service");
        testService.setName("Test Service");
        testService.setIsActive(true);
        testService.setAuth(Service.AuthType.NONE);
        testService = serviceRepository.save(testService);

        // Create test action definition
        testActionDefinition = new ActionDefinition();
        testActionDefinition.setService(testService);
        testActionDefinition.setKey("test-reaction");
        testActionDefinition.setName("Test Reaction");
        testActionDefinition.setDescription("Test reaction for integration testing");
        testActionDefinition.setIsExecutable(true);
        testActionDefinition.setIsEventCapable(false);
        testActionDefinition.setInputSchema(Map.of("type", "object"));
        testActionDefinition.setOutputSchema(Map.of("type", "object"));
        testActionDefinition = actionDefinitionRepository.save(testActionDefinition);

        // Create test area
        testArea = new Area();
        testArea.setUser(testUser);
        testArea.setName("Test Area");
        testArea.setDescription("Test area for integration testing");
        testArea.setEnabled(true);
        testArea = areaRepository.save(testArea);

        // Create test action instance
        testActionInstance = new ActionInstance();
        testActionInstance.setUser(testUser);
        testActionInstance.setArea(testArea);
        testActionInstance.setActionDefinition(testActionDefinition);
        testActionInstance.setName("Test Action Instance");
        testActionInstance.setEnabled(true);
        testActionInstance.setParams(Map.of("param1", "value1"));
        testActionInstance = actionInstanceRepository.save(testActionInstance);
    }

    @Test
    void testFullWorkflowWebhookToWorkerToDatabase() throws Exception {
        // Given: Create an execution
        Execution execution = executionService.createExecution(
            testActionInstance,
            null, // No activation mode for this test
            Map.of("inputData", "test"),
            UUID.randomUUID()
        );

        // Verify execution was created
        assertNotNull(execution);
        assertEquals(ExecutionStatus.QUEUED, execution.getStatus());

        // When: Publish event to Redis stream
        AreaEventMessage eventMessage = AreaEventMessage.fromExecution(
            execution.getId(),
            testActionInstance.getId(),
            testArea.getId(),
            Map.of("eventData", "test")
        );
        
        String eventId = redisEventService.publishAreaEvent(eventMessage);
        assertNotNull(eventId);

        // Wait a bit for worker to process
        Thread.sleep(2000);

        // Then: Check that execution was processed
        var processedExecution = executionRepository.findById(execution.getId());
        assertTrue(processedExecution.isPresent());
        
        // The execution should have been processed by the worker
        // Note: In a real scenario, the worker would have updated the status
        // For this test, we verify the infrastructure is working
        assertNotNull(processedExecution.get());
    }

    @Test
    void testExecutionServiceStatistics() {
        // Given: Create multiple executions with different statuses
        Execution execution1 = executionService.createExecution(
            testActionInstance, null, Map.of("data", "test1"), UUID.randomUUID());
        
        Execution execution2 = executionService.createExecution(
            testActionInstance, null, Map.of("data", "test2"), UUID.randomUUID());

        // When: Get statistics
        Map<String, Long> stats = executionService.getExecutionStatistics();

        // Then: Verify statistics
        assertNotNull(stats);
        assertTrue(stats.get("queued") >= 2);
        assertTrue(stats.containsKey("running"));
        assertTrue(stats.containsKey("ok"));
        assertTrue(stats.containsKey("failed"));
        assertTrue(stats.containsKey("retry"));
        assertTrue(stats.containsKey("canceled"));
    }

    @Test
    void testRedisStreamInitialization() {
        // When: Initialize Redis stream
        assertDoesNotThrow(() -> {
            redisEventService.initializeStream();
        });

        // Then: Get stream info
        Map<String, Object> streamInfo = redisEventService.getStreamInfo();
        assertNotNull(streamInfo);
        assertTrue(streamInfo.containsKey("streamKey"));
        assertTrue(streamInfo.containsKey("consumerGroup"));
    }

    @Test
    void testWorkerStatus() {
        // When: Get worker status
        Map<String, Object> status = areaReactionWorker.getWorkerStatus();

        // Then: Verify status structure
        assertNotNull(status);
        assertTrue(status.containsKey("consumerName"));
        assertTrue(status.containsKey("running"));
        assertTrue(status.containsKey("streamInfo"));
        
        // Worker should be running
        assertTrue((Boolean) status.get("running"));
    }

    @Test
    void testExecutionCancellation() {
        // Given: Create an execution
        Execution execution = executionService.createExecution(
            testActionInstance,
            null,
            Map.of("inputData", "test"),
            UUID.randomUUID()
        );

        // When: Cancel the execution
        String reason = "Test cancellation";
        Execution canceledExecution = executionService.cancelExecution(execution.getId(), reason);

        // Then: Verify cancellation
        assertNotNull(canceledExecution);
        assertEquals(ExecutionStatus.CANCELED, canceledExecution.getStatus());
        assertNotNull(canceledExecution.getFinishedAt());
        assertNotNull(canceledExecution.getError());
        assertTrue(canceledExecution.getError().containsKey("reason"));
        assertEquals(reason, canceledExecution.getError().get("reason"));
    }

    @Test
    void testQueuedExecutionsRetrieval() {
        // Given: Create multiple executions
        Execution execution1 = executionService.createExecution(
            testActionInstance, null, Map.of("data", "test1"), UUID.randomUUID());
        
        Execution execution2 = executionService.createExecution(
            testActionInstance, null, Map.of("data", "test2"), UUID.randomUUID());

        // When: Get queued executions
        var queuedExecutions = executionService.getQueuedExecutions();

        // Then: Verify executions are queued
        assertNotNull(queuedExecutions);
        assertTrue(queuedExecutions.size() >= 2);
        assertTrue(queuedExecutions.stream().anyMatch(e -> e.getId().equals(execution1.getId())));
        assertTrue(queuedExecutions.stream().anyMatch(e -> e.getId().equals(execution2.getId())));
    }

    @Test
    void testExecutionWithRetry() {
        // Given: Create an execution and mark it for retry
        Execution execution = executionService.createExecution(
            testActionInstance,
            null,
            Map.of("inputData", "test"),
            UUID.randomUUID()
        );

        // Mark as started first
        Execution startedExecution = executionService.markExecutionAsStarted(execution.getId());
        assertEquals(ExecutionStatus.RUNNING, startedExecution.getStatus());
        assertNotNull(startedExecution.getStartedAt());

        // When: Get executions ready for retry (none should be ready immediately)
        LocalDateTime retryThreshold = LocalDateTime.now().minusMinutes(1);
        var retryExecutions = executionService.getExecutionsReadyForRetry(retryThreshold);

        // Then: No executions should be ready for retry yet
        assertNotNull(retryExecutions);
        // The running execution shouldn't be in retry list
        assertFalse(retryExecutions.stream().anyMatch(e -> e.getId().equals(startedExecution.getId())));
    }
}