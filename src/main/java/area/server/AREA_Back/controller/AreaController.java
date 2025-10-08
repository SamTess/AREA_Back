package area.server.AREA_Back.controller;

import area.server.AREA_Back.constants.AuthTokenConstants;
import area.server.AREA_Back.dto.AreaResponse;
import area.server.AREA_Back.dto.CreateAreaRequest;
import area.server.AREA_Back.dto.CreateAreaWithActionsRequest;
import area.server.AREA_Back.dto.CreateAreaWithActionsAndLinksRequest;
import area.server.AREA_Back.entity.Area;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.repository.AreaRepository;
import area.server.AREA_Back.repository.UserRepository;
import area.server.AREA_Back.service.AreaService;
import area.server.AREA_Back.service.CronSchedulerService;
import area.server.AREA_Back.service.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
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

import java.time.LocalDateTime;
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

    @Autowired
    private JwtService jwtService;

    @GetMapping
    public ResponseEntity<Page<AreaResponse>> getAllAreas(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            HttpServletRequest request) {

        try {
            UUID userId = getUserIdFromRequest(request);

            Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();

            Pageable pageable = PageRequest.of(page, size, sort);

            Page<Area> areas = areaRepository.findByUserId(userId, pageable);
            Page<AreaResponse> areaResponses = areas.map(this::convertToResponse);

            return ResponseEntity.ok(areaResponses);
        } catch (Exception e) {
            log.error("Failed to get areas for user", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<AreaResponse> getAreaById(@PathVariable UUID id, HttpServletRequest request) {
        try {
            UUID userId = getUserIdFromRequest(request);

            Optional<Area> area = areaRepository.findById(id);
            if (area.isPresent()) {
                if (!area.get().getUser().getId().equals(userId)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
                }
                return ResponseEntity.ok(convertToResponse(area.get()));
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Failed to get area for user", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<AreaResponse>> getAreasByUserId(@PathVariable UUID userId, HttpServletRequest request) {
        try {
            UUID currentUserId = getUserIdFromRequest(request);

            if (!userId.equals(currentUserId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            List<Area> areas = areaRepository.findByUserId(userId);
            List<AreaResponse> areaResponses = areas.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
            return ResponseEntity.ok(areaResponses);
        } catch (Exception e) {
            log.error("Failed to get areas for user", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PostMapping
    @Operation(summary = "Create a new basic area")
    public ResponseEntity<AreaResponse> createArea(@Valid @RequestBody CreateAreaRequest request, HttpServletRequest httpRequest) {
        try {
            UUID userId = getUserIdFromRequest(httpRequest);

            Optional<User> user = userRepository.findById(userId);
            if (!user.isPresent()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            Area area = new Area();
            area.setName(request.getName());
            area.setDescription(request.getDescription());
            area.setUser(user.get());
            area.setEnabled(true);

            Area savedArea = areaRepository.save(area);
            return ResponseEntity.status(HttpStatus.CREATED).body(areaService.convertToResponse(savedArea));
        } catch (Exception e) {
            log.error("Failed to create area for user", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PostMapping("/with-actions")
    @Operation(summary = "Create a new AREA automation with actions and reactions",
               description = "Creates a new AREA with specified actions (triggers) and reactions. "
                           + "Validates JSON schemas and creates necessary action instances.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "AREA created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data or validation failure"),
        @ApiResponse(responseCode = "404", description = "User, action definition, or service account not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<?> createAreaWithActions(@Valid @RequestBody CreateAreaWithActionsRequest request,
                                                 HttpServletRequest httpRequest) {
        try {
            UUID userId = getUserIdFromRequest(httpRequest);
            request.setUserId(userId);

            log.info("Creating AREA with actions: {} for user: {}", request.getName(), userId);
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
        @ApiResponse(responseCode = "400", description = "AREA is disabled or has no action instances"),
        @ApiResponse(responseCode = "403", description = "Access denied - AREA does not belong to user")
    })
    public ResponseEntity<Map<String, Object>> triggerArea(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> inputPayload,
            HttpServletRequest request) {

        try {
            UUID userId = getUserIdFromRequest(request);

            Optional<Area> areaOpt = areaRepository.findById(id);
            if (!areaOpt.isPresent()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                        "error", "Not found",
                        "message", "AREA not found"
                    ));
            }

            Area area = areaOpt.get();
            if (!area.getUser().getId().equals(userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                        "error", "Access denied",
                        "message", "This AREA does not belong to you"
                    ));
            }

            log.info("Manual trigger requested for AREA: {} by user: {}", id, userId);

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

    @PostMapping("/scheduler/reload")
    @Operation(summary = "Reload CRON scheduler",
               description = "Reloads and reschedules all CRON activation modes")
    public ResponseEntity<Map<String, Object>> reloadScheduler() {
        try {
            cronSchedulerService.reloadAllCronActivations();

            Map<UUID, Boolean> tasksStatus = cronSchedulerService.getScheduledTasksStatus();
            int activeTasksCount = cronSchedulerService.getActiveTasksCount();

            return ResponseEntity.ok(Map.of(
                "message", "CRON scheduler reloaded successfully",
                "active_tasks_count", activeTasksCount,
                "total_tasks_count", tasksStatus.size(),
                "reloaded_at", LocalDateTime.now().toString()
            ));
        } catch (Exception e) {
            log.error("Failed to reload scheduler", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "error", "Failed to reload scheduler",
                    "message", e.getMessage()
                ));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<AreaResponse> updateArea(
            @PathVariable UUID id,
            @Valid @RequestBody CreateAreaRequest request,
            HttpServletRequest httpRequest) {

        try {
            UUID userId = getUserIdFromRequest(httpRequest);

            Optional<Area> optionalArea = areaRepository.findById(id);
            if (!optionalArea.isPresent()) {
                return ResponseEntity.notFound().build();
            }

            Area area = optionalArea.get();
            if (!area.getUser().getId().equals(userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            area.setName(request.getName());
            area.setDescription(request.getDescription());

            Area updatedArea = areaRepository.save(area);
            return ResponseEntity.ok(convertToResponse(updatedArea));
        } catch (Exception e) {
            log.error("Failed to update area for user", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteArea(@PathVariable UUID id, HttpServletRequest request) {
        try {
            UUID userId = getUserIdFromRequest(request);

            Optional<Area> optionalArea = areaRepository.findById(id);
            if (!optionalArea.isPresent()) {
                return ResponseEntity.notFound().build();
            }

            Area area = optionalArea.get();
            if (!area.getUser().getId().equals(userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            areaRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Failed to delete area for user", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<AreaResponse> toggleArea(@PathVariable UUID id, HttpServletRequest request) {
        try {
            UUID userId = getUserIdFromRequest(request);

            Optional<Area> optionalArea = areaRepository.findById(id);
            if (!optionalArea.isPresent()) {
                return ResponseEntity.notFound().build();
            }

            Area area = optionalArea.get();
            if (!area.getUser().getId().equals(userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            area.setEnabled(!area.getEnabled());
            Area updatedArea = areaRepository.save(area);

            return ResponseEntity.ok(convertToResponse(updatedArea));
        } catch (Exception e) {
            log.error("Failed to toggle area for user", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @GetMapping("/search")
    public ResponseEntity<List<AreaResponse>> searchAreas(@RequestParam String name, HttpServletRequest request) {
        try {
            UUID userId = getUserIdFromRequest(request);

            List<Area> userAreas = areaRepository.findByUserId(userId);
            List<Area> filteredAreas = userAreas.stream()
                .filter(area -> area.getName().toLowerCase().contains(name.toLowerCase()))
                .collect(Collectors.toList());

            List<AreaResponse> areaResponses = filteredAreas.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
            return ResponseEntity.ok(areaResponses);
        } catch (Exception e) {
            log.error("Failed to search areas for user", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PostMapping("/with-links")
    @Operation(summary = "Create a new AREA with actions, reactions, and connections",
               description = "Creates a new AREA including actions, reactions, and connections between them for advanced workflows")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "AREA created successfully with connections"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<AreaResponse> createAreaWithActionsAndLinks(
            @Valid @RequestBody CreateAreaWithActionsAndLinksRequest request,
            HttpServletRequest httpRequest) {
        try {
            UUID userId = getUserIdFromRequest(httpRequest);
            request.setUserId(userId);

            log.info("Creating AREA with links: {} for user: {}", request.getName(), userId);

            AreaResponse response = areaService.createAreaWithActionsAndLinks(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            log.error("Validation error creating AREA with links: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Failed to create AREA with links for user", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    private AreaResponse convertToResponse(Area area) {
        return areaService.convertToResponse(area);
    }

    private UUID getUserIdFromRequest(HttpServletRequest request) {
        try {
            String accessToken = extractAccessTokenFromCookies(request);
            if (accessToken == null) {
                throw new RuntimeException("Access token not found in cookies");
            }

            return jwtService.extractUserIdFromAccessToken(accessToken);
        } catch (Exception e) {
            log.error("Failed to extract user ID from request: {}", e.getMessage());
            throw new RuntimeException("Failed to extract user ID", e);
        }
    }

    private String extractAccessTokenFromCookies(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }

        for (Cookie cookie : request.getCookies()) {
            if (AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}