package area.server.AREA_Back.controller;

import area.server.AREA_Back.dto.AreaResponse;
import area.server.AREA_Back.dto.CreateAreaRequest;
import area.server.AREA_Back.dto.CreateAreaWithActionsRequest;
import area.server.AREA_Back.entity.Area;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.repository.AreaRepository;
import area.server.AREA_Back.repository.UserRepository;
import area.server.AREA_Back.service.AreaService;
import area.server.AREA_Back.service.CronSchedulerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/areas")
@Tag(name = "Areas", description = "API for managing areas (automations)")
@Slf4j
public class AreaController {

    @Autowired
    private AreaRepository areaRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AreaService areaService;

    @Autowired  
    private CronSchedulerService cronSchedulerService;

    @GetMapping
    public ResponseEntity<Page<AreaResponse>> getAllAreas(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("desc")
            ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Area> areas = areaRepository.findAll(pageable);
        Page<AreaResponse> areaResponses = areas.map(this::convertToResponse);

        return ResponseEntity.ok(areaResponses);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AreaResponse> getAreaById(@PathVariable UUID id) {
        Optional<Area> area = areaRepository.findById(id);
        if (area.isPresent()) {
            return ResponseEntity.ok(convertToResponse(area.get()));
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<AreaResponse>> getAreasByUserId(@PathVariable UUID userId) {
        List<Area> areas = areaRepository.findByUserId(userId);
        List<AreaResponse> areaResponses = areas.stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
        return ResponseEntity.ok(areaResponses);
    }

    @PostMapping
    @Operation(summary = "Create a new basic area")
    public ResponseEntity<AreaResponse> createArea(@Valid @RequestBody CreateAreaRequest request) {
        Optional<User> user = userRepository.findById(request.getUserId());
        if (!user.isPresent()) {
            return ResponseEntity.badRequest().build();
        }

        Area area = new Area();
        area.setName(request.getName());
        area.setDescription(request.getDescription());
        area.setUser(user.get());
        area.setEnabled(true);

        Area savedArea = areaRepository.save(area);
        return ResponseEntity.status(HttpStatus.CREATED).body(areaService.convertToResponse(savedArea));
    }

    @PostMapping("/with-actions")
    @Operation(summary = "Create a new AREA automation with actions and reactions",
               description = "Creates a new AREA with specified actions (triggers) and reactions. "
                           + "Validates JSON schemas and creates necessary action instances.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "AREA created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data or validation failure"),
        @ApiResponse(responseCode = "404", description = "User, action definition, or service account not found")
    })
    public ResponseEntity<?> createAreaWithActions(@Valid @RequestBody CreateAreaWithActionsRequest request) {
        try {
            log.info("Creating AREA with actions: {} for user: {}", request.getName(), request.getUserId());
            AreaResponse response = areaService.createAreaWithActions(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            log.warn("Area creation failed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(Map.of(
                    "error", "Validation failed",
                    "message", e.getMessage()
                ));
        } catch (Exception e) {
            log.error("Unexpected error creating AREA", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "error", "Internal server error",
                    "message", "An unexpected error occurred"
                ));
        }
    }

    @PostMapping("/{id}/trigger")
    @Operation(summary = "Manually trigger an AREA execution",
               description = "Manually triggers the execution of an AREA with optional input payload")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "AREA triggered successfully"),
        @ApiResponse(responseCode = "404", description = "AREA not found"),
        @ApiResponse(responseCode = "400", description = "AREA is disabled or has no action instances")
    })
    public ResponseEntity<Map<String, Object>> triggerArea(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> inputPayload) {
        
        try {
            log.info("Manual trigger requested for AREA: {}", id);
            
            Map<String, Object> result = areaService.triggerAreaManually(id, inputPayload);
            
            return ResponseEntity.ok(result);
            
        } catch (IllegalArgumentException e) {
            log.warn("Failed to trigger AREA {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest()
                .body(Map.of(
                    "error", "Invalid request",
                    "message", e.getMessage()
                ));
        } catch (Exception e) {
            log.error("Unexpected error triggering AREA {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "error", "Internal server error",
                    "message", "An unexpected error occurred"
                ));
        }
    }

    @GetMapping("/scheduler/status")
    @Operation(summary = "Get CRON scheduler status",
               description = "Returns the status of all scheduled CRON tasks")
    public ResponseEntity<Map<String, Object>> getSchedulerStatus() {
        try {
            Map<UUID, Boolean> tasksStatus = cronSchedulerService.getScheduledTasksStatus();
            int activeTasksCount = cronSchedulerService.getActiveTasksCount();
            
            return ResponseEntity.ok(Map.of(
                "active_tasks_count", activeTasksCount,
                "total_tasks_count", tasksStatus.size(),
                "tasks_status", tasksStatus,
                "scheduler_running", true
            ));
        } catch (Exception e) {
            log.error("Failed to get scheduler status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "error", "Failed to get scheduler status",
                    "message", e.getMessage()
                ));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<AreaResponse> updateArea(
            @PathVariable UUID id,
            @Valid @RequestBody CreateAreaRequest request) {

        Optional<Area> optionalArea = areaRepository.findById(id);
        if (!optionalArea.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        Area area = optionalArea.get();
        area.setName(request.getName());
        area.setDescription(request.getDescription());

        Area updatedArea = areaRepository.save(area);
        return ResponseEntity.ok(convertToResponse(updatedArea));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteArea(@PathVariable UUID id) {
        if (!areaRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        areaRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<AreaResponse> toggleArea(@PathVariable UUID id) {
        Optional<Area> optionalArea = areaRepository.findById(id);
        if (!optionalArea.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        Area area = optionalArea.get();
        area.setEnabled(!area.getEnabled());
        Area updatedArea = areaRepository.save(area);

        return ResponseEntity.ok(convertToResponse(updatedArea));
    }

    @GetMapping("/search")
    public ResponseEntity<List<AreaResponse>> searchAreas(@RequestParam String name) {
        List<Area> areas = areaRepository.findByNameContainingIgnoreCase(name);
        List<AreaResponse> areaResponses = areas.stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
        return ResponseEntity.ok(areaResponses);
    }

    private AreaResponse convertToResponse(Area area) {
        return areaService.convertToResponse(area);
    }
}