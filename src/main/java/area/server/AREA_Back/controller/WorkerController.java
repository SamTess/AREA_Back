package area.server.AREA_Back.controller;

import area.server.AREA_Back.service.ExecutionService;
import area.server.AREA_Back.service.RedisEventService;
import area.server.AREA_Back.worker.AreaReactionWorker;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/worker")
@RequiredArgsConstructor
@Tag(name = "Worker", description = "AREA reaction worker monitoring and control")
public class WorkerController {

    private final AreaReactionWorker areaReactionWorker;
    private final ExecutionService executionService;
    private final RedisEventService redisEventService;

    @GetMapping("/status")
    @Operation(summary = "Get worker status", description = "Returns the current status of the AREA reaction worker")
    public ResponseEntity<Map<String, Object>> getWorkerStatus() {
        return ResponseEntity.ok(areaReactionWorker.getWorkerStatus());
    }

    @GetMapping("/statistics")
    @Operation(summary = "Get execution statistics", description = "Returns statistics about execution status")
    public ResponseEntity<Map<String, Long>> getExecutionStatistics() {
        return ResponseEntity.ok(executionService.getExecutionStatistics());
    }

    @GetMapping("/stream-info")
    @Operation(summary = "Get Redis stream information", description = "Returns information about the Redis streams")
    public ResponseEntity<Map<String, Object>> getStreamInfo() {
        return ResponseEntity.ok(redisEventService.getStreamInfo());
    }

    @PostMapping("/executions/{executionId}/cancel")
    @Operation(summary = "Cancel execution", description = "Cancel a specific execution")
    public ResponseEntity<Map<String, String>> cancelExecution(
            @PathVariable UUID executionId,
            @RequestParam(required = false) String reason) {
        try {
            executionService.cancelExecution(executionId, reason);
            return ResponseEntity.ok(Map.of(
                "status", "canceled",
                "executionId", executionId.toString(),
                "reason", reason != null ? reason : "Manual cancellation"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/test-event")
    @Operation(summary = "Send test event", description = "Send a test event to the worker for testing purposes")
    public ResponseEntity<Map<String, String>> sendTestEvent(
            @RequestParam UUID actionInstanceId,
            @RequestParam UUID areaId) {
        try {
            String eventId = redisEventService.publishExecutionEvent(
                UUID.randomUUID(),
                actionInstanceId,
                areaId,
                Map.of("test", true, "timestamp", System.currentTimeMillis())
            );

            return ResponseEntity.ok(Map.of(
                "status", "sent",
                "eventId", eventId,
                "actionInstanceId", actionInstanceId.toString(),
                "areaId", areaId.toString()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/initialize-stream")
    @Operation(summary = "Initialize Redis stream", description = "Initialize the Redis stream and consumer group")
    public ResponseEntity<Map<String, String>> initializeStream() {
        try {
            redisEventService.initializeStream();
            return ResponseEntity.ok(Map.of(
                "status", "initialized",
                "message", "Redis stream and consumer group initialized successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
        }
    }
}