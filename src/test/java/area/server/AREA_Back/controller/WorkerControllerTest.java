package area.server.AREA_Back.controller;

import area.server.AREA_Back.service.Area.ExecutionService;
import area.server.AREA_Back.service.Redis.RedisEventService;
import area.server.AREA_Back.worker.AreaReactionWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkerController Tests")
class WorkerControllerTest {

    @Mock
    private AreaReactionWorker areaReactionWorker;

    @Mock
    private ExecutionService executionService;

    @Mock
    private RedisEventService redisEventService;

    @InjectMocks
    private WorkerController workerController;

    private MockMvc mockMvc;

    private UUID executionId;
    private UUID actionInstanceId;
    private UUID areaId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(workerController).build();
        executionId = UUID.randomUUID();
        actionInstanceId = UUID.randomUUID();
        areaId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Should get worker status successfully")
    void testGetWorkerStatus() throws Exception {
        Map<String, Object> status = new HashMap<>();
        status.put("running", true);
        status.put("activeThreads", 5);
        status.put("processedEvents", 100L);

        when(areaReactionWorker.getWorkerStatus()).thenReturn(status);

        mockMvc.perform(get("/api/worker/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(true))
                .andExpect(jsonPath("$.activeThreads").value(5))
                .andExpect(jsonPath("$.processedEvents").value(100));

        verify(areaReactionWorker, times(1)).getWorkerStatus();
    }

    @Test
    @DisplayName("Should get execution statistics successfully")
    void testGetExecutionStatistics() throws Exception {
        Map<String, Long> statistics = new HashMap<>();
        statistics.put("pending", 10L);
        statistics.put("running", 5L);
        statistics.put("completed", 100L);
        statistics.put("failed", 2L);

        when(executionService.getExecutionStatistics()).thenReturn(statistics);

        mockMvc.perform(get("/api/worker/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value(10))
                .andExpect(jsonPath("$.running").value(5))
                .andExpect(jsonPath("$.completed").value(100))
                .andExpect(jsonPath("$.failed").value(2));

        verify(executionService, times(1)).getExecutionStatistics();
    }

    @Test
    @DisplayName("Should get stream info successfully")
    void testGetStreamInfo() throws Exception {
        Map<String, Object> streamInfo = new HashMap<>();
        streamInfo.put("streamName", "area-events");
        streamInfo.put("messageCount", 50L);
        streamInfo.put("consumerGroups", 3);

        when(redisEventService.getStreamInfo()).thenReturn(streamInfo);

        mockMvc.perform(get("/api/worker/stream-info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.streamName").value("area-events"))
                .andExpect(jsonPath("$.messageCount").value(50))
                .andExpect(jsonPath("$.consumerGroups").value(3));

        verify(redisEventService, times(1)).getStreamInfo();
    }

    @Test
    @DisplayName("Should cancel execution successfully")
    void testCancelExecution() throws Exception {
        String reason = "Manual cancellation";
        // cancelExecution returns Execution, not void
        mockMvc.perform(post("/api/worker/executions/{executionId}/cancel", executionId)
                        .param("reason", reason))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("canceled"))
                .andExpect(jsonPath("$.executionId").value(executionId.toString()))
                .andExpect(jsonPath("$.reason").value(reason));

        verify(executionService, times(1)).cancelExecution(executionId, reason);
    }

    @Test
    @DisplayName("Should cancel execution without reason")
    void testCancelExecutionWithoutReason() throws Exception {
        // cancelExecution returns Execution, not void
        mockMvc.perform(post("/api/worker/executions/{executionId}/cancel", executionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("canceled"))
                .andExpect(jsonPath("$.executionId").value(executionId.toString()))
                .andExpect(jsonPath("$.reason").value("Manual cancellation"));

        verify(executionService, times(1)).cancelExecution(eq(executionId), isNull());
    }

    @Test
    @DisplayName("Should return 404 when canceling non-existent execution")
    void testCancelExecutionNotFound() throws Exception {
        doThrow(new IllegalArgumentException("Execution not found"))
                .when(executionService).cancelExecution(any(UUID.class), anyString());

        mockMvc.perform(post("/api/worker/executions/{executionId}/cancel", executionId)
                        .param("reason", "Test"))
                .andExpect(status().isNotFound());

        verify(executionService, times(1)).cancelExecution(any(UUID.class), anyString());
    }

    @Test
    @DisplayName("Should send test event successfully")
    void testSendTestEvent() throws Exception {
        String eventId = "test-event-123";
        when(redisEventService.publishExecutionEvent(
                any(UUID.class),
                eq(actionInstanceId),
                eq(areaId),
                anyMap()
        )).thenReturn(eventId);

        mockMvc.perform(post("/api/worker/test-event")
                        .param("actionInstanceId", actionInstanceId.toString())
                        .param("areaId", areaId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("sent"))
                .andExpect(jsonPath("$.eventId").value(eventId))
                .andExpect(jsonPath("$.actionInstanceId").value(actionInstanceId.toString()))
                .andExpect(jsonPath("$.areaId").value(areaId.toString()));

        verify(redisEventService, times(1)).publishExecutionEvent(
                any(UUID.class),
                eq(actionInstanceId),
                eq(areaId),
                anyMap()
        );
    }

    @Test
    @DisplayName("Should handle error when sending test event")
    void testSendTestEventError() throws Exception {
        when(redisEventService.publishExecutionEvent(
                any(UUID.class),
                any(UUID.class),
                any(UUID.class),
                anyMap()
        )).thenThrow(new RuntimeException("Redis connection error"));

        mockMvc.perform(post("/api/worker/test-event")
                        .param("actionInstanceId", actionInstanceId.toString())
                        .param("areaId", areaId.toString()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Redis connection error"));
    }

    @Test
    @DisplayName("Should initialize stream successfully")
    void testInitializeStream() throws Exception {
        doNothing().when(redisEventService).initializeStream();

        mockMvc.perform(post("/api/worker/initialize-stream"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("initialized"))
                .andExpect(jsonPath("$.message").value("Redis stream and consumer group initialized successfully"));

        verify(redisEventService, times(1)).initializeStream();
    }

    @Test
    @DisplayName("Should handle error when initializing stream")
    void testInitializeStreamError() throws Exception {
        doThrow(new RuntimeException("Stream already exists"))
                .when(redisEventService).initializeStream();

        mockMvc.perform(post("/api/worker/initialize-stream"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Stream already exists"));

        verify(redisEventService, times(1)).initializeStream();
    }

    @Test
    @DisplayName("Should return empty statistics when no data available")
    void testGetExecutionStatisticsEmpty() throws Exception {
        when(executionService.getExecutionStatistics()).thenReturn(new HashMap<>());

        mockMvc.perform(get("/api/worker/statistics"))
                .andExpect(status().isOk())
                .andExpect(content().json("{}"));
    }

    @Test
    @DisplayName("Should return empty stream info when no data available")
    void testGetStreamInfoEmpty() throws Exception {
        when(redisEventService.getStreamInfo()).thenReturn(new HashMap<>());

        mockMvc.perform(get("/api/worker/stream-info"))
                .andExpect(status().isOk())
                .andExpect(content().json("{}"));
    }
}
